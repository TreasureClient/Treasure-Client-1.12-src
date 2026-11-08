package net.lax1dude.eaglercraft.opengl;

import net.lax1dude.eaglercraft.vector.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

/**
 * Treasure Client: name tag batching.
 *
 * Vanilla draws each name tag (players, mobs and the floating hologram lines
 * servers put at spawn) on its own: a dark box, the faint see-through text and
 * the solid text, each its own trip to the graphics chip with its own settings.
 * A spawn with a dozen holograms was about 3 ms per frame on a slow laptop.
 *
 * While the world's entities are drawn, name tags are only remembered here
 * (text and position). At the end of the entity pass they are drawn all at
 * once: every box in one draw, then all see-through text, then all solid text,
 * one draw per font page. Each letter is moved into place on the CPU with the
 * same matrix vanilla would use, so the tags look the same. Like Eaglercraft's
 * fast text path, tags are drawn at full brightness (no lightmap).
 */
public final class TCNameTagBatch {

	private TCNameTagBatch() {
	}

	/** Follows the ImmediatelyFast / model batching switch. */
	public static boolean enabled = true;

	private static boolean active;
	private static boolean drawing;
	/** True while the font renderer should hand its letters to this batch. */
	public static boolean capturing;

	private static final class Tag {
		final Matrix4f m = new Matrix4f();
		FontRenderer font;
		String str;
		int shift;
		int halfWidth;
		boolean sneak;
	}

	private static Tag[] tags = new Tag[32];
	private static int tagCount;

	/** Letters waiting to be drawn, one bucket per font page. */
	private static final class Bucket {
		ResourceLocation tex; // null: plain colored boxes (underline, strikethrough)
		final WorldRenderer buf = new WorldRenderer(256 * 4 * 6);
		int quads;
		boolean open;
	}

	private static Bucket[] buckets = new Bucket[8];
	private static int bucketCount;
	private static Bucket last;
	private static Tag cur;
	private static WorldRenderer boxBuf;

	/** Start of the entity pass. */
	public static void begin() {
		active = enabled;
		tagCount = 0;
	}

	/**
	 * Remembers a name tag instead of drawing it. Returns false when it should be
	 * drawn the normal way (outside the entity pass, or while a display list is built).
	 */
	public static boolean defer(FontRenderer font, String str, float x, float y, float z, int verticalShift,
			float viewerYaw, float viewerPitch, boolean isThirdPersonFrontal, boolean isSneaking) {
		if (!active || drawing || str == null || EaglercraftGPU.tcCompilingList()) {
			return false;
		}
		if (tagCount == tags.length) {
			Tag[] n = new Tag[tags.length * 2];
			System.arraycopy(tags, 0, n, 0, tags.length);
			tags = n;
		}
		Tag t = tags[tagCount];
		if (t == null) {
			t = tags[tagCount] = new Tag();
		}
		++tagCount;
		// the same steps as vanilla's drawNameplate, so the matrix is exactly the same
		GlStateManager.pushMatrix();
		GlStateManager.translate(x, y, z);
		GlStateManager.rotate(-viewerYaw, 0.0F, 1.0F, 0.0F);
		GlStateManager.rotate((float) (isThirdPersonFrontal ? -1 : 1) * viewerPitch, 1.0F, 0.0F, 0.0F);
		GlStateManager.scale(-0.025F, -0.025F, 0.025F);
		t.m.load(GlStateManager.getModelViewReference());
		GlStateManager.popMatrix();
		t.font = font;
		t.str = str;
		t.shift = verticalShift;
		t.halfWidth = font.getStringWidth(str) / 2;
		t.sneak = isSneaking;
		return true;
	}

	/** End of the entity pass: draws every remembered tag. */
	public static void end() {
		active = false;
		if (tagCount == 0) {
			return;
		}
		drawing = true;
		boolean lmWas = GlStateManager.stateTexture[1];
		try {
			setLightmap(false);
			drawAll();
		} finally {
			capturing = false;
			drawing = false;
			setLightmap(lmWas);
			for (int i = 0; i < tagCount; ++i) {
				tags[i].str = null;
				tags[i].font = null;
			}
			tagCount = 0;
		}
	}

