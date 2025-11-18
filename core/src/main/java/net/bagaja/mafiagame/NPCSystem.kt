package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
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
import java.util.*
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class DialogOutcomeType(val displayName: String) {
    NONE("None (Conversation Only)"),
    GIVE_ITEM("Give Item to Player"),
    TRADE_ITEM("Trade (Player gives X, gets Y)"),
    SELL_ITEM_TO_PLAYER("Sell Item to Player"),
    BUY_ITEM_FROM_PLAYER("Buy Item from Player")
}

data class DialogOutcome(
    val type: DialogOutcomeType = DialogOutcomeType.NONE,
    // For GIVE, SELL, and TRADE (reward part)
    val itemToGive: ItemType? = null,
    val ammoToGive: Int? = null,
    // For BUY and SELL
    val price: Int? = null,
    // For TRADE and BUY
    val requiredItem: ItemType? = null
)

enum class DamageReaction {
    FLEE,       // Runs away when damaged
    FIGHT_BACK  // Becomes hostile when damaged
}

enum class PathFollowingStyle(val displayName: String) {
    CONTINUOUS("Continuous"),
    STOP_AND_GO("Stop and Go")
}

enum class PathfindingSubState {
    MOVING,
    PAUSING
}

// --- ENUMERATIONS FOR NPC PROPERTIES ---

enum class NPCType(
    val displayName: String,
    val texturePath: String,
    val width: Float,
    val height: Float,
    val baseHealth: Float,
    val speed: Float
) {
    GEORGE_MELES("George Meles", "textures/characters/george_meles.png", 3.4f, 4.5f, 120f, 5.5f),
    LADY_FOX("Lady Fox", "textures/characters/lady_fox.png", 5f, 6.5f, 80f, 8f),
    LADY_FOX_MICROPHONE("Lady Fox with Microphone", "textures/characters/lady_fox_with_microphone.png", 5f, 6.5f, 80f, 8f),
    FRED_THE_HERMIT("Fred the Hermit", "textures/characters/fred_hermit.png", 4f, 5f, 100f, 4.5f),
    MR_QUESTMARK("Mr. Questmark", "textures/characters/Mr_Questmark.png", 4.0f, 5f, 100f, 5.0f),
    NUN("Nun", "textures/characters/nun.png", 4f, 5f, 70f, 5.5f),
    SINGER("Singer", "textures/characters/singer.png", 5f, 7f, 80f, 6.0f),
    ARMADILLO("Armadillo", "textures/characters/armadillo.png", 4f, 5f, 120f, 5.5f),
    BEAR("Bear", "textures/characters/bear.png", 4.5f, 6f, 120f, 5.5f),
    DODO("Dodo", "textures/characters/dodo.png", 3.4f, 4.5f, 120f, 5.5f),
    RACCOON("Raccoon", "textures/characters/raccoon.png", 4f, 5f, 120f, 5.5f),
    MS_EINIGSTEIN("Ms Einigstein", "textures/characters/ms_einigstein.png", 3.4f, 4.5f, 120f, 5.5f),
    FROG("Frog", "textures/characters/frog.png", 3.4f, 4.5f, 120f, 5.5f),
    GOOSE("Goose", "textures/characters/goose.png", 4.5f, 6f, 120f, 5.5f),
    LIZARD("Lizard", "textures/characters/lizard.png", 3.4f, 4.5f, 120f, 5.5f),
    MOTH("Moth", "textures/characters/moth.png", 3.4f, 4.5f, 120f, 5.5f),
    RED_PANDA("Red Panda", "textures/characters/red_panda.png", 4f, 5f, 120f, 5.5f),
    SNAKE("Snake", "textures/characters/snake.png", 4f, 5f, 120f, 5.5f),
    OCTOPUS("Octopus", "textures/characters/octopus.png", 4f, 5f, 120f, 5.5f),
    PRESIDENT("President", "textures/characters/rooster.png", 5f, 6.5f, 80f, 8f),
    PRESIDENT_TOMMY_GUN("President with Tommy Gun", "textures/characters/rooster_president.png", 5f, 6.5f, 80f, 8f),
}

enum class NPCBehavior(val displayName: String) {
    STATIONARY("Stationary"),   // Stands still
    WATCHER("Watcher"),          // Stands still, looks at player
    WANDER("Wanderer"),         // Wanders in a radius
    FOLLOW("Follower"),           // Follows the player
    GUARD("Guard"),              // Hostile on sight (for story villains)
    PATH_FOLLOWER("Path Follower")
}

enum class NPCPersonality {
    COWARDLY, // Will always flee from crime
    CIVILIAN, // Might report crime, might flee
    BRAVE     // High chance of reporting crime
}

// AI State for the NPC's state machine
enum class NPCState {
    IDLE,
    WANDERING,
    FOLLOWING,
    PROVOKED,
    COOLDOWN,
    FLEEING,
    DYING,
    PATROLLING_IN_CAR,
    CHASING_STOLEN_CAR,
    REPORTING_CRIME_SEARCHING, // NPC is looking for a cop
    REPORTING_CRIME_RUNNING,   // NPC is running to a cop
    REPORTING_CRIME_DELIVERING // NPC has reached the cop and is "talking"
}

