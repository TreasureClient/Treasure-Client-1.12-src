package net.minecraft.client.renderer;

import net.lax1dude.eaglercraft.EagRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.TreasureMods;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.tileentity.TileEntityEndGateway;
import net.minecraft.tileentity.TileEntityEndPortal;
import net.minecraft.tileentity.TileEntityPiston;
import net.minecraft.tileentity.TileEntityStructure;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;

/**
 * Treasure Client: Entity Culling. Skips drawing mobs and block entities
 * (chests, signs, banners, heads...) that are fully hidden behind solid blocks.
 *
 * <p>Works by casting rays from the camera to points on the thing's bounding
 * box, stepping block by block. If every ray hits a solid, opaque block before
 * reaching the box, it's hidden. Results are cached on the entity and refreshed
 * a few times a second, within a small time budget each frame, so a scene full
 * of hidden entities can't cost more than about 1.5 ms per frame.
 *
 * <p>Also does the entity render distance setting: past that many blocks, mobs,
 * players, items and block entities aren't drawn (bosses and glowing mobs are).
 *
 * <p>Our own code, inspired by tr7zw's Entity Culling mod. Works with both
 * Celeritas and the vanilla renderer.
 */
public final class TCEntityCulling {

	private TCEntityCulling() {
	}

	/** How long a "hidden" result is trusted before checking again (ms). Short, so things don't pop in late. */
	private static final long HIDDEN_RECHECK_MS = 40L;
	/** How long a "visible" result is trusted (ms). Becoming hidden late only costs a little FPS. */
	private static final long VISIBLE_RECHECK_MS = 150L;
	/** Max time spent casting rays per frame (ns). */
	public static long FRAME_BUDGET_NS = 1_500_000L; // Culling Time setting
	/** Anything this close (blocks) is always drawn. */
	public static double ALWAYS_VISIBLE_DIST_SQ = 4.0 * 4.0; // Always Draw Within setting
	/** Rays never step further than this many blocks. Past that, just draw it. */
	private static final double MAX_DIST = 128.0;

	private static boolean frameActive;
	/** Entity render distance this frame (squared blocks), or 0 when off. */
	private static double maxDistSq;
	private static long frameStartNs;
	private static boolean budgetLeft;
	private static long nowMs;
	private static double camX, camY, camZ;
	private static int camBX, camBY, camBZ;

	private static WorldClient world;
	private static Chunk lastChunk;
	private static int lastChunkX = Integer.MIN_VALUE, lastChunkZ = Integer.MIN_VALUE;

	/** Counters for the F3 screen (last frame). */
	public static int culledEntities, culledTiles, farEntities, farTiles;
	private static int frameCulledEntities, frameCulledTiles, frameFarEntities, frameFarTiles;

	/** Call once per frame before entities draw. */
	public static void beginFrame(Entity viewEntity, float partialTicks) {
		culledEntities = frameCulledEntities;
		culledTiles = frameCulledTiles;
		farEntities = frameFarEntities;
		farTiles = frameFarTiles;
		frameCulledEntities = 0;
		frameCulledTiles = 0;
		frameFarEntities = 0;
		frameFarTiles = 0;
		Minecraft mc = Minecraft.getMinecraft();
		world = mc.world;
		lastChunk = null;
		lastChunkX = lastChunkZ = Integer.MIN_VALUE;
		frameActive = false;
		maxDistSq = 0.0;
		if (world == null || viewEntity == null) {
			return;
		}
		Vec3d cam = ActiveRenderInfo.projectViewFromEntity(viewEntity, partialTicks);
		camX = cam.xCoord;
		camY = cam.yCoord;
		camZ = cam.zCoord;
		camBX = floor(camX);
		camBY = floor(camY);
		camBZ = floor(camZ);
		int dist = TreasureMods.entityDistance();
		if (dist > 0) {
			maxDistSq = (double) dist * (double) dist;
		}
		if (!TreasureMods.entityCulling()) {
			return;
		}
		if (viewEntity instanceof EntityPlayer && ((EntityPlayer) viewEntity).isSpectator()) {
			return; // spectators can look through walls
		}
		if (isOpaque(camBX, camBY, camBZ)) {
			return; // camera inside a block: don't guess
		}
		frameStartNs = EagRuntime.nanoTime();
		nowMs = frameStartNs / 1_000_000L;
		budgetLeft = true;
		frameActive = true;
	}

