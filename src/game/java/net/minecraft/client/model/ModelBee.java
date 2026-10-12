package net.minecraft.client.model;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/**
 * Treasure Client — model for {@link net.minecraft.entity.passive.EntityBee}.
 *
 * Simple bee: one fuzzy striped body box + two flat wings that flap. Built on a
 * 64x32 layout with a matching custom texture so UVs map cleanly.
 */
public class ModelBee extends ModelBase {
	public final ModelRenderer body;
	public final ModelRenderer rightWing;
	public final ModelRenderer leftWing;

	public ModelBee() {
		this.textureWidth = 64;
		this.textureHeight = 32;

		this.body = new ModelRenderer(this, 0, 0);
		this.body.addBox(-3.5F, -3.5F, -5.0F, 7, 7, 10);
		this.body.setRotationPoint(0.0F, 19.0F, 0.0F);

		this.rightWing = new ModelRenderer(this, 0, 18);
		this.rightWing.addBox(-9.0F, 0.0F, 0.0F, 9, 0, 6);
		this.rightWing.setRotationPoint(-1.5F, 15.0F, -3.0F);

		this.leftWing = new ModelRenderer(this, 0, 18);
		this.leftWing.mirror = true;
		this.leftWing.addBox(0.0F, 0.0F, 0.0F, 9, 0, 6);
		this.leftWing.setRotationPoint(1.5F, 15.0F, -3.0F);
	}

	public void render(Entity entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch, float scale) {
		this.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entityIn);
		this.body.render(scale);
		this.rightWing.render(scale);
		this.leftWing.render(scale);
	}

	public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch, float scaleFactor, Entity entityIn) {
		float flap = MathHelper.cos(ageInTicks * 2.1F) * 0.3F;
		this.rightWing.rotateAngleZ = 0.2F + flap;
		this.leftWing.rotateAngleZ = -0.2F - flap;
		this.body.rotateAngleX = 0.0F;
	}
}
