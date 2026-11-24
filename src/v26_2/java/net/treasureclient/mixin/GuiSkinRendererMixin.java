package net.treasureclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.render.pip.GuiSkinRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.GuiSkinRenderState;
import net.treasureclient.cos.TCEmit;
import net.treasureclient.cos.TCPreview;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Treasure Client cosmetics: your cape, hat, boots and aura on the menus' player picture (26.2 and 26.3:
 * the model draw has one more argument in 26.2, so the call is matched by name; it is the only one there).
 */
@Mixin(GuiSkinRenderer.class)
public class GuiSkinRendererMixin {
    @Inject(method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiSkinRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel", shift = At.Shift.AFTER))
    private void tc$cosmetics(GuiSkinRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
        TCPreview.draw(TCEmit.of(collector), poseStack, state.playerModel().root(), state.rotationY());
    }
}
