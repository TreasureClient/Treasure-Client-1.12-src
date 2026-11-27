//! Treasure Client things: the Treasure Client mod, the Treasure Pack, news and the two
//! instances every new launcher starts with.
//!
//! Where the files are is listed in one small file on the website (CHANNEL_URL), so new versions
//! go out without a launcher update:
//! {
//!   "treasure_client": { "26.3": { "version": "1.5.0", "url": "https://...jar", "sha1": "..." }, ... },
//!   "treasure_pack":   { "26.3": { "version": "1.0.0", "url": "https://...mrpack", "sha1": "..." }, ... },
//!   "news": [ { "title": "...", "text": "...", "tag": "Update" } ],
//!   "launcher": { "version": "0.1.0", "page": "https://treasureclient.github.io/launcher" }
//! }

use std::collections::HashMap;
use std::fs;

use anyhow::{bail, Context, Result};
use serde::{Deserialize, Serialize};

use crate::instance::{self, Instance, Loader, PackSource};
use crate::net::{self, Download};
use crate::{modrinth, mrpack, paths, report, settings, util, ProgressFn};

pub const CHANNEL_URL: &str = "https://treasureclient.github.io/launcher/channel.json";

/// Minecraft versions Treasure Client is made for.
pub const SUPPORTED: [&str; 5] = ["26.3", "26.2", "26.1.2", "26.1", "1.21.11"];

