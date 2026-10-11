package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.ModelCopperGolem;
import net.minecraft.entity.passive.EntityCopperGolem;
import net.minecraft.util.ResourceLocation;

/**
 * Treasure Client — renderer for the modern Copper Golem (see
 * {@link EntityCopperGolem}).
 */
public class RenderCopperGolem extends RenderLiving<EntityCopperGolem> {
	private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/copper_golem.png");

	public RenderCopperGolem(RenderManager renderManagerIn) {
		super(renderManagerIn, new ModelCopperGolem(), 0.5F);
	}

	protected ResourceLocation getEntityTexture(EntityCopperGolem entity) {
		return TEXTURE;
	}
}
