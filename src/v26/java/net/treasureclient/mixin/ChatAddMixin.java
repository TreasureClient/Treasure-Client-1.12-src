package net.treasureclient.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.treasureclient.feature.ChatTweaks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/** Chat Tweaks: the time next to each message, and repeated messages stacked (26.x: there is only one addMessage). */
@Mixin(ChatComponent.class)
public class ChatAddMixin {
    @Shadow @Final private List<?> allMessages;

    @Shadow
    private void refreshTrimmedMessages() {}

    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true)
    private Component treasureclient$message(Component c) {
        return ChatTweaks.incoming(c, allMessages, this::refreshTrimmedMessages);
    }
}
