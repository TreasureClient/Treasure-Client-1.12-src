//! Treasure Client: the first-person animation mods, the same as the JS and WASM editions.
//! Visual only: nothing extra is sent to the server.
//!
//! Treasure Animations (`tAnim`):
//! - Swing styles: Treasure (a heavy chop with a follow-through twist), Slash (a wide
//!   diagonal cut), Spin (the item twirls a full turn) and Smooth (vanilla, eased).
//! - Swing speed: snappier swings.
//! - Hit punch: the item kicks back when a hit lands.
//! - Draw twirl: a new item spins and pops into your hand when you switch to it.
//! - Sway: the item leans when you turn.
//! - Eating and drinking: bites, squash and wobble instead of the up-down bob.
//! - Throwing: snowballs, pearls, potions and charges are wound up and thrown.
//!
//! 1.7 Animations (`oldAnim`):
//! - Small dip: after a hit the held item lowers only a little, and using an item doesn't
//!   replay the equip animation.
//! - Swing while using: the hand can punch while eating, drinking, drawing a bow or
//!   holding up a shield.

use std::f32::consts::PI;
use std::sync::Mutex;

use super::hand::Chain;
use crate::platform::time::Instant;

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Style {
    Treasure,
    Slash,
    Spin,
    Smooth,
}

#[derive(Default)]
struct State {
    punch_at: Option<Instant>,
    draw_at: Option<Instant>,
    throw_at: Option<Instant>,
    throw_spin: bool,
}

static STATE: Mutex<Option<State>> = Mutex::new(None);

fn with<R>(f: impl FnOnce(&mut State) -> R) -> R {
    let mut guard = STATE.lock().unwrap();
    f(guard.get_or_insert_with(State::default))
}

const THROW_SECS: f32 = 0.42;

fn since(at: Option<Instant>, secs: f32) -> Option<f32> {
    let t = at?.elapsed().as_secs_f32() / secs;
    (t < 1.0).then_some(t)
}

/// Treasure Animations are on.
pub fn on() -> bool {
    crate::tc_mods::get().t_anim
}

/// A hit landed: kick the item back.
pub fn on_hit() {
    let m = crate::tc_mods::get();
    if m.t_anim && m.ta_punch {
        with(|s| s.punch_at = Some(Instant::now()));
    }
}

/// A different item came into the main hand: twirl it in.
pub fn on_draw() {
    let m = crate::tc_mods::get();
    if m.t_anim && m.ta_draw {
        with(|s| s.draw_at = Some(Instant::now()));
    }
}

/// Snowballs, eggs, ender pearls, eyes of ender, splash and lingering potions, XP bottles,
/// and fire and wind charges.
pub fn is_throwable(id: &str) -> bool {
    matches!(
        id,
        "snowball"
            | "egg"
            | "ender_pearl"
            | "ender_eye"
            | "splash_potion"
            | "lingering_potion"
            | "experience_bottle"
            | "fire_charge"
            | "wind_charge"
    )
}

/// Used an item from the main hand: play the throw if it is a throwable.
pub fn on_use(id: &str) {
    let m = crate::tc_mods::get();
    if m.t_anim && m.ta_throw && is_throwable(id) {
        let spin = matches!(id, "fire_charge" | "wind_charge"); // charges are hurled with a spin
        with(|s| {
            s.throw_at = Some(Instant::now());
            s.throw_spin = spin;
        });
    }
}

/// How far the item dips after a hit (1.7 small dip keeps a quarter of it).
pub fn dip_target(target: f32) -> f32 {
    let m = crate::tc_mods::get();
    if m.old_anim && m.oa_no_dip { 1.0 - (1.0 - target) * 0.25 } else { target }
}

/// Using an item replays the equip animation, except with the 1.7 small dip.
pub fn use_replays_equip() -> bool {
    let m = crate::tc_mods::get();
    !(m.old_anim && m.oa_no_dip)
}

/// 1.7: the hand punches while you use an item.
pub fn use_swing() -> bool {
    let m = crate::tc_mods::get();
    m.old_anim && m.oa_use_swing
}

