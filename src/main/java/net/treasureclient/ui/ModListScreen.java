package net.treasureclient.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.treasureclient.compat.TCScreen;
import net.treasureclient.boost.Boosts;
import net.treasureclient.compat.V;
import net.treasureclient.core.Anim;
import net.treasureclient.core.Theme;

/**
 * The main menu's "Mods": every mod you have installed, laid out like Fabric's Mod Menu (a
 * searchable list on the left, the picked mod's details on the right), in the 1.5 look. The browser
 * editions' "Mods" screen is the same idea for the mods built into them.
 */
public class ModListScreen extends TCScreen {
    private record Entry(String id, String name, String version, String authors, String description,
                         String license, String homepage) {}

    private final Screen parent;
    private final List<Entry> all = new ArrayList<>();
    private String search = "";
    private int selected;
    private float scroll, scrollTarget, contentH, viewH;
    private final long openedAt = System.nanoTime();
    private long pickedAt = System.nanoTime();
    private final List<Object[]> hits = new ArrayList<>();
    private String failedId = "";
    private long failedAt;

    public ModListScreen(Screen parent) {
        super(Component.literal("Mods"));
        this.parent = parent;
        for (ModContainer c : FabricLoader.getInstance().getAllMods()) {
            ModMetadata m = c.getMetadata();
            // like Mod Menu: leave out the mods inside other mods' jars and Java itself
            if (c.getContainingMod().isPresent() || m.getId().equals("java")) continue;
            List<String> people = new ArrayList<>();
            for (Person p : m.getAuthors()) people.add(p.getName());
            all.add(new Entry(m.getId(), m.getName(), m.getVersion().getFriendlyString(), String.join(", ", people),
                m.getDescription() == null ? "" : m.getDescription(), String.join(", ", m.getLicense()),
                m.getContact().get("homepage").orElse("")));
        }
        // Treasure Client first, then Minecraft, then by name
        all.sort((a, b) -> {
            int ra = rank(a.id), rb = rank(b.id);
            return ra != rb ? Integer.compare(ra, rb) : a.name.compareToIgnoreCase(b.name);
        });
    }

    private static boolean hasSettings(Entry e) {
        return e.id.equals("treasureclient") || ModSettings.has(e.id);
    }

    private void openSettings(Entry e) {
        if (e.id.equals("treasureclient")) {
            V.open(new ModMenuScreen(this));
            return;
        }
        if (!ModSettings.open(e.id, this)) {
            failedId = e.id;
            failedAt = System.currentTimeMillis();
        }
    }

    private static int rank(String id) {
        return id.equals("treasureclient") ? 0 : id.equals("minecraft") ? 1 : 2;
    }

    @Override
    public void onClose() {
        V.open(parent);
    }

    private List<Entry> listed() {
        String q = search.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return all;
        List<Entry> out = new ArrayList<>();
        for (Entry e : all)
            if (e.name.toLowerCase(Locale.ROOT).contains(q) || e.id.contains(q) || e.authors.toLowerCase(Locale.ROOT).contains(q))
                out.add(e);
        return out;
    }

    private static float a(long start, float ms, float delay) {
        return Anim.since(start, ms, delay);
    }

    private float headTop() {
        return Ui.vh * 0.042f;
    }

    private float listW() {
        return Math.max(240, Ui.vw * 0.28f);
    }

    // ------------------------------------------------------------------ drawing
    @Override
    protected void draw(int mouseX, int mouseY, float delta) {
        Ui.begin();
        try {
            float mx = Ui.mx(mouseX), my = Ui.my(mouseY);
            hits.clear();
            Theme th = Theme.current();
            if (minecraft.level == null) TitleBackground.draw(th);
            Ui.rect(0, 0, Ui.vw, Ui.vh, 0x8008080A);
            Ui.gradientH(0, 0, Ui.vw, Ui.vh, new float[]{0, .3f, 1}, new int[]{0xE00A0A0C, 0xBD0A0A0C, 0x9E0A0A0C});
            drawHead(mx, my);
            List<Entry> list = listed();
            if (selected >= list.size()) selected = Math.max(0, list.size() - 1);
            drawList(th, list, mx, my);
            if (!list.isEmpty()) drawDetails(th, list.get(selected), mx, my);
        } finally {
            Ui.end();
        }
    }

