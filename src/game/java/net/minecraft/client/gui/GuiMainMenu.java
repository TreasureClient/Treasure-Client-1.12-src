package net.minecraft.client.gui;

import com.google.common.collect.Lists;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.EaglercraftRandom;
import net.lax1dude.eaglercraft.EaglercraftVersion;
import net.lax1dude.eaglercraft.IOUtils;
import net.lax1dude.eaglercraft.internal.PlatformApplication;
import net.lax1dude.eaglercraft.internal.PlatformOpenGL;
import net.lax1dude.eaglercraft.minecraft.MainMenuSkyboxTexture;
import net.lax1dude.eaglercraft.opengl.WorldRenderer;
import net.lax1dude.eaglercraft.sp.SingleplayerServerController;
import net.lax1dude.eaglercraft.sp.gui.GuiScreenIntegratedServerBusy;
import net.lax1dude.eaglercraft.sp.gui.GuiScreenIntegratedServerStartup;
import net.lax1dude.eaglercraft.opengl.EaglercraftGPU;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.lax1dude.eaglercraft.opengl.RealOpenGLEnums;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResource;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServerDemo;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraft.world.storage.WorldInfo;
import net.peyton.eagler.gui.GuiCredits;
import net.lax1dude.eaglercraft.profile.*;

public class GuiMainMenu extends GuiScreen {
	private static final EaglercraftRandom RANDOM = new EaglercraftRandom();

	// Treasure Client: current brand mode (false = Normal/gold, true = Deep Sea/blue).
	// Shared with the in-browser overlay mod menu via EagRuntime storage key "tcmode"
	// (localStorage "_eaglercraft_1.12.tcmode"), so toggling in either place agrees.
	private static boolean deepSeaMode = false;
	private static boolean modeLoaded = false;

	private static void loadMode() {
		try {
			byte[] b = EagRuntime.getStorage("tcmode");
			deepSeaMode = (b != null && b.length > 0 && b[0] == 1);
		} catch (Throwable t) {
			deepSeaMode = false;
		}
		modeLoaded = true;
	}

	private static void saveMode() {
		try {
			EagRuntime.setStorage("tcmode", new byte[] { (byte) (deepSeaMode ? 1 : 0) });
		} catch (Throwable t) {
		}
	}

	// Treasure Client: custom client title chosen in the Mod Menu's Themes tab.
	// The in-browser overlay writes the active theme's title to storage key
	// "tctitle" (localStorage "_eaglercraft_1.12.tctitle"); we read it here so the
	// main-menu brand line shows whatever the user renamed the client to
	// (e.g. "Frost Client"). Falls back to the default when unset. Throttled so
	// renaming in the overlay shows up live without a per-frame storage read.
	private static String brandTitle = "Treasure Client 1.5";
	private static boolean customBrand = false; // true when the user set a custom title
	/** The version shown on the 1.5 main menu, taken from the default title above (e.g. "1.5"). */
	private static final String TC_VERSION = brandTitle.startsWith("Treasure Client ") ? brandTitle.substring(16) : brandTitle;
	private static long brandRead = 0L;

	private static String brandLine() {
		try {
			long now = EagRuntime.steadyTimeMillis();
			if (now - brandRead > 500L) {
				brandRead = now;
				byte[] b = EagRuntime.getStorage("tctitle");
				if (b != null && b.length > 0) {
					String s = new String(b, StandardCharsets.UTF_8).trim();
					if (s.isEmpty()) { brandTitle = "Treasure Client 1.5"; customBrand = false; }
					else { brandTitle = s; customBrand = true; }
				} else {
					brandTitle = "Treasure Client 1.5"; customBrand = false;
				}
			}
		} catch (Throwable t) {
			brandTitle = "Treasure Client 1.5"; customBrand = false;
		}
		return brandTitle;
	}

	// palette helpers, per theme/mode. Frost (selected in the Mod Menu Themes tab)
	// uses the icy blue/cyan accent so the whole menu matches the theme.
	private boolean frost()      { return TreasureTheme.isFrost(); }
	private boolean tropical()   { return TreasureTheme.isTropical(); }
	private boolean terracotta() { return TreasureTheme.isTerracotta(); }
	private boolean forest()     { return TreasureTheme.isForest(); }
	private boolean sakura()     { return TreasureTheme.isSakura(); }
	private boolean dark()       { return TreasureTheme.isDark(); }
	private int cBgTop()    { return deepSeaMode ? 0xff0a3350 : 0xff2a1b0d; }
	private int cBgBottom() { return deepSeaMode ? 0xff030f18 : 0xff0f0a05; }
	// Per-theme accents: Frost=icy blue, Tropical=turquoise+sand, Terracotta=burnt
	// orange, Forest=green, else the default gold.
	private int cAccent() {
		if (sakura()) return 0xffff9cc6;
		if (dark()) return 0xffb9b4e8;
		if (frost()) return 0xff7fe9ff;
		if (tropical()) return 0xff45e3c6;
		if (terracotta()) return 0xffdb7a3c;
		if (forest()) return 0xff6fcb5b;
		return 0xffffd76b;
	}
	private int cAccentDim() {
		if (sakura()) return 0xffddb0c4;
		if (dark()) return 0xffa9a6bd;
		if (frost()) return 0xff8fb9cc;
		if (tropical()) return 0xffe0cd97;
		if (terracotta()) return 0xffe8b98a;
		if (forest()) return 0xffbfe0a0;
		return 0xffc9b183;
	}
	private int cParticle() {
		if (sakura()) return 0x80ffb7d5;
		if (dark()) return 0x80ffd98a;
		if (frost()) return 0x807fe9ff;
		if (tropical()) return 0x8045e3c6;
		if (terracotta()) return 0x80e89a5a;
		if (forest()) return 0x80bfe88a;
		return 0x80ffd76b;
	}

