//! What's inside an instance: mods, resource packs, shaders, worlds, screenshots and logs.

use std::fs;
use std::io::Read;
use std::path::Path;

use anyhow::{bail, Context, Result};
use serde::{Deserialize, Serialize};

use crate::instance::{self, Instance};
use crate::{modrinth, util};

#[derive(Serialize, Clone, Debug, Default)]
#[serde(rename_all = "camelCase")]
pub struct ContentFile {
    /// The file name on disk (".disabled" at the end means switched off).
    pub file: String,
    pub enabled: bool,
    pub name: String,
    pub version: String,
    pub description: String,
    pub id: String,
    pub size: u64,
}

fn mod_info(path: &Path) -> Option<(String, String, String, String)> {
    let mut z = zip::ZipArchive::new(fs::File::open(path).ok()?).ok()?;
    let mut f = z.by_name("fabric.mod.json").ok()?;
    let mut s = String::new();
    f.by_ref().take(1 << 20).read_to_string(&mut s).ok()?;
    // some mods put raw line breaks inside strings, which strict JSON doesn't allow
    let s = s.replace(['\n', '\r', '\t'], " ");
    let v: serde_json::Value = serde_json::from_str(&s).ok()?;
    let g = |k: &str| v.get(k).and_then(|x| x.as_str()).unwrap_or("").to_string();
    Some((g("id"), g("name"), g("version"), g("description")))
}

/// Everything in one content folder ("mod", "resourcepack" or "shader"), sorted by name.
pub fn list(id: &str, kind: &str) -> Result<Vec<ContentFile>> {
    let dir = instance::game_dir(id)?.join(modrinth::folder(kind)?);
    let mut out = Vec::new();
    let Ok(rd) = fs::read_dir(&dir) else { return Ok(out) };
    for e in rd.flatten() {
        let file = e.file_name().to_string_lossy().into_owned();
        let enabled = !file.ends_with(".disabled");
        let base = file.trim_end_matches(".disabled");
        let is_dir = e.file_type().map(|t| t.is_dir()).unwrap_or(false);
        let ok = match kind {
            "mod" => base.ends_with(".jar") && !is_dir,
            _ => base.ends_with(".zip") || is_dir,
        };
        if !ok || file.starts_with('.') {
            continue;
        }
        let mut c = ContentFile { file: file.clone(), enabled, size: e.metadata().map(|m| m.len()).unwrap_or(0), ..Default::default() };
        let pretty = base.trim_end_matches(".jar").trim_end_matches(".zip").to_string();
        if kind == "mod" {
            if let Some((mid, name, ver, desc)) = mod_info(&e.path()) {
                c.id = mid;
                c.name = if name.is_empty() { pretty.clone() } else { name };
                c.version = ver;
                c.description = desc;
            }
        }
        if c.name.is_empty() {
            c.name = pretty;
        }
        out.push(c);
    }
    out.sort_by_key(|c| c.name.to_lowercase());
    Ok(out)
}

/// Switches a file on or off (adds/removes ".disabled"). Returns the new file name.
pub fn set_enabled(id: &str, kind: &str, file: &str, on: bool) -> Result<String> {
    if !util::plain_name(file) {
        bail!("bad file name");
    }
    let dir = instance::game_dir(id)?.join(modrinth::folder(kind)?);
    let base = file.trim_end_matches(".disabled");
    let new = if on { base.to_string() } else { format!("{base}.disabled") };
    if new != file {
        fs::rename(dir.join(file), dir.join(&new)).with_context(|| format!("couldn't switch {file}"))?;
    }
    Ok(new)
}

pub fn remove(id: &str, kind: &str, file: &str) -> Result<()> {
    if !util::plain_name(file) {
        bail!("bad file name");
    }
    let p = instance::game_dir(id)?.join(modrinth::folder(kind)?).join(file);
    if p.is_dir() {
        fs::remove_dir_all(&p)?;
    } else {
        fs::remove_file(&p).with_context(|| format!("couldn't remove {file}"))?;
    }
    Ok(())
}

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Update {
    pub file: String,
    pub project_id: String,
    pub version_id: String,
    pub version_number: String,
}

