package net.treasureclient.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.treasureclient.feature.ChatTweaks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chat Tweaks: a longer history (times and stacked repeats are in ChatAddMixin, per version). */
@Mixin(ChatComponent.class)
public class ChatTweaksMixin {
    @ModifyConstant(method = {"addMessageToQueue", "addMessageToDisplayQueue"}, constant = @Constant(intValue = 100))
    private int treasureclient$history(int max) {
        return ChatTweaks.history(max);
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void treasureclient$cleared(boolean history, CallbackInfo ci) {
        ChatTweaks.cleared();
    }
}
