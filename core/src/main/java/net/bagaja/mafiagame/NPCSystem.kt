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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

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
    FRED_THE_HERMIT("Fred the Hermit", "textures/characters/fred_hermit.png", 4f, 5f, 100f, 4.5f),
    MR_QUESTMARK("Mr. Questmark", "textures/characters/Mr_Questmark.png", 4.0f, 5f, 100f, 5.0f),
    NUN("Nun", "textures/characters/nun.png", 4f, 5f, 70f, 5.5f),
    SINGER("Singer", "textures/characters/singer.png", 5f, 7f, 80f, 6.0f),
    ARMADILLO("Armadillo", "textures/characters/armadillo.png", 4f, 5f, 120f, 5.5f),
    BEAR("Bear", "textures/characters/bear.png", 4.5f, 6f, 120f, 5.5f),
    DODO("Dodo", "textures/characters/dodo.png", 3.4f, 4.5f, 120f, 5.5f),
    FROG("Frog", "textures/characters/frog.png", 3.4f, 4.5f, 120f, 5.5f),
    GOOSE("Goose", "textures/characters/goose.png", 4.5f, 6f, 120f, 5.5f),
    LIZARD("Lizard", "textures/characters/lizard.png", 3.4f, 4.5f, 120f, 5.5f),
    MOTH("Moth", "textures/characters/moth.png", 3.4f, 4.5f, 120f, 5.5f),
    RED_PANDA("Red Panda", "textures/characters/red_panda.png", 4f, 5f, 120f, 5.5f),
    SNAKE("Snake", "textures/characters/snake.png", 4f, 5f, 120f, 5.5f),
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
    COOLDOWN            // Temp state after hostility before returning to normal
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
    // AI state properties
    var currentState: NPCState = NPCState.IDLE
    var stateTimer: Float = 0f
    var targetPosition: Vector3? = null
    var facingRotationY: Float = 0f
    var homePosition: Vector3 = position.cpy() // For wandering radius

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

    fun updateVisuals() {
        modelInstance.transform.idt()
        modelInstance.transform.setTranslation(position)
        modelInstance.transform.rotate(Vector3.Y, facingRotationY)
        modelInstance.transform.rotate(Vector3.Z, wobbleAngle) // Rotate on Z-axis for the wobble
    }

    fun getBoundingBox(): BoundingBox {
        boundingBox.set(
            Vector3(position.x - boundsSize.x / 2, position.y - npcType.height / 2, position.z - boundsSize.z / 2),
            Vector3(position.x + boundsSize.x / 2, position.y + npcType.height / 2, position.z + boundsSize.z / 2)
        )
        return boundingBox
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

    fun takeDamage(damage: Float): Boolean {
        // If NPC is already a Guard or Provoked, they just take damage and stay mad.
        if (behaviorType == NPCBehavior.GUARD || currentState == NPCState.PROVOKED) {
            health -= damage
            println("Hostile NPC ${npcType.displayName} took $damage damage. HP: $health")
        } else {
            // For neutral NPCs, hitting them increases provocation.
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

        // Return true if the NPC should be removed from the game
        return health <= 0
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
    private val npcModels = mutableMapOf<NPCType, Model>()
    private val npcTextures = mutableMapOf<NPCType, Texture>()
    private lateinit var billboardModelBatch: ModelBatch
    private lateinit var billboardShaderProvider: BillboardShaderProvider
    private val WOBBLE_AMPLITUDE_DEGREES = 5f // How far it tilts left/right. 10 degrees is a good start.
    private val WOBBLE_FREQUENCY = 6f

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

    fun initialize() {
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

    fun createNPC(position: Vector3, npcType: NPCType, behavior: NPCBehavior, initialRotation: Float = 0f): GameNPC? {
        val model = npcModels[npcType] ?: return null
        val instance = ModelInstance(model)
        instance.userData = "player"

        val newNpc = GameNPC(
            modelInstance = instance,
            npcType = npcType,
            behaviorType = behavior,
            position = position.cpy()
        )

        // If an initial rotation is provided
        newNpc.facingRotationY = if (initialRotation != 0f) initialRotation else this.currentRotation

        // Immediately update the model's transform
        newNpc.updateVisuals()

        return newNpc
    }

    fun update(deltaTime: Float, playerSystem: PlayerSystem, sceneManager: SceneManager, blockSize: Float) {
        if (playerSystem.isDriving) return

        val playerPos = playerSystem.getPosition()

        for (npc in sceneManager.activeNPCs) {
            // Reset moving flag
            npc.isMoving = false
            // ACTIVATION CHECK
            val distanceToPlayer = npc.position.dst(playerPos)
            if (distanceToPlayer > activationRange) {
                // This NPC is too far away to matter
                if (npc.currentState == NPCState.WANDERING || npc.currentState == NPCState.FOLLOWING) {
                    npc.currentState = NPCState.IDLE
                    npc.targetPosition = null
                }
                continue
            }

            if (!finePosMode) {
                applyPhysics(npc, deltaTime, sceneManager, blockSize)
            }
            npc.decayProvocation(deltaTime)
            updateAI(npc, playerPos, deltaTime, sceneManager)

            if (npc.isMoving) {
                // If moving, advance animation timer
                npc.walkAnimationTimer += deltaTime
                // Calculate wobble angle
                val angleRad = sin(npc.walkAnimationTimer * WOBBLE_FREQUENCY)
                npc.wobbleAngle = angleRad * WOBBLE_AMPLITUDE_DEGREES
            } else {
                // If not moving, smoothly return to an upright position
                if (kotlin.math.abs(npc.wobbleAngle) > 0.1f) {
                    // Interpolate back to 0. The '10f' is the speed of return.
                    npc.wobbleAngle *= (1.0f - deltaTime * 10f).coerceAtLeast(0f)
                } else {
                    npc.wobbleAngle = 0f // Snap to 0 if very close
                }
                // Reset the timer when not moving
                npc.walkAnimationTimer = 0f
            }

            val shouldAutoRotate = npc.behaviorType != NPCBehavior.STATIONARY || npc.currentState == NPCState.PROVOKED

            if (shouldAutoRotate) {
                val targetForFacing = when {
                    npc.currentState == NPCState.PROVOKED -> playerPos
                    npc.behaviorType == NPCBehavior.GUARD -> playerPos
                    npc.behaviorType == NPCBehavior.WATCHER -> playerPos
                    npc.currentState == NPCState.FOLLOWING -> playerPos
                    else -> npc.targetPosition
                }

                if (targetForFacing != null) {
                    // This logic now only applies to non-stationary NPCs
                    if (targetForFacing.x > npc.position.x) npc.facingRotationY = 0f // Face right
                    else npc.facingRotationY = 180f // Face left
                }
            }

            npc.updateVisuals()
        }
    }

    fun toggleRotation() {
        currentRotation = if (currentRotation == 0f) 180f else 0f
    }

    private fun updateAI(npc: GameNPC, playerPos: Vector3, deltaTime: Float, sceneManager: SceneManager) {
        // Hostility Overrides
        if (npc.currentState == NPCState.PROVOKED) {
            npc.stateTimer += deltaTime
            if (npc.stateTimer > hostileCooldownTime || npc.position.dst(playerPos) > provokedChaseDistance) {
                npc.currentState = NPCState.COOLDOWN
                npc.provocationLevel = 0f // Forgiven!
                npc.stateTimer = 0f
            } else {
                if (npc.position.dst(playerPos) > stopProvokedChaseDistance) {
                    moveTowards(npc, playerPos, deltaTime, sceneManager)
                }
            }
            return // Skip normal behaviors
        }

        // A brief period after being hostile where the NPC does nothing.
        if (npc.currentState == NPCState.COOLDOWN) {
            npc.stateTimer += deltaTime
            if (npc.stateTimer > 5f) { // 5 second cooldown
                npc.currentState = NPCState.IDLE
                npc.stateTimer = 0f
            }
            return // Skip normal behaviors
        }

        // Standard Behaviors
        when (npc.behaviorType) {
            NPCBehavior.STATIONARY -> npc.currentState = NPCState.IDLE
            NPCBehavior.WATCHER -> npc.currentState = NPCState.IDLE
            NPCBehavior.WANDER -> updateWanderAI(npc, deltaTime, sceneManager)
            NPCBehavior.FOLLOW -> {
                val distanceToPlayer = npc.position.dst(playerPos)
                if (distanceToPlayer > playerFollowDistance) {
                    npc.currentState = NPCState.FOLLOWING
                    moveTowards(npc, playerPos, deltaTime, sceneManager)
                } else if (distanceToPlayer < stopFollowingDistance) {
                    npc.currentState = NPCState.IDLE
                } else if (npc.currentState == NPCState.FOLLOWING) {
                    moveTowards(npc, playerPos, deltaTime, sceneManager)
                }
            }
            NPCBehavior.GUARD -> { // This is a permanently hostile NPC
                if (npc.position.dst(playerPos) > stopProvokedChaseDistance) {
                    moveTowards(npc, playerPos, deltaTime, sceneManager)
                }
            }
        }
    }

    private fun updateWanderAI(npc: GameNPC, deltaTime: Float, sceneManager: SceneManager) {
        when (npc.currentState) {
            NPCState.IDLE -> {
                npc.stateTimer += deltaTime
                if (npc.stateTimer > wanderPauseTime) {
                    val randomAngle = (Random().nextFloat() * 2 * Math.PI).toFloat()
                    val randomDist = Random().nextFloat() * wanderRadius
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

    // The following methods are adapted from your EnemySystem
    private fun applyPhysics(npc: GameNPC, deltaTime: Float, sceneManager: SceneManager, blockSize: Float) {
        val npcFootY = npc.position.y - (npc.npcType.height / 2f)
        val supportY = sceneManager.findHighestSupportY(npc.position.x, npc.position.z, npcFootY, npc.npcType.width / 2f, blockSize)
        val effectiveSupportY = if (supportY - npcFootY <= MAX_STEP_HEIGHT) supportY else npcFootY
        val targetY = effectiveSupportY + (npc.npcType.height / 2f)
        val fallY = npc.position.y - FALL_SPEED * deltaTime

        val newY = max(targetY, fallY)
        val tolerance = 0.01f // A small dead zone

        // Only apply the change if it's significant enough to avoid jitter
        if (kotlin.math.abs(npc.position.y - newY) > tolerance) {
            npc.position.y = newY
        }
    }

    private fun moveTowards(npc: GameNPC, target: Vector3, deltaTime: Float, sceneManager: SceneManager) {
        val direction = Vector3(target).sub(npc.position).nor()
        val delta = direction.scl(npc.npcType.speed * deltaTime)
        val nextPos = Vector3(npc.position).add(delta)
        if (canNpcMoveTo(nextPos, npc, sceneManager)) {
            npc.position.set(nextPos)
            npc.isMoving = true
        }
    }

    private fun canNpcMoveTo(newPosition: Vector3, npc: GameNPC, sceneManager: SceneManager): Boolean {
        val npcBounds = npc.getBoundingBox()
        npcBounds.set(
            npcBounds.min.set(newPosition.x - npc.npcType.width / 2f, newPosition.y - npc.npcType.height / 2f, newPosition.z - npc.npcType.width / 2f),
            npcBounds.max.set(newPosition.x + npc.npcType.width / 2f, newPosition.y + npc.npcType.height / 2f, newPosition.z + npc.npcType.width / 2f)
        )
        sceneManager.activeBlocks.forEach { block ->
            // Use the block's accurate collision check
            if (block.collidesWith(npcBounds)) return false
        }
        sceneManager.activeHouses.forEach {
            if (it.collidesWithMesh(npcBounds)) return false
        }
        return true
    }

    fun renderNPCs(camera: Camera, environment: Environment, npcs: Array<GameNPC>) {
        billboardShaderProvider.setEnvironment(environment)

        billboardModelBatch.begin(camera)
        for (npc in npcs) {
            billboardModelBatch.render(npc.modelInstance, environment)
        }
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
