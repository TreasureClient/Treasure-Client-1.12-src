//! Treasure Client: 1.12.2 items, turned into modern ones.
//!
//! A 1.12 item is a number, a count, a "damage" value (a variant such as a wool colour,
//! or durability used for tools and armor) and optional NBT holding its name, lore,
//! enchantments, colour and so on. `items_1_12.tsv` (from minecraft-data, MIT) names the
//! modern item; the NBT becomes modern item components.

use std::collections::HashMap;
use std::str::FromStr;
use std::sync::OnceLock;

use azalea_chat::FormattedText;
use azalea_chat::text_component::TextComponent;
use azalea_inventory::ItemStack;
use azalea_inventory::components::{
    CustomName, Damage, DyedColor, Enchantments, Lore, StoredEnchantments, Unbreakable,
};
use azalea_registry::DataRegistry;
use azalea_registry::builtin::ItemKind;
use azalea_registry::data::Enchantment;

use super::wire::In;

const TABLE_TEXT: &str = include_str!("items_1_12.tsv");

/// `id << 16 | damage` -> modern item.
fn table() -> &'static HashMap<u32, ItemKind> {
    static TABLE: OnceLock<HashMap<u32, ItemKind>> = OnceLock::new();
    TABLE.get_or_init(|| {
        let mut map = HashMap::new();
        for line in TABLE_TEXT.lines() {
            if line.starts_with('#') || line.is_empty() {
                continue;
            }
            let Some((key, name)) = line.split_once('\t') else { continue };
            let Some((id, damage)) = key.split_once(':') else { continue };
            let (Ok(id), Ok(damage)) = (id.parse::<u32>(), damage.parse::<u32>()) else {
                continue;
            };
            if let Ok(kind) = ItemKind::from_str(&format!("minecraft:{name}")) {
                map.insert(id << 16 | (damage & 0xFFFF), kind);
            }
        }
        map
    })
}

/// 1.12 numeric enchantment ids.
fn enchantment_name(id: i16) -> Option<&'static str> {
    Some(match id {
        0 => "protection",
        1 => "fire_protection",
        2 => "feather_falling",
        3 => "blast_protection",
        4 => "projectile_protection",
        5 => "respiration",
        6 => "aqua_affinity",
        7 => "thorns",
        8 => "depth_strider",
        9 => "frost_walker",
        10 => "binding_curse",
        16 => "sharpness",
        17 => "smite",
        18 => "bane_of_arthropods",
        19 => "knockback",
        20 => "fire_aspect",
        21 => "looting",
        22 => "sweeping_edge",
        32 => "efficiency",
        33 => "silk_touch",
        34 => "unbreaking",
        35 => "fortune",
        48 => "power",
        49 => "punch",
        50 => "flame",
        51 => "infinity",
        61 => "luck_of_the_sea",
        62 => "lure",
        70 => "mending",
        71 => "vanishing_curse",
        _ => return None,
    })
}

/// What item translation needs from the configuration we sent.
#[derive(Default)]
pub(super) struct Ctx {
    /// Enchantment name -> its index in the enchantment registry we sent.
    pub(super) enchantments: HashMap<String, u32>,
}

/// One 1.12 item as read from a packet: the raw bytes (sent back to the server in clicks)
/// and the modern item.
pub(super) struct Read {
    pub(super) raw: Vec<u8>,
    pub(super) item: ItemStack,
}

/// The empty 1.12 slot.
pub(super) const EMPTY: [u8; 2] = [0xFF, 0xFF];

pub(super) fn read(r: &mut In, ctx: &Ctx) -> Option<Read> {
    let start = r.at();
    let id = r.i16()?;
    if id < 0 {
        return Some(Read { raw: EMPTY.to_vec(), item: ItemStack::Empty });
    }
    let count = r.i8()?;
    let damage = r.i16()?;
    let nbt = read_root(r)?;
    let raw = r.since(start).to_vec();
    Some(Read { raw, item: modern(id, count, damage, nbt.as_ref(), ctx) })
}

/// Every modern item a 1.12 item can turn into (for ViaItems: everything else is "modern").
pub(super) fn old_kinds() -> std::collections::HashSet<ItemKind> {
    table().values().copied().collect()
}