// --- MAIN NPC DATA CLASS ---
data class GameNPC(
    val id: String = UUID.randomUUID().toString(),
    val modelInstance: ModelInstance,
    val npcType: NPCType,
    var behaviorType: NPCBehavior,
    var position: Vector3,
    var health: Float = npcType.baseHealth,
    val isHonest: Boolean = true,
    val canCollectItems: Boolean = true,
    val inventory: MutableList<GameItem> = mutableListOf(),
    var equippedMeleeWeapon: WeaponType = WeaponType.UNARMED,
    var assignedPathId: String? = null,
    var pathFollowingStyle: PathFollowingStyle = PathFollowingStyle.CONTINUOUS,
    @Transient var pathfindingState: PathfindingSubState = PathfindingSubState.MOVING,
    @Transient var subStateTimer: Float = 0f,
    var missionId: String? = null,
    var standaloneDialog: StandaloneDialog? = null,
    var standaloneDialogCompleted: Boolean = false,
    var scheduledForDespawn: Boolean = false,
    var canBePulledFromCar: Boolean = true,
    @Transient var baseTexture: Texture? = null,
    @Transient var carProvocation: Float = 0f,
    val personality: NPCPersonality = NPCPersonality.CIVILIAN
) {
    @Transient var carToChaseId: String? = null
    @Transient var chaseTimer: Float = 0f
    var isOnFire: Boolean = false
    var onFireTimer: Float = 0f
    var initialOnFireDuration: Float = 0f
    var onFireDamagePerSecond: Float = 0f
    @Transient lateinit var physics: PhysicsComponent
    @Transient var currentTargetPathNodeId: String? = null
    @Transient var currentPathNode: CharacterPathNode? = null
    var bleedTimer: Float = 0f
    var bloodDripSpawnTimer: Float = 0f

    // AI state properties
    var currentState: NPCState = NPCState.IDLE
    var stateTimer: Float = 0f
    var targetPosition: Vector3? = null
    var facingRotationY: Float = 0f
    var homePosition: Vector3 = position.cpy() // For wandering radius
    var reactionToDamage: DamageReaction = DamageReaction.FLEE
    var lastDamageType: DamageType = DamageType.GENERIC
    var fadeOutTimer: Float = 0f
    @Transient val blendingAttribute: BlendingAttribute = modelInstance.materials.first().get(BlendingAttribute.Type) as BlendingAttribute
    @Transient var ashSpawned: Boolean = false

    // --- Provocation properties ---
    var provocationLevel: Float = 0f
    private val provocationDecayRate: Float = 5.0f  // Points per second
    val provocationThreshold: Float = 30f         // After this, NPC becomes hostile
    private val provocationPerHit: Float = 12f          // One hit isn't enough, but 3 quick hits are.

    var isMoving: Boolean = false
    var walkAnimationTimer: Float = 0f
    var wobbleAngle: Float = 0f

    // Collision properties
    private val boundsSize = Vector3(npcType.width, npcType.height, npcType.width)
    private val boundingBox = BoundingBox()

    companion object {
        const val BLEED_DURATION = 1.0f
        const val BLOOD_DRIP_INTERVAL = 0.7f
    }

    @Transient var isInCar: Boolean = false
    @Transient var drivingCar: GameCar? = null
    @Transient var currentSeat: CarSeat? = null

    fun enterCar(car: GameCar) {
        val seat = car.addOccupant(this)
        if (seat != null) {
            val soundManager = car.sceneManager.game.soundManager
            val soundIdToPlay = car.assignedOpenSoundId ?: "CAR_DOOR_OPEN_V1" // Fallback sound
            soundManager.playSound(id = soundIdToPlay, position = car.position)

            isInCar = true
            drivingCar = car
            currentSeat = seat
            drivingCar?.modelInstance?.userData = "car"
            println("${this.npcType.displayName} entered a car.")
        }
    }

    fun exitCar() {
        val car = drivingCar ?: return

        val soundManager = car.sceneManager.game.soundManager
        val closeSoundId = car.assignedCloseSoundId ?: "CAR_DOOR_CLOSE_V1"
        soundManager.playSound(id = closeSoundId, position = car.position)

        // If the car is locked, play a "lock" sound after a short delay
        if (car.isLocked) {
            com.badlogic.gdx.utils.Timer.schedule(object : com.badlogic.gdx.utils.Timer.Task() {
                override fun run() {
                    val lockedSoundId = car.assignedLockedSoundId ?: "CAR_LOCKED_V1"
                    soundManager.playSound(id = lockedSoundId, position = car.position)
                }
            }, 0.3f) // 0.3-second delay
        }

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
        modelInstance.transform.rotate(Vector3.Z, wobbleAngle) // Rotate on Z-axis for the wobble
    }

    /** Called when the player damages this NPC. This is the core of the "Iron Golem" logic. */
    fun onPlayerHit(damage: Float) {
        // If NPC is already a Guard or Provoked, they just take damage and stay mad.
        if (behaviorType == NPCBehavior.GUARD || currentState == NPCState.PROVOKED) {
            health -= damage
            println("Hostile NPC ${npcType.displayName} took $damage damage. HP: $health")
            return
        }

        health -= damage
        provocationLevel += provocationPerHit
        println("NPC ${npcType.displayName} was hit! Provocation: $provocationLevel / $provocationThreshold. HP: $health")

        // If threshold is reached, become hostile
        if (provocationLevel >= provocationThreshold) {
            println("Enough is enough! NPC ${npcType.displayName} is now hostile!")
            currentState = NPCState.PROVOKED
            stateTimer = 0f // Reset timer for the new state
        }
    }

    fun takeDamage(damage: Float, type: DamageType, sceneManager: SceneManager): Boolean {
        if (isInCar) return false

        if (health <= 0) return true

        sceneManager.npcSystem.destroyLinkedObjects(this.id)

        // Apply damage first, regardless of reaction
        health -= damage

        // If not dead and was shot or hit with melee, start bleeding
        if (health > 0 && (type == DamageType.GENERIC || type == DamageType.MELEE)) {
            val violenceLevel = sceneManager.game.uiManager.getViolenceLevel()
            this.bleedTimer = if (violenceLevel == ViolenceLevel.ULTRA_VIOLENCE) {
                BLEED_DURATION * 2.5f // Bleed for 2.5 seconds in ultra mode
            } else {
                BLEED_DURATION // Normal 1 second bleed
            }
        }

        // The type of the killing blow is what matters.
        if (health <= 0) {
            lastDamageType = type
        }
        println("NPC ${npcType.displayName} took $damage $type damage. HP: ${health.coerceAtLeast(0f)}")

        if (health <= 0) return true // Died, no need for reaction logic

        // If the NPC is already a guard or provoked, they just stay mad
        if (behaviorType == NPCBehavior.GUARD || currentState == NPCState.PROVOKED) {
            return false
        }

        // Check if the NPC should try to flee in a car
        if (!isInCar && reactionToDamage == DamageReaction.FLEE && Random.nextFloat() < sceneManager.npcSystem.CAR_FLEE_CHANCE) {
            val closestCar = sceneManager.activeCars
                .filter { !it.isLocked && it.seats.first().occupant == null && it.state == CarState.DRIVABLE }
                .minByOrNull { it.position.dst2(this.position) }

            if (closestCar != null && this.position.dst(closestCar.position) < sceneManager.npcSystem.CAR_SEARCH_RADIUS) {
                println("${this.npcType.displayName} is stealing a car to escape!")
                this.enterCar(closestCar)
                this.currentState = NPCState.FLEEING
                return false // Don't trigger on-foot fleeing
            }
        }

        when (reactionToDamage) {
            DamageReaction.FIGHT_BACK -> {
                // old "Iron Golem" logic
                provocationLevel += provocationPerHit
                println("Provocation: $provocationLevel / $provocationThreshold.")
                if (provocationLevel >= provocationThreshold) {
                    println("NPC is now hostile!")
                    currentState = NPCState.PROVOKED
                    stateTimer = 0f // Reset timer for the new state
                }
            }
            DamageReaction.FLEE -> {
                currentState = NPCState.FLEEING
                stateTimer = 0f // Reset timer for flee duration
            }
        }
        return false // Not dead yet
    }

    /** Decays the provocation level over time so the NPC can "forgive" accidental hits. */
    fun decayProvocation(deltaTime: Float) {
        if (provocationLevel > 0 && currentState != NPCState.PROVOKED) {
            provocationLevel = max(0f, provocationLevel - provocationDecayRate * deltaTime)
        }
    }
}

// --- NPC MANAGEMENT SYSTEM ---

class NPCSystem : IFinePositionable {
    private val tempVec2_1 = com.badlogic.gdx.math.Vector2()
    private val tempVec2_2 = com.badlogic.gdx.math.Vector2()
    private val tempVec2_3 = com.badlogic.gdx.math.Vector2()
    private val tempVec2_Result = com.badlogic.gdx.math.Vector2()
    private lateinit var characterPhysicsSystem: CharacterPhysicsSystem
    private val npcModels = mutableMapOf<NPCType, Model>()
    private val npcTextures = mutableMapOf<NPCType, Texture>()
    private lateinit var billboardModelBatch: ModelBatch
    private lateinit var billboardShaderProvider: BillboardShaderProvider
    private val renderableInstances = Array<ModelInstance>()
    private val fleeDuration = 5.0f
    private val BLEED_DAMAGE_PER_SECOND = 5f
    private val CRIME_DETECTION_RADIUS = 50f
    private val THREATENING_WEAPON_RADIUS = 25f
    private val POLICE_SEARCH_RADIUS = 100f
    private val REPORTING_DISTANCE = 4f // How close NPC needs to be to a cop to report
    private val REPORTING_DURATION = 2.5f
    private val POLICE_HEARING_RADIUS = 70f

