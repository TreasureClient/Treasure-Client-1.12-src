package net.minecraft.tcpvp;

import java.util.List;

import net.minecraft.block.state.IBlockState;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTCMace;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Treasure Client (singleplayer, server side): the 1.21 mace smash attack.
 *
 * <p>Hitting with a mace after falling more than 1.5 blocks (not while gliding) adds
 * damage from the fall: 4 per block for the first 3 blocks, 2 per block for the next
 * 5, then 1 per block. It then knocks everyone within 3.5 blocks of the target away
 * (twice as hard after a fall over 5 blocks), stops your fall, and resets your fall
 * distance so you take no fall damage.
 */
public final class TCMace {

	private TCMace() {
	}

	public static boolean isMace(ItemStack s) {
		return s != null && !s.func_190926_b() && s.getItem() instanceof ItemTCMace;
	}

	public static boolean canSmash(EntityPlayer p) {
		return isMace(p.getHeldItemMainhand()) && p.fallDistance > 1.5F && !p.isElytraFlying();
	}

	/** Extra damage for a smash after falling {@code h} blocks. */
	public static float bonus(float h) {
		if (h <= 3.0F) {
			return 4.0F * h;
		}
		if (h <= 8.0F) {
			return 12.0F + 2.0F * (h - 3.0F);
		}
		return 22.0F + (h - 8.0F);
	}

	// 1.21 mace enchantments, kept in the item's "TCEnch" tag and shown as lore
	public static final String DENSITY = "density", BREACH = "breach", WIND_BURST = "wind_burst";

	public static ItemStack addEnch(ItemStack s, String id, int lvl) {
		if (s.func_190926_b()) {
			return s;
		}
		net.minecraft.nbt.NBTTagCompound tag = s.hasTagCompound() ? s.getTagCompound()
				: new net.minecraft.nbt.NBTTagCompound();
		net.minecraft.nbt.NBTTagCompound e = tag.getCompoundTag("TCEnch");
		e.setInteger(id, lvl);
		tag.setTag("TCEnch", e);
		net.minecraft.nbt.NBTTagCompound disp = tag.getCompoundTag("display");
		net.minecraft.nbt.NBTTagList lore = disp.getTagList("Lore", 8);
		String name = id.equals(DENSITY) ? "Density" : id.equals(BREACH) ? "Breach" : "Wind Burst";
		String[] roman = { "I", "II", "III", "IV", "V" };
		lore.appendTag(new net.minecraft.nbt.NBTTagString("\u00a77" + name + " " + roman[Math.max(0, Math.min(4, lvl - 1))]));
		disp.setTag("Lore", lore);
		tag.setTag("display", disp);
		s.setTagCompound(tag);
		return s;
	}

	/** Adds gray lore lines (enchantments 1.12 doesn't have, shown like the real ones). */
	public static ItemStack addLore(ItemStack s, String... lines) {
		if (s.func_190926_b()) {
			return s;
		}
		net.minecraft.nbt.NBTTagCompound tag = s.hasTagCompound() ? s.getTagCompound()
				: new net.minecraft.nbt.NBTTagCompound();
		net.minecraft.nbt.NBTTagCompound disp = tag.getCompoundTag("display");
		net.minecraft.nbt.NBTTagList lore = disp.getTagList("Lore", 8);
		for (String l : lines) {
			lore.appendTag(new net.minecraft.nbt.NBTTagString("\u00a77" + l));
		}
		disp.setTag("Lore", lore);
		tag.setTag("display", disp);
		s.setTagCompound(tag);
		return s;
	}

	public static int level(ItemStack s, String id) {
		if (!isMace(s) || !s.hasTagCompound()) {
			return 0;
		}
		return s.getTagCompound().getCompoundTag("TCEnch").getInteger(id);
	}

	/** Density: 0.5 more damage per block fallen per level. */
	public static float density(ItemStack s, float fell) {
		return 0.5F * level(s, DENSITY) * fell;
	}

	/** Breach level of a melee hit's mace (0 if none). */
	public static int breach(net.minecraft.util.DamageSource src) {
		String t = src.getDamageType();
		if (!"player".equals(t) && !"mob".equals(t)) {
			return 0;
		}
		Entity e = src.getEntity();
		return e instanceof EntityLivingBase ? level(((EntityLivingBase) e).getHeldItemMainhand(), BREACH) : 0;
	}

