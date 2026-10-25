package net.lax1dude.eaglercraft.internal.buffer;

/**
 * Treasure Client: raw memory access for the Celeritas chunk renderer.
 *
 * <p>The JavaScript build has no linear memory, so Celeritas is switched off
 * here ({@link #SUPPORTED} is false) and the vanilla renderer is used.
 */
public final class TCNativeMemory {

	private TCNativeMemory() {
	}

	public static final boolean SUPPORTED = false;

	private static UnsupportedOperationException unsupported() {
		return new UnsupportedOperationException("Native memory is not available in the JavaScript build");
	}

	public static int pointerSize() {
		return 4;
	}

	public static long malloc(long size) {
		throw unsupported();
	}

	public static long calloc(long size) {
		throw unsupported();
	}

	public static void free(long ptr) {
		throw unsupported();
	}

	public static long realloc(long ptr, long oldSize, long newSize) {
		throw unsupported();
	}

	public static long alignedAlloc(long alignment, long size) {
		throw unsupported();
	}

	public static void alignedFree(long ptr) {
		throw unsupported();
	}

	public static ByteBuffer wrap(long address, int capacity) {
		throw unsupported();
	}

	public static long address(Buffer buffer) {
		throw unsupported();
	}

	public static ByteBuffer allocByteBuffer(int size) {
		return EaglerArrayBufferAllocator.allocateByteBuffer(size);
	}

	public static void freeBuffer(Buffer buffer) {
	}

	public static void set(long address, int value, long bytes) {
		throw unsupported();
	}

	public static void copy(long src, long dst, long bytes) {
		throw unsupported();
	}

	public static void putByte(long address, byte value) {
		throw unsupported();
	}

	public static void putShort(long address, short value) {
		throw unsupported();
	}

	public static void putInt(long address, int value) {
		throw unsupported();
	}

	public static void putFloat(long address, float value) {
		throw unsupported();
	}

	public static void putLong(long address, long value) {
		throw unsupported();
	}

	public static byte getByte(long address) {
		throw unsupported();
	}

	public static short getShort(long address) {
		throw unsupported();
	}

	public static int getInt(long address) {
		throw unsupported();
	}

	public static float getFloat(long address) {
		throw unsupported();
	}

	public static long getLong(long address) {
		throw unsupported();
	}
}
