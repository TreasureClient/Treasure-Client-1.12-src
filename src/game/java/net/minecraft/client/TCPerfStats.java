package net.minecraft.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.EagRuntime;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.util.text.TextComponentString;

/**
 * Treasure Client: the ".perf" chat command. Shows counts of things that could
 * pile up during a long session (entities, chunks, textures, scoreboard teams...)
 * and how much each has grown since the first ".perf". Whatever keeps growing
 * from game to game is what slows the game down.
 */
public final class TCPerfStats {

	private TCPerfStats() {
	}

	private static final Map<String, Long> START = new HashMap<>();
	private static long startTime = -1L;

	public static void print(Minecraft mc) {
		List<String> names = new ArrayList<>();
		List<Long> values = new ArrayList<>();
		WorldClient w = mc.world;
		add(names, values, "FPS", Minecraft.getDebugFPS());
		long[] heap = pageMemory();
		if (heap != null) {
			add(names, values, "Page memory MB", heap[0]);
		}
		if (w != null) {
			add(names, values, "Entities", w.loadedEntityList.size());
			add(names, values, "Entity set", w.tcEntitySetSize());
			add(names, values, "Spawn queue", w.tcSpawnQueueSize());
			add(names, values, "Block entities", w.loadedTileEntityList.size());
			add(names, values, "Ticking block ents", w.tickableTileEntities.size());
			add(names, values, "Stale block ents", staleTileEntities(w));
			add(names, values, "Weather effects", w.weatherEffects.size());
			add(names, values, "Chunks", count(w.getProviderName()));
			add(names, values, "Score teams", w.getScoreboard().getTeams().size());
			add(names, values, "Score objectives", w.getScoreboard().getScoreObjectives().size());
		}
		if (mc.player != null && mc.player.connection != null) {
			add(names, values, "Tab list players", mc.player.connection.getPlayerInfoMap().size());
		}
		add(names, values, "Particles", count(mc.effectRenderer.getStatistics()));
		add(names, values, "Textures", mc.getTextureManager().tcCount());
		add(names, values, "Sent chat history", mc.ingameGUI.getChatGUI().getSentMessages().size());

		long now = EagRuntime.steadyTimeMillis();
		boolean first = startTime < 0L;
		if (first) {
			startTime = now;
			for (int i = 0; i < names.size(); ++i) {
				START.put(names.get(i), values.get(i));
			}
		}
		String breakdown = (w != null) ? tileEntityBreakdown(w) : null;
		say(mc, "§6[Perf] §f" + (first ? "first reading saved, run .perf again when it gets laggy"
				: "change since first .perf, " + ((now - startTime) / 60000L) + " min ago"));
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < names.size(); ++i) {
			String n = names.get(i);
			long v = values.get(i);
			StringBuilder part = new StringBuilder("§7" + n + ": §f" + v);
			Long s = START.get(n);
			if (!first && s != null && s.longValue() != v) {
				long d = v - s.longValue();
				part.append(d > 0 ? " §c(+" + d + ")" : " §a(" + d + ")");
			}
			if (line.length() > 0) {
				line.append("§8 | ");
			}
			line.append(part);
			if (i % 3 == 2 || i == names.size() - 1) {
				say(mc, line.toString());
				line.setLength(0);
			}
		}
		if (breakdown != null && breakdown.length() > 0) {
			say(mc, "§7Block entity kinds: §f" + breakdown);
		}
		if (heap == null) {
			say(mc, "§7Page memory: not available in this browser");
		} else {
			say(mc, "§7Page memory limit: §f" + heap[1] + " MB");
		}
	}

	/** Block entities still in the world's list whose chunk is gone or that were replaced (a leak if this grows). */
	private static int staleTileEntities(WorldClient w) {
		int n = 0;
		for (int i = 0, l = w.loadedTileEntityList.size(); i < l; ++i) {
			TileEntity te = w.loadedTileEntityList.get(i);
			BlockPos p = te.getPos();
			if (te.isInvalid() || !w.isBlockLoaded(p, false)) {
				++n;
				continue;
			}
			Chunk c = w.getChunkFromBlockCoords(p);
			if (c.getTileEntity(p, Chunk.EnumCreateEntityType.CHECK) != te) {
				++n;
			}
		}
		return n;
	}

	/** The most common kinds of block entity, e.g. "Skull 900, Sign 400". */
	private static String tileEntityBreakdown(WorldClient w) {
		Map<String, Integer> counts = new HashMap<>();
		for (int i = 0, l = w.loadedTileEntityList.size(); i < l; ++i) {
			String n = w.loadedTileEntityList.get(i).getClass().getSimpleName();
			if (n.startsWith("TileEntity")) {
				n = n.substring(10);
			}
			Integer c = counts.get(n);
			counts.put(n, c == null ? 1 : c + 1);
		}
		List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
		list.sort((a, b) -> b.getValue() - a.getValue());
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < list.size() && i < 5; ++i) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(list.get(i).getKey()).append(' ').append(list.get(i).getValue());
		}
		return sb.toString();
	}

	/** Used and max page memory in MB, shared by the overlay (Chrome only), or null. */
	private static long[] pageMemory() {
		try {
			byte[] b = EagRuntime.getStorage("tcheap");
			if (b == null) {
				return null;
			}
			String[] parts = new String(b, "UTF-8").trim().split(",");
			return new long[] { Long.parseLong(parts[0]), Long.parseLong(parts[1]) };
		} catch (Throwable t) {
			return null;
		}
	}

	private static void add(List<String> names, List<Long> values, String name, long v) {
		names.add(name);
		values.add(Long.valueOf(v));
	}

	/** First number in a debug string like "MultiplayerChunkCache: 441, 441". */
	private static long count(String s) {
		if (s == null) {
			return 0L;
		}
		long n = 0L;
		boolean found = false;
		for (int i = 0; i < s.length(); ++i) {
			char c = s.charAt(i);
			if (c >= '0' && c <= '9') {
				n = n * 10L + (c - '0');
				found = true;
			} else if (found) {
				break;
			}
		}
		return n;
	}

	private static void say(Minecraft mc, String s) {
		mc.ingameGUI.getChatGUI().printChatMessage(new TextComponentString(s));
	}
}
