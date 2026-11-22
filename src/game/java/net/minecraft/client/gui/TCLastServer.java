package net.minecraft.client.gui;

import java.nio.charset.StandardCharsets;

import net.lax1dude.eaglercraft.EagRuntime;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

/**
 * Treasure Client: remembers the last server you joined, for the title screen's "Rejoin" button.
 * Saved as "name\naddress" in storage key "tclastserver".
 */
public final class TCLastServer {
	private static final String STORE = "tclastserver";
	private static String name = null;
	private static String ip = null;
	private static boolean loaded = false;

	private TCLastServer() {
	}

	public static void remember(ServerData data) {
		if (data == null || data.serverIP == null) {
			return;
		}
		String a = data.serverIP.trim();
		if (a.isEmpty() || a.toLowerCase().startsWith("settings://")) {
			return; // the Java Servers settings window is not a real server
		}
		String n = data.serverName == null || data.serverName.trim().isEmpty() ? a : data.serverName.trim();
		name = n.replace('\n', ' ');
		ip = a;
		loaded = true;
		try {
			EagRuntime.setStorage(STORE, (name + "\n" + ip).getBytes(StandardCharsets.UTF_8));
		} catch (Throwable t) {
		}
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			byte[] b = EagRuntime.getStorage(STORE);
			if (b != null && b.length > 0) {
				String s = new String(b, StandardCharsets.UTF_8);
				int i = s.indexOf('\n');
				if (i > 0 && i < s.length() - 1) {
					name = s.substring(0, i);
					ip = s.substring(i + 1).trim();
				}
			}
		} catch (Throwable t) {
		}
	}

	public static boolean has() {
		load();
		return ip != null && !ip.isEmpty();
	}

	public static String name() {
		load();
		return name;
	}

	/** The saved server-list entry for that address (keeps its settings), or a new entry. */
	public static ServerData data() {
		load();
		if (ip == null) {
			return null;
		}
		try {
			ServerList list = ServerList.getServerList();
			for (int i = 0, n = list.countServers(); i < n; ++i) {
				ServerData d = list.getServerData(i);
				if (d != null && d.serverIP != null && d.serverIP.trim().equalsIgnoreCase(ip)) {
					return d;
				}
			}
		} catch (Throwable t) {
		}
		return new ServerData(name, ip, false);
	}
}
