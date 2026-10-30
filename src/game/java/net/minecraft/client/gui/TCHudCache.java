package net.minecraft.client.gui;

import net.lax1dude.eaglercraft.EagRuntime;

/**
 * Treasure Client: HUD cache (part of the ImmediatelyFast port).
 *
 * The HUD is drawn into its own image, which is pasted over the world every frame
 * with one draw call. The image is only drawn again when something on the HUD can
 * have changed:
 * <ul>
 * <li>the player's own state changed (health, food, armor, XP, hotbar, potions...),
 * checked each frame by {@link GuiIngame#tcHudSignature()};</li>
 * <li>the server changed the chat, scoreboard, boss bars, titles or tab list
 * ({@link #markDirty()} is called when those arrive);</li>
 * <li>something on it animates every frame (F3, titles, the hotbar pop when picking
 * items up, a menu is open...), then it is drawn every frame like before;</li>
 * <li>and 5 times a second regardless, as a safety net.</li>
 * </ul>
 */
public final class TCHudCache {

	private TCHudCache() {
	}

	/** Most time between redraws, so nothing can stay out of date for long. */
	private static final long MAX_AGE_MS = 200L;

	private static int version;
	private static int drawnVersion = -1;
	private static long drawnSignature;
	private static long drawnAt;
	private static boolean valid;

	/** Something the HUD shows changed (chat, scoreboard, boss bar, title, tab list). */
	public static void markDirty() {
		++version;
	}

	/** Throws the cached image away, so the next frame draws the HUD again. */
	public static void invalidate() {
		valid = false;
	}

	/** Reuse the cached HUD image. */
	public static final int REUSE = 0;
	/** Draw the HUD into the cached image, then paste it. */
	public static final int REDRAW = 1;
	/** Draw the HUD straight onto the screen (it animates every frame, so caching wouldn't help). */
	public static final int DIRECT = 2;

	/** How to draw the HUD this frame. */
	public static int decide(GuiIngame gui) {
		long sig = gui.tcHudSignature();
		if (sig == Long.MIN_VALUE) {
			valid = false;
			return DIRECT;
		}
		long now = EagRuntime.steadyTimeMillis();
		if (!valid || sig != drawnSignature || version != drawnVersion || now - drawnAt >= MAX_AGE_MS) {
			drawnSignature = sig;
			drawnVersion = version;
			drawnAt = now;
			valid = true;
			return REDRAW;
		}
		return REUSE;
	}
}
