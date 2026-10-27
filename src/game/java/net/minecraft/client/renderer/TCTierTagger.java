package net.minecraft.client.renderer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.lax1dude.eaglercraft.EagRuntime;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.TreasureMods;

/**
 * Treasure Client: Tier Tagger. Shows a player's Eaglercraft Ranked tier and
 * role (play.eaglercraftranked.com) and their EaglerTiers tier (eaglertiers.com)
 * before their name, e.g. "[Mod] G3 HT3 | Steve".
 * Inspired by the TierTagger mod.
 *
 * <p>The game can't make web requests itself, so it asks the Treasure Settings
 * overlay: names to look up go in the "tcttreq" storage key, and the overlay
 * fetches them and writes the results to "tctiers" as lines of
 * {@code name=role|overall:gold_3,sword:emerald_1,...} (empty = not found).
 */
public final class TCTierTagger {

	private TCTierTagger() {
	}

	/** Lower-case name -> tier data ("" = not on the tierlist). */
	private static final Map<String, String> DATA = new HashMap<>();
	private static final Set<String> REQUESTED = new HashSet<>();
	private static final List<String> PENDING = new ArrayList<>();
	private static long lastPoll = 0L;
	private static String lastRaw = null;

	/** Returns the name to draw above a player, with their tier in front when known. */
	public static String decorate(String playerName, String display) {
		if (!TreasureMods.tierTagger() || playerName == null || display == null) {
			return display;
		}
		poll();
		String key = playerName.toLowerCase();
		String data = DATA.get(key);
		if (data == null) {
			if (isValidName(playerName) && REQUESTED.add(key)) {
				PENDING.add(playerName);
			}
			return display;
		}
		String tag = format(data, TreasureMods.tierTaggerMode());
		return tag == null ? display : tag + " §8| §r" + display;
	}

	private static boolean isValidName(String n) {
		int len = n.length();
		if (len < 3 || len > 16) {
			return false;
		}
		for (int i = 0; i < len; ++i) {
			char c = n.charAt(i);
			if (!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_')) {
				return false;
			}
		}
		return true;
	}

	/** Every half second: send new lookups to the overlay and read its results. */
	private static void poll() {
		long now = EagRuntime.steadyTimeMillis();
		if (now - lastPoll < 500L) {
			return;
		}
		lastPoll = now;
		try {
			if (!PENDING.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				byte[] old = EagRuntime.getStorage("tcttreq");
				if (old != null && old.length > 0) {
					sb.append(new String(old, StandardCharsets.UTF_8));
				}
				for (int i = 0, n = PENDING.size(); i < n; ++i) {
					if (sb.length() > 0) {
						sb.append(',');
					}
					sb.append(PENDING.get(i));
				}
				PENDING.clear();
				EagRuntime.setStorage("tcttreq", sb.toString().getBytes(StandardCharsets.UTF_8));
			}
			byte[] raw = EagRuntime.getStorage("tctiers");
			String s = raw != null ? new String(raw, StandardCharsets.UTF_8) : "";
			if (!s.equals(lastRaw)) {
				lastRaw = s;
				for (String line : s.split("\n")) {
					int eq = line.indexOf('=');
					if (eq > 0) {
						DATA.put(line.substring(0, eq).toLowerCase(), line.substring(eq + 1));
					}
				}
			}
		} catch (Throwable t) {
		}
	}

