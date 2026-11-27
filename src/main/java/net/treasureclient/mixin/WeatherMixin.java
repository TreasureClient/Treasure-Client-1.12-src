package net.treasureclient.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.treasureclient.feature.TimeChanger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time & Weather: the rain and thunder you see (your world only; singleplayer's server side is left alone). */
@Mixin(Level.class)
public class WeatherMixin {
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void treasureclient$rain(float partial, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientLevel)) return;
        float r = TimeChanger.rain();
        if (r >= 0) cir.setReturnValue(r);
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void treasureclient$thunder(float partial, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientLevel)) return;
        float t = TimeChanger.thunder();
        if (t >= 0) cir.setReturnValue(t);
    }
}
