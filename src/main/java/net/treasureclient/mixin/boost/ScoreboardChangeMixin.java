package net.treasureclient.mixin.boost;

import net.minecraft.world.scores.Scoreboard;
import net.treasureclient.boost.HudLists;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sidebar + Tab List Cache: the scoreboard's own "something changed" methods (a score, its lock,
 * an objective, a team, a team's members, a display slot) tell the caches to make things fresh.
 */
@Mixin(Scoreboard.class)
public class ScoreboardChangeMixin {
    @Inject(method = {"onScoreChanged", "onScoreLockChanged", "onPlayerRemoved", "onPlayerScoreRemoved", "onObjectiveAdded",
        "onObjectiveChanged", "onObjectiveRemoved", "onTeamAdded", "onTeamChanged", "onTeamRemoved", "setDisplayObjective",
        "addPlayerToTeam", "removePlayerFromTeam"}, at = @At("HEAD"))
    private void tc$changed(CallbackInfo ci) {
        HudLists.boardChanged();
    }
}
