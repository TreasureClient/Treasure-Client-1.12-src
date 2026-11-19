//! Treasure Client: ViaItems, the same rules as the JS and WASM editions.
//!
//! A modern server behind ViaVersion/ViaBackwards turns modern items (a mace, netherite
//! gear, cobbled deepslate...) into a 1.12 look-alike before it reaches us, but keeps
//! clues: a backup of the real item id in the NBT, the item's modern name ("1.20.5 Mace")
//! and a CustomModelData number. This engine has every modern item, so a recognised
//! look-alike simply becomes the real item (its name, lore and enchantments are kept).
//!
//! In order:
//! 1. the id ViaBackwards backed up (`"VB|Protocol1_21_11To1_21_9": {..., id: N}` and the
//!    two other forms seen on real servers);
//! 2. the display name, matched against modern items' names (exact, or a modern item's
//!    name inside it; a player's rename on an item that has a backup is left alone);
//! 3. the CustomModelData number, for items ViaBackwards labelled with a version.

use std::collections::{HashMap, HashSet};
use std::str::FromStr as _;
use std::sync::{Mutex, OnceLock};

use azalea_registry::Registry as _;
use azalea_registry::builtin::ItemKind;

use super::items::Tag;

const DATA: &str = include_str!("via_items.txt");

struct Tables {
    /// version ("1_21_11") -> (id -> item name), and the version order (oldest first).
    ids: HashMap<String, HashMap<u32, String>>,
    order: Vec<String>,
    /// CustomModelData number -> item name.
    cmd: HashMap<u32, String>,
    /// lowercased name ("cobbled deepslate", "netherite sword") -> modern item name.
    exact: HashMap<String, String>,
    /// Modern item names as phrases (lowercased, spaces), longest first, with letter masks.
    phrases: Vec<(String, String, u64)>,
}

fn letters(s: &str) -> u64 {
    s.bytes().fold(0u64, |m, c| match c {
        b'a'..=b'z' => m | 1 << (c - b'a'),
        b'0'..=b'9' => m | 1 << (26 + c - b'0'),
        _ => m,
    })
}

fn tables() -> &'static Tables {
    static TABLES: OnceLock<Tables> = OnceLock::new();
    TABLES.get_or_init(|| {
        let mut t = Tables {
            ids: HashMap::new(),
            order: Vec::new(),
            cmd: HashMap::new(),
            exact: HashMap::new(),
            phrases: Vec::new(),
        };
        let pairs = |list: &str| -> HashMap<u32, String> {
            list.split(';')
                .filter_map(|p| {
                    let (n, name) = p.split_once('=')?;
                    Some((n.parse().ok()?, name.to_owned()))
                })
                .collect()
        };
        for line in DATA.lines() {
            let mut cols = line.split('\t');
            match cols.next() {
                Some("ver") => {
                    if let (Some(v), Some(list)) = (cols.next(), cols.next()) {
                        t.ids.insert(v.to_owned(), pairs(list));
                        t.order.push(v.to_owned());
                    }
                }
                Some("cmd") => {
                    if let Some(list) = cols.next() {
                        t.cmd = pairs(list);
                    }
                }
                Some("name") => {
                    if let (Some(reg), Some(display)) = (cols.next(), cols.next()) {
                        t.exact.insert(display.to_lowercase(), reg.to_owned());
                    }
                }
                _ => {}
            }
        }
        // Every item 1.12 doesn't have is a "modern" item.
        let old: HashSet<ItemKind> = super::items::old_kinds();
        let mut i = 0u32;
        while let Some(kind) = ItemKind::from_u32(i) {
            i += 1;
            if old.contains(&kind) {
                continue;
            }
            let full = kind.to_string();
            let name = full.strip_prefix("minecraft:").unwrap_or(&full).to_owned();
            let phrase = name.replace('_', " ");
            t.exact.entry(phrase.clone()).or_insert_with(|| name.clone());
            let mask = letters(&phrase);
            t.phrases.push((phrase, name, mask));
        }
        // longest first so "netherite sword" wins over a bare "sword"
        t.phrases.sort_by(|a, b| b.0.len().cmp(&a.0.len()));
        t
    })
}

/// Lowercase, without § formatting codes, trimmed.
fn clean(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut chars = s.chars();
    while let Some(c) = chars.next() {
        if c == '\u{00A7}' {
            chars.next();
            continue;
        }
        out.extend(c.to_lowercase());
    }
    out.trim().to_owned()
}

/// "1.20.5 mace" -> "mace"; anything not starting with a version is returned as-is.
fn strip_version(s: &str) -> &str {
    let lead = s.bytes().take_while(|c| c.is_ascii_digit() || *c == b'.').count();
    let head = &s[..lead];
    if head.contains('.') && head.bytes().any(|c| c.is_ascii_digit()) && s[lead..].starts_with(' ') {
        s[lead + 1..].trim()
    } else {
        s
    }
}

fn kind_of(name: &str) -> Option<ItemKind> {
    ItemKind::from_str(&format!("minecraft:{name}")).ok()
}

