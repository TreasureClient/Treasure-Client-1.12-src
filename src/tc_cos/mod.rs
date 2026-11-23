//! Treasure Client cosmetics: what you're wearing (cape, aura, hat, boots, and what you made in
//! the Maker), and what other Treasure Client players are wearing. The same as the JS and WASM
//! editions (`net.minecraft.client.tccos.TCCosmetics`), so a look made in one edition shows the
//! same in the others.
//!
//! A look is one short line, the same in your storage (`tccos`) and on the sharing server:
//! `c=frost;a=halo;ac=gold;h=crown;hc=natural;b=rocket;bc=natural;bt=1;mk=...;as=normal;sp=normal;acc=ffd76b;ak=...;hk=...;bk=...`
//!
//! Sharing goes through the page overlay (it makes the web requests):
//! - game -> overlay: `tccospub` = "name\nlook" (your look, when it changes); `tccosreq` = names to look up
//! - overlay -> game: `tccosdata` = lines of "name=look" ("name=" = not a Treasure Client player)

pub mod auras;
pub mod capeart;
pub mod shapes;

use std::collections::HashMap;
use std::sync::{Arc, Mutex};

pub const CAPES: &[&str] = &["none", "treasure", "frost", "tropical", "ember", "aurora", "galaxy", "circuit", "theme", "custom"];
pub const AURAS: &[&str] = &["none", "halo", "orbit", "rising", "ripple", "helix", "custom"];
pub const COLORS: &[&str] = &["theme", "gold", "ice", "aqua", "ember", "violet", "rose", "lime", "white", "rainbow"];
const COLOR_RGB: &[u32] = &[0, 0xFFD76B, 0x7FE9FF, 0x45E3C6, 0xFF7A3C, 0xB38CFF, 0xFF6FA8, 0x9BEA5B, 0xF4F4F4, 0];
pub const HATS: &[&str] = &["none", "crown", "tophat", "wizard", "beanie", "catears", "horns", "propeller", "angel", "custom"];
pub const BOOTS: &[&str] = &["none", "sneakers", "rocket", "winged", "cloud", "frost", "magma", "custom"];
pub const WEAR_COLORS: &[&str] = &["natural", "theme", "gold", "ice", "aqua", "ember", "violet", "rose", "lime", "white", "black", "rainbow"];
const WEAR_RGB: &[u32] = &[0, 0, 0xFFD76B, 0x7FE9FF, 0x45E3C6, 0xFF7A3C, 0xB38CFF, 0xFF6FA8, 0x9BEA5B, 0xF4F4F4, 0x26262B, 0];
pub const SIZES: &[&str] = &["small", "normal", "big"];
pub const SPEEDS: &[&str] = &["calm", "normal", "lively"];

// the Maker's cape: pattern.colour1.colour2.emblem.motion
pub const PATTERNS: &[&str] = &["solid", "fade", "stripes", "chevron", "checks", "waves", "stars", "split"];
pub const PALETTE: &[&str] = &["theme", "black", "white", "gold", "red", "orange", "yellow", "lime", "green", "aqua", "blue", "violet", "pink"];
const PALETTE_RGB: &[u32] = &[0, 0x16161A, 0xF0F0EA, 0xFFD76B, 0xE8434F, 0xFF8A3C, 0xFFE066, 0x9BEA5B, 0x2FBF71, 0x45E3C6, 0x4A8CFF, 0x9A6BFF, 0xFF6FA8];
pub const EMBLEMS: &[&str] = &["none", "star", "heart", "diamond", "bolt", "moon", "crown", "sword"];
pub const MOTIONS: &[&str] = &["still", "shine", "flow", "pulse", "sparkle"];
pub const DEFAULT_MK: &str = "fade.theme.black.star.shine";
// the Maker's aura: base.particles.colour1.colour2.height (colours from COLORS)
pub const AK_BASES: &[&str] = &["none", "ring", "glow", "ripple", "double"];
pub const AK_PARTS: &[&str] = &["none", "orbit", "rising", "helix", "sparkle", "snow"];
pub const AK_HEIGHTS: &[&str] = &["low", "waist", "head"];
pub const DEFAULT_AK: &str = "ring.sparkle.theme.white.waist";
// the Maker's hat: base.top.trim.colour1.colour2 (colours from PALETTE)
pub const HK_BASES: &[&str] = &["none", "cap", "beanie", "brim", "crown", "band"];
pub const HK_TOPS: &[&str] = &["none", "pompom", "cone", "propeller", "ears", "horns", "antenna", "halo"];
pub const HK_TRIMS: &[&str] = &["none", "stripe", "gems", "stars"];
pub const DEFAULT_HK: &str = "cap.pompom.stripe.theme.white";
// the Maker's boots: shape.extra.trail.colour1.colour2 (colours from PALETTE)
pub const BK_SHAPES: &[&str] = &["sneaker", "boot", "chunky", "cloud"];
pub const BK_EXTRAS: &[&str] = &["none", "wings", "crystals", "cracks", "nozzle"];
pub const BK_TRAILS: &[&str] = &["none", "prints", "puffs", "flames", "sparks"];
pub const DEFAULT_BK: &str = "sneaker.none.sparks.white.theme";

