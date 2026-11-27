//! Modrinth: search modpacks/mods/resource packs/shaders and install them. Files always download
//! from Modrinth's own servers (the author's page), never from a copy of ours.

use std::collections::{HashMap, HashSet};
use std::fs;

use anyhow::{bail, Context, Result};
use serde::{Deserialize, Serialize};
use serde_json::json;

use crate::instance::{self, Instance, Loader};
use crate::net::{self, Download};
use crate::util;

pub const API: &str = "https://api.modrinth.com/v2";
/// Fabric API's project id on Modrinth.
pub const FABRIC_API: &str = "P7dR8mSH";

/// Percent-encodes a URL query value.
pub fn enc(s: &str) -> String {
    let mut o = String::with_capacity(s.len() * 3);
    for b in s.bytes() {
        if b.is_ascii_alphanumeric() || b"-_.~".contains(&b) {
            o.push(b as char);
        } else {
            o.push_str(&format!("%{b:02X}"));
        }
    }
    o
}

fn valid_id(id: &str) -> bool {
    !id.is_empty() && id.len() <= 64 && id.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct SearchHit {
    pub project_id: String,
    pub slug: String,
    pub title: String,
    #[serde(default)]
    pub description: String,
    #[serde(default)]
    pub icon_url: Option<String>,
    #[serde(default)]
    pub downloads: u64,
    #[serde(default)]
    pub follows: u64,
    #[serde(default)]
    pub categories: Vec<String>,
    #[serde(default)]
    pub display_categories: Vec<String>,
    #[serde(default)]
    pub author: String,
    #[serde(default)]
    pub project_type: String,
    #[serde(default)]
    pub versions: Vec<String>,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct SearchResult {
    pub hits: Vec<SearchHit>,
    #[serde(default)]
    pub offset: u32,
    #[serde(default)]
    pub limit: u32,
    #[serde(default)]
    pub total_hits: u64,
}

#[derive(Deserialize, Clone, Debug, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct Query {
    pub text: String,
    /// "modpack", "mod", "resourcepack" or "shader"
    pub kind: String,
    pub mc: Option<String>,
    pub loader: Option<String>,
    pub categories: Vec<String>,
    /// "relevance", "downloads", "follows", "newest" or "updated"
    pub sort: String,
    pub offset: u32,
    pub limit: u32,
}

pub fn search(q: &Query) -> Result<SearchResult> {
    let kind = match q.kind.as_str() {
        "modpack" | "mod" | "resourcepack" | "shader" => q.kind.as_str(),
        _ => bail!("unknown kind of content"),
    };
    let mut facets: Vec<Vec<String>> = vec![vec![format!("project_type:{kind}")]];
    if let Some(v) = q.mc.as_ref().filter(|v| !v.is_empty()) {
        facets.push(vec![format!("versions:{v}")]);
    }
    if let Some(l) = q.loader.as_ref().filter(|l| !l.is_empty()) {
        if kind == "mod" || kind == "modpack" {
            facets.push(vec![format!("categories:{l}")]);
        }
    }
    for c in &q.categories {
        facets.push(vec![format!("categories:{c}")]);
    }
    if kind == "mod" || kind == "modpack" {
        facets.push(vec!["client_side:required".into(), "client_side:optional".into()]);
    }
    let sort = match q.sort.as_str() {
        "downloads" | "follows" | "newest" | "updated" => q.sort.as_str(),
        _ => "relevance",
    };
    let url = format!(
        "{API}/search?query={}&facets={}&index={sort}&offset={}&limit={}",
        enc(&q.text),
        enc(&serde_json::to_string(&facets)?),
        q.offset,
        q.limit.clamp(1, 50)
    );
    net::get_json(&url)
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct Version {
    pub id: String,
    pub project_id: String,
    #[serde(default)]
    pub name: String,
    #[serde(default)]
    pub version_number: String,
    #[serde(default)]
    pub version_type: String,
    #[serde(default)]
    pub game_versions: Vec<String>,
    #[serde(default)]
    pub loaders: Vec<String>,
    #[serde(default)]
    pub files: Vec<VersionFile>,
    #[serde(default)]
    pub dependencies: Vec<Dependency>,
    #[serde(default)]
    pub date_published: String,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct VersionFile {
    pub url: String,
    pub filename: String,
    #[serde(default)]
    pub primary: bool,
    #[serde(default)]
    pub size: u64,
    #[serde(default)]
    pub hashes: HashMap<String, String>,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct Dependency {
    #[serde(default)]
    pub project_id: Option<String>,
    #[serde(default)]
    pub version_id: Option<String>,
    #[serde(default)]
    pub dependency_type: String,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct Project {
    pub id: String,
    pub slug: String,
    pub title: String,
    #[serde(default)]
    pub description: String,
    #[serde(default)]
    pub icon_url: Option<String>,
    #[serde(default)]
    pub project_type: String,
}

impl Version {
    pub fn primary_file(&self) -> Option<&VersionFile> {
        self.files.iter().find(|f| f.primary).or(self.files.first())
    }
}

pub fn version(id: &str) -> Result<Version> {
    if !valid_id(id) {
        bail!("bad Modrinth version id");
    }
    net::get_json(&format!("{API}/version/{id}"))
}

pub fn project(id: &str) -> Result<Project> {
    if !valid_id(id) {
        bail!("bad Modrinth project id");
    }
    net::get_json(&format!("{API}/project/{id}"))
}

pub fn projects(ids: &[String]) -> Result<Vec<Project>> {
    let ids: Vec<&String> = ids.iter().filter(|i| valid_id(i)).collect();
    if ids.is_empty() {
        return Ok(vec![]);
    }
    net::get_json(&format!("{API}/projects?ids={}", enc(&serde_json::to_string(&ids)?)))
}

/// A project's versions for these loaders and Minecraft versions, newest first.
pub fn project_versions(project: &str, loaders: &[&str], mc: &[&str]) -> Result<Vec<Version>> {
    if !valid_id(project) {
        bail!("bad Modrinth project id");
    }
    let mut url = format!("{API}/project/{project}/version?include_changelog=false");
    if !loaders.is_empty() {
        url += &format!("&loaders={}", enc(&serde_json::to_string(loaders)?));
    }
    if !mc.is_empty() {
        url += &format!("&game_versions={}", enc(&serde_json::to_string(mc)?));
    }
    net::get_json(&url)
}

/// Which loaders to ask Modrinth for, per kind of content.
pub fn loaders_for(kind: &str, loader: Loader) -> Vec<&'static str> {
    match kind {
        "resourcepack" => vec!["minecraft"],
        "shader" => vec!["iris", "optifine"],
        _ => match loader {
            Loader::Fabric => vec!["fabric"],
            Loader::Vanilla => vec!["minecraft"],
        },
    }
}

/// The newest version that works with this Minecraft version (a full release if there is one).
pub fn best_version(project: &str, kind: &str, mc: &str, loader: Loader) -> Result<Version> {
    let v = project_versions(project, &loaders_for(kind, loader), &[mc])?;
    v.iter()
        .find(|x| x.version_type == "release")
        .or(v.first())
        .cloned()
        .with_context(|| format!("there's no version of this for Minecraft {mc}"))
}

/// The Modrinth version each file (by SHA-1) belongs to.
pub fn versions_by_hash(hashes: &[String]) -> Result<HashMap<String, Version>> {
    if hashes.is_empty() {
        return Ok(HashMap::new());
    }
    net::post_json(&format!("{API}/version_files"), &json!({ "hashes": hashes, "algorithm": "sha1" }))
}

/// The newest version for each file (by SHA-1) that works with these loaders and Minecraft versions.
pub fn latest_by_hash(hashes: &[String], loaders: &[&str], mc: &str) -> Result<HashMap<String, Version>> {
    if hashes.is_empty() {
        return Ok(HashMap::new());
    }
    net::post_json(
        &format!("{API}/version_files/update"),
        &json!({ "hashes": hashes, "algorithm": "sha1", "loaders": loaders, "game_versions": [mc] }),
    )
}

/// The folder each kind of content goes in.
pub fn folder(kind: &str) -> Result<&'static str> {
    Ok(match kind {
        "mod" => "mods",
        "resourcepack" => "resourcepacks",
        "shader" => "shaderpacks",
        _ => bail!("unknown kind of content"),
    })
}

/// Downloads one version's main file into the instance. Replaces an older file of the same
/// project if `old` is given. Returns the new file name.
pub fn install_version(inst: &Instance, kind: &str, v: &Version, old: Option<&str>, keep_disabled: bool) -> Result<String> {
    let f = v.primary_file().context("this version has no file")?;
    if !util::plain_name(&f.filename) {
        bail!("bad file name from Modrinth");
    }
    if !f.url.starts_with("https://cdn.modrinth.com/") {
        bail!("this file isn't on Modrinth's servers");
    }
    let dir = instance::game_dir(&inst.id)?.join(folder(kind)?);
    fs::create_dir_all(&dir)?;
    let mut name = f.filename.clone();
    if keep_disabled {
        name.push_str(".disabled");
    }
    net::download(&Download::new(f.url.clone(), dir.join(&name), f.hashes.get("sha1").cloned(), Some(f.size)))?;
    if let Some(o) = old.filter(|o| *o != name && util::plain_name(o)) {
        let _ = fs::remove_file(dir.join(o));
    }
    Ok(name)
}

/// SHA-1 -> file name for every file in an instance's content folder.
pub fn hashes_in(inst: &Instance, kind: &str) -> Result<HashMap<String, String>> {
    let dir = instance::game_dir(&inst.id)?.join(folder(kind)?);
    let mut out = HashMap::new();
    if let Ok(rd) = fs::read_dir(&dir) {
        for e in rd.flatten() {
            if e.file_type().map(|t| t.is_file()).unwrap_or(false) {
                if let Ok(h) = util::sha1_file(&e.path()) {
                    out.insert(h, e.file_name().to_string_lossy().into_owned());
                }
            }
        }
    }
    Ok(out)
}

/// Adds a project (and, for mods, every mod it needs) to an instance. Returns the new files.
pub fn add_project(inst: &Instance, project_id: &str, kind: &str) -> Result<Vec<String>> {
    folder(kind)?;
    let have = hashes_in(inst, kind)?;
    // project id -> (file name, version id) for what's already there
    let installed: HashMap<String, (String, String)> = versions_by_hash(&have.keys().cloned().collect::<Vec<_>>())?
        .into_iter()
        .filter_map(|(h, v)| have.get(&h).map(|f| (v.project_id.clone(), (f.clone(), v.id.clone()))))
        .collect();
    let mut added = Vec::new();
    let mut seen = HashSet::new();
    let mut todo = vec![project_id.to_string()];
    while let Some(pid) = todo.pop() {
        if !seen.insert(pid.clone()) || seen.len() > 40 {
            continue;
        }
        let first = pid == project_id;
        if !first && installed.contains_key(&pid) {
            continue;
        }
        // dependencies get the newest version for this Minecraft version (not the one the author
        // pinned, which is often for another Minecraft version)
        let v = best_version(&pid, kind, &inst.mc_version, inst.loader)?;
        let old = installed.get(&pid).map(|(f, _)| f.as_str());
        if first && installed.get(&pid).is_some_and(|(_, id)| *id == v.id) {
            bail!("that's already in this instance");
        }
        added.push(install_version(inst, kind, &v, old, old.is_some_and(|o| o.ends_with(".disabled")))?);
        if kind == "mod" {
            for d in &v.dependencies {
                if d.dependency_type == "required" {
                    if let Some(p) = &d.project_id {
                        todo.push(p.clone());
                    }
                }
            }
        }
    }
    Ok(added)
}

#[cfg(test)]
mod tests {
    #[test]
    fn encoding() {
        assert_eq!(super::enc("a b/[\"x\"]"), "a%20b%2F%5B%22x%22%5D");
    }
}
