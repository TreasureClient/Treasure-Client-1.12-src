package net.treasureclient.mixin.boost;

import net.minecraft.world.entity.Entity;
import net.treasureclient.boost.TCNameHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Name Tag Cache: each entity keeps its name tag and score line (see NameTags). */
@Mixin(Entity.class)
public class NameTagEntityMixin implements TCNameHolder {
    @Unique
    private Object tc$nameTag, tc$scoreTag;

    @Override
    public Object tc$name() {
        return tc$nameTag;
    }

    @Override
    public void tc$setName(Object tag) {
        tc$nameTag = tag;
    }

    @Override
    public Object tc$score() {
        return tc$scoreTag;
    }

    @Override
    public void tc$setScore(Object tag) {
        tc$scoreTag = tag;
    }
}
