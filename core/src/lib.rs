//! Treasure Launcher's engine. Everything here is plain Rust with no window code, so it can be
//! built and tested anywhere; the app (app/src-tauri) only calls into it.
//!
//! What it does:
//! - downloads Minecraft (game jar, libraries, assets) from Mojang and checks every file's SHA-1
//! - downloads Mojang's own Java runtime for the version being played
//! - adds Fabric (from meta.fabricmc.net), Treasure Client and Fabric API
//! - searches Modrinth, installs .mrpack modpacks and adds/updates mods
//! - keeps instances (one folder each) and starts the game
//! - signs in with Microsoft (once Mojang has approved the app's client ID)

pub mod auth;
pub mod fabric;
pub mod install;
pub mod instance;
pub mod java;
pub mod launch;
pub mod modrinth;
pub mod mods;
pub mod mojang;
pub mod mrpack;
pub mod net;
pub mod paths;
pub mod settings;
pub mod treasure;
pub mod util;

use std::sync::Arc;

pub use anyhow::{Error, Result};

pub const NAME: &str = "Treasure Launcher";
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// A progress report for a long job (downloads, installs).
#[derive(Clone, Debug, serde::Serialize)]
pub struct Progress {
    /// What is happening, in plain words ("Downloading Minecraft 26.3").
    pub stage: String,
    pub done: u64,
    pub total: u64,
}

pub type ProgressFn = Arc<dyn Fn(Progress) + Send + Sync>;

pub fn no_progress() -> ProgressFn {
    Arc::new(|_| {})
}

pub(crate) fn report(p: &ProgressFn, stage: &str, done: u64, total: u64) {
    p(Progress { stage: stage.to_string(), done, total });
}
