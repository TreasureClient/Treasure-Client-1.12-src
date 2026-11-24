package net.treasureclient.cos;

/**
 * Treasure Client cosmetics: the shapes of the hats and boots (all original designs).
 *
 * Plain Java on purpose (no game classes), so they can be drawn into pictures and checked
 * without starting the game; {@link TCWear} sends them to the game.
 *
 * Coordinates are model pixels with y pointing down, like the player model: the head is
 * x -4..4, y -8..0, z -4..4 (its top at y = -8, the face towards -z); a leg is x -2..2,
 * y 0..12, z -2..2 (the foot at y = 12).
 */
public final class TCWearShapes {

	private TCWearShapes() {
	}

	public static boolean hatGlows(String id) {
		return "crown".equals(id) || "wizard".equals(id) || "angel".equals(id) || "horns".equals(id);
	}

	public static boolean customHatGlows(String top, String trim) {
		return "antenna".equals(top) || "halo".equals(top) || "gems".equals(trim) || "stars".equals(trim);
	}

	public static boolean customBootsGlow(String extra) {
		return "crystals".equals(extra) || "cracks".equals(extra) || "nozzle".equals(extra);
	}

	public static boolean bootsGlow(String id) {
		return "rocket".equals(id) || "frost".equals(id) || "magma".equals(id);
	}

	// ------------------------------------------------------------------ the hats

	/** A hat's own colour (used when its colour is "natural"). */
	public static int naturalHat(String id) {
		switch (id) {
		case "crown": return 0xF2C14E;
		case "tophat": return 0xB3263A;
		case "wizard": return 0x3E2C86;
		case "beanie": return 0xE04C4C;
		case "catears": return 0x2E2E34;
		case "horns": return 0x6E2222;
		case "propeller": return 0xE8434F;
		case "angel": return 0xFFE7A0;
		default: return 0xFFFFFF;
		}
	}

	/** Draws a hat; main = its colour, natural = true if that's its own colour. */
	public static void drawHat(String id, int main, boolean natural, float t) {
		switch (id) {
		case "crown": crown(main, t); break;
		case "tophat": topHat(main); break;
		case "wizard": wizard(main, t); break;
		case "beanie": beanie(main); break;
		case "catears": catEars(main, t); break;
		case "horns": horns(main, t); break;
		case "propeller": propeller(natural, main, t); break;
		case "angel": angel(main, t); break;
		default: break;
		}
	}

	/** A gold crown: four walls with points, velvet inside, gems that glow softly. */
	private static void crown(int gold, float t) {
		int dark = shade(gold, 0.72f), velvet = 0x8E1F3A;
		float o = 4.7f, i = 4.1f;
		box(-o, -10.2f, -o, o, -8f, -i, gold);
		box(-o, -10.2f, i, o, -8f, o, gold);
		box(-o, -10.2f, -i, -i, -8f, i, gold);
		box(i, -10.2f, -i, o, -8f, i, gold);
		box(-o - 0.06f, -8.7f, -o - 0.06f, o + 0.06f, -8f, o + 0.06f, dark); // the rim
		box(-i, -9.3f, -i, i, -8f, i, velvet);
		box(-2.8f, -9.9f, -2.8f, 2.8f, -9.3f, 2.8f, velvet);
		float[] at = { -3.3f, 0f, 3.3f };
		for (int k = 0; k < 3; ++k) {
			float top = k == 1 ? -12.7f : -11.8f, a = at[k];
			// front and back walls
			box(a - 0.7f, top, -o, a + 0.7f, -10.2f, -i, gold);
			box(a - 0.7f, top, i, a + 0.7f, -10.2f, o, gold);
			// side walls
			box(-o, top, a - 0.7f, -i, -10.2f, a + 0.7f, gold);
			box(i, top, a - 0.7f, o, -10.2f, a + 0.7f, gold);
			if (k == 1) {
				box(-0.45f, top - 0.8f, -o - 0.05f, 0.45f, top, -i + 0.05f, gold);
			}
		}
		// gems
		float p = 0.8f + 0.2f * (float) Math.sin(t * 2.6f);
		glowBox(-0.75f, -9.8f, -o - 0.25f, 0.75f, -8.6f, -o, shade(0xFF3B5C, p));
		glowBox(-3.3f, -9.6f, -o - 0.2f, -2.5f, -8.8f, -o, shade(0x5AB8FF, p));
		glowBox(2.5f, -9.6f, -o - 0.2f, 3.3f, -8.8f, -o, shade(0x5AB8FF, p));
		glowBox(-o - 0.2f, -9.7f, -0.6f, -o, -8.7f, 0.6f, shade(0x56E39A, p));
		glowBox(o, -9.7f, -0.6f, o + 0.2f, -8.7f, 0.6f, shade(0x56E39A, p));
		glowBox(-0.6f, -9.7f, o, 0.6f, -8.7f, o + 0.2f, shade(0xFF3B5C, p));
	}

	/** A tall black top hat with a coloured band and a gold buckle, worn at a slight angle. */
	private static void topHat(int band) {
		int body = 0x1C1C21, edge = 0x2A2A31;
		rot(0f, -8f, 0f, 0f, 0f, 5f);
		box(-6.2f, -8.7f, -6.2f, 6.2f, -8f, 6.2f, edge); // the brim
		box(-6.2f, -9.2f, -6.2f, -5.5f, -8.7f, 6.2f, edge); // curled up at the sides
		box(5.5f, -9.2f, -6.2f, 6.2f, -8.7f, 6.2f, edge);
		box(-3.9f, -15.6f, -3.9f, 3.9f, -8.7f, 3.9f, body);
		box(-4.0f, -10.5f, -4.0f, 4.0f, -8.7f, 4.0f, band);
		box(-0.9f, -10.4f, -4.15f, 0.9f, -8.8f, -4.0f, 0xE8B94A);
		box(-0.4f, -9.95f, -4.2f, 0.4f, -9.25f, -4.1f, 0x1C1C21);
		rotReset();
	}

