package net.minecraft.client.renderer.entity;

import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.util.ResourceLocation;

/**
 * Treasure Client: draws a ViaBackwards stand-in mob (a squid for a salmon, a cow for a
 * hoglin...) with the real modern model and texture, and hides the "Salmon" style name
 * tag ViaBackwards puts on it. Picked by {@link TCViaMobs}.
 */
final class RenderViaMob extends RenderLiving<EntityLiving> {

	private final ResourceLocation[] tex;
	private final boolean tentacles;

	RenderViaMob(RenderManager rm, TCViaModel model, float shadow, boolean tentacles, ResourceLocation... tex) {
		super(rm, model, shadow);
		this.tex = tex;
		this.tentacles = tentacles;
	}

	protected ResourceLocation getEntityTexture(EntityLiving entity) {
		return tex.length == 1 ? tex[0] : tex[(entity.getEntityId() & 0x7fffffff) % tex.length];
	}

	protected boolean canRenderName(EntityLiving entity) {
		return false;
	}

	protected void preRenderCallback(EntityLiving entity, float partialTicks) {
		if (entity.isChild()) {
			GlStateManager.scale(0.5F, 0.5F, 0.5F);
		}
	}

	protected float handleRotationFloat(EntityLiving entity, float partialTicks) {
		if (tentacles && entity instanceof EntitySquid) {
			EntitySquid s = (EntitySquid) entity;
			return s.lastTentacleAngle + (s.tentacleAngle - s.lastTentacleAngle) * partialTicks;
		}
		return super.handleRotationFloat(entity, partialTicks);
	}

	protected void rotateCorpse(EntityLiving entity, float ageInTicks, float yaw, float partialTicks) {
		if (tentacles && entity instanceof EntitySquid) {
			EntitySquid s = (EntitySquid) entity;
			float pitch = s.prevSquidPitch + (s.squidPitch - s.prevSquidPitch) * partialTicks;
			float roll = s.prevSquidYaw + (s.squidYaw - s.prevSquidYaw) * partialTicks;
			GlStateManager.translate(0.0F, 0.5F, 0.0F);
			GlStateManager.rotate(180.0F - yaw, 0.0F, 1.0F, 0.0F);
			GlStateManager.rotate(pitch, 1.0F, 0.0F, 0.0F);
			GlStateManager.rotate(roll, 0.0F, 1.0F, 0.0F);
			GlStateManager.translate(0.0F, -1.2F, 0.0F);
			return;
		}
		super.rotateCorpse(entity, ageInTicks, yaw, partialTicks);
	}
}
