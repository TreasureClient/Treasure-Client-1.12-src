package net.treasureclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.render.pip.GuiSkinRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.GuiSkinRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.treasureclient.cos.TCPreview;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Treasure Client cosmetics: your cape, hat, boots and aura on the menus' player picture (1.21.11 draws it straight away). */
@Mixin(GuiSkinRenderer.class)
public abstract class GuiSkinRendererMixin extends PictureInPictureRenderer<GuiSkinRenderState> {
    protected GuiSkinRendererMixin(MultiBufferSource.BufferSource buffers) {
        super(buffers);
    }

    @Inject(method = "renderToTexture(Lnet/minecraft/client/gui/render/state/pip/GuiSkinRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/player/PlayerModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V", shift = At.Shift.AFTER))
    private void tc$cosmetics(GuiSkinRenderState state, PoseStack poseStack, CallbackInfo ci) {
        MultiBufferSource.BufferSource buffers = this.bufferSource;
        TCPreview.draw((ps, type, geo) -> geo.render(ps.last(), buffers.getBuffer(type)), poseStack, state.playerModel().root(), state.rotationY());
    }
}
