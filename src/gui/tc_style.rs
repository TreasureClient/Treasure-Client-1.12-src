//! Treasure Client: the Treasure Client look for every menu, the same as the JS and WASM
//! editions (`TreasureButtons` and `TreasureBackground` there).
//!
//! - Buttons: rounded, a hollow outline in the theme's accent colour, filling with the
//!   accent when hovered (animated), dark text on the fill.
//! - Backgrounds: the picture picked for the theme (sharp, or blurred if the theme's
//!   "Blur background" is on), slowly panning, darker toward the bottom.
//!
//! The theme is the one picked in the Mod Menu's Themes tab (storage key `tctheme`); the overlay
//! also writes the theme's picture (`tcbg`, e.g. "sakura") and its blur switch (`tcbgblur` = 1).

use std::collections::HashMap;
use std::sync::{Mutex, OnceLock};

use crate::gui::painter::Painter;

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Theme {
    Treasure,
    Frost,
    Tropical,
    Terracotta,
    Forest,
    Sakura,
    Dark,
}

fn clock() -> f32 {
    static START: OnceLock<crate::platform::time::Instant> = OnceLock::new();
    START
        .get_or_init(crate::platform::time::Instant::now)
        .elapsed()
        .as_secs_f32()
}

/// The theme picked in the Mod Menu (checked twice a second).
pub fn theme() -> Theme {
    static CACHE: Mutex<(f32, Option<Theme>)> = Mutex::new((-1.0, None));
    let now = clock();
    let mut cache = CACHE.lock().unwrap();
    if let (at, Some(theme)) = *cache
        && now - at < 0.5
    {
        return theme;
    }
    let theme = match read_theme().as_deref() {
        Some("frost") => Theme::Frost,
        Some("tropical") => Theme::Tropical,
        Some("terracotta") => Theme::Terracotta,
        Some("forest") => Theme::Forest,
        Some("sakura") => Theme::Sakura,
        Some("dark") => Theme::Dark,
        _ => Theme::Treasure,
    };
    *cache = (now, Some(theme));
    theme
}

const BACKGROUNDS: [&str; 7] = ["treasure", "frost", "tropical", "terracotta", "forest", "sakura", "dark"];

