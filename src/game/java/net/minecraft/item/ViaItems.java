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
		"honey_bottle",
		// 1.19 Wild Update + 1.20 Trails & Tales items
		"recovery_compass",
		"echo_shard",
		"goat_horn",
		"disc_fragment_5",
		"music_disc_5",
		"music_disc_otherside",
		"brush",
		"music_disc_relic",
		"torchflower_seeds",
		"pitcher_pod",
		"netherite_upgrade_smithing_template",
		"angler_pottery_sherd",
		"archer_pottery_sherd",
		"arms_up_pottery_sherd",
		"blade_pottery_sherd",
		"brewer_pottery_sherd",
		"burn_pottery_sherd",
		"danger_pottery_sherd",
		"explorer_pottery_sherd",
		"friend_pottery_sherd",
		"heart_pottery_sherd",
		"heartbreak_pottery_sherd",
		"howl_pottery_sherd",
		"miner_pottery_sherd",
		"mourner_pottery_sherd",
		"plenty_pottery_sherd",
		"prize_pottery_sherd",
		"sheaf_pottery_sherd",
		"shelter_pottery_sherd",
		"skull_pottery_sherd",
		"snort_pottery_sherd",
		"coast_armor_trim_smithing_template",
		"dune_armor_trim_smithing_template",
		"eye_armor_trim_smithing_template",
		"host_armor_trim_smithing_template",
		"raiser_armor_trim_smithing_template",
		"rib_armor_trim_smithing_template",
		"sentry_armor_trim_smithing_template",
		"shaper_armor_trim_smithing_template",
		"silence_armor_trim_smithing_template",
		"snout_armor_trim_smithing_template",
		"spire_armor_trim_smithing_template",
		"tide_armor_trim_smithing_template",
		"vex_armor_trim_smithing_template",
		"ward_armor_trim_smithing_template",
		"wayfinder_armor_trim_smithing_template",
		"wild_armor_trim_smithing_template"
	};

	/** Registered item instances, filled in by Item.registerItems(). */
	public static final Item[] ITEMS = new Item[NAMES.length];

	/**
	 * Direct lookup of a registered ViaItem by its registry name, straight from
	 * {@link #ITEMS}. We avoid {@link Item#getByNameOrId} here because the item
	 * registry can fail to resolve our high ids (3000+) in some runtimes, which
	 * silently breaks the name-based remap. This always sees what we registered.
	 */
	public static Item getByName(String name) {
		if (name == null) {
			return null;
		}
		for (int i = 0; i < NAMES.length; i++) {
			if (NAMES[i].equals(name)) {
				return ITEMS[i];
			}
		}
		return null;
	}

	/** Sword-like melee weapons (mace, trident, netherite sword, and every spear). */
	private static boolean isWeapon(String name) {
		return name.endsWith("_spear") || name.equals("mace") || name.equals("trident")
				|| name.equals("netherite_sword");
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
		} else if (isWeapon(name)) {
			// mace, trident, and all spears: sword-like melee weapons
			it = new ItemSword(swordMaterial(name));
		} else {
			// everything else is a plain material / misc item (Materials tab)
			it = (new Item()).setCreativeTab(CreativeTabs.MATERIALS);
		}
		return it.setUnlocalizedName(name);
	}
}
