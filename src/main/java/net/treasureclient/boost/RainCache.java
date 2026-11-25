package net.treasureclient.boost;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;

/**
 * Rain Cache. While it rains, every frame the game asks "rain, snow or nothing?" for every column
 * around you (441 at the default weather radius), and each answer means a blended biome lookup and a
 * temperature check. The answer for a spot only depends on that spot, whether its chunk is loaded and
 * the chunk's biomes, so here it's remembered per column (at your height) until a chunk is loaded,
 * unloaded or has its biomes changed, you change world, or a second has passed.
 * Steps aside for More Culling, which changes the same lookup.
 */
public final class RainCache {
    private RainCache() {}

    private static final long KEEP_NS = 1_000_000_000L;
    private static final int N = 64, MASK = N - 1; // a 64 x 64 window of columns (the radius is at most 10)
    private static final int[] X = new int[N * N], Y = new int[N * N], Z = new int[N * N], EPOCH = new int[N * N];
    private static final long[] AT = new long[N * N];
    private static final Biome.Precipitation[] VALUE = new Biome.Precipitation[N * N];
    private static int epoch = 1;
    private static Object level;

    /** A chunk was loaded, unloaded or had its biomes changed, or the world changed. */
    public static void chunksChanged() {
        epoch++;
    }

    private static int slot(BlockPos pos) {
        return ((pos.getX() & MASK) << 6) | (pos.getZ() & MASK);
    }

    /** The remembered answer for this spot, or null (then ask the game and call put). */
    public static Biome.Precipitation get(Object lvl, BlockPos pos) {
        if (!Boosts.ACTIVE[Boosts.RAIN]) return null;
        try {
            if (lvl != level) {
                level = lvl;
                epoch++;
            }
            int i = slot(pos);
            Biome.Precipitation v = VALUE[i];
            if (v != null && EPOCH[i] == epoch && X[i] == pos.getX() && Y[i] == pos.getY() && Z[i] == pos.getZ()
                && System.nanoTime() - AT[i] < KEEP_NS) {
                Boosts.ran(Boosts.RAIN);
                return v;
            }
        } catch (Throwable t) {
            Boosts.failed(Boosts.RAIN, t);
        }
        return null;
    }

    public static Biome.Precipitation put(BlockPos pos, Biome.Precipitation v) {
        if (v != null && Boosts.ACTIVE[Boosts.RAIN]) {
            int i = slot(pos);
            X[i] = pos.getX();
            Y[i] = pos.getY();
            Z[i] = pos.getZ();
            EPOCH[i] = epoch;
            AT[i] = System.nanoTime();
            VALUE[i] = v;
        }
        return v;
    }
}
