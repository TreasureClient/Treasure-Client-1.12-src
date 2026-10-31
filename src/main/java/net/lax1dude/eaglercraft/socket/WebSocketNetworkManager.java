package net.lax1dude.eaglercraft.socket;

import java.io.IOException;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.lax1dude.eaglercraft.internal.EnumEaglerConnectionState;
import net.lax1dude.eaglercraft.internal.IWebSocketClient;
import net.lax1dude.eaglercraft.internal.IWebSocketFrame;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * Copyright (c) 2022-2024 lax1dude. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */
public class WebSocketNetworkManager extends NetworkManager {

	protected final IWebSocketClient webSocketClient;

	/**
	 * Treasure Client (C2ME smooth chunk loading): when the server sends a big
	 * burst (joining a game sends hundreds of chunk and entity packets at once),
	 * only a few ms of packets are handled per frame and the rest wait for the
	 * next frame, in the same order. The world loads in over a few frames
	 * instead of freezing the game for a moment.
	 *
	 * The time limit is per frame, not per game tick: a slow frame that runs
	 * several ticks to catch up used to get the full limit for each one, which
	 * itself caused spikes. Packets are also handled every frame (like vanilla,
	 * which runs them as scheduled tasks each frame), so the small limit still
	 * keeps up. The limit grows when packets pile up, so the game never falls
	 * far behind the server. Anything before you are in a world is handled all
	 * at once. Set from the "c2me" setting.
	 */
	public static boolean tcSpreadBursts = true;
	private static final int TC_MAX_BACKLOG = 4000;
	private static final int TC_MIN_PER_FRAME = 8;
	private static long tcFrameUsedNs;
	private static int tcFrameHandled;
	private final java.util.ArrayDeque<IWebSocketFrame> tcBacklog = new java.util.ArrayDeque<IWebSocketFrame>();

	/** Called at the start of every frame: resets this frame's packet time. */
	public static void tcFrameStart() {
		tcFrameUsedNs = 0L;
		tcFrameHandled = 0;
	}

	/** Packet time allowed per frame, more when packets are piling up. */
	/** Video Settings: packet time per frame (more when packets pile up). */
	public static long tcBaseBudgetNs = 4000000L;

	private static long tcBudgetNs(int backlog) {
		if (backlog < 200) return tcBaseBudgetNs;
		if (backlog < 1000) return tcBaseBudgetNs * 2L;
		return tcBaseBudgetNs * 4L;
	}

	/** True while packets are waiting for a later frame. */
	public boolean tcHasBacklog() {
		return !tcBacklog.isEmpty() || webSocketClient.availableBinaryFrames() > 0;
	}

	public WebSocketNetworkManager(IWebSocketClient webSocketClient) {
		super(webSocketClient.getCurrentURI());
		this.webSocketClient = webSocketClient;
	}

	public void connect() {
	}

	public EnumEaglerConnectionState getConnectStatus() {
		return webSocketClient.getState();
	}

	public void closeChannel(ITextComponent reason) {
		webSocketClient.close();
		if(nethandler != null) {
			nethandler.onDisconnect(reason);
		}
		clientDisconnected = true;
	}

