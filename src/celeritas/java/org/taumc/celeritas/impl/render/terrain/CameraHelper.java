package org.taumc.celeritas.impl.render.terrain;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.lax1dude.eaglercraft.opengl.TCRenderState;

public class CameraHelper {
    public static Vector3f getThirdPersonOffset() {
        final Vector3f offset = new Vector3f(); // third person offset
        float[] modelView = new float[16];
        TCRenderState.getModelView(modelView);
        final Matrix4f inverseModelView = new Matrix4f().set(modelView).invert();
        inverseModelView.transformPosition(offset);
        return offset;
    }
}
