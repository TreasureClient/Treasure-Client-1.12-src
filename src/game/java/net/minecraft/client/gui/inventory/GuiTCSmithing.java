package net.minecraft.client.gui.inventory;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerTCSmithing;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ViaItems;
import net.minecraft.util.ResourceLocation;

/**
 * Treasure Client: the smithing table screen (see {@link ContainerTCSmithing}),
 * laid out like the modern one: the table and "Upgrade Gear" at the top, faded hints
 * of what goes in each empty slot, a red cross when the items don't combine, and an
 * armor stand on the right wearing or holding the result.
 *
 * Drawn from 1.12 pieces (the dispenser menu for the panel and your inventory,
 * its slot frame, the crafting table's result frame and the furnace arrow), since
 * 1.12 has no smithing menu picture.
 */
public class GuiTCSmithing extends GuiContainer {
	private static final ResourceLocation PANEL = new ResourceLocation("textures/gui/container/dispenser.png");
	private static final ResourceLocation CRAFTING = new ResourceLocation(
			"textures/gui/container/crafting_table.png");
	private static final ResourceLocation FURNACE = new ResourceLocation("textures/gui/container/furnace.png");

	private static final int PANEL_COLOR = 0xFFC6C6C6;

	private final InventoryPlayer playerInventory;
	private final ContainerTCSmithing smithing;
	private EntityArmorStand stand;
	private ItemStack shownOnStand = ItemStack.field_190927_a;

	private ItemStack[][] hints;
	private ItemStack icon;

	public GuiTCSmithing(InventoryPlayer playerInv, int inputs) {
		this(playerInv, new ContainerTCSmithing(playerInv, inputs));
	}

	private GuiTCSmithing(InventoryPlayer playerInv, ContainerTCSmithing c) {
		super(c);
		this.playerInventory = playerInv;
		this.smithing = c;
	}

	private static void add(java.util.List<ItemStack> l, Item it) {
		if (it != null) {
			l.add(new ItemStack(it));
		}
	}

