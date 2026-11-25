package net.treasureclient.mixin.boost;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.network.PacketProcessor;
import net.treasureclient.boost.SmoothPackets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smooth Packets (the same in 1.21.11 and 26.x): processQueuedPackets is
 * "while (!closed && !queue.isEmpty()) queue.poll().handle();" — the queue reads as empty once
 * the frame's packet time is used up, so the rest wait (in order) for the next frame.
 */
@Mixin(PacketProcessor.class)
public class SmoothPacketsMixin {
    @Inject(method = "processQueuedPackets", at = @At("HEAD"))
    private void tc$begin(CallbackInfo ci) {
        SmoothPackets.begin();
    }

    @ModifyExpressionValue(method = "processQueuedPackets", at = @At(value = "INVOKE", target = "Ljava/util/Queue;isEmpty()Z"))
    private boolean tc$budget(boolean empty) {
        return SmoothPackets.stopHere(empty);
    }
}
