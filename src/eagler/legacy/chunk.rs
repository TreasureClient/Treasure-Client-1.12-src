//! Treasure Client: 1.12.2 chunks and block changes, turned into modern ones.
//!
//! A 1.12 block is a number and a 4-bit variant ("meta"). `blocks_1_12.tsv` (from
//! minecraft-data, MIT) says which modern block each one is. 1.12 servers only send what
//! is stored in the world, not how a block looks next to its neighbours, so after
//! converting a whole chunk we fix the neighbour-dependent parts the same way 1.12 did
//! when drawing: fences, panes and walls connect, door halves agree, double plants get
//! their upper half, and grass under snow is snowy.

use std::collections::HashMap;
use std::sync::{Arc, OnceLock};

use azalea::block::{BlockState, BlockTrait, fluid_state::FluidState};
use azalea::physics::collision::BlockWithShape;
use azalea::world::Section;
use azalea_buf::AzBuf;
use azalea_core::bitset::BitSet;
use azalea_core::position::{BlockPos, ChunkSectionBlockPos, ChunkSectionPos};
use azalea_protocol::packets::game::{
    ClientboundBlockUpdate, ClientboundLevelChunkWithLight, ClientboundSectionBlocksUpdate,
    ClientboundBlockEntityData,
    c_level_chunk_with_light::{BlockEntity as PacketBlockEntity, ClientboundLevelChunkPacketData},
    c_light_update::ClientboundLightUpdatePacketData,
    c_section_blocks_update::BlockStateWithPosition,
};
use azalea_registry::DataRegistry;
use azalea_registry::data::Biome;

use super::wire::In;

const TABLE_TEXT: &str = include_str!("blocks_1_12.tsv");

/// Sections in a 1.12 column (0..256).
const SECTIONS: usize = 16;
const NIBBLES: usize = 2048;

/// What a modern block state is, for the neighbour fix-ups.
#[derive(Clone, Copy, PartialEq, Eq)]
enum Kind {
    Other,
    WoodFence,
    NetherFence,
    Gate,
    Pane,
    Wall,
    Door,
    DoublePlant,
    Grassy,
    Snow,
}

struct Tables {
    /// 1.12 `id << 4 | meta` -> modern state id.
    legacy: Box<[u16]>,
    /// Modern state id -> its kind.
    kind: Box<[Kind]>,
    /// Modern state id -> a full solid cube (fences and panes connect to it).
    full: Box<[bool]>,
    /// Modern state id -> stops movement or holds fluid (the "motion blocking" heightmap).
    blocking: Box<[bool]>,
    /// Modern block name -> its default state.
    by_name: HashMap<&'static str, BlockState>,
}

fn tables() -> &'static Tables {
    static TABLES: OnceLock<Tables> = OnceLock::new();
    TABLES.get_or_init(build)
}

fn build() -> Tables {
    let max = usize::from(BlockState::MAX_STATE);
    let mut by_name: HashMap<&'static str, BlockState> = HashMap::new();
    let mut kind = vec![Kind::Other; max + 1];
    let mut full = vec![false; max + 1];
    let mut blocking = vec![false; max + 1];
    for id in 0..=max {
        let Ok(state) = BlockState::try_from(id as u32) else {
            continue;
        };
        let block: Box<dyn BlockTrait> = state.into();
        let name = block.id().trim_start_matches("minecraft:");
        by_name
            .entry(name)
            .or_insert_with(|| BlockState::from(block.as_block_kind()));
        full[id] = state.is_collision_shape_full();
        blocking[id] = !state.is_collision_shape_empty() || !FluidState::from(state).is_empty();
        kind[id] = match name {
            "nether_brick_fence" => Kind::NetherFence,
            n if n.ends_with("_fence") => Kind::WoodFence,
            n if n.ends_with("_fence_gate") => Kind::Gate,
            n if n.ends_with("glass_pane") || n == "iron_bars" => Kind::Pane,
            n if n.ends_with("_wall") && block.get_property("up").is_some() => Kind::Wall,
            n if n.ends_with("_door") && n != "iron_trapdoor" && !n.ends_with("trapdoor") => {
                Kind::Door
            }
            "tall_grass" | "large_fern" | "sunflower" | "lilac" | "rose_bush" | "peony" => {
                Kind::DoublePlant
            }
            "grass_block" | "podzol" | "mycelium" => Kind::Grassy,
            "snow" | "snow_block" => Kind::Snow,
            _ => Kind::Other,
        };
    }

    let mut legacy = vec![u16::MAX; 4096];
    let mut missing = 0;
    for line in TABLE_TEXT.lines() {
        if line.starts_with('#') || line.is_empty() {
            continue;
        }
        let Some((key, state)) = line.split_once('\t') else {
            continue;
        };
        let Some((id, meta)) = key.split_once(':') else {
            continue;
        };
        let (Ok(id), Ok(meta)) = (id.parse::<usize>(), meta.parse::<usize>()) else {
            continue;
        };
        let index = id << 4 | meta;
        if index >= legacy.len() {
            continue;
        }
        match modern(state, &by_name) {
            Some(state) => legacy[index] = state.id(),
            None => missing += 1,
        }
    }
    // Variants the table does not list look like the block's first variant.
    for index in 0..legacy.len() {
        if legacy[index] == u16::MAX {
            let first = legacy[index & !0xF];
            legacy[index] = if first == u16::MAX { 0 } else { first };
        }
    }
    if missing > 0 {
        crate::log_warn!("net", "1.12: {missing} old blocks have no modern match");
    }
    Tables {
        legacy: legacy.into_boxed_slice(),
        kind: kind.into_boxed_slice(),
        full: full.into_boxed_slice(),
        blocking: blocking.into_boxed_slice(),
        by_name,
    }
}

