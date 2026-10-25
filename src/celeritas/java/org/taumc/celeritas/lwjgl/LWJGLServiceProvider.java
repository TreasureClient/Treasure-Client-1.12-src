package org.taumc.celeritas.lwjgl;

/**
 * Loads LWJGLService via ServiceLoader, picking highest priority.
 */
public final class LWJGLServiceProvider {
    public static final LWJGLService LWJGL = createInstance();
    public static final int POINTER_SIZE = LWJGL.getPointerSize();
    public static final long NULL = 0L;

    private LWJGLServiceProvider() {}

    static LWJGLService createInstance() {
        // Treasure Client: always the WebGL 2 backend
        return org.taumc.celeritas.lwjgl.webgl.WebGLService.create();
    }
}
