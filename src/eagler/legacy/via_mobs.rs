//! Treasure Client: ViaMobs, the same rules as the JS and WASM editions.
//!
//! ViaBackwards shows a modern mob as an old one with its name on top ("Salmon" on a
//! squid, "Hoglin" on a cow). This engine has every modern mob, so such a mob is spawned
//! as the real one instead, without the name tag. The old mob must be the one
//! ViaBackwards uses, so a pig someone named "Fox" stays a pig.

use std::collections::HashMap;
use std::sync::Mutex;

use azalea_core::position::Vec3;
use azalea_registry::builtin::EntityKind;
use uuid::Uuid;

/// A mob that could be a stand-in: what it was spawned as, and where it is (to swap it
/// later when its name only arrives after spawning).
#[derive(Clone, Copy)]
pub(super) struct Tracked {
    pub(super) kind: EntityKind,
    pub(super) uuid: Uuid,
    pub(super) pos: Vec3,
    pub(super) rot: (i8, i8, i8),
    /// The modern mob it was turned into, if any.
    pub(super) real: Option<EntityKind>,
}

static TRACKED: Mutex<Option<HashMap<i32, Tracked>>> = Mutex::new(None);

fn with<R>(f: impl FnOnce(&mut HashMap<i32, Tracked>) -> R) -> R {
    f(TRACKED.lock().unwrap().get_or_insert_with(HashMap::new))
}

/// Whether ViaBackwards uses this old mob for some modern one.
fn is_stand_in(kind: EntityKind) -> bool {
    use EntityKind as K;
    matches!(
        kind,
        K::Squid
            | K::ZombieVillager
            | K::Parrot
            | K::Ocelot
            | K::Llama
            | K::Wolf
            | K::PolarBear
            | K::Villager
            | K::Cow
            | K::ZombifiedPiglin
            | K::MagmaCube
            | K::Rabbit
            | K::IronGolem
            | K::Vex
            | K::Sheep
            | K::Donkey
            | K::Stray
            | K::Skeleton
    )
}

/// The modern mob behind a stand-in with this name (lowercased, no § codes).
fn real(name: &str, old: EntityKind) -> Option<EntityKind> {
    use EntityKind as K;
    let (want, real) = match name {
        "cod" => (K::Squid, K::Cod),
        "salmon" => (K::Squid, K::Salmon),
        "pufferfish" => (K::Squid, K::Pufferfish),
        "tropical fish" => (K::Squid, K::TropicalFish),
        "dolphin" => (K::Squid, K::Dolphin),
        "glow squid" => (K::Squid, K::GlowSquid),
        "bee" => (K::Squid, K::Bee),
        "axolotl" => (K::Squid, K::Axolotl),
        "tadpole" => (K::Squid, K::Tadpole),
        "nautilus" => (K::Squid, K::Nautilus),
        "drowned" => (K::ZombieVillager, K::Drowned),
        "phantom" => (K::Parrot, K::Phantom),
        "turtle" => (K::Ocelot, K::Turtle),
        "trader llama" => (K::Llama, K::TraderLlama),
        "fox" => (K::Wolf, K::Fox),
        "panda" => (K::PolarBear, K::Panda),
        "pillager" => (K::Villager, K::Pillager),
        "wandering trader" => (K::Villager, K::WanderingTrader),
        "ravager" => (K::Cow, K::Ravager),
        "hoglin" => (K::Cow, K::Hoglin),
        "zoglin" => (K::Cow, K::Zoglin),
        "sniffer" => (K::Cow, K::Sniffer),
        "armadillo" => (K::Cow, K::Armadillo),
        "piglin" => (K::ZombifiedPiglin, K::Piglin),
        "piglin brute" => (K::ZombifiedPiglin, K::PiglinBrute),
        "strider" => (K::MagmaCube, K::Strider),
        "frog" => (K::Rabbit, K::Frog),
        "copper golem" => (K::Rabbit, K::CopperGolem),
        "warden" => (K::IronGolem, K::Warden),
        "creaking" => (K::IronGolem, K::Creaking),
        "allay" => (K::Vex, K::Allay),
        "goat" => (K::Sheep, K::Goat),
        "camel" => (K::Donkey, K::Camel),
        "bogged" => (K::Stray, K::Bogged),
        "parched" => (K::Skeleton, K::Parched),
        _ => return None,
    };
    (old == want).then_some(real)
}

fn clean(name: &str) -> String {
    let mut out = String::with_capacity(name.len());
    let mut chars = name.chars();
    while let Some(c) = chars.next() {
        if c == '\u{00A7}' {
            chars.next();
            continue;
        }
        out.extend(c.to_lowercase());
    }
    out.trim().to_owned()
}

fn match_name(name: Option<&str>, old: EntityKind) -> Option<EntityKind> {
    if !crate::tc_mods::get().via {
        return None;
    }
    let name = clean(name?);
    if name.len() <= 2 || name.len() >= 40 {
        return None;
    }
    real(&name, old)
}

/// A mob spawned. Returns the modern mob to spawn instead (its name tag is then dropped).
pub(super) fn on_spawn(
    raw: i32,
    kind: EntityKind,
    uuid: Uuid,
    pos: Vec3,
    rot: (i8, i8, i8),
    name: Option<&str>,
) -> Option<EntityKind> {
    if !is_stand_in(kind) {
        return None;
    }
    let real = match_name(name, kind);
    with(|t| t.insert(raw, Tracked { kind, uuid, pos, rot, real }));
    real
}

/// What a metadata update means for a tracked mob.
pub(super) enum Update {
    /// Not a stand-in (or nothing changed): pass the metadata on.
    Keep,
    /// Already turned into its modern mob: pass it on without the name.
    HideName,
    /// Its name just told us what it is: remove it and spawn this instead.
    Swap(Tracked),
}

pub(super) fn on_metadata(raw: i32, name: Option<&str>) -> Update {
    with(|t| {
        let Some(tr) = t.get_mut(&raw) else { return Update::Keep };
        if tr.real.is_some() {
            return Update::HideName;
        }
        if name.is_none() {
            return Update::Keep;
        }
        match match_name(name, tr.kind) {
            Some(real) => {
                tr.real = Some(real);
                Update::Swap(*tr)
            }
            None => Update::Keep,
        }
    })
}

/// A tracked mob moved (relative, in 1/4096 blocks).
pub(super) fn moved(raw: i32, delta: (i16, i16, i16)) {
    with(|t| {
        if let Some(tr) = t.get_mut(&raw) {
            tr.pos.x += f64::from(delta.0) / 4096.0;
            tr.pos.y += f64::from(delta.1) / 4096.0;
            tr.pos.z += f64::from(delta.2) / 4096.0;
        }
    });
}

pub(super) fn teleported(raw: i32, pos: Vec3, yaw: i8, pitch: i8) {
    with(|t| {
        if let Some(tr) = t.get_mut(&raw) {
            tr.pos = pos;
            tr.rot = (yaw, pitch, yaw);
        }
    });
}

pub(super) fn removed(raw: i32) {
    with(|t| {
        t.remove(&raw);
    });
}

/// A new world: every entity is gone.
pub(super) fn clear() {
    with(HashMap::clear);
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn only_the_right_old_mob_counts() {
        assert_eq!(real("salmon", EntityKind::Squid), Some(EntityKind::Salmon));
        assert_eq!(real("fox", EntityKind::Pig), None);
        assert_eq!(clean("\u{00A7}fHoglin "), "hoglin");
    }
}
