package net.treasureclient.compat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;

/** Arm swings for 1.7 Animations (26.3: swings got their own SwingAnimation). */
public final class HandHooks {
    private HandHooks() {}

    /** Starts a swing on your screen only (on the client this never sends a packet). */
    public static void swingLocally(LivingEntity e, InteractionHand hand) {
        e.swing(hand, SwingAnimation.DEFAULT, false);
    }

    public static boolean swinging(LivingEntity e) {
        return e.isSwinging();
    }

    /** How far through its swing the arm is (0..1). */
    public static float swingProgress(LivingEntity e, float partialTick) {
        return e.getSwingAnimation(partialTick);
    }

    /** Turns the pose (26.3 renamed PoseStack.mulPose to rotate). */
    public static void rotate(com.mojang.blaze3d.vertex.PoseStack pose, org.joml.Quaternionf q) {
        pose.rotate(q);
    }
}