	/** A pointy wizard hat that bends back and sways, with twinkling stars. */
	private static void wizard(int cloth, float t) {
		box(-6.6f, -8.65f, -6.6f, 6.6f, -8f, 6.6f, shade(cloth, 0.85f));
		box(-4.4f, -9.7f, -4.4f, 4.4f, -8.65f, 4.4f, 0xE8B94A); // the band
		rot(0f, -8.65f, 0f, (float) Math.sin(t * 1.4f) * 3f, 0f, (float) Math.sin(t * 1.1f) * 2f);
		float[][] seg = { // half size, top, bottom, z offset (bends back)
				{ 4.3f, -10.7f, -9.7f, 0f }, { 3.6f, -12.6f, -10.7f, 0.2f }, { 2.9f, -14.4f, -12.6f, 0.6f },
				{ 2.2f, -16f, -14.4f, 1.2f }, { 1.5f, -17.3f, -16f, 2f }, { 0.95f, -18.3f, -17.3f, 3f },
				{ 0.55f, -18.9f, -18.3f, 4.1f }, { 0.35f, -19.1f, -18.6f, 5f } };
		for (float[] s : seg) {
			box(-s[0], s[1], -s[0] + s[3], s[0], s[2], s[0] + s[3], cloth);
		}
		// stars on the front, twinkling one after another: x, y, which part of the cone they're on
		float[][] star = { { -1.9f, -11.6f, 1 }, { 1.5f, -13.5f, 2 }, { -0.8f, -15.2f, 3 }, { 1.1f, -10.2f, 0 } };
		for (int k = 0; k < star.length; ++k) {
			float[] s = star[k];
			float[] part = seg[(int) s[2]];
			float z = part[3] - part[0];
			float tw = 0.55f + 0.45f * (float) Math.max(0, Math.sin(t * 2.2f + k * 1.7f));
			int c = shade(0xFFE58A, tw);
			glowBox(s[0] - 0.55f, s[1] - 0.15f, z - 0.2f, s[0] + 0.55f, s[1] + 0.15f, z, c);
			glowBox(s[0] - 0.15f, s[1] - 0.55f, z - 0.2f, s[0] + 0.15f, s[1] + 0.55f, z, c);
		}
		rotReset();
	}

	/** A knitted beanie with a folded cuff and a pompom. */
	private static void beanie(int knit) {
		box(-4.7f, -9.3f, -4.7f, 4.7f, -5.6f, 4.7f, knit);
		box(-3.8f, -9.9f, -3.8f, 3.8f, -9.3f, 3.8f, knit);
		box(-4.95f, -6.6f, -4.95f, 4.95f, -5f, 4.95f, shade(knit, 0.78f));
		// knitted ribs on the cuff
		for (int k = -4; k <= 4; k += 2) {
			box(k - 0.3f, -6.5f, -5.05f, k + 0.3f, -5.1f, -4.95f, shade(knit, 0.66f));
		}
		box(-1.5f, -12.3f, -1.5f, 1.5f, -9.8f, 1.5f, 0xF2F2F2);
		box(-1.1f, -12.7f, -1.1f, 1.1f, -12.3f, 1.1f, 0xF2F2F2);
	}

	/** Cat ears on a thin headband; now and then one twitches. */
	private static void catEars(int fur, float t) {
		int inner = 0xF29CB8;
		box(-4.65f, -8.45f, -1.2f, 4.65f, -7.95f, -0.3f, fur); // the band over the top
		box(-4.8f, -8.45f, -1.2f, -4.6f, -4.6f, -0.3f, fur);
		box(4.6f, -8.45f, -1.2f, 4.8f, -4.6f, -0.3f, fur);
		for (int s = -1; s <= 1; s += 2) {
			float cx = s * 2.6f, cz = -0.7f;
			float period = s < 0 ? 4.7f : 6.1f;
			float ph = (t + (s < 0 ? 0f : 2.3f)) % period;
			float twitch = ph < 0.35f ? (float) Math.sin(ph / 0.35f * Math.PI) * 16f : 0f;
			rot(cx, -8f, cz, -twitch, 0f, -s * 13f);
			float[][] st = { { 2.0f, -9.4f, -8f }, { 1.6f, -10.8f, -9.4f }, { 1.15f, -12.1f, -10.8f }, { 0.7f, -13.2f, -12.1f }, { 0.3f, -13.9f, -13.2f } };
			for (float[] q : st) {
				box(cx - q[0], q[1], cz - 0.6f, cx + q[0], q[2], cz + 0.6f, fur);
			}
			float[][] in = { { 1.3f, -9.8f, -8.3f }, { 0.95f, -11.1f, -9.8f }, { 0.55f, -12.3f, -11.1f }, { 0.2f, -13f, -12.3f } };
			for (float[] q : in) {
				box(cx - q[0], q[1], cz - 0.74f, cx + q[0], q[2], cz - 0.6f, inner);
			}
			rotReset();
		}
	}

