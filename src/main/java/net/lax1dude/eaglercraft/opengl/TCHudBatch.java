package net.lax1dude.eaglercraft.opengl;

import net.lax1dude.eaglercraft.internal.IBufferGL;
import net.lax1dude.eaglercraft.internal.IProgramGL;
import net.lax1dude.eaglercraft.internal.IVertexArrayGL;
import net.lax1dude.eaglercraft.vector.Matrix4f;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

/**
 * Treasure Client: ImmediatelyFast-style HUD batching (own implementation,
 * inspired by RaphiMC's ImmediatelyFast).
 *
 * Vanilla draws every HUD icon with its own draw call: each heart, hunger
 * shank, armor icon, hotbar piece and text background is a separate trip to
 * the graphics chip. While the HUD is drawn, flat quads that share the same
 * texture and settings are collected here and drawn together in one call.
 *
 * Drawing order never changes: anything else that draws, and any change to a
 * graphics setting, first draws what has been collected (GlStateManager and
 * EaglercraftGPU call {@link #flushIfPending()}). Positions are turned into
 * screen positions as they are added, so moving or scaling between icons
 * doesn't break a batch, and each quad keeps its own color.
 */
public final class TCHudBatch {

	private TCHudBatch() {
	}

	private static final int MAX_QUADS = 1024;

	private static boolean enabled = true;
	private static boolean active;
	private static boolean flushing;
	private static int count;
	private static boolean batchTextured;
	private static WorldRenderer buffer;

	// projection * model-view, cached by the stacks' change serials
	private static final Matrix4f mvp = new Matrix4f();
	private static int mvpModelSerial = -1, mvpProjSerial = -1, mvpModelPtr = -1, mvpProjPtr = -1;
	private static boolean mvpAffine;

	/** Turns batching on or off (the ImmediatelyFast switch). */
	public static void setEnabled(boolean on) {
		if (!on) {
			flushIfPending();
		}
		enabled = on;
	}

	/** Start of the HUD. */
	public static void begin() {
		active = enabled;
		InstancedFontRenderer.tcBatching = enabled;
	}

	/** End of the HUD: draws whatever is left. */
	public static void end() {
		flushIfPending();
		active = false;
		InstancedFontRenderer.tcBatching = false;
	}

