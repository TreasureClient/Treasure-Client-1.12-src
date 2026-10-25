package org.embeddedt.embeddium.impl.render.shader;

import net.lax1dude.eaglercraft.EagRuntime;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderParser;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;

public class ShaderLoader {
    /**
     * Creates a shader from GLSL sources stored in the game assets at
     * {@code /assets/{namespace}/shaders/{path}}.
     */
    public static GlShader loadShader(ShaderType type, String name, ShaderConstants constants) {
        return new GlShader(type, name, toWebGL(ShaderParser.parseShader(getShaderSource(name), ShaderLoader::getShaderSource, constants)));
    }

    private static final String DESKTOP_VERSION = "#version 330 core";

    /** Treasure Client: WebGL 2 wants GLSL ES 3.00 with explicit precision. */
    private static final String WEBGL_HEADER = "#version 300 es\n"
            + "precision highp float;\n"
            + "precision highp int;\n"
            + "precision highp sampler2D;";

    public static boolean useLegacyGlsl() {
        return false;
    }

    public static String toWebGL(String shaderSource) {
        int i = shaderSource.indexOf(DESKTOP_VERSION);
        if (i >= 0) {
            shaderSource = shaderSource.substring(0, i) + WEBGL_HEADER + shaderSource.substring(i + DESKTOP_VERSION.length());
        }
        return shaderSource;
    }

    public static String getShaderSource(String name) {
        String namespace = "minecraft";
        String path = name;
        int colon = name.indexOf(':');
        if (colon >= 0) {
            namespace = name.substring(0, colon);
            path = name.substring(colon + 1);
        }
        return EagRuntime.getRequiredResourceString("/assets/" + namespace + "/shaders/" + path);
    }
}
