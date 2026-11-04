package net.minecraft.block;

import javax.annotation.Nullable;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Treasure Client: a thin modern block lying on the ground (moss carpet, pale moss
 * carpet) or flat ground cover you walk through (pink petals).
 */
public class BlockViaCarpet extends Block {
	protected static final AxisAlignedBB CARPET_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.0625D, 1.0D);
	private final boolean flat;

	public BlockViaCarpet(boolean flat) {
		super(flat ? Material.PLANTS : Material.CARPET);
		this.flat = flat;
		this.setCreativeTab(CreativeTabs.DECORATIONS);
	}

	public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
		return CARPET_AABB;
	}

	@Nullable
	public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos) {
		return flat ? NULL_AABB : CARPET_AABB;
	}

	public boolean isOpaqueCube(IBlockState state) {
		return false;
	}

	public boolean isFullCube(IBlockState state) {
		return false;
	}

	public BlockRenderLayer getBlockLayer() {
		return flat ? BlockRenderLayer.CUTOUT : BlockRenderLayer.SOLID;
	}

	public boolean shouldSideBeRendered(IBlockState blockState, IBlockAccess blockAccess, BlockPos pos,
			EnumFacing side) {
		return side == EnumFacing.UP ? true : super.shouldSideBeRendered(blockState, blockAccess, pos, side);
	}

	public BlockFaceShape func_193383_a(IBlockAccess p_193383_1_, IBlockState p_193383_2_, BlockPos p_193383_3_,
			EnumFacing p_193383_4_) {
		return p_193383_4_ == EnumFacing.DOWN && !flat ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
	}
}
