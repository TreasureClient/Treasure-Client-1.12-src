package org.taumc.celeritas.lwjgl.webgl;

import net.lax1dude.eaglercraft.internal.buffer.ByteBuffer;
import net.lax1dude.eaglercraft.internal.buffer.FloatBuffer;
import net.lax1dude.eaglercraft.internal.buffer.IntBuffer;
import net.lax1dude.eaglercraft.internal.buffer.ShortBuffer;
import net.lax1dude.eaglercraft.internal.buffer.TCNativeMemory;
import org.taumc.celeritas.lwjgl.MemoryStack;

import java.nio.LongBuffer;

/**
 * Treasure Client: a small scratch-memory stack for Celeritas (used for uniform
 * uploads and similar short-lived buffers). Single-threaded.
 */
final class WebGLMemoryStack extends MemoryStack {
    private static final int SIZE = 64 * 1024;
    private static final int MAX_DEPTH = 32;

    private final long base;
    private long pointer;
    private final long[] frames = new long[MAX_DEPTH];
    private int depth;

    WebGLMemoryStack() {
        this.base = TCNativeMemory.alignedAlloc(16, SIZE);
        this.pointer = this.base + SIZE;
    }

    WebGLMemoryStack push() {
        if (this.depth >= MAX_DEPTH) {
            throw new IllegalStateException("Celeritas memory stack overflow");
        }
        this.frames[this.depth++] = this.pointer;
        return this;
    }

    @Override
    public void close() {
        this.pointer = this.frames[--this.depth];
    }

    @Override
    public long getPointer() {
        return this.pointer;
    }

    @Override
    public void setPointer(long pointer) {
        this.pointer = pointer;
    }

    @Override
    public long getAddress() {
        return this.base;
    }

    @Override
    public int getSize() {
        return SIZE;
    }

    @Override
    public long nmalloc(int size) {
        return nmalloc(8, size);
    }

    @Override
    public long nmalloc(int alignment, int size) {
        long p = (this.pointer - size) & ~((long) Math.max(alignment, 1) - 1);
        if (p < this.base) {
            throw new OutOfMemoryError("Celeritas memory stack is full");
        }
        this.pointer = p;
        return p;
    }

    @Override
    public long ncalloc(int alignment, int count, int size) {
        int bytes = count * size;
        long p = nmalloc(alignment, bytes);
        TCNativeMemory.set(p, 0, bytes);
        return p;
    }

    @Override
    public ByteBuffer malloc(int size) {
        return TCNativeMemory.wrap(nmalloc(8, size), size);
    }

    @Override
    public ShortBuffer mallocShort(int count) {
        return malloc(count << 1).asShortBuffer();
    }

    @Override
    public IntBuffer mallocInt(int count) {
        return malloc(count << 2).asIntBuffer();
    }

    @Override
    public LongBuffer mallocLong(int count) {
        throw new UnsupportedOperationException("LongBuffer is not available");
    }

    @Override
    public FloatBuffer mallocFloat(int count) {
        return malloc(count << 2).asFloatBuffer();
    }

    @Override
    public ByteBuffer calloc(int size) {
        return TCNativeMemory.wrap(ncalloc(8, size, 1), size);
    }

    @Override
    public ShortBuffer callocShort(int count) {
        return calloc(count << 1).asShortBuffer();
    }

    @Override
    public IntBuffer callocInt(int count) {
        return calloc(count << 2).asIntBuffer();
    }

    @Override
    public LongBuffer callocLong(int count) {
        throw new UnsupportedOperationException("LongBuffer is not available");
    }

    @Override
    public FloatBuffer callocFloat(int count) {
        return calloc(count << 2).asFloatBuffer();
    }
}
