package net.treasureclient.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.treasureclient.compat.HandHooks;
import net.treasureclient.core.Config;

/** Low Shield and 1.7 Animations: small changes to how the item in your hand moves. */
public final class OldAnim {
    private OldAnim() {}

    /** The item and hand being drawn right now (set by HandMixin). */
    public static ItemStack drawing = ItemStack.EMPTY;
    public static InteractionHand drawingHand = InteractionHand.MAIN_HAND;

    /** The attack-cooldown dip: with "small dip" on, the item only drops a fifth as far. */
    public static float dip(float v) {
        Config c = Config.get();
        if (!c.on("oldAnim") || !c.flag("oaNoDip", true)) return v;
        return 1f - (1f - v) * 0.2f;
    }

    /** Runs right after Minecraft puts the arm in place. */
    public static void afterArmTransform(PoseStack pose, HumanoidArm arm) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || drawing == null) return;
        Config c = Config.get();
        if (c.on("lowShield") && drawing.is(Items.SHIELD))
            pose.translate(0f, -0.5f * c.num("lowShieldA", 30) / 100f, 0f);
        if (c.on("oldAnim") && c.flag("oaUseSwing", true) && p.isUsingItem() && p.getUsedItemHand() == drawingHand
            && HandHooks.swinging(p)) {
            // the normal attack swing, which Minecraft leaves out while an item is in use
            float sw = HandHooks.swingProgress(p, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            float a = Mth.sin(sw * sw * Math.PI);
            float b = Mth.sin(Mth.sqrt(sw) * Math.PI);
            HandHooks.rotate(pose, Axis.YP.rotationDegrees(side * (45f + a * -20f)));
            HandHooks.rotate(pose, Axis.ZP.rotationDegrees(side * b * -20f));
            HandHooks.rotate(pose, Axis.XP.rotationDegrees(b * -80f));
            HandHooks.rotate(pose, Axis.YP.rotationDegrees(side * -45f));
        }
    }

    /** Holding attack while eating, drinking, drawing a bow or blocking swings the arm (only on your screen). */
    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || net.treasureclient.compat.V.screen() != null) return;
        Config c = Config.get();
        if (!c.on("oldAnim") || !c.flag("oaUseSwing", true)) return;
        if (p.isUsingItem() && mc.options.keyAttack.isDown() && !HandHooks.swinging(p))
            HandHooks.swingLocally(p, p.getUsedItemHand());
    }
}
