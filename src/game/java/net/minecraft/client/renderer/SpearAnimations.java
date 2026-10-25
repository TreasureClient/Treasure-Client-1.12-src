package net.minecraft.client.renderer;

import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.item.ItemSpear;
import net.minecraft.util.EnumHandSide;

/**
 * Treasure Client — spear animations, ported from the modern client
 * (net.minecraft.client.model.effects.SpearAnimations + util.Ease).
 * Modern "rotateAround(q, px, py, pz)" becomes translate / rotate / translate back.
 */
public final class SpearAnimations {

	private SpearAnimations() {
	}

	private static final float DEG = (float) Math.PI / 180.0F;

	// ---- first person -------------------------------------------------------

	/** Jab. Call after the normal arm placement (transformSideFirstPerson). */
	public static void firstPersonAttack(float attack, int invert) {
		float starting = inOutSine(progress(attack, 0.0F, 0.05F));
		float middle = outBack(progress(attack, 0.05F, 0.2F));
		float ending = inOutExpo(progress(attack, 0.4F, 1.0F));
		GlStateManager.translate((float) invert * 0.1F * (starting - middle), -0.075F * (starting - ending),
				0.65F * (starting - middle));
		GlStateManager.rotate(-70.0F * (starting - ending), 1.0F, 0.0F, 0.0F);
		GlStateManager.translate(0.0F, 0.0F, -0.25F * (ending - middle));
	}

	/** Charge (holding right click). Replaces the arm placement. */
	public static void firstPersonUse(float timeHeld, int invert, ItemSpear spear) {
		Params p = new Params(spear, timeHeld);
		GlStateManager.translate((float) invert * 0.56F, -0.52F, -0.72F);
		GlStateManager.translate(
				(float) invert * (p.raise * 0.15F + p.raiseEnd * -0.05F + p.sway * -0.1F + p.swaySlow * 0.005F),
				p.raise * -0.075F + p.raiseMiddle * 0.075F + p.swayFast * 0.01F,
				p.raiseStart * 0.05F + p.raiseEnd * -0.05F + p.swaySlow * 0.005F);
		rotateAround(-65.0F * inOutBack(p.raise) - 35.0F * p.lower + 100.0F * p.raiseBack + -0.5F * p.swayFast,
				1.0F, 0.0F, 0.0F, 0.0F, 0.1F, 0.0F);
		rotateAround((float) invert * (-90.0F * progress(p.raise, 0.5F, 0.55F) + 90.0F * p.sway + 2.0F * p.swaySlow),
				0.0F, -1.0F, 0.0F, (float) invert * 0.15F, 0.0F, 0.0F);
	}

	// ---- third person: arms ----------------------------------------------------

	/** Arm pose while charging (modern ArmPose.SPEAR). */
	public static void thirdPersonHandUse(ModelRenderer arm, ModelRenderer head, boolean rightArm, boolean flying,
			float ticksUsing, ItemSpear spear) {
		int invert = rightArm ? 1 : -1;
		arm.rotateAngleY = -0.1F * (float) invert + head.rotateAngleY;
		arm.rotateAngleX = -1.5707964F + head.rotateAngleX + 0.8F;
		if (flying) {
			arm.rotateAngleX -= 0.9599311F;
		}
		arm.rotateAngleY = DEG * clamp(57.295776F * arm.rotateAngleY, -60.0F, 60.0F);
		arm.rotateAngleX = DEG * clamp(57.295776F * arm.rotateAngleX, -120.0F, 30.0F);
		if (ticksUsing <= 0.0F || spear == null) {
			return;
		}
		Params p = new Params(spear, ticksUsing);
		arm.rotateAngleY += (float) (-invert) * p.swayFast * DEG * p.swayIntensity;
		arm.rotateAngleZ += (float) (-invert) * p.swaySlow * DEG * p.swayIntensity * 0.5F;
		arm.rotateAngleX += DEG * (-40.0F * p.raiseStart + 30.0F * p.raiseMiddle + -20.0F * p.raiseEnd
				+ 20.0F * p.lower + 10.0F * p.raiseBack + 0.6F * p.swaySlow * p.swayIntensity);
	}

	/** Arm during a jab. Call after the body-turn part of the normal swing code. */
	public static void thirdPersonAttackHand(ModelBiped model, float animation, EnumHandSide side) {
		model.bipedRightArm.rotateAngleY -= model.bipedBody.rotateAngleY;
		model.bipedLeftArm.rotateAngleY -= model.bipedBody.rotateAngleY;
		model.bipedLeftArm.rotateAngleX -= model.bipedBody.rotateAngleY;
		float prepare = inOutSine(progress(animation, 0.0F, 0.05F));
		float attack = inQuad(progress(animation, 0.05F, 0.2F));
		float retract = inOutExpo(progress(animation, 0.4F, 1.0F));
		ModelRenderer arm = side == EnumHandSide.LEFT ? model.bipedLeftArm : model.bipedRightArm;
		arm.rotateAngleX += (90.0F * prepare - 120.0F * attack + 30.0F * retract) * DEG;
	}

	// ---- third person: the item in the hand ------------------------------------

