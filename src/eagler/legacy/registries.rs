//! The configuration phase a 1.12 server never sends.
//!
//! Modern clients learn their data-driven registries (dimension types, biomes, damage
//! types, chat types, mob variants and so on) from the server before play starts. A
//! 1.12 server has none of that, so we build the same registries from the game data we
//! bundle (`data/minecraft/...`, read through `platform::assets`) and hand them to
//! azalea as if a modern server had sent them.
//!
//! Dimension heights stay modern (the overworld runs from y = -64); the chunk translator
//! places 1.12's 16 sections at their real heights inside it.

use azalea_protocol::packets::Packet;
use azalea_protocol::packets::config::{
    ClientboundConfigPacket, ClientboundFinishConfiguration, ClientboundRegistryData,
};
use azalea_registry::identifier::Identifier;
use simdnbt::owned::{NbtCompound, NbtList, NbtTag};

/// Registries sent, by their folder under `data/minecraft/`. Anything azalea or the game
/// does not know is kept as raw NBT, so sending a registry it doesn't use is harmless.
const REGISTRIES: &[&str] = &[
    "dimension_type",
    "worldgen/biome",
    "damage_type",
    "chat_type",
    "painting_variant",
    "wolf_variant",
    "wolf_sound_variant",
    "cat_variant",
    "cat_sound_variant",
    "cow_variant",
    "cow_sound_variant",
    "pig_variant",
    "pig_sound_variant",
    "chicken_variant",
    "chicken_sound_variant",
    "frog_variant",
    "zombie_nautilus_variant",
    "trim_material",
    "trim_pattern",
    "banner_pattern",
    "enchantment",
    "jukebox_song",
    "instrument",
    "dialog",
    "timeline",
    "world_clock",
];

/// Dimension type ids in the order we send them; 1.12 dimension numbers map here.
pub(super) const DIMENSION_TYPES: [&str; 3] = ["overworld", "the_nether", "the_end"];

/// What the configuration phase told the client, which the translator needs later.
#[derive(Default)]
pub(super) struct Sent {
    /// Biome name (without `minecraft:`) to its index in the biome registry.
    pub(super) biomes: Vec<String>,
    pub(super) clocks: Vec<String>,
    pub(super) enchantments: Vec<String>,
}

impl Sent {
    pub(super) fn biome(&self, name: &str) -> Option<u32> {
        self.biomes.iter().position(|b| b == name).map(|i| i as u32)
    }

    pub(super) fn clock(&self, name: &str) -> Option<u32> {
        self.clocks.iter().position(|c| c == name).map(|i| i as u32)
    }
}

/// The configuration packets (as raw modern packet bodies) and what they contained.
pub(super) fn configuration() -> (Vec<Vec<u8>>, Sent) {
    let mut out = Vec::new();
    let mut sent = Sent::default();
    for folder in REGISTRIES {
        let mut entries = entries(folder);
        if *folder == "dimension_type" {
            entries = dimension_types(entries);
        }
        if entries.is_empty() {
            crate::log_warn!("net", "1.12: no bundled data for registry {folder}");
            continue;
        }
        let names: Vec<String> = entries.iter().map(|(name, _)| name.clone()).collect();
        match *folder {
            "worldgen/biome" => sent.biomes = names,
            "world_clock" => sent.clocks = names,
            "enchantment" => sent.enchantments = names,
            _ => {}
        }
        let packet = ClientboundRegistryData {
            registry_id: Identifier::new(format!("minecraft:{folder}")),
            entries: entries
                .into_iter()
                .map(|(name, nbt)| (Identifier::new(format!("minecraft:{name}")), Some(nbt)))
                .collect(),
        };
        if let Some(body) = encode(packet.into_variant()) {
            out.push(body);
        }
    }
    if let Some(body) = encode(ClientboundFinishConfiguration.into_variant()) {
        out.push(body);
    }
    crate::log_info!(
        "net",
        "1.12: built the configuration phase ({} registries, {} biomes)",
        out.len().saturating_sub(1),
        sent.biomes.len()
    );
    (out, sent)
}

fn encode(packet: ClientboundConfigPacket) -> Option<Vec<u8>> {
    match azalea_protocol::write::serialize_packet(&packet) {
        Ok(body) => Some(body.into_vec()),
        Err(e) => {
            crate::log_warn!("net", "1.12: could not encode a configuration packet: {e}");
            None
        }
    }
}

