package rangarok.com.androidpbr

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import glm_.glm
import glm_.mat3x3.Mat3
import glm_.mat4x4.Mat4
import glm_.vec3.Vec3

class SceneRenderer(context: Context) {

    private var sceneWidth = 0
    private var sceneHeight = 0

    private val pbrShader: Shader = Shader(PbrVs, PbrWithSpecularRadianceIBLFs)
    private val sphereRenderer = SphereRenderer()
    private val cubeRenderer = CubeRenderer()
    private val triangleRenderer = TriangleRenderer()

    private val skybox = Skybox().apply { init(context) }

    private val camera = Camera(Vec3(5, 2, 3))

    private var metallic = 0.5f
    private var roughness = 0.5f

    private var irradianceTexture = IrradianceTexture(context)
    private var radianceTexture = RadianceTexture(context)
    private var envBRDFLookUpTexture = EnvBRDFLookUpTexture()


    fun drawFrame(sceneWidth: Int, sceneHeight: Int) {
        this.sceneWidth = sceneWidth
        this.sceneHeight = sceneHeight

        viewport(sceneWidth, sceneHeight)
        clearGL()
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        val projection = glm.perspective(glm.radians(camera.zoom), sceneWidth/sceneHeight.toFloat(), 0.1f, 200.0f)
        val view = camera.lookAt(Vec3(0))

        drawPBRSphere(projection, view)

        skybox.render(projection, Mat4(Mat3(view)))

        cleanup()

//        renderEnvBrdfLookupTexture()

    }

    fun setMetallic(metallic: Float) {
        if (metallic in 0.0..1.0) {
            this.metallic = metallic
        }
    }

    fun setRougness(roughness: Float) {
        if (roughness in 0.0..1.0) {
            this.roughness = roughness
        }
    }

    private fun drawPBRSphere(projection: Mat4, view: Mat4) {
        Log.i(TAG, "drawPBRSphere, sceneWidth:$sceneWidth, sceneHeight:$sceneHeight")
        pbrShader.enable()
        pbrShader.setVec3("albedo", Vec3(0.5, 0.5, 0.5))
        pbrShader.setFloat("ao", 1.0f)
        pbrShader.setMat4("projection", projection)
        pbrShader.setMat4("view", view)
        pbrShader.setVec3("camPos", camera.position)
        pbrShader.setFloat("metallic", metallic)
        pbrShader.setFloat("roughness", roughness)
        pbrShader.setVec3("ambient", Vec3(0.1, 0.1, 0.1))
        val model = Mat4(1.0)
        pbrShader.setMat4("model", model)

        for (i in 0 until LightPositions.size) {
            pbrShader.setVec3("lightPositions[$i]", LightPositions[i])
            pbrShader.setVec3("lightColors[$i]", LightColors[i])
        }
        irradianceTexture.active(pbrShader)
        radianceTexture.active(pbrShader)
        envBRDFLookUpTexture.active(pbrShader)

        sphereRenderer.render()

        cleanup()
    }

    fun renderEnvBrdfLookupTexture() {
        EnvBRDFLookUpTexture(renderToScreen = true)
    }


    private fun cleanup() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, 0)
    }

    companion object {
        const val TAG = "SceneRenderer"
    }

}