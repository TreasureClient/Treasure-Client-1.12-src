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
    pub clear_lava: bool,
    pub dynamic_fov: bool,
    pub pause_unfocused: bool,
    pub tnt_timer: bool,
    pub password_hider: bool,
    pub saturation: bool,
    pub hit_color: Option<(u32, f32)>,
    /// Hide entities farther than this many blocks (0 = off).
    pub entity_distance: f32,
    pub auto_gg: bool,
    pub gg_message: String,
    pub gg_delay_ms: u32,
    pub gg_glhf: bool,
    pub auto_login: bool,
    pub old_anim: bool,
    pub oa_no_dip: bool,
    pub oa_use_swing: bool,
    pub t_anim: bool,
    pub ta_style: crate::renderer::tc_anim::Style,
    pub ta_speed: f32,
    pub ta_punch: bool,
    pub ta_draw: bool,
    pub ta_sway: bool,
    pub ta_eat: bool,
    pub ta_throw: bool,
    /// Performance: Dynamic FPS (10 fps when another window is focused, 30 on menus).
    pub dyn_fps: bool,
    /// Performance: FPS Reducer (10 fps after 60 s without input).
    pub fps_reducer: bool,
    /// Performance: Particle Core (far particles skip block collision).
    pub particle_core: bool,
    /// Performance: Entity Culling (mobs and players hidden behind walls aren't drawn).
    pub entity_culling: bool,
    /// Performance: Leaf Culling (0 off, 1 hollow, 2 aggressive, 3 solid).
    pub leaf_cull: u8,
    /// ViaVersion: the master switch for ViaItems, ViaMobs and ViaBlocks.
    pub via: bool,
    /// Modern Block Guessing (ViaBlocks without a plugin).
    pub via_guess: bool,
    pub tier_tagger: bool,
    /// The gamemode to show ("highest" = overall).
    pub tt_mode: String,
    pub ch_addons: bool,
    pub ch_target: bool,
    pub ch_mobs: bool,
    pub ch_elytra: bool,
    pub ch_rocket: bool,
    pub ch_hit: bool,
    pub ch_hit_melee: bool,
    pub ch_shield_break: bool,
    pub ch_shield_up: bool,
    /// Reach Display, Combo Counter and Item Counter (HUD boxes in the overlay).
    pub reach_disp: bool,
    pub combo: bool,
    pub item_count: bool,
    /// Item Counter: arrows, pearls, golden apples, totems, splash potions, blocks.
    pub ic: [bool; 6],
    /// Scoreboard: on, hide the red numbers, size (1 = normal), background (0 normal, 1 lighter, 2 none).
    pub sb_on: bool,
    pub sb_no_nums: bool,
    pub sb_scale: f32,
    pub sb_bg: u8,
    /// Block Outline: on, colour (0xRRGGBB), thickness in pixels, opacity (0-1), fill the sides.
    pub bo_on: bool,
    pub bo_color: u32,
    pub bo_width: f32,
    pub bo_alpha: f32,
    pub bo_fill: bool,
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
            hitbox_other: false,
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
            clear_water: false,
            clear_lava: false,
            dynamic_fov: false,
            pause_unfocused: false,
            tnt_timer: false,
            password_hider: false,
            saturation: false,
            hit_color: None,
            entity_distance: 0.0,
            auto_gg: false,
            gg_message: "gg".to_owned(),
            gg_delay_ms: 1000,
            gg_glhf: false,
            auto_login: false,
            old_anim: false,
            oa_no_dip: true,
            oa_use_swing: true,
            t_anim: false,
            ta_style: crate::renderer::tc_anim::Style::Treasure,
            ta_speed: 100.0,
            ta_punch: true,
            ta_draw: true,
            ta_sway: true,
            ta_eat: true,
            ta_throw: true,
            dyn_fps: true,
            fps_reducer: true,
            particle_core: true,
            entity_culling: true,
            leaf_cull: 3,
            via: true,
            via_guess: true,
            tier_tagger: false,
            tt_mode: "highest".to_owned(),
            ch_addons: false,
            ch_target: true,
            ch_mobs: false,
            ch_elytra: true,
            ch_rocket: true,
            ch_hit: true,
            ch_hit_melee: false,
            ch_shield_break: false,
            ch_shield_up: false,
            reach_disp: false,
            combo: false,
            item_count: false,
            ic: [true, true, true, true, true, false],
            sb_on: false,
            sb_no_nums: true,
            sb_scale: 1.0,
            sb_bg: 0,
            bo_on: false,
            bo_color: 0x000000,
            bo_width: 2.0,
            bo_alpha: 0.4,
            bo_fill: false,
        }
    }
}

