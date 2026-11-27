//! Where the launcher keeps its files. Games, libraries, assets and Java are shared by all
//! instances so each Minecraft version is only downloaded once.
//!
//! Linux:   ~/.local/share/treasure-launcher (or $XDG_DATA_HOME/treasure-launcher)
//! Windows: %APPDATA%\TreasureLauncher
//! macOS:   ~/Library/Application Support/TreasureLauncher
//! $TREASURE_LAUNCHER_HOME overrides all of these (used for testing).

use std::path::PathBuf;
use std::sync::OnceLock;

static ROOT: OnceLock<PathBuf> = OnceLock::new();

/// Use a different data folder (must be called before anything else touches the files).
pub fn set_root(p: PathBuf) {
    let _ = ROOT.set(p);
}

pub fn root() -> PathBuf {
    ROOT.get_or_init(default_root).clone()
}

fn env(k: &str) -> Option<PathBuf> {
    std::env::var_os(k).filter(|v| !v.is_empty()).map(PathBuf::from)
}

fn default_root() -> PathBuf {
    if let Some(p) = env("TREASURE_LAUNCHER_HOME") {
        return p;
    }
    if cfg!(windows) {
        if let Some(a) = env("APPDATA") {
            return a.join("TreasureLauncher");
        }
    }
    if cfg!(target_os = "macos") {
        if let Some(h) = env("HOME") {
            return h.join("Library/Application Support/TreasureLauncher");
        }
    }
    if let Some(x) = env("XDG_DATA_HOME") {
        return x.join("treasure-launcher");
    }
    if let Some(h) = env("HOME").or_else(|| env("USERPROFILE")) {
        return h.join(".local/share/treasure-launcher");
    }
    PathBuf::from("treasure-launcher")
}

/// Cached JSON from Mojang, Fabric and our own channel.
pub fn meta() -> PathBuf {
    root().join("meta")
}
/// Game jars and natives, one folder per version.
pub fn versions() -> PathBuf {
    root().join("versions")
}
pub fn libraries() -> PathBuf {
    root().join("libraries")
}
pub fn assets() -> PathBuf {
    root().join("assets")
}
/// Java runtimes from Mojang, one folder per component (java-runtime-delta etc.).
pub fn runtimes() -> PathBuf {
    root().join("runtimes")
}
pub fn instances() -> PathBuf {
    root().join("instances")
}
/// Downloaded modpack files etc.
pub fn cache() -> PathBuf {
    root().join("cache")
}
