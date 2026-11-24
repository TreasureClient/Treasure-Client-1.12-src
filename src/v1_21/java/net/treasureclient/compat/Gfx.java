package net.treasureclient.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/** Minecraft 1.21.11 drawing (GuiGraphics). The same methods exist in src/v26 for 26.x. */
public final class Gfx {
    private Gfx() {}

    private static GuiGraphics g;

    public static void set(GuiGraphics graphics) {
        g = graphics;
    }

    public static Matrix3x2fStack pose() {
        return g.pose();
    }

    public static void fill(int x0, int y0, int x1, int y1, int argb) {
        g.fill(x0, y0, x1, y1, argb);
    }

    public static void gradient(int x0, int y0, int x1, int y1, int top, int bottom) {
        g.fillGradient(x0, y0, x1, y1, top, bottom);
    }

    public static void blit(Identifier tex, int x, int y, float u, float v, int w, int h, int uw, int vh, int texW, int texH, int argb) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, uw, vh, texW, texH, argb);
    }

    public static void text(Component c, int x, int y, int argb) {
        g.drawString(Minecraft.getInstance().font, c, x, y, argb, false);
    }

    public static void scissor(int x0, int y0, int x1, int y1) {
        g.enableScissor(x0, y0, x1, y1);
    }

    public static void unscissor() {
        g.disableScissor();
    }
}