    private void drawHead(float mx, float my) {
        float p = a(openedAt, 450, 0);
        float y = headTop() - 10 * (1 - p), x = Ui.vw * 0.05f;
        Ui.image(Ui.chest(), x, y, 24 * 91f / 92f, 24, 91, 92, Theme.alpha(0xFFFFFFFF, p));
        x += 24 * 91f / 92f + 10;
        float bs = 15;
        x += Ui.text(Ui.BRAND, "TREASURE CLIENT", x, y + (24 - Ui.lineH(Ui.BRAND, bs)) / 2, bs, Theme.alpha(Theme.INK, p), .14f, 0) + 26;
        float s = 11.5f, h = Ui.lineH(Ui.MONO, s) + 8;
        float w = Ui.width(Ui.MONO, "CLOSE ✕", s, .06f);
        float ly = y + (24 - h) / 2;
        boolean hover = Ui.in(mx, my, x, ly, w, h);
        Ui.mono("Close ✕", x, ly + 4, s, Theme.alpha(hover ? Theme.INK : Theme.INK_DIM, p), .06f);
        hits.add(new Object[]{new float[]{x, ly, w, h}, (Runnable) this::onClose});
    }

    private void drawList(Theme th, List<Entry> list, float mx, float my) {
        float p = a(openedAt, 550, 60);
        float x0 = Ui.vw * 0.05f - 20 * (1 - p), w = listW();
        float y = headTop() + 24 + Ui.vh * 0.05f;
        Ui.mono("Mods · " + all.size() + " installed", x0, y, 11.5f, Theme.alpha(Theme.INK_DIM, p), .06f);
        y += Ui.lineH(Ui.MONO, 11.5f) + 14;
        // the search line (just type)
        boolean typing = !search.isEmpty();
        long now = System.currentTimeMillis();
        if (typing) {
            float tw = Ui.text(Ui.TEXT, search, x0, y, 14, Theme.alpha(Theme.INK, p));
            if (now / 500 % 2 == 0) Ui.rect(x0 + tw + 1, y + 1, 1, Ui.lineH(Ui.TEXT, 14) - 2, Theme.alpha(Theme.INK, p));
        } else {
            Ui.text(Ui.TEXT, "Type to search", x0, y, 14, Theme.alpha(Theme.INK_DIM, p));
        }
        y += Ui.lineH(Ui.TEXT, 14) + 8;
        Ui.rect(x0, y, w, 1, Theme.alpha(typing ? th.a1() : Theme.INK_FAINT, p));
        y += 8;
        float top = y, bottom = Ui.vh - Ui.vh * 0.05f;
        viewH = bottom - top;
        float rh = 46;
        contentH = list.size() * rh;
        scrollTarget = Math.max(0, Math.min(Math.max(0, contentH - viewH), scrollTarget));
        scroll += (scrollTarget - scroll) * 0.35f;
        if (list.isEmpty()) Ui.text(Ui.TEXT, "No mods match \"" + search + "\"", x0, top + 12, 14, Theme.alpha(Theme.INK_DIM, p));
        Ui.clip(x0 - 4, top, w + 8, viewH);
        for (int i = 0; i < list.size(); i++) {
            float ry = top + i * rh - scroll;
            if (ry + rh < top || ry > bottom) continue;
            Entry e = list.get(i);
            boolean sel = i == selected, hover = Ui.in(mx, my, x0, ry, w, rh - 1) && my >= top && my <= bottom;
            if (sel || hover) Ui.rect(x0, ry, w, rh - 1, Theme.alpha(sel ? 0x0FFFFFFF : 0x09FFFFFF, p));
            if (sel) Ui.rect(x0, ry + 8, 2, rh - 17, Theme.alpha(th.a1(), p));
            Ui.text(Ui.SEMI, ModMenuScreen.wrap(Ui.SEMI, e.name, 15, 0, w - 28).get(0), x0 + 14, ry + 7, 15, Theme.alpha(Theme.INK, p));
            Ui.mono(e.version, x0 + 14, ry + 9 + Ui.lineH(Ui.SEMI, 15), 10.5f, Theme.alpha(sel ? th.a1() : Theme.INK_DIM, p), .04f);
            Ui.rect(x0, ry + rh - 1, w, 1, Theme.alpha(Theme.INK_FAINT, p));
            final int idx = i;
            if (ry >= top - rh / 2 && ry + rh / 2 <= bottom) {
                hits.add(new Object[]{new float[]{x0, Math.max(top, ry), w, rh - 1}, (Runnable) () -> {
                    if (selected != idx) {
                        selected = idx;
                        pickedAt = System.nanoTime();
                    }
                }, hasSettings(e) ? (Runnable) () -> openSettings(e) : null});
            }
        }
        Ui.unclip();
    }

