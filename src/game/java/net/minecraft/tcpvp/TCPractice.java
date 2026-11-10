package net.minecraft.tcpvp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.lax1dude.eaglercraft.EaglercraftUUID;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ViaItems;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionType;
import net.minecraft.potion.PotionUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.WorldInfo;

/**
 * Treasure Client: the Practice world (a world type on the Create World screen).
 *
 * <p>A plain superflat with a ring of kit stands at spawn. Right-click a stand to pick
 * whether the bot blocks with a shield, then you and a bot with that kit are sent far
 * out to duel; the bot has endless totems. Kit items never run out while you play.
 * Practice bot idea and command design from HeroBot by HerobaneNair (CC BY-NC-SA 4.0);
 * this is a new implementation for 1.12, not their code.
 */
public final class TCPractice {

	public static final String[] KITS = { "mace", "cart", "sword", "netherite", "axe", "crystal" };
	public static final String[] KIT_TITLES = { "Mace", "Cart", "Sword", "NethPot", "Axe", "Crystal" };

	/** Set by the game client: opens the "duel this kit" screen (in the worker it stays null). */
	public static Consumer<String> clientKitScreen;

	private TCPractice() {
	}

	public static boolean isPractice(World world) {
		return world != null && world.getWorldInfo().getTerrainType() == WorldType.PRACTICE;
	}

	// ---------------------------------------------------------------- world setup

	/** First start of a new Practice world: spawn in the middle, noon forever, no mobs or weather. */
	public static void setupNewWorld(WorldInfo info, GameRules rules) {
		info.setSpawn(new BlockPos(0, TCArena.GROUND + 1, 0));
		info.setMapFeaturesEnabled(false);
		info.setRaining(false);
		info.setThundering(false);
		info.setCleanWeatherTime(1000000000);
		info.setWorldTime(6000L);
		info.setAllowCommands(true);
		rules.setOrCreateGameRule("doDaylightCycle", "false");
		rules.setOrCreateGameRule("doWeatherCycle", "false");
		rules.setOrCreateGameRule("doMobSpawning", "false");
		rules.setOrCreateGameRule("doFireTick", "false");
		rules.setOrCreateGameRule("keepInventory", "true");
		rules.setOrCreateGameRule("spawnRadius", "0");
		rules.setOrCreateGameRule("announceAdvancements", "false");
		rules.setOrCreateGameRule("commandBlockOutput", "false");
	}

	/** A player (not a bot) joined a Practice world. */
	public static void welcome(EntityPlayerMP p) {
		p.addChatMessage(text("Treasure Practice", TextFormatting.GOLD, true));
		p.addChatMessage(text("Right-click a kit stand to duel a bot with that kit.", TextFormatting.GRAY, false));
		ITextComponent bots = text("", TextFormatting.GRAY, false);
		bots.appendSibling(clickable("[Spawn a bot]", "/playerspawn", TextFormatting.LIGHT_PURPLE));
		bots.appendSibling(new TextComponentString(" "));
		bots.appendSibling(clickable("[Spawn]", "/spawn", TextFormatting.YELLOW));
		bots.appendSibling(new TextComponentString(" "));
		bots.appendSibling(clickable("[Help]", "/practice", TextFormatting.WHITE));
		p.addChatMessage(bots);
	}

	public static void help(net.minecraft.command.ICommandSender s) {
		String[] lines = { "§6Treasure Practice commands",
				"§b/duel <" + String.join("|", KITS) + "> [shield|noshield]§7 (or right-click a kit stand)",
				"§b/kit <kit> [player]§7 · §b/spawn",
				"§d/playerspawn [name] [at <x y z>] [facing <yaw pitch>]",
				"§d/player <bot> kit <name>§7 · §dshield [on|off]§7 · §dattack|use|jump|swing [once|continuous|interval <ticks>]",
				"§d/player <bot> move [forward|backward|left|right]§7 · §dsneak|unsneak|sprint|unsprint",
				"§d/player <bot> look <north|south|east|west|up|down|at <x y z>|<yaw> <pitch>>§7 · §dlook me",
				"§d/player <bot> hotbar <1-9>§7 · §dstop|kill|disconnect|heal|home§7 · §d/player list",
				"§e/herobot§7 bot and combat settings" };
		for (String l : lines) {
			s.addChatMessage(new TextComponentString(l));
		}
	}

