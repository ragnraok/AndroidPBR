package rangarok.com.androidpbr.brdf

import android.opengl.GLES30
import android.util.Log
import rangarok.com.androidpbr.renderer.CubeRenderer
import rangarok.com.androidpbr.utils.*

class IrradianceCalcTexture(cubeMapTexId: Int) {

    companion object {
        const val TAG = "IrradianceCalcTexture"
    }

    private val shader = Shader(CubeMapVs, IrrandianceCalcFs)

    private val texId: Int

    private val cubeRenderer = CubeRenderer()

    init {
        val texArray = intArrayOf(0)
        GLES30.glGenTextures(1, texArray, 0)
        texId = texArray[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, texId)

        for (i in 0 until 6) {
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, GLES30.GL_RGB16F, 32, 32, 0, GLES30.GL_RGB, GLES30.GL_FLOAT, null)
        }
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_CUBE_MAP)
        setCubemapTexParam(true)

        val fbo = genFBO()

        shader.enable()
        shader.setInt("environmentMap", 0)
        shader.setMat4("projection", cubemapProjection)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, cubeMapTexId)


        viewport(32, 32)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, fbo)
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, 32, 32)

        for (i in 0 until 6) {
            shader.setMat4("view", cubemapViews[i])
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, texId, 0);
            clearGL()
            cubeRenderer.render()
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        Log.i(TAG, "finish draw skybox texId:$texId")
    }

    fun texId(): Int {
        return texId
    }

    fun active(pbrShader: Shader) {
        pbrShader.setInt("irradianceMap", 0)

        // irradianceMap use uniform texture 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, texId)
    }
}