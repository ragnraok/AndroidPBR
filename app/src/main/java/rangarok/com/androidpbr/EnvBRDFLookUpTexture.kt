package rangarok.com.androidpbr

import android.opengl.GLES30
import android.util.Log

class EnvBRDFLookUpTexture {

    private var texId = 0

    private val envBrdfShader = Shader(envBrdfVs, envBrdfFs)
    private val quadRenderer = QuadRenderer()

    init {
        envBrdfShader.enable()

        val texArray = intArrayOf(0)
        GLES30.glGenTextures(1, texArray, 0)
        texId = texArray[0]

        val fboArray = intArrayOf(0)
        var fbo = 0
        GLES30.glGenFramebuffers(1, fboArray, 0)
        fbo = fboArray[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGB16F, EnvBrdfTextureSize, EnvBrdfTextureSize, 0, GLES30.GL_RGB, GLES30.GL_FLOAT, null)

        setup2DTexParam()

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)

        viewport(EnvBrdfTextureSize, EnvBrdfTextureSize)
        clearGL()
        quadRenderer.render()

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        Log.i(TAG, "finish render env brdf lut: $texId")

    }

    fun texId(): Int {
        return texId
    }

    companion object {
        const val TAG = "EnvBRDFLookUpTexture"
    }
}