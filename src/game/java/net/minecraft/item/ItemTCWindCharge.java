package net.minecraft.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityTCWindCharge;
import net.minecraft.init.SoundEvents;
import net.minecraft.stats.StatList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;

/**
 * Treasure Client: the modern wind charge. Thrown like a snowball (speed 1.5) with a
 * half-second cooldown; in singleplayer it flies straight without gravity and bursts
 * on impact, pushing everything nearby, including you (see {@link EntityTCWindCharge}).
 * On modern servers the client side is the same: swing, sound and the use packet.
 */
public class ItemTCWindCharge extends Item {

	public ItemTCWindCharge() {
		this.maxStackSize = 64;
		this.setCreativeTab(CreativeTabs.COMBAT);
	}

	public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
		ItemStack itemstack = player.getHeldItem(hand);
		world.playSound((EntityPlayer) null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_SNOWBALL_THROW,
				SoundCategory.NEUTRAL, 0.5F, 0.4F / (itemRand.nextFloat() * 0.4F + 0.8F));
		if (!world.isRemote) {
			EntityTCWindCharge charge = new EntityTCWindCharge(world, player);
			charge.setHeadingFromThrower(player, player.rotationPitch, player.rotationYaw, 0.0F, 1.5F, 1.0F);
			world.spawnEntityInWorld(charge);
		}
		player.getCooldownTracker().setCooldown(this, 10);
		if (!player.capabilities.isCreativeMode) {
			itemstack.func_190918_g(1);
		}
		player.addStat(StatList.getObjectUseStats(this));
		return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, itemstack);
	}
}