/// The swing progress for our own swing at the menu's speed (100 = normal, higher = faster).
pub fn swing_progress(attack: f32) -> f32 {
    let m = crate::tc_mods::get();
    if !m.t_anim || m.ta_speed <= 100.0 || attack <= 0.0 {
        return attack;
    }
    (attack * m.ta_speed / 100.0).min(1.0)
}

/// The main-hand pose for a normal swing (instead of the vanilla swing; after the hand is
/// placed). `side`: 1 = right hand, -1 = left. `lean` is the view lag the hand already has.
pub fn first_person(c: &mut Chain, side: f32, swing: f32, lean: f32) {
    let m = crate::tc_mods::get();
    if m.ta_sway {
        c.rot_z(-(lean * 3.5).clamp(-14.0, 14.0));
    }
    let (draw, throw, spin) = with(|s| {
        let draw = since(s.draw_at, 0.32);
        if draw.is_none() {
            s.draw_at = None;
        }
        let throw = since(s.throw_at, THROW_SECS);
        if throw.is_none() {
            s.throw_at = None;
        }
        (draw, throw, s.throw_spin)
    });
    if let Some(t) = draw {
        let inv = 1.0 - t;
        c.rot_y(side * 360.0 * inv * inv * inv);
        let pop = 1.0 + 0.18 * (t * PI).sin();
        c.scale(pop, pop, pop);
    }
    if let Some(t) = throw {
        throw_anim(c, side, t, spin);
    } else {
        match m.ta_style {
            Style::Slash => slash(c, side, swing),
            Style::Spin => spin_style(c, side, swing),
            Style::Smooth => smooth(c, side, swing),
            Style::Treasure => treasure(c, side, swing),
        }
    }
    punch(c);
}

fn punch(c: &mut Chain) {
    let t = with(|s| {
        let t = since(s.punch_at, 0.18);
        if t.is_none() {
            s.punch_at = None;
        }
        t
    });
    let Some(t) = t else { return };
    let p = (t * PI).sin() * (1.0 - t);
    c.translate(0.0, 0.03 * p, 0.10 * p);
    c.rot_x(14.0 * p);
}

/// Treasure: the item lifts a little, chops down hard, then twists through and settles.
fn treasure(c: &mut Chain, side: f32, s: f32) {
    let a = (s.sqrt() * PI).sin();
    let b = (s * PI).sin();
    let late = (s * s * PI).sin();
    let pop = (s.sqrt() * PI * 2.0).sin();
    c.translate(side * -0.32 * a, 0.10 * pop, -0.16 * b);
    c.rot_y(side * 45.0);
    c.rot_z(side * -28.0 * a);
    c.rot_x(-100.0 * a);
    c.rot_y(side * 24.0 * late);
    c.rot_y(side * -45.0);
}

/// Slash: a wide cut from the top right across to the bottom left.
fn slash(c: &mut Chain, side: f32, s: f32) {
    let a = (s.sqrt() * PI).sin();
    let d = (s * PI).sin();
    c.translate(side * -0.55 * a, 0.12 * a - 0.18 * d * d, -0.10 * a);
    c.rot_y(side * 45.0);
    c.rot_z(side * 70.0 * a);
    c.rot_x(-45.0 * a);
    c.rot_y(side * -45.0);
}

/// Spin: the item flips a full turn over the hand, like twirling a baton.
fn spin_style(c: &mut Chain, side: f32, s: f32) {
    let a = (s.sqrt() * PI).sin();
    let inv = 1.0 - s;
    let turn = 360.0 * (1.0 - inv * inv * inv);
    c.translate(side * -0.16 * a, 0.12 * a, -0.20 * a);
    c.rot_y(side * 45.0);
    c.rot_x(-25.0 * a);
    c.translate(0.0, 0.1, 0.0);
    c.rot_x(-turn);
    c.translate(0.0, -0.1, 0.0);
    c.rot_y(side * -45.0);
}

/// Smooth: the vanilla swing on an eased curve, so it starts and stops softly.
fn smooth(c: &mut Chain, side: f32, s: f32) {
    let e = s * s * (3.0 - 2.0 * s);
    let f = -0.4 * (e.sqrt() * PI).sin();
    let f1 = 0.2 * (e.sqrt() * PI * 2.0).sin();
    let f2 = -0.2 * (e * PI).sin();
    c.translate(side * f, f1, f2);
    let g = (e * e * PI).sin();
    c.rot_y(side * (45.0 + g * -20.0));
    let g1 = (e.sqrt() * PI).sin();
    c.rot_z(side * g1 * -20.0);
    c.rot_x(g1 * -80.0);
    c.rot_y(side * -45.0);
}

