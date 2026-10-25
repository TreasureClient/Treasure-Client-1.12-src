package org.taumc.celeritas.impl.render.terrain.compile;

//? if 1.10.2 {
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.util.QuadUtil;
import net.lax1dude.eaglercraft.opengl.WorldRenderer;
import org.taumc.celeritas.lwjgl.GL11;
import org.taumc.celeritas.CeleritasVintage;
import net.minecraft.client.renderer.texture.TextureMap;
import org.taumc.celeritas.impl.world.WorldSlice;
import net.lax1dude.eaglercraft.internal.buffer.ByteBuffer;
import java.util.Objects;

public class VintageChunkBuildContext extends ChunkBuildContext {
    public static final BlockRenderLayer[] LAYERS = BlockRenderLayer.values();
    private final TextureMap textureAtlas;
    private final WorldRenderer[] worldRenderers = new WorldRenderer[LAYERS.length];
    private final boolean[] usedWorldRenderers = new boolean[LAYERS.length];
    private int offX;
    private int offY;
    private int offZ;
    private final WorldSlice worldSlice;
    private final RenderPassConfiguration<?> renderPassConfiguration;
    private final boolean useRenderPassOptimization;

    public VintageChunkBuildContext(WorldClient world, RenderPassConfiguration renderPassConfiguration) {
        super(renderPassConfiguration);
        this.renderPassConfiguration = renderPassConfiguration;
        this.worldSlice = new WorldSlice(world);
        this.textureAtlas = Minecraft.getMinecraft().getTextureMapBlocks();
        this.useRenderPassOptimization = CeleritasVintage.options().performance.useRenderPassOptimization;
    }

    public void setupTranslation(int x, int y, int z) {
        this.offX = x;
        this.offY = y;
        this.offZ = z;
    }

    public WorldRenderer getBufferForLayer(BlockRenderLayer layer) {
        var builder = this.worldRenderers[layer.ordinal()];
        if (builder == null) {
            builder = new ChunkBufferBuilder(131072, this, layer);
            this.worldRenderers[layer.ordinal()] = builder;
        }
        if (!this.usedWorldRenderers[layer.ordinal()]) {
            builder.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            builder.setTranslation(-this.offX, -this.offY, -this.offZ);
            this.usedWorldRenderers[layer.ordinal()] = true;
        }
        return builder;
    }

    public void convertVanillaDataToCeleritasData(ChunkBuildBuffers buffers) {
        var renderers = this.worldRenderers;
        var used = this.usedWorldRenderers;
        for (int i = 0; i < renderers.length; i++) {
            if (!used[i]) {
                continue;
            }
            var bufferBuilder = Objects.requireNonNull(renderers[i]);
            bufferBuilder.finishDrawing();
            used[i] = false;
            ByteBuffer rawBuffer = bufferBuilder.getByteBuffer();
            var material = buffers.getRenderPassConfiguration().getMaterialForRenderType(LAYERS[i]);
            copyBlockData(rawBuffer, buffers, material);
        }
    }

    @Override
    public void cleanup() {
        super.cleanup();
        this.worldSlice.reset();
        for (int i = 0; i < LAYERS.length; i++) {
            if (this.usedWorldRenderers[i]) {
                this.worldRenderers[i].finishDrawing();
                this.usedWorldRenderers[i] = false;
            }
        }
    }

    private Material selectMaterial(Material material, TextureAtlasSprite sprite) {
        if (sprite != null && sprite.getClass() == TextureAtlasSprite.class && !sprite.hasAnimationMetadata() && this.useRenderPassOptimization) {
            // Treasure Client: sprite transparency isn't analysed yet, so nothing gets downgraded
            var transparencyLevel = SpriteTransparencyLevel.TRANSLUCENT;
            if (transparencyLevel == SpriteTransparencyLevel.OPAQUE) {
                // Downgrade to solid
                return this.renderPassConfiguration.defaultSolidMaterial();
            } else if (material == this.renderPassConfiguration.defaultTranslucentMaterial() && transparencyLevel != SpriteTransparencyLevel.TRANSLUCENT) {
                // Downgrade to cutout
                return this.renderPassConfiguration.defaultCutoutMippedMaterial();
            }
        }
        return material;
    }

    private void copyBlockData(ByteBuffer source, ChunkBuildBuffers buffers, Material material) {
        // Treasure Client: read Eaglercraft's BLOCK vertices (pos, color, uv, lightmap) straight from the buffer
        var format = DefaultVertexFormats.BLOCK;
        int vsize = format.attribStride;
        int offColor = format.attribColorOffset;
        int offTex = format.attribTextureOffset;
        int offLight = format.attribLightmapOffset;
        int numQuads = source.limit() / (vsize * 4);
        int ptr = 0;
        var quad = ChunkVertexEncoder.Vertex.uninitializedQuad();
        var animatedSpritesList = ((MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity>) buffers.getSectionContextBundle()).animatedSprites;
        for (int q = 0; q < numQuads; q++) {
            float uSum = 0;
            float vSum = 0;
            for (int v = 0; v < 4; v++) {
                var vertex = quad[v];
                vertex.x = source.getFloat(ptr);
                vertex.y = source.getFloat(ptr + 4);
                vertex.z = source.getFloat(ptr + 8);
                vertex.color = source.getInt(ptr + offColor);
                vertex.u = source.getFloat(ptr + offTex);
                vertex.v = source.getFloat(ptr + offTex + 4);
                uSum += vertex.u;
                vSum += vertex.v;
                vertex.light = source.getInt(ptr + offLight);
                ptr += vsize;
            }
            TextureAtlasSprite sprite = this.textureAtlas.tcFindSpriteAt(uSum * 0.25F, vSum * 0.25F);
            if (sprite != null && sprite.hasAnimationMetadata()) {
                animatedSpritesList.add(sprite);
            }
            int trueNormal = QuadUtil.calculateNormal(quad);
            for (int v = 0; v < 4; v++) {
                var vertex = quad[v];
                vertex.vanillaNormal = trueNormal;
                vertex.trueNormal = trueNormal;
            }
            ModelQuadFacing facing = QuadUtil.findNormalFace(trueNormal);
            Material correctMaterial = selectMaterial(material, sprite);
            buffers.get(correctMaterial).getVertexBuffer(facing).push(quad, correctMaterial);
        }
    }

    @java.lang.SuppressWarnings("all")
    public int getOffX() {
        return this.offX;
    }

    @java.lang.SuppressWarnings("all")
    public int getOffY() {
        return this.offY;
    }

    @java.lang.SuppressWarnings("all")
    public int getOffZ() {
        return this.offZ;
    }

    @java.lang.SuppressWarnings("all")
    public WorldSlice getWorldSlice() {
        return this.worldSlice;
    }

}
