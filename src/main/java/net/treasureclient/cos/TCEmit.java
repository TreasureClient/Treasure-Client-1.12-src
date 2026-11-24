package net.treasureclient.cos;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Treasure Client cosmetics: where shapes are sent to be drawn. In the world (and in newer
 * versions' menu previews) they're queued with the frame's other draws; 1.21.11's menu preview
 * draws them straight away.
 */
public interface TCEmit {
    /** Draws geo with the pose stack's current transform. */
    void draw(PoseStack poseStack, RenderType type, Geo geo);

    interface Geo {
        void render(PoseStack.Pose pose, VertexConsumer vc);
    }

    static TCEmit of(SubmitNodeCollector collector) {
        return (ps, type, geo) -> collector.submitCustomGeometry(ps, type, geo::render);
    }
}
