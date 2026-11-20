package net.minecraft.client.gui;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.lax1dude.eaglercraft.opengl.WorldRenderer;
import net.lax1dude.eaglercraft.vector.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/**
 * Treasure Client: Reach Display, Combo Counter, Item Counter, Scoreboard and Block Outline.
 *
 * Reach and combo only count hits the server confirmed (the target's hurt animation started
 * right after your attack), so they show what really happened. Nothing here changes how the
 * game plays: they only show information or change how things look.
 */
public final class TCPvpMods {

	private TCPvpMods() {}

	// ---- settings (from the mod menu, via tcmods) ----
	public static boolean reachOn, comboOn, itemsOn;
	/** Item Counter: which items to count (arrow, pearl, gapple, totem, potion, blocks). */
	public static boolean icArrow = true, icPearl = true, icGapple = true, icTotem = true, icPotion = true, icBlocks = false;

	public static boolean sbOn;
	public static boolean sbNoNums = true;
	public static float sbScale = 1.0f;
	/** 0 = normal background, 1 = lighter, 2 = none. */
	public static int sbBg = 0;

	public static boolean boOn;
	public static int boColor = 0x000000;
	public static float boAlpha = 0.4f;
	public static float boWidth = 2.0f;
	public static boolean boFill = false;

	public static void reset() {
		reachOn = false; comboOn = false; itemsOn = false;
		icArrow = true; icPearl = true; icGapple = true; icTotem = true; icPotion = true; icBlocks = false;
		sbOn = false; sbNoNums = true; sbScale = 1.0f; sbBg = 0;
		boOn = false; boColor = 0x000000; boAlpha = 0.4f; boWidth = 2.0f; boFill = false;
	}

	/** One tcmods setting; returns false if it isn't one of ours. */
	public static boolean parse(String k, String v) {
		boolean on = v.equals("1");
		switch (k) {
		case "reachDisp": reachOn = on; return true;
		case "combo": comboOn = on; return true;
		case "itemCount": itemsOn = on; return true;
		case "icArrow": icArrow = on; return true;
		case "icPearl": icPearl = on; return true;
		case "icGapple": icGapple = on; return true;
		case "icTotem": icTotem = on; return true;
		case "icPotion": icPotion = on; return true;
		case "icBlocks": icBlocks = on; return true;
		case "sbOn": sbOn = on; TCHudCache.invalidate(); return true;
		case "sbNoNums": sbNoNums = on; TCHudCache.invalidate(); return true;
		case "sbScale": sbScale = num(v, 100, 50, 150) / 100.0f; TCHudCache.invalidate(); return true;
		case "sbBg": sbBg = v.equals("none") ? 2 : v.equals("light") ? 1 : 0; TCHudCache.invalidate(); return true;
		case "boOn": boOn = on; return true;
		case "boColor":
			try { boColor = Integer.parseInt(v, 16) & 0xFFFFFF; } catch (Throwable t) {}
			return true;
		case "boAlpha": boAlpha = num(v, 40, 10, 100) / 100.0f; return true;
		case "boWidth": boWidth = num(v, 2, 1, 6); return true;
		case "boFill": boFill = on; return true;
		default: return false;
		}
	}

	private static int num(String v, int def, int lo, int hi) {
		try {
			return Math.max(lo, Math.min(hi, Integer.parseInt(v)));
		} catch (Throwable t) {
			return def;
		}
	}

	// ---- Reach Display + Combo Counter ----
	private static Entity attacked;
	private static long attackAt;
	private static double attackReach = -1;
	private static int attackedHurt;
	private static int ownHurt;
	private static double reach = -1;
	private static long reachAt;
	private static int combo;
	private static long comboAt;

	private static long now() {
		return EagRuntime.steadyTimeMillis();
	}

