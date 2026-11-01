package net.minecraft.client;

import net.lax1dude.eaglercraft.EagRuntime;
import net.minecraft.client.gui.TreasureMods;

/**
 * Treasure Client: Auto GG. When a game ends (a "Winner:" / "won the game" style
 * message in chat, or a VICTORY / GAME OVER title) it says gg in chat for you, once,
 * after a short delay. Can also say "glhf" when a game starts.
 */
public final class TCAutoGG {

	private TCAutoGG() {
	}

	/** Chat lines (lower case) that mean a game just ended. */
	private static final String[] END = { "winner:", "winners:", "winning team:", "winner is", "1st killer",
			"#1 killer", "1st place", "won the game", "won the match", "won the duel", "game over", "match over", "match ended", "game ended", "duel ended",
			"reward summary", "victory!", "defeat!" };

	/** Titles (lower case, whole title) that mean a game just ended. */
	private static final String[] END_TITLE = { "victory", "victory!", "defeat", "defeat!", "game over",
			"game over!", "you won", "you won!", "you win", "you win!", "you lost", "you lost!",
			"draw", "draw!" };

	/** Chat lines (lower case) that mean a game just started. */
	private static final String[] START = { "the game starts in 1 second", "the game has started",
			"the match has started", "the game has begun", "the match has begun", "game started!", "match started!",
			"fight!" };

	private static final long COOLDOWN_MS = 10000L;

	private static long sendAt, lastGG, lastGL;
	private static String pending;

	/** A chat message from the server. */
	public static void onChat(String text) {
		if (!TreasureMods.autoGG() || text == null) {
			return;
		}
		String t = text.trim().toLowerCase();
		if (t.isEmpty() || t.length() > 120) {
			return;
		}
		if (matches(t, END)) {
			gameEnded();
		} else if (TreasureMods.agGlhf() && matches(t, START)) {
			long now = EagRuntime.steadyTimeMillis();
			if (now - lastGL > COOLDOWN_MS && now - lastGG > COOLDOWN_MS) {
				lastGL = now;
				schedule("glhf", 300L);
			}
		}
	}

	/** A big title on screen (only the main title, not subtitles). */
	public static void onTitle(String text) {
		if (!TreasureMods.autoGG() || text == null) {
			return;
		}
		String t = text.trim().toLowerCase();
		for (String s : END_TITLE) {
			if (t.equals(s)) {
				gameEnded();
				return;
			}
		}
	}

	/**
	 * True when the line contains one of the phrases and doesn't look like a player
	 * talking ("Name: ..." or "<Name> ..." before the phrase).
	 */
	private static boolean matches(String t, String[] phrases) {
		for (String s : phrases) {
			int i = t.indexOf(s);
			if (i < 0) {
				continue;
			}
			String before = t.substring(0, i);
			if (before.indexOf(':') >= 0 || before.indexOf('>') >= 0 || before.indexOf('»') >= 0) {
				continue; // someone typed it in chat
			}
			return true;
		}
		return false;
	}

	private static void gameEnded() {
		long now = EagRuntime.steadyTimeMillis();
		if (now - lastGG < COOLDOWN_MS) {
			return; // one gg per game, even when the server sends several end messages
		}
		lastGG = now;
		schedule(TreasureMods.agMessage(), TreasureMods.agDelay());
	}

	private static void schedule(String msg, long delay) {
		pending = msg;
		sendAt = EagRuntime.steadyTimeMillis() + Math.max(0L, delay);
	}

	/** Sends the message once its delay is up. */
	public static void tick(Minecraft mc) {
		if (pending == null || EagRuntime.steadyTimeMillis() < sendAt) {
			return;
		}
		String msg = pending;
		pending = null;
		if (mc.player != null && mc.world != null && TreasureMods.autoGG()) {
			mc.player.sendChatMessage(msg);
		}
	}
}
