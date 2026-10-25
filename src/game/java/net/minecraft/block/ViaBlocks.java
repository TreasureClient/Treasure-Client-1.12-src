package net.minecraft.block;

/**
 * Treasure Client — "ViaBlocks": modern (1.13+) block rendering.
 *
 * Phase 3 proof-of-concept: a small set of modern full-cube blocks is registered
 * into the normal 1.12 block registry, each with its own blockstate / model /
 * texture assets, so the client can actually render modern content with real
 * textures. This proves the whole pipeline end to end (registry -> block-state id
 * -> chunk palette -> model -> texture atlas -> render) independent of the
 * network side.
 *
 * To grow coverage later: add a name here and drop the matching
 *   assets/minecraft/blockstates/&lt;name&gt;.json
 *   assets/minecraft/models/block/&lt;name&gt;.json
 *   assets/minecraft/models/item/&lt;name&gt;.json
 *   assets/minecraft/textures/blocks/&lt;name&gt;.png
 * (full-cube blocks only, for now). The registration is driven from
 * {@link Block#registerBlocks()} -> Block.registerViaBlocks().
 *
 * IDs start well above vanilla's last block id (255) to avoid any collision.
 */
public final class ViaBlocks {

	private ViaBlocks() {
	}

	/** First numeric block id used for ViaBlocks (well clear of vanilla 0..255). */
	public static final int FIRST_ID = 1000;

	/** Registry names (also the asset base names). Full-cube blocks only for now. */
	public static final String[] NAMES = {
		// Phase 3 proof-of-concept set.
		"deepslate",
		"calcite",
		"tuff",
		"amethyst_block",
		"copper_block",
		"smooth_basalt",
		// Phase 4 expansion — more modern full-cube blocks.
		"cobbled_deepslate",
		"polished_deepslate",
		"deepslate_bricks",
		"deepslate_tiles",
		"chiseled_deepslate",
		"polished_tuff",
		"tuff_bricks",
		"blackstone",
		"polished_blackstone",
		"polished_blackstone_bricks",
		"gilded_blackstone",
		"exposed_copper",
		"weathered_copper",
		"oxidized_copper",
		"cut_copper",
		"dripstone_block",
		"moss_block",
		"packed_mud",
		"mud_bricks",
		// Deepslate ores (full cubes).
		"deepslate_coal_ore",
		"deepslate_iron_ore",
		"deepslate_copper_ore",
		"deepslate_gold_ore",
		"deepslate_redstone_ore",
		"deepslate_emerald_ore",
		"deepslate_lapis_ore",
		"deepslate_diamond_ore",
		// Raw metal storage blocks (full cubes).
		"raw_iron_block",
		"raw_copper_block",
		"raw_gold_block",
		// 1.13 ocean blocks — full cubes (coral blocks, dried kelp, blue ice).
		"blue_ice",
		"tube_coral_block",
		"brain_coral_block",
		"bubble_coral_block",
		"fire_coral_block",
		"horn_coral_block",
		"dead_tube_coral_block",
		"dead_brain_coral_block",
		"dead_bubble_coral_block",
		"dead_fire_coral_block",
		"dead_horn_coral_block",
		"dried_kelp_block",
		// Nether + overworld ores (full cubes).
		"nether_gold_ore",
		"ancient_debris",
		"copper_ore",
		// 1.14 villager workstations — full cubes (furnaces/tables render a fixed
		// front; composter is a simplified closed box).
		"barrel",
		"blast_furnace",
		"smoker",
		"composter",
		"cartography_table",
		"fletching_table",
		"smithing_table",
		// 1.15 bees — full cubes (honey_block simplified opaque; nest/hive fixed front).
		"honeycomb_block",
		"honey_block",
		"bee_nest",
		"beehive",
		// 1.16 Nether Update — full cubes (cube_all).
		"netherite_block",
		"chiseled_polished_blackstone",
		"cracked_polished_blackstone_bricks",
		"chiseled_nether_bricks",
		"cracked_nether_bricks",
		"soul_soil",
		"crying_obsidian",
		"shroomlight",
		"warped_wart_block",
		"nether_wart_block",
		"quartz_bricks",
		"crimson_planks",
		"warped_planks",
		// 1.16 — cubes with a distinct top (custom models).
		"crimson_nylium",
		"warped_nylium",
		"target",
		"lodestone",
		// 1.16 nether stems (pillars — listed in PILLARS below).
		"crimson_stem",
		"warped_stem",
		"stripped_crimson_stem",
		"stripped_warped_stem",
		// Phase 4b — non-cube models: axis-rotated pillars (end + side textures,
		// rendered via block/cube_column). Listed in PILLARS below so the
		// registrar builds them as BlockRotatedPillar instead of a plain cube.
		"basalt",
		"polished_basalt",
		"bamboo_block",
		"muddy_mangrove_roots"
	};

