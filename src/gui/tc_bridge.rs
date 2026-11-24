//! Treasure Client: the bridge to the Treasure Client menu and HUD.
//!
//! The Mod Menu, the HUD (keystrokes, CPS, FPS, coordinates, armor, potions...), the
//! HUD editor and the themes are the same HTML overlay the JS and WASM editions use
//! (`web/tc-overlay.js`). It talks to the game through browser storage keys, base64
//! encoded, named `_eaglercraft_1.12.<key>` (the Eaglercraft storage names, so the
//! settings are shared with the other editions on the same site):
//!
//! - `tchud`: the game writes what the HUD shows (`active`, `x`, `y`, `z`, `yaw`,
//!   `ping`, `armor`, `potions`, `held`, `sprintmode`, `sneakmode`).
//! - `tcinworld`: 1 while in a world, then 1 while playing (no menu, chat or inventory open).
//! - `tcpause`: the overlay writes 3 to freeze the game (an empty screen that frees the
//!   mouse) and 2 to go back; the game answers 0, or 4 when Escape closed it.
//! - `tczoom`: 1 while the Zoom key is held. `tcmods`: the mod settings.
//! - `tcmenu`: 1 while the title screen shows, with `tcmenuinfo` ("name\nrejoin\nprofile\nversion\ndemo\nnosp").
//!   The overlay draws the 1.5 main menu words, writes the clicked one to `tcmenuact`, and
//!   writes the time to `tcmenuack` while it shows them (the title screen's own buttons
//!   come back if it stops).

use std::sync::Mutex;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};

use base64::Engine;

use crate::gui::{GuiState, Snapshot};

const PREFIX: &str = "_eaglercraft_1.12.";

fn storage() -> Option<web_sys::Storage> {
    web_sys::window()?.local_storage().ok()?
}

fn get(key: &str) -> Option<Vec<u8>> {
    let raw = storage()?.get_item(&format!("{PREFIX}{key}")).ok()??;
    base64::engine::general_purpose::STANDARD.decode(raw.trim()).ok()
}

fn set(key: &str, value: &[u8]) {
    if let Some(s) = storage() {
        let _ = s.set_item(
            &format!("{PREFIX}{key}"),
            &base64::engine::general_purpose::STANDARD.encode(value),
        );
    }
}

fn remove(key: &str) {
    if let Some(s) = storage() {
        let _ = s.remove_item(&format!("{PREFIX}{key}"));
    }
}

static TITLE_SHOWN: AtomicBool = AtomicBool::new(false);
static TITLE_SINCE: AtomicU64 = AtomicU64::new(0);
static TITLE_AT: AtomicU64 = AtomicU64::new(0);
static MENU_LAST_LIVE: AtomicU64 = AtomicU64::new(0);
static MENU_FLAG_AT: AtomicU64 = AtomicU64::new(0);
static MENU_LIVE: AtomicBool = AtomicBool::new(false);
static MENU_CHECKED: AtomicU64 = AtomicU64::new(0);

fn now_ms() -> u64 {
    js_sys::Date::now() as u64
}

/// The title screen is drawing this frame. The first frame tells the overlay to show the
/// 1.5 main menu (with `info`). Returns how long the title has been showing, in ms.
pub(crate) fn title_frame(info: &str) -> u64 {
    let now = now_ms();
    if !TITLE_SHOWN.swap(true, Ordering::Relaxed) {
        TITLE_SINCE.store(now, Ordering::Relaxed);
        MENU_CHECKED.store(0, Ordering::Relaxed);
        MENU_LAST_LIVE.store(0, Ordering::Relaxed);
        MENU_FLAG_AT.store(now, Ordering::Relaxed);
        set("tcmenuinfo", info.as_bytes());
        set("tcmenu", &[1]);
    } else if now.saturating_sub(MENU_FLAG_AT.load(Ordering::Relaxed)) >= 1000 {
        // the overlay only draws the menu while "tcmenu" says so; if something cleared it while
        // the title is still up (the overlay clears a stale one when it starts), say it again
        MENU_FLAG_AT.store(now, Ordering::Relaxed);
        if get("tcmenu").map_or(true, |v| v.first() != Some(&1)) {
            set("tcmenuinfo", info.as_bytes());
            set("tcmenu", &[1]);
        }
    }
    TITLE_AT.store(now, Ordering::Relaxed);
    now.saturating_sub(TITLE_SINCE.load(Ordering::Relaxed))
}

