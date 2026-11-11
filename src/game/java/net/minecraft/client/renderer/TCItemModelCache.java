package net.minecraft.client.renderer;

import java.util.HashMap;
import java.util.List;

import net.lax1dude.eaglercraft.opengl.EaglercraftGPU;
import net.lax1dude.eaglercraft.opengl.RealOpenGLEnums;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

/**
 * Treasure Client: item model cache.
 *
 * Every frame, each item on screen (the one in your hand, the swords and blocks other
 * players hold, dropped items, and each enchantment glint pass, which draws the item
 * twice more) copied all of its model's quads into a fresh vertex buffer and sent it
 * to the graphics chip. A flat item like a sword is about 100 quads. The quads of an
 * item model never change, so they are now stored on the graphics chip once (one
 * display list per model and color) and drawn from there with one call.
 *
 * Items whose color comes from the item itself (potions, leather armor, spawn eggs)
 * are drawn the old way, since their color can change.
 */
public final class TCItemModelCache {

	private TCItemModelCache() {
	}

	private static final int MAX_ENTRIES = 768;

	private static final class Entry {
		final int color;
		final int list;
		final TextureAtlasSprite[] sprites;
		Entry next;
		long lastUse;

		Entry(int color, int list, TextureAtlasSprite[] sprites) {
			this.color = color;
			this.list = list;
			this.sprites = sprites;
		}
	}

	private static final HashMap<IBakedModel, Entry> lists = new HashMap<IBakedModel, Entry>();
	/** Models with quads that take their color from the item stack (never cached). */
	private static final HashMap<IBakedModel, Boolean> tinted = new HashMap<IBakedModel, Boolean>();
	private static int entries;
	private static long useClock;
	public static boolean enabled = true;

	/**
	 * Draws the model from its stored copy. Returns false when the caller must draw it the
	 * normal way (cache off, item-colored model, or a display list is being recorded).
	 */
	static boolean draw(RenderItem ri, IBakedModel model, int color, ItemStack stack) {
		if (!enabled || model == null || EaglercraftGPU.tcIsCompilingList()) {
			return false;
		}
		if (color == -1 && !stack.func_190926_b() && isTinted(model)) {
			return false;
		}
		Entry e = lists.get(model);
		for (Entry f = e; f != null; f = f.next) {
			if (f.color == color) {
				f.lastUse = ++useClock;
				use(f.sprites);
				EaglercraftGPU.glCallList(f.list);
				return true;
			}
		}
		if (entries >= MAX_ENTRIES) {
			// full: free the quarter used longest ago. Clearing everything made every item on
			// screen get rebuilt in the same frame (a lag spike), and the count of GPU objects
			// climbed all session long before dropping.
			evictOldest(MAX_ENTRIES / 4);
			e = lists.get(model);
		}
		int id = EaglercraftGPU.glGenLists();
		EaglercraftGPU.glNewList(id, RealOpenGLEnums.GL_COMPILE);
		try {
			ri.tcDrawModelDirect(model, color, stack);
		} finally {
			EaglercraftGPU.glEndList();
		}
		Entry n = new Entry(color, id, spritesOf(model));
		n.lastUse = ++useClock;
		n.next = e;
		lists.put(model, n);
		++entries;
		use(n.sprites);
		EaglercraftGPU.glCallList(id);
		return true;
	}

	private static void use(TextureAtlasSprite[] sprites) {
		for (int i = 0; i < sprites.length; ++i) {
			net.minecraft.client.renderer.texture.TCAnimTracker.use(sprites[i]);
		}
	}

	private static boolean isTinted(IBakedModel model) {
		Boolean b = tinted.get(model);
		if (b == null) {
			boolean t = false;
			for (int s = 0; s <= 6 && !t; ++s) {
				List<BakedQuad> q = model.getQuads(null, s < 6 ? EnumFacing._VALUES[s] : null, 0L);
				for (int i = 0, n = q.size(); i < n; ++i) {
					if (q.get(i).hasTintIndex()) {
						t = true;
						break;
					}
				}
			}
			b = Boolean.valueOf(t);
			if (tinted.size() > 4096) {
				tinted.clear();
			}
			tinted.put(model, b);
		}
		return b.booleanValue();
	}

	private static TextureAtlasSprite[] spritesOf(IBakedModel model) {
		java.util.ArrayList<TextureAtlasSprite> out = new java.util.ArrayList<TextureAtlasSprite>(2);
		for (int s = 0; s <= 6; ++s) {
			List<BakedQuad> q = model.getQuads(null, s < 6 ? EnumFacing._VALUES[s] : null, 0L);
			for (int i = 0, n = q.size(); i < n; ++i) {
				TextureAtlasSprite sp = q.get(i).getSprite();
				if (sp != null && !out.contains(sp)) {
					out.add(sp);
				}
			}
		}
		return out.toArray(new TextureAtlasSprite[out.size()]);
	}

	/** Frees the {@code count} stored models used longest ago. */
	private static void evictOldest(int count) {
		long[] uses = new long[entries];
		int k = 0;
		for (Entry e : lists.values()) {
			for (Entry f = e; f != null && k < uses.length; f = f.next) {
				uses[k++] = f.lastUse;
			}
		}
		java.util.Arrays.sort(uses, 0, k);
		long cutoff = uses[Math.min(count, k) - 1];
		java.util.Iterator<java.util.Map.Entry<IBakedModel, Entry>> it = lists.entrySet().iterator();
		while (it.hasNext()) {
			java.util.Map.Entry<IBakedModel, Entry> me = it.next();
			Entry head = null, tail = null;
			for (Entry f = me.getValue(); f != null;) {
				Entry nx = f.next;
				if (f.lastUse <= cutoff) {
					EaglercraftGPU.glDeleteLists(f.list);
					--entries;
				} else {
					f.next = null;
					if (head == null) head = f;
					else tail.next = f;
					tail = f;
				}
				f = nx;
			}
			if (head == null) {
				it.remove();
			} else {
				me.setValue(head);
			}
		}
	}

	/** Frees every stored model (resource reload). */
	public static void clear() {
		for (Entry e : lists.values()) {
			for (Entry f = e; f != null; f = f.next) {
				EaglercraftGPU.glDeleteLists(f.list);
			}
		}
		lists.clear();
		tinted.clear();
		entries = 0;
	}
}
