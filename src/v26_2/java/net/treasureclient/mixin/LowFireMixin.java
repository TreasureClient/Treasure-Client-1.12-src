package net.treasureclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.treasureclient.core.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low Fire: the burning overlay is moved down so it covers less of the view. */
@Mixin(ScreenEffectRenderer.class)
public class LowFireMixin {
    @Inject(method = "submitFire", at = @At("HEAD"))
    private static void treasureclient$lowFireStart(PoseStack pose, SubmitNodeCollector buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
        pose.pushPose();
        if (Config.get().on("lowFire")) {
            float keep = Config.get().num("lowFireH", 40) / 100f;
            pose.translate(0, -(1f - keep) * 0.5f, 0);
        }
    }

    @Inject(method = "submitFire", at = @At("RETURN"))
    private static void treasureclient$lowFireEnd(PoseStack pose, SubmitNodeCollector buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
        pose.popPose();
    }
}
