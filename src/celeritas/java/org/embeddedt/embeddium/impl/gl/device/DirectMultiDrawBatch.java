package org.embeddedt.embeddium.impl.gl.device;

import org.embeddedt.embeddium.impl.gl.tessellation.GlIndexType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;

import static org.taumc.celeritas.lwjgl.LWJGLServiceProvider.LWJGL;
import org.taumc.celeritas.lwjgl.LWJGLServiceProvider;
import net.lax1dude.eaglercraft.internal.buffer.IntBuffer;

public class DirectMultiDrawBatch extends MultiDrawBatch {
    public final long pElementPointer;
    public final long pElementCount;
    public final long pBaseVertex;

    private static final int POINTER_SHIFT = Integer.numberOfTrailingZeros(LWJGL.getPointerSize());

    public DirectMultiDrawBatch(int capacity) {
        super(capacity);

        int allocCount = this.capacity();

        this.pElementPointer = LWJGL.nmemAlignedAlloc(32, (long) allocCount * LWJGL.getPointerSize());
        if (this.pElementPointer == LWJGLServiceProvider.NULL) {
            throw new OutOfMemoryError("Failed to allocate element pointer array");
        }
        LWJGL.memSet(this.pElementPointer, 0x0, (long) allocCount * LWJGL.getPointerSize());

        this.pElementCount = LWJGL.nmemAlignedAlloc(32, (long) allocCount * Integer.BYTES);
        if (this.pElementCount == LWJGLServiceProvider.NULL) {
            LWJGL.nmemAlignedFree(this.pElementPointer);
            throw new OutOfMemoryError("Failed to allocate element count array");
        }
        this.pBaseVertex = LWJGL.nmemAlignedAlloc(32, (long) allocCount * Integer.BYTES);
        if (this.pBaseVertex == LWJGLServiceProvider.NULL) {
            LWJGL.nmemAlignedFree(this.pElementPointer);
            LWJGL.nmemAlignedFree(this.pElementCount);
            throw new OutOfMemoryError("Failed to allocate base vertex array");
        }
    }

    @Override
    public void delete() {
        LWJGL.nmemAlignedFree(this.pElementPointer);
        LWJGL.nmemAlignedFree(this.pElementCount);
        LWJGL.nmemAlignedFree(this.pBaseVertex);
    }

    @Override
    public void appendDrawCommand(int baseVertex, int elementCount, long elementPointer) {
        int index = this.size;

        LWJGL.memPutInt(this.pBaseVertex + ((long) index << 2), baseVertex);
        LWJGL.memPutInt(this.pElementCount + ((long) index << 2), elementCount);
        LWJGL.memPutAddress(this.pElementPointer + ((long) index << POINTER_SHIFT), elementPointer);

        // Element counts are never negative, so the sign bit of the negation is set iff the command is non-empty.
        this.size = index + ((-elementCount) >>> 31);

        // Safe to apply unconditionally: an empty command cannot raise the maximum.
        this.maxElementCount = Math.max(this.maxElementCount, elementCount);
    }

    @Override
    public void mergeIntoLastCommand(int additionalElementCount) {
        long countPointer = this.pElementCount + ((long) (this.size - 1) << 2);
        int mergedCount = LWJGL.memGetInt(countPointer) + additionalElementCount;

        LWJGL.memPutInt(countPointer, mergedCount);
        this.maxElementCount = Math.max(this.maxElementCount, mergedCount);
    }

    /**
     * Treasure Client: WebGL 2 has no base-vertex draws, so each command used to
     * re-point every vertex attribute before drawing. When all commands index
     * into the shared quad index buffer (index i of quad q is 4q + pattern[i]),
     * drawing from vertex {@code b} is the same as reading the shared indices
     * {@code b / verticesPerPrimitive} primitives further in. This folds each
     * base vertex into the element pointer and then merges commands that end up
     * back to back, so a region usually becomes a few draws with no re-pointing.
     * Only valid for passes that use the shared index buffer (not sorted ones).
     */
    public void foldBaseVertexIntoElementPointer(int verticesPerPrimitive, int indicesPerPrimitive) {
        int count = this.size;
        for (int i = 0; i < count; i++) {
            if (LWJGL.memGetInt(this.pBaseVertex + ((long) i << 2)) % verticesPerPrimitive != 0) {
                return; // not aligned to whole primitives; keep the base-vertex path
            }
        }
        int out = 0;
        long prevEnd = -1L;
        long maxEnd = 0L;
        for (int i = 0; i < count; i++) {
            int baseVertex = LWJGL.memGetInt(this.pBaseVertex + ((long) i << 2));
            int elements = LWJGL.memGetInt(this.pElementCount + ((long) i << 2));
            long pointer = LWJGL.memGetAddress(this.pElementPointer + ((long) i << POINTER_SHIFT));
            pointer += (long) (baseVertex / verticesPerPrimitive) * indicesPerPrimitive * Integer.BYTES;
            long end = pointer + (long) elements * Integer.BYTES;
            if (out > 0 && pointer == prevEnd) {
                long countPointer = this.pElementCount + ((long) (out - 1) << 2);
                LWJGL.memPutInt(countPointer, LWJGL.memGetInt(countPointer) + elements);
            } else {
                LWJGL.memPutInt(this.pBaseVertex + ((long) out << 2), 0);
                LWJGL.memPutInt(this.pElementCount + ((long) out << 2), elements);
                LWJGL.memPutAddress(this.pElementPointer + ((long) out << POINTER_SHIFT), pointer);
                out++;
            }
            prevEnd = end;
            maxEnd = Math.max(maxEnd, end / Integer.BYTES);
        }
        this.size = out;
        // The shared index buffer must now reach the furthest index any command reads
        this.maxElementCount = (int) Math.min(Integer.MAX_VALUE, maxEnd);
    }

    @Override
    public void upload(CommandList commandList) {
        // no-op: the GL call reads these arrays directly out of CPU memory
    }

    @Override
    public void execute(CommandList commandList, GlTessellation tessellation, GlPrimitiveType primitiveType) {
        try (DrawCommandList drawCommandList = commandList.beginTessellating(tessellation)) {
            drawCommandList.multiDrawElementsBaseVertex(this, primitiveType, GlIndexType.UNSIGNED_INT);
        }
    }
}
