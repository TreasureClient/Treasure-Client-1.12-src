package net.treasureclient.feature;

import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.treasureclient.compat.Gfx;
import net.treasureclient.compat.V;
import net.treasureclient.core.Config;
import net.treasureclient.core.Mods;
import net.treasureclient.core.Theme;
import net.treasureclient.ui.Ui;

/** Custom Crosshair (replaces Minecraft's) and Crosshair Addons (drawn around either one). */
public final class Crosshair {
    private Crosshair() {}

    private static final ItemStack ELYTRA = new ItemStack(Items.ELYTRA);
    private static final ItemStack ROCKET = new ItemStack(Items.FIREWORK_ROCKET);
    private static final ItemStack SHIELD = new ItemStack(Items.SHIELD);

    private static long hitAt, shieldBreakAt, boostUntil;
    // shield break: your axe hit a player who was blocking; if their shield drops right after, it broke
    private static int shieldTarget = -1;
    private static long shieldHitAt;

    public static boolean ours() {
        return Config.get().on("crosshair");
    }

    /** Minecraft only draws the crosshair in first person, and not for a spectator looking at nothing. */
    private static boolean visible(Minecraft mc) {
        return mc.player != null && !V.hudHidden() && mc.options.getCameraType().isFirstPerson()
            && !(mc.player.isSpectator() && mc.crosshairPickEntity == null);
    }

    // ------------------------------------------------------------------ the crosshair

    public static void draw() {
        Minecraft mc = Minecraft.getInstance();
        if (!visible(mc)) return;
        Ui.begin();
        try {
            Config c = Config.get();
            float cx = Ui.vw / 2f, cy = Ui.vh / 2f;
            float size = clamp(c.num("crosshairSize", 22), 12, 40);
            float t = clamp(c.num("crosshairThickness", 2), 1, 5);
            float gap = clamp(c.num("crosshairGap", 3), 0, 8);
            int a = Math.round(255 * clamp(c.num("crosshairOpacity", 100), 20, 100) / 100f);
            int col = a << 24 | Mods.color("crosshairColor");
            float half = size / 2f, arm = Math.max(1f, half - gap);
            switch (c.str("crosshairStyle", "cross")) {
                case "dot" -> Ui.circle(cx, cy, Math.max(t, size / 8f), col);
                case "tshape" -> {
                    Ui.rect(cx - half, cy - t / 2f, arm, t, col);
                    Ui.rect(cx + gap, cy - t / 2f, arm, t, col);
                    Ui.rect(cx - t / 2f, cy + gap, t, arm, col);
                }
                default -> {
                    Ui.rect(cx - half, cy - t / 2f, arm, t, col);
                    Ui.rect(cx + gap, cy - t / 2f, arm, t, col);
                    Ui.rect(cx - t / 2f, cy - half, t, arm, col);
                    Ui.rect(cx - t / 2f, cy + gap, t, arm, col);
                }
            }
            // the attack cooldown, when Minecraft's Attack Indicator is set to Crosshair
            if (mc.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR) {
                float f = mc.player.getAttackStrengthScale(0f);
                if (f < 1f) {
                    float w = 16, y = cy + half + 4;
                    Ui.rect(cx - w / 2, y, w, 2, 0x66000000);
                    Ui.rect(cx - w / 2, y, w * f, 2, col | 0xFF000000);
                }
            }
        } finally {
            Ui.end();
        }
    }

    // ------------------------------------------------------------------ the addons

    /** An arrow you shot hit a player (Minecraft plays the "ding" for this). */
    public static void arrowHit() {
        if (Config.get().flag("chHit", true)) hitAt = System.currentTimeMillis();
    }

    /** One of your melee hits landed. */
    public static void meleeHit() {
        if (Config.get().flag("chHitMelee", false)) hitAt = System.currentTimeMillis();
    }

    /** You swung at an entity. */
    public static void attacked(Player you, Entity target) {
        if (target instanceof Player p && p.isBlocking() && you.getMainHandItem().is(ItemTags.AXES)) {
            shieldTarget = p.getId();
            shieldHitAt = System.currentTimeMillis();
        }
    }

    /** You used an item: a firework while gliding is a boost. */
    public static void usedItem(Player you, ItemStack stack) {
        if (!stack.is(Items.FIREWORK_ROCKET) || !you.isFallFlying()) return;
        Fireworks fw = stack.get(DataComponents.FIREWORKS);
        int flight = fw == null ? 1 : fw.flightDuration();
        long ticks = 10L * (flight + 1) + 5; // Minecraft adds 0-11 random ticks: use the middle
        boostUntil = System.currentTimeMillis() + ticks * 50;
    }

