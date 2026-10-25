package net.minecraft.util;

import net.minecraft.client.Minecraft;

public class Timer {
	/**
	 * How many full ticks have turned over since the last call to updateTimer(),
	 * capped at 10.
	 */
	public int elapsedTicks;
	public float field_194147_b;
	public float field_194148_c;

	/**
	 * The time reported by the system clock at the last sync, in milliseconds
	 */
	private long lastSyncSysClock;
	private float field_194149_e;

	public Timer(float tps) {
		this.field_194149_e = 1000.0F / tps;
		this.lastSyncSysClock = Minecraft.getSystemTime();
	}

	/**
	 * Updates all fields of the Timer using the current time
	 */
	public void updateTimer() {
		long i = Minecraft.getSystemTime();
		this.field_194148_c = (float) (i - this.lastSyncSysClock) / this.field_194149_e;
		this.lastSyncSysClock = i;
		this.field_194147_b += this.field_194148_c;
		int newTicks = (int) this.field_194147_b;
		this.field_194147_b -= (float) newTicks;

		// Treasure Client: pace catch-up ticks. After a freeze (tab switch, big
		// GC, chunk load) the game used to run every missed tick in one frame,
		// which froze it again. Now at most 10 ticks are owed (like vanilla), and
		// they're spread over the next few frames: each frame runs about as many
		// as it normally needs, plus one while it's behind.
		this.tcOwed += newTicks;
		if (this.tcOwed > 10) {
			this.tcOwed = 10;
		}
		this.tcAverage = this.tcAverage * 0.9F + (float) newTicks * 0.1F;
		float avg = Math.max(1.0F, this.tcAverage);
		int run = Math.round(avg);
		if ((float) this.tcOwed > avg * 2.0F) {
			++run;
		}
		this.elapsedTicks = Math.min(this.tcOwed, run);
		this.tcOwed -= this.elapsedTicks;
	}

	private int tcOwed = 0;
	private float tcAverage = 1.0F;
}
