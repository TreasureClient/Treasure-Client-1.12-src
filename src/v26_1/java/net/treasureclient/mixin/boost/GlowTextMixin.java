package net.treasureclient.mixin.boost;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import net.treasureclient.boost.GlowText;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Glowing Sign Text (v26_126.1: Font.drawInBatch8xOutline draws straight away). */
@Mixin(Font.class)
public abstract class GlowTextMixin {
    @Inject(method = "drawInBatch8xOutline", at = @At("HEAD"), cancellable = true)
    private void tc$replay(FormattedCharSequence seq, float x, float y, int color, int outline, Matrix4fc pose,
                           MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (GlowText.replay((Font) (Object) this, seq, x, y, color, outline,
            mode -> Font.GlyphVisitor.forMultiBufferSource(buffers, pose, mode, light))) ci.cancel();
    }

    @WrapOperation(method = "drawInBatch8xOutline", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/Font$GlyphVisitor;forMultiBufferSource(Lnet/minecraft/client/renderer/MultiBufferSource;Lorg/joml/Matrix4fc;Lnet/minecraft/client/gui/Font$DisplayMode;I)Lnet/minecraft/client/gui/Font$GlyphVisitor;"))
    private Font.GlyphVisitor tc$record(MultiBufferSource buffers, Matrix4fc pose, Font.DisplayMode mode, int light,
                                        Operation<Font.GlyphVisitor> make) {
        return GlowText.record(make.call(buffers, pose, mode, light));
    }

    @Inject(method = "drawInBatch8xOutline", at = @At("RETURN"))
    private void tc$done(CallbackInfo ci) {
        GlowText.recorded();
    }
}
