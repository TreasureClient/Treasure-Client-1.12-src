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
        image(ICONS, x, y, size, size, (i % 8) * 64, (i / 8) * 64, 64, 64, 512, 384, argb);
    }

    public static Identifier chest() {
        return id("textures/gui/chest_" + Theme.current().id() + ".png");
    }

    // ---------------- text ----------------
    private static final Map<String, Component> CACHE = new HashMap<>();

    private static int pick(String face, float px) {
        int[] s = SIZES.get(face);
        int best = s[0];
        for (int v : s) if (Math.abs(v - px) < Math.abs(best - px)) best = v;
        return best;
    }

    private static Component comp(String face, int size, String text) {
        String key = face + size + '\u0000' + text;
        Component c = CACHE.get(key);
        if (c == null) {
            if (CACHE.size() > 4000) CACHE.clear();
            Style st = Style.EMPTY.withFont(new FontDescription.Resource(id(face + "_" + size)));
            c = Component.literal(text).withStyle(st);
            CACHE.put(key, c);
        }
        return c;
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
        var font = Minecraft.getInstance().font;
        float w;
        if (letterSpacing == 0) w = font.width(comp(face, sz, text)) * scale;
        else {
            w = 0;
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                String ch = new String(Character.toChars(cp));
                w += font.width(comp(face, sz, ch)) * scale + letterSpacing * px;
                i += Character.charCount(cp);
            }
        }
        return w / k;
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
        var font = Minecraft.getInstance().font;
        Gfx.pose().pushMatrix();
        Gfx.pose().translate(Math.round(x * k), Math.round(baseline * k));
        if (Math.abs(scale - 1f) > 0.002f) Gfx.pose().scale(scale, scale);
        float w;
        if (letterSpacing == 0) {
            Component c = comp(face, sz, text);
            Gfx.text(c, 0, -7, argb);
            w = font.width(c) * scale;
        } else {
            float cx = 0;
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                Component c = comp(face, sz, new String(Character.toChars(cp)));
                Gfx.text(c, Math.round(cx), -7, argb);
                cx += font.width(c) + letterSpacing * px / scale;
                i += Character.charCount(cp);
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
