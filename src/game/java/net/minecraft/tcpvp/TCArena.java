package net.minecraft.tcpvp;

import net.minecraft.block.BlockStone;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.BlockStoneSlab;
import net.minecraft.block.BlockQuartz;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;

/**
 * Treasure Client: the Practice world map, built by code (original design).
 *
 * <p>Flat grass everywhere (bedrock at y 58, dirt, grass at 63) so players knocked
 * out of an arena land on ground. On it:
 * <ul>
 *   <li>Spawn hub at 0,0: a round stone-brick and quartz plaza with kit signs
 *       (north half) and arena signs (south half).</li>
 *   <li>Duel arena at 0,120: stone-brick floor with a 3-high wall (sword/axe/netherite).</li>
 *   <li>Mace arena at 120,0: big andesite floor with four tall pillars with ladders,
 *       for falls, wind charges and smashes.</li>
 *   <li>Cart arena at 0,-120: grass field with a fence edge (rails go anywhere).</li>
 *   <li>Crystal arena at -120,0: obsidian floor on bedrock with a bedrock wall.</li>
 * </ul>
 * {@link #blockAt} is the whole map, so the chunk generator and the arena reset
 * ({@link TCPractice#resetArena}) build exactly the same blocks.
 */
public final class TCArena {

	public static final int GROUND = 63; // top grass/floor block; players stand at 64

	/** An arena: name, centre, half size (blocks from centre to wall), where its two fighters start. */
	public static final class Arena {
		public final String name, title;
		public final int cx, cz, half;

		Arena(String name, String title, int cx, int cz, int half) {
			this.name = name;
			this.title = title;
			this.cx = cx;
			this.cz = cz;
			this.half = half;
		}

		public boolean contains(int x, int z, int margin) {
			return Math.abs(x - this.cx) <= this.half + margin && Math.abs(z - this.cz) <= this.half + margin;
		}
	}

	public static final Arena DUEL = new Arena("duel", "Duel", 0, 120, 20);
	public static final Arena MACE = new Arena("mace", "Mace", 120, 0, 26);
	public static final Arena CART = new Arena("cart", "Cart", 0, -120, 24);
	public static final Arena CRYSTAL = new Arena("crystal", "Crystal", -120, 0, 20);
	public static final Arena[] ARENAS = { DUEL, MACE, CART, CRYSTAL };

	public static final int HUB_RADIUS = 16;

	private static IBlockState GRASS, DIRT, BEDROCK, STONEBRICK, CHISELED, MOSSY, QUARTZ, QUARTZ_PILLAR, SEA_LANTERN,
			GOLD, ANDESITE, POLISHED_ANDESITE, OBSIDIAN, FENCE, GLOWSTONE, SLAB, LADDER_N, LADDER_S, LADDER_E,
			LADDER_W;

	private TCArena() {
	}

