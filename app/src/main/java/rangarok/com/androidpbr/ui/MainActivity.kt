package rangarok.com.androidpbr.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import rangarok.com.androidpbr.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions(
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            1024
        )

        findViewById<Button>(R.id.sphere_scene_direct_light_button).setOnClickListener {
            startActivity(Intent(this@MainActivity, SphereSceneDirectLightUI::class.java))
        }

        findViewById<Button>(R.id.sphere_scene_irradiance_ibl).setOnClickListener {
            val intent = Intent(this@MainActivity, SphereSceneUI::class.java)
            intent.putExtra(SphereSceneUI.IRRADIANCE, true)
            startActivity(intent)
        }

        findViewById<Button>(R.id.sphere_scene_button).setOnClickListener {
            startActivity(Intent(this@MainActivity, SphereSceneUI::class.java))
        }

        findViewById<Button>(R.id.textured_sphere_scene_button).setOnClickListener {
            val intent = Intent(this@MainActivity, TexturedSphereSceneUI::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.monkey_scene_button).setOnClickListener {
            startActivity(Intent(this@MainActivity, ModelSceneUI::class.java))
        }

    }

    companion object {
        const val TAG = "AndroidPBR"
    }
}
