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
import rangarok.com.androidpbr.brdf.*
import rangarok.com.androidpbr.renderer.ObjRender
import rangarok.com.androidpbr.renderer.Skybox
import rangarok.com.androidpbr.renderer.SkyboxCalcTex
import rangarok.com.androidpbr.renderer.SphereRenderer
import rangarok.com.androidpbr.utils.*

class SceneRenderer(private val context: Context) {

    private var sceneWidth = 0
    private var sceneHeight = 0

    private var pbrShader: Shader? = null
    private val sphereRenderer = SphereRenderer()

    private var hdrTex = uploadTexture(context, "envs/newport_loft.png")
    private var skyboxCalcTex = SkyboxCalcTex(hdrTex)

    private val skybox = Skybox().apply { initWithSkyboxTex(skyboxCalcTex.texId()) }

    private val camera = Camera(Vec3(0, 1, 6))

    private var metallic = 0.5f
    private var roughness = 0.5f

//    private var irradianceTexture = IrradianceTexture(context)
    private var irradianceTexture = IrradianceCalcTexture(skyboxCalcTex.texId())
    private var radianceTexture = RadianceCalcTexture(skyboxCalcTex.texId())
//    private var envBRDFLookUpTexture = EnvBRDFLookUpTexture()

    private var objRenderer =
        ObjRender(context.assets.open("monkey/monkey.obj"))

    private var renderScene: Int = SCENE_SPHERE

    private var rotateDegree: Float = 0f
    private var spin: Boolean = false
    private var lastRenderTick = 0L
    private var setRenderTick = false
    private var timeToLastRenderTick = 0L

    private var monkeyAlbedoMapTexId = 0
    private var monkeyNormalMapTexId = 0
    private var monkeyMetallicMapTexId = 0
    private var monkeyRoughnessMapTexId = 0
    private var monkeyAoMapTexId = 0

