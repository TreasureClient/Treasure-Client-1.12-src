package net.treasureclient.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cooldowns: which of your items can't be used yet, and for how long. */
public final class Cooldowns {
    private Cooldowns() {}

    /** Cooldown group -> {end time (ms), length (ms)}. */
    private static final Map<Identifier, long[]> ACTIVE = new LinkedHashMap<>();
    private static final Map<Identifier, ItemStack> ICON = new java.util.HashMap<>();

    public record Row(ItemStack icon, float secondsLeft, float fraction) {}

    private static boolean mine(ItemCooldowns who) {
        var p = Minecraft.getInstance().player;
        return p != null && who == p.getCooldowns();
    }

    public static void started(ItemCooldowns who, Identifier group, int ticks) {
        if (!mine(who)) return;
        if (ticks <= 0) {
            ACTIVE.remove(group);
            return;
        }
        long len = ticks * 50L;
        ACTIVE.put(group, new long[]{System.currentTimeMillis() + len, len});
    }

    public static void removed(ItemCooldowns who, Identifier group) {
        if (mine(who)) ACTIVE.remove(group);
    }

    public static void reset() {
        ACTIVE.clear();
    }

    /** The cooldowns running now, oldest first. */
    public static List<Row> rows() {
        List<Row> out = new ArrayList<>();
        if (ACTIVE.isEmpty()) return out;
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<Identifier, long[]>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Identifier, long[]> e = it.next();
            long left = e.getValue()[0] - now;
            if (left <= 0) {
                it.remove();
                continue;
            }
            out.add(new Row(icon(e.getKey()), left / 1000f, left / (float) e.getValue()[1]));
        }
        return out;
    }

    /** The item a cooldown group belongs to (its own item, or one in your inventory that uses it). */
    private static ItemStack icon(Identifier group) {
        ItemStack s = ICON.get(group);
        if (s != null) return s;
        var item = BuiltInRegistries.ITEM.getValue(group);
        if (item != null && item != Items.AIR) s = new ItemStack(item);
        else {
            s = ItemStack.EMPTY;
            var p = Minecraft.getInstance().player;
            if (p != null) {
                var inv = p.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack st = inv.getItem(i);
                    if (!st.isEmpty() && group.equals(p.getCooldowns().getCooldownGroup(st))) {
                        s = st.copyWithCount(1);
                        break;
                    }
                }
            }
            if (s.isEmpty()) return s; // try again next frame
        }
        ICON.put(group, s);
        return s;
    }
}
