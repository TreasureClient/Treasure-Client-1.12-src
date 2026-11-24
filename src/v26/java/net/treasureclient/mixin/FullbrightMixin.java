package net.treasureclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.treasureclient.core.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Fullbright: the lightmap reads a very high brightness instead of the Brightness setting. */
@Mixin(LightmapRenderStateExtractor.class)
public class FullbrightMixin {
    // the second settings read in extract() is options.gamma() (after hideLightningFlash)
    @ModifyExpressionValue(method = "extract", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 1))
    private Object treasureclient$fullbright(Object gamma) {
        return Config.get().on("fullbright") ? (Object) Double.valueOf(16.0) : gamma;
    }
}
