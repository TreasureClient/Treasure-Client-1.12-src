package net.treasureclient.hud;

import net.treasureclient.compat.V;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.treasureclient.core.Config;
import net.treasureclient.core.Theme;
import net.treasureclient.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The HUD mods in the 1.5 look: dark see-through boxes with a thin bar in the theme colour on the left,
 * a small theme-coloured label and a white value (the same sizes as the browser client, in CSS px).
 */
public final class Hud {
    private Hud() {}

    /** A HUD element: draws itself at (x, y) or, with draw = false, only measures. Returns {w, h}. */
    interface Element {
        float[] render(float x, float y, boolean draw);
    }

    /** Where a HUD element sits by default: which corner/edge it hangs from and the CSS offsets. */
    public record Anchor(String h, float hx, String v, float vy) {}

    public record Entry(String id, String mod, Anchor anchor, Element el) {}

    public static final List<Entry> ENTRIES = new ArrayList<>();
    private static final float LABEL = 9.5f, VALUE = 14f;
    private static final int LOW = 0xFFFF6B57;

    static {
        ENTRIES.add(new Entry("coords", "coords", new Anchor("left", 18, "top", 18), Hud::coords));
        ENTRIES.add(new Entry("direction", "direction", new Anchor("center", 0, "top", 18), (x, y, d) -> line(x, y, d, "Facing", direction(), "", 0)));
        ENTRIES.add(new Entry("ping", "ping", new Anchor("right", 18, "top", 18), Hud::ping));
        ENTRIES.add(new Entry("armor", "armorStatus", new Anchor("right", 18, "top", 56), Hud::armor));
        ENTRIES.add(new Entry("sprint", "toggleSprint", new Anchor("left", 18, "top", 56),
            (x, y, d) -> line(x, y, d, "Sprint", Toggles.sprint ? "On" : "Off", "", Toggles.sprint ? 1 : -1)));
        ENTRIES.add(new Entry("sneak", "toggleSneak", new Anchor("left", 18, "top", 84),
            (x, y, d) -> line(x, y, d, "Sneak", Toggles.sneak ? "On" : "Off", "", Toggles.sneak ? 1 : -1)));
        ENTRIES.add(new Entry("reach", "reachDisp", new Anchor("left", 18, "top", 112), Hud::reach));
        ENTRIES.add(new Entry("combo", "combo", new Anchor("left", 18, "top", 140),
            (x, y, d) -> line(x, y, d, "Combo", String.valueOf(Trackers.combo), "", Trackers.combo > 0 ? 0 : -1)));
        ENTRIES.add(new Entry("items", "itemCount", new Anchor("left", 18, "top", 168), Hud::items));
        ENTRIES.add(new Entry("perfmon", "perfMonitor", new Anchor("left", 18, "top", 60), Hud::perf));
        ENTRIES.add(new Entry("potions", "potionEffects", new Anchor("right", 18, "bottom", 130), Hud::potions));
        ENTRIES.add(new Entry("held", "heldItem", new Anchor("center", 0, "bottom", 128), Hud::held));
        ENTRIES.add(new Entry("fpsgraph", "fpsGraph", new Anchor("center", 0, "bottom", 18), Hud::graph));
        ENTRIES.add(new Entry("keystrokes", "keystrokes", new Anchor("left", 18, "bottom", 66), Hud::keys));
        ENTRIES.add(new Entry("cps", "cps", new Anchor("left", 18, "bottom", 34), (x, y, d) -> line(x, y, d, "CPS", String.valueOf(Trackers.cps(true)), "", 0)));
        ENTRIES.add(new Entry("fps", "fps", new Anchor("left", 18, "bottom", 6),
            (x, y, d) -> line(x, y, d, "FPS", String.valueOf(Minecraft.getInstance().getFps()), "", 0)));
    }

    static boolean shown(Entry e) {
        Config c = Config.get();
        if (e.id().equals("sprint")) return c.on("toggleSprint") && c.flag("sprintStatus", false);
        if (e.id().equals("sneak")) return c.on("toggleSneak") && c.flag("sneakStatus", false);
        if (e.id().equals("reach") && c.str("reachStyle", "box").equals("cross")) return false;
        return c.on(e.mod());
    }

