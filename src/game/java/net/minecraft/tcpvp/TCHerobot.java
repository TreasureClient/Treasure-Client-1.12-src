package net.minecraft.tcpvp;

import net.minecraft.world.GameRules;
import net.minecraft.world.World;

/**
 * Treasure Client: /herobot settings, saved with the world as game rules.
 * <ul>
 *   <li>botLeaveOnDeath: bots leave when they die instead of respawning.</li>
 *   <li>shieldStunning: stunning a shield with an axe leaves no hit cooldown, so the
 *       next hit (like a mace smash) lands right away.</li>
 *   <li>noProjectileRandom: arrows, pearls, snowballs and wind charges fly perfectly straight.</li>
 *   <li>explosionNoBlockDamage: explosions (crystals, TNT, carts) break no blocks.</li>
 * </ul>
 */
public final class TCHerobot {

	public static final String LEAVE_ON_DEATH = "botLeaveOnDeath";
	public static final String SHIELD_STUNNING = "shieldStunning";
	public static final String NO_PROJECTILE_RANDOM = "noProjectileRandom";
	public static final String NO_BLOCK_DAMAGE = "explosionNoBlockDamage";

	public static final String[] SETTINGS = { LEAVE_ON_DEATH, SHIELD_STUNNING, NO_PROJECTILE_RANDOM, NO_BLOCK_DAMAGE };
	public static final String[] INFO = { "bots leave when they die instead of respawning",
			"an axe-stunned shield leaves no hit cooldown, so the next hit lands at once",
			"arrows, pearls and wind charges fly perfectly straight",
			"explosions break no blocks" };
	/** Defaults in a Practice world (elsewhere everything is off). */
	private static final boolean[] PRACTICE_DEFAULT = { false, true, false, false };

	private TCHerobot() {
	}

	public static boolean get(World world, String name) {
		if (world == null || world.isRemote) {
			return false;
		}
		GameRules r = world.getGameRules();
		if (r.hasRule(name)) {
			return r.getBoolean(name);
		}
		if (TCPractice.isPractice(world)) {
			for (int i = 0; i < SETTINGS.length; i++) {
				if (SETTINGS[i].equals(name)) {
					return PRACTICE_DEFAULT[i];
				}
			}
		}
		return false;
	}

	public static void set(World world, String name, boolean value) {
		world.getGameRules().setOrCreateGameRule(name, value ? "true" : "false");
	}

	public static String byName(String s) {
		for (String n : SETTINGS) {
			if (n.equalsIgnoreCase(s)) {
				return n;
			}
		}
		return null;
	}

	public static boolean shieldStunning(World w) {
		return get(w, SHIELD_STUNNING);
	}

	public static boolean noProjectileRandom(World w) {
		return get(w, NO_PROJECTILE_RANDOM);
	}

	public static boolean noBlockDamage(World w) {
		return get(w, NO_BLOCK_DAMAGE);
	}
}
