package net.minecraft.client.tccos;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.opengl.EaglercraftGPU;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.lax1dude.eaglercraft.opengl.TCModelBatch;
import net.lax1dude.eaglercraft.opengl.WorldRenderer;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

/**
 * Treasure Client cosmetics: draws hats and boots (their shapes are in {@link TCWearShapes}).
 *
 * They're built from little blocks, like Minecraft's own models, so they match the game's look
 * and are lit like the player. Each one is at most two draws: the solid parts, then the parts
 * that glow (gems, stars, flames), which ignore the dark. Nothing is kept between frames except
 * a short list of footprints for the boots' trails.
 *
 * Coordinates are model pixels with y pointing down, like the player model: the head is
 * x -4..4, y -8..0, z -4..4 (its top at y = -8, the face towards -z); a leg is x -2..2,
 * y 0..12, z -2..2 (the foot at y = 12).
 */
public final class TCWear {

	private TCWear() {
	}

	// ------------------------------------------------------------------ drawing a hat and boots on a model

	/**
	 * Draws the hat and boots on a model whose parts are already posed (call right after the model
	 * is drawn, with the same matrix). active = flying, jumping or sprinting (rocket flames etc.).
	 */
	public static void render(TCCosmetics.Look look, ModelBiped model, boolean hatOk, boolean bootsOk, boolean active) {
		if (look == null) return;
		boolean hat = hatOk && look.hasHat(), boots = bootsOk && look.hasBoots();
		if (!hat && !boots) return;
		TCModelBatch.flushIfPending();
		long ms = EagRuntime.steadyTimeMillis();
		float t = (ms % 3600000L) / 1000f;
		GlStateManager.enableTexture2D();
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		bindNoise();
		int hatRgb = hat ? look.wearRgb(look.hatColor, TCWearShapes.naturalHat(look.hat), ms) : 0;
		int bootRgb = boots ? look.wearRgb(look.bootColor, TCWearShapes.naturalBoots(look.boots), ms) : 0;
		boolean natural = "natural".equals(look.hatColor);
		for (int pass = 0; pass < 2; ++pass) {
			boolean glowPass = pass == 1;
			if (glowPass && !(hat && hatGlows(look.hat) || boots && bootsGlow(look.boots))) break;
			float lx = 0f, ly = 0f;
			if (glowPass) {
				lx = GlStateManager.getTexCoordX(1);
				ly = GlStateManager.getTexCoordY(1);
				GlStateManager.disableLighting();
				OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
			}
			if (hat && (!glowPass || hatGlows(look.hat))) {
				GlStateManager.pushMatrix();
				model.bipedHead.postRender(0.0625F);
				GlStateManager.scale(0.0625F, 0.0625F, 0.0625F);
				begin(glowPass);
				TCWearShapes.drawHat(look.hat, hatRgb, natural, t);
				Tessellator.getInstance().draw();
				GlStateManager.popMatrix();
			}
			if (boots && (!glowPass || bootsGlow(look.boots))) {
				for (int leg = 0; leg < 2; ++leg) {
					GlStateManager.pushMatrix();
					(leg == 0 ? model.bipedRightLeg : model.bipedLeftLeg).postRender(0.0625F);
					GlStateManager.scale(0.0625F, 0.0625F, 0.0625F);
					begin(glowPass);
					TCWearShapes.drawBoot(look.boots, bootRgb, t, leg == 0 ? -1 : 1, active);
					Tessellator.getInstance().draw();
					GlStateManager.popMatrix();
				}
			}
			if (glowPass) {
				OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
				GlStateManager.enableLighting();
			}
		}
		buf = null;
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
	}

	static boolean hatGlows(String id) {
		return TCWearShapes.hatGlows(id);
	}

	static boolean bootsGlow(String id) {
		return TCWearShapes.bootsGlow(id);
	}

	private static WorldRenderer buf;
	private static int noiseTex = -1;
	private static final TCWearShapes.Sink GL_SINK = new TCWearShapes.Sink() {
		public void vertex(float x, float y, float z, float u, float v, float r, float g, float b, float nx, float ny, float nz) {
			buf.pos(x, y, z).tex(u, v).color(r, g, b, 1.0F).normal(nx, ny, nz).endVertex();
		}
	};

	private static void begin(boolean glow) {
		buf = Tessellator.getInstance().getBuffer();
		buf.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
		TCWearShapes.begin(GL_SINK, glow);
	}

	/** Boots that leave something behind where you walk. */
	public static boolean bootsHaveTrail(String id) {
		return "rocket".equals(id) || "winged".equals(id) || "cloud".equals(id) || "frost".equals(id) || "magma".equals(id);
	}

	// ------------------------------------------------------------------ boot trails (drawn by TCAuras)

