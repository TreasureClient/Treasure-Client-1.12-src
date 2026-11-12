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
 * Server changes are grouped: at most one redraw for them every 100 ms.
 * <p>
 * The HUD is cached as two layers: your own part (hotbar items, health, food, XP,
 * effects) and the server's part (scoreboard, chat, boss bars, titles, tab list).
 * Busy scoreboards and chat change many times a second; they now only redraw their
 * own layer, not the hotbar items, which are the slowest part of the HUD.
 */
public final class TCHudCache {

	private TCHudCache() {
	}

	/** Most time between redraws, so nothing can stay out of date for long. */
	private static final long MAX_AGE_MS = 1000L;
	/** Least time between redraws caused by the server (chat, scoreboard, boss bars, tab list). */
	private static final long SERVER_CHANGE_MS = 150L;

	private static int version;
	private static long drawnSignature;
	private static long drawnAt;
	private static boolean valid;
	// the server layer
	private static int drawnVersionS = -1;
	private static long drawnAtS;
	private static boolean validS;
	private static boolean lastChatOpen;
	private static long lastChatSig = Long.MIN_VALUE;
	/** Most time between redraws of your own layer (it is redrawn whenever it changes anyway). */
	private static final long OWN_MAX_AGE_MS = 1000L;
	/** Redraw interval of the server layer while something on it animates. */
	private static final long ANIM_MS = 50L;

	/** Something the HUD shows changed (chat, scoreboard, boss bar, title, tab list). */
	public static void markDirty() {
		++version;
	}

	/** Throws the cached image away, so the next frame draws the HUD again. */
	public static void invalidate() {
		valid = false;
		validS = false;
		validO = false;
	}

	// your own live layer (hearts, food, XP, effects, item counts...)
	private static long drawnSignatureO;
	private static long drawnAtO;
	private static boolean validO;

	/**
	 * How to draw your own live layer this frame (after {@link #decide} said it isn't DIRECT). It used
	 * to be drawn from scratch every frame (about 1 ms on slow laptops); now it is drawn into its own
	 * image only when something on it changes, at most once per game tick while something animates.
	 */
	public static int decideOwn(GuiIngame gui, float partialTicks, int scaledWidth, int scaledHeight) {
		long sig = gui.tcOwnSignature(partialTicks, scaledWidth, scaledHeight);
		if (sig == Long.MIN_VALUE) {
			validO = false;
			return DIRECT;
		}
		long now = EagRuntime.steadyTimeMillis();
		if (!validO || sig != drawnSignatureO || now - drawnAtO >= OWN_MAX_AGE_MS) {
			drawnSignatureO = sig;
			drawnAtO = now;
			validO = true;
			return REDRAW;
		}
		return REUSE;
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
			validS = false;
			validO = false;
			return DIRECT;
		}
		long now = EagRuntime.steadyTimeMillis();
		// Server-side changes (busy scoreboards, ping and team updates, chat) can arrive many times
		// a second, and each one used to redraw the whole HUD. They are grouped into at most one
		// redraw every 100 ms; changes to your own HUD (health, hotbar...) still redraw right away.
		if (!valid || sig != drawnSignature || now - drawnAt >= OWN_MAX_AGE_MS) {
			drawnSignature = sig;
			drawnAt = now;
			valid = true;
			return REDRAW;
		}
		return REUSE;
	}

	/** How to draw the server's layer this frame (after {@link #decide} said it isn't DIRECT). */
	public static int decideServer(GuiIngame gui) {
		long now = EagRuntime.steadyTimeMillis();
		// chat opened or closed: the chat looks different (all lines, no fading), redraw right away.
		// The HUD stays cached while chat is open; it used to be drawn from scratch every frame.
		boolean chatOpen = gui.getChatGUI().getChatOpen();
		if (chatOpen != lastChatOpen) {
			lastChatOpen = chatOpen;
			validS = false;
		}
		// a chat line started fading (it is drawn live from now on), or lines moved one that is
		// fading: redraw right away so the cached picture and the live lines never overlap
		long chatSig = gui.getChatGUI().tcCacheSignature(gui.getUpdateCounter());
		if (chatSig != lastChatSig) {
			lastChatSig = chatSig;
			validS = false;
		}
		if (gui.tcServerAnimating()) {
			// titles and the tab list change over ticks: redraw 20 times a second instead of every frame
			if (!validS || now - drawnAtS >= ANIM_MS) {
				drawnVersionS = version;
				drawnAtS = now;
				validS = true;
				return REDRAW;
			}
			return REUSE;
		}
		boolean serverChange = version != drawnVersionS && now - drawnAtS >= SERVER_CHANGE_MS;
		if (!validS || serverChange || now - drawnAtS >= MAX_AGE_MS) {
			drawnVersionS = version;
			drawnAtS = now;
			validS = true;
			return REDRAW;
		}
		return REUSE;
	}
}