    /** Top-left corner of an element in CSS px (dragged position, or its default). */
    static float[] position(Entry e, float w, float h) {
        float[] saved = Config.get().hud.get(e.id());
        if (saved != null && saved.length == 2) {
            float x = saved[0] * Ui.vw, y = saved[1] * Ui.vh;
            return new float[]{Math.max(0, Math.min(Ui.vw - w, x)), Math.max(0, Math.min(Ui.vh - h, y))};
        }
        Anchor a = e.anchor();
        float x = switch (a.h()) {
            case "right" -> Ui.vw - a.hx() - w;
            case "center" -> (Ui.vw - w) / 2;
            default -> a.hx();
        };
        float y = a.v().equals("bottom") ? Ui.vh - a.vy() - h : a.vy();
        return new float[]{x, y};
    }

    /** Draws the whole HUD (called every frame by the HUD layer). */
    public static void render() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || V.hudHidden()) return;
        if (V.screen() instanceof HudEditorScreenMarker) return; // the editor draws its own copy
        Trackers.sampleFps(mc.getFps());
        Ui.begin();
        try {
            drawShown(null);
            if (Config.get().on("reachDisp") && Config.get().str("reachStyle", "box").equals("cross")) reachCross();
        } finally {
            Ui.end();
        }
    }

    /** Draws every shown element; returns their boxes {x, y, w, h} by id (for the editor). */
    public static java.util.Map<String, float[]> drawAll(String highlight) {
        java.util.Map<String, float[]> boxes = new java.util.LinkedHashMap<>();
        drawShown(boxes);
        return boxes;
    }

    /** Each element's size last frame (null = not shown last frame). */
    private static float[][] lastSize = new float[0][];

    /**
     * Draws every shown element. Each one is placed using its size from the frame before, so it's
     * worked out once per frame instead of twice (measured, then drawn); only an element that just
     * appeared is measured first.
     */
    private static void drawShown(java.util.Map<String, float[]> boxes) {
        if (lastSize.length != ENTRIES.size()) lastSize = new float[ENTRIES.size()][];
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry e = ENTRIES.get(i);
            if (!shown(e)) {
                lastSize[i] = null;
                continue;
            }
            float[] size = lastSize[i];
            if (size == null) size = e.el().render(0, 0, false);
            float[] pos = position(e, size[0], size[1]);
            float[] drawn = e.el().render(pos[0], pos[1], true);
            lastSize[i] = drawn;
            if (boxes != null) boxes.put(e.id(), new float[]{pos[0], pos[1], drawn[0], drawn[1]});
        }
    }

    // ------------------------------------------------------------------ small helpers (no String.format: it's slow)
    private static final java.util.HashMap<String, String> UPPER = new java.util.HashMap<>();

    /** The label in capitals (remembered: labels never change). */
    static String up(String s) {
        String u = UPPER.get(s);
        if (u == null) {
            u = s.toUpperCase(Locale.ROOT);
            if (UPPER.size() < 256) UPPER.put(s, u);
        }
        return u;
    }

    /** A number with one decimal ("12.3"). */
    static String dec1(double v) {
        long a = Math.round(Math.abs(v) * 10);
        return (v < 0 && a > 0 ? "-" : "") + (a / 10) + "." + (a % 10);
    }

    /** A number with two decimals ("3.05"). */
    static String dec2(double v) {
        long a = Math.round(Math.abs(v) * 100), f = a % 100;
        return (v < 0 && a > 0 ? "-" : "") + (a / 100) + "." + (f < 10 ? "0" : "") + f;
    }

    // ------------------------------------------------------------------ the box
    static void box(float x, float y, float w, float h) {
        Ui.round(x, y, w, h, 3, Theme.HUD_BG);
        Ui.rect(x, y + 1, 2, h - 2, Theme.current().a1());
    }

    /** One line: LABEL value unit. tone 1 = value in the theme colour, -1 = dimmed. */
    static float[] line(float x, float y, boolean draw, String label, String value, String unit, int tone) {
        float lh = VALUE * 1.35f;
        float lw = Ui.width(Ui.MONOB, up(label), LABEL, .12f);
        float vw = Ui.width(Ui.HUD, value, VALUE, .01f);
        float uw = unit.isEmpty() ? 0 : 3 + Ui.width(Ui.MONO, unit, 10);
        float w = 11 + lw + 8 + vw + uw + 10, h = 5 + lh + 5;
        if (draw) {
            Theme th = Theme.current();
            box(x, y, w, h);
            float base = y + 5 + (lh - (Ui.asc(Ui.HUD) + 0.21f) * VALUE) / 2 + Ui.asc(Ui.HUD) * VALUE;
            float cx = x + 11;
            cx += Ui.textBase(Ui.MONOB, up(label), cx, base - 1, LABEL, th.a1(), .12f) + 8;
            int col = tone > 0 ? th.a1() : tone < 0 ? Theme.INK_DIM : Theme.INK;
            cx += Ui.textBase(Ui.HUD, value, cx, base, VALUE, col, .01f);
            if (!unit.isEmpty()) Ui.textBase(Ui.MONO, unit, cx + 3, base, 10, Theme.INK_DIM, 0);
        }
        return new float[]{w, h};
    }

    // ------------------------------------------------------------------ elements
    private static String fmt(double v) {
        return Config.get().str("coordPrecision", "1").equals("0") ? String.valueOf((long) Math.floor(v)) : dec1(v);
    }

    private static float[] coords(float x, float y, boolean draw) {
        Player p = Minecraft.getInstance().player;
        List<String[]> parts = new ArrayList<>();
        parts.add(new String[]{"X", fmt(p.getX())});
        parts.add(new String[]{"Y", fmt(p.getY())});
        parts.add(new String[]{"Z", fmt(p.getZ())});
        if (Config.get().flag("coordBiome", true)) parts.add(new String[]{"Biome", biome()});
        float lh = VALUE * 1.35f;
        float w = 11;
        for (int i = 0; i < parts.size(); i++) {
            w += Ui.width(Ui.MONOB, up(parts.get(i)[0]), LABEL, .12f) + 6 + Ui.width(Ui.HUD, parts.get(i)[1], VALUE, .01f);
            if (i < parts.size() - 1) w += 12;
        }
        w += 10;
        float h = 5 + lh + 5;
        if (draw) {
            Theme th = Theme.current();
            box(x, y, w, h);
            float base = y + 5 + (lh - (Ui.asc(Ui.HUD) + 0.21f) * VALUE) / 2 + Ui.asc(Ui.HUD) * VALUE;
            float cx = x + 11;
            for (String[] part : parts) {
                cx += Ui.textBase(Ui.MONOB, up(part[0]), cx, base - 1, LABEL, th.a1(), .12f) + 6;
                cx += Ui.textBase(Ui.HUD, part[1], cx, base, VALUE, Theme.INK, .01f) + 12;
            }
        }
        return new float[]{w, h};
    }

    private static long biomeAt = Long.MIN_VALUE;
    private static Object biomeLevel;
    private static String biomeName = "—";
    private static long biomeTime;

    /** The biome's name, looked up again when you move to another block (or once a second, for chunks still loading). */
    private static String biome() {
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = mc.player.blockPosition();
        long at = pos.asLong();
        long now = System.currentTimeMillis();
        if (at != biomeAt || mc.level != biomeLevel || now - biomeTime > 1000) {
            biomeAt = at;
            biomeTime = now;
            biomeLevel = mc.level;
            biomeName = mc.level.getBiome(pos).unwrapKey().map(k -> pretty(k.identifier().getPath())).orElse("—");
        }
        return biomeName;
    }

    static String pretty(String id) {
        StringBuilder b = new StringBuilder();
        for (String w : id.split("_")) {
            if (w.isEmpty()) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    private static String direction() {
        float yaw = Minecraft.getInstance().player.getYRot();
        String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        int i = Math.floorMod(Math.round(yaw / 45f), 8);
        String card = dirs[i];
        String axis = switch (card) {
            case "S" -> "+Z";
            case "W" -> "-X";
            case "N" -> "-Z";
            case "E" -> "+X";
            default -> "";
        };
        return Config.get().str("dirMode", "cardinal").equals("detailed") && !axis.isEmpty() ? card + " (" + axis + ")" : card;
    }

    private static float[] ping(float x, float y, boolean draw) {
        Minecraft mc = Minecraft.getInstance();
        int ms = -1;
        if (mc.getConnection() != null && !mc.isLocalServer()) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) ms = info.getLatency();
        }
        return ms >= 0 ? line(x, y, draw, "Ping", String.valueOf(ms), "ms", 0) : line(x, y, draw, "Ping", "—", "", -1);
    }

    private static float[] reach(float x, float y, boolean draw) {
        double r = Trackers.lastReach;
        return line(x, y, draw, "Reach", Double.isNaN(r) ? "0.00" : dec2(r), "blocks", 0);
    }

    /** Reach Display "Above crosshair": a small white number just over the crosshair. */
    private static void reachCross() {
        double r = Trackers.lastReach;
        String s = Double.isNaN(r) ? "0.00" : dec2(r);
        float w = Ui.width(Ui.MONOB, s, 10);
        Ui.text(Ui.MONOB, s, (Ui.vw - w) / 2, Ui.vh / 2 - 24, 10, 0xFFFFFFFF, 0, 1);
    }

    private static float[] held(float x, float y, boolean draw) {
        ItemStack st = Minecraft.getInstance().player.getMainHandItem();
        if (st.isEmpty()) return line(x, y, draw, "Holding", "—", "", -1);
        String txt = st.getHoverName().getString() + (st.getCount() > 1 ? " ×" + st.getCount() : "");
        if (st.isDamageableItem() && st.getMaxDamage() > 0)
            txt += "  " + Math.round(100f * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage()) + "%";
        return line(x, y, draw, "Holding", txt, "", 0);
    }

    private static float[] armor(float x, float y, boolean draw) {
        Player p = Minecraft.getInstance().player;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        String[] names = {"Helmet", "Chest", "Legs", "Boots"};
        String[] vals = new String[4];
        int[] cols = new int[4];
        float[] colW = new float[4];
        for (int i = 0; i < 4; i++) {
            ItemStack st = p.getItemBySlot(slots[i]);
            if (st.isEmpty()) {
                vals[i] = "—";
                cols[i] = Theme.INK_DIM;
            } else if (st.isDamageableItem() && st.getMaxDamage() > 0) {
                float frac = (st.getMaxDamage() - st.getDamageValue()) / (float) st.getMaxDamage();
                vals[i] = Math.round(frac * 100) + "%";
                cols[i] = frac < 0.25f ? LOW : Theme.INK;
            } else {
                vals[i] = "✓";
                cols[i] = Theme.INK;
            }
            colW[i] = Math.max(30, Math.max(Ui.width(Ui.MONOB, up(names[i]), LABEL, .12f), Ui.width(Ui.HUD, vals[i], VALUE)));
        }
        float labH = Ui.lineH(Ui.MONOB, LABEL), valH = VALUE * 1.35f;
        float w = 12 + colW[0] + colW[1] + colW[2] + colW[3] + 3 * 12 + 12, h = 6 + labH + 1 + valH + 6;
        if (draw) {
            Theme th = Theme.current();
            box(x, y, w, h);
            float cx = x + 12;
            for (int i = 0; i < 4; i++) {
                String lab = up(names[i]);
                Ui.text(Ui.MONOB, lab, cx + (colW[i] - Ui.width(Ui.MONOB, lab, LABEL, .12f)) / 2, y + 6, LABEL, th.a1(), .12f, 0);
                Ui.text(Ui.HUD, vals[i], cx + (colW[i] - Ui.width(Ui.HUD, vals[i], VALUE)) / 2, y + 6 + labH + 1, VALUE, cols[i], 0, 1.35f);
                cx += colW[i] + 12;
            }
        }
        return new float[]{w, h};
    }

    private static float[] potions(float x, float y, boolean draw) {
        Player p = Minecraft.getInstance().player;
        List<String[]> rows = new ArrayList<>();
        for (MobEffectInstance e : p.getActiveEffects()) {
            String name = e.getEffect().value().getDisplayName().getString() + (e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "");
            String time;
            if (e.isInfiniteDuration()) time = "∞";
            else {
                int secs = e.getDuration() / 20;
                time = (secs / 60) + ":" + (secs % 60 < 10 ? "0" : "") + (secs % 60);
            }
            rows.add(new String[]{name, time});
        }
        if (rows.isEmpty() && !(V.screen() instanceof HudEditorScreenMarker)) return new float[]{0, 0};
        if (rows.isEmpty()) rows.add(new String[]{"Speed 2", "1:30"});
        float s = 13.5f, lh = s * 1.35f, rh = 5 + lh + 5;
        float maxW = 0;
        float[] ws = new float[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            ws[i] = 11 + Ui.width(Ui.HUD, rows.get(i)[0], s) + 10 + Ui.width(Ui.MONO, rows.get(i)[1], 10.5f) + 10;
            maxW = Math.max(maxW, ws[i]);
        }
        float h = rows.size() * rh + (rows.size() - 1) * 4;
        if (draw) {
            for (int i = 0; i < rows.size(); i++) {
                float ry = y + i * (rh + 4), rx = x + maxW - ws[i]; // rows line up on the right
                box(rx, ry, ws[i], rh);
                float base = ry + 5 + (lh - (Ui.asc(Ui.HUD) + 0.21f) * s) / 2 + Ui.asc(Ui.HUD) * s;
                float cx = rx + 11 + Ui.textBase(Ui.HUD, rows.get(i)[0], rx + 11, base, s, Theme.INK, 0) + 10;
                Ui.textBase(Ui.MONO, rows.get(i)[1], cx, base, 10.5f, Theme.INK_DIM, 0);
            }
        }
        return new float[]{maxW, h};
    }

    private static float[] items(float x, float y, boolean draw) {
        Player p = Minecraft.getInstance().player;
        Config c = Config.get();
        int arrows = 0, pearls = 0, gapples = 0, totems = 0, splash = 0, blocks = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            if (st.is(Items.ARROW) || st.is(Items.SPECTRAL_ARROW) || st.is(Items.TIPPED_ARROW)) arrows += st.getCount();
            else if (st.is(Items.ENDER_PEARL)) pearls += st.getCount();
            else if (st.is(Items.GOLDEN_APPLE) || st.is(Items.ENCHANTED_GOLDEN_APPLE)) gapples += st.getCount();
            else if (st.is(Items.TOTEM_OF_UNDYING)) totems += st.getCount();
            else if (st.is(Items.SPLASH_POTION)) splash += st.getCount();
            else if (st.getItem() instanceof BlockItem) blocks += st.getCount();
        }
        List<Object[]> rows = new ArrayList<>();
        boolean hideZero = c.flag("icHideZero", true);
        Object[][] all = {
            {"icArrow", "Arrows", 0xFFCFD3D6, arrows, true}, {"icPearl", "Ender Pearls", 0xFF2FB39A, pearls, true},
            {"icGapple", "Golden Apples", 0xFFF2C94C, gapples, true}, {"icTotem", "Totems", 0xFFE3B341, totems, true},
            {"icPotion", "Splash Potions", 0xFFE0715A, splash, true}, {"icBlocks", "Blocks", 0xFFB08155, blocks, false}};
        for (Object[] r : all) {
            if (!c.flag((String) r[0], (Boolean) r[4])) continue;
            if ((Integer) r[3] == 0 && hideZero) continue;
            rows.add(r);
        }
        float s = 13.5f, lh = s * 1.35f;
        float nameW = 0, numW = 0;
        for (Object[] r : rows) {
            nameW = Math.max(nameW, Ui.width(Ui.HUD, (String) r[1], s));
            numW = Math.max(numW, Ui.width(Ui.HUD, String.valueOf(r[3]), s));
        }
        if (rows.isEmpty()) nameW = Ui.width(Ui.HUD, "No items", s);
        float w = Math.max(96, 11 + 6 + 7 + nameW + 10 + numW + 12);
        int n = Math.max(1, rows.size());
        float h = 6 + n * lh + (n - 1) + 6;
        if (draw) {
            box(x, y, w, h);
            if (rows.isEmpty()) Ui.text(Ui.HUD, "No items", x + 11, y + 6, s, Theme.INK_DIM, 0, 1.35f);
            for (int i = 0; i < rows.size(); i++) {
                Object[] r = rows.get(i);
                float ry = y + 6 + i * (lh + 1);
                int col = (Integer) r[3] == 0 ? 0x73000000 : 0;
                float alpha = (Integer) r[3] == 0 ? .45f : 1f;
                Ui.round(x + 11, ry + lh / 2 - 3, 6, 6, 1, Theme.alpha((Integer) r[2], alpha));
                Ui.text(Ui.HUD, (String) r[1], x + 11 + 6 + 7, ry, s, Theme.alpha(Theme.INK, alpha), 0, 1.35f);
                String num = String.valueOf(r[3]);
                Ui.text(Ui.HUD, num, x + w - 12 - Ui.width(Ui.HUD, num, s), ry, s, Theme.alpha(Theme.INK, alpha), 0, 1.35f);
            }
        }
        return new float[]{w, h};
    }

    private static float[] perf(float x, float y, boolean draw) {
        Minecraft mc = Minecraft.getInstance();
        int fps = mc.getFps();
        String frame = fps > 0 ? dec1(1000f / fps) + " ms" : "—";
        int ms = -1;
        if (mc.getConnection() != null && !mc.isLocalServer()) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) ms = info.getLatency();
        }
        String[][] rows = {{"FPS", String.valueOf(fps)}, {"Frame", frame}, {"Ping", ms >= 0 ? ms + " ms" : "n/a"}};
        float s = VALUE, lh = s * 1.35f;
        float w = 108;
        for (String[] r : rows)
            w = Math.max(w, 11 + Ui.width(Ui.MONOB, up(r[0]), LABEL, .12f) + 14 + Ui.width(Ui.HUD, r[1], s) + 10);
        float h = 5 + 3 * lh + 2 * 2 + 5;
        if (draw) {
            Theme th = Theme.current();
            box(x, y, w, h);
            for (int i = 0; i < 3; i++) {
                float ry = y + 5 + i * (lh + 2);
                float base = ry + (lh - (Ui.asc(Ui.HUD) + 0.21f) * s) / 2 + Ui.asc(Ui.HUD) * s;
                Ui.textBase(Ui.MONOB, up(rows[i][0]), x + 11, base - 1, LABEL, th.a1(), .12f);
                Ui.textBase(Ui.HUD, rows[i][1], x + w - 10 - Ui.width(Ui.HUD, rows[i][1], s), base, s, Theme.INK, 0);
            }
        }
        return new float[]{w, h};
    }

    private static float[] graph(float x, float y, boolean draw) {
        float gw = 140, gh = 34;
        float w = 9 + gw + 8, h = 6 + gh + 6;
        if (draw) {
            Theme th = Theme.current();
            box(x, y, w, h);
            int n = Trackers.fpsCount;
            if (n > 0) {
                int max = 60;
                for (int i = 0; i < n; i++) max = Math.max(max, Trackers.FPS_HISTORY[i]);
                float colW = gw / Math.max(1, n - 1 == 0 ? 1 : 70);
                float gx = x + 9, gy = y + 6;
                for (int i = 0; i < n; i++) {
                    float v = Trackers.FPS_HISTORY[i] / (float) max;
                    float cx = gx + i * gw / 69f, top = gy + gh - v * (gh - 2) - 1;
                    Ui.rect(cx, top, Math.max(1, colW), gy + gh - top, Theme.alpha(th.a1(), .18f));
                    Ui.rect(cx, top, Math.max(1.5f, colW), 1.5f, th.a1());
                }
            }
        }
        return new float[]{w, h};
    }

    private static float[] keys(float x, float y, boolean draw) {
        Minecraft mc = Minecraft.getInstance();
        Config c = Config.get();
        boolean mouse = c.flag("ksShowMouse", true), space = c.flag("ksShowSpace", true);
        float k = 33, gap = 4;
        float w = 3 * k + 2 * gap;
        float h = 2 * k + gap + (mouse ? k + gap : 0) + (space ? 20 + gap : 0);
        if (draw) {
            var o = mc.options;
            key(x + k + gap, y, k, k, "W", o.keyUp.isDown(), false);
            float ry = y + k + gap;
            key(x, ry, k, k, "A", o.keyLeft.isDown(), false);
            key(x + k + gap, ry, k, k, "S", o.keyDown.isDown(), false);
            key(x + 2 * (k + gap), ry, k, k, "D", o.keyRight.isDown(), false);
            ry += k + gap;
            if (mouse) {
                float mw = (w - gap) / 2;
                key(x, ry, mw, k, "LMB", o.keyAttack.isDown(), true);
                key(x + mw + gap, ry, mw, k, "RMB", o.keyUse.isDown(), true);
                ry += k + gap;
            }
            if (space) key(x, ry, w, 20, "SPACE", o.keyJump.isDown(), true);
        }
        return new float[]{w, h};
    }

    private static void key(float x, float y, float w, float h, String label, boolean down, boolean mono) {
        Theme th = Theme.current();
        if (down) {
            float sx = w * 0.03f, sy = h * 0.03f; // scale(.94)
            Ui.round(x + sx, y + sy, w - 2 * sx, h - 2 * sy, 3, th.a1());
        } else Ui.round(x, y, w, h, 3, Theme.HUD_BG);
        int col = down ? 0xFF0B0B0D : Theme.INK;
        if (mono) {
            float size = label.equals("SPACE") ? 9 : 9.5f, ls = label.equals("SPACE") ? .3f : .12f;
            float tw = Ui.width(Ui.MONO, label, size, ls);
            Ui.text(Ui.MONO, label, x + (w - tw) / 2 + ls * size / 2, y + (h - Ui.lineH(Ui.MONO, size)) / 2, size, col, ls, 0);
        } else {
            float tw = Ui.width(Ui.HUD, label, VALUE);
            Ui.text(Ui.HUD, label, x + (w - tw) / 2, y + (h - Ui.lineH(Ui.HUD, VALUE)) / 2, VALUE, col, 0, 0);
        }
    }

    /** Marker for the HUD editor screen (the normal HUD stays hidden while it shows its own copy). */
    public interface HudEditorScreenMarker {}
}
