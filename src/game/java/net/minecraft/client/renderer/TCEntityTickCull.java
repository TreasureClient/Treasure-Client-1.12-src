package net.minecraft.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.TreasureMods;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/**
 * Treasure Client: entities too far away to be drawn (survival worlds load mobs and items up to
 * the render distance) are updated every 4th tick instead of every tick. Their position still
 * comes from the server; they only move in bigger steps while nobody can see them.
 */
public final class TCEntityTickCull {

	private TCEntityTickCull() {
	}

	private static int tick;
	private static double thrSq;
	private static double px, py, pz;
	private static Entity player;

	/** Once per world tick, before the entity loop. */
	public static void beginTick() {
		++tick;
		Minecraft mc = Minecraft.getMinecraft();
		player = mc.player;
		if (player != null) {
			px = player.posX;
			py = player.posY;
			pz = player.posZ;
		}
		int d = TreasureMods.entityDistance();
		double thr = Math.max(48.0D, d > 0 ? d + 8.0D : 0.0D);
		thrSq = thr * thr;
	}

	/** True when this entity's update can be skipped this tick. */
	public static boolean skip(Entity e) {
		if (player == null || e == player || ((e.getEntityId() + tick) & 3) == 0) {
			return false;
		}
		double dx = e.posX - px, dz = e.posZ - pz;
		double hSq = dx * dx + dz * dz;
		if (hSq <= thrSq) {
			return false;
		}
		double dy = e.posY - py;
		if (e.isInRangeToRenderDist(hSq + dy * dy)) {
			return false; // big mobs (ghasts, withers) are drawn from further away
		}
		if (e.isBeingRidden() || e.isRiding() || e.isGlowing() || e == player.getRidingEntity()) {
			return false;
		}
		// hold still between updates (the drawing code would otherwise move it back and forth)
		e.lastTickPosX = e.prevPosX = e.posX;
		e.lastTickPosY = e.prevPosY = e.posY;
		e.lastTickPosZ = e.prevPosZ = e.posZ;
		e.prevRotationYaw = e.rotationYaw;
		e.prevRotationPitch = e.rotationPitch;
		if (e instanceof EntityLivingBase) {
			EntityLivingBase l = (EntityLivingBase) e;
			l.prevRenderYawOffset = l.renderYawOffset;
			l.prevRotationYawHead = l.rotationYawHead;
			l.prevLimbSwingAmount = l.limbSwingAmount;
			l.prevSwingProgress = l.swingProgress;
		}
		return true;
	}
}
