package net.minecraft.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Treasure Client — a modern block you use with right-click (the smithing table).
 * Right-clicking it uses it like a crafting table does, instead of placing the
 * block in your hand against it; the server opens the menu.
 */
public class BlockViaWorkstation extends Block {

	public BlockViaWorkstation(Material m) {
		super(m);
	}

	public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn,
			EnumHand hand, EnumFacing heading, float hitX, float hitY, float hitZ) {
		return true;
	}
}