/// `name[a=b]` (a modern block) as a state.
pub(super) fn state_named(text: &str) -> Option<BlockState> {
    modern(text, &tables().by_name)
}

/// `name[a=b,c=d]` -> the modern state, starting from the block's default.
fn modern(text: &str, by_name: &HashMap<&'static str, BlockState>) -> Option<BlockState> {
    let (name, props) = match text.split_once('[') {
        Some((name, rest)) => (name, rest.trim_end_matches(']')),
        None => (text, ""),
    };
    let default = *by_name.get(name)?;
    let mut block: Box<dyn BlockTrait> = default.into();
    for pair in props.split(',').filter(|p| !p.is_empty()) {
        if let Some((key, value)) = pair.split_once('=') {
            // Properties that no longer exist (or changed) keep the modern default.
            let _ = block.set_property(key, value);
        }
    }
    Some(block.as_block_state())
}

/// A 1.12 global block id (`id << 4 | meta`) as a modern state.
pub(super) fn state(global: u32) -> BlockState {
    let id = tables()
        .legacy
        .get(global as usize)
        .copied()
        .unwrap_or(0);
    BlockState::try_from(u32::from(id)).unwrap_or(BlockState::AIR)
}

/// Reads one 1.12 section's blocks into `out` (4096 modern ids, y-z-x order).
fn read_section(r: &mut In, out: &mut [u16], special: &mut Vec<(usize, u32)>) -> Option<()> {
    let bits = usize::from(r.u8()?);
    let palette_len = usize::try_from(r.varint()?).ok()?;
    let mut palette = Vec::with_capacity(palette_len);
    for _ in 0..palette_len {
        palette.push(r.varint()? as u32);
    }
    let longs = usize::try_from(r.varint()?).ok()?;
    let mut data = Vec::with_capacity(longs);
    for _ in 0..longs {
        data.push(r.i64()? as u64);
    }
    if bits == 0 || bits > 32 {
        out.fill(0);
        return Some(());
    }
    let legacy = &tables().legacy;
    let mask = (1u64 << bits) - 1;
    for (i, slot) in out.iter_mut().enumerate().take(4096) {
        // 1.12 packs values back to back, so one may run over into the next long.
        let bit = i * bits;
        let (word, offset) = (bit >> 6, bit & 63);
        let mut value = *data.get(word)? >> offset;
        if offset + bits > 64 {
            value |= *data.get(word + 1)? << (64 - offset);
        }
        let value = (value & mask) as usize;
        let global = if palette_len > 0 {
            *palette.get(value)? as usize
        } else {
            value
        };
        *slot = legacy.get(global).copied().unwrap_or(0);
        if super::blockents::is_special(global as u32) {
            special.push((i, global as u32));
        }
    }
    Some(())
}

