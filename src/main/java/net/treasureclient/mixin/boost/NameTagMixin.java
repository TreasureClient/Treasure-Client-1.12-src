package net.treasureclient.mixin.boost;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.treasureclient.boost.NameTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Name Tag Cache (the same in 1.21.11 and 26.x): getNameTag's getDisplayName() gives back the kept name. */
@Mixin(EntityRenderer.class)
public class NameTagMixin {
    @WrapOperation(method = "getNameTag", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;getDisplayName()Lnet/minecraft/network/chat/Component;"))
    private Component tc$name(Entity entity, Operation<Component> made) {
        return NameTags.name(entity, made);
    }
}
