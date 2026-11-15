package net.minecraft.client.gui;

import java.io.IOException;
import java.util.List;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.Mouse;
import net.lax1dude.eaglercraft.profile.GuiScreenEditProfile;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.init.SoundEvents;

/**
 * Treasure Client: the Profiles screen (alt manager), opened from the face icon in the
 * top right of the title screen. Click a profile to use it; the buttons edit the
 * profile in use (name + skin + cape, or its mods), add a new one or delete one.
 * Everything saves by itself (see {@link TCProfiles}).
 */
public class GuiTCProfiles extends GuiScreen {

	private static final int CARD_H = 30, GAP = 4, CARD_W = 250;
	private static final int ID_EDIT = 0, ID_MODS = 1, ID_NEW = 2, ID_DELETE = 3, ID_DONE = 4, ASK_DELETE = 77;

	private final GuiScreen parent;
	private int scroll = 0;
	private int listTop, listBottom;
	private GuiButton newBtn, deleteBtn;

	public GuiTCProfiles(GuiScreen parent) {
		this.parent = parent;
	}

	public void initGui() {
		this.buttonList.clear();
		int cx = this.width / 2;
		int by = this.height - 52;
		this.buttonList.add(new GuiButton(ID_EDIT, cx - 152, by, 150, 20, "Edit Name & Skin"));
		this.buttonList.add(new GuiButton(ID_MODS, cx + 2, by, 150, 20, "Mod Settings"));
		this.buttonList.add(this.newBtn = new GuiButton(ID_NEW, cx - 152, by + 24, 98, 20, "New Profile"));
		this.buttonList.add(this.deleteBtn = new GuiButton(ID_DELETE, cx - 50, by + 24, 98, 20, "Delete"));
		this.buttonList.add(new GuiButton(ID_DONE, cx + 54, by + 24, 98, 20, "Done"));
		this.listTop = 44;
		this.listBottom = by - 8;
		clampScroll();
		updateButtons();
	}

	private void updateButtons() {
		this.newBtn.enabled = TCProfiles.list().size() < TCProfiles.MAX;
		this.deleteBtn.enabled = TCProfiles.list().size() > 1;
	}

	private int contentHeight() {
		return TCProfiles.list().size() * (CARD_H + GAP);
	}

	private void clampScroll() {
		int max = Math.max(0, contentHeight() - (this.listBottom - this.listTop));
		if (this.scroll > max) this.scroll = max;
		if (this.scroll < 0) this.scroll = 0;
	}

	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();
		int cx = this.width / 2;
		int acc = TreasureButtons.accent();
		this.drawCenteredString(this.fontRendererObj, "Profiles", cx, 14, acc);
		this.drawCenteredString(this.fontRendererObj, "Each profile has its own name, skin, cape and mods", cx, 27, 0xFFB8A888);

		List<TCProfiles.Profile> list = TCProfiles.list();
		int x = cx - CARD_W / 2;
		for (int i = 0; i < list.size(); i++) {
			int y = this.listTop + i * (CARD_H + GAP) - this.scroll;
			if (y < this.listTop - 1 || y + CARD_H > this.listBottom + 1) {
				continue; // only whole cards inside the list area
			}
			TCProfiles.Profile p = list.get(i);
			boolean act = p.id == TCProfiles.activeId();
			boolean hover = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
			TreasureButtons.roundedRect(x, y, CARD_W, CARD_H, hover && !act
					? TreasureButtons.lerpColor(TreasureButtons.panelBg(), acc, 0.12F) : TreasureButtons.panelBg());
			TreasureButtons.roundedOutline(x, y, CARD_W, CARD_H, act ? acc : (hover ? 0x80FFFFFF : 0x40FFFFFF));
			TCProfiles.drawFace(p, x + 5, y + 5, 20);
			this.fontRendererObj.drawStringWithShadow(p.name, (float) (x + 32), (float) (y + 6), act ? acc : 0xFFFFFFFF);
			String sub = act ? "In use" : "Click to use";
			if (p.skinLabel != null && !p.skinLabel.isEmpty()) {
				sub += " · " + p.skinLabel;
			}
			this.fontRendererObj.drawString(sub, x + 32, y + 17, act ? TreasureButtons.lerpColor(acc, 0xFF000000, 0.25F) : 0xFF9A8F7A);
			if (act) {
				String tag = "✔";
				this.fontRendererObj.drawStringWithShadow(tag, (float) (x + CARD_W - 12), (float) (y + 11), acc);
			}
		}
		if (contentHeight() > this.listBottom - this.listTop) {
			this.drawCenteredString(this.fontRendererObj, "↕ scroll", cx + CARD_W / 2 + 26, this.listBottom - 10, 0xFF6F6656);
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		super.mouseClicked(mouseX, mouseY, mouseButton);
		if (mouseButton != 0 || mouseY < this.listTop || mouseY >= this.listBottom) {
			return;
		}
		int x = this.width / 2 - CARD_W / 2;
		if (mouseX < x || mouseX >= x + CARD_W) {
			return;
		}
		List<TCProfiles.Profile> list = TCProfiles.list();
		for (int i = 0; i < list.size(); i++) {
			int y = this.listTop + i * (CARD_H + GAP) - this.scroll;
			if (mouseY >= y && mouseY < y + CARD_H) {
				TCProfiles.Profile p = list.get(i);
				if (p.id != TCProfiles.activeId()) {
					this.mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
					TCProfiles.switchTo(p.id);
				}
				return;
			}
		}
	}

	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int d = Mouse.getEventDWheel();
		if (d != 0) {
			this.scroll += d > 0 ? -(CARD_H + GAP) : (CARD_H + GAP);
			clampScroll();
		}
	}

	protected void actionPerformed(GuiButton button) throws IOException {
		switch (button.id) {
		case ID_EDIT:
			this.mc.displayGuiScreen(new GuiScreenEditProfile(this));
			break;
		case ID_MODS:
			try {
				EagRuntime.setStorage("tcopen", new byte[] { (byte) 1 }); // the mod menu opens over this screen
			} catch (Throwable t) {
			}
			break;
		case ID_NEW:
			if (TCProfiles.addNew() != null) {
				this.scroll = Integer.MAX_VALUE;
				clampScroll();
				this.mc.displayGuiScreen(new GuiScreenEditProfile(this)); // name it and pick a skin right away
			}
			break;
		case ID_DELETE: {
			TCProfiles.Profile p = TCProfiles.get(TCProfiles.activeId());
			if (p != null && TCProfiles.list().size() > 1) {
				this.mc.displayGuiScreen(new GuiYesNo(this, "Delete the profile \"" + p.name + "\"?",
						"Its name, skin, cape and mod settings will be removed.", "Delete", "Cancel", ASK_DELETE));
			}
			break;
		}
		case ID_DONE:
			this.mc.displayGuiScreen(this.parent);
			break;
		}
	}

	public void confirmClicked(boolean result, int id) {
		if (id == ASK_DELETE) {
			if (result) {
				TCProfiles.delete(TCProfiles.activeId());
			}
			this.mc.displayGuiScreen(this);
			return;
		}
		super.confirmClicked(result, id);
	}

	public void onGuiClosed() {
		TCProfiles.releaseFaces();
	}
}
