package net.treasureclient.mixin.boost;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.treasureclient.boost.HudLists;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tab List Cache: players joined, left or changed (name, game mode, ping, list order...). */
@Mixin(ClientPacketListener.class)
public class PlayerListChangeMixin {
    @Inject(method = {"handlePlayerInfoUpdate", "handlePlayerInfoRemove"}, at = @At("TAIL"))
    private void tc$changed(CallbackInfo ci) {
        HudLists.playersChanged();
    }
}
