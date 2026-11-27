package net.treasureclient.mixin;

import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.entity.state.TntRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;
import net.treasureclient.core.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


/** TNT Timer: lit TNT gets a name tag with the seconds left (green, then yellow, then red). */
@Mixin(TntRenderer.class)
public class TntTimerMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/PrimedTnt;Lnet/minecraft/client/renderer/entity/state/TntRenderState;F)V",
        at = @At("TAIL"))
    private void treasureclient$fuse(PrimedTnt tnt, TntRenderState state, float partial, CallbackInfo ci) {
        if (!Config.get().on("tntTimer")) return;
        float sec = Math.max(0f, (tnt.getFuse() - partial) / 20f);
        int col = sec > 2f ? 0x5BEA6B : sec > 1f ? 0xFFE066 : 0xFF4A4A;
        state.nameTag = Component.literal(net.treasureclient.hud.Hud.dec1(sec)).withColor(col);
        state.nameTagAttachment = new Vec3(0, tnt.getBbHeight() + 0.25, 0);
    }
}