	/** Two curled horns, dark towards the tips, the tips faintly glowing. */
	private static void horns(int base, float t) {
		float[][] seg = { { 3.2f, -8.3f, -2.2f, 0.95f }, { 3.9f, -9.3f, -2.4f, 0.85f }, { 4.6f, -10.3f, -2.5f, 0.75f },
				{ 5.0f, -11.4f, -2.4f, 0.62f }, { 5.1f, -12.4f, -2.0f, 0.5f }, { 4.8f, -13.2f, -1.4f, 0.4f },
				{ 4.3f, -13.7f, -0.8f, 0.3f } };
		int tip = 0x160B0B;
		for (int s = -1; s <= 1; s += 2) {
			for (int k = 0; k < seg.length; ++k) {
				float[] q = seg[k];
				float x = s * q[0], h = q[3];
				int c = mix(base, tip, k / (float) (seg.length - 1));
				box(x - h, q[1] - h, q[2] - h, x + h, q[1] + h, q[2] + h, c);
			}
			float[] q = seg[seg.length - 1];
			float p = 0.5f + 0.25f * (float) Math.sin(t * 2f + s);
			glowBox(s * q[0] - 0.18f, q[1] - 0.48f, q[2] - 0.18f, s * q[0] + 0.18f, q[1] - 0.25f, q[2] + 0.18f, shade(0xFF5A3C, p));
		}
	}

	/** A four-colour cap with a little propeller spinning on top. */
	private static void propeller(boolean natural, int main, float t) {
		int[] q = natural ? new int[] { 0xE8434F, 0xFFD84A, 0x4A8CFF, 0x45C46A } : new int[] { main, shade(main, 0.75f), main, shade(main, 0.75f) };
		float o = 4.65f;
		box(-o, -9f, -o, 0f, -6.8f, 0f, q[0]);
		box(0f, -9f, -o, o, -6.8f, 0f, q[1]);
		box(0f, -9f, 0f, o, -6.8f, o, q[2]);
		box(-o, -9f, 0f, 0f, -6.8f, o, q[3]);
		box(-3.4f, -9.7f, -3.4f, 0f, -9f, 0f, q[0]);
		box(0f, -9.7f, -3.4f, 3.4f, -9f, 0f, q[1]);
		box(0f, -9.7f, 0f, 3.4f, -9f, 3.4f, q[2]);
		box(-3.4f, -9.7f, 0f, 0f, -9f, 3.4f, q[3]);
		box(-3.6f, -7.3f, -7.4f, 3.6f, -6.8f, -o, q[0]); // the peak
		box(-0.3f, -11.2f, -0.3f, 0.3f, -9.7f, 0.3f, 0x9A9AA2);
		box(-0.6f, -11.7f, -0.6f, 0.6f, -11.1f, 0.6f, q[0]);
		rot(0f, -11.4f, 0f, 0f, (t * 760f) % 360f, 0f);
		box(-4.6f, -11.55f, -0.55f, -0.6f, -11.2f, 0.55f, q[1]);
		box(0.6f, -11.55f, -0.55f, 4.6f, -11.2f, 0.55f, q[2]);
		rotReset();
	}

	/** A glowing ring floating above the head, bobbing and slowly turning. */
	private static void angel(int c, float t) {
		float y = -11.4f + 0.35f * (float) Math.sin(t * 2f);
		float spin = (t * 25f) % 360f;
		int n = 18;
		float r = 3.7f, half = (float) (Math.PI * r / n) * 1.12f;
		float p = 0.85f + 0.15f * (float) Math.sin(t * 3f);
		int col = shade(c, p);
		for (int k = 0; k < n; ++k) {
			float a = spin + k * 360f / n;
			rot(0f, y, 0f, 7f, a, 0f);
			glowBox(-half, y - 0.28f, -r - 0.32f, half, y + 0.28f, -r + 0.32f, col);
		}
		rotReset();
	}

	// ------------------------------------------------------------------ the Maker's hat and boots

