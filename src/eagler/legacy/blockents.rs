//! Treasure Client: 1.12.2 block entities (signs, skulls, banners, chests, beds, flower
//! pots...) turned into modern ones.
//!
//! In 1.12 a skull's type, a banner's or bed's colour and a flower pot's plant were kept
//! in the block entity, not in the block, so for those the block itself changes too.

use azalea::block::BlockState;
use azalea_registry::builtin::BlockEntityKind;
use simdnbt::Mutf8String;
use simdnbt::owned::{Nbt, NbtCompound, NbtList, NbtTag};

use super::chunk;
use super::items::Tag;

/// 1.12 blocks whose look comes from their block entity.
pub(super) fn is_special(global: u32) -> bool {
    matches!(global >> 4, 26 | 140 | 144 | 176 | 177)
}

pub(super) struct Converted {
    pub(super) pos: (i32, i32, i32),
    pub(super) kind: Option<BlockEntityKind>,
    pub(super) data: Nbt,
    /// The block, when the block entity decides how it looks.
    pub(super) state: Option<BlockState>,
}

/// `special` is the 1.12 block at the position, when it is one of [`is_special`].
pub(super) fn convert(tag: &Tag, special: impl Fn((i32, i32, i32)) -> Option<u32>) -> Option<Converted> {
    let pos = (tag.get("x")?.as_int()?, tag.get("y")?.as_int()?, tag.get("z")?.as_int()?);
    let id = match tag.get("id") {
        Some(Tag::String(id)) => id.strip_prefix("minecraft:").unwrap_or(id).to_owned(),
        _ => return None,
    };
    let global = special(pos);
    let meta = global.map(|g| g & 15).unwrap_or(0);
    let mut state = None;
    let mut out: Vec<(Mutf8String, NbtTag)> = Vec::new();
    let kind = match id.as_str() {
        "sign" => {
            let lines: Vec<Mutf8String> = (1..=4)
                .map(|i| match tag.get(&format!("Text{i}")) {
                    Some(Tag::String(json)) => Mutf8String::from(legacy_line(json).as_str()),
                    _ => Mutf8String::from(""),
                })
                .collect();
            let face = |lines: Vec<Mutf8String>| {
                NbtTag::Compound(NbtCompound::from_values(vec![
                    ("messages".into(), NbtTag::List(NbtList::String(lines))),
                    ("color".into(), NbtTag::String("black".into())),
                    ("has_glowing_text".into(), NbtTag::Byte(0)),
                ]))
            };
            out.push(("front_text".into(), face(lines)));
            out.push(("back_text".into(), face(vec![Mutf8String::from(""); 4])));
            out.push(("is_waxed".into(), NbtTag::Byte(0)));
            Some(BlockEntityKind::Sign)
        }
        "skull" => {
            let kind = tag.get("SkullType").and_then(Tag::as_int).unwrap_or(0);
            let rot = tag.get("Rot").and_then(Tag::as_int).unwrap_or(0) & 15;
            let (floor, wall) = match kind {
                1 => ("wither_skeleton_skull", "wither_skeleton_wall_skull"),
                2 => ("zombie_head", "zombie_wall_head"),
                3 => ("player_head", "player_wall_head"),
                4 => ("creeper_head", "creeper_wall_head"),
                5 => ("dragon_head", "dragon_wall_head"),
                _ => ("skeleton_skull", "skeleton_wall_skull"),
            };
            if global.is_some() {
                state = match facing(meta) {
                    Some(f) => chunk::state_named(&format!("{wall}[facing={f}]")),
                    None => chunk::state_named(&format!("{floor}[rotation={rot}]")),
                };
            }
            if let Some(owner) = tag.get("Owner") {
                out.push(("profile".into(), NbtTag::Compound(profile(owner))));
            }
            Some(BlockEntityKind::Skull)
        }
        "banner" => {
            let base = 15 - (tag.get("Base").and_then(Tag::as_int).unwrap_or(0) & 15);
            let color = DYES[base as usize];
            if global.is_some() {
                state = if global.unwrap_or(0) >> 4 == 177 {
                    chunk::state_named(&format!("{color}_wall_banner[facing={}]", facing(meta).unwrap_or("north")))
                } else {
                    chunk::state_named(&format!("{color}_banner[rotation={meta}]"))
                };
            }
            if let Some(Tag::List(patterns)) = tag.get("Patterns") {
                let list = patterns
                    .iter()
                    .filter_map(|p| {
                        let Some(Tag::String(code)) = p.get("Pattern") else { return None };
                        let name = pattern(code)?;
                        let c = 15 - (p.get("Color")?.as_int()? & 15);
                        Some(NbtCompound::from_values(vec![
                            ("pattern".into(), NbtTag::String(format!("minecraft:{name}").as_str().into())),
                            ("color".into(), NbtTag::String(DYES[c as usize].into())),
                        ]))
                    })
                    .collect();
                out.push(("patterns".into(), NbtTag::List(NbtList::Compound(list))));
            }
            Some(BlockEntityKind::Banner)
        }
        "bed" => {
            let color = DYES[(tag.get("color").and_then(Tag::as_int).unwrap_or(14) & 15) as usize];
            if global.is_some() {
                let facing = ["south", "west", "north", "east"][(meta & 3) as usize];
                let part = if meta & 8 != 0 { "head" } else { "foot" };
                let occupied = meta & 4 != 0;
                state = chunk::state_named(&format!(
                    "{color}_bed[facing={facing},part={part},occupied={occupied}]"
                ));
            }
            None
        }
        "flower_pot" => {
            let item = match tag.get("Item") {
                Some(Tag::String(s)) => s.strip_prefix("minecraft:").unwrap_or(s).to_owned(),
                _ => String::new(),
            };
            let data = tag.get("Data").and_then(Tag::as_int).unwrap_or(0);
            if global.is_some() {
                state = chunk::state_named(potted(&item, data));
            }
            None
        }
        "chest" => Some(BlockEntityKind::Chest),
        "ender_chest" => Some(BlockEntityKind::EnderChest),
        "shulker_box" => Some(BlockEntityKind::ShulkerBox),
        "beacon" => Some(BlockEntityKind::Beacon),
        "enchanting_table" => Some(BlockEntityKind::EnchantingTable),
        "end_portal" => Some(BlockEntityKind::EndPortal),
        "end_gateway" => Some(BlockEntityKind::EndGateway),
        "mob_spawner" => Some(BlockEntityKind::MobSpawner),
        _ => None,
    };
    if let Some(Tag::String(name)) = tag.get("CustomName") {
        out.push(("CustomName".into(), NbtTag::String(name.as_str().into())));
    }
    Some(Converted { pos, kind, data: Nbt::new("".into(), NbtCompound::from_values(out)), state })
}

