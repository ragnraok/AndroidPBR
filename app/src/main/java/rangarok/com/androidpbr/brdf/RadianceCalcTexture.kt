package rangarok.com.androidpbr.brdf

import android.opengl.GLES30
import android.util.Log
import rangarok.com.androidpbr.renderer.CubeRenderer
import rangarok.com.androidpbr.utils.*
import java.lang.Math.pow
import kotlin.math.pow


class RadianceCalcTexture(cubeMapTexId: Int) {

    companion object {
        const val TAG = "RadianceCalcTexture"
    }

    private val shader = Shader(CubeMapVs, RadianceCalcFs)

    private val texId: Int

    private val cubeRenderer = CubeRenderer()

    init {
        val texArray = intArrayOf(0)
        GLES30.glGenTextures(1, texArray, 0)
        texId = texArray[0]

        val radianceTextureSize = 128

        val fbo = genFBO()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, texId)

        for (i in 0 until 6) {
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, GLES30.GL_RGB16F, radianceTextureSize, radianceTextureSize, 0, GLES30.GL_RGB, GLES30.GL_FLOAT, null)
        }
        setCubemapTexParam(true)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_CUBE_MAP)

        shader.enable()
        shader.setInt("environmentMap", 0)
        shader.setMat4("projection", cubemapProjection)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, cubeMapTexId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

        for (mip in 0 until RadianceMipmapLevel) {
            val factor = pow(0.5, mip.toDouble())
            val mipWidth = (radianceTextureSize * factor).toInt()
            val mipHeight = (radianceTextureSize * factor).toInt()
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, fbo)
            GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, mipWidth, mipHeight)

            viewport(mipWidth, mipHeight)

            val roughness = mip / (RadianceMipmapLevel - 1).toFloat()
            shader.setFloat("roughness", roughness)

            for (i in 0 until 6) {
                Log.i(TAG, "draw face $i for mip level $mip, width:$mipWidth, height:$mipHeight")
                shader.setMat4("view", cubemapViews[i])
                GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, texId, mip)
                clearGL()
                cubeRenderer.render()
            }
        }



        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        Log.i(TAG, "finish draw radiance texId:$texId")
    }

    fun texId(): Int {
        return texId
    }

    fun active(pbrShader: Shader) {
        pbrShader.setInt("radianceMap", 1)

        // radianceMap use uniform texture 1
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, texId)
    }

}