/// Files that have a newer version on Modrinth for this instance's Minecraft version.
pub fn updates(inst: &Instance, kind: &str) -> Result<Vec<Update>> {
    let have = modrinth::hashes_in(inst, kind)?;
    let latest = modrinth::latest_by_hash(&have.keys().cloned().collect::<Vec<_>>(), &modrinth::loaders_for(kind, inst.loader), &inst.mc_version)?;
    let mut out = Vec::new();
    for (hash, mut v) in latest {
        let Some(file) = have.get(&hash) else { continue };
        // only offer betas/alphas when the project has no full release for this version
        if v.version_type != "release" {
            if let Ok(r) = modrinth::best_version(&v.project_id, kind, &inst.mc_version, inst.loader) {
                v = r;
            }
        }
        let newer = v.primary_file().and_then(|f| f.hashes.get("sha1")).is_some_and(|h| !h.eq_ignore_ascii_case(&hash));
        if newer {
            out.push(Update { file: file.clone(), project_id: v.project_id.clone(), version_id: v.id.clone(), version_number: v.version_number.clone() });
        }
    }
    out.sort_by(|a, b| a.file.cmp(&b.file));
    Ok(out)
}

/// Swaps a file for its newer version (staying switched off if it was off).
pub fn apply_update(inst: &Instance, kind: &str, u: &Update) -> Result<String> {
    let v = modrinth::version(&u.version_id)?;
    modrinth::install_version(inst, kind, &v, Some(&u.file), u.file.ends_with(".disabled"))
}

#[derive(Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct World {
    /// Folder name (what Quick Play needs).
    pub folder: String,
    pub last_played: u64,
}

pub fn worlds(id: &str) -> Result<Vec<World>> {
    let mut out: Vec<World> = fs::read_dir(instance::game_dir(id)?.join("saves"))
        .map(|rd| {
            rd.flatten()
                .filter(|e| e.path().join("level.dat").exists())
                .map(|e| World {
                    folder: e.file_name().to_string_lossy().into_owned(),
                    last_played: fs::metadata(e.path().join("level.dat")).and_then(|m| m.modified()).ok().and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok()).map(|d| d.as_secs()).unwrap_or(0),
                })
                .collect()
        })
        .unwrap_or_default();
    out.sort_by(|a, b| b.last_played.cmp(&a.last_played));
    Ok(out)
}

/// Screenshot file names, newest first.
pub fn screenshots(id: &str) -> Result<Vec<String>> {
    let mut v: Vec<(std::time::SystemTime, String)> = fs::read_dir(instance::game_dir(id)?.join("screenshots"))
        .map(|rd| {
            rd.flatten()
                .filter(|e| e.file_name().to_string_lossy().ends_with(".png"))
                .map(|e| (e.metadata().and_then(|m| m.modified()).unwrap_or(std::time::UNIX_EPOCH), e.file_name().to_string_lossy().into_owned()))
                .collect()
        })
        .unwrap_or_default();
    v.sort_by(|a, b| b.0.cmp(&a.0));
    Ok(v.into_iter().map(|x| x.1).collect())
}

/// A screenshot as a data: URL the app can show.
pub fn screenshot(id: &str, file: &str) -> Result<String> {
    if !util::plain_name(file) || !file.ends_with(".png") {
        bail!("bad file name");
    }
    let b = fs::read(instance::game_dir(id)?.join("screenshots").join(file))?;
    Ok(format!("data:image/png;base64,{}", util::base64(&b)))
}

/// The last `lines` lines of the game's latest.log.
pub fn latest_log(id: &str, lines: usize) -> Result<Vec<String>> {
    let t = fs::read(instance::game_dir(id)?.join("logs/latest.log")).unwrap_or_default();
    let t = String::from_utf8_lossy(&t);
    let all: Vec<&str> = t.lines().collect();
    Ok(all[all.len().saturating_sub(lines)..].iter().map(|s| s.to_string()).collect())
}