/// 1.12 facing numbers for wall skulls and banners.
fn facing(meta: u32) -> Option<&'static str> {
    Some(match meta & 7 {
        2 => "north",
        3 => "south",
        4 => "west",
        5 => "east",
        _ => return None,
    })
}

const DYES: [&str; 16] = [
    "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
    "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
];

fn pattern(code: &str) -> Option<&'static str> {
    Some(match code {
        "bs" => "stripe_bottom",
        "ts" => "stripe_top",
        "ls" => "stripe_left",
        "rs" => "stripe_right",
        "cs" => "stripe_center",
        "ms" => "stripe_middle",
        "drs" => "stripe_downright",
        "dls" => "stripe_downleft",
        "ss" => "small_stripes",
        "cr" => "cross",
        "sc" => "straight_cross",
        "ld" => "diagonal_left",
        "rud" => "diagonal_right",
        "lud" => "diagonal_up_left",
        "rd" => "diagonal_up_right",
        "vh" => "half_vertical",
        "vhr" => "half_vertical_right",
        "hh" => "half_horizontal",
        "hhb" => "half_horizontal_bottom",
        "bl" => "square_bottom_left",
        "br" => "square_bottom_right",
        "tl" => "square_top_left",
        "tr" => "square_top_right",
        "bt" => "triangle_bottom",
        "tt" => "triangle_top",
        "bts" => "triangles_bottom",
        "tts" => "triangles_top",
        "mc" => "circle",
        "mr" => "rhombus",
        "bo" => "border",
        "cbo" => "curly_border",
        "bri" => "bricks",
        "gra" => "gradient",
        "gru" => "gradient_up",
        "cre" => "creeper",
        "sku" => "skull",
        "flo" => "flower",
        "moj" => "mojang",
        _ => return None,
    })
}

