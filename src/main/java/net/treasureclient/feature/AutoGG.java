package net.treasureclient.feature;

import net.minecraft.client.Minecraft;
import net.treasureclient.core.Config;

import java.util.Locale;

/**
 * Auto GG: when a game ends (a "Winner:" / "won the game" style message, or a VICTORY / GAME OVER
 * title) it says gg in chat for you, once, after a short delay. Can also say "glhf" when a game starts.
 * Same rules as the browser version (TCAutoGG).
 */
public final class AutoGG {
    private AutoGG() {}

    private static final String[] END = {"winner:", "winners:", "winning team:", "winner is", "1st killer",
        "#1 killer", "1st place", "won the game", "won the match", "won the duel", "game over", "match over",
        "match ended", "game ended", "duel ended", "reward summary", "victory!", "defeat!"};
    private static final String[] END_TITLE = {"victory", "victory!", "defeat", "defeat!", "game over",
        "game over!", "you won", "you won!", "you win", "you win!", "you lost", "you lost!", "draw", "draw!"};
    private static final String[] START = {"the game starts in 1 second", "the game has started",
        "the match has started", "the game has begun", "the match has begun", "game started!", "match started!",
        "fight!"};
    private static final long COOLDOWN_MS = 10000L;
    private static long sendAt, lastGG, lastGL;
    private static String pending;

    private static boolean on() {
        return Config.get().on("autoGG");
    }

    /** A message from the server (not a player's chat). */
    public static void onChat(String text) {
        if (!on() || text == null) return;
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty() || t.length() > 120) return;
        if (matches(t, END)) {
            gameEnded();
        } else if (Config.get().flag("agGlhf", false) && matches(t, START)) {
            long now = System.currentTimeMillis();
            if (now - lastGL > COOLDOWN_MS && now - lastGG > COOLDOWN_MS) {
                lastGL = now;
                schedule("glhf", 300L);
            }
        }
    }

    /** A big title on screen. */
    public static void onTitle(String text) {
        if (!on() || text == null) return;
        String t = text.trim().toLowerCase(Locale.ROOT);
        for (String s : END_TITLE)
            if (t.equals(s)) {
                gameEnded();
                return;
            }
    }

    /** True when the line has one of the phrases and doesn't look like a player typing it. */
    private static boolean matches(String t, String[] phrases) {
        for (String s : phrases) {
            int i = t.indexOf(s);
            if (i < 0) continue;
            String before = t.substring(0, i);
            if (before.indexOf(':') >= 0 || before.indexOf('>') >= 0 || before.indexOf('»') >= 0) continue;
            return true;
        }
        return false;
    }

    private static void gameEnded() {
        long now = System.currentTimeMillis();
        if (now - lastGG < COOLDOWN_MS) return; // one gg per game, even when the server sends several end messages
        lastGG = now;
        String msg = switch (Config.get().str("agMsg", "gg")) {
            case "ggs" -> "ggs";
            case "ggwp" -> "gg wp";
            case "goodgame" -> "good game";
            case "gf" -> "gf";
            default -> "gg";
        };
        schedule(msg, Config.get().num("agDelay", 1000));
    }

    private static void schedule(String msg, long delay) {
        pending = msg;
        sendAt = System.currentTimeMillis() + Math.max(0L, delay);
    }

    public static void tick(Minecraft mc) {
        if (pending == null || System.currentTimeMillis() < sendAt) return;
        String msg = pending;
        pending = null;
        if (mc.player != null && mc.level != null && on()) mc.player.connection.sendChat(msg);
    }
}
