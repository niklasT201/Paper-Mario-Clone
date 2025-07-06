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
    private val FALL_SPEED = 25f
    private val MAX_STEP_HEIGHT = 4.0f
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

    var isDriving = false
        private set
    private var drivingCar: GameCar? = null
    private val carSpeed = 20f // Speed is still relevant

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

        // Create player bounds for this check
        val tempPlayerBounds = BoundingBox()
        tempPlayerBounds.set(
            Vector3(x - (playerSize.x / 2 - horizontalShrink), y - playerSize.y / 2, z - (playerSize.z / 2 - horizontalShrink)),
            Vector3(x + (playerSize.x / 2 - horizontalShrink), y + playerSize.y / 2, z + (playerSize.z / 2 - horizontalShrink))
        )

        for (gameBlock in gameBlocks) {
            if (!gameBlock.blockType.hasCollision) {
                continue
            }
            // We pass the *potential* future player bounds to the block's collision method.
            if (gameBlock.collidesWith(tempPlayerBounds)) {
                // Check if player is just standing on top. This is a simple check that can be improved.
                val playerBottom = tempPlayerBounds.min.y
                val blockAABB = gameBlock.getBoundingBox(blockSize, BoundingBox())
                val blockTop = blockAABB.max.y
                val tolerance = 0.1f

                if (playerBottom >= blockTop - tolerance) {
                    // Player is standing on the block, not colliding with its side.
                    continue
                }

                // If it's not a "standing on top" situation, it's a real collision.
                println("Player collided with block of shape: ${gameBlock.shape.name}")
                return false // Collision detected
            }
        }

        // Create smaller collision bounds specifically for doors
        val doorBounds = BoundingBox()
        doorBounds.set(
            Vector3(x - (playerSize.x / 2 - doorHorizontalShrink), y - playerSize.y / 2, z - (playerSize.z / 2 - doorHorizontalShrink)),
            Vector3(x + (playerSize.x / 2 - doorHorizontalShrink), y + playerSize.y / 2, z + (playerSize.z / 2 - doorHorizontalShrink))
        )

        // Check house collisions
        for (house in gameHouses) {
            if (house.houseType == HouseType.STAIR) {
                continue
            } else {
                if (house.collidesWithMesh(tempPlayerBounds)) {
                    return false // Collision with house detected
                }
            }
        }

        // Check interior collisions - use different bounds based on interior type
        for (interior in gameInteriors) {
            if (!interior.interiorType.hasCollision) continue

            if (interior.interiorType.is3D) {
                // For 3D objects, check if it's a door and use appropriate bounds
                val boundsToUse = if (interior.interiorType == InteriorType.DOOR_INTERIOR) doorBounds else tempPlayerBounds
                if (interior.collidesWithMesh(boundsToUse)) {
                    return false
                }
            } else {
                val playerRadius = (playerSize.x / 2f) - (if (interior.interiorType == InteriorType.DOOR_INTERIOR) doorHorizontalShrink else horizontalShrink)
                if (interior.collidesWithPlayer2D(Vector3(x, y, z), playerRadius)) {
                    return false
                }
            }
        }

        return true // No collision detected
    }

    fun getControlledEntityPosition(): Vector3 {
        return if (isDriving && drivingCar != null) {
            drivingCar!!.position
        } else {
            playerPosition
        }
    }

    fun enterCar(car: GameCar) {
        if (isDriving) return // Already driving, can't enter another car

        // Check if the car is locked before entering
        if (car.isLocked) {
            println("This car is locked.")
            // You could add a UI message or sound effect here
            return
        }

        isDriving = true
        drivingCar = car
        car.modelInstance.userData = "player"

        // Hide the player by setting its position to the car's position
        playerPosition.set(car.position)
        println("Player entered car ${car.carType.displayName}")
    }

    fun exitCar(gameBlocks: Array<GameBlock>, gameHouses: Array<GameHouse>, gameInteriors: Array<GameInterior>) {
        if (!isDriving || drivingCar == null) return

        val car = drivingCar!!
        car.modelInstance.userData = null

        // Calculate a safe exit spot
        val exitOffset = Vector3(-5f, 0f, 0f) // Offset from car's local center
        exitOffset.rotate(Vector3.Y, car.direction) // Rotate the offset to match the car's direction
        val exitPosition = Vector3(car.position).add(exitOffset)

        val safeY = calculateSafeYPositionForExit(exitPosition.x, exitPosition.z, car.position.y, gameBlocks, gameHouses, gameInteriors)
        val finalExitPos = Vector3(exitPosition.x, safeY, exitPosition.z)

        // Check if the exit spot is clear for the player to stand
        if (canMoveToWithDoorCollision(finalExitPos.x, finalExitPos.y, finalExitPos.z, gameBlocks, gameHouses, gameInteriors)) {
            setPosition(finalExitPos) // Use the existing setPosition method
            println("Player exited car. Placed at $finalExitPos")
            isDriving = false
            drivingCar = null
        } else {
            println("Cannot exit car, path is blocked.")
            car.modelInstance.userData = "player"
        }
    }

    private fun calculateSafeYPositionForExit(x: Float, z: Float, carY: Float, gameBlocks: Array<GameBlock>, gameHouses: Array<GameHouse>, gameInteriors: Array<GameInterior>): Float {
        // Find the highest block/surface that the player can stand on at position (x, z)
        var highestSupportY = 0f // Ground level
        var foundSupport = false

        // Create a small area around the player position to check for support
        val checkRadius = playerSize.x / 2f

        // Check blocks - allow standing on any block at this position
        for (gameBlock in gameBlocks) {
            if (!gameBlock.blockType.hasCollision) {
                continue
            }
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

                // For car exit, consider ANY block that could support the player
                // Check if this block is at or below the car's level (within reasonable range)
                if (blockTop <= carY + 2f && blockTop > highestSupportY) {
                    highestSupportY = blockTop
                    foundSupport = true
                }
            }
        }

        // Handle stairs (similar to original logic but without the "already close" requirement)
        for (house in gameHouses) {
            if (house.houseType == HouseType.STAIR) {
                val supportHeight = findStairSupportHeightForExit(house, x, z, carY)
                if (supportHeight > highestSupportY && supportHeight <= carY + 2f) {
                    highestSupportY = supportHeight
                    foundSupport = true
                }
            }
        }

        // Check 3D interiors (similar to original logic but without the "already close" requirement)
        for (interior in gameInteriors) {
            if (!interior.interiorType.is3D || !interior.interiorType.hasCollision) continue

            val objectBounds = interior.instance.calculateBoundingBox(BoundingBox())
            if (x >= objectBounds.min.x && x <= objectBounds.max.x &&
                z >= objectBounds.min.z && z <= objectBounds.max.z) {

                val interiorTop = objectBounds.max.y
                if (interiorTop <= carY + 2f && interiorTop > highestSupportY) {
                    highestSupportY = interiorTop
                    foundSupport = true
                }
            }
        }

        // Calculate where the player should be placed
        val surfaceMargin = 0.05f
        val targetY = highestSupportY + playerSize.y / 2f + surfaceMargin

        return if (foundSupport) targetY else (0f + playerSize.y / 2f) // Ground level if no support
    }

    // Helper function for stair support during car exit
    private fun findStairSupportHeightForExit(house: GameHouse, x: Float, z: Float, maxHeight: Float): Float {
        val checkRadius = playerSize.x / 2f
        val stepSize = 0.05f

        // Check from maxHeight downward to find the stair surface
        for (checkHeight in generateSequence(maxHeight) { it - stepSize }.takeWhile { it >= 0f }) {
            val testBounds = BoundingBox()
            testBounds.set(
                Vector3(x - checkRadius, checkHeight - playerSize.y / 2f, z - checkRadius),
                Vector3(x + checkRadius, checkHeight + playerSize.y / 2f, z + checkRadius)
            )

            if (!house.collidesWithMesh(testBounds)) {
                // Found a height where we don't collide - this is where the player can stand
                return checkHeight - playerSize.y / 2f + 0.05f
            }
        }

        return 0f // Ground level if no suitable height found
    }

    fun handleMovement(deltaTime: Float, sceneManager: SceneManager, gameBlocks: Array<GameBlock>, gameHouses: Array<GameHouse>, gameInteriors: Array<GameInterior>, allCars: Array<GameCar>): Boolean {
        return if (isDriving) {
            handleCarMovement(deltaTime, sceneManager, gameBlocks, gameHouses, gameInteriors, allCars)
        } else {
            handlePlayerOnFootMovement(deltaTime, sceneManager, gameBlocks, gameHouses, gameInteriors)
        }
    }

    private fun handleCarMovement(deltaTime: Float, sceneManager: SceneManager, gameBlocks: Array<GameBlock>, gameHouses: Array<GameHouse>, gameInteriors: Array<GameInterior>, allCars: Array<GameCar>): Boolean {
        val car = drivingCar ?: return false
        var moved = false

        val moveAmount = carSpeed * deltaTime

        // 1. Calculate desired horizontal movement
        var deltaX = 0f
        var deltaZ = 0f
        var horizontalDirection = 0f

        // Check for A or D key presses
        val isMovingHorizontally = Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.D)

        if (Gdx.input.isKeyPressed(Input.Keys.A)) { deltaX -= moveAmount; horizontalDirection = 1f }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) { deltaX += moveAmount; horizontalDirection = -1f }
        if (Gdx.input.isKeyPressed(Input.Keys.W)) { deltaZ -= moveAmount }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) { deltaZ += moveAmount }

        car.updateFlipAnimation(horizontalDirection, deltaTime)

        // Tell the car to play the correct animation based on input
        car.setDrivingAnimationState(isMovingHorizontally)

        // 2. Determine potential next position and apply physics
        val nextX = car.position.x + deltaX
        val nextZ = car.position.z + deltaZ

        // Find the ground at the potential next spot
        val playerFootY = playerPosition.y - (playerSize.y / 2f)
        val supportY = sceneManager.findHighestSupportYForCar(nextX, nextZ, car.carType.width / 2f, blockSize)

        val carBottomY = car.position.y
        val effectiveSupportY = if (supportY - carBottomY <= MAX_STEP_HEIGHT) {
            // The ground is within stepping range, so we can use it.
            supportY
        } else {
            // The ground is too high (it's a wall), so we maintain our current Y-level for the check.
            carBottomY
        }

        // Apply Gravity
        val fallY = car.position.y - FALL_SPEED * deltaTime
        val nextY = kotlin.math.max(effectiveSupportY, fallY) // Car is on ground, stepping up, or falling.

        // 3. Check for collisions and finalize movement
        if (deltaX != 0f || deltaZ != 0f || kotlin.math.abs(nextY - car.position.y) > 0.01f) {
            val newPos = Vector3(nextX, nextY, nextZ)
            if (canCarMoveTo(newPos, car, gameBlocks, gameHouses, gameInteriors, allCars)) {
                car.position.set(newPos)
                moved = true
            }
        }

        // 4. Update the car's 3D model transform (this now includes flip animation)
        car.updateTransform()

        return moved
    }

    private fun canCarMoveTo(newPosition: Vector3, thisCar: GameCar, gameBlocks: Array<GameBlock>, gameHouses: Array<GameHouse>, gameInteriors: Array<GameInterior>, allCars: Array<GameCar>): Boolean {
        // Create a temporary car with the new position to get accurate bounding box
        val carBounds = thisCar.getBoundingBox(newPosition)
        val tempBlockBounds = BoundingBox() // Create a temporary box to reuse

        // Check collision with blocks - BUT allow driving ON TOP of blocks
        for (block in gameBlocks) {
            if (!block.blockType.hasCollision) {
                continue
            }
            // We need the block's standard bounding box, not a hypothetical one.
            val blockBounds = block.getBoundingBox(blockSize, tempBlockBounds)

            if (carBounds.intersects(blockBounds)) {
                // Check if the car is actually ON TOP of the block (not intersecting)
                val carBottom = carBounds.min.y
                val blockTop = blockBounds.max.y
                val tolerance = 0.5f // Small tolerance for "on top" detection

                // If car bottom is above block top (minus tolerance), it's driving on top - allow it
                if (carBottom >= blockTop - tolerance) {
                    continue // This is fine - car is on top of block
                }

                // Otherwise, it's a real collision
                return false
            }
        }

        // Check collision with houses
        for (house in gameHouses) {
            if (house.collidesWithMesh(carBounds)) {
                return false
            }
        }

        // Check collision with interiors that have collision
        for (interior in gameInteriors) {
            if (interior.interiorType.hasCollision && interior.collidesWithMesh(carBounds)) {
                return false
            }
        }

        // Check collision with other cars
        for (otherCar in allCars) {
            // Get the other car's bounding box at its actual position
            if (otherCar.id != thisCar.id && otherCar.getBoundingBox().intersects(carBounds)) {
                return false
            }
        }

        return true
    }


    private fun handlePlayerOnFootMovement(deltaTime: Float, sceneManager: SceneManager, gameBlocks: Array<GameBlock>, gameHouses: Array<GameHouse>, gameInteriors: Array<GameInterior>): Boolean {
        var moved = false

        // Reset movement flag
        isMoving = false
        var currentMovementDirection = 0f

        // 1. Calculate desired horizontal movement
        var deltaX = 0f
        var deltaZ = 0f
        if (Gdx.input.isKeyPressed(Input.Keys.A)) { deltaX -= playerSpeed * deltaTime; currentMovementDirection = -1f }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) { deltaX += playerSpeed * deltaTime; currentMovementDirection = 1f }
        if (Gdx.input.isKeyPressed(Input.Keys.W)) { deltaZ -= playerSpeed * deltaTime }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) { deltaZ += playerSpeed * deltaTime }

        val nextX = playerPosition.x + deltaX
        val nextZ = playerPosition.z + deltaZ

        // 2. Apply Gravity and Step-Up Logic
        val playerFootY = playerPosition.y - (playerSize.y / 2f)
        val supportY = sceneManager.findHighestSupportY(nextX, nextZ, playerFootY, playerSize.x / 2f, blockSize)

        val effectiveSupportY = if (supportY - playerFootY <= MAX_STEP_HEIGHT) {
            // Step is valid, use the new ground height.
            supportY
        } else {
            // Step is too high (a wall), maintain current Y-level.
            playerFootY
        }

        val targetY = effectiveSupportY + (playerSize.y / 2f) // Player's origin is in its center
        val fallY = playerPosition.y - FALL_SPEED * deltaTime
        val nextY = kotlin.math.max(targetY, fallY)

        // 3. Check for collisions before moving
        if (canMoveToWithDoorCollision(nextX, nextY, nextZ, gameBlocks, gameHouses, gameInteriors)) {
            if (deltaX != 0f || deltaZ != 0f || kotlin.math.abs(nextY - playerPosition.y) > 0.01f) {
                playerPosition.set(nextX, nextY, nextZ)
                moved = true
                isMoving = deltaX != 0f || deltaZ != 0f
            }
        }

        // Handle animation and rotation
        if (isMoving && !lastIsMoving) animationSystem.playAnimation("walking")
        else if (!isMoving && lastIsMoving) animationSystem.playAnimation("idle")
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
                if (!gameBlock.blockType.hasCollision) {
                    continue
                }
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
        // If the player is driving, do not render their 2D model.
        if (isDriving) {
            return
        }

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

    fun dispose() {
        playerModel.dispose()
        animationSystem.dispose()
        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()
    }
}
