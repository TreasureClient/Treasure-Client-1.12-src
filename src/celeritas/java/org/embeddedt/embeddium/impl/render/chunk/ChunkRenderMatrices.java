package org.embeddedt.embeddium.impl.render.chunk;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import net.lax1dude.eaglercraft.internal.buffer.FloatBuffer;

public record ChunkRenderMatrices(Matrix4fc projection, Matrix4fc modelView) {
    public ChunkRenderMatrices(FloatBuffer projection, FloatBuffer modelView) {
        this(toMatrix(projection), toMatrix(modelView));
    }

    private static Matrix4f toMatrix(FloatBuffer buf) {
        float[] m = new float[16];
        for (int i = 0; i < 16; i++) {
            m[i] = buf.get(buf.position() + i);
        }
        return new Matrix4f().set(m);
    }
}
