package net.minecraft.client.renderer;

import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.TreasureMods;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.item.ItemSpear;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.EnumHand;

/**
 * Treasure Client: 1.7 Animations module. Visual only, nothing extra is sent
 * to the server.
 *
 * <ul>
 * <li>Sword blocking (1.8 servers only): holding right-click with a sword shows the
 * 1.7/1.8 block pose (first and third person). Swinging while blocking gives block-hitting.</li>
 * <li>No dip: the held item doesn't lower after each hit (attack cooldown) or
 * when the server updates it (durability changes).</li>
 * <li>Swing while using: the hand can punch while eating, drinking, drawing a
 * bow or holding up a shield.</li>
 * <li>Red armor: armor turns red with the player when hurt.</li>
 * </ul>
 */
public final class TCOldAnimations {

	private TCOldAnimations() {
	}

	public static boolean blockOn() {
		return TreasureMods.oldAnim() && TreasureMods.oaBlock();
	}

	public static boolean noDipOn() {
		return TreasureMods.oldAnim() && TreasureMods.oaNoDip();
	}

	public static boolean useSwingOn() {
		return TreasureMods.oldAnim() && TreasureMods.oaUseSwing();
	}

	public static boolean armorOn() {
		return TreasureMods.oldAnim() && TreasureMods.oaArmor();
	}

	/** True while the local player holds right-click with a sword (and isn't using something else). */
	public static boolean isSwordBlocking(EntityLivingBase e) {
		if (!blockOn()) {
			return false;
		}
		Minecraft mc = Minecraft.getMinecraft();
		EntityPlayerSP p = mc.player;
		if (e != p || p == null || mc.currentScreen != null || p.isHandActive() || p.isSpectator()) {
			return false;
		}
		ItemStack s = p.getHeldItemMainhand();
		return !s.func_190926_b() && s.getItem() instanceof ItemSword && !(s.getItem() instanceof ItemSpear)
				&& mc.gameSettings.keyBindUseItem.isKeyDown() && isLegacyCombatServer(p);
	}

	/**
	 * True on 1.8 servers, where swords could block. ViaVersion gives the player an
	 * attack speed of 20 when it connects us to a 1.8 server (no attack cooldown);
	 * 1.9+ servers use 4. 1.8-style PvP servers that remove the cooldown also set
	 * it high, and those allow block-hitting too.
	 */
	public static boolean isLegacyCombatServer(EntityPlayerSP p) {
		IAttributeInstance speed = p.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED);
		return speed != null && speed.getBaseValue() >= 16.0D;
	}

	/** The 1.7/1.8 sword block pose for the first-person item (side: 1 = right hand, -1 = left). */
	public static void swordBlockFirstPerson(int side) {
		GlStateManager.translate((float) side * -0.14142136F, 0.08F, 0.14142136F);
		GlStateManager.rotate(-102.25F, 1.0F, 0.0F, 0.0F);
		GlStateManager.rotate((float) side * 13.365F, 0.0F, 1.0F, 0.0F);
		GlStateManager.rotate((float) side * 78.05F, 0.0F, 0.0F, 1.0F);
	}

	/** Starts a hand swing on our screen only (no packet), for punching while using an item. */
	public static void localSwing(EntityPlayerSP p) {
		if (!p.isSwingInProgress || p.swingProgressInt >= 3 || p.swingProgressInt < 0) {
			p.swingProgressInt = -1;
			p.isSwingInProgress = true;
			p.swingingHand = EnumHand.MAIN_HAND;
		}
	}
}
