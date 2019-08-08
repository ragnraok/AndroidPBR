package rangarok.com.androidpbr

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class TriangleRenderer : PrimitiveRenderer {

    private var vao = 0

    override fun render() {
        if (vao == 0) {
            vao = genVAO()


            val vbo = genBuffer()

            GLES30.glBindVertexArray(vao)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
            val buffer = ByteBuffer.allocateDirect(TriangleVertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(TriangleVertices)
            buffer.position(0)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, TriangleVertices.size * 4, buffer, GLES30.GL_STATIC_DRAW)

            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 3 * 4, 0)
            GLES30.glEnableVertexAttribArray(0)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            GLES30.glBindVertexArray(0)

            Log.i(TAG, "finish setup vao:$vao")
        }

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        Log.i(TAG, "draw vao:$vao")
    }

    companion object {
        const val TAG = "TriangleRenderer"
    }

}