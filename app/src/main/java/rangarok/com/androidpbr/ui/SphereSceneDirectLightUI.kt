package rangarok.com.androidpbr.ui

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import rangarok.com.androidpbr.R
import rangarok.com.androidpbr.utils.SCENE_DIRECT_LIGHT
import kotlin.math.max

class SphereSceneDirectLightUI : AppCompatActivity() {
    var drawView: RenderGLSurfaceView? = null
    var roughnessSeekbar: SeekBar? = null
    var metallicSeekbar: SeekBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sphere_scene_ui)

        drawView = findViewById(R.id.draw_view)
        roughnessSeekbar = findViewById(R.id.roughness_seekbar)
        metallicSeekbar = findViewById(R.id.metallic_seekbar)

        roughnessSeekbar?.progress = 50
        metallicSeekbar?.progress = 50

        roughnessSeekbar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                drawView?.setRoughness(max(progress / 100.0f, 0.1f))
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }

        })

        metallicSeekbar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                drawView?.setMetallic(max(progress / 100.0f, 0.1f))
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }

        })
        drawView?.setRenderScene(SCENE_DIRECT_LIGHT)
    }

    companion object {
        const val TAG = "SphereSceneUI"
    }
}