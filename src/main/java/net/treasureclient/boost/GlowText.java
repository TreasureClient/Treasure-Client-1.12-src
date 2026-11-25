package net.treasureclient.boost;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.EmptyArea;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.util.FormattedCharSequence;

/**
 * Glowing Sign Text. Glow-inked sign text has a coloured outline, and every frame, for every glowing
 * line on screen, the game builds that outline letter by letter 8 times over (once per direction it's
 * shifted), plus the text on top once more. Text with many or unusual characters makes this slow
 * (Mojang bug MC-304967).
 *
 * The sign's text object stays the same until the sign is edited, so the finished outline and text
 * are kept and drawn again as they are. Where it's drawn, the lighting and the angle still come fresh
 * every frame. Kept text is made fresh when the fonts change (resource packs, "Force Unicode Font"),
 * and at least once a second anyway. Scrambled text (the "magic" style, which changes every frame)
 * and text with player heads or picture glyphs is never kept.
 *
 * 26.2+: the game prepares the outline (Font.prepare8xTextOutline) before drawing, so that's kept.
 * 1.21.11 and 26.1: the game draws it straight away (Font.drawInBatch8xOutline), so the letters it
 * hands to the drawer are written down once and handed over again next time.
 */
public final class GlowText {
    private GlowText() {}

    private static final long KEEP_NS = 1_000_000_000L;
    private static final long FORGET_NS = 5_000_000_000L;

    static final class Entry {
        Font font;
        float x, y;
        int color, outline, innerColor;
        boolean noCache, ready;
        int epoch;
        long at, used;
        /** 26.2+: Font.PreparedText. 1.21.11 / 26.1: List of the letters, effects and gaps handed over. */
        Object outlined, inner;
    }

    private static final IdentityHashMap<FormattedCharSequence, Entry> MAP = new IdentityHashMap<>();
    private static volatile int epoch;
    private static Thread owner;
    private static long sweptAt;

    /** The fonts were loaded again or changed: nothing kept may be drawn again. */
    public static void fontsChanged() {
        epoch++;
    }

    private static boolean mine() {
        Thread t = Thread.currentThread();
        if (owner == null) owner = t;
        return owner == t;
    }

    /** Only plain text is kept: no scrambled letters, no player heads or picture glyphs. */
    private static boolean plain(FormattedCharSequence seq) {
        boolean[] bad = {false};
        seq.accept((i, style, cp) -> {
            if (style.isObfuscated() || !(style.getFont() instanceof FontDescription.Resource)) bad[0] = true;
            return !bad[0];
        });
        return !bad[0];
    }

    /**
     * The kept entry for this line if it can be used as it is, otherwise null. Sets {@code fill}
     * to the entry to fill in (or null when this line isn't kept).
     */
    private static Entry fill;

    private static Entry look(Font font, FormattedCharSequence seq, float x, float y, int color, int outline) {
        fill = null;
        if (!Boosts.ACTIVE[Boosts.GLOW_TEXT] || seq == null || !mine()) return null;
        long now = System.nanoTime();
        if (now - sweptAt > KEEP_NS) sweep(now);
        Entry e = MAP.get(seq);
        if (e != null) {
            if (e.noCache) return null;
            if (e.ready && e.font == font && e.x == x && e.y == y && e.color == color && e.outline == outline
                && e.epoch == epoch && now - e.at < KEEP_NS + (System.identityHashCode(seq) & 255) * 1_000_000L) {
                e.used = now;
                Boosts.ran(Boosts.GLOW_TEXT);
                return e;
            }
        } else {
            if (MAP.size() >= 2048) MAP.clear();
            e = new Entry();
            MAP.put(seq, e);
            if (!plain(seq)) {
                e.noCache = true;
                return null;
            }
        }
        e.font = font;
        e.x = x;
        e.y = y;
        e.color = color;
        e.outline = outline;
        e.epoch = epoch;
        e.at = e.used = now;
        e.ready = false;
        e.outlined = e.inner = null;
        fill = e;
        return null;
    }

    private static void sweep(long now) {
        sweptAt = now;
        for (Iterator<Entry> it = MAP.values().iterator(); it.hasNext(); ) {
            if (now - it.next().used > FORGET_NS) it.remove(); // signs out of sight for a while
        }
    }

