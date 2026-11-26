package net.treasureclient.core;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sorts out key clashes: when two key bindings share a key, the more important one keeps it. The
 * other moves to a free key if it matters (Minecraft's own keys, Treasure Client's, a few key mod
 * keys like opening Xaero's world map or Iris' shader menu) or is unbound if it's a minor extra.
 *
 * Once (and again whenever FULL goes up) it cleans up every clash, including keys that a modpack's
 * settings or you set earlier (Minecraft only shows a clash in red when one side isn't on its
 * default, so those are the ones you see). After that it only checks bindings it hasn't seen
 * before, such as a mod you add later, and those give way to the keys you already have, so a key
 * you set yourself after the clean-up is never changed. F3 debug keys are left alone (they only
 * work while F3 is held). Every change is written to the game log as "Key clash fixed".
 */
public final class KeyFixer {
    private KeyFixer() {}

    private static boolean done;

    /** Raise this to run the full clean-up again for everyone. */
    private static final int FULL = 4;

    /** Minecraft keys that are rarely used: they give way to Treasure Client's (e.g. Save Hotbar on C vs Zoom). */
    private static final Set<String> MINOR_VANILLA = Set.of("key.saveToolbarActivator", "key.loadToolbarActivator",
        "key.spectatorOutlines", "key.spectatorHotbar", "key.socialInteractions", "key.smoothCamera", "key.advancements");

    /** Mod keys that matter enough to be moved to a free key instead of unbound. */
    private static final List<String> IMPORTANT = List.of(
        "gui.xaero_open_map", "gui.xaero_minimap_settings", "gui.xaero_new_waypoint", "gui.xaero_waypoints_key",
        "gui.xaero_toggle_map", "gui.xaero_open_settings",
        "iris.keybind.reload", "iris.keybind.toggleShaders", "iris.keybind.shaderPackSelection",
        "key.jade.config", "key.jade.show_overlay", "key.jade.toggle_liquid", "key.jade.narrate");

    /** Free keys to move to, in this order (none of these are used by Minecraft itself). */
    private static final String[] SPARE = {"k", "j", "h", "g", "n", "v", "b", "y", "u", "i", "o", "m", "z", "comma",
        "period", "semicolon", "apostrophe", "left.bracket", "right.bracket", "backslash", "keypad.0", "keypad.1",
        "keypad.2", "keypad.3", "keypad.4", "keypad.5", "keypad.6", "keypad.7", "keypad.8", "keypad.9", "f4", "f6",
        "f7", "f8", "f9", "f10", "f12"};

    /** Called every client tick until it has run once. */
    public static void tick(Minecraft mc) {
        // wait until the game has finished loading (every mod has added its keys by then)
        // (the main menu or a world only appears once loading is over)
        if (done || mc.options == null || (net.treasureclient.compat.V.screen() == null && mc.level == null)) return;
        done = true;
        try {
            fix(mc);
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("treasureclient").warn("Key fixer failed", t);
        }
    }

    private static boolean debug(KeyMapping k) {
        return k.getCategory() == KeyMapping.Category.DEBUG;
    }

    private static boolean vanilla(KeyMapping k) {
        return "minecraft".equals(k.getCategory().id().getNamespace());
    }

    /** Lower keeps the key: 0 Minecraft, 1 Treasure Client, 2 minor Minecraft keys, 3 important mod keys, 4 the rest. */
    private static int rank(KeyMapping k) {
        String n = k.getName();
        if (vanilla(k)) return MINOR_VANILLA.contains(n) ? 2 : 0;
        if (n.startsWith("key.treasureclient.")) return 1;
        return IMPORTANT.contains(n) ? 3 : 4;
    }

