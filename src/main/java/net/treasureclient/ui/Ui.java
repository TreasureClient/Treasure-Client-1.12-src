package net.treasureclient.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.treasureclient.compat.Gfx;
import net.treasureclient.core.Mods;
import net.treasureclient.core.Theme;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws the 1.5 look in "CSS pixels": the browser client's sizes, laid out on a 1366 x 768 design
 * and scaled to the window, so every screen keeps the same proportions as Treasure Client in the browser.
 * Text is drawn at its true pixel size from fonts made at many sizes (Minecraft samples font
 * textures without smoothing, so scaling one big font down would look jagged).
 */
public final class Ui {
    private Ui() {}

    public static final String WORD = "word", BRAND = "brand", HUD = "hud", SEMI = "semi", TEXT = "text", MONO = "mono", MONOB = "monob";

    private static final Map<String, int[]> SIZES = new HashMap<>();
    static {
        SIZES.put("word", new int[]{20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50, 52, 55, 58, 61, 64, 67, 70, 74, 78, 82, 86, 90, 94, 99, 104, 109, 114, 120, 126, 132, 139, 146, 150});
        SIZES.put("brand", new int[]{9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48});
        SIZES.put("hud", new int[]{8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48});
        SIZES.put("semi", new int[]{8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48});
        SIZES.put("text", new int[]{8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48});
        SIZES.put("mono", new int[]{6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 32, 34, 36});
        SIZES.put("monob", new int[]{6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 32, 34, 36});
    }

    /** Window pixels per CSS pixel, and the screen size in CSS pixels. */
    public static float k = 1f;
    public static float vw = 1366, vh = 768;
    private static int guiScale = 1;

    /** Starts drawing in CSS pixels (call end() when done). */
    public static void begin() {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
        guiScale = Math.max(1, mc.getWindow().getGuiScale());
        k = Math.max(0.55f, Math.min(4f, Math.min(w / 1366f, h / 768f)));
        vw = w / k;
        vh = h / k;
        Gfx.pose().pushMatrix();
        Gfx.pose().scale(1f / guiScale, 1f / guiScale);
    }

    public static void end() {
        Gfx.pose().popMatrix();
    }

    /** Mouse position (Minecraft's GUI units) in CSS pixels. */
    public static float mx(double guiX) {
        return (float) (guiX * guiScale / k);
    }

    public static float my(double guiY) {
        return (float) (guiY * guiScale / k);
    }

    private static int p(float css) {
        return Math.round(css * k);
    }

    // ---------------- shapes ----------------
    public static void rect(float x, float y, float w, float h, int argb) {
        if (((argb >>> 24) & 0xFF) == 0 || w <= 0 || h <= 0) return;
        int x0 = p(x), y0 = p(y), x1 = p(x + w), y1 = p(y + h);
        if (x1 > x0 && y1 > y0) Gfx.fill(x0, y0, x1, y1, argb);
    }

    /** A rectangle with round corners (radius in CSS px), drawn as rows of pixels. */
    public static void round(float x, float y, float w, float h, float radius, int argb) {
        if (((argb >>> 24) & 0xFF) == 0) return;
        int x0 = p(x), y0 = p(y), x1 = p(x + w), y1 = p(y + h);
        int r = Math.min(Math.round(radius * k), Math.min((x1 - x0) / 2, (y1 - y0) / 2));
        if (r <= 0) {
            Gfx.fill(x0, y0, x1, y1, argb);
            return;
        }
        Gfx.fill(x0, y0 + r, x1, y1 - r, argb);
        for (int i = 0; i < r; i++) {
            float dy = r - i - 0.5f;
            int inset = Math.round(r - (float) Math.sqrt(Math.max(0, r * r - dy * dy)));
            Gfx.fill(x0 + inset, y0 + i, x1 - inset, y0 + i + 1, argb);
            Gfx.fill(x0 + inset, y1 - i - 1, x1 - inset, y1 - i, argb);
        }
    }

    public static void circle(float cx, float cy, float radius, int argb) {
        round(cx - radius, cy - radius, radius * 2, radius * 2, radius, argb);
    }

    public static void gradientV(float x, float y, float w, float h, int top, int bottom) {
        int x0 = p(x), y0 = p(y), x1 = p(x + w), y1 = p(y + h);
        if (x1 > x0 && y1 > y0) Gfx.gradient(x0, y0, x1, y1, top, bottom);
    }

