package net.minecraft.client;

/**
 * Treasure Client: Frame Pacing. Keeps the game doing only as much work per frame as it can
 * finish before the next screen refresh, so a weak or slowed-down computer holds a steady
 * 60 fps instead of jumping between 60 and 45.
 *
 * <p>Twice a second it looks at how many frames missed the screen refresh. When too many
 * did, it gives the work that can wait (chunk building, water re-sorts, packets) less time
 * per frame, one step at a time. After a few calm seconds it gives that time back, one step
 * at a time. The work every frame needs (the world, players, HUD) is never cut, and strong
 * computers never miss refreshes, so for them nothing changes.
 */
public final class TCFramePacer {

	/** Video Settings: Frame Pacing (on by default). */
	public static boolean enabled = true;

	/** Game work each frame should stay under, per level (leaves the rest of the refresh to the browser). */
	private static final long[] WORK_TARGET_NS = { 12500000L, 11000000L, 9500000L, 8000000L, 6500000L };
	/** Share of the normal packet time per frame, per level. */
	private static final int[] PKT_PERCENT = { 100, 85, 70, 60, 50 };
	private static final int MAX_LEVEL = WORK_TARGET_NS.length - 1;

	/** A frame longer than this missed the 60 Hz refresh (16.7 ms, with some slack). */
	private static final long MISSED_MS = 19L;
	private static final long WINDOW_MS = 500L;
	/** More than this share of missed frames in a window: shed a step. */
	private static final int SHED_PERCENT = 8;
	/** Below this share for CALM_WINDOWS windows in a row: give a step back. */
	private static final int CALM_PERCENT = 2;
	private static final int CALM_WINDOWS = 6;

	private static int level;
	private static long winStart = -1L;
	private static int winFrames, winMissed;
	private static int calm;

	/** Windows to judge a step by: if missed frames did not drop by a quarter, the step is undone. */
	private static final int TRIAL_WINDOWS = 2;
	/** After an undone step, windows to wait before trying another (10 s). */
	private static final int HOLD_WINDOWS = 20;
	private static int trial = -1, trialBase, trialSum;
	private static int hold;

	// for .lag
	private static int maxLevelSeen;
	private static int stepsDown, stepsUp, stepsUndone;
	private static long levelFramesSum, levelFrames;

	private TCFramePacer() {
	}

	/** Called for every gameplay frame with its length in ms (TCLagMonitor). */
	public static void frame(long now, long frameMs) {
		if (!enabled) {
			if (level != 0) {
				level = 0;
				calm = 0;
			}
			trial = -1;
			hold = 0;
			winStart = -1L;
			return;
		}
		if (frameMs > 1000L) {
			return; // the tab was hidden or a world loaded: not a sign of too much work
		}
		levelFramesSum += level;
		++levelFrames;
		if (winStart < 0L) {
			winStart = now;
			winFrames = winMissed = 0;
		}
		++winFrames;
		// with Steady FPS a frame owns several refreshes; it only missed when it overran its slot
		int steady = TCSteadyFps.target();
		long missedMs = steady > 0 ? 1000L / steady + 3L : MISSED_MS;
		if (frameMs >= missedMs) {
			++winMissed;
		}
		if (now - winStart < WINDOW_MS) {
			return;
		}
		if (winFrames >= 10) {
			window(winMissed * 100 / winFrames);
		}
		winStart = now;
		winFrames = winMissed = 0;
	}

	private static void window(int missedPct) {
		if (hold > 0) {
			--hold;
		}
		if (trial >= 0) {
			// a step was just taken: keep it only if it helped
			trialSum += missedPct;
			if (++trial < TRIAL_WINDOWS) {
				return;
			}
			int avg = trialSum / TRIAL_WINDOWS;
			trial = -1;
			if (avg * 4 > trialBase * 3) {
				--level; // cutting the waitable work did not help: give it back
				++stepsUndone;
				hold = HOLD_WINDOWS;
				calm = 0;
				return;
			}
		}
		if (missedPct > SHED_PERCENT) {
			calm = 0;
			if (level < MAX_LEVEL && hold == 0) {
				++level;
				++stepsDown;
				if (level > maxLevelSeen) maxLevelSeen = level;
				trial = 0;
				trialBase = missedPct;
				trialSum = 0;
			}
		} else if (missedPct < CALM_PERCENT) {
			if (++calm >= CALM_WINDOWS && level > 0) {
				--level;
				++stepsUp;
				calm = 0;
			}
		} else {
			calm = 0;
		}
	}

	/** Game work this frame should stay under (chunk building fills up to this). */
	public static long workTargetNs() {
		return WORK_TARGET_NS[level];
	}

	/** Packet time per frame, scaled down while the pacer is shedding work. */
	public static long packetBudgetNs(long normalNs) {
		long v = normalNs * PKT_PERCENT[level] / 100L;
		return Math.max(2000000L, v);
	}

	public static int level() {
		return level;
	}

	/** One line for .lag. */
	public static String describe() {
		if (!enabled) {
			return "§7Frame pacing: §foff";
		}
		String avg = levelFrames > 0 ? String.valueOf((levelFramesSum * 10L / levelFrames) / 10.0) : "0";
		return "§7Frame pacing: level §f" + level + "§7/" + MAX_LEVEL + " (work target §f"
				+ (WORK_TARGET_NS[level] / 100000L) / 10.0 + "ms§7) · avg level " + avg + ", highest " + maxLevelSeen
				+ " · " + stepsDown + " steps down, " + stepsUp + " up, " + stepsUndone + " undone (did not help)";
	}
}
