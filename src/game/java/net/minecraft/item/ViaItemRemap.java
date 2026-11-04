package net.minecraft.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.init.Items;

/**
 * Treasure Client — client-side ViaItems for real servers (e.g. arch.mc).
 *
 * Modern servers behind ViaVersion/ViaBackwards down-map modern items (a 1.21
 * mace, trident, netherite gear, …) to a 1.12 fallback item before they reach an
 * Eaglercraft 1.12 client — but they keep the item's modern <b>display name</b>
 * (and ViaVersion backup NBT). TuffX+ doesn't touch items, so no server hands us
 * the real id; instead we recover it here on the client: when an item's display
 * name names one of our registered {@link ViaItems}, we render OUR item in its
 * place (keeping the original name/lore/NBT), so the mace looks like a mace.
 *
 * Name-based, so it works on any server that keeps the modern item name (as
 * arch.mc does: "1.20.5 Mace"). Purely visual/client-side and gated by the
 * ViaVersion Mod Menu toggle. Fully defensive.
 */
public final class ViaItemRemap {

	private ViaItemRemap() {
	}

	/** [searchPhrase (lowercased, spaces), registry name], longest phrase first. */
	private static List<String[]> phrases;

	/** lowercased real display name -> registry name (from {@link ViaItemData}). */
	private static java.util.Map<String, String> exactNames;

	private static void ensure() {
		if (phrases != null) {
			return;
		}
		java.util.Map<String, String> ex = new java.util.HashMap<String, String>();
		for (int i = 0; i < ViaItemData.DISPLAY.length; i++) {
			ex.put(ViaItemData.DISPLAY[i][1].toLowerCase(), ViaItemData.DISPLAY[i][0]);
		}
		for (int i = 0; i < ViaItems.NAMES.length; i++) {
			String phrase = ViaItems.NAMES[i].replace('_', ' ');
			if (!ex.containsKey(phrase)) {
				ex.put(phrase, ViaItems.NAMES[i]);
			}
		}
		// Modern blocks too ("1.17 Cobbled Deepslate", "1.16 Soul Soil"): exact names only,
		// so a block name never matches part of some other item's name.
		addBlockNames(ex, net.minecraft.block.ViaBlocks.NAMES);
		addBlockNames(ex, net.minecraft.block.ViaBlocks.SLAB_NAMES);
		addBlockNames(ex, net.minecraft.block.ViaBlocks.WALL_NAMES);
		addBlockNames(ex, net.minecraft.block.ViaBlocks.EXTRA_NAMES);
		exactNames = ex;
		List<String[]> p = new ArrayList<String[]>();
		for (int i = 0; i < ViaItems.NAMES.length; i++) {
			String name = ViaItems.NAMES[i];
			p.add(new String[] { name.replace('_', ' '), name });
		}
		// Longest phrase first so "netherite sword" wins over a bare "sword".
		Collections.sort(p, new Comparator<String[]>() {
			@Override
			public int compare(String[] a, String[] b) {
				return b[0].length() - a[0].length();
			}
		});
		// Parallel plain arrays (no arrays inside collections at match time), plus a letter
		// fingerprint per phrase so most phrases are ruled out with one comparison.
		int n = p.size();
		String[] text = new String[n];
		String[] reg = new String[n];
		long[] mask = new long[n];
		for (int i = 0; i < n; i++) {
			String[] pr = p.get(i);
			text[i] = pr[0];
			reg[i] = pr[1];
			mask[i] = letters(pr[0]);
		}
		phraseText = text;
		phraseReg = reg;
		phraseMask = mask;
		phrases = p;
	}

	private static String[] phraseText;
	private static String[] phraseReg;
	private static long[] phraseMask;

	/** Which letters and digits a string contains, one bit each. */
	private static long letters(String s) {
		long m = 0L;
		for (int i = 0, n = s.length(); i < n; i++) {
			char c = s.charAt(i);
			if (c >= 'a' && c <= 'z') {
				m |= 1L << (c - 'a');
			} else if (c >= '0' && c <= '9') {
				m |= 1L << (26 + c - '0');
			}
		}
		return m;
	}

	/**
	 * If this stack is a down-mapped modern item we support, return a copy rendered
	 * as our item (same count, original NBT preserved); otherwise return it as-is.
	 */
	public static ItemStack remap(ItemStack st) {
		int at = 0; // which step failed, for the dump command (never touch the caught error: it can be null)
		try {
			if (st == null || st.func_190926_b()) {
				return st;
			}
			if (!net.minecraft.client.gui.TreasureMods.viaEnabled()) {
				return st;
			}
			// 0) Most reliable: the original item id ViaBackwards saved in the NBT.
			//    Works even when the server gave the item a custom name ("Spearmace").
			at = 1;
			String backup = fromBackup(st);
			if (backup != null) {
				ItemStack r = swap(st, backup);
				if (r != null) {
					return r;
				}
			}
			// Down-mapped modern items always carry a custom name ("1.20.5 Mace"); plain
			// items don't, so they skip the name matching (keeps equipment packets cheap).
			at = 2;
			if (!st.hasDisplayName()) {
				return st;
			}
			String disp = st.getDisplayName();
			if (disp == null) {
				return st;
			}
			at = 3;
			String lower = clean(disp);
			at = 4;
			String[] names = candidates(st, lower);
			if (names == null) {
				return st;
			}
			at = 5;
			for (int i = 0; i < names.length; i++) {
				ItemStack r = swap(st, names[i]);
				if (r != null) {
					return r;
				}
			}
		} catch (Throwable t) {
			lastError = "remap failed at step " + at;
		}
		return st;
	}