	/** Item during a jab (LayerHeldItem, before drawing the item). */
	public static void thirdPersonAttackItem(float animation) {
		if (animation <= 0.0F) {
			return;
		}
		float attack = inQuad(progress(animation, 0.05F, 0.2F));
		float retract = inOutExpo(progress(animation, 0.4F, 1.0F));
		rotateAround(70.0F * (attack - retract), -1.0F, 0.0F, 0.0F, 0.0F, -0.125F, 0.125F);
		GlStateManager.translate(0.0F, ItemSpear.FORWARD_MOVEMENT * (attack - retract), 0.0F);
	}

	/** Item while charging (LayerHeldItem, before drawing the item). */
	public static void thirdPersonUseItem(float swingAnimation, float timeHeld, boolean rightArm, ItemSpear spear) {
		if (timeHeld == 0.0F) {
			return;
		}
		float attack = inQuad(progress(swingAnimation, 0.05F, 0.2F));
		float retract = inOutExpo(progress(swingAnimation, 0.4F, 1.0F));
		Params p = new Params(spear, timeHeld);
		int invert = rightArm ? 1 : -1;
		float raiseModified = 1.0F - outBack(1.0F - p.raise);
		GlStateManager.translate(0.0F, 0.0F, -ItemSpear.FORWARD_MOVEMENT * (raiseModified - p.raiseBack));
		rotateAround(70.0F * (p.raise - p.raiseBack) - 40.0F * (attack - retract), -1.0F, 0.0F, 0.0F, 0.0F,
				-0.03125F, 0.125F);
		rotateAround((float) (invert * 90) * (p.raise - p.sway + 3.0F * retract + attack), 0.0F, 1.0F, 0.0F, 0.0F,
				0.0F, 0.125F);
	}

	// ---- shared ---------------------------------------------------------------

	private static final class Params {
		final float raise, raiseStart, raiseMiddle, raiseEnd, sway, lower, raiseBack, swayIntensity, swaySlow,
				swayFast;

		Params(ItemSpear s, float time) {
			int finishRaising = s.delayTicks;
			int finishSwaying = s.dismountTicks + finishRaising;
			int startSwaying = finishSwaying - 20;
			int finishLowering = s.knockbackTicks + finishRaising;
			int startLowering = finishLowering - 40;
			int finishRaisingBack = s.damageTicks + finishRaising;
			raise = progress(time, 0.0F, finishRaising);
			raiseStart = progress(raise, 0.0F, 0.5F);
			raiseMiddle = progress(raise, 0.5F, 0.8F);
			raiseEnd = progress(raise, 0.8F, 1.0F);
			sway = progress(time, startSwaying, finishSwaying);
			lower = outCubic(inOutElastic(progress(time - 20.0F, startLowering, finishLowering)));
			raiseBack = progress(time, finishRaisingBack - 5, finishRaisingBack);
			swayIntensity = 2.0F * outCirc(sway) - 2.0F * inCirc(raiseBack);
			swaySlow = (float) Math.sin(time * 19.0F * DEG) * swayIntensity;
			swayFast = (float) Math.sin(time * 30.0F * DEG) * swayIntensity;
		}
	}

	private static void rotateAround(float degrees, float ax, float ay, float az, float px, float py, float pz) {
		GlStateManager.translate(px, py, pz);
		GlStateManager.rotate(degrees, ax, ay, az);
		GlStateManager.translate(-px, -py, -pz);
	}

	private static float clamp(float v, float lo, float hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}

	static float progress(float time, float start, float end) {
		if (end == start) {
			return time >= end ? 1.0F : 0.0F;
		}
		return clamp((time - start) / (end - start), 0.0F, 1.0F);
	}

	private static float inQuad(float x) {
		return x * x;
	}

	private static float inOutSine(float x) {
		return -((float) Math.cos(Math.PI * x) - 1.0F) / 2.0F;
	}

	private static float outBack(float x) {
		float d = x - 1.0F;
		return 1.0F + 2.70158F * d * d * d + 1.70158F * d * d;
	}

	private static float outCubic(float x) {
		float d = 1.0F - x;
		return 1.0F - d * d * d;
	}

	private static float inOutExpo(float x) {
		if (x < 0.5F) {
			return x == 0.0F ? 0.0F : (float) (Math.pow(2.0, 20.0 * x - 10.0) / 2.0);
		}
		return x == 1.0F ? 1.0F : (float) ((2.0 - Math.pow(2.0, -20.0 * x + 10.0)) / 2.0);
	}

	private static float outCirc(float x) {
		return (float) Math.sqrt(1.0F - (x - 1.0F) * (x - 1.0F));
	}

	private static float inCirc(float x) {
		return (float) (-Math.sqrt(1.0F - x * x)) + 1.0F;
	}

	private static float inOutBack(float x) {
		if (x < 0.5F) {
			return 4.0F * x * x * (7.189819F * x - 2.5949094F) / 2.0F;
		}
		float dt = 2.0F * x - 2.0F;
		return (dt * dt * (3.5949094F * dt + 2.5949094F) + 2.0F) / 2.0F;
	}

	private static float inOutElastic(float x) {
		if (x == 0.0F) {
			return 0.0F;
		}
		if (x == 1.0F) {
			return 1.0F;
		}
		double sin = Math.sin((20.0 * x - 11.125) * 1.3962634801864624);
		if (x < 0.5F) {
			return (float) (-(Math.pow(2.0, 20.0 * x - 10.0) * sin) / 2.0);
		}
		return (float) (Math.pow(2.0, -20.0 * x + 10.0) * sin / 2.0 + 1.0);
	}
}
