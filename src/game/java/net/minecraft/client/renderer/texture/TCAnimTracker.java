package net.minecraft.client.renderer.texture;

import java.util.List;

/**
 * Treasure Client: keeps track of which animated textures (water, lava, fire,
 * sea lanterns, magma...) were drawn recently, so only those get new frames
 * copied into the texture atlas. Every animation keeps its timing either way,
 * and a texture coming back into view jumps to its current frame.
 *
 * <p>Chunks remember the animated textures they contain when they're built.
 * Each frame the visible chunks mark theirs as seen. Items, entities,
 * particles and overlays mark theirs when they draw.
 */
public final class TCAnimTracker {

	private TCAnimTracker() {
	}

	/** Counts texture ticks (one per game tick). */
	static int tick = 0;

	/** A texture counts as visible if it was drawn within this many ticks. */
	static final int VISIBLE_TICKS = 4;

	/** While a chunk is being built, its list of animated textures. */
	private static List<TextureAtlasSprite> compileTarget = null;

	/** Marks a texture as drawn (or, while building a chunk, records it for that chunk). */
	public static void use(TextureAtlasSprite sprite) {
		if (sprite == null || !sprite.tcAnimated) {
			return;
		}
		List<TextureAtlasSprite> target = compileTarget;
		if (target != null) {
			if (!target.contains(sprite)) {
				target.add(sprite);
			}
		} else {
			sprite.tcLastSeenTick = tick;
		}
	}

	/** Marks every animated texture in a visible chunk as drawn. */
	public static void useAll(List<TextureAtlasSprite> sprites) {
		for (int i = 0, n = sprites.size(); i < n; ++i) {
			sprites.get(i).tcLastSeenTick = tick;
		}
	}

	public static void beginCompile(List<TextureAtlasSprite> chunkSprites) {
		chunkSprites.clear();
		compileTarget = chunkSprites;
	}

	public static void endCompile() {
		compileTarget = null;
	}

	static boolean isVisible(TextureAtlasSprite sprite) {
		return tick - sprite.tcLastSeenTick <= VISIBLE_TICKS;
	}
}
