package rangarok.com.androidpbr.ui

import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import androidx.appcompat.app.AppCompatActivity
import rangarok.com.androidpbr.R
import rangarok.com.androidpbr.utils.SCENE_MONKEY_MODEL
import rangarok.com.androidpbr.utils.SCENE_TEXTURE_SPHERE

class TexturedSphereSceneUI : AppCompatActivity() {

    var drawView: RenderGLSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.model_scene_ui)

        drawView = findViewById(R.id.draw_view)

        drawView?.setRenderScene(SCENE_TEXTURE_SPHERE)

        drawView?.holder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(
                holder: SurfaceHolder?,
                format: Int,
                width: Int,
                height: Int
            ) {
                Log.i(TAG, "surfaceChanged")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
            }

            override fun surfaceCreated(holder: SurfaceHolder?) {

            }
        })
        drawView?.setSpin(true)
    }

    companion object {
        const val TAG = "ModelSceneUI"
    }
}