	/**
	 * Names from {@link #NAMES} that register as axis-rotated pillars
	 * ({@link BlockRotatedPillar}) rather than plain full cubes. Each needs a
	 * cube_column block model with &lt;name&gt; (side) + &lt;name&gt;_top (end) textures
	 * and an axis-variant blockstate.
	 */
	public static final java.util.Set<String> PILLARS = new java.util.HashSet<String>(java.util.Arrays.asList(
		"basalt",
		"polished_basalt",
		"bamboo_block",
		"muddy_mangrove_roots",
		"crimson_stem",
		"warped_stem",
		"stripped_crimson_stem",
		"stripped_warped_stem"
	));

	/** True when the given ViaBlock name should be built as a rotated pillar. */
	public static boolean isPillar(String name) {
		return PILLARS.contains(name);
	}

	/** Registered block instances, filled in by Block.registerViaBlocks(). */
	public static final Block[] BLOCKS = new Block[NAMES.length];

	// ---- Phase 4b: slabs (non-cube geometry) --------------------------------
	/** First numeric id for ViaBlocks slabs (double + half take two ids each). */
	public static final int SLAB_FIRST_ID = 1100;

	/** Slab registry base names (each registers &lt;name&gt; + &lt;name without _slab&gt;_double_slab). */
	public static final String[] SLAB_NAMES = {
		"cobbled_deepslate_slab",
		"polished_deepslate_slab",
		"deepslate_brick_slab",
		"deepslate_tile_slab",
		"polished_tuff_slab",
		"tuff_brick_slab",
		"polished_blackstone_slab",
		"cut_copper_slab"
	};

	/** Base block texture each slab is made of (parallel to {@link #SLAB_NAMES}). */
	public static final String[] SLAB_TEX = {
		"cobbled_deepslate",
		"polished_deepslate",
		"deepslate_bricks",
		"deepslate_tiles",
		"polished_tuff",
		"tuff_bricks",
		"polished_blackstone",
		"cut_copper"
	};

	/** Registered slab instances (half + double), filled in by Block.registerViaBlocks(). */
	public static final BlockSlab[] SLAB_HALF = new BlockSlab[SLAB_NAMES.length];
	public static final BlockSlab[] SLAB_DOUBLE = new BlockSlab[SLAB_NAMES.length];

	// ---- Phase 4b: walls (non-cube geometry) --------------------------------
	/** First numeric id for ViaBlocks walls. */
	public static final int WALL_FIRST_ID = 1200;

	/** Wall registry names. */
	public static final String[] WALL_NAMES = {
		"cobbled_deepslate_wall",
		"deepslate_brick_wall",
		"blackstone_wall",
		"polished_blackstone_wall",
		"polished_blackstone_brick_wall",
		"mud_brick_wall"
	};

	/** The base cube (name in {@link #NAMES}) each wall is modelled on + textured from. */
	public static final String[] WALL_BASE = {
		"cobbled_deepslate",
		"deepslate_bricks",
		"blackstone",
		"polished_blackstone",
		"polished_blackstone_bricks",
		"mud_bricks"
	};

	/** Registered wall instances, filled in by Block.registerViaBlocks(). */
	public static final Block[] WALLS = new Block[WALL_NAMES.length];

	// ---- Cross-model plants (coral plants, kelp, seagrass, berry bush) ------
	/** First numeric id for ViaBlocks cross-model plants. */
	public static final int CROSS_FIRST_ID = 1300;

	/** Cross-plant registry names. */
	public static final String[] CROSS_NAMES = {
		"tube_coral",
		"brain_coral",
		"bubble_coral",
		"fire_coral",
		"horn_coral",
		"dead_tube_coral",
		"dead_brain_coral",
		"dead_bubble_coral",
		"dead_fire_coral",
		"dead_horn_coral",
		"kelp",
		"seagrass",
		"sweet_berry_bush",
		// Non-solid decorations (custom box/podium models, not the cross model).
		"lantern",
		"soul_lantern",
		"lectern"
	};

	/** The texture each cross/non-solid block uses (parallel to {@link #CROSS_NAMES}). */
	public static final String[] CROSS_TEX = {
		"tube_coral",
		"brain_coral",
		"bubble_coral",
		"fire_coral",
		"horn_coral",
		"dead_tube_coral",
		"dead_brain_coral",
		"dead_bubble_coral",
		"dead_fire_coral",
		"dead_horn_coral",
		"kelp",
		"seagrass",
		"sweet_berry_bush_stage3",
		"lantern",
		"soul_lantern",
		"lectern"
	};

