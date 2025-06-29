package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.math.Vector2

enum class ShaderEffect(val displayName: String) {
    NONE("Default"),
    BRIGHT_VIBRANT("Bright & Vibrant"),
    RETRO_PIXEL("Retro Pixel"),
    DREAMY_SOFT("Dreamy Soft"),
    NEON_GLOW("Neon Glow"),
    OLD_MOVIE("1920s Cartoon"),
    BLACK_WHITE("Schwarz-Weiß"),
    FILM_NOIR("Film Noir")
}

class ShaderEffectManager {
    private var currentEffect = ShaderEffect.NONE
    private lateinit var frameBuffer: FrameBuffer
    private lateinit var postProcessBatch: SpriteBatch
    private lateinit var postProcessCamera: OrthographicCamera
    private lateinit var fullScreenQuad: Mesh

    // Shaders for different effects
    private val shaders = mutableMapOf<ShaderEffect, ShaderProgram>()
    var isEffectsEnabled = true
        private set

    // Shader source code
    private val vertexShader = """
        attribute vec4 a_position;
        attribute vec2 a_texCoord0;
        varying vec2 v_texCoord;

        void main() {
            v_texCoord = a_texCoord0;
            gl_Position = a_position;
        }
    """

    // Fragment shaders for different effects
    private val brightVibrantShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        uniform float u_time;
        varying vec2 v_texCoord;

        void main() {
            vec4 color = texture2D(u_texture, v_texCoord);

            // Increase brightness and saturation
            color.rgb = pow(color.rgb, vec3(0.8)); // Gamma correction for brightness

            // Boost saturation
            float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            color.rgb = mix(vec3(luminance), color.rgb, 1.5);

            // Add slight warm tint
            color.r *= 1.1;
            color.g *= 1.05;

            // Subtle brightness oscillation
            color.rgb *= 1.0 + 0.1 * sin(u_time * 2.0) * 0.5;

            gl_FragColor = vec4(color.rgb, color.a);
        }
    """

    private val retroPixelShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        uniform vec2 u_resolution;
        uniform float u_time;
        varying vec2 v_texCoord;

        void main() {
            // Pixelation effect
            float pixelSize = 4.0;
            vec2 pixelCoord = floor(v_texCoord * u_resolution / pixelSize) * pixelSize / u_resolution;
            vec4 color = texture2D(u_texture, pixelCoord);

            // Retro color palette reduction
            color.r = floor(color.r * 8.0) / 8.0;
            color.g = floor(color.g * 8.0) / 8.0;
            color.b = floor(color.b * 8.0) / 8.0;

            // Add scanlines
            float scanline = sin(v_texCoord.y * u_resolution.y * 2.0) * 0.1;
            color.rgb -= scanline;

            // Slight CRT curve effect (simplified)
            vec2 center = v_texCoord - 0.5;
            float vignette = 1.0 - dot(center, center) * 0.5;
            color.rgb *= vignette;

            gl_FragColor = color;
        }
    """

    private val dreamySoftShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        uniform vec2 u_resolution;
        uniform float u_time;
        varying vec2 v_texCoord;

        void main() {
            vec4 color = vec4(0.0);
            vec2 offset = 1.0 / u_resolution;

            // Gaussian blur approximation
            for(int x = -2; x <= 2; x++) {
                for(int y = -2; y <= 2; y++) {
                    vec2 sampleCoord = v_texCoord + vec2(float(x), float(y)) * offset * 1.5;
                    float weight = 1.0 / (1.0 + float(x*x + y*y));
                    color += texture2D(u_texture, sampleCoord) * weight;
                }
            }
            color /= 9.0;

            // Soft, pastel color adjustment
            color.rgb = pow(color.rgb, vec3(1.2)); // Softer contrast

            // Add dreamy color tint
            color.r *= 1.1;
            color.b *= 1.2;
            color.g *= 1.05;

            // Gentle brightness pulsing
            float pulse = 0.95 + 0.05 * sin(u_time * 1.5);
            color.rgb *= pulse;

            // Soft vignette
            vec2 center = v_texCoord - 0.5;
            float vignette = 1.0 - dot(center, center) * 0.3;
            color.rgb *= vignette;

            gl_FragColor = color;
        }
    """

    private val neonGlowShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        uniform vec2 u_resolution;
        uniform float u_time;
        varying vec2 v_texCoord;

        void main() {
            vec4 originalColor = texture2D(u_texture, v_texCoord);
            vec4 glowColor = vec4(0.0);
            vec2 offset = 1.0 / u_resolution;

            // Create glow effect by sampling around the pixel
            for(int x = -3; x <= 3; x++) {
                for(int y = -3; y <= 3; y++) {
                    vec2 sampleCoord = v_texCoord + vec2(float(x), float(y)) * offset * 2.0;
                    vec4 sampleColor = texture2D(u_texture, sampleCoord);
                    float distance = length(vec2(float(x), float(y)));
                    float weight = 1.0 / (1.0 + distance * 0.5);
                    glowColor += sampleColor * weight;
                }
            }
            glowColor /= 25.0;

            // Edge detection for enhanced neon effect
            vec4 edgeColor = vec4(0.0);
            edgeColor += texture2D(u_texture, v_texCoord + vec2(-offset.x, 0.0));
            edgeColor += texture2D(u_texture, v_texCoord + vec2(offset.x, 0.0));
            edgeColor += texture2D(u_texture, v_texCoord + vec2(0.0, -offset.y));
            edgeColor += texture2D(u_texture, v_texCoord + vec2(0.0, offset.y));
            edgeColor = abs(edgeColor - originalColor * 4.0);

            // Neon color enhancement
            vec4 neonColor = originalColor;
            neonColor.r *= 1.3 + 0.3 * sin(u_time * 3.0);
            neonColor.g *= 1.2 + 0.2 * sin(u_time * 3.5 + 1.0);
            neonColor.b *= 1.4 + 0.4 * sin(u_time * 2.5 + 2.0);

            // Combine effects
            vec4 finalColor = neonColor + glowColor * 0.5 + edgeColor * 2.0;

            // Dark background enhancement for neon effect
            float luminance = dot(originalColor.rgb, vec3(0.299, 0.587, 0.114));
            if(luminance < 0.3) {
                finalColor.rgb *= 0.3; // Darken dark areas
            }

            gl_FragColor = vec4(finalColor.rgb, originalColor.a);
        }
    """

