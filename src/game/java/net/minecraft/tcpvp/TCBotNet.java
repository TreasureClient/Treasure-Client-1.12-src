package net.minecraft.tcpvp;

import net.lax1dude.eaglercraft.internal.EnumEaglerConnectionState;
import net.lax1dude.eaglercraft.sp.server.socket.IntegratedServerPlayerNetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.util.text.ITextComponent;

/**
 * Treasure Client: the "connection" of a practice bot. Nothing is sent anywhere and
 * nothing comes in; the bot is driven by {@link TCBotActions} on the server.
 */
public class TCBotNet extends IntegratedServerPlayerNetworkManager {

	private boolean open = true;

	public TCBotNet(String name) {
		super("tcbot:" + name);
	}

	public void connect() {
	}

	public EnumEaglerConnectionState getConnectStatus() {
		return open ? EnumEaglerConnectionState.CONNECTED : EnumEaglerConnectionState.CLOSED;
	}

	/** Kicks (e.g. "floating too long") just remove the bot. */
	public void closeChannel(ITextComponent reason) {
		this.open = false;
	}

	public void processReceivedPackets() {
	}

	public void sendPacket(Packet pkt) {
	}

	public boolean isChannelOpen() {
		return open;
	}

	public void tick() {
	}
}
