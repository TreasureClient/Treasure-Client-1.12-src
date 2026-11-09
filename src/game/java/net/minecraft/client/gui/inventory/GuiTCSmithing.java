package net.minecraft.client.gui.inventory;

import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ContainerTCSmithing;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;

/**
 * Treasure Client: the smithing table screen (see {@link ContainerTCSmithing}).
 * Built from pieces of the 1.12 menu textures: the dispenser menu for the panel and
 * your inventory, its slot frame, the crafting table's result frame and the furnace arrow.
 */
public class GuiTCSmithing extends GuiContainer {
	private static final ResourceLocation PANEL = new ResourceLocation("textures/gui/container/dispenser.png");
	private static final ResourceLocation CRAFTING = new ResourceLocation(
			"textures/gui/container/crafting_table.png");
	private static final ResourceLocation FURNACE = new ResourceLocation("textures/gui/container/furnace.png");

	private final InventoryPlayer playerInventory;
	private final ContainerTCSmithing smithing;

	public GuiTCSmithing(InventoryPlayer playerInv, int inputs) {
		this(playerInv, new ContainerTCSmithing(playerInv, inputs));
	}

	private GuiTCSmithing(InventoryPlayer playerInv, ContainerTCSmithing c) {
		super(c);
		this.playerInventory = playerInv;
		this.smithing = c;
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

	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		String s = "Upgrade Gear";
		this.fontRendererObj.drawString(s, this.xSize / 2 - this.fontRendererObj.getStringWidth(s) / 2, 6, 4210752);
		if (this.smithing.inputs == 2) {
			this.fontRendererObj.drawString("+", 56, 51, 4210752);
		}
		this.fontRendererObj.drawString(this.playerInventory.getDisplayName().getUnformattedText(), 8,
				this.ySize - 96 + 2, 4210752);
	}

	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
		int i = this.guiLeft;
		int j = this.guiTop;
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		this.mc.getTextureManager().bindTexture(PANEL);
		this.drawTexturedModalRect(i, j, 0, 0, this.xSize, this.ySize);
		// clear the dispenser's 3x3 grid, then the smithing slots on the plain panel
		drawRect(i + 60, j + 15, i + 118, j + 73, 0xFFC6C6C6);
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
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
			this.drawTexturedModalRect(i + 102, j + 47, 79, 34, 24, 17);
		}
	}
}
