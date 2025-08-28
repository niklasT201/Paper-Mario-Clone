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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class DamageReaction {
    FLEE,       // Runs away when damaged
    FIGHT_BACK  // Becomes hostile when damaged
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
    GUARD("Guard")              // Hostile on sight (for story villains)
}

// AI State for the NPC's state machine
enum class NPCState {
    IDLE,               // Doing nothing (standing, pausing)
    WANDERING,          // Moving to a random point
    FOLLOWING,          // Moving towards the player
    PROVOKED,           // Hostile state, attacking the player
    COOLDOWN,           // Temp state after hostility before returning to normal
    FLEEING,
    DYING
}

// --- MAIN NPC DATA CLASS ---
data class GameNPC(
    val id: String = UUID.randomUUID().toString(),
    val modelInstance: ModelInstance,
    val npcType: NPCType,
    var behaviorType: NPCBehavior, // Behavior can change during the story
    var position: Vector3,
    var health: Float = npcType.baseHealth
) {
    var isOnFire: Boolean = false
    var onFireTimer: Float = 0f
    var initialOnFireDuration: Float = 0f
    var onFireDamagePerSecond: Float = 0f
    @Transient lateinit var physics: PhysicsComponent
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
            isInCar = true
            drivingCar = car
            currentSeat = seat
            println("${this.npcType.displayName} entered a car.")
        }
    }

    fun exitCar() {
        val car = drivingCar ?: return
        car.removeOccupant(this)
        isInCar = false
        // TODO: In the future, add logic here to place the NPC in a safe spot next to the car.
        this.position.set(car.position.x - 5f, car.position.y, car.position.z)
        println("${this.npcType.displayName} exited a car.")
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

    fun takeDamage(damage: Float, type: DamageType): Boolean {
        if (health <= 0) return false

        // Apply damage first, regardless of reaction
        health -= damage

        // If not dead and was shot or hit with melee, start bleeding
        if (health > 0 && (type == DamageType.GENERIC || type == DamageType.MELEE)) {
            this.bleedTimer = BLEED_DURATION
        }

        // The type of the killing blow is what matters.
        if (health <= 0) {
            lastDamageType = type
        }
        println("NPC ${npcType.displayName} took $damage $type damage. HP: ${health.coerceAtLeast(0f)}")

        if (health <= 0) return true // Died, no need for reaction logic

        // If the NPC is already a guard or provoked, they just stay mad
        if (behaviorType == NPCBehavior.GUARD || currentState == NPCState.PROVOKED) {
            return false // Not dead yet
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
    private lateinit var characterPhysicsSystem: CharacterPhysicsSystem
    private val npcModels = mutableMapOf<NPCType, Model>()
    private val npcTextures = mutableMapOf<NPCType, Texture>()
    private lateinit var billboardModelBatch: ModelBatch
    private lateinit var billboardShaderProvider: BillboardShaderProvider
    private val renderableInstances = Array<ModelInstance>()
    private val WOBBLE_AMPLITUDE_DEGREES = 5f // How far it tilts left/right. 10 degrees is a good start.
    private val WOBBLE_FREQUENCY = 6f
    private val fleeDuration = 5.0f

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

    private val FALL_SPEED = 25f
    private val MAX_STEP_HEIGHT = 4.0f
    override var finePosMode: Boolean = false
    override val fineStep: Float = 0.25f
    private val tempBlockBounds = BoundingBox()
    private val nearbyBlocks = Array<GameBlock>()
    private val FADE_OUT_DURATION = 1.5f
    private val ASH_SPAWN_START_TIME = 1.5f

    lateinit var sceneManager: SceneManager
    private lateinit var raycastSystem: RaycastSystem
    private val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)
    private val tempVec3 = Vector3()
    private var blockSize: Float = 4f

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
        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            val surfaceY = findHighestSurfaceYAt(tempVec3.x, tempVec3.z)

            // Position the NPC so its feet are on the surface.
            val npcType = currentSelectedNPCType
            val npcPosition = Vector3(tempVec3.x, surfaceY + npcType.height / 2f, tempVec3.z)

            val newNPC = createNPC(
                npcPosition,
                currentSelectedNPCType,
                currentSelectedBehavior,
                currentRotation // Pass the saved rotation
            )

            if (newNPC != null) {
                sceneManager.activeNPCs.add(newNPC)
                sceneManager.game.lastPlacedInstance = newNPC
                println("Placed ${newNPC.npcType.displayName} with ${newNPC.behaviorType.displayName} behavior at $npcPosition")
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

    fun createNPC(position: Vector3, npcType: NPCType, behavior: NPCBehavior, initialRotation: Float = 0f): GameNPC? {
        val model = npcModels[npcType] ?: return null
        val instance = ModelInstance(model)
        instance.userData = "character"

        val newNpc = GameNPC(
            modelInstance = instance,
            npcType = npcType,
            behaviorType = behavior,
            position = position.cpy()
        )

        newNpc.physics = PhysicsComponent(
            position = position.cpy(),
            size = Vector3(npcType.width, npcType.height, npcType.width),
            speed = npcType.speed
        )
        newNpc.physics.facingRotationY = initialRotation // Set initial facing direction
        newNpc.physics.updateBounds()

        return newNpc
    }

    fun startDeathSequence(npc: GameNPC, sceneManager: SceneManager) {
        if (npc.currentState == NPCState.DYING) return

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

    fun update(deltaTime: Float, playerSystem: PlayerSystem, sceneManager: SceneManager, blockSize: Float) {
        if (playerSystem.isDriving) return

        val playerPos = playerSystem.getPosition()
        val iterator = sceneManager.activeNPCs.iterator()

        while(iterator.hasNext()) {
            val npc = iterator.next()

            // HANDLE ON FIRE STATE (DAMAGE & VISUALS)
            if (npc.isOnFire) {
                npc.onFireTimer -= deltaTime
                if (npc.onFireTimer <= 0) {
                    npc.isOnFire = false
                } else {
                    // Damage falloff over the duration of the effect
                    val progress = (npc.onFireTimer / npc.initialOnFireDuration).coerceIn(0f, 1f)
                    val currentDps = npc.onFireDamagePerSecond * progress
                    val damageThisFrame = currentDps * deltaTime

                    if (npc.takeDamage(damageThisFrame, DamageType.FIRE) && npc.currentState != NPCState.DYING) {
                        sceneManager.npcSystem.startDeathSequence(npc, sceneManager)
                    }

                    // Spawn flame particles on the NPC
                    if (Random.nextFloat() < 0.3f) { // 30% chance each frame
                        val particlePos = npc.position.cpy().add(0f, npc.npcType.height * 0.5f, 0f)
                        sceneManager.game.particleSystem.spawnEffect(ParticleEffectType.FIRE_FLAME, particlePos)
                    }
                }
            }

            // Skip all AI, physics, and visual updates for NPCs inside cars
            if (npc.isInCar) continue

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
                if (npc.physics.isMoving) {
                    npc.bloodDripSpawnTimer -= deltaTime
                    if (npc.bloodDripSpawnTimer <= 0f) {
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

            // Reset moving flag
            val distanceToPlayer = npc.physics.position.dst(playerPos)
            // ACTIVATION CHECK
            if (distanceToPlayer > activationRange) {
                // This NPC is too far away to matter
                if (npc.currentState == NPCState.WANDERING || npc.currentState == NPCState.FOLLOWING) {
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

            npc.updateVisuals()
        }
    }

    fun toggleRotation() {
        currentRotation = if (currentRotation == 0f) 180f else 0f
    }

    private fun updateAI(npc: GameNPC, playerPos: Vector3, deltaTime: Float, sceneManager: SceneManager) {
        var desiredMovement = Vector3.Zero // Default to no movement

        if (npc.isOnFire) {
            // Find the closest fire to run away from
            val closestFire = sceneManager.game.fireSystem.activeFires.minByOrNull { it.gameObject.position.dst2(npc.position) }
            if (closestFire != null) {
                val awayDirection = npc.physics.position.cpy().sub(closestFire.gameObject.position).nor()
                desiredMovement = awayDirection
            } else {
                // No fire found? Just run in a random direction.
                if (npc.targetPosition == null || npc.physics.position.dst2(npc.targetPosition!!) < 4f) {
                    npc.targetPosition = npc.position.cpy().add((Random.nextFloat() - 0.5f) * 20f, 0f, (Random.nextFloat() - 0.5f) * 20f)
                }
                desiredMovement = npc.targetPosition!!.cpy().sub(npc.physics.position).nor()
            }
        }

        else if (npc.currentState == NPCState.FLEEING) {
            npc.stateTimer += deltaTime

            // Check if the flee duration is over
            if (npc.stateTimer >= fleeDuration) {
                // Fleeing is over, return to idle
                npc.currentState = NPCState.IDLE
                npc.stateTimer = 0f
            } else {
                // Calculate direction away from the player
                val awayDirection = npc.physics.position.cpy().sub(playerPos).nor()
                desiredMovement = awayDirection
            }
        }
        // State 2: Provoked/Hostile
        else if (npc.currentState == NPCState.PROVOKED) {
            npc.stateTimer += deltaTime
            if (npc.stateTimer > hostileCooldownTime || npc.physics.position.dst(playerPos) > provokedChaseDistance) {
                // Cooldown is over or player escaped
                npc.currentState = NPCState.COOLDOWN
                npc.provocationLevel = 0f // Forgiven!
                npc.stateTimer = 0f
            } else {
                // Chase the player
                if (npc.physics.position.dst(playerPos) > stopProvokedChaseDistance) {
                    desiredMovement = playerPos.cpy().sub(npc.physics.position).nor()
                }
            }
        }

        // A brief period after being hostile where the NPC does nothing.
        else if (npc.currentState == NPCState.COOLDOWN) {
            npc.stateTimer += deltaTime
            if (npc.stateTimer > 5f) { // 5 second cooldown
                npc.currentState = NPCState.IDLE
                npc.stateTimer = 0f
            }
        }

        // Standard Behaviors
        else {
            when (npc.behaviorType) {
                NPCBehavior.STATIONARY -> {
                    npc.currentState = NPCState.IDLE
                    // No movement, facing direction is fixed from placement
                }
                NPCBehavior.WATCHER -> {
                    npc.currentState = NPCState.IDLE
                    // Face the player
                    npc.physics.facingRotationY = if (playerPos.x > npc.physics.position.x) 0f else 180f
                }
                NPCBehavior.WANDER -> {
                    if (npc.currentState == NPCState.IDLE) {
                        npc.stateTimer += deltaTime
                        if (npc.stateTimer > wanderPauseTime) {
                            val randomAngle = (Random.nextFloat() * 2 * Math.PI).toFloat()
                            val randomDist = Random.nextFloat() * wanderRadius
                            npc.targetPosition = Vector3(
                                npc.homePosition.x + cos(randomAngle) * randomDist,
                                npc.position.y, // Y will be handled by physics
                                npc.homePosition.z + sin(randomAngle) * randomDist
                            )
                            npc.currentState = NPCState.WANDERING
                            npc.stateTimer = 0f
                        }
                    } else if (npc.currentState == NPCState.WANDERING) {
                        val target = npc.targetPosition
                        if (target == null || npc.physics.position.dst(target) < 1.5f) {
                            npc.currentState = NPCState.IDLE
                            npc.targetPosition = null
                            npc.stateTimer = 0f
                        } else {
                            desiredMovement = target.cpy().sub(npc.physics.position).nor()
                        }
                    }
                }
                NPCBehavior.FOLLOW -> {
                    val distanceToPlayer = npc.physics.position.dst(playerPos)
                    if (distanceToPlayer > stopFollowingDistance) {
                        npc.currentState = NPCState.FOLLOWING
                        desiredMovement = playerPos.cpy().sub(npc.physics.position).nor()
                    } else {
                        npc.currentState = NPCState.IDLE
                    }
                }
                NPCBehavior.GUARD -> { // Permanently hostile
                    if (npc.physics.position.dst(playerPos) > stopProvokedChaseDistance) {
                        desiredMovement = playerPos.cpy().sub(npc.physics.position).nor()
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