	/** Last error inside remap (shown by the dump command), or null. */
	public static String lastError;

	/** For the dump command: what the name matched and whether the cache had it. */
	public static String tcDebug(ItemStack st) {
		try {
			if (st == null || st.func_190926_b()) {
				return "empty";
			}
			String lower = clean(st.getDisplayName());
			String[] c = candidates(st, lower);
			if (c == null) c = new String[0];
			StringBuilder sb = new StringBuilder("name='").append(lower).append("' matches=");
			for (int i = 0; i < c.length && i < 4; i++) {
				sb.append(i > 0 ? "," : "").append(c[i]).append(ViaItems.getByName(c[i]) == null ? "(missing)" : "");
			}
			if (c.length == 0) sb.append("none");
			sb.append(" customName=").append(st.hasDisplayName());
			try {
				sb.append(" backup=").append(fromBackup(st));
			} catch (Throwable t) {
				sb.append(" backup-error=").append(t);
			}
			for (int i = 0; i < c.length && i < 2; i++) {
				try {
					ItemStack r = swap(st, c[i]);
					sb.append(" swap(").append(c[i]).append(")=").append(r == null ? "null" : "ok");
				} catch (Throwable t) {
					sb.append(" swap(").append(c[i]).append(")-error=").append(t);
				}
			}
			sb.append(" lastError=").append(lastError);
			return sb.toString();
		} catch (Throwable t) {
			return "error " + t;
		}
	}

	/** Item names this display name could be, in the order they're tried. */
	private static String[] candidates(ItemStack st, String lower) {
		ensure();
		List<String> out = null;
		// 1) Exact name: ViaBackwards labels a down-mapped item "<version> <Name>",
		//    e.g. "1.21 Wind Charge" — strip the version, look up the real name.
		String bare = stripVersion(lower);
		String exact = exactNames.get(bare);
		if (exact == null && bare != lower) {
			exact = exactNames.get(lower);
		}
		if (exact != null) {
			out = new ArrayList<String>();
			out.add(exact);
		}
		// 2) A renamed spear ("Spearmace") down-mapped to a sword: spear of that material.
		if (lower.contains("spear")) {
			String sp = spearFor(st.getItem());
			if (sp != null) {
				if (out == null) out = new ArrayList<String>();
				out.add(sp);
			}
		}
		// 3) Fallback: the name merely contains one of our item names.
		String[] text = phraseText;
		String[] reg = phraseReg;
		long[] mask = phraseMask;
		long have = letters(lower);
		int len = lower.length();
		for (int i = 0; i < text.length; i++) {
			String t = text[i];
			if ((mask[i] & ~have) != 0L || t.length() < 4 || t.length() > len) {
				continue; // needs a letter the name doesn't have, or longer than the name
			}
			if (lower.contains(t)) {
				if (out == null) out = new ArrayList<String>();
				out.add(reg[i]);
			}
		}
		if (out == null) {
			return null;
		}
		String[] arr = new String[out.size()];
		for (int i = 0; i < arr.length; i++) {
			arr[i] = out.get(i);
		}
		return arr;
	}

	/** version ("1_21_11") -> (id -> registry name), parsed from {@link ViaItemIds} on first use. */
	private static java.util.Map<String, java.util.Map<Integer, String>> idTables;
	/** Version order, oldest first, so the newest backup wins. */
	private static java.util.List<String> idOrder;

	private static void ensureIds() {
		if (idTables != null) {
			return;
		}
		java.util.Map<String, java.util.Map<Integer, String>> t = new java.util.HashMap<String, java.util.Map<Integer, String>>();
		java.util.List<String> order = new ArrayList<String>();
		for (int i = 0; i < ViaItemIds.TABLE.length; i++) {
			java.util.Map<Integer, String> m = new java.util.HashMap<Integer, String>();
			String[] pairs = ViaItemIds.TABLE[i][1].split(";");
			for (int j = 0; j < pairs.length; j++) {
				int eq = pairs[j].indexOf('=');
				if (eq > 0) {
					m.put(Integer.valueOf(Integer.parseInt(pairs[j].substring(0, eq))), pairs[j].substring(eq + 1));
				}
			}
			t.put(ViaItemIds.TABLE[i][0], m);
			order.add(ViaItemIds.TABLE[i][0]);
		}
		idOrder = order;
		idTables = t;
	}

