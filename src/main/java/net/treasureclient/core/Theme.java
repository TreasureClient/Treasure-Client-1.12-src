package net.treasureclient.core;

import java.util.List;

/** The seven Treasure Client themes, with the same colours as the browser client (1.5). */
public record Theme(String id, String name, int hi, int a1, int a2, int a3, int txt, int dim, int bg0) {

    public static final List<Theme> ALL = List.of(
        new Theme("normal", "Treasure", 0xFFFFF3C4, 0xFFFFD76B, 0xFFD4A537, 0xFF8A5A20, 0xFFF6ECD2, 0xFFC9B183, 0xFF0F0A05),
        new Theme("frost", "Frost", 0xFFEAF7FF, 0xFFBFE6FF, 0xFF6FC3EC, 0xFF2F6F9C, 0xFFEEF7FF, 0xFFA9CADD, 0xFF07131F),
        new Theme("tropical", "Tropical", 0xFFD9FFF6, 0xFF45E3C6, 0xFF1FB6BA, 0xFF0E6D78, 0xFFF3ECD6, 0xFFD8C38F, 0xFF05171A),
        new Theme("terracotta", "Terracotta", 0xFFFFCF9E, 0xFFE8863A, 0xFFC05F26, 0xFF7A3D1D, 0xFFF6E6D4, 0xFFD8AD84, 0xFF1A0E06),
        new Theme("forest", "Forest", 0xFFD6F8C2, 0xFF6FCB5B, 0xFF3D9A3F, 0xFF215A27, 0xFFEAF6DD, 0xFFAECB96, 0xFF07160A),
        new Theme("sakura", "Sakura", 0xFFFFE3EF, 0xFFFF9CC6, 0xFFE8679F, 0xFF9C3A66, 0xFFFBEAF1, 0xFFDDB0C4, 0xFF1A0A12),
        new Theme("dark", "Dark", 0xFFF0EEFC, 0xFFB9B4E8, 0xFF8580C4, 0xFF4A4678, 0xFFECEAF4, 0xFFA9A6BD, 0xFF08080C));

    public static Theme byId(String id) {
        for (Theme t : ALL) if (t.id.equals(id)) return t;
        return ALL.get(0);
    }

    public static Theme current() {
        return byId(Config.get().theme);
    }

    // The 1.5 look's neutral colours (--t15-ink, --t15-dim, --t15-faint, the HUD box background)
    public static final int INK = 0xFFECEBE6;
    public static final int INK_DIM = 0xA8ECEBE6;
    public static final int INK_FAINT = 0x21ECEBE6;
    public static final int HUD_BG = 0x8509090B;

    /** The colour with a new alpha (0..1). */
    public static int alpha(int argb, float a) {
        int al = Math.round(Math.max(0, Math.min(1, a)) * ((argb >>> 24) & 0xFF));
        return (al << 24) | (argb & 0xFFFFFF);
    }

    public static int lerp(int c0, int c1, float t) {
        t = Math.max(0, Math.min(1, t));
        int a = (int) (((c0 >>> 24) & 0xFF) + (((c1 >>> 24) & 0xFF) - ((c0 >>> 24) & 0xFF)) * t);
        int r = (int) (((c0 >> 16) & 0xFF) + (((c1 >> 16) & 0xFF) - ((c0 >> 16) & 0xFF)) * t);
        int g = (int) (((c0 >> 8) & 0xFF) + (((c1 >> 8) & 0xFF) - ((c0 >> 8) & 0xFF)) * t);
        int b = (int) ((c0 & 0xFF) + ((c1 & 0xFF) - (c0 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
