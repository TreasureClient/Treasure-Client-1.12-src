package net.treasureclient.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.treasureclient.compat.Gfx;
import net.treasureclient.core.Theme;

/**
 * What every Minecraft menu shows behind it (the browser client's menu backdrop): the theme's picture
 * (sharp, or blurred if the theme's "Blur background" is on), slowly panning, a soft darken toward the bottom and the theme's floating particles.
 * In a world, menus get a plain dark shade over the game instead of Minecraft's blur.
 */
public final class MenuBackground {
    private MenuBackground() {}


    /** Behind menus on the main menu side (no world loaded). Sizes are Minecraft GUI units. */
    public static void draw(int width, int height) {
        Theme th = Theme.current();
        boolean blur = Backgrounds.blur(th);
        Identifier tex = blur ? Backgrounds.blurred(Backgrounds.of(th)) : Backgrounds.sharp(Backgrounds.of(th));
        int tw = blur ? 512 : 1024, tht = blur ? 256 : 512;
        float scale = Math.max(width / (float) tw, height / (float) tht) / 0.82f; // slight zoom so the pan never shows an edge
        float uw = width / scale, vh = height / scale;
        double t = System.currentTimeMillis();
        float panX = (float) (Math.sin(t / 9000.0) * 0.5 + 0.5) * (tw - uw);
        float panY = (tht - vh) / 2f;
        Gfx.blit(tex, 0, 0, panX, panY, width, height, Math.round(uw), Math.round(vh), tw, tht, 0xFFFFFFFF);
        Gfx.gradient(0, 0, width, height, 0x14000000, 0x5C000000);
        particles(th, width, height);
    }

    /** In a world: the classic dark shade over the game. */
    public static void drawInWorld(int width, int height) {
        Gfx.gradient(0, 0, width, height, 0xC0101010, 0xD0101010);
    }

    private static void particles(Theme th, int width, int height) {
        String id = th.id();
        boolean frost = id.equals("frost"), tropical = id.equals("tropical"), terracotta = id.equals("terracotta"), forest = id.equals("forest");
        boolean sakura = id.equals("sakura"), dark = id.equals("dark");
        int col = frost ? 0x99EAF6FF : tropical ? 0x9945E3C6 : terracotta ? 0x99E89A5A : forest ? 0x99C8E88A
            : sakura ? 0x99FFB7D5 : dark ? 0xAAFFD98A : 0x99F2C64B;
        long tt = System.currentTimeMillis();
        float gust = frost ? (float) (Math.sin(tt / 7000.0) * 0.5 + Math.sin(tt / 2300.0) * 0.5) * 34f : 0f;
        int count = frost ? 50 : 30;
        for (int i = 0; i < count; i++) {
            float sx = ((i * 73 + 11) % 100) / 100f;
            float speed = 0.25f + (((i * 37 + 7) % 100) / 100f) * 0.55f;
            int size = frost ? 1 + ((i * 29 + 5) % 2) : 1 + ((i * 53 + 3) % 2);
            float sway = (float) Math.sin(tt / 1600.0 + i) * (frost ? 7f : 12f);
            float lane = 0.4f + ((i * 17 + 9) % 100) / 100f;
            int x = (int) (sx * width + sway + (frost ? gust * lane : 0f));
            if (frost) {
                int w = width + 32;
                x = (((x + 16) % w) + w) % w - 16;
            }
            float phase = (float) (((tt / 2200.0) * speed + i * 0.137f) % 1.0);
            int y = (!frost && (tropical || forest || dark)) ? (int) (height - phase * height) : (int) (phase * height);
            Gfx.fill(x, y, x + size, y + size, col);
        }
    }

    /** No world loaded (main menu side). */
    public static boolean noWorld() {
        return Minecraft.getInstance().level == null;
    }
}
