package org.taumc.celeritas.impl.render.terrain.compile;

import net.lax1dude.eaglercraft.opengl.WorldRenderer;
import net.minecraft.util.BlockRenderLayer;

/**
 * The vanilla buffer handed to the block renderer during meshing. It lets
 * our dispatcher hook recognize calls made on behalf of a chunk build and find that build's context.
 */
public class ChunkBufferBuilder extends WorldRenderer {
    private final VintageChunkBuildContext context;
    private final BlockRenderLayer layer;

    public ChunkBufferBuilder(int bufferSize, VintageChunkBuildContext context, BlockRenderLayer layer) {
        super(bufferSize);
        this.context = context;
        this.layer = layer;
    }

    @java.lang.SuppressWarnings("all")
    public VintageChunkBuildContext getContext() {
        return this.context;
    }

    @java.lang.SuppressWarnings("all")
    public BlockRenderLayer getLayer() {
        return this.layer;
    }
}
