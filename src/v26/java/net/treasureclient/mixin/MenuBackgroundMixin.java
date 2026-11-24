package net.treasureclient.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.treasureclient.compat.Gfx;
import net.treasureclient.ui.MenuBackground;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Menus show the theme backdrop instead of the panorama and blur (a dark shade when in a world). */
@Mixin(Screen.class)
public class MenuBackgroundMixin {
    @Shadow public int width;
    @Shadow public int height;

    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
    private void treasureclient$panorama(GuiGraphicsExtractor g, float delta, CallbackInfo ci) {
        Gfx.set(g);
        MenuBackground.draw(width, height);
        ci.cancel();
    }

    @Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void treasureclient$noBlur(GuiGraphicsExtractor g, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"), cancellable = true)
    private void treasureclient$menuBackground(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (!MenuBackground.noWorld()) {
            Gfx.set(g);
            MenuBackground.drawInWorld(width, height);
        }
        ci.cancel();
    }
}
