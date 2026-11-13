package net.lax1dude.eaglercraft.sp.server.internal.wasm_gc_teavm;

import java.util.List;

import org.teavm.interop.Import;
import org.teavm.jso.JSObject;

import net.lax1dude.eaglercraft.EagUtils;
import net.lax1dude.eaglercraft.internal.IPCPacketData;
import net.lax1dude.eaglercraft.internal.PlatformRuntime;
import net.lax1dude.eaglercraft.internal.wasm_gc_teavm.WASMGCClientConfigAdapter;
import net.lax1dude.eaglercraft.sp.server.internal.ServerPlatformSingleplayer;

/**
 * Treasure Client: mesher worker test (.mesher). Runs on the second CPU core, separate from the
 * singleplayer server. It loads the game's block types (what a real chunk builder there would need
 * first), reports how long that took, measures how fast this core does a fixed piece of work, and then
 * sends back every message it gets so the game can time the round trip of chunk-sized data.
 *
 * Messages (channel "tcmesh"): the first byte says what it is. 1 = ready report (text after it),
 * 2 = echo, 3 = error (text after it).
 */
public class TCMesherMain {

	private static final String CH = "tcmesh";

	public static void _main() {
		try {
			PlatformRuntime.setThreadName("TCMesher");
			long t0 = PlatformRuntime.steadyTimeMillis();
			((WASMGCClientConfigAdapter) WASMGCClientConfigAdapter.instance).loadNative(getEaglerXOpts());
			long t1 = PlatformRuntime.steadyTimeMillis();
			net.minecraft.init.Bootstrap.register();
			long t2 = PlatformRuntime.steadyTimeMillis();
			int states = 0;
			for (net.minecraft.block.Block b : net.minecraft.block.Block.REGISTRY) {
				states += b.getBlockState().getValidStates().size();
			}
			long t3 = PlatformRuntime.steadyTimeMillis();
			long work = workload();
			long t4 = PlatformRuntime.steadyTimeMillis();
			send(1, "config " + (t1 - t0) + " ms, blocks " + (t2 - t1) + " ms (" + states + " block states), work test "
					+ (t4 - t3) + " ms (" + (work & 1) + ")");
			while (true) {
				List<IPCPacketData> list = ServerPlatformSingleplayer.recieveAllPacket();
				if (list == null) {
					EagUtils.sleep(1);
					continue;
				}
				for (int i = 0, n = list.size(); i < n; ++i) {
					IPCPacketData p = list.get(i);
					if (CH.equals(p.channel) && p.contents.length > 0) {
						byte[] c = p.contents;
						c[0] = 2;
						ServerPlatformSingleplayer.sendPacket(new IPCPacketData(CH, c));
					}
				}
			}
		} catch (Throwable t) {
			try {
				send(3, String.valueOf(t));
			} catch (Throwable tt) {
			}
		}
	}

	/** A fixed piece of plain CPU work; the game times the same thing on its own core. */
	public static long workload() {
		long x = 1234567L;
		for (int i = 0; i < 20000000; ++i) {
			x = x * 6364136223846793005L + 1442695040888963407L;
			x ^= x >>> 29;
		}
		return x;
	}

	private static void send(int kind, String text) {
		byte[] t = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		byte[] b = new byte[t.length + 1];
		b[0] = (byte) kind;
		System.arraycopy(t, 0, b, 1, t.length);
		ServerPlatformSingleplayer.sendPacket(new IPCPacketData(CH, b));
	}

	@Import(module = "platformRuntime", name = "getEaglercraftXOpts")
	private static native JSObject getEaglerXOpts();
}