	/** A hat put together in the Maker: a base, something on top, a trim, two colours. */
	public static void customHat(String base, String top, String trim, int c1, int c2, float t) {
		float topY; // where the top part sits
		float bandLo = 0f, bandHi = 0f, bandR = 0f; // where a stripe or gems go
		switch (base) {
		case "cap":
			box(-4.65f, -9f, -4.65f, 4.65f, -6.8f, 4.65f, c1);
			box(-3.4f, -9.7f, -3.4f, 3.4f, -9f, 3.4f, c1);
			box(-3.6f, -7.3f, -7.4f, 3.6f, -6.8f, -4.65f, trim.equals("stripe") ? c2 : shade(c1, 0.85f));
			topY = -9.7f; bandLo = -7.7f; bandHi = -6.9f; bandR = 4.75f;
			break;
		case "beanie":
			box(-4.7f, -9.3f, -4.7f, 4.7f, -5.6f, 4.7f, c1);
			box(-3.8f, -9.9f, -3.8f, 3.8f, -9.3f, 3.8f, c1);
			box(-4.95f, -6.6f, -4.95f, 4.95f, -5f, 4.95f, shade(c1, 0.78f));
			topY = -9.9f; bandLo = -6.65f; bandHi = -4.95f; bandR = 5.0f;
			break;
		case "brim":
			box(-6f, -8.6f, -6f, 6f, -8f, 6f, shade(c1, 0.85f));
			box(-4f, -12f, -4f, 4f, -8.6f, 4f, c1);
			box(-3.4f, -12.6f, -3.4f, 3.4f, -12f, 3.4f, c1);
			topY = -12.6f; bandLo = -9.8f; bandHi = -8.6f; bandR = 4.1f;
			break;
		case "crown": {
			float o = 4.7f, i = 4.1f;
			box(-o, -10.2f, -o, o, -8f, -i, c1);
			box(-o, -10.2f, i, o, -8f, o, c1);
			box(-o, -10.2f, -i, -i, -8f, i, c1);
			box(i, -10.2f, -i, o, -8f, i, c1);
			box(-i, -9.3f, -i, i, -8f, i, shade(c1, 0.55f));
			float[] at = { -3.3f, 0f, 3.3f };
			for (int k = 0; k < 3; ++k) {
				float tp = k == 1 ? -12.4f : -11.6f, a = at[k];
				box(a - 0.7f, tp, -o, a + 0.7f, -10.2f, -i, c1);
				box(a - 0.7f, tp, i, a + 0.7f, -10.2f, o, c1);
				box(-o, tp, a - 0.7f, -i, -10.2f, a + 0.7f, c1);
				box(i, tp, a - 0.7f, o, -10.2f, a + 0.7f, c1);
			}
			topY = -9.3f; bandLo = -9.0f; bandHi = -8.2f; bandR = 4.8f;
			break;
		}
		case "band":
			box(-4.75f, -7.6f, -4.75f, 4.75f, -6.2f, -4.4f, c1);
			box(-4.75f, -7.6f, 4.4f, 4.75f, -6.2f, 4.75f, c1);
			box(-4.75f, -7.6f, -4.4f, -4.4f, -6.2f, 4.4f, c1);
			box(4.4f, -7.6f, -4.4f, 4.75f, -6.2f, 4.4f, c1);
			topY = -8f; bandLo = -7.2f; bandHi = -6.6f; bandR = 4.8f;
			break;
		default:
			topY = -8f;
			break;
		}
		// the trim
		if (bandR > 0f) {
			switch (trim) {
			case "stripe":
				box(-bandR, bandLo, -bandR, bandR, bandHi, bandR, c2);
				break;
			case "gems": {
				float p = 0.8f + 0.2f * (float) Math.sin(t * 2.6f), y = (bandLo + bandHi) / 2f;
				int g = shade(c2, p);
				glowBox(-0.7f, y - 0.6f, -bandR - 0.25f, 0.7f, y + 0.6f, -bandR, g);
				glowBox(-3.1f, y - 0.4f, -bandR - 0.2f, -2.3f, y + 0.4f, -bandR, g);
				glowBox(2.3f, y - 0.4f, -bandR - 0.2f, 3.1f, y + 0.4f, -bandR, g);
				glowBox(-bandR - 0.2f, y - 0.5f, -0.5f, -bandR, y + 0.5f, 0.5f, g);
				glowBox(bandR, y - 0.5f, -0.5f, bandR + 0.2f, y + 0.5f, 0.5f, g);
				break;
			}
			case "stars": {
				float y = (bandLo + bandHi) / 2f - ("brim".equals(base) ? 1.6f : 0.9f);
				float[] xs = { -2.4f, 0.4f, 2.6f };
				for (int k = 0; k < 3; ++k) {
					float tw = 0.55f + 0.45f * (float) Math.max(0, Math.sin(t * 2.2f + k * 1.9f));
					int g = shade(mix(c2, 0xFFFFFF, 0.35f), tw);
					float x = xs[k], yy = y - (k == 1 ? 0.8f : 0f), z = -bandR;
					glowBox(x - 0.5f, yy - 0.14f, z - 0.2f, x + 0.5f, yy + 0.14f, z, g);
					glowBox(x - 0.14f, yy - 0.5f, z - 0.2f, x + 0.14f, yy + 0.5f, z, g);
				}
				break;
			}
			default:
				break;
			}
		}
		// the top (a floating ring or an antenna goes above a crown's points)
		float above = "crown".equals(base) ? -12.4f : topY;
		switch (top) {
		case "pompom":
			box(-1.5f, topY - 2.5f, -1.5f, 1.5f, topY + 0.1f, 1.5f, c2);
			box(-1.1f, topY - 2.9f, -1.1f, 1.1f, topY - 2.5f, 1.1f, c2);
			break;
		case "cone": {
			rot(0f, topY, 0f, (float) Math.sin(t * 1.4f) * 3f, 0f, 0f);
			float[][] seg = { { 2.6f, 1.6f, 0f }, { 2.0f, 1.5f, 0.3f }, { 1.4f, 1.4f, 0.8f }, { 0.85f, 1.2f, 1.5f }, { 0.45f, 0.9f, 2.4f } };
			float y = topY;
			for (float[] q : seg) {
				box(-q[0], y - q[1], -q[0] + q[2], q[0], y, q[0] + q[2], c2);
				y -= q[1];
			}
			rotReset();
			break;
		}
		case "propeller":
			box(-0.3f, topY - 1.5f, -0.3f, 0.3f, topY, 0.3f, 0x9A9AA2);
			box(-0.6f, topY - 2f, -0.6f, 0.6f, topY - 1.4f, 0.6f, c1);
			rot(0f, topY - 1.7f, 0f, 0f, (t * 760f) % 360f, 0f);
			box(-4.6f, topY - 1.85f, -0.55f, -0.6f, topY - 1.5f, 0.55f, c2);
			box(0.6f, topY - 1.85f, -0.55f, 4.6f, topY - 1.5f, 0.55f, shade(c2, 0.8f));
			rotReset();
			break;
		case "ears":
			for (int sd = -1; sd <= 1; sd += 2) {
				float cx = sd * 2.6f, cz = -0.7f, b = Math.min(topY, -8f) + 0.4f;
				rot(cx, b, cz, 0f, 0f, -sd * 13f);
				float[][] st = { { 1.8f, 1.3f }, { 1.4f, 1.3f }, { 0.95f, 1.2f }, { 0.5f, 1.0f } };
				float y = b;
				for (float[] q : st) {
					box(cx - q[0], y - q[1], cz - 0.6f, cx + q[0], y, cz + 0.6f, c1);
					if (q[0] > 0.6f) box(cx - q[0] + 0.55f, y - q[1], cz - 0.74f, cx + q[0] - 0.55f, y, cz - 0.6f, c2);
					y -= q[1];
				}
				rotReset();
			}
			break;
		case "horns": {
			float[][] sg = { { 3.2f, -0.3f, -2.2f, 0.95f }, { 3.9f, -1.3f, -2.4f, 0.85f }, { 4.6f, -2.3f, -2.5f, 0.75f },
					{ 5.0f, -3.4f, -2.4f, 0.62f }, { 5.1f, -4.4f, -2.0f, 0.5f }, { 4.8f, -5.2f, -1.4f, 0.4f } };
			float b = Math.min(topY, -8f);
			for (int sd = -1; sd <= 1; sd += 2) {
				for (int k = 0; k < sg.length; ++k) {
					float[] q = sg[k];
					int c = mix(c2, shade(c2, 0.3f), k / (float) (sg.length - 1));
					box(sd * q[0] - q[3], b + q[1] - q[3], q[2] - q[3], sd * q[0] + q[3], b + q[1] + q[3], q[2] + q[3], c);
				}
			}
			break;
		}
		case "antenna": {
			float bob = 0.3f * (float) Math.sin(t * 2.4f);
			box(-0.25f, above - 4f, -0.25f, 0.25f, topY, 0.25f, 0x9A9AA2);
			float p = 0.8f + 0.2f * (float) Math.sin(t * 3f);
			glowBox(-0.8f, above - 5.4f + bob, -0.8f, 0.8f, above - 3.8f + bob, 0.8f, shade(c2, p));
			break;
		}
		case "halo": {
			float y = above - 2.6f + 0.35f * (float) Math.sin(t * 2f), spin = (t * 25f) % 360f;
			int n = 18;
			float r = 3.7f, half = (float) (Math.PI * r / n) * 1.12f;
			int col = shade(c2, 0.85f + 0.15f * (float) Math.sin(t * 3f));
			for (int k = 0; k < n; ++k) {
				rot(0f, y, 0f, 7f, spin + k * 360f / n, 0f);
				glowBox(-half, y - 0.28f, -r - 0.32f, half, y + 0.28f, -r + 0.32f, col);
			}
			rotReset();
			break;
		}
		default:
			break;
		}
	}

