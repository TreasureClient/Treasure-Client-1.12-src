package net.treasureclient.mixin.boost;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.util.FormattedCharSequence;
import net.treasureclient.boost.GlowText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Glowing Sign Text (26.2+): the outline is prepared (Font.prepare8xTextOutline) and then the text on
 * top (the second Font.prepareText in the same method). 26.2 does it in buildGroup, 26.3 in the
 * static renderText, so there's one pair of hooks for each.
 */
@Mixin(TextFeatureRenderer.class)
public class GlowTextMixin {
    private static final String OUTLINE = "Lnet/minecraft/client/gui/Font;prepare8xTextOutline(Lnet/minecraft/util/FormattedCharSequence;FFI)Lnet/minecraft/client/gui/Font$PreparedText;";
    private static final String TEXT = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;";

    // ---- 26.2: buildGroup
    @WrapOperation(method = "buildGroup", at = @At(value = "INVOKE", target = OUTLINE))
    private Font.PreparedText tc$outline(Font font, FormattedCharSequence seq, float x, float y, int outline, Operation<Font.PreparedText> make) {
        return GlowText.outline(font, seq, x, y, outline, n -> make.call(font, seq, x, y, outline));
    }

    @WrapOperation(method = "buildGroup", at = @At(value = "INVOKE", target = TEXT, ordinal = 1))
    private Font.PreparedText tc$inner(Font font, FormattedCharSequence seq, float x, float y, int color, boolean shadow, boolean b, int bg,
                                       Operation<Font.PreparedText> make) {
        return GlowText.inner(seq, x, y, color, n -> make.call(font, seq, x, y, color, shadow, b, bg));
    }

    // ---- 26.3: renderText (static)
    @WrapOperation(method = "renderText", at = @At(value = "INVOKE", target = OUTLINE))
    private static Font.PreparedText tc$outlineS(Font font, FormattedCharSequence seq, float x, float y, int outline, Operation<Font.PreparedText> make) {
        return GlowText.outline(font, seq, x, y, outline, n -> make.call(font, seq, x, y, outline));
    }

    @WrapOperation(method = "renderText", at = @At(value = "INVOKE", target = TEXT, ordinal = 1))
    private static Font.PreparedText tc$innerS(Font font, FormattedCharSequence seq, float x, float y, int color, boolean shadow, boolean b, int bg,
                                               Operation<Font.PreparedText> make) {
        return GlowText.inner(seq, x, y, color, n -> make.call(font, seq, x, y, color, shadow, b, bg));
    }
}
