package net.lax1dude.eaglercraft.internal.buffer;

import org.teavm.interop.Address;
import org.teavm.interop.DirectMalloc;

/**
 * Treasure Client: raw memory access for the Celeritas chunk renderer (WASM
 * build). Addresses are offsets into WebAssembly linear memory.
 */
public final class TCNativeMemory {

	private TCNativeMemory() {
	}

	public static final boolean SUPPORTED = true;

	public static int pointerSize() {
		return 4;
	}

	public static long malloc(long size) {
		return WASMGCBufferAllocator.malloc((int) size).toInt() & 0xFFFFFFFFL;
	}

	public static long calloc(long size) {
		return WASMGCBufferAllocator.calloc((int) size).toInt() & 0xFFFFFFFFL;
	}

	public static void free(long ptr) {
		WASMGCBufferAllocator.free(Address.fromInt((int) ptr));
	}

	public static long realloc(long ptr, long oldSize, long newSize) {
		long ret = malloc(newSize);
		if (ptr != 0L) {
			copy(ptr, ret, Math.min(oldSize, newSize));
			free(ptr);
		}
		return ret;
	}

	/** Allocates {@code size} bytes aligned to {@code alignment} (a power of two). */
	public static long alignedAlloc(long alignment, long size) {
		int align = (int) Math.max(alignment, 4L);
		long raw = malloc(size + align + 4);
		long aligned = (raw + 4 + align - 1) & ~((long) align - 1);
		Address.fromInt((int) (aligned - 4)).putInt((int) raw);
		return aligned;
	}

	public static void alignedFree(long ptr) {
		if (ptr != 0L) {
			free(Address.fromInt((int) (ptr - 4)).getInt() & 0xFFFFFFFFL);
		}
	}

	public static ByteBuffer wrap(long address, int capacity) {
		return new DirectMallocByteBuffer(Address.fromInt((int) address), capacity, false);
	}

	public static long address(Buffer buffer) {
		if (buffer instanceof DirectMallocByteBuffer) {
			DirectMallocByteBuffer b = (DirectMallocByteBuffer) buffer;
			return (b.address.toInt() & 0xFFFFFFFFL) + b.position();
		} else if (buffer instanceof DirectMallocIntBuffer) {
			DirectMallocIntBuffer b = (DirectMallocIntBuffer) buffer;
			return (b.address.toInt() & 0xFFFFFFFFL) + ((long) b.position() << 2);
		} else if (buffer instanceof DirectMallocFloatBuffer) {
			DirectMallocFloatBuffer b = (DirectMallocFloatBuffer) buffer;
			return (b.address.toInt() & 0xFFFFFFFFL) + ((long) b.position() << 2);
		} else if (buffer instanceof DirectMallocShortBuffer) {
			DirectMallocShortBuffer b = (DirectMallocShortBuffer) buffer;
			return (b.address.toInt() & 0xFFFFFFFFL) + ((long) b.position() << 1);
		}
		throw new UnsupportedOperationException("Buffer has no address: " + buffer);
	}

	public static ByteBuffer allocByteBuffer(int size) {
		return WASMGCBufferAllocator.allocateByteBuffer(size);
	}

	public static void freeBuffer(Buffer buffer) {
		if (buffer instanceof ByteBuffer) {
			WASMGCBufferAllocator.freeByteBuffer((ByteBuffer) buffer);
		} else if (buffer instanceof IntBuffer) {
			WASMGCBufferAllocator.freeIntBuffer((IntBuffer) buffer);
		} else if (buffer instanceof FloatBuffer) {
			WASMGCBufferAllocator.freeFloatBuffer((FloatBuffer) buffer);
		} else if (buffer instanceof ShortBuffer) {
			WASMGCBufferAllocator.freeShortBuffer((ShortBuffer) buffer);
		}
	}

	public static void set(long address, int value, long bytes) {
		DirectMalloc.memset(Address.fromInt((int) address), value, (int) bytes);
	}

	public static void copy(long src, long dst, long bytes) {
		DirectMalloc.memcpy(Address.fromInt((int) dst), Address.fromInt((int) src), (int) bytes);
	}

	public static void putByte(long address, byte value) {
		Address.fromInt((int) address).putByte(value);
	}

	public static void putShort(long address, short value) {
		Address.fromInt((int) address).putShort(value);
	}

	public static void putInt(long address, int value) {
		Address.fromInt((int) address).putInt(value);
	}

	public static void putFloat(long address, float value) {
		Address.fromInt((int) address).putFloat(value);
	}

	public static void putLong(long address, long value) {
		Address.fromInt((int) address).putLong(value);
	}

	public static byte getByte(long address) {
		return Address.fromInt((int) address).getByte();
	}

	public static short getShort(long address) {
		return Address.fromInt((int) address).getShort();
	}

	public static int getInt(long address) {
		return Address.fromInt((int) address).getInt();
	}

	public static float getFloat(long address) {
		return Address.fromInt((int) address).getFloat();
	}

	public static long getLong(long address) {
		return Address.fromInt((int) address).getLong();
	}
}
