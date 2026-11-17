//! Treasure Client: Video Settings in the Treasure Client layout (the same as the JS and
//! WASM editions): page tabs at the top, one row per setting (name on the left, the value
//! or a slider on the right), a tip box for the setting under the mouse, Reset and Done at
//! the bottom. Click a row to change it; drag a slider.

use super::*;
use crate::gui::GuiOptions;
use std::sync::Mutex;

const ROW_H: f32 = 18.0;
const GROUP_GAP: f32 = 6.0;
const TAB_H: f32 = 18.0;
const SLIDER_W: f32 = 96.0;

enum Kind {
    Slider {
        min: i32,
        max: fn(&ScreenCtx) -> i32,
        get: fn(&GuiOptions) -> i32,
        set: fn(&mut GuiOptions, i32),
        show: fn(i32) -> String,
    },
    Toggle {
        get: fn(&GuiOptions) -> bool,
        set: fn(&mut GuiOptions, bool),
    },
    Cycle {
        show: fn(&GuiOptions) -> String,
        next: fn(&mut GuiOptions),
    },
}

struct Opt {
    name: &'static str,
    tip: &'static str,
    impact: Option<&'static str>,
    kind: Kind,
}

struct Page {
    name: &'static str,
    groups: Vec<Vec<Opt>>,
}

#[derive(Default)]
struct Ui {
    page: usize,
    scroll: f32,
    dragging: Option<(usize, usize, usize)>,
}

static UI: Mutex<Option<Ui>> = Mutex::new(None);

/// The value part of a "Caption: value" label.
fn value_of(label: String) -> String {
    match label.split_once(": ") {
        Some((_, v)) => v.to_owned(),
        None => label,
    }
}

fn fixed<const N: i32>(_: &ScreenCtx) -> i32 {
    N
}

fn gui_scale_max(ctx: &ScreenCtx) -> i32 {
    crate::gui::render::auto_gui_scale(ctx.vw * ctx.input.scale, ctx.vh * ctx.input.scale) as i32
}

const RENDER_DISTANCE_TOP: i32 = RENDER_DISTANCE_MAX;
const GAMMA_TOP: i32 = crate::gui::GAMMA_MAX as i32;
const FPS_TOP: i32 = (crate::gui::MAX_FPS_UNLIMITED / FRAMERATE_STEP) as i32;
#[cfg(feature = "render_scale")]
const SCALE_TOP: i32 = crate::gui::RENDER_SCALE_PERCENTS.len() as i32 - 1;

