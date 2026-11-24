package net.treasureclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.treasureclient.hud.Trackers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CPS Counter: counts mouse presses while playing (not in menus). */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void treasureclient$cps(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action == 1 && net.treasureclient.compat.V.screen() == null && (info.button() == 0 || info.button() == 1)) {
            Trackers.click(info.button() == 0);
        }
    }
}
