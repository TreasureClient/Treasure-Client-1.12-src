package net.minecraft.client.gui;

import net.lax1dude.eaglercraft.EagRuntime;

/**
 * Treasure Client — shared button theming.
 *
 * Gives every {@link GuiButton} in the client a consistent Treasure Client look:
 * rounded corners, a hollow outline in the active mode's accent color, and a
 * smooth hover animation that fills the button with that color. Because all of
 * the vanilla screens (Options, Edit Profile, Video Settings, world lists, …)
 * build their controls from GuiButton, restyling here restyles the whole client
 * at once.
 *
 * Mode color follows the same shared source of truth as everything else:
 * localStorage["_eaglercraft_1.12.tcmode"] (read via EagRuntime storage key
 * "tcmode"): 1 = Deep Sea (blue/cyan), else Normal (gold/yellow).
 */
public class TreasureButtons {

	// cache the mode so we don't hit storage on every button every frame
	private static boolean deepSea = false;
	private static long lastModeRead = 0L;

	// per-button hover fill amount (0..1), advanced by time so the fill animates
	// in/out smoothly instead of snapping. Stored on the button itself (see the
	// tcHoverFill field on GuiButton) — TeaVM doesn't support WeakHashMap.
	

	public static boolean isDeepSea() {
		// Treasure Client: Deep Sea mode has been removed. The client is now
		// single-theme (Normal / gold). This always returns false so all button
		// theming renders in the Normal palette.
		return false;
	}

	/**
	 * True when the Frost theme is active (selected in the Mod Menu's Themes tab).
	 * When it is, every button switches to the icy blue/cyan palette so the theme
	 * applies to the whole client, not just the main menu.
	 */
	private static boolean isFrost() {
		return TreasureTheme.isFrost();
	}

	private static boolean isTropical() {
		return TreasureTheme.isTropical();
	}

	// ---- palette (ARGB ints). Tropical = turquoise accent + sand text; Frost =
	// icy blue; else the default gold. ----
	private static int accent()      { return isTropical() ? 0xFF45E3C6 : isFrost() ? 0xFF7FE9FF : 0xFFFFD76B; } // main accent
	private static int accentDeep()  { return isTropical() ? 0xFF1AA7B0 : isFrost() ? 0xFF2EC4F1 : 0xFFD4A537; } // darker accent (gradient bottom)
	private static int textIdle()    { return isTropical() ? 0xFFF1E6C6 : isFrost() ? 0xFFDFF6FF : 0xFFF6ECD2; }
	private static int textOnFill()  { return isTropical() ? 0xFF042024 : isFrost() ? 0xFF03101A : 0xFF0F0A05; } // dark text on filled button
	private static int textDisabled(){ return 0xFF6A6A6A; }
	private static int panelBg()     { return isTropical() ? 0xE6071D22 : isFrost() ? 0xE6031019 : 0xE6140D06; } // hollow interior fill

	private static int lerpColor(int a, int b, float t) {
		int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
		int oa = (int) (aa + (ba - aa) * t);
		int or = (int) (ar + (br - ar) * t);
		int og = (int) (ag + (bg - ag) * t);
		int ob = (int) (ab + (bb - ab) * t);
		return (oa << 24) | (or << 16) | (og << 8) | ob;
	}

	/**
	 * Draw a rounded rectangle by stacking drawRect rows with the corners inset
	 * (1px on the outer row, then full width). Cheap and matches the pixel look.
	 */
	private static void roundedRect(int x, int y, int w, int h, int color) {
		if (w <= 0 || h <= 0) return;
		int x2 = x + w, y2 = y + h;
		// middle block
		Gui.drawRect(x, y + 2, x2, y2 - 2, color);
		// top/bottom rows inset by 2 (rounded feel)
		Gui.drawRect(x + 2, y, x2 - 2, y + 1, color);
		Gui.drawRect(x + 1, y + 1, x2 - 1, y + 2, color);
		Gui.drawRect(x + 2, y2 - 1, x2 - 2, y2, color);
		Gui.drawRect(x + 1, y2 - 2, x2 - 1, y2 - 1, color);
	}