fn pages() -> Vec<Page> {
    let general = Page {
        name: "General",
        groups: vec![
            vec![
                Opt {
                    name: "Render Distance",
                    tip: "How far away terrain is drawn. Shorter distances draw less terrain, which raises frame rates.",
                    impact: Some("High"),
                    kind: Kind::Slider {
                        min: RENDER_DISTANCE_MIN,
                        max: fixed::<RENDER_DISTANCE_TOP>,
                        get: |o| o.render_distance as i32,
                        set: |o, v| o.render_distance = v.max(0) as u32,
                        show: |v| value_of(render_distance_label(v)),
                    },
                },
                Opt {
                    name: "Brightness",
                    tip: "How bright the game is. Higher values make dark areas easier to see.",
                    impact: None,
                    kind: Kind::Slider {
                        min: 0,
                        max: fixed::<GAMMA_TOP>,
                        get: |o| o.gamma as i32,
                        set: |o, v| o.gamma = v.max(0) as u32,
                        show: |v| value_of(brightness_label(v)),
                    },
                },
            ],
            vec![
                Opt {
                    name: "GUI Scale",
                    tip: "How big menus and the HUD are.",
                    impact: None,
                    kind: Kind::Slider {
                        min: 0,
                        max: gui_scale_max,
                        get: |o| o.gui_scale as i32,
                        set: |o, v| o.gui_scale = v.max(0) as u32,
                        show: |v| value_of(gui_scale_label(v)),
                    },
                },
                Opt {
                    name: "VSync",
                    tip: "Matches the frame rate to your screen's refresh rate (usually 60). Turn it off to go above that.",
                    impact: Some("Varies"),
                    kind: Kind::Toggle {
                        get: |o| o.vsync,
                        set: |o, v| o.vsync = v,
                    },
                },
                Opt {
                    name: "Max Framerate",
                    tip: "The highest frame rate the game aims for. A lower limit keeps your device cooler.",
                    impact: None,
                    kind: Kind::Slider {
                        min: (crate::gui::MAX_FPS_MIN / FRAMERATE_STEP) as i32,
                        max: fixed::<FPS_TOP>,
                        get: |o| (o.max_fps / FRAMERATE_STEP) as i32,
                        set: |o, v| {
                            o.max_fps = (v.max(0) as u32 * FRAMERATE_STEP)
                                .clamp(crate::gui::MAX_FPS_MIN, crate::gui::MAX_FPS_UNLIMITED)
                        },
                        show: |v| value_of(max_framerate_label(v.max(0) as u32 * FRAMERATE_STEP)),
                    },
                },
                Opt {
                    name: "Inactivity FPS Limit",
                    tip: "When the game slows down to save power: when you are away (AFK), or only when the window is minimized.",
                    impact: None,
                    kind: Kind::Cycle {
                        show: |o| match o.inactivity_fps_limit {
                            crate::gui::InactivityFpsLimit::Afk => tr::inactivity_afk().to_owned(),
                            crate::gui::InactivityFpsLimit::Minimized => {
                                tr::inactivity_minimized().to_owned()
                            }
                        },
                        next: |o| {
                            o.inactivity_fps_limit = match o.inactivity_fps_limit {
                                crate::gui::InactivityFpsLimit::Afk => {
                                    crate::gui::InactivityFpsLimit::Minimized
                                }
                                crate::gui::InactivityFpsLimit::Minimized => {
                                    crate::gui::InactivityFpsLimit::Afk
                                }
                            }
                        },
                    },
                },
            ],
            vec![
                Opt {
                    name: "FOV",
                    tip: "How wide your view is.",
                    impact: None,
                    kind: Kind::Slider {
                        min: FOV_MIN,
                        max: fixed::<FOV_MAX>,
                        get: |o| o.fov as i32,
                        set: |o, v| o.fov = v.max(0) as u32,
                        show: |v| value_of(fov_label(v)),
                    },
                },
                Opt {
                    name: "FPS Counter",
                    tip: "Shows the game's own frame rate counter. The Treasure Client FPS mod is in the mod menu.",
                    impact: None,
                    kind: Kind::Toggle {
                        get: |o| o.fps_counter,
                        set: |o, v| o.fps_counter = v,
                    },
                },
            ],
        ],
    };

    let mut quality_tail = vec![
        Opt {
            name: "Antialiasing",
            tip: "Smooths jagged edges.",
            impact: Some("Medium"),
            kind: Kind::Toggle {
                get: |o| o.antialiasing,
                set: |o, v| o.antialiasing = v,
            },
        },
        Opt {
            name: "Lighting Engine",
            tip: "Light from torches and the sky. Off lights everything fully, which is quicker.",
            impact: Some("Medium"),
            kind: Kind::Toggle {
                get: |o| o.lighting_enabled,
                set: |o, v| o.lighting_enabled = v,
            },
        },
    ];
    #[cfg(feature = "render_scale")]
    quality_tail.push(Opt {
        name: "Render Resolution",
        tip: "Draws the world at a lower resolution and stretches it to fit. Lower is faster.",
        impact: Some("High"),
        kind: Kind::Slider {
            min: 0,
            max: fixed::<SCALE_TOP>,
            get: |o| o.render_scale_step as i32,
            set: |o, v| o.render_scale_step = v.max(0) as u32,
            show: |v| value_of(render_scale_label(v)),
        },
    });
    let quality = Page {
        name: "Quality",
        groups: vec![
            vec![
                Opt {
                    name: "Clouds",
                    tip: "How clouds are drawn, or turn them off.",
                    impact: Some("Low"),
                    kind: Kind::Cycle {
                        show: |o| o.clouds.caption().to_owned(),
                        next: |o| o.clouds = o.clouds.next(),
                    },
                },
                Opt {
                    name: "Particles",
                    tip: "How many particles are shown.",
                    impact: Some("Medium"),
                    kind: Kind::Cycle {
                        show: |o| o.particles.caption().to_owned(),
                        next: |o| o.particles = o.particles.next(),
                    },
                },
                Opt {
                    name: "Smooth Lighting",
                    tip: "Soft shadows in corners and under blocks.",
                    impact: Some("Low"),
                    kind: Kind::Toggle {
                        get: |o| o.smooth_lighting,
                        set: |o, v| o.smooth_lighting = v,
                    },
                },
                Opt {
                    name: "Entity Shadows",
                    tip: "Shows round shadows under mobs and items.",
                    impact: Some("Low"),
                    kind: Kind::Toggle {
                        get: |o| o.entity_shadows,
                        set: |o, v| o.entity_shadows = v,
                    },
                },
                Opt {
                    name: "Fancy Leaves",
                    tip: "See-through leaves. Off draws solid leaves, which is quicker.",
                    impact: Some("Medium"),
                    kind: Kind::Toggle {
                        get: |o| o.cutout_leaves,
                        set: |o, v| o.cutout_leaves = v,
                    },
                },
            ],
            quality_tail,
        ],
    };

    let details = Page {
        name: "Details",
        groups: vec![vec![
            Opt {
                name: "FOV Effects",
                tip: "How much your view widens when you sprint or use speed effects.",
                impact: None,
                kind: Kind::Slider {
                    min: 0,
                    max: fixed::<FOV_EFFECTS_MAX>,
                    get: |o| o.fov_effects as i32,
                    set: |o, v| o.fov_effects = v.max(0) as u32,
                    show: |v| value_of(percent_or_off(tr::fov_effects(), v)),
                },
            },
            Opt {
                name: "Distortion Effects",
                tip: "How strong nausea and nether portal wobble are.",
                impact: None,
                kind: Kind::Slider {
                    min: 0,
                    max: fixed::<SCREEN_EFFECTS_MAX>,
                    get: |o| o.screen_effects as i32,
                    set: |o, v| o.screen_effects = v.max(0) as u32,
                    show: |v| value_of(percent_or_off(tr::screen_effects(), v)),
                },
            },
            Opt {
                name: "Damage Tilt",
                tip: "How much your view shakes when you get hurt.",
                impact: None,
                kind: Kind::Slider {
                    min: 0,
                    max: fixed::<DAMAGE_TILT_MAX>,
                    get: |o| o.damage_tilt as i32,
                    set: |o, v| o.damage_tilt = v.max(0) as u32,
                    show: |v| value_of(percent_or_off(tr::damage_tilt(), v)),
                },
            },
            Opt {
                name: "Hide Lightning Flashes",
                tip: "Stops lightning from flashing the sky.",
                impact: None,
                kind: Kind::Toggle {
                    get: |o| o.hide_lightning_flashes,
                    set: |o, v| o.hide_lightning_flashes = v,
                },
            },
        ]],
    };
    vec![general, quality, details]
}