	/**
	 * Looks for ViaBackwards backups like {@code "VB|Protocol1_21_11To1_21_9": ...}
	 * next to an {@code id}. That id is the item's id in the first version of the
	 * key. Returns our item name for the newest backup that names one, or null.
	 */
	static String fromBackup(ItemStack st) {
		if (!st.hasTagCompound()) {
			return null;
		}
		ensureIds();
		String[] best = new String[2]; // {name, version}
		scan(st.getTagCompound(), best, 0);
		return best[0];
	}

	private static void scan(net.minecraft.nbt.NBTTagCompound c, String[] best, int depth) {
		if (c == null || depth > 8) {
			return;
		}
		boolean hasId = c.hasKey("id", 99);
		for (String key : c.getKeySet()) {
			if (hasId && key.startsWith("VB|Protocol")) {
				int to = key.indexOf("To", 11);
				if (to > 11) {
					String ver = key.substring(11, to);
					java.util.Map<Integer, String> m = idTables.get(ver);
					if (m != null) {
						String name = m.get(Integer.valueOf(c.getInteger("id")));
						if (name != null && (best[1] == null || idOrder.indexOf(ver) > idOrder.indexOf(best[1]))) {
							best[0] = name;
							best[1] = ver;
						}
					}
				}
			}
			net.minecraft.nbt.NBTBase child = c.getTag(key);
			if (child instanceof net.minecraft.nbt.NBTTagCompound) {
				scan((net.minecraft.nbt.NBTTagCompound) child, best, depth + 1);
			}
		}
	}

	/** The spear ViaBackwards turns into this sword, or null. */
	private static String spearFor(Item it) {
		if (it == Items.WOODEN_SWORD) return "wooden_spear";
		if (it == Items.STONE_SWORD) return "stone_spear";
		if (it == Items.IRON_SWORD) return "iron_spear";
		if (it == Items.GOLDEN_SWORD) return "golden_spear";
		if (it == Items.DIAMOND_SWORD) return "diamond_spear";
		Item n = ViaItems.getByName("netherite_sword");
		if (n != null && it == n) return "netherite_spear";
		return null;
	}

	/** Our item in place of {@code st} (same count, NBT kept), or null if not applicable. */
	private static void addBlockNames(java.util.Map<String, String> ex, String[] names) {
		for (int i = 0; i < names.length; i++) {
			String phrase = names[i].replace('_', ' ');
			if (!ex.containsKey(phrase)) {
				ex.put(phrase, names[i]);
			}
		}
	}

	/** The item form of one of our modern blocks, or null. */
	private static Item blockItem(String name) {
		try {
			net.minecraft.block.Block b = net.minecraft.block.Block.getBlockFromName(name);
			if (b == null || net.minecraft.block.Block.getIdFromBlock(b) < net.minecraft.block.ViaBlocks.FIRST_ID) {
				return null;
			}
			Item it = Item.getItemFromBlock(b);
			return it == null || it == net.minecraft.init.Items.AIR ? null : it;
		} catch (Throwable t) {
			return null;
		}
	}

	private static ItemStack swap(ItemStack st, String name) {
		// Direct array lookup, not Item.getByNameOrId — the registry can fail to
		// resolve our high ids in some runtimes.
		Item via = ViaItems.getByName(name);
		if (via == null) {
			via = blockItem(name); // a modern block (dropped or in your inventory)
		}
		if (via == null || via == st.getItem()) {
			return null;
		}
		ItemStack r = new ItemStack(via, st.func_190916_E());
		if (st.hasTagCompound()) {
			try {
				r.setTagCompound(st.getTagCompound().copy());
			} catch (Throwable t) {
				lastError = "nbt copy failed";
				r.setTagCompound(st.getTagCompound()); // share it rather than lose the swap
			}
		}
		return r;
	}

	/** Lowercase, without § formatting codes, trimmed. */
	private static String clean(String s) {
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\u00a7' && i + 1 < s.length()) {
				i++;
				continue;
			}
			b.append(Character.toLowerCase(c));
		}
		return b.toString().trim();
	}

	/** "1.20.5 mace" -> "mace"; anything not starting with a version is returned as-is. */
	private static String stripVersion(String s) {
		int i = 0;
		int n = s.length();
		boolean digit = false;
		boolean dot = false;
		while (i < n) {
			char c = s.charAt(i);
			if (c >= '0' && c <= '9') {
				digit = true;
			} else if (c == '.') {
				dot = true;
			} else {
				break;
			}
			i++;
		}
		if (digit && dot && i < n && s.charAt(i) == ' ') {
			return s.substring(i + 1).trim();
		}
		return s;
	}

	/** Remap every stack in a window-items list in place. */
	public static void remapList(List<ItemStack> list) {
		if (list == null) {
			return;
		}
		try {
			for (int i = 0; i < list.size(); i++) {
				list.set(i, remap(list.get(i)));
			}
		} catch (Throwable t) {
			// ignore
		}
	}
}
