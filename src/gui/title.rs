use std::sync::OnceLock;

use crate::gui::painter::Painter;
use crate::gui::tooltip::label;
use crate::gui::widgets::{Button, WIDGET_HEIGHT, WIDGET_WIDTH_BIG, centered_x};
use crate::gui::{GuiState, Screen, ScreenCtx};
use crate::platform::time::{SystemTime, UNIX_EPOCH};
use crate::text::{Span, Style, texts};

#[cfg(feature = "asset_download")]
const LANGUAGE_X_OFFSET: f32 = 124.0;
#[cfg(feature = "asset_download")]
const LANGUAGE_ICON: (f32, f32) = (15.0, 15.0);
const EDIT_PROFILE: &str = "Edit Profile";
const PROFILE_LABEL_PAD: f32 = 8.0;

const LOGO_W: f32 = 256.0;
const LOGO_H: f32 = 44.0;
const LOGO_Y: f32 = 30.0;
const EDITION_W: f32 = 128.0;
const EDITION_H: f32 = 14.0;
const EDITION_OVERLAP: f32 = 7.0;

const BOTTOM_ROW_STEP: f32 = 36.0;

const HALF_WIDTH: f32 = 98.0;
#[allow(dead_code, reason = "asserted on by this module's layout test")]
const BUTTON_GAP: f32 = 4.0;



fn first_row_y(vh: f32) -> f32 {
    (vh / 4.0).floor() + 48.0
}

pub(crate) fn logo(p: &mut Painter, vw: f32, y: f32) {
    p.sprite(
        "title/minecraft",
        (vw / 2.0 - LOGO_W / 2.0).floor(),
        y,
        LOGO_W,
        LOGO_H,
    );
    // Treasure Client: our edition line (gold) instead of "Java Edition".
    let _ = (EDITION_W, EDITION_H);
    p.text_centered(
        TC_EDITION,
        (vw / 2.0).floor(),
        y + LOGO_H - EDITION_OVERLAP + 3.0,
        TC_GOLD,
        true,
    );
}

/// Treasure Client branding (the Rust edition is based on Torch Client).
pub(crate) const TC_EDITION: &str = "Treasure Client Rust (Beta)";
pub(crate) const TC_GOLD: u32 = 0xFFD76B;

