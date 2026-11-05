package net.minecraft.client.renderer.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntityPolarBear;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityStray;
import net.minecraft.entity.monster.EntityVex;
import net.minecraft.entity.monster.EntityZombieVillager;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityDonkey;
import net.minecraft.entity.passive.EntityLlama;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.passive.EntityParrot;
import net.minecraft.entity.passive.EntityRabbit;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.util.ResourceLocation;

/**
 * Treasure Client: modern mobs on servers that only send ViaBackwards stand-ins.
 *
 * ViaBackwards shows a modern mob as an old one with its name on top ("Salmon" on a
 * squid, "Hoglin" on a cow). When such a name arrives (only on a name change, never per
 * frame) the entity gets a type here; RenderManager then draws it with the real modern
 * model and texture and without the name tag. The old mob class must match the one
 * ViaBackwards uses, so a pig someone named "Fox" stays a pig.
 */
public final class TCViaMobs {

	private TCViaMobs() {
	}

	static final int COD = 1, SALMON = 2, PUFFERFISH = 3, TROPICAL_A = 4, TROPICAL_B = 5, DOLPHIN = 6,
			GLOW_SQUID = 7, BEE = 8, AXOLOTL = 9, TADPOLE = 10, NAUTILUS = 11, DROWNED = 12, PHANTOM = 13,
			TURTLE = 14, TRADER_LLAMA = 15, FOX = 16, PANDA = 17, PILLAGER = 18, WANDERING_TRADER = 19,
			RAVAGER = 20, HOGLIN = 21, ZOGLIN = 22, SNIFFER = 23, ARMADILLO = 24, PIGLIN = 25, PIGLIN_BRUTE = 26,
			STRIDER = 27, FROG = 28, COPPER_GOLEM = 29, WARDEN = 30, CREAKING = 31, ALLAY = 32, GOAT = 33,
			CAMEL = 34, BOGGED = 35, PARCHED = 36, COUNT = 37;

	private static RenderViaMob[] cache;

	/** Called when an entity's custom name changes on the client. */
	public static void onName(Entity e) {
		int t = 0;
		if (net.minecraft.client.gui.TreasureMods.viaEnabled()) {
			String n = e.getCustomNameTag();
			if (n != null && n.length() > 2 && n.length() < 40) {
				t = match(net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(n).trim().toLowerCase(), e);
			}
		}
		e.tcViaMob = (byte) t;
	}

	/** Squids that are really fish keep the server's facing instead of squid steering. */
	public static boolean ownsFacing(Entity e) {
		return e.tcViaMob != 0 && e.tcViaMob != GLOW_SQUID;
	}

	private static int match(String n, Entity e) {
		boolean squid = e instanceof EntitySquid;
		switch (n) {
		case "cod": return squid ? COD : 0;
		case "salmon": return squid ? SALMON : 0;
		case "pufferfish": return squid ? PUFFERFISH : 0;
		case "tropical fish": return squid ? ((e.getEntityId() & 1) == 0 ? TROPICAL_A : TROPICAL_B) : 0;
		case "dolphin": return squid ? DOLPHIN : 0;
		case "glow squid": return squid ? GLOW_SQUID : 0;
		case "bee": return squid ? BEE : 0;
		case "axolotl": return squid ? AXOLOTL : 0;
		case "tadpole": return squid ? TADPOLE : 0;
		case "nautilus": return squid ? NAUTILUS : 0;
		case "drowned": return e instanceof EntityZombieVillager ? DROWNED : 0;
		case "phantom": return e instanceof EntityParrot ? PHANTOM : 0;
		case "turtle": return e instanceof EntityOcelot ? TURTLE : 0;
		case "trader llama": return e instanceof EntityLlama ? TRADER_LLAMA : 0;
		case "fox": return e instanceof EntityWolf ? FOX : 0;
		case "panda": return e instanceof EntityPolarBear ? PANDA : 0;
		case "pillager": return e instanceof EntityVillager ? PILLAGER : 0;
		case "wandering trader": return e instanceof EntityVillager ? WANDERING_TRADER : 0;
		case "ravager": return e instanceof EntityCow ? RAVAGER : 0;
		case "hoglin": return e instanceof EntityCow ? HOGLIN : 0;
		case "zoglin": return e instanceof EntityCow ? ZOGLIN : 0;
		case "sniffer": return e instanceof EntityCow ? SNIFFER : 0;
		case "armadillo": return e instanceof EntityCow ? ARMADILLO : 0;
		case "piglin": return e instanceof EntityPigZombie ? PIGLIN : 0;
		case "piglin brute": return e instanceof EntityPigZombie ? PIGLIN_BRUTE : 0;
		case "strider": return e instanceof EntityMagmaCube ? STRIDER : 0;
		case "frog": return e instanceof EntityRabbit ? FROG : 0;
		case "copper golem": return e instanceof EntityRabbit ? COPPER_GOLEM : 0;
		case "warden": return e instanceof EntityIronGolem ? WARDEN : 0;
		case "creaking": return e instanceof EntityIronGolem ? CREAKING : 0;
		case "allay": return e instanceof EntityVex ? ALLAY : 0;
		case "goat": return e instanceof EntitySheep ? GOAT : 0;
		case "camel": return e instanceof EntityDonkey ? CAMEL : 0;
		case "bogged": return e instanceof EntityStray ? BOGGED : 0;
		case "parched": return e instanceof EntitySkeleton ? PARCHED : 0;
		default: return 0;
		}
	}

