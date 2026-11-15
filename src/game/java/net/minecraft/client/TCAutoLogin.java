package net.minecraft.client;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import net.lax1dude.eaglercraft.EagRuntime;
import net.minecraft.client.gui.TreasureMods;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.text.TextComponentString;

/**
 * Treasure Client: Auto Login. Remembers the password you type in /login, /l,
 * /register, /reg or /changepassword, and when a server asks you to log in (a chat
 * line, title or action bar mentioning "/login") it types /login with that password
 * for you, once per join.
 *
 * Passwords are kept per server address AND username, so a password is only ever
 * sent back to the same server, for the same name, that it was typed on. They are
 * stored only in this browser (localStorage "_eaglercraft_1.12.tclogin"). If the server
 * says the password was wrong, the saved one is forgotten so it never keeps retrying.
 */
public final class TCAutoLogin {

	private TCAutoLogin() {
	}

	private static final String STORE = "tclogin";
	/** Wait a little after the prompt, like a person would, and so the server is ready. */
	private static final long DELAY_MS = 900L;
	/** How long after sending we still treat "wrong password" as an answer to us. */
	private static final long ANSWER_MS = 10000L;

	private static Map<String, String> saved;
	private static Object promptedConn;
	private static String sendKey, sendPw;
	private static long sendAt, sentAt;
	private static String sentKey;

	/** "server address|username" for the current server, or null in singleplayer. */
	private static String key(Minecraft mc) {
		try {
			ServerData sd = mc.getCurrentServerData();
			if (sd == null || sd.serverIP == null || mc.getSession() == null) {
				return null;
			}
			String ip = sd.serverIP.trim().toLowerCase();
			String user = mc.getSession().getUsername();
			if (ip.isEmpty() || user == null || user.isEmpty()) {
				return null;
			}
			return ip + "|" + user.toLowerCase();
		} catch (Throwable t) {
			return null;
		}
	}

	private static Map<String, String> load() {
		if (saved != null) {
			return saved;
		}
		saved = new LinkedHashMap<String, String>();
		try {
			byte[] b = EagRuntime.getStorage(STORE);
			if (b != null) {
				String[] lines = new String(b, StandardCharsets.UTF_8).split("\n");
				for (String ln : lines) {
					int tab = ln.indexOf('\t');
					if (tab > 0) {
						String pw = new String(net.lax1dude.eaglercraft.Base64.decodeBase64(ln.substring(tab + 1)),
								StandardCharsets.UTF_8);
						saved.put(ln.substring(0, tab), pw);
					}
				}
			}
		} catch (Throwable t) {
		}
		return saved;
	}

	private static void store() {
		try {
			StringBuilder sb = new StringBuilder();
			for (Map.Entry<String, String> e : load().entrySet()) {
				sb.append(e.getKey()).append('\t').append(net.lax1dude.eaglercraft.Base64
						.encodeBase64String(e.getValue().getBytes(StandardCharsets.UTF_8))).append('\n');
			}
			EagRuntime.setStorage(STORE, sb.length() > 0 ? sb.toString().getBytes(StandardCharsets.UTF_8) : null);
		} catch (Throwable t) {
		}
	}

	/** A chat line you are sending: remember the password from login commands. */
	public static void onSend(Minecraft mc, String message) {
		if (!TreasureMods.autoLogin() || message == null || !message.startsWith("/")) {
			return;
		}
		String[] p = message.trim().split("\\s+");
		if (p.length < 2) {
			return;
		}
		String cmd = p[0].toLowerCase();
		String pw = null;
		if (cmd.equals("/login") || cmd.equals("/l") || cmd.equals("/log")) {
			pw = p[1];
		} else if (cmd.equals("/register") || cmd.equals("/reg")) {
			// "/register pw pw": only when both match (a typo would save the wrong one)
			if (p.length == 2 || p[1].equals(p[2])) {
				pw = p[1];
			}
		} else if ((cmd.equals("/changepassword") || cmd.equals("/changepass")) && p.length >= 3) {
			pw = p[2];
		}
		if (pw == null || pw.length() > 100) {
			return;
		}
		String k = key(mc);
		if (k == null) {
			return;
		}
		if (!pw.equals(load().get(k))) {
			load().put(k, pw);
			store();
			say(mc, "§7Password saved for this server. It will log you in next time.");
		}
	}