pub fn draw(p: &mut Painter, state: &mut GuiState, ctx: &ScreenCtx) {
    // Treasure Client: the Treasure Client title screen (the same layout and look as
    // the JS and WASM editions' main menu).
    use crate::gui::tc_style;
    tc_style::title_background(p, ctx.vw, ctx.vh);

    // Treasure Client 1.5: the page overlay draws the main menu words (the same menu as the
    // JS and WASM editions) and the game draws your player under the name. The buttons
    // below only come back if the overlay doesn't answer within a second and a half.
    #[cfg(target_arch = "wasm32")]
    {
        let name = state
            .profile
            .accounts
            .active()
            .map(|a| a.name.clone())
            .unwrap_or_else(|| "Player".to_owned());
        // Treasure Client cosmetics: the overlay draws the Cosmetics page; the game draws your
        // player on the right wearing what you pick.
        if crate::gui::tc_bridge::cos_is_open() {
            let (acts, live) = crate::gui::tc_bridge::cos_frame(&name);
            for act in acts {
                match act.as_str() {
                    "back" => crate::gui::tc_bridge::cos_close(),
                    "skin" => {
                        crate::gui::tc_bridge::cos_close();
                        menu_action(state, "skin");
                    }
                    other => {
                        if let Some((k, v)) = other.split_once(':') {
                            crate::tc_cos::set(k.trim(), v.trim());
                        }
                    }
                }
            }
            if !live {
                // the page isn't there: the old skin screen instead
                crate::gui::tc_bridge::cos_close();
                menu_action(state, "skin");
            }
            #[cfg(eagler_skins)]
            if crate::gui::tc_bridge::cos_is_open() {
                let h = ctx.vh * 0.46;
                let w = h * 0.6;
                let cx = (ctx.vw * 0.78).floor();
                crate::gui::profile::title_figure(p, state, ctx, [cx - w / 2.0, ctx.vh * 0.22, w, h]);
            }
            return;
        }
        let info = format!("{}\n\n\nRust Beta\n0\nnosp", name.replace('\n', " "));
        let shown_ms = crate::gui::tc_bridge::title_frame(&info);
        if let Some(action) = crate::gui::tc_bridge::take_menu_action() {
            menu_action(state, &action);
        }
        if crate::gui::tc_bridge::menu_overlay_live() || shown_ms < 1500 {
            #[cfg(eagler_skins)]
            {
                let h = ctx.vh * 0.40;
                let w = h * 0.6;
                let cx = (ctx.vw * 0.80).floor();
                crate::gui::profile::title_figure(p, state, ctx, [cx - w / 2.0, ctx.vh * 0.29, w, h]);
            }
            return;
        }
    }
    let (accent, dim, _) = tc_style::title_colors();
    let cx = (ctx.vw / 2.0).floor();

    // Chest logo crest, then the wordmark.
    let logo_h = 36.0;
    let logo_w = (logo_h * 221.0 / 224.0_f32).round();
    p.sprite(tc_style::logo_sprite(), cx - logo_w / 2.0, 1.0, logo_w, logo_h);
    let bold = |text: &str, color: u32| {
        [Span {
            text: text.to_owned(),
            style: Style { color: color & 0x00FF_FFFF, bold: true, ..Style::default() },
        }]
    };
    let plain = |text: &str, color: u32| {
        [Span {
            text: text.to_owned(),
            style: Style { color: color & 0x00FF_FFFF, ..Style::default() },
        }]
    };
    let centered = |p: &mut Painter, spans: &[Span], scale: f32, x: f32, y: f32| {
        let w = p.atlas.font.width(spans);
        p.scaled(scale, x, y, |p| {
            p.text(spans, -w / 2.0, 0.0, false);
        });
    };
    centered(p, &bold("TREASURE", 0xFF00_0000), 2.5, cx + 2.5, 48.0 - 3.0 * 2.5);
    centered(p, &bold("TREASURE", accent), 2.5, cx, 48.0 - 4.0 * 2.5);
    centered(p, &plain("C L I E N T", 0xFF00_0000), 1.4, cx + 1.4, 72.0 + 1.4);
    centered(p, &plain("C L I E N T", dim), 1.4, cx, 72.0);

    if let Some(splash) = session_splash() {
        let millis = (SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_millis())
            .unwrap_or(0)
            % 1000) as f32;
        let pulse = 1.0 - 0.08 * (millis / 1000.0 * std::f32::consts::TAU).sin().abs();
        let spans = plain(splash, accent);
        let w = p.atlas.font.width(&spans);
        p.scaled(pulse, cx, 90.0, |p| {
            p.text(&spans, -w / 2.0, 0.0, true);
        });
    }

    let x = centered_x(ctx.vw);
    let top = first_row_y(ctx.vh);
    let row = 24.0;

    #[cfg(feature = "online_mode")]
    crate::client::ban::check_once();
    // Singleplayer comes with the 1.12 world (a later stage).
    Button {
        active: false,
        ..Button::new(x, top, WIDGET_WIDTH_BIG, WIDGET_HEIGHT, label!("menu.singleplayer"))
    }
    .draw(p, ctx);
    if Button::new(x, top + row, WIDGET_WIDTH_BIG, WIDGET_HEIGHT, label!("menu.multiplayer"))
        .draw(p, ctx)
    {
        state.nav = Some(Screen::Multiplayer);
        #[cfg(feature = "online_mode")]
        crate::gui::bannotice::before_multiplayer(state);
    }
    if Button::new(x, top + row * 2.0, WIDGET_WIDTH_BIG, WIDGET_HEIGHT, label!("menu.options"))
        .draw(p, ctx)
    {
        state.options_parent = Screen::Title;
        state.nav = Some(Screen::Options);
    }

    let mods_y = top + 72.0 + 12.0;
    #[cfg(feature = "asset_download")]
    if crate::gui::widgets::icon_button(
        p,
        ctx,
        cx - LANGUAGE_X_OFFSET,
        mods_y,
        WIDGET_HEIGHT,
        WIDGET_HEIGHT,
        "icon/language",
        LANGUAGE_ICON,
    ) {
        state.language.open();
        state.nav = Some(Screen::Language);
    }
    if Button::new(x, mods_y, HALF_WIDTH, WIDGET_HEIGHT, "Mods").draw(p, ctx) {
        open_mod_menu();
    }
    if Button::new(cx + 2.0, mods_y, HALF_WIDTH, WIDGET_HEIGHT, EDIT_PROFILE).draw(p, ctx) {
        #[cfg(eagler_skins)]
        crate::gui::profile::open_edit(state, Screen::Title);
        #[cfg(not(eagler_skins))]
        {
            state.profile.open();
            state.nav = Some(Screen::Accounts);
        }
    }
    // Profiles: your face in the top right.
    #[cfg(eagler_skins)]
    crate::gui::profile::title_button(p, state, ctx);
    if Button::new(x, mods_y + 24.0, WIDGET_WIDTH_BIG, WIDGET_HEIGHT, "Treasure Settings")
        .draw(p, ctx)
    {
        open_mod_menu();
    }

    // Bottom left: the edition, and where it comes from (with the credits).
    p.text_plain(TC_EDITION, 2.0, ctx.vh - 20.0, dim & 0x00FF_FFFF, true);
    let line = "Eaglercraft 1.12.2 \u{2022} based on Torch";
    let credits_x = p.text_plain(line, 2.0, ctx.vh - 10.0, dim & 0x00FF_FFFF, true)
        + p.atlas.font.width_str(" ");
    if credits_link(p, ctx, credits_x, ctx.vh - 10.0) {
        state.credits_parent = Screen::Title;
        state.nav = Some(Screen::Credits);
    }
    let _ = (LOGO_W, LOGO_H, LOGO_Y, BOTTOM_ROW_STEP, PROFILE_LABEL_PAD, DISCLAIMER, EAGLER_MODE);

    #[cfg(all(feature = "mobile_ui", target_arch = "wasm32"))]
    {
        const W: f32 = 90.0;
        if crate::platform::fullscreen::available() {
            let label = if crate::platform::fullscreen::is_fullscreen() {
                "Exit Fullscreen"
            } else {
                "Fullscreen"
            };
            if Button::new(ctx.vw - W - 4.0, 4.0, W, WIDGET_HEIGHT, label).draw(p, ctx) {
                crate::platform::fullscreen::toggle();
            }
        }
    }
}

