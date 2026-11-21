package net.minecraft.client.gui;

import java.io.IOException;
import java.util.List;

import net.lax1dude.eaglercraft.sp.SingleplayerServerController;
import net.lax1dude.eaglercraft.sp.gui.GuiScreenIntegratedServerBusy;
import net.lax1dude.eaglercraft.sp.ipc.IPCPacket15Crashed;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

public class GuiDisconnected extends GuiScreen {
	private final String reason;
	private final ITextComponent message;
	private List<String> multilineMessage;
	private final GuiScreen parentScreen;
	private int textHeight;
	// Treasure Client: save the packets sent just before the kick (".kicklog" shows them)
	private final List<String> tcKickLog = net.minecraft.client.TCKickLog.snapshot();

	public GuiDisconnected(GuiScreen screen, String reasonLocalizationKey, ITextComponent chatComp) {
		this.parentScreen = screen;
		this.reason = I18n.format(reasonLocalizationKey);
		this.message = chatComp;
		this.tcVpn = chatComp != null && TCJavaNames.isVpnKick(chatComp.getUnformattedText());
	}

	// Treasure Client: a Java server (java://) kicked us as a VPN. Explain why and offer the
	// Java Servers settings, where a different Wisp relay can be set.
	private final boolean tcVpn;
	private static final int TC_ID_JAVA_SETTINGS = 1;

	/**
	 * Fired when a key is typed (except F11 which toggles full screen). This is the
	 * equivalent of KeyListener.keyTyped(KeyEvent e). Args : character (character
	 * on the key), keyCode (lwjgl Keyboard key code)
	 */
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
	}

	/**
	 * Adds the buttons (and other controls) to the screen in question. Called when
	 * the GUI is displayed and when the window resizes, the buttonList is cleared
	 * beforehand.
	 */
	public void initGui() {
		this.buttonList.clear();
		this.multilineMessage = this.fontRendererObj.listFormattedStringToWidth(this.message.getFormattedText(),
				this.width - 50);
		if (this.tcVpn) {
			this.multilineMessage = new java.util.ArrayList<String>(this.multilineMessage);
			this.multilineMessage.add("");
			String[] help = {
					"\u00a7eWhy this happens: \u00a77Java servers see the address of the relay that connects",
					"\u00a77your browser to them, not your own. Lots of players share that relay,",
					"\u00a77so some servers' anti-VPN checks block it. You are not on a VPN.",
					"\u00a7eWhat you can do: \u00a77open Java Servers and change the Wisp relay to a",
					"\u00a77different one (or your own), or play the server's Eaglercraft address if it has one." };
			for (String h : help) {
				this.multilineMessage.addAll(this.fontRendererObj.listFormattedStringToWidth(h, this.width - 50));
			}
		}
		this.textHeight = this.multilineMessage.size() * this.fontRendererObj.FONT_HEIGHT;
		this.buttonList.add(new GuiButton(0, this.width / 2 - 100,
				Math.min(this.height / 2 + this.textHeight / 2 + this.fontRendererObj.FONT_HEIGHT, this.height - 30),
				I18n.format("gui.toMenu")));
		if (this.tcVpn) {
			GuiButton back = this.buttonList.get(0);
			this.buttonList.add(new GuiButton(TC_ID_JAVA_SETTINGS, this.width / 2 - 100,
					Math.min(back.yPosition + 24, this.height - 6 - 20), "Java Servers settings"));
			if (back.yPosition + 24 > this.height - 26) {
				back.yPosition = this.height - 26 - 24;
			}
		}
	}

	/**
	 * Called by the controls from the buttonList when activated. (Mouse pressed for
	 * buttons)
	 */
	protected void actionPerformed(GuiButton button) throws IOException {
		if (button.id == 0) {
			this.mc.displayGuiScreen(this.parentScreen);
		} else if (button.id == TC_ID_JAVA_SETTINGS) {
			// Wispcraft's own settings window opens when you "join" its settings entry
			this.mc.displayGuiScreen(new net.minecraft.client.multiplayer.GuiConnecting(this.parentScreen, this.mc,
					new net.minecraft.client.multiplayer.ServerData("Java Servers", "settings://", false)));
		}
	}

	/**
	 * Draws the screen and all the components in it.
	 */
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();
		this.drawCenteredString(this.fontRendererObj, this.reason, this.width / 2,
				this.height / 2 - this.textHeight / 2 - this.fontRendererObj.FONT_HEIGHT * 2, 11184810);
		int i = this.height / 2 - this.textHeight / 2;

		if (this.multilineMessage != null) {
			for (String s : this.multilineMessage) {
				this.drawCenteredString(this.fontRendererObj, s, this.width / 2, i, 16777215);
				i += this.fontRendererObj.FONT_HEIGHT;
			}
		}

		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	public void updateScreen() {
		IPCPacket15Crashed[] pkt = SingleplayerServerController.worldStatusErrors();
		if (pkt != null && pkt.length > 0) {
			mc.displayGuiScreen(
					GuiScreenIntegratedServerBusy.createException(this, "singleplayer.failed.serverCrash", pkt));
		}
	}

	public static GuiScreen createRateLimitKick(GuiScreen prev) {
		return new GuiDisconnected(prev, "connect.failed", new TextComponentTranslation("disconnect.tooManyRequests"));
	}
}
