package net.treasureclient.ui;

import net.minecraft.resources.Identifier;
import net.treasureclient.core.Config;
import net.treasureclient.core.Theme;

/**
 * Which background picture each theme uses (any of the seven; each theme starts on its own) and
 * whether it's blurred ("Blur background", off by default: the picture stays sharp). The same
 * choices as the browser editions' theme settings.
 */
public final class Backgrounds {
    private Backgrounds() {}

    public static final String[] IDS = {"treasure", "frost", "tropical", "terracotta", "forest", "sakura", "dark"};
    public static final String[] NAMES = {"Treasure", "Frost", "Tropical", "Terracotta", "Forest", "Sakura", "Dark"};

    /** The theme's own picture. */
    public static String own(Theme t) {
        return t.id().equals("normal") ? "treasure" : t.id();
    }

    /** The picture picked for this theme. */
    public static String of(Theme t) {
        String v = Config.get().str("bg." + t.id(), "");
        for (String id : IDS) if (id.equals(v)) return v;
        return own(t);
    }

    public static void set(Theme t, String id) {
        Config.get().put("bg." + t.id(), id);
    }

    public static boolean blur(Theme t) {
        return Config.get().flag("bgblur." + t.id(), false);
    }

    public static void setBlur(Theme t, boolean on) {
        Config.get().put("bgblur." + t.id(), on);
    }

    /** 1024 x 512. */
    public static Identifier sharp(String id) {
        return Ui.id("textures/gui/bg/" + id + ".png");
    }

    /** 512 x 256 (made by tools/Blur.java). */
    public static Identifier blurred(String id) {
        return Ui.id("textures/gui/bg_blur/" + id + ".png");
    }
}