	/** Boots put together in the Maker: a shape, an extra, two colours (the trail is drawn elsewhere). */
	public static void customBoot(String shape, String extra, int c1, int c2, float t, int side, boolean active) {
		switch (shape) {
		case "boot":
			box(-2.35f, 6.4f, -2.35f, 2.35f, 11.8f, 2.35f, c1);
			box(-2.35f, 10.5f, -3.1f, 2.35f, 11.8f, -2.35f, c1);
			box(-2.45f, 11.75f, -3.2f, 2.45f, 12.35f, 2.45f, c2);
			box(-2.5f, 6.2f, -2.5f, 2.5f, 7.0f, 2.5f, c2);
			break;
		case "chunky":
			box(-2.35f, 8.6f, -2.35f, 2.35f, 11.4f, 2.35f, c1);
			box(-2.35f, 10.2f, -3.1f, 2.35f, 11.4f, -2.35f, c1);
			box(-2.6f, 11.3f, -3.4f, 2.6f, 13.0f, 2.7f, c2);
			box(-2.42f, 8.4f, -2.42f, 2.42f, 9.0f, 2.42f, c2);
			break;
		case "cloud":
			cloud(c1, t, side);
			box(-1.2f, 12.3f, -2.4f, 1.6f, 12.8f, 2.0f, c2);
			break;
		default: // sneaker
			box(-2.35f, 8.6f, -2.35f, 2.35f, 11.8f, 2.35f, c1);
			box(-2.35f, 10.5f, -3.1f, 2.35f, 11.8f, -2.35f, c1);
			box(-2.45f, 11.75f, -3.2f, 2.45f, 12.35f, 2.45f, mix(c2, 0xF0F0EA, 0.5f));
			box(-2.42f, 8.4f, -2.42f, 2.42f, 9.0f, 2.42f, c2);
			box(side * 2.35f, 9.9f, -2.0f, side * 2.43f, 10.7f, 1.8f, c2);
			break;
		}
		switch (extra) {
		case "wings": {
			float flap = (float) Math.sin(t * (active ? 14f : 4f)) * (active ? 26f : 10f);
			float x = side * 2.4f;
			rot(x, 9.4f, 0.4f, 0f, side * (20f + flap), 0f);
			float x0 = side > 0 ? x : x - 0.3f, x1 = side > 0 ? x + 0.3f : x;
			box(x0, 7.6f, 0.4f, x1, 8.6f, 4.6f, 0xFFFFFF);
			box(x0, 8.6f, 0.4f, x1, 9.5f, 3.8f, 0xF4F1EA);
			box(x0, 9.5f, 0.4f, x1, 10.3f, 3.0f, 0xE9E4DA);
			box(x0 - 0.02f, 7.5f, 0.3f, x1 + 0.02f, 7.85f, 4.7f, c2);
			rotReset();
			break;
		}
		case "crystals": {
			int g = shade(mix(c2, 0xFFFFFF, 0.4f), 0.55f + 0.2f * (float) Math.sin(t * 1.8f + side));
			float x = side * 2.35f;
			crystal(x, 9.6f, -1.1f, side, 1.5f, g);
			crystal(x, 10.6f, 0.9f, side, 1.1f, g);
			crystal(x, 8.7f, 1.6f, side, 0.8f, g);
			break;
		}
		case "cracks": {
			float p = 0.6f + 0.4f * (float) Math.sin(t * 2.4f + side * 0.8f);
			int g = shade(c2, p);
			glowBox(-1.6f, 9.2f, -2.45f, -1.2f, 11.6f, -2.35f, g);
			glowBox(-1.2f, 10.2f, -2.45f, 0.9f, 10.6f, -2.35f, g);
			glowBox(0.9f, 9.0f, -2.45f, 1.3f, 10.6f, -2.35f, g);
			float x = side * 2.35f;
			glowBox(x - 0.06f, 9.4f, -1.8f, x + 0.06f, 9.8f, 1.6f, g);
			glowBox(x - 0.06f, 9.8f, 0.6f, x + 0.06f, 11.4f, 1.0f, g);
			break;
		}
		case "nozzle": {
			box(-1.1f, 10.3f, 2.35f, 1.1f, 11.9f, 3.3f, 0x3A3E45);
			float flick = 0.75f + 0.25f * (float) Math.sin(t * 40f) * (float) Math.sin(t * 23f + 1f);
			float len = active ? 2.2f + 1.4f * flick : 0.6f + 0.3f * flick;
			glowBox(-0.75f, 11.9f, 2.45f, 0.75f, 11.9f + len, 3.15f, mix(c2, 0xFF7A1A, 0.5f));
			glowBox(-0.4f, 11.9f, 2.6f, 0.4f, 11.9f + len * 0.6f, 3.0f, 0xFFE07A);
			break;
		}
		default:
			break;
		}
	}

