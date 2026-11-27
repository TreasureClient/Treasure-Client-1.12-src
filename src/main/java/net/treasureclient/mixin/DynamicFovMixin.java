package net.treasureclient.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.treasureclient.core.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dynamic FOV off: sprinting, flying, Speed and bows no longer zoom the view. */
@Mixin(AbstractClientPlayer.class)
public class DynamicFovMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("HEAD"), cancellable = true)
    private void treasureclient$fov(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
        if (!Config.get().on("dynfov")) cir.setReturnValue(1f);
    }
}
