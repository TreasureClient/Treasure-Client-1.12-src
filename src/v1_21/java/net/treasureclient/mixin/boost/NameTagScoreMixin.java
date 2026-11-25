package net.treasureclient.mixin.boost;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.treasureclient.boost.NameTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Name Tag Cache, the score under players' names (1.21.11: AvatarRenderer.extractRenderState). */
@Mixin(AvatarRenderer.class)
public class NameTagScoreMixin {
    @WrapOperation(method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/ClientAvatarEntity;belowNameDisplay()Lnet/minecraft/network/chat/Component;"))
    private Component tc$score(ClientAvatarEntity avatar, Operation<Component> made) {
        return avatar instanceof Entity e ? NameTags.score(e, made) : made.call(avatar);
    }
}
