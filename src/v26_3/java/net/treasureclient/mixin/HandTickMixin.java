package net.treasureclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.LocalPlayer;
import net.treasureclient.feature.OldAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 1.7 Animations: the item only dips a little after a hit, instead of the attack-cooldown dip. */
@Mixin(net.minecraft.client.player.FirstPersonHandsAndItems.class)
public class HandTickMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"))
    private float treasureclient$dip(LocalPlayer player, float partial, Operation<Float> original) {
        return OldAnim.dip(original.call(player, partial));
    }
}
