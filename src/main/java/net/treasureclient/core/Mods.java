package net.treasureclient.core;

import net.treasureclient.core.ModDef.Choice;
import net.treasureclient.core.ModDef.Colors;
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

    /** Colour swatches: name, 0xRRGGBB, name, 0xRRGGBB... ("theme" = the theme's accent colour). */
    private static Colors colors(String key, String label, String def, Object... namesAndColors) {
        List<String> v = new ArrayList<>();
        List<Integer> c = new ArrayList<>();
        for (int i = 0; i + 1 < namesAndColors.length; i += 2) {
            v.add((String) namesAndColors[i]);
            c.add((Integer) namesAndColors[i + 1]);
        }
        return new Colors(key, label, v, c, def);
    }

    /** The colour a Colors setting is set to (0xRRGGBB); "theme" follows the theme's accent colour. */
    public static int color(String key) {
        for (ModDef d : ALL)
            for (Setting s : d.settings())
                if (s instanceof Colors c && c.key().equals(key)) {
                    String v = Config.get().str(key, c.def());
                    return v.equals("theme") ? Theme.current().a1() & 0xFFFFFF : c.rgb(v);
                }
        return 0xFFFFFF;
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
        add("clock", "Clock", "The real time of day in a small box", "hud",
            choice("clockFmt", "Format", "24", "24", "24-hour", "12", "12-hour"),
            new Toggle("clockSecs", "Show seconds", false));
        add("cooldowns", "Cooldowns", "Time left on ender pearls, chorus fruit, wind charges, shields and other items you can't use yet", "hud");
        add("crosshair", "Custom Crosshair", "Your own crosshair: shape, size, gap and colour", "hud",
            choice("crosshairStyle", "Style", "cross", "cross", "Cross", "dot", "Dot", "tshape", "T-Shape"),
            new Slider("crosshairSize", "Size", 12, 40, 1, 22, ""),
            new Slider("crosshairThickness", "Thickness", 1, 5, 1, 2, ""),
            new Slider("crosshairGap", "Gap", 0, 8, 1, 3, ""),
            colors("crosshairColor", "Colour", "theme", "theme", 0xFFD76B, "white", 0xFFFFFF, "red", 0xFF4A4A,
                "green", 0x5BEA6B, "cyan", 0x45E3E3, "black", 0x111111),
            new Slider("crosshairOpacity", "Opacity", 20, 100, 5, 100, "%"));
        add("chAddons", "Crosshair Addons", "Small icons around the crosshair: target in reach, elytra, rocket boost, hit marker, shields", "hud",
            new Toggle("chTarget", "Brackets when a player is in reach", true),
            new Toggle("chMobs", "Hostile mobs too", false),
            new Toggle("chElytra", "Elytra icon while wearing one", true),
            new Toggle("chRocket", "Rocket boost time left", true),
            new Toggle("chHit", "Hit marker when your arrow hits a player", true),
            new Toggle("chHitMelee", "Hit marker on melee hits too", false),
            new Toggle("chShieldBreak", "Shield break when you stun a shield", true),
            new Toggle("chShieldUp", "Shield icon when your target is blocking", true));
        add("satDisplay", "Saturation Display", "Gold outlines on the food bar show hidden saturation", "hud");
        add("sbOn", "Scoreboard", "Hide the red numbers, change the size and background of the sidebar", "hud",
            new Toggle("sbNoNums", "Hide the red numbers", true),
            new Slider("sbScale", "Size", 50, 150, 5, 100, "%"),
            choice("sbBg", "Background", "normal", "normal", "Normal", "light", "Lighter", "none", "None"));

        // ---- Render ----
        add("fullbright", "Fullbright", "Max brightness — see in caves & at night", "render");
        add("noHurtCam", "No Hurt Cam", "Stop the camera tilting when you take damage", "render");
        add("hitboxes", "Hitboxes", "Visual-only entity bounding boxes — no reach or aim assist", "render");
        add("lowFire", "Low Fire", "Shorter fire overlay so it blocks less of your view", "render",
            new Slider("lowFireH", "Height", 0, 100, 5, 40, "%"));
        add("zoom", "Zoom", "Hold a key to zoom in", "render",
            new Slider("zoomLevel", "Zoom field of view", 10, 60, 5, 30, "°"),
            new Toggle("smoothZoom", "Smooth zoom", false));
        add("boOn", "Block Outline", "Colour and thickness of the box around the block you're looking at", "render",
            colors("boColor", "Colour", "black", "black", 0x000000, "white", 0xFFFFFF, "red", 0xFF4A4A, "orange", 0xFF8A3C,
                "yellow", 0xFFE066, "green", 0x5BEA6B, "cyan", 0x45E3E3, "blue", 0x4A8CFF, "purple", 0x9A6BFF, "pink", 0xFF6FA8),
            new Slider("boWidth", "Thickness", 1, 6, 1, 2, ""),
            new Slider("boAlpha", "Opacity", 10, 100, 5, 40, "%"));
        add("hitColorOn", "Hit Color", "Change the red flash when players and mobs get hurt", "render",
            colors("hitColor", "Colour", "red", "red", 0xFF0000, "orange", 0xFF8A00, "yellow", 0xFFE000, "green", 0x30E030,
                "cyan", 0x00E0E0, "blue", 0x3070FF, "purple", 0xA040FF, "pink", 0xFF50B0, "white", 0xFFFFFF),
            new Slider("hitAlpha", "Strength", 10, 80, 5, 50, "%"));
        add("clearLiquids", "Clear Liquids", "See clearly when you are inside water or lava", "render");
        ALL.add(new ModDef("dynfov", "Dynamic FOV", "Zoom the view when sprinting, flying, with Speed or drawing a bow (turn off to keep it still)",
            "render", "dynfov", true, List.of()));
        add("tntTimer", "TNT Timer", "Show the fuse countdown above lit TNT", "render");
        add("timeChanger", "Time & Weather", "Pick the time of day and the weather you see (only on your screen; the server doesn't change)", "render",
            choice("tcTime", "Time", "server", "server", "Server", "day", "Morning", "noon", "Noon", "sunset", "Sunset", "night", "Night", "midnight", "Midnight"),
            choice("tcWeather", "Weather", "server", "server", "Server", "clear", "Clear", "rain", "Rain", "thunder", "Storm"));
        add("lowShield", "Low Shield", "Lower the shield in your hand so it covers less of your view", "render",
            new Slider("lowShieldA", "How much lower", 0, 60, 5, 30, "%"));
        add("smallTotem", "Small Totem", "Shrink the totem-pop effect on your screen", "render",
            new Slider("totemScale", "Size", 10, 100, 5, 40, "%"));
        add("oldAnim", "1.7 Animations", "Old-school item animations: a small dip after hitting, and swinging while you eat, drink, draw a bow or block", "render",
            new Toggle("oaNoDip", "Small item dip after a hit (instead of the big cooldown dip)", true),
            new Toggle("oaUseSwing", "Swing while eating, drinking, using a bow or blocking", true));
        ALL.add(new ModDef("betterShift", "Better Shift", "Sneaking lowers your view only a little, like 1.8, so bridging doesn't bounce the screen (camera only)",
            "render", "toggleSneak", false, List.of()));

        // ---- Performance ----
        add("noParticles", "No Particles", "Disable all particles for more FPS", "perf");

        // ---- Misc ----
        add("toggleSprint", "Toggle Sprint", "Sprint key flips ON (tap W to sprint) / OFF (vanilla)", "misc",
            new Toggle("sprintStatus", "Show Sprint on the HUD", false));
        add("toggleSneak", "Toggle Sneak", "Sneak key flips ON (stay sneaking) / OFF (vanilla)", "misc",
            new Toggle("sneakStatus", "Show Sneak on the HUD", false));
        add("chatTweaks", "Chat Tweaks", "Times next to messages, repeated messages stacked into one line, and a longer chat history", "misc",
            new Toggle("ctTime", "Show the time next to each message", true),
            choice("ctTimeFmt", "Time format", "24", "24", "24-hour", "12", "12-hour"),
            new Toggle("ctStack", "Stack repeated messages (x2, x3...)", true),
            new Toggle("ctLong", "Keep 1000 messages instead of 100", true));
        add("autoGG", "Auto GG", "Says gg in chat for you when a game ends", "misc",
            choice("agMsg", "Message", "gg", "gg", "gg", "ggs", "ggs", "ggwp", "gg wp", "goodgame", "good game", "gf", "gf"),
            new Slider("agDelay", "Delay", 0, 3000, 250, 1000, "ms"),
            new Toggle("agGlhf", "Say \"glhf\" when a game starts", false));
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
        "guiAnim", "_gear", "_search", "clock", "cooldowns", "chatTweaks", "timeChanger");
}
