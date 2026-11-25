package net.treasureclient.boost;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

/**
 * Sidebar Cache and Tab List Cache.
 *
 * Sidebar: every frame the game goes through every score of the sidebar's objective, sorts them,
 * and builds every line again (team colours, the number). Here the finished lines are kept until a
 * score, objective, team or display slot changes (the scoreboard says so itself: onScoreChanged,
 * onTeamChanged...).
 *
 * Tab list: while Tab is held, every frame the game sorts every player, builds and measures every
 * name, and splits the header and footer into lines. Here the sorted list, the names and their
 * widths are kept until the player list or a team changes; the header and footer lines until the
 * server sends new ones. Pings, heads and heart scores are still read every frame.
 *
 * Both are also made fresh at least once a second (for anything another mod changes).
 */
public final class HudLists {
    private HudLists() {}

    private static final long KEEP_NS = 1_000_000_000L;

    /** Bumped whenever the scoreboard (scores, objectives, teams, display slots) or the player list changes. */
    private static volatile int board, players;

    public static void boardChanged() {
        board++;
    }

    public static void playersChanged() {
        players++;
    }

    // ------------------------------------------------------------------ the sidebar

    private static Object sideObjective;
    private static int sideBoard;
    private static long sideAt;
    private static Object[] sideRows;
    private static boolean sideReuse;

    /** The sidebar asks for every score of its objective (the first step of building its lines). */
    public static Collection<PlayerScoreEntry> sidebarScores(Scoreboard sb, Objective obj, Operation<Collection<PlayerScoreEntry>> list) {
        sideReuse = false;
        if (!Boosts.ACTIVE[Boosts.SIDEBAR]) {
            sideRows = null;
            return list.call(sb, obj);
        }
        try {
            long now = System.nanoTime();
            int b = board;
            if (sideRows != null && sideObjective == obj && sideBoard == b && now - sideAt < KEEP_NS) {
                sideReuse = true;
                Boosts.ran(Boosts.SIDEBAR);
                return List.of(); // nothing to go through: the kept lines are handed back below
            }
            sideObjective = obj;
            sideBoard = b;
            sideAt = now;
            sideRows = null;
        } catch (Throwable t) {
            Boosts.failed(Boosts.SIDEBAR, t);
        }
        return list.call(sb, obj);
    }

    /** The sidebar's finished lines (sorted, built). */
    public static Object[] sidebarRows(Stream<?> stream, IntFunction<?> gen, Operation<Object[]> make) {
        if (sideReuse && sideRows != null) {
            sideReuse = false;
            return sideRows;
        }
        sideReuse = false;
        Object[] rows = make.call(stream, gen);
        if (Boosts.ACTIVE[Boosts.SIDEBAR]) sideRows = rows;
        return rows;
    }

    // ------------------------------------------------------------------ the tab list

    private static List<PlayerInfo> tabList;
    private static int tabBoard, tabPlayers;
    private static long tabAt;

    private record Kept(Component name, int siblings, Style style) {}

    private static final IdentityHashMap<PlayerInfo, Kept> NAMES = new IdentityHashMap<>();
    private static final IdentityHashMap<Component, int[]> NAME_W = new IdentityHashMap<>();
    private static final IdentityHashMap<FormattedCharSequence, int[]> LINE_W = new IdentityHashMap<>();
    private static final Object[] SPLIT_TEXT = new Object[2];
    private static final int[] SPLIT_W = new int[2];
    private static final long[] SPLIT_AT = new long[2];
    @SuppressWarnings("unchecked")
    private static final List<FormattedCharSequence>[] SPLIT_OUT = new List[2];

    /** The sorted list of players (the tab list's first step each frame). */
    public static List<PlayerInfo> tabPlayers(PlayerTabOverlay tab, Operation<List<PlayerInfo>> sort) {
        if (!Boosts.ACTIVE[Boosts.TAB_LIST]) {
            tabList = null;
            return sort.call(tab);
        }
        try {
            long now = System.nanoTime();
            int b = board, p = players;
            if (tabList != null && tabBoard == b && tabPlayers == p && now - tabAt < KEEP_NS) {
                Boosts.ran(Boosts.TAB_LIST);
                return tabList;
            }
            tabBoard = b;
            tabPlayers = p;
            tabAt = now;
            NAMES.clear();
            NAME_W.clear();
            tabList = null;
            List<PlayerInfo> list = sort.call(tab);
            tabList = list;
            return list;
        } catch (Throwable t) {
            Boosts.failed(Boosts.TAB_LIST, t);
            tabList = null;
            return sort.call(tab);
        }
    }

    /** A player's name in the tab list. */
    public static Component tabName(PlayerTabOverlay tab, PlayerInfo info, Operation<Component> make) {
        if (!Boosts.ACTIVE[Boosts.TAB_LIST] || tabList == null) return make.call(tab, info);
        Kept k = NAMES.get(info);
        if (k != null) {
            if (k.name().getSiblings().size() == k.siblings() && k.name().getStyle() == k.style()) return k.name();
            // something changed the kept name itself: make it fresh every time from now on
            Boosts.failed(Boosts.TAB_LIST, new IllegalStateException("a tab list name was changed after it was made"));
            return make.call(tab, info);
        }
        Component c = make.call(tab, info);
        if (c != null) {
            NAMES.put(info, new Kept(c, c.getSiblings().size(), c.getStyle()));
            NAME_W.put(c, new int[]{-1});
        }
        return c;
    }

    /** Measuring a name (or score) in the tab list. */
    public static int tabWidth(Font font, FormattedText text, Operation<Integer> measure) {
        if (Boosts.ACTIVE[Boosts.TAB_LIST] && text instanceof Component) {
            int[] w = NAME_W.get(text);
            if (w != null) {
                if (w[0] < 0) w[0] = measure.call(font, text);
                return w[0];
            }
        }
        return measure.call(font, text);
    }

    /** Splitting the header or footer into lines. */
    public static List<FormattedCharSequence> tabSplit(Font font, FormattedText text, int width, Operation<List<FormattedCharSequence>> split) {
        if (!Boosts.ACTIVE[Boosts.TAB_LIST] || text == null) return split.call(font, text, width);
        long now = System.nanoTime();
        for (int i = 0; i < 2; i++) {
            if (SPLIT_TEXT[i] == text && SPLIT_W[i] == width && SPLIT_OUT[i] != null && now - SPLIT_AT[i] < KEEP_NS) return SPLIT_OUT[i];
        }
        List<FormattedCharSequence> lines = split.call(font, text, width);
        int slot = SPLIT_AT[0] <= SPLIT_AT[1] ? 0 : 1; // replace the older one
        SPLIT_TEXT[slot] = text;
        SPLIT_W[slot] = width;
        SPLIT_AT[slot] = now;
        SPLIT_OUT[slot] = lines;
        if (LINE_W.size() > 512) LINE_W.clear();
        for (FormattedCharSequence l : lines) LINE_W.put(l, new int[]{-1});
        return lines;
    }

    /** Measuring a header or footer line. */
    public static int tabLineWidth(Font font, FormattedCharSequence line, Operation<Integer> measure) {
        if (Boosts.ACTIVE[Boosts.TAB_LIST]) {
            int[] w = LINE_W.get(line);
            if (w != null) {
                if (w[0] < 0) w[0] = measure.call(font, line);
                return w[0];
            }
        }
        return measure.call(font, line);
    }
}
