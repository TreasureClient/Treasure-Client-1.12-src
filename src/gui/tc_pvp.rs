//! Treasure Client: Reach Display, Combo Counter and Item Counter, the same rules as the JS and
//! WASM editions (TCPvpMods).
//!
//! Reach and combo only count hits the server confirmed: the target's hurt animation arrives
//! within 700 ms of your attack. The combo resets when you get hurt, or after 3 s without a hit.

use std::sync::Mutex;

use crate::platform::time::Instant;

#[derive(Default)]
struct State {
    /// Who you last attacked, when, and how far away the hit landed.
    attacked: Option<(i32, Instant, f64)>,
    reach: Option<(f64, Instant)>,
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
    with(|s| s.attacked = Some((id, Instant::now(), reach)));
}

/// The server says entity `id` was hurt (hurt animation or damage event).
pub fn on_hurt(id: i32) {
    with(|s| {
        if id == crate::client::tracking::local_entity_id() {
            s.combo = 0; // you got hit
            return;
        }
        let Some((who, at, reach)) = s.attacked else { return };
        if who != id || at.elapsed().as_millis() >= 700 {
            return;
        }
        s.attacked = None; // the next hurt packet for the same hit doesn't count again
        s.reach = Some((reach, Instant::now()));
        let in_a_row = s.combo_at.is_some_and(|t| t.elapsed().as_millis() < 3000);
        s.combo = if in_a_row { s.combo + 1 } else { 1 };
        s.combo_at = Some(Instant::now());
    });
}

/// The item counts, from the inventory.
pub fn set_items(items: [u32; 6]) {
    with(|s| s.items = items);
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
                Some((r, at)) if at.elapsed().as_millis() <= 5000 => out.push_str(&format!("reach={r:.2}\n")),
                _ => out.push_str("reach=-\n"),
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