	/** Registered cross-plant instances, filled in by Block.registerViaBlocks(). */
	public static final Block[] CROSS = new Block[CROSS_NAMES.length];

	/** Look up a registered ViaBlocks full-cube block by its name in {@link #NAMES}. */
	public static Block blockByName(String name) {
		for (int i = 0; i < NAMES.length; i++) {
			if (NAMES[i].equals(name)) {
				return BLOCKS[i];
			}
		}
		return null;
	}

	// ---- Extra simple blocks (cubes / pillars / see-through cubes) for TuffX+ servers ----
	public static final int EXTRA_FIRST_ID = 1400;

	public static final String[] EXTRA_NAMES = {
		"stripped_oak_log",
		"stripped_spruce_log",
		"stripped_birch_log",
		"stripped_jungle_log",
		"stripped_acacia_log",
		"stripped_dark_oak_log",
		"stripped_oak_wood",
		"stripped_spruce_wood",
		"stripped_birch_wood",
		"stripped_jungle_wood",
		"stripped_acacia_wood",
		"stripped_dark_oak_wood",
		"oak_wood",
		"spruce_wood",
		"birch_wood",
		"jungle_wood",
		"acacia_wood",
		"dark_oak_wood",
		"smooth_stone",
		"smooth_quartz",
		"crimson_hyphae",
		"warped_hyphae",
		"stripped_crimson_hyphae",
		"stripped_warped_hyphae",
		"infested_deepslate",
		"cracked_deepslate_bricks",
		"cracked_deepslate_tiles",
		"exposed_cut_copper",
		"weathered_cut_copper",
		"oxidized_cut_copper",
		"waxed_copper_block",
		"waxed_cut_copper",
		"waxed_exposed_copper",
		"waxed_exposed_cut_copper",
		"waxed_weathered_copper",
		"waxed_weathered_cut_copper",
		"waxed_oxidized_copper",
		"waxed_oxidized_cut_copper",
		"budding_amethyst",
		"rooted_dirt",
		"azalea_leaves",
		"flowering_azalea_leaves",
		"tinted_glass",
		"mangrove_log",
		"stripped_mangrove_log",
		"mangrove_wood",
		"stripped_mangrove_wood",
		"mangrove_planks",
		"mud",
		"pearlescent_froglight",
		"verdant_froglight",
		"ochre_froglight",
		"sculk",
		"cherry_wood",
		"stripped_cherry_wood",
		"cherry_planks",
		"bamboo_planks",
		"bamboo_mosaic",
		"suspicious_sand",
		"suspicious_gravel",
		"copper_grate",
		"exposed_copper_grate",
		"weathered_copper_grate",
		"oxidized_copper_grate",
		"waxed_copper_grate",
		"waxed_exposed_copper_grate",
		"waxed_weathered_copper_grate",
		"waxed_oxidized_copper_grate",
		"copper_bulb",
		"exposed_copper_bulb",
		"weathered_copper_bulb",
		"oxidized_copper_bulb",
		"waxed_copper_bulb",
		"waxed_exposed_copper_bulb",
		"waxed_weathered_copper_bulb",
		"waxed_oxidized_copper_bulb",
		"chiseled_copper",
		"exposed_chiseled_copper",
		"weathered_chiseled_copper",
		"oxidized_chiseled_copper",
		"waxed_chiseled_copper",
		"waxed_exposed_chiseled_copper",
		"waxed_weathered_chiseled_copper",
		"waxed_oxidized_chiseled_copper",
		"pale_oak_log",
		"stripped_pale_oak_log",
		"pale_oak_wood",
		"stripped_pale_oak_wood",
		"pale_oak_planks",
		"pale_moss_block",
		"resin_block",
		"resin_bricks",
		"chiseled_resin_bricks",
		"sulfur",
		"potent_sulfur",
		"polished_sulfur",
		"sulfur_bricks",
		"chiseled_sulfur",
		"cinnabar",
		"polished_cinnabar",
		"cinnabar_bricks",
		"chiseled_cinnabar",
		"poplar_planks",
		"poplar_log",
		"stripped_poplar_log",
		"stripped_poplar_wood",
		"poplar_wood",
		"red_poplar_leaves",
		"orange_poplar_leaves",
		"yellow_poplar_leaves"
	};

	/** 0 = cube, 1 = pillar (axis), 2 = see-through cube (parallel to EXTRA_NAMES). */
	public static final int[] EXTRA_KIND = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 2, 1, 1, 1, 1, 0, 0, 1, 1, 1, 0, 1, 1, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2};

	public static final Block[] EXTRA = new Block[EXTRA_NAMES.length];
}
