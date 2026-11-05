package net.minecraft.client.gui;

import io.netty.buffer.Unpooled;
import java.io.IOException;

import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ContainerMerchant;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GuiMerchant extends GuiContainer {
	private static final Logger LOGGER = LogManager.getLogger();

	/** The GUI texture for the villager merchant GUI. */
	private static final ResourceLocation MERCHANT_GUI_TEXTURE = new ResourceLocation(
			"textures/gui/container/villager.png");

	/** The current IMerchant instance in use for this specific merchant. */
	private final IMerchant merchant;

	/** The button which proceeds to the next available merchant recipe. */
	private GuiMerchant.MerchantButton nextButton;

	/** Returns to the previous Merchant recipe if one is applicable. */
	private GuiMerchant.MerchantButton previousButton;

	/**
	 * The integer value corresponding to the currently selected merchant recipe.
	 */
	private int selectedMerchantRecipe;

	/** The chat component utilized by this GuiMerchant instance. */
	private final ITextComponent chatComponent;

	// Treasure Client: modern (1.14+) trading screen. All trades are listed on the left as
	// buttons (7 at a time, scrollable); clicking one selects it. On 1.14+ servers the server
	// then moves the needed items into the slots itself. Setting "Modern Villager Trading".
	private static final ResourceLocation TC_BG = new ResourceLocation("textures/gui/container/villager2.png");
	private static final ResourceLocation TC_ARROW = new ResourceLocation(
			"textures/gui/container/villager2/trade_arrow.png");
	private static final ResourceLocation TC_ARROW_OUT = new ResourceLocation(
			"textures/gui/container/villager2/trade_arrow_out_of_stock.png");
	private static final ResourceLocation TC_OUT_OF_STOCK = new ResourceLocation(
			"textures/gui/container/villager2/out_of_stock.png");
	private static final ResourceLocation TC_SCROLLER = new ResourceLocation(
			"textures/gui/container/villager2/scroller.png");
	private static final ResourceLocation TC_SCROLLER_OFF = new ResourceLocation(
			"textures/gui/container/villager2/scroller_disabled.png");
	private static final ResourceLocation TC_WIDGETS = new ResourceLocation("textures/gui/widgets.png");
	private static final int TC_ROWS = 7;
	private final boolean tcModern;
	private int tcScroll;
	private boolean tcDragging;

	public GuiMerchant(InventoryPlayer p_i45500_1_, IMerchant p_i45500_2_, World worldIn) {
		super(new ContainerMerchant(p_i45500_1_, p_i45500_2_, worldIn));
		this.merchant = p_i45500_2_;
		this.chatComponent = p_i45500_2_.getDisplayName();
		Minecraft mc = Minecraft.getMinecraft();
		this.tcModern = net.minecraft.client.gui.TreasureMods.viaTrade() && mc != null && !mc.isSingleplayer();
		if (this.tcModern) {
			this.xSize = 276;
			// slots move to where the modern screen has them
			java.util.List<net.minecraft.inventory.Slot> slots = this.inventorySlots.inventorySlots;
			for (int k = 0; k < slots.size(); ++k) {
				net.minecraft.inventory.Slot sl = slots.get(k);
				if (k == 0) {
					sl.xDisplayPosition = 136;
					sl.yDisplayPosition = 37;
				} else if (k == 1) {
					sl.xDisplayPosition = 162;
					sl.yDisplayPosition = 37;
				} else if (k == 2) {
					sl.xDisplayPosition = 220;
					sl.yDisplayPosition = 37;
				} else {
					sl.xDisplayPosition += 100;
				}
			}
		}
	}

	/**
	 * Adds the buttons (and other controls) to the screen in question. Called when
	 * the GUI is displayed and when the window resizes, the buttonList is cleared
	 * beforehand.
	 */
	public void initGui() {
		super.initGui();
		int i = (this.width - this.xSize) / 2;
		int j = (this.height - this.ySize) / 2;
		this.nextButton = (GuiMerchant.MerchantButton) this
				.addButton(new GuiMerchant.MerchantButton(1, i + 120 + 27, j + 24 - 1, true));
		this.previousButton = (GuiMerchant.MerchantButton) this
				.addButton(new GuiMerchant.MerchantButton(2, i + 36 - 19, j + 24 - 1, false));
		this.nextButton.enabled = false;
		this.previousButton.enabled = false;
		if (this.tcModern) {
			this.nextButton.visible = false;
			this.previousButton.visible = false;
		}
	}

	/**
	 * Draw the foreground layer for the GuiContainer (everything in front of the
	 * items)
	 */
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		if (this.tcModern) {
			String t = this.chatComponent.getUnformattedText();
			this.fontRendererObj.drawString(t, 49 + this.xSize / 2 - this.fontRendererObj.getStringWidth(t) / 2, 6,
					4210752);
			String tr = I18n.hasKey("merchant.trades") ? I18n.format("merchant.trades") : "Trades";
			this.fontRendererObj.drawString(tr, 53 - this.fontRendererObj.getStringWidth(tr) / 2, 6, 4210752);
			this.fontRendererObj.drawString(I18n.format("container.inventory"), 107, this.ySize - 94, 4210752);
			return;
		}
		String s = this.chatComponent.getUnformattedText();
		this.fontRendererObj.drawString(s, this.xSize / 2 - this.fontRendererObj.getStringWidth(s) / 2, 6, 4210752);
		this.fontRendererObj.drawString(I18n.format("container.inventory"), 8, this.ySize - 96 + 2, 4210752);
	}

	/**
	 * Called from the main game loop to update the screen.
	 */
	public void updateScreen() {
		super.updateScreen();
		MerchantRecipeList merchantrecipelist = this.merchant.getRecipes(this.mc.player);

		if (this.tcModern) {
			this.tcScroll = Math.max(0, Math.min(this.tcScroll, this.tcMaxScroll()));
			return;
		}

		if (merchantrecipelist != null) {
			this.nextButton.enabled = this.selectedMerchantRecipe < merchantrecipelist.size() - 1;
			this.previousButton.enabled = this.selectedMerchantRecipe > 0;
		}
	}

	/**
	 * Called by the controls from the buttonList when activated. (Mouse pressed for
	 * buttons)
	 */
	protected void actionPerformed(GuiButton button) throws IOException {
		boolean flag = false;

		if (button == this.nextButton) {
			++this.selectedMerchantRecipe;
			MerchantRecipeList merchantrecipelist = this.merchant.getRecipes(this.mc.player);

			if (merchantrecipelist != null && this.selectedMerchantRecipe >= merchantrecipelist.size()) {
				this.selectedMerchantRecipe = merchantrecipelist.size() - 1;
			}

			flag = true;
		} else if (button == this.previousButton) {
			--this.selectedMerchantRecipe;

			if (this.selectedMerchantRecipe < 0) {
				this.selectedMerchantRecipe = 0;
			}

			flag = true;
		}

		if (flag) {
			this.tcSendSelection();
		}
	}

	private void tcSendSelection() {
		((ContainerMerchant) this.inventorySlots).setCurrentRecipeIndex(this.selectedMerchantRecipe);
		PacketBuffer packetbuffer = new PacketBuffer(Unpooled.buffer());
		packetbuffer.writeInt(this.selectedMerchantRecipe);
		this.mc.getConnection().sendPacket(new CPacketCustomPayload("MC|TrSel", packetbuffer));
	}

	private int tcTradeCount() {
		MerchantRecipeList l = this.merchant.getRecipes(this.mc.player);
		return l == null ? 0 : l.size();
	}

	private int tcMaxScroll() {
		return Math.max(0, this.tcTradeCount() - TC_ROWS);
	}

	/** Trade row under the mouse (0-6), or -1. */
	private int tcRowAt(int mouseX, int mouseY) {
		int x = this.guiLeft + 5, y = this.guiTop + 18;
		if (mouseX < x || mouseX >= x + 88 || mouseY < y || mouseY >= y + TC_ROWS * 20) {
			return -1;
		}
		int r = (mouseY - y) / 20;
		return r + this.tcScroll < this.tcTradeCount() ? r : -1;
	}

	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		if (this.tcModern && mouseButton == 0) {
			int r = this.tcRowAt(mouseX, mouseY);
			if (r >= 0) {
				this.selectedMerchantRecipe = r + this.tcScroll;
				this.mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord
						.getMasterRecord(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.0F));
				this.tcSendSelection();
				return;
			}
			int sx = this.guiLeft + 94, sy = this.guiTop + 18;
			if (this.tcMaxScroll() > 0 && mouseX >= sx && mouseX < sx + 6 && mouseY >= sy && mouseY < sy + 139) {
				this.tcDragging = true;
				this.tcDragTo(mouseY);
				return;
			}
		}
		super.mouseClicked(mouseX, mouseY, mouseButton);
	}

	private void tcDragTo(int mouseY) {
		int top = this.guiTop + 18, bottom = top + 139, max = this.tcMaxScroll();
		float f = ((float) mouseY - (float) top - 13.5F) / ((float) (bottom - top) - 27.0F);
		f = f * (float) max + 0.5F;
		this.tcScroll = Math.max(0, Math.min((int) f, max));
	}

	protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
		if (this.tcDragging) {
			this.tcDragTo(mouseY);
			return;
		}
		super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
	}

	protected void mouseReleased(int mouseX, int mouseY, int state) {
		if (this.tcDragging) {
			this.tcDragging = false;
			return;
		}
		super.mouseReleased(mouseX, mouseY, state);
	}

	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		if (this.tcModern) {
			int d = net.lax1dude.eaglercraft.Mouse.getEventDWheel();
			if (d != 0 && this.tcMaxScroll() > 0) {
				this.tcScroll = Math.max(0, Math.min(this.tcScroll - (d > 0 ? 1 : -1), this.tcMaxScroll()));
			}
		}
	}

	private void tcDrawBackground() {
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		this.mc.getTextureManager().bindTexture(TC_BG);
		int i = this.guiLeft, j = this.guiTop;
		drawModalRectWithCustomSizedTexture(i, j, 0.0F, 0.0F, this.xSize, this.ySize, 512.0F, 256.0F);
		MerchantRecipeList list = this.merchant.getRecipes(this.mc.player);
		if (list != null && this.selectedMerchantRecipe >= 0 && this.selectedMerchantRecipe < list.size()
				&& ((MerchantRecipe) list.get(this.selectedMerchantRecipe)).isRecipeDisabled()) {
			this.mc.getTextureManager().bindTexture(TC_OUT_OF_STOCK);
			drawModalRectWithCustomSizedTexture(i + 182, j + 35, 0.0F, 0.0F, 28, 21, 28.0F, 21.0F);
		}
		// scroll bar (same maths as the modern game)
		int n = list == null ? 0 : list.size();
		int k = n + 1 - TC_ROWS;
		if (k > 1) {
			int l = 139 - (27 + (k - 1) * 139 / k);
			int step = 1 + l / k + 139 / k;
			int y = Math.min(113, this.tcScroll * step);
			if (this.tcScroll == k - 1) {
				y = 113;
			}
			this.mc.getTextureManager().bindTexture(TC_SCROLLER);
			drawModalRectWithCustomSizedTexture(i + 94, j + 18 + y, 0.0F, 0.0F, 6, 27, 6.0F, 27.0F);
		} else {
			this.mc.getTextureManager().bindTexture(TC_SCROLLER_OFF);
			drawModalRectWithCustomSizedTexture(i + 94, j + 18, 0.0F, 0.0F, 6, 27, 6.0F, 27.0F);
		}
	}

	/** The trade buttons on the left, with their items and arrows; returns the hovered stack for a tooltip. */
	private ItemStack tcDrawTrades(int mouseX, int mouseY) {
		MerchantRecipeList list = this.merchant.getRecipes(this.mc.player);
		if (list == null || list.isEmpty()) {
			return ItemStack.field_190927_a;
		}
		int i = this.guiLeft, j = this.guiTop;
		int hoverRow = this.tcRowAt(mouseX, mouseY);
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.blendFunc(770, 771);
		for (int r = 0; r < TC_ROWS && r + this.tcScroll < list.size(); ++r) {
			int bx = i + 5, by = j + 18 + r * 20;
			int v = 46 + (r == hoverRow ? 2 : 1) * 20;
			this.mc.getTextureManager().bindTexture(TC_WIDGETS);
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			this.drawTexturedModalRect(bx, by, 0, v, 44, 20);
			this.drawTexturedModalRect(bx + 44, by, 200 - 44, v, 44, 20);
		}
		for (int r = 0; r < TC_ROWS && r + this.tcScroll < list.size(); ++r) {
			MerchantRecipe rec = (MerchantRecipe) list.get(r + this.tcScroll);
			int y = j + 19 + r * 20;
			this.mc.getTextureManager().bindTexture(rec.isRecipeDisabled() ? TC_ARROW_OUT : TC_ARROW);
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			drawModalRectWithCustomSizedTexture(i + 60, y + 3, 0.0F, 0.0F, 10, 9, 10.0F, 9.0F);
		}
		ItemStack hovered = ItemStack.field_190927_a;
		GlStateManager.pushMatrix();
		RenderHelper.enableGUIStandardItemLighting();
		GlStateManager.disableLighting();
		GlStateManager.enableRescaleNormal();
		GlStateManager.enableColorMaterial();
		GlStateManager.enableLighting();
		this.itemRender.zLevel = 100.0F;
		for (int r = 0; r < TC_ROWS && r + this.tcScroll < list.size(); ++r) {
			MerchantRecipe rec = (MerchantRecipe) list.get(r + this.tcScroll);
			int y = j + 19 + r * 20;
			ItemStack a = rec.getItemToBuy(), b = rec.getSecondItemToBuy(), out = rec.getItemToSell();
			this.itemRender.renderItemAndEffectIntoGUI(a, i + 10, y);
			this.itemRender.renderItemOverlays(this.fontRendererObj, a, i + 10, y);
			if (!b.func_190926_b()) {
				this.itemRender.renderItemAndEffectIntoGUI(b, i + 40, y);
				this.itemRender.renderItemOverlays(this.fontRendererObj, b, i + 40, y);
			}
			this.itemRender.renderItemAndEffectIntoGUI(out, i + 73, y);
			this.itemRender.renderItemOverlays(this.fontRendererObj, out, i + 73, y);
			if (r == hoverRow) {
				int rx = mouseX - (i + 5);
				if (rx < 20) {
					hovered = a;
				} else if (rx > 30 && rx < 50 && !b.func_190926_b()) {
					hovered = b;
				} else if (rx > 65) {
					hovered = out;
				}
			}
		}
		this.itemRender.zLevel = 0.0F;
		GlStateManager.disableLighting();
		GlStateManager.popMatrix();
		GlStateManager.enableLighting();
		GlStateManager.enableDepth();
		RenderHelper.enableStandardItemLighting();
		return hovered;
	}

	/**
	 * Draws the background layer of this container (behind the items).
	 */
	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
		if (this.tcModern) {
			this.tcDrawBackground();
			return;
		}
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		this.mc.getTextureManager().bindTexture(MERCHANT_GUI_TEXTURE);
		int i = (this.width - this.xSize) / 2;
		int j = (this.height - this.ySize) / 2;
		this.drawTexturedModalRect(i, j, 0, 0, this.xSize, this.ySize);
		MerchantRecipeList merchantrecipelist = this.merchant.getRecipes(this.mc.player);

		if (merchantrecipelist != null && !merchantrecipelist.isEmpty()) {
			int k = this.selectedMerchantRecipe;

			if (k < 0 || k >= merchantrecipelist.size()) {
				return;
			}

			MerchantRecipe merchantrecipe = (MerchantRecipe) merchantrecipelist.get(k);

			if (merchantrecipe.isRecipeDisabled()) {
				this.mc.getTextureManager().bindTexture(MERCHANT_GUI_TEXTURE);
				GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
				GlStateManager.disableLighting();
				this.drawTexturedModalRect(this.guiLeft + 83, this.guiTop + 21, 212, 0, 28, 21);
				this.drawTexturedModalRect(this.guiLeft + 83, this.guiTop + 51, 212, 0, 28, 21);
			}
		}
	}

	/**
	 * Draws the screen and all the components in it.
	 */
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		if (this.tcModern) {
			this.drawDefaultBackground();
			super.drawScreen(mouseX, mouseY, partialTicks);
			ItemStack hov = this.tcDrawTrades(mouseX, mouseY);
			if (!hov.func_190926_b()) {
				this.renderToolTip(hov, mouseX, mouseY);
			} else {
				MerchantRecipeList l = this.merchant.getRecipes(this.mc.player);
				if (l != null && this.selectedMerchantRecipe >= 0 && this.selectedMerchantRecipe < l.size()
						&& ((MerchantRecipe) l.get(this.selectedMerchantRecipe)).isRecipeDisabled()
						&& this.isPointInRegion(182, 35, 28, 21, mouseX, mouseY)) {
					this.drawCreativeTabHoveringText(I18n.format("merchant.deprecated"), mouseX, mouseY);
				}
			}
			this.func_191948_b(mouseX, mouseY);
			return;
		}
		this.drawDefaultBackground();
		super.drawScreen(mouseX, mouseY, partialTicks);
		MerchantRecipeList merchantrecipelist = this.merchant.getRecipes(this.mc.player);

		if (merchantrecipelist != null && !merchantrecipelist.isEmpty()) {
			int i = (this.width - this.xSize) / 2;
			int j = (this.height - this.ySize) / 2;
			int k = this.selectedMerchantRecipe;
			MerchantRecipe merchantrecipe = (MerchantRecipe) merchantrecipelist.get(k);
			ItemStack itemstack = merchantrecipe.getItemToBuy();
			ItemStack itemstack1 = merchantrecipe.getSecondItemToBuy();
			ItemStack itemstack2 = merchantrecipe.getItemToSell();
			GlStateManager.pushMatrix();
			RenderHelper.enableGUIStandardItemLighting();
			GlStateManager.disableLighting();
			GlStateManager.enableRescaleNormal();
			GlStateManager.enableColorMaterial();
			GlStateManager.enableLighting();
			this.itemRender.zLevel = 100.0F;
			this.itemRender.renderItemAndEffectIntoGUI(itemstack, i + 36, j + 24);
			this.itemRender.renderItemOverlays(this.fontRendererObj, itemstack, i + 36, j + 24);

			if (!itemstack1.func_190926_b()) {
				this.itemRender.renderItemAndEffectIntoGUI(itemstack1, i + 62, j + 24);
				this.itemRender.renderItemOverlays(this.fontRendererObj, itemstack1, i + 62, j + 24);
			}

			this.itemRender.renderItemAndEffectIntoGUI(itemstack2, i + 120, j + 24);
			this.itemRender.renderItemOverlays(this.fontRendererObj, itemstack2, i + 120, j + 24);
			this.itemRender.zLevel = 0.0F;
			GlStateManager.disableLighting();

			if (this.isPointInRegion(36, 24, 16, 16, mouseX, mouseY) && !itemstack.func_190926_b()) {
				this.renderToolTip(itemstack, mouseX, mouseY);
			} else if (!itemstack1.func_190926_b() && this.isPointInRegion(62, 24, 16, 16, mouseX, mouseY)
					&& !itemstack1.func_190926_b()) {
				this.renderToolTip(itemstack1, mouseX, mouseY);
			} else if (!itemstack2.func_190926_b() && this.isPointInRegion(120, 24, 16, 16, mouseX, mouseY)
					&& !itemstack2.func_190926_b()) {
				this.renderToolTip(itemstack2, mouseX, mouseY);
			} else if (merchantrecipe.isRecipeDisabled() && (this.isPointInRegion(83, 21, 28, 21, mouseX, mouseY)
					|| this.isPointInRegion(83, 51, 28, 21, mouseX, mouseY))) {
				this.drawCreativeTabHoveringText(I18n.format("merchant.deprecated"), mouseX, mouseY);
			}

			GlStateManager.popMatrix();
			GlStateManager.enableLighting();
			GlStateManager.enableDepth();
			RenderHelper.enableStandardItemLighting();
		}

		this.func_191948_b(mouseX, mouseY);
	}

	public IMerchant getMerchant() {
		return this.merchant;
	}

	static class MerchantButton extends GuiButton {
		private final boolean forward;

		public MerchantButton(int buttonID, int x, int y, boolean p_i1095_4_) {
			super(buttonID, x, y, 12, 19, "");
			this.forward = p_i1095_4_;
		}

		public void func_191745_a(Minecraft p_191745_1_, int p_191745_2_, int p_191745_3_, float p_191745_4_) {
			if (this.visible) {
				p_191745_1_.getTextureManager().bindTexture(GuiMerchant.MERCHANT_GUI_TEXTURE);
				GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
				boolean flag = p_191745_2_ >= this.xPosition && p_191745_3_ >= this.yPosition
						&& p_191745_2_ < this.xPosition + this.width && p_191745_3_ < this.yPosition + this.height;
				int i = 0;
				int j = 176;

				if (!this.enabled) {
					j += this.width * 2;
				} else if (flag) {
					j += this.width;
				}

				if (!this.forward) {
					i += this.height;
				}

				this.drawTexturedModalRect(this.xPosition, this.yPosition, j, i, this.width, this.height);
			}
		}
	}
}
