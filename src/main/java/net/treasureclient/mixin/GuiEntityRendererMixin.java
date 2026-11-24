package net.treasureclient.mixin;

import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.treasureclient.cos.TCCosRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Treasure Client cosmetics: marks when the inventory's player picture is drawn (no auras there). */
@Mixin(GuiEntityRenderer.class)
public class GuiEntityRendererMixin {
    @Inject(method = "renderToTexture", at = @At("HEAD"))
    private void tc$enter(CallbackInfo ci) {
        TCCosRender.enterGui();
    }

    @Inject(method = "renderToTexture", at = @At("RETURN"))
    private void tc$leave(CallbackInfo ci) {
        TCCosRender.leaveGui();
    }
}
