package net.treasureclient.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.treasureclient.ui.ShotToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.io.File;
import java.util.function.Consumer;

/** Every screenshot (F2) also shows Treasure Client's pop-up, not just the chat line. */
@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {
    @ModifyVariable(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true)
    private static Consumer<Component> tc$toast(Consumer<Component> chat) {
        return msg -> {
            try {
                ShotToast.saved(msg);
            } catch (Throwable ignored) {
            }
            if (chat != null) chat.accept(msg);
        };
    }
}
