package net.minecraft.client.network;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.ViaBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/**
 * Treasure Client: Modern Block Guessing.
 *
 * A modern server behind ViaVersion/ViaBackwards turns every modern block into an
 * old 1.12 look-alike before it reaches us (deepslate becomes nether bricks, basalt
 * becomes gray glazed terracotta...). Chunk data only carries that look-alike, never
 * the real block's name, so on servers without a modern-blocks plugin (TuffX+ or
 * Treasure Client's own) the real block can only be guessed.
 *
 * The look-alikes come from ViaBackwards' own tables. A guess is only made where the
 * look-alike can't be a real old block in that spot: nether bricks near the bottom of
 * the overworld are deepslate, gray glazed terracotta in the nether is basalt, purpur
 * next to polished diorite underground is an amethyst geode, and so on. Builds in
 * normal worlds can't be told apart, so they are left alone.
 *
 * Purely visual and client-side.
 */
public final class TCViaGuess {

	private TCViaGuess() {
	}

	// 1.12 look-alike block ids (id << 4 | meta is the state id)
	private static final int COBBLESTONE = 4, GOLD_ORE = 14, IRON_ORE = 15, COAL_ORE = 16, LAPIS_ORE = 21,
			DIRT = 3, STONE = 1, NETHERRACK = 87, WOOL = 35, OBSIDIAN = 49, DIAMOND_ORE = 56, REDSTONE_ORE = 73,
			LIT_REDSTONE_ORE = 74, LIT_PUMPKIN = 91, MYCELIUM = 110, NETHER_BRICK = 112, EMERALD_ORE = 129,
			LOG2 = 162, PURPUR_BLOCK = 201, PURPUR_PILLAR = 202, RED_NETHER_BRICK = 215, GRAY_GLAZED = 242,
			SOUL_SAND = 88, MAGMA = 213, GRAVEL = 13, LAVA = 10, FLOWING_LAVA = 11;

	/** Deepslate only reaches above y=0 a few blocks (y 0 to 7). */
	private static final int DEEP_MAX_Y = 7;
	/** Amethyst geodes are underground. */
	private static final int GEODE_MAX_Y = 40;

	private static boolean ready, failed;
	private static IBlockState deepslate, tuff, amethyst, budding, calcite, smoothBasalt, basalt, crimsonStem,
			crimsonHyphae, warpedStem, warpedHyphae, crimsonNylium, warpedNylium, warpedWart, shroomlight, soulSoil,
			debris, blackstoneBricks, netherGold, dCoal, dIron, dGold, dDiamond, dEmerald, dLapis, dRedstone;
	private static int deepslateId, tuffId, amethystId, buddingId, calciteId, smoothBasaltId;

	private static IBlockState get(String name) {
		Block b = Block.getBlockFromName(name);
		if (b == null || Block.getIdFromBlock(b) < ViaBlocks.FIRST_ID) {
			return null;
		}
		return b.getDefaultState();
	}

	private static boolean init() {
		if (ready) {
			return !failed;
		}
		ready = true;
		try {
			deepslate = get("deepslate");
			tuff = get("tuff");
			amethyst = get("amethyst_block");
			budding = get("budding_amethyst");
			calcite = get("calcite");
			smoothBasalt = get("smooth_basalt");
			basalt = get("basalt");
			crimsonStem = get("crimson_stem");
			crimsonHyphae = get("crimson_hyphae");
			warpedStem = get("warped_stem");
			warpedHyphae = get("warped_hyphae");
			crimsonNylium = get("crimson_nylium");
			warpedNylium = get("warped_nylium");
			warpedWart = get("warped_wart_block");
			shroomlight = get("shroomlight");
			soulSoil = get("soul_soil");
			debris = get("ancient_debris");
			blackstoneBricks = get("polished_blackstone_bricks");
			netherGold = get("nether_gold_ore");
			dCoal = get("deepslate_coal_ore");
			dIron = get("deepslate_iron_ore");
			dGold = get("deepslate_gold_ore");
			dDiamond = get("deepslate_diamond_ore");
			dEmerald = get("deepslate_emerald_ore");
			dLapis = get("deepslate_lapis_ore");
			dRedstone = get("deepslate_redstone_ore");
			deepslateId = idOf(deepslate);
			tuffId = idOf(tuff);
			amethystId = idOf(amethyst);
			buddingId = idOf(budding);
			calciteId = idOf(calcite);
			smoothBasaltId = idOf(smoothBasalt);
		} catch (Throwable t) {
			failed = true;
		}
		return !failed;
	}

	private static int idOf(IBlockState s) {
		return s == null ? -1 : Block.getIdFromBlock(s.getBlock());
	}

