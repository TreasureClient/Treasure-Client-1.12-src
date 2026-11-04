package net.minecraft.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Treasure Client: modern trapdoors (spruce, birch... and copper). Wooden ones work like
 * the oak trapdoor; copper ones are metal (pickaxe, metal sounds) but open by hand like
 * in modern Minecraft.
 */
public class BlockViaTrapDoor extends BlockTrapDoor {

	private final boolean metalHandOpens;

	public BlockViaTrapDoor(Material material, boolean metalHandOpens) {
		super(material);
		this.metalHandOpens = metalHandOpens;
	}

	@Override
	public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn,
			EnumHand hand, EnumFacing heldItem, float side, float hitX, float hitY) {
		if (!this.metalHandOpens) {
			return super.onBlockActivated(worldIn, pos, state, playerIn, hand, heldItem, side, hitX, hitY);
		}
		state = state.cycleProperty(OPEN);
		worldIn.setBlockState(pos, state, 2);
		this.playSound(playerIn, worldIn, pos, ((Boolean) state.getValue(OPEN)).booleanValue());
		return true;
	}
}
