package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import kotlin.random.Random
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import java.util.*

// Data class to hold all fire-specific properties and state
data class GameFire(
    val id: String = UUID.randomUUID().toString(),
    val gameObject: GameObject,
    val animationSystem: AnimationSystem = AnimationSystem(),

    // Gameplay Properties
    val isLooping: Boolean,
    val dealsDamage: Boolean,
    val damagePerSecond: Float,
    val damageRadius: Float,
    val initialScale: Float,
    val canBeExtinguished: Boolean,
    var fadesOut: Boolean,
    var lifetime: Float,
    val canSpread: Boolean = false, // NEW: Can this fire type spread?
    val generation: Int = 0 // NEW: How many times it has spread
) {
    private val material = gameObject.modelInstance.materials.first()
    private val blendingAttribute: BlendingAttribute? = material.get(BlendingAttribute.Type) as? BlendingAttribute
    private var lastTexture: Texture? = null

    // State variables
    var currentScale: Float = initialScale
    var isBeingExtinguished: Boolean = false

    var fireSpotSpawnTimer: Float = -1f // Timer, disabled by default
    var hasSpawnedFireSpot: Boolean = false
    var spreadTimer: Float = 2.0f

    fun update(deltaTime: Float, particleSystem: ParticleSystem) {
        if (fadesOut && lifetime > 0) {
            lifetime -= deltaTime
        }

        // FADING AND EXTINGUISHING
        if (isBeingExtinguished) {
            // Shrink the fire and fade it out
            currentScale -= deltaTime * 2.0f // Shrinks over ~4 seconds (8.0 / 2.0)
            if (currentScale < 0) currentScale = 0f

            // Spawn smoke particles as it dies
            if (Random.nextFloat() < 0.2) { // 20% chance per frame to spawn smoke
                val smokeType = when(Random.nextInt(3)) {
                    0 -> ParticleEffectType.SMOKE_FRAME_1
                    1 -> ParticleEffectType.SMOKE_FRAME_2
                    else -> ParticleEffectType.SMOKE_FRAME_3
                }
                val spawnPos = gameObject.position.cpy().add(0f, currentScale * 0.5f, 0f)
                particleSystem.spawnEffect(smokeType, spawnPos)
            }
        } else if (fadesOut && lifetime <= 1.5f) { // Start fading 1.5s before expiring
            // If the fire is naturally burning out, just fade the opacity
            val fadeProgress = (lifetime / 1.5f).coerceIn(0f, 1f)
            blendingAttribute?.opacity = fadeProgress
        }

        // Apply scale to the visual model
        gameObject.modelInstance.transform.setToTranslation(gameObject.position)
        gameObject.modelInstance.transform.scale(currentScale, currentScale, currentScale)

        animationSystem.update(deltaTime)

        // Update the texture on the model if the animation frame has changed
        val newTexture = animationSystem.getCurrentTexture()
        if (newTexture != null && newTexture != lastTexture) {
            val textureAttribute = material.get(TextureAttribute.Diffuse) as TextureAttribute?
            textureAttribute?.textureDescription?.texture = newTexture
            lastTexture = newTexture
        }
    }

    fun isExpired(): Boolean = currentScale <= 0f || (fadesOut && lifetime <= 0)
}

// System to manage all fire objects
class FireSystem {
    val activeFires = Array<GameFire>()
    private lateinit var fireAnimationFrames: kotlin.Array<String>
    private lateinit var frameTextures: List<Texture>
    private lateinit var billboardModelBatch: ModelBatch
    private val renderableInstances = Array<ModelInstance>()
    private lateinit var billboardShaderProvider: BillboardShaderProvider

    // Configurable properties for the NEXT fire to be placed
    var nextFireIsLooping = true
    var nextFireDealsDamage = true
    var nextFireDamagePerSecond = 10f
    var nextFireDamageRadius = 5f
    var nextFireMinScale = 0.2f // The smallest possible fire size
    var nextFireMaxScale = 1.0f // The largest possible fire size
    var nextFireFadesOut = false
    var nextFireLifetime = 20f
    var nextFireCanBeExtinguished = true // By default, fire can be put out

    fun initialize() {
        fireAnimationFrames = arrayOf(
            "textures/particles/fire_spread/fire_spread_frame_one.png",
            "textures/particles/fire_spread/fire_spread_frame_two.png",
            "textures/particles/fire_spread/fire_spread_frame_three.png",
            "textures/particles/fire_spread/fire_spread_frame_fourth.png",
            "textures/particles/fire_spread/fire_spread_frame_five.png"
        )
        // Pre-load textures to avoid lag
        frameTextures = fireAnimationFrames.map { Texture(Gdx.files.internal(it)) }

        // Initialize the rendering tools
        billboardShaderProvider = BillboardShaderProvider().apply {
            setBillboardLightingStrength(0.9f)
            setMinLightLevel(0.4f) // Fire should be visible in the dark
        }
        billboardModelBatch = ModelBatch(billboardShaderProvider)
    }

