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
    MOUSE_THUG("Mouse Thug", "textures/characters/mouse.png", 3f, 4f, 100f, 6f),
    GUNTHER("Gunther", "textures/characters/Gunther.png", 7.0f, 8.0f, 180f, 5f),
    CORRUPT_DETECTIVE("Corrupt Detective", "textures/characters/detective.png", 3f, 4.2f, 150f, 6.5f),
    MAFIA_BOSS("Mafia Boss", "textures/characters/mafia_boss.png", 3.8f, 4.8f, 500f, 4f),
}

enum class EnemyBehavior(val displayName: String) {
    STATIONARY_SHOOTER("Stationary"),
    AGGRESSIVE_RUSHER("Rusher"),
    SKIRMISHER("Skirmisher"),
    NEUTRAL("Neutral"),
    COWARD_HIDER("Coward")
}

// AI State for the enemy's state machine
enum class AIState {
    IDLE,
    CHASING,
    FLEEING,
    FLEEING_AFTER_ATTACK,
    SEARCHING,
    HIDING,
    RELOADING,
    DYING,
    PATROLLING_IN_CAR
}

data class GameEnemy(
    val id: String = UUID.randomUUID().toString(),
    val modelInstance: ModelInstance,
    val enemyType: EnemyType,
    val behaviorType: EnemyBehavior,
    var position: Vector3,
    var health: Float = enemyType.baseHealth,
    val weaponCollectionPolicy: WeaponCollectionPolicy = WeaponCollectionPolicy.CANNOT_COLLECT,
    val canCollectItems: Boolean = true,
    val inventory: MutableList<GameItem> = mutableListOf(),
    val weapons: MutableMap<WeaponType, Int> = mutableMapOf(), // Weapon -> Ammo in reserve
    var equippedWeapon: WeaponType = WeaponType.UNARMED
) {
    var currentBehavior: EnemyBehavior = behaviorType
    var provocationLevel: Float = 0f

    var isOnFire: Boolean = false
    var onFireTimer: Float = 0f
    var initialOnFireDuration: Float = 0f
    var onFireDamagePerSecond: Float = 0f
    @Transient lateinit var physics: PhysicsComponent
    @Transient var currentTargetPathNodeId: String? = null

    var attackTimer: Float = 0f
    var currentMagazineCount: Int = 0
    var ammo: Int = 0
    var bleedTimer: Float = 0f
    var bloodDripSpawnTimer: Float = 0f

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

    // Pathfinding properties
    @Transient var path: Queue<Vector3> = LinkedList()
    @Transient var pathRequestTimer: Float = 0f
    @Transient var waypoint: Vector3? = null

    fun enterCar(car: GameCar) {
        val seat = car.addOccupant(this)
        if (seat != null) {
            isInCar = true
            drivingCar = car
            currentSeat = seat
            drivingCar?.modelInstance?.userData = "car"
            println("${this.enemyType.displayName} entered a car.")
        }
    }

    fun exitCar() {
        val car = drivingCar ?: return
        car.removeOccupant(this)
        drivingCar?.modelInstance?.userData = null
        isInCar = false
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
        const val BLEED_DURATION = 1.0f
        const val BLOOD_DRIP_INTERVAL = 0.7f
    }

    fun takeDamage(damage: Float, type: DamageType, sceneManager: SceneManager): Boolean {
        if (isInCar) return false

        if (health <= 0) return false // Already dead, don't process more damage

        // Check if hurting this enemy triggers a mission
        sceneManager.game.missionSystem.onPlayerHurtEnemy(this.id)

        // Provoke neutral enemies BEFORE applying damage, so they can react
        if (this.currentBehavior == EnemyBehavior.NEUTRAL) {
            this.provocationLevel += 25f // A simple provocation value per hit
            if (this.provocationLevel >= 50f) { // Threshold to turn hostile
                println("${this.enemyType.displayName} has been provoked and is now hostile!")
                this.currentBehavior = EnemyBehavior.AGGRESSIVE_RUSHER
            }
        }

        health -= damage
        // The type of the killing blow is what matters most.
        if (health > 0 && (type == DamageType.GENERIC || type == DamageType.MELEE)) {
            this.bleedTimer = BLEED_DURATION
        }

        // Check if the enemy should try to flee in a car
        if (health > 0 && !isInCar && Random.nextFloat() < sceneManager.enemySystem.CAR_FLEE_CHANCE) { // Using the 15% chance directly
            val closestCar = sceneManager.activeCars
                .filter { !it.isLocked && it.seats.first().occupant == null && it.state == CarState.DRIVABLE }
                .minByOrNull { it.position.dst2(this.position) }

            if (closestCar != null && this.position.dst(closestCar.position) < sceneManager.enemySystem.CAR_SEARCH_RADIUS) {
                println("${this.enemyType.displayName} is attempting to steal a car to flee!")
                this.enterCar(closestCar)
                this.currentState = AIState.FLEEING // Ensure it's in a fleeing state
            }
        }

        if (health <= 0) {
            lastDamageType = type
        }
        println("${this.enemyType.displayName} took $damage $type damage. HP remaining: ${this.health.coerceAtLeast(0f)}")
        return health <= 0
    }
}

class EnemySystem : IFinePositionable {
    private lateinit var characterPhysicsSystem: CharacterPhysicsSystem
    private lateinit var pathfindingSystem: PathfindingSystem
    private val enemyModels = mutableMapOf<EnemyType, Model>()
    private val enemyTextures = mutableMapOf<EnemyType, Texture>()
    private lateinit var billboardModelBatch: ModelBatch
    private lateinit var billboardShaderProvider: BillboardShaderProvider
    private val renderableInstances = Array<ModelInstance>()

    // UI selection state
    var currentSelectedEnemyType = EnemyType.MOUSE_THUG
    var currentSelectedBehavior = EnemyBehavior.STATIONARY_SHOOTER
    var currentEnemyTypeIndex = 0
    var currentBehaviorIndex = 0

    private val BEHAVIOR_REEVALUATION_INTERVAL = 2.0f
    private val FIRE_FLEE_DISTANCE = 15f
    private val SHOOTER_IDEAL_DISTANCE = 15f
    private val SHOOTER_MIN_DISTANCE = 8f

    private val PATH_RECALCULATION_INTERVAL = 1.0f
    private val WAYPOINT_TOLERANCE = 1.5f
    private val COMBAT_DEPTH_TOLERANCE = 1.5f
    private val activationRange = 150f

