package net.treasureclient.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.treasureclient.feature.TimeChanger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time & Weather (1.21.11): the time of day your world reports. */
@Mixin(ClientLevel.ClientLevelData.class)
public class TimeMixin {
    @Inject(method = "getDayTime", at = @At("RETURN"), cancellable = true)
    private void treasureclient$time(CallbackInfoReturnable<Long> cir) {
        long t = cir.getReturnValueJ();
        long v = TimeChanger.time(t);
        if (v != t) cir.setReturnValue(v);
    }
}