/// The menu word the overlay clicked ("mp", "set", ...), once.
pub(crate) fn take_menu_action() -> Option<String> {
    let value = get("tcmenuact")?;
    remove("tcmenuact");
    String::from_utf8(value).ok().map(|s| s.trim().to_owned())
}

/// Whether the overlay is drawing the main menu (it writes the time every 400 ms).
pub(crate) fn menu_overlay_live() -> bool {
    let now = now_ms();
    if now.saturating_sub(MENU_CHECKED.load(Ordering::Relaxed)) >= 200 {
        MENU_CHECKED.store(now, Ordering::Relaxed);
        let ack = get("tcmenuack")
            .and_then(|v| String::from_utf8(v).ok())
            .and_then(|s| s.trim().parse::<u64>().ok())
            .unwrap_or(0);
        if now.abs_diff(ack) < 2500 {
            MENU_LAST_LIVE.store(now, Ordering::Relaxed);
        }
        // live once it has answered, until it's been quiet for 8 s (it used to be 2.5 s, so one
        // slow moment swapped the menu for the old buttons)
        let last = MENU_LAST_LIVE.load(Ordering::Relaxed);
        MENU_LIVE.store(last != 0 && now.saturating_sub(last) < 8000, Ordering::Relaxed);
    }
    MENU_LIVE.load(Ordering::Relaxed)
}

/// Hides the overlay's main menu once the title screen stops drawing.
fn title_gone_check() {
    if TITLE_SHOWN.load(Ordering::Relaxed) && now_ms().saturating_sub(TITLE_AT.load(Ordering::Relaxed)) > 250 {
        TITLE_SHOWN.store(false, Ordering::Relaxed);
        remove("tcmenu");
    }
}

// ------------------------------------------------------------------ the Cosmetics screen
//
// Like the JS and WASM editions: the overlay draws the Cosmetics page while `tccosui` holds a
// recent time (renewed every 400 ms, so a reloaded tab never shows it by mistake), with
// `tccosinfo` = "name\nlook"; it writes what you pick to `tccosact` (lines of "key:value",
// "back" or "skin") and the time to `tccosack` every 400 ms while it shows the page.

static COS_OPEN: AtomicBool = AtomicBool::new(false);
static COS_SINCE: AtomicU64 = AtomicU64::new(0);
static COS_BEAT: AtomicU64 = AtomicU64::new(0);

/// Opens the Cosmetics page (from the main menu's Cosmetics word).
pub(crate) fn cos_open() {
    COS_OPEN.store(true, Ordering::Relaxed);
    COS_SINCE.store(now_ms(), Ordering::Relaxed);
    COS_BEAT.store(0, Ordering::Relaxed);
    remove("tccosact");
    // the main menu words hide while the Cosmetics page shows
    TITLE_SHOWN.store(false, Ordering::Relaxed);
    remove("tcmenu");
}

pub(crate) fn cos_is_open() -> bool {
    COS_OPEN.load(Ordering::Relaxed)
}

pub(crate) fn cos_close() {
    if COS_OPEN.swap(false, Ordering::Relaxed) {
        remove("tccosui");
    }
}

/// The Cosmetics page is showing this frame (`name` = your player name). Returns what the
/// overlay asked for, and whether it answers (false for a second and a half = it isn't there).
pub(crate) fn cos_frame(name: &str) -> (Vec<String>, bool) {
    let now = now_ms();
    if now.saturating_sub(COS_BEAT.load(Ordering::Relaxed)) >= 400 {
        COS_BEAT.store(now, Ordering::Relaxed);
        set("tccosui", now.to_string().as_bytes());
        static LAST_INFO: Mutex<String> = Mutex::new(String::new());
        let info = format!("{}\n{}", name.replace('\n', " "), crate::tc_cos::mine().encode());
        let mut last = LAST_INFO.lock().unwrap();
        if *last != info {
            set("tccosinfo", info.as_bytes());
            *last = info;
        }
    }
    let mut acts = Vec::new();
    if let Some(raw) = get("tccosact") {
        remove("tccosact");
        if let Ok(text) = String::from_utf8(raw) {
            acts.extend(text.lines().map(|l| l.trim().to_owned()).filter(|l| !l.is_empty()));
        }
    }
    let ack = get("tccosack")
        .and_then(|v| String::from_utf8(v).ok())
        .and_then(|s| s.trim().parse::<u64>().ok())
        .unwrap_or(0);
    let live = now.abs_diff(ack) < 2500 || now.saturating_sub(COS_SINCE.load(Ordering::Relaxed)) < 1500;
    (acts, live)
}

