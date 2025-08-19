package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Array
import java.util.*
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

// --- ENUMERATIONS FOR ENEMY PROPERTIES ---

enum class EnemyType(
    val displayName: String,
    val texturePath: String,
    val width: Float,
    val height: Float,
    val baseHealth: Float,
    val speed: Float
) {
    NOUSE_THUG("Nouse Thug", "textures/characters/enemy_nouse.png", 3f, 4f, 100f, 6f),
    GUNTHER("Gunther", "textures/characters/Gunther.png", 7.0f, 8.0f, 180f, 5f),
    CORRUPT_DETECTIVE("Corrupt Detective", "textures/characters/detective.png", 3f, 4.2f, 150f, 6.5f),
    MAFIA_BOSS("Mafia Boss", "textures/characters/mafia_boss.png", 3.8f, 4.8f, 500f, 4f),
}

enum class EnemyBehavior(val displayName: String) {
    STATIONARY_SHOOTER("Stationary"),
    COWARD_HIDER("Coward"),
    AGGRESSIVE_RUSHER("Rusher")
}

// AI State for the enemy's state machine
enum class AIState {
    IDLE,       // Standing still, "shooting"
    CHASING,    // Moving towards the player (Rusher)
    FLEEING,    // Moving away from the player (Coward)
    SEARCHING,  // Looking for a hiding spot (Coward)
    HIDING,      // Reached a hiding spot (Coward)
    DYING
}

// --- MAIN ENEMY DATA CLASS ---
data class GameEnemy(
    val id: String = UUID.randomUUID().toString(),
    val modelInstance: ModelInstance,
    val enemyType: EnemyType,
    val behaviorType: EnemyBehavior,
    var position: Vector3,
    var health: Float = enemyType.baseHealth
) {
    var bleedTimer: Float = 0f // How long the bleeding effect lasts
    var bloodDripSpawnTimer: Float = 0f // Timer to control the rate of drips

    // AI state properties
    var currentState: AIState = AIState.IDLE
    var stateTimer: Float = 0f
    var targetPosition: Vector3? = null
    var facingRotationY: Float = 0f
    var lastDamageType: DamageType = DamageType.GENERIC
    var fadeOutTimer: Float = 0f
    @Transient val blendingAttribute: BlendingAttribute = modelInstance.materials.first().get(BlendingAttribute.Type) as BlendingAttribute
    @Transient var ashSpawned: Boolean = false

    var isMoving: Boolean = false
    var walkAnimationTimer: Float = 0f
    var wobbleAngle: Float = 0f

    // Collision properties
    private val boundsSize = Vector3(enemyType.width, enemyType.height, enemyType.width)
    private val boundingBox = BoundingBox()

    @Transient var isInCar: Boolean = false
    @Transient var drivingCar: GameCar? = null
    @Transient var currentSeat: CarSeat? = null

    fun enterCar(car: GameCar) {
        val seat = car.addOccupant(this)
        if (seat != null) {
            isInCar = true
            drivingCar = car
            currentSeat = seat
            println("${this.enemyType.displayName} entered a car.")
        }
    }

    fun exitCar() {
        val car = drivingCar ?: return
        car.removeOccupant(this)
        isInCar = false
        // TODO: In the future, add logic here to place the enemy in a safe spot next to the car.
        this.position.set(car.position.x - 5f, car.position.y, car.position.z)
        println("${this.enemyType.displayName} exited a car.")
        drivingCar = null
        currentSeat = null
    }

    fun updateVisuals() {
        modelInstance.transform.idt()
        modelInstance.transform.setTranslation(position)
        modelInstance.transform.rotate(Vector3.Y, facingRotationY)
        // APPLY THE WOBBLE
        modelInstance.transform.rotate(Vector3.Z, wobbleAngle)
    }

    fun getBoundingBox(): BoundingBox {
        boundingBox.set(
            Vector3(position.x - boundsSize.x / 2, position.y - boundsSize.y / 2, position.z - boundsSize.z / 2),
            Vector3(position.x + boundsSize.x / 2, position.y + boundsSize.y / 2, position.z + boundsSize.z / 2)
        )
        return boundingBox
    }

    companion object {
        const val BLEED_DURATION = 1.0f // Bleeding lasts for 5 seconds
        const val BLOOD_DRIP_INTERVAL = 0.7f // Spawn a drip every 0.25s of movement
    }

    fun takeDamage(damage: Float, type: DamageType): Boolean {
        if (health <= 0) return false // Already dead, don't process more damage

        health -= damage
        // The type of the killing blow is what matters most.
        if (health > 0 && (type == DamageType.GENERIC || type == DamageType.MELEE)) {
            this.bleedTimer = BLEED_DURATION
        }

        if (health <= 0) {
            lastDamageType = type
        }
        println("${this.enemyType.displayName} took $damage $type damage. HP remaining: ${this.health.coerceAtLeast(0f)}")
        return health <= 0
    }
}

