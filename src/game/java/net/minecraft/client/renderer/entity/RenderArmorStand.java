package net.minecraft.client.renderer.entity;

import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.client.model.ModelArmorStand;
import net.minecraft.client.model.ModelArmorStandArmor;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.renderer.entity.layers.LayerCustomHead;
import net.minecraft.client.renderer.entity.layers.LayerElytra;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

public class RenderArmorStand extends RenderLivingBase<EntityArmorStand> {
	/**
	 * A constant instance of the armor stand texture, wrapped inside a
	 * ResourceLocation wrapper.
	 */
	public static final ResourceLocation TEXTURE_ARMOR_STAND = new ResourceLocation(
			"textures/entity/armorstand/wood.png");

	public RenderArmorStand(RenderManager manager) {
		super(manager, new ModelArmorStand(), 0.0F);
		LayerBipedArmor layerbipedarmor = new LayerBipedArmor(this) {
			protected void initArmor() {
				this.modelLeggings = new ModelArmorStandArmor(0.5F);
				this.modelArmor = new ModelArmorStandArmor(1.0F);
			}
		};
		this.addLayer(layerbipedarmor);
		this.addLayer(new LayerHeldItem(this));
		this.addLayer(new LayerElytra(this));
		this.addLayer(new LayerCustomHead(this.getMainModel().bipedHead));
	}

	/**
	 * Returns the location of an entity's texture. Doesn't seem to be called unless
	 * you call Render.bindEntityTexture.
	 */
	protected ResourceLocation getEntityTexture(EntityArmorStand entity) {
		return TEXTURE_ARMOR_STAND;
	}

	public ModelArmorStand getMainModel() {
		return (ModelArmorStand) super.getMainModel();
	}

	protected void rotateCorpse(EntityArmorStand entityLiving, float p_77043_2_, float p_77043_3_, float partialTicks) {
		GlStateManager.rotate(180.0F - p_77043_3_, 0.0F, 1.0F, 0.0F);
		float f = (float) (entityLiving.world.getTotalWorldTime() - entityLiving.punchCooldown) + partialTicks;

		if (f < 5.0F) {
			GlStateManager.rotate(MathHelper.sin(f / 1.5F * (float) Math.PI) * 3.0F, 0.0F, 1.0F, 0.0F);
		}
	}

	private static final net.minecraft.inventory.EntityEquipmentSlot[] TC_SLOTS = net.minecraft.inventory.EntityEquipmentSlot
			.values();

	/** True when the stand is hidden from you and holds and wears nothing, so only its name can show. */
	private static boolean tcOnlyName(EntityArmorStand e) {
		if (!e.isInvisibleToPlayer(net.minecraft.client.Minecraft.getMinecraft().player)) {
			return false;
		}
		for (int i = 0; i < TC_SLOTS.length; ++i) {
			if (!e.getItemStackFromSlot(TC_SLOTS[i]).func_190926_b()) {
				return false;
			}
		}
		return true;
	}

	protected boolean canRenderName(EntityArmorStand entity) {
		return entity.getAlwaysRenderNameTag();
	}

	/**
	 * Renders the desired {@code T} type Entity.
	 */
	public void doRender(EntityArmorStand entity, double x, double y, double z, float entityYaw, float partialTicks) {
		// Treasure Client: servers use invisible, empty armor stands as floating text (holograms), often
		// dozens per lobby. Nothing of them shows but the name, yet each went through the whole model
		// setup (pose, light, every armor and item layer). Draw just the name, at the same place.
		if (!this.renderOutlines && tcOnlyName(entity)) {
			this.renderName(entity, x, y, z);
			return;
		}
		if (entity.hasMarker()) {
			this.renderMarker = true;
		}

		super.doRender(entity, x, y, z, entityYaw, partialTicks);

		if (entity.hasMarker()) {
			this.renderMarker = false;
		}
	}
}
