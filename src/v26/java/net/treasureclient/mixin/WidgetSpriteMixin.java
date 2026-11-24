package net.treasureclient.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.treasureclient.compat.Gfx;
import net.treasureclient.ui.Widgets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every widget frame (button, slider, text box, checkbox, tab, scrollbar) is drawn in the Treasure Client style.
 * Matched by pattern: the pipeline class moved package in 26.3, so it isn't named here.
 */
@Mixin(GuiGraphicsExtractor.class)
public class WidgetSpriteMixin {
    @Inject(method = "name=/^blitSprite$/ desc=/^\\(L[^;]+;Lnet\\/minecraft\\/resources\\/Identifier;IIII[IF]?\\)V$/", at = @At("HEAD"), cancellable = true)
    private void treasureclient$sprite(CallbackInfo ci, @Local(argsOnly = true) Identifier id,
                                       @Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) int y,
                                       @Local(argsOnly = true, ordinal = 2) int w, @Local(argsOnly = true, ordinal = 3) int h) {
        Gfx.set((GuiGraphicsExtractor) (Object) this);
        if (Widgets.sprite(id, x, y, w, h)) ci.cancel();
    }
}
