package net.minecraft.client.gui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.Mouse;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.math.MathHelper;

/**
 * Treasure Client: Video Settings laid out like Sodium's options screen (own
 * implementation). Pages along the top, option rows on the left and a description
 * of the hovered option on the right.
 *
 * Besides the vanilla video options, this is where every ported performance mod is
 * set up. Those settings are stored by the overlay (Treasure Settings), so changes
 * are sent to it through the "tcset" storage key, like the Mods screen does. The
 * overlay's single "Performance modpack" switch turns all of them on or off.
 */
public class GuiVideoSettings extends GuiScreen {

	private static final int ID_DONE = 200;
	private static final int ROW_H = 18;
	private static final int GROUP_GAP = 6;
	private static final int TAB_H = 18;

	private final GuiScreen parentGuiScreen;
	private final GameSettings guiGameSettings;

	private final List<Page> pages = new ArrayList<>();
	private static int lastPage = 0;
	private int page;
	private float scroll;
	private Opt dragging;

	// setting values sent to the overlay that the game hasn't reported back yet
	private final Map<String, String> pending = new HashMap<>();
	private final Map<String, Long> pendingTime = new HashMap<>();

	// layout, worked out in initGui
	private int listX, listY, listW, listBottom, tipX, tipW;

	public GuiVideoSettings(GuiScreen parentScreenIn, GameSettings gameSettingsIn) {
		this.parentGuiScreen = parentScreenIn;
		this.guiGameSettings = gameSettingsIn;
		this.buildPages();
	}

	// ---------------------------------------------------------------- options

	private abstract class Opt {
		final String name, tip, impact;

		Opt(String name, String tip, String impact) {
			this.name = name;
			this.tip = tip;
			this.impact = impact;
		}

		abstract String value();

		/** Left click = next value, right click = previous. */
		void click(int button) {
		}

		boolean slider() {
			return false;
		}

		float sliderPos() {
			return 0.0F;
		}

		void setSlider(float pos) {
		}

		boolean usable() {
			return true;
		}

		/** Shown grayed out because something above it is off (it can still be changed). */
		String inactiveReason() {
			return null;
		}
	}

	private class VanillaOpt extends Opt {
		final GameSettings.Options o;

		VanillaOpt(GameSettings.Options o, String tip, String impact) {
			super(I18n.format(o.getEnumString()), tip, impact);
			this.o = o;
		}

		String value() {
			String s = guiGameSettings.getKeyBinding(this.o);
			int i = s.indexOf(": ");
			return i >= 0 ? s.substring(i + 2) : s;
		}

		void click(int button) {
			if (!this.o.getEnumFloat()) {
				guiGameSettings.setOptionValue(this.o, 1);
			}
		}

		boolean slider() {
			return this.o.getEnumFloat();
		}

		float sliderPos() {
			return this.o.normalizeValue(guiGameSettings.getOptionFloatValue(this.o));
		}

		void setSlider(float pos) {
			float v = this.o.denormalizeValue(MathHelper.clamp(pos, 0.0F, 1.0F));
			if (v != guiGameSettings.getOptionFloatValue(this.o)) {
				guiGameSettings.setOptionFloatValue(this.o, v);
			}
		}
	}

	/** A switch saved by the overlay. */
	private class ModBool extends Opt {
		final String key;
		final boolean def;
		final String parent; // grayed out while this switch is off ("perfpack", "cextra")

		ModBool(String key, boolean def, String parent, String name, String tip, String impact) {
			super(name, tip, impact);
			this.key = key;
			this.def = def;
			this.parent = parent;
		}

		boolean on() {
			return "1".equals(setting(this.key, this.def ? "1" : "0"));
		}

		String value() {
			return this.on() ? "§aOn" : "§7Off";
		}

		void click(int button) {
			send(this.key, this.on() ? "0" : "1");
		}

		String inactiveReason() {
			return parentOff(this.parent);
		}
	}

	private class Celeritas extends ModBool {
		Celeritas() {
			super("celeritas", true, "perfpack", "Celeritas Renderer",
					"Faster chunk rendering, based on Sodium for Minecraft 1.12. Draws the world with far fewer draw calls. Turn it off if the world looks wrong.",
					"High");
		}

		boolean usable() {
			try {
				return org.taumc.celeritas.impl.TreasureCeleritas.isAvailable();
			} catch (Throwable t) {
				return false;
			}
		}

		String value() {
			return this.usable() ? super.value() : "§8Not available";
		}

