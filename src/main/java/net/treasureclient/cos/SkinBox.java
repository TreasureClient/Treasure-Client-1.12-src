package net.treasureclient.cos;

/**
 * Room around the menus' player picture. Minecraft draws that picture into its own image exactly the
 * size of the player widget, so big cosmetics (auras, wings, tall hats, trails) were cut off at its
 * edges. The image is made bigger on every side here, while the player stays the same size, in the
 * same place, and the widget itself (where you click and drag) doesn't change.
 *
 * The player stands on the bottom of that image, so when room is added below, the renderer is told
 * to lift the player by the same amount ({@link #lift}).
 */
public final class SkinBox {
    private SkinBox() {}

    /** Extra room, as parts of the widget's height. */
    private static final float SIDE = 0.55f, TOP = 0.40f, BOTTOM = 0.22f;

    /** The boxes made bigger lately (a few per frame at most) and the room added below each. */
    private static final int N = 8;
    private static final int[] BOX = new int[N * 4];
    private static final int[] BELOW = new int[N];
    private static int next;

    /** The bigger box for a player picture: {x0, y0, x1, y1}. */
    public static int[] grow(int x0, int y0, int x1, int y1) {
        int h = Math.max(1, y1 - y0);
        int side = Math.round(h * SIDE), top = Math.round(h * TOP), below = Math.round(h * BOTTOM);
        int[] out = {x0 - side, y0 - top, x1 + side, y1 + below};
        int i = next;
        next = (next + 1) % N;
        System.arraycopy(out, 0, BOX, i * 4, 4);
        BELOW[i] = below;
        return out;
    }

    /** How far up (Minecraft GUI units) to move the player in this picture: the room added below it, or 0. */
    public static int lift(int x0, int y0, int x1, int y1) {
        for (int i = 0; i < N; i++) {
            int b = i * 4;
            if (BOX[b] == x0 && BOX[b + 1] == y0 && BOX[b + 2] == x1 && BOX[b + 3] == y1) return BELOW[i];
        }
        return 0;
    }
}
