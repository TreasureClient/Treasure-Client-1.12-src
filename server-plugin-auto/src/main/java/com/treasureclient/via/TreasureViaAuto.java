package com.treasureclient.via;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
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
 * Treasure Client — Option A phase 4: automatic modern-block down-mapping.
 *
 * Runs on a <b>1.13+ Spigot/Paper backend</b> (the phase-3 {@code
 * TreasureViaServer} targets the 1.12 API; this one targets modern). It detects
 * modern blocks that are actually placed in the world — by players, and
 * optionally by world generation on chunk load — and streams OUR ViaBlocks
 * state ids to Treasure Clients over the same {@code TC|ViaBlocks} channel, so
 * modern content appears with <b>no manual commands</b>. The client applies
 * them from its ViaBlocks registry; no client change is needed.
 *
 * <p>Matching is version-robust: our block short-names were deliberately named
 * after the vanilla identifiers, so {@code Material.name().toLowerCase()} (e.g.
 * {@code DEEPSLATE} → {@code deepslate}) is looked up directly in the mapping
 * json. A backend only has a Material constant for a block if it's new enough to
 * know it, so this works across 1.13–1.21 without hardcoding enum constants.
 *
 * <p>Channel caveat: Via rewrites the 1.12 client's legacy {@code TC|ViaBlocks}
 * channel to a namespaced name on the way to a modern backend, and the exact
 * name depends on the Via version. We register several candidates and reply /
 * stream on whichever one the handshake arrives on (see {@code config.yml}).
 *
 * <p>Like phases 2–3 this is client-side/visual: the modern blocks live in the
 * client's view. Overrides are persisted and re-asserted so they stick.
 */
public final class TreasureViaAuto extends JavaPlugin implements PluginMessageListener, Listener {

	public static final int PROTOCOL = 1;
	public static final int OP_ACK = 1;
	public static final int OP_SET_BLOCK = 16;
	public static final int OP_SET_BLOCKS = 17;

	// config
	private List<String> channels;
	private boolean tryLegacyName;
	private long resendPeriodTicks;
	private int resendRadius;
	private boolean scanChunksOnLoad;
	private boolean debug;
	private static final int MAX_BATCH = 2048;

