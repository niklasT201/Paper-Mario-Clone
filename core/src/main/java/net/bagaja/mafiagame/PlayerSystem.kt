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
    private lateinit var idleTexture: Texture
    private lateinit var walkingTextures: Array<Texture>
    private lateinit var playerModel: Model
    private lateinit var playerInstance: ModelInstance
    private lateinit var idleMaterial: Material
    private lateinit var walkingMaterials: Array<Material>

    // Animation system
    private var currentAnimationFrame = -1 // -1 = idle, 0-2 = walking frames
    private var animationTimer = 0f
    private val animationFrameDuration = 0.15f // Time per frame in seconds (adjust for speed)
    private var isWalking = false

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
        loadTextures()
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

    private fun loadTextures() {
        // Load idle texture (your original pig character)
        idleTexture = Texture(Gdx.files.internal("textures/player/pig_character.png"))

        // Load walking animation frames
        walkingTextures = Array()
        walkingTextures.add(Texture(Gdx.files.internal("textures/player/animations/walking/walking_anim_1.png")))
        walkingTextures.add(Texture(Gdx.files.internal("textures/player/animations/walking/walking_anim_2.png")))
        walkingTextures.add(Texture(Gdx.files.internal("textures/player/animations/walking/walking_anim_3.png")))

        // Create materials for each texture
        idleMaterial = Material(
            TextureAttribute.createDiffuse(idleTexture),
            com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
            com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.createCullFace(GL20.GL_NONE)
        )

        walkingMaterials = Array()
        for (texture in walkingTextures) {
            val material = Material(
                TextureAttribute.createDiffuse(texture),
                com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.createCullFace(GL20.GL_NONE)
            )
            walkingMaterials.add(material)
        }
    }

    private fun setupPlayerModel() {
        val modelBuilder = ModelBuilder()

        // Create a 3D plane/quad for the player (billboard) - keep original size for idle
        val playerSizeVisual = 4f // Keep original size for idle
        playerModel = modelBuilder.createRect(
            -playerSizeVisual / 2, -playerSizeVisual / 2, 0f,
            playerSizeVisual / 2, -playerSizeVisual / 2, 0f,
            playerSizeVisual / 2, playerSizeVisual / 2, 0f,
            -playerSizeVisual / 2, playerSizeVisual / 2, 0f,
            0f, 0f, 1f,
            idleMaterial, // Start with idle material
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
        )

        // Create player instance
        playerInstance = ModelInstance(playerModel)
        playerInstance.userData = "player"
        updatePlayerTransform()
    }

    fun handleMovement(deltaTime: Float, gameBlocks: Array<GameBlock>): Boolean {
        var moved = false
        var currentMovementDirection = 0f
        var isMovingHorizontally = false

        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            val newX = playerPosition.x - playerSpeed * deltaTime
            if (canMoveTo(newX, playerPosition.y, playerPosition.z, gameBlocks)) {
                playerPosition.x = newX
                moved = true
                currentMovementDirection = -1f
                isMovingHorizontally = true
            }
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            val newX = playerPosition.x + playerSpeed * deltaTime
            if (canMoveTo(newX, playerPosition.y, playerPosition.z, gameBlocks)) {
                playerPosition.x = newX
                moved = true
                currentMovementDirection = 1f
                isMovingHorizontally = true
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

        // Update walking state - only trigger walking animation for horizontal movement
        isWalking = isMovingHorizontally

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

    private fun updateAnimation(deltaTime: Float) {
        if (isWalking) {
            // If we just started walking, initialize the animation frame and resize model
            if (currentAnimationFrame == -1) {
                currentAnimationFrame = 0
                animationTimer = 0f
                // Resize model for walking animations
                resizePlayerModel(3.2f) // Smaller size for walking animations
            }

            // Update animation timer
            animationTimer += deltaTime

            // Check if it's time to switch to the next frame
            if (animationTimer >= animationFrameDuration) {
                animationTimer = 0f
                currentAnimationFrame = (currentAnimationFrame + 1) % walkingTextures.size

                // Update the model instance material to use the current walking frame
                if (playerInstance.materials.size > 0) {
                    val material = playerInstance.materials.first()
                    material.clear()
                    material.set(TextureAttribute.createDiffuse(walkingTextures.get(currentAnimationFrame)))
                    material.set(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA))
                    material.set(com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.createCullFace(GL20.GL_NONE))
                }

                println("Animation frame: $currentAnimationFrame") // Debug output
            }
        } else {
            // Player is idle, reset to idle material and animation frame
            if (currentAnimationFrame != -1) { // Only update if we were previously walking
                currentAnimationFrame = -1 // Use -1 to indicate idle state
                animationTimer = 0f

                // Resize model back to original size for idle
                resizePlayerModel(4f) // Original size for idle

                // Update the material to use idle texture
                if (playerInstance.materials.size > 0) {
                    val material = playerInstance.materials.first()
                    material.clear()
                    material.set(TextureAttribute.createDiffuse(idleTexture))
                    material.set(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA))
                    material.set(com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.createCullFace(GL20.GL_NONE))
                }

                println("Player idle") // Debug output
            }
        }
    }

    private fun resizePlayerModel(newSize: Float) {
        // Dispose old model
        playerModel.dispose()

        // Create new model with different size
        val modelBuilder = ModelBuilder()
        playerModel = modelBuilder.createRect(
            -newSize / 2, -newSize / 2, 0f,
            newSize / 2, -newSize / 2, 0f,
            newSize / 2, newSize / 2, 0f,
            -newSize / 2, newSize / 2, 0f,
            0f, 0f, 1f,
            if (currentAnimationFrame == -1) idleMaterial else walkingMaterials.get(currentAnimationFrame),
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
        )

        // Update the model instance to use the new model
        playerInstance = ModelInstance(playerModel)
        playerInstance.userData = "player"
        updatePlayerTransform()
    }

    fun update(deltaTime: Float) {
        updateAnimation(deltaTime)
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
        idleTexture.dispose()

        // Dispose walking animation textures
        for (texture in walkingTextures) {
            texture.dispose()
        }

        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()
    }
}