/// The picture picked for the current theme and whether to blur it (checked twice a second).
fn background_choice() -> (&'static str, bool) {
    static CACHE: Mutex<(f32, Option<(&'static str, bool)>)> = Mutex::new((-1.0, None));
    let now = clock();
    let mut cache = CACHE.lock().unwrap();
    if let (at, Some(c)) = *cache
        && now - at < 0.5
    {
        return c;
    }
    let own = match theme() {
        Theme::Treasure => "treasure",
        Theme::Frost => "frost",
        Theme::Tropical => "tropical",
        Theme::Terracotta => "terracotta",
        Theme::Forest => "forest",
        Theme::Sakura => "sakura",
        Theme::Dark => "dark",
    };
    let picked = read_key("tcbg");
    let bg = BACKGROUNDS
        .iter()
        .copied()
        .find(|b| picked.as_deref() == Some(*b))
        .unwrap_or(own);
    let blur = read_key("tcbgblur").as_deref() == Some("1");
    *cache = (now, Some((bg, blur)));
    (bg, blur)
}

fn read_theme() -> Option<String> {
    read_key("tctheme")
}

#[cfg(target_arch = "wasm32")]
fn read_key(key: &str) -> Option<String> {
    use base64::Engine;
    let raw = web_sys::window()?
        .local_storage()
        .ok()??
        .get_item(&format!("_eaglercraft_1.12.{key}"))
        .ok()??;
    let bytes = base64::engine::general_purpose::STANDARD.decode(raw.trim()).ok()?;
    String::from_utf8(bytes).ok().map(|s| s.trim().to_string())
}

#[cfg(not(target_arch = "wasm32"))]
fn read_key(_key: &str) -> Option<String> {
    None
}

/// The theme's accent colour (ARGB).
pub fn accent() -> u32 {
    match theme() {
        Theme::Sakura => 0xFFFF9CC6,
        Theme::Dark => 0xFFB9B4E8,
        Theme::Tropical => 0xFF45E3C6,
        Theme::Frost => 0xFF7FE9FF,
        Theme::Terracotta => 0xFFDB7A3C,
        Theme::Forest => 0xFF6FCB5B,
        Theme::Treasure => 0xFFFFD76B,
    }
}

fn accent_deep() -> u32 {
    match theme() {
        Theme::Sakura => 0xFFE8679F,
        Theme::Dark => 0xFF8580C4,
        Theme::Tropical => 0xFF1AA7B0,
        Theme::Frost => 0xFF2EC4F1,
        Theme::Terracotta => 0xFFB85C2A,
        Theme::Forest => 0xFF2F8F3E,
        Theme::Treasure => 0xFFD4A537,
    }
}

/// Button text when the button is not filled.
pub fn text_idle() -> u32 {
    match theme() {
        Theme::Sakura => 0xFFFBEAF1,
        Theme::Dark => 0xFFECEAF4,
        Theme::Tropical => 0xFFF1E6C6,
        Theme::Frost => 0xFFDFF6FF,
        Theme::Terracotta => 0xFFF3E0CC,
        Theme::Forest => 0xFFE8F6D8,
        Theme::Treasure => 0xFFF6ECD2,
    }
}

fn text_on_fill() -> u32 {
    match theme() {
        Theme::Sakura => 0xFF2A0A18,
        Theme::Dark => 0xFF0C0B14,
        Theme::Tropical => 0xFF042024,
        Theme::Frost => 0xFF03101A,
        Theme::Terracotta => 0xFF2A1205,
        Theme::Forest => 0xFF07200C,
        Theme::Treasure => 0xFF0F0A05,
    }
}

/// The dark panel colour behind buttons and boxes.
pub fn panel_bg() -> u32 {
    match theme() {
        Theme::Sakura => 0xE6220C18,
        Theme::Dark => 0xE60E0E12,
        Theme::Tropical => 0xE6071D22,
        Theme::Frost => 0xE6031019,
        Theme::Terracotta => 0xE6241206,
        Theme::Forest => 0xE60C2410,
        Theme::Treasure => 0xE6140D06,
    }
}

pub fn lerp(a: u32, b: u32, t: f32) -> u32 {
    let t = t.clamp(0.0, 1.0);
    let ch = |shift: u32| {
        let x = ((a >> shift) & 0xFF) as f32;
        let y = ((b >> shift) & 0xFF) as f32;
        ((x + (y - x) * t) as u32) << shift
    };
    ch(24) | ch(16) | ch(8) | ch(0)
}

/// A filled rectangle with its corners cut (the Treasure Client rounded look).
pub fn rounded_rect(p: &mut Painter, x: f32, y: f32, w: f32, h: f32, color: u32) {
    if w <= 4.0 || h <= 4.0 {
        p.fill(x, y, w, h, color);
        return;
    }
    p.fill(x, y + 2.0, w, h - 4.0, color);
    p.fill(x + 2.0, y, w - 4.0, 1.0, color);
    p.fill(x + 1.0, y + 1.0, w - 2.0, 1.0, color);
    p.fill(x + 2.0, y + h - 1.0, w - 4.0, 1.0, color);
    p.fill(x + 1.0, y + h - 2.0, w - 2.0, 1.0, color);
}

/// A 1-pixel outline with cut corners, matching [`rounded_rect`].
pub fn rounded_outline(p: &mut Painter, x: f32, y: f32, w: f32, h: f32, color: u32) {
    if w <= 4.0 || h <= 4.0 {
        return;
    }
    p.fill(x + 2.0, y, w - 4.0, 1.0, color);
    p.fill(x + 2.0, y + h - 1.0, w - 4.0, 1.0, color);
    p.fill(x, y + 2.0, 1.0, h - 4.0, color);
    p.fill(x + w - 1.0, y + 2.0, 1.0, h - 4.0, color);
    p.fill(x + 1.0, y + 1.0, 1.0, 1.0, color);
    p.fill(x + w - 2.0, y + 1.0, 1.0, 1.0, color);
    p.fill(x + 1.0, y + h - 2.0, 1.0, 1.0, color);
    p.fill(x + w - 2.0, y + h - 2.0, 1.0, 1.0, color);
}

/// Each button's hover fill (0..1) and when it was last drawn, keyed by its rectangle.
fn fills() -> &'static Mutex<HashMap<(i32, i32, i32, i32), (f32, f32)>> {
    static FILLS: OnceLock<Mutex<HashMap<(i32, i32, i32, i32), (f32, f32)>>> = OnceLock::new();
    FILLS.get_or_init(Default::default)
}

/// Draws a Treasure Client button and returns its label colour and whether the label
/// wants a shadow.
pub fn button(p: &mut Painter, x: f32, y: f32, w: f32, h: f32, hovered: bool, enabled: bool) -> (u32, bool) {
    if !enabled {
        rounded_rect(p, x, y, w, h, 0x9900_0000);
        rounded_outline(p, x, y, w, h, 0xFF3A_3A3A);
        return (0xFF6A_6A6A, true);
    }
    let now = clock();
    let key = (x as i32, y as i32, w as i32, h as i32);
    let fill = {
        let mut fills = fills().lock().unwrap();
        if fills.len() > 512 {
            fills.retain(|_, (_, at)| now - *at < 2.0);
        }
        let (fill, at) = fills.entry(key).or_insert((0.0, now));
        let dt = (now - *at).clamp(0.0, 0.05);
        *at = now;
        let target = if hovered { 1.0 } else { 0.0 };
        let step = dt / 0.14;
        *fill = if *fill < target { (*fill + step).min(target) } else { (*fill - step).max(target) };
        *fill
    };
    let (acc, acc_lo) = (accent(), accent_deep());
    rounded_rect(p, x, y, w, h, lerp(panel_bg(), acc, fill));
    if fill > 0.01 {
        let tint = (lerp(panel_bg(), acc_lo, fill) & 0x00FF_FFFF) | (((0x55 as f32 * fill) as u32) << 24);
        p.fill(x + 1.0, y + (h / 2.0).floor(), w - 2.0, (h / 2.0).ceil() - 2.0, tint);
    }
    rounded_outline(p, x, y, w, h, lerp(acc, 0xFFFF_FFFF, fill * 0.25));
    (lerp(text_idle(), text_on_fill(), fill), fill < 0.5)
}

/// The theme's background picture over the whole screen (covering, slowly panning),
/// darkened toward the bottom so text stays readable.
pub fn background(p: &mut Painter, vw: f32, vh: f32) {
    background_with(p, vw, vh, 0x1400_0000, 0x5C00_0000);
}

fn background_with(p: &mut Painter, vw: f32, vh: f32, top: u32, bottom: u32) {
    let (bg, blur) = background_choice();
    let sprite = format!("tc_bg/{bg}{}", if blur { "_blur" } else { "" });
    // Cover the screen with a little room to pan (the pictures are 2:1).
    let scale = (vw / 2.0).max(vh) * 1.08;
    let (w, h) = (2.0 * scale, scale);
    let t = clock() / 40.0 * std::f32::consts::TAU;
    let x = (vw - w) / 2.0 + t.sin() * (w - vw) * 0.45;
    let y = (vh - h) / 2.0 + (t * 0.7).cos() * (h - vh) * 0.3;
    p.fill(0.0, 0.0, vw, vh, 0xFF00_0000);
    p.sprite(&sprite, x, y, w, h);
    p.gradient_v(0.0, 0.0, vw, vh, top, bottom);
}

/// The title screen's accent colours: (accent, dim accent, particle).
pub fn title_colors() -> (u32, u32, u32) {
    match theme() {
        Theme::Frost => (0xFF7FE9FF, 0xFF8FB9CC, 0x99EAF6FF),
        Theme::Tropical => (0xFF45E3C6, 0xFFE0CD97, 0x9945E3C6),
        Theme::Terracotta => (0xFFDB7A3C, 0xFFE8B98A, 0x99E89A5A),
        Theme::Forest => (0xFF6FCB5B, 0xFFBFE0A0, 0x99C8E88A),
        Theme::Sakura => (0xFFFF9CC6, 0xFFDDB0C4, 0x99FFB7D5),
        Theme::Dark => (0xFFB9B4E8, 0xFFA9A6BD, 0xAAFFD98A),
        Theme::Treasure => (0xFFFFD76B, 0xFFC9B183, 0x80FFD76B),
    }
}

/// The theme's chest logo sprite.
pub fn logo_sprite() -> &'static str {
    match theme() {
        Theme::Frost => "tc_logo/frost",
        Theme::Tropical => "tc_logo/tropical",
        Theme::Terracotta => "tc_logo/terracotta",
        Theme::Forest => "tc_logo/forest",
        Theme::Sakura => "tc_logo/sakura",
        Theme::Dark => "tc_logo/dark",
        Theme::Treasure => "tc_logo/normal",
    }
}