    // ------------------------------------------------------------------ 26.2+: prepared text

    private static Entry last;

    /** The outline (Font.prepare8xTextOutline). */
    public static Font.PreparedText outline(Font font, FormattedCharSequence seq, float x, float y, int outline,
                                            Function<Object, Font.PreparedText> make) {
        last = null;
        try {
            Entry e = look(font, seq, x, y, 0, outline);
            if (e != null) {
                last = e;
                return (Font.PreparedText) e.outlined;
            }
            Font.PreparedText p = make.apply(null);
            if (fill != null) {
                fill.outlined = p;
                fill.ready = p != null;
                last = fill;
            }
            fill = null;
            return p;
        } catch (Throwable t) {
            Boosts.failed(Boosts.GLOW_TEXT, t);
            last = null;
            return make.apply(null);
        }
    }

    /** The text drawn on top of the outline (the Font.prepareText call right after it). */
    public static Font.PreparedText inner(FormattedCharSequence seq, float x, float y, int color,
                                          Function<Object, Font.PreparedText> make) {
        Entry e = last;
        last = null;
        if (e == null || !Boosts.ACTIVE[Boosts.GLOW_TEXT] || MAP.get(seq) != e || e.x != x || e.y != y) return make.apply(null);
        if (e.inner != null && e.innerColor == color) return (Font.PreparedText) e.inner;
        Font.PreparedText p = make.apply(null);
        e.inner = p;
        e.innerColor = color;
        return p;
    }

    // ------------------------------------------------------------------ 1.21.11 / 26.1: drawn straight away

    private static Entry recording;
    private static int recordStep;

    /** Start of Font.drawInBatch8xOutline: draws the kept letters and returns true, or gets ready to write them down. */
    public static boolean replay(Font font, FormattedCharSequence seq, float x, float y, int color, int outline,
                                 Function<Font.DisplayMode, Font.GlyphVisitor> drawer) {
        recording = null;
        try {
            Entry e = look(font, seq, x, y, color, outline);
            if (e != null) {
                Font.GlyphVisitor v = drawer.apply(Font.DisplayMode.NORMAL);
                for (Object o : (List<?>) e.outlined) hand(v, o);
                v = drawer.apply(Font.DisplayMode.POLYGON_OFFSET);
                for (Object o : (List<?>) e.inner) hand(v, o);
                return true;
            }
            if (fill != null) {
                recording = fill;
                recordStep = 0;
            }
            fill = null;
        } catch (Throwable t) {
            Boosts.failed(Boosts.GLOW_TEXT, t);
            recording = null;
        }
        return false;
    }

    private static void hand(Font.GlyphVisitor v, Object o) {
        if (o instanceof TextRenderable.Styled g) v.acceptGlyph(g);
        else if (o instanceof EmptyArea a) v.acceptEmptyArea(a);
        else v.acceptEffect((TextRenderable) o);
    }

    /** The game made a drawer (first for the outline, then for the text on top): write down what it's handed. */
    public static Font.GlyphVisitor record(Font.GlyphVisitor real) {
        Entry e = recording;
        if (e == null || real == null) return real;
        List<Object> out = new ArrayList<>();
        if (recordStep == 0) e.outlined = out;
        else if (recordStep == 1) e.inner = out;
        else {
            recording = null;
            return real;
        }
        recordStep++;
        return new Font.GlyphVisitor() {
            @Override
            public void acceptGlyph(TextRenderable.Styled g) {
                out.add(g);
                real.acceptGlyph(g);
            }

            @Override
            public void acceptEffect(TextRenderable r) {
                out.add(r);
                real.acceptEffect(r);
            }

            @Override
            public void acceptEmptyArea(EmptyArea a) {
                out.add(a);
                real.acceptEmptyArea(a);
            }
        };
    }

    /** End of Font.drawInBatch8xOutline: the written-down letters are complete. */
    public static void recorded() {
        Entry e = recording;
        recording = null;
        if (e != null && recordStep == 2 && e.outlined != null && e.inner != null) e.ready = true;
    }
}
