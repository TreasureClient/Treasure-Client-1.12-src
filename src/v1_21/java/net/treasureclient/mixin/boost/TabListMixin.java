package net.treasureclient.mixin.boost;

import java.util.List;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.treasureclient.boost.HudLists;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Tab List Cache (render): the sorted players, their names and widths, the header and footer lines. */
@Mixin(PlayerTabOverlay.class)
public class TabListMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;getPlayerInfos()Ljava/util/List;"))
    private List<PlayerInfo> tc$players(PlayerTabOverlay tab, Operation<List<PlayerInfo>> sort) {
        return HudLists.tabPlayers(tab, sort);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;"))
    private Component tc$name(PlayerTabOverlay tab, PlayerInfo info, Operation<Component> make) {
        return HudLists.tabName(tab, info, make);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"))
    private int tc$width(Font font, FormattedText text, Operation<Integer> measure) {
        return HudLists.tabWidth(font, text, measure);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"))
    private List<FormattedCharSequence> tc$split(Font font, FormattedText text, int width, Operation<List<FormattedCharSequence>> split) {
        return HudLists.tabSplit(font, text, width, split);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/util/FormattedCharSequence;)I"))
    private int tc$lineWidth(Font font, FormattedCharSequence line, Operation<Integer> measure) {
        return HudLists.tabLineWidth(font, line, measure);
    }
}
