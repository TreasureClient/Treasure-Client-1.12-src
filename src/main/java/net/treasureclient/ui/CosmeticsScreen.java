package net.treasureclient.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.treasureclient.compat.TCScreen;
import net.treasureclient.compat.V;
import net.treasureclient.core.Anim;
import net.treasureclient.core.Config;
import net.treasureclient.core.Theme;
import net.treasureclient.cos.TCCapeArt;
import net.treasureclient.cos.TCCosmetics;
import net.treasureclient.cos.TCShare;

/**
 * The 1.5 Cosmetics screen, laid out like the browser client's: Capes, Auras, Hats, Boots and the
 * Maker as big stepped words on the left, each cosmetic as a card in the middle, and your player on
 * the right wearing what you pick (it turns round to show it; drag it to spin). Everything you pick
 * is saved straight away and shared with other Treasure Client players.
 */
public class CosmeticsScreen extends TCScreen {
    private static final int CARD_BG = 0x09FFFFFF, CARD_HOVER = 0x12FFFFFF, CHIP_BG = 0x0DFFFFFF, CHIP_HOVER = 0x1AFFFFFF;

    // ------------------------------------------------------------------ what's on offer (id, name, description)
    private static final String[][] CAPES = {
        {"none", "None", "Your normal Minecraft cape"},
        {"treasure", "Treasure", "Gold chest, a shine sweeps across"},
        {"frost", "Frost", "Snowflakes drifting over ice"},
        {"tropical", "Tropical", "A sunset over rolling waves"},
        {"ember", "Ember", "Lava cracks that pulse"},
        {"aurora", "Aurora", "Northern lights over pines"},
        {"galaxy", "Galaxy", "A drifting nebula and stars"},
        {"circuit", "Circuit", "Light running along the tracks"},
        {"theme", "Your theme", "Changes with your TC theme"},
        {"custom", "Made by you", "The cape from the Maker"}};
    private static final String[][] AURAS = {
        {"none", "None", "No aura"},
        {"halo", "Halo", "A thin ring round your waist"},
        {"orbit", "Orbit", "Three lights circling you"},
        {"rising", "Rising", "Soft dots floating up"},
        {"ripple", "Ripple", "Circles spreading on the ground"},
        {"helix", "Helix", "Two strands spiralling round you"},
        {"custom", "Made by you", "The aura from the Maker"}};
    private static final String[][] HATS = {
        {"none", "None", "No hat"},
        {"crown", "Crown", "Gold, with softly glowing gems"},
        {"tophat", "Top hat", "Tall and dapper, a little tilted"},
        {"wizard", "Wizard", "Bends and sways, stars twinkle"},
        {"beanie", "Beanie", "Knitted, with a pompom"},
        {"catears", "Cat ears", "They twitch now and then"},
        {"horns", "Horns", "Curled, with glowing tips"},
        {"propeller", "Propeller", "A cap with a spinning propeller"},
        {"angel", "Angel ring", "A glowing ring floating above you"},
        {"custom", "Made by you", "The hat from the Maker"}};
    private static final String[][] BOOTS = {
        {"none", "None", "No boots"},
        {"sneakers", "Sneakers", "Clean, with a coloured stripe"},
        {"rocket", "Rocket", "Flames when you jump or sprint"},
        {"winged", "Winged", "Little wings that flap"},
        {"cloud", "Cloud", "Puffs when you jump and land"},
        {"frost", "Frost", "Icy footprints where you walk"},
        {"magma", "Magma", "Glowing cracks, ember steps"},
        {"custom", "Made by you", "The boots from the Maker"}};

    // colours: id, name, rgb (-1 = drawn specially: theme, rainbow, its own colours)
    private static final Object[][] COLORS = {
        {"theme", "Your theme", -1}, {"gold", "Gold", 0xFFD76B}, {"ice", "Ice", 0x7FE9FF}, {"aqua", "Aqua", 0x45E3C6},
        {"ember", "Ember", 0xFF7A3C}, {"violet", "Violet", 0xB38CFF}, {"rose", "Rose", 0xFF6FA8}, {"lime", "Lime", 0x9BEA5B},
        {"white", "White", 0xF4F4F4}, {"rainbow", "Rainbow", -1}};
    private static final Object[][] WEAR = {
        {"natural", "Its own colours", -1}, {"theme", "Your theme", -1}, {"gold", "Gold", 0xFFD76B}, {"ice", "Ice", 0x7FE9FF},
        {"aqua", "Aqua", 0x45E3C6}, {"ember", "Ember", 0xFF7A3C}, {"violet", "Violet", 0xB38CFF}, {"rose", "Rose", 0xFF6FA8},
        {"lime", "Lime", 0x9BEA5B}, {"white", "White", 0xF4F4F4}, {"black", "Black", 0x26262B}, {"rainbow", "Rainbow", -1}};
    private static final Object[][] PALETTE = {
        {"theme", "Your theme", -1}, {"black", "Black", 0x16161A}, {"white", "White", 0xF0F0EA}, {"gold", "Gold", 0xFFD76B},
        {"red", "Red", 0xE8434F}, {"orange", "Orange", 0xFF8A3C}, {"yellow", "Yellow", 0xFFE066}, {"lime", "Lime", 0x9BEA5B},
        {"green", "Green", 0x2FBF71}, {"aqua", "Aqua", 0x45E3C6}, {"blue", "Blue", 0x4A8CFF}, {"violet", "Violet", 0x9A6BFF},
        {"pink", "Pink", 0xFF6FA8}};

