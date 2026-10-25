package net.minecraft.world;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import net.lax1dude.eaglercraft.EaglercraftRandom;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Explosion {
	/** whether or not the explosion sets fire to blocks around it */
	private final boolean isFlaming;

	/** whether or not this explosion spawns smoke particles */
	private final boolean isSmoking;
	private final EaglercraftRandom explosionRNG;
	private final World worldObj;
	private final double explosionX;
	private final double explosionY;
	private final double explosionZ;
	private final Entity exploder;
	private final float explosionSize;
	private final List<BlockPos> affectedBlockPositions;
	private final Map<EntityPlayer, Vec3d> playerKnockbackMap;

	public Explosion(World worldIn, Entity entityIn, double x, double y, double z, float size,
			List<BlockPos> affectedPositions) {
		this(worldIn, entityIn, x, y, z, size, false, true, affectedPositions);
	}

	public Explosion(World worldIn, Entity entityIn, double x, double y, double z, float size, boolean flaming,
			boolean smoking, List<BlockPos> affectedPositions) {
		this(worldIn, entityIn, x, y, z, size, flaming, smoking);
		this.affectedBlockPositions.addAll(affectedPositions);
	}

	public Explosion(World worldIn, Entity entityIn, double x, double y, double z, float size, boolean flaming,
			boolean smoking) {
		this.explosionRNG = new EaglercraftRandom();
		this.affectedBlockPositions = Lists.<BlockPos>newArrayList();
		this.playerKnockbackMap = Maps.<EntityPlayer, Vec3d>newHashMap();
		this.worldObj = worldIn;
		this.exploder = entityIn;
		this.explosionSize = size;
		this.explosionX = x;
		this.explosionY = y;
		this.explosionZ = z;
		this.isFlaming = flaming;
		this.isSmoking = smoking;
	}

	/**
	 * Does the first part of the explosion (destroy blocks)
	 */
	/** Treasure Client: the 1352 ray directions, worked out once instead of every explosion. */
	private static double[] RAYS;

	private static double[] rays() {
		if (RAYS == null) {
			double[] r = new double[1352 * 3];
			int n = 0;
			for (int j = 0; j < 16; ++j) {
				for (int k = 0; k < 16; ++k) {
					for (int l = 0; l < 16; ++l) {
						if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) {
							double d0 = (double) ((float) j / 15.0F * 2.0F - 1.0F);
							double d1 = (double) ((float) k / 15.0F * 2.0F - 1.0F);
							double d2 = (double) ((float) l / 15.0F * 2.0F - 1.0F);
							double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
							r[n++] = d0 / d3;
							r[n++] = d1 / d3;
							r[n++] = d2 / d3;
						}
					}
				}
			}
			RAYS = r;
		}
		return RAYS;
	}

	/**
	 * Does the first part of the explosion (destroy blocks)
	 */
	public void doExplosionA() {
		Set<BlockPos> set = Sets.<BlockPos>newHashSet();
		double[] rays = rays();

		// Treasure Client: same result as vanilla, but each ray only looks up a
		// block once (vanilla re-read it about 3 times, once per 0.3 step) and
		// only adds it to the set once.
		for (int r = 0; r < rays.length; r += 3) {
			double d0 = rays[r];
			double d1 = rays[r + 1];
			double d2 = rays[r + 2];
			float f = this.explosionSize * (0.7F + this.worldObj.rand.nextFloat() * 0.6F);
			double d4 = this.explosionX;
			double d6 = this.explosionY;
			double d8 = this.explosionZ;

			int lastX = 0, lastY = Integer.MIN_VALUE, lastZ = 0;
			BlockPos blockpos = null;
			IBlockState iblockstate = null;
			boolean solid = false;
			float cost = 0.0F;
			boolean added = false;

			for (; f > 0.0F; f -= 0.22500001F) {
				int bx = MathHelper.floor(d4);
				int by = MathHelper.floor(d6);
				int bz = MathHelper.floor(d8);

				if (bx != lastX || by != lastY || bz != lastZ) {
					lastX = bx;
					lastY = by;
					lastZ = bz;
					blockpos = new BlockPos(bx, by, bz);
					iblockstate = this.worldObj.getBlockState(blockpos);
					solid = iblockstate.getMaterial() != Material.AIR;
					if (solid) {
						float f2 = this.exploder != null
								? this.exploder.getExplosionResistance(this, this.worldObj, blockpos, iblockstate)
								: iblockstate.getBlock().getExplosionResistance((Entity) null);
						cost = (f2 + 0.3F) * 0.3F;
					}
					added = false;
				}

				if (solid) {
					f -= cost;
				}

				if (f > 0.0F && (this.exploder == null
						|| this.exploder.verifyExplosion(this, this.worldObj, blockpos, iblockstate, f))) {
					if (!added) {
						set.add(blockpos);
						added = true;
					}
				}

				d4 += d0 * 0.30000001192092896D;
				d6 += d1 * 0.30000001192092896D;
				d8 += d2 * 0.30000001192092896D;
			}
		}

		this.affectedBlockPositions.addAll(set);
		float f3 = this.explosionSize * 2.0F;
		int k1 = MathHelper.floor(this.explosionX - (double) f3 - 1.0D);
		int l1 = MathHelper.floor(this.explosionX + (double) f3 + 1.0D);
		int i2 = MathHelper.floor(this.explosionY - (double) f3 - 1.0D);
		int i1 = MathHelper.floor(this.explosionY + (double) f3 + 1.0D);
		int j2 = MathHelper.floor(this.explosionZ - (double) f3 - 1.0D);
		int j1 = MathHelper.floor(this.explosionZ + (double) f3 + 1.0D);
		List<Entity> list = this.worldObj.getEntitiesWithinAABBExcludingEntity(this.exploder,
				new AxisAlignedBB((double) k1, (double) i2, (double) j2, (double) l1, (double) i1, (double) j1));
		Vec3d vec3d = new Vec3d(this.explosionX, this.explosionY, this.explosionZ);

		for (int k2 = 0; k2 < list.size(); ++k2) {
			Entity entity = list.get(k2);

			if (!entity.isImmuneToExplosions()) {
				double d12 = entity.getDistance(this.explosionX, this.explosionY, this.explosionZ) / (double) f3;

				if (d12 <= 1.0D) {
					double d5 = entity.posX - this.explosionX;
					double d7 = entity.posY + (double) entity.getEyeHeight() - this.explosionY;
					double d9 = entity.posZ - this.explosionZ;
					double d13 = (double) MathHelper.sqrt(d5 * d5 + d7 * d7 + d9 * d9);

					if (d13 != 0.0D) {
						d5 = d5 / d13;
						d7 = d7 / d13;
						d9 = d9 / d13;
						double d14 = (double) cachedBlockDensity(vec3d, entity.getEntityBoundingBox());
						double d10 = (1.0D - d12) * d14;
						entity.attackEntityFrom(DamageSource.causeExplosionDamage(this),
								(float) ((int) ((d10 * d10 + d10) / 2.0D * 7.0D * (double) f3 + 1.0D)));
						double d11 = d10;

						if (entity instanceof EntityLivingBase) {
							d11 = EnchantmentProtection.getBlastDamageReduction((EntityLivingBase) entity, d10);
						}

						entity.motionX += d5 * d11;
						entity.motionY += d7 * d11;
						entity.motionZ += d9 * d11;

						if (entity instanceof EntityPlayer) {
							EntityPlayer entityplayer = (EntityPlayer) entity;

							if (!entityplayer.isSpectator()
									&& (!entityplayer.isCreative() || !entityplayer.capabilities.isFlying)) {
								this.playerKnockbackMap.put(entityplayer, new Vec3d(d5 * d10, d7 * d10, d9 * d10));
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Does the second part of the explosion (sound, particles, drop spawn)
	 */
	public void doExplosionB(boolean spawnParticles) {
		this.worldObj.playSound((EntityPlayer) null, this.explosionX, this.explosionY, this.explosionZ,
				SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 4.0F,
				(1.0F + (this.worldObj.rand.nextFloat() - this.worldObj.rand.nextFloat()) * 0.2F) * 0.7F);

		if (this.explosionSize >= 2.0F && this.isSmoking) {
			this.worldObj.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE, this.explosionX, this.explosionY,
					this.explosionZ, 1.0D, 0.0D, 0.0D);
		} else {
			this.worldObj.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, this.explosionX, this.explosionY,
					this.explosionZ, 1.0D, 0.0D, 0.0D);
		}

		// Treasure Client: limit the smoke particles (vanilla spawns two for every
		// block in the blast, often over a thousand per TNT) and work out the
		// lighting once after all blocks are removed instead of after each one.
		boolean particles = spawnParticles && this.isSmoking;
		int stride = 1;
		if (particles) {
			int total = this.affectedBlockPositions.size();
			int allowed = TCPerf.takeExplosionPairs(this.worldObj, total);
			if (allowed <= 0) {
				particles = false;
			} else {
				stride = (total + allowed - 1) / allowed;
			}
		}

		boolean removedBlocks = false;
		this.worldObj.tcBeginLightBatch();
		try {
			if (this.isSmoking) {
				for (int idx = 0, n = this.affectedBlockPositions.size(); idx < n; ++idx) {
					BlockPos blockpos = this.affectedBlockPositions.get(idx);
					IBlockState iblockstate = this.worldObj.getBlockState(blockpos);
					Block block = iblockstate.getBlock();

					if (particles && idx % stride == 0) {
						double d0 = (double) ((float) blockpos.getX() + this.worldObj.rand.nextFloat());
						double d1 = (double) ((float) blockpos.getY() + this.worldObj.rand.nextFloat());
						double d2 = (double) ((float) blockpos.getZ() + this.worldObj.rand.nextFloat());
						double d3 = d0 - this.explosionX;
						double d4 = d1 - this.explosionY;
						double d5 = d2 - this.explosionZ;
						double d6 = (double) MathHelper.sqrt(d3 * d3 + d4 * d4 + d5 * d5);
						d3 = d3 / d6;
						d4 = d4 / d6;
						d5 = d5 / d6;
						double d7 = 0.5D / (d6 / (double) this.explosionSize + 0.1D);
						d7 = d7 * (double) (this.worldObj.rand.nextFloat() * this.worldObj.rand.nextFloat() + 0.3F);
						d3 = d3 * d7;
						d4 = d4 * d7;
						d5 = d5 * d7;
						this.worldObj.spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL, (d0 + this.explosionX) / 2.0D,
								(d1 + this.explosionY) / 2.0D, (d2 + this.explosionZ) / 2.0D, d3, d4, d5);
						this.worldObj.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, d0, d1, d2, d3, d4, d5);
					}

					if (iblockstate.getMaterial() != Material.AIR) {
						if (block.canDropFromExplosion(this)) {
							block.dropBlockAsItemWithChance(this.worldObj, blockpos, this.worldObj.getBlockState(blockpos),
									1.0F / this.explosionSize, 0);
						}

						this.worldObj.setBlockState(blockpos, Blocks.AIR.getDefaultState(), 3);
						removedBlocks = true;
						block.onBlockDestroyedByExplosion(this.worldObj, blockpos, this);
					}
				}
			}

			if (this.isFlaming) {
				for (BlockPos blockpos1 : this.affectedBlockPositions) {
					if (this.worldObj.getBlockState(blockpos1).getMaterial() == Material.AIR
							&& this.worldObj.getBlockState(blockpos1.down()).isFullBlock()
							&& this.explosionRNG.nextInt(3) == 0) {
						this.worldObj.setBlockState(blockpos1, Blocks.FIRE.getDefaultState());
					}
				}
			}
		} finally {
			this.worldObj.tcEndLightBatch();
		}

		if (removedBlocks) {
			// blocks changed, so remembered exposure results may be wrong now
			DENSITY_CACHE.clear();
		}
	}

	public Map<EntityPlayer, Vec3d> getPlayerKnockbackMap() {
		return this.playerKnockbackMap;
	}

	@Nullable

	/**
	 * Returns either the entity that placed the explosive block, the entity that
	 * caused the explosion or null.
	 */
	public EntityLivingBase getExplosivePlacedBy() {
		if (this.exploder == null) {
			return null;
		} else if (this.exploder instanceof EntityTNTPrimed) {
			return ((EntityTNTPrimed) this.exploder).getTntPlacedBy();
		} else {
			return this.exploder instanceof EntityLivingBase ? (EntityLivingBase) this.exploder : null;
		}
	}

	public void clearAffectedBlockPositions() {
		this.affectedBlockPositions.clear();
	}

	public List<BlockPos> getAffectedBlockPositions() {
		return this.affectedBlockPositions;
	}
	// Treasure Client: TNT cannons stack many TNT in the same spot, so the same
	// "how exposed is this entity" ray test gets repeated a lot in one tick.
	// Remember the answers for the rest of the tick.
	private static final Map<DensityKey, Float> DENSITY_CACHE = Maps.<DensityKey, Float>newHashMap();
	private static int densityWorld;
	private static long densityTime = Long.MIN_VALUE;

	private float cachedBlockDensity(Vec3d vec, AxisAlignedBB bb) {
		int id = System.identityHashCode(this.worldObj);
		long time = this.worldObj.getTotalWorldTime();
		if (id != densityWorld || time != densityTime) {
			densityWorld = id;
			densityTime = time;
			DENSITY_CACHE.clear();
		}
		DensityKey key = new DensityKey(vec, bb);
		Float cached = DENSITY_CACHE.get(key);
		if (cached != null) {
			return cached.floatValue();
		}
		float density = this.worldObj.getBlockDensity(vec, bb);
		if (DENSITY_CACHE.size() < 4096) {
			DENSITY_CACHE.put(key, Float.valueOf(density));
		}
		return density;
	}

	private static final class DensityKey {
		private final double x, y, z, minX, minY, minZ, maxX, maxY, maxZ;
		private final int hash;

		DensityKey(Vec3d v, AxisAlignedBB bb) {
			this.x = v.xCoord;
			this.y = v.yCoord;
			this.z = v.zCoord;
			this.minX = bb.minX;
			this.minY = bb.minY;
			this.minZ = bb.minZ;
			this.maxX = bb.maxX;
			this.maxY = bb.maxY;
			this.maxZ = bb.maxZ;
			int h = dh(x);
			h = h * 31 + dh(y);
			h = h * 31 + dh(z);
			h = h * 31 + dh(minX);
			h = h * 31 + dh(minY);
			h = h * 31 + dh(minZ);
			h = h * 31 + dh(maxX);
			h = h * 31 + dh(maxY);
			h = h * 31 + dh(maxZ);
			this.hash = h;
		}

		private static int dh(double d) {
			long b = Double.doubleToLongBits(d);
			return (int) (b ^ (b >>> 32));
		}

		public int hashCode() {
			return this.hash;
		}

		public boolean equals(Object o) {
			if (!(o instanceof DensityKey)) {
				return false;
			}
			DensityKey k = (DensityKey) o;
			return x == k.x && y == k.y && z == k.z && minX == k.minX && minY == k.minY && minZ == k.minZ
					&& maxX == k.maxX && maxY == k.maxY && maxZ == k.maxZ;
		}
	}
}
