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
		"muddy_mangrove_roots"
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

	/** Look up a registered ViaBlocks full-cube block by its name in {@link #NAMES}. */
	public static Block blockByName(String name) {
		for (int i = 0; i < NAMES.length; i++) {
			if (NAMES[i].equals(name)) {
				return BLOCKS[i];
			}
		}
		return null;
	}
}