    // Physics constants
    override var finePosMode: Boolean = false
    override val fineStep: Float = 0.25f

    private val FADE_OUT_DURATION = 1.5f
    private val ASH_SPAWN_START_TIME = 1.5f

    lateinit var sceneManager: SceneManager
    lateinit var raycastSystem: RaycastSystem
    private val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)
    private val tempVec3 = Vector3()
    private var blockSize: Float = 4f
    private val enemyWeaponTextures = mutableMapOf<EnemyType, Map<WeaponType, Texture>>()

    internal val CAR_FLEE_CHANCE = 0.15f
    internal val CAR_SEARCH_RADIUS = 5f
    private val FLEE_EXIT_DISTANCE = 60f

    fun initialize(blockSize: Float, characterPhysicsSystem: CharacterPhysicsSystem, pathfindingSystem: PathfindingSystem) {
        this.blockSize = blockSize
        this.raycastSystem = RaycastSystem(blockSize)
        this.characterPhysicsSystem = characterPhysicsSystem
        this.pathfindingSystem = pathfindingSystem

        billboardShaderProvider = BillboardShaderProvider()
        billboardModelBatch = ModelBatch(billboardShaderProvider)
        billboardShaderProvider.setBillboardLightingStrength(0.9f)
        billboardShaderProvider.setMinLightLevel(0.3f)

        // Pre-load weapon textures for enemies that have them
        try {
            val mouseWeaponTextures = mapOf(
                WeaponType.LIGHT_TOMMY_GUN to Texture(Gdx.files.internal("textures/characters/mouse_tommy_gun.png")),
                WeaponType.SHOTGUN to Texture(Gdx.files.internal("textures/characters/mouse_shotgun.png"))
            )
            enemyWeaponTextures[EnemyType.MOUSE_THUG] = mouseWeaponTextures
        } catch (e: Exception) {
            println("ERROR: Could not load weapon textures for Mouse Thug: ${e.message}")
        }

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
        // Check the current editor mode
        if (sceneManager.game.uiManager.currentEditorMode == EditorMode.MISSION) {
            handleMissionPlacement(ray)
            return
        }

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            val surfaceY = findHighestSurfaceYAt(tempVec3.x, tempVec3.z)
            val enemyType = sceneManager.game.uiManager.enemySelectionUI.getSpawnConfig(Vector3()).enemyType
            val enemyPosition = Vector3(tempVec3.x, surfaceY + enemyType.height / 2f, tempVec3.z)

            val config = sceneManager.game.uiManager.enemySelectionUI.getSpawnConfig(enemyPosition)

            val newEnemy = createEnemy(config)
            if (newEnemy != null) {
                sceneManager.activeEnemies.add(newEnemy)
                sceneManager.game.lastPlacedInstance = newEnemy
                println("Placed ${newEnemy.enemyType.displayName} with custom config at $enemyPosition")
            }
        }
    }

    private fun handleMissionPlacement(ray: Ray) {
        val mission = sceneManager.game.uiManager.selectedMissionForEditing
        if (mission == null) {
            sceneManager.game.uiManager.updatePlacementInfo("ERROR: No mission selected for editing!")
            return
        }

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            // First, get the config with a temporary position to determine the enemy's height
            val tempConfig = sceneManager.game.uiManager.enemySelectionUI.getSpawnConfig(Vector3.Zero)
            val surfaceY = findHighestSurfaceYAt(tempVec3.x, tempVec3.z)
            val finalEnemyPosition = Vector3(tempVec3.x, surfaceY + tempConfig.enemyType.height / 2f, tempVec3.z)

            // Now, get the final, complete config using the correct spawn position
            val finalConfig = sceneManager.game.uiManager.enemySelectionUI.getSpawnConfig(finalEnemyPosition)

            val currentSceneId = if (sceneManager.currentScene == SceneType.HOUSE_INTERIOR) {
                sceneManager.getCurrentHouse()?.id ?: "WORLD"
            } else {
                "WORLD"
            }

            // 1. Create the GameEvent for the mission file, now including ALL config details
            val event = GameEvent(
                type = GameEventType.SPAWN_ENEMY,
                spawnPosition = finalEnemyPosition,
                sceneId = currentSceneId,
                targetId = "enemy_${UUID.randomUUID()}",
                enemyType = finalConfig.enemyType,
                enemyBehavior = finalConfig.behavior,
                healthSetting = finalConfig.healthSetting,
                customHealthValue = finalConfig.customHealthValue,
                minRandomHealth = finalConfig.minRandomHealth,
                maxRandomHealth = finalConfig.maxRandomHealth,
                initialWeapon = finalConfig.initialWeapon,
                ammoSpawnMode = finalConfig.ammoSpawnMode,
                setAmmoValue = finalConfig.setAmmoValue,
                weaponCollectionPolicy = finalConfig.weaponCollectionPolicy,
                canCollectItems = finalConfig.canCollectItems
            )

            // 2. Add the event and save the mission
            mission.eventsOnStart.add(event)
            sceneManager.game.missionSystem.saveMission(mission)

            // 3. Create a temporary "preview" enemy to see in the world
            val previewConfig = finalConfig.copy(id = event.targetId)
            val previewEnemy = createEnemy(previewConfig)
            if (previewEnemy != null) {
                previewEnemy.modelInstance.transform.setToTranslation(previewEnemy.position)

                sceneManager.activeMissionPreviewEnemies.add(previewEnemy)
                sceneManager.game.lastPlacedInstance = previewEnemy
                sceneManager.game.uiManager.updatePlacementInfo("Added SPAWN_ENEMY to '${mission.title}'")
                sceneManager.game.uiManager.missionEditorUI.refreshEventWidgets()
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

    fun createEnemy(config: EnemySpawnConfig): GameEnemy? {
        val model = enemyModels[config.enemyType] ?: return null
        val instance = ModelInstance(model)
        instance.userData = "character"

        val finalHealth = when (config.healthSetting) {
            HealthSetting.FIXED_DEFAULT -> config.enemyType.baseHealth
            HealthSetting.FIXED_CUSTOM -> config.customHealthValue
            HealthSetting.RANDOM_RANGE -> Random.nextFloat() * (config.maxRandomHealth - config.minRandomHealth) + config.minRandomHealth
        }

        val enemy = GameEnemy(
            id = config.id ?: UUID.randomUUID().toString(),
            modelInstance = instance,
            enemyType = config.enemyType,
            behaviorType = config.behavior, // This is the initially assigned behavior
            position = config.position.cpy(),
            health = finalHealth,
            weaponCollectionPolicy = config.weaponCollectionPolicy,
            canCollectItems = config.canCollectItems,
            equippedWeapon = config.initialWeapon
        )
        // Set current behavior from the config
        enemy.currentBehavior = config.behavior

        // Add initial weapon and set ammo based on UI config
        if (config.initialWeapon != WeaponType.UNARMED) {
            val ammoInReserve = when (config.ammoSpawnMode) {
                AmmoSpawnMode.FIXED -> config.initialWeapon.magazineSize * 2 // Default: 2 extra clips
                AmmoSpawnMode.SET -> config.setAmmoValue
                AmmoSpawnMode.RANDOM -> 0
            }
            enemy.weapons[config.initialWeapon] = ammoInReserve
        }

        // Load first magazine from the calculated reserve
        val ammoToLoad = minOf(enemy.weapons.getOrDefault(enemy.equippedWeapon, 0), enemy.equippedWeapon.magazineSize)
        enemy.currentMagazineCount = ammoToLoad
        enemy.weapons[enemy.equippedWeapon] = enemy.weapons.getOrDefault(enemy.equippedWeapon, 0) - ammoToLoad

        // Set texture based on equipped weapon
        updateEnemyTexture(enemy)

        // Create and attach the physics component
        enemy.physics = PhysicsComponent(
            position = config.position.cpy(),
            size = Vector3(config.enemyType.width, config.enemyType.height, config.enemyType.width),
            speed = config.enemyType.speed
        )
        enemy.physics.updateBounds()

        // After the enemy is fully set up, validate its starting behavior.
        val hasRangedWeapon = enemy.weapons.any { (w, a) -> w.actionType == WeaponActionType.SHOOTING && a > 0 } ||
            (enemy.equippedWeapon.actionType == WeaponActionType.SHOOTING && enemy.currentMagazineCount > 0)

        // If the intended behavior is a shooter, but they have no gun, force them to be a rusher.
        if (enemy.currentBehavior == EnemyBehavior.STATIONARY_SHOOTER && !hasRangedWeapon) {
            println("Configuration Warning: An unarmed Stationary Shooter was spawned. Forcing behavior to AGGRESSIVE_RUSHER.")
            enemy.currentBehavior = EnemyBehavior.AGGRESSIVE_RUSHER
        }

        return enemy
    }

    fun startDeathSequence(enemy: GameEnemy, sceneManager: SceneManager) {
        if (enemy.currentState == AIState.DYING) return // Already dying

        dropAllItemsOnDeath(enemy)

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

    fun setOnFire(enemy: GameEnemy, duration: Float, dps: Float) {
        if (enemy.isOnFire) return // Already on fire, don't re-apply
        enemy.isOnFire = true
        enemy.onFireTimer = duration
        enemy.initialOnFireDuration = duration
        enemy.onFireDamagePerSecond = dps
    }

    fun applyKnockback(enemy: GameEnemy, force: Vector3) {
        if (!enemy.isInCar) {
            enemy.physics.knockbackVelocity.set(force)
        }
    }

    fun update(deltaTime: Float, playerSystem: PlayerSystem, sceneManager: SceneManager, blockSize: Float) {
       val playerPos = playerSystem.getPosition()
        val iterator = sceneManager.activeEnemies.iterator()

        while (iterator.hasNext()) {
            val enemy = iterator.next()

            val isObjectiveTarget = sceneManager.game.missionSystem.activeMission?.getCurrentObjective()?.completionCondition?.targetId == enemy.id
            if (playerSystem.isDriving && !enemy.isInCar && !isObjectiveTarget) {
                continue // Skip this specific on-foot enemy, but continue the loop for others.
            }

            // First, handle AI logic (either driving or on-foot)
            if (!finePosMode) {
                updateAI(enemy, playerSystem, deltaTime, sceneManager)
            }

            // If the enemy is in a car, their update is complete. Skip on-foot logic.
            if (enemy.isInCar) {
                continue
            }

            // HANDLE ON FIRE STATE (DAMAGE & VISUALS)
            if (enemy.isOnFire) {
                enemy.onFireTimer -= deltaTime
                if (enemy.onFireTimer <= 0) {
                    enemy.isOnFire = false
                } else {
                    // Damage falloff over the duration of the effect
                    val progress = (enemy.onFireTimer / enemy.initialOnFireDuration).coerceIn(0f, 1f)
                    val currentDps = enemy.onFireDamagePerSecond * progress
                    val damageThisFrame = currentDps * deltaTime

                    if (enemy.takeDamage(damageThisFrame, DamageType.FIRE, sceneManager) && enemy.currentState != AIState.DYING) {
                        sceneManager.enemySystem.startDeathSequence(enemy, sceneManager)
                    }

                    // 1% chance for big head on fire effect
                    if (Random.nextFloat() < 0.01f) {
                        val particlePos = enemy.position.cpy().add(0f, enemy.enemyType.height * 0.8f, 0f)
                        sceneManager.game.particleSystem.spawnEffect(ParticleEffectType.FIRE_FLAME, particlePos)
                    }

                    // Frequent, smaller body flames
                    if (Random.nextFloat() < 0.6f) {
                        val halfWidth = enemy.enemyType.width / 2f
                        val offsetX = (Random.nextFloat() - 0.5f) * (halfWidth * 1.5f)

                        // Spawn around the character's torso
                        val particlePos = enemy.position.cpy().add(offsetX, -enemy.enemyType.height * 0.2f, 0f)

                        sceneManager.game.particleSystem.spawnEffect(ParticleEffectType.BODY_FLAME, particlePos)
                    }
                }
            }

            // Decrement the attack timer
            if (enemy.attackTimer > 0f) {
                enemy.attackTimer -= deltaTime
            }

            // DYING STATE LOGIC
            if (enemy.currentState == AIState.DYING) {
                enemy.fadeOutTimer -= deltaTime

                // Check if it's time to spawn ash
                if (enemy.lastDamageType == DamageType.FIRE && !enemy.ashSpawned && enemy.fadeOutTimer <= ASH_SPAWN_START_TIME) {
                    // Start spawning when fadeOutTimer is less than or equal to the ash fade-in time
                    val groundY = sceneManager.findHighestSupportY(enemy.position.x, enemy.position.z, enemy.position.y, 0.1f, blockSize)
                    val ashPosition = Vector3(enemy.position.x, groundY + 0.86f, enemy.position.z)

                    sceneManager.game.particleSystem.spawnEffect(ParticleEffectType.BURNED_ASH, ashPosition)
                    enemy.ashSpawned = true // Spawn only once
                }

                if (enemy.fadeOutTimer <= 0f) {
                    iterator.remove() // Completely faded out, now remove
                    continue
                }

                enemy.blendingAttribute.opacity = (enemy.fadeOutTimer / FADE_OUT_DURATION).coerceIn(0f, 1f)

                // Don't process any other logic for a dying enemy
                continue
            }

            if (enemy.bleedTimer > 0f) {
                enemy.bleedTimer -= deltaTime

                // Check if the enemy is moving and it's time to spawn a drip
                if (enemy.physics.isMoving) {
                    enemy.bloodDripSpawnTimer -= deltaTime
                    if (enemy.bloodDripSpawnTimer <= 0f) {
                        enemy.bloodDripSpawnTimer = GameEnemy.BLOOD_DRIP_INTERVAL

                        // Spawn a drip at the enemy's position with a slight random offset
                        if (sceneManager.game.uiManager.getViolenceLevel() != ViolenceLevel.NO_VIOLENCE) {
                            val spawnPosition = enemy.physics.position.cpy().add(
                                (Random.nextFloat() - 0.5f) * 1f,
                                (Random.nextFloat() - 0.5f) * 2f,
                                (Random.nextFloat() - 0.5f) * 1f
                            )
                            sceneManager.game.particleSystem.spawnEffect(ParticleEffectType.BLOOD_DRIP, spawnPosition)
                        }
                    }
                }
            }

            if (enemy.currentState != AIState.DYING) {
                checkForItemPickups(enemy, sceneManager)
            }

            // If the enemy is outside our activation range, skip its update entirely.
            val distanceToPlayer = enemy.physics.position.dst(playerPos)
            if (distanceToPlayer > activationRange) {
                if (enemy.currentState != AIState.IDLE && enemy.currentState != AIState.DYING) {
                    enemy.currentState = AIState.IDLE
                    enemy.targetPosition = null // Clear any old target
                }
                continue // Skip AI and physics for distant enemies
            }

            enemy.position.set(enemy.physics.position)

            if (enemy.currentState != AIState.DYING) {
                reEvaluateBehavior(enemy)
            }

            enemy.isMoving = enemy.physics.isMoving
            enemy.wobbleAngle = enemy.physics.wobbleAngle
            enemy.facingRotationY = enemy.physics.facingRotationY

            // Update the enemy's visual transform.
            enemy.updateVisuals()
        }
    }

    private fun handleWeaponSwitch(enemy: GameEnemy) {
        println("${enemy.enemyType.displayName} is out of ammo for ${enemy.equippedWeapon.displayName}, evaluating options...")

        // Temporarily put the current mag back to calculate total ammo correctly
        val currentAmmo = enemy.weapons.getOrDefault(enemy.equippedWeapon, 0) + enemy.currentMagazineCount
        if (enemy.equippedWeapon != WeaponType.UNARMED) {
            enemy.weapons[enemy.equippedWeapon] = currentAmmo
        }
        enemy.currentMagazineCount = 0

        // Default to unarmed before checking for better options
        enemy.equippedWeapon = WeaponType.UNARMED

        // 1. Find the best available RANGED weapon (highest damage) with ammo.
        val bestRanged = enemy.weapons.entries
            .filter { (weapon, ammo) -> weapon.actionType == WeaponActionType.SHOOTING && ammo > 0 }
            .maxByOrNull { it.key.damage }

        if (bestRanged != null) {
            enemy.equippedWeapon = bestRanged.key
            println(" -> Switching to best ranged weapon: ${bestRanged.key.displayName}")
        } else {
            // 2. If no ranged weapons are usable, find the best MELEE weapon.
            val bestMelee = enemy.weapons.keys
                .filter { it.actionType == WeaponActionType.MELEE }
                .maxByOrNull { it.damage }

            if (bestMelee != null) {
                enemy.equippedWeapon = bestMelee
                println(" -> No ranged weapons available. Switching to best melee weapon: ${bestMelee.displayName}")
            } else {
                println(" -> No usable weapons left! Switching to fists.")
            }
        }

        // --- Load Magazine for the new weapon ---
        val ammoInReserve = enemy.weapons.getOrDefault(enemy.equippedWeapon, 0)
        val ammoToLoad = minOf(ammoInReserve, enemy.equippedWeapon.magazineSize)
        enemy.currentMagazineCount = ammoToLoad
        // Subtract the loaded ammo from the reserve
        enemy.weapons[enemy.equippedWeapon] = ammoInReserve - ammoToLoad

        // Update the enemy's texture to match the new weapon
        updateEnemyTexture(enemy)
    }

    fun updateEnemyTexture(enemy: GameEnemy) {
        val weaponTexture = enemyWeaponTextures[enemy.enemyType]?.get(enemy.equippedWeapon)
        val defaultTexture = enemyTextures[enemy.enemyType]

        val textureToApply = weaponTexture ?: defaultTexture

        if (textureToApply != null) {
            val material = enemy.modelInstance.materials.first()
            val texAttr = material.get(TextureAttribute.Diffuse) as? TextureAttribute
            if (texAttr?.textureDescription?.texture != textureToApply) {
                material.set(TextureAttribute.createDiffuse(textureToApply))
            }
        }
    }

    private fun checkForItemPickups(enemy: GameEnemy, sceneManager: SceneManager) {
        val pickupRadius = 3f
        val itemsToRemove = mutableListOf<GameItem>()
        var pickedUpNewWeapon = false

        for (item in sceneManager.activeItems) {
            if (item.pickupDelay <= 0f && item.position.dst(enemy.position) < pickupRadius) {
                if (item.itemType.correspondingWeapon != null) { // It's a weapon
                    if (enemy.weaponCollectionPolicy != WeaponCollectionPolicy.CANNOT_COLLECT) {
                        println("${enemy.enemyType.displayName} collected ${item.itemType.displayName}")
                        val weaponType = item.itemType.correspondingWeapon
                        val currentAmmo = enemy.weapons.getOrDefault(weaponType, 0)
                        enemy.weapons[weaponType] = currentAmmo + item.ammo
                        itemsToRemove.add(item)
                        pickedUpNewWeapon = true

                        // If allowed, switch to the new weapon if it's better
                        if (enemy.weaponCollectionPolicy == WeaponCollectionPolicy.COLLECT_AND_USE) {
                            if (weaponType.damage > enemy.equippedWeapon.damage) {
                                enemy.equippedWeapon = weaponType
                                // We need to load the magazine for the newly equipped weapon
                                handleWeaponSwitch(enemy) // This function now handles loading the mag
                            }
                        }
                    }
                } else if (enemy.canCollectItems) { // It's a non-weapon item like money
                    println("${enemy.enemyType.displayName} collected ${item.itemType.displayName}")
                    // Handle money or other mission items here
                    enemy.inventory.add(item)
                    itemsToRemove.add(item)
                }
            }
        }

        itemsToRemove.forEach { sceneManager.activeItems.removeValue(it, true) }

        // If we picked up any new weapon, update the texture
        if (pickedUpNewWeapon) {
            updateEnemyTexture(enemy)
        }
    }

    private fun reEvaluateBehavior(enemy: GameEnemy) {
        enemy.stateTimer += Gdx.graphics.deltaTime
        if (enemy.stateTimer < BEHAVIOR_REEVALUATION_INTERVAL) {
            return // Not time to re-evaluate yet
        }
        enemy.stateTimer = 0f // Reset timer

        // Check if the enemy has any usable ranged weapon
        val hasRangedWeapon = enemy.weapons.any { (weapon, ammo) ->
            weapon.actionType == WeaponActionType.SHOOTING && ammo > 0
        } || (enemy.equippedWeapon.actionType == WeaponActionType.SHOOTING && enemy.currentMagazineCount > 0)


        // CASE 1: Enemy is currently a Rusher. Should it switch back to being a Shooter?
        if (enemy.currentBehavior == EnemyBehavior.AGGRESSIVE_RUSHER && hasRangedWeapon) {
            // 70% chance to switch back to a more tactical role
            if (Random.nextFloat() < 0.7f) {
                println("${enemy.enemyType.displayName} found a ranged weapon and is switching to STATIONARY_SHOOTER tactics.")
                enemy.currentBehavior = EnemyBehavior.STATIONARY_SHOOTER
                // Immediately switch to the best available ranged weapon
                handleWeaponSwitch(enemy)
            } else {
                println("${enemy.enemyType.displayName} found a ranged weapon but decided to keep rushing!")
            }
        }

        // CASE 2: Enemy is a Stationary Shooter but has no ranged weapons left. It MUST switch.
        if (enemy.currentBehavior == EnemyBehavior.STATIONARY_SHOOTER && !hasRangedWeapon) {
            println("${enemy.enemyType.displayName} is out of ammo! Switching to AGGRESSIVE RUSH.")
            enemy.currentBehavior = EnemyBehavior.AGGRESSIVE_RUSHER
            // Switch to the best available melee weapon (or fists)
            handleWeaponSwitch(enemy)
        }
    }

    private fun dropAllItemsOnDeath(enemy: GameEnemy) {
        val modifiers = sceneManager.game.missionSystem.activeModifiers
        if (modifiers?.disableNoItemDrops == true) {
            println("${enemy.enemyType.displayName} died, but item drops are disabled by the current mission.")
            return // Exit the function immediately, dropping nothing.
        }

        println("${enemy.enemyType.displayName} is dropping its inventory.")
        val groundY = sceneManager.findHighestSupportY(enemy.position.x, enemy.position.z, enemy.position.y, 0.1f, blockSize)
        val baseDropPosition = Vector3(enemy.position.x, groundY, enemy.position.z)
        val itemSystem = sceneManager.game.itemSystem

        // A) Separate money from other items in the inventory list.
        val (moneyItems, otherItems) = enemy.inventory.partition { it.itemType == ItemType.MONEY_STACK }

        // B) Calculate the total value of all collected money stacks.
        val totalMoneyValue = moneyItems.sumOf { it.value }

        // C) If there's any money, drop it as a SINGLE consolidated stack.
        if (totalMoneyValue > 0) {
            val consolidatedMoneyStack = itemSystem.createItem(baseDropPosition, ItemType.MONEY_STACK)
            if (consolidatedMoneyStack != null) {
                consolidatedMoneyStack.value = totalMoneyValue // Set the combined value!
                consolidatedMoneyStack.position.add((Random.nextFloat() - 0.5f) * 1.5f, 0.2f, (Random.nextFloat() - 0.5f) * 1.5f)
                sceneManager.activeItems.add(consolidatedMoneyStack)
                println(" -> Dropped consolidated money stack worth $$totalMoneyValue")
            }
        }

        // D) Now, drop all other non-money items individually.
        for (item in otherItems) {
            val droppedItem = itemSystem.createItem(baseDropPosition, item.itemType)
            if (droppedItem != null) {
                droppedItem.value = item.value // Ensure it keeps its original value
                droppedItem.position.add(
                    (Random.nextFloat() - 0.5f) * 1.5f,
                    0.2f,
                    (Random.nextFloat() - 0.5f) * 1.5f
                )
                sceneManager.activeItems.add(droppedItem)
                println(" -> Dropped ${item.itemType.displayName}")
            }
        }

        // First, put the ammo from the current magazine back into the reserves map for easy calculation.
        if (enemy.equippedWeapon != WeaponType.UNARMED) {
            val reserveAmmo = enemy.weapons.getOrDefault(enemy.equippedWeapon, 0)
            enemy.weapons[enemy.equippedWeapon] = reserveAmmo + enemy.currentMagazineCount
        }

        // Now, iterate through every weapon the enemy was carrying.
        for ((weaponType, totalAmmo) in enemy.weapons) {
            if (weaponType == WeaponType.UNARMED) continue // Don't drop fists

            val weaponItemType = ItemType.entries.find { it.correspondingWeapon == weaponType }
            if (weaponItemType != null) {
                val droppedWeapon = itemSystem.createItem(baseDropPosition, weaponItemType)
                if (droppedWeapon != null) {
                    // Set the exact total ammo count for this weapon
                    droppedWeapon.ammo = totalAmmo
                    droppedWeapon.position.add((Random.nextFloat() - 0.5f) * 1.5f, 0.2f, (Random.nextFloat() - 0.5f) * 1.5f)
                    sceneManager.activeItems.add(droppedWeapon)
                    println(" -> Dropped ${weaponItemType.displayName} with $totalAmmo ammo.")
                }
            }
        }

        // Clear the enemy's inventory so it doesn't get processed again
        enemy.inventory.clear()
        enemy.weapons.clear()
    }

    fun handleCarExit(enemy: GameEnemy, sceneManager: SceneManager) {
        val car = enemy.drivingCar ?: return

        // Calculate a safe exit position next to the car's CURRENT location
        val exitOffset = Vector3(-5f, 0f, 0f).rotate(Vector3.Y, car.visualRotationY)
        val potentialExitPos = car.position.cpy().add(exitOffset)

        // Find the ground level at this potential exit spot
        val groundY = sceneManager.findHighestSupportY(potentialExitPos.x, potentialExitPos.z, car.position.y, 0.1f, sceneManager.game.blockSize)

        // Set the character's new position
        enemy.position.set(potentialExitPos.x, groundY + (enemy.enemyType.height / 2f), potentialExitPos.z)
        // Also sync the physics component immediately
        enemy.physics.position.set(enemy.position)
        enemy.physics.updateBounds()

        // Now, call the simplified exitCar method on the enemy object itself
        enemy.exitCar()
        println("${enemy.enemyType.displayName} exited a car at ${enemy.position}")
    }

    private fun updateAI(enemy: GameEnemy, playerSystem: PlayerSystem, deltaTime: Float, sceneManager: SceneManager) {
        if (enemy.currentState == AIState.DYING) {
            return
        }

        if (enemy.isInCar) {
            val car = enemy.drivingCar!!
            val playerPos = playerSystem.getPosition()

            if (enemy.currentState == AIState.PATROLLING_IN_CAR) {
                // 1. Self-preservation: Exit if car is about to explode
                if (car.health < 40f) {
                    println("${enemy.enemyType.displayName} is bailing out of the damaged car!")
                    handleCarExit(enemy, sceneManager)
                    enemy.currentState = AIState.IDLE
                    return
                }

                // 2. Path Following Logic
                var desiredMovement = Vector3.Zero
                var targetNode = enemy.currentTargetPathNodeId?.let { sceneManager.game.carPathSystem.nodes[it] }

                // If we don't have a target, find the closest one
                if (targetNode == null) {
                    targetNode = sceneManager.game.carPathSystem.findNearestNode(car.position)
                    enemy.currentTargetPathNodeId = targetNode?.id
                }

                if (targetNode != null) {
                    // Check if we've arrived at the target node
                    if (car.position.dst2(targetNode.position) < 25f) { // 5 unit radius (squared)
                        // Arrived! Get the next node in the path.
                        val nextNode = targetNode.nextNodeId?.let { sceneManager.game.carPathSystem.nodes[it] }
                        enemy.currentTargetPathNodeId = nextNode?.id
                        if (nextNode == null) {
                            // End of the path, stop the car.
                            desiredMovement.set(Vector3.Zero)
                        } else {
                            // Move towards the new next node
                            desiredMovement = nextNode.position.cpy().sub(car.position).nor()
                        }
                    } else {
                        // Still driving towards the current target node
                        desiredMovement = targetNode.position.cpy().sub(car.position).nor()
                    }
                }

                car.updateAIControlled(deltaTime, desiredMovement, sceneManager, sceneManager.activeCars)
                enemy.position.set(car.position)
                return
            }

            val distanceToPlayer = car.position.dst(playerPos)

            // Exit condition: if player is far away
            if (distanceToPlayer > FLEE_EXIT_DISTANCE) {
                println("${enemy.enemyType.displayName} feels safe and is exiting the stolen car.")
                handleCarExit(enemy, sceneManager)
                enemy.currentState = AIState.IDLE // Go back to normal
            } else {
                // Fleeing behavior: drive away from the player
                val awayDirection = car.position.cpy().sub(playerPos).nor()
                car.updateAIControlled(deltaTime, awayDirection, sceneManager, sceneManager.activeCars)
                enemy.position.set(car.position) // Keep enemy's logical position synced with the car
            }
            return // Skip all on-foot AI while in the car
        }

        checkForItemPickups(enemy, sceneManager)

        // High priority override: Reloading
        if (enemy.currentState == AIState.RELOADING) {
            enemy.stateTimer -= deltaTime
            if (enemy.stateTimer <= 0f) {
                val ammoInReserve = enemy.weapons.getOrDefault(enemy.equippedWeapon, 0)
                if (ammoInReserve <= 0) {
                    // No more ammo for this gun! Switch weapons.
                    handleWeaponSwitch(enemy)
                } else {
                    // Finish reloading
                    val ammoNeeded = enemy.equippedWeapon.magazineSize - enemy.currentMagazineCount
                    val ammoToMove = minOf(ammoNeeded, ammoInReserve)
                    enemy.currentMagazineCount += ammoToMove
                    enemy.weapons[enemy.equippedWeapon] = ammoInReserve - ammoToMove
                }
                enemy.currentState = AIState.IDLE // Go back to idle to re-evaluate
            }
            characterPhysicsSystem.update(enemy.physics, Vector3.Zero, deltaTime)
            return
        }

        // Dispatch to the correct AI routine based on the enemy's CURRENT behavior
        when (enemy.currentBehavior) {
            EnemyBehavior.STATIONARY_SHOOTER -> updateShooterAI(enemy, playerSystem, deltaTime, sceneManager)
            EnemyBehavior.AGGRESSIVE_RUSHER -> updateRusherAI(enemy, playerSystem, deltaTime, sceneManager)
            EnemyBehavior.SKIRMISHER -> updateSkirmisherAI(enemy, playerSystem, deltaTime, sceneManager)
            EnemyBehavior.NEUTRAL -> updateNeutralAI(enemy, deltaTime)
            else -> {
                characterPhysicsSystem.update(enemy.physics, Vector3.Zero, deltaTime)
            }
        }
    }

    private fun updateShooterAI(enemy: GameEnemy, playerSystem: PlayerSystem, deltaTime: Float, sceneManager: SceneManager) {
        val playerPos = playerSystem.getPosition()
        var desiredMovement = Vector3.Zero

        // --- AMMO CHECK & BEHAVIOR SWITCH ---
        if (enemy.currentMagazineCount <= 0 && enemy.ammo <= 0) {
            // 50% chance to become a Rusher, 50% to become a Skirmisher
            if (Random.nextFloat() < 0.5f) {
                println("${enemy.enemyType.displayName} is out of ammo! Switching to AGGRESSIVE RUSH.")
                enemy.currentBehavior = EnemyBehavior.AGGRESSIVE_RUSHER
            } else {
                println("${enemy.enemyType.displayName} is out of ammo! Switching to SKIRMISH tactics.")
                enemy.currentBehavior = EnemyBehavior.SKIRMISHER
            }
            enemy.equippedWeapon = WeaponType.UNARMED // Switch to fists for melee
            return // End this update cycle, the next one will use the new AI behavior
        }

        // --- SHOOTING LOGIC ---
        val isYAligned = kotlin.math.abs(playerPos.y - enemy.position.y) < enemy.enemyType.height
        val isZAligned = kotlin.math.abs(playerPos.z - enemy.position.z) < COMBAT_DEPTH_TOLERANCE
        val ray = Ray(enemy.position, playerPos.cpy().sub(enemy.position).nor())
        val collision = sceneManager.checkCollisionForRay(ray, enemy.position.dst(playerPos))
        val hasLineOfSight = collision == null || collision.type == HitObjectType.PLAYER || collision.type == HitObjectType.ENEMY

        if (hasLineOfSight && isYAligned && isZAligned && enemy.attackTimer <= 0f) {
            if (enemy.currentMagazineCount > 0) {
                spawnEnemyBullet(enemy, playerPos, sceneManager)
                enemy.attackTimer = enemy.equippedWeapon.fireCooldown
                enemy.currentMagazineCount--
            } else {
                // No ammo left for this weapon at all.
                enemy.currentState = AIState.RELOADING
                enemy.stateTimer = enemy.equippedWeapon.reloadTime
            }
        }

        // --- POSITIONING LOGIC ---
        enemy.pathRequestTimer -= deltaTime
        val distanceToPlayer = enemy.position.dst(playerPos)

        if (enemy.pathRequestTimer <= 0f) {
            var targetPosition: Vector3? = null
            if (enemy.isOnFire) {
                // Flee from fire
                val closestFire = sceneManager.game.fireSystem.activeFires.minByOrNull { it.gameObject.position.dst2(enemy.position) }
                if (closestFire != null) {
                    val awayDir = enemy.position.cpy().sub(closestFire.gameObject.position).nor()
                    targetPosition = enemy.position.cpy().add(awayDir.scl(FIRE_FLEE_DISTANCE))
                }
            } else if (distanceToPlayer < SHOOTER_MIN_DISTANCE) {
                // Too close, back away
                val awayDir = enemy.position.cpy().sub(playerPos).nor()
                targetPosition = enemy.position.cpy().add(awayDir.scl(SHOOTER_IDEAL_DISTANCE))
            } else if (!hasLineOfSight) {
                // Can't see player, move to a position where we might
                targetPosition = playerPos
            }

            if (targetPosition != null) {
                pathfindingSystem.findPath(enemy.position, targetPosition)?.let {
                    enemy.path = it
                    enemy.waypoint = enemy.path.poll()
                }
                enemy.pathRequestTimer = PATH_RECALCULATION_INTERVAL
            } else {
                enemy.path.clear() // No reason to move, clear path
                enemy.waypoint = null
            }
        }

        // --- MOVEMENT EXECUTION ---
        enemy.waypoint?.let { targetWaypoint ->
            if (enemy.position.dst(targetWaypoint) < WAYPOINT_TOLERANCE) {
                enemy.waypoint = enemy.path.poll()
            } else {
                desiredMovement = targetWaypoint.cpy().sub(enemy.physics.position).nor()
            }
        }

        if (!desiredMovement.isZero) {
            enemy.physics.facingRotationY = if (desiredMovement.x > 0) 0f else 180f
        } else {
            enemy.physics.facingRotationY = if (playerPos.x > enemy.physics.position.x) 0f else 180f
        }

        characterPhysicsSystem.update(enemy.physics, desiredMovement, deltaTime)
    }

    private fun updateRusherAI(enemy: GameEnemy, playerSystem: PlayerSystem, deltaTime: Float, sceneManager: SceneManager) {
        val playerPos = playerSystem.getPosition()
        var desiredMovement = Vector3.Zero
        val distanceToPlayer = enemy.position.dst(playerPos)
        val meleeRange = enemy.equippedWeapon.meleeRange

        // MELEE ATTACK LOGIC
        if (distanceToPlayer < meleeRange && enemy.attackTimer <= 0f) {
            performEnemyMeleeAttack(enemy, playerSystem)
            enemy.attackTimer = enemy.equippedWeapon.fireCooldown
            characterPhysicsSystem.update(enemy.physics, Vector3.Zero, deltaTime) // Stop moving to attack
            return // End AI update for this frame
        }

        // --- PATHFINDING & MOVEMENT LOGIC ---
        enemy.pathRequestTimer -= deltaTime
        if (enemy.pathRequestTimer <= 0f) {
            var targetPosition: Vector3? = null
            if (enemy.isOnFire) {
                // Flee from fire if burning
                val closestFire = sceneManager.game.fireSystem.activeFires.minByOrNull { it.gameObject.position.dst2(enemy.position) }
                if (closestFire != null) {
                    val awayDir = enemy.position.cpy().sub(closestFire.gameObject.position).nor()
                    targetPosition = enemy.position.cpy().add(awayDir.scl(FIRE_FLEE_DISTANCE))
                }
            } else {
                // The target is always the player
                targetPosition = playerPos
            }

            if (targetPosition != null) {
                // Try to find a path
                val foundPath = pathfindingSystem.findPath(enemy.position, targetPosition)
                if (!foundPath.isNullOrEmpty()) {
                    enemy.path = foundPath
                    enemy.waypoint = enemy.path.poll()
                } else {
                    // Pathfinding failed or returned an empty path
                    enemy.path.clear()
                    enemy.waypoint = targetPosition // The waypoint is now the final destination
                    println("${enemy.enemyType.displayName} couldn't find a path, moving directly towards target.")
                }
                enemy.pathRequestTimer = PATH_RECALCULATION_INTERVAL
            } else {
                // If there's no reason to move (e.g., already in melee range), clear the path.
                enemy.path.clear()
                enemy.waypoint = null
            }
        }

        // MOVEMENT EXECUTION
        enemy.waypoint?.let { targetWaypoint ->
            // Check if we are close enough to the current waypoint
            if (enemy.position.dst(targetWaypoint) < WAYPOINT_TOLERANCE) {
                // If there are more waypoints in the path, get the next one. Otherwise, we've arrived.
                enemy.waypoint = enemy.path.poll()
            } else {
                // We still have a waypoint to move to
                desiredMovement = targetWaypoint.cpy().sub(enemy.physics.position).nor()
            }
        }

        // VISUALS & PHYSICS
        // Make the enemy face the direction of movement, or the player if standing still
        if (!desiredMovement.isZero) {
            enemy.physics.facingRotationY = if (desiredMovement.x > 0) 0f else 180f
        } else if (distanceToPlayer > 0.1f) {
            enemy.physics.facingRotationY = if (playerPos.x > enemy.physics.position.x) 0f else 180f
        }

        characterPhysicsSystem.update(enemy.physics, desiredMovement, deltaTime)
    }

    private fun updateNeutralAI(enemy: GameEnemy, deltaTime: Float) {
        // Neutral enemies just stand still until provoked
        if (enemy.provocationLevel > 0) {
            enemy.provocationLevel -= 5f * deltaTime // Decay 5 points per second
        }
        characterPhysicsSystem.update(enemy.physics, Vector3.Zero, deltaTime)
    }

    private fun updateSkirmisherAI(enemy: GameEnemy, playerSystem: PlayerSystem, deltaTime: Float, sceneManager: SceneManager) {
        val playerPos = playerSystem.getPosition()
        var desiredMovement = Vector3.Zero
        val distanceToPlayer = enemy.position.dst(playerPos)

        // State 1: Fleeing after a successful hit
        if (enemy.currentState == AIState.FLEEING_AFTER_ATTACK) {
            enemy.stateTimer -= deltaTime
            if (enemy.stateTimer > 0) {
                // Flee away from the player
                val awayDir = enemy.position.cpy().sub(playerPos).nor()
                desiredMovement = awayDir
            } else {
                // Flee time is over, go back to idle to plan the next attack
                enemy.currentState = AIState.IDLE
            }
        } else { // State 2: Not fleeing, so we are either idle or chasing to attack
            // Pathfinding logic
            enemy.pathRequestTimer -= deltaTime
            if (enemy.pathRequestTimer <= 0f) {
                pathfindingSystem.findPath(enemy.position, playerPos)?.let {
                    enemy.path = it
                    enemy.waypoint = enemy.path.poll()
                }
                enemy.pathRequestTimer = PATH_RECALCULATION_INTERVAL
            }

            // Movement execution
            enemy.waypoint?.let { target ->
                if (enemy.position.dst(target) < WAYPOINT_TOLERANCE) enemy.waypoint = enemy.path.poll()
                else desiredMovement = target.cpy().sub(enemy.physics.position).nor()
            }

            // Attack logic
            if (distanceToPlayer < enemy.equippedWeapon.meleeRange && enemy.attackTimer <= 0f) {
                performEnemyMeleeAttack(enemy, playerSystem)
                enemy.attackTimer = enemy.equippedWeapon.fireCooldown * 1.5f // Longer cooldown after skirmish hit

                // CRITICAL: After attacking, enter the fleeing state
                enemy.currentState = AIState.FLEEING_AFTER_ATTACK
                enemy.stateTimer = 2.0f // Flee for 2 seconds
                enemy.path.clear() // Clear the old path, a new one will be calculated for fleeing
                enemy.waypoint = null
            }
        }

        // Update visuals and physics
        if (!desiredMovement.isZero) {
            enemy.physics.facingRotationY = if (desiredMovement.x > 0) 0f else 180f
        } else {
            enemy.physics.facingRotationY = if (playerPos.x > enemy.physics.position.x) 0f else 180f
        }

        characterPhysicsSystem.update(enemy.physics, desiredMovement, deltaTime)
    }

    private fun performEnemyMeleeAttack(enemy: GameEnemy, playerSystem: PlayerSystem) {
        val attackRange = enemy.equippedWeapon.meleeRange
        val attackWidth = 3f
        val directionX = if (enemy.physics.facingRotationY == 0f) 1f else -1f

        val hitBoxCenter = enemy.position.cpy().add(directionX * (attackRange / 2f), 0f, 0f)
        val hitBox = BoundingBox(
            Vector3(hitBoxCenter.x - (attackRange / 2f), hitBoxCenter.y - (enemy.enemyType.height / 2f), hitBoxCenter.z - (attackWidth / 2f)),
            Vector3(hitBoxCenter.x + (attackRange / 2f), hitBoxCenter.y + (enemy.enemyType.height / 2f), hitBoxCenter.z + (attackWidth / 2f))
        )

        if (hitBox.intersects(playerSystem.getPlayerBounds())) {
            println("${enemy.enemyType.displayName} hit player with melee attack!")
            playerSystem.takeDamage(enemy.equippedWeapon.damage)
        }
    }

    private fun spawnEnemyBullet(enemy: GameEnemy, playerPos: Vector3, sceneManager: SceneManager) {
        val bulletModel = sceneManager.game.playerSystem.bulletModels[enemy.equippedWeapon.bulletTexturePath] ?: return

        // 1. Determine direction ONLY on the X-axis (left or right)
        val directionX = if (enemy.physics.facingRotationY == 0f) 1f else -1f
        val direction = Vector3(directionX, 0f, 0f) // Force a purely horizontal direction

        // 2. Calculate velocity based on this new horizontal direction
        val velocity = direction.cpy().scl(enemy.equippedWeapon.bulletSpeed)

        // 3. Calculate the spawn position based on the horizontal direction
        val verticalOffset = -0.3f
        val horizontalOffset = directionX * (enemy.enemyType.width / 2f)
        val spawnPos = Vector3(
            enemy.position.x + horizontalOffset,
            enemy.position.y + verticalOffset,
            enemy.position.z
        )
        spawnPos.mulAdd(direction, 1.0f) // Push it slightly forward from the enemy

        val bullet = Bullet(
            position = spawnPos,
            velocity = velocity,
            modelInstance = ModelInstance(bulletModel),
            lifetime = enemy.equippedWeapon.bulletLifetime,
            rotationY = if (direction.x < 0) 180f else 0f,
            owner = enemy,
            damage = enemy.equippedWeapon.damage
        )

        sceneManager.activeBullets.add(bullet)
        println("${enemy.enemyType.displayName} shoots at player!")
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

        enemyWeaponTextures.values.forEach { map ->
            map.values.forEach { it.dispose() }
        }

        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()
    }
}
