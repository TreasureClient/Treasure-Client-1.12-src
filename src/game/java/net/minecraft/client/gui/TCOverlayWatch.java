package net.minecraft.client.gui;

import net.lax1dude.eaglercraft.EagRuntime;

/**
 * Treasure Client: decides when a screen that the page overlay draws (the 1.5 main menu, the
 * Cosmetics screen) should show the game's own plain buttons instead.
 *
 * The plain buttons are only a safety net for an overlay that doesn't work at all. Once the
 * overlay has answered even once this session, a slow moment (a freeze while a world closes,
 * the browser holding back the page's timers, the game catching up several ticks in a row)
 * must never swap the new look for the old one, so it then needs 20 s of silence across at
 * least 300 drawn frames. Counting frames too means a long freeze, when neither side can run,
 * never counts as silence.
 */
public final class TCOverlayWatch {

	/** The overlay has answered at least once since the game started. */
	private static boolean everAnswered;

	private long startAt, liveAt;
	private int frames, liveFrames;

	/** The screen opened (or came back): wait for the overlay's next answer. */
	public void start() {
		this.startAt = EagRuntime.steadyTimeMillis();
		this.liveAt = 0L;
		this.frames = 0;
		this.liveFrames = 0;
	}

	/** Call once per drawn frame. */
	public void frame() {
		++this.frames;
	}

	/** The overlay just answered. */
	public void live() {
		everAnswered = true;
		this.liveAt = EagRuntime.steadyTimeMillis();
		this.liveFrames = this.frames;
	}

	public boolean answered() {
		return this.liveAt != 0L;
	}

	/** True when the game should draw its own buttons. */
	public boolean fallback() {
		long now = EagRuntime.steadyTimeMillis();
		if (!everAnswered) {
			// the overlay never worked this session: show the buttons after 3 s (and 45 frames)
			return now - this.startAt > 3000L && this.frames > 45;
		}
		long since = this.liveAt == 0L ? this.startAt : this.liveAt;
		int f = this.liveAt == 0L ? this.frames : this.frames - this.liveFrames;
		return now - since > 20000L && f > 300;
	}
}
