package org.taumc.celeritas.impl;

import java.util.Map;

import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.lax1dude.eaglercraft.opengl.TCRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.DestroyBlockProgress;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.taumc.celeritas.impl.render.terrain.CameraHelper;
import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer;
import org.taumc.celeritas.lwjgl.webgl.WebGLService;

/**
 * Treasure Client: connects the Celeritas chunk renderer to RenderGlobal.
 *
 * <p>RenderGlobal calls into this class at the points where Celeritas' Forge
 * mixins used to hook in. If Celeritas fails at any point, it is switched off
 * and the game falls back to the vanilla chunk renderer.
 */
public final class TreasureCeleritas {
    private static final Logger LOGGER = LogManager.getLogger("Celeritas");

    /** Player setting: use the Celeritas renderer when the browser supports it. */
    public static boolean enabled = true;

    private static boolean failed = false;
    private static String failure;
    private static CeleritasWorldRenderer renderer;

    private static final FrustumIntersection frustum = new FrustumIntersection();
    private static final Matrix4f projection = new Matrix4f();
    private static final Matrix4f modelView = new Matrix4f();
    private static final float[] matrixTmp = new float[16];

    private TreasureCeleritas() {
    }

    /** Whether RenderGlobal should use Celeritas instead of its own chunk renderer. */
    public static boolean isActive() {
        return enabled && !failed && isSupported();
    }

    private static Boolean supported;

    private static boolean isSupported() {
        if (supported == null) {
            supported = WebGLService.isSupported();
            if (!supported) {
                LOGGER.info("Celeritas needs WebGL 2; using the vanilla chunk renderer");
            }
        }
        return supported;
    }

    public static CeleritasWorldRenderer getRenderer() {
        return isActive() ? renderer : null;
    }

    private static CeleritasWorldRenderer renderer() {
        if (renderer == null) {
            renderer = new CeleritasWorldRenderer();
        }
        return renderer;
    }

    /** Switches Celeritas off after an error so vanilla rendering takes over. */
    private static void fail(String what, Throwable t) {
        LOGGER.error("Celeritas failed while " + what + "; switching to the vanilla chunk renderer", t);
        failed = true;
        failure = what + ": " + t;
        try {
            if (renderer != null) {
                RenderDevice.enterManagedCode();
                try {
                    renderer.setWorld(null);
                } finally {
                    RenderDevice.exitManagedCode();
                }
            }
        } catch (Throwable t2) {
            LOGGER.error("Could not clean up Celeritas", t2);
        }
        renderer = null;
    }

