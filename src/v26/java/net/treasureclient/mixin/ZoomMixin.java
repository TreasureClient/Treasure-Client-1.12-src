package net.treasureclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Camera;
import net.treasureclient.feature.Zoom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Zoom: narrows the field of view while the Zoom key is held. */
@Mixin(Camera.class)
public class ZoomMixin {
    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float treasureclient$zoom(float fov) {
        return Zoom.apply(fov);
    }
}
