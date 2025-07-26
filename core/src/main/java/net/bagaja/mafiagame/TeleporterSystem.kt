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
        // SETUP FOR 3D SPRITEBATCH RENDERING
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        spriteBatch.projectionMatrix = camera.combined
        spriteBatch.begin()

        val renderDistanceSq = 30f * 30f

        for (teleporter in activeTeleporters) {
            val teleporterPos = teleporter.gameObject.position
            val textWorldPos = tempVec3.set(teleporterPos).add(0f, 2.5f, 0f)

            if (camera.position.dst2(textWorldPos) < renderDistanceSq && camera.frustum.pointInFrustum(textWorldPos)) {

                // STATIC 3D TRANSFORMATION
                val transformMatrix = Matrix4()
                transformMatrix.setToTranslation(textWorldPos)

                val scale = 0.045f
                transformMatrix.scl(scale)

                // DRAWING
                spriteBatch.transformMatrix = transformMatrix
                font.data.setScale(1.0f)
                font.color = Color.WHITE
                glyphLayout.setText(font, teleporter.name)
                font.draw(spriteBatch, glyphLayout, -glyphLayout.width / 2, glyphLayout.height / 2)
            }
        }

        spriteBatch.end()

        // Restore matrices and GL state
        spriteBatch.transformMatrix.idt()
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_BLEND)
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