    private static void fix(Minecraft mc) {
        Config cfg = Config.get();
        Set<String> seen = new HashSet<>(cfg.keysChecked);
        boolean full = cfg.keyFixVersion < FULL;
        KeyMapping[] all = mc.options.keyMappings;
        List<String> log = new ArrayList<>();
        // a few rounds: a key that had to move can only land on a key nothing else uses, so one round
        // is normally enough; the extra rounds are a safety net
        for (int round = 0; round < 4; round++) {
            Map<String, List<KeyMapping>> byKey = new LinkedHashMap<>();
            for (KeyMapping k : all)
                if (!k.isUnbound()) byKey.computeIfAbsent(k.saveString(), x -> new ArrayList<>()).add(k);
            // every key anything is on, F3 debug keys included, counts as taken
            Set<String> used = new HashSet<>(byKey.keySet());
            int changes = 0;
            for (List<KeyMapping> on : byKey.values()) {
                if (on.size() < 2) continue;
                List<KeyMapping> others = new ArrayList<>();  // everything but the F3 debug keys (those never move)
                boolean hasDebug = false;
                for (KeyMapping k : on) {
                    if (debug(k)) hasDebug = true;
                    else others.add(k);
                }
                if (others.isEmpty() || (others.size() == 1 && (!hasDebug || others.get(0).isDefault()))) continue;
                // the ones we may move: all of them in the full clean-up, otherwise only new ones still on their default
                List<KeyMapping> movable = new ArrayList<>();
                for (KeyMapping k : others) if (full || (k.isDefault() && !seen.contains(k.getName()))) movable.add(k);
                if (movable.isEmpty()) continue;
                KeyMapping keep = null;
                if (movable.size() == others.size()) {
                    for (KeyMapping k : movable) {
                        // next to an F3 debug key only a key on its default may stay (Minecraft shows the rest in red)
                        if (hasDebug && !k.isDefault()) continue;
                        if (keep == null || rank(k) < rank(keep) || (rank(k) == rank(keep) && !k.isDefault() && keep.isDefault()))
                            keep = k;
                    }
                }
                for (KeyMapping k : movable) {
                    if (k == keep) continue;
                    String was = k.getTranslatedKeyMessage().getString();
                    changes++;
                    if (rank(k) <= 3) {
                        String to = null;
                        for (String sp : SPARE) if (!used.contains("key.keyboard." + sp)) { to = "key.keyboard." + sp; break; }
                        if (to != null) {
                            k.setKey(InputConstants.getKey(to));
                            used.add(to);
                            log.add(k.getName() + ": " + was + " -> " + k.getTranslatedKeyMessage().getString());
                            continue;
                        }
                    }
                    k.setKey(InputConstants.UNKNOWN);
                    log.add(k.getName() + ": " + was + " -> unbound");
                }
            }
            if (changes == 0) break;
        }
        boolean added = false;
        for (KeyMapping k : all) added |= seen.add(k.getName());
        var logger = org.slf4j.LoggerFactory.getLogger("treasureclient");
        if (!log.isEmpty()) {
            KeyMapping.resetMapping();
            mc.options.save();
            for (String l : log) logger.info("Key clash fixed: {}", l);
        }
        // what Minecraft's Controls screen would still show in red (its own rule: two bindings on the
        // same key, unless both are on their default)
        List<String> red = new ArrayList<>();
        for (KeyMapping k : all) {
            if (k.isUnbound()) continue;
            for (KeyMapping o : all)
                if (o != k && !o.isUnbound() && k.same(o) && !(k.isDefault() && o.isDefault())) {
                    red.add(k.getName() + " (" + k.getTranslatedKeyMessage().getString() + ")");
                    break;
                }
        }
        if (full) logger.info("Key clash clean-up done: {} change(s), {} still clashing", log.size(), red.size());
        if (!red.isEmpty()) logger.info("Keys still clashing (you set these yourself): {}", String.join(", ", red));
        if (added || full || !log.isEmpty()) {
            cfg.keyFixVersion = FULL;
            cfg.keysChecked = new ArrayList<>(seen);
            java.util.Collections.sort(cfg.keysChecked);
            cfg.markDirty();
        }
    }
}