	// ------------------------------------------------------------------ the boots

	/** A pair of boots' own colour (used when their colour is "natural"). */
	public static int naturalBoots(String id) {
		switch (id) {
		case "sneakers": return 0x4A8CFF;
		case "rocket": return 0xFF7A3C;
		case "winged": return 0xFFD76B;
		case "cloud": return 0xF4F6FA;
		case "frost": return 0x9FDFFF;
		case "magma": return 0xFF6A1A;
		default: return 0xFFFFFF;
		}
	}

	/** Draws one boot; side = -1 for the right leg, 1 for the left (the outside of the foot). */
	public static void drawBoot(String id, int main, float t, int side, boolean active) {
		switch (id) {
		case "sneakers": sneakers(main, side); break;
		case "rocket": rocket(main, t, active); break;
		case "winged": winged(main, t, side, active); break;
		case "cloud": cloud(main, t, side); break;
		case "frost": frost(main, t, side); break;
		case "magma": magma(main, t, side); break;
		default: break;
		}
	}

	/** The basic boot shape: shell, toe and sole. */
	private static void shell(int upper, int sole) {
		box(-2.35f, 8.6f, -2.35f, 2.35f, 11.8f, 2.35f, upper);
		box(-2.35f, 10.5f, -3.1f, 2.35f, 11.8f, -2.35f, upper);
		box(-2.45f, 11.75f, -3.2f, 2.45f, 12.35f, 2.45f, sole);
	}

	/** Clean white sneakers with a coloured stripe and collar. */
	private static void sneakers(int accent, int side) {
		shell(0xF1F1EE, 0xE3E1DA);
		box(-2.5f, 12.2f, -3.3f, 2.5f, 12.45f, 2.55f, 0xB98C5A); // gum under the sole
		box(-2.42f, 8.4f, -2.42f, 2.42f, 9.0f, 2.42f, accent);
		box(side * 2.35f, 9.9f, -2.0f, side * 2.43f, 10.7f, 1.8f, accent); // the stripe on the outside
		for (int k = 0; k < 3; ++k) {
			float y = 9.2f + k * 0.65f;
			box(-1.2f, y, -2.45f, 1.2f, y + 0.25f, -2.35f, 0x34343A);
		}
	}

	/** Metal boots with a nozzle at the heel; flames when you jump, fly or sprint. */
	private static void rocket(int accent, float t, boolean active) {
		shell(0xA7B0BA, 0x4A4F57);
		box(-2.42f, 8.4f, -2.42f, 2.42f, 9.1f, 2.42f, accent);
		box(-2.42f, 10.2f, -3.18f, 2.42f, 10.6f, -2.35f, accent);
		box(-1.1f, 10.3f, 2.35f, 1.1f, 11.9f, 3.3f, 0x3A3E45); // the nozzle
		float flick = 0.75f + 0.25f * (float) Math.sin(t * 40f) * (float) Math.sin(t * 23f + 1f);
		float len = active ? 2.2f + 1.4f * flick : 0.6f + 0.3f * flick;
		glowBox(-0.75f, 11.9f, 2.45f, 0.75f, 11.9f + len, 3.15f, 0xFF7A1A);
		glowBox(-0.4f, 11.9f, 2.6f, 0.4f, 11.9f + len * 0.6f, 3.0f, 0xFFE07A);
	}

