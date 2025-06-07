package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
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

        vec3 calculatePointLight(vec3 lightPos, vec3 lightColor, float intensity, vec3 fragPos, vec3 normal) {
            vec3 lightDir = lightPos - fragPos;
            float distance = length(lightDir);
            lightDir = normalize(lightDir);

            // Less aggressive attenuation for better light reach
            float attenuation = intensity / (1.0 + 0.01 * distance + 0.001 * distance * distance);

            float diffuse = max(dot(normal, lightDir), u_minLightLevel);

            return lightColor * diffuse * attenuation;
        }

        vec3 calculateDirectionalLight(vec3 lightDir, vec3 lightColor, vec3 normal) {
            lightDir = normalize(-lightDir);
            float diffuse = max(dot(normal, lightDir), u_minLightLevel);
            return lightColor * diffuse;
        }

        void main() {
            vec4 texColor = texture2D(u_diffuseTexture, v_texCoords);

            vec3 finalColor = u_ambientLight;

            for (int i = 0; i < MAX_DIRECTIONAL_LIGHTS; i++) {
                if (i >= u_numDirLights) break;
                finalColor += calculateDirectionalLight(u_dirLights[i], u_dirLightColors[i], v_normal);
            }

            for (int i = 0; i < MAX_POINT_LIGHTS; i++) {
                if (i >= u_numPointLights) break;
                finalColor += calculatePointLight(
                    u_pointLightPositions[i],
                    u_pointLightColors[i],
                    u_pointLightIntensities[i],
                    v_worldPos,
                    v_normal
                );
            }

            // Ensure minimum brightness
            finalColor = max(finalColor, vec3(0.1));

            gl_FragColor = vec4(texColor.rgb * finalColor, texColor.a);
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
        val diffuseTexture = renderable.material.get(TextureAttribute.Diffuse) as? TextureAttribute
        diffuseTexture?.textureDescription?.texture?.bind(0)
        set(u_diffuseTexture, 0)
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

        val numPointLights = pointLightsArray.size.coerceAtMost(16) // New limit
        set(u_numPointLights, numPointLights)

        for (i in 0 until 16) {
            if (i < numPointLights) {
                val light = pointLightsArray[i]
                set(u_pointLightPositions[i], light.position)
                set(u_pointLightColors[i], light.color.r, light.color.g, light.color.b)
                set(u_pointLightIntensities[i], light.intensity)
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
