package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array

class BackgroundSystem {
    private val backgroundModels = mutableMapOf<BackgroundType, Model>()
    private val backgroundTextures = mutableMapOf<BackgroundType, Texture>()
    private val modelBuilder = ModelBuilder()
    private val gameBackgrounds = Array<GameBackground>()

    var currentSelectedBackground = BackgroundType.SMALL_HOUSE
        private set
    var currentSelectedBackgroundIndex = 0
        private set

    fun initialize() {
        // Load textures and create models for each background type
        for (backgroundType in BackgroundType.values()) {
            try {
                // Load texture for 2D backgrounds
                val texture = Texture(Gdx.files.internal(backgroundType.texturePath))
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                backgroundTextures[backgroundType] = texture

                // Create material with texture and transparency
                val material = Material(
                    TextureAttribute.createDiffuse(texture),
                    BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                    IntAttribute.createCullFace(GL20.GL_NONE) // Disable backface culling
                )

                // Create simple quad model
                val model = createSimpleQuadModel(modelBuilder, material, backgroundType)
                backgroundModels[backgroundType] = model
                println("Loaded 2D background type: ${backgroundType.displayName}")
            } catch (e: Exception) {
                println("Failed to load background ${backgroundType.displayName}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun createSimpleQuadModel(modelBuilder: ModelBuilder, material: Material, backgroundType: BackgroundType): Model {
        // Calculate dimensions based on the background size
        val width = backgroundType.width
        val height = backgroundType.height
        val halfWidth = width / 2f

        // Create a model with a single quad
        modelBuilder.begin()

        val part = modelBuilder.part(
            "background",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or
                VertexAttributes.Usage.Normal or
                VertexAttributes.Usage.TextureCoordinates).toLong(),
            material
        )

        // Vertices for the quad (facing forward along Z-axis)
        part.vertex(-halfWidth, 0f, 0f, 0f, 0f, 1f, 0f, 1f) // Bottom left
        part.vertex(halfWidth, 0f, 0f, 0f, 0f, 1f, 1f, 1f)  // Bottom right
        part.vertex(halfWidth, height, 0f, 0f, 0f, 1f, 1f, 0f) // Top right
        part.vertex(-halfWidth, height, 0f, 0f, 0f, 1f, 0f, 0f) // Top left

        // Triangles for the quad
        part.triangle(0, 1, 2) // First triangle
        part.triangle(2, 3, 0) // Second triangle

        return modelBuilder.end()
    }

    fun nextBackground() {
        currentSelectedBackgroundIndex = (currentSelectedBackgroundIndex + 1) % BackgroundType.values().size
        currentSelectedBackground = BackgroundType.values()[currentSelectedBackgroundIndex]
        println("Selected background: ${currentSelectedBackground.displayName}")
    }

    fun previousBackground() {
        currentSelectedBackgroundIndex = if (currentSelectedBackgroundIndex > 0) {
            currentSelectedBackgroundIndex - 1
        } else {
            BackgroundType.values().size - 1
        }
        currentSelectedBackground = BackgroundType.values()[currentSelectedBackgroundIndex]
        println("Selected background: ${currentSelectedBackground.displayName}")
    }

    fun createBackgroundInstance(backgroundType: BackgroundType): ModelInstance? {
        val model = backgroundModels[backgroundType]
        return model?.let { ModelInstance(it) }
    }

    fun addBackground(x: Float, y: Float, z: Float, backgroundType: BackgroundType) {
        val backgroundInstance = createBackgroundInstance(backgroundType)
        if (backgroundInstance != null) {
            val position = Vector3(x, y, z)
            backgroundInstance.transform.setTranslation(position)

            val gameBackground = GameBackground(backgroundInstance, backgroundType, position)
            gameBackgrounds.add(gameBackground)
            println("Background ${backgroundType.displayName} added at: $position")
        }
    }

    fun removeBackground(backgroundToRemove: GameBackground) {
        gameBackgrounds.removeValue(backgroundToRemove, true)
        println("Background ${backgroundToRemove.backgroundType.displayName} removed at: ${backgroundToRemove.position}")
    }

    fun getBackgrounds(): Array<GameBackground> {
        return gameBackgrounds
    }

    fun getBackgroundAtPosition(position: Vector3, tolerance: Float): GameBackground? {
        return gameBackgrounds.find { background ->
            kotlin.math.abs(background.position.x - position.x) < tolerance &&
                kotlin.math.abs(background.position.z - position.z) < tolerance
        }
    }

    fun dispose() {
        backgroundModels.values.forEach { it.dispose() }
        backgroundTextures.values.forEach { it.dispose() }
    }
}

// Game background class to store background data (no collision needed)
data class GameBackground(
    val modelInstance: ModelInstance,
    val backgroundType: BackgroundType,
    val position: Vector3
)

// Background type definitions (2D backgrounds)
enum class BackgroundType(
    val displayName: String,
    val texturePath: String,
    val width: Float,
    val height: Float
) {
    SMALL_HOUSE("Small House BG", "textures/objects/houses/worker_house.png", 20f, 25f),
    FLAT("Flat BG", "textures/objects/houses/flat_default.png", 20f, 25f),
    VILLA("Villa BG", "textures/objects/houses/villa.png", 30f, 35f),
}