/// What ViaBackwards backed up: (the newest backup's item name, whether any backup was found).
fn from_backup(nbt: &Tag) -> (Option<String>, bool) {
    let t = tables();
    let mut best: Option<(String, usize)> = None;
    let mut saw = false;
    let mut consider = |key: &str, id: i32| {
        let Some(rest) = key.strip_prefix("VB|Protocol") else { return };
        let Some(to) = rest.find("To") else { return };
        let ver = &rest[..to];
        let Some(map) = t.ids.get(ver) else { return };
        saw = true;
        let rank = t.order.iter().position(|v| v == ver).unwrap_or(0);
        if let Some(name) = u32::try_from(id).ok().and_then(|id| map.get(&id))
            && best.as_ref().is_none_or(|(_, r)| rank > *r)
        {
            best = Some((name.clone(), rank));
        }
    };
    fn scan(c: &Tag, depth: u32, consider: &mut dyn FnMut(&str, i32)) {
        let Tag::Compound(entries) = c else { return };
        if depth > 8 {
            return;
        }
        let id = c.get("id").and_then(Tag::as_int);
        if let (Some(id), Some(Tag::String(tag))) = (id, c.get("backup_tag")) {
            consider(tag, id);
        }
        for (key, value) in entries {
            if key.starts_with("VB|Protocol") {
                match key[11..].find('|') {
                    None => {
                        if let Some(id) = id {
                            consider(key, id);
                        }
                    }
                    Some(_) if key.ends_with("|id") => {
                        if let Some(v) = value.as_int() {
                            consider(key, v);
                        }
                    }
                    Some(_) => {}
                }
            }
            if matches!(value, Tag::Compound(_)) {
                scan(value, depth + 1, consider);
            }
        }
    }
    scan(nbt, 0, &mut consider);
    (best.map(|(name, _)| name), saw)
}

/// The spear ViaBackwards turns into this sword.
fn spear_for(kind: ItemKind) -> Option<&'static str> {
    Some(match kind {
        ItemKind::WoodenSword => "wooden_spear",
        ItemKind::StoneSword => "stone_spear",
        ItemKind::IronSword => "iron_spear",
        ItemKind::GoldenSword => "golden_spear",
        ItemKind::DiamondSword => "diamond_spear",
        ItemKind::NetheriteSword => "netherite_spear",
        _ => return None,
    })
}

/// Item names this display name could be, in the order they're tried.
fn candidates(stand_in: ItemKind, lower: &str) -> Vec<String> {
    let t = tables();
    let mut out = Vec::new();
    let bare = strip_version(lower);
    let exact = t
        .exact
        .get(bare)
        .or_else(|| (bare != lower).then(|| t.exact.get(lower)).flatten())
        .cloned()
        .or_else(|| {
            let key = bare
                .strip_prefix("item.minecraft.")
                .or_else(|| bare.strip_prefix("block.minecraft."))?;
            (!key.is_empty()).then(|| key.to_owned())
        });
    out.extend(exact);
    if lower.contains("spear")
        && let Some(spear) = spear_for(stand_in)
    {
        out.push(spear.to_owned());
    }
    let have = letters(lower);
    for (phrase, name, mask) in &t.phrases {
        if mask & !have != 0 || phrase.len() < 4 || phrase.len() > lower.len() {
            continue;
        }
        if lower.contains(phrase.as_str()) {
            out.push(name.clone());
        }
    }
    out
}

/// The real modern item for a 1.12 look-alike, or `None` to keep it.
pub(super) fn remap(stand_in: ItemKind, nbt: Option<&Tag>) -> Option<ItemKind> {
    if !crate::tc_mods::get().via {
        return None;
    }
    let nbt = nbt?;
    let differs = |name: &str| kind_of(name).filter(|k| *k != stand_in);

    // 1) the id ViaBackwards saved (works even when the server renamed the item)
    let (backup, saw_backup) = from_backup(nbt);
    if let Some(kind) = backup.as_deref().and_then(differs) {
        return Some(kind);
    }
    let name = match nbt.get("display").and_then(|d| d.get("Name")) {
        Some(Tag::String(name)) => name,
        _ => return None, // down-mapped items always carry a name
    };
    let lower = clean(name);
    // ViaBackwards saved what this really was and it isn't a modern item: only its own
    // label ("1.16 Stone Brick Wall") counts, a player's rename doesn't
    if backup.is_none() && saw_backup && strip_version(&lower) == lower {
        return None;
    }

    // 2) the name (remembered per look-alike and name: server menus resend them a lot)
    static CACHE: Mutex<Option<HashMap<(u32, String), Option<ItemKind>>>> = Mutex::new(None);
    let key = (stand_in.to_u32(), lower.clone());
    let known = CACHE.lock().unwrap().get_or_insert_with(HashMap::new).get(&key).copied();
    let found = match known {
        Some(found) => found,
        None => {
            let found = candidates(stand_in, &lower).iter().find_map(|n| differs(n));
            let mut cache = CACHE.lock().unwrap();
            let cache = cache.get_or_insert_with(HashMap::new);
            if cache.len() >= 2048 {
                cache.clear();
            }
            cache.insert(key, found);
            found
        }
    };
    if found.is_some() {
        return found;
    }

    // 3) labelled by ViaBackwards ("1.20.5 ..."): the model number it stamps on every item
    if strip_version(&lower) != lower
        && let Some(v) = nbt.get("CustomModelData").and_then(Tag::as_int)
        && v >= 102
        && let Some(name) = tables().cmd.get(&(v as u32))
    {
        return differs(name);
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_labels() {
        assert_eq!(strip_version("1.20.5 mace"), "mace");
        assert_eq!(strip_version("mace"), "mace");
        assert_eq!(strip_version("1 mace"), "1 mace");
        assert_eq!(clean("\u{00A7}c1.17 Cobbled Deepslate "), "1.17 cobbled deepslate");
    }
}
