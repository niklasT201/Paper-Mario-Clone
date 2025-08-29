package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Plane
import com.badlogic.gdx.math.Vector3

class TargetingIndicatorSystem {

    private lateinit var indicatorTexture: Texture
    private lateinit var indicatorModel: Model
    private lateinit var indicatorInstance: ModelInstance

    private lateinit var billboardModelBatch: ModelBatch
    private lateinit var billboardShaderProvider: BillboardShaderProvider

    private var isVisible = false
    private var isEnabledByUser = true // Toggled by the user with a shortcut

    private val groundPlane = Plane(Vector3.Y, 0f)
    private val intersectionPoint = Vector3()
    private val indicatorPosition = Vector3()
    private val screenCoords = Vector3()
    private var needsCentering = false

    companion object {
        private const val INDICATOR_SIZE = 3.5f
        private const val GROUND_OFFSET = 0.08f // To prevent Z-fighting with the ground
    }

    fun initialize() {
        billboardShaderProvider = BillboardShaderProvider().apply {
            setBillboardLightingStrength(0.8f) // Make it affected by world light
            setMinLightLevel(0.4f)             // Not completely black in shadows
        }
        billboardModelBatch = ModelBatch(billboardShaderProvider)

        val modelBuilder = ModelBuilder()
        try {
            indicatorTexture = Texture(Gdx.files.internal("gui/visual_debug.png")).apply {
                setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            }
            val material = Material(
                TextureAttribute.createDiffuse(indicatorTexture),
                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                IntAttribute.createCullFace(GL20.GL_NONE)
            )

            // Create a horizontal plane model for the indicator, so it lays flat on the ground
            indicatorModel = modelBuilder.createRect(
                -INDICATOR_SIZE / 2f, 0f,  INDICATOR_SIZE / 2f,
                -INDICATOR_SIZE / 2f, 0f, -INDICATOR_SIZE / 2f,
                INDICATOR_SIZE / 2f, 0f, -INDICATOR_SIZE / 2f,
                INDICATOR_SIZE / 2f, 0f,  INDICATOR_SIZE / 2f,
                0f, 1f, 0f, // Normal pointing straight up
                material,
                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
            )

            indicatorInstance = ModelInstance(indicatorModel)
            indicatorInstance.userData = "effect" // Use a generic userData for lighting

        } catch (e: Exception) {
            println("ERROR: Could not load targeting indicator texture 'gui/visual_debug.png': ${e.message}")
        }
    }

    /**
     * Toggles the user's preference for showing the indicator.
     */
    fun toggle() {
        isEnabledByUser = !isEnabledByUser
        // If the user turns it off, immediately hide the visual
        if (!isEnabledByUser) {
            // If turning off, just hide it.
            isVisible = false
        } else {
            // If turning ON, set the flag to center it on the next update.
            needsCentering = true
        }
    }

    fun isEnabled(): Boolean = isEnabledByUser

    /**
     * The main logic loop for the indicator.
     */
    fun update(
        cameraManager: CameraManager,
        playerSystem: PlayerSystem,
        sceneManager: SceneManager,
        raycastSystem: RaycastSystem
    ) {
        val equippedWeapon = playerSystem.equippedWeapon
        val shouldBeVisible = equippedWeapon.actionType == WeaponActionType.SHOOTING ||
            (equippedWeapon.actionType == WeaponActionType.MELEE && equippedWeapon != WeaponType.UNARMED)

        // First, check all conditions for the indicator to be visible.
        if (!isEnabledByUser || !shouldBeVisible) {
            isVisible = false
            return
        }

        if (needsCentering) {
            val playerPos = playerSystem.getPosition()
            val playerRotation = playerSystem.playerCurrentRotationY

            val directionX = if (playerRotation == 180f) -1f else 1f
            val offset = Vector3(directionX * 8f, 0f, 0f)
            val targetWorldPos = playerPos.add(offset)

            val groundY = sceneManager.findHighestSupportY(targetWorldPos.x, targetWorldPos.z, targetWorldPos.y, 0.1f, sceneManager.game.blockSize)
            targetWorldPos.y = groundY + GROUND_OFFSET

            cameraManager.camera.project(screenCoords.set(targetWorldPos))

            val correctedY = Gdx.graphics.height - screenCoords.y.toInt()
            Gdx.input.setCursorPosition(screenCoords.x.toInt(), correctedY)

            needsCentering = false
        }

        // Create a ray from the current mouse position.
        val mouseX = Gdx.input.x.toFloat()
        val mouseY = Gdx.input.y.toFloat()
        val ray = cameraManager.camera.getPickRay(mouseX, mouseY)

        // 1. Raycast against enemies first, as they are a priority target.
        val hitEnemy = raycastSystem.getEnemyAtRay(ray, sceneManager.activeEnemies)
        if (hitEnemy != null) {
            // "Stick" to the enemy by snapping to their base position.
            val groundY = sceneManager.findHighestSupportY(hitEnemy.position.x, hitEnemy.position.z, hitEnemy.position.y, 0.1f, sceneManager.game.blockSize)
            indicatorPosition.set(hitEnemy.position.x, groundY + GROUND_OFFSET, hitEnemy.position.z)
            isVisible = true
            updateTransform()
            return // Stop here since we found a target
        }

        // 2. If no enemy was hit, raycast against NPCs.
        val hitNPC = raycastSystem.getNPCAtRay(ray, sceneManager.activeNPCs)
        if (hitNPC != null) {
            // "Stick" to the NPC.
            val groundY = sceneManager.findHighestSupportY(hitNPC.position.x, hitNPC.position.z, hitNPC.position.y, 0.1f, sceneManager.game.blockSize)
            indicatorPosition.set(hitNPC.position.x, groundY + GROUND_OFFSET, hitNPC.position.z)
            isVisible = true
            updateTransform()
            return // Stop here
        }

        // 3. If nothing was hit, place the indicator on the ground.
        if (Intersector.intersectRayPlane(ray, groundPlane, intersectionPoint)) {
            val groundY = sceneManager.findHighestSupportY(intersectionPoint.x, intersectionPoint.z, intersectionPoint.y, 0.1f, sceneManager.game.blockSize)
            indicatorPosition.set(intersectionPoint.x, groundY + GROUND_OFFSET, intersectionPoint.z)
            isVisible = true
            updateTransform()
        } else {
            // If the ray doesn't hit anything, hide the indicator.
            isVisible = false
        }
    }

    private fun updateTransform() {
        indicatorInstance.transform.setToTranslation(indicatorPosition)
    }

    fun render(camera: Camera, environment: Environment) {
        if (!isVisible || !::billboardModelBatch.isInitialized) return

        billboardShaderProvider.setEnvironment(environment)
        billboardModelBatch.begin(camera)
        billboardModelBatch.render(indicatorInstance, environment)
        billboardModelBatch.end()
    }

    fun dispose() {
        if (::indicatorTexture.isInitialized) indicatorTexture.dispose()
        if (::indicatorModel.isInitialized) indicatorModel.dispose()
        if (::billboardModelBatch.isInitialized) billboardModelBatch.dispose()
        if (::billboardShaderProvider.isInitialized) billboardShaderProvider.dispose()
    }
}
