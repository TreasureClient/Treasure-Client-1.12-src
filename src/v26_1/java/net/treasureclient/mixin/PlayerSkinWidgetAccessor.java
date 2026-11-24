package net.treasureclient.mixin;

import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.model.player.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerSkinWidget.class)
public interface PlayerSkinWidgetAccessor {
    @Accessor("wideModel")
    PlayerModel tc$wide();

    @Accessor("slimModel")
    PlayerModel tc$slim();

    @Accessor("rotationY")
    float tc$rotationY();

    @Accessor("rotationY")
    void tc$setRotationY(float degrees);
}
