package net.minecraft.client;

/**
 * Treasure Client: is the processor slowed down right now (heat on a fanless laptop)?
 *
 * <p>TCLagMonitor runs the same small CPU test every 5 seconds of play. When the chip
 * gets hot it lowers its clock and the same test takes longer. The speed here is the
 * fastest of the last 3 tests (15 s, so one busy moment doesn't count) compared to the
 * fastest test this session.
 */
public final class TCHeat {

	/** Slower than this share of its best: hot. */
	private static final int HOT_BELOW = 65;
	/** Faster than this again: cooled down. */
	private static final int COOL_ABOVE = 80;

	private static int best;
	private static final int[] recent = new int[3];
	private static int count, idx;
	private static boolean hot;

	private TCHeat() {
	}

	/** One CPU test result in nanoseconds (TCLagMonitor). */
	public static void sample(int ns) {
		if (ns <= 0) {
			return;
		}
		if (best == 0 || ns < best) {
			best = ns;
		}
		recent[idx] = ns;
		idx = (idx + 1) % recent.length;
		if (count < recent.length) {
			++count;
		}
		int s = speed();
		if (hot) {
			if (s > COOL_ABOVE) {
				hot = false;
			}
		} else if (s < HOT_BELOW) {
			hot = true;
		}
	}

	/** Speed now as a share of the best this session (100 = not slowed down). */
	public static int speed() {
		if (count < recent.length || best == 0) {
			return 100;
		}
		int m = Integer.MAX_VALUE;
		for (int i = 0; i < count; i++) {
			m = Math.min(m, recent[i]);
		}
		return (int) ((long) best * 100L / m);
	}

	/** Video Settings "Lighter When Hot" (on by default). */
	public static boolean liteEnabled = true;

	/**
	 * True while hot and Lighter When Hot is on: work that can wait does less (fewer particles,
	 * faraway mobs updated every other tick, less chunk building per frame). The frame rate
	 * itself is never capped.
	 */
	public static boolean lite() {
		return hot && liteEnabled;
	}

	/** True while the processor runs well below its best. */
	public static boolean hot() {
		return hot;
	}
}
