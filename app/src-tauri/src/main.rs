//! Treasure Launcher's window. All the real work is in treasure-core; this file only connects
//! the page (app/ui) to it. Long jobs run on their own threads and report back with events:
//!   progress      {id, stage, done, total}   downloads/installs for an instance
//!   job-done      {id}                       an install/repair finished
//!   job-error     {id, error, removed}       it failed (removed = the half-made instance was deleted)
//!   game-started  {id}
//!   game-log      {id, line, err}
//!   game-exited   {id, code, seconds}

#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::collections::{HashMap, HashSet};
use std::path::PathBuf;
use std::sync::{Arc, Mutex};

use serde::Serialize;
use serde_json::json;
use tauri::{AppHandle, Emitter, Manager};
use treasure_core::instance::{self, Instance, Loader, PackSource};
use treasure_core::net::{self, Check, Download};
use treasure_core::settings::{self, Settings};
use treasure_core::{auth, install, launch, modrinth, mods, mojang, mrpack, paths, treasure, util, Progress, ProgressFn};

type R<T> = Result<T, String>;

#[derive(Default)]
struct Games(Mutex<HashMap<String, launch::Game>>);
#[derive(Default)]
struct SignIn(Mutex<Option<auth::DeviceCode>>);
/// Instances with a download or install going on.
#[derive(Default)]
struct Busy(Mutex<HashSet<String>>);

async fn blocking<T: Send + 'static>(f: impl FnOnce() -> anyhow::Result<T> + Send + 'static) -> R<T> {
    tauri::async_runtime::spawn_blocking(f).await.map_err(|e| e.to_string())?.map_err(|e| format!("{e:#}"))
}

fn progress_for(app: &AppHandle, id: &str) -> ProgressFn {
    let app = app.clone();
    let id = id.to_string();
    Arc::new(move |p: Progress| {
        let _ = app.emit("progress", json!({"id": id, "stage": p.stage, "done": p.done, "total": p.total}));
    })
}

/// Runs `job` for instance `id` on its own thread, unless something is already happening to it.
fn job(app: &AppHandle, id: &str, remove_on_fail: bool, job: impl FnOnce(&AppHandle) -> anyhow::Result<()> + Send + 'static) -> R<()> {
    if !app.state::<Busy>().0.lock().unwrap().insert(id.to_string()) {
        return Err("That instance is busy. Wait for it to finish.".into());
    }
    let app = app.clone();
    let id = id.to_string();
    std::thread::spawn(move || {
        let r = job(&app);
        app.state::<Busy>().0.lock().unwrap().remove(&id);
        match r {
            Ok(()) => {
                let _ = app.emit("job-done", json!({"id": id}));
            }
            Err(e) => {
                if remove_on_fail {
                    let _ = instance::delete(&id);
                }
                let _ = app.emit("job-error", json!({"id": id, "error": format!("{e:#}"), "removed": remove_on_fail}));
            }
        }
    });
    Ok(())
}

// ---------------------------------------------------------------- start-up

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Init {
    settings: Settings,
    instances: Vec<Instance>,
    accounts: Vec<auth::AccountInfo>,
    signin_available: bool,
    version: &'static str,
    data_dir: String,
    supported: Vec<&'static str>,
    first_run: bool,
    memory_total_mb: Option<u64>,
}

#[tauri::command]
async fn init() -> R<Init> {
    blocking(|| {
        let first_run = treasure::ensure_defaults().unwrap_or(false);
        let s = settings::load();
        net::set_threads(s.downloads_at_once as usize);
        Ok(Init {
            settings: s,
            instances: instance::list(),
            accounts: auth::accounts(),
            signin_available: auth::available(),
            version: treasure_core::VERSION,
            data_dir: paths::root().to_string_lossy().into_owned(),
            supported: treasure::SUPPORTED.to_vec(),
            first_run,
            memory_total_mb: settings::total_memory_mb(),
        })
    })
    .await
}

#[tauri::command]
async fn get_settings() -> R<Settings> {
    blocking(|| Ok(settings::load())).await
}

#[tauri::command]
async fn set_settings(s: Settings) -> R<()> {
    blocking(move || {
        let mut s = s;
        s.memory_mb = s.memory_mb.clamp(1024, 65536);
        s.downloads_at_once = s.downloads_at_once.clamp(1, 32);
        // first-run state belongs to the launcher, not the page
        s.defaults_created = settings::load().defaults_created;
        net::set_threads(s.downloads_at_once as usize);
        settings::save(&s)
    })
    .await
}

