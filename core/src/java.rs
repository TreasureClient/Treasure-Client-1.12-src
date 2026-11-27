//! Java, from Mojang's own runtime downloads (the same Java the official launcher uses), so
//! nobody has to install Java themselves. Each Minecraft version names the runtime it needs.

use std::collections::HashMap;
use std::fs;
use std::path::PathBuf;

use anyhow::{bail, Context, Result};
use serde::Deserialize;

use crate::net::{self, Check, Download};
use crate::{paths, report, util, ProgressFn};

const ALL: &str = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

#[derive(Deserialize)]
struct Entry {
    manifest: ManifestRef,
    version: VersionName,
}
#[derive(Deserialize)]
struct ManifestRef {
    sha1: String,
    url: String,
}
#[derive(Deserialize)]
struct VersionName {
    name: String,
}
#[derive(Deserialize)]
struct RuntimeManifest {
    files: HashMap<String, RuntimeFile>,
}
#[derive(Deserialize)]
struct RuntimeFile {
    #[serde(rename = "type")]
    kind: String,
    #[serde(default)]
    executable: bool,
    #[serde(default)]
    downloads: Option<RuntimeDownloads>,
    #[serde(default)]
    target: Option<String>,
}
#[derive(Deserialize)]
struct RuntimeDownloads {
    raw: RawFile,
}
#[derive(Deserialize)]
struct RawFile {
    sha1: String,
    size: u64,
    url: String,
}

/// Mojang's name for this computer, or None if Mojang has no Java for it.
pub fn platform() -> Option<&'static str> {
    Some(match (std::env::consts::OS, std::env::consts::ARCH) {
        ("linux", "x86_64") => "linux",
        ("linux", "x86") => "linux-i386",
        ("windows", "x86_64") => "windows-x64",
        ("windows", "x86") => "windows-x86",
        ("windows", "aarch64") => "windows-arm64",
        ("macos", "x86_64") => "mac-os",
        ("macos", "aarch64") => "mac-os-arm64",
        _ => return None,
    })
}

pub fn runtime_dir(component: &str) -> PathBuf {
    paths::runtimes().join(component)
}

pub fn java_exe(component: &str) -> PathBuf {
    let d = runtime_dir(component);
    if cfg!(target_os = "macos") {
        d.join("jre.bundle/Contents/Home/bin/java")
    } else if cfg!(windows) {
        d.join("bin/javaw.exe")
    } else {
        d.join("bin/java")
    }
}

/// Makes sure the Java runtime `component` is downloaded and returns the path to `java`.
pub fn ensure(component: &str, check: Check, progress: &ProgressFn) -> Result<PathBuf> {
    if !util::plain_name(component) {
        bail!("bad Java name");
    }
    let exe = java_exe(component);
    let marker = runtime_dir(component).join(".treasure-complete");
    let stage = "Downloading Java";
    // the list is only re-checked when the runtime is missing or a full repair was asked for
    if check == Check::Quick && exe.exists() && marker.exists() {
        return Ok(exe);
    }
    let plat = platform().context("Mojang doesn't make Java for this kind of computer. Set your own Java in Settings.")?;
    report(progress, stage, 0, 0);
    let all: HashMap<String, HashMap<String, Vec<Entry>>> = net::get_json(ALL)?;
    let entry = all
        .get(plat)
        .and_then(|p| p.get(component))
        .and_then(|v| v.first())
        .with_context(|| format!("Mojang has no {component} Java for {plat}. Set your own Java in Settings."))?;
    let text = net::get_text(&entry.manifest.url)?;
    if !util::sha1_bytes(text.as_bytes()).eq_ignore_ascii_case(&entry.manifest.sha1) {
        bail!("Java's file list failed its checksum");
    }
    let man: RuntimeManifest = serde_json::from_str(&text).context("Java's file list looks wrong")?;
    let base = runtime_dir(component);
    let mut list = Vec::new();
    let mut links = Vec::new();
    for (rel, f) in &man.files {
        let path = util::safe_join(&base, rel).with_context(|| format!("unsafe path in Java's file list: {rel}"))?;
        match f.kind.as_str() {
            "directory" => fs::create_dir_all(&path)?,
            "file" => {
                if let Some(d) = &f.downloads {
                    let mut dl = Download::new(d.raw.url.clone(), path, Some(d.raw.sha1.clone()), Some(d.raw.size));
                    dl.executable = f.executable;
                    list.push(dl);
                }
            }
            "link" => {
                if let Some(t) = &f.target {
                    links.push((path, t.clone()));
                }
            }
            _ => {}
        }
    }
    net::download_all(list, check, stage, progress)?;
    #[cfg(unix)]
    for (path, target) in links {
        // a link must stay inside the runtime folder
        let resolved = path.parent().map(|p| p.join(&target));
        if target.starts_with('/') || resolved.as_ref().map_or(true, |r| !normalize(r).starts_with(&base)) {
            continue;
        }
        let _ = fs::remove_file(&path);
        if let Some(p) = path.parent() {
            fs::create_dir_all(p)?;
        }
        std::os::unix::fs::symlink(&target, &path)?;
    }
    #[cfg(not(unix))]
    let _ = links;
    if !exe.exists() {
        bail!("Java downloaded but {} is missing", exe.display());
    }
    fs::write(&marker, &entry.version.name)?;
    Ok(exe)
}

#[cfg(unix)]
fn normalize(p: &std::path::Path) -> PathBuf {
    use std::path::Component;
    let mut out = PathBuf::new();
    for c in p.components() {
        match c {
            Component::ParentDir => {
                out.pop();
            }
            Component::CurDir => {}
            c => out.push(c),
        }
    }
    out
}