/// What the overlay asked for through `tcpause`, if anything (3 = freeze, 2 = back).
pub(crate) fn take_pause() -> Option<u8> {
    let value = *get("tcpause")?.first()?;
    if value == 2 || value == 3 {
        set("tcpause", &[0]);
        Some(value)
    } else {
        None
    }
}

/// The loading screen: the game has drawn its first menu frame (once; after a few
/// frames, so the picture is up before the loading screen fades).
pub(crate) fn boot_done() {
    static FRAMES: std::sync::atomic::AtomicU32 = std::sync::atomic::AtomicU32::new(0);
    match FRAMES.fetch_add(1, Ordering::Relaxed) {
        0 => set("tcboot", b"title"),
        5 => set("tcboot", b"done"),
        _ => {}
    }
}

/// Asks the overlay to open the Mod Menu (the title screen's Mods buttons).
pub(crate) fn open_menu() {
    set("tcopen", &[1]);
}

/// Tells the Mod Menu which profile is in use ("active\nname,name,..."), so each
/// profile keeps its own mod setup. Only writes when it changes.
pub(crate) fn set_profile(active: &str, names: &[&str]) {
    static LAST: Mutex<String> = Mutex::new(String::new());
    let text = format!("{active}\n{}", names.join(","));
    let mut last = LAST.lock().unwrap();
    if *last != text {
        set("tcprofile", text.as_bytes());
        *last = text;
    }
}

/// The freeze screen was closed by the game (Escape), not by the overlay.
pub(crate) fn freeze_closed() {
    set("tcpause", &[4]);
}

/// True while the overlay's Zoom key is held (and the Zoom mod is on).
pub(crate) fn zooming() -> bool {
    get("tczoom").and_then(|v| v.first().copied()) == Some(1)
}

static OWN_UUID: Mutex<u128> = Mutex::new(0);

pub(crate) fn set_own_uuid(uuid: u128) {
    *OWN_UUID.lock().unwrap() = uuid;
}

static LAST_HUD: AtomicU64 = AtomicU64::new(0);
static IN_WORLD: AtomicBool = AtomicBool::new(false);
/// In a world with no menu, chat or inventory open (the Mod Menu key only works then).
static PLAYING: AtomicBool = AtomicBool::new(false);
static WROTE_IN_WORLD: AtomicBool = AtomicBool::new(false);

/// Called every frame with the published game state; writes `tchud` ten times a second.
/// Storage writes block the page, so an unchanged HUD isn't written again.
fn set_hud(out: String) {
    static LAST_OUT: std::sync::Mutex<String> = std::sync::Mutex::new(String::new());
    let mut last = LAST_OUT.lock().unwrap();
    if *last != out {
        set("tchud", out.as_bytes());
        *last = out;
    }
}

