package net.minecraft.tcpvp;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.init.Items;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/**
 * Treasure Client: what a practice bot is "pressing", set by /player &lt;bot&gt; ...
 *
 * <p>Clicks (attack, use, jump, swing, drop, dropStack, swapHands) run once,
 * every tick (continuous) or every N ticks (interval N). Movement, sneak, sprint,
 * looking and the shield stay on until changed.
 */
public final class TCBotActions {

	public static final String[] CLICKS = { "attack", "use", "jump", "swing", "drop", "dropStack", "swapHands" };

	private final TCBot bot;

	/** Per click: 0 = off, -1 = once, 1 = every tick, N = every N ticks. */
	private final int[] every = new int[CLICKS.length];
	private final int[] wait = new int[CLICKS.length];

	public float forward, strafe;
	public boolean sneak, sprint, shield;
	/** Keep looking at this entity (look me), or null. */
	public Entity lookAt;

	TCBotActions(TCBot bot) {
		this.bot = bot;
	}

	public static int clickIndex(String name) {
		for (int i = 0; i < CLICKS.length; i++) {
			if (CLICKS[i].equalsIgnoreCase(name)) {
				return i;
			}
		}
		return -1;
	}

	/** mode: -1 once, 0 stop, 1 continuous, N interval. */
	public void setClick(int i, int mode) {
		this.every[i] = mode;
		this.wait[i] = 0;
		if (mode == 0) {
			if (i == 1) { // stop holding "use" (releases a bow, stops eating)
				if (this.bot.isHandActive() && !isShield(this.bot.getActiveItemStack())) {
					this.bot.stopActiveHand();
				}
			} else if (i == 2) {
				this.bot.setJumping(false);
			}
		}
	}

	/** Stops every click, movement and the shield. */
	public void stopAll() {
		for (int i = 0; i < CLICKS.length; i++) {
			setClick(i, 0);
		}
		this.forward = this.strafe = 0.0F;
		this.sneak = this.sprint = false;
		this.shield = false;
		this.lookAt = null;
		this.bot.setSneaking(false);
		this.bot.setSprinting(false);
		if (this.bot.isHandActive()) {
			this.bot.resetActiveHand();
		}
	}

	void onDeath() {
		this.bot.setJumping(false);
	}

	/** Copies the lasting state (movement, shield, repeating clicks) to a respawned bot. */
	public void copyTo(TCBotActions o) {
		for (int i = 0; i < CLICKS.length; i++) {
			o.every[i] = this.every[i] < 0 ? 0 : this.every[i];
		}
		o.forward = this.forward;
		o.strafe = this.strafe;
		o.sneak = this.sneak;
		o.sprint = this.sprint;
		o.shield = this.shield;
		o.lookAt = this.lookAt != null && !this.lookAt.isDead ? this.lookAt : null;
	}

	// ---------------------------------------------------------------- per tick

	void tick() {
		TCBot b = this.bot;
		if (this.lookAt != null) {
			if (this.lookAt.isDead || this.lookAt.world != b.world) {
				this.lookAt = null;
			} else {
				lookAtPoint(this.lookAt.posX, this.lookAt.posY + this.lookAt.getEyeHeight(), this.lookAt.posZ);
			}
		}

		b.setSneaking(this.sneak);
		b.setSprinting(this.sprint && this.forward > 0.0F && !this.sneak && b.getFoodStats().getFoodLevel() > 6);
		float slow = this.sneak ? 0.3F : 1.0F;
		if (b.isHandActive() && !isShieldUseOnly()) {
			slow *= 0.2F; // using an item slows you like a real player
		}
		b.moveStrafing = this.strafe * slow;
		b.field_191988_bg = this.forward * slow;

		for (int i = 0; i < CLICKS.length; i++) {
			int m = this.every[i];
			if (m == 0) {
				continue;
			}
			if (i == 2 && m == 1) {
				b.setJumping(true); // held jump: jumps whenever it lands
				continue;
			}
			if (m < 0) {
				this.every[i] = 0;
				click(i, false);
			} else if (--this.wait[i] <= 0) {
				this.wait[i] = m;
				click(i, m > 1);
			}
		}
		if (this.every[2] != 1) {
			b.setJumping(false);
		}

		if (this.shield) {
			holdShield();
		}
	}

	private boolean isShieldUseOnly() {
		return this.bot.isHandActive() && isShield(this.bot.getActiveItemStack());
	}

	private static boolean isShield(ItemStack s) {
		return s != null && !s.func_190926_b() && s.getItem() instanceof ItemShield;
	}

	/** Keeps a shield raised (after an axe stuns it, raises it again when it can). */
	private void holdShield() {
		TCBot b = this.bot;
		if (b.getCooldownTracker().hasCooldown(Items.SHIELD)) {
			return;
		}
		if (b.isHandActive()) {
			if (isShield(b.getActiveItemStack())) {
				return;
			}
			if (this.every[1] != 0) {
				return; // busy using something else on purpose
			}
		}
		if (isShield(b.getHeldItemOffhand())) {
			b.setActiveHand(EnumHand.OFF_HAND);
		} else if (isShield(b.getHeldItemMainhand())) {
			b.setActiveHand(EnumHand.MAIN_HAND);
		}
	}

	// ---------------------------------------------------------------- clicks