/// The instances a new launcher starts with (downloaded the first time you press Play).
pub const DEFAULT_PACKS: [&str; 2] = ["26.3", "1.21.11"];

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
#[serde(default)]
pub struct Channel {
    pub treasure_client: HashMap<String, Asset>,
    pub treasure_pack: HashMap<String, Asset>,
    pub news: Vec<News>,
    pub launcher: Option<LauncherInfo>,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct Asset {
    pub version: String,
    pub url: String,
    pub sha1: String,
    #[serde(default)]
    pub size: Option<u64>,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
#[serde(default)]
pub struct News {
    pub title: String,
    pub text: String,
    pub tag: String,
    pub url: Option<String>,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
#[serde(default)]
pub struct LauncherInfo {
    pub version: String,
    pub page: String,
}

pub fn supported(mc: &str) -> bool {
    SUPPORTED.contains(&mc)
}

/// The channel file (the saved copy is used when offline). $TREASURE_CHANNEL can point at a
/// local file for testing.
pub fn channel() -> Result<Channel> {
    let cached = paths::meta().join("channel.json");
    if let Ok(p) = std::env::var("TREASURE_CHANNEL") {
        if !p.starts_with("https://") {
            return util::read_json(std::path::Path::new(&p));
        }
    }
    let url = std::env::var("TREASURE_CHANNEL").unwrap_or_else(|_| CHANNEL_URL.to_string());
    match net::get_text(&url) {
        Ok(t) => {
            let c: Channel = serde_json::from_str(&t).context("Treasure Client's download list looks wrong")?;
            let _ = util::write_bytes(&cached, t.as_bytes());
            Ok(c)
        }
        Err(e) => {
            if cached.exists() {
                util::read_json(&cached)
            } else {
                Err(e.context("can't reach the Treasure Client website"))
            }
        }
    }
}

fn file_name(url: &str) -> Result<String> {
    let n = url.rsplit('/').next().unwrap_or("").split(['?', '#']).next().unwrap_or("").replace("%2B", "+").replace("%2b", "+");
    if !util::plain_name(&n) {
        bail!("bad download name");
    }
    Ok(n)
}

fn fetch_asset(a: &Asset, dest: std::path::PathBuf) -> Result<()> {
    if !a.url.starts_with("https://") {
        bail!("Treasure Client downloads must use https");
    }
    net::download(&Download::new(a.url.clone(), dest, Some(a.sha1.clone()), a.size))
}

/// Has a Treasure Client jar in its mods folder (switched on or off).
pub fn has_tc(inst: &Instance) -> Result<bool> {
    let dir = instance::game_dir(&inst.id)?.join("mods");
    Ok(fs::read_dir(dir).map(|rd| rd.flatten().any(|e| e.file_name().to_string_lossy().starts_with("treasure-client"))).unwrap_or(false))
}

/// Adds the newest Treasure Client for the instance's version, plus Fabric API if it's missing.
pub fn install_tc(inst: &Instance, progress: &ProgressFn) -> Result<()> {
    if inst.loader != Loader::Fabric {
        bail!("Treasure Client needs Fabric");
    }
    if !supported(&inst.mc_version) {
        bail!("Treasure Client isn't made for Minecraft {}", inst.mc_version);
    }
    report(progress, "Adding Treasure Client", 0, 0);
    let ch = channel()?;
    let a = ch.treasure_client.get(&inst.mc_version).with_context(|| format!("Treasure Client for {} isn't out yet", inst.mc_version))?;
    let dir = instance::game_dir(&inst.id)?.join("mods");
    fs::create_dir_all(&dir)?;
    let name = file_name(&a.url)?;
    if !name.starts_with("treasure-client") || !name.ends_with(".jar") {
        bail!("unexpected Treasure Client file name");
    }
    fetch_asset(a, dir.join(&name))?;
    // take out older versions
    for e in fs::read_dir(&dir)?.flatten() {
        let n = e.file_name().to_string_lossy().into_owned();
        if n.starts_with("treasure-client") && n != name {
            let _ = fs::remove_file(e.path());
        }
    }
    // Fabric API, from Modrinth
    let have = modrinth::hashes_in(inst, "mod")?;
    let known = modrinth::versions_by_hash(&have.keys().cloned().collect::<Vec<_>>())?;
    if !known.values().any(|v| v.project_id == modrinth::FABRIC_API) {
        report(progress, "Adding Fabric API", 0, 0);
        modrinth::add_project(inst, modrinth::FABRIC_API, "mod")?;
    }
    Ok(())
}

/// Takes Treasure Client out of an instance.
pub fn remove_tc(inst: &Instance) -> Result<()> {
    let dir = instance::game_dir(&inst.id)?.join("mods");
    if let Ok(rd) = fs::read_dir(&dir) {
        for e in rd.flatten() {
            if e.file_name().to_string_lossy().starts_with("treasure-client") {
                fs::remove_file(e.path())?;
            }
        }
    }
    Ok(())
}

/// Downloads and installs the Treasure Pack into a Treasure Pack instance.
pub fn install_pack(inst: &mut Instance, progress: &ProgressFn) -> Result<()> {
    report(progress, "Getting the Treasure Pack", 0, 0);
    let ch = channel()?;
    let a = ch.treasure_pack.get(&inst.mc_version).with_context(|| format!("the Treasure Pack for {} isn't out yet", inst.mc_version))?.clone();
    let name = file_name(&a.url)?;
    let file = paths::cache().join("packs").join(&name);
    fetch_asset(&a, file.clone())?;
    mrpack::install(&file, inst, progress)?;
    let p = inst.pack.get_or_insert_with(Default::default);
    p.kind = "treasure-pack".into();
    p.installed = true;
    p.version_number = Some(a.version);
    instance::save(inst)
}

/// Makes the starting Treasure Pack instances the first time the launcher opens.
pub fn ensure_defaults() -> Result<bool> {
    let mut s = settings::load();
    if s.defaults_created {
        return Ok(false);
    }
    if instance::list().is_empty() {
        for mc in DEFAULT_PACKS {
            let mut i = instance::create(&format!("Treasure Pack {mc}"), mc, Loader::Fabric, true, "Treasure Packs")?;
            i.pinned = true;
            i.pack = Some(PackSource { kind: "treasure-pack".into(), name: Some("Treasure Pack".into()), ..Default::default() });
            instance::save(&i)?;
        }
    }
    s.defaults_created = true;
    settings::save(&s)?;
    Ok(true)
}
