package net.treasureclient.ui;

import net.minecraft.resources.Identifier;
import net.treasureclient.compat.Gfx;
import net.treasureclient.core.Theme;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Treasure Client look for every Minecraft widget (the same style as the browser client's buttons):
 * rounded, a hollow outline in the theme colour that fills smoothly on hover, dark text once filled.
 * Minecraft draws each widget's frame by asking for a named sprite (widget/button_highlighted, ...);
 * a hook sends those here instead, so every screen (and other mods' screens) gets the look.
 */
public final class Widgets {
    private Widgets() {}

    /** Theme palettes from the browser client: accent, accent deep, idle text, text on fill, panel. */
    private static int[] palette() {
        return switch (Theme.current().id()) {
            case "tropical" -> new int[]{0xFF45E3C6, 0xFF1AA7B0, 0xFFF1E6C6, 0xFF042024, 0xE6071D22};
            case "frost" -> new int[]{0xFF7FE9FF, 0xFF2EC4F1, 0xFFDFF6FF, 0xFF03101A, 0xE6031019};
            case "terracotta" -> new int[]{0xFFDB7A3C, 0xFFB85C2A, 0xFFF3E0CC, 0xFF2A1205, 0xE6241206};
            case "forest" -> new int[]{0xFF6FCB5B, 0xFF2F8F3E, 0xFFE8F6D8, 0xFF07200C, 0xE60C2410};
            case "sakura" -> new int[]{0xFFFF9CC6, 0xFFE8679F, 0xFFFBEAF1, 0xFF2A0A18, 0xE6220C18};
            case "dark" -> new int[]{0xFFB9B4E8, 0xFF8580C4, 0xFFECEAF4, 0xFF0C0B14, 0xE60E0E12};
            default -> new int[]{0xFFFFD76B, 0xFFD4A537, 0xFFF6ECD2, 0xFF0F0A05, 0xE6140D06};
        };
    }

    private static final int DISABLED_TEXT = 0xFF6A6A6A;

    /** The widget being drawn right now (set by a hook around every widget's render). */
    private static Object current;
    /** Label colour for the current widget (0 = leave Minecraft's), and whether to drop the shadow. */
    private static int labelColor;
    private static boolean labelNoShadow;
    private static final Map<Object, float[]> HOVER = new WeakHashMap<>();

    /** Widgets can draw inside other widgets (buttons inside a settings list), so keep a stack. */
    private static final java.util.ArrayDeque<Object> STACK = new java.util.ArrayDeque<>();

    public static void begin(Object widget) {
        if (current != null) STACK.push(current);
        current = widget;
        labelColor = 0;
        labelNoShadow = false;
    }

    public static void end() {
        current = STACK.isEmpty() ? null : STACK.pop();
        labelColor = 0;
        labelNoShadow = false;
    }

    /** Minecraft's label colour, swapped for ours while a themed widget is drawing its text. */
    public static int label(int original) {
        if (labelColor == 0) return original;
        int a = (original >>> 24) * (labelColor >>> 24) / 255;
        return a << 24 | (labelColor & 0xFFFFFF);
    }

    public static boolean labelShadow(boolean original) {
        return labelColor != 0 && labelNoShadow ? false : original;
    }

    /** Smooth 0..1 hover fill per widget (~140 ms, like the browser client). */
    private static float fill(boolean hovered) {
        Object key = current != null ? current : "_";
        float[] st = HOVER.computeIfAbsent(key, k -> new float[]{0, 0});
        float now = System.nanoTime() / 1e9f;
        float dt = st[1] == 0 ? 0 : Math.min(0.05f, now - st[1]);
        st[1] = now;
        float target = hovered ? 1 : 0, speed = dt / 0.14f;
        if (st[0] < target) st[0] = Math.min(target, st[0] + speed);
        else if (st[0] > target) st[0] = Math.max(target, st[0] - speed);
        return st[0];
    }

    /** Draws the themed version of a widget sprite; false = not one of ours (Minecraft draws it). */
    public static boolean sprite(Identifier id, int x, int y, int w, int h) {
        if (!id.getNamespace().equals("minecraft")) return false;
        String p = id.getPath();
        if (!p.startsWith("widget/")) return false;
        p = p.substring(7);
        int[] c = palette();
        switch (p) {
            case "button", "button_highlighted" -> {
                float f = fill(p.endsWith("_highlighted"));
                roundedRect(x, y, w, h, Theme.lerp(c[4], c[0], f));
                if (f > 0.01f) Gfx.fill(x + 1, y + h / 2, x + w - 1, y + h - 2, ((int) (0x55 * f)) << 24 | (c[1] & 0xFFFFFF));
                roundedOutline(x, y, w, h, Theme.lerp(c[0], 0xFFFFFFFF, f * 0.25f));
                labelColor = Theme.lerp(c[2], c[3], f);
                labelNoShadow = f >= 0.5f;
            }
            case "button_disabled" -> {
                roundedRect(x, y, w, h, 0x99000000);
                roundedOutline(x, y, w, h, 0xFF3A3A3A);
                labelColor = DISABLED_TEXT;
            }
            case "slider", "slider_highlighted" -> {
                boolean hi = p.endsWith("_highlighted");
                roundedRect(x, y, w, h, c[4]);
                roundedOutline(x, y, w, h, hi ? Theme.lerp(c[0], 0xFFFFFFFF, .25f) : c[0]);
                labelColor = c[2];
            }
            case "slider_handle", "slider_handle_highlighted" -> {
                boolean hi = p.endsWith("_highlighted");
                roundedRect(x + 1, y + 1, w - 2, h - 2, hi ? c[0] : c[1]);
                roundedOutline(x + 1, y + 1, w - 2, h - 2, Theme.lerp(c[0], 0xFFFFFFFF, hi ? .45f : .2f));
            }
            case "text_field", "text_field_highlighted" -> {
                boolean focus = p.endsWith("_highlighted");
                roundedRect(x, y, w, h, 0xD9050505);
                roundedOutline(x, y, w, h, focus ? c[0] : Theme.alpha(c[0], .45f));
            }
            case "checkbox", "checkbox_highlighted", "checkbox_selected", "checkbox_selected_highlighted" -> {
                boolean hi = p.endsWith("_highlighted"), on = p.contains("selected");
                if (on) {
                    roundedRect(x, y, w, h, hi ? c[0] : c[1]);
                    check(x, y, w, h, c[3]);
                } else roundedRect(x, y, w, h, c[4]);
                roundedOutline(x, y, w, h, hi ? Theme.lerp(c[0], 0xFFFFFFFF, .25f) : c[0]);
                labelColor = c[2];
            }
            case "tab", "tab_highlighted", "tab_selected", "tab_selected_highlighted" -> {
                boolean hi = p.endsWith("_highlighted"), sel = p.startsWith("tab_selected");
                Gfx.fill(x, y, x + w, y + h, sel ? c[4] : hi ? 0x80000000 : 0x55000000);
                if (sel) Gfx.fill(x + 2, y + h - 2, x + w - 2, y + h, c[0]);
                else if (hi) Gfx.fill(x + 2, y + h - 1, x + w - 2, y + h, Theme.alpha(c[0], .6f));
                labelColor = sel ? c[0] : c[2];
            }
            case "scroller" -> roundedRect(x + 1, y, Math.max(1, w - 2), h, c[0]);
            case "scroller_background" -> Gfx.fill(x, y, x + w, y + h, 0x66000000);
            default -> {
                return false;
            }
        }
        if (current == null) { // drawn outside a widget: don't recolour whatever text comes next
            labelColor = 0;
            labelNoShadow = false;
        }
        return true;
    }

    /** A tick mark for a selected checkbox, drawn as little squares. */
    private static void check(int x, int y, int w, int h, int col) {
        float s = Math.max(1, w / 9f);
        float[][] pts = {{2, 5}, {3, 6}, {4, 7}, {5, 6}, {6, 5}, {7, 4}, {8, 3}};
        for (float[] q : pts) {
            int px = x + Math.round(q[0] * w / 10f - s / 2), py = y + Math.round(q[1] * h / 10f - s / 2);
            Gfx.fill(px, py, px + Math.round(s * 1.3f), py + Math.round(s * 1.3f), col);
        }
    }

    /** Rounded rectangle the browser client's way (rows inset at the corners). */
    public static void roundedRect(int x, int y, int w, int h, int col) {
        if (w <= 0 || h <= 0) return;
        int x2 = x + w, y2 = y + h;
        if (h < 5 || w < 5) {
            Gfx.fill(x, y, x2, y2, col);
            return;
        }
        Gfx.fill(x, y + 2, x2, y2 - 2, col);
        Gfx.fill(x + 2, y, x2 - 2, y + 1, col);
        Gfx.fill(x + 1, y + 1, x2 - 1, y + 2, col);
        Gfx.fill(x + 2, y2 - 1, x2 - 2, y2, col);
        Gfx.fill(x + 1, y2 - 2, x2 - 1, y2 - 1, col);
    }

    public static void roundedOutline(int x, int y, int w, int h, int col) {
        if (w <= 0 || h <= 0) return;
        int x2 = x + w, y2 = y + h;
        Gfx.fill(x + 2, y, x2 - 2, y + 1, col);
        Gfx.fill(x + 2, y2 - 1, x2 - 2, y2, col);
        Gfx.fill(x, y + 2, x + 1, y2 - 2, col);
        Gfx.fill(x2 - 1, y + 2, x2, y2 - 2, col);
        Gfx.fill(x + 1, y + 1, x + 2, y + 2, col);
        Gfx.fill(x2 - 2, y + 1, x2 - 1, y + 2, col);
        Gfx.fill(x + 1, y2 - 2, x + 2, y2 - 1, col);
        Gfx.fill(x2 - 2, y2 - 2, x2 - 1, y2 - 1, col);
    }
}
