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
 * Treasure Client — generic "cross" plant for the modern "ViaBlocks" (coral
 * plants, kelp, seagrass, sweet berry bush, …).
 *
 * Renders as a 45°-crossed quad (block/cross model), is non-solid (cutout
 * render layer, no collision, not opaque / not a full cube) and — unlike
 * {@link BlockBush} — carries no "must sit on dirt" survival rule, so it can be
 * placed anywhere as decorative modern content. The per-type texture comes from
 * the registry name's blockstate + model assets.
 */
public class BlockViaCross extends Block {
	protected static final AxisAlignedBB CROSS_AABB = new AxisAlignedBB(0.1D, 0.0D, 0.1D, 0.9D, 0.8D, 0.9D);

	public BlockViaCross() {
		super(Material.PLANTS);
		this.setCreativeTab(CreativeTabs.DECORATIONS);
	}

	public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
		return CROSS_AABB;
	}

	@Nullable
	public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos) {
		return NULL_AABB;
	}

	public boolean isOpaqueCube(IBlockState state) {
		return false;
	}

	public boolean isFullCube(IBlockState state) {
		return false;
	}

	public BlockRenderLayer getBlockLayer() {
		return BlockRenderLayer.CUTOUT;
	}

	public BlockFaceShape func_193383_a(IBlockAccess p_193383_1_, IBlockState p_193383_2_, BlockPos p_193383_3_,
			EnumFacing p_193383_4_) {
		return BlockFaceShape.UNDEFINED;
	}
}