	private void click(int i, boolean repeating) {
		TCBot b = this.bot;
		switch (i) {
		case 0:
			attack();
			break;
		case 1:
			use(repeating);
			break;
		case 2:
			if (b.onGround) {
				b.jump();
			}
			break;
		case 3:
			b.swingArm(EnumHand.MAIN_HAND);
			break;
		case 4:
			b.dropItem(false);
			break;
		case 5:
			b.dropItem(true);
			break;
		case 6:
			ItemStack off = b.getHeldItem(EnumHand.OFF_HAND);
			b.setHeldItem(EnumHand.OFF_HAND, b.getHeldItem(EnumHand.MAIN_HAND));
			b.setHeldItem(EnumHand.MAIN_HAND, off);
			b.resetActiveHand();
			break;
		}
	}

	/** Left click: hit what the bot looks at (3 blocks), else just swing. */
	private void attack() {
		TCBot b = this.bot;
		Entity e = lookedAtEntity(3.0D);
		if (e != null) {
			b.attackTargetEntityWithCurrentItem(e);
		} else {
			b.resetCooldown();
		}
		b.swingArm(EnumHand.MAIN_HAND);
	}

	/** Right click: on a block first (4.5 blocks), then the item itself, main hand then off hand. */
	private void use(boolean repeating) {
		TCBot b = this.bot;
		if (b.isHandActive() && !isShield(b.getActiveItemStack())) {
			if (!repeating) {
				return; // still holding use (eating, drawing a bow)
			}
			b.stopActiveHand(); // interval use: let go (shoots a bow), then click again
		}
		RayTraceResult hit = lookedAtBlock(4.5D);
		for (EnumHand hand : EnumHand.values()) {
			ItemStack stack = b.getHeldItem(hand);
			if (hit != null) {
				float hx = (float) (hit.hitVec.xCoord - hit.getBlockPos().getX());
				float hy = (float) (hit.hitVec.yCoord - hit.getBlockPos().getY());
				float hz = (float) (hit.hitVec.zCoord - hit.getBlockPos().getZ());
				EnumActionResult r = b.interactionManager.processRightClickBlock(b, b.world, stack, hand,
						hit.getBlockPos(), hit.sideHit, hx, hy, hz);
				if (r == EnumActionResult.SUCCESS) {
					b.swingArm(hand);
					return;
				}
			}
			if (stack.func_190926_b()) {
				continue;
			}
			EnumActionResult r = b.interactionManager.processRightClick(b, b.world, stack, hand);
			if (r == EnumActionResult.SUCCESS) {
				return;
			}
		}
	}

	private RayTraceResult lookedAtBlock(double reach) {
		Vec3d eye = this.bot.getPositionEyes(1.0F);
		Vec3d end = eye.add(this.bot.getLook(1.0F).scale(reach));
		RayTraceResult r = this.bot.world.rayTraceBlocks(eye, end);
		return r != null && r.typeOfHit == RayTraceResult.Type.BLOCK ? r : null;
	}

	/** The nearest entity on the bot's line of sight within reach, not behind a block. */
	private Entity lookedAtEntity(double reach) {
		TCBot b = this.bot;
		Vec3d eye = b.getPositionEyes(1.0F);
		Vec3d look = b.getLook(1.0F);
		Vec3d end = eye.add(look.scale(reach));
		RayTraceResult block = b.world.rayTraceBlocks(eye, end);
		double best = block != null ? block.hitVec.distanceTo(eye) : reach;
		Entity found = null;
		AxisAlignedBB area = b.getEntityBoundingBox().addCoord(look.xCoord * reach, look.yCoord * reach,
				look.zCoord * reach).expandXyz(1.0D);
		List<Entity> list = b.world.getEntitiesWithinAABBExcludingEntity(b, area);
		for (int i = 0, n = list.size(); i < n; i++) {
			Entity e = list.get(i);
			if (!e.canBeCollidedWith() || e.isDead) {
				continue;
			}
			if (e instanceof EntityArmorStand && ((EntityArmorStand) e).hasMarker()) {
				continue;
			}
			AxisAlignedBB box = e.getEntityBoundingBox().expandXyz((double) e.getCollisionBorderSize());
			if (box.isVecInside(eye)) {
				return e;
			}
			RayTraceResult r = box.calculateIntercept(eye, end);
			if (r != null) {
				double d = eye.distanceTo(r.hitVec);
				if (d < best) {
					best = d;
					found = e;
				}
			}
		}
		return found;
	}

	// ---------------------------------------------------------------- looking

	public void look(float yaw, float pitch) {
		TCBot b = this.bot;
		b.rotationYaw = MathHelper.wrapDegrees(yaw);
		b.rotationPitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
		b.rotationYawHead = b.rotationYaw;
		b.renderYawOffset = b.rotationYaw;
	}

	public void lookAtPoint(double x, double y, double z) {
		TCBot b = this.bot;
		double dx = x - b.posX, dy = y - (b.posY + b.getEyeHeight()), dz = z - b.posZ;
		double h = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
		look(yaw, pitch);
	}

	/** For /player list: a short line of what the bot is doing. */
	public String describe() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < CLICKS.length; i++) {
			int m = this.every[i];
			if (m != 0) {
				sb.append(CLICKS[i]).append(m == 1 ? " continuous" : m < 0 ? " once" : " every " + m).append(", ");
			}
		}
		if (this.forward != 0 || this.strafe != 0) {
			sb.append("moving, ");
		}
		if (this.sneak) sb.append("sneaking, ");
		if (this.sprint) sb.append("sprinting, ");
		if (this.shield) sb.append("shield up, ");
		if (this.lookAt != null) sb.append("looking at ").append(this.lookAt.getName()).append(", ");
		return sb.length() == 0 ? "idle" : sb.substring(0, sb.length() - 2);
	}
}
