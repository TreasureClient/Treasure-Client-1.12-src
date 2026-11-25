package net.treasureclient.mixin.boost;

import java.util.Collection;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.Gui;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;
import net.treasureclient.boost.HudLists;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sidebar Cache (Gui.displayScoreboardSidebar): its lines are "listPlayerScores(objective).stream()
 * .filter().sorted().limit(15).map(build a line).toArray()"; the kept lines come back from toArray.
 */
@Mixin(Gui.class)
public class SidebarMixin {
    @WrapOperation(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/scores/Scoreboard;listPlayerScores(Lnet/minecraft/world/scores/Objective;)Ljava/util/Collection;"))
    private Collection<PlayerScoreEntry> tc$scores(Scoreboard sb, Objective obj, Operation<Collection<PlayerScoreEntry>> list) {
        return HudLists.sidebarScores(sb, obj, list);
    }

    @WrapOperation(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
        target = "Ljava/util/stream/Stream;toArray(Ljava/util/function/IntFunction;)[Ljava/lang/Object;"))
    private Object[] tc$rows(Stream<?> stream, IntFunction<?> gen, Operation<Object[]> make) {
        return HudLists.sidebarRows(stream, gen, make);
    }
}
