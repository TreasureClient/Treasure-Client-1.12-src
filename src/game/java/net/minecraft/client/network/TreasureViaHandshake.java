package net.minecraft.client.network;

import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.ViaBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ViaItems;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * Treasure Client — ViaBlocks capability handshake (Option A data source).
 *
 * On joining a server the client announces, on a plugin channel, that it
 * supports the modern ViaBlocks/ViaItems registries and advertises the id scheme
 * (the base ids; the full modern-identifier → id table lives in
 * docs/viablocks_mapping.json). An Option-A server/proxy uses this to detect our
 * client and start emitting OUR block-state ids in chunk data (which the client
 * already renders, since the blocks are registered) instead of the vanilla
 * 1.12 fallback.
 *
 * Everything here is fully defensive: any failure is swallowed so the handshake
 * can never stop the player from joining a normal (non-ViaBlocks) server.
 */
public final class TreasureViaHandshake {

	private TreasureViaHandshake() {
	}

	/** Plugin channel for the handshake (legacy 1.12 channel-name style). */
	public static final String CHANNEL = "TC|ViaBlocks";

	/** Handshake protocol version. */
	public static final int PROTOCOL = 1;

	// Server -> client message opcodes (first byte of a TC|ViaBlocks message).
	/** Server acknowledges the handshake; client flips {@link #serverSupports}. */
	public static final int OP_ACK = 1;
	/** Place one modern block: int x, int y, int z, int blockStateId. */
	public static final int OP_SET_BLOCK = 16;
	/** Place many modern blocks: int count, then count × (x, y, z, stateId). */
	public static final int OP_SET_BLOCKS = 17;

	/** Set true once a server acknowledges it will send modern ViaBlocks content. */
	public static boolean serverSupports = false;

	/** Announce ViaBlocks support to the server (called on join). */
	public static void sendHandshake(NetworkManager nm) {
		try {
			PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
			buf.writeByte(PROTOCOL);
			buf.writeVarIntToBuffer(ViaBlocks.FIRST_ID);
			buf.writeVarIntToBuffer(ViaItems.FIRST_ID);
			nm.sendPacket(new CPacketCustomPayload(CHANNEL, buf));
		} catch (Throwable t) {
			// never let the handshake break joining a server
		}
	}

	/** Handle a server message on {@link #CHANNEL} (dispatch by opcode). */
	public static void handleServer(PacketBuffer buf) {
		try {
			int op = buf.readUnsignedByte();
			switch (op) {
			case OP_ACK:
				serverSupports = true;
				break;
			case OP_SET_BLOCK:
				applyBlock(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
				break;
			case OP_SET_BLOCKS:
				int n = buf.readInt();
				for (int i = 0; i < n; i++) {
					applyBlock(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
				}
				break;
			default:
				// unknown opcode — ignore so a newer server can't break us
				break;
			}
		} catch (Throwable t) {
			// never let a malformed message break the client
		}
	}

	/**
	 * Apply one modern block in the client world. The block is set client-side
	 * from its state id via the registry we populated (ViaBlocks), so modern
	 * content renders even though the 1.12 server world doesn't hold it. Note it
	 * is visual/client-authoritative: a later server chunk/block update for the
	 * same position can overwrite it.
	 */
	private static void applyBlock(int x, int y, int z, int stateId) {
		try {
			Minecraft mc = Minecraft.getMinecraft();
			if (mc == null || mc.world == null) {
				return;
			}
			IBlockState state = Block.getStateById(stateId);
			if (state != null) {
				mc.world.setBlockState(new BlockPos(x, y, z), state, 3);
			}
		} catch (Throwable t) {
			// ignore a single bad block
		}
	}
}