fn content_h(page: &Page) -> f32 {
    page.groups
        .iter()
        .map(|g| g.len() as f32 * ROW_H + GROUP_GAP)
        .sum()
}

pub(super) fn draw(p: &mut Painter, state: &mut GuiState, ctx: &ScreenCtx) {
    let mut guard = UI.lock().unwrap();
    let ui = guard.get_or_insert_with(Ui::default);
    let pages = pages();
    ui.page = ui.page.min(pages.len() - 1);
    crate::gui::menu::background(p, ctx.vw, ctx.vh);
    let acc = crate::gui::tc_style::accent();

    // Page tabs.
    let mut tx = 6.0;
    for (i, page) in pages.iter().enumerate() {
        let w = p.atlas.font.width_str(page.name) + 16.0;
        let selected = i == ui.page;
        let hovered = ctx.hovering(tx, 6.0, w, TAB_H);
        let bg = if selected {
            0xE000_0000
        } else if hovered {
            0xB000_0000
        } else {
            0x9000_0000
        };
        p.fill(tx, 6.0, w, TAB_H, bg);
        if selected {
            p.fill(tx, 6.0 + TAB_H - 1.0, w, 1.0, acc);
        }
        let color = if selected { 0xFFFFFF } else { 0xAAAAAA };
        p.text_plain(page.name, tx + 8.0, 6.0 + ((TAB_H - 8.0) / 2.0).floor(), color, true);
        if !selected && crate::gui::widgets::click_in(ctx, tx, 6.0, w, TAB_H) {
            ui.page = i;
            ui.scroll = 0.0;
            ui.dragging = None;
            #[cfg(feature = "audio")]
            crate::gui::widgets::play_click();
        }
        tx += w + 4.0;
    }

    let list_x = 6.0;
    let list_y = TAB_H + 14.0;
    let mut list_w = (ctx.vw / 2.0).clamp(200.0, 310.0);
    if list_w > ctx.vw - 12.0 {
        list_w = ctx.vw - 12.0;
    }
    let list_bottom = ctx.vh - 32.0;
    let tip_x = list_x + list_w + 8.0;
    let tip_w = (ctx.vw - tip_x - 6.0).min(220.0);
    let slider_x = list_x + list_w - SLIDER_W;

    let page = &pages[ui.page];
    let content = content_h(page);
    let view = list_bottom - list_y;
    let max_scroll = (content - view).max(0.0);
    if ctx.hovering(list_x, list_y, list_w, view) && ctx.input.scroll != 0.0 {
        ui.scroll -= ctx.input.scroll.signum() * ROW_H * 2.0;
    }
    ui.scroll = ui.scroll.clamp(0.0, max_scroll);

    if !ctx.input.left_down && !ctx.input.left_click {
        ui.dragging = None;
    }
    let mouse = ctx.mouse();
    let mut changed = false;
    let mut hovered_opt: Option<&Opt> = None;
    let clip = p.push_clip(list_x, list_y, list_w, view);
    let mut y = list_y - ui.scroll;
    for (gi, group) in page.groups.iter().enumerate() {
        for (oi, o) in group.iter().enumerate() {
            let key = (ui.page, gi, oi);
            let dragging_this = ui.dragging == Some(key);
            let over = mouse.is_some_and(|m| {
                m.x >= list_x
                    && m.x < list_x + list_w
                    && m.y >= y
                    && m.y < y + ROW_H
                    && m.y >= list_y
                    && m.y < list_bottom
            });
            let hot = (over && ui.dragging.is_none()) || dragging_this;
            if hot {
                hovered_opt = Some(o);
            }
            if y + ROW_H > list_y && y < list_bottom {
                p.fill(list_x, y, list_w, ROW_H, if hot { 0xE000_0000 } else { 0x9000_0000 });
                let text_y = y + ((ROW_H - 8.0) / 2.0).floor();
                p.text_plain(o.name, list_x + 6.0, text_y, 0xFFFFFF, true);
                match &o.kind {
                    Kind::Slider {
                        min,
                        max,
                        get,
                        set,
                        show,
                    } => {
                        let max = max(ctx).max(*min + 1);
                        let v = get(&state.options).clamp(*min, max);
                        p.fill(slider_x, y + 4.0, SLIDER_W, ROW_H - 8.0, 0xFF30_3030);
                        let frac = (v - min) as f32 / (max - min) as f32;
                        let thumb = slider_x + 4.0 + (88.0 * frac).floor();
                        let lit = crate::gui::tc_style::lerp(acc, 0xFF00_0000, 0.3) | 0xFF00_0000;
                        p.fill(slider_x + 1.0, y + 5.0, thumb - slider_x - 1.0, ROW_H - 10.0, lit);
                        p.fill(thumb - 2.0, y + 2.0, 4.0, ROW_H - 4.0, 0xFFFF_FFFF);
                        let value = show(v);
                        let vw = p.atlas.font.width_str(&value);
                        p.text_plain(&value, slider_x - 6.0 - vw, text_y, 0xFFFFFF, true);
                        if over && ui.dragging.is_none() && ctx.input.left_click {
                            ui.dragging = Some(key);
                        }
                        if ui.dragging == Some(key)
                            && let Some(m) = mouse
                        {
                            let f = ((m.x - slider_x - 4.0) / 88.0).clamp(0.0, 1.0);
                            let nv = (*min as f32 + f * (max - min) as f32).round() as i32;
                            if nv != v {
                                set(&mut state.options, nv);
                                changed = true;
                            }
                        }
                    }
                    Kind::Toggle { get, set } => {
                        let v = get(&state.options);
                        let (value, color) = if v { ("ON", 0x55FF55) } else { ("OFF", 0xFF5555) };
                        let vw = p.atlas.font.width_str(value);
                        p.text_plain(value, list_x + list_w - 6.0 - vw, text_y, color, true);
                        if over && (ctx.input.left_click || ctx.input.right_click) {
                            set(&mut state.options, !v);
                            changed = true;
                            #[cfg(feature = "audio")]
                            crate::gui::widgets::play_click();
                        }
                    }
                    Kind::Cycle { show, next } => {
                        let value = show(&state.options);
                        let vw = p.atlas.font.width_str(&value);
                        p.text_plain(&value, list_x + list_w - 6.0 - vw, text_y, 0xFFFFFF, true);
                        if over && ctx.input.left_click {
                            next(&mut state.options);
                            changed = true;
                            #[cfg(feature = "audio")]
                            crate::gui::widgets::play_click();
                        }
                    }
                }
            }
            y += ROW_H;
        }
        y += GROUP_GAP;
    }
    p.pop_clip(clip);
    if max_scroll > 0.0 {
        let bar = (view * view / content).max(16.0);
        let bar_y = list_y + (view - bar) * (ui.scroll / max_scroll);
        p.fill(list_x + list_w + 2.0, bar_y, 2.0, bar, 0x80FF_FFFF);
    }
    if changed {
        crate::gui::save_options(&state.options);
    }

    // The tip for the setting under the mouse.
    if let Some(o) = hovered_opt {
        let side = tip_w >= 120.0;
        let w = if side { tip_w - 10.0 } else { ctx.vw - 22.0 };
        let mut lines: Vec<(String, u32)> = vec![(o.name.to_owned(), 0xFFFF55)];
        lines.extend(wrap(p, o.tip, w).into_iter().map(|l| (l, 0xFFFFFF)));
        if let Some(impact) = o.impact {
            lines.push((String::new(), 0));
            lines.push((format!("Performance impact: {impact}"), 0xAAAAAA));
        }
        let h = lines.len() as f32 * 10.0 + 8.0;
        let (x, y) = if side {
            let my = mouse.map_or(list_y, |m| m.y);
            (tip_x, (my - 10.0).clamp(list_y, (ctx.vh - 32.0 - h).max(list_y)))
        } else {
            (6.0, (ctx.vh - 32.0 - h).max(list_y))
        };
        p.fill(x, y, w + 10.0, h, 0xF000_0000);
        p.fill(x, y, w + 10.0, 1.0, acc);
        let mut ly = y + 5.0;
        for (line, color) in &lines {
            p.text_plain(line, x + 5.0, ly, *color, true);
            ly += 10.0;
        }
    }

    // Reset (bottom left) and Done (bottom right).
    if Button::new(6.0, ctx.vh - 26.0, 140.0, WIDGET_HEIGHT, RESET_VIDEO).draw(p, ctx) {
        reset_video_options(&mut state.options);
        crate::gui::save_options(&state.options);
    }
    if Button::new(ctx.vw - 106.0, ctx.vh - 26.0, 100.0, WIDGET_HEIGHT, tr::done()).draw(p, ctx)
        || ctx.input.escape
    {
        ui.dragging = None;
        state.nav = Some(Screen::Options);
    }
}

fn wrap(p: &Painter, text: &str, w: f32) -> Vec<String> {
    let mut out = Vec::new();
    let mut line = String::new();
    for word in text.split(' ') {
        let candidate = if line.is_empty() {
            word.to_owned()
        } else {
            format!("{line} {word}")
        };
        if p.atlas.font.width_str(&candidate) > w && !line.is_empty() {
            out.push(std::mem::take(&mut line));
            line = word.to_owned();
        } else {
            line = candidate;
        }
    }
    if !line.is_empty() {
        out.push(line);
    }
    out
}
