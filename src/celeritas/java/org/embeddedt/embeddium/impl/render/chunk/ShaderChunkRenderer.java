package org.embeddedt.embeddium.impl.render.chunk;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.shader.*;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.shader.*;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class ShaderChunkRenderer implements ChunkRenderer {
    private static final Logger LOGGER = LogManager.getLogger("ShaderChunkRenderer");

    private final Map<ChunkShaderOptions, GlProgram<ChunkShaderInterface>> programs = new Object2ObjectOpenHashMap<>();

    protected final RenderPassConfiguration<?> renderPassConfiguration;

    protected final RenderDevice device;

    protected GlProgram<ChunkShaderInterface> activeProgram;

    protected final boolean enableLegacyGLPatches;

    protected final ChunkShaderEnvironment environment;

    public ShaderChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration, FogService fogService) {
        this.device = device;
        this.environment = new ChunkShaderEnvironment(fogService);
        this.renderPassConfiguration = renderPassConfiguration;
        this.enableLegacyGLPatches = ShaderLoader.useLegacyGlsl();
        if (this.enableLegacyGLPatches) {
            LOGGER.warn("System does not support modern GLSL, will attempt to patch terrain shaders");
        }
    }

    // Treasure Client: compiled terrain shaders are kept for the whole session. Before,
    // every world change (each server switch or respawn into another world) threw them
    // away and compiled them again, one of the slowest things a browser can do.
    // A program only depends on its defines and vertex attributes, so those are the key.
    private static final Map<String, GlProgram<ChunkShaderInterface>> SHARED_PROGRAMS = new Object2ObjectOpenHashMap<>();

    private String tcProgramKey(ChunkShaderOptions options) {
        StringBuilder sb = new StringBuilder();
        sb.append(this.enableLegacyGLPatches).append('|');
        for (String d : options.constants().getDefineStrings()) {
            sb.append(d).append(';');
        }
        sb.append('|');
        for (var attr : options.pass().vertexType().getVertexFormat().getAttributes()) {
            sb.append(attr.getName()).append(';');
        }
        sb.append('|');
        for (var c : options.components()) {
            sb.append(c.getClass().getName()).append(':').append(String.valueOf(c)).append(';');
        }
        return sb.toString();
    }

    protected GlProgram<ChunkShaderInterface> compileProgram(ChunkShaderOptions options) {
        GlProgram<ChunkShaderInterface> program = this.programs.get(options);

        if (program == null && !this.programs.containsKey(options)) {
            String key = this.tcProgramKey(options);
            program = SHARED_PROGRAMS.get(key);
            if (program == null) {
                try {
                    program = this.createShader("blocks/block_layer_opaque", options);
                } catch(Exception e) {
                    LOGGER.error("There was an error creating a chunk program. Terrain will not render until this is fixed.", e);
                }
                if (program != null) {
                    SHARED_PROGRAMS.put(key, program);
                }
            }
            this.programs.put(options, program);
        }

        return program;
    }

    protected GlProgram<ChunkShaderInterface> createShader(String path, ChunkShaderOptions options) {
        ShaderConstants constants = options.constants();

        List<GlShader> loadedShaders = new ArrayList<>();

        loadedShaders.add(ShaderLoader.loadShader(ShaderType.VERTEX,
                "sodium:" + path + ".vsh", constants));

        loadedShaders.add(ShaderLoader.loadShader(ShaderType.FRAGMENT,
                "sodium:" + path + ".fsh", constants));

        try {
            var builder = GlProgram.builder("sodium:chunk_shader");
            loadedShaders.forEach(builder::attachShader);
            int i = 0;
            for (var attr : options.pass().vertexType().getVertexFormat().getAttributes()) {
                builder.bindAttribute(attr.getName(), i++);
            }
            if (!this.enableLegacyGLPatches) {
                builder.bindFragmentData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR);
            }
            return builder.link((shader) -> new DefaultChunkShaderInterface(shader, options, this.environment));
        } finally {
            loadedShaders.forEach(GlShader::delete);
        }
    }

    protected List<ChunkShaderComponent.Factory<?>> getShaderComponents() {
        var componentFactories = new ArrayList<ChunkShaderComponent.Factory<?>>(4);
        componentFactories.add(this.environment.fogService().getFogMode());
        return componentFactories;
    }

    protected void begin(TerrainRenderPass pass) {
        pass.startDrawing();

        ChunkShaderOptions options = new ChunkShaderOptions(getShaderComponents(), pass);

        this.activeProgram = this.compileProgram(options);

        if (this.activeProgram != null) {
            this.activeProgram.bind();
            this.activeProgram.getInterface()
                    .setupState(pass);
        }
    }

    protected void end(TerrainRenderPass pass) {
        if (this.activeProgram != null) {
            this.activeProgram.getInterface().restoreState();
            this.activeProgram.unbind();
            this.activeProgram = null;
        }


        pass.endDrawing();
    }

    @Override
    public void delete(CommandList commandList) {
        // Treasure Client: the programs are shared and kept (see SHARED_PROGRAMS)
        this.programs.clear();
    }

    @Override
    public RenderPassConfiguration<?> getRenderPassConfiguration() {
        return this.renderPassConfiguration;
    }
}
