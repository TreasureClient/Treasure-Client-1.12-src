package net.minecraft.client.gui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.lax1dude.eaglercraft.opengl.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityFireworkRocket;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemShield;

/**
 * Treasure Client: Crosshair Addons, small pixel icons around the crosshair.
 *
 * <ul>
 * <li>Target: corner brackets while a player (or, if set, a hostile mob) is in reach under the crosshair.</li>
 * <li>Elytra: a small elytra while you wear one.</li>
 * <li>Rocket: while a rocket boosts you, a rocket that empties as the boost runs out.</li>
 * <li>Hit marker: an X when your arrow hits a player (and, if set, when you hit one).</li>
 * <li>Shield break: a shield that cracks apart when you knock someone's shield down.</li>
 * <li>Shield up: a shield while the player you aim at is blocking.</li>
 * </ul>
 * The attack cooldown indicator moves down below the icons.
 *
 * <p>Port of Crosshair Addons by Walksy (https://github.com/Walksy/Crosshair-Addons), icon
 * shapes from it. MIT License, Copyright (c) 2026 Walksy: Permission is hereby granted, free
 * of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions: The above copyright notice and this permission
 * notice shall be included in all copies or substantial portions of the Software. THE
 * SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.
 */
public final class TCCrosshairAddons {

	private TCCrosshairAddons() {
	}

	// settings (from the Treasure menu, see TreasureMods)
	public static boolean on = false, target = true, mobs = false, elytra = true, rocket = true, hit = true,
			hitMelee = false, shieldBreak = false, shieldUp = false;

	// icon shapes: x,y pairs on the 15x15 crosshair grid
	private static final int[] TARGET = { 4, 4, 5, 4, 9, 4, 10, 4, 4, 5, 10, 5, 4, 10, 5, 10, 9, 10, 10, 10, 4, 9,
			10, 9 };
	private static final int[] ELYTRA = { 1, 4, 1, 5, 1, 6, 1, 7, 2, 3, 2, 8, 2, 9, 3, 3, 3, 10, 3, 11, 4, 2, 4,
			11, 5, 2, 5, 10, 6, 2, 6, 3, 6, 4, 6, 5, 6, 6, 6, 7, 6, 8, 6, 9, 7, 2, 7, 3, 8, 2, 8, 3, 8, 4, 8, 5, 8,
			6, 8, 7, 8, 8, 8, 9, 9, 2, 9, 10, 10, 2, 10, 11, 11, 3, 11, 10, 11, 11, 12, 3, 12, 8, 12, 9, 13, 4, 13,
			5, 13, 6, 13, 7 };
	private static final int[] ROCKET = { 12, 5, 2, 6, 3, 6, 4, 6, 5, 6, 6, 6, 7, 6, 8, 6, 9, 6, 10, 6, 11, 6, 12,
			6, 13, 6, 1, 7, 2, 7, 3, 7, 4, 7, 5, 7, 6, 7, 7, 7, 8, 7, 9, 7, 10, 7, 11, 7, 12, 7, 13, 7, 1, 8, 12, 8,
			2, 9, 2, 10 };
	private static final int[] HIT = { 3, 3, 11, 3, 4, 4, 10, 4, 5, 5, 9, 5, 5, 9, 9, 9, 4, 10, 10, 10, 3, 11, 11,
			11 };
	private static final int[] SHIELD = { 4, 5, 5, 5, 6, 5, 7, 5, 8, 5, 9, 5, 10, 5, 4, 6, 10, 6, 4, 7, 10, 7, 4,
			8, 10, 8, 4, 9, 10, 9, 4, 10, 10, 10, 5, 11, 9, 11, 6, 12, 7, 12, 8, 12 };
	private static final int[][] SHIELD_BREAK = { SHIELD,
			{ 3, 5, 4, 5, 5, 5, 9, 5, 10, 5, 11, 5, 3, 6, 11, 6, 3, 7, 11, 7, 3, 8, 11, 8, 3, 9, 11, 9, 3, 10, 11,
					10, 4, 11, 10, 11, 5, 12, 9, 12, 6, 12, 8, 12 },
			{ 2, 5, 4, 5, 10, 5, 11, 5, 2, 6, 12, 6, 2, 7, 12, 7, 12, 8, 2, 9, 12, 9, 2, 10, 12, 10, 3, 11, 10,
					12 },
			{ 1, 5, 3, 5, 11, 5, 1, 7, 13, 7, 1, 9, 13, 9, 13, 10, 2, 11, 10, 12 },
			{ 2, 5, 0, 7, 14, 7, 14, 9, 1, 11, 12, 12 } };

	private static final float OFF_ELYTRA = 11.0F, OFF_ROCKET = 19.0F, OFF_SHIELD = 8.5F;
	private static final long HIT_MS = 400L, BREAK_FRAME_MS = 70L;

	private static long hitAt = -1L, breakAt = -1L;
	private static Entity lastAttacked;
	private static long lastAttackAt;

	private static long now() {
		return Minecraft.getSystemTime();
	}

	/** Your arrow hit a player (the "ding" the server sends). */
	public static void onArrowHit() {
		hitAt = now();
	}

	/** You attacked something. */
	public static void onAttack(Entity target) {
		lastAttacked = target;
		lastAttackAt = now();
		if (hitMelee && target instanceof EntityPlayer) {
			hitAt = now();
		}
	}

