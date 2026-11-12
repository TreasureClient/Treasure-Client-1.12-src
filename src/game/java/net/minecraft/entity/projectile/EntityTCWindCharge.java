package net.minecraft.entity.projectile;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tcpvp.TCWind;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

/**
 * Treasure Client: a thrown wind charge (singleplayer). Like 1.21: flies in a straight
 * line at the speed it was thrown (no gravity, no slowing down), hits an entity for 1
 * damage, then bursts (radius 1.2, knockback x1.22) where it landed.
 */
public class EntityTCWindCharge extends EntityThrowable {

	public EntityTCWindCharge(World worldIn) {
		super(worldIn);
	}

	public EntityTCWindCharge(World worldIn, double x, double y, double z) {
		super(worldIn, x, y, z);
	}

	public EntityTCWindCharge(World worldIn, EntityLivingBase thrower) {
		super(worldIn, thrower);
	}

	protected float getGravityVelocity() {
		return 0.0F;
	}

	public void onUpdate() {
		double mx = this.motionX, my = this.motionY, mz = this.motionZ;
		super.onUpdate();
		if (!this.isDead) {
			// no air drag: the throwable code slows it by 1% a tick
			this.motionX = mx;
			this.motionY = my;
			this.motionZ = mz;
		}
		if (!this.world.isRemote && this.ticksExisted > 300) {
			this.setDead();
		}
	}

	/** Hits you even when a pearl puts you right on top of it (pearl catching). */
	protected boolean tcHitsFromInside() {
		return !this.world.isRemote;
	}

	protected void onImpact(RayTraceResult result) {
		if (this.world.isRemote || this.isDead) {
			return;
		}
		double x = this.posX, y = this.posY, z = this.posZ;
		if (result.typeOfHit == RayTraceResult.Type.ENTITY && result.entityHit != null) {
			result.entityHit.attackEntityFrom(DamageSource.causeThrownDamage(this, this.getThrower()), 1.0F);
		} else if (result.typeOfHit == RayTraceResult.Type.BLOCK && result.hitVec != null) {
			// burst a quarter block off the face it hit, like 1.21
			EnumFacing side = result.sideHit;
			x = result.hitVec.xCoord + (side != null ? side.getFrontOffsetX() * 0.25D : 0.0D);
			y = result.hitVec.yCoord + (side != null ? side.getFrontOffsetY() * 0.25D : 0.0D);
			z = result.hitVec.zCoord + (side != null ? side.getFrontOffsetZ() * 0.25D : 0.0D);
		}
		TCWind.burst(this.world, this, x, y, z, 1.2F, 1.22F);
		this.setDead();
	}
}
