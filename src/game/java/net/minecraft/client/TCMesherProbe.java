package net.minecraft.client;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.sp.internal.ClientPlatformSingleplayer;
import net.minecraft.util.text.TextComponentString;

/**
 * Treasure Client: mesher worker test (.mesher), step one of moving chunk building to the second CPU
 * core. Starts a worker there, waits for it to load the game's block types, then sends it 200
 * chunk-section-sized messages (what building one 16x16x16 piece of terrain would need: block ids
 * and light) and times how long they take to come back. Prints the results in chat and stops the
 * worker. Does nothing unless the command is typed.
 */
public final class TCMesherProbe {

	private TCMesherProbe() {
	}

	private static final int ROUNDS = 200;
	/** One section: 4096 block ids (2 bytes each) and 4096 light values. */
	private static final int SIZE = 4096 * 3;
	private static final long TIMEOUT_MS = 60000L;

	private static final int IDLE = 0, BOOTING = 1, PINGING = 2;
	private static int state = IDLE;
	private static long startMs, readyMs, pingStartNs, pingEndNs;
	private static int sent, back;
	private static String report;
	private static long mainWorkMs;

	/** The .mesher command. */
	public static void start(Minecraft mc) {
		if (state != IDLE) {
			say(mc, "§7test already running");
			return;
		}
		say(mc, "§7starting a worker on the second CPU core (takes a few seconds)...");
		// the same fixed work the worker times on its core, timed here on the game's core
		long w0 = EagRuntime.steadyTimeMillis();
		long x = workload();
		mainWorkMs = EagRuntime.steadyTimeMillis() - w0 + (x & 0L);
		startMs = EagRuntime.steadyTimeMillis();
		if (!ClientPlatformSingleplayer.tcMesherStart()) {
			say(mc, "§cthis version can't start a worker (WASM version only)");
			return;
		}
		state = BOOTING;
		sent = 0;
		back = 0;
	}

	/** Called every frame; does nothing while no test is running. */
	public static void frame(Minecraft mc) {
		if (state == IDLE) {
			return;
		}
		byte[] m;
		while ((m = ClientPlatformSingleplayer.tcMesherPoll()) != null) {
			if (m.length == 0) {
				continue;
			}
			if (m[0] == 1 && state == BOOTING) {
				readyMs = EagRuntime.steadyTimeMillis() - startMs;
				report = new String(m, 1, m.length - 1, java.nio.charset.StandardCharsets.UTF_8);
				state = PINGING;
				pingStartNs = EagRuntime.nanoTime();
				byte[] section = new byte[SIZE];
				for (int i = 0; i < ROUNDS; ++i) {
					section[0] = 2;
					ClientPlatformSingleplayer.tcMesherSend(section);
					++sent;
				}
			} else if (m[0] == 2 && state == PINGING) {
				if (++back == ROUNDS) {
					pingEndNs = EagRuntime.nanoTime();
					finish(mc);
					return;
				}
			} else if (m[0] == 3) {
				say(mc, "§cworker failed: " + new String(m, 1, m.length - 1, java.nio.charset.StandardCharsets.UTF_8));
				stop();
				return;
			}
		}
		if (EagRuntime.steadyTimeMillis() - startMs > TIMEOUT_MS) {
			say(mc, "§cworker didn't answer within 60 s (" + (state == BOOTING ? "never started" : back + " of "
					+ ROUNDS + " messages came back") + ")");
			stop();
		}
	}

	private static void finish(Minecraft mc) {
		long totalUs = (pingEndNs - pingStartNs) / 1000L;
		say(mc, "§6[Mesher test] §7worker ready after §f" + readyMs + " ms");
		say(mc, "§7  inside the worker: §f" + report);
		say(mc, "§7  same work test on the game's core: §f" + mainWorkMs + " ms");
		say(mc, "§7  " + ROUNDS + " chunk-sized messages there and back: §f" + (totalUs / 1000L) + " ms§7 total, §f"
				+ (totalUs / ROUNDS) + " µs§7 each");
		say(mc, "§7  (worker stopped; take a screenshot of these lines)");
		stop();
	}

	private static void stop() {
		ClientPlatformSingleplayer.tcMesherKill();
		state = IDLE;
	}

	/** Same as the worker's: TCMesherMain.workload. */
	private static long workload() {
		long x = 1234567L;
		for (int i = 0; i < 20000000; ++i) {
			x = x * 6364136223846793005L + 1442695040888963407L;
			x ^= x >>> 29;
		}
		return x;
	}

	private static void say(Minecraft mc, String s) {
		if (mc.ingameGUI != null) {
			mc.ingameGUI.getChatGUI().printChatMessage(new TextComponentString(s));
		}
	}
}