	/** Whether an entity should be drawn. */
	public static boolean isVisible(Entity e) {
		if (maxDistSq > 0.0 && !e.isGlowing() && !e.isBeingRidden() && !e.isRiding()
				&& !(e instanceof net.minecraft.entity.boss.EntityDragon)
				&& !(e instanceof net.minecraft.entity.boss.EntityWither)) {
			double dx = e.posX - camX, dy = e.posY - camY, dz = e.posZ - camZ;
			if (dx * dx + dy * dy + dz * dz > maxDistSq) {
				++frameFarEntities;
				return false;
			}
		}
		if (!frameActive || e instanceof EntityPlayer || e.isGlowing() || e.getAlwaysRenderNameTagForRender()
				|| e.isBeingRidden() || e.isRiding() || e.ignoreFrustumCheck) {
			return true;
		}
		long age = nowMs - e.tcCullCheckedAt;
		if (age < (e.tcCullHidden ? HIDDEN_RECHECK_MS : VISIBLE_RECHECK_MS) || !hasBudget()) {
			if (e.tcCullHidden) {
				++frameCulledEntities;
			}
			return !e.tcCullHidden;
		}
		AxisAlignedBB b = e.getRenderBoundingBox();
		boolean visible = isBoxVisible(b.minX - 0.25, b.minY - 0.25, b.minZ - 0.25, b.maxX + 0.25, b.maxY + 0.25,
				b.maxZ + 0.25);
		e.tcCullHidden = !visible;
		e.tcCullCheckedAt = nowMs;
		if (!visible) {
			++frameCulledEntities;
		}
		return visible;
	}

	/** Whether a block entity should be drawn. */
	public static boolean isVisible(TileEntity te) {
		if (maxDistSq > 0.0 && !(te instanceof TileEntityBeacon) && !(te instanceof TileEntityEndGateway)) {
			BlockPos p = te.getPos();
			double dx = p.getX() + 0.5 - camX, dy = p.getY() + 0.5 - camY, dz = p.getZ() + 0.5 - camZ;
			if (dx * dx + dy * dy + dz * dz > maxDistSq) {
				++frameFarTiles;
				return false;
			}
		}
		if (!frameActive || te instanceof TileEntityBeacon || te instanceof TileEntityEndGateway
				|| te instanceof TileEntityEndPortal || te instanceof TileEntityStructure
				|| te instanceof TileEntityPiston) {
			return true;
		}
		long age = nowMs - te.tcCullCheckedAt;
		if (age < (te.tcCullHidden ? HIDDEN_RECHECK_MS : VISIBLE_RECHECK_MS) || !hasBudget()) {
			if (te.tcCullHidden) {
				++frameCulledTiles;
			}
			return !te.tcCullHidden;
		}
		BlockPos p = te.getPos();
		// Big enough for double chests (+1 x/z), standing banners (+1 up) and wall banners (1 down)
		boolean visible = isBoxVisible(p.getX(), p.getY() - 1, p.getZ(), p.getX() + 2, p.getY() + 2, p.getZ() + 2);
		te.tcCullHidden = !visible;
		te.tcCullCheckedAt = nowMs;
		if (!visible) {
			++frameCulledTiles;
		}
		return visible;
	}

	private static boolean hasBudget() {
		if (budgetLeft && EagRuntime.nanoTime() - frameStartNs > FRAME_BUDGET_NS) {
			budgetLeft = false;
		}
		return budgetLeft;
	}

