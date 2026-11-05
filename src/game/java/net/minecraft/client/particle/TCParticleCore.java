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

	private static final java.util.ArrayList<net.minecraft.util.math.AxisAlignedBB> BOXES = new java.util.ArrayList<net.minecraft.util.math.AxisAlignedBB>();
	private static final net.minecraft.util.math.BlockPos POS = new net.minecraft.util.math.BlockPos();

	/**
	 * Block boxes a particle could bump into while moving through box. Vanilla looks one
	 * block further out on every side (made for tall fences next to big mobs), which is
	 * 30-60 block lookups per particle per tick. A particle is tiny, so only the blocks its
	 * path touches are checked (1-8 lookups), into one reused list.
	 */
	static java.util.List<net.minecraft.util.math.AxisAlignedBB> boxes(net.minecraft.world.World w,
			net.minecraft.util.math.AxisAlignedBB box) {
		java.util.ArrayList<net.minecraft.util.math.AxisAlignedBB> out = BOXES;
		out.clear();
		int x0 = net.minecraft.util.math.MathHelper.floor(box.minX), x1 = net.minecraft.util.math.MathHelper.floor(box.maxX);
		int y0 = net.minecraft.util.math.MathHelper.floor(box.minY) - 1; // fences and walls stick up half a block
		int y1 = net.minecraft.util.math.MathHelper.floor(box.maxY);
		int z0 = net.minecraft.util.math.MathHelper.floor(box.minZ), z1 = net.minecraft.util.math.MathHelper.floor(box.maxZ);
		if (y1 < 0 || y0 > 255 || (x1 - x0) > 3 || (z1 - z0) > 3 || (y1 - y0) > 4) {
			return w.getCollisionBoxes((net.minecraft.entity.Entity) null, box); // fast or odd particle: vanilla
		}
		if (y0 < 0) y0 = 0;
		if (y1 > 255) y1 = 255;
		net.minecraft.util.math.BlockPos p = POS;
		for (int x = x0; x <= x1; ++x) {
			for (int z = z0; z <= z1; ++z) {
				net.minecraft.world.chunk.Chunk c = w.getChunkProvider().getLoadedChunk(x >> 4, z >> 4);
				if (c == null) {
					continue;
				}
				for (int y = y0; y <= y1; ++y) {
					net.minecraft.block.state.IBlockState s = c.getBlockState(x, y, z);
					if (s.getBlock() != net.minecraft.init.Blocks.AIR) {
						p.setPos(x, y, z);
						s.addCollisionBoxToList(w, p, box, out, (net.minecraft.entity.Entity) null, false);
					}
				}
			}
		}
		return out;
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
		if (dx * dx + dy * dy + dz * dz > FAR_SQ) {
			return true;
		}
		// behind the camera (more than 2 blocks): nobody sees it bump into a block
		net.minecraft.util.math.Vec3d v = Particle.cameraViewDir;
		return v != null && dx * v.xCoord + dy * v.yCoord + dz * v.zCoord < -2.0D;
	}
}
