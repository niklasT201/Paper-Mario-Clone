package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Array
import kotlin.math.floor

class PlayerSystem {
    // Player model and rendering
    private lateinit var playerTexture: Texture
    private lateinit var playerModel: Model
    private lateinit var playerInstance: ModelInstance
    private lateinit var playerMaterial: Material

    // Custom shader for billboard lighting
    private lateinit var billboardShaderProvider: BillboardShaderProvider
    private lateinit var billboardModelBatch: ModelBatch

    // Player position and movement
    private val playerPosition = Vector3(0f, 2f, 0f)
    private val playerSpeed = 8f
    private val playerBounds = BoundingBox()
    private val playerSize = Vector3(3f, 4f, 3f)

    // Player rotation for Paper Mario effect
    private var playerTargetRotationY = 0f
    private var playerCurrentRotationY = 0f
    private val rotationSpeed = 360f
    private var lastMovementDirection = 0f

    // Reference to block system for collision detection
    private var blockSize = 4f

    fun initialize(blockSize: Float) {
        this.blockSize = blockSize
        setupBillboardShader()
        setupPlayerModel()
        updatePlayerBounds()
    }

    private fun setupBillboardShader() {
        billboardShaderProvider = BillboardShaderProvider()
        billboardModelBatch = ModelBatch(billboardShaderProvider)

        // Configure billboard lighting
        billboardShaderProvider.setBillboardLightingStrength(0.8f) // 80% billboard lighting, 20% standard
        billboardShaderProvider.setMinLightLevel(0.25f) // Minimum 25% light level
    }

    private fun setupPlayerModel() {
        val modelBuilder = ModelBuilder()

        // Load player texture
        playerTexture = Texture(Gdx.files.internal("textures/player/pig_character.png"))

        // Create player material with the texture
        playerMaterial = Material(
            TextureAttribute.createDiffuse(playerTexture),
            com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
            com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.createCullFace(GL20.GL_NONE)
        )

        // Create a 3D plane/quad for the player (billboard)
        val playerSizeVisual = 4f
        playerModel = modelBuilder.createRect(
            -playerSizeVisual / 2, -playerSizeVisual / 2, 0f,
            playerSizeVisual / 2, -playerSizeVisual / 2, 0f,
            playerSizeVisual / 2, playerSizeVisual / 2, 0f,
            -playerSizeVisual / 2, playerSizeVisual / 2, 0f,
            0f, 0f, 1f,
            playerMaterial,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
        )

        // Create player instance
        playerInstance = ModelInstance(playerModel)
        updatePlayerTransform()
    }

