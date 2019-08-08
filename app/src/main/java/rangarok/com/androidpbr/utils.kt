package rangarok.com.androidpbr

import android.opengl.GLES30

fun clearGL() {
    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
    GLES30.glClearColor(0f, 0f, 0.0f, 1.0f)
}

fun viewport(width: Int, height: Int) {
    GLES30.glViewport(0, 0, width, height)
}

fun genVAO(): Int {
    val vaoArray = intArrayOf(0)
    GLES30.glGenVertexArrays(1, vaoArray, 0)
    return vaoArray[0]
}

fun genBuffer(): Int {
    val vboArray = intArrayOf(0)
    GLES30.glGenBuffers(1, vboArray, 0)
    return vboArray[0]
}

fun setup2DTexParam() {
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
}

fun setCubemapTexParam(withMipmap: Boolean = false) {
    GLES30.glTexParameteri(
        GLES30.GL_TEXTURE_CUBE_MAP,
        GLES30.GL_TEXTURE_MIN_FILTER,
        if(withMipmap)  GLES30.GL_LINEAR_MIPMAP_LINEAR else  GLES30.GL_LINEAR
    )
    GLES30.glTexParameteri(
        GLES30.GL_TEXTURE_CUBE_MAP,
        GLES30.GL_TEXTURE_MAG_FILTER,
        GLES30.GL_LINEAR
    )
    GLES30.glTexParameteri(
        GLES30.GL_TEXTURE_CUBE_MAP,
        GLES30.GL_TEXTURE_WRAP_R,
        GLES30.GL_CLAMP_TO_EDGE
    )
    GLES30.glTexParameteri(
        GLES30.GL_TEXTURE_CUBE_MAP,
        GLES30.GL_TEXTURE_WRAP_S,
        GLES30.GL_CLAMP_TO_EDGE
    )
    GLES30.glTexParameteri(
        GLES30.GL_TEXTURE_CUBE_MAP,
        GLES30.GL_TEXTURE_WRAP_T,
        GLES30.GL_CLAMP_TO_EDGE
    )

}