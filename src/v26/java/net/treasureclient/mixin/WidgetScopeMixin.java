package net.treasureclient.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.treasureclient.ui.Widgets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the widget styling which widget is drawing (for its own hover animation). */
@Mixin(AbstractWidget.class)
public class WidgetScopeMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void treasureclient$begin(GuiGraphicsExtractor g, int mx, int my, float delta, CallbackInfo ci) {
        Widgets.begin(this);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void treasureclient$end(GuiGraphicsExtractor g, int mx, int my, float delta, CallbackInfo ci) {
        Widgets.end();
    }
}
