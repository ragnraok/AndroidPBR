package rangarok.com.androidpbr

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import glm_.glm
import glm_.mat3x3.Mat3
import glm_.mat4x4.Mat4
import glm_.vec3.Vec3

class SceneRenderer(private val context: Context) {

    private var sceneWidth = 0
    private var sceneHeight = 0

    private val pbrShader: Shader = Shader(PbrVs, PbrWithSpecularRadianceIBLFAndEnvBrdCalcsAndTextures)
    private val sphereRenderer = SphereRenderer()

    private val skybox = Skybox().apply { init(context) }

    private val camera = Camera(Vec3(0, 2, 6))

    private var metallic = 0.5f
    private var roughness = 0.5f

    private var irradianceTexture = IrradianceTexture(context)
    private var radianceTexture = RadianceTexture(context)
//    private var envBRDFLookUpTexture = EnvBRDFLookUpTexture()

    private var objRenderer = ObjRender(context.assets.open("monkey/monkey.obj"))


    //TODO: fix light spot shape problem
    fun drawFrame(sceneWidth: Int, sceneHeight: Int) {
        this.sceneWidth = sceneWidth
        this.sceneHeight = sceneHeight

        viewport(sceneWidth, sceneHeight)
        clearGL()
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        val projection = glm.perspective(glm.radians(camera.zoom), sceneWidth/sceneHeight.toFloat(), 0.1f, 200.0f)
        val view = camera.lookAt(Vec3(0))

        renderModelsScene(projection, view)
//        renderSphereScene(projection, view)

        skybox.render(projection, Mat4(Mat3(view)))

        cleanup()

//        renderEnvBrdfLookupTexture()

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
        //TODO: fix uv error
        val albedoMapTexId = uploadTexture(context, "monkey/albedo.png")
        val normalMapTexId = uploadTexture(context, "monkey/normal.png")
        val metallicMapTexId =  uploadTexture(context,  "monkey/metallic.png")
        val roughnessMapTexId = uploadTexture(context,  "monkey/roughness.png")
        val aoMapTexId = uploadTexture(context, "monkey/ao.png")

        var model = Mat4(1.0)
        model.translate(Vec3(0.0, -0.0, 0.0), model)
        drawPBRModel(projection, view, model, albedoMapTexId, normalMapTexId, metallicMapTexId, roughnessMapTexId, aoMapTexId)

        model = Mat4(1.0)
        model.translate(Vec3(1.5, -0.0, -3.0), model)
        model.rotate(-180.0f, Vec3(0.0, 1.0, 0.0), model)
        drawPBRModel(projection, view, model, albedoMapTexId, normalMapTexId, metallicMapTexId, roughnessMapTexId, aoMapTexId)

        model = Mat4(1.0)
        model.translate(Vec3(-1.5, -0.0, -3.0), model)
        model.rotate(180.0f, Vec3(0.0, 1.0, 0.0), model)
        drawPBRModel(projection, view, model, albedoMapTexId, normalMapTexId, metallicMapTexId, roughnessMapTexId, aoMapTexId)

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

    private fun setUpPBRShader(projection: Mat4, view: Mat4, model: Mat4) {
        pbrShader.enable()
        pbrShader.setVec3("albedo", Vec3(1.0, 1.0, 1.0))
        pbrShader.setFloat("ao", 1.0f)
        pbrShader.setMat4("projection", projection)
        pbrShader.setMat4("view", view)
        pbrShader.setVec3("camPos", camera.position)
        pbrShader.setFloat("metallic", metallic)
        pbrShader.setFloat("roughness", roughness)
        pbrShader.setVec3("ambient", Vec3(0.1, 0.1, 0.1))
        pbrShader.setMat4("model", model)

        for (i in 0 until LightPositions.size) {
            pbrShader.setVec3("lightPositions[$i]", LightPositions[i])
            pbrShader.setVec3("lightColors[$i]", LightColors[i])
        }
        irradianceTexture.active(pbrShader)
        radianceTexture.active(pbrShader)

//        envBRDFLookUpTexture.active(pbrShader)
    }

    private fun setUpPBRShaderWithTextures(projection: Mat4, view: Mat4, model: Mat4,
                                           albedoMapTexId: Int, normalMapTexId: Int,
                                           metallicMapTexId: Int, roughnessMapTexId: Int,
                                           aoMapTexId: Int) {
        pbrShader.enable()
        if (albedoMapTexId > 0) {
            activeTexture(albedoMapTexId, albedoMapSlot)
        }
        if (normalMapTexId > 0) {
            activeTexture(normalMapTexId, normalMapSlot)
        }
        if (roughnessMapTexId > 0) {
            activeTexture(roughnessMapTexId, roughnessMapSlot)
        }
        if (metallicMapTexId > 0) {
            activeTexture(metallicMapTexId, metallicMapSlot)
        }
        if (aoMapTexId > 0) {
            activeTexture(aoMapTexId, aoMapSlot)
        }

        pbrShader.setInt("albedoMap", albedoMapSlot)
        pbrShader.setInt("normalMap", normalMapSlot)
        pbrShader.setInt("metallicMap", metallicMapSlot)
        pbrShader.setInt("roughnessMap", roughnessMapSlot)
        pbrShader.setInt("aoMap", aoMapSlot)
        pbrShader.setMat4("projection", projection)
        pbrShader.setMat4("view", view)
        pbrShader.setVec3("camPos", camera.position)
        pbrShader.setFloat("metallic", metallic)
        pbrShader.setFloat("roughness", roughness)
        pbrShader.setVec3("ambient", Vec3(0.1, 0.1, 0.1))
        pbrShader.setMat4("model", model)

        for (i in 0 until LightPositions.size) {
            pbrShader.setVec3("lightPositions[$i]", LightPositions[i])
            pbrShader.setVec3("lightColors[$i]", LightColors[i])
        }
        irradianceTexture.active(pbrShader)
        radianceTexture.active(pbrShader)
//        envBRDFLookUpTexture.active(pbrShader)

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
    }

}