// --- ENEMY MANAGEMENT SYSTEM ---

class EnemySystem : IFinePositionable {
    private val enemyModels = mutableMapOf<EnemyType, Model>()
    private val enemyTextures = mutableMapOf<EnemyType, Texture>()
    private lateinit var billboardModelBatch: ModelBatch
    private lateinit var billboardShaderProvider: BillboardShaderProvider
    private val renderableInstances = Array<ModelInstance>()
    private val WOBBLE_AMPLITUDE_DEGREES = 5f
    private val WOBBLE_FREQUENCY = 6f

    // UI selection state
    var currentSelectedEnemyType = EnemyType.NOUSE_THUG
        private set
    var currentSelectedBehavior = EnemyBehavior.STATIONARY_SHOOTER
        private set
    var currentEnemyTypeIndex = 0
    var currentBehaviorIndex = 0

    // AI constants
    private val detectionRange = 25f // When cowards start fleeing
    private val hideSearchRadius = 40f
    private val hideDistance = 15f
    private val stopChasingDistance = 1.5f
    private val activationRange = 150f

    // Physics constants
    private val FALL_SPEED = 25f
    private val MAX_STEP_HEIGHT = 4.0f
    override var finePosMode: Boolean = false
    override val fineStep: Float = 0.25f
    private val tempBlockBounds = BoundingBox()
    private val nearbyBlocks = Array<GameBlock>()

    private val FADE_OUT_DURATION = 1.5f
    private val ASH_SPAWN_START_TIME = 1.5f // Must match the ash particle's fadeIn time