		String inactiveReason() {
			if (!this.usable()) {
				return "Needs the WASM version and WebGL 2.";
			}
			return super.inactiveReason();
		}
	}

	private class LeafCull extends Opt {
		final String[] vals = { "off", "hollow", "aggressive", "solid" };
		final String[] labels = { "Off", "Hollow", "Solid Aggressive", "Solid" };

		LeafCull() {
			super("Leaf Culling",
					"Skips leaf faces hidden inside trees (Fancy graphics only). Hollow gives the most FPS, Solid looks like normal fancy leaves.",
					"Medium");
		}

		int index() {
			String v = setting("leafCull", "solid");
			for (int i = 0; i < this.vals.length; ++i) {
				if (this.vals[i].equals(v)) {
					return i;
				}
			}
			return 3;
		}

		String value() {
			return this.labels[this.index()];
		}

		void click(int button) {
			int n = this.vals.length;
			send("leafCull", this.vals[(this.index() + (button == 1 ? n - 1 : 1)) % n]);
		}

		String inactiveReason() {
			return parentOff("perfpack");
		}
	}

	private class EntityDistance extends Opt {
		EntityDistance() {
			super("Entity Render Distance",
					"Stops drawing mobs, players, items and chests past this many blocks. Off draws them as far as vanilla does. Lower gives more FPS in crowded places.",
					"Medium");
		}

		int blocks() {
			try {
				return Math.max(0, Math.min(128, Integer.parseInt(setting("entDist", "0"))));
			} catch (Throwable t) {
				return 0;
			}
		}

		String value() {
			int b = this.blocks();
			return b == 0 ? "Off" : b + " blocks";
		}

		boolean slider() {
			return true;
		}

		float sliderPos() {
			return this.blocks() / 128.0F;
		}

		void setSlider(float pos) {
			int b = Math.round(MathHelper.clamp(pos, 0.0F, 1.0F) * 16.0F) * 8;
			if (b != this.blocks()) {
				send("entDist", Integer.toString(b));
			}
		}

		String inactiveReason() {
			return parentOff("perfpack");
		}
	}

	private static final class Page {
		final String name;
		final List<List<Opt>> groups = new ArrayList<>();

		Page(String name) {
			this.name = name;
		}

		List<Opt> group() {
			List<Opt> g = new ArrayList<>();
			this.groups.add(g);
			return g;
		}
	}

