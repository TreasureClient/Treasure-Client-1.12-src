package org.taumc.celeritas.lwjgl.webgl;

import static net.lax1dude.eaglercraft.internal.PlatformOpenGL.*;

import java.util.ArrayList;

import net.lax1dude.eaglercraft.internal.IBufferGL;
import net.lax1dude.eaglercraft.internal.IProgramGL;
import net.lax1dude.eaglercraft.internal.IShaderGL;
import net.lax1dude.eaglercraft.internal.IUniformGL;
import net.lax1dude.eaglercraft.internal.IVertexArrayGL;
import net.lax1dude.eaglercraft.internal.buffer.Buffer;
import net.lax1dude.eaglercraft.internal.buffer.ByteBuffer;
import net.lax1dude.eaglercraft.internal.buffer.FloatBuffer;
import net.lax1dude.eaglercraft.internal.buffer.IntBuffer;
import net.lax1dude.eaglercraft.internal.buffer.TCNativeMemory;
import net.lax1dude.eaglercraft.opengl.EaglercraftGPU;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import org.taumc.celeritas.lwjgl.DebugMessageHandler;
import org.taumc.celeritas.lwjgl.GLExtension;
import org.taumc.celeritas.lwjgl.LWJGLService;
import org.taumc.celeritas.lwjgl.MemoryStack;

/**
 * Treasure Client: runs Celeritas on WebGL 2 through Eaglercraft's GL layer.
 *
 * <p>Celeritas talks to OpenGL with integer object names and raw memory
 * addresses. This class keeps tables that map those names to Eaglercraft's GL
 * objects, routes bindings through EaglercraftGPU/GlStateManager so their
 * caches stay correct, and emulates the desktop-only calls Celeritas needs:
 * draws with a base vertex (WebGL 2 has none) are done by re-pointing the
 * vertex attributes before each draw.
 */
public final class WebGLService implements LWJGLService {

    private static final int GL_ARRAY_BUFFER = 0x8892;
    private static final int GL_ELEMENT_ARRAY_BUFFER = 0x8893;
    private static final int GL_COPY_READ_BUFFER = 0x8F36;
    private static final int GL_COPY_WRITE_BUFFER = 0x8F37;
    private static final int GL_COMPILE_STATUS = 0x8B81;
    private static final int GL_LINK_STATUS = 0x8B82;
    private static final int GL_INFO_LOG_LENGTH = 0x8B84;
    private static final int GL_ALREADY_SIGNALED = 0x911A;
    private static final int GL_SIGNALED = 0x9119;
    private static final int GL_BLEND = 0x0BE2;
    private static final int GL_DEPTH_TEST = 0x0B71;
    private static final int GL_CULL_FACE = 0x0B44;
    private static final int GL_TEXTURE0 = 0x84C0;

    /** One recorded vertex attribute of a vertex array. */
    private static final class Attrib {
        int index;
        int size;
        int type;
        boolean normalized;
        boolean integer;
        int stride;
        int offset;
        IBufferGL buffer;
    }

    /** A vertex array plus the attribute layout we need to re-point it. */
    private static final class VertexArray {
        final IVertexArrayGL handle;
        final ArrayList<Attrib> attribs = new ArrayList<>(6);
        int baseVertex;

        VertexArray(IVertexArrayGL handle) {
            this.handle = handle;
        }
    }

    private final ArrayList<IBufferGL> buffers = new ArrayList<>();
    private final ArrayList<VertexArray> vertexArrays = new ArrayList<>();
    private final ArrayList<IShaderGL> shaders = new ArrayList<>();
    private final ArrayList<IProgramGL> programs = new ArrayList<>();
    private final ArrayList<IUniformGL> uniforms = new ArrayList<>();

    private final WebGLMemoryStack stack = new WebGLMemoryStack();

    private IBufferGL boundArrayBuffer;

    // Index buffers, by name. WebGL never lets these be bound as vertex data.
    private boolean[] elementBuffers = new boolean[256];

