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
import com.badlogic.gdx.utils.Array
import java.util.*
import kotlin.math.max

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
    GEORGE_MELES("George Meles", "textures/characters/george_meles.png", 3.2f, 4.2f, 120f, 5.5f),
    GUNTHER("Gunther", "textures/characters/Gunther.png", 3.5f, 4.5f, 180f, 5f),
    CORRUPT_DETECTIVE("Corrupt Detective", "textures/characters/detective.png", 3f, 4.2f, 150f, 6.5f),
    LADY_FOX("Lady Fox", "textures/characters/lady_fox.png", 2.8f, 4f, 80f, 8f),
    MAFIA_BOSS("Mafia Boss", "textures/characters/mafia_boss.png", 3.8f, 4.8f, 500f, 4f)
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
    HIDING      // Reached a hiding spot (Coward)
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
    // AI state properties
    var currentState: AIState = AIState.IDLE
    var stateTimer: Float = 0f
    var targetPosition: Vector3? = null

    // Collision properties
    private val boundsSize = Vector3(enemyType.width, enemyType.height, enemyType.width)
    private val boundingBox = BoundingBox()

    fun updateVisuals(deltaTime: Float, cameraPosition: Vector3) {
        // Billboard rotation to always face the camera
        val direction = Vector3(cameraPosition).sub(position).nor()
        direction.y = 0f // Keep rotation horizontal
        val angle = Math.toDegrees(Math.atan2(direction.x.toDouble(), direction.z.toDouble())).toFloat()

        modelInstance.transform.setToTranslation(position)
        modelInstance.transform.rotate(Vector3.Y, angle)
    }

    fun getBoundingBox(): BoundingBox {
        boundingBox.set(
            Vector3(position.x - boundsSize.x / 2, position.y - boundsSize.y / 2, position.z - boundsSize.z / 2),
            Vector3(position.x + boundsSize.x / 2, position.y + boundsSize.y / 2, position.z + boundsSize.z / 2)
        )
        return boundingBox
    }
}

// --- ENEMY MANAGEMENT SYSTEM ---

class EnemySystem : IFinePositionable {
    private val enemyModels = mutableMapOf<EnemyType, Model>()
    private val enemyTextures = mutableMapOf<EnemyType, Texture>()
    private lateinit var billboardModelBatch: ModelBatch
    private lateinit var billboardShaderProvider: BillboardShaderProvider

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

    // Physics constants
    private val FALL_SPEED = 25f
    private val MAX_STEP_HEIGHT = 4.0f
    override var finePosMode: Boolean = false
    override val fineStep: Float = 0.25f

    fun initialize() {
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

    fun createEnemy(position: Vector3, enemyType: EnemyType, behavior: EnemyBehavior): GameEnemy? {
        val model = enemyModels[enemyType] ?: return null
        val instance = ModelInstance(model)
        return GameEnemy(
            modelInstance = instance,
            enemyType = enemyType,
            behaviorType = behavior,
            position = position.cpy()
        )
    }

    fun update(deltaTime: Float, playerSystem: PlayerSystem, sceneManager: SceneManager, blockSize: Float) {
        if (playerSystem.isDriving) return // Don't update AI if player is safe in a car

        val playerPos = playerSystem.getPosition()

        for (enemy in sceneManager.activeEnemies) {
            applyPhysics(enemy, deltaTime, sceneManager, blockSize)
            updateAI(enemy, playerPos, deltaTime, sceneManager)
            enemy.updateVisuals(deltaTime, sceneManager.cameraManager.camera.position)
        }
    }

    private fun applyPhysics(enemy: GameEnemy, deltaTime: Float, sceneManager: SceneManager, blockSize: Float) {
        val supportY = sceneManager.findHighestSupportY(enemy.position.x, enemy.position.z, enemy.enemyType.width / 2f, blockSize)
        val enemyFootY = enemy.position.y - (enemy.enemyType.height / 2f)
        val effectiveSupportY = if (supportY - enemyFootY <= MAX_STEP_HEIGHT) supportY else enemyFootY
        val targetY = effectiveSupportY + (enemy.enemyType.height / 2f)
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
        val nearbyBlocks = sceneManager.activeBlocks.filter { it.position.dst(position) < hideSearchRadius }
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
        val enemyBounds = enemy.getBoundingBox() // Get the box at the new position
        enemyBounds.set(
            enemyBounds.min.set(newPosition.x - enemy.enemyType.width / 2f, newPosition.y - enemy.enemyType.height / 2f, newPosition.z - enemy.enemyType.width / 2f),
            enemyBounds.max.set(newPosition.x + enemy.enemyType.width / 2f, newPosition.y + enemy.enemyType.height / 2f, newPosition.z + enemy.enemyType.width / 2f)
        )

        sceneManager.activeBlocks.forEach {
            if (it.getBoundingBox(4f).intersects(enemyBounds)) return false
        }
        sceneManager.activeHouses.forEach {
            if (it.collidesWithMesh(enemyBounds)) return false
        }
        return true
    }

    fun render(camera: Camera, environment: Environment) {
        billboardShaderProvider.setEnvironment(environment)
        billboardModelBatch.begin(camera)
        // This assumes activeEnemies is now accessible from somewhere, like SceneManager
        // You will need to adjust this based on your final architecture.
        // For now, let's assume a parameter is passed.
        // for (enemy in activeEnemies) {
        //     billboardModelBatch.render(enemy.modelInstance, environment)
        // }
        billboardModelBatch.end()
    }

    // This method needs to be called from the actual render loop with the active enemies.
    fun renderEnemies(camera: Camera, environment: Environment, enemies: Array<GameEnemy>) {
        billboardModelBatch.begin(camera)
        for (enemy in enemies) {
            billboardModelBatch.render(enemy.modelInstance, environment)
        }
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
