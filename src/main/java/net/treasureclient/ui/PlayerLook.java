package net.treasureclient.ui;

import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.model.geom.ModelPart;
import net.treasureclient.compat.V;

/** Turns the main menu player's head toward the mouse (the hat layer is attached to the head). */
public final class PlayerLook {
    private PlayerLook() {}

    /** mouseX / mouseY in Minecraft GUI units. */
    public static void aim(PlayerSkinWidget w, double mouseX, double mouseY) {
        float bodyYaw = (float) Math.toRadians(V.skinRotationY(w)); // the player can be dragged to spin
        double k = Math.max(20, w.getHeight() * 0.45);
        double dx = mouseX - (w.getX() + w.getWidth() / 2.0);
        double dy = mouseY - (w.getY() + w.getHeight() * 0.14);
        // positive yRot turns the face toward the left of the screen; the widget turns the body by -rotationY
        float yaw = (float) (-Math.atan(dx / k)) + bodyYaw;
        yaw = (float) Math.atan2(Math.sin(yaw), Math.cos(yaw)); // keep it in -180..180 degrees
        yaw = Math.max(-1.2f, Math.min(1.2f, yaw));
        float pitch = (float) Math.max(-0.8, Math.min(0.8, Math.atan(dy / k)));
        for (ModelPart head : V.skinHeads(w)) {
            head.yRot = yaw;
            head.xRot = pitch;
        }
    }
}