pub(super) struct Column {
    pub(super) packets: Vec<ClientboundLevelChunkWithLight>,
    pub(super) updates: Vec<ClientboundSectionBlocksUpdate>,
    /// Block entities of a partial update (a full chunk carries its own).
    pub(super) entity_data: Vec<ClientboundBlockEntityData>,
}

/// Reads a chunk's 1.12 block entities, changes the blocks whose look they decide, and
/// returns the modern ones.
fn block_entities(
    r: &mut In,
    x: i32,
    z: i32,
    blocks: &mut [u16],
    specials: &HashMap<(i32, i32, i32), u32>,
) -> Vec<super::blockents::Converted> {
    let mut list = Vec::new();
    let Some(count) = r.varint() else { return list };
    for _ in 0..count.max(0) {
        let Some(Some(tag)) = super::items::read_root(r) else { break };
        let Some(entity) = super::blockents::convert(&tag, |p| specials.get(&p).copied()) else {
            continue;
        };
        let (bx, by, bz) = entity.pos;
        if let Some(state) = entity.state
            && bx >> 4 == x
            && bz >> 4 == z
            && (0..256).contains(&by)
        {
            let index = (by as usize) << 8 | ((bz & 15) as usize) << 4 | (bx & 15) as usize;
            blocks[index] = state.id();
        }
        list.push(entity);
    }
    list
}