	static final int PRINT_FROST = 1, PRINT_MAGMA = 2, PUFF = 3, FLAME = 4, SPARK = 5;
	private static final int TRAIL_N = 48;

	/** The footprints and puffs a player has left behind (in world positions). */
	static final class Trail {
		final double[] x = new double[TRAIL_N], y = new double[TRAIL_N], z = new double[TRAIL_N];
		final long[] born = new long[TRAIL_N];
		final byte[] kind = new byte[TRAIL_N];
		int next, side = 1;
		float lastWalk;
		boolean lastGround = true;
		long lastEmit, lastSeen;

		void add(double px, double py, double pz, int k, long now) {
			x[next] = px; y[next] = py; z[next] = pz;
			born[next] = now;
			kind[next] = (byte) k;
			next = (next + 1) % TRAIL_N;
		}
	}

	private static final Map<Integer, Trail> TRAILS = new HashMap<>();

	/** Adds new footprints for this player and returns their trail. */
	static Trail trail(AbstractClientPlayer p, TCCosmetics.Look look) {
		long now = EagRuntime.steadyTimeMillis();
		Trail tr = TRAILS.get(p.getEntityId());
		if (tr == null) {
			if (TRAILS.size() > 40) {
				Iterator<Trail> it = TRAILS.values().iterator();
				while (it.hasNext()) {
					if (now - it.next().lastSeen > 5000L) it.remove();
				}
			}
			tr = new Trail();
			tr.lastWalk = p.distanceWalkedModified;
			TRAILS.put(p.getEntityId(), tr);
		}
		tr.lastSeen = now;
		boolean ground = p.onGround;
		float walk = p.distanceWalkedModified;
		if (walk < tr.lastWalk) tr.lastWalk = walk;
		double yaw = Math.toRadians(p.renderYawOffset);
		double px = Math.cos(yaw), pz = Math.sin(yaw); // to the side
		double fx = -Math.sin(yaw), fz = Math.cos(yaw); // forwards
		switch (look.boots) {
		case "frost":
		case "magma":
			if (ground && walk - tr.lastWalk >= 0.62f) {
				tr.lastWalk = walk;
				tr.side = -tr.side;
				tr.add(p.posX + px * 0.14 * tr.side, p.posY + 0.016, p.posZ + pz * 0.14 * tr.side,
						"frost".equals(look.boots) ? PRINT_FROST : PRINT_MAGMA, now);
			}
			break;
		case "cloud":
			if (ground != tr.lastGround) {
				for (int k = 0; k < 3; ++k) {
					double a = k * 2.1 + now * 0.001;
					tr.add(p.posX + Math.cos(a) * 0.22, p.posY + 0.08, p.posZ + Math.sin(a) * 0.22, PUFF, now - k * 60L);
				}
			}
			break;
		case "rocket":
			if ((!ground || p.isSprinting()) && now - tr.lastEmit >= 45L) {
				tr.lastEmit = now;
				for (int s = -1; s <= 1; s += 2) {
					tr.add(p.posX + px * 0.12 * s - fx * 0.14, p.posY - 0.02, p.posZ + pz * 0.12 * s - fz * 0.14, FLAME, now);
				}
			}
			break;
		case "winged":
			if (!ground && now - tr.lastEmit >= 70L) {
				tr.lastEmit = now;
				tr.side = -tr.side;
				tr.add(p.posX + px * 0.3 * tr.side - fx * 0.1, p.posY + 0.5, p.posZ + pz * 0.3 * tr.side - fz * 0.1, SPARK, now);
			}
			break;
		default:
			break;
		}
		tr.lastGround = ground;
		return tr;
	}


	/** A small grey-white texture with a gentle pixel pattern, so the blocks don't look flat. */
	private static void bindNoise() {
		if (noiseTex == -1) {
			int n = 16;
			int[] px = new int[n * n];
			for (int y = 0; y < n; ++y) {
				for (int x = 0; x < n; ++x) {
					int v = 222 + (int) (TCCapeArt.hash(x, y, 1234) * 33f);
					px[y * n + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
				}
			}
			noiseTex = GlStateManager.generateTexture();
			TextureUtil.allocateTexture(noiseTex, n, n);
			TextureUtil.uploadTexture(noiseTex, px, n, n);
			EaglercraftGPU.glTexParameteri(0x0DE1, 0x2801, 0x2600); // sharp pixels
			EaglercraftGPU.glTexParameteri(0x0DE1, 0x2800, 0x2600);
			EaglercraftGPU.glTexParameteri(0x0DE1, 0x2802, 0x2901); // repeat
			EaglercraftGPU.glTexParameteri(0x0DE1, 0x2803, 0x2901);
		}
		GlStateManager.bindTexture(noiseTex);
	}

}
