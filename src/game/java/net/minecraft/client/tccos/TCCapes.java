package net.minecraft.client.tccos;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.client.TCLagMonitor;
import net.minecraft.client.gui.TreasureButtons;
import net.minecraft.client.renderer.texture.TextureUtil;

/**
 * Treasure Client cosmetics: the animated cape textures.
 *
 * A cape is drawn a few pictures per frame (about 3 ms) the first time it's needed (until then the normal
 * cape shows), uploaded once, and then animated only by moving the texture matrix to the
 * current frame. The textures are kept outside the game's texture manager, so a resource
 * pack reload doesn't throw them away.
 */
public final class TCCapes {

	private TCCapes() {
	}

	private static final class Entry {
		TCCapeArt.Builder builder;
		int glId = -1;
		long lastUsed;
	}

	private static final Map<String, Entry> CACHE = new HashMap<>();
	private static final int MAX_KEPT = 6;
	/** Time a frame may spend drawing cape pictures (at least one picture, then stop once this is used). */
	private static final long BUDGET_NS = 3_000_000L;
	/** Only one cape is drawn per frame, even if several players need a new one. */
	private static long lastStepFrame = -1L;

	/**
	 * Binds the cape's texture. Returns false while it's still being drawn (or if it's unknown):
	 * the caller then shows the normal cape (or none).
	 */
	public static boolean bind(String id) {
		if (id == null || !TCCapeArt.known(id)) return false;
		int accent = TreasureButtons.accent();
		String key = "theme".equals(id) ? "theme:" + Integer.toHexString(accent) : id;
		Entry e = CACHE.get(key);
		long now = EagRuntime.steadyTimeMillis();
		if (e == null) {
			if ("theme".equals(id)) forgetOldThemes();
			trim();
			e = new Entry();
			try {
				e.builder = new TCCapeArt.Builder(id, accent);
			} catch (Throwable t) {
				return false;
			}
			CACHE.put(key, e);
		}
		e.lastUsed = now;
		if (e.glId == -1) {
			long frame = TCLagMonitor.frameStartNs;
			if (frame == lastStepFrame) return false;
			lastStepFrame = frame;
			try {
				long start = EagRuntime.nanoTime();
				boolean done;
				do {
					done = e.builder.step(1);
				} while (!done && EagRuntime.nanoTime() - start < BUDGET_NS);
				if (!done) return false;
				// the game's textures take colours as red, green, blue, alpha bytes: swap red and blue
				int[] px = e.builder.pixels;
				for (int i = 0; i < px.length; ++i) {
					int c = px[i];
					px[i] = (c & 0xFF00FF00) | ((c >>> 16) & 0xFF) | ((c & 0xFF) << 16);
				}
				int id2 = GlStateManager.generateTexture();
				TextureUtil.allocateTexture(id2, TCCapeArt.TEX_W, TCCapeArt.TEX_H);
				TextureUtil.uploadTexture(id2, e.builder.pixels, TCCapeArt.TEX_W, TCCapeArt.TEX_H);
				e.glId = id2;
				e.builder = null;
			} catch (Throwable t) {
				CACHE.remove(key);
				return false;
			}
		}
		GlStateManager.bindTexture(e.glId);
		return true;
	}

	/** Points the texture at the current animation frame (call after bind, before drawing the cape). */
	public static void pushFrame() {
		long t = EagRuntime.steadyTimeMillis();
		int f = (int) ((t % TCCapeArt.LOOP_MS) * TCCapeArt.FRAMES / TCCapeArt.LOOP_MS);
		int col = f % TCCapeArt.COLS, row = f / TCCapeArt.COLS;
		GlStateManager.matrixMode(5890);
		GlStateManager.loadIdentity();
		GlStateManager.translate(col * TCCapeArt.CELL_W / (float) TCCapeArt.TEX_W,
				row * TCCapeArt.CELL_H / (float) TCCapeArt.TEX_H, 0.0F);
		// the cape model's texture coordinates are for a 32x32 texture
		GlStateManager.scale(32.0F * TCCapeArt.S / TCCapeArt.TEX_W, 32.0F * TCCapeArt.S / TCCapeArt.TEX_H, 1.0F);
		GlStateManager.matrixMode(5888);
	}

	public static void popFrame() {
		GlStateManager.matrixMode(5890);
		GlStateManager.loadIdentity();
		GlStateManager.matrixMode(5888);
	}

	/** Frees the least recently used cape when too many are kept. */
	private static void trim() {
		while (CACHE.size() >= MAX_KEPT) {
			String oldKey = null;
			long old = Long.MAX_VALUE;
			for (Map.Entry<String, Entry> me : CACHE.entrySet()) {
				if (me.getValue().lastUsed < old) {
					old = me.getValue().lastUsed;
					oldKey = me.getKey();
				}
			}
			if (oldKey == null) return;
			Entry e = CACHE.remove(oldKey);
			if (e != null && e.glId != -1) {
				try {
					GlStateManager.deleteTexture(e.glId);
				} catch (Throwable t) {
				}
			}
		}
	}

	/** Frees theme capes made for an older theme colour. */
	public static void forgetOldThemes() {
		String keep = "theme:" + Integer.toHexString(TreasureButtons.accent());
		Iterator<Map.Entry<String, Entry>> it = CACHE.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Entry> me = it.next();
			if (me.getKey().startsWith("theme:") && !me.getKey().equals(keep)) {
				if (me.getValue().glId != -1) {
					try {
						GlStateManager.deleteTexture(me.getValue().glId);
					} catch (Throwable t) {
					}
				}
				it.remove();
			}
		}
	}
}
