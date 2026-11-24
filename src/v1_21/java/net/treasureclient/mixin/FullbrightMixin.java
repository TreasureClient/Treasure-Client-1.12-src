package net.treasureclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LightTexture;
import net.treasureclient.core.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Fullbright: the lightmap reads a very high brightness instead of the Brightness setting. */
@Mixin(LightTexture.class)
public class FullbrightMixin {
    // the third settings read in updateLightTexture is options.gamma()
    @ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 2))
    private Object treasureclient$fullbright(Object gamma) {
        return Config.get().on("fullbright") ? (Object) Double.valueOf(16.0) : gamma;
    }
}