/// A 1.12 Chunk Data packet (after x and z) -> a modern chunk, or for a partial update,
/// whole-section block updates.
pub(super) fn chunk(
    r: &mut In,
    x: i32,
    z: i32,
    full: bool,
    has_sky: bool,
    below: usize,
    biome_of: impl Fn(u8) -> u32,
    specials: &mut HashMap<(i32, i32, i32), u32>,
) -> Option<Column> {
    let mask = r.varint()? as u32;
    let size = usize::try_from(r.varint()?).ok()?;
    let mut d = In::new(r.bytes(size)?);

    let mut blocks = vec![0u16; SECTIONS * 4096];
    let mut block_light: Vec<Option<&[u8]>> = vec![None; SECTIONS];
    let mut sky_light: Vec<Option<&[u8]>> = vec![None; SECTIONS];
    for s in 0..SECTIONS {
        if mask & (1 << s) == 0 {
            continue;
        }
        let mut special = Vec::new();
        read_section(&mut d, &mut blocks[s * 4096..(s + 1) * 4096], &mut special)?;
        let base_y = s as i32 * 16;
        specials.retain(|p, _| p.0 >> 4 != x || p.2 >> 4 != z || p.1 >> 4 != s as i32);
        for (i, global) in special {
            let pos = (x * 16 + (i & 15) as i32, base_y + (i >> 8) as i32, z * 16 + ((i >> 4) & 15) as i32);
            specials.insert(pos, global);
        }
        block_light[s] = Some(d.bytes(NIBBLES)?);
        if has_sky {
            sky_light[s] = Some(d.bytes(NIBBLES)?);
        }
    }

    if !full {
        let entities = block_entities(r, x, z, &mut blocks, specials);
        let entity_data = entities
            .into_iter()
            .filter_map(|e| {
                Some(ClientboundBlockEntityData {
                    pos: BlockPos::new(e.pos.0, e.pos.1, e.pos.2),
                    block_entity_type: Ok(e.kind?),
                    tag: e.data,
                })
            })
            .collect();
        let updates = (0..SECTIONS)
            .filter(|s| mask & (1 << s) != 0)
            .map(|s| ClientboundSectionBlocksUpdate {
                section_pos: ChunkSectionPos::new(x, s as i32, z),
                states: blocks[s * 4096..(s + 1) * 4096]
                    .iter()
                    .enumerate()
                    .map(|(i, id)| BlockStateWithPosition {
                        pos: ChunkSectionBlockPos::new((i & 15) as u8, (i >> 8) as u8, ((i >> 4) & 15) as u8),
                        state: BlockState::try_from(u32::from(*id)).unwrap_or(BlockState::AIR),
                    })
                    .collect(),
            })
            .collect();
        return Some(Column { packets: Vec::new(), updates, entity_data });
    }

    let biomes = d.bytes(256)?;
    fix_neighbours(&mut blocks);
    let entities = block_entities(r, x, z, &mut blocks, specials);
    let block_entities: Vec<_> = entities
        .into_iter()
        .filter_map(|e| {
            Some(PacketBlockEntity {
                packed_xz: ((e.pos.0 & 15) << 4 | (e.pos.2 & 15)) as u8,
                y: e.pos.1 as i16 as u16,
                kind: e.kind?,
                data: e.data,
            })
        })
        .collect();

    // `below` empty sections first (the modern overworld starts at y = -64, under 1.12's
    // bottom), then the 16 sections of 1.12, then empty sky to the modern top.
    let above = below;
    let total = below + SECTIONS + above;
    let mut data = Vec::with_capacity(total * 512);
    let column_biome = biome_of(biomes[8 * 16 + 8]);
    for _ in 0..below {
        let mut section = Section::default();
        for i in 0..64 {
            section.biomes.set_at_index(i, Biome::new_raw(column_biome));
        }
        section.azalea_write(&mut data).ok()?;
    }
    for s in 0..SECTIONS {
        let mut section = Section::default();
        for (i, id) in blocks[s * 4096..(s + 1) * 4096].iter().enumerate() {
            if *id == 0 {
                continue;
            }
            let state = BlockState::try_from(u32::from(*id)).unwrap_or(BlockState::AIR);
            section.states.set_at_index(i, state);
            section.block_count += 1;
            if !FluidState::from(state).is_empty() {
                section.fluid_count += 1;
            }
        }
        // Modern biomes are per 4x4x4 cell; 1.12 has one per column.
        for i in 0..64 {
            let (bx, bz) = (i & 3, (i >> 2) & 3);
            let old = biomes[(bz * 4 + 1) * 16 + bx * 4 + 1];
            section.biomes.set_at_index(i, Biome::new_raw(biome_of(old)));
        }
        section.azalea_write(&mut data).ok()?;
    }
    for _ in 0..above {
        let mut section = Section::default();
        for i in 0..64 {
            section.biomes.set_at_index(i, Biome::new_raw(column_biome));
        }
        section.azalea_write(&mut data).ok()?;
    }

    // Light: bit 0 is the section under the world, then every section, then one above.
    let bits = total + 2;
    let mut light = ClientboundLightUpdatePacketData {
        sky_y_mask: BitSet::new(bits),
        block_y_mask: BitSet::new(bits),
        empty_sky_y_mask: BitSet::new(bits),
        empty_block_y_mask: BitSet::new(bits),
        sky_updates: Arc::new(Box::new([])),
        block_updates: Arc::new(Box::new([])),
    };
    let mut sky = Vec::new();
    let mut block = Vec::new();
    for bit in 0..bits {
        // Index into the 1.12 sections, if this bit is one of them.
        let section = bit
            .checked_sub(1 + below)
            .filter(|s| *s < SECTIONS);
        let under = bit < 1 + below;
        match section.and_then(|s| block_light[s]) {
            Some(nibbles) => {
                light.block_y_mask.set(bit);
                block.push(Box::from(nibbles));
            }
            None => light.empty_block_y_mask.set(bit),
        }
        if !has_sky {
            continue;
        }
        match section.and_then(|s| sky_light[s]) {
            Some(nibbles) => {
                light.sky_y_mask.set(bit);
                sky.push(Box::from(nibbles));
            }
            // Empty sections and the ones above are open sky; under 1.12's floor is dark.
            None if !under => {
                light.sky_y_mask.set(bit);
                sky.push(vec![0xFFu8; NIBBLES].into_boxed_slice());
            }
            None => light.empty_sky_y_mask.set(bit),
        }
    }
    light.sky_updates = Arc::new(sky.into_boxed_slice());
    light.block_updates = Arc::new(block.into_boxed_slice());

    Some(Column {
        packets: vec![ClientboundLevelChunkWithLight {
            x,
            z,
            chunk_data: ClientboundLevelChunkPacketData {
                heightmaps: heightmaps(&blocks, below * 16),
                data: Arc::new(data.into_boxed_slice()),
                block_entities,
            },
            light_data: light,
        }],
        updates: Vec::new(),
        entity_data: Vec::new(),
    })
}

