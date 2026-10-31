package net.minecraft.client.gui;

import java.io.IOException;

import net.lax1dude.eaglercraft.EagRuntime;

/**
 * Treasure Client: an empty screen opened while the Right Shift screen or the mod menu is
 * up. It freezes the game (no movement or attacks, the mouse is free, singleplayer pauses)
 * without showing the pause menu. The overlay draws everything on top of it.
 */
public class GuiTreasureFreeze extends GuiScreen {

	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (keyCode == 1) {
			// Escape reached the game: close and tell the overlay (it closes its screen too)
			this.mc.displayGuiScreen((GuiScreen) null);
			EagRuntime.setStorage("tcpause", new byte[] { 4 });
		}
	}

	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		// nothing: the world stays visible behind the overlay
	}
}
