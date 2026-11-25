package net.treasureclient.mixin.boost;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.treasureclient.boost.RainCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Rain Cache (26.2+: ClientLevel.getPrecipitationAt). */
@Mixin(WeatherEffectRenderer.class)
public class RainMixin {
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation tc$rain(ClientLevel level, BlockPos pos, Operation<Biome.Precipitation> ask) {
        Biome.Precipitation v = RainCache.get(level, pos);
        return v != null ? v : RainCache.put(pos, ask.call(level, pos));
    }
}
