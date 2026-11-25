package net.treasureclient.cos;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Treasure Client cosmetics: hats and boots on a posed player model (the shapes are in
 * {@link TCWearShapes}, the same as the browser editions'). Each part is at most two draws: the
 * solid bits (lit like the player), then the glowing bits (gems, stars, flames) at full brightness.
 */
public final class TCWearDraw {
    private TCWearDraw() {}

    /** Collects the shapes' corners: x, y, z, u, v, r, g, b, nx, ny, nz. */
    private static final class Buf implements TCWearShapes.Sink {
        float[] f = new float[11 * 4 * 64];
        int n;

        @Override
        public void vertex(float x, float y, float z, float u, float v, float r, float g, float b, float nx, float ny, float nz) {
            if (n + 11 > f.length) f = java.util.Arrays.copyOf(f, f.length * 2);
            float[] d = f;
            d[n] = x; d[n + 1] = y; d[n + 2] = z; d[n + 3] = u; d[n + 4] = v;
            d[n + 5] = r; d[n + 6] = g; d[n + 7] = b; d[n + 8] = nx; d[n + 9] = ny; d[n + 10] = nz;
            n += 11;
        }

        float[] take() {
            return java.util.Arrays.copyOf(f, n);
        }
    }

    /** Reused for every part (what's in it is copied out when it's sent to be drawn). */
    private static final Buf BUF = new Buf();

    private static Buf fresh() {
        BUF.n = 0;
        return BUF;
    }

    /**
     * Draws the hat (on head) and boots (on the legs) of a model whose parts are already posed;
     * the pose stack is the model's own (before any part's turn).
     */
    public static void submit(TCEmit emit, PoseStack poseStack, TCCosmetics.Look look, ModelPart root, ModelPart head,
                              ModelPart rightLeg, ModelPart leftLeg, boolean hat, boolean boots, boolean active, int light, long ms) {
        float t = (ms % 3600000L) / 1000f;
        int acc = look.accent();
        boolean hatCustom = "custom".equals(look.hat), bootsCustom = "custom".equals(look.boots);
        String[] hk = hatCustom ? look.hkParts() : null, bk = bootsCustom ? look.bkParts() : null;
        int hatRgb = hat ? look.wearRgb(look.hatColor, TCWearShapes.naturalHat(look.hat), ms) : 0;
        int bootRgb = boots ? look.wearRgb(look.bootColor, TCWearShapes.naturalBoots(look.boots), ms) : 0;
        boolean natural = "natural".equals(look.hatColor);
        boolean hatGlow = hat && (hatCustom ? TCWearShapes.customHatGlows(hk[1], hk[2]) : TCWearShapes.hatGlows(look.hat));
        boolean bootGlow = boots && (bootsCustom ? TCWearShapes.customBootsGlow(bk[1]) : TCWearShapes.bootsGlow(look.boots));
        for (int pass = 0; pass < 2; ++pass) {
            boolean glow = pass == 1;
            if (glow && !hatGlow && !bootGlow) break;
            int lit = glow ? TCCosLayer.FULL_BRIGHT : light;
            if (hat && head != null && (!glow || hatGlow)) {
                Buf b = fresh();
                TCWearShapes.begin(b, glow);
                if (hatCustom) {
                    TCWearShapes.customHat(hk[0], hk[1], hk[2], TCCapeArt.paletteRgb(hk[3], acc), TCCapeArt.paletteRgb(hk[4], acc), t);
                } else {
                    TCWearShapes.drawHat(look.hat, hatRgb, natural, t);
                }
                part(emit, poseStack, root, head, b.take(), lit);
            }
            if (boots && (!glow || bootGlow)) {
                for (int leg = 0; leg < 2; ++leg) {
                    ModelPart p = leg == 0 ? rightLeg : leftLeg;
                    if (p == null) continue;
                    Buf b = fresh();
                    TCWearShapes.begin(b, glow);
                    int side = leg == 0 ? -1 : 1;
                    if (bootsCustom) {
                        TCWearShapes.customBoot(bk[0], bk[1], TCCapeArt.paletteRgb(bk[3], acc), TCCapeArt.paletteRgb(bk[4], acc), t, side, active);
                    } else {
                        TCWearShapes.drawBoot(look.boots, bootRgb, t, side, active);
                    }
                    part(emit, poseStack, root, p, b.take(), lit);
                }
            }
        }
    }

    private static void part(TCEmit emit, PoseStack poseStack, ModelPart root, ModelPart part, final float[] v, int light) {
        if (v.length == 0) return;
        poseStack.pushPose();
        // like Minecraft's own head layer: the model's root, then the part
        if (root != null) root.translateAndRotate(poseStack);
        part.translateAndRotate(poseStack);
        // the shapes are in model pixels
        poseStack.scale(0.0625f, 0.0625f, 0.0625f);
        emit.draw(poseStack, RenderTypes.entityTranslucent(TCCosRender.noise()), (pose, vc) -> {
            for (int i = 0; i < v.length; i += 11) {
                vc.addVertex(pose, v[i], v[i + 1], v[i + 2])
                    .setColor(v[i + 5], v[i + 6], v[i + 7], 1f)
                    .setUv(v[i + 3], v[i + 4])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, v[i + 8], v[i + 9], v[i + 10]);
            }
        });
        poseStack.popPose();
    }
}
