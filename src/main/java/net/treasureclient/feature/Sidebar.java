package net.treasureclient.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.treasureclient.compat.Gfx;
import net.treasureclient.core.Config;

/** Scoreboard: hide the red numbers, resize the sidebar, change its background. */
public final class Sidebar {
    private Sidebar() {}

    public static NumberFormat numbers(NumberFormat f) {
        Config c = Config.get();
        return c.on("sbOn") && c.flag("sbNoNums", true) ? BlankFormat.INSTANCE : f;
    }

    public static int background(int argb) {
        Config c = Config.get();
        if (!c.on("sbOn")) return argb;
        return switch (c.str("sbBg", "normal")) {
            case "none" -> 0;
            case "light" -> ((argb >>> 24) / 2) << 24 | (argb & 0xFFFFFF);
            default -> argb;
        };
    }

    private static boolean scaled;

    /** Before the sidebar is drawn: scale it around the middle of the right edge (where it sits). */
    public static void before() {
        scaled = false;
        Config c = Config.get();
        if (!c.on("sbOn")) return;
        int pct = Math.max(50, Math.min(150, c.num("sbScale", 100)));
        if (pct == 100) return;
        float s = pct / 100f;
        var win = Minecraft.getInstance().getWindow();
        float w = win.getGuiScaledWidth(), h = win.getGuiScaledHeight() / 2f;
        Gfx.pose().pushMatrix();
        Gfx.pose().translate(w, h);
        Gfx.pose().scale(s, s);
        Gfx.pose().translate(-w, -h);
        scaled = true;
    }

    public static void after() {
        if (scaled) Gfx.pose().popMatrix();
        scaled = false;
    }
}
