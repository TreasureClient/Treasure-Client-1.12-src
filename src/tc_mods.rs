//! Treasure Client: the Mod Menu's mod switches and settings, as the game sees them.
//!
//! The menu (the HTML overlay, `web/tc-overlay.js`) writes them to browser storage
//! `_eaglercraft_1.12.tcmods` as base64 of `key=value,key=value,...` (the same string the
//! JS and WASM editions read in `TreasureMods`). This reads it four times a second, so a
//! switch flipped in the menu takes effect right away.

use std::sync::{Arc, Mutex};

#[derive(Clone, Debug)]
pub struct TcMods {
    pub fullbright: bool,
    pub toggle_sprint: bool,
    pub toggle_sneak: bool,
    pub hitboxes: bool,
    pub hitbox_players: bool,
    pub hitbox_mobs: bool,
    pub hitbox_animals: bool,
    pub hitbox_other: bool,
    pub no_particles: bool,
    pub no_hurt_cam: bool,
    /// Percent of the normal FOV while zoomed.
    pub zoom_level: f32,
    pub smooth_zoom: bool,
    pub zoom_speed: f32,
    pub low_fire: bool,
    /// Percent of the normal fire overlay height.
    pub low_fire_height: f32,
    pub low_shield: bool,
    /// How far down the shield sits (percent).
    pub low_shield_amount: f32,
    pub small_totem: bool,
    /// Percent of the normal totem animation size.
    pub totem_scale: f32,
    pub clear_water: bool,
    pub saturation: bool,
    pub hit_color: Option<(u32, f32)>,
    /// Hide entities farther than this many blocks (0 = off).
    pub entity_distance: f32,
    pub auto_gg: bool,
    pub gg_message: String,
    pub gg_delay_ms: u32,
    pub gg_glhf: bool,
    pub auto_login: bool,
}

impl Default for TcMods {
    fn default() -> Self {
        TcMods {
            fullbright: false,
            toggle_sprint: false,
            toggle_sneak: false,
            hitboxes: false,
            hitbox_players: true,
            hitbox_mobs: true,
            hitbox_animals: true,
            hitbox_other: true,
            no_particles: false,
            no_hurt_cam: false,
            zoom_level: 30.0,
            smooth_zoom: false,
            zoom_speed: 8.0,
            low_fire: false,
            low_fire_height: 40.0,
            low_shield: false,
            low_shield_amount: 30.0,
            small_totem: false,
            totem_scale: 40.0,
            clear_water: true,
            saturation: false,
            hit_color: None,
            entity_distance: 0.0,
            auto_gg: false,
            gg_message: "gg".to_owned(),
            gg_delay_ms: 1000,
            gg_glhf: false,
            auto_login: false,
        }
    }
}

