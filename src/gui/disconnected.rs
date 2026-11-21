use crate::gui::painter::Painter;
use crate::gui::tooltip::label;
use crate::gui::widgets::{self, Button, HeaderFooter};
use crate::gui::{GuiState, Screen, ScreenCtx};
use crate::text::{LINE_HEIGHT, Span};

const LAYOUT: HeaderFooter = HeaderFooter::new(HeaderFooter::DEFAULT_H, 42.0);

const WRAP_W: f32 = 300.0;

// Treasure Client: the address we last tried to join, so a kick from a java:// server that
// looks like an anti-VPN block can explain why (the server sees the Wisp relay, not you).
static LAST_ADDRESS: std::sync::Mutex<String> = std::sync::Mutex::new(String::new());

pub(crate) fn note_address(address: &str) {
    if let Ok(mut last) = LAST_ADDRESS.lock() {
        *last = address.to_string();
    }
}

fn last_was_java() -> bool {
    LAST_ADDRESS.lock().is_ok_and(|last| {
        let a = last.trim().to_ascii_lowercase();
        let a = a.strip_prefix("wss://").or_else(|| a.strip_prefix("ws://")).unwrap_or(&a);
        a.starts_with("java://")
    })
}

fn is_vpn_kick(reason: &[Span]) -> bool {
    if !last_was_java() {
        return false;
    }
    let t: String = reason.iter().map(|s| s.text.as_str()).collect::<String>().to_lowercase();
    ["vpn", "proxy detected", "using a proxy", "proxies are not", "anonymous network",
        "hosting provider", "datacenter", "data center"]
        .iter()
        .any(|k| t.contains(k))
}

const VPN_HELP: [&str; 3] = [
    "\u{a7}eWhy this happens: \u{a7}7Java servers see the address of the relay that connects your browser to them, not your own. Lots of players share that relay, so some servers' anti-VPN checks block it. You are not on a VPN.",
    "",
    "\u{a7}eWhat you can do: \u{a7}7on the server list, change the Java proxy (top left) to a different one or your own, or play the server's Eaglercraft address if it has one.",
];

pub fn draw(p: &mut Painter, state: &mut GuiState, ctx: &ScreenCtx) -> Option<Screen> {
    crate::gui::menu::background(p, ctx.vw, ctx.vh);
    p.text_centered(
        "Disconnected",
        ctx.vw / 2.0,
        LAYOUT.title_y(),
        0xFFFFFF,
        true,
    );

    let width = WRAP_W.min(ctx.vw - 40.0).max(80.0);
    let mut lines: Vec<Vec<Span>> = match &state.disconnect_reason {
        Some(reason) if !reason.is_empty() => p.atlas.font.wrap(reason, width),
        _ => p
            .atlas
            .font
            .wrap(&crate::text::parse_formatted("Connection lost"), width),
    };
    let vpn = state.disconnect_reason.as_deref().is_some_and(is_vpn_kick);
    if vpn {
        lines.push(Vec::new());
        for help in VPN_HELP {
            if help.is_empty() {
                lines.push(Vec::new());
            } else {
                lines.extend(p.atlas.font.wrap(&crate::text::parse_formatted(help), width));
            }
        }
    }

    let block_h = lines.len() as f32 * LINE_HEIGHT;
    let (_, content_y, _, content_h) = LAYOUT.content_rect(ctx.vw, ctx.vh);
    let mut y = (content_y + (content_h - block_h) * 0.5)
        .floor()
        .max(content_y);
    for line in &lines {
        let w = p.atlas.font.width(line);
        p.text(line, ((ctx.vw - w) * 0.5).floor(), y, true);
        y += LINE_HEIGHT;
    }

    if vpn {
        let y = LAYOUT.footer_y(ctx.vh);
        let half = (widgets::WIDGET_WIDTH_BIG - 4.0) / 2.0;
        let left = (ctx.vw / 2.0 - half - 2.0).floor();
        if Button::new(left, y, half, widgets::WIDGET_HEIGHT, "Server list").draw(p, ctx) {
            return Some(Screen::Multiplayer);
        }
        return Button::new(left + half + 4.0, y, half, widgets::WIDGET_HEIGHT, label!("gui.toTitle"))
            .draw(p, ctx)
            .then_some(Screen::Title);
    }
    let x = widgets::centered_x(ctx.vw);
    Button::new(
        x,
        LAYOUT.footer_y(ctx.vh),
        widgets::WIDGET_WIDTH_BIG,
        widgets::WIDGET_HEIGHT,
        label!("gui.toTitle"),
    )
    .draw(p, ctx)
    .then_some(Screen::Title)
}