    public static void tick(Minecraft mc) {
        if (shieldTarget < 0 || mc.level == null) return;
        long now = System.currentTimeMillis();
        if (now - shieldHitAt > 600) {
            shieldTarget = -1;
            return;
        }
        Entity e = mc.level.getEntity(shieldTarget);
        if (e instanceof Player p && !p.isBlocking() && now - shieldHitAt > 50) {
            shieldTarget = -1;
            if (Config.get().flag("chShieldBreak", true)) shieldBreakAt = now;
        }
    }

    public static void addons() {
        Minecraft mc = Minecraft.getInstance();
        if (!Config.get().on("chAddons") || !visible(mc)) return;
        Config c = Config.get();
        LocalPlayer you = mc.player;
        long now = System.currentTimeMillis();
        Ui.begin();
        try {
            float cx = Ui.vw / 2f, cy = Ui.vh / 2f;
            float r = (ours() ? clamp(c.num("crosshairSize", 22), 12, 40) / 2f : 9f) + 5f;
            int accent = Theme.current().a1();
            Entity t = mc.crosshairPickEntity;
            boolean target = t != null && !t.isInvisible()
                && (t instanceof Player || (c.flag("chMobs", false) && t instanceof Enemy));
            if (target && c.flag("chTarget", true)) brackets(cx, cy, r, accent | 0xFF000000);
            if (c.flag("chShieldUp", true) && t instanceof Player p && p.isBlocking())
                icon(SHIELD, cx + r + 4, cy - 6, 12, 1f);
            if (c.flag("chElytra", true) && you.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA))
                icon(ELYTRA, cx - r - 16, cy + r - 4, 12, you.isFallFlying() ? 1f : 0.55f);
            if (c.flag("chRocket", true) && now < boostUntil && you.isFallFlying()) {
                String s = String.format(java.util.Locale.ROOT, "%.1fs", (boostUntil - now) / 1000f);
                icon(ROCKET, cx + r + 2, cy + r - 4, 12, 1f);
                Ui.text(Ui.MONOB, s, cx + r + 16, cy + r - 2, 9.5f, 0xFFFFFFFF, 0, 1);
            } else if (now >= boostUntil) {
                boostUntil = 0;
            }
            if (now - shieldBreakAt < 1200) {
                float f = 1f - (now - shieldBreakAt) / 1200f;
                icon(SHIELD, cx - 7, cy - r - 18, 14, f);
                int a = Math.round(255 * f);
                Ui.rect(cx - 8, cy - r - 11, 16, 2, a << 24 | 0xFF4A4A);
            }
            if (now - hitAt < 300) hitMarker(cx, cy, 1f - (now - hitAt) / 300f);
        } finally {
            Ui.end();
        }
    }

    /** "[ ]" around the crosshair. */
    private static void brackets(float cx, float cy, float r, int col) {
        float h = 10, w = 3.5f, s = 1.5f;
        Ui.rect(cx - r, cy - h / 2, s, h, col);
        Ui.rect(cx - r, cy - h / 2, w, s, col);
        Ui.rect(cx - r, cy + h / 2 - s, w, s, col);
        Ui.rect(cx + r - s, cy - h / 2, s, h, col);
        Ui.rect(cx + r - w, cy - h / 2, w, s, col);
        Ui.rect(cx + r - w, cy + h / 2 - s, w, s, col);
    }

    /** A small white X that fades out. */
    private static void hitMarker(float cx, float cy, float f) {
        int col = Math.round(255 * f) << 24 | 0xFFFFFF;
        var pose = Gfx.pose();
        pose.pushMatrix();
        pose.translate(cx * Ui.k, cy * Ui.k);
        pose.rotate((float) (Math.PI / 4));
        float gap = 4, len = 5, t = 1.5f;
        Ui.rect(-gap - len, -t / 2, len, t, col);
        Ui.rect(gap, -t / 2, len, t, col);
        Ui.rect(-t / 2, -gap - len, t, len, col);
        Ui.rect(-t / 2, gap, t, len, col);
        pose.popMatrix();
    }

    /** An item icon {@code size} CSS px wide at (x, y). */
    private static void icon(ItemStack stack, float x, float y, float size, float alpha) {
        if (alpha < 0.3f) return; // item icons can't fade, so they just disappear at the end
        var pose = Gfx.pose();
        pose.pushMatrix();
        pose.translate(x * Ui.k, y * Ui.k);
        float s = size * Ui.k / 16f;
        pose.scale(s, s);
        Gfx.item(stack, 0, 0);
        pose.popMatrix();
    }

    private static float clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