/// Every `*.json` in `data/minecraft/<folder>/`, as (name, NBT), sorted by name.
fn entries(folder: &str) -> Vec<(String, NbtCompound)> {
    let dir = crate::datapack_root().join(folder);
    let mut found = Vec::new();
    for path in crate::platform::assets::read_dir(&dir) {
        let Some(name) = path
            .file_name()
            .and_then(|n| n.to_str())
            .and_then(|n| n.strip_suffix(".json"))
        else {
            continue;
        };
        let Some(text) = crate::platform::assets::read_to_string(&path) else {
            continue;
        };
        match serde_json::from_str::<serde_json::Value>(&text) {
            Ok(serde_json::Value::Object(map)) => found.push((name.to_owned(), compound(&map))),
            Ok(_) => {}
            Err(e) => crate::log_warn!("net", "1.12: {folder}/{name}.json: {e}"),
        }
    }
    found.sort_by(|a, b| a.0.cmp(&b.0));
    found
}

/// The three 1.12 dimensions, first and in 1.12's order (heights stay modern).
fn dimension_types(mut all: Vec<(String, NbtCompound)>) -> Vec<(String, NbtCompound)> {
    let mut out = Vec::new();
    for name in DIMENSION_TYPES {
        let Some(at) = all.iter().position(|(n, _)| n == name) else {
            continue;
        };
        let (name, nbt) = all.remove(at);
        // Heights stay modern (overworld -64..320, nether and end 0..256): some of the
        // renderer assumes them, and 1.12 chunks are placed at their real y inside.
        out.push((name, nbt));
    }
    out.extend(all);
    out
}

fn compound(map: &serde_json::Map<String, serde_json::Value>) -> NbtCompound {
    let mut c = NbtCompound::new();
    for (key, value) in map {
        if let Some(tag) = tag(value) {
            c.insert(key.as_str(), tag);
        }
    }
    c
}

fn tag(value: &serde_json::Value) -> Option<NbtTag> {
    use serde_json::Value;
    Some(match value {
        Value::Null => return None,
        Value::Bool(b) => NbtTag::Byte(i8::from(*b)),
        Value::Number(n) => number(n),
        Value::String(s) => NbtTag::String(s.as_str().into()),
        Value::Object(map) => NbtTag::Compound(compound(map)),
        Value::Array(items) => NbtTag::List(list(items)),
    })
}

fn number(n: &serde_json::Number) -> NbtTag {
    if let Some(i) = n.as_i64() {
        match i32::try_from(i) {
            Ok(i) => NbtTag::Int(i),
            Err(_) => NbtTag::Long(i),
        }
    } else {
        NbtTag::Double(n.as_f64().unwrap_or(0.0))
    }
}

/// NBT lists hold one type. Mixed JSON arrays are wrapped the way the game does it:
/// each element in a compound under the empty key.
fn list(items: &[serde_json::Value]) -> NbtList {
    let tags: Vec<NbtTag> = items.iter().filter_map(tag).collect();
    if tags.is_empty() {
        return NbtList::Empty;
    }
    let first = tags[0].id();
    if tags.iter().any(|t| t.id() != first) {
        let all_numbers = tags
            .iter()
            .all(|t| matches!(t, NbtTag::Int(_) | NbtTag::Long(_) | NbtTag::Double(_)));
        if all_numbers {
            return NbtList::Double(
                tags.iter()
                    .map(|t| match t {
                        NbtTag::Int(i) => f64::from(*i),
                        NbtTag::Long(l) => *l as f64,
                        NbtTag::Double(d) => *d,
                        _ => 0.0,
                    })
                    .collect(),
            );
        }
        return NbtList::Compound(
            tags.into_iter()
                .map(|t| {
                    let mut c = NbtCompound::new();
                    c.insert("", t);
                    c
                })
                .collect(),
        );
    }
    macro_rules! gather {
        ($variant:ident) => {
            NbtList::$variant(
                tags.into_iter()
                    .filter_map(|t| match t {
                        NbtTag::$variant(v) => Some(v),
                        _ => None,
                    })
                    .collect(),
            )
        };
    }
    match &tags[0] {
        NbtTag::Byte(_) => gather!(Byte),
        NbtTag::Int(_) => gather!(Int),
        NbtTag::Long(_) => gather!(Long),
        NbtTag::Double(_) => gather!(Double),
        NbtTag::String(_) => gather!(String),
        NbtTag::Compound(_) => gather!(Compound),
        NbtTag::List(_) => gather!(List),
        _ => NbtList::Empty,
    }
}
