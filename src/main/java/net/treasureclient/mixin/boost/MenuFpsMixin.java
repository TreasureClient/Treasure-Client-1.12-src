package net.treasureclient.mixin.boost;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.treasureclient.boost.Boosts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Full Menu Frame Rate (the same in 1.21.11 and 26.x): outside a world, Minecraft holds every menu to
 * 60 FPS (getFramerateLimit's only "60"). Here menus use your Max Framerate setting instead, like in a world.
 * The other slow-downs (window minimised, away from the keyboard) are left as they are.
 */
@Mixin(FramerateLimitTracker.class)
public class MenuFpsMixin {
    @Shadow
    private int framerateLimit;

    @ModifyConstant(method = "getFramerateLimit", constant = @Constant(intValue = 60))
    private int tc$menuLimit(int sixty) {
        if (!Boosts.ACTIVE[Boosts.MENU_FPS]) return sixty;
        Boosts.ran(Boosts.MENU_FPS);
        return Math.max(sixty, framerateLimit);
    }
}
