package net.minecraft.client;

/**
 * Treasure Client: Steady FPS. Holds the frame rate at a rate the screen can show evenly
 * (45, 40 or 30 on a 60 Hz screen) instead of letting it wobble between 44 and 60.
 *
 * <p>A frame that takes one refresh and the next that takes two look like stutter, even at
 * 50 fps. Waiting for a fixed pattern of refreshes (30 = every 2nd, 40 = 2,1,2,1, 45 = 2,1,1)
 * makes motion even, and the computer does less work, so a fanless laptop heats up slower.
 * The wait itself happens in PlatformInput (it receives the rate as the frame limit while
 * VSync is on).
 *
 * <p>Auto starts at 60 and steps down (45, 40, 30) when too many frames miss their slot.
 * After a calm stretch it tries the next rate up again; if that fails it waits twice as
 * long before the next try.
 */
public final class TCSteadyFps {

	/** Video Settings "Steady FPS": 0 = off, -1 = auto, else the fixed rate. */
	public static int mode = 0;

	private static final int[] RATES = { 60, 45, 40, 30 };
	private static final long WINDOW_MS = 2000L;
	/** More than this share of frames late for their slot: step down. */
	private static final int LATE_PERCENT = 10;
	/** Below this share: a calm window. */
	private static final int CALM_PERCENT = 2;

	private static int autoIdx;
	private static long winStart = -1L;
	private static int winFrames, winLate;
	private static int calmWindows;
	private static int upWait = 10; // calm windows before trying a higher rate (20 s)
	private static boolean trying; // the current rate is a try-up

	private TCSteadyFps() {
	}

	/** The rate to hold this frame, or 0 for no steady rate. */
	public static int target() {
		if (mode > 0) {
			return mode;
		}
		if (mode < 0) {
			int r = RATES[autoIdx];
			return r >= 60 ? 0 : r;
		}
		return 0;
	}

	/** Called every gameplay frame with its length in ms (TCLagMonitor). */
	public static void frame(long now, long frameMs) {
		if (mode >= 0) {
			autoIdx = 0;
			winStart = -1L;
			return;
		}
		if (frameMs > 1000L) {
			return; // hidden tab or a loading moment
		}
		if (winStart < 0L) {
			winStart = now;
			winFrames = winLate = 0;
		}
		++winFrames;
		double slot = 1000.0 / RATES[autoIdx];
		if (frameMs > slot + 4.0) {
			++winLate;
		}
		if (now - winStart < WINDOW_MS) {
			return;
		}
		if (winFrames >= 10) {
			int late = winLate * 100 / winFrames;
			if (late > LATE_PERCENT) {
				if (trying) {
					upWait = Math.min(upWait * 2, 120); // the higher rate didn't hold: try again later
				}
				trying = false;
				calmWindows = 0;
				if (autoIdx < RATES.length - 1) {
					++autoIdx;
				}
			} else if (late < CALM_PERCENT) {
				if (trying) {
					trying = false; // held a whole window: keep it
					upWait = 10;
				}
				if (++calmWindows >= upWait && autoIdx > 0) {
					--autoIdx;
					trying = true;
					calmWindows = 0;
				}
			} else {
				calmWindows = 0;
			}
		}
		winStart = now;
		winFrames = winLate = 0;
	}

	/** One line for .lag. */
	public static String describe() {
		if (mode == 0) {
			return "§7Steady FPS: §foff";
		}
		if (mode > 0) {
			return "§7Steady FPS: §f" + mode;
		}
		return "§7Steady FPS: §fauto, now " + RATES[autoIdx];
	}
}
