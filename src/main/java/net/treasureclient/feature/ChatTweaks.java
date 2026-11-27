package net.treasureclient.feature;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.treasureclient.core.Config;

import java.time.LocalTime;
import java.util.List;

/** Chat Tweaks: the time next to messages, repeated messages stacked into one line, a longer history. */
public final class ChatTweaks {
    private ChatTweaks() {}

    private static Component lastOut;
    private static String lastKey;
    private static int count;

    /**
     * A message about to go into chat. {@code all} is the chat's message list (newest first);
     * {@code refresh} rebuilds the lines on screen after a message is taken out.
     */
    public static Component incoming(Component c, List<?> all, Runnable refresh) {
        if (c == null || c == lastOut) return c; // 1.21.11 hands our own result on to a second addMessage
        Config cfg = Config.get();
        if (!cfg.on("chatTweaks")) {
            lastKey = null;
            return c;
        }
        String key = c.getString();
        MutableComponent out = c.copy();
        if (cfg.flag("ctStack", true) && key.equals(lastKey) && !all.isEmpty()) {
            count++;
            all.remove(0); // the previous copy (the newest message) goes; this one shows "(x2)"
            refresh.run();
            out.append(Component.literal(" (x" + count + ")").withStyle(ChatFormatting.GRAY));
        } else {
            count = 1;
        }
        lastKey = key;
        if (cfg.flag("ctTime", true))
            out = Component.literal("[" + now(cfg.str("ctTimeFmt", "24").equals("12")) + "] ").withStyle(ChatFormatting.DARK_GRAY).append(out);
        lastOut = out;
        return out;
    }

    /** How many messages chat keeps (Minecraft: 100). */
    public static int history(int vanilla) {
        Config cfg = Config.get();
        return cfg.on("chatTweaks") && cfg.flag("ctLong", true) ? 1000 : vanilla;
    }

    /** Chat was cleared. */
    public static void cleared() {
        lastKey = null;
        count = 0;
    }

    /** "14:05" or "2:05 PM". */
    public static String now(boolean twelve) {
        LocalTime t = LocalTime.now();
        int h = t.getHour(), m = t.getMinute();
        String mm = (m < 10 ? "0" : "") + m;
        if (!twelve) return (h < 10 ? "0" : "") + h + ":" + mm;
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + ":" + mm + (h < 12 ? " AM" : " PM");
    }
}
