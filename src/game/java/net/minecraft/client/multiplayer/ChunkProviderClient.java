package net.minecraft.client.multiplayer;

import com.carrotsearch.hppc.LongObjectHashMap;
import com.carrotsearch.hppc.LongObjectMap;
import com.carrotsearch.hppc.cursors.ObjectCursor;

import java.util.Iterator;

import javax.annotation.Nullable;

import net.lax1dude.eaglercraft.EagRuntime;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.IChunkProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChunkProviderClient implements IChunkProvider {
	private static final Logger LOGGER = LogManager.getLogger();

	/**
	 * The completely empty chunk used by ChunkProviderClient when chunkMapping
	 * doesn't contain the requested coordinates.
	 */
	private final Chunk blankChunk;
	private final LongObjectMap<Chunk> chunkMapping = new LongObjectHashMap<Chunk>(8192);

	/** Reference to the World object. */
	private final World worldObj;

	public ChunkProviderClient(World worldIn) {
		this.blankChunk = new EmptyChunk(worldIn, 0, 0);
		this.worldObj = worldIn;
	}

	/**
	 * Unload chunk from ChunkProviderClient's hashmap. Called in response to a
	 * Packet50PreChunk with its mode field set to false
	 */
	// Treasure Client (Lithium): the last chunk looked up, since the same one is asked for many times in a row
	private long tcLastKey;
	private Chunk tcLastChunk;

	private Chunk tcLookup(int x, int z) {
		long key = ChunkPos.asLong(x, z);
		Chunk c = this.tcLastChunk;
		if (c != null && this.tcLastKey == key) {
			return c;
		}
		c = this.chunkMapping.get(key);
		if (c != null && net.minecraft.util.TCLithium.enabled) {
			this.tcLastKey = key;
			this.tcLastChunk = c;
		}
		return c;
	}

	// ---------------------------------------------------------------- Bobby
	// Treasure Client, inspired by Johni0702's Bobby (our own code): when the server unloads a chunk
	// that's still inside your render distance, it stays on screen as it was, so you can see as
	// far as your render distance even when the server sends less (or after you walk away from a
	// place). Kept chunks go once they're more than one chunk past your render distance, when
	// Bobby is turned off, or when the server sends that chunk again (it replaces ours). They're
	// only kept for this world (a new world, dimension or server starts empty) and never saved.

	/** The chunks the server unloaded that we're still showing. */
	private final com.carrotsearch.hppc.LongHashSet tcKept = new com.carrotsearch.hppc.LongHashSet();
	/** At most this many (a render distance of 16 is 33 x 33 = 1089 chunks). */
	private static final int TC_KEEP_MAX = 1200;
	private int tcKeptTicks;

	/** The server unloaded this chunk: true if Bobby keeps it (then nothing else happens). */
	public boolean tcKeepOnUnload(int x, int z) {
		if (!net.minecraft.client.gui.TreasureMods.bobby() || this.tcKept.size() >= TC_KEEP_MAX) {
			return false;
		}
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
		net.minecraft.entity.Entity view = mc.getRenderViewEntity() != null ? mc.getRenderViewEntity() : mc.player;
		if (view == null || view.world != this.worldObj) {
			return false;
		}
		Chunk chunk = this.tcLookup(x, z);
		if (chunk == null || chunk.isEmpty()) {
			return false;
		}
		if (tcDistance(view, x, z) > mc.gameSettings.renderDistanceChunks) {
			return false;
		}
		this.tcKept.add(ChunkPos.asLong(x, z));
		return true;
	}

	/** How many chunks away (the bigger of the two directions, like render distance). */
	private static int tcDistance(net.minecraft.entity.Entity view, int x, int z) {
		int px = net.minecraft.util.math.MathHelper.floor(view.posX) >> 4;
		int pz = net.minecraft.util.math.MathHelper.floor(view.posZ) >> 4;
		return Math.max(Math.abs(x - px), Math.abs(z - pz));
	}

	/** Kept chunks found too far away, let go a few per tick (see tcTrimKept). */
	private long[] tcDrop;
	private int tcDropN, tcDropAt;
	/** At most this many kept chunks are let go per tick (after a teleport there can be hundreds). */
	private static final int TC_DROP_PER_TICK = 12;

	/**
	 * Once a second: find kept chunks that are now too far away (or all of them if Bobby is off),
	 * then let them go a few each tick, so a teleport doesn't drop hundreds in one frame.
	 */
	private void tcTrimKept() {
		if (this.tcDropAt < this.tcDropN) {
			int end = Math.min(this.tcDropN, this.tcDropAt + TC_DROP_PER_TICK);
			for (; this.tcDropAt < end; this.tcDropAt++) {
				long key = this.tcDrop[this.tcDropAt];
				if (!this.tcKept.contains(key)) {
					continue; // the server sent it again meanwhile
				}
				int x = (int) key, z = (int) (key >>> 32);
				this.tcKept.remove(key);
				this.unloadChunk(x, z);
				this.worldObj.markBlockRangeForRenderUpdate(x * 16, 0, z * 16, x * 16 + 15, 256, z * 16 + 15);
			}
			return;
		}
		if (this.tcKept.isEmpty() || ++this.tcKeptTicks < 20) {
			return;
		}
		this.tcKeptTicks = 0;
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
		net.minecraft.entity.Entity view = mc.getRenderViewEntity() != null ? mc.getRenderViewEntity() : mc.player;
		boolean all = !net.minecraft.client.gui.TreasureMods.bobby() || view == null;
		int keep = mc.gameSettings.renderDistanceChunks + 1;
		int n = 0;
		for (com.carrotsearch.hppc.cursors.LongCursor c : this.tcKept) {
			long key = c.value;
			int x = (int) key, z = (int) (key >>> 32);
			if (all || tcDistance(view, x, z) > keep) {
				if (this.tcDrop == null || this.tcDrop.length < this.tcKept.size()) {
					long[] grown = new long[Math.max(64, this.tcKept.size())];
					if (this.tcDrop != null) {
						System.arraycopy(this.tcDrop, 0, grown, 0, n);
					}
					this.tcDrop = grown;
				}
				this.tcDrop[n++] = key;
			}
		}
		this.tcDropN = n;
		this.tcDropAt = 0;
	}

	/** .lag / F3: how many chunks Bobby is showing that the server unloaded. */
	public int tcKeptCount() {
		return this.tcKept.size();
	}

	public void unloadChunk(int x, int z) {
		Chunk chunk = this.provideChunk(x, z);
		this.tcLastChunk = null;
		this.tcKept.remove(ChunkPos.asLong(x, z));

		if (!chunk.isEmpty()) {
			chunk.onChunkUnload();
		}

		this.chunkMapping.remove(ChunkPos.asLong(x, z));
		if (this.worldObj instanceof WorldClient) {
			((WorldClient) this.worldObj).sodium$getTracker().onChunkStatusRemoved(x, z,
					org.embeddedt.embeddium.impl.render.chunk.map.ChunkStatus.FLAG_ALL);
		}
	}

	@Nullable
	public Chunk getLoadedChunk(int x, int z) {
		return this.tcLookup(x, z);
	}

	/**
	 * loads or generates the chunk at the chunk location specified
	 */
	public Chunk loadChunk(int chunkX, int chunkZ) {
		Chunk chunk = new Chunk(this.worldObj, chunkX, chunkZ);
		this.tcLastChunk = null;
		// the server sent it again: it's a normal chunk now (the old copy is cleaned up below)
		this.tcKept.remove(ChunkPos.asLong(chunkX, chunkZ));
		Chunk old = this.chunkMapping.put(ChunkPos.asLong(chunkX, chunkZ), chunk);
		if (old != null && old != chunk) {
			this.tcReplaced(old, chunk);
		}
		chunk.setChunkLoaded(true);
		if (this.worldObj instanceof WorldClient) {
			((WorldClient) this.worldObj).sodium$getTracker().onChunkStatusAdded(chunkX, chunkZ,
					org.embeddedt.embeddium.impl.render.chunk.map.ChunkStatus.FLAG_ALL);
		}
		return chunk;
	}

	/**
	 * Treasure Client: the server sent a whole chunk we already had (Duels map resets,
	 * minigame rounds and proxies do this without unloading it first). Vanilla just
	 * dropped the old chunk, so its block entities (chests, signs, skulls, banners) stayed
	 * in the world's lists forever and the ticking ones kept running every tick: block
	 * entities grew from 43 to 4965 in 18 minutes of Duels on the same 117 chunks. The
	 * old ones are removed now (the packet brings fresh ones), and entities standing in
	 * the old chunk move to the new one so hitting and collisions still find them.
	 */
	private void tcReplaced(Chunk old, Chunk chunk) {
		try {
			old.setChunkLoaded(false);
			for (net.minecraft.tileentity.TileEntity te : old.getTileEntityMap().values()) {
				this.worldObj.markTileEntityForRemoval(te);
			}
			java.util.ArrayList<net.minecraft.entity.Entity> move = null;
			for (net.minecraft.util.ClassInheritanceMultiMap<net.minecraft.entity.Entity> list : old.getEntityLists()) {
				for (net.minecraft.entity.Entity e : list) {
					if (move == null) {
						move = new java.util.ArrayList<net.minecraft.entity.Entity>();
					}
					move.add(e);
				}
			}
			if (move != null) {
				// not chunk.addEntity: that kills an entity that drifted over the chunk edge since
				// the last tick. The world's entity update moves such an entity to the right chunk.
				net.minecraft.util.ClassInheritanceMultiMap<net.minecraft.entity.Entity>[] lists = chunk.getEntityLists();
				for (int i = 0, n = move.size(); i < n; i++) {
					net.minecraft.entity.Entity e = move.get(i);
					int k = Math.max(0, Math.min(lists.length - 1, net.minecraft.util.math.MathHelper.floor(e.posY / 16.0D)));
					e.addedToChunk = true;
					e.chunkCoordX = chunk.xPosition;
					e.chunkCoordY = k;
					e.chunkCoordZ = chunk.zPosition;
					lists[k].add(e);
				}
			}
		} catch (Throwable t) {
			LOGGER.warn("Could not clean up a replaced chunk", t);
		}
	}

	public Chunk provideChunk(int x, int z) {
		Chunk chunk = this.tcLookup(x, z);
		return chunk == null ? this.blankChunk : chunk;
	}

	/**
	 * Unloads chunks that are marked to be unloaded. This is not guaranteed to
	 * unload every such chunk.
	 */
	public boolean unloadQueuedChunks() {
		long i = EagRuntime.steadyTimeMillis();

		Iterator<ObjectCursor<Chunk>> objectiterator = this.chunkMapping.values().iterator();

		// Treasure Client: read the clock every 32 chunks, not once per chunk (a browser call each)
		boolean late = false;
		int n = 0;
		while (objectiterator.hasNext()) {
			Chunk chunk = objectiterator.next().value;
			if (!late && (++n & 31) == 0) {
				late = EagRuntime.steadyTimeMillis() - i > 5L;
			}
			chunk.onTick(late);
		}

		if (EagRuntime.steadyTimeMillis() - i > 100L) {
			LOGGER.info("Warning: Clientside chunk ticking took {} ms", (long) (EagRuntime.steadyTimeMillis() - i));
		}
		this.tcTrimKept();

		return false;
	}

	/**
	 * Converts the instance data to a readable string.
	 */
	/** Treasure Client: .lag counts loaded chunks. */
	public int tcChunkCount() {
		return this.chunkMapping.size();
	}

	public String makeString() {
		return "MultiplayerChunkCache: " + this.chunkMapping.size() + ", " + this.chunkMapping.size();
	}

	public boolean func_191062_e(int p_191062_1_, int p_191062_2_) {
		return this.chunkMapping.containsKey(ChunkPos.asLong(p_191062_1_, p_191062_2_));
	}
}