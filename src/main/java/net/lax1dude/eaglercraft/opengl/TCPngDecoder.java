package net.lax1dude.eaglercraft.opengl;

import java.io.DataInputStream;
import java.io.InputStream;

import net.lax1dude.eaglercraft.EaglerInputStream;
import net.lax1dude.eaglercraft.EaglerZLIB;

/**
 * Treasure Client: decodes small PNG textures right away.
 *
 * The browser decodes images on its own schedule: the game has to stop and wait
 * until the browser says the picture is ready, which on a slow laptop took up to
 * 40 ms even for a tiny mob texture. Every new mob, armor piece or menu seen for
 * the first time was a stutter. Small PNGs (up to 256x256, which covers mobs,
 * armor and menus) are decoded here in a millisecond or two instead.
 *
 * Anything unusual (large, interlaced, 16 bits per channel, color profiles or
 * gamma, which the browser would adjust) returns null and goes to the browser as
 * before, so every picture comes out the same.
 */
public final class TCPngDecoder {

	private TCPngDecoder() {
	}

	/** Off switch (in case a texture ever looks wrong). */
	public static boolean enabled = true;

	private static final int MAX_PIXELS = 256 * 256;

	/** Pixels as the browser path returns them (red in the low byte, alpha on top), or null. */
	public static ImageData decode(byte[] d, String mime) {
		if (!enabled || d == null || d.length < 57 || (mime != null && !"image/png".equals(mime))) {
			return null;
		}
		try {
			return decode0(d);
		} catch (Throwable t) {
			return null; // the browser will try
		}
	}

	private static int i32(byte[] d, int p) {
		return ((d[p] & 255) << 24) | ((d[p + 1] & 255) << 16) | ((d[p + 2] & 255) << 8) | (d[p + 3] & 255);
	}

