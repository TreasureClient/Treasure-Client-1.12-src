package net.treasureclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.treasureclient.feature.OldAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low Shield + 1.7 Animations: which item each hand is drawing, then a nudge after the arm is placed. */
@Mixin(net.minecraft.client.renderer.ItemInHandRenderer.class)
public class HandMixin {
    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true)
    private ItemStack treasureclient$item(ItemStack stack) {
        OldAnim.drawing = stack;
        return stack;
    }

    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true)
    private InteractionHand treasureclient$hand(InteractionHand hand) {
        OldAnim.drawingHand = hand;
        return hand;
    }

    @Inject(method = "applyItemArmTransform", at = @At("TAIL"))
    private void treasureclient$arm(PoseStack pose, HumanoidArm arm, float equip, CallbackInfo ci) {
        OldAnim.afterArmTransform(pose, arm);
    }
}
