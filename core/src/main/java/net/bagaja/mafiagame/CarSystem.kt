package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Array
import java.util.*
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

data class CarSeat(
    val localOffset: Vector3,
    var occupant: Any? = null
)

class CarSystem: IFinePositionable {
    private val carModels = mutableMapOf<CarType, Model>()
    private lateinit var wreckedCarTexture: Texture
    private lateinit var billboardShaderProvider: BillboardShaderProvider
    private lateinit var billboardModelBatch: ModelBatch
    lateinit var enemySystem: EnemySystem
    lateinit var npcSystem: NPCSystem
    lateinit var uiManager: UIManager

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
    lateinit var raycastSystem: RaycastSystem
    private val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)
    private val tempVec3 = Vector3()
    private var blockSize: Float = 4f

    companion object {
        const val HEADLIGHT_INTENSITY = 12f
    }

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
        // Check the current editor mode
        if (sceneManager.game.uiManager.currentEditorMode == EditorMode.MISSION) {
            handleMissionPlacement(ray)
            return
        }

        // World Editing logic
        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            // Snap to grid
            val gridX = floor(tempVec3.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(tempVec3.z / blockSize) * blockSize + blockSize / 2
            val properY = findHighestSurfaceYAt(gridX, gridZ)
            val position = Vector3(gridX, properY, gridZ)

            // Check if there's already a car at this position
            val existingCar = sceneManager.activeCars.find { car -> car.position.dst2(position) < 9f }

            if (existingCar == null) {
                val config = uiManager.getCarSpawnConfig()
                val newCar = addCar(position.x, position.y, position.z, config.carType, config.isLocked)
                if (newCar != null) {
                    when (config.driverCharacterType) {
                        "Enemy" -> {
                            val enemyConfig = EnemySpawnConfig(
                                enemyType = config.enemyDriverType!!,
                                behavior = EnemyBehavior.AGGRESSIVE_RUSHER,
                                position = newCar.position
                            )
                            val driver = enemySystem.createEnemy(enemyConfig)
                            if (driver != null) {
                                driver.enterCar(newCar)
                                driver.currentState = AIState.PATROLLING_IN_CAR
                                sceneManager.activeEnemies.add(driver)
                                println("Spawned car with Enemy driver.")
                            }
                        }
                        "NPC" -> {
                            val npcConfig = NPCSpawnConfig(config.npcDriverType!!, NPCBehavior.GUARD, newCar.position)
                            val driver = npcSystem.createNPC(npcConfig)
                            if (driver != null) {
                                driver.enterCar(newCar)
                                driver.currentState = NPCState.PATROLLING_IN_CAR
                                sceneManager.activeNPCs.add(driver)
                                println("Spawned car with NPC driver.")
                            }
                        }
                    }
                }
            } else {
                println("Car already exists near this position")
            }
        }
    }

    private fun handleMissionPlacement(ray: Ray) {
        val mission = sceneManager.game.uiManager.selectedMissionForEditing
        if (mission == null) {
            sceneManager.game.uiManager.updatePlacementInfo("ERROR: No mission selected for editing!")
            return
        }

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            val gridX = floor(tempVec3.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(tempVec3.z / blockSize) * blockSize + blockSize / 2
            val properY = findHighestSurfaceYAt(gridX, gridZ)
            val carPosition = Vector3(gridX, properY, gridZ)

            // Get the full configuration from the UI, including driver info
            val config = uiManager.getCarSpawnConfig()
            val currentSceneId = sceneManager.getCurrentSceneId()

            if (currentSceneId != "WORLD") {
                uiManager.updatePlacementInfo("ERROR: Cars can only be placed in the World scene.")
                return
            }

            // 1. Create the GameEvent for the mission file
            val event = GameEvent(
                type = GameEventType.SPAWN_CAR,
                spawnPosition = carPosition,
                sceneId = currentSceneId,
                targetId = "car_${UUID.randomUUID()}",
                carType = config.carType,
                carIsLocked = config.isLocked,
                carDriverType = config.driverCharacterType,
                carEnemyDriverType = config.enemyDriverType,
                carNpcDriverType = config.npcDriverType
            )

            // 2. Add the event and save the mission
            mission.eventsOnStart.add(event)
            sceneManager.game.missionSystem.saveMission(mission)

            // 3. Create a temporary "preview" car to see in the world
            val carInstance = createCarInstance(config.carType)
            if (carInstance != null) {
                val previewCar = GameCar(
                    id = event.targetId!!,
                    modelInstance = carInstance,
                    carType = config.carType,
                    position = carPosition,
                    sceneManager = this.sceneManager,
                    isLocked = config.isLocked,
                    health = config.carType.baseHealth,
                    missionId = mission.id
                )
                sceneManager.activeMissionPreviewCars.add(previewCar)
                sceneManager.game.lastPlacedInstance = previewCar

                // 4. Create a preview driver if one was configured
                when (config.driverCharacterType) {
                    "Enemy" -> {
                        val enemyConfig = EnemySpawnConfig(config.enemyDriverType!!, EnemyBehavior.AGGRESSIVE_RUSHER, previewCar.position)
                        enemySystem.createEnemy(enemyConfig)?.let { driver ->
                            driver.missionId = mission.id
                            driver.enterCar(previewCar) // Place preview enemy in preview car
                            sceneManager.activeMissionPreviewEnemies.add(driver) // Add to preview list
                        }
                    }
                    "NPC" -> {
                        val npcConfig = NPCSpawnConfig(config.npcDriverType!!, NPCBehavior.WANDER, previewCar.position)
                        npcSystem.createNPC(npcConfig)?.let { driver ->
                            driver.missionId = mission.id
                            driver.enterCar(previewCar) // Place preview NPC in preview car
                            sceneManager.activeMissionPreviewNPCs.add(driver) // Add to preview list
                        }
                    }
                }
                uiManager.updatePlacementInfo("Added SPAWN_CAR to '${mission.title}'")
                uiManager.missionEditorUI.refreshEventWidgets()
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

    fun spawnCar(position: Vector3, carType: CarType, isLocked: Boolean, initialVisualRotation: Float = 0f): GameCar? {
        return addCar(position.x, position.y, position.z, carType, isLocked, initialVisualRotation)
    }

    private fun addCar(x: Float, y: Float, z: Float, carType: CarType, isLocked: Boolean, initialVisualRotation: Float = 0f): GameCar? {
        val carInstance = createCarInstance(carType)
        if (carInstance != null) {
            val position = Vector3(x, y, z)
            val gameCar = GameCar(
                modelInstance = carInstance,
                carType = carType,
                position = position,
                sceneManager = this.sceneManager,
                direction = 0f,
                isLocked = isLocked,
                health = carType.baseHealth,
                initialVisualRotation = initialVisualRotation
            )

            // Create and attach a unique headlight
            val headlight = sceneManager.game.objectSystem.createLightSource(
                position = Vector3(), // Position will be updated every frame by the car itself
                intensity = 0f,       // Start with lights off
                range = 45f,
                color = Color(1f, 1f, 0.8f, 1f)
            )
            // Use a unique negative ID to prevent conflicts with placeable lights
            headlight.id = -(2000 + sceneManager.activeCars.size)
            val instances = sceneManager.game.objectSystem.createLightSourceInstances(headlight)
            sceneManager.game.lightingManager.addLightSource(headlight, instances)

            // Associate the created light with the car object
            gameCar.headlightLight = headlight

            sceneManager.activeCars.add(gameCar)
            sceneManager.game.lastPlacedInstance = gameCar
            println("Placed ${carType.displayName}. Locked: $isLocked")
            return gameCar
        }
        return null
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

        val scaleFactor = 0.85f // Scale characters down slightly to fit in the car

        for (car in cars) {
            // 1. RENDER OCCUPANTS (BEHIND THE CAR)
            for (seat in car.seats) {
                val occupant = seat.occupant ?: continue

                // Determine which ModelInstance to render based on the occupant's type
                val occupantInstance: ModelInstance? = when (occupant) {
                    is PlayerSystem -> sceneManager.playerSystem.playerInstance
                    is GameEnemy -> occupant.modelInstance
                    is GameNPC -> occupant.modelInstance
                    else -> null
                }

                if (occupantInstance != null) {
                    // If the car is flipping, skip rendering
                    if (car.isFlipping) continue

                    // Calculate the occupant's world position by mirroring the offset when the car is flipped
                    val finalOffset = seat.localOffset.cpy()
                    if (car.visualRotationY == 180f) {
                        finalOffset.x *= -1f // Mirror the X offset ONLY
                    }
                    val finalOccupantPos = car.position.cpy().add(finalOffset)

                    // Save the original transform to restore it later.
                    val originalTransform = occupantInstance.transform.cpy()

                    // Apply the new transform for rendering inside the car
                    occupantInstance.transform.setToTranslation(finalOccupantPos)
                    // Add 180 degrees to the car's rotation to make the occupant face the same direction
                    occupantInstance.transform.rotate(Vector3.Y, car.visualRotationY + 180f)
                    occupantInstance.transform.scale(scaleFactor, scaleFactor, scaleFactor)

                    // Render the occupant
                    billboardModelBatch.render(occupantInstance, environment)

                    // Restore the original transform so it doesn't affect other game logic
                    occupantInstance.transform.set(originalTransform)
                }
            }

            // 2. RENDER THE CAR ITSELF (IN FRONT OF OCCUPANTS)
            car.updateTransform()
            billboardModelBatch.render(car.modelInstance, environment)
        }
        billboardModelBatch.end()
    }

    fun update(deltaTime: Float, sceneManager: SceneManager) {
        val carsToDestroy = Array<GameCar>()
        val carIterator = sceneManager.activeCars.iterator()
        while (carIterator.hasNext()) {
            val car = carIterator.next()

            // Let the car update its internal timers and animations
            car.update(deltaTime)

            // 1. Check if a drivable car's health has dropped to zero.
            if (car.state == CarState.DRIVABLE && car.health <= 0) {
                // Instead of destroying it immediately, we add it to our list for later processing.
                carsToDestroy.add(car)
            }

            // 2. Check if a faded-out car should be removed. This part is safe because
            if (car.isReadyForRemoval) {
                if (sceneManager.playerSystem.isDriving && sceneManager.playerSystem.getControlledEntityPosition() == car.position) {
                    sceneManager.playerSystem.exitCar(sceneManager)
                }
                carIterator.remove()
                println("Removed wrecked car from scene: ${car.carType.displayName}")
            }
        }

        // --- STAGE 2: DEFERRED PROCESSING ---
        if (carsToDestroy.notEmpty()) {
            for (car in carsToDestroy) {
                // If the player is driving this car, kick them out first.
                if (sceneManager.playerSystem.isDriving && sceneManager.playerSystem.getControlledEntityPosition() == car.position) {
                    sceneManager.playerSystem.exitCar(sceneManager)
                }
                sceneManager.game.missionSystem.reportCarDestroyed(car.id)
                val shouldSpawnFire = car.lastDamageType != DamageType.FIRE

                // Trigger the destruction sequence
                val newFireObjects = car.destroy(
                    sceneManager.game.particleSystem,
                    this,
                    shouldSpawnFire,
                    sceneManager.fireSystem,
                    sceneManager.game.objectSystem,
                    sceneManager.game.lightingManager
                )
                sceneManager.activeObjects.addAll(newFireObjects)
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
    val sceneManager: SceneManager,
    var id: String = UUID.randomUUID().toString(),
    var direction: Float = 0f,
    val isLocked: Boolean = false,
    var health: Float = carType.baseHealth,
    val initialVisualRotation: Float = 0f,
    @Transient var headlightLight: LightSource? = null,
    var areHeadlightsOn: Boolean = false,
    var missionId: String? = null
) {
    companion object {
        const val WRECKED_DURATION = 25f
        const val FADE_OUT_DURATION = 5f
    }

    var visualRotationY = 0f // Current visual rotation
    private var targetRotationY = 0f // Target visual rotation
    private val rotationSpeed = 360f // Degrees per second
    private var lastHorizontalDirection = if (initialVisualRotation == 180f) -1f else 1f

    // Animation System for the Car
    private val animationSystem = AnimationSystem()
    private val material: Material = modelInstance.materials.get(0)
    private var lastTexture: Texture? = null

    // State management properties
    var state: CarState = CarState.DRIVABLE
    var isVisible: Boolean = true
    private var wreckSpawnTimer: Float = 0f
    var wreckedTimer: Float = 0f
    var fadeOutTimer: Float = FADE_OUT_DURATION

    // Convenience properties
    val isDestroyed: Boolean get() = state == CarState.WRECKED || state == CarState.FADING_OUT
    val isReadyForRemoval: Boolean get() = state == CarState.FADING_OUT && fadeOutTimer <= 0f
    var lastDamageType: DamageType = DamageType.GENERIC
    val seats = com.badlogic.gdx.utils.Array<CarSeat>()

    var isFlipping: Boolean = false
        private set

    init {
        this.visualRotationY = initialVisualRotation
        this.targetRotationY = initialVisualRotation
        // Set lastHorizontalDirection to prevent an immediate flip on first movement
        this.lastHorizontalDirection = if (initialVisualRotation == 180f) -1f else 1f

        seats.add(CarSeat(Vector3(0.8f, 4.5f, -0.2f)))
        // seats.add(CarSeat(Vector3(1.5f, 4.0f, 0f))) // Passenger seat

        initializeAnimations()
    }

    private fun findAvailableSeat(): CarSeat? = seats.find { it.occupant == null }

    fun addOccupant(character: Any): CarSeat? {
        val seat = findAvailableSeat()
        if (seat != null) {
            seat.occupant = character
            println("$character occupied a seat.")
        } else {
            println("No available seats in the car.")
        }
        return seat
    }

    fun removeOccupant(character: Any) {
        // Use identity check (===) to ensure we remove the exact object instance
        val seat = seats.find { it.occupant === character }
        if (seat != null) {
            seat.occupant = null
            println("$character vacated a seat.")
        }
    }

    private fun initializeAnimations() {
        // Only the default car has animations for now.
        if (carType != CarType.DEFAULT) return

        // 1. Create Idle Animation (the single default texture)
        animationSystem.createAnimation(
            name = "car_idle",
            texturePaths = arrayOf(carType.texturePath),
            frameDuration = 1f, // Duration doesn't matter for a single frame
            isLooping = true
        )

        // 2. Create Driving Animation (COMMENTED OUT)
        /*
        val drivingFrames = mutableListOf<String>()
        // The sequence is: standard -> driving_1 -> ... -> driving_9
        drivingFrames.add(carType.texturePath) // The standard "idle" frame
        for (i in 1..9) {
            // CORRECTED PATH:
            drivingFrames.add("textures/objects/cars/Default/animations/driving_$i.png")
        }
        animationSystem.createAnimation(
            name = "car_driving",
            texturePaths = drivingFrames.toTypedArray(),
            frameDuration = 0.05f, // Each frame lasts 0.06 seconds for a quick animation
            isLooping = true
        )
        */

        // 3. Start with the idle animation
        animationSystem.playAnimation("car_idle")
        lastTexture = animationSystem.getCurrentTexture()
    }

    fun setDrivingAnimationState(isDrivingHorizontally: Boolean) {
        /*
        if (carType != CarType.DEFAULT) return

        val targetAnimation = if (isDrivingHorizontally) "car_driving" else "car_idle"
        if (animationSystem.getCurrentAnimationName() != targetAnimation) {
            animationSystem.playAnimation(targetAnimation)
        }
        */
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

    fun updateAIControlled(deltaTime: Float, desiredMovement: Vector3, sceneManager: SceneManager, allCars: com.badlogic.gdx.utils.Array<GameCar>) {
        if (isDestroyed) {
            // Ensure headlight turns off if car is destroyed
            headlightLight?.let { it.intensity = 0f; it.updatePointLight() }
            setDrivingAnimationState(false)
            return
        }

        // AI Headlight
        val sunIntensity = sceneManager.game.lightingManager.getDayNightCycle().getSunIntensity()
        // AI turns lights on if it's dark and the car isn't wrecked.
        areHeadlightsOn = sunIntensity < 0.25f && !isDestroyed
        updateHeadlight(deltaTime)

        val originalPosition = position.cpy()
        val moveAmount = carType.speed * deltaTime // Speed can be based on car health or a fixed value

        // 1. Calculate desired movement from AI
        val deltaX = desiredMovement.x * moveAmount
        val deltaZ = desiredMovement.z * moveAmount
        val horizontalDirection = if (deltaX > 0) -1f else if (deltaX < 0) 1f else 0f

        updateFlipAnimation(horizontalDirection, deltaTime)
        setDrivingAnimationState(deltaX != 0f || deltaZ != 0f)

        // 2. Resolve X-axis movement
        if (deltaX != 0f) {
            val nextX = position.x + deltaX
            val supportY = sceneManager.findHighestSupportYForCar(nextX, position.z, carType.width / 2f, sceneManager.game.blockSize)
            if (supportY - position.y <= PlayerSystem.CAR_MAX_STEP_HEIGHT) {
                if (sceneManager.playerSystem.canCarMoveTo(Vector3(nextX, position.y, position.z), this, sceneManager, allCars)) {
                    position.x = nextX
                }
            }
        }

        // 3. Resolve Z-axis movement
        if (deltaZ != 0f) {
            val nextZ = position.z + deltaZ
            val supportY = sceneManager.findHighestSupportYForCar(position.x, nextZ, carType.width / 2f, sceneManager.game.blockSize)
            if (supportY - position.y <= PlayerSystem.CAR_MAX_STEP_HEIGHT) {
                if (sceneManager.playerSystem.canCarMoveTo(Vector3(position.x, position.y, nextZ), this, sceneManager, allCars)) {
                    position.z = nextZ
                }
            }
        }

        // 4. Resolve Y-axis movement (Gravity and Grounding)
        val finalSupportY = sceneManager.findHighestSupportYForCar(position.x, position.z, carType.width / 2f, sceneManager.game.blockSize)
        val effectiveSupportY = if (finalSupportY - originalPosition.y <= PlayerSystem.CAR_MAX_STEP_HEIGHT) finalSupportY else sceneManager.findHighestSupportYForCar(originalPosition.x, originalPosition.z, carType.width / 2f, sceneManager.game.blockSize)
        position.y = kotlin.math.max(effectiveSupportY, position.y - PlayerSystem.FALL_SPEED * deltaTime)

        // 5. Finalize
        if (!position.epsilonEquals(originalPosition, 0.001f)) {
            updateTransform()
        }
    }

    fun updateHeadlight(deltaTime: Float) {
        headlightLight?.let { light ->
            val targetIntensity = if (areHeadlightsOn) CarSystem.HEADLIGHT_INTENSITY else 0f
            // Smoothly fade the light on or off
            light.intensity = MathUtils.lerp(light.intensity, targetIntensity, deltaTime * 5f)

            // Calculate the forward direction based on the car's visual rotation
            val forwardX = if (visualRotationY >= 90f) 1f else -1f // Check if we are more left-facing than right-facing

            // Position the light in front of the car
            val headlightForwardOffset = 10f
            val headlightVerticalOffset = 2.0f
            val lightPosition = position.cpy().add(
                forwardX * headlightForwardOffset,
                headlightVerticalOffset,
                0f
            )

            light.position.set(lightPosition)
            light.updatePointLight()
        }
    }

    fun takeDamage(damage: Float, type: DamageType) {
        // Check for mission modifier
        val playerSystem = sceneManager.playerSystem
        val isPlayerDrivingThisCar = playerSystem.isDriving && playerSystem.getControlledEntityPosition() == this.position

        if (isPlayerDrivingThisCar && sceneManager.game.missionSystem.activeModifiers?.makePlayerVehicleInvincible == true) {
            println("${this.carType.displayName} is invincible due to mission modifier. Damage blocked.")
            return
        }

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

        val sceneManager = carSystem.sceneManager
        val playerSystem = sceneManager.playerSystem
        val distanceToPlayer = this.position.dst(playerSystem.getControlledEntityPosition())
        val maxShakeDistance = 50f // Explosions are felt from further away

        if (distanceToPlayer < maxShakeDistance) {
            // A powerful shake, similar to dynamite but slightly less intense
            val baseIntensity = 1.1f
            val baseDuration = 0.8f

            val intensity = baseIntensity * (1f - (distanceToPlayer / maxShakeDistance))
            val duration = baseDuration * (1f - (distanceToPlayer / maxShakeDistance))

            // Start the shake, ensuring it has at least a little intensity if player is at max range
            sceneManager.cameraManager.startShake(duration, intensity.coerceAtLeast(0.5f))
        }

        val explosionRadius = 15f  // The range of the explosion
        val baseDamage = 120f      // The damage at the very center of the explosion

        fun calculateFalloffDamage(distance: Float): Float {
            if (distance >= explosionRadius) return 0f
            // Damage decreases linearly from 100% to 0% based on distance
            val multiplier = 1.0f - (distance / explosionRadius)
            return baseDamage * multiplier
        }

        // Damage Player
        val damageToPlayer = calculateFalloffDamage(distanceToPlayer)
        if (damageToPlayer > 0) {
            println("Car explosion hits player for $damageToPlayer damage.")
            playerSystem.takeDamage(damageToPlayer)
        }

        // Damage Enemies
        sceneManager.activeEnemies.forEach { enemy ->
            val distanceToEnemy = this.position.dst(enemy.position)
            if (distanceToEnemy < explosionRadius) {
                val damageToEnemy = calculateFalloffDamage(distanceToEnemy)
                println("Car explosion hits ${enemy.enemyType.displayName} for $damageToEnemy damage.")
                if (enemy.takeDamage(damageToEnemy, DamageType.EXPLOSIVE, sceneManager) && enemy.currentState != AIState.DYING) {
                    sceneManager.enemySystem.startDeathSequence(enemy, sceneManager)
                }
            }
        }

        // Damage NPCs
        sceneManager.activeNPCs.forEach { npc ->
            val distanceToNPC = this.position.dst(npc.position)
            if (distanceToNPC < explosionRadius) {
                val damageToNPC = calculateFalloffDamage(distanceToNPC)
                println("Car explosion hits ${npc.npcType.displayName} for $damageToNPC damage.")
                if (npc.takeDamage(damageToNPC, DamageType.EXPLOSIVE, sceneManager) && npc.currentState != NPCState.DYING) {
                    sceneManager.npcSystem.startDeathSequence(npc, sceneManager)
                }
            }
        }

        val otherCars = com.badlogic.gdx.utils.Array(sceneManager.activeCars)
        for (otherCar in otherCars) {
            // Make sure a car doesn't damage itself in the explosion
            if (otherCar.id == this.id) continue

            val distanceToCar = this.position.dst(otherCar.position)
            if (distanceToCar < explosionRadius) {
                val damageToCar = calculateFalloffDamage(distanceToCar)
                otherCar.takeDamage(damageToCar, DamageType.EXPLOSIVE)
            }
        }

        for (seat in this.seats) {
            when (val occupant = seat.occupant) {
                is GameEnemy -> {
                    // Start the death sequence for the enemy
                    carSystem.sceneManager.enemySystem.startDeathSequence(occupant, carSystem.sceneManager)
                    occupant.exitCar() // This updates the enemy's state
                }
                is GameNPC -> {
                    // Start the death sequence for the NPC
                    carSystem.sceneManager.npcSystem.startDeathSequence(occupant, carSystem.sceneManager)
                    occupant.exitCar() // This updates the NPC's state
                }
            }
        }

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
            isFlipping = true // Set the flag to true because we are in motion
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
            isFlipping = false // Set the flag to false
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
                // Animation logic is now disabled
                /*
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
                 */
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
    val baseHealth: Float,
    val speed: Float = 10f
) {
    DEFAULT("Default", "textures/objects/cars/car_driving.png", 10f, 7f, 250f),
    BOSS_CAR("Boss Car", "textures/objects/cars/boss_car.png", 10f, 7f, 500f),
    TAXI("Taxi", "textures/objects/cars/taxi.png", 10f, 7f, 220f),
    TAXI_CAB("Taxi Cab", "textures/objects/cars/taxi_cab.png", 10f, 7f, 240f),
    NEWSPAPER_TRUCK("Newspaper Truck", "textures/objects/cars/newspaper_truck.png", 12f, 8f, 300f),
    CARGO_TRUCK("Cargo Truck", "textures/objects/cars/red_cargo.png", 12f, 5f, 400f),
    DARK_SEDAN("Dark Sedan", "textures/objects/cars/car_dark_gray.png", 10f, 7f, 250f),
    PARKED_CAR("Parked Car", "textures/objects/cars/car_parking.png", 10f, 7f, 250f),
    CASH_TRANSPORT("Cash Transport", "textures/objects/cars/cash_transport.png", 11f, 8f, 350f),
    CLASSIC_SEDAN("Classic Sedan", "textures/objects/cars/default_car.png", 10f, 7f, 260f),
    POLICE_CAR("Police Car", "textures/objects/cars/police_car.png", 10f, 7f, 250f),
    CASH_TRUCK("Cash Truck", "textures/objects/cars/cash_truck.png", 16f, 10f, 450f),
    LIMOUSINE("Limousine", "textures/objects/cars/limousine.png", 15f, 6.5f, 380f)
}
