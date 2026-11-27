package net.treasureclient.compat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/** Arm swings for 1.7 Animations (up to 26.2; 26.3 has its own copy in src/v26_3). */
public final class HandHooks {
    private HandHooks() {}

    /** Starts a swing on your screen only (no packet: the server never hears about it). */
    public static void swingLocally(LivingEntity e, InteractionHand hand) {
        e.swing(hand, false);
    }

    public static boolean swinging(LivingEntity e) {
        return e.swinging;
    }

    /** How far through its swing the arm is (0..1). */
    public static float swingProgress(LivingEntity e, float partialTick) {
        return e.getAttackAnim(partialTick);
    }

    /** Turns the pose (26.3 renamed PoseStack.mulPose to rotate). */
    public static void rotate(com.mojang.blaze3d.vertex.PoseStack pose, org.joml.Quaternionf q) {
        pose.mulPose(q);
    }
}