pub fn index_of(list: &[&str], v: &str) -> Option<usize> {
    list.iter().position(|x| *x == v)
}

/// A Maker design is parts joined with dots, each part from its own list.
fn valid_spec(s: &str, lists: &[&[&str]]) -> bool {
    if s.len() > 80 {
        return false;
    }
    let parts: Vec<&str> = s.split('.').collect();
    parts.len() == lists.len() && parts.iter().zip(lists).all(|(p, l)| index_of(l, p).is_some())
}

pub fn valid_mk(s: &str) -> bool {
    s.len() <= 60 && valid_spec(s, &[PATTERNS, PALETTE, PALETTE, EMBLEMS, MOTIONS])
}

pub fn valid_ak(s: &str) -> bool {
    valid_spec(s, &[AK_BASES, AK_PARTS, COLORS, COLORS, AK_HEIGHTS])
}

pub fn valid_hk(s: &str) -> bool {
    valid_spec(s, &[HK_BASES, HK_TOPS, HK_TRIMS, PALETTE, PALETTE])
}

pub fn valid_bk(s: &str) -> bool {
    valid_spec(s, &[BK_SHAPES, BK_EXTRAS, BK_TRAILS, PALETTE, PALETTE])
}

/// A palette colour as 0xRRGGBB ("theme" = the given accent).
pub fn palette_rgb(id: &str, accent: u32) -> u32 {
    match index_of(PALETTE, id) {
        Some(i) if i > 0 => PALETTE_RGB[i],
        _ => accent & 0xFF_FFFF,
    }
}

/// Your theme's colour as 0xRRGGBB.
pub fn my_accent() -> u32 {
    crate::gui::tc_style::accent() & 0xFF_FFFF
}

/// A clock in ms for animations and timers.
pub fn now_ms() -> u64 {
    #[cfg(target_arch = "wasm32")]
    {
        js_sys::Date::now() as u64
    }
    #[cfg(not(target_arch = "wasm32"))]
    {
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_millis() as u64)
            .unwrap_or(0)
    }
}

pub fn hsv(h: f32, s: f32, v: f32) -> [f32; 3] {
    let h = (h - h.floor()) * 6.0;
    let i = h as i32;
    let f = h - i as f32;
    let (p, q, u) = (v * (1.0 - s), v * (1.0 - s * f), v * (1.0 - s * (1.0 - f)));
    match i {
        0 => [v, u, p],
        1 => [q, v, p],
        2 => [p, v, u],
        3 => [p, q, v],
        4 => [u, p, v],
        _ => [v, p, q],
    }
}

fn c8(v: f32) -> u32 {
    ((v * 255.0 + 0.5) as i32).clamp(0, 255) as u32
}

pub fn rainbow(ms: u64, offset: f32) -> u32 {
    let c = hsv((ms % 7000) as f32 / 7000.0 + offset, 0.6, 1.0);
    (c8(c[0]) << 16) | (c8(c[1]) << 8) | c8(c[2])
}

/// One player's look.
#[derive(Clone, Debug, PartialEq)]
pub struct Look {
    pub cape: String,
    pub aura: String,
    pub color: String,
    pub hat: String,
    pub hat_color: String,
    pub boots: String,
    pub boot_color: String,
    pub trail: bool,
    pub mk: String,
    pub aura_size: String,
    pub aura_speed: String,
    pub ak: String,
    pub hk: String,
    pub bk: String,
    /// Their theme colour (0xRRGGBB), or None = yours, live.
    pub acc: Option<u32>,
}

impl Default for Look {
    fn default() -> Self {
        Look {
            cape: "none".into(),
            aura: "none".into(),
            color: "theme".into(),
            hat: "none".into(),
            hat_color: "natural".into(),
            boots: "none".into(),
            boot_color: "natural".into(),
            trail: true,
            mk: DEFAULT_MK.into(),
            aura_size: "normal".into(),
            aura_speed: "normal".into(),
            ak: DEFAULT_AK.into(),
            hk: DEFAULT_HK.into(),
            bk: DEFAULT_BK.into(),
            acc: None,
        }
    }
}

