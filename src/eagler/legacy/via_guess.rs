//! Treasure Client: Modern Block Guessing (ViaBlocks), the same rules as the JS and WASM
//! editions (TCViaGuess).
//!
//! ViaBackwards sends modern blocks as old look-alikes (deepslate as nether bricks, moss as
//! grass, cherry logs as acacia...). Nothing in the block says which is which, so the
//! guesses go by where a block is: the biome, how deep under the ground, how dark, and
//! what is next to it. They run on the 1.12 block numbers of a whole chunk, just before it
//! is turned into modern blocks, so a guess can read the old look-alikes around it.
//!
//! Also here: blocks you placed yourself (we know what they really are), and smithing
//! tables (they arrive as crafting tables; one is remembered once it opens the smithing
//! menu).

use std::collections::{HashMap, HashSet};
use std::sync::{Mutex, OnceLock};

use azalea::block::BlockState;
use azalea_registry::builtin::ItemKind;

// ---- 1.12 look-alike block ids (a 1.12 code is id << 4 | meta) ------------------------

const STONE: u16 = 1;
const GRASS: u16 = 2;
const DIRT: u16 = 3;
const COBBLESTONE: u16 = 4;
const LAVA: u16 = 10;
const FLOWING_LAVA: u16 = 11;
const GRAVEL: u16 = 13;
const GOLD_ORE: u16 = 14;
const IRON_ORE: u16 = 15;
const COAL_ORE: u16 = 16;
const LOG: u16 = 17;
const LEAVES: u16 = 18;
const LAPIS_ORE: u16 = 21;
const TALLGRASS: u16 = 31;
const WOOL: u16 = 35;
const RED_FLOWER: u16 = 38;
const BROWN_MUSHROOM: u16 = 39;
const RED_MUSHROOM: u16 = 40;
const GOLD_BLOCK: u16 = 41;
const DOUBLE_STONE_SLAB: u16 = 43;
const STONE_SLAB: u16 = 44;
const OBSIDIAN: u16 = 49;
const MOB_SPAWNER: u16 = 52;
const DIAMOND_ORE: u16 = 56;
const CRAFTING_TABLE: u16 = 58;
const REDSTONE_ORE: u16 = 73;
const LIT_REDSTONE_ORE: u16 = 74;
const NETHERRACK: u16 = 87;
const SOUL_SAND: u16 = 88;
const LIT_PUMPKIN: u16 = 91;
const VINE: u16 = 106;
const BRICK_STAIRS: u16 = 108;
const MYCELIUM: u16 = 110;
const NETHER_BRICK: u16 = 112;
const NETHER_BRICK_FENCE: u16 = 113;
const NETHER_BRICK_STAIRS: u16 = 114;
const NETHER_WART: u16 = 115;
const EMERALD_ORE: u16 = 129;
const COBBLE_WALL: u16 = 139;
const LEAVES2: u16 = 161;
const LOG2: u16 = 162;
const CARPET: u16 = 171;
const PURPUR_BLOCK: u16 = 201;
const PURPUR_PILLAR: u16 = 202;
const MAGMA: u16 = 213;
const RED_NETHER_BRICK: u16 = 215;
const GRAY_GLAZED: u16 = 242;

// ---- the modern blocks a guess can give (codes from 4096 up) --------------------------

const T: u16 = 4096;
const NAMES: [&str; 88] = [
    "deepslate", "tuff", "amethyst_block", "budding_amethyst", "calcite", "smooth_basalt", "basalt",
    "crimson_stem", "crimson_hyphae", "warped_stem", "warped_hyphae", "crimson_nylium", "warped_nylium",
    "warped_wart_block", "shroomlight", "soul_soil", "ancient_debris", "polished_blackstone_bricks",
    "nether_gold_ore", "deepslate_coal_ore", "deepslate_iron_ore", "copper_ore", "deepslate_copper_ore",
    "deepslate_gold_ore", "deepslate_diamond_ore", "deepslate_emerald_ore", "deepslate_lapis_ore",
    "deepslate_redstone_ore", "mangrove_log", "mangrove_wood", "mangrove_leaves", "mangrove_roots", "mud",
    "cherry_log", "cherry_wood", "cherry_leaves", "pale_oak_log", "pale_oak_wood", "pale_oak_leaves",
    "moss_block", "moss_carpet", "pale_moss_carpet", "short_dry_grass", "tall_dry_grass", "cornflower",
    "lily_of_the_valley", "open_eyeblossom", "closed_eyeblossom", "cave_vines", "hanging_roots",
    "mangrove_propagule", "pale_hanging_moss", "pink_petals", "dripstone_block", "pointed_dripstone",
    "small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud", "amethyst_cluster", "crimson_roots",
    "warped_roots", "nether_sprouts", "crimson_fungus", "warped_fungus", "weeping_vines", "twisting_vines",
    "blackstone", "blackstone_stairs", "polished_blackstone_brick_stairs", "blackstone_slab",
    "polished_blackstone_brick_slab", "polished_blackstone", "smithing_table",
    // the 1.14+ walls, in WALL order
    "stone_brick_wall", "mossy_stone_brick_wall", "brick_wall", "andesite_wall", "granite_wall",
    "diorite_wall", "sandstone_wall", "red_sandstone_wall", "prismarine_wall", "nether_brick_wall",
    "red_nether_brick_wall", "end_stone_brick_wall", "blackstone_wall", "polished_blackstone_wall",
    "polished_blackstone_brick_wall",
];
const DEEPSLATE: u16 = T;
const TUFF: u16 = T + 1;
const AMETHYST: u16 = T + 2;
const BUDDING: u16 = T + 3;
const CALCITE: u16 = T + 4;
const SMOOTH_BASALT: u16 = T + 5;
const BASALT: u16 = T + 6;
const CRIMSON_STEM: u16 = T + 7;
const CRIMSON_HYPHAE: u16 = T + 8;
const WARPED_STEM: u16 = T + 9;
const WARPED_HYPHAE: u16 = T + 10;
const CRIMSON_NYLIUM: u16 = T + 11;
const WARPED_NYLIUM: u16 = T + 12;
const WARPED_WART: u16 = T + 13;
const SHROOMLIGHT: u16 = T + 14;
const SOUL_SOIL: u16 = T + 15;
const DEBRIS: u16 = T + 16;
const PBB_BRICKS: u16 = T + 17;
const NETHER_GOLD: u16 = T + 18;
const D_COAL: u16 = T + 19;
const D_IRON: u16 = T + 20;
const COPPER: u16 = T + 21;
const D_COPPER: u16 = T + 22;
const D_GOLD: u16 = T + 23;
const D_DIAMOND: u16 = T + 24;
const D_EMERALD: u16 = T + 25;
const D_LAPIS: u16 = T + 26;
const D_REDSTONE: u16 = T + 27;
const MANGROVE_LOG: u16 = T + 28;
const MANGROVE_WOOD: u16 = T + 29;
const MANGROVE_LEAVES: u16 = T + 30;
const MANGROVE_ROOTS: u16 = T + 31;
const MUD: u16 = T + 32;
const CHERRY_LOG: u16 = T + 33;
const CHERRY_WOOD: u16 = T + 34;
const CHERRY_LEAVES: u16 = T + 35;
const PALE_LOG: u16 = T + 36;
const PALE_WOOD: u16 = T + 37;
const PALE_LEAVES: u16 = T + 38;
const MOSS: u16 = T + 39;
const MOSS_CARPET: u16 = T + 40;
const PALE_MOSS_CARPET: u16 = T + 41;
const SHORT_DRY: u16 = T + 42;
const TALL_DRY: u16 = T + 43;
const CORNFLOWER: u16 = T + 44;
const LILY: u16 = T + 45;
const OPEN_EYE: u16 = T + 46;
const CLOSED_EYE: u16 = T + 47;
const CAVE_VINES: u16 = T + 48;
const HANGING_ROOTS: u16 = T + 49;
const PROPAGULE: u16 = T + 50;
const PALE_MOSS: u16 = T + 51;
const PINK_PETALS: u16 = T + 52;
const DRIPSTONE: u16 = T + 53;
const POINTED_DRIP: u16 = T + 54;
const BUD_SMALL: u16 = T + 55;
const BUD_MEDIUM: u16 = T + 56;
const BUD_LARGE: u16 = T + 57;
const CLUSTER: u16 = T + 58;
const CRIMSON_ROOTS: u16 = T + 59;
const WARPED_ROOTS: u16 = T + 60;
const SPROUTS: u16 = T + 61;
const CRIMSON_FUNGUS: u16 = T + 62;
const WARPED_FUNGUS: u16 = T + 63;
const WEEPING: u16 = T + 64;
const TWISTING: u16 = T + 65;
const BLACKSTONE: u16 = T + 66;
const BS_STAIRS: u16 = T + 67;
const PBB_STAIRS: u16 = T + 68;
const BS_SLAB: u16 = T + 69;
const PBB_SLAB: u16 = T + 70;
const POLISHED_BS: u16 = T + 71;
const SMITHING: u16 = T + 72;
const WALL0: u16 = T + 73;
/// A modern block you placed yourself (its real state is in the placed list).
const PLACED: u16 = u16::MAX;

