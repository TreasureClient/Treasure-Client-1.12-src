package net.minecraft.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.TreasureMods;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Treasure Client: Treasure Animations particles. Recolors totem pops and adds our own
 * sparkle bursts (a ring and a rising spiral on totem pops, a small burst when your hit
 * lands). Only on your screen, nothing is sent to the server.
 *
 * Styles: 1 Treasure (gold with the odd gem), 2 Rainbow, 3 Frost, 4 Ember.
 */
public final class TCTreasureParticles {

	private TCTreasureParticles() {
	}

	private static int style() {
		return TreasureMods.treasureAnim() ? TreasureMods.taParticles() : 0;
	}

	/** Colors one sparkle for the current style. Returns false when particles are off. */
	static boolean color(ParticleSimpleAnimated p, java.util.Random r) {
		int st = style();
		if (st == 0) {
			return false;
		}
		float cr, cg, cb;
		int fade;
		switch (st) {
		case 2: { // Rainbow
			float hue = r.nextFloat();
			int rgb = MathHelper.hsvToRGB(hue, 0.85F, 1.0F);
			cr = ((rgb >> 16) & 255) / 255.0F;
			cg = ((rgb >> 8) & 255) / 255.0F;
			cb = (rgb & 255) / 255.0F;
			fade = 0xFFFFFF;
			break;
		}
		case 3: // Frost
			if (r.nextInt(3) == 0) {
				cr = 0.95F; cg = 0.98F; cb = 1.0F;
			} else {
				cr = 0.35F + r.nextFloat() * 0.2F; cg = 0.8F + r.nextFloat() * 0.2F; cb = 1.0F;
			}
			fade = 0x5577FF;
			break;
		case 4: // Ember
			cr = 1.0F; cg = 0.25F + r.nextFloat() * 0.45F; cb = r.nextFloat() * 0.12F;
			fade = 0x661100;
			break;
		default: // Treasure: gold, with the odd emerald, diamond or ruby
			int gem = r.nextInt(9);
			if (gem == 0) {
				cr = 0.2F; cg = 1.0F; cb = 0.45F;
			} else if (gem == 1) {
				cr = 0.35F; cg = 0.9F; cb = 1.0F;
			} else if (gem == 2) {
				cr = 1.0F; cg = 0.2F; cb = 0.35F;
			} else {
				cr = 1.0F; cg = 0.72F + r.nextFloat() * 0.22F; cb = 0.1F + r.nextFloat() * 0.25F;
			}
			fade = 0xFFF2B0;
		}
		p.setRBGColorF(cr, cg, cb);
		p.setColorFade(fade);
		return true;
	}

	/** Extra effect when anyone pops a totem: a ring that spreads out and a rising spiral. */
	public static void totemPop(Entity e) {
		if (style() == 0 || !TreasureMods.taTotemFx()) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		World w = mc.world;
		if (w == null) {
			return;
		}
		double cx = e.posX, cy = e.posY + e.height * 0.5D, cz = e.posZ;
		int ring = 28;
		for (int i = 0; i < ring; i++) {
			double a = Math.PI * 2.0D * i / ring;
			double s = 0.32D;
			mc.effectRenderer.addEffect(new Sparkle(w, cx, cy, cz, Math.cos(a) * s, 0.02D, Math.sin(a) * s, 0.9F, 34));
		}
		int spiral = 36;
		for (int i = 0; i < spiral; i++) {
			double a = i * 0.55D;
			double up = 0.06D + i * 0.008D;
			mc.effectRenderer.addEffect(new Sparkle(w, cx + Math.cos(a) * 0.45D, e.posY + 0.1D, cz + Math.sin(a) * 0.45D,
					-Math.sin(a) * 0.08D, up, Math.cos(a) * 0.08D, 0.7F, 40));
		}
	}

	/** A small burst of sparkles where your hit lands. */
	public static void hit(Entity target) {
		if (style() == 0 || !TreasureMods.taHitFx() || target == null) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		World w = mc.world;
		if (w == null || mc.player == null) {
			return;
		}
		// the side of the target facing you, around chest height
		double dx = mc.player.posX - target.posX, dz = mc.player.posZ - target.posZ;
		double len = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
		double r = target.width * 0.5D;
		double hx = target.posX + dx / len * r, hz = target.posZ + dz / len * r;
		double hy = target.posY + target.height * 0.62D;
		java.util.Random rnd = w.rand;
		for (int i = 0; i < 12; i++) {
			double vx = (rnd.nextDouble() - 0.5D) * 0.28D + dx / len * 0.06D;
			double vy = rnd.nextDouble() * 0.22D;
			double vz = (rnd.nextDouble() - 0.5D) * 0.28D + dz / len * 0.06D;
			mc.effectRenderer.addEffect(new Sparkle(w, hx, hy, hz, vx, vy, vz, 0.5F, 14 + rnd.nextInt(6)));
		}
	}

	/** A totem sparkle with its own size and life, colored by the style. */
	static class Sparkle extends ParticleTotem {
		Sparkle(World w, double x, double y, double z, double vx, double vy, double vz, float size, int age) {
			super(w, x, y, z, vx, vy, vz);
			this.particleScale *= size;
			this.particleMaxAge = age + this.rand.nextInt(6);
			color(this, this.rand);
		}
	}
}
