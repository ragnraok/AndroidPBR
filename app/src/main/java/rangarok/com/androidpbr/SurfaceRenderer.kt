package rangarok.com.androidpbr

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SurfaceRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private lateinit var sceneRenderer: SceneRenderer

    override fun onDrawFrame(gl: GL10?) {
        sceneRenderer.drawFrame(this.surfaceWidth, this.surfaceHeight)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.i(TAG, "onSurfaceChanged:$width, height:$height")
        this.surfaceWidth = width
        this.surfaceHeight = height
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated")
        sceneRenderer = SceneRenderer(context)
    }

    fun setMetallic(metallic: Float) {
        sceneRenderer.setMetallic(metallic)
    }

    fun setRougness(roughness: Float) {
        sceneRenderer.setRougness(roughness)
    }

    companion object {
        const val TAG = "SurfaceRenderer"
    }

}