/// The modern default state of each guess (`None` if this engine lacks one).
fn guess_states() -> &'static [Option<BlockState>] {
    static STATES: OnceLock<Vec<Option<BlockState>>> = OnceLock::new();
    STATES.get_or_init(|| NAMES.iter().map(|n| super::chunk::state_named(n)).collect())
}

fn tag_state(code: u16) -> Option<BlockState> {
    guess_states().get(usize::from(code.checked_sub(T)?)).copied().flatten()
}

// ---- limits from the WASM edition -----------------------------------------------------

/// Lush and dripstone caves: underground rules only look below this height.
const UNDER_MAX_Y: i32 = 60;
/// Deepslate only reaches above y=0 a few blocks (y 0 to 7).
const DEEP_MAX_Y: i32 = 7;
/// Amethyst geodes are underground.
const GEODE_MAX_Y: i32 = 40;
/// Copper ore doesn't generate above this.
const COPPER_MAX_Y: i32 = 112;
/// Iron veins are at most 9 blocks; a vein this big is copper.
const COPPER_VEIN: usize = 10;
const MAX_IRON: usize = 8192;
const MAX_PLACED: usize = 16384;
const MAX_SMITH: usize = 512;

// biome groups. ViaBackwards turns modern biomes into old ones: mangrove swamp -> swamp,
// cherry grove -> flower forest, pale garden -> gravelly mountains.
const B_SWAMP: u8 = 1;
const B_FLOWER: u8 = 2;
const B_PALE: u8 = 4;
const B_DRY: u8 = 8;
const B_FOREST: u8 = 16;
const B_PLAINS: u8 = 32;

fn group(biome: u8) -> u8 {
    match biome {
        6 | 134 => B_SWAMP,
        132 => B_FLOWER,
        131 => B_PALE,
        2 | 17 | 130 | 37 | 38 | 39 | 165 | 166 | 167 => B_DRY,
        4 | 18 | 27 | 28 | 29 | 155 | 156 | 157 => B_FOREST,
        1 | 129 => B_PLAINS,
        _ => 0,
    }
}

// neighbour kinds (bit flags), counting both the look-alike and an already guessed block
const DEEPSLATE_LIKE: u32 = 1;
const CALCITE_LIKE: u32 = 2;
const AMETHYST_LIKE: u32 = 4;
const BUDDING_LIKE: u32 = 8;
const BASALT_LIKE: u32 = 16;
const OBSIDIAN_LIKE: u32 = 32;
const NETHER_STONE: u32 = 64;
const DRIP_LIKE: u32 = 128;
const MOSS_LIKE: u32 = 256;
const COBBLE_LIKE: u32 = 512;
const FORTRESS_LIKE: u32 = 1024;
const BLACKSTONE_HINT: u32 = 2048;

/// The 1.12 block id of a code (a guessed block keeps its own code, never an old id).
fn id_of(c: u16) -> u16 {
    if c < T { c >> 4 } else { c }
}

fn meta(c: u16) -> u16 {
    c & 15
}

fn kind_of(c: Option<u16>) -> u32 {
    let Some(c) = c else { return 0 };
    if c < T {
        return match c >> 4 {
            NETHER_BRICK => DEEPSLATE_LIKE,
            STONE => if meta(c) == 4 { CALCITE_LIKE } else { 0 },
            PURPUR_BLOCK => AMETHYST_LIKE,
            PURPUR_PILLAR => BUDDING_LIKE,
            GRAY_GLAZED => BASALT_LIKE | BLACKSTONE_HINT,
            NETHER_BRICK_FENCE | NETHER_WART | MOB_SPAWNER => FORTRESS_LIKE,
            // bastion bricks, bastion gold, soul soil
            RED_NETHER_BRICK | GOLD_BLOCK | DIRT | BRICK_STAIRS => BLACKSTONE_HINT,
            OBSIDIAN => OBSIDIAN_LIKE,
            COBBLE_WALL => DRIP_LIKE,
            GRASS => MOSS_LIKE,
            COBBLESTONE => COBBLE_LIKE,
            MAGMA => NETHER_STONE | BLACKSTONE_HINT, // basalt deltas
            NETHERRACK | SOUL_SAND | GRAVEL | LAVA | FLOWING_LAVA => NETHER_STONE,
            _ => 0,
        };
    }
    match c {
        TUFF => DEEPSLATE_LIKE | COBBLE_LIKE,
        DEEPSLATE => DEEPSLATE_LIKE,
        MOSS => MOSS_LIKE,
        CALCITE => CALCITE_LIKE,
        AMETHYST => AMETHYST_LIKE,
        BUDDING => BUDDING_LIKE | AMETHYST_LIKE,
        SMOOTH_BASALT => BASALT_LIKE,
        BASALT | BLACKSTONE | PBB_BRICKS | SOUL_SOIL => BLACKSTONE_HINT,
        POINTED_DRIP => DRIP_LIKE,
        _ => 0,
    }
}

fn is_leaves(c: Option<u16>) -> bool {
    matches!(c.map(id_of), Some(LEAVES | LEAVES2 | MANGROVE_LEAVES | PALE_LEAVES))
}

fn iron_like(c: Option<u16>) -> bool {
    match c {
        Some(c) if c < T => c >> 4 == IRON_ORE,
        Some(c) => matches!(c, COPPER | D_COPPER | D_IRON),
        None => false,
    }
}

