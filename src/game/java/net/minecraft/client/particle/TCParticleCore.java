package net.minecraft.client.particle;

/**
 * Treasure Client: Particle Core (own implementation, inspired by fzzyhmstrs'
 * MIT-licensed mod of the same name).
 *
 * <ul>
 * <li>Particles further than 24 blocks away move without checking for blocks
 * in their way, and particles that aren't moving skip the check entirely.
 * Looking up the blocks around every particle every tick is most of the cost
 * of ticking particles.</li>
 * <li>At most 2048 particles of each kind are kept (vanilla 16384, Treasure
 * Client 8192 with this off), so particle-heavy lobbies and explosions can't
 * pile up thousands of particles nobody can tell apart.</li>
 * </ul>
 * Particle culling (not drawing particles outside the view) is its own switch.
 */
public final class TCParticleCore {

	private TCParticleCore() {
	}

	/** Set from the "pcore" setting every tick. */
	public static boolean enabled = true;

	// Video Settings: collision check distance, particles kept per kind, off-screen margin
	public static double FAR_SQ = 24.0D * 24.0D;
	public static int maxParticles = 2048;
	public static double cullMargin = 2.0D;

	public static int maxPerLayer() {
		return enabled ? maxParticles : 8192;
	}

	/** Whether a particle can move without the block collision check. */
	static boolean skipCollision(Particle p, double x, double y, double z) {
		if (!enabled) {
			return false;
		}
		if (x == 0.0D && y == 0.0D && z == 0.0D) {
			return true;
		}
		double dx = p.posX - Particle.interpPosX, dy = p.posY - Particle.interpPosY, dz = p.posZ - Particle.interpPosZ;
		return dx * dx + dy * dy + dz * dz > FAR_SQ;
	}
}
