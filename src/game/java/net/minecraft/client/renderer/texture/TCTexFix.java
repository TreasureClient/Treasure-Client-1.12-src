package net.minecraft.client.renderer.texture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Treasure Client: TexFix-style memory saving (inspired by Speiger's TexFix).
 * Once models are baked, sprite pixel data is only needed for animated
 * sprites. Vanilla frees it for block and item model sprites; this frees it
 * for every other sprite left in the atlas too.
 */
public class TCTexFix {

	private static final Logger LOGGER = LogManager.getLogger("TexFix");

	public static void sweep(TextureMap map) {
		if (map == null) {
			return;
		}
		int cleared = 0;
		long bytes = 0L;
		for (TextureAtlasSprite sprite : map.tcUploadedSprites()) {
			if (sprite.hasAnimationMetadata() || sprite.framesTextureData.isEmpty()) {
				continue;
			}
			for (int i = 0, l = sprite.framesTextureData.size(); i < l; ++i) {
				int[][] frame = sprite.framesTextureData.get(i);
				if (frame != null) {
					for (int j = 0; j < frame.length; ++j) {
						if (frame[j] != null) {
							bytes += frame[j].length * 4L;
						}
					}
				}
			}
			sprite.clearFramesTextureData();
			++cleared;
		}
		LOGGER.info("Freed pixel data of {} sprites (~{} KB)", cleared, bytes / 1024L);
	}
}
