package net.treasureclient.cos;

/** Treasure Client cosmetics: what a player render state carries for us (added to AvatarRenderState by a mixin). */
public interface TCAvatarState {
    TCCosmetics.Look tc$look();

    void tc$setLook(TCCosmetics.Look look);

    /** Their boot trail (null without one). */
    TCAuraShapes.Trail tc$trail();

    void tc$setTrail(TCAuraShapes.Trail trail);

    /** Flying, jumping or sprinting (rocket flames and the like). */
    boolean tc$active();

    void tc$setActive(boolean active);
}
