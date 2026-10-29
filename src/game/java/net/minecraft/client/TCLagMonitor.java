package net.minecraft.client;

import net.lax1dude.eaglercraft.EagRuntime;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.text.TextComponentString;

/**
 * Treasure Client: records lag spikes from the moment the game starts, so the
 * ".lag" chat command can show what changed over a session: FPS, spikes and
 * page memory per minute, and where the time of the last big spikes went.
 *
 * A frame is split into: logic (game ticks, which include server packets),
 * drawing (building the frame on the CPU) and browser/GPU (the time the game
 * waits for the browser: GPU work, garbage collection, other tabs).
 */
public class TCLagMonitor {

	private static final int SPIKE_MS = 60;
	private static final int MINUTES = 120;
	private static final int RING = 8;

	/** Server packets handled; the network code adds to this. */
	public static int packets;

	// parts of the drawing time, timed by the renderers each frame
	public static final int TERRAIN = 0, ENTITIES = 1, BLOCK_ENTS = 2, NAME_TAGS = 3, PARTICLES = 4, HUD = 5,
			SKY = 6, OTHER = 7, PARTS = 8;
	private static final String[] PART_NAMES = { "terrain", "entities", "block ents", "name tags", "particles",
			"HUD", "sky", "other" };
	private static final long[] cur = new long[PARTS];
	private static final long[][] mPartAll = new long[MINUTES][PARTS]; // all frames, ns
	private static final long[][] mPartSpike = new long[MINUTES][PARTS]; // spike frames, ns

	/** Current time for timing a part. */
	public static long now() {
		return EagRuntime.nanoTime();
	}

	/** Adds the time since startNs to a part of this frame's drawing. */
	public static void add(int part, long startNs) {
		cur[part] += EagRuntime.nanoTime() - startNs;
	}

	private static long start = -1L, lastEnd = -1L, lastHeapRead;
	// Dynamic Resolution: frames and logic time over the last second, written for the overlay
	private static long winStart = -1L, winLogicNs;
	private static int winFrames, winSeq;
	private static int lastChunks;
	private static int curMinute = -1;
	private static final int[] mFrames = new int[MINUTES];
	private static final int[] mSpikes = new int[MINUTES];
	private static final int[] mWorst = new int[MINUTES];
	private static final int[] mHeap = new int[MINUTES];
	private static final long[] mMs = new long[MINUTES];
	private static final long[] mBrowser = new long[MINUTES];
	private static final long[] mLogic = new long[MINUTES];
	private static final long[] mDraw = new long[MINUTES];
	private static int heapNow = -1;

	// last spikes: minute, second, total, logic, draw, browser, packets, chunk rebuilds, heap,
	// biggest drawing part, its ms
	private static final int[][] ring = new int[RING][11];
	private static int ringPos, ringCount;

	/** Called once per frame from Minecraft.runGameLoop with the time of each part in nanoseconds. */
	public static void frame(long logicNs, long drawNs, long browserNs) {
		long now = EagRuntime.steadyTimeMillis();
		int pkts = packets;
		packets = 0;
		int chunks = RenderChunk.renderChunksUpdated;
		int chunkDelta = chunks >= lastChunks ? chunks - lastChunks : chunks;
		lastChunks = chunks;
		if (start < 0L) {
			start = now;
			lastEnd = now;
			clearParts();
			return;
		}
		long total = now - lastEnd;
		lastEnd = now;
		reportWindow(now, logicNs);
		// entities were timed including the block entities and name tags inside them
		cur[ENTITIES] = Math.max(0L, cur[ENTITIES] - cur[BLOCK_ENTS] - cur[NAME_TAGS]);
		long timed = 0L;
		for (int k = 0; k < OTHER; k++) timed += cur[k];
		cur[OTHER] = Math.max(0L, drawNs - timed);
		if (now - lastHeapRead > 5000L) {
			lastHeapRead = now;
			long[] h = TCPerfStats.pageMemory();
			heapNow = h == null ? -1 : (int) h[0];
		}
		int minute = (int) ((now - start) / 60000L);
		if (minute >= MINUTES) {
			clearParts();
			return; // two hours is plenty of history
		}
		if (minute != curMinute) {
			curMinute = minute;
		}
		mFrames[minute]++;
		mMs[minute] += total;
		mHeap[minute] = heapNow;
		for (int k = 0; k < PARTS; k++) mPartAll[minute][k] += cur[k];
		int logic = (int) (logicNs / 1000000L), draw = (int) (drawNs / 1000000L);
		int browser = (int) Math.max(0L, total - logic - draw);
		if (total >= SPIKE_MS) {
			mSpikes[minute]++;
			if (total > mWorst[minute]) mWorst[minute] = (int) total;
			mLogic[minute] += logic;
			mDraw[minute] += draw;
			mBrowser[minute] += browser;
			int[] r = ring[ringPos];
			int sec = (int) ((now - start) / 1000L);
			r[0] = sec / 60;
			r[1] = sec % 60;
			r[2] = (int) total;
			r[3] = logic;
			r[4] = draw;
			r[5] = browser;
			r[6] = pkts;
			r[7] = chunkDelta;
			r[8] = heapNow;
			int big = 0;
			for (int k = 0; k < PARTS; k++) {
				mPartSpike[minute][k] += cur[k];
				if (cur[k] > cur[big]) big = k;
			}
			r[9] = big;
			r[10] = (int) (cur[big] / 1000000L);
			ringPos = (ringPos + 1) % RING;
			if (ringCount < RING) ringCount++;
		}
		clearParts();
	}

