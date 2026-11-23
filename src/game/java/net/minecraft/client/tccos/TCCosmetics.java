package net.minecraft.client.tccos;

import java.nio.charset.StandardCharsets;

import net.lax1dude.eaglercraft.EagRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.TreasureButtons;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Treasure Client cosmetics: what you're wearing (cape, aura, aura colour), saved in the
 * game's storage under "tccos" as plain lines ("cape=frost", "aura=halo", "color=theme").
 *
 * Step 1: only your own cosmetics exist, so only you see them (press F5). Sharing them with
 * other Treasure Client players comes next, through {@link #of(EntityPlayer)}.
 */
public final class TCCosmetics {

	private TCCosmetics() {
	}

	/** Cape ids in menu order ("none" = your normal Eaglercraft cape). */
	public static final String[] CAPES = { "none", "treasure", "frost", "tropical", "ember", "aurora", "galaxy", "circuit", "theme" };
	public static final String[] AURAS = { "none", "halo", "orbit", "rising", "ripple", "helix" };
	public static final String[] COLORS = { "theme", "gold", "ice", "aqua", "ember", "violet", "rose", "lime", "white", "rainbow" };
	private static final int[] COLOR_RGB = { 0, 0xFFD76B, 0x7FE9FF, 0x45E3C6, 0xFF7A3C, 0xB38CFF, 0xFF6FA8, 0x9BEA5B, 0xF4F4F4, 0 };

	/** One player's look. */
	public static final class Look {
		public String cape = "none";
		public String aura = "none";
		public String color = "theme";

		public boolean hasCape() {
			return !"none".equals(cape) && TCCapeArt.known(cape);
		}

		public boolean hasAura() {
			return !"none".equals(aura);
		}

		/** The aura colour as 0xRRGGBB right now (rainbow changes over time). */
		public int auraRgb(long timeMs, float offset) {
			int i = indexOf(COLORS, color);
			if (i <= 0) return TreasureButtons.accent() & 0xFFFFFF;
			if ("rainbow".equals(color)) {
				float[] c = TCCapeArt.hsv((timeMs % 7000L) / 7000f + offset, 0.6f, 1f);
				return (TCCapeArt.c8(c[0]) << 16) | (TCCapeArt.c8(c[1]) << 8) | TCCapeArt.c8(c[2]);
			}
			return COLOR_RGB[i];
		}
	}

	private static Look mine = null;

	/** Your own cosmetics. */
	public static Look mine() {
		if (mine == null) {
			mine = new Look();
			try {
				byte[] b = EagRuntime.getStorage("tccos");
				if (b != null) {
					for (String line : new String(b, StandardCharsets.UTF_8).split("\n")) {
						int eq = line.indexOf('=');
						if (eq <= 0) continue;
						String k = line.substring(0, eq).trim(), v = line.substring(eq + 1).trim();
						if ("cape".equals(k) && indexOf(CAPES, v) >= 0) mine.cape = v;
						else if ("aura".equals(k) && indexOf(AURAS, v) >= 0) mine.aura = v;
						else if ("color".equals(k) && indexOf(COLORS, v) >= 0) mine.color = v;
					}
				}
			} catch (Throwable t) {
			}
		}
		return mine;
	}

	/** Changes one of your cosmetics ("cape", "aura" or "color") and saves it. */
	public static boolean set(String what, String id) {
		Look m = mine();
		if ("cape".equals(what) && indexOf(CAPES, id) >= 0) m.cape = id;
		else if ("aura".equals(what) && indexOf(AURAS, id) >= 0) m.aura = id;
		else if ("color".equals(what) && indexOf(COLORS, id) >= 0) m.color = id;
		else return false;
		try {
			String s = "cape=" + m.cape + "\naura=" + m.aura + "\ncolor=" + m.color + "\n";
			EagRuntime.setStorage("tccos", s.getBytes(StandardCharsets.UTF_8));
		} catch (Throwable t) {
		}
		return true;
	}

	/** What a player in the world is wearing, or null for none. */
	public static Look of(EntityPlayer p) {
		if (p == null) return null;
		Minecraft mc = Minecraft.getMinecraft();
		if (p == mc.player) return mine();
		return null; // other Treasure Client players: comes with sharing (step 2)
	}

	public static int indexOf(String[] a, String v) {
		for (int i = 0; i < a.length; ++i) {
			if (a[i].equals(v)) return i;
		}
		return -1;
	}
}