    private val passthroughShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        varying vec2 v_texCoord;

        void main() {
            gl_FragColor = texture2D(u_texture, v_texCoord);
        }
    """

    private val blackWhiteShader = """
    #ifdef GL_ES
    precision mediump float;
    #endif

    uniform sampler2D u_texture;
    uniform float u_time;
    varying vec2 v_texCoord;

    void main() {
        vec4 color = texture2D(u_texture, v_texCoord);

        // Convert to grayscale using luminance formula
        float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));

        // Apply high contrast for that classic cartoon look
        gray = smoothstep(0.4, 0.6, gray);

        // Add slight film grain effect
        float noise = fract(sin(dot(v_texCoord, vec2(12.9898, 78.233))) * 43758.5453);
        gray += (noise - 0.5) * 0.1;

        // Optional: Add subtle vignette for old film look
        vec2 center = v_texCoord - 0.5;
        float vignette = 1.0 - dot(center, center) * 0.8;
        gray *= vignette;

        // Clamp to ensure we stay in valid range
        gray = clamp(gray, 0.0, 1.0);

        gl_FragColor = vec4(vec3(gray), color.a);
    }
"""

    private val oldmovieShader = """
    #ifdef GL_ES
    precision mediump float;
    #endif

    uniform sampler2D u_texture;
    varying vec2 v_texCoord;

    void main() {
        vec4 color = texture2D(u_texture, v_texCoord);

        // Simple grayscale conversion using luminance formula
        float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));

        gl_FragColor = vec4(vec3(gray), color.a);
    }
"""

    private val filmNoirShader = """
    #ifdef GL_ES
    precision mediump float;
    #endif

    uniform sampler2D u_texture;
    uniform float u_time;
    uniform vec2 u_resolution;
    varying vec2 v_texCoord;

    void main() {
        vec4 color = texture2D(u_texture, v_texCoord);

        // Calculate the base luminance (how bright the pixel is in grayscale)
        float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));

        // Detect colored lighting by finding the difference between the
        // original color and its grayscale equivalent.
        // A colorful pixel will have a large deviation. A gray pixel will have zero.
        vec3 colorDeviation = color.rgb - vec3(luminance);

        // Amplify the colored lighting effect to make it pop.
        // You can change '3.0' to make the color more or less intense.
        vec3 coloredLighting = colorDeviation * 3.0;

        // Create a high-contrast grayscale base using smoothstep.
        // This crushes the blacks and blows out the whites for a classic noir look.
        float contrastGray = smoothstep(0.25, 0.75, luminance);

        // Combine the high-contrast grayscale base with the colored lighting.
        vec3 finalColor = vec3(contrastGray) + coloredLighting;

        // --- Optional but highly recommended "Old Film" effects ---

        // Add film grain that jitters over time
        float grain = fract(sin(dot(v_texCoord + u_time * 0.001, vec2(12.9898, 78.233))) * 43758.5453);
        finalColor += (grain - 0.5) * 0.15; // Adjust 0.15 to change grain intensity

        // Add faint scanlines
        float scanline = sin(v_texCoord.y * u_resolution.y * 1.5) * 0.04;
        finalColor -= scanline;

        // Add a strong vignette to darken the corners
        vec2 center = v_texCoord - 0.5;
        float vignette = 1.0 - dot(center, center) * 1.2;
        vignette = smoothstep(0.0, 1.0, vignette);
        finalColor *= vignette;

        // Clamp the final result to keep it in the valid 0.0-1.0 range
        finalColor = clamp(finalColor, 0.0, 1.0);

        gl_FragColor = vec4(finalColor, color.a);
    }
