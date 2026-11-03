package net.minecraft.client;

import java.util.ArrayList;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.opengl.FixedFunctionPipeline;
import net.minecraft.client.audio.Sound;
import net.minecraft.client.audio.SoundEventAccessor;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

/**
 * Treasure Client: loads the textures and sounds that games use all the time while
 * nothing is happening (menus, loading screens, or standing around in a lobby),
 * instead of the first time they are needed. Each first load waits on storage and
 * the image or sound decoder, which was a spike in the middle of a fight: the first
 * creeper, the first explosion, the first boss bar, the first chest menu.
 */
public final class TCWarmup {

	private TCWarmup() {
	}

	private static final String[] TEXTURES = {
			// HUD and menus
			"textures/gui/bars.png", "textures/gui/container/inventory.png", "textures/gui/toasts.png",
			"textures/gui/container/generic_54.png", "textures/gui/container/hopper.png",
			"textures/gui/container/dispenser.png", "textures/misc/pumpkinblur.png", "textures/misc/underwater.png",
			"textures/misc/vignette.png", "textures/misc/enchanted_item_glint.png",
			// fights
			"textures/entity/creeper/creeper.png", "textures/entity/creeper/creeper_armor.png",
			"textures/entity/explosion.png", "textures/entity/sweep.png", "textures/entity/projectiles/arrow.png",
			"textures/entity/projectiles/tipped_arrow.png", "textures/entity/projectiles/spectral_arrow.png",
			"textures/entity/elytra.png", "textures/entity/shield_base_nopattern.png",
			"textures/entity/experience_orb.png",
			"textures/models/armor/diamond_layer_1.png", "textures/models/armor/diamond_layer_2.png",
			"textures/models/armor/iron_layer_1.png", "textures/models/armor/iron_layer_2.png",
			"textures/models/armor/chainmail_layer_1.png", "textures/models/armor/chainmail_layer_2.png",
			"textures/models/armor/gold_layer_1.png", "textures/models/armor/gold_layer_2.png",
			"textures/models/armor/leather_layer_1.png", "textures/models/armor/leather_layer_2.png",
			"textures/models/armor/leather_layer_1_overlay.png", "textures/models/armor/leather_layer_2_overlay.png",
			// modern armor drawn by ViaVersion items (netherite, copper, turtle)
			"textures/models/armor/netherite_layer_1.png", "textures/models/armor/netherite_layer_2.png",
			"textures/models/armor/copper_layer_1.png", "textures/models/armor/copper_layer_2.png",
			"textures/models/armor/turtle_layer_1.png", "textures/models/armor/turtle_layer_2.png",
			// lobbies and common mobs
			"textures/entity/chest/normal.png", "textures/entity/chest/ender.png", "textures/entity/chest/trapped.png",
			"textures/entity/sign.png", "textures/entity/villager/villager.png", "textures/entity/armorstand/wood.png",
			"textures/entity/zombie/zombie.png", "textures/entity/skeleton/skeleton.png",
			"textures/entity/spider/spider.png", "textures/entity/spider_eyes.png",
			"textures/entity/enderman/enderman.png", "textures/entity/enderman/enderman_eyes.png",
			"textures/entity/iron_golem.png", "textures/entity/blaze.png", "textures/entity/slime/slime.png",
			"textures/entity/witch.png", "textures/environment/rain.png", "textures/environment/sun.png",
			"textures/environment/moon_phases.png", "textures/environment/clouds.png",
			"textures/environment/snow.png", "textures/misc/forcefield.png" };