    // What Celeritas thinks is bound to each target. Uploads and copies are done
    // through the COPY_READ/COPY_WRITE slots, which accept both kinds of buffer.
    private int logicalArray, logicalElement, logicalCopyRead, logicalCopyWrite;
    private VertexArray boundVertexArray;

    public static LWJGLService create() {
        return new WebGLService();
    }

    private WebGLService() {
        // Slot 0 means "no object" everywhere
        this.buffers.add(null);
        this.vertexArrays.add(null);
        this.shaders.add(null);
        this.programs.add(null);
    }

    private static <T> int store(ArrayList<T> table, T obj) {
        for (int i = 1, n = table.size(); i < n; ++i) {
            if (table.get(i) == null) {
                table.set(i, obj);
                return i;
            }
        }
        table.add(obj);
        return table.size() - 1;
    }

    private static <T> T get(ArrayList<T> table, int id) {
        return id > 0 && id < table.size() ? table.get(id) : null;
    }

    /** True when this browser can run Celeritas (needs WebGL 2). */
    public static boolean isSupported() {
        return TCNativeMemory.SUPPORTED && EaglercraftGPU.checkOpenGLESVersion() >= 300;
    }

    // ===================== CAPABILITIES =====================

    @Override
    public boolean isOpenGLVersionSupported(int major, int minor) {
        // WebGL 2 gives us roughly OpenGL 3.1 (copy buffers, VAOs, integer attribs).
        // Buffer mapping (3.0) is missing, but Celeritas only maps when buffer storage exists.
        return major < 3 || (major == 3 && minor <= 1);
    }

    @Override
    public boolean isExtensionSupported(GLExtension extension) {
        switch (extension) {
            case ARB_vertex_array_object:
            case ARB_copy_buffer:
            case ARB_uniform_buffer_object:
                return true;
            default:
                return false;
        }
    }

    @Override
    public int getPointerSize() {
        return TCNativeMemory.pointerSize();
    }

    // ===================== BUFFER OPERATIONS =====================

    @Override
    public int glGenBuffers() {
        int name = store(this.buffers, _wglGenBuffers());
        setElement(name, false);
        return name;
    }

    private boolean isElement(int name) {
        return name > 0 && name < this.elementBuffers.length && this.elementBuffers[name];
    }

    private void setElement(int name, boolean value) {
        if (name >= this.elementBuffers.length) {
            if (!value) {
                return;
            }
            boolean[] grown = new boolean[Math.max(name + 1, this.elementBuffers.length * 2)];
            System.arraycopy(this.elementBuffers, 0, grown, 0, this.elementBuffers.length);
            this.elementBuffers = grown;
        }
        this.elementBuffers[name] = value;
    }

    @Override
    public void hintElementArrayBuffer(int buffer) {
        IBufferGL buf = get(this.buffers, buffer);
        if (buf != null && !isElement(buffer)) {
            setElement(buffer, true);
            net.lax1dude.eaglercraft.opengl.TCRenderState.typeAsElementBuffer(buf);
        }
    }

    /** The buffer name Celeritas has bound to {@code target}, or -1 for targets we don't track. */
    private int logical(int target) {
        switch (target) {
            case GL_ARRAY_BUFFER:
                return this.logicalArray;
            case GL_ELEMENT_ARRAY_BUFFER:
                return this.logicalElement;
            case GL_COPY_READ_BUFFER:
                return this.logicalCopyRead;
            case GL_COPY_WRITE_BUFFER:
                return this.logicalCopyWrite;
            default:
                return -1;
        }
    }

    /** Binds the buffer behind {@code target} to COPY_WRITE and returns the target to use for a data call. */
    private int dataTarget(int target) {
        int name = logical(target);
        if (name < 0) {
            return target;
        }
        _wglBindBuffer(GL_COPY_WRITE_BUFFER, get(this.buffers, name));
        return GL_COPY_WRITE_BUFFER;
    }