"""

    fun initialize() {
        // Create frame buffer for post-processing
        frameBuffer = FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.width, Gdx.graphics.height, true)

        // Create sprite batch for post-processing
        postProcessBatch = SpriteBatch()
        postProcessCamera = OrthographicCamera(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        postProcessCamera.setToOrtho(false)

        // Create full-screen quad
        createFullScreenQuad()

        // Compile shaders
        compileShaders()

        println("ShaderEffectManager initialized with ${shaders.size} effects")
    }

    private fun createFullScreenQuad() {
        val vertices = floatArrayOf(
            -1f, -1f, 0f, 0f,  // Bottom-left
            1f, -1f, 1f, 0f,  // Bottom-right
            1f,  1f, 1f, 1f,  // Top-right
            -1f,  1f, 0f, 1f   // Top-left
        )

        val indices = shortArrayOf(0, 1, 2, 2, 3, 0)

        fullScreenQuad = Mesh(true, 4, 6,
            VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
            VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0")
        )

        fullScreenQuad.setVertices(vertices)
        fullScreenQuad.setIndices(indices)
    }

    private fun compileShaders() {
        // Default passthrough shader
        shaders[ShaderEffect.NONE] = createShader(vertexShader, passthroughShader)

        // Effect shaders
        shaders[ShaderEffect.BRIGHT_VIBRANT] = createShader(vertexShader, brightVibrantShader)
        shaders[ShaderEffect.RETRO_PIXEL] = createShader(vertexShader, retroPixelShader)
        shaders[ShaderEffect.DREAMY_SOFT] = createShader(vertexShader, dreamySoftShader)
        shaders[ShaderEffect.NEON_GLOW] = createShader(vertexShader, neonGlowShader)
        shaders[ShaderEffect.BLACK_WHITE] = createShader(vertexShader, blackWhiteShader)
        shaders[ShaderEffect.OLD_MOVIE] = createShader(vertexShader, oldmovieShader)
        shaders[ShaderEffect.FILM_NOIR] = createShader(vertexShader, filmNoirShader)
    }

    private fun createShader(vertex: String, fragment: String): ShaderProgram {
        ShaderProgram.pedantic = false
        val shader = ShaderProgram(vertex, fragment)
        if (!shader.isCompiled) {
            println("Shader compilation error: ${shader.log}")
        } else {
            println("Shader compiled successfully")
        }
        return shader
    }

    fun beginCapture() {
        frameBuffer.begin()
    }

    fun endCaptureAndRender() {
        frameBuffer.end()

        // Apply post-processing effect
        applyPostProcessing()
    }

    private fun applyPostProcessing() {
        val activeEffect = if (isEffectsEnabled) currentEffect else ShaderEffect.NONE
        val shader = shaders[activeEffect] ?: return

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)

        shader.bind()
        shader.setUniformi("u_texture", 0)
        shader.setUniformf("u_time", System.currentTimeMillis() / 1000f)
        shader.setUniformf("u_resolution", Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

        frameBuffer.colorBufferTexture.bind(0)

        fullScreenQuad.render(shader, GL20.GL_TRIANGLES)

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
    }

    fun toggleEffectsEnabled() {
        isEffectsEnabled = !isEffectsEnabled
        println("Shader effects are now ${if (isEffectsEnabled) "ENABLED" else "DISABLED"}")
    }

    private fun setEffect(effect: ShaderEffect) {
        currentEffect = effect
        println("Shader effect changed to: ${effect.displayName}")
    }

    fun getCurrentEffect(): ShaderEffect = currentEffect

    fun nextEffect() {
        val effects = ShaderEffect.entries.toTypedArray()
        val currentIndex = effects.indexOf(currentEffect)
        val nextIndex = (currentIndex + 1) % effects.size
        setEffect(effects[nextIndex])
    }

    fun previousEffect() {
        val effects = ShaderEffect.entries.toTypedArray()
        val currentIndex = effects.indexOf(currentEffect)
        val prevIndex = if (currentIndex == 0) effects.size - 1 else currentIndex - 1
        setEffect(effects[prevIndex])
    }

    fun resize(width: Int, height: Int) {
        frameBuffer.dispose()
        frameBuffer = FrameBuffer(Pixmap.Format.RGBA8888, width, height, true)

        postProcessCamera.setToOrtho(false, width.toFloat(), height.toFloat())
        postProcessCamera.update()
    }

    fun dispose() {
        frameBuffer.dispose()
        postProcessBatch.dispose()
        fullScreenQuad.dispose()

        for (shader in shaders.values) {
            shader.dispose()
        }
        shaders.clear()
    }
}