	/**
	 * Once a second, writes "fps,logic%,fps limit,in world,seq" to the "tcframe" storage key.
	 * The overlay's Dynamic Resolution reads it to lower the render resolution when
	 * frames get slow from drawing (a hot device), not from server lag.
	 */
	private static void reportWindow(long now, long logicNs) {
		if (winStart < 0L) {
			winStart = now;
			return;
		}
		winFrames++;
		winLogicNs += logicNs;
		long ms = now - winStart;
		if (ms < 1000L) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		int fps = (int) (winFrames * 1000L / ms);
		int logicPct = (int) Math.min(100L, winLogicNs / 10000L / ms);
		int limit = mc.getLimitFramerate();
		boolean inWorld = mc.world != null && mc.player != null;
		try {
			EagRuntime.setStorage("tcframe",
					(fps + "," + logicPct + "," + limit + "," + (inWorld ? 1 : 0) + "," + (++winSeq))
							.getBytes("UTF-8"));
		} catch (Throwable t) {
		}
		winStart = now;
		winFrames = 0;
		winLogicNs = 0L;
	}

	private static void clearParts() {
		for (int k = 0; k < PARTS; k++) cur[k] = 0L;
	}

	/** "terrain 6.1 · entities 2.0 ..." as ms per frame (perFrame) or % of the total. */
	private static String partLine(long[] sum, long frames, boolean perFrame) {
		long all = 0L;
		for (int k = 0; k < PARTS; k++) all += sum[k];
		StringBuilder sb = new StringBuilder();
		for (int k = 0; k < PARTS; k++) {
			if (sb.length() > 0) sb.append(" §7· ");
			sb.append("§f").append(PART_NAMES[k]).append(' ');
			if (perFrame) {
				long tenths = frames > 0 ? sum[k] / 100000L / frames : 0L;
				sb.append(tenths / 10).append('.').append(tenths % 10);
			} else {
				sb.append(all > 0 ? sum[k] * 100L / all : 0L).append('%');
			}
		}
		return sb.toString();
	}

	private static void say(Minecraft mc, String s) {
		mc.ingameGUI.getChatGUI().printChatMessage(new TextComponentString(s));
	}

	private static int fps(int m) {
		return mMs[m] > 0 ? (int) (mFrames[m] * 1000L / mMs[m]) : 0;
	}

