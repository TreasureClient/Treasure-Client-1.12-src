package net.minecraft.client.network;

import net.lax1dude.eaglercraft.EagRuntime;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiTCSmithing;
import net.minecraft.util.math.BlockPos;

/**
 * Treasure Client: smithing tables on modern servers.
 *
 * ViaBackwards shows a smithing table as a crafting table and throws away the
 * server's "open the smithing menu" message, because a 1.12 client has no such
 * menu. The server still opens it on its side and sends what is in it. So when
 * you right-click a crafting table look-alike and, instead of a menu opening, the
 * contents of a 40-slot menu (4 smithing slots + your 36) arrive for a menu we
 * don't have, that was a smithing table: our own smithing screen opens with the
 * server's menu number, and every click goes to the server like a modern client's.
 * (1.16 to 1.19 servers: 39 slots, the older 3-slot smithing menu.)
 */
public final class TCSmithing {

	private TCSmithing() {
	}

	private static BlockPos clickPos;
	private static long clickTime;
	private static boolean pending;

	/** You right-clicked this block (and it gets used, not built on). */
	public static void noteClick(BlockPos pos, IBlockState st) {
		pending = pos != null && TCViaGuess.isSmithingCandidate(st);
		if (pending) {
			clickPos = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
			clickTime = EagRuntime.steadyTimeMillis();
		}
	}

	/** The server opened an ordinary menu. */
	public static void onOpenWindow(String guiId) {
		if (pending && clickPos != null && "minecraft:crafting_table".equals(guiId)) {
			TCViaGuess.forgetSmithing(Minecraft.getMinecraft().world, clickPos); // a real crafting table
		}
		pending = false;
	}

	/** Menu contents arrived; opens the smithing screen when they belong to the hidden menu. */
	public static void onWindowItems(int windowId, int size) {
		if (!pending || windowId <= 0) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.player == null || mc.world == null) {
			return;
		}
		if (EagRuntime.steadyTimeMillis() - clickTime > 3000L) {
			pending = false;
			return;
		}
		if (mc.player.openContainer != mc.player.inventoryContainer && mc.player.openContainer.windowId == windowId) {
			return;
		}
		int inputs = size - 37;
		if (inputs != 3 && inputs != 2) {
			return;
		}
		if (mc.currentScreen != null && mc.currentScreen.doesGuiPauseGame()) {
			return;
		}
		pending = false;
		GuiTCSmithing g = new GuiTCSmithing(mc.player.inventory, inputs);
		mc.displayGuiScreen(g);
		mc.player.openContainer = g.inventorySlots;
		mc.player.openContainer.windowId = windowId;
		TCViaGuess.rememberSmithing(mc.world, clickPos);
	}
}
