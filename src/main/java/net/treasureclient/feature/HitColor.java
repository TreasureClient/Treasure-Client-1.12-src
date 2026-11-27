package net.treasureclient.feature;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.treasureclient.core.Config;
import net.treasureclient.core.Mods;

/**
 * Hit Color: the red flash on hurt players and mobs comes from the top half of a small 16 x 16 texture
 * (Minecraft's "overlay"). We repaint that half when the setting changes. Its alpha is how much of the
 * mob's own colour shows through, so Strength 30% = alpha 70% (what Minecraft uses).
 */
public final class HitColor {
    private HitColor() {}

    private static final int VANILLA = 0xB2FF0000;
    public static DynamicTexture texture;
    private static int applied = VANILLA;

    public static void tick() {
        if (texture == null) return;
        Config c = Config.get();
        int want = VANILLA;
        if (c.on("hitColorOn")) {
            int strength = Math.max(0, Math.min(100, c.num("hitAlpha", 30)));
            int alpha = Math.round(255 * (1 - strength / 100f));
            want = alpha << 24 | Mods.color("hitColor");
        }
        if (want == applied) return;
        NativeImage img = texture.getPixels();
        if (img == null) return;
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 16; x++) img.setPixel(x, y, want);
        texture.upload();
        applied = want;
    }
}
