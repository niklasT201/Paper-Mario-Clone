package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.GdxRuntimeException

class BlockShader : BaseShader() {
    private val VERTEX_SHADER = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        attribute vec3 a_position;
        attribute vec3 a_normal;
        attribute vec2 a_texCoord0;

        uniform mat4 u_worldTrans;
        uniform mat4 u_projViewTrans;
        uniform vec3 u_cameraPosition;

        varying vec2 v_texCoords;
        varying vec3 v_worldPos;
        varying vec3 v_normal;
        varying vec3 v_viewDir;

        void main() {
            vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);
            v_worldPos = worldPos.xyz;

            mat3 normalMatrix = mat3(
                u_worldTrans[0].xyz,
                u_worldTrans[1].xyz,
                u_worldTrans[2].xyz
            );
            v_normal = normalize(normalMatrix * a_normal);

            v_viewDir = normalize(u_cameraPosition - v_worldPos);
            v_texCoords = a_texCoord0;

            gl_Position = u_projViewTrans * worldPos;
        }
    """.trimIndent()

    private val FRAGMENT_SHADER = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        #define MAX_POINT_LIGHTS 128
        #define MAX_DIRECTIONAL_LIGHTS 2

        varying vec2 v_texCoords;
        varying vec3 v_worldPos;
        varying vec3 v_normal;
        varying vec3 v_viewDir;

        uniform sampler2D u_diffuseTexture;
        uniform vec3 u_ambientLight;
        uniform vec3 u_dirLights[MAX_DIRECTIONAL_LIGHTS];
        uniform vec3 u_dirLightColors[MAX_DIRECTIONAL_LIGHTS];
        uniform vec3 u_pointLightPositions[MAX_POINT_LIGHTS];
        uniform vec3 u_pointLightColors[MAX_POINT_LIGHTS];
        uniform float u_pointLightIntensities[MAX_POINT_LIGHTS];
        uniform int u_numPointLights;
        uniform int u_numDirLights;
        uniform float u_cartoonySaturation;
        uniform float u_glowIntensity;
        uniform float u_lightFalloffPower;
        uniform float u_minLightLevel;
        uniform vec4 u_materialDiffuseColor; // For ColorAttribute.Diffuse
        uniform bool u_hasDiffuseTexture;

        vec3 calculatePointLight(vec3 lightPos, vec3 lightColor, float lightRange, vec3 fragPos, vec3 normal) { // Renamed intensity to lightRange for clarity
            vec3 lightDir = lightPos - fragPos;
            float distance = length(lightDir);
            lightDir = normalize(lightDir);

            // Attenuation: smooth fade from 1 to 0 as distance goes from (lightRange * 0.2) to lightRange
            // Adjust the 0.2 factor to control how quickly it starts fading within its range.
            float attenuation = smoothstep(lightRange, lightRange * 0.2, distance);
                                        // smoothstep(edge0, edge1, x): 0 if x <= edge0, 1 if x >= edge1
                                        // So, for attenuation (light falls off): smoothstep(far_edge, near_edge, distance)
                                        // Light is full (attenuation=1) until distance > lightRange*0.2, then fades to 0 at lightRange.

            float diffuse = max(dot(normal, lightDir), u_minLightLevel); // u_minLightLevel is 0.1 from BlockShader.java

            return lightColor * diffuse * attenuation;
        }

        vec3 calculateDirectionalLight(vec3 lightDir, vec3 lightColor, vec3 normal) {
            lightDir = normalize(-lightDir);
            float diffuse = max(dot(normal, lightDir), u_minLightLevel);
            return lightColor * diffuse;
        }

        void main() {
            vec4 baseColor;
            if (u_hasDiffuseTexture) {
                baseColor = texture2D(u_diffuseTexture, v_texCoords);
            } else {
                baseColor = u_materialDiffuseColor; // Use material's diffuse color
            }

            // Alpha test for transparent parts of textures (like your cross-objects)
            // Your current cross-objects use blending, so this might not be strictly needed if blending is set up right
            // but can be good for cutouts.
            if (baseColor.a < 0.05) { // Adjusted threshold
                 discard;
            }

            vec3 totalLightContribution = u_ambientLight;

            for (int i = 0; i < MAX_DIRECTIONAL_LIGHTS; i++) {
                if (i >= u_numDirLights) break;
                totalLightContribution += calculateDirectionalLight(u_dirLights[i], u_dirLightColors[i], v_normal);
            }

            for (int i = 0; i < MAX_POINT_LIGHTS; i++) {
                if (i >= u_numPointLights) break;
                totalLightContribution += calculatePointLight(
                    u_pointLightPositions[i],
                    u_pointLightColors[i],
                    u_pointLightIntensities[i], // This is lightRange if using Option 1A from Step 1
                    v_worldPos,
                    v_normal
                );
            }

            totalLightContribution = max(totalLightContribution, vec3(0.1)); // Ensure minimum light contribution

            gl_FragColor = vec4(baseColor.rgb * totalLightContribution, baseColor.a);
        }
    """.trimIndent()

    // Same structure as BillboardShader but simplified
    private val u_worldTrans = register("u_worldTrans")
    private val u_projViewTrans = register("u_projViewTrans")
    private val u_cameraPosition = register("u_cameraPosition")
    private val u_diffuseTexture = register("u_diffuseTexture")
    private val u_ambientLight = register("u_ambientLight")
    private val u_numPointLights = register("u_numPointLights")
    private val u_numDirLights = register("u_numDirLights")
    private val u_cartoonySaturation = register("u_cartoonySaturation")
    private val u_glowIntensity = register("u_glowIntensity")
    private val u_lightFalloffPower = register("u_lightFalloffPower")
    private val u_minLightLevel = register("u_minLightLevel")
    private val u_materialDiffuseColor = register("u_materialDiffuseColor")
    private val u_hasDiffuseTexture = register("u_hasDiffuseTexture")

    private val u_pointLightPositions = Array<Int>()
    private val u_pointLightColors = Array<Int>()
    private val u_pointLightIntensities = Array<Int>()
    private val u_dirLights = Array<Int>()
    private val u_dirLightColors = Array<Int>()

    var cartoonySaturation = 1.2f
    var glowIntensity = 1.0f
    var lightFalloffPower = 1.0f
    var minLightLevel = 0.1f
    private var currentEnvironment: Environment? = null

    override fun init() {
        val program = ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (!program.isCompiled) {
            throw GdxRuntimeException("Block shader compilation failed:\n${program.log}")
        }

        for (i in 0 until 128) {
            u_pointLightPositions.add(register("u_pointLightPositions[$i]"))
            u_pointLightColors.add(register("u_pointLightColors[$i]"))
            u_pointLightIntensities.add(register("u_pointLightIntensities[$i]"))
        }
        for (i in 0 until 2) {
            u_dirLights.add(register("u_dirLights[$i]"))
            u_dirLightColors.add(register("u_dirLightColors[$i]"))
        }

        super.init(program, null)
    }

    override fun compareTo(other: Shader): Int = 0
    override fun canRender(renderable: Renderable): Boolean =
        renderable.material.has(TextureAttribute.Diffuse)

    override fun begin(camera: Camera, context: RenderContext) {
        program.bind()
        set(u_projViewTrans, camera.combined)
        set(u_cameraPosition, camera.position)
        set(u_cartoonySaturation, cartoonySaturation)
        set(u_glowIntensity, glowIntensity)
        set(u_lightFalloffPower, lightFalloffPower)
        set(u_minLightLevel, minLightLevel)
        currentEnvironment?.let { applyEnvironment(it) }

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glCullFace(GL20.GL_BACK)
    }

    override fun render(renderable: Renderable) {
        set(u_worldTrans, renderable.worldTransform)

        // Handle Texture or Diffuse Color
        val diffuseTextureAttr = renderable.material.get(TextureAttribute.Diffuse) as? TextureAttribute
        val texture = diffuseTextureAttr?.textureDescription?.texture

        if (texture != null) {
            texture.bind(0)
            set(u_diffuseTexture, 0)
            set(u_hasDiffuseTexture, 1) // 1 for true
        } else {
            set(u_hasDiffuseTexture, 0) // 0 for false
            val diffuseColorAttr = renderable.material.get(ColorAttribute.Diffuse) as? ColorAttribute
            if (diffuseColorAttr != null) {
                set(u_materialDiffuseColor, diffuseColorAttr.color)
            } else {
                set(u_materialDiffuseColor, 1f, 1f, 1f, 1f) // Default white
            }
        }

        // Handle Blending Attribute
        val blendingAttr = renderable.material.get(BlendingAttribute.Type) as? BlendingAttribute
        if (blendingAttr != null && blendingAttr.blended) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(blendingAttr.sourceFunction, blendingAttr.destFunction)
        } else {
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }

        // Handle CullFace Attribute
        val cullFaceAttr = renderable.material.get(IntAttribute.CullFace) as? IntAttribute
        if (cullFaceAttr != null) {
            if (cullFaceAttr.value == GL20.GL_NONE) {
                Gdx.gl.glDisable(GL20.GL_CULL_FACE)
            } else {
                Gdx.gl.glEnable(GL20.GL_CULL_FACE)
                Gdx.gl.glCullFace(cullFaceAttr.value)
            }
        } else {
            Gdx.gl.glEnable(GL20.GL_CULL_FACE)
            Gdx.gl.glCullFace(GL20.GL_BACK)
        }

        renderable.meshPart.render(program)
    }

    override fun end() {
        // Restore default state if needed
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDepthMask(true)
    }

    fun setEnvironment(environment: Environment) {
        currentEnvironment = environment
    }

    private fun applyEnvironment(environment: Environment) {
        // Same environment application as BillboardShader
        val ambientLight = environment.get(ColorAttribute.AmbientLight) as? ColorAttribute
        if (ambientLight != null) {
            val r = Math.max(ambientLight.color.r, 0.1f)
            val g = Math.max(ambientLight.color.g, 0.1f)
            val b = Math.max(ambientLight.color.b, 0.1f)
            set(u_ambientLight, r, g, b)
        } else {
            set(u_ambientLight, 0.3f, 0.3f, 0.3f)
        }

        val pointLightsArray = com.badlogic.gdx.utils.Array<PointLight>()
        environment.forEach { attribute ->
            if (attribute is PointLight) {
                pointLightsArray.add(attribute)
            }
        }

        val numPointLights = pointLightsArray.size.coerceAtMost(16)
        set(u_numPointLights, numPointLights)
        //println("BlockShader: NumPointLights being sent to GLSL: $numPointLights")

        for (i in 0 until 16) {
            if (i < numPointLights) {
                val light = pointLightsArray[i]
                set(u_pointLightPositions[i], light.position)
                set(u_pointLightColors[i], light.color.r, light.color.g, light.color.b)
                set(u_pointLightIntensities[i], light.intensity)

                // DEBUG: Print info for the first light
                if (i == 0) {
                    println("BlockShader: Light 0 data: Pos=${light.position}, Color=${light.color}, Range(Intensity)=${light.intensity}")
                }
            } else {
                // Zero out unused light uniforms
                set(u_pointLightPositions[i], 0f, 0f, 0f)
                set(u_pointLightColors[i], 0f, 0f, 0f)
                set(u_pointLightIntensities[i], 0f)
            }
        }

        val dirLightsArray = com.badlogic.gdx.utils.Array<DirectionalLight>()
        environment.forEach { attribute ->
            if (attribute is DirectionalLight) {
                dirLightsArray.add(attribute)
            }
        }
        set(u_numDirLights, dirLightsArray.size.coerceAtMost(2))
        for (i in 0 until 2) {
            if (i < dirLightsArray.size) {
                val light = dirLightsArray[i]
                set(u_dirLights[i], light.direction)
                set(u_dirLightColors[i], light.color.r, light.color.g, light.color.b)
            } else {
                set(u_dirLights[i], 0f, 0f, 0f)
                set(u_dirLightColors[i], 0f, 0f, 0f)
            }
        }
    }
}
