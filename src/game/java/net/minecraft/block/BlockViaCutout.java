package net.minecraft.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;

/**
 * Treasure Client — a full-cube ViaBlock with see-through pixels (leaves,
 * copper grates, tinted glass).
 */
public class BlockViaCutout extends Block {

	public BlockViaCutout() {
		super(Material.GLASS);
	}

	public boolean isOpaqueCube(IBlockState state) {
		return false;
	}

	public BlockRenderLayer getBlockLayer() {
		return BlockRenderLayer.CUTOUT_MIPPED;
	}
}
