package net.treasureclient.ui;

import net.treasureclient.compat.V;
import net.treasureclient.compat.TCScreen;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.SharedConstants;
import net.treasureclient.core.Anim;
import net.treasureclient.core.Config;
import net.treasureclient.core.ModDef;
import net.treasureclient.core.Mods;
import net.treasureclient.core.Theme;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The 1.5 main menu: six big words stepping right (Singleplayer, Multiplayer, Options,
 * Treasure, Mods, Cosmetics), your player on the right, Rejoin bottom left and Language / About / What's new / Quit
 * bottom right. Same sizes, colours and animations as the browser client.
 */
public class TitleScreenTC extends TCScreen {
    private static final String[][] ITEMS = {
        {"sp", "Singleplayer", "Your worlds"},
        {"mp", "Multiplayer", "Servers · LAN · Realms"},
        {"set", "Options", "Video · controls · sound"},
        {"tcs", "Treasure", ""},
        {"mods", "Mods", "Every mod you have installed"},
        {"cos", "Cosmetics", "Capes · auras · hats · boots"}};
    private static final int N = ITEMS.length;
    private static final String CHANGELOG = "https://treasureclient.github.io/changelog.html";

    private final long openedAt = System.nanoTime();
    private final Anim[] shift = new Anim[N], bar = new Anim[N], hint = new Anim[N], dim = new Anim[N];
    private int hover = -1;
    private boolean keyboard;
    private long leaveAt;
    private String leaving;
    private int leavingIndex = -1;
    private PlayerSkinWidget player;
    private final List<Object[]> hits = new ArrayList<>();

    public TitleScreenTC() {
        super(Component.translatable("narrator.screen.title"));
        for (int i = 0; i < N; i++) {
            shift[i] = new Anim(0, 280);
            bar[i] = new Anim(0, 280);
            hint[i] = new Anim(0, 280);
            dim[i] = new Anim(1, 200);
        }
    }

    @Override
    protected void init() {
        // your player, on the right (drag to spin); same place as the browser client (80% across)
        int h = Math.round(height * 0.38f), w = Math.round(h * 0.5f);
        int cx = Math.round(width * 0.80f), top = Math.round(height * 0.28f);
        var profile = minecraft.getGameProfile();
        var skins = minecraft.getSkinManager().createLookup(profile, false);
        player = new PlayerSkinWidget(w, h, minecraft.getEntityModels(), skins);
        player.setPosition(cx - w / 2, top);
        addRenderableWidget(player);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }


    private static float a(long start, float ms, float delay) {
        return Anim.since(start, ms, delay);
    }

    @Override
    protected void draw(int mouseX, int mouseY, float delta) {
        if (leaving != null && System.nanoTime() - leaveAt > 170_000_000L) {
            String code = leaving;
            leaving = null;
            act(code);
            return;
        }
        Theme th = Theme.current();
        Ui.begin();
        float mx = Ui.mx(mouseX), my = Ui.my(mouseY);
        hits.clear();
        try {
            TitleBackground.draw(th);
            // the shade: darker on the left behind the words, and along the bottom
            Ui.gradientH(0, 0, Ui.vw, Ui.vh, new float[]{0, .32f, .62f, 1}, new int[]{0xB80C0C0E, 0x800C0C0E, 0x1F0C0C0E, 0x380C0C0E});
            Ui.gradientV(0, Ui.vh * 0.72f, Ui.vw, Ui.vh * 0.28f, 0x000C0C0E, 0x730C0C0E);
            drawTop(th);
            drawMenu(th, mx, my);
            drawWho(th, mx, my);
            drawBottom(th, mx, my);
        } finally {
            Ui.end();
        }
        PlayerLook.aim(player, mouseX, mouseY); // the head follows the mouse
        drawWidgets(mouseX, mouseY, delta); // the player
    }