    // the Maker's parts: ids and names
    private static final String[][] PATTERNS = {{"solid", "Solid"}, {"fade", "Fade"}, {"stripes", "Stripes"}, {"chevron", "Chevron"},
        {"checks", "Checks"}, {"waves", "Waves"}, {"stars", "Stars"}, {"split", "Split"}};
    private static final String[][] EMBLEMS = {{"none", "None"}, {"star", "Star"}, {"heart", "Heart"}, {"diamond", "Diamond"},
        {"bolt", "Bolt"}, {"moon", "Moon"}, {"crown", "Crown"}, {"sword", "Sword"}};
    private static final String[][] MOTIONS = {{"still", "Still"}, {"shine", "Shine"}, {"flow", "Flow"}, {"pulse", "Pulse"}, {"sparkle", "Sparkle"}};
    private static final String[][] AK_BASE = {{"none", "None"}, {"ring", "Ring"}, {"glow", "Ground glow"}, {"ripple", "Ripples"}, {"double", "Double ring"}};
    private static final String[][] AK_PART = {{"none", "None"}, {"orbit", "Orbiting lights"}, {"rising", "Rising dots"}, {"helix", "Helix"},
        {"sparkle", "Sparkles"}, {"snow", "Snow"}};
    private static final String[][] AK_HEIGHT = {{"low", "Feet"}, {"waist", "Waist"}, {"head", "Head"}};
    private static final String[][] HK_BASE = {{"none", "None"}, {"cap", "Cap"}, {"beanie", "Beanie"}, {"brim", "Bowler"}, {"crown", "Crown"}, {"band", "Headband"}};
    private static final String[][] HK_TOP = {{"none", "None"}, {"pompom", "Pompom"}, {"cone", "Cone"}, {"propeller", "Propeller"}, {"ears", "Ears"},
        {"horns", "Horns"}, {"antenna", "Antenna"}, {"halo", "Floating ring"}};
    private static final String[][] HK_TRIM = {{"none", "None"}, {"stripe", "Stripe"}, {"gems", "Glowing gems"}, {"stars", "Stars"}};
    private static final String[][] BK_SHAPE = {{"sneaker", "Sneaker"}, {"boot", "Tall boot"}, {"chunky", "Chunky"}, {"cloud", "Cloud"}};
    private static final String[][] BK_EXTRA = {{"none", "None"}, {"wings", "Wings"}, {"crystals", "Ice crystals"}, {"cracks", "Glowing cracks"},
        {"nozzle", "Rocket nozzle"}};
    private static final String[][] BK_TRAIL = {{"none", "None"}, {"prints", "Footprints"}, {"puffs", "Puffs"}, {"flames", "Flames"}, {"sparks", "Sparks"}};
    private static final String[][] SIZES = {{"small", "Small"}, {"normal", "Normal"}, {"big", "Big"}};
    private static final String[][] SPEEDS = {{"calm", "Calm"}, {"normal", "Normal"}, {"lively", "Lively"}};
    private static final String[][] ON_OFF = {{"1", "On"}, {"0", "Off"}};
    private static final String[][] TABS = {{"capes", "Capes"}, {"auras", "Auras"}, {"hats", "Hats"}, {"boots", "Boots"}, {"maker", "Maker"}};
    private static final String[][] MAKER_TABS = {{"cape", "Cape"}, {"aura", "Aura"}, {"hat", "Hat"}, {"boots", "Boots"}};

    // ------------------------------------------------------------------ little pixel pictures of the hats and boots (one letter per pixel)
    private static final String[][] PIX = {
        {"crown", "Y....Y....Y", "YY..YYY..YY", "YYY.YYY.YYY", "YYYYYYYYYYY", "YbYYYrYYYbY", "YYYYYYYYYYY", "yyyyyyyyyyy"},
        {"tophat", "..KKKKKKK..", "..KKKKKKK..", "..KKKKKKK..", "..KKKKKKK..", "..RRRGRRR..", "kkkkkkkkkkk"},
        {"wizard", ".........P.", "........PP.", ".......PPP.", "......PPPP.", ".....PSPP..", "....PPPPP..", "...PPPPSP..", "...PPPPPPP.", "...GGGGGGG.", "PPPPPPPPPPP"},
        {"beanie", "....WWW....", "....WWW....", "..RRRRRRR..", ".RRRRRRRRR.", ".RRRRRRRRR.", ".DDDDDDDDD.", ".DdDdDdDdD."},
        {"catears", ".C.......C.", ".CC.....CC.", ".CpC...CpC.", ".CppC.CppC.", ".CppCCCppC.", "CCCCCCCCCCC"},
        {"horns", "r.........r", "r.........r", "Rr.......rR", ".R.......R.", ".RR.....RR.", "..RR...RR.."},
        {"propeller", "YYYYYgBBBBB", ".....g.....", "...RRYYY...", "..RRRYYYY..", ".RRRRYYYYB.", ".GGGGGBBBB.", "RRR........"},
        {"angel", "..LLLLLLL..", ".L.......L.", "..LLLLLLL.."},
        {"sneakers", "...BBBB.", "...WWWW.", "...WKWW.", "..WWKWWW", "WWWBBBWW", "gggggggg"},
        {"rocket", "...OOOO.", "...MMMM.", "...MMMM.", "..MOOOMM", "MMMMMMMn", "dddddddn", "......FF", ".......F"},
        {"winged", "...TTTT.WWW", "...EEEEWWW.", "...EEEEWW..", "..EEEEEE...", "EEEEEEEE...", "TTTTTTTT..."},
        {"cloud", "...CCC...", "..CCCCC..", ".CCCCCCCC", "CCCCCCCCC", "cCCCCCCCc", ".ccccccc."},
        {"frost", "...IIII.", "..XIIII.", "...IIIIX", "..IIIIII", "IIIIIIIX", "jjjjjjjj"},
        {"magma", "...MMMM.", "...MOMM.", "...MOMM.", "..MMOOMM", "MOOMMMMM", "dddddddd"}};
    private static final String PIX_KEYS = "YybrKkRGPSWDdpLBgOMnFTECcIXj";
    private static final int[] PIX_RGB = {0xF2C14E, 0xB58A2C, 0x5AB8FF, 0xFF3B5C, 0x33333C, 0x45454F, 0xB3263A, 0xE8B94A, 0x4A35A0, 0xFFE58A,
        0xF2F2F2, 0xB23B3B, 0x8E2C2C, 0xF29CB8, 0xFFE7A0, 0x4A8CFF, 0x9A9AA2, 0xFF7A3C, 0xA7B0BA, 0x3A3E45, 0xFFB23A, 0xFFD76B, 0xEDE6D6,
        0xF4F6FA, 0xD9DDE6, 0x9FDFFF, 0xE8FBFF, 0x6FA8C4};

    // ------------------------------------------------------------------ state
    private final Screen parent;
    private String tab = "capes", makerTab = "cape";
    private float scroll, scrollTarget, contentH, viewH;
    private final long openedAt = System.nanoTime();
    private long pageAt = System.nanoTime();
    private final Anim[] tabBar = new Anim[5], tabShift = new Anim[5];
    private final List<Object[]> hits = new ArrayList<>();
    private PlayerSkinWidget player;
    private boolean draggingPlayer;
    // turning the player round to show what you picked
    private float turnTo = Float.NaN;
    private long turnUntil, lastFrame = System.nanoTime();
    private static final float FACE = 30f; // where Minecraft starts the player

    public CosmeticsScreen(Screen parent) {
        super(Component.literal("Cosmetics"));
        this.parent = parent;
        for (int i = 0; i < 5; i++) {
            tabBar[i] = new Anim(0, 300);
            tabShift[i] = new Anim(0, 300);
        }
    }

