package net.treasureclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.treasureclient.feature.AutoGG;
import net.treasureclient.feature.Crosshair;
import net.treasureclient.hud.Trackers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reach Display + Combo Counter: a hit counts once the server says the target got hurt.
 * Crosshair Addons: the arrow-hit "ding". Auto GG: big titles like VICTORY.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void treasureclient$damage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (packet.entityId() == player.getId()) Trackers.selfHurt();
        else Trackers.entityHurt(packet.entityId());
    }

    @Inject(method = "handleGameEvent", at = @At("TAIL"))
    private void treasureclient$game(ClientboundGameEventPacket packet, CallbackInfo ci) {
        if (packet.getEvent() == ClientboundGameEventPacket.PLAY_ARROW_HIT_SOUND) Crosshair.arrowHit();
    }

    @Inject(method = "setTitleText", at = @At("TAIL"))
    private void treasureclient$title(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
        if (packet.text() != null) AutoGG.onTitle(packet.text().getString());
    }
}
