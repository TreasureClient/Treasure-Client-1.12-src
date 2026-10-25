package net.minecraft.client.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.ViaBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

/**
 * Treasure Client — TuffX+ "ViaBlocks" client protocol.
 *
 * Makes modern blocks sent by a server running the <b>TuffX+</b> plugin
 * (github.com/TuffNetwork/TuffXPlus) render on Treasure Client, the same way
 * Tuff Client does.
 *
 * <p>Wire format (Java {@code DataOutput} framing: 2-byte length-prefixed UTF,
 * big-endian int/long):
 * <ul>
 *   <li>On join (and on every respawn, since a proxy server switch only sends a
 *       respawn), the client registers {@code viablocks:data} and sends a byte on
 *       {@code viablocks:handshake} to opt in.</li>
 *   <li>Messages: {@code INIT_PALETTE} (int n, n × UTF state),
 *       {@code NEW_PALETTE_ENTRY} (UTF state),
 *       {@code ADD_CHUNK} (int entries, each [int id][int n][long × n pos]),
 *       {@code ADD_SINGLE} (int id, long pos). Palette id 0 means "clear".</li>
 *   <li>The palette and the first chunks come as {@code viablocks:data} plugin
 *       messages. <b>After that, TuffX+ appends the ADD_CHUNK / ADD_SINGLE bytes
 *       to the end of the normal chunk (0x20), block change (0x0B) and multi block
 *       change (0x10) packets</b>, optionally followed by a Below-Y0 trailer
 *       (int 0x59304348, int length, bytes). Those packets keep their trailing
 *       bytes and hand them to {@link #handleTrailer(byte[])}.</li>
 * </ul>
 * Only blocks from our own {@link ViaBlocks} registry are used; anything else
 * keeps the server's 1.12 stand-in block.
 */
public final class TuffViaBlocks {

	private TuffViaBlocks() {
	}

	public static final String HANDSHAKE_CHANNEL = "viablocks:handshake";
	public static final String DATA_CHANNEL = "viablocks:data";
	private static final String REGISTER_CHANNEL = "REGISTER";
	private static final int Y0_MAGIC = 0x59304348;
	private static final int MAX_PENDING_CHUNKS = 4096;

	/** palette index -> modern block-state string, e.g. "minecraft:deepslate". */
	private static final List<String> palette = new ArrayList<String>();
	/** chunk key -> positions + palette ids waiting for that chunk to arrive. */
	private static final Map<Long, List<long[]>> pending = new HashMap<Long, List<long[]>>();
	/** state string -> client state; {@link #NONE} when we have no matching block. */
	private static final Map<String, IBlockState> stateCache = new HashMap<String, IBlockState>();
	private static final IBlockState NONE = net.minecraft.init.Blocks.AIR.getDefaultState();

	// counters shown by the ".viablocks" chat command
	public static int statMessages = 0;
	public static int statTrailers = 0;
	public static int statApplied = 0;
	public static int statUnknown = 0;

	// ---- join / registration ------------------------------------------------

	/** Called on JoinGame: reset everything and opt in. */
	public static void onJoin(NetworkManager nm) {
		palette.clear();
		pending.clear();
		statMessages = statTrailers = statApplied = statUnknown = 0;
		sendHandshake(nm);
	}

	/**
	 * Called on Respawn. A BungeeCord server switch only sends a respawn, so we
	 * opt in again for the new server. The palette stays (a new TuffX+ server
	 * sends a fresh INIT_PALETTE; the same server ignores a repeat handshake).
	 */
	public static void onRespawn(NetworkManager nm) {
		pending.clear();
		sendHandshake(nm);
	}

	private static void sendHandshake(NetworkManager nm) {
		if (!net.minecraft.client.gui.TreasureMods.viaEnabled() || nm == null) {
			return;
		}
		try {
			byte[] reg = (DATA_CHANNEL + "\u0000" + HANDSHAKE_CHANNEL)
					.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			nm.sendPacket(new CPacketCustomPayload(REGISTER_CHANNEL,
					new PacketBuffer(Unpooled.wrappedBuffer(reg))));
			PacketBuffer hs = new PacketBuffer(Unpooled.buffer());
			hs.writeByte(1);
			nm.sendPacket(new CPacketCustomPayload(HANDSHAKE_CHANNEL, hs));
		} catch (Throwable t) {
			// never let this break joining
		}
	}

