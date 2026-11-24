//! Treasure Client: the "Mods" screen (the main menu's Mods word and About both open it), laid
//! out like Fabric's Mod Menu, the same idea as the browser editions' Mods screen: the Rust
//! edition's parts and the mods ported into it on the left, the picked one's details (who made
//! it, its license, what it does, links) on the right.

use std::sync::Mutex;

use crate::gui::painter::Painter;
use crate::gui::tc_style;
use crate::gui::widgets::{Button, HeaderFooter, WIDGET_HEIGHT, WIDGET_WIDTH_BIG};
use crate::gui::{Screen, ScreenCtx};
use crate::text::LINE_HEIGHT;

const LAYOUT: HeaderFooter = HeaderFooter::new(HeaderFooter::DEFAULT_H, 42.0);

const TORCH: &[(&str, &str)] = &[
    ("GitHub", "https://github.com/torch-client/torch-client"),
    ("Website", "https://torchclient.com"),
    ("Discord", "https://discord.gg/qqXSHEYXaq"),
];

struct ModInfo {
    name: &'static str,
    by: &'static str,
    license: &'static str,
    about: &'static str,
    links: &'static [(&'static str, &'static str)],
}

const MODS: &[ModInfo] = &[
    ModInfo {
        name: "Treasure Client Rust (Beta)",
        by: "the Treasure Client team",
        license: "AGPL-3.0",
        about: "This edition of Treasure Client, built on Torch Client. It plays 1.12 servers through its own 1.12 translator. Not affiliated with Mojang or Microsoft.",
        links: &[],
    },
    ModInfo {
        name: "Torch Client",
        by: "the Torch Client contributors",
        license: "AGPL-3.0",
        about: "The Minecraft client written in Rust that this edition is built on. Free software under the GNU Affero General Public License v3.0.",
        links: TORCH,
    },
    ModInfo {
        name: "Dynamic FPS",
        by: "Treasure Client, inspired by juliand665",
        license: "Built in",
        about: "Draws far fewer frames when the tab isn't focused or you've been idle for a minute, so a weak computer stays cool.",
        links: &[],
    },
    ModInfo {
        name: "FPS Reducer",
        by: "Treasure Client, inspired by bre2el",
        license: "Built in",
        about: "Menus like the main menu, the server list and the pause menu run at 30 frames a second, which saves power without you noticing.",
        links: &[],
    },
    ModInfo {
        name: "Entity Culling",
        by: "Treasure Client, inspired by tr7zw",
        license: "Built in",
        about: "Mobs and players hidden behind walls aren't drawn. Anything within 8 blocks and glowing players are always drawn.",
        links: &[],
    },
    ModInfo {
        name: "Block Entity Culling",
        by: "Treasure Client",
        license: "Built in",
        about: "Chests, signs and other block entities hidden behind walls aren't drawn, and banners and heads only within 40 blocks. Beacon beams always show.",
        links: &[],
    },
    ModInfo {
        name: "Leaf Culling",
        by: "Treasure Client, inspired by Karnatour's Celeritas Leaf Culling",
        license: "Built in",
        about: "Leaves inside a tree aren't drawn, only the outside of it, so forests cost much less.",
        links: &[],
    },
    ModInfo {
        name: "Particle Core",
        by: "Treasure Client, inspired by fzzyhmstrs",
        license: "Built in",
        about: "Particles more than 24 blocks away skip their collision checks.",
        links: &[],
    },
    ModInfo {
        name: "ViaVersion support",
        by: "Treasure Client",
        license: "Built in",
        about: "On 1.12 servers that run ViaVersion, modern items and mobs show as the real thing instead of their 1.12 stand-ins.",
        links: &[],
    },
    ModInfo {
        name: "Modern Block Guessing",
        by: "Treasure Client",
        license: "Built in",
        about: "Works out which modern block a 1.12 stand-in really is from the blocks around it, and shows that block.",
        links: &[],
    },
    ModInfo {
        name: "Crosshair Addons",
        by: "Walksy",
        license: "MIT",
        about: "Small icons around the crosshair: a target in reach, elytra, rocket boost, hit marker and shields.",
        links: &[("GitHub", "https://github.com/Walksy/Crosshair-Addons")],
    },
    ModInfo {
        name: "minecraft-data",
        by: "PrismarineJS",
        license: "MIT",
        about: "The table of 1.12 blocks that the 1.12 translator is built from.",
        links: &[("GitHub", "https://github.com/PrismarineJS/minecraft-data")],
    },
];

