//! Instances: each one is a folder with its own mods, settings and worlds.
//!
//! instances/<id>/instance.json   what it is (version, loader, play time...)
//! instances/<id>/minecraft/      the game folder (mods, saves, options.txt...)

use std::fs;
use std::path::PathBuf;

use anyhow::{bail, Context, Result};
use serde::{Deserialize, Serialize};

use crate::{paths, util};

#[derive(Serialize, Deserialize, Clone, Copy, Debug, PartialEq, Eq, Default)]
#[serde(rename_all = "lowercase")]
pub enum Loader {
    #[default]
    Vanilla,
    Fabric,
}

/// Where an instance's content came from.
#[derive(Serialize, Deserialize, Clone, Debug, Default)]
#[serde(rename_all = "camelCase")]
pub struct PackSource {
    /// "treasure-pack", "modrinth" or "file"
    pub kind: String,
    #[serde(default)]
    pub project_id: Option<String>,
    #[serde(default)]
    pub version_id: Option<String>,
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub version_number: Option<String>,
    /// The pack's files are in the game folder.
    #[serde(default)]
    pub installed: bool,
    #[serde(default)]
    pub icon_url: Option<String>,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct Instance {
    pub id: String,
    pub name: String,
    pub mc_version: String,
    pub loader: Loader,
    pub loader_version: Option<String>,
    /// Add the Treasure Client mod (and Fabric API).
    pub treasure_client: bool,
    pub group: String,
    pub created: u64,
    pub last_played: u64,
    pub play_seconds: u64,
    pub memory_mb: Option<u32>,
    pub java_path: Option<String>,
    pub jvm_args: Option<String>,
    pub width: Option<u32>,
    pub height: Option<u32>,
    pub pack: Option<PackSource>,
    pub pinned: bool,
}

pub fn dir(id: &str) -> Result<PathBuf> {
    if !util::plain_name(id) {
        bail!("bad instance id");
    }
    Ok(paths::instances().join(id))
}

pub fn game_dir(id: &str) -> Result<PathBuf> {
    Ok(dir(id)?.join("minecraft"))
}

pub fn get(id: &str) -> Result<Instance> {
    let mut i: Instance = util::read_json(&dir(id)?.join("instance.json")).with_context(|| format!("instance {id} is missing"))?;
    i.id = id.to_string();
    Ok(i)
}

pub fn save(i: &Instance) -> Result<()> {
    let d = dir(&i.id)?;
    fs::create_dir_all(d.join("minecraft"))?;
    util::write_json(&d.join("instance.json"), i)
}

/// All instances, most recently played (or made) first.
pub fn list() -> Vec<Instance> {
    let mut out: Vec<Instance> = fs::read_dir(paths::instances())
        .map(|rd| rd.flatten().filter_map(|e| e.file_name().to_str().and_then(|id| get(id).ok())).collect())
        .unwrap_or_default();
    out.sort_by(|a, b| b.last_played.max(b.created).cmp(&a.last_played.max(a.created)).then(a.name.cmp(&b.name)));
    out
}

fn free_id(name: &str) -> String {
    let base = util::slug(name);
    let mut id = base.clone();
    let mut n = 2;
    while paths::instances().join(&id).exists() {
        id = format!("{base}-{n}");
        n += 1;
    }
    id
}

/// Makes a new, empty instance (nothing downloads until it's played).
pub fn create(name: &str, mc: &str, loader: Loader, treasure_client: bool, group: &str) -> Result<Instance> {
    let name = name.trim();
    if name.is_empty() {
        bail!("give the instance a name");
    }
    if treasure_client && loader != Loader::Fabric {
        bail!("Treasure Client needs Fabric");
    }
    let i = Instance {
        id: free_id(name),
        name: name.chars().take(60).collect(),
        mc_version: mc.to_string(),
        loader,
        treasure_client,
        group: if group.is_empty() { "My instances".into() } else { group.into() },
        created: util::now(),
        ..Default::default()
    };
    save(&i)?;
    Ok(i)
}

/// Copies an instance (mods, settings and worlds).
pub fn duplicate(id: &str) -> Result<Instance> {
    let src = get(id)?;
    let mut copy = src.clone();
    copy.name = format!("{} (copy)", src.name);
    copy.id = free_id(&copy.name);
    copy.created = util::now();
    copy.last_played = 0;
    copy.play_seconds = 0;
    copy.pinned = false;
    util::copy_dir(&game_dir(id)?, &game_dir(&copy.id)?)?;
    save(&copy)?;
    Ok(copy)
}

/// Deletes an instance and everything in it.
pub fn delete(id: &str) -> Result<()> {
    let d = dir(id)?;
    if !d.join("instance.json").exists() {
        bail!("instance {id} is missing");
    }
    fs::remove_dir_all(&d).with_context(|| format!("couldn't delete {}", d.display()))
}

/// Adds play time once the game closes.
pub fn played(id: &str, seconds: u64) -> Result<()> {
    let mut i = get(id)?;
    i.last_played = util::now();
    i.play_seconds += seconds;
    save(&i)
}
