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
		"wild_armor_trim_smithing_template",
		// --- auto-added from ViaVersion mappings: every post-1.12 item with a flat icon ---
		"abandoned_camp_map",
		"acacia_chest_boat",
		"acacia_hanging_sign",
		"acacia_sign",
		"armadillo_scute",
		"axolotl_bucket",
		"bamboo",
		"bamboo_chest_raft",
		"bamboo_door",
		"bamboo_hanging_sign",
		"bamboo_raft",
		"bamboo_sign",
		"bell",
		"birch_chest_boat",
		"birch_hanging_sign",
		"birch_sign",
		"black_bundle",
		"black_candle",
		"black_cushion",
		"black_dye",
		"black_harness",
		"blue_bundle",
		"blue_candle",
		"blue_cushion",
		"blue_dye",
		"blue_egg",
		"blue_harness",
		"bolt_armor_trim_smithing_template",
		"bordure_indented_banner_pattern",
		"breeze_rod",
		"brown_bundle",
		"brown_candle",
		"brown_cushion",
		"brown_dye",
		"brown_egg",
		"brown_harness",
		"bundle",
		"buried_ancient_city_map",
		"buried_mineshaft_map",
		"buried_treasure_map",
		"buried_trial_chambers_map",
		"campfire",
		"candle",
		"cherry_boat",
		"cherry_chest_boat",
		"cherry_door",
		"cherry_hanging_sign",
		"cherry_sign",
		"cod_bucket",
		"copper_axe",
		"copper_boots",
		"copper_chain",
		"copper_chestplate",
		"copper_door",
		"copper_helmet",
		"copper_hoe",
		"copper_horse_armor",
		"copper_lantern",
		"copper_leggings",
		"copper_nautilus_armor",
		"copper_nugget",
		"copper_pickaxe",
		"copper_shovel",
		"copper_sword",
		"creeper_banner_pattern",
		"crimson_door",
		"crimson_hanging_sign",
		"crimson_sign",
		"crossbow",
		"cyan_bundle",
		"cyan_candle",
		"cyan_cushion",
		"cyan_harness",
		"dark_oak_chest_boat",
		"dark_oak_hanging_sign",
		"dark_oak_sign",
		"desert_pyramid_map",
		"desert_village_map",
		"diamond_nautilus_armor",
		"dried_kelp",
		"exposed_copper_chain",
		"exposed_copper_door",
		"exposed_copper_lantern",
		"field_masoned_banner_pattern",
		"firefly_bush",
		"flow_armor_trim_smithing_template",
		"flow_banner_pattern",
		"flow_pottery_sherd",
		"flower_banner_pattern",
		"globe_banner_pattern",
		"glow_berries",
		"glow_ink_sac",
		"glow_item_frame",
		"golden_nautilus_armor",
		"golden_spear",
		"gray_bundle",
		"gray_candle",
		"gray_cushion",
		"gray_harness",
		"green_bundle",
		"green_candle",
		"green_cushion",
		"green_harness",
		"guster_banner_pattern",
		"guster_pottery_sherd",
		"heart_of_the_sea",
		"iron_chain",
		"iron_nautilus_armor",
		"jungle_chest_boat",
		"jungle_hanging_sign",
		"jungle_pyramid_map",
		"jungle_sign",
		"leaf_litter",
		"leather_horse_armor",
		"light_blue_bundle",
		"light_blue_candle",
		"light_blue_cushion",
		"light_blue_harness",
		"light_gray_bundle",
		"light_gray_candle",
		"light_gray_cushion",
		"light_gray_harness",
		"lime_bundle",
		"lime_candle",
		"lime_cushion",
		"lime_harness",
		"magenta_bundle",
		"magenta_candle",
		"magenta_cushion",
		"magenta_harness",
		"mangrove_boat",
		"mangrove_chest_boat",
		"mangrove_door",
		"mangrove_hanging_sign",
		"mangrove_propagule",
		"mangrove_sign",
		"mojang_banner_pattern",
		"music_disc_bounce",
		"music_disc_creator",
		"music_disc_creator_music_box",
		"music_disc_lava_chicken",
		"music_disc_pigstep",
		"music_disc_precipice",
		"music_disc_tears",
		"nautilus_shell",
		"nether_sprouts",
		"netherite_horse_armor",
		"netherite_nautilus_armor",
		"netherite_scrap",
		"oak_chest_boat",
		"oak_hanging_sign",
		"ocean_monument_map",
		"ominous_bottle",
		"ominous_trial_key",
		"orange_bundle",
		"orange_candle",
		"orange_cushion",
		"orange_harness",
		"oxidized_copper_chain",
		"oxidized_copper_door",
		"oxidized_copper_lantern",
		"pale_oak_boat",
		"pale_oak_chest_boat",
		"pale_oak_door",
		"pale_oak_hanging_sign",
		"pale_oak_sign",
		"phantom_membrane",
		"piglin_banner_pattern",
		"pink_bundle",
		"pink_candle",
		"pink_cushion",
		"pink_harness",
		"pink_petals",
		"pitcher_plant",
		"plains_village_map",
		"pointed_dripstone",
		"poplar_boat",
		"poplar_chest_boat",
		"poplar_door",
		"poplar_hanging_sign",
		"poplar_sign",
		"powder_snow_bucket",
		"pufferfish_bucket",
		"purple_bundle",
		"purple_candle",
		"purple_cushion",
		"purple_harness",
		"raw_copper",
		"raw_gold",
		"raw_iron",
		"red_bundle",
		"red_candle",
		"red_cushion",
		"red_harness",
		"resin_brick",
		"resin_clump",
		"salmon_bucket",
		"savanna_village_map",
		"scrape_pottery_sherd",
		"sea_pickle",
		"skull_banner_pattern",
		"sniffer_egg",
		"snowy_village_map",
		"soul_campfire",
		"spruce_chest_boat",
		"spruce_hanging_sign",
		"spruce_sign",
		"spyglass",
		"sulfur_cube_bucket",
		"sulfur_spike",
		"suspicious_stew",
		"swamp_hut_map",
		"sweet_berries",
		"tadpole_bucket",
		"taiga_village_map",
		"trial_key",
		"tropical_fish_bucket",
		"turtle_egg",
		"turtle_helmet",
		"turtle_scute",
		"warm_ocean_ruins_map",
		"warped_door",
		"warped_fungus_on_a_stick",
		"warped_hanging_sign",
		"warped_sign",
		"weathered_copper_chain",
		"weathered_copper_door",
		"weathered_copper_lantern",
		"white_bundle",
		"white_candle",
		"white_cushion",
		"white_dye",
		"white_harness",
		"wildflowers",
		"wind_charge",
		"wolf_armor",
		"woodland_mansion_map",
		"yellow_bundle",
		"yellow_candle",
		"yellow_cushion",
		"yellow_harness"
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
				|| name.equals("netherite_sword") || name.equals("copper_sword");
	}

	/** Tool material for the sword-like items (swords + spears), by name. */
	private static Item.ToolMaterial swordMaterial(String name) {
		if (name.equals("wooden_spear")) return Item.ToolMaterial.WOOD;
		if (name.equals("stone_spear")) return Item.ToolMaterial.STONE;
		if (name.equals("copper_spear")) return Item.ToolMaterial.STONE; // copper ~ stone tier (no copper tier in 1.12)
		if (name.equals("copper_sword")) return Item.ToolMaterial.STONE;
		if (name.equals("golden_spear")) return Item.ToolMaterial.GOLD;
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
		} else if (name.equals("copper_helmet")) {
			it = new ItemArmor(ItemArmor.ArmorMaterial.COPPER, 3, EntityEquipmentSlot.HEAD);
		} else if (name.equals("copper_chestplate")) {
			it = new ItemArmor(ItemArmor.ArmorMaterial.COPPER, 3, EntityEquipmentSlot.CHEST);
		} else if (name.equals("copper_leggings")) {
			it = new ItemArmor(ItemArmor.ArmorMaterial.COPPER, 3, EntityEquipmentSlot.LEGS);
		} else if (name.equals("copper_boots")) {
			it = new ItemArmor(ItemArmor.ArmorMaterial.COPPER, 3, EntityEquipmentSlot.FEET);
		} else if (name.equals("turtle_helmet")) {
			it = new ItemArmor(ItemArmor.ArmorMaterial.TURTLE, 3, EntityEquipmentSlot.HEAD);
		} else if (name.equals("copper_pickaxe")) {
			it = new ItemPickaxe(Item.ToolMaterial.STONE); // no copper tier in 1.12; stone is closest
		} else if (name.equals("copper_axe")) {
			it = new ItemAxe(Item.ToolMaterial.STONE);
		} else if (name.equals("copper_shovel")) {
			it = new ItemSpade(Item.ToolMaterial.STONE);
		} else if (name.equals("copper_hoe")) {
			it = new ItemHoe(Item.ToolMaterial.STONE);
		} else if (name.equals("wind_charge")) {
			// Thrown like a snowball: the client swings + sends the use packet, the server throws it.
			it = new ItemSnowball();
		} else if (name.equals("crossbow")) {
			// Held "use" like a bow so the server's crossbow charging gets the right packets.
			it = new ItemBow();
		} else if (name.equals("sweet_berries") || name.equals("glow_berries")) {
			it = new ItemFood(2, 0.1F, false);
		} else if (name.equals("dried_kelp")) {
			it = new ItemFood(1, 0.3F, false);
		} else if (name.equals("suspicious_stew")) {
			it = new ItemSoup(6);
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
