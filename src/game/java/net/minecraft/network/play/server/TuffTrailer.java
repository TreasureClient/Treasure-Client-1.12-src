package net.minecraft.network.play.server;

import net.minecraft.network.PacketBuffer;

/** Treasure Client — reads the bytes a TuffX+ server appends after a vanilla packet. */
final class TuffTrailer {

	private TuffTrailer() {
	}

	static byte[] read(PacketBuffer buf) {
		int n = buf.readableBytes();
		if (n <= 0) {
			return null;
		}
		byte[] b = new byte[n];
		buf.readBytes(b);
		return b;
	}
}