    /**
     * Player toggle from the Mod Menu. Swaps between Celeritas and the vanilla
     * chunk renderer, rebuilding the world's chunks if one is loaded. Turning it
     * back on also retries after an earlier failure.
     */
    public static void setEnabled(boolean on) {
        if (enabled == on) {
            return;
        }
        enabled = on;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (on) {
            failed = false;
            failure = null;
            if (mc.world != null) {
                setWorld(mc.world);
            }
        } else if (renderer != null) {
            try {
                RenderDevice.enterManagedCode();
                try {
                    renderer.setWorld(null);
                } finally {
                    RenderDevice.exitManagedCode();
                }
            } catch (Throwable t) {
                LOGGER.error("Could not shut down Celeritas", t);
            }
            renderer = null;
        }
        if (mc.world != null && mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
    }

    /** Called when RenderGlobal's world changes (null when leaving a world). */
    public static void setWorld(WorldClient world) {
        if (!isActive() && renderer == null) {
            return;
        }
        try {
            RenderDevice.enterManagedCode();
            try {
                renderer().setWorld(isActive() ? world : null);
            } finally {
                RenderDevice.exitManagedCode();
            }
        } catch (Throwable t) {
            fail("loading the world", t);
        }
    }

    /**
     * Called at the end of RenderGlobal.loadRenderers. Returns false if Celeritas
     * just failed, meaning RenderGlobal must set up vanilla rendering instead.
     */
    public static boolean reload() {
        if (!isActive()) {
            return true;
        }
        try {
            RenderDevice.enterManagedCode();
            try {
                renderer().reload();
            } finally {
                RenderDevice.exitManagedCode();
            }
            return true;
        } catch (Throwable t) {
            fail("loading renderers", t);
            return false;
        }
    }

    /** Replaces RenderGlobal.setupTerrain: decides which chunks are visible and queues rebuilds. */
    public static void setupTerrain(Entity viewEntity, double partialTicks, int frame, boolean spectator) {
        try {
            RenderDevice.enterManagedCode();
            try {
                renderer().setupTerrain(createViewport(viewEntity, partialTicks),
                        CeleritasWorldRenderer.captureCameraState(partialTicks), frame, spectator, false);
            } finally {
                RenderDevice.exitManagedCode();
            }
        } catch (Throwable t) {
            fail("setting up terrain", t);
            Minecraft.getMinecraft().renderGlobal.loadRenderers();
        }
    }

    private static Viewport createViewport(Entity viewEntity, double partialTicks) {
        // Same as vanilla's ClippingHelperImpl: built from the current camera matrices
        TCRenderState.getProjection(matrixTmp);
        projection.set(matrixTmp);
        TCRenderState.getModelView(matrixTmp);
        modelView.set(matrixTmp);
        frustum.set(projection.mul(modelView, new Matrix4f()), true);

        // Same position as the vanilla frustum (the view entity's feet)
        double x = viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * partialTicks;
        double y = viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * partialTicks;
        double z = viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * partialTicks;

        // The camera itself sits at the eyes (or behind the player in third person)
        Vector3f offset = CameraHelper.getThirdPersonOffset();
        final float ox = offset.x, oy = offset.y, oz = offset.z;
        final FrustumIntersection f = frustum;
        return new Viewport((minX, minY, minZ, maxX, maxY, maxZ) -> f.testAab(minX + ox, minY + oy, minZ + oz, maxX + ox, maxY + oy, maxZ + oz),
                new Vector3d(x + ox, y + oy, z + oz));
    }

    /** Replaces RenderGlobal.renderBlockLayer. */
    public static int renderBlockLayer(BlockRenderLayer layer, double partialTicks, Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        RenderHelper.disableStandardItemLighting();

        if (!mc.gameSettings.fog && mc.entityRenderer.fogStandard) {
            GlStateManager.disableFog();
        }

        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture(mc.getTextureMapBlocks().getGlTextureId());
        GlStateManager.enableTexture2D();
        mc.entityRenderer.enableLightmap();

        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;

        try {
            RenderDevice.enterManagedCode();
            try {
                renderer().drawChunkLayer(layer, x, y, z);
            } finally {
                RenderDevice.exitManagedCode();
            }
        } catch (Throwable t) {
            fail("drawing terrain", t);
            mc.renderGlobal.loadRenderers();
        }

        mc.entityRenderer.disableLightmap();
        return 1;
    }

    /** Replaces RenderGlobal.markBlocksForUpdate. */
    public static void markBlocksForUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        CeleritasWorldRenderer r = renderer;
        if (r != null) {
            r.scheduleRebuildForBlockArea(minX, minY, minZ, maxX, maxY, maxZ, important);
        }
    }

    public static void scheduleTerrainUpdate() {
        CeleritasWorldRenderer r = renderer;
        if (r != null) {
            r.scheduleTerrainUpdate();
        }
    }

    /** Draws the visible tile entities (chests, signs, banners...). Returns how many were drawn. */
    public static int renderTileEntities(Map<Integer, DestroyBlockProgress> damagedBlocks, float partialTicks) {
        CeleritasWorldRenderer r = renderer;
        if (r == null) {
            return 0;
        }
        return r.renderBlockEntities(new CeleritasWorldRenderer.TileEntityRenderContext(damagedBlocks, partialTicks));
    }

    /** Whether an entity is inside a chunk Celeritas considers visible. */
    public static boolean isEntityVisible(Entity entity) {
        CeleritasWorldRenderer r = renderer;
        return r == null || r.isEntityVisible(entity);
    }

    public static int getRenderedChunks() {
        CeleritasWorldRenderer r = renderer;
        return r != null ? r.getVisibleChunkCount() : 0;
    }

    public static String getDebugInfoRenders() {
        CeleritasWorldRenderer r = renderer;
        return r != null ? r.getChunksDebugString() : "Celeritas: off";
    }

    /** True when this browser can run Celeritas (WASM build with WebGL 2). */
    public static boolean isAvailable() {
        return isSupported();
    }

    /** Whether Celeritas is drawing the world right now. */
    public static boolean isRunning() {
        return renderer != null && isActive();
    }

    /** Why Celeritas switched itself off, or null. */
    public static String getFailure() {
        return failure;
    }

    /** Shown on the F3 chunk line after Celeritas switched itself off. */
    public static String getFailureNote() {
        return failure == null ? "" : " [Celeritas off - " + failure + "]";
    }

    public static java.util.Collection<String> getDebugStrings() {
        CeleritasWorldRenderer r = renderer;
        return r != null ? r.getDebugStrings() : java.util.Collections.<String>emptyList();
    }
}
