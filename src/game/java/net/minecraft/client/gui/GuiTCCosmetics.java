package net.minecraft.client.gui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.profile.GuiScreenEditProfile;
import net.minecraft.client.tccos.TCCosmetics;
import net.minecraft.util.math.MathHelper;

/**
 * Treasure Client 1.5: the Cosmetics screen (capes and auras; hats, boots and the Maker are
 * coming). Like the main menu, the page overlay draws the words and the cards, and the game
 * draws the background and your player wearing what you pick.
 *
 * Talk with the overlay (game storage, like the main menu):
 *   game -> overlay: tccosui = 1 while this screen shows; tccosinfo = "name\ncape\naura\ncolor"
 *   overlay -> game: tccosact = lines of "cape:id" | "aura:id" | "color:id" | "back" | "skin"
 *                    tccosack = the time, every 400 ms (the game shows plain buttons if it stops)
 */
public class GuiTCCosmetics extends GuiScreen {

	private final GuiScreen parent;
	private long initAt = 0L;
	private boolean overlayLive = false;

	// the player's turn: spins slowly by itself, turns round to show a new cape, can be dragged
	private static float yaw = -25.0F;
	private float yawTarget = 0.0F;
	private long showUntil = 0L, idleUntil = 0L, lastFrame = 0L;
	private boolean dragging = false;
	private int dragLastX;

	public GuiTCCosmetics(GuiScreen parent) {
		this.parent = parent;
	}

	public void initGui() {
		this.buttonList.clear();
		int bw = 200, bx = this.width / 2 - bw / 2 - this.width / 6, y = this.height / 4 + 10;
		this.buttonList.add(new GuiButton(1, bx, y, bw, 20, ""));
		this.buttonList.add(new GuiButton(2, bx, y + 24, bw, 20, ""));
		this.buttonList.add(new GuiButton(3, bx, y + 48, bw, 20, ""));
		this.buttonList.add(new GuiButton(4, bx, y + 84, bw, 20, "Skin & Eaglercraft cape..."));
		this.buttonList.add(new GuiButton(5, bx, y + 108, bw, 20, "Done"));
		this.labels();
		if (this.initAt == 0L) this.initAt = EagRuntime.steadyTimeMillis();
		this.lastFrame = EagRuntime.steadyTimeMillis();
		try {
			EagRuntime.setStorage("tccosui", new byte[] { (byte) 1 });
		} catch (Throwable t) {
		}
		this.publish();
		this.updateButtons();
	}

	private void labels() {
		TCCosmetics.Look m = TCCosmetics.mine();
		this.buttonList.get(0).displayString = "Cape: " + name(m.cape);
		this.buttonList.get(1).displayString = "Aura: " + name(m.aura);
		this.buttonList.get(2).displayString = "Aura colour: " + name(m.color);
		this.buttonList.get(2).enabled = m.hasAura();
	}

	private static String name(String id) {
		if ("none".equals(id)) return "None";
		if ("theme".equals(id)) return "Your theme";
		return Character.toUpperCase(id.charAt(0)) + id.substring(1);
	}

	/** Tells the overlay what you're wearing. */
	private void publish() {
		try {
			TCCosmetics.Look m = TCCosmetics.mine();
			String info = this.mc.getSession().getProfile().getName() + "\n" + m.cape + "\n" + m.aura + "\n" + m.color;
			EagRuntime.setStorage("tccosinfo", info.getBytes(StandardCharsets.UTF_8));
		} catch (Throwable t) {
		}
	}

	private boolean fallback() {
		return !this.overlayLive && EagRuntime.steadyTimeMillis() - this.initAt > 1500L;
	}

	private void updateButtons() {
		boolean fb = this.fallback();
		for (int i = 0; i < this.buttonList.size(); ++i) {
			this.buttonList.get(i).visible = fb;
		}
	}

	public void updateScreen() {
		try {
			byte[] a = EagRuntime.getStorage("tccosact");
			if (a != null) {
				EagRuntime.setStorage("tccosact", null);
				for (String line : new String(a, StandardCharsets.UTF_8).split("\n")) {
					if (this.mc.currentScreen != this) break;
					this.act(line.trim());
				}
			}
			byte[] k = EagRuntime.getStorage("tccosack");
			long ack = 0L;
			if (k != null && k.length > 0 && k.length < 20) {
				try {
					ack = Long.parseLong(new String(k, StandardCharsets.US_ASCII).trim());
				} catch (NumberFormatException e) {
					ack = 0L;
				}
			}
			this.overlayLive = Math.abs(System.currentTimeMillis() - ack) < 2500L;
		} catch (Throwable t) {
			this.overlayLive = false;
		}
		this.updateButtons();
	}