	public void processReceivedPackets() throws IOException {
		if(nethandler == null) return;
		if(webSocketClient.availableStringFrames() > 0) {
			logger.warn("discarding {} string frames recieved on a binary connection", webSocketClient.availableStringFrames());
			webSocketClient.clearStringFrames();
		}
		List<IWebSocketFrame> pkts = webSocketClient.getNextBinaryFrames();

		if(pkts != null) {
			net.minecraft.client.TCLagMonitor.packets += pkts.size(); // Treasure Client: lag monitor
			tcBacklog.addAll(pkts);
		}
		if(tcBacklog.isEmpty()) {
			return;
		}

		boolean spread = tcSpreadBursts && isChannelOpen() && tcBacklog.size() < TC_MAX_BACKLOG
				&& nethandler instanceof net.minecraft.client.network.NetHandlerPlayClient;
		long start = net.lax1dude.eaglercraft.EagRuntime.nanoTime();
		long deadline = start + tcBudgetNs(tcBacklog.size()) - tcFrameUsedNs;
		if (spread && tcFrameHandled >= TC_MIN_PER_FRAME && start >= deadline) {
			return; // this frame's packet time is used up
		}
		int handled = 0;
		IWebSocketFrame next;
		while((next = tcBacklog.poll()) != null) {
			++debugPacketCounter;
			long tcT = net.lax1dude.eaglercraft.EagRuntime.nanoTime();
			int tcPart = net.minecraft.client.TCLagMonitor.L_OTHER_PKTS;
			Object tcPkt = null;
			try {
				byte[] asByteArray = next.getByteArray();
				ByteBuf nettyBuffer = Unpooled.buffer(asByteArray, asByteArray.length);
				nettyBuffer.writerIndex(asByteArray.length);
				PacketBuffer input = new PacketBuffer(nettyBuffer);
				int pktId = input.readVarIntFromBuffer();
				
				Packet pkt;
				try {
					pkt = packetState.getPacket(EnumPacketDirection.CLIENTBOUND, pktId);
				}catch(IllegalAccessException | InstantiationException ex) {
					throw new IOException("Recieved a packet with type " + pktId + " which is invalid!");
				}
				
				if(pkt == null) {
					throw new IOException("Recieved packet type " + pktId + " which is undefined in state " + packetState);
				}
				
				try {
					pkt.readPacketData(input);
				}catch(Throwable t) {
					throw new IOException("Failed to read packet type '" + pkt.getClass().getSimpleName() + "'", t);
				}
				
				tcPart = net.minecraft.client.TCLagMonitor.packetPart(pkt);
				tcPkt = pkt;
				try {
					pkt.processPacket(nethandler);
				}catch(Throwable t) {
					logger.error("Failed to process {}! It'll be skipped for debug purposes.", pkt.getClass().getSimpleName());
					logger.error(t);
				}
				
			}catch(Throwable t) {
				logger.error("Failed to process websocket frame {}! It'll be skipped for debug purposes.", debugPacketCounter);
				logger.error(t);
			}
			net.minecraft.client.TCLagMonitor.addLogic(tcPart, tcT); // Treasure Client: lag monitor
			net.minecraft.client.TCLagMonitor.addPacketType(tcPkt, net.lax1dude.eaglercraft.EagRuntime.nanoTime() - tcT);
			// at least a few packets per frame, then stop once this frame's time is used up
			++handled;
			if(spread && tcFrameHandled + handled >= TC_MIN_PER_FRAME
					&& net.lax1dude.eaglercraft.EagRuntime.nanoTime() > deadline) {
				break;
			}
		}
		tcFrameHandled += handled;
		tcFrameUsedNs += net.lax1dude.eaglercraft.EagRuntime.nanoTime() - start;
	}

	public void sendPacket(Packet pkt) {
		if(!isChannelOpen()) {
			logger.error("Packet was sent on a closed connection: {}", pkt.getClass().getSimpleName());
			return;
		}
		
		int i;
		try {
			i = packetState.getPacketId(EnumPacketDirection.SERVERBOUND, pkt);
		}catch(Throwable t) {
			logger.error("Incorrect packet for state: {}", pkt.getClass().getSimpleName());
			return;
		}
		
		temporaryBuffer.clear();
		temporaryBuffer.writeVarIntToBuffer(i);
		try {
			pkt.writePacketData(temporaryBuffer);
		}catch(IOException ex) {
			logger.error("Failed to write packet {}!", pkt.getClass().getSimpleName());
			return;
		}
		
		int len = temporaryBuffer.writerIndex();
		byte[] bytes = new byte[len];
		temporaryBuffer.getBytes(0, bytes);
		
		webSocketClient.send(bytes);
	}

	public boolean checkDisconnected() {
		if(webSocketClient.isClosed()) {
			try {
				processReceivedPackets(); // catch kick message
			} catch (IOException e) {
			}
			doClientDisconnect(new TextComponentTranslation("disconnect.endOfStream"));
			return true;
		}else {
			return false;
		}
	}

}
