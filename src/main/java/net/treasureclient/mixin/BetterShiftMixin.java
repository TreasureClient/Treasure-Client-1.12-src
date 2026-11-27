package net.treasureclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.treasureclient.core.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Better Shift: since 1.14 sneaking drops your view from 1.62 to 1.27 blocks, so shift-bridging
 * makes the screen bounce up and down. In 1.8 it only dropped 0.08 blocks. This puts the camera
 * back to the 1.8 drop while you sneak. Only the camera moves: your real eye height, hitbox and
 * where you aim and place from are Minecraft's own, so the server sees exactly the same player.
 */
@Mixin(Camera.class)
public class BetterShiftMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F"))
    private float treasureclient$oldSneakCamera(Entity e, Operation<Float> op) {
        float h = op.call(e);
        if (!Config.get().on("betterShift") || e != Minecraft.getInstance().player || e.getPose() != Pose.CROUCHING) return h;
        float stand = e.getEyeHeight(Pose.STANDING);
        return stand - 0.08f * (stand / 1.62f);  // 1.8's sneak drop, scaled for bigger or smaller players
    }
}
