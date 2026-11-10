package net.minecraft.tcpvp;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

/** Treasure Client: the practice bots of the running world (finding, removing, respawning). */
public final class TCBots {

	private static final List<TCBot> respawns = new ArrayList<>();

	private TCBots() {
	}

	public static List<TCBot> all(MinecraftServer server) {
		List<TCBot> out = new ArrayList<>();
		for (EntityPlayerMP p : server.getPlayerList().getPlayerList()) {
			if (p instanceof TCBot) {
				out.add((TCBot) p);
			}
		}
		return out;
	}

	public static TCBot find(MinecraftServer server, String name) {
		EntityPlayerMP p = server.getPlayerList().getPlayerByUsername(name);
		return p instanceof TCBot ? (TCBot) p : null;
	}

	static void queueRespawn(TCBot bot) {
		respawns.add(bot);
	}

	/** {@code base}, or base2, base3... whichever no one in the world is using. */
	public static String freeName(MinecraftServer server, String base) {
		if (server.getPlayerList().getPlayerByUsername(base) == null) {
			return base;
		}
		for (int i = 2; i < 1000; i++) {
			if (server.getPlayerList().getPlayerByUsername(base + i) == null) {
				return base + i;
			}
		}
		return base + server.getTickCounter();
	}

	/** Takes a bot out of the world, like a player leaving. */
	public static void remove(TCBot bot) {
		MinecraftServer server = bot.mcServer;
		bot.mountEntityAndWakeUp();
		server.getPlayerList().playerLoggedOut(bot);
		TCPractice.forgetKit(bot);
	}

	/** Called at the end of every server tick. */
	public static void tick(MinecraftServer server) {
		net.minecraft.item.ItemMinecart.tcPracticeStack = TCPractice.isPractice(server.worldServers[0]);
		TCPractice.refillAll(server);
		if (respawns.isEmpty()) {
			return;
		}
		List<TCBot> list = new ArrayList<>(respawns);
		respawns.clear();
		for (int i = 0, n = list.size(); i < n; i++) {
			TCBot old = list.get(i);
			if (server.getPlayerList().getPlayerList().indexOf(old) < 0) {
				continue; // already removed
			}
			WorldServer world = old.getServerWorld();
			if (TCHerobot.get(world, TCHerobot.LEAVE_ON_DEATH)) {
				remove(old);
				continue;
			}
			server.getPlayerList().playerLoggedOut(old);
			TCBot bot = TCBot.spawn(server, world, old.getName(), old.homeX, old.homeY, old.homeZ, old.homeYaw,
					old.homePitch, false);
			old.actions.copyTo(bot.actions);
			bot.kit = old.kit;
			bot.infiniteTotems = old.infiniteTotems;
			bot.duelShield = old.duelShield;
			if (TCPractice.replaced(old, bot)) {
				// a duel bot: its duel set it up again
			} else if (bot.kit != null) {
				TCPractice.giveKit(bot, bot.kit);
				if (bot.duelShield) {
					bot.setHeldItem(net.minecraft.util.EnumHand.OFF_HAND,
							new net.minecraft.item.ItemStack(net.minecraft.init.Items.SHIELD));
				}
			} else {
				bot.inventory.copyInventory(old.inventory); // keepInventory-style
			}
			if (bot.actions.lookAt != null) {
				bot.actions.lookAtPoint(bot.actions.lookAt.posX, bot.actions.lookAt.posY, bot.actions.lookAt.posZ);
			} else {
				bot.actions.look(old.homeYaw, old.homePitch);
			}
		}
	}

	/** A real player left the world or it closed: forget pending respawns. */
	public static void clear() {
		respawns.clear();
	}
}
