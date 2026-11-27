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

/** Chat Tweaks: the time next to each message, and repeated messages stacked (1.21.11: the 3-part addMessage, which the 1-part one also goes through). */
@Mixin(ChatComponent.class)
public class ChatAddMixin {
    @Shadow @Final private List<?> allMessages;

    @Shadow
    private void refreshTrimmedMessages() {}

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true)
    private Component treasureclient$message(Component c) {
        return ChatTweaks.incoming(c, allMessages, this::refreshTrimmedMessages);
    }
}