    /** A left-to-right gradient through colour stops (positions 0..1), drawn as thin columns. */
    public static void gradientH(float x, float y, float w, float h, float[] stops, int[] colors) {
        int x0 = p(x), y0 = p(y), x1 = p(x + w), y1 = p(y + h);
        int cols = Math.max(1, Math.min(96, (x1 - x0) / 6));
        for (int i = 0; i < cols; i++) {
            int a = x0 + (x1 - x0) * i / cols, b = x0 + (x1 - x0) * (i + 1) / cols;
            float t = (i + 0.5f) / cols;
            int c = colors[colors.length - 1];
            for (int s = 0; s + 1 < stops.length; s++) {
                if (t <= stops[s + 1]) {
                    float u = (t - stops[s]) / Math.max(1e-4f, stops[s + 1] - stops[s]);
                    c = Theme.lerp(colors[s], colors[s + 1], u);
                    break;
                }
            }
            if (b > a) Gfx.fill(a, y0, b, y1, c);
        }
    }

    // ---------------- images ----------------
    public static final Identifier ICONS = id("textures/gui/icons.png");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("treasureclient", path);
    }

    public static void image(Identifier tex, float x, float y, float w, float h, int texW, int texH, int argb) {
        image(tex, x, y, w, h, 0, 0, texW, texH, texW, texH, argb);
    }

    public static void image(Identifier tex, float x, float y, float w, float h, float u, float v, int uw, int vh, int texW, int texH, int argb) {
        int x0 = p(x), y0 = p(y), pw = p(x + w) - x0, ph = p(y + h) - y0;
        if (pw <= 0 || ph <= 0) return;
        Gfx.blit(tex, x0, y0, u, v, pw, ph, uw, vh, texW, texH, argb);
    }

    /** A Mod Menu icon (white line drawing, tinted). */
    public static void icon(String key, float x, float y, float size, int argb) {
        int i = Mods.ICONS.indexOf(key);
        if (i < 0) return;
        image(ICONS, x, y, size, size, (i % 8) * 64, (i / 8) * 64, 64, 64, 512, 448, argb);
    }

    /** An item's icon {@code size} CSS px wide at (x, y). */
    public static void item(net.minecraft.world.item.ItemStack stack, float x, float y, float size) {
        if (stack == null || stack.isEmpty()) return;
        var pose = Gfx.pose();
        pose.pushMatrix();
        pose.translate(x * k, y * k);
        float s = size * k / 16f;
        pose.scale(s, s);
        Gfx.item(stack, 0, 0);
        pose.popMatrix();
    }

    public static Identifier chest() {
        return id("textures/gui/chest_" + Theme.current().id() + ".png");
    }

    // ---------------- text ----------------
    /**
     * Every text drawn is remembered per font and size: its Component (and, for letter-spaced text,
     * one per letter) and its width, so drawing the same words every frame only costs a lookup.
     */
    private static final class Txt {
        final Component whole;
        float w = -1;
        Component[] chars;
        float[] charW;
        float charSum;

        Txt(Component whole) {
            this.whole = whole;
        }
    }

    private static final int SLOTS = 160;
    @SuppressWarnings("unchecked")
    private static final HashMap<String, Txt>[] TEXTS = new HashMap[7 * SLOTS];
    private static final Style[] STYLES = new Style[7 * SLOTS];
    private static Object fontSeen;

    private static int faceNo(String face) {
        return switch (face) {
            case WORD -> 0;
            case BRAND -> 1;
            case HUD -> 2;
            case SEMI -> 3;
            case TEXT -> 4;
            case MONO -> 5;
            default -> 6;
        };
    }

    private static int pick(String face, float px) {
        int[] s = SIZES.get(face);
        int best = s[0];
        for (int v : s) if (Math.abs(v - px) < Math.abs(best - px)) best = v;
        return best;
    }

    private static Txt txt(String face, int size, String text) {
        var font = Minecraft.getInstance().font;
        if (font != fontSeen) { // a new font (resource reload): measure everything again
            fontSeen = font;
            java.util.Arrays.fill(TEXTS, null);
        }
        int slot = faceNo(face) * SLOTS + Math.min(SLOTS - 1, size);
        HashMap<String, Txt> m = TEXTS[slot];
        if (m == null) TEXTS[slot] = m = new HashMap<>();
        Txt t = m.get(text);
        if (t == null) {
            if (m.size() > 1500) m.clear(); // changing numbers (coordinates...) would pile up
            t = new Txt(Component.literal(text).withStyle(style(face, size, slot)));
            m.put(text, t);
        }
        return t;
    }

    private static Style style(String face, int size, int slot) {
        Style st = STYLES[slot];
        if (st == null) STYLES[slot] = st = Style.EMPTY.withFont(new FontDescription.Resource(id(face + "_" + size)));
        return st;
    }

    private static float wholeW(Txt t) {
        if (t.w < 0) t.w = Minecraft.getInstance().font.width(t.whole);
        return t.w;
    }

    private static void letters(Txt t, String face, int size, String text) {
        if (t.chars != null) return;
        var font = Minecraft.getInstance().font;
        Style st = style(face, size, faceNo(face) * SLOTS + Math.min(SLOTS - 1, size));
        int n = text.codePointCount(0, text.length());
        Component[] cs = new Component[n];
        float[] ws = new float[n];
        float sum = 0;
        for (int i = 0, j = 0; i < text.length(); j++) {
            int cp = text.codePointAt(i);
            cs[j] = Component.literal(new String(Character.toChars(cp))).withStyle(st);
            ws[j] = font.width(cs[j]);
            sum += ws[j];
            i += Character.charCount(cp);
        }
        t.charW = ws;
        t.charSum = sum;
        t.chars = cs;
    }

    public static float asc(String face) {
        return face.startsWith("mono") ? 1.02f : 0.878f;
    }

    private static float desc(String face) {
        return face.startsWith("mono") ? 0.30f : 0.21f;
    }

    /** The CSS "line-height: normal" height of a font at a size. */
    public static float lineH(String face, float size) {
        return (asc(face) + desc(face)) * size;
    }

    /** Width in CSS px of text in a font (letterSpacing in em, like CSS). */
    public static float width(String face, String text, float size, float letterSpacing) {
        if (text.isEmpty()) return 0;
        float px = size * k;
        int sz = pick(face, px);
        float scale = px / sz;
        Txt t = txt(face, sz, text);
        if (letterSpacing == 0) return wholeW(t) * scale / k;
        letters(t, face, sz, text);
        return (t.charSum * scale + t.chars.length * letterSpacing * px) / k;
    }

    public static float width(String face, String text, float size) {
        return width(face, text, size, 0);
    }

    /**
     * Draws text with its CSS line box starting at (x, y). lineHeight is a CSS line-height
     * factor (0 = "normal"). Returns the width drawn, in CSS px.
     */
    public static float text(String face, String text, float x, float y, float size, int argb, float letterSpacing, float lineHeight) {
        if (text.isEmpty() || ((argb >>> 24) & 0xFF) < 4) return 0;
        float px = size * k;
        int sz = pick(face, px);
        float scale = px / sz;
        float a = asc(face), d = desc(face);
        float lh = lineHeight > 0 ? lineHeight : a + d;
        float baseline = y + (lh * size - (a + d) * size) / 2f + a * size;
        Txt t = txt(face, sz, text);
        Gfx.pose().pushMatrix();
        Gfx.pose().translate(Math.round(x * k), Math.round(baseline * k));
        if (Math.abs(scale - 1f) > 0.002f) Gfx.pose().scale(scale, scale);
        float w;
        if (letterSpacing == 0) {
            Gfx.text(t.whole, 0, -7, argb);
            w = wholeW(t) * scale;
        } else {
            letters(t, face, sz, text);
            float cx = 0, gap = letterSpacing * px / scale;
            for (int i = 0; i < t.chars.length; i++) {
                Gfx.text(t.chars[i], Math.round(cx), -7, argb);
                cx += t.charW[i] + gap;
            }
            w = cx * scale;
        }
        Gfx.pose().popMatrix();
        return w / k;
    }

    public static float text(String face, String text, float x, float y, float size, int argb) {
        return text(face, text, x, y, size, argb, 0, 0);
    }

    /** Draws text so its baseline sits at baselineY (for mixing fonts on one line, like CSS inline text). */
    public static float textBase(String face, String text, float x, float baselineY, float size, int argb, float letterSpacing) {
        return text(face, text, x, baselineY - asc(face) * size, size, argb, letterSpacing, 0);
    }

    /**
     * A small mono button (Back, Edit HUD, Close) with a dark backing and a thin outline, so it reads on
     * any background picture. (x, y) is its top-left; it is lineH + 8 tall. Returns its width.
     */
    public static float chip(String label, float x, float y, float size, boolean hover, int accent, float alpha) {
        float w = chipW(label, size), h = chipH(size);
        round(x, y, w, h, 4, Theme.alpha(hover ? accent : 0x59ECEBE6, alpha));
        round(x + 1, y + 1, w - 2, h - 2, 3, Theme.alpha(hover ? 0xF0141418 : 0xD90C0C0F, alpha));
        mono(label, x + 10, y + 4, size, Theme.alpha(Theme.INK, alpha), .06f);
        return w;
    }

    public static float chipW(String label, float size) {
        return width(MONO, label.toUpperCase(java.util.Locale.ROOT), size, .06f) + 20;
    }

    public static float chipH(float size) {
        return lineH(MONO, size) + 8;
    }

    /** The small uppercase mono labels used all over the 1.5 look. */
    public static float mono(String text, float x, float y, float size, int argb, float letterSpacing) {
        return text(MONO, text.toUpperCase(), x, y, size, argb, letterSpacing, 0);
    }

    // ---------------- clipping ----------------
    public static void clip(float x, float y, float w, float h) {
        Gfx.scissor(p(x), p(y), p(x + w), p(y + h));
    }

    public static void unclip() {
        Gfx.unscissor();
    }

    public static boolean in(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
