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

class BillboardShader : BaseShader() {
    private val VERTEX_SHADER = """
        #ifdef GL_ES
        #define LOWP lowp
        #define MED mediump
        #define HIGH highp
        precision mediump float;
        #else
        #define MED
        #define LOWP
        #define HIGH
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

            mat3 normalCalcMatrix = mat3(
                u_worldTrans[0].xyz,
                u_worldTrans[1].xyz,
                u_worldTrans[2].xyz
            );
            v_normal = normalize(normalCalcMatrix * a_normal);

            v_viewDir = normalize(u_cameraPosition - v_worldPos);
            v_texCoords = a_texCoord0;

            gl_Position = u_projViewTrans * worldPos;
        }
    """.trimIndent()

    private val FRAGMENT_SHADER = """
        // Enhanced fragment shader for more cartoony/glowing effects
    #ifdef GL_ES
    #define LOWP lowp
    #define MED mediump
    #define HIGH highp
    precision mediump float;
    #else
    #define MED
    #define LOWP
    #define HIGH
    #endif

    #define MAX_POINT_LIGHTS 8
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
    uniform float u_billboardLightingStrength;
    uniform float u_minLightLevel;

    // New cartoony parameters
    uniform float u_cartoonySaturation; // How saturated/warm the lights should be
    uniform float u_glowIntensity;      // How much glow effect
    uniform float u_lightFalloffPower;  // Controls how sharp/soft light falloff is

    vec3 calculatePointLight(vec3 lightPos, vec3 lightColor, float intensity, vec3 fragPos, vec3 normal) {
        vec3 lightDir = lightPos - fragPos;
        float distance = length(lightDir);
        lightDir = normalize(lightDir);

        // More cartoony attenuation - less realistic, more dramatic
        float attenuation = intensity / (1.0 + 0.05 * distance + 0.01 * distance * distance);

        // Add exponential falloff for more dramatic lighting
        attenuation = pow(attenuation, u_lightFalloffPower);

        float normalDot = dot(normal, lightDir);
        float diffuseStandard = max(normalDot, 0.0);
        float billboardDiffuse = max(abs(normalDot), 0.3);

        float diffuse = mix(diffuseStandard, billboardDiffuse, u_billboardLightingStrength);
        diffuse = max(diffuse, u_minLightLevel);

        // Enhance the light color for more cartoony effect
        vec3 enhancedColor = lightColor * u_cartoonySaturation;

        // Add glow effect - lights contribute more when close
        float glowFactor = 1.0 + (u_glowIntensity / (1.0 + distance * 0.1));

        return enhancedColor * diffuse * attenuation * glowFactor;
    }

    vec3 calculateDirectionalLight(vec3 lightDir, vec3 lightColor, vec3 normal) {
        lightDir = normalize(-lightDir);

        float normalDot = dot(normal, lightDir);
        float diffuseStandard = max(normalDot, 0.0);
        float billboardDiffuse = max(abs(normalDot), 0.3);

        float diffuse = mix(diffuseStandard, billboardDiffuse, u_billboardLightingStrength);
        diffuse = max(diffuse, u_minLightLevel);

        // Make directional lights warmer and more saturated too
        vec3 enhancedColor = lightColor * u_cartoonySaturation;

        return enhancedColor * diffuse;
    }

    void main() {
        vec4 texColor = texture2D(u_diffuseTexture, v_texCoords);

        if (texColor.a < 0.1) {
            discard;
        }

        // Start with warmer, more saturated ambient light
        vec3 finalColor = u_ambientLight * 1.2;

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

        // Apply tone mapping for more cartoony look
        finalColor = finalColor / (finalColor + vec3(1.0));
        finalColor = pow(finalColor, vec3(0.8)); // Gamma correction for brighter look

        gl_FragColor = vec4(texColor.rgb * finalColor, texColor.a);
    }
    """.trimIndent()


    // This field will store the compiled shader program.
    // BaseShader also has a 'protected ShaderProgram program', which super.init() will use.
    private lateinit var compiledProgram: ShaderProgram

    // Uniform locations (registered when the class instance is created)
    private val u_worldTrans = register("u_worldTrans")
    private val u_projViewTrans = register("u_projViewTrans")
    private val u_cameraPosition = register("u_cameraPosition")
    private val u_diffuseTexture = register("u_diffuseTexture")
    private val u_ambientLight = register("u_ambientLight")
    private val u_numPointLights = register("u_numPointLights")
    private val u_numDirLights = register("u_numDirLights")
    private val u_billboardLightingStrength = register("u_billboardLightingStrength")
    private val u_minLightLevel = register("u_minLightLevel")

    // Point light uniforms arrays
    private val u_pointLightPositions = Array<Int>()
    private val u_pointLightColors = Array<Int>()
    private val u_pointLightIntensities = Array<Int>()

    // Directional light uniforms arrays
    private val u_dirLights = Array<Int>()
    private val u_dirLightColors = Array<Int>()

    // Configuration
    var billboardLightingStrength = 0.8f
    var minLightLevel = 0.2f
    private var currentEnvironment: Environment? = null
    private val u_cartoonySaturation = register("u_cartoonySaturation")
    private val u_glowIntensity = register("u_glowIntensity")
    private val u_lightFalloffPower = register("u_lightFalloffPower")

    // Add these configuration properties
    var cartoonySaturation = 1.5f  // Higher = more saturated/warm colors
    var glowIntensity = 2.0f       // Higher = more glow around lights
    var lightFalloffPower = 0.6f   // Lower = softer falloff, Higher = sharper falloff