/// The picked row and how far the list is scrolled.
static STATE: Mutex<(usize, f32)> = Mutex::new((0, 0.0));

const ROW_H: f32 = 24.0;

pub fn draw(p: &mut Painter, ctx: &ScreenCtx, parent: Screen) -> Option<Screen> {
    p.text_centered("Mods", ctx.vw / 2.0, LAYOUT.title_y(), 0xFFFFFF, true);
    let (_, top, _, view_h) = LAYOUT.content_rect(ctx.vw, ctx.vh);
    let (top, view_h) = (top + 4.0, (view_h - 8.0).max(0.0));
    let mut st = STATE.lock().unwrap();
    let accent = tc_style::accent();

    // ---- the list
    let list_x = (ctx.vw * 0.05).floor();
    let list_w = (ctx.vw * 0.3).max(140.0).min(ctx.vw * 0.45).floor();
    let content_h = MODS.len() as f32 * ROW_H;
    if ctx.input.scroll != 0.0 && ctx.hovering(list_x, top, list_w, view_h) {
        st.1 -= ctx.input.scroll * ROW_H;
    }
    st.1 = st.1.clamp(0.0, (content_h - view_h).max(0.0));
    p.fill(list_x, top, list_w, view_h, 0x6600_0000);
    let clip = p.push_clip(list_x, top, list_w, view_h);
    for (i, m) in MODS.iter().enumerate() {
        let y = top + i as f32 * ROW_H - st.1;
        if y + ROW_H < top || y > top + view_h {
            continue;
        }
        // only the visible part of a row answers the mouse
        let vis_top = y.max(top);
        let vis_h = (y + ROW_H).min(top + view_h) - vis_top;
        let hover = vis_h > 0.0 && ctx.hovering(list_x, vis_top, list_w, vis_h);
        if i == st.0 {
            p.fill(list_x, y, list_w, ROW_H, 0x30FF_FFFF);
            p.fill(list_x, y + 3.0, 2.0, ROW_H - 6.0, accent);
        } else if hover {
            p.fill(list_x, y, list_w, ROW_H, 0x18FF_FFFF);
        }
        let name = p.fit(m.name, list_w - 12.0).into_owned();
        p.text_plain(&name, list_x + 6.0, y + 3.0, 0xFFFFFF, true);
        let license = p.fit(m.license, list_w - 12.0).into_owned();
        p.text_plain(&license, list_x + 6.0, y + 13.0, 0x9A9A9A, false);
        if hover && ctx.input.left_click {
            st.0 = i;
        }
    }
    p.pop_clip(clip);

    // ---- the picked mod
    let m = &MODS[st.0.min(MODS.len() - 1)];
    let x = list_x + list_w + 14.0;
    let w = (ctx.vw * 0.95 - x).max(80.0);
    let mut y = top + 2.0;
    p.text_plain(m.name, x, y, accent & 0x00FF_FFFF, true);
    y += LINE_HEIGHT + 2.0;
    for line in wrap(p, &format!("by {}", m.by), w) {
        p.text_plain(&line, x, y, 0xC8C8C8, false);
        y += LINE_HEIGHT;
    }
    p.text_plain(&format!("License: {}", m.license), x, y, 0x9A9A9A, false);
    y += LINE_HEIGHT + 6.0;
    for line in wrap(p, m.about, w) {
        p.text_plain(&line, x, y, 0xFFFFFF, true);
        y += LINE_HEIGHT;
    }
    y += 6.0;
    let mut lx = x;
    for &(label, url) in m.links {
        if Button::new(lx, y, 64.0, WIDGET_HEIGHT, label).draw(p, ctx) {
            crate::platform::url::open(url);
        }
        lx += 68.0;
    }

    let done = Button::new(
        crate::gui::widgets::centered_x(ctx.vw),
        LAYOUT.footer_y(ctx.vh),
        WIDGET_WIDTH_BIG,
        WIDGET_HEIGHT,
        "Done",
    )
    .draw(p, ctx);
    (done || ctx.input.escape).then_some(parent)
}

/// Splits text into lines no wider than `max` (at spaces).
fn wrap(p: &Painter, text: &str, max: f32) -> Vec<String> {
    let mut out = Vec::new();
    let mut line = String::new();
    for word in text.split(' ') {
        let candidate = if line.is_empty() { word.to_string() } else { format!("{line} {word}") };
        if !line.is_empty() && p.atlas.font.width_str(&candidate) > max {
            out.push(std::mem::take(&mut line));
            line = word.to_string();
        } else {
            line = candidate;
        }
    }
    if !line.is_empty() {
        out.push(line);
    }
    out
}
