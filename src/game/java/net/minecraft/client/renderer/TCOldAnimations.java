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
 * <li>Small dip: after a hit the held item lowers only a little and rises back as the
 * attack cooldown refills, and it doesn't replay the equip animation when the
 * server updates it (durability changes).</li>
 * <li>Hit swings: a sweep hit swings the sword sideways, a sprint (knockback) hit is a
 * quick forward stab, so you can tell which one you landed.</li>
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

	public static boolean hitSwingOn() {
		return TreasureMods.oldAnim() && TreasureMods.oaHitSwing();
	}

	/** Kind of the current swing: 0 = normal, 1 = sweep, 2 = sprint hit. */
	public static int swingType;

	public static final int SWING_NORMAL = 0, SWING_SWEEP = 1, SWING_SPRINT = 2;

	/**
	 * Works out, right before the client attacks, whether this hit will be a sweep or a
	 * sprint (knockback) hit, using the same rules as the game's attack code.
	 */
	public static void classifyHit(EntityPlayerSP p) {
		swingType = SWING_NORMAL;
		if (!hitSwingOn()) {
			return;
		}
		boolean charged = p.getCooledAttackStrength(0.5F) > 0.9F;
		if (!charged) {
			return;
		}
		if (p.isSprinting()) {
			swingType = SWING_SPRINT;
			return;
		}
		boolean crit = p.fallDistance > 0.0F && !p.onGround && !p.isOnLadder() && !p.isInWater()
				&& !p.isPotionActive(net.minecraft.init.MobEffects.BLINDNESS) && !p.isRiding();
		double walked = (double) (p.distanceWalkedModified - p.prevDistanceWalkedModified);
		ItemStack s = p.getHeldItemMainhand();
		if (!crit && p.onGround && walked < (double) p.getAIMoveSpeed() && s.getItem() instanceof ItemSword
				&& !(s.getItem() instanceof ItemSpear)) {
			swingType = SWING_SWEEP;
		}
	}

	/** First-person sweep: the blade swings across the screen, lying flat (side: 1 = right hand). */
	public static void sweepFirstPerson(int side, float swing) {
		float arc = net.minecraft.util.math.MathHelper.sin(net.minecraft.util.math.MathHelper.sqrt(swing) * (float) Math.PI);
		GlStateManager.translate((float) side * -0.42F * arc, 0.06F * arc, -0.12F * arc);
		GlStateManager.rotate((float) side * 75.0F * arc, 0.0F, 1.0F, 0.0F);
		GlStateManager.rotate((float) side * -40.0F * arc, 0.0F, 0.0F, 1.0F);
		GlStateManager.rotate(-15.0F * arc, 1.0F, 0.0F, 0.0F);
	}

	/** First-person sprint hit: a quick forward stab with the tip pointing ahead. */
	public static void stabFirstPerson(int side, float swing) {
		float t = net.minecraft.util.math.MathHelper.sin(net.minecraft.util.math.MathHelper.sqrt(swing) * (float) Math.PI);
		GlStateManager.translate((float) side * -0.12F * t, 0.08F * t, -0.42F * t);
		GlStateManager.rotate(-55.0F * t, 1.0F, 0.0F, 0.0F);
		GlStateManager.rotate((float) side * 10.0F * t, 0.0F, 1.0F, 0.0F);
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