impl Look {
    pub fn has_cape(&self) -> bool {
        self.cape != "none"
    }
    pub fn has_aura(&self) -> bool {
        self.aura != "none"
    }
    pub fn has_hat(&self) -> bool {
        self.hat != "none"
    }
    pub fn has_boots(&self) -> bool {
        self.boots != "none"
    }
    pub fn is_empty(&self) -> bool {
        !self.has_cape() && !self.has_aura() && !self.has_hat() && !self.has_boots()
    }

    pub fn ak_parts(&self) -> Vec<&str> {
        self.ak.split('.').collect()
    }
    pub fn hk_parts(&self) -> Vec<&str> {
        self.hk.split('.').collect()
    }
    pub fn bk_parts(&self) -> Vec<&str> {
        self.bk.split('.').collect()
    }

    /// The id to draw the cape from ("custom:<maker cape>" for the Maker's).
    pub fn cape_art_id(&self) -> String {
        if self.cape == "custom" {
            format!("custom:{}", self.mk)
        } else {
            self.cape.clone()
        }
    }

    /// Their theme colour as 0xRRGGBB.
    pub fn accent(&self) -> u32 {
        self.acc.unwrap_or_else(my_accent)
    }

    /// What the boots leave behind: frost, magma, prints, puffs, flames, sparks, or None.
    pub fn trail_kind(&self) -> Option<&str> {
        match self.boots.as_str() {
            "frost" => Some("frost"),
            "magma" => Some("magma"),
            "cloud" => Some("puffs"),
            "rocket" => Some("flames"),
            "winged" => Some("sparks"),
            "custom" => self.bk.split('.').nth(2).filter(|t| *t != "none"),
            _ => None,
        }
    }

    pub fn has_trail(&self) -> bool {
        self.trail && self.trail_kind().is_some()
    }

    /// The trail's colour (0xRRGGBB), or None for the boots' own.
    pub fn trail_tint(&self, ms: u64) -> Option<u32> {
        if self.boots == "custom" {
            return Some(palette_rgb(self.bk.split('.').nth(4).unwrap_or("theme"), self.accent()));
        }
        self.wear_rgb(&self.boot_color, None, ms)
    }

    /// An aura colour id (from COLORS) as 0xRRGGBB right now.
    pub fn color_rgb(&self, id: &str, ms: u64, offset: f32) -> u32 {
        match index_of(COLORS, id) {
            None | Some(0) => self.accent(),
            Some(_) if id == "rainbow" => rainbow(ms, offset),
            Some(i) => COLOR_RGB[i],
        }
    }

    pub fn aura_rgb(&self, ms: u64, offset: f32) -> u32 {
        self.color_rgb(&self.color, ms, offset)
    }

    /// A hat or boots colour as 0xRRGGBB, or the given natural colour.
    pub fn wear_rgb(&self, id: &str, natural: Option<u32>, ms: u64) -> Option<u32> {
        match index_of(WEAR_COLORS, id) {
            None | Some(0) => natural,
            Some(1) => Some(self.accent()),
            Some(_) if id == "rainbow" => Some(rainbow(ms, 0.0)),
            Some(i) => Some(WEAR_RGB[i]),
        }
    }

    pub fn aura_scale(&self) -> f32 {
        match self.aura_size.as_str() {
            "small" => 0.78,
            "big" => 1.28,
            _ => 1.0,
        }
    }

    pub fn aura_speed_mul(&self) -> f32 {
        match self.aura_speed.as_str() {
            "calm" => 0.55,
            "lively" => 1.7,
            _ => 1.0,
        }
    }

    /// The one-line form (stored and shared).
    pub fn encode(&self) -> String {
        format!(
            "c={};a={};ac={};h={};hc={};b={};bc={};bt={};mk={};as={};sp={};acc={:06x};ak={};hk={};bk={}",
            self.cape,
            self.aura,
            self.color,
            self.hat,
            self.hat_color,
            self.boots,
            self.boot_color,
            if self.trail { "1" } else { "0" },
            self.mk,
            self.aura_size,
            self.aura_speed,
            self.accent(),
            self.ak,
            self.hk,
            self.bk
        )
    }

