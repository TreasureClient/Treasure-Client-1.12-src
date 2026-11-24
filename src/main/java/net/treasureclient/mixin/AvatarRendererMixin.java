package net.treasureclient.mixin;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.treasureclient.cos.TCCosLayer;
import net.treasureclient.cos.TCCosRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Treasure Client cosmetics: adds the hats/boots/auras layer to the player renderer, and finds each
 * player's look as they're about to be drawn (swapping in an animated cape).
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
    @SuppressWarnings("unchecked")
    @Inject(method = "<init>", at = @At("RETURN"))
    private void tc$addLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) this;
        ((LivingRendererLayersAccessor) (Object) this).tc$layers().add(new TCCosLayer(parent));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void tc$extract(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        TCCosRender.extract(entity, state);
    }
}
