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
    private val playerSize = Vector3(3f, 4f, 3f) //z = thickness

    // Player rotation for Paper Mario effect
    private var playerTargetRotationY = 0f
    private var playerCurrentRotationY = 0f
    private val rotationSpeed = 360f
    private var lastMovementDirection = 0f

    // Animation system
    private lateinit var animationSystem: AnimationSystem
    private var isMoving = false
    private var lastIsMoving = false

    // Reference to block system for collision detection
    private var blockSize = 4f

    fun getPlayerBounds(): BoundingBox {
        return playerBounds
    }

    fun initialize(blockSize: Float) {
        this.blockSize = blockSize
        setupAnimationSystem()
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

    private fun setupAnimationSystem() {
        animationSystem = AnimationSystem()

        // Create walking animation
        val walkingFrames = arrayOf(
            "textures/player/animations/walking/walking_left.png",
            //"textures/player/animations/walking/walking_middle.png",
            "textures/player/animations/walking/walking_right.png",
            //"textures/player/animations/walking/walking_middle.png" // Return to middle for smooth loop
        )

        // Create walking animation with 0.15 seconds per frame (about 6.7 fps for smooth walking)
        animationSystem.createAnimation("walking", walkingFrames, 0.4f, true)

        // Create idle animation (single frame)
        val idleFrames = arrayOf("textures/player/pig_character.png")
        animationSystem.createAnimation("idle", idleFrames, 1.0f, true)

        // Start with idle animation
        animationSystem.playAnimation("idle")
    }

    private fun setupPlayerModel() {
        val modelBuilder = ModelBuilder()

        // Get initial texture from animation system
        playerTexture = animationSystem.getCurrentTexture() ?: Texture(Gdx.files.internal("textures/player/pig_character.png"))

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

    fun setPosition(newPosition: Vector3) {
        playerPosition.set(newPosition)
        updatePlayerBounds()
        println("Player position set to: $newPosition")
    }

    private fun canMoveToWithDoorCollision(x: Float, y: Float, z: Float, gameBlocks: Array<GameBlock>, gameHouses: Array<GameHouse>, gameInteriors: Array<GameInterior>): Boolean {
        val horizontalShrink = 0.2f // Normal shrink for most objects
        val doorHorizontalShrink = 0.8f // Much smaller collision box for doors

        // Create normal collision bounds for blocks and houses
        val normalBounds = BoundingBox()
        normalBounds.set(
            Vector3(x - (playerSize.x / 2 - horizontalShrink), y - playerSize.y / 2, z - (playerSize.z / 2 - horizontalShrink)),
            Vector3(x + (playerSize.x / 2 - horizontalShrink), y + playerSize.y / 2, z + (playerSize.z / 2 - horizontalShrink))
        )

        // Create smaller collision bounds specifically for doors
        val doorBounds = BoundingBox()
        doorBounds.set(
            Vector3(x - (playerSize.x / 2 - doorHorizontalShrink), y - playerSize.y / 2, z - (playerSize.z / 2 - doorHorizontalShrink)),
            Vector3(x + (playerSize.x / 2 - doorHorizontalShrink), y + playerSize.y / 2, z + (playerSize.z / 2 - doorHorizontalShrink))
        )

        // Check block collisions with normal bounds
        for (gameBlock in gameBlocks) {
            val blockHeight = blockSize * gameBlock.blockType.height
            val blockBounds = BoundingBox()
            blockBounds.set(
                Vector3(gameBlock.position.x - blockSize / 2, gameBlock.position.y - blockHeight / 2, gameBlock.position.z - blockSize / 2),
                Vector3(gameBlock.position.x + blockSize / 2, gameBlock.position.y + blockHeight / 2, gameBlock.position.z + blockSize / 2)
            )

            if (normalBounds.intersects(blockBounds)) {
                val playerBottom = normalBounds.min.y
                val blockTop = blockBounds.max.y
                val tolerance = 0.1f

                if (playerBottom >= blockTop - tolerance) {
                    continue // Player is standing on top
                }
                return false // Block collision detected
            }
        }

        // Check house collisions with normal bounds
        for (house in gameHouses) {
            if (house.houseType == HouseType.STAIR) {
                continue
            } else {
                if (house.collidesWithMesh(normalBounds)) {
                    return false // Collision with house detected
                }
            }
        }

        // Check interior collisions - use different bounds based on interior type
        for (interior in gameInteriors) {
            if (!interior.interiorType.hasCollision) continue

            if (interior.interiorType.is3D) {
                // For 3D objects, check if it's a door and use appropriate bounds
                val boundsToUse = if (interior.interiorType == InteriorType.DOOR_INTERIOR) doorBounds else normalBounds

                if (interior.collidesWithMesh(boundsToUse)) {
                    return false
                }
            } else {
                // For 2D objects, use custom collision detection
                if (interior.interiorType == InteriorType.DOOR_INTERIOR) {
                    // Use smaller player radius for door collision
                    val doorPlayerRadius = (playerSize.x / 2f) - doorHorizontalShrink
                    if (interior.collidesWithPlayer2D(Vector3(x, y, z), doorPlayerRadius)) {
                        return false
                    }
                } else {
                    // Use normal player radius for other 2D objects
                    val normalPlayerRadius = (playerSize.x / 2f) - horizontalShrink
                    if (interior.collidesWithPlayer2D(Vector3(x, y, z), normalPlayerRadius)) {
                        return false
                    }
                }
            }
        }

        return true // No collision detected
    }

    fun handleMovement(deltaTime: Float, gameBlocks: Array<GameBlock>, gameHouses: Array<GameHouse>, gameInteriors: Array<GameInterior>): Boolean {
        var moved = false
        var currentMovementDirection = 0f

        // Reset movement flag
        isMoving = false

        // Store original position for rollback if needed
        val originalX = playerPosition.x
        val originalZ = playerPosition.z
        val originalY = playerPosition.y

        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            val newX = playerPosition.x - playerSpeed * deltaTime
            // Try to move horizontally first, then adjust Y if needed
            val adjustedY = calculateSafeYPosition(newX, playerPosition.z, originalY, gameBlocks, gameHouses, gameInteriors)
            if (canMoveToWithDoorCollision(newX, adjustedY, playerPosition.z, gameBlocks, gameHouses, gameInteriors)) {
                playerPosition.x = newX
                playerPosition.y = adjustedY
                moved = true
                isMoving = true
                currentMovementDirection = -1f
            }
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            val newX = playerPosition.x + playerSpeed * deltaTime
            val adjustedY = calculateSafeYPosition(newX, playerPosition.z, originalY, gameBlocks, gameHouses, gameInteriors)
            if (canMoveToWithDoorCollision(newX, adjustedY, playerPosition.z, gameBlocks, gameHouses, gameInteriors)) {
                playerPosition.x = newX
                playerPosition.y = adjustedY
                moved = true
                isMoving = true
                currentMovementDirection = 1f
            }
        }
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            val newZ = playerPosition.z - playerSpeed * deltaTime
            val adjustedY = calculateSafeYPosition(playerPosition.x, newZ, originalY, gameBlocks, gameHouses, gameInteriors)
            if (canMoveToWithDoorCollision(playerPosition.x, adjustedY, newZ, gameBlocks, gameHouses, gameInteriors)) {
                playerPosition.z = newZ
                playerPosition.y = adjustedY
                moved = true
                isMoving = true
            }
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            val newZ = playerPosition.z + playerSpeed * deltaTime
            val adjustedY = calculateSafeYPosition(playerPosition.x, newZ, originalY, gameBlocks, gameHouses, gameInteriors)
            if (canMoveToWithDoorCollision(playerPosition.x, adjustedY, newZ, gameBlocks, gameHouses, gameInteriors)) {
                playerPosition.z = newZ
                playerPosition.y = adjustedY
                moved = true
                isMoving = true
            }
        }

        // Handle animation state changes
        if (isMoving && !lastIsMoving) {
            // Started moving - play walking animation
            animationSystem.playAnimation("walking")
        } else if (!isMoving && lastIsMoving) {
            // Stopped moving - play idle animation
            animationSystem.playAnimation("idle")
        }

        lastIsMoving = isMoving

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

    private fun calculateSafeYPosition(x: Float, z: Float, currentY: Float, gameBlocks: Array<GameBlock>, gameHouses: Array<GameHouse>, gameInteriors: Array<GameInterior>): Float {
        // Find the highest block/surface that the player is standing on at position (x, z)
        var highestSupportY = 0f // Ground level
        var foundSupport = false

        // Create a small area around the player position to check for support
        val checkRadius = playerSize.x / 2f

        // Check blocks - but DON'T allow stepping up onto them
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

                // Only consider this block as support if player is already on or very close to its top
                val tolerance = 0.5f // Small tolerance for being "on" the block
                if (kotlin.math.abs(currentY - playerSize.y / 2f - blockTop) <= tolerance) {
                    if (blockTop > highestSupportY) {
                        highestSupportY = blockTop
                        foundSupport = true
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

                    if (stairStepHeight > highestSupportY) {
                        highestSupportY = stairStepHeight
                        foundSupport = true
                    }
                } else {
                    // Not colliding with stair - check if we're standing on top of it
                    val supportHeight = findStairSupportHeight(house, x, z, currentY)
                    if (supportHeight > highestSupportY) {
                        highestSupportY = supportHeight
                        foundSupport = true
                    }
                }
            }
        }

        for (interior in gameInteriors) {
            // Only solid 3D interiors can be stood on
            if (!interior.interiorType.is3D || !interior.interiorType.hasCollision) continue

            // A simplified check: is the player horizontally "over" the object?
            val objectBounds = interior.instance.calculateBoundingBox(BoundingBox())
            if (x >= objectBounds.min.x && x <= objectBounds.max.x &&
                z >= objectBounds.min.z && z <= objectBounds.max.z) {

                val interiorTop = objectBounds.max.y

                // Only consider this interior as support if the player is on or very close to its top
                val tolerance = 0.5f
                if (kotlin.math.abs(currentY - playerSize.y / 2f - interiorTop) <= tolerance) {
                    if (interiorTop > highestSupportY) {
                        highestSupportY = interiorTop
                        foundSupport = true
                    }
                }
            }
        }

        // Calculate where the player should be
        val surfaceMargin = 0.05f
        val targetY = highestSupportY + playerSize.y / 2f + surfaceMargin

        // If no supporting surface found and we're above ground, gradually fall to ground
        if (!foundSupport && currentY > playerSize.y / 2f) {
            val fallSpeed = 20f
            val groundY = 0f + playerSize.y / 2f
            val fallingY = currentY - fallSpeed * Gdx.graphics.deltaTime
            return kotlin.math.max(fallingY, groundY)
        }

        // If we found support, smoothly move to the target position
        if (foundSupport) {
            // Smooth transition to prevent shaking
            val heightDifference = kotlin.math.abs(targetY - currentY)
            val smoothingThreshold = 0.05f

            if (heightDifference < smoothingThreshold) {
                return currentY // Stay at current position to prevent micro-adjustments
            }

            // Smooth interpolation for stepping on stairs
            val lerpSpeed = 12f
            return currentY + (targetY - currentY) * lerpSpeed * Gdx.graphics.deltaTime
        }

        // Default: stay at current Y position if no support changes
        return currentY
    }

    private fun findStairStepHeight(house: GameHouse, x: Float, z: Float, currentY: Float): Float {
        val checkRadius = playerSize.x / 2f
        val stepSize = 0.1f // Even smaller step increments for more precision
        val maxStepUp = 3f

        var lastCollisionHeight = currentY - playerSize.y / 2f

        // Try different heights to find where we stop colliding
        for (stepHeight in generateSequence(0f) { it + stepSize }.takeWhile { it <= maxStepUp }) {
            val testYPosition = currentY + stepHeight
            val testBounds = BoundingBox()
            testBounds.set(
                Vector3(x - checkRadius, testYPosition - playerSize.y / 2f, z - checkRadius),
                Vector3(x + checkRadius, testYPosition + playerSize.y / 2f, z + checkRadius)
            )

            if (house.collidesWithMesh(testBounds)) {
                lastCollisionHeight = testYPosition - playerSize.y / 2f
            } else {
                // Found the first non-colliding height - this is just above the step surface
                // Add a small margin to ensure we're clearly above the step but not floating
                val surfaceHeight = lastCollisionHeight + 0.1f // Small margin above collision
                return surfaceHeight
            }
        }

        // If we still collide after max step up, return current position
        return currentY - playerSize.y / 2f
    }

    private fun findStairSupportHeight(house: GameHouse, x: Float, z: Float, currentY: Float): Float {
        val checkRadius = playerSize.x / 2f
        val stepSize = 0.05f // Smaller steps for more precision

        // Check downward from current position to find the stair surface
        var lastNonCollisionHeight = 0f

        for (checkHeight in generateSequence(currentY) { it - stepSize }.takeWhile { it >= 0f }) {
            val testBounds = BoundingBox()
            testBounds.set(
                Vector3(x - checkRadius, checkHeight - playerSize.y / 2f, z - checkRadius),
                Vector3(x + checkRadius, checkHeight + playerSize.y / 2f, z + checkRadius)
            )

            if (house.collidesWithMesh(testBounds)) {
                // Found collision - the surface is just above this point
                return lastNonCollisionHeight + 0.05f // Minimal margin above surface
            } else {
                lastNonCollisionHeight = checkHeight - playerSize.y / 2f
            }
        }

        return 0f // Ground level if no collision found
    }

    fun placePlayer(ray: Ray, gameBlocks: Array<GameBlock>, gameHouses: Array<GameHouse>, gameInteriors: Array<GameInterior>): Boolean {
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

                    val blockHeight = blockSize * gameBlock.blockType.height
                    val blockTop = gameBlock.position.y + blockHeight / 2f

                    if (blockTop > highestBlockY) {
                        highestBlockY = blockTop
                    }
                }
            }

            // Place player on top of the highest block (or ground if no blocks)
            val playerY = highestBlockY + playerSize.y / 2f // Position player so their bottom is on the block top

            // Check if the new position would cause a collision with other objects
            if (canMoveToWithDoorCollision(gridX, playerY, gridZ, gameBlocks, gameHouses, gameInteriors)) {
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
        // Update animation system
        animationSystem.update(deltaTime)

        // Update player texture if it changed
        val newTexture = animationSystem.getCurrentTexture()
        if (newTexture != null && newTexture != playerTexture) {
            updatePlayerTexture(newTexture)
            println("Updated texture to: ${animationSystem.getCurrentAnimationName()}") // Debug print
        }

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

    private fun updatePlayerTexture(newTexture: Texture) {
        // This line is fine, it just keeps track of the current texture reference.
        playerTexture = newTexture

        val instanceMaterial = playerInstance.materials.get(0)

        // Update the attribute on the correct material
        val textureAttribute = instanceMaterial.get(TextureAttribute.Diffuse) as TextureAttribute?
        textureAttribute?.textureDescription?.texture = newTexture

        // println("Updated texture for animation: ${animationSystem.getCurrentAnimationName()}")
    }

    // Add these utility methods for external animation control:
    fun playAnimation(animationName: String, restart: Boolean = false) {
        animationSystem.playAnimation(animationName, restart)
    }

    fun getCurrentAnimationName(): String? {
        return animationSystem.getCurrentAnimationName()
    }

    fun isAnimationFinished(): Boolean {
        return animationSystem.isAnimationFinished()
    }

    // Modify your dispose method:
    fun dispose() {
        playerModel.dispose()
        // Don't dispose playerTexture directly anymore - let animation system handle it
        animationSystem.dispose()
        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()
    }
}
