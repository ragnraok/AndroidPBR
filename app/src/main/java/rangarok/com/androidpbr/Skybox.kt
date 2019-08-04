package rangarok.com.androidpbr

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import glm_.mat4x4.Mat4
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Skybox {

    var skyBoxVAO = 0

    var cubeMapTexture = 0

    var skyBoxShader = Shader(skyBoxVs, skyBoxFs)

    fun init(context: Context) {
        val texArray = intArrayOf(0)
        GLES30.glGenTextures(1, texArray, 0)
        cubeMapTexture = texArray[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, cubeMapTexture)

        val cubemapFaceMap = arrayOf(
            BitmapFactory.decodeStream(context.assets.open("simpleSkyBox/right.png")),
            BitmapFactory.decodeStream(context.assets.open("simpleSkyBox/left.png")),
            BitmapFactory.decodeStream(context.assets.open("simpleSkyBox/top.png")),
            BitmapFactory.decodeStream(context.assets.open("simpleSkyBox/bottom.png")),
            BitmapFactory.decodeStream(context.assets.open("simpleSkyBox/front.png")),
            BitmapFactory.decodeStream(context.assets.open("simpleSkyBox/back.png"))
        )

        for (i in 0 until cubemapFaceMap.size) {
            GLUtils.texImage2D(GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, cubemapFaceMap[i], 0)
        }

        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, 0)

        Log.i(TAG, "init cubemapTexture:$cubeMapTexture")
    }

    fun render(projection: Mat4, view: Mat4) {
        if (skyBoxVAO == 0) {
            val vaoArray = intArrayOf(0)
            GLES30.glGenVertexArrays(1, vaoArray, 0)
            skyBoxVAO = vaoArray[0]

            val vboArray = intArrayOf(0)
            GLES30.glGenBuffers(1, vboArray, 0)
            val vbo = vboArray[0]

            GLES30.glBindVertexArray(skyBoxVAO)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)

            val buffer = ByteBuffer.allocateDirect(SkyBoxVertices.size * 4).
                order(ByteOrder.nativeOrder()).asFloatBuffer().put(SkyBoxVertices)
            buffer.position(0)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, SkyBoxVertices.size * 4, buffer, GLES30.GL_STATIC_DRAW)

            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 3 * 4, 0)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            GLES30.glBindVertexArray(0)

            Log.i(TAG, "init skyBoxVAO:$skyBoxVAO")
        }

        skyBoxShader.enable()
        skyBoxShader.setMat4("projection", projection)
        skyBoxShader.setMat4("view", view)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glBindVertexArray(skyBoxVAO)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, cubeMapTexture)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 36)
        GLES30.glBindVertexArray(0)
        GLES30.glDepthFunc(GLES30.GL_LESS)

        Log.i(TAG, "draw skybox:$skyBoxVAO")
    }

    companion object {
        const val TAG = "Skybox"
    }
}