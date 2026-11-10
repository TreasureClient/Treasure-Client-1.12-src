package net.minecraft.tcpvp;

import java.util.Collections;
import java.util.List;

import net.minecraft.enchantment.EnchantmentProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.play.server.SPacketExplosion;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Treasure Client (singleplayer, server side): the wind burst of a wind charge, done
 * like a 1.21 explosion that hurts nothing and breaks nothing, only pushes.
 */
public final class TCWind {

	/**
	 * Strength sent in an explosion packet that only carries knockback: the client
	 * adds the push and skips the explosion sound and particles.
	 */
	public static final float PUSH_ONLY = -1.0F;

	private TCWind() {
	}

	/**
	 * Pushes everything within {@code radius * 2} of the burst away from it, scaled by
	 * distance and by how much of the entity the burst can see, like 1.21.
	 */
	public static void burst(World world, Entity source, double x, double y, double z, float radius, float knockback) {
		if (world.isRemote) {
			return;
		}
		float f = radius * 2.0F;
		AxisAlignedBB box = new AxisAlignedBB(x - f - 1.0D, y - f - 1.0D, z - f - 1.0D, x + f + 1.0D, y + f + 1.0D,
				z + f + 1.0D);
		List<Entity> list = world.getEntitiesWithinAABBExcludingEntity(source, box);
		Vec3d center = new Vec3d(x, y, z);
		for (int i = 0, n = list.size(); i < n; ++i) {
			Entity e = list.get(i);
			if (e.isImmuneToExplosions()) {
				continue;
			}
			if (e instanceof EntityPlayer) {
				EntityPlayer p = (EntityPlayer) e;
				if (p.isSpectator() || (p.capabilities.isCreativeMode && p.capabilities.isFlying)) {
					continue;
				}
			}
			double d = Math.sqrt(e.getDistanceSq(x, y, z)) / f;
			if (d > 1.0D) {
				continue;
			}
			double dx = e.posX - x;
			double dy = (e instanceof EntityTNTPrimed ? e.posY : e.posY + e.getEyeHeight()) - y;
			double dz = e.posZ - z;
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (len == 0.0D) {
				continue;
			}
			dx /= len;
			dy /= len;
			dz /= len;
			double seen = world.getBlockDensity(center, e.getEntityBoundingBox());
			double k = (1.0D - d) * seen * knockback;
			if (e instanceof EntityLivingBase) {
				k = EnchantmentProtection.getBlastDamageReduction((EntityLivingBase) e, k);
			}
			if (e instanceof EntityPlayer) {
				// 1.21: falling after a wind burst only hurts for the part below where it hit you
				EntityPlayer p = (EntityPlayer) e;
				p.fallDistance = 0.0F;
				p.tcImpulseY = p.posY;
			}
			push(e, dx * k, dy * k, dz * k);
		}
		world.playSound((EntityPlayer) null, x, y, z, SoundEvents.ENTITY_ENDERDRAGON_FLAP, SoundCategory.NEUTRAL, 0.9F,
				1.6F + world.rand.nextFloat() * 0.3F);
		if (world instanceof WorldServer) {
			WorldServer ws = (WorldServer) world;
			ws.spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL, x, y, z, 12, 0.35D, 0.35D, 0.35D, 0.04D);
			ws.spawnParticle(EnumParticleTypes.CLOUD, x, y, z, 8, 0.3D, 0.3D, 0.3D, 0.08D);
		}
	}

	/**
	 * Adds velocity to an entity. A real player's movement is decided by their own
	 * game, so they get the push as a knockback-only explosion packet (added to their
	 * speed, like TNT) instead of a velocity packet that would replace it.
	 */
	public static void push(Entity e, double x, double y, double z) {
		if (e instanceof EntityPlayerMP && !(e instanceof TCBot)) {
			EntityPlayerMP p = (EntityPlayerMP) e;
			if (p.connection != null) {
				p.connection.sendPacket(new SPacketExplosion(p.posX, p.posY, p.posZ, PUSH_ONLY,
						Collections.<BlockPos>emptyList(), new Vec3d(x, y, z)));
			}
			return;
		}
		e.motionX += x;
		e.motionY += y;
		e.motionZ += z;
		e.velocityChanged = true;
		e.isAirBorne = true;
	}
}
