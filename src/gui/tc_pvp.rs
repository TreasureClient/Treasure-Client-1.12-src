//! Treasure Client: Reach Display, Combo Counter and Item Counter, the same rules as the JS and
//! WASM editions (TCPvpMods).
//!
//! Reach and combo only count hits the server confirmed: the target's hurt status arrives
//! within a second of your attack. The combo resets when you get hurt, or after 3 s without a hit.

use std::sync::Mutex;

use crate::platform::time::Instant;

#[derive(Default)]
struct State {
    /// Who you last attacked, when, and how far away the hit landed.
    attacked: Option<(i32, Instant, f64)>,
    /// The last confirmed hit's reach (kept until the next one).
    reach: Option<f64>,
    /// For .lag full: attacks and confirmed hits since the last .lag.
    lag_attacks: u32,
    lag_hits: u32,
    combo: u32,
    combo_at: Option<Instant>,
    /// Arrows, pearls, golden apples, totems, splash potions, blocks.
    items: [u32; 6],
}

static STATE: Mutex<Option<State>> = Mutex::new(None);

fn with<R>(f: impl FnOnce(&mut State) -> R) -> R {
    let mut guard = STATE.lock().unwrap();
    f(guard.get_or_insert_with(State::default))
}

/// You attacked entity `id`; `reach` is from your eyes to where the crosshair touched it.
pub fn on_attack(id: i32, reach: f64) {
    let m = crate::tc_mods::get();
    if !(m.reach_disp || m.combo) {
        return;
    }
    with(|s| {
        s.attacked = Some((id, Instant::now(), reach));
        s.lag_attacks += 1;
    });
}

/// The server says entity `id` was hurt (hurt animation or damage event).
pub fn on_hurt(id: i32) {
    with(|s| {
        if id == crate::client::tracking::local_entity_id() {
            s.combo = 0; // you got hit
            return;
        }
        let Some((who, at, reach)) = s.attacked else { return };
        if who != id || at.elapsed().as_millis() >= 1000 {
            return;
        }
        s.attacked = None; // the next hurt packet for the same hit doesn't count again
        s.lag_hits += 1;
        s.reach = Some(reach);
        let in_a_row = s.combo_at.is_some_and(|t| t.elapsed().as_millis() < 3000);
        s.combo = if in_a_row { s.combo + 1 } else { 1 };
        s.combo_at = Some(Instant::now());
    });
}

/// The item counts, from the inventory.
pub fn set_items(items: [u32; 6]) {
    with(|s| s.items = items);
}

/// ".lag full" line for Reach Display / Combo Counter, or None when both are off; resets the counts.
pub(crate) fn lag_line() -> Option<String> {
    let m = crate::tc_mods::get();
    if !(m.reach_disp || m.combo) {
        return None;
    }
    Some(with(|s| {
        let line = format!(
            "§7Reach/combo: §f{}§7 attacks, §f{}§7 confirmed by the server",
            s.lag_attacks, s.lag_hits
        );
        s.lag_attacks = 0;
        s.lag_hits = 0;
        line
    }))
}

/// Left the world: forget the fight.
pub fn reset() {
    with(|s| *s = State::default());
}

/// Lines for the overlay's HUD data (`tchud`).
pub fn hud(out: &mut String) {
    let m = crate::tc_mods::get();
    with(|s| {
        if s.combo > 0 && s.combo_at.is_none_or(|t| t.elapsed().as_millis() >= 3000) {
            s.combo = 0;
        }
        if m.reach_disp {
            match s.reach {
                Some(r) => out.push_str(&format!("reach={r:.2}\n")),
                None => out.push_str("reach=-\n"), // before your first hit
            }
        }
        if m.combo {
            out.push_str(&format!("combo={}\n", s.combo));
        }
        if m.item_count {
            const NAMES: [&str; 6] = ["arrow", "pearl", "gapple", "totem", "potion", "blocks"];
            out.push_str("items=");
            for (i, name) in NAMES.iter().enumerate() {
                if m.ic[i] {
                    out.push_str(&format!("{name}:{}~", s.items[i]));
                }
            }
            out.push('\n');
        }
    });
}

/// Which Item Counter group an item is in (0-5), or None.
pub fn item_group(kind: azalea_registry::builtin::ItemKind) -> Option<usize> {
    use azalea_registry::builtin::ItemKind as K;
    Some(match kind {
        K::Arrow | K::TippedArrow | K::SpectralArrow => 0,
        K::EnderPearl => 1,
        K::GoldenApple | K::EnchantedGoldenApple => 2,
        K::TotemOfUndying => 3,
        K::SplashPotion | K::LingeringPotion => 4,
        _ => return is_block(kind).then_some(5),
    })
}

fn is_block(kind: azalea_registry::builtin::ItemKind) -> bool {
    use std::collections::HashMap;
    use std::str::FromStr as _;

    use azalea_registry::builtin::BlockKind;
    thread_local! {
        static KNOWN: std::cell::RefCell<HashMap<azalea_registry::builtin::ItemKind, bool>> = Default::default();
    }
    KNOWN.with(|k| {
        *k.borrow_mut().entry(kind).or_insert_with(|| {
            BlockKind::from_str(kind.to_str()).is_ok_and(|b| b != BlockKind::Air)
        })
    })
}
