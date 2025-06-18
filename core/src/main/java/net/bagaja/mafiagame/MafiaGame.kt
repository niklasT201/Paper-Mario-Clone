package net.bagaja.mafiagame

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import kotlin.math.floor

class MafiaGame : ApplicationAdapter() {
    private lateinit var modelBatch: ModelBatch
    private lateinit var shaderProvider: BillboardShaderProvider
    private lateinit var spriteBatch: SpriteBatch
    private lateinit var cameraManager: CameraManager

    // UI and Input Managers
    private lateinit var uiManager: UIManager
    private lateinit var inputHandler: InputHandler

    // Raycast System
    private lateinit var raycastSystem: RaycastSystem

    // Block system
    private lateinit var blockSystem: BlockSystem
    private lateinit var objectSystem: ObjectSystem
    private lateinit var itemSystem: ItemSystem
    private lateinit var carSystem: CarSystem
    private val gameCars = Array<GameCar>()
    private val gameObjects = Array<GameObject>()
    private lateinit var houseSystem: HouseSystem
    private val gameHouses = Array<GameHouse>()

    // Highlight System
    private lateinit var highlightSystem: HighlightSystem

    // 2D Player (but positioned in 3D space)
    private lateinit var playerSystem: PlayerSystem

    // Game objects
    private val gameBlocks = Array<GameBlock>()
    private var lastPlacedInstance: Any? = null

    private lateinit var backgroundSystem: BackgroundSystem
    private lateinit var parallaxBackgroundSystem: ParallaxBackgroundSystem

    // Block size
    private val blockSize = 4f

    private lateinit var lightingManager: LightingManager

    override fun create() {
        setupGraphics()
        setupBlockSystem()
        setupObjectSystem()
        setupItemSystem()
        setupCarSystem()
        setupHouseSystem()
        setupBackgroundSystem()
        setupParallaxSystem()

        // Initialize UI Manager
        uiManager = UIManager(blockSystem, objectSystem, itemSystem, carSystem, houseSystem, backgroundSystem, parallaxBackgroundSystem)
        uiManager.initialize()

        // Initialize Input Handler
        inputHandler = InputHandler(
            uiManager,
            cameraManager,
            blockSystem,
            objectSystem,
            itemSystem,
            carSystem,
            houseSystem,
            backgroundSystem,
            parallaxBackgroundSystem,
            this::handleLeftClickAction,
            this::handleRightClickAndRemoveAction,
            this::handleFinePosMove
        )
        inputHandler.initialize()
        raycastSystem = RaycastSystem(blockSize)

        playerSystem = PlayerSystem()
        playerSystem.initialize(blockSize)

        highlightSystem = HighlightSystem(blockSize)
        highlightSystem.initialize()

        // initial test blocks
        addBlock(0f, 0f, 0f, BlockType.GRASS)
        addBlock(blockSize, 0f, 0f, BlockType.COBBLESTONE)
        addBlock(0f, 0f, blockSize, BlockType.ROOM_FLOOR)
    }

    private fun setupGraphics() {
        //shaderProvider = BillboardShaderProvider()
        //shaderProvider.setBlockCartoonySaturation(1.3f)
        //modelBatch = ModelBatch(shaderProvider)
        modelBatch = ModelBatch()
        spriteBatch = SpriteBatch()

        // Initialize camera manager
        cameraManager = CameraManager()
        cameraManager.initialize()

        // Initialize lighting manager
        lightingManager = LightingManager()
        lightingManager.initialize()
    }

    private fun setupBlockSystem() {
        blockSystem = BlockSystem()
        blockSystem.initialize(blockSize)
    }

    private fun setupObjectSystem() {
        objectSystem = ObjectSystem()
        objectSystem.initialize()
    }

    private fun setupItemSystem() {
        itemSystem = ItemSystem()
        itemSystem.initialize()
    }

    private fun setupCarSystem() {
        carSystem = CarSystem()
        carSystem.initialize()
    }

    private fun setupHouseSystem() {
        houseSystem = HouseSystem()
        houseSystem.initialize()
    }

    private fun setupBackgroundSystem() {
        backgroundSystem = BackgroundSystem()
        backgroundSystem.initialize()
    }

