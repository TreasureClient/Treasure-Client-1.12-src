package net.lax1dude.eaglercraft.opengl;

/**
 * Treasure Client: read-only access to GlStateManager's emulated fixed-function
 * state (matrices and fog), for the Celeritas chunk renderer.
 */
public final class TCRenderState {

	private TCRenderState() {
	}

	/** Copies the current model-view matrix (column-major) into {@code out}. */
	public static void getModelView(float[] out) {
		GlStateManager.modelMatrixStack[GlStateManager.modelMatrixStackPointer].store(out);
	}

	/** Copies the current projection matrix (column-major) into {@code out}. */
	public static void getProjection(float[] out) {
		GlStateManager.projectionMatrixStack[GlStateManager.projectionMatrixStackPointer].store(out);
	}

	/**
	 * WebGL fixes a buffer's kind the first time it is bound. Binds {@code buffer}
	 * as an index buffer once, with no vertex array bound so no VAO state changes.
	 */
	public static void typeAsElementBuffer(net.lax1dude.eaglercraft.internal.IBufferGL buffer) {
		net.lax1dude.eaglercraft.internal.IVertexArrayGL prev = EaglercraftGPU.currentVertexArray;
		net.lax1dude.eaglercraft.internal.PlatformOpenGL._wglBindVertexArray(null);
		net.lax1dude.eaglercraft.internal.PlatformOpenGL._wglBindBuffer(0x8893, buffer);
		net.lax1dude.eaglercraft.internal.PlatformOpenGL._wglBindBuffer(0x8893, null);
		if (prev != null && !EaglercraftGPU.emulatedVAOs) {
			net.lax1dude.eaglercraft.internal.PlatformOpenGL._wglBindVertexArray(prev);
		}
	}

	public static boolean isFogEnabled() {
		return GlStateManager.stateFog;
	}

	public static boolean isFogExp() {
		return GlStateManager.stateFogEXP;
	}

	public static float getFogStart() {
		return GlStateManager.stateFogStart;
	}

	public static float getFogEnd() {
		return GlStateManager.stateFogEnd;
	}

	public static float getFogDensity() {
		return GlStateManager.stateFogDensity;
	}

	public static float getFogRed() {
		return GlStateManager.stateFogColorR;
	}

	public static float getFogGreen() {
		return GlStateManager.stateFogColorG;
	}

	public static float getFogBlue() {
		return GlStateManager.stateFogColorB;
	}
}
