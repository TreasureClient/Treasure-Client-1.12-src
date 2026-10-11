package net.minecraft.client.model;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/**
 * Treasure Client — model for {@link net.minecraft.entity.passive.EntityCopperGolem}.
 *
 * A compact humanoid built on the standard 64x32 biped UV layout (head, body,
 * two arms, two legs) so a normal skin-style texture maps cleanly, with a simple
 * walk animation.
 */
public class ModelCopperGolem extends ModelBase {
	public final ModelRenderer head;
	public final ModelRenderer body;
	public final ModelRenderer rightArm;
	public final ModelRenderer leftArm;
	public final ModelRenderer rightLeg;
	public final ModelRenderer leftLeg;

	public ModelCopperGolem() {
		this.textureWidth = 64;
		this.textureHeight = 32;

		this.head = new ModelRenderer(this, 0, 0);
		this.head.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8);
		this.head.setRotationPoint(0.0F, 0.0F, 0.0F);

		this.body = new ModelRenderer(this, 16, 16);
		this.body.addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4);
		this.body.setRotationPoint(0.0F, 0.0F, 0.0F);

		this.rightArm = new ModelRenderer(this, 40, 16);
		this.rightArm.addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4);
		this.rightArm.setRotationPoint(-5.0F, 2.0F, 0.0F);

		this.leftArm = new ModelRenderer(this, 40, 16);
		this.leftArm.mirror = true;
		this.leftArm.addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4);
		this.leftArm.setRotationPoint(5.0F, 2.0F, 0.0F);

		this.rightLeg = new ModelRenderer(this, 0, 16);
		this.rightLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4);
		this.rightLeg.setRotationPoint(-2.0F, 12.0F, 0.0F);

		this.leftLeg = new ModelRenderer(this, 0, 16);
		this.leftLeg.mirror = true;
		this.leftLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4);
		this.leftLeg.setRotationPoint(2.0F, 12.0F, 0.0F);
	}

	public void render(Entity entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch, float scale) {
		this.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entityIn);
		this.head.render(scale);
		this.body.render(scale);
		this.rightArm.render(scale);
		this.leftArm.render(scale);
		this.rightLeg.render(scale);
		this.leftLeg.render(scale);
	}

	public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch, float scaleFactor, Entity entityIn) {
		this.head.rotateAngleY = netHeadYaw * 0.017453292F;
		this.head.rotateAngleX = headPitch * 0.017453292F;

		float swing = MathHelper.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
		float swingOpp = MathHelper.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.4F * limbSwingAmount;
		this.rightLeg.rotateAngleX = swing;
		this.leftLeg.rotateAngleX = swingOpp;
		this.rightArm.rotateAngleX = swingOpp * 0.6F;
		this.leftArm.rotateAngleX = swing * 0.6F;
	}
}
