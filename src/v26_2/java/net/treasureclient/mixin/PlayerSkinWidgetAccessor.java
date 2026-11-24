package net.treasureclient.mixin;

import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.model.Model;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerSkinWidget.class)
public interface PlayerSkinWidgetAccessor {
    @Accessor("wideModel")
    Model.Simple tc$wide();

    @Accessor("slimModel")
    Model.Simple tc$slim();

    @Accessor("rotationY")
    float tc$rotationY();

    @Accessor("rotationY")
    void tc$setRotationY(float degrees);
}
