package net.treasureclient.mixin.boost;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.network.chat.FormattedText;
import net.treasureclient.boost.NameTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Name Tag Cache, the width (26.2+: measured in SubmitNodeCollection.submitNameTag). */
@Mixin(SubmitNodeCollection.class)
public class NameTagWidthMixin {
    @WrapOperation(method = "submitNameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"))
    private int tc$width(Font font, FormattedText text, Operation<Integer> measure) {
        return NameTags.width(font, text, measure);
    }
}