/// A main menu word the overlay clicked (Treasure Client 1.5).
#[cfg(target_arch = "wasm32")]
fn menu_action(state: &mut GuiState, action: &str) {
    match action {
        "mp" => {
            state.nav = Some(Screen::Multiplayer);
            #[cfg(feature = "online_mode")]
            crate::gui::bannotice::before_multiplayer(state);
        }
        "set" => {
            state.options_parent = Screen::Title;
            state.nav = Some(Screen::Options);
        }
        "cos" => {
            #[cfg(target_arch = "wasm32")]
            crate::gui::tc_bridge::cos_open();
            #[cfg(not(target_arch = "wasm32"))]
            menu_action(state, "skin");
        }
        "skin" => {
            #[cfg(eagler_skins)]
            crate::gui::profile::open_edit(state, Screen::Title);
            #[cfg(not(eagler_skins))]
            {
                state.profile.open();
                state.nav = Some(Screen::Accounts);
            }
        }
        "prof" => {
            state.profile.open();
            state.nav = Some(Screen::Accounts);
        }
        #[cfg(feature = "asset_download")]
        "lang" => {
            state.language.open();
            state.nav = Some(Screen::Language);
        }
        "about" => {
            state.credits_parent = Screen::Title;
            state.nav = Some(Screen::Credits);
        }
        _ => return,
    }
    #[cfg(feature = "audio")]
    crate::gui::widgets::play_click();
}

/// Opens the Treasure Client Mod Menu (the overlay watches `tcopen`).
fn open_mod_menu() {
    #[cfg(target_arch = "wasm32")]
    crate::gui::tc_bridge::open_menu();
}

const DISCLAIMER: &str = "Not affiliated with Mojang or Microsoft.";
const CREDITS: &str = "(Open Credits)";

