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
		"deepslate",
		"calcite",
		"tuff",
		"amethyst_block",
		"copper_block",
		"smooth_basalt"
	};

	/** Registered block instances, filled in by Block.registerViaBlocks(). */
	public static final Block[] BLOCKS = new Block[NAMES.length];
}