	public static void print(Minecraft mc) {
		if (start < 0L || curMinute < 0) {
			say(mc, "§6[Lag] §7no data yet");
			return;
		}
		int last = Math.min(curMinute, MINUTES - 1);
		say(mc, "§6[Lag] §f" + (last + 1) + " min recorded §7(spike = a frame over " + SPIKE_MS + " ms)");
		// minute table, newest last, at most 20 minutes
		int from = Math.max(0, last - 19);
		StringBuilder sb = new StringBuilder();
		for (int m = from; m <= last; m++) {
			if (sb.length() > 0 && (m - from) % 5 == 0) {
				say(mc, sb.toString());
				sb.setLength(0);
			}
			int sp = mSpikes[m];
			String spc = sp == 0 ? "§a" : sp < 5 ? "§e" : "§c";
			sb.append("§7m").append(m + 1).append(" §f").append(fps(m)).append("fps ").append(spc).append(sp)
					.append("sp");
			if (mHeap[m] > 0) sb.append(" §8").append(mHeap[m]).append("MB");
			sb.append("  ");
		}
		if (sb.length() > 0) say(mc, sb.toString());

		// where spike time went, first half of the session vs. the last 5 minutes
		long l = 0, d = 0, b = 0;
		for (int m = Math.max(0, last - 4); m <= last; m++) {
			l += mLogic[m];
			d += mDraw[m];
			b += mBrowser[m];
		}
		long sum = l + d + b;
		if (sum > 0) {
			say(mc, "§7Spike time, last 5 min: §flogic " + (l * 100 / sum) + "% §7· §fdrawing " + (d * 100 / sum)
					+ "% §7· §fbrowser/GPU " + (b * 100 / sum) + "%");
		}
		// what the drawing time is made of, last 5 minutes
		long[] all = new long[PARTS], spk = new long[PARTS];
		long frames = 0L;
		for (int m = Math.max(0, last - 4); m <= last; m++) {
			frames += mFrames[m];
			for (int k = 0; k < PARTS; k++) {
				all[k] += mPartAll[m][k];
				spk[k] += mPartSpike[m][k];
			}
		}
		say(mc, "§7Drawing, ms per frame, last 5 min: " + partLine(all, frames, true));
		if (sum > 0) {
			say(mc, "§7Drawing in spikes, last 5 min: " + partLine(spk, 0L, false));
		}
		if (ringCount > 0) {
			say(mc, "§7Last spikes (time: total = logic + drawing + browser/GPU · packets · chunk rebuilds):");
			for (int i = 0; i < ringCount; i++) {
				int[] r = ring[(ringPos - 1 - i + RING * 2) % RING];
				say(mc, "§8 " + r[0] + ":" + (r[1] < 10 ? "0" : "") + r[1] + " §f" + r[2] + "ms §7= " + r[3] + " + "
						+ r[4] + " + " + r[5] + " §8· " + r[6] + " pkts · " + r[7] + " chunks"
						+ (r[8] > 0 ? " · " + r[8] + "MB" : "") + " · most: " + PART_NAMES[r[9]] + " " + r[10]
						+ "ms");
			}
		}
		say(mc, "§6[Lag] §7" + verdict(last));
	}

	private static String verdict(int last) {
		int early = Math.min(3, last);
		int heapStart = -1, heapEnd = -1;
		for (int m = 0; m <= last; m++) {
			if (mHeap[m] > 0) {
				if (heapStart < 0) heapStart = mHeap[m];
				heapEnd = mHeap[m];
			}
		}
		int spikesLate = 0, spikesEarly = 0;
		for (int m = Math.max(0, last - 4); m <= last; m++) spikesLate += mSpikes[m];
		for (int m = 0; m < early; m++) spikesEarly += mSpikes[m];
		long l = 0, d = 0, b = 0;
		for (int m = Math.max(0, last - 4); m <= last; m++) {
			l += mLogic[m];
			d += mDraw[m];
			b += mBrowser[m];
		}
		if (spikesLate == 0) {
			return "No spikes in the last 5 minutes.";
		}
		String trend = (heapStart > 0 && heapEnd > heapStart * 3 / 2)
				? "Page memory grew from " + heapStart + " to " + heapEnd + " MB. "
				: (heapStart > 0 ? "Page memory is steady (" + heapStart + " -> " + heapEnd + " MB). " : "");
		if (b >= l && b >= d) {
			return trend + "Most spike time is the browser/GPU (garbage collection, GPU work or the device slowing down when hot).";
		}
		if (l >= d) {
			return trend + "Most spike time is game logic (server packets, entities, world updates).";
		}
		long[] spk = new long[PARTS];
		for (int m = Math.max(0, last - 4); m <= last; m++) {
			for (int k = 0; k < PARTS; k++) spk[k] += mPartSpike[m][k];
		}
		int big = 0;
		for (int k = 0; k < PARTS; k++) if (spk[k] > spk[big]) big = k;
		return trend + "Most spike time is drawing the frame, mostly " + PART_NAMES[big] + ".";
	}
}
