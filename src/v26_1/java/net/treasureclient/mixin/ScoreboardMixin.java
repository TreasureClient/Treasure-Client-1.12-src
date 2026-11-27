package net.treasureclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.Objective;
import net.treasureclient.feature.Sidebar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Scoreboard: the sidebar's number format (hide the red numbers) and background colour. */
@Mixin(Gui.class)
public class ScoreboardMixin {
    @WrapOperation(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/scores/Objective;numberFormatOrDefault(Lnet/minecraft/network/chat/numbers/NumberFormat;)Lnet/minecraft/network/chat/numbers/NumberFormat;"))
    private NumberFormat treasureclient$numbers(Objective objective, NumberFormat def, Operation<NumberFormat> original) {
        return Sidebar.numbers(original.call(objective, def));
    }

    @WrapOperation(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Options;getBackgroundColor(F)I"))
    private int treasureclient$background(Options options, float opacity, Operation<Integer> original) {
        return Sidebar.background(original.call(options, opacity));
    }
}
