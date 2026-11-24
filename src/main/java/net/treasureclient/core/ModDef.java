package net.treasureclient.core;

import java.util.List;

/**
 * One mod in the Mod Menu: its key (the same keys the browser client uses), name, one-line
 * description, tab (hud / render / perf / misc), icon and settings.
 */
public record ModDef(String key, String name, String desc, String cat, String icon, boolean defaultOn, List<Setting> settings) {

    public boolean hasSettings() {
        return !settings.isEmpty();
    }

    /** One row on a mod's settings page. */
    public sealed interface Setting permits Toggle, Choice, Slider {
        String key();
        String label();
    }

    /** An on/off switch. */
    public record Toggle(String key, String label, boolean def) implements Setting {}

    /** A row of buttons: one of several values. */
    public record Choice(String key, String label, List<String> values, List<String> labels, String def) implements Setting {}

    /** A number between min and max. */
    public record Slider(String key, String label, int min, int max, int step, int def, String unit) implements Setting {}
}
