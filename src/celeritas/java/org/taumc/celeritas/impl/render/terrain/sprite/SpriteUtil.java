package org.taumc.celeritas.impl.render.terrain.sprite;

import net.minecraft.client.renderer.texture.TCAnimTracker;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public class SpriteUtil {
    /** Treasure Client: visible chunks keep their animated textures updating (see TCAnimTracker). */
    public static void markSpriteActive(TextureAtlasSprite sprite) {
        TCAnimTracker.use(sprite);
    }
}