    /// Sets one field from a key ("c"/"cape", "hc", ...) and a value; false if either isn't known.
    pub fn set_field(&mut self, k: &str, v: &str, with_accent: bool) -> bool {
        fn pick(list: &[&str], v: &str, into: &mut String) -> bool {
            if index_of(list, v).is_none() {
                return false;
            }
            *into = v.to_owned();
            true
        }
        match k {
            "c" | "cape" => pick(CAPES, v, &mut self.cape),
            "a" | "aura" => pick(AURAS, v, &mut self.aura),
            "ac" | "color" => pick(COLORS, v, &mut self.color),
            "h" | "hat" => pick(HATS, v, &mut self.hat),
            "hc" | "hatc" => pick(WEAR_COLORS, v, &mut self.hat_color),
            "b" | "boots" => pick(BOOTS, v, &mut self.boots),
            "bc" | "bootc" => pick(WEAR_COLORS, v, &mut self.boot_color),
            "bt" | "trail" => match v {
                "1" | "0" => {
                    self.trail = v == "1";
                    true
                }
                _ => false,
            },
            "mk" if valid_mk(v) => {
                self.mk = v.to_owned();
                true
            }
            "ak" if valid_ak(v) => {
                self.ak = v.to_owned();
                true
            }
            "hk" if valid_hk(v) => {
                self.hk = v.to_owned();
                true
            }
            "bk" if valid_bk(v) => {
                self.bk = v.to_owned();
                true
            }
            "as" | "asize" => pick(SIZES, v, &mut self.aura_size),
            "sp" | "aspeed" => pick(SPEEDS, v, &mut self.aura_speed),
            "acc" if with_accent && v.len() == 6 => match u32::from_str_radix(v, 16) {
                Ok(rgb) => {
                    self.acc = Some(rgb & 0xFF_FFFF);
                    true
                }
                Err(_) => false,
            },
            _ => false,
        }
    }

    /// Reads a look; unknown names and values are skipped (so older or newer clients never break it).
    pub fn parse(s: &str, with_accent: bool) -> Look {
        let mut l = Look::default();
        for part in s.split([';', '\n']) {
            if let Some((k, v)) = part.split_once('=')
                && !k.trim().is_empty()
            {
                l.set_field(k.trim(), v.trim(), with_accent);
            }
        }
        l
    }
}

pub fn valid_name(n: &str) -> bool {
    (1..=16).contains(&n.len()) && n.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'_')
}

// ------------------------------------------------------------------ storage

#[cfg(target_arch = "wasm32")]
pub(crate) fn storage_get(key: &str) -> Option<String> {
    use base64::Engine as _;
    let raw = web_sys::window()?
        .local_storage()
        .ok()??
        .get_item(&format!("_eaglercraft_1.12.{key}"))
        .ok()??;
    let bytes = base64::engine::general_purpose::STANDARD.decode(raw.trim()).ok()?;
    String::from_utf8(bytes).ok()
}

#[cfg(target_arch = "wasm32")]
pub(crate) fn storage_set(key: &str, value: Option<&str>) {
    use base64::Engine as _;
    let Some(s) = web_sys::window().and_then(|w| w.local_storage().ok().flatten()) else {
        return;
    };
    let k = format!("_eaglercraft_1.12.{key}");
    let _ = match value {
        Some(v) => s.set_item(&k, &base64::engine::general_purpose::STANDARD.encode(v.as_bytes())),
        None => s.remove_item(&k),
    };
}

#[cfg(not(target_arch = "wasm32"))]
pub(crate) fn storage_get(_key: &str) -> Option<String> {
    None
}

#[cfg(not(target_arch = "wasm32"))]
pub(crate) fn storage_set(_key: &str, _value: Option<&str>) {}

// ------------------------------------------------------------------ yours and others'

struct Other {
    look: Option<Arc<Look>>,
    at: Option<u64>,
    asked_at: u64,
    pending: bool,
}

#[derive(Default)]
struct State {
    mine: Option<Arc<Look>>,
    /// True once you've picked anything (until then nothing is shared, so your name isn't claimed for nothing).
    ever_set: bool,
    others: HashMap<String, Other>,
    pending: Vec<String>,
    last_poll: u64,
    last_pub_check: u64,
    last_data: Option<String>,
    last_pub: Option<String>,
    my_name: String,
}

static STATE: Mutex<Option<State>> = Mutex::new(None);

/// How long another player's look is kept before asking again (they may have changed it).
const KEEP_MS: u64 = 180_000;

fn with<R>(f: impl FnOnce(&mut State) -> R) -> R {
    let mut guard = STATE.lock().unwrap();
    f(guard.get_or_insert_with(State::default))
}

fn load_mine(st: &mut State) -> Arc<Look> {
    if st.mine.is_none() {
        let s = storage_get("tccos").unwrap_or_default();
        st.ever_set = !s.is_empty();
        let mut l = Look::parse(&s, false);
        l.acc = None;
        st.mine = Some(Arc::new(l));
    }
    st.mine.clone().unwrap()
}

