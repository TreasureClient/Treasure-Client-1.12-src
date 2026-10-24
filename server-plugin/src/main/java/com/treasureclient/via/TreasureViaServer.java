package com.treasureclient.via;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Treasure Client — Option A data source, server side (phase 3).
 *
 * Paper/Spigot 1.12.2 plugin for the backend of an EaglerXBungee stack.
 *
 * It completes the {@code TC|ViaBlocks} handshake, then streams modern blocks,
 * items and entities to Treasure Clients over the same plugin channel. The
 * client applies them from its own ViaBlocks/ViaItems registries, so modern
 * content renders even though the 1.12 server world doesn't hold those blocks.
 * No ProtocolLib / NMS packet rewriting needed — the id scheme lives in
 * {@code viablocks_mapping.json}.
 *
 * Phase 3 adds persistence: modern block overrides are stored on disk and
 * periodically re-sent to each Treasure Client, so they survive chunk reloads
 * and relogs (a plain 1.12 server would otherwise overwrite them with the real
 * block whenever it re-sends the chunk).
 *
 * Commands (ops):
 *   /viablock &lt;name&gt;   — place the modern block you're looking at (persisted).
 *   /viablocks          — list available modern block names.
 *   /viatest            — place a sampler row of modern blocks in front of you.
 *   /viagive &lt;name&gt;    — give yourself a modern item (client-side).
 *   /viaentity &lt;name&gt;  — spawn a modern entity where you're looking.
 *   /viaclear [all]     — clear your nearby modern blocks (or all of them).
 */
public final class TreasureViaServer extends JavaPlugin implements PluginMessageListener, Listener {

	/** Must match TreasureViaHandshake.CHANNEL / opcodes in the client. */
	public static final String CHANNEL = "TC|ViaBlocks";
	public static final int PROTOCOL = 1;
	public static final int OP_ACK = 1;
	public static final int OP_SET_BLOCK = 16;
	public static final int OP_SET_BLOCKS = 17;
	public static final int OP_GIVE_ITEM = 32;
	public static final int OP_SPAWN_ENTITY = 48;

	/** How often (ticks) to re-assert stored block overrides to via clients. */
	private static final long RESEND_PERIOD_TICKS = 60L;
	/** Re-send stored blocks within this many blocks of the player. */
	private static final int RESEND_RADIUS = 96;
	/** Max blocks per OP_SET_BLOCKS message (keep payloads reasonable). */
	private static final int MAX_BATCH = 2048;

	private final Set<UUID> viaClients = Collections.synchronizedSet(new HashSet<UUID>());
	/** modern block name -> our base block-state id (meta 0), from the mapping json. */
	private final Map<String, Integer> blockStateIds = new TreeMap<String, Integer>();
	/** modern item name -> our item id, from the mapping json. */
	private final Map<String, Integer> itemIds = new TreeMap<String, Integer>();
	/** modern entity name -> our entity-type id, from the mapping json. */
	private final Map<String, Integer> entityTypeIds = new TreeMap<String, Integer>();

	/** Persistent store of modern block overrides: "world;x;y;z" -> stateId. */
	private final Map<String, Integer> overrides = new ConcurrentHashMap<String, Integer>();
	private File blocksFile;

	@Override
	public void onEnable() {
		loadMapping();
		loadOverrides();
		getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
		getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
		getServer().getPluginManager().registerEvents(this, this);
		getServer().getScheduler().runTaskTimer(this, new Runnable() {
			@Override
			public void run() {
				resendNearbyOverrides();
			}
		}, RESEND_PERIOD_TICKS, RESEND_PERIOD_TICKS);
		getLogger().info("TreasureViaServer enabled — " + blockStateIds.size() + " blocks, "
				+ itemIds.size() + " items, " + entityTypeIds.size() + " entities; "
				+ overrides.size() + " stored overrides; listening on " + CHANNEL);
	}

