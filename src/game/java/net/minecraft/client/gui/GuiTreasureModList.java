package net.minecraft.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.Keyboard;
import net.lax1dude.eaglercraft.Mouse;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.peyton.eagler.gui.GuiCredits;

/**
 * Treasure Client: the "Mods" screen on the main menu, laid out like Fabric's Mod
 * Menu: a searchable list on the left, details for the picked mod on the right.
 * The treasure touches: gem icons, a gold shimmer header and drifting gold dust.
 *
 * Switches are stored by the overlay (Treasure Settings), so changes here are
 * sent to it through the "tcset" storage key ("celeritas=0,via=1"). The overlay
 * saves them and passes them back to the game, which keeps both menus in sync.
 */
public class GuiTreasureModList extends GuiScreen {

	private static final int ID_DONE = 0;
	private static final int ID_CREDITS = 1;
	private static final int ID_TOGGLE = 2;

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

		/** Button text for entries with no switch. */
		String fixedLabel() {
			return "§aAlways on";
		}

		abstract String status(boolean on);

		boolean shownOn() {
			return this.pending != null ? this.pending : this.setting();
		}
	}

	private final GuiScreen parent;
	private final List<Entry> entries = new ArrayList<>();

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
		this.entries.add(new Entry("Krypton", "Treasure Client, inspired by astei", "Built in",
				"Faster packet reading: numbers in packets are read straight from the packet's bytes, and text is decoded where it is instead of being copied out first. Same results, less work for every packet.",
				"krypton") {
			boolean setting() {
				return true;
			}

			boolean alwaysOn() {
				return true;
			}

			String status(boolean on) {
				return "§aOn";
			}
		});
		this.entries.add(new Entry("C2ME", "Treasure Client, inspired by ishland", "Built in",
				"Smoother chunk loading: when a server sends a big burst of chunks and entities (like joining a game), about 8 ms of it is handled per tick instead of all at once, so the world loads in over a few ticks without a freeze. Singleplayer world generation also uses faster noise math (same worlds).",
				"c2me") {
			boolean setting() {
				return TreasureMods.c2me();
			}

			String status(boolean on) {
				return on ? "§aOn" : "§7Off §8(world generation math stays on)";
			}
		});
		this.entries.add(new Entry("Particle Core", "Treasure Client, inspired by fzzyhmstrs", "Built in",
				"Cheaper particles: far away particles move without checking for blocks, still particles skip the check, and at most 2048 of each kind are kept. Also fixes particles never landing on the ground.",
				"pcore") {
			boolean setting() {
				return TreasureMods.particleCore();
			}

			String status(boolean on) {
				return on ? "§aOn" : "§7Off";
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
		this.entries.add(new Entry("FerriteCore", "Treasure Client, inspired by malte0811", "Built in",
				"Block states stop keeping their own property map: values are read from their place in a shared grid, which is faster than a map lookup and saves one map per block state.",
				"ferritecore") {
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
		this.entries.add(new Entry("No Chat Lag", "Treasure Client, inspired by noahvdaa", "Built in",
				"No stutter from busy chat: each chat line's colored text is worked out once instead of every frame, and logging new messages no longer runs slow text patterns.",
				"nochatlag") {
			boolean setting() {
				return true;
			}

			boolean alwaysOn() {
				return true;
			}

			String status(boolean on) {
				return "§aOn";
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
		this.entries.add(new Entry("CensoredASM", "Treasure Client, inspired by Rongmario", "Built in",
				"Less memory and faster lookups: one shared copy of each repeated name (blocks, items, textures, NBT tags), better block position hashing, and no forced memory cleanup when joining a world.",
				"censoredasm") {
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
		this.entries.add(new Entry("BetterFps", "Treasure Client, inspired by Guichaguri", "Built in",
				"Faster sine and cosine math from a small table (the same numbers as OptiFine's Fast Math, so anticheats accept it), and hoppers with a solid block on top stop searching for dropped items.",
				"bfmath") {
			boolean setting() {
				return TreasureMods.betterFpsMath();
			}

			String status(boolean on) {
				return on ? "§aOn" : "§7Off §8(hoppers stay fast)";
			}
		});
		this.entries.add(new Entry("Dynamic Resolution", "Treasure Client", "Built in",
				"When frames get slow from drawing, like when your device heats up, the world is drawn at a slightly lower resolution so the graphics chip has less to do. It goes back up once things are smooth again. Off by default. It never goes below 70% or below your own Render Resolution setting, and it ignores lag that comes from the server.",
				"dynResOn") {
			boolean setting() {
				return TreasureMods.dynamicResolution();
			}

			String status(boolean on) {
				return on ? "§aOn §7· adjusts by itself" : "§7Off";
			}
		});
		this.entries.add(new Entry("AI Improvements", "Treasure Client, inspired by QueenOfMissiles", "Built in",
				"Mobs reuse their last head-turn angles while what they look at hasn't moved, skipping costly math. Mobs only run in singleplayer, so this speeds up singleplayer worlds.",
				"aiimprove") {
			boolean setting() {
				return true;
			}

			boolean alwaysOn() {
				return true;
			}

			String status(boolean on) {
				return "§aOn §7· singleplayer";
			}
		});
		this.entries.add(new Entry("Celeritas Extra", "Sumire-Labs", "LGPL-3.0",
				"Extra switches for animations, particles, sky, weather, item frames, armor stands, paintings, beacon beams, enchanting books, pistons and toasts. Set them with the gear in Treasure Settings.",
				"cextra") {
			boolean setting() {
				return net.minecraft.client.renderer.TCExtra.enabled();
			}

			String status(boolean on) {
				return on ? "§aOn" : "§7Off";
			}
		});
		this.entries.add(new Entry("Celeritas Leaf Culling", "Karnatour (fork of Sodium Leaf Culling)", "MIT",
				"With fancy leaves, skips leaf faces hidden inside trees. Pick Hollow, Solid Aggressive or Solid in Treasure Settings.",
				"leafCull") {
			boolean setting() {
				return TreasureMods.leafCulling() != net.minecraft.block.TCLeafCulling.OFF;
			}

			boolean alwaysOn() {
				return true;
			}

			String fixedLabel() {
				return "Set in Settings";
			}

			String status(boolean on) {
				switch (TreasureMods.leafCulling()) {
				case net.minecraft.block.TCLeafCulling.HOLLOW: return "§aHollow";
				case net.minecraft.block.TCLeafCulling.SOLID_AGGRESSIVE: return "§aSolid Aggressive";
				case net.minecraft.block.TCLeafCulling.SOLID: return "§aSolid";
				default: return "§7Off";
				}
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
				"Fewer draw calls for the HUD: hearts, hunger, armor and hotbar pieces that share a texture are drawn together in one go, and the HUD is drawn straight to the screen instead of through an extra full-screen image. Also cheaper name labels and signs, and no sign text past 32 blocks.",
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

	// ---------------- layout ----------------

	private static final int HEADER_H = 34;
	private static final int ROW_H = 32;
	/** Gem colors for the mod icons: ruby, emerald, sapphire, amethyst, topaz, aquamarine. */
	private static final int[] GEMS = { 0xFFD8204F, 0xFF1FB86A, 0xFF2F6FE0, 0xFF9B4DDB, 0xFFF0A020, 0xFF22B8C9 };
	private static final int GOLD = 0xFFF5C542;

	private GuiTextField search;
	private GuiButton toggle;
	private final List<Entry> shown = new ArrayList<>();
	private Entry selected;
	private float scroll, scrollTarget;
	private boolean dragging, dragMoved;
	private int dragStartY, pressX, pressY;
	private float dragStartScroll;
	private long openedAt;

	private int listLeft() {
		return 8;
	}

	private int listRight() {
		return listLeft() + Math.max(130, Math.min(210, (this.width - 24) * 2 / 5));
	}

	private int searchTop() {
		return HEADER_H + 6;
	}

	private int listTop() {
		return searchTop() + 20;
	}

	private int listBottom() {
		return this.height - 34;
	}

	private int detailLeft() {
		return listRight() + 8;
	}

	private int detailRight() {
		return this.width - 8;
	}

	private int maxScroll() {
		return Math.max(0, this.shown.size() * ROW_H - (listBottom() - listTop()));
	}

	private float clampScroll(float s) {
		return Math.max(0, Math.min(maxScroll(), s));
	}

	private static String categoryOf(Entry e) {
		if (e.key.equals("vanillafix")) return "Bug fixes";
		if (e.key.equals("via")) return "Compatibility";
		return "Performance";
	}

	public void initGui() {
		Keyboard.enableRepeatEvents(true);
		String q = this.search != null ? this.search.getText() : "";
		this.buttonList.clear();
		this.search = new GuiTextField(2, this.fontRendererObj, listLeft() + 1, searchTop() + 1,
				listRight() - listLeft() - 2, 14);
		this.search.setMaxStringLength(40);
		this.search.setText(q);
		int dw = detailRight() - detailLeft();
		this.toggle = new GuiButton(ID_TOGGLE, detailLeft() + 10, listBottom() - 28, Math.min(150, dw - 20), 20, "");
		this.buttonList.add(this.toggle);
		this.buttonList.add(new GuiButton(ID_CREDITS, this.width / 2 - 154, this.height - 26, 150, 20, "Credits"));
		this.buttonList.add(new GuiButton(ID_DONE, this.width / 2 + 4, this.height - 26, 150, 20, "Done"));
		if (this.openedAt == 0L) {
			this.openedAt = Minecraft.getSystemTime();
		}
		this.refilter();
		this.refreshToggles();
	}

	private void refilter() {
		String q = this.search == null ? "" : this.search.getText().trim().toLowerCase();
		this.shown.clear();
		for (int i = 0; i < this.entries.size(); i++) {
			Entry e = this.entries.get(i);
			if (q.isEmpty() || e.name.toLowerCase().contains(q) || e.by.toLowerCase().contains(q)
					|| e.description.toLowerCase().contains(q) || categoryOf(e).toLowerCase().contains(q)) {
				this.shown.add(e);
			}
		}
		if (this.selected == null || !this.shown.contains(this.selected)) {
			this.selected = this.shown.isEmpty() ? null : this.shown.get(0);
		}
		this.scrollTarget = clampScroll(this.scrollTarget);
		this.scroll = clampScroll(this.scroll);
	}

	private void refreshToggles() {
		for (int i = 0; i < this.entries.size(); i++) {
			Entry e = this.entries.get(i);
			if (e.pending != null && e.pending == e.setting()) {
				e.pending = null; // the game has picked up the change
			}
		}
		Entry e = this.selected;
		this.toggle.visible = e != null;
		if (e != null) {
			this.toggle.enabled = e.available() && !e.alwaysOn();
			this.toggle.displayString = e.alwaysOn() ? e.fixedLabel()
					: !e.available() ? "Unavailable" : (e.shownOn() ? "§aEnabled" : "§cDisabled");
		}
	}

	public void updateScreen() {
		this.search.updateCursorCounter();
		this.refreshToggles();
	}

	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
	}

	protected void actionPerformed(GuiButton button) throws IOException {
		if (button.id == ID_DONE) {
			this.mc.displayGuiScreen(this.parent);
		} else if (button.id == ID_CREDITS) {
			this.mc.displayGuiScreen(new GuiCredits(this, "/assets/eagler/credits.txt"));
		} else if (button.id == ID_TOGGLE && this.selected != null) {
			Entry e = this.selected;
			e.pending = !e.shownOn();
			try {
				EagRuntime.setStorage("tcset", (e.key + "=" + (e.pending ? 1 : 0))
						.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			} catch (Throwable t) {
			}
			this.refreshToggles();
		}
	}

	private void select(Entry e) {
		if (e != null && e != this.selected) {
			this.selected = e;
			this.mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord
					.getMasterRecord(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.4F));
			this.refreshToggles();
		}
	}

	private void ensureVisible(int index) {
		int top = index * ROW_H;
		int view = listBottom() - listTop();
		if (top < this.scrollTarget) this.scrollTarget = top;
		if (top + ROW_H > this.scrollTarget + view) this.scrollTarget = top + ROW_H - view;
		this.scrollTarget = clampScroll(this.scrollTarget);
	}

	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (keyCode == 1) {
			this.mc.displayGuiScreen(this.parent);
			return;
		}
		if ((keyCode == 200 || keyCode == 208) && !this.shown.isEmpty()) { // up / down arrows
			int i = Math.max(0, this.shown.indexOf(this.selected)) + (keyCode == 200 ? -1 : 1);
			i = Math.max(0, Math.min(this.shown.size() - 1, i));
			this.select(this.shown.get(i));
			this.ensureVisible(i);
			return;
		}
		if (this.search.textboxKeyTyped(typedChar, keyCode)) {
			this.refilter();
			this.refreshToggles();
		}
	}

	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int d = Mouse.getEventDWheel();
		if (d != 0) {
			this.scrollTarget = clampScroll(this.scrollTarget - Integer.signum(d) * ROW_H * 1.5f);
		}
	}

	private boolean inList(int x, int y) {
		return x >= listLeft() && x < listRight() && y >= listTop() && y < listBottom();
	}

	private int rowAt(int y) {
		int i = (int) Math.floor((y - listTop() + this.scroll) / ROW_H);
		return i >= 0 && i < this.shown.size() ? i : -1;
	}

	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		super.mouseClicked(mouseX, mouseY, mouseButton);
		this.search.mouseClicked(mouseX, mouseY, mouseButton);
		if (mouseButton == 0 && inList(mouseX, mouseY)) {
			this.dragging = true;
			this.dragMoved = false;
			this.dragStartY = mouseY;
			this.pressX = mouseX;
			this.pressY = mouseY;
			this.dragStartScroll = this.scrollTarget;
		}
	}

	protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
		super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
		if (this.dragging) {
			int dy = mouseY - this.dragStartY;
			if (Math.abs(dy) > 3) this.dragMoved = true;
			if (this.dragMoved) {
				this.scrollTarget = this.scroll = clampScroll(this.dragStartScroll - dy);
			}
		}
	}

	protected void mouseReleased(int mouseX, int mouseY, int state) {
		super.mouseReleased(mouseX, mouseY, state);
		if (this.dragging && !this.dragMoved) {
			int i = rowAt(this.pressY);
			if (i >= 0) this.select(this.shown.get(i));
		}
		this.dragging = false;
	}

	// ---------------- drawing ----------------

	/** drawRect clipped to the list's top and bottom edges. */
	private void clipRect(int x1, int y1, int x2, int y2, int color) {
		int top = listTop(), bottom = listBottom();
		if (y1 < top) y1 = top;
		if (y2 > bottom) y2 = bottom;
		if (y2 > y1) drawRect(x1, y1, x2, y2, color);
	}

	private boolean textFits(int y) {
		return y >= listTop() && y + 9 <= listBottom();
	}

	private static int gemColor(Entry e) {
		return GEMS[(e.name.hashCode() & 0x7FFFFFFF) % GEMS.length];
	}

	private static int shade(int argb, float f) {
		int r = Math.min(255, (int) (((argb >> 16) & 255) * f));
		int g = Math.min(255, (int) (((argb >> 8) & 255) * f));
		int b = Math.min(255, (int) ((argb & 255) * f));
		return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	/** A cut-gem icon: gold rim, two-tone facets, a shine, and the mod's first letter. */
	private void drawGem(int x, int y, int size, Entry e, boolean clip) {
		int c = gemColor(e);
		int half = size / 2;
		if (clip) {
			clipRect(x, y, x + size, y + size, GOLD);
			clipRect(x + 1, y + 1, x + size - 1, y + half, shade(c, 1.25f));
			clipRect(x + 1, y + half, x + size - 1, y + size - 1, shade(c, 0.8f));
			clipRect(x + 3, y + 3, x + 3 + size / 4, y + 4, 0x90FFFFFF);
		} else {
			drawRect(x, y, x + size, y + size, GOLD);
			drawRect(x + 1, y + 1, x + size - 1, y + half, shade(c, 1.25f));
			drawRect(x + 1, y + half, x + size - 1, y + size - 1, shade(c, 0.8f));
			drawRect(x + 3, y + 3, x + 3 + size / 4, y + 4, 0x90FFFFFF);
		}
		String letter = e.name.substring(0, 1);
		int ly = y + (size - 8) / 2;
		if (!clip || (ly >= listTop() && ly + 8 <= listBottom())) {
			if (size >= 32) {
				GlStateManager.pushMatrix();
				GlStateManager.translate(x + size / 2f, y + size / 2f, 0);
				GlStateManager.scale(2.0F, 2.0F, 1.0F);
				this.fontRendererObj.drawStringWithShadow("§l" + letter,
						-this.fontRendererObj.getStringWidth("§l" + letter) / 2f, -4, 0xFFFFFF);
				GlStateManager.popMatrix();
			} else {
				this.fontRendererObj.drawStringWithShadow("§l" + letter,
						x + (size - this.fontRendererObj.getStringWidth("§l" + letter)) / 2f + 0.5f, ly, 0xFFFFFF);
			}
		}
	}

	/** A small rounded-looking pill with colored border, used for badges. Returns its width. */
	private int drawPill(int x, int y, String text, int color) {
		int w = this.fontRendererObj.getStringWidth(text) + 8;
		drawRect(x + 1, y, x + w - 1, y + 11, (color & 0x00FFFFFF) | 0x38000000);
		drawRect(x + 1, y, x + w - 1, y + 1, color);
		drawRect(x + 1, y + 10, x + w - 1, y + 11, color);
		drawRect(x, y + 1, x + 1, y + 10, color);
		drawRect(x + w - 1, y + 1, x + w, y + 10, color);
		this.fontRendererObj.drawStringWithShadow(text, x + 4, y + 2, color);
		return w;
	}

	/** Gold dust drifting up behind everything. */
	private void drawSparkles(long t) {
		for (int k = 0; k < 28; k++) {
			int seed = k * 7919 + 104729;
			int x = (int) (((seed * 31L) & 0x7FFFFFFF) % Math.max(1, this.width));
			int speed = 18 + (seed % 23);
			int y = this.height - (int) ((t / speed + k * 53L) % (this.height + 20)) + 10;
			float tw = (float) Math.sin(t / 380.0 + k) * 0.5f + 0.5f;
			int a = (int) (40 + tw * 120);
			int s = (k % 3 == 0) ? 2 : 1;
			drawRect(x, y, x + s, y + s, (a << 24) | 0xF5C542);
		}
	}

	private void drawHeader(long t) {
		drawGradientRect(0, 0, this.width, HEADER_H, 0xD0261A04, 0x90100A00);
		// a soft shimmer sweeping across the header
		int band = 90;
		int sx = (int) ((t / 7) % (this.width + band * 2)) - band;
		for (int i = 0; i < band; i += 6) {
			float f = 1.0f - Math.abs(i - band / 2f) / (band / 2f);
			drawRect(sx + i, 0, sx + i + 6, HEADER_H - 1, ((int) (f * 34) << 24) | 0xFFE9A0);
		}
		drawGradientRect(0, HEADER_H - 1, this.width, HEADER_H, GOLD, 0xFFB8860B);

		GlStateManager.pushMatrix();
		GlStateManager.translate(12, 9, 0);
		GlStateManager.scale(2.0F, 2.0F, 1.0F);
		this.fontRendererObj.drawStringWithShadow("§lMods", 0, 0, 0xFFD54F);
		GlStateManager.popMatrix();
		int tx = 12 + this.fontRendererObj.getStringWidth("§lMods") * 2 + 10;
		this.fontRendererObj.drawStringWithShadow("§6Treasure Client", tx, 10, 0xFFFFFF);
		int on = 0;
		for (int i = 0; i < this.entries.size(); i++) {
			Entry e = this.entries.get(i);
			if (e.available() && (e.alwaysOn() ? e.setting() : e.shownOn())) on++;
		}
		this.fontRendererObj.drawStringWithShadow("§8" + this.entries.size() + " built in · " + on + " active", tx,
				20, 0xFFFFFF);
	}

	private void drawList(int mouseX, int mouseY, long t) {
		int l = listLeft(), r = listRight(), top = listTop(), bottom = listBottom();
		drawRect(l, top, r, bottom, 0x88000000);
		int hover = inList(mouseX, mouseY) && !this.dragMoved ? rowAt(mouseY) : -1;
		for (int i = 0; i < this.shown.size(); i++) {
			int y = top + i * ROW_H - Math.round(this.scroll);
			if (y + ROW_H <= top || y >= bottom) continue;
			Entry e = this.shown.get(i);
			// rows slide in from the left when the screen opens
			int slide = (int) Math.max(0, 24 - (t - i * 35) / 8);
			int x = l + slide;
			boolean sel = e == this.selected;
			if (sel) {
				clipRect(l, y, r, y + ROW_H, 0x50F5C542);
				clipRect(l, y, l + 2, y + ROW_H, GOLD);
			} else if (i == hover) {
				clipRect(l, y, r, y + ROW_H, 0x28FFFFFF);
			}
			clipRect(l + 4, y + ROW_H - 1, r - 4, y + ROW_H, 0x18FFFFFF);
			drawGem(x + 6, y + 5, 22, e, true);
			int tx = x + 34;
			int maxW = r - tx - 12;
			if (textFits(y + 6)) {
				this.fontRendererObj.drawStringWithShadow(
						this.fontRendererObj.trimStringToWidth(e.name, maxW), tx, y + 6, sel ? 0xFFE08A : 0xFFFFFF);
			}
			if (textFits(y + 17)) {
				this.fontRendererObj.drawStringWithShadow(
						this.fontRendererObj.trimStringToWidth("§7" + e.by, maxW), tx, y + 17, 0xFFFFFF);
			}
			boolean active = e.available() && (e.alwaysOn() ? e.setting() : e.shownOn());
			int dot = !e.available() ? 0xFFAA3333 : active ? (e.alwaysOn() ? GOLD : 0xFF55DD66) : 0xFF666666;
			clipRect(r - 10, y + ROW_H / 2 - 2, r - 6, y + ROW_H / 2 + 2, dot);
		}
		if (this.shown.isEmpty()) {
			this.drawCenteredString(this.fontRendererObj, "§7No mods found", (l + r) / 2, top + 12, 0xFFFFFF);
		}
		int max = maxScroll();
		if (max > 0) {
			int view = bottom - top;
			int thumb = Math.max(16, view * view / (view + max));
			int ty = top + (int) ((view - thumb) * (this.scroll / max));
			drawRect(r - 3, top, r - 1, bottom, 0x40000000);
			drawRect(r - 3, ty, r - 1, ty + thumb, 0xC0F5C542);
		}
	}

	private void drawDetails() {
		int l = detailLeft(), r = detailRight(), top = searchTop(), bottom = listBottom();
		drawRect(l, top, r, bottom, 0x88000000);
		drawGradientRect(l, top, r, top + 2, GOLD, 0xFFB8860B);
		Entry e = this.selected;
		if (e == null) {
			return;
		}
		int x = l + 10;
		drawGem(x, top + 12, 40, e, false);
		int nx = x + 50;
		GlStateManager.pushMatrix();
		GlStateManager.translate(nx, top + 14, 0);
		GlStateManager.scale(1.5F, 1.5F, 1.0F);
		this.fontRendererObj.drawStringWithShadow(
				this.fontRendererObj.trimStringToWidth("§l" + e.name, (int) ((r - nx - 8) / 1.5f)), 0, 0, 0xFFE08A);
		GlStateManager.popMatrix();
		List<String> byLines = this.fontRendererObj.listFormattedStringToWidth("§7by " + e.by, r - nx - 8);
		for (int i = 0; i < byLines.size() && i < 2; i++) {
			this.fontRendererObj.drawStringWithShadow(byLines.get(i), nx, top + 30 + i * 10, 0xFFFFFF);
		}

		// badges
		int by = top + 60, bx = x;
		boolean builtIn = e.license.equals("Built in");
		bx += drawPill(bx, by, builtIn ? "Built in" : e.license, builtIn ? GOLD : 0xFF5FC8E8) + 4;
		bx += drawPill(bx, by, categoryOf(e), 0xFFB48CFF) + 4;
		boolean active = e.available() && (e.alwaysOn() ? e.setting() : e.shownOn());
		String st = !e.available() ? "Unavailable" : e.alwaysOn() ? (active ? "Always on" : "Off") : (active ? "On" : "Off");
		if (bx + this.fontRendererObj.getStringWidth(st) + 8 > r - 8) {
			bx = x;
			by += 14;
		}
		drawPill(bx, by, st, !e.available() ? 0xFFE05555 : active ? 0xFF55DD66 : 0xFF999999);

		int dy = by + 18;
		drawRect(x, dy, r - 10, dy + 1, 0x30F5C542);
		dy += 7;
		int maxY = this.toggle.yPosition - 16;
		List<String> lines = this.fontRendererObj.listFormattedStringToWidth(e.description, r - l - 20);
		for (int i = 0; i < lines.size() && dy + 9 <= maxY; i++) {
			this.fontRendererObj.drawStringWithShadow(lines.get(i), x, dy, 0xDDDDDD);
			dy += 10;
		}
		if (dy + 9 <= maxY) {
			dy += 4;
			this.fontRendererObj.drawStringWithShadow(e.status(e.shownOn()), x, dy, 0xFFFFFF);
		}
	}

	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.scroll += (this.scrollTarget - this.scroll) * 0.35f;
		if (Math.abs(this.scrollTarget - this.scroll) < 0.5f) this.scroll = this.scrollTarget;
		long t = Minecraft.getSystemTime() - this.openedAt;
		this.drawDefaultBackground();
		this.drawSparkles(t);
		this.drawHeader(t);
		this.drawList(mouseX, mouseY, t);
		this.drawDetails();
		this.search.drawTextBox();
		if (this.search.getText().isEmpty() && !this.search.isFocused()) {
			this.fontRendererObj.drawStringWithShadow("§8Search mods...", listLeft() + 5, searchTop() + 4, 0xFFFFFF);
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
	}
}
