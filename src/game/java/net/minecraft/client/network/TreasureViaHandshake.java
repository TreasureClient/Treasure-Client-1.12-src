package net.minecraft.client.network;

import io.netty.buffer.Unpooled;
import net.minecraft.block.ViaBlocks;
import net.minecraft.item.ViaItems;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;

/**
 * Treasure Client — ViaBlocks capability handshake (Option A data source).
 *
 * On joining a server the client announces, on a plugin channel, that it
 * supports the modern ViaBlocks/ViaItems registries and advertises the id scheme
 * (the base ids; the full modern-identifier → id table lives in
 * docs/viablocks_mapping.json). An Option-A server/proxy uses this to detect our
 * client and start emitting OUR block-state ids in chunk data (which the client
 * already renders, since the blocks are registered) instead of the vanilla
 * 1.12 fallback.
 *
 * Everything here is fully defensive: any failure is swallowed so the handshake
 * can never stop the player from joining a normal (non-ViaBlocks) server.
 */
public final class TreasureViaHandshake {

	private TreasureViaHandshake() {
	}

	/** Plugin channel for the handshake (legacy 1.12 channel-name style). */
	public static final String CHANNEL = "TC|ViaBlocks";

	/** Handshake protocol version. */
	public static final int PROTOCOL = 1;

	/** Set true once a server acknowledges it will send modern ViaBlocks content. */
	public static boolean serverSupports = false;

	/** Announce ViaBlocks support to the server (called on join). */
	public static void sendHandshake(NetworkManager nm) {
		try {
			PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
			buf.writeByte(PROTOCOL);
			buf.writeVarIntToBuffer(ViaBlocks.FIRST_ID);
			buf.writeVarIntToBuffer(ViaItems.FIRST_ID);
			nm.sendPacket(new CPacketCustomPayload(CHANNEL, buf));
		} catch (Throwable t) {
			// never let the handshake break joining a server
		}
	}

	/** Handle the server's reply on {@link #CHANNEL}. */
	public static void handleServer(PacketBuffer buf) {
		try {
			int proto = buf.readUnsignedByte();
			serverSupports = proto >= 1;
		} catch (Throwable t) {
			serverSupports = false;
		}
	}
}
