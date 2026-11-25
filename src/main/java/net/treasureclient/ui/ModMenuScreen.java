package net.treasureclient.ui;

import net.treasureclient.compat.V;
import net.treasureclient.compat.Gfx;
import net.treasureclient.compat.TCScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.treasureclient.core.Anim;
import net.treasureclient.core.Config;
import net.treasureclient.core.ModDef;
import net.treasureclient.core.Mods;
import net.treasureclient.core.Theme;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The 1.5 Mod Menu, drawn exactly like the browser client's: full screen, "Modules" and "Themes" as
 * big stepped words on the left, the mod types as tabs along the top, and each mod as a clean row.
 * All sizes are CSS pixels from the browser version (see Ui).
 */
public class ModMenuScreen extends TCScreen {
    private static final int HOVER_BG = 0x09FFFFFF;
    private static final int PRESS_BG = 0x0FFFFFFF;
    private static final int SWITCH_OFF = 0x29ECEBE6;
    private static final String HINT1 = "Click a mod to turn it on or off. ⚙ opens its settings.";
    private static final String HINT2 = "Change the menu and zoom keys in Options ▸ Controls ▸ Key Binds ▸ Treasure Client";

    private final Screen parent;
    private String view = "modules";
    private String tab = "all";
    private String search = "";
    private boolean searchFocus;
    private ModDef sub;
    /** A theme's settings page (background and blur), or null. */
    private Theme themeSub;
    private float scroll, scrollTarget, contentH, listViewH;

    private final long openedAt = System.nanoTime();
    private long listAnimAt = System.nanoTime();
    private long subAt;
    private final Anim[] viewBar = {new Anim(0, 300), new Anim(0, 300)};
    private final Anim[] viewShift = {new Anim(0, 300), new Anim(0, 300)};
    private final Anim inkX = new Anim(-1, 350), inkW = new Anim(0, 350);
    private final Map<String, Anim> knobs = new HashMap<>();
    private final Map<String, Anim> edges = new HashMap<>();
    private final Map<String, Anim> gears = new HashMap<>();

    // layout of the last frame, for clicks (CSS px)
    private final List<float[]> tabBoxes = new ArrayList<>();
    private final List<Object[]> hits = new ArrayList<>();
    private boolean pressed;

