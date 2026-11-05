package net.minecraft.client.multiplayer;

import com.google.common.collect.Sets;
import net.lax1dude.eaglercraft.EaglercraftRandom;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MovingSoundMinecart;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.particle.ParticleFirework;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.crash.ICrashReportDetail;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.DimensionType;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.SaveDataMemoryStorage;
import net.minecraft.world.storage.SaveHandlerMP;
import net.minecraft.world.storage.WorldInfo;
import net.optifine.PlayerControllerOF;

public class WorldClient extends World implements org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder {
	/** Treasure Client: tells the Celeritas renderer which chunks are loaded. */
	private final org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker tcChunkTracker = new org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker();

	@Override
	public org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker sodium$getTracker() {
		return this.tcChunkTracker;
	}

	/** The packets that need to be sent to the server. */
	private final NetHandlerPlayClient connection;

	/** The ChunkProviderClient instance */
	private ChunkProviderClient clientChunkProvider;
	private final Set<Entity> entityList = Sets.<Entity>newHashSet();
	private final Set<Entity> entitySpawnQueue = Sets.<Entity>newHashSet();
	private final Minecraft mc = Minecraft.getMinecraft();
	private final Set<ChunkPos> previousActiveChunkSet = Sets.<ChunkPos>newHashSet();
	private int ambienceTicks;
	protected Set<ChunkPos> viewableChunks;
	private boolean playerUpdate = false;

	public WorldClient(NetHandlerPlayClient netHandler, WorldSettings settings, int dimension,
			EnumDifficulty difficulty) {
		super(new SaveHandlerMP(), new WorldInfo(settings, "MpServer"),
				DimensionType.getById(dimension).createDimension(), true);
		this.ambienceTicks = this.rand.nextInt(12000);
		this.viewableChunks = Sets.<ChunkPos>newHashSet();
		this.connection = netHandler;
		this.getWorldInfo().setDifficulty(difficulty);
		this.setSpawnPoint(new BlockPos(8, 64, 8));
		this.provider.registerWorld(this);
		this.chunkProvider = this.createChunkProvider();
		this.mapStorage = new SaveDataMemoryStorage();
		this.calculateInitialSkylight();
		this.calculateInitialWeather();
		
		if (this.mc.playerController != null && this.mc.playerController.getClass() == PlayerControllerMP.class) {
            this.mc.playerController = new PlayerControllerOF(this.mc, netHandler);
        }
	}

	/**
	 * Runs a single tick for the world
	 */
	public void tick() {
		super.tick();
		this.setTotalWorldTime(this.getTotalWorldTime() + 1L);

		if (this.getGameRules().getBoolean("doDaylightCycle")) {
			this.setWorldTime(this.getWorldTime() + 1L);
		}

		for (int i = 0; i < 10 && !this.entitySpawnQueue.isEmpty(); ++i) {
			Entity entity = this.entitySpawnQueue.iterator().next();
			this.entitySpawnQueue.remove(entity);

			if (!this.loadedEntityList.contains(entity)) {
				this.spawnEntityInWorld(entity);
			}
		}

		this.clientChunkProvider.unloadQueuedChunks();
		this.updateBlocks();
	}

	/**
	 * Invalidates an AABB region of blocks from the receive queue, in the event
	 * that the block has been modified client-side in the intervening 80 receive
	 * ticks.
	 */
	public void invalidateBlockReceiveRegion(int x1, int y1, int z1, int x2, int y2, int z2) {
	}

	/**
	 * Creates the chunk provider for this world. Called in the constructor.
	 * Retrieves provider from worldProvider?
	 */
	protected IChunkProvider createChunkProvider() {
		this.clientChunkProvider = new ChunkProviderClient(this);
		return this.clientChunkProvider;
	}

	protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
		return allowEmpty || !this.getChunkProvider().provideChunk(x, z).isEmpty();
	}

	// Treasure Client: the list only changes when you enter another chunk or change render
	// distance, so it isn't rebuilt (hundreds of new objects) on every tick
	private int tcListR = -1, tcListX, tcListZ;

	protected void buildChunkCoordList() {
		int i = this.mc.gameSettings.renderDistanceChunks;
		int j = MathHelper.floor(this.mc.player.posX / 16.0D);
		int k = MathHelper.floor(this.mc.player.posZ / 16.0D);
		if (i == this.tcListR && j == this.tcListX && k == this.tcListZ && !this.viewableChunks.isEmpty()) {
			return;
		}
		this.tcListR = i;
		this.tcListX = j;
		this.tcListZ = k;
		this.viewableChunks.clear();

		for (int l = -i; l <= i; ++l) {
			for (int i1 = -i; i1 <= i; ++i1) {
				this.viewableChunks.add(new ChunkPos(l + j, i1 + k));
			}
		}
	}

	protected void updateBlocks() {
		this.buildChunkCoordList();

		if (this.ambienceTicks > 0) {
			--this.ambienceTicks;
		}

		this.previousActiveChunkSet.retainAll(this.viewableChunks);

		if (this.previousActiveChunkSet.size() == this.viewableChunks.size()) {
			this.previousActiveChunkSet.clear();
		}

		int i = 0;

		for (ChunkPos chunkpos : this.viewableChunks) {
			if (!this.previousActiveChunkSet.contains(chunkpos)) {
				int j = chunkpos.chunkXPos * 16;
				int k = chunkpos.chunkZPos * 16;
				Chunk chunk = this.getChunkFromChunkCoords(chunkpos.chunkXPos, chunkpos.chunkZPos);
				this.playMoodSoundAndCheckLight(j, k, chunk);
				this.previousActiveChunkSet.add(chunkpos);
				++i;

				// Treasure Client: vanilla re-checks the server's lighting in 10 chunks every tick
				// (~25,000 light checks a second for ~19 minutes after every world change, and each
				// "fix" redraws chunks). The server already sends the light, so 2 a tick is plenty.
				if (i >= 2) {
					return;
				}
			}
		}
	}

	public void doPreChunk(int chunkX, int chunkZ, boolean loadChunk) {
		if (loadChunk) {
			this.clientChunkProvider.loadChunk(chunkX, chunkZ);
		} else {
			this.clientChunkProvider.unloadChunk(chunkX, chunkZ);
			this.markBlockRangeForRenderUpdate(chunkX * 16, 0, chunkZ * 16, chunkX * 16 + 15, 256, chunkZ * 16 + 15);
		}
	}

	/**
	 * Called when an entity is spawned in the world. This includes players.
	 */
	public boolean spawnEntityInWorld(Entity entityIn) {
		boolean flag = super.spawnEntityInWorld(entityIn);
		this.entityList.add(entityIn);

		if (flag) {
			if (entityIn instanceof EntityMinecart) {
				this.mc.getSoundHandler().playSound(new MovingSoundMinecart((EntityMinecart) entityIn));
			}
		} else {
			this.entitySpawnQueue.add(entityIn);
		}

		return flag;
	}

	/**
	 * Schedule the entity for removal during the next tick. Marks the entity dead
	 * in anticipation.
	 */
	public void removeEntity(Entity entityIn) {
		super.removeEntity(entityIn);
		this.entityList.remove(entityIn);
	}

	protected void onEntityAdded(Entity entityIn) {
		super.onEntityAdded(entityIn);

		if (this.entitySpawnQueue.contains(entityIn)) {
			this.entitySpawnQueue.remove(entityIn);
		}
	}

	protected void onEntityRemoved(Entity entityIn) {
		super.onEntityRemoved(entityIn);

		if (this.entityList.contains(entityIn)) {
			if (entityIn.isEntityAlive()) {
				this.entitySpawnQueue.add(entityIn);
			} else {
				this.entityList.remove(entityIn);
			}
		}
	}

	/**
	 * Add an ID to Entity mapping to entityHashSet
	 */
	public void addEntityToWorld(int entityID, Entity entityToSpawn) {
		Entity entity = this.getEntityByID(entityID);

		if (entity != null) {
			this.removeEntity(entity);
		}

		this.entityList.add(entityToSpawn);
		entityToSpawn.setEntityId(entityID);

		if (!this.spawnEntityInWorld(entityToSpawn)) {
			this.entitySpawnQueue.add(entityToSpawn);
		}

		this.entitiesById.addKey(entityID, entityToSpawn);
	}

	@Nullable

	/**
	 * Returns the Entity with the given ID, or null if it doesn't exist in this
	 * World.
	 */
	public Entity getEntityByID(int id) {
		return (Entity) (id == this.mc.player.getEntityId() ? this.mc.player : super.getEntityByID(id));
	}

	public Entity removeEntityFromWorld(int entityID) {
		Entity entity = this.entitiesById.removeObject(entityID);

		if (entity != null) {
			this.entityList.remove(entity);
			this.removeEntity(entity);
		}

		return entity;
	}

	@Deprecated
	public boolean invalidateRegionAndSetBlock(BlockPos pos, IBlockState state) {
		int i = pos.getX();
		int j = pos.getY();
		int k = pos.getZ();
		this.invalidateBlockReceiveRegion(i, j, k, i, j, k);
		return super.setBlockState(pos, state, 3);
	}

	/**
	 * If on MP, sends a quitting packet.
	 */
	public void sendQuittingDisconnectingPacket() {
		this.connection.getNetworkManager().closeChannel(new TextComponentString("Quitting"));
	}

	/**
	 * Updates all weather states.
	 */
	protected void updateWeather() {
	}

	protected void playMoodSoundAndCheckLight(int p_147467_1_, int p_147467_2_, Chunk chunkIn) {
		// Treasure Client: no client relight pass. It re-checked the server's light in every air
		// block nearby (~5,000 checks a second) and any disagreement (often around modern
		// stand-in blocks) redrew chunks. The server sends the light; block changes still relight.

		if (this.ambienceTicks == 0) {
			this.updateLCG = this.updateLCG * 3 + 1013904223;
			int i = this.updateLCG >> 2;
			int j = i & 15;
			int k = i >> 8 & 15;
			int l = i >> 16 & 255;
			BlockPos blockpos = new BlockPos(j + p_147467_1_, l, k + p_147467_2_);
			IBlockState iblockstate = chunkIn.getBlockState(blockpos);
			j = j + p_147467_1_;
			k = k + p_147467_2_;

			if (iblockstate.getMaterial() == Material.AIR && this.getLight(blockpos) <= this.rand.nextInt(8)
					&& this.getLightFor(EnumSkyBlock.SKY, blockpos) <= 0) {
				double d0 = this.mc.player.getDistanceSq((double) j + 0.5D, (double) l + 0.5D, (double) k + 0.5D);

				if (this.mc.player != null && d0 > 4.0D && d0 < 256.0D) {
					this.playSound((double) j + 0.5D, (double) l + 0.5D, (double) k + 0.5D, SoundEvents.AMBIENT_CAVE,
							SoundCategory.AMBIENT, 0.7F, 0.8F + this.rand.nextFloat() * 0.2F, false);
					this.ambienceTicks = this.rand.nextInt(12000) + 6000;
				}
			}
		}
	}

	// Treasure Client: ambient block effects (torch smoke, dripping leaves, portal sparkles) pick
	// 1334 random spots around you every tick. The same picks are made, but with a cheap number
	// generator (3 steps a spot instead of 6 slow ones) and the last chunk remembered, instead of
	// a full world lookup for every spot. Most spots are air or empty chunk sections.
	private final EaglercraftRandom tcAmbientRand = new EaglercraftRandom();
	private final BlockPos tcAmbientPos = new BlockPos();
	private int tcAmbientSeed = 0x9E3779B9;

	public void doVoidFogParticles(int posX, int posY, int posZ) {
		ItemStack itemstack = this.mc.player.getHeldItemMainhand();
		boolean flag = this.mc.playerController.getCurrentGameType() == GameType.CREATIVE && !itemstack.func_190926_b()
				&& itemstack.getItem() == Item.getItemFromBlock(Blocks.BARRIER);
		EaglercraftRandom random = this.tcAmbientRand;
		BlockPos pos = this.tcAmbientPos;
		int seed = this.tcAmbientSeed ^ (int) this.getTotalWorldTime() * 0x61C88647;
		Chunk last = null;
		int lastCx = Integer.MIN_VALUE, lastCz = Integer.MIN_VALUE;

		for (int j = 0; j < 1334; ++j) {
			// alternate 16 and 32 block reach like vanilla; each offset is (a - b), a and b in 0..reach-1
			int sh = (j & 1) == 0 ? 4 : 5, m = (1 << sh) - 1;
			seed ^= seed << 13; seed ^= seed >>> 17; seed ^= seed << 5;
			int x = posX + (seed & m) - ((seed >>> sh) & m);
			seed ^= seed << 13; seed ^= seed >>> 17; seed ^= seed << 5;
			int y = posY + (seed & m) - ((seed >>> sh) & m);
			seed ^= seed << 13; seed ^= seed >>> 17; seed ^= seed << 5;
			int z = posZ + (seed & m) - ((seed >>> sh) & m);
			if (y < 0 || y > 255) {
				continue;
			}
			int cx = x >> 4, cz = z >> 4;
			if (cx != lastCx || cz != lastCz) {
				last = this.clientChunkProvider.getLoadedChunk(cx, cz);
				lastCx = cx;
				lastCz = cz;
			}
			if (last == null) {
				continue;
			}
			net.minecraft.world.chunk.storage.ExtendedBlockStorage sec = last.getBlockStorageArray()[y >> 4];
			if (sec == Chunk.NULL_BLOCK_STORAGE) {
				continue;
			}
			IBlockState st = sec.get(x & 15, y & 15, z & 15);
			net.minecraft.block.Block b = st.getBlock();
			if (b == Blocks.AIR) {
				continue;
			}
			pos.setPos(x, y, z);
			b.randomDisplayTick(st, this, pos, random);
			if (flag && b == Blocks.BARRIER) {
				this.spawnParticle(EnumParticleTypes.BARRIER, (double) ((float) x + 0.5F), (double) ((float) y + 0.5F),
						(double) ((float) z + 0.5F), 0.0D, 0.0D, 0.0D, new int[0]);
			}
		}
		this.tcAmbientSeed = seed;
	}

	public void showBarrierParticles(int p_184153_1_, int p_184153_2_, int p_184153_3_, int p_184153_4_,
			EaglercraftRandom random, boolean p_184153_6_, BlockPos pos) {
		int i = p_184153_1_ + this.rand.nextInt(p_184153_4_) - this.rand.nextInt(p_184153_4_);
		int j = p_184153_2_ + this.rand.nextInt(p_184153_4_) - this.rand.nextInt(p_184153_4_);
		int k = p_184153_3_ + this.rand.nextInt(p_184153_4_) - this.rand.nextInt(p_184153_4_);
		if (j < 0 || j > 255) {
			return; // Treasure Client: outside the world, nothing to show (and no barrier there)
		}
		pos.setPos(i, j, k);
		IBlockState iblockstate = this.getBlockState(pos);
		if (iblockstate.getBlock() == Blocks.AIR) {
			return; // Treasure Client: most picks are air, which shows nothing
		}
		iblockstate.getBlock().randomDisplayTick(iblockstate, this, pos, random);

		if (p_184153_6_ && iblockstate.getBlock() == Blocks.BARRIER) {
			this.spawnParticle(EnumParticleTypes.BARRIER, (double) ((float) i + 0.5F), (double) ((float) j + 0.5F),
					(double) ((float) k + 0.5F), 0.0D, 0.0D, 0.0D, new int[0]);
		}
	}

	/**
	 * also releases skins.
	 */
	public void removeAllEntities() {
		net.minecraft.util.TCLithium.removeAll(this.loadedEntityList, this.unloadedEntityList); // Treasure Client (Lithium): one pass

		for (int i = 0; i < this.unloadedEntityList.size(); ++i) {
			Entity entity = this.unloadedEntityList.get(i);
			int j = entity.chunkCoordX;
			int k = entity.chunkCoordZ;

			if (entity.addedToChunk && this.isChunkLoaded(j, k, true)) {
				this.getChunkFromChunkCoords(j, k).removeEntity(entity);
			}
		}

		for (int i1 = 0; i1 < this.unloadedEntityList.size(); ++i1) {
			this.onEntityRemoved(this.unloadedEntityList.get(i1));
		}

		this.unloadedEntityList.clear();

		for (int j1 = 0; j1 < this.loadedEntityList.size(); ++j1) {
			Entity entity1 = this.loadedEntityList.get(j1);
			Entity entity2 = entity1.getRidingEntity();

			if (entity2 != null) {
				if (!entity2.isDead && entity2.isPassenger(entity1)) {
					continue;
				}

				entity1.dismountRidingEntity();
			}

			if (entity1.isDead) {
				int k1 = entity1.chunkCoordX;
				int l = entity1.chunkCoordZ;

				if (entity1.addedToChunk && this.isChunkLoaded(k1, l, true)) {
					this.getChunkFromChunkCoords(k1, l).removeEntity(entity1);
				}

				this.loadedEntityList.remove(j1--);
				this.onEntityRemoved(entity1);
			}
		}
	}

	/**
	 * Adds some basic stats of the world to the given crash report.
	 */
	public CrashReportCategory addWorldInfoToCrashReport(CrashReport report) {
		CrashReportCategory crashreportcategory = super.addWorldInfoToCrashReport(report);
		crashreportcategory.setDetail("Forced entities", new ICrashReportDetail<String>() {
			public String call() {
				return WorldClient.this.entityList.size() + " total; " + WorldClient.this.entityList;
			}
		});
		crashreportcategory.setDetail("Retry entities", new ICrashReportDetail<String>() {
			public String call() {
				return WorldClient.this.entitySpawnQueue.size() + " total; " + WorldClient.this.entitySpawnQueue;
			}
		});
		crashreportcategory.setDetail("Server brand", new ICrashReportDetail<String>() {
			public String call() throws Exception {
				return WorldClient.this.mc.player.getServerBrand();
			}
		});
		crashreportcategory.setDetail("Server type", new ICrashReportDetail<String>() {
			public String call() throws Exception {
				return "Non-integrated multiplayer server";
			}
		});
		return crashreportcategory;
	}

	public void playSound(@Nullable EntityPlayer player, double x, double y, double z, SoundEvent soundIn,
			SoundCategory category, float volume, float pitch) {
		if (player == this.mc.player) {
			this.playSound(x, y, z, soundIn, category, volume, pitch, false);
		}
	}

	public void playSound(BlockPos pos, SoundEvent soundIn, SoundCategory category, float volume, float pitch,
			boolean distanceDelay) {
		this.playSound((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, soundIn,
				category, volume, pitch, distanceDelay);
	}

	public void playSound(double x, double y, double z, SoundEvent soundIn, SoundCategory category, float volume,
			float pitch, boolean distanceDelay) {
		double d0 = this.mc.getRenderViewEntity().getDistanceSq(x, y, z);
		PositionedSoundRecord positionedsoundrecord = new PositionedSoundRecord(soundIn, category, volume, pitch,
				(float) x, (float) y, (float) z);

		if (distanceDelay && d0 > 100.0D) {
			double d1 = Math.sqrt(d0) / 40.0D;
			this.mc.getSoundHandler().playDelayedSound(positionedsoundrecord, (int) (d1 * 20.0D));
		} else {
			this.mc.getSoundHandler().playSound(positionedsoundrecord);
		}
	}

	public void makeFireworks(double x, double y, double z, double motionX, double motionY, double motionZ,
			@Nullable NBTTagCompound compund) {
		this.mc.effectRenderer.addEffect(new ParticleFirework.Starter(this, x, y, z, motionX, motionY, motionZ,
				this.mc.effectRenderer, compund));
	}

	public void sendPacketToServer(Packet<?> packetIn) {
		this.connection.sendPacket(packetIn);
	}

	public void setWorldScoreboard(Scoreboard scoreboardIn) {
		this.worldScoreboard = scoreboardIn;
	}

	/**
	 * Sets the world time.
	 */
	public void setWorldTime(long time) {
		if (time < 0L) {
			time = -time;
			this.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
		} else {
			this.getGameRules().setOrCreateGameRule("doDaylightCycle", "true");
		}

		super.setWorldTime(time);
	}

	/**
	 * gets the world's chunk provider
	 */
	public ChunkProviderClient getChunkProvider() {
		return (ChunkProviderClient) super.getChunkProvider();
	}
	
	public boolean isPlayerUpdate() {
        return this.playerUpdate;
    }
	
	private boolean isPlayerActing() {
        if (this.mc.playerController instanceof PlayerControllerOF) {
            PlayerControllerOF playercontrollerof = (PlayerControllerOF)this.mc.playerController;
            return playercontrollerof.isActing();
        } else {
            return false;
        }
    }
	
	 /**
     * Flag 1 will cause a block update. Flag 2 will send the change to clients. Flag 4 will prevent the block from
     * being re-rendered, if this is a client world. Flag 8 will force any re-renders to run on the main thread instead
     * of the worker pool, if this is a client world and flag 4 is clear. Flag 16 will prevent observers from seeing
     * this change. Flags can be OR-ed
     */
    public boolean setBlockState(BlockPos pos, IBlockState newState, int flags) {
        this.playerUpdate = this.isPlayerActing();
        boolean flag = super.setBlockState(pos, newState, flags);
        this.playerUpdate = false;
        return flag;
    }

	/** Treasure Client: sizes for the ".perf" readout. */
	public int tcEntitySetSize() {
		return this.entityList.size();
	}

	public int tcSpawnQueueSize() {
		return this.entitySpawnQueue.size();
	}
}