	/** On, and the server isn't already sending the real modern blocks. */
	private static boolean active() {
		return net.minecraft.client.gui.TreasureMods.viaGuess() && !TCServerChannels.hasModernBlocksPlugin()
				&& init();
	}

	private static int dimOf(World w) {
		try {
			return w.provider.getDimensionType().getId();
		} catch (Throwable t) {
			return 0;
		}
	}

	// ---- chunks -----------------------------------------------------------------

	/** Called after a chunk packet is applied, before the chunk is drawn. */
	public static void onChunk(World w, Chunk c) {
		if (c == null || !active()) {
			return;
		}
		try {
			int dim = dimOf(w);
			if (dim != 0 && dim != -1) {
				return; // the End: purpur there is real (end cities)
			}
			applyPlaced(w, c);
			ExtendedBlockStorage[] secs = c.getBlockStorageArray();
			int mycelium = 0; // nether: crimson or warped nylium, from the stems in this chunk
			for (int si = 0; si < secs.length; si++) {
				ExtendedBlockStorage sec = secs[si];
				if (sec == Chunk.NULL_BLOCK_STORAGE || sec.isEmpty()) {
					continue;
				}
				int baseY = si << 4;
				if (dim == 0 && baseY > GEODE_MAX_Y) {
					break; // nothing to guess higher up in the overworld
				}
				BlockStateContainer data = sec.getData();
				IBlockState[] pal = data.tcPaletteStates();
				if (pal != null && !paletteHasCandidate(pal, dim, baseY)) {
					continue; // most sections: one look over the palette and done
				}
				if (dim == -1 && mycelium == 0) {
					mycelium = nyliumHint(c);
				}
				for (int y = 0; y < 16; y++) {
					int wy = baseY + y;
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							IBlockState st = data.get(x, y, z);
							IBlockState g = guess(dim, st, c, null, x, wy, z, mycelium);
							if (g != null) {
								data.set(x, y, z, g);
							}
						}
					}
				}
			}
		} catch (Throwable t) {
			// a guess must never break chunk loading
		}
	}

	// ---- blocks you placed yourself ----------------------------------------------
	// You placed a modern block from its item, so we know what it really is; the server
	// sends it back as the old look-alike. Remembered per position (this world only).

	private static java.util.HashMap<Long, IBlockState> placed;
	private static World placedWorld;
	private static final int MAX_PLACED = 4096;

	public static void rememberPlaced(World w, BlockPos pos, IBlockState st) {
		try {
			if (st == null || !net.minecraft.client.gui.TreasureMods.viaGuess()) {
				return;
			}
			if (placed == null) {
				placed = new java.util.HashMap<Long, IBlockState>();
			}
			if (placedWorld != w) {
				placed.clear();
				placedWorld = w;
			}
			if (placed.size() >= MAX_PLACED) {
				placed.clear(); // keep it small; older spots fall back to guessing
			}
			placed.put(Long.valueOf(pos.toLong()), st);
		} catch (Throwable t) {
		}
	}

	/** What you placed at this spot, or null. */
	private static IBlockState placedAt(World w, long key) {
		if (placed == null || placed.isEmpty() || placedWorld != w) {
			return null;
		}
		return placed.get(Long.valueOf(key));
	}

	private static void applyPlaced(World w, Chunk c) {
		if (placed == null || placed.isEmpty() || placedWorld != w) {
			return;
		}
		int cx = c.xPosition, cz = c.zPosition;
		for (java.util.Map.Entry<Long, IBlockState> e : placed.entrySet()) {
			BlockPos p = BlockPos.fromLong(e.getKey().longValue());
			if ((p.getX() >> 4) != cx || (p.getZ() >> 4) != cz || p.getY() < 0 || p.getY() > 255) {
				continue;
			}
			ExtendedBlockStorage sec = c.getBlockStorageArray()[p.getY() >> 4];
			if (sec == Chunk.NULL_BLOCK_STORAGE) {
				continue;
			}
			IBlockState cur = sec.get(p.getX() & 15, p.getY() & 15, p.getZ() & 15);
			if (cur.getBlock() != net.minecraft.init.Blocks.AIR
					&& Block.getIdFromBlock(cur.getBlock()) < ViaBlocks.FIRST_ID) {
				sec.set(p.getX() & 15, p.getY() & 15, p.getZ() & 15, e.getValue());
			}
		}
	}

	/** Called for single block changes. Returns the state to use. */
	public static IBlockState onBlock(World w, BlockPos pos, IBlockState st) {
		if (st == null || w == null || !active()) {
			return st;
		}
		try {
			IBlockState mine = placedAt(w, pos.toLong());
			if (mine != null) {
				if (st.getBlock() == net.minecraft.init.Blocks.AIR) {
					placed.remove(Long.valueOf(pos.toLong())); // broken or the placement was refused
					return st;
				}
				if (Block.getIdFromBlock(st.getBlock()) < ViaBlocks.FIRST_ID) {
					return mine;
				}
			}
			int dim = dimOf(w);
			if (dim != 0 && dim != -1) {
				return st;
			}
			IBlockState g = guess(dim, st, null, w, pos.getX(), pos.getY(), pos.getZ(), 1);
			return g != null ? g : st;
		} catch (Throwable t) {
			return st;
		}
	}

	private static boolean paletteHasCandidate(IBlockState[] pal, int dim, int baseY) {
		for (int i = 0; i < pal.length; i++) {
			IBlockState s = pal[i];
			if (s == null) {
				continue;
			}
			int id = Block.getIdFromBlock(s.getBlock());
			if (dim == 0) {
				if (baseY <= DEEP_MAX_Y) {
					switch (id) {
					case NETHER_BRICK: case COBBLESTONE: case COAL_ORE: case IRON_ORE: case GOLD_ORE:
					case DIAMOND_ORE: case EMERALD_ORE: case LAPIS_ORE: case REDSTONE_ORE: case LIT_REDSTONE_ORE:
						return true;
					}
				}
				if (id == PURPUR_BLOCK || id == PURPUR_PILLAR || id == GRAY_GLAZED
						|| (id == STONE && meta(s) == 4)) {
					return true;
				}
			} else {
				switch (id) {
				case GRAY_GLAZED: case LOG2: case MYCELIUM: case LIT_PUMPKIN: case RED_NETHER_BRICK: case GOLD_ORE:
				case OBSIDIAN:
					return true;
				case WOOL:
					if (meta(s) == 9) return true;
					break;
				case DIRT:
					if (meta(s) == 0) return true;
					break;
				}
			}
		}
		return false;
	}

	private static int meta(IBlockState s) {
		return s.getBlock().getMetaFromState(s);
	}

	/** 1 = crimson (acacia look-alike stems nearby), 2 = warped (dark oak look-alike). */
	private static int nyliumHint(Chunk c) {
		int crimson = 0, warped = 0;
		ExtendedBlockStorage[] secs = c.getBlockStorageArray();
		for (int si = 0; si < secs.length; si++) {
			ExtendedBlockStorage sec = secs[si];
			if (sec == Chunk.NULL_BLOCK_STORAGE || sec.isEmpty()) {
				continue;
			}
			IBlockState[] pal = sec.getData().tcPaletteStates();
			if (pal == null) {
				continue;
			}
			for (int i = 0; i < pal.length; i++) {
				IBlockState s = pal[i];
				if (s != null && Block.getIdFromBlock(s.getBlock()) == LOG2) {
					if ((meta(s) & 3) == 1) ++warped;
					else ++crimson;
				}
			}
		}
		return warped > crimson ? 2 : 1;
	}

	// ---- the guesses ------------------------------------------------------------

	/**
	 * The modern block a look-alike really is, or null to keep it. In chunk mode
	 * (c != null) x/z are 0-15 inside the chunk; otherwise they are world positions.
	 */
	private static IBlockState guess(int dim, IBlockState st, Chunk c, World w, int x, int y, int z, int nylium) {
		int id = Block.getIdFromBlock(st.getBlock());
		if (dim == 0) {
			if (y <= DEEP_MAX_Y && y >= 0) {
				switch (id) {
				case NETHER_BRICK:
					return deepslate;
				case COAL_ORE:
					return dCoal;
				case IRON_ORE:
					return dIron;
				case GOLD_ORE:
					return dGold;
				case DIAMOND_ORE:
					return dDiamond;
				case EMERALD_ORE:
					return dEmerald;
				case LAPIS_ORE:
					return dLapis;
				case REDSTONE_ORE:
				case LIT_REDSTONE_ORE:
					return dRedstone;
				case COBBLESTONE:
					// tuff blobs sit in deepslate (cobblestone doesn't generate down there)
					return countAround(c, w, x, y, z, DEEPSLATE_LIKE) >= 2 ? tuff : null;
				}
			}
			if (y < GEODE_MAX_Y && y >= 0) {
				// amethyst geodes: amethyst inside, calcite around it, smooth basalt outside
				switch (id) {
				case PURPUR_BLOCK:
					return countAround(c, w, x, y, z, CALCITE_LIKE | BUDDING_LIKE) >= 1 ? amethyst : null;
				case PURPUR_PILLAR:
					return countAround(c, w, x, y, z, AMETHYST_LIKE) >= 1 ? budding : null;
				case STONE:
					if (meta(st) == 4) { // polished diorite
						return countAround(c, w, x, y, z, AMETHYST_LIKE | BASALT_LIKE) >= 1 ? calcite : null;
					}
					return null;
				case GRAY_GLAZED:
					return countAround(c, w, x, y, z, CALCITE_LIKE) >= 1 ? smoothBasalt : null;
				}
			}
			return null;
		}
		// the nether
		switch (id) {
		case GRAY_GLAZED:
			return basalt; // basalt deltas and pillars (the look-alike drops the direction)
		case LOG2: {
			int m = meta(st);
			boolean warped = (m & 3) == 1;
			if ((m & 3) > 1) {
				return null;
			}
			int axis = m & 12;
			if (axis == 12) {
				return warped ? warpedHyphae : crimsonHyphae;
			}
			return withAxis(warped ? warpedStem : crimsonStem, axis);
		}
		case MYCELIUM:
			return nylium == 2 ? warpedNylium : crimsonNylium;
		case WOOL:
			return meta(st) == 9 ? warpedWart : null;
		case LIT_PUMPKIN:
			return shroomlight;
		case DIRT:
			return meta(st) == 0 ? soulSoil : null;
		case RED_NETHER_BRICK:
			return blackstoneBricks; // bastions
		case GOLD_ORE:
			return netherGold;
		case OBSIDIAN:
			// ancient debris: a lone block in netherrack (portals and bastions have obsidian next to obsidian)
			if (countAround(c, w, x, y, z, OBSIDIAN_LIKE) == 0 && countAround(c, w, x, y, z, NETHER_STONE) >= 4) {
				return debris;
			}
			return null;
		}
		return null;
	}

	private static IBlockState withAxis(IBlockState s, int logAxis) {
		if (s == null || !(s.getBlock() instanceof BlockRotatedPillar)) {
			return s;
		}
		try {
			if (logAxis == 4) {
				return s.withProperty(BlockRotatedPillar.AXIS, EnumFacing.Axis.X);
			} else if (logAxis == 8) {
				return s.withProperty(BlockRotatedPillar.AXIS, EnumFacing.Axis.Z);
			}
		} catch (Throwable t) {
		}
		return s;
	}

	// neighbour kinds (bit flags), counting both the look-alike and an already guessed block
	private static final int DEEPSLATE_LIKE = 1, CALCITE_LIKE = 2, AMETHYST_LIKE = 4, BUDDING_LIKE = 8,
			BASALT_LIKE = 16, OBSIDIAN_LIKE = 32, NETHER_STONE = 64;

	private static int kindOf(IBlockState s) {
		if (s == null) {
			return 0;
		}
		int id = Block.getIdFromBlock(s.getBlock());
		switch (id) {
		case NETHER_BRICK:
			return DEEPSLATE_LIKE;
		case STONE:
			return meta(s) == 4 ? CALCITE_LIKE : 0;
		case PURPUR_BLOCK:
			return AMETHYST_LIKE;
		case PURPUR_PILLAR:
			return BUDDING_LIKE;
		case GRAY_GLAZED:
			return BASALT_LIKE;
		case OBSIDIAN:
			return OBSIDIAN_LIKE;
		case NETHERRACK: case SOUL_SAND: case MAGMA: case GRAVEL: case LAVA: case FLOWING_LAVA:
			return NETHER_STONE;
		}
		if (id < ViaBlocks.FIRST_ID) {
			return 0;
		}
		if (id == deepslateId || id == tuffId) return DEEPSLATE_LIKE;
		if (id == calciteId) return CALCITE_LIKE;
		if (id == amethystId) return AMETHYST_LIKE;
		if (id == buddingId) return BUDDING_LIKE | AMETHYST_LIKE;
		if (id == smoothBasaltId) return BASALT_LIKE;
		return 0;
	}

	private static int countAround(Chunk c, World w, int x, int y, int z, int kinds) {
		int n = 0;
		if ((kindOf(at(c, w, x + 1, y, z)) & kinds) != 0) ++n;
		if ((kindOf(at(c, w, x - 1, y, z)) & kinds) != 0) ++n;
		if ((kindOf(at(c, w, x, y + 1, z)) & kinds) != 0) ++n;
		if ((kindOf(at(c, w, x, y - 1, z)) & kinds) != 0) ++n;
		if ((kindOf(at(c, w, x, y, z + 1)) & kinds) != 0) ++n;
		if ((kindOf(at(c, w, x, y, z - 1)) & kinds) != 0) ++n;
		return n;
	}

	/** A neighbour's block; outside this chunk (chunk mode) or the world it is unknown (null). */
	private static IBlockState at(Chunk c, World w, int x, int y, int z) {
		if (y < 0 || y > 255) {
			return null;
		}
		if (c != null) {
			if (x < 0 || x > 15 || z < 0 || z > 15) {
				return null;
			}
			return c.getBlockState(x, y, z);
		}
		BlockPos p = new BlockPos(x, y, z);
		return w.isBlockLoaded(p) ? w.getBlockState(p) : null;
	}
}
