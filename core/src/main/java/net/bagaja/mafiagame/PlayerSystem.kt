package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Array
import kotlin.math.floor
import kotlin.random.Random

enum class HitObjectType {
    NONE,
    BLOCK,
    HOUSE,
    OBJECT,
    INTERIOR,
    ENEMY,
    NPC,
    CAR
}

data class CollisionResult(
    val type: HitObjectType,
    val hitObject: Any?,
    val hitPoint: Vector3,
    val surfaceNormal: Vector3
)

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
    private lateinit var playerBackTexture: Texture
    private var isPressingW = false
    private val isRotating: Boolean
        get() {
            return kotlin.math.abs(playerCurrentRotationY - playerTargetRotationY) > 1f
        }

    // Animation system
    private lateinit var animationSystem: AnimationSystem
    private var isMoving = false
    private var lastIsMoving = false

    // Wipe effect
    private var continuousMovementTimer = 0f
    private var wipeEffectTimer = 0f
    private val WIPE_EFFECT_INTERVAL = 0.15f

    // Reference to block system for collision detection
    private var blockSize = 4f
    private lateinit var particleSystem: ParticleSystem
    private val PARTICLE_IMPACT_OFFSET = 1.2f

    var isDriving = false
        private set
    private var drivingCar: GameCar? = null
    private val carSpeed = 20f // Speed is still relevant

    private var equippedWeapon: WeaponType = WeaponType.TOMMY_GUN
    private var weapons: List<WeaponType> = listOf(WeaponType.UNARMED, WeaponType.TOMMY_GUN, WeaponType.PISTOL)
    private var currentWeaponIndex = 1
    private var currentMagazineCount = 0

    private var isShooting = false
    private val shootingPoseDuration = 0.2f
    private var shootingPoseTimer = 0f
    private var fireRateTimer = 0f
    private var chargeTime = 0f
    private val minShotScale = 0.7f // The initial size of the particle on a quick tap
    private val maxShotScale = 2.0f // The maximum size limit for the particle
    private val chargeDurationForMaxScale = 10f

    // Caches for weapon assets to avoid loading them repeatedly
    private val poseTextures = mutableMapOf<String, Texture>()
    private val bulletModels = mutableMapOf<String, Model>()

    private val activeBullets = Array<Bullet>()
    private val tempCheckBounds = BoundingBox()

    fun getPlayerBounds(): BoundingBox {
        return playerBounds
    }

    fun initialize(blockSize: Float, particleSystem: ParticleSystem) {
        this.blockSize = blockSize
        this.particleSystem = particleSystem
        setupAnimationSystem()

        // Load weapon
        setupWeaponAssets()

        setupBillboardShader()
        setupPlayerModel()
        updatePlayerBounds()

        // Set initial weapon state
        currentMagazineCount = equippedWeapon.magazineSize
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
        playerBackTexture = Texture(Gdx.files.internal("textures/player/pig_character_back.png"))

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

    private fun setupWeaponAssets() {
        val modelBuilder = ModelBuilder()
        for (weapon in WeaponType.entries) {
            // Load player pose texture
            if (!poseTextures.containsKey(weapon.playerPoseTexturePath)) {
                poseTextures[weapon.playerPoseTexturePath] = Texture(Gdx.files.internal(weapon.playerPoseTexturePath))
            }

            // Load bullet model if it's a shooting weapon and we haven't loaded it yet
            weapon.bulletTexturePath?.let { path ->
                if (!bulletModels.containsKey(path)) {
                    val bulletTexture = Texture(Gdx.files.internal(path))
                    val bulletMaterial = Material(
                        TextureAttribute.createDiffuse(bulletTexture),
                        BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                        IntAttribute.createCullFace(GL20.GL_NONE)
                    )
                    bulletModels[path] = modelBuilder.createRect(
                        -0.3f, -0.15f, 0f, 0.3f, -0.15f, 0f, 0.3f, 0.15f, 0f, -0.3f, 0.15f, 0f,
                        0f, 0f, 1f, bulletMaterial,
                        (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
                    )
                }
            }
        }
    }

    private fun handleWeaponInput(deltaTime: Float) {
        fireRateTimer -= deltaTime

        if (equippedWeapon == WeaponType.UNARMED) {
            isShooting = false
            chargeTime = 0f // Reset charge when unarmed
            return
        }

        // Check for button press
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            chargeTime += deltaTime // Increment charge time while holding the button

            when (equippedWeapon.actionType) {
                WeaponActionType.SHOOTING -> {
                    if (fireRateTimer <= 0f && !isRotating) {
                        // TODO: Add check for magazine count here
                        // if (currentMagazineCount > 0 || !equippedWeapon.requiresReload) { ... }

                        spawnBullet()
                        fireRateTimer = equippedWeapon.fireCooldown
                        // currentMagazineCount--
                    } else if (isRotating) {
                        fireRateTimer = equippedWeapon.fireCooldown
                    }
                    isShooting = true
                    shootingPoseTimer = shootingPoseDuration
                }
                WeaponActionType.MELEE -> {
                    // TODO: Implement melee attack logic
                }
                WeaponActionType.THROWABLE -> {
                    // TODO: Implement throwable weapon logic
                }
            }
        } else {
            // Button is released, reset the charge time
            chargeTime = 0f
        }

        if (isShooting) {
            shootingPoseTimer -= deltaTime
            if (shootingPoseTimer <= 0f) {
                isShooting = false
            }
        }
    }

    private fun spawnBullet() {
        val bulletModel = bulletModels[equippedWeapon.bulletTexturePath] ?: return

        val directionX = if (playerCurrentRotationY == 180f) -1f else 1f
        val velocity = Vector3(directionX * equippedWeapon.bulletSpeed, 0f, 0f)
        val bulletSpawnOffsetX = directionX * 1.5f
        val bulletSpawnPos = playerPosition.cpy().add(bulletSpawnOffsetX, 0f, 0f)

        // Determine rotation
        val bulletRotation = if (directionX < 0) 180f else 0f

        val bullet = Bullet(
            position = bulletSpawnPos,
            velocity = velocity,
            modelInstance = ModelInstance(bulletModel),
            lifetime = equippedWeapon.bulletLifetime,
            rotationY = bulletRotation
        )
        activeBullets.add(bullet)

        // Spawn the muzzle flash particle effect
        val muzzleFlashRightOffset = 0.7f
        val muzzleFlashLeftOffset = -0.01f

        // vertical offset
        val muzzleFlashVerticalOffset = 0.4f

        val finalHorizontalOffset = when (directionX) {
            -1f -> -muzzleFlashLeftOffset
            else -> muzzleFlashRightOffset
        }

        // Calculate the final position
        val muzzleFlashPosition = bulletSpawnPos.cpy().add(
            finalHorizontalOffset,
            muzzleFlashVerticalOffset,
            0f
        )

        // Spawn particle effect
        val chargeProgress = (chargeTime / chargeDurationForMaxScale).coerceIn(0f, 1f)
        val currentShotScale = minShotScale + (maxShotScale - minShotScale) * chargeProgress

        // Spawn particle effect with override scale
        particleSystem.spawnEffect(
            type = ParticleEffectType.FIRED_SHOT,
            position = muzzleFlashPosition,
            overrideScale = currentShotScale
        )
    }

    fun cycleWeapon() {
        currentWeaponIndex = (currentWeaponIndex + 1) % weapons.size
        equippedWeapon = weapons[currentWeaponIndex]
        currentMagazineCount = equippedWeapon.magazineSize
        println("Equipped: ${equippedWeapon.displayName}")
    }

    private fun canMoveToWithDoorCollision(x: Float, y: Float, z: Float, gameBlocks: Array<GameBlock>, gameHouses: Array<GameHouse>, gameInteriors: Array<GameInterior>): Boolean {
        val shrinkFor3D = 0.2f  // Normal shrink for most objects
        val shrinkFor2D = 0.8f  // Much smaller collision box for doors

        // Create player bounds for this check
        val playerBoundsFor3D = BoundingBox()
        playerBoundsFor3D.set(
            Vector3(x - (playerSize.x / 2 - shrinkFor3D), y - playerSize.y / 2, z - (playerSize.z / 2 - shrinkFor3D)),
            Vector3(x + (playerSize.x / 2 - shrinkFor3D), y + playerSize.y / 2, z + (playerSize.z / 2 - shrinkFor3D))
        )

        for (gameBlock in gameBlocks) {
            if (!gameBlock.blockType.hasCollision) {
                continue
            }
            if (gameBlock.collidesWith(playerBoundsFor3D)) {
                val playerBottom = playerBoundsFor3D.min.y
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

        // Check house collisions
        for (house in gameHouses) {
            if (house.houseType == HouseType.STAIR) {
                continue
            } else {
                if (house.collidesWithMesh(playerBoundsFor3D)) {
                    return false // Collision with house detected
                }
            }
        }

        // Check interior collisions
        for (interior in gameInteriors) {
            if (!interior.interiorType.hasCollision) continue

            if (interior.interiorType.is3D) {
                // Use the standard player bounds for 3D interiors
                if (interior.collidesWithMesh(playerBoundsFor3D)) {
                    return false
                }
            } else {
                // It's a 2D interior! Use the tighter player collision radius.
                val playerRadius = (playerSize.x / 2f) - shrinkFor2D
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
            val blockBack = blockCenterZ - blockHalfSize

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

    fun handleMovement(deltaTime: Float, sceneManager: SceneManager, gameBlocks: Array<GameBlock>, gameHouses: Array<GameHouse>, gameInteriors: Array<GameInterior>, allCars: Array<GameCar>, particleSystem: ParticleSystem): Boolean {
        return if (isDriving) {
            handleCarMovement(deltaTime, sceneManager, gameBlocks, gameHouses, gameInteriors, allCars)
        } else {
            handlePlayerOnFootMovement(deltaTime, sceneManager, gameBlocks, gameHouses, gameInteriors, particleSystem)
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

    private fun handlePlayerOnFootMovement(deltaTime: Float, sceneManager: SceneManager, gameBlocks: Array<GameBlock>, gameHouses: Array<GameHouse>, gameInteriors: Array<GameInterior>, particleSystem: ParticleSystem): Boolean {
        val originalPosition = playerPosition.cpy() // For checking if movement occurred
        isMoving = false
        var currentMovementDirection = 0f

        // 1. Calculate desired horizontal movement
        var deltaX = 0f
        var deltaZ = 0f
        if (Gdx.input.isKeyPressed(Input.Keys.A)) { deltaX -= playerSpeed * deltaTime; currentMovementDirection = -1f }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) { deltaX += playerSpeed * deltaTime; currentMovementDirection = 1f }

        isPressingW = Gdx.input.isKeyPressed(Input.Keys.W)
        if (isPressingW) { deltaZ -= playerSpeed * deltaTime }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) { deltaZ += playerSpeed * deltaTime }

        if (isShooting && !equippedWeapon.allowsMovementWhileShooting) {
            deltaX = 0f
            deltaZ = 0f
        }

        // DEBUG: Print initial state only when trying to move
        if (deltaX != 0f || deltaZ != 0f) {
            println("\n--- FRAME START ---")
            println("DEBUG: originalPosition.y = ${"%.2f".format(originalPosition.y)}")
        }

        // 2. Resolve X-axis movement first
        if (deltaX != 0f) {
            val playerFootY = playerPosition.y - (playerSize.y / 2f)
            val supportY = sceneManager.findHighestSupportY(playerPosition.x + deltaX, playerPosition.z, playerPosition.y, playerSize.x / 2f, blockSize)
            println("DEBUG (X-Move): Found support at target X. supportY = ${"%.2f".format(supportY)}, playerFootY = ${"%.2f".format(playerFootY)}")

            // Check if the step is too high before attempting to move
            if (supportY - playerFootY <= MAX_STEP_HEIGHT) {
                // For the collision check, we use a Y that's on top of the potential new ground.
                val checkY = supportY + (playerSize.y / 2f)
                println("DEBUG (X-Move): Step is OK. Collision check at checkY = ${"%.2f".format(checkY)}")
                if (canMoveToWithDoorCollision(playerPosition.x + deltaX, checkY, playerPosition.z, gameBlocks, gameHouses, gameInteriors)) {
                    println("DEBUG (X-Move): PASSED. Moving player.x.")
                    playerPosition.x += deltaX
                } else {
                    println("DEBUG (X-Move): FAILED. Collision detected.")
                }
            } else {
                println("DEBUG (X-Move): Step too high. (${"%.2f".format(supportY - playerFootY)} > $MAX_STEP_HEIGHT)")
            }
        }

        // 3. Resolve Z-axis movement second
        if (deltaZ != 0f) {
            // Use the potentially updated X position from the previous step
            val playerFootY = playerPosition.y - (playerSize.y / 2f)
            val supportY = sceneManager.findHighestSupportY(playerPosition.x, playerPosition.z + deltaZ, playerPosition.y, playerSize.x / 2f, blockSize)
            println("DEBUG (Z-Move): Found support at target Z. supportY = ${"%.2f".format(supportY)}, playerFootY = ${"%.2f".format(playerFootY)}")

            // Check if the step is too high
            if (supportY - playerFootY <= MAX_STEP_HEIGHT) {
                val checkY = supportY + (playerSize.y / 2f)
                println("DEBUG (Z-Move): Step is OK. Collision check at checkY = ${"%.2f".format(checkY)}")
                if (canMoveToWithDoorCollision(playerPosition.x, checkY, playerPosition.z + deltaZ, gameBlocks, gameHouses, gameInteriors)) {
                    println("DEBUG (Z-Move): PASSED. Moving player.z.")
                    playerPosition.z += deltaZ
                } else {
                    println("DEBUG (Z-Move): FAILED. Collision detected.")
                }
            } else {
                println("DEBUG (Z-Move): Step too high. (${"%.2f".format(supportY - playerFootY)} > $MAX_STEP_HEIGHT)")
            }
        }

        // 4. Resolve Y-axis (Gravity and Final Grounding)
        //println("--- Y-AXIS RESOLUTION ---")
        val finalSupportY = sceneManager.findHighestSupportY(playerPosition.x, playerPosition.z, originalPosition.y, playerSize.x / 2f, blockSize)
        val playerFootY = originalPosition.y - (playerSize.y / 2f)
        //println("DEBUG (Y-Axis): finalSupportY = ${"%.2f".format(finalSupportY)}, originalPlayerFootY = ${"%.2f".format(playerFootY)}")

        // Ensure the final ground position is a valid step from where the player *started* the frame.
        val effectiveSupportY: Float
        if (finalSupportY - playerFootY <= MAX_STEP_HEIGHT) {
            effectiveSupportY = finalSupportY
            //println("DEBUG (Y-Axis): Step is valid. effectiveSupportY = ${"%.2f".format(effectiveSupportY)}")
        } else {
            effectiveSupportY = sceneManager.findHighestSupportY(originalPosition.x, originalPosition.z, originalPosition.y, playerSize.x / 2f, blockSize)
           // println("DEBUG (Y-Axis): Step too high! Using original support. effectiveSupportY = ${"%.2f".format(effectiveSupportY)}")
        }

        val targetY = effectiveSupportY + (playerSize.y / 2f)
        val fallY = playerPosition.y - FALL_SPEED * deltaTime // Fall from the current position
        //println("DEBUG (Y-Axis): final targetY = ${"%.2f".format(targetY)}, fallY = ${"%.2f".format(fallY)}")

        val finalY = kotlin.math.max(targetY, fallY)
        playerPosition.y = finalY
        //println("DEBUG (Y-Axis): Chose finalY = ${"%.2f".format(finalY)}. Setting playerPosition.y.")


        // 5. Update flags and visuals
        val moved = !playerPosition.epsilonEquals(originalPosition, 0.001f)
        isMoving = (originalPosition.x != playerPosition.x) || (originalPosition.z != playerPosition.z)

        if (isMoving) {
            // Player is moving
            continuousMovementTimer += deltaTime

            // Only check for wipe spawning if player moving for at least 1 second
            if (continuousMovementTimer >= 0.3f) {
                wipeEffectTimer += deltaTime
                if (wipeEffectTimer >= WIPE_EFFECT_INTERVAL) {
                    wipeEffectTimer = 0f // Reset timer

                    // Spawn position
                    val yOffset = 1.0f
                    val zOffset = -0.5f
                    val wipePosition = playerPosition.cpy().add(0f, -yOffset, zOffset)
                    val wipeSize = 2.5f

                    // Spawn the effect
                    particleSystem.spawnEffect(
                        type = ParticleEffectType.MOVEMENT_WIPE,
                        position = wipePosition,
                        initialRotation = playerCurrentRotationY,
                        targetRotation = playerTargetRotationY,
                        overrideScale = wipeSize
                    )
                }
            }
        } else {
            // If player stops, reset the continuous movement timer
            continuousMovementTimer = 0f

            // Reset timer if not moving
            wipeEffectTimer = WIPE_EFFECT_INTERVAL
        }

        if(moved) {
            println("DEBUG: Final Position: (${"%.2f".format(playerPosition.x)}, ${"%.2f".format(playerPosition.y)}, ${"%.2f".format(playerPosition.z)})")
        }
        if (deltaX != 0f || deltaZ != 0f || moved) {
            println("--- FRAME END ---\n")
        }

        // Handle animation and rotation
        if (isMoving && !lastIsMoving) {
            // No need for a separate animation, just update the texture directly
            animationSystem.playAnimation("walking")
        } else if (!isMoving && lastIsMoving) {
            // Revert to normal walk/idle animations
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

    fun update(deltaTime: Float, sceneManager: SceneManager) {
        handleWeaponInput(deltaTime)

        // Update animation system
        animationSystem.update(deltaTime)

        // Update player texture if it changed
        val finalTexture: Texture? = when {
            isShooting -> poseTextures[equippedWeapon.playerPoseTexturePath]
            isPressingW -> playerBackTexture
            else -> animationSystem.getCurrentTexture()
        }

        // Update the player's texture
        finalTexture?.let {
            updatePlayerTexture(it)
        }

        val bulletIterator = activeBullets.iterator()
        while (bulletIterator.hasNext()) {
            val bullet = bulletIterator.next()
            bullet.update(deltaTime)

            // Collision Check
            val collisionResult = checkBulletCollision(bullet, sceneManager)

            // Check if ANY valid object was hit
            if (collisionResult != null) {
                // Destroy bullet
                bulletIterator.remove()

                // Calculate the spawn position
                val particleSpawnPos = collisionResult.hitPoint.cpy().mulAdd(collisionResult.surfaceNormal, PARTICLE_IMPACT_OFFSET)

                when (collisionResult.type) {
                    HitObjectType.BLOCK, HitObjectType.INTERIOR, HitObjectType.CAR -> {
                        // Spawn dust/sparks for static objects
                        particleSystem.spawnEffect(ParticleEffectType.DUST_IMPACT, particleSpawnPos)
                    }

                    HitObjectType.OBJECT,
                    HitObjectType.HOUSE -> {
                        // Intentionally do nothing
                    }

                    HitObjectType.ENEMY -> {
                        val enemy = collisionResult.hitObject as GameEnemy

                        // Use the enemy's center
                        val bloodSpawnPosition = enemy.position.cpy()

                        // small random offset to make it look less robotic
                        val offsetX = (Random.nextFloat() - 0.5f) * (enemy.enemyType.width * 0.4f)
                        val offsetY = (Random.nextFloat() - 0.5f) * (enemy.enemyType.height * 0.4f)
                        bloodSpawnPosition.add(offsetX, offsetY, 0f) // No Z offset for 2D characters

                        // 50% chance to spawn blood effects
                        if (Random.nextFloat() < 0.5f) {
                            spawnBloodEffects(bloodSpawnPosition, sceneManager)
                        }

                        if (enemy.takeDamage(equippedWeapon.damage)) {
                            // Enemy died, remove it
                            sceneManager.activeEnemies.removeValue(enemy, true)
                        }
                    }
                    HitObjectType.NPC -> {
                        val npc = collisionResult.hitObject as GameNPC

                        // Use the NPC's center
                        val bloodSpawnPosition = npc.position.cpy()

                        // small random offset
                        val offsetX = (Random.nextFloat() - 0.5f) * (npc.npcType.width * 0.4f)
                        val offsetY = (Random.nextFloat() - 0.5f) * (npc.npcType.height * 0.4f)
                        bloodSpawnPosition.add(offsetX, offsetY, 0f)

                        // 50% chance to spawn blood effects
                        if (Random.nextFloat() < 0.5f) {
                            spawnBloodEffects(bloodSpawnPosition, sceneManager)
                        }

                        if (npc.takeDamage(equippedWeapon.damage)) {
                            // NPC died, remove it from the scene
                            sceneManager.activeNPCs.removeValue(npc, true)
                        }
                    }
                    HitObjectType.NONE -> {}
                }
                continue // Skip next bullet
            }

            if (bullet.lifetime <= 0f) {
                bulletIterator.remove()
            }
        }
        updatePlayerTransform()
    }

    private fun checkBulletCollision(bullet: Bullet, sceneManager: SceneManager): CollisionResult? {
        val travelDistanceSq = bullet.velocity.len2() * (Gdx.graphics.deltaTime * Gdx.graphics.deltaTime)
        if (travelDistanceSq == 0f) return null // Don't check if bullet hasn't moved

        val bulletRay = Ray(bullet.position.cpy().mulAdd(bullet.velocity, -Gdx.graphics.deltaTime), bullet.velocity.cpy().nor())
        val intersectionPoint = Vector3()

        var closestResult: CollisionResult? = null
        var closestDistSq = Float.MAX_VALUE

        // 1. Check against Blocks
        for (block in sceneManager.activeBlocks) {
            // Ignore invisible blocks or blocks without collision
            if (!block.blockType.hasCollision || !block.blockType.isVisible) continue

            val blockBounds = block.getBoundingBox(blockSize, BoundingBox())
            if (Intersector.intersectRayBounds(bulletRay, blockBounds, intersectionPoint)) {
                val distSq = bulletRay.origin.dst2(intersectionPoint)
                if (distSq <= travelDistanceSq && distSq < closestDistSq) {
                    val blockCenter = block.position
                    val relativeHitPos = intersectionPoint.cpy().sub(blockCenter)
                    val normal = Vector3()

                    if (kotlin.math.abs(relativeHitPos.x) > kotlin.math.abs(relativeHitPos.y) && kotlin.math.abs(relativeHitPos.x) > kotlin.math.abs(relativeHitPos.z)) {
                        normal.set(if (relativeHitPos.x > 0) 1f else -1f, 0f, 0f)
                    } else if (kotlin.math.abs(relativeHitPos.y) > kotlin.math.abs(relativeHitPos.x) && kotlin.math.abs(relativeHitPos.y) > kotlin.math.abs(relativeHitPos.z)) {
                        normal.set(0f, if (relativeHitPos.y > 0) 1f else -1f, 0f)
                    } else {
                        normal.set(0f, 0f, if (relativeHitPos.z > 0) 1f else -1f)
                    }
                    closestResult = CollisionResult(HitObjectType.BLOCK, block, intersectionPoint.cpy(), normal)
                    closestDistSq = distSq
                }
            }
        }

        // 2. Check against complex meshes
        val allMeshes = sceneManager.activeHouses.map { it to HitObjectType.HOUSE } +
            sceneManager.activeInteriors.filter { it.interiorType.is3D && it.interiorType.hasCollision }.map { it to HitObjectType.INTERIOR }

        for ((meshObject, type) in allMeshes) {
            var hit = false
            when (meshObject) {
                is GameHouse -> { if (meshObject.intersectsRay(bulletRay, intersectionPoint)) hit = true }
                is GameInterior -> { if (meshObject.intersectsRay(bulletRay, intersectionPoint)) hit = true }
            }

            if (hit) {
                val distSq = bulletRay.origin.dst2(intersectionPoint)
                if (distSq <= travelDistanceSq && distSq < closestDistSq) {
                    val normal = bullet.velocity.cpy().nor().scl(-1f)
                    closestResult = CollisionResult(type, meshObject, intersectionPoint.cpy(), normal)
                    closestDistSq = distSq
                }
            }
        }

        // 3. Check against simple bounding boxes
        val simpleObjects = sceneManager.activeEnemies.map { it to HitObjectType.ENEMY } +
            sceneManager.activeNPCs.map { it to HitObjectType.NPC } +
            sceneManager.activeObjects.filter { !it.objectType.isInvisible }.map { it to HitObjectType.OBJECT } +
            sceneManager.activeCars.map { it to HitObjectType.CAR }

        for ((obj, type) in simpleObjects) {
            val bounds = (obj as? GameEnemy)?.getBoundingBox() ?: (obj as? GameNPC)?.getBoundingBox() ?: (obj as? GameObject)?.getBoundingBox() ?: (obj as? GameCar)?.getBoundingBox() ?: continue

            if (Intersector.intersectRayBounds(bulletRay, bounds, intersectionPoint)) {
                val distSq = bulletRay.origin.dst2(intersectionPoint)
                if (distSq <= travelDistanceSq && distSq < closestDistSq) {
                    val normal = bullet.velocity.cpy().nor().scl(-1f)
                    closestResult = CollisionResult(type, obj, intersectionPoint.cpy(), normal)
                    closestDistSq = distSq
                }
            }
        }

        return closestResult
    }

    private fun spawnBloodEffects(position: Vector3, sceneManager: SceneManager) {
        val bloodSplatterEffects = listOf(
            ParticleEffectType.BLOOD_SPLATTER_1,
            ParticleEffectType.BLOOD_SPLATTER_2,
            ParticleEffectType.BLOOD_SPLATTER_3,
        )
        val bloodDripEffect = ParticleEffectType.BLOOD_DRIP

        // All possible effects, used for random picking
        val allBloodEffects = bloodSplatterEffects + bloodDripEffect

        // Spawn a random number of particles
        val particleCount = (1..3).random()

        for (i in 0 until particleCount) {
            // Pick a random effect from our list
            val randomEffect = allBloodEffects.random()

            if (randomEffect == bloodDripEffect) {
                val randomVelocity = Vector3(
                    (Math.random() - 0.5).toFloat() * 5f,
                    (Math.random() * 3f).toFloat(),
                    (Math.random() - 0.5).toFloat() * 5f
                )
                particleSystem.spawnEffect(randomEffect, position.cpy(), randomVelocity)
            } else {
                // 1. Find the ground Y-coordinate
                val groundY = sceneManager.findHighestSupportY(position.x, position.z, position.y, 0.1f, blockSize)
                val spawnPos = Vector3(position.x, groundY + 0.1f, position.z) // Place slightly above ground

                // 2. The normal for a flat ground surface is always straight up.
                val groundNormal = Vector3.Y

                // 3. Spawn the splatter
                particleSystem.spawnEffect(
                    type = randomEffect,
                    position = spawnPos,
                    baseDirection = null,
                    surfaceNormal = groundNormal
                )
            }
        }
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
        billboardModelBatch.begin(camera)

        // If the player is driving, do not render their 2D model.
        if (!isDriving) {
            billboardModelBatch.render(playerInstance, environment)
        }

        // Render all active bullets
        for (bullet in activeBullets) {
            billboardModelBatch.render(bullet.modelInstance, environment)
        }

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
        playerBackTexture.dispose()

        animationSystem.dispose()
        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()

        poseTextures.values.forEach { it.dispose() }
        bulletModels.values.forEach { it.dispose() }
    }
}
