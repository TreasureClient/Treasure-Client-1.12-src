package net.minecraft.block;

import net.lax1dude.eaglercraft.EaglercraftRandom;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Treasure Client — generic single-variant slab for the modern "ViaBlocks".
 *
 * Mirrors {@link BlockPurpurSlab}: one {@code variant=default}, a {@link #HALF}
 * property on the half form, and a Half/Double pair. One class serves every
 * ViaBlocks slab type; the per-type texture/model comes from the registry name's
 * blockstate + model assets, and drops map back to the half form via
 * {@link #halfSource} (set by {@code Block.registerViaBlocks()} after both halves
 * of the pair are built, so there is no hard-coded Blocks.* reference).
 */
public abstract class BlockViaSlab extends BlockSlab {
	public static final PropertyEnum<BlockViaSlab.Variant> VARIANT = PropertyEnum
			.<BlockViaSlab.Variant>create("variant", BlockViaSlab.Variant.class);

	/** The half form of this slab pair — what the block drops / picks as. */
	public BlockViaSlab halfSource;

	public BlockViaSlab() {
		super(Material.ROCK);
		IBlockState iblockstate = this.blockState.getBaseState();

		if (!this.isDouble()) {
			iblockstate = iblockstate.withProperty(HALF, BlockSlab.EnumBlockHalf.BOTTOM);
		}

		this.setDefaultState(iblockstate.withProperty(VARIANT, BlockViaSlab.Variant.DEFAULT));
		this.setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
	}

	private Block dropBlock() {
		return this.halfSource != null ? this.halfSource : this;
	}

	public Item getItemDropped(IBlockState state, EaglercraftRandom rand, int fortune) {
		return Item.getItemFromBlock(this.dropBlock());
	}

	public ItemStack getItem(World worldIn, BlockPos pos, IBlockState state) {
		return new ItemStack(this.dropBlock());
	}

	public IBlockState getStateFromMeta(int meta) {
		IBlockState iblockstate = this.getDefaultState().withProperty(VARIANT, BlockViaSlab.Variant.DEFAULT);

		if (!this.isDouble()) {
			iblockstate = iblockstate.withProperty(HALF,
					(meta & 8) == 0 ? BlockSlab.EnumBlockHalf.BOTTOM : BlockSlab.EnumBlockHalf.TOP);
		}

		return iblockstate;
	}

	public int getMetaFromState(IBlockState state) {
		int i = 0;

		if (!this.isDouble() && state.getValue(HALF) == BlockSlab.EnumBlockHalf.TOP) {
			i |= 8;
		}

		return i;
	}

	protected BlockStateContainer createBlockState() {
		return this.isDouble() ? new BlockStateContainer(this, new IProperty[] { VARIANT })
				: new BlockStateContainer(this, new IProperty[] { HALF, VARIANT });
	}

	public String getUnlocalizedName(int meta) {
		return super.getUnlocalizedName();
	}

	public IProperty<?> getVariantProperty() {
		return VARIANT;
	}

	public Comparable<?> getTypeForItem(ItemStack stack) {
		return BlockViaSlab.Variant.DEFAULT;
	}

	public static class Double extends BlockViaSlab {
		public boolean isDouble() {
			return true;
		}
	}

	public static class Half extends BlockViaSlab {
		public boolean isDouble() {
			return false;
		}
	}

	public static enum Variant implements IStringSerializable {
		DEFAULT;

		public String getName() {
			return "default";
		}
	}
}
