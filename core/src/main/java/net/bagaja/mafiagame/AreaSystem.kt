package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.math.collision.BoundingBox
import java.util.UUID

data class GameArea(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val position: Vector3, // Center position
    var width: Float,
    var depth: Float,
    val debugInstance: ModelInstance? = null // Nullable for saving/loading
) {
    // Helper to check if a point is inside this area (ignoring Y height for now, treating it as infinite column)
    fun contains(point: Vector3): Boolean {
        val halfWidth = width / 2f
        val halfDepth = depth / 2f
        return point.x >= position.x - halfWidth &&
            point.x <= position.x + halfWidth &&
            point.z >= position.z - halfDepth &&
            point.z <= position.z + halfDepth
    }

    fun updateDebugVisuals() {
        debugInstance?.transform?.setToTranslation(position)
        debugInstance?.transform?.scale(width, 1f, depth)
    }
}

class AreaSystem(private val game: MafiaGame) : Disposable {
    val activeAreas = Array<GameArea>()
    private var lastActiveArea: GameArea? = null // To track enter/leave events

    // Rendering
    private val modelBatch = ModelBatch()
    private lateinit var debugBoxModel: Model
    var previewArea: GameArea? = null // The one currently being placed
    var currentPreviewName: String = "New Area"
    var currentPreviewWidth: Float = 10f
    var currentPreviewDepth: Float = 10f
    var previewPosition: Vector3 = Vector3()

    fun initialize() {
        val modelBuilder = ModelBuilder()
        val material = Material(
            ColorAttribute.createDiffuse(Color(1f, 0f, 1f, 0.3f)), // Purple transparent
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        )
        // Create a 1x1x1 box that we will scale up
        debugBoxModel = modelBuilder.createBox(1f, 4f, 1f, material,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong())
    }

    fun update(deltaTime: Float) {
        // Don't check triggers in editor mode to avoid spam
        if (game.isEditorMode) return

        val playerPos = game.playerSystem.getPosition()

        // Find the area the player is currently inside (prioritize smaller/nested areas if needed, here just takes first)
        val currentArea = activeAreas.find { it.contains(playerPos) }

        if (currentArea != lastActiveArea) {
            // State changed!
            if (lastActiveArea != null) {
                game.uiManager.showFloatingText("Leaving ${lastActiveArea!!.name}", Color.ORANGE, playerPos.cpy().add(0f, 2f, 0f))
            }

            if (currentArea != null) {
                game.uiManager.showAreaNotification("Entering ${currentArea.name}")
            }

            lastActiveArea = currentArea
        }
    }

    // --- Editor Logic ---

    fun startPlacement(name: String) {
        currentPreviewName = name
        val instance = ModelInstance(debugBoxModel)
        // Default start position (e.g., where player is looking, or 0,0,0)
        previewArea = GameArea(name = name, position = Vector3(), width = 10f, depth = 10f, debugInstance = instance)

        // NEW: Open the Properties Window immediately
        game.uiManager.showAreaPropertiesWindow()
    }

    fun updatePreviewPosition(position: Vector3) {
        previewPosition.set(position.x, position.y + 2f, position.z)
        syncPreview()
    }

    fun manualUpdate(width: Float, depth: Float, x: Float, y: Float, z: Float) {
        currentPreviewWidth = width
        currentPreviewDepth = depth
        previewPosition.set(x, y, z)
        syncPreview()
    }

    private fun syncPreview() {
        previewArea?.let {
            it.name = currentPreviewName
            it.position.set(previewPosition)
            it.width = currentPreviewWidth
            it.depth = currentPreviewDepth
            it.updateDebugVisuals()
        }
    }

    fun handleScroll(amount: Float) {
        if (previewArea == null) return

        // SPEED MODIFIERS
        var step = 2f
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT)) step = 10f
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_LEFT)) step = 50f

        val change = -amount * step

        // Update values
        currentPreviewWidth = (currentPreviewWidth + change).coerceAtLeast(2f)
        currentPreviewDepth = (currentPreviewDepth + change).coerceAtLeast(2f)

        // Force visual update immediately
        syncPreview()

        // Force UI update
        game.uiManager.updateAreaPropertiesValues(currentPreviewWidth, currentPreviewDepth, previewPosition)
    }

    fun confirmPlacement() {
        previewArea?.let { preview ->
            val newInstance = ModelInstance(debugBoxModel)
            val newArea = GameArea(
                name = preview.name,
                position = preview.position.cpy(), // Use current position
                width = preview.width,
                depth = preview.depth,
                debugInstance = newInstance
            )
            newArea.updateDebugVisuals()
            activeAreas.add(newArea)
            println("Created Area: ${newArea.name}")
        }
        game.uiManager.hideAreaPropertiesWindow() // Close window
    }

    fun cancelPlacement() {
        previewArea = null
        game.uiManager.hideAreaPropertiesWindow() // Close window
    }

    fun restoreAreaVisuals(area: GameArea) {
        // Create a new model instance for the loaded area data
        val instance = ModelInstance(debugBoxModel)
        // Use reflection or a copy constructor ideally, but for now we can't easily assign val properties
    }

    fun createDebugInstance(): ModelInstance {
        return ModelInstance(debugBoxModel)
    }

    fun render(camera: com.badlogic.gdx.graphics.Camera) {
        if (!game.isEditorMode) return

        game.shaderEffectManager.beginCapture() // Ensure transparency works if needed
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        modelBatch.begin(camera)

        // Render active areas
        activeAreas.forEach { area ->
            area.debugInstance?.let { modelBatch.render(it) }
        }

        // Render preview
        previewArea?.debugInstance?.let { modelBatch.render(it) }

        modelBatch.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    override fun dispose() {
        modelBatch.dispose()
        debugBoxModel.dispose()
    }
}
