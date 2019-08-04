package rangarok.com.androidpbr

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder

class RenderGLSurfaceView : GLSurfaceView {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    private val renderer = SurfaceRenderer(context)

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY

        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(
                holder: SurfaceHolder?,
                format: Int,
                width: Int,
                height: Int
            ) {
                Log.i(TAG, "surfaceChanged, width:$width, height:$height, format:$format")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
            }

        })
    }

    fun setRougness(roughness: Float) {
        queueEvent {
            renderer.setRougness(roughness)
            requestRender()
        }
    }

    fun setMetallic(metallic: Float) {
        queueEvent {
            renderer.setMetallic(metallic)
            requestRender()
        }
    }

    companion object {
        const val TAG = "RenderGLSurfaceView"
    }
}