package net.minecraft.client;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.Mouse;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.TreasureMods;
import net.minecraft.client.gui.inventory.GuiContainer;

/**
 * Treasure Client (Dynamic FPS): a lower frame rate where full speed isn't needed, so a
 * fanless laptop cools down between fights instead of heating up drawing menus.
 *
 * <p>Calm: the title screen and server list, menus over the world (pause menu, chat,
 * death screen) and standing still without touching anything for 5 seconds. Inventories
 * and chests stay at full speed (quick refills in a fight), and anything that moves the
 * player or the camera, or any key or mouse press, ends standing still at once.
 */
public final class TCCalmFps {

	private static final long STILL_MS = 5000L;

	private static double lx, ly, lz;
	private static float lyaw, lpitch;
	private static long lInput;
	private static long stillSince = -1L;
	/** Frames in a world, and how many of them ran calm, for .lag. */
	private static int worldFrames, calmFrames;
	/** True when the frame that just ended was held to the calm rate on purpose (TCLagMonitor skips it). */
	public static boolean lastCalm;

	private TCCalmFps() {
	}

	/** The frame limit to use: {@code rate} (0 = every refresh) or the calm rate if lower. */
	public static int cap(Minecraft mc, int rate) {
		int c = calmRate(mc);
		if (mc.world != null) {
			++worldFrames;
			if (c > 0) {
				++calmFrames;
			}
		}
		lastCalm = c > 0 && (rate <= 0 || c < rate);
		return lastCalm ? c : rate;
	}

	/** One line for .lag: how much of the time in a world ran calm. Resets the count. */
	public static String describe() {
		int r = TreasureMods.calmFps();
		String s = r >= 60 || !TreasureMods.dynamicFps() ? "§7Calm FPS: §foff"
				: "§7Calm FPS: §f" + r + " §7for §f" + (worldFrames > 0 ? calmFrames * 100 / worldFrames : 0)
						+ "% §7of frames (menus, chat, standing still)";
		worldFrames = calmFrames = 0;
		return s;
	}

	/** The calm frame rate right now, or 0 for full speed. */
	public static int calmRate(Minecraft mc) {
		int r = TreasureMods.calmFps();
		if (r >= 60 || !TreasureMods.dynamicFps()) {
			stillSince = -1L;
			return 0;
		}
		GuiScreen s = mc.currentScreen;
		if (mc.world == null) {
			stillSince = -1L;
			return s != null ? r : 0;
		}
		if (s != null) {
			stillSince = -1L;
			return s instanceof GuiContainer ? 0 : r;
		}
		EntityPlayerSP p = mc.player;
		if (p == null) {
			stillSince = -1L;
			return 0;
		}
		long now = EagRuntime.steadyTimeMillis();
		boolean same = p.posX == lx && p.posY == ly && p.posZ == lz && p.rotationYaw == lyaw
				&& p.rotationPitch == lpitch && Mouse.tcLastInputMs == lInput
				// a held button (bow, eating, blocking, mining) is one press but still playing
				&& !p.isHandActive() && !p.isSwingInProgress;
		lx = p.posX;
		ly = p.posY;
		lz = p.posZ;
		lyaw = p.rotationYaw;
		lpitch = p.rotationPitch;
		lInput = Mouse.tcLastInputMs;
		if (!same || stillSince < 0L) {
			stillSince = now;
			return 0;
		}
		return now - stillSince >= STILL_MS ? r : 0;
	}
}
