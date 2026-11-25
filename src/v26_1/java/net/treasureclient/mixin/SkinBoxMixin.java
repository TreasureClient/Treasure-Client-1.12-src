package net.treasureclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.resources.Identifier;
import net.treasureclient.cos.SkinBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Room around the player picture so big cosmetics aren't cut off (26.1). See SkinBox. */
@Mixin(PlayerSkinWidget.class)
public class SkinBoxMixin {
    @WrapOperation(method = "extractWidgetRenderState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;skin(Lnet/minecraft/client/model/player/PlayerModel;Lnet/minecraft/resources/Identifier;FFFFIIII)V"))
    private void tc$room(GuiGraphicsExtractor g, PlayerModel model, Identifier tex, float scale, float rotX, float rotY, float pivot,
                         int x0, int y0, int x1, int y1, Operation<Void> draw) {
        int[] b = SkinBox.grow(x0, y0, x1, y1);
        draw.call(g, model, tex, scale, rotX, rotY, pivot, b[0], b[1], b[2], b[3]);
    }
}