	private void act(String cmd) {
		if (cmd.isEmpty()) return;
		if ("back".equals(cmd)) {
			this.mc.displayGuiScreen(this.parent);
			return;
		}
		if ("skin".equals(cmd)) {
			this.mc.displayGuiScreen(new GuiScreenEditProfile(this));
			return;
		}
		int c = cmd.indexOf(':');
		if (c <= 0) return;
		String what = cmd.substring(0, c), id = cmd.substring(c + 1);
		if (TCCosmetics.set(what, id)) {
			this.picked(what, id);
		}
	}

	/** After a change: turn the player so you see it, and tell the overlay. */
	private void picked(String what, String id) {
		if ("cape".equals(what) && !"none".equals(id)) {
			this.yawTarget = 160.0F;
			this.showUntil = EagRuntime.steadyTimeMillis() + 3200L;
		} else if ("aura".equals(what) && !"none".equals(id)) {
			this.yawTarget = -25.0F;
			this.showUntil = EagRuntime.steadyTimeMillis() + 2500L;
		}
		this.labels();
		this.publish();
	}

	protected void actionPerformed(GuiButton b) throws IOException {
		TCCosmetics.Look m = TCCosmetics.mine();
		switch (b.id) {
		case 1: this.cycle("cape", TCCosmetics.CAPES, m.cape); break;
		case 2: this.cycle("aura", TCCosmetics.AURAS, m.aura); break;
		case 3: this.cycle("color", TCCosmetics.COLORS, m.color); break;
		case 4: this.mc.displayGuiScreen(new GuiScreenEditProfile(this)); break;
		case 5: this.mc.displayGuiScreen(this.parent); break;
		default: break;
		}
	}

	private void cycle(String what, String[] ids, String cur) {
		int i = TCCosmetics.indexOf(ids, cur);
		String next = ids[(i + 1) % ids.length];
		if (TCCosmetics.set(what, next)) this.picked(what, next);
	}

	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (keyCode == 1) {
			this.mc.displayGuiScreen(this.parent);
			return;
		}
		super.keyTyped(typedChar, keyCode);
	}

	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		TreasureBackground.draw(this.width, this.height, this.zLevel);

		long now = EagRuntime.steadyTimeMillis();
		float dt = Math.min(0.05F, (now - this.lastFrame) / 1000.0F); // a slow frame never makes it jump
		this.lastFrame = now;
		if (!this.dragging) {
			if (now < this.showUntil) {
				float diff = MathHelper.wrapDegrees(this.yawTarget - yaw);
				yaw += diff * Math.min(1.0F, dt * 6.0F);
			} else if (now > this.idleUntil) {
				yaw = MathHelper.wrapDegrees(yaw + dt * 16.0F);
			}
		}

		boolean fb = this.fallback();
		int cx = (int) (this.width * (fb ? 0.74F : 0.78F));
		float scale = Math.max(24.0F, this.height * 0.2F);
		int feetY = (int) (this.height * 0.24F + scale * 2.0F);
		int sw = (int) (scale * 0.55F);
		for (int k = 0; k < 3; ++k) {
			int ins = k * sw / 4;
			Gui.drawRect(cx - sw + ins, feetY - 1 + k, cx + sw - ins, feetY + 1 + k, 0x22000000);
		}
		TCPlayerPreview.draw(cx, feetY, scale, yaw, mouseX, mouseY, TCCosmetics.mine());

		if (fb) {
			this.drawCenteredString(this.fontRendererObj, "Cosmetics", this.width / 2 - this.width / 6, this.height / 4 - 14,
					TreasureButtons.accent());
			this.drawCenteredString(this.fontRendererObj, "Press F5 in game to see yourself", this.width / 2 - this.width / 6,
					this.height / 4 + 140, 0xFFA0A0A0);
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		super.mouseClicked(mouseX, mouseY, mouseButton);
		int cx = (int) (this.width * (this.fallback() ? 0.74F : 0.78F));
		if (mouseButton == 0 && Math.abs(mouseX - cx) < this.width * 0.13F && mouseY > this.height * 0.18F
				&& mouseY < this.height * 0.7F) {
			this.dragging = true;
			this.dragLastX = mouseX;
		}
	}

	protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
		super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
		if (this.dragging) {
			yaw = MathHelper.wrapDegrees(yaw + (mouseX - this.dragLastX) * 1.6F);
			this.dragLastX = mouseX;
			this.showUntil = 0L;
			this.idleUntil = EagRuntime.steadyTimeMillis() + 4000L;
		}
	}

	protected void mouseReleased(int mouseX, int mouseY, int state) {
		super.mouseReleased(mouseX, mouseY, state);
		this.dragging = false;
	}

	public boolean doesGuiPauseGame() {
		return false;
	}

	public void onGuiClosed() {
		try {
			EagRuntime.setStorage("tccosui", null);
		} catch (Throwable t) {
		}
	}
}
