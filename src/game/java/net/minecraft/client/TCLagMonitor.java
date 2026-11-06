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
			SKY = 6, HAND = 7, SETUP = 8, SCREENS = 9, OTHER = 10, PARTS = 11;
	private static final String[] PART_NAMES = { "terrain", "entities", "block ents", "name tags", "particles",
			"HUD", "sky", "hand", "camera setup", "menus/toasts", "other" };
	private static final long[] cur = new long[PARTS];
	private static final long[][] mPartAll = new long[MINUTES][PARTS]; // all frames, ns
	private static final long[][] mPartSpike = new long[MINUTES][PARTS]; // spike frames, ns

	// parts of the logic time (game ticks), timed by the tick code and the network code
	public static final int L_CHUNK_PKTS = 0, L_ENTITY_PKTS = 1, L_OTHER_PKTS = 2, L_ENTITIES = 3, L_WORLD = 4,
			L_PARTICLES = 5, L_OTHER = 6, L_SOUND = 7, L_MODS = 8, L_TEXTURES = 9, L_INPUT = 10, L_VIEW = 11,
			L_HUDTICK = 12, L_SCREEN = 13, L_NET = 14, L_LOADING = 15, LPARTS = 16;
	private static final String[] LPART_NAMES = { "chunk packets", "entity packets", "other packets",
			"entity updates", "world updates", "particles", "other", "sounds/music", "client mods",
			"texture animations", "input/clicks", "aim + camera", "HUD tick", "menus", "network",
			"warm-up loads" };
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
		addChannel(p, ns);
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

	// slowest world change: total and its parts (new world, renderer + load, player) in ns
	private static long wcTotal, wcWorld, wcLoad, wcPlayer;
	private static int wcCount;

	/** One world change (dimension switch) from handleRespawn. */
	public static void worldChange(long world, long load, long player) {
		wcCount++;
		long t = world + load + player;
		if (t > wcTotal) {
			wcTotal = t;
			wcWorld = world;
			wcLoad = load;
			wcPlayer = player;
		}
	}

	// sound files loaded while playing (reading + handing to the browser): count, total, slowest
	public static long sndLoads, sndLoadNs, sndSlowNs;
	public static String sndSlow;

	/** Adds one sound file load (SoundManager) to the stats. */
	public static void addSoundLoad(String name, long ns) {
		sndLoads++;
		sndLoadNs += ns;
		if (ns > sndSlowNs) {
			sndSlowNs = ns;
			sndSlow = name;
		}
	}

	// plugin message time per channel, so a slow CustomPayload can be traced to its sender
	private static final java.util.HashMap<String, long[]> chanTimes = new java.util.HashMap<String, long[]>();

	private static void addChannel(Object p, long ns) {
		if (!(p instanceof net.minecraft.network.play.server.SPacketCustomPayload)) {
			return;
		}
		String ch = ((net.minecraft.network.play.server.SPacketCustomPayload) p).getChannelName();
		long[] t = chanTimes.get(ch);
		if (t == null) {
			if (chanTimes.size() > 64) {
				return;
			}
			t = new long[1];
			chanTimes.put(ch, t);
		}
		t[0] += ns;
	}

	private static String slowestChannel() {
		String best = null;
		long bt = 0L;
		for (java.util.Map.Entry<String, long[]> e : chanTimes.entrySet()) {
			if (e.getValue()[0] > bt) {
				bt = e.getValue()[0];
				best = e.getKey();
			}
		}
		return best;
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
			if (name.equals("CustomPayload")) {
				String ch = slowestChannel();
				if (ch != null) name += " [" + ch + "]";
			}
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
	/** Time spent tracing entity culling rays this frame (ns). */
	public static long tCull;

	// parts of a HUD redraw this frame: hotbar, chat, scoreboard (ns); the rest of the HUD is "other"
	public static long hHotbar, hChat, hBoard, hBoss, hEffects, hTitle, hTab, hTotem;

	// why chunks get redrawn: light changes, single block changes, whole areas (chunk data, modern blocks)
	public static int mkLight, mkBlock, mkRange;
	private static int buildJobs;
	private static final long[] mLogicAll = new long[MINUTES];
	private static final long[] mDrawAll = new long[MINUTES];
	private static final int[] mBuilds = new int[MINUTES];
	private static final int[] mMkLight = new int[MINUTES];
	private static final int[] mMkBlock = new int[MINUTES];
	private static final int[] mMkRange = new int[MINUTES];

	/** Adds one chunk build job's time (Celeritas). */
	public static void addBuild(long startNs) {
		++buildJobs;
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
	// fastest run of a fixed CPU test in each minute (ns): when the device gets hot and slows
	// down, the same work takes longer, which tells heat apart from the game doing more work
	private static final int[] mCpu = new int[MINUTES];
	private static long lastCpuTest;
	public static int cpuSink;

	private static int cpuTest() {
		long t0 = EagRuntime.nanoTime();
		int x = 0x2545F491 ^ cpuSink;
		for (int i = 0; i < 300000; i++) {
			x ^= x << 13;
			x ^= x >>> 17;
			x ^= x << 5;
		}
		cpuSink += x;
		return (int) Math.min(Integer.MAX_VALUE, EagRuntime.nanoTime() - t0);
	}
	private static final long[] mMs = new long[MINUTES];
	private static final long[] mBrowser = new long[MINUTES];
	private static final long[] mLogic = new long[MINUTES];
	private static final long[] mDraw = new long[MINUTES];
	private static int heapNow = -1;

	// last spikes: minute, second, total, logic, draw, browser, packets, chunk rebuilds, heap,
	// biggest drawing part, its ms, biggest logic part, its ms
	private static final int RW = 27;
	private static final int[][] ring = new int[RING][RW];
	// the worst spike of the session, same layout as a ring entry
	private static final int[] worstRow = new int[RW];
	private static int worstMinute = -1;
	private static int ringPos, ringCount;

	// ---- frame time governor ----------------------------------------------------
	// Chunk building and sorting can wait a frame; a frame that runs past the screen refresh
	// can't (with VSync it costs a whole extra refresh: 60 fps becomes 30 for that frame).
	// So building only gets the time left in this frame.

	/** When this frame's game work started (EagRuntime.nanoTime), set by Minecraft.runGameLoop. */
	public static long frameStartNs;
	/** Game work this frame should stay under, leaving room for the browser and GPU. */
	private static final long FRAME_TARGET_NS = 12500000L;
	/** Drawing done after the chunk builder runs (the rest of the frame), smoothed. */
	private static long restEmaNs = 6000000L;
	private static long builderAtNs = -1L;

	/** How much time chunk building may use now without pushing this frame past the target (can be negative). */
	public static long buildHeadroomNs() {
		long start = frameStartNs;
		if (start == 0L) {
			return Long.MAX_VALUE;
		}
		long at = EagRuntime.nanoTime() - start;
		if (at < 0L || at > 1000000000L) {
			return Long.MAX_VALUE;
		}
		if (builderAtNs < 0L) {
			builderAtNs = at;
		}
		return FRAME_TARGET_NS - at - restEmaNs;
	}

	// ---- moving vs standing still, and why frames miss the screen refresh ----------
	private static final int MV_FRAMES = 0, MV_MS = 1, MV_WORK = 2, MV_GPU = 3, MV_GPUN = 4, MV_SLOW = 5,
			MV_SLOW_GAME = 6, MV_SLOW_GPU = 7, MV_TERRAIN = 8, MV_BUILD = 9, MV_VIS = 10, MV_ENT = 11, MV_HUD = 12,
			MV_LOGIC = 13, MV_CULL = 14, MV_BE = 15, MV_TAGS = 16, MV_PART = 17, MV_SKY = 18, MV_HAND = 19,
			MV_SCR = 20, MV_OTHER = 21, MV_E0 = 22, MV_C0 = 26, MV_N = 30;
	/** Entity drawing by kind this frame: players, armor stands (holograms), other mobs, everything else. */
	public static final long[] eNs = new long[4];
	public static final int[] eCnt = new int[4];
	private static final String[] E_NAMES = { "players", "armor stands", "mobs", "other" };

	/** Adds one entity's drawing time (RenderGlobal.renderEntities). */
	public static void addEntity(net.minecraft.entity.Entity e, long startNs) {
		int k = e instanceof net.minecraft.entity.player.EntityPlayer ? 0
				: e instanceof net.minecraft.entity.item.EntityArmorStand ? 1
						: e instanceof net.minecraft.entity.EntityLivingBase ? 2 : 3;
		eNs[k] += EagRuntime.nanoTime() - startNs;
		eCnt[k]++;
	}
	/** [0] standing still, [1] moving or turning; times in microseconds. Reset after each .lag. */
	private static final long[][] mv = new long[2][MV_N];
	private static float mvYaw, mvPitch;
	private static boolean mvHave;

	private static void addMoving(Minecraft mc, long total, long logicNs, long drawNs) {
		net.minecraft.client.entity.EntityPlayerSP p = mc.player;
		boolean moving = mvHave && (Math.abs(p.rotationYaw - mvYaw) > 0.05F || Math.abs(p.rotationPitch - mvPitch) > 0.05F
				|| Math.abs(p.posX - p.prevPosX) + Math.abs(p.posY - p.prevPosY) + Math.abs(p.posZ - p.prevPosZ) > 0.001D);
		mvYaw = p.rotationYaw;
		mvPitch = p.rotationPitch;
		mvHave = true;
		long[] a = mv[moving ? 1 : 0];
		long work = (logicNs + drawNs) / 1000L;
		int gpu = net.lax1dude.eaglercraft.internal.PlatformInput.tcGpuFrameUs();
		a[MV_FRAMES]++;
		a[MV_MS] += total;
		a[MV_WORK] += work;
		if (gpu >= 0) {
			a[MV_GPU] += gpu;
			a[MV_GPUN]++;
		}
		if (total > 20L) { // missed the 60 Hz refresh
			a[MV_SLOW]++;
			if (work > 15000L) {
				a[MV_SLOW_GAME]++;
			} else if (gpu > 15000) {
				a[MV_SLOW_GPU]++;
			}
		}
		a[MV_TERRAIN] += cur[TERRAIN] / 1000L;
		a[MV_BUILD] += tBuild / 1000L;
		a[MV_VIS] += tGraph / 1000L;
		// block entities and name tags are drawn inside the entity pass (already counted in it)
		a[MV_ENT] += cur[ENTITIES] / 1000L;
		a[MV_BE] += cur[BLOCK_ENTS] / 1000L;
		a[MV_CULL] += tCull / 1000L;
		a[MV_HUD] += cur[HUD] / 1000L;
		a[MV_LOGIC] += logicNs / 1000L;
		// the rest of the frame, so the parts add up to the whole game work
		a[MV_TAGS] += cur[NAME_TAGS] / 1000L;
		a[MV_PART] += cur[PARTICLES] / 1000L;
		a[MV_SKY] += (cur[SKY] + cur[SETUP]) / 1000L;
		a[MV_HAND] += cur[HAND] / 1000L;
		a[MV_SCR] += cur[SCREENS] / 1000L;
		a[MV_OTHER] += cur[OTHER] / 1000L;
		for (int k = 0; k < 4; k++) {
			a[MV_E0 + k] += eNs[k] / 1000L;
			a[MV_C0 + k] += eCnt[k];
			eNs[k] = 0L;
			eCnt[k] = 0;
		}
	}

	private static void printMoving(Minecraft mc) {
		for (int i = 1; i >= 0; i--) {
			long[] a = mv[i];
			long f = a[MV_FRAMES];
			if (f < 30L) {
				continue;
			}
			say(mc, "§7" + (i == 1 ? "Moving/turning" : "Standing still") + ": §f" + (a[MV_MS] > 0L ? f * 1000L / a[MV_MS] : 0L)
					+ " fps §7· game work §f" + ms1(a[MV_WORK] / f) + "ms §7(logic " + ms1(a[MV_LOGIC] / f) + ", terrain "
					+ ms1(a[MV_TERRAIN] / f) + " [builds " + ms1(a[MV_BUILD] / f) + ", visibility " + ms1(a[MV_VIS] / f)
					+ "], entities " + ms1(a[MV_ENT] / f) + " [block ents " + ms1(a[MV_BE] / f) + ", culling "
					+ ms1(a[MV_CULL] / f) + "], HUD " + ms1(a[MV_HUD] / f) + ") · GPU §f"
					+ (a[MV_GPUN] > 0L ? ms1(a[MV_GPU] / a[MV_GPUN]) + "ms" : "not measurable"));
			say(mc, "§7  rest of the work: name tags " + ms1(a[MV_TAGS] / f) + ", particles " + ms1(a[MV_PART] / f)
					+ ", sky+camera " + ms1(a[MV_SKY] / f) + ", hand " + ms1(a[MV_HAND] / f) + ", menus/chat "
					+ ms1(a[MV_SCR] / f) + ", other " + ms1(a[MV_OTHER] / f));
			StringBuilder eb = new StringBuilder("§7  entities by kind (each incl. its name tag):");
			for (int k = 0; k < 4; k++) {
				if (a[MV_C0 + k] > 0L) {
					eb.append(' ').append(E_NAMES[k]).append(' ').append(a[MV_C0 + k] / f).append(" = §f")
							.append(ms1(a[MV_E0 + k] / f)).append("ms§7");
				}
			}
			say(mc, eb.toString());
			long slow = a[MV_SLOW];
			long other = Math.max(0L, slow - a[MV_SLOW_GAME] - a[MV_SLOW_GPU]);
			say(mc, "§7  missed refreshes: §f" + (slow * 100L / f) + "% §7of frames (because of game work "
					+ (a[MV_SLOW_GAME] * 100L / f) + "%, GPU " + (a[MV_SLOW_GPU] * 100L / f) + "%, browser/other "
					+ (other * 100L / f) + "%)");
		}
		for (int i = 0; i < 2; i++) {
			for (int k = 0; k < MV_N; k++) mv[i][k] = 0L;
		}
	}

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
		if (builderAtNs >= 0L) {
			long rest = logicNs + drawNs - builderAtNs - tBuild; // drawing after the builder, not the building itself
			if (rest >= 0L && rest < 200000000L) {
				restEmaNs += (rest - restEmaNs) / 8L;
			}
			builderAtNs = -1L;
		}
		reportWindow(now, logicNs);
		Minecraft tcMc = Minecraft.getMinecraft();
		if (total > 2000L) {
			// the tab was hidden or the computer slept (53 s once): not lag, and it ruined the averages
			skipFrame = false;
			clearParts();
			return;
		}
		if (tcMc.world == null || tcMc.player == null || skipFrame) {
			skipFrame = false;
			// menus and the login handshake (which waits for the server) aren't gameplay
			clearParts();
			return;
		}
		// entities were timed including the block entities and name tags inside them
		cur[ENTITIES] = Math.max(0L, cur[ENTITIES] - cur[BLOCK_ENTS] - cur[NAME_TAGS]);
		long timed = 0L;
		for (int k = 0; k < OTHER; k++) timed += cur[k];
		cur[OTHER] = Math.max(0L, drawNs - timed);
		long ltimed = 0L;
		for (int k = 0; k < LPARTS; k++) if (k != L_OTHER) ltimed += lcur[k];
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
		if (now - lastCpuTest > 5000L) {
			lastCpuTest = now;
			int c = cpuTest();
			if (mCpu[minute] == 0 || c < mCpu[minute]) mCpu[minute] = c;
		}
		mMs[minute] += total;
		mLogicAll[minute] += logicNs;
		mDrawAll[minute] += drawNs;
		mBuilds[minute] += buildJobs;
		mMkLight[minute] += mkLight;
		mMkBlock[minute] += mkBlock;
		mMkRange[minute] += mkRange;
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
			r[17] = (int) (hHotbar / 1000000L);
			r[18] = (int) (hChat / 1000000L);
			r[19] = (int) (hBoard / 1000000L);
			r[20] = (int) (hBoss / 1000000L);
			r[21] = (int) (hEffects / 1000000L);
			r[22] = (int) (hTitle / 1000000L);
			r[23] = (int) (hTab / 1000000L);
			r[26] = (int) (hTotem / 1000000L);
			// second biggest logic part, so "other" isn't the only name shown
			int l2 = lbig == 0 ? 1 : 0;
			for (int k = 0; k < LPARTS; k++) if (k != lbig && lcur[k] > lcur[l2]) l2 = k;
			r[24] = l2;
			r[25] = (int) (lcur[l2] / 1000000L);
			if (r[2] > worstRow[2] || worstMinute < 0 || minute - worstMinute > 4) {
				System.arraycopy(r, 0, worstRow, 0, RW);
				worstMinute = minute;
			}
			ringPos = (ringPos + 1) % RING;
			if (ringCount < RING) ringCount++;
		}
		addMoving(tcMc, total, logicNs, drawNs);
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
		tBuild = tBuildMax = tUpload = tGraph = tCull = 0L;
		for (int k = 0; k < 4; k++) {
			eNs[k] = 0L;
			eCnt[k] = 0;
		}
		buildJobs = mkLight = mkBlock = mkRange = 0;
		hHotbar = hChat = hBoard = hBoss = hEffects = hTitle = hTab = hTotem = 0L;
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

	/** The frame that printed the .lag report itself (writing it to chat isn't gameplay lag). */
	private static boolean skipFrame;

	/** Leave this frame out of the report (logging in to a server waits for the server, not the game). */
	public static void skipThisFrame() {
		skipFrame = true;
	}

	public static void print(Minecraft mc) {
		skipFrame = true;
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
			if (wcCount > 0) {
				say(mc, "§7World changes: §f" + wcCount + "§7, slowest §f" + (wcTotal / 1000000L) + "ms §7(new world "
						+ (wcWorld / 1000000L) + ", renderer " + (wcLoad / 1000000L) + ", player " + (wcPlayer / 1000000L) + ")");
			}
			if (sndLoads > 0) {
				String sn = sndSlow == null ? "?" : sndSlow;
				int sl = sn.lastIndexOf('/');
				if (sl >= 0) sn = sn.substring(sl + 1);
				say(mc, "§7Sound loads: §f" + sndLoads + " §7taking §f" + (sndLoadNs / 1000000L) + "ms§7, slowest §f" + sn
						+ " " + (sndSlowNs / 1000000L) + "ms");
			}
		}
		say(mc, "§7Shader compiles on demand: §f" + net.lax1dude.eaglercraft.opengl.FixedFunctionPipeline.tcCompiles
				+ " §7taking §f" + (net.lax1dude.eaglercraft.opengl.FixedFunctionPipeline.tcCompileNs / 1000000L)
				+ "ms§7, slowest §f" + (net.lax1dude.eaglercraft.opengl.FixedFunctionPipeline.tcSlowNs / 1000000L)
				+ "ms §7(warmed on menus: §f" + net.lax1dude.eaglercraft.opengl.FixedFunctionPipeline.tcWarmed + "§7)");
		if (ringCount > 0) {
			say(mc, "§7Last spikes (logic / drawing / browser, ms):");
			for (int i = 0; i < Math.min(3, ringCount); i++) {
				say(mc, row(ring[(ringPos - 1 - i + RING * 2) % RING]));
			}
		}
		if (worstMinute >= 0 && worstMinute >= from) {
			say(mc, "§7Worst spike: " + row(worstRow));
		}
		if (frames > 0) {
			// every frame, not only spikes: shows why the game is slow when it is slow all the time
			long la = 0L, da = 0L;
			int bj = 0, ml = 0, mb = 0, mr = 0;
			long[] all = new long[PARTS], lall = new long[LPARTS];
			for (int m = from; m <= last; m++) {
				la += mLogicAll[m];
				da += mDrawAll[m];
				bj += mBuilds[m];
				ml += mMkLight[m];
				mb += mMkBlock[m];
				mr += mMkRange[m];
				for (int k = 0; k < PARTS; k++) all[k] += mPartAll[m][k];
				for (int k = 0; k < LPARTS; k++) lall[k] += mLPartAll[m][k];
			}
			long frameUs = ms * 1000L / frames, lUs = la / 1000L / frames, dUs = da / 1000L / frames;
			say(mc, "§7Every frame: §f" + ms1(frameUs) + "ms §7(logic §f" + ms1(lUs) + "§7, drawing §f" + ms1(dUs)
					+ "§7, browser/GPU §f" + ms1(Math.max(0L, frameUs - lUs - dUs)) + "§7)");
			say(mc, "§7  drawing: " + bigParts(all, PART_NAMES) + " §7· logic: " + bigParts(lall, LPART_NAMES));
			long mins = Math.max(1L, ms / 60000L);
			say(mc, "§7  chunk redraws: §f" + (bj / mins) + "/min §7(asked by light " + (ml / mins) + ", blocks "
					+ (mb / mins) + ", areas " + (mr / mins) + ")");
		}
		int speed = cpuSpeed(last);
		int first = -1;
		for (int m = 0; m <= last; m++) {
			if (mFrames[m] > 0) {
				first = m;
				break;
			}
		}
		if (speed > 0 && first >= 0 && mFrames[last] > 0) {
			long early = (mLogicAll[first] + mDrawAll[first]) / 1000L / mFrames[first];
			// the last 5 minutes, not only the current one (which may have just started with a world load)
			long nowW = 0L, nf = 0L;
			for (int m = Math.max(first + 1, last - 4); m <= last; m++) {
				nowW += (mLogicAll[m] + mDrawAll[m]) / 1000L;
				nf += mFrames[m];
			}
			if (nf > 0) {
				nowW /= nf;
				say(mc, "§7Device speed: §f" + speed + "% §7of its best (heat slows it down) · game work per frame: §f"
						+ ms1(early) + " §7(min " + (first + 1) + ") → §f" + ms1(nowW) + "ms §7(last 5 min)");
			} else {
				// only one minute of play so far: nothing to compare with yet
				say(mc, "§7Device speed: §f" + speed + "% §7of its best (heat slows it down) · game work per frame: §f"
						+ ms1(early) + "ms §7(first minute)");
			}
		}
		printMoving(mc);
		say(mc, "§6[Lag] §7" + verdict(last));
	}

	private static String ms1(long us) {
		return (us / 1000L) + "." + ((us / 100L) % 10L);
	}

	private static String row(int[] r) {
		StringBuilder hud = new StringBuilder();
		if (r[9] == HUD && r[10] >= 8) {
			hud.append(" (hotbar ").append(r[17]).append(" chat ").append(r[18]).append(" board ").append(r[19]);
			if (r[20] > 0) hud.append(" boss bar ").append(r[20]);
			if (r[21] > 0) hud.append(" effects ").append(r[21]);
			if (r[22] > 0) hud.append(" title ").append(r[22]);
			if (r[23] > 0) hud.append(" tab ").append(r[23]);
			if (r[26] > 0) hud.append(" totem ").append(r[26]);
			hud.append(')');
		}
		return "§8" + r[0] + ":" + (r[1] < 10 ? "0" : "") + r[1] + " §f" + r[2] + "ms §7" + r[3] + "/" + r[4]
				+ "/" + r[5] + " · " + PART_NAMES[r[9]] + " " + r[10] + hud
				+ ", " + LPART_NAMES[r[11]] + " " + r[12]
				+ (r[25] > 0 ? ", " + LPART_NAMES[r[24]] + " " + r[25] : "")
				+ (r[7] > 0 ? " · " + r[7] + " chunks" : "")
				+ (r[13] + r[15] + r[16] > 0 ? " · build " + r[13] + " (max " + r[14] + ") upload " + r[15]
						+ " vis " + r[16] : "");
	}

	/** CPU test speed in the last minute compared to the fastest minute (100 = no slowdown). */
	private static int cpuSpeed(int last) {
		int best = 0, cur = 0;
		for (int m = 0; m <= last; m++) {
			if (mCpu[m] > 0) {
				if (best == 0 || mCpu[m] < best) best = mCpu[m];
				cur = mCpu[m];
			}
		}
		return cur > 0 ? (int) ((long) best * 100L / cur) : 0;
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
		int speed = cpuSpeed(last);
		String heat = speed > 0 && speed < 80
				? "The device slowed to " + speed + "% speed (likely heat), so everything takes longer. " : "";
		String trend = heat + ((heapStart > 0 && heapEnd > heapStart * 3 / 2)
				? "Page memory grew from " + heapStart + " to " + heapEnd + " MB. "
				: (heapStart > 0 ? "Page memory is steady (" + heapStart + " -> " + heapEnd + " MB). " : ""));
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
