package net.treasureclient.compat;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

import java.util.function.BooleanSupplier;

/** Replacing or wrapping Minecraft's own HUD parts (26.x: HudElement.extractRenderState). Same methods in src/v1_21. */
public final class HudHooks {
    private HudHooks() {}

    /** Draws {@code draw} instead of Minecraft's part while {@code ours} is true. */
    public static void replace(Identifier vanilla, BooleanSupplier ours, Runnable draw) {
        HudElementRegistry.replaceElement(vanilla, old -> (g, delta) -> {
            if (ours.getAsBoolean()) {
                Gfx.set(g);
                draw.run();
            } else {
                old.extractRenderState(g, delta);
            }
        });
    }

    /** Runs {@code before} and {@code after} around Minecraft's part (for moving or scaling it). */
    public static void wrap(Identifier vanilla, Runnable before, Runnable after) {
        HudElementRegistry.replaceElement(vanilla, old -> (g, delta) -> {
            Gfx.set(g);
            before.run();
            try {
                old.extractRenderState(g, delta);
            } finally {
                Gfx.set(g);
                after.run();
            }
        });
    }

    /** A new HUD part drawn right after one of Minecraft's. */
    public static void after(Identifier vanilla, Identifier id, Runnable draw) {
        HudElementRegistry.attachElementAfter(vanilla, id, (g, delta) -> {
            Gfx.set(g);
            draw.run();
        });
    }
}
