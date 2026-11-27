package net.treasureclient.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.treasureclient.compat.Gfx;
import net.treasureclient.compat.V;
import net.treasureclient.core.Config;

/** Saturation Display: gold outlines on the food bar for the saturation you can't normally see. */
public final class Saturation {
    private Saturation() {}

    public static void draw() {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || V.hudHidden() || !Config.get().on("satDisplay")) return;
        if (mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return; // no food bar in creative/spectator
        if (p.getVehicle() instanceof LivingEntity) return; // the mount's hearts take the food bar's place
        float sat = p.getFoodData().getSaturationLevel();
        if (sat <= 0) return;
        var win = mc.getWindow();
        int right = win.getGuiScaledWidth() / 2 + 91, y = win.getGuiScaledHeight() - 39;
        for (int i = 0; i < 10; i++) {
            float v = sat - i * 2;
            if (v <= 0) break;
            int x = right - i * 8 - 9;
            int col = v >= 2 ? 0xFFFFC42E : 0x99FFC42E; // full or half a drumstick's worth
            Gfx.fill(x, y, x + 9, y + 1, col);
            Gfx.fill(x, y + 8, x + 9, y + 9, col);
            Gfx.fill(x, y + 1, x + 1, y + 8, col);
            Gfx.fill(x + 8, y + 1, x + 9, y + 8, col);
        }
    }
}
