package org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters;

public enum AlphaCutoffParameter {
    ZERO(0.0F), ONE_TENTH(0.1F), HALF(0.5F), ONE(1.0F);
    private final float cutoff;

    public static AlphaCutoffParameter valueOf(float val) {
        for (var param : AlphaCutoffParameter.values()) {
            if (Math.abs(param.cutoff() - val) < 0.001F) {
                return param;
            }
        }
        throw new IllegalArgumentException("No alpha cutoff option found for value: " + val);
    }

    @java.lang.SuppressWarnings("all")
    private AlphaCutoffParameter(final float cutoff) {
        this.cutoff = cutoff;
    }

    @java.lang.SuppressWarnings("all")
    public float cutoff() {
        return this.cutoff;
    }
}
