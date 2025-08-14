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
import com.badlogic.gdx.math.collision.Ray
import kotlin.math.floor
import kotlin.random.Random

enum class CarState {
    DRIVABLE,
    WRECKED, // The burned-out state
    FADING_OUT // The state before being removed
}

enum class DamageType {
    GENERIC, // For bullets or other untyped sources
    FIRE,
    EXPLOSIVE,
    MELEE
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

    lateinit var sceneManager: SceneManager
    private lateinit var raycastSystem: RaycastSystem
    private val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)
    private val tempVec3 = Vector3()
    private var blockSize: Float = 4f


    fun initialize(blockSize: Float) {
        this.blockSize = blockSize
        this.raycastSystem = RaycastSystem(blockSize)

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
        this.blockSize = 4f
        this.raycastSystem = RaycastSystem(blockSize)
    }

    fun handlePlaceAction(ray: Ray) {
        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            // Snap to grid
            val gridX = floor(tempVec3.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(tempVec3.z / blockSize) * blockSize + blockSize / 2
            val properY = findHighestSurfaceYAt(gridX, gridZ) // Use local helper

            // Check if there's already a car at this position
            val existingCar = sceneManager.activeCars.find { car ->
                kotlin.math.abs(car.position.x - gridX) < 2f &&
                    kotlin.math.abs(car.position.z - gridZ) < 2f
            }

            if (existingCar == null) {
                addCar(gridX, properY, gridZ, currentSelectedCar)
                println("${currentSelectedCar.displayName} placed at: $gridX, $properY, $gridZ")
            } else {
                println("Car already exists near this position")
            }
        }
    }

    fun handleRemoveAction(ray: Ray): Boolean {
        val carToRemove = raycastSystem.getCarAtRay(ray, sceneManager.activeCars)
        if (carToRemove != null) {
            removeCar(carToRemove)
            return true
        }
        return false
    }

    private fun addCar(x: Float, y: Float, z: Float, carType: CarType) {
        val carInstance = createCarInstance(carType)
        if (carInstance != null) {
            val position = Vector3(x, y, z)
            val gameCar = GameCar(carInstance, carType, position, 0f, isNextCarLocked)
            sceneManager.activeCars.add(gameCar)
            sceneManager.game.lastPlacedInstance = gameCar

            println("Placed ${carType.displayName}. Locked: ${gameCar.isLocked}")
        }
    }

    private fun removeCar(carToRemove: GameCar) {
        sceneManager.activeCars.removeValue(carToRemove, true)
        println("${carToRemove.carType.displayName} removed at: ${carToRemove.position}")
    }

    private fun findHighestSurfaceYAt(x: Float, z: Float): Float {
        val blocksInColumn = sceneManager.activeChunkManager.getBlocksInColumn(x, z)
        var highestY = 0f // Default to ground level
        val tempBounds = BoundingBox() // Re-use this to avoid creating new objects in the loop

        for (gameBlock in blocksInColumn) {
            // Skip blocks that don't have collision
            if (!gameBlock.blockType.hasCollision) continue

            // Get the world-space bounding box for the block
            val blockBounds = gameBlock.getBoundingBox(blockSize, BoundingBox())

            // If it is, check if this block's top surface is the highest we've found so far
            if (blockBounds.max.y > highestY) {
                highestY = blockBounds.max.y
            }
        }
        return highestY
    }

    fun render(camera: Camera, environment: Environment, cars: com.badlogic.gdx.utils.Array<GameCar>) {
        billboardShaderProvider.setEnvironment(environment)

        billboardModelBatch.begin(camera)
        for (car in cars) {
            // The car's internal update no longer needs the texture passed to it
            car.update(Gdx.graphics.deltaTime)

            car.updateTransform()
            billboardModelBatch.render(car.modelInstance, environment)
        }
        billboardModelBatch.end()
    }

    fun update(deltaTime: Float, sceneManager: SceneManager) {
        val carIterator = sceneManager.activeCars.iterator()
        while (carIterator.hasNext()) {
            val car = carIterator.next()

            // Let the car update its internal timers and animations
            car.update(deltaTime)

            // 1. Check if a drivable car should be destroyed
            if (car.state == CarState.DRIVABLE && car.health <= 0) {
                // If the player is driving this car, kick them out
                if (sceneManager.playerSystem.isDriving && sceneManager.playerSystem.getControlledEntityPosition() == car.position) {
                    sceneManager.playerSystem.exitCar(sceneManager)
                }
                val shouldSpawnFire = car.lastDamageType != DamageType.FIRE

                // Trigger the destruction sequence
                val newFireObjects = car.destroy(
                    sceneManager.game.particleSystem,
                    this, // Pass this CarSystem instance
                    shouldSpawnFire,
                    sceneManager.fireSystem,
                    sceneManager.game.objectSystem, // Get systems from SceneManager's game reference
                    sceneManager.game.lightingManager
                )
                sceneManager.activeObjects.addAll(newFireObjects)
            }

            // 2. Check if a faded-out car should be removed
            if (car.isReadyForRemoval) {
                if (sceneManager.playerSystem.isDriving && sceneManager.playerSystem.getControlledEntityPosition() == car.position) {
                    sceneManager.playerSystem.exitCar(sceneManager)
                }
                carIterator.remove()
                println("Removed wrecked car from scene: ${car.carType.displayName}")
            }
        }
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

    var visualRotationY = 0f // Current visual rotation
    private var targetRotationY = 0f // Target visual rotation
    private val rotationSpeed = 360f // Degrees per second
    private var lastHorizontalDirection = 0f

    // Animation System for the Car
    private val animationSystem = AnimationSystem()
    private val material: Material = modelInstance.materials.get(0)
    private var lastTexture: Texture? = null

    // State management properties
    var state: CarState = CarState.DRIVABLE
    var isVisible: Boolean = true
    private var wreckSpawnTimer: Float = 0f
    private var wreckedTimer: Float = 0f
    private var fadeOutTimer: Float = FADE_OUT_DURATION

    // Convenience properties
    val isDestroyed: Boolean get() = state == CarState.WRECKED || state == CarState.FADING_OUT
    val isReadyForRemoval: Boolean get() = state == CarState.FADING_OUT && fadeOutTimer <= 0f
    var lastDamageType: DamageType = DamageType.GENERIC

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

    fun takeDamage(damage: Float, type: DamageType) {
        if (state == CarState.DRIVABLE && health > 0) {
            health -= damage
            this.lastDamageType = type // Remember the last damage source
            println("${this.carType.displayName} took $damage $type damage. HP: ${this.health.toInt()}")
        }
    }

    fun destroy(
        particleSystem: ParticleSystem,
        carSystem: CarSystem,
        shouldSpawnFire: Boolean,
        fireSystem: FireSystem,
        objectSystem: ObjectSystem,
        lightingManager: LightingManager
    ): com.badlogic.gdx.utils.Array<GameObject> {
        if (state != CarState.DRIVABLE) return com.badlogic.gdx.utils.Array()

        println("${this.carType.displayName} has been destroyed!")
        state = CarState.WRECKED
        wreckedTimer = WRECKED_DURATION

        // 1. Spawn the explosion particle effect
        val explosionPos = position.cpy().add(0f, carType.height / 1.15f, 0f)

        // Randomly choose which explosion effect to use
        if (Random.nextFloat() < 0.5f) {
            println("Spawning BIG car explosion!")

            val bigExplosionSpawnPos = explosionPos.cpy().add(0f, 3.15f, 0f)
            // Spawn big explosion
            particleSystem.spawnEffect(ParticleEffectType.CAR_EXPLOSION_BIG, bigExplosionSpawnPos)

        } else {
            println("Spawning standard car explosion.")
            particleSystem.spawnEffect(ParticleEffectType.CAR_EXPLOSION, explosionPos)
        }

        // 2. Immediately switch to the wrecked texture
        val wreckedTexture = carSystem.getWreckedTexture()
        val textureAttribute = material.get(TextureAttribute.Diffuse) as TextureAttribute?
        textureAttribute?.textureDescription?.texture = wreckedTexture

        // SPAWNING GAMEPLAY FIRE
        val newFireObjects = com.badlogic.gdx.utils.Array<GameObject>()

        if (shouldSpawnFire) {
            println("${this.carType.displayName} is exploding and spawning fire!")

            // Save the FireSystem's current settings so we don't mess up the user's editor selection
            val originalFadesOut = fireSystem.nextFireFadesOut
            val originalLifetime = fireSystem.nextFireLifetime
            val originalMinScale = fireSystem.nextFireMinScale
            val originalMaxScale = fireSystem.nextFireMaxScale

            // Configure the FireSystem for small, temporary car fires
            fireSystem.nextFireFadesOut = true
            fireSystem.nextFireLifetime = 12f // Fire lasts for 12 seconds
            fireSystem.nextFireMinScale = 0.4f // Small flames
            fireSystem.nextFireMaxScale = 0.8f // with a little size variation

            val fireCount = (2..4).random() // Spawn 2 to 4 fires
            val spawnRadius = carType.width / 2.5f

            for (i in 0 until fireCount) {
                val offsetX = (Random.nextFloat() * 2f - 1f) * spawnRadius
                val offsetZ = (Random.nextFloat() * 2f - 1f) * spawnRadius
                val firePosition = position.cpy().add(offsetX, 0.1f, offsetZ)

                // Add the fire using the FireSystem
                val newFire = fireSystem.addFire(
                    position = firePosition,
                    objectSystem = objectSystem,
                    lightingManager = lightingManager,
                    lightIntensityOverride = 20f,
                    lightRangeOverride = 10f
                )

                if (newFire != null) {
                    newFireObjects.add(newFire.gameObject)
                }
            }

            // Restore original settings
            fireSystem.nextFireFadesOut = originalFadesOut
            fireSystem.nextFireLifetime = originalLifetime
            fireSystem.nextFireMinScale = originalMinScale
            fireSystem.nextFireMaxScale = originalMaxScale
        } else {
            println("${this.carType.displayName} was destroyed by fire, no additional fire spawned.")
        }

        return newFireObjects
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
    TAXI("Taxi", "textures/objects/cars/taxi.png", 10f, 7f, 220f),
    TAXI_CAB("Taxi Cab", "textures/objects/cars/taxi_cab.png", 10f, 7f, 240f),
    NEWSPAPER_TRUCK("Newspaper Truck", "textures/objects/cars/newspaper_truck.png", 12f, 8f, 300f),
    CARGO_TRUCK("Cargo Truck", "textures/objects/cars/red_cargo.png", 12f, 5f, 400f),
    POLICE_CAR("Police Car", "textures/objects/cars/police_car.png", 8f, 5f, 200f),
    VAN("Van", "textures/objects/cars/van.png", 9f, 6f, 280f),
}
