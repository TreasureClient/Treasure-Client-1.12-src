package net.treasureclient.mixin.boost;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.treasureclient.boost.NameTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Name Tag Cache, the width (1.21.11 and 26.1: measured when a tag is queued, in NameTagFeatureRenderer.Storage.add). */
@Mixin(targets = "net.minecraft.client.renderer.feature.NameTagFeatureRenderer$Storage")
public class NameTagWidthMixin {
    @WrapOperation(method = "add", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"))
    private int tc$width(Font font, FormattedText text, Operation<Integer> measure) {
        return NameTags.width(font, text, measure);
    }
}
