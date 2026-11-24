package net.treasureclient.cos;

import com.mojang.blaze3d.vertex.PoseStack;

import org.joml.Matrix4f;

import net.minecraft.client.Camera;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Pose;
import net.treasureclient.compat.V;

/**
 * Treasure Client cosmetics on players in the world: hats and boots (built from little blocks like
 * Minecraft's own models, lit like the player, the glowing bits at full brightness), auras and boot
 * trails (soft glows at full brightness; only the 16 nearest are drawn, fading out from 28 to 40
 * blocks away). A helmet or a head item hides the hat, armour boots hide the boots. In the
 * inventory's player picture only the hat and boots are drawn.
 */
public class TCCosLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    static final int FULL_BRIGHT = 0xF000F0;
    private static final int MAX_AURAS = 16;
    private static final double FAR = 40.0, FADE_FROM = 28.0;

    private static long auraFrame;
    private static int aurasThisFrame;

    public TCCosLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        TCCosmetics.Look look = ((TCAvatarState) state).tc$look();
        if (look == null || state.isInvisible) return;
        TCEmit emit = TCEmit.of(collector);
        PlayerModel model = getParentModel();
        long ms = System.currentTimeMillis();
        boolean hat = look.hasHat() && state.headEquipment.isEmpty() && state.headItem.isEmpty();
        boolean boots = look.hasBoots() && state.feetEquipment.isEmpty();
        if (hat || boots) TCWearDraw.submit(emit, poseStack, look, model.root(), model.head, model.rightLeg, model.leftLeg, hat, boots,
            ((TCAvatarState) state).tc$active(), light, ms);

        if (TCCosRender.inGui()) return;
        TCAuraShapes.Trail trail = ((TCAvatarState) state).tc$trail();
        boolean aura = look.hasAura() && !state.hasPose(Pose.SLEEPING);
        if (!aura && trail == null) return;
        double dist = Math.sqrt(state.distanceToCameraSq);
        if (dist > FAR) return;
        long frame = net.minecraft.client.Minecraft.getInstance().getFrameTimeNs();
        if (frame != auraFrame) {
            auraFrame = frame;
            aurasThisFrame = 0;
        }
        if (++aurasThisFrame > MAX_AURAS) return;
        float fade = dist < FADE_FROM ? 1f : (float) ((FAR - dist) / (FAR - FADE_FROM));

        // the camera's directions (world): the dots always face it
        Camera cam = V.camera();
        float rX = -cam.leftVector().x(), rY = -cam.leftVector().y(), rZ = -cam.leftVector().z();
        float uX = cam.upVector().x(), uY = cam.upVector().y(), uZ = cam.upVector().z();

        TCAuraShapes.Out out = new TCAuraShapes.Out();
        if (trail != null) trail.draw(out, look, state.x, state.y, state.z, fade, ms, rX, rY, rZ, uX, uY, uZ);
        if (aura) TCAuraShapes.aura(out, look, ms, fade, state.id * 0.618f, rX, rY, rZ, uX, uY, uZ);
        if (out.quads() == 0) return;

        poseStack.pushPose();
        // undo the player's turn, flip and size, back to the feet in world directions:
        // the renderer did scale(s), turn(180 - body), scale(-1, -1, 1), scale(0.9375), move(0, -1.501, 0)
        poseStack.translate(0.0f, 1.501f, 0.0f);
        poseStack.scale(1f / 0.9375f, 1f / 0.9375f, 1f / 0.9375f);
        poseStack.scale(-1f, -1f, 1f);
        poseStack.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-(180.0f - state.bodyRot))));
        float s = state.scale > 0.01f ? 1f / state.scale : 1f;
        poseStack.scale(s, s, s);
        glows(emit, poseStack, out);
        poseStack.popPose();
    }

    /**
     * Sends glows to be drawn: full brightness, softly blended, and they don't hide anything behind
     * them (so they need no sorting).
     */
    static void glows(TCEmit emit, PoseStack poseStack, TCAuraShapes.Out out) {
        final float[] f = java.util.Arrays.copyOf(out.f, out.n);
        emit.draw(poseStack, RenderTypes.eyes(TCCosRender.dot()), (pose, vc) -> {
            // each quad both ways round (this draw type hides faces seen from behind)
            for (int q = 0; q + 36 <= f.length; q += 36) {
                for (int k = 0; k < 4; ++k) corner(pose, vc, f, q + k * 9);
                for (int k = 3; k >= 0; --k) corner(pose, vc, f, q + k * 9);
            }
        });
    }

    private static void corner(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer vc, float[] f, int i) {
        float a = Math.max(0f, Math.min(1f, f[i + 8]));
        vc.addVertex(pose, f[i], f[i + 1], f[i + 2])
            .setColor(f[i + 5], f[i + 6], f[i + 7], a)
            .setUv(f[i + 3], f[i + 4])
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(FULL_BRIGHT)
            .setNormal(pose, 0f, 1f, 0f);
    }
}
