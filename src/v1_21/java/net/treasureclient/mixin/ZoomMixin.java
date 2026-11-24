package net.treasureclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.GameRenderer;
import net.treasureclient.feature.Zoom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Zoom: narrows the field of view while the Zoom key is held. */
@Mixin(GameRenderer.class)
public class ZoomMixin {
    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private float treasureclient$zoom(float fov) {
        return Zoom.apply(fov);
    }
}