    @Override
    public void glDeleteBuffers(int buffer) {
        IBufferGL buf = get(this.buffers, buffer);
        if (buf != null) {
            if (buf == this.boundArrayBuffer) {
                glBindBuffer(GL_ARRAY_BUFFER, 0);
            }
            EaglercraftGPU.destroyGLArrayBuffer(buf);
            this.buffers.set(buffer, null);
            setElement(buffer, false);
            if (this.logicalArray == buffer) this.logicalArray = 0;
            if (this.logicalElement == buffer) this.logicalElement = 0;
            if (this.logicalCopyRead == buffer) this.logicalCopyRead = 0;
            if (this.logicalCopyWrite == buffer) this.logicalCopyWrite = 0;
        }
    }

    @Override
    public void glBindBuffer(int target, int buffer) {
        IBufferGL buf = get(this.buffers, buffer);
        switch (target) {
            case GL_ARRAY_BUFFER:
                this.logicalArray = buffer;
                // Index buffers are only bound here to upload data, which goes through COPY_WRITE
                if (!isElement(buffer)) {
                    EaglercraftGPU.bindGLArrayBuffer(buf);
                    this.boundArrayBuffer = buf;
                }
                break;
            case GL_ELEMENT_ARRAY_BUFFER:
                this.logicalElement = buffer;
                EaglercraftGPU.bindVAOGLElementArrayBuffer(buf);
                break;
            case GL_COPY_READ_BUFFER:
                this.logicalCopyRead = buffer;
                break;
            case GL_COPY_WRITE_BUFFER:
                this.logicalCopyWrite = buffer;
                break;
            default:
                _wglBindBuffer(target, buf);
                break;
        }
    }

    @Override
    public void glBufferData(int target, long size, int usage) {
        _wglBufferData(dataTarget(target), (int) size, usage);
    }

    @Override
    public void glBufferData(int target, ByteBuffer data, int usage) {
        _wglBufferData(dataTarget(target), data, usage);
    }

    @Override
    public void glBufferSubData(int target, long offset, ByteBuffer data) {
        _wglBufferSubData(dataTarget(target), (int) offset, data);
    }

    @Override
    public void glBufferData(int target, long size, long data, int usage) {
        int t = dataTarget(target);
        if (data == 0L) {
            _wglBufferData(t, (int) size, usage);
        } else {
            _wglBufferData(t, TCNativeMemory.wrap(data, (int) size), usage);
        }
    }

    @Override
    public void glBufferStorage(int target, long size, int flags) {
        throw new UnsupportedOperationException("glBufferStorage");
    }

    @Override
    public ByteBuffer glMapBufferRange(int target, long offset, long length, int flags) {
        throw new UnsupportedOperationException("glMapBufferRange");
    }

    @Override
    public long nglMapBuffer(int target, int access) {
        throw new UnsupportedOperationException("glMapBuffer");
    }

    @Override
    public ByteBuffer glMapBuffer(int target, int access) {
        throw new UnsupportedOperationException("glMapBuffer");
    }

    @Override
    public void glUnmapBuffer(int target) {
        throw new UnsupportedOperationException("glUnmapBuffer");
    }

    @Override
    public void glFlushMappedBufferRange(int target, long offset, long length) {
        throw new UnsupportedOperationException("glFlushMappedBufferRange");
    }