    private void drawTop(Theme th) {
        float p = a(openedAt, 600, 100);
        float x = Ui.vw * 0.05f, y = Ui.vh * 0.042f;
        float lh = 22;
        Ui.image(Ui.chest(), x, y, lh * 91f / 92f, lh, 91, 92, Theme.alpha(0xFFFFFFFF, p));
        Ui.text(Ui.BRAND, "TREASURE CLIENT", x + lh * 91f / 92f + 10, y + (lh - Ui.lineH(Ui.BRAND, 15)) / 2, 15, Theme.alpha(Theme.INK, p), .14f, 0);
        String ver = "1.5 · " + SharedConstants.getCurrentVersion().name();
        float vw = Ui.width(Ui.MONO, ver.toUpperCase(Locale.ROOT), 11.5f, .06f);
        Ui.mono(ver, Ui.vw * 0.95f - vw, y + (lh - Ui.lineH(Ui.MONO, 11.5f)) / 2, 11.5f, Theme.alpha(Theme.INK_DIM, p), .06f);
    }

    private float wordSize() {
        return Math.max(30, Math.min(56, Ui.vh * 0.059f));
    }

    private float itemH() {
        return Ui.vh * 0.025f + wordSize() * 0.98f;
    }

    private void drawMenu(Theme th, float mx, float my) {
        float ws = wordSize(), ih = itemH();
        float top = Ui.vh / 2 - ih * N / 2;
        int over = -1;
        for (int i = 0; i < N; i++) {
            float ix = Ui.vw * 0.05f + i * Ui.vw * 0.03f;
            float w = 44 + Ui.width(Ui.WORD, ITEMS[i][1].toUpperCase(Locale.ROOT), ws, -.01f) + 18;
            if (Ui.in(mx, my, ix, top + i * ih, w, ih)) over = i;
        }
        if (over >= 0 && over != hover) {
            hover = over;
            keyboard = false;
        } else if (over < 0 && !keyboard) hover = -1;
        boolean anyOn = hover >= 0;
        for (int i = 0; i < N; i++) {
            String[] it = ITEMS[i];
            float y = top + i * ih;
            float ix = Ui.vw * 0.05f + i * Ui.vw * 0.03f;
            boolean on = i == hover;
            float enter = a(openedAt, 550, 60 + i * 60);
            float alpha = enter;
            float dx = 0;
            float dy = 14 * (1 - enter);
            if (leaving != null) {
                float lp = Math.min(1, (System.nanoTime() - leaveAt) / 1e6f / 180f);
                if (i != leavingIndex) {
                    alpha *= 1 - lp;
                    dx = -26 * lp;
                }
            }
            float b = bar[i].target(on ? Ui.vw * 0.05f - 10 + i * Ui.vw * 0.03f : 0).get();
            if (b > 0.5f) Ui.rect(0, y + dy + ih / 2 - 1.5f, b, 3, Theme.alpha(th.a1(), alpha));
            Ui.mono(String.format("%02d", i + 1), ix + dx, y + dy + Ui.vh * 0.015f + Ui.vh * 0.009f, 11.5f, Theme.alpha(on ? th.a1() : Theme.INK_DIM, alpha), .06f);
            float s = shift[i].target(on ? (leaving != null && i == leavingIndex ? 30 : 18) : 0).get();
            float op = dim[i].target(anyOn && !on ? .45f : 1).get();
            String word = it[1].toUpperCase(Locale.ROOT);
            float wx = ix + 44 + s + dx, wy = y + dy + Ui.vh * 0.015f;
            // a soft shadow under the words (the browser uses a blurred text shadow)
            Ui.text(Ui.WORD, word, wx, wy + 2, ws, Theme.alpha(0x33000000, op * alpha), -.01f, .98f);
            float ww = Ui.text(Ui.WORD, word, wx, wy, ws, Theme.alpha(Theme.INK, op * alpha), -.01f, .98f);
            String h = it[0].equals("tcs") ? modsHint() : it[2];
            float hp = hint[i].target(on ? 1 : 0).get();
            if (hp > 0.01f && !h.isEmpty()) {
                float hx = ix + 44 + ww + 18 + 18 * hp - 8 * (1 - hp) + dx;
                Ui.mono(h, hx, y + dy + ih / 2 - Ui.lineH(Ui.MONO, 11.5f) / 2, 11.5f, Theme.alpha(Theme.INK_DIM, hp * alpha), .06f);
            }
        }
    }

