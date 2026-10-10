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
	private static long lastRead = 0L;

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
			}
		} catch (Throwable t) {
			theme = "normal";
		}
		return theme;
	}

	/** True when the Frost theme is selected (icy palette + snowy main menu). */
	public static boolean isFrost() {
		return "frost".equals(current());
	}

	/** True when the Tropical theme is selected (turquoise + sand palette). */
	public static boolean isTropical() {
		return "tropical".equals(current());
	}
}
