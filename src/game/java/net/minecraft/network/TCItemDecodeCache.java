package net.minecraft.network;

import net.minecraft.item.ItemStack;

/**
 * Treasure Client: remembers items already read from packets.
 *
 * On modern servers every menu item carries several KB of hidden data (lore, and the copy of the
 * modern item ViaBackwards keeps), and servers send the same menu items again and again (menus
 * that refresh, inventory resyncs). Reading that data from scratch is most of a 12 ms menu packet
 * on a slow device. When the exact same bytes arrive again, a copy of the item read last time is
 * used instead: copying shares the text instead of decoding it again.
 */
final class TCItemDecodeCache {

	private TCItemDecodeCache() {
	}

	/** Items with less hidden data than this are cheap to read and aren't remembered. */
	static final int MIN_NBT_BYTES = 64;
	private static final int MAX_ENTRIES = 1024;
	private static final long MAX_BYTES = 2L * 1024L * 1024L;

	private static final class Entry {
		final int hash;
		final byte[] data;
		final ItemStack stack;
		final Entry next;

		Entry(int hash, byte[] data, ItemStack stack, Entry next) {
			this.hash = hash;
			this.data = data;
			this.stack = stack;
			this.next = next;
		}
	}

	private static final int BUCKETS = 2048; // power of two
	private static final Entry[] table = new Entry[BUCKETS];
	private static int entries;
	private static long bytes;

	static int hash(byte[] a, int from, int to) {
		int h = 1;
		for (int i = from; i < to; ++i) {
			h = 31 * h + a[i];
		}
		return h;
	}

	/** The item read from exactly these bytes before (callers copy it), or null. */
	static ItemStack get(byte[] a, int from, int to, int hash) {
		int len = to - from;
		for (Entry e = table[(hash ^ (hash >>> 16)) & (BUCKETS - 1)]; e != null; e = e.next) {
			if (e.hash == hash && e.data.length == len && same(e.data, a, from)) {
				return e.stack;
			}
		}
		return null;
	}

	private static boolean same(byte[] k, byte[] a, int from) {
		for (int i = 0; i < k.length; ++i) {
			if (k[i] != a[from + i]) {
				return false;
			}
		}
		return true;
	}

	/** Remembers {@code st} (the caller's own copy, never handed out directly). */
	static void put(byte[] a, int from, int to, int hash, ItemStack st) {
		int len = to - from;
		if (entries >= MAX_ENTRIES || bytes + len > MAX_BYTES) {
			java.util.Arrays.fill(table, null);
			entries = 0;
			bytes = 0L;
		}
		byte[] k = new byte[len];
		System.arraycopy(a, from, k, 0, len);
		int b = (hash ^ (hash >>> 16)) & (BUCKETS - 1);
		table[b] = new Entry(hash, k, st, table[b]);
		++entries;
		bytes += len;
	}

	/**
	 * Where the NBT starting at {@code p} ends (index just past it), or -1 if it can't be walked
	 * (then the item is read the normal way). Only reads lengths; no text is decoded.
	 */
	static int nbtEnd(byte[] a, int p, int lim) {
		if (p >= lim) {
			return -1;
		}
		int t = a[p++];
		if (t == 0) {
			return p;
		}
		p = skipStr(a, p, lim);
		if (p < 0) {
			return -1;
		}
		return skipPayload(a, p, lim, t, 0);
	}

	private static int skipStr(byte[] a, int p, int lim) {
		if (p + 2 > lim) {
			return -1;
		}
		int n = ((a[p] & 255) << 8) | (a[p + 1] & 255);
		p += 2 + n;
		return p <= lim ? p : -1;
	}

	private static int readInt(byte[] a, int p) {
		return ((a[p] & 255) << 24) | ((a[p + 1] & 255) << 16) | ((a[p + 2] & 255) << 8) | (a[p + 3] & 255);
	}

	private static int skipArray(int p, int lim, int each, byte[] a) {
		if (p + 4 > lim) {
			return -1;
		}
		int n = readInt(a, p);
		if (n < 0) {
			return -1;
		}
		long e = (long) p + 4L + (long) n * each;
		return e <= lim ? (int) e : -1;
	}

	private static int skipPayload(byte[] a, int p, int lim, int t, int depth) {
		if (depth > 512) {
			return -1;
		}
		switch (t) {
		case 1:
			p += 1;
			break;
		case 2:
			p += 2;
			break;
		case 3:
		case 5:
			p += 4;
			break;
		case 4:
		case 6:
			p += 8;
			break;
		case 7:
			return skipArray(p, lim, 1, a);
		case 8:
			return skipStr(a, p, lim);
		case 9: {
			if (p + 5 > lim) {
				return -1;
			}
			int et = a[p];
			int n = readInt(a, p + 1);
			p += 5;
			if (n < 0) {
				return -1;
			}
			for (int i = 0; i < n; ++i) {
				p = skipPayload(a, p, lim, et, depth + 1);
				if (p < 0) {
					return -1;
				}
			}
			break;
		}
		case 10:
			for (;;) {
				if (p >= lim) {
					return -1;
				}
				int ct = a[p++];
				if (ct == 0) {
					break;
				}
				p = skipStr(a, p, lim);
				if (p < 0) {
					return -1;
				}
				p = skipPayload(a, p, lim, ct, depth + 1);
				if (p < 0) {
					return -1;
				}
			}
			break;
		case 11:
			return skipArray(p, lim, 4, a);
		case 12:
			return skipArray(p, lim, 8, a);
		default:
			return -1;
		}
		return p <= lim ? p : -1;
	}
}
