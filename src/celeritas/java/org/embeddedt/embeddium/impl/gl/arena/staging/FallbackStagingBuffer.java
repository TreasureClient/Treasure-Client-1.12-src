package org.embeddedt.embeddium.impl.gl.arena.staging;

import org.embeddedt.embeddium.impl.gl.buffer.GlBuffer;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferUsage;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;

import net.lax1dude.eaglercraft.internal.buffer.ByteBuffer;

public class FallbackStagingBuffer implements StagingBuffer {
    private final GlMutableBuffer fallbackBufferObject;

    public FallbackStagingBuffer(CommandList commandList) {
        this.fallbackBufferObject = commandList.createMutableBuffer();
    }

    @Override
    public void enqueueCopy(CommandList commandList, ByteBuffer data, GlBuffer dst, long writeOffset) {
        // Treasure Client: upload straight into the destination. WebGL can't copy
        // between index and vertex buffers, and this skips a copy anyway.
        commandList.bindBuffer(org.embeddedt.embeddium.impl.gl.buffer.GlBufferTarget.COPY_WRITE_BUFFER, dst);
        org.taumc.celeritas.lwjgl.LWJGLServiceProvider.LWJGL.glBufferSubData(
                org.embeddedt.embeddium.impl.gl.buffer.GlBufferTarget.COPY_WRITE_BUFFER.getTargetParameter(), writeOffset, data);
    }

    @Override
    public void flush(CommandList commandList) {
    }

    @Override
    public void delete(CommandList commandList) {
        commandList.deleteBuffer(this.fallbackBufferObject);
    }

    @Override
    public void flip() {

    }

    @Override
    public String toString() {
        return "Fallback";
    }
}
