package net.treasureclient.boost;

/**
 * Smooth Packets: the game handles every packet waiting from the server in one frame, however
 * long that takes. When a big burst arrives (joining, teleporting, a minigame starting, lots of
 * chunks or entities at once) that one frame freezes. Here the client handles packets for up to
 * 4 ms a frame; the rest wait, in the same order, for the next frame.
 *
 * The longer packets have been waiting, the more time each frame gets (4 ms + a tenth of the wait),
 * so a steady stream finds its own level; after a whole second of waiting it catches up in one go,
 * so nothing can fall far behind. The game's singleplayer server (another thread) is never touched.
 * Normal play never fills 4 ms, so it only changes anything during bursts.
 */
public final class SmoothPackets {
    private SmoothPackets() {}

    private static final long BASE_NS = 4_000_000L;
    private static final long CATCH_UP_NS = 1_000_000_000L;

    private static volatile Thread clientThread;
    private static long start;
    /** When packets were first left for a later frame (0 = none waiting). */
    private static long behindSince;

    /** Called when the mod starts (on the game's own thread). */
    public static void init() {
        clientThread = Thread.currentThread();
    }

    /** The frame's packet handling starts. */
    public static void begin() {
        if (Thread.currentThread() == clientThread) start = System.nanoTime();
    }

    /**
     * Asked before each packet: empty = the queue really is empty. Returns true to stop for this
     * frame (always when it's empty).
     */
    public static boolean stopHere(boolean empty) {
        if (Thread.currentThread() != clientThread) return empty;
        if (empty) {
            behindSince = 0;
            return true;
        }
        if (!Boosts.ACTIVE[Boosts.SMOOTH_PACKETS]) return false;
        try {
            long now = System.nanoTime();
            long behind = behindSince == 0 ? 0 : now - behindSince;
            if (behind > CATCH_UP_NS) return false; // waited a whole second: catch up now
            if (now - start < BASE_NS + behind / 10) return false;
            if (behindSince == 0) behindSince = now;
            Boosts.ran(Boosts.SMOOTH_PACKETS);
            return true;
        } catch (Throwable t) {
            Boosts.failed(Boosts.SMOOTH_PACKETS, t);
            return false;
        }
    }
}
