package net.minecraft.tcpvp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import net.minecraft.util.math.AxisAlignedBB;
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
 * <p>A plain superflat with a ring of kit stands at spawn. Right-click a stand and you
 * and a bot with that kit go to that kit's duel area; the bot has endless totems and
 * always holds one. Command block buttons there switch the map between a small square
 * and the big flat, and (mace) raise the bot's shield or put it in the air. Kit items
 * never run out while you play.
 * Practice bot idea and command design from HeroBot by HerobaneNair (CC BY-NC-SA 4.0);
 * this is a new implementation for 1.12, not their code.
 */
public final class TCPractice {

	public static final String[] KITS = { "mace", "htcart", "ltcart", "sword", "axe", "netherite", "pot", "uhc",
			"smp", "diasmp", "crystal", "custom" };
	public static final String[] KIT_TITLES = { "Mace", "HT Cart", "LT Cart", "Sword", "Axe", "NethPot", "Diamond Pot",
			"UHC", "SMP", "Diamond SMP", "Crystal", "Custom" };
	/** Game rule that holds the custom kit (as item NBT text), saved with the world. */
	public static final String CUSTOM_RULE = "tcCustomKit";

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
		WorldServer w = p.getServerWorld();
		refreshStands(w);
		DUELS.remove(p.getUniqueID()); // a duel from before doesn't carry over (its bot is gone)
		bigMap(w);
		p.addChatMessage(text("Treasure Practice", TextFormatting.GOLD, true));
		p.addChatMessage(text("Right-click a kit stand to duel a bot with that kit.", TextFormatting.GRAY, false));
		ITextComponent bots = text("", TextFormatting.GRAY, false);
		bots.appendSibling(clickable("[Spawn a bot]", "/playerspawn", TextFormatting.LIGHT_PURPLE));
		bots.appendSibling(new TextComponentString(" "));
		bots.appendSibling(clickable("[Make custom kit]", "/customkit edit", TextFormatting.AQUA));
		bots.appendSibling(new TextComponentString(" "));
		bots.appendSibling(clickable("[Spawn]", "/spawn", TextFormatting.YELLOW));
		bots.appendSibling(new TextComponentString(" "));
		bots.appendSibling(clickable("[Help]", "/practice", TextFormatting.WHITE));
		p.addChatMessage(bots);
	}

	public static void help(net.minecraft.command.ICommandSender s) {
		String[] lines = { "§6Treasure Practice commands",
				"§b/duel <kit>§7 (or right-click a kit stand) · §b/kit <kit> [player]§7 · §b/spawn",
				"§b/customkit edit|save|clear§7 (save your inventory as the Custom kit)",
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
		return new int[] { (int) Math.round(Math.cos(a) * 8.0D), (int) Math.round(Math.sin(a) * 8.0D) };
	}

	/** The yaw of something at x,z looking at spawn. */
	private static float yawToCentre(double x, double z) {
		return (float) Math.toDegrees(Math.atan2(x, -z));
	}

	/** Nothing is built when chunks are made; the map is plain flat. */
	public static void populate(World world, int cx, int cz) {
	}

	/** Puts the kit stands at spawn back to the current set (old worlds get the new kits too). */
	public static void refreshStands(WorldServer w) {
		AxisAlignedBB box = new AxisAlignedBB(-12, TCArena.GROUND - 2, -12, 13, TCArena.GROUND + 6, 13);
		List<EntityArmorStand> old = w.getEntitiesWithinAABB(EntityArmorStand.class, box);
		for (int i = 0, n = old.size(); i < n; i++) {
			EntityArmorStand st = old.get(i);
			if (st.hasCustomName() && st.getCustomNameTag().startsWith("§6") && st.getCustomNameTag().endsWith(" Kit")) {
				st.setDead();
			}
		}
		for (int i = 0; i < KITS.length; i++) {
			int[] q = standPos(i);
			placeStand(w, q[0], q[1], i);
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
		ItemStack[] show = display(KITS[kit], world);
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
		return null;
	}

	/** Right-click on a kit stand: the server starts that kit's duel. */
	public static boolean onStandClicked(EntityArmorStand stand, EntityPlayer player) {
		String kit = kitOfStand(stand);
		if (kit == null) {
			return false;
		}
		if (!stand.world.isRemote && player instanceof EntityPlayerMP && !(player instanceof TCBot)) {
			EntityPlayerMP p = (EntityPlayerMP) player;
			Long last = LAST_CLICK.get(p.getUniqueID());
			long now = p.world.getTotalWorldTime();
			if (last == null || now - last.longValue() > 10L) {
				LAST_CLICK.put(p.getUniqueID(), Long.valueOf(now));
				if (kit.equals("custom") && customKit(p.world) == null) {
					customEdit(p);
				} else {
					startDuel(p, kit);
				}
			}
		}
		return true;
	}

	private static final Map<EaglercraftUUID, Long> LAST_CLICK = new HashMap<>();

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

	private static ItemStack drink(PotionType type) {
		return PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM, 1), type);
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
	private static ItemStack[] display(String kit, World w) {
		Kit k = build(kit, w);
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
			armor = new ItemStack[] { ItemStack.field_190927_a, ItemStack.field_190927_a, ItemStack.field_190927_a,
					ItemStack.field_190927_a };
		}
	}

	/**
	 * The kits, laid out like the usual tier-test kits (slots: hotbar 0-8, inventory
	 * rows 0-26). 1.21-only enchantments (Breach, Density, Wind Burst) and items 1.12
	 * can't use (respawn anchors) are left out.
	 */
	private static Kit build(String kit, World w) {
		Kit k = new Kit();
		ItemStack[] h = k.hotbar, v = k.inv;
		if (kit.equals("custom")) {
			ItemStack[] slots = customKit(w);
			if (slots == null) {
				h[0] = st(Items.WRITABLE_BOOK, 1).setStackDisplayName("§bRight-click to make your kit");
				return k;
			}
			for (int i = 0; i < 9; i++) h[i] = slots[i];
			for (int i = 0; i < 27; i++) v[i] = slots[9 + i];
			k.armor = new ItemStack[] { slots[39], slots[38], slots[37], slots[36] };
			k.offhand = slots[40];
			return k;
		}
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
		} else if (kit.equals("htcart")) {
			// high tier: netherite, bow and crossbow, lots of carts and totems
			k.armor = netherite(P4U3M);
			k.armor[2] = ench(via("netherite_leggings", 1), Enchantments.BLAST_PROTECTION, 4, Enchantments.UNBREAKING, 3,
					Enchantments.MENDING, 1);
			ench(k.armor[3], Enchantments.FEATHER_FALLING, 4);
			h[0] = ench(via("netherite_sword", 1), Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3);
			h[1] = ench(st(Items.BOW, 1), Enchantments.POWER, 5, Enchantments.FLAME, 1, Enchantments.UNBREAKING, 3);
			h[2] = block(Blocks.RAIL, 64);
			h[3] = st(Items.TNT_MINECART, 64);
			h[4] = ench(via("crossbow", 1), Enchantments.UNBREAKING, 3);
			h[5] = st(Items.ENDER_PEARL, 16);
			h[6] = st(Items.GOLDEN_APPLE, 64);
			h[7] = ench(via("netherite_pickaxe", 1), Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3);
			h[8] = ench(st(Items.FLINT_AND_STEEL, 1), Enchantments.UNBREAKING, 3);
			v[0] = st(Items.ARROW, 64);
			v[1] = block(Blocks.RAIL, 64);
			v[2] = st(Items.TNT_MINECART, 64);
			v[3] = ench(via("netherite_axe", 1), Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3);
			v[4] = ench(st(Items.SHIELD, 1), Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1);
			v[5] = st(Items.EXPERIENCE_BOTTLE, 64);
			for (int i = 9; i < 18; i++) v[i] = st(Items.TOTEM_OF_UNDYING, 1);
			k.offhand = st(Items.TOTEM_OF_UNDYING, 1);
		} else if (kit.equals("ltcart")) {
			// low tier: diamond, bow only, no totems
			k.armor = diamond(P4U3);
			k.armor[2] = ench(st(Items.DIAMOND_LEGGINGS, 1), Enchantments.BLAST_PROTECTION, 4, Enchantments.UNBREAKING, 3);
			ench(k.armor[3], Enchantments.FEATHER_FALLING, 4);
			h[0] = ench(st(Items.DIAMOND_SWORD, 1), Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3);
			h[1] = ench(st(Items.DIAMOND_AXE, 1), Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3);
			h[2] = ench(st(Items.BOW, 1), Enchantments.POWER, 5, Enchantments.FLAME, 1, Enchantments.UNBREAKING, 3);
			h[3] = block(Blocks.RAIL, 64);
			h[4] = st(Items.TNT_MINECART, 64);
			h[5] = st(Items.ENDER_PEARL, 16);
			h[6] = st(Items.GOLDEN_APPLE, 64);
			h[7] = ench(st(Items.DIAMOND_PICKAXE, 1), Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3);
			h[8] = st(Items.COOKED_BEEF, 64);
			v[0] = st(Items.ARROW, 64);
			v[1] = block(Blocks.LOG, 64);
			v[2] = ench(st(Items.FLINT_AND_STEEL, 1), Enchantments.UNBREAKING, 3);
			k.offhand = ench(st(Items.SHIELD, 1), Enchantments.UNBREAKING, 3);
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
		} else if (kit.equals("pot")) {
			// Diamond Pot: diamond gear, healing splashes, speed and fire resistance
			k.armor = diamond(P4U3);
			h[0] = ench(st(Items.DIAMOND_SWORD, 1), Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3,
					Enchantments.FIRE_ASPECT, 2);
			for (int i = 1; i < 7; i++) h[i] = splash(PotionTypes.STRONG_HEALING);
			h[7] = drink(PotionTypes.STRONG_SWIFTNESS);
			h[8] = st(Items.COOKED_BEEF, 64);
			for (int i = 0; i < 21; i++) v[i] = splash(PotionTypes.STRONG_HEALING);
			for (int i = 21; i < 24; i++) v[i] = drink(PotionTypes.STRONG_SWIFTNESS);
			for (int i = 24; i < 26; i++) v[i] = drink(PotionTypes.LONG_FIRE_RESISTANCE);
		} else if (kit.equals("uhc")) {
			// UHC: diamond gear, lava and water, webs, bow and crossbow, blocks to build
			k.armor = diamond(new Object[0]);
			ench(k.armor[0], Enchantments.PROTECTION, 3, Enchantments.UNBREAKING, 3);
			ench(k.armor[1], Enchantments.PROTECTION, 2, Enchantments.UNBREAKING, 3);
			ench(k.armor[2], Enchantments.PROTECTION, 3, Enchantments.UNBREAKING, 3);
			ench(k.armor[3], Enchantments.PROTECTION, 3, Enchantments.UNBREAKING, 3);
			h[0] = ench(st(Items.DIAMOND_SWORD, 1), Enchantments.SHARPNESS, 4, Enchantments.UNBREAKING, 3);
			h[1] = ench(st(Items.DIAMOND_AXE, 1), Enchantments.SHARPNESS, 1, Enchantments.UNBREAKING, 3);
			h[2] = st(Items.LAVA_BUCKET, 1);
			h[3] = st(Items.WATER_BUCKET, 1);
			h[4] = ench(st(Items.BOW, 1), Enchantments.POWER, 3, Enchantments.UNBREAKING, 3);
			h[5] = st(Items.GOLDEN_APPLE, 13);
			h[6] = block(Blocks.COBBLESTONE, 64);
			h[7] = block(Blocks.WEB, 8);
			h[8] = st(Items.FISHING_ROD, 1);
			v[0] = st(Items.ARROW, 16);
			v[1] = st(Items.LAVA_BUCKET, 1);
			v[2] = st(Items.WATER_BUCKET, 1);
			v[3] = ench(via("crossbow", 1), Enchantments.UNBREAKING, 3);
			v[4] = ench(st(Items.DIAMOND_PICKAXE, 1), Enchantments.EFFICIENCY, 3, Enchantments.UNBREAKING, 3);
			v[5] = block(Blocks.PLANKS, 64);
			v[6] = st(Items.COOKED_BEEF, 64);
			k.offhand = ench(st(Items.SHIELD, 1), Enchantments.UNBREAKING, 3);
		} else if (kit.equals("smp") || kit.equals("diasmp")) {
			// SMP (netherite) / Diamond SMP: totems, pearls, gapples, potions, webs, shield
			boolean neth = kit.equals("smp");
			k.armor = neth ? netherite(P4U3M) : diamond(P4U3M);
			ench(k.armor[3], Enchantments.FEATHER_FALLING, 4);
			h[0] = ench(neth ? via("netherite_sword", 1) : st(Items.DIAMOND_SWORD, 1), Enchantments.SHARPNESS, 5,
					Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1);
			h[1] = ench(neth ? via("netherite_axe", 1) : st(Items.DIAMOND_AXE, 1), Enchantments.SHARPNESS, 5,
					Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1);
			h[2] = st(Items.ENDER_PEARL, 16);
			h[3] = st(Items.GOLDEN_APPLE, 64);
			h[4] = st(Items.EXPERIENCE_BOTTLE, 64);
			h[5] = splash(PotionTypes.STRONG_STRENGTH);
			h[6] = splash(PotionTypes.STRONG_SWIFTNESS);
			h[7] = block(Blocks.WEB, 64);
			h[8] = st(Items.TOTEM_OF_UNDYING, 1);
			for (int i = 0; i < 6; i++) v[i] = st(Items.TOTEM_OF_UNDYING, 1);
			for (int i = 9; i < 13; i++) v[i] = splash(PotionTypes.STRONG_STRENGTH);
			for (int i = 13; i < 17; i++) v[i] = splash(PotionTypes.STRONG_SWIFTNESS);
			for (int i = 17; i < 19; i++) v[i] = drink(PotionTypes.LONG_FIRE_RESISTANCE);
			v[19] = st(Items.WATER_BUCKET, 1);
			v[20] = st(Items.ENDER_PEARL, 16);
			v[21] = block(Blocks.OBSIDIAN, 64);
			v[22] = st(Items.GOLDEN_CARROT, 64);
			v[26] = st(Items.EXPERIENCE_BOTTLE, 64);
			k.offhand = ench(st(Items.SHIELD, 1), Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1);
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
		return kitId(name) != null;
	}

	/** The kit id for a name ("cart" means the high tier cart kit), or null. */
	public static String kitId(String name) {
		if (name.equalsIgnoreCase("cart")) {
			return "htcart";
		}
		for (String k : KITS) {
			if (k.equalsIgnoreCase(name)) {
				return k;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- custom kit

	/** The saved custom kit (41 slots: 0-35 inventory, 36-39 boots..helmet, 40 off hand), or null. */
	public static ItemStack[] customKit(World w) {
		if (w == null || !w.getGameRules().hasRule(CUSTOM_RULE)) {
			return null;
		}
		String txt = w.getGameRules().getString(CUSTOM_RULE);
		if (txt == null || txt.isEmpty()) {
			return null;
		}
		try {
			NBTTagCompound tag = net.minecraft.nbt.JsonToNBT.getTagFromJson(txt);
			net.minecraft.nbt.NBTTagList list = tag.getTagList("Items", 10);
			ItemStack[] out = new ItemStack[41];
			for (int i = 0; i < out.length; i++) out[i] = ItemStack.field_190927_a;
			for (int i = 0; i < list.tagCount(); i++) {
				NBTTagCompound it = list.getCompoundTagAt(i);
				int slot = it.getByte("Slot") & 255;
				if (slot < out.length) {
					out[slot] = new ItemStack(it);
				}
			}
			return out;
		} catch (Exception e) {
			return null;
		}
	}

	/** Saves the player's whole inventory as the custom kit. */
	public static void customSave(EntityPlayerMP p) {
		net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
		for (int i = 0; i < p.inventory.getSizeInventory() && i < 41; i++) {
			ItemStack s = p.inventory.getStackInSlot(i);
			if (!s.func_190926_b()) {
				NBTTagCompound it = new NBTTagCompound();
				it.setByte("Slot", (byte) i);
				s.writeToNBT(it);
				list.appendTag(it);
			}
		}
		NBTTagCompound tag = new NBTTagCompound();
		tag.setTag("Items", list);
		p.world.getGameRules().setOrCreateGameRule(CUSTOM_RULE, tag.toString());
		if (p.isCreative()) {
			p.setGameType(net.minecraft.world.GameType.SURVIVAL);
		}
		refreshStands(p.getServerWorld());
		p.addChatMessage(text("Saved your inventory as the Custom kit.", TextFormatting.GREEN, false));
		ITextComponent o = text("", TextFormatting.GRAY, false);
		o.appendSibling(clickable("[Duel with it]", "/duel custom", TextFormatting.GREEN));
		o.appendSibling(new TextComponentString(" "));
		o.appendSibling(clickable("[Edit again]", "/customkit edit", TextFormatting.AQUA));
		p.addChatMessage(o);
	}

	/** Creative mode at spawn to put a kit together, with a Save button in chat. */
	public static void customEdit(EntityPlayerMP p) {
		endDuel(p);
		toSpawn(p);
		ItemStack[] saved = customKit(p.world);
		p.inventory.clear();
		if (saved != null) {
			for (int i = 0; i < saved.length && i < p.inventory.getSizeInventory(); i++) {
				p.inventory.setInventorySlotContents(i, saved[i].copy());
			}
		}
		KIT_OF.remove(p.getUniqueID());
		p.setGameType(net.minecraft.world.GameType.CREATIVE);
		p.addChatMessage(text("Custom kit maker: put the items, armor and off hand you want in your inventory "
				+ "(you're in creative to pick anything), then press Save.", TextFormatting.AQUA, false));
		ITextComponent o = text("", TextFormatting.GRAY, false);
		o.appendSibling(clickable("[Save as custom kit]", "/customkit save", TextFormatting.GREEN));
		o.appendSibling(new TextComponentString(" "));
		o.appendSibling(clickable("[Cancel]", "/customkit cancel", TextFormatting.RED));
		p.addChatMessage(o);
	}

	/** Clears the player, heals them, gives the kit and keeps it topped up from now on. */
	public static void giveKit(EntityPlayer p, String kit) {
		Kit k = build(kit.toLowerCase(), p.world);
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

	/** A running duel: its kit, where it is, its bot and switches. */
	public static final class Duel {
		public final String kit;
		public final int x, z;
		public TCBot bot;
		public boolean shield, air, small;

		Duel(String kit, int x, int z) {
			this.kit = kit;
			this.x = x;
			this.z = z;
		}
	}

	private static final Map<EaglercraftUUID, Duel> DUELS = new HashMap<>();

	public static Duel duelOf(EntityPlayer p) {
		return DUELS.get(p.getUniqueID());
	}

	/** Each kit has its own duel area out on the flat (the same one every time). */
	private static int[] duelSpot(String kit) {
		return new int[] { 512 + 192 * indexOf(kit), 512 };
	}

	private static final int SMALL_MAP = 40;

	/** The whole world is open again (normal big flat). */
	public static void bigMap(WorldServer w) {
		w.getWorldBorder().setCenter(0.0D, 0.0D);
		w.getWorldBorder().setTransition(6.0E7D);
		w.getWorldBorder().setWarningDistance(5);
	}

	private static void applyMap(WorldServer w, Duel d) {
		if (d.small) {
			w.getWorldBorder().setCenter(d.x + 0.5D, d.z + 3.5D);
			w.getWorldBorder().setTransition(SMALL_MAP);
			w.getWorldBorder().setWarningDistance(1);
		} else {
			bigMap(w);
		}
	}

	/**
	 * You and a bot with the kit go to the kit's duel area: you 6 blocks from the bot,
	 * facing it. The bot never runs out of totems and always holds one. Mace starts on
	 * the small square map, the rest on the big flat; command block buttons behind you
	 * switch that (and, for mace, the bot's shield and floating).
	 */
	public static void startDuel(EntityPlayerMP p, String kit) {
		MinecraftServer server = p.getServer();
		WorldServer w = p.getServerWorld();
		endDuel(p);
		int[] spot = duelSpot(kit);
		int x = spot[0], z = spot[1];
		// make the ground there first, so it's there when you arrive
		for (int cx = (x >> 4) - 2; cx <= (x >> 4) + 2; cx++) {
			for (int cz = (z >> 4) - 2; cz <= (z >> 4) + 2; cz++) {
				w.getChunkFromChunkCoords(cx, cz);
			}
		}
		clearDrops(w, x, z);
		Duel d = new Duel(kit, x, z);
		d.small = kit.equals("mace");
		buildButtons(w, d);
		double y = TCArena.GROUND + 1;
		giveKit(p, kit);
		if (p.isCreative() || p.isSpectator()) {
			p.setGameType(net.minecraft.world.GameType.SURVIVAL);
		}
		teleport(p, x + 0.5D, y, z + 0.5D, 0.0F);
		String name = TCBots.freeName(server, "Bot");
		TCBot bot = TCBot.spawn(server, w, name, x + 0.5D, y, z + 6.5D, 180.0F, 0.0F, false);
		bot.infiniteTotems = true;
		bot.kit = kit;
		bot.actions.lookAt = p;
		d.bot = bot;
		loadout(bot, d);
		applyMap(w, d);
		DUELS.put(p.getUniqueID(), d);
		p.addChatMessage(text(KIT_TITLES[indexOf(kit)] + " duel. Your items never run out; the bot never runs out of totems.",
				TextFormatting.GOLD, false));
		ITextComponent opts = text("", TextFormatting.GRAY, false);
		opts.appendSibling(clickable("[Rematch]", "/duel " + kit, TextFormatting.GREEN));
		opts.appendSibling(new TextComponentString(" "));
		opts.appendSibling(clickable("[Map: big/small]", "/practicebutton map", TextFormatting.AQUA));
		if (kit.equals("mace")) {
			opts.appendSibling(new TextComponentString(" "));
			opts.appendSibling(clickable("[Bot shield]", "/practicebutton shield", TextFormatting.AQUA));
			opts.appendSibling(new TextComponentString(" "));
			opts.appendSibling(clickable("[Bot in air]", "/practicebutton air", TextFormatting.AQUA));
		}
		opts.appendSibling(new TextComponentString(" "));
		opts.appendSibling(clickable("[Back to spawn]", "/spawn", TextFormatting.YELLOW));
		p.addChatMessage(opts);
	}

	/** The bot gets the kit and always holds a totem: off hand, or main hand when its shield is up. */
	private static void loadout(TCBot bot, Duel d) {
		giveKit(bot, d.kit);
		ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
		if (d.shield) {
			bot.inventory.currentItem = 0;
			bot.setHeldItem(EnumHand.MAIN_HAND, totem);
			bot.setHeldItem(EnumHand.OFF_HAND, ench(new ItemStack(Items.SHIELD), Enchantments.UNBREAKING, 3));
		} else {
			bot.setHeldItem(EnumHand.OFF_HAND, totem);
			bot.resetActiveHand();
		}
		bot.actions.shield = d.shield;
		bot.duelShield = d.shield;
		remember(bot);
	}

	/** A command block button (or its chat twin) in the player's duel. */
	public static void pressButton(EntityPlayerMP p, String what) {
		Duel d = DUELS.get(p.getUniqueID());
		if (d == null || d.bot == null) {
			p.addChatMessage(text("Start a duel first (right-click a kit stand).", TextFormatting.RED, false));
			return;
		}
		WorldServer w = p.getServerWorld();
		TCBot bot = d.bot;
		if (what.equals("map")) {
			d.small = !d.small;
			applyMap(w, d);
			if (d.small) {
				teleport(p, d.x + 0.5D, TCArena.GROUND + 1, d.z + 0.5D, 0.0F);
				TCPractice.teleport(bot, d.x + 0.5D, bot.posY, d.z + 6.5D, 180.0F);
			}
			p.addChatMessage(text("Map: " + (d.small ? "small square" : "big flat"), TextFormatting.AQUA, false));
		} else if (what.equals("shield")) {
			d.shield = !d.shield;
			loadout(bot, d);
			p.addChatMessage(text("Bot shield: " + (d.shield ? "up (stays up even when stunned)" : "down"),
					TextFormatting.AQUA, false));
		} else if (what.equals("air")) {
			d.air = !d.air;
			bot.actions.setFloating(d.air, TCArena.GROUND + 1 + 8);
			p.addChatMessage(text("Bot " + (d.air ? "in the air" : "back on the ground"), TextFormatting.AQUA, false));
		}
	}

	/** Leaving a duel (back to spawn): its bot goes away and the map opens up. */
	public static void endDuel(EntityPlayerMP p) {
		Duel d = DUELS.remove(p.getUniqueID());
		if (d != null) {
			if (d.bot != null && p.getServer().getPlayerList().getPlayerList().contains(d.bot)) {
				TCBots.remove(d.bot);
			}
			bigMap(p.getServerWorld());
		}
	}

	/** A duel bot respawned as a new entity. */
	static boolean replaced(TCBot old, TCBot now) {
		for (Duel d : DUELS.values()) {
			if (d.bot == old) {
				d.bot = now;
				now.kit = d.kit;
				loadout(now, d);
				if (d.air) {
					now.actions.setFloating(true, TCArena.GROUND + 1 + 8);
				}
				return true;
			}
		}
		return false;
	}

	/** Items, arrows, carts, crystals and such left from the last fight there. */
	private static void clearDrops(WorldServer w, int x, int z) {
		AxisAlignedBB box = new AxisAlignedBB(x - 48, 0, z - 48, x + 49, 256, z + 49);
		List<net.minecraft.entity.Entity> list = w.getEntitiesWithinAABB(net.minecraft.entity.Entity.class, box);
		for (int i = 0, n = list.size(); i < n; i++) {
			net.minecraft.entity.Entity e = list.get(i);
			if (!(e instanceof EntityPlayer) && !(e instanceof EntityLivingBase)) {
				e.setDead();
			}
		}
	}

	/**
	 * Command blocks with buttons 4 blocks behind where you start (turn around to press):
	 * Map for every kit; mace also gets Shield and Air. A sign on top names each one.
	 */
	private static void buildButtons(WorldServer w, Duel d) {
		int y = TCArena.GROUND + 1, bz = d.z - 4;
		// clear the row first (an older duel of another kit may have left a different set)
		for (int bx = d.x - 3; bx <= d.x + 3; bx++) {
			for (int dy = 0; dy < 2; dy++) {
				w.setBlockToAir(new BlockPos(bx, y + dy, bz));
				w.setBlockToAir(new BlockPos(bx, y + dy, bz + 1));
			}
		}
		boolean mace = d.kit.equals("mace");
		placeButton(w, d.x, y, bz, "map", "Map", "big / small");
		if (mace) {
			placeButton(w, d.x - 2, y, bz, "shield", "Bot Shield", "on / off");
			placeButton(w, d.x + 2, y, bz, "air", "Bot in Air", "up / down");
		}
	}

	private static void placeButton(WorldServer w, int x, int y, int z, String what, String l1, String l2) {
		BlockPos cb = new BlockPos(x, y, z);
		w.setBlockState(cb, Blocks.COMMAND_BLOCK.getDefaultState(), 2);
		net.minecraft.tileentity.TileEntity te = w.getTileEntity(cb);
		if (te instanceof net.minecraft.tileentity.TileEntityCommandBlock) {
			net.minecraft.tileentity.CommandBlockBaseLogic logic = ((net.minecraft.tileentity.TileEntityCommandBlock) te)
					.getCommandBlockLogic();
			logic.setCommand("/practicebutton " + what);
			logic.setTrackOutput(false);
			te.markDirty();
		}
		w.setBlockState(cb.south(), Blocks.STONE_BUTTON.getDefaultState()
				.withProperty(net.minecraft.block.BlockDirectional.FACING, net.minecraft.util.EnumFacing.SOUTH), 2);
		BlockPos sp = cb.up();
		w.setBlockState(sp, Blocks.STANDING_SIGN.getDefaultState()
				.withProperty(net.minecraft.block.BlockStandingSign.ROTATION, Integer.valueOf(0)), 2);
		net.minecraft.tileentity.TileEntity st = w.getTileEntity(sp);
		if (st instanceof net.minecraft.tileentity.TileEntitySign) {
			net.minecraft.tileentity.TileEntitySign sign = (net.minecraft.tileentity.TileEntitySign) st;
			sign.signText[0] = new TextComponentString("");
			sign.signText[1] = text(l1, TextFormatting.DARK_BLUE, true);
			sign.signText[2] = new TextComponentString(l2);
			sign.signText[3] = text("press the button", TextFormatting.DARK_GRAY, false);
			sign.markDirty();
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
