package net.treasureclient.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.treasureclient.ui.Widgets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the widget styling which widget is drawing (for its own hover animation). */
@Mixin(AbstractWidget.class)
public class WidgetScopeMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void treasureclient$begin(GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
        Widgets.begin(this);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void treasureclient$end(GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
        Widgets.end();
    }
}