    private fun setupParallaxSystem() {
        parallaxBackgroundSystem = ParallaxBackgroundSystem()
        parallaxBackgroundSystem.initialize()
    }

    private fun handlePlayerInput() {
        val deltaTime = Gdx.graphics.deltaTime

        if (cameraManager.isFreeCameraMode) {
            // Handle free camera movement
            cameraManager.handleInput(deltaTime)
        } else {
            // Handle player movement through PlayerSystem
            val moved = playerSystem.handleMovement(deltaTime, gameBlocks, gameHouses)

            if (moved) {
                // Update camera manager with player position
                cameraManager.setPlayerPosition(playerSystem.getPosition())

                // Auto-switch to player camera when moving (optional)
                cameraManager.switchToPlayerCamera()
            }

            // Handle camera input for player camera mode
            cameraManager.handleInput(deltaTime)
        }
    }

    // Callback for InputHandler for left mouse click
    private fun handleLeftClickAction(screenX: Int, screenY: Int) {
        val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
        when (uiManager.selectedTool) {
            UIManager.Tool.BLOCK -> placeBlock(ray)
            UIManager.Tool.PLAYER -> placePlayer(ray)
            UIManager.Tool.OBJECT -> placeObject(ray)
            UIManager.Tool.ITEM -> placeItem(ray)
            UIManager.Tool.CAR -> placeCar(ray)
            UIManager.Tool.HOUSE -> placeHouse(ray)
            UIManager.Tool.BACKGROUND -> {
                val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
                placeBackground(ray)
                backgroundSystem.hidePreview() // Hide preview after placement
            }
            UIManager.Tool.PARALLAX -> placeParallaxImage(ray)
        }
    }

    // Callback for InputHandler for right mouse click
    private fun handleRightClickAndRemoveAction(screenX: Int, screenY: Int): Boolean {
        val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())

