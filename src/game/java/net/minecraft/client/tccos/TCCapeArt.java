package net.minecraft.client.tccos;

/**
 * Treasure Client cosmetics: draws the animated capes (all original designs).
 *
 * Every cape is a strip of {@link #FRAMES} pictures laid out in a grid on one texture,
 * and the game only slides the texture across to the next picture (a texture matrix,
 * like the enchantment shine), so nothing is redrawn or uploaded while you play.
 *
 * A cape picture follows Minecraft's cape layout at 4x detail: the outside of the cape is
 * 40x64 pixels at (4,4), the lining is at (48,4), the edges around them. Each animation
 * loops exactly, so frame 63 runs straight into frame 0.
 *
 * Plain Java on purpose (no game classes), so the capes can be drawn into pictures and
 * checked without starting the game.
 */
public final class TCCapeArt {

	private TCCapeArt() {
	}

	/** Detail: 4 texture pixels per Minecraft pixel. */
	public static final int S = 4;
	/** One picture: the part of a 32x32 cape texture that the cape uses (22x17), at 4x. */
	public static final int CELL_W = 22 * S, CELL_H = 17 * S;
	public static final int FRAMES = 64, COLS = 8, ROWS = 8;
	public static final int TEX_W = CELL_W * COLS, TEX_H = CELL_H * ROWS;
	/** How long one loop of the animation lasts. */
	public static final long LOOP_MS = 4000L;

	/** The outside face (what people see from behind you). */
	static final int FW = 10 * S, FH = 16 * S;

	public static final String[] IDS = { "treasure", "frost", "tropical", "ember", "aurora", "galaxy", "circuit", "theme" };

