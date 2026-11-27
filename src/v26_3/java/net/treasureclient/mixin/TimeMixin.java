package net.treasureclient.mixin;

import net.minecraft.client.ClientClockManager;
import net.treasureclient.feature.TimeChanger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time & Weather (26.3): the time each world clock reports on your side (the sky reads these). */
@Mixin(ClientClockManager.ClientClockInstance.class)
public class TimeMixin {
    @Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
    private void treasureclient$time(CallbackInfoReturnable<Long> cir) {
        long t = cir.getReturnValueJ();
        long v = TimeChanger.time(t);
        if (v != t) cir.setReturnValue(v);
    }

    @Inject(method = "partialTick", at = @At("RETURN"), cancellable = true)
    private void treasureclient$partial(CallbackInfoReturnable<Float> cir) {
        if (TimeChanger.fixedTime()) cir.setReturnValue(0f);
    }
}
