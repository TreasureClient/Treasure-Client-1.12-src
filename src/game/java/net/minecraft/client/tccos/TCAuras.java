package net.minecraft.client.tccos;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.opengl.EaglercraftGPU;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.lax1dude.eaglercraft.opengl.TCModelBatch;
import net.lax1dude.eaglercraft.opengl.WorldRenderer;
import net.minecraft.client.TCLagMonitor;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

/**
 * Treasure Client cosmetics: auras. Thin, soft, glowing shapes around the player.
 *
 * Each aura is a few dozen soft dots and thin rings, all in one draw call, added on top of
 * the picture (they glow, they never darken). Only the 16 nearest players' auras are drawn,
 * they fade out from 28 to 40 blocks away, and your own is never in your face in first person.
 * Positions are worked out from the clock, so there's nothing to update each tick.
 */
public final class TCAuras {

	private TCAuras() {
	}

	private static final int MAX_PER_FRAME = 16;
	private static final double FAR = 40.0, FADE_FROM = 28.0;

	private static int dotTex = -1;
	private static long frame = 0L;
	private static int drawnThisFrame = 0;

	// the camera's right and up directions (dots always face you)
	private static float rx, ry, rz, ux, uy, uz;
	private static WorldRenderer buf;

	/** Draws a player's aura in the world; x, y, z is where their feet are, from the camera. */
	public static void renderWorld(AbstractClientPlayer p, double x, double y, double z) {
		TCCosmetics.Look look = TCCosmetics.of(p);
		if (look == null || !look.hasAura() || p.isInvisible() || p.isSpectator() || p.isPlayerSleeping()) return;
		double dist = Math.sqrt(x * x + y * y + z * z);
		if (dist > FAR) return;
		long f = TCLagMonitor.frameStartNs;
		if (f != frame) {
			frame = f;
			drawnThisFrame = 0;
		}
		if (++drawnThisFrame > MAX_PER_FRAME) return;
		float fade = dist < FADE_FROM ? 1f : (float) ((FAR - dist) / (FAR - FADE_FROM));

		rx = ActiveRenderInfo.getRotationX();
		ry = 0f;
		rz = ActiveRenderInfo.getRotationZ();
		ux = ActiveRenderInfo.getRotationYZ();
		uy = ActiveRenderInfo.getRotationXZ();
		uz = ActiveRenderInfo.getRotationXY();

		TCModelBatch.flushIfPending();
		GlStateManager.pushMatrix();
		GlStateManager.translate((float) x, (float) y, (float) z);
		begin();
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
		draw(look, fade, p.getEntityId() * 0.618f);
		end();
		GlStateManager.enableLighting();
		GlStateManager.popMatrix();
	}

	/**
	 * Draws an aura in a menu preview. The current matrix must have the feet at (0,0,0), y up,
	 * one unit per block, facing the screen.
	 */
	public static void renderGui(TCCosmetics.Look look) {
		if (look == null || !look.hasAura()) return;
		rx = 1f; ry = 0f; rz = 0f;
		ux = 0f; uy = 1f; uz = 0f;
		begin();
		draw(look, 1f, 0f);
		end();
	}

	private static void begin() {
		GlStateManager.disableLighting();
		GlStateManager.enableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.blendFunc(770, 1); // glow: added on top
		GlStateManager.alphaFunc(516, 0.003921569F);
		GlStateManager.depthMask(false);
		GlStateManager.disableCull();
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		bindDot();
		buf = Tessellator.getInstance().getBuffer();
		buf.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
	}

	private static void end() {
		Tessellator.getInstance().draw();
		buf = null;
		GlStateManager.depthMask(true);
		GlStateManager.blendFunc(770, 771);
		GlStateManager.disableBlend();
		GlStateManager.alphaFunc(516, 0.1F);
		GlStateManager.enableCull();
	}

	// ------------------------------------------------------------------ the auras

	private static float cr, cg, cb; // the aura colour
	private static float wr, wg, wb; // the colour of the bright centres (towards white)

	private static void color(TCCosmetics.Look look, long ms, float offset) {
		int c = look.auraRgb(ms, offset);
		cr = ((c >> 16) & 255) / 255f;
		cg = ((c >> 8) & 255) / 255f;
		cb = (c & 255) / 255f;
		wr = cr + (1f - cr) * 0.55f;
		wg = cg + (1f - cg) * 0.55f;
		wb = cb + (1f - cb) * 0.55f;
	}

	private static void draw(TCCosmetics.Look look, float fade, float seed) {
		long ms = EagRuntime.steadyTimeMillis();
		float t = (ms % 3600000L) / 1000f;
		color(look, ms, 0f);
		switch (look.aura) {
		case "halo": halo(t, fade, seed); break;
		case "orbit": orbit(look, ms, t, fade, seed); break;
		case "rising": rising(t, fade, seed); break;
		case "ripple": ripple(t, fade); break;
		case "helix": helix(look, ms, t, fade, seed); break;
		default: break;
		}
	}

