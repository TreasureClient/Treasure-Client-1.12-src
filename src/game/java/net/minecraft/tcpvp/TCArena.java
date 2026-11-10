package net.minecraft.tcpvp;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

/**
 * Treasure Client: the Practice world map, a plain superflat (bedrock, dirt, grass at
 * y 63) with nothing on it. Kit stands are placed at spawn by {@link TCPractice#populate};
 * duels happen far out on the same flat ground.
 */
public final class TCArena {

	public static final int GROUND = 63; // top grass block; players stand at 64
	public static final int BOTTOM = 59;

	private TCArena() {
	}

	/** The block of the Practice map at a height (null = air). */
	public static IBlockState blockAt(int y) {
		if (y == BOTTOM) {
			return Blocks.BEDROCK.getDefaultState();
		}
		if (y > BOTTOM && y < GROUND) {
			return Blocks.DIRT.getDefaultState();
		}
		if (y == GROUND) {
			return Blocks.GRASS.getDefaultState();
		}
		return null;
	}
}
