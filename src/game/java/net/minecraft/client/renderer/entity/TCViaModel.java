package net.minecraft.client.renderer.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/**
 * Treasure Client: a modern mob model built from {@link TCViaModelData}, with a simple
 * walk/swim/fly animation picked by part names. Parts are normal ModelRenderers, so they
 * are compiled once and drawn like any vanilla mob.
 */
final class TCViaModel extends ModelBase {

	static final int LOOK = 1, LEGS = 2, ARMS = 4, FISH = 8, WINGS_SLOW = 16, WINGS_FAST = 32, SWIMX = 64,
			TENT = 128;

	private final ModelRenderer[] parts;
	private final List<ModelRenderer> tops = new ArrayList<ModelRenderer>();
	private final float[] def;
	private final int flags;
	private int head = -1;
	private final int[] legs, arms, tails, wings, tents;
	private final float[] legPhase, armPhase, wingSign;

	TCViaModel(int model, int texW, int texH, int flags, String[] hide) {
		this.flags = flags;
		this.textureWidth = texW;
		this.textureHeight = texH;
		String[] names = TCViaModelData.names(model);
		float[] d = TCViaModelData.data(model);
		int n = names.length;
		parts = new ModelRenderer[n];
		def = new float[n * 3];
		int[] lg = new int[n], ar = new int[n], tl = new int[n], wg = new int[n], tn = new int[n];
		float[] lp = new float[n], ap = new float[n], ws = new float[n];
		int nl = 0, na = 0, nt = 0, nw = 0, ntn = 0;
		boolean hasBodyBack = false;
		for (String s : names) {
			if (s.equals("body_back")) hasBodyBack = true;
		}
		int p = 0;
		for (int i = 0; i < n; ++i) {
			int parent = (int) d[p++];
			ModelRenderer r = new ModelRenderer(this);
			r.setTextureSize(texW, texH);
			r.setRotationPoint(d[p], d[p + 1], d[p + 2]);
			r.rotateAngleX = def[i * 3] = d[p + 3];
			r.rotateAngleY = def[i * 3 + 1] = d[p + 4];
			r.rotateAngleZ = def[i * 3 + 2] = d[p + 5];
			p += 6;
			int cubes = (int) d[p++];
			for (int c = 0; c < cubes; ++c) {
				r.cubeList.add(new ModelBox(r, (int) d[p], (int) d[p + 1], d[p + 2], d[p + 3], d[p + 4],
						Math.round(d[p + 5]), Math.round(d[p + 6]), Math.round(d[p + 7]), d[p + 8], d[p + 9] != 0));
				p += 10;
			}
			parts[i] = r;
			if (parent < 0) tops.add(r);
			else parts[parent].addChild(r);
			String s = names[i];
			if (hide != null) {
				for (String h : hide) {
					if (h.equals(s)) r.isHidden = true;
				}
			}
			if (s.equals("head")) head = i;
			if (s.endsWith("_leg")) {
				boolean right = s.startsWith("right");
				boolean front = s.indexOf("front") >= 0;
				lp[nl] = (right != front) ? 0.0F : (float) Math.PI;
				lg[nl++] = i;
			}
			if (s.equals("right_arm") || s.equals("left_arm")) {
				ap[na] = s.startsWith("right") ? (float) Math.PI : 0.0F;
				ar[na++] = i;
			}
			if (s.equals("tail_fin") || s.equals("tail") || s.equals("body_back")
					|| (!hasBodyBack && s.equals("back_fin"))) {
				tl[nt++] = i;
			}
			if (s.indexOf("wing") >= 0) {
				ws[nw] = s.startsWith("left") ? 1.0F : -1.0F;
				wg[nw++] = i;
			}
			if (s.length() == 1 && Character.isDigit(s.charAt(0))) tn[ntn++] = i;
		}
		legs = java.util.Arrays.copyOf(lg, nl);
		legPhase = java.util.Arrays.copyOf(lp, nl);
		arms = java.util.Arrays.copyOf(ar, na);
		armPhase = java.util.Arrays.copyOf(ap, na);
		tails = java.util.Arrays.copyOf(tl, nt);
		wings = java.util.Arrays.copyOf(wg, nw);
		wingSign = java.util.Arrays.copyOf(ws, nw);
		tents = java.util.Arrays.copyOf(tn, ntn);
	}

	public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch, float scale) {
		this.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);
		for (int i = 0, n = tops.size(); i < n; ++i) {
			tops.get(i).render(scale);
		}
	}

	public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch, float scale, Entity entity) {
		for (int i = 0; i < parts.length; ++i) {
			ModelRenderer r = parts[i];
			r.rotateAngleX = def[i * 3];
			r.rotateAngleY = def[i * 3 + 1];
			r.rotateAngleZ = def[i * 3 + 2];
		}
		if ((flags & LOOK) != 0 && head >= 0) {
			parts[head].rotateAngleY += netHeadYaw * 0.017453292F;
			parts[head].rotateAngleX += headPitch * 0.017453292F;
		}
		if ((flags & LEGS) != 0) {
			for (int i = 0; i < legs.length; ++i) {
				parts[legs[i]].rotateAngleX += MathHelper.cos(limbSwing * 0.6662F + legPhase[i]) * 1.4F * limbSwingAmount;
			}
		}
		if ((flags & ARMS) != 0) {
			for (int i = 0; i < arms.length; ++i) {
				parts[arms[i]].rotateAngleX += MathHelper.cos(limbSwing * 0.6662F + armPhase[i]) * limbSwingAmount;
			}
		}
		if ((flags & FISH) != 0) {
			float w = -0.45F * MathHelper.sin(0.6F * ageInTicks);
			for (int i = 0; i < tails.length; ++i) {
				parts[tails[i]].rotateAngleY += w;
			}
		}
		if ((flags & SWIMX) != 0) {
			float c = MathHelper.cos(ageInTicks * 0.3F);
			for (int i = 0; i < tails.length; ++i) {
				parts[tails[i]].rotateAngleX += -0.1F * c;
			}
		}
		if ((flags & (WINGS_SLOW | WINGS_FAST)) != 0) {
			float f = (flags & WINGS_FAST) != 0 ? MathHelper.cos(ageInTicks * 2.1F) * 0.47F
					: MathHelper.cos((entity.getEntityId() * 3 + ageInTicks) * 0.13F) * 0.28F;
			for (int i = 0; i < wings.length; ++i) {
				parts[wings[i]].rotateAngleZ += wingSign[i] * f;
			}
		}
		if ((flags & TENT) != 0) {
			for (int i = 0; i < tents.length; ++i) {
				parts[tents[i]].rotateAngleX = ageInTicks; // tentacle angle (see RenderViaMob)
			}
		}
	}
}
