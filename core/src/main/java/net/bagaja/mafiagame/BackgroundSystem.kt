package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import kotlin.math.abs
import kotlin.math.round

class BackgroundSystem: IFinePositionable {
    private val backgroundModels = mutableMapOf<BackgroundType, Model>()
    private val backgroundTextures = mutableMapOf<BackgroundType, Texture>()
    private val modelBuilder = ModelBuilder()
    private val gameBackgrounds = Array<GameBackground>()

    // Placement assistance
    private var previewInstance: ModelInstance? = null
    private var isShowingPreview = false
    private val snapTolerance = 2f // Distance for snapping to existing backgrounds
    private val gridSize = 4f // Optional grid snapping

    // Placement modes
    enum class PlacementMode {
        FREE,           // Free placement anywhere
        GRID_SNAP,      // Snap to grid
        ALIGN_ASSIST    // Assist with aligning to existing backgrounds
    }

    var placementMode = PlacementMode.ALIGN_ASSIST
    var currentSelectedBackground = BackgroundType.SMALL_HOUSE
        private set
    var currentSelectedBackgroundIndex = 0
        private set

    override var finePosMode = false
    override val fineStep = 0.25f

    fun initialize() {
        // Load textures and create models for each background type
        for (backgroundType in BackgroundType.entries) {
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

    private fun createPreviewModel(backgroundType: BackgroundType): Model? {
        val originalModel = backgroundModels[backgroundType] ?: return null

        // Create a semi-transparent preview material
        val previewMaterial = Material(
            backgroundTextures[backgroundType]?.let { TextureAttribute.createDiffuse(it) },
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
            IntAttribute.createCullFace(GL20.GL_NONE),
            ColorAttribute.createDiffuse(1f, 1f, 1f, 0.5f) // 50% transparency
        )

        return createSimpleQuadModel(modelBuilder, previewMaterial, backgroundType)
    }

    fun updatePreview(mousePosition: Vector3): Vector3 {
        val adjustedPosition = getAdjustedPosition(mousePosition)

        // Create or update preview instance
        if (previewInstance == null) {
            val previewModel = createPreviewModel(currentSelectedBackground)
            previewInstance = previewModel?.let { ModelInstance(it) }
        }

        previewInstance?.transform?.setTranslation(adjustedPosition)
        isShowingPreview = true

        return adjustedPosition
    }

    fun hidePreview() {
        isShowingPreview = false
    }

    fun renderPreview(modelBatch: com.badlogic.gdx.graphics.g3d.ModelBatch,
                      camera: com.badlogic.gdx.graphics.Camera,
                      environment: com.badlogic.gdx.graphics.g3d.Environment) {
        if (isShowingPreview && previewInstance != null) {
            modelBatch.render(previewInstance, environment)
        }
    }

    private fun getAdjustedPosition(originalPosition: Vector3): Vector3 {
        return when (placementMode) {
            PlacementMode.FREE -> originalPosition.cpy()
            PlacementMode.GRID_SNAP -> snapToGrid(originalPosition)
            PlacementMode.ALIGN_ASSIST -> getAlignedPosition(originalPosition)
        }
    }

    private fun snapToGrid(position: Vector3): Vector3 {
        return Vector3(
            round(position.x / gridSize) * gridSize,
            round(position.y / gridSize) * gridSize,
            round(position.z / gridSize) * gridSize
        )
    }

    private fun getAlignedPosition(position: Vector3): Vector3 {
        if (gameBackgrounds.isEmpty) {
            return snapToGrid(position) // Fall back to grid if no backgrounds exist
        }

        var bestPosition = position.cpy()
        var minDistance = Float.MAX_VALUE

        // Find the closest alignment opportunity
        for (background in gameBackgrounds) {
            val bgPos = background.position

            // Check for X-axis alignment (same X, different Z)
            val xAlignedPos = Vector3(bgPos.x, position.y, position.z)
            val xDistance = position.dst(xAlignedPos)
            if (xDistance < minDistance && xDistance < snapTolerance) {
                bestPosition = xAlignedPos
                minDistance = xDistance
            }

            // Check for Z-axis alignment (same Z, different X)
            val zAlignedPos = Vector3(position.x, position.y, bgPos.z)
            val zDistance = position.dst(zAlignedPos)
            if (zDistance < minDistance && zDistance < snapTolerance) {
                bestPosition = zAlignedPos
                minDistance = zDistance
            }

            // Check for Y-axis alignment (same Y, useful for layering)
            val yAlignedPos = Vector3(position.x, bgPos.y, position.z)
            val yDistance = position.dst(yAlignedPos)
            if (yDistance < minDistance && yDistance < snapTolerance) {
                bestPosition = yAlignedPos
                minDistance = yDistance
            }

            // Check for perfect grid alignment with existing backgrounds
            val gridAlignedPos = Vector3(
                round((bgPos.x + position.x) / (2 * gridSize)) * gridSize,
                position.y,
                round((bgPos.z + position.z) / (2 * gridSize)) * gridSize
            )
            val gridDistance = position.dst(gridAlignedPos)
            if (gridDistance < minDistance && gridDistance < snapTolerance * 1.5f) {
                bestPosition = gridAlignedPos
                minDistance = gridDistance
            }
        }

        // If no good alignment found, snap to grid
        if (minDistance == Float.MAX_VALUE) {
            bestPosition = snapToGrid(position)
        }

        return bestPosition
    }

    fun getPlacementInfo(position: Vector3): String {
        val adjustedPosition = getAdjustedPosition(position)
        val info = StringBuilder()

        info.append("Mode: ${placementMode.name}\n")
        info.append("Position: (${adjustedPosition.x.toInt()}, ${adjustedPosition.y.toInt()}, ${adjustedPosition.z.toInt()})\n")

        when (placementMode) {
            PlacementMode.ALIGN_ASSIST -> {
                val alignmentInfo = getAlignmentInfo(position, adjustedPosition)
                if (alignmentInfo.isNotEmpty()) {
                    info.append("Aligned: $alignmentInfo")
                }
            }
            PlacementMode.GRID_SNAP -> {
                info.append("Snapped to grid")
            }
            PlacementMode.FREE -> {
                info.append("Free placement")
            }
        }

        return info.toString()
    }

    private fun getAlignmentInfo(original: Vector3, adjusted: Vector3): String {
        if (original.dst(adjusted) < 0.1f) return ""

        val alignments = mutableListOf<String>()

        if (abs(original.x - adjusted.x) > 0.1f) alignments.add("X-axis")
        if (abs(original.y - adjusted.y) > 0.1f) alignments.add("Y-axis")
        if (abs(original.z - adjusted.z) > 0.1f) alignments.add("Z-axis")

        return alignments.joinToString(", ")
    }

    fun cyclePlacementMode() {
        placementMode = when (placementMode) {
            PlacementMode.FREE -> PlacementMode.GRID_SNAP
            PlacementMode.GRID_SNAP -> PlacementMode.ALIGN_ASSIST
            PlacementMode.ALIGN_ASSIST -> PlacementMode.FREE
        }
        println("Placement mode changed to: ${placementMode.name}")
    }

    fun nextBackground() {
        currentSelectedBackgroundIndex = (currentSelectedBackgroundIndex + 1) % BackgroundType.entries.size
        currentSelectedBackground = BackgroundType.entries.toTypedArray()[currentSelectedBackgroundIndex]

        // Clear preview when changing background type
        previewInstance = null

        println("Selected background: ${currentSelectedBackground.displayName}")
    }

    fun previousBackground() {
        currentSelectedBackgroundIndex = if (currentSelectedBackgroundIndex > 0) {
            currentSelectedBackgroundIndex - 1
        } else {
            BackgroundType.entries.size - 1
        }
        currentSelectedBackground = BackgroundType.entries.toTypedArray()[currentSelectedBackgroundIndex]

        // Clear preview when changing background type
        previewInstance = null

        println("Selected background: ${currentSelectedBackground.displayName}")
    }

    private fun createBackgroundInstance(backgroundType: BackgroundType): ModelInstance? {
        val model = backgroundModels[backgroundType]
        return model?.let { ModelInstance(it) }
    }

    fun addBackground(x: Float, y: Float, z: Float, backgroundType: BackgroundType): GameBackground? {
        val position = Vector3(x, y, z)
        val adjustedPosition = getAdjustedPosition(position)

        // Check if there's already a background too close to this position
        val existingBackground = getBackgroundAtPosition(adjustedPosition, 1f)
        if (existingBackground != null) {
            println("Background already exists near this position")
            return null
        }

        val backgroundInstance = createBackgroundInstance(backgroundType)
        if (backgroundInstance != null) {
            backgroundInstance.transform.setTranslation(adjustedPosition)
            val gameBackground = GameBackground(backgroundInstance, backgroundType, adjustedPosition)
            gameBackgrounds.add(gameBackground)
            println("Background ${backgroundType.displayName} added at: $adjustedPosition")
            return gameBackground
        }
        return null
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
            abs(background.position.x - position.x) < tolerance &&
                abs(background.position.z - position.z) < tolerance
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
    MANSION("Mansion", "textures/objects/houses/mansion.png", 30f, 35f),
    NICKELODEON("Nickelodeon", "textures/objects/houses/nickelodeon.png", 30f, 35f),
    TRANSPARENT_HOUSE("Test House", "textures/objects/houses/reuseable_house.png", 20f, 25f),
}
