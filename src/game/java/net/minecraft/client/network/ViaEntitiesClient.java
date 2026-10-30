package net.minecraft.client.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;

/**
 * Treasure Client — TuffX+ "ViaEntities" client protocol.
 *
 * Renders modern entities a TuffX+ backend streams, the same way TuffClient does
 * (reverse-engineered from TuffNetwork/TuffXPlus). We only have models for the
 * modern entities we registered (Bee, Copper Golem), so those render and any
 * other modern entity type in the server's palette is skipped gracefully.
 *
 * Wire protocol (Java DataOutput framing on channel {@code viaentities:data}):
 * <ul>
 *   <li>Client registers {@code viaentities:data} and opts in on
 *       {@code entities:handshake}.</li>
 *   <li>{@code INIT_PALETTE}: int count, count × UTF type ("minecraft:bee").</li>
 *   <li>{@code SPAWN_ENTITY}: int entityId, short paletteIndex, double x/y/z,
 *       float yaw, float pitch.</li>
 *   <li>{@code DESTROY_ENTITY}: int entityId.</li>
 *   <li>{@code ENTITY_METADATA} / {@code ENTITY_ANIMATION}: ignored (v1).</li>
 * </ul>
 * Fully defensive and gated by the ViaVersion Mod Menu toggle.
 */
public final class ViaEntitiesClient {

	private ViaEntitiesClient() {
	}

	public static final String HANDSHAKE_CHANNEL = "entities:handshake";
	public static final String DATA_CHANNEL = "viaentities:data";
	private static final String REGISTER_CHANNEL = "REGISTER";

	/** palette index -> modern entity type string, e.g. "minecraft:bee". */
	private static final List<String> palette = new ArrayList<String>();

	/** modern entity type -> our registered EntityList id (only the ones we model). */
	private static final Map<String, Integer> TYPE_TO_ID = new HashMap<String, Integer>();
	static {
		TYPE_TO_ID.put("minecraft:bee", Integer.valueOf(202));
		TYPE_TO_ID.put("minecraft:copper_golem", Integer.valueOf(201));
	}

	// ---- join / registration ------------------------------------------------
	/** Called on JoinGame. The handshake waits for the server's REGISTER (TCServerChannels). */
	public static void reset() {
		palette.clear();
	}

	static void sendHandshake(NetworkManager nm) {
		if (!net.minecraft.client.gui.TreasureMods.viaEnabled()) {
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
			} else if ("SPAWN_ENTITY".equals(type)) {
				int entityId = buf.readInt();
				int idx = buf.readShort();
				double x = buf.readDouble();
				double y = buf.readDouble();
				double z = buf.readDouble();
				float yaw = buf.readFloat();
				float pitch = buf.readFloat();
				spawn(entityId, idx, x, y, z, yaw, pitch);
			} else if ("DESTROY_ENTITY".equals(type)) {
				destroy(buf.readInt());
			}
			// ENTITY_METADATA / ENTITY_ANIMATION and anything else: ignored for v1
		} catch (Throwable t) {
			// ignore a malformed message
		}
	}

	private static void spawn(int entityId, int paletteIndex, double x, double y, double z, float yaw, float pitch) {
		try {
			Minecraft mc = Minecraft.getMinecraft();
			if (mc == null || mc.world == null) {
				return;
			}
			String typeStr;
			synchronized (palette) {
				if (paletteIndex < 0 || paletteIndex >= palette.size()) {
					return;
				}
				typeStr = palette.get(paletteIndex);
			}
			Integer typeId = TYPE_TO_ID.get(typeStr);
			if (typeId == null) {
				return; // a modern entity we don't have a model for — skip
			}
			Entity entity = EntityList.createEntityByID(typeId.intValue(), mc.world);
			if (entity != null) {
				entity.setLocationAndAngles(x, y, z, yaw, pitch);
				mc.world.addEntityToWorld(entityId, entity);
			}
		} catch (Throwable t) {
			// ignore a single bad entity
		}
	}

	private static void destroy(int entityId) {
		try {
			Minecraft mc = Minecraft.getMinecraft();
			if (mc != null && mc.world != null) {
				mc.world.removeEntityFromWorld(entityId);
			}
		} catch (Throwable t) {
			// ignore
		}
	}

	/** Java DataInput-style modified-UTF string: unsigned short length + UTF-8 bytes. */
	private static String readUTF(PacketBuffer buf) {
		int len = buf.readUnsignedShort();
		byte[] b = new byte[len];
		buf.readBytes(b);
		return new String(b, java.nio.charset.StandardCharsets.UTF_8);
	}
}
