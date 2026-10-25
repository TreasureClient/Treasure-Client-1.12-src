package org.embeddedt.embeddium.impl.gui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.taumc.celeritas.api.options.structure.OptionStorage;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.embeddedt.embeddium.impl.gui.options.TextProvider;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode;

import java.io.IOException;
import java.util.List;

public class SodiumGameOptions implements OptionStorage<SodiumGameOptions> {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String DEFAULT_FILE_NAME = "embeddium-options.json";

    public final QualitySettings quality = new QualitySettings();
    public final AdvancedSettings advanced = new AdvancedSettings();
    public final PerformanceSettings performance = new PerformanceSettings();
    public final NotificationSettings notifications = new NotificationSettings();

    private boolean readOnly;

    public static SodiumGameOptions defaults() {
        var options = new SodiumGameOptions();

        return options;
    }

    @Override
    public SodiumGameOptions getData() {
        return this;
    }

    @Override
    public void save() {
        try {
            this.writeChanges();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't save configuration changes", e);
        }

        LOGGER.info("Flushed changes to Embeddium configuration");
    }

    public static class PerformanceSettings {
        public int chunkBuilderThreads = 0;
        public boolean alwaysDeferChunkUpdates = true;

        public boolean animateOnlyVisibleTextures = true;
        public boolean useEntityCulling = true;
        public boolean useFogOcclusion = true;
        public boolean useBlockFaceCulling = true;
        public boolean useCompactVertexFormat = false;
        public boolean useTranslucentFaceSorting = true;
        public boolean useRenderPassOptimization = true;
        public boolean useRenderPassConsolidation = true;
        public boolean useFasterClouds = true;
        public boolean useNoErrorGLContext = true;

        public AsyncOcclusionMode asyncOcclusionMode = AsyncOcclusionMode.ONLY_SHADOW;

        public boolean useRasterOcclusionCulling = false;
    }

    public static class AdvancedSettings {
        public boolean enableMemoryTracing = false;
        public boolean useAdvancedStagingBuffers = true;
        public boolean disableIncompatibleModWarnings = false;

        public int cpuRenderAheadLimit = 3;
    }

    public static class QualitySettings {
        public GraphicsQuality weatherQuality = GraphicsQuality.DEFAULT;
        public GraphicsQuality leavesQuality = GraphicsQuality.DEFAULT;

        public boolean enableVignette = true;
        public boolean useQuadNormalsForShading = true;

        public int chunkFadeInDuration = 0;

        public int legacyBiomeBlendRadius = 0;
    }

    public static class NotificationSettings {
        public boolean forceDisableDonationPrompts = false;

        public boolean hasClearedDonationButton = false;
        public boolean hasSeenDonationPrompt = false;
    }

    public enum GraphicsQuality implements TextProvider {
        DEFAULT(List.of("options.gamma.default", "generator.default")),
        FANCY("options.clouds.fancy"),
        FAST("options.clouds.fast");

        private final TextComponent name;

        GraphicsQuality(String name) {
            this.name = TextComponent.translatable(name);
        }

        GraphicsQuality(List<String> names) {
            this.name = TextComponent.translatable(names);
        }

        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }

        public boolean isFancy(boolean fancyGraphics) {
            return (this == FANCY) || (this == DEFAULT && fancyGraphics);
        }
    }

    // Treasure Client: settings live in memory; no config file.
    public static SodiumGameOptions load() {
        return new SodiumGameOptions();
    }

    public static SodiumGameOptions load(String name) {
        return new SodiumGameOptions();
    }

    @Deprecated
    public void writeChanges() throws IOException {
    }

    public static void writeToDisk(SodiumGameOptions config) throws IOException {
    }

    public boolean isReadOnly() {
        return this.readOnly;
    }

    public void setReadOnly() {
        this.readOnly = true;
    }

    public String getFileName() {
        return DEFAULT_FILE_NAME;
    }
}