	private static void drawAll() {
		EaglercraftGPU.glNormal3f(0.0F, 1.0F, 0.0F);
		GlStateManager.disableLighting();
		GlStateManager.depthMask(false);
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(RealOpenGLEnums.GL_SRC_ALPHA, RealOpenGLEnums.GL_ONE_MINUS_SRC_ALPHA,
				RealOpenGLEnums.GL_ONE, RealOpenGLEnums.GL_ZERO);

		// see through walls: boxes and faint text of tags that aren't sneaking
		GlStateManager.disableDepth();
		drawBoxes(false);
		drawTexts(true);
		GlStateManager.enableDepth();

		// sneaking players' boxes only show where nothing is in front
		drawBoxes(true);

		// solid text, hidden behind walls
		GlStateManager.depthMask(true);
		drawTexts(false);

		GlStateManager.enableLighting();
		GlStateManager.disableBlend();
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
	}

	/** The dark box behind each tag (black at 25%, so light doesn't change it). */
	private static void drawBoxes(boolean sneaking) {
		WorldRenderer w = null;
		for (int i = 0; i < tagCount; ++i) {
			Tag t = tags[i];
			if (t.sneak != sneaking) {
				continue;
			}
			if (w == null) {
				if (boxBuf == null) {
					boxBuf = new WorldRenderer(64 * 4 * 4);
				}
				w = boxBuf;
				if (boxOpen) {
					w.finishDrawing(); // left open by an error last time
				}
				w.begin(7, DefaultVertexFormats.POSITION_COLOR);
				boxOpen = true;
			}
			Matrix4f m = t.m;
			float l = -t.halfWidth - 1, r = t.halfWidth + 1, top = -1 + t.shift, bot = 8 + t.shift;
			boxVertex(w, m, l, top);
			boxVertex(w, m, l, bot);
			boxVertex(w, m, r, bot);
			boxVertex(w, m, r, top);
		}
		if (w == null) {
			return;
		}
		boxOpen = false;
		GlStateManager.disableTexture2D();
		drawViewSpace(w);
		GlStateManager.enableTexture2D();
	}

	private static boolean boxOpen;

	private static void boxVertex(WorldRenderer w, Matrix4f m, float x, float y) {
		w.pos(m.m00 * x + m.m10 * y + m.m30, m.m01 * x + m.m11 * y + m.m31, m.m02 * x + m.m12 * y + m.m32)
				.color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
	}

	private static void drawTexts(boolean seeThrough) {
		bucketCount = 0;
		last = null;
		capturing = true;
		try {
			for (int i = 0; i < tagCount; ++i) {
				Tag t = tags[i];
				if (seeThrough && t.sneak) {
					continue;
				}
				cur = t;
				int color = seeThrough || t.sneak ? 553648127 : -1;
				t.font.drawString(t.str, -t.halfWidth, t.shift, color);
			}
		} finally {
			capturing = false;
			cur = null;
		}
		GlStateManager.enableAlpha();
		for (int i = 0; i < bucketCount; ++i) {
			Bucket b = buckets[i];
			if (b.quads == 0) {
				continue;
			}
			b.open = false;
			if (b.tex == null) {
				GlStateManager.disableTexture2D();
				drawViewSpace(b.buf);
				GlStateManager.enableTexture2D();
			} else {
				Minecraft.getMinecraft().getTextureManager().bindTexture(b.tex);
				drawViewSpace(b.buf);
			}
			b.quads = 0;
		}
	}

