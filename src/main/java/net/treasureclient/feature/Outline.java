package net.treasureclient.feature;

import net.treasureclient.core.Config;
import net.treasureclient.core.Mods;

/** Block Outline: colour and thickness of the box around the block you look at. */
public final class Outline {
    private Outline() {}

    /** Minecraft's extra black pass for High Contrast Block Outline: left alone. */
    private static final int CONTRAST = 0xFF000000;

    public static int color(int c) {
        Config cfg = Config.get();
        if (!cfg.on("boOn") || c == CONTRAST) return c;
        int a = Math.round(255 * Math.max(10, Math.min(100, cfg.num("boAlpha", 40))) / 100f);
        return a << 24 | Mods.color("boColor");
    }

    public static float width(float w) {
        Config cfg = Config.get();
        if (!cfg.on("boOn") || w == 7f) return w;
        return w * Math.max(1, Math.min(6, cfg.num("boWidth", 2))) / 2f; // 2 = Minecraft's own thickness
    }
}
