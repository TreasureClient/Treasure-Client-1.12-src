package net.minecraft.block;

import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Treasure Client: Celeritas Leaf Culling port (Karnatour, MIT; a fork of
 * Sodium Leaf Culling). With fancy leaves, skips leaf faces nobody can see
 * inside tree canopies.
 *  - HOLLOW: hides a face when the next two blocks that way are leaves (like OptiFine Smart).
 *  - SOLID_AGGRESSIVE: leaves boxed in on all 4 sides draw as solid blocks, their hidden faces skipped.
 *  - SOLID: the same, but boxed in on all 6 sides. Looks almost like normal fancy leaves.
 */
public class TCLeafCulling {

	public static final int OFF = 0, HOLLOW = 1, SOLID_AGGRESSIVE = 2, SOLID = 3;

	public static int mode = SOLID;

	public static int parseMode(String v) {
		if (v.equals("off")) return OFF;
		if (v.equals("hollow")) return HOLLOW;
		if (v.equals("aggressive")) return SOLID_AGGRESSIVE;
		return SOLID;
	}

	private static boolean isLeaves(IBlockState s) {
		return s.getBlock() instanceof BlockLeaves;
	}

	public static boolean surroundedByLeaves(IBlockAccess view, BlockPos pos) {
		BlockPos mpos = new BlockPos(0, 0, 0);
		boolean aggressive = mode == SOLID_AGGRESSIVE;
		EnumFacing[] dirs = EnumFacing._VALUES;
		for (int i = 0; i < dirs.length; ++i) {
			EnumFacing dir = dirs[i];
			if (aggressive && (dir == EnumFacing.DOWN || dir == EnumFacing.UP)) {
				continue;
			}
			mpos.setPos(pos.getX() + dir.getFrontOffsetX(), pos.getY() + dir.getFrontOffsetY(),
					pos.getZ() + dir.getFrontOffsetZ());
			IBlockState s = view.getBlockState(mpos);
			if (isLeaves(s)) {
				continue;
			}
			if (s.func_193401_d(view, mpos, dir.getOpposite()) != BlockFaceShape.SOLID) {
				return false;
			}
		}
		return true;
	}

	/** Returns true when this leaf face can be skipped. */
	public static boolean cullSide(IBlockAccess view, BlockPos pos, EnumFacing facing) {
		if (mode == HOLLOW) {
			int dx = facing.getFrontOffsetX(), dy = facing.getFrontOffsetY(), dz = facing.getFrontOffsetZ();
			BlockPos mpos = new BlockPos(pos.getX() + dx, pos.getY() + dy,
					pos.getZ() + dz);
			if (!isLeaves(view.getBlockState(mpos))) return false;
			mpos.setPos(pos.getX() + dx * 2, pos.getY() + dy * 2, pos.getZ() + dz * 2);
			return isLeaves(view.getBlockState(mpos));
		}
		if (mode == SOLID || mode == SOLID_AGGRESSIVE) {
			return surroundedByLeaves(view, pos.offset(facing));
		}
		return false;
	}

	/** Layer a block draws into: boxed-in leaves go to the solid layer in the solid modes. */
	public static BlockRenderLayer layer(Block block, IBlockState state, IBlockAccess view, BlockPos pos) {
		BlockRenderLayer layer = block.getBlockLayer();
		if (layer != BlockRenderLayer.SOLID && (mode == SOLID || mode == SOLID_AGGRESSIVE)
				&& block instanceof BlockLeaves && surroundedByLeaves(view, pos)) {
			return BlockRenderLayer.SOLID;
		}
		return layer;
	}
}
