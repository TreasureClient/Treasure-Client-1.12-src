package net.treasureclient.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Setting a key binding to Backspace unbinds it (like Escape), in Controls and in any mod's key
 * screen. A binding whose own default is Backspace can still be put back on it.
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
}