	/** Pale boots with little feathered wings that flap (faster in the air). */
	private static void winged(int trim, float t, int side, boolean active) {
		shell(0xEDE6D6, shade(trim, 0.8f));
		box(-2.42f, 8.4f, -2.42f, 2.42f, 9.0f, 2.42f, trim);
		float flap = (float) Math.sin(t * (active ? 14f : 4f)) * (active ? 26f : 10f);
		float x = side * 2.4f;
		rot(x, 9.4f, 0.4f, 0f, side * (20f + flap), 0f);
		float x0 = side > 0 ? x : x - 0.3f, x1 = side > 0 ? x + 0.3f : x;
		box(x0, 7.6f, 0.4f, x1, 8.6f, 4.6f, 0xFFFFFF);
		box(x0, 8.6f, 0.4f, x1, 9.5f, 3.8f, 0xF4F1EA);
		box(x0, 9.5f, 0.4f, x1, 10.3f, 3.0f, 0xE9E4DA);
		box(x0, 10.3f, 0.4f, x1, 10.9f, 2.2f, 0xDDD6C8);
		box(x0 - 0.02f, 7.5f, 0.3f, x1 + 0.02f, 7.85f, 4.7f, trim);
		rotReset();
	}

	/** Puffy little clouds round your feet, gently bobbing. */
	private static void cloud(int c, float t, int side) {
		float b = 0.25f * (float) Math.sin(t * 2.2f + side);
		int lo = shade(c, 0.9f), lo2 = shade(c, 0.8f);
		// a round cloud: a wide middle, rounder lumps on every side and on top
		box(-3.0f, 10.4f + b, -3.6f, 3.0f, 12.3f + b, 3.0f, c);
		box(-2.4f, 9.4f + b, -2.9f, 2.4f, 10.4f + b, 2.4f, c);
		box(-2.6f, 8.8f + b, -2.7f, 2.6f, 9.4f + b, 2.5f, c); // a puff round the top (outside the leg, so it never flickers through it)
		box(-3.8f, 10.8f + b, -2.6f, -3.0f, 12.0f + b, 2.0f, lo);
		box(3.0f, 10.6f + b, -2.2f, 3.8f, 11.9f + b, 2.4f, lo);
		box(-2.2f, 10.7f + b, 3.0f, 2.0f, 12.0f + b, 3.8f, lo);
		box(-2.4f, 10.8f + b, -4.4f, 1.8f, 12.1f + b, -3.6f, lo);
		box(-1.2f, 12.3f + b, -2.4f, 1.6f, 12.8f + b, 2.0f, lo2);
		box(side * 2.2f - 0.9f, 9.0f + b, -1.0f, side * 2.2f + 0.9f, 10.4f + b, 0.9f, c);
	}

	/** Ice boots with crystals that catch the light. */
	private static void frost(int ice, float t, int side) {
		shell(ice, shade(ice, 0.7f));
		box(-2.45f, 8.3f, -2.45f, 2.45f, 8.9f, 2.45f, 0xE6F8FF);
		float p = 0.55f + 0.2f * (float) Math.sin(t * 1.8f + side);
		int g = shade(0xE0F6FF, p);
		float x = side * 2.35f;
		// crystals sticking out of the outside and the toe
		crystal(x, 9.6f, -1.1f, side, 1.5f, g);
		crystal(x, 10.6f, 0.9f, side, 1.1f, g);
		crystal(x, 8.7f, 1.6f, side, 0.8f, g);
		glowBox(-0.35f, 10.5f, -3.5f, 0.35f, 11.3f, -3.1f, g);
	}

	/** A small ice crystal pointing out sideways and a little up. */
	private static void crystal(float x, float y, float z, int side, float len, int c) {
		rot(x, y, z, 0f, 0f, side * -28f);
		float x1 = side > 0 ? x : x - len, x2 = side > 0 ? x + len : x;
		glowBox(x1, y - 0.3f, z - 0.3f, x2, y + 0.3f, z + 0.3f, c);
		float tip = side > 0 ? x2 : x1;
		glowBox(tip - 0.35f, y - 0.15f, z - 0.15f, tip + 0.35f, y + 0.15f, z + 0.15f, c); // a thinner point
		rotReset();
	}

	/** Dark rock boots with glowing cracks that pulse. */
	private static void magma(int glow, float t, int side) {
		shell(0x2A1B17, 0x1A100D);
		float p = 0.6f + 0.4f * (float) Math.sin(t * 2.4f + side * 0.8f);
		int g = shade(glow, p), g2 = shade(mix(glow, 0xFFE07A, 0.5f), p);
		glowBox(-1.6f, 9.2f, -2.45f, -1.2f, 11.6f, -2.35f, g);
		glowBox(-1.2f, 10.2f, -2.45f, 0.9f, 10.6f, -2.35f, g2);
		glowBox(0.9f, 9.0f, -2.45f, 1.3f, 10.6f, -2.35f, g);
		float x = side * 2.35f;
		glowBox(x - 0.06f, 9.4f, -1.8f, x + 0.06f, 9.8f, 1.6f, g);
		glowBox(x - 0.06f, 9.8f, 0.6f, x + 0.06f, 11.4f, 1.0f, g2);
		glowBox(-1.8f, 11.8f, -3.25f, 1.8f, 12.0f, -3.15f, g);
	}

	// ------------------------------------------------------------------ building blocks

	/** Where the shapes' corners go (the game's vertex buffer, or the picture checker). */
	public interface Sink {
		void vertex(float x, float y, float z, float u, float v, float r, float g, float b, float nx, float ny, float nz);
	}

	private static Sink sink;
	/** true = only the glowing parts are drawn, false = only the solid parts. */
	static boolean glowPass;
	private static int boxNo;

	// the turn applied to the next boxes: v' = R (v - pivot) + pivot
	private static boolean turned;
	private static float m00, m01, m02, m10, m11, m12, m20, m21, m22, pvx, pvy, pvz;