#[derive(Serialize)]
struct Ver {
    id: String,
    kind: String,
}

#[tauri::command]
async fn game_versions(snapshots: bool) -> R<Vec<Ver>> {
    blocking(move || {
        Ok(mojang::manifest()?
            .versions
            .into_iter()
            .filter(|v| v.kind == "release" || (snapshots && v.kind == "snapshot"))
            .map(|v| Ver { id: v.id, kind: v.kind })
            .collect())
    })
    .await
}

#[tauri::command]
async fn news() -> R<serde_json::Value> {
    blocking(|| {
        Ok(match treasure::channel() {
            Ok(c) => json!({"news": c.news, "launcher": c.launcher}),
            Err(_) => json!({"news": [], "launcher": null}),
        })
    })
    .await
}

// ---------------------------------------------------------------- instances

#[tauri::command]
async fn list_instances() -> R<Vec<Instance>> {
    blocking(|| Ok(instance::list())).await
}

#[tauri::command]
async fn create_instance(name: String, mc: String, loader: String, tc: bool, group: String) -> R<Instance> {
    blocking(move || {
        let loader = if loader == "fabric" || tc { Loader::Fabric } else { Loader::Vanilla };
        if tc && !treasure::supported(&mc) {
            anyhow::bail!("Treasure Client isn't made for Minecraft {mc}");
        }
        mojang::vanilla(&mc)?;
        if loader == Loader::Fabric {
            treasure_core::fabric::latest_loader(&mc)?;
        }
        instance::create(&name, &mc, loader, tc, &group)
    })
    .await
}

/// Saves the things the page may change about an instance.
#[tauri::command]
async fn save_instance(inst: Instance) -> R<Instance> {
    blocking(move || {
        let mut cur = instance::get(&inst.id)?;
        let name = inst.name.trim();
        if !name.is_empty() {
            cur.name = name.chars().take(60).collect();
        }
        if !inst.group.trim().is_empty() {
            cur.group = inst.group.trim().chars().take(40).collect();
        }
        cur.memory_mb = inst.memory_mb.map(|m| m.clamp(1024, 65536));
        cur.java_path = inst.java_path.filter(|p| !p.trim().is_empty());
        cur.jvm_args = inst.jvm_args.filter(|a| !a.trim().is_empty());
        cur.width = inst.width.filter(|w| (320..=7680).contains(w));
        cur.height = inst.height.filter(|h| (240..=4320).contains(h));
        cur.pinned = inst.pinned;
        let tc_off = cur.treasure_client && !inst.treasure_client;
        if inst.treasure_client && !cur.treasure_client {
            if cur.loader != Loader::Fabric || !treasure::supported(&cur.mc_version) {
                anyhow::bail!("Treasure Client needs Fabric on 1.21.11 or 26.1 to 26.3");
            }
        }
        cur.treasure_client = inst.treasure_client;
        instance::save(&cur)?;
        if tc_off {
            treasure::remove_tc(&cur)?;
        }
        Ok(cur)
    })
    .await
}

#[tauri::command]
async fn duplicate_instance(id: String) -> R<Instance> {
    blocking(move || instance::duplicate(&id)).await
}

#[tauri::command]
async fn delete_instance(app: AppHandle, id: String) -> R<()> {
    if app.state::<Games>().0.lock().unwrap().contains_key(&id) || app.state::<Busy>().0.lock().unwrap().contains(&id) {
        return Err("Close the game (or wait for the download) first.".into());
    }
    blocking(move || instance::delete(&id)).await
}

#[tauri::command]
async fn instance_size(id: String) -> R<u64> {
    blocking(move || Ok(util::dir_size(&instance::game_dir(&id)?))).await
}

// ---------------------------------------------------------------- playing

