package net.minecraft.client.gui;

import java.io.IOException;
import java.util.List;

import net.minecraft.crash.CrashReport;

/**
 * Treasure Client: shown when the game crashes while playing (VanillaFix crash
 * recovery). The game leaves the world and keeps running instead of closing, and
 * the crash report can be copied to share it.
 */
public class GuiTCCrashScreen extends GuiScreen {

	private static final int ID_TITLE = 0;
	private static final int ID_COPY = 1;

	private final String cause;
	private final String report;
	private GuiButton copyButton;

	public GuiTCCrashScreen(CrashReport crash, String report) {
		Throwable t = crash.getCrashCause();
		String c = crash.getDescription();
		if (t != null) {
			c = c + ": " + t.toString();
		}
		this.cause = c;
		this.report = report;
	}

	public void initGui() {
		this.buttonList.clear();
		this.buttonList.add(new GuiButton(ID_TITLE, this.width / 2 - 154, this.height / 2 + 50, 150, 20,
				"Back to title screen"));
		this.copyButton = new GuiButton(ID_COPY, this.width / 2 + 4, this.height / 2 + 50, 150, 20,
				"Copy crash report");
		this.buttonList.add(this.copyButton);
	}

	protected void actionPerformed(GuiButton button) throws IOException {
		if (button.id == ID_TITLE) {
			goToTitle();
		} else if (button.id == ID_COPY) {
			setClipboardString(this.report);
			this.copyButton.displayString = "Copied!";
		}
	}

	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (keyCode == 1) { // Esc
			goToTitle();
		}
	}

	private void goToTitle() {
		// also stops the singleplayer world if one was open
		this.mc.shutdownIntegratedServer(new GuiMainMenu());
	}

	public boolean doesGuiPauseGame() {
		return false;
	}

	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		TreasureBackground.draw(this.width, this.height, this.zLevel);
		int cx = this.width / 2;
		int y = this.height / 2 - 70;
		this.drawCenteredString(this.fontRendererObj, "§c§lTreasure Client crashed", cx, y, 0xFFFFFF);
		y += 18;
		this.drawCenteredString(this.fontRendererObj, "VanillaFix kept the game from closing.", cx, y, 0xFFFFFF);
		y += 11;
		this.drawCenteredString(this.fontRendererObj, "You were taken out of the world, and you can keep playing.",
				cx, y, 0xFFFFFF);
		y += 18;
		List<String> lines = this.fontRendererObj.listFormattedStringToWidth(this.cause, Math.min(this.width - 40, 400));
		for (int i = 0; i < lines.size() && i < 4; ++i) {
			this.drawCenteredString(this.fontRendererObj, "§7" + lines.get(i), cx, y, 0xFFFFFF);
			y += 10;
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
	}
}