    fun addFire(
        position: Vector3,
        objectSystem: ObjectSystem,
        lightingManager: LightingManager,
        lightIntensityOverride: Float? = null,
        lightRangeOverride: Float? = null,
        generation: Int = 0,
        canSpread: Boolean = false
    ): GameFire? {
        // If an override is provided
        val finalLightIntensity = lightIntensityOverride ?: ObjectType.FIRE_SPREAD.lightIntensity
        val finalLightRange = lightRangeOverride ?: ObjectType.FIRE_SPREAD.lightRange

        val fireObject = objectSystem.createGameObjectWithLight(ObjectType.FIRE_SPREAD, position) ?: return null
        fireObject.modelInstance.userData = "fire_effect"

        // Remove the default light that
        fireObject.associatedLightId?.let {
            lightingManager.removeLightSource(it)
            objectSystem.removeLightSource(it)
        }

        // Now create new customized light source
        val customLightSource = objectSystem.createLightSource(
            position = position.cpy().add(0f, ObjectType.FIRE_SPREAD.lightOffsetY, 0f),
            intensity = finalLightIntensity,
            range = finalLightRange,
            color = ObjectType.FIRE_SPREAD.getLightColor()
        )
        // Associate this new light
        fireObject.associatedLightId = customLightSource.id

        val lightInstances = objectSystem.createLightSourceInstances(customLightSource)
        lightingManager.addLightSource(customLightSource, lightInstances)

        val randomScale = nextFireMinScale + Random.nextFloat() * (nextFireMaxScale - nextFireMinScale)

        val newFire = GameFire(
            gameObject = fireObject,
            isLooping = nextFireIsLooping,
            dealsDamage = nextFireDealsDamage,
            damagePerSecond = nextFireDamagePerSecond,
            damageRadius = nextFireDamageRadius,
            initialScale = randomScale,
            canBeExtinguished = nextFireCanBeExtinguished,
            fadesOut = nextFireFadesOut,
            lifetime = nextFireLifetime,
            canSpread = canSpread,
            generation = generation
        )

        // ADDED: If this is a permanent fire, set up its fire spot timer.
        if (!newFire.fadesOut) {
            // Random time between 7 and 15 seconds
            newFire.fireSpotSpawnTimer = 7f + Random.nextFloat() * 8f
        }

        // Apply the initial scale to the transform immediately
        newFire.gameObject.modelInstance.transform.scale(randomScale, randomScale, randomScale)

        // Create the animation using our pre-loaded textures
        val animationFrames = Array(frameTextures.map { AnimationFrame(it, 0.15f) }.toTypedArray())
        val animation = Animation("fire_spread", animationFrames, nextFireIsLooping)
        newFire.animationSystem.addAnimation(animation)
        newFire.animationSystem.playAnimation("fire_spread")

        activeFires.add(newFire)
        return newFire
    }

    fun removeFire(fireToRemove: GameFire, objectSystem: ObjectSystem, lightingManager: LightingManager) {
        // remove the associated light source
        objectSystem.removeGameObjectWithLight(fireToRemove.gameObject, lightingManager)
        activeFires.removeValue(fireToRemove, true)
    }

