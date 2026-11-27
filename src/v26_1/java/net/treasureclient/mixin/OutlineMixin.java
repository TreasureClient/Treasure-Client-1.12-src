package net.treasureclient.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.treasureclient.feature.Outline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Block Outline: the colour and thickness passed to renderHitOutline (26.1, 26.1.2). */
@Mixin(LevelRenderer.class)
public class OutlineMixin {
    @ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true)
    private int treasureclient$color(int color) {
        return Outline.color(color);
    }

    @ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true)
    private float treasureclient$width(float width) {
        return Outline.width(width);
    }
}