    @Override
    protected void init() {
        // your player on the right, a little bigger than on the main menu (drag to spin)
        int h = Math.round(height * 0.46f), w = Math.round(h * 0.5f);
        int cx = Math.round(width * 0.78f), top = Math.round(height * 0.20f);
        var skins = minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), false);
        player = new PlayerSkinWidget(w, h, minecraft.getEntityModels(), skins);
        player.setPosition(cx - w / 2, top);
        addRenderableWidget(player);
    }

    @Override
    public void onClose() {
        Config.get().saveNow();
        V.open(parent);
    }

    // ------------------------------------------------------------------ layout
    private static float a(long start, float ms, float delay) {
        return Anim.since(start, ms, delay);
    }

    private float wordSize() {
        return Math.max(26, Math.min(46, Ui.vh * 0.052f));
    }

    private float midX() {
        return Ui.vw * 0.29f;
    }

    private float midW() {
        return Ui.vw * 0.35f;
    }

    private float midTop() {
        return Ui.vh * 0.13f;
    }

    private float midBottom() {
        return Ui.vh * 0.95f;
    }

    private int accent() {
        return Theme.current().a1();
    }

    // ------------------------------------------------------------------ drawing
    @Override
    protected void draw(int mouseX, int mouseY, float delta) {
        long nowNs = System.nanoTime();
        float dt = Math.min(0.1f, (nowNs - lastFrame) / 1e9f);
        lastFrame = nowNs;
        Theme th = Theme.current();
        Ui.begin();
        float mx = Ui.mx(mouseX), my = Ui.my(mouseY);
        hits.clear();
        try {
            if (minecraft.level == null) TitleBackground.draw(th);
            Ui.gradientH(0, 0, Ui.vw, Ui.vh, new float[]{0, .4f, .64f, 1}, new int[]{0xDB0C0C0E, 0xBD0C0C0E, 0x4D0C0C0E, 0x290C0C0E});
            drawTop(mx, my);
            drawLeft(th, mx, my);
            drawMid(th, mx, my);
            drawWho(th);
        } finally {
            Ui.end();
        }
        turn(dt);
        PlayerLook.aim(player, mouseX, mouseY);
        drawWidgets(mouseX, mouseY, delta);
    }

    private void drawTop(float mx, float my) {
        float p = a(openedAt, 450, 0);
        float x = Ui.vw * 0.05f, y = Ui.vh * 0.042f, lh = 22;
        Ui.image(Ui.chest(), x, y, lh * 91f / 92f, lh, 91, 92, Theme.alpha(0xFFFFFFFF, p));
        Ui.text(Ui.BRAND, "TREASURE CLIENT", x + lh * 91f / 92f + 10, y + (lh - Ui.lineH(Ui.BRAND, 15)) / 2, 15, Theme.alpha(Theme.INK, p), .14f, 0);
        String back = "← Back";
        float s = 11.5f, w = Ui.width(Ui.MONO, back.toUpperCase(Locale.ROOT), s, .06f), h = Ui.lineH(Ui.MONO, s) + 8;
        float bx = Ui.vw * 0.95f - w, by = y + (lh - h) / 2;
        boolean hover = Ui.in(mx, my, bx, by, w, h);
        Ui.mono(back, bx, by + 4, s, Theme.alpha(hover ? Theme.INK : Theme.INK_DIM, p), .06f);
        hits.add(new Object[]{new float[]{bx, by, w, h}, (Runnable) this::onClose});
    }

    private void drawLeft(Theme th, float mx, float my) {
        float x0 = Ui.vw * 0.05f, y = midTop();
        float ls = 11.5f;
        float p0 = a(openedAt, 550, 60);
        Ui.mono("Cosmetics", x0 - 28 * (1 - p0), y, ls, Theme.alpha(Theme.INK_DIM, p0), .06f);
        y += Ui.lineH(Ui.MONO, ls) + Ui.vh * 0.02f;
        float ws = wordSize();
        for (int i = 0; i < TABS.length; i++) {
            boolean active = TABS[i][0].equals(tab);
            float itemH = Ui.vh * 0.022f + ws * 0.98f;
            float ix = x0 + i * Ui.vw * 0.016f;
            String word = TABS[i][1].toUpperCase(Locale.ROOT);
            float wordW = Ui.width(Ui.WORD, word, ws, -.01f);
            boolean hover = Ui.in(mx, my, ix, y, 38 + wordW + 14, itemH);
            float p = a(openedAt, 550, 60 + i * 60);
            float slide = -28 * (1 - p);
            float bw = tabBar[i].target(active ? Ui.vw * 0.05f - 10 + i * Ui.vw * 0.016f : 0).get();
            if (bw > 0.5f) Ui.rect(0, y + itemH / 2 - 1.5f, bw, 3, Theme.alpha(th.a1(), p));
            Ui.mono(String.format("%02d", i + 1), ix + slide, y + Ui.vh * 0.011f + Ui.vh * 0.007f, ls, Theme.alpha(active ? th.a1() : Theme.INK_DIM, p), .06f);
            float shift = tabShift[i].target(active ? 14 : 0).get();
            float op = active ? 1 : hover ? .75f : .42f;
            Ui.text(Ui.WORD, word, ix + 38 + shift + slide, y + Ui.vh * 0.011f, ws, Theme.alpha(Theme.INK, op * p), -.01f, .98f);
            final String id = TABS[i][0];
            hits.add(new Object[]{new float[]{ix, y, 38 + wordW + 14, itemH}, (Runnable) () -> setTab(id)});
            y += itemH;
        }
        // the foot: who sees these, and the skin settings
        float p = a(openedAt, 600, 300);
        String share = switch (TCShare.status()) {
            case "taken" -> "Your name's cosmetics are set from another device, so others won't see these.";
            case "offline" -> "Can't reach the cosmetics server: only you see these for now.";
            case "off" -> "Only you see these for now (sharing isn't set up yet).";
            default -> "Other Treasure Client players see what you wear.";
        };
        List<String> lines = wrap(Ui.MONO, share + "\nPress F5 in game to see yourself.", ls, .02f, Ui.vw * 0.22f);
        float lh = ls * 1.8f, linkH = Ui.lineH(Ui.MONO, ls) + 8;
        float fy = Ui.vh * 0.95f - lines.size() * lh - 6 - linkH;
        for (String line : lines) {
            Ui.text(Ui.MONO, line, x0, fy, ls, Theme.alpha(Theme.INK_DIM, p), .02f, 1.8f);
            fy += lh;
        }
        String link = "Skin customization →";
        float lw = Ui.width(Ui.MONO, link.toUpperCase(Locale.ROOT), ls, .06f);
        boolean hover = Ui.in(mx, my, x0, fy + 6, lw, linkH);
        Ui.mono(link, x0, fy + 10, ls, Theme.alpha(hover ? Theme.INK : th.a1(), p), .06f);
        hits.add(new Object[]{new float[]{x0, fy + 6, lw, linkH}, (Runnable) () -> V.open(new SkinCustomizationScreen(this, minecraft.options))});
    }

    private void setTab(String t) {
        if (!t.equals(tab)) {
            tab = t;
            scroll = scrollTarget = 0;
            pageAt = System.nanoTime();
        }
    }

    // ------------------------------------------------------------------ the middle: cards, colours, choices
    private float cx0, cw, clipTop, clipBottom, mmx, mmy;
    private float pageP;

    private void drawMid(Theme th, float mx, float my) {
        cx0 = midX();
        cw = midW() - 8;
        clipTop = midTop();
        clipBottom = midBottom();
        viewH = clipBottom - clipTop;
        mmx = mx;
        mmy = my;
        pageP = a(pageAt, 420, 40);
        scroll += (scrollTarget - scroll) * (Anim.enabled() ? 0.3f : 1f);
        Ui.clip(cx0 - 4, clipTop, cw + 12, viewH);
        float y0 = clipTop - scroll + 10 * (1 - pageP);
        float y = y0;
        TCCosmetics.Look look = TCCosmetics.mine();
        switch (tab) {
            case "capes" -> {
                y = sub("Animated capes", y);
                y = grid("cape", CAPES, look.cape, y);
            }
            case "auras" -> {
                y = sub("Auras", y);
                y = grid("aura", AURAS, look.aura, y);
                y = head("Aura colour", nameOf(COLORS, look.color), y + Ui.vh * 0.016f);
                y = swatches("color", COLORS, look.color, y);
            }
            case "hats" -> {
                y = sub("Hats", y);
                y = grid("hat", HATS, look.hat, y);
                y = head("Hat colour", nameOf(WEAR, look.hatColor), y + Ui.vh * 0.016f);
                y = swatches("hatc", WEAR, look.hatColor, y);
                y = note("A helmet hides your hat.", y);
            }
            case "boots" -> {
                y = sub("Boots", y);
                y = grid("boots", BOOTS, look.boots, y);
                y = head("Boots colour", nameOf(WEAR, look.bootColor), y + Ui.vh * 0.016f);
                y = swatches("bootc", WEAR, look.bootColor, y);
                y = head("Trail", null, y + Ui.vh * 0.01f);
                y = chips(ON_OFF, look.trail ? "1" : "0", y, v -> pick("trail", v));
                y = note("Armour boots hide them.", y);
            }
            default -> y = maker(look, y);
        }
        contentH = y - y0 + Ui.vh * 0.04f;
        Ui.unclip();
        // a thin scroll bar when it doesn't all fit
        if (contentH > viewH + 1) {
            float bh = Math.max(30, viewH * viewH / contentH);
            float by = clipTop + (viewH - bh) * (scroll / Math.max(1, contentH - viewH));
            Ui.round(cx0 + cw + 6, by, 3, bh, 1.5f, Theme.alpha(Theme.INK_DIM, .45f));
        }
    }

    /** True when a point of the middle is visible (clicks outside the scrolled area don't count). */
    private boolean visible(float y) {
        return y >= clipTop && y < clipBottom;
    }

    private void hit(float x, float y, float w, float h, Runnable r) {
        float top = Math.max(y, clipTop), bottom = Math.min(y + h, clipBottom);
        if (bottom > top) hits.add(new Object[]{new float[]{x, top, w, bottom - top}, r});
    }

    private boolean over(float x, float y, float w, float h) {
        return visible(mmy) && Ui.in(mmx, mmy, x, y, w, h);
    }

    private float sub(String label, float y) {
        y += Ui.vh * 0.014f;
        Ui.mono(label, cx0, y, 11.5f, Theme.alpha(Theme.INK_DIM, pageP), .06f);
        return y + Ui.lineH(Ui.MONO, 11.5f) + Ui.vh * 0.014f;
    }

    private float head(String label, String value, float y) {
        y += Ui.vh * 0.016f;
        float w = Ui.mono(label, cx0, y, 11.5f, Theme.alpha(Theme.INK_DIM, pageP), .06f);
        if (value != null) Ui.mono(value, cx0 + w + 8, y, 11.5f, Theme.alpha(Theme.INK, pageP), .06f);
        return y + Ui.lineH(Ui.MONO, 11.5f) + Ui.vh * 0.012f;
    }

    private float note(String text, float y) {
        y += Ui.vh * 0.02f;
        Ui.mono(text, cx0, y, 10.5f, Theme.alpha(Theme.INK_DIM, .8f * pageP), .04f);
        return y + Ui.lineH(Ui.MONO, 10.5f);
    }

    private float grid(String kind, String[][] items, String current, float y) {
        int cols = 3;
        float gap = 10, w = (cw - gap * (cols - 1)) / cols;
        float descS = 12, descLH = descS * 1.3f;
        float h = 10 + 44 + 8 + Ui.lineH(Ui.SEMI, 15) + 3 + descLH * 2 + 10;
        long ms = System.currentTimeMillis();
        for (int i = 0; i < items.length; i++) {
            float x = cx0 + (i % cols) * (w + gap), cy = y + (i / cols) * (h + gap);
            if (cy + h < clipTop || cy > clipBottom) continue;
            float p = a(pageAt, 420, 30 + Math.min(i, 9) * 22);
            float ry = cy + 10 * (1 - p);
            String id = items[i][0];
            boolean on = id.equals(current);
            boolean hover = over(x, cy, w, h);
            float lift = hover ? 2 : 0;
            ry -= lift;
            Ui.round(x, ry, w, h, 6, Theme.alpha(hover ? CARD_HOVER : CARD_BG, p));
            outline(x, ry, w, h, on ? 2 : 1, Theme.alpha(on ? accent() : Theme.INK_FAINT, p));
            preview(kind, id, x + 10, ry + 10, 44, p, ms);
            Ui.text(Ui.SEMI, items[i][1], x + 10, ry + 10 + 44 + 8, 15, Theme.alpha(Theme.INK, p));
            float dy = ry + 10 + 44 + 8 + Ui.lineH(Ui.SEMI, 15) + 3;
            int n = 0;
            for (String line : wrap(Ui.TEXT, items[i][2], descS, 0, w - 20)) {
                if (n++ == 2) break;
                Ui.text(Ui.TEXT, line, x + 10, dy, descS, Theme.alpha(Theme.INK_DIM, p), 0, 1.3f);
                dy += descLH;
            }
            if (on) {
                String tag = "WEARING";
                float ts = 9.5f, tw = Ui.width(Ui.MONO, tag, ts, .06f) + 12, tl = Ui.lineH(Ui.MONO, ts) + 4;
                Ui.round(x + w - 8 - tw, ry + 8, tw, tl, 2, Theme.alpha(accent(), p));
                Ui.mono(tag, x + w - 8 - tw + 6, ry + 10, ts, Theme.alpha(0xFF0B0B0D, p), .06f);
            }
            final String what = kind.equals("hat") ? "hat" : kind;
            hit(x, cy, w, h, () -> pick(what, id));
        }
        int rows = (items.length + cols - 1) / cols;
        return y + rows * h + (rows - 1) * gap;
    }

    private float swatches(String key, Object[][] list, String current, float y) {
        float r = 15, gap = 8, x = cx0;
        long ms = System.currentTimeMillis();
        for (Object[] c : list) {
            if (x + r * 2 > cx0 + cw) {
                x = cx0;
                y += r * 2 + gap;
            }
            String id = (String) c[0];
            boolean on = id.equals(current);
            boolean hover = over(x, y, r * 2, r * 2);
            float rr = hover ? r * 1.08f : r;
            float ccx = x + r, ccy = y + r;
            if (on) {
                Ui.circle(ccx, ccy, r + 4, Theme.alpha(Theme.INK, pageP));
                Ui.circle(ccx, ccy, r + 2, Theme.alpha(0xFF0B0B0D, pageP));
            }
            swatch(id, (int) c[2], ccx, ccy, rr, pageP, ms);
            final String v = id;
            hit(x, y, r * 2, r * 2, () -> pick(key, v));
            x += r * 2 + gap;
        }
        return y + r * 2 + 4;
    }

    private float chips(String[][] list, String current, float y, Consumer<String> onPick) {
        float s = 11.5f, padX = 11, h = Ui.lineH(Ui.MONO, s) + 14, gap = 6, x = cx0;
        for (String[] c : list) {
            String label = c[1].toUpperCase(Locale.ROOT);
            float w = Ui.width(Ui.MONO, label, s, .06f) + padX * 2;
            if (x + w > cx0 + cw && x > cx0) {
                x = cx0;
                y += h + gap;
            }
            boolean on = c[0].equals(current);
            boolean hover = over(x, y, w, h);
            Ui.round(x, y, w, h, 4, Theme.alpha(hover ? CHIP_HOVER : CHIP_BG, pageP));
            if (on) outline(x, y, w, h, 2, Theme.alpha(accent(), pageP));
            Ui.mono(label, x + padX, y + 7, s, Theme.alpha(on ? accent() : Theme.INK, pageP), .06f);
            final String v = c[0];
            hit(x, y, w, h, () -> onPick.accept(v));
            x += w + gap;
        }
        return y + h;
    }

    private static void outline(float x, float y, float w, float h, float t, int argb) {
        Ui.rect(x + 3, y, w - 6, t, argb);
        Ui.rect(x + 3, y + h - t, w - 6, t, argb);
        Ui.rect(x, y + 3, t, h - 6, argb);
        Ui.rect(x + w - t, y + 3, t, h - 6, argb);
    }

    // ------------------------------------------------------------------ the Maker
    private float maker(TCCosmetics.Look look, float y) {
        y += Ui.vh * 0.014f;
        y = chips(MAKER_TABS, makerTab, y, v -> {
            makerTab = v;
            pageAt = System.nanoTime();
        });
        y += Ui.vh * 0.02f;
        switch (makerTab) {
            case "cape" -> {
                String[] p = look.mk.split("\\.");
                y = makerTop("Your cape", "Pick a pattern, two colours, an emblem and how it moves. It goes on as you build it; press F5 in game to see it.",
                    "cape", "custom".equals(look.cape), y, "custom", "cape");
                y = head("Pattern", null, y);
                y = chips(PATTERNS, p[0], y, v -> spec("mk", look.mk, 0, v));
                y = head("Colour 1", nameOf(PALETTE, p[1]), y);
                y = swatchSpec("mk", look.mk, 1, PALETTE, y);
                y = head("Colour 2", nameOf(PALETTE, p[2]), y);
                y = swatchSpec("mk", look.mk, 2, PALETTE, y);
                y = head("Emblem", null, y);
                y = chips(EMBLEMS, p[3], y, v -> spec("mk", look.mk, 3, v));
                y = head("Motion", null, y);
                y = chips(MOTIONS, p[4], y, v -> spec("mk", look.mk, 4, v));
            }
            case "aura" -> {
                String[] p = look.ak.split("\\.");
                y = makerTop("Your aura", "A base in colour 1 and particles in colour 2, at the height you like. It goes on as you build it.",
                    "aura", "custom".equals(look.aura), y, "custom", "aura");
                y = head("Base", null, y);
                y = chips(AK_BASE, p[0], y, v -> spec("ak", look.ak, 0, v));
                y = head("Particles", null, y);
                y = chips(AK_PART, p[1], y, v -> spec("ak", look.ak, 1, v));
                y = head("Colour 1", nameOf(COLORS, p[2]), y);
                y = swatchSpec("ak", look.ak, 2, COLORS, y);
                y = head("Colour 2", nameOf(COLORS, p[3]), y);
                y = swatchSpec("ak", look.ak, 3, COLORS, y);
                y = head("Height", null, y);
                y = chips(AK_HEIGHT, p[4], y, v -> spec("ak", look.ak, 4, v));
                y = head("Size (every aura)", null, y);
                y = chips(SIZES, look.auraSize, y, v -> pick("asize", v));
                y = head("Speed (every aura)", null, y);
                y = chips(SPEEDS, look.auraSpeed, y, v -> pick("aspeed", v));
            }
            case "hat" -> {
                String[] p = look.hk.split("\\.");
                y = makerTop("Your hat", "A base, something on top and a trim, in two colours. It goes on as you build it.",
                    "hat", "custom".equals(look.hat), y, "custom", "hat");
                y = head("Base", null, y);
                y = chips(HK_BASE, p[0], y, v -> spec("hk", look.hk, 0, v));
                y = head("On top", null, y);
                y = chips(HK_TOP, p[1], y, v -> spec("hk", look.hk, 1, v));
                y = head("Trim", null, y);
                y = chips(HK_TRIM, p[2], y, v -> spec("hk", look.hk, 2, v));
                y = head("Colour 1", nameOf(PALETTE, p[3]), y);
                y = swatchSpec("hk", look.hk, 3, PALETTE, y);
                y = head("Colour 2", nameOf(PALETTE, p[4]), y);
                y = swatchSpec("hk", look.hk, 4, PALETTE, y);
            }
            default -> {
                String[] p = look.bk.split("\\.");
                y = makerTop("Your boots", "A shape, an extra and a trail, in two colours. They go on as you build them.",
                    "boots", "custom".equals(look.boots), y, "custom", "boots");
                y = head("Shape", null, y);
                y = chips(BK_SHAPE, p[0], y, v -> spec("bk", look.bk, 0, v));
                y = head("Extra", null, y);
                y = chips(BK_EXTRA, p[1], y, v -> spec("bk", look.bk, 1, v));
                y = head("Trail", null, y);
                y = chips(BK_TRAIL, p[2], y, v -> spec("bk", look.bk, 2, v));
                y = head("Colour 1", nameOf(PALETTE, p[3]), y);
                y = swatchSpec("bk", look.bk, 3, PALETTE, y);
                y = head("Colour 2", nameOf(PALETTE, p[4]), y);
                y = swatchSpec("bk", look.bk, 4, PALETTE, y);
            }
        }
        return y;
    }

    /** The top of a Maker page: a picture of the design, its title, a line about it and "Wear it". */
    private float makerTop(String title, String text, String kind, boolean wearing, float y, String id, String what) {
        long ms = System.currentTimeMillis();
        float pic = 64;
        Ui.round(cx0, y, pic, pic, 6, Theme.alpha(CARD_BG, pageP));
        preview(kind, "custom", cx0 + 10, y + 10, pic - 20, pageP, ms);
        float tx = cx0 + pic + 16, tw = cw - pic - 16;
        float ws = Math.max(20, Math.min(30, Ui.vh * 0.034f));
        Ui.text(Ui.WORD, title.toUpperCase(Locale.ROOT), tx, y, ws, Theme.alpha(Theme.INK, pageP), -.01f, .98f);
        float ty = y + ws * 0.98f + 4;
        for (String line : wrap(Ui.TEXT, text, 13, 0, tw)) {
            Ui.text(Ui.TEXT, line, tx, ty, 13, Theme.alpha(Theme.INK_DIM, pageP), 0, 1.35f);
            ty += 13 * 1.35f;
        }
        ty += 6;
        String[][] wear = {{"custom", wearing ? "Wearing it" : "Wear it"}};
        float save = cx0;
        cx0 = tx;
        ty = chips(wear, wearing ? "custom" : "", ty, v -> pick(what, id));
        cx0 = save;
        return Math.max(y + pic, ty) + Ui.vh * 0.01f;
    }

    private float swatchSpec(String key, String spec, int part, Object[][] list, float y) {
        String[] p = spec.split("\\.");
        String cur = p[part];
        float r = 15, gap = 8, x = cx0;
        long ms = System.currentTimeMillis();
        for (Object[] c : list) {
            if (x + r * 2 > cx0 + cw) {
                x = cx0;
                y += r * 2 + gap;
            }
            String id = (String) c[0];
            boolean on = id.equals(cur);
            boolean hover = over(x, y, r * 2, r * 2);
            float ccx = x + r, ccy = y + r;
            if (on) {
                Ui.circle(ccx, ccy, r + 4, Theme.alpha(Theme.INK, pageP));
                Ui.circle(ccx, ccy, r + 2, Theme.alpha(0xFF0B0B0D, pageP));
            }
            swatch(id, (int) c[2], ccx, ccy, hover ? r * 1.08f : r, pageP, ms);
            final String v = id;
            hit(x, y, r * 2, r * 2, () -> spec(key, spec, part, v));
            x += r * 2 + gap;
        }
        return y + r * 2 + 4;
    }

    /** Changes one part of a Maker design ("mk", "ak", "hk", "bk"), which also puts it on. */
    private void spec(String key, String spec, int part, String value) {
        String[] p = spec.split("\\.");
        if (part >= p.length) return;
        p[part] = value;
        pick(key, String.join(".", p));
    }

    // ------------------------------------------------------------------ picking, and turning the player to show it
    private void pick(String what, String id) {
        if (!TCCosmetics.set(what, id)) return;
        switch (what) {
            case "cape", "mk" -> turnFor(FACE + 180f, 3200);
            case "aura", "color", "ak", "asize", "aspeed" -> turnFor(FACE - 10f, 2500);
            case "hat", "hatc", "hk" -> turnFor(FACE - 20f, 2500);
            case "boots", "bootc", "trail", "bk" -> turnFor(FACE + 25f, 2500);
            default -> {
            }
        }
    }

    private void turnFor(float yaw, long ms) {
        if (draggingPlayer) return;
        turnTo = yaw;
        turnUntil = System.currentTimeMillis() + ms;
    }

    private void turn(float dt) {
        if (player == null || draggingPlayer || Float.isNaN(turnTo)) return;
        boolean showing = System.currentTimeMillis() < turnUntil;
        float goal = showing ? turnTo : FACE;
        float cur = V.skinRotationY(player);
        float d = goal - cur;
        d = (float) Math.toDegrees(Math.atan2(Math.sin(Math.toRadians(d)), Math.cos(Math.toRadians(d))));
        float k = Anim.enabled() ? Math.min(1f, dt * 6f) : 1f;
        V.setSkinRotationY(player, cur + d * k);
        if (!showing && Math.abs(d) < 0.3f) {
            V.setSkinRotationY(player, goal);
            turnTo = Float.NaN;
        }
    }

    // ------------------------------------------------------------------ under the player: your name and what you wear
    private void drawWho(Theme th) {
        float p = a(openedAt, 600, 200);
        float cx = Ui.vw * 0.78f, y = Ui.vh * 0.69f;
        String name = TCCosmetics.myName();
        float nw = Ui.width(Ui.SEMI, name, 22, 0);
        Ui.text(Ui.SEMI, name, cx - nw / 2, y, 22, Theme.alpha(Theme.INK, p));
        y += Ui.lineH(Ui.SEMI, 22) + 6;
        String wear = wearing(TCCosmetics.mine());
        float ww = Ui.width(Ui.MONO, wear.toUpperCase(Locale.ROOT), 11, .06f);
        Ui.mono(wear, cx - ww / 2, y, 11, Theme.alpha(th.a1(), p), .06f);
        y += Ui.lineH(Ui.MONO, 11) + 6;
        String hint = "Drag to spin";
        float hw = Ui.width(Ui.MONO, hint.toUpperCase(Locale.ROOT), 11, .06f);
        Ui.mono(hint, cx - hw / 2, y, 11, Theme.alpha(Theme.INK_DIM, p), .06f);
    }

    private static String wearing(TCCosmetics.Look l) {
        List<String> on = new ArrayList<>();
        if (l.hasCape()) on.add("custom".equals(l.cape) ? "Your own cape" : nameOf(CAPES, l.cape) + " cape");
        if (l.hasAura()) on.add("custom".equals(l.aura) ? "Your own aura" : nameOf(AURAS, l.aura));
        if (l.hasHat()) on.add("custom".equals(l.hat) ? "Your own hat" : nameOf(HATS, l.hat));
        if (l.hasBoots()) on.add("custom".equals(l.boots) ? "Your own boots" : nameOf(BOOTS, l.boots));
        return on.isEmpty() ? "Nothing on yet" : String.join(" · ", on);
    }

    private static String nameOf(String[][] list, String id) {
        for (String[] s : list) if (s[0].equals(id)) return s[1];
        return id;
    }

    private static String nameOf(Object[][] list, String id) {
        for (Object[] s : list) if (s[0].equals(id)) return (String) s[1];
        return id;
    }

    // ------------------------------------------------------------------ the little pictures
    private int colorOf(Object[][] list, String id, long ms) {
        if ("theme".equals(id)) return accent() & 0xFFFFFF;
        if ("rainbow".equals(id)) return hue((ms % 4000L) / 4000f);
        for (Object[] c : list) if (c[0].equals(id) && (int) c[2] >= 0) return (int) c[2];
        return 0xECEBE6;
    }

    private static int hue(float h) {
        int i = (int) (h * 6) % 6;
        float f = h * 6 - (int) (h * 6);
        int q = Math.round(255 * (1 - f)), t = Math.round(255 * f);
        return switch (i) {
            case 0 -> 0xFF0000 | (t << 8);
            case 1 -> (q << 16) | 0xFF00;
            case 2 -> 0xFF00 | t;
            case 3 -> (q << 8) | 0xFF;
            case 4 -> (t << 16) | 0xFF;
            default -> 0xFF0000 | q;
        };
    }

    private void swatch(String id, int rgb, float cx, float cy, float r, float p, long ms) {
        int a = Theme.alpha(0xFF000000, p) & 0xFF000000;
        switch (id) {
            case "theme" -> {
                Ui.circle(cx, cy, r, a | 0xD9D9D9);
                Ui.clip(cx - r, cy - r, r, r * 2);
                Ui.circle(cx, cy, r, a | (accent() & 0xFFFFFF));
                Ui.unclip();
            }
            case "natural" -> {
                int[] c = {0xF2C14E, 0xE8434F, 0x4A8CFF};
                for (int i = 0; i < 3; i++) {
                    Ui.clip(cx - r + i * r * 2 / 3f, cy - r, r * 2 / 3f + 0.5f, r * 2);
                    Ui.circle(cx, cy, r, a | c[i]);
                    Ui.unclip();
                }
            }
            case "rainbow" -> {
                int[] c = {0xFF6F6F, 0xFFD76B, 0x9BEA5B, 0x45E3C6, 0x7FB0FF, 0xB38CFF, 0xFF6FA8};
                for (int i = 0; i < c.length; i++) {
                    Ui.clip(cx - r + i * r * 2 / c.length, cy - r, r * 2 / c.length + 0.5f, r * 2);
                    Ui.circle(cx, cy, r, a | c[i]);
                    Ui.unclip();
                }
            }
            default -> {
                if ("black".equals(id)) Ui.circle(cx, cy, r + 1, Theme.alpha(0x47FFFFFF, p));
                Ui.circle(cx, cy, r, a | (rgb & 0xFFFFFF));
            }
        }
    }

    /** A card's picture: s x s CSS px at (x, y). */
    private void preview(String kind, String id, float x, float y, float s, float p, long ms) {
        TCCosmetics.Look look = TCCosmetics.mine();
        float t = (ms % 3_600_000L) / 1000f;
        if ("none".equals(id)) {
            // an empty frame
            float w = s * 0.62f, h = s * 0.8f, ox = x + (s - w) / 2, oy = y + (s - h) / 2;
            outline(ox, oy, w, h, 1.5f, Theme.alpha(0x4DECEBE6, p));
            Ui.rect(ox + w * 0.2f, oy + h / 2 - 0.75f, w * 0.6f, 1.5f, Theme.alpha(0x4DECEBE6, p));
            return;
        }
        switch (kind) {
            case "cape" -> cape(id, look, x, y, s, p, t);
            case "aura" -> {
                if ("custom".equals(id)) {
                    String[] k = look.ak.split("\\.");
                    made(colorOf(COLORS, k[2], ms), colorOf(COLORS, k[3], ms), x, y, s, p);
                } else aura(id, colorOf(COLORS, look.color, ms), x, y, s, p, t);
            }
            default -> {
                if ("custom".equals(id)) {
                    String[] k = ("hat".equals(kind) ? look.hk : look.bk).split("\\.");
                    made(TCCapeArt.paletteRgb(k[3], accent()), TCCapeArt.paletteRgb(k[4], accent()), x, y, s, p);
                } else pixels(id, x, y, s, p);
            }
        }
    }

    /** "Made by you": two rings in the design's colours and a spark. */
    private static void made(int c1, int c2, float x, float y, float s, float p) {
        float r = s * 0.24f;
        Ui.circle(x + s * 0.4f, y + s * 0.5f, r, Theme.alpha(0xFF000000 | c1, p));
        Ui.circle(x + s * 0.6f, y + s * 0.5f, r, Theme.alpha(0xE6000000 | c2, p));
        float d = s * 0.09f, cx = x + s * 0.5f, cy = y + s * 0.5f;
        Ui.rect(cx - d / 4, cy - d * 1.4f, d / 2, d * 2.8f, Theme.alpha(0xFFFFFFFF, p));
        Ui.rect(cx - d * 1.4f, cy - d / 4, d * 2.8f, d / 2, Theme.alpha(0xFFFFFFFF, p));
    }

    private void aura(String id, int rgb, float x, float y, float s, float p, float t) {
        float cx = x + s / 2, cy = y + s * 0.6f, rx = s * 0.42f, ry = s * 0.14f;
        int c = 0xFF000000 | rgb;
        switch (id) {
            case "halo" -> {
                for (int i = 0; i < 24; i++) {
                    double ang = i * Math.PI * 2 / 24 + t * 0.6;
                    Ui.circle(cx + (float) Math.cos(ang) * rx, cy + (float) Math.sin(ang) * ry, 1.6f, Theme.alpha(c, p * .8f));
                }
            }
            case "orbit" -> {
                for (int i = 0; i < 3; i++) {
                    double ang = i * Math.PI * 2 / 3 + t * 1.4;
                    Ui.circle(cx + (float) Math.cos(ang) * rx, cy - s * 0.12f + (float) Math.sin(ang) * ry, 3.2f, Theme.alpha(c, p));
                }
            }
            case "rising" -> {
                for (int i = 0; i < 6; i++) {
                    float k = ((t * 0.5f + i / 6f) % 1f);
                    float dx = (float) Math.sin(i * 2.3) * s * 0.3f;
                    Ui.circle(cx + dx, y + s * (0.9f - 0.8f * k), 2.4f, Theme.alpha(c, p * (1 - k)));
                }
            }
            case "ripple" -> {
                for (int i = 0; i < 3; i++) {
                    float k = ((t * 0.45f + i / 3f) % 1f);
                    for (int j = 0; j < 20; j++) {
                        double ang = j * Math.PI * 2 / 20;
                        Ui.circle(cx + (float) Math.cos(ang) * rx * k, y + s * 0.8f + (float) Math.sin(ang) * ry * k, 1.3f, Theme.alpha(c, p * (1 - k)));
                    }
                }
            }
            default -> { // helix
                for (int i = 0; i < 10; i++) {
                    for (int strand = 0; strand < 2; strand++) {
                        double ang = i * 0.7 + t * 2 + strand * Math.PI;
                        float yy = y + s * (0.9f - i * 0.08f);
                        Ui.circle(cx + (float) Math.cos(ang) * rx * 0.8f, yy, 1.8f, Theme.alpha(c, p * (0.4f + 0.6f * (float) (Math.sin(ang) * 0.5 + 0.5))));
                    }
                }
            }
        }
    }

    private void cape(String id, TCCosmetics.Look look, float x, float y, float s, float p, float t) {
        float w = s * 0.62f, h = s * 0.92f, ox = x + (s - w) / 2, oy = y + (s - h) / 2;
        int acc = accent() & 0xFFFFFF;
        switch (id) {
            case "treasure", "theme" -> {
                boolean theme = id.equals("theme");
                int edge = theme ? acc : 0xFFD76B;
                Ui.gradientV(ox, oy, w, h, Theme.alpha(theme ? 0xFF0F0F12 : 0xFF2B1A0C, p), Theme.alpha(theme ? 0xFF0F0F12 : 0xFF120A04, p));
                outline(ox + 2, oy + 2, w - 4, h - 4, 2, Theme.alpha(0xFF000000 | edge, p));
                Ui.round(ox + w * 0.3f, oy + h * 0.36f, w * 0.4f, h * 0.24f, 1.5f, Theme.alpha(0xFF000000 | edge, p));
                // the shine sweeping across
                float k = (t * 0.35f) % 1f;
                Ui.rect(ox + 4 + (w - 8) * k, oy + 4, Math.min(3, w - 8 - (w - 8) * k), h - 8, Theme.alpha(0x40FFFFFF, p));
            }
            case "frost" -> {
                Ui.gradientV(ox, oy, w, h, Theme.alpha(0xFF0A1D36, p), Theme.alpha(0xFF5FB4E6, p));
                for (int i = 0; i < 7; i++) {
                    float fx = ox + w * (0.12f + 0.76f * ((i * 0.37f) % 1f));
                    float fy = oy + h * ((t * 0.12f + i / 7f) % 1f);
                    Ui.circle(fx, fy, 1.1f, Theme.alpha(0xFFFFFFFF, p));
                }
            }
            case "tropical" -> {
                Ui.gradientV(ox, oy, w, h * 0.47f, Theme.alpha(0xFF6B2A72, p), Theme.alpha(0xFFFF8A5A, p));
                Ui.circle(ox + w / 2, oy + h * 0.44f, w * 0.26f, Theme.alpha(0xFFFFD27A, p));
                Ui.gradientV(ox, oy + h * 0.47f, w, h * 0.53f, Theme.alpha(0xFF0B7A86, p), Theme.alpha(0xFF04323B, p));
                for (int i = 0; i < 3; i++) Ui.rect(ox, oy + h * (0.56f + i * 0.13f) + (float) Math.sin(t * 2 + i) * 1.2f, w, 1, Theme.alpha(0x8C78F0DC, p));
            }
            case "ember" -> {
                Ui.rect(ox, oy, w, h, Theme.alpha(0xFF1A1210, p));
                float glow = 0.6f + 0.4f * (float) Math.sin(t * 2.4);
                for (int i = 0; i < 4; i++) Ui.rect(ox + w * 0.1f, oy + h * (0.18f + i * 0.2f), w * (0.5f + 0.1f * i % 0.4f), 1.5f, Theme.alpha(0xFFFF6A1A, p * glow));
                for (int i = 0; i < 3; i++) Ui.rect(ox + w * (0.25f + i * 0.22f), oy + h * 0.1f, 1.2f, h * 0.8f, Theme.alpha(0x99FFB23A, p * glow));
            }
            case "aurora" -> {
                Ui.gradientV(ox, oy, w, h * 0.7f, Theme.alpha(0xFF050A1F, p), Theme.alpha(0xFF0C1636, p));
                Ui.rect(ox, oy + h * 0.7f, w, h * 0.3f, Theme.alpha(0xFF02040A, p));
                for (int i = 0; i < 6; i++) {
                    float bx = ox + i * w / 6f;
                    float by = oy + h * (0.12f + 0.08f * (float) Math.sin(t * 1.3 + i * 0.9));
                    Ui.gradientV(bx, by, w / 6f + 0.5f, h * 0.3f, Theme.alpha(0xCC45E3C6, p), Theme.alpha(0x0045E3C6, p));
                }
            }
            case "galaxy" -> {
                Ui.rect(ox, oy, w, h, Theme.alpha(0xFF05030F, p));
                Ui.circle(ox + w * 0.36f, oy + h * 0.38f, w * 0.3f, Theme.alpha(0x998A3CCF, p));
                Ui.circle(ox + w * 0.66f, oy + h * 0.7f, w * 0.24f, Theme.alpha(0x88D0408F, p));
                float tw = 0.5f + 0.5f * (float) Math.sin(t * 2);
                for (int i = 0; i < 8; i++) Ui.circle(ox + w * ((i * 0.41f + 0.1f) % 1f), oy + h * ((i * 0.27f + 0.05f) % 1f), 0.9f, Theme.alpha(0xFFFFFFFF, p * (i % 2 == 0 ? tw : 1 - tw)));
            }
            case "circuit" -> {
                Ui.rect(ox, oy, w, h, Theme.alpha(0xFF071A14, p));
                Ui.rect(ox + w * 0.47f, oy, w * 0.06f, h, Theme.alpha(0x802FD39A, p));
                Ui.rect(ox + w * 0.2f, oy, w * 0.05f, h, Theme.alpha(0x592FD39A, p));
                Ui.rect(ox + w * 0.75f, oy, w * 0.05f, h, Theme.alpha(0x592FD39A, p));
                Ui.rect(ox + w * 0.3f, oy + h * 0.4f, w * 0.4f, h * 0.2f, Theme.alpha(0xFF0B241C, p));
                outline(ox + w * 0.3f, oy + h * 0.4f, w * 0.4f, h * 0.2f, 1.5f, Theme.alpha(0xFF2FD39A, p));
                float k = (t * 0.5f) % 1f;
                Ui.round(ox + w / 2 - 2, oy + (h - 14) * k, 4, 14, 2, Theme.alpha(0xFFB6FFE4, p));
            }
            default -> { // made by you: the design's two colours
                String[] k = look.mk.split("\\.");
                int c1 = TCCapeArt.paletteRgb(k[1], acc), c2 = TCCapeArt.paletteRgb(k[2], acc);
                Ui.gradientV(ox, oy, w, h, Theme.alpha(0xFF000000 | c1, p), Theme.alpha(0xFF000000 | c2, p));
                if (!"none".equals(k[3])) Ui.circle(ox + w / 2, oy + h * 0.45f, w * 0.16f, Theme.alpha(0xFFFFFFFF, p * .9f));
            }
        }
    }

    private static void pixels(String id, float x, float y, float s, float p) {
        String[] rows = null;
        for (String[] r : PIX) if (r[0].equals(id)) rows = r;
        if (rows == null) return;
        int w = rows[1].length(), h = rows.length - 1;
        float px = Math.min(s / w, s / h) * 0.92f;
        float ox = x + (s - px * w) / 2, oy = y + (s - px * h) / 2;
        for (int yy = 0; yy < h; yy++) {
            String row = rows[yy + 1];
            for (int xx = 0; xx < w; xx++) {
                char ch = row.charAt(xx);
                if (ch == '.') continue;
                int k = PIX_KEYS.indexOf(ch);
                if (k < 0) continue;
                int col = PIX_RGB[k];
                // a few pictures use their own shades for shared letters
                if (id.equals("catears") && ch == 'C') col = 0x5A5A66;
                else if (id.equals("magma") && ch == 'M') col = 0x55403A;
                else if (id.equals("magma") && ch == 'O') col = 0xFF6A1A;
                else if (id.equals("magma") && ch == 'd') col = 0x33241F;
                else if (id.equals("rocket") && ch == 'd') col = 0x4A4F57;
                else if (id.equals("horns") && ch == 'R') col = 0x9A2E2E;
                else if (id.equals("horns") && ch == 'r') col = 0x4A1818;
                Ui.rect(ox + xx * px, oy + yy * px, px + 0.3f, px + 0.3f, Theme.alpha(0xFF000000 | col, p));
            }
        }
    }

    private static List<String> wrap(String face, String text, float size, float ls, float maxW) {
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
    private boolean onPlayer(double x, double y) {
        return player != null && x >= player.getX() && y >= player.getY() && x < player.getX() + player.getWidth() && y < player.getY() + player.getHeight();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (onPlayer(e.x(), e.y())) {
            draggingPlayer = true;
            turnTo = Float.NaN; // you're turning it yourself now
            return super.mouseClicked(e, doubleClick);
        }
        if (e.button() != 0) return true;
        float mx = Ui.mx(e.x()), my = Ui.my(e.y());
        for (int i = hits.size() - 1; i >= 0; i--) {
            float[] r = (float[]) hits.get(i)[0];
            if (Ui.in(mx, my, r[0], r[1], r[2], r[3])) {
                ((Runnable) hits.get(i)[1]).run();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        draggingPlayer = false;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        float max = Math.max(0, contentH - viewH);
        scrollTarget = Math.max(0, Math.min(max, scrollTarget - (float) v * 64));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        if (key == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        // 1-5 pick a page, like the main menu
        if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_5) {
            setTab(TABS[key - InputConstants.KEY_1][0]);
            return true;
        }
        return super.keyPressed(e);
    }
}
