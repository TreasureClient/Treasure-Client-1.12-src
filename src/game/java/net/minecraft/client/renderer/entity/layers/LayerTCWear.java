package net.minecraft.client.renderer.entity.layers;

import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.tccos.TCCosmetics;
import net.minecraft.client.tccos.TCWear;
import net.minecraft.inventory.EntityEquipmentSlot;

/**
 * Treasure Client cosmetics: draws a player's hat and boots. A helmet (or a pumpkin or head)
 * hides the hat, and armour boots hide the boots, so they never poke through armour.
 */
public class LayerTCWear implements LayerRenderer<AbstractClientPlayer> {
	private final RenderPlayer playerRenderer;

	public LayerTCWear(RenderPlayer playerRendererIn) {
		this.playerRenderer = playerRendererIn;
	}

	public void doRenderLayer(AbstractClientPlayer p, float limbSwing, float limbSwingAmount, float partialTicks,
			float ageInTicks, float netHeadYaw, float headPitch, float scale) {
		if (p.isInvisible()) return;
		TCCosmetics.Look look = TCCosmetics.of(p);
		if (look == null || (!look.hasHat() && !look.hasBoots())) return;
		boolean hatOk = p.getItemStackFromSlot(EntityEquipmentSlot.HEAD).func_190926_b();
		boolean bootsOk = p.getItemStackFromSlot(EntityEquipmentSlot.FEET).func_190926_b();
		if (!hatOk && !bootsOk) return;
		GlStateManager.pushMatrix();
		if (p.isSneaking()) {
			GlStateManager.translate(0.0F, 0.2F, 0.0F); // the model moves down when sneaking
		}
		TCWear.render(look, this.playerRenderer.getMainModel(), hatOk, bootsOk, !p.onGround || p.isSprinting() || p.isElytraFlying());
		GlStateManager.popMatrix();
	}

	public boolean shouldCombineTextures() {
		return false;
	}
}
