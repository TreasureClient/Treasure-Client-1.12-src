package net.minecraft.client.gui;

import java.io.IOException;

import net.minecraft.tcpvp.TCPractice;

/**
 * Treasure Client: opened by right-clicking a kit stand in a Practice world. Pick whether
 * the bot blocks with a shield; you both get the kit and are sent far out to duel.
 */
public class GuiTCKitDuel extends GuiScreen {

	private final String kit;
	private final String title;

	public GuiTCKitDuel(String kit) {
		this.kit = kit;
		String t = kit;
		for (int i = 0; i < TCPractice.KITS.length; i++) {
			if (TCPractice.KITS[i].equals(kit)) {
				t = TCPractice.KIT_TITLES[i];
			}
		}
		this.title = t + " Kit";
	}

	public void initGui() {
		this.buttonList.clear();
		int y = this.height / 4 + 24;
		this.buttonList.add(new GuiButton(0, this.width / 2 - 100, y, "Duel: bot holds a shield"));
		this.buttonList.add(new GuiButton(1, this.width / 2 - 100, y + 24, "Duel: bot doesn't block"));
		this.buttonList.add(new GuiButton(2, this.width / 2 - 100, y + 48, "Just give me the kit"));
		this.buttonList.add(new GuiButton(3, this.width / 2 - 100, y + 84, "Cancel"));
	}

	protected void actionPerformed(GuiButton button) throws IOException {
		String cmd = null;
		if (button.id == 0) {
			cmd = "/duel " + this.kit + " shield";
		} else if (button.id == 1) {
			cmd = "/duel " + this.kit + " noshield";
		} else if (button.id == 2) {
			cmd = "/kit " + this.kit;
		}
		this.mc.displayGuiScreen((GuiScreen) null);
		this.mc.setIngameFocus();
		if (cmd != null && this.mc.player != null) {
			this.mc.player.sendChatMessage(cmd);
		}
	}

	public boolean doesGuiPauseGame() {
		return false;
	}

	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();
		this.drawCenteredString(this.fontRendererObj, "§6" + this.title, this.width / 2, this.height / 4 - 8, 0xFFFFFF);
		this.drawCenteredString(this.fontRendererObj,
				"You and a bot get this kit and go far out to fight. The bot never runs out of totems.",
				this.width / 2, this.height / 4 + 6, 0xA0A0A0);
		super.drawScreen(mouseX, mouseY, partialTicks);
	}
}