	/** You attacked something (PlayerControllerMP.attackEntity). */
	public static void onAttack(Minecraft mc, Entity target) {
		if (!(reachOn || comboOn) || mc.player == null || !(target instanceof EntityLivingBase)) {
			return;
		}
		try {
			EntityPlayer p = mc.player;
			Vec3d eye = p.getPositionEyes(1.0F);
			double d;
			RayTraceResult r = mc.objectMouseOver;
			if (r != null && r.typeOfHit == RayTraceResult.Type.ENTITY && r.entityHit == target && r.hitVec != null) {
				d = eye.distanceTo(r.hitVec); // where your crosshair touched its hitbox
			} else {
				AxisAlignedBB bb = target.getEntityBoundingBox();
				double x = Math.max(bb.minX, Math.min(eye.xCoord, bb.maxX));
				double y = Math.max(bb.minY, Math.min(eye.yCoord, bb.maxY));
				double z = Math.max(bb.minZ, Math.min(eye.zCoord, bb.maxZ));
				d = eye.distanceTo(new Vec3d(x, y, z)); // nearest point of its hitbox
			}
			if (target != attacked) {
				attackedHurt = ((EntityLivingBase) target).hurtTime;
			}
			attacked = target;
			attackAt = now();
			attackReach = d;
		} catch (Throwable t) {}
	}

	/** Every client tick: confirm hits and reset the combo when you get hit. */
	public static void tick(Minecraft mc) {
		EntityPlayer p = mc.player;
		if (p == null) {
			attacked = null;
			combo = 0;
			reach = -1;
			return;
		}
		long t = now();
		int own = p.hurtTime;
		if (own > ownHurt) {
			combo = 0; // you got hit
		}
		ownHurt = own;
		if (attacked instanceof EntityLivingBase) {
			int h = ((EntityLivingBase) attacked).hurtTime;
			if (h > attackedHurt && t - attackAt < 700L) {
				// the server hurt it right after your attack: a real hit
				reach = attackReach;
				reachAt = t;
				combo = (t - comboAt < 3000L) ? combo + 1 : 1;
				comboAt = t;
				attackAt = 0;
			}
			attackedHurt = h;
			if (attacked.isDead) {
				attacked = null;
			}
		}
		if (combo > 0 && t - comboAt >= 3000L) {
			combo = 0;
		}
	}

	/** Adds the HUD lines for the overlay (TreasureHud). */
	public static void hud(EntityPlayer p, StringBuilder sb) {
		if (reachOn) {
			long t = now();
			sb.append("reach=").append(reach < 0 || t - reachAt > 5000L ? "-" : String.valueOf(Math.round(reach * 100.0) / 100.0)).append('\n');
		}
		if (comboOn) {
			sb.append("combo=").append(combo).append('\n');
		}
		if (itemsOn) {
			int arrow = 0, pearl = 0, gapple = 0, totem = 0, potion = 0, blocks = 0;
			try {
				int n = p.inventory.getSizeInventory();
				for (int i = 0; i < n; ++i) {
					ItemStack s = p.inventory.getStackInSlot(i);
					if (s == null || s.func_190926_b()) {
						continue;
					}
					Item it = s.getItem();
					int c = s.func_190916_E();
					if (it == Items.ARROW || it == Items.TIPPED_ARROW || it == Items.SPECTRAL_ARROW) arrow += c;
					else if (it == Items.ENDER_PEARL) pearl += c;
					else if (it == Items.GOLDEN_APPLE) gapple += c;
					else if (it == Items.TOTEM_OF_UNDYING) totem += c;
					else if (it == Items.SPLASH_POTION || it == Items.LINGERING_POTION) potion += c;
					else if (it instanceof ItemBlock && i < 36) blocks += c; // armor slots don't count
				}
			} catch (Throwable t) {}
			sb.append("items=");
			if (icArrow) sb.append("arrow:").append(arrow).append('~');
			if (icPearl) sb.append("pearl:").append(pearl).append('~');
			if (icGapple) sb.append("gapple:").append(gapple).append('~');
			if (icTotem) sb.append("totem:").append(totem).append('~');
			if (icPotion) sb.append("potion:").append(potion).append('~');
			if (icBlocks) sb.append("blocks:").append(blocks).append('~');
			sb.append('\n');
		}
	}

	// ---- Block Outline ----
	private static final Matrix4f INV = new Matrix4f();