    private static String modsHint() {
        int all = Mods.ALL.size(), on = 0;
        for (ModDef d : Mods.ALL) if (Config.get().on(d.key())) on++;
        return all + " mods · " + on + " on";
    }

    private void drawWho(Theme th, float mx, float my) {
        float p = a(openedAt, 600, 350);
        float cx = Ui.vw * 0.80f, y = Ui.vh * 0.69f;
        String name = minecraft.getUser().getName();
        float nw = Ui.width(Ui.HUD, name, 22, .02f);
        Ui.text(Ui.HUD, name, cx - nw / 2, y + 2, 22, Theme.alpha(0x55000000, p), .02f, 0);
        Ui.text(Ui.HUD, name, cx - nw / 2, y, 22, Theme.alpha(Theme.INK, p), .02f, 0);
        y += Ui.lineH(Ui.HUD, 22) + 6;
        String left = "Skin & cape \u00B7 ";
        String link = "Change";
        float lw = Ui.width(Ui.MONO, left.toUpperCase(Locale.ROOT), 11.5f, .06f);
        float kw = Ui.width(Ui.MONO, link.toUpperCase(Locale.ROOT), 11.5f, .06f);
        float x = cx - (lw + kw) / 2;
        Ui.mono(left, x, y, 11.5f, Theme.alpha(Theme.INK_DIM, p), .06f);
        boolean h = Ui.in(mx, my, x + lw, y - 2, kw, Ui.lineH(Ui.MONO, 11.5f) + 4);
        Ui.mono(link, x + lw, y, 11.5f, Theme.alpha(th.a1(), p), .06f);
        if (h) Ui.rect(x + lw, y + Ui.lineH(Ui.MONO, 11.5f) - 1, kw, 1, Theme.alpha(th.a1(), p));
        hits.add(new Object[]{new float[]{x + lw, y - 2, kw, Ui.lineH(Ui.MONO, 11.5f) + 4}, "cos"});
    }

    private void drawBottom(Theme th, float mx, float my) {
        float p = a(openedAt, 600, 100);
        float s = 11.5f, lh = Ui.lineH(Ui.MONO, s);
        float y = Ui.vh - Ui.vh * 0.04f - lh;
        // left: Rejoin <last server> →
        Config c = Config.get();
        if (!c.lastServerAddress.isEmpty()) {
            float x = Ui.vw * 0.05f;
            String srv = (c.lastServerName.isEmpty() ? c.lastServerAddress : c.lastServerName).toUpperCase(Locale.ROOT);
            float w1 = Ui.width(Ui.MONO, "REJOIN ", s, .06f), w2 = Ui.width(Ui.MONO, srv, s, .06f), w3 = Ui.width(Ui.MONO, " →", s, .06f);
            boolean h = Ui.in(mx, my, x, y - 4, w1 + w2 + w3, lh + 8);
            Ui.mono("Rejoin ", x, y, s, Theme.alpha(Theme.INK, p), .06f);
            Ui.mono(srv, x + w1, y, s, Theme.alpha(th.a1(), p), .06f);
            Ui.mono(" →", x + w1 + w2 + (h ? 3 : 0), y, s, Theme.alpha(Theme.INK, p), .06f);
            hits.add(new Object[]{new float[]{x, y - 4, w1 + w2 + w3, lh + 8}, "rejoin"});
        }
        // right: Language / About / What's new / Quit
        String[][] links = {{"lang", "Language"}, {"about", "About"}, {"new", "What's new"}, {"quit", "Quit"}};
        float sepW = Ui.width(Ui.MONO, "/", s, .06f) + 24;
        float total = -sepW;
        for (String[] l : links) total += Ui.width(Ui.MONO, l[1].toUpperCase(Locale.ROOT), s, .06f) + sepW;
        float x = Ui.vw * 0.95f - total;
        for (int i = 0; i < links.length; i++) {
            float w = Ui.width(Ui.MONO, links[i][1].toUpperCase(Locale.ROOT), s, .06f);
            boolean h = Ui.in(mx, my, x, y - 4, w, lh + 8);
            Ui.mono(links[i][1], x, y, s, Theme.alpha(h ? Theme.INK : Theme.INK_DIM, p), .06f);
            hits.add(new Object[]{new float[]{x, y - 4, w, lh + 8}, links[i][0]});
            x += w;
            if (i < links.length - 1) {
                Ui.mono("/", x + 12, y, s, Theme.alpha(Theme.INK_DIM, .5f * p), .06f);
                x += sepW;
            }
        }
    }