	private void buildPages() {
		Page general = new Page("General");
		List<Opt> g = general.group();
		g.add(new VanillaOpt(GameSettings.Options.RENDER_DISTANCE,
				"How far away terrain is drawn. Shorter distances draw less terrain, which raises frame rates.", "High"));
		g.add(new VanillaOpt(GameSettings.Options.GAMMA,
				"How bright the game is. Higher values make dark areas easier to see.", null));
		g = general.group();
		g.add(new VanillaOpt(GameSettings.Options.GUI_SCALE, "How big menus and the HUD are.", null));
		g.add(new VanillaOpt(GameSettings.Options.USE_FULLSCREEN, "Makes the game fill your whole screen.", null));
		g.add(new VanillaOpt(GameSettings.Options.ENABLE_VSYNC,
				"Matches the frame rate to your screen's refresh rate. Removes tearing.", "Varies"));
		g.add(new VanillaOpt(GameSettings.Options.FRAMERATE_LIMIT,
				"The highest frame rate the game aims for. A lower limit keeps your device cooler.", null));
		g = general.group();
		g.add(new VanillaOpt(GameSettings.Options.VIEW_BOBBING,
				"Moves your view up and down slightly while walking.", null));
		g.add(new VanillaOpt(GameSettings.Options.ATTACK_INDICATOR,
				"Shows how charged your attack is, next to the crosshair or the hotbar.", null));
		g.add(new VanillaOpt(GameSettings.Options.HUD_FPS, "Shows your frame rate on the HUD.", null));
		g.add(new VanillaOpt(GameSettings.Options.HUD_COORDS, "Shows your coordinates on the HUD.", null));
		g.add(new VanillaOpt(GameSettings.Options.FNAW_SKINS, "Shows the special FNAW player skins.", null));
		this.pages.add(general);

		Page quality = new Page("Quality");
		g = quality.group();
		g.add(new VanillaOpt(GameSettings.Options.GRAPHICS,
				"Fancy draws see-through leaves and nicer weather. Fast is quicker.", "Medium"));
		g = quality.group();
		g.add(new VanillaOpt(GameSettings.Options.RENDER_CLOUDS, "How clouds are drawn, or turn them off.",
				"Low"));
		g.add(new VanillaOpt(GameSettings.Options.PARTICLES, "How many particles are shown.", "Medium"));
		g.add(new VanillaOpt(GameSettings.Options.AMBIENT_OCCLUSION,
				"Smooth lighting: soft shadows in corners and under blocks.", "Low"));
		g.add(new VanillaOpt(GameSettings.Options.ENTITY_SHADOWS, "Shows round shadows under mobs and items.",
				"Low"));
		g.add(new VanillaOpt(GameSettings.Options.FOG, "Fades out terrain at the edge of your render distance.",
				null));
		g = quality.group();
		g.add(new VanillaOpt(GameSettings.Options.MIPMAP_LEVELS,
				"Smooths textures that are far away. Lower can be faster on weak graphics chips.", "Low"));
		g.add(new VanillaOpt(GameSettings.Options.OF_CUSTOM_ITEMS,
				"Uses custom item textures from resource packs (OptiFine format).", "Low"));
		g.add(new VanillaOpt(GameSettings.Options.ANAGLYPH, "Red and cyan 3D, for 3D glasses.", "High"));
		this.pages.add(quality);

		Page perf = new Page("Performance");
		g = perf.group();
		g.add(new ModBool("perfpack", true, null, "Performance modpack",
				"All of the ported performance mods below. Turn it off to play like vanilla (handy if something looks broken). Their own settings stay saved. Also in Treasure Settings → Performance.",
				"High"));
		g = perf.group();
		g.add(new Celeritas());
		g.add(new ModBool("entcull", true, "perfpack", "Entity Culling",
				"Skips drawing mobs, chests, signs and banners that are hidden behind solid blocks.", "High"));
		g.add(new EntityDistance());
		g.add(new LeafCull());
		g.add(new ModBool("ebe", true, "perfpack", "Enhanced Block Entities",
				"Draws chests and beds as part of the world instead of one by one, which is much faster.",
				"Medium"));
		g = perf.group();
		g.add(new ModBool("ifast", true, "perfpack", "ImmediatelyFast",
				"Draws the HUD with far fewer draw calls, plus cheaper name labels and sign text.", "Medium"));
		g.add(new ModBool("pcull", true, "perfpack", "Particle Culling",
				"Skips particles behind you or off screen.", "Low"));
		g.add(new ModBool("pcore", true, "perfpack", "Particle Core",
				"Cheaper particle movement, fewer leftover particles, and particles land on the ground properly.",
				"Low"));
		g.add(new ModBool("bfmath", true, "perfpack", "BetterFps",
				"Faster sine and cosine math, the same numbers as OptiFine's Fast Math.", "Low"));
		g.add(new ModBool("lithium", true, "perfpack", "Lithium",
				"Faster collisions, chunk lookups and block entity lists. Same results as vanilla.", "Medium"));
		g = perf.group();
		g.add(new ModBool("c2me", true, "perfpack", "C2ME",
				"Smoother chunk loading: big bursts from the server are handled over a few ticks instead of freezing the game.",
				"Medium"));
		g.add(new ModBool("dynfps", true, "perfpack", "Dynamic FPS",
				"Drops to 10 FPS while another window is focused, so your device stays cool.", "Varies"));
		g.add(new ModBool("memsaver", true, "perfpack", "Memory Saver",
				"Stops the idle singleplayer worker while you're on a server.",
				"Low"));
		g.add(new ModBool("vanillafix", true, "perfpack", "VanillaFix",
				"Recovers from crashes instead of closing the game, plus Minecraft bug fixes.", null));
		g = perf.group();
		g.add(new VanillaOpt(GameSettings.Options.CHUNK_UPDATES,
				"How many chunks are rebuilt per frame. Lower is smoother, higher loads changes faster.", "Medium"));
		g.add(new VanillaOpt(GameSettings.Options.CHUNK_FIX, "Fixes chunks that sometimes don't show up.",
				null));
		this.pages.add(perf);

		Page mods = new Page("Mods");
		g = mods.group();
		g.add(new ModBool("cextra", true, "perfpack", "Celeritas Extra",
				"Lets you turn off animations, particles, sky parts and some entities below for extra FPS. When off, everything is drawn like vanilla.",
				"Varies"));
		g = mods.group();
		g.add(this.extra("ceAnim", "All Animations", "Every animated texture. Off freezes them all."));
		g.add(this.extra("ceAnimWater", "Water Animation", "Moving water."));
		g.add(this.extra("ceAnimLava", "Lava Animation", "Moving lava."));
		g.add(this.extra("ceAnimFire", "Fire Animation", "Flickering fire."));
		g.add(this.extra("ceAnimPortal", "Portal Animation", "Swirling nether portals."));
		g.add(this.extra("ceAnimBlock", "Block Animations",
				"Magma, sea lanterns, prismarine and other animated blocks."));
		g = mods.group();
		g.add(this.extra("ceRainSplash", "Rain Splashes", "Splash particles where rain lands."));
		g.add(this.extra("ceBreakFx", "Block Break Particles", "Pieces flying out when a block breaks."));
		g.add(this.extra("ceHitFx", "Block Mining Particles", "Small pieces while you mine a block."));
		g = mods.group();
		g.add(this.extra("ceSky", "Sky", "The sky color and sunset."));
		g.add(this.extra("ceStars", "Stars", "Stars at night."));
		g.add(this.extra("ceSunMoon", "Sun and Moon", "The sun and moon."));
		g.add(this.extra("ceWeather", "Rain and Snow", "Falling rain and snow."));
		g = mods.group();
		g.add(this.extra("ceFrames", "Item Frames", "Item frames and what's in them."));
		g.add(this.extra("ceStands", "Armor Stands", "Armor stands."));
		g.add(this.extra("cePaintings", "Paintings", "Paintings."));
		g.add(this.extra("ceBeacon", "Beacon Beams", "The light beam above beacons."));
		g.add(this.extra("ceEnchBook", "Enchanting Table Book", "The floating book on enchanting tables."));
		g.add(this.extra("cePiston", "Piston Movement", "Blocks sliding when pistons move them."));
		g.add(this.extra("ceToasts", "Toast Pop-ups", "Advancement and recipe pop-ups in the corner."));
		this.pages.add(mods);

		this.page = Math.max(0, Math.min(lastPage, this.pages.size() - 1));
	}

