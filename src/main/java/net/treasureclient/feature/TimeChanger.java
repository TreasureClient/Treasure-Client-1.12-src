package net.treasureclient.feature;

import net.treasureclient.core.Config;

/** Time & Weather: the time of day and weather you see. Only on your screen; the server is never touched. */
public final class TimeChanger {
    private TimeChanger() {}

    /** The world's time (in ticks) as you'll see it: same day, picked time of day. */
    public static long time(long real) {
        Config c = Config.get();
        if (!c.on("timeChanger")) return real;
        long target = switch (c.str("tcTime", "server")) {
            case "day" -> 1000;
            case "noon" -> 6000;
            case "sunset" -> 12000;
            case "night" -> 14000;
            case "midnight" -> 18000;
            default -> -1;
        };
        if (target < 0) return real;
        return real - Math.floorMod(real, 24000L) + target;
    }

    /** True when the time is fixed (no in-between ticks, so the sun doesn't wobble). */
    public static boolean fixedTime() {
        Config c = Config.get();
        return c.on("timeChanger") && !c.str("tcTime", "server").equals("server");
    }

    /** Rain strength you see, or -1 to keep the server's. */
    public static float rain() {
        Config c = Config.get();
        if (!c.on("timeChanger")) return -1;
        return switch (c.str("tcWeather", "server")) {
            case "clear" -> 0f;
            case "rain", "thunder" -> 1f;
            default -> -1;
        };
    }

    /** Thunder strength you see, or -1 to keep the server's. */
    public static float thunder() {
        Config c = Config.get();
        if (!c.on("timeChanger")) return -1;
        return switch (c.str("tcWeather", "server")) {
            case "clear", "rain" -> 0f;
            case "thunder" -> 1f;
            default -> -1;
        };
    }
}