/// Throw: pull back and up, snap forward (the item shrinks as it leaves your hand), dip out
/// of view, then the next one rises back into place.
fn throw_anim(c: &mut Chain, side: f32, t: f32, spin: bool) {
    let (back, fwd, drop) = if t < 0.28 {
        let k = t / 0.28;
        (k * k * (3.0 - 2.0 * k), 0.0, 0.0)
    } else if t < 0.5 {
        let k = (t - 0.28) / 0.22;
        (1.0 - k, (k * PI * 0.5).sin(), 0.0)
    } else {
        let k = (t - 0.5) / 0.5;
        (0.0, 1.0 - k, (k * PI).sin())
    };
    c.translate(
        side * (0.06 * back - 0.10 * fwd),
        0.14 * back + 0.06 * fwd - 0.45 * drop,
        0.16 * back - 0.40 * fwd,
    );
    c.rot_x(38.0 * back - 75.0 * fwd);
    c.rot_z(side * (-12.0 * back + 18.0 * fwd));
    if spin && (0.28..0.6).contains(&t) {
        let k = (t - 0.28) / 0.32;
        c.translate(0.0, 0.1, 0.0);
        c.rot_z(side * -360.0 * k);
        c.translate(0.0, -0.1, 0.0);
    }
    let sc = if t < 0.28 {
        1.0
    } else if t < 0.5 {
        1.0 - 0.55 * ((t - 0.28) / 0.22)
    } else {
        0.45 + 0.55 * ((t - 0.5) / 0.5)
    };
    if sc < 1.0 {
        c.scale(sc, sc, sc);
    }
}

/// Treasure eating and drinking are on.
pub fn eat_on() -> bool {
    let m = crate::tc_mods::get();
    m.t_anim && m.ta_eat
}

/// 0..1 bite pulse, one quick chomp every 4 ticks.
fn bite(used: f32) -> f32 {
    let c = used / 4.0;
    let frac = c - c.floor();
    if frac < 0.4 { (frac / 0.4 * PI).sin() } else { 0.0 }
}

/// Before the hand is placed: brings the food to your mouth with a quick bite every few
/// ticks instead of the up-down bob. `left` = ticks of use left, `max` = the item's use time.
pub fn eat_pre(c: &mut Chain, left: f32, max: f32, side: f32, drink: bool) {
    let used = max - left;
    let f1 = left / max;
    let f3 = 1.0 - f1.powi(27);
    if drink {
        c.translate(0.0, (used * 0.5).sin() * 0.025 * f3, 0.0);
    } else {
        let b = bite(used);
        c.translate(0.0, 0.06 * b * f3, 0.09 * b * f3);
    }
    c.translate(f3 * 0.6 * side, f3 * -0.5, 0.0);
    c.rot_y(side * f3 * 90.0);
    c.rot_x(f3 * 10.0);
    c.rot_z(side * f3 * 30.0);
}

/// After the hand is placed: squash on each bite, a little wobble, and the food shrinks as
/// it's finished.
pub fn eat_post(c: &mut Chain, left: f32, max: f32, side: f32, drink: bool) {
    let used = max - left;
    let f1 = left / max;
    let fade_in = (used / 4.0).clamp(0.0, 1.0);
    if drink {
        c.rot_x(-(10.0 + 25.0 * (1.0 - f1)) * fade_in);
        c.rot_z(side * (used * 0.35).sin() * 4.0 * fade_in);
        return;
    }
    let b = bite(used);
    c.rot_z(side * (used * 0.8).sin() * 7.0 * fade_in);
    let (sx, sy) = (1.0 + 0.12 * b, 1.0 - 0.16 * b);
    let nearly = if f1 < 0.2 { 0.55 + 0.45 * (f1 / 0.2) } else { 1.0 };
    c.scale(sx * nearly, sy * nearly, sx * nearly);
}
