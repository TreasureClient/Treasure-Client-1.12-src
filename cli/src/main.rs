//! `tl`: a small command-line tool to test Treasure Launcher's engine without the window.
//! Set TREASURE_LAUNCHER_HOME to use a test folder.

use std::path::Path;
use std::sync::Arc;

use anyhow::{bail, Context, Result};
use treasure_core::instance::{self, Loader};
use treasure_core::launch::{self, Auth, LaunchOptions};
use treasure_core::net::{self, Check};
use treasure_core::{auth, fabric, install, java, modrinth, mods, mojang, mrpack, paths, treasure, Progress, ProgressFn};

const HELP: &str = "tl <command>
  versions                     Minecraft releases
  plan <mc> [--fabric]         what a version needs (files, sizes, Java) without downloading it
  libs <mc> [--fabric]         download only the game jar and libraries
  java <component>             download a Java runtime
  pack-info <file.mrpack>      check a modpack file
  pack-install <file.mrpack>   make an instance from a modpack file
  search <text> [--kind mod|modpack|resourcepack|shader] [--mc 26.3]
  instances                    list instances
  defaults                     make the starting Treasure Pack instances
  create <name> <mc> [--fabric] [--tc]
  mods <id>                    list an instance's mods
  updates <id>                 mods with newer versions
  add <id> <project> [--kind mod]
  cmd <id>                     the launch command (with a fake player, token hidden)
  prepare <id>                 download everything an instance needs
  signin                       Microsoft sign-in
  play <id> [--server host]    start the game";

fn bar() -> ProgressFn {
    Arc::new(|p: Progress| {
        if p.total > 0 {
            eprint!("\r{}: {:.1}/{:.1} MB      ", p.stage, p.done as f64 / 1e6, p.total as f64 / 1e6);
        } else {
            eprint!("\r{}...                    ", p.stage);
        }
    })
}

fn flag(args: &[String], f: &str) -> bool {
    args.iter().any(|a| a == f)
}

fn opt<'a>(args: &'a [String], f: &str) -> Option<&'a str> {
    args.iter().position(|a| a == f).and_then(|i| args.get(i + 1)).map(String::as_str)
}

fn arg(args: &[String], i: usize) -> Result<&str> {
    args.get(i).map(String::as_str).filter(|a| !a.starts_with("--")).with_context(|| format!("missing argument\n\n{HELP}"))
}

fn mb(b: u64) -> String {
    format!("{:.1} MB", b as f64 / 1e6)
}

fn main() {
    if let Err(e) = run() {
        eprintln!("\nerror: {e:#}");
        std::process::exit(1);
    }
}