	private Opt extra(String key, String name, String what) {
		return new ModBool(key, true, "cextra", name, what + " Turn off for a little more FPS.", "Low");
	}

	// ---------------------------------------------------------------- settings plumbing

	/** The saved value, or the one we just asked for while the game catches up. */
	private String setting(String key, String def) {
		String saved = TreasureMods.userSetting(key, def);
		String want = this.pending.get(key);
		if (want != null) {
			Long t = this.pendingTime.get(key);
			if (want.equals(saved) || t == null || EagRuntime.steadyTimeMillis() - t > 3000L) {
				this.pending.remove(key); // picked up (or the overlay isn't there)
				this.pendingTime.remove(key);
			} else {
				return want;
			}
		}
		return saved;
	}

	private void send(String key, String value) {
		this.pending.put(key, value);
		this.pendingTime.put(key, EagRuntime.steadyTimeMillis());
		try {
			// add to any request the overlay hasn't read yet, so quick clicks aren't lost
			byte[] old = EagRuntime.getStorage("tcset");
			String prev = old != null ? new String(old, StandardCharsets.UTF_8) : "";
			String kv = key + "=" + value;
			EagRuntime.setStorage("tcset", (prev.isEmpty() ? kv : prev + "," + kv).getBytes(StandardCharsets.UTF_8));
		} catch (Throwable t) {
		}
	}

	private String parentOff(String parent) {
		if (parent == null) {
			return null;
		}
		if (parent.equals("cextra")) {
			String r = this.parentOff("perfpack");
			if (r != null) {
				return r;
			}
			return "1".equals(this.setting("cextra", "1")) ? null : "Celeritas Extra is off, so this is drawn like vanilla.";
		}
		return "1".equals(this.setting(parent, "1")) ? null
				: "Performance modpack is off, so this isn't running.";
	}

	// ---------------------------------------------------------------- layout

	public void initGui() {
		this.buttonList.clear();
		this.buttonList.add(new GuiButton(ID_DONE, this.width - 106, this.height - 26, 100, 20, I18n.format("gui.done")));
		this.listX = 6;
		this.listY = TAB_H + 14;
		this.listW = Math.min(310, Math.max(200, this.width / 2));
		if (this.listW > this.width - 12) {
			this.listW = this.width - 12;
		}
		this.listBottom = this.height - 32;
		this.tipX = this.listX + this.listW + 8;
		this.tipW = Math.min(220, this.width - this.tipX - 6);
		this.clampScroll();
	}

