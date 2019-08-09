package rangarok.com.androidpbr

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class IrradianceTexture(context: Context) {

    private var texId = 0

    init {
        val texArray = intArrayOf(0)
        GLES30.glGenTextures(1, texArray, 0)
        texId = texArray[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, texId)

        val cubemapFaceMap = arrayOf(
            BitmapFactory.decodeStream(context.assets.open("irradiance_cubemap/right.png")),
            BitmapFactory.decodeStream(context.assets.open("irradiance_cubemap/left.png")),
            BitmapFactory.decodeStream(context.assets.open("irradiance_cubemap/top.png")),
            BitmapFactory.decodeStream(context.assets.open("irradiance_cubemap/bottom.png")),
            BitmapFactory.decodeStream(context.assets.open("irradiance_cubemap/front.png")),
            BitmapFactory.decodeStream(context.assets.open("irradiance_cubemap/back.png"))
        )

        for (i in 0 until cubemapFaceMap.size) {
            Log.i(TAG, "internal format:${GLUtils.getInternalFormat(cubemapFaceMap[i])}, $i")
            GLUtils.texImage2D(GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, cubemapFaceMap[i], 0)
        }

        setCubemapTexParam()

        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, 0)

        Log.i(TAG, "init irradiance texture finish: $texId")
    }

    fun texId(): Int {
        return texId
    }

    fun active(pbrShader: Shader) {
        pbrShader.setInt("irradianceMap", 0)

        // irradianceMpa use uniform texture 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, texId)
    }

    companion object {
        const val TAG = "IrradianceTexture"
    }
}