	/** A thin glowing ring round your waist, with a bright arc running round it. */
	private static void halo(float t, float fade, float seed) {
		float y = 1.0f + 0.035f * sin(t * 1.3f);
		float rad = 0.62f, ph = t * 1.25f + seed;
		ring(y, rad, 0.13f, 48, cr, cg, cb, 0.14f * fade, 0f, 0f, 0);
		ring(y, rad, 0.026f, 48, wr, wg, wb, 0.3f * fade, 0.7f * fade, ph, 6);
		ring(y, rad, 0.026f, 48, cr, cg, cb, 0f, 0.4f * fade, ph + (float) Math.PI, 10);
		float hx = rad * cos(ph), hz = rad * sin(ph);
		dot(hx, y, hz, 0.2f, cr, cg, cb, 0.35f * fade);
		dot(hx, y, hz, 0.055f, 1f, 1f, 1f, 0.9f * fade);
	}

	/** Three small lights circling at different heights, with short fading trails. */
	private static void orbit(TCCosmetics.Look look, long ms, float t, float fade, float seed) {
		float[] h = { 0.45f, 1.05f, 1.7f };
		float[] r = { 0.78f, 0.7f, 0.55f };
		float[] w = { 1.7f, -1.3f, 2.1f };
		for (int k = 0; k < 3; ++k) {
			if ("rainbow".equals(look.color)) color(look, ms, k * 0.33f);
			float ang = t * w[k] + k * 2.1f + seed;
			float yk = h[k] + 0.07f * sin(t * 1.7f + k);
			float dir = w[k] > 0 ? 1f : -1f;
			for (int s = 10; s >= 1; --s) {
				float a = ang - dir * s * 0.075f;
				float k2 = 1f - s / 11f;
				dot(r[k] * cos(a), yk, r[k] * sin(a), 0.065f * (1f - s / 15f), cr, cg, cb, 0.8f * k2 * k2 * fade);
			}
			float x = r[k] * cos(ang), z = r[k] * sin(ang);
			dot(x, yk, z, 0.22f, cr, cg, cb, 0.32f * fade);
			dot(x, yk, z, 0.05f, wr, wg, wb, 0.95f * fade);
		}
	}

	/** Soft dots floating gently up from your feet. */
	private static void rising(float t, float fade, float seed) {
		flat(0.02f, 0.75f, cr, cg, cb, 0.14f * fade);
		for (int i = 0; i < 18; ++i) {
			float per = 2.4f + h(i, 1) * 1.6f;
			float f = frac(t / per + h(i, 2));
			float yy = f * 2.1f;
			float ang = h(i, 3) * 6.2832f + f * 1.2f + seed;
			float rr = 0.3f + 0.38f * h(i, 4);
			float a = (float) Math.pow(sin((float) Math.PI * f), 1.5) * 0.85f * fade;
			float size = 0.03f + 0.03f * h(i, 5);
			float x = rr * cos(ang), z = rr * sin(ang);
			dot(x, yy, z, size * 3.2f, cr, cg, cb, a * 0.28f);
			dot(x, yy, z, size, wr, wg, wb, a);
		}
	}

	/** A soft circle spreading out on the ground, again and again. */
	private static void ripple(float t, float fade) {
		flat(0.02f, 0.5f, cr, cg, cb, (0.13f + 0.06f * sin(t * 2.6f)) * fade);
		for (int k = 0; k < 3; ++k) {
			float f = frac(t / 2.4f + k / 3f);
			float e = 1f - (1f - f) * (1f - f);
			float rad = 0.18f + 1.15f * e;
			float a = (float) Math.pow(1f - f, 1.4) * fade;
			ring(0.03f, rad, 0.11f, 56, cr, cg, cb, 0.28f * a, 0f, 0f, 0);
			ring(0.03f, rad, 0.028f, 56, wr, wg, wb, 0.85f * a, 0f, 0f, 0);
		}
	}

	/** Two thin strands spiralling round you, with a light running up each one. */
	private static void helix(TCCosmetics.Look look, long ms, float t, float fade, float seed) {
		int n = 22;
		for (int s = 0; s < 2; ++s) {
			if ("rainbow".equals(look.color)) color(look, ms, s * 0.5f);
			float hl = frac(t * 0.45f + s * 0.5f) * 2.3f - 0.2f;
			for (int j = 0; j < n; ++j) {
				float yy = 0.08f + j * (1.85f / (n - 1));
				float ang = yy * (float) Math.PI * 1.7f + t * 1.5f + s * (float) Math.PI + seed;
				float rr = 0.6f - 0.08f * (yy / 1.9f);
				float boost = (float) Math.exp(-sq((yy - hl) / 0.18f));
				float ends = (float) Math.pow(sin((float) Math.PI * (j + 0.5f) / n), 0.7);
				float a = (0.32f + 0.68f * boost) * ends * fade;
				float x = rr * cos(ang), z = rr * sin(ang);
				if (boost > 0.25f) dot(x, yy, z, 0.18f, cr, cg, cb, 0.3f * boost * fade);
				dot(x, yy, z, 0.032f + 0.03f * boost, boost > 0.5f ? wr : cr, boost > 0.5f ? wg : cg, boost > 0.5f ? wb : cb, a);
			}
		}
	}

