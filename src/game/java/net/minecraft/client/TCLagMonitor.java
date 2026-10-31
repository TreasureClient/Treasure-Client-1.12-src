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

	// parts of the logic time (game ticks), timed by the tick code and the network code
	public static final int L_CHUNK_PKTS = 0, L_ENTITY_PKTS = 1, L_OTHER_PKTS = 2, L_ENTITIES = 3, L_WORLD = 4,
			L_PARTICLES = 5, L_OTHER = 6, LPARTS = 7;
	private static final String[] LPART_NAMES = { "chunk packets", "entity packets", "other packets",
			"entity updates", "world updates", "particles", "other" };
	private static final long[] lcur = new long[LPARTS];
	private static final long[][] mLPartAll = new long[MINUTES][LPARTS];
	private static final long[][] mLPartSpike = new long[MINUTES][LPARTS];

	/** Adds the time since startNs to a part of this frame's logic. */
	public static void addLogic(int part, long startNs) {
		lcur[part] += EagRuntime.nanoTime() - startNs;
	}

	// time per server packet type: {total ns, count, ns in spike frames, ns this frame}
	private static final java.util.HashMap<Class<?>, long[]> pktTypes = new java.util.HashMap<Class<?>, long[]>();
	private static final java.util.ArrayList<long[]> pktTouched = new java.util.ArrayList<long[]>();

	/** Adds the time spent reading and handling one server packet to its type. */
	public static void addPacketType(Object p, long ns) {
		if (p == null) {
			return;
		}
		Class<?> c = p.getClass();
		long[] st = pktTypes.get(c);
		if (st == null) {
			st = new long[4];
			pktTypes.put(c, st);
		}
		st[0] += ns;
		st[1]++;
		if (st[3] == 0L) {
			pktTouched.add(st);
		}
		st[3] += ns + 1L; // +1 so a 0 ns packet still counts as touched
	}

	/** The packet types that took the most time (in spikes if there were any), slowest first. */
	private static String slowestPackets(boolean spikes) {
		java.util.ArrayList<java.util.Map.Entry<Class<?>, long[]>> list =
				new java.util.ArrayList<java.util.Map.Entry<Class<?>, long[]>>(pktTypes.entrySet());
		final int key = spikes ? 2 : 0;
		java.util.Collections.sort(list, new java.util.Comparator<java.util.Map.Entry<Class<?>, long[]>>() {
			public int compare(java.util.Map.Entry<Class<?>, long[]> a, java.util.Map.Entry<Class<?>, long[]> b) {
				return Long.compare(b.getValue()[key], a.getValue()[key]);
			}
		});
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < Math.min(3, list.size()); i++) {
			long[] st = list.get(i).getValue();
			if (st[key] <= 0L) break;
			String name = list.get(i).getKey().getSimpleName();
			if (name.startsWith("SPacket")) name = name.substring(7);
			if (sb.length() > 0) sb.append(" §7· ");
			sb.append("§f").append(name).append(" §7").append(st[key] / 1000000L).append("ms (")
					.append(st[1] > 0 ? st[0] / st[1] / 1000L : 0L).append("µs each)");
		}
		return sb.length() > 0 ? sb.toString() : "§7none";
	}

	/** Which logic part handling this server packet counts as. */
	public static int packetPart(Object p) {
		if (p instanceof net.minecraft.network.play.server.SPacketChunkData
				|| p instanceof net.minecraft.network.play.server.SPacketUnloadChunk
				|| p instanceof net.minecraft.network.play.server.SPacketBlockChange
				|| p instanceof net.minecraft.network.play.server.SPacketMultiBlockChange
				|| p instanceof net.minecraft.network.play.server.SPacketUpdateTileEntity
				|| p instanceof net.minecraft.network.play.server.SPacketBlockAction
				|| p instanceof net.minecraft.network.play.server.SPacketBlockBreakAnim
				|| p instanceof net.minecraft.network.play.server.SPacketExplosion) {
			return L_CHUNK_PKTS;
		}
		if (p instanceof net.minecraft.network.play.server.SPacketEntity
				|| p instanceof net.minecraft.network.play.server.SPacketEntityTeleport
				|| p instanceof net.minecraft.network.play.server.SPacketEntityVelocity
				|| p instanceof net.minecraft.network.play.server.SPacketEntityMetadata
				|| p instanceof net.minecraft.network.play.server.SPacketEntityHeadLook
				|| p instanceof net.minecraft.network.play.server.SPacketEntityEquipment
				|| p instanceof net.minecraft.network.play.server.SPacketEntityProperties
				|| p instanceof net.minecraft.network.play.server.SPacketEntityStatus
				|| p instanceof net.minecraft.network.play.server.SPacketEntityEffect
				|| p instanceof net.minecraft.network.play.server.SPacketRemoveEntityEffect
				|| p instanceof net.minecraft.network.play.server.SPacketDestroyEntities
				|| p instanceof net.minecraft.network.play.server.SPacketSpawnObject
				|| p instanceof net.minecraft.network.play.server.SPacketSpawnMob
				|| p instanceof net.minecraft.network.play.server.SPacketSpawnPlayer
				|| p instanceof net.minecraft.network.play.server.SPacketSpawnExperienceOrb
				|| p instanceof net.minecraft.network.play.server.SPacketSpawnPainting
				|| p instanceof net.minecraft.network.play.server.SPacketSetPassengers
				|| p instanceof net.minecraft.network.play.server.SPacketEntityAttach
				|| p instanceof net.minecraft.network.play.server.SPacketCollectItem
				|| p instanceof net.minecraft.network.play.server.SPacketAnimation) {
			return L_ENTITY_PKTS;
		}
		return L_OTHER_PKTS;
	}

	// parts of the terrain time with Celeritas, this frame: chunk builds, slowest single build,
	// uploads to the graphics chip, visibility search (ns)
	public static long tBuild, tBuildMax, tUpload, tGraph;

	/** Adds one chunk build job's time (Celeritas). */
	public static void addBuild(long startNs) {
		long d = EagRuntime.nanoTime() - startNs;
		tBuild += d;
		if (d > tBuildMax) tBuildMax = d;
	}

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
	// biggest drawing part, its ms, biggest logic part, its ms
	private static final int[][] ring = new int[RING][17];
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
		long ltimed = 0L;
		for (int k = 0; k < L_OTHER; k++) ltimed += lcur[k];
		lcur[L_OTHER] = Math.max(0L, logicNs - ltimed);
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
		for (int k = 0; k < LPARTS; k++) mLPartAll[minute][k] += lcur[k];
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
			int lbig = 0;
			for (int k = 0; k < LPARTS; k++) {
				mLPartSpike[minute][k] += lcur[k];
				if (lcur[k] > lcur[lbig]) lbig = k;
			}
			for (int k = 0, n = pktTouched.size(); k < n; k++) {
				long[] st = pktTouched.get(k);
				st[2] += st[3];
			}
			r[11] = lbig;
			r[12] = (int) (lcur[lbig] / 1000000L);
			r[13] = (int) (tBuild / 1000000L);
			r[14] = (int) (tBuildMax / 1000000L);
			r[15] = (int) (tUpload / 1000000L);
			r[16] = (int) (tGraph / 1000000L);
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
		for (int k = 0; k < LPARTS; k++) lcur[k] = 0L;
		for (int k = 0, n = pktTouched.size(); k < n; k++) pktTouched.get(k)[3] = 0L;
		pktTouched.clear();
		tBuild = tBuildMax = tUpload = tGraph = 0L;
	}

	private static void say(Minecraft mc, String s) {
		mc.ingameGUI.getChatGUI().printChatMessage(new TextComponentString(s));
	}

	private static int fps(int m) {
		return mMs[m] > 0 ? (int) (mFrames[m] * 1000L / mMs[m]) : 0;
	}

	/** Parts taking at least 5% of the time, biggest first: "terrain 54% · HUD 22%". */
	private static String bigParts(long[] sum, String[] names) {
		long all = 0L;
		for (int k = 0; k < sum.length; k++) all += sum[k];
		if (all <= 0L) return "§7none";
		boolean[] used = new boolean[sum.length];
		StringBuilder sb = new StringBuilder();
		for (int n = 0; n < sum.length; n++) {
			int big = -1;
			for (int k = 0; k < sum.length; k++) if (!used[k] && (big < 0 || sum[k] > sum[big])) big = k;
			used[big] = true;
			long pct = sum[big] * 100L / all;
			if (pct < 5L) break;
			if (sb.length() > 0) sb.append(" §7· ");
			sb.append("§f").append(names[big]).append(' ').append(pct).append('%');
		}
		return sb.toString();
	}

	public static void print(Minecraft mc) {
		if (start < 0L || curMinute < 0) {
			say(mc, "§6[Lag] §7no data yet");
			return;
		}
		int last = Math.min(curMinute, MINUTES - 1);
		int from = Math.max(0, last - 4);
		long frames = 0L, ms = 0L, l = 0, d = 0, b = 0;
		int spikes = 0, worst = 0;
		long[] spk = new long[PARTS], lspk = new long[LPARTS];
		for (int m = from; m <= last; m++) {
			frames += mFrames[m];
			ms += mMs[m];
			spikes += mSpikes[m];
			worst = Math.max(worst, mWorst[m]);
			l += mLogic[m];
			d += mDraw[m];
			b += mBrowser[m];
			for (int k = 0; k < PARTS; k++) spk[k] += mPartSpike[m][k];
			for (int k = 0; k < LPARTS; k++) lspk[k] += mLPartSpike[m][k];
		}
		say(mc, "§6[Lag] §f" + (last + 1) + " min §7· last 5 min: §f" + (ms > 0 ? frames * 1000L / ms : 0L)
				+ " fps, " + spikes + " spikes §7(over " + SPIKE_MS + " ms), worst §f" + worst + " ms");
		long sum = l + d + b;
		if (sum > 0) {
			say(mc, "§7Spikes: §flogic " + (l * 100 / sum) + "% §7· §fdrawing " + (d * 100 / sum)
					+ "% §7· §fbrowser/GPU " + (b * 100 / sum) + "%");
			say(mc, "§7Drawing: " + bigParts(spk, PART_NAMES));
			say(mc, "§7Logic: " + bigParts(lspk, LPART_NAMES));
			if (!pktTypes.isEmpty()) {
				say(mc, "§7Packets: " + slowestPackets(true));
			}
		}
		if (ringCount > 0) {
			say(mc, "§7Last spikes (logic / drawing / browser, ms):");
			for (int i = 0; i < Math.min(5, ringCount); i++) {
				int[] r = ring[(ringPos - 1 - i + RING * 2) % RING];
				say(mc, "§8" + r[0] + ":" + (r[1] < 10 ? "0" : "") + r[1] + " §f" + r[2] + "ms §7" + r[3] + "/" + r[4]
						+ "/" + r[5] + " · " + PART_NAMES[r[9]] + " " + r[10] + ", " + LPART_NAMES[r[11]] + " "
						+ r[12] + (r[7] > 0 ? " · " + r[7] + " chunks" : "")
						+ (r[13] + r[15] + r[16] > 0 ? " · build " + r[13] + " (max " + r[14] + ") upload " + r[15]
								+ " vis " + r[16] : ""));
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
			long[] lspk = new long[LPARTS];
			for (int m = Math.max(0, last - 4); m <= last; m++) {
				for (int k = 0; k < LPARTS; k++) lspk[k] += mLPartSpike[m][k];
			}
			int lbig = 0;
			for (int k = 0; k < LPARTS; k++) if (lspk[k] > lspk[lbig]) lbig = k;
			return trend + "Most spike time is game logic, mostly " + LPART_NAMES[lbig] + ".";
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