	private static void init() {
		if (GRASS != null) {
			return;
		}
		DIRT = Blocks.DIRT.getDefaultState();
		BEDROCK = Blocks.BEDROCK.getDefaultState();
		STONEBRICK = Blocks.STONEBRICK.getDefaultState();
		CHISELED = Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT,
				BlockStoneBrick.EnumType.CHISELED);
		MOSSY = Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.MOSSY);
		QUARTZ = Blocks.QUARTZ_BLOCK.getDefaultState();
		QUARTZ_PILLAR = Blocks.QUARTZ_BLOCK.getDefaultState().withProperty(BlockQuartz.VARIANT,
				BlockQuartz.EnumType.LINES_Y);
		SEA_LANTERN = Blocks.SEA_LANTERN.getDefaultState();
		GOLD = Blocks.GOLD_BLOCK.getDefaultState();
		ANDESITE = Blocks.STONE.getDefaultState().withProperty(BlockStone.VARIANT, BlockStone.EnumType.ANDESITE);
		POLISHED_ANDESITE = Blocks.STONE.getDefaultState().withProperty(BlockStone.VARIANT,
				BlockStone.EnumType.ANDESITE_SMOOTH);
		OBSIDIAN = Blocks.OBSIDIAN.getDefaultState();
		FENCE = Blocks.OAK_FENCE.getDefaultState();
		GLOWSTONE = Blocks.GLOWSTONE.getDefaultState();
		SLAB = Blocks.STONE_SLAB.getDefaultState().withProperty(BlockStoneSlab.VARIANT, BlockStoneSlab.EnumType.SMOOTHBRICK);
		LADDER_N = Blocks.LADDER.getDefaultState().withProperty(BlockLadder.FACING, EnumFacing.NORTH);
		LADDER_S = Blocks.LADDER.getDefaultState().withProperty(BlockLadder.FACING, EnumFacing.SOUTH);
		LADDER_E = Blocks.LADDER.getDefaultState().withProperty(BlockLadder.FACING, EnumFacing.EAST);
		LADDER_W = Blocks.LADDER.getDefaultState().withProperty(BlockLadder.FACING, EnumFacing.WEST);
		GRASS = Blocks.GRASS.getDefaultState();
	}

	/** The block of the Practice map at a position (null = air). */
	public static IBlockState blockAt(int x, int y, int z) {
		init();
		if (y < 58) {
			return null;
		}
		if (y == 58) {
			return BEDROCK;
		}
		IBlockState a = arenaBlock(x, y, z);
		if (a != null) {
			return a == AIR ? null : a;
		}
		if (y < GROUND) {
			return DIRT;
		}
		if (y == GROUND) {
			return GRASS;
		}
		return null;
	}

	/** Marker for "air on purpose" inside an arena area. */
	private static final IBlockState AIR = Blocks.AIR.getDefaultState();

	/** A block decided by the hub or an arena, AIR, or null when it's plain ground there. */
	private static IBlockState arenaBlock(int x, int y, int z) {
		// hub
		int d2 = x * x + z * z;
		if (d2 <= (HUB_RADIUS + 1) * (HUB_RADIUS + 1)) {
			return hub(x, y, z, d2);
		}
		if (DUEL.contains(x, z, 0)) {
			return duel(x - DUEL.cx, y, z - DUEL.cz);
		}
		if (MACE.contains(x, z, 0)) {
			return mace(x - MACE.cx, y, z - MACE.cz);
		}
		if (CART.contains(x, z, 0)) {
			return cart(x - CART.cx, y, z - CART.cz);
		}
		if (CRYSTAL.contains(x, z, 0)) {
			return crystal(x - CRYSTAL.cx, y, z - CRYSTAL.cz);
		}
		return null;
	}

	private static IBlockState hub(int x, int y, int z, int d2) {
		int r2 = HUB_RADIUS * HUB_RADIUS;
		if (y == GROUND) {
			if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
				return x == 0 && z == 0 ? SEA_LANTERN : GOLD; // spawn pad
			}
			if (d2 > r2) {
				return QUARTZ; // rim
			}
			int ring = (int) Math.round(Math.sqrt(d2));
			if (ring == 5 || ring == 12) {
				return (x + z & 3) == 0 ? SEA_LANTERN : QUARTZ;
			}
			return ((x ^ z) & 4) == 0 ? STONEBRICK : CHISELED;
		}
		if (y < GROUND) {
			return y == GROUND - 1 ? STONEBRICK : DIRT;
		}
		// low rim with gaps at the four compass points, quartz pillars on the diagonals
		if (d2 > r2) {
			boolean gap = Math.abs(x) <= 2 || Math.abs(z) <= 2;
			if (y == GROUND + 1 && !gap) {
				return SLAB;
			}
			boolean diag = Math.abs(Math.abs(x) - Math.abs(z)) <= 1 && Math.abs(x) >= 11;
			if (diag && y <= GROUND + 4) {
				return y == GROUND + 4 ? GLOWSTONE : QUARTZ_PILLAR;
			}
			return AIR;
		}
		return AIR;
	}

	private static IBlockState duel(int x, int y, int z) {
		int h = DUEL.half;
		boolean edge = Math.abs(x) == h || Math.abs(z) == h;
		if (y == GROUND) {
			if (edge) {
				return CHISELED;
			}
			if (x == 0 && Math.abs(z) == h - 4) {
				return GOLD; // start pads
			}
			return ((x + z) & 1) == 0 ? STONEBRICK : MOSSY;
		}
		if (y < GROUND) {
			return y == GROUND - 1 ? STONEBRICK : DIRT;
		}
		if (edge && y <= GROUND + 3) {
			boolean corner = Math.abs(x) == h && Math.abs(z) == h;
			if (y == GROUND + 3) {
				return corner ? GLOWSTONE : SLAB;
			}
			return corner ? QUARTZ_PILLAR : STONEBRICK;
		}
		return AIR;
	}

	private static IBlockState mace(int x, int y, int z) {
		int h = MACE.half;
		boolean edge = Math.abs(x) == h || Math.abs(z) == h;
		if (y == GROUND) {
			if (edge) {
				return STONEBRICK;
			}
			int r = Math.max(Math.abs(x), Math.abs(z));
			if (r == 0) {
				return SEA_LANTERN;
			}
			return (r % 6) == 0 ? ANDESITE : POLISHED_ANDESITE;
		}
		if (y < GROUND) {
			return y == GROUND - 1 ? ANDESITE : DIRT;
		}
		if (edge) {
			return y == GROUND + 1 ? SLAB : AIR;
		}
		// four 3x3 pillars, 12 high, with a ladder on the side facing the middle
		int px = Math.abs(x) - 14, pz = Math.abs(z) - 14;
		if (Math.abs(px) <= 1 && Math.abs(pz) <= 1 && y <= GROUND + 12) {
			return y == GROUND + 12 ? GLOWSTONE : STONEBRICK;
		}
		if (y <= GROUND + 11 && Math.abs(pz) <= 0 && px == -2) {
			// ladder on the inner x side of the pillar
			return x > 0 ? LADDER_W : LADDER_E;
		}
		return AIR;
	}

	private static IBlockState cart(int x, int y, int z) {
		int h = CART.half;
		boolean edge = Math.abs(x) == h || Math.abs(z) == h;
		if (y == GROUND) {
			return edge ? STONEBRICK : GRASS;
		}
		if (y < GROUND) {
			return DIRT;
		}
		if (edge && y == GROUND + 1) {
			return FENCE;
		}
		return AIR;
	}

	private static IBlockState crystal(int x, int y, int z) {
		int h = CRYSTAL.half;
		boolean edge = Math.abs(x) == h || Math.abs(z) == h;
		if (y == GROUND) {
			return edge ? BEDROCK : OBSIDIAN;
		}
		if (y < GROUND) {
			return BEDROCK;
		}
		if (edge && y <= GROUND + 3) {
			return BEDROCK;
		}
		return AIR;
	}

	/** Where the two fighters start in an arena: index 0 or 1 -> {x, z, yaw}. */
	public static double[] start(Arena a, int i) {
		double off = a.half - 4;
		if (a == MACE) {
			off = 8;
		}
		double z = a.cz + (i == 0 ? -off : off) + 0.5D;
		return new double[] { a.cx + 0.5D, z, i == 0 ? 0.0D : 180.0D };
	}

	public static Arena byName(String name) {
		for (Arena a : ARENAS) {
			if (a.name.equalsIgnoreCase(name)) {
				return a;
			}
		}
		return null;
	}

	/** The arena (or null) whose area holds this x,z (with a small margin). */
	public static Arena at(double x, double z) {
		for (Arena a : ARENAS) {
			if (a.contains((int) Math.floor(x), (int) Math.floor(z), 4)) {
				return a;
			}
		}
		return null;
	}
}
