//! Launcher settings (settings.json in the data folder).

use serde::{Deserialize, Serialize};

use crate::{paths, util};

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase", default)]
pub struct Settings {
    /// One of Treasure Client's themes: Treasure, Frost, Tropical, Terracotta, Forest, Sakura, Dark.
    pub theme: String,
    /// Memory for instances that don't set their own.
    pub memory_mb: u32,
    /// "hide" (comes back when the game closes), "open" or "minimize".
    pub while_playing: String,
    pub reduce_motion: bool,
    pub sidebar_labels: bool,
    /// Your own Java instead of Mojang's (blank = automatic).
    pub java_path: Option<String>,
    /// How many files download at once.
    pub downloads_at_once: u32,
    /// The two Treasure Pack instances were made on first start.
    pub defaults_created: bool,
}

impl Default for Settings {
    fn default() -> Self {
        Settings {
            theme: "Treasure".into(),
            memory_mb: suggested_memory_mb(),
            while_playing: "hide".into(),
            reduce_motion: false,
            sidebar_labels: true,
            java_path: None,
            downloads_at_once: 8,
            defaults_created: false,
        }
    }
}

pub fn load() -> Settings {
    util::read_json(&paths::root().join("settings.json")).unwrap_or_default()
}

pub fn save(s: &Settings) -> anyhow::Result<()> {
    util::write_json(&paths::root().join("settings.json"), s)
}

/// This computer's memory in MB, when we can tell.
pub fn total_memory_mb() -> Option<u64> {
    #[cfg(target_os = "linux")]
    {
        let t = std::fs::read_to_string("/proc/meminfo").ok()?;
        let line = t.lines().find(|l| l.starts_with("MemTotal:"))?;
        let kb: u64 = line.split_whitespace().nth(1)?.parse().ok()?;
        Some(kb / 1024)
    }
    #[cfg(not(target_os = "linux"))]
    None
}

/// 4 GB, or less on computers that don't have much (half their memory, at least 2 GB).
pub fn suggested_memory_mb() -> u32 {
    match total_memory_mb() {
        Some(t) if t < 8192 => ((t / 2).max(2048) / 512 * 512) as u32,
        _ => 4096,
    }
}
