package net.treasureclient.mixin;

import net.minecraft.client.gui.GuiGraphics;
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

    @Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
    private void treasureclient$panorama(GuiGraphics g, float delta, CallbackInfo ci) {
        Gfx.set(g);
        MenuBackground.draw(width, height);
        ci.cancel();
    }

    @Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void treasureclient$noBlur(GuiGraphics g, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true)
    private void treasureclient$menuBackground(GuiGraphics g, CallbackInfo ci) {
        if (!MenuBackground.noWorld()) {
            Gfx.set(g);
            MenuBackground.drawInWorld(width, height);
        }
        ci.cancel();
    }
}