fn potted(item: &str, data: i32) -> &'static str {
    const SAPLINGS: [&str; 6] = [
        "potted_oak_sapling", "potted_spruce_sapling", "potted_birch_sapling",
        "potted_jungle_sapling", "potted_acacia_sapling", "potted_dark_oak_sapling",
    ];
    const FLOWERS: [&str; 9] = [
        "potted_poppy", "potted_blue_orchid", "potted_allium", "potted_azure_bluet",
        "potted_red_tulip", "potted_orange_tulip", "potted_white_tulip", "potted_pink_tulip",
        "potted_oxeye_daisy",
    ];
    let d = usize::try_from(data).unwrap_or(0);
    match item {
        "sapling" => SAPLINGS.get(d).copied().unwrap_or(SAPLINGS[0]),
        "red_flower" => FLOWERS.get(d).copied().unwrap_or(FLOWERS[0]),
        "yellow_flower" => "potted_dandelion",
        "red_mushroom" => "potted_red_mushroom",
        "brown_mushroom" => "potted_brown_mushroom",
        "cactus" => "potted_cactus",
        "deadbush" => "potted_dead_bush",
        "tallgrass" => "potted_fern",
        _ => "flower_pot",
    }
}

/// A 1.12 skull owner as a modern profile.
fn profile(owner: &Tag) -> NbtCompound {
    let mut values: Vec<(Mutf8String, NbtTag)> = Vec::new();
    if let Some(Tag::String(name)) = owner.get("Name") {
        values.push(("name".into(), NbtTag::String(name.as_str().into())));
    }
    let textures = owner
        .get("Properties")
        .and_then(|p| p.get("textures"))
        .and_then(|t| match t {
            Tag::List(list) => list.first(),
            _ => None,
        });
    if let Some(Tag::String(value)) = textures.and_then(|t| t.get("Value")) {
        let mut entry: Vec<(Mutf8String, NbtTag)> = vec![
            ("name".into(), NbtTag::String("textures".into())),
            ("value".into(), NbtTag::String(value.as_str().into())),
        ];
        if let Some(Tag::String(sig)) = textures.and_then(|t| t.get("Signature")) {
            entry.push(("signature".into(), NbtTag::String(sig.as_str().into())));
        }
        values.push((
            "properties".into(),
            NbtTag::List(NbtList::Compound(vec![NbtCompound::from_values(entry)])),
        ));
    }
    NbtCompound::from_values(values)
}

/// A 1.12 sign line (JSON text) as plain text with § colour codes.
pub(super) fn legacy_line(json: &str) -> String {
    let mut out = String::new();
    match serde_json::from_str::<serde_json::Value>(json) {
        Ok(value) => walk(&value, &mut out, None),
        Err(_) => out.push_str(json),
    }
    out
}

fn walk(value: &serde_json::Value, out: &mut String, parent_color: Option<char>) {
    use serde_json::Value;
    match value {
        Value::String(s) => out.push_str(s),
        Value::Array(parts) => parts.iter().for_each(|p| walk(p, out, parent_color)),
        Value::Object(map) => {
            let color = map
                .get("color")
                .and_then(Value::as_str)
                .and_then(color_code)
                .or(parent_color);
            let mut codes = String::new();
            if let Some(c) = color {
                codes.push('§');
                codes.push(c);
            }
            for (key, code) in [("bold", 'l'), ("italic", 'o'), ("underlined", 'n'), ("strikethrough", 'm'), ("obfuscated", 'k')] {
                if map.get(key).and_then(Value::as_bool) == Some(true) {
                    codes.push('§');
                    codes.push(code);
                }
            }
            if let Some(text) = map.get("text").and_then(Value::as_str) {
                if !text.is_empty() {
                    out.push_str(&codes);
                    out.push_str(text);
                }
            }
            if let Some(Value::Array(extra)) = map.get("extra") {
                for part in extra {
                    walk(part, out, color);
                }
            }
        }
        _ => {}
    }
}

fn color_code(name: &str) -> Option<char> {
    Some(match name {
        "black" => '0',
        "dark_blue" => '1',
        "dark_green" => '2',
        "dark_aqua" => '3',
        "dark_red" => '4',
        "dark_purple" => '5',
        "gold" => '6',
        "gray" => '7',
        "dark_gray" => '8',
        "blue" => '9',
        "green" => 'a',
        "aqua" => 'b',
        "red" => 'c',
        "light_purple" => 'd',
        "yellow" => 'e',
        "white" => 'f',
        "reset" => 'r',
        _ => return None,
    })
}
