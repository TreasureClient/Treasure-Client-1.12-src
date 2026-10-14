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
	private static String brandTitle = "Treasure Client 1.2";
	private static long brandRead = 0L;

	private static String brandLine() {
		try {
			long now = EagRuntime.steadyTimeMillis();
			if (now - brandRead > 500L) {
				brandRead = now;
				byte[] b = EagRuntime.getStorage("tctitle");
				if (b != null && b.length > 0) {
					String s = new String(b, StandardCharsets.UTF_8).trim();
					brandTitle = s.isEmpty() ? "Treasure Client 1.2" : s;
				} else {
					brandTitle = "Treasure Client 1.2";
				}
			}
		} catch (Throwable t) {
			brandTitle = "Treasure Client 1.2";
		}
		return brandTitle;
	}

	// palette helpers, per theme/mode. Frost (selected in the Mod Menu Themes tab)
	// uses the icy blue/cyan accent so the whole menu matches the theme.
	private boolean frost()      { return TreasureTheme.isFrost(); }
	private boolean tropical()   { return TreasureTheme.isTropical(); }
	private boolean terracotta() { return TreasureTheme.isTerracotta(); }
	private boolean forest()     { return TreasureTheme.isForest(); }
	private int cBgTop()    { return deepSeaMode ? 0xff0a3350 : 0xff2a1b0d; }
	private int cBgBottom() { return deepSeaMode ? 0xff030f18 : 0xff0f0a05; }
	// Per-theme accents: Frost=icy blue, Tropical=turquoise+sand, Terracotta=burnt
	// orange, Forest=green, else the default gold.
	private int cAccent() {
		if (frost()) return 0xff7fe9ff;
		if (tropical()) return 0xff45e3c6;
		if (terracotta()) return 0xffdb7a3c;
		if (forest()) return 0xff6fcb5b;
		return 0xffffd76b;
	}
	private int cAccentDim() {
		if (frost()) return 0xff8fb9cc;
		if (tropical()) return 0xffe0cd97;
		if (terracotta()) return 0xffe8b98a;
		if (forest()) return 0xffbfe0a0;
		return 0xffc9b183;
	}
	private int cParticle() {
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
	// Treasure Client: main-menu background images (packed into assets.epk from
	// desktopRuntime/resources/assets/minecraft/textures/gui/title/*.png).
	private static final ResourceLocation TREASURE_BG = new ResourceLocation("textures/gui/title/treasure_bg.png");
	private static final ResourceLocation FROST_BG = new ResourceLocation("textures/gui/title/frost_bg.png");
	private static final ResourceLocation TROPICAL_BG = new ResourceLocation("textures/gui/title/tropical_bg.png");
	private static final ResourceLocation TERRACOTTA_BG = new ResourceLocation("textures/gui/title/terracotta_bg.png");
	private static final ResourceLocation FOREST_BG = new ResourceLocation("textures/gui/title/forest_bg.png");

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
		// Treasure Client: Deep Sea mode removed — nothing to poll here anymore.
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

		int i = 24;
		int j = this.height / 4 + 48;

		if (this.mc.isDemo()) {
			this.addDemoButtons(j, 24);
		} else {
			this.addSingleplayerMultiplayerButtons(j, 24);
		}

		this.buttonList.add(new GuiButton(0, this.width / 2 - 100, j + 72 + 12, 98, 20, I18n.format("menu.options")));
		this.buttonList.add(new GuiButton(4, this.width / 2 + 2, j + 72 + 12, 98, 20, I18n.format("Edit Profile")));
		this.buttonList.add(new GuiButtonLanguage(5, this.width / 2 - 124, j + 72 + 12));
		// Treasure Client: always-available Mod Menu opener (so an unbound hotkey
		// can never lock you out of the Mod Menu). Deep Sea mode was removed, so this
		// now sits centered on its own row.
		this.buttonList.add(new GuiButton(101, this.width / 2 - 100, j + 72 + 36, 200, 20, "Mod Menu"));
	}

	/**
	 * Adds Singleplayer and Multiplayer buttons on Main Menu for players who have
	 * bought the game.
	 */
	private void addSingleplayerMultiplayerButtons(int p_73969_1_, int p_73969_2_) {
		this.buttonList.add(new GuiButton(1, this.width / 2 - 100, p_73969_1_, I18n.format("menu.singleplayer")));
		this.buttonList.add(
				new GuiButton(2, this.width / 2 - 100, p_73969_1_ + p_73969_2_ * 1, I18n.format("menu.multiplayer")));
		this.buttonList
				.add(new GuiButton(14, this.width / 2 - 100, p_73969_1_ + p_73969_2_ * 2, I18n.format("menu.credits")));
	}

	/**
	 * Adds Demo buttons on Main Menu for players who are playing Demo.
	 */
	private void addDemoButtons(int p_73972_1_, int p_73972_2_) {
		this.buttonList.add(new GuiButton(11, this.width / 2 - 100, p_73972_1_, I18n.format("menu.playdemo")));
		this.buttonResetDemo = this.addButton(
				new GuiButton(12, this.width / 2 - 100, p_73972_1_ + p_73972_2_ * 1, I18n.format("menu.resetdemo")));
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
		GlStateManager.disableDepth();
		GlStateManager.depthMask(false);
		GlStateManager.enableTexture2D();
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		this.mc.getTextureManager().bindTexture(tex);
		// Linear filtering so the stretched/blurred photo isn't nearest-neighbor
		// pixelated (3553=GL_TEXTURE_2D, 10241/10240=MIN/MAG, 9729=GL_LINEAR).
		EaglercraftGPU.glTexParameteri(3553, 10241, 9729);
		EaglercraftGPU.glTexParameteri(3553, 10240, 9729);

		// Slow horizontal pan (eases left <-> right) with a slight zoom, so the
		// sampled sub-rect always drifts within the image and never shows an edge.
		float t = (float) Minecraft.getSystemTime();
		float zoom = 0.82F;
		float margin = 1.0F - zoom;
		float panX = (MathHelper.sin(t / 9000.0F) * 0.5F + 0.5F) * margin; // 0..margin
		float panY = margin * 0.5F; // vertically centered
		float u0 = panX, u1 = panX + zoom;
		float v0 = panY, v1 = panY + zoom;

		// Cheap multi-tap "blur": an opaque base plus 8 low-alpha copies at small
		// screen-space offsets, so the background reads as soft/blurred — no shaders.
		GlStateManager.enableBlend();
		GlStateManager.blendFunc(770, 771); // GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA
		float r = Math.max(2.0F, this.width / 300.0F);
		blitBg(u0, v0, u1, v1, 0.0F, 0.0F, 255);
		float[][] ring = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 },
				{ 1, 1 }, { -1, -1 }, { 1, -1 }, { -1, 1 } };
		for (int i = 0; i < ring.length; i++) {
			blitBg(u0, v0, u1, v1, ring[i][0] * r, ring[i][1] * r, 30);
		}
		GlStateManager.disableBlend();

		GlStateManager.depthMask(true);
		GlStateManager.enableDepth();
	}

	/** Draw the full-screen background quad with a tex sub-rect, pixel offset and alpha. */
	private void blitBg(float u0, float v0, float u1, float v1, float ox, float oy, int a) {
		Tessellator tessellator = Tessellator.getInstance();
		WorldRenderer bufferbuilder = tessellator.getBuffer();
		bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
		double z = (double) this.zLevel;
		double x0 = ox, y0 = oy, x1 = this.width + ox, y1 = this.height + oy;
		bufferbuilder.pos(x0, y1, z).tex((double) u0, (double) v1).color(255, 255, 255, a).endVertex();
		bufferbuilder.pos(x1, y1, z).tex((double) u1, (double) v1).color(255, 255, 255, a).endVertex();
		bufferbuilder.pos(x1, y0, z).tex((double) u1, (double) v0).color(255, 255, 255, a).endVertex();
		bufferbuilder.pos(x0, y0, z).tex((double) u0, (double) v0).color(255, 255, 255, a).endVertex();
		tessellator.draw();
	}

	/**
	 * Draws the screen and all the components in it.
	 */
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.panoramaTimer += partialTicks;

		boolean frost = this.frost();
		boolean tropical = this.tropical();
		boolean terracotta = this.terracotta();
		boolean forest = this.forest();

		// ---- background ----
		// Each theme uses a full-screen image background.
		ResourceLocation bg;
		if (frost) bg = FROST_BG;
		else if (tropical) bg = TROPICAL_BG;
		else if (terracotta) bg = TERRACOTTA_BG;
		else if (forest) bg = FOREST_BG;
		else bg = TREASURE_BG;
		this.drawBgTexture(bg);
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
			if (!frost && (tropical || forest || deepSeaMode)) {
				// tropical / forest / deep-sea particles rise
				y = (int) (this.height - phase * this.height);
			} else {
				// snow / gold flecks fall
				y = (int) (phase * this.height);
			}
			this.drawRect(x, y, x + (int) size, y + (int) size, particleColor);
		}

		int j = this.width / 2;

		// ---- wordmark: big, layered shadow + themed accent + subtle pulse ----
		GlStateManager.pushMatrix();
		GlStateManager.translate((float) j, 40.0F, 0.0F);
		GlStateManager.scale(2.5F, 2.5F, 2.5F);
		// drop shadow
		this.drawCenteredString(this.fontRendererObj, TextFormatting.BOLD + "TREASURE", 1, -3, 0xff000000);
		// main
		this.drawCenteredString(this.fontRendererObj, TextFormatting.BOLD + "TREASURE", 0, -4, cAccent());
		GlStateManager.popMatrix();

		GlStateManager.pushMatrix();
		GlStateManager.translate((float) j, 66.0F, 0.0F);
		GlStateManager.scale(1.4F, 1.4F, 1.4F);
		this.drawCenteredString(this.fontRendererObj, "C L I E N T", 1, 1, 0xff000000);
		this.drawCenteredString(this.fontRendererObj, "C L I E N T", 0, 0, cAccentDim());
		GlStateManager.popMatrix();

		// splash with gentle pulse
		GlStateManager.pushMatrix();
		GlStateManager.translate((float) j, 90.0F, 0.0F);
		float f = 1.0F - 0.08F * MathHelper
				.abs(MathHelper.sin((float) (t % 1000L) / 1000.0F * ((float) Math.PI * 2F)));
		GlStateManager.scale(f, f, f);
		this.drawCenteredString(this.fontRendererObj, this.splashText, 0, 0, cAccent());
		GlStateManager.popMatrix();

		this.drawString(this.fontRendererObj, brandLine(), 2, this.height - 20, cAccentDim());
		this.drawString(this.fontRendererObj, "Eaglercraft 1.12.2",
				2, this.height - 10, cAccentDim());

		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	/**
	 * Called when the mouse is clicked. Args : mouseX, mouseY, clickedButton
	 */
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		super.mouseClicked(mouseX, mouseY, mouseButton);

		if (mouseX > this.field_193979_N && mouseX < this.field_193979_N + this.field_193978_M
				&& mouseY > this.height - 10 && mouseY < this.height) {
			this.mc.displayGuiScreen(new GuiWinGame(false, null));
		}

	}

	/**
	 * Called when the screen is unloaded. Used to disable keyboard repeat events
	 */
	public void onGuiClosed() {
	}
}