	private static ImageData decode0(byte[] d) throws Exception {
		if ((d[0] & 255) != 0x89 || d[1] != 'P' || d[2] != 'N' || d[3] != 'G' || d[4] != 13 || d[5] != 10
				|| d[6] != 26 || d[7] != 10) {
			return null;
		}
		int w = 0, h = 0, depth = 0, type = -1;
		byte[] plte = null, trns = null;
		int idatLen = 0;
		int p = 8;
		// first pass: header, palette, transparency, total IDAT size
		while (p + 8 <= d.length) {
			int len = i32(d, p);
			int ct = i32(d, p + 4);
			int body = p + 8;
			if (len < 0 || body + len + 4 > d.length) {
				return null;
			}
			switch (ct) {
			case 0x49484452: // IHDR
				w = i32(d, body);
				h = i32(d, body + 4);
				depth = d[body + 8] & 255;
				type = d[body + 9] & 255;
				if (d[body + 10] != 0 || d[body + 11] != 0 || d[body + 12] != 0) {
					return null; // unknown compression/filter, or interlaced
				}
				break;
			case 0x504C5445: // PLTE
				plte = new byte[len];
				System.arraycopy(d, body, plte, 0, len);
				break;
			case 0x74524E53: // tRNS
				trns = new byte[len];
				System.arraycopy(d, body, trns, 0, len);
				break;
			case 0x49444154: // IDAT
				idatLen += len;
				break;
			case 0x67414D41: // gAMA
			case 0x69434350: // iCCP
			case 0x6348524D: // cHRM
				return null; // the browser color-corrects these
			}
			if (ct == 0x49454E44) { // IEND
				break;
			}
			p = body + len + 4;
		}
		if (w <= 0 || h <= 0 || (long) w * h > MAX_PIXELS || idatLen == 0) {
			return null;
		}
		int channels;
		switch (type) {
		case 0: channels = 1; break;
		case 2: channels = 3; break;
		case 3: channels = 1; break;
		case 4: channels = 2; break;
		case 6: channels = 4; break;
		default: return null;
		}
		if (depth == 16 || (depth != 8 && type != 0 && type != 3) || (depth != 1 && depth != 2 && depth != 4
				&& depth != 8)) {
			return null;
		}
		if (type == 3 && plte == null) {
			return null;
		}
		if (trns != null && type != 3) {
			return null; // single-color transparency (rare): leave it to the browser
		}
		// join the compressed data
		byte[] z = new byte[idatLen];
		int zp = 0;
		p = 8;
		while (p + 8 <= d.length) {
			int len = i32(d, p);
			int ct = i32(d, p + 4);
			if (ct == 0x49444154) {
				System.arraycopy(d, p + 8, z, zp, len);
				zp += len;
			} else if (ct == 0x49454E44) {
				break;
			}
			p += len + 12;
		}
		int bitsPerPixel = channels * depth;
		int rowBytes = (w * bitsPerPixel + 7) >> 3;
		int bpp = Math.max(1, bitsPerPixel >> 3);
		byte[] raw = new byte[h * (rowBytes + 1)];
		InputStream in = EaglerZLIB.newInflaterInputStream(new EaglerInputStream(z));
		try {
			new DataInputStream(in).readFully(raw);
		} finally {
			in.close();
		}
		// undo the row filters, in place
		byte[] prev = new byte[rowBytes];
		byte[] cur = new byte[rowBytes];
		int[] px = new int[w * h];
		for (int y = 0; y < h; y++) {
			int rp = y * (rowBytes + 1);
			int f = raw[rp] & 255;
			System.arraycopy(raw, rp + 1, cur, 0, rowBytes);
			switch (f) {
			case 0:
				break;
			case 1:
				for (int i = bpp; i < rowBytes; i++) cur[i] += cur[i - bpp];
				break;
			case 2:
				for (int i = 0; i < rowBytes; i++) cur[i] += prev[i];
				break;
			case 3:
				for (int i = 0; i < rowBytes; i++) {
					int a = i >= bpp ? cur[i - bpp] & 255 : 0;
					cur[i] += (byte) ((a + (prev[i] & 255)) >> 1);
				}
				break;
			case 4:
				for (int i = 0; i < rowBytes; i++) {
					int a = i >= bpp ? cur[i - bpp] & 255 : 0;
					int b = prev[i] & 255;
					int c = i >= bpp ? prev[i - bpp] & 255 : 0;
					int pa = Math.abs(b - c), pb = Math.abs(a - c), pc = Math.abs(a + b - 2 * c);
					int pr = (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c);
					cur[i] += (byte) pr;
				}
				break;
			default:
				return null;
			}
			int o = y * w;
			switch (type) {
			case 6:
				for (int x = 0, i = 0; x < w; x++, i += 4) {
					px[o + x] = ((cur[i + 3] & 255) << 24) | ((cur[i + 2] & 255) << 16) | ((cur[i + 1] & 255) << 8)
							| (cur[i] & 255);
				}
				break;
			case 2:
				for (int x = 0, i = 0; x < w; x++, i += 3) {
					px[o + x] = 0xFF000000 | ((cur[i + 2] & 255) << 16) | ((cur[i + 1] & 255) << 8) | (cur[i] & 255);
				}
				break;
			case 4:
				for (int x = 0, i = 0; x < w; x++, i += 2) {
					int g = cur[i] & 255;
					px[o + x] = ((cur[i + 1] & 255) << 24) | (g << 16) | (g << 8) | g;
				}
				break;
			case 0: {
				int max = (1 << depth) - 1;
				for (int x = 0; x < w; x++) {
					int g = sample(cur, x, depth) * 255 / max;
					px[o + x] = 0xFF000000 | (g << 16) | (g << 8) | g;
				}
				break;
			}
			case 3:
				for (int x = 0; x < w; x++) {
					int idx = sample(cur, x, depth);
					int pi = idx * 3;
					if (pi + 2 >= plte.length) {
						return null;
					}
					int a = trns != null && idx < trns.length ? trns[idx] & 255 : 255;
					px[o + x] = (a << 24) | ((plte[pi + 2] & 255) << 16) | ((plte[pi + 1] & 255) << 8)
							| (plte[pi] & 255);
				}
				break;
			}
			byte[] t = prev;
			prev = cur;
			cur = t;
		}
		// the browser path hands back colors un-premultiplied from a canvas, so fully clear
		// pixels come back black; do the same so nothing that relies on it changes
		for (int i = 0; i < px.length; i++) {
			if ((px[i] >>> 24) == 0) {
				px[i] = 0;
			}
		}
		return new ImageData(w, h, px, true);
	}

	private static int sample(byte[] row, int x, int depth) {
		if (depth == 8) {
			return row[x] & 255;
		}
		int bit = x * depth;
		int b = row[bit >> 3] & 255;
		int shift = 8 - depth - (bit & 7);
		return (b >> shift) & ((1 << depth) - 1);
	}
}