	/**
	 * Draws collected HUD quads and text. This can run inside another draw call after it
	 * has bound its own shader and buffers, so those are put back afterwards.
	 */
	public static void flushIfPending() {
		if ((count > 0 || InstancedFontRenderer.tcPending) && !flushing) {
			IProgramGL prog = EaglercraftGPU.currentShaderProgram;
			IVertexArrayGL vao = EaglercraftGPU.currentVertexArray;
			IBufferGL buf = EaglercraftGPU.currentArrayBuffer;
			if (count > 0) {
				flush();
			}
			InstancedFontRenderer.tcFlushBatch();
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

	/**
	 * Adds a textured quad, colored by the current color, if batching can take
	 * it. Returns false when the caller should draw it the normal way.
	 */
	public static boolean texQuad(double left, double top, double right, double bottom, double z, double u0,
			double v0, double u1, double v1) {
		if (!active || flushing || !GlStateManager.stateTexture[0] || !canBatch()) {
			return false;
		}
		if (InstancedFontRenderer.tcPending) {
			flushIfPending(); // text collected before this quad is drawn first
		}
		int c = packColor(GlStateManager.stateColorR, GlStateManager.stateColorG, GlStateManager.stateColorB,
				GlStateManager.stateColorA);
		prepare(true);
		vertex(left, bottom, z, u0, v1, c);
		vertex(right, bottom, z, u1, v1, c);
		vertex(right, top, z, u1, v0, c);
		vertex(left, top, z, u0, v0, c);
		finishQuad();
		return true;
	}

	/** Only plain 2D drawing is batched: no lighting, fog, lightmap, special shaders or display lists. */
	private static boolean canBatch() {
		if (GlStateManager.activeTexture != 0 || GlStateManager.stateLighting || GlStateManager.stateFog
				|| GlStateManager.stateTexture[1] || GlStateManager.stateTexGen
				|| GlStateManager.stateEnableShaderBlendColor || GlStateManager.stateUseExtensionPipeline
				|| GlStateManager.statePolygonOffset || GlStateManager.stateEnableOverlayFramebufferBlending
				|| GlStateManager.textureMatrixStackPointer[0] != 0 || EaglercraftGPU.tcCompilingList()) {
			return false;
		}
		Matrix4f tm = GlStateManager.textureMatrixStack[0][0];
		if (tm.m00 != 1.0f || tm.m11 != 1.0f || tm.m30 != 0.0f || tm.m31 != 0.0f || tm.m10 != 0.0f
				|| tm.m01 != 0.0f) {
			return false;
		}
		updateMvp();
		return mvpAffine;
	}

	private static void updateMvp() {
		int mp = GlStateManager.modelMatrixStackPointer, pp = GlStateManager.projectionMatrixStackPointer;
		int ms = GlStateManager.modelMatrixStackAccessSerial[mp], ps = GlStateManager.projectionMatrixStackAccessSerial[pp];
		if (mp == mvpModelPtr && pp == mvpProjPtr && ms == mvpModelSerial && ps == mvpProjSerial) {
			return;
		}
		mvpModelPtr = mp;
		mvpProjPtr = pp;
		mvpModelSerial = ms;
		mvpProjSerial = ps;
		Matrix4f.mul(GlStateManager.projectionMatrixStack[pp], GlStateManager.modelMatrixStack[mp], mvp);
		// screen positions stay exact only without perspective (the HUD uses a flat projection)
		mvpAffine = mvp.m03 == 0.0f && mvp.m13 == 0.0f && mvp.m23 == 0.0f && mvp.m33 == 1.0f;
	}

	private static void prepare(boolean textured) {
		if (count > 0 && textured != batchTextured) {
			flush();
		}
		if (count == 0) {
			if (buffer == null) {
				buffer = new WorldRenderer(MAX_QUADS * 4 * 6);
			}
			batchTextured = textured;
			buffer.begin(7, textured ? DefaultVertexFormats.POSITION_TEX_COLOR : DefaultVertexFormats.POSITION_COLOR);
		}
	}

	private static void vertex(double x, double y, double z, double u, double v, int argb) {
		Matrix4f m = mvp;
		float fx = (float) x, fy = (float) y, fz = (float) z;
		double cx = m.m00 * fx + m.m10 * fy + m.m20 * fz + m.m30;
		double cy = m.m01 * fx + m.m11 * fy + m.m21 * fz + m.m31;
		double cz = m.m02 * fx + m.m12 * fy + m.m22 * fz + m.m32;
		WorldRenderer b = buffer.pos(cx, cy, cz);
		if (batchTextured) {
			b.tex(u, v);
		}
		b.color(argb >>> 16 & 255, argb >>> 8 & 255, argb & 255, argb >>> 24).endVertex();
	}

	private static void finishQuad() {
		if (++count >= MAX_QUADS) {
			flush();
		}
	}

	private static int packColor(float r, float g, float b, float a) {
		return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	private static int clamp(float f) {
		int i = (int) (f * 255.0f + 0.5f);
		return i < 0 ? 0 : (i > 255 ? 255 : i);
	}

	/** Draws the collected quads with the current settings (they haven't changed since the batch began). */
	private static void flush() {
		flushing = true;
		try {
			int mode = GlStateManager.stateMatrixMode;
			float r = GlStateManager.stateColorR, g = GlStateManager.stateColorG, b = GlStateManager.stateColorB,
					a = GlStateManager.stateColorA;
			// positions are already screen positions, so draw with plain matrices
			GlStateManager.matrixMode(RealOpenGLEnums.GL_PROJECTION);
			GlStateManager.pushMatrix();
			GlStateManager.loadIdentity();
			GlStateManager.matrixMode(RealOpenGLEnums.GL_MODELVIEW);
			GlStateManager.pushMatrix();
			GlStateManager.loadIdentity();
			GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
			buffer.finishDrawing();
			WorldVertexBufferUploader.func_181679_a(buffer);
			GlStateManager.matrixMode(RealOpenGLEnums.GL_PROJECTION);
			GlStateManager.popMatrix();
			GlStateManager.matrixMode(RealOpenGLEnums.GL_MODELVIEW);
			GlStateManager.popMatrix();
			GlStateManager.matrixMode(mode);
			GlStateManager.color(r, g, b, a);
		} finally {
			count = 0;
			flushing = false;
		}
	}
}
