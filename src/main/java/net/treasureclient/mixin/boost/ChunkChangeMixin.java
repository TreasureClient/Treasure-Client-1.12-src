package net.treasureclient.mixin.boost;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.treasureclient.boost.RainCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rain Cache: chunks loaded, unloaded or given new biomes, or a new world, make the answers fresh. */
@Mixin(ClientPacketListener.class)
public class ChunkChangeMixin {
    @Inject(method = {"handleLevelChunkWithLight", "handleForgetLevelChunk", "handleChunksBiomes", "handleRespawn", "handleLogin"},
        at = @At("TAIL"))
    private void tc$changed(CallbackInfo ci) {
        RainCache.chunksChanged();
    }
}