        when (uiManager.selectedTool) {
            UIManager.Tool.BLOCK -> {
                val blockToRemove = raycastSystem.getBlockAtRay(ray, gameBlocks)
                if (blockToRemove != null) {
                    removeBlock(blockToRemove)
                    return true
                }
            }
            UIManager.Tool.OBJECT -> {
                if (objectSystem.currentSelectedObject == ObjectType.LIGHT_SOURCE) {
                    // Handle light source removal with proper 3D raycasting
                    val lightToRemove = findLightSourceAtRay(ray)
                    if (lightToRemove != null) {
                        lightingManager.removeLightSource(lightToRemove.id)
                        objectSystem.removeLightSource(lightToRemove.id)
                        return true
                    }
                } else {
                    val objectToRemove = raycastSystem.getObjectAtRay(ray, gameObjects)
                    if (objectToRemove != null) {
                        removeObject(objectToRemove)
                        return true
                    }
                }
            }
            UIManager.Tool.PLAYER -> {
                return false
            }
            UIManager.Tool.ITEM -> {
                val itemToRemove = raycastSystem.getItemAtRay(ray, itemSystem)
                if (itemToRemove != null) {
                    itemSystem.removeItem(itemToRemove)
                    return true
                }
            }
            UIManager.Tool.CAR -> {
                val carToRemove = raycastSystem.getCarAtRay(ray, gameCars)
                if (carToRemove != null) {
                    removeCar(carToRemove)
                    return true
                }
            }
            UIManager.Tool.HOUSE -> {
                val houseToRemove = raycastSystem.getHouseAtRay(ray, gameHouses)
                if (houseToRemove != null) {
                    removeHouse(houseToRemove)
                    return true
                }
            }
            UIManager.Tool.BACKGROUND -> {
                val backgroundToRemove = raycastSystem.getBackgroundAtRay(ray, backgroundSystem.getBackgrounds())
                if (backgroundToRemove != null) {
                    removeBackground(backgroundToRemove)
                    return true
                }
            }
            UIManager.Tool.PARALLAX -> { // NEW
                val parallaxImageToRemove = raycastSystem.getParallaxImageAtRay(ray, parallaxBackgroundSystem)
                if (parallaxImageToRemove != null) {
                    // Use the parallax system's remove function with its required parameters
                    parallaxBackgroundSystem.removeImage(
                        parallaxImageToRemove.layerIndex,
                        parallaxImageToRemove.basePosition.x
                    )
                    return true
                }
            }
        }
        return false
    }

    private fun findLightSourceAtRay(ray: Ray): LightSource? {
        var closestLight: LightSource? = null
        var closestDistance = Float.MAX_VALUE

        val lightSources = lightingManager.getLightSources()

        for ((_, lightSource) in lightSources) {
            // Create a sphere around the light source position
            val lightCenter = Vector3(lightSource.position)
            val lightRadius = LightSource.LIGHT_SIZE // Use the light's size as radius

            // Check if ray intersects with sphere
            val intersection = Vector3()
            if (com.badlogic.gdx.math.Intersector.intersectRaySphere(ray, lightCenter, lightRadius, intersection)) {
                val distance = ray.origin.dst(intersection)

                if (distance < closestDistance) {
                    closestDistance = distance
                    closestLight = lightSource
                }
            }
        }

        return closestLight
    }

    private fun handleFinePosMove(deltaX: Float, deltaY: Float, deltaZ: Float) {
        if (lastPlacedInstance == null) {
            println("No object selected to move. Place an object first.")
            return
        }

        when (val instance = lastPlacedInstance) {
            is GameCar -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                instance.updateTransform() // Uses the car's specific update method
                println("Moved Car to ${instance.position}")
            }
            is GameObject -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                instance.modelInstance.transform.setTranslation(instance.position)
                instance.debugInstance?.transform?.setTranslation(instance.position)
                println("Moved Object to ${instance.position}")
            }
            is LightSource -> {
                // Use lighting manager for movement
                val moved = lightingManager.moveLightSource(instance.id, deltaX, deltaY, deltaZ)
                if (moved) {
                    instance.updateTransform()
                    println("Moved Light to ${instance.position}")
                }
            }
            is GameHouse -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                // Apply uniform scaling to all house types
                instance.modelInstance.transform.setToTranslationAndScaling(instance.position, Vector3(6f, 6f, 6f))
                println("Moved House to ${instance.position}")
            }
            is GameItem -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                println("Moved Item to ${instance.position}")
            }
            is GameBackground -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                instance.modelInstance.transform.setTranslation(instance.position)
                println("Moved Background to ${instance.position}")
            }
            else -> println("Fine positioning not supported for this object type.")
        }
    }

    private fun calculateObjectYPosition(x: Float, z: Float, objectHeight: Float = 0f): Float {
        // Find the highest block at the given X,Z position
        var highestBlockY = 0f // Ground level

        for (gameBlock in gameBlocks) {
            val blockCenterX = gameBlock.position.x
            val blockCenterZ = gameBlock.position.z

            // Check if this block is at the same grid position
            val tolerance = blockSize / 4f // Allow some tolerance for floating point precision
            if (kotlin.math.abs(blockCenterX - x) < tolerance &&
                kotlin.math.abs(blockCenterZ - z) < tolerance) {

                // Calculate the top of this block
                val blockHeight = blockSize * gameBlock.blockType.height
                val blockTop = gameBlock.position.y + blockHeight / 2f

                if (blockTop > highestBlockY) {
                    highestBlockY = blockTop
                }
            }
        }

        // Return the Y position where the object should be placed (on top of highest block + object height offset)
        return highestBlockY + objectHeight
    }

    private fun placeBlock(ray: Ray) {
        // First, try to find intersection with existing blocks
        val hitBlock = raycastSystem.getBlockAtRay(ray, gameBlocks)

        if (hitBlock != null) {
            // We hit an existing block, place new block adjacent to it
            placeBlockAdjacentTo(ray, hitBlock)
        } else {
            // No block hit, place on ground
            val intersection = Vector3()
            val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

            if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
                // Snap to grid
                val gridX = floor(intersection.x / blockSize) * blockSize
                val gridZ = floor(intersection.z / blockSize) * blockSize

                // Check if block already exists at this position
                val existingBlock = gameBlocks.find { gameBlock ->
                    kotlin.math.abs(gameBlock.position.x - (gridX + blockSize / 2)) < 0.1f &&
                        kotlin.math.abs(gameBlock.position.y - (blockSize / 2)) < 0.1f &&
                        kotlin.math.abs(gameBlock.position.z - (gridZ + blockSize / 2)) < 0.1f
                }

                if (existingBlock == null) {
                    addBlock(gridX, 0f, gridZ, blockSystem.currentSelectedBlock)
                    println("${blockSystem.currentSelectedBlock.displayName} block placed at: $gridX, 0, $gridZ")
                } else {
                    println("Block already exists at this position")
                }
            }
        }
    }

    private fun placeBlockAdjacentTo(ray: Ray, hitBlock: GameBlock) {
        // Calculate intersection point with the hit block
        val blockBounds = BoundingBox()
        blockBounds.set(
            Vector3(hitBlock.position.x - blockSize / 2, hitBlock.position.y - blockSize / 2, hitBlock.position.z - blockSize / 2),
            Vector3(hitBlock.position.x + blockSize / 2, hitBlock.position.y + blockSize / 2, hitBlock.position.z + blockSize / 2)
        )

        val intersection = Vector3()
        if (com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, blockBounds, intersection)) {
            // Determine which face was hit by finding the closest face
            val relativePos = Vector3(intersection).sub(hitBlock.position)
            var newX = hitBlock.position.x
            var newY = hitBlock.position.y
            var newZ = hitBlock.position.z

            // Find the dominant axis (which face was hit)
            val absX = kotlin.math.abs(relativePos.x)
            val absY = kotlin.math.abs(relativePos.y)
            val absZ = kotlin.math.abs(relativePos.z)

            when {
                // Hit top or bottom face
                absY >= absX && absY >= absZ -> newY += if (relativePos.y > 0) blockSize else -blockSize
                // Hit left or right face
                absX >= absY && absX >= absZ -> newX += if (relativePos.x > 0) blockSize else -blockSize
                // Hit front or back face
                else -> newZ += if (relativePos.z > 0) blockSize else -blockSize
            }
            val gridX = floor(newX / blockSize) * blockSize
            val gridY = floor(newY / blockSize) * blockSize
            val gridZ = floor(newZ / blockSize) * blockSize

            // Check if block already exists at this position
            val existingBlock = gameBlocks.find { gameBlock ->
                kotlin.math.abs(gameBlock.position.x - (gridX + blockSize / 2)) < 0.1f &&
                    kotlin.math.abs(gameBlock.position.y - (gridY + blockSize / 2)) < 0.1f &&
                    kotlin.math.abs(gameBlock.position.z - (gridZ + blockSize / 2)) < 0.1f
            }

            if (existingBlock == null) {
                addBlock(gridX, gridY, gridZ, blockSystem.currentSelectedBlock)
                println("${blockSystem.currentSelectedBlock.displayName} block placed adjacent at: $gridX, $gridY, $gridZ")
            } else {
                println("Block already exists at this position")
            }
        }
    }

    private fun removeBlock(blockToRemove: GameBlock) {
        gameBlocks.removeValue(blockToRemove, true)
        println("${blockToRemove.blockType.displayName} block removed at: ${blockToRemove.position}")
    }

    private fun removeObject(objectToRemove: GameObject) {
        // Remove light from lighting manager if it exists
        if (objectToRemove.pointLight != null) {
            val environment = lightingManager.getEnvironment()
            environment.remove(objectToRemove.pointLight)
            println("Light removed from environment")
        }

        gameObjects.removeValue(objectToRemove, true)
        println("${objectToRemove.objectType.displayName} removed at: ${objectToRemove.position}")
    }

    private fun placePlayer(ray: Ray) {
        playerSystem.placePlayer(ray, gameBlocks, gameHouses)
    }

    private fun placeObject(ray: Ray) {
        val intersection = Vector3()
        val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            when (objectSystem.currentSelectedObject) {
                ObjectType.LIGHT_SOURCE -> {
                    // Snap to grid and calculate proper Y position
                    val gridX = floor(intersection.x / blockSize) * blockSize + blockSize / 2
                    val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize / 2
                    val properY = calculateObjectYPosition(gridX, gridZ, 0f) // Light sources at block surface

                    placeLightSource(Vector3(gridX, properY, gridZ))
                }
                else -> {
                    val gridX = floor(intersection.x / blockSize) * blockSize + blockSize / 2
                    val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize / 2
                    val properY = calculateObjectYPosition(gridX, gridZ, 0f)

                    // Check if there's already an object at this position (optional)
                    val existingObject = gameObjects.find { gameObject ->
                        kotlin.math.abs(gameObject.position.x - gridX) < 1f &&
                            kotlin.math.abs(gameObject.position.z - gridZ) < 1f
                    }

                    if (existingObject == null) {
                        addObject(gridX, properY, gridZ, objectSystem.currentSelectedObject)
                        println("${objectSystem.currentSelectedObject.displayName} placed at: $gridX, $properY, $gridZ")
                    } else {
                        println("Object already exists near this position")
                    }
                }
            }
        }
    }

    private fun placeLightSource(position: Vector3) {
        // Check if light source already exists nearby
        val existingLight = lightingManager.getLightSourceAt(position, 2f)
        if (existingLight != null) {
            println("Light source already exists near this position")
            return
        }

        val settings = uiManager.getLightSourceSettings()
        val (intensity, range, color, rotX, rotY, rotZ) = settings

        // Create light source with rotation
        val lightSource = objectSystem.createLightSource(
            Vector3(position.x, position.y, position.z),
            intensity,
            range,
            color,
            rotX, rotY, rotZ  // Pass rotation values
        )

        // Create model instances
        val instances = objectSystem.createLightSourceInstances(lightSource)

        // Add to lighting manager
        lightingManager.addLightSource(lightSource, instances)

        lastPlacedInstance = lightSource
        println("Light source placed at: ${position.x}, ${position.y}, ${position.z}")
    }

    // New function to place items
    private fun placeItem(ray: Ray) {
        val intersection = Vector3()
        val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            // Items can be placed more freely, but still need to be on top of blocks
            val gridX = floor(intersection.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize / 2
            val properY = calculateObjectYPosition(gridX, gridZ, 1f) // Items float 1 unit above surface

            val itemPosition = Vector3(gridX, properY, gridZ)

            // Check if there's already an item too close to this position
            val existingItem = itemSystem.getItemAtPosition(itemPosition, 1.5f)

            if (existingItem == null) {
                // Capture the result of addItem
                val newItem = itemSystem.addItem(itemPosition, itemSystem.currentSelectedItem)
                // If it was created successfully, set it as the last placed instance
                if (newItem != null) {
                    lastPlacedInstance = newItem
                }
            } else {
                println("Item already exists near this position")
            }
        }
    }

    private fun addObject(x: Float, y: Float, z: Float, objectType: ObjectType) {
        val objectInstance = objectSystem.createObjectInstance(objectType)
        if (objectInstance != null) {
            val position = Vector3(x, y, z)
            objectInstance.transform.setTranslation(position)

            val gameObject = GameObject(objectInstance, objectType, position)

            // Create debug instance for invisible objects
            if (objectType.isInvisible) {
                val debugInstance = objectSystem.createDebugInstance(objectType)
                gameObject.debugInstance = debugInstance
                debugInstance?.transform?.setTranslation(position)
            }

            // Create and add light source if it's a light object
            if (objectType == ObjectType.LIGHT_SOURCE) {
                val light = gameObject.createLight()
                if (light != null) {
                    // Use lighting manager instead of direct environment/activeLights access
                    val environment = lightingManager.getEnvironment()
                    val currentLightCount = lightingManager.getActiveLightsCount()
                    val maxLights = lightingManager.getMaxLights()

                    if (currentLightCount < maxLights) {
                        environment.add(light)
                        println("Light source added at position: $position (Total lights: ${currentLightCount + 1})")
                    } else {
                        println("Warning: Maximum number of lights ($maxLights) reached!")
                    }
                } else {
                    println("Light object created, but light component is null or not added.")
                }
            }

            gameObjects.add(gameObject)
            lastPlacedInstance = gameObject
        }
    }

    private fun addBlock(x: Float, y: Float, z: Float, blockType: BlockType) {
        val blockInstance = blockSystem.createBlockInstance(blockType)
        if (blockInstance != null) {
            val blockHeight = blockSize * blockType.height
            val position = Vector3(x + blockSize/2, y + blockHeight/2, z + blockSize/2)

            // Create GameBlock with current rotation
            val gameBlock = GameBlock(
                blockInstance,
                blockType,
                position,
                blockSystem.currentBlockRotation  // Store the rotation used
            )

            // Set position and rotation using the GameBlock's method
            gameBlock.updateTransform()

            gameBlocks.add(gameBlock)
        }
    }

    private fun placeCar(ray: Ray) {
        val intersection = Vector3()
        val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            // Snap to grid
            val gridX = floor(intersection.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize / 2
            val properY = calculateObjectYPosition(gridX, gridZ, 0f) // Cars sit on block surface

            // Check if there's already a car at this position
            val existingCar = gameCars.find { car ->
                kotlin.math.abs(car.position.x - gridX) < 2f &&
                    kotlin.math.abs(car.position.z - gridZ) < 2f
            }

            if (existingCar == null) {
                addCar(gridX, properY, gridZ, carSystem.currentSelectedCar)
                println("${carSystem.currentSelectedCar.displayName} placed at: $gridX, $properY, $gridZ")
            } else {
                println("Car already exists near this position")
            }
        }
    }

    private fun addCar(x: Float, y: Float, z: Float, carType: CarType) {
        val carInstance = carSystem.createCarInstance(carType)
        if (carInstance != null) {
            val position = Vector3(x, y, z)
            val gameCar = GameCar(carInstance, carType, position, 0f) // 0f = facing north
            gameCars.add(gameCar)
            lastPlacedInstance = gameCar
        }
    }

    private fun removeCar(carToRemove: GameCar) {
        gameCars.removeValue(carToRemove, true)
        println("${carToRemove.carType.displayName} removed at: ${carToRemove.position}")
    }

    private fun placeHouse(ray: Ray) {
        val intersection = Vector3()
        val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            val gridX = floor(intersection.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize / 2
            val properY = calculateObjectYPosition(gridX, gridZ, 0f) // Houses sit on block surface

            // Check if there's already a house at this position
            val existingHouse = gameHouses.find { house ->
                kotlin.math.abs(house.position.x - gridX) < 3f &&
                    kotlin.math.abs(house.position.z - gridZ) < 3f
            }

            if (existingHouse == null) {
                addHouse(gridX, properY, gridZ, houseSystem.currentSelectedHouse)
                println("${houseSystem.currentSelectedHouse.displayName} placed at: $gridX, $properY, $gridZ")
            } else {
                println("House already exists near this position")
            }
        }
    }

    private fun addHouse(x: Float, y: Float, z: Float, houseType: HouseType) {
        val houseInstance = houseSystem.createHouseInstance(houseType)
        if (houseInstance != null) {
            val position = Vector3(x, y, z)

            // Scale up ALL houses uniformly
            houseInstance.transform.setToTranslationAndScaling(position, Vector3(6f, 6f, 6f))

            val gameHouse = GameHouse(houseInstance, houseType, position)
            gameHouses.add(gameHouse)
            lastPlacedInstance = gameHouse
        }
    }

    private fun removeHouse(houseToRemove: GameHouse) {
        gameHouses.removeValue(houseToRemove, true)
        println("${houseToRemove.houseType.displayName} removed at: ${houseToRemove.position}")
    }

    private fun placeBackground(ray: Ray) {
        val intersection = Vector3()
        val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            val gridX = floor(intersection.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize / 2
            val properY = calculateObjectYPosition(gridX, gridZ, 0f) // Backgrounds at block surface

            val newBackground = backgroundSystem.addBackground(
                gridX,
                properY,
                gridZ,
                backgroundSystem.currentSelectedBackground
            )

            if (newBackground != null) {
                lastPlacedInstance = newBackground
                println("${backgroundSystem.currentSelectedBackground.displayName} placed successfully at: $gridX, $properY, $gridZ")
            }
        }
    }

    private fun placeParallaxImage(ray: Ray) {
        // Parallax images are placed based on where the cursor intersects the ground plane.
        val intersection = Vector3()
        val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            val imageType = uiManager.getCurrentParallaxImageType()
            val layerIndex = uiManager.getCurrentParallaxLayer()

            // The x-position comes from the ray intersection with the ground.
            val xPosition = intersection.x

            // Attempt to add the image using the parallax system.
            val success = parallaxBackgroundSystem.addParallaxImage(imageType, xPosition, layerIndex)

            if (success) {
                println("Placed ${imageType.displayName} at X: $xPosition in layer $layerIndex")
                // Clear the last placed instance to prevent accidental fine-positioning of other objects
                lastPlacedInstance = null
            } else {
                println("Failed to place ${imageType.displayName}. Is there enough space?")
            }
        }
    }

    private fun removeBackground(backgroundToRemove: GameBackground) {
        backgroundSystem.removeBackground(backgroundToRemove)
        println("${backgroundToRemove.backgroundType.displayName} removed at: ${backgroundToRemove.position}")
    }

    override fun render() {
        // Clear screen
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)

        // Get current sky color for clearing
        val skyColor = lightingManager.getCurrentSkyColor()
        Gdx.gl.glClearColor(skyColor.r, skyColor.g, skyColor.b, skyColor.a)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        // Get delta time for this frame
        val deltaTime = Gdx.graphics.deltaTime
        val timeMultiplier = if (inputHandler.isTimeSpeedUpActive()) 200f else 1f

        // Update lighting manager
        lightingManager.update(deltaTime, cameraManager.camera.position, timeMultiplier)

        // Update input handler for continuous actions
        inputHandler.update(deltaTime)

        // Handle player input
        handlePlayerInput()
        playerSystem.update(deltaTime)

        // Update item system (animations, collisions, etc.)
        itemSystem.update(deltaTime, cameraManager.camera.position, playerSystem.getPosition(), 2f)

        // Update highlight system
        highlightSystem.update(
            cameraManager,
            uiManager,
            gameBlocks,
            gameObjects,
            gameCars,
            gameHouses,
            backgroundSystem,
            parallaxBackgroundSystem,
            itemSystem,
            objectSystem,
            raycastSystem
        )

        //shaderProvider.setEnvironment(environment)
        //println("MafiaGame.render: Passing environment to provider, hash: ${environment.hashCode()}")

        parallaxBackgroundSystem.update(cameraManager.camera.position)

        val environment = lightingManager.getEnvironment()

        // Render 3D scene
        modelBatch.begin(cameraManager.camera)

        // Render sky FIRST
        lightingManager.renderSky(modelBatch, cameraManager.camera)

        // Render sun
        lightingManager.renderSun(modelBatch, cameraManager.camera)

        // Render parallax backgrounds
        parallaxBackgroundSystem.render(modelBatch, cameraManager.camera, environment)

        // Render all blocks
        for (gameBlock in gameBlocks) {
            modelBatch.render(gameBlock.modelInstance, environment)
        }

        // Render all objects
        for (gameObject in gameObjects) {
            val renderInstance = gameObject.getRenderInstance(objectSystem.debugMode)
            if (renderInstance != null) {
                modelBatch.render(renderInstance, environment)
            }
        }

        // Render light sources
        lightingManager.renderLightInstances(modelBatch, environment, objectSystem.debugMode)

        for (car in gameCars) {
            car.updateTransform()
            modelBatch.render(car.modelInstance, environment)
        }

        for (house in gameHouses) {
            modelBatch.render(house.modelInstance, environment)
        }

        // Render backgrounds
        for (background in backgroundSystem.getBackgrounds()) {
            modelBatch.render(background.modelInstance, environment)
        }

        // Render background preview
        backgroundSystem.renderPreview(modelBatch, cameraManager.camera, environment)

        // Render 3D player with custom billboard shader
        playerSystem.render(cameraManager.camera, environment)

        // Render items
        itemSystem.render(cameraManager.camera, environment)

        modelBatch.end()

        // NEW: Render highlight using HighlightSystem
        highlightSystem.render(modelBatch, cameraManager.camera, environment)

        // Transition to 2D UI Rendering
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        // Render UI using UIManager
        uiManager.render()
    }

    override fun resize(width: Int, height: Int) {
        // Resize UIManager's viewport
        uiManager.resize(width, height)
        cameraManager.resize(width, height)
    }

    override fun dispose() {
        modelBatch.dispose()
        spriteBatch.dispose()
        blockSystem.dispose()
        objectSystem.dispose()
        itemSystem.dispose()
        carSystem.dispose()
        playerSystem.dispose()
        houseSystem.dispose()
        backgroundSystem.dispose()
        highlightSystem.dispose()
        lightingManager.dispose()
        parallaxBackgroundSystem.dispose()

        // Dispose UIManager
        uiManager.dispose()
    }
}
