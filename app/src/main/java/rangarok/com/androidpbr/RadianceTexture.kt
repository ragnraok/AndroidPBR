package rangarok.com.androidpbr

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log

class RadianceTexture(context: Context) {
    private var texId = 0

    init {
        val texArray = intArrayOf(0)
        GLES30.glGenTextures(1, texArray, 0)
        texId = texArray[0]

        val mipmapLevelCubeMapFaceBitmap = { level: Int ->
            arrayOf(
                BitmapFactory.decodeStream(context.assets.open("radiance_cubemap/m${level}_right.png")),
                BitmapFactory.decodeStream(context.assets.open("radiance_cubemap/m${level}_left.png")),
                BitmapFactory.decodeStream(context.assets.open("radiance_cubemap/m${level}_top.png")),
                BitmapFactory.decodeStream(context.assets.open("radiance_cubemap/m${level}_bottom.png")),
                BitmapFactory.decodeStream(context.assets.open("radiance_cubemap/m${level}_front.png")),
                BitmapFactory.decodeStream(context.assets.open("radiance_cubemap/m${level}_back.png"))
            )
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, texId)

        for (level in 0 until RadianceMipmapLevel) {
            val faceMap = mipmapLevelCubeMapFaceBitmap(level)
            for (i in 0 until faceMap.size) {
                Log.i(TAG, "internal format:${GLUtils.getInternalFormat(faceMap[i])}, $i")
                GLUtils.texImage2D(
                    GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i,
                    level,
                    faceMap[i],
                    0
                )
            }
        }

        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_CUBE_MAP,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR_MIPMAP_LINEAR
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

        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_CUBE_MAP)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, 0)

        Log.i(TAG, "init radiance texture finish: $texId")
    }

    fun active(pbrShader: Shader) {
        pbrShader.setInt("radianceMap", 1)

        // radianceMap use uniform texture 1
        GLES30.glActiveTexture(1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, texId)
    }
    companion object {
        const val TAG = "RadianceTexture"
    }
}