	/** Text the server showed (chat, action bar, title or subtitle). */
	public static void onServerText(Minecraft mc, String text) {
		if (!TreasureMods.autoLogin() || text == null || mc.player == null) {
			return;
		}
		String t = text.trim().toLowerCase();
		if (t.isEmpty() || t.length() > 200) {
			return;
		}
		long now = EagRuntime.steadyTimeMillis();
		if (sentKey != null && now - sentAt < ANSWER_MS && fromServer(t, "password")
				&& (t.contains("wrong") || t.contains("incorrect") || t.contains("invalid") || t.contains("not correct"))) {
			load().remove(sentKey);
			store();
			sentKey = null;
			say(mc, "§cThe saved password was wrong, so it was forgotten. §7Type /login yourself to save the new one.");
			return;
		}
		if (!fromServer(t, "/login") && !fromServer(t, "/l <") && !fromServer(t, "/l [")) {
			return;
		}
		Object conn = mc.getConnection();
		if (conn == null || conn == promptedConn) {
			return; // once per join: never spam a server with attempts
		}
		String k = key(mc);
		String pw = k != null ? load().get(k) : null;
		if (pw == null) {
			return;
		}
		promptedConn = conn;
		sendKey = k;
		sendPw = pw;
		sendAt = now + DELAY_MS;
	}

	/** True when the line has the phrase and isn't a player typing it ("Name: ...", "<Name> ..."). */
	private static boolean fromServer(String t, String phrase) {
		int i = t.indexOf(phrase);
		if (i < 0) {
			return false;
		}
		String before = t.substring(0, i);
		return before.indexOf('>') < 0 && before.indexOf('»') < 0 && !before.matches(".*\\w: .*");
	}

	/** Sends the login once its delay is up, if we're still on that server. */
	public static void tick(Minecraft mc) {
		if (sendPw == null || EagRuntime.steadyTimeMillis() < sendAt) {
			return;
		}
		String k = sendKey, pw = sendPw;
		sendKey = null;
		sendPw = null;
		if (mc.player == null || mc.world == null || !TreasureMods.autoLogin() || !k.equals(key(mc))) {
			return;
		}
		sentKey = k;
		sentAt = EagRuntime.steadyTimeMillis();
		mc.player.sendChatMessage("/login " + pw);
		say(mc, "§7Logged you in with your saved password.");
	}

	/** ".autologin", ".autologin forget", ".autologin forgetall". */
	public static void command(Minecraft mc, String args) {
		String a = args == null ? "" : args.trim().toLowerCase();
		String k = key(mc);
		if (a.equals("forgetall")) {
			int n = load().size();
			load().clear();
			store();
			say(mc, "§7Forgot " + n + " saved password" + (n == 1 ? "" : "s") + ".");
		} else if (a.equals("forget")) {
			if (k != null && load().remove(k) != null) {
				store();
				say(mc, "§7Forgot the saved password for this server.");
			} else {
				say(mc, "§7No saved password for this server.");
			}
		} else {
			boolean here = k != null && load().containsKey(k);
			say(mc, "§7" + (TreasureMods.autoLogin() ? "On" : "Off (turn it on in the mod menu)") + ". "
					+ (k == null ? "Not on a server." : here ? "This server has a saved password." : "No saved password for this server.")
					+ " Saved for " + load().size() + " server" + (load().size() == 1 ? "" : "s")
					+ ". §8.autologin forget · .autologin forgetall");
		}
	}

	private static void say(Minecraft mc, String msg) {
		try {
			mc.ingameGUI.getChatGUI().printChatMessage(new TextComponentString("§6[Auto Login] " + msg));
		} catch (Throwable t) {
		}
	}
}
