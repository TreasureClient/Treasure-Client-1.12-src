package net.minecraft.client.renderer;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.TreasureMods;
import net.minecraft.util.math.MathHelper;

/**
 * Treasure Client: Treasure Animations. Our own first-person animations, made to feel
 * good on both 1.8 and 1.12 servers. Visual only: nothing extra is sent to the server.
 *
 * <ul>
 * <li>Swing styles: Treasure (a heavy chop with a follow-through twist), Slash (a wide
 * diagonal cut), Spin (the item twirls a full turn) and Smooth (vanilla, eased).</li>
 * <li>Swing speed: slower or snappier swings.</li>
 * <li>Hit punch: the item kicks back when a hit lands.</li>
 * <li>Draw twirl: a new item spins and pops into your hand when you switch to it.</li>
 * <li>Sway: the item leans when you turn.</li>
 * <li>Block-hit bounce (1.8 servers): swinging while sword blocking bobs the sword
 * instead of the full swing.</li>
 * </ul>
 */
public final class TCTreasureAnimations {

	private TCTreasureAnimations() {
	}

	private static final float PI = (float) Math.PI;

	public static final int STYLE_TREASURE = 0, STYLE_SLASH = 1, STYLE_SPIN = 2, STYLE_SMOOTH = 3;

	private static long punchAt, drawAt;

	public static boolean on() {
		return TreasureMods.treasureAnim();
	}

	/** A hit landed on something: kick the item back. */
	public static void onHit() {
		if (on() && TreasureMods.taPunch()) {
			punchAt = EagRuntime.steadyTimeMillis();
		}
	}

	/** A different item came into the main hand: twirl it in. */
	public static void onDraw() {
		if (on() && TreasureMods.taDraw()) {
			drawAt = EagRuntime.steadyTimeMillis();
		}
	}

	/**
	 * The main-hand pose for a normal swing (called after the hand is placed, side: 1 = right).
	 * At swing 0 with nothing else going on this is exactly the vanilla resting pose.
	 */
	public static void firstPerson(int side, float swing) {
		sway(side);
		draw(side);
		switch (TreasureMods.taStyle()) {
		case STYLE_SLASH:
			slash(side, swing);
			break;
		case STYLE_SPIN:
			spin(side, swing);
			break;
		case STYLE_SMOOTH:
			smooth(side, swing);
			break;
		default:
			treasure(side, swing);
		}
		punch();
	}

	/** Treasure: the item lifts a little, chops down hard, then twists through and settles. */
	private static void treasure(int side, float s) {
		float a = MathHelper.sin(MathHelper.sqrt(s) * PI); // fast start, slow settle
		float b = MathHelper.sin(s * PI);
		float c = MathHelper.sin(s * s * PI); // late follow-through
		float pop = MathHelper.sin(MathHelper.sqrt(s) * PI * 2.0F); // up, then down
		GlStateManager.translate((float) side * -0.32F * a, 0.10F * pop, -0.16F * b);
		GlStateManager.rotate((float) side * 45.0F, 0.0F, 1.0F, 0.0F);
		GlStateManager.rotate((float) side * -28.0F * a, 0.0F, 0.0F, 1.0F);
		GlStateManager.rotate(-100.0F * a, 1.0F, 0.0F, 0.0F);
		GlStateManager.rotate((float) side * 24.0F * c, 0.0F, 1.0F, 0.0F);
		GlStateManager.rotate((float) side * -45.0F, 0.0F, 1.0F, 0.0F);
	}

	/** Slash: a wide cut from the top right across to the bottom left. */
	private static void slash(int side, float s) {
		float a = MathHelper.sin(MathHelper.sqrt(s) * PI);
		float d = MathHelper.sin(s * PI);
		GlStateManager.translate((float) side * -0.55F * a, 0.12F * a - 0.18F * d * d, -0.10F * a);
		GlStateManager.rotate((float) side * 45.0F, 0.0F, 1.0F, 0.0F);
		GlStateManager.rotate((float) side * 70.0F * a, 0.0F, 0.0F, 1.0F);
		GlStateManager.rotate(-45.0F * a, 1.0F, 0.0F, 0.0F);
		GlStateManager.rotate((float) side * -45.0F, 0.0F, 1.0F, 0.0F);
	}

