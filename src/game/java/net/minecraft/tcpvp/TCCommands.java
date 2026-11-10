package net.minecraft.tcpvp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

/**
 * Treasure Client: the Practice world commands (/kit, /arena, /spawn, /practice) and
 * the HeroBot-style bot commands (/playerspawn, /player, /herobot). In a Practice
 * world everyone may use them; in other worlds they need cheats.
 */
public final class TCCommands {

	/** Most bots at once, so a 2-core laptop stays smooth. */
	public static final int MAX_BOTS = 10;

	private TCCommands() {
	}

	public static void register(CommandHandler h) {
		h.registerCommand(new Kit());
		h.registerCommand(new Arena());
		h.registerCommand(new Spawn());
		h.registerCommand(new Practice());
		h.registerCommand(new PlayerSpawn());
		h.registerCommand(new PlayerCmd());
		h.registerCommand(new Herobot());
	}

	static void say(ICommandSender s, String msg) {
		s.addChatMessage(new TextComponentString(msg));
	}

	abstract static class Base extends CommandBase {
		public int getRequiredPermissionLevel() {
			return 2;
		}

		public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
			return TCPractice.isPractice(sender.getEntityWorld()) || super.checkPermission(server, sender);
		}
	}

	// ---------------------------------------------------------------- practice

	static final class Kit extends Base {
		public String getCommandName() {
			return "kit";
		}

		public String getCommandUsage(ICommandSender s) {
			return "/kit <" + String.join("|", TCPractice.KITS) + "> [player]";
		}

		public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
			if (args.length < 1 || !TCPractice.isKit(args[0])) {
				throw new WrongUsageException(getCommandUsage(sender));
			}
			EntityPlayerMP p = args.length > 1 ? getPlayer(server, sender, args[1]) : getCommandSenderAsPlayer(sender);
			TCPractice.giveKit(p, args[0]);
			if (p instanceof TCBot) {
				((TCBot) p).kit = args[0].toLowerCase();
			}
			say(sender, "§aGave the " + args[0].toLowerCase() + " kit to " + p.getName());
		}

		public List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args,
				@Nullable BlockPos pos) {
			if (args.length == 1) {
				return getListOfStringsMatchingLastWord(args, TCPractice.KITS);
			}
			return args.length == 2 ? getListOfStringsMatchingLastWord(args, server.getAllUsernames())
					: Collections.<String>emptyList();
		}
	}

	static final class Arena extends Base {
		public String getCommandName() {
			return "arena";
		}

		public String getCommandUsage(ICommandSender s) {
			return "/arena <duel|mace|cart|crystal|reset>";
		}

		public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
			if (args.length < 1) {
				throw new WrongUsageException(getCommandUsage(sender));
			}
			EntityPlayerMP p = getCommandSenderAsPlayer(sender);
			WorldServer w = p.getServerWorld();
			if (!TCPractice.isPractice(w)) {
				throw new CommandException("Arenas are only in Practice worlds");
			}
			if (args[0].equalsIgnoreCase("reset")) {
				TCArena.Arena a = TCArena.at(p.posX, p.posZ);
				if (a == null) {
					throw new CommandException("Stand in an arena to reset it");
				}
				TCPractice.resetArena(w, a);
				say(sender, "§aReset the " + a.title + " arena");
				return;
			}
			TCArena.Arena a = TCArena.byName(args[0]);
			if (a == null) {
				throw new WrongUsageException(getCommandUsage(sender));
			}
			TCPractice.resetArena(w, a);
			double[] st = TCArena.start(a, 0);
			TCPractice.teleport(p, st[0], TCArena.GROUND + 1, st[1], (float) st[2]);
			say(sender, "§aWelcome to the " + a.title + " arena §7(bots: /playerspawn <name>, then /player <name> ...)");
		}

		public List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args,
				@Nullable BlockPos pos) {
			return args.length == 1
					? getListOfStringsMatchingLastWord(args, "duel", "mace", "cart", "crystal", "reset")
					: Collections.<String>emptyList();
		}
	}

	static final class Spawn extends Base {
		public String getCommandName() {
			return "spawn";
		}

		public String getCommandUsage(ICommandSender s) {
			return "/spawn";
		}

		public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
			EntityPlayerMP p = getCommandSenderAsPlayer(sender);
			if (TCPractice.isPractice(p.world)) {
				TCPractice.toSpawn(p);
			} else {
				BlockPos sp = p.world.getSpawnPoint();
				TCPractice.teleport(p, sp.getX() + 0.5D, p.world.getTopSolidOrLiquidBlock(sp).getY(), sp.getZ() + 0.5D,
						p.rotationYaw);
			}
		}
	}

	static final class Practice extends Base {
		public String getCommandName() {
			return "practice";
		}

		public String getCommandUsage(ICommandSender s) {
			return "/practice";
		}

		public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
			TCPractice.help(sender);
		}
	}

	// ---------------------------------------------------------------- bots

	static final class PlayerSpawn extends Base {
		public String getCommandName() {
			return "playerspawn";
		}

		public String getCommandUsage(ICommandSender s) {
			return "/playerspawn <name> [at <x> <y> <z>] [facing <yaw> <pitch>]";
		}

		public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
			if (args.length < 1) {
				throw new WrongUsageException(getCommandUsage(sender));
			}
			String name = args[0];
			if (!name.matches("[A-Za-z0-9_]{1,16}")) {
				throw new CommandException("Bot names are 1-16 letters, numbers or _");
			}
			if (server.getPlayerList().getPlayerByUsername(name) != null) {
				throw new CommandException(name + " is already in the world");
			}
			if (TCBots.all(server).size() >= MAX_BOTS) {
				throw new CommandException("At most " + MAX_BOTS + " bots at once");
			}
			Entity at = sender.getCommandSenderEntity();
			WorldServer w = (WorldServer) sender.getEntityWorld();
			double x = at != null ? at.posX : sender.getPosition().getX() + 0.5D;
			double y = at != null ? at.posY : sender.getPosition().getY();
			double z = at != null ? at.posZ : sender.getPosition().getZ() + 0.5D;
			float yaw = at != null ? at.rotationYaw : 0.0F, pitch = 0.0F;
			for (int i = 1; i < args.length; i++) {
				if (args[i].equalsIgnoreCase("at") && i + 3 < args.length) {
					x = parseDouble(x, args[i + 1], true);
					y = parseDouble(y, args[i + 2], false);
					z = parseDouble(z, args[i + 3], true);
					i += 3;
				} else if (args[i].equalsIgnoreCase("facing") && i + 2 < args.length) {
					yaw = (float) parseDouble(yaw, args[i + 1], false);
					pitch = (float) parseDouble(pitch, args[i + 2], false);
					i += 2;
				} else {
					throw new WrongUsageException(getCommandUsage(sender));
				}
			}
			if (at instanceof EntityPlayer && w != at.world) {
				w = (WorldServer) at.world;
			}
			TCBot.spawn(server, w, name, x, y, z, yaw, pitch, true);
		}

		public List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args,
				@Nullable BlockPos pos) {
			if (args.length == 1) {
				return getListOfStringsMatchingLastWord(args, "Bot", "Steve", "Alex");
			}
			if (args.length == 2 || args.length == 5) {
				return getListOfStringsMatchingLastWord(args, "at", "facing");
			}
			if (args.length >= 3 && args.length <= 5 && args[1].equalsIgnoreCase("at")) {
				return getTabCompletionCoordinate(args, 2, pos);
			}
			return Collections.<String>emptyList();
		}
	}

	static final class PlayerCmd extends Base {
		private static final String[] ACTIONS = { "attack", "use", "jump", "swing", "drop", "dropStack", "swapHands",
				"shield", "kit", "move", "sneak", "unsneak", "sprint", "unsprint", "look", "hotbar", "stop", "heal",
				"kill", "disconnect", "home" };

		public String getCommandName() {
			return "player";
		}

		public String getCommandUsage(ICommandSender s) {
			return "/player <bot|list> <attack|use|jump|shield|kit|move|look|sneak|sprint|hotbar|stop|heal|kill|disconnect|home> ...";
		}

		public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
			if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
				List<TCBot> bots = TCBots.all(server);
				if (bots.isEmpty()) {
					say(sender, "§7No bots. Make one with /playerspawn <name>");
				}
				for (TCBot b : bots) {
					say(sender, "§d" + b.getName() + "§7: " + b.actions.describe()
							+ (b.kit != null ? ", kit " + b.kit : ""));
				}
				return;
			}
			if (args.length < 2) {
				throw new WrongUsageException(getCommandUsage(sender));
			}
			TCBot bot = TCBots.find(server, args[0]);
			if (bot == null) {
				throw new CommandException(args[0] + " isn't a bot (make one with /playerspawn " + args[0] + ")");
			}
			TCBotActions a = bot.actions;
			String act = args[1];
			int click = TCBotActions.clickIndex(act);
			if (click >= 0) {
				int mode = -1;
				if (args.length > 2) {
					if (args[2].equalsIgnoreCase("once")) {
						mode = -1;
					} else if (args[2].equalsIgnoreCase("continuous")) {
						mode = 1;
					} else if (args[2].equalsIgnoreCase("interval") && args.length > 3) {
						mode = parseInt(args[3], 1, 72000);
					} else if (args[2].equalsIgnoreCase("stop") || args[2].equalsIgnoreCase("off")) {
						mode = 0;
					} else {
						throw new WrongUsageException("/player <bot> " + act + " [once|continuous|interval <ticks>|stop]");
					}
				}
				a.setClick(click, mode);
				return;
			}
			switch (act.toLowerCase()) {
			case "shield": {
				boolean on = args.length > 2 ? args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("true")
						: !a.shield;
				a.shield = on;
				if (!on && bot.isHandActive()) {
					bot.resetActiveHand();
				}
				if (on && bot.getHeldItemOffhand().func_190926_b() && !(bot.getHeldItemMainhand()
						.getItem() instanceof net.minecraft.item.ItemShield)) {
					bot.setHeldItem(net.minecraft.util.EnumHand.OFF_HAND,
							new net.minecraft.item.ItemStack(net.minecraft.init.Items.SHIELD));
				}
				say(sender, "§d" + bot.getName() + "§7 shield " + (on ? "up" : "down"));
				return;
			}
			case "kit":
				if (args.length < 3 || !TCPractice.isKit(args[2])) {
					throw new WrongUsageException("/player <bot> kit <" + String.join("|", TCPractice.KITS) + ">");
				}
				TCPractice.giveKit(bot, args[2]);
				bot.kit = args[2].toLowerCase();
				return;
			case "move":
				a.forward = a.strafe = 0.0F;
				if (args.length > 2) {
					String d = args[2].toLowerCase();
					if (d.equals("forward")) a.forward = 1.0F;
					else if (d.equals("backward")) a.forward = -1.0F;
					else if (d.equals("left")) a.strafe = 1.0F;
					else if (d.equals("right")) a.strafe = -1.0F;
					else if (!d.equals("stop")) throw new WrongUsageException("/player <bot> move [forward|backward|left|right|stop]");
				}
				return;
			case "sneak":
				a.sneak = true;
				return;
			case "unsneak":
				a.sneak = false;
				return;
			case "sprint":
				a.sprint = true;
				return;
			case "unsprint":
				a.sprint = false;
				return;
			case "look":
				look(sender, bot, args);
				return;
			case "hotbar":
				if (args.length < 3) {
					throw new WrongUsageException("/player <bot> hotbar <1-9>");
				}
				bot.inventory.currentItem = parseInt(args[2], 1, 9) - 1;
				bot.resetActiveHand();
				return;
			case "stop":
				a.stopAll();
				return;
			case "heal":
				TCPractice.heal(bot);
				return;
			case "kill":
				bot.onKillCommand();
				return;
			case "disconnect":
				TCBots.remove(bot);
				return;
			case "home":
				bot.homeX = bot.posX;
				bot.homeY = bot.posY;
				bot.homeZ = bot.posZ;
				bot.homeYaw = bot.rotationYaw;
				bot.homePitch = bot.rotationPitch;
				say(sender, "§d" + bot.getName() + "§7 will respawn here");
				return;
			default:
				throw new WrongUsageException(getCommandUsage(sender));
			}
		}

		private static void look(ICommandSender sender, TCBot bot, String[] args) throws CommandException {
			TCBotActions a = bot.actions;
			if (args.length < 3) {
				throw new WrongUsageException("/player <bot> look <north|south|east|west|up|down|me|at <x y z>|<yaw> <pitch>>");
			}
			String d = args[2].toLowerCase();
			if (!d.equals("me")) {
				a.lookAt = null;
			}
			switch (d) {
			case "north": a.look(180.0F, 0.0F); return;
			case "south": a.look(0.0F, 0.0F); return;
			case "east": a.look(-90.0F, 0.0F); return;
			case "west": a.look(90.0F, 0.0F); return;
			case "up": a.look(bot.rotationYaw, -90.0F); return;
			case "down": a.look(bot.rotationYaw, 90.0F); return;
			case "me": {
				Entity e = sender.getCommandSenderEntity();
				if (e == null) {
					throw new CommandException("Only a player can be looked at");
				}
				a.lookAt = e;
				return;
			}
			case "at":
				if (args.length < 6) {
					throw new WrongUsageException("/player <bot> look at <x> <y> <z>");
				}
				a.lookAtPoint(parseDouble(bot.posX, args[3], true), parseDouble(bot.posY, args[4], false),
						parseDouble(bot.posZ, args[5], true));
				return;
			default:
				if (args.length < 4) {
					throw new WrongUsageException("/player <bot> look <yaw> <pitch>");
				}
				a.look((float) parseDouble(bot.rotationYaw, args[2], false),
						(float) parseDouble(bot.rotationPitch, args[3], false));
			}
		}

		public List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args,
				@Nullable BlockPos pos) {
			if (args.length == 1) {
				List<String> names = new ArrayList<>();
				names.add("list");
				for (TCBot b : TCBots.all(server)) {
					names.add(b.getName());
				}
				return getListOfStringsMatchingLastWord(args, names);
			}
			if (args.length == 2) {
				return getListOfStringsMatchingLastWord(args, ACTIONS);
			}
			if (args.length == 3) {
				String act = args[1];
				if (TCBotActions.clickIndex(act) >= 0) {
					return getListOfStringsMatchingLastWord(args, "once", "continuous", "interval", "stop");
				}
				switch (act.toLowerCase()) {
				case "shield": return getListOfStringsMatchingLastWord(args, "on", "off");
				case "kit": return getListOfStringsMatchingLastWord(args, TCPractice.KITS);
				case "move": return getListOfStringsMatchingLastWord(args, "forward", "backward", "left", "right", "stop");
				case "look": return getListOfStringsMatchingLastWord(args, "north", "south", "east", "west", "up", "down", "me", "at");
				case "hotbar": return getListOfStringsMatchingLastWord(args, "1", "2", "3", "4", "5", "6", "7", "8", "9");
				}
			}
			return Collections.<String>emptyList();
		}
	}

	static final class Herobot extends Base {
		public String getCommandName() {
			return "herobot";
		}

		public String getCommandUsage(ICommandSender s) {
			return "/herobot [setting] [true|false]";
		}

		public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
			net.minecraft.world.World w = sender.getEntityWorld();
			if (args.length == 0) {
				say(sender, "§6Bot and combat settings §7(click a name or use /herobot <name> true|false)");
				for (int i = 0; i < TCHerobot.SETTINGS.length; i++) {
					String n = TCHerobot.SETTINGS[i];
					say(sender, (TCHerobot.get(w, n) ? "§a" : "§c") + n + " = " + TCHerobot.get(w, n) + "§7 - "
							+ TCHerobot.INFO[i]);
				}
				return;
			}
			String n = TCHerobot.byName(args[0]);
			if (n == null) {
				throw new CommandException("No setting " + args[0]);
			}
			if (args.length > 1) {
				TCHerobot.set(w, n, parseBoolean(args[1]));
			}
			say(sender, "§e" + n + " = " + TCHerobot.get(w, n));
		}

		public List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args,
				@Nullable BlockPos pos) {
			if (args.length == 1) {
				return getListOfStringsMatchingLastWord(args, TCHerobot.SETTINGS);
			}
			return args.length == 2 ? getListOfStringsMatchingLastWord(args, "true", "false")
					: Collections.<String>emptyList();
		}
	}
}