    fun update(deltaTime: Float, playerSystem: PlayerSystem, particleSystem: ParticleSystem, sceneManager: SceneManager): List<GameFire> {
        val expiredFires = mutableListOf<GameFire>()
        val iterator = activeFires.iterator()

        while(iterator.hasNext()) {
            val fire = iterator.next()
            fire.update(deltaTime, particleSystem)

            if (fire.canSpread && !fire.isBeingExtinguished) {
                fire.spreadTimer -= deltaTime
                if (fire.spreadTimer <= 0f) {
                    fire.spreadTimer = Random.nextFloat() * 1.5f + 1.0f // Check again in 1-2.5 seconds

                    // The chance decreases with each generation.
                    // Gen 0 (Molotov): 4%, Gen 1: 2%, Gen 2: 1.3%, etc.
                    val baseSpreadChance = 0.04f
                    val spreadChance = baseSpreadChance / (fire.generation + 1)

                    if (Random.nextFloat() < spreadChance) {
                        attemptToSpread(fire, sceneManager)
                    }
                }
            }

            // Fire Spot Spawning Logic
            if (fire.fireSpotSpawnTimer > 0 && !fire.hasSpawnedFireSpot && !fire.isBeingExtinguished) {
                fire.fireSpotSpawnTimer -= deltaTime

                if (fire.fireSpotSpawnTimer <= 0) {
                    // Time to spawn the fire spot!
                    val firePosition = fire.gameObject.position
                    val groundY = sceneManager.findHighestSupportY(firePosition.x, firePosition.z, firePosition.y, 0.1f, sceneManager.game.blockSize)
                    val spotPosition = Vector3(firePosition.x, groundY + 0.1f, firePosition.z)

                    val fireVisualWidth = ObjectType.FIRE_SPREAD.width * fire.initialScale
                    val spotScale = fireVisualWidth

                    particleSystem.spawnEffect(
                        type = ParticleEffectType.FIRE_BURN_SPOT,
                        position = spotPosition,
                        surfaceNormal = Vector3.Y, // It's on the ground, so normal is up
                        gravityOverride = 0f,
                        overrideScale = spotScale // Use the correctly calculated scale
                    )

                    fire.hasSpawnedFireSpot = true
                }
            }

            // Check if fire should be extinguished
            if (fire.canBeExtinguished) {
                // TODO: Replace 'isRaining' with actual game logic for rain or water collision.
                val isRaining = false
                if (isRaining) {
                    fire.isBeingExtinguished = true
                }
            }

            if (fire.isExpired()) {
                // You will need to pass objectSystem and lightingManager here to fully remove it.
                println("Fire ${fire.id} has expired and should be removed.")
                // iterator.remove() // We can't fully remove it without the other systems.
                expiredFires.add(fire)
                continue // Skip damage logic for expired fires
            }

            // Damage logic
            if (fire.dealsDamage && !fire.isBeingExtinguished) {
                // Get the position from the GameObject, which is always correct.
                val fireDamage = fire.damagePerSecond * deltaTime
                val fireRadius = fire.damageRadius * fire.currentScale / fire.initialScale

                // Damage Player
                if (fire.gameObject.position.dst(playerSystem.getPosition()) < fireRadius) {
                    playerSystem.takeDamage(fireDamage)
                }

                // Damage Cars
                for (car in sceneManager.activeCars) {
                    if (fire.gameObject.position.dst(car.position) < fireRadius) {
                        car.takeDamage(fireDamage, DamageType.FIRE)
                    }
                }

                // Damage Enemies
                val enemyIterator = sceneManager.activeEnemies.iterator()
                while (enemyIterator.hasNext()) {
                    val enemy = enemyIterator.next()
                    if (fire.gameObject.position.dst(enemy.position) < fireRadius) {
                        if (enemy.takeDamage(fireDamage, DamageType.FIRE) && enemy.currentState != AIState.DYING) {
                            // Enemy died from fire, spawn a blood pool and remove them
                            sceneManager.enemySystem.startDeathSequence(enemy, sceneManager)
                        }
                    }
                }

                // Damage NPCs
                val npcIterator = sceneManager.activeNPCs.iterator()
                while (npcIterator.hasNext()) {
                    val npc = npcIterator.next()
                    if (fire.gameObject.position.dst(npc.position) < fireRadius) {
                        if (npc.takeDamage(fireDamage, DamageType.FIRE) && npc.currentState != NPCState.DYING) {
                            // NPC died from fire, spawn a blood pool and remove them
                            sceneManager.npcSystem.startDeathSequence(npc, sceneManager)
                        }
                    }
                }
            }
        }
        return expiredFires
    }

    private fun attemptToSpread(sourceFire: GameFire, sceneManager: SceneManager) {
        val blockSize = sceneManager.game.blockSize
        val offsets = listOf(
            Vector3(blockSize, 0f, 0f), Vector3(-blockSize, 0f, 0f),
            Vector3(0f, 0f, blockSize), Vector3(0f, 0f, -blockSize)
            // Not spreading up/down for now to keep it simple and on the ground
        )

        val direction = offsets.random()
        val potentialPosition = sourceFire.gameObject.position.cpy().add(direction)

        // Check if the new position is valid (not inside a solid block)
        if (sceneManager.isPositionValidForFire(potentialPosition)) {
            println("Fire is spreading from generation ${sourceFire.generation}!")

            // Configure the properties for the new fire
            nextFireFadesOut = true
            nextFireLifetime = 8f + Random.nextFloat() * 7f // Lasts 8-15 seconds
            nextFireMinScale = 0.4f
            nextFireMaxScale = 0.7f

            // Add the new fire
            addFire(
                position = potentialPosition,
                objectSystem = sceneManager.game.objectSystem,
                lightingManager = sceneManager.game.lightingManager,
                generation = sourceFire.generation + 1, // Crucial: increment generation
                canSpread = true, // The new fire can also spread
                lightIntensityOverride = 30f,
                lightRangeOverride = 12f
            )
        }
    }

    fun render(camera: Camera, environment: Environment) {
        if (activeFires.isEmpty) return

        billboardShaderProvider.setEnvironment(environment)
        billboardModelBatch.begin(camera)
        renderableInstances.clear()

        for (fire in activeFires) {
            renderableInstances.add(fire.gameObject.modelInstance)
        }
        billboardModelBatch.render(renderableInstances, environment)
        billboardModelBatch.end()
    }

    fun dispose() {
        frameTextures.forEach { it.dispose() }
        // ADDED: Dispose of the new rendering tools
        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()
    }
}
