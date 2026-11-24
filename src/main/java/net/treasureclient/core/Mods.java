package net.treasureclient.core;

import net.treasureclient.core.ModDef.Choice;
import net.treasureclient.core.ModDef.Setting;
import net.treasureclient.core.ModDef.Slider;
import net.treasureclient.core.ModDef.Toggle;

import java.util.ArrayList;
import java.util.List;

/**
 * The Mod Menu's mods, with the same keys, names, descriptions and tabs as the browser client.
 * Browser-only mods (ViaVersion, Dynamic Resolution, the Eaglercraft performance pack...) are left out:
 * real Minecraft gets its performance mods from the Treasure Launcher pack instead.
 */
public final class Mods {
    public static final List<String> TABS = List.of("all", "hud", "render", "perf", "misc");
    public static final List<String> TAB_NAMES = List.of("All", "HUD", "Render", "Performance", "Misc");

    public static final List<ModDef> ALL = new ArrayList<>();

    private static void add(String key, String name, String desc, String cat, Setting... settings) {
        ALL.add(new ModDef(key, name, desc, cat, key, false, List.of(settings)));
    }

    private static Choice choice(String key, String label, String def, String... valuesAndLabels) {
        List<String> v = new ArrayList<>(), l = new ArrayList<>();
        for (int i = 0; i + 1 < valuesAndLabels.length; i += 2) {
            v.add(valuesAndLabels[i]);
            l.add(valuesAndLabels[i + 1]);
        }
        return new Choice(key, label, v, l, def);
    }

    static {
        // ---- HUD ----
        add("coords", "Coordinates", "X, Y, Z and biome", "hud",
            choice("coordPrecision", "Precision", "1", "0", "Whole blocks", "1", "1 decimal"),
            new Toggle("coordBiome", "Show biome", true));
        add("direction", "Direction", "Compass heading", "hud",
            choice("dirMode", "Style", "cardinal", "cardinal", "N / NE / E", "detailed", "With axis (+X)"));
        add("armorStatus", "Armor Status", "Equipped armor + durability", "hud");
        add("reachDisp", "Reach Display", "How far away your last hit landed, in blocks", "hud",
            choice("reachStyle", "Style", "box", "box", "Box", "cross", "Above crosshair"));
        add("combo", "Combo Counter", "Hits in a row before you get hit back", "hud");
        add("itemCount", "Item Counter", "How many arrows, pearls, golden apples and more you have", "hud",
            new Toggle("icArrow", "Arrows", true), new Toggle("icPearl", "Ender pearls", true),
            new Toggle("icGapple", "Golden apples", true), new Toggle("icTotem", "Totems", true),
            new Toggle("icPotion", "Splash potions", true), new Toggle("icBlocks", "Blocks", false),
            new Toggle("icHideZero", "Hide items you have none of", true));
        add("potionEffects", "Potion Effects", "Active effects, level & duration", "hud");
        add("heldItem", "Held Item", "Name, count & durability", "hud");
        add("keystrokes", "Keystrokes", "On-screen WASD + mouse", "hud",
            new Toggle("ksShowMouse", "Mouse buttons", true), new Toggle("ksShowSpace", "Space bar", true));
        add("cps", "CPS Counter", "Live clicks per second", "hud");
        add("fps", "FPS Counter", "Live framerate readout", "hud");
        add("ping", "Ping Display", "Connection latency to the server", "hud");
        add("fpsGraph", "FPS Graph", "Recent framerate history", "hud");
        add("perfMonitor", "Performance Monitor", "FPS, frame time & ping together", "hud");

        // ---- Render ----
        add("fullbright", "Fullbright", "Max brightness — see in caves & at night", "render");
        add("noHurtCam", "No Hurt Cam", "Stop the camera tilting when you take damage", "render");
        add("hitboxes", "Hitboxes", "Visual-only entity bounding boxes — no reach or aim assist", "render");
        add("lowFire", "Low Fire", "Shorter fire overlay so it blocks less of your view", "render",
            new Slider("lowFireH", "Height", 0, 100, 5, 40, "%"));
        add("zoom", "Zoom", "Hold a key to zoom in", "render",
            new Slider("zoomLevel", "Zoom field of view", 10, 60, 5, 30, "°"),
            new Toggle("smoothZoom", "Smooth zoom", false));

        // ---- Performance ----
        add("noParticles", "No Particles", "Disable all particles for more FPS", "perf");

        // ---- Misc ----
        add("toggleSprint", "Toggle Sprint", "Sprint key flips ON (tap W to sprint) / OFF (vanilla)", "misc",
            new Toggle("sprintStatus", "Show Sprint on the HUD", false));
        add("toggleSneak", "Toggle Sneak", "Sneak key flips ON (stay sneaking) / OFF (vanilla)", "misc",
            new Toggle("sneakStatus", "Show Sneak on the HUD", false));
        ALL.add(new ModDef("guiAnim", "Menu Animations", "Smooth open / close & hover transitions", "misc", "guiAnim", true, List.of()));
    }

    public static ModDef byKey(String key) {
        for (ModDef d : ALL) if (d.key().equals(key)) return d;
        return null;
    }

    public static List<ModDef> inTab(String tab) {
        if (tab.equals("all")) return ALL;
        List<ModDef> out = new ArrayList<>();
        for (ModDef d : ALL) if (d.cat().equals(tab)) out.add(d);
        return out;
    }

    /** Where each icon sits in textures/gui/icons.png (8 columns of 64 px). */
    public static final List<String> ICONS = List.of("coords", "direction", "chAddons", "satDisplay", "clearLiquids",
        "reachDisp", "combo", "itemCount", "sbOn", "boOn", "armorStatus", "potionEffects", "heldItem", "keystrokes", "cps",
        "fps", "ping", "fpsGraph", "perfMonitor", "fullbright", "noHurtCam", "dynfov", "tntTimer", "crosshair", "hitboxes",
        "tAnim", "oldAnim", "hitColorOn", "tierTagger", "lowFire", "lowShield", "smallTotem", "zoom", "perfpack",
        "noParticles", "pauseUnfocused", "dynResOn", "toggleSprint", "toggleSneak", "via", "autoGG", "autoLogin", "pwHide",
        "guiAnim", "_gear", "_search");
}
