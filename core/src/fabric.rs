//! Fabric Loader, from Fabric's own server (meta.fabricmc.net).

use anyhow::{bail, Context, Result};
use serde::Deserialize;

use crate::mojang::VersionJson;
use crate::{net, paths, util};

const META: &str = "https://meta.fabricmc.net/v2";

#[derive(Deserialize, Clone, Debug)]
pub struct LoaderEntry {
    pub loader: LoaderInfo,
}

#[derive(Deserialize, Clone, Debug)]
pub struct LoaderInfo {
    pub version: String,
    #[serde(default)]
    pub stable: bool,
}

/// Every Fabric Loader build for a Minecraft version, newest first (empty if Fabric doesn't support it).
pub fn loaders(mc: &str) -> Result<Vec<LoaderEntry>> {
    if !util::plain_name(mc) {
        bail!("'{mc}' isn't a Minecraft version");
    }
    net::get_json(&format!("{META}/versions/loader/{mc}"))
}

/// The newest stable Fabric Loader for `mc`.
pub fn latest_loader(mc: &str) -> Result<String> {
    let l = loaders(mc)?;
    l.iter()
        .find(|e| e.loader.stable)
        .or(l.first())
        .map(|e| e.loader.version.clone())
        .with_context(|| format!("Fabric doesn't support Minecraft {mc} yet"))
}

/// Fabric's version file for `mc` + `loader` (it builds on Mojang's, see mojang::merge).
pub fn profile(mc: &str, loader: &str) -> Result<VersionJson> {
    if !util::plain_name(mc) || !util::plain_name(loader) {
        bail!("bad Fabric version");
    }
    let path = paths::meta().join("fabric").join(format!("{mc}-{loader}.json"));
    if path.exists() {
        if let Ok(v) = util::read_json::<VersionJson>(&path) {
            return Ok(v);
        }
    }
    let text = net::get_text(&format!("{META}/versions/loader/{mc}/{loader}/profile/json"))?;
    let v: VersionJson = serde_json::from_str(&text).context("Fabric's version file looks wrong")?;
    util::write_bytes(&path, text.as_bytes())?;
    Ok(v)
}
