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

	// textures loaded on first use while playing (font sheets, GUI images, ...): count, total, slowest
	public static long texLoads, texLoadNs, texSlowNs;
	public static String texSlow;

	/** Adds one texture loaded on first use (TextureManager.bindTexture) while in a world. */
	public static void addTextureLoad(String name, long ns) {
		texLoads++;
		texLoadNs += ns;
		if (ns > texSlowNs) {
			texSlowNs = ns;
			texSlow = name;
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
		return slowestPackets(spikes, 3);
	}

	private static String slowestPackets(boolean spikes, int max) {
		java.util.ArrayList<java.util.Map.Entry<Class<?>, long[]>> list =
				new java.util.ArrayList<java.util.Map.Entry<Class<?>, long[]>>(pktTypes.entrySet());
		final int key = spikes ? 2 : 0;
		java.util.Collections.sort(list, new java.util.Comparator<java.util.Map.Entry<Class<?>, long[]>>() {
			public int compare(java.util.Map.Entry<Class<?>, long[]> a, java.util.Map.Entry<Class<?>, long[]> b) {
				return Long.compare(b.getValue()[key], a.getValue()[key]);
			}
		});
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < Math.min(max, list.size()); i++) {
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
	/** Builds that only re-sorted see-through blocks (water, glass) for the camera. */
	public static int sortJobs;
	private static final int[] mSorts = new int[MINUTES];
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
	// full browser memory cleanups (garbage collections) per minute, and how many landed in a spike
	private static final int[] mGc = new int[MINUTES];
	private static final int[] mGcSpike = new int[MINUTES];
	private static int gcLast = -2;
	private static final long[] mLogic = new long[MINUTES];
	private static final long[] mDraw = new long[MINUTES];
	private static int heapNow = -1;

	// last spikes: minute, second, total, logic, draw, browser, packets, chunk rebuilds, heap,
	// biggest drawing part, its ms, biggest logic part, its ms
	// r[27] = 1 when the browser ran a full memory cleanup during that frame
	private static final int RW = 28;
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
		return TCFramePacer.workTargetNs() - at - restEmaNs;
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

	// players drawn, part by part (body, armor, armor shine, held item, cape/elytra, other layers)
	private static final long[] pNs = new long[6];
	public static long pRenders;
	private static final String[] P_NAMES = { "body", "armor", "armor shine", "held item", "cape/elytra",
			"other layers" };

	public static void playerPart(int part, long startNs) {
		pNs[part] += EagRuntime.nanoTime() - startNs;
	}

	/** "Each player: body 0.4, armor 1.2 (shine 0.6), ..." in ms per player drawn; null if none were. */
	private static String playerParts() {
		if (pRenders < 30L) {
			return null;
		}
		StringBuilder sb = new StringBuilder("§7  each player drawn (");
		sb.append(pRenders).append(" times): ");
		for (int i = 0; i < pNs.length; i++) {
			if (i == 2) continue;
			if (i > 0) sb.append(", ");
			sb.append(P_NAMES[i]).append(" §f").append(ms2(pNs[i] / pRenders)).append("§7");
			if (i == 1) {
				sb.append(" (shine §f").append(ms2(pNs[2] / pRenders)).append("§7)");
			}
		}
		for (int i = 0; i < pNs.length; i++) pNs[i] = 0L;
		pRenders = 0L;
		return sb.append(" ms").toString();
	}

	private static String ms2(long ns) {
		long v = ns / 10000L; // hundredths of a ms
		return (v / 100L) + "." + (v % 100L < 10L ? "0" : "") + (v % 100L);
	}

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
		glDraws += net.lax1dude.eaglercraft.opengl.EaglercraftGPU.tcDraws;
		glAttribs += net.lax1dude.eaglercraft.opengl.EaglercraftGPU.tcAttribSets;
		glTerrain += net.lax1dude.eaglercraft.opengl.EaglercraftGPU.tcTerrainDraws;
		glMulti += net.lax1dude.eaglercraft.opengl.EaglercraftGPU.tcMultiDraws;
		glParts += net.lax1dude.eaglercraft.opengl.EaglercraftGPU.tcMultiParts;
		glUp += net.lax1dude.eaglercraft.internal.PlatformOpenGL.tcUpBytes;
		glFrames++;
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
			StringBuilder eb = new StringBuilder("§7  entities by kind (name tags are drawn together, see name tags):");
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
		String pp = playerParts();
		if (pp != null) say(mc, pp);
		String et = entityTicks();
		if (et != null) say(mc, et);
		for (int i = 0; i < 2; i++) {
			for (int k = 0; k < MV_N; k++) mv[i][k] = 0L;
		}
	}

	/** Called once per frame from Minecraft.runGameLoop with the time of each part in nanoseconds. */
	public static void frame(long logicNs, long drawNs, long browserNs) {
		try {
			frame0(logicNs, drawNs, browserNs);
		} finally {
			frameEndGl();
		}
	}

	private static void frame0(long logicNs, long drawNs, long browserNs) {
		long now = EagRuntime.steadyTimeMillis();
		growCheck(now);
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
		mSorts[minute] += sortJobs;
		mMkLight[minute] += mkLight;
		mMkBlock[minute] += mkBlock;
		mMkRange[minute] += mkRange;
		mHeap[minute] = heapNow;
		for (int k = 0; k < PARTS; k++) mPartAll[minute][k] += cur[k];
		for (int k = 0; k < LPARTS; k++) mLPartAll[minute][k] += lcur[k];
		int logic = (int) (logicNs / 1000000L), draw = (int) (drawNs / 1000000L);
		int browser = (int) Math.max(0L, total - logic - draw);
		int gcNow = net.lax1dude.eaglercraft.EagRuntime.tcGcCount();
		boolean gcFrame = gcLast >= 0 && gcNow > gcLast;
		gcLast = gcNow;
		if (gcFrame) mGc[minute]++;
		if (total >= SPIKE_MS) {
			if (gcFrame) mGcSpike[minute]++;
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
			r[27] = gcFrame ? 1 : 0;
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
		addSecond(now, total, logicNs, drawNs);
		TCFramePacer.frame(now, total);
		TCSteadyFps.frame(now, total);
		addMoving(tcMc, total, logicNs, drawNs);
		clearParts();
	}

	/** Treasure Client: WebGL counters start fresh every frame, including skipped ones. */
	private static void frameEndGl() {
		resetGlCounters();
	}

	// ---- slow seconds: FPS dips made of many medium frames (none over the 60 ms spike limit) ----
	// A second at 30 fps is 30 frames of 33 ms: no single spike, so the spike list never showed it.
	// Each second is summed; a second under 50 fps is kept with where its time went.
	private static final int SS_N = 17, SS_KEEP = 6;
	// frames, ms, worst frame ms, logic, drawing, browser, terrain, builds, uploads, visibility,
	// entities (with block entities and name tags), HUD, chunk packets, other packets, entity updates,
	// chunks built, second of the session  (times in microseconds)
	private static final long[] ssCur = new long[SS_N];
	private static final long[][] ssRing = new long[SS_KEEP][SS_N];
	private static int ssPos, ssCount;
	private static long ssSecond = -1L;

	private static void addSecond(long now, long total, long logicNs, long drawNs) {
		long sec = (now - start) / 1000L;
		if (sec != ssSecond) {
			long f = ssCur[0];
			if (ssSecond >= 0L && f >= 5L && f < 50L && ssCur[1] >= 700L) {
				long[] r = ssRing[ssPos];
				System.arraycopy(ssCur, 0, r, 0, SS_N);
				r[16] = ssSecond;
				ssPos = (ssPos + 1) % SS_KEEP;
				if (ssCount < SS_KEEP) ssCount++;
			}
			java.util.Arrays.fill(ssCur, 0L);
			ssSecond = sec;
		}
		long[] c = ssCur;
		c[0]++;
		c[1] += total;
		if (total > c[2]) c[2] = total;
		c[3] += logicNs / 1000L;
		c[4] += drawNs / 1000L;
		c[5] += Math.max(0L, total * 1000L - (logicNs + drawNs) / 1000L);
		c[6] += cur[TERRAIN] / 1000L;
		c[7] += tBuild / 1000L;
		c[8] += tUpload / 1000L;
		c[9] += tGraph / 1000L;
		c[10] += (cur[ENTITIES] + cur[BLOCK_ENTS] + cur[NAME_TAGS]) / 1000L;
		c[11] += cur[HUD] / 1000L;
		c[12] += lcur[L_CHUNK_PKTS] / 1000L;
		c[13] += (lcur[L_ENTITY_PKTS] + lcur[L_OTHER_PKTS]) / 1000L;
		c[14] += lcur[L_ENTITIES] / 1000L;
		c[15] += buildJobs;
	}

	// ---- map chunk loads: where each SPacketChunkData's handling time goes ----
	public static final int CK_NEW = 0, CK_UNPACK = 1, CK_GUESS = 2, CK_MARK = 3, CK_TILES = 4, CK_TUFF = 5, CK_N = 6;
	private static final String[] CK_NAMES = { "new chunk", "unpack", "block guessing", "redraw marks",
			"block entities", "TuffX blocks" };
	private static final long[] ckSum = new long[CK_N], ckWorst = new long[CK_N];
	private static long ckWorstTotal;
	private static int ckCount;

	private static String beSlowName;
	private static long beSlowNs;

	// entity updates (World.updateEntities, once per game tick) by kind, for .lag full
	public static final int U_YOU = 0, U_PLAYERS = 1, U_STANDS = 2, U_MOBS = 3, U_ITEMS = 4, U_OTHER = 5,
			U_BLOCK_ENTS = 6;
	private static final String[] U_NAMES = { "you", "other players", "armor stands", "mobs", "items/xp",
			"other entities", "block entities" };
	private static final long[] uNs = new long[7];
	private static final long[] uCnt = new long[7];
	private static long uTicks;
	private static String uSlowName;
	private static long uSlowNs;

	/** One entity or block entity updated; kind is one of the U_ constants. */
	public static void entityTick(Object e, int kind, long startNs) {
		long d = EagRuntime.nanoTime() - startNs;
		uNs[kind] += d;
		uCnt[kind]++;
		if (d > uSlowNs) {
			uSlowNs = d;
			uSlowName = e.getClass().getSimpleName();
		}
	}

	/** One game tick of entity updates done. */
	public static void entityTickRound() {
		uTicks++;
	}

	/** "entity updates per tick: you 0.40, armor stands 0.30 (9), ..." in ms; null if too few ticks. */
	private static String entityTicks() {
		if (uTicks < 20L) {
			return null;
		}
		StringBuilder sb = new StringBuilder("§7  entity updates per game tick (").append(uTicks).append(" ticks):");
		boolean any = false;
		for (int i = 0; i < uNs.length; i++) {
			if (uCnt[i] == 0L) continue;
			sb.append(any ? ", " : " ").append(U_NAMES[i]).append(" §f").append(ms2(uNs[i] / uTicks)).append("§7");
			if (i != U_YOU) sb.append(" (").append((uCnt[i] + uTicks / 2L) / uTicks).append(')');
			any = true;
		}
		sb.append(" ms");
		if (uSlowName != null) {
			sb.append(" · slowest single update: §f").append(uSlowName).append(' ').append(ms1(uSlowNs / 1000L))
					.append("ms§7");
		}
		for (int i = 0; i < uNs.length; i++) {
			uNs[i] = 0L;
			uCnt[i] = 0L;
		}
		uTicks = 0L;
		uSlowName = null;
		uSlowNs = 0L;
		return sb.toString();
	}

	/** One block entity read from a map chunk (finds which kind is slow to load). */
	public static void blockEntityLoad(Object te, long ns) {
		if (te != null && ns > beSlowNs) {
			beSlowNs = ns;
			beSlowName = te.getClass().getSimpleName();
		}
	}

	/** One chunk packet handled; steps in ns. */
	public static void chunkLoad(long[] steps) {
		long t = 0L;
		for (int i = 0; i < CK_N; i++) {
			ckSum[i] += steps[i];
			t += steps[i];
		}
		ckCount++;
		if (t > ckWorstTotal) {
			ckWorstTotal = t;
			System.arraycopy(steps, 0, ckWorst, 0, CK_N);
		}
	}

	private static String ckParts(long[] a, long div) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < CK_N; i++) {
			if (sb.length() > 0) sb.append(", ");
			sb.append(CK_NAMES[i]).append(' ').append(ms1(a[i] / 1000L / div));
		}
		return sb.toString();
	}

	// slowest menu update (its screen's name), so a multi-second "menus" spike says which menu it was
	private static String scrSlowName;
	private static long scrSlowNs;
	private static int scrSlowSec;

	public static void screenTime(Object screen, long startNs) {
		if (screen == null) {
			return;
		}
		if (screen instanceof net.minecraft.client.multiplayer.GuiConnecting) {
			skipThisFrame(); // joining a server waits for the server's answer: not the game lagging
			return;
		}
		if (skipFrame) {
			return; // a screenshot or the .lag report: not the menu being slow
		}
		long ns = now() - startNs;
		if (ns > scrSlowNs && ns > 20000000L) {
			scrSlowNs = ns;
			scrSlowName = screen.getClass().getSimpleName();
			scrSlowSec = start < 0L ? 0 : (int) ((EagRuntime.steadyTimeMillis() - start) / 1000L);
		}
	}

	private static long sumOf(long[] a) {
		long t = 0L;
		for (int i = 0; i < a.length; i++) t += a[i];
		return t;
	}

	private static void printSlowSeconds(Minecraft mc) {
		if (scrSlowName != null) {
			say(mc, "§7Slowest menu: §f" + scrSlowName + " " + (scrSlowNs / 1000000L) + "ms §7at "
					+ (scrSlowSec / 60) + ":" + (scrSlowSec % 60 < 10 ? "0" : "") + (scrSlowSec % 60));
		}
		if (ckCount > 0) {
			say(mc, "§7Map chunks loaded: §f" + ckCount + "§7, average §f" + ms1(sumOf(ckSum) / 1000L / ckCount)
					+ "ms §7(" + ckParts(ckSum, ckCount) + ") · slowest §f" + ms1(ckWorstTotal / 1000L) + "ms §7("
					+ ckParts(ckWorst, 1L) + ")");
			if (beSlowName != null) {
				say(mc, "§7  slowest block entity to load: §f" + beSlowName + " " + ms1(beSlowNs / 1000L) + "ms");
			}
		}
		if (!pktTypes.isEmpty()) {
			say(mc, "§7Slowest packets: " + slowestPackets(true) + " §7· overall: " + slowestPackets(false));
		}
		if (ssCount == 0) {
			say(mc, "§7Slow seconds (under 50 fps): §fnone recorded");
			return;
		}
		say(mc, "§7Slow seconds (under 50 fps), newest first, ms per frame:");
		for (int i = 0; i < Math.min(4, ssCount); i++) {
			long[] r = ssRing[(ssPos - 1 - i + SS_KEEP * 2) % SS_KEEP];
			long f = Math.max(1L, r[0]);
			long sec = r[16];
			say(mc, "§f " + (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60) + " " + r[0] + " fps §7(worst "
					+ r[2] + "ms): logic §f" + ms1(r[3] / f) + "§7 [chunk pkts " + ms1(r[12] / f) + ", other pkts "
					+ ms1(r[13] / f) + ", entity upd " + ms1(r[14] / f) + "] · terrain §f" + ms1(r[6] / f)
					+ "§7 [builds " + ms1(r[7] / f) + ", uploads " + ms1(r[8] / f) + ", visibility " + ms1(r[9] / f)
					+ ", " + r[15] + " chunks] · entities §f" + ms1(r[10] / f) + "§7 · HUD " + ms1(r[11] / f)
					+ " · other drawing " + ms1(Math.max(0L, r[4] - r[6] - r[10] - r[11]) / f)
					+ " · browser/GPU §f" + ms1(r[5] / f));
		}
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
		buildJobs = sortJobs = mkLight = mkBlock = mkRange = 0;
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

	/** ".lag": a short report that fits in the open chat. ".lag full" prints everything. */
	public static void print(Minecraft mc) {
		skipFrame = true;
		if (start < 0L || curMinute < 0) {
			say(mc, "§6[Lag] §7no data yet");
			return;
		}
		int last = Math.min(curMinute, MINUTES - 1);
		int from = Math.max(0, last - 4);
		long frames = 0L, ms = 0L, l = 0, d = 0, b = 0, la = 0L, da = 0L;
		int spikes = 0, worst = 0;
		for (int m = from; m <= last; m++) {
			frames += mFrames[m];
			ms += mMs[m];
			spikes += mSpikes[m];
			worst = Math.max(worst, mWorst[m]);
			l += mLogic[m];
			d += mDraw[m];
			b += mBrowser[m];
			la += mLogicAll[m];
			da += mDrawAll[m];
		}
		int speed = cpuSpeed(last);
		int heap = mHeap[last];
		say(mc, "§6[Lag] §f" + (last + 1) + " min §7· §f" + (ms > 0 ? frames * 1000L / ms : 0L) + " fps §7· §f" + spikes
				+ " §7spikes, worst §f" + worst + "ms" + (speed > 0 ? " §7· speed §f" + speed + "%" : "")
				+ (heap > 0 ? " §7· §f" + heap + " MB" : ""));
		if (frames > 0) {
			long frameUs = ms * 1000L / frames, lUs = la / 1000L / frames, dUs = da / 1000L / frames;
			String sp = "";
			long sum = l + d + b;
			if (sum > 0) {
				sp = " §7· spikes: logic " + (l * 100 / sum) + "% draw " + (d * 100 / sum) + "% browser "
						+ (b * 100 / sum) + "%";
			}
			say(mc, "§7Frame §f" + ms1(frameUs) + "ms §7(logic " + ms1(lUs) + ", draw " + ms1(dUs) + ", browser "
					+ ms1(Math.max(0L, frameUs - lUs - dUs)) + ")" + sp);
			say(mc, TCFramePacer.describe() + " §7· " + TCSteadyFps.describe());
			long[] all = new long[PARTS], lall = new long[LPARTS];
			int bj = 0, sj = 0;
			for (int m = from; m <= last; m++) {
				bj += mBuilds[m];
				sj += mSorts[m];
				for (int k = 0; k < PARTS; k++) all[k] += mPartAll[m][k];
				for (int k = 0; k < LPARTS; k++) lall[k] += mLPartAll[m][k];
			}
			say(mc, "§7Draw: " + topParts(all, PART_NAMES, 3) + " §7· Logic: " + topParts(lall, LPART_NAMES, 2)
					+ " §7· §f" + (bj / Math.max(1L, ms / 60000L)) + "§7 chunk builds/min (§f"
					+ (sj / Math.max(1L, ms / 60000L)) + "§7 water re-sorts)");
			String gl = glLine();
			if (gl != null) say(mc, gl);
		}
		printMovingShort(mc);
		if (worstMinute >= 0 && worstMinute >= from) {
			say(mc, "§7Worst " + shortRow(worstRow));
		}
		if (ringCount > 0) {
			say(mc, "§7Last " + shortRow(ring[(ringPos - 1 + RING * 2) % RING]));
		}
		if (scrSlowName != null) {
			say(mc, "§7Slowest menu: §f" + scrSlowName + " " + (scrSlowNs / 1000000L) + "ms §7at " + (scrSlowSec / 60)
					+ ":" + (scrSlowSec % 60 < 10 ? "0" : "") + (scrSlowSec % 60));
		}
		if (ckCount > 0) {
			int big = 0;
			for (int i = 1; i < CK_N; i++) if (ckWorst[i] > ckWorst[big]) big = i;
			say(mc, "§7Map chunks §f" + ckCount + "§7: avg " + ms1(sumOf(ckSum) / 1000L / ckCount) + "ms, worst §f"
					+ ms1(ckWorstTotal / 1000L) + "ms §7(" + CK_NAMES[big] + " " + ms1(ckWorst[big] / 1000L) + ")");
		}
		if (!pktTypes.isEmpty()) {
			say(mc, "§7Packets: " + slowestPackets(true, 2));
		}
		StringBuilder once = new StringBuilder();
		if (wcCount > 0) once.append(wcCount).append(" world changes (slowest ").append(wcTotal / 1000000L).append("ms) · ");
		if (sndLoads > 0) once.append("sound loads ").append(sndLoadNs / 1000000L).append("ms · ");
		if (texLoads > 0) once.append("texture loads ").append(texLoadNs / 1000000L).append("ms · ");
		once.append("shader compiles ").append(net.lax1dude.eaglercraft.opengl.FixedFunctionPipeline.tcCompiles)
				.append(" (").append(net.lax1dude.eaglercraft.opengl.FixedFunctionPipeline.tcCompileNs / 1000000L).append("ms)");
		say(mc, "§7Loads: " + once);
		printSlowShort(mc);
		printGrowth(mc, false);
		String v = verdict(last);
		int vi = v.indexOf("Most spike time");
		say(mc, "§6[Lag] §7" + (vi >= 0 ? v.substring(vi) : v) + " §8(.lag full for more)");
	}


	// ---- things that can pile up while you stay on a server (rejoining clears them) ----
	private static final String[] GROW_NAMES = { "entities", "block ents", "ticking blocks", "chunks",
			"score names", "teams", "tab players", "skins", "textures", "terrain sections", "terrain buffers",
			"particles", "GL buffers", "GL textures", "GL vertex arrays", "framebuffers", "renderbuffers",
			"GL queries" };
	private static Object growConn;
	private static long growConnMs;
	private static int[] growStart;
	private static int growStartMinute = -1;

	/** A new server connection restarts the comparison; one minute in, the starting counts are taken. */
	private static void growCheck(long now) {
		Minecraft mc = Minecraft.getMinecraft();
		Object conn = mc.getConnection();
		if (conn != growConn) {
			growConn = conn;
			growConnMs = now;
			growStart = null;
			growStartMinute = -1;
		}
		if (conn != null && growStart == null && now - growConnMs > 60000L && mc.world != null) {
			growStart = growSample(mc);
			growStartMinute = curMinute;
		}
	}

	private static int[] growSample(Minecraft mc) {
		int[] v = new int[GROW_NAMES.length];
		java.util.Arrays.fill(v, -1);
		net.minecraft.client.multiplayer.WorldClient w = mc.world;
		net.minecraft.client.network.NetHandlerPlayClient c = mc.getConnection();
		try { v[0] = w.loadedEntityList.size(); } catch (Throwable t) { }
		try { v[1] = w.loadedTileEntityList.size(); } catch (Throwable t) { }
		try { v[2] = w.tickableTileEntities.size(); } catch (Throwable t) { }
		try { v[3] = ((net.minecraft.client.multiplayer.ChunkProviderClient) w.getChunkProvider()).tcChunkCount(); } catch (Throwable t) { }
		try { v[4] = w.getScoreboard().tcNameCount(); } catch (Throwable t) { }
		try { v[5] = w.getScoreboard().getTeams().size(); } catch (Throwable t) { }
		try { v[6] = c.getPlayerInfoMap().size(); } catch (Throwable t) { }
		try { v[7] = c.getSkinCache().tcSize(); } catch (Throwable t) { }
		try { v[8] = net.lax1dude.eaglercraft.opengl.EaglercraftGPU.tcTextureCount(); } catch (Throwable t) { }
		try { v[9] = org.taumc.celeritas.impl.TreasureCeleritas.getRenderer().getRenderSectionManager().getTotalSections(); } catch (Throwable t) { }
		try { v[10] = org.taumc.celeritas.lwjgl.webgl.WebGLService.tcLiveBuffers; } catch (Throwable t) { }
		try { v[11] = Integer.parseInt(mc.effectRenderer.getStatistics().trim()); } catch (Throwable t) { }
		for (int k = 0; k < 6; k++) v[12 + k] = net.lax1dude.eaglercraft.internal.PlatformOpenGL.tcGL[k];
		return v;
	}

	/** "Since 1:00: work 5.4 -> 10.6ms, speed 80 -> 31%, grew: score names 40 -> 9000". */
	private static void printGrowth(Minecraft mc, boolean all) {
		if (growStart == null) {
			say(mc, "§7Grew: §fneeds 1 min on this server");
			return;
		}
		int[] now = growSample(mc);
		int last = Math.min(curMinute, MINUTES - 1), m0 = Math.min(growStartMinute, MINUTES - 1);
		StringBuilder sb = new StringBuilder();
		sb.append("§7Since ").append(m0 + 1).append(" min:");
		if (m0 >= 0 && mFrames[m0] > 0 && mFrames[last] > 0) {
			sb.append(" work §f").append(ms1((mLogicAll[m0] + mDrawAll[m0]) / 1000L / mFrames[m0])).append("→")
					.append(ms1((mLogicAll[last] + mDrawAll[last]) / 1000L / mFrames[last])).append("ms§7");
		}
		if (m0 >= 0 && mCpu[m0] > 0 && mCpu[last] > 0) {
			sb.append(", speed §f").append(cpuPct(mCpu[m0])).append("→").append(cpuPct(mCpu[last])).append("%§7");
		}
		StringBuilder g = new StringBuilder();
		for (int i = 0; i < now.length; i++) {
			int a = growStart[i], b = now[i];
			if (a < 0 || b < 0) continue;
			boolean grew = b - a >= 50 && b > a + a / 2;
			if (!all && !grew) continue;
			if (g.length() > 0) g.append(", ");
			g.append(grew ? "§c" : "§f").append(GROW_NAMES[i]).append(' ').append(a).append("→").append(b).append("§7");
		}
		sb.append(all ? " · counts: " : " · grew: ").append(g.length() > 0 ? g.toString() : "§fnothing");
		say(mc, sb.toString());
	}

	/** The n biggest parts with their share: "terrain 30% · entities 20%". */
	private static String topParts(long[] sum, String[] names, int n) {
		long all = 0L;
		for (int k = 0; k < sum.length; k++) all += sum[k];
		if (all <= 0L) return "§7none";
		boolean[] used = new boolean[sum.length];
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++) {
			int big = -1;
			for (int k = 0; k < sum.length; k++) if (!used[k] && (big < 0 || sum[k] > sum[big])) big = k;
			used[big] = true;
			if (sum[big] <= 0L) break;
			if (sb.length() > 0) sb.append(", ");
			sb.append("§f").append(names[big]).append(' ').append(sum[big] * 100L / all).append("%§7");
		}
		return sb.toString();
	}

	private static String shortRow(int[] r) {
		return "§8" + r[0] + ":" + (r[1] < 10 ? "0" : "") + r[1] + " §f" + r[2] + "ms §7" + r[3] + "/" + r[4] + "/"
				+ r[5] + " · " + PART_NAMES[r[9]] + " " + r[10] + ", " + LPART_NAMES[r[11]] + " " + r[12]
				+ (r[7] > 0 ? " · " + r[7] + " chunks" : "");
	}

	// WebGL work sent per frame: draw calls, terrain vertex-layout changes, bytes uploaded
	private static long glDraws, glAttribs, glUp, glFrames, glTerrain, glMulti, glParts;

	/** Called every frame (gameplay or not) so the counters cover exactly one frame. */
	private static void resetGlCounters() {
		net.lax1dude.eaglercraft.opengl.EaglercraftGPU.tcDraws = 0;
		net.lax1dude.eaglercraft.opengl.EaglercraftGPU.tcAttribSets = 0;
		net.lax1dude.eaglercraft.opengl.EaglercraftGPU.tcTerrainDraws = 0;
		net.lax1dude.eaglercraft.opengl.EaglercraftGPU.tcMultiDraws = 0;
		net.lax1dude.eaglercraft.opengl.EaglercraftGPU.tcMultiParts = 0;
		net.lax1dude.eaglercraft.internal.PlatformOpenGL.tcUpBytes = 0;
	}

	private static final String[] GPU_NAMES = { "other", "sky+clouds", "terrain", "entities", "particles",
			"rain/border", "water/glass", "hand", "HUD/menus" };

	/** "GPU per frame: 7.4ms (terrain 3.1, entities 1.2, ...)" from the per-part timer queries. */
	private static String gpuLine() {
		int frames = net.lax1dude.eaglercraft.internal.PlatformInput.tcGpuTakeFrames();
		long[] us = new long[GPU_NAMES.length];
		long tot = 0L;
		for (int i = 0; i < us.length; i++) {
			us[i] = net.lax1dude.eaglercraft.internal.PlatformInput.tcGpuTakePart(i);
			tot += us[i];
		}
		if (frames < 0) {
			return "§7GPU parts: §fthis browser can't measure GPU time";
		}
		if (frames < 30) {
			return "§7GPU parts: §fonly " + frames + " frames measured";
		}
		StringBuilder sb = new StringBuilder("§7GPU per frame: §f").append(ms1(tot / frames)).append("ms §7(");
		boolean[] used = new boolean[us.length];
		for (int n = 0; n < us.length; n++) {
			int big = -1;
			for (int i = 0; i < us.length; i++) {
				if (!used[i] && (big < 0 || us[i] > us[big])) big = i;
			}
			used[big] = true;
			if (us[big] * 20L < tot) break; // under 5%
			if (n > 0) sb.append(", ");
			sb.append(GPU_NAMES[big]).append(" §f").append(ms1(us[big] / frames)).append("§7");
		}
		return sb.append(")").toString();
	}

	private static String glLine() {
		if (glFrames < 30L) return null;
		String md = !net.lax1dude.eaglercraft.internal.PlatformOpenGL.tcMultiDrawSupported() ? "not supported"
				: !net.lax1dude.eaglercraft.opengl.EaglercraftGPU.tcMultiDrawOn ? "off"
						: (glMulti / glFrames) + " drawing " + (glParts / glFrames) + " pieces";
		String r = "§7WebGL per frame: §f" + (glDraws / glFrames) + "§7 draw calls (terrain §f" + (glTerrain / glFrames)
				+ "§7; multi-draw " + md + "), §f" + (glAttribs / glFrames) + "§7 terrain layout changes, §f"
				+ (glUp / glFrames / 1024L) + " KB§7 uploaded";
		glDraws = glAttribs = glUp = glFrames = glTerrain = glMulti = glParts = 0L;
		return r;
	}

	private static void printMovingShort(Minecraft mc) {
		for (int i = 1; i >= 0; i--) {
			long[] a = mv[i];
			long f = a[MV_FRAMES];
			if (f < 30L) {
				continue;
			}
			say(mc, "§7" + (i == 1 ? "Moving" : "Still") + " §f" + (a[MV_MS] > 0L ? f * 1000L / a[MV_MS] : 0L)
					+ " fps §7work §f" + ms1(a[MV_WORK] / f) + "§7 (logic " + ms1(a[MV_LOGIC] / f) + ", terrain "
					+ ms1(a[MV_TERRAIN] / f) + ", entities " + ms1(a[MV_ENT] / f) + ", HUD " + ms1(a[MV_HUD] / f)
					+ ")" + (a[MV_GPUN] > 0L ? " GPU " + ms1(a[MV_GPU] / a[MV_GPUN]) : "")
					+ " · missed " + (a[MV_SLOW] * 100L / f) + "% §8(game " + (a[MV_SLOW_GAME] * 100L / f) + "%, GPU "
					+ (a[MV_SLOW_GPU] * 100L / f) + "%)");
		}
		long[] t = new long[MV_N];
		for (int i = 0; i < 2; i++) {
			for (int k = 0; k < MV_N; k++) t[k] += mv[i][k];
		}
		long f = t[MV_FRAMES];
		if (f >= 30L) {
			StringBuilder eb = new StringBuilder("§7Per frame: ");
			for (int k = 0; k < 4; k++) {
				if (t[MV_C0 + k] > 0L) {
					long n = (t[MV_C0 + k] + f / 2) / f;
					eb.append(n).append(' ').append(E_NAMES[k]).append(" §f").append(ms1(t[MV_E0 + k] / f))
							.append("§7, ");
				}
			}
			eb.append("name tags ").append(ms1(t[MV_TAGS] / f)).append(", particles ").append(ms1(t[MV_PART] / f))
					.append(", block ents ").append(ms1(t[MV_BE] / f)).append(", hand ").append(ms1(t[MV_HAND] / f))
					.append(", sky ").append(ms1(t[MV_SKY] / f));
			say(mc, eb.toString());
		}
		for (int i = 0; i < 2; i++) {
			for (int k = 0; k < MV_N; k++) mv[i][k] = 0L;
		}
	}

	private static final String[] SS_SHORT = { "chunk pkts", "other pkts", "entity upd", "builds", "uploads",
			"visibility", "entities", "HUD", "other drawing", "browser" };

	/** The two newest slow seconds, each with its three biggest costs. */
	private static void printSlowShort(Minecraft mc) {
		if (ssCount == 0) {
			say(mc, "§7Slow seconds: §fnone");
			return;
		}
		for (int i = 0; i < Math.min(2, ssCount); i++) {
			long[] r = ssRing[(ssPos - 1 - i + SS_KEEP * 2) % SS_KEEP];
			long f = Math.max(1L, r[0]);
			long[] v = { r[12], r[13], r[14], r[7], r[8], r[9], r[10], r[11],
					Math.max(0L, r[4] - r[6] - r[10] - r[11]), r[5] };
			StringBuilder sb = new StringBuilder();
			boolean[] used = new boolean[v.length];
			for (int n = 0; n < 3; n++) {
				int big = -1;
				for (int k = 0; k < v.length; k++) if (!used[k] && (big < 0 || v[k] > v[big])) big = k;
				used[big] = true;
				if (sb.length() > 0) sb.append(", ");
				sb.append(SS_SHORT[big]).append(' ').append(ms1(v[big] / f));
			}
			long sec = r[16];
			say(mc, "§7Slow " + (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60) + " §f" + r[0] + " fps§7: "
					+ sb + " ms/frame");
		}
	}

	public static void printFull(Minecraft mc) {
		skipFrame = true;
		if (start < 0L || curMinute < 0) {
			say(mc, "§6[Lag] §7no data yet");
			return;
		}
		int last = Math.min(curMinute, MINUTES - 1);
		int from = Math.max(0, last - 4);
		long frames = 0L, ms = 0L, l = 0, d = 0, b = 0;
		int spikes = 0, worst = 0, gcs = 0, gcSpikes = 0;
		long[] spk = new long[PARTS], lspk = new long[LPARTS];
		for (int m = from; m <= last; m++) {
			frames += mFrames[m];
			ms += mMs[m];
			spikes += mSpikes[m];
			worst = Math.max(worst, mWorst[m]);
			l += mLogic[m];
			d += mDraw[m];
			b += mBrowser[m];
			gcs += mGc[m];
			gcSpikes += mGcSpike[m];
			for (int k = 0; k < PARTS; k++) spk[k] += mPartSpike[m][k];
			for (int k = 0; k < LPARTS; k++) lspk[k] += mLPartSpike[m][k];
		}
		say(mc, "§6[Lag] §f" + (last + 1) + " min §7· last 5 min: §f" + (ms > 0 ? frames * 1000L / ms : 0L)
				+ " fps, " + spikes + " spikes §7(over " + SPIKE_MS + " ms), worst §f" + worst + " ms");
		if (gcLast >= 0) {
			say(mc, "§7Browser memory cleanups: §f" + gcs + " §7(in §f" + gcSpikes + "§7 of the " + spikes + " spikes)");
		}
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
			if (texLoads > 0) {
				String tn = texSlow == null ? "?" : texSlow;
				int tl = tn.lastIndexOf('/');
				if (tl >= 0) tn = tn.substring(tl + 1);
				say(mc, "§7Textures loaded mid-game: §f" + texLoads + " §7taking §f" + (texLoadNs / 1000000L)
						+ "ms§7, slowest §f" + tn + " " + (texSlowNs / 1000000L) + "ms");
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
			int bj = 0, sj = 0, ml = 0, mb = 0, mr = 0;
			long[] all = new long[PARTS], lall = new long[LPARTS];
			for (int m = from; m <= last; m++) {
				la += mLogicAll[m];
				da += mDrawAll[m];
				bj += mBuilds[m];
				sj += mSorts[m];
				ml += mMkLight[m];
				mb += mMkBlock[m];
				mr += mMkRange[m];
				for (int k = 0; k < PARTS; k++) all[k] += mPartAll[m][k];
				for (int k = 0; k < LPARTS; k++) lall[k] += mLPartAll[m][k];
			}
			long frameUs = ms * 1000L / frames, lUs = la / 1000L / frames, dUs = da / 1000L / frames;
			say(mc, "§7Every frame: §f" + ms1(frameUs) + "ms §7(logic §f" + ms1(lUs) + "§7, drawing §f" + ms1(dUs)
					+ "§7, browser/GPU §f" + ms1(Math.max(0L, frameUs - lUs - dUs)) + "§7)");
			say(mc, TCFramePacer.describe() + " §7· " + TCSteadyFps.describe());
			String gl = glLine();
			if (gl != null) say(mc, gl);
			say(mc, gpuLine());
			say(mc, "§7  drawing: " + bigParts(all, PART_NAMES) + " §7· logic: " + bigParts(lall, LPART_NAMES));
			long mins = Math.max(1L, ms / 60000L);
			say(mc, "§7  chunk redraws: §f" + (bj / mins) + "/min §7(asked by light " + (ml / mins) + ", blocks "
					+ (mb / mins) + ", areas " + (mr / mins) + "; water re-sorts " + (sj / mins) + ")");
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
		printSlowSeconds(mc);
		printGrowth(mc, true);
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
						+ " vis " + r[16] : "")
				+ (r[27] != 0 ? " · §cmemory cleanup§7" : "");
	}

	/** A minute's CPU test time as a speed compared to the fastest minute (100 = no slowdown). */
	private static int cpuPct(int t) {
		int best = 0;
		for (int m = 0; m < MINUTES; m++) if (mCpu[m] > 0 && (best == 0 || mCpu[m] < best)) best = mCpu[m];
		return t > 0 && best > 0 ? (int) ((long) best * 100L / t) : 0;
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
