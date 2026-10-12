package com.treasureclient.via;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Treasure Client — Option A data source, server side (phase 1: handshake).
 *
 * Drops into a Paper/Spigot 1.12.2 backend (e.g. the backend of an EaglerXBungee
 * stack). It completes the {@code TC|ViaBlocks} plugin-channel handshake the
 * client sends on join (see {@code TreasureViaHandshake} in the client): it
 * records which players are Treasure Clients and replies so the client's
 * {@code serverSupports} flag flips true.
 *
 * Phase 2 (separate, needs ProtocolLib): intercept outgoing chunk / block-change
 * packets for {@link #isViaClient(Player)} players and inject our modern
 * block-state ids from viablocks_mapping.json. That half can't be unit-tested
 * without a running server, so it's built and iterated against a live stack.
 */
public final class TreasureViaServer extends JavaPlugin implements PluginMessageListener {

	/** Must match TreasureViaHandshake.CHANNEL in the client. */
	public static final String CHANNEL = "TC|ViaBlocks";

	/** Must match TreasureViaHandshake.PROTOCOL in the client. */
	public static final int PROTOCOL = 1;

	private final Set<UUID> viaClients = Collections.synchronizedSet(new HashSet<UUID>());

	@Override
	public void onEnable() {
		getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
		getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
		getLogger().info("TreasureViaServer enabled — listening for Treasure Clients on " + CHANNEL);
	}

	@Override
	public void onDisable() {
		viaClients.clear();
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!CHANNEL.equals(channel) || player == null) {
			return;
		}
		try {
			// Client handshake payload: [proto byte][varint blockFirstId][varint itemFirstId].
			// We only need the proto byte to confirm support.
			int clientProto = (message != null && message.length > 0) ? (message[0] & 0xFF) : 0;
			viaClients.add(player.getUniqueId());
			getLogger().info("Treasure Client detected: " + player.getName() + " (proto " + clientProto + ")");

			// Reply so the client flips serverSupports = true.
			player.sendPluginMessage(this, CHANNEL, new byte[] { (byte) PROTOCOL });
		} catch (Throwable t) {
			getLogger().warning("ViaBlocks handshake error for "
					+ (player != null ? player.getName() : "?") + ": " + t);
		}
	}

	/** True once the given player completed the ViaBlocks handshake. */
	public boolean isViaClient(Player player) {
		return player != null && viaClients.contains(player.getUniqueId());
	}
}
