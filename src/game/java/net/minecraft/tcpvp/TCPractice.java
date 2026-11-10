package net.minecraft.tcpvp;

import java.util.List;

import net.minecraft.block.BlockStandingSign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
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
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
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
 * <p>A spawn hub with kit signs and arena signs, four arenas ({@link TCArena}), kits
 * for modern PvP, and HeroBot-style practice bots ({@link TCBot}). Practice bot idea
 * and command design from HeroBot by HerobaneNair (CC BY-NC-SA 4.0); this is a new
 * implementation for 1.12, not their code.
 */
public final class TCPractice {

	public static final String[] KITS = { "mace", "cart", "sword", "netherite", "axe", "crystal" };
	private static final String[] KIT_TITLES = { "Mace", "Cart", "Sword", "Netherite", "Axe", "Crystal" };
	private static final String[] KIT_NOTES = { "wind charges", "TNT minecarts", "classic duel", "pots + totems",
			"shield + axe", "end crystals" };

	private TCPractice() {
	}

	public static boolean isPractice(World world) {
		return world != null && world.getWorldInfo().getTerrainType() == WorldType.PRACTICE;
	}

	// ---------------------------------------------------------------- world setup

	/** First start of a new Practice world: spawn on the hub, noon forever, no mobs or weather. */
	public static void setupNewWorld(WorldInfo info, GameRules rules) {
		info.setSpawn(new BlockPos(0, TCArena.GROUND + 1, 0));
		info.setMapFeaturesEnabled(false);
		info.setRaining(false);
		info.setThundering(false);
		info.setCleanWeatherTime(1000000000);
		info.setWorldTime(6000L);
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
		ITextComponent kits = text("Kits: ", TextFormatting.GRAY, false);
		for (int i = 0; i < KITS.length; i++) {
			kits.appendSibling(clickable("[" + KIT_TITLES[i] + "]", "/kit " + KITS[i], TextFormatting.AQUA));
			kits.appendSibling(new TextComponentString(" "));
		}
		p.addChatMessage(kits);
		ITextComponent arenas = text("Arenas: ", TextFormatting.GRAY, false);
		for (TCArena.Arena a : TCArena.ARENAS) {
			arenas.appendSibling(clickable("[" + a.title + "]", "/arena " + a.name, TextFormatting.GREEN));
			arenas.appendSibling(new TextComponentString(" "));
		}
		arenas.appendSibling(clickable("[Spawn]", "/spawn", TextFormatting.YELLOW));
		p.addChatMessage(arenas);
		ITextComponent bots = text("Practice bots: ", TextFormatting.GRAY, false);
		bots.appendSibling(clickable("[Spawn a bot here]", "/playerspawn Bot", TextFormatting.LIGHT_PURPLE));
		bots.appendSibling(new TextComponentString(" "));
		bots.appendSibling(clickable("[Help]", "/practice", TextFormatting.WHITE));
		p.addChatMessage(bots);
	}

	public static void help(net.minecraft.command.ICommandSender s) {
		String[] lines = { "§6Treasure Practice commands",
				"§b/kit <mace|cart|sword|netherite|axe|crystal> [player]",
				"§b/arena <duel|mace|cart|crystal>§7 (resets it, takes you there) · §b/arena reset",
				"§b/spawn",
				"§d/playerspawn <name> [at <x y z>] [facing <yaw pitch>]",
				"§d/player <bot> kit <name>§7 · §dshield [on|off]§7 · §dattack|use|jump|swing [once|continuous|interval <ticks>]",
				"§d/player <bot> move [forward|backward|left|right]§7 · §dsneak|unsneak|sprint|unsprint",
				"§d/player <bot> look <north|south|east|west|up|down|at <x y z>|<yaw> <pitch>>§7 · §dlook me",
				"§d/player <bot> hotbar <1-9>§7 · §dstop|kill|disconnect|heal",
				"§e/herobot§7 bot and combat settings" };
		for (String l : lines) {
			s.addChatMessage(new TextComponentString(l));
		}
	}

	private static ITextComponent text(String s, TextFormatting color, boolean bold) {
		TextComponentString t = new TextComponentString(s);
		t.getStyle().setColor(color);
		if (bold) {
			t.getStyle().setBold(Boolean.valueOf(true));
		}
		return t;
	}

