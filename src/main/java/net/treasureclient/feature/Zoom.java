package net.treasureclient.feature;

import net.treasureclient.compat.V;
import net.minecraft.client.Minecraft;
import net.treasureclient.TreasureClient;
import net.treasureclient.core.Config;

/** Zoom: hold the Zoom key (C by default) to narrow the view to the Zoom field of view. */
public final class Zoom {
    private Zoom() {}

    private static float current = 1f;
    private static long last = System.nanoTime();

    public static float apply(float fov) {
        Config c = Config.get();
        boolean held = c.on("zoom") && TreasureClient.ZOOM_KEY.isDown() && V.screen() == null;
        float target = held ? Math.min(1f, c.num("zoomLevel", 30) / Math.max(1f, fov)) : 1f;
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        if (c.flag("smoothZoom", false)) current += (target - current) * Math.min(1f, dt * 12f);
        else current = target;
        return fov * current;
    }
}
