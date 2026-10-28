package org.taumc.celeritas.impl.render.terrain.fog;

import net.lax1dude.eaglercraft.opengl.TCRenderState;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;

/**
 * Treasure Client: reads fog settings from Eaglercraft's emulated GL state.
 */
public class GLStateManagerFogService implements FogService {
    public static final GLStateManagerFogService INSTANCE = new GLStateManagerFogService();

    private final float[] color = new float[4];

    @Override
    public float getFogEnd() {
        return TCRenderState.getFogEnd();
    }

    @Override
    public float getFogStart() {
        return TCRenderState.getFogStart();
    }

    @Override
    public float getFogDensity() {
        return TCRenderState.getFogDensity();
    }

    @Override
    public int getFogShapeIndex() {
        return 0;
    }

    @Override
    public float getFogCutoff() {
        // Celeritas skips chunks (and the entities in them) past this distance. Only trust it for
        // normal linear fog that's turned on: with exp fog (water, lava, clouds) or fog off, the fog
        // end value is left over from earlier and could hide chunks and entities that should show.
        if (!TCRenderState.isFogEnabled() || TCRenderState.isFogExp() || TCRenderState.getFogEnd() <= 0.0F) {
            return Float.MAX_VALUE;
        }
        return TCRenderState.getFogEnd();
    }

    @Override
    public float[] getFogColor() {
        float[] c = this.color;
        c[0] = TCRenderState.getFogRed();
        c[1] = TCRenderState.getFogGreen();
        c[2] = TCRenderState.getFogBlue();
        c[3] = 1.0F;
        return c;
    }

    @Override
    public ChunkFogMode getFogMode() {
        if (!TCRenderState.isFogEnabled()) {
            return ChunkFogMode.NONE;
        }
        return ChunkFogMode.fromGLMode(TCRenderState.isFogExp() ? 0x0800 /* GL_EXP */ : 0x2601 /* GL_LINEAR */);
    }
}
