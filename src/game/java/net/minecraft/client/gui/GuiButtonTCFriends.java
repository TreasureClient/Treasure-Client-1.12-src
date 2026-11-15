package net.minecraft.client.gui;

import java.nio.charset.StandardCharsets;

import net.lax1dude.eaglercraft.EagRuntime;
import net.minecraft.client.Minecraft;

/**
 * Treasure Client: the Friends button in the top left of the title screen. A chat
 * bubble with a red badge for unread messages and friend requests. It opens the mod
 * menu on its Friends tab (the overlay owns Friends & Messages).
 */
public class GuiButtonTCFriends extends GuiButton {

	private static long lastRead = 0L;
	private static int unread = 0;

	public GuiButtonTCFriends(int id, int x, int y) {
		super(id, x, y, 22, 22, "");
	}

	/** Asks the mod menu to open on the Friends tab. */
	public static void open() {
		try {
			EagRuntime.setStorage("tcopen", new byte[] { (byte) 2 });
		} catch (Throwable t) {
		}
	}

	/** Unread count written by the overlay (storage "tcnetunread", a number as text). */
	private static int unread() {
		long now = System.currentTimeMillis();
		if (now - lastRead > 500L) {
			lastRead = now;
			try {
				byte[] b = EagRuntime.getStorage("tcnetunread");
				unread = b == null ? 0 : Integer.parseInt(new String(b, StandardCharsets.UTF_8).trim());
			} catch (Throwable t) {
				unread = 0;
			}
		}
		return unread;
	}

	public void func_191745_a(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
		if (!this.visible) {
			return;
		}
		this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width
				&& mouseY < this.yPosition + this.height;
		int acc = TreasureButtons.accent();
		int x = this.xPosition, y = this.yPosition;
		TreasureButtons.roundedRect(x, y, this.width, this.height,
				this.hovered ? TreasureButtons.lerpColor(TreasureButtons.panelBg(), acc, 0.25F) : TreasureButtons.panelBg());
		TreasureButtons.roundedOutline(x, y, this.width, this.height, this.hovered ? 0xFFFFFFFF : acc);

		// chat bubble: 14x9 body with a small tail, three dots inside
		int ic = this.hovered ? 0xFFFFFFFF : acc;
		int bx = x + 4, by = y + 5;
		drawRect(bx + 1, by, bx + 13, by + 9, ic);
		drawRect(bx, by + 1, bx + 14, by + 8, ic);
		drawRect(bx + 3, by + 9, bx + 6, by + 10, ic);
		drawRect(bx + 3, by + 10, bx + 4, by + 12, ic);
		int dot = 0xFF1A1206;
		drawRect(bx + 3, by + 4, bx + 5, by + 6, dot);
		drawRect(bx + 6, by + 4, bx + 8, by + 6, dot);
		drawRect(bx + 9, by + 4, bx + 11, by + 6, dot);

		mc.fontRendererObj.drawStringWithShadow("Friends", (float) (x + this.width + 6), (float) (y + 7),
				this.hovered ? 0xFFFFFFFF : TreasureButtons.textIdle());

		int n = unread();
		if (n > 0) {
			String s = n > 99 ? "99+" : String.valueOf(n);
			int sw = mc.fontRendererObj.getStringWidth(s);
			int rw = Math.max(9, sw + 4);
			int rx = x + this.width - rw + 3, ry = y - 3;
			TreasureButtons.roundedRect(rx, ry, rw, 9, 0xFFE0383E);
			mc.fontRendererObj.drawString(s, rx + (rw - sw) / 2 + 1, ry + 1, 0xFFFFFFFF);
		}
	}
}