	/**
	 * Draws the box around the block you're looking at (already offset to the camera) with the
	 * chosen colour, thickness and fill. Lines are flat strips facing the camera, so any
	 * thickness works (browsers draw GL lines 1 pixel wide whatever width is asked for).
	 */
	public static void drawOutline(Minecraft mc, AxisAlignedBB bb) {
		float r = (boColor >> 16 & 255) / 255.0f, g = (boColor >> 8 & 255) / 255.0f, b = (boColor & 255) / 255.0f;
		Tessellator tess = Tessellator.getInstance();
		WorldRenderer wr = tess.getBuffer();
		boolean cull = GlStateManager.isCullEnabled();
		GlStateManager.disableCull();
		if (boFill) {
			GlStateManager.color(r, g, b, boAlpha * 0.35f);
			wr.begin(7, DefaultVertexFormats.POSITION);
			face(wr, bb);
			tess.draw();
		}
		// the camera, in the box's coordinates
		Matrix4f.invert(GlStateManager.getModelViewReference(), INV);
		double cx = INV.m30, cy = INV.m31, cz = INV.m32;
		float p11 = GlStateManager.getProjectionReference().m11;
		double px = 1.0 / (Math.max(0.1, Math.abs(p11)) * Math.max(1, mc.displayHeight)); // world size of a pixel at distance 1
		double hw = boWidth * px; // half the width at distance 1 (strip is boWidth pixels wide)
		GlStateManager.color(r, g, b, boAlpha);
		wr.begin(7, DefaultVertexFormats.POSITION);
		double x0 = bb.minX, y0 = bb.minY, z0 = bb.minZ, x1 = bb.maxX, y1 = bb.maxY, z1 = bb.maxZ;
		edge(wr, x0, y0, z0, x1, y0, z0, cx, cy, cz, hw);
		edge(wr, x0, y0, z1, x1, y0, z1, cx, cy, cz, hw);
		edge(wr, x0, y1, z0, x1, y1, z0, cx, cy, cz, hw);
		edge(wr, x0, y1, z1, x1, y1, z1, cx, cy, cz, hw);
		edge(wr, x0, y0, z0, x0, y1, z0, cx, cy, cz, hw);
		edge(wr, x1, y0, z0, x1, y1, z0, cx, cy, cz, hw);
		edge(wr, x0, y0, z1, x0, y1, z1, cx, cy, cz, hw);
		edge(wr, x1, y0, z1, x1, y1, z1, cx, cy, cz, hw);
		edge(wr, x0, y0, z0, x0, y0, z1, cx, cy, cz, hw);
		edge(wr, x1, y0, z0, x1, y0, z1, cx, cy, cz, hw);
		edge(wr, x0, y1, z0, x0, y1, z1, cx, cy, cz, hw);
		edge(wr, x1, y1, z0, x1, y1, z1, cx, cy, cz, hw);
		tess.draw();
		if (cull) {
			GlStateManager.enableCull();
		}
	}

	/** One edge as a strip facing the camera, stretched a little past its ends to fill corners. */
	private static void edge(WorldRenderer wr, double ax, double ay, double az, double bx, double by, double bz,
			double cx, double cy, double cz, double hw) {
		double dx = bx - ax, dy = by - ay, dz = bz - az;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1.0E-6) {
			return;
		}
		dx /= len; dy /= len; dz /= len;
		double vx = (ax + bx) * 0.5 - cx, vy = (ay + by) * 0.5 - cy, vz = (az + bz) * 0.5 - cz;
		double dist = Math.sqrt(vx * vx + vy * vy + vz * vz);
		double w = hw * Math.max(0.05, dist);
		// sideways = edge direction x view direction
		double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
		double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (sl < 1.0E-9) {
			return; // looking straight along the edge
		}
		sx *= w / sl; sy *= w / sl; sz *= w / sl;
		ax -= dx * w; ay -= dy * w; az -= dz * w;
		bx += dx * w; by += dy * w; bz += dz * w;
		wr.pos(ax - sx, ay - sy, az - sz).endVertex();
		wr.pos(bx - sx, by - sy, bz - sz).endVertex();
		wr.pos(bx + sx, by + sy, bz + sz).endVertex();
		wr.pos(ax + sx, ay + sy, az + sz).endVertex();
	}

	private static void face(WorldRenderer wr, AxisAlignedBB bb) {
		double x0 = bb.minX, y0 = bb.minY, z0 = bb.minZ, x1 = bb.maxX, y1 = bb.maxY, z1 = bb.maxZ;
		quad(wr, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(wr, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		quad(wr, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		quad(wr, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(wr, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(wr, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
	}

	private static void quad(WorldRenderer wr, double ax, double ay, double az, double bx, double by, double bz,
			double cx, double cy, double cz, double dx, double dy, double dz) {
		wr.pos(ax, ay, az).endVertex();
		wr.pos(bx, by, bz).endVertex();
		wr.pos(cx, cy, cz).endVertex();
		wr.pos(dx, dy, dz).endVertex();
	}
}