    private void drawDetails(Theme th, Entry e, float mx, float my) {
        float p = a(pickedAt, 400, 0);
        float x0 = Ui.vw * 0.05f + listW() + Ui.vw * 0.05f + 20 * (1 - p), x1 = Ui.vw * 0.95f, w = x1 - x0;
        float y = headTop() + 24 + Ui.vh * 0.05f;
        Ui.mono(e.id, x0, y, 11.5f, Theme.alpha(Theme.INK_DIM, p), .06f);
        y += Ui.lineH(Ui.MONO, 11.5f) + 10;
        float ws = Math.max(30, Math.min(54, Ui.vh * 0.06f));
        for (String line : ModMenuScreen.wrap(Ui.WORD, e.name.toUpperCase(Locale.ROOT), ws, -.01f, w)) {
            Ui.text(Ui.WORD, line, x0, y, ws, Theme.alpha(Theme.INK, p), -.01f, .98f);
            y += ws * 0.98f;
        }
        y += 12;
        String meta = "Version " + e.version + (e.authors.isEmpty() ? "" : "  ·  by " + e.authors);
        for (String line : ModMenuScreen.wrap(Ui.MONO, meta, 11.5f, .04f, w)) {
            Ui.mono(line, x0, y, 11.5f, Theme.alpha(th.a1(), p), .04f);
            y += 11.5f * 1.7f;
        }
        if (hasSettings(e)) {
            String label = "Settings ›";
            float s = 11.5f, cw = Ui.chipW(label, s), ch = Ui.chipH(s);
            boolean hover = Ui.in(mx, my, x0, y + 4, cw, ch);
            Ui.chip(label, x0, y + 4, s, hover, th.a1(), p);
            hits.add(new Object[]{new float[]{x0, y + 4, cw, ch}, (Runnable) () -> openSettings(e)});
            if (e.id.equals(failedId) && System.currentTimeMillis() - failedAt < 6000)
                Ui.text(Ui.TEXT, "Its settings page couldn't open (the game log says why).", x0 + cw + 12,
                    y + 4 + (ch - Ui.lineH(Ui.TEXT, 13)) / 2, 13, Theme.alpha(0xFFE07A6E, p));
            y += ch + 8;
        }
        y += 14;
        String desc = e.description.isEmpty() ? "No description." : e.description;
        for (String line : ModMenuScreen.wrap(Ui.TEXT, desc, 15, 0, Math.min(w, 720))) {
            Ui.text(Ui.TEXT, line, x0, y, 15, Theme.alpha(Theme.INK, .85f * p), 0, 1.5f);
            y += 15 * 1.5f;
        }
        y += 18;
        if (!e.license.isEmpty()) {
            Ui.mono("License: " + e.license, x0, y, 11, Theme.alpha(Theme.INK_DIM, p), .06f);
            y += Ui.lineH(Ui.MONO, 11) + 10;
        }
        if (!e.homepage.isEmpty() && (e.homepage.startsWith("https://") || e.homepage.startsWith("http://"))) {
            String label = "Website →";
            float s = 11.5f, h = Ui.lineH(Ui.MONO, s) + 8, lw = Ui.width(Ui.MONO, label.toUpperCase(Locale.ROOT), s, .06f);
            boolean hover = Ui.in(mx, my, x0, y - 4, lw, h);
            Ui.mono(label, x0, y, s, Theme.alpha(hover ? Theme.INK : th.a1(), p), .06f);
            String url = e.homepage;
            hits.add(new Object[]{new float[]{x0, y - 4, lw, h}, (Runnable) () -> V.openLink(this, url)});
            y += h + 6;
        }
        if (e.id.equals("treasureclient") && !Boosts.ALL.isEmpty()) drawBoosts(th, x0, y + 18, Math.min(w, 720), p, mx, my);
    }

