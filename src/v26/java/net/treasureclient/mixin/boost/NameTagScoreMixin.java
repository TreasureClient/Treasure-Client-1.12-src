package net.treasureclient.mixin.boost;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.treasureclient.boost.NameTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Name Tag Cache, the score under names (26.1: in extractRenderState; 26.2+: in extractNameTags). */
@Mixin(EntityRenderer.class)
public class NameTagScoreMixin {
    @WrapOperation(method = {"extractNameTags", "extractRenderState"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;belowNameDisplay()Lnet/minecraft/network/chat/Component;"))
    private Component tc$score(Entity entity, Operation<Component> made) {
        return NameTags.score(entity, made);
    }
}
