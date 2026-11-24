package net.treasureclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.treasureclient.core.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Hurt Cam: skip the camera tilt when you take damage. */
@Mixin(GameRenderer.class)
public class HurtCamMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void treasureclient$noHurtCam(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
        if (Config.get().on("noHurtCam")) ci.cancel();
    }
}
