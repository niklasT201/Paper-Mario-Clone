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
    val canSpread: Boolean = false,
    val generation: Int = 0,
    var missionId: String? = null,
    @Transient var soundInstanceId: Long? = null
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

    // --- MODIFIED ---: The update method now accepts the LightingManager
    fun update(deltaTime: Float, particleSystem: ParticleSystem, lightingManager: LightingManager) {
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

        // --- NEW LOGIC: DYNAMIC LIGHT UPDATE ---
        gameObject.associatedLightId?.let { lightId ->
            lightingManager.getLightSources()[lightId]?.let { lightSource ->
                // Calculate the ratio of the current size to its starting size
                if (initialScale > 0.01f) {
                    val scaleRatio = (currentScale / initialScale).coerceIn(0f, 2f) // Coerce to prevent insane values

                    // Update intensity and range based on the scale ratio
                    lightSource.intensity = gameObject.objectType.lightIntensity * scaleRatio
                    lightSource.range = gameObject.objectType.lightRange * scaleRatio

                    // IMPORTANT: Push the changes to the renderable point light
                    lightSource.updatePointLight()
                }
            }
        }
        // --- END OF NEW LOGIC ---

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
    lateinit var sceneManager: SceneManager
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

    companion object {
        const val ON_FIRE_BASE_DPS = 20f
        const val ON_FIRE_MIN_DURATION = 3.0f
        const val ON_FIRE_MAX_DURATION = 6.0f
    }

    private val fireSoundIds = listOf(
        "FIRE_BURNING_V1", "FIRE_BURNING_V2", "FIRE_BURNING_V3", "FIRE_BURNING_V4", "FIRE_BURNING_V5",
        "FIRE_CRACKLE_V1", "FIRE_CRACKLE_V2", "FIRE_CRACKLE_V3"
    )

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
        canSpread: Boolean = false,
        id: String = UUID.randomUUID().toString(),
        existingAssociatedLightId: Int? = null
    ): GameFire? {
        // If an override is provided
        val finalLightIntensity = lightIntensityOverride ?: ObjectType.FIRE_SPREAD.lightIntensity
        val finalLightRange = lightRangeOverride ?: ObjectType.FIRE_SPREAD.lightRange

        val fireObject = objectSystem.createGameObjectWithLight(
            ObjectType.FIRE_SPREAD,
            position,
            lightingManager,
            existingAssociatedLightId // Pass the ID here
        ) ?: return null

        fireObject.modelInstance.userData = "fire_effect"
        fireObject.id = id // Assign the ID here

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
        customLightSource.parentObjectId = fireObject.id
        fireObject.associatedLightId = customLightSource.id

        val lightInstances = objectSystem.createLightSourceInstances(customLightSource)
        lightingManager.addLightSource(customLightSource, lightInstances)

        val randomScale = nextFireMinScale + Random.nextFloat() * (nextFireMaxScale - nextFireMinScale)
        val effectiveDamageRadius = (fireObject.objectType.width / 2f) * 1.1f

        val newFire = GameFire(
            id = id,
            gameObject = fireObject,
            isLooping = nextFireIsLooping,
            dealsDamage = nextFireDealsDamage,
            damagePerSecond = nextFireDamagePerSecond,
            damageRadius = effectiveDamageRadius,
            initialScale = randomScale,
            canBeExtinguished = nextFireCanBeExtinguished,
            fadesOut = nextFireFadesOut,
            lifetime = nextFireLifetime,
            canSpread = canSpread,
            generation = generation
        )

        // If this is a permanent fire, set up its fire spot timer.
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

        // Play a random looping fire sound
        fireSoundIds.randomOrNull()?.let { randomSoundId ->
            val soundId = sceneManager.game.soundManager.playSound(
                id = randomSoundId,
                position = newFire.gameObject.position,
                loop = true,
                reverbProfile = SoundManager.DEFAULT_REVERB,
                maxRange = 40f // Fire sound doesn't need to travel across the entire map
            )
            // Store the unique instance ID on the fire object
            newFire.soundInstanceId = soundId
        }

        return newFire
    }

    fun removeFire(fireToRemove: GameFire, objectSystem: ObjectSystem, lightingManager: LightingManager) {
        // Stop the looping sound
        fireToRemove.soundInstanceId?.let { soundId ->
            sceneManager.game.soundManager.stopLoopingSound(soundId)
        }

        // remove the associated light source
        objectSystem.removeGameObjectWithLight(fireToRemove.gameObject, lightingManager)
        activeFires.removeValue(fireToRemove, true)
    }

    fun update(deltaTime: Float, playerSystem: PlayerSystem, particleSystem: ParticleSystem, sceneManager: SceneManager, weatherSystem: WeatherSystem, isInInterior: Boolean): List<GameFire> {
        val expiredFires = mutableListOf<GameFire>()
        val iterator = activeFires.iterator()

        val visualRainIntensity = weatherSystem.getVisualRainIntensity()
        val isRainingVisually = visualRainIntensity > 0.01f // Only extinguish in medium-to-heavy rain

        while(iterator.hasNext()) {
            val fire = iterator.next()
            fire.update(deltaTime, particleSystem, sceneManager.game.lightingManager)

            fire.soundInstanceId?.let { soundId ->
                if (fire.initialScale > 0.01f) {
                    // Calculate a volume scale based on the fire's current size vs its original size
                    val scaleRatio = fire.currentScale / fire.initialScale
                    sceneManager.game.soundManager.setLoopingSoundVolumeMultiplier(soundId, scaleRatio)
                }
            }

            if (fire.canBeExtinguished && isRainingVisually && !isInInterior) {
                // --- RAIN INTERACTION LOGIC (Sputtering & Shrinking) ---
                if (Random.nextFloat() < (0.5f * visualRainIntensity * fire.currentScale)) {
                    val steamPosition = fire.gameObject.position.cpy().add(
                        (Random.nextFloat() - 0.5f) * (fire.gameObject.objectType.width * fire.currentScale * 0.5f),
                        fire.gameObject.objectType.height * fire.currentScale * 0.7f,
                        (Random.nextFloat() - 0.5f) * (fire.gameObject.objectType.width * fire.currentScale * 0.5f)
                    )
                    particleSystem.spawnEffect(ParticleEffectType.SMOKE_FRAME_1, steamPosition)
                }

                val component = fire.gameObject.washAwayComponent
                component.timer -= deltaTime
                if (component.timer <= 0) {
                    when (component.state) {
                        WashAwayState.IDLE -> {
                            component.state = WashAwayState.SHRINKING
                            component.timer = Random.nextFloat() * 1.5f + 0.5f // Shrink for 0.5-2 seconds
                        }
                        WashAwayState.SHRINKING -> {
                            component.state = WashAwayState.IDLE
                            component.timer = Random.nextFloat() * 4f + 2f // Pause for 2-6 seconds
                        }
                    }
                }

                if (component.state == WashAwayState.SHRINKING) {
                    val shrinkRate = 0.8f * visualRainIntensity
                    fire.currentScale -= shrinkRate * deltaTime
                }
            } else {
                // --- FIRE RECOVERY LOGIC ---
                if (fire.currentScale < fire.initialScale) {
                    val recoveryRate = 0.2f
                    fire.currentScale += recoveryRate * deltaTime
                    if (fire.currentScale > fire.initialScale) {
                        fire.currentScale = fire.initialScale
                    }
                }
                fire.gameObject.washAwayComponent.state = WashAwayState.IDLE
            }

            // --- FIRE SPREAD LOGIC ---
            if (fire.canSpread && !fire.isBeingExtinguished) {
                fire.spreadTimer -= deltaTime
                if (fire.spreadTimer <= 0f) {
                    fire.spreadTimer = Random.nextFloat() * 1.5f + 1.0f
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
                        overrideScale = spotScale
                    )

                    fire.hasSpawnedFireSpot = true
                }
            }

            // Check if fire should be extinguished
            if (fire.isExpired()) {
                println("Fire ${fire.id} has expired and should be removed.")
                expiredFires.add(fire)
                continue // Skip damage logic for expired fires
            }

            // Damage logic
            if (fire.dealsDamage && !fire.isBeingExtinguished) {
                // Get the position from the GameObject, which is always correct.
                val baseDamagePerSecond = fire.damagePerSecond
                val fireRadius = fire.damageRadius * (fire.currentScale / fire.initialScale)

                // Damage Player
                val playerPosition = playerSystem.getPosition()
                val distanceToPlayer = fire.gameObject.position.dst(playerPosition)
                if (distanceToPlayer < fireRadius) {
                    val damageMultiplier = 1.0f - (distanceToPlayer / fireRadius)
                    val directDamageThisFrame = baseDamagePerSecond * damageMultiplier * deltaTime
                    playerSystem.takeDamage(directDamageThisFrame)

                    // 2. Set the player on fire
                    val fireDuration = Random.nextFloat() * (ON_FIRE_MAX_DURATION - ON_FIRE_MIN_DURATION) + ON_FIRE_MIN_DURATION
                    playerSystem.setOnFire(fireDuration, ON_FIRE_BASE_DPS)
                }

                // Damage Cars
                for (car in sceneManager.activeCars) {
                    val distanceToCar = fire.gameObject.position.dst(car.position)
                    if (distanceToCar < fireRadius) {
                        val damageMultiplier = 1.0f - (distanceToCar / fireRadius)
                        val directDamageThisFrame = baseDamagePerSecond * damageMultiplier * deltaTime
                        car.takeDamage(directDamageThisFrame, DamageType.FIRE)
                    }
                }

                // Damage Enemies
                sceneManager.activeEnemies.forEach { enemy ->
                    val distanceToEnemy = fire.gameObject.position.dst(enemy.position)
                    if (distanceToEnemy < fireRadius) {
                        val damageMultiplier = 1.0f - (distanceToEnemy / fireRadius)
                        val directDamageThisFrame = baseDamagePerSecond * damageMultiplier * deltaTime
                        if (enemy.takeDamage(directDamageThisFrame, DamageType.FIRE, sceneManager) && enemy.currentState != AIState.DYING) {
                            // Enemy died from fire, spawn a blood pool and remove them
                            sceneManager.enemySystem.startDeathSequence(enemy, sceneManager)
                        }

                        // 2. Set the enemy on fire
                        val fireDuration = Random.nextFloat() * (ON_FIRE_MAX_DURATION - ON_FIRE_MIN_DURATION) + ON_FIRE_MIN_DURATION
                        sceneManager.enemySystem.setOnFire(enemy, fireDuration, ON_FIRE_BASE_DPS)
                    }
                }

                // Damage NPCs
                sceneManager.activeNPCs.forEach { npc ->
                    val distanceToNPC = fire.gameObject.position.dst(npc.position)
                    if (distanceToNPC < fireRadius) {
                        val damageMultiplier = 1.0f - (distanceToNPC / fireRadius)
                        val directDamageThisFrame = baseDamagePerSecond * damageMultiplier * deltaTime
                        if (npc.takeDamage(directDamageThisFrame, DamageType.FIRE, sceneManager) && npc.currentState != NPCState.DYING) {
                            // NPC died from fire, spawn a blood pool and remove them
                            sceneManager.npcSystem.startDeathSequence(npc, sceneManager)
                        }

                        // 2. Set the NPC on fire
                        val fireDuration = Random.nextFloat() * (ON_FIRE_MAX_DURATION - ON_FIRE_MIN_DURATION) + ON_FIRE_MIN_DURATION
                        sceneManager.npcSystem.setOnFire(npc, fireDuration, ON_FIRE_BASE_DPS)
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