/// Cheap position hash (picks one of several looks the same way every time), as in Java.
fn hash(x: i32, y: i32, z: i32) -> i32 {
    let h = x.wrapping_mul(73_856_093) ^ y.wrapping_mul(19_349_663) ^ z.wrapping_mul(83_492_791);
    (h ^ ((h as u32) >> 13) as i32) & 0x7fff_ffff
}

fn nibble(arr: &[u8], x: i32, y: i32, z: i32) -> u8 {
    let i = ((y << 8) | (z << 4) | x) as usize;
    let b = arr.get(i >> 1).copied().unwrap_or(0);
    if i & 1 == 0 { b & 15 } else { b >> 4 }
}

// ---- what a guess can see ------------------------------------------------------------

/// One chunk as a guess sees it. x/z are always 0-15 inside the chunk. A single block
/// change has no `grid`: its neighbours are unknown, as in the WASM edition when they are
/// not loaded.
struct View<'a> {
    grid: Option<&'a [u16]>,
    biomes: &'a [u8],
    heights: &'a [u16],
    sky: &'a [Option<&'a [u8]>],
    light: &'a [Option<&'a [u8]>],
    /// Light is known (a whole chunk). Guesses that need it are skipped otherwise.
    light_known: bool,
    ox: i32,
    oz: i32,
    cherry: bool,
    /// A fortress is in or next to this chunk.
    fortress: bool,
}

impl View<'_> {
    fn at(&self, x: i32, y: i32, z: i32) -> Option<u16> {
        if !(0..256).contains(&y) || !(0..16).contains(&x) || !(0..16).contains(&z) {
            return None;
        }
        self.grid.map(|g| g[((y << 8) | (z << 4) | x) as usize])
    }

    fn group_at(&self, x: i32, z: i32) -> u8 {
        self.biomes.get((((z & 15) << 4) | (x & 15)) as usize).map_or(0, |b| group(*b))
    }

    /// Deep under the ground (no sky light, well below the surface), so a cave.
    fn underground(&self, x: i32, y: i32, z: i32) -> bool {
        if !(0..UNDER_MAX_Y).contains(&y) {
            return false;
        }
        let h = self.heights.get((((z & 15) << 4) | (x & 15)) as usize).copied().unwrap_or(0);
        if i32::from(h) <= y + 6 {
            return false;
        }
        if !self.light_known {
            return true;
        }
        match self.sky.get(((y + 1) >> 4) as usize).copied().flatten() {
            Some(s) => nibble(s, x, (y + 1) & 15, z) == 0,
            None => false,
        }
    }

    /// Light from torches and other lamps at a spot (0 when unknown).
    fn block_light(&self, x: i32, y: i32, z: i32) -> u8 {
        if !(0..256).contains(&y) {
            return 0;
        }
        match self.light.get((y >> 4) as usize).copied().flatten() {
            Some(l) => nibble(l, x, y & 15, z),
            None => 0,
        }
    }

    fn hash_w(&self, x: i32, y: i32, z: i32) -> i32 {
        hash(self.ox + x, y, self.oz + z)
    }

    fn count_around(&self, x: i32, y: i32, z: i32, kinds: u32) -> usize {
        [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]
            .iter()
            .filter(|(dx, dy, dz)| kind_of(self.at(x + dx, y + dy, z + dz)) & kinds != 0)
            .count()
    }

    /// Blocks of these kinds in the cube of the given radius around a spot (not the spot).
    fn count_box(&self, x: i32, y: i32, z: i32, r: i32, kinds: u32) -> usize {
        let mut n = 0;
        for dy in -r..=r {
            for dz in -r..=r {
                for dx in -r..=r {
                    if (dx | dy | dz) != 0 && kind_of(self.at(x + dx, y + dy, z + dz)) & kinds != 0 {
                        n += 1;
                    }
                }
            }
        }
        n
    }

    /// Following the spike up and down through more wall look-alikes, one end is a
    /// dripstone block.
    fn on_dripstone(&self, x: i32, y: i32, z: i32) -> bool {
        for dir in [-1, 1] {
            let mut yy = y + dir;
            for _ in 0..16 {
                let Some(s) = self.at(x, yy, z) else { break };
                let id = id_of(s);
                if id == COBBLE_WALL || id == POINTED_DRIP {
                    yy += dir;
                    continue; // more of the same spike
                }
                if (id == STONE && meta(s) == 1) || id == DRIPSTONE {
                    return true;
                }
                break;
            }
        }
        false
    }

    /// Connected ore blocks (touching faces, edges or corners), counted up to 24.
    fn vein_size(&self, x: i32, y: i32, z: i32) -> usize {
        let max = 24;
        let mut q = vec![(x, y, z)];
        let mut head = 0;
        while head < q.len() && q.len() < max {
            let (bx, by, bz) = q[head];
            head += 1;
            for dx in -1..=1 {
                for dy in -1..=1 {
                    for dz in -1..=1 {
                        if q.len() >= max || (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        let p = (bx + dx, by + dy, bz + dz);
                        if !q.contains(&p) && iron_like(self.at(p.0, p.1, p.2)) {
                            q.push(p);
                        }
                    }
                }
            }
        }
        q.len()
    }
}

// ---- the guesses ---------------------------------------------------------------------

/// Whether blocks of this kind can be a look-alike worth guessing here.
fn is_candidate(c: u16, dim: i32, base_y: i32, groups: u8, sec_drip: bool) -> bool {
    if c >= T {
        return false;
    }
    let id = c >> 4;
    let m = meta(c);
    if dim == 0 {
        let low = base_y < UNDER_MAX_Y;
        match id {
            LOG2 => return groups & (B_SWAMP | B_FLOWER) != 0,
            LEAVES2 | DIRT if groups & B_SWAMP != 0 => return true,
            LEAVES => return groups & (B_FLOWER | B_PALE) != 0,
            LOG => return groups & (B_PALE | B_SWAMP) != 0,
            GRASS | COBBLE_WALL => return low,
            CARPET => return low || groups & B_PALE != 0,
            TALLGRASS => return low || groups & (B_DRY | B_SWAMP | B_PALE | B_FLOWER) != 0,
            RED_FLOWER => return groups & (B_PLAINS | B_FLOWER | B_FOREST | B_PALE) != 0,
            STONE if low && sec_drip && m == 1 => return true, // granite next to pointed dripstone
            _ => {}
        }
        if base_y <= DEEP_MAX_Y
            && matches!(
                id,
                NETHER_BRICK | COBBLESTONE | COAL_ORE | IRON_ORE | GOLD_ORE | DIAMOND_ORE | EMERALD_ORE
                    | LAPIS_ORE | REDSTONE_ORE | LIT_REDSTONE_ORE
            )
        {
            return true;
        }
        if id == IRON_ORE && base_y <= COPPER_MAX_Y {
            return true;
        }
        return base_y < GEODE_MAX_Y
            && (id == PURPUR_BLOCK || id == PURPUR_PILLAR || id == GRAY_GLAZED || (id == STONE && m == 4));
    }
    match id {
        GRAY_GLAZED | LOG2 | MYCELIUM | LIT_PUMPKIN | RED_NETHER_BRICK | GOLD_ORE | NETHER_BRICK | OBSIDIAN
        | NETHER_WART | BROWN_MUSHROOM | RED_MUSHROOM | VINE | NETHER_BRICK_STAIRS | BRICK_STAIRS => true,
        STONE_SLAB | DOUBLE_STONE_SLAB => matches!(m & 7, 4 | 6), // brick or nether brick slabs
        TALLGRASS => m == 1,
        WOOL => m == 9,
        DIRT => m == 0,
        _ => false,
    }
}

