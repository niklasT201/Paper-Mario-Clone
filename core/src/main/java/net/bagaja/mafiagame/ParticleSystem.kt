package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import kotlin.random.Random

/**
 * Defines the properties of a particle effect, now matching your asset library.
 */
enum class ParticleEffectType(
    val displayName: String,
    val texturePaths: kotlin.Array<String>,
    val frameDuration: Float,
    val isLooping: Boolean,
    val particleLifetime: Float,
    val lifetimeVariance: Float = 0f, // How much to randomize lifetime
    val swingChance: Float = 0f, // Chance that a particle will swing
    val swingAmplitude: Float = 15f, // How far it swings in degrees
    val swingAmplitudeVariance: Float = 0f,
    val swingFrequency: Float = 2f, // How many full swings per second
    val swingFrequencyVariance: Float = 0f,
    val particleCount: IntRange,
    val initialSpeed: Float,
    val speedVariance: Float,
    val gravity: Float,
    val scale: Float,
    val scaleVariance: Float,
    val sizeRandomnessChance: Float = 1.0f,
    val fadeIn: Float = 0.1f, // Time to fade in
    val fadeOut: Float = 0.5f  // Time to fade out
) {
    BLOOD_SPLATTER_1(
        "Blood Splatter 1",
        arrayOf("textures/particles/blood/blood_frame.png"), // Single image
        frameDuration = 0.1f, isLooping = false, particleLifetime = 10.0f, // Longer lifetime for splatters
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = -0.1f, // Sticks to ground
        scale = 1.2f, scaleVariance = 0.2f, fadeIn = 0.1f, fadeOut = 2.0f
    ),
    BLOOD_SPLATTER_2(
        "Blood Splatter 2",
        arrayOf("textures/particles/blood/blood_frame_2.png"), // Single image
        frameDuration = 0.1f, isLooping = false, particleLifetime = 10.0f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = -0.1f,
        scale = 1.3f, scaleVariance = 0.2f, fadeIn = 0.1f, fadeOut = 2.0f
    ),
    BLOOD_SPLATTER_3(
        "Blood Splatter 3",
        arrayOf("textures/particles/blood/blood_frame_3.png"), // Single image
        frameDuration = 0.1f, isLooping = false, particleLifetime = 10.0f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = -0.1f,
        scale = 1.1f, scaleVariance = 0.2f, fadeIn = 0.1f, fadeOut = 2.0f
    ),
    BLOOD_DRIP(
        "Blood Drip",
        arrayOf("textures/particles/blood/blood_drops.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 2.0f,
        particleCount = 5..10, initialSpeed = 5f, speedVariance = 3f, gravity = -20f,
        scale = 0.5f, scaleVariance = 0.2f
    ),
    DUST_CLOUD(
        "Dust Cloud",
        arrayOf(
            "textures/particles/dust/smoke_1.png",
            "textures/particles/dust/smoke_3.png" // Skips frame 2 as requested
        ),
        frameDuration = 0.15f, isLooping = false, particleLifetime = 1.5f,
        particleCount = 2..4, initialSpeed = 1.5f, speedVariance = 1f, gravity = 0.5f,
        scale = 1.0f, scaleVariance = 0.5f, fadeOut = 1.0f
    ),
    DUST_IMPACT(
        "Dust Impact",
        arrayOf("textures/particles/dust/smoke_2.png"), // Uses only frame 2 as a static image
        frameDuration = 0.1f, isLooping = false, particleLifetime = 2.0f,
        particleCount = 1..1, initialSpeed = 0.2f, speedVariance = 0.1f, gravity = 0.2f, // Slower, hangs in air
        scale = 0.9f, scaleVariance = 0.3f, fadeOut = 1.8f
    ),
    GUN_SMOKE_PLUME(
        "Gun Smoke Plume",
        arrayOf(
            "textures/particles/gun_smoke/gun_smoke.png",
            "textures/particles/gun_smoke/gun_smoke_2.png",
            "textures/particles/gun_smoke/gun_smoke_4.png",
            "textures/particles/gun_smoke/gun_smoke_6.png",
            "textures/particles/gun_smoke/gun_smoke_7.png",
            "textures/particles/gun_smoke/gun_smoke_8.png"
        ),
        frameDuration = 0.08f, isLooping = false, particleLifetime = 1.2f,
        particleCount = 1..1, initialSpeed = 6f, speedVariance = 2f, gravity = 2f,
        scale = 1.5f, scaleVariance = 0.3f, fadeIn = 0.0f, fadeOut = 0.5f
    ),
    GUN_SMOKE_BURST(
        "Gun Smoke Burst",
        arrayOf(
            "textures/particles/gun_smoke/gun_smoke_1.png",
            "textures/particles/gun_smoke/gun_smoke_3.png",
            "textures/particles/gun_smoke/gun_smoke_5.png",
            "textures/particles/gun_smoke/gun_smoke_6.png"
        ),
        frameDuration = 0.05f, isLooping = false, particleLifetime = 0.5f,
        particleCount = 1..1, initialSpeed = 10f, speedVariance = 1f, gravity = 1f,
        scale = 1.2f, scaleVariance = 0.2f, fadeIn = 0.0f, fadeOut = 0.25f
    ),
    RISING_SMOKE(
        "Rising Smoke",
        arrayOf(
            "textures/particles/snoke/smoke_frame.png",
            "textures/particles/snoke/smoke_frame_3.png",
            "textures/particles/snoke/smoke_frame_5.png",
            "textures/particles/snoke/smoke_frame_7.png",
            "textures/particles/snoke/smoke_frame_9.png"
        ),
        frameDuration = 0.18f, // Slowed down for a smooth, rising feel
        isLooping = true,      // Smoke should loop
        particleLifetime = 4.0f,
        particleCount = 1..1, initialSpeed = 1f, speedVariance = 0.5f, gravity = 1.5f, // Rises gently
        scale = 2.0f, scaleVariance = 0.5f, fadeIn = 0.5f, fadeOut = 2.0f
    ),
    PUFFING_SMOKE(
        "Puffing Smoke",
        arrayOf(
            "textures/particles/snoke/smoke_frame_2.png",
            "textures/particles/snoke/smoke_frame_4.png",
            "textures/particles/snoke/smoke_frame_6.png",
            "textures/particles/snoke/smoke_frame_8.png"
        ),
        frameDuration = 0.15f, // Slightly faster to feel like "puffs"
        isLooping = true,
        particleLifetime = 3.5f,
        particleCount = 1..1, initialSpeed = 1.2f, speedVariance = 0.6f, gravity = 1.8f,
        scale = 1.8f, scaleVariance = 0.4f, fadeIn = 0.4f, fadeOut = 1.5f
    ),
    CHIMNEY_SMOKE(
        "Chimney Smoke",
        arrayOf(
            "textures/particles/factory_smoke/factory_smoke.png",
            "textures/particles/factory_smoke/factory_smoke_1.png",
            "textures/particles/factory_smoke/factory_smoke_2.png",
            "textures/particles/factory_smoke/factory_smoke_3.png",
            "textures/particles/factory_smoke/factory_smoke_4.png",
            "textures/particles/factory_smoke/factory_smoke_5.png"
        ),
        frameDuration = 0.2f, // Very slow for a billowing effect
        isLooping = true,
        particleLifetime = 6.0f,
        particleCount = 1..1, initialSpeed = 2f, speedVariance = 0.5f, gravity = 3f, // Rises steadily
        scale = 4.0f, scaleVariance = 1.0f, fadeIn = 1.0f, fadeOut = 2.5f
    ),
    THICK_SMOKE_PUFF(
        "Thick Smoke Puff",
        arrayOf(
            "textures/particles/factory_smoke/factory_smoke_3.png",
            "textures/particles/factory_smoke/factory_smoke.png",
            "textures/particles/factory_smoke/factory_smoke_1.png",
        ),
        frameDuration = 0.15f, isLooping = false, // A single puff
        particleLifetime = 2.5f, particleCount = 1..1,
        initialSpeed = 2.5f, speedVariance = 0.5f, gravity = 3.5f,
        scale = 3.0f, scaleVariance = 0.5f, fadeIn = 0.2f, fadeOut = 1.5f
    ),
    WISPY_SMOKE(
        "Wispy Smoke",
        arrayOf(
            "textures/particles/factory_smoke/factory_smoke_2.png",
            "textures/particles/factory_smoke/factory_smoke_5.png"
        ),
        frameDuration = 0.25f, isLooping = true, // A gentle, looping wisp
        particleLifetime = 5.0f, particleCount = 1..1,
        initialSpeed = 1.0f, speedVariance = 0.3f, gravity = 1.0f,
        scale = 2.8f, scaleVariance = 0.4f, fadeIn = 1.0f, fadeOut = 2.0f
    ),
    EXPLOSION(
        "Explosion",
        arrayOf("textures/particles/explosion_effect.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 0.5f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = 0f,
        scale = 3.0f, scaleVariance = 0f, fadeIn = 0.0f, fadeOut = 0.5f
    ),
    FIRE_FLAME(
        "Fire Flame",
        arrayOf("textures/particles/fire_flame.png"), // Can be made into an animation if you have more frames
        frameDuration = 0.1f, isLooping = true, particleLifetime = 3.0f,
        particleCount = 1..1, initialSpeed = 0.5f, speedVariance = 0.2f, gravity = 2f, // Flames go up
        scale = 1.5f, scaleVariance = 0.3f, fadeOut = 1.0f
    ),
    FIRED_SHOT(
        "Shot",
        arrayOf("textures/particles/gun_smoke/gun_smoke_6.png"), // Can be made into an animation if you have more frames
        frameDuration = 0.1f, isLooping = false, particleLifetime = 3.0f, lifetimeVariance = 0.4f, swingChance = 0.75f, swingAmplitude = 4f, swingAmplitudeVariance = 5f,  swingFrequencyVariance = 0.5f, swingFrequency = 0.5f,
        particleCount = 1..1, initialSpeed = 0.5f, speedVariance = 0.2f, gravity = 2f, // Flames go up
        scale = 1.5f, scaleVariance = 0.3f, fadeIn = 0.05f, fadeOut = 1.0f
    ),
    ITEM_GLOW(
        "Item Glow",
        arrayOf("textures/particles/item_glow.png"),
        frameDuration = 0.1f, isLooping = true, particleLifetime = 1.5f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = 0f,
        scale = 2.0f, scaleVariance = 0f, fadeIn = 0.5f, fadeOut = 0.5f
    ),
    BOOT_PRINTS(
        "Boot Prints",
        arrayOf("textures/particles/boot_prints.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 5.0f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = 0f, // No gravity, sticks to ground
        scale = 1.0f, scaleVariance = 0f, fadeIn = 0.2f, fadeOut = 2.0f
    ),
    MOVEMENT_WIPE(
        "Movement Wipe",
        arrayOf("textures/particles/wipe.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 0.4f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = 0f,
        scale = 1.5f, scaleVariance = 0.3f, fadeIn = 0.05f, fadeOut = 0.35f
    );

    val isAnimated: Boolean get() = texturePaths.size > 1
}

/**
 * Represents a single particle active in the world.
 */
data class GameParticle(
    val type: ParticleEffectType,
    val instance: ModelInstance,
    val animationSystem: AnimationSystem,
    val position: Vector3,
    val velocity: Vector3,
    var life: Float, // Remaining lifetime
    val initialLife: Float, // For calculating fade
    val scale: Float, // Store the particle's scale
    var animatesRotation: Boolean = false,
    var currentRotationY: Float = 0f,
    var targetRotationY: Float = 0f,
    var isSurfaceOriented: Boolean = false,
    val swings: Boolean,
    val swingAmplitude: Float,
    val swingFrequency: Float,
    var swingAngle: Float = 0f
) {
    val material: Material = instance.materials.first()
    private val blendingAttribute: BlendingAttribute = material.get(BlendingAttribute.Type) as BlendingAttribute
    private val rotationSpeed: Float = 360f // Degrees per second, matches player

    fun update(deltaTime: Float) {
        // Basic physics
        velocity.y += type.gravity * deltaTime
        position.add(velocity.x * deltaTime, velocity.y * deltaTime, velocity.z * deltaTime)

        // Animation
        if (type.isAnimated) {
            animationSystem.update(deltaTime)
            animationSystem.getCurrentTexture()?.let {
                material.set(TextureAttribute.createDiffuse(it))
            }
        }

        // Lifetime and fading
        life -= deltaTime
        updateOpacity()

        // Update rotation if enabled
        if (animatesRotation) {
            updateRotation(deltaTime)
        }

        // Update swinging animation if enabled
        if (this.swings) {
            updateSwinging()
        }

        // Update the model's transform
        updateTransform()
    }

    private fun updateOpacity() {
        val timeAlive = initialLife - life
        var opacity = 1f

        // Fade In
        if (type.fadeIn > 0 && timeAlive < type.fadeIn) {
            opacity = timeAlive / type.fadeIn
        }

        // Fade Out
        if (type.fadeOut > 0 && life < type.fadeOut) {
            opacity = life / type.fadeOut
        }

        blendingAttribute.opacity = opacity.coerceIn(0f, 1f)
    }

    private fun updateRotation(deltaTime: Float) {
        var rotationDifference = targetRotationY - currentRotationY
        if (rotationDifference > 180f) rotationDifference -= 360f
        else if (rotationDifference < -180f) rotationDifference += 360f

        if (kotlin.math.abs(rotationDifference) > 1f) {
            val rotationStep = rotationSpeed * deltaTime
            if (rotationDifference > 0f) currentRotationY += kotlin.math.min(rotationStep, rotationDifference)
            else currentRotationY += kotlin.math.max(-rotationStep, rotationDifference)
            if (currentRotationY >= 360f) currentRotationY -= 360f
            else if (currentRotationY < 0f) currentRotationY += 360f
        } else {
            currentRotationY = targetRotationY
        }
    }

    private fun updateSwinging() {
        val timeAlive = initialLife - life
        swingAngle = kotlin.math.sin(timeAlive * this.swingFrequency * 2 * kotlin.math.PI.toFloat()) * this.swingAmplitude
    }

    fun updateTransform() {
        if (isSurfaceOriented) {
            instance.transform.setTranslation(position)
        } else {
            // For billboard particles
            instance.transform.idt() // Reset transform
            instance.transform.setTranslation(position) // Set position

            // 1. Apply the base Y-axis rotation first (for facing left/right)
            if (animatesRotation) {
                instance.transform.rotate(Vector3.Y, currentRotationY) // Apply animated rotation
            }

            // 2. Apply the swing/rocking animation
            if (this.swings) {
                instance.transform.rotate(Vector3.Z, swingAngle)
            }

            // 3. Re-apply scale last
            instance.transform.scale(scale, scale, scale) // Re-apply scale
        }
    }
}

/**
 * Manages the creation, updating, and rendering of all particle effects.
 */
class ParticleSystem {
    private val activeParticles = Array<GameParticle>()
    private val particleModels = mutableMapOf<ParticleEffectType, Model>()

    private val billboardModelBatch: ModelBatch
    private val billboardShaderProvider: BillboardShaderProvider = BillboardShaderProvider().apply {
        setBillboardLightingStrength(0.9f)
        setMinLightLevel(0.5f)
    }

    var currentSelectedEffect: ParticleEffectType = ParticleEffectType.BLOOD_SPLATTER_1

    init {
        billboardModelBatch = ModelBatch(billboardShaderProvider)
    }

    fun initialize() {
        println("Initializing Particle System...")
        val modelBuilder = ModelBuilder()

        for (type in ParticleEffectType.entries) {
            try {
                val texture = Texture(Gdx.files.internal(type.texturePaths.first()), true).apply {
                    setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
                }

                val material = Material(
                    TextureAttribute.createDiffuse(texture),
                    BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1.0f),
                    IntAttribute.createCullFace(GL20.GL_NONE)
                )

                val model = modelBuilder.createRect(
                    -type.scale / 2f, -type.scale / 2f, 0f,
                    type.scale / 2f, -type.scale / 2f, 0f,
                    type.scale / 2f, type.scale / 2f, 0f,
                    -type.scale / 2f, type.scale / 2f, 0f,
                    0f, 0f, 1f, // Normal facing forward
                    material,
                    (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
                )
                particleModels[type] = model
            } catch (e: Exception) {
                println("ERROR: Could not load particle resources for ${type.displayName}: ${e.message}")
            }
        }
        println("Particle System initialized with ${particleModels.size} effect models.")
    }

    /**
     * Spawns a particle effect at a given position.
     */
    fun spawnEffect(
        type: ParticleEffectType,
        position: Vector3,
        baseDirection: Vector3? = null,
        surfaceNormal: Vector3? = null,
        initialRotation: Float? = null,
        targetRotation: Float? = null
    ) {
        val model = particleModels[type] ?: return // Can't spawn if model isn't loaded
        val particleCount = type.particleCount.random()

        for (i in 0 until particleCount) {
            val instance = ModelInstance(model)
            instance.userData = "player"

            val lifeVariance = (Random.nextFloat() - 0.5f) * 2f * type.lifetimeVariance
            val life = (type.particleLifetime + lifeVariance).coerceAtLeast(0.1f)

            // Create a unique animation system for each particle if needed
            val animSystem = AnimationSystem()
            if (type.isAnimated) {
                animSystem.createAnimation(type.name, type.texturePaths, type.frameDuration, type.isLooping)
                animSystem.playAnimation(type.name)
            }

            // Calculate velocity
            val velocity = if (baseDirection != null) {
                Vector3(baseDirection).nor()
            } else {
                // Random direction for explosions like blood
                Vector3(Random.nextFloat() - 0.5f, Random.nextFloat() - 0.5f, Random.nextFloat() - 0.5f).nor()
            }
            val speed = type.initialSpeed + (Random.nextFloat() - 0.5f) * 2f * type.speedVariance
            velocity.scl(speed)

            // Calculate scale
            val scale: Float
            if (type.scaleVariance > 0f && Random.nextFloat() < type.sizeRandomnessChance) {
                val sizeVariance = (Random.nextFloat() - 0.5f) * 2f * type.scaleVariance
                // Ensure scale doesn't become zero or negative
                scale = (type.scale + sizeVariance).coerceAtLeast(0.1f)
            } else {
                // This particle gets the default base size
                scale = type.scale
            }

            // Per-particle randomization
            val willSwing = Random.nextFloat() < type.swingChance

            val ampVariance = (Random.nextFloat() - 0.5f) * 2f * type.swingAmplitudeVariance
            val particleAmplitude = (type.swingAmplitude + ampVariance).coerceAtLeast(0f)

            val freqVariance = (Random.nextFloat() - 0.5f) * 2f * type.swingFrequencyVariance
            val particleFrequency = (type.swingFrequency + freqVariance).coerceAtLeast(0.1f)

            val particle = GameParticle(
                type = type,
                instance = instance,
                animationSystem = animSystem,
                position = position.cpy(),
                velocity = velocity,
                life = life,
                initialLife = life,
                scale = scale,
                swings = willSwing,
                swingAmplitude = particleAmplitude,
                swingFrequency = particleFrequency
            )

            // Handle surface orientation before setting up other animations
            if (isGroundOrientedEffect(type) && surfaceNormal != null) {
                orientParticleToSurface(instance, surfaceNormal)
                particle.isSurfaceOriented = true
            }

            // Set up rotation animation
            if (initialRotation != null && targetRotation != null) {
                particle.animatesRotation = true
                particle.currentRotationY = initialRotation
                particle.targetRotationY = targetRotation
            }

            // Apply the initial transform
            particle.updateTransform()

            activeParticles.add(particle)
        }
    }

    private fun isGroundOrientedEffect(type: ParticleEffectType): Boolean {
        return when (type) {
            ParticleEffectType.BLOOD_SPLATTER_1,
            ParticleEffectType.BLOOD_SPLATTER_2,
            ParticleEffectType.BLOOD_SPLATTER_3,
            ParticleEffectType.BOOT_PRINTS -> true
            else -> false
        }
    }

    private fun orientParticleToSurface(instance: ModelInstance, surfaceNormal: Vector3) {
        val transform = instance.transform
        val position = Vector3()
        transform.getTranslation(position)

        // Reset rotation and set position
        transform.idt()
        transform.setTranslation(position)

        // Create rotation to align with surface
        val up = Vector3(0f, 0f, 1f) // Original particle "up" direction (facing camera)
        val normal = Vector3(surfaceNormal).nor()

        // Calculate rotation axis and angle
        val rotationAxis = Vector3(up).crs(normal)
        val angle = Math.acos(up.dot(normal).toDouble()).toFloat() * com.badlogic.gdx.math.MathUtils.radiansToDegrees

        // Only rotate if there's a meaningful rotation needed
        if (rotationAxis.len() > 0.001f) {
            rotationAxis.nor()
            transform.rotate(rotationAxis, angle)
        }

        // Apply scaling after rotation
        val currentScale = Vector3()
        transform.getScale(currentScale)
        transform.scale(currentScale.x, currentScale.y, currentScale.z)
    }

    fun update(deltaTime: Float) {
        val iterator = activeParticles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            particle.update(deltaTime)
            if (particle.life <= 0) {
                particle.animationSystem.dispose()
                iterator.remove()
            }
        }
    }

    fun render(camera: Camera, environment: Environment) {
        if (activeParticles.isEmpty) return

        billboardShaderProvider.setEnvironment(environment)
        billboardModelBatch.begin(camera)
        for (particle in activeParticles) {
            billboardModelBatch.render(particle.instance, environment)
        }
        billboardModelBatch.end()
    }

    fun nextEffect() {
        val currentIndex = ParticleEffectType.entries.indexOf(currentSelectedEffect)
        val nextIndex = (currentIndex + 1) % ParticleEffectType.entries.size
        currentSelectedEffect = ParticleEffectType.entries[nextIndex]
    }

    fun previousEffect() {
        val currentIndex = ParticleEffectType.entries.indexOf(currentSelectedEffect)
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else ParticleEffectType.entries.size - 1
        currentSelectedEffect = ParticleEffectType.entries[prevIndex]
    }

    fun dispose() {
        particleModels.values.forEach { it.dispose() }
        particleModels.clear()
        activeParticles.forEach { it.animationSystem.dispose() }
        activeParticles.clear()
        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()
    }
}
