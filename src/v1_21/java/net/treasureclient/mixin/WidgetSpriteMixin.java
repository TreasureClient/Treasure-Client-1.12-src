package net.treasureclient.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.treasureclient.compat.Gfx;
import net.treasureclient.ui.Widgets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every widget frame (button, slider, text box, checkbox, tab, scrollbar) is drawn in the Treasure Client style. */
@Mixin(GuiGraphics.class)
public class WidgetSpriteMixin {
    private boolean tc$themed(Identifier id, int x, int y, int w, int h) {
        Gfx.set((GuiGraphics) (Object) this);
        return Widgets.sprite(id, x, y, w, h);
    }

    @Inject(method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", at = @At("HEAD"), cancellable = true)
    private void treasureclient$sprite(RenderPipeline pipe, Identifier id, int x, int y, int w, int h, CallbackInfo ci) {
        if (tc$themed(id, x, y, w, h)) ci.cancel();
    }

    @Inject(method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V", at = @At("HEAD"), cancellable = true)
    private void treasureclient$spriteColor(RenderPipeline pipe, Identifier id, int x, int y, int w, int h, int color, CallbackInfo ci) {
        if (tc$themed(id, x, y, w, h)) ci.cancel();
    }

    @Inject(method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIF)V", at = @At("HEAD"), cancellable = true)
    private void treasureclient$spriteAlpha(RenderPipeline pipe, Identifier id, int x, int y, int w, int h, float alpha, CallbackInfo ci) {
        if (tc$themed(id, x, y, w, h)) ci.cancel();
    }
}