/// The modern block a look-alike really is, or `None` to keep it.
fn guess(dim: i32, st: u16, v: &View, x: i32, y: i32, z: i32, nylium: u8) -> Option<u16> {
    let id = st >> 4;
    let m = meta(st);
    if dim == 0 {
        if let Some(g) = surface_guess(id, st, v, x, y, z) {
            return Some(g);
        }
        if (0..=DEEP_MAX_Y).contains(&y) {
            match id {
                NETHER_BRICK => return Some(DEEPSLATE),
                COAL_ORE => return Some(D_COAL),
                IRON_ORE => return Some(if v.vein_size(x, y, z) >= COPPER_VEIN { D_COPPER } else { D_IRON }),
                GOLD_ORE => return Some(D_GOLD),
                DIAMOND_ORE => return Some(D_DIAMOND),
                EMERALD_ORE => return Some(D_EMERALD),
                LAPIS_ORE => return Some(D_LAPIS),
                REDSTONE_ORE | LIT_REDSTONE_ORE => return Some(D_REDSTONE),
                // tuff blobs sit in deepslate (a lone cobblestone there is someone's mining)
                COBBLESTONE => {
                    return (v.count_around(x, y, z, DEEPSLATE_LIKE) >= 2
                        && v.count_around(x, y, z, COBBLE_LIKE) >= 1)
                        .then_some(TUFF);
                }
                _ => {}
            }
        }
        if id == IRON_ORE && y > DEEP_MAX_Y && y <= COPPER_MAX_Y {
            return (v.vein_size(x, y, z) >= COPPER_VEIN).then_some(COPPER);
        }
        if (0..GEODE_MAX_Y).contains(&y) {
            // amethyst geodes: amethyst inside, calcite around it, smooth basalt outside
            match id {
                PURPUR_BLOCK => {
                    return (v.count_around(x, y, z, CALCITE_LIKE | BUDDING_LIKE) >= 1).then_some(AMETHYST);
                }
                PURPUR_PILLAR => return (v.count_around(x, y, z, AMETHYST_LIKE) >= 1).then_some(BUDDING),
                STONE => {
                    return (m == 4 && v.count_around(x, y, z, AMETHYST_LIKE | BASALT_LIKE) >= 1)
                        .then_some(CALCITE);
                }
                GRAY_GLAZED => return (v.count_around(x, y, z, CALCITE_LIKE) >= 1).then_some(SMOOTH_BASALT),
                _ => {}
            }
        }
        return None;
    }
    // the nether
    match id {
        GRAY_GLAZED => Some(BASALT), // basalt deltas and pillars
        LOG2 => {
            if m & 3 > 1 {
                return None;
            }
            let warped = m & 3 == 1;
            Some(match (m & 12 == 12, warped) {
                (true, true) => WARPED_HYPHAE,
                (true, false) => CRIMSON_HYPHAE,
                (false, true) => WARPED_STEM,
                (false, false) => CRIMSON_STEM,
            })
        }
        MYCELIUM => Some(if nylium == 2 { WARPED_NYLIUM } else { CRIMSON_NYLIUM }),
        WOOL => (m == 9).then_some(WARPED_WART),
        LIT_PUMPKIN => Some(SHROOMLIGHT),
        DIRT => (m == 0).then_some(SOUL_SOIL),
        RED_NETHER_BRICK => Some(PBB_BRICKS), // bastions
        NETHER_BRICK => nether_brick_guess(v, x, y, z),
        // blackstone stairs and slabs arrive as nether brick ones (fortresses have those
        // too), polished blackstone brick ones as brick ones (never in the nether)
        NETHER_BRICK_STAIRS => (!v.fortress).then_some(BS_STAIRS),
        BRICK_STAIRS => Some(PBB_STAIRS),
        STONE_SLAB | DOUBLE_STONE_SLAB => match m & 7 {
            6 => (!v.fortress).then_some(BS_SLAB),
            4 => Some(PBB_SLAB),
            _ => None,
        },
        GOLD_ORE => Some(NETHER_GOLD),
        NETHER_WART => {
            // roots grow on nylium (nether wart grows on soul sand)
            let below = v.at(x, y - 1, z).map(id_of);
            match below {
                Some(MYCELIUM) => Some(if nylium == 2 { WARPED_ROOTS } else { CRIMSON_ROOTS }),
                Some(CRIMSON_NYLIUM) => Some(CRIMSON_ROOTS),
                Some(WARPED_NYLIUM) => Some(WARPED_ROOTS),
                _ => None,
            }
        }
        TALLGRASS => (m == 1).then_some(SPROUTS),
        BROWN_MUSHROOM | RED_MUSHROOM => {
            let below = v.at(x, y - 1, z).map(id_of);
            matches!(below, Some(MYCELIUM | CRIMSON_NYLIUM | WARPED_NYLIUM))
                .then_some(if id == BROWN_MUSHROOM { WARPED_FUNGUS } else { CRIMSON_FUNGUS })
        }
        VINE => Some(if nylium == 2 { TWISTING } else { WEEPING }),
        // ancient debris: a lone block in netherrack (portals and bastions have obsidian
        // next to obsidian)
        OBSIDIAN => (v.count_around(x, y, z, OBSIDIAN_LIKE) == 0 && v.count_around(x, y, z, NETHER_STONE) >= 4)
            .then_some(DEBRIS),
        _ => None,
    }
}

fn nether_brick_guess(v: &View, x: i32, y: i32, z: i32) -> Option<u16> {
    if !v.fortress {
        // no fortress in or next to this chunk: nether bricks are blackstone
        return Some(BLACKSTONE);
    }
    // a fortress is near. Its walls sit in netherrack too, so it takes basalt, magma, soul
    // soil or bastion blocks touching it, and no fence, wart or spawner close by
    let hint = v.count_box(x, y, z, 1, BLACKSTONE_HINT);
    if hint == 0 {
        return None;
    }
    let natural = v.count_box(x, y, z, 1, NETHER_STONE);
    if hint + natural < 3 {
        return None;
    }
    (v.count_box(x, y, z, 2, FORTRESS_LIKE) == 0).then_some(BLACKSTONE)
}

