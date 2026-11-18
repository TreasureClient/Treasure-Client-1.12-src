//! Treasure Client: Crosshair Addons, small pixel icons around the crosshair (the same as
//! the JS and WASM editions).
//!
//! - Target: corner brackets while a player (or, if set, a hostile mob) is under the crosshair.
//! - Elytra: a small elytra while you wear one.
//! - Rocket: while a rocket boosts you, a rocket that empties as the boost runs out.
//! - Hit marker: an X when your arrow hits a player (and, if set, when you hit one).
//! - Shield break: a shield that cracks apart when you knock someone's shield down.
//! - Shield up: a shield while the player you aim at is blocking.
//!
//! The attack cooldown indicator moves down below the icons.
//!
//! Port of Crosshair Addons by Walksy (https://github.com/Walksy/Crosshair-Addons), icon
//! shapes from it. MIT License, Copyright (c) 2026 Walksy: Permission is hereby granted, free
//! of charge, to any person obtaining a copy of this software and associated documentation
//! files (the "Software"), to deal in the Software without restriction, including without
//! limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
//! sell copies of the Software, and to permit persons to whom the Software is furnished to do
//! so, subject to the following conditions: The above copyright notice and this permission
//! notice shall be included in all copies or substantial portions of the Software. THE
//! SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.

use std::sync::Mutex;

use crate::gui::painter::Painter;
use crate::platform::time::Instant;

#[derive(Clone, Copy, Default)]
struct Settings {
    on: bool,
    target: bool,
    mobs: bool,
    elytra: bool,
    rocket: bool,
    hit: bool,
    hit_melee: bool,
    shield_break: bool,
    shield_up: bool,
}

#[derive(Default)]
struct State {
    hit_at: Option<Instant>,
    break_at: Option<Instant>,
    last_attacked: Option<(i32, Instant)>,
    boost_since: Option<Instant>,
    // what is under the crosshair, from the last HUD snapshot
    target: bool,
    target_blocking: bool,
    wearing_elytra: bool,
    fall_flying: bool,
}

static STATE: Mutex<Option<State>> = Mutex::new(None);

fn with<R>(f: impl FnOnce(&mut State) -> R) -> R {
    let mut guard = STATE.lock().unwrap();
    f(guard.get_or_insert_with(State::default))
}

/// Read from the Mod Menu's `tcmods` (chAddons, chTarget, ...).
fn settings() -> Settings {
    let m = crate::tc_mods::get();
    Settings {
        on: m.ch_addons,
        target: m.ch_target,
        mobs: m.ch_mobs,
        elytra: m.ch_elytra,
        rocket: m.ch_rocket,
        hit: m.ch_hit,
        hit_melee: m.ch_hit_melee,
        shield_break: m.ch_shield_break,
        shield_up: m.ch_shield_up,
    }
}

/// Your arrow hit a player (the "ding" the server sends).
pub fn on_arrow_hit() {
    with(|s| s.hit_at = Some(Instant::now()));
}

/// You attacked something.
pub fn on_attack(id: i32, is_player: bool) {
    let melee = settings().hit_melee;
    with(|s| {
        let now = Instant::now();
        s.last_attacked = Some((id, now));
        if melee && is_player {
            s.hit_at = Some(now);
        }
    });
}

/// Someone's shield was knocked down (entity event 30); counts if it was the one you just hit.
pub fn on_shield_disabled(id: i32) {
    with(|s| {
        if s.last_attacked.is_some_and(|(who, at)| who == id && at.elapsed().as_millis() < 1000) {
            s.break_at = Some(Instant::now());
        }
    });
}

/// Whether a firework rocket is boosting you right now (every game tick).
pub fn rocket_boost(boosting: bool) {
    with(|s| match (boosting, s.boost_since) {
        (true, None) => s.boost_since = Some(Instant::now()),
        (false, Some(_)) => s.boost_since = None,
        _ => {}
    });
}

/// What is under the crosshair (from the HUD snapshot, every frame).
pub fn observe(session: &crate::session::SessionState) {
    let st = settings();
    if !st.on {
        return;
    }
    let pointed = session.crosshair_entity;
    let player = pointed.and_then(|id| {
        session
            .other_players
            .iter()
            .find(|p| p.id == id && p.health.is_none_or(|h| h > 0.0))
    });
    let mob = st.mobs
        && pointed.is_some_and(|id| {
            session.entities.iter().any(|e| {
                e.id == id && crate::tc_mods::group(e.kind) == crate::tc_mods::Group::Mob
            })
        });
    let blocking = player.is_some_and(|p| {
        let [main, off] = p.anim.held();
        p.anim.using_item() && (if p.anim.using_offhand() { off } else { main }).id == "shield"
    });
    let elytra = session.armor_stacks.get(1).is_some_and(|s| s.item == "elytra" && s.count > 0);
    with(|s| {
        s.target = player.is_some() || mob;
        s.target_blocking = blocking;
        s.wearing_elytra = elytra;
        s.fall_flying = session.fall_flying;
    });
}

