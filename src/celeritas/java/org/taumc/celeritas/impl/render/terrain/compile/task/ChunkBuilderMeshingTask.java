package org.taumc.celeritas.impl.render.terrain.compile.task;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.GraphDirection;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.VisibilityEncoding;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;
import org.joml.Vector3d;
import org.taumc.celeritas.impl.render.terrain.compile.VintageChunkBuildContext;
import org.taumc.celeritas.impl.world.WorldSlice;
import org.taumc.celeritas.impl.world.cloned.ChunkRenderContext;

import java.util.*;

public class ChunkBuilderMeshingTask extends ChunkBuilderTask<ChunkBuildOutput> {
    private final RenderSection render;
    private final int buildTime;
    private final Vector3d camera;
    private final ChunkRenderContext renderContext;

    public ChunkBuilderMeshingTask(RenderSection render, ChunkRenderContext context, int time, Vector3d camera) {
        this.render = render;
        this.buildTime = time;
        this.camera = camera;
        this.renderContext = context;
    }

    // Treasure Client: a build can pause between layers and continue next frame (see ChunkBuilder.tcDeadlineNs)
    private MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity> tcRenderData;
    private VisGraph tcOccluder;
    private BlockPos tcPos;
    private int tcNextY = Integer.MIN_VALUE;
    private int tcNextZ = Integer.MIN_VALUE; // row inside layer tcNextY to continue from
    private boolean tcGroupNext;

    @Override
    public ChunkBuildOutput execute(ChunkBuildContext context, CancellationToken cancellationToken) {
        VintageChunkBuildContext buildContext = (VintageChunkBuildContext)context;

        int minX = this.render.getOriginX();
        int minY = this.render.getOriginY();
        int minZ = this.render.getOriginZ();

        int maxX = minX + 16;
        int maxY = minY + 16;
        int maxZ = minZ + 16;

        ChunkBuildBuffers buffers = buildContext.buffers;

        if (this.tcNextY == Integer.MIN_VALUE) {
            this.tcRenderData = new MinecraftBuiltRenderSectionData<>();
            this.tcOccluder = new VisGraph();
            buffers.init(this.tcRenderData, this.render.getSectionIndex());
            // Initialise with minX/minY/minZ so initial getBlockState crash context is correct
            this.tcPos = new BlockPos(minX, minY, minZ);
            buildContext.getWorldSlice().copyData(this.renderContext);
            buildContext.setupTranslation(minX, minY, minZ);
            this.tcNextY = minY;
            this.tcNextZ = minZ;
            // the world copy alone can use up the frame's build time: place blocks next frame
            long d0 = org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkBuilder.tcDeadlineNs;
            if (d0 != 0L && System.nanoTime() > d0) {
                org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkBuilder.tcPaused = true;
                return null;
            }
        }

        MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity> renderData = this.tcRenderData;
        VisGraph occluder = this.tcOccluder;
        BlockPos blockPos = this.tcPos;

        WorldSlice slice = buildContext.getWorldSlice();

        var dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();

        int startY = this.tcNextY;
        int startZ = this.tcNextZ == Integer.MIN_VALUE ? minZ : this.tcNextZ;
        boolean first = true; // always place at least one row, so the build keeps moving
        long deadline = org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkBuilder.tcDeadlineNs;

        try {
            for (int y = startY; y < maxY; y++) {
                if (cancellationToken.isCancelled()) {
                    return null;
                }

                for (int z = y == startY ? startZ : minZ; z < maxZ; z++) {
                    // Treasure Client: out of build time for this frame: stop at this row of 16
                    // blocks and continue from it next frame
                    if (!first && deadline != 0L && System.nanoTime() > deadline) {
                        this.tcNextY = y;
                        this.tcNextZ = z;
                        org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkBuilder.tcPaused = true;
                        return null;
                    }
                    first = false;
                    for (int x = minX; x < maxX; x++) {
                        blockPos.setPos(x, y, z);

                        IBlockState blockState = slice.getBlockState(blockPos);
                        var block = blockState.getBlock();

                        if (block == Blocks.AIR) {
                            continue;
                        }

                        if (block.hasTileEntity()) {
                            TileEntity tileEntity = slice.getTileEntity(blockPos);
                            if (tileEntity != null) {
                                TileEntitySpecialRenderer<TileEntity> tesr = TileEntityRendererDispatcher.instance.getSpecialRenderer(tileEntity);

                                if (tesr != null) {
                                    (tesr.isGlobalRenderer(tileEntity) ? renderData.globalBlockEntities : renderData.culledBlockEntities).add(tileEntity);
                                }
                            }
                        }

                        // Treasure Client: same as vanilla RenderChunk, each block draws into its one layer
                        if (blockState.getRenderType() != EnumBlockRenderType.INVISIBLE) {
                            var buffer = buildContext.getBufferForLayer(net.minecraft.block.TCLeafCulling.layer(block, blockState, slice, blockPos)); // Treasure Client (Leaf Culling)
                            dispatcher.renderBlock(blockState, blockPos, slice, buffer);
                        }

                        if (blockState.isOpaqueCube()) {
                            occluder.setOpaqueCube(blockPos);
                        }
                    }
                }
            }
        } catch (ReportedException ex) {
            // Propagate existing crashes (add context)
            throw fillCrashInfo(ex.getCrashReport(), slice, blockPos);
        } catch (Throwable ex) {
            // Create a new crash report for other exceptions (e.g. thrown in getQuads)
            throw fillCrashInfo(CrashReport.makeCrashReport(ex, "Encountered exception while building chunk meshes"), slice, blockPos);
        }

        // Treasure Client: if this frame's build time ran out while placing blocks, turn the result
        // into a mesh at the start of the next frame instead (that last step is a few ms on its own)
        if (startY < maxY && deadline != 0L && System.nanoTime() > deadline) {
            this.tcNextY = maxY;
            this.tcNextZ = minZ;
            org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkBuilder.tcPaused = true;
            return null;
        }

        // Treasure Client: the conversion and the mesh grouping can also continue next frame
        if (!this.tcGroupNext) {
            if (!buildContext.tcConvert(buffers, deadline)) {
                this.tcNextY = maxY;
                this.tcNextZ = minZ;
                org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkBuilder.tcPaused = true;
                return null;
            }
            if (deadline != 0L && System.nanoTime() > deadline) {
                this.tcGroupNext = true;
                this.tcNextY = maxY;
                this.tcNextZ = minZ;
                org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkBuilder.tcPaused = true;
                return null;
            }
        }

        Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes = BuiltSectionMeshParts.groupFromBuildBuffers(buffers, (float) (camera.x - minX), (float) (camera.y - minY), (float) (camera.z - minZ));

        if (!meshes.isEmpty()) {
            renderData.hasBlockGeometry = true;
        }

        encodeVisibilityData(occluder, renderData);

        return new ChunkBuildOutput(this.render, renderData, meshes, this.buildTime);
    }

