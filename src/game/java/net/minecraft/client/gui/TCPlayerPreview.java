package net.minecraft.client.gui;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.lax1dude.eaglercraft.profile.EaglerProfile;
import net.lax1dude.eaglercraft.profile.SkinModel;
import net.lax1dude.eaglercraft.profile.SkinPreviewRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

/**
 * Treasure Client: your player standing on the title screen (1.5 side-panel layout), with your
 * skin and cape. Drag to spin; the head follows the mouse while the player faces you.
 * Later the cosmetics (hats, wings and so on) are drawn here too.
 */
public final class TCPlayerPreview {
	private static ModelPlayer steve = null;
	private static ModelPlayer alex = null;

	private TCPlayerPreview() {
	}

	/**
	 * @param cx     centre of the player on screen
	 * @param feetY  where the feet stand
	 * @param scale  pixels per block (the player is 2 blocks tall)
	 * @param yaw    body turn in degrees (0 = facing you, a little to the side looks nicer)
	 */
	public static void draw(int cx, int feetY, float scale, float yaw, int mouseX, int mouseY) {
		SkinModel skinModel = EaglerProfile.getActiveSkinModel();
		ResourceLocation skin = EaglerProfile.getActiveSkinResourceLocation();
		ResourceLocation cape = EaglerProfile.getActiveCapeResourceLocation();
		boolean isAlex = skinModel == SkinModel.ALEX;
		if (skinModel != SkinModel.STEVE && !isAlex) {
			// zombie and the special high-poly skins: use Eaglercraft's own preview (fixed size)
			SkinPreviewRenderer.renderPreview(cx, feetY + 5, mouseX, mouseY, skinModel);
			return;
		}
		if (steve == null) {
			steve = new ModelPlayer(0.0f, false);
			steve.isChild = false;
			alex = new ModelPlayer(0.0f, true);
			alex.isChild = false;
		}
		ModelPlayer model = isAlex ? alex : steve;

		// head looks toward the mouse, less so as the body turns away from you
		float facing = Math.max(0.0f, MathHelper.cos(yaw * 0.017453292F));
		float headY = feetY - scale * 1.75f;
		float headYaw = MathHelper.clamp((cx - mouseX) * 0.35f, -50.0f, 50.0f) * facing;
		float headPitch = MathHelper.clamp((mouseY - headY) * 0.3f, -30.0f, 30.0f) * facing;
		float ticks = (float) (EagRuntime.steadyTimeMillis() % 2000000L) / 50.0f;

		GlStateManager.enableTexture2D();
		GlStateManager.disableBlend();
		GlStateManager.disableCull();
		GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
		GlStateManager.pushMatrix();
		// model space: head top at y = -0.5, feet at y = 1.5 (in blocks)
		GlStateManager.translate((float) cx, feetY - 1.5f * scale, 100.0f);
		GlStateManager.scale(scale, scale, scale);
		GlStateManager.rotate(180.0f, 1.0f, 0.0f, 0.0f);
		GlStateManager.scale(1.0f, -1.0f, 1.0f);
		RenderHelper.enableGUIStandardItemLighting();
		GlStateManager.rotate(yaw, 0.0f, 1.0f, 0.0f);
		if (skin != null) {
			Minecraft.getMinecraft().getTextureManager().bindTexture(skin);
		}
		model.render(null, 0.0f, 0.0f, ticks, headYaw, headPitch, 0.0625f);
		if (cape != null) {
			Minecraft.getMinecraft().getTextureManager().bindTexture(cape);
			GlStateManager.pushMatrix();
			GlStateManager.translate(0.0F, 0.0F, 0.125F);
			GlStateManager.rotate(6.0F + MathHelper.sin(ticks * 0.05f) * 2.0F, 1.0F, 0.0F, 0.0F);
			GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
			model.renderCape(0.0625f);
			GlStateManager.popMatrix();
		}
		GlStateManager.popMatrix();
		RenderHelper.disableStandardItemLighting();
		GlStateManager.disableLighting();
		GlStateManager.enableBlend();
	}
}
