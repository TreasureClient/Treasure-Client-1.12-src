package net.treasureclient.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.treasureclient.compat.V;
import net.treasureclient.core.Theme;

/**
 * The small "Screenshot saved" pop-up. Minecraft only tells you in chat, and there is no chat on the
 * main menu or in most screens, so F2 there looked like it did nothing. This shows on every screen,
 * in the 1.5 look, for 4.5 s at the bottom right. It is drawn after the screen (or as the last HUD
 * layer in a world), so it is never in the picture itself.
 */
public final class ShotToast {
    private ShotToast() {}

    private static final long SHOW_MS = 4500, SLIDE_MS = 220;

    // set from Minecraft's file-saving thread, read while drawing
    private static volatile long shownAt;
    private static volatile boolean ok;
    private static volatile String file = "";

    /** Called with the message Minecraft would put in chat after saving (or failing to save). */
    public static void saved(Component msg) {
        boolean good = true;
        String name = "";
        if (msg != null && msg.getContents() instanceof TranslatableContents t) {
            good = !t.getKey().contains("failure");
            Object[] args = t.getArgs();
            if (good && args.length > 0) name = args[0] instanceof Component c ? c.getString() : String.valueOf(args[0]);
        }
        file = name;
        ok = good;
        shownAt = now();
    }

    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }

    /** In a world without a screen: drawn by the HUD layer. */
    public static void renderHud() {
        if (V.screen() == null) render();
    }

    public static void render() {
        long at = shownAt;
        if (at == 0) return;
        long age = now() - at;
        if (age < 0 || age > SHOW_MS) return;
        float in = Math.min(1f, age / (float) SLIDE_MS), out = Math.min(1f, (SHOW_MS - age) / (float) SLIDE_MS);
        float t = Math.min(in, out);
        float e = 1f - (1f - t) * (1f - t) * (1f - t); // ease out
        Ui.begin();
        try {
            Theme th = Theme.current();
            String title = ok ? "Screenshot saved" : "Screenshot didn't work";
            String sub = ok ? (file.isEmpty() ? "In the screenshots folder" : "screenshots/" + file) : "Try again";
            float pad = 12, chest = 26, gap = 11;
            float tw = Math.max(Ui.width(Ui.SEMI, title, 15), Ui.width(Ui.MONO, sub, 10.5f, 0.2f));
            float w = pad + chest + gap + tw + pad + 2, h = 50;
            float x = Ui.vw - 18 - w, y = Ui.vh - 34 - h + (1f - e) * 10;
            Ui.rect(x, y, w, h, Theme.alpha(0xE00A0A0C, e));
            Ui.rect(x, y, 2, h, Theme.alpha(ok ? th.a1() : 0xFFE0604A, e));
            Ui.image(Ui.chest(), x + 2 + pad, y + (h - chest) / 2f, chest * 91f / 92f, chest, 91, 92, Theme.alpha(0xFFFFFFFF, e));
            float tx = x + 2 + pad + chest + gap;
            Ui.text(Ui.SEMI, title, tx, y + 9, 15, Theme.alpha(Theme.INK, e));
            Ui.mono(sub, tx, y + 30, 10.5f, Theme.alpha(Theme.INK_DIM, e), 0.2f);
        } finally {
            Ui.end();
        }
    }
}
