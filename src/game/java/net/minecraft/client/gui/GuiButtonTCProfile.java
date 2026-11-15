package net.minecraft.client.gui;

import net.minecraft.client.Minecraft;

/**
 * Treasure Client: the Profiles button in the top right of the title screen. It shows
 * the face of the profile in use, with its name beside it, and opens the Profiles
 * screen.
 */
public class GuiButtonTCProfile extends GuiButton {

	public GuiButtonTCProfile(int id, int x, int y) {
		super(id, x, y, 22, 22, "");
	}

	public void func_191745_a(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
		if (!this.visible) {
			return;
		}
		this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width
				&& mouseY < this.yPosition + this.height;
		int acc = TreasureButtons.accent();
		TreasureButtons.roundedRect(this.xPosition, this.yPosition, this.width, this.height,
				this.hovered ? TreasureButtons.lerpColor(TreasureButtons.panelBg(), acc, 0.25F) : TreasureButtons.panelBg());
		TreasureButtons.roundedOutline(this.xPosition, this.yPosition, this.width, this.height,
				this.hovered ? 0xFFFFFFFF : acc);
		TCProfiles.drawFace(null, this.xPosition + 3, this.yPosition + 3, 16);

		String name = net.lax1dude.eaglercraft.profile.EaglerProfile.getName();
		if (name != null) {
			int w = mc.fontRendererObj.getStringWidth(name);
			mc.fontRendererObj.drawStringWithShadow(name, (float) (this.xPosition - 6 - w),
					(float) (this.yPosition + 7), this.hovered ? 0xFFFFFFFF : TreasureButtons.textIdle());
		}
		if (this.hovered) {
			String tip = "Profiles";
			int tw = mc.fontRendererObj.getStringWidth(tip);
			int tx = this.xPosition + this.width - tw - 4, ty = this.yPosition + this.height + 4;
			drawRect(tx - 3, ty - 2, tx + tw + 3, ty + 10, 0xE0000000);
			mc.fontRendererObj.drawStringWithShadow(tip, (float) tx, (float) ty, acc);
		}
	}
}