fn start_game(app: &AppHandle, id: &str, server: Option<String>, world: Option<String>) -> anyhow::Result<()> {
    let player = auth::active_auth()?;
    let (inst, res, java) = install::prepare(id, Check::Quick, &progress_for(app, id))?;
    let s = settings::load();
    let opts = launch::LaunchOptions { server, world };
    let cmd = launch::build(&inst, &res, &java, &player, &opts, inst.memory_mb.unwrap_or(s.memory_mb))?;
    let (a1, i1) = (app.clone(), id.to_string());
    let (a2, i2) = (app.clone(), id.to_string());
    let game = launch::spawn(
        &cmd,
        Arc::new(move |line: &str, err| {
            let _ = a1.emit("game-log", json!({"id": i1, "line": line, "err": err}));
        }),
        Box::new(move |code, secs| {
            let _ = instance::played(&i2, secs);
            a2.state::<Games>().0.lock().unwrap().remove(&i2);
            if let Some(w) = a2.get_webview_window("main") {
                let _ = w.show();
                let _ = w.unminimize();
                let _ = w.set_focus();
            }
            let _ = a2.emit("game-exited", json!({"id": i2, "code": code, "seconds": secs}));
        }),
    )?;
    let running = game.running();
    if running {
        app.state::<Games>().0.lock().unwrap().insert(id.to_string(), game);
    }
    let _ = app.emit("game-started", json!({"id": id}));
    if running {
        if let Some(w) = app.get_webview_window("main") {
            match s.while_playing.as_str() {
                "hide" => {
                    let _ = w.hide();
                }
                "minimize" => {
                    let _ = w.minimize();
                }
                _ => {}
            }
        }
    }
    Ok(())
}

/// Downloads whatever is missing and starts the game. Returns straight away; watch the events.
#[tauri::command]
async fn play(app: AppHandle, id: String, server: Option<String>, world: Option<String>) -> R<()> {
    if app.state::<Games>().0.lock().unwrap().contains_key(&id) {
        return Err("It's already running.".into());
    }
    let id2 = id.clone();
    job(&app, &id, false, move |app| start_game(app, &id2, server, world))
}

#[tauri::command]
fn stop(app: AppHandle, id: String) -> R<()> {
    if let Some(g) = app.state::<Games>().0.lock().unwrap().get(&id) {
        g.stop();
    }
    Ok(())
}

#[tauri::command]
fn running(app: AppHandle) -> Vec<String> {
    app.state::<Games>().0.lock().unwrap().keys().cloned().collect()
}

#[tauri::command]
fn busy(app: AppHandle) -> Vec<String> {
    app.state::<Busy>().0.lock().unwrap().iter().cloned().collect()
}

/// Checks every file of an instance (and its Java) and downloads any that are wrong.
#[tauri::command]
async fn repair(app: AppHandle, id: String) -> R<()> {
    let id2 = id.clone();
    job(&app, &id, false, move |app| install::prepare(&id2, Check::Full, &progress_for(app, &id2)).map(|_| ()))
}

// ---------------------------------------------------------------- content

#[tauri::command]
async fn content(id: String, kind: String) -> R<Vec<mods::ContentFile>> {
    blocking(move || mods::list(&id, &kind)).await
}

#[tauri::command]
async fn set_content(id: String, kind: String, file: String, on: bool) -> R<String> {
    blocking(move || mods::set_enabled(&id, &kind, &file, on)).await
}

#[tauri::command]
async fn remove_content(id: String, kind: String, file: String) -> R<()> {
    blocking(move || mods::remove(&id, &kind, &file)).await
}

#[tauri::command]
async fn add_content(id: String, project: String, kind: String) -> R<Vec<String>> {
    blocking(move || modrinth::add_project(&instance::get(&id)?, &project, &kind)).await
}

#[tauri::command]
async fn check_updates(id: String, kind: String) -> R<Vec<mods::Update>> {
    blocking(move || mods::updates(&instance::get(&id)?, &kind)).await
}

#[tauri::command]
async fn apply_updates(id: String, kind: String, updates: Vec<mods::Update>) -> R<usize> {
    blocking(move || {
        let inst = instance::get(&id)?;
        let mut n = 0;
        for u in &updates {
            mods::apply_update(&inst, &kind, u)?;
            n += 1;
        }
        Ok(n)
    })
    .await
}

#[tauri::command]
async fn worlds(id: String) -> R<Vec<mods::World>> {
    blocking(move || mods::worlds(&id)).await
}

#[tauri::command]
async fn screenshots(id: String) -> R<Vec<String>> {
    blocking(move || mods::screenshots(&id)).await
}

#[tauri::command]
async fn screenshot(id: String, file: String) -> R<String> {
    blocking(move || mods::screenshot(&id, &file)).await
}

