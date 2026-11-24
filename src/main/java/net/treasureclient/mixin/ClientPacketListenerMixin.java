package net.treasureclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.treasureclient.hud.Trackers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reach Display + Combo Counter: a hit counts once the server says the target got hurt. */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void treasureclient$damage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (packet.entityId() == player.getId()) Trackers.selfHurt();
        else Trackers.entityHurt(packet.entityId());
    }
}