fn run() -> Result<()> {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let cmd = args.first().map(String::as_str).unwrap_or("help");
    eprintln!("data folder: {}", paths::root().display());
    match cmd {
        "versions" => {
            let m = mojang::manifest()?;
            println!("latest: {} (snapshot {})", m.latest.release, m.latest.snapshot);
            let rel: Vec<&str> = m.versions.iter().filter(|v| v.kind == "release").take(20).map(|v| v.id.as_str()).collect();
            println!("{}", rel.join(", "));
        }
        "plan" | "libs" => {
            let mc = arg(&args, 1)?;
            let loader = if flag(&args, "--fabric") { Some(fabric::latest_loader(mc)?) } else { None };
            let r = install::resolve(mc, loader.as_deref())?;
            let assets = r.downloads.iter().filter(|d| d.path.starts_with(paths::assets().join("objects"))).count();
            let (n, b) = net::missing_bytes(&r.downloads);
            println!("version:   {}", r.version_name);
            println!("main:      {}", r.version.main_class.clone().unwrap_or_default());
            println!("java:      {} (Java {})", r.java_component, r.java_major);
            println!("classpath: {} jars, natives to unpack: {}", r.classpath.len(), r.native_jars.len());
            println!("files:     {} ({} assets), {} still to download ({})", r.downloads.len(), assets, n, mb(b));
            if cmd == "libs" {
                let list: Vec<_> = r.downloads.into_iter().filter(|d| !d.path.starts_with(paths::assets())).collect();
                net::download_all(list, Check::Quick, "Downloading libraries", &bar())?;
                eprintln!();
                let missing = r.classpath.iter().filter(|p| !p.exists()).count();
                println!("classpath jars missing after download: {missing}");
            }
        }
        "java" => {
            let p = java::ensure(arg(&args, 1)?, Check::Quick, &bar())?;
            eprintln!();
            println!("{}", p.display());
        }
        "pack-info" => {
            let idx = mrpack::read_index(Path::new(arg(&args, 1)?))?;
            let (mc, l, lv) = mrpack::loader_of(&idx)?;
            let opt = idx.files.iter().filter(|f| f.env.as_ref().and_then(|e| e.get("client")).is_some_and(|c| c == "optional")).count();
            println!("{} {} for {mc} ({l:?} {})", idx.name, idx.version_id, lv.unwrap_or_default());
            println!("{} files ({} optional), {}", idx.files.len(), opt, mb(idx.files.iter().map(|f| f.file_size).sum()));
        }
        "pack-install" => {
            let file = Path::new(arg(&args, 1)?);
            let idx = mrpack::read_index(file)?;
            let mut i = instance::create(&idx.name, "", Loader::Vanilla, false, "Modpacks")?;
            let r = mrpack::install(file, &mut i, &bar());
            eprintln!();
            if let Err(e) = r {
                let _ = instance::delete(&i.id);
                return Err(e);
            }
            println!("made instance {} ({} {:?}, Treasure Client: {})", i.id, i.mc_version, i.loader, i.treasure_client);
        }
        "search" => {
            let q = modrinth::Query {
                text: args.get(1).filter(|a| !a.starts_with("--")).cloned().unwrap_or_default(),
                kind: opt(&args, "--kind").unwrap_or("modpack").into(),
                mc: opt(&args, "--mc").map(String::from),
                loader: Some("fabric".into()),
                limit: 10,
                ..Default::default()
            };
            let r = modrinth::search(&q)?;
            println!("{} results", r.total_hits);
            for h in r.hits {
                println!("  {:<10} {:<32} {:>10} downloads  {}", h.project_id, h.title, h.downloads, h.description.chars().take(50).collect::<String>());
            }
        }
        "instances" => {
            for i in instance::list() {
                println!("{:<28} {:<26} {:<8} {:?} TC:{} pack:{}", i.id, i.name, i.mc_version, i.loader, i.treasure_client, i.pack.map(|p| p.kind).unwrap_or_default());
            }
        }
        "defaults" => println!("made: {}", treasure::ensure_defaults()?),
        "create" => {
            let fab = flag(&args, "--fabric") || flag(&args, "--tc");
            let i = instance::create(arg(&args, 1)?, arg(&args, 2)?, if fab { Loader::Fabric } else { Loader::Vanilla }, flag(&args, "--tc"), "")?;
            println!("made {}", i.id);
        }
        "mods" => {
            for m in mods::list(arg(&args, 1)?, "mod")? {
                println!("{} {:<34} {:<16} {}", if m.enabled { "on " } else { "off" }, m.name, m.version.chars().take(16).collect::<String>(), m.file);
            }
        }
        "updates" => {
            let i = instance::get(arg(&args, 1)?)?;
            let u = mods::updates(&i, "mod")?;
            println!("{} update(s)", u.len());
            for x in u {
                println!("  {} -> {}", x.file, x.version_number);
            }
        }
        "add" => {
            let i = instance::get(arg(&args, 1)?)?;
            println!("added: {:?}", modrinth::add_project(&i, arg(&args, 2)?, opt(&args, "--kind").unwrap_or("mod"))?);
        }
        "cmd" => {
            let i = instance::get(arg(&args, 1)?)?;
            let lv = match (i.loader, &i.loader_version) {
                (Loader::Fabric, Some(v)) => Some(v.clone()),
                (Loader::Fabric, None) => Some(fabric::latest_loader(&i.mc_version)?),
                _ => None,
            };
            let r = install::resolve(&i.mc_version, lv.as_deref())?;
            let fake = Auth { name: "Player".into(), uuid: "00000000000000000000000000000000".into(), access_token: "SECRET-TOKEN-SHOULD-NOT-SHOW".into(), xuid: String::new(), client_id: String::new() };
            let o = LaunchOptions { server: opt(&args, "--server").map(String::from), world: opt(&args, "--world").map(String::from) };
            let c = launch::build(&i, &r, &java::java_exe(&r.java_component), &fake, &o, 4096)?;
            let text = c.redacted();
            if text.contains("SECRET-TOKEN") {
                bail!("the token showed in the printed command");
            }
            println!("{text}");
        }
        "prepare" => {
            let (_, r, j) = install::prepare(arg(&args, 1)?, Check::Quick, &bar())?;
            eprintln!();
            println!("ready: {} with {}", r.version_name, j.display());
        }
        "signin" => {
            let dc = auth::start()?;
            println!("Go to {} and type {}", dc.verification_uri, dc.user_code);
            loop {
                std::thread::sleep(std::time::Duration::from_secs(dc.interval));
                if let Some(a) = auth::poll(&dc)? {
                    println!("signed in as {}", a.name);
                    break;
                }
            }
        }
        "play" => {
            let id = arg(&args, 1)?;
            let a = auth::active_auth()?;
            let (i, r, j) = install::prepare(id, Check::Quick, &bar())?;
            eprintln!();
            let o = LaunchOptions { server: opt(&args, "--server").map(String::from), world: None };
            let c = launch::build(&i, &r, &j, &a, &o, i.memory_mb.unwrap_or(4096))?;
            let (tx, rx) = std::sync::mpsc::channel();
            let id2 = id.to_string();
            launch::spawn(
                &c,
                Arc::new(|l: &str, _| println!("{l}")),
                Box::new(move |code, secs| {
                    let _ = instance::played(&id2, secs);
                    let _ = tx.send(code);
                }),
            )?;
            println!("game closed: {:?}", rx.recv()?);
        }
        _ => println!("{HELP}"),
    }
    Ok(())
}
