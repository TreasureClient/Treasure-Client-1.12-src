package net.treasureclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.treasureclient.ui.TitleScreenTC;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Shows the Treasure Client main menu wherever Minecraft would show its title screen. */
@Mixin(Minecraft.class)
public class TitleHookMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen treasureclient$title(Screen screen) {
        return TitleScreenTC.swap(screen);
    }
}
