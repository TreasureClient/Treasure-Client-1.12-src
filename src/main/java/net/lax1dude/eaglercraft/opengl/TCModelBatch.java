package net.lax1dude.eaglercraft.opengl;

import net.lax1dude.eaglercraft.internal.IBufferGL;
import net.lax1dude.eaglercraft.internal.IProgramGL;
import net.lax1dude.eaglercraft.internal.IVertexArrayGL;
import net.lax1dude.eaglercraft.vector.Matrix4f;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

/**
 * Treasure Client: entity model batching (own implementation of the idea in
 * Sodium's and ImmediatelyFast's entity rendering).
 *
 * Vanilla draws every model part (head, body, each arm and leg, every skin
 * and armor layer) with its own draw call, so one player is 12 to 30 trips to
 * the graphics chip and a lobby full of NPCs is well over a thousand per frame.
 * While entities and block entities are drawn, each part's vertices are moved
 * into place on the CPU (by the same matrix the graphics chip would use) and
 * collected here, then drawn together in one call.
 *
 * The picture stays the same: positions and normals end up exactly where the
 * shader would have put them, each part keeps its own color, and anything else
 * that draws, or any change to a graphics setting (texture, blending, lights,
 * fog, lightmap, hurt tint...), first draws what has been collected. Parts drawn
 * with special settings (enchantment glint, shaders) are drawn the normal way.
 */
public final class TCModelBatch {

	private TCModelBatch() {
	}

	private static final int MAX_QUADS = 4096;

	private static boolean enabled = true;
	private static boolean active;
	private static boolean flushing;
	static int count;
	private static WorldRenderer buffer;

	/** Turns batching on or off (the ImmediatelyFast switch). */
	public static void setEnabled(boolean on) {
		if (!on) {
			flushIfPending();
		}
		enabled = on;
	}

	/** Start of the entity and block entity pass. */
	public static void begin() {
		active = enabled;
	}

	/** End of the pass: draws whatever is left. */
	public static void end() {
		flushIfPending();
		active = false;
	}

	/**
	 * Adds a model part. data holds 8 floats per vertex (x, y, z, u, v and the
	 * normal), 4 vertices per quad. Returns false when the caller should draw it
	 * the normal way.
	 */
	public static boolean add(float[] data, int quads) {
		if (!active || flushing || quads <= 0 || !canBatch()) {
			return false;
		}
		if (count + quads > MAX_QUADS) {
			flushIfPending();
			if (quads > MAX_QUADS) {
				return false;
			}
		}
		if (count == 0) {
			if (buffer == null) {
				buffer = new WorldRenderer(MAX_QUADS * 4 * 8);
			}
			buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
		}
		Matrix4f m = GlStateManager.modelMatrixStack[GlStateManager.modelMatrixStackPointer];
		float m00 = m.m00, m01 = m.m01, m02 = m.m02, m10 = m.m10, m11 = m.m11, m12 = m.m12;
		float m20 = m.m20, m21 = m.m21, m22 = m.m22, m30 = m.m30, m31 = m.m31, m32 = m.m32;
		int r = clamp(GlStateManager.stateColorR), g = clamp(GlStateManager.stateColorG);
		int b = clamp(GlStateManager.stateColorB), a = clamp(GlStateManager.stateColorA);
		WorldRenderer w = buffer;
		int n = quads * 4 * 8;
		int lastNormal = 0;
		float lnx = Float.NaN, lny = Float.NaN, lnz = Float.NaN;
		for (int i = 0; i < n; i += 8) {
			float x = data[i], y = data[i + 1], z = data[i + 2];
			float nx = data[i + 5], ny = data[i + 6], nz = data[i + 7];
			if (nx != lnx || ny != lny || nz != lnz) {
				// same as the shader: normalize(mat3(modelview) * normal)
				lnx = nx;
				lny = ny;
				lnz = nz;
				float tx = m00 * nx + m10 * ny + m20 * nz;
				float ty = m01 * nx + m11 * ny + m21 * nz;
				float tz = m02 * nx + m12 * ny + m22 * nz;
				float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
				if (len > 0.0f) {
					tx /= len;
					ty /= len;
					tz /= len;
				}
				lastNormal = ((byte) (int) (tx * 127.0f) & 255) | (((byte) (int) (ty * 127.0f) & 255) << 8)
						| (((byte) (int) (tz * 127.0f) & 255) << 16);
			}
			w.pos(m00 * x + m10 * y + m20 * z + m30, m01 * x + m11 * y + m21 * z + m31,
					m02 * x + m12 * y + m22 * z + m32).tex(data[i + 3], data[i + 4]).color(r, g, b, a)
					.tcNormalPacked(lastNormal).endVertex();
		}
		count += quads;
		return true;
	}

	/** Settings the batch can't reproduce: texture matrix (glint), texgen, shaders, display list compiling. */
	private static boolean canBatch() {
		if (GlStateManager.activeTexture != 0 || GlStateManager.stateTexGen || GlStateManager.stateUseExtensionPipeline
				|| EaglercraftGPU.tcCompilingList()) {
			return false;
		}
		Matrix4f tm = GlStateManager.textureMatrixStack[0][GlStateManager.textureMatrixStackPointer[0]];
		return tm.m00 == 1.0f && tm.m11 == 1.0f && tm.m22 == 1.0f && tm.m30 == 0.0f && tm.m31 == 0.0f
				&& tm.m10 == 0.0f && tm.m01 == 0.0f && tm.m20 == 0.0f && tm.m21 == 0.0f;
	}

	private static int clamp(float f) {
		int i = (int) (f * 255.0f + 0.5f);
		return i < 0 ? 0 : (i > 255 ? 255 : i);
	}

	/**
	 * Draws the collected parts. This can run inside another draw call after it
	 * has bound its own shader and buffers, so those are put back afterwards.
	 */
	public static void flushIfPending() {
		if (count == 0 || flushing) {
			return;
		}
		IProgramGL prog = EaglercraftGPU.currentShaderProgram;
		IVertexArrayGL vao = EaglercraftGPU.currentVertexArray;
		IBufferGL buf = EaglercraftGPU.currentArrayBuffer;
		flushing = true;
		try {
			int mode = GlStateManager.stateMatrixMode;
			float r = GlStateManager.stateColorR, g = GlStateManager.stateColorG, b = GlStateManager.stateColorB,
					a = GlStateManager.stateColorA;
			// positions are already in view space, so draw with a plain model-view matrix
			GlStateManager.matrixMode(RealOpenGLEnums.GL_MODELVIEW);
			GlStateManager.pushMatrix();
			GlStateManager.loadIdentity();
			GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
			buffer.finishDrawing();
			WorldVertexBufferUploader.func_181679_a(buffer);
			GlStateManager.popMatrix();
			GlStateManager.matrixMode(mode);
			GlStateManager.color(r, g, b, a);
		} finally {
			count = 0;
			flushing = false;
		}
		if (prog != null) {
			EaglercraftGPU.bindGLShaderProgram(prog);
		}
		if (vao != null) {
			EaglercraftGPU.bindGLVertexArray(vao);
		}
		if (buf != null) {
			EaglercraftGPU.bindGLArrayBuffer(buf);
		}
	}
}