	@Override
	public void onDisable() {
		saveOverrides();
		viaClients.clear();
	}

	// ---- mapping ------------------------------------------------------------
	private void loadMapping() {
		blockStateIds.clear();
		itemIds.clear();
		entityTypeIds.clear();
		try (InputStream in = getResource("viablocks_mapping.json")) {
			if (in == null) {
				getLogger().warning("viablocks_mapping.json not found in the jar!");
				return;
			}
			JsonObject root = new JsonParser()
					.parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			readIntField(root, "blocks", "baseStateId", blockStateIds);
			readIntField(root, "items", "itemId", itemIds);
			readIntField(root, "entities", "entityTypeId", entityTypeIds);
		} catch (Throwable t) {
			getLogger().warning("Failed to load viablocks_mapping.json: " + t);
		}
	}

	private static void readIntField(JsonObject root, String section, String field, Map<String, Integer> out) {
		if (!root.has(section)) {
			return;
		}
		JsonObject obj = root.getAsJsonObject(section);
		for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
			JsonObject o = e.getValue().getAsJsonObject();
			if (o.has(field)) {
				out.put(e.getKey(), o.get(field).getAsInt());
			}
		}
	}

	// ---- persistence --------------------------------------------------------
	private static String key(String world, int x, int y, int z) {
		return world + ';' + x + ';' + y + ';' + z;
	}

	private void putOverride(String world, int x, int y, int z, int stateId) {
		overrides.put(key(world, x, y, z), stateId);
		saveOverrides();
	}

	private void loadOverrides() {
		overrides.clear();
		blocksFile = new File(getDataFolder(), "blocks.yml");
		if (!blocksFile.exists()) {
			return;
		}
		try {
			YamlConfiguration cfg = YamlConfiguration.loadConfiguration(blocksFile);
			for (String k : cfg.getKeys(false)) {
				// stored as "world;x;y;z" -> stateId, with ';' escaped to avoid YAML path splitting
				overrides.put(k.replace('|', ';'), cfg.getInt(k));
			}
		} catch (Throwable t) {
			getLogger().warning("Failed to load blocks.yml: " + t);
		}
	}

	private synchronized void saveOverrides() {
		try {
			if (blocksFile == null) {
				blocksFile = new File(getDataFolder(), "blocks.yml");
			}
			if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
				getLogger().warning("Could not create data folder for blocks.yml");
			}
			YamlConfiguration cfg = new YamlConfiguration();
			for (Map.Entry<String, Integer> e : overrides.entrySet()) {
				// '.' and ';' are path separators to YAML; use '|' as a safe key form
				cfg.set(e.getKey().replace(';', '|'), e.getValue());
			}
			cfg.save(blocksFile);
		} catch (Throwable t) {
			getLogger().warning("Failed to save blocks.yml: " + t);
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
			boolean isNew = viaClients.add(player.getUniqueId());
			getLogger().info("Treasure Client detected: " + player.getName() + " (proto " + clientProto + ")");
			player.sendPluginMessage(this, CHANNEL, new byte[] { (byte) OP_ACK });
			if (isNew) {
				// push any stored overrides in this player's world right away
				sendOverridesTo(player);
			}
		} catch (Throwable t) {
			getLogger().warning("ViaBlocks handshake error: " + t);
		}
	}

	public boolean isViaClient(Player player) {
		return player != null && viaClients.contains(player.getUniqueId());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		viaClients.remove(e.getPlayer().getUniqueId());
	}

	/** If a real block is broken where we hold an override, drop the override. */
	@EventHandler
	public void onBreak(BlockBreakEvent e) {
		Block b = e.getBlock();
		String k = key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
		if (overrides.remove(k) != null) {
			saveOverrides();
		}
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

	/** Re-assert all stored overlays near every via client (keeps them "stuck"). */
	private void resendNearbyOverrides() {
		if (overrides.isEmpty() || viaClients.isEmpty()) {
			return;
		}
		for (Player p : getServer().getOnlinePlayers()) {
			if (isViaClient(p)) {
				sendOverridesTo(p);
			}
		}
	}

	private void sendOverridesTo(Player p) {
		String world = p.getWorld().getName();
		int px = p.getLocation().getBlockX();
		int pz = p.getLocation().getBlockZ();
		List<int[]> batch = new ArrayList<int[]>();
		for (Map.Entry<String, Integer> e : overrides.entrySet()) {
			String[] parts = e.getKey().split(";");
			if (parts.length != 4 || !parts[0].equals(world)) {
				continue;
			}
			int x, y, z;
			try {
				x = Integer.parseInt(parts[1]);
				y = Integer.parseInt(parts[2]);
				z = Integer.parseInt(parts[3]);
			} catch (NumberFormatException nfe) {
				continue;
			}
			if (Math.abs(x - px) > RESEND_RADIUS || Math.abs(z - pz) > RESEND_RADIUS) {
				continue;
			}
			batch.add(new int[] { x, y, z, e.getValue() });
			if (batch.size() >= MAX_BATCH) {
				flushBatch(p, batch);
				batch.clear();
			}
		}
		if (!batch.isEmpty()) {
			flushBatch(p, batch);
		}
	}

	private void flushBatch(Player p, List<int[]> batch) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bos)) {
			out.writeByte(OP_SET_BLOCKS);
			out.writeInt(batch.size());
			for (int[] b : batch) {
				out.writeInt(b[0]);
				out.writeInt(b[1]);
				out.writeInt(b[2]);
				out.writeInt(b[3]);
			}
		} catch (Throwable t) {
			return;
		}
		p.sendPluginMessage(this, CHANNEL, bos.toByteArray());
	}

	// ---- sending items / entities ------------------------------------------
	private void sendGiveItem(Player p, int itemId, int count) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bos)) {
			out.writeByte(OP_GIVE_ITEM);
			writeVarInt(out, itemId);
			out.writeInt(count);
		} catch (Throwable t) {
			return;
		}
		p.sendPluginMessage(this, CHANNEL, bos.toByteArray());
	}

	private void sendSpawnEntity(Player p, int typeId, double x, double y, double z, float yaw, float pitch) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bos)) {
			out.writeByte(OP_SPAWN_ENTITY);
			out.writeInt(nextEntityId());
			out.writeInt(typeId);
			out.writeDouble(x);
			out.writeDouble(y);
			out.writeDouble(z);
			out.writeFloat(yaw);
			out.writeFloat(pitch);
		} catch (Throwable t) {
			return;
		}
		p.sendPluginMessage(this, CHANNEL, bos.toByteArray());
	}

	/** Client-side entity ids; keep them high to avoid colliding with real ones. */
	private int entityIdCounter = 2_000_000;

	private synchronized int nextEntityId() {
		return entityIdCounter++;
	}

	/** VarInt encoder matching PacketBuffer.readVarIntFromBuffer on the client. */
	private static void writeVarInt(DataOutputStream out, int value) throws java.io.IOException {
		while ((value & 0xFFFFFF80) != 0) {
			out.writeByte((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.writeByte(value & 0x7F);
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
			p.sendMessage(ChatColor.GOLD + "ViaItems (" + itemIds.size() + "), "
					+ ChatColor.GOLD + "ViaEntities (" + entityTypeIds.size() + "): "
					+ ChatColor.GRAY + String.join(", ", entityTypeIds.keySet()));
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
			putOverride(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), sid);
			sendSetBlock(p, l.getBlockX(), l.getBlockY(), l.getBlockZ(), sid);
			p.sendMessage(ChatColor.GREEN + "Placed " + args[0] + " (persisted).");
			return true;
		}

		if (cmd.equals("viagive")) {
			if (args.length < 1) {
				p.sendMessage(ChatColor.RED + "Usage: /viagive <name>");
				return true;
			}
			Integer iid = itemIds.get(args[0].toLowerCase());
			if (iid == null) {
				p.sendMessage(ChatColor.RED + "Unknown modern item: " + args[0]);
				return true;
			}
			int count = 1;
			if (args.length >= 2) {
				try {
					count = Math.max(1, Math.min(64, Integer.parseInt(args[1])));
				} catch (NumberFormatException ignored) {
				}
			}
			sendGiveItem(p, iid, count);
			p.sendMessage(ChatColor.GREEN + "Gave " + count + "x " + args[0] + " (client-side).");
			return true;
		}

		if (cmd.equals("viaentity")) {
			if (args.length < 1) {
				p.sendMessage(ChatColor.RED + "Usage: /viaentity <" + String.join("|", entityTypeIds.keySet()) + ">");
				return true;
			}
			Integer tid = entityTypeIds.get(args[0].toLowerCase());
			if (tid == null) {
				p.sendMessage(ChatColor.RED + "Unknown modern entity: " + args[0]);
				return true;
			}
			Block target = p.getTargetBlock((Set<org.bukkit.Material>) null, 16);
			Location l = (target != null) ? target.getLocation().add(0.5, 1.0, 0.5)
					: p.getLocation();
			sendSpawnEntity(p, tid, l.getX(), l.getY(), l.getZ(), p.getLocation().getYaw(), 0.0F);
			p.sendMessage(ChatColor.GREEN + "Spawned " + args[0] + " (client-side).");
			return true;
		}

		if (cmd.equals("viaclear")) {
			boolean all = args.length >= 1 && args[0].equalsIgnoreCase("all");
			int removed = clearOverrides(p, all);
			p.sendMessage(ChatColor.GREEN + "Cleared " + removed + " override(s)"
					+ (all ? " (all)." : " nearby. Rejoin/reload chunks to see the real blocks."));
			return true;
		}

		if (cmd.equals("viatest")) {
			String[] sample = { "deepslate", "calcite", "tuff", "amethyst_block", "copper_block",
					"netherite_block", "cartography_table", "crying_obsidian" };
			Location base = p.getLocation();
			int placed = 0;
			List<int[]> batch = new ArrayList<int[]>();
			for (int i = 0; i < sample.length; i++) {
				Integer sid = blockStateIds.get(sample[i]);
				if (sid == null) {
					continue;
				}
				int x = base.getBlockX() + 1 + i;
				int y = base.getBlockY();
				int z = base.getBlockZ() + 2;
				putOverride(base.getWorld().getName(), x, y, z, sid);
				batch.add(new int[] { x, y, z, sid });
				placed++;
			}
			flushBatch(p, batch);
			p.sendMessage(ChatColor.GREEN + "Sent " + placed + " sample modern blocks (persisted, look ~2 blocks +Z).");
			return true;
		}
		return false;
	}

	/** Remove stored overrides (all, or just near the player) and return the count. */
	private int clearOverrides(Player p, boolean all) {
		String world = p.getWorld().getName();
		int px = p.getLocation().getBlockX();
		int pz = p.getLocation().getBlockZ();
		int removed = 0;
		for (String k : new ArrayList<String>(overrides.keySet())) {
			if (all) {
				overrides.remove(k);
				removed++;
				continue;
			}
			String[] parts = k.split(";");
			if (parts.length != 4 || !parts[0].equals(world)) {
				continue;
			}
			try {
				int x = Integer.parseInt(parts[1]);
				int z = Integer.parseInt(parts[3]);
				if (Math.abs(x - px) <= RESEND_RADIUS && Math.abs(z - pz) <= RESEND_RADIUS) {
					overrides.remove(k);
					removed++;
				}
			} catch (NumberFormatException ignored) {
			}
		}
		if (removed > 0) {
			saveOverrides();
		}
		return removed;
	}
}
