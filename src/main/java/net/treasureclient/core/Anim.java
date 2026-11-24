package net.treasureclient.core;

/**
 * Smooth animations for the menus, the same easing as the browser client's 1.5 look
 * (cubic-bezier(.2,.7,.2,1)). Values move toward their target over time, independent of FPS.
 */
public final class Anim {
    private float value;
    private float from;
    private float to;
    private long start;
    private final float durationMs;

    public Anim(float initial, float durationMs) {
        this.value = this.from = this.to = initial;
        this.durationMs = durationMs;
    }

    /** Sets the target; starts a new animation when it changes. */
    public Anim target(float t) {
        if (t != to) {
            from = get();
            to = t;
            start = System.nanoTime();
        }
        return this;
    }

    public void snap(float v) {
        value = from = to = v;
    }

    public float get() {
        if (value == to) return to;
        float p = enabled() ? (System.nanoTime() - start) / 1_000_000f / durationMs : 1f;
        if (p >= 1f) return value = to;
        value = from + (to - from) * ease(p);
        return value;
    }

    public float target() {
        return to;
    }

    /** cubic-bezier(.2,.7,.2,1), solved for x with a few Newton steps. */
    public static float ease(float x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        float t = x;
        for (int i = 0; i < 6; i++) {
            float bx = bez(t, .2f, .2f) - x;
            float d = bezD(t, .2f, .2f);
            if (Math.abs(d) < 1e-5f) break;
            t -= bx / d;
        }
        return bez(Math.max(0, Math.min(1, t)), .7f, 1f);
    }

    private static float bez(float t, float p1, float p2) {
        float u = 1 - t;
        return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t;
    }

    private static float bezD(float t, float p1, float p2) {
        float u = 1 - t;
        return 3 * u * u * p1 + 6 * u * t * (p2 - p1) + 3 * t * t * (1 - p2);
    }

    /** Menu Animations (on by default) — off makes everything instant. */
    public static boolean enabled() {
        return Config.get().on("guiAnim");
    }

    /** 0..1 progress of something that started at startNanos and lasts ms (eased), for one-shot entrances. */
    public static float since(long startNanos, float ms, float delayMs) {
        if (!enabled()) return 1;
        float p = ((System.nanoTime() - startNanos) / 1_000_000f - delayMs) / ms;
        return ease(Math.max(0, Math.min(1, p)));
    }
}