	private final Set<UUID> viaClients = Collections.synchronizedSet(new HashSet<UUID>());
	/** player -> the channel their handshake arrived on (what we reply/stream on). */
	private final Map<UUID, String> clientChannel = new ConcurrentHashMap<UUID, String>();
	/** our block short-name -> base block-state id (meta 0), from the mapping json. */
	private final Map<String, Integer> blockStateIds = new TreeMap<String, Integer>();
	/** persistent overrides: "world;x;y;z" -> stateId. */
	private final Map<String, Integer> overrides = new ConcurrentHashMap<String, Integer>();
	private File blocksFile;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		readConfig();
		loadMapping();
		loadOverrides();
		registerChannels();
		getServer().getPluginManager().registerEvents(this, this);
		getServer().getScheduler().runTaskTimer(this, this::resendNearbyOverrides,
				resendPeriodTicks, resendPeriodTicks);
		getLogger().info("TreasureViaAuto enabled — " + blockStateIds.size() + " mappable blocks, "
				+ overrides.size() + " stored overrides. Listening on channels: " + channels
				+ (scanChunksOnLoad ? " (chunk scan ON)" : ""));
	}

	@Override
	public void onDisable() {
		saveOverrides();
		viaClients.clear();
		clientChannel.clear();
	}

	private void readConfig() {
		channels = new ArrayList<String>(getConfig().getStringList("channels"));
		if (channels.isEmpty()) {
			channels.add("legacy:tc_viablocks");
		}
		tryLegacyName = getConfig().getBoolean("try-legacy-name", true);
		if (tryLegacyName && !channels.contains("TC|ViaBlocks")) {
			channels.add("TC|ViaBlocks");
		}
		resendPeriodTicks = Math.max(20L, getConfig().getLong("resend-period-ticks", 60L));
		resendRadius = Math.max(16, getConfig().getInt("resend-radius", 96));
		scanChunksOnLoad = getConfig().getBoolean("scan-chunks-on-load", false);
		debug = getConfig().getBoolean("debug", false);
	}

	private void registerChannels() {
		List<String> ok = new ArrayList<String>();
		for (String ch : channels) {
			try {
				getServer().getMessenger().registerOutgoingPluginChannel(this, ch);
				getServer().getMessenger().registerIncomingPluginChannel(this, ch, this);
				ok.add(ch);
			} catch (Throwable t) {
				getLogger().warning("Channel '" + ch + "' could not be registered (" + t.getMessage()
						+ ") — skipping. This is expected for non-namespaced names on 1.13+.");
			}
		}
		channels = ok;
	}

	// ---- mapping / persistence ---------------------------------------------
	private void loadMapping() {
		blockStateIds.clear();
		try (InputStream in = getResource("viablocks_mapping.json")) {
			if (in == null) {
				getLogger().warning("viablocks_mapping.json not found in the jar!");
				return;
			}
			JsonObject root = new JsonParser()
					.parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			if (root.has("blocks")) {
				JsonObject blocks = root.getAsJsonObject("blocks");
				for (Map.Entry<String, JsonElement> e : blocks.entrySet()) {
					JsonObject o = e.getValue().getAsJsonObject();
					if (o.has("baseStateId")) {
						blockStateIds.put(e.getKey().toLowerCase(), o.get("baseStateId").getAsInt());
					}
				}
			}
		} catch (Throwable t) {
			getLogger().warning("Failed to load viablocks_mapping.json: " + t);
		}
	}

	private static String key(String world, int x, int y, int z) {
		return world + ';' + x + ';' + y + ';' + z;
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
				cfg.set(e.getKey().replace(';', '|'), e.getValue());
			}
			cfg.save(blocksFile);
		} catch (Throwable t) {
			getLogger().warning("Failed to save blocks.yml: " + t);
		}
	}

	// ---- detection ----------------------------------------------------------
	/** Our base state id for a Bukkit block, or null if we don't map it. */
	private Integer stateIdFor(Block b) {
		try {
			return blockStateIds.get(b.getType().name().toLowerCase());
		} catch (Throwable t) {
			return null;
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent e) {
		Block b = e.getBlockPlaced();
		Integer sid = stateIdFor(b);
		if (sid == null) {
			return;
		}
		String world = b.getWorld().getName();
		overrides.put(key(world, b.getX(), b.getY(), b.getZ()), sid);
		saveOverrides();
		if (debug) {
			getLogger().info("Detected modern block " + b.getType().name() + " -> state " + sid);
		}
		// push to via clients nearby right away
		for (Player p : b.getWorld().getPlayers()) {
			if (isViaClient(p) && near(p, b.getX(), b.getZ())) {
				sendSetBlock(p, b.getX(), b.getY(), b.getZ(), sid);
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onBreak(BlockBreakEvent e) {
		Block b = e.getBlock();
		if (overrides.remove(key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ())) != null) {
			saveOverrides();
		}
	}

	@EventHandler
	public void onChunkLoad(ChunkLoadEvent e) {
		if (scanChunksOnLoad) {
			scanChunk(e.getChunk());
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		viaClients.remove(e.getPlayer().getUniqueId());
		clientChannel.remove(e.getPlayer().getUniqueId());
	}

	/** Scan a loaded chunk for mappable blocks and store them (resend task sends). */
	private int scanChunk(Chunk chunk) {
		World w = chunk.getWorld();
		int minY, maxY;
		try {
			minY = w.getMinHeight();
			maxY = w.getMaxHeight();
		} catch (Throwable t) {
			minY = 0;
			maxY = 256;
		}
		int baseX = chunk.getX() << 4;
		int baseZ = chunk.getZ() << 4;
		int found = 0;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				for (int y = minY; y < maxY; y++) {
					Block b = chunk.getBlock(dx, y, dz);
					Integer sid = stateIdFor(b);
					if (sid != null) {
						overrides.put(key(w.getName(), baseX + dx, y, baseZ + dz), sid);
						found++;
					}
				}
			}
		}
		if (found > 0) {
			saveOverrides();
			if (debug) {
				getLogger().info("Chunk scan (" + chunk.getX() + "," + chunk.getZ() + "): "
						+ found + " modern blocks found.");
			}
		}
		return found;
	}

	// ---- handshake + sending -----------------------------------------------
	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (player == null || !channels.contains(channel)) {
			return;
		}
		try {
			int clientProto = (message != null && message.length > 0) ? (message[0] & 0xFF) : 0;
			boolean isNew = viaClients.add(player.getUniqueId());
			clientChannel.put(player.getUniqueId(), channel);
			getLogger().info("Treasure Client detected: " + player.getName() + " (proto " + clientProto
					+ ") on channel '" + channel + "'");
			player.sendPluginMessage(this, channel, new byte[] { (byte) OP_ACK });
			if (isNew) {
				sendOverridesTo(player);
			}
		} catch (Throwable t) {
			getLogger().warning("ViaBlocks handshake error: " + t);
		}
	}

	public boolean isViaClient(Player player) {
		return player != null && viaClients.contains(player.getUniqueId());
	}

	private String channelFor(Player p) {
		String ch = clientChannel.get(p.getUniqueId());
		return (ch != null) ? ch : (channels.isEmpty() ? null : channels.get(0));
	}

	private boolean near(Player p, int x, int z) {
		return Math.abs(x - p.getLocation().getBlockX()) <= resendRadius
				&& Math.abs(z - p.getLocation().getBlockZ()) <= resendRadius;
	}

	private void sendSetBlock(Player p, int x, int y, int z, int stateId) {
		String ch = channelFor(p);
		if (ch == null) {
			return;
		}
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
		p.sendPluginMessage(this, ch, bos.toByteArray());
	}

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
		String ch = channelFor(p);
		if (ch == null) {
			return;
		}
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
			if (Math.abs(x - px) > resendRadius || Math.abs(z - pz) > resendRadius) {
				continue;
			}
			batch.add(new int[] { x, y, z, e.getValue() });
			if (batch.size() >= MAX_BATCH) {
				flushBatch(p, ch, batch);
				batch.clear();
			}
		}
		if (!batch.isEmpty()) {
			flushBatch(p, ch, batch);
		}
	}

	private void flushBatch(Player p, String ch, List<int[]> batch) {
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
		p.sendPluginMessage(this, ch, bos.toByteArray());
	}

	// ---- command ------------------------------------------------------------
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!command.getName().equalsIgnoreCase("viaauto")) {
			return false;
		}
		String sub = (args.length >= 1) ? args[0].toLowerCase() : "status";

		if (sub.equals("status")) {
			sender.sendMessage(ChatColor.GOLD + "TreasureViaAuto: " + ChatColor.GRAY
					+ blockStateIds.size() + " mappable blocks, " + overrides.size() + " stored overrides, "
					+ viaClients.size() + " detected client(s).");
			sender.sendMessage(ChatColor.GOLD + "Channels: " + ChatColor.GRAY + channels);
			sender.sendMessage(ChatColor.GOLD + "Chunk scan: " + ChatColor.GRAY
					+ (scanChunksOnLoad ? "on" : "off") + ", radius " + resendRadius);
			return true;
		}

		if (sub.equals("reload")) {
			reloadConfig();
			readConfig();
			loadMapping();
			sender.sendMessage(ChatColor.GREEN + "Reloaded config + mapping. (Channel re-registration "
					+ "needs a server restart.)");
			return true;
		}

		if (sub.equals("clear")) {
			int n = overrides.size();
			overrides.clear();
			saveOverrides();
			sender.sendMessage(ChatColor.GREEN + "Cleared " + n + " stored override(s).");
			return true;
		}

		if (sub.equals("scan")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("Players only for scan.");
				return true;
			}
			Player p = (Player) sender;
			int total = 0;
			for (Chunk c : p.getWorld().getLoadedChunks()) {
				if (Math.abs((c.getX() << 4) - p.getLocation().getBlockX()) <= resendRadius
						&& Math.abs((c.getZ() << 4) - p.getLocation().getBlockZ()) <= resendRadius) {
					total += scanChunk(c);
				}
			}
			p.sendMessage(ChatColor.GREEN + "Scanned nearby chunks: " + total + " modern block(s) stored.");
			if (isViaClient(p)) {
				sendOverridesTo(p);
			}
			return true;
		}

		sender.sendMessage(ChatColor.RED + "Usage: /viaauto <status|reload|scan|clear>");
		return true;
	}
}