    override fun init() {
        // Create and compile the ShaderProgram
        val program = ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (!program.isCompiled) {
            throw GdxRuntimeException("Shader compilation failed:\n${program.log}")
        }
        this.compiledProgram = program // Store it if you need to access it directly in this class

        // Register array uniforms. These calls add names to BaseShader's internal list.
        // This should happen *before* super.init() so BaseShader knows about all uniforms.
        for (i in 0 until 8) { // MAX_POINT_LIGHTS
            u_pointLightPositions.add(register("u_pointLightPositions[$i]"))
            u_pointLightColors.add(register("u_pointLightColors[$i]"))
            u_pointLightIntensities.add(register("u_pointLightIntensities[$i]"))
        }
        for (i in 0 until 2) { // MAX_DIRECTIONAL_LIGHTS
            u_dirLights.add(register("u_dirLights[$i]"))
            u_dirLightColors.add(register("u_dirLightColors[$i]"))
        }

        super.init(program, null)
    }

    override fun compareTo(other: Shader): Int {
        return 0 // Or a more meaningful comparison if needed
    }

    override fun canRender(renderable: Renderable): Boolean {
        // Ensure the material has what this shader needs (e.g., a diffuse texture)
        return renderable.material.has(TextureAttribute.Diffuse)
    }

    private fun needsTransparency(): Boolean {
        // Return true only if your player texture actually has transparency
        // For most sprite-based characters, you'll want this to be true
        return true
    }

    override fun begin(camera: Camera, context: RenderContext) {
        this.compiledProgram.bind()

        // IMPORTANT: Ensure proper depth testing is enabled
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)  // Less than or equal for proper depth testing
        Gdx.gl.glDepthMask(true)            // Enable depth writing

        // Configure blending properly - only enable if you need transparency
        // For opaque billboard objects, you might want to disable blending entirely
        if (needsTransparency()) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        } else {
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }

        // Existing uniforms
        set(u_projViewTrans, camera.combined)
        set(u_cameraPosition, camera.position)
        set(u_billboardLightingStrength, billboardLightingStrength)
        set(u_minLightLevel, minLightLevel)

        // Add the new cartoony uniforms
        set(u_cartoonySaturation, cartoonySaturation)
        set(u_glowIntensity, glowIntensity)
        set(u_lightFalloffPower, lightFalloffPower)

        currentEnvironment?.let { applyEnvironment(it) }
    }

    override fun render(renderable: Renderable) {
        // Set renderable-specific uniforms
        set(u_worldTrans, renderable.worldTransform)

        // Set texture
        val diffuseTexture = renderable.material.get(TextureAttribute.Diffuse) as? TextureAttribute
        diffuseTexture?.textureDescription?.texture?.bind(0) // Bind to texture unit 0
        set(u_diffuseTexture, 0) // Tell the shader sampler to use texture unit 0

        // Render the mesh part using the compiled program
        renderable.meshPart.render(this.compiledProgram) // Or use 'super.program'
    }

    override fun end() {
        // Restore default GL state
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
    }

    override fun dispose() {
        compiledProgram.dispose() // Dispose the shader program you created
        super.dispose() // Call super, though BaseShader.dispose() is empty by default
    }

    fun setEnvironment(environment: Environment) {
        currentEnvironment = environment
    }

    private fun applyEnvironment(environment: Environment) {
        val ambientLight = environment.get(ColorAttribute.AmbientLight) as? ColorAttribute
        if (ambientLight != null) {
            set(u_ambientLight, ambientLight.color.r, ambientLight.color.g, ambientLight.color.b)
        } else {
            set(u_ambientLight, 0.2f, 0.2f, 0.2f)
        }

        val pointLightsArray = com.badlogic.gdx.utils.Array<PointLight>()
        environment.forEach { attribute ->
            if (attribute is PointLight) {
                pointLightsArray.add(attribute)
            }
        }
        set(u_numPointLights, pointLightsArray.size.coerceAtMost(8))
        for (i in 0 until 8) {
            if (i < pointLightsArray.size) {
                val light = pointLightsArray[i]
                set(u_pointLightPositions[i], light.position)
                set(u_pointLightColors[i], light.color.r, light.color.g, light.color.b)
                set(u_pointLightIntensities[i], light.intensity)
            } else {
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

// Shader provider for billboard objects
class BillboardShaderProvider : com.badlogic.gdx.graphics.g3d.utils.ShaderProvider {
    private val billboardShader = BillboardShader()
    private var initialized = false

    override fun getShader(renderable: Renderable): Shader {
        if (!initialized) {
            billboardShader.init() // This now correctly calls super.init()
            initialized = true
        }
        return billboardShader
    }

    override fun dispose() {
        billboardShader.dispose()
    }

    fun setBillboardLightingStrength(strength: Float) {
        billboardShader.billboardLightingStrength = strength
    }

    fun setMinLightLevel(level: Float) {
        billboardShader.minLightLevel = level
    }

    fun setEnvironment(environment: Environment) {
        billboardShader.setEnvironment(environment)
    }

    fun setCartoonySaturation(saturation: Float) {
        billboardShader.cartoonySaturation = saturation
    }

    fun setGlowIntensity(intensity: Float) {
        billboardShader.glowIntensity = intensity
    }

    fun setLightFalloffPower(power: Float) {
        billboardShader.lightFalloffPower = power
    }
}
