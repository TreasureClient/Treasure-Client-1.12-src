package org.taumc.celeritas;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gui.SodiumGameOptions;

/**
 * Treasure Client: Celeritas entry point. Originally a Forge mod class; here it
 * just holds the logger and renderer settings.
 */
public class CeleritasVintage {
    public static final String MODID = "celeritas";
    public static final String VERSION = "treasure-port";
    private static final Logger LOGGER = LogManager.getLogger("Celeritas");
    private static final SodiumGameOptions CONFIG = createConfig();

    private static SodiumGameOptions createConfig() {
        SodiumGameOptions options = SodiumGameOptions.defaults();
        // Browsers can't build chunks on worker threads, so build on the main thread.
        options.performance.chunkBuilderThreads = -1;
        return options;
    }

    public static Logger logger() {
        return LOGGER;
    }

    public static SodiumGameOptions options() {
        return CONFIG;
    }
}