	/** Damage after armor when the hit has Breach: armor works 15% less per level (1.21). */
	public static float afterArmor(float damage, float armor, float toughness, int breach) {
		float f = 2.0F + toughness / 4.0F;
		float f1 = net.minecraft.util.math.MathHelper.clamp(armor - damage / f, armor * 0.2F, 20.0F);
		float h = net.minecraft.util.math.MathHelper.clamp(f1 / 25.0F * (1.0F - 0.15F * breach), 0.0F, 1.0F);
		return damage * (1.0F - h);
	}

	/** After a smash landed: knockback around the target, sound, dust, and stop the attacker's fall. */
	public static void afterSmash(EntityPlayer attacker, Entity target, float fell) {
		World world = attacker.world;
		if (world.isRemote) {
			return;
		}
		List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class,
				target.getEntityBoundingBox().expandXyz(3.5D));
		for (int i = 0, n = list.size(); i < n; ++i) {
			EntityLivingBase e = list.get(i);
			if (e == attacker || e == target || attacker.isOnSameTeam(e)) {
				continue;
			}
			if (e instanceof EntityPlayer && ((EntityPlayer) e).isSpectator()) {
				continue;
			}
			if (e instanceof EntityArmorStand && ((EntityArmorStand) e).hasMarker()) {
				continue;
			}
			if (e instanceof EntityTameable && ((EntityTameable) e).isOwner(attacker)) {
				continue;
			}
			if (e.getDistanceSqToEntity(target) > 3.5D * 3.5D) {
				continue;
			}
			double dx = e.posX - target.posX, dy = e.posY - target.posY, dz = e.posZ - target.posZ;
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (len == 0.0D) {
				continue;
			}
			double power = (3.5D - len) * 0.7D * (fell > 5.0F ? 2.0D : 1.0D)
					* (1.0D - e.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).getAttributeValue());
			if (power > 0.0D) {
				TCWind.push(e, dx / len * power, 0.7D, dz / len * power);
			}
		}

		if (target.onGround) {
			world.playSound((EntityPlayer) null, target.posX, target.posY, target.posZ,
					fell > 5.0F ? SoundEvents.ENTITY_GENERIC_EXPLODE : SoundEvents.BLOCK_ANVIL_LAND,
					SoundCategory.PLAYERS, fell > 5.0F ? 0.6F : 0.45F, fell > 5.0F ? 1.3F : 1.6F);
			if (world instanceof WorldServer) {
				BlockPos below = new BlockPos(target.posX, target.posY - 0.2D, target.posZ);
				IBlockState state = world.getBlockState(below);
				if (state.getMaterial().blocksMovement()) {
					((WorldServer) world).spawnParticle(EnumParticleTypes.BLOCK_DUST, target.posX, target.posY + 0.1D,
							target.posZ, fell > 5.0F ? 60 : 30, 1.2D, 0.1D, 1.2D, 0.15D, Block.getStateId(state));
				}
			}
		} else {
			world.playSound((EntityPlayer) null, target.posX, target.posY, target.posZ,
					SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0F, 0.7F);
		}

		// the attacker stops falling and takes no fall damage
		attacker.fallDistance = 0.0F;
		attacker.tcImpulseY = Double.NaN;
		if (attacker instanceof EntityPlayerMP && !(attacker instanceof TCBot)) {
			EntityPlayerMP p = (EntityPlayerMP) attacker;
			if (p.connection != null) {
				p.connection.sendPacket(new SPacketEntityVelocity(p.getEntityId(), p.posX - p.prevPosX, 0.01D,
						p.posZ - p.prevPosZ));
			}
		} else {
			attacker.motionY = 0.01D;
			attacker.velocityChanged = true;
		}

		// Wind Burst: launched back up after the smash, like 1.21 (1.2 / 1.75 / 2.2)
		int wb = level(attacker.getHeldItemMainhand(), WIND_BURST);
		if (wb > 0) {
			attacker.tcImpulseY = attacker.posY;
			TCWind.push(attacker, 0.0D, 1.2D + 0.55D * (wb - 1), 0.0D);
			world.playSound((EntityPlayer) null, attacker.posX, attacker.posY, attacker.posZ,
					SoundEvents.ENTITY_ENDERDRAGON_FLAP, SoundCategory.PLAYERS, 0.9F, 1.6F);
			if (world instanceof WorldServer) {
				// at the feet, spread sideways only: puffs around the eyes covered the whole view in
				// first person as big flat squares
				((WorldServer) world).spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL, attacker.posX, attacker.posY - 0.2D,
						attacker.posZ, 12, 0.6D, 0.0D, 0.6D, 0.02D);
			}
		}
	}
}