pub(crate) fn publish(state: &GuiState, snap: &Snapshot, time: f64) {
    title_gone_check();
    // Treasure Client cosmetics: share your look and fetch other players' (every half second).
    if let Some(account) = state.profile.accounts.active() {
        crate::tc_cos::set_my_name(&account.name);
    }
    crate::tc_cos::tick();
    let in_world = state.hud.in_world;
    let playing = in_world && !state.screen.is_open();
    let changed = in_world != IN_WORLD.swap(in_world, Ordering::Relaxed);
    let changed = playing != PLAYING.swap(playing, Ordering::Relaxed) || changed;
    if changed || !WROTE_IN_WORLD.swap(true, Ordering::Relaxed) {
        set("tcinworld", &[u8::from(in_world), u8::from(playing)]);
    }
    let now = (time * 1000.0) as u64;
    if now.saturating_sub(LAST_HUD.load(Ordering::Relaxed)) < 100 {
        return;
    }
    LAST_HUD.store(now, Ordering::Relaxed);
    // The Mod Menu and Zoom keys from Controls, for the overlay (as Minecraft key numbers).
    {
        use crate::gui::keybinds::{Action, lwjgl_code};
        static LAST_KEYS: std::sync::Mutex<String> = std::sync::Mutex::new(String::new());
        let keys = format!(
            "menu={},zoom={}",
            lwjgl_code(state.keybinds.bound(Action::TcMenu)),
            lwjgl_code(state.keybinds.bound(Action::TcZoom))
        );
        let mut last = LAST_KEYS.lock().unwrap();
        if *last != keys {
            set("tckeys", keys.as_bytes());
            *last = keys;
        }
    }
    if !in_world {
        crate::gui::tc_pvp::reset();
        set_hud("active=0\n".to_owned());
        return;
    }

    let hud = &state.hud;
    let mut out = String::with_capacity(512);
    out.push_str("active=1\n");
    // The game's own frame rate (higher than the browser's when VSync is off).
    out.push_str(&format!("fps={}\n", hud.fps.round() as i64));
    out.push_str(&format!("x={:.3}\ny={:.3}\nz={:.3}\n", hud.pos[0], hud.pos[1], hud.pos[2]));
    let yaw = hud.yaw.rem_euclid(360.0);
    out.push_str(&format!("yaw={yaw:.1}\n"));

    let own = *OWN_UUID.lock().unwrap();
    if let Some(row) = snap.tab_list.rows.iter().find(|r| r.uuid == own && own != 0) {
        out.push_str(&format!("ping={}\n", row.latency.max(0)));
    }

    // Armor: helmet, chestplate, leggings, boots, as "name|durability left" or "-".
    let armor: Vec<String> = (0..4)
        .map(|i| match snap.armor.get(i) {
            Some(stack) if !stack.item.is_empty() && stack.count > 0 => {
                format!("{}|{}", safe(&name(stack)), durability(stack))
            }
            _ => "-".to_owned(),
        })
        .collect();
    out.push_str(&format!("armor={}\n", armor.join("~")));

    let potions: Vec<String> = snap
        .active_effects
        .iter()
        .map(|e| {
            let label = crate::play::mob_effects::display_name(&e.id, 0);
            format!("{}|{}|{}", safe(&label), e.amplifier, (e.duration / 20).max(0))
        })
        .collect();
    out.push_str(&format!("potions={}\n", potions.join("~")));

    let held = snap.hotbar(snap.selected as usize);
    if held.item.is_empty() || held.count == 0 {
        out.push_str("held=-\n");
    } else {
        out.push_str(&format!("held={}|{}|{}\n", safe(&name(held)), held.count, durability(held)));
    }
    let (sprint, sneak) = crate::tc_mods::toggled();
    let mode = |on: bool| if on { "toggled" } else { "vanilla" };
    out.push_str(&format!("sprintmode={}\nsneakmode={}\n", mode(sprint), mode(sneak)));
    crate::gui::tc_pvp::hud(&mut out); // Reach Display, Combo Counter, Item Counter
    set_hud(out);
}

fn name(stack: &crate::session::SlotStack) -> String {
    crate::gui::tooltip::styled_hover_name(stack)
        .iter()
        .map(|s| s.text.as_str())
        .collect::<String>()
}

/// The fraction of durability left, or -1 for items that don't wear out.
fn durability(stack: &crate::session::SlotStack) -> String {
    if stack.max_damage == 0 || stack.unbreakable {
        return "-1".to_owned();
    }
    let left = 1.0 - f64::from(stack.damage) / f64::from(stack.max_damage);
    format!("{left:.3}")
}

/// The HUD format uses `\n`, `~` and `|` as separators.
fn safe(text: &str) -> String {
    text.chars()
        .map(|c| if matches!(c, '\n' | '~' | '|') { ' ' } else { c })
        .collect()
}