	/** What each empty input slot shows, faded, one after another. */
	private ItemStack[][] hints() {
		if (this.hints == null) {
			java.util.List<ItemStack> template = new java.util.ArrayList<ItemStack>();
			add(template, ViaItems.getByName("netherite_upgrade_smithing_template"));
			add(template, ViaItems.getByName("sentry_armor_trim_smithing_template"));
			add(template, ViaItems.getByName("coast_armor_trim_smithing_template"));
			add(template, ViaItems.getByName("wild_armor_trim_smithing_template"));
			java.util.List<ItemStack> base = new java.util.ArrayList<ItemStack>();
			add(base, Items.DIAMOND_HELMET);
			add(base, Items.DIAMOND_SWORD);
			add(base, Items.DIAMOND_CHESTPLATE);
			add(base, Items.DIAMOND_PICKAXE);
			add(base, Items.DIAMOND_LEGGINGS);
			add(base, Items.DIAMOND_AXE);
			add(base, Items.DIAMOND_BOOTS);
			add(base, Items.DIAMOND_SHOVEL);
			java.util.List<ItemStack> addition = new java.util.ArrayList<ItemStack>();
			add(addition, ViaItems.getByName("netherite_ingot"));
			if (this.smithing.inputs == 3) {
				add(addition, Items.IRON_INGOT);
				add(addition, Items.GOLD_INGOT);
				add(addition, Items.DIAMOND);
				add(addition, Items.EMERALD);
				add(addition, ViaItems.getByName("amethyst_shard"));
				add(addition, ViaItems.getByName("copper_ingot"));
				add(addition, Items.QUARTZ);
				add(addition, Items.REDSTONE);
			}
			ItemStack[] t = template.toArray(new ItemStack[0]);
			ItemStack[] b = base.toArray(new ItemStack[0]);
			ItemStack[] a = addition.toArray(new ItemStack[0]);
			this.hints = this.smithing.inputs == 3 ? new ItemStack[][] { t, b, a } : new ItemStack[][] { b, a };
		}
		return this.hints;
	}

	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();
		super.drawScreen(mouseX, mouseY, partialTicks);
		this.func_191948_b(mouseX, mouseY);
		// what goes in an empty slot, like the modern menu says
		if (this.mc.player.inventory.getItemStack().func_190926_b()) {
			for (int i = 0; i < this.smithing.inputs; ++i) {
				Slot s = this.smithing.inventorySlots.get(i);
				int x = this.guiLeft + s.xDisplayPosition, y = this.guiTop + s.yDisplayPosition;
				if (!s.getHasStack() && mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) {
					this.drawCreativeTabHoveringText(hint(i), mouseX, mouseY);
				}
			}
		}
	}

	private String hint(int i) {
		if (this.smithing.inputs == 3) {
			return i == 0 ? "Add Smithing Template" : i == 1 ? "Add Armor, Weapon or Tool" : "Add Ingot or Crystal";
		}
		return i == 0 ? "Add Armor, Weapon or Tool" : "Add Netherite Ingot";
	}

	/** Every input filled but no result: the items don't combine. */
	private boolean showError() {
		int n = this.smithing.inputs;
		for (int i = 0; i < n; ++i) {
			if (!this.smithing.inventorySlots.get(i).getHasStack()) {
				return false;
			}
		}
		return !this.smithing.inventorySlots.get(n).getHasStack();
	}

	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		// the smithing table, top left, then the title beside it
		if (this.icon == null) {
			net.minecraft.block.Block b = net.minecraft.block.Block.getBlockFromName("smithing_table");
			Item it = b == null ? null : Item.getItemFromBlock(b);
			this.icon = it == null || it == Items.AIR ? ItemStack.field_190927_a : new ItemStack(it);
		}
		if (!this.icon.func_190926_b()) {
			RenderHelper.enableGUIStandardItemLighting();
			this.itemRender.renderItemAndEffectIntoGUI(this.icon, 20, 11);
			RenderHelper.disableStandardItemLighting();
		}
		this.fontRendererObj.drawString("Upgrade Gear", 44, 15, 4210752);
		this.fontRendererObj.drawString(this.playerInventory.getDisplayName().getUnformattedText(), 8,
				this.ySize - 96 + 2, 4210752);
		drawHints();
		if (showError()) {
			drawCross(this.smithing.inputs == 3 ? 72 : 107, 49);
		}
	}

	/** The faded items in the empty input slots, changing every second and a half. */
	private void drawHints() {
		ItemStack[][] h = hints();
		long step = EagRuntime.steadyTimeMillis() / 1500L;
		for (int i = 0; i < this.smithing.inputs && i < h.length; ++i) {
			Slot s = this.smithing.inventorySlots.get(i);
			if (s.getHasStack() || h[i].length == 0) {
				continue;
			}
			ItemStack it = h[i][(int) (step % h[i].length)];
			RenderHelper.enableGUIStandardItemLighting();
			this.itemRender.renderItemAndEffectIntoGUI(it, s.xDisplayPosition, s.yDisplayPosition);
			RenderHelper.disableStandardItemLighting();
			// fade it into the slot
			GlStateManager.disableDepth();
			Gui.drawRect(s.xDisplayPosition, s.yDisplayPosition, s.xDisplayPosition + 16, s.yDisplayPosition + 16,
					0xB08B8B8B);
			GlStateManager.enableDepth();
		}
	}

	/** The red cross over the arrow. */
	private void drawCross(int x, int y) {
		GlStateManager.disableDepth();
		for (int i = 0; i < 13; ++i) {
			Gui.drawRect(x + i, y + i, x + i + 2, y + i + 2, 0xFFD02020);
			Gui.drawRect(x + 12 - i, y + i, x + 14 - i, y + i + 2, 0xFFD02020);
		}
		GlStateManager.enableDepth();
	}

	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
		int i = this.guiLeft;
		int j = this.guiTop;
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		this.mc.getTextureManager().bindTexture(PANEL);
		this.drawTexturedModalRect(i, j, 0, 0, this.xSize, this.ySize);
		// plain panel above your inventory (no dispenser grid)
		drawRect(i + 7, j + 14, i + 169, j + 80, PANEL_COLOR);

		// input slots and the result
		this.mc.getTextureManager().bindTexture(PANEL);
		int n = this.smithing.inputs;
		for (int k = 0; k < n; ++k) {
			Slot s = this.smithing.inventorySlots.get(k);
			this.drawTexturedModalRect(i + s.xDisplayPosition - 1, j + s.yDisplayPosition - 1, 61, 16, 18, 18);
		}
		Slot out = this.smithing.inventorySlots.get(n);
		this.mc.getTextureManager().bindTexture(CRAFTING);
		this.drawTexturedModalRect(i + out.xDisplayPosition - 5, j + out.yDisplayPosition - 5, 119, 30, 26, 26);
		this.mc.getTextureManager().bindTexture(FURNACE);
		if (n == 3) {
			this.drawTexturedModalRect(i + 67, j + 48, 79, 34, 24, 17);
		} else {
			this.fontRendererObj.drawString("+", i + 56, j + 51, 4210752);
			this.mc.getTextureManager().bindTexture(FURNACE);
			this.drawTexturedModalRect(i + 102, j + 47, 79, 34, 24, 17);
		}

		if (n == 3) {
			drawPreview(i, j);
		}
	}

	/** The armor stand on the right, wearing or holding the result (or what is being upgraded). */
	private void drawPreview(int i, int j) {
		int x0 = i + 124, y0 = j + 16, x1 = i + 167, y1 = j + 78;
		// a sunken box like a big slot
		drawRect(x0, y0, x1, y1, 0xFF8B8B8B);
		drawRect(x0, y0, x1 - 1, y0 + 1, 0xFF373737);
		drawRect(x0, y0, x0 + 1, y1 - 1, 0xFF373737);
		drawRect(x0 + 1, y1 - 1, x1, y1, 0xFFFFFFFF);
		drawRect(x1 - 1, y0 + 1, x1, y1, 0xFFFFFFFF);
		if (this.mc.world == null) {
			return;
		}
		if (this.stand == null) {
			this.stand = new EntityArmorStand(this.mc.world);
			this.stand.setShowArms(true);
			this.stand.setNoBasePlate(true);
		}
		ItemStack show = this.smithing.inventorySlots.get(3).getStack();
		if (show.func_190926_b()) {
			show = this.smithing.inventorySlots.get(1).getStack();
		}
		if (show != this.shownOnStand) {
			this.shownOnStand = show;
			for (EntityEquipmentSlot es : EntityEquipmentSlot.values()) {
				this.stand.setItemStackToSlot(es, ItemStack.field_190927_a);
			}
			if (!show.func_190926_b()) {
				EntityEquipmentSlot es = show.getItem() instanceof ItemArmor ? ((ItemArmor) show.getItem()).armorType
						: EntityEquipmentSlot.MAINHAND;
				this.stand.setItemStackToSlot(es, show.copy());
			}
		}
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		GuiInventory.drawEntityOnScreen(x0 + 21, y1 - 6, 25, -30.0F, -10.0F, this.stand);
	}
}
