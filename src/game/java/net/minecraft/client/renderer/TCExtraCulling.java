package net.minecraft.client.renderer;

import net.minecraft.client.renderer.culling.ClippingHelper;

/**
 * Treasure Client: Extra Culling. Skips drawing things that can't be seen, on top of
 * Entity Culling (which hides things behind walls):
 * <ul>
 * <li>beacon beams outside the view (a beam always counted as visible, so every beacon in
 * the loaded area drew its full-height beam every frame);</li>
 * <li>sign text when you're looking at the back of the sign.</li>
 * </ul>
 * Written for Treasure Client; it doesn't contain code from other culling mods.
 */
public final class TCExtraCulling {

	private TCExtraCulling() {
	}

	/** This frame's view frustum (camera-relative), set by EntityRenderer.renderWorldPass. */
	public static ClippingHelper frustum;

	public static boolean enabled() {
		return net.minecraft.client.gui.TreasureMods.extraCulling();
	}

	/**
	 * Whether a beacon beam at camera-relative (x, y, z) going up {@code height} blocks
	 * could be on screen. The box is padded for the beam's outer glow.
	 */
	public static boolean beamVisible(double x, double y, double z, int height) {
		ClippingHelper f = frustum;
		if (!enabled() || f == null) {
			return true;
		}
		return f.isBoxInFrustum(x - 0.5, y, z - 0.5, x + 1.5, y + Math.max(1, height), z + 1.5);
	}

	/**
	 * Whether the front of a sign can be seen. (cx, cz) is the board's centre relative to the
	 * camera and {@code angleDeg} the rotation its renderer uses; the text faces
	 * (-sin a, cos a). Behind the board the text is hidden by the board anyway.
	 */
	public static boolean signFrontVisible(double cx, double cz, float angleDeg) {
		if (!enabled()) {
			return true;
		}
		double a = Math.toRadians(angleDeg);
		double nx = -Math.sin(a), nz = Math.cos(a);
		// camera minus board centre, dotted with the front direction
		return -cx * nx - cz * nz > -0.05;
	}
}