	private static Bucket bucket(ResourceLocation tex) {
		Bucket b = last;
		if (b != null && b.tex == tex) {
			return b;
		}
		for (int i = 0; i < bucketCount; ++i) {
			b = buckets[i];
			if (b.tex == tex) {
				return last = b;
			}
		}
		if (bucketCount == buckets.length) {
			Bucket[] n = new Bucket[buckets.length * 2];
			System.arraycopy(buckets, 0, n, 0, buckets.length);
			buckets = n;
		}
		b = buckets[bucketCount];
		if (b == null) {
			b = buckets[bucketCount] = new Bucket();
		}
		++bucketCount;
		b.tex = tex;
		b.quads = 0;
		if (b.open) {
			b.buf.finishDrawing(); // left open by an error last time
		}
		b.open = true;
		b.buf.begin(7, tex != null ? DefaultVertexFormats.POSITION_TEX_COLOR : DefaultVertexFormats.POSITION_COLOR);
		return last = b;
	}

	/** A letter from the font renderer, in the tag's own coordinates. */
	public static void glyph(ResourceLocation tex, float xTopL, float xBotL, float xTopR, float xBotR, float yTop,
			float yBot, float u0, float v0, float u1, float v1, int argb) {
		Bucket b = bucket(tex);
		WorldRenderer w = b.buf;
		Matrix4f m = cur.m;
		int r = argb >>> 16 & 255, g = argb >>> 8 & 255, bl = argb & 255, a = argb >>> 24;
		texVertex(w, m, xTopL, yTop, u0, v0, r, g, bl, a);
		texVertex(w, m, xBotL, yBot, u0, v1, r, g, bl, a);
		texVertex(w, m, xBotR, yBot, u1, v1, r, g, bl, a);
		texVertex(w, m, xTopR, yTop, u1, v0, r, g, bl, a);
		++b.quads;
	}

	/** A plain box from the font renderer (underline or strikethrough). */
	public static void rect(float left, float top, float right, float bottom, int argb) {
		Bucket b = bucket(null);
		WorldRenderer w = b.buf;
		Matrix4f m = cur.m;
		int r = argb >>> 16 & 255, g = argb >>> 8 & 255, bl = argb & 255, a = argb >>> 24;
		colVertex(w, m, left, bottom, r, g, bl, a);
		colVertex(w, m, right, bottom, r, g, bl, a);
		colVertex(w, m, right, top, r, g, bl, a);
		colVertex(w, m, left, top, r, g, bl, a);
		++b.quads;
	}

	private static void texVertex(WorldRenderer w, Matrix4f m, float x, float y, float u, float v, int r, int g,
			int b, int a) {
		w.pos(m.m00 * x + m.m10 * y + m.m30, m.m01 * x + m.m11 * y + m.m31, m.m02 * x + m.m12 * y + m.m32).tex(u, v)
				.color(r, g, b, a).endVertex();
	}

	private static void colVertex(WorldRenderer w, Matrix4f m, float x, float y, int r, int g, int b, int a) {
		w.pos(m.m00 * x + m.m10 * y + m.m30, m.m01 * x + m.m11 * y + m.m31, m.m02 * x + m.m12 * y + m.m32)
				.color(r, g, b, a).endVertex();
	}

	/** Draws vertices that are already in view space (plain model-view matrix, white color). */
	private static void drawViewSpace(WorldRenderer w) {
		int mode = GlStateManager.stateMatrixMode;
		GlStateManager.matrixMode(RealOpenGLEnums.GL_MODELVIEW);
		GlStateManager.pushMatrix();
		GlStateManager.loadIdentity();
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		w.finishDrawing();
		WorldVertexBufferUploader.func_181679_a(w);
		GlStateManager.popMatrix();
		GlStateManager.matrixMode(mode);
	}

	private static void setLightmap(boolean on) {
		if (GlStateManager.stateTexture[1] != on) {
			int was = GlStateManager.activeTexture;
			GlStateManager.setActiveTexture(RealOpenGLEnums.GL_TEXTURE1);
			if (on) {
				GlStateManager.enableTexture2D();
			} else {
				GlStateManager.disableTexture2D();
			}
			GlStateManager.setActiveTexture(RealOpenGLEnums.GL_TEXTURE0 + was);
		}
	}
}