// icon shapes: x,y pairs on the 15x15 crosshair grid
const TARGET: &[u8] = &[4, 4, 5, 4, 9, 4, 10, 4, 4, 5, 10, 5, 4, 10, 5, 10, 9, 10, 10, 10, 4, 9, 10, 9];
const ELYTRA: &[u8] = &[
    1, 4, 1, 5, 1, 6, 1, 7, 2, 3, 2, 8, 2, 9, 3, 3, 3, 10, 3, 11, 4, 2, 4, 11, 5, 2, 5, 10, 6, 2, 6,
    3, 6, 4, 6, 5, 6, 6, 6, 7, 6, 8, 6, 9, 7, 2, 7, 3, 8, 2, 8, 3, 8, 4, 8, 5, 8, 6, 8, 7, 8, 8, 8,
    9, 9, 2, 9, 10, 10, 2, 10, 11, 11, 3, 11, 10, 11, 11, 12, 3, 12, 8, 12, 9, 13, 4, 13, 5, 13, 6,
    13, 7,
];
const ROCKET: &[u8] = &[
    12, 5, 2, 6, 3, 6, 4, 6, 5, 6, 6, 6, 7, 6, 8, 6, 9, 6, 10, 6, 11, 6, 12, 6, 13, 6, 1, 7, 2, 7,
    3, 7, 4, 7, 5, 7, 6, 7, 7, 7, 8, 7, 9, 7, 10, 7, 11, 7, 12, 7, 13, 7, 1, 8, 12, 8, 2, 9, 2, 10,
];
const HIT: &[u8] = &[3, 3, 11, 3, 4, 4, 10, 4, 5, 5, 9, 5, 5, 9, 9, 9, 4, 10, 10, 10, 3, 11, 11, 11];
const SHIELD: &[u8] = &[
    4, 5, 5, 5, 6, 5, 7, 5, 8, 5, 9, 5, 10, 5, 4, 6, 10, 6, 4, 7, 10, 7, 4, 8, 10, 8, 4, 9, 10, 9, 4,
    10, 10, 10, 5, 11, 9, 11, 6, 12, 7, 12, 8, 12,
];
const SHIELD_BREAK: [&[u8]; 5] = [
    SHIELD,
    &[
        3, 5, 4, 5, 5, 5, 9, 5, 10, 5, 11, 5, 3, 6, 11, 6, 3, 7, 11, 7, 3, 8, 11, 8, 3, 9, 11, 9, 3,
        10, 11, 10, 4, 11, 10, 11, 5, 12, 9, 12, 6, 12, 8, 12,
    ],
    &[
        2, 5, 4, 5, 10, 5, 11, 5, 2, 6, 12, 6, 2, 7, 12, 7, 12, 8, 2, 9, 12, 9, 2, 10, 12, 10, 3, 11,
        10, 12,
    ],
    &[1, 5, 3, 5, 11, 5, 1, 7, 13, 7, 1, 9, 13, 9, 13, 10, 2, 11, 10, 12],
    &[2, 5, 0, 7, 14, 7, 14, 9, 1, 11, 12, 12],
];

const OFF_ELYTRA: f32 = 11.0;
const OFF_ROCKET: f32 = 19.0;
const OFF_SHIELD: f32 = 8.5;
const HIT_MS: u128 = 400;
const BREAK_FRAME_MS: u128 = 70;
/// A rocket's boost lasts about this long (its lifetime is rolled on the server).
const BOOST_SECS: f32 = 1.275;

fn put(p: &mut Painter, px: &[u8], x: f32, y: f32, cut: Option<f32>) {
    let limit = cut.map(|prog| {
        let min = px.iter().step_by(2).min().copied().unwrap_or(0);
        let max = px.iter().step_by(2).max().copied().unwrap_or(0);
        let span = f32::from(max - min + 1);
        f32::from(min) + (prog * span).ceil().max(1.0)
    });
    for pair in px.chunks_exact(2) {
        if limit.is_some_and(|l| f32::from(pair[0]) >= l) {
            continue;
        }
        p.fill(x + f32::from(pair[0]), y + f32::from(pair[1]), 1.0, 1.0, 0xFFFF_FFFF);
    }
}

/// Draws the icons around the crosshair (its top-left corner at `left`, `top`). Returns how
/// many pixels below its usual spot the attack indicator should go.
pub fn draw(p: &mut Painter, left: f32, top: f32) -> f32 {
    let st = settings();
    if !st.on {
        return 0.0;
    }
    with(|s| {
        let mut bottom: f32 = -1.0;
        if st.target && s.target {
            put(p, TARGET, left, top, None);
        }
        if st.elytra && s.wearing_elytra {
            put(p, ELYTRA, left, top + OFF_ELYTRA, None);
            bottom = bottom.max(OFF_ELYTRA + 12.0);
        }
        if st.rocket
            && s.fall_flying
            && let Some(since) = s.boost_since
        {
            let prog = (1.0 - since.elapsed().as_secs_f32() / BOOST_SECS).clamp(0.07, 1.0);
            put(p, ROCKET, left, top + OFF_ROCKET, Some(prog));
            bottom = bottom.max(OFF_ROCKET + 11.0);
        }
        if st.hit && s.hit_at.is_some_and(|t| t.elapsed().as_millis() < HIT_MS) {
            put(p, HIT, left, top, None);
        }
        let frame = s
            .break_at
            .map(|t| (t.elapsed().as_millis() / BREAK_FRAME_MS) as usize)
            .filter(|f| st.shield_break && *f < SHIELD_BREAK.len());
        if let Some(frame) = frame {
            put(p, SHIELD_BREAK[frame], left, top + OFF_SHIELD, None);
            bottom = bottom.max(OFF_SHIELD + 13.0);
        } else if st.shield_up && s.target_blocking {
            put(p, SHIELD, left, top + OFF_SHIELD, None);
            bottom = bottom.max(OFF_SHIELD + 13.0);
        }
        // the attack indicator sits 16 below the crosshair top; push it under the icons
        if bottom < 0.0 { 0.0 } else { ((bottom - 2.0).ceil() - 16.0).max(0.0) }
    })
}
