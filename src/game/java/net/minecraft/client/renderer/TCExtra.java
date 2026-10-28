package net.minecraft.client.renderer;

/**
 * Treasure Client: Celeritas Extra port (Sumire-Labs, LGPL-3.0). Extra
 * switches to turn off animations, particles, sky parts and some block
 * entities and entities. Values come from the overlay through TreasureMods.
 * Everything defaults to on, so the game looks vanilla until something is
 * switched off. With the master switch off, every option counts as on.
 */
public class TCExtra {

	private static final String[] KEYS = { "ceAnim", "ceAnimWater", "ceAnimLava", "ceAnimFire", "ceAnimPortal",
			"ceAnimBlock", "ceRainSplash", "ceBreakFx", "ceHitFx", "ceSky", "ceStars", "ceSunMoon", "ceWeather",
			"ceFrames", "ceStands", "cePaintings", "ceBeacon", "ceEnchBook", "cePiston", "ceToasts" };

	private static boolean master = true;
	private static final boolean[] raw = new boolean[KEYS.length];

	public static boolean animations = true, animWater = true, animLava = true, animFire = true, animPortal = true,
			animBlock = true, rainSplash = true, breakParticles = true, hitParticles = true, sky = true, stars = true,
			sunMoon = true, weather = true, itemFrames = true, armorStands = true, paintings = true, beaconBeam = true,
			enchantBook = true, pistons = true, toasts = true;

	static {
		reset();
		apply();
	}

	public static void reset() {
		master = true;
		for (int i = 0; i < raw.length; ++i) {
			raw[i] = true;
		}
	}

	/** Returns true if the key belongs to Celeritas Extra. */
	public static boolean parse(String k, String v) {
		if (k.equals("cextra")) {
			master = v.equals("1");
			return true;
		}
		if (!k.startsWith("ce")) {
			return false;
		}
		for (int i = 0; i < KEYS.length; ++i) {
			if (KEYS[i].equals(k)) {
				raw[i] = v.equals("1");
				return true;
			}
		}
		return false;
	}

	private static boolean get(int i) {
		return !master || raw[i];
	}

	public static void apply() {
		animations = get(0);
		animWater = get(1);
		animLava = get(2);
		animFire = get(3);
		animPortal = get(4);
		animBlock = get(5);
		rainSplash = get(6);
		breakParticles = get(7);
		hitParticles = get(8);
		sky = get(9);
		stars = get(10);
		sunMoon = get(11);
		weather = get(12);
		itemFrames = get(13);
		armorStands = get(14);
		paintings = get(15);
		beaconBeam = get(16);
		enchantBook = get(17);
		pistons = get(18);
		toasts = get(19);
	}

	public static boolean enabled() {
		return master;
	}

	// sprite animation groups, same split as Celeritas Extra
	public static final int ANIM_OTHER = 0, ANIM_WATER = 1, ANIM_LAVA = 2, ANIM_PORTAL = 3, ANIM_FIRE = 4,
			ANIM_BLOCK = 5;

	public static int classify(String icon) {
		if (icon == null) return ANIM_OTHER;
		if (icon.contains("water_still") || icon.contains("water_flow")) return ANIM_WATER;
		if (icon.contains("lava_still") || icon.contains("lava_flow")) return ANIM_LAVA;
		if (icon.contains("portal")) return ANIM_PORTAL;
		if (icon.contains("fire_layer_0") || icon.contains("fire_layer_1")) return ANIM_FIRE;
		if (icon.contains("magma") || icon.contains("sea_lantern") || icon.contains("prismarine")) return ANIM_BLOCK;
		return ANIM_OTHER;
	}

	public static boolean animates(int group) {
		if (!animations) return false;
		switch (group) {
		case ANIM_WATER: return animWater;
		case ANIM_LAVA: return animLava;
		case ANIM_PORTAL: return animPortal;
		case ANIM_FIRE: return animFire;
		case ANIM_BLOCK: return animBlock;
		default: return true;
		}
	}

	/** Entity kinds Celeritas Extra can hide. */
	public static boolean hidden(net.minecraft.entity.Entity e) {
		if (master) {
			if (!itemFrames && e instanceof net.minecraft.entity.item.EntityItemFrame) return true;
			if (!armorStands && e instanceof net.minecraft.entity.item.EntityArmorStand) return true;
			if (!paintings && e instanceof net.minecraft.entity.item.EntityPainting) return true;
		}
		return false;
	}
}
