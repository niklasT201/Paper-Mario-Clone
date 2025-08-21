package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import kotlin.math.max
import kotlin.math.sin

class CharacterPhysicsSystem(private val sceneManager: SceneManager) {

    // Constants taken from your original systems
    companion object {
        const val FALL_SPEED = 25f
        const val MAX_STEP_HEIGHT = 1.1f
        private const val WOBBLE_AMPLITUDE_DEGREES = 5f
        private const val WOBBLE_FREQUENCY = 6f
    }

    private val tempBlockBounds = BoundingBox()
    private val nearbyBlocks = Array<GameBlock>()

    fun update(component: PhysicsComponent, desiredMovement: Vector3, deltaTime: Float): Boolean {
        val originalPosition = component.position.cpy()
        component.isMoving = false

        val moveAmount = component.speed * deltaTime
        val deltaX = desiredMovement.x * moveAmount
        val deltaZ = desiredMovement.z * moveAmount

        // --- 1. Horizontal Movement Resolution (X then Z) ---
        if (deltaX != 0f) {
            val footY = component.position.y - (component.size.y / 2f)
            val supportY = sceneManager.findHighestSupportY(component.position.x + deltaX, component.position.z, component.position.y, component.size.x / 2f, sceneManager.game.blockSize)
            if (supportY - footY <= MAX_STEP_HEIGHT) {
                if (canMoveTo(component.position.x + deltaX, component.position.y, component.position.z, component)) {
                    component.position.x += deltaX
                }
            }
        }

        if (deltaZ != 0f) {
            val footY = component.position.y - (component.size.y / 2f)
            val supportY = sceneManager.findHighestSupportY(component.position.x, component.position.z + deltaZ, component.position.y, component.size.x / 2f, sceneManager.game.blockSize)
            if (supportY - footY <= MAX_STEP_HEIGHT) {
                if (canMoveTo(component.position.x, component.position.y, component.position.z + deltaZ, component)) {
                    component.position.z += deltaZ
                }
            }
        }

        // 2. Vertical Movement
        val xEdgeOffset = component.size.x / 2f * 0.9f
        val zEdgeOffset = component.size.z / 2f * 0.9f // Use component's Z size for depth
        val supportCandidates = mutableListOf<Float>()
        val checkRadius = 0.1f // A small radius for point checks
        val blockSize = sceneManager.game.blockSize
        val checkSupport = { x: Float, z: Float -> sceneManager.findStrictSupportY(x, z, originalPosition.y, checkRadius, blockSize) }

        // Perform all 9 checks to find the highest ground beneath the character's footprint
        supportCandidates.add(checkSupport(component.position.x, component.position.z)) // Center
        supportCandidates.add(checkSupport(component.position.x - xEdgeOffset, component.position.z)) // Left
        supportCandidates.add(checkSupport(component.position.x + xEdgeOffset, component.position.z)) // Right
        supportCandidates.add(checkSupport(component.position.x, component.position.z - zEdgeOffset)) // Front
        supportCandidates.add(checkSupport(component.position.x, component.position.z + zEdgeOffset)) // Back
        supportCandidates.add(checkSupport(component.position.x - xEdgeOffset, component.position.z - zEdgeOffset)) // Front-Left
        supportCandidates.add(checkSupport(component.position.x + xEdgeOffset, component.position.z - zEdgeOffset)) // Front-Right
        supportCandidates.add(checkSupport(component.position.x - xEdgeOffset, component.position.z + zEdgeOffset)) // Back-Left
        supportCandidates.add(checkSupport(component.position.x + xEdgeOffset, component.position.z + zEdgeOffset)) // Back-Right

        val maxSupportY = supportCandidates.maxOrNull() ?: 0f
        val footY = originalPosition.y - (component.size.y / 2f)
        val effectiveSupportY = if (maxSupportY - footY <= MAX_STEP_HEIGHT) maxSupportY else sceneManager.findStrictSupportY(originalPosition.x, originalPosition.z, originalPosition.y, component.size.x / 2f, blockSize)

        val targetY = effectiveSupportY + (component.size.y / 2f)
        val fallY = component.position.y - FALL_SPEED * deltaTime
        component.position.y = max(targetY, fallY)

        component.isGrounded = component.position.y <= targetY + 0.1f

        // --- 3. Finalize State & Animation ---
        val moved = !component.position.epsilonEquals(originalPosition, 0.001f)
        component.isMoving = (originalPosition.x != component.position.x) || (originalPosition.z != component.position.z)

        if (component.isMoving) {
            component.walkAnimationTimer += deltaTime
            component.wobbleAngle = sin(component.walkAnimationTimer * WOBBLE_FREQUENCY) * WOBBLE_AMPLITUDE_DEGREES
        } else {
            if (kotlin.math.abs(component.wobbleAngle) > 0.1f) component.wobbleAngle *= (1.0f - deltaTime * 10f).coerceAtLeast(0f) else component.wobbleAngle = 0f
            component.walkAnimationTimer = 0f
        }

        if (moved) component.updateBounds()
        return moved
    }

    /**
     * The single, unified collision detection function for all characters.
     * Based on your robust player collision.
     */
    private fun canMoveTo(x: Float, y: Float, z: Float, component: PhysicsComponent): Boolean {
        val shrinkFor3D = 0.2f
        val shrinkFor2D = 0.8f

        val boundsFor3D = BoundingBox()
        boundsFor3D.set(
            Vector3(x - (component.size.x / 2 - shrinkFor3D), y - component.size.y / 2, z - (component.size.z / 2 - shrinkFor3D)),
            Vector3(x + (component.size.x / 2 - shrinkFor3D), y + component.size.y / 2, z + (component.size.z / 2 - shrinkFor3D))
        )

        // 1. Check against Blocks
        sceneManager.activeChunkManager.getBlocksAround(Vector3(x, y, z), 10f, nearbyBlocks)
        for (gameBlock in nearbyBlocks) {
            if (!gameBlock.blockType.hasCollision) continue
            if (gameBlock.collidesWith(boundsFor3D)) {
                val footY = y - (component.size.y / 2f)
                val blockTop = gameBlock.getBoundingBox(sceneManager.game.blockSize, tempBlockBounds).max.y
                if (blockTop - footY <= MAX_STEP_HEIGHT) continue
                return false
            }
        }

        // 2. Check against Houses (complex meshes)
        for (house in sceneManager.activeHouses) {
            if (house.collidesWithMesh(boundsFor3D)) return false
        }

        // 3. Check against Interior Objects (RE-ADDED AND CORRECTED)
        for (interior in sceneManager.activeInteriors) {
            // Skip any interior objects that don't have collision enabled.
            if (!interior.interiorType.hasCollision) continue

            // Differentiate collision logic for 3D vs 2D interior objects.
            if (interior.interiorType.is3D) {
                if (interior.collidesWithMesh(boundsFor3D)) return false
            } else {
                // For 2D objects like doors, use the simpler rectangular check.
                val characterRadius = (component.size.x / 2f) - shrinkFor2D
                if (interior.collidesWithPlayer2D(Vector3(x, y, z), characterRadius)) return false
            }
        }

        // If we passed all checks, the move is valid.
        return true
    }

    fun isPositionValid(potentialPosition: Vector3, component: PhysicsComponent): Boolean {
        // This simply calls the internal canMoveTo method.
        return canMoveTo(potentialPosition.x, potentialPosition.y, potentialPosition.z, component)
    }
}