fn parse(text: &str) -> TcMods {
    let mut m = TcMods::default();
    let mut hit_on = false;
    let mut hit_rgb = 0xFF0000;
    let mut hit_alpha = 30.0;
    let mut perf_pack = true;
    let mut clear_water = true;
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
            "clearWater" => clear_water = on,
            "clearLiquids" => m.clear_lava = on,
            "dynfov" => m.dynamic_fov = on,
            "pauseUnfocused" => m.pause_unfocused = on,
            "tntTimer" => m.tnt_timer = on,
            "pwHide" => m.password_hider = on,
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
            "oldAnim" => m.old_anim = on,
            "oaNoDip" => m.oa_no_dip = on,
            "oaUseSwing" => m.oa_use_swing = on,
            "tAnim" => m.t_anim = on,
            "taStyle" => {
                use crate::renderer::tc_anim::Style;
                m.ta_style = match v {
                    "slash" => Style::Slash,
                    "spin" => Style::Spin,
                    "smooth" => Style::Smooth,
                    _ => Style::Treasure,
                };
            }
            "taSpeed" => m.ta_speed = num.unwrap_or(100.0).clamp(25.0, 300.0),
            "taPunch" => m.ta_punch = on,
            "taDraw" => m.ta_draw = on,
            "taSway" => m.ta_sway = on,
            "taEat" => m.ta_eat = on,
            "taThrow" => m.ta_throw = on,
            "dynfps" => m.dyn_fps = on,
            "pcore" => m.particle_core = on,
            "entcull" => m.entity_culling = on,
            "leafCull" => {
                m.leaf_cull = match v {
                    "off" => 0,
                    "hollow" => 1,
                    "aggressive" => 2,
                    _ => 3,
                }
            }
            "via" => m.via = on,
            "viaguess" => m.via_guess = on,
            "tierTagger" => m.tier_tagger = on,
            "ttMode" => m.tt_mode = v.to_owned(),
            "chAddons" => m.ch_addons = on,
            "chTarget" => m.ch_target = on,
            "chMobs" => m.ch_mobs = on,
            "chElytra" => m.ch_elytra = on,
            "chRocket" => m.ch_rocket = on,
            "chHit" => m.ch_hit = on,
            "chHitMelee" => m.ch_hit_melee = on,
            "chShieldBreak" => m.ch_shield_break = on,
            "chShieldUp" => m.ch_shield_up = on,
            "reachDisp" => m.reach_disp = on,
            "combo" => m.combo = on,
            "itemCount" => m.item_count = on,
            "icArrow" => m.ic[0] = on,
            "icPearl" => m.ic[1] = on,
            "icGapple" => m.ic[2] = on,
            "icTotem" => m.ic[3] = on,
            "icPotion" => m.ic[4] = on,
            "icBlocks" => m.ic[5] = on,
            "sbOn" => m.sb_on = on,
            "sbNoNums" => m.sb_no_nums = on,
            "sbScale" => m.sb_scale = num.unwrap_or(100.0).clamp(50.0, 150.0) / 100.0,
            "sbBg" => {
                m.sb_bg = match v {
                    "none" => 2,
                    "light" => 1,
                    _ => 0,
                }
            }
            "boOn" => m.bo_on = on,
            "boColor" => m.bo_color = u32::from_str_radix(v, 16).unwrap_or(0) & 0xFF_FFFF,
            "boWidth" => m.bo_width = num.unwrap_or(2.0).clamp(1.0, 6.0),
            "boAlpha" => m.bo_alpha = num.unwrap_or(40.0).clamp(10.0, 100.0) / 100.0,
            "boFill" => m.bo_fill = on,
            _ => {}
        }
    }
    // Like the JS and WASM editions: Clear Water is a Fullbright option, and Clear Liquids
    // clears both water and lava.
    m.clear_water = (m.fullbright && clear_water) || m.clear_lava;
    // Like the JS and WASM editions, Entity Distance is part of the performance pack.
    if !perf_pack {
        m.entity_distance = 0.0;
        m.fps_reducer = false;
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

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Group {
    /// Hostile mobs (1.12's IMob).
    Mob,
    /// Animals.
    Animal,
    Other,
}

/// The same groups as the JS and WASM editions: monsters, then animals, then the rest.
pub fn group(kind: azalea_registry::builtin::EntityKind) -> Group {
    const MOBS: &[&str] = &[
        "Zombie", "Husk", "Drowned", "ZombieVillager", "Skeleton", "Stray", "WitherSkeleton",
        "Bogged", "Creeper", "Spider", "CaveSpider", "Enderman", "Endermite", "Silverfish",
        "Blaze", "Ghast", "MagmaCube", "Slime", "Witch", "Guardian", "ElderGuardian", "Shulker",
        "Vex", "Vindicator", "Evoker", "Pillager", "Ravager", "Illusioner", "Phantom", "Piglin",
        "PiglinBrute", "ZombifiedPiglin", "Hoglin", "Zoglin", "Wither", "EnderDragon", "Warden",
        "Breeze", "Creaking",
    ];
    const ANIMALS: &[&str] = &[
        "Pig", "Cow", "Sheep", "Chicken", "Horse", "Donkey", "Mule", "SkeletonHorse",
        "ZombieHorse", "Llama", "TraderLlama", "Wolf", "Cat", "Ocelot", "Rabbit", "PolarBear",
        "Parrot", "Fox", "Panda", "Bee", "Turtle", "Mooshroom", "Goat", "Axolotl", "Frog",
        "Strider", "Camel", "Sniffer", "Armadillo",
    ];
    let name = format!("{kind:?}");
    if MOBS.contains(&name.as_str()) {
        Group::Mob
    } else if ANIMALS.contains(&name.as_str()) {
        Group::Animal
    } else {
        Group::Other
    }
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