    private ReportedException fillCrashInfo(CrashReport report, IBlockAccess slice, BlockPos pos) {
        CrashReportCategory crashReportSection = report.makeCategory("Block being rendered");

        IBlockState state = null;
        try {
            state = slice.getBlockState(pos);
        } catch (Exception ignored) {}
        CrashReportCategory.addBlockInfo(crashReportSection, pos, state);

        crashReportSection.addCrashSection("Chunk section", this.render);
        /*
        if (this.renderContext != null) {
            crashReportSection.addCrashSection("Render context volume", this.renderContext.getVolume());
        }

         */

        return new ReportedException(report);
    }

    private static final EnumFacing[] FACINGS = new EnumFacing[GraphDirection.COUNT];

    static {
        FACINGS[GraphDirection.UP] = EnumFacing.UP;
        FACINGS[GraphDirection.DOWN] = EnumFacing.DOWN;
        FACINGS[GraphDirection.WEST] = EnumFacing.WEST;
        FACINGS[GraphDirection.EAST] = EnumFacing.EAST;
        FACINGS[GraphDirection.NORTH] = EnumFacing.NORTH;
        FACINGS[GraphDirection.SOUTH] = EnumFacing.SOUTH;
    }

    private static void encodeVisibilityData(VisGraph occluder, MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity> renderData) {
        var data = occluder.computeVisibility();
        renderData.visibilityData = VisibilityEncoding.encode((from, to) -> data.isVisible(FACINGS[from], FACINGS[to]));
    }

}
