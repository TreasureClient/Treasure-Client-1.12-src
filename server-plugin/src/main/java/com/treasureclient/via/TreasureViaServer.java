package com.treasureclient.via;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Treasure Client — Option A data source, server side.
 *
 * Paper/Spigot 1.12.2 plugin for the backend of an EaglerXBungee stack.
 *
 * It completes the {@code TC|ViaBlocks} handshake, then streams modern blocks to
 * Treasure Clients over the same plugin channel. The client applies them from
 * its own ViaBlocks registry, so modern content renders even though the 1.12
 * server world doesn't hold those blocks. No ProtocolLib / NMS packet rewriting
 * needed — the id scheme lives in {@code viablocks_mapping.json}.
 *
 * Commands (ops):
 *   /viablock &lt;name&gt;  — place the modern block you're looking at.
 *   /viablocks         — list available modern block names.
 *   /viatest           — place a sampler row of modern blocks in front of you.
 */
public final class TreasureViaServer extends JavaPlugin implements PluginMessageListener {

	/** Must match TreasureViaHandshake.CHANNEL / opcodes in the client. */
	public static final String CHANNEL = "TC|ViaBlocks";
	public static final int PROTOCOL = 1;
	public static final int OP_ACK = 1;
	public static final int OP_SET_BLOCK = 16;
	public static final int OP_SET_BLOCKS = 17;

	private final Set<UUID> viaClients = Collections.synchronizedSet(new HashSet<UUID>());
	/** modern block name -> our base block-state id (meta 0), from the mapping json. */
	private final Map<String, Integer> blockStateIds = new TreeMap<String, Integer>();

	@Override
	public void onEnable() {
		loadMapping();
		getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
		getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
		getLogger().info("TreasureViaServer enabled — " + blockStateIds.size()
				+ " modern blocks loaded; listening on " + CHANNEL);
	}

	@Override
	public void onDisable() {
		viaClients.clear();
	}

	private void loadMapping() {
		blockStateIds.clear();
		try (InputStream in = getResource("viablocks_mapping.json")) {
			if (in == null) {
				getLogger().warning("viablocks_mapping.json not found in the jar!");
				return;
			}
			JsonObject root = new JsonParser()
					.parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject blocks = root.getAsJsonObject("blocks");
			for (Map.Entry<String, JsonElement> e : blocks.entrySet()) {
				JsonObject o = e.getValue().getAsJsonObject();
				if (o.has("baseStateId")) {
					blockStateIds.put(e.getKey(), o.get("baseStateId").getAsInt());
				}
			}
		} catch (Throwable t) {
			getLogger().warning("Failed to load viablocks_mapping.json: " + t);
		}
	}

	// ---- handshake ----------------------------------------------------------
	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!CHANNEL.equals(channel) || player == null) {
			return;
		}
		try {
			int clientProto = (message != null && message.length > 0) ? (message[0] & 0xFF) : 0;
			viaClients.add(player.getUniqueId());
			getLogger().info("Treasure Client detected: " + player.getName() + " (proto " + clientProto + ")");
			player.sendPluginMessage(this, CHANNEL, new byte[] { (byte) OP_ACK });
		} catch (Throwable t) {
			getLogger().warning("ViaBlocks handshake error: " + t);
		}
	}

	public boolean isViaClient(Player player) {
		return player != null && viaClients.contains(player.getUniqueId());
	}

	// ---- sending blocks -----------------------------------------------------
	private void sendSetBlock(Player p, int x, int y, int z, int stateId) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bos)) {
			out.writeByte(OP_SET_BLOCK);
			out.writeInt(x);
			out.writeInt(y);
			out.writeInt(z);
			out.writeInt(stateId);
		} catch (Throwable t) {
			return;
		}
		p.sendPluginMessage(this, CHANNEL, bos.toByteArray());
	}

	// ---- commands -----------------------------------------------------------
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("Players only.");
			return true;
		}
		Player p = (Player) sender;
		String cmd = command.getName().toLowerCase();

		if (cmd.equals("viablocks")) {
			p.sendMessage(ChatColor.GOLD + "ViaBlocks (" + blockStateIds.size() + "): "
					+ ChatColor.GRAY + String.join(", ", blockStateIds.keySet()));
			return true;
		}

		if (!isViaClient(p)) {
			p.sendMessage(ChatColor.RED + "You are not detected as a Treasure Client "
					+ "(no TC|ViaBlocks handshake). Join with Treasure Client.");
			return true;
		}

		if (cmd.equals("viablock")) {
			if (args.length < 1) {
				p.sendMessage(ChatColor.RED + "Usage: /viablock <name>");
				return true;
			}
			Integer sid = blockStateIds.get(args[0].toLowerCase());
			if (sid == null) {
				p.sendMessage(ChatColor.RED + "Unknown modern block: " + args[0]);
				return true;
			}
			Block target = p.getTargetBlock((Set<org.bukkit.Material>) null, 8);
			if (target == null) {
				p.sendMessage(ChatColor.RED + "Look at a block first.");
				return true;
			}
			Location l = target.getLocation();
			sendSetBlock(p, l.getBlockX(), l.getBlockY(), l.getBlockZ(), sid);
			p.sendMessage(ChatColor.GREEN + "Placed " + args[0] + " (client-side).");
			return true;
		}

		if (cmd.equals("viatest")) {
			String[] sample = { "deepslate", "calcite", "tuff", "amethyst_block", "copper_block",
					"netherite_block", "cartography_table", "crying_obsidian" };
			Location base = p.getLocation().getBlock().getRelative(0, 0, 0).getLocation();
			int placed = 0;
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			try (DataOutputStream out = new DataOutputStream(bos)) {
				out.writeByte(OP_SET_BLOCKS);
				out.writeInt(sample.length);
				for (int i = 0; i < sample.length; i++) {
					Integer sid = blockStateIds.get(sample[i]);
					if (sid == null) {
						sid = blockStateIds.values().iterator().next();
					}
					int x = base.getBlockX() + 1 + i;
					int y = base.getBlockY();
					int z = base.getBlockZ() + 2;
					out.writeInt(x);
					out.writeInt(y);
					out.writeInt(z);
					out.writeInt(sid);
					placed++;
				}
			} catch (Throwable t) {
				p.sendMessage(ChatColor.RED + "viatest failed: " + t);
				return true;
			}
			p.sendPluginMessage(this, CHANNEL, bos.toByteArray());
			p.sendMessage(ChatColor.GREEN + "Sent " + placed + " sample modern blocks (look ~2 blocks +Z).");
			return true;
		}
		return false;
	}
}
