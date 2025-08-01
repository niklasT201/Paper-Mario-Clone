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
import com.badlogic.gdx.math.collision.BoundingBox

enum class CarState {
    DRIVABLE,
    WRECKED, // The burned-out state
    FADING_OUT // The state before being removed
}

class CarSystem: IFinePositionable {
    private val carModels = mutableMapOf<CarType, Model>()
    private lateinit var wreckedCarTexture: Texture
    private lateinit var billboardShaderProvider: BillboardShaderProvider
    private lateinit var billboardModelBatch: ModelBatch

    var currentSelectedCar = CarType.DEFAULT
        private set
    var currentSelectedCarIndex = 0
        private set
    var isNextCarLocked = false
        private set

    // Fine positioning mode
    override var finePosMode = false
    override val fineStep = 0.25f

    fun initialize() {
        billboardShaderProvider = BillboardShaderProvider()
        billboardModelBatch = ModelBatch(billboardShaderProvider)
        billboardShaderProvider.setBillboardLightingStrength(0.9f)
        billboardShaderProvider.setMinLightLevel(0.3f)
        // Load wrecked car texture once
        wreckedCarTexture = Texture(Gdx.files.internal("textures/objects/cars/burned_down_car.png"))
        val modelBuilder = ModelBuilder()

        // Load models for each car type
        for (carType in CarType.entries) {
            try {
                // Load texture for cars
                val initialTexture = Texture(Gdx.files.internal(carType.texturePath))
                initialTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)

                // Create material with texture and transparency
                val material = Material(
                    TextureAttribute.createDiffuse(initialTexture),
                    BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                    IntAttribute.createCullFace(GL20.GL_NONE) // Disable backface culling
                )

                // Create 2D billboard model (single quad that stands upright)
                val model = createCarBillboard(modelBuilder, material, carType)
                carModels[carType] = model

                println("Loaded car type: ${carType.displayName}")
            } catch (e: Exception) {
                println("Failed to load car ${carType.displayName}: ${e.message}")
            }
        }
    }

    fun render(camera: Camera, environment: Environment, cars: com.badlogic.gdx.utils.Array<GameCar>) {
        // This is the crucial step: feed the lighting info to the shader
        billboardShaderProvider.setEnvironment(environment)

        billboardModelBatch.begin(camera)
        for (car in cars) {
            // We update the transform and animation right before rendering
            car.update(Gdx.graphics.deltaTime)
            car.updateTransform()
            billboardModelBatch.render(car.modelInstance, environment)
        }
        billboardModelBatch.end()
    }

    private fun createCarBillboard(modelBuilder: ModelBuilder, material: Material, carType: CarType): Model {
        // Calculate dimensions based on the car size
        val width = carType.width
        val height = carType.height
        val halfWidth = width / 2f

        // Create a model with a single upright quad (billboard)
        modelBuilder.begin()

        val part = modelBuilder.part(
            "car_billboard",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or
                VertexAttributes.Usage.Normal or
                VertexAttributes.Usage.TextureCoordinates).toLong(),
            material
        )

        // Vertices for upright quad facing the camera (Z-axis oriented)
        part.vertex(-halfWidth, 0f, 0f, 0f, 0f, 1f, 0f, 1f) // Bottom left
        part.vertex(halfWidth, 0f, 0f, 0f, 0f, 1f, 1f, 1f)  // Bottom right
        part.vertex(halfWidth, height, 0f, 0f, 0f, 1f, 1f, 0f) // Top right
        part.vertex(-halfWidth, height, 0f, 0f, 0f, 1f, 0f, 0f) // Top left

        // Triangles for the quad
        part.triangle(0, 1, 2) // First triangle
        part.triangle(2, 3, 0) // Second triangle

        return modelBuilder.end()
    }

    fun toggleLockState() {
        isNextCarLocked = !isNextCarLocked
        println("Next car lock state toggled to: $isNextCarLocked")
    }

    fun nextCar() {
        currentSelectedCarIndex = (currentSelectedCarIndex + 1) % CarType.entries.size
        currentSelectedCar = CarType.entries.toTypedArray()[currentSelectedCarIndex]
        println("Selected car: ${currentSelectedCar.displayName}")
    }

    fun previousCar() {
        currentSelectedCarIndex = if (currentSelectedCarIndex > 0) {
            currentSelectedCarIndex - 1
        } else {
            CarType.entries.size - 1
        }
        currentSelectedCar = CarType.entries.toTypedArray()[currentSelectedCarIndex]
        println("Selected car: ${currentSelectedCar.displayName}")
    }

    fun createCarInstance(carType: CarType): ModelInstance? {
        val model = carModels[carType]
        return model?.let { ModelInstance(it) }
    }

    fun getWreckedTexture(): Texture = wreckedCarTexture

    fun dispose() {
        carModels.values.forEach { it.dispose() }
        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()
    }
}

