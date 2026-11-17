package net.minecraft.client.gui;

import java.io.IOException;
import java.util.List;

import net.lax1dude.eaglercraft.Keyboard;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.text.TextFormatting;

/**
 * Treasure Client: shown instead of Wispcraft's "log in with Microsoft" kick when a
 * java:// server wants a Microsoft login because your name belongs to a paid account.
 * Pick a suggested name (or type one) for that server only and rejoin in one click, or
 * sign in with Microsoft after all.
 */
public class GuiTCJavaName extends GuiScreen {

	private static final int ID_BACK = 0, ID_SIGNIN = 1, ID_USE = 2, ID_MORE = 3, ID_FORGET = 4, ID_SUGGEST = 10;

	private final GuiScreen parent;
	private final String uri;
	private final String triedName;
	private final boolean wasSaved;
	private List<String> ideas;
	private GuiTextField field;
	private GuiButton useBtn;
	private String error = null;

	public GuiTCJavaName(GuiScreen parent, String uri, String triedName) {
		this.parent = parent;
		this.uri = uri;
		this.triedName = triedName == null ? "" : triedName;
		String saved = TCJavaNames.get(uri);
		this.wasSaved = saved != null && saved.equalsIgnoreCase(this.triedName);
		this.ideas = TCJavaNames.suggestions(this.triedName, 4);
	}

	private int top() {
		return Math.max(8, this.height / 2 - 112);
	}

	public void initGui() {
		Keyboard.enableRepeatEvents(true);
		this.buttonList.clear();
		int cx = this.width / 2, y = top() + 72;
		for (int i = 0; i < this.ideas.size(); i++) {
			int col = i % 2, row = i / 2;
			this.buttonList.add(new GuiButton(ID_SUGGEST + i, cx - 152 + col * 154, y + row * 24, 150, 20,
					this.ideas.get(i)));
		}
		y += 50;
		this.buttonList.add(new GuiButton(ID_MORE, cx - 50, y, 100, 20, "More ideas"));
		y += 34;
		String keep = this.field != null ? this.field.getText() : "";
		this.field = new GuiTextField(0, this.fontRendererObj, cx - 152, y, 200, 20);
		this.field.setMaxStringLength(16);
		this.field.setText(keep);
		this.buttonList.add(this.useBtn = new GuiButton(ID_USE, cx + 52, y, 100, 20, "Use this name"));
		y += 32;
		this.buttonList.add(new GuiButton(ID_SIGNIN, cx - 152, y, 150, 20, "Sign in with Microsoft"));
		this.buttonList.add(new GuiButton(ID_BACK, cx + 2, y, 150, 20, "Back"));
		if (this.wasSaved) {
			this.buttonList.add(new GuiButton(ID_FORGET, cx - 100, y + 24, 200, 20, "Go back to my normal name here"));
		}
		updateUse();
	}

	private void updateUse() {
		this.useBtn.enabled = TCJavaNames.isValid(this.field.getText().trim());
	}

	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
	}

	public void updateScreen() {
		this.field.updateCursorCounter();
	}

	private void join(String name) {
		TCJavaNames.set(this.uri, name);
		this.mc.displayGuiScreen(new GuiConnecting(this.parent, this.mc,
				new ServerData(TCJavaNames.shortName(this.uri), TCJavaNames.key(this.uri), false)));
	}

	protected void actionPerformed(GuiButton b) throws IOException {
		if (b.id >= ID_SUGGEST && b.id < ID_SUGGEST + this.ideas.size()) {
			join(this.ideas.get(b.id - ID_SUGGEST));
		} else if (b.id == ID_MORE) {
			this.ideas = TCJavaNames.suggestions(this.triedName, 4);
			initGui();
		} else if (b.id == ID_USE) {
			tryTyped();
		} else if (b.id == ID_SIGNIN) {
			// Wispcraft's own sign-in window opens when you "join" its settings entry
			this.mc.displayGuiScreen(new GuiConnecting(this.parent, this.mc,
					new ServerData("Java Servers", "settings://", false)));
		} else if (b.id == ID_FORGET) {
			TCJavaNames.forget(this.uri);
			this.mc.displayGuiScreen(this.parent);
		} else if (b.id == ID_BACK) {
			this.mc.displayGuiScreen(this.parent);
		}
	}

	private void tryTyped() {
		String n = this.field.getText().trim();
		if (!TCJavaNames.isValid(n)) {
			this.error = "Names are 3 to 16 letters, numbers or _";
		} else if (n.equalsIgnoreCase(this.triedName)) {
			this.error = "That's the name the server just refused";
		} else {
			join(n);
		}
	}

	protected void keyTyped(char c, int key) throws IOException {
		if (this.field.textboxKeyTyped(c, key)) {
			this.error = null;
			updateUse();
			return;
		}
		if ((key == 28 || key == 156) && this.field.isFocused()) {
			tryTyped();
			return;
		}
		super.keyTyped(c, key);
	}

	protected void mouseClicked(int x, int y, int button) throws IOException {
		super.mouseClicked(x, y, button);
		this.field.mouseClicked(x, y, button);
	}

	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();
		int cx = this.width / 2, y = top();
		int acc = TreasureButtons.accent();
		this.drawCenteredString(this.fontRendererObj, "That name needs a Microsoft account here", cx, y, acc);
		String server = TCJavaNames.shortName(this.uri);
		this.drawCenteredString(this.fontRendererObj, TextFormatting.YELLOW + server + TextFormatting.RESET
				+ " asks for a Microsoft login for the name " + TextFormatting.YELLOW + this.triedName, cx, y + 16,
				0xFFE0E0E0);
		this.drawCenteredString(this.fontRendererObj,
				"because a paid Minecraft: Java Edition account owns that name.", cx, y + 27, 0xFFE0E0E0);
		this.drawCenteredString(this.fontRendererObj, "Pick another name just for this server. Everywhere else keeps "
				+ TextFormatting.YELLOW + displayNormal() + TextFormatting.GRAY + ".", cx, y + 44, 0xFFA0A0A0);
		this.drawCenteredString(this.fontRendererObj, "Click a name to join with it:", cx, y + 59, acc);
		int fy = y + 72 + 50 + 34;
		this.drawString(this.fontRendererObj, "Or type your own:", cx - 152, fy - 11, 0xFFA0A0A0);
		this.field.drawTextBox();
		if (this.error != null) {
			this.drawCenteredString(this.fontRendererObj, TextFormatting.RED + this.error, cx, fy + 23, 0xFFFF5555);
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	private static String displayNormal() {
		String n = net.lax1dude.eaglercraft.profile.EaglerProfile.getName();
		return n != null ? n : "your name";
	}
}
