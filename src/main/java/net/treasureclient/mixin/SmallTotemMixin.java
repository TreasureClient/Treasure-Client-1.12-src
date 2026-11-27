package net.treasureclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.treasureclient.core.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Small Totem: the totem that pops up on your screen is drawn smaller. */
@Mixin(ScreenEffectRenderer.class)
public class SmallTotemMixin {
    @WrapOperation(method = "renderItemActivationAnimation", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private void treasureclient$scale(PoseStack pose, float x, float y, float z, Operation<Void> scale) {
        Config c = Config.get();
        float s = c.on("smallTotem") ? Math.max(10, Math.min(100, c.num("totemScale", 40))) / 100f : 1f;
        scale.call(pose, x * s, y * s, z * s);
    }
}
