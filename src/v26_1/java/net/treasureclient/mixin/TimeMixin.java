package net.treasureclient.mixin;

import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.treasureclient.feature.TimeChanger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time & Weather (26.1 - 26.2): the time the world clocks report on your side (the sky reads these). */
@Mixin(ClientClockManager.class)
public class TimeMixin {
    @Inject(method = "getTotalTicks", at = @At("RETURN"), cancellable = true)
    private void treasureclient$time(Holder<WorldClock> clock, CallbackInfoReturnable<Long> cir) {
        long t = cir.getReturnValueJ();
        long v = TimeChanger.time(t);
        if (v != t) cir.setReturnValue(v);
    }
}