/// Overworld plants, trees and cave blocks, from where they grow.
fn surface_guess(id: u16, st: u16, v: &View, x: i32, y: i32, z: i32) -> Option<u16> {
    let m = meta(st);
    match id {
        LOG2 => {
            if m & 3 != 0 {
                return None; // acacia only
            }
            let g = v.group_at(x, z);
            let bark = m & 12 == 12;
            if g & B_SWAMP != 0 {
                return Some(if bark { MANGROVE_WOOD } else { MANGROVE_LOG });
            }
            if g & B_FLOWER != 0 {
                return Some(if bark { CHERRY_WOOD } else { CHERRY_LOG });
            }
            None
        }
        LEAVES2 => (m & 3 == 0 && v.group_at(x, z) & B_SWAMP != 0).then_some(MANGROVE_LEAVES),
        LEAVES => {
            let g = v.group_at(x, z);
            match m & 3 {
                0 if g & B_FLOWER != 0 && v.cherry => Some(CHERRY_LEAVES),
                2 if g & B_PALE != 0 => Some(PALE_LEAVES),
                _ => None,
            }
        }
        LOG => {
            let g = v.group_at(x, z);
            if m & 3 == 2 && g & B_PALE != 0 {
                return Some(if m & 12 == 12 { PALE_WOOD } else { PALE_LOG });
            }
            (m == 12 && g & B_SWAMP != 0).then_some(MANGROVE_ROOTS) // oak "all bark" in a swamp
        }
        DIRT => (m == 1 && v.group_at(x, z) & B_SWAMP != 0).then_some(MUD),
        GRASS => {
            // lush caves: grass in the dark is moss; grass lit by torches (a tunnel or
            // base) stays grass unless it's part of a moss patch
            if !v.underground(x, y, z) || !v.light_known {
                return None;
            }
            (v.block_light(x, y + 1, z) < 4 || v.count_around(x, y, z, MOSS_LIKE) >= 2).then_some(MOSS)
        }
        CARPET => {
            if m != 13 {
                return None; // green
            }
            if v.underground(x, y, z) {
                // on moss, or in the dark (not a carpet in someone's base)
                let mossy = v.light_known
                    && (v.block_light(x, y, z) < 4 || kind_of(v.at(x, y - 1, z)) & MOSS_LIKE != 0);
                return mossy.then_some(MOSS_CARPET);
            }
            (v.group_at(x, z) & B_PALE != 0).then_some(PALE_MOSS_CARPET)
        }
        TALLGRASS => {
            if m == 1 {
                if v.group_at(x, z) & B_DRY == 0 {
                    return None;
                }
                return Some(if v.hash_w(x, y, z) & 3 == 0 { TALL_DRY } else { SHORT_DRY });
            }
            if m != 2 {
                return None;
            }
            // a fern where ferns never grow
            if y < GEODE_MAX_Y && v.count_around(x, y, z, AMETHYST_LIKE | BUDDING_LIKE) >= 1 {
                return Some(match v.hash_w(x, y, z) & 3 {
                    0 => BUD_SMALL,
                    1 => BUD_MEDIUM,
                    2 => BUD_LARGE,
                    _ => CLUSTER,
                });
            }
            let above = v.at(x, y + 1, z);
            let below = v.at(x, y - 1, z);
            if below == Some(0) && v.underground(x, y, z) {
                // hanging from a cave roof
                return Some(if above.map(id_of) == Some(DIRT) { HANGING_ROOTS } else { CAVE_VINES });
            }
            let g = v.group_at(x, z);
            if g & B_SWAMP != 0 && is_leaves(above) {
                return Some(PROPAGULE);
            }
            if g & B_PALE != 0 && is_leaves(above) {
                return Some(PALE_MOSS);
            }
            (g & B_FLOWER != 0 && v.cherry).then_some(PINK_PETALS)
        }
        RED_FLOWER => {
            let g = v.group_at(x, z);
            match m {
                // blue orchid only grows in swamps
                1 if g & B_PALE != 0 => Some(CLOSED_EYE),
                1 => (g & (B_PLAINS | B_FLOWER | B_FOREST) != 0).then_some(CORNFLOWER),
                6 => (g & B_FOREST != 0).then_some(LILY), // white tulip in a forest
                4 => (g & B_PALE != 0).then_some(OPEN_EYE),
                _ => None,
            }
        }
        // every modern wall arrives as a cobblestone wall, so only call it pointed
        // dripstone when it grows like dripstone: hanging from or standing on a dripstone
        // block (which arrives as granite)
        COBBLE_WALL => (v.underground(x, y, z) && v.on_dripstone(x, y, z)).then_some(POINTED_DRIP),
        // spikes hang below or stand on top of it (a wall beside granite is a build)
        STONE => (m == 1
            && y < UNDER_MAX_Y
            && (kind_of(v.at(x, y + 1, z)) & DRIP_LIKE != 0 || kind_of(v.at(x, y - 1, z)) & DRIP_LIKE != 0))
            .then_some(DRIPSTONE),
        _ => None,
    }
}

// ---- 1.14 walls ------------------------------------------------------------------------
// Every modern wall arrives as a cobblestone wall (mossy stone brick as a mossy
// cobblestone wall). People build a wall from the same stone as what it stands on or
// joins, so a run of connected walls takes the material of the blocks under and beside
// it. Any cobblestone touching the run means it really is a cobblestone wall.

const WALLS: usize = 15;
const W_STONE_BRICK: i32 = 0;
const W_MOSSY: i32 = 1;
const W_COBBLE: i32 = -2;

/// Which wall a neighbouring block suggests: a wall index, `W_COBBLE`, or -1 for none.
fn wall_mat(c: Option<u16>) -> i32 {
    let Some(c) = c else { return -1 };
    if c >= T {
        return match c {
            BLACKSTONE | BS_STAIRS | BS_SLAB => 12,
            POLISHED_BS => 13,
            PBB_BRICKS | PBB_STAIRS | PBB_SLAB => 14,
            w if (WALL0..WALL0 + WALLS as u16).contains(&w) => i32::from(w - WALL0),
            _ => -1,
        };
    }
    let m = meta(c);
    match c >> 4 {
        98 => if m == 1 { W_MOSSY } else { W_STONE_BRICK }, // stone bricks
        109 => W_STONE_BRICK,                                 // stone brick stairs
        45 | 108 => 2,                                        // bricks, brick stairs
        1 => match m {
            5 | 6 => 3,
            1 | 2 => 4,
            3 | 4 => 5,
            _ => -1,
        },
        24 | 128 => 6,
        179 | 180 | 181 | 182 => 7,
        168 => 8,
        112 | 114 => 9,
        215 => 10,
        206 => 11,
        43 | 44 => match m & 7 {
            1 => 6,
            3 => W_COBBLE,
            4 => 2,
            5 => W_STONE_BRICK,
            6 => 9,
            _ => -1,
        },
        4 | 48 | 67 => W_COBBLE, // cobblestone, mossy cobblestone, cobblestone stairs
        _ => -1,
    }
}

/// Adds one wall block's neighbours to the votes; false when cobblestone touches it.
fn vote_around(v: &View, x: i32, y: i32, z: i32, votes: &mut [i32; WALLS]) -> bool {
    for d in 0..6 {
        let (dx, dy, dz) = match d {
            0 => (1, 0, 0),
            1 => (-1, 0, 0),
            2 => (0, 1, 0),
            3 => (0, -1, 0),
            4 => (0, 0, 1),
            _ => (0, 0, -1),
        };
        let n = v.at(x + dx, y + dy, z + dz);
        if n.is_none_or(|n| n < T && n >> 4 == COBBLE_WALL) {
            continue;
        }
        let m = wall_mat(n);
        if m == W_COBBLE {
            return false;
        }
        if m >= 0 {
            votes[m as usize] += if d == 3 { 2 } else { 1 }; // what it stands on counts most
        }
    }
    true
}

/// The wall the votes agree on (needs the block below or two touching blocks), or -1.
fn wall_winner(votes: &[i32; WALLS], look_meta: u16) -> i32 {
    let (mut best, mut best_v) = (-1, 0);
    for i in 0..WALLS as i32 {
        if i == W_MOSSY {
            continue; // mossy and plain stone bricks count together
        }
        let v = if i == W_STONE_BRICK {
            votes[W_STONE_BRICK as usize] + votes[W_MOSSY as usize]
        } else {
            votes[i as usize]
        };
        if v > best_v {
            best = i;
            best_v = v;
        }
    }
    if best_v < 2 {
        return -1;
    }
    let bricks = best == W_STONE_BRICK || best == W_MOSSY;
    if look_meta == 1 {
        // a mossy cobblestone wall look-alike is only ever the mossy stone brick wall
        return if bricks { W_MOSSY } else { -1 };
    }
    if bricks { W_STONE_BRICK } else { best }
}

