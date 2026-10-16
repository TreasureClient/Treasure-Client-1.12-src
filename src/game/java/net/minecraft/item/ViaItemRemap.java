package net.minecraft.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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

	private static void ensure() {
		if (phrases != null) {
			return;
		}
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
			String disp = st.getDisplayName();
			if (disp == null) {
				return st;
			}
			String lower = disp.toLowerCase();
			ensure();
			for (int i = 0; i < phrases.size(); i++) {
				String[] pr = phrases.get(i);
				if (pr[0].length() >= 3 && lower.contains(pr[0])) {
					Item via = Item.getByNameOrId(pr[1]);
					if (via == null || via == st.getItem()) {
						return st; // unresolved, or already our item
					}
					ItemStack r = new ItemStack(via, st.func_190916_E());
					if (st.hasTagCompound()) {
						r.setTagCompound(st.getTagCompound().copy());
					}
					return r;
				}
			}
		} catch (Throwable t) {
			// never let item remapping break the client
		}
		return st;
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
