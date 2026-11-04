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
			SOUL_SAND = 88, MAGMA = 213, GRAVEL = 13, LAVA = 10, FLOWING_LAVA = 11, GRASS = 2, LOG = 17, LEAVES = 18,
			TALLGRASS = 31, RED_FLOWER = 38, BROWN_MUSHROOM = 39, RED_MUSHROOM = 40, VINE = 106, NETHER_WART = 115,
			COBBLE_WALL = 139, LEAVES2 = 161, CARPET = 171;

	/** Lush and dripstone caves: underground rules only look below this height. */
	private static final int UNDER_MAX_Y = 60;

	// biome groups (bit flags). ViaBackwards turns modern biomes into old ones: mangrove
	// swamp -> swamp, cherry grove -> flower forest, pale garden -> gravelly mountains.
	private static final int B_SWAMP = 1, B_FLOWER = 2, B_PALE = 4, B_DRY = 8, B_FOREST = 16, B_PLAINS = 32;
	private static byte[] groupTable;

	private static int group(int biomeId) {
		byte[] t = groupTable;
		if (t == null) {
			t = new byte[256];
			t[6] = t[134] = B_SWAMP;
			t[132] = B_FLOWER;
			t[131] = B_PALE;
			t[2] = t[17] = t[130] = t[37] = t[38] = t[39] = t[165] = t[166] = t[167] = B_DRY;
			t[4] = t[18] = t[27] = t[28] = t[29] = t[155] = t[156] = t[157] = B_FOREST;
			t[1] = t[129] = B_PLAINS;
			groupTable = t;
		}
		return t[biomeId & 255];
	}

	/** Every biome group present in this chunk. */
	private static int groupsIn(Chunk c) {
		byte[] b = c.getBiomeArray();
		int m = 0;
		for (int i = 0; i < b.length; i++) {
			m |= group(b[i]);
		}
		return m;
	}

	/** The biome group of one column. */
	private static int groupAt(Chunk c, World w, int x, int z) {
		try {
			if (c != null) {
				return group(c.getBiomeArray()[(z & 15) << 4 | (x & 15)]);
			}
			Chunk wc = w.getChunkFromChunkCoords(x >> 4, z >> 4);
			return wc == null ? 0 : group(wc.getBiomeArray()[(z & 15) << 4 | (x & 15)]);
		} catch (Throwable t) {
			return 0;
		}
	}

	/** Deep under the ground (no sky light, well below the surface), so a cave. */
	private static boolean underground(Chunk c, World w, int x, int y, int z) {
		if (y >= UNDER_MAX_Y || y < 0) {
			return false;
		}
		try {
			if (c != null) {
				if (c.getHeightValue(x, z) <= y + 6) {
					return false;
				}
				ExtendedBlockStorage sec = c.getBlockStorageArray()[(y + 1) >> 4];
				return sec != Chunk.NULL_BLOCK_STORAGE && sec.getExtSkylightValue(x, (y + 1) & 15, z) == 0;
			}
			Chunk wc = w.getChunkFromChunkCoords(x >> 4, z >> 4);
			if (wc == null || wc.getHeightValue(x & 15, z & 15) <= y + 6) {
				return false;
			}
			return w.getLightFor(net.minecraft.world.EnumSkyBlock.SKY, new BlockPos(x, y + 1, z)) == 0;
		} catch (Throwable t) {
			return false;
		}
	}

	/** Cheap position hash (picks one of several looks the same way every time). */
	private static int hash(int x, int y, int z) {
		int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
		return (h ^ (h >>> 13)) & 0x7fffffff;
	}

	/** {@link #hash} of the world position (chunk mode passes x/z inside the chunk). */
	private static int hashW(Chunk c, int x, int y, int z) {
		return c != null ? hash((c.xPosition << 4) + x, y, (c.zPosition << 4) + z) : hash(x, y, z);
	}

	// cherry groves: acacia look-alike logs in a flower forest chunk (real flower forests have none)
	private static Chunk cherryChunk;
	private static boolean cherryYes;

	private static boolean cherryHere(Chunk c, World w, int x, int z) {
		Chunk ch = c;
		if (ch == null) {
			ch = w.getChunkFromChunkCoords(x >> 4, z >> 4);
			if (ch == null) {
				return false;
			}
		}
		if (ch != cherryChunk) {
			cherryChunk = ch;
			cherryYes = false;
			ExtendedBlockStorage[] secs = ch.getBlockStorageArray();
			for (int si = 0; si < secs.length && !cherryYes; si++) {
				ExtendedBlockStorage sec = secs[si];
				if (sec == Chunk.NULL_BLOCK_STORAGE || sec.isEmpty()) {
					continue;
				}
				IBlockState[] pal = sec.getData().tcPaletteStates();
				if (pal == null) {
					continue;
				}
				for (int i = 0; i < pal.length; i++) {
					IBlockState ps = pal[i];
					if (ps == null) {
						continue;
					}
					Block b = ps.getBlock();
					if ((Block.getIdFromBlock(b) == LOG2 && (meta(ps) & 3) == 0)
							|| (cherryLog != null && b == cherryLog.getBlock())) {
						cherryYes = true;
						break;
					}
				}
			}
		}
		return cherryYes;
	}

	/** Deepslate only reaches above y=0 a few blocks (y 0 to 7). */
	private static final int DEEP_MAX_Y = 7;
	/** Amethyst geodes are underground. */
	private static final int GEODE_MAX_Y = 40;
	/** Copper ore doesn't generate above this. */
	private static final int COPPER_MAX_Y = 112;
	/** Iron veins are at most 9 blocks; a vein this big is copper. */
	private static final int COPPER_VEIN = 10;

	private static boolean ready, failed;
	private static IBlockState copper, dCopper, ironOre;
	private static IBlockState deepslate, tuff, amethyst, budding, calcite, smoothBasalt, basalt, crimsonStem,
			crimsonHyphae, warpedStem, warpedHyphae, crimsonNylium, warpedNylium, warpedWart, shroomlight, soulSoil,
			debris, blackstoneBricks, netherGold, dCoal, dIron, dGold, dDiamond, dEmerald, dLapis, dRedstone;
	private static int deepslateId, tuffId, amethystId, buddingId, calciteId, smoothBasaltId;
	private static IBlockState mangroveLog, mangroveWood, mangroveLeaves, mangroveRoots, mud, cherryLog, cherryWood,
			cherryLeaves, paleLog, paleWood, paleLeaves, moss, mossCarpet, paleMossCarpet, shortDry, tallDry,
			cornflower, lily, openEye, closedEye, caveVines, hangingRoots, propagule, paleMoss, pinkPetals, dripstone,
			pointedDrip, budSmall, budMedium, budLarge, cluster, crimsonRoots, warpedRoots, sprouts, crimsonFungus,
			warpedFungus, weeping, twisting;
	private static int pointedDripId, crimsonNyliumId, warpedNyliumId;

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
			copper = get("copper_ore");
			dCopper = get("deepslate_copper_ore");
			ironOre = net.minecraft.init.Blocks.IRON_ORE.getDefaultState();
			dGold = get("deepslate_gold_ore");
			dDiamond = get("deepslate_diamond_ore");
			dEmerald = get("deepslate_emerald_ore");
			dLapis = get("deepslate_lapis_ore");
			dRedstone = get("deepslate_redstone_ore");
			mangroveLog = get("mangrove_log");
			mangroveWood = get("mangrove_wood");
			mangroveLeaves = get("mangrove_leaves");
			mangroveRoots = get("mangrove_roots");
			mud = get("mud");
			cherryLog = get("cherry_log");
			cherryWood = get("cherry_wood");
			cherryLeaves = get("cherry_leaves");
			paleLog = get("pale_oak_log");
			paleWood = get("pale_oak_wood");
			paleLeaves = get("pale_oak_leaves");
			moss = get("moss_block");
			mossCarpet = get("moss_carpet");
			paleMossCarpet = get("pale_moss_carpet");
			shortDry = get("short_dry_grass");
			tallDry = get("tall_dry_grass");
			cornflower = get("cornflower");
			lily = get("lily_of_the_valley");
			openEye = get("open_eyeblossom");
			closedEye = get("closed_eyeblossom");
			caveVines = get("cave_vines");
			hangingRoots = get("hanging_roots");
			propagule = get("mangrove_propagule");
			paleMoss = get("pale_hanging_moss");
			pinkPetals = get("pink_petals");
			dripstone = get("dripstone_block");
			pointedDrip = get("pointed_dripstone");
			budSmall = get("small_amethyst_bud");
			budMedium = get("medium_amethyst_bud");
			budLarge = get("large_amethyst_bud");
			cluster = get("amethyst_cluster");
			crimsonRoots = get("crimson_roots");
			warpedRoots = get("warped_roots");
			sprouts = get("nether_sprouts");
			crimsonFungus = get("crimson_fungus");
			warpedFungus = get("warped_fungus");
			weeping = get("weeping_vines");
			twisting = get("twisting_vines");
			pointedDripId = idOf(pointedDrip);
			crimsonNyliumId = idOf(crimsonNylium);
			warpedNyliumId = idOf(warpedNylium);
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
			cherryChunk = null; // this chunk's blocks are new
			ExtendedBlockStorage[] secs = c.getBlockStorageArray();
			int mycelium = 0; // nether: crimson or warped nylium, from the stems in this chunk
			int groups = dim == 0 ? groupsIn(c) : 0;
			boolean highTrees = (groups & (B_SWAMP | B_FLOWER | B_PALE | B_DRY | B_FOREST | B_PLAINS)) != 0;
			int ironCount = 0;
			for (int si = 0; si < secs.length; si++) {
				ExtendedBlockStorage sec = secs[si];
				if (sec == Chunk.NULL_BLOCK_STORAGE || sec.isEmpty()) {
					continue;
				}
				int baseY = si << 4;
				if (dim == 0 && baseY > COPPER_MAX_Y && !highTrees) {
					break; // nothing to guess higher up in the overworld
				}
				BlockStateContainer data = sec.getData();
				int palSize = data.tcPaletteSize();
				if (palSize <= 0) {
					continue; // a section with 256+ kinds of block: a build, nothing to guess
				}
				// which palette entries are worth a look (most sections: none, done)
				if (wantBuf == null) {
					wantBuf = new boolean[256];
				}
				boolean[] want = wantBuf;
				boolean any = false;
				// granite is only dripstone where pointed dripstone (a cobblestone wall look-alike) is near
				secDrip = false;
				if (dim == 0 && baseY < UNDER_MAX_Y) {
					for (int i = 0; i < palSize; i++) {
						IBlockState ps = data.tcPaletteState(i);
						if (ps != null) {
							int pid = Block.getIdFromBlock(ps.getBlock());
							if (pid == COBBLE_WALL || pid == pointedDripId) {
								secDrip = true;
								break;
							}
						}
					}
				}
				for (int i = 0; i < 256; i++) {
					boolean wv = false;
					if (i < palSize) {
						IBlockState ps = data.tcPaletteState(i);
						wv = ps != null && isCandidate(ps, dim, baseY, groups);
					}
					want[i] = wv;
					any |= wv;
				}
				if (!any) {
					continue;
				}
				if (dim == -1 && mycelium == 0) {
					mycelium = nyliumHint(c);
				}
				if (idxBuf == null) {
					idxBuf = new int[4096];
				}
				int n = data.tcFind(want, idxBuf);
				for (int k = 0; k < n; k++) {
					int i = idxBuf[k];
					int x = i & 15, z = (i >> 4) & 15, y = i >> 8;
					IBlockState st = data.get(x, y, z);
					if (dim == 0 && Block.getIdFromBlock(st.getBlock()) == IRON_ORE) {
						// copper or iron: decided per vein below, once every ore block is known
						if (ironCount < MAX_IRON) {
							if (grid == null) {
								grid = new byte[65536];
								ironList = new int[MAX_IRON];
								queue = new int[MAX_IRON];
							}
							int key = ((baseY + y) << 8) | (z << 4) | x;
							grid[key] = 1;
							ironList[ironCount++] = key;
						}
						continue;
					}
					IBlockState g = guess(dim, st, c, null, x, baseY + y, z, mycelium);
					if (g != null) {
						data.set(x, y, z, g);
					}
				}
			}
			if (ironCount > 0) {
				decideVeins(c, ironCount);
			}
		} catch (Throwable t) {
			// a guess must never break chunk loading
		}
	}

	// ---- blocks you placed yourself ----------------------------------------------
	// You placed a modern block from its item, so we know what it really is; the server
	// sends it back as the old look-alike. Remembered per position (this world only).

	private static java.util.HashMap<Long, IBlockState> placed;
	private static java.util.HashSet<Long> placedChunks;
	private static World placedWorld;
	private static final int MAX_PLACED = 4096;

	public static void rememberPlaced(World w, BlockPos pos, IBlockState st) {
		try {
			if (st == null || !net.minecraft.client.gui.TreasureMods.viaGuess()) {
				return;
			}
			if (placed == null) {
				placed = new java.util.HashMap<Long, IBlockState>();
				placedChunks = new java.util.HashSet<Long>();
			}
			if (placedWorld != w || placed.size() >= MAX_PLACED) {
				placed.clear(); // a new world, or keep it small (older spots fall back to guessing)
				placedChunks.clear();
				placedWorld = w;
			}
			placed.put(Long.valueOf(pos.toLong()), st);
			placedChunks.add(Long.valueOf(((long) (pos.getX() >> 4) << 32) ^ ((pos.getZ() >> 4) & 0xffffffffL)));
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
		if (!placedChunks.contains(Long.valueOf(((long) cx << 32) ^ (cz & 0xffffffffL)))) {
			return; // nothing remembered in this chunk
		}
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

	/** The section being scanned has pointed dripstone (or its look-alike) in it. */
	private static boolean secDrip;

	// chunk scan buffers, made once and reused (no garbage per chunk)
	private static final int MAX_IRON = 8192;
	private static int[] idxBuf, ironList, queue;
	private static boolean[] wantBuf;
	private static byte[] grid; // y << 8 | z << 4 | x inside one chunk: 1 = iron look-alike, 2 = counted

	/** Labels each connected group of iron look-alikes in the chunk once: 10+ is copper. */
	private static void decideVeins(Chunk c, int count) {
		ExtendedBlockStorage[] secs = c.getBlockStorageArray();
		for (int s = 0; s < count; s++) {
			int start = ironList[s];
			if (grid[start] != 1) {
				continue; // already part of a counted vein
			}
			int n = 0, head = 0;
			grid[start] = 2;
			queue[n++] = start;
			while (head < n) {
				int p = queue[head++];
				int px = p & 15, pz = (p >> 4) & 15, py = p >> 8;
				for (int dy = -1; dy <= 1; dy++) {
					int ny = py + dy;
					if (ny < 0 || ny > 255) continue;
					for (int dz = -1; dz <= 1; dz++) {
						int nz = pz + dz;
						if (nz < 0 || nz > 15) continue;
						for (int dx = -1; dx <= 1; dx++) {
							int nx = px + dx;
							if (nx < 0 || nx > 15) continue;
							int q = (ny << 8) | (nz << 4) | nx;
							if (grid[q] == 1) {
								grid[q] = 2;
								queue[n++] = q;
							}
						}
					}
				}
			}
			boolean isCopper = n >= COPPER_VEIN;
			for (int k = 0; k < n; k++) {
				int p = queue[k];
				int y = p >> 8;
				boolean deep = y <= DEEP_MAX_Y;
				IBlockState want = isCopper ? (deep ? dCopper : copper) : (deep ? dIron : null);
				if (want != null) {
					ExtendedBlockStorage sec = secs[y >> 4];
					if (sec != Chunk.NULL_BLOCK_STORAGE) {
						sec.getData().set(p & 15, y & 15, (p >> 4) & 15, want);
					}
				}
			}
		}
		for (int s = 0; s < count; s++) {
			grid[ironList[s]] = 0; // clean for the next chunk
		}
	}

	/** Called for single block changes. Returns the state to use. */
	public static IBlockState onBlock(World w, BlockPos pos, IBlockState st) {
		if (st == null || w == null || !active()) {
			return st;
		}
		try {
			if (st.getBlock() == net.minecraft.init.Blocks.AIR) {
				noteBreak(w, pos);
			}
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

	/** Whether blocks of this kind can be a look-alike worth guessing here. */
	private static boolean isCandidate(IBlockState s, int dim, int baseY, int groups) {
		{
			int id = Block.getIdFromBlock(s.getBlock());
			if (dim == 0) {
				boolean low = baseY < UNDER_MAX_Y;
				switch (id) {
				case LOG2:
					return (groups & (B_SWAMP | B_FLOWER)) != 0;
				case LEAVES2: case DIRT:
					if ((groups & B_SWAMP) != 0) return true;
					break;
				case LEAVES:
					return (groups & (B_FLOWER | B_PALE)) != 0;
				case LOG:
					return (groups & (B_PALE | B_SWAMP)) != 0;
				case GRASS: case COBBLE_WALL:
					return low;
				case CARPET:
					return low || (groups & B_PALE) != 0;
				case TALLGRASS:
					return low || (groups & (B_DRY | B_SWAMP | B_PALE | B_FLOWER)) != 0;
				case RED_FLOWER:
					return (groups & (B_PLAINS | B_FLOWER | B_FOREST | B_PALE)) != 0;
				case STONE:
					if (low && secDrip && meta(s) == 1) return true; // granite: dripstone next to pointed dripstone
					break;
				}
				if (baseY <= DEEP_MAX_Y) {
					switch (id) {
					case NETHER_BRICK: case COBBLESTONE: case COAL_ORE: case IRON_ORE: case GOLD_ORE:
					case DIAMOND_ORE: case EMERALD_ORE: case LAPIS_ORE: case REDSTONE_ORE: case LIT_REDSTONE_ORE:
						return true;
					}
				}
				if (id == IRON_ORE && baseY <= COPPER_MAX_Y) {
					return true;
				}
				if (baseY < GEODE_MAX_Y && (id == PURPUR_BLOCK || id == PURPUR_PILLAR || id == GRAY_GLAZED
						|| (id == STONE && meta(s) == 4))) {
					return true;
				}
			} else {
				switch (id) {
				case GRAY_GLAZED: case LOG2: case MYCELIUM: case LIT_PUMPKIN: case RED_NETHER_BRICK: case GOLD_ORE:
				case OBSIDIAN: case NETHER_WART: case BROWN_MUSHROOM: case RED_MUSHROOM: case VINE:
					return true;
				case TALLGRASS:
					if (meta(s) == 1) return true;
					break;
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
			IBlockState lp = surfaceGuess(id, st, c, w, x, y, z);
			if (lp != null) {
				return lp;
			}
			if (y <= DEEP_MAX_Y && y >= 0) {
				switch (id) {
				case NETHER_BRICK:
					return deepslate;
				case COAL_ORE:
					return dCoal;
				case IRON_ORE:
					return veinSize(c, w, x, y, z) >= COPPER_VEIN ? dCopper : dIron;
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
			if (id == IRON_ORE && y > DEEP_MAX_Y && y <= COPPER_MAX_Y) {
				return veinSize(c, w, x, y, z) >= COPPER_VEIN ? copper : null;
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
		case NETHER_WART: {
			// roots grow on nylium (nether wart grows on soul sand)
			int below = idAt(c, w, x, y - 1, z);
			if (below == MYCELIUM || below == crimsonNyliumId) return below == MYCELIUM && nylium == 2 ? warpedRoots : crimsonRoots;
			if (below == warpedNyliumId) return warpedRoots;
			return null;
		}
		case TALLGRASS:
			return meta(st) == 1 ? sprouts : null;
		case BROWN_MUSHROOM:
		case RED_MUSHROOM: {
			int below = idAt(c, w, x, y - 1, z);
			if (below == MYCELIUM || below == crimsonNyliumId || below == warpedNyliumId) {
				return id == BROWN_MUSHROOM ? warpedFungus : crimsonFungus;
			}
			return null;
		}
		case VINE:
			return nylium == 2 ? twisting : weeping;
		case OBSIDIAN:
			// ancient debris: a lone block in netherrack (portals and bastions have obsidian next to obsidian)
			if (countAround(c, w, x, y, z, OBSIDIAN_LIKE) == 0 && countAround(c, w, x, y, z, NETHER_STONE) >= 4) {
				return debris;
			}
			return null;
		}
		return null;
	}

	/** A neighbour's block id, or -1 when unknown. */
	private static int idAt(Chunk c, World w, int x, int y, int z) {
		IBlockState s = at(c, w, x, y, z);
		return s == null ? -1 : Block.getIdFromBlock(s.getBlock());
	}

	private static boolean isLeaves(IBlockState s) {
		if (s == null) {
			return false;
		}
		int id = Block.getIdFromBlock(s.getBlock());
		if (id == LEAVES || id == LEAVES2) {
			return true;
		}
		Block b = s.getBlock();
		return (mangroveLeaves != null && b == mangroveLeaves.getBlock())
				|| (paleLeaves != null && b == paleLeaves.getBlock());
	}

	/**
	 * Overworld plants, trees and cave blocks, from where they grow: the biome, how deep
	 * under the ground, and what is next to them. Null when nothing fits.
	 */
	private static IBlockState surfaceGuess(int id, IBlockState st, Chunk c, World w, int x, int y, int z) {
		switch (id) {
		case LOG2: {
			int m = meta(st);
			if ((m & 3) != 0) return null; // acacia only
			int g = groupAt(c, w, x, z);
			if ((g & B_SWAMP) != 0) return (m & 12) == 12 ? mangroveWood : withAxis(mangroveLog, m & 12);
			if ((g & B_FLOWER) != 0) return (m & 12) == 12 ? cherryWood : withAxis(cherryLog, m & 12);
			return null;
		}
		case LEAVES2:
			return (meta(st) & 3) == 0 && (groupAt(c, w, x, z) & B_SWAMP) != 0 ? mangroveLeaves : null;
		case LEAVES: {
			int v = meta(st) & 3;
			int g = groupAt(c, w, x, z);
			if (v == 0 && (g & B_FLOWER) != 0 && cherryHere(c, w, x, z)) return cherryLeaves;
			if (v == 2 && (g & B_PALE) != 0) return paleLeaves;
			return null;
		}
		case LOG: {
			int m = meta(st);
			int g = groupAt(c, w, x, z);
			if ((m & 3) == 2 && (g & B_PALE) != 0) return (m & 12) == 12 ? paleWood : withAxis(paleLog, m & 12);
			if (m == 12 && (g & B_SWAMP) != 0) return mangroveRoots; // oak "all bark" in a swamp
			return null;
		}
		case DIRT:
			return meta(st) == 1 && (groupAt(c, w, x, z) & B_SWAMP) != 0 ? mud : null;
		case GRASS:
			return underground(c, w, x, y, z) ? moss : null; // lush caves
		case CARPET:
			if (meta(st) != 13) return null; // green
			if (underground(c, w, x, y, z)) return mossCarpet;
			return (groupAt(c, w, x, z) & B_PALE) != 0 ? paleMossCarpet : null;
		case TALLGRASS: {
			int m = meta(st);
			if (m == 1) {
				if ((groupAt(c, w, x, z) & B_DRY) == 0) return null;
				return (hashW(c, x, y, z) & 3) == 0 ? tallDry : shortDry;
			}
			if (m != 2) return null;
			// a fern where ferns never grow
			if (y < GEODE_MAX_Y && countAround(c, w, x, y, z, AMETHYST_LIKE | BUDDING_LIKE) >= 1) {
				switch (hashW(c, x, y, z) & 3) {
				case 0: return budSmall;
				case 1: return budMedium;
				case 2: return budLarge;
				default: return cluster;
				}
			}
			IBlockState above = at(c, w, x, y + 1, z);
			IBlockState below = at(c, w, x, y - 1, z);
			if (below != null && below.getBlock() == net.minecraft.init.Blocks.AIR && underground(c, w, x, y, z)) {
				int a = above == null ? -1 : Block.getIdFromBlock(above.getBlock());
				return a == DIRT ? hangingRoots : caveVines; // hanging from a cave roof
			}
			int g = groupAt(c, w, x, z);
			if ((g & B_SWAMP) != 0 && isLeaves(above)) return propagule;
			if ((g & B_PALE) != 0 && isLeaves(above)) return paleMoss;
			if ((g & B_FLOWER) != 0 && cherryHere(c, w, x, z)) return pinkPetals;
			return null;
		}
		case RED_FLOWER: {
			int m = meta(st);
			int g = groupAt(c, w, x, z);
			if (m == 1) { // blue orchid only grows in swamps
				if ((g & B_PALE) != 0) return closedEye;
				return (g & (B_PLAINS | B_FLOWER | B_FOREST)) != 0 ? cornflower : null;
			}
			if (m == 6) return (g & B_FOREST) != 0 ? lily : null; // white tulip in a forest
			if (m == 4) return (g & B_PALE) != 0 ? openEye : null;
			return null;
		}
		case COBBLE_WALL:
			return underground(c, w, x, y, z) ? pointedDrip : null; // dripstone caves
		case STONE:
			if (meta(st) == 1 && y < UNDER_MAX_Y && countAround(c, w, x, y, z, DRIP_LIKE) >= 1) {
				return dripstone; // granite holding pointed dripstone
			}
			return null;
		}
		return null;
	}

	// ---- copper or iron ------------------------------------------------------------
	// Both arrive as iron ore. A vein of 10+ is copper (iron veins are smaller), and when
	// one is broken its drop ("1.17 Raw Copper" / "1.17 Raw Iron") says for sure.

	private static boolean ironLike(IBlockState s) {
		if (s == null) {
			return false;
		}
		Block b = s.getBlock();
		return b == net.minecraft.init.Blocks.IRON_ORE || (copper != null && b == copper.getBlock())
				|| (dCopper != null && b == dCopper.getBlock()) || (dIron != null && b == dIron.getBlock());
	}

	/** Connected ore blocks (touching faces, edges or corners), counted up to 24. */
	private static int veinSize(Chunk c, World w, int x, int y, int z) {
		return vein(c, w, x, y, z, 24, null);
	}

	/** Flood fill over ore blocks; fills out (x,y,z triples) when given. Returns the count. */
	private static int vein(Chunk c, World w, int x, int y, int z, int max, int[] out) {
		int[] q = new int[max * 3];
		int n = 0, head = 0;
		q[n * 3] = x;
		q[n * 3 + 1] = y;
		q[n * 3 + 2] = z;
		++n;
		while (head < n && n < max) {
			int bx = q[head * 3], by = q[head * 3 + 1], bz = q[head * 3 + 2];
			++head;
			for (int dx = -1; dx <= 1 && n < max; dx++) {
				for (int dy = -1; dy <= 1 && n < max; dy++) {
					for (int dz = -1; dz <= 1 && n < max; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						int nx = bx + dx, ny = by + dy, nz = bz + dz;
						boolean seen = false;
						for (int k = 0; k < n; k++) {
							if (q[k * 3] == nx && q[k * 3 + 1] == ny && q[k * 3 + 2] == nz) {
								seen = true;
								break;
							}
						}
						if (!seen && ironLike(at(c, w, nx, ny, nz))) {
							q[n * 3] = nx;
							q[n * 3 + 1] = ny;
							q[n * 3 + 2] = nz;
							++n;
						}
					}
				}
			}
		}
		if (out != null) {
			System.arraycopy(q, 0, out, 0, Math.min(out.length, n * 3));
		}
		return n;
	}

	// recently broken ore blocks: x, y, z and when (a few at once while mining a vein)
	private static final int BREAKS = 8;
	private static int[] breakPos;
	private static long[] breakAt;
	private static int breakNext;
	private static long lastBreakAt;

	/** You are breaking this block (before the client removes it). */
	public static void noteLocalBreak(World w, BlockPos pos) {
		try {
			if (w != null && pos != null && active()) {
				noteBreak(w, pos);
			}
		} catch (Throwable t) {
		}
	}

	/** A block was just broken (the server set it to air): remember ore spots for the drop. */
	private static void noteBreak(World w, BlockPos pos) {
		IBlockState old = w.getBlockState(pos);
		if (!ironLike(old)) {
			return;
		}
		if (breakPos == null) {
			breakPos = new int[BREAKS * 3];
			breakAt = new long[BREAKS];
		}
		int i = breakNext;
		breakNext = (breakNext + 1) % BREAKS;
		breakPos[i * 3] = pos.getX();
		breakPos[i * 3 + 1] = pos.getY();
		breakPos[i * 3 + 2] = pos.getZ();
		breakAt[i] = net.lax1dude.eaglercraft.EagRuntime.steadyTimeMillis();
		lastBreakAt = breakAt[i];
	}

	/**
	 * A dropped item showed up (after its name was turned into the modern item). If it is
	 * raw copper / copper ore or raw iron / iron ore next to an ore just broken, the rest
	 * of that vein is that ore too.
	 */
	public static void onDrop(World w, net.minecraft.entity.Entity e, net.minecraft.item.ItemStack st) {
		try {
			if (breakPos == null || w == null || e == null || st == null || st.func_190926_b()) {
				return;
			}
			long now0 = net.lax1dude.eaglercraft.EagRuntime.steadyTimeMillis();
			if (now0 - lastBreakAt > 4000L || !active()) {
				return; // no ore broken just now: nothing to learn (most dropped items)
			}
			net.minecraft.item.Item it = st.getItem();
			String name = String.valueOf(net.minecraft.item.Item.REGISTRY.getNameForObject(it));
			boolean isCopper = name.endsWith("raw_copper") || name.endsWith("copper_ore");
			boolean isIron = name.endsWith("raw_iron") || name.endsWith("iron_ore");
			if (!isCopper && !isIron) {
				return;
			}
			long now = net.lax1dude.eaglercraft.EagRuntime.steadyTimeMillis();
			for (int i = 0; i < BREAKS; i++) {
				if (now - breakAt[i] > 4000L) {
					continue;
				}
				double dx = e.posX - (breakPos[i * 3] + 0.5), dy = e.posY - (breakPos[i * 3 + 1] + 0.5),
						dz = e.posZ - (breakPos[i * 3 + 2] + 0.5);
				if (dx * dx + dy * dy + dz * dz > 4.0D) {
					continue;
				}
				breakAt[i] = 0L; // used
				relabelVein(w, breakPos[i * 3], breakPos[i * 3 + 1], breakPos[i * 3 + 2], isCopper);
				return;
			}
		} catch (Throwable t) {
		}
	}

	private static void relabelVein(World w, int x, int y, int z, boolean isCopper) {
		int[] out = new int[64 * 3];
		int n = 0;
		// start from the ore blocks around the broken one (it is air now)
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (ironLike(at(null, w, x + dx, y + dy, z + dz))) {
						n = vein(null, w, x + dx, y + dy, z + dz, 64, out);
						dx = dy = dz = 2; // found the vein
					}
				}
			}
		}
		for (int i = 0; i < n; i++) {
			BlockPos p = new BlockPos(out[i * 3], out[i * 3 + 1], out[i * 3 + 2]);
			IBlockState cur = w.getBlockState(p);
			boolean deep = (dIron != null && cur.getBlock() == dIron.getBlock())
					|| (dCopper != null && cur.getBlock() == dCopper.getBlock())
					|| (p.getY() <= DEEP_MAX_Y && p.getY() >= 0);
			IBlockState want = isCopper ? (deep ? dCopper : copper) : (deep ? dIron : ironOre);
			if (want != null && cur != want) {
				w.setBlockState(p, want, 3);
				rememberPlaced(w, p, want); // keep it if the chunk is sent again
			}
		}
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
			BASALT_LIKE = 16, OBSIDIAN_LIKE = 32, NETHER_STONE = 64, DRIP_LIKE = 128;

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
		case COBBLE_WALL:
			return DRIP_LIKE;
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
		if (id == pointedDripId) return DRIP_LIKE;
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
