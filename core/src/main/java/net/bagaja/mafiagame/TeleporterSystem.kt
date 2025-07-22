package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array

class TeleporterSystem(
    private val objectSystem: ObjectSystem,
    private val uiManager: UIManager
) : IFinePositionable {
    val activeTeleporters = Array<GameTeleporter>()
    private val spriteBatch = SpriteBatch()
    private val font = BitmapFont()
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
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST) // Disable depth testing for 2D overlay

        // DO NOT change the projection matrix. Let SpriteBatch use its default screen coordinates.
        spriteBatch.begin()

        val playerPos = camera.position
        val renderDistance = 20f // The distance you wanted

        for (teleporter in activeTeleporters) {
            if (playerPos.dst(teleporter.gameObject.position) < renderDistance) {
                // Project 3D world coordinates to 2D screen coordinates
                tempVec3.set(teleporter.gameObject.position).add(0f, 1f, 0f) // Position text slightly above the pad
                camera.project(tempVec3)

                if (tempVec3.z < 1f) { // Only draw if it's in front of the camera
                    font.color = Color.CYAN

                    // Use GlyphLayout to get the width of the text
                    glyphLayout.setText(font, teleporter.name)

                    // Calculate the centered X position
                    val textX = tempVec3.x - glyphLayout.width / 2

                    font.draw(spriteBatch, glyphLayout, textX, tempVec3.y)
                }
            }
        }
        spriteBatch.end()
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST) // Re-enable depth testing
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