/// Your own cosmetics.
pub fn mine() -> Arc<Look> {
    with(load_mine)
}

/// Changes one of your cosmetics ("cape", "aura", "color", "hat", "hatc", "boots", "bootc",
/// "trail", "mk", "ak", "hk", "bk", "asize", "aspeed") and saves it.
pub fn set(what: &str, id: &str) -> bool {
    with(|st| {
        let mut m = (*load_mine(st)).clone();
        if what == "acc" || !m.set_field(what, id, false) {
            return false;
        }
        // editing a Maker design puts it on
        match what {
            "mk" => m.cape = "custom".into(),
            "ak" => m.aura = "custom".into(),
            "hk" => m.hat = "custom".into(),
            "bk" => m.boots = "custom".into(),
            _ => {}
        }
        st.ever_set = true;
        storage_set("tccos", Some(&m.encode()));
        st.mine = Some(Arc::new(m));
        true
    })
}

/// The name you play as (for sharing your look).
pub fn set_my_name(name: &str) {
    with(|st| {
        if st.my_name != name {
            st.my_name = name.to_owned();
        }
    });
}

pub fn my_name() -> String {
    with(|st| st.my_name.clone())
}

/// What another player is wearing (by their name), or None.
pub fn of_name(name: &str) -> Option<Arc<Look>> {
    if !valid_name(name) {
        return None;
    }
    let now = now_ms();
    with(|st| {
        if name.eq_ignore_ascii_case(&st.my_name) {
            return Some(load_mine(st));
        }
        let key = name.to_ascii_lowercase();
        if !st.others.contains_key(&key) && st.others.len() > 600 {
            st.others.clear();
        }
        let o = st.others.entry(key).or_insert(Other {
            look: None,
            at: None,
            asked_at: 0,
            pending: false,
        });
        if o.pending && now.saturating_sub(o.asked_at) > 20_000 {
            o.pending = false; // no answer (offline?): try again later
            o.at = Some(now);
        }
        if !o.pending && o.at.is_none_or(|at| now.saturating_sub(at) > KEEP_MS) {
            o.pending = true;
            o.asked_at = now;
            st.pending.push(name.to_owned());
        }
        o.look.clone()
    })
}

/// Called every frame: shares your look when it changes, and swaps lookups with the overlay
/// (at most every half second).
pub fn tick() {
    let now = now_ms();
    with(|st| {
        if now.saturating_sub(st.last_poll) < 500 {
            return;
        }
        st.last_poll = now;
        if !st.pending.is_empty() {
            let mut list = storage_get("tccosreq").filter(|s| s.len() < 4000).unwrap_or_default();
            for n in st.pending.drain(..) {
                if !list.is_empty() {
                    list.push(',');
                }
                list.push_str(&n);
            }
            storage_set("tccosreq", Some(&list));
        }
        let data = storage_get("tccosdata").unwrap_or_default();
        if st.last_data.as_deref() != Some(data.as_str()) {
            for line in data.lines() {
                let Some((name, v)) = line.split_once('=') else { continue };
                let Some(o) = st.others.get_mut(&name.to_ascii_lowercase()) else { continue };
                let l = (!v.is_empty()).then(|| Look::parse(v, true)).filter(|l| !l.is_empty());
                o.look = l.map(Arc::new);
                o.pending = false;
                o.at = Some(now);
            }
            st.last_data = Some(data);
        }
        if now.saturating_sub(st.last_pub_check) >= 2000 {
            st.last_pub_check = now;
            let m = load_mine(st);
            if st.ever_set && valid_name(&st.my_name) {
                let s = format!("{}\n{}", st.my_name, m.encode());
                if st.last_pub.as_deref() != Some(s.as_str()) {
                    storage_set("tccospub", Some(&s));
                    st.last_pub = Some(s);
                }
            }
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn look_round_trips() {
        let mut l = Look::parse("c=frost;a=custom;h=custom;hk=brim.antenna.gems.black.aqua;b=custom;bk=cloud.wings.puffs.white.pink;acc=ffd76b", true);
        assert_eq!(l.cape, "frost");
        assert_eq!(l.hat, "custom");
        assert_eq!(l.hk, "brim.antenna.gems.black.aqua");
        assert_eq!(l.trail_kind(), Some("puffs"));
        assert_eq!(l.acc, Some(0xFFD76B));
        let again = Look::parse(&l.encode(), true);
        assert_eq!(again, l);
        assert!(!l.set_field("hk", "brim.nope.gems.black.aqua", false));
        assert!(l.encode().len() < 300);
    }
}
