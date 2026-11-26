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
 * Sorts out key clashes once, the first time the game starts with a key binding we haven't seen:
 * when two bindings are still on their default key and share it, the more important one keeps it.
 * The other moves to a free key if it matters (Minecraft's own keys, Treasure Client's, a few key
 * mod keys like opening Xaero's world map or Iris' shader menu) or is unbound if it's a minor extra.
 *
 * It only ever touches bindings that are still on their default key and that it hasn't checked
 * before, so a key you set yourself is never changed, and a new mod's keys are checked once when
 * it's added (they give way to the keys you already have). F3 debug keys are left alone (they
 * only work while F3 is held). Every change is written to the game log.
 */
public final class KeyFixer {
    private KeyFixer() {}

    private static boolean done;

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
        if (done || mc.options == null) return;
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
        // every non-debug binding that's bound, grouped by the key it's on
        Map<String, List<KeyMapping>> byKey = new LinkedHashMap<>();
        List<KeyMapping> all = new ArrayList<>();
        for (KeyMapping k : mc.options.keyMappings) {
            if (debug(k)) continue;
            all.add(k);
            if (!k.isUnbound()) byKey.computeIfAbsent(k.saveString(), x -> new ArrayList<>()).add(k);
        }
        Set<String> used = new HashSet<>(byKey.keySet());
        List<String> log = new ArrayList<>();
        for (Map.Entry<String, List<KeyMapping>> e : byKey.entrySet()) {
            List<KeyMapping> on = e.getValue();
            if (on.size() < 2) continue;
            // the ones we may move: still on their default key and never checked
            List<KeyMapping> fresh = new ArrayList<>();
            for (KeyMapping k : on) if (k.isDefault() && !seen.contains(k.getName())) fresh.add(k);
            if (fresh.isEmpty()) continue;
            boolean keeperSet = fresh.size() < on.size();  // a key you set (or already had) keeps its place
            KeyMapping keep = null;
            if (!keeperSet) {
                for (KeyMapping k : fresh) if (keep == null || rank(k) < rank(keep)) keep = k;
            }
            for (KeyMapping k : fresh) {
                if (k == keep) continue;
                String was = k.getTranslatedKeyMessage().getString();
                if (rank(k) <= 3) {
                    String to = null;
                    for (String s : SPARE) if (!used.contains("key.keyboard." + s)) { to = "key.keyboard." + s; break; }
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
        boolean added = false;
        for (KeyMapping k : all) added |= seen.add(k.getName());
        if (!log.isEmpty()) {
            KeyMapping.resetMapping();
            mc.options.save();
            for (String l : log) org.slf4j.LoggerFactory.getLogger("treasureclient").info("Key clash fixed: {}", l);
        }
        if (added || !log.isEmpty()) {
            cfg.keysChecked = new ArrayList<>(seen);
            java.util.Collections.sort(cfg.keysChecked);
            cfg.markDirty();
        }
    }
}