    fun handleMovement(deltaTime: Float, gameBlocks: Array<GameBlock>): Boolean {
        var moved = false
        var currentMovementDirection = 0f

        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            val newX = playerPosition.x - playerSpeed * deltaTime
            if (canMoveTo(newX, playerPosition.y, playerPosition.z, gameBlocks)) {
                playerPosition.x = newX
                moved = true
                currentMovementDirection = -1f
            }
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            val newX = playerPosition.x + playerSpeed * deltaTime
            if (canMoveTo(newX, playerPosition.y, playerPosition.z, gameBlocks)) {
                playerPosition.x = newX
                moved = true
                currentMovementDirection = 1f
            }
        }
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            val newZ = playerPosition.z - playerSpeed * deltaTime
            if (canMoveTo(playerPosition.x, playerPosition.y, newZ, gameBlocks)) {
                playerPosition.z = newZ
                moved = true
            }
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            val newZ = playerPosition.z + playerSpeed * deltaTime
            if (canMoveTo(playerPosition.x, playerPosition.y, newZ, gameBlocks)) {
                playerPosition.z = newZ
                moved = true
            }
        }

        // Update target rotation based on horizontal movement
        if (currentMovementDirection != 0f && currentMovementDirection != lastMovementDirection) {
            playerTargetRotationY = if (currentMovementDirection < 0f) 180f else 0f
            lastMovementDirection = currentMovementDirection
        }

        // Smoothly interpolate current rotation towards target rotation
        updatePlayerRotation(deltaTime)

        // Update player model position if moved
        if (moved) {
            playerPosition.y = 2f
            updatePlayerBounds()
        }

        return moved
    }

    fun placePlayer(ray: Ray, gameBlocks: Array<GameBlock>): Boolean {
        val intersection = Vector3()
        val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            // Snap to grid
            val gridX = floor(intersection.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize / 2

            // Check if the new position would cause a collision
            if (canMoveTo(gridX, 2f, gridZ, gameBlocks)) {
                playerPosition.set(gridX, 2f, gridZ)
                updatePlayerBounds()
                println("Player placed at: $gridX, 2, $gridZ")
                return true
            } else {
                println("Cannot place player here - collision with block")
                return false
            }
        }
        return false
    }

    private fun canMoveTo(x: Float, y: Float, z: Float, gameBlocks: Array<GameBlock>): Boolean {
        // Create a temporary bounding box for the new position
        val horizontalShrink = 0.3f
        val tempBounds = BoundingBox()
        tempBounds.set(
            Vector3(x - (playerSize.x / 2 - horizontalShrink), y - playerSize.y / 2, z - (playerSize.z / 2 - horizontalShrink)),
            Vector3(x + (playerSize.x / 2 - horizontalShrink), y + playerSize.y / 2, z + (playerSize.z / 2 - horizontalShrink))
        )

        for (gameBlock in gameBlocks) {
            val blockBounds = BoundingBox()
            blockBounds.set(
                Vector3(gameBlock.position.x - blockSize / 2, gameBlock.position.y - blockSize / 2, gameBlock.position.z - blockSize / 2),
                Vector3(gameBlock.position.x + blockSize / 2, gameBlock.position.y + blockSize / 2, gameBlock.position.z + blockSize / 2)
            )

            // Check if the temporary player bounds intersect with the block bounds
            if (tempBounds.intersects(blockBounds)) {
                // Additional check: if player is standing on top of the block, allow movement
                val playerBottom = tempBounds.min.y
                val blockTop = blockBounds.max.y
                val tolerance = 0.5f // Small tolerance for floating point precision

                // If player's bottom is at or above the block's top (standing on it), allow movement
                if (playerBottom >= blockTop - tolerance) {
                    continue // Skip this collision, player is standing on the block
                }

                return false // Real collision detected
            }
        }

        return true // No collision
    }

    private fun updatePlayerBounds() {
        playerBounds.set(
            Vector3(playerPosition.x - playerSize.x / 2, playerPosition.y - playerSize.y / 2, playerPosition.z - playerSize.z / 2),
            Vector3(playerPosition.x + playerSize.x / 2, playerPosition.y + playerSize.y / 2, playerPosition.z + playerSize.z / 2)
        )
    }

    private fun updatePlayerRotation(deltaTime: Float) {
        // Calculate the shortest rotation path
        var rotationDifference = playerTargetRotationY - playerCurrentRotationY
        if (rotationDifference > 180f) rotationDifference -= 360f
        else if (rotationDifference < -180f) rotationDifference += 360f

        if (kotlin.math.abs(rotationDifference) > 1f) {
            val rotationStep = rotationSpeed * deltaTime
            if (rotationDifference > 0f) playerCurrentRotationY += kotlin.math.min(rotationStep, rotationDifference)
            else playerCurrentRotationY += kotlin.math.max(-rotationStep, rotationDifference)
            if (playerCurrentRotationY >= 360f) playerCurrentRotationY -= 360f
            else if (playerCurrentRotationY < 0f) playerCurrentRotationY += 360f
        } else {
            // Snap to target if close enough
            playerCurrentRotationY = playerTargetRotationY
        }
    }

    fun update(deltaTime: Float) {
        updatePlayerTransform()
    }

    private fun updatePlayerTransform() {
        // Reset transform matrix
        playerInstance.transform.idt()

        // Set position
        playerInstance.transform.setTranslation(playerPosition)

        // Apply Y-axis rotation for Paper Mario effect
        playerInstance.transform.rotate(Vector3.Y, playerCurrentRotationY)
    }

    fun render(camera: Camera, environment: Environment) {
        // Set the environment for the billboard shader so it knows about the lights
        billboardShaderProvider.setEnvironment(environment)

        // Render using the custom billboard model batch with proper lighting
        billboardModelBatch.begin(camera) // Use the passed camera directly
        billboardModelBatch.render(playerInstance, environment)
        billboardModelBatch.end()
    }

    fun getPosition(): Vector3 = Vector3(playerPosition)

    fun dispose() {
        playerModel.dispose()
        playerTexture.dispose()
        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()
    }
}
