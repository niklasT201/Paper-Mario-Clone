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
    var isMovingArea = false

    // Rendering
    private val modelBatch = ModelBatch()
    private lateinit var debugBoxModel: Model
    var previewArea: GameArea? = null // The one currently being placed
    var currentPreviewName: String = "New Area"
    var currentPreviewWidth: Float = 10f
    var currentPreviewDepth: Float = 10f
    var previewPosition: Vector3 = Vector3()
    private var editingArea: GameArea? = null
    private var areaTransitionCooldown = 0f
    private val COOLDOWN_DURATION = 3.0f // Minimum seconds between notifications for same area
    private var pendingArea: GameArea? = null

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
        if (game.isEditorMode) return
        if (game.sceneManager.currentScene != SceneType.WORLD) return

        val playerPos = game.playerSystem.getPosition()
        val currentArea = activeAreas.find { it.contains(playerPos) }

        // Timer update
        if (areaTransitionCooldown > 0f) {
            areaTransitionCooldown -= deltaTime
        }

        // Logic:
        // 1. If we are truly in a NEW area (different from last confirmed active area)
        if (currentArea != lastActiveArea) {

            // Case A: We are just "flickering" back and forth rapidly
            if (areaTransitionCooldown > 0f) {
                // Do nothing. We ignore rapid changes.
                // Effectively, the system thinks you never left the lastActiveArea.
                return
            }

            // Case B: This is a legitimate, sustained transition

            // 1. Notify leaving old area
            if (lastActiveArea != null) {
                // Only show "Leaving" if we are actually entering "Null" (Wilderness)
                // If we are moving from Area A -> Area B directly, just show "Entering B"
                if (currentArea == null) {
                    game.uiManager.queueAreaNotification("Leaving ${lastActiveArea!!.name}")
                }
            }

            // 2. Notify entering new area
            if (currentArea != null) {
                game.uiManager.queueAreaNotification("Entering ${currentArea.name}")
                // Start cooldown to prevent "Leaving" spam if player steps back out immediately
                areaTransitionCooldown = COOLDOWN_DURATION
            }

            // 3. Commit state
            lastActiveArea = currentArea
        }
    }

    // --- Editor Logic ---

    fun startPlacement(name: String) {
        currentPreviewName = name
        val instance = ModelInstance(debugBoxModel)
        // Default start position (e.g., where player is looking, or 0,0,0)
        previewArea = GameArea(name = name, position = Vector3(), width = 10f, depth = 10f, debugInstance = instance)

        isMovingArea = true // New areas always start in moving mode
        game.uiManager.showAreaPropertiesWindow()
    }

    fun updatePreviewPosition(position: Vector3) {
        if (!isMovingArea) return // Ignore mouse if not in moving mode

        previewPosition.set(position.x, position.y + 2f, position.z)
        syncPreview()
    }

    fun toggleMovingMode() {
        isMovingArea = !isMovingArea
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

    fun startEditing(area: GameArea) {
        editingArea = area
        isMovingArea = false // Editing starts LOCKED in place

        // Load values into temp variables
        currentPreviewName = area.name
        currentPreviewWidth = area.width
        currentPreviewDepth = area.depth
        previewPosition.set(area.position)

        // "Hide" the original visual while editing by shrinking it or moving it away
        // (or just let the preview overlay it, which is simpler)

        // Set the preview visual to match
        previewArea = GameArea(
            name = area.name,
            position = area.position.cpy(),
            width = area.width,
            depth = area.depth,
            debugInstance = ModelInstance(debugBoxModel)
        )
        previewArea!!.updateDebugVisuals()

        // Open UI with current values
        game.uiManager.showAreaPropertiesWindow()
    }

    fun confirmPlacement() {
        previewArea?.let { preview ->
            if (editingArea != null) {
                // UPDATE EXISTING
                editingArea!!.name = preview.name
                editingArea!!.position.set(preview.position)
                editingArea!!.width = preview.width
                editingArea!!.depth = preview.depth
                editingArea!!.updateDebugVisuals()
                println("Updated Area: ${editingArea!!.name}")
            } else {
                // CREATE NEW
                val newInstance = ModelInstance(debugBoxModel)
                val newArea = GameArea(
                    name = preview.name,
                    position = preview.position.cpy(),
                    width = preview.width,
                    depth = preview.depth,
                    debugInstance = newInstance
                )
                newArea.updateDebugVisuals()
                activeAreas.add(newArea)
                println("Created Area: ${newArea.name}")
            }
        }
        // Cleanup
        editingArea = null
        previewArea = null
        game.uiManager.hideAreaPropertiesWindow()
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