    public ModMenuScreen(Screen parent) {
        super(Component.literal("Treasure Client"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        Config.get().saveNow();
        V.open(parent);
    }


    // ------------------------------------------------------------------ layout helpers
    private float leftW() {
        return Math.max(210, Ui.vw * 0.27f);
    }

    private float rightX() {
        return Ui.vw * 0.05f + leftW() + Ui.vw * 0.02f;
    }

    private float rightEnd() {
        return Ui.vw * 0.95f;
    }

    private float headTop() {
        return Ui.vh * 0.042f;
    }

    private float bodyTop() {
        return headTop() + 24;
    }

    private float wordSize() {
        return Math.max(30, Math.min(54, Ui.vh * 0.06f));
    }

    private static float a(long start, float ms, float delay) {
        return Anim.since(start, ms, delay);
    }

    private List<ModDef> listed() {
        String q = search.trim().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            List<ModDef> out = new ArrayList<>();
            for (ModDef d : Mods.ALL)
                if (d.name().toLowerCase(Locale.ROOT).contains(q) || d.desc().toLowerCase(Locale.ROOT).contains(q)) out.add(d);
            return out;
        }
        return Mods.inTab(tab);
    }

    private Anim anim(Map<String, Anim> m, String key, float initial, float ms) {
        return m.computeIfAbsent(key, k -> new Anim(initial, ms));
    }

    // ------------------------------------------------------------------ drawing
    @Override
    protected void draw(int mouseX, int mouseY, float delta) {
        Ui.begin();
        try {
            float mx = Ui.mx(mouseX), my = Ui.my(mouseY);
            hits.clear();
            Theme th = Theme.current();
            drawBackdrop(th);
            drawHead(th, mx, my);
            if (sub != null) drawSettings(th, sub, mx, my);
            else if (themeSub != null) drawThemeSettings(th, themeSub, mx, my);
            else {
                drawLeft(th, mx, my);
                drawBar(th, mx, my);
                if (view.equals("themes")) drawThemes(th, mx, my);
                else drawList(th, mx, my);
            }
        } finally {
            Ui.end();
        }
    }

    private void drawBackdrop(Theme th) {
        if (minecraft.level == null) {
            // from the title screen: the theme's background picture behind the menu
            TitleBackground.draw(th);
        }
        Ui.rect(0, 0, Ui.vw, Ui.vh, 0x8008080A);
        Ui.gradientH(0, 0, Ui.vw, Ui.vh, new float[]{0, .3f, 1}, new int[]{0xE00A0A0C, 0xBD0A0A0C, 0x9E0A0A0C});
    }

    private void drawHead(Theme th, float mx, float my) {
        float p = a(openedAt, 450, 0);
        float y = headTop() - 10 * (1 - p);
        float x = Ui.vw * 0.05f;
        float al = p;
        Ui.image(Ui.chest(), x, y, 24 * 91f / 92f, 24, 91, 92, Theme.alpha(0xFFFFFFFF, al));
        x += 24 * 91f / 92f + 10;
        float bs = 15;
        float bw = Ui.text(Ui.BRAND, "TREASURE CLIENT", x, y + (24 - Ui.lineH(Ui.BRAND, bs)) / 2, bs, Theme.alpha(Theme.INK, al), .14f, 0);
        x += bw + 26;
        if (minecraft.player != null) x += link("Edit HUD", x, y, mx, my, al, () -> V.open(new HudEditorScreen(this))) + 26;
        link("Close ✕", x, y, mx, my, al, this::onClose);
    }

    /** A small mono link in the head row; returns its width. */
    private float link(String label, float x, float rowY, float mx, float my, float alpha, Runnable onClick) {
        float s = 11.5f, h = Ui.lineH(Ui.MONO, s) + 8;
        float w = Ui.width(Ui.MONO, label.toUpperCase(Locale.ROOT), s, .06f);
        float y = rowY + (24 - h) / 2;
        boolean hover = Ui.in(mx, my, x, y, w, h);
        Ui.mono(label, x, y + 4, s, Theme.alpha(hover ? Theme.INK : Theme.INK_DIM, alpha), .06f);
        hits.add(new Object[]{new float[]{x, y, w, h}, onClick});
        return w;
    }

    private void drawLeft(Theme th, float mx, float my) {
        float x0 = Ui.vw * 0.05f;
        float y = bodyTop() + Ui.vh * 0.05f;
        float ls = 11.5f;
        {
            float p = a(openedAt, 550, 60);
            Ui.mono("Mod Menu", x0 - 28 * (1 - p), y, ls, Theme.alpha(Theme.INK_DIM, p), .06f);
        }
        y += Ui.lineH(Ui.MONO, ls) + Ui.vh * 0.026f;
        String[] names = {"Modules", "Themes"};
        String[] ids = {"modules", "themes"};
        float ws = wordSize();
        for (int i = 0; i < 2; i++) {
            boolean active = view.equals(ids[i]) && sub == null;
            float itemH = Ui.vh * 0.024f + ws * 0.98f;
            float ix = x0 + i * Ui.vw * 0.03f;
            float wordW = Ui.width(Ui.WORD, names[i].toUpperCase(Locale.ROOT), ws, -.01f);
            boolean hover = Ui.in(mx, my, ix, y, 40 + wordW + 14, itemH);
            float p = a(openedAt, 550, 60 + i * 70);
            float slide = -28 * (1 - p);
            // the accent bar from the screen edge
            float bw = viewBar[i].target(active ? Ui.vw * 0.05f - 10 + i * Ui.vw * 0.03f : 0).get();
            if (bw > 0.5f) Ui.rect(0, y + itemH / 2 - 1.5f, bw, 3, Theme.alpha(th.a1(), p));
            Ui.mono((i + 1 < 10 ? "0" : "") + (i + 1), ix + slide, y + Ui.vh * 0.012f + Ui.vh * 0.008f, ls,
                Theme.alpha(active ? th.a1() : Theme.INK_DIM, p), .06f);
            float shift = viewShift[i].target(active ? 14 : 0).get();
            float op = active ? 1 : hover ? .75f : .42f;
            Ui.text(Ui.WORD, names[i].toUpperCase(Locale.ROOT), ix + 40 + shift + slide, y + Ui.vh * 0.012f, ws,
                Theme.alpha(Theme.INK, op * p), -.01f, .98f);
            final String id = ids[i];
            hits.add(new Object[]{new float[]{ix, y, 40 + wordW + 14, itemH}, (Runnable) () -> setView(id)});
            y += itemH;
        }
        // the hint at the bottom
        float p = a(openedAt, 600, 300);
        float hy = Ui.vh - Ui.vh * 0.045f - 4 * ls * 1.7f;
        float maxW = Ui.vw * 0.22f;
        for (String line : wrap(Ui.MONO, HINT1 + "\n" + HINT2, ls, .02f, maxW)) {
            Ui.text(Ui.MONO, line, x0, hy, ls, Theme.alpha(Theme.INK_DIM, p), .02f, 1.7f);
            hy += ls * 1.7f;
        }
    }

    private void setView(String v) {
        sub = null;
        themeSub = null;
        if (!v.equals(view)) {
            view = v;
            scroll = scrollTarget = 0;
            listAnimAt = System.nanoTime();
        }
    }

    private float barLineY() {
        return bodyTop() + Ui.vh * 0.05f + 6 + Ui.lineH(Ui.MONO, 12) + 6 + 12;
    }

    private void drawBar(Theme th, float mx, float my) {
        float p = a(openedAt, 500, 80);
        float dy = -10 * (1 - p);
        float x0 = rightX(), x1 = rightEnd();
        float line = barLineY() + dy;
        float searchW = Math.min(260, (x1 - x0) * 0.4f);
        float sx = x1 - searchW;
        float catEnd = sx - 28;
        boolean showTabs = view.equals("modules") && search.trim().isEmpty();
        // tabs
        tabBoxes.clear();
        float tx = x0, ty = line - 12 - 6 - Ui.lineH(Ui.MONO, 12) - 6;
        float activeX = -1, activeW = 0;
        for (int i = 0; i < Mods.TABS.size(); i++) {
            String id = Mods.TABS.get(i);
            String name = Mods.TAB_NAMES.get(i).toUpperCase(Locale.ROOT);
            List<ModDef> in = Mods.inTab(id);
            int on = 0;
            for (ModDef d : in) if (Config.get().on(d.key())) on++;
            String count = on + "/" + in.size();
            float nw = Ui.width(Ui.MONO, name, 12, .07f);
            float cw = Ui.width(Ui.MONO, count, 10.5f, .07f);
            float w = nw + 7 + cw;
            boolean active = id.equals(tab);
            boolean hover = Ui.in(mx, my, tx, ty, w, line - ty);
            if (showTabs) {
                Ui.text(Ui.MONO, name, tx, ty + 6, 12, Theme.alpha(active || hover ? Theme.INK : Theme.INK_DIM, p), .07f, 0);
                float cy = ty + 6 + (Ui.lineH(Ui.MONO, 12) - Ui.lineH(Ui.MONO, 10.5f)) * 0.8f;
                Ui.text(Ui.MONO, count, tx + nw + 7, cy, 10.5f, active ? Theme.alpha(th.a1(), p) : Theme.alpha(Theme.INK_DIM, .8f * p), .07f, 0);
                final String tid = id;
                hits.add(new Object[]{new float[]{tx, ty, w, line - ty}, (Runnable) () -> setTab(tid)});
            }
            if (active) {
                activeX = tx;
                activeW = w;
            }
            tx += w + 26;
        }
        if (showTabs) {
            Ui.rect(x0, line, catEnd - x0, 1, Theme.alpha(Theme.INK_FAINT, p));
            if (inkX.target() < 0) {
                inkX.snap(activeX);
                inkW.snap(activeW);
            }
            float ix = inkX.target(activeX).get(), iw = inkW.target(activeW).get();
            Ui.rect(ix, line - 1, iw, 2, Theme.alpha(th.a1(), p));
        }
        // search
        if (view.equals("modules")) {
            float sh = 6 + Ui.lineH(Ui.TEXT, 14) + 6;
            float sy = line - 12 - sh;
            Ui.rect(sx, line, searchW, 1, Theme.alpha(searchFocus ? th.a1() : Theme.INK_FAINT, p));
            boolean caret = searchFocus && (System.currentTimeMillis() / 530) % 2 == 0;
            if (search.isEmpty()) {
                Ui.text(Ui.TEXT, "Search mods", sx, sy + 6, 14, Theme.alpha(Theme.INK_DIM, p));
                if (caret) Ui.rect(sx, sy + 7, 1, Ui.lineH(Ui.TEXT, 14) - 2, Theme.alpha(Theme.INK, p));
            } else {
                Ui.clip(sx, sy, searchW, sh);
                float w = Ui.text(Ui.TEXT, search, sx, sy + 6, 14, Theme.alpha(Theme.INK, p));
                if (caret) Ui.rect(sx + w + 1, sy + 7, 1, Ui.lineH(Ui.TEXT, 14) - 2, Theme.alpha(Theme.INK, p));
                Ui.unclip();
            }
            hits.add(new Object[]{new float[]{sx, sy, searchW, line - sy + 1}, (Runnable) () -> searchFocus = true});
        }
    }

    private void setTab(String t) {
        if (!t.equals(tab)) {
            tab = t;
            scroll = scrollTarget = 0;
            listAnimAt = System.nanoTime();
        }
    }

    private float rowH() {
        return 15 + Math.max(Ui.lineH(Ui.SEMI, 16) + 3 + Ui.lineH(Ui.TEXT, 13), 28) + 15 + 1;
    }

    private void drawList(Theme th, float mx, float my) {
        float x0 = rightX(), x1 = rightEnd();
        float top = barLineY() + 1;
        float listTop = top + Ui.vh * 0.014f;
        listViewH = Ui.vh - top;
        List<ModDef> mods = listed();
        float rh = rowH();
        contentH = Ui.vh * 0.014f + mods.size() * rh + Ui.vh * 0.06f;
        scroll += (scrollTarget - scroll) * (Anim.enabled() ? 0.3f : 1f);
        Ui.clip(x0 - 4, top, x1 - x0 + 8, Ui.vh - top);
        boolean inList = my >= top;
        float y = listTop - scroll;
        if (mods.isEmpty()) Ui.text(Ui.TEXT, "No mods match \"" + search + "\"", x0 + 16, y + 20, 14, Theme.INK_DIM);
        for (int i = 0; i < mods.size(); i++, y += rh) {
            if (y + rh < top || y > Ui.vh) continue;
            ModDef d = mods.get(i);
            float p = a(listAnimAt, 450, 40 + Math.min(i, 12) * 26);
            float ry = y + 14 * (1 - p);
            boolean on = Config.get().on(d.key());
            boolean hover = inList && Ui.in(mx, my, x0, y, x1 - x0, rh - 1);
            if (hover) Ui.rect(x0, ry, x1 - x0, rh - 1, Theme.alpha(pressed ? PRESS_BG : HOVER_BG, p));
            Ui.rect(x0, ry + rh - 1, x1 - x0, 1, Theme.alpha(Theme.INK_FAINT, p));
            // the accent edge when on
            float edge = anim(edges, d.key(), on ? 1 : 0, 250).target(on ? 1 : 0).get();
            if (edge > 0.01f) {
                float full = (rh - 1) * 0.48f, eh = full * (0.2f + 0.8f * edge);
                Ui.round(x0, ry + (rh - 1) / 2 - eh / 2, 2, eh, 1, Theme.alpha(th.a1(), edge * p));
            }
            // icon
            float cx = x0 + 16;
            float iconY = ry + (rh - 1 - 21) / 2;
            int iconCol = on ? th.a1() : Theme.alpha(Theme.INK, hover ? .8f : .45f);
            Ui.icon(d.icon(), cx, iconY, 21, Theme.alpha(iconCol, p));
            cx += 21 + 16;
            // the switch and gear on the right
            float swX = x1 - 6 - 40, swY = ry + (rh - 1 - 22) / 2;
            float gearX = swX - 16 - 28, gearY = ry + (rh - 1 - 28) / 2;
            float textEnd = (d.hasSettings() ? gearX : swX) - 12 - 8;
            // name + description
            float nameH = Ui.lineH(Ui.SEMI, 16), descH = Ui.lineH(Ui.TEXT, 13);
            float ty = ry + (rh - 1 - (nameH + 3 + descH)) / 2;
            Ui.text(Ui.SEMI, d.name(), cx, ty, 16, Theme.alpha(Theme.INK, p));
            Ui.text(Ui.TEXT, ellipsis(Ui.TEXT, d.desc(), 13, textEnd - cx), cx, ty + nameH + 3, 13, Theme.alpha(Theme.INK_DIM, p));
            final ModDef def = d;
            if (d.hasSettings()) {
                boolean gh = Ui.in(mx, my, gearX, gearY, 28, 28) && inList;
                float rot = anim(gears, d.key(), 0, 300).target(gh ? 1 : 0).get();
                if (gh) Ui.round(gearX, gearY, 28, 28, 6, Theme.alpha(0x0FFFFFFF, p));
                gear(gearX + 14, gearY + 14, 16, rot * (float) Math.PI / 3f, Theme.alpha(gh ? th.a1() : Theme.INK_DIM, p));
                hits.add(new Object[]{new float[]{gearX, gearY, 28, 28}, (Runnable) () -> openSettings(def)});
            }
            boolean swHover = inList && Ui.in(mx, my, swX, swY, 40, 22);
            drawSwitch(th, d.key(), on, swX, swY, swHover, p);
            hits.add(new Object[]{new float[]{x0, Math.max(top, y), x1 - x0, rh - 1}, (Runnable) () -> toggle(def)});
        }
        Ui.unclip();
    }

    private void drawSwitch(Theme th, String key, boolean on, float x, float y, boolean hover, float p) {
        if (hover) Ui.round(x - 3, y - 3, 46, 28, 14, Theme.alpha(0x1AECEBE6, p));
        float t = anim(knobs, key, on ? 1 : 0, 250).target(on ? 1 : 0).get();
        Ui.round(x, y, 40, 22, 11, Theme.alpha(Theme.lerp(SWITCH_OFF, th.a1(), t), p));
        Ui.circle(x + 3 + 8 + 18 * t, y + 11, 8, Theme.alpha(Theme.lerp(Theme.INK, 0xFF111111, t), p));
    }

    private void gear(float cx, float cy, float size, float angle, int argb) {
        float k = Ui.k;
        Gfx.pose().pushMatrix();
        Gfx.pose().translate(Math.round(cx * k), Math.round(cy * k));
        Gfx.pose().rotate(angle);
        int s = Math.round(size * k);
        int i = Mods.ICONS.indexOf("_gear");
        Gfx.blit(Ui.ICONS, -s / 2, -s / 2, (i % 8) * 64, (i / 8) * 64, s, s, 64, 64, 512, 384, argb);
        Gfx.pose().popMatrix();
    }

    private void toggle(ModDef d) {
        Config.get().set(d.key(), !Config.get().on(d.key()));
        net.treasureclient.TreasureClient.onModToggled(d.key());
        playClick();
    }

    private void openSettings(ModDef d) {
        sub = d;
        subAt = System.nanoTime();
        searchFocus = false;
        playClick();
    }

    private void playClick() {
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    // ------------------------------------------------------------------ themes
    private void drawThemes(Theme th, float mx, float my) {
        float x0 = rightX(), x1 = rightEnd();
        float y = barLineY() + 1 + Ui.vh * 0.014f;
        float p = a(listAnimAt, 450, 0);
        y += 14 * (1 - p);
        Ui.text(Ui.WORD, "APPEARANCE", x0, y, 26, Theme.alpha(Theme.INK, p));
        y += Ui.lineH(Ui.WORD, 26) + 8;
        Ui.mono("Click a theme to use it everywhere \u00B7 \u2699 for its background and blur", x0, y, 11, Theme.alpha(Theme.INK_DIM, p), .06f);
        y += Ui.lineH(Ui.MONO, 11) + 18;
        int cols = Math.max(1, (int) ((x1 - x0 + 12) / (156 + 12)));
        float cw = (x1 - x0 - (cols - 1) * 12) / cols, ch = cw * 10 / 16;
        for (int i = 0; i < Theme.ALL.size(); i++) {
            Theme t = Theme.ALL.get(i);
            float cx = x0 + (i % cols) * (cw + 12), cy = y + (i / cols) * (ch + 12);
            boolean hover = Ui.in(mx, my, cx, cy, cw, ch);
            boolean sel = t.id().equals(th.id());
            if (hover && !sel) cy -= 2;
            // the picture (cropped to 16:10 from the 2:1 background)
            int uw = 819;
            Ui.image(TitleBackground.texture(t), cx, cy, cw, ch, (1024 - uw) / 2f, 0, uw, 512, 1024, 512, Theme.alpha(0xFFFFFFFF, p));
            Ui.gradientV(cx, cy + ch - 34, cw, 34, 0x00000000, Theme.alpha(0xCC000000, p));
            Ui.text(Ui.SEMI, t.name(), cx + 11, cy + ch - 9 - Ui.lineH(Ui.SEMI, 13), 13, Theme.alpha(0xFFFFFFFF, p));
            if (sel) {
                String tag = "ACTIVE";
                float tw = Ui.width(Ui.SEMI, tag, 10) + 18;
                float tagH = Ui.lineH(Ui.SEMI, 10) + 6;
                Ui.round(cx + cw - 11 - tw, cy + ch - 9 - tagH, tw, tagH, tagH / 2, Theme.alpha(t.a1(), p));
                Ui.text(Ui.SEMI, tag, cx + cw - 11 - tw + 9, cy + ch - 9 - tagH + 3, 10, Theme.alpha(t.bg0(), p));
            }
            int border = sel ? th.a1() : hover ? th.a2() : Theme.INK_FAINT;
            float bw = sel ? 2 : 1;
            Ui.rect(cx, cy, cw, bw, Theme.alpha(border, p));
            Ui.rect(cx, cy + ch - bw, cw, bw, Theme.alpha(border, p));
            Ui.rect(cx, cy, bw, ch, Theme.alpha(border, p));
            Ui.rect(cx + cw - bw, cy, bw, ch, Theme.alpha(border, p));
            final String id = t.id();
            hits.add(new Object[]{new float[]{cx, cy, cw, ch}, (Runnable) () -> {
                Config.get().theme = id;
                Config.get().markDirty();
                playClick();
            }});
            // the settings gear in the top corner (added after the card, so it's checked first)
            float gs = 24, gx = cx + cw - gs - 7, gy = cy + 7;
            boolean gh = Ui.in(mx, my, gx, gy, gs, gs);
            Ui.round(gx, gy, gs, gs, 6, Theme.alpha(gh ? t.a2() : 0x73000000, p));
            gear(gx + gs / 2, gy + gs / 2, 14, 0, Theme.alpha(0xFFFFFFFF, (gh ? 1f : .8f) * p));
            final Theme ft = t;
            hits.add(new Object[]{new float[]{gx, gy, gs, gs}, (Runnable) () -> {
                themeSub = ft;
                subAt = System.nanoTime();
                playClick();
            }});
        }
    }

    // ------------------------------------------------------------------ a theme's settings page
    private void drawThemeSettings(Theme th, Theme t, float mx, float my) {
        float p = a(subAt, 400, 0);
        float x0 = Ui.vw * 0.05f + 26 * (1 - p);
        float y = bodyTop() + Ui.vh * 0.04f;
        float ws = wordSize();
        float backS = 11.5f;
        float backY = y + ws * 0.72f - Ui.lineH(Ui.MONO, backS) * 0.75f;
        float bw = Ui.width(Ui.MONO, "\u2039 BACK", backS, .06f);
        boolean bh = Ui.in(mx, my, x0, backY - 4, bw, Ui.lineH(Ui.MONO, backS) + 8);
        Ui.mono("\u2039 Back", x0, backY, backS, Theme.alpha(bh ? Theme.INK : Theme.INK_DIM, p), .06f);
        hits.add(new Object[]{new float[]{x0, backY - 4, bw, Ui.lineH(Ui.MONO, backS) + 8}, (Runnable) () -> {
            themeSub = null;
            listAnimAt = System.nanoTime();
        }});
        Ui.text(Ui.WORD, ("Theme \u2014 " + t.name()).toUpperCase(Locale.ROOT), x0 + bw + 22, y, ws, Theme.alpha(Theme.INK, p), -.01f, 1f);
        y += ws + Ui.vh * 0.03f;
        float w = Math.min(860, Ui.vw * 0.9f);
        // the background: any of the seven pictures
        Ui.text(Ui.TEXT, "Background", x0, y, 14, Theme.alpha(Theme.INK, p));
        y += Ui.lineH(Ui.TEXT, 14) + 10;
        String cur = Backgrounds.of(t);
        float tw = 112, tht = 63, gap = 10;
        int cols = Math.max(1, (int) ((w + gap) / (tw + gap)));
        for (int i = 0; i < Backgrounds.IDS.length; i++) {
            String id = Backgrounds.IDS[i];
            float bx = x0 + (i % cols) * (tw + gap), by = y + (i / cols) * (tht + gap);
            boolean sel = id.equals(cur), hover = Ui.in(mx, my, bx, by, tw, tht);
            if (hover && !sel) by -= 1;
            Ui.image(Backgrounds.sharp(id), bx, by, tw, tht, (1024 - 910) / 2f, 0, 910, 512, 1024, 512, Theme.alpha(0xFFFFFFFF, p));
            Ui.gradientV(bx, by + tht - 24, tw, 24, 0x00000000, Theme.alpha(0xBF000000, p));
            Ui.text(Ui.SEMI, Backgrounds.NAMES[i], bx + 7, by + tht - 6 - Ui.lineH(Ui.SEMI, 11), 11, Theme.alpha(0xFFFFFFFF, p));
            int border = sel ? th.a1() : hover ? th.a2() : Theme.INK_FAINT;
            float bb = sel ? 2 : 1;
            Ui.rect(bx, by, tw, bb, Theme.alpha(border, p));
            Ui.rect(bx, by + tht - bb, tw, bb, Theme.alpha(border, p));
            Ui.rect(bx, by, bb, tht, Theme.alpha(border, p));
            Ui.rect(bx + tw - bb, by, bb, tht, Theme.alpha(border, p));
            hits.add(new Object[]{new float[]{bx, by, tw, tht}, (Runnable) () -> {
                Backgrounds.set(t, id);
                playClick();
            }});
        }
        y += ((Backgrounds.IDS.length + cols - 1) / cols) * (tht + gap) + 8;
        // blur (off by default: the picture stays sharp)
        y = settingRow(th, "Blur background", x0, y, w, mx, my, p, (rx, ry, rh) -> {
            boolean on = Backgrounds.blur(t);
            float sx = rx, sy = ry + (rh - 22) / 2;
            drawSwitch(th, "bgblur:" + t.id(), on, sx, sy, Ui.in(mx, my, sx, sy, 40, 22), p);
            hits.add(new Object[]{new float[]{sx, sy, 40, 22}, (Runnable) () -> {
                Backgrounds.setBlur(t, !Backgrounds.blur(t));
                playClick();
            }});
            return 40;
        });
        Ui.mono("Off = the picture stays sharp", x0, y + 10, 11, Theme.alpha(Theme.INK_DIM, p), .06f);
    }

    // ------------------------------------------------------------------ a mod's settings page
    private void drawSettings(Theme th, ModDef d, float mx, float my) {
        float p = a(subAt, 400, 0);
        float x0 = Ui.vw * 0.05f + 26 * (1 - p);
        float y = bodyTop() + Ui.vh * 0.04f;
        float ws = wordSize();
        // "‹ Back" and the big title on one baseline
        float titleH = ws;
        float backS = 11.5f;
        float backY = y + titleH * 0.72f - Ui.lineH(Ui.MONO, backS) * 0.75f;
        float bw = Ui.width(Ui.MONO, "‹ BACK", backS, .06f);
        boolean bh = Ui.in(mx, my, x0, backY - 4, bw, Ui.lineH(Ui.MONO, backS) + 8);
        Ui.mono("‹ Back", x0, backY, backS, Theme.alpha(bh ? Theme.INK : Theme.INK_DIM, p), .06f);
        hits.add(new Object[]{new float[]{x0, backY - 4, bw, Ui.lineH(Ui.MONO, backS) + 8}, (Runnable) () -> {
            sub = null;
            listAnimAt = System.nanoTime();
        }});
        Ui.text(Ui.WORD, d.name().toUpperCase(Locale.ROOT), x0 + bw + 22, y, ws, Theme.alpha(Theme.INK, p), -.01f, 1f);
        y += titleH + Ui.vh * 0.03f;
        float w = Math.min(760, Ui.vw * 0.9f);
        // the mod's own switch first
        y = settingRow(th, "Enabled", x0, y, w, mx, my, p, (rx, ry, rh) -> {
            boolean on = Config.get().on(d.key());
            float sx = rx, sy = ry + (rh - 22) / 2;
            drawSwitch(th, d.key(), on, sx, sy, Ui.in(mx, my, sx, sy, 40, 22), p);
            hits.add(new Object[]{new float[]{sx, sy, 40, 22}, (Runnable) () -> toggle(d)});
            return 40;
        });
        for (ModDef.Setting s : d.settings()) {
            if (s instanceof ModDef.Toggle t) {
                y = settingRow(th, t.label(), x0, y, w, mx, my, p, (rx, ry, rh) -> {
                    boolean on = Config.get().flag(t.key(), t.def());
                    float sx = rx, sy = ry + (rh - 22) / 2;
                    drawSwitch(th, "s:" + t.key(), on, sx, sy, Ui.in(mx, my, sx, sy, 40, 22), p);
                    hits.add(new Object[]{new float[]{sx, sy, 40, 22}, (Runnable) () -> {
                        Config.get().put(t.key(), !Config.get().flag(t.key(), t.def()));
                        playClick();
                    }});
                    return 40;
                });
            } else if (s instanceof ModDef.Choice c) {
                y = settingRow(th, c.label(), x0, y, w, mx, my, p, (rx, ry, rh) -> seg(th, c, rx, ry, rh, mx, my, p));
            } else if (s instanceof ModDef.Slider sl) {
                y = settingRow(th, sl.label(), x0, y, w, mx, my, p, (rx, ry, rh) -> slider(th, sl, rx, ry, rh, mx, my, p));
            }
        }
    }

    private interface Control {
        /** Draws the control at the row's right side (x = where it may start); returns its width. */
        float draw(float x, float y, float h);
    }

    private float settingRow(Theme th, String label, float x, float y, float w, float mx, float my, float p, Control control) {
        float h = 50;
        Ui.text(Ui.TEXT, label, x, y + (h - Ui.lineH(Ui.TEXT, 14)) / 2, 14, Theme.alpha(Theme.INK, p));
        // controls are right-aligned: measure by drawing into the right edge
        float cw = controlWidth(label);
        control.draw(x + w - cw, y, h);
        Ui.rect(x, y + h - 1, w, 1, Theme.alpha(Theme.INK_FAINT, p));
        return y + h;
    }

    private final Map<String, Float> controlW = new HashMap<>();

    private float controlWidth(String label) {
        return controlW.getOrDefault(label, 40f);
    }

    private float seg(Theme th, ModDef.Choice c, float x, float y, float h, float mx, float my, float p) {
        String cur = Config.get().str(c.key(), c.def());
        float pad = 12, gap = 2, s = 12.5f;
        float total = 4;
        for (String l : c.labels()) total += Ui.width(Ui.SEMI, l, s) + pad * 2 + gap;
        total -= gap;
        controlW.put(c.label(), total);
        float bh = Ui.lineH(Ui.SEMI, s) + 12;
        float by = y + (h - bh) / 2;
        Ui.round(x, by, total, bh, 6, Theme.alpha(0x0DFFFFFF, p));
        float bx = x + 2;
        for (int i = 0; i < c.values().size(); i++) {
            String v = c.values().get(i), l = c.labels().get(i);
            float lw = Ui.width(Ui.SEMI, l, s) + pad * 2;
            boolean on = v.equals(cur);
            boolean hover = Ui.in(mx, my, bx, by + 2, lw, bh - 4);
            if (on) Ui.round(bx, by + 2, lw, bh - 4, 5, Theme.alpha(th.a1(), p));
            else if (hover) Ui.round(bx, by + 2, lw, bh - 4, 5, Theme.alpha(0x0FFFFFFF, p));
            Ui.text(Ui.SEMI, l, bx + pad, by + 6, s, Theme.alpha(on ? 0xFF111111 : Theme.INK, p));
            final String val = v;
            hits.add(new Object[]{new float[]{bx, by + 2, lw, bh - 4}, (Runnable) () -> {
                Config.get().put(c.key(), val);
                playClick();
            }});
            bx += lw + gap;
        }
        return total;
    }

    private ModDef.Slider dragging;
    private float dragX, dragW;

    private float slider(Theme th, ModDef.Slider sl, float x, float y, float h, float mx, float my, float p) {
        float w = 220;
        controlW.put(sl.label(), w);
        int v = Config.get().num(sl.key(), sl.def());
        String txt = v + sl.unit();
        float tw = 46;
        float tx = x + w - tw;
        Ui.text(Ui.MONO, txt, tx + tw - Ui.width(Ui.MONO, txt, 11.5f), y + (h - Ui.lineH(Ui.MONO, 11.5f)) / 2, 11.5f, Theme.alpha(Theme.INK, p));
        float trackW = w - tw - 12, ty = y + h / 2;
        float f = (v - sl.min()) / (float) (sl.max() - sl.min());
        Ui.round(x, ty - 2, trackW, 4, 2, Theme.alpha(SWITCH_OFF, p));
        Ui.round(x, ty - 2, trackW * f, 4, 2, Theme.alpha(th.a1(), p));
        Ui.circle(x + trackW * f, ty, 7, Theme.alpha(Theme.INK, p));
        hits.add(new Object[]{new float[]{x - 6, y + 8, trackW + 12, h - 16}, (Runnable) () -> {
            dragging = sl;
            dragX = x;
            dragW = trackW;
        }});
        if (dragging == sl) {
            dragX = x;
            dragW = trackW;
        }
        return w;
    }

    private void dragTo(float mx) {
        ModDef.Slider sl = dragging;
        float f = Math.max(0, Math.min(1, (mx - dragX) / dragW));
        int raw = Math.round(sl.min() + f * (sl.max() - sl.min()));
        int v = Math.round((raw - sl.min()) / (float) sl.step()) * sl.step() + sl.min();
        Config.get().put(sl.key(), Math.max(sl.min(), Math.min(sl.max(), v)));
    }

    // ------------------------------------------------------------------ text helpers
    private static String ellipsis(String face, String text, float size, float maxW) {
        if (Ui.width(face, text, size) <= maxW) return text;
        String t = text;
        while (t.length() > 1 && Ui.width(face, t + "…", size) > maxW) t = t.substring(0, t.length() - 1);
        return t.stripTrailing() + "…";
    }

    private record WrapKey(String face, String text, float size, float ls, int maxW, float k) {}
    private static final java.util.HashMap<WrapKey, List<String>> WRAPS = new java.util.HashMap<>();

    /** Splits text into lines that fit (remembered: the menus ask for the same text every frame). */
    static List<String> wrap(String face, String text, float size, float ls, float maxW) {
        WrapKey key = new WrapKey(face, text, size, ls, Math.round(maxW), Ui.k);
        List<String> got = WRAPS.get(key);
        if (got != null) return got;
        if (WRAPS.size() > 400) WRAPS.clear();
        got = List.copyOf(wrapNow(face, text, size, ls, maxW));
        WRAPS.put(key, got);
        return got;
    }

    private static List<String> wrapNow(String face, String text, float size, float ls, float maxW) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String tryLine = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && Ui.width(face, tryLine, size, ls) > maxW) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else line = new StringBuilder(tryLine);
            }
            out.add(line.toString());
        }
        return out;
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() != 0) return true;
        float mx = Ui.mx(e.x()), my = Ui.my(e.y());
        pressed = true;
        boolean wasFocus = searchFocus;
        searchFocus = false;
        // later entries are drawn on top: check them first
        for (int i = hits.size() - 1; i >= 0; i--) {
            float[] r = (float[]) hits.get(i)[0];
            if (Ui.in(mx, my, r[0], r[1], r[2], r[3])) {
                ((Runnable) hits.get(i)[1]).run();
                if (dragging != null) dragTo(mx);
                return true;
            }
        }
        if (wasFocus) searchFocus = false;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging != null) dragTo(Ui.mx(e.x()));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        pressed = false;
        dragging = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        float max = Math.max(0, contentH - listViewH);
        scrollTarget = Math.max(0, Math.min(max, scrollTarget - (float) v * 64));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        if (key == InputConstants.KEY_ESCAPE) {
            if (searchFocus && !search.isEmpty()) {
                search = "";
                return true;
            }
            if (sub != null || themeSub != null) {
                sub = null;
                themeSub = null;
                listAnimAt = System.nanoTime();
                return true;
            }
            onClose();
            return true;
        }
        if (searchFocus) {
            if (key == InputConstants.KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.offsetByCodePoints(search.length(), -1));
                scroll = scrollTarget = 0;
            }
            return true;
        }
        if (net.treasureclient.TreasureClient.MENU_KEY.matches(e) && sub == null && themeSub == null) {
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (!searchFocus && view.equals("modules") && sub == null && Character.isLetterOrDigit(e.codepoint())) searchFocus = true;
        if (searchFocus && e.isAllowedChatCharacter() && search.length() < 40) {
            search += e.codepointAsString();
            scroll = scrollTarget = 0;
            listAnimAt = System.nanoTime();
            return true;
        }
        return false;
    }
}