	private static ITextComponent clickable(String s, String command, TextFormatting color) {
		TextComponentString t = new TextComponentString(s);
		t.getStyle().setColor(color);
		t.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
		t.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(command)));
		return t;
	}

	// ---------------------------------------------------------------- hub decoration (chunk populate)

	/** Kit sign i: position on the north half of the hub; arena sign j: south half. */
	private static int[] kitSignPos(int i, double radius) {
		double a = Math.toRadians(-150.0D + i * 24.0D);
		return new int[] { (int) Math.round(Math.cos(a) * radius), (int) Math.round(Math.sin(a) * radius) };
	}

	private static int[] arenaSignPos(int j) {
		double a = Math.toRadians(45.0D + j * 30.0D);
		return new int[] { (int) Math.round(Math.cos(a) * 8.0D), (int) Math.round(Math.sin(a) * 8.0D) };
	}

	/** The yaw of something at x,z looking at the hub centre. */
	private static float yawToCentre(double x, double z) {
		return (float) Math.toDegrees(Math.atan2(x, -z));
	}

	/** Signs and kit stands of the chunk at cx,cz (called when it is first decorated). */
	public static void populate(World world, int cx, int cz) {
		for (int i = 0; i < KITS.length; i++) {
			int[] p = kitSignPos(i, 8.0D);
			if (p[0] >> 4 == cx && p[1] >> 4 == cz) {
				placeSign(world, p[0], p[1], "[ " + KIT_TITLES[i] + " ]", "Kit", KIT_NOTES[i], "/kit " + KITS[i],
						TextFormatting.DARK_AQUA);
			}
			int[] q = kitSignPos(i, 10.5D);
			if (q[0] >> 4 == cx && q[1] >> 4 == cz) {
				placeStand(world, q[0], q[1], i);
			}
		}
		for (int j = 0; j < TCArena.ARENAS.length; j++) {
			int[] p = arenaSignPos(j);
			if (p[0] >> 4 == cx && p[1] >> 4 == cz) {
				TCArena.Arena a = TCArena.ARENAS[j];
				placeSign(world, p[0], p[1], "[ " + a.title + " ]", "Arena", "click to go", "/arena " + a.name,
						TextFormatting.DARK_GREEN);
			}
		}
		if (0 >> 4 == cx && 4 >> 4 == cz) {
			placeSign(world, 0, 4, "Treasure", "Practice", "click for help", "/practice", TextFormatting.GOLD);
		}
	}

	private static void placeSign(World world, int x, int z, String title, String l2, String l3, String command,
			TextFormatting color) {
		BlockPos pos = new BlockPos(x, TCArena.GROUND + 1, z);
		// text toward the centre (a sign placed by someone standing in the middle looks the same)
		int rot = Math.round(yawToCentre(x, z) / 22.5F) & 15;
		IBlockState st = Blocks.STANDING_SIGN.getDefaultState().withProperty(BlockStandingSign.ROTATION,
				Integer.valueOf(rot));
		world.setBlockState(pos, st, 2);
		TileEntity te = world.getTileEntity(pos);
		if (te instanceof TileEntitySign) {
			TileEntitySign sign = (TileEntitySign) te;
			TextComponentString t = new TextComponentString(title);
			t.getStyle().setColor(color);
			t.getStyle().setBold(Boolean.valueOf(true));
			t.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
			sign.signText[0] = new TextComponentString("");
			sign.signText[1] = t;
			sign.signText[2] = new TextComponentString(l2);
			sign.signText[3] = text(l3, TextFormatting.DARK_GRAY, false);
			sign.markDirty();
		}
	}

	private static void placeStand(World world, int x, int z, int kit) {
		EntityArmorStand stand = new EntityArmorStand(world, x + 0.5D, TCArena.GROUND + 1, z + 0.5D);
		stand.rotationYaw = yawToCentre(x, z);
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
		stand.setCustomNameTag("§6" + KIT_TITLES[kit] + " Kit");
		stand.setAlwaysRenderNameTag(true);
		world.spawnEntityInWorld(stand);
	}

	// ---------------------------------------------------------------- kits

	private static ItemStack st(Item it, int n) {
		return it == null ? ItemStack.field_190927_a : new ItemStack(it, n);
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

	private static ItemStack potion(Item it, PotionType type, int n) {
		return PotionUtils.addPotionToItemStack(new ItemStack(it, n), type);
	}

	private static ItemStack fireworks(int n) {
		ItemStack s = new ItemStack(Items.FIREWORKS, n);
		NBTTagCompound tag = new NBTTagCompound();
		NBTTagCompound fw = new NBTTagCompound();
		fw.setByte("Flight", (byte) 1);
		tag.setTag("Fireworks", fw);
		s.setTagCompound(tag);
		return s;
	}

	/** Armor helmet..boots, both made of netherite (prot IV) or diamond (prot III). */
	private static ItemStack[] armor(boolean netherite, Enchantment legs, int legsLvl, boolean featherFalling) {
		ItemStack h = netherite ? via("netherite_helmet", 1) : st(Items.DIAMOND_HELMET, 1);
		ItemStack c = netherite ? via("netherite_chestplate", 1) : st(Items.DIAMOND_CHESTPLATE, 1);
		ItemStack l = netherite ? via("netherite_leggings", 1) : st(Items.DIAMOND_LEGGINGS, 1);
		ItemStack b = netherite ? via("netherite_boots", 1) : st(Items.DIAMOND_BOOTS, 1);
		int prot = netherite ? 4 : 3;
		ench(h, Enchantments.PROTECTION, prot, Enchantments.UNBREAKING, 3);
		ench(c, Enchantments.PROTECTION, prot, Enchantments.UNBREAKING, 3);
		ench(l, legs != null ? legs : Enchantments.PROTECTION, legs != null ? legsLvl : prot, Enchantments.UNBREAKING, 3);
		ench(b, Enchantments.PROTECTION, prot, Enchantments.UNBREAKING, 3);
		if (featherFalling) {
			ench(b, Enchantments.FEATHER_FALLING, 4);
		}
		return new ItemStack[] { h, c, l, b };
	}

	/** What a kit stand shows: helmet, chest, legs, boots, main hand, off hand. */
	private static ItemStack[] display(String kit) {
		Kit k = build(kit);
		return new ItemStack[] { k.armor[0], k.armor[1], k.armor[2], k.armor[3], k.hotbar[0], k.offhand };
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

	private static Kit build(String kit) {
		Kit k = new Kit();
		ItemStack[] h = k.hotbar, v = k.inv;
		if (kit.equals("mace")) {
			k.armor = armor(true, null, 0, true);
			h[0] = ench(via("mace", 1), Enchantments.UNBREAKING, 3);
			h[1] = ench(via("netherite_sword", 1), Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3);
			h[2] = via("wind_charge", 64);
			h[3] = st(Items.ENDER_PEARL, 16);
			h[4] = st(Items.GOLDEN_APPLE, 16);
			h[5] = ench(via("netherite_axe", 1), Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3);
			h[6] = ench(st(Items.ELYTRA, 1), Enchantments.UNBREAKING, 3);
			h[7] = fireworks(64);
			h[8] = st(Items.TOTEM_OF_UNDYING, 1);
			v[0] = via("wind_charge", 64);
			v[1] = via("wind_charge", 64);
			v[2] = st(Items.ENDER_PEARL, 16);
			for (int i = 3; i < 8; i++) v[i] = st(Items.TOTEM_OF_UNDYING, 1);
			v[8] = st(Items.GOLDEN_APPLE, 16);
			k.offhand = st(Items.TOTEM_OF_UNDYING, 1);
		} else if (kit.equals("cart")) {
			k.armor = armor(true, Enchantments.BLAST_PROTECTION, 4, false);
			h[0] = ench(via("netherite_sword", 1), Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3);
			h[1] = ench(st(Items.BOW, 1), Enchantments.POWER, 5, Enchantments.FLAME, 1, Enchantments.INFINITY, 1,
					Enchantments.UNBREAKING, 3);
			h[2] = st(Item.getItemFromBlock(Blocks.RAIL), 64);
			for (int i = 3; i < 7; i++) h[i] = st(Items.TNT_MINECART, 1);
			h[7] = st(Items.GOLDEN_APPLE, 16);
			h[8] = st(Items.ENDER_PEARL, 16);
			v[0] = st(Items.ARROW, 1);
			v[1] = st(Item.getItemFromBlock(Blocks.RAIL), 64);
			v[2] = st(Items.FLINT_AND_STEEL, 1);
			v[3] = ench(via("netherite_pickaxe", 1), Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3);
			for (int i = 9; i < 21; i++) v[i] = st(Items.TNT_MINECART, 1);
			for (int i = 21; i < 25; i++) v[i] = st(Items.TOTEM_OF_UNDYING, 1);
			k.offhand = st(Items.TOTEM_OF_UNDYING, 1);
		} else if (kit.equals("sword")) {
			k.armor = armor(false, null, 0, false);
			h[0] = ench(st(Items.DIAMOND_SWORD, 1), Enchantments.SHARPNESS, 3, Enchantments.UNBREAKING, 3);
			h[1] = st(Items.FISHING_ROD, 1);
			h[2] = ench(st(Items.BOW, 1), Enchantments.POWER, 1);
			h[3] = st(Items.GOLDEN_APPLE, 8);
			h[4] = st(Items.WATER_BUCKET, 1);
			h[5] = st(Items.COOKED_BEEF, 64);
			v[0] = st(Items.ARROW, 16);
			k.offhand = ItemStack.field_190927_a;
		} else if (kit.equals("netherite")) {
			k.armor = armor(true, null, 0, false);
			h[0] = ench(via("netherite_sword", 1), Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3);
			h[1] = ench(via("netherite_axe", 1), Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3);
			h[2] = st(Items.GOLDEN_APPLE, 16);
			h[3] = st(Items.ENDER_PEARL, 16);
			for (int i = 4; i < 8; i++) h[i] = potion(Items.SPLASH_POTION, PotionTypes.STRONG_HEALING, 1);
			h[8] = potion(Items.SPLASH_POTION, PotionTypes.STRONG_SWIFTNESS, 1);
			for (int i = 0; i < 12; i++) v[i] = potion(Items.SPLASH_POTION, PotionTypes.STRONG_HEALING, 1);
			v[12] = potion(Items.SPLASH_POTION, PotionTypes.STRONG_SWIFTNESS, 1);
			for (int i = 13; i < 17; i++) v[i] = st(Items.TOTEM_OF_UNDYING, 1);
			k.offhand = st(Items.TOTEM_OF_UNDYING, 1);
		} else if (kit.equals("axe")) {
			k.armor = armor(false, null, 0, false);
			h[0] = ench(st(Items.DIAMOND_AXE, 1), Enchantments.SHARPNESS, 3, Enchantments.UNBREAKING, 3);
			h[1] = ench(st(Items.DIAMOND_SWORD, 1), Enchantments.SHARPNESS, 3, Enchantments.UNBREAKING, 3);
			h[2] = ench(st(Items.BOW, 1), Enchantments.POWER, 2);
			h[3] = st(Items.GOLDEN_APPLE, 6);
			h[4] = st(Items.COOKED_BEEF, 64);
			v[0] = st(Items.ARROW, 16);
			k.offhand = ench(st(Items.SHIELD, 1), Enchantments.UNBREAKING, 3);
		} else { // crystal
			k.armor = armor(true, Enchantments.BLAST_PROTECTION, 4, false);
			h[0] = ench(via("netherite_sword", 1), Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3);
			h[1] = st(Items.END_CRYSTAL, 64);
			h[2] = st(Item.getItemFromBlock(Blocks.OBSIDIAN), 64);
			h[3] = st(Items.ENDER_PEARL, 16);
			h[4] = st(Items.GOLDEN_APPLE, 64);
			h[5] = ench(via("netherite_pickaxe", 1), Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3);
			h[6] = st(Items.EXPERIENCE_BOTTLE, 64);
			h[8] = st(Items.TOTEM_OF_UNDYING, 1);
			v[0] = st(Items.END_CRYSTAL, 64);
			v[1] = st(Items.END_CRYSTAL, 64);
			v[2] = st(Item.getItemFromBlock(Blocks.OBSIDIAN), 64);
			v[3] = st(Item.getItemFromBlock(Blocks.OBSIDIAN), 64);
			for (int i = 9; i < 17; i++) v[i] = st(Items.TOTEM_OF_UNDYING, 1);
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

	/** Clears the player, heals them and gives the kit. */
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

	// ---------------------------------------------------------------- teleports and arena reset

	public static void teleport(EntityPlayer p, double x, double y, double z, float yaw) {
		p.dismountRidingEntity();
		p.fallDistance = 0.0F;
		if (p instanceof EntityPlayerMP && ((EntityPlayerMP) p).connection != null) {
			((EntityPlayerMP) p).connection.setPlayerLocation(x, y, z, yaw, 0.0F);
		} else {
			p.setLocationAndAngles(x, y, z, yaw, 0.0F);
		}
		p.motionX = p.motionY = p.motionZ = 0.0D;
	}

	public static void toSpawn(EntityPlayer p) {
		teleport(p, 0.5D, TCArena.GROUND + 1, 0.5D, 180.0F);
	}

	/** Puts the arena back to how it was built: blocks broken or placed, and dropped things cleared. */
	public static void resetArena(WorldServer w, TCArena.Arena a) {
		int m = 3;
		BlockPos pos = new BlockPos();
		IBlockState air = Blocks.AIR.getDefaultState();
		for (int x = a.cx - a.half - m; x <= a.cx + a.half + m; x++) {
			for (int z = a.cz - a.half - m; z <= a.cz + a.half + m; z++) {
				for (int y = 59; y <= TCArena.GROUND + 24; y++) {
					IBlockState want = TCArena.blockAt(x, y, z);
					if (want == null) {
						want = air;
					}
					pos.setPos(x, y, z);
					if (w.getBlockState(pos) != want) {
						w.setBlockState(pos, want, 2);
					}
				}
			}
		}
		AxisAlignedBB box = new AxisAlignedBB(a.cx - a.half - m, 0, a.cz - a.half - m, a.cx + a.half + m + 1, 256,
				a.cz + a.half + m + 1);
		List<Entity> list = w.getEntitiesWithinAABB(Entity.class, box);
		for (int i = 0, n = list.size(); i < n; i++) {
			Entity e = list.get(i);
			if (!(e instanceof EntityLivingBase)) {
				e.setDead(); // items, crystals, minecarts, TNT, arrows, fireworks, wind charges
			}
		}
	}
}
