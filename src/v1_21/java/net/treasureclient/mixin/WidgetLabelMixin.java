package net.treasureclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.treasureclient.ui.Widgets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Widget labels: light text on a hollow button, dark text (no shadow) once it fills on hover. */
@Mixin(targets = "net.minecraft.client.gui.GuiGraphics$RenderingTextCollector")
public class WidgetLabelMixin {
    private static final String ACCEPT = "accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/client/gui/ActiveTextCollector$Parameters;Lnet/minecraft/util/FormattedCharSequence;)V";

    @ModifyExpressionValue(method = ACCEPT, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;white(F)I"))
    private int treasureclient$color(int original) {
        return Widgets.label(original);
    }

    @ModifyArg(method = ACCEPT, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/state/GuiTextRenderState;<init>(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;Lorg/joml/Matrix3x2fc;IIIIZZLnet/minecraft/client/gui/navigation/ScreenRectangle;)V"), index = 7)
    private boolean treasureclient$shadow(boolean original) {
        return Widgets.labelShadow(original);
    }
}