	/** True if any ray from the camera reaches the box without passing through an opaque block. */
	private static boolean isBoxVisible(double x0, double y0, double z0, double x1, double y1, double z1) {
		if (camX >= x0 && camX <= x1 && camY >= y0 && camY <= y1 && camZ >= z0 && camZ <= z1) {
			return true;
		}
		double cx = clamp(camX, x0, x1) - camX, cy = clamp(camY, y0, y1) - camY, cz = clamp(camZ, z0, z1) - camZ;
		double distSq = cx * cx + cy * cy + cz * cz;
		if (distSq < ALWAYS_VISIBLE_DIST_SQ || distSq > MAX_DIST * MAX_DIST) {
			return true;
		}
		int bx0 = floor(x0), by0 = floor(y0), bz0 = floor(z0);
		int bx1 = floor(x1), by1 = floor(y1), bz1 = floor(z1);
		// Aim at a 3x3x3 grid of points on the box (corners, edge middles, face middles, centre),
		// pulled slightly inward so rays don't run exactly along block edges. Centre first:
		// most visible things are found with the first ray.
		double ex = (x1 - x0) * 0.05, ey = (y1 - y0) * 0.05, ez = (z1 - z0) * 0.05;
		double mx = (x0 + x1) * 0.5, my = (y0 + y1) * 0.5, mz = (z0 + z1) * 0.5;
		if (ray(mx, my, mz, bx0, by0, bz0, bx1, by1, bz1)) {
			return true;
		}
		for (int i = 0; i < 27; ++i) {
			if (i == 13) {
				continue; // centre, done above
			}
			int ix = i % 3, iy = (i / 3) % 3, iz = i / 9;
			double tx = ix == 0 ? x0 + ex : ix == 1 ? mx : x1 - ex;
			double ty = iy == 0 ? y0 + ey : iy == 1 ? my : y1 - ey;
			double tz = iz == 0 ? z0 + ez : iz == 1 ? mz : z1 - ez;
			if (ray(tx, ty, tz, bx0, by0, bz0, bx1, by1, bz1)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Steps block by block from the camera toward (tx,ty,tz). Returns true if it
	 * reaches a block inside the target's block range before hitting an opaque block.
	 */
	private static boolean ray(double tx, double ty, double tz, int bx0, int by0, int bz0, int bx1, int by1,
			int bz1) {
		double dx = tx - camX, dy = ty - camY, dz = tz - camZ;
		int x = camBX, y = camBY, z = camBZ;
		int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
		int stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
		int stepZ = dz > 0 ? 1 : (dz < 0 ? -1 : 0);
		// Ray parameter t goes 0..1 from camera to target
		double tDeltaX = stepX != 0 ? Math.abs(1.0 / dx) : Double.POSITIVE_INFINITY;
		double tDeltaY = stepY != 0 ? Math.abs(1.0 / dy) : Double.POSITIVE_INFINITY;
		double tDeltaZ = stepZ != 0 ? Math.abs(1.0 / dz) : Double.POSITIVE_INFINITY;
		double tMaxX = stepX > 0 ? (x + 1 - camX) * tDeltaX : stepX < 0 ? (camX - x) * tDeltaX : Double.POSITIVE_INFINITY;
		double tMaxY = stepY > 0 ? (y + 1 - camY) * tDeltaY : stepY < 0 ? (camY - y) * tDeltaY : Double.POSITIVE_INFINITY;
		double tMaxZ = stepZ > 0 ? (z + 1 - camZ) * tDeltaZ : stepZ < 0 ? (camZ - z) * tDeltaZ : Double.POSITIVE_INFINITY;
		for (int guard = 0; guard < 512; ++guard) {
			if (tMaxX < tMaxY) {
				if (tMaxX < tMaxZ) {
					x += stepX;
					tMaxX += tDeltaX;
				} else {
					z += stepZ;
					tMaxZ += tDeltaZ;
				}
			} else if (tMaxY < tMaxZ) {
				y += stepY;
				tMaxY += tDeltaY;
			} else {
				z += stepZ;
				tMaxZ += tDeltaZ;
			}
			if (x >= bx0 && x <= bx1 && y >= by0 && y <= by1 && z >= bz0 && z <= bz1) {
				return true; // reached the target
			}
			if (isOpaque(x, y, z)) {
				return false;
			}
			if (tMaxX > 1.0 && tMaxY > 1.0 && tMaxZ > 1.0) {
				return true; // passed the target point without hitting anything
			}
		}
		return true;
	}

	private static boolean isOpaque(int x, int y, int z) {
		if (y < 0 || y > 255) {
			return false;
		}
		int cx = x >> 4, cz = z >> 4;
		Chunk c = lastChunk;
		if (cx != lastChunkX || cz != lastChunkZ) {
			c = world.getChunkFromChunkCoordsIfLoaded(cx, cz);
			lastChunk = c;
			lastChunkX = cx;
			lastChunkZ = cz;
		}
		return c != null && c.getBlockState(x, y, z).isOpaqueCube();
	}

	private static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}

	private static int floor(double v) {
		int i = (int) v;
		return v < i ? i - 1 : i;
	}

	public static String getDebugString() {
		String s = frameActive || culledEntities > 0 || culledTiles > 0
				? "Culled: " + culledEntities + " entities, " + culledTiles + " block entities"
				: (TreasureMods.entityCulling() ? "Culled: 0" : "Culling off");
		if (maxDistSq > 0.0 || farEntities > 0 || farTiles > 0) {
			s += ", Far: " + farEntities + " + " + farTiles;
		}
		return s;
	}
}
