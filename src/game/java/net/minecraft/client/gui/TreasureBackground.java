package net.minecraft.client.gui;

import net.lax1dude.eaglercraft.opengl.EaglercraftGPU;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.lax1dude.eaglercraft.opengl.ImageData;
import net.lax1dude.eaglercraft.opengl.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

/**
 * Treasure Client — shared themed menu backdrop.
 *
 * One reusable implementation of the main-menu look (the active theme's
 * background image, blurred and slowly panning, plus the theme's animated
 * particle field) so every out-of-game screen can share it instead of the
 * default dirt tile. It reads the active theme from {@link TreasureTheme}, so
 * changing the theme updates every screen automatically.
 *
 * Fully self-contained: it binds its own texture via {@link Minecraft} and draws
 * with the tessellator + {@link Gui#drawRect}, so it can be called from any
 * package (the native menus in {@code net.minecraft.client.gui} and the
 * Eaglercraft profile/skin screens alike). Call {@link #draw(int,int,float)}
 * at the very start of a screen's {@code drawScreen} in place of
 * {@code drawDefaultBackground()}.
 */
public final class TreasureBackground {

	private TreasureBackground() {
	}

	private static final ResourceLocation TREASURE_BG = new ResourceLocation("textures/gui/title/treasure_bg.png");
	private static final ResourceLocation FROST_BG = new ResourceLocation("textures/gui/title/frost_bg.png");
	private static final ResourceLocation TROPICAL_BG = new ResourceLocation("textures/gui/title/tropical_bg.png");
	private static final ResourceLocation TERRACOTTA_BG = new ResourceLocation("textures/gui/title/terracotta_bg.png");
	private static final ResourceLocation FOREST_BG = new ResourceLocation("textures/gui/title/forest_bg.png");

	/** The theme's accent color (ARGB), matching the main menu's accent. */
	public static int accent() {
		if (TreasureTheme.isFrost()) {
			return 0xFF8FD3FF;
		}
		if (TreasureTheme.isTropical()) {
			return 0xFF45E3C6;
		}
		if (TreasureTheme.isTerracotta()) {
			return 0xFFE8975A;
		}
		if (TreasureTheme.isForest()) {
			return 0xFFAEE86A;
		}
		return 0xFFF2C64B; // normal / treasure gold
	}

	/**
	 * Draw a polished themed panel: a dark translucent fill with a 1px accent
	 * border, for grouping a screen's controls on top of {@link #draw}.
	 */
	public static void panel(int left, int top, int right, int bottom) {
		Gui.drawRect(left - 1, top - 1, right + 1, bottom + 1, accent());
		Gui.drawRect(left, top, right, bottom, 0xC8101018);
	}

	/** The background image for the currently selected theme. */
	public static ResourceLocation activeBg() {
		if (TreasureTheme.isFrost()) {
			return FROST_BG;
		}
		if (TreasureTheme.isTropical()) {
			return TROPICAL_BG;
		}
		if (TreasureTheme.isTerracotta()) {
			return TERRACOTTA_BG;
		}
		if (TreasureTheme.isForest()) {
			return FOREST_BG;
		}
		return TREASURE_BG;
	}

	/**
	 * Draw the full themed backdrop: blurred + panning theme image, a subtle
	 * darken for text legibility, then the animated particle field. Safe to call
	 * once per frame at the top of a screen's drawScreen.
	 */
	public static void draw(int width, int height, float zLevel) {
		try {
			drawBgTexture(activeBg(), width, height, zLevel);
			// subtle darken (stronger toward the bottom) so widgets/text stay readable
			drawVGradient(0, 0, width, height, 0x14000000, 0x5c000000);
			drawParticles(width, height);
		} catch (Throwable t) {
			// never let the backdrop break a screen — fall back to nothing drawn
		}
	}

	// ---- blurred + panning theme image (shared with GuiMainMenu) --------------
	/** Theme image -> its blurred copy (made once, then reused every frame). */
	private static final java.util.Map<ResourceLocation, ResourceLocation> BLURRED = new java.util.HashMap<>();