	// ---- incoming data ------------------------------------------------------

	/** A {@code viablocks:data} plugin message. */
	public static void handleData(PacketBuffer buf) {
		if (!net.minecraft.client.gui.TreasureMods.viaEnabled()) {
			return;
		}
		statMessages++;
		try {
			readMessage(buf);
		} catch (Throwable t) {
			// ignore a malformed message
		}
	}

	/**
	 * Extra bytes TuffX+ appended to a chunk / block change / multi block change
	 * packet. Called after the vanilla packet has been applied.
	 */
	public static void handleTrailer(byte[] extra) {
		if (extra == null || extra.length < 2 || !net.minecraft.client.gui.TreasureMods.viaEnabled()) {
			return;
		}
		statTrailers++;
		try {
			PacketBuffer buf = new PacketBuffer(Unpooled.wrappedBuffer(extra));
			while (buf.readableBytes() >= 2) {
				if (buf.readableBytes() >= 8 && buf.getInt(buf.readerIndex()) == Y0_MAGIC) {
					buf.skipBytes(4);
					int len = buf.readInt();
					if (len < 0 || len > buf.readableBytes()) {
						return;
					}
					buf.skipBytes(len); // Below-Y0 data: not supported yet
				} else if (!readMessage(buf)) {
					return; // not something we understand
				}
			}
		} catch (Throwable t) {
			// ignore
		}
	}

	/** Reads one message. Returns false if the bytes aren't a known message. */
	private static boolean readMessage(PacketBuffer buf) {
		int len = buf.getUnsignedShort(buf.readerIndex());
		if (len < 1 || len > 32 || len + 2 > buf.readableBytes()) {
			return false;
		}
		String type = readUTF(buf);
		if ("INIT_PALETTE".equals(type)) {
			int n = buf.readInt();
			palette.clear();
			for (int i = 0; i < n; i++) {
				palette.add(readUTF(buf));
			}
		} else if ("NEW_PALETTE_ENTRY".equals(type)) {
			palette.add(readUTF(buf));
		} else if ("ADD_CHUNK".equals(type)) {
			int entries = buf.readInt();
			for (int e = 0; e < entries; e++) {
				int id = buf.readInt();
				int cnt = buf.readInt();
				for (int i = 0; i < cnt; i++) {
					place(buf.readLong(), id);
				}
			}
		} else if ("ADD_SINGLE".equals(type)) {
			int id = buf.readInt();
			place(buf.readLong(), id);
		} else {
			return false;
		}
		return true;
	}

	// ---- applying blocks ------------------------------------------------------

	private static void place(long loc, int id) {
		if (id == 0) {
			return; // "clear": the server's own block update already restored it
		}
		int y = unpackY(loc);
		if (y < 0 || y > 255) {
			return; // outside the 1.12 world
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.world == null) {
			return;
		}
		int x = unpackX(loc);
		int z = unpackZ(loc);
		Chunk chunk = mc.world.getChunkFromChunkCoords(x >> 4, z >> 4);
		if (chunk == null || chunk.isEmpty()) {
			// chunk hasn't arrived yet: keep it until it does
			Long key = Long.valueOf(chunkKey(x >> 4, z >> 4));
			List<long[]> list = pending.get(key);
			if (list == null) {
				if (pending.size() >= MAX_PENDING_CHUNKS) {
					return;
				}
				list = new ArrayList<long[]>();
				pending.put(key, list);
			}
			list.add(new long[] { loc, id });
			return;
		}
		apply(mc, x, y, z, id);
	}

	private static void apply(Minecraft mc, int x, int y, int z, int id) {
		IBlockState state = stateForPalette(id);
		if (state == null) {
			statUnknown++;
			return;
		}
		mc.world.setBlockState(new BlockPos(x, y, z), state, 2);
		statApplied++;
	}

