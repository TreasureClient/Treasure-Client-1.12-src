package net.minecraft.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.inventory.EntityEquipmentSlot;

/**
 * Treasure Client — "ViaItems": modern (1.13+) item rendering.
 *
 * A set of modern items registered into the normal 1.12 item registry, each with
 * its own item model + texture (and, for armor, worn layer textures). Mirrors
 * {@link net.minecraft.block.ViaBlocks}.
 *
 * Kinds (decided in {@link #createItem(String)} by name):
 *   - weapons / spears  -> {@link ItemSword} (handheld model, Combat tab);
 *   - netherite tools   -> ItemPickaxe/ItemAxe/ItemSpade/ItemHoe (Tools tab);
 *   - netherite armor   -> {@link ItemArmor} with the NETHERITE armor material
 *                          (worn texture = textures/models/armor/netherite_layer_*);
 *   - materials         -> plain {@link Item} (Materials tab).
 *
 * Netherite is wired as real tiers: {@code Item.ToolMaterial.NETHERITE} and
 * {@code ItemArmor.ArmorMaterial.NETHERITE} were added to those enums.
 *
 * To grow coverage later: add a name here, classify it in {@link #createItem},
 * and drop the matching model JSON + texture(s).
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
		// weapons — handheld, Combat tab
		"mace",
		"trident",
		// spears (one per material) — handheld, Combat tab
		"wooden_spear",
		"stone_spear",
		"copper_spear",
		"iron_spear",
		"diamond_spear",
		"netherite_spear",
		// netherite tools — handheld, Tools tab
		"netherite_sword",
		"netherite_pickaxe",
		"netherite_axe",
		"netherite_shovel",
		"netherite_hoe",
		// netherite armor — generated icon, Combat tab, worn netherite_layer_*
		"netherite_helmet",
		"netherite_chestplate",
		"netherite_leggings",
		"netherite_boots",
		// materials — generated, Materials tab
		"amethyst_shard",
		"copper_ingot",
		"netherite_ingot",
		// 1.15 bees
		"honeycomb",
		"honey_bottle"
	};

	/** Registered item instances, filled in by Item.registerItems(). */
	public static final Item[] ITEMS = new Item[NAMES.length];

	private static boolean isMaterial(String name) {
		return name.equals("amethyst_shard") || name.equals("copper_ingot") || name.equals("netherite_ingot")
				|| name.equals("honeycomb") || name.equals("honey_bottle");
	}

	/** Tool material for the sword-like items (swords + spears), by name. */
	private static Item.ToolMaterial swordMaterial(String name) {
		if (name.equals("wooden_spear")) return Item.ToolMaterial.WOOD;
		if (name.equals("stone_spear")) return Item.ToolMaterial.STONE;
		if (name.equals("copper_spear")) return Item.ToolMaterial.STONE; // copper ~ stone tier (no copper tier in 1.12)
		if (name.equals("iron_spear")) return Item.ToolMaterial.IRON;
		if (name.equals("diamond_spear")) return Item.ToolMaterial.DIAMOND;
		if (name.equals("netherite_spear") || name.equals("netherite_sword")) return Item.ToolMaterial.NETHERITE;
		return Item.ToolMaterial.DIAMOND; // mace, trident
	}

	/** Build and configure the item for the given ViaItems name. */
	public static Item createItem(String name) {
		Item it;
		if (name.equals("netherite_helmet")) {
			it = new ItemArmor(ItemArmor.ArmorMaterial.NETHERITE, 3, EntityEquipmentSlot.HEAD);
		} else if (name.equals("netherite_chestplate")) {
			it = new ItemArmor(ItemArmor.ArmorMaterial.NETHERITE, 3, EntityEquipmentSlot.CHEST);
		} else if (name.equals("netherite_leggings")) {
			it = new ItemArmor(ItemArmor.ArmorMaterial.NETHERITE, 3, EntityEquipmentSlot.LEGS);
		} else if (name.equals("netherite_boots")) {
			it = new ItemArmor(ItemArmor.ArmorMaterial.NETHERITE, 3, EntityEquipmentSlot.FEET);
		} else if (name.equals("netherite_pickaxe")) {
			it = new ItemPickaxe(Item.ToolMaterial.NETHERITE);
		} else if (name.equals("netherite_axe")) {
			it = new ItemAxe(Item.ToolMaterial.NETHERITE);
		} else if (name.equals("netherite_shovel")) {
			it = new ItemSpade(Item.ToolMaterial.NETHERITE);
		} else if (name.equals("netherite_hoe")) {
			it = new ItemHoe(Item.ToolMaterial.NETHERITE);
		} else if (isMaterial(name)) {
			it = (new Item()).setCreativeTab(CreativeTabs.MATERIALS);
		} else {
			// mace, trident, and all spears: sword-like melee weapons
			it = new ItemSword(swordMaterial(name));
		}
		return it.setUnlocalizedName(name);
	}
}
