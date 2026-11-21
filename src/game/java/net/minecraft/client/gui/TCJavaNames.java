package net.minecraft.client.gui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.profile.EaglerProfile;

/**
 * Treasure Client: a name just for one Java server (java:// through Wispcraft).
 *
 * Cracked Java servers like Pika ask for a Microsoft login when your name belongs to a
 * paid Minecraft account. Instead of signing in, you can pick another name for that one
 * server; everywhere else keeps your normal name. Saved in storage "tcjavanames" as
 * lines of "server\tname" (this browser only).
 */
public final class TCJavaNames {

	private TCJavaNames() {
	}

	private static final String STORE = "tcjavanames";
	/** Wispcraft's kick text when a server asks for a Microsoft login and nobody is signed in. */
	public static final String WISPCRAFT_NEEDS_ACCOUNT = "requires authentication, but you are not logged in";

	private static Map<String, String> names = null;

	/** The address of the server we last tried to join (set by GuiConnecting). */
	public static String lastUri = "";

	/**
	 * True when a kick message from a java:// server looks like an anti-VPN block. Java servers see
	 * the Wisp relay's address instead of the player's, and many players share that relay.
	 */
	public static boolean isVpnKick(String text) {
		if (!isJava(lastUri) || text == null) {
			return false;
		}
		String t = text.replaceAll("\u00a7.", "").toLowerCase();
		return t.contains("vpn") || t.contains("proxy detected") || t.contains("using a proxy")
				|| t.contains("proxies are not") || t.contains("anonymous network") || t.contains("hosting provider")
				|| t.contains("datacenter") || t.contains("data center");
	}

	/** "ws://java://Play.Pika-Network.net/" -> "java://play.pika-network.net" */
	public static String key(String uri) {
		if (uri == null) {
			return "";
		}
		String s = uri.trim().toLowerCase();
		if (s.startsWith("wss://")) {
			s = s.substring(6);
		} else if (s.startsWith("ws://")) {
			s = s.substring(5);
		}
		while (s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	public static boolean isJava(String uri) {
		return key(uri).startsWith("java://");
	}

	/** The server's address without "java://", for showing to the player. */
	public static String shortName(String uri) {
		String k = key(uri);
		return k.startsWith("java://") ? k.substring(7) : k;
	}

	private static Map<String, String> map() {
		if (names == null) {
			names = new LinkedHashMap<String, String>();
			try {
				byte[] b = EagRuntime.getStorage(STORE);
				if (b != null) {
					for (String line : new String(b, StandardCharsets.UTF_8).split("\n")) {
						int t = line.indexOf('\t');
						if (t > 0 && isValid(line.substring(t + 1))) {
							names.put(line.substring(0, t), line.substring(t + 1));
						}
					}
				}
			} catch (Throwable t) {
			}
		}
		return names;
	}

	private static void save() {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : map().entrySet()) {
			sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
		}
		try {
			EagRuntime.setStorage(STORE, sb.toString().getBytes(StandardCharsets.UTF_8));
		} catch (Throwable t) {
		}
	}

	/** The saved name for this server, or null. */
	public static String get(String uri) {
		return isJava(uri) ? map().get(key(uri)) : null;
	}

	/** Name to log in with: the saved one for this java:// server, otherwise the normal one. */
	public static String nameFor(String uri, String normal) {
		String n = get(uri);
		return n != null ? n : normal;
	}

	public static void set(String uri, String name) {
		if (isJava(uri) && isValid(name)) {
			map().put(key(uri), name);
			save();
		}
	}

	public static void forget(String uri) {
		if (map().remove(key(uri)) != null) {
			save();
		}
	}

	/** Minecraft names: 3 to 16 letters, numbers or underscores. */
	public static boolean isValid(String n) {
		if (n == null || n.length() < 3 || n.length() > 16) {
			return false;
		}
		for (int i = 0; i < n.length(); i++) {
			char c = n.charAt(i);
			if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')) {
				return false;
			}
		}
		return true;
	}

	/** Base name with only allowed characters (falls back to "Player"). */
	private static String clean(String n) {
		StringBuilder sb = new StringBuilder();
		if (n != null) {
			for (int i = 0; i < n.length(); i++) {
				char c = n.charAt(i);
				if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
					sb.append(c);
				}
			}
		}
		String s = sb.toString();
		while (s.startsWith("_")) {
			s = s.substring(1);
		}
		while (s.endsWith("_")) {
			s = s.substring(0, s.length() - 1);
		}
		return s.length() >= 2 ? s : "Player";
	}

	private static String cut(String s, int max) {
		return s.length() > max ? s.substring(0, max) : s;
	}

	/**
	 * Names that are probably free: the same name with a tag or a few numbers added. Long,
	 * odd names are very rarely owned. Different every time (press "More ideas").
	 */
	public static List<String> suggestions(String taken, int count) {
		String b = clean(taken);
		List<String> pool = new ArrayList<String>();
		int r = EaglerProfile.rand.nextInt(900) + 100;
		int r2 = EaglerProfile.rand.nextInt(9000) + 1000;
		pool.add(cut(b, 13) + "_TC");
		pool.add(cut(b, 12) + "_" + r);
		pool.add("x" + cut(b, 14) + "x");
		pool.add(cut(b, 12) + r2);
		pool.add("The" + cut(b, 13));
		pool.add("Its" + cut(b, 13));
		pool.add(cut(b, 11) + "_Gold");
		pool.add(cut(b, 13) + "_YT");
		pool.add("Not" + cut(b, 13));
		pool.add(cut(b, 10) + "_" + r + "x");
		List<String> out = new ArrayList<String>();
		while (!pool.isEmpty() && out.size() < count) {
			String s = pool.remove(EaglerProfile.rand.nextInt(pool.size()));
			if (isValid(s) && !s.equalsIgnoreCase(taken) && !out.contains(s)) {
				out.add(s);
			}
		}
		return out;
	}
}
