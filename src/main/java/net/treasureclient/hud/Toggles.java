package net.treasureclient.hud;

import net.treasureclient.compat.V;
import net.minecraft.client.Minecraft;
import net.treasureclient.core.Config;

/** Toggle Sprint and Toggle Sneak: tapping the key flips it on or off (like the browser client). */
public final class Toggles {
    private Toggles() {}

    public static boolean sprint, sneak;

    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        var o = mc.options;
        Config c = Config.get();
        if (c.on("toggleSprint")) {
            while (o.keySprint.consumeClick()) sprint = !sprint;
            if (sprint && V.screen() == null) o.keySprint.setDown(true);
        } else if (sprint) {
            sprint = false;
            o.keySprint.setDown(false);
        }
        if (c.on("toggleSneak")) {
            while (o.keyShift.consumeClick()) sneak = !sneak;
            if (sneak && V.screen() == null) o.keyShift.setDown(true);
        } else if (sneak) {
            sneak = false;
            o.keyShift.setDown(false);
        }
    }

    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        if (sprint) mc.options.keySprint.setDown(false);
        if (sneak) mc.options.keyShift.setDown(false);
        sprint = sneak = false;
    }
}