    /** Treasure Client's page: the built-in boosts, each with its switch and what it's doing. */
    private void drawBoosts(Theme th, float x0, float y, float w, float p, float mx, float my) {
        Ui.mono("Built-in boosts", x0, y, 11.5f, Theme.alpha(th.a1(), p), .06f);
        y += Ui.lineH(Ui.MONO, 11.5f) + 4;
        Ui.text(Ui.TEXT, "All on by default. Only switch one off if it causes a problem.", x0, y, 13, Theme.alpha(Theme.INK_DIM, p));
        y += Ui.lineH(Ui.TEXT, 13) + 10;
        float rh = 40;
        for (Boosts.Boost b : Boosts.ALL) {
            if (y + rh > Ui.vh - 8) break;
            boolean can = Boosts.switchable(b), on = can && Boosts.switchedOn(b);
            float swX = x0, swY = y + (rh - 22) / 2;
            boolean hover = can && Ui.in(mx, my, x0, y, w, rh);
            if (hover) Ui.round(swX - 3, swY - 3, 46, 28, 14, Theme.alpha(0x1AECEBE6, p));
            Ui.round(swX, swY, 40, 22, 11, Theme.alpha(on ? th.a1() : 0x29ECEBE6, can ? p : p * .45f));
            Ui.circle(swX + 11 + (on ? 18 : 0), swY + 11, 8, Theme.alpha(on ? 0xFF111111 : Theme.INK, can ? p : p * .45f));
            float tx = x0 + 40 + 14;
            Ui.text(Ui.SEMI, b.name(), tx, y + 3, 14.5f, Theme.alpha(Theme.INK, p));
            String status = Boosts.status(b);
            float sw = Ui.text(Ui.MONO, status.toUpperCase(Locale.ROOT), tx, y + 5 + Ui.lineH(Ui.SEMI, 14.5f), 10, Theme.alpha(
                status.equals("Working") ? th.a1() : Theme.INK_DIM, p), .04f, 0);
            Ui.text(Ui.TEXT, ModMenuScreen.wrap(Ui.TEXT, "· " + b.desc(), 12.5f, 0, w - 54 - sw - 10).get(0), tx + sw + 10,
                y + 3 + Ui.lineH(Ui.SEMI, 14.5f), 12.5f, Theme.alpha(Theme.INK_DIM, .8f * p));
            if (can) hits.add(new Object[]{new float[]{x0, y, w, rh}, (Runnable) () -> {
                Boosts.setSwitchedOn(b, !Boosts.switchedOn(b));
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
            }});
            y += rh;
        }
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() != 0 && e.button() != 1) return true;
        float mx = Ui.mx(e.x()), my = Ui.my(e.y());
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] h = hits.get(i);
            float[] r = (float[]) h[0];
            if (Ui.in(mx, my, r[0], r[1], r[2], r[3])) {
                if (e.button() == 0) ((Runnable) h[1]).run();
                else if (h.length > 2 && h[2] != null) ((Runnable) h[2]).run();  // right click: the row's settings
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        scrollTarget = Math.max(0, Math.min(Math.max(0, contentH - viewH), scrollTarget - (float) v * 64));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        if (key == InputConstants.KEY_ESCAPE) {
            if (!search.isEmpty()) {
                search = "";
                selected = 0;
                scroll = scrollTarget = 0;
                return true;
            }
            onClose();
            return true;
        }
        if (key == InputConstants.KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.offsetByCodePoints(search.length(), -1));
            selected = 0;
            scroll = scrollTarget = 0;
            return true;
        }
        int n = listed().size();
        if ((key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) && n > 0) {
            selected = Math.floorMod(selected + (key == InputConstants.KEY_DOWN ? 1 : -1), n);
            pickedAt = System.nanoTime();
            // keep the picked row in view
            float rowTop = selected * 46f;
            if (rowTop < scrollTarget) scrollTarget = rowTop;
            else if (rowTop + 46 > scrollTarget + viewH) scrollTarget = rowTop + 46 - viewH;
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (e.isAllowedChatCharacter() && search.length() < 40) {
            search += e.codepointAsString();
            selected = 0;
            scroll = scrollTarget = 0;
            pickedAt = System.nanoTime();
            return true;
        }
        return false;
    }
}
