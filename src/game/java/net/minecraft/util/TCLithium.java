package net.minecraft.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.monster.EntityShulker;

/**
 * Treasure Client: Lithium port, based on the mod by CaffeineMC (jellysquid3).
 * Lithium has no 1.12 version and mostly targets servers, so this brings over
 * the ideas that fit 1.12's code. Every change gives the same result as
 * vanilla, just with less work. The singleplayer server runs this same code,
 * so it gets the speedups too (there it's always on).
 * <ul>
 * <li>Entity collisions: each moving entity searched every nearby entity for
 * collision boxes every tick, but only boats, minecarts and shulkers have one.
 * Each world now tracks those, and the search is skipped when there are
 * none.</li>
 * <li>Block collisions: a box was made for every block checked just to test it;
 * now it's only made when it actually touches.</li>
 * <li>Chunk access: the last chunk looked up is remembered, since the game
 * asks for the same chunk many times in a row.</li>
 * <li>Removing many block entities or entities at once (chunks unloading,
 * switching games) searched the whole list once per removal; now it's one
 * pass.</li>
 * </ul>
 */
public final class TCLithium {

	private TCLithium() {
	}

	/** Lithium on/off. The client sets it from the Lithium switch; the singleplayer server keeps it on. */
	public static boolean enabled = true;

	/** Boats, minecarts and shulkers: the only entities that other entities can bump into. */
	public static boolean hasHardCollision(Entity e) {
		return e instanceof EntityBoat || e instanceof EntityMinecart || e instanceof EntityShulker;
	}

	/** Removes every element of {@code list} that's in {@code remove}, in one pass, keeping the order. */
	public static <T> void removeAll(List<T> list, Set<?> remove) {
		int n = list.size();
		int w = 0;
		for (int r = 0; r < n; ++r) {
			T e = list.get(r);
			if (!remove.contains(e)) {
				if (w != r) {
					list.set(w, e);
				}
				++w;
			}
		}
		for (int i = n - 1; i >= w; --i) {
			list.remove(i); // removing from the end doesn't shift anything
		}
	}

	/** Same as {@code list.removeAll(remove)} but in one pass. */
	public static <T> void removeAll(List<T> list, List<?> remove) {
		if (remove.isEmpty()) {
			return;
		}
		if (remove.size() < 4) {
			list.removeAll(remove); // tiny: not worth building a set
			return;
		}
		removeAll(list, new HashSet<Object>(remove));
	}
}
