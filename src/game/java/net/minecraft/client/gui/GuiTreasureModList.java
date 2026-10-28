package net.minecraft.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.lax1dude.eaglercraft.EagRuntime;
import net.peyton.eagler.gui.GuiCredits;

/**
 * Treasure Client: the "Mods" screen on the main menu. Lists the mods built into
 * the client, with their authors and licenses, and lets you turn them on or off.
 *
 * Switches are stored by the overlay (Treasure Settings), so changes here are
 * sent to it through the "tcset" storage key ("celeritas=0,via=1"). The overlay
 * saves them and passes them back to the game, which keeps both menus in sync.
 */
public class GuiTreasureModList extends GuiScreen {

	private static final int ID_DONE = 0;
	private static final int ID_CREDITS = 1;
	private static final int ID_TOGGLE_BASE = 100;

	private static final int PANEL_WIDTH = 320;
	private static final int ENTRY_HEIGHT = 72;

	/** One built-in mod. */
	private abstract static class Entry {
		final String name, by, license, description, key;
		Boolean pending; // value we asked the overlay for, until the game reports it back

		Entry(String name, String by, String license, String description, String key) {
			this.name = name;
			this.by = by;
			this.license = license;
			this.description = description;
			this.key = key;
		}

		abstract boolean setting();

		boolean available() {
			return true;
		}

		/** Built into how the game loads, so it has no switch. */
		boolean alwaysOn() {
			return false;
		}

		abstract String status(boolean on);

		boolean shownOn() {
			return this.pending != null ? this.pending : this.setting();
		}
	}

	private final GuiScreen parent;
	private final List<Entry> entries = new ArrayList<>();
	private final List<GuiButton> toggles = new ArrayList<>();

	public GuiTreasureModList(GuiScreen parent) {
		this.parent = parent;
		this.entries.add(new Entry("Celeritas", "embeddedt, taumc and contributors", "LGPL-3.0",
				"Faster chunk rendering, based on Sodium for Minecraft 1.12. Draws the world with far fewer draw calls.",
				"celeritas") {
			boolean setting() {
				return TreasureMods.celeritasSetting();
			}

			boolean available() {
				return org.taumc.celeritas.impl.TreasureCeleritas.isAvailable();
			}

			String status(boolean on) {
				if (!available()) {
					return "§7Not available here (needs the WASM version and WebGL 2)";
				}
				String failure = org.taumc.celeritas.impl.TreasureCeleritas.getFailure();
				if (on && failure != null) {
					return "§cStopped after an error. Turn it off and on to retry.";
				}
				if (!on) {
					return "§7Off · using the vanilla renderer";
				}
				return org.taumc.celeritas.impl.TreasureCeleritas.isRunning() ? "§aRunning"
						: "§aOn · starts when you join a world";
			}
		});
		this.entries.add(new Entry("Entity Culling", "Treasure Client, inspired by tr7zw", "Built in",
				"Skips drawing mobs, chests, signs and banners that are hidden behind solid blocks.", "entcull") {
			boolean setting() {
				return TreasureMods.entityCulling();
			}

			String status(boolean on) {
				if (!on) {
					return "§7Off";
				}
				return "§aOn §7· " + net.minecraft.client.renderer.TCEntityCulling.culledEntities + " entities, "
						+ net.minecraft.client.renderer.TCEntityCulling.culledTiles + " block entities hidden";
			}
		});
		this.entries.add(new Entry("Enhanced Block Entities", "FoundationGames", "LGPL-3.0",
				"Draws chests, ender chests and beds as part of the world instead of one by one every frame. Chests switch back while their lid moves.",
				"ebe") {
			boolean setting() {
				return TreasureMods.enhancedBlockEntities();
			}

			String status(boolean on) {
				return on ? "§aOn" : "§7Off";
			}
		});
		this.entries.add(new Entry("FoamFix", "Treasure Client, inspired by asie", "Built in",
				"Less memory and a faster start: block states share one compact lookup instead of a table each, and identical model faces are stored once.",
				"foamfix") {
			boolean setting() {
				return true;
			}

			boolean alwaysOn() {
				return true;
			}

			String status(boolean on) {
				return "§aOn §7· built into loading";
			}
		});
		this.entries.add(new Entry("TexFix", "Treasure Client, inspired by Speiger", "Built in",
				"Less memory: after textures load, frees the leftover pixel copies of every texture that never animates.",
				"texfix") {
			boolean setting() {
				return true;
			}

			boolean alwaysOn() {
				return true;
			}

			String status(boolean on) {
				return "§aOn §7· built into loading";
			}
		});
		this.entries.add(new Entry("Lithium", "CaffeineMC", "LGPL-3.0",
				"Faster collisions, chunk lookups and block entity lists, with the same results. Also speeds up singleplayer.",
				"lithium") {
			boolean setting() {
				return TreasureMods.lithium();
			}

			String status(boolean on) {
				return on ? "§aOn" : "§7Off";
			}
		});
		this.entries.add(new Entry("ImmediatelyFast", "RaphiMC", "LGPL-3.0",
				"Cheaper name labels and signs: no rebuilding each name and re-measuring each sign line every frame, and no sign text past 32 blocks.",
				"ifast") {
			boolean setting() {
				return TreasureMods.immediatelyFast();
			}

			String status(boolean on) {
				return on ? "§aOn" : "§7Off";
			}
		});
		this.entries.add(new Entry("Dynamic FPS", "juliand665", "MIT",
				"Drops to 10 FPS while another window is focused, so the game doesn't heat up your device in the background.",
				"dynfps") {
			boolean setting() {
				return TreasureMods.dynamicFps();
			}

			String status(boolean on) {
				return on ? "§aOn" : "§7Off";
			}
		});
		this.entries.add(new Entry("Memory Saver", "Treasure Client", "Built in",
				"Stops the idle singleplayer engine while you're on a server and frees unused sounds after 2 minutes instead of 10.",
				"memsaver") {
			boolean setting() {
				return TreasureMods.memorySaver();
			}

			String status(boolean on) {
				return on ? "§aOn" : "§7Off";
			}
		});
		this.entries.add(new Entry("VanillaFix", "DimensionalDevelopment", "MIT",
				"Recovers from crashes instead of closing the game, and fixes Minecraft bugs (menus in portals, camera in grass, a memory leak).",
				"vanillafix") {
			boolean setting() {
				return TreasureMods.vanillaFix();
			}

			String status(boolean on) {
				return on ? "§aOn" : "§7Off";
			}
		});
		this.entries.add(new Entry("ViaVersion support", "Treasure Client", "Built in",
				"Shows modern 1.13+ blocks, items and entities on servers that send them.", "via") {
			boolean setting() {
				return TreasureMods.viaEnabled();
			}

			String status(boolean on) {
				return on ? "§aOn" : "§7Off";
			}
		});
	}

