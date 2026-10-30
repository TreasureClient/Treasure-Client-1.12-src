package net.minecraft.client.network;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;

/**
 * Treasure Client: the ViaVersion mod's handshakes (modern blocks, items and
 * entities) are only sent to servers that support them.
 *
 * They used to be sent to every server on join and on every lobby switch.
 * Anti-exploit plugins can kick for plugin messages on channels the server
 * never registered ("suspicious packets"). A Spigot server lists the plugin
 * channels it listens on (a "REGISTER" message) right after you join, so each
 * handshake now waits until the server has listed its channel.
 */
public final class TCServerChannels {

	private TCServerChannels() {
	}

	private static final Set<String> channels = new HashSet<String>();

	/** Called on JoinGame: a new connection, so forget the old server's channels. */
	public static void onJoin() {
		channels.clear();
		TuffViaBlocks.reset();
		ViaEntitiesClient.reset();
	}

	/**
	 * Called on Respawn (a BungeeCord server switch only sends a respawn). The new
	 * server sends its own REGISTER; until then, opt in again only where the
	 * channel is already known to be supported.
	 */
	public static void onRespawn(NetworkManager nm) {
		TuffViaBlocks.onRespawn(nm, channels.contains(TuffViaBlocks.HANDSHAKE_CHANNEL));
	}

	/** The server listed the plugin channels it listens on (NUL-separated names). */
	public static void onRegister(PacketBuffer buf, NetworkManager nm) {
		try {
			byte[] b = new byte[buf.readableBytes()];
			buf.getBytes(buf.readerIndex(), b);
			for (String name : new String(b, StandardCharsets.UTF_8).split("\u0000")) {
				if (name.isEmpty() || !channels.add(name)) {
					continue; // empty or already handled
				}
				if (name.equals(TuffViaBlocks.HANDSHAKE_CHANNEL)) {
					TuffViaBlocks.sendHandshake(nm);
				} else if (name.equals(ViaEntitiesClient.HANDSHAKE_CHANNEL)) {
					ViaEntitiesClient.sendHandshake(nm);
				} else if (name.equals(TreasureViaHandshake.CHANNEL)) {
					TreasureViaHandshake.sendHandshake(nm);
				}
			}
		} catch (Throwable t) {
			// never let this break the connection
		}
	}
}
