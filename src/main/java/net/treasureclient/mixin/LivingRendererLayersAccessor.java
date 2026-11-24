package net.treasureclient.mixin;

import java.util.List;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Treasure Client cosmetics: lets us add our layer to the player renderer. */
@Mixin(LivingEntityRenderer.class)
public interface LivingRendererLayersAccessor {
    @Accessor("layers")
    @SuppressWarnings("rawtypes")
    List tc$layers();
}
