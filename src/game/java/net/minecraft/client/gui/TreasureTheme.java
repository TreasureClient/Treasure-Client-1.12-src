package net.minecraft.client.gui;

import java.nio.charset.StandardCharsets;

import net.lax1dude.eaglercraft.EagRuntime;

/**
 * Treasure Client — active client theme (shared source of truth).
 *
 * The in-browser Mod Menu's "Themes" tab writes the selected theme id to
 * EagRuntime storage key "tctheme" (localStorage "_eaglercraft_1.12.tctheme",
 * Base64 of the plain id, e.g. "frost" / "normal"). The native game UI reads it
 * here so selecting a theme restyles the real main menu and every button, not
 * just the HTML overlay.
 *
 * The read is throttled so theme-aware draw code can call {@link #isFrost()}
 * every frame without touching storage each time.
 */
public final class TreasureTheme {

	private TreasureTheme() {
	}

	private static String theme = "normal";
	private static String bg = "";
	private static boolean blur = false;
	private static long lastRead = 0L;

	/** The backgrounds you can pick for a theme (each theme starts on its own one). */
	public static final String[] BACKGROUNDS = { "treasure", "frost", "tropical", "terracotta", "forest", "sakura", "dark" };

	/** Current theme id, refreshed from storage at most ~4x/second. */
	public static String current() {
		try {
			long now = EagRuntime.steadyTimeMillis();
			if (now - lastRead > 250L) {
				lastRead = now;
				byte[] b = EagRuntime.getStorage("tctheme");
				if (b != null && b.length > 0) {
					String s = new String(b, StandardCharsets.UTF_8).trim();
					theme = s.isEmpty() ? "normal" : s;
				} else {
					theme = "normal";
				}
				// the Treasure Settings page writes the background picked for this theme ("tcbg")
				// and whether to blur it ("tcbgblur" = 1); sharp by default
				byte[] g = EagRuntime.getStorage("tcbg");
				bg = g != null ? new String(g, StandardCharsets.UTF_8).trim() : "";
				byte[] u = EagRuntime.getStorage("tcbgblur");
				blur = u != null && u.length > 0 && u[0] == (byte) '1';
			}
		} catch (Throwable t) {
			theme = "normal";
		}
		return theme;
	}

	/** The background picked for the current theme (one of {@link #BACKGROUNDS}). */
	public static String background() {
		String th = current();
		for (String b : BACKGROUNDS) {
			if (b.equals(bg)) return b;
		}
		return "normal".equals(th) ? "treasure" : th;
	}

	/** True when the current theme's background should be blurred (off by default). */
	public static boolean blurBackground() {
		current();
		return blur;
	}

	/** True when the Frost theme is selected (icy palette + snowy main menu). */
	public static boolean isFrost() {
		return "frost".equals(current());
	}

	/** True when the Tropical theme is selected (turquoise + sand palette). */
	public static boolean isTropical() {
		return "tropical".equals(current());
	}

	/** True when the Terracotta theme is selected (burnt-orange palette). */
	public static boolean isTerracotta() {
		return "terracotta".equals(current());
	}

	/** True when the Sakura theme is selected (cherry-blossom pink). */
	public static boolean isSakura() {
		return "sakura".equals(current());
	}

	/** True when the Dark theme is selected (near-black with a soft lilac accent). */
	public static boolean isDark() {
		return "dark".equals(current());
	}

	/** True when the Forest theme is selected (green palette). */
	public static boolean isForest() {
		return "forest".equals(current());
	}
}