	public static boolean known(String id) {
		if (id == null) return false;
		if (id.startsWith("custom:")) return validMaker(id.substring(7));
		for (int i = 0; i < IDS.length; ++i) {
			if (IDS[i].equals(id)) return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ the Maker's parts

	public static final String[] PATTERNS = { "solid", "fade", "stripes", "chevron", "checks", "waves", "stars", "split" };
	public static final String[] PALETTE = { "theme", "black", "white", "gold", "red", "orange", "yellow", "lime", "green", "aqua", "blue", "violet", "pink" };
	static final int[] PALETTE_RGB = { 0, 0x16161A, 0xF0F0EA, 0xFFD76B, 0xE8434F, 0xFF8A3C, 0xFFE066, 0x9BEA5B, 0x2FBF71, 0x45E3C6, 0x4A8CFF, 0x9A6BFF, 0xFF6FA8 };
	public static final String[] EMBLEMS = { "none", "star", "heart", "diamond", "bolt", "moon", "crown", "sword" };
	public static final String[] MOTIONS = { "still", "shine", "flow", "pulse", "sparkle" };

	/** A Maker cape is "pattern.colour1.colour2.emblem.motion", each from the lists above. */
	public static boolean validMaker(String spec) {
		if (spec == null || spec.length() > 60) return false;
		String[] p = spec.split("\\.", -1);
		return p.length == 5 && idx(PATTERNS, p[0]) >= 0 && idx(PALETTE, p[1]) >= 0 && idx(PALETTE, p[2]) >= 0
				&& idx(EMBLEMS, p[3]) >= 0 && idx(MOTIONS, p[4]) >= 0;
	}

	/** True if a Maker cape uses the theme colour (so it changes with the wearer's theme). */
	public static boolean makerUsesTheme(String spec) {
		String[] p = spec.split("\\.", -1);
		return p.length == 5 && ("theme".equals(p[1]) || "theme".equals(p[2]));
	}

	static int idx(String[] a, String v) {
		for (int i = 0; i < a.length; ++i) {
			if (a[i].equals(v)) return i;
		}
		return -1;
	}

	/** Draws every frame of a cape at once (used by the picture checker; the game uses {@link Builder}). */
	public static int[] build(String id, int accent) {
		Builder b = new Builder(id, accent);
		while (!b.step(FRAMES)) {
		}
		return b.pixels;
	}

	/**
	 * Draws a cape a few frames at a time, so making a new cape never stalls a frame
	 * (on a slow Chromebook a whole cape takes a few hundred milliseconds).
	 */
	public static final class Builder {
		public final int[] pixels = new int[TEX_W * TEX_H];
		private final Art art;
		private final float[] r = new float[FW * FH], g = new float[FW * FH], b = new float[FW * FH];
		private int next = 0;

		/** @param accent the theme colour (only the "theme" cape uses it) */
		public Builder(String id, int accent) {
			this.art = make(id, accent);
		}

		/** Draws up to n more frames; true when the whole cape is done. */
		public boolean step(int n) {
			for (int k = 0; k < n && next < FRAMES; ++k) {
				drawFrame(next++);
			}
			return next >= FRAMES;
		}

		private void drawFrame(int f) {
			int[] out = pixels;
			float t = f / (float) FRAMES;
			java.util.Arrays.fill(r, 0f);
			java.util.Arrays.fill(g, 0f);
			java.util.Arrays.fill(b, 0f);
			art.frame(t, r, g, b);
			int ox = (f % COLS) * CELL_W, oy = (f / COLS) * CELL_H;
			// the outside face
			for (int y = 0; y < FH; ++y) {
				for (int x = 0; x < FW; ++x) {
					int i = y * FW + x;
					out[(oy + S + y) * TEX_W + ox + S + x] = argb(r[i], g[i], b[i]);
				}
			}
			// the lining, the edges, the top and bottom
			for (int y = 0; y < FH; ++y) {
				float k = 1f - 0.25f * y / FH;
				int lin = scale(art.lining, k);
				for (int x = 0; x < FW; ++x) {
					out[(oy + S + y) * TEX_W + ox + 2 * S + FW + x] = lin;
				}
				for (int x = 0; x < S; ++x) {
					out[(oy + S + y) * TEX_W + ox + x] = art.trim;
					out[(oy + S + y) * TEX_W + ox + S + FW + x] = art.trim;
				}
			}
			for (int y = 0; y < S; ++y) {
				for (int x = 0; x < FW; ++x) {
					out[(oy + y) * TEX_W + ox + S + x] = art.trim;
					out[(oy + y) * TEX_W + ox + S + FW + x] = art.trim;
				}
			}
		}
	}

	// ------------------------------------------------------------------ the designs

	abstract static class Art {
		int lining = 0xFF1A1A1A, trim = 0xFF333333;

		abstract void frame(float t, float[] r, float[] g, float[] b);
	}

	static Art make(String id, int accent) {
		if (id.startsWith("custom:")) return new Custom(id.substring(7), accent);
		switch (id) {
		case "frost": return new Frost();
		case "tropical": return new Tropical();
		case "ember": return new Ember();
		case "aurora": return new Aurora();
		case "galaxy": return new Galaxy();
		case "circuit": return new Circuit();
		case "theme": return new Treasure(accent, true);
		default: return new Treasure(0xFFFFD76B, false);
		}
	}

	/** Dark cape, a gold border and the Treasure chest, with a shine sweeping across. */
	static final class Treasure extends Art {
		final float ar, ag, ab;
		final boolean theme;

		Treasure(int accent, boolean theme) {
			this.theme = theme;
			ar = ((accent >> 16) & 255) / 255f;
			ag = ((accent >> 8) & 255) / 255f;
			ab = (accent & 255) / 255f;
			lining = theme ? mixRgb(0xFF0D0D10, accent, 0.18f) : 0xFF3A2410;
			trim = mixRgb(0xFF000000, accent, 0.7f);
		}

		void frame(float t, float[] r, float[] g, float[] b) {
			for (int y = 0; y < FH; ++y) {
				for (int x = 0; x < FW; ++x) {
					int i = y * FW + x;
					float v = y / (float) FH;
					float cr, cg, cb;
					if (theme) {
						float k = 0.16f - 0.08f * v;
						cr = 0.04f + ar * k; cg = 0.04f + ag * k; cb = 0.05f + ab * k;
						// diagonal stripes sliding slowly down
						int s = ((x + y + (int) (t * 16)) % 16 + 16) % 16;
						if (s < 2) { cr += ar * 0.14f; cg += ag * 0.14f; cb += ab * 0.14f; }
					} else {
						cr = lerp(0.17f, 0.07f, v); cg = lerp(0.105f, 0.04f, v); cb = lerp(0.05f, 0.016f, v);
						// a fine weave
						if (((x >> 1) + (y >> 1)) % 2 == 0) { cr *= 0.9f; cg *= 0.9f; cb *= 0.9f; }
					}
					float gold = 0f;
					// border: a 2px line, 2px in from the edge
					if (inBorder(x, y, 2, 2)) gold = 1f;
					else if (inBorder(x, y, 6, 1)) gold = 0.55f;
					float e = emblem(x, y);
					if (e > gold) gold = e;
					if (gold > 0f) {
						float d = gold >= 0.99f ? 1f : gold;
						float shade = d < 0.6f ? 0.55f : (d < 0.9f ? 0.78f : 1f);
						cr = lerp(cr, ar * shade, Math.min(1f, d * 1.4f));
						cg = lerp(cg, ag * shade, Math.min(1f, d * 1.4f));
						cb = lerp(cb, ab * shade, Math.min(1f, d * 1.4f));
					}
					// the shine: a soft diagonal band crossing once per loop
					float s = (x * 0.55f + y) / (FH + FW * 0.55f);
					float c = t * 1.7f - 0.35f;
					float sh = (float) Math.exp(-sq((s - c) / 0.045f));
					float add = sh * (gold > 0f ? 0.65f : 0.12f);
					r[i] = cr + add; g[i] = cg + add; b[i] = cb + add * 0.85f;
				}
			}
			// a few gold sparkles that twinkle
			int[][] sp = { { 8, 12 }, { 31, 47 }, { 27, 15 }, { 11, 52 } };
			for (int k = 0; k < sp.length; ++k) {
				float tw = Math.max(0f, (float) Math.sin(2 * Math.PI * (t * 2 + k * 0.37)));
				tw = tw * tw * tw;
				sparkle(r, g, b, sp[k][0], sp[k][1], 2, 1f * tw, ar * 0.5f + 0.5f, ag * 0.5f + 0.5f, ab * 0.5f + 0.5f);
			}
		}

		static boolean inBorder(int x, int y, int inset, int width) {
			if (x < inset || y < inset || x >= FW - inset || y >= FH - inset) return false;
			return x < inset + width || y < inset + width || x >= FW - inset - width || y >= FH - inset - width;
		}

		/** The chest in the middle: 1 = bright gold, 0.75 = mid, 0.5 = dark outline, 0 = none. */
		static float emblem(int x, int y) {
			int x0 = 11, x1 = 29, ly0 = 21, ly1 = 28, by1 = 39;
			if (x < x0 || x >= x1 || y < ly0 || y >= by1) return 0f;
			// rounded lid corners
			if (y < ly0 + 2 && (x < x0 + 2 || x >= x1 - 2)) return 0f;
			boolean edge = x == x0 || x == x1 - 1 || y == ly0 || y == by1 - 1;
			if (edge) return 0.5f;
			if (y == ly1 || y == ly1 + 1) return 0.5f; // where the lid meets the box
			// the lock
			if (x >= 18 && x < 22 && y >= ly1 - 2 && y < ly1 + 5) return (x == 19 || x == 20) && y == ly1 + 2 ? 0.5f : 1f;
			// planks
			if (y > ly1 + 1 && (y - ly1) % 4 == 0) return 0.75f;
			if (x == x0 + 4 || x == x1 - 5) return 0.75f;
			return y < ly1 ? 1f : 0.88f;
		}
	}

	/** Icy blue with snowflakes drifting down and a soft shimmer. */
	static final class Frost extends Art {
		Frost() {
			lining = 0xFF0C2440;
			trim = 0xFFBFEFFF;
		}

		void frame(float t, float[] r, float[] g, float[] b) {
			for (int y = 0; y < FH; ++y) {
				for (int x = 0; x < FW; ++x) {
					int i = y * FW + x;
					float v = y / (float) FH;
					float n = noise(x * 0.16f, y * 0.16f, 11) * 0.5f + noise(x * 0.4f, y * 0.4f, 12) * 0.25f;
					float cr = lerp(0.03f, 0.25f, v) + n * 0.06f;
					float cg = lerp(0.10f, 0.58f, v) + n * 0.10f;
					float cb = lerp(0.22f, 0.86f, v) + n * 0.10f;
					// frost crystals: thin bright lines from the noise
					float ridge = 1f - Math.abs(noise(x * 0.09f, y * 0.09f, 13) * 2f - 1f);
					float cry = (float) Math.pow(ridge, 14) * 0.35f;
					// a shimmer moving down the cape, twice per loop
					float m = frac(v * 0.9f - t * 2f);
					float sh = (float) Math.exp(-sq((m - 0.5f) / 0.08f)) * 0.16f;
					r[i] = cr + cry + sh; g[i] = cg + cry + sh; b[i] = cb + cry * 0.8f + sh * 0.7f;
				}
			}
			for (int k = 0; k < 16; ++k) {
				int loops = 1 + (k % 2);
				float x0 = hash(k, 1, 77) * FW;
				float y0 = hash(k, 2, 77);
				float y = frac(y0 + t * loops) * (FH + 8) - 4;
				float x = x0 + 2.5f * (float) Math.sin(2 * Math.PI * (t * loops + hash(k, 3, 77)));
				int size = k % 3 == 0 ? 3 : (k % 3 == 1 ? 2 : 1);
				flake(r, g, b, Math.round(x), Math.round(y), size, 0.92f);
			}
		}
	}

	/** A sunset over turquoise waves rolling across. */
	static final class Tropical extends Art {
		Tropical() {
			lining = 0xFF06343C;
			trim = 0xFF45E3C6;
		}

		void frame(float t, float[] r, float[] g, float[] b) {
			int sea = 30;
			for (int y = 0; y < FH; ++y) {
				for (int x = 0; x < FW; ++x) {
					int i = y * FW + x;
					float cr, cg, cb;
					if (y < sea) {
						float v = y / (float) sea;
						cr = lerp(0.42f, 1.0f, v); cg = lerp(0.16f, 0.46f, v); cb = lerp(0.45f, 0.38f, v);
						// the sun, striped at the bottom
						float dx = x - 19.5f, dy = y - 21f, d = (float) Math.sqrt(dx * dx + dy * dy);
						if (d < 10.5f) {
							boolean cut = y > 18 && ((y - 18) % 4 == 0 || (y > 24 && (y - 18) % 4 == 1));
							if (!cut) {
								float k = Math.min(1f, (10.5f - d) * 1.2f);
								cr = lerp(cr, 1.0f, k); cg = lerp(cg, lerp(0.92f, 0.62f, (y - 11) / 10f), k); cb = lerp(cb, 0.42f, k);
							}
						} else if (d < 15f) {
							float k = (15f - d) / 4.5f * 0.25f;
							cr += k; cg += k * 0.7f; cb += k * 0.3f;
						}
					} else {
						float v = (y - sea) / (float) (FH - sea);
						cr = lerp(0.04f, 0.01f, v); cg = lerp(0.48f, 0.17f, v); cb = lerp(0.52f, 0.24f, v);
						// the sun's reflection, broken by the waves
						float w = 6f - v * 3f;
						if (Math.abs(x - 19.5f) < w) {
							float ripple = (float) Math.sin(2 * Math.PI * (y * 0.33f - t * 2) + Math.sin(x * 0.7f) * 2);
							if (ripple > 0.45f) { float k = (1f - v) * 0.7f; cr += k; cg += k * 0.75f; cb += k * 0.3f; }
						}
					}
					r[i] = cr; g[i] = cg; b[i] = cb;
				}
			}
			// wave crests rolling across (alternate rows go opposite ways)
			for (int j = 0; j < 7; ++j) {
				float yj = sea + 1 + j * 5.2f;
				int dir = j % 2 == 0 ? 1 : -1;
				float bright = 1f - j * 0.1f;
				for (int x = 0; x < FW; ++x) {
					float ph = 2f * (float) Math.PI * (x / 20f + dir * t + j * 0.31f);
					float s = (float) Math.sin(ph);
					int y = Math.round(yj - 1.6f * s);
					add(r, g, b, x, y, 0.25f * bright, 0.9f * bright, 0.78f * bright, 0.85f);
					if (s > 0.8f) add(r, g, b, x, y - 1, 1f, 1f, 1f, 0.75f * bright); // foam on top of the crest
					add(r, g, b, x, y + 1, 0.1f, 0.5f, 0.45f, 0.35f * bright);
				}
			}
			// the horizon line
			for (int x = 0; x < FW; ++x) add(r, g, b, x, sea, 1f, 0.75f, 0.5f, 0.5f);
		}
	}

	/** Charcoal with glowing lava cracks that pulse upwards, and rising sparks. */
	static final class Ember extends Art {
		final float[] crack = new float[FW * FH];
		final float[] cell = new float[FW * FH];

		Ember() {
			lining = 0xFF1C0C06;
			trim = 0xFFFF7A2C;
			// cracks: the borders of a cell pattern (Voronoi), worked out once
			int n = 15;
			float[] px = new float[n], py = new float[n];
			for (int k = 0; k < n; ++k) {
				px[k] = hash(k, 5, 31) * FW;
				py[k] = hash(k, 6, 31) * FH;
			}
			for (int y = 0; y < FH; ++y) {
				for (int x = 0; x < FW; ++x) {
					float wx = x + (noise(x * 0.2f, y * 0.2f, 3) - 0.5f) * 5f;
					float wy = y + (noise(x * 0.2f, y * 0.2f, 4) - 0.5f) * 5f;
					float d1 = 1e9f, d2 = 1e9f; int c1 = 0;
					for (int k = 0; k < n; ++k) {
						float dx = wx - px[k], dy = wy - py[k], d = (float) Math.sqrt(dx * dx + dy * dy);
						if (d < d1) { d2 = d1; d1 = d; c1 = k; } else if (d < d2) d2 = d;
					}
					crack[y * FW + x] = d2 - d1;
					cell[y * FW + x] = hash(c1, 9, 31);
				}
			}
		}

		void frame(float t, float[] r, float[] g, float[] b) {
			for (int y = 0; y < FH; ++y) {
				for (int x = 0; x < FW; ++x) {
					int i = y * FW + x;
					float c = cell[i];
					float n = noise(x * 0.3f, y * 0.3f, 8);
					float cr = 0.09f + c * 0.05f + n * 0.04f, cg = 0.06f + c * 0.025f + n * 0.02f, cb = 0.05f + n * 0.015f;
					float d = crack[i];
					// the glow travels up the cape (twice per loop), each crack a little out of step
					float pulse = 0.55f + 0.45f * (float) Math.sin(2 * Math.PI * (2 * t + y / (float) FH + c * 0.5f));
					if (d < 1.3f) {
						float k = pulse * (1f - d / 1.3f * 0.35f);
						cr = lerp(0.55f, 1.0f, k); cg = lerp(0.12f, 0.78f, k * k); cb = lerp(0.02f, 0.25f, k * k * k);
					} else if (d < 4.5f) {
						float k = (1f - (d - 1.3f) / 3.2f);
						k = k * k * pulse * 0.55f;
						cr += k; cg += k * 0.32f; cb += k * 0.06f;
					}
					r[i] = cr; g[i] = cg; b[i] = cb;
				}
			}
			for (int k = 0; k < 9; ++k) {
				int loops = 1 + (k % 3 == 0 ? 1 : 0);
				float f = frac(hash(k, 1, 51) + t * loops);
				float y = FH + 3 - f * (FH + 6);
				float x = hash(k, 2, 51) * FW + 2f * (float) Math.sin(2 * Math.PI * (f * 2 + hash(k, 3, 51)));
				float a = (float) Math.sin(Math.PI * f);
				add(r, g, b, Math.round(x), Math.round(y), 1f, 0.85f, 0.35f, a);
				add(r, g, b, Math.round(x), Math.round(y) + 1, 1f, 0.45f, 0.1f, a * 0.5f);
			}
		}
	}

	/** A night sky with northern-lights bands slowly changing colour, over pine trees. */
	static final class Aurora extends Art {
		private final float[] tmp = new float[3];

		Aurora() {
			lining = 0xFF070C22;
			trim = 0xFF6CF2B4;
		}

		void frame(float t, float[] r, float[] g, float[] b) {
			for (int y = 0; y < FH; ++y) {
				for (int x = 0; x < FW; ++x) {
					int i = y * FW + x;
					float v = y / (float) FH;
					float cr = lerp(0.02f, 0.05f, v), cg = lerp(0.03f, 0.08f, v), cb = lerp(0.10f, 0.20f, v);
					// stars
					float h = hash(x, y, 101);
					if (h > 0.985f && y < 44) {
						int loops = 1 + (int) (hash(x, y, 102) * 3);
						float tw = 0.45f + 0.55f * (float) Math.sin(2 * Math.PI * (t * loops + hash(x, y, 103)));
						cr += 0.7f * tw; cg += 0.7f * tw; cb += 0.75f * tw;
					}
					// three curtains of light
					for (int l = 0; l < 3; ++l) {
						float cx = x / (float) FW;
						float cy = 14 + l * 8 + 4.5f * (float) Math.sin(2 * Math.PI * (cx + t + l * 0.3f))
								+ 2f * (float) Math.sin(2 * Math.PI * (cx * 2 - 2 * t + l * 0.7f));
						float dy = y - cy;
						float k = dy < 0 ? (float) Math.exp(dy / (7f + l * 2f)) : (float) Math.exp(-sq(dy / 1.8f));
						float rays = 0.55f + 0.45f * (float) Math.sin(x * 0.9f + 2 * Math.PI * t + l * 2.1f);
						k *= rays * (0.75f - l * 0.12f);
						// green -> cyan -> violet, drifting with time
						float hue = 0.36f + 0.14f * l / 2f + 0.1f * (float) Math.sin(2 * Math.PI * (t + l / 3f)) + (dy < 0 ? -dy * 0.006f : 0f);
						hsvInto(hue, 0.75f, 1f, tmp);
						cr += tmp[0] * k; cg += tmp[1] * k; cb += tmp[2] * k;
					}
					// pine trees along the bottom
					float tree = treeLine(x);
					if (y >= tree) { cr = 0.008f; cg = 0.015f; cb = 0.03f; }
					r[i] = cr; g[i] = cg; b[i] = cb;
				}
			}
		}

		static float treeLine(int x) {
			float base = FH - 5;
			float best = base;
			int[] tx = { 3, 10, 17, 24, 31, 37 };
			int[] th = { 10, 7, 12, 8, 11, 7 };
			for (int k = 0; k < tx.length; ++k) {
				float top = FH - 5 - th[k] - 2;
				float y = top + Math.abs(x - tx[k]) * 2.2f;
				if (y < best) best = y;
			}
			return best;
		}
	}

	/** Deep space: a drifting nebula, twinkling stars and a shooting star. */
	static final class Galaxy extends Art {
		Galaxy() {
			lining = 0xFF0A0618;
			trim = 0xFFB38CFF;
		}

		void frame(float t, float[] r, float[] g, float[] b) {
			float ox = 1.6f * (float) Math.cos(2 * Math.PI * t), oy = 1.6f * (float) Math.sin(2 * Math.PI * t);
			for (int y = 0; y < FH; ++y) {
				for (int x = 0; x < FW; ++x) {
					int i = y * FW + x;
					float cr = 0.02f, cg = 0.012f, cb = 0.06f;
					// the nebula: moves round in a circle, so the loop is seamless
					float n = fbm(x * 0.07f + ox, y * 0.055f + oy, 21);
					float m = fbm(x * 0.05f - oy, y * 0.05f + ox, 22);
					float k = smooth(0.42f, 0.82f, n);
					float pr = lerp(0.36f, 0.86f, m), pg = lerp(0.14f, 0.22f, m), pb = lerp(0.66f, 0.58f, m);
					cr += pr * k * 0.85f; cg += pg * k * 0.85f; cb += pb * k * 0.85f;
					float core = smooth(0.72f, 0.95f, n);
					cr += core * 0.35f; cg += core * 0.25f; cb += core * 0.35f;
					float h = hash(x, y, 201);
					if (h > 0.978f) {
						int loops = 1 + (int) (hash(x, y, 202) * 3);
						float tw = 0.35f + 0.65f * Math.max(0f, (float) Math.sin(2 * Math.PI * (t * loops + hash(x, y, 203))));
						cr += 0.8f * tw; cg += 0.8f * tw; cb += 0.9f * tw;
					}
					r[i] = cr; g[i] = cg; b[i] = cb;
				}
			}
			int[][] big = { { 7, 9 }, { 30, 22 }, { 14, 44 }, { 33, 55 } };
			for (int k = 0; k < big.length; ++k) {
				float tw = 0.5f + 0.5f * (float) Math.sin(2 * Math.PI * (t * (1 + k % 2) + k * 0.29));
				sparkle(r, g, b, big[k][0], big[k][1], 2, 0.9f * tw, 0.9f, 0.9f, 1f);
			}
			// a shooting star once per loop
			if (t > 0.55f && t < 0.75f) {
				float f = (t - 0.55f) / 0.2f;
				float hx = lerp(36, 4, f), hy = lerp(6, 30, f);
				for (int s = 0; s < 12; ++s) {
					float a = (1f - s / 12f) * (float) Math.sin(Math.PI * f);
					add(r, g, b, Math.round(hx + s * 1.33f), Math.round(hy - s * 1f), 1f, 0.95f, 0.85f, a);
				}
			}
		}
	}

	/** Circuit tracks with pulses of light running along them. */
	static final class Circuit extends Art {
		final int[] pathPix;   // x | y << 8 | path << 16, in order along each path
		final float[] pathS;   // how far along its path each pixel is
		final int[] pathLen;
		final int count;

		Circuit() {
			lining = 0xFF06140F;
			trim = 0xFF2FD39A;
			java.util.ArrayList<int[]> pix = new java.util.ArrayList<>();
			int paths = 8;
			pathLen = new int[paths];
			java.util.Random rnd = new java.util.Random(4242L);
			boolean[] used = new boolean[10 * 16];
			for (int p = 0; p < paths; ++p) {
				int cx = 1 + rnd.nextInt(8), cy = p % 2 == 0 ? 0 : 2 + rnd.nextInt(4);
				int len = 0, s = 0;
				int steps = 10 + rnd.nextInt(10);
				int lastDir = 0;
				for (int k = 0; k < steps; ++k) {
					used[cy * 10 + cx] = true;
					int dir; // 0 down, 1 left, 2 right
					int roll = rnd.nextInt(10);
					dir = roll < 6 || lastDir != 0 ? 0 : (roll < 8 ? 1 : 2);
					int nx = cx + (dir == 1 ? -1 : dir == 2 ? 1 : 0), ny = cy + (dir == 0 ? 1 : 0);
					if (nx < 0 || nx > 9 || ny > 15) break;
					// draw the line between the two cell centres, 4 pixels long
					for (int q = 0; q < S; ++q) {
						int x = cx * S + 2 + (nx - cx) * q, y = cy * S + 2 + (ny - cy) * q;
						pix.add(new int[] { x, y, p, s++ });
					}
					cx = nx; cy = ny; lastDir = dir;
					len = s;
				}
				pathLen[p] = Math.max(1, len);
			}
			count = pix.size();
			pathPix = new int[count];
			pathS = new float[count];
			for (int k = 0; k < count; ++k) {
				int[] q = pix.get(k);
				pathPix[k] = q[0] | (q[1] << 8) | (q[2] << 16);
				pathS[k] = q[3];
			}
		}

		void frame(float t, float[] r, float[] g, float[] b) {
			for (int y = 0; y < FH; ++y) {
				for (int x = 0; x < FW; ++x) {
					int i = y * FW + x;
					float v = y / (float) FH;
					float cr = lerp(0.03f, 0.015f, v), cg = lerp(0.11f, 0.06f, v), cb = lerp(0.08f, 0.045f, v);
					if (x % S == 2 && y % S == 2) { cr += 0.03f; cg += 0.08f; cb += 0.06f; }
					r[i] = cr; g[i] = cg; b[i] = cb;
				}
			}
			for (int k = 0; k < count; ++k) {
				int x = pathPix[k] & 255, y = (pathPix[k] >> 8) & 255, p = pathPix[k] >> 16;
				float L = pathLen[p];
				int loops = 1 + p % 2;
				float head = frac(t * loops + p * 0.37f) * (L + 14f);
				float dd = head - pathS[k];
				float bri = dd >= 0f && dd < 14f ? (1f - dd / 14f) : 0f;
				bri = bri * bri;
				set(r, g, b, x, y, lerp(0.08f, 0.85f, bri), lerp(0.34f, 1f, bri), lerp(0.25f, 0.92f, bri));
				if (bri > 0.3f) {
					float a = (bri - 0.3f) * 0.8f;
					add(r, g, b, x + 1, y, 0.25f, 0.95f, 0.75f, a);
					add(r, g, b, x - 1, y, 0.25f, 0.95f, 0.75f, a);
					add(r, g, b, x, y + 1, 0.25f, 0.95f, 0.75f, a);
					add(r, g, b, x, y - 1, 0.25f, 0.95f, 0.75f, a);
				}
			}
			// the chip in the middle
			for (int y = 26; y < 38; ++y) {
				for (int x = 12; x < 28; ++x) {
					boolean edge = x == 12 || x == 27 || y == 26 || y == 37;
					set(r, g, b, x, y, edge ? 0.14f : 0.03f, edge ? 0.55f : 0.07f, edge ? 0.4f : 0.06f);
				}
			}
			// its pins, and a light that breathes in the middle
			float glow = 0.5f + 0.5f * (float) Math.sin(2 * Math.PI * t);
			for (int k = 0; k < 4; ++k) {
				for (int d = 1; d <= 2; ++d) {
					set(r, g, b, 14 + k * 4, 26 - d, 0.6f, 0.75f, 0.7f);
					set(r, g, b, 14 + k * 4, 37 + d, 0.6f, 0.75f, 0.7f);
				}
			}
			for (int y = 30; y < 34; ++y)
				for (int x = 18; x < 22; ++x)
					set(r, g, b, x, y, lerp(0.1f, 0.6f, glow), lerp(0.4f, 1f, glow), lerp(0.3f, 0.85f, glow));
			// pads where the tracks start
			for (int p = 0; p < pathLen.length; ++p) {
				for (int k = 0; k < count; ++k) {
					if ((pathPix[k] >> 16) == p) {
						int x = pathPix[k] & 255, y = (pathPix[k] >> 8) & 255;
						for (int dy = -1; dy <= 1; ++dy)
							for (int dx = -1; dx <= 1; ++dx)
								set(r, g, b, x + dx, y + dy, 0.2f, 0.66f, 0.5f);
						break;
					}
				}
			}
		}
	}

	/**
	 * The Maker's cape: a pattern in two colours, an emblem on a round badge, and a motion.
	 * Every pattern repeats every 16 (or 10) pixels, so "flow" slides it exactly one repeat per
	 * loop and the animation joins up.
	 */
	static final class Custom extends Art {
		final int pat, emb, mot;
		final float r1, g1, b1, r2, g2, b2;

		Custom(String spec, int accent) {
			String[] p = spec.split("\\.", -1);
			pat = Math.max(0, idx(PATTERNS, p[0]));
			int c1 = color(p[1], accent), c2 = color(p[2], accent);
			emb = Math.max(0, idx(EMBLEMS, p[3]));
			mot = Math.max(0, idx(MOTIONS, p[4]));
			r1 = ((c1 >> 16) & 255) / 255f; g1 = ((c1 >> 8) & 255) / 255f; b1 = (c1 & 255) / 255f;
			r2 = ((c2 >> 16) & 255) / 255f; g2 = ((c2 >> 8) & 255) / 255f; b2 = (c2 & 255) / 255f;
			lining = mixRgb(0xFF000000 | c1, 0xFF0A0A0C, 0.62f);
			trim = 0xFF000000 | mixRgb(0xFF000000 | c2, 0xFF000000, 0.25f);
		}

		static int color(String id, int accent) {
			int i = idx(PALETTE, id);
			return i <= 0 ? (accent & 0xFFFFFF) : PALETTE_RGB[i];
		}

		/** How much of colour 2 a pixel gets (0..1). */
		float pattern(int x, int y, float t) {
			boolean flow = mot == 2;
			float o16 = flow ? t * 16f : 0f;
			switch (pat) {
			case 1: // fade
				return flow ? 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * (y / (float) FH - t)) : y / (float) (FH - 1);
			case 2: { // stripes
				float v = (x + (flow ? t * 10f : 0f)) % 10f;
				return edge(v, 5f, 10f);
			}
			case 3: { // chevron
				float v = (y + Math.abs(x - FW / 2f + 0.5f) * 0.9f - o16 + 64f) % 16f;
				return edge(v, 8f, 16f);
			}
			case 4: { // checks
				int cx = x / 8, cy = (int) Math.floor((y + o16) / 8f);
				return ((cx + cy) & 1) == 0 ? 0f : 1f;
			}
			case 5: { // waves
				float v = (y + 3f * (float) Math.sin(2 * Math.PI * x / 20f) + o16 + 64f) % 16f;
				return edge(v, 8f, 16f);
			}
			case 7: // split: a diagonal
				return x * 1.25f + y > FH * 0.72f + FW * 0.4f ? 1f : 0f;
			default: // solid, stars (the stars are drawn on top)
				return 0f;
			}
		}

		/** 1 in the first half of a repeat, 0 in the second, with a one-pixel soft edge. */
		static float edge(float v, float half, float period) {
			if (v < 0.5f) return 0.5f + v;
			if (v < half - 0.5f) return 1f;
			if (v < half + 0.5f) return half + 0.5f - v;
			if (v > period - 0.5f) return v - (period - 0.5f);
			return 0f;
		}

		void frame(float t, float[] r, float[] g, float[] b) {
			float pulse = mot == 3 ? 0.9f + 0.18f * (float) Math.sin(2 * Math.PI * 2 * t) : 1f;
			for (int y = 0; y < FH; ++y) {
				float v = y / (float) FH;
				float shade = (1.06f - 0.22f * v) * pulse;
				for (int x = 0; x < FW; ++x) {
					int i = y * FW + x;
					float k = pattern(x, y, t);
					float cr = lerp(r1, r2, k), cg = lerp(g1, g2, k), cb = lerp(b1, b2, k);
					// a little cloth texture
					float n = 0.94f + 0.06f * hash(x >> 1, y >> 1, 9);
					r[i] = cr * shade * n; g[i] = cg * shade * n; b[i] = cb * shade * n;
				}
			}
			if (pat == 6) { // stars in colour 2, drifting down when flowing
				for (int k = 0; k < 14; ++k) {
					float x = 3 + hash(k, 1, 51) * (FW - 6);
					float y0 = hash(k, 2, 51);
					float y = (mot == 2 ? frac(y0 + t) : y0) * (FH + 6) - 3;
					int arm = k % 3 == 0 ? 2 : 1;
					sparkle(r, g, b, Math.round(x), Math.round(y), arm, 1f, r2, g2, b2);
					set(r, g, b, Math.round(x), Math.round(y), r2, g2, b2);
				}
			}
			if (emb > 0) badge(r, g, b);
			if (mot == 1) { // shine: a soft band crossing once per loop
				for (int y = 0; y < FH; ++y) {
					for (int x = 0; x < FW; ++x) {
						float s = (x * 0.55f + y) / (FH + FW * 0.55f);
						float sh = (float) Math.exp(-sq((s - (t * 1.7f - 0.35f)) / 0.05f)) * 0.3f;
						int i = y * FW + x;
						r[i] += sh; g[i] += sh; b[i] += sh;
					}
				}
			} else if (mot == 4) { // sparkle
				for (int k = 0; k < 9; ++k) {
					float tw = Math.max(0f, (float) Math.sin(2 * Math.PI * (t * 2 + k * 0.37)));
					tw = tw * tw * tw;
					sparkle(r, g, b, 3 + (int) (hash(k, 7, 52) * (FW - 6)), 3 + (int) (hash(k, 8, 52) * (FH - 6)), 2, tw, 1f, 1f, 1f);
				}
			}
		}

		/** The emblem on a round badge in the middle (readable on any pattern). */
		void badge(float[] r, float[] g, float[] b) {
			float cx = FW / 2f - 0.5f, cy = FH * 0.42f;
			float br = r1 * 0.4f, bg = g1 * 0.4f, bb = b1 * 0.4f;
			// the emblem is colour 2, or white if colour 2 is too dark to see on the badge
			float er = r2, eg = g2, eb = b2;
			float le = r2 * 0.3f + g2 * 0.59f + b2 * 0.11f, lb = br * 0.3f + bg * 0.59f + bb * 0.11f;
			if (le - lb < 0.3f) { er = 0.95f; eg = 0.95f; eb = 0.92f; }
			for (int y = 0; y < FH; ++y) {
				for (int x = 0; x < FW; ++x) {
					float d = (float) Math.sqrt(sq(x - cx) + sq(y - cy));
					if (d > 14.5f) continue;
					int i = y * FW + x;
					if (d > 13f) { r[i] = er * 0.85f; g[i] = eg * 0.85f; b[i] = eb * 0.85f; } // the ring
					else { r[i] = lerp(r[i], br, 0.86f); g[i] = lerp(g[i], bg, 0.86f); b[i] = lerp(b[i], bb, 0.86f); }
				}
			}
			String[] bmp = EMBLEM_BITMAPS[emb - 1];
			int ox = Math.round(cx - 10.5f), oy = Math.round(cy - 10.5f);
			for (int by = 0; by < 11; ++by) {
				for (int bx = 0; bx < 11; ++bx) {
					if (bmp[by].charAt(bx) != '#') continue;
					float lit = 1.08f - by * 0.03f; // a little lighter at the top
					for (int sy = 0; sy < 2; ++sy)
						for (int sx = 0; sx < 2; ++sx)
							set(r, g, b, ox + bx * 2 + sx, oy + by * 2 + sy, er * lit, eg * lit, eb * lit);
				}
			}
		}
	}

	/** The emblems, 11x11 each (drawn twice the size). */
	static final String[][] EMBLEM_BITMAPS = {
		{ ".....#.....", ".....#.....", "....###....", "....###....", "###########", ".#########.", "..#######..", "...#####...", "...##.##...", "..##...##..", "..#.....#.." },
		{ "...........", ".###...###.", "###########", "###########", "###########", ".#########.", "..#######..", "...#####...", "....###....", ".....#.....", "..........." },
		{ ".....#.....", "....###....", "...#####...", "..#######..", ".#########.", "###########", ".#########.", "..#######..", "...#####...", "....###....", ".....#....." },
		{ "......###..", ".....###...", "....###....", "...###.....", "..########.", ".......###.", "......###..", ".....###...", "....###....", "...##......", "..#........" },
		{ "....####...", "..#####....", ".#####.....", ".####......", "#####......", "#####......", "#####......", ".####......", ".#####.....", "..#####....", "....####..." },
		{ "...........", "#....#....#", "##...#...##", "##..###..##", "###.###.###", "###########", "###########", "###########", "...........", "###########", "..........." },
		{ ".........##", "........###", ".......###.", "......###..", ".....###...", "#...###....", "##.###.....", ".####......", "..##.......", ".#..#......", "#....#....." },
	};

	// ------------------------------------------------------------------ drawing helpers

	static void set(float[] r, float[] g, float[] b, int x, int y, float cr, float cg, float cb) {
		if (x < 0 || y < 0 || x >= FW || y >= FH) return;
		int i = y * FW + x;
		r[i] = cr; g[i] = cg; b[i] = cb;
	}

	/** Mixes a colour in (a = how much). */
	static void add(float[] r, float[] g, float[] b, int x, int y, float cr, float cg, float cb, float a) {
		if (x < 0 || y < 0 || x >= FW || y >= FH || a <= 0f) return;
		int i = y * FW + x;
		r[i] = lerp(r[i], cr, a); g[i] = lerp(g[i], cg, a); b[i] = lerp(b[i], cb, a);
	}

	static void flake(float[] r, float[] g, float[] b, int x, int y, int size, float a) {
		add(r, g, b, x, y, 1f, 1f, 1f, a);
		for (int k = 1; k <= size; ++k) {
			float f = a * (k == size && size > 1 ? 0.6f : 0.85f);
			add(r, g, b, x + k, y, 0.9f, 0.97f, 1f, f);
			add(r, g, b, x - k, y, 0.9f, 0.97f, 1f, f);
			add(r, g, b, x, y + k, 0.9f, 0.97f, 1f, f);
			add(r, g, b, x, y - k, 0.9f, 0.97f, 1f, f);
		}
		if (size >= 3) {
			add(r, g, b, x + 1, y + 1, 0.9f, 0.97f, 1f, a * 0.5f);
			add(r, g, b, x - 1, y - 1, 0.9f, 0.97f, 1f, a * 0.5f);
			add(r, g, b, x + 1, y - 1, 0.9f, 0.97f, 1f, a * 0.5f);
			add(r, g, b, x - 1, y + 1, 0.9f, 0.97f, 1f, a * 0.5f);
		}
	}

	static void sparkle(float[] r, float[] g, float[] b, int x, int y, int arm, float a, float cr, float cg, float cb) {
		if (a <= 0.02f) return;
		add(r, g, b, x, y, 1f, 1f, 1f, a);
		for (int k = 1; k <= arm; ++k) {
			float f = a * (1f - k / (arm + 1f));
			add(r, g, b, x + k, y, cr, cg, cb, f);
			add(r, g, b, x - k, y, cr, cg, cb, f);
			add(r, g, b, x, y + k, cr, cg, cb, f);
			add(r, g, b, x, y - k, cr, cg, cb, f);
		}
	}

	static int argb(float r, float g, float b) {
		return 0xFF000000 | (c8(r) << 16) | (c8(g) << 8) | c8(b);
	}

	static int c8(float v) {
		int i = (int) (v * 255f + 0.5f);
		return i < 0 ? 0 : (i > 255 ? 255 : i);
	}

	static int scale(int c, float k) {
		return 0xFF000000 | (c8(((c >> 16) & 255) / 255f * k) << 16) | (c8(((c >> 8) & 255) / 255f * k) << 8)
				| c8((c & 255) / 255f * k);
	}

	static int mixRgb(int a, int b, float k) {
		float r = lerp(((a >> 16) & 255) / 255f, ((b >> 16) & 255) / 255f, k);
		float g = lerp(((a >> 8) & 255) / 255f, ((b >> 8) & 255) / 255f, k);
		float bl = lerp((a & 255) / 255f, (b & 255) / 255f, k);
		return argb(r, g, bl);
	}

	static float lerp(float a, float b, float k) {
		return a + (b - a) * k;
	}

	static float sq(float v) {
		return v * v;
	}

	static float frac(float v) {
		return v - (float) Math.floor(v);
	}

	static float smooth(float e0, float e1, float v) {
		float k = Math.max(0f, Math.min(1f, (v - e0) / (e1 - e0)));
		return k * k * (3f - 2f * k);
	}

	static float hash(int x, int y, int seed) {
		int h = x * 374761393 + y * 668265263 + seed * 982451653;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (float) 0x1000000;
	}

	static float noise(float x, float y, int seed) {
		int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
		float fx = x - xi, fy = y - yi;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		float a = hash(xi, yi, seed), b = hash(xi + 1, yi, seed), c = hash(xi, yi + 1, seed), d = hash(xi + 1, yi + 1, seed);
		return lerp(lerp(a, b, fx), lerp(c, d, fx), fy);
	}

	static float fbm(float x, float y, int seed) {
		return noise(x, y, seed) * 0.55f + noise(x * 2.1f, y * 2.1f, seed + 1) * 0.3f + noise(x * 4.3f, y * 4.3f, seed + 2) * 0.15f;
	}

	/** Same as {@link #hsv} but writes into out (no new array: used per pixel). */
	static void hsvInto(float h, float s, float v, float[] out) {
		h = frac(h) * 6f;
		int i = (int) h;
		float f = h - i, p = v * (1 - s), q = v * (1 - s * f), u = v * (1 - s * (1 - f));
		switch (i) {
		case 0: out[0] = v; out[1] = u; out[2] = p; break;
		case 1: out[0] = q; out[1] = v; out[2] = p; break;
		case 2: out[0] = p; out[1] = v; out[2] = u; break;
		case 3: out[0] = p; out[1] = q; out[2] = v; break;
		case 4: out[0] = u; out[1] = p; out[2] = v; break;
		default: out[0] = v; out[1] = p; out[2] = q; break;
		}
	}

	static float[] hsv(float h, float s, float v) {
		h = frac(h) * 6f;
		int i = (int) h;
		float f = h - i, p = v * (1 - s), q = v * (1 - s * f), u = v * (1 - s * (1 - f));
		switch (i) {
		case 0: return new float[] { v, u, p };
		case 1: return new float[] { q, v, p };
		case 2: return new float[] { p, v, u };
		case 3: return new float[] { p, q, v };
		case 4: return new float[] { u, p, v };
		default: return new float[] { v, p, q };
		}
	}
}