// Game car class to store car data
data class GameCar(
    val modelInstance: ModelInstance,
    val carType: CarType,
    val position: Vector3,
    var direction: Float = 0f, // Direction in degrees (0 = facing forward/north)
    val isLocked: Boolean = false,
    var health: Float = carType.baseHealth
) {
    companion object {
        const val WRECKED_DURATION = 25f
        const val FADE_OUT_DURATION = 5f
    }

    val id: String = java.util.UUID.randomUUID().toString()

    private var visualRotationY = 0f // Current visual rotation
    private var targetRotationY = 0f // Target visual rotation
    private val rotationSpeed = 360f // Degrees per second
    private var lastHorizontalDirection = 0f

    // Animation System for the Car
    private val animationSystem = AnimationSystem()
    private val material: Material = modelInstance.materials.get(0)
    private var lastTexture: Texture? = null

    // State management properties
    var state: CarState = CarState.DRIVABLE
    private var wreckedTimer: Float = 0f
    private var fadeOutTimer: Float = FADE_OUT_DURATION

    // Convenience properties
    val isDestroyed: Boolean get() = state == CarState.WRECKED || state == CarState.FADING_OUT
    val isReadyForRemoval: Boolean get() = state == CarState.FADING_OUT && fadeOutTimer <= 0f

    fun initializeAnimations() {
        // Only the default car has animations for now.
        if (carType != CarType.DEFAULT) return

        // 1. Create Idle Animation (the single default texture)
        animationSystem.createAnimation(
            name = "car_idle",
            texturePaths = arrayOf(carType.texturePath),
            frameDuration = 1f, // Duration doesn't matter for a single frame
            isLooping = true
        )

        // 2. Create Driving Animation
        val drivingFrames = mutableListOf<String>()
        // The sequence is: standard -> driving_1 -> ... -> driving_9
        drivingFrames.add(carType.texturePath) // The standard "idle" frame
        for (i in 1..9) {
            drivingFrames.add("textures/objects/cars/Default/ainmations/driving_$i.png")
        }
        animationSystem.createAnimation(
            name = "car_driving",
            texturePaths = drivingFrames.toTypedArray(),
            frameDuration = 0.05f, // Each frame lasts 0.06 seconds for a quick animation
            isLooping = true
        )

        // 3. Start with the idle animation
        animationSystem.playAnimation("car_idle")
        lastTexture = animationSystem.getCurrentTexture()
    }

    fun setDrivingAnimationState(isDrivingHorizontally: Boolean) {
        if (carType != CarType.DEFAULT) return

        val targetAnimation = if (isDrivingHorizontally) "car_driving" else "car_idle"
        if (animationSystem.getCurrentAnimationName() != targetAnimation) {
            animationSystem.playAnimation(targetAnimation)
        }
    }

    // Get bounding box for collision detection
    fun getBoundingBox(positionOverride: Vector3? = null): BoundingBox {
        val bounds = BoundingBox()
        val currentPos = positionOverride ?: this.position // Use the override if it exists, otherwise use the car's actual position

        val halfWidth = carType.width / 2f
        val halfHeight = carType.height / 2f

        // Use a more reasonable thickness based on car type
        val thickness = carType.width * 0.3f // Make thickness proportional to car width
        val halfThickness = thickness / 2f

        // Create collision box based on car's actual orientation
        when (direction.toInt()) {
            0, 180 -> {
                // Car is facing north/south: wide on X, thin on Z
                bounds.set(
                    Vector3(currentPos.x - halfWidth, currentPos.y, currentPos.z - halfThickness),
                    Vector3(currentPos.x + halfWidth, currentPos.y + halfHeight, currentPos.z + halfThickness)
                )
            }
            90, 270 -> {
                // Car is facing east/west: thin on X, wide on Z
                bounds.set(
                    Vector3(currentPos.x - halfThickness, currentPos.y, currentPos.z - halfWidth),
                    Vector3(currentPos.x + halfThickness, currentPos.y + halfHeight, currentPos.z + halfWidth)
                )
            }
            else -> {
                // For other angles, use a square collision box (simpler but less precise)
                val maxDimension = kotlin.math.max(halfWidth, halfThickness)
                bounds.set(
                    Vector3(currentPos.x - maxDimension, currentPos.y, currentPos.z - maxDimension),
                    Vector3(currentPos.x + maxDimension, currentPos.y + halfHeight, currentPos.z + maxDimension)
                )
            }
        }
        return bounds
    }

    fun takeDamage(damage: Float) {
        if (state == CarState.DRIVABLE && health > 0) {
            health -= damage
            println("${this.carType.displayName} took $damage damage. HP remaining: ${this.health.toInt()}")
            if (health <= 0) {
                health = 0f
            }
        }
    }

    fun destroy(particleSystem: ParticleSystem, wreckedTexture: Texture) {
        if (state != CarState.DRIVABLE) return // Can only be destroyed once

        println("${this.carType.displayName} has been destroyed!")
        state = CarState.WRECKED
        wreckedTimer = WRECKED_DURATION

        // Spawn explosion effect at the center of the car
        val explosionPos = position.cpy().add(0f, carType.height / 2f, 0f)
        particleSystem.spawnEffect(ParticleEffectType.CAR_EXPLOSION, explosionPos)

        // Switch to the wrecked texture
        val textureAttribute = material.get(TextureAttribute.Diffuse) as TextureAttribute?
        textureAttribute?.textureDescription?.texture = wreckedTexture
    }

    fun updateFlipAnimation(horizontalDirection: Float, deltaTime: Float) {
        // Only update target rotation if there's actual horizontal movement
        if (horizontalDirection != 0f && horizontalDirection != lastHorizontalDirection) {
            targetRotationY = if (horizontalDirection < 0f) 180f else 0f
            lastHorizontalDirection = horizontalDirection
        }

        // Smoothly interpolate current rotation towards target rotation
        updateVisualRotation(deltaTime)
    }

    private fun updateVisualRotation(deltaTime: Float) {
        // Calculate the shortest rotation path
        var rotationDifference = targetRotationY - visualRotationY
        if (rotationDifference > 180f) rotationDifference -= 360f
        else if (rotationDifference < -180f) rotationDifference += 360f

        if (kotlin.math.abs(rotationDifference) > 1f) {
            val rotationStep = rotationSpeed * deltaTime
            if (rotationDifference > 0f) {
                visualRotationY += kotlin.math.min(rotationStep, rotationDifference)
            } else {
                visualRotationY += kotlin.math.max(-rotationStep, rotationDifference)
            }

            // Keep rotation in 0-360 range
            if (visualRotationY >= 360f) visualRotationY -= 360f
            else if (visualRotationY < 0f) visualRotationY += 360f
        } else {
            // Snap to target if close enough
            visualRotationY = targetRotationY
        }
    }

    // Update the car's position and visual rotation
    fun updateTransform() {
        // Reset transform matrix
        modelInstance.transform.idt()

        // Update position
        modelInstance.transform.setTranslation(position)

        // Apply visual rotation for flip effect (this is separate from movement direction)
        modelInstance.transform.rotate(Vector3.Y, visualRotationY)
    }

    fun update(deltaTime: Float) {
        when (state) {
            CarState.DRIVABLE -> {
                // 1. Update the animation timer
                animationSystem.update(deltaTime)

                // 2. Check if the texture needs to be changed
                val newTexture = animationSystem.getCurrentTexture()
                if (newTexture != null && newTexture != lastTexture) {
                    // 3. Apply the new texture to the car's material
                    val textureAttribute = material.get(TextureAttribute.Diffuse) as TextureAttribute?
                    textureAttribute?.textureDescription?.texture = newTexture
                    lastTexture = newTexture
                }
            }
            CarState.WRECKED -> {
                wreckedTimer -= deltaTime
                if (wreckedTimer <= 0) {
                    state = CarState.FADING_OUT
                }
            }
            CarState.FADING_OUT -> {
                fadeOutTimer -= deltaTime
                // Update opacity for the fade-out effect
                val blendingAttribute = material.get(BlendingAttribute.Type) as? BlendingAttribute
                blendingAttribute?.opacity = (fadeOutTimer / FADE_OUT_DURATION).coerceIn(0f, 1f)
            }
        }
    }

    fun dispose() {
        animationSystem.dispose()
    }

    fun moveForward(distance: Float) {
        val radians = Math.toRadians(direction.toDouble()).toFloat()
        position.x += kotlin.math.sin(radians) * distance
        position.z += kotlin.math.cos(radians) * distance
    }

    // Method to move car sideways (strafe)
    fun moveSideways(distance: Float) {
        val radians = Math.toRadians((direction + 90f).toDouble()).toFloat()
        position.x += kotlin.math.sin(radians) * distance
        position.z += kotlin.math.cos(radians) * distance
    }
}

// Car type definitions
enum class CarType(
    val displayName: String,
    val texturePath: String,
    val width: Float,
    val height: Float,
    val baseHealth: Float
) {
    DEFAULT("Default", "textures/objects/cars/car_driving.png", 10f, 7f, 250f),
    BOSS_CAR("Boss Car", "textures/objects/cars/boss_car.png", 10f, 7f, 500f),
    SUV("SUV", "textures/objects/cars/suv.png", 8f, 5f, 300f),
    TRUCK("Truck", "textures/objects/cars/truck.png", 11f, 6f, 400f),
    VAN("Van", "textures/objects/cars/van.png", 7f, 5.5f, 280f),
    POLICE_CAR("Police Car", "textures/objects/cars/police_car.png", 7f, 4f, 200f),
    TAXI("Taxi", "textures/objects/cars/taxi.png", 10f, 7f, 220f),
}