    @Override
    public void glCopyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        int src = logical(readTarget);
        int dst = logical(writeTarget);
        if (src >= 0 && dst >= 0) {
            _wglBindBuffer(GL_COPY_READ_BUFFER, get(this.buffers, src));
            _wglBindBuffer(GL_COPY_WRITE_BUFFER, get(this.buffers, dst));
            readTarget = GL_COPY_READ_BUFFER;
            writeTarget = GL_COPY_WRITE_BUFFER;
        }
        _wglCopyBufferSubData(readTarget, writeTarget, (int) readOffset, (int) writeOffset, (int) size);
    }

    @Override
    public void glBindBufferBase(int target, int index, int buffer) {
        throw new UnsupportedOperationException("glBindBufferBase");
    }

    // ===================== VAO OPERATIONS =====================

    @Override
    public int glGenVertexArrays() {
        return store(this.vertexArrays, new VertexArray(EaglercraftGPU.createGLVertexArray()));
    }

    @Override
    public void glDeleteVertexArrays(int array) {
        VertexArray vao = get(this.vertexArrays, array);
        if (vao != null) {
            if (vao == this.boundVertexArray) {
                glBindVertexArray(0);
            }
            EaglercraftGPU.destroyGLVertexArray(vao.handle);
            this.vertexArrays.set(array, null);
        }
    }

    @Override
    public void glBindVertexArray(int array) {
        VertexArray vao = get(this.vertexArrays, array);
        EaglercraftGPU.bindGLVertexArray(vao != null ? vao.handle : null);
        this.boundVertexArray = vao;
    }

    private void recordAttrib(int index, int size, int type, boolean normalized, boolean integer, int stride, long pointer) {
        VertexArray vao = this.boundVertexArray;
        if (vao == null) {
            return;
        }
        Attrib a = null;
        for (int i = 0, n = vao.attribs.size(); i < n; ++i) {
            if (vao.attribs.get(i).index == index) {
                a = vao.attribs.get(i);
                break;
            }
        }
        if (a == null) {
            a = new Attrib();
            vao.attribs.add(a);
        }
        a.index = index;
        a.size = size;
        a.type = type;
        a.normalized = normalized;
        a.integer = integer;
        a.stride = stride;
        a.offset = (int) pointer;
        a.buffer = this.boundArrayBuffer;
        vao.baseVertex = 0;
    }

    @Override
    public void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        recordAttrib(index, size, type, normalized, false, stride, pointer);
        _wglVertexAttribPointer(index, size, type, normalized, stride, (int) pointer);
    }

    @Override
    public void glVertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        recordAttrib(index, size, type, false, true, stride, pointer);
        _wglVertexAttribIPointer(index, size, type, stride, (int) pointer);
    }

    @Override
    public void glEnableVertexAttribArray(int index) {
        _wglEnableVertexAttribArray(index);
    }

    @Override
    public void glVertexAttribDivisor(int index, int divisor) {
        _wglVertexAttribDivisor(index, divisor);
    }

    /** Moves the bound vertex array's attributes so vertex 0 is {@code baseVertex}. */
    private void applyBaseVertex(int baseVertex) {
        VertexArray vao = this.boundVertexArray;
        if (vao == null || vao.baseVertex == baseVertex) {
            return;
        }
        vao.baseVertex = baseVertex;
        ArrayList<Attrib> attribs = vao.attribs;
        for (int i = 0, n = attribs.size(); i < n; ++i) {
            Attrib a = attribs.get(i);
            EaglercraftGPU.bindGLArrayBuffer(a.buffer);
            int offset = a.offset + baseVertex * a.stride;
            if (a.integer) {
                _wglVertexAttribIPointer(a.index, a.size, a.type, a.stride, offset);
            } else {
                _wglVertexAttribPointer(a.index, a.size, a.type, a.normalized, a.stride, offset);
            }
        }
        this.boundArrayBuffer = attribs.isEmpty() ? this.boundArrayBuffer : attribs.get(attribs.size() - 1).buffer;
    }

    // ===================== SHADER OPERATIONS =====================

    @Override
    public int glCreateShader(int type) {
        return store(this.shaders, _wglCreateShader(type));
    }

    @Override
    public void glShaderSource(int shader, CharSequence source) {
        _wglShaderSource(get(this.shaders, shader), source.toString());
    }

    @Override
    public void glShaderSourceSafe(int shader, CharSequence source) {
        glShaderSource(shader, source);
    }

    @Override
    public void glCompileShader(int shader) {
        _wglCompileShader(get(this.shaders, shader));
    }

    @Override
    public String glGetShaderInfoLog(int shader, int maxLength) {
        String log = _wglGetShaderInfoLog(get(this.shaders, shader));
        return log != null ? log : "";
    }

    @Override
    public int glGetShaderi(int shader, int pname) {
        if (pname == GL_INFO_LOG_LENGTH) {
            return glGetShaderInfoLog(shader, 0).length() + 1;
        }
        return _wglGetShaderi(get(this.shaders, shader), pname);
    }

    @Override
    public void glDeleteShader(int shader) {
        IShaderGL obj = get(this.shaders, shader);
        if (obj != null) {
            _wglDeleteShader(obj);
            this.shaders.set(shader, null);
        }
    }

    @Override
    public int glCreateProgram() {
        return store(this.programs, _wglCreateProgram());
    }

    @Override
    public void glAttachShader(int program, int shader) {
        _wglAttachShader(get(this.programs, program), get(this.shaders, shader));
    }

    @Override
    public void glLinkProgram(int program) {
        _wglLinkProgram(get(this.programs, program));
    }

    @Override
    public String glGetProgramInfoLog(int program, int maxLength) {
        String log = _wglGetProgramInfoLog(get(this.programs, program));
        return log != null ? log : "";
    }

    @Override
    public int glGetProgrami(int program, int pname) {
        if (pname == GL_INFO_LOG_LENGTH) {
            return glGetProgramInfoLog(program, 0).length() + 1;
        }
        return _wglGetProgrami(get(this.programs, program), pname);
    }

    @Override
    public void glUseProgram(int program) {
        EaglercraftGPU.bindGLShaderProgram(get(this.programs, program));
    }

    @Override
    public void glDeleteProgram(int program) {
        IProgramGL obj = get(this.programs, program);
        if (obj != null) {
            EaglercraftGPU.clearCurrentBinding(EaglercraftGPU.CLEAR_BINDING_SHADER_PROGRAM);
            _wglDeleteProgram(obj);
            this.programs.set(program, null);
        }
    }

    @Override
    public void glBindAttribLocation(int program, int index, CharSequence name) {
        _wglBindAttribLocation(get(this.programs, program), index, name.toString());
    }

    @Override
    public void glBindFragDataLocation(int program, int colorNumber, CharSequence name) {
        // GLSL ES 3.00 assigns the only fragment output to location 0 already
    }

    // ===================== UNIFORM OPERATIONS =====================

    private IUniformGL uniform(int location) {
        return location >= 0 && location < this.uniforms.size() ? this.uniforms.get(location) : null;
    }

    @Override
    public int glGetUniformLocation(int program, CharSequence name) {
        IUniformGL loc = _wglGetUniformLocation(get(this.programs, program), name.toString());
        if (loc == null) {
            return -1;
        }
        this.uniforms.add(loc);
        return this.uniforms.size() - 1;
    }

    @Override
    public int glGetUniformBlockIndex(int program, CharSequence name) {
        return _wglGetUniformBlockIndex(get(this.programs, program), name.toString());
    }

    @Override
    public void glUniformBlockBinding(int program, int blockIndex, int blockBinding) {
        _wglUniformBlockBinding(get(this.programs, program), blockIndex, blockBinding);
    }

    @Override
    public void glUniform1f(int location, float v0) {
        _wglUniform1f(uniform(location), v0);
    }

    @Override
    public void glUniform1i(int location, int v0) {
        _wglUniform1i(uniform(location), v0);
    }

    @Override
    public void glUniform1fv(int location, FloatBuffer value) {
        // Only single values are supported (uniform arrays aren't used by our passes)
        if (value.remaining() > 0) {
            _wglUniform1f(uniform(location), value.get(value.position()));
        }
    }

    @Override
    public void glUniform2i(int location, int v0, int v1) {
        _wglUniform2i(uniform(location), v0, v1);
    }

    @Override
    public void glUniform3f(int location, float v0, float v1, float v2) {
        _wglUniform3f(uniform(location), v0, v1, v2);
    }

    @Override
    public void glUniform3fv(int location, FloatBuffer value) {
        int p = value.position();
        _wglUniform3f(uniform(location), value.get(p), value.get(p + 1), value.get(p + 2));
    }

    @Override
    public void glUniform3fv(int location, float[] value) {
        _wglUniform3f(uniform(location), value[0], value[1], value[2]);
    }

    @Override
    public void glUniform4fv(int location, FloatBuffer value) {
        int p = value.position();
        _wglUniform4f(uniform(location), value.get(p), value.get(p + 1), value.get(p + 2), value.get(p + 3));
    }

    @Override
    public void glUniform4fv(int location, float[] value) {
        _wglUniform4f(uniform(location), value[0], value[1], value[2], value[3]);
    }

    @Override
    public void glUniformMatrix3fv(int location, boolean transpose, FloatBuffer value) {
        _wglUniformMatrix3fv(uniform(location), transpose, value);
    }

    @Override
    public void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        _wglUniformMatrix4fv(uniform(location), transpose, value);
    }

    // ===================== DRAW OPERATIONS =====================

    @Override
    public void glDrawArrays(int mode, int first, int count) {
        _wglDrawArrays(mode, first, count);
    }

    @Override
    public void glDrawElementsBaseVertex(int mode, int count, int type, long indices, int basevertex) {
        applyBaseVertex(basevertex);
        _wglDrawElements(mode, count, type, (int) indices);
    }

    @Override
    public void glMultiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount, long pBaseVertex) {
        int ptrSize = TCNativeMemory.pointerSize();
        for (int i = 0; i < drawcount; ++i) {
            int count = TCNativeMemory.getInt(pCount + ((long) i << 2));
            if (count == 0) {
                continue;
            }
            long indices = ptrSize == 8 ? TCNativeMemory.getLong(pIndices + ((long) i << 3))
                    : (TCNativeMemory.getInt(pIndices + ((long) i << 2)) & 0xFFFFFFFFL);
            applyBaseVertex(TCNativeMemory.getInt(pBaseVertex + ((long) i << 2)));
            _wglDrawElements(mode, count, type, (int) indices);
        }
    }

    @Override
    public void glMultiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        throw new UnsupportedOperationException("glMultiDrawElementsIndirect");
    }

    // ===================== SYNC OPERATIONS =====================
    // WebGL calls finish in order, so fences are always signaled.

    @Override
    public long glFenceSync(int condition, int flags) {
        return 1L;
    }

    @Override
    public int glClientWaitSync(long sync, int flags, long timeout) {
        return GL_ALREADY_SIGNALED;
    }

    @Override
    public int glGetSynci(long sync, int pname, IntBuffer length) {
        return GL_SIGNALED;
    }

    @Override
    public void glWaitSync(long sync, int flags, long timeout) {
    }

    @Override
    public void glDeleteSync(long sync) {
    }

    // ===================== QUERY OPERATIONS =====================

    @Override
    public int glGenQueries() {
        return 0;
    }

    @Override
    public void glDeleteQueries(int query) {
    }

    @Override
    public void glBeginQuery(int target, int id) {
    }

    @Override
    public void glEndQuery(int target) {
    }

    @Override
    public long glGetQueryObjectui64(int id, int pname) {
        return 0L;
    }

    // ===================== DEBUG OPERATIONS =====================

    @Override
    public int setupDebugCallback(DebugMessageHandler handler) {
        return 0;
    }

    @Override
    public void disableDebugCallback() {
    }

    @Override
    public void glObjectLabel(int identifier, int name, CharSequence label) {
    }

    @Override
    public void glPushDebugGroup(int source, int id, CharSequence message) {
    }

    @Override
    public void glPopDebugGroup() {
    }

    // ===================== TEXTURE OPERATIONS =====================

    @Override
    public int glGenTextures() {
        return GlStateManager.generateTexture();
    }

    @Override
    public void glGenTextures(int[] textures) {
        for (int i = 0; i < textures.length; ++i) {
            textures[i] = GlStateManager.generateTexture();
        }
    }

    @Override
    public void glDeleteTextures(int texture) {
        GlStateManager.deleteTexture(texture);
    }

    @Override
    public void glDeleteTextures(int[] textures) {
        for (int i = 0; i < textures.length; ++i) {
            GlStateManager.deleteTexture(textures[i]);
        }
    }

    @Override
    public void glBindTexture(int target, int texture) {
        GlStateManager.bindTexture(texture);
    }

    @Override
    public void glActiveTexture(int texture) {
        GlStateManager.setActiveTexture(texture);
    }

    @Override
    public int glGetTexLevelParameteri(int target, int level, int pname) {
        throw new UnsupportedOperationException("glGetTexLevelParameteri");
    }

    @Override
    public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        EaglercraftGPU.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }

    @Override
    public void glPixelStorei(int pname, int param) {
        _wglPixelStorei(pname, param);
    }

    @Override
    public void glTexImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        EaglercraftGPU.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    @Override
    public void glTexParameteri(int target, int pname, int param) {
        EaglercraftGPU.glTexParameteri(target, pname, param);
    }

    // ===================== FRAMEBUFFER OPERATIONS =====================
    // Celeritas' terrain renderer doesn't use framebuffers.

    @Override
    public int glGenFramebuffers() {
        throw new UnsupportedOperationException("glGenFramebuffers");
    }

    @Override
    public void glDeleteFramebuffers(int framebuffer) {
        throw new UnsupportedOperationException("glDeleteFramebuffers");
    }

    @Override
    public void glBindFramebuffer(int target, int framebuffer) {
        throw new UnsupportedOperationException("glBindFramebuffer");
    }

    @Override
    public int glCheckFramebufferStatus(int target) {
        throw new UnsupportedOperationException("glCheckFramebufferStatus");
    }

    @Override
    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        throw new UnsupportedOperationException("glFramebufferTexture2D");
    }

    @Override
    public void glDrawBuffers(int[] buffers) {
        _wglDrawBuffers(buffers);
    }

    @Override
    public void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        _wglBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    // ===================== STATE OPERATIONS =====================

    @Override
    public void glEnable(int cap) {
        switch (cap) {
            case GL_BLEND: GlStateManager.enableBlend(); break;
            case GL_DEPTH_TEST: GlStateManager.enableDepth(); break;
            case GL_CULL_FACE: GlStateManager.enableCull(); break;
            default: _wglEnable(cap); break;
        }
    }

    @Override
    public void glDisable(int cap) {
        switch (cap) {
            case GL_BLEND: GlStateManager.disableBlend(); break;
            case GL_DEPTH_TEST: GlStateManager.disableDepth(); break;
            case GL_CULL_FACE: GlStateManager.disableCull(); break;
            default: _wglDisable(cap); break;
        }
    }

    @Override
    public void glBlendFunc(int sfactor, int dfactor) {
        GlStateManager.blendFunc(sfactor, dfactor);
    }

    @Override
    public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        GlStateManager.tryBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @Override
    public void glDepthFunc(int func) {
        GlStateManager.depthFunc(func);
    }

    @Override
    public void glDepthMask(boolean flag) {
        GlStateManager.depthMask(flag);
    }

    @Override
    public void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GlStateManager.colorMask(red, green, blue, alpha);
    }

    @Override
    public void glViewport(int x, int y, int width, int height) {
        GlStateManager.viewport(x, y, width, height);
    }

    @Override
    public void glClear(int mask) {
        GlStateManager.clear(mask);
    }

    @Override
    public void glClearColor(float red, float green, float blue, float alpha) {
        GlStateManager.clearColor(red, green, blue, alpha);
    }

    @Override
    public int glGetError() {
        return _wglGetError();
    }

    // ===================== COMPATIBILITY PROFILE =====================

    @Override
    public void glMatrixMode(int mode) {
        GlStateManager.matrixMode(mode);
    }

    @Override
    public void glLoadMatrixf(FloatBuffer m) {
        throw new UnsupportedOperationException("glLoadMatrixf");
    }

    // ===================== MISC GL =====================

    @Override
    public int glGetInteger(int pname) {
        return _wglGetInteger(pname);
    }

    @Override
    public void glGetIntegerv(int pname, int[] params) {
        params[0] = _wglGetInteger(pname);
    }

    @Override
    public boolean glGetBoolean(int pname) {
        return _wglGetInteger(pname) != 0;
    }

    @Override
    public String glGetString(int pname) {
        return _wglGetString(pname);
    }

    @Override
    public int glGetAttribLocation(int program, CharSequence name) {
        return _wglGetAttribLocation(get(this.programs, program), name.toString());
    }

    // ===================== MEMORY STACK =====================

    @Override
    public MemoryStack stackPush() {
        return this.stack.push();
    }

    // ===================== NATIVE MEMORY =====================

    @Override
    public long nmemAlloc(long size) {
        return TCNativeMemory.malloc(size);
    }

    @Override
    public long nmemCalloc(long count, long size) {
        return TCNativeMemory.calloc(count * size);
    }

    @Override
    public long nmemAlignedAlloc(long alignment, long size) {
        return TCNativeMemory.alignedAlloc(alignment, size);
    }

    @Override
    public long nmemRealloc(long ptr, long size) {
        throw new UnsupportedOperationException("nmemRealloc without a known size");
    }

    @Override
    public void nmemFree(long ptr) {
        TCNativeMemory.free(ptr);
    }

    @Override
    public void nmemAlignedFree(long ptr) {
        TCNativeMemory.alignedFree(ptr);
    }

    @Override
    public ByteBuffer memAlloc(int size) {
        return TCNativeMemory.allocByteBuffer(size);
    }

    @Override
    public ByteBuffer memCalloc(int size) {
        ByteBuffer buf = TCNativeMemory.allocByteBuffer(size);
        TCNativeMemory.set(TCNativeMemory.address(buf), 0, size);
        return buf;
    }

    @Override
    public ByteBuffer memRealloc(ByteBuffer buffer, int size) {
        ByteBuffer ret = TCNativeMemory.allocByteBuffer(size);
        if (buffer != null) {
            TCNativeMemory.copy(TCNativeMemory.address(buffer) - buffer.position(), TCNativeMemory.address(ret), Math.min(buffer.capacity(), size));
            ret.position(Math.min(buffer.position(), size));
            TCNativeMemory.freeBuffer(buffer);
        }
        return ret;
    }

    @Override
    public void memFree(Buffer buffer) {
        if (buffer != null) {
            TCNativeMemory.freeBuffer(buffer);
        }
    }

    @Override
    public ByteBuffer memByteBuffer(long address, int capacity) {
        return TCNativeMemory.wrap(address, capacity);
    }

    @Override
    public long memAddress(Buffer buffer) {
        return TCNativeMemory.address(buffer);
    }

    @Override
    public long memAddress(Buffer buffer, int position) {
        return TCNativeMemory.address(buffer) - buffer.position() + position;
    }

    @Override
    public void memSet(long address, int value, long bytes) {
        TCNativeMemory.set(address, value, bytes);
    }

    @Override
    public void memCopy(long src, long dst, long bytes) {
        TCNativeMemory.copy(src, dst, bytes);
    }

    @Override
    public void memPutByte(long address, byte value) {
        TCNativeMemory.putByte(address, value);
    }

    @Override
    public void memPutShort(long address, short value) {
        TCNativeMemory.putShort(address, value);
    }

    @Override
    public void memPutInt(long address, int value) {
        TCNativeMemory.putInt(address, value);
    }

    @Override
    public void memPutFloat(long address, float value) {
        TCNativeMemory.putFloat(address, value);
    }

    @Override
    public void memPutLong(long address, long value) {
        TCNativeMemory.putLong(address, value);
    }

    @Override
    public void memPutAddress(long address, long value) {
        if (TCNativeMemory.pointerSize() == 8) {
            TCNativeMemory.putLong(address, value);
        } else {
            TCNativeMemory.putInt(address, (int) value);
        }
    }

    @Override
    public byte memGetByte(long address) {
        return TCNativeMemory.getByte(address);
    }

    @Override
    public short memGetShort(long address) {
        return TCNativeMemory.getShort(address);
    }

    @Override
    public int memGetInt(long address) {
        return TCNativeMemory.getInt(address);
    }

    @Override
    public float memGetFloat(long address) {
        return TCNativeMemory.getFloat(address);
    }

    @Override
    public long memGetLong(long address) {
        return TCNativeMemory.getLong(address);
    }

    @Override
    public long memGetAddress(long address) {
        return TCNativeMemory.pointerSize() == 8 ? TCNativeMemory.getLong(address) : (TCNativeMemory.getInt(address) & 0xFFFFFFFFL);
    }

    @Override
    public ByteBuffer memSlice(ByteBuffer buffer, int offset, int capacity) {
        return TCNativeMemory.wrap(TCNativeMemory.address(buffer) + offset, capacity);
    }
}
