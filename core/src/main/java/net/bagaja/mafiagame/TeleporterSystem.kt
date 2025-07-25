package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array

class TeleporterSystem(
    private val objectSystem: ObjectSystem,
    private val uiManager: UIManager
) : IFinePositionable {
    val activeTeleporters = Array<GameTeleporter>()
    private val spriteBatch = SpriteBatch()
    private val font: BitmapFont by lazy { uiManager.skin.getFont("default-font") }
    private val tempVec3 = Vector3()
    private val glyphLayout = GlyphLayout()

    // --- Linking State ---
    var isLinkingMode = false
        private set
    private var firstTeleporterToLink: GameTeleporter? = null

    override var finePosMode = false
    override val fineStep = 0.25f

    override fun toggleFinePosMode() {
        finePosMode = !finePosMode
        val status = if (finePosMode) "ON" else "OFF"
        uiManager.updatePlacementInfo("Teleporter Fine Positioning: $status")
    }

    fun startLinking(firstTeleporter: GameTeleporter) {
        if (isLinkingMode) return

        // Ask for the first teleporter's name immediately
        uiManager.showTeleporterNameDialog("Set Name for Point 1") { name ->
            firstTeleporter.name = name
            isLinkingMode = true
            firstTeleporterToLink = firstTeleporter
            uiManager.setPersistentMessage("Place second teleporter or Right-Click/ESC to cancel.")
        }
    }

    fun cancelLinking() {
        if (!isLinkingMode) return
        firstTeleporterToLink?.let {
            activeTeleporters.removeValue(it, true)
        }
        resetLinkingState()
    }

    fun completeLinking(secondTeleporter: GameTeleporter) {
        val first = firstTeleporterToLink ?: return

        // Now, ask for the second teleporter's name
        uiManager.showTeleporterNameDialog("Set Name for Point 2") { name ->
            secondTeleporter.name = name

            // Link them together
            first.linkedTeleporterId = secondTeleporter.id
            secondTeleporter.linkedTeleporterId = first.id

            println("Teleporter link created: '${first.name}' <--> '${secondTeleporter.name}'")
            resetLinkingState()
        }
    }

    private fun resetLinkingState() {
        isLinkingMode = false
        firstTeleporterToLink = null
        uiManager.clearPersistentMessage()
    }

    fun addTeleporterAt(position: Vector3): GameTeleporter? {
        val gameObject = objectSystem.createGameObjectWithLight(ObjectType.TELEPORTER, position.cpy()) ?: return null

        // Set the transform for BOTH the main instance and the debug instance
        gameObject.modelInstance.transform.setTranslation(position)
        gameObject.debugInstance?.transform?.setTranslation(position)

        val newTeleporter = GameTeleporter(
            id = java.util.UUID.randomUUID().toString(),
            gameObject = gameObject
        )
        activeTeleporters.add(newTeleporter)
        return newTeleporter
    }

    fun removeTeleporter(teleporterToRemove: GameTeleporter) {
        // Find and remove its linked partner first
        teleporterToRemove.linkedTeleporterId?.let { linkedId ->
            val partner = activeTeleporters.find { it.id == linkedId }
            partner?.let {
                activeTeleporters.removeValue(it, true)
                println("Removed linked teleporter partner: ${it.id}")
            }
        }
        // Then remove the one that was clicked
        activeTeleporters.removeValue(teleporterToRemove, true)
        println("Removed teleporter: ${teleporterToRemove.id}")
    }

    fun checkAndTeleportPlayer(playerSystem: PlayerSystem) {
        val playerPos = playerSystem.getPosition()

        for (teleporter in activeTeleporters) {
            // Check if player is close enough to the teleporter pad
            if (playerPos.dst(teleporter.gameObject.position) < 2.5f) {
                // Find the destination teleporter
                teleporter.linkedTeleporterId?.let { destId ->
                    val destination = activeTeleporters.find { it.id == destId }
                    if (destination != null) {
                        playerSystem.teleportTo(destination.gameObject.position)
                        return // Exit after one teleport to prevent loops
                    }
                }
            }
        }
    }

    fun renderNameplates(camera: Camera) {
        val playerPos = camera.position
        val renderDistance = 20f

        for (teleporter in activeTeleporters) {
            val teleporterPos = teleporter.gameObject.position
            val distanceToPlayer = playerPos.dst(teleporterPos)

            if (distanceToPlayer < renderDistance) {
                // Position for the text (above the teleporter)
                val textWorldPos = Vector3(teleporterPos).add(0f, 3f, 0f)

                // Check if the teleporter is in front of the camera
                val directionToTeleporter = Vector3(textWorldPos).sub(playerPos).nor()
                val cameraForward = camera.direction.cpy().nor()
                val dotProduct = directionToTeleporter.dot(cameraForward)

                // Only render if teleporter is in front of camera
                if (dotProduct > 0.1f) {
                    // Save current matrices
                    val oldProjection = spriteBatch.projectionMatrix.cpy()
                    val oldTransform = spriteBatch.transformMatrix.cpy()

                    // Create billboard transformation matrix
                    val billboardMatrix = Matrix4()

                    // Calculate billboard vectors
                    val forward = Vector3(playerPos).sub(textWorldPos).nor()
                    val right = Vector3(camera.up).crs(forward).nor()
                    val up = Vector3(forward).crs(right).nor()

                    // Set up billboard matrix (always faces camera)
                    billboardMatrix.setToLookAt(textWorldPos, playerPos, camera.up)
                    billboardMatrix.inv() // Invert to face the camera

                    // Scale based on distance for consistent size
                    val scale = Math.max(0.005f, distanceToPlayer * 0.001f)
                    billboardMatrix.scl(scale)

                    // Set matrices for 3D rendering
                    spriteBatch.projectionMatrix = camera.combined
                    spriteBatch.transformMatrix = billboardMatrix

                    // Disable depth testing so text always shows on top
                    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
                    Gdx.gl.glEnable(GL20.GL_BLEND)
                    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

                    spriteBatch.begin()

                    font.color = Color.CYAN

                    // Use GlyphLayout to get the width of the text
                    glyphLayout.setText(font, teleporter.name)

                    // Draw text centered at origin (billboard matrix handles positioning)
                    val textX = -glyphLayout.width / 2f
                    val textY = glyphLayout.height / 2f

                    font.draw(spriteBatch, glyphLayout, textX, textY)

                    spriteBatch.end()

                    // Restore matrices and GL state
                    spriteBatch.projectionMatrix = oldProjection
                    spriteBatch.transformMatrix = oldTransform
                    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
                }
            }
        }
    }

    fun findClosestTeleporter(position: Vector3, maxDistance: Float): GameTeleporter? {
        return activeTeleporters
            .minByOrNull { it.gameObject.position.dst2(position) } // Find the closest one
            ?.takeIf { it.gameObject.position.dst(position) < maxDistance } // Return it only if it's within range
    }

    fun dispose() {
        spriteBatch.dispose()
        font.dispose()
    }
}
