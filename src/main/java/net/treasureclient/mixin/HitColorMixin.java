package net.treasureclient.mixin;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.treasureclient.feature.HitColor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hit Color: remembers Minecraft's hurt-flash texture so HitColor can repaint it. */
@Mixin(OverlayTexture.class)
public class HitColorMixin {
    @Shadow @Final private DynamicTexture texture;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void treasureclient$made(CallbackInfo ci) {
        HitColor.texture = texture;
    }
}