	/** Called after a chunk packet is applied: place anything that was waiting for it. */
	public static void onChunkLoaded(int cx, int cz) {
		if (pending.isEmpty()) {
			return;
		}
		List<long[]> list = pending.remove(Long.valueOf(chunkKey(cx, cz)));
		if (list == null) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.world == null) {
			return;
		}
		for (int i = 0, n = list.size(); i < n; i++) {
			long[] e = list.get(i);
			apply(mc, unpackX(e[0]), unpackY(e[0]), unpackZ(e[0]), (int) e[1]);
		}
	}

	/** Called every client tick: catch chunks that loaded some other way. */
	public static void tick() {
		if (pending.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.world == null) {
			return;
		}
		List<Long> ready = null;
		for (Iterator<Long> it = pending.keySet().iterator(); it.hasNext();) {
			long key = it.next().longValue();
			Chunk c = mc.world.getChunkFromChunkCoords((int) (key >> 32), (int) key);
			if (c != null && !c.isEmpty()) {
				if (ready == null) {
					ready = new ArrayList<Long>();
				}
				ready.add(Long.valueOf(key));
			}
		}
		if (ready != null) {
			for (int i = 0; i < ready.size(); i++) {
				long key = ready.get(i).longValue();
				onChunkLoaded((int) (key >> 32), (int) key);
			}
		}
	}

	// ---- palette -> block-state --------------------------------------------

	private static IBlockState stateForPalette(int id) {
		if (id < 0 || id >= palette.size()) {
			return null;
		}
		String s = palette.get(id);
		if (s == null) {
			return null;
		}
		IBlockState cached = stateCache.get(s);
		if (cached == null) {
			cached = resolve(s);
			stateCache.put(s, cached == null ? NONE : cached);
		}
		return cached == NONE ? null : cached;
	}

	/** "minecraft:polished_basalt[axis=x]" -> our registered block, or null. */
	private static IBlockState resolve(String s) {
		try {
			int br = s.indexOf('[');
			String name = (br >= 0) ? s.substring(0, br) : s;
			String props = (br >= 0) ? s.substring(br) : "";
			Block b = Block.getBlockFromName(name);
			if (b == null || Block.getIdFromBlock(b) < ViaBlocks.FIRST_ID) {
				return null; // not one of our modern blocks (also skips name clashes like stone_slab)
			}
			IBlockState st = b.getDefaultState();
			if (b instanceof BlockRotatedPillar) {
				if (props.contains("axis=x")) {
					st = st.withProperty(BlockRotatedPillar.AXIS, EnumFacing.Axis.X);
				} else if (props.contains("axis=z")) {
					st = st.withProperty(BlockRotatedPillar.AXIS, EnumFacing.Axis.Z);
				}
			} else if (b instanceof BlockSlab) {
				for (int i = 0; i < ViaBlocks.SLAB_HALF.length; i++) {
					if (ViaBlocks.SLAB_HALF[i] == b) {
						if (props.contains("type=double")) {
							st = ViaBlocks.SLAB_DOUBLE[i].getDefaultState();
						} else if (props.contains("type=top")) {
							st = st.withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.TOP);
						}
						break;
					}
				}
			}
			return st;
		} catch (Throwable t) {
			return null;
		}
	}

	/** Text for the ".viablocks" chat command. */
	public static String status() {
		int known = 0;
		for (int i = 0; i < palette.size(); i++) {
			if (stateForPalette(i) != null) {
				known++;
			}
		}
		if (palette.isEmpty() && statMessages == 0 && statTrailers == 0) {
			return "[ViaBlocks] No TuffX+ data from this server. The server needs the TuffX+ plugin"
					+ (net.minecraft.client.gui.TreasureMods.viaEnabled() ? "." : " (and ViaVersion is OFF in the Mod Menu).");
		}
		return "[ViaBlocks] TuffX+ connected: palette " + palette.size() + " (" + known + " we can draw), messages "
				+ statMessages + ", chunk/block extras " + statTrailers + ", blocks placed " + statApplied
				+ ", skipped (no model yet) " + statUnknown + ", waiting chunks " + pending.size();
	}

	// ---- helpers ------------------------------------------------------------

	private static long chunkKey(int cx, int cz) {
		return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
	}

	/** Position packing matches TuffXPlus CustomBlockListener.packLocation. */
	static int unpackX(long p) {
		return (int) (p >> 38);
	}

	static int unpackY(long p) {
		return (int) ((p << 52) >> 52);
	}

	static int unpackZ(long p) {
		return (int) ((p << 26) >> 38);
	}

	/** Java DataInput-style UTF string: unsigned short length + bytes. */
	private static String readUTF(PacketBuffer buf) {
		int len = buf.readUnsignedShort();
		byte[] b = new byte[len];
		buf.readBytes(b);
		return new String(b, java.nio.charset.StandardCharsets.UTF_8);
	}
}
