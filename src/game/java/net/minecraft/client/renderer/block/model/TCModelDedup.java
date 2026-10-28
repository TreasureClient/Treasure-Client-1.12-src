package net.minecraft.client.renderer.block.model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Treasure Client: FoamFix model deduplication, based on the mod by asie.
 * After models load, many baked faces are exact copies of each other (same
 * vertices, texture, side and tint). Each copy is swapped for one shared face,
 * and identical vertex arrays are shared too, so the duplicates can be freed.
 * Faces are only ever read after loading, so sharing them is safe.
 */
final class TCModelDedup {

	private TCModelDedup() {
	}

	/** A face's identity for deduplication: same class, vertices, tint, side and texture. */
	private static final class QuadKey {
		final BakedQuad quad;
		final int hash;

		QuadKey(BakedQuad q) {
			this.quad = q;
			int h = Arrays.hashCode(q.vertexData);
			h = 31 * h + q.tintIndex;
			h = 31 * h + (q.face == null ? 0 : q.face.ordinal());
			h = 31 * h + System.identityHashCode(q.sprite);
			this.hash = h;
		}

		public int hashCode() {
			return this.hash;
		}

		public boolean equals(Object o) {
			if (!(o instanceof QuadKey)) {
				return false;
			}
			BakedQuad a = this.quad, b = ((QuadKey) o).quad;
			return a.tintIndex == b.tintIndex && a.face == b.face && a.sprite == b.sprite
					&& Arrays.equals(a.vertexData, b.vertexData);
		}
	}

	private static Map<QuadKey, BakedQuad> quads;
	private static Map<IBakedModel, Boolean> seen;
	private static int replaced;

	static void dedupe(Iterable<IBakedModel> registry) {
		quads = new HashMap<>();
		seen = new IdentityHashMap<>();
		replaced = 0;
		try {
			for (IBakedModel m : registry) {
				visit(m);
			}
		} catch (Throwable t) {
			// leave whatever was already shared; models still work either way
		}
		System.out.println("[FoamFix] shared " + replaced + " duplicate model faces (" + quads.size() + " unique)");
		quads = null;
		seen = null;
	}

	private static void visit(IBakedModel m) {
		if (m == null || seen.put(m, Boolean.TRUE) != null) {
			return;
		}
		if (m instanceof SimpleBakedModel) {
			SimpleBakedModel s = (SimpleBakedModel) m;
			dedupeList(s.generalQuads);
			for (List<BakedQuad> l : s.faceQuads.values()) {
				dedupeList(l);
			}
		} else if (m instanceof MultipartBakedModel) {
			for (IBakedModel part : ((MultipartBakedModel) m).tcSelectors().values()) {
				visit(part);
			}
		} else if (m instanceof WeightedBakedModel) {
			for (WeightedBakedModel.WeightedModel w : ((WeightedBakedModel) m).tcModels()) {
				visit(w.model);
			}
		}
	}

	private static void dedupeList(List<BakedQuad> list) {
		if (list == null) {
			return;
		}
		try {
			for (int i = 0, n = list.size(); i < n; ++i) {
				BakedQuad q = list.get(i);
				if (q == null || q.getClass() != BakedQuad.class) {
					continue; // only plain faces; subclasses may carry extra data
				}
				QuadKey k = new QuadKey(q);
				BakedQuad shared = quads.get(k);
				if (shared == null) {
					quads.put(k, q);
				} else if (shared != q) {
					list.set(i, shared);
					++replaced;
				}
			}
		} catch (UnsupportedOperationException e) {
			// a list that can't be changed: leave it as it is
		}
	}
}
