package net.treasureclient.mixin;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.treasureclient.cos.TCAuraShapes;
import net.treasureclient.cos.TCAvatarState;
import net.treasureclient.cos.TCCosmetics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Treasure Client cosmetics: room for the player's look in their render state. */
@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements TCAvatarState {
    @Unique
    private TCCosmetics.Look tc$look;
    @Unique
    private TCAuraShapes.Trail tc$trail;
    @Unique
    private boolean tc$active;

    @Override
    public TCCosmetics.Look tc$look() {
        return tc$look;
    }

    @Override
    public void tc$setLook(TCCosmetics.Look look) {
        tc$look = look;
    }

    @Override
    public TCAuraShapes.Trail tc$trail() {
        return tc$trail;
    }

    @Override
    public void tc$setTrail(TCAuraShapes.Trail trail) {
        tc$trail = trail;
    }

    @Override
    public boolean tc$active() {
        return tc$active;
    }

    @Override
    public void tc$setActive(boolean active) {
        tc$active = active;
    }
}