// ---- remembered things -----------------------------------------------------------------

#[derive(Clone, Copy)]
struct Col {
    biomes: [u8; 256],
    heights: [u16; 256],
    cherry: bool,
    fortress: bool,
}

#[derive(Default)]
struct State {
    /// Blocks you placed: `Some` = a modern block (its real look), `None` = an ordinary one.
    placed: HashMap<(i32, i32, i32), Option<BlockState>>,
    placed_chunks: HashMap<(i32, i32), Vec<(i32, i32, i32)>>,
    /// Per loaded chunk: what single block changes need to know.
    cols: HashMap<(i32, i32), Box<Col>>,
    /// The server sends the real modern blocks itself (TuffX+ or a TC|ViaBlocks plugin).
    plugin: bool,
    server: String,
    dim: i32,
    smith_key: String,
    smith: Option<HashSet<i64>>,
    /// The block you just right-clicked, and when.
    click: Option<((i32, i32, i32), crate::platform::time::Instant)>,
}

static STATE: Mutex<Option<State>> = Mutex::new(None);

fn with<R>(f: impl FnOnce(&mut State) -> R) -> R {
    f(STATE.lock().unwrap().get_or_insert_with(State::default))
}

/// The server you're connecting to (smithing tables are remembered per server).
pub(crate) fn set_server(address: &str) {
    with(|s| s.server = address.trim().to_lowercase());
}

/// A new world (join or respawn): positions from the old one no longer count.
pub(super) fn new_world(dim: i32, join: bool) {
    with(|s| {
        s.placed.clear();
        s.placed_chunks.clear();
        s.cols.clear();
        s.dim = dim;
        s.click = None;
        if join {
            s.plugin = false;
        }
    });
}

/// The server announced it sends modern blocks itself: no guessing then.
pub(super) fn set_plugin() {
    with(|s| s.plugin = true);
}

pub(super) fn unload(cx: i32, cz: i32) {
    with(|s| {
        s.cols.remove(&(cx, cz));
    });
}

/// On, and the server isn't already sending the real modern blocks.
fn active(s: &State) -> bool {
    let m = crate::tc_mods::get();
    m.via && m.via_guess && !s.plugin
}

fn old_items() -> &'static HashSet<ItemKind> {
    static OLD: OnceLock<HashSet<ItemKind>> = OnceLock::new();
    OLD.get_or_init(super::items::old_kinds)
}

/// You placed a block from this item at `pos`.
pub(super) fn placed(pos: (i32, i32, i32), item: ItemKind) {
    let m = crate::tc_mods::get();
    if !m.via || !m.via_guess || item == ItemKind::Air {
        return;
    }
    let full = item.to_string();
    let name = full.strip_prefix("minecraft:").unwrap_or(&full);
    let Some(block) = super::chunk::block_default(name) else { return };
    let modern = (!old_items().contains(&item)).then_some(block);
    with(|s| {
        if s.placed.len() >= MAX_PLACED {
            s.placed.clear(); // keep it small (older spots fall back to guessing)
            s.placed_chunks.clear();
        }
        if s.placed.insert(pos, modern).is_none() {
            s.placed_chunks.entry((pos.0 >> 4, pos.2 >> 4)).or_default().push(pos);
        }
    });
}

// ---- smithing tables -------------------------------------------------------------------
// ViaBackwards sends smithing tables as crafting tables. Once one opens the smithing menu
// its spot is remembered for that server and dimension, kept between visits (the same
// `tcsmith` list as the WASM edition).

fn pos_long(p: (i32, i32, i32)) -> i64 {
    ((i64::from(p.0) & 0x3FF_FFFF) << 38) | ((i64::from(p.1) & 0xFFF) << 26) | (i64::from(p.2) & 0x3FF_FFFF)
}

fn smith_server(s: &State) -> String {
    let srv = if s.server.is_empty() { "local" } else { &s.server };
    format!("{srv}|{}", s.dim)
}

#[cfg(target_arch = "wasm32")]
fn smith_lines() -> String {
    use base64::Engine as _;
    web_sys::window()
        .and_then(|w| w.local_storage().ok().flatten())
        .and_then(|st| st.get_item("_eaglercraft_1.12.tcsmith").ok().flatten())
        .and_then(|raw| base64::engine::general_purpose::STANDARD.decode(raw.trim()).ok())
        .and_then(|b| String::from_utf8(b).ok())
        .unwrap_or_default()
}

#[cfg(not(target_arch = "wasm32"))]
fn smith_lines() -> String {
    String::new()
}

fn smith_set(s: &mut State) -> &mut HashSet<i64> {
    let key = smith_server(s);
    if s.smith.is_none() || s.smith_key != key {
        let mut set = HashSet::new();
        for line in smith_lines().lines() {
            if let Some((k, v)) = line.rsplit_once('|')
                && k == key
                && let Ok(v) = v.parse()
            {
                set.insert(v);
            }
        }
        s.smith_key = key;
        s.smith = Some(set);
    }
    s.smith.as_mut().unwrap()
}

fn save_smith(s: &mut State) {
    #[cfg(target_arch = "wasm32")]
    {
        use base64::Engine as _;
        let key = smith_server(s);
        let mut out = String::new();
        let mut kept = 0;
        for l in smith_set(s).iter().take(MAX_SMITH) {
            out.push_str(&format!("{key}|{l}\n"));
            kept += 1;
        }
        for line in smith_lines().lines() {
            if kept >= MAX_SMITH {
                break;
            }
            if line.rsplit_once('|').is_some_and(|(k, _)| k != key) {
                out.push_str(line);
                out.push('\n'); // other servers stay as they are
                kept += 1;
            }
        }
        if let Some(st) = web_sys::window().and_then(|w| w.local_storage().ok().flatten()) {
            let _ = st.set_item(
                "_eaglercraft_1.12.tcsmith",
                &base64::engine::general_purpose::STANDARD.encode(out.as_bytes()),
            );
        }
    }
    #[cfg(not(target_arch = "wasm32"))]
    let _ = s;
}

/// You right-clicked a block (not sneaking): it may open the hidden smithing menu.
pub(super) fn clicked(pos: (i32, i32, i32)) {
    with(|s| s.click = Some((pos, crate::platform::time::Instant::now())));
}

/// The server opened an ordinary menu. A crafting table after the click: not a smithing table.
/// Returns a spot to show as a crafting table again.
pub(super) fn opened(crafting: bool) -> Option<(i32, i32, i32)> {
    with(|s| {
        let (pos, _) = s.click.take()?;
        if !crafting || !active(s) {
            return None;
        }
        let removed = smith_set(s).remove(&pos_long(pos));
        if removed {
            save_smith(s);
        }
        removed.then_some(pos)
    })
}