	/**
	 * Builds the colored tag, e.g. "§4[Admin] §6G3 §cHT3", for the chosen gamemode
	 * ("highest" = overall rank), or null when there is nothing to show.
	 * Data is {@code role|overall:gold_3,sword:emerald_1,...|sword:HT3,...}: the
	 * Eaglercraft Ranked role and ranks, then the EaglerTiers tiers.
	 */
	static String format(String data, String mode) {
		String[] parts = data.split("\\|", -1);
		if (parts.length < 2) {
			return null;
		}
		String role = roleTag(parts[0]);
		String overall = null, chosen = null;
		for (String entry : parts[1].split(",")) {
			int c = entry.indexOf(':');
			if (c <= 0) {
				continue;
			}
			String m = entry.substring(0, c), rank = entry.substring(c + 1);
			if (m.equals("overall")) {
				overall = rank;
			} else if (m.equals(mode)) {
				chosen = rank;
			}
		}
		String tier = tierTag(chosen);
		if (tier == null) {
			tier = tierTag(overall); // "Overall", or unranked in the chosen gamemode
		}
		String et = parts.length >= 3 ? eaglerTiersTag(parts[2], mode) : null;
		StringBuilder sb = new StringBuilder();
		for (String t : new String[] { role, tier, et }) {
			if (t != null) {
				if (sb.length() > 0) {
					sb.append(' ');
				}
				sb.append(t);
			}
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	/**
	 * EaglerTiers tag for the gamemode, or their best tier ("highest", or not
	 * tested in that gamemode). Data is {@code sword:HT3,axe:RLT2,...}, R = retired.
	 */
	private static String eaglerTiersTag(String data, String mode) {
		if (data.isEmpty()) {
			return null;
		}
		if ("netheriteop".equals(mode)) {
			mode = "nethop"; // EaglerTiers' name for Neth OP
		}
		String best = null, chosen = null;
		int bestScore = Integer.MAX_VALUE;
		for (String entry : data.split(",")) {
			int c = entry.indexOf(':');
			if (c <= 0) {
				continue;
			}
			String m = entry.substring(0, c), t = entry.substring(c + 1);
			boolean retired = t.startsWith("R");
			String code = retired ? t.substring(1) : t;
			if (code.length() != 3 || code.charAt(1) != 'T') {
				continue;
			}
			int num = code.charAt(2) - '0';
			if (num < 1 || num > 5) {
				continue;
			}
			// lower is better: HT1 < LT1 < HT2 ..., active beats retired
			int score = num * 2 + (code.charAt(0) == 'H' ? 0 : 1) + (retired ? 100 : 0);
			if (score < bestScore) {
				bestScore = score;
				best = t;
			}
			if (m.equals(mode)) {
				chosen = t;
			}
		}
		String t = chosen != null ? chosen : best;
		if (t == null) {
			return null;
		}
		boolean retired = t.startsWith("R");
		String code = retired ? t.substring(1) : t;
		boolean high = code.charAt(0) == 'H';
		String color;
		switch (code.charAt(2)) {
		case '1':
			color = high ? "§6" : "§e"; // gold
			break;
		case '2':
			color = high ? "§f" : "§7"; // silver
			break;
		case '3':
			color = "§c"; // bronze
			break;
		default:
			color = high ? "§9" : "§8";
		}
		return (retired ? "§8R" : "") + color + code;
	}

	/** "gold_3" -> "G3", "netherite" -> "NT", in the Eaglercraft Ranked site colors; null for unranked. */
	private static String tierTag(String rank) {
		if (rank == null || rank.isEmpty() || rank.startsWith("unranked")) {
			return null;
		}
		int color;
		String code;
		switch (rank) {
		case "coal_1": color = 0x4A4A4A; code = "C1"; break;
		case "coal_2": color = 0x5C5C5C; code = "C2"; break;
		case "coal_3": color = 0x707070; code = "C3"; break;
		case "iron_1": color = 0x9FA6AD; code = "I1"; break;
		case "iron_2": color = 0xB7BEC4; code = "I2"; break;
		case "iron_3": color = 0xD4D9DD; code = "I3"; break;
		case "gold_1": color = 0xD9A824; code = "G1"; break;
		case "gold_2": color = 0xE8BC2F; code = "G2"; break;
		case "gold_3": color = 0xF7D33E; code = "G3"; break;
		case "emerald_1": color = 0x2F9E57; code = "E1"; break;
		case "emerald_2": color = 0x25B869; code = "E2"; break;
		case "emerald_3": color = 0x1FD67D; code = "E3"; break;
		case "diamond_1": color = 0x3FC7E0; code = "D1"; break;
		case "diamond_2": color = 0x4FDCF5; code = "D2"; break;
		case "diamond_3": color = 0x7BE9FF; code = "D3"; break;
		case "netherite": color = 0xB96CFF; code = "NT"; break;
		case "champion": color = 0xFF3B3B; code = "CH"; break;
		default:
			return null;
		}
		return FontRenderer.tcColor(color) + code;
	}

	/** Eaglercraft Ranked role tag in the site's role color, or null. */
	private static String roleTag(String role) {
		int color;
		String label;
		switch (role) {
		case "admin": color = 0xBB0C00; label = "Admin"; break;
		case "moderator": color = 0x2ECC71; label = "Mod"; break;
		case "helper": color = 0xADD8E6; label = "Helper"; break;
		case "media": color = 0xFF4A2A; label = "Media"; break;
		case "booster": color = 0xFF73FA; label = "Booster"; break;
		case "supporter": color = 0xF4B7FF; label = "Supporter"; break;
		case "premium": color = 0xFFD700; label = "Premium"; break;
		default:
			return null;
		}
		return FontRenderer.tcColor(color) + "[" + label + "]";
	}
}
