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

enum class ShaderEffect(val displayName: String) {
    NONE("Default"),
    BRIGHT_VIBRANT("Bright & Vibrant"),
    RETRO_PIXEL("Retro Pixel"),
    DREAMY_SOFT("Dreamy Soft"),
    NEON_GLOW("Neon Glow"),
    OLD_MOVIE("1920s Cartoon"),
    BLACK_WHITE("Schwarz-Weiß"),
    FILM_NOIR("Film Noir"),
    SIN_CITY("Sin City"),
    GRINDHOUSE("Grindhouse Film"),
    VINTAGE_SEPIA("Vintage Sepia"),
    COMIC_BOOK("Comic Book"),
    UNDERWATER("Underwater"),
    NIGHT_VISION("Night Vision"),
}

class ShaderEffectManager {
    private var currentEffect = ShaderEffect.NONE
    private var roomShaderOverride: ShaderEffect? = null
    private lateinit var frameBuffer: FrameBuffer
    private lateinit var postProcessBatch: SpriteBatch
    private lateinit var postProcessCamera: OrthographicCamera
    private lateinit var fullScreenQuad: Mesh

    // Shaders for different effects
    private val shaders = mutableMapOf<ShaderEffect, ShaderProgram>()
    var isEffectsEnabled = true
        private set

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
        shaders[ShaderEffect.NONE] = createShader(ShaderDefinitions.vertexShader, ShaderDefinitions.passthroughShader)

        // Effect shaders - now using the separated shader definitions
        shaders[ShaderEffect.BRIGHT_VIBRANT] = createShader(ShaderDefinitions.vertexShader, ShaderDefinitions.brightVibrantShader)
        shaders[ShaderEffect.RETRO_PIXEL] = createShader(ShaderDefinitions.vertexShader, ShaderDefinitions.retroPixelShader)
        shaders[ShaderEffect.DREAMY_SOFT] = createShader(ShaderDefinitions.vertexShader, ShaderDefinitions.dreamySoftShader)
        shaders[ShaderEffect.NEON_GLOW] = createShader(ShaderDefinitions.vertexShader, ShaderDefinitions.neonGlowShader)
        shaders[ShaderEffect.BLACK_WHITE] = createShader(ShaderDefinitions.vertexShader, ShaderDefinitions.blackWhiteShader)
        shaders[ShaderEffect.OLD_MOVIE] = createShader(ShaderDefinitions.vertexShader, ShaderDefinitions.oldmovieShader)
        shaders[ShaderEffect.FILM_NOIR] = createShader(ShaderDefinitions.vertexShader, ShaderDefinitions.filmNoirShader)
        shaders[ShaderEffect.SIN_CITY] = createShader(ShaderDefinitions.vertexShader, ShaderDefinitions.sinCityShader)
        shaders[ShaderEffect.GRINDHOUSE] = createShader(ShaderDefinitions.vertexShader, ShaderDefinitions.grindhouseShader)
        shaders[ShaderEffect.VINTAGE_SEPIA] = createShader(ShaderDefinitions.vertexShader, ShaderDefinitions.vintageSepia)
        shaders[ShaderEffect.COMIC_BOOK] = createShader(ShaderDefinitions.vertexShader, ShaderDefinitions.comicBookShader)
        shaders[ShaderEffect.UNDERWATER] = createShader(ShaderDefinitions.vertexShader, ShaderDefinitions.underwaterShader)
        shaders[ShaderEffect.NIGHT_VISION] = createShader(ShaderDefinitions.vertexShader, ShaderDefinitions.nightVisionShader)
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
        val activeEffect = if (currentEffect != ShaderEffect.NONE) {
            // If the user has made a choice, it ALWAYS wins.
            currentEffect
        } else {
            // Otherwise, use the room's override, or default to NONE if no override is set.
            roomShaderOverride ?: ShaderEffect.NONE
        }
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

    fun setRoomOverride(effect: ShaderEffect) {
        // Only set an override if the room actually has a special shader.
        if (effect != ShaderEffect.NONE) {
            roomShaderOverride = effect
        }
    }

    fun clearRoomOverride() {
        roomShaderOverride = null
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
        if (width == 0 || height == 0) {
            println("ShaderEffectManager: Ignoring resize to 0x0")
            return
        }

        // Dispose the old one before creating a new one
        if (::frameBuffer.isInitialized) {
            frameBuffer.dispose()
        }

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
