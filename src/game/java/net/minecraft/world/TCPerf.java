package net.minecraft.world;

/**
 * Treasure Client: per-tick limits that keep big TNT chains from flooding the
 * game with particles. Each world tick gets a fresh budget.
 */
public final class TCPerf {

	private TCPerf() {
	}

	/** Most smoke/poof particle pairs one explosion may spawn (vanilla: one per block in the blast). */
	public static final int EXPLOSION_PAIRS_PER_EXPLOSION = 96;
	/** Most explosion smoke/poof particle pairs spawned per tick across all explosions. */
	public static final int EXPLOSION_PAIRS_PER_TICK = 288;
	/** Most fuse smoke particles from lit TNT per tick. */
	public static final int TNT_SMOKE_PER_TICK = 48;
	/** Most big explosion "boom" particles per tick (one TNT makes 6 per tick). */
	public static final int LARGE_EXPLOSION_PER_TICK = 18;

	private static int budgetWorld;
	private static long budgetTime = Long.MIN_VALUE;
	private static int explosionPairs;
	private static int tntSmoke;
	private static int largeExplosion;

	private static void roll(World world) {
		int id = System.identityHashCode(world);
		long time = world.getTotalWorldTime();
		if (id != budgetWorld || time != budgetTime) {
			budgetWorld = id;
			budgetTime = time;
			explosionPairs = 0;
			tntSmoke = 0;
			largeExplosion = 0;
		}
	}

	/** How many of {@code wanted} explosion particle pairs may be spawned now. */
	public static int takeExplosionPairs(World world, int wanted) {
		roll(world);
		int n = Math.min(wanted, EXPLOSION_PAIRS_PER_EXPLOSION);
		n = Math.min(n, EXPLOSION_PAIRS_PER_TICK - explosionPairs);
		if (n < 0) {
			n = 0;
		}
		explosionPairs += n;
		return n;
	}

	public static boolean takeTntSmoke(World world) {
		roll(world);
		return tntSmoke++ < TNT_SMOKE_PER_TICK;
	}

	public static boolean takeLargeExplosion(World world) {
		roll(world);
		return largeExplosion++ < LARGE_EXPLOSION_PER_TICK;
	}
}
