package org.embeddedt.embeddium.impl.render.chunk.terrain;

import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import java.util.Map;
import java.util.Objects;

/**
 * A terrain render pass corresponds to a draw call to render some subset of terrain geometry. Passes are generally
 * used for fixed configuration that will not change from quad to quad and allow for optimizations to be made
 * within the terrain shader code at compile time (e.g. omitting the fragment discard conditional entirely on the solid pass).
 * <p></p>
 * Geometry that shares the same terrain render pass may still be able to specify some more dynamic properties. See {@link Material}
 * for more information.
 */
public class TerrainRenderPass {
    /**
     * The friendly name of this render pass.
     */
    private final String name;
    /**
     * A callback used to set up/clear GPU pipeline state.
     */
    private final PipelineState pipelineState;
    /**
     * Whether sections on this render pass should be rendered farthest-to-nearest, rather than nearest-to-farthest.
     */
    private final boolean useReverseOrder;
    /**
     * Whether fragment alpha testing should be enabled for this render pass.
     */
    private final boolean fragmentDiscard;
    /**
     * Whether this render pass wants to opt in to translucency sorting if enabled.
     */
    private final boolean useTranslucencySorting;
    /**
     * Whether this render pass has no lightmap texture.
     */
    private final boolean hasNoLightmap;
    
    private final ChunkPrimitiveType primitiveType;
    
    private final ChunkVertexType vertexType;


    public record TessellationKey(GlVertexFormat vertexFormat, ChunkPrimitiveType primitiveType, boolean sorted) {
    }

    private final TessellationKey tessellationKey;
    private final Map<String, String> extraDefines;