	// ------------------------------------------------------------------ shapes

	/** A soft dot facing the camera. */
	private static void dot(float x, float y, float z, float s, float r, float g, float b, float a) {
		if (a <= 0.004f) return;
		float ax = (rx + ux) * s, ay = (ry + uy) * s, az = (rz + uz) * s; // right + up
		float bx = (rx - ux) * s, by = (ry - uy) * s, bz = (rz - uz) * s; // right - up
		buf.pos(x - ax, y - ay, z - az).tex(0.0, 1.0).color(r, g, b, a).endVertex();
		buf.pos(x - bx, y - by, z - bz).tex(0.0, 0.0).color(r, g, b, a).endVertex();
		buf.pos(x + ax, y + ay, z + az).tex(1.0, 0.0).color(r, g, b, a).endVertex();
		buf.pos(x + bx, y + by, z + bz).tex(1.0, 1.0).color(r, g, b, a).endVertex();
	}

	/** A soft round glow lying flat (on the ground). */
	private static void flat(float y, float s, float r, float g, float b, float a) {
		if (a <= 0.004f) return;
		buf.pos(-s, y, -s).tex(0.0, 0.0).color(r, g, b, a).endVertex();
		buf.pos(-s, y, s).tex(0.0, 1.0).color(r, g, b, a).endVertex();
		buf.pos(s, y, s).tex(1.0, 1.0).color(r, g, b, a).endVertex();
		buf.pos(s, y, -s).tex(1.0, 0.0).color(r, g, b, a).endVertex();
	}

	/**
	 * A thin flat ring with soft edges. Its brightness is base + peak * (closeness to the
	 * angle "phase") ^ sharp, so part of it can glow brighter (sharp = 0: even all round).
	 */
	private static void ring(float y, float rad, float halfW, int segs, float r, float g, float b, float base, float peak,
			float phase, int sharp) {
		float ri = Math.max(0f, rad - halfW), ro = rad + halfW;
		float pc = cos(0f), ps = sin(0f), pa = ringAlpha(0f, base, peak, phase, sharp);
		for (int i = 1; i <= segs; ++i) {
			float ang = i * 6.2831855f / segs;
			float c = cos(ang), s = sin(ang), a = ringAlpha(ang, base, peak, phase, sharp);
			if (pa > 0.004f || a > 0.004f) {
				buf.pos(pc * ri, y, ps * ri).tex(0.5, 0.0).color(r, g, b, pa).endVertex();
				buf.pos(pc * ro, y, ps * ro).tex(0.5, 1.0).color(r, g, b, pa).endVertex();
				buf.pos(c * ro, y, s * ro).tex(0.5, 1.0).color(r, g, b, a).endVertex();
				buf.pos(c * ri, y, s * ri).tex(0.5, 0.0).color(r, g, b, a).endVertex();
			}
			pc = c; ps = s; pa = a;
		}
	}

	private static float ringAlpha(float ang, float base, float peak, float phase, int sharp) {
		if (peak <= 0f) return base;
		float k = 0.5f + 0.5f * cos(ang - phase);
		float p = k;
		for (int i = 1; i < sharp; ++i) p *= k;
		return base + peak * p;
	}

	/** The soft dot texture (white, see-through towards the edge), made once. */
	private static void bindDot() {
		if (dotTex == -1) {
			int n = 64;
			int[] px = new int[n * n];
			for (int y = 0; y < n; ++y) {
				for (int x = 0; x < n; ++x) {
					float dx = (x + 0.5f) / n * 2f - 1f, dy = (y + 0.5f) / n * 2f - 1f;
					float d2 = dx * dx + dy * dy;
					// a bright little centre in a soft glow, reaching zero at the edge
					float a = (float) Math.exp(-d2 * 9f) * 0.75f + (float) Math.exp(-d2 * 40f) * 0.25f;
					a *= Math.max(0f, 1f - d2);
					int ai = Math.min(255, (int) (a * 255f + 0.5f));
					px[y * n + x] = (ai << 24) | 0xFFFFFF;
				}
			}
			dotTex = GlStateManager.generateTexture();
			TextureUtil.allocateTexture(dotTex, n, n);
			TextureUtil.uploadTexture(dotTex, px, n, n);
			// smooth when stretched; clamped so the edges never wrap round
			EaglercraftGPU.glTexParameteri(0x0DE1, 0x2801, 0x2601);
			EaglercraftGPU.glTexParameteri(0x0DE1, 0x2800, 0x2601);
			EaglercraftGPU.glTexParameteri(0x0DE1, 0x2802, 0x812F);
			EaglercraftGPU.glTexParameteri(0x0DE1, 0x2803, 0x812F);
		}
		GlStateManager.bindTexture(dotTex);
	}

	// ------------------------------------------------------------------ maths

	private static float sin(float v) {
		return (float) Math.sin(v);
	}

	private static float cos(float v) {
		return (float) Math.cos(v);
	}

	private static float sq(float v) {
		return v * v;
	}

	private static float frac(float v) {
		return v - (float) Math.floor(v);
	}

	private static float h(int i, int k) {
		return TCCapeArt.hash(i, k, 404);
	}
}
