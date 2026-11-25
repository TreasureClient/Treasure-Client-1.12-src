package net.treasureclient.boost;

import java.util.IdentityHashMap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;

/**
 * Name Tag Cache. Every frame, for every name tag on screen, the game builds the name again from
 * scratch: the team's colour, prefix and suffix, plus chat-only extras it never draws on a tag (the
 * "/tell" click, the hover box). Then it works out the characters to draw (with the right-to-left
 * text check) and measures the width. The score under names is built again the same way.
 *
 * Here each entity keeps its name tag and score line, and gets the same one back while nothing it's
 * made from has changed: for players their team, the team's prefix, suffix and colour; for named mobs
 * and holograms their custom name; for the score the objective, its title, number format and the score.
 * Because it's the same object, Minecraft's own memory of the characters to draw (kept inside the
 * name, but useless when the name is new every frame) now works, and the width is remembered too.
 *
 * Safety: every name is made fresh at least once a second anyway (so a change another mod makes to
 * names still shows up), dropped items are never kept (their name follows the item), and if anything
 * ever changes a kept name, that entity goes back to the normal way for good.
 */
public final class NameTags {
    private NameTags() {}

    private static final long KEEP_NS = 1_000_000_000L;

    /** What one entity's name tag (or score line) was made from. */
    static final class Tag {
        Component value;
        int siblings;
        Style style;
        Object a, b, c, d, e;
        int v;
        long at;
        boolean noCache;

        boolean same(Object a, Object b, Object c, Object d, Object e, int v) {
            return this.a == a && this.b == b && this.c == c && this.d == d && this.e == e && this.v == v;
        }

        /** Something changed the kept name itself (added to it, restyled it). */
        boolean tampered() {
            return value.getSiblings().size() != siblings || value.getStyle() != style;
        }

        void set(Component value, Object a, Object b, Object c, Object d, Object e, int v, long now) {
            this.value = value;
            this.siblings = value.getSiblings().size();
            this.style = value.getStyle();
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
            this.v = v;
            this.at = now;
        }
    }

    /** The width of each kept name ({-1} until first measured). Only kept names are in here. */
    private static final IdentityHashMap<Component, int[]> WIDTHS = new IdentityHashMap<>();

    private static long keepFor(Entity e) {
        return KEEP_NS + (e.getId() & 255) * 1_000_000L; // spread out, so they aren't all remade in one frame
    }

    // ------------------------------------------------------------------ the name

    /** The name tag (EntityRenderer.getNameTag's getDisplayName()). */
    public static Component name(Entity e, Operation<Component> made) {
        if (!Boosts.ACTIVE[Boosts.NAME_TAGS] || e instanceof ItemEntity || !(e instanceof TCNameHolder h)) return made.call(e);
        Object key;
        PlayerTeam team;
        Object pre, suf, col;
        Tag t;
        long now;
        try {
            if (e instanceof Player p) key = p.getGameProfile();
            else {
                key = e.getCustomName();
                if (key == null) return made.call(e); // not a named mob or hologram: leave it alone
            }
            team = e.getTeam();
            pre = team == null ? null : team.getPlayerPrefix();
            suf = team == null ? null : team.getPlayerSuffix();
            col = team == null ? null : team.getColor();
            t = (Tag) h.tc$name();
            now = System.nanoTime();
            if (t != null) {
                if (t.noCache) return made.call(e);
                if (t.same(key, team, pre, suf, col, 0) && now - t.at < keepFor(e)) {
                    if (!t.tampered()) {
                        Boosts.ran(Boosts.NAME_TAGS);
                        return t.value;
                    }
                    t.noCache = true;
                    WIDTHS.remove(t.value);
                    return made.call(e);
                }
                WIDTHS.remove(t.value);
            }
        } catch (Throwable x) {
            Boosts.failed(Boosts.NAME_TAGS, x);
            return made.call(e);
        }
        Component c = made.call(e);
        if (c == null) return null;
        if (t == null) h.tc$setName(t = new Tag());
        t.set(c, key, team, pre, suf, col, 0, now);
        remember(c);
        return c;
    }

    // ------------------------------------------------------------------ the score under the name

    /** The score line under a name (Entity.belowNameDisplay()). */
    public static Component score(Entity e, Operation<Component> made) {
        if (!Boosts.ACTIVE[Boosts.NAME_TAGS] || !(e instanceof TCNameHolder h)) return made.call(e);
        Objective obj;
        ReadOnlyScoreInfo s;
        Object title, objFormat, scoreFormat;
        int value;
        Tag t;
        long now;
        try {
            Scoreboard sb = e.level().getScoreboard();
            obj = sb.getDisplayObjective(DisplaySlot.BELOW_NAME);
            if (obj == null) return made.call(e);
            s = sb.getPlayerScoreInfo(e, obj);
            if (s == null) return made.call(e);
            title = obj.getDisplayName();
            objFormat = obj.numberFormat();
            scoreFormat = s.numberFormat();
            value = s.value();
            t = (Tag) h.tc$score();
            now = System.nanoTime();
            if (t != null) {
                if (t.noCache) return made.call(e);
                if (t.same(obj, title, objFormat, scoreFormat, null, value) && now - t.at < keepFor(e)) {
                    if (!t.tampered()) {
                        Boosts.ran(Boosts.NAME_TAGS);
                        return t.value;
                    }
                    t.noCache = true;
                    WIDTHS.remove(t.value);
                    return made.call(e);
                }
                WIDTHS.remove(t.value);
            }
        } catch (Throwable x) {
            Boosts.failed(Boosts.NAME_TAGS, x);
            return made.call(e);
        }
        Component c = made.call(e);
        if (c == null) return null;
        if (t == null) h.tc$setScore(t = new Tag());
        t.set(c, obj, title, objFormat, scoreFormat, null, value, now);
        remember(c);
        return c;
    }

    // ------------------------------------------------------------------ the width

    private static void remember(Component c) {
        if (WIDTHS.size() >= 4096) WIDTHS.clear(); // names of entities that went away
        WIDTHS.put(c, new int[]{-1});
    }

    /** Measuring a name tag's width (Font.width while a tag is queued to draw). */
    public static int width(Font font, FormattedText text, Operation<Integer> measure) {
        if (Boosts.ACTIVE[Boosts.NAME_TAGS] && text instanceof Component) {
            int[] w = WIDTHS.get(text);
            if (w != null) {
                if (w[0] < 0) w[0] = measure.call(font, text);
                return w[0];
            }
        }
        return measure.call(font, text);
    }
}
