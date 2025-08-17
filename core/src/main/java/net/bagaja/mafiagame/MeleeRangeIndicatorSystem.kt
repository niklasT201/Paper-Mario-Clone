package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import kotlin.math.cos
import kotlin.math.sin

// Step 1.1: Create an enum to define the different indicator styles
enum class IndicatorStyle(val displayName: String) {
    SOLID_CIRCLE("Solid"),
    TEXTURED_RING("Textured")
}

class MeleeRangeIndicatorSystem {

    private lateinit var modelBatch: ModelBatch
    private lateinit var shaderProvider: BillboardShaderProvider

    // --- Models for different styles ---
    private var solidCircleModel: Model? = null
    private var texturedRingModel: Model? = null
    private var indicatorInstance: ModelInstance? = null

    // --- Textures ---
    private lateinit var whitePixelTexture: Texture
    private lateinit var ringTexture: Texture // For your custom texture

    // --- State Management ---
    private var isVisible = false
    private var lastRange = -1f
    private var currentStyle = IndicatorStyle.SOLID_CIRCLE // Default style
    private var isEnabledByUser = false

    fun toggle() { isEnabledByUser = !isEnabledByUser }
    fun isEnabled(): Boolean = isEnabledByUser

    companion object {
        private const val GROUND_OFFSET = 0.08f
    }

    fun initialize() {
        shaderProvider = BillboardShaderProvider().apply {
            setBillboardLightingStrength(0.9f)
            setMinLightLevel(0.4f)
        }
        modelBatch = ModelBatch(shaderProvider)

        // Create a 1x1 white pixel texture for the solid circle
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.WHITE)
        pixmap.fill()
        whitePixelTexture = Texture(pixmap)
        pixmap.dispose()

        // Step 1.2: Load your custom ring texture
        try {
            // Note: I'm using the relative path your project expects.
            ringTexture = Texture(Gdx.files.internal("gui/highlight_circle.png"))
            ringTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        } catch (e: Exception) {
            println("ERROR: Could not load 'gui/highlight_circle.png'. A fallback will be used.")
            // Create a fallback texture if yours is missing
            val fallbackPixmap = Pixmap(64, 64, Pixmap.Format.RGBA8888)
            fallbackPixmap.setColor(Color.WHITE)
            fallbackPixmap.drawCircle(32, 32, 30)
            fallbackPixmap.setColor(Color.CLEAR)
            fallbackPixmap.fillCircle(32, 32, 25)
            ringTexture = Texture(fallbackPixmap)
            fallbackPixmap.dispose()
        }
    }

    // Step 1.3: Add a public function to change the style from the UI
    fun setStyle(style: IndicatorStyle) {
        if (currentStyle == style) return
        currentStyle = style
        // Force a rebuild of the model on the next update
        lastRange = -1f
        println("Melee indicator style set to: ${style.displayName}")
    }

    fun getCurrentStyle(): IndicatorStyle = currentStyle

    /**
     * Rebuilds the appropriate model (solid circle or textured quad) when the range changes.
     */
    private fun rebuildIndicatorModel(range: Float) {
        // Dispose old models to prevent memory leaks AND set them to null
        solidCircleModel?.dispose()
        solidCircleModel = null
        texturedRingModel?.dispose()
        texturedRingModel = null

        val modelBuilder = ModelBuilder()

        when (currentStyle) {
            IndicatorStyle.SOLID_CIRCLE -> {
                // --- NEW, SAFER METHOD ---
                // Create a cylinder with a very small height to act as a flat disc.
                // This is a high-level helper and is much safer than manual part building.
                val material = Material(
                    TextureAttribute.createDiffuse(whitePixelTexture),
                    ColorAttribute.createDiffuse(1f, 1f, 1f, 0.35f),
                    BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
                )
                val segments = 32
                val attributes = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()

                solidCircleModel = modelBuilder.createCylinder(
                    range * 2, 0.01f, range * 2, // width, height, depth
                    segments,
                    material,
                    attributes
                )
                indicatorInstance = ModelInstance(solidCircleModel!!)
            }
            IndicatorStyle.TEXTURED_RING -> {
                // This method is already using a safe, high-level helper, so it remains the same.
                val size = range * 2
                val material = Material(
                    TextureAttribute.createDiffuse(ringTexture),
                    BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                    IntAttribute.createCullFace(GL20.GL_NONE) // Added this for consistency
                )
                texturedRingModel = modelBuilder.createRect(
                    -size / 2f, 0f,  size / 2f,
                    -size / 2f, 0f, -size / 2f,
                    size / 2f, 0f, -size / 2f,
                    size / 2f, 0f,  size / 2f,
                    0f, 1f, 0f,
                    material,
                    (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
                )
                indicatorInstance = ModelInstance(texturedRingModel!!)
            }
        }
        indicatorInstance?.userData = "effect"
    }

    fun update(playerSystem: PlayerSystem, sceneManager: SceneManager) {
        val equippedWeapon = playerSystem.equippedWeapon

        if (!playerSystem.isDriving && equippedWeapon.actionType == WeaponActionType.MELEE) {
            isVisible = true
            val currentRange = equippedWeapon.meleeRange

            if (currentRange != lastRange) {
                rebuildIndicatorModel(currentRange) // This now builds the correct model based on style
                lastRange = currentRange
            }

            val playerPos = playerSystem.getPosition()
            val groundY = sceneManager.findHighestSupportY(playerPos.x, playerPos.z, playerPos.y, 0.1f, sceneManager.game.blockSize)
            val indicatorY = groundY + GROUND_OFFSET

            // Step 1.5: A full circle doesn't need rotation, so this is simpler
            indicatorInstance?.transform?.setToTranslation(playerPos.x, indicatorY, playerPos.z)

        } else {
            isVisible = false
        }
    }

    fun render(camera: Camera, environment: Environment) {
        if (!isVisible || !isEnabledByUser || indicatorInstance == null) return
        shaderProvider.setEnvironment(environment)
        modelBatch.begin(camera)
        modelBatch.render(indicatorInstance!!, environment)
        modelBatch.end()
    }

    fun dispose() {
        solidCircleModel?.dispose()
        texturedRingModel?.dispose()
        modelBatch.dispose()
        shaderProvider.dispose()
        whitePixelTexture.dispose()
        ringTexture.dispose()
    }
}
