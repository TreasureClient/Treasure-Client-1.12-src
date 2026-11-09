package net.minecraft.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;

/**
 * Treasure Client — modern stairs for the "ViaBlocks" (blackstone and friends).
 * Plain 1.12 stairs made of a modern block; the look comes from the blockstate
 * and stair models named after the registry name.
 */
public class BlockViaStairs extends BlockStairs {

	public BlockViaStairs(IBlockState modelState) {
		super(modelState);
		this.setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
	}
}