	/**
	 * Draws a theme image over the whole screen, blurred and slowly panning.
	 * The blur is done once on the image when it's first used (one texture
	 * upload), so each frame is a single full-screen draw. It used to be drawn
	 * 17 times per frame to fake the blur, which made menus very slow on weak GPUs.
	 */
	public static void drawBlurredBg(ResourceLocation tex, int width, int height, float zLevel) {
		Minecraft mc = Minecraft.getMinecraft();
		GlStateManager.disableDepth();
		GlStateManager.depthMask(false);
		GlStateManager.enableTexture2D();
		GlStateManager.disableBlend();
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		mc.getTextureManager().bindTexture(blurred(tex));
		// linear filtering so the stretched image isn't pixelated (GL_TEXTURE_2D, MIN/MAG = GL_LINEAR)
		EaglercraftGPU.glTexParameteri(3553, 10241, 9729);
		EaglercraftGPU.glTexParameteri(3553, 10240, 9729);

		// slow horizontal pan with a slight zoom, so an edge never shows
		float t = (float) Minecraft.getSystemTime();
		float zoom = 0.82F;
		float margin = 1.0F - zoom;
		float panX = (MathHelper.sin(t / 9000.0F) * 0.5F + 0.5F) * margin;
		float panY = margin * 0.5F;
		blit(panX, panY, panX + zoom, panY + zoom, 0.0F, 0.0F, 255, width, height, zLevel);

		GlStateManager.depthMask(true);
		GlStateManager.enableDepth();
	}

	private static void drawBgTexture(ResourceLocation tex, int width, int height, float zLevel) {
		drawBlurredBg(tex, width, height, zLevel);
	}

	/** The blurred copy of a theme image, made the first time it's needed (the sharp one if that fails). */
	private static ResourceLocation blurred(ResourceLocation tex) {
		ResourceLocation done = BLURRED.get(tex);
		if (done != null) {
			return done;
		}
		ResourceLocation result = new ResourceLocation("treasure", "bg_blur/" + tex.getResourcePath());
		if (!Minecraft.getMinecraft().getTextureManager().loadTexture(result, new BlurredTexture(tex))) {
			result = tex;
		}
		BLURRED.put(tex, result);
		return result;
	}

	/**
	 * A theme image blurred and uploaded straight to the GPU. No copy of the
	 * pixels is kept in memory; after a resource reload it's made again from the
	 * image file.
	 */
	private static final class BlurredTexture extends net.minecraft.client.renderer.texture.AbstractTexture {
		private final ResourceLocation source;

		BlurredTexture(ResourceLocation source) {
			this.source = source;
		}

		public void loadTexture(net.minecraft.client.resources.IResourceManager resourceManager) throws java.io.IOException {
			this.deleteGlTexture();
			ImageData img;
			try (java.io.InputStream in = resourceManager.getResource(this.source).getInputStream()) {
				img = TextureUtil.readBufferedImage(in);
			}
			if (img == null) {
				throw new java.io.IOException("Could not read " + this.source);
			}
			int[] px = img.pixels.clone();
			// three box blurs in a row look like a soft gaussian blur
			int radius = Math.max(2, img.width / 170);
			for (int pass = 0; pass < 3; ++pass) {
				boxBlur(px, img.width, img.height, radius, true);
				boxBlur(px, img.width, img.height, radius, false);
			}
			TextureUtil.uploadTextureImageAllocate(this.getGlTextureId(), new ImageData(img.width, img.height, px, true),
					false, false);
		}
	}

	/** One box-blur pass along rows (horizontal) or columns, edges clamped, every channel. */
	private static void boxBlur(int[] px, int w, int h, int r, boolean horizontal) {
		int len = horizontal ? w : h, lines = horizontal ? h : w;
		int[] line = new int[len];
		int div = r * 2 + 1;
		for (int l = 0; l < lines; ++l) {
			for (int i = 0; i < len; ++i) {
				line[i] = px[horizontal ? l * w + i : i * w + l];
			}
			int sa = 0, sr = 0, sg = 0, sb = 0;
			for (int k = -r; k <= r; ++k) {
				int c = line[k < 0 ? 0 : (k >= len ? len - 1 : k)];
				sa += c >>> 24;
				sr += c >> 16 & 255;
				sg += c >> 8 & 255;
				sb += c & 255;
			}
			for (int i = 0; i < len; ++i) {
				px[horizontal ? l * w + i : i * w + l] = (sa / div) << 24 | (sr / div) << 16 | (sg / div) << 8 | sb / div;
				int out = line[i - r < 0 ? 0 : i - r];
				int in = line[i + r + 1 >= len ? len - 1 : i + r + 1];
				sa += (in >>> 24) - (out >>> 24);
				sr += (in >> 16 & 255) - (out >> 16 & 255);
				sg += (in >> 8 & 255) - (out >> 8 & 255);
				sb += (in & 255) - (out & 255);
			}
		}
	}