/// Menu contents arrived for a menu that never opened. Right after clicking a block, with
/// 2 or 3 inputs, it is the smithing menu: returns the spot clicked (now remembered).
pub(super) fn hidden_menu(inputs: usize) -> Option<(i32, i32, i32)> {
    with(|s| {
        let (pos, at) = s.click?;
        if at.elapsed().as_millis() > 3000 {
            s.click = None;
            return None;
        }
        if inputs != 2 && inputs != 3 {
            return None;
        }
        s.click = None;
        if !crate::tc_mods::get().via {
            return None;
        }
        if smith_set(s).insert(pos_long(pos)) {
            save_smith(s);
        }
        Some(pos)
    })
}

pub(super) fn smithing_state() -> Option<BlockState> {
    tag_state(SMITHING)
}

// ---- chunks ----------------------------------------------------------------------------

/// What a freshly loaded chunk needs from the translator.
pub(super) struct ChunkIn<'a> {
    pub(super) cx: i32,
    pub(super) cz: i32,
    pub(super) dim: i32,
    /// The chunk's 1.12 codes (65536, y-z-x order). Changed in place by the guesses.
    pub(super) old: &'a mut [u16],
    /// `None` for a partial chunk (the chunk's biomes from before are used).
    pub(super) biomes: Option<&'a [u8]>,
    pub(super) heights: [u16; 256],
    pub(super) sky: &'a [Option<&'a [u8]>],
    pub(super) light: &'a [Option<&'a [u8]>],
}

/// Runs the guesses over a chunk. Returns each changed spot with the modern block's
/// default state (the caller keeps the look-alike's direction and shape).
pub(super) fn chunk(c: ChunkIn) -> Vec<(usize, BlockState)> {
    let mut guard = STATE.lock().unwrap();
    let s = guard.get_or_insert_with(State::default);
    let mut out = Vec::new();
    let old = c.old;

    // what single block changes in this chunk will need
    let cherry = old.iter().any(|&b| b < T && b >> 4 == LOG2 && meta(b) & 3 == 0);
    let fortress_here = old
        .iter()
        .any(|&b| b < T && matches!(b >> 4, NETHER_BRICK_FENCE | NETHER_WART | MOB_SPAWNER));
    let col = match c.biomes {
        Some(b) if b.len() >= 256 => {
            let mut biomes = [0u8; 256];
            biomes.copy_from_slice(&b[..256]);
            Some(Col { biomes, heights: c.heights, cherry, fortress: fortress_here })
        }
        _ => s.cols.get(&(c.cx, c.cz)).map(|col| Col { heights: c.heights, cherry, fortress: fortress_here, ..**col }),
    };
    let Some(col) = col else { return out };
    s.cols.insert((c.cx, c.cz), Box::new(col));
    if !active(s) {
        return out;
    }

    // smithing tables remembered in this chunk
    let set = smith_set(s);
    if !set.is_empty() {
        for &l in set.iter() {
            let (x, y, z) = ((l >> 38) as i32, ((l << 26) >> 52) as i32, ((l << 38) >> 38) as i32);
            if x >> 4 != c.cx || z >> 4 != c.cz || !(0..256).contains(&y) {
                continue;
            }
            let i = ((y << 8) | ((z & 15) << 4) | (x & 15)) as usize;
            if old[i] < T && old[i] >> 4 == CRAFTING_TABLE {
                old[i] = SMITHING;
            }
        }
    }
    if c.dim != 0 && c.dim != -1 {
        return collect(old, &HashMap::new(), out); // the End: purpur there is real (end cities)
    }

    // blocks you placed: the guesses leave them alone, modern ones show what they are
    let mut skip: HashSet<usize> = HashSet::new();
    let mut mine: HashMap<usize, BlockState> = HashMap::new();
    if let Some(spots) = s.placed_chunks.get(&(c.cx, c.cz)) {
        for p in spots {
            let Some(real) = s.placed.get(p) else { continue }; // forgotten (broken)
            if !(0..256).contains(&p.1) {
                continue;
            }
            let i = ((p.1 << 8) | ((p.2 & 15) << 4) | (p.0 & 15)) as usize;
            skip.insert(i);
            if let Some(real) = real
                && old[i] != 0
                && old[i] < T
            {
                old[i] = PLACED;
                mine.insert(i, *real);
            }
        }
    }

    let fortress = fortress_here
        || (-1..=1).any(|dx| {
            (-1..=1).any(|dz| (dx != 0 || dz != 0) && s.cols.get(&(c.cx + dx, c.cz + dz)).is_some_and(|n| n.fortress))
        });
    let groups = if c.dim == 0 { col.biomes.iter().fold(0, |m, b| m | group(*b)) } else { 0 };
    let high_trees = groups & (B_SWAMP | B_FLOWER | B_PALE | B_DRY | B_FOREST | B_PLAINS) != 0;
    let mut nylium = 0u8;
    let mut iron: Vec<usize> = Vec::new();
    let mut seen = vec![0u64; 1024];
    let mut want = vec![0u64; 1024];
    let bit = |set: &[u64], c: u16| set[usize::from(c) >> 6] >> (c & 63) & 1 != 0;

    for si in 0..16usize {
        let base_y = si as i32 * 16;
        if c.dim == 0 && base_y > COPPER_MAX_Y && !high_trees {
            break; // nothing to guess higher up in the overworld
        }
        let sec = si * 4096..(si + 1) * 4096;
        // the section's palette
        let mut palette: Vec<u16> = Vec::new();
        for &b in &old[sec.clone()] {
            if !bit(&seen, b) {
                seen[usize::from(b) >> 6] |= 1 << (b & 63);
                palette.push(b);
            }
        }
        for &b in &palette {
            seen[usize::from(b) >> 6] = 0;
        }
        if palette == [0] || palette.len() > 256 {
            continue; // empty, or 256+ kinds of block: a build, nothing to guess
        }
        // granite is only dripstone where pointed dripstone (a wall look-alike) is near
        let sec_drip = c.dim == 0
            && base_y < UNDER_MAX_Y
            && palette.iter().any(|&b| id_of(b) == COBBLE_WALL || b == POINTED_DRIP);
        let mut any = false;
        for &b in &palette {
            if is_candidate(b, c.dim, base_y, groups, sec_drip) {
                want[usize::from(b) >> 6] |= 1 << (b & 63);
                any = true;
            }
        }
        if !any {
            continue;
        }
        if c.dim == -1 && nylium == 0 {
            nylium = nylium_hint(old);
        }
        let found: Vec<usize> = sec.clone().filter(|&i| bit(&want, old[i])).collect();
        for &b in &palette {
            want[usize::from(b) >> 6] = 0;
        }
        for key in found {
            if skip.contains(&key) {
                continue; // you placed this block yourself
            }
            let st = old[key];
            let (x, y, z) = ((key & 15) as i32, (key >> 8) as i32, ((key >> 4) & 15) as i32);
            if c.dim == 0 && st >> 4 == IRON_ORE {
                // copper or iron: decided per vein below, once every ore block is known
                if iron.len() < MAX_IRON {
                    iron.push(key);
                }
                continue;
            }
            let g = {
                let v = View {
                    grid: Some(&*old),
                    biomes: &col.biomes,
                    heights: &col.heights,
                    sky: c.sky,
                    light: c.light,
                    light_known: true,
                    ox: c.cx * 16,
                    oz: c.cz * 16,
                    cherry,
                    fortress,
                };
                guess(c.dim, st, &v, x, y, z, nylium)
            };
            if let Some(g) = g {
                old[key] = g;
            }
        }
    }
    if !iron.is_empty() {
        decide_veins(old, &iron);
    }
    wall_pass(old, &skip, &col);
    out = collect(old, &mine, out);
    out
}

