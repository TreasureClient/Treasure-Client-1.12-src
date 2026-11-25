package net.treasureclient.boost;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.treasureclient.core.Config;

/**
 * Treasure Client's built-in boosts: fixes for work the game does that no other mod fixes.
 *
 * Every boost is on by default and is listed (with a switch) on Treasure Client's page in the
 * Mods screen. The switch is only there so a broken boost can be turned off while testing.
 *
 * Rules every boost follows:
 * - It steps aside on its own: if one of the mods in {@code yieldTo} is installed, its mixins are
 *   never applied (see {@link BoostMixinPlugin}) and it shows "Stepped aside for ...".
 * - Its mixins live in the package {@code net.treasureclient.mixin.boost}, one copy per version
 *   folder (src/v1_21 for 1.21.11, src/v26 for all 26.x, src/v26_1 or src/v26_2 when 26.1 and 26.2+
 *   differ), with the same class name. A version without a copy shows "Not in this version".
 * - The game code checks {@code Boosts.ACTIVE[ID]} (one array read) and calls {@link #ran(int)}
 *   when it did its job, so the Mods screen can show "Working".
 * - If it ever throws, the game code calls {@link #failed(int, Throwable)}: the boost turns
 *   itself off until the game restarts, and the game carries on as normal.
 * - Code-only and automatic: no FPS caps, no lower quality, nothing for the player to set.
 */
public final class Boosts {
    private Boosts() {}

    /** One boost. mixins = its mixin class names (in net.treasureclient.mixin.boost). */
    public record Boost(int index, String id, String name, String desc, List<String> yieldTo, List<String> mixins) {}

    public static final List<Boost> ALL = new ArrayList<>();

    /** For the game code: ACTIVE[id] = switched on, built for this version, not stepped aside, not failed. */
    public static boolean[] ACTIVE = new boolean[0];
    private static boolean[] ran = new boolean[0], broke = new boolean[0];
    private static String[] steppedFor;
    private static boolean[] built;

    /** Adds a boost; returns its number (keep it in a static final int for the game code). */
    private static int add(String id, String name, String desc, List<String> yieldTo, String... mixins) {
        int i = ALL.size();
        ALL.add(new Boost(i, id, name, desc, List.copyOf(yieldTo), List.of(mixins)));
        return i;
    }

    // ------------------------------------------------------------------ the boosts
    // Each one: its number = add(id, name, description, mods it steps aside for, its mixins).

    /** Phase 1: big bursts of server data spread over a few frames (see SmoothPackets). */
    public static final int SMOOTH_PACKETS = add("smoothPackets", "Smooth Packets",
        "Big bursts of server data (joining, teleports, minigame starts) are spread over a few frames instead of freezing one",
        List.of(), "SmoothPacketsMixin");

    /** Phase 2: name tags and the score under names kept until they change (see NameTags). */
    public static final int NAME_TAGS = add("nameTags", "Name Tag Cache",
        "Name tags and the score under names are built once and reused until they change, instead of every frame",
        List.of(), "NameTagEntityMixin", "NameTagMixin", "NameTagScoreMixin", "NameTagWidthMixin");

    static {
        refresh();
    }

    // ------------------------------------------------------------------ state

    /** Works out again which boosts run (after a switch changes). */
    public static synchronized void refresh() {
        int n = ALL.size();
        if (ACTIVE.length != n) {
            ran = new boolean[n];
            broke = new boolean[n];
        }
        if (steppedFor == null || steppedFor.length != n) {
            steppedFor = new String[n];
            built = new boolean[n];
            for (Boost b : ALL) {
                steppedFor[b.index()] = installedFrom(b.yieldTo());
                built[b.index()] = hasAnyMixin(b);
            }
        }
        boolean[] a = new boolean[n];
        for (Boost b : ALL) {
            int i = b.index();
            a[i] = switchedOn(b) && built[i] && steppedFor[i] == null && !broke[i];
        }
        ACTIVE = a;
    }

    /** The switch in the Mods screen (on unless the player turned it off). */
    public static boolean switchedOn(Boost b) {
        Boolean v = Config.get().enabled.get(key(b));
        return v == null || v;
    }

    public static void setSwitchedOn(Boost b, boolean on) {
        Config.get().set(key(b), on);
        refresh();
    }

    private static String key(Boost b) {
        return "boost." + b.id();
    }

    /** The boost did its job at least once (for the Mods screen's "Working"). */
    public static void ran(int i) {
        ran[i] = true;
    }

    /** The boost's code threw: it turns itself off until the game restarts. */
    public static void failed(int i, Throwable t) {
        if (broke[i]) return;
        broke[i] = true;
        System.err.println("[Treasure Client] boost \"" + ALL.get(i).name() + "\" turned itself off after an error: " + t);
        t.printStackTrace();
        refresh();
    }

    /** What the Mods screen says about a boost. */
    public static String status(Boost b) {
        int i = b.index();
        if (steppedFor[i] != null) return "Stepped aside for " + steppedFor[i];
        if (!built[i]) return "Not in this Minecraft version";
        if (broke[i]) return "Turned itself off after an error (see the log)";
        if (!switchedOn(b)) return "Off";
        return ran[i] ? "Working" : "On (waiting for something to speed up)";
    }

    /** True while the boost can be switched (it isn't stepped aside or missing from this version). */
    public static boolean switchable(Boost b) {
        return steppedFor[b.index()] == null && built[b.index()];
    }

    // ------------------------------------------------------------------ for the mixin plugin

    /** The boost mixins to apply in this game: built for this version and not stepped aside. */
    static List<String> mixinsToApply() {
        List<String> out = new ArrayList<>();
        for (Boost b : ALL) {
            if (installedFrom(b.yieldTo()) != null) {
                System.out.println("[Treasure Client] boost \"" + b.name() + "\" steps aside for " + installedFrom(b.yieldTo()));
                continue;
            }
            for (String m : b.mixins()) if (mixinExists(m)) out.add(m);
        }
        return out;
    }

    private static boolean hasAnyMixin(Boost b) {
        for (String m : b.mixins()) if (mixinExists(m)) return true;
        return false;
    }

    private static boolean mixinExists(String simpleName) {
        ClassLoader cl = Boosts.class.getClassLoader();
        return cl != null && cl.getResource("net/treasureclient/mixin/boost/" + simpleName + ".class") != null;
    }

    /** The name of the first of these mods that's installed, or null. */
    private static String installedFrom(List<String> modIds) {
        FabricLoader fl = FabricLoader.getInstance();
        for (String id : modIds) {
            var c = fl.getModContainer(id);
            if (c.isPresent()) return c.get().getMetadata().getName();
        }
        return null;
    }
}