	/** Someone's shield was knocked down (status 30); counts if it was the one you just hit. */
	public static void onShieldDisabled(Entity e) {
		if (e != null && e == lastAttacked && now() - lastAttackAt < 1000L) {
			breakAt = now();
		}
	}

	/**
	 * Draws the icons around the crosshair (blend is already the crosshair's invert blend).
	 * Returns how many pixels below its usual spot the attack indicator should go.
	 */
	public static int draw(Minecraft mc, int cx, int cy) {
		if (!on || mc.player == null) {
			return 0;
		}
		EntityPlayer p = mc.player;
		Entity pointed = mc.pointedEntity;
		long t = now();
		float left = cx, top = cy; // top-left of the 15x15 crosshair
		float bottom = -1.0F; // lowest icon row drawn below the crosshair
		WorldRenderer buf = Tessellator.getInstance().getBuffer();
		boolean begun = false;

		if (target && pointed instanceof EntityLivingBase && pointed.isEntityAlive()
				&& (pointed instanceof EntityPlayer || (mobs && pointed instanceof IMob))) {
			begun = begin(buf, begun);
			put(buf, TARGET, left, top);
		}
		if (elytra && p.getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
			begun = begin(buf, begun);
			put(buf, ELYTRA, left, top + OFF_ELYTRA);
			bottom = Math.max(bottom, OFF_ELYTRA + 12.0F);
		}
		if (rocket && p.isElytraFlying()) {
			float prog = rocketLeft(mc, p);
			if (prog > 0.0F) {
				begun = begin(buf, begun);
				putCut(buf, ROCKET, left, top + OFF_ROCKET, prog);
				bottom = Math.max(bottom, OFF_ROCKET + 11.0F);
			}
		}
		if (hit && hitAt >= 0L && t - hitAt < HIT_MS) {
			begun = begin(buf, begun);
			put(buf, HIT, left, top);
		}
		boolean breaking = shieldBreak && breakAt >= 0L && t - breakAt < BREAK_FRAME_MS * SHIELD_BREAK.length;
		if (breaking) {
			begun = begin(buf, begun);
			put(buf, SHIELD_BREAK[(int) ((t - breakAt) / BREAK_FRAME_MS)], left, top + OFF_SHIELD);
			bottom = Math.max(bottom, OFF_SHIELD + 13.0F);
		} else if (shieldUp && pointed instanceof EntityPlayer && isBlocking((EntityPlayer) pointed)) {
			begun = begin(buf, begun);
			put(buf, SHIELD, left, top + OFF_SHIELD);
			bottom = Math.max(bottom, OFF_SHIELD + 13.0F);
		}

		if (begun) {
			GlStateManager.disableTexture2D();
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			Tessellator.getInstance().draw();
			GlStateManager.enableTexture2D();
		}
		// the attack indicator sits 16 below the crosshair top; push it under the icons (2px overlap)
		return bottom < 0.0F ? 0 : Math.max(0, (int) Math.ceil(bottom - 2.0F) - 16);
	}

	private static boolean isBlocking(EntityPlayer e) {
		return e.isHandActive() && !e.getActiveItemStack().func_190926_b()
				&& e.getActiveItemStack().getItem() instanceof ItemShield;
	}

	/** How much of the rocket boost on you is left (0..1), 0 if none. */
	private static float rocketLeft(Minecraft mc, EntityPlayer p) {
		float best = 0.0F;
		List<Entity> list = mc.world.loadedEntityList;
		for (int i = 0, n = list.size(); i < n; ++i) {
			Entity e = list.get(i);
			if (e instanceof EntityFireworkRocket && !e.isDead) {
				EntityFireworkRocket r = (EntityFireworkRocket) e;
				if (r.tcBoosted() == p) {
					// lifetime is rolled on the server; use its average
					float life = 10.0F * (1 + r.tcFlight()) + 5.5F;
					float prog = Math.max(0.0F, Math.min(1.0F, (life - r.tcAge()) / life));
					best = Math.max(best, Math.max(prog, 0.07F)); // keep a sliver until the rocket is gone
				}
			}
		}
		return best;
	}

	private static boolean begin(WorldRenderer buf, boolean begun) {
		if (!begun) {
			buf.begin(7, DefaultVertexFormats.POSITION);
		}
		return true;
	}

	private static void put(WorldRenderer buf, int[] px, float x, float y) {
		for (int i = 0; i < px.length; i += 2) {
			quad(buf, x + px[i], y + px[i + 1]);
		}
	}

	/** Draws only the left part of the icon (fraction {@code prog} of its width). */
	private static void putCut(WorldRenderer buf, int[] px, float x, float y, float prog) {
		int minX = 99, maxX = -1;
		for (int i = 0; i < px.length; i += 2) {
			minX = Math.min(minX, px[i]);
			maxX = Math.max(maxX, px[i]);
		}
		int span = maxX - minX + 1;
		int cut = minX + Math.max(1, (int) Math.ceil(prog * span));
		for (int i = 0; i < px.length; i += 2) {
			if (px[i] < cut) {
				quad(buf, x + px[i], y + px[i + 1]);
			}
		}
	}

	private static void quad(WorldRenderer buf, float x, float y) {
		buf.pos(x, y + 1.0F, 0.0D).endVertex();
		buf.pos(x + 1.0F, y + 1.0F, 0.0D).endVertex();
		buf.pos(x + 1.0F, y, 0.0D).endVertex();
		buf.pos(x, y, 0.0D).endVertex();
	}
}
