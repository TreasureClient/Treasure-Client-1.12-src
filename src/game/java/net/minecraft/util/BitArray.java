package net.minecraft.util;

import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.Validate;

public class BitArray {
	/** The long array that is used to store the data for this BitArray. */
	private final long[] longArray;

	/** Number of bits a single entry takes up */
	private final int bitsPerEntry;

	/**
	 * The maximum value for a single entry. This also asks as a bitmask for a
	 * single entry. For instance, if bitsPerEntry were 5, this value would be 31
	 * (ie, {@code 0b00011111}).
	 */
	private final long maxEntryValue;

	/**
	 * Number of entries in this array (<b>not</b> the length of the long array that
	 * internally backs this array)
	 */
	private final int arraySize;

	public BitArray(int bitsPerEntryIn, int arraySizeIn) {
		Validate.inclusiveBetween(1L, 32L, (long) bitsPerEntryIn);
		this.arraySize = arraySizeIn;
		this.bitsPerEntry = bitsPerEntryIn;
		this.maxEntryValue = (1L << bitsPerEntryIn) - 1L;
		this.longArray = new long[MathHelper.roundUp(arraySizeIn * bitsPerEntryIn, 64) / 64];
	}

	/**
	 * Sets the entry at the given location to the given value
	 */
	public void setAt(int index, int value) {
		// Treasure Client: plain checks and shifts instead of Validate calls and divisions
		// (this runs for every block read and written: chunk loading, building, physics)
		if (index < 0 || index >= this.arraySize) {
			throw new IllegalArgumentException("BitArray index " + index + " out of range");
		}
		if (value < 0 || (long) value > this.maxEntryValue) {
			throw new IllegalArgumentException("BitArray value " + value + " out of range");
		}
		int i = index * this.bitsPerEntry;
		int j = i >>> 6;
		int k = ((index + 1) * this.bitsPerEntry - 1) >>> 6;
		int l = i & 63;
		this.longArray[j] = this.longArray[j] & ~(this.maxEntryValue << l) | ((long) value & this.maxEntryValue) << l;

		if (j != k) {
			int i1 = 64 - l;
			int j1 = this.bitsPerEntry - i1;
			this.longArray[k] = this.longArray[k] >>> j1 << j1 | ((long) value & this.maxEntryValue) >> i1;
		}
	}

	/**
	 * Gets the entry at the given index
	 */
	public int getAt(int index) {
		if (index < 0 || index >= this.arraySize) {
			throw new IllegalArgumentException("BitArray index " + index + " out of range");
		}
		int i = index * this.bitsPerEntry;
		int j = i >>> 6;
		int l = i & 63;

		if (l + this.bitsPerEntry <= 64) {
			return (int) (this.longArray[j] >>> l & this.maxEntryValue);
		} else {
			return (int) ((this.longArray[j] >>> l | this.longArray[j + 1] << (64 - l)) & this.maxEntryValue);
		}
	}

	/** Treasure Client: bits per entry (for fast whole-array scans). */
	public int tcBits() {
		return this.bitsPerEntry;
	}

	/**
	 * Gets the long array that is used to store the data in this BitArray. This is
	 * useful for sending packet data.
	 */
	public long[] getBackingLongArray() {
		return this.longArray;
	}

	public int size() {
		return this.arraySize;
	}
}
