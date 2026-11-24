package net.treasureclient.cos;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Treasure Client cosmetics in the world: finds each player's look, keeps their boot trail, and
 * makes the animated cape textures.
 *
 * A cape's 64 pictures are drawn the first time someone wears it, a few each frame (about 3 ms,
 * one cape at a time; until then their normal cape shows). Its texture is the size of a Minecraft
 * cape at 4x detail, and only the current picture is copied into it, 16 times a second.
 */
public final class TCCosRender {
    private TCCosRender() {}

    public static final String NS = "treasureclient";

    // ------------------------------------------------------------------ each player, as they're about to be drawn

    private static final Map<Integer, TCAuraShapes.Trail> TRAILS = new HashMap<>();
    private static int guiDepth;
    private static ClientAsset.Texture defaultElytra;

    /** While the inventory's player picture is being drawn (set by a mixin). */
    public static void enterGui() {
        ++guiDepth;
    }

    public static void leaveGui() {
        if (guiDepth > 0) --guiDepth;
    }

    public static boolean inGui() {
        return guiDepth > 0;
    }

    /** Each client tick (outside drawing): forget a picture that never finished (so auras can't stay hidden). */
    public static void tick() {
        guiDepth = 0;
    }

    public static void extract(Avatar entity, AvatarRenderState state) {
        TCAvatarState tc = (TCAvatarState) state;
        tc.tc$setLook(null);
        tc.tc$setTrail(null);
        if (!(entity instanceof Player p)) return;
        Minecraft mc = Minecraft.getInstance();
        TCCosmetics.Look look = p == mc.player ? TCCosmetics.mine() : TCCosmetics.of(p.getScoreboardName());
        if (look == null || look.isEmpty() || p.isSpectator()) return;
        tc.tc$setLook(look);
        tc.tc$setActive(!p.onGround() || p.isSprinting() || state.isFallFlying);
        long now = System.currentTimeMillis();

        if (look.hasTrail() && !p.isSleeping()) {
            TCAuraShapes.Trail tr = TRAILS.get(p.getId());
            if (tr == null) {
                if (TRAILS.size() > 40) {
                    Iterator<TCAuraShapes.Trail> it = TRAILS.values().iterator();
                    while (it.hasNext()) if (now - it.next().lastSeen() > 5000L) it.remove();
                }
                tr = new TCAuraShapes.Trail();
                TRAILS.put(p.getId(), tr);
            }
            tr.update(look, state.x, state.y, state.z, p.yBodyRot, p.onGround(), p.isSprinting(), now);
            tc.tc$setTrail(tr);
        }

        if (look.hasCape()) {
            Identifier cape = cape(look);
            if (cape != null) {
                PlayerSkin s = state.skin;
                // elytras use the cape's picture when they have none of their own, but the animated
                // one only has the cape in it: give those the normal elytra picture instead
                ClientAsset.Texture elytra = s.elytra();
                if (elytra == null) {
                    if (defaultElytra == null) {
                        Identifier e = Identifier.withDefaultNamespace("textures/entity/equipment/wings/elytra.png");
                        defaultElytra = new ClientAsset.ResourceTexture(e, e);
                    }
                    elytra = defaultElytra;
                }
                state.skin = new PlayerSkin(s.body(), new ClientAsset.ResourceTexture(cape, cape), elytra, s.model(), s.secure());
            }
        }
    }

    // ------------------------------------------------------------------ cape textures

    private static final class Cape {
        TCCapeArt.Builder builder;
        DynamicTexture tex;
        Identifier id;
        int shown = -1;
        long lastUsed;
    }

    private static final Map<String, Cape> CAPES = new HashMap<>();
    private static final int MAX_KEPT = 10;
    private static final long BUDGET_NS = 3_000_000L;
    private static long lastStepFrame = Long.MIN_VALUE;
    private static int capeNo;
    // the texture is a Minecraft cape texture (64 x 32) at the cape pictures' detail
    private static final int TW = 64 * TCCapeArt.S, TH = 32 * TCCapeArt.S;