	private int panelLeft() {
		return (this.width - PANEL_WIDTH) / 2;
	}

	/** Full-size panels if they fit above the buttons, otherwise compact ones (one description line). */
	private boolean compact() {
		return 40 + this.entries.size() * (ENTRY_HEIGHT + 6) > this.height - 34;
	}

	private int entryHeight() {
		return compact() ? ENTRY_HEIGHT - 16 : ENTRY_HEIGHT;
	}

	private int entryTop(int index) {
		return 40 + index * (entryHeight() + 6);
	}

	public void initGui() {
		this.buttonList.clear();
		this.toggles.clear();
		int left = panelLeft();
		for (int i = 0; i < this.entries.size(); i++) {
			GuiButton b = new GuiButton(ID_TOGGLE_BASE + i, left + PANEL_WIDTH - 78, entryTop(i) + 8, 70, 20, "");
			this.toggles.add(b);
			this.buttonList.add(b);
		}
		this.buttonList.add(new GuiButton(ID_CREDITS, this.width / 2 - 154, this.height - 28, 150, 20, "Credits"));
		this.buttonList.add(new GuiButton(ID_DONE, this.width / 2 + 4, this.height - 28, 150, 20, "Done"));
		this.refreshToggles();
	}

	private void refreshToggles() {
		for (int i = 0; i < this.entries.size(); i++) {
			Entry e = this.entries.get(i);
			if (e.pending != null && e.pending == e.setting()) {
				e.pending = null; // the game has picked up the change
			}
			GuiButton b = this.toggles.get(i);
			b.enabled = e.available() && !e.alwaysOn();
			b.displayString = e.alwaysOn() ? "§aAlways on"
					: !e.available() ? "Unavailable" : (e.shownOn() ? "§aEnabled" : "§cDisabled");
		}
	}

	public void updateScreen() {
		this.refreshToggles();
	}

	protected void actionPerformed(GuiButton button) throws IOException {
		if (button.id == ID_DONE) {
			this.mc.displayGuiScreen(this.parent);
		} else if (button.id == ID_CREDITS) {
			this.mc.displayGuiScreen(new GuiCredits(this, "/assets/eagler/credits.txt"));
		} else if (button.id >= ID_TOGGLE_BASE && button.id < ID_TOGGLE_BASE + this.entries.size()) {
			Entry e = this.entries.get(button.id - ID_TOGGLE_BASE);
			e.pending = !e.shownOn();
			try {
				EagRuntime.setStorage("tcset", (e.key + "=" + (e.pending ? 1 : 0))
						.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			} catch (Throwable t) {
			}
			this.refreshToggles();
		}
	}

	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();
		this.drawCenteredString(this.fontRendererObj, "Mods", this.width / 2, 16, 0xFFFFFF);
		this.drawCenteredString(this.fontRendererObj, "§7Built into Treasure Client", this.width / 2, 27, 0xFFFFFF);
		int left = panelLeft();
		for (int i = 0; i < this.entries.size(); i++) {
			Entry e = this.entries.get(i);
			int top = entryTop(i);
			int h = entryHeight();
			drawRect(left, top, left + PANEL_WIDTH, top + h, 0xA0000000);
			drawRect(left, top, left + 2, top + h, (e.alwaysOn() || e.shownOn()) && e.available() ? 0xFFF5C542 : 0xFF555555);
			int x = left + 8;
			this.fontRendererObj.drawStringWithShadow("§l" + e.name, x, top + 8, 0xFFE08A);
			this.fontRendererObj.drawStringWithShadow("§7by " + e.by + " · " + e.license, x, top + 19, 0xFFFFFF);
			List<String> lines = this.fontRendererObj.listFormattedStringToWidth(e.description, PANEL_WIDTH - 16);
			int y = top + 33;
			for (int l = 0; l < lines.size() && l < (compact() ? 1 : 2); l++) {
				this.fontRendererObj.drawStringWithShadow(lines.get(l), x, y, 0xDDDDDD);
				y += 10;
			}
			this.fontRendererObj.drawStringWithShadow(e.status(e.shownOn()), x, top + h - 12, 0xFFFFFF);
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
	}
}
