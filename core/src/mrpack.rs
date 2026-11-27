//! Modrinth modpacks (.mrpack files): a list of files to download plus "overrides" (configs,
//! and in the Treasure Pack the Treasure Client jar) copied straight into the game folder.

use std::collections::HashMap;
use std::fs;
use std::io::Read;
use std::path::Path;

use anyhow::{bail, Context, Result};
use serde::Deserialize;

use crate::instance::{self, Instance, Loader};
use crate::net::{self, Check, Download};
use crate::{report, util, ProgressFn};

/// Modrinth's rule: pack files may only download from these sites.
const ALLOWED_HOSTS: [&str; 4] = ["cdn.modrinth.com", "github.com", "raw.githubusercontent.com", "gitlab.com"];

#[derive(Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct PackIndex {
    pub format_version: u32,
    pub game: String,
    #[serde(default)]
    pub version_id: String,
    pub name: String,
    #[serde(default)]
    pub summary: Option<String>,
    #[serde(default)]
    pub files: Vec<PackFile>,
    #[serde(default)]
    pub dependencies: HashMap<String, String>,
}

#[derive(Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct PackFile {
    pub path: String,
    #[serde(default)]
    pub hashes: HashMap<String, String>,
    #[serde(default)]
    pub env: Option<HashMap<String, String>>,
    #[serde(default)]
    pub downloads: Vec<String>,
    #[serde(default)]
    pub file_size: u64,
}

fn host(url: &str) -> Option<&str> {
    let rest = url.strip_prefix("https://")?;
    let auth = rest.split(['/', '?', '#']).next()?;
    if auth.contains('@') {
        return None;
    }
    Some(auth.split(':').next().unwrap_or(auth))
}

pub fn read_index(path: &Path) -> Result<PackIndex> {
    let mut z = zip::ZipArchive::new(fs::File::open(path)?).context("this isn't a modpack file (.mrpack)")?;
    let mut f = z.by_name("modrinth.index.json").context("this modpack has no modrinth.index.json")?;
    let mut s = String::new();
    f.by_ref().take(32 << 20).read_to_string(&mut s)?;
    let idx: PackIndex = serde_json::from_str(&s).context("this modpack's file list looks wrong")?;
    if idx.game != "minecraft" {
        bail!("this modpack isn't for Minecraft");
    }
    if idx.format_version != 1 {
        bail!("this modpack uses a newer format than the launcher knows");
    }
    Ok(idx)
}

/// Minecraft version, loader and loader version a pack asks for.
pub fn loader_of(idx: &PackIndex) -> Result<(String, Loader, Option<String>)> {
    let mc = idx.dependencies.get("minecraft").cloned().context("the modpack doesn't say which Minecraft version it's for")?;
    for other in ["forge", "neoforge", "quilt-loader"] {
        if idx.dependencies.contains_key(other) {
            let name = match other {
                "forge" => "Forge",
                "neoforge" => "NeoForge",
                _ => "Quilt",
            };
            bail!("this modpack needs {name}. Treasure Launcher runs Fabric and vanilla packs for now.");
        }
    }
    Ok(match idx.dependencies.get("fabric-loader") {
        Some(v) => (mc, Loader::Fabric, Some(v.clone())),
        None => (mc, Loader::Vanilla, None),
    })
}

/// Installs a pack into an instance (sets its version and loader too). Optional mods are added
/// switched off, so they show in the instance's Mods list ready to turn on.
pub fn install(path: &Path, inst: &mut Instance, progress: &ProgressFn) -> Result<PackIndex> {
    let idx = read_index(path)?;
    let (mc, loader, lv) = loader_of(&idx)?;
    inst.mc_version = mc;
    inst.loader = loader;
    inst.loader_version = lv;
    let gd = instance::game_dir(&inst.id)?;
    let mut list = Vec::new();
    for f in &idx.files {
        let client = f.env.as_ref().and_then(|e| e.get("client")).map(String::as_str).unwrap_or("required");
        if client == "unsupported" {
            continue;
        }
        let mut rel = f.path.clone();
        if client == "optional" {
            if !(rel.starts_with("mods/") && rel.ends_with(".jar")) {
                continue;
            }
            // already switched on by you: leave it
            if util::safe_join(&gd, &rel).is_some_and(|p| p.exists()) {
                continue;
            }
            rel.push_str(".disabled");
        }
        let dest = util::safe_join(&gd, &rel).with_context(|| format!("the modpack has an unsafe file path: {}", f.path))?;
        let url = f
            .downloads
            .iter()
            .find(|u| host(u).is_some_and(|h| ALLOWED_HOSTS.contains(&h)))
            .with_context(|| format!("{} doesn't download from a site Modrinth allows", f.path))?;
        let sha1 = f.hashes.get("sha1").cloned().with_context(|| format!("{} has no checksum", f.path))?;
        list.push(Download::new(url.clone(), dest, Some(sha1), Some(f.file_size).filter(|s| *s > 0)));
    }
    net::download_all(list, Check::Quick, &format!("Downloading {}", idx.name), progress)?;
    report(progress, &format!("Unpacking {}", idx.name), 0, 0);
    extract_overrides(path, &gd)?;
    if fs::read_dir(gd.join("mods")).map(|rd| rd.flatten().any(|e| e.file_name().to_string_lossy().starts_with("treasure-client"))).unwrap_or(false) {
        inst.treasure_client = true;
    }
    instance::save(inst)?;
    Ok(idx)
}

fn extract_overrides(path: &Path, gd: &Path) -> Result<()> {
    let mut z = zip::ZipArchive::new(fs::File::open(path)?)?;
    for prefix in ["overrides/", "client-overrides/"] {
        for i in 0..z.len() {
            let mut f = z.by_index(i)?;
            let Some(rel) = f.name().strip_prefix(prefix).map(String::from) else { continue };
            if f.is_dir() || rel.is_empty() {
                continue;
            }
            let out = util::safe_join(gd, &rel).with_context(|| format!("the modpack has an unsafe file path: {}", f.name()))?;
            if let Some(p) = out.parent() {
                fs::create_dir_all(p)?;
            }
            let mut buf = Vec::new();
            f.by_ref().take(1 << 30).read_to_end(&mut buf)?;
            fs::write(&out, buf)?;
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    #[test]
    fn hosts() {
        assert_eq!(super::host("https://cdn.modrinth.com/data/x.jar"), Some("cdn.modrinth.com"));
        assert_eq!(super::host("https://evil.com@cdn.modrinth.com/x"), None);
        assert_eq!(super::host("http://cdn.modrinth.com/x"), None);
        assert_eq!(super::host("https://github.com:443/x"), Some("github.com"));
    }
}
