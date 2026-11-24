package net.treasureclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.treasureclient.core.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Hurt Cam: skip the camera tilt when you take damage. */
@Mixin(GameRenderer.class)
public class HurtCamMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void treasureclient$noHurtCam(PoseStack pose, float partialTick, CallbackInfo ci) {
        if (Config.get().on("noHurtCam")) ci.cancel();
    }
}
