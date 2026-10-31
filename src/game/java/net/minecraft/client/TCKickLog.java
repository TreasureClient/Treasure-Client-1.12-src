package net.minecraft.client;

import java.util.ArrayList;
import java.util.List;

import net.lax1dude.eaglercraft.EagRuntime;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.client.CPacketUseEntity;

/**
 * Treasure Client: remembers the last packets the game sent to the server. When the
 * server kicks you, the disconnect screen shows them, so a "suspicious packets" kick
 * can be traced to the packet that caused it. ".kicklog" shows the last one again.
 */
public class TCKickLog {

	private static final int SIZE = 64;
	private static final String[] names = new String[SIZE];
	private static final long[] times = new long[SIZE];
	private static int head, count;

	/** Lines from the last kick (empty if none yet). */
	private static List<String> lastKick = new ArrayList<String>();

	public static void sent(Packet<?> p) {
		String n;
		try {
			n = name(p);
		} catch (Throwable t) {
			n = "?";
		}
		names[head] = n;
		times[head] = EagRuntime.steadyTimeMillis();
		head = (head + 1) % SIZE;
		if (count < SIZE) ++count;
	}

	private static String name(Packet<?> p) {
		String n = p.getClass().getSimpleName();
		if (n.startsWith("CPacket")) n = n.substring(7);
		if (p instanceof CPacketEntityAction) {
			n += " " + ((CPacketEntityAction) p).getAction();
		} else if (p instanceof CPacketPlayerDigging) {
			n += " " + ((CPacketPlayerDigging) p).getAction();
		} else if (p instanceof CPacketUseEntity) {
			n += " " + ((CPacketUseEntity) p).getAction();
		}
		return n;
	}

	/** Called when you get disconnected: saves the last couple of seconds of packets. */
	public static List<String> snapshot() {
		List<String> out = new ArrayList<String>();
		if (count == 0) {
			return out; // nothing sent since the last disconnect
		}
		long now = EagRuntime.steadyTimeMillis();
		String prev = null;
		long prevT = 0;
		int repeat = 0;
		int start = (head - count + SIZE) % SIZE;
		for (int k = 0; k < count; ++k) {
			int i = (start + k) % SIZE;
			if (now - times[i] > 3000L) continue; // only the last 3 seconds
			if (names[i].equals(prev)) {
				++repeat;
				continue;
			}
			if (prev != null) out.add(line(prev, prevT, now, repeat));
			prev = names[i];
			prevT = times[i];
			repeat = 1;
		}
		if (prev != null) out.add(line(prev, prevT, now, repeat));
		while (out.size() > 14) out.remove(0);
		count = 0;
		lastKick = out;
		return out;
	}

	private static String line(String n, long t, long now, int repeat) {
		long ms = now - t;
		return "-" + (ms / 1000L) + "." + ((ms / 100L) % 10L) + "s  " + n + (repeat > 1 ? " x" + repeat : "");
	}

	public static List<String> last() {
		return lastKick;
	}
}
