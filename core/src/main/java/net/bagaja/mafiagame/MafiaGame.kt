package net.bagaja.mafiagame

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
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
    private lateinit var sceneManager: SceneManager
    private lateinit var roomTemplateManager: RoomTemplateManager
    private lateinit var houseSystem: HouseSystem

    // Highlight System
    private lateinit var highlightSystem: HighlightSystem

    // Transition System
    private lateinit var transitionSystem: TransitionSystem

    // 2D Player (but positioned in 3D space)
    private lateinit var playerSystem: PlayerSystem

    // Game objects
    private var lastPlacedInstance: Any? = null

    private lateinit var backgroundSystem: BackgroundSystem
    private lateinit var parallaxBackgroundSystem: ParallaxBackgroundSystem
    private lateinit var interiorSystem: InteriorSystem
    private var isPlacingExitDoorMode = false
    private var houseRequiringDoor: GameHouse? = null

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
        setupInteriorSystem()

        roomTemplateManager = RoomTemplateManager()
        roomTemplateManager.initialize()

        // Initialize Transition System
        transitionSystem = TransitionSystem()

        playerSystem = PlayerSystem()
        playerSystem.initialize(blockSize)

        sceneManager = SceneManager(
            playerSystem,
            blockSystem,
            objectSystem,
            itemSystem,
            interiorSystem,
            roomTemplateManager,
            cameraManager,
            transitionSystem,
            this
        )

        transitionSystem.create(cameraManager.findUiCamera())

        // Initialize UI Manager
        uiManager = UIManager(blockSystem, objectSystem, itemSystem, carSystem, houseSystem, backgroundSystem, parallaxBackgroundSystem, roomTemplateManager, interiorSystem)
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
            interiorSystem,
            sceneManager,
            roomTemplateManager,
            this::handleLeftClickAction,
            this::handleRightClickAndRemoveAction,
            this::handleFinePosMove
        )
        inputHandler.initialize()
        raycastSystem = RaycastSystem(blockSize)

        highlightSystem = HighlightSystem(blockSize)
        highlightSystem.initialize()

        // Pass the initial world data to the SceneManager
        sceneManager.initializeWorld(
            Array<GameBlock>(),
            Array<GameObject>(),
            Array<GameCar>(),
            Array<GameHouse>(),
            Array<GameItem>()
        )
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

    private fun setupInteriorSystem() {
        interiorSystem = InteriorSystem()
        interiorSystem.initialize()
    }

    private fun handlePlayerInput() {
        val deltaTime = Gdx.graphics.deltaTime

        // House Interaction Logic
        handleInteractionInput()

        if (cameraManager.isFreeCameraMode) {
            // Handle free camera movement
            cameraManager.handleInput(deltaTime)
        } else {
            // Handle player movement through PlayerSystem
            val moved = playerSystem.handleMovement(deltaTime, sceneManager.activeBlocks, sceneManager.activeHouses, sceneManager.activeInteriors)

            if (moved) {
                // Update camera manager with player position
                cameraManager.setPlayerPosition(playerSystem.getPosition())

                // Auto-switch to player camera when moving
                cameraManager.switchToPlayerCamera()
            }

            // Handle camera input for player camera mode
            cameraManager.handleInput(deltaTime)
        }
    }

    private fun handleInteractionInput() {
        // Prevent interaction while a transition is active
        if (sceneManager.isTransitioning()) {
            return
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            when (sceneManager.currentScene) {
                SceneType.WORLD -> {
                    // Try to enter a house
                    val door_level_y = 4.5f
                    val playerPos = playerSystem.getPosition()
                    val closestHouse = sceneManager.activeHouses.minByOrNull { it.position.dst(playerPos) }

                    if (closestHouse != null) {
                        // Check if the house is locked before proceeding
                        if (closestHouse.isLocked) {
                            println("This house is locked.")
                            // Here you could play a "locked door" sound or show a UI message
                            return // Stop the interaction
                        }

                        val doorPosition = Vector3(closestHouse.position.x, door_level_y, closestHouse.position.z)

                        if (playerPos.dst(doorPosition) < 8f) {
                            // Success! The player is close enough to the door.
                            sceneManager.transitionToInterior(closestHouse)
                        } else {
                            println("No house nearby to enter. (Too far from the door)")
                        }
                    } else {
                        println("No houses in the scene.")
                    }
                }
                SceneType.HOUSE_INTERIOR -> {
                    // If we are in the special placement mode, 'E' does nothing.
                    if (isPlacingExitDoorMode) {
                        println("You must place the designated exit door first!")
                        uiManager.setPersistentMessage("ACTION LOCKED: Place the EXIT DOOR to continue.")
                        return
                    }

                    // IMPROVED EXIT LOGIC
                    val currentHouse = sceneManager.getCurrentHouse()
                    if (currentHouse == null) {
                        println("Error: Cannot find current house data.")
                        return
                    }

                    val exitDoorId = currentHouse.exitDoorId
                    if (exitDoorId == null) {
                        println("This house has no designated exit! This shouldn't happen in normal gameplay.")
                        enterExitDoorPlacementMode(currentHouse)
                        return
                    }

                    // Find the one specific door that is the exit
                    val exitDoor = sceneManager.activeInteriors.find { it.id == exitDoorId }

                    if (exitDoor == null) {
                        println("Error: The designated exit door (ID: $exitDoorId) is missing from the room!")
                        return
                    }

                    val playerPos = playerSystem.getPosition()
                    val playerRadius = 1.5f // Half of player width from PlayerSystem

                    // Collision detection for doors
                    if (isPlayerNearDoor(playerPos, exitDoor)) {
                        println("Player is at the designated exit. Leaving...")
                        sceneManager.transitionToWorld()
                    } else {
                        val distance = playerPos.dst(exitDoor.position)
                        println("You are not close enough to the designated exit door. Distance: $distance")

                        // Debug: Print door position and player position
                        println("Door position: ${exitDoor.position}")
                        println("Player position: $playerPos")
                    }
                }
                else -> {}
            }
        }
    }

    private fun isPlayerNearDoor(playerPos: Vector3, door: GameInterior): Boolean {
        val playerBounds = playerSystem.getPlayerBounds()

        // Create a slightly expanded player bounds for door interaction
        val expandedBounds = BoundingBox(playerBounds)
        val expansion = 0.5f // Expand by 0.5 units in all directions
        expandedBounds.set(
            expandedBounds.min.sub(expansion, 0f, expansion),
            expandedBounds.max.add(expansion, 0f, expansion)
        )

        // Use the special door bounding box
        val doorBounds = door.getBoundingBoxForDoor()

        val intersects = expandedBounds.intersects(doorBounds)

        if (intersects) {
            println("Door collision detected using bounding box intersection!")
        } else {
            println("No door collision. Player bounds: ${expandedBounds.min} to ${expandedBounds.max}")
            println("Door bounds: ${doorBounds.min} to ${doorBounds.max}")
        }

        return intersects
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
                val bgRay = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
                placeBackground(bgRay)
                backgroundSystem.hidePreview()
            }
            UIManager.Tool.PARALLAX -> placeParallaxImage(ray)
            UIManager.Tool.INTERIOR -> placeInterior(ray)
        }
    }

    // Callback for InputHandler for right mouse click
    private fun handleRightClickAndRemoveAction(screenX: Int, screenY: Int): Boolean {
        val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())

        when (uiManager.selectedTool) {
            UIManager.Tool.BLOCK -> {
                val blockToRemove = raycastSystem.getBlockAtRay(ray, sceneManager.activeBlocks)
                if (blockToRemove != null) {
                    removeBlock(blockToRemove)
                    return true
                }
            }
            UIManager.Tool.OBJECT -> {
                if (objectSystem.currentSelectedObject == ObjectType.LIGHT_SOURCE) {
                    // Handle light source removal with proper 3D raycasting
                    val lightToRemove = raycastSystem.getLightSourceAtRay(ray, lightingManager)
                    if (lightToRemove != null) {
                        lightingManager.removeLightSource(lightToRemove.id)
                        objectSystem.removeLightSource(lightToRemove.id)
                        return true
                    }
                } else {
                    val objectToRemove = raycastSystem.getObjectAtRay(ray, sceneManager.activeObjects)
                    if (objectToRemove != null) {
                        removeObject(objectToRemove)
                        return true
                    }
                }
            }
            UIManager.Tool.ITEM -> {
                val itemToRemove = raycastSystem.getItemAtRay(ray, itemSystem)
                if (itemToRemove != null) {
                    // 1. Remove from the SceneManager's master list for this scene
                    sceneManager.activeItems.removeValue(itemToRemove, true)
                    // 2. Tell the ItemSystem to update its active list immediately
                    itemSystem.setActiveItems(sceneManager.activeItems)

                    println("Removed ${itemToRemove.itemType.displayName}")
                    return true
                }
            }
            UIManager.Tool.CAR -> {
                val carToRemove = raycastSystem.getCarAtRay(ray, sceneManager.activeCars)
                if (carToRemove != null) {
                    removeCar(carToRemove)
                    return true
                }
            }
            UIManager.Tool.HOUSE -> {
                val houseToRemove = raycastSystem.getHouseAtRay(ray, sceneManager.activeHouses)
                if (houseToRemove != null) {
                    removeHouse(houseToRemove)
                    return true
                }
            }
            UIManager.Tool.PLAYER -> return false
            UIManager.Tool.BACKGROUND -> {
                val backgroundToRemove = raycastSystem.getBackgroundAtRay(ray, backgroundSystem.getBackgrounds())
                if (backgroundToRemove != null) {
                    removeBackground(backgroundToRemove)
                    return true
                }
            }
            UIManager.Tool.PARALLAX -> {
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
            UIManager.Tool.INTERIOR -> {
                // Note: You will need to expose `activeInteriors` from your SceneManager
                val interiorToRemove = raycastSystem.getInteriorAtRay(ray, sceneManager.activeInteriors)
                if (interiorToRemove != null) {
                    removeInterior(interiorToRemove)
                    return true
                }
            }
        }
        return false
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

                // If it has an associated light, move that too.
                instance.associatedLightId?.let { lightId ->
                    val lightSource = lightingManager.getLightSources()[lightId]
                    if (lightSource != null) {
                        // The light's position must follow the object's position, including its offset.
                        val objectType = instance.objectType
                        lightSource.position.set(
                            instance.position.x,
                            instance.position.y + objectType.lightOffsetY,
                            instance.position.z
                        )
                        // Update the light's render data and models
                        lightSource.updateTransform()
                        lightSource.updatePointLight()
                    }
                }
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
            is GameInterior -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                instance.updateTransform()
                println("Moved Interior to ${instance.position}")
            }
            else -> println("Fine positioning not supported for this object type.")
        }
    }

    private fun calculateObjectYPosition(x: Float, z: Float, objectHeight: Float = 0f): Float {
        // Find the highest block at the given X,Z position
        var highestBlockY = 0f // Ground level

        for (gameBlock in sceneManager.activeBlocks) {
            val blockCenterX = gameBlock.position.x
            val blockCenterZ = gameBlock.position.z

            val tolerance = blockSize / 4f
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
        val hitBlock = raycastSystem.getBlockAtRay(ray, sceneManager.activeBlocks)

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
                val existingBlock = sceneManager.activeBlocks.find { gameBlock ->
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
            val existingBlock = sceneManager.activeBlocks.find { gameBlock ->
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
        sceneManager.activeBlocks.removeValue(blockToRemove, true)
        println("${blockToRemove.blockType.displayName} block removed at: ${blockToRemove.position}")
    }

    private fun removeObject(objectToRemove: GameObject) {
        objectSystem.removeGameObjectWithLight(objectToRemove, lightingManager)

        sceneManager.activeObjects.removeValue(objectToRemove, true)
        println("${objectToRemove.objectType.displayName} removed at: ${objectToRemove.position}")
    }

    private fun placePlayer(ray: Ray) {
        playerSystem.placePlayer(ray, sceneManager.activeBlocks, sceneManager.activeHouses, sceneManager.activeInteriors)
    }

    private fun placeObject(ray: Ray) {
        // First try to hit existing blocks
        val hitBlock = raycastSystem.getBlockAtRay(ray, sceneManager.activeBlocks)

        if (hitBlock != null) {
            placeObjectOnBlock(ray, hitBlock)
        } else {
            placeObjectOnGround(ray)
        }
    }

    private fun placeObjectOnBlock(ray: Ray, hitBlock: GameBlock) {
        when (objectSystem.currentSelectedObject) {
            ObjectType.LIGHT_SOURCE -> {
                // For light sources, place them on the block surface
                val lightPosition = Vector3(
                    hitBlock.position.x,
                    hitBlock.position.y + blockSize / 2, // On the block surface
                    hitBlock.position.z
                )
                placeLightSource(lightPosition)
            }
            else -> {
                // For other objects, place them on top of the block
                val objectPosition = Vector3(
                    hitBlock.position.x,
                    hitBlock.position.y + blockSize / 2, // On the block surface
                    hitBlock.position.z
                )

                val existingObject = sceneManager.activeObjects.find { gameObject ->
                    kotlin.math.abs(gameObject.position.x - objectPosition.x) < 1f &&
                        kotlin.math.abs(gameObject.position.z - objectPosition.z) < 1f
                }

                if (existingObject == null) {
                    addObject(objectPosition, objectSystem.currentSelectedObject)
                    println("${objectSystem.currentSelectedObject.displayName} placed on block at: ${objectPosition.x}, ${objectPosition.y}, ${objectPosition.z}")
                } else {
                    println("Object already exists near this position")
                }
            }
        }
    }

    private fun placeObjectOnGround(ray: Ray) {
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
                    val existingObject = sceneManager.activeObjects.find { gameObject ->
                        kotlin.math.abs(gameObject.position.x - gridX) < 1f &&
                            kotlin.math.abs(gameObject.position.z - gridZ) < 1f
                    }

                    if (existingObject == null) {
                        addObject(Vector3(gridX, properY, gridZ), objectSystem.currentSelectedObject)
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

    private fun addItemToScene(position: Vector3) {
        // 1. Use the ItemSystem as a factory to create a new item
        val newItem = itemSystem.createItem(position, itemSystem.currentSelectedItem)

        if (newItem != null) {
            // 2. Add the new item to the SceneManager's master list
            sceneManager.activeItems.add(newItem)
            // 3. Immediately sync the ItemSystem with the updated list
            itemSystem.setActiveItems(sceneManager.activeItems)

            lastPlacedInstance = newItem
            println("${newItem.itemType.displayName} placed in scene at: $position")
        }
    }

    // New function to place items
    private fun placeItem(ray: Ray) {
        val hitBlock = raycastSystem.getBlockAtRay(ray, sceneManager.activeBlocks)
        if (hitBlock != null) {
            // We hit a block directly - place item on top of it
            placeItemOnBlock(ray, hitBlock)
        } else {
            // No block hit, use the original ground plane method
            placeItemOnGround(ray)
        }
    }

    private fun placeItemOnBlock(ray: Ray, hitBlock: GameBlock) {
        // Calculate intersection point with the hit block
        val blockBounds = BoundingBox()
        blockBounds.set(
            Vector3(
                hitBlock.position.x - blockSize / 2,
                hitBlock.position.y - blockSize / 2,
                hitBlock.position.z - blockSize / 2
            ),
            Vector3(
                hitBlock.position.x + blockSize / 2,
                hitBlock.position.y + blockSize / 2,
                hitBlock.position.z + blockSize / 2
            )
        )

        val intersection = Vector3()
        if (com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, blockBounds, intersection)) {
            // Determine which face was hit
            val relativePos = Vector3(intersection).sub(hitBlock.position)

            // Find the dominant axis (which face was hit)
            val absX = kotlin.math.abs(relativePos.x)
            val absY = kotlin.math.abs(relativePos.y)
            val absZ = kotlin.math.abs(relativePos.z)

            val itemPosition = when {
                // Hit top face - place item on top
                absY >= absX && absY >= absZ && relativePos.y > 0 -> {
                    Vector3(
                        hitBlock.position.x,
                        hitBlock.position.y + blockSize / 2 + 1f, // 1f above the block surface
                        hitBlock.position.z
                    )
                }
                // Hit side faces - place item at the side but on the same level as block top
                else -> {
                    val blockTop = hitBlock.position.y + blockSize / 2
                    Vector3(intersection.x, blockTop + 1f, intersection.z)
                }
            }

            // Check if there's already an item too close to this position
            val existingItem = itemSystem.getItemAtPosition(itemPosition, 1.5f)

            if (existingItem == null) {
                addItemToScene(itemPosition)
            } else {
                println("Item already exists near this position")
            }
        }
    }

    private fun placeItemOnGround(ray: Ray) {
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
                addItemToScene(itemPosition)
            } else {
                println("Item already exists near this position")
            }
        }
    }

    private fun addObject(position: Vector3, objectType: ObjectType) {
        val newGameObject = objectSystem.createGameObjectWithLight(
            objectType = objectType,
            position = position,
            lightingManager = lightingManager
        )

        if (newGameObject != null) {
            // Apply initial transform to the model instance(s)
            newGameObject.modelInstance.transform.setTranslation(position)
            newGameObject.debugInstance?.transform?.setTranslation(position)

            sceneManager.activeObjects.add(newGameObject)
            lastPlacedInstance = newGameObject
            println("${objectType.displayName} placed at: ${position.x}, ${position.y}, ${position.z}")
        } else {
            println("Failed to create ${objectType.displayName}")
        }
    }

    private fun addBlock(x: Float, y: Float, z: Float, blockType: BlockType) {
        addBlockToCollection(x, y, z, blockType, sceneManager.activeBlocks)
    }

    private fun addBlockToCollection(x: Float, y: Float, z: Float, blockType: BlockType, collection: Array<GameBlock>) {
        val blockInstance = blockSystem.createBlockInstance(blockType) ?: return
        val blockHeight = blockSize * blockType.height
        val position = Vector3(x + blockSize / 2, y + blockHeight / 2, z + blockSize / 2)
        val gameBlock = GameBlock(blockInstance, blockType, position, blockSystem.currentBlockRotation)
        gameBlock.updateTransform()
        collection.add(gameBlock)
    }

    private fun addHouseToCollection(x: Float, y: Float, z: Float, houseType: HouseType, collection: Array<GameHouse>) {
        val houseInstance = houseSystem.createHouseInstance(houseType) ?: return
        val position = Vector3(x, y, z)
        houseInstance.transform.setToTranslationAndScaling(position, Vector3(6f, 6f, 6f))

        val canHaveRoom = houseType.canHaveRoom
        val isLocked = if (canHaveRoom) houseSystem.isNextHouseLocked else false
        val roomTemplateId = if (isLocked || !canHaveRoom) null else houseSystem.selectedRoomTemplateId

        val gameHouse = GameHouse(
            modelInstance = houseInstance,
            houseType = houseType,
            position = position,
            isLocked = isLocked,
            assignedRoomTemplateId = roomTemplateId,
            exitDoorId = null
        )
        collection.add(gameHouse)
        lastPlacedInstance = gameHouse

        println("Placed ${houseType.displayName}. Locked: ${gameHouse.isLocked}. Room Template ID: ${gameHouse.assignedRoomTemplateId ?: "None"}")
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
            val existingCar = sceneManager.activeCars.find { car ->
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
            sceneManager.activeCars.add(gameCar)
            lastPlacedInstance = gameCar
        }
    }

    private fun removeCar(carToRemove: GameCar) {
        sceneManager.activeCars.removeValue(carToRemove, true)
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
            val existingHouse = sceneManager.activeHouses.find { house ->
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
        addHouseToCollection(x, y, z, houseType, sceneManager.activeHouses)
    }

    private fun removeHouse(houseToRemove: GameHouse) {
        sceneManager.activeHouses.removeValue(houseToRemove, true)
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

    fun enterExitDoorPlacementMode(house: GameHouse) {
        println("Entering EXIT DOOR PLACEMENT mode for house ${house.id}")
        isPlacingExitDoorMode = true
        houseRequiringDoor = house

        // Force the UI and system to the DOOR_INTERIOR tool
        uiManager.selectedTool = UIManager.Tool.INTERIOR
        val doorIndex = InteriorType.entries.indexOf(InteriorType.DOOR_INTERIOR)
        if (doorIndex != -1) {
            interiorSystem.currentSelectedInteriorIndex = doorIndex
            interiorSystem.currentSelectedInterior = InteriorType.DOOR_INTERIOR
            uiManager.updateInteriorSelection() // Refresh the UI to show the door is selected
        }

        // Show a persistent message on the UI
        uiManager.setPersistentMessage("PLACE THE EXIT DOOR (Press J to see options)")
    }

    private fun placeInterior(ray: Ray) {
        // Interior placement only makes sense inside a house
        if (sceneManager.currentScene != SceneType.HOUSE_INTERIOR) {
            println("Can only place interiors inside a house.")
            return
        }

        if (isPlacingExitDoorMode) {
            if (interiorSystem.currentSelectedInterior != InteriorType.DOOR_INTERIOR) {
                println("You must place a DOOR to designate it as the exit.")
                uiManager.setPersistentMessage("ERROR: You must select and place a DOOR.")
                return
            }
        }

        val intersection = Vector3()
        val floorPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, floorPlane, intersection)) {
            addInterior(intersection, interiorSystem.currentSelectedInterior)
        }
    }

    private fun addInterior(position: Vector3, interiorType: InteriorType) {
        val newInterior = interiorSystem.createInteriorInstance(interiorType) ?: return

        newInterior.position.set(position)
        // Raise the object so its base is at the specified position
        newInterior.position.y += interiorType.height / 2f
        newInterior.rotation = interiorSystem.currentRotation
        newInterior.updateTransform()

        // Add to the scene manager's list of active interiors
        sceneManager.activeInteriors.add(newInterior)
        lastPlacedInstance = newInterior
        println("${interiorType.displayName} placed at: $position")

        // Assign the door and exit placement mode
        if (isPlacingExitDoorMode && interiorType == InteriorType.DOOR_INTERIOR) {
            houseRequiringDoor?.exitDoorId = newInterior.id
            println("SUCCESS: Door ${newInterior.id} assigned as exit for house ${houseRequiringDoor?.id}")

            // Exit the special mode
            isPlacingExitDoorMode = false
            houseRequiringDoor = null
            uiManager.clearPersistentMessage()
        }
    }

    private fun removeInterior(interiorToRemove: GameInterior) {
        sceneManager.activeInteriors.removeValue(interiorToRemove, true)
        println("${interiorToRemove.interiorType.displayName} removed at: ${interiorToRemove.position}")
    }

    override fun render() {
        // Clear screen
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)

        // Check if we are in an interior
        val isInInterior = sceneManager.currentScene == SceneType.HOUSE_INTERIOR

        // If in an interior, use a black background
        val clearColor = if (isInInterior) {
            Color.BLACK
        } else {
            // Get current sky color for clearing
            lightingManager.getCurrentSkyColor()
        }
        Gdx.gl.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        // Get delta time for this frame
        val deltaTime = Gdx.graphics.deltaTime
        val timeMultiplier = if (inputHandler.isTimeSpeedUpActive()) 200f else 1f

        sceneManager.update(deltaTime)
        transitionSystem.update(deltaTime)

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
            sceneManager.activeBlocks,
            sceneManager.activeObjects,
            sceneManager.activeCars,
            sceneManager.activeHouses,
            backgroundSystem,
            parallaxBackgroundSystem,
            itemSystem,
            objectSystem,
            raycastSystem,
            sceneManager.activeInteriors,
            interiorSystem,
        )

        //shaderProvider.setEnvironment(environment)
        //println("MafiaGame.render: Passing environment to provider, hash: ${environment.hashCode()}")

        parallaxBackgroundSystem.update(cameraManager.camera.position)

        val environment = lightingManager.getEnvironment()

        // Render 3D scene
        modelBatch.begin(cameraManager.camera)

        // Only render the sky and sun when we are in the outside world scene.
        if (!isInInterior) {
            // Render sky FIRST
            lightingManager.renderSky(modelBatch, cameraManager.camera)

            // Render sun
            lightingManager.renderSun(modelBatch, cameraManager.camera)
        }

        // Render parallax backgrounds
        parallaxBackgroundSystem.render(modelBatch, cameraManager.camera, environment)

        // Render all blocks
        for (gameBlock in sceneManager.activeBlocks) {
            modelBatch.render(gameBlock.modelInstance, environment)
        }

        // Render all objects
        for (gameObject in sceneManager.activeObjects) {
            gameObject.getRenderInstance(objectSystem.debugMode)?.let {
                modelBatch.render(it, environment)
            }
        }

        // Render light sources
        lightingManager.renderLightInstances(modelBatch, environment, objectSystem.debugMode)

        for (car in sceneManager.activeCars) {
            car.updateTransform()
            modelBatch.render(car.modelInstance, environment)
        }

        for (house in sceneManager.activeHouses) {
            modelBatch.render(house.modelInstance, environment)
        }

        // Render backgrounds
        for (background in backgroundSystem.getBackgrounds()) {
            modelBatch.render(background.modelInstance, environment)
        }

        for (interior in sceneManager.activeInteriors) {
            interior.render(modelBatch, environment) // This will only render the 3D ones
        }

        // Render background preview
        backgroundSystem.renderPreview(modelBatch, cameraManager.camera, environment)

        // Render 3D player with custom billboard shader
        playerSystem.render(cameraManager.camera, environment)

        // Render items
        itemSystem.render(cameraManager.camera, environment)

        modelBatch.end()

        interiorSystem.renderBillboards(cameraManager.camera, environment, sceneManager.activeInteriors)

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

        // Render the transition animation ON TOP of everything else.
        transitionSystem.render()
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
        interiorSystem.dispose()
        transitionSystem.dispose()

        // Dispose UIManager
        uiManager.dispose()
    }
}