	/** Starts sending shapes to a sink (glow = the glowing parts' pass). */
	public static void begin(Sink s, boolean glow) {
		sink = s;
		glowPass = glow;
		boxNo = 0;
		turned = false;
	}

	/** Turns the next boxes round a pivot (degrees; about x, then y... applied as Ry * Rx * Rz). */
	private static void rot(float px, float py, float pz, float ax, float ay, float az) {
		double a = Math.toRadians(ax), b = Math.toRadians(ay), c = Math.toRadians(az);
		float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
		float cb = (float) Math.cos(b), sb = (float) Math.sin(b);
		float cc = (float) Math.cos(c), sc = (float) Math.sin(c);
		// Rx
		float x00 = 1, x01 = 0, x02 = 0, x10 = 0, x11 = ca, x12 = -sa, x20 = 0, x21 = sa, x22 = ca;
		// Rz
		float z00 = cc, z01 = -sc, z10 = sc, z11 = cc;
		// Rx * Rz
		float r00 = x00 * z00 + x01 * z10, r01 = x00 * z01 + x01 * z11, r02 = x02;
		float r10 = x10 * z00 + x11 * z10, r11 = x10 * z01 + x11 * z11, r12 = x12;
		float r20 = x20 * z00 + x21 * z10, r21 = x20 * z01 + x21 * z11, r22 = x22;
		// Ry * (Rx * Rz)
		m00 = cb * r00 + sb * r20; m01 = cb * r01 + sb * r21; m02 = cb * r02 + sb * r22;
		m10 = r10; m11 = r11; m12 = r12;
		m20 = -sb * r00 + cb * r20; m21 = -sb * r01 + cb * r21; m22 = -sb * r02 + cb * r22;
		pvx = px; pvy = py; pvz = pz;
		turned = true;
	}

	private static void rotReset() {
		turned = false;
	}

	private static void glowBox(float x1, float y1, float z1, float x2, float y2, float z2, int rgb) {
		if (glowPass) emit(x1, y1, z1, x2, y2, z2, rgb);
	}

	private static void box(float x1, float y1, float z1, float x2, float y2, float z2, int rgb) {
		if (!glowPass) emit(x1, y1, z1, x2, y2, z2, rgb);
	}

	private static void emit(float x1, float y1, float z1, float x2, float y2, float z2, int rgb) {
		if (x1 > x2) { float s = x1; x1 = x2; x2 = s; }
		if (y1 > y2) { float s = y1; y1 = y2; y2 = s; }
		if (z1 > z2) { float s = z1; z1 = z2; z2 = s; }
		float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
		float ou = ((boxNo * 7) % 16) / 16f, ov = ((boxNo * 11) % 16) / 16f;
		++boxNo;
		// top (y1, facing up = -y), bottom, front (-z), back, left (-x), right
		face(x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, 0, -1, 0, x2 - x1, z2 - z1, ou, ov, r, g, b);
		face(x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, 0, 1, 0, x2 - x1, z2 - z1, ou, ov, r, g, b);
		face(x1, y2, z1, x2, y2, z1, x2, y1, z1, x1, y1, z1, 0, 0, -1, x2 - x1, y2 - y1, ou, ov, r, g, b);
		face(x2, y2, z2, x1, y2, z2, x1, y1, z2, x2, y1, z2, 0, 0, 1, x2 - x1, y2 - y1, ou, ov, r, g, b);
		face(x1, y2, z2, x1, y2, z1, x1, y1, z1, x1, y1, z2, -1, 0, 0, z2 - z1, y2 - y1, ou, ov, r, g, b);
		face(x2, y2, z1, x2, y2, z2, x2, y1, z2, x2, y1, z1, 1, 0, 0, z2 - z1, y2 - y1, ou, ov, r, g, b);
	}

	private static void face(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
			float dx, float dy, float dz, float nx, float ny, float nz, float w, float h, float ou, float ov, float r,
			float g, float b) {
		if (turned) {
			float tx = m00 * nx + m01 * ny + m02 * nz, ty = m10 * nx + m11 * ny + m12 * nz, tz = m20 * nx + m21 * ny + m22 * nz;
			nx = tx; ny = ty; nz = tz;
		}
		float u1 = ou + w / 16f, v1 = ov + h / 16f;
		vert(ax, ay, az, ou, v1, r, g, b, nx, ny, nz);
		vert(bx, by, bz, u1, v1, r, g, b, nx, ny, nz);
		vert(cx, cy, cz, u1, ov, r, g, b, nx, ny, nz);
		vert(dx, dy, dz, ou, ov, r, g, b, nx, ny, nz);
	}

	private static void vert(float x, float y, float z, float u, float v, float r, float g, float b, float nx, float ny, float nz) {
		if (turned) {
			float lx = x - pvx, ly = y - pvy, lz = z - pvz;
			x = m00 * lx + m01 * ly + m02 * lz + pvx;
			y = m10 * lx + m11 * ly + m12 * lz + pvy;
			z = m20 * lx + m21 * ly + m22 * lz + pvz;
		}
		sink.vertex(x, y, z, u, v, r, g, b, nx, ny, nz);
	}

	static int shade(int rgb, float k) {
		int r = Math.min(255, (int) (((rgb >> 16) & 255) * k));
		int g = Math.min(255, (int) (((rgb >> 8) & 255) * k));
		int b = Math.min(255, (int) ((rgb & 255) * k));
		return (r << 16) | (g << 8) | b;
	}

	static int mix(int a, int b, float k) {
		int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
		int g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
		int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
		return (r << 16) | (g << 8) | bl;
	}
}
