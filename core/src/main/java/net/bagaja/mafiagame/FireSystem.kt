package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
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
    val staysBurning: Boolean,
    val dealsDamage: Boolean,
    val damagePerSecond: Float,
    val damageRadius: Float
) {
    private var lifetime: Float = if (staysBurning) -1f else animationSystem.currentAnimation?.getTotalDuration() ?: 1f
    private val material = gameObject.modelInstance.materials.first()
    private var lastTexture: Texture? = null

    fun update(deltaTime: Float) {
        if (!staysBurning) {
            lifetime -= deltaTime
        }

        animationSystem.update(deltaTime)

        // Update the texture on the model if the animation frame has changed
        val newTexture = animationSystem.getCurrentTexture()
        if (newTexture != null && newTexture != lastTexture) {
            val textureAttribute = material.get(TextureAttribute.Diffuse) as TextureAttribute?
            textureAttribute?.textureDescription?.texture = newTexture
            lastTexture = newTexture
        }
    }

    fun isExpired(): Boolean = !staysBurning && lifetime <= 0f
}

// System to manage all fire objects
class FireSystem {
    val activeFires = Array<GameFire>()
    private lateinit var fireAnimationFrames: kotlin.Array<String>
    private lateinit var frameTextures: List<Texture>

    // Configurable properties for the NEXT fire to be placed
    var nextFireIsLooping = true
    var nextFireStaysBurning = true
    var nextFireDealsDamage = true
    var nextFireDamagePerSecond = 10f
    var nextFireDamageRadius = 5f

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
    }

    fun addFire(position: Vector3, objectSystem: ObjectSystem, lightingManager: LightingManager): GameFire? {
        val fireObject = objectSystem.createGameObjectWithLight(ObjectType.FIRE_SPREAD, position, lightingManager) ?: return null

        val newFire = GameFire(
            gameObject = fireObject,
            isLooping = nextFireIsLooping,
            staysBurning = nextFireStaysBurning,
            dealsDamage = nextFireDealsDamage,
            damagePerSecond = nextFireDamagePerSecond,
            damageRadius = nextFireDamageRadius
        )

        // Create the animation using our pre-loaded textures
        val animationFrames = Array(frameTextures.map { AnimationFrame(it, 0.15f) }.toTypedArray())
        val animation = Animation("fire_spread", animationFrames, nextFireIsLooping)
        newFire.animationSystem.addAnimation(animation)
        newFire.animationSystem.playAnimation("fire_spread")

        activeFires.add(newFire)
        return newFire
    }

    fun removeFire(fireToRemove: GameFire, objectSystem: ObjectSystem, lightingManager: LightingManager) {
        // Important: remove the associated light source!
        objectSystem.removeGameObjectWithLight(fireToRemove.gameObject, lightingManager)
        activeFires.removeValue(fireToRemove, true)
    }

    fun update(deltaTime: Float, playerSystem: PlayerSystem) {
        val iterator = activeFires.iterator()
        while(iterator.hasNext()) {
            val fire = iterator.next()
            fire.update(deltaTime)

            if (fire.isExpired()) {
                // This fire has burned out, but we need the main game to handle removal
                // because it has references to the object/lighting systems.
                // For now, we'll just let it fade. The object itself will be removed when right-clicked.
            }

            // Damage logic
            if (fire.dealsDamage) {
                // --- THIS IS THE CORRECTED LINE ---
                // Get the position from the GameObject, which is always correct.
                val distanceToPlayer = fire.gameObject.position.dst(playerSystem.getPosition())
                if (distanceToPlayer < fire.damageRadius) {
                    // You may need to create a takeDamage method on your PlayerSystem
                    // playerSystem.takeDamage(fire.damagePerSecond * deltaTime)
                }
            }
        }
    }

    fun dispose() {
        frameTextures.forEach { it.dispose() }
    }
}
