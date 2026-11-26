package net.treasureclient.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two small key binding fixes.
 * <p>Setting a key binding to Backspace unbinds it (like Escape), in Controls and in any mod's key
 * screen. A binding whose own default is Backspace can still be put back on it.
 * <p>An F3 debug key (F3+A reload chunks, F3+S, F3+D, F3+1/2/3 charts...) only works while F3 is
 * held, so it doesn't clash with a normal key on the same letter, like A for walking left or 1 for
 * the first hotbar slot. Minecraft (and Controlling, which shows clashes even between two default
 * keys) marked those in red; now they aren't counted as a clash. Only the Controls screens ask this.
 */
@Mixin(KeyMapping.class)
public abstract class KeyBackspaceMixin {
    private static InputConstants.Key tc$backspace;

    @ModifyVariable(method = "setKey", at = @At("HEAD"), argsOnly = true)
    private InputConstants.Key tc$unbindOnBackspace(InputConstants.Key key) {
        if (tc$backspace == null) tc$backspace = InputConstants.getKey("key.keyboard.backspace");
        if (key != null && key.equals(tc$backspace) && !key.equals(((KeyMapping) (Object) this).getDefaultKey()))
            return InputConstants.UNKNOWN;
        return key;
    }

    @Inject(method = "same", at = @At("HEAD"), cancellable = true)
    private void tc$debugIsNotAClash(KeyMapping other, CallbackInfoReturnable<Boolean> cir) {
        if (other != null && tc$withF3((KeyMapping) (Object) this) != tc$withF3(other)) cir.setReturnValue(false);
    }

    /** A debug key that only works while F3 is held (F3 itself and the F3 overlay key are real keys). */
    private static boolean tc$withF3(KeyMapping k) {
        if (k.getCategory() != KeyMapping.Category.DEBUG) return false;
        String n = k.getName();
        return !n.equals("key.debug.modifier") && !n.equals("key.debug.overlay");
    }
}