fn parse(text: &str) -> TcMods {
    let mut m = TcMods::default();
    let mut hit_on = false;
    let mut hit_rgb = 0xFF0000;
    let mut hit_alpha = 30.0;
    let mut perf_pack = true;
    for part in text.split(',') {
        let Some((k, v)) = part.split_once('=') else { continue };
        let on = v == "1";
        let num = v.parse::<f32>().ok();
        match k {
            "fullbright" => m.fullbright = on,
            "sprint" => m.toggle_sprint = on,
            "sneak" => m.toggle_sneak = on,
            "hitbox" => m.hitboxes = on,
            "hbPlayers" => m.hitbox_players = on,
            "hbMobs" => m.hitbox_mobs = on,
            "hbAnimals" => m.hitbox_animals = on,
            "hbOther" => m.hitbox_other = on,
            "noParticles" => m.no_particles = on,
            "noHurtCam" => m.no_hurt_cam = on,
            "zoomLevel" => m.zoom_level = num.unwrap_or(30.0).clamp(5.0, 100.0),
            "smoothZoom" => m.smooth_zoom = on,
            "zoomSpeed" => m.zoom_speed = num.unwrap_or(8.0).max(1.0),
            "lowFire" => m.low_fire = on,
            "lowFireH" => m.low_fire_height = num.unwrap_or(40.0).clamp(0.0, 100.0),
            "lowShield" => m.low_shield = on,
            "lowShieldA" => m.low_shield_amount = num.unwrap_or(30.0).clamp(0.0, 100.0),
            "smallTotem" => m.small_totem = on,
            "totemScale" => m.totem_scale = num.unwrap_or(40.0).clamp(5.0, 100.0),
            "clearWater" => m.clear_water = on,
            "satDisplay" => m.saturation = on,
            "hitColorOn" => hit_on = on,
            "hitColor" => hit_rgb = u32::from_str_radix(v, 16).unwrap_or(0xFF0000),
            "hitAlpha" => hit_alpha = num.unwrap_or(30.0),
            "entDist" => m.entity_distance = num.unwrap_or(0.0).clamp(0.0, 256.0),
            "autoGG" => m.auto_gg = on,
            "agMsg" => m.gg_message = v.to_owned(),
            "agDelay" => m.gg_delay_ms = num.unwrap_or(1000.0).max(0.0) as u32,
            "agGlhf" => m.gg_glhf = on,
            "autoLogin" => m.auto_login = on,
            "perfpack" => perf_pack = on,
            _ => {}
        }
    }
    // Like the JS and WASM editions, Entity Distance is part of the performance pack.
    if !perf_pack {
        m.entity_distance = 0.0;
    }
    if hit_on {
        m.hit_color = Some((hit_rgb, (hit_alpha / 100.0).clamp(0.0, 1.0)));
    }
    m
}

#[cfg(target_arch = "wasm32")]
fn read() -> Option<String> {
    use base64::Engine;
    let raw = web_sys::window()?
        .local_storage()
        .ok()??
        .get_item("_eaglercraft_1.12.tcmods")
        .ok()??;
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(raw.trim())
        .ok()?;
    String::from_utf8(bytes).ok()
}

#[cfg(not(target_arch = "wasm32"))]
fn read() -> Option<String> {
    None
}

fn clock() -> f32 {
    static START: std::sync::OnceLock<crate::platform::time::Instant> = std::sync::OnceLock::new();
    START
        .get_or_init(crate::platform::time::Instant::now)
        .elapsed()
        .as_secs_f32()
}

static SPRINT_TOGGLED: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);
static SNEAK_TOGGLED: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);

/// Whether Toggle Sprint / Toggle Sneak are switched on right now (for the HUD).
pub fn set_toggled(sprint: bool, sneak: bool) {
    SPRINT_TOGGLED.store(sprint, std::sync::atomic::Ordering::Relaxed);
    SNEAK_TOGGLED.store(sneak, std::sync::atomic::Ordering::Relaxed);
}

pub fn toggled() -> (bool, bool) {
    (
        SPRINT_TOGGLED.load(std::sync::atomic::Ordering::Relaxed),
        SNEAK_TOGGLED.load(std::sync::atomic::Ordering::Relaxed),
    )
}

/// The current mod settings (re-read from the menu four times a second).
pub fn get() -> Arc<TcMods> {
    static CACHE: Mutex<Option<(f32, Arc<TcMods>)>> = Mutex::new(None);
    let now = clock();
    let mut cache = CACHE.lock().unwrap();
    if let Some((at, mods)) = cache.as_ref()
        && now - at < 0.25
    {
        return mods.clone();
    }
    let mods = Arc::new(read().map(|t| parse(&t)).unwrap_or_default());
    *cache = Some((now, mods.clone()));
    mods
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_the_menu_string() {
        let m = parse("sprint=1,sneak=0,zoomLevel=20,hitColorOn=1,hitColor=00ff44,hitAlpha=50,agMsg=gg wp");
        assert!(m.toggle_sprint && !m.toggle_sneak);
        assert_eq!(m.zoom_level, 20.0);
        assert_eq!(m.hit_color, Some((0x00ff44, 0.5)));
        assert_eq!(m.gg_message, "gg wp");
    }
}