/// The title screen backdrop: the theme picture, lightly darkened near the bottom, and
/// the theme's particle field (snow on the wind, gold flecks falling, bubbles rising).
pub fn title_background(p: &mut Painter, vw: f32, vh: f32) {
    background_with(p, vw, vh, 0x0000_0000, 0x3300_0000);
    let t = clock() * 1000.0;
    let theme = theme();
    let frost = theme == Theme::Frost;
    let rises = matches!(theme, Theme::Tropical | Theme::Forest | Theme::Dark);
    let color = title_colors().2;
    let gust = if frost {
        ((t / 7000.0).sin() * 0.5 + (t / 2300.0).sin() * 0.5) * 34.0
    } else {
        0.0
    };
    let count = if frost { 50 } else { 30 };
    for i in 0..count {
        let fi = i as f32;
        let sx = ((i * 73 + 11) % 100) as f32 / 100.0;
        let speed = 0.25 + ((i * 37 + 7) % 100) as f32 / 100.0 * 0.55;
        let size = 1.0 + if frost { ((i * 29 + 5) % 2) as f32 } else { ((i * 53 + 3) % 2) as f32 };
        let sway = ((t / 1600.0) + fi).sin() * if frost { 7.0 } else { 12.0 };
        let lane = 0.4 + ((i * 17 + 9) % 100) as f32 / 100.0;
        let mut x = sx * vw + sway + if frost { gust * lane } else { 0.0 };
        if frost {
            let w = vw + 32.0;
            x = (x + 16.0).rem_euclid(w) - 16.0;
        }
        let phase = ((t / 2200.0) * speed + fi * 0.137).rem_euclid(1.0);
        let y = if rises { vh - phase * vh } else { phase * vh };
        p.fill(x.floor(), y.floor(), size, size, color);
    }
}