    lateinit var sceneManager: SceneManager
    private lateinit var raycastSystem: RaycastSystem
    private val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)
    private val tempVec3 = Vector3()
    private var blockSize: Float = 4f

    fun initialize(blockSize: Float) { // Modified to accept blockSize
        this.blockSize = blockSize
        this.raycastSystem = RaycastSystem(blockSize)

        billboardShaderProvider = BillboardShaderProvider()
        billboardModelBatch = ModelBatch(billboardShaderProvider)
        billboardShaderProvider.setBillboardLightingStrength(0.9f)
        billboardShaderProvider.setMinLightLevel(0.3f)

        val modelBuilder = ModelBuilder()
        for (type in EnemyType.entries) {
            try {
                val texture = Texture(Gdx.files.internal(type.texturePath))
                enemyTextures[type] = texture
                val material = Material(
                    TextureAttribute.createDiffuse(texture),
                    BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                    IntAttribute.createCullFace(GL20.GL_NONE)
                )
                val model = modelBuilder.createRect(
                    -type.width / 2, -type.height / 2, 0f,
                    type.width / 2, -type.height / 2, 0f,
                    type.width / 2, type.height / 2, 0f,
                    -type.width / 2, type.height / 2, 0f,
                    0f, 0f, 1f,
                    material,
                    (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
                )
                enemyModels[type] = model
            } catch (e: Exception) {
                println("ERROR: Could not load enemy texture or model for ${type.displayName}: ${e.message}")
            }
        }
    }

    fun handlePlaceAction(ray: Ray) {
        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            val surfaceY = findHighestSurfaceYAt(tempVec3.x, tempVec3.z)

            // Position the enemy so its feet are on the surface
            val enemyType = currentSelectedEnemyType
            val enemyPosition = Vector3(tempVec3.x, surfaceY + enemyType.height / 2f, tempVec3.z)

            val newEnemy = createEnemy(
                enemyPosition,
                currentSelectedEnemyType,
                currentSelectedBehavior
            )

            if (newEnemy != null) {
                sceneManager.activeEnemies.add(newEnemy)
                sceneManager.game.lastPlacedInstance = newEnemy // For fine positioning
                println("Placed ${newEnemy.enemyType.displayName} with ${newEnemy.behaviorType.displayName} behavior at $enemyPosition")
            }
        }
    }

    fun handleRemoveAction(ray: Ray): Boolean {
        val enemyToRemove = raycastSystem.getEnemyAtRay(ray, sceneManager.activeEnemies)
        if (enemyToRemove != null) {
            removeEnemy(enemyToRemove)
            return true
        }
        return false
    }

    private fun removeEnemy(enemyToRemove: GameEnemy) {
        sceneManager.activeEnemies.removeValue(enemyToRemove, true)
        println("Removed ${enemyToRemove.enemyType.displayName} at: ${enemyToRemove.position}")
    }

    private fun findHighestSurfaceYAt(x: Float, z: Float): Float {
        val blocksInColumn = sceneManager.activeChunkManager.getBlocksInColumn(x, z)
        var highestY = 0f // Default to ground level
        val tempBounds = BoundingBox()

        for (gameBlock in blocksInColumn) {
            if (!gameBlock.blockType.hasCollision) continue

            val blockBounds = gameBlock.getBoundingBox(blockSize, tempBounds)

            if (blockBounds.max.y > highestY) {
                highestY = blockBounds.max.y
            }
        }
        return highestY
    }

    fun createEnemy(position: Vector3, enemyType: EnemyType, behavior: EnemyBehavior): GameEnemy? {
        val model = enemyModels[enemyType] ?: return null
        val instance = ModelInstance(model)

        instance.userData = "character"

        return GameEnemy(
            modelInstance = instance,
            enemyType = enemyType,
            behaviorType = behavior,
            position = position.cpy()
        )
    }

    fun startDeathSequence(enemy: GameEnemy, sceneManager: SceneManager) {
        if (enemy.currentState == AIState.DYING) return // Already dying

        enemy.currentState = AIState.DYING
        enemy.fadeOutTimer = FADE_OUT_DURATION
        enemy.health = 0f // Finalize death

        println("${enemy.enemyType.displayName} is dying from ${enemy.lastDamageType}.")

        // If not burned, spawn a blood pool and bones
        if (enemy.lastDamageType != DamageType.FIRE) {
            sceneManager.game.playerSystem.bloodPoolSystem.addPool(enemy.position.cpy(), sceneManager)
            sceneManager.boneSystem.spawnBones(enemy.position.cpy(), enemy.facingRotationY, sceneManager)
        }
    }

    fun update(deltaTime: Float, playerSystem: PlayerSystem, sceneManager: SceneManager, blockSize: Float) {
        if (playerSystem.isDriving) return // Don't update AI if player is safe in a car

        val playerPos = playerSystem.getPosition()
        val iterator = sceneManager.activeEnemies.iterator() // Use iterator for safe removal

        while(iterator.hasNext()) {
            val enemy = iterator.next()

            // Skip all AI, physics, and visual updates for enemies inside cars
            if (enemy.isInCar) continue

            // DYING STATE LOGIC
            if (enemy.currentState == AIState.DYING) {
                enemy.fadeOutTimer -= deltaTime

                // Check if it's time to spawn ash
                if (enemy.lastDamageType == DamageType.FIRE && !enemy.ashSpawned) {
                    // Start spawning when fadeOutTimer is less than or equal to the ash fade-in time
                    if (enemy.fadeOutTimer <= ASH_SPAWN_START_TIME) {
                        val groundY = sceneManager.findHighestSupportY(enemy.position.x, enemy.position.z, enemy.position.y, 0.1f, blockSize)
                        val ashPosition = Vector3(enemy.position.x, groundY + 0.86f, enemy.position.z) // Spawn on the ground + tiny offset for visibility

                        sceneManager.game.particleSystem.spawnEffect(ParticleEffectType.BURNED_ASH, ashPosition)
                        enemy.ashSpawned = true // Spawn only once
                    }
                }
                if (enemy.fadeOutTimer <= 0f) {
                    iterator.remove() // Completely faded out, now remove
                    continue
                }

                // Update opacity
                val opacity = (enemy.fadeOutTimer / FADE_OUT_DURATION).coerceIn(0f, 1f)
                enemy.blendingAttribute.opacity = opacity

                // Don't process any other logic for a dying enemy
                continue
            }

            if (enemy.bleedTimer > 0f) {
                enemy.bleedTimer -= deltaTime

                // Check if the enemy is moving and it's time to spawn a drip
                if (enemy.isMoving) {
                    enemy.bloodDripSpawnTimer -= deltaTime
                    if (enemy.bloodDripSpawnTimer <= 0f) {
                        enemy.bloodDripSpawnTimer = GameEnemy.BLOOD_DRIP_INTERVAL

                        // Spawn a drip at the enemy's position with a slight random offset
                        val spawnPosition = enemy.position.cpy().add(
                            (Random.nextFloat() - 0.5f) * 1f,  // Small horizontal offset
                            (Random.nextFloat() - 0.5f) * 2f,  // Vertical offset around the torso
                            (Random.nextFloat() - 0.5f) * 1f
                        )
                        sceneManager.game.particleSystem.spawnEffect(ParticleEffectType.BLOOD_DRIP, spawnPosition)
                    }
                }
            }

            enemy.isMoving = false

            // --- ACTIVATION CHECK ---
            // Calculate the distance from the player to this enemy.
            val distanceToPlayer = enemy.position.dst(playerPos)

            // If the enemy is outside our activation range, skip its update entirely.
            if (distanceToPlayer > activationRange) {
                // To prevent enemies getting "stuck" in a state, reset them to IDLE if they deactivate.
                if (enemy.currentState != AIState.IDLE) {
                    enemy.currentState = AIState.IDLE
                    enemy.targetPosition = null // Clear any old target
                }
                continue // <<< Skip to the next enemy in the loop
            }

            // Only apply physics if we are NOT in fine positioning mode.
            if (!finePosMode) {
                applyPhysics(enemy, deltaTime, sceneManager, blockSize)
            }
            updateAI(enemy, playerPos, deltaTime, sceneManager)

            if (enemy.isMoving) {
                enemy.walkAnimationTimer += deltaTime
                val angleRad = sin(enemy.walkAnimationTimer * WOBBLE_FREQUENCY)
                enemy.wobbleAngle = angleRad * WOBBLE_AMPLITUDE_DEGREES
            } else {
                // Smoothly return to upright
                if (kotlin.math.abs(enemy.wobbleAngle) > 0.1f) {
                    enemy.wobbleAngle *= (1.0f - deltaTime * 10f).coerceAtLeast(0f)
                } else {
                    enemy.wobbleAngle = 0f
                }
                enemy.walkAnimationTimer = 0f
            }

            if (playerPos.x > enemy.position.x) {
                // Player is to the right of the enemy, so enemy should face right.
                enemy.facingRotationY = 0f
            } else {
                // Player is to the left of the enemy, so enemy should face left.
                enemy.facingRotationY = 180f
            }

            // Update the enemy's visual transform.
            enemy.updateVisuals()
        }
    }

    private fun applyPhysics(enemy: GameEnemy, deltaTime: Float, sceneManager: SceneManager, blockSize: Float) {
        // 1. Find the highest solid ground directly beneath the enemy.
        var highestSupportY = 0f // Default to ground level at Y=0
        val blocksBeneath = sceneManager.activeChunkManager.getBlocksInColumn(enemy.position.x, enemy.position.z)

        for (block in blocksBeneath) {
            if (!block.blockType.hasCollision) continue

            val blockTop = block.getBoundingBox(blockSize, tempBlockBounds).max.y

            // We only care about surfaces that are actually below the enemy's feet.
            if (blockTop <= enemy.position.y - (enemy.enemyType.height / 2f) + MAX_STEP_HEIGHT) {
                if (blockTop > highestSupportY) {
                    highestSupportY = blockTop
                }
            }
        }

        // 2. Determine the target Y position (where the enemy's center should be).
        val targetY = highestSupportY + (enemy.enemyType.height / 2f)

        // 3. Apply gravity.
        val fallY = enemy.position.y - FALL_SPEED * deltaTime
        enemy.position.y = max(targetY, fallY)
    }

    private fun updateAI(enemy: GameEnemy, playerPos: Vector3, deltaTime: Float, sceneManager: SceneManager) {
        when (enemy.behaviorType) {
            EnemyBehavior.STATIONARY_SHOOTER -> {
                enemy.currentState = AIState.IDLE
                // In the future, this is where you'd add shooting logic
            }
            EnemyBehavior.AGGRESSIVE_RUSHER -> {
                if (enemy.position.dst(playerPos) > stopChasingDistance) {
                    enemy.currentState = AIState.CHASING
                    moveTowards(enemy, playerPos, deltaTime, sceneManager)
                } else {
                    enemy.currentState = AIState.IDLE // Reached player, now attacking
                }
            }
            EnemyBehavior.COWARD_HIDER -> {
                val distanceToPlayer = enemy.position.dst(playerPos)
                when (enemy.currentState) {
                    AIState.IDLE -> if (distanceToPlayer < detectionRange) {
                        enemy.currentState = AIState.SEARCHING
                        enemy.stateTimer = 0f
                    }
                    AIState.SEARCHING -> {
                        if (enemy.targetPosition == null) findHidingSpot(enemy, playerPos, sceneManager)

                        if (enemy.targetPosition != null) {
                            enemy.currentState = AIState.FLEEING
                        } else {
                            enemy.stateTimer += deltaTime
                            if (enemy.stateTimer > 2f) enemy.currentState = AIState.IDLE // Give up for now
                        }
                    }
                    AIState.FLEEING -> {
                        enemy.targetPosition?.let { target ->
                            moveTowards(enemy, target, deltaTime, sceneManager)
                            if (enemy.position.dst(target) < 2f) {
                                enemy.currentState = AIState.HIDING
                                enemy.targetPosition = null
                                enemy.stateTimer = 0f
                            }
                        }
                    }
                    AIState.HIDING -> {
                        enemy.stateTimer += deltaTime
                        if (enemy.stateTimer > 5f) enemy.currentState = AIState.IDLE // Emerge after 5s
                    }
                    else -> enemy.currentState = AIState.IDLE
                }
            }
        }
    }

    private fun moveTowards(enemy: GameEnemy, target: Vector3, deltaTime: Float, sceneManager: SceneManager) {
        val direction = Vector3(target).sub(enemy.position).nor()
        val delta = direction.scl(enemy.enemyType.speed * deltaTime)
        val nextPos = Vector3(enemy.position).add(delta)

        // A basic collision check to prevent walking through walls
        if (canEnemyMoveTo(nextPos, enemy, sceneManager)) {
            enemy.position.set(nextPos)
            enemy.isMoving = true
        }
    }

    private fun findHidingSpot(enemy: GameEnemy, playerPos: Vector3, sceneManager: SceneManager) {
        val directionFromPlayer = Vector3(enemy.position).sub(playerPos).nor()

        // Check in a few directions around the "away from player" vector
        for (i in -1..1) {
            val searchAngle = i * 30f // Check 30 degrees to the left and right
            val searchDirection = directionFromPlayer.cpy().rotate(Vector3.Y, searchAngle)
            val potentialSpot = Vector3(enemy.position).add(searchDirection.scl(hideDistance))

            // Find a nearby block or house to hide behind
            val occluder = findNearestOccluder(potentialSpot, sceneManager)

            if (occluder != null) {
                // We found something to hide behind! Target the spot just behind it.
                val dirFromPlayerToOccluder = Vector3(occluder).sub(playerPos).nor()
                enemy.targetPosition = Vector3(occluder).add(dirFromPlayerToOccluder.scl(5f)) // 5 units behind
                return // Found a spot, exit
            }
        }
        enemy.targetPosition = null // No suitable spot found
    }

    private fun findNearestOccluder(position: Vector3, sceneManager: SceneManager): Vector3? {
        val nearbyBlocks = sceneManager.activeChunkManager.getAllBlocks().filter { it.position.dst(position) < hideSearchRadius }
        val nearbyHouses = sceneManager.activeHouses.filter { it.position.dst(position) < hideSearchRadius }

        val closestBlockPos = nearbyBlocks.minByOrNull { it.position.dst2(position) }?.position
        val closestHousePos = nearbyHouses.minByOrNull { it.position.dst2(position) }?.position

        return when {
            closestBlockPos != null && closestHousePos != null -> {
                if (position.dst2(closestBlockPos) < position.dst2(closestHousePos)) closestBlockPos else closestHousePos
            }
            closestBlockPos != null -> closestBlockPos
            closestHousePos != null -> closestHousePos
            else -> null
        }
    }

    private fun canEnemyMoveTo(newPosition: Vector3, enemy: GameEnemy, sceneManager: SceneManager): Boolean {
        // Create the enemy's bounding box at the potential new position
        val enemyBounds = enemy.getBoundingBox()
        enemyBounds.set(
            enemyBounds.min.set(newPosition.x - enemy.enemyType.width / 2f, newPosition.y - enemy.enemyType.height / 2f, newPosition.z - enemy.enemyType.width / 2f),
            enemyBounds.max.set(newPosition.x + enemy.enemyType.width / 2f, newPosition.y + enemy.enemyType.height / 2f, newPosition.z + enemy.enemyType.width / 2f)
        )

        // Check against blocks with step-up logic
        sceneManager.activeChunkManager.getBlocksAround(newPosition, 10f, nearbyBlocks)

        // Loop over the small 'nearbyBlocks' array
        for (block in nearbyBlocks) {
            if (!block.blockType.hasCollision) continue

            val blockBounds = block.getBoundingBox(4f, tempBlockBounds) // Use 4f as blockSize

            if (enemyBounds.intersects(blockBounds)) {
                // A collision occurred. Check if it's a step or a wall.
                val enemyBottomY = enemyBounds.min.y
                val blockTopY = blockBounds.max.y

                // If the enemy's bottom is above or very close to the block's top, it's a valid surface, not a wall.
                if (enemyBottomY >= blockTopY - 0.5f) { // 0.5f tolerance
                    continue // It's a valid step, not a wall
                }

                // It's a real side collision with a wall.
                return false
            }
        }

        // Check against houses
        sceneManager.activeHouses.forEach {
            if (it.collidesWithMesh(enemyBounds)) {
                return false
            }
        }

        // Check against solid interior objects
        sceneManager.activeInteriors.forEach { interior ->
            if (interior.interiorType.hasCollision && interior.collidesWithMesh(enemyBounds)) {
                return false
            }
        }

        // If we passed all checks, the move is valid
        return true
    }

    fun renderEnemies(camera: Camera, environment: Environment, enemies: Array<GameEnemy>) {
        if (enemies.isEmpty) return

        billboardShaderProvider.setEnvironment(environment)
        billboardModelBatch.begin(camera)

        // Collect all enemy instances.
        renderableInstances.clear()
        for (enemy in enemies) {
            // Only render enemies who are not in a car
            if (!enemy.isInCar) {
                renderableInstances.add(enemy.modelInstance)
            }
        }

        // Render all enemies at once.
        billboardModelBatch.render(renderableInstances, environment)
        billboardModelBatch.end()
    }

    // --- UI Interaction Methods ---
    fun nextEnemyType() {
        currentEnemyTypeIndex = (currentEnemyTypeIndex + 1) % EnemyType.entries.size
        currentSelectedEnemyType = EnemyType.entries[currentEnemyTypeIndex]
    }

    fun previousEnemyType() {
        currentEnemyTypeIndex = if (currentEnemyTypeIndex > 0) currentEnemyTypeIndex - 1 else EnemyType.entries.size - 1
        currentSelectedEnemyType = EnemyType.entries[currentEnemyTypeIndex]
    }

    fun nextBehavior() {
        currentBehaviorIndex = (currentBehaviorIndex + 1) % EnemyBehavior.entries.size
        currentSelectedBehavior = EnemyBehavior.entries[currentBehaviorIndex]
    }

    fun previousBehavior() {
        currentBehaviorIndex = if (currentBehaviorIndex > 0) currentBehaviorIndex - 1 else EnemyBehavior.entries.size - 1
        currentSelectedBehavior = EnemyBehavior.entries[currentBehaviorIndex]
    }

    fun dispose() {
        enemyModels.values.forEach { it.dispose() }
        enemyTextures.values.forEach { it.dispose() }
        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()
    }
}
