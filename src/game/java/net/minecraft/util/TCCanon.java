package net.minecraft.util;

import java.util.HashMap;

/**
 * Treasure Client: CensoredASM-style string deduplication (inspired by
 * Rongmario's CensoredASM / LoliASM, LGPL-2.1). Many identical strings get
 * made over and over: block and item names in resource locations, texture
 * names, and NBT tag names like "id" or "display". This keeps one shared copy
 * of each. The table stops growing at a fixed size, so odd one-off strings
 * can't fill memory.
 */
public final class TCCanon {

	private static final int MAX = 32768;
	private static final HashMap<String, String> table = new HashMap<>(4096);

	private TCCanon() {
	}

	public static String of(String s) {
		if (s == null) {
			return null;
		}
		if (s.equals("minecraft")) {
			return "minecraft";
		}
		String c = table.get(s);
		if (c != null) {
			return c;
		}
		if (table.size() < MAX && s.length() <= 128) {
			table.put(s, s);
		}
		return s;
	}

	public static int size() {
		return table.size();
	}
}
