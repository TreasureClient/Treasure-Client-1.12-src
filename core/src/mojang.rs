//! Mojang's version list and version files (what to download and how to start each version).

use std::collections::{HashMap, HashSet};
use std::sync::Mutex;
use std::time::{Duration, Instant};

use anyhow::{bail, Context, Result};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::{net, paths, util};

pub const MANIFEST_URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct Manifest {
    pub latest: Latest,
    pub versions: Vec<ManifestEntry>,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct Latest {
    pub release: String,
    pub snapshot: String,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct ManifestEntry {
    pub id: String,
    #[serde(rename = "type")]
    pub kind: String,
    pub url: String,
    #[serde(rename = "releaseTime", default)]
    pub release_time: String,
    #[serde(default)]
    pub sha1: Option<String>,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
#[serde(rename_all = "camelCase")]
pub struct VersionJson {
    pub id: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub inherits_from: Option<String>,
    #[serde(rename = "type", default)]
    pub kind: Option<String>,
    #[serde(default)]
    pub main_class: Option<String>,
    #[serde(default)]
    pub arguments: Option<Arguments>,
    #[serde(default)]
    pub minecraft_arguments: Option<String>,
    #[serde(default)]
    pub asset_index: Option<AssetIndexRef>,
    #[serde(default)]
    pub assets: Option<String>,
    #[serde(default)]
    pub downloads: Option<HashMap<String, FileRef>>,
    #[serde(default)]
    pub java_version: Option<JavaVersion>,
    #[serde(default)]
    pub libraries: Vec<Library>,
    #[serde(default)]
    pub logging: Option<Logging>,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct Arguments {
    #[serde(default)]
    pub game: Vec<Value>,
    #[serde(default)]
    pub jvm: Vec<Value>,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct AssetIndexRef {
    pub id: String,
    pub sha1: String,
    pub size: u64,
    pub url: String,
    #[serde(rename = "totalSize", default)]
    pub total_size: u64,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct FileRef {
    #[serde(default)]
    pub path: Option<String>,
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default)]
    pub size: Option<u64>,
    #[serde(default)]
    pub url: String,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct JavaVersion {
    pub component: String,
    pub major_version: u32,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct Library {
    pub name: String,
    #[serde(default)]
    pub downloads: Option<LibDownloads>,
    /// Maven repository (Fabric's libraries give this instead of `downloads`).
    #[serde(default)]
    pub url: Option<String>,
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default)]
    pub size: Option<u64>,
    /// Old versions only: which classifier holds this system's native files.
    #[serde(default)]
    pub natives: Option<HashMap<String, String>>,
    #[serde(default)]
    pub rules: Option<Vec<Rule>>,
    #[serde(default)]
    pub extract: Option<Extract>,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct LibDownloads {
    #[serde(default)]
    pub artifact: Option<FileRef>,
    #[serde(default)]
    pub classifiers: Option<HashMap<String, FileRef>>,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct Extract {
    #[serde(default)]
    pub exclude: Vec<String>,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct Rule {
    pub action: String,
    #[serde(default)]
    pub os: Option<OsRule>,
    #[serde(default)]
    pub features: Option<HashMap<String, bool>>,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct OsRule {
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub arch: Option<String>,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct Logging {
    #[serde(default)]
    pub client: Option<LoggingClient>,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct LoggingClient {
    pub argument: String,
    pub file: LogFile,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct LogFile {
    pub id: String,
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

#[derive(Deserialize, Clone, Debug)]
pub struct AssetIndex {
    pub objects: HashMap<String, AssetObject>,
}

#[derive(Deserialize, Clone, Debug)]
pub struct AssetObject {
    pub hash: String,
    pub size: u64,
}

/// Launch options that version files can switch arguments on and off with.
#[derive(Default, Clone, Debug)]
pub struct Features {
    pub custom_resolution: bool,
    pub quick_play_multiplayer: bool,
    pub quick_play_singleplayer: bool,
}

impl Features {
    fn has(&self, k: &str) -> bool {
        match k {
            "has_custom_resolution" => self.custom_resolution,
            "is_quick_play_multiplayer" => self.quick_play_multiplayer,
            "is_quick_play_singleplayer" => self.quick_play_singleplayer,
            _ => false,
        }
    }
}

fn arch_matches(a: &str) -> bool {
    match a {
        "x86" => cfg!(target_arch = "x86"),
        "x86_64" | "amd64" => cfg!(target_arch = "x86_64"),
        "arm64" | "aarch64" => cfg!(target_arch = "aarch64"),
        _ => false,
    }
}

impl Rule {
    fn matches(&self, f: &Features) -> bool {
        if let Some(os) = &self.os {
            if os.name.as_deref().is_some_and(|n| n != util::os_name()) {
                return false;
            }
            if os.arch.as_deref().is_some_and(|a| !arch_matches(a)) {
                return false;
            }
        }
        if let Some(fs) = &self.features {
            if fs.iter().any(|(k, v)| f.has(k) != *v) {
                return false;
            }
        }
        true
    }
}

/// Mojang's rule lists: no rules = allowed; otherwise the last matching rule decides.
pub fn rules_allow(rules: Option<&Vec<Rule>>, f: &Features) -> bool {
    match rules {
        None => true,
        Some(r) if r.is_empty() => true,
        Some(r) => r.iter().filter(|x| x.matches(f)).last().is_some_and(|x| x.action == "allow"),
    }
}

/// Turns a version file's argument list into plain strings for this computer.
pub fn expand_args(list: &[Value], f: &Features) -> Vec<String> {
    let mut out = Vec::new();
    for v in list {
        match v {
            Value::String(s) => out.push(s.clone()),
            Value::Object(o) => {
                let rules: Option<Vec<Rule>> = o.get("rules").and_then(|r| serde_json::from_value(r.clone()).ok());
                if !rules_allow(rules.as_ref(), f) {
                    continue;
                }
                match o.get("value") {
                    Some(Value::String(s)) => out.push(s.clone()),
                    Some(Value::Array(a)) => out.extend(a.iter().filter_map(|x| x.as_str().map(String::from))),
                    _ => {}
                }
            }
            _ => {}
        }
    }
    out
}

static MANIFEST: Mutex<Option<(Instant, Manifest)>> = Mutex::new(None);

/// Mojang's list of every version (kept for 10 minutes; the saved copy is used when offline).
pub fn manifest() -> Result<Manifest> {
    if let Some((t, m)) = MANIFEST.lock().unwrap().as_ref() {
        if t.elapsed() < Duration::from_secs(600) {
            return Ok(m.clone());
        }
    }
    let path = paths::meta().join("version_manifest_v2.json");
    let m: Manifest = match net::get_text(MANIFEST_URL) {
        Ok(t) => {
            let m = serde_json::from_str(&t).context("Mojang's version list looks wrong")?;
            let _ = util::write_bytes(&path, t.as_bytes());
            m
        }
        Err(e) => {
            if !path.exists() {
                return Err(e.context("can't reach Mojang (are you online?)"));
            }
            util::read_json(&path)?
        }
    };
    *MANIFEST.lock().unwrap() = Some((Instant::now(), m.clone()));
    Ok(m)
}

/// The version file for a Minecraft version, downloaded once and then read from disk.
pub fn vanilla(id: &str) -> Result<VersionJson> {
    if id.is_empty() || !util::plain_name(id) {
        bail!("'{id}' isn't a Minecraft version");
    }
    let path = paths::meta().join("versions").join(format!("{id}.json"));
    if path.exists() {
        if let Ok(v) = util::read_json::<VersionJson>(&path) {
            return Ok(v);
        }
    }
    let m = manifest()?;
    let e = m.versions.iter().find(|v| v.id == id).with_context(|| format!("Minecraft {id} doesn't exist"))?;
    let text = net::get_text(&e.url)?;
    if let Some(sha) = &e.sha1 {
        if !util::sha1_bytes(text.as_bytes()).eq_ignore_ascii_case(sha) {
            bail!("the version file for {id} failed its checksum");
        }
    }
    let v: VersionJson = serde_json::from_str(&text).with_context(|| format!("the version file for {id} looks wrong"))?;
    util::write_bytes(&path, text.as_bytes())?;
    Ok(v)
}

/// Puts a loader's version file (Fabric) on top of the Minecraft one it builds on.
pub fn merge(child: VersionJson, parent: VersionJson) -> VersionJson {
    let child_keys: HashSet<String> = child.libraries.iter().map(|l| util::lib_key(&l.name)).collect();
    let mut libraries = child.libraries;
    libraries.extend(parent.libraries.into_iter().filter(|l| !child_keys.contains(&util::lib_key(&l.name))));
    let arguments = match (parent.arguments, child.arguments) {
        (Some(mut p), Some(c)) => {
            p.game.extend(c.game);
            p.jvm.extend(c.jvm);
            Some(p)
        }
        (p, c) => p.or(c),
    };
    VersionJson {
        id: child.id,
        inherits_from: None,
        kind: child.kind.or(parent.kind),
        main_class: child.main_class.or(parent.main_class),
        arguments,
        minecraft_arguments: child.minecraft_arguments.or(parent.minecraft_arguments),
        asset_index: child.asset_index.or(parent.asset_index),
        assets: child.assets.or(parent.assets),
        downloads: child.downloads.or(parent.downloads),
        java_version: child.java_version.or(parent.java_version),
        libraries,
        logging: child.logging.or(parent.logging),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rules() {
        let f = Features::default();
        let r: Vec<Rule> = serde_json::from_str(r#"[{"action":"allow"},{"action":"disallow","os":{"name":"nope"}}]"#).unwrap();
        assert!(rules_allow(Some(&r), &f));
        let r: Vec<Rule> = serde_json::from_str(&format!(r#"[{{"action":"allow","os":{{"name":"{}"}}}}]"#, util::os_name())).unwrap();
        assert!(rules_allow(Some(&r), &f));
        let r: Vec<Rule> = serde_json::from_str(r#"[{"action":"allow","os":{"name":"nope"}}]"#).unwrap();
        assert!(!rules_allow(Some(&r), &f));
        let args: Vec<Value> = serde_json::from_str(r#"["--a",{"rules":[{"action":"allow","features":{"has_custom_resolution":true}}],"value":["--width","${resolution_width}"]}]"#).unwrap();
        assert_eq!(expand_args(&args, &f), vec!["--a"]);
        let f2 = Features { custom_resolution: true, ..Default::default() };
        assert_eq!(expand_args(&args, &f2).len(), 3);
    }

    #[test]
    fn merging() {
        let lib = |n: &str| Library { name: n.into(), ..Default::default() };
        let parent = VersionJson { id: "1.0".into(), main_class: Some("Vanilla".into()), libraries: vec![lib("org.ow2.asm:asm:9.0"), lib("a:b:1")], ..Default::default() };
        let child = VersionJson { id: "fabric".into(), main_class: Some("Knot".into()), libraries: vec![lib("org.ow2.asm:asm:9.8")], ..Default::default() };
        let m = merge(child, parent);
        assert_eq!(m.main_class.as_deref(), Some("Knot"));
        assert_eq!(m.libraries.iter().map(|l| l.name.as_str()).collect::<Vec<_>>(), vec!["org.ow2.asm:asm:9.8", "a:b:1"]);
    }
}