#[tauri::command]
async fn latest_log(id: String) -> R<Vec<String>> {
    blocking(move || mods::latest_log(&id, 400)).await
}

// ---------------------------------------------------------------- Modrinth

#[tauri::command]
async fn search(q: modrinth::Query) -> R<modrinth::SearchResult> {
    blocking(move || modrinth::search(&q)).await
}

fn pack_job(app: &AppHandle, inst: Instance, file: impl FnOnce(&ProgressFn) -> anyhow::Result<PathBuf> + Send + 'static) -> R<Instance> {
    let id = inst.id.clone();
    job(app, &id, true, move |app| {
        let p = progress_for(app, &id);
        let path = file(&p)?;
        let mut i = instance::get(&id)?;
        let idx = mrpack::install(&path, &mut i, &p)?;
        let src = i.pack.get_or_insert_with(Default::default);
        src.installed = true;
        if src.version_number.is_none() {
            src.version_number = Some(idx.version_id.clone());
        }
        if src.name.is_none() {
            src.name = Some(idx.name.clone());
        }
        instance::save(&i)
    })?;
    Ok(inst)
}

/// Makes a new instance from a Modrinth modpack. Returns the instance straight away; it fills in
/// as the download goes (watch the events).
#[tauri::command]
async fn install_modpack(app: AppHandle, project: String, title: String, icon_url: Option<String>) -> R<Instance> {
    let inst = blocking(move || {
        let mut i = instance::create(&title, "", Loader::Vanilla, false, "Modpacks")?;
        i.pack = Some(PackSource { kind: "modrinth".into(), project_id: Some(project), name: Some(title), icon_url, ..Default::default() });
        instance::save(&i)?;
        Ok(i)
    })
    .await?;
    let pid = inst.pack.as_ref().and_then(|p| p.project_id.clone()).unwrap_or_default();
    let id = inst.id.clone();
    pack_job(&app, inst, move |p| {
        p(Progress { stage: "Finding the newest version".into(), done: 0, total: 0 });
        let mut vs = modrinth::project_versions(&pid, &["fabric"], &[])?;
        if vs.is_empty() {
            vs = modrinth::project_versions(&pid, &[], &[])?;
        }
        let v = vs.iter().find(|v| v.version_type == "release").or(vs.first()).cloned().ok_or_else(|| anyhow::anyhow!("this modpack has no versions to download"))?;
        let f = v.primary_file().cloned().ok_or_else(|| anyhow::anyhow!("this modpack version has no file"))?;
        if !util::plain_name(&f.filename) || !f.url.starts_with("https://cdn.modrinth.com/") {
            anyhow::bail!("this modpack's file isn't on Modrinth's servers");
        }
        let path = paths::cache().join("packs").join(&f.filename);
        p(Progress { stage: format!("Downloading {}", f.filename), done: 0, total: f.size });
        net::download(&Download::new(f.url.clone(), path.clone(), f.hashes.get("sha1").cloned(), Some(f.size)))?;
        let mut i = instance::get(&id)?;
        if let Some(src) = i.pack.as_mut() {
            src.version_id = Some(v.id.clone());
            src.version_number = Some(v.version_number.clone());
        }
        instance::save(&i)?;
        Ok(path)
    })
}

fn new_pack_instance(path: &std::path::Path) -> anyhow::Result<Instance> {
    let idx = mrpack::read_index(path)?;
    mrpack::loader_of(&idx)?;
    let mut i = instance::create(&idx.name, "", Loader::Vanilla, false, "Modpacks")?;
    i.pack = Some(PackSource { kind: "file".into(), name: Some(idx.name.clone()), version_number: Some(idx.version_id.clone()), ..Default::default() });
    instance::save(&i)?;
    Ok(i)
}

/// Makes an instance from a .mrpack file on disk (dropped onto the window).
#[tauri::command]
async fn import_mrpack(app: AppHandle, path: String) -> R<Instance> {
    let p = PathBuf::from(&path);
    if !path.to_lowercase().ends_with(".mrpack") || !p.is_file() {
        return Err("That isn't a .mrpack file.".into());
    }
    let p2 = p.clone();
    let inst = blocking(move || new_pack_instance(&p2)).await?;
    pack_job(&app, inst, move |_| Ok(p))
}

