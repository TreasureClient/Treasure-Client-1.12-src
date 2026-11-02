package org.embeddedt.embeddium.impl.render.chunk.compile;

import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;

public class ChunkBuildContext {
    public final ChunkBuildBuffers buffers;

    public ChunkBuildContext(RenderPassConfiguration renderPassConfiguration) {
        this.buffers = new ChunkBuildBuffers(renderPassConfiguration);
    }

    public void cleanup() {
        if (!this.tcSoft) {
            this.buffers.destroy();
        }
    }

    // Treasure Client: after each chunk build, reset per-build state but keep the scratch mesh
    // buffers, so the next build doesn't allocate and regrow them (they're copied out already).
    private boolean tcSoft;

    public void tcJobDone() {
        this.tcSoft = true;
        try {
            this.cleanup();
        } finally {
            this.tcSoft = false;
        }
    }
}