	private static void blit(float u0, float v0, float u1, float v1, float ox, float oy, int a,
			int width, int height, float zLevel) {
		Tessellator tessellator = Tessellator.getInstance();
		WorldRenderer bufferbuilder = tessellator.getBuffer();
		bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
		double z = (double) zLevel;
		double x0 = ox, y0 = oy, x1 = width + ox, y1 = height + oy;
		bufferbuilder.pos(x0, y1, z).tex((double) u0, (double) v1).color(255, 255, 255, a).endVertex();
		bufferbuilder.pos(x1, y1, z).tex((double) u1, (double) v1).color(255, 255, 255, a).endVertex();
		bufferbuilder.pos(x1, y0, z).tex((double) u1, (double) v0).color(255, 255, 255, a).endVertex();
		bufferbuilder.pos(x0, y0, z).tex((double) u0, (double) v0).color(255, 255, 255, a).endVertex();
		tessellator.draw();
	}

	// ---- animated particle field (matches GuiMainMenu, theme-driven) ---------
	private static void drawParticles(int width, int height) {
		boolean frost = TreasureTheme.isFrost();
		boolean tropical = TreasureTheme.isTropical();
		boolean terracotta = TreasureTheme.isTerracotta();
		boolean forest = TreasureTheme.isForest();

		int particleColor = frost ? 0x99EAF6FF
				: tropical ? 0x9945E3C6
				: terracotta ? 0x99E89A5A
				: forest ? 0x99C8E88A
				: 0x99F2C64B; // normal / treasure gold
		long tt = Minecraft.getSystemTime();
		float gust = frost
				? (MathHelper.sin((float) (tt / 7000.0)) * 0.5F + MathHelper.sin((float) (tt / 2300.0)) * 0.5F) * 34.0F
				: 0.0F;
		int count = frost ? 50 : 30;
		for (int i = 0; i < count; i++) {
			float sx = ((i * 73 + 11) % 100) / 100.0F;
			float speed = 0.25F + (((i * 37 + 7) % 100) / 100.0F) * 0.55F;
			float size = frost ? (1.0F + ((i * 29 + 5) % 2)) : (1.0F + ((i * 53 + 3) % 2));
			float sway = MathHelper.sin((float) ((tt / 1600.0) + i)) * (frost ? 7.0F : 12.0F);
			float lane = 0.4F + ((i * 17 + 9) % 100) / 100.0F;
			int x = (int) (sx * width + sway + (frost ? gust * lane : 0.0F));
			if (frost) {
				int w = width + 32;
				x = (((x + 16) % w) + w) % w - 16;
			}
			float phase = (float) (((tt / 2200.0) * speed + (i * 0.137F)) % 1.0);
			int y;
			if (!frost && (tropical || forest)) {
				y = (int) (height - phase * height);
			} else {
				y = (int) (phase * height);
			}
			Gui.drawRect(x, y, x + (int) size, y + (int) size, particleColor);
		}
	}

	// ---- vertical gradient fill (self-contained; no GuiScreen needed) --------
	private static void drawVGradient(int left, int top, int right, int bottom, int colorTop, int colorBottom) {
		float aT = (colorTop >>> 24) / 255.0F, rT = (colorTop >> 16 & 255) / 255.0F,
				gT = (colorTop >> 8 & 255) / 255.0F, bT = (colorTop & 255) / 255.0F;
		float aB = (colorBottom >>> 24) / 255.0F, rB = (colorBottom >> 16 & 255) / 255.0F,
				gB = (colorBottom >> 8 & 255) / 255.0F, bB = (colorBottom & 255) / 255.0F;
		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.disableAlpha();
		GlStateManager.blendFunc(770, 771);
		GlStateManager.shadeModel(7425); // GL_SMOOTH
		Tessellator tessellator = Tessellator.getInstance();
		WorldRenderer bufferbuilder = tessellator.getBuffer();
		bufferbuilder.begin(7, DefaultVertexFormats.POSITION_COLOR);
		bufferbuilder.pos((double) right, (double) top, 0.0D).color(rT, gT, bT, aT).endVertex();
		bufferbuilder.pos((double) left, (double) top, 0.0D).color(rT, gT, bT, aT).endVertex();
		bufferbuilder.pos((double) left, (double) bottom, 0.0D).color(rB, gB, bB, aB).endVertex();
		bufferbuilder.pos((double) right, (double) bottom, 0.0D).color(rB, gB, bB, aB).endVertex();
		tessellator.draw();
		GlStateManager.shadeModel(7424); // GL_FLAT
		GlStateManager.disableBlend();
		GlStateManager.enableAlpha();
		GlStateManager.enableTexture2D();
	}
}
