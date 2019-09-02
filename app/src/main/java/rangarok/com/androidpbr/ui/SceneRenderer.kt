package rangarok.com.androidpbr.ui

import android.content.Context
import android.opengl.GLES30
import android.os.Handler
import android.os.Looper
import android.util.Log
import glm_.glm
import glm_.mat3x3.Mat3
import glm_.mat4x4.Mat4
import glm_.vec3.Vec3
import rangarok.com.androidpbr.brdf.EnvBRDFLookUpTexture
import rangarok.com.androidpbr.brdf.IrradianceTexture
import rangarok.com.androidpbr.brdf.RadianceTexture
import rangarok.com.androidpbr.renderer.ObjRender
import rangarok.com.androidpbr.renderer.Skybox
import rangarok.com.androidpbr.renderer.SphereRenderer
import rangarok.com.androidpbr.utils.*

class SceneRenderer(private val context: Context) {

    private var sceneWidth = 0
    private var sceneHeight = 0

    private var pbrShader: Shader? = null
    private val sphereRenderer = SphereRenderer()

    private val skybox = Skybox().apply { init(context) }

    private val camera = Camera(Vec3(0, 1, 6))

    private var metallic = 0.5f
    private var roughness = 0.5f

    private var irradianceTexture = IrradianceTexture(context)
    private var radianceTexture = RadianceTexture(context)
//    private var envBRDFLookUpTexture = EnvBRDFLookUpTexture()

    private var objRenderer =
        ObjRender(context.assets.open("monkey/monkey.obj"))

    private var renderScene: Int = SCENE_SPHERE

    private var rotateDegree: Float = 0f
    private var spin: Boolean = false
    private var lastRenderTick = 0L
    private var setRenderTick = false
    private var timeToLastRenderTick = 0L

    private val albedoMapTexId =
        uploadTexture(context, "monkey/albedo.png")
    private val normalMapTexId =
        uploadTexture(context, "monkey/normal.png")
    private val metallicMapTexId =
        uploadTexture(context, "monkey/metallic.png")
    private val roughnessMapTexId =
        uploadTexture(context, "monkey/roughness.png")
    private val aoMapTexId = uploadTexture(context, "monkey/ao.png")

    private val handler = Handler(Looper.getMainLooper())

    fun drawFrame(sceneWidth: Int, sceneHeight: Int, afterRender: (()->Unit)? = null) {
        this.sceneWidth = sceneWidth
        this.sceneHeight = sceneHeight

        val tick = currentTick()

        timeToLastRenderTick = if (setRenderTick) tickToNowMs(
            lastRenderTick
        ) else 0
        setRenderTick = true
        lastRenderTick = currentTick()

        viewport(sceneWidth, sceneHeight)
        clearGL()
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        val projection = glm.perspective(glm.radians(camera.zoom), sceneWidth/sceneHeight.toFloat(), 0.1f, 200.0f)
        val view = camera.lookAt(Vec3(0))

        if (pbrShader != null) {
            if (renderScene == SCENE_MONKEY_MODEL) {
                renderModelsScene(projection, view)
            } else if (renderScene == SCENE_SPHERE || renderScene == SCENE_DIRECT_LIGHT || renderScene == SCENE_IRRADIANCE_IBL) {
                renderSphereScene(projection, view)
            }

            skybox.render(projection, Mat4(Mat3(view)))
        }

        cleanup()
        GLES30.glFinish()

//        renderEnvBrdfLookupTexture()
        Log.i(
            TAG, "render cost:${tickToNowMs(tick)}ms, timeToLastRenderTick:$timeToLastRenderTick")

        handler.post(afterRender)

    }

    private fun renderSphereScene(projection: Mat4, view: Mat4) {
        var model = Mat4(1.0)
        // center sphere
        drawPBRSphere(projection, view, model)

        // right sphere
        model = Mat4(1.0f)
        model.translate(Vec3(1.5, 0.0, -3.0), model)
        drawPBRSphere(projection, view, model)

        // left sphere
        model = Mat4(1.0f)
        model.translate(Vec3(-1.5, 0.0, -3.0), model)
        drawPBRSphere(projection, view, model)
    }

    private fun renderModelsScene(projection: Mat4, view: Mat4) {

        if (spin) {
            rotateDegree += timeToLastRenderTick / SPIN_DURATION.toFloat() * 360.0f
        }
        Log.i(TAG, "renderModelsScene, rotate:$rotateDegree")

        var model = Mat4(1.0)
        model.translate(Vec3(0.0, -0.0, 0.0), model)
        model.rotate(rotateDegree, Vec3(1.0, 1.0, 0.0), model)
        drawPBRModel(projection, view, model, albedoMapTexId, normalMapTexId, metallicMapTexId, roughnessMapTexId, aoMapTexId)

        model = Mat4(1.0)
        model.translate(Vec3(1.5, -0.0, -3.0), model)
        model.rotate(-100.0f - rotateDegree, Vec3(0.0, 1.0, 0.0), model)
        drawPBRModel(projection, view, model, albedoMapTexId, normalMapTexId, metallicMapTexId, roughnessMapTexId, aoMapTexId)

        model = Mat4(1.0)
        model.translate(Vec3(-1.5, -0.0, -3.0), model)
        model.rotate(100.0f + rotateDegree, Vec3(0.0, 1.0, 0.0), model)
        drawPBRModel(projection, view, model, albedoMapTexId, normalMapTexId, metallicMapTexId, roughnessMapTexId, aoMapTexId)

    }

