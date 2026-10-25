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
		phrases = p;
	}

	/**
	 * If this stack is a down-mapped modern item we support, return a copy rendered
	 * as our item (same count, original NBT preserved); otherwise return it as-is.
	 */
	public static ItemStack remap(ItemStack st) {
		try {
			if (st == null || st.func_190926_b()) {
				return st;
			}
			if (!net.minecraft.client.gui.TreasureMods.viaEnabled()) {
				return st;
			}
			// 0) Most reliable: the original item id ViaBackwards saved in the NBT.
			//    Works even when the server gave the item a custom name ("Spearmace").
			String backup = fromBackup(st);
			if (backup != null) {
				ItemStack r = swap(st, backup);
				if (r != null) {
					return r;
				}
			}
			String disp = st.getDisplayName();
			if (disp == null) {
				return st;
			}
			String lower = clean(disp);
			ensure();
			// 1) Exact name: ViaBackwards labels a down-mapped item "<version> <Name>",
			//    e.g. "1.21 Wind Charge" — strip the version, look up the real name.
			String bare = stripVersion(lower);
			String exact = exactNames.get(bare);
			if (exact == null && bare != lower) {
				exact = exactNames.get(lower);
			}
			if (exact != null) {
				ItemStack r = swap(st, exact);
				if (r != null) {
					return r;
				}
			}
			// 2) A renamed spear ("Spearmace") down-mapped to a sword: spear of that material.
			if (lower.contains("spear")) {
				String sp = spearFor(st.getItem());
				if (sp != null) {
					ItemStack r = swap(st, sp);
					if (r != null) {
						return r;
					}
				}
			}
			// 3) Fallback: the name merely contains one of our item names.
			for (int i = 0; i < phrases.size(); i++) {
				String[] pr = phrases.get(i);
				if (pr[0].length() >= 4 && lower.contains(pr[0])) {
					// Direct array lookup, not Item.getByNameOrId — the registry can
					// fail to resolve our high ids in some runtimes.
					ItemStack r = swap(st, pr[1]);
					if (r == null) {
						continue; // not registered / already ours — try the next phrase
					}
					return r;
				}
			}
		} catch (Throwable t) {
			// never let item remapping break the client
		}
		return st;
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
	private static ItemStack swap(ItemStack st, String name) {
		// Direct array lookup, not Item.getByNameOrId — the registry can fail to
		// resolve our high ids in some runtimes.
		Item via = ViaItems.getByName(name);
		if (via == null || via == st.getItem()) {
			return null;
		}
		ItemStack r = new ItemStack(via, st.func_190916_E());
		if (st.hasTagCompound()) {
			r.setTagCompound(st.getTagCompound().copy());
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