    /** The player's animated cape texture showing the current picture, or null while it's still being drawn. */
    public static Identifier cape(TCCosmetics.Look look) {
        String art = look.capeArtId();
        if (!TCCapeArt.known(art)) return null;
        int accent = look.accent();
        boolean themed = "theme".equals(art) || art.startsWith("custom:") && TCCapeArt.makerUsesTheme(art.substring(7));
        String key = themed ? art + "@" + Integer.toHexString(accent & 0xFFFFFF) : art;
        long now = System.currentTimeMillis();
        Cape c = CAPES.get(key);
        if (c == null) {
            // every kept cape was used a moment ago (lots of different capes around): don't throw one
            // away just to make another, or they'd be drawn over and over; show the normal cape instead
            if (CAPES.size() >= MAX_KEPT && oldestUse() > now - 1500L) return null;
            trim();
            c = new Cape();
            try {
                c.builder = new TCCapeArt.Builder(art, accent);
            } catch (Throwable t) {
                return null;
            }
            CAPES.put(key, c);
        }
        c.lastUsed = now;
        if (c.tex == null) {
            long frame = Minecraft.getInstance().getFrameTimeNs();
            if (frame == lastStepFrame) return null;
            lastStepFrame = frame;
            try {
                long start = System.nanoTime();
                boolean done;
                do {
                    done = c.builder.step(1);
                } while (!done && System.nanoTime() - start < BUDGET_NS);
                if (!done) return null;
                NativeImage img = new NativeImage(TW, TH, true);
                c.id = Identifier.fromNamespaceAndPath(NS, "cape/" + (capeNo++));
                c.tex = new DynamicTexture(() -> "Treasure Client cape", img);
                Minecraft.getInstance().getTextureManager().register(c.id, c.tex);
            } catch (Throwable t) {
                System.err.println("[Treasure Client] cape " + art + ": " + t);
                CAPES.remove(key);
                return null;
            }
        }
        int f = (int) ((now % TCCapeArt.LOOP_MS) * TCCapeArt.FRAMES / TCCapeArt.LOOP_MS);
        if (f != c.shown) {
            c.shown = f;
            int ox = (f % TCCapeArt.COLS) * TCCapeArt.CELL_W, oy = (f / TCCapeArt.COLS) * TCCapeArt.CELL_H;
            int[] px = c.builder.pixels;
            NativeImage img = c.tex.getPixels();
            if (img != null) {
                for (int y = 0; y < TCCapeArt.CELL_H; ++y) {
                    int row = (oy + y) * TCCapeArt.TEX_W + ox;
                    for (int x = 0; x < TCCapeArt.CELL_W; ++x) img.setPixel(x, y, px[row + x]);
                }
                c.tex.upload();
            }
        }
        return c.id;
    }

    private static long oldestUse() {
        long old = Long.MAX_VALUE;
        for (Cape c : CAPES.values()) old = Math.min(old, c.lastUsed);
        return old;
    }

    private static void trim() {
        while (CAPES.size() >= MAX_KEPT) {
            String oldKey = null;
            long old = Long.MAX_VALUE;
            for (Map.Entry<String, Cape> e : CAPES.entrySet()) {
                if (e.getValue().lastUsed < old) {
                    old = e.getValue().lastUsed;
                    oldKey = e.getKey();
                }
            }
            if (oldKey == null) return;
            Cape c = CAPES.remove(oldKey);
            if (c != null && c.id != null) Minecraft.getInstance().getTextureManager().release(c.id);
        }
    }

    // ------------------------------------------------------------------ the small shared textures

    private static Identifier dot, noise;

    /** The soft dot for glows: white, see-through towards the edge (the colour comes from each corner). */
    public static Identifier dot() {
        if (dot == null) {
            int n = 128;
            float[] a = TCAuraShapes.dotAlpha(n);
            NativeImage img = new NativeImage(n, n, true);
            for (int y = 0; y < n; ++y) {
                for (int x = 0; x < n; ++x) {
                    int v = Math.round(a[y * n + x] * 255f);
                    img.setPixel(x, y, (v << 24) | 0xFFFFFF);
                }
            }
            dot = Identifier.fromNamespaceAndPath(NS, "cos_dot");
            Minecraft.getInstance().getTextureManager().register(dot, new DynamicTexture(() -> "Treasure Client glow", img));
        }
        return dot;
    }

    /** The faint grain hats and boots are drawn with (the same as the browser editions'). */
    public static Identifier noise() {
        if (noise == null) {
            int n = 16;
            NativeImage img = new NativeImage(n, n, true);
            for (int y = 0; y < n; ++y) {
                for (int x = 0; x < n; ++x) {
                    int v = 222 + (int) (TCCapeArt.hash(x, y, 1234) * 33f);
                    img.setPixel(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
                }
            }
            noise = Identifier.fromNamespaceAndPath(NS, "cos_noise");
            Minecraft.getInstance().getTextureManager().register(noise, new DynamicTexture(() -> "Treasure Client grain", img));
        }
        return noise;
    }
}