fn collect(old: &[u16], mine: &HashMap<usize, BlockState>, mut out: Vec<(usize, BlockState)>) -> Vec<(usize, BlockState)> {
    for (i, &c) in old.iter().enumerate() {
        if c < T {
            continue;
        }
        let state = if c == PLACED { mine.get(&i).copied() } else { tag_state(c) };
        if let Some(state) = state {
            out.push((i, state));
        }
    }
    out
}

/// 1 = crimson (acacia look-alike stems), 2 = warped (dark oak look-alike).
fn nylium_hint(old: &[u16]) -> u8 {
    let (mut crimson, mut warped) = (0, 0);
    for si in 0..16 {
        // like the WASM edition: each kind of stem counts once per section
        let mut metas = 0u16;
        for &b in &old[si * 4096..(si + 1) * 4096] {
            if b < T && b >> 4 == LOG2 {
                metas |= 1 << meta(b);
            }
        }
        for m in 0..16 {
            if metas >> m & 1 != 0 {
                if m & 3 == 1 { warped += 1 } else { crimson += 1 }
            }
        }
    }
    if warped > crimson { 2 } else { 1 }
}

/// Labels each connected group of iron look-alikes in the chunk once: 10+ is copper.
fn decide_veins(old: &mut [u16], iron: &[usize]) {
    let mut grid = vec![0u8; 65536];
    for &k in iron {
        grid[k] = 1;
    }
    for &start in iron {
        if grid[start] != 1 {
            continue; // already part of a counted vein
        }
        grid[start] = 2;
        let mut queue = vec![start];
        let mut head = 0;
        while head < queue.len() {
            let p = queue[head];
            head += 1;
            let (px, pz, py) = ((p & 15) as i32, ((p >> 4) & 15) as i32, (p >> 8) as i32);
            for dy in -1..=1 {
                let ny = py + dy;
                if !(0..256).contains(&ny) {
                    continue;
                }
                for dz in -1..=1 {
                    let nz = pz + dz;
                    if !(0..16).contains(&nz) {
                        continue;
                    }
                    for dx in -1..=1 {
                        let nx = px + dx;
                        if !(0..16).contains(&nx) {
                            continue;
                        }
                        let q = ((ny << 8) | (nz << 4) | nx) as usize;
                        if grid[q] == 1 {
                            grid[q] = 2;
                            queue.push(q);
                        }
                    }
                }
            }
        }
        let copper = queue.len() >= COPPER_VEIN;
        for p in queue {
            let deep = (p >> 8) as i32 <= DEEP_MAX_Y;
            let want = match (copper, deep) {
                (true, true) => Some(D_COPPER),
                (true, false) => Some(COPPER),
                (false, true) => Some(D_IRON),
                (false, false) => None,
            };
            if let Some(want) = want {
                old[p] = want;
            }
        }
    }
}

/// Every cobblestone wall look-alike in the chunk, one connected run at a time.
fn wall_pass(old: &mut [u16], skip: &HashSet<usize>, col: &Col) {
    let is_wall = |b: u16| b < T && b >> 4 == COBBLE_WALL;
    if !old.iter().any(|&b| is_wall(b)) {
        return;
    }
    let mut seen = vec![false; 65536];
    for start in 0..old.len() {
        if seen[start] || !is_wall(old[start]) {
            continue;
        }
        let look = meta(old[start]);
        let mut run = vec![start];
        seen[start] = true;
        let mut votes = [0i32; WALLS];
        let mut cobble = false;
        {
            let v = View {
                grid: Some(&*old),
                biomes: &col.biomes,
                heights: &col.heights,
                sky: &[],
                light: &[],
                light_known: false,
                ox: 0,
                oz: 0,
                cherry: false,
                fortress: false,
            };
            let mut head = 0;
            while head < run.len() {
                let key = run[head];
                head += 1;
                let (x, y, z) = ((key & 15) as i32, (key >> 8) as i32, ((key >> 4) & 15) as i32);
                if skip.contains(&key) {
                    cobble = true; // a wall you placed yourself is part of this: leave the run
                }
                if !vote_around(&v, x, y, z, &mut votes) {
                    cobble = true;
                }
                for (dx, dy, dz) in [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)] {
                    let (nx, ny, nz) = (x + dx, y + dy, z + dz);
                    if !(0..16).contains(&nx) || !(0..16).contains(&nz) || !(0..256).contains(&ny) {
                        continue;
                    }
                    let nk = ((ny << 8) | (nz << 4) | nx) as usize;
                    if !seen[nk] && is_wall(old[nk]) && meta(old[nk]) == look {
                        seen[nk] = true;
                        run.push(nk);
                    }
                }
            }
        }
        if cobble {
            continue;
        }
        let win = wall_winner(&votes, look);
        if win >= 0 {
            for k in run {
                old[k] = WALL0 + win as u16;
            }
        }
    }
}

// ---- single block changes --------------------------------------------------------------

/// A block change from the server. Returns the modern block to show instead (the caller
/// keeps the look-alike's direction and shape), or `None` to keep it.
pub(super) fn block(pos: (i32, i32, i32), global: u32, dim: i32) -> Option<BlockState> {
    let st = u16::try_from(global).ok().filter(|g| *g < T)?;
    let mut guard = STATE.lock().unwrap();
    let s = guard.get_or_insert_with(State::default);
    if !active(s) {
        return None;
    }
    if let Some(mine) = s.placed.get(&pos).copied() {
        if st == 0 {
            s.placed.remove(&pos); // broken, or the placement was refused
            return None;
        }
        return mine; // a modern block you placed keeps its real look; an ordinary one is never guessed
    }
    // a remembered smithing table
    {
        let key = pos_long(pos);
        if smith_set(s).contains(&key) {
            if st >> 4 == CRAFTING_TABLE {
                return tag_state(SMITHING);
            }
            smith_set(s).remove(&key); // broken or replaced
            save_smith(s);
        }
    }
    if dim != 0 && dim != -1 {
        return None;
    }
    let (cx, cz) = (pos.0 >> 4, pos.2 >> 4);
    let col = s.cols.get(&(cx, cz))?;
    let fortress = dim == -1
        && (-1..=1).any(|dx| (-1..=1).any(|dz| s.cols.get(&(cx + dx, cz + dz)).is_some_and(|n| n.fortress)));
    let v = View {
        grid: None,
        biomes: &col.biomes,
        heights: &col.heights,
        sky: &[],
        light: &[],
        light_known: false,
        ox: cx * 16,
        oz: cz * 16,
        cherry: col.cherry,
        fortress,
    };
    // walls on their own have no neighbours to vote, so they stay
    let g = guess(dim, st, &v, pos.0 & 15, pos.1, pos.2 & 15, 1)?;
    tag_state(g)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hash_matches_java() {
        // Java: (h ^ (h >>> 13)) & 0x7fffffff with int overflow
        assert_eq!(hash(0, 0, 0), 0);
        assert!(hash(-5, 64, 1_000_000) >= 0);
    }

    #[test]
    fn walls_vote() {
        let mut votes = [0; WALLS];
        votes[0] = 2;
        assert_eq!(wall_winner(&votes, 0), 0);
        assert_eq!(wall_winner(&votes, 1), 1);
        votes[0] = 1;
        assert_eq!(wall_winner(&votes, 0), -1);
    }
}
