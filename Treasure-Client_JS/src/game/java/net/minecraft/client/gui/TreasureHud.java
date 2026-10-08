package net.minecraft.client.gui;

import java.util.Collection;

import net.lax1dude.eaglercraft.EagRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;

/**
 * Treasure Client — HUD data bridge.
 *
 * Every client tick this pushes REAL, currently-visible game state (position,
 * facing, ping, armor durability, active potion effects, held item) out to
 * localStorage["_eaglercraft_1.12.tchud"] as a small plain-text blob. The
 * overlay (an HTML/CSS/JS layer, not part of the Java client) reads this once
 * per animation frame to draw the HUD mods (Coordinates, Direction, Ping,
 * Armor Status, Potion Effects, Held Item).
 *
 * This class only ever reports values that are genuinely available to the
 * client this tick — nothing here is fabricated or estimated, per the
 * Treasure Client non-cheat rule. When a value isn't available (no world
 * loaded, no ping response yet, empty slot) the corresponding field is
 * omitted or marked "-" and the overlay treats it as unknown.
 */
public class TreasureHud {

	private static long lastPush = 0L;

	public static void tick(Minecraft mc) {
		try {
			if (mc == null) return;
			long now = EagRuntime.steadyTimeMillis();
			// push ~10x/second — plenty smooth for a HUD, cheap on the tick loop
			if (now - lastPush < 100L) return;
			lastPush = now;

			EntityPlayer player = mc.player;
			if (player == null) {
				EagRuntime.setStorage("tchud", "active=0".getBytes(java.nio.charset.StandardCharsets.UTF_8));
				return;
			}

			StringBuilder sb = new StringBuilder(256);
			sb.append("active=1\n");
			sb.append("x=").append(fmt(player.posX)).append('\n');
			sb.append("y=").append(fmt(player.posY)).append('\n');
			sb.append("z=").append(fmt(player.posZ)).append('\n');
			sb.append("yaw=").append(fmt(normalizeYaw(player.rotationYaw))).append('\n');

			int ping = -1;
			try {
				if (mc.player.connection != null) {
					NetworkPlayerInfo info = mc.player.connection
							.getPlayerInfo(mc.player.getGameProfile().getId());
					if (info != null) ping = info.getResponseTime();
				}
			} catch (Throwable t) {}
			sb.append("ping=").append(ping).append('\n');
			// Treasure Client: sprint + sneak modes for the status HUD elements
			sb.append("sprintmode=").append(TreasureMods.sprintToggled() ? "toggled" : "vanilla").append('\n');
			sb.append("sneakmode=").append(TreasureMods.sneakToggled() ? "toggled" : "vanilla").append('\n');

			// armor: helmet, chestplate, leggings, boots (engine order is boots..helmet, so reverse)
			StringBuilder armor = new StringBuilder(64);
			try {
				ItemStack[] slots = new ItemStack[4];
				int i = 3;
				for (ItemStack st : player.getArmorInventoryList()) {
					if (i < 0) break;
					slots[i--] = st;
				}
				for (int s = 0; s < 4; s++) {
					if (s > 0) armor.append('~');
					ItemStack st = slots[s];
					if (st == null || st.func_190926_b()) { armor.append('-'); continue; }
					double frac = -1;
					if (st.isItemStackDamageable() && st.getMaxDamage() > 0) {
						frac = 1.0 - ((double) st.getItemDamage() / (double) st.getMaxDamage());
					}
					armor.append(safe(st.getDisplayName())).append('|').append(fmt(frac));
				}
			} catch (Throwable t) { armor = new StringBuilder("-~-~-~-"); }
			sb.append("armor=").append(armor).append('\n');

			// potion effects
			StringBuilder pot = new StringBuilder(64);
			try {
				Collection<PotionEffect> effects = player.getActivePotionEffects();
				boolean first = true;
				for (PotionEffect e : effects) {
					if (!first) pot.append('~');
					first = false;
					pot.append(safe(I18n.format(e.getEffectName()))).append('|').append(e.getAmplifier()).append('|')
							.append(e.getDuration() / 20);
				}
			} catch (Throwable t) {}
			sb.append("potions=").append(pot).append('\n');

			// held item (main hand)
			try {
				ItemStack held = player.getHeldItemMainhand();
				if (held == null || held.func_190926_b()) {
					sb.append("held=-\n");
				} else {
					double frac = -1;
					if (held.isItemStackDamageable() && held.getMaxDamage() > 0) {
						frac = 1.0 - ((double) held.getItemDamage() / (double) held.getMaxDamage());
					}
					sb.append("held=").append(safe(held.getDisplayName())).append('|')
							.append(held.func_190916_E()).append('|').append(fmt(frac)).append('\n');
				}
			} catch (Throwable t) { sb.append("held=-\n"); }

			EagRuntime.setStorage("tchud", sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		} catch (Throwable t) {
			// never let HUD reporting break the game loop
		}
	}

	private static float normalizeYaw(float yaw) {
		float y = yaw % 360.0f;
		if (y < 0) y += 360.0f;
		return y;
	}

	private static String fmt(double d) {
		return String.valueOf(Math.round(d * 10.0) / 10.0);
	}

	// strip characters that would break our tiny delimiter format
	private static String safe(String s) {
		if (s == null) return "";
		return s.replace('|', '/').replace('~', '-').replace('\n', ' ').replace('=', '-');
	}
}
