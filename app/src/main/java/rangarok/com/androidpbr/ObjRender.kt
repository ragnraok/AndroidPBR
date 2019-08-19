package rangarok.com.androidpbr

import android.opengl.GLES30
import android.util.Log
import de.javagl.obj.Obj
import de.javagl.obj.ObjData
import de.javagl.obj.ObjReader
import de.javagl.obj.ObjUtils
import glm_.size
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

class ObjRender(input: InputStream) : PrimitiveRenderer {

    private var vao = 0

    private var obj: Obj? = null
    private var indices: IntArray? = null
    private var vertices: FloatBuffer? = null
    private var texCoords: FloatBuffer? = null
    private var normals: FloatBuffer? = null

    init {
        obj = ObjUtils.convertToRenderable(ObjReader.read(input))
        if (obj != null) {
            vao = genVAO()

            GLES30.glBindVertexArray(vao)

            indices = ObjData.getFaceVertexIndicesArray(obj)
            vertices = ObjData.getVertices(obj)
            texCoords = ObjData.getTexCoords(obj, 2)
            normals = ObjData.getNormals(obj)

            indices?.apply {
                val buffer = ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().put(this)
                buffer.position(0)
                val ebo = genBuffer()
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo)
                GLES30.glBufferData(
                    GLES30.GL_ELEMENT_ARRAY_BUFFER,
                    size * 4, buffer, GLES30.GL_STATIC_DRAW
                )
            }

            vertices?.apply {
                position(0)
                val vbo = genBuffer()
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, size, this, GLES30.GL_STATIC_DRAW)
                GLES30.glEnableVertexAttribArray(0)
                GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            }

            texCoords?.apply {
                position(0)
                val vbo = genBuffer()
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, size, this, GLES30.GL_STATIC_DRAW)
                GLES30.glEnableVertexAttribArray(1)
                GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, 0)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            }

            normals?.apply {
                position(0)
                val vbo = genBuffer()
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, size, this, GLES30.GL_STATIC_DRAW)
                GLES30.glEnableVertexAttribArray(2)
                GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, 0, 0)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            }

            GLES30.glBindVertexArray(0)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)

            Log.i(TAG, "finish init vao:$vao, indices:${indices?.size}, vertices:${vertices?.capacity()}, texCoords:${texCoords?.size}, normals:${normals?.size}")

            input.close()
        }
    }

    override fun render() {
        if (vao != 0) {
            GLES30.glBindVertexArray(vao)

            GLES30.glDrawElements(
                GLES30.GL_TRIANGLE_STRIP,
                indices?.size ?: 0,
                GLES30.GL_UNSIGNED_INT,
                0
            )
            GLES30.glBindVertexArray(0)
        }

    }

    companion object {
        const val TAG = "ObjRender"
    }

}