    private var sphereAlbedoTexIds = intArrayOf(0, 0, 0)
    private var sphereNormalTexIds = intArrayOf(0, 0, 0)
    private var sphereMetallicTexIds = intArrayOf(0, 0, 0)
    private var sphereRoughnessTexIds = intArrayOf(0, 0, 0)
    private var sphereAoTexIds = intArrayOf(0, 0, 0)

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
            } else if (renderScene == SCENE_TEXTURE_SPHERE) {
                renderTextureSphereScene(projection, view)
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
        drawPBRModel(projection, view, model, monkeyAlbedoMapTexId, monkeyNormalMapTexId, monkeyMetallicMapTexId, monkeyRoughnessMapTexId, monkeyAoMapTexId)

        model = Mat4(1.0)
        model.translate(Vec3(1.5, -0.0, -3.0), model)
        model.rotate(-100.0f - rotateDegree, Vec3(0.0, 1.0, 0.0), model)
        drawPBRModel(projection, view, model, monkeyAlbedoMapTexId, monkeyNormalMapTexId, monkeyMetallicMapTexId, monkeyRoughnessMapTexId, monkeyAoMapTexId)

        model = Mat4(1.0)
        model.translate(Vec3(-1.5, -0.0, -3.0), model)
        model.rotate(100.0f + rotateDegree, Vec3(0.0, 1.0, 0.0), model)
        drawPBRModel(projection, view, model, monkeyAlbedoMapTexId, monkeyNormalMapTexId, monkeyMetallicMapTexId, monkeyRoughnessMapTexId, monkeyAoMapTexId)

    }

    private fun renderTextureSphereScene(projection: Mat4, view: Mat4) {
        if (spin) {
            rotateDegree += timeToLastRenderTick / SPIN_DURATION.toFloat() * 360.0f
        }
        var model = Mat4(1.0)
        // center sphere
        model.rotate(rotateDegree, Vec3(1.0, 1.0, 0.0), model)
        drawPBRTextureShphere(projection, view, model, sphereAlbedoTexIds[0], sphereNormalTexIds[0], sphereMetallicTexIds[0], sphereRoughnessTexIds[0], sphereAoTexIds[0])

        // right sphere
        model = Mat4(1.0f)
        model.translate(Vec3(1.5, 0.0, -3.0), model)
        model.rotate(-100.0f - rotateDegree, Vec3(0.0, 1.0, 0.0), model)
        drawPBRTextureShphere(projection, view, model, sphereAlbedoTexIds[1], sphereNormalTexIds[1], sphereMetallicTexIds[1], sphereRoughnessTexIds[1], sphereAoTexIds[1])

        // left sphere
        model = Mat4(1.0f)
        model.translate(Vec3(-1.5, 0.0, -3.0), model)
        model.rotate(100.0f + rotateDegree, Vec3(0.0, 1.0, 0.0), model)
        drawPBRTextureShphere(projection, view, model, sphereAlbedoTexIds[2], sphereNormalTexIds[2], sphereMetallicTexIds[2], sphereRoughnessTexIds[2], sphereAoTexIds[2])
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
        } else if (renderScene == SCENE_MONKEY_MODEL || renderScene == SCENE_TEXTURE_SPHERE) {
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

        if (renderScene == SCENE_TEXTURE_SPHERE) {
            initSphereTexture()
        }
        if (renderScene == SCENE_MONKEY_MODEL) {
            initMonkeyTexture()
        }
    }

    private fun initSphereTexture() {
        sphereAlbedoTexIds[0] =
            uploadTexture(context, "gold/albedo.png")
        sphereNormalTexIds[0] =
            uploadTexture(context, "gold/normal.png")
        sphereMetallicTexIds[0] =
            uploadTexture(context, "gold/metallic.png")
        sphereRoughnessTexIds[0] = uploadTexture(context, "gold/roughness.png")
        sphereAoTexIds[0] = uploadTexture(context, "gold/ao.png")

        sphereAlbedoTexIds[1] =
            uploadTexture(context, "cavefloor1-Unreal-Engine/cavefloor1_Base_Color.png")
        sphereNormalTexIds[1] =
            uploadTexture(context, "cavefloor1-Unreal-Engine/cavefloor1_Normal.png")
        sphereMetallicTexIds[1] =
            uploadTexture(context, "cavefloor1-Unreal-Engine/cavefloor1_Metallic.png")
        sphereRoughnessTexIds[1] =
            uploadTexture(context, "cavefloor1-Unreal-Engine/cavefloor1_Roughness.png")
        sphereAoTexIds[1] = uploadTexture(context, "cavefloor1-Unreal-Engine/cavefloor1_Ambient_Occlusion.png")

        sphereAlbedoTexIds[2] =
            uploadTexture(context, "scuffed-plastic-1-Unreal-Engine/scuffed-plastic-alb.png")
        sphereNormalTexIds[2] =
            uploadTexture(context, "scuffed-plastic-1-Unreal-Engine/scuffed-plastic-normal.png")
        sphereMetallicTexIds[2] =
            uploadTexture(context, "scuffed-plastic-1-Unreal-Engine/scuffed-plastic-metal.png")
        sphereRoughnessTexIds[2] =
            uploadTexture(context, "scuffed-plastic-1-Unreal-Engine/scuffed-plastic-rough.png")
        sphereAoTexIds[2] = uploadTexture(context, "scuffed-plastic-1-Unreal-Engine/scuffed-plastic-ao.png")
    }

    private fun initMonkeyTexture() {
        monkeyAlbedoMapTexId =
            uploadTexture(context, "monkey/albedo.png")
        monkeyNormalMapTexId =
            uploadTexture(context, "monkey/normal.png")
        monkeyMetallicMapTexId =
            uploadTexture(context, "monkey/metallic.png")
        monkeyRoughnessMapTexId =
            uploadTexture(context, "monkey/roughness.png")
        monkeyAoMapTexId = uploadTexture(context, "monkey/ao.png")
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

    private fun drawPBRTextureShphere(projection: Mat4, view: Mat4, model: Mat4,
                                      albedoMapTexId: Int, normalMapTexId: Int,
                                      metallicMapTexId: Int, roughnessMapTexId: Int,
                                      aoMapTexId: Int) {
        Log.i(TAG, "drawPBRTextureShphere, sceneWidth:$sceneWidth, sceneHeight:$sceneHeight")
        setUpPBRShaderWithTextures(projection, view, model, albedoMapTexId, normalMapTexId, metallicMapTexId, roughnessMapTexId, aoMapTexId)

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