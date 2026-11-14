package net.minecraft.client.renderer.texture;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

/**
 * Treasure Client: loads the pictures of common mobs, armor and menus while you're on the title
 * screen or server list, one per game tick. They used to load the first time a mob came into
 * view or a menu opened (the villager trading menu took 66 ms on a hot Chromebook), which made
 * a stutter mid-game. Loading them early gives the same pictures with no stutter in play.
 */
public final class TCTexturePreload {

	private TCTexturePreload() {
	}

	private static final String[] NAMES = {
			// menus
			"gui/container/inventory.png", "gui/container/generic_54.png", "gui/container/crafting_table.png",
			"gui/container/furnace.png", "gui/container/villager2.png", "gui/container/villager2/trade_arrow.png",
			"gui/container/villager2/trade_arrow_out_of_stock.png", "gui/container/villager2/out_of_stock.png",
			"gui/container/villager2/scroller.png", "gui/container/anvil.png", "gui/container/enchanting_table.png",
			"gui/container/brewing_stand.png", "gui/container/hopper.png", "gui/container/dispenser.png",
			"gui/container/shulker_box.png", "gui/container/beacon.png", "gui/container/horse.png",
			"gui/container/creative_inventory/tabs.png", "gui/container/creative_inventory/tab_items.png",
			"gui/container/creative_inventory/tab_item_search.png",
			"gui/container/creative_inventory/tab_inventory.png", "gui/recipe_book.png", "gui/book.png",
			"gui/container/stats_icons.png", "gui/advancements/window.png", "gui/bars.png", "gui/toasts.png",
			// mobs and players
			"entity/zombie/zombie.png", "entity/skeleton/skeleton.png", "entity/creeper/creeper.png",
			"entity/spider/spider.png", "entity/spider_eyes.png", "entity/enderman/enderman.png",
			"entity/enderman/enderman_eyes.png", "entity/villager/villager.png", "entity/villager/farmer.png",
			"entity/villager/librarian.png", "entity/villager/priest.png", "entity/villager/smith.png",
			"entity/villager/butcher.png", "entity/pig/pig.png", "entity/cow/cow.png", "entity/sheep/sheep.png",
			"entity/sheep/sheep_fur.png", "entity/chicken.png", "entity/wolf/wolf.png", "entity/iron_golem.png",
			"entity/slime/slime.png", "entity/witch.png", "entity/blaze.png", "entity/zombie_pigman.png",
			"entity/horse/horse_white.png", "entity/steve.png", "entity/alex.png", "entity/projectiles/arrow.png",
			"entity/experience_orb.png", "entity/armorstand/wood.png", "entity/elytra.png",
			"entity/shield_base_nopattern.png", "entity/minecart.png", "entity/boat/boat_oak.png",
			// item shine, shadows, armor
			"misc/enchanted_item_glint.png", "misc/shadow.png", "models/armor/diamond_layer_1.png",
			"models/armor/diamond_layer_2.png", "models/armor/iron_layer_1.png", "models/armor/iron_layer_2.png",
			"models/armor/chainmail_layer_1.png", "models/armor/chainmail_layer_2.png",
			"models/armor/gold_layer_1.png", "models/armor/gold_layer_2.png", "models/armor/leather_layer_1.png",
			"models/armor/leather_layer_2.png", "models/armor/leather_layer_1_overlay.png",
			"models/armor/leather_layer_2_overlay.png", "models/armor/netherite_layer_1.png",
			"models/armor/netherite_layer_2.png",
			// cracks drawn over chests, signs and other block entities while you break them
			"blocks/destroy_stage_0.png", "blocks/destroy_stage_1.png", "blocks/destroy_stage_2.png",
			"blocks/destroy_stage_3.png", "blocks/destroy_stage_4.png", "blocks/destroy_stage_5.png",
			"blocks/destroy_stage_6.png", "blocks/destroy_stage_7.png", "blocks/destroy_stage_8.png",
			"blocks/destroy_stage_9.png" };

	private static int next;

	/** Called every game tick; only does anything on menus outside a world, until all are loaded. */
	// banner and shield designs are built from these (decoded ahead of time, see LayeredColorMaskTexture)
	private static final String[] LAYERED = { "textures/entity/shield_base.png", "textures/entity/shield/base.png",
			"textures/entity/banner_base.png", "textures/entity/banner/base.png" };
	private static int nextLayered;

	public static void tick(Minecraft mc) {
		if (next >= NAMES.length && nextLayered < LAYERED.length && mc.world == null && mc.currentScreen != null
				&& mc.getResourceManager() != null) {
			LayeredColorMaskTexture.tcWarm(mc.getResourceManager(), LAYERED[nextLayered++]);
			return;
		}
		if (next >= NAMES.length || mc.world != null || mc.currentScreen == null) {
			return;
		}
		TextureManager tm = mc.getTextureManager();
		if (tm == null) {
			return;
		}
		// skip ones already loaded, then load one
		while (next < NAMES.length) {
			ResourceLocation rl = new ResourceLocation("textures/" + NAMES[next++]);
			if (tm.getTexture(rl) == null) {
				try {
					tm.loadTexture(rl, new SimpleTexture(rl));
				} catch (Throwable t) {
					// missing in this resource pack: it would show as missing anyway
				}
				return;
			}
		}
	}
}