	private static final String[] SOUNDS = { "entity.generic.explode", "entity.creeper.primed", "entity.creeper.hurt",
			"entity.creeper.death", "entity.tnt.primed", "entity.player.hurt", "entity.player.death",
			"entity.player.attack.strong", "entity.player.attack.weak", "entity.player.attack.sweep",
			"entity.player.attack.crit", "entity.player.attack.knockback", "entity.player.attack.nodamage",
			"entity.generic.hurt", "entity.player.small_fall", "entity.player.big_fall", "entity.arrow.shoot",
			"entity.arrow.hit", "entity.arrow.hit_player", "entity.enderpearl.throw", "entity.endermen.teleport",
			"entity.generic.eat", "entity.player.burp", "entity.generic.drink", "entity.splash_potion.throw",
			"entity.splash_potion.break", "entity.player.levelup", "entity.experience_orb.pickup",
			"entity.item.pickup", "entity.item.break", "item.shield.block", "item.shield.break", "item.totem.use",
			"block.stone.break", "block.stone.place", "block.wood.break", "block.wood.place", "block.glass.break",
			"block.anvil.land", "item.bucket.fill", "item.bucket.empty", "item.bucket.empty_lava",
			"item.flintandsteel.use", "block.fire.extinguish", "entity.bobber.throw", "entity.snowball.throw",
			"entity.egg.throw", "entity.firework.launch", "entity.blaze.shoot", "item.armor.equip_diamond",
			"item.armor.equip_iron", "item.armor.equip_generic", "block.chest.open", "block.chest.close",
			"ui.button.click", "block.note.pling" };

	private static int texIdx, sndEventIdx, sndIdx;
	private static boolean tcCopyDone;
	private static final ArrayList<Sound> sndList = new ArrayList<Sound>();
	private static Object lastWorld;
	private static long lastBusy, nextInWorld, nextShaderSave;

	/** Sounds were dropped from memory (resource reload): check them again. */
	public static void resetSounds() {
		sndEventIdx = 0;
		sndIdx = 0;
		sndList.clear();
	}

	/** Called once per frame from the game loop. */
	public static void tick(Minecraft mc) {
		if (mc.getTextureManager() == null || mc.getSoundHandler() == null) {
			return;
		}
		if (mc.world != lastWorld) {
			// a new world: sounds that dropped out of memory since the last one get loaded again
			lastWorld = mc.world;
			sndEventIdx = 0;
			sndIdx = 0;
		}
		long now = EagRuntime.steadyTimeMillis();
		if (now > nextShaderSave) {
			nextShaderSave = now + 10000L;
			try {
				FixedFunctionPipeline.tcSave();
			} catch (Throwable t) {
			}
		}
		boolean loading = mc.world == null || mc.player == null || mc.currentScreen instanceof GuiDownloadTerrain;
		if (!loading) {
			// in a world: only when nothing has happened for a while, one file at a time
			EntityPlayerSP p = mc.player;
			if (p.hurtTime > 0 || p.isSwingInProgress || p.isHandActive() || p.deathTime > 0) {
				lastBusy = now;
			}
			if (now - lastBusy < 8000L || now < nextInWorld) {
				return;
			}
			nextInWorld = now + 250L;
			step(mc);
			return;
		}
		long start = EagRuntime.nanoTime();
		while (step(mc) && EagRuntime.nanoTime() - start < 8000000L) {
		}
	}

	/** Loads one file that isn't loaded yet. False when everything is loaded. */
	private static boolean step(Minecraft mc) {
		try {
			// shaders first: an uncompiled one stalls a frame for 20-100 ms
			if (FixedFunctionPipeline.tcWarmStep()) {
				return true;
			}
			if (!tcCopyDone) {
				if (net.lax1dude.eaglercraft.opengl.TextureCopyUtil.tcWarmStep()) {
					return true;
				}
				tcCopyDone = true;
			}
			TextureManager tm = mc.getTextureManager();
			while (texIdx < TEXTURES.length) {
				ResourceLocation loc = new ResourceLocation(TEXTURES[texIdx++]);
				if (tm.getTexture(loc) == null) {
					tm.loadTexture(loc, new SimpleTexture(loc));
					return true;
				}
			}
			SoundHandler sh = mc.getSoundHandler();
			while (true) {
				while (sndIdx < sndList.size()) {
					if (sh.tcManager().tcWarm(sndList.get(sndIdx++))) {
						return true;
					}
				}
				if (sndEventIdx >= SOUNDS.length) {
					return false;
				}
				if (sndEventIdx == 0) {
					sndList.clear();
					sndIdx = 0;
				}
				SoundEventAccessor acc = sh.getAccessor(new ResourceLocation(SOUNDS[sndEventIdx++]));
				if (acc != null) {
					acc.tcCollect(sndList);
				}
			}
		} catch (Throwable t) {
			texIdx = TEXTURES.length; // never let a warm-up problem break the game
			sndEventIdx = SOUNDS.length;
			sndIdx = sndList.size();
			return false;
		}
	}
}
