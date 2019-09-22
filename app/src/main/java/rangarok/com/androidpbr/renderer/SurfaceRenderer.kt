package rangarok.com.androidpbr.renderer

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import rangarok.com.androidpbr.utils.SCENE_RADIANCE_SPHERE
import rangarok.com.androidpbr.ui.SceneRenderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SurfaceRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private lateinit var sceneRenderer: SceneRenderer

    private var renderScene = SCENE_RADIANCE_SPHERE
    private var surfaceCreated = false
    private var spin = false

    var afterRender: (()->Unit)? = null

    override fun onDrawFrame(gl: GL10?) {
        sceneRenderer.drawFrame(this.surfaceWidth, this.surfaceHeight) {
            afterRender?.invoke()
        }
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
        sceneRenderer.setRenderScene(renderScene)
        sceneRenderer.setSpin(spin)
        surfaceCreated = true
    }

    fun setMetallic(metallic: Float) {
        sceneRenderer.setMetallic(metallic)
    }

    fun setRougness(roughness: Float) {
        sceneRenderer.setRoughness(roughness)
    }


    fun setRenderScene(scene: Int) {
        renderScene = scene
        if (surfaceCreated) {
            sceneRenderer.setRenderScene(scene)
        }

    }

    fun setSpin(spin: Boolean) {
        if (surfaceCreated) {
            sceneRenderer.setSpin(spin)
        }
        this.spin = spin
    }

    companion object {
        const val TAG = "SurfaceRenderer"
    }

}