/// The top of each column (one above the highest block), packed the modern way:
/// 9 bits per column (256 or 384 high worlds), 7 to a long, none split across longs.
/// Heights count from the bottom of the world, `floor` blocks under 1.12's y = 0.
fn heightmaps(
    blocks: &[u16],
    floor: usize,
) -> Vec<(azalea_core::heightmap_kind::HeightmapKind, Box<[u64]>)> {
    use azalea_core::heightmap_kind::HeightmapKind;
    let t = tables();
    let pack = |test: &dyn Fn(u16) -> bool| -> Box<[u64]> {
        const BITS: usize = 9;
        const PER_LONG: usize = 64 / BITS;
        let mut longs = vec![0u64; 256usize.div_ceil(PER_LONG)];
        for column in 0..256 {
            let top = (0..256usize)
                .rev()
                .find(|y| test(blocks[y << 8 | column]))
                .map_or(0, |y| y + 1 + floor);
            longs[column / PER_LONG] |= (top as u64) << (column % PER_LONG * BITS);
        }
        longs.into_boxed_slice()
    };
    let blocking = |id: u16| t.blocking.get(usize::from(id)).copied().unwrap_or(false);
    vec![
        (HeightmapKind::WorldSurface, pack(&|id| id != 0)),
        (HeightmapKind::MotionBlocking, pack(&blocking)),
        (HeightmapKind::MotionBlockingNoLeaves, pack(&|id| blocking(id) && !is_leaves(id))),
    ]
}

fn is_leaves(id: u16) -> bool {
    static LEAVES: OnceLock<Box<[bool]>> = OnceLock::new();
    let table = LEAVES.get_or_init(|| {
        (0..=usize::from(BlockState::MAX_STATE))
            .map(|id| {
                BlockState::try_from(id as u32).is_ok_and(|s| {
                    let block: Box<dyn BlockTrait> = s.into();
                    block.id().ends_with("leaves")
                })
            })
            .collect()
    });
    table.get(usize::from(id)).copied().unwrap_or(false)
}

pub(super) fn block_update(pos: (i32, i32, i32), global: u32) -> ClientboundBlockUpdate {
    ClientboundBlockUpdate {
        pos: BlockPos::new(pos.0, pos.1, pos.2),
        block_state: state(global),
    }
}

pub(super) fn section_update(
    cx: i32,
    cz: i32,
    records: Vec<(u8, u8, u32)>,
) -> Vec<ClientboundSectionBlocksUpdate> {
    let mut by_section: Vec<ClientboundSectionBlocksUpdate> = Vec::new();
    for (xz, y, global) in records {
        let sy = i32::from(y >> 4);
        let entry = BlockStateWithPosition {
            pos: ChunkSectionBlockPos::new(xz >> 4, y & 15, xz & 15),
            state: state(global),
        };
        match by_section.iter_mut().find(|u| u.section_pos.y == sy) {
            Some(update) => update.states.push(entry),
            None => by_section.push(ClientboundSectionBlocksUpdate {
                section_pos: ChunkSectionPos::new(cx, sy, cz),
                states: vec![entry],
            }),
        }
    }
    by_section
}

fn at(x: i32, y: i32, z: i32) -> Option<usize> {
    ((0..16).contains(&x) && (0..256).contains(&y) && (0..16).contains(&z))
        .then(|| (y as usize) << 8 | (z as usize) << 4 | x as usize)
}

/// The neighbour-dependent looks 1.12 worked out when drawing (inside one chunk).
fn fix_neighbours(blocks: &mut [u16]) {
    let t = tables();
    let kind = |id: u16| t.kind.get(usize::from(id)).copied().unwrap_or(Kind::Other);
    let full = |id: u16| t.full.get(usize::from(id)).copied().unwrap_or(false);
    let state = |id: u16| BlockState::try_from(u32::from(id)).unwrap_or(BlockState::AIR);
    let snapshot = blocks.to_vec();
    let get = |x: i32, y: i32, z: i32| at(x, y, z).map_or(0, |i| snapshot[i]);

    for i in 0..blocks.len() {
        let id = snapshot[i];
        let k = kind(id);
        if k == Kind::Other || k == Kind::Snow {
            continue;
        }
        let (x, y, z) = ((i & 15) as i32, (i >> 8) as i32, ((i >> 4) & 15) as i32);
        let mut block: Box<dyn BlockTrait> = state(id).into();
        match k {
            Kind::WoodFence | Kind::NetherFence | Kind::Pane | Kind::Wall => {
                let joins = |n: u16| {
                    let nk = kind(n);
                    full(n)
                        || match k {
                            Kind::WoodFence => matches!(nk, Kind::WoodFence | Kind::Gate),
                            Kind::NetherFence => matches!(nk, Kind::NetherFence | Kind::Gate),
                            Kind::Pane => nk == Kind::Pane,
                            _ => matches!(nk, Kind::Wall | Kind::Gate),
                        }
                };
                let sides = [
                    ("north", get(x, y, z - 1)),
                    ("south", get(x, y, z + 1)),
                    ("west", get(x - 1, y, z)),
                    ("east", get(x + 1, y, z)),
                ];
                let mut joined = [false; 4];
                for (n, (side, neighbour)) in sides.iter().enumerate() {
                    joined[n] = joins(*neighbour);
                    let value = match (k, joined[n]) {
                        (Kind::Wall, true) => "low",
                        (Kind::Wall, false) => "none",
                        (_, true) => "true",
                        (_, false) => "false",
                    };
                    let _ = block.set_property(side, value);
                }
                if k == Kind::Wall {
                    // A wall has a post unless it is a straight line.
                    let straight = (joined[0] && joined[1] && !joined[2] && !joined[3])
                        || (joined[2] && joined[3] && !joined[0] && !joined[1]);
                    let above = get(x, y + 1, z);
                    let up = !straight || (above != 0 && kind(above) != Kind::Other);
                    let _ = block.set_property("up", if up { "true" } else { "false" });
                }
            }
            Kind::Door => {
                let upper = block.get_property("half") == Some("upper");
                let other = if upper { get(x, y - 1, z) } else { get(x, y + 1, z) };
                if kind(other) == Kind::Door {
                    let other: Box<dyn BlockTrait> = state(other).into();
                    let keys: &[&str] = if upper { &["facing", "open"] } else { &["hinge", "powered"] };
                    for key in keys {
                        if let Some(value) = other.get_property(key) {
                            let _ = block.set_property(key, value);
                        }
                    }
                }
            }
            Kind::DoublePlant => {
                let below = get(x, y - 1, z);
                if block.get_property("half") == Some("upper") && kind(below) == Kind::DoublePlant {
                    let mut lower: Box<dyn BlockTrait> = state(below).into();
                    let _ = lower.set_property("half", "upper");
                    blocks[i] = lower.as_block_state().id();
                }
                continue;
            }
            Kind::Grassy => {
                let snowy = kind(get(x, y + 1, z)) == Kind::Snow;
                let _ = block.set_property("snowy", if snowy { "true" } else { "false" });
            }
            Kind::Gate | Kind::Snow | Kind::Other => continue,
        }
        blocks[i] = block.as_block_state().id();
    }
}

/// 1.12 biome id -> modern biome name.
pub(super) fn biome_name(id: u8) -> &'static str {
    match id {
        0 => "ocean",
        1 => "plains",
        2 | 17 | 130 => "desert",
        3 | 20 => "windswept_hills",
        4 | 18 => "forest",
        5 | 19 | 133 => "taiga",
        6 | 134 => "swamp",
        7 => "river",
        8 => "nether_wastes",
        9 => "the_end",
        10 => "frozen_ocean",
        11 => "frozen_river",
        12 | 13 => "snowy_plains",
        14 | 15 => "mushroom_fields",
        16 => "beach",
        21 | 22 | 149 => "jungle",
        23 | 151 => "sparse_jungle",
        24 => "deep_ocean",
        25 => "stony_shore",
        26 => "snowy_beach",
        27 | 28 => "birch_forest",
        29 | 157 => "dark_forest",
        30 | 31 | 158 => "snowy_taiga",
        32 | 33 => "old_growth_pine_taiga",
        34 => "windswept_forest",
        35 => "savanna",
        36 => "savanna_plateau",
        37 | 39 | 167 => "badlands",
        38 | 166 => "wooded_badlands",
        127 => "the_void",
        129 => "sunflower_plains",
        131 | 162 => "windswept_gravelly_hills",
        132 => "flower_forest",
        140 => "ice_spikes",
        155 | 156 => "old_growth_birch_forest",
        160 | 161 => "old_growth_spruce_taiga",
        163 | 164 => "windswept_savanna",
        165 => "eroded_badlands",
        _ => "plains",
    }
}
