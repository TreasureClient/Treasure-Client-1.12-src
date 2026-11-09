package net.minecraft.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * Treasure Client: the smithing table menu of modern servers.
 *
 * ViaBackwards can't show this menu to a 1.12 client, so the server's "open menu"
 * never arrives, but the server still keeps the menu open and talks to it. This is
 * the same menu on our side, slot for slot, so clicks go to the server exactly as a
 * modern client's would and the server does the smithing.
 *
 * 1.20 and newer: template, base, addition, result (4 slots).
 * 1.16 to 1.19: base, addition, result (3 slots).
 */
public class ContainerTCSmithing extends Container {

	public final IInventory table;
	/** Input slots before the result: 3 (1.20+) or 2 (1.16 to 1.19). */
	public final int inputs;

	public ContainerTCSmithing(IInventory playerInventory, int inputs) {
		this.inputs = inputs;
		this.table = new InventoryBasic("Upgrade Gear", false, inputs + 1);
		if (inputs == 3) {
			this.addSlotToContainer(new Slot(table, 0, 8, 48));
			this.addSlotToContainer(new Slot(table, 1, 26, 48));
			this.addSlotToContainer(new Slot(table, 2, 44, 48));
			this.addSlotToContainer(new Result(table, 3, 98, 48));
		} else {
			this.addSlotToContainer(new Slot(table, 0, 27, 47));
			this.addSlotToContainer(new Slot(table, 1, 76, 47));
			this.addSlotToContainer(new Result(table, 2, 134, 47));
		}
		for (int k = 0; k < 3; ++k) {
			for (int i = 0; i < 9; ++i) {
				this.addSlotToContainer(new Slot(playerInventory, i + k * 9 + 9, 8 + i * 18, 84 + k * 18));
			}
		}
		for (int l = 0; l < 9; ++l) {
			this.addSlotToContainer(new Slot(playerInventory, l, 8 + l * 18, 142));
		}
	}

	/** The result slot: you can take from it, never put into it. */
	private static final class Result extends Slot {
		Result(IInventory inv, int index, int x, int y) {
			super(inv, index, x, y);
		}

		public boolean isItemValid(ItemStack stack) {
			return false;
		}
	}

	public boolean canInteractWith(EntityPlayer playerIn) {
		return true; // the server decides when the menu closes
	}

	/** Shift-click (the server does the real move, this only shows it right away). */
	public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
		ItemStack itemstack = ItemStack.field_190927_a;
		Slot slot = this.inventorySlots.get(index);
		int own = this.inputs + 1;
		if (slot != null && slot.getHasStack()) {
			ItemStack itemstack1 = slot.getStack();
			itemstack = itemstack1.copy();
			if (index < own) {
				if (!this.mergeItemStack(itemstack1, own, own + 36, true)) {
					return ItemStack.field_190927_a;
				}
			} else if (!this.mergeItemStack(itemstack1, 0, this.inputs, false)) {
				return ItemStack.field_190927_a;
			}
			if (itemstack1.func_190926_b()) {
				slot.putStack(ItemStack.field_190927_a);
			} else {
				slot.onSlotChanged();
			}
			if (itemstack1.func_190916_E() == itemstack.func_190916_E()) {
				return ItemStack.field_190927_a;
			}
			slot.func_190901_a(playerIn, itemstack1);
		}
		return itemstack;
	}
}
