package net.minecraft.client;

import java.nio.charset.StandardCharsets;

import net.lax1dude.eaglercraft.EagRuntime;

/**
 * Treasure Client: tells the page's loading screen which startup step the game
 * is on. It writes a short step name to localStorage["_eaglercraft_1.12.tcboot"]
 * (the same channel the HUD uses, so no new browser import is needed), and the
 * loading screen in OfflineDownloadTemplate.txt moves its progress bar to match.
 * After "done" (the first full frame) it never writes again.
 */
public final class TCBoot {

	private static boolean finished = false;

	private TCBoot() {
	}

	public static void stage(String id) {
		if (finished) {
			return;
		}
		try {
			EagRuntime.setStorage("tcboot", id.getBytes(StandardCharsets.UTF_8));
		} catch (Throwable t) {
		}
	}

	/** Called after every game loop pass; only the first call does anything. */
	public static void done() {
		if (!finished) {
			stage("done");
			finished = true;
		}
	}
}
