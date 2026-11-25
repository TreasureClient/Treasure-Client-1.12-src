package net.treasureclient.mixin.boost;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.treasureclient.boost.RainCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Rain Cache (1.21.11 and 26.1: the weather renderer's own getPrecipitationAt helper). */
@Mixin(WeatherEffectRenderer.class)
public class RainMixin {
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;getPrecipitationAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation tc$rain(WeatherEffectRenderer self, Level level, BlockPos pos, Operation<Biome.Precipitation> ask) {
        Biome.Precipitation v = RainCache.get(level, pos);
        return v != null ? v : RainCache.put(pos, ask.call(self, level, pos));
    }
}