/// A dropdown list like the Eaglercraft skin and cape pickers: a box showing the picked
/// entry with an arrow button, opening a scrollable list below it.
#[derive(Default)]
pub struct Dropdown {
    pub open: bool,
    scroll: usize,
}

const DROP_W: f32 = 140.0;
const DROP_H: f32 = 22.0;
const ROW: f32 = 10.0;

impl Dropdown {
    /// The closed box; clicking it (or its arrow) opens and closes the list.
    pub fn draw_box(
        &mut self,
        p: &mut Painter,
        ctx: &crate::gui::ScreenCtx,
        x: f32,
        y: f32,
        label: &str,
    ) {
        let acc = accent();
        rounded_rect(p, x, y, DROP_W, DROP_H, panel_bg());
        rounded_outline(p, x, y, DROP_W, DROP_H, acc);
        p.fill(x + DROP_W - 21.0, y + 3.0, 1.0, DROP_H - 6.0, (acc & 0x00FF_FFFF) | 0x6000_0000);
        // The arrow (a small triangle pointing down, or up while open).
        let (ax, ay) = (x + DROP_W - 15.0, y + 9.0);
        for i in 0..4 {
            let row = if self.open { 3 - i } else { i };
            p.fill(ax + row as f32, ay + i as f32, 7.0 - 2.0 * row as f32, 1.0, acc);
        }
        let shown = p.atlas.font.width_str(label);
        let mut text = label.to_owned();
        if shown > DROP_W - 30.0 {
            while !text.is_empty() && p.atlas.font.width_str(&format!("{text}...")) > DROP_W - 30.0 {
                text.pop();
            }
            text.push_str("...");
        }
        p.text_plain(&text, x + 5.0, y + 7.0, text_idle() & 0x00FF_FFFF, true);
        if crate::gui::widgets::click_in(ctx, x + DROP_W - 21.0, y, 21.0, DROP_H)
            || (!self.open && crate::gui::widgets::click_in(ctx, x, y, DROP_W, DROP_H))
        {
            self.open = !self.open;
            #[cfg(feature = "audio")]
            crate::gui::widgets::play_click();
        }
    }

