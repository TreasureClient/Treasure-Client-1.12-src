package net.treasureclient.hud;

import java.util.ArrayDeque;

/** Clicks per second, frame rate history, reach and combo, the same rules as the browser client. */
public final class Trackers {
    private Trackers() {}

    // ---- CPS: clicks in the last second ----
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();

    public static void click(boolean left) {
        (left ? LEFT : RIGHT).addLast(System.currentTimeMillis());
    }

    public static int cps(boolean left) {
        ArrayDeque<Long> q = left ? LEFT : RIGHT;
        long now = System.currentTimeMillis();
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
        return q.size();
    }

    // ---- FPS history for the FPS Graph (one sample every half second, 70 samples) ----
    public static final int[] FPS_HISTORY = new int[70];
    public static int fpsCount;
    private static long lastSample;

    public static void sampleFps(int fps) {
        long now = System.currentTimeMillis();
        if (now - lastSample < 500) return;
        lastSample = now;
        if (fpsCount < FPS_HISTORY.length) FPS_HISTORY[fpsCount++] = fps;
        else {
            System.arraycopy(FPS_HISTORY, 1, FPS_HISTORY, 0, FPS_HISTORY.length - 1);
            FPS_HISTORY[FPS_HISTORY.length - 1] = fps;
        }
    }

    // ---- Reach + combo ----
    /** The last confirmed hit's distance, or NaN before the first hit. */
    public static double lastReach = Double.NaN;
    public static int combo;
    private static long lastComboHit;
    private static int pendingTarget = -1;
    private static double pendingReach;
    private static long pendingAt;
    public static int attacks, confirmed;

    /** You swung at an entity (reach measured from your eyes to the nearest point of its box). */
    public static void attacked(int entityId, double reach) {
        attacks++;
        pendingTarget = entityId;
        pendingReach = reach;
        pendingAt = System.currentTimeMillis();
    }

    /** The server says an entity got hurt: counts as a hit if it's the one you swung at within a second. */
    public static void entityHurt(int entityId) {
        if (entityId == pendingTarget && System.currentTimeMillis() - pendingAt < 1000) {
            pendingTarget = -1;
            confirmed++;
            lastReach = pendingReach;
            combo++;
            lastComboHit = System.currentTimeMillis();
            net.treasureclient.feature.Crosshair.meleeHit();
        }
    }

    /** You got hurt: the combo ends. */
    public static void selfHurt() {
        combo = 0;
    }

    public static void tick() {
        if (combo > 0 && System.currentTimeMillis() - lastComboHit > 3000) combo = 0;
    }

    public static void reset() {
        combo = 0;
        pendingTarget = -1;
    }
}
