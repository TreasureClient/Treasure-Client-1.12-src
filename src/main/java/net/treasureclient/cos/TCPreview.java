package net.treasureclient.cos;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Treasure Client cosmetics on the menus' player picture (the main menu, the Cosmetics screen and
 * Skin Customization): your cape, hat, boots and aura. Minecraft's own picture there has no cape,
 * so the cape is drawn here too (a 10 x 16 pixel board hanging from the shoulders, like the game's).
 */
public final class TCPreview {
    private TCPreview() {}

    /**
     * Called right after the player picture's model is drawn. poseStack = the model's transform,
     * root = the model's root part, rotY = how far the picture is turned (degrees).
     */
    public static void draw(TCEmit emit, PoseStack poseStack, ModelPart root, float rotY) {
        TCCosmetics.Look look = TCCosmetics.mine();
        if (look == null || look.isEmpty() || root == null) return;
        try {
            long ms = System.currentTimeMillis();
            ModelPart head = part(root, "head"), body = part(root, "body");
            ModelPart rightLeg = part(root, "right_leg"), leftLeg = part(root, "left_leg");
            if (look.hasCape() && body != null) {
                Identifier tex = TCCosRender.cape(look);
                if (tex != null) cape(emit, poseStack, root, body, tex);
            }
            if (look.hasHat() || look.hasBoots()) {
                TCWearDraw.submit(emit, poseStack, look, root, head, rightLeg, leftLeg, look.hasHat(), look.hasBoots(), false,
                    TCCosLayer.FULL_BRIGHT, ms);
            }
            if (look.hasAura()) {
                TCAuraShapes.Out out = new TCAuraShapes.Out();
                // the picture faces you: dots face along its depth
                TCAuraShapes.aura(out, look, ms, 1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
                if (out.quads() > 0) {
                    poseStack.pushPose();
                    // from the model (y down, turned) to the feet, y up, facing you
                    poseStack.translate(0f, 1.5f, 0f);
                    poseStack.mulPose(new Matrix4f().rotationY((float) Math.toRadians(rotY)));
                    poseStack.scale(1f, -1f, 1f);
                    TCCosLayer.glows(emit, poseStack, out);
                    poseStack.popPose();
                }
            }
        } catch (RuntimeException e) {
            // never break the menu over a cosmetic
            if (!warned) {
                warned = true;
                System.err.println("[Treasure Client] cosmetics preview: " + e);
            }
        }
    }

    private static boolean warned;

    static ModelPart part(ModelPart root, String name) {
        try {
            return root.hasChild(name) ? root.getChild(name) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ the cape

    /** The cape hangs from the top of the back, tilted out 6 degrees (like a standing player's). */
    private static final float TILT = (float) Math.toRadians(6.0);

    private static void cape(TCEmit emit, PoseStack poseStack, ModelPart root, ModelPart body, Identifier tex) {
        poseStack.pushPose();
        root.translateAndRotate(poseStack);
        body.translateAndRotate(poseStack);
        poseStack.scale(1f / 16f, 1f / 16f, 1f / 16f);
        emit.draw(poseStack, RenderTypes.entitySolid(tex), (pose, vc) -> {
            // model pixels, y down, the back towards +z: x -5..5, y 0..16, z 0 (inside) .. 1 (outside).
            // Corners go round the way Minecraft's own boxes do; texture spots are in the 64 x 32 cape
            // layout (the outside at 1,1-11,17, the inside at 12,1-22,17), the same way round as the
            // game's cape, so the picture matches the one on your back in the world.
            // outside (+z)
            face(pose, vc, -5, 0, 1, 11, 1, 5, 0, 1, 1, 1, 5, 16, 1, 1, 17, -5, 16, 1, 11, 17, 0, 0, 1);
            // inside (-z)
            face(pose, vc, 5, 0, 0, 22, 1, -5, 0, 0, 12, 1, -5, 16, 0, 12, 17, 5, 16, 0, 22, 17, 0, 0, -1);
            // the player's left edge (+x) and right edge (-x)
            face(pose, vc, 5, 0, 1, 0, 1, 5, 0, 0, 1, 1, 5, 16, 0, 1, 17, 5, 16, 1, 0, 17, 1, 0, 0);
            face(pose, vc, -5, 0, 0, 11, 1, -5, 0, 1, 12, 1, -5, 16, 1, 12, 17, -5, 16, 0, 11, 17, -1, 0, 0);
            // top (-y) and bottom (+y)
            face(pose, vc, 5, 0, 1, 1, 0, -5, 0, 1, 11, 0, -5, 0, 0, 11, 1, 5, 0, 0, 1, 1, 0, -1, 0);
            face(pose, vc, 5, 16, 0, 11, 0, -5, 16, 0, 21, 0, -5, 16, 1, 21, 1, 5, 16, 1, 11, 1, 0, 1, 0);
        });
        poseStack.popPose();
    }

    /** One side of the cape: four corners (x, y, z, then u, v in cape-texture pixels) and which way it faces. */
    private static void face(PoseStack.Pose pose, VertexConsumer vc,
                             float ax, float ay, float az, float au, float av, float bx, float by, float bz, float bu, float bv,
                             float cx, float cy, float cz, float cu, float cv, float dx, float dy, float dz, float du, float dv,
                             float nx, float ny, float nz) {
        // the normal turns with the cape
        float c = (float) Math.cos(TILT), s = (float) Math.sin(TILT);
        float tny = ny * c - nz * s, tnz = nz * c + ny * s;
        corner(pose, vc, ax, ay, az, au, av, nx, tny, tnz);
        corner(pose, vc, bx, by, bz, bu, bv, nx, tny, tnz);
        corner(pose, vc, cx, cy, cz, cu, cv, nx, tny, tnz);
        corner(pose, vc, dx, dy, dz, du, dv, nx, tny, tnz);
    }

    private static void corner(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) {
        float c = (float) Math.cos(TILT), s = (float) Math.sin(TILT);
        // turn round the top edge so the bottom swings back, then sit just behind the body (z 2..3)
        float ty = y * c - z * s, tz = z * c + y * s;
        vc.addVertex(pose, x, ty, tz + 2f)
            .setColor(1f, 1f, 1f, 1f)
            .setUv(u / 64f, v / 32f)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(TCCosLayer.FULL_BRIGHT)
            .setNormal(pose, nx, ny, nz);
    }
}
