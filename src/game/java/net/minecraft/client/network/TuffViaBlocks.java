package net.minecraft.client.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * Treasure Client — TuffX+ "ViaBlocks" client protocol.
 *
 * This makes modern blocks sent by a <b>TuffX+</b> backend (e.g. arch.mc) render
 * on Treasure Client, exactly the way Tuff/Blade/Voltan clients do. It is the
 * real, de-facto-standard data path — reverse-engineered from TuffNetwork's
 * open-source TuffXPlus plugin — and it replaces our homemade {@code TC|ViaBlocks}
 * channel for real servers.
 *
 * <p>Wire protocol (all messages use Java {@code DataOutput} framing — a 2-byte
 * length-prefixed modified-UTF string, big-endian int/long):
 * <ul>
 *   <li>On join, the client registers {@code viablocks:data} (legacy REGISTER)
 *       and sends a byte on {@code viablocks:handshake} to opt in.</li>
 *   <li>Server → {@code viablocks:data}:
 *     <ul>
 *       <li>{@code INIT_PALETTE}: int count, count × UTF state ("minecraft:deepslate").</li>
 *       <li>{@code NEW_PALETTE_ENTRY}: UTF state (appended).</li>
 *       <li>{@code ADD_CHUNK}: int entries, each [int paletteId][int n][long×n packedPos].</li>
 *       <li>{@code ADD_SINGLE}: int paletteId, long packedPos (id 0 = clear override).</li>
 *     </ul>
 *   </li>
 * </ul>
 * Palette state strings are mapped to OUR registered ViaBlocks by name; blocks
 * are placed client-side via {@link net.minecraft.world.World#setBlockState}.
 *
 * <p>Everything is fully defensive and gated by the ViaVersion Mod Menu toggle
 * ({@link net.minecraft.client.gui.TreasureMods#viaEnabled()}).
 */
public final class TuffViaBlocks {

	private TuffViaBlocks() {
	}

	public static final String HANDSHAKE_CHANNEL = "viablocks:handshake";
	public static final String DATA_CHANNEL = "viablocks:data";
	private static final String REGISTER_CHANNEL = "REGISTER";

	/** palette index -> modern block-state string, e.g. "minecraft:deepslate". */
	private static final List<String> palette = new ArrayList<String>();
	/** packed pos -> palette id (active overrides, kept so they can be re-applied). */
	private static final Map<Long, Integer> overrides = new ConcurrentHashMap<Long, Integer>();
	/** positions whose chunk wasn't loaded yet, retried in {@link #tick()}. */
	private static final ConcurrentLinkedQueue<Long> pending = new ConcurrentLinkedQueue<Long>();
	/** cache: state string -> resolved client state (or null if unmappable). */
	private static final Map<String, IBlockState> stateCache = new ConcurrentHashMap<String, IBlockState>();

	// ---- join / registration ------------------------------------------------
	/** Register the data channel + opt in. Called once the client joins a world. */
	public static void onJoin(NetworkManager nm) {
		if (!net.minecraft.client.gui.TreasureMods.viaEnabled()) {
			return;
		}
		try {
			palette.clear();
			overrides.clear();
			pending.clear();
			// 1.12 REGISTER: raw channel names separated by NUL, so the server is
			// allowed to send us viablocks:data.
			byte[] reg = (DATA_CHANNEL + "\u0000" + HANDSHAKE_CHANNEL)
					.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			nm.sendPacket(new CPacketCustomPayload(REGISTER_CHANNEL,
					new PacketBuffer(Unpooled.wrappedBuffer(reg))));
			// handshake — TuffX+ enables the player on any message on this channel.
			PacketBuffer hs = new PacketBuffer(Unpooled.buffer());
			hs.writeByte(1);
			nm.sendPacket(new CPacketCustomPayload(HANDSHAKE_CHANNEL, hs));
		} catch (Throwable t) {
			// never let this break joining
		}
	}

	// ---- incoming data ------------------------------------------------------
	public static void handleData(PacketBuffer buf) {
		if (!net.minecraft.client.gui.TreasureMods.viaEnabled()) {
			return;
		}
		try {
			String type = readUTF(buf);
			if ("INIT_PALETTE".equals(type)) {
				int n = buf.readInt();
				synchronized (palette) {
					palette.clear();
					for (int i = 0; i < n; i++) {
						palette.add(readUTF(buf));
					}
				}
			} else if ("NEW_PALETTE_ENTRY".equals(type)) {
				String s = readUTF(buf);
				synchronized (palette) {
					palette.add(s);
				}
			} else if ("ADD_CHUNK".equals(type)) {
				int entries = buf.readInt();
				for (int e = 0; e < entries; e++) {
					int id = buf.readInt();
					int cnt = buf.readInt();
					for (int i = 0; i < cnt; i++) {
						setOverride(buf.readLong(), id);
					}
				}
			} else if ("ADD_SINGLE".equals(type)) {
				int id = buf.readInt();
				long loc = buf.readLong();
				if (id == 0) {
					removeOverride(loc);
				} else {
					setOverride(loc, id);
				}
			}
			// unknown message types are ignored so a newer server can't break us
		} catch (Throwable t) {
			// ignore a malformed message
		}
	}

	// ---- override application ------------------------------------------------
	private static void setOverride(long loc, int id) {
		overrides.put(Long.valueOf(loc), Integer.valueOf(id));
		if (!tryApply(loc, id)) {
			pending.add(Long.valueOf(loc));
		}
	}

	private static void removeOverride(long loc) {
		overrides.remove(Long.valueOf(loc));
		// the server's own block change already restored the vanilla block here,
		// so we just forget our override.
	}

	private static boolean tryApply(long loc, int id) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.world == null) {
			return false;
		}
		int x = unpackX(loc);
		int y = unpackY(loc);
		int z = unpackZ(loc);
		BlockPos pos = new BlockPos(x, y, z);
		if (!mc.world.isBlockLoaded(pos)) {
			return false;
		}
		IBlockState state = stateForPalette(id);
		if (state != null) {
			mc.world.setBlockState(pos, state, 3);
		}
		return true; // resolved (even if unmappable) — don't keep retrying
	}

	/** Drain the pending queue for chunks that have since loaded. Called each client tick. */
	public static void tick() {
		if (pending.isEmpty() || !net.minecraft.client.gui.TreasureMods.viaEnabled()) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.world == null) {
			return;
		}
		int size = pending.size();
		int budget = 4096;
		for (int i = 0; i < size && budget > 0; i++, budget--) {
			Long loc = pending.poll();
			if (loc == null) {
				break;
			}
			Integer id = overrides.get(loc);
			if (id == null) {
				continue; // override was removed
			}
			if (!tryApply(loc.longValue(), id.intValue())) {
				pending.add(loc); // chunk still not ready; retry next tick
			}
		}
	}

	// ---- palette -> block-state --------------------------------------------
	private static IBlockState stateForPalette(int id) {
		String s;
		synchronized (palette) {
			if (id < 0 || id >= palette.size()) {
				return null;
			}
			s = palette.get(id);
		}
		return stateForString(s);
	}

	private static IBlockState stateForString(String s) {
		if (s == null) {
			return null;
		}
		IBlockState cached = stateCache.get(s);
		if (cached != null) {
			return cached;
		}
		IBlockState resolved = null;
		try {
			int br = s.indexOf('[');
			String name = (br >= 0) ? s.substring(0, br) : s; // drop [props] for now
			Block b = Block.getBlockFromName(name);
			if (b != null && b != net.minecraft.init.Blocks.AIR) {
				resolved = b.getDefaultState();
			}
		} catch (Throwable t) {
			resolved = null;
		}
		if (resolved != null) {
			stateCache.put(s, resolved);
		}
		return resolved;
	}

	// ---- helpers ------------------------------------------------------------
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

	/** Java DataInput-style modified-UTF string: unsigned short length + UTF-8 bytes. */
	private static String readUTF(PacketBuffer buf) {
		int len = buf.readUnsignedShort();
		byte[] b = new byte[len];
		buf.readBytes(b);
		return new String(b, java.nio.charset.StandardCharsets.UTF_8);
	}
}
