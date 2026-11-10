package net.minecraft.world.gen;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Biomes;
import net.minecraft.tcpvp.TCArena;
import net.minecraft.tcpvp.TCPractice;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;

/**
 * Treasure Client: builds the Practice world, a plain superflat ({@link TCArena#blockAt}),
 * then places the kit stands at spawn.
 */
public class ChunkGeneratorPractice implements IChunkGenerator {


	private final World world;

	public ChunkGeneratorPractice(World world) {
		this.world = world;
	}

	public Chunk provideChunk(int x, int z) {
		ChunkPrimer primer = new ChunkPrimer();
		for (int y = TCArena.BOTTOM; y <= TCArena.GROUND; ++y) {
			IBlockState s = TCArena.blockAt(y);
			for (int i = 0; i < 16; ++i) {
				for (int k = 0; k < 16; ++k) {
					primer.setBlockState(i, y, k, s);
				}
			}
		}
		Chunk chunk = new Chunk(this.world, primer, x, z);
		byte[] biomes = chunk.getBiomeArray();
		byte plains = (byte) Biome.getIdForBiome(Biomes.PLAINS);
		for (int l = 0; l < biomes.length; ++l) {
			biomes[l] = plains;
		}
		chunk.generateSkylightMap();
		return chunk;
	}

	public void populate(int x, int z) {
		TCPractice.populate(this.world, x, z);
	}

	public boolean generateStructures(Chunk chunkIn, int x, int z) {
		return false;
	}

	public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) {
		return Collections.<Biome.SpawnListEntry>emptyList();
	}

	@Nullable
	public BlockPos getStrongholdGen(World worldIn, String structureName, BlockPos position, boolean p_180513_4_) {
		return null;
	}

	public void recreateStructures(Chunk chunkIn, int x, int z) {
	}

	public boolean func_193414_a(World p_193414_1_, String p_193414_2_, BlockPos p_193414_3_) {
		return false;
	}
}