/// Same, from a file picked in the page (sent as base64).
#[tauri::command]
async fn import_mrpack_data(app: AppHandle, data: String) -> R<Instance> {
    let (inst, path) = blocking(move || {
        let bytes = util::unbase64(data.split(',').next_back().unwrap_or("")).ok_or_else(|| anyhow::anyhow!("couldn't read that file"))?;
        let path = paths::cache().join("packs").join(format!("import-{}.mrpack", util::now()));
        util::write_bytes(&path, &bytes)?;
        Ok((new_pack_instance(&path)?, path))
    })
    .await?;
    pack_job(&app, inst, move |_| Ok(path))
}

// ---------------------------------------------------------------- accounts

#[tauri::command]
fn accounts() -> Vec<auth::AccountInfo> {
    auth::accounts()
}

#[tauri::command]
async fn signin_start(app: AppHandle) -> R<auth::DeviceCode> {
    let dc = blocking(auth::start).await?;
    *app.state::<SignIn>().0.lock().unwrap() = Some(dc.clone());
    Ok(dc)
}

/// Call every few seconds after signin_start. Null = not finished in the browser yet.
#[tauri::command]
async fn signin_poll(app: AppHandle) -> R<Option<auth::AccountInfo>> {
    let dc = app.state::<SignIn>().0.lock().unwrap().clone().ok_or("Start signing in first.")?;
    let r = blocking(move || auth::poll(&dc)).await;
    if !matches!(r, Ok(None)) {
        *app.state::<SignIn>().0.lock().unwrap() = None;
    }
    r
}

#[tauri::command]
fn signin_cancel(app: AppHandle) {
    *app.state::<SignIn>().0.lock().unwrap() = None;
}

#[tauri::command]
async fn set_account(uuid: String) -> R<()> {
    blocking(move || auth::set_active(&uuid)).await
}

#[tauri::command]
async fn sign_out(uuid: String) -> R<()> {
    blocking(move || auth::sign_out(&uuid)).await
}

// ---------------------------------------------------------------- opening things

fn open(target: &std::ffi::OsStr) -> anyhow::Result<()> {
    let prog = if cfg!(windows) {
        "explorer"
    } else if cfg!(target_os = "macos") {
        "open"
    } else {
        "xdg-open"
    };
    std::process::Command::new(prog).arg(target).spawn()?;
    Ok(())
}

/// Opens an instance's folder (or one inside it, or the launcher's data folder) in the file manager.
#[tauri::command]
async fn open_folder(id: Option<String>, sub: Option<String>) -> R<()> {
    blocking(move || {
        let mut p = match id {
            Some(id) => instance::game_dir(&id)?,
            None => paths::root(),
        };
        if let Some(s) = sub {
            if !util::plain_name(&s) {
                anyhow::bail!("bad folder");
            }
            p.push(s);
        }
        std::fs::create_dir_all(&p)?;
        open(p.as_os_str())
    })
    .await
}

const LINK_HOSTS: [&str; 12] = [
    "www.microsoft.com", "microsoft.com", "login.live.com", "modrinth.com", "treasureclient.github.io", "github.com",
    "www.minecraft.net", "minecraft.net", "www.xbox.com", "account.xbox.com", "xbox.com", "account.microsoft.com",
];

/// Opens a web page in the browser (only a few known sites).
#[tauri::command]
fn open_url(url: String) -> R<()> {
    let host = url.strip_prefix("https://").and_then(|r| r.split(['/', '?', '#']).next()).unwrap_or("");
    if !LINK_HOSTS.contains(&host) {
        return Err("The launcher only opens links to known sites.".into());
    }
    open(std::ffi::OsStr::new(&url)).map_err(|e| e.to_string())
}

fn main() {
    tauri::Builder::default()
        .manage(Games::default())
        .manage(SignIn::default())
        .manage(Busy::default())
        .invoke_handler(tauri::generate_handler![
            init, get_settings, set_settings, game_versions, news,
            list_instances, create_instance, save_instance, duplicate_instance, delete_instance, instance_size,
            play, stop, running, busy, repair,
            content, set_content, remove_content, add_content, check_updates, apply_updates, worlds, screenshots, screenshot, latest_log,
            search, install_modpack, import_mrpack, import_mrpack_data,
            accounts, signin_start, signin_poll, signin_cancel, set_account, sign_out,
            open_folder, open_url
        ])
        .run(tauri::generate_context!())
        .expect("Treasure Launcher couldn't start");
}
