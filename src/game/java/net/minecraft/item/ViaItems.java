package net.minecraft.item;

/**
 * Treasure Client — "ViaItems": modern (1.13+) item rendering.
 *
 * Phase 4 (items): a small set of modern items is registered into the normal
 * 1.12 item registry, each with its own item model + texture, so the client can
 * render modern items with real textures. Mirrors {@link net.minecraft.block.ViaBlocks}.
 *
 * Two kinds:
 *   - weapons ({@link #WEAPONS}) register as {@link ItemSword} (handheld model,
 *     real melee damage, Combat tab);
 *   - everything else registers as a plain material item (generated model,
 *     Materials tab).
 *
 * To grow coverage later: add a name here and drop the matching
 *   assets/minecraft/models/item/&lt;name&gt;.json   (parent item/handheld or item/generated)
 *   assets/minecraft/textures/items/&lt;name&gt;.png
 * The registration is driven from {@link Item#registerItems()} and the item
 * models from {@code RenderItem.registerItems()}.
 *
 * IDs start well above vanilla's last item id (~2267) to avoid any collision.
 */
public final class ViaItems {

	private ViaItems() {
	}

	/** First numeric item id used for ViaItems (well clear of vanilla's ~2267). */
	public static final int FIRST_ID = 3000;

	/** Registry names (also the asset base names). */
	public static final String[] NAMES = {
		// weapons — handheld model, ItemSword (melee damage), Combat tab
		"mace",
		"spear",
		"trident",
		// materials — generated (flat) model, Materials tab
		"amethyst_shard",
		"copper_ingot"
	};

	/** Names from {@link #NAMES} that register as melee weapons ({@link ItemSword}). */
	public static final java.util.Set<String> WEAPONS = new java.util.HashSet<String>(java.util.Arrays.asList(
		"mace",
		"spear",
		"trident"
	));

	/** True when the given ViaItem name should be built as a melee weapon. */
	public static boolean isWeapon(String name) {
		return WEAPONS.contains(name);
	}

	/** Registered item instances, filled in by Item.registerItems(). */
	public static final Item[] ITEMS = new Item[NAMES.length];
}