    fun setSpin(spin: Boolean) {
        this.spin = spin
    }

    fun setMetallic(metallic: Float) {
        if (metallic in 0.0..1.0) {
            this.metallic = metallic
        }
    }

    fun setRoughness(roughness: Float) {
        if (roughness in 0.0..1.0) {
            this.roughness = roughness
        }
    }

    fun setRenderScene(scene: Int) {
        this.renderScene = scene
        if (renderScene == SCENE_SPHERE) {
            pbrShader = Shader(
                PbrVs,
                PbrWithSpecularRadianceIBLFAndEnvBrdCalcs
            )
        } else if (renderScene == SCENE_MONKEY_MODEL) {
            pbrShader = Shader(
                PbrVs,
                PbrWithSpecularRadianceIBLFAndEnvBrdCalcsAndTextures
            )
        } else if (renderScene == SCENE_DIRECT_LIGHT) {
            pbrShader = Shader(
                PbrVs,
                PbrDirectLightFs
            )
        } else if (renderScene == SCENE_IRRADIANCE_IBL) {
            pbrShader = Shader(
                PbrVs,
                PbrWithIrradianceIBLFs
            )
        }
    }

    private fun setUpPBRShader(projection: Mat4, view: Mat4, model: Mat4) {
        pbrShader?.let {
            it.enable()
            it.setVec3("albedo", Vec3(1.0, 1.0, 1.0))
            it.setFloat("ao", 1.0f)
            it.setMat4("projection", projection)
            it.setMat4("view", view)
            it.setVec3("camPos", camera.position)
            it.setFloat("metallic", metallic)
            it.setFloat("roughness", roughness)
            it.setVec3("ambient", Vec3(0.1, 0.1, 0.1))
            it.setMat4("model", model)

            for (i in 0 until PointLightPositions.size) {
                it.setVec3("pointLightPositions[$i]", PointLightPositions[i])
                it.setVec3("pointLightColors[$i]", PointLightColors[i])
            }
            it.setVec3("directionLightDir", DirectionalLightDir)
            it.setVec3("directionLightColor", SphereSceneDirectionalLightColor)
            irradianceTexture.active(it)
            radianceTexture.active(it)

//        envBRDFLookUpTexture.active(it)
        }
    }

    private fun setUpPBRShaderWithTextures(projection: Mat4, view: Mat4, model: Mat4,
                                           albedoMapTexId: Int, normalMapTexId: Int,
                                           metallicMapTexId: Int, roughnessMapTexId: Int,
                                           aoMapTexId: Int) {

        pbrShader?.let {
            it.enable()
            if (albedoMapTexId > 0) {
                activeTexture(
                    albedoMapTexId,
                    albedoMapSlot
                )
            }
            if (normalMapTexId > 0) {
                activeTexture(
                    normalMapTexId,
                    normalMapSlot
                )
            }
            if (roughnessMapTexId > 0) {
                activeTexture(
                    roughnessMapTexId,
                    roughnessMapSlot
                )
            }
            if (metallicMapTexId > 0) {
                activeTexture(
                    metallicMapTexId,
                    metallicMapSlot
                )
            }
            if (aoMapTexId > 0) {
                activeTexture(aoMapTexId, aoMapSlot)
            }

            it.setInt("albedoMap", albedoMapSlot)
            it.setInt("normalMap", normalMapSlot)
            it.setInt("metallicMap", metallicMapSlot)
            it.setInt("roughnessMap", roughnessMapSlot)
            it.setInt("aoMap", aoMapSlot)
            it.setMat4("projection", projection)
            it.setMat4("view", view)
            it.setVec3("camPos", camera.position)
            it.setFloat("metallic", metallic)
            it.setFloat("roughness", roughness)
            it.setVec3("ambient", Vec3(0.1, 0.1, 0.1))
            it.setMat4("model", model)

            for (i in 0 until PointLightPositions.size) {
                it.setVec3("pointLightPositions[$i]", PointLightPositions[i])
                it.setVec3("pointLightColors[$i]", PointLightColors[i])
            }
            it.setVec3("directionLightDir", DirectionalLightDir)
            it.setVec3("directionLightColor", ModelSceneDirectionalLightColor)
            irradianceTexture.active(it)
            radianceTexture.active(it)
//        envBRDFLookUpTexture.active(it)
        }

    }

    private fun drawPBRSphere(projection: Mat4, view: Mat4, model: Mat4) {
        Log.i(TAG, "drawPBRSphere, sceneWidth:$sceneWidth, sceneHeight:$sceneHeight")
        setUpPBRShader(projection, view, model)

        sphereRenderer.render()

        cleanup()
    }

    private fun drawPBRModel(projection: Mat4, view: Mat4, model: Mat4,
                             albedoMapTexId: Int, normalMapTexId: Int,
                             metallicMapTexId: Int, roughnessMapTexId: Int,
                             aoMapTexId: Int) {
        Log.i(TAG, "drawPBRModel, sceneWidth:$sceneWidth, sceneHeight:$sceneHeight")
        setUpPBRShaderWithTextures(projection, view, model, albedoMapTexId, normalMapTexId, metallicMapTexId, roughnessMapTexId, aoMapTexId)

        objRenderer.render()

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

        const val SPIN_DURATION = 300_000
    }

}