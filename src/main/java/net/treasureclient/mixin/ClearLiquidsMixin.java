package net.treasureclient.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import net.treasureclient.core.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear Liquids: the thick fog inside water and lava is pushed out of sight (normal distance fog stays). */
@Mixin({WaterFogEnvironment.class, LavaFogEnvironment.class})
public class ClearLiquidsMixin {
    @Inject(method = "setupFog", at = @At("TAIL"))
    private void treasureclient$clear(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker delta, CallbackInfo ci) {
        if (!Config.get().on("clearLiquids")) return;
        fog.environmentalStart = 100000f;
        fog.environmentalEnd = 100001f;
    }
}
