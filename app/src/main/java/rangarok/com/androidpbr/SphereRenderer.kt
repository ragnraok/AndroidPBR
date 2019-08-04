package rangarok.com.androidpbr

import android.opengl.GLES30
import android.util.Log
import glm_.vec2.Vec2
import glm_.vec3.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SphereRenderer : PrimitiveRenderer {

    private var sphereVAO = 0
    private var sphereIndexCount = 0


    override fun render() {
        if (sphereVAO == 0) {
            val sphereVaoArrary = intArrayOf(1)
            GLES30.glGenVertexArrays(1, sphereVaoArrary, 0)
            sphereVAO = sphereVaoArrary[0]

            val vboArray = intArrayOf(1)
            val eboArray = intArrayOf(1)
            GLES30.glGenBuffers(1, vboArray, 0)
            GLES30.glGenBuffers(1, eboArray, 0)
            val vbo = vboArray[0]
            val ebo = eboArray[0]

            val positions: ArrayList<Vec3> = arrayListOf()
            val uvs: ArrayList<Vec2> = arrayListOf()
            val normals: ArrayList<Vec3> = arrayListOf()
            val indices: ArrayList<Int> = arrayListOf()

            val X_SEGMENTS = 64
            val Y_SEGMENTS = 64
            for (y in 0..Y_SEGMENTS) {
                for (x in 0..X_SEGMENTS) {
                    val xSegment = x.toFloat() / X_SEGMENTS
                    val ySegment = y.toFloat() / Y_SEGMENTS
                    val xPos = cos(xSegment * 2.0f * PI) * sin(ySegment * PI)
                    val yPos = cos(ySegment * PI)
                    val zPos = sin(xSegment * 2.0f * PI) * sin(ySegment * PI)

                    positions.add(Vec3(xPos, yPos, zPos))
                    uvs.add(Vec2(xSegment, ySegment))
                    normals.add(Vec3(xPos, yPos, zPos))
                }
            }

            var oddRow = false
            for (y in 0 until Y_SEGMENTS) {
                if (!oddRow) // even rows: y == 0, y == 2; and so on
                {
                    for (x in 0..X_SEGMENTS) {
                        indices.add(y * (X_SEGMENTS + 1) + x)
                        indices.add((y + 1) * (X_SEGMENTS + 1) + x)
                    }
                } else {
                    for (x in X_SEGMENTS downTo 0) {
                        indices.add((y + 1) * (X_SEGMENTS + 1) + x)
                        indices.add(y * (X_SEGMENTS + 1) + x)
                    }
                }
                oddRow = !oddRow
            }
            sphereIndexCount = indices.size

            val data: ArrayList<Float> = arrayListOf()
            for (i in 0 until positions.size) {
                data.add(positions[i].x)
                data.add(positions[i].y)
                data.add(positions[i].z)
                if (uvs.size > 0) {
                    data.add(uvs[i].x)
                    data.add(uvs[i].y)
                }
                if (normals.size > 0) {
                    data.add(normals[i].x)
                    data.add(normals[i].y)
                    data.add(normals[i].z)
                }
            }

            GLES30.glBindVertexArray(sphereVAO)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)

            val buffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(data.toFloatArray())
            buffer.position(0)
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                data.size * 4,
                buffer,
                GLES30.GL_STATIC_DRAW
            )

            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo)
            val indicesBuffer = ByteBuffer.allocateDirect(indices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().put(indices.toIntArray())
            indicesBuffer.position(0)
            GLES30.glBufferData(
                GLES30.GL_ELEMENT_ARRAY_BUFFER,
                indices.size * 4, indicesBuffer, GLES30.GL_STATIC_DRAW
            )

            val stride = (3 + 2 + 3) * 4

            // positions
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)

            // texCoords
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, 3 * 4)

            // normals
            GLES30.glEnableVertexAttribArray(2)
            GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, stride, 5 * 4)

            Log.i(TAG, "setup sphereVAO finish:$sphereVAO, sphereIndexCount:$sphereIndexCount")

            GLES30.glBindVertexArray(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        }

        GLES30.glBindVertexArray(sphereVAO)
        GLES30.glDrawElements(
            GLES30.GL_TRIANGLE_STRIP,
            sphereIndexCount,
            GLES30.GL_UNSIGNED_INT,
            0
        )
        Log.i(TAG, "draw sphereVAO")
    }

    companion object {
        const val TAG = "SphereRenderer"
    }
}