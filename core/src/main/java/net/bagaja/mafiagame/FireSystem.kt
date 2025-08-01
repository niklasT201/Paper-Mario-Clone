package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
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
    var lifetime: Float
) {
    private val material = gameObject.modelInstance.materials.first()
    private val blendingAttribute: BlendingAttribute? = material.get(BlendingAttribute.Type) as? BlendingAttribute
    private var lastTexture: Texture? = null

    // State variables
    var currentScale: Float = initialScale
    var isBeingExtinguished: Boolean = false

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

    fun addFire(position: Vector3, objectSystem: ObjectSystem, lightingManager: LightingManager): GameFire? {
        val fireObject = objectSystem.createGameObjectWithLight(ObjectType.FIRE_SPREAD, position, lightingManager) ?: return null
        fireObject.modelInstance.userData = "player"

        // Calculate the random scale
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
            lifetime = nextFireLifetime
        )

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
            }

            // Damage logic
            if (fire.dealsDamage && !fire.isBeingExtinguished) {
                // Get the position from the GameObject, which is always correct.
                val fireDamage = fire.damagePerSecond * deltaTime
                val fireRadius = fire.damageRadius * fire.currentScale / fire.initialScale

                // Damage Player
                val distanceToPlayer = fire.gameObject.position.dst(playerSystem.getPosition())
                if (distanceToPlayer < fireRadius) {
                    playerSystem.takeDamage(fireDamage)
                }

                // Damage Cars
                for (car in sceneManager.activeCars) {
                    val distanceToCar = fire.gameObject.position.dst(car.position)
                    if (distanceToCar < fireRadius) {
                        car.takeDamage(fireDamage)
                    }
                }
            }
        }
        return expiredFires
    }

    fun render(camera: Camera, environment: Environment) {
        if (activeFires.isEmpty) return

        billboardShaderProvider.setEnvironment(environment)
        billboardModelBatch.begin(camera)
        for (fire in activeFires) {
            // The fire's update logic already sets its transform correctly
            billboardModelBatch.render(fire.gameObject.modelInstance, environment)
        }
        billboardModelBatch.end()
    }

    fun dispose() {
        frameTextures.forEach { it.dispose() }
        // ADDED: Dispose of the new rendering tools
        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()
    }
}