    var currentSelectedNPCType = NPCType.FRED_THE_HERMIT
        private set
    var currentSelectedBehavior = NPCBehavior.STATIONARY
        private set
    var currentRotation: Float = 0f
        private set
    var currentNPCTypeIndex = 0
    var currentBehaviorIndex = 0

    private val playerFollowDistance = 8f
    private val stopFollowingDistance = 3f
    private val wanderRadius = 15f
    private val wanderPauseTime = 4f
    private val provokedChaseDistance = 30f
    private val stopProvokedChaseDistance = 1.5f
    private val hostileCooldownTime = 10f
    private val activationRange = 150f

    override var finePosMode: Boolean = false
    override val fineStep: Float = 0.25f
    private val FADE_OUT_DURATION = 1.5f
    private val ASH_SPAWN_START_TIME = 1.5f

    lateinit var sceneManager: SceneManager
    lateinit var raycastSystem: RaycastSystem
    private val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)
    private val tempVec3 = Vector3()
    private var blockSize: Float = 4f

    internal val CAR_FLEE_CHANCE = 0.20f
    internal val CAR_SEARCH_RADIUS = 5f
    private val FLEE_EXIT_DISTANCE = 60f
    private val MAX_CHASE_DISTANCE = 60f

    fun initialize(blockSize: Float, characterPhysicsSystem: CharacterPhysicsSystem) {
        this.blockSize = blockSize
        this.raycastSystem = RaycastSystem(blockSize)
        this.characterPhysicsSystem = characterPhysicsSystem

        billboardShaderProvider = BillboardShaderProvider()
        billboardModelBatch = ModelBatch(billboardShaderProvider)
        billboardShaderProvider.setBillboardLightingStrength(0.9f)
        billboardShaderProvider.setMinLightLevel(0.3f)

        val modelBuilder = ModelBuilder()
        for (type in NPCType.entries) {
            try {
                val texture = Texture(Gdx.files.internal(type.texturePath))
                npcTextures[type] = texture
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
                npcModels[type] = model
            } catch (e: Exception) {
                println("ERROR: Could not load NPC texture or model for ${type.displayName}: ${e.message}")
            }
        }
    }

    fun handlePlaceAction(ray: Ray) {
        // Check the current editor mode
        if (sceneManager.game.uiManager.currentEditorMode == EditorMode.MISSION) {
            handleMissionPlacement(ray)
            return
        }

        // World Editing logic
        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            var surfaceY = findHighestSurfaceYAt(tempVec3.x, tempVec3.z)
            if (surfaceY < -500f) {
                surfaceY = 0f
            }

            // Get config from UI to know the height for placement
            val tempConfig = sceneManager.game.uiManager.npcSelectionUI.getSpawnConfig(Vector3.Zero)
            val npcPosition = Vector3(tempVec3.x, surfaceY + tempConfig.npcType.height / 2f, tempVec3.z)

            // Get the final, complete config with the correct position
            val finalConfig = sceneManager.game.uiManager.npcSelectionUI.getSpawnConfig(npcPosition)

            val newNPC = createNPC(finalConfig, currentRotation)

            if (newNPC != null) {
                sceneManager.activeNPCs.add(newNPC)
                sceneManager.game.lastPlacedInstance = newNPC
                println("Placed ${newNPC.npcType.displayName} with ${newNPC.behaviorType.displayName} behavior at $npcPosition")
            }
        }
    }

    private fun handleMissionPlacement(ray: Ray) {
        val mission = sceneManager.game.uiManager.selectedMissionForEditing ?: return

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            val config = sceneManager.game.uiManager.npcSelectionUI.getSpawnConfig(Vector3.Zero)

            var surfaceY = findHighestSurfaceYAt(tempVec3.x, tempVec3.z)
            if (surfaceY < -500f) {
                surfaceY = 0f
            }

            val npcPosition = Vector3(tempVec3.x, surfaceY + config.npcType.height / 2f, tempVec3.z)

            val currentSceneId = sceneManager.getCurrentSceneId()

            // 1. Create the GameEvent for the mission file
            val event = GameEvent(
                type = GameEventType.SPAWN_NPC,
                spawnPosition = npcPosition,
                sceneId = currentSceneId,
                targetId = "npc_${UUID.randomUUID()}",
                npcType = config.npcType,
                npcBehavior = config.behavior,
                npcRotation = currentRotation,
                pathFollowingStyle = config.pathFollowingStyle,
                assignedPathId = config.assignedPathId
            )

            // 2. Add the event and save the mission
            mission.eventsOnStart.add(event)
            sceneManager.game.missionSystem.saveMission(mission)

            // 3. Create a temporary "preview" NPC to see in the world
            val previewConfig = config.copy(position = npcPosition, id = event.targetId)
            val previewNpc = createNPC(previewConfig, currentRotation)
            if (previewNpc != null) {
                previewNpc.modelInstance.transform.setToTranslation(previewNpc.position)
                previewNpc.modelInstance.transform.rotate(Vector3.Y, currentRotation)

                previewNpc.missionId = mission.id
                sceneManager.activeMissionPreviewNPCs.add(previewNpc)
                sceneManager.game.lastPlacedInstance = previewNpc
                sceneManager.game.uiManager.updatePlacementInfo("Added SPAWN_NPC to '${mission.title}'")
                sceneManager.game.uiManager.missionEditorUI.refreshEventWidgets()
            }
        }
    }

    fun handleRemoveAction(ray: Ray): Boolean {
        val npcToRemove = raycastSystem.getNPCAtRay(ray, sceneManager.activeNPCs)
        if (npcToRemove != null) {
            removeNPC(npcToRemove)
            return true
        }
        return false
    }

    private fun removeNPC(npcToRemove: GameNPC) {
        sceneManager.activeNPCs.removeValue(npcToRemove, true)
        println("Removed ${npcToRemove.npcType.displayName} at: ${npcToRemove.position}")
    }

    // --- Helper Function Copied from MafiaGame.kt ---
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

    fun createNPC(config: NPCSpawnConfig, initialRotation: Float = 0f): GameNPC? {
        val model = npcModels[config.npcType] ?: return null
        val instance = ModelInstance(model)
        instance.userData = "character"

        val newNpc = GameNPC(
            id = config.id ?: UUID.randomUUID().toString(),
            modelInstance = instance,
            npcType = config.npcType,
            behaviorType = config.behavior,
            position = config.position.cpy(),
            canCollectItems = config.canCollectItems,
            isHonest = config.isHonest,
            pathFollowingStyle = config.pathFollowingStyle,
            assignedPathId = config.assignedPathId,
            standaloneDialog = config.standaloneDialog
        )

        newNpc.baseTexture = npcTextures[config.npcType]

        newNpc.physics = PhysicsComponent(
            position = config.position.cpy(),
            size = Vector3(config.npcType.width, config.npcType.height, config.npcType.width),
            speed = config.npcType.speed
        )
        newNpc.physics.facingRotationY = initialRotation
        newNpc.physics.updateBounds()

        return newNpc
    }

    fun destroyLinkedObjects(npcId: String) {
        // Find and remove linked spawners
        val spawnersToRemove = sceneManager.game.spawnerSystem.sceneManager.activeSpawners.filter { it.parentId == npcId }
        if (spawnersToRemove.isNotEmpty()) {
            println("NPC $npcId was damaged/killed, removing ${spawnersToRemove.size} linked spawner(s).")
            spawnersToRemove.forEach { sceneManager.game.spawnerSystem.removeSpawner(it) }
        }

        // Find and remove linked audio emitters
        val emittersToRemove = sceneManager.game.audioEmitterSystem.activeEmitters.filter { it.parentId == npcId }
        if (emittersToRemove.isNotEmpty()) {
            println("NPC $npcId was damaged/killed, removing ${emittersToRemove.size} linked audio emitter(s).")
            // Create a copy to avoid ConcurrentModificationException while iterating and removing
            val emittersCopy = com.badlogic.gdx.utils.Array(emittersToRemove.toTypedArray())
            emittersCopy.forEach { sceneManager.game.audioEmitterSystem.removeEmitter(it) }
        }
    }

    fun startDeathSequence(npc: GameNPC, sceneManager: SceneManager) {
        if (npc.currentState == NPCState.DYING) return

        destroyLinkedObjects(npc.id)

        npc.currentState = NPCState.DYING
        npc.fadeOutTimer = FADE_OUT_DURATION
        npc.health = 0f

        println("NPC ${npc.npcType.displayName} is dying from ${npc.lastDamageType}.")

        if (npc.lastDamageType != DamageType.FIRE) {
            sceneManager.game.playerSystem.bloodPoolSystem.addPool(npc.position.cpy(), sceneManager)
            sceneManager.boneSystem.spawnBones(npc.position.cpy(), npc.facingRotationY, sceneManager)
        }
    }

    fun setOnFire(npc: GameNPC, duration: Float, dps: Float) {
        if (npc.isOnFire) return // Already on fire
        npc.isOnFire = true
        npc.onFireTimer = duration
        npc.initialOnFireDuration = duration
        npc.onFireDamagePerSecond = dps
    }

    fun applyKnockback(npc: GameNPC, force: Vector3) {
        if (!npc.isInCar) {
            npc.physics.knockbackVelocity.set(force)
        }
    }

    private fun checkForItemPickups(npc: GameNPC, sceneManager: SceneManager) {
        if (!npc.canCollectItems) return

        val pickupRadius = 3f
        val itemsToRemove = mutableListOf<GameItem>()

        for (item in sceneManager.activeItems) {
            if (item.pickupDelay <= 0f && item.position.dst(npc.position) < pickupRadius) {
                println("${npc.npcType.displayName} found ${item.itemType.displayName}")
                npc.inventory.add(item)
                itemsToRemove.add(item)
            }
        }

        // Remove the items from the main scene list
        itemsToRemove.forEach {
            sceneManager.activeItems.removeValue(it, true)
        }
    }

    private fun checkForWitnessedCrimes(npc: GameNPC, playerSystem: PlayerSystem) {
        // An NPC can only be a witness if they are idle, wandering, and in the world.
        val canBeWitness = npc.currentState == NPCState.IDLE || npc.currentState == NPCState.WANDERING
        if (!canBeWitness || sceneManager.currentScene != SceneType.WORLD) {
            return
        }

        // --- 1. Check for "Threatening" Weapons ---
        val threateningWeapons = listOf(WeaponType.TOMMY_GUN, WeaponType.MACHINE_GUN, WeaponType.DYNAMITE)

        // Check player first
        if (playerSystem.equippedWeapon in threateningWeapons && npc.position.dst(playerSystem.getPosition()) < THREATENING_WEAPON_RADIUS) {
            decideToReportCrime(npc, playerSystem.getPosition(), "a character holding a threatening weapon")
            return // Stop checking for other crimes
        }

        // Check enemies
        for (enemy in sceneManager.activeEnemies) {
            if (enemy.equippedWeapon in threateningWeapons && npc.position.dst(enemy.position) < THREATENING_WEAPON_RADIUS) {
                decideToReportCrime(npc, enemy.position, "a character holding a threatening weapon")
                return
            }
        }
    }

    fun alertNpcsToCrime(crimePosition: Vector3, crimeType: CrimeType) {
        // Alert civilian NPCs
        for (npc in sceneManager.activeNPCs) {
            val canBeWitness = npc.currentState == NPCState.IDLE || npc.currentState == NPCState.WANDERING
            if (canBeWitness && npc.position.dst(crimePosition) < CRIME_DETECTION_RADIUS) {
                decideToReportCrime(npc, crimePosition, crimeType.name)
            }
        }

        // Alert active police officers
        for (police in sceneManager.game.wantedSystem.activePolice) {
            // Police react to crimes much further away than civilians
            if (police.position.dst(crimePosition) < POLICE_HEARING_RADIUS) {
                println("${police.enemyType.displayName} heard a loud crime and is investigating.")

                // If they are on foot, they investigate. If in a car, they drive.
                if (police.isInCar) {
                    police.currentState = AIState.DRIVING_TO_SCENE
                } else {
                    police.currentState = AIState.INVESTIGATING
                }

                police.targetPosition = crimePosition.cpy()
                // Directly report the crime to the wanted system
                sceneManager.game.wantedSystem.reportCrimeByPolice(crimeType)
            }
        }
    }

    private fun decideToReportCrime(npc: GameNPC, crimeLocation: Vector3, crimeDescription: String) {
        val chanceToReport = when (npc.personality) {
            NPCPersonality.COWARDLY -> 0.05f // 5% chance, very unlikely
            NPCPersonality.CIVILIAN -> 0.40f // 40% chance
            NPCPersonality.BRAVE -> 0.85f  // 85% chance
        }

        if (Random.nextFloat() < chanceToReport) {
            // This NPC will report the crime!
            println("${npc.npcType.displayName} (Brave) decided to report crime: $crimeDescription")
            npc.currentState = NPCState.REPORTING_CRIME_SEARCHING
            npc.targetPosition = crimeLocation.cpy() // Store where the crime happened
            npc.stateTimer = 0f // Reset timer for the new state
        } else {
            // This NPC will just flee
            println("${npc.npcType.displayName} (Cowardly) saw crime and is fleeing!")
            npc.currentState = NPCState.FLEEING
            npc.stateTimer = 0f
        }
    }

    fun updateNpcVisuals(npc: GameNPC) {
        npc.modelInstance.transform.idt()
        npc.modelInstance.transform.setTranslation(npc.position)

        // For NPCs, we don't have weapon textures yet, so it's simpler
        val baseTex = npc.baseTexture
        if (baseTex != null) {
            val textureToApply = npcTextures[npc.npcType] ?: baseTex

            val material = npc.modelInstance.materials.first()
            val texAttr = material.get(TextureAttribute.Diffuse) as? TextureAttribute
            if (texAttr?.textureDescription?.texture != textureToApply) {
                material.set(TextureAttribute.createDiffuse(textureToApply))
            }

            val baseAspect = baseTex.width.toFloat() / baseTex.height.toFloat()
            val currentAspect = textureToApply.width.toFloat() / textureToApply.height.toFloat()
            val scaleX = currentAspect / baseAspect
            npc.modelInstance.transform.scale(scaleX, 1f, 1f)
        }

        npc.modelInstance.transform.rotate(Vector3.Y, npc.facingRotationY)
        npc.modelInstance.transform.rotate(Vector3.Z, npc.wobbleAngle)
    }

    fun update(deltaTime: Float, playerSystem: PlayerSystem, sceneManager: SceneManager, blockSize: Float, weatherSystem: WeatherSystem, isInInterior: Boolean) {
        val playerPos = playerSystem.getPosition()
        val iterator = sceneManager.activeNPCs.iterator()
        val visualRainIntensity = weatherSystem.getVisualRainIntensity()

        while(iterator.hasNext()) {
            val npc = iterator.next()

            // Decay car provocation if they are in a car
            if (npc.isInCar && npc.carProvocation > 0) {
                npc.carProvocation -= 5f * deltaTime
                if (npc.carProvocation < 0) npc.carProvocation = 0f
            }

            if (npc.scheduledForDespawn && npc.position.dst2(playerPos) > 4900f) { // 70 units
                iterator.remove()
                println("Despawned NPC ${npc.id} as per post-dialog behavior.")
                continue // Skip the rest of the update for this despawned NPC
            }

            // First, handle AI logic (either driving or on-foot)
            if (!finePosMode) {
                updateAI(npc, playerPos, deltaTime, sceneManager)
            }

            // If the NPC is in a car, their update is complete. Skip on-foot logic.
            if (npc.isInCar) {
                continue
            }

            // HANDLE ON FIRE STATE (DAMAGE & VISUALS)
            if (npc.isOnFire) {
                npc.onFireTimer -= deltaTime

                // RAIN EXTINGUISH
                if (visualRainIntensity > 0.1f && !isInInterior && !npc.isInCar) {
                    val extinguishRate = 3.0f * visualRainIntensity
                    npc.onFireTimer -= extinguishRate * deltaTime

                    if (Random.nextFloat() < 0.3f) {
                        val steamPosition = npc.position.cpy().add(
                            (Random.nextFloat() - 0.5f) * (npc.npcType.width * 0.8f),
                            (Random.nextFloat()) * (npc.npcType.height * 0.8f),
                            0f
                        )
                        sceneManager.game.particleSystem.spawnEffect(ParticleEffectType.SMOKE_FRAME_1, steamPosition)
                    }
                }

                if (npc.onFireTimer <= 0) {
                    npc.isOnFire = false
                } else {
                    // Damage falloff over the duration of the effect
                    val progress = (npc.onFireTimer / npc.initialOnFireDuration).coerceIn(0f, 1f)
                    val currentDps = npc.onFireDamagePerSecond * progress
                    val damageThisFrame = currentDps * deltaTime

                    if (npc.takeDamage(damageThisFrame, DamageType.FIRE, sceneManager) && npc.currentState != NPCState.DYING) {
                        sceneManager.npcSystem.startDeathSequence(npc, sceneManager)
                    }

                    // 1% chance for big head on fire effect
                    if (Random.nextFloat() < 0.01f) {
                        val particlePos = npc.position.cpy().add(0f, npc.npcType.height * 0.8f, 0f)
                        sceneManager.game.particleSystem.spawnEffect(ParticleEffectType.FIRE_FLAME, particlePos)
                    }

                    // Frequent, smaller body flames
                    if (Random.nextFloat() < 0.6f) {
                        val halfWidth = npc.npcType.width / 2f
                        val offsetX = (Random.nextFloat() - 0.5f) * (halfWidth * 1.5f)
                        val particlePos = npc.position.cpy().add(offsetX, -npc.npcType.height * 0.2f, 0f)

                        sceneManager.game.particleSystem.spawnEffect(ParticleEffectType.BODY_FLAME, particlePos)
                    }
                }
            }

            // DYING STATE LOGIC
            if (npc.currentState == NPCState.DYING) {
                npc.fadeOutTimer -= deltaTime

                if (npc.lastDamageType == DamageType.FIRE && !npc.ashSpawned && npc.fadeOutTimer <= ASH_SPAWN_START_TIME) {
                    val groundY = sceneManager.findHighestSupportY(npc.position.x, npc.position.z, npc.position.y, 0.1f, blockSize)
                    val ashPosition = Vector3(npc.position.x, groundY + 0.86f, npc.position.z)
                    sceneManager.game.particleSystem.spawnEffect(ParticleEffectType.BURNED_ASH, ashPosition)
                    npc.ashSpawned = true
                }

                if (npc.fadeOutTimer <= 0f) {
                    iterator.remove()
                    continue
                }

                npc.blendingAttribute.opacity = (npc.fadeOutTimer / FADE_OUT_DURATION).coerceIn(0f, 1f)
                continue
            }

            if (npc.bleedTimer > 0f) {
                npc.bleedTimer -= deltaTime

                // Check if the NPC is moving and it's time to spawn a drip
                if (sceneManager.game.uiManager.getViolenceLevel() == ViolenceLevel.ULTRA_VIOLENCE) {
                    val damageThisFrame = BLEED_DAMAGE_PER_SECOND * deltaTime
                    if (npc.takeDamage(damageThisFrame, DamageType.GENERIC, sceneManager) && npc.currentState != NPCState.DYING) {
                        sceneManager.npcSystem.startDeathSequence(npc, sceneManager)
                    }
                }

                if (npc.physics.isMoving) {
                    npc.bloodDripSpawnTimer -= deltaTime
                    if (npc.bloodDripSpawnTimer <= 0f) {

                        val violenceLevel = sceneManager.game.uiManager.getViolenceLevel()

                        if (violenceLevel == ViolenceLevel.ULTRA_VIOLENCE) {
                            npc.bloodDripSpawnTimer = 0.25f
                            val groundY = sceneManager.findHighestSupportY(npc.position.x, npc.position.z, npc.position.y, 0.1f, blockSize)
                            val trailPosition = Vector3(npc.position.x, groundY, npc.position.z)
                            sceneManager.game.footprintSystem.spawnBloodTrail(trailPosition, sceneManager)

                            if (Random.nextFloat() < 0.25f) {
                                val spawnPosition = npc.physics.position.cpy().add(
                                    (Random.nextFloat() - 0.5f) * 1f,
                                    (Random.nextFloat() - 0.5f) * 2f,
                                    (Random.nextFloat() - 0.5f) * 1f
                                )
                                sceneManager.game.particleSystem.spawnEffect(ParticleEffectType.BLOOD_DRIP, spawnPosition)
                            }
                        } else {
                            npc.bloodDripSpawnTimer = GameNPC.BLOOD_DRIP_INTERVAL

                            // Spawn a drip at the NPC's position with a slight random offset
                            val spawnPosition = npc.physics.position.cpy().add(
                                (Random.nextFloat() - 0.5f) * 1f,
                                (Random.nextFloat() - 0.5f) * 2f,
                                (Random.nextFloat() - 0.5f) * 1f
                            )
                            sceneManager.game.particleSystem.spawnEffect(ParticleEffectType.BLOOD_DRIP, spawnPosition)
                        }
                    }
                }
            }

            checkForItemPickups(npc, sceneManager)

            // Gift-giving logic
            if (npc.isHonest && npc.inventory.isNotEmpty() && npc.currentState != NPCState.PROVOKED) {
                val distanceToPlayer = npc.position.dst(playerPos)
                if (distanceToPlayer < 5f) { // Must be close to the player
                    // 0.2% chance per frame to offer a gift
                    if (Random.nextFloat() < 0.002f) {
                        val itemToGive = npc.inventory.removeAt(0) // Get and remove the first item
                        val dropPosition = npc.position.cpy().add(0f, 0.5f, 0f)

                        val droppedItem = sceneManager.game.itemSystem.createItem(dropPosition, itemToGive.itemType)
                        if (droppedItem != null) {
                            droppedItem.ammo = itemToGive.ammo // Preserve ammo if it's a weapon
                            sceneManager.activeItems.add(droppedItem)

                            val message = "${npc.npcType.displayName} gave you a ${itemToGive.itemType.displayName}!"
                            sceneManager.game.uiManager.setPersistentMessage(message)
                            println(message)
                        }
                    }
                }
            }

            // Reset moving flag
           val distanceToPlayer = npc.physics.position.dst(playerPos)
            // ACTIVATION CHECK
            if (distanceToPlayer > activationRange) {
                // This NPC is too far away to matter
                if (npc.currentState != NPCState.IDLE && npc.currentState != NPCState.DYING) {
                    npc.currentState = NPCState.IDLE
                    npc.targetPosition = null
                }
                continue
            }
            npc.decayProvocation(deltaTime)

            if (!finePosMode) {
                updateAI(npc, playerPos, deltaTime, sceneManager)
            }

            npc.position.set(npc.physics.position) // Sync legacy position
            npc.isMoving = npc.physics.isMoving     // Sync legacy moving flag

            npc.wobbleAngle = npc.physics.wobbleAngle
            npc.facingRotationY = npc.physics.facingRotationY // The AI now controls this via the component

            updateNpcVisuals(npc)
        }
    }

    fun handleEjectionFromCar(npc: GameNPC, ejectedFromCar: GameCar, sceneManager: SceneManager) {
        // 1. Physically remove the NPC from the car
        handleCarExit(npc, sceneManager)

        // 2. Set their AI state to IDLE and give them a "stun" timer.
        npc.currentState = NPCState.IDLE
        npc.stateTimer = 1.5f // NPCs are a bit more confused before reacting

        // 3. Schedule the AI to make a decision after the stun wears off.
        com.badlogic.gdx.utils.Timer.schedule(object : com.badlogic.gdx.utils.Timer.Task() {
            override fun run() {
                if (npc.health <= 0) return

                // Let's give it a 40% chance to chase the car.
                if (Random.nextFloat() < 0.4f) {
                    // CHOICE 1: CHASE THE CAR!
                    println("${npc.npcType.displayName} is chasing their stolen car!")
                    npc.carToChaseId = ejectedFromCar.id
                    npc.currentState = NPCState.CHASING_STOLEN_CAR
                    npc.chaseTimer = 25f // Give them 12 seconds to chase before giving up
                } else {
                    // CHOICE 2: Don't chase, just flee on foot.
                    println("${npc.npcType.displayName} is scared and running away on foot.")
                    npc.currentState = NPCState.FLEEING
                }
            }
        }, npc.stateTimer)
    }

    fun toggleRotation() {
        currentRotation = if (currentRotation == 0f) 180f else 0f
    }

    fun handleCarExit(npc: GameNPC, sceneManager: SceneManager) {
        val car = npc.drivingCar ?: return

        // Calculate a safe exit position next to the car's CURRENT location
        val exitOffset = Vector3(-5f, 0f, 0f).rotate(Vector3.Y, car.visualRotationY)
        val potentialExitPos = car.position.cpy().add(exitOffset)

        // Find the ground level at this potential exit spot
        var groundY = sceneManager.findHighestSupportY(potentialExitPos.x, potentialExitPos.z, car.position.y, 0.1f, sceneManager.game.blockSize)
        if (groundY < -500f) groundY = 0f

        // Set the character's new position
        npc.position.set(potentialExitPos.x, groundY + (npc.npcType.height / 2f), potentialExitPos.z)
        // Also sync the physics component immediately
        npc.physics.position.set(npc.position)
        npc.physics.updateBounds()

        // Now, call the simplified exitCar method on the NPC object itself
        npc.exitCar()
        println("${npc.npcType.displayName} exited a car at ${npc.position}")
    }

    private fun updatePathFollowerAI(npc: GameNPC, deltaTime: Float) {
        var desiredMovement = Vector3.Zero
        val charPathSystem = sceneManager.game.characterPathSystem

        // --- Path Initialization (same for both styles) ---
        if (npc.currentPathNode == null) {
            val startNode = npc.assignedPathId?.let { charPathSystem.nodes[it] }
                ?: charPathSystem.nodes.values.minByOrNull { it.position.dst2(npc.position) }

            if (startNode == null) {
                characterPhysicsSystem.update(npc.physics, desiredMovement, deltaTime) // Pass the empty vector
                return // No paths exist
            }
            npc.currentPathNode = startNode

            // When starting, immediately set the sub-state timer for the Stop-and-Go style
            if (npc.pathFollowingStyle == PathFollowingStyle.STOP_AND_GO) {
                npc.pathfindingState = PathfindingSubState.MOVING
                npc.subStateTimer = Random.nextFloat() * 5f + 3f // Start by moving for 3 to 8 seconds
            }
        }
        val currentNode = npc.currentPathNode ?: return // Exit if no path node

        // Check if the path is for a mission that isn't active.
        if (currentNode.isMissionOnly) {
            val mission = sceneManager.game.missionSystem.getMissionDefinition(currentNode.missionId ?: "")
            if (mission == null || !sceneManager.game.missionSystem.isMissionActive(mission.id)) {
                characterPhysicsSystem.update(npc.physics, Vector3.Zero, deltaTime) // Stop moving
                return
            }
        }

        // --- AI LOGIC: Differentiated by Style ---
        if (npc.pathFollowingStyle == PathFollowingStyle.STOP_AND_GO) {
            // TOURIST BEHAVIOR
            npc.subStateTimer -= deltaTime

            when (npc.pathfindingState) {
                PathfindingSubState.MOVING -> {
                    // Execute the standard movement logic
                    desiredMovement = calculatePathMovement(npc, currentNode, charPathSystem)

                    if (npc.subStateTimer <= 0f) {
                        // Time to pause
                        npc.pathfindingState = PathfindingSubState.PAUSING
                        npc.subStateTimer = Random.nextFloat() * 3f + 1.5f // Pause for 1.5 to 4.5 seconds
                        desiredMovement.set(Vector3.Zero) // Stop for this frame
                    }
                }
                PathfindingSubState.PAUSING -> {
                    // Stand still
                    desiredMovement.set(Vector3.Zero)

                    if (npc.subStateTimer <= 0f) {
                        // Time to move again
                        npc.pathfindingState = PathfindingSubState.MOVING
                        npc.subStateTimer = Random.nextFloat() * 5f + 3f // Move for 3 to 8 seconds
                    }
                }
            }

        } else {
            // CONTINUOUS BEHAVIOR
            desiredMovement = calculatePathMovement(npc, currentNode, charPathSystem)
        }

        // --- Final Physics Update (same for both styles) ---
        if (!desiredMovement.isZero) {
            npc.physics.facingRotationY = if (desiredMovement.x > 0) 0f else 180f
        }
        characterPhysicsSystem.update(npc.physics, desiredMovement, deltaTime)
        npc.physics.speed = npc.npcType.speed // Restore default speed
    }

    private fun calculatePathMovement(npc: GameNPC, currentNode: CharacterPathNode, charPathSystem: CharacterPathSystem): Vector3 {
        var nextNode = currentNode.nextNodeId?.let { charPathSystem.nodes[it] }

        if (nextNode == null) {
            val prevNode = currentNode.previousNodeId?.let { charPathSystem.nodes[it] }
            if (prevNode != null && !prevNode.isOneWay) {
                nextNode = prevNode // Set the previous node as the new target to go back.
            }
        }

        if (nextNode != null) {
            val segmentStart = currentNode.position
            val segmentEnd = nextNode.position

            // Convert 3D positions to 2D for the calculation on the X/Z plane
            val segmentStart2D = tempVec2_1.set(segmentStart.x, segmentStart.z)
            val segmentEnd2D = tempVec2_2.set(segmentEnd.x, segmentEnd.z)
            val npcPos2D = tempVec2_3.set(npc.position.x, npc.position.z)

            // Perform the 2D calculation
            Intersector.nearestSegmentPoint(segmentStart2D, segmentEnd2D, npcPos2D, tempVec2_Result)

            // Convert the 2D result back to a 3D point
            val closestPointOnSegment = Vector3(tempVec2_Result.x, npc.position.y, tempVec2_Result.y)

            // Aim for a point slightly ahead on the path for smoother movement
            val direction = segmentEnd.cpy().sub(segmentStart).nor()
            val lookAheadDistance = 2.0f
            val targetPoint = closestPointOnSegment.cpy().mulAdd(direction, lookAheadDistance)

            val perpendicular = Vector3(-direction.z, 0f, direction.x)
            val randomOffset = (Random.nextFloat() - 0.5f) * 2.0f
            targetPoint.mulAdd(perpendicular, randomOffset)

            // Calculate final movement vector
            val directionToTarget = targetPoint.sub(npc.position)
            val chillSpeed = npc.npcType.speed * 0.4f
            npc.physics.speed = chillSpeed

            // If we are close to the next node, switch our target to it
            if (npc.position.dst2(nextNode.position) < 16f) {
                npc.currentPathNode = nextNode
            }
            return directionToTarget.nor()
        }
        return Vector3.Zero
    }

    private fun updateAI(npc: GameNPC, playerPos: Vector3, deltaTime: Float, sceneManager: SceneManager) {
        var desiredMovement = Vector3.Zero  // Default to no movement

        // Witness logic
        checkForWitnessedCrimes(npc, sceneManager.playerSystem)

        if (npc.currentState == NPCState.DYING) {
            return // A dead NPC should not think.
        }

        if (npc.currentState == NPCState.CHASING_STOLEN_CAR) {
            npc.chaseTimer -= deltaTime

            // Find the car we are supposed to be chasing
            val targetCar = sceneManager.activeCars.find { it.id == npc.carToChaseId }

            // CHECK STOP CONDITIONS
            if (targetCar == null || npc.chaseTimer <= 0f || npc.position.dst(targetCar.position) > MAX_CHASE_DISTANCE) {
                if (targetCar == null) println("${npc.npcType.displayName}'s car was destroyed. Stopping chase.")
                else if (npc.chaseTimer <= 0f) println("${npc.npcType.displayName} gave up the chase.")
                else println("${npc.npcType.displayName} lost sight of the car. Stopping chase.")

                npc.currentState = NPCState.IDLE
                npc.carToChaseId = null
                desiredMovement = Vector3.Zero // Stop moving
            } else {
                // CONTINUE CHASING
                desiredMovement = targetCar.position.cpy().sub(npc.physics.position).nor()
            }

            // Apply physics and exit this AI update
            characterPhysicsSystem.update(npc.physics, desiredMovement, deltaTime)
            return
        }

        val distanceToPlayer = npc.position.dst(playerPos)
        if (distanceToPlayer > MAX_CHASE_DISTANCE) {
            // If player is too far, stop being provoked or following.
            if (npc.currentState == NPCState.PROVOKED || npc.currentState == NPCState.FOLLOWING) {
                println("${npc.npcType.displayName} lost interest and is returning to IDLE.")
                npc.currentState = NPCState.IDLE
                npc.targetPosition = null
            }
        }

        val modifiers = sceneManager.game.missionSystem.activeModifiers
        val behaviorToUse = modifiers?.overrideNpcBehavior ?: npc.behaviorType

        if (npc.isInCar) {
            val car = npc.drivingCar!!

            if (npc.currentState == NPCState.PATROLLING_IN_CAR) {
                // 1. Self-preservation
                if (car.health < 40f) {
                    println("${npc.npcType.displayName} is bailing out!")
                    handleCarExit(npc, sceneManager)
                    npc.currentState = NPCState.IDLE
                    return
                }

                val modifiers = sceneManager.game.missionSystem.activeModifiers
                if (modifiers?.civiliansFleeOnSight == true && npc.behaviorType != NPCBehavior.GUARD) {
                    // This NPC is a civilian and should flee
                    if (npc.position.dst(playerPos) < 30f) {
                        val awayDirection = npc.physics.position.cpy().sub(playerPos).nor()
                        characterPhysicsSystem.update(npc.physics, awayDirection, deltaTime)
                        return
                    }
                }

                // 2. Path Following Logic
                var targetNode = npc.currentTargetPathNodeId?.let { sceneManager.game.carPathSystem.nodes[it] }

                if (targetNode == null) {
                    targetNode = sceneManager.game.carPathSystem.findNearestNode(car.position)
                    npc.currentTargetPathNodeId = targetNode?.id
                }

                if (targetNode != null) {
                    if (car.position.dst2(targetNode.position) < 25f) {
                        val nextNode = targetNode.nextNodeId?.let { sceneManager.game.carPathSystem.nodes[it] }
                        npc.currentTargetPathNodeId = nextNode?.id
                        if (nextNode != null) {
                            desiredMovement = nextNode.position.cpy().sub(car.position).nor()
                        }
                    } else {
                        desiredMovement = targetNode.position.cpy().sub(car.position).nor()
                    }
                }

                car.updateAIControlled(deltaTime, desiredMovement, sceneManager, sceneManager.activeCars)
                npc.position.set(car.position)
                return
            }

            if (car.position.dst(playerPos) > FLEE_EXIT_DISTANCE) {
                println("${npc.npcType.displayName} is exiting the car.")
                handleCarExit(npc, sceneManager)
                npc.currentState = NPCState.IDLE
            } else {
                val awayDirection = car.position.cpy().sub(playerPos).nor()
                car.updateAIControlled(deltaTime, awayDirection, sceneManager, sceneManager.activeCars)
                npc.position.set(car.position)
            }
            return // Skip on-foot AI
        }

        // High-priority states override standard behaviors
        when (npc.currentState) {
            NPCState.REPORTING_CRIME_SEARCHING -> {
                val closestPolice = sceneManager.activeEnemies.filter { it.enemyType.name.startsWith("POLICE") }
                    .minByOrNull { it.position.dst2(npc.position) }
                if (closestPolice != null && npc.position.dst(closestPolice.position) < POLICE_SEARCH_RADIUS) {
                    npc.targetPosition = closestPolice.position.cpy()
                    npc.currentState = NPCState.REPORTING_CRIME_RUNNING
                    println("${npc.npcType.displayName} found a cop and is running to report.")
                } else {
                    npc.stateTimer += deltaTime
                    if (npc.stateTimer > 10f) {
                        println("${npc.npcType.displayName} couldn't find a cop and gave up.")
                        npc.currentState = NPCState.IDLE
                    }
                }
                desiredMovement = Vector3.Zero
            }
            NPCState.REPORTING_CRIME_RUNNING -> {
                val copPosition = npc.targetPosition
                if (copPosition == null) {
                    npc.currentState = NPCState.IDLE
                    return
                }
                if (npc.position.dst(copPosition) < REPORTING_DISTANCE) {
                    npc.currentState = NPCState.REPORTING_CRIME_DELIVERING
                    npc.stateTimer = REPORTING_DURATION
                } else {
                    desiredMovement = copPosition.cpy().sub(npc.physics.position).nor()
                }
            }
            NPCState.REPORTING_CRIME_DELIVERING -> {
                npc.stateTimer -= deltaTime
                desiredMovement = Vector3.Zero
                if (npc.stateTimer <= 0f) {
                    val cop = sceneManager.activeEnemies.find { it.position.epsilonEquals(npc.targetPosition, 1f) }
                    if (cop != null) {
                        println("Report delivered to ${cop.enemyType.displayName}!")
                        cop.currentState = AIState.INVESTIGATING
                        // We stored the crime scene in `homePosition` when the report started
                        cop.targetPosition = npc.homePosition.cpy()
                        sceneManager.game.wantedSystem.reportCrimeByWitness(CrimeType.SHOOT_WEAPON, cop.targetPosition!!)
                    }
                    npc.currentState = NPCState.WANDERING
                    npc.stateTimer = 0f
                }
            }
            NPCState.FLEEING -> {
                npc.stateTimer += deltaTime
                if (npc.stateTimer >= fleeDuration) {
                    npc.currentState = NPCState.IDLE
                    npc.stateTimer = 0f
                } else {
                    val awayDirection = npc.physics.position.cpy().sub(playerPos).nor()
                    desiredMovement = awayDirection
                }
            }
            NPCState.PROVOKED -> {
                npc.stateTimer += deltaTime
                if (npc.stateTimer > hostileCooldownTime || npc.physics.position.dst(playerPos) > provokedChaseDistance) {
                    npc.currentState = NPCState.COOLDOWN
                    npc.provocationLevel = 0f
                    npc.stateTimer = 0f
                } else {
                    if (npc.physics.position.dst(playerPos) > stopProvokedChaseDistance) {
                        desiredMovement = playerPos.cpy().sub(npc.physics.position).nor()
                    }
                }
            }
            NPCState.COOLDOWN -> {
                npc.stateTimer += deltaTime
                if (npc.stateTimer > 5f) {
                    npc.currentState = NPCState.IDLE
                    npc.stateTimer = 0f
                }
            }
            // If not in a high-priority state, execute standard behavior
            else -> {
                if (npc.isOnFire) {
                    val awayDirection = npc.physics.position.cpy().sub(playerPos).nor()
                    desiredMovement = awayDirection
                } else {
                    when (behaviorToUse) {
                        NPCBehavior.STATIONARY -> { /* No movement */ }
                        NPCBehavior.WATCHER -> {
                            npc.physics.facingRotationY = if (playerPos.x > npc.physics.position.x) 0f else 180f
                        }
                        NPCBehavior.WANDER -> updateWanderAI(npc, deltaTime, sceneManager)
                        NPCBehavior.FOLLOW -> {
                            if (distanceToPlayer > stopFollowingDistance) {
                                if (npc.currentState != NPCState.FOLLOWING) npc.currentState = NPCState.FOLLOWING
                                desiredMovement = playerPos.cpy().sub(npc.physics.position).nor()
                            } else {
                                if (npc.currentState != NPCState.IDLE) npc.currentState = NPCState.IDLE
                            }
                        }
                        NPCBehavior.GUARD -> {
                            if (distanceToPlayer > stopProvokedChaseDistance) {
                                desiredMovement = playerPos.cpy().sub(npc.physics.position).nor()
                            }
                        }
                        NPCBehavior.PATH_FOLLOWER -> {
                            updatePathFollowerAI(npc, deltaTime)
                            return // Path follower has its own physics update call
                        }
                    }
                }
            }
        }

        if (!desiredMovement.isZero) {
            npc.physics.facingRotationY = if (desiredMovement.x > 0) 0f else 180f
        }

        characterPhysicsSystem.update(npc.physics, desiredMovement, deltaTime)
    }

    private fun updateWanderAI(npc: GameNPC, deltaTime: Float, sceneManager: SceneManager) {
        when (npc.currentState) {
            NPCState.IDLE -> {
                npc.stateTimer += deltaTime
                if (npc.stateTimer > wanderPauseTime) {
                    val randomAngle = (Random.nextFloat() * 2 * Math.PI).toFloat()
                    val randomDist = Random.nextFloat() * wanderRadius
                    val newTarget = Vector3(
                        npc.homePosition.x + cos(randomAngle) * randomDist,
                        npc.position.y,
                        npc.homePosition.z + sin(randomAngle) * randomDist
                    )
                    npc.targetPosition = newTarget
                    npc.currentState = NPCState.WANDERING
                    npc.stateTimer = 0f
                }
            }
            NPCState.WANDERING -> {
                val target = npc.targetPosition
                if (target == null || npc.position.dst(target) < 1.5f) {
                    npc.currentState = NPCState.IDLE
                    npc.targetPosition = null
                    npc.stateTimer = 0f
                } else {
                    moveTowards(npc, target, deltaTime, sceneManager)
                }
            }
            else -> npc.currentState = NPCState.IDLE // Reset if in a weird state
        }
    }

    private fun moveTowards(npc: GameNPC, target: Vector3, deltaTime: Float, sceneManager: SceneManager) {
        val direction = Vector3(target).sub(npc.physics.position).nor()
        characterPhysicsSystem.update(npc.physics, direction, deltaTime)
    }

    fun renderNPCs(camera: Camera, environment: Environment, npcs: Array<GameNPC>) {
        if (npcs.isEmpty) return

        billboardShaderProvider.setEnvironment(environment)
        billboardModelBatch.begin(camera)

        // Collect all NPC instances.
        renderableInstances.clear()
        for (npc in npcs) {
            // Only render NPCs who are not in a car
            if (!npc.isInCar) {
                renderableInstances.add(npc.modelInstance)
            }
        }

        // Render all NPCs at once.
        billboardModelBatch.render(renderableInstances, environment)
        billboardModelBatch.end()
    }

    fun nextNPCType() {
        currentNPCTypeIndex = (currentNPCTypeIndex + 1) % NPCType.entries.size
        currentSelectedNPCType = NPCType.entries[currentNPCTypeIndex]
    }

    fun previousNPCType() {
        currentNPCTypeIndex = if (currentNPCTypeIndex > 0) currentNPCTypeIndex - 1 else NPCType.entries.size - 1
        currentSelectedNPCType = NPCType.entries[currentNPCTypeIndex]
    }

    fun nextBehavior() {
        currentBehaviorIndex = (currentBehaviorIndex + 1) % NPCBehavior.entries.size
        currentSelectedBehavior = NPCBehavior.entries[currentBehaviorIndex]
    }

    fun previousBehavior() {
        currentBehaviorIndex = if (currentBehaviorIndex > 0) currentBehaviorIndex - 1 else NPCBehavior.entries.size - 1
        currentSelectedBehavior = NPCBehavior.entries[currentBehaviorIndex]
    }

    fun dispose() {
        npcModels.values.forEach { it.dispose() }
        npcTextures.values.forEach { it.dispose() }
        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()
    }
}