	private int contentHeight() {
		Page p = this.pages.get(this.page);
		int h = 0;
		for (List<Opt> g : p.groups) {
			h += g.size() * ROW_H + GROUP_GAP;
		}
		return h;
	}

	private void clampScroll() {
		int max = Math.max(0, this.contentHeight() - (this.listBottom - this.listY));
		this.scroll = MathHelper.clamp(this.scroll, 0.0F, (float) max);
	}

	/** The option under the mouse, or null. */
	private Opt optAt(int mx, int my) {
		if (mx < this.listX || mx >= this.listX + this.listW || my < this.listY || my >= this.listBottom) {
			return null;
		}
		int y = this.listY - (int) this.scroll;
		for (List<Opt> g : this.pages.get(this.page).groups) {
			for (Opt o : g) {
				if (my >= y && my < y + ROW_H) {
					return o;
				}
				y += ROW_H;
			}
			y += GROUP_GAP;
		}
		return null;
	}

	private int sliderX() {
		return this.listX + this.listW - 96;
	}

	private int tabX(int i) {
		int x = 6;
		for (int j = 0; j < i; ++j) {
			x += this.fontRendererObj.getStringWidth(this.pages.get(j).name) + 20;
		}
		return x;
	}

	// ---------------------------------------------------------------- input

	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int d = Mouse.getEventDWheel();
		if (d != 0) {
			this.scroll -= d > 0 ? ROW_H * 2 : -ROW_H * 2;
			this.clampScroll();
		}
	}

	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		super.mouseClicked(mouseX, mouseY, mouseButton);
		if (mouseY >= 6 && mouseY < 6 + TAB_H) {
			for (int i = 0; i < this.pages.size(); ++i) {
				int x = this.tabX(i);
				if (mouseX >= x && mouseX < x + this.fontRendererObj.getStringWidth(this.pages.get(i).name) + 16) {
					if (this.page != i) {
						this.page = lastPage = i;
						this.scroll = 0.0F;
						this.mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord
								.getMasterRecord(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.0F));
					}
					return;
				}
			}
		}
		Opt o = this.optAt(mouseX, mouseY);
		if (o == null || !o.usable() || (mouseButton != 0 && mouseButton != 1)) {
			return;
		}
		int scale = this.guiGameSettings.guiScale;
		if (o.slider()) {
			if (mouseButton == 0) {
				this.dragging = o;
				o.setSlider((mouseX - this.sliderX() - 4) / 88.0F);
			}
		} else {
			o.click(mouseButton);
			this.mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord
					.getMasterRecord(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}
		if (this.guiGameSettings.guiScale != scale) {
			this.resized();
		}
	}

	protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
		super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
		if (this.dragging != null) {
			this.dragging.setSlider((mouseX - this.sliderX() - 4) / 88.0F);
		}
	}

	protected void mouseReleased(int mouseX, int mouseY, int state) {
		super.mouseReleased(mouseX, mouseY, state);
		this.dragging = null;
	}

	/** GUI Scale changed: lay the screen out again at the new size. */
	private void resized() {
		ScaledResolution sr = this.mc.scaledResolution = new ScaledResolution(this.mc);
		int w = sr.getScaledWidth();
		int h = sr.getScaledHeight();
		this.setWorldAndResolution(this.mc, w, h);
		this.mc.notifRenderer.setResolution(this.mc, w, h, sr.getScaleFactor());
	}

	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (keyCode == 1) {
			this.mc.gameSettings.saveOptions();
		}
		super.keyTyped(typedChar, keyCode);
	}

	protected void actionPerformed(GuiButton button) throws IOException {
		if (button.enabled && button.id == ID_DONE) {
			this.mc.gameSettings.saveOptions();
			this.mc.displayGuiScreen(this.parentGuiScreen);
		}
	}

	// ---------------------------------------------------------------- drawing

	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();

		// page tabs
		for (int i = 0; i < this.pages.size(); ++i) {
			String n = this.pages.get(i).name;
			int x = this.tabX(i);
			int w = this.fontRendererObj.getStringWidth(n) + 16;
			boolean sel = i == this.page;
			boolean hov = mouseX >= x && mouseX < x + w && mouseY >= 6 && mouseY < 6 + TAB_H;
			drawRect(x, 6, x + w, 6 + TAB_H, sel ? 0xE0000000 : (hov ? 0xB0000000 : 0x90000000));
			if (sel) {
				drawRect(x, 6 + TAB_H - 1, x + w, 6 + TAB_H, 0xFFF5C542);
			}
			this.fontRendererObj.drawStringWithShadow(n, x + 8, 6 + (TAB_H - 8) / 2, sel ? 0xFFFFFF : 0xAAAAAA);
		}

		// option rows, cut off at the list edges
		Opt hovered = this.dragging != null ? this.dragging : this.optAt(mouseX, mouseY);
		int y = this.listY - (int) this.scroll;
		for (List<Opt> g : this.pages.get(this.page).groups) {
			for (Opt o : g) {
				if (y + ROW_H > this.listY && y < this.listBottom) {
					this.drawRow(o, y, o == hovered);
				}
				y += ROW_H;
			}
			y += GROUP_GAP;
		}
		int max = this.contentHeight() - (this.listBottom - this.listY);
		if (max > 0) {
			int barH = Math.max(16, (this.listBottom - this.listY) * (this.listBottom - this.listY) / this.contentHeight());
			int barY = this.listY + (int) ((this.listBottom - this.listY - barH) * (this.scroll / max));
			drawRect(this.listX + this.listW + 2, barY, this.listX + this.listW + 4, barY + barH, 0x80FFFFFF);
		}

		if (hovered != null) {
			this.drawTip(hovered, mouseY);
		}

		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	private void drawRow(Opt o, int y, boolean hovered) {
		int top = Math.max(y, this.listY);
		int bottom = Math.min(y + ROW_H, this.listBottom);
		if (bottom <= top) {
			return;
		}
		drawRect(this.listX, top, this.listX + this.listW, bottom, hovered ? 0xE0000000 : 0x90000000);
		if (y < this.listY || y + ROW_H > this.listBottom) {
			return; // only partly visible: just the background
		}
		boolean inactive = !o.usable() || o.inactiveReason() != null;
		int textY = y + (ROW_H - 8) / 2;
		this.fontRendererObj.drawStringWithShadow(o.name, this.listX + 6, textY, inactive ? 0x808080 : 0xFFFFFF);
		String v = o.value();
		if (o.slider()) {
			int sx = this.sliderX();
			drawRect(sx, y + 4, sx + 96, y + ROW_H - 4, 0xFF303030);
			int thumb = sx + 4 + (int) (88 * MathHelper.clamp(o.sliderPos(), 0.0F, 1.0F));
			drawRect(sx + 1, y + 5, thumb, y + ROW_H - 5, inactive ? 0xFF606060 : 0xFFB08A2E);
			drawRect(thumb - 2, y + 2, thumb + 2, y + ROW_H - 2, inactive ? 0xFF909090 : 0xFFFFFFFF);
			int vw = this.fontRendererObj.getStringWidth(v);
			this.fontRendererObj.drawStringWithShadow(v, sx - 6 - vw, textY, inactive ? 0x808080 : 0xFFFFFF);
		} else {
			if (inactive && v.startsWith("§")) {
				v = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(v);
			}
			int vw = this.fontRendererObj.getStringWidth(v);
			this.fontRendererObj.drawStringWithShadow(v, this.listX + this.listW - 6 - vw, textY,
					inactive ? 0x808080 : 0xFFFFFF);
		}
	}

	private void drawTip(Opt o, int mouseY) {
		List<String> lines = new ArrayList<>();
		boolean side = this.tipW >= 120;
		int w = side ? this.tipW - 10 : this.width - 22;
		lines.add("§e" + o.name);
		lines.addAll(this.fontRendererObj.listFormattedStringToWidth(o.tip, w));
		String reason = o.inactiveReason();
		if (reason != null) {
			lines.add("");
			lines.addAll(this.fontRendererObj.listFormattedStringToWidth("§c" + reason, w));
		}
		if (o.impact != null) {
			lines.add("");
			lines.add("§7Performance impact: §f" + o.impact);
		}
		int h = lines.size() * 10 + 8;
		int x, y;
		if (side) {
			x = this.tipX;
			y = MathHelper.clamp(mouseY - 10, this.listY, Math.max(this.listY, this.height - 32 - h));
		} else {
			x = 6;
			y = Math.max(this.listY, this.height - 32 - h);
		}
		int bw = w + 10;
		drawRect(x, y, x + bw, y + h, 0xF0000000);
		drawRect(x, y, x + bw, y + 1, 0xFFB08A2E);
		int ly = y + 5;
		for (String s : lines) {
			this.fontRendererObj.drawStringWithShadow(s, x + 5, ly, 0xFFFFFF);
			ly += 10;
		}
	}
}
