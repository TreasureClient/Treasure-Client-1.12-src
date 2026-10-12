package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.ModelBee;
import net.minecraft.entity.passive.EntityBee;
import net.minecraft.util.ResourceLocation;

/**
 * Treasure Client — renderer for the modern Bee (see {@link EntityBee}).
 */
public class RenderBee extends RenderLiving<EntityBee> {
	private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/bee.png");

	public RenderBee(RenderManager renderManagerIn) {
		super(renderManagerIn, new ModelBee(), 0.3F);
	}

	protected ResourceLocation getEntityTexture(EntityBee entity) {
		return TEXTURE;
	}
}