    public TerrainRenderPass(String name, PipelineState pipelineState, boolean useReverseOrder, boolean fragmentDiscard, boolean useTranslucencySorting, boolean hasNoLightmap, ChunkVertexType vertexType, ChunkPrimitiveType primitiveType, Map<String, String> extraDefines) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("Name not specified for terrain pass");
        }
        Objects.requireNonNull(vertexType);
        Objects.requireNonNull(primitiveType);
        this.name = name;
        this.pipelineState = pipelineState;
        this.useReverseOrder = useReverseOrder;
        this.fragmentDiscard = fragmentDiscard;
        this.useTranslucencySorting = useTranslucencySorting;
        this.hasNoLightmap = hasNoLightmap;
        this.primitiveType = primitiveType;
        this.vertexType = vertexType;
        this.tessellationKey = new TessellationKey(vertexType.getVertexFormat(), primitiveType, useTranslucencySorting);
        this.extraDefines = Map.copyOf(extraDefines);
    }

    public boolean isReverseOrder() {
        return this.useReverseOrder;
    }

    public boolean isSorted() {
        return this.useTranslucencySorting;
    }

    public boolean hasNoLightmap() {
        return this.hasNoLightmap;
    }

    public void startDrawing() {
        this.pipelineState.setup();
    }

    public void endDrawing() {
        this.pipelineState.clear();
    }

    public boolean supportsFragmentDiscard() {
        return this.fragmentDiscard;
    }

    public ChunkPrimitiveType primitiveType() {
        return this.primitiveType;
    }

    public ChunkVertexType vertexType() {
        return this.vertexType;
    }

    public Map<String, String> extraDefines() {
        return this.extraDefines;
    }

    @Override
    public String toString() {
        return "TerrainRenderPass[name=" + this.name + "]";
    }


    public interface PipelineState {
        PipelineState DEFAULT = new PipelineState() {
            @Override
            public void setup() {
            }
            @Override
            public void clear() {
            }
        };

        void setup();

        void clear();
    }


    @java.lang.SuppressWarnings("all")
    public static class TerrainRenderPassBuilder {
        @java.lang.SuppressWarnings("all")
        private String name;
        @java.lang.SuppressWarnings("all")
        private PipelineState pipelineState;
        @java.lang.SuppressWarnings("all")
        private boolean useReverseOrder;
        @java.lang.SuppressWarnings("all")
        private boolean fragmentDiscard;
        @java.lang.SuppressWarnings("all")
        private boolean useTranslucencySorting;
        @java.lang.SuppressWarnings("all")
        private boolean hasNoLightmap;
        @java.lang.SuppressWarnings("all")
        private ChunkVertexType vertexType;
        @java.lang.SuppressWarnings("all")
        private ChunkPrimitiveType primitiveType;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<String> extraDefines$key;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<String> extraDefines$value;

        @java.lang.SuppressWarnings("all")
        TerrainRenderPassBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public TerrainRenderPass.TerrainRenderPassBuilder name(final String name) {
            this.name = name;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public TerrainRenderPass.TerrainRenderPassBuilder pipelineState(final PipelineState pipelineState) {
            this.pipelineState = pipelineState;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public TerrainRenderPass.TerrainRenderPassBuilder useReverseOrder(final boolean useReverseOrder) {
            this.useReverseOrder = useReverseOrder;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public TerrainRenderPass.TerrainRenderPassBuilder fragmentDiscard(final boolean fragmentDiscard) {
            this.fragmentDiscard = fragmentDiscard;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public TerrainRenderPass.TerrainRenderPassBuilder useTranslucencySorting(final boolean useTranslucencySorting) {
            this.useTranslucencySorting = useTranslucencySorting;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public TerrainRenderPass.TerrainRenderPassBuilder hasNoLightmap(final boolean hasNoLightmap) {
            this.hasNoLightmap = hasNoLightmap;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public TerrainRenderPass.TerrainRenderPassBuilder vertexType(final ChunkVertexType vertexType) {
            if (vertexType == null) {
                throw new java.lang.NullPointerException("vertexType is marked non-null but is null");
            }
            this.vertexType = vertexType;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public TerrainRenderPass.TerrainRenderPassBuilder primitiveType(final ChunkPrimitiveType primitiveType) {
            if (primitiveType == null) {
                throw new java.lang.NullPointerException("primitiveType is marked non-null but is null");
            }
            this.primitiveType = primitiveType;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public TerrainRenderPass.TerrainRenderPassBuilder extraDefine(final String extraDefineKey, final String extraDefineValue) {
            if (this.extraDefines$key == null) {
                this.extraDefines$key = new java.util.ArrayList<String>();
                this.extraDefines$value = new java.util.ArrayList<String>();
            }
            this.extraDefines$key.add(extraDefineKey);
            this.extraDefines$value.add(extraDefineValue);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public TerrainRenderPass.TerrainRenderPassBuilder extraDefines(final java.util.Map<? extends String, ? extends String> extraDefines) {
            if (extraDefines == null) {
                throw new java.lang.NullPointerException("extraDefines cannot be null");
            }
            if (this.extraDefines$key == null) {
                this.extraDefines$key = new java.util.ArrayList<String>();
                this.extraDefines$value = new java.util.ArrayList<String>();
            }
            for (final java.util.Map.Entry<? extends String, ? extends String> $lombokEntry : extraDefines.entrySet()) {
                this.extraDefines$key.add($lombokEntry.getKey());
                this.extraDefines$value.add($lombokEntry.getValue());
            }
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public TerrainRenderPass.TerrainRenderPassBuilder clearExtraDefines() {
            if (this.extraDefines$key != null) {
                this.extraDefines$key.clear();
                this.extraDefines$value.clear();
            }
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public TerrainRenderPass build() {
            java.util.Map<String, String> extraDefines;
            switch (this.extraDefines$key == null ? 0 : this.extraDefines$key.size()) {
            case 0: 
                extraDefines = java.util.Collections.emptyMap();
                break;
            case 1: 
                extraDefines = java.util.Collections.singletonMap(this.extraDefines$key.get(0), this.extraDefines$value.get(0));
                break;
            default: 
                extraDefines = new java.util.LinkedHashMap<String, String>(this.extraDefines$key.size() < 1073741824 ? 1 + this.extraDefines$key.size() + (this.extraDefines$key.size() - 3) / 3 : java.lang.Integer.MAX_VALUE);
                for (int $i = 0; $i < this.extraDefines$key.size(); $i++) extraDefines.put(this.extraDefines$key.get($i), (String) this.extraDefines$value.get($i));
                extraDefines = java.util.Collections.unmodifiableMap(extraDefines);
            }
            return new TerrainRenderPass(this.name, this.pipelineState, this.useReverseOrder, this.fragmentDiscard, this.useTranslucencySorting, this.hasNoLightmap, this.vertexType, this.primitiveType, extraDefines);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "TerrainRenderPass.TerrainRenderPassBuilder(name=" + this.name + ", pipelineState=" + this.pipelineState + ", useReverseOrder=" + this.useReverseOrder + ", fragmentDiscard=" + this.fragmentDiscard + ", useTranslucencySorting=" + this.useTranslucencySorting + ", hasNoLightmap=" + this.hasNoLightmap + ", vertexType=" + this.vertexType + ", primitiveType=" + this.primitiveType + ", extraDefines$key=" + this.extraDefines$key + ", extraDefines$value=" + this.extraDefines$value + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static TerrainRenderPass.TerrainRenderPassBuilder builder() {
        return new TerrainRenderPass.TerrainRenderPassBuilder();
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof TerrainRenderPass)) return false;
        final TerrainRenderPass other = (TerrainRenderPass) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        if (this.useReverseOrder != other.useReverseOrder) return false;
        if (this.fragmentDiscard != other.fragmentDiscard) return false;
        if (this.useTranslucencySorting != other.useTranslucencySorting) return false;
        if (this.hasNoLightmap() != other.hasNoLightmap()) return false;
        final java.lang.Object this$pipelineState = this.pipelineState;
        final java.lang.Object other$pipelineState = other.pipelineState;
        if (this$pipelineState == null ? other$pipelineState != null : !this$pipelineState.equals(other$pipelineState)) return false;
        final java.lang.Object this$primitiveType = this.primitiveType();
        final java.lang.Object other$primitiveType = other.primitiveType();
        if (this$primitiveType == null ? other$primitiveType != null : !this$primitiveType.equals(other$primitiveType)) return false;
        final java.lang.Object this$vertexType = this.vertexType();
        final java.lang.Object other$vertexType = other.vertexType();
        if (this$vertexType == null ? other$vertexType != null : !this$vertexType.equals(other$vertexType)) return false;
        final java.lang.Object this$extraDefines = this.extraDefines();
        final java.lang.Object other$extraDefines = other.extraDefines();
        if (this$extraDefines == null ? other$extraDefines != null : !this$extraDefines.equals(other$extraDefines)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof TerrainRenderPass;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + (this.useReverseOrder ? 79 : 97);
        result = result * PRIME + (this.fragmentDiscard ? 79 : 97);
        result = result * PRIME + (this.useTranslucencySorting ? 79 : 97);
        result = result * PRIME + (this.hasNoLightmap() ? 79 : 97);
        final java.lang.Object $pipelineState = this.pipelineState;
        result = result * PRIME + ($pipelineState == null ? 43 : $pipelineState.hashCode());
        final java.lang.Object $primitiveType = this.primitiveType();
        result = result * PRIME + ($primitiveType == null ? 43 : $primitiveType.hashCode());
        final java.lang.Object $vertexType = this.vertexType();
        result = result * PRIME + ($vertexType == null ? 43 : $vertexType.hashCode());
        final java.lang.Object $extraDefines = this.extraDefines();
        result = result * PRIME + ($extraDefines == null ? 43 : $extraDefines.hashCode());
        return result;
    }

    /**
     * The friendly name of this render pass.
     */
    @java.lang.SuppressWarnings("all")
    public String name() {
        return this.name;
    }

    @java.lang.SuppressWarnings("all")
    public TessellationKey tessellationKey() {
        return this.tessellationKey;
    }
}