	/** The modern renderer for this type, built the first time it is needed. */
	static RenderViaMob renderer(RenderManager rm, int type) {
		if (type <= 0 || type >= COUNT) return null;
		if (cache == null) cache = new RenderViaMob[COUNT];
		RenderViaMob r = cache[type];
		if (r == null) {
			try {
				r = create(rm, type);
			} catch (Throwable t) {
				r = null;
			}
			if (r == null) return null;
			cache[type] = r;
		}
		return r;
	}

	private static ResourceLocation tex(String name) {
		return new ResourceLocation("textures/entity/via/" + name + ".png");
	}

	private static RenderViaMob mob(RenderManager rm, int model, int w, int h, int flags, String[] hide, float shadow,
			String... tex) {
		ResourceLocation[] t = new ResourceLocation[tex.length];
		for (int i = 0; i < tex.length; ++i) t[i] = tex(tex[i]);
		return new RenderViaMob(rm, new TCViaModel(model, w, h, flags, hide), shadow, (flags & TCViaModel.TENT) != 0, t);
	}

	private static RenderViaMob create(RenderManager rm, int type) {
		final int L = TCViaModel.LOOK, G = TCViaModel.LEGS, A = TCViaModel.ARMS, F = TCViaModel.FISH;
		final int BIPED = L | G | A, QUAD = L | G;
		switch (type) {
		case COD: return mob(rm, TCViaModelData.COD, 32, 32, F, null, 0.3F, "cod");
		case SALMON: return mob(rm, TCViaModelData.SALMON, 32, 32, F, null, 0.4F, "salmon");
		case PUFFERFISH: return mob(rm, TCViaModelData.PUFFERFISHSMALL, 32, 32, F, null, 0.2F, "pufferfish");
		case TROPICAL_A: return mob(rm, TCViaModelData.TROPICALFISHSMALL, 32, 32, F, null, 0.15F,
				"tropical_a_0", "tropical_a_1", "tropical_a_2", "tropical_a_3");
		case TROPICAL_B: return mob(rm, TCViaModelData.TROPICALFISHLARGE, 32, 32, F, null, 0.15F,
				"tropical_b_0", "tropical_b_1", "tropical_b_2", "tropical_b_3");
		case DOLPHIN: return mob(rm, TCViaModelData.DOLPHIN, 64, 64, TCViaModel.SWIMX, null, 0.7F, "dolphin");
		case GLOW_SQUID: return mob(rm, TCViaModelData.SQUID, 64, 32, TCViaModel.TENT, null, 0.7F, "glow_squid");
		case BEE: return mob(rm, TCViaModelData.BEE, 64, 64, TCViaModel.WINGS_FAST, null, 0.4F, "bee");
		case AXOLOTL: return mob(rm, TCViaModelData.AXOLOTL, 64, 64, G | F, null, 0.4F, "axolotl");
		case TADPOLE: return mob(rm, TCViaModelData.TADPOLE, 16, 16, F, null, 0.14F, "tadpole");
		case NAUTILUS: return mob(rm, TCViaModelData.NAUTILUS, 128, 128, 0, null, 0.6F, "nautilus");
		case DROWNED: return mob(rm, TCViaModelData.DROWNED, 64, 64, BIPED, null, 0.5F, "drowned");
		case PHANTOM: return mob(rm, TCViaModelData.PHANTOM, 64, 64, TCViaModel.WINGS_SLOW, null, 0.75F, "phantom");
		case TURTLE: return mob(rm, TCViaModelData.TURTLE, 128, 64, QUAD, new String[] { "egg_belly" }, 0.7F, "turtle");
		case TRADER_LLAMA: return mob(rm, TCViaModelData.LLAMA, 128, 64, QUAD,
				new String[] { "right_chest", "left_chest" }, 0.7F, "trader_llama");
		case FOX: return mob(rm, TCViaModelData.FOX, 48, 32, QUAD, null, 0.4F, "fox");
		case PANDA: return mob(rm, TCViaModelData.PANDA, 64, 64, QUAD, null, 0.9F, "panda");
		case PILLAGER: return mob(rm, TCViaModelData.ILLAGER, 64, 64, BIPED, new String[] { "arms" }, 0.5F, "pillager");
		case WANDERING_TRADER: return mob(rm, TCViaModelData.VILLAGER, 64, 64, QUAD, null, 0.5F, "wandering_trader");
		case RAVAGER: return mob(rm, TCViaModelData.RAVAGER, 128, 128, QUAD, null, 1.1F, "ravager");
		case HOGLIN: return mob(rm, TCViaModelData.HOGLIN, 128, 64, QUAD, null, 0.7F, "hoglin");
		case ZOGLIN: return mob(rm, TCViaModelData.HOGLIN, 128, 64, QUAD, null, 0.7F, "zoglin");
		case SNIFFER: return mob(rm, TCViaModelData.SNIFFER, 192, 192, QUAD, null, 1.0F, "sniffer");
		case ARMADILLO: return mob(rm, TCViaModelData.ARMADILLO, 64, 64, QUAD, new String[] { "cube" }, 0.4F, "armadillo");
		case PIGLIN: return mob(rm, TCViaModelData.PIGLIN, 64, 64, BIPED, null, 0.5F, "piglin");
		case PIGLIN_BRUTE: return mob(rm, TCViaModelData.PIGLIN, 64, 64, BIPED, null, 0.5F, "piglin_brute");
		case STRIDER: return mob(rm, TCViaModelData.STRIDER, 64, 128, G, null, 0.5F, "strider");
		case FROG: return mob(rm, TCViaModelData.FROG, 48, 48, G | A, new String[] { "croaking_body", "tongue" },
				0.3F, "frog");
		case COPPER_GOLEM: return mob(rm, TCViaModelData.COPPERGOLEM, 64, 64, BIPED, null, 0.4F, "copper_golem");
		case WARDEN: return mob(rm, TCViaModelData.WARDEN, 128, 128, BIPED, null, 0.9F, "warden");
		case CREAKING: return mob(rm, TCViaModelData.CREAKING, 64, 64, BIPED, null, 0.6F, "creaking");
		case ALLAY: return mob(rm, TCViaModelData.ALLAY, 32, 32, L | TCViaModel.WINGS_FAST, null, 0.4F, "allay");
		case GOAT: return mob(rm, TCViaModelData.GOAT, 64, 64, QUAD, null, 0.7F, "goat");
		case CAMEL: return mob(rm, TCViaModelData.CAMEL, 128, 128, QUAD, null, 0.7F, "camel");
		case BOGGED: return mob(rm, TCViaModelData.SKELETON, 64, 32, BIPED, null, 0.5F, "bogged");
		case PARCHED: return mob(rm, TCViaModelData.PARCHED, 64, 64, BIPED, null, 0.5F, "parched");
		default: return null;
		}
	}
}