	static ITextComponent text(String s, TextFormatting color, boolean bold) {
		TextComponentString t = new TextComponentString(s);
		t.getStyle().setColor(color);
		if (bold) {
			t.getStyle().setBold(Boolean.valueOf(true));
		}
		return t;
	}

	static ITextComponent clickable(String s, String command, TextFormatting color) {
		TextComponentString t = new TextComponentString(s);
		t.getStyle().setColor(color);
		t.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
		t.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(command)));
		return t;
	}

	// ---------------------------------------------------------------- kit stands at spawn

	private static int[] standPos(int i) {
		double a = Math.toRadians(-90.0D + i * (360.0D / KITS.length));
		return new int[] { (int) Math.round(Math.cos(a) * 5.0D), (int) Math.round(Math.sin(a) * 5.0D) };
	}

	/** The yaw of something at x,z looking at spawn. */
	private static float yawToCentre(double x, double z) {
		return (float) Math.toDegrees(Math.atan2(x, -z));
	}

	/** Kit stands of the chunk at cx,cz (called when it is first decorated). */
	public static void populate(World world, int cx, int cz) {
		for (int i = 0; i < KITS.length; i++) {
			int[] q = standPos(i);
			if (q[0] >> 4 == cx && q[1] >> 4 == cz) {
				placeStand(world, q[0], q[1], i);
			}
		}
	}

	private static void placeStand(World world, int x, int z, int kit) {
		EntityArmorStand stand = new EntityArmorStand(world, x + 0.5D, TCArena.GROUND + 1, z + 0.5D);
		stand.rotationYaw = yawToCentre(x + 0.5D, z + 0.5D);
		stand.prevRotationYaw = stand.rotationYaw;
		NBTTagCompound tag = new NBTTagCompound();
		stand.writeToNBT(tag);
		tag.setBoolean("Invulnerable", true);
		tag.setBoolean("NoGravity", true);
		tag.setBoolean("ShowArms", true);
		tag.setBoolean("NoBasePlate", true);
		tag.setInteger("DisabledSlots", 4144959);
		tag.setBoolean("PersistenceRequired", true);
		stand.readFromNBT(tag);
		ItemStack[] show = display(KITS[kit]);
		stand.setItemStackToSlot(EntityEquipmentSlot.HEAD, show[0]);
		stand.setItemStackToSlot(EntityEquipmentSlot.CHEST, show[1]);
		stand.setItemStackToSlot(EntityEquipmentSlot.LEGS, show[2]);
		stand.setItemStackToSlot(EntityEquipmentSlot.FEET, show[3]);
		stand.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, show[4]);
		stand.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, show[5]);
		stand.setCustomNameTag(standName(kit));
		stand.setAlwaysRenderNameTag(true);
		world.spawnEntityInWorld(stand);
	}

	private static String standName(int kit) {
		return "§6" + KIT_TITLES[kit] + " Kit";
	}

	/** The kit a Practice kit stand shows (by its name), or null if it isn't one. */
	public static String kitOfStand(EntityArmorStand stand) {
		if (!isPractice(stand.world) || !stand.hasCustomName()) {
			return null;
		}
		String n = stand.getCustomNameTag();
		for (int i = 0; i < KITS.length; i++) {
			if (n.equals(standName(i))) {
				return KITS[i];
			}
		}
		if (n.equals("§6Netherite Kit")) { // stands from the first test build
			return "netherite";
		}
		return null;
	}

	/** Right-click on a kit stand: on the client opens the duel screen; the server does nothing. */
	public static boolean onStandClicked(EntityArmorStand stand, EntityPlayer player) {
		String kit = kitOfStand(stand);
		if (kit == null) {
			return false;
		}
		if (stand.world.isRemote && clientKitScreen != null) {
			clientKitScreen.accept(kit);
		}
		return true;
	}

	// ---------------------------------------------------------------- kits

	private static ItemStack st(Item it, int n) {
		return it == null ? ItemStack.field_190927_a : new ItemStack(it, n);
	}

	private static ItemStack block(net.minecraft.block.Block b, int n) {
		return st(Item.getItemFromBlock(b), n);
	}

	private static ItemStack via(String name, int n) {
		return st(ViaItems.getByName(name), n);
	}

	private static ItemStack ench(ItemStack s, Object... e) {
		if (s.func_190926_b()) {
			return s;
		}
		for (int i = 0; i + 1 < e.length; i += 2) {
			s.addEnchantment((Enchantment) e[i], ((Integer) e[i + 1]).intValue());
		}
		return s;
	}

	private static ItemStack splash(PotionType type) {
		return PotionUtils.addPotionToItemStack(new ItemStack(Items.SPLASH_POTION, 1), type);
	}

	private static final Object[] P4U3 = { Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3 };
	private static final Object[] P4U3M = { Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3,
			Enchantments.MENDING, 1 };

	private static ItemStack[] netherite(Object[] e) {
		return new ItemStack[] { ench(via("netherite_helmet", 1), e), ench(via("netherite_chestplate", 1), e),
				ench(via("netherite_leggings", 1), e), ench(via("netherite_boots", 1), e) };
	}

	private static ItemStack[] diamond(Object[] e) {
		return new ItemStack[] { ench(st(Items.DIAMOND_HELMET, 1), e), ench(st(Items.DIAMOND_CHESTPLATE, 1), e),
				ench(st(Items.DIAMOND_LEGGINGS, 1), e), ench(st(Items.DIAMOND_BOOTS, 1), e) };
	}

	/** What a kit stand shows: helmet, chest, legs, boots, main hand, off hand. */
	private static ItemStack[] display(String kit) {
		Kit k = build(kit);
		ItemStack main = k.hotbar[0];
		if (kit.equals("mace")) {
			main = k.hotbar[6]; // the mace
		}
		return new ItemStack[] { k.armor[0], k.armor[1], k.armor[2], k.armor[3], main, k.offhand };
	}

	private static final class Kit {
		ItemStack[] armor;
		ItemStack[] hotbar = new ItemStack[9];
		ItemStack[] inv = new ItemStack[27];
		ItemStack offhand = ItemStack.field_190927_a;

		Kit() {
			for (int i = 0; i < 9; i++) hotbar[i] = ItemStack.field_190927_a;
			for (int i = 0; i < 27; i++) inv[i] = ItemStack.field_190927_a;
		}
	}

	/**
	 * The kits, laid out like the usual tier-test kits (slots: hotbar 0-8, inventory
	 * rows 0-26). 1.21-only enchantments (Breach, Density, Wind Burst) and items 1.12
	 * can't use (respawn anchors) are left out.
	 */
	private static Kit build(String kit) {
		Kit k = new Kit();
		ItemStack[] h = k.hotbar, v = k.inv;
		if (kit.equals("mace")) {
			k.armor = netherite(P4U3);
			ench(k.armor[3], Enchantments.FEATHER_FALLING, 4);
			h[0] = ench(via("netherite_sword", 1), Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3);
			h[1] = ench(via("netherite_axe", 1), Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3);
			h[2] = st(Items.ENDER_PEARL, 16);
			h[3] = st(Items.GOLDEN_APPLE, 64);
			h[4] = via("wind_charge", 64);
			h[5] = st(Items.ELYTRA, 1);
			h[6] = ench(via("mace", 1), Enchantments.UNBREAKING, 3);
			h[7] = ench(via("mace", 1), Enchantments.UNBREAKING, 3);
			h[8] = ench(st(Items.SHIELD, 1), Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1);
			v[0] = via("wind_charge", 64);
			for (int i = 1; i < 8; i++) v[i] = splash(PotionTypes.STRONG_STRENGTH);
			v[8] = st(Items.GOLDEN_APPLE, 64);
			v[9] = st(Items.ENDER_PEARL, 16);
			for (int i = 10; i < 17; i++) v[i] = splash(PotionTypes.STRONG_SWIFTNESS);
			v[18] = st(Items.ENDER_PEARL, 16);
			for (int i = 19; i < 23; i++) v[i] = splash(PotionTypes.STRONG_STRENGTH);
			for (int i = 23; i < 26; i++) v[i] = splash(PotionTypes.STRONG_SWIFTNESS);
			v[26] = st(Items.TOTEM_OF_UNDYING, 1);
			k.offhand = st(Items.TOTEM_OF_UNDYING, 1);
		} else if (kit.equals("cart")) {
			k.armor = diamond(P4U3);
			k.armor[2] = ench(st(Items.DIAMOND_LEGGINGS, 1), Enchantments.BLAST_PROTECTION, 4, Enchantments.UNBREAKING, 3);
			ench(k.armor[3], Enchantments.FEATHER_FALLING, 4);
			h[0] = ench(st(Items.DIAMOND_SWORD, 1), Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3);
			h[1] = ench(st(Items.DIAMOND_AXE, 1), Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3);
			h[2] = ench(st(Items.BOW, 1), Enchantments.POWER, 5, Enchantments.FLAME, 1, Enchantments.UNBREAKING, 3);
			h[3] = block(Blocks.RAIL, 64);
			h[4] = st(Items.TNT_MINECART, 1);
			h[5] = st(Items.ENDER_PEARL, 16);
			h[6] = st(Items.GOLDEN_APPLE, 64);
			h[7] = ench(st(Items.DIAMOND_PICKAXE, 1), Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3);
			h[8] = st(Items.COOKED_BEEF, 64);
			v[0] = st(Items.ARROW, 64);
			v[1] = block(Blocks.RAIL, 64);
			v[2] = block(Blocks.LOG, 64);
			v[3] = ench(st(Items.FLINT_AND_STEEL, 1), Enchantments.UNBREAKING, 3);
			for (int i = 9; i < 18; i++) v[i] = st(Items.TNT_MINECART, 1);
			for (int i = 18; i < 22; i++) v[i] = st(Items.TOTEM_OF_UNDYING, 1);
			v[26] = ench(st(Items.SHIELD, 1), Enchantments.UNBREAKING, 3);
			k.offhand = st(Items.TOTEM_OF_UNDYING, 1);
		} else if (kit.equals("sword")) {
			k.armor = diamond(P4U3);
			k.armor[2] = ench(st(Items.DIAMOND_LEGGINGS, 1), Enchantments.PROTECTION, 3, Enchantments.UNBREAKING, 3);
			k.armor[3] = ench(st(Items.DIAMOND_BOOTS, 1), Enchantments.PROTECTION, 3, Enchantments.UNBREAKING, 3);
			h[0] = ench(st(Items.DIAMOND_SWORD, 1), Enchantments.UNBREAKING, 3);
		} else if (kit.equals("netherite")) {
			k.armor = netherite(P4U3M);
			h[0] = ench(via("netherite_sword", 1), Enchantments.SHARPNESS, 5, Enchantments.SWEEPING_EDGE, 3,
					Enchantments.UNBREAKING, 3);
			for (int i = 1; i < 6; i++) h[i] = splash(PotionTypes.STRONG_HEALING);
			h[6] = splash(PotionTypes.STRONG_STRENGTH);
			h[7] = splash(PotionTypes.STRONG_SWIFTNESS);
			h[8] = st(Items.TOTEM_OF_UNDYING, 1);
			for (int i = 0; i < 17; i++) v[i] = splash(PotionTypes.STRONG_HEALING);
			for (int i = 17; i < 21; i++) v[i] = splash(PotionTypes.STRONG_STRENGTH);
			for (int i = 21; i < 25; i++) v[i] = splash(PotionTypes.STRONG_SWIFTNESS);
			v[25] = st(Items.EXPERIENCE_BOTTLE, 64);
			v[26] = st(Items.GOLDEN_APPLE, 64);
			k.offhand = st(Items.TOTEM_OF_UNDYING, 1);
		} else if (kit.equals("axe")) {
			k.armor = diamond(new Object[0]);
			h[0] = st(Items.DIAMOND_SWORD, 1);
			h[1] = st(Items.DIAMOND_AXE, 1);
			h[2] = st(Items.BOW, 1);
			h[3] = via("crossbow", 1);
			h[8] = st(Items.ARROW, 6);
			k.offhand = st(Items.SHIELD, 1);
		} else { // crystal
			k.armor = netherite(P4U3M);
			ench(k.armor[0], Enchantments.RESPIRATION, 3, Enchantments.AQUA_AFFINITY, 1);
			k.armor[2] = ench(via("netherite_leggings", 1), Enchantments.BLAST_PROTECTION, 4, Enchantments.UNBREAKING, 3,
					Enchantments.MENDING, 1);
			ench(k.armor[3], Enchantments.FEATHER_FALLING, 4, Enchantments.DEPTH_STRIDER, 3);
			h[0] = ench(via("netherite_sword", 1), Enchantments.SHARPNESS, 5, Enchantments.SWEEPING_EDGE, 3,
					Enchantments.UNBREAKING, 3, Enchantments.KNOCKBACK, 1, Enchantments.MENDING, 1);
			h[1] = block(Blocks.OBSIDIAN, 64);
			h[2] = st(Items.END_CRYSTAL, 64);
			h[3] = st(Items.ENDER_PEARL, 16);
			h[4] = st(Items.GOLDEN_APPLE, 64);
			h[5] = st(Items.EXPERIENCE_BOTTLE, 64);
			h[6] = ench(via("netherite_pickaxe", 1), Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3);
			h[7] = st(Items.TOTEM_OF_UNDYING, 1);
			h[8] = ench(st(Items.SHIELD, 1), Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1);
			for (int i = 0; i < 5; i++) v[i] = st(Items.TOTEM_OF_UNDYING, 1);
			v[5] = ench(via("netherite_axe", 1), Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3);
			v[6] = splash(PotionTypes.STRONG_STRENGTH);
			v[7] = splash(PotionTypes.STRONG_SWIFTNESS);
			v[8] = st(Items.EXPERIENCE_BOTTLE, 64);
			for (int i = 9; i < 14; i++) v[i] = st(Items.TOTEM_OF_UNDYING, 1);
			v[14] = st(Items.ENDER_PEARL, 16);
			v[15] = st(Items.ELYTRA, 1);
			v[16] = st(Items.FIREWORKS, 64);
			for (int i = 18; i < 22; i++) v[i] = st(Items.TOTEM_OF_UNDYING, 1);
			v[25] = st(Items.END_CRYSTAL, 64);
			v[26] = block(Blocks.OBSIDIAN, 64);
			k.offhand = st(Items.TOTEM_OF_UNDYING, 1);
		}
		return k;
	}

	public static boolean isKit(String name) {
		for (String k : KITS) {
			if (k.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	/** Clears the player, heals them, gives the kit and keeps it topped up from now on. */
	public static void giveKit(EntityPlayer p, String kit) {
		Kit k = build(kit.toLowerCase());
		p.inventory.clear();
		for (int i = 0; i < 9; i++) p.inventory.setInventorySlotContents(i, k.hotbar[i].copy());
		for (int i = 0; i < 27; i++) p.inventory.setInventorySlotContents(9 + i, k.inv[i].copy());
		p.setItemStackToSlot(EntityEquipmentSlot.HEAD, k.armor[0].copy());
		p.setItemStackToSlot(EntityEquipmentSlot.CHEST, k.armor[1].copy());
		p.setItemStackToSlot(EntityEquipmentSlot.LEGS, k.armor[2].copy());
		p.setItemStackToSlot(EntityEquipmentSlot.FEET, k.armor[3].copy());
		p.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, k.offhand.copy());
		p.inventory.currentItem = 0;
		heal(p);
		remember(p);
		if (p instanceof EntityPlayerMP) {
			EntityPlayerMP mp = (EntityPlayerMP) p;
			mp.inventoryContainer.detectAndSendChanges();
			if (mp.connection != null) {
				mp.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(0));
			}
		}
	}

	public static void heal(EntityLivingBase p) {
		p.clearActivePotions();
		p.extinguish();
		p.setHealth(p.getMaxHealth());
		p.fallDistance = 0.0F;
		if (p instanceof EntityPlayer) {
			((EntityPlayer) p).getFoodStats().setFoodLevel(20);
			((EntityPlayer) p).getFoodStats().setFoodSaturationLevel(5.0F);
			((EntityPlayer) p).tcImpulseY = Double.NaN;
		}
	}

	// ---------------------------------------------------------------- endless kit items

	/** Each player's kit as given (all inventory slots), to top up what they use. */
	private static final Map<EaglercraftUUID, ItemStack[]> KIT_OF = new HashMap<>();
	private static MinecraftServer kitServer;
	private static int refillTick;

	private static void remember(EntityPlayer p) {
		MinecraftServer server = p.getServer();
		if (server != kitServer) {
			KIT_OF.clear();
			kitServer = server;
		}
		InventoryPlayer inv = p.inventory;
		ItemStack[] a = new ItemStack[inv.getSizeInventory()];
		for (int i = 0; i < a.length; i++) {
			a[i] = inv.getStackInSlot(i).copy();
		}
		KIT_OF.put(p.getUniqueID(), a);
	}

	/** Same item (damage aside), same enchantments/potion. */
	private static boolean same(ItemStack a, ItemStack b) {
		return !a.func_190926_b() && !b.func_190926_b() && ItemStack.areItemsEqualIgnoreDurability(a, b)
				&& ItemStack.areItemStackTagsEqual(a, b);
	}

	/** Every server tick: puts back kit items that got used up, and repairs worn gear. */
	public static void refillAll(MinecraftServer server) {
		if (server != kitServer || KIT_OF.isEmpty()) {
			return;
		}
		boolean repair = (++refillTick % 10) == 0;
		List<EntityPlayerMP> players = server.getPlayerList().getPlayerList();
		for (int i = 0, n = players.size(); i < n; i++) {
			EntityPlayerMP p = players.get(i);
			if (p.getHealth() <= 0.0F || p.isSpectator() || !isPractice(p.world)) {
				continue;
			}
			ItemStack[] kit = KIT_OF.get(p.getUniqueID());
			if (kit != null) {
				refill(p, kit, repair);
			}
		}
	}

	private static void refill(EntityPlayerMP p, ItemStack[] kit, boolean repair) {
		InventoryPlayer inv = p.inventory;
		int size = Math.min(kit.length, inv.getSizeInventory());
		if (repair) {
			for (int i = 0; i < size; i++) {
				ItemStack s = inv.getStackInSlot(i);
				if (!s.func_190926_b() && s.isItemStackDamageable() && s.getItemDamage() > 0) {
					s.setItemDamage(0);
				}
			}
		}
		ItemStack cursor = inv.getItemStack();
		for (int i = 0; i < size; i++) {
			ItemStack want = kit[i];
			if (want.func_190926_b()) {
				continue;
			}
			boolean seen = false;
			for (int j = 0; j < i; j++) {
				if (same(kit[j], want)) {
					seen = true;
					break;
				}
			}
			if (seen) {
				continue; // counted with its first slot
			}
			int need = 0;
			for (int j = i; j < size; j++) {
				if (same(kit[j], want)) {
					need += kit[j].func_190916_E();
				}
			}
			int have = same(cursor, want) ? cursor.func_190916_E() : 0;
			for (int j = 0; j < inv.getSizeInventory(); j++) {
				ItemStack s = inv.getStackInSlot(j);
				if (same(s, want)) {
					have += s.func_190916_E();
				}
			}
			int missing = need - have;
			// put it back in its own kit slots first, then any free slot
			for (int j = i; j < size && missing > 0; j++) {
				if (!same(kit[j], want)) {
					continue;
				}
				ItemStack s = inv.getStackInSlot(j);
				if (s.func_190926_b()) {
					ItemStack add = kit[j].copy();
					int c = Math.min(missing, add.getMaxStackSize());
					add.func_190920_e(c);
					inv.setInventorySlotContents(j, add);
					missing -= c;
				} else if (same(s, want) && s.func_190916_E() < s.getMaxStackSize()) {
					int c = Math.min(missing, s.getMaxStackSize() - s.func_190916_E());
					s.func_190917_f(c);
					missing -= c;
				}
			}
			for (int j = 0; j < 36 && missing > 0; j++) {
				if (inv.getStackInSlot(j).func_190926_b()) {
					ItemStack add = want.copy();
					int c = Math.min(missing, add.getMaxStackSize());
					add.func_190920_e(c);
					inv.setInventorySlotContents(j, add);
					missing -= c;
				}
			}
		}
	}

	// ---------------------------------------------------------------- teleports and duels

	public static void teleport(EntityPlayer p, double x, double y, double z, float yaw) {
		p.dismountRidingEntity();
		p.fallDistance = 0.0F;
		p.tcImpulseY = Double.NaN;
		if (p instanceof EntityPlayerMP && !(p instanceof TCBot) && ((EntityPlayerMP) p).connection != null) {
			((EntityPlayerMP) p).connection.setPlayerLocation(x, y, z, yaw, 0.0F);
		} else {
			p.setLocationAndAngles(x, y, z, yaw, 0.0F);
		}
		p.motionX = p.motionY = p.motionZ = 0.0D;
	}

	public static void toSpawn(EntityPlayer p) {
		teleport(p, 0.5D, TCArena.GROUND + 1, 0.5D, 180.0F);
	}

	/** Each player's current duel bot. */
	private static final Map<EaglercraftUUID, TCBot> DUEL_BOT = new HashMap<>();

	/**
	 * Sends the player far out onto the flat, gives them and a new bot the kit, and puts
	 * the bot 6 blocks in front facing them. The bot has endless totems and, if asked,
	 * keeps a shield raised.
	 */
	public static void startDuel(EntityPlayerMP p, String kit, boolean shield) {
		MinecraftServer server = p.getServer();
		WorldServer w = p.getServerWorld();
		TCBot old = DUEL_BOT.remove(p.getUniqueID());
		if (old != null && server.getPlayerList().getPlayerList().contains(old)) {
			TCBots.remove(old);
		}
		double ang = p.world.rand.nextDouble() * Math.PI * 2.0D;
		double dist = 4000.0D + p.world.rand.nextInt(6000);
		int x = (int) (Math.cos(ang) * dist), z = (int) (Math.sin(ang) * dist);
		double y = TCArena.GROUND + 1;
		giveKit(p, kit);
		teleport(p, x + 0.5D, y, z + 0.5D, 0.0F);
		String name = TCBots.freeName(server, "Bot");
		TCBot bot = TCBot.spawn(server, w, name, x + 0.5D, y, z + 6.5D, 180.0F, 0.0F, false);
		bot.infiniteTotems = true;
		bot.kit = kit;
		giveKit(bot, kit);
		if (shield) {
			bot.setHeldItem(EnumHand.OFF_HAND, ench(new ItemStack(Items.SHIELD), Enchantments.UNBREAKING, 3));
			remember(bot);
			bot.actions.shield = true;
		}
		bot.actions.lookAt = p;
		bot.duelShield = shield;
		DUEL_BOT.put(p.getUniqueID(), bot);
		p.addChatMessage(text(KIT_TITLES[indexOf(kit)] + " duel vs " + name
				+ (shield ? " (blocks with a shield)" : "") + ". Your items never run out.", TextFormatting.GOLD, false));
		ITextComponent opts = text("", TextFormatting.GRAY, false);
		opts.appendSibling(clickable("[Rematch]", "/duel " + kit + (shield ? " shield" : " noshield"),
				TextFormatting.GREEN));
		opts.appendSibling(new TextComponentString(" "));
		opts.appendSibling(clickable("[Bot shield on/off]", "/player " + name + " shield", TextFormatting.AQUA));
		opts.appendSibling(new TextComponentString(" "));
		opts.appendSibling(clickable("[Back to spawn]", "/spawn", TextFormatting.YELLOW));
		p.addChatMessage(opts);
	}

	/** Leaving a duel (back to spawn): its bot goes away. */
	public static void endDuel(EntityPlayerMP p) {
		TCBot old = DUEL_BOT.remove(p.getUniqueID());
		if (old != null && p.getServer().getPlayerList().getPlayerList().contains(old)) {
			TCBots.remove(old);
		}
	}

	/** A duel bot respawned as a new entity. */
	static void replaced(TCBot old, TCBot now) {
		for (Map.Entry<EaglercraftUUID, TCBot> e : DUEL_BOT.entrySet()) {
			if (e.getValue() == old) {
				e.setValue(now);
				return;
			}
		}
	}

	static void forgetKit(TCBot bot) {
		KIT_OF.remove(bot.getUniqueID());
	}

	private static int indexOf(String kit) {
		for (int i = 0; i < KITS.length; i++) {
			if (KITS[i].equalsIgnoreCase(kit)) {
				return i;
			}
		}
		return 0;
	}
}
