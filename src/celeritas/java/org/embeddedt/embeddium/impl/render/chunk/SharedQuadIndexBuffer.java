package org.embeddedt.embeddium.impl.render.chunk;

import org.embeddedt.embeddium.impl.gl.buffer.GlBuffer;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferMapFlags;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferUsage;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.util.EnumBitField;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;

public class SharedQuadIndexBuffer {
    private final GlMutableBuffer buffer;
    private final ChunkPrimitiveType primitiveType;

    private int maxPrimitives;

    public SharedQuadIndexBuffer(CommandList commandList, ChunkPrimitiveType primitiveType) {
        this.buffer = commandList.createMutableBuffer();
        org.taumc.celeritas.lwjgl.LWJGLServiceProvider.LWJGL.hintElementArrayBuffer(this.buffer.handle());
        this.primitiveType = primitiveType;
    }

    public void ensureCapacity(CommandList commandList, int elementCount) {
        int primitiveCount = elementCount / primitiveType.getIndexBufferElementsPerPrimitive();

        if (primitiveCount > this.maxPrimitives) {
            this.grow(commandList, this.getNextSize(primitiveCount));
        }
    }

    private int getNextSize(int primitiveCount) {
        return Math.max(this.maxPrimitives * 2, primitiveCount + 16384);
    }

    private void grow(CommandList commandList, int primitiveCount) {
        var bufferSize = primitiveType.getIndexBufferSize(primitiveCount);

        // Treasure Client: WebGL can't map buffers, so build the indices in memory and upload them
        var data = new org.embeddedt.embeddium.impl.common.util.NativeBuffer(bufferSize);
        try {
            this.primitiveType.generateSimpleIndexBuffer(data.getDirectBuffer(), primitiveCount);
            commandList.uploadData(this.buffer, data.getDirectBuffer(), GlBufferUsage.STATIC_DRAW);
        } finally {
            data.free();
        }

        this.maxPrimitives = primitiveCount;
    }


    public GlBuffer getBufferObject() {
        return this.buffer;
    }

    public void delete(CommandList commandList) {
        commandList.deleteBuffer(this.buffer);
    }
}
