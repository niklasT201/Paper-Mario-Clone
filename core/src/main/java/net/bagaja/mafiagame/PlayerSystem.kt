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

        // Updated configuration for better billboard lighting
        billboardShaderProvider.setBillboardLightingStrength(0.9f)
        billboardShaderProvider.setMinLightLevel(0.3f)
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
        playerInstance.userData = "player"
        updatePlayerTransform()
    }

    fun handleMovement(deltaTime: Float, gameBlocks: Array<GameBlock>, gameHouses: Array<GameHouse>): Boolean {
        var moved = false
        var currentMovementDirection = 0f

        // Store original position for rollback if needed
        val originalX = playerPosition.x
        val originalZ = playerPosition.z
        val originalY = playerPosition.y

        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            val newX = playerPosition.x - playerSpeed * deltaTime
            // Try to move horizontally first, then adjust Y if needed
            val adjustedY = calculateSafeYPosition(newX, playerPosition.z, originalY, gameBlocks, gameHouses)
            if (canMoveTo(newX, adjustedY, playerPosition.z, gameBlocks, gameHouses)) {
                playerPosition.x = newX
                playerPosition.y = adjustedY
                moved = true
                currentMovementDirection = -1f
            }
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            val newX = playerPosition.x + playerSpeed * deltaTime
            val adjustedY = calculateSafeYPosition(newX, playerPosition.z, originalY, gameBlocks, gameHouses)
            if (canMoveTo(newX, adjustedY, playerPosition.z, gameBlocks, gameHouses)) {
                playerPosition.x = newX
                playerPosition.y = adjustedY
                moved = true
                currentMovementDirection = 1f
            }
        }
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            val newZ = playerPosition.z - playerSpeed * deltaTime
            val adjustedY = calculateSafeYPosition(playerPosition.x, newZ, originalY, gameBlocks, gameHouses)
            if (canMoveTo(playerPosition.x, adjustedY, newZ, gameBlocks, gameHouses)) {
                playerPosition.z = newZ
                playerPosition.y = adjustedY
                moved = true
            }
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            val newZ = playerPosition.z + playerSpeed * deltaTime
            val adjustedY = calculateSafeYPosition(playerPosition.x, newZ, originalY, gameBlocks, gameHouses)
            if (canMoveTo(playerPosition.x, adjustedY, newZ, gameBlocks, gameHouses)) {
                playerPosition.z = newZ
                playerPosition.y = adjustedY
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

        // Update player bounds if moved
        if (moved) {
            updatePlayerBounds()
        }

        return moved
    }

    private fun calculateSafeYPosition(x: Float, z: Float, currentY: Float, gameBlocks: Array<GameBlock>, gameHouses: Array<GameHouse>): Float {
        // Find the highest block that the player would be standing on at position (x, z)
        var highestBlockY = 0f // Ground level
        var foundSupportingBlock = false

        // Create a small area around the player position to check for blocks
        val checkRadius = playerSize.x / 2f

        // Check blocks first
        for (gameBlock in gameBlocks) {
            val blockCenterX = gameBlock.position.x
            val blockCenterZ = gameBlock.position.z
            val blockHalfSize = blockSize / 2f

            // Check if the player's position overlaps with this block horizontally
            val playerLeft = x - checkRadius
            val playerRight = x + checkRadius
            val playerFront = z - checkRadius
            val playerBack = z + checkRadius

            val blockLeft = blockCenterX - blockHalfSize
            val blockRight = blockCenterX + blockHalfSize
            val blockFront = blockCenterZ - blockHalfSize
            val blockBack = blockCenterZ + blockHalfSize

            // Check for horizontal overlap
            val horizontalOverlap = !(playerRight < blockLeft || playerLeft > blockRight ||
                playerBack < blockFront || playerFront > blockBack)

            if (horizontalOverlap) {
                val blockHeight = blockSize * gameBlock.blockType.height
                val blockTop = gameBlock.position.y + blockHeight / 2f

                if (blockTop <= currentY + playerSize.y / 2f + blockSize) {
                    if (blockTop > highestBlockY) {
                        highestBlockY = blockTop
                        foundSupportingBlock = true
                    }
                }
            }
        }

        // Handle stairs by finding the appropriate step height
        for (house in gameHouses) {
            if (house.houseType == HouseType.STAIR) {
                // Create test bounds at current position
                val testBounds = BoundingBox()
                testBounds.set(
                    Vector3(x - checkRadius, currentY - playerSize.y / 2f, z - checkRadius),
                    Vector3(x + checkRadius, currentY + playerSize.y / 2f, z + checkRadius)
                )

                // Check if we're colliding with the stair at current height
                if (house.collidesWithMesh(testBounds)) {
                    // Calculate the appropriate step height based on stair geometry
                    val stairStepHeight = findStairStepHeight(house, x, z, currentY)

                    if (stairStepHeight > highestBlockY) {
                        highestBlockY = stairStepHeight
                        foundSupportingBlock = true
                    }
                } else {
                    // Not colliding with stair - check if we're standing on top of it
                    val supportHeight = findStairSupportHeight(house, x, z, currentY)
                    if (supportHeight > highestBlockY) {
                        highestBlockY = supportHeight
                        foundSupportingBlock = true
                    }
                }
            }
        }

        // Calculate where the player should be
        val targetY = highestBlockY + playerSize.y / 2f

        // If no supporting block found and we're above ground, gradually fall to ground
        if (!foundSupportingBlock && currentY > playerSize.y / 2f) {
            val fallSpeed = 20f
            val groundY = 0f + playerSize.y / 2f
            val fallingY = currentY - fallSpeed * Gdx.graphics.deltaTime
            return kotlin.math.max(fallingY, groundY)
        }

        // Smooth transition to prevent shaking - only move if the difference is significant
        val heightDifference = kotlin.math.abs(targetY - currentY)
        val smoothingThreshold = 0.1f // Don't adjust for very small differences

        if (heightDifference < smoothingThreshold) {
            return currentY // Stay at current position to prevent micro-adjustments
        }

        // Limit step height to prevent teleporting
        val maxStepHeight = 1.5f
        if (targetY > currentY + maxStepHeight) {
            return currentY + maxStepHeight
        }

        // Smooth interpolation for stepping up/down
        val lerpSpeed = 8f // Adjust this value to control step smoothness
        return currentY + (targetY - currentY) * lerpSpeed * Gdx.graphics.deltaTime
    }

    private fun findStairStepHeight(house: GameHouse, x: Float, z: Float, currentY: Float): Float {
        val checkRadius = playerSize.x / 2f
        val stepSize = 0.2f // Smaller step increments for more precision
        val maxStepUp = 3f

        // Try different heights to find where we stop colliding
        for (stepHeight in generateSequence(stepSize) { it + stepSize }.takeWhile { it <= maxStepUp }) {
            val testYPosition = currentY + stepHeight
            val testBounds = BoundingBox()
            testBounds.set(
                Vector3(x - checkRadius, testYPosition - playerSize.y / 2f, z - checkRadius),
                Vector3(x + checkRadius, testYPosition + playerSize.y / 2f, z + checkRadius)
            )

            // If we no longer collide at this height, this is our step height
            if (!house.collidesWithMesh(testBounds)) {
                return testYPosition - playerSize.y / 2f
            }
        }

        // If we still collide after max step up, return current position
        return currentY - playerSize.y / 2f
    }

    private fun findStairSupportHeight(house: GameHouse, x: Float, z: Float, currentY: Float): Float {
        val checkRadius = playerSize.x / 2f

        // Check downward from current position to find the stair surface
        for (checkHeight in generateSequence(currentY) { it - 0.1f }.takeWhile { it >= 0f }) {
            val testBounds = BoundingBox()
            testBounds.set(
                Vector3(x - checkRadius, checkHeight - playerSize.y / 2f, z - checkRadius),
                Vector3(x + checkRadius, checkHeight + playerSize.y / 2f, z + checkRadius)
            )

            // If we start colliding at this height, the surface is just above
            if (house.collidesWithMesh(testBounds)) {
                return checkHeight + 0.1f // Surface is slightly above collision point
            }
        }

        return 0f // Ground level if no collision found
    }

    fun placePlayer(ray: Ray, gameBlocks: Array<GameBlock>, gameHouses: Array<GameHouse>): Boolean {
        val intersection = Vector3()
        val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            // Snap to grid
            val gridX = floor(intersection.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize / 2

            // Find the highest block at this X,Z position
            var highestBlockY = 0f // Ground level

            for (gameBlock in gameBlocks) {
                val blockCenterX = gameBlock.position.x
                val blockCenterZ = gameBlock.position.z

                // Check if this block is at the same grid position
                val tolerance = blockSize / 4f // Allow some tolerance for floating point precision
                if (kotlin.math.abs(blockCenterX - gridX) < tolerance &&
                    kotlin.math.abs(blockCenterZ - gridZ) < tolerance) {

                    // Calculate the top of this block using ACTUAL block height
                    val blockHeight = blockSize * gameBlock.blockType.height // Fixed: now accounts for 0.8 height!
                    val blockTop = gameBlock.position.y + blockHeight / 2f

                    if (blockTop > highestBlockY) {
                        highestBlockY = blockTop
                    }
                }
            }

            // Place player on top of the highest block (or ground if no blocks)
            val playerY = highestBlockY + playerSize.y / 2f // Position player so their bottom is on the block top

            // Check if the new position would cause a collision with other objects
            if (canMoveTo(gridX, playerY, gridZ, gameBlocks, gameHouses)) {
                playerPosition.set(gridX, playerY, gridZ)
                updatePlayerBounds()
                println("Player placed at: $gridX, $playerY, $gridZ (block height: $highestBlockY)")
                return true
            } else {
                println("Cannot place player here - collision with block or house")
                return false
            }
        }
        return false
    }

    private fun canMoveTo(x: Float, y: Float, z: Float, gameBlocks: Array<GameBlock>, gameHouses: Array<GameHouse>): Boolean {
        // Create a temporary bounding box for the new position
        val horizontalShrink = 0.2f // Reduced shrink for more accurate collision
        val tempBounds = BoundingBox()
        tempBounds.set(
            Vector3(x - (playerSize.x / 2 - horizontalShrink), y - playerSize.y / 2, z - (playerSize.z / 2 - horizontalShrink)),
            Vector3(x + (playerSize.x / 2 - horizontalShrink), y + playerSize.y / 2, z + (playerSize.z / 2 - horizontalShrink))
        )

        // Check block collisions
        for (gameBlock in gameBlocks) {
            // Calculate block bounds using ACTUAL block height
            val blockHeight = blockSize * gameBlock.blockType.height
            val blockBounds = BoundingBox()
            blockBounds.set(
                Vector3(gameBlock.position.x - blockSize / 2, gameBlock.position.y - blockHeight / 2, gameBlock.position.z - blockSize / 2),
                Vector3(gameBlock.position.x + blockSize / 2, gameBlock.position.y + blockHeight / 2, gameBlock.position.z + blockSize / 2)
            )

            // Check if the temporary player bounds intersect with the block bounds
            if (tempBounds.intersects(blockBounds)) {
                // Check if player is standing on top of the block
                val playerBottom = tempBounds.min.y
                val blockTop = blockBounds.max.y
                val tolerance = 0.1f // Small tolerance for floating point precision

                // Player is on top if their bottom is at or very slightly above the block top
                if (playerBottom >= blockTop - tolerance) {
                    continue // Allow movement - player is standing on top
                }
                return false // Block collision detected
            }
        }

        // Check house collisions
        for (house in gameHouses) {
            if (house.houseType == HouseType.STAIR) {
                continue
            } else {
                // For non-stair houses, use normal mesh collision (blocks movement)
                if (house.collidesWithMesh(tempBounds)) {
                    return false // Collision with house detected
                }
            }
        }

        return true // No collision with blocks or houses
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