fn credits_link(p: &mut Painter, ctx: &ScreenCtx, x: f32, y: f32) -> bool {
    #[cfg(feature = "tutorial")]
    crate::gui::tutorial::mark(
        crate::gui::tutorial::Spot::Credits,
        crate::gui::hud_layout::Rect::new(
            x,
            y - 1.0,
            p.atlas.font.width_str(CREDITS),
            crate::text::LINE_HEIGHT + 1.0,
        ),
    );
    link(p, ctx, CREDITS, 0xFFFFFF, x, y)
}

fn link(p: &mut Painter, ctx: &ScreenCtx, text: &str, color: u32, x: f32, y: f32) -> bool {
    let w = p.atlas.font.width_str(text);
    let (hx, hy, hw, hh) = (x, y - 1.0, w, crate::text::LINE_HEIGHT + 1.0);
    let hovered = ctx.hovering(hx, hy, hw, hh);
    let span = Span {
        text: text.to_owned(),
        style: Style {
            color,
            underline: hovered,
            ..Style::default()
        },
    };
    p.text(std::slice::from_ref(&span), x, y, true);
    let clicked = crate::gui::widgets::click_in(ctx, hx, hy, hw, hh);
    #[cfg(feature = "audio")]
    if clicked {
        crate::gui::widgets::play_click();
    }
    clicked
}

const EAGLER_MODE: &str = "Eagler mode (wss:// enabled)";

/// The Treasure Client splashes (the same list as the JS and WASM editions).
const TREASURE_CLIENT_SPLASHES: [&str; 22] = [
    "Set sail!", "X marks the spot!", "Yo ho ho!", "Anchors aweigh!",
    "Dead men tell no tales...", "Thar she blows!", "Shiver me timbers!",
    "Hoist the colors!", "Avast, ye scallywags!", "Batten down the hatches!",
    "Land ho!", "All hands on deck!", "Buried treasure awaits!",
    "Dive for the gold!", "Fair winds and following seas!", "A pirate's life for ye!",
    "Hidden coves and buried chests!", "Beware the kraken!", "Walk the plank!",
    "Full sail ahead!", "Doubloons and pieces of eight!", "To the depths we go!",
];

/// A random Treasure Client splash, with the usual holiday ones.
fn tc_splash(seed: u64) -> String {
    #[cfg(target_arch = "wasm32")]
    {
        let date = js_sys::Date::new_0();
        match (date.get_month() + 1, date.get_date()) {
            (12, 24) => return "Merry X-mas!".to_owned(),
            (1, 1) => return "Happy new year!".to_owned(),
            (10, 31) => return "OOoooOOOoooo! Spooky!".to_owned(),
            _ => {}
        }
    }
    let mixed = seed.wrapping_mul(0x9E37_79B9_7F4A_7C15) >> 32;
    TREASURE_CLIENT_SPLASHES[(mixed % TREASURE_CLIENT_SPLASHES.len() as u64) as usize].to_owned()
}

fn session_splash() -> Option<&'static str> {
    static SPLASH: OnceLock<Option<String>> = OnceLock::new();
    SPLASH
        .get_or_init(|| {
            let seed = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map(|d| d.as_nanos() as u64)
                .unwrap_or(0);
            let _ = texts::splash_for;
            Some(tc_splash(seed))
        })
        .as_deref()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn first_row_matches_the_java_layout() {
        assert_eq!(first_row_y(240.0), 108.0);
        assert_eq!(first_row_y(480.0), 168.0);
    }

    #[test]
    fn the_bottom_row_lines_up_with_the_column() {
        let vw = 854.0;
        let left = centered_x(vw);
        let right = (vw / 2.0).floor() + 2.0;
        assert_eq!(left + HALF_WIDTH + BUTTON_GAP, right);
        assert_eq!(right + HALF_WIDTH, left + WIDGET_WIDTH_BIG);
    }

    #[test]
    fn the_bottom_row_sits_36_below_the_last_menu_row() {
        let top = first_row_y(240.0);
        assert_eq!(top + BOTTOM_ROW_STEP - (top + WIDGET_HEIGHT), 16.0);
    }
}