    /// The open list (drawn last so it covers the buttons below). Returns the entry
    /// clicked. Clicking anywhere else closes it.
    pub fn draw_list(
        &mut self,
        p: &mut Painter,
        ctx: &crate::gui::ScreenCtx,
        x: f32,
        y: f32,
        options: &[String],
        selected: usize,
    ) -> Option<usize> {
        if !self.open || options.is_empty() {
            return None;
        }
        let top = y + DROP_H - 1.0;
        let fit = (((ctx.vh - top - 10.0) / ROW).floor().max(1.0)) as usize;
        let visible = fit.min(options.len());
        let h = visible as f32 * ROW + 7.0;
        let max_scroll = options.len() - visible;
        if ctx.hovering(x, top, DROP_W, h) && ctx.input.scroll != 0.0 {
            if ctx.input.scroll > 0.0 {
                self.scroll = self.scroll.saturating_sub(3);
            } else {
                self.scroll += 3;
            }
        }
        self.scroll = self.scroll.min(max_scroll);
        rounded_rect(p, x, top, DROP_W, h, 0xF00A_0806);
        rounded_outline(p, x, top, DROP_W, h, accent());
        let mut picked = None;
        for i in 0..visible {
            let at = i + self.scroll;
            let ry = top + 4.0 + i as f32 * ROW;
            let hovered = ctx.hovering(x + 1.0, ry, DROP_W - 6.0, ROW);
            if at == selected {
                p.fill(x + 1.0, ry, DROP_W - 2.0, ROW, (accent() & 0x00FF_FFFF) | 0x7000_0000);
            } else if hovered {
                p.fill(x + 1.0, ry, DROP_W - 2.0, ROW, 0x55FF_FFFF);
            }
            p.text_plain(&options[at], x + 5.0, ry + 1.0, 0xE0E0E0, true);
            if hovered && ctx.input.left_click {
                picked = Some(at);
            }
        }
        if options.len() > visible {
            let bar = h * visible as f32 / options.len() as f32;
            let pos = h * self.scroll as f32 / options.len() as f32;
            p.fill(x + DROP_W - 4.0, top + pos + 1.0, 3.0, bar - 2.0, 0xFF88_8888);
        }
        let outside = ctx.input.left_click
            && !ctx.hovering(x, y, DROP_W, DROP_H + h);
        if picked.is_some() || outside {
            self.open = false;
        }
        picked
    }

    /// Scrolls the list so `selected` shows when it opens.
    pub fn show(&mut self, selected: usize) {
        self.scroll = selected.saturating_sub(2);
    }
}

/// An underlined link (like "Capes" on the Edit Profile screen). True when clicked.
pub fn link(p: &mut Painter, ctx: &crate::gui::ScreenCtx, text: &str, x: f32, y: f32, scale: f32) -> bool {
    use crate::text::{Span, Style};
    let w = p.atlas.font.width_str(text) * scale;
    let hovered = ctx.hovering(x - 2.0, y - 2.0, w + 4.0, 10.0 * scale + 2.0);
    let color = if hovered { 0xCCCC44 } else { 0xA0A0A0 };
    let span = [Span {
        text: text.to_owned(),
        style: Style { color, underline: true, ..Style::default() },
    }];
    p.scaled(scale, x, y, |p| {
        p.text(&span, 0.0, 0.0, true);
    });
    let clicked = crate::gui::widgets::click_in(ctx, x - 2.0, y - 2.0, w + 4.0, 10.0 * scale + 2.0);
    #[cfg(feature = "audio")]
    if clicked {
        crate::gui::widgets::play_click();
    }
    clicked
}

/// A box with the theme's accent outline and a dark inside (the skin preview frame).
pub fn frame(p: &mut Painter, x: f32, y: f32, w: f32, h: f32) {
    p.fill(x, y, w, h, accent());
    p.fill(x + 1.0, y + 1.0, w - 2.0, h - 2.0, 0xFF00_0015);
}
