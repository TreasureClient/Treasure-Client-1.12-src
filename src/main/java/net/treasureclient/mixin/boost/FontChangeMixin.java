package net.treasureclient.mixin.boost;

import net.minecraft.client.gui.font.FontManager;
import net.treasureclient.boost.GlowText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Glowing Sign Text: fonts loaded again (resource packs) or font options changed (Force Unicode Font). */
@Mixin(FontManager.class)
public class FontChangeMixin {
    @Inject(method = {"apply", "updateOptions", "close"}, at = @At("HEAD"))
    private void tc$before(CallbackInfo ci) {
        GlowText.fontsChanged();
    }

    @Inject(method = {"apply", "updateOptions"}, at = @At("TAIL"))
    private void tc$after(CallbackInfo ci) {
        GlowText.fontsChanged();
    }
}
