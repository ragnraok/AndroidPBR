package rangarok.com.androidpbr.brdf

import android.opengl.GLES30
import android.util.Log
import rangarok.com.androidpbr.utils.EnvBrdfFs
import rangarok.com.androidpbr.utils.EnvBrdfTextureSize
import rangarok.com.androidpbr.utils.EnvBrdfVs
import rangarok.com.androidpbr.renderer.QuadRenderer
import rangarok.com.androidpbr.utils.Shader
import rangarok.com.androidpbr.utils.clearGL
import rangarok.com.androidpbr.utils.setup2DTexParam
import rangarok.com.androidpbr.utils.viewport

class EnvBRDFLookUpTexture(renderToScreen: Boolean = false) {

    private var texId = 0

    private val envBrdfShader = Shader(
        EnvBrdfVs,
        EnvBrdfFs
    )
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

        val rboArray = intArrayOf(0)
        GLES30.glGenRenderbuffers(1, rboArray, 0)
        val rbo = rboArray[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RG32F,
            EnvBrdfTextureSize,
            EnvBrdfTextureSize, 0, GLES30.GL_RG, GLES30.GL_FLOAT, null)

        setup2DTexParam()

        if (!renderToScreen) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)

            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, rbo)
            GLES30.glRenderbufferStorage(
                GLES30.GL_RENDERBUFFER,
                GLES30.GL_DEPTH_COMPONENT24,
                EnvBrdfTextureSize,
                EnvBrdfTextureSize
            )
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                texId,
                0
            )

            viewport(
                EnvBrdfTextureSize,
                EnvBrdfTextureSize
            )
        }
        clearGL()
        quadRenderer.render()

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        Log.i(TAG, "finish render env brdf lut: $texId")

    }

    fun texId(): Int {
        return texId
    }

    fun active(pbrShader: Shader) {
        pbrShader.setInt("envBrdfMap", 2)

        // envBrdfMap use uniform texture 2
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
    }

    companion object {
        const val TAG = "EnvBRDFLookUpTexture"
    }
}