	/** Counts the number of screen updates. */
	private final float updateCounter;

	/** The splash message. */
	private String splashText;
	private GuiButton buttonResetDemo;

	/** Timer used to rotate the panorama, increases every tick. */
	private float panoramaTimer;

	/**
	 * Texture allocated for the current viewport of the main menu's panorama
	 * background.
	 */
	private static MainMenuSkyboxTexture viewportTexture = null;
	private static MainMenuSkyboxTexture viewportTexture2 = null;

	/**
	 * The Object object utilized as a thread lock when performing non thread-safe
	 * operations
	 */
	// private final Object threadLock = new Object();
	public static final String MORE_INFO_TEXT = "Please click " + TextFormatting.UNDERLINE + "here"
			+ TextFormatting.RESET + " for more information.";

	private static final ResourceLocation SPLASH_TEXTS = new ResourceLocation("texts/splashes.txt");
	private static final ResourceLocation MINECRAFT_TITLE_TEXTURES = new ResourceLocation(
			"textures/gui/title/minecraft.png");
	private static final ResourceLocation field_194400_H = new ResourceLocation("textures/gui/title/edition.png");
	// Treasure Client: the theme background images are in TreasureBackground.
	// Treasure Client: per-theme recolored chest logo drawn above the wordmark.
	private static final ResourceLocation LOGO_NORMAL = new ResourceLocation("textures/gui/title/treasure_logo_normal.png");
	private static final ResourceLocation LOGO_FROST = new ResourceLocation("textures/gui/title/treasure_logo_frost.png");
	private static final ResourceLocation LOGO_TROPICAL = new ResourceLocation("textures/gui/title/treasure_logo_tropical.png");
	private static final ResourceLocation LOGO_TERRACOTTA = new ResourceLocation("textures/gui/title/treasure_logo_terracotta.png");
	private static final ResourceLocation LOGO_FOREST = new ResourceLocation("textures/gui/title/treasure_logo_forest.png");
	private static final ResourceLocation LOGO_SAKURA = new ResourceLocation("textures/gui/title/treasure_logo_sakura.png");
	private static final ResourceLocation LOGO_DARK = new ResourceLocation("textures/gui/title/treasure_logo_dark.png");
	private static final float LOGO_ASPECT = 221.0F / 224.0F; // texture width / height

	/** An array of all the paths to the panorama pictures. */
	private static final ResourceLocation[] TITLE_PANORAMA_PATHS = new ResourceLocation[] {
			new ResourceLocation("textures/gui/title/background/panorama_0.png"),
			new ResourceLocation("textures/gui/title/background/panorama_1.png"),
			new ResourceLocation("textures/gui/title/background/panorama_2.png"),
			new ResourceLocation("textures/gui/title/background/panorama_3.png"),
			new ResourceLocation("textures/gui/title/background/panorama_4.png"),
			new ResourceLocation("textures/gui/title/background/panorama_5.png") };
	private ResourceLocation backgroundTexture = null;
	private static ResourceLocation backgroundTexture2 = null;

	private int field_193978_M;
	private int field_193979_N;

	private static final String[] TREASURE_CLIENT_SPLASHES = new String[] {
			"Set sail!", "X marks the spot!", "Yo ho ho!", "Anchors aweigh!",
			"Dead men tell no tales...", "Thar she blows!", "Shiver me timbers!",
			"Hoist the colors!", "Avast, ye scallywags!", "Batten down the hatches!",
			"Land ho!", "All hands on deck!", "Buried treasure awaits!",
			"Dive for the gold!", "Fair winds and following seas!", "A pirate's life for ye!",
			"Hidden coves and buried chests!", "Beware the kraken!", "Walk the plank!",
			"Full sail ahead!", "Doubloons and pieces of eight!", "To the depths we go!" };

	public GuiMainMenu() {
		this.splashText = TREASURE_CLIENT_SPLASHES[RANDOM.nextInt(TREASURE_CLIENT_SPLASHES.length)];
		this.updateCounter = RANDOM.nextFloat();
	}

	/**
	 * Called from the main game loop to update the screen.
	 */
	public void updateScreen() {
		// Treasure Client 1.5: the big menu words are drawn by the page overlay (shared by every
		// edition). It asks for things through "tcmenuact" and says it is alive through "tcmenuack".
		try {
			byte[] a = EagRuntime.getStorage("tcmenuact");
			if (a != null) {
				EagRuntime.setStorage("tcmenuact", null);
				if (a.length > 0) {
					this.tcDoAction(new String(a, StandardCharsets.UTF_8).trim());
					return;
				}
			}
			byte[] k = EagRuntime.getStorage("tcmenuack");
			long ack = 0L;
			if (k != null && k.length > 0 && k.length < 20) {
				try { ack = Long.parseLong(new String(k, StandardCharsets.US_ASCII).trim()); } catch (NumberFormatException e) { ack = 0L; }
			}
			long now = EagRuntime.steadyTimeMillis();
			if (Math.abs(System.currentTimeMillis() - ack) < 2500L) {
				this.tcWatch.live();
			}
			// the overlay only draws the menu while "tcmenu" says it's showing. Something can clear it
			// while we're still open (the overlay clears a stale one when it starts up, which can happen
			// after this menu opened), so say it again once a second if it's gone.
			if (now - this.tcLastFlagCheck > 1000L) {
				this.tcLastFlagCheck = now;
				byte[] m = EagRuntime.getStorage("tcmenu");
				if (m == null || m.length == 0 || m[0] != 1) {
					this.tcPublishMenu();
				}
			}
		} catch (Throwable t) {
		}
		// the game's own buttons only come back if the overlay isn't answering (so you can never get stuck)
		boolean fallback = this.tcFallback();
		for (int i = 0; i < this.buttonList.size(); ++i) {
			this.buttonList.get(i).visible = fallback;
		}
	}

	// Treasure Client 1.5: overlay main menu state
	/** Decides when the overlay isn't answering, so the game draws its own buttons. */
	private final TCOverlayWatch tcWatch = new TCOverlayWatch();
	private long tcLastFlagCheck = 0L;

	/** True when the overlay isn't drawing the menu, so the game draws its own buttons (see TCOverlayWatch). */
	private boolean tcFallback() {
		return this.tcWatch.fallback();
	}

	/** Tells the overlay the main menu is showing, and what to put on it. */
	private void tcPublishMenu() {
		try {
			String rejoin = TCLastServer.has() && !this.mc.isDemo() ? TCLastServer.name() : "";
			String prof = "";
			try {
				TCProfiles.Profile p = TCProfiles.get(TCProfiles.activeId());
				if (p != null && p.name != null) prof = p.name;
			} catch (Throwable t) {
			}
			String info = this.mc.getSession().getProfile().getName() + "\n" + rejoin.replace('\n', ' ') + "\n"
					+ prof.replace('\n', ' ') + "\n" + TC_VERSION + "\n" + (this.mc.isDemo() ? "1" : "0");
			EagRuntime.setStorage("tcmenuinfo", info.getBytes(StandardCharsets.UTF_8));
			EagRuntime.setStorage("tcmenu", new byte[] { (byte) 1 });
		} catch (Throwable t) {
		}
	}

	/** Runs a menu word the overlay clicked (same as pressing the matching button). */
	private void tcDoAction(String code) {
		int id;
		switch (code) {
		case "sp": id = 1; break;
		case "mp": id = 2; break;
		case "demo": id = 11; break;
		case "cos": id = 104; break;
		case "set": id = 0; break;
		case "prof": id = 103; break;
		case "rejoin": id = 110; break;
		case "lang": id = 5; break;
		case "about": id = 102; break;
		case "mods": id = 102; break; // the Fabric-style list of every mod we ported
		default: return;
		}
		for (int i = 0; i < this.buttonList.size(); ++i) {
			GuiButton b = this.buttonList.get(i);
			if (b.id == id) {
				b.playPressSound(this.mc.getSoundHandler());
				try {
					this.actionPerformed(b);
				} catch (IOException e) {
				}
				return;
			}
		}
	}

	/**
	 * Returns true if this GUI should pause the game when it is displayed in
	 * single-player
	 */
	public boolean doesGuiPauseGame() {
		return false;
	}

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
		this.mc.gameSettings.hasSeenFirstLoad = true;
		if (!modeLoaded) loadMode();

		viewportTexture = new MainMenuSkyboxTexture(256, 256);
		this.backgroundTexture = this.mc.getTextureManager().getDynamicTextureLocation("background", this.viewportTexture);
		viewportTexture2 = new MainMenuSkyboxTexture(256, 256);
		backgroundTexture2 = this.mc.getTextureManager().getDynamicTextureLocation("background", viewportTexture2);
		this.field_193978_M = this.fontRendererObj.getStringWidth("Resources copyright Mojang AB");
		this.field_193979_N = this.width - this.field_193978_M - 2;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(new Date());

		if (calendar.get(2) + 1 == 12 && calendar.get(5) == 24) {
			this.splashText = I18n.format("eaglercraft.splashes.xmas");
		} else if (calendar.get(2) + 1 == 1 && calendar.get(5) == 1) {
			this.splashText = I18n.format("eaglercraft.splashes.newyear");
		} else if (calendar.get(2) + 1 == 10 && calendar.get(5) == 31) {
			this.splashText = I18n.format("eaglercraft.splashes.halloween");
		}

		// Treasure Client 1.5: side-panel layout. Buttons sit in a dark glass panel on the left,
		// your player stands on the right, and "Rejoin" sits under the panel.
		int gap = this.height < 300 ? 22 : 24;
		int rows = 6;
		this.tcPanelW = 136;
		this.tcPanelX = 12;
		this.tcPanelH = 8 + rows * gap - (gap - 20) + 8;
		boolean rejoin = TCLastServer.has() && !this.mc.isDemo();
		int below = rejoin ? 24 : 0;
		this.tcPanelY = Math.max(48, (this.height - this.tcPanelH - below) / 2 + 6);
		int bx = this.tcPanelX + 8, bw = this.tcPanelW - 16;
		int j = this.tcPanelY + 8;

		if (this.mc.isDemo()) {
			this.addDemoButtons(bx, bw, j, gap);
		} else {
			this.buttonList.add(new GuiButton(1, bx, j, bw, 20, I18n.format("menu.singleplayer")));
			this.buttonList.add(new GuiButton(2, bx, j + gap, bw, 20, I18n.format("menu.multiplayer")));
		}
		// the Mod Menu is the in-browser overlay (also on its key, Right Shift by default)
		// same order as the overlay's words: Options, Treasure (the Mod Menu), Mods
		// (the list of every mod we ported, with the credits), Cosmetics
		this.buttonList.add(new GuiButton(0, bx, j + gap * 2, bw - 24, 20, I18n.format("menu.options")));
		this.buttonList.add(new GuiButtonLanguage(5, bx + bw - 20, j + gap * 2));
		this.buttonList.add(new GuiButton(101, bx, j + gap * 3, bw, 20, "Treasure"));
		this.buttonList.add(new GuiButton(102, bx, j + gap * 4, bw, 20, "Mods"));
		this.buttonList.add(new GuiButton(104, bx, j + gap * 5, bw, 20, "Cosmetics"));
		if (rejoin) {
			String n = TCLastServer.name();
			String label = "Rejoin " + n;
			while (n.length() > 3 && this.fontRendererObj.getStringWidth(label) > this.tcPanelW - 12) {
				n = n.substring(0, n.length() - 1);
				label = "Rejoin " + n.trim() + "...";
			}
			this.buttonList.add(new GuiButton(110, this.tcPanelX, this.tcPanelY + this.tcPanelH + 4, this.tcPanelW, 20, label));
		}
		this.buttonList.add(new GuiButtonTCProfile(103, this.width - 28, 6)); // Treasure Client: Profiles
		// Treasure Client 1.5: the overlay draws the menu; the buttons above stay hidden unless it doesn't answer
		this.tcWatch.start(); // coming back to this menu: wait for the overlay's next answer
		boolean fallback = this.tcFallback();
		for (int i = 0; i < this.buttonList.size(); ++i) {
			this.buttonList.get(i).visible = fallback;
		}
		this.tcPublishMenu();
	}

	// Treasure Client 1.5: side panel and player positions (set in initGui)
	private int tcPanelX, tcPanelY, tcPanelW, tcPanelH;
	private static float tcYaw = -22.0F; // the player's turn, kept while the game is open
	private boolean tcDragging = false;
	private int tcDragLastX = 0;
	private static final String TC_CHANGELOG_URL = "https://treasureclient.github.io/changelog.html";

	/** Left edge of the player's area, right of the panel. */
	private int tcPlayerLeft() {
		return this.tcPanelX + this.tcPanelW + 10;
	}

	/** Whether there is room for the player (very narrow windows only show the panel). */
	private boolean tcShowPlayer() {
		return this.width - this.tcPlayerLeft() >= 90 && this.height >= 150;
	}

	private int tcWhatsNewW() {
		return this.fontRendererObj.getStringWidth("What's new");
	}

	/**
	 * Adds Singleplayer and Multiplayer buttons on Main Menu for players who have
	 * bought the game.
	 */
	private void addSingleplayerMultiplayerButtons(int p_73969_1_, int p_73969_2_) {
		this.buttonList.add(new GuiButton(1, this.width / 2 - 100, p_73969_1_, I18n.format("menu.singleplayer")));
		this.buttonList.add(
				new GuiButton(2, this.width / 2 - 100, p_73969_1_ + p_73969_2_ * 1, I18n.format("menu.multiplayer")));
	}

	/**
	 * Adds Demo buttons on Main Menu for players who are playing Demo.
	 */
	private void addDemoButtons(int x, int w, int p_73972_1_, int p_73972_2_) {
		this.buttonList.add(new GuiButton(11, x, p_73972_1_, w, 20, I18n.format("menu.playdemo")));
		this.buttonResetDemo = this.addButton(
				new GuiButton(12, x, p_73972_1_ + p_73972_2_ * 1, w, 20, I18n.format("menu.resetdemo")));
		ISaveFormat isaveformat = this.mc.getSaveLoader();
		WorldInfo worldinfo = isaveformat.getWorldInfo("Demo_World");

		if (worldinfo == null) {
			this.buttonResetDemo.enabled = false;
		}
	}

	/**
	 * Called by the controls from the buttonList when activated. (Mouse pressed for
	 * buttons)
	 */
	protected void actionPerformed(GuiButton button) throws IOException {
		if (button.id == 101) {
			// Treasure Client: ask the overlay to open its Mod Menu. The overlay
			// polls this storage key and opens itself (works even if the hotkey
			// is unbound). Written via the shared EagRuntime storage.
			try {
				EagRuntime.setStorage("tcopen", new byte[] { (byte) 1 });
			} catch (Throwable t) {
			}
			return;
		}

		if (button.id == 0) {
			this.mc.displayGuiScreen(new GuiOptions(this, this.mc.gameSettings));
		}

		if (button.id == 104) {
			// Treasure Client 1.5: Cosmetics (capes and auras; skin & Eaglercraft cape is inside)
			this.mc.displayGuiScreen(new GuiTCCosmetics(this));
		}

		if (button.id == 110) {
			net.minecraft.client.multiplayer.ServerData d = TCLastServer.data();
			if (d != null) {
				this.mc.displayGuiScreen(new net.minecraft.client.multiplayer.GuiConnecting(this, this.mc, d));
			}
		}

		if (button.id == 103) {
			this.mc.displayGuiScreen(new GuiTCProfiles(this));
		}

		if (button.id == 102) {
			this.mc.displayGuiScreen(new GuiTreasureModList(this));
		}

		if (button.id == 5) {
			this.mc.displayGuiScreen(new GuiLanguage(this, this.mc.gameSettings, this.mc.getLanguageManager()));
		}

		if (button.id == 1) {
			this.mc.displayGuiScreen(new GuiScreenIntegratedServerStartup(this));
		}

		if (button.id == 2) {
			this.mc.displayGuiScreen(new GuiMultiplayer(this));
		}

		if (button.id == 4) {
			this.mc.displayGuiScreen(new GuiScreenEditProfile(this));
		}

		if (button.id == 11) {
			this.mc.launchIntegratedServer("Demo_World", "Demo_World", WorldServerDemo.DEMO_WORLD_SETTINGS);
		}

		if (button.id == 12) {
			ISaveFormat isaveformat = this.mc.getSaveLoader();
			WorldInfo worldinfo = isaveformat.getWorldInfo("Demo_World");

			if (worldinfo != null) {
				this.mc.displayGuiScreen(new GuiYesNo(this, I18n.format("selectWorld.deleteQuestion"),
						"'" + worldinfo.getWorldName() + "' " + I18n.format("selectWorld.deleteWarning"),
						I18n.format("selectWorld.deleteButton"), I18n.format("gui.cancel"), 12));
			}
		}

		if (button.id == 14) {
			this.mc.displayGuiScreen(new GuiCredits(this, "/assets/eagler/credits.txt"));
		}
	}

	public void confirmClicked(boolean result, int id) {
		if (result && id == 12) {
			ISaveFormat isaveformat = this.mc.getSaveLoader();
			isaveformat.flushCache();
			isaveformat.deleteWorldDirectory("Demo_World");
			this.mc.displayGuiScreen(new GuiScreenIntegratedServerBusy(this, "singleplayer.busy.deleting",
					"singleplayer.failed.deleting", SingleplayerServerController::isReady));
		} else if (id == 12) {
			this.mc.displayGuiScreen(this);
		}
	}

	/**
	 * Draws the main menu panorama
	 */
	private void drawPanorama(int mouseX, int mouseY, float partialTicks) {
		Tessellator tessellator = Tessellator.getInstance();
		WorldRenderer bufferbuilder = tessellator.getBuffer();
		GlStateManager.matrixMode(5889);
		GlStateManager.pushMatrix();
		GlStateManager.loadIdentity();
		GlStateManager.gluPerspective(120.0F, 1.0F, 0.05F, 10.0F);
		GlStateManager.matrixMode(5888);
		GlStateManager.pushMatrix();
		GlStateManager.loadIdentity();
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
		GlStateManager.rotate(90.0F, 0.0F, 0.0F, 1.0F);
		GlStateManager.enableBlend();
		GlStateManager.disableAlpha();
		GlStateManager.disableCull();
		GlStateManager.depthMask(false);
		GlStateManager.tryBlendFuncSeparate(RealOpenGLEnums.GL_SRC_ALPHA, RealOpenGLEnums.GL_ONE_MINUS_SRC_ALPHA,
				RealOpenGLEnums.GL_ONE, RealOpenGLEnums.GL_ZERO);
		byte b0 = 4;

		for (int i = 0; i < b0 * b0; ++i) {
			GlStateManager.pushMatrix();
			float f = ((float) (i % b0) / (float) b0 - 0.5F) / 64.0F;
			float f1 = ((float) (i / b0) / (float) b0 - 0.5F) / 64.0F;
			float f2 = 0.0F;
			GlStateManager.translate(f, f1, f2);
			GlStateManager.rotate(MathHelper.sin(((float) this.panoramaTimer + partialTicks) / 400.0F) * 25.0F + 20.0F,
					1.0F, 0.0F, 0.0F);
			GlStateManager.rotate(-((float) this.panoramaTimer + partialTicks) * 0.1F, 0.0F, 1.0F, 0.0F);

			for (int j = 0; j < 6; ++j) {
				GlStateManager.pushMatrix();
				if (j == 1) {
					GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
				}

				if (j == 2) {
					GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
				}

				if (j == 3) {
					GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F);
				}

				if (j == 4) {
					GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
				}

				if (j == 5) {
					GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
				}

				this.mc.getTextureManager().bindTexture(TITLE_PANORAMA_PATHS[j]);

				bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
				int k = 255 / (i + 1);
				float f3 = 0.0F;
				bufferbuilder.pos(-1.0D, -1.0D, 1.0D).tex(0.0D, 0.0D).color(255, 255, 255, k).endVertex();
				bufferbuilder.pos(1.0D, -1.0D, 1.0D).tex(1.0D, 0.0D).color(255, 255, 255, k).endVertex();
				bufferbuilder.pos(1.0D, 1.0D, 1.0D).tex(1.0D, 1.0D).color(255, 255, 255, k).endVertex();
				bufferbuilder.pos(-1.0D, 1.0D, 1.0D).tex(0.0D, 1.0D).color(255, 255, 255, k).endVertex();
				tessellator.draw();
				GlStateManager.popMatrix();
			}

			GlStateManager.popMatrix();
			GlStateManager.colorMask(true, true, true, false);
		}

		bufferbuilder.setTranslation(0.0D, 0.0D, 0.0D);
		GlStateManager.colorMask(true, true, true, true);
		GlStateManager.matrixMode(5889);
		GlStateManager.popMatrix();
		GlStateManager.matrixMode(5888);
		GlStateManager.popMatrix();
		GlStateManager.depthMask(true);
		GlStateManager.enableCull();
		GlStateManager.enableDepth();
	}

	/**
	 * Rotate and blurs the skybox view in the main menu
	 */
	private void rotateAndBlurSkybox() {
		EaglercraftGPU.glTexParameteri(3553, 10241, 9729);
		EaglercraftGPU.glTexParameteri(3553, 10240, 9729);
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(RealOpenGLEnums.GL_SRC_ALPHA, RealOpenGLEnums.GL_ONE_MINUS_SRC_ALPHA,
				RealOpenGLEnums.GL_ONE, RealOpenGLEnums.GL_ZERO);
		GlStateManager.colorMask(true, true, true, false);
		Tessellator tessellator = Tessellator.getInstance();
		WorldRenderer bufferbuilder = tessellator.getBuffer();
		bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
		GlStateManager.disableAlpha();
		int i = 3;

		for (int j = 0; j < 3; ++j) {
			float f = 1.0F / (float) (j + 1);
			int k = this.width;
			int l = this.height;
			float f1 = (float) (j - 1) / 256.0F;
			bufferbuilder.pos((double) k, (double) l, (double) this.zLevel).tex((double) (0.0F + f1), 1.0D)
					.color(1.0F, 1.0F, 1.0F, f).endVertex();
			bufferbuilder.pos((double) k, 0.0D, (double) this.zLevel).tex((double) (1.0F + f1), 1.0D)
					.color(1.0F, 1.0F, 1.0F, f).endVertex();
			bufferbuilder.pos(0.0D, 0.0D, (double) this.zLevel).tex((double) (1.0F + f1), 0.0D)
					.color(1.0F, 1.0F, 1.0F, f).endVertex();
			bufferbuilder.pos(0.0D, (double) l, (double) this.zLevel).tex((double) (0.0F + f1), 0.0D)
					.color(1.0F, 1.0F, 1.0F, f).endVertex();
		}

		tessellator.draw();
		GlStateManager.enableAlpha();
		GlStateManager.colorMask(true, true, true, true);
	}

	/**
	 * Renders the skybox in the main menu
	 */
	private void renderSkybox(int mouseX, int mouseY, float partialTicks) {
		viewportTexture.bindFramebuffer();
		GlStateManager.viewport(0, 0, 256, 256);
		GlStateManager.clearColor(0.0f, 0.0f, 0.0f, 1.0f);
		GlStateManager.clear(RealOpenGLEnums.GL_COLOR_BUFFER_BIT);
		this.drawPanorama(mouseX, mouseY, partialTicks);
		viewportTexture2.bindFramebuffer();
		GlStateManager.clearColor(0.0f, 0.0f, 0.0f, 1.0f);
		GlStateManager.clear(RealOpenGLEnums.GL_COLOR_BUFFER_BIT);
		this.mc.getTextureManager().bindTexture(backgroundTexture);
		this.rotateAndBlurSkybox();
		viewportTexture.bindFramebuffer();
		this.mc.getTextureManager().bindTexture(backgroundTexture2);
		this.rotateAndBlurSkybox();
		viewportTexture2.bindFramebuffer();
		this.mc.getTextureManager().bindTexture(backgroundTexture);
		this.rotateAndBlurSkybox();
		viewportTexture.bindFramebuffer();
		this.mc.getTextureManager().bindTexture(backgroundTexture2);
		this.rotateAndBlurSkybox();
		viewportTexture2.bindFramebuffer();
		this.mc.getTextureManager().bindTexture(backgroundTexture);
		this.rotateAndBlurSkybox();
		viewportTexture.bindFramebuffer();
		this.mc.getTextureManager().bindTexture(backgroundTexture2);
		this.rotateAndBlurSkybox();

		PlatformOpenGL._wglBindFramebuffer(0x8D40, null);
		this.mc.getTextureManager().bindTexture(backgroundTexture);
		
		GlStateManager.viewport(0, 0, this.mc.displayWidth, this.mc.displayHeight);
		float f = 120.0F / (float) (this.width > this.height ? this.width : this.height);
		float f1 = (float) this.height * f / 256.0F;
		float f2 = (float) this.width * f / 256.0F;
		int i = this.width;
		int j = this.height;
		Tessellator tessellator = Tessellator.getInstance();
		WorldRenderer bufferbuilder = tessellator.getBuffer();
		bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
		bufferbuilder.pos(0.0D, (double) j, (double) this.zLevel).tex((double) (0.5F - f1), (double) (0.5F + f2))
				.color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
		bufferbuilder.pos((double) i, (double) j, (double) this.zLevel).tex((double) (0.5F - f1), (double) (0.5F - f2))
				.color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
		bufferbuilder.pos((double) i, 0.0D, (double) this.zLevel).tex((double) (0.5F + f1), (double) (0.5F - f2))
				.color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
		bufferbuilder.pos(0.0D, 0.0D, (double) this.zLevel).tex((double) (0.5F + f1), (double) (0.5F + f2))
				.color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
		tessellator.draw();
	}

	/**
	 * Treasure Client: draw a main-menu background image stretched to fill the
	 * whole screen (behind the buttons), the same way the panorama blit does.
	 */
	private void drawBgTexture(ResourceLocation tex) {
		// sharp, or pre-blurred once if the theme's "Blur background" is on (see TreasureBackground.drawBlurredBg)
		TreasureBackground.drawBlurredBg(tex, this.width, this.height, this.zLevel);
	}


	/** Treasure Client: the chest logo texture for the active theme. */
	private ResourceLocation activeLogo() {
		if (this.frost()) return LOGO_FROST;
		if (this.tropical()) return LOGO_TROPICAL;
		if (this.terracotta()) return LOGO_TERRACOTTA;
		if (this.forest()) return LOGO_FOREST;
		if (this.sakura()) return LOGO_SAKURA;
		if (this.dark()) return LOGO_DARK;
		return LOGO_NORMAL;
	}

	/**
	 * Treasure Client: draw the recolored chest logo centered at cx with its top
	 * at topY and the given height (width follows the texture aspect ratio).
	 */
	private void drawLogo(int cx, int topY, int h) {
		int w = Math.round(h * LOGO_ASPECT);
		GlStateManager.enableBlend();
		GlStateManager.blendFunc(770, 771); // GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		this.mc.getTextureManager().bindTexture(this.activeLogo());
		EaglercraftGPU.glTexParameteri(3553, 10241, 9729); // GL_LINEAR so it isn't pixelated
		EaglercraftGPU.glTexParameteri(3553, 10240, 9729);
		Tessellator tessellator = Tessellator.getInstance();
		WorldRenderer bufferbuilder = tessellator.getBuffer();
		double x0 = cx - w / 2.0, x1 = cx + w / 2.0, y0 = topY, y1 = topY + h, z = this.zLevel;
		bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
		bufferbuilder.pos(x0, y1, z).tex(0.0D, 1.0D).color(255, 255, 255, 255).endVertex();
		bufferbuilder.pos(x1, y1, z).tex(1.0D, 1.0D).color(255, 255, 255, 255).endVertex();
		bufferbuilder.pos(x1, y0, z).tex(1.0D, 0.0D).color(255, 255, 255, 255).endVertex();
		bufferbuilder.pos(x0, y0, z).tex(0.0D, 0.0D).color(255, 255, 255, 255).endVertex();
		tessellator.draw();
		GlStateManager.disableBlend();
	}

	/**
	 * Draws the screen and all the components in it.
	 */
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.tcWatch.frame();
		this.panoramaTimer += partialTicks;

		boolean frost = this.frost();
		boolean tropical = this.tropical();
		boolean terracotta = this.terracotta();
		boolean forest = this.forest();

		// ---- background ----
		// Each theme uses a full-screen image background.
		// (the background picked for this theme in Treasure (the Mod Menu); sharp unless "Blur background" is on)
		this.drawBgTexture(TreasureBackground.activeBg());
		// very light darkening only near the bottom, so the background stays clearly visible
		this.drawGradientRect(0, 0, this.width, this.height, 0x00000000, 0x33000000);

		// ---- animated particle field (snow on wind / gold flecks fall / bubbles rise) ----
		// Deterministic pseudo-random field advanced by system time; no textures,
		// just small drawn rects, so it's cheap and portable.
		long t = Minecraft.getSystemTime();
		int particleColor = frost ? 0x99EAF6FF
				: tropical ? 0x9945E3C6
				: terracotta ? 0x99E89A5A
				: forest ? 0x99C8E88A
				: dark() ? 0xAAFFD98A
				: cParticle();
		// Frost wind: two slow sine waves summed so the gust eases between calm and
		// windy over time (never a constant gale). Value = horizontal push in px.
		float gust = frost
				? (MathHelper.sin((float) (t / 7000.0)) * 0.5F + MathHelper.sin((float) (t / 2300.0)) * 0.5F) * 34.0F
				: 0.0F;
		int count = frost ? 50 : 30;
		for (int i = 0; i < count; i++) {
			// stable per-particle pseudo-random seeds
			float sx = ((i * 73 + 11) % 100) / 100.0F;
			float speed = 0.25F + (((i * 37 + 7) % 100) / 100.0F) * 0.55F;
			float size = frost ? (1.0F + ((i * 29 + 5) % 2)) : (1.0F + ((i * 53 + 3) % 2));
			float sway = MathHelper.sin((float) ((t / 1600.0) + i)) * (frost ? 7.0F : 12.0F);
			float lane = 0.4F + ((i * 17 + 9) % 100) / 100.0F; // how strongly this flake catches the wind
			int x = (int) (sx * this.width + sway + (frost ? gust * lane : 0.0F));
			if (frost) {
				// wrap horizontally so wind-blown flakes re-enter from the other edge
				int w = this.width + 32;
				x = (((x + 16) % w) + w) % w - 16;
			}
			float phase = (float) (((t / 2200.0) * speed + (i * 0.137F)) % 1.0);
			int y;
			if (!frost && (tropical || forest || deepSeaMode || dark())) {
				// tropical / forest / deep-sea particles and the Dark theme's fireflies rise
				y = (int) (this.height - phase * this.height);
			} else {
				// snow / gold flecks fall
				y = (int) (phase * this.height);
			}
			this.drawRect(x, y, x + (int) size, y + (int) size, particleColor);
		}

		// ---- Treasure Client 1.5: the overlay draws the words; the game draws just your player ----
		if (!this.tcFallback()) {
			int cx = (int) (this.width * 0.80F);
			float scale = Math.max(24.0F, this.height * 0.18F);
			int feetY = (int) (this.height * 0.31F + scale * 2.0F);
			int sw = (int) (scale * 0.55F);
			for (int k = 0; k < 3; ++k) {
				int ins = k * sw / 4;
				Gui.drawRect(cx - sw + ins, feetY - 1 + k, cx + sw - ins, feetY + 1 + k, 0x22000000);
			}
			TCPlayerPreview.draw(cx, feetY, scale, tcYaw, mouseX, mouseY);
			super.drawScreen(mouseX, mouseY, partialTicks);
			return;
		}

		// ---- header, top left: chest logo + wordmark ----
		int lx = 10, ly = 6, lh = 30;
		this.drawLogo(lx + Math.round(lh * LOGO_ASPECT) / 2, ly, lh);
		int wx = lx + Math.round(lh * LOGO_ASPECT) + 6;
		brandLine(); // refresh brandTitle / customBrand from the overlay
		int wordRight;
		if (customBrand) {
			// The user renamed the client in the Mod Menu: show that title as the wordmark
			String ct = brandTitle;
			int cw = Math.max(1, this.fontRendererObj.getStringWidth(ct));
			float sc = Math.min(2.0F, Math.max(1.0F, (this.width - wx - 60.0F) / (float) cw));
			GlStateManager.pushMatrix();
			GlStateManager.translate((float) wx, 15.0F, 0.0F);
			GlStateManager.scale(sc, sc, sc);
			this.drawString(this.fontRendererObj, ct, 1, 1, 0xff000000);
			this.drawString(this.fontRendererObj, ct, 0, 0, cAccent());
			GlStateManager.popMatrix();
			wordRight = wx + (int) (cw * sc);
		} else {
			String w1 = TextFormatting.BOLD + "TREASURE";
			GlStateManager.pushMatrix();
			GlStateManager.translate((float) wx, 9.0F, 0.0F);
			GlStateManager.scale(2.0F, 2.0F, 2.0F);
			this.drawString(this.fontRendererObj, w1, 1, 1, 0xff000000);
			this.drawString(this.fontRendererObj, w1, 0, 0, cAccent());
			GlStateManager.popMatrix();
			wordRight = wx + this.fontRendererObj.getStringWidth(w1) * 2;
			this.drawString(this.fontRendererObj, "C L I E N T", wx + 1, 28, cAccentDim());
		}

		// splash: tilted and pulsing at the end of the wordmark, like the vanilla one
		GlStateManager.pushMatrix();
		GlStateManager.translate((float) (wordRight + 6), 26.0F, 0.0F);
		GlStateManager.rotate(-12.0F, 0.0F, 0.0F, 1.0F);
		float f = 0.9F - 0.08F * MathHelper
				.abs(MathHelper.sin((float) (t % 1000L) / 1000.0F * ((float) Math.PI * 2F)));
		GlStateManager.scale(f, f, f);
		if (wordRight + 6 + this.fontRendererObj.getStringWidth(this.splashText) * f < this.width - 34) {
			this.drawString(this.fontRendererObj, this.splashText, 0, 0, cAccent());
		}
		GlStateManager.popMatrix();

		// ---- the glass panel behind the buttons ----
		int acc = cAccent();
		TreasureButtons.roundedRect(this.tcPanelX, this.tcPanelY, this.tcPanelW, this.tcPanelH,
				(TreasureButtons.panelBg() & 0x00FFFFFF) | 0xB0000000);
		TreasureButtons.roundedOutline(this.tcPanelX, this.tcPanelY, this.tcPanelW, this.tcPanelH,
				(acc & 0x00FFFFFF) | 0x66000000);

		// ---- your player on the right ----
		if (this.tcShowPlayer()) {
			int left = this.tcPlayerLeft();
			int cx = (left + this.width) / 2;
			int feetY = this.height - 30;
			float scale = Math.min(80.0F, Math.max(30.0F, (feetY - 56) / 2.2F));
			// soft shadow under the feet
			int sw = (int) (scale * 0.55F);
			for (int k = 0; k < 3; ++k) {
				int ins = k * sw / 4;
				Gui.drawRect(cx - sw + ins, feetY - 1 + k, cx + sw - ins, feetY + 1 + k, 0x22000000);
			}
			TCPlayerPreview.draw(cx, feetY, scale, tcYaw, mouseX, mouseY);
			// your name above the head
			String pn = this.mc.getSession().getProfile().getName();
			int nw = this.fontRendererObj.getStringWidth(pn);
			int ny = (int) (feetY - scale * 2.0F) - 14;
			Gui.drawRect(cx - nw / 2 - 3, ny - 2, cx + nw / 2 + 3, ny + 9, 0x66000000);
			this.drawString(this.fontRendererObj, pn, cx - nw / 2, ny, 0xFFFFFFFF);
			if (!this.tcDragging && mouseX >= left && mouseY > ny + 10 && mouseY < feetY + 4) {
				String hint = "Drag to spin";
				this.drawString(this.fontRendererObj, hint, cx - this.fontRendererObj.getStringWidth(hint) / 2,
						feetY + 6, cAccentDim());
			}
		}

		// ---- bottom corners: version (left), What's new (right) ----
		this.drawString(this.fontRendererObj, brandLine(), 2, this.height - 20, cAccentDim());
		this.drawString(this.fontRendererObj, "Eaglercraft 1.12.2", 2, this.height - 10, cAccentDim());
		int ww = this.tcWhatsNewW();
		boolean overNew = mouseX >= this.width - ww - 4 && mouseY >= this.height - 12;
		this.drawString(this.fontRendererObj, (overNew ? TextFormatting.UNDERLINE : "") + "What's new",
				this.width - ww - 2, this.height - 10, overNew ? 0xFFFFFFFF : cAccent());

		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	/**
	 * Called when the mouse is clicked. Args : mouseX, mouseY, clickedButton
	 */
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		super.mouseClicked(mouseX, mouseY, mouseButton);

		// Treasure Client 1.5: with the overlay menu, press on the player to spin it
		if (!this.tcFallback()) {
			int cx = (int) (this.width * 0.80F);
			if (mouseButton == 0 && Math.abs(mouseX - cx) < this.width * 0.12F && mouseY > this.height * 0.24F
					&& mouseY < this.height * 0.68F) {
				this.tcDragging = true;
				this.tcDragLastX = mouseX;
			}
			return;
		}
		// Treasure Client: "What's new" opens the changelog on the website
		if (mouseButton == 0 && mouseX >= this.width - this.tcWhatsNewW() - 4 && mouseY >= this.height - 12) {
			EagRuntime.openLink(TC_CHANGELOG_URL);
			return;
		}
		// Treasure Client: press on the player (right side) to start spinning it
		if (mouseButton == 0 && this.tcShowPlayer() && mouseX >= this.tcPlayerLeft() && mouseY > 40
				&& mouseY < this.height - 14) {
			this.tcDragging = true;
			this.tcDragLastX = mouseX;
		}
	}

	protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
		super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
		if (this.tcDragging) {
			tcYaw += (mouseX - this.tcDragLastX) * 1.6F;
			tcYaw = MathHelper.wrapDegrees(tcYaw);
			this.tcDragLastX = mouseX;
		}
	}

	protected void mouseReleased(int mouseX, int mouseY, int state) {
		super.mouseReleased(mouseX, mouseY, state);
		this.tcDragging = false;
	}

	/**
	 * Called when the screen is unloaded. Used to disable keyboard repeat events
	 */
	public void onGuiClosed() {
		// Treasure Client 1.5: the overlay hides the menu words
		try {
			EagRuntime.setStorage("tcmenu", null);
		} catch (Throwable t) {
		}
	}
}