    // ---------------------------------------------------------------- actions
    private void choose(int i) {
        if (leaving != null) return;
        String code = ITEMS[i][0];
        playClick();
        if (code.equals("tcs")) {
            act(code);
            return;
        }
        hover = i;
        leavingIndex = i;
        leaving = code;
        leaveAt = System.nanoTime();
        if (!Anim.enabled()) leaveAt -= 1_000_000_000L;
    }

    private void act(String code) {
        switch (code) {
            case "sp" -> V.open(new SelectWorldScreen(this));
            case "mp" -> V.open(new JoinMultiplayerScreen(this));
            case "tcs" -> V.open(new ModMenuScreen(this));
            case "mods" -> V.open(new ModListScreen(this));
            case "cos" -> V.open(new CosmeticsScreen(this));
            case "set" -> V.open(V.optionsScreen(this));
            case "lang" -> V.open(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager()));
            case "about" -> V.open(new CreditsAndAttributionScreen(this));
            case "new" -> V.openLink(this, CHANGELOG);
            case "quit" -> minecraft.stop();
            case "rejoin" -> {
                Config c = Config.get();
                ServerData data = new ServerData(c.lastServerName.isEmpty() ? c.lastServerAddress : c.lastServerName,
                    c.lastServerAddress, ServerData.Type.OTHER);
                ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(c.lastServerAddress), data, false, null);
            }
            default -> {}
        }
    }

    private void playClick() {
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (super.mouseClicked(e, doubleClick)) return true; // the player (drag to spin)
        if (e.button() != 0) return false;
        float mx = Ui.mx(e.x()), my = Ui.my(e.y());
        if (hover >= 0 && !keyboard) {
            choose(hover);
            return true;
        }
        for (Object[] h : hits) {
            float[] r = (float[]) h[0];
            if (Ui.in(mx, my, r[0], r[1], r[2], r[3])) {
                playClick();
                act((String) h[1]);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if (k >= InputConstants.KEY_1 && k <= InputConstants.KEY_6) {
            choose(k - InputConstants.KEY_1);
            return true;
        }
        if (k == InputConstants.KEY_DOWN || k == InputConstants.KEY_UP) {
            keyboard = true;
            hover = hover < 0 ? 0 : Math.floorMod(hover + (k == InputConstants.KEY_DOWN ? 1 : -1), N);
            return true;
        }
        if ((k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) && hover >= 0) {
            choose(hover);
            return true;
        }
        if (net.treasureclient.TreasureClient.MENU_KEY.matches(e)) {
            act("tcs");
            return true;
        }
        return super.keyPressed(e);
    }

    /** Whether the vanilla title screen should be replaced (always, unless turned off in the config). */
    public static boolean replaces(Screen s) {
        return s instanceof TitleScreen && !Config.get().flag("vanillaTitle", false);
    }

    /**
     * The screen Minecraft should really open: ours instead of its title screen. Opening "no screen"
     * while not in a world also means the title screen (Minecraft makes its own one later on), which is
     * what happens after cancelling your first world or leaving a server.
     */
    public static Screen swap(Screen s) {
        if (Config.get().flag("vanillaTitle", false)) return s;
        if (s instanceof TitleScreen) return new TitleScreenTC();
        if (s == null && net.minecraft.client.Minecraft.getInstance().level == null) return new TitleScreenTC();
        return s;
    }
}