	/** 1px rounded outline (four sides, corners inset to match roundedRect). */
	private static void roundedOutline(int x, int y, int w, int h, int color) {
		if (w <= 0 || h <= 0) return;
		int x2 = x + w, y2 = y + h;
		// top & bottom (inset ends)
		Gui.drawRect(x + 2, y, x2 - 2, y + 1, color);
		Gui.drawRect(x + 2, y2 - 1, x2 - 2, y2, color);
		// left & right (inset ends)
		Gui.drawRect(x, y + 2, x + 1, y2 - 2, color);
		Gui.drawRect(x2 - 1, y + 2, x2, y2 - 2, color);
		// corner pixels
		Gui.drawRect(x + 1, y + 1, x + 2, y + 2, color);
		Gui.drawRect(x2 - 2, y + 1, x2 - 1, y + 2, color);
		Gui.drawRect(x + 1, y2 - 2, x + 2, y2 - 1, color);
		Gui.drawRect(x2 - 2, y2 - 2, x2 - 1, y2 - 1, color);
	}

	/**
	 * Render a Treasure Client themed button. Returns the text color to use, so
	 * the caller (GuiButton) can draw its label on top with the right contrast.
	 *
	 * @param hovered whether the mouse is over the button this frame
	 * @param enabled whether the button is enabled
	 */
	public static int drawButton(GuiButton btn, int x, int y, int w, int h, boolean hovered, boolean enabled) {
		// advance this button's hover animation using the time since IT last drew
		// (a per-button clock — a single shared timer made only the first-drawn
		// button animate smoothly and left the rest crawling).
		long now = EagRuntime.steadyTimeMillis();
		float dt = (btn.tcLastDraw == 0L) ? 0f : Math.min(0.05f, (now - btn.tcLastDraw) / 1000f);
		btn.tcLastDraw = now;

		float target = (hovered && enabled) ? 1f : 0f;
		// ~140ms ease
		float speed = dt / 0.14f;
		if (btn.tcHoverFill < target) btn.tcHoverFill = Math.min(target, btn.tcHoverFill + speed);
		else if (btn.tcHoverFill > target) btn.tcHoverFill = Math.max(target, btn.tcHoverFill - speed);
		float fill = btn.tcHoverFill;

		int acc = accent();
		int accLo = accentDeep();

		if (!enabled) {
			// disabled: faint hollow outline, muted interior, grey text
			roundedRect(x, y, w, h, 0x99000000);
			roundedOutline(x, y, w, h, 0xFF3A3A3A);
			return textDisabled();
		}

		// interior: fade from hollow panel bg -> accent gradient as fill rises
		int interiorTop = lerpColor(panelBg(), acc, fill);
		int interiorBot = lerpColor(panelBg(), accLo, fill);
		// vertical gradient interior
		roundedRect(x, y, w, h, interiorTop);
		if (fill > 0.01f) {
			// overlay a subtle bottom-half gradient tint for depth when filling
			int botTint = (interiorBot & 0x00FFFFFF) | (((int) (0x55 * fill)) << 24);
			Gui.drawRect(x + 1, y + h / 2, x + w - 1, y + h - 2, botTint);
		}
		// outline always in accent (brighter as it fills)
		int outline = lerpColor(acc, 0xFFFFFFFF, fill * 0.25f);
		roundedOutline(x, y, w, h, outline);

		// text color: idle accent-tinted -> dark when filled (for contrast on the fill)
		lastTextShadow = (fill < 0.5f); // light text on dark => shadow; dark text on fill => none
		return lerpColor(textIdle(), textOnFill(), fill);
	}

	/** Whether the last drawButton() call wants its label drawn with a shadow.
	 *  Dark text on a bright hover-fill looks fuzzy with a shadow, so we drop it
	 *  once the fill passes halfway. */
	public static boolean lastTextShadow = true;
}