fn modern(id: i16, count: i8, damage: i16, nbt: Option<&Tag>, ctx: &Ctx) -> ItemStack {
    let table = table();
    let id = id as u16 as u32;
    let (mut kind, durability) = match table.get(&(id << 16 | damage as u16 as u32)) {
        Some(kind) => (*kind, 0),
        // Not a variant: the damage is wear (tools, armor), on the item's first entry.
        None => match table.get(&(id << 16)) {
            Some(kind) => (*kind, i32::from(damage).max(0)),
            None => (ItemKind::Barrier, 0),
        },
    };
    // Treasure Client: ViaItems, a look-alike from ViaBackwards becomes the real modern item.
    if let Some(real) = super::via_items::remap(kind, nbt) {
        kind = real;
    }
    let mut stack = ItemStack::new(kind, i32::from(count.max(1)));
    if durability > 0 {
        stack = stack.with_component(Damage { amount: durability });
    }
    let Some(nbt) = nbt else { return stack };
    if let Some(display) = nbt.get("display") {
        if let Some(Tag::String(name)) = display.get("Name") {
            stack = stack.with_component(CustomName { name: legacy_text(name) });
        }
        if let Some(Tag::List(lines)) = display.get("Lore") {
            let lines = lines
                .iter()
                .filter_map(|l| match l {
                    Tag::String(s) => Some(legacy_text(s)),
                    _ => None,
                })
                .collect();
            stack = stack.with_component(Lore { lines });
        }
        if let Some(Tag::Int(rgb)) = display.get("color") {
            stack = stack.with_component(DyedColor { rgb: *rgb });
        }
    }
    if let Some(levels) = nbt.get("ench").map(|l| enchantments(l, ctx)) {
        stack = stack.with_component(Enchantments { levels });
    }
    if let Some(levels) = nbt.get("StoredEnchantments").map(|l| enchantments(l, ctx)) {
        stack = stack.with_component(StoredEnchantments { enchantments: levels });
    }
    if matches!(nbt.get("Unbreakable"), Some(Tag::Byte(b)) if *b != 0) {
        stack = stack.with_component(Unbreakable);
    }
    stack
}

fn enchantments(list: &Tag, ctx: &Ctx) -> HashMap<Enchantment, i32> {
    let Tag::List(list) = list else { return HashMap::new() };
    list.iter()
        .filter_map(|e| {
            let id = e.get("id")?.as_int()?;
            let level = e.get("lvl")?.as_int()?;
            let name = enchantment_name(id as i16)?;
            let index = *ctx.enchantments.get(name)?;
            Some((Enchantment::new_raw(index), level))
        })
        .collect()
}

/// 1.12 names and lore are plain strings with § colour codes.
fn legacy_text(s: &str) -> FormattedText {
    FormattedText::Text(TextComponent::new(s.to_owned()))
}

/// A little NBT tree, enough for item data.
pub(super) enum Tag {
    Byte(i8),
    Short(i16),
    Int(i32),
    Long(i64),
    Float(f32),
    Double(f64),
    String(String),
    List(Vec<Tag>),
    Compound(Vec<(String, Tag)>),
    Other,
}

impl Tag {
    pub(super) fn get(&self, key: &str) -> Option<&Tag> {
        match self {
            Tag::Compound(entries) => entries.iter().find(|(k, _)| k == key).map(|(_, v)| v),
            _ => None,
        }
    }

    pub(super) fn as_int(&self) -> Option<i32> {
        match *self {
            Tag::Byte(v) => Some(i32::from(v)),
            Tag::Short(v) => Some(i32::from(v)),
            Tag::Int(v) => Some(v),
            Tag::Long(v) => Some(v as i32),
            Tag::Float(v) => Some(v as i32),
            Tag::Double(v) => Some(v as i32),
            _ => None,
        }
    }
}

/// A named root tag (1.12 items and metadata), or none when the first byte is 0.
pub(super) fn read_root(r: &mut In) -> Option<Option<Tag>> {
    let kind = r.u8()?;
    if kind == 0 {
        return Some(None);
    }
    let name_len = usize::from(r.i16()? as u16);
    r.bytes(name_len)?;
    Some(Some(payload(r, kind, 0)?))
}

fn nbt_string(r: &mut In) -> Option<String> {
    let len = usize::from(r.i16()? as u16);
    Some(String::from_utf8_lossy(r.bytes(len)?).into_owned())
}

fn payload(r: &mut In, kind: u8, depth: u32) -> Option<Tag> {
    if depth > 64 {
        return None;
    }
    Some(match kind {
        1 => Tag::Byte(r.i8()?),
        2 => Tag::Short(r.i16()?),
        3 => Tag::Int(r.i32()?),
        4 => Tag::Long(r.i64()?),
        5 => Tag::Float(r.f32()?),
        6 => Tag::Double(r.f64()?),
        7 => {
            let len = usize::try_from(r.i32()?).ok()?;
            r.bytes(len)?;
            Tag::Other
        }
        8 => Tag::String(nbt_string(r)?),
        9 => {
            let inner = r.u8()?;
            let len = usize::try_from(r.i32()?).unwrap_or(0);
            let mut items = Vec::with_capacity(len.min(256));
            for _ in 0..len {
                items.push(payload(r, inner, depth + 1)?);
            }
            Tag::List(items)
        }
        10 => {
            let mut entries = Vec::new();
            loop {
                let inner = r.u8()?;
                if inner == 0 {
                    break;
                }
                let key = nbt_string(r)?;
                entries.push((key, payload(r, inner, depth + 1)?));
            }
            Tag::Compound(entries)
        }
        11 => {
            let len = usize::try_from(r.i32()?).ok()?;
            r.bytes(len.checked_mul(4)?)?;
            Tag::Other
        }
        12 => {
            let len = usize::try_from(r.i32()?).ok()?;
            r.bytes(len.checked_mul(8)?)?;
            Tag::Other
        }
        _ => return None,
    })
}
