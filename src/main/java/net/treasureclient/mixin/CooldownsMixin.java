package net.treasureclient.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import net.treasureclient.feature.Cooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cooldowns: notes when one of your item cooldowns starts or ends. */
@Mixin(ItemCooldowns.class)
public class CooldownsMixin {
    @Inject(method = "addCooldown(Lnet/minecraft/resources/Identifier;I)V", at = @At("HEAD"))
    private void treasureclient$start(Identifier group, int ticks, CallbackInfo ci) {
        Cooldowns.started((ItemCooldowns) (Object) this, group, ticks);
    }

    @Inject(method = "removeCooldown", at = @At("HEAD"))
    private void treasureclient$end(Identifier group, CallbackInfo ci) {
        Cooldowns.removed((ItemCooldowns) (Object) this, group);
    }
}