	/** Spin: the item flips a full turn over the hand, like twirling a baton. */
	private static void spin(int side, float s) {
		float a = MathHelper.sin(MathHelper.sqrt(s) * PI);
		float inv = 1.0F - s;
		float turn = 360.0F * (1.0F - inv * inv * inv); // fast, then eases into place
		GlStateManager.translate((float) side * -0.16F * a, 0.12F * a, -0.20F * a);
		GlStateManager.rotate((float) side * 45.0F, 0.0F, 1.0F, 0.0F);
		GlStateManager.rotate(-25.0F * a, 1.0F, 0.0F, 0.0F);
		GlStateManager.translate(0.0F, 0.1F, 0.0F); // spin around the middle of the item
		GlStateManager.rotate(-turn, 1.0F, 0.0F, 0.0F);
		GlStateManager.translate(0.0F, -0.1F, 0.0F);
		GlStateManager.rotate((float) side * -45.0F, 0.0F, 1.0F, 0.0F);
	}

	/** Smooth: the vanilla swing on an eased curve, so it starts and stops softly. */
	private static void smooth(int side, float s) {
		float e = s * s * (3.0F - 2.0F * s); // smoothstep
		float f = -0.4F * MathHelper.sin(MathHelper.sqrt(e) * PI);
		float f1 = 0.2F * MathHelper.sin(MathHelper.sqrt(e) * PI * 2.0F);
		float f2 = -0.2F * MathHelper.sin(e * PI);
		GlStateManager.translate((float) side * f, f1, f2);
		float g = MathHelper.sin(e * e * PI);
		GlStateManager.rotate((float) side * (45.0F + g * -20.0F), 0.0F, 1.0F, 0.0F);
		float g1 = MathHelper.sin(MathHelper.sqrt(e) * PI);
		GlStateManager.rotate((float) side * g1 * -20.0F, 0.0F, 0.0F, 1.0F);
		GlStateManager.rotate(g1 * -80.0F, 1.0F, 0.0F, 0.0F);
		GlStateManager.rotate((float) side * -45.0F, 0.0F, 1.0F, 0.0F);
	}

	/** Swinging while sword blocking (1.8 servers): a quick bob instead of the full swing. */
	public static boolean blockHitOn() {
		return on() && TreasureMods.taBlockHit();
	}

	public static void blockHit(int side, float s) {
		float a = MathHelper.sin(MathHelper.sqrt(s) * PI);
		GlStateManager.translate((float) side * -0.05F * a, -0.10F * a, 0.02F * a);
		GlStateManager.rotate(-22.0F * a, 1.0F, 0.0F, 0.0F);
		GlStateManager.rotate((float) side * 8.0F * a, 0.0F, 0.0F, 1.0F);
	}

	/** Item kicks back for a moment after a hit lands. */
	private static void punch() {
		if (punchAt == 0L) {
			return;
		}
		float t = (float) (EagRuntime.steadyTimeMillis() - punchAt) / 180.0F;
		if (t >= 1.0F) {
			punchAt = 0L;
			return;
		}
		float p = MathHelper.sin(t * PI) * (1.0F - t);
		GlStateManager.translate(0.0F, 0.03F * p, 0.10F * p);
		GlStateManager.rotate(14.0F * p, 1.0F, 0.0F, 0.0F);
	}

	/** New item: a quick twirl and a little pop in size. */
	private static void draw(int side) {
		if (drawAt == 0L) {
			return;
		}
		float t = (float) (EagRuntime.steadyTimeMillis() - drawAt) / 320.0F;
		if (t >= 1.0F) {
			drawAt = 0L;
			return;
		}
		float inv = 1.0F - t;
		float k = inv * inv * inv; // ease out
		GlStateManager.rotate((float) side * 360.0F * k, 0.0F, 1.0F, 0.0F);
		float pop = 1.0F + 0.18F * MathHelper.sin(t * PI);
		GlStateManager.scale(pop, pop, pop);
	}

	/** The item leans into your turns. */
	private static void sway(int side) {
		if (!TreasureMods.taSway()) {
			return;
		}
		EntityPlayerSP p = Minecraft.getMinecraft().player;
		if (p == null) {
			return;
		}
		float pt = Minecraft.getMinecraft().getRenderPartialTicks();
		float armYaw = p.prevRenderArmYaw + (p.renderArmYaw - p.prevRenderArmYaw) * pt;
		float yaw = p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * pt;
		float lean = MathHelper.clamp((yaw - armYaw) * 0.35F, -14.0F, 14.0F);
		GlStateManager.rotate(-lean, 0.0F, 0.0F, 1.0F);
	}

	/** Swing length for our own player: 100 = normal, higher = faster. Read by EntityLivingBase. */
	public static void tick(Minecraft mc) {
		net.minecraft.entity.EntityLivingBase.tcSwingEntity = mc.player;
		net.minecraft.entity.EntityLivingBase.tcSwingPct = on() ? TreasureMods.taSpeed() : 100;
	}
}
