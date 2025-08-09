package net.bagaja.mafiagame

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import kotlin.math.floor

class MafiaGame : ApplicationAdapter() {
    val isEditorMode = true
    val renderDistanceInChunks = 2
    private lateinit var modelBatch: ModelBatch
    private lateinit var shaderProvider: BillboardShaderProvider
    private lateinit var spriteBatch: SpriteBatch
    private lateinit var cameraManager: CameraManager
    lateinit var shaderEffectManager: ShaderEffectManager

    // UI and Input Managers
    lateinit var uiManager: UIManager
    private lateinit var inputHandler: InputHandler

    // Raycast System
    private lateinit var raycastSystem: RaycastSystem

    // Block system
    private lateinit var blockSystem: BlockSystem
    lateinit var objectSystem: ObjectSystem
    private lateinit var itemSystem: ItemSystem
    private lateinit var carSystem: CarSystem
    private lateinit var sceneManager: SceneManager
    private lateinit var enemySystem: EnemySystem
    private lateinit var npcSystem: NPCSystem
    private lateinit var roomTemplateManager: RoomTemplateManager
    private lateinit var houseSystem: HouseSystem

    // Highlight System
    private lateinit var highlightSystem: HighlightSystem
    private lateinit var lockIndicatorSystem: LockIndicatorSystem

    private lateinit var faceCullingSystem: FaceCullingSystem
    private lateinit var occlusionSystem: OcclusionSystem

    // Transition System
    private lateinit var transitionSystem: TransitionSystem

    // 2D Player (but positioned in 3D space)
    private lateinit var playerSystem: PlayerSystem

    private lateinit var spawnerSystem: SpawnerSystem

    // Game objects
    private var lastPlacedInstance: Any? = null

    private lateinit var backgroundSystem: BackgroundSystem
    private lateinit var parallaxBackgroundSystem: ParallaxBackgroundSystem
    private lateinit var interiorSystem: InteriorSystem
    private var isPlacingExitDoorMode = false
    private var houseRequiringDoor: GameHouse? = null

    private var showInvisibleBlockOutlines = false

    // Block size
    val blockSize = 4f

    lateinit var lightingManager: LightingManager
    private lateinit var particleSystem: ParticleSystem
    lateinit var teleporterSystem: TeleporterSystem
    lateinit var fireSystem: FireSystem
    private lateinit var bloodPoolSystem: BloodPoolSystem
    private lateinit var footprintSystem: FootprintSystem

    private val tempRay = Ray()
    private val tempVec3 = Vector3()
    private val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

    override fun create() {
        setupGraphics()
        particleSystem = ParticleSystem()
        particleSystem.initialize()
        setupBlockSystem()
        faceCullingSystem = FaceCullingSystem(blockSize)
        //occlusionSystem = OcclusionSystem(blockSize)
        setupObjectSystem()
        fireSystem = FireSystem()
        fireSystem.initialize()
        bloodPoolSystem = BloodPoolSystem()
        bloodPoolSystem.initialize()
        footprintSystem = FootprintSystem()
        footprintSystem.initialize()
        setupItemSystem()
        setupCarSystem()
        lockIndicatorSystem = LockIndicatorSystem()
        lockIndicatorSystem.initialize()
        setupHouseSystem()
        setupBackgroundSystem()
        setupParallaxSystem()
        setupInteriorSystem()

        enemySystem = EnemySystem()
        enemySystem.initialize()

        npcSystem = NPCSystem()
        npcSystem.initialize()

        roomTemplateManager = RoomTemplateManager()
        roomTemplateManager.initialize()

        // Initialize Transition System
        transitionSystem = TransitionSystem()

        playerSystem = PlayerSystem()
        playerSystem.initialize(blockSize, particleSystem, lightingManager, bloodPoolSystem, footprintSystem)

        // Initialize Shader Effect Manager
        shaderEffectManager = ShaderEffectManager()
        shaderEffectManager.initialize()
        spawnerSystem = SpawnerSystem(particleSystem, itemSystem)

        // Initialize UI Manager
        uiManager = UIManager(
            this,
            blockSystem,
            objectSystem,
            itemSystem,
            carSystem,
            houseSystem,
            backgroundSystem,
            parallaxBackgroundSystem,
            roomTemplateManager,
            interiorSystem,
            lightingManager,
            shaderEffectManager,
            enemySystem,
            npcSystem,
            particleSystem,
            spawnerSystem,
            this::removeSpawner
        )
        teleporterSystem = TeleporterSystem(objectSystem, uiManager)

        sceneManager = SceneManager(
            playerSystem,
            blockSystem,
            objectSystem,
            itemSystem,
            interiorSystem,
            enemySystem,
            npcSystem,
            roomTemplateManager,
            cameraManager,
            transitionSystem,
            faceCullingSystem,
            this,
            particleSystem,
            fireSystem
        )
        spawnerSystem.sceneManager = sceneManager
        sceneManager.teleporterSystem = teleporterSystem

        transitionSystem.create(cameraManager.findUiCamera())
        uiManager.initialize()

        // Initialize Input Handler
        inputHandler = InputHandler(
            this,
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
            enemySystem,
            npcSystem,
            particleSystem,
            spawnerSystem,
            teleporterSystem,
            sceneManager,
            roomTemplateManager,
            shaderEffectManager,
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
            Array<GameItem>(),
            Array<GameEnemy>(),
            Array<GameNPC>()
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
        cameraManager.game = this
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
        // MODIFIED: Pass the blockSize to the item system for physics calculations.
        itemSystem.initialize(blockSize)
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
            val allBlocks = sceneManager.activeChunkManager.getAllBlocks()

            // Now, call the function with the list it expects
            val moved = playerSystem.handleMovement(
                deltaTime,
                sceneManager,
                sceneManager.activeCars,
                particleSystem
            )

            if (moved) {
                val isDriving = playerSystem.isDriving
                // Update camera manager with player position
                cameraManager.setPlayerPosition(playerSystem.getControlledEntityPosition(), isDriving)

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
            // If the player is currently driving
            if (playerSystem.isDriving) {

                // Get all blocks from the chunk manager first
                val allBlocks = sceneManager.activeChunkManager.getAllBlocks()

                // Now, call the function with the list it expects
                playerSystem.exitCar(sceneManager)
                return
            }

            val playerPosForTeleport = playerSystem.getPosition()
            val closestTeleporter = teleporterSystem.findClosestTeleporter(playerPosForTeleport, 3f)

            if (closestTeleporter != null) {
                closestTeleporter.linkedTeleporterId?.let { destId ->
                    val destination = teleporterSystem.activeTeleporters.find { it.id == destId }
                    if (destination != null) {
                        // Step 1: Attempt to teleport and capture the result
                        val teleportSucceeded = playerSystem.teleportTo(destination.gameObject.position)

                        // Step 2: If it succeeded, update the camera
                        if (teleportSucceeded) {
                            // Use the camera's dedicated function to instantly snap to the player's new position
                            cameraManager.resetAndSnapToPlayer(playerSystem.getPosition(), playerSystem.isDriving)
                        }
                        return // Interaction handled, stop here.
                    }
                }
            }

            when (sceneManager.currentScene) {
                SceneType.WORLD -> {
                    val playerPos = playerSystem.getPosition()
                    val closestCar = sceneManager.activeCars.minByOrNull { it.position.dst(playerPos) }

                    // Check if a car is found and is close enough
                    if (closestCar != null && playerPos.dst(closestCar.position) < 8f) {
                        playerSystem.enterCar(closestCar)
                        return // Interaction handled, stop here.
                    }

                    // Try to enter a house
                    val closestHouse = sceneManager.activeHouses.minByOrNull { it.position.dst(playerPos) }

                    if (closestHouse != null) {
                        // First, check if the "house" is even enterable (e.g., not a stair model).
                        if (!closestHouse.houseType.canHaveRoom) {
                            return
                        }

                        // Check if the house is locked before proceeding
                        if (closestHouse.isLocked) {
                            println("This house is locked.")
                            // Here you could play a "locked door" sound or show a UI message
                            return // Stop the interaction
                        }

                        // We now calculate the HORIZONTAL distance, ignoring the Y-axis.
                        val playerPos2D = Vector2(playerPos.x, playerPos.z)
                        val housePos2D = Vector2(closestHouse.position.x, closestHouse.position.z)

                        // Check the 2D distance on the ground plane.
                        if (playerPos2D.dst(housePos2D) < 8f) {
                            // Success! The player is close enough horizontally.
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
        val newRay = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
        tempRay.set(newRay)
        val ray = tempRay
        when (uiManager.selectedTool) {
            UIManager.Tool.BLOCK -> placeBlock(ray)
            UIManager.Tool.PLAYER -> placePlayer(ray)
            UIManager.Tool.OBJECT -> {
                if (objectSystem.currentSelectedObject == ObjectType.SPAWNER) {
                    placeSpawner(ray)
                } else if (objectSystem.currentSelectedObject == ObjectType.FIRE_SPREAD) {
                    placeFire(ray)
                } else if (objectSystem.currentSelectedObject == ObjectType.TELEPORTER) {
                    placeTeleporter(ray)
                } else {
                    placeObject(ray)
                }
            }
            UIManager.Tool.ITEM -> placeItem(ray)
            UIManager.Tool.CAR -> placeCar(ray)
            UIManager.Tool.HOUSE -> placeHouse(ray)
            UIManager.Tool.BACKGROUND -> {
                val bgNewRay = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
                tempRay.set(bgNewRay)
                placeBackground(tempRay)
                backgroundSystem.hidePreview()
            }
            UIManager.Tool.PARALLAX -> placeParallaxImage(ray)
            UIManager.Tool.INTERIOR -> {
                placeInterior(ray)
                interiorSystem.hidePreview()
            }
            UIManager.Tool.ENEMY -> placeEnemy(ray)
            UIManager.Tool.NPC -> placeNPC(ray)
            UIManager.Tool.PARTICLE -> placeParticleEffect(ray)
        }
    }

    // Callback for InputHandler for right mouse click
    private fun handleRightClickAndRemoveAction(screenX: Int, screenY: Int): Boolean {
        val newRay = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
        tempRay.set(newRay)
        val ray = tempRay

        // Handle cancelling teleporter linking
        if (teleporterSystem.isLinkingMode) {
            teleporterSystem.cancelLinking()
            return true // Consume the click
        }

        when (uiManager.selectedTool) {
            UIManager.Tool.BLOCK -> {
                val blockToRemove = raycastSystem.getBlockAtRay(ray, sceneManager.activeChunkManager.getAllBlocks())
                if (blockToRemove != null) {
                    removeBlockArea(blockToRemove)
                    return true
                }
            }
            UIManager.Tool.OBJECT -> {
                val fireToRemove = fireSystem.activeFires.find { fire ->
                    raycastSystem.getObjectAtRay(ray, Array(arrayOf(fire.gameObject))) != null
                }
                if (fireToRemove != null) {
                    sceneManager.activeObjects.removeValue(fireToRemove.gameObject, true)
                    fireSystem.removeFire(fireToRemove, objectSystem, lightingManager)
                    return true
                }

                // Check for teleporter removal
                val teleporterGameObjects = Array(teleporterSystem.activeTeleporters.map { it.gameObject }.toTypedArray())
                val teleporterToRemove = raycastSystem.getObjectAtRay(ray, teleporterGameObjects)

                if (teleporterToRemove != null) {
                    val tp = teleporterSystem.activeTeleporters.find { it.gameObject.id == teleporterToRemove.id }
                    if (tp != null) {
                        teleporterSystem.removeTeleporter(tp)
                        return true
                    }
                }

                if (objectSystem.currentSelectedObject == ObjectType.LIGHT_SOURCE) {
                    // Handle light source removal with proper 3D raycasting
                    val lightToRemove = raycastSystem.getLightSourceAtRay(ray, lightingManager)
                    if (lightToRemove != null) {
                        lightingManager.removeLightSource(lightToRemove.id)
                        objectSystem.removeLightSource(lightToRemove.id)
                        return true
                    }
                } else {
                    // Try removing a normal object first
                    val objectToRemove = raycastSystem.getObjectAtRay(ray, sceneManager.activeObjects)
                    if (objectToRemove != null) {
                        removeObject(objectToRemove)
                        return true
                    }
                    // If no normal object found, try removing a spawner.
                    val spawnerToRemove = raycastSystem.getSpawnerAtRay(ray, sceneManager.activeSpawners)
                    if (spawnerToRemove != null && objectSystem.debugMode) {
                        removeSpawner(spawnerToRemove)
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
                val interiorToRemove = raycastSystem.getInteriorAtRay(ray, sceneManager.activeInteriors)
                if (interiorToRemove != null) {
                    removeInterior(interiorToRemove)
                    return true
                }
            }
            UIManager.Tool.ENEMY -> {
                val enemyToRemove = raycastSystem.getEnemyAtRay(ray, sceneManager.activeEnemies)
                if (enemyToRemove != null) {
                    removeEnemy(enemyToRemove)
                    return true
                }
            }
            UIManager.Tool.NPC -> {
                val npcToRemove = raycastSystem.getNPCAtRay(ray, sceneManager.activeNPCs)
                if (npcToRemove != null) {
                    removeNPC(npcToRemove)
                    return true
                }
            }
            UIManager.Tool.PARTICLE -> return false
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
            is GameFire -> {
                // Move the fire's underlying GameObject
                instance.gameObject.position.add(deltaX, deltaY, deltaZ)
                instance.gameObject.modelInstance.transform.setTranslation(instance.gameObject.position)

                // Also move the fire's associated light source
                instance.gameObject.associatedLightId?.let { lightId ->
                    val lightSource = lightingManager.getLightSources()[lightId]
                    if (lightSource != null) {
                        val objectType = instance.gameObject.objectType
                        lightSource.position.set(
                            instance.gameObject.position.x,
                            instance.gameObject.position.y + objectType.lightOffsetY,
                            instance.gameObject.position.z
                        )
                        lightSource.updateTransform()
                        lightSource.updatePointLight()
                    }
                }
                println("Moved Fire to ${instance.gameObject.position}")
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
                instance.updateTransform()
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
            is GameEnemy -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                instance.updateVisuals()
                println("Moved Enemy to ${instance.position}")
            }
            is GameNPC -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                instance.updateVisuals()
                println("Moved NPC to ${instance.position}")
            }
            is GameSpawner -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                instance.gameObject.position.set(instance.position)
                instance.gameObject.modelInstance.transform.setTranslation(instance.position)
                instance.gameObject.debugInstance?.transform?.setTranslation(instance.position)
                println("Moved Spawner to ${instance.position}")
            }
            is GameTeleporter -> {
                instance.gameObject.position.add(deltaX, deltaY, deltaZ)
                instance.gameObject.modelInstance.transform.setTranslation(instance.gameObject.position)
                instance.gameObject.debugInstance?.transform?.setTranslation(instance.gameObject.position)
                println("Moved Teleporter to ${instance.gameObject.position}")
            }
            else -> println("Fine positioning not supported for this object type.")
        }
    }

    private fun calculateObjectYPosition(x: Float, z: Float, objectHeight: Float = 0f): Float {
        // Find the highest block at the given X,Z position
        var highestBlockY = 0f // Ground level

        for (gameBlock in sceneManager.activeChunkManager.getBlocksInColumn(x, z)) {
            // Ignore blocks that don't have collision for height calculation
            if (!gameBlock.blockType.hasCollision) continue

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

    private fun placeBlockArea(cornerX: Float, cornerY: Float, cornerZ: Float) {
        val buildMode = blockSystem.currentBuildMode
        val blockType = blockSystem.currentSelectedBlock
        val size = buildMode.size

        // If it's just a 1x1 area
        if (size == 1) {
            val position = Vector3(cornerX + blockSize / 2, cornerY + (blockSize * blockType.height) / 2, cornerZ + blockSize / 2)
            // Check if block already exists at this position
            val existingBlock = sceneManager.activeChunkManager.getAllBlocks().find { gameBlock ->
                // This check might need to be more robust for different shapes and sizes
                gameBlock.position.dst(position) < 0.1f
            }
            if (existingBlock == null) {
                addBlock(cornerX, cornerY, cornerZ, blockType)
                println("${blockType.displayName} (${blockSystem.currentSelectedShape.getDisplayName()}) placed at: $cornerX, $cornerY, $cornerZ")
            } else {
                println("Block already exists at this position")
            }
            return
        }

        // For 3x3 or 5x5 areas
        val offset = (size - 1) / 2
        val isWall = buildMode.isWall

        for (i in 0 until size) {
            for (j in 0 until size) {
                // Calculate the grid coordinates (bottom-left corner) for the current block in the area
                val currentBlockX: Float
                val currentBlockY: Float
                val currentBlockZ: Float

                if (isWall) {
                    // Placing a wall (X, Y plane). The provided corner is the center block's corner.
                    currentBlockX = cornerX + (i - offset) * blockSize
                    currentBlockY = cornerY + (j - offset) * blockSize
                    currentBlockZ = cornerZ
                } else {
                    // Placing a floor (X, Z plane)
                    currentBlockX = cornerX + (i - offset) * blockSize
                    currentBlockY = cornerY
                    currentBlockZ = cornerZ + (j - offset) * blockSize
                }

                // Calculate the center position for the existence check
                val checkPosition = Vector3(
                    currentBlockX + blockSize / 2,
                    currentBlockY + (blockSize * blockType.height) / 2,
                    currentBlockZ + blockSize / 2
                )

                // Check if a block already exists at this position
                val existingBlock = sceneManager.activeChunkManager.getAllBlocks().find { gameBlock ->
                    gameBlock.position.dst(checkPosition) < 0.1f
                }

                if (existingBlock == null) {
                    // Pass the calculated corner coordinates to addBlock
                    addBlock(currentBlockX, currentBlockY, currentBlockZ, blockType)
                }
            }
        }
        val areaType = if (isWall) "Wall" else "Floor"
        println("${blockType.displayName} $size x $size $areaType placed around center corner: $cornerX, $cornerY, $cornerZ")
    }

    private fun placeBlock(ray: Ray) {
        val hitBlock = raycastSystem.getBlockAtRay(ray, sceneManager.activeChunkManager.getAllBlocks())

        if (hitBlock != null) {
            // We hit an existing block, place new block adjacent to it
            placeBlockAdjacentTo(ray, hitBlock)
        } else {
            // No block hit, place on ground
            if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
                // Snap to grid
                val gridX = floor(tempVec3.x / blockSize) * blockSize
                val gridZ = floor(tempVec3.z / blockSize) * blockSize
                placeBlockArea(gridX, 0f, gridZ)
            }
        }
    }

    private fun placeBlockAdjacentTo(ray: Ray, hitBlock: GameBlock) {
        // Calculate intersection point with the hit block
        val blockBounds = BoundingBox()
        hitBlock.getBoundingBox(blockSize, blockBounds)

        if (com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, blockBounds, tempVec3)) {
            // Determine which face was hit by finding the closest face
            val relativePos = Vector3(tempVec3).sub(hitBlock.position)
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

            placeBlockArea(gridX, gridY, gridZ)
        }
    }

    private fun removeBlock(blockToRemove: GameBlock) {
        sceneManager.removeBlock(blockToRemove)
        println("${blockToRemove.blockType.displayName} block removed at: ${blockToRemove.position}")
    }

    private fun removeBlockArea(centerBlock: GameBlock) {
        val buildMode = blockSystem.currentBuildMode
        val size = buildMode.size

        // If it's just a 1x1 area
        if (size == 1) {
            removeBlock(centerBlock)
            return
        }

        // For 3x3 or 5x5 areas
        val blocksToRemoveList = mutableListOf<GameBlock>()
        val centerPos = centerBlock.position
        val offset = (size - 1) / 2
        val isWall = buildMode.isWall

        for (i in 0 until size) {
            for (j in 0 until size) {
                // Calculate the target center position for each block in the area
                val targetX: Float
                val targetY: Float
                val targetZ: Float

                if (isWall) {
                    // Removing a wall area (X, Y plane), centered on the block that was clicked
                    targetX = centerPos.x + (i - offset) * blockSize
                    targetY = centerPos.y + (j - offset) * blockSize
                    targetZ = centerPos.z
                } else {
                    // Removing a floor area (X, Z plane)
                    targetX = centerPos.x + (i - offset) * blockSize
                    targetY = centerPos.y
                    targetZ = centerPos.z + (j - offset) * blockSize
                }
                val targetPos = Vector3(targetX, targetY, targetZ)

                // Find if a block exists at this target position
                val blockFound = sceneManager.activeChunkManager.getBlockAtWorld(targetPos)
                if (blockFound != null) {
                    blocksToRemoveList.add(blockFound)
                }
            }
        }

        if (blocksToRemoveList.isNotEmpty()) {
            for (blockToRemove in blocksToRemoveList) {
                removeBlock(blockToRemove)
            }
            println("Removed ${blocksToRemoveList.size} blocks in a $size x $size area.")
        }
    }

    private fun removeObject(objectToRemove: GameObject) {
        objectSystem.removeGameObjectWithLight(objectToRemove, lightingManager)

        sceneManager.activeObjects.removeValue(objectToRemove, true)
        println("${objectToRemove.objectType.displayName} removed at: ${objectToRemove.position}")
    }

    private fun placePlayer(ray: Ray) {
        playerSystem.placePlayer(ray, sceneManager)
    }

    private fun placeObject(ray: Ray) {
        // First try to hit existing blocks
        val hitBlock = raycastSystem.getBlockAtRay(ray, sceneManager.activeChunkManager.getAllBlocks())

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
      if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            when (objectSystem.currentSelectedObject) {
                ObjectType.LIGHT_SOURCE -> {
                    // Snap to grid and calculate proper Y position
                    val gridX = floor(tempVec3.x / blockSize) * blockSize + blockSize / 2
                    val gridZ = floor(tempVec3.z / blockSize) * blockSize + blockSize / 2
                    val properY = calculateObjectYPosition(gridX, gridZ, 0f) // Light sources at block surface

                    placeLightSource(Vector3(gridX, properY, gridZ))
                }
                else -> {
                    val gridX = floor(tempVec3.x / blockSize) * blockSize + blockSize / 2
                    val gridZ = floor(tempVec3.z / blockSize) * blockSize + blockSize / 2
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

        // Create light source with rotation
        val lightSource = objectSystem.createLightSource(
            position = Vector3(position.x, position.y, position.z),
            intensity = settings.intensity,
            range = settings.range,
            color = settings.color,
            rotationX = settings.rotationX,
            rotationY = settings.rotationY,
            rotationZ = settings.rotationZ,
            flickerMode = settings.flickerMode,
            loopOnDuration = settings.loopOnDuration,
            loopOffDuration = settings.loopOffDuration,
            timedFlickerLifetime = settings.timedFlickerLifetime
        )

        // Create model instances
        val instances = objectSystem.createLightSourceInstances(lightSource)

        // Add to lighting manager
        lightingManager.addLightSource(lightSource, instances)

        lastPlacedInstance = lightSource
        println("Light source placed at: ${position.x}, ${position.y}, ${position.z} with flicker mode: ${settings.flickerMode}")
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
        val hitBlock = raycastSystem.getBlockAtRay(ray, sceneManager.activeChunkManager.getAllBlocks())
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
                        hitBlock.position.y + blockSize / 2 + ItemSystem.ITEM_SURFACE_OFFSET, // 1f above the block surface
                        hitBlock.position.z
                    )
                }
                // Hit side faces - place item at the side but on the same level as block top
                else -> {
                    val blockTop = hitBlock.position.y + blockSize / 2
                    Vector3(intersection.x, blockTop + ItemSystem.ITEM_SURFACE_OFFSET, intersection.z)
                }
            }
            addItemToScene(itemPosition)
        }
    }

    private fun placeItemOnGround(ray: Ray) {
       if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            // Items can be placed more freely, but still need to be on top of blocks
            val gridX = floor(tempVec3.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(tempVec3.z / blockSize) * blockSize + blockSize / 2
            val properY = calculateObjectYPosition(gridX, gridZ, ItemSystem.ITEM_SURFACE_OFFSET) // Items float 1 unit above surface

            val itemPosition = Vector3(gridX, properY, gridZ)
            addItemToScene(itemPosition)
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
        val shape = blockSystem.currentSelectedShape
        val blockHeight = blockSize * blockType.height

        val position = when {
            blockType == BlockType.WATER -> Vector3(x + blockSize / 2, y + blockHeight, z + blockSize / 2)
            shape == BlockShape.SLAB_BOTTOM -> Vector3(x + blockSize / 2, y + blockHeight / 4, z + blockSize / 2)
            shape == BlockShape.SLAB_TOP -> Vector3(x + blockSize / 2, y + (blockHeight * 0.75f), z + blockSize / 2)
            else -> Vector3(x + blockSize / 2, y + blockHeight / 2, z + blockSize / 2)
        }

        val gameBlock = blockSystem.createGameBlock(
            type = blockType,
            shape = shape,
            position = position,
            geometryRotation = blockSystem.currentGeometryRotation,
            textureRotation = blockSystem.currentTextureRotation,
            topTextureRotation = blockSystem.currentTopTextureRotation
        )

        // The only call needed now
        sceneManager.addBlock(gameBlock)
    }

    private fun addHouseToCollection(x: Float, y: Float, z: Float, houseType: HouseType, collection: Array<GameHouse>) {
        val houseInstance = houseSystem.createHouseInstance(houseType) ?: return
        val position = Vector3(x, y, z)

        val canHaveRoom = houseType.canHaveRoom
        val isLocked = if (canHaveRoom) houseSystem.isNextHouseLocked else false
        val roomTemplateId = if (isLocked || !canHaveRoom) null else houseSystem.selectedRoomTemplateId

        val gameHouse = GameHouse(
            modelInstance = houseInstance,
            houseType = houseType,
            position = position,
            isLocked = isLocked,
            assignedRoomTemplateId = roomTemplateId,
            exitDoorId = null,
            rotationY = houseSystem.currentRotation
        )
        gameHouse.updateTransform()

        collection.add(gameHouse)
        lastPlacedInstance = gameHouse

        println("Placed ${houseType.displayName}. Locked: ${gameHouse.isLocked}. Room Template ID: ${gameHouse.assignedRoomTemplateId ?: "None"}")
    }

    private fun placeCar(ray: Ray) {
        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            // Snap to grid
            val gridX = floor(tempVec3.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(tempVec3.z / blockSize) * blockSize + blockSize / 2
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
            val gameCar = GameCar(carInstance, carType, position, 0f, carSystem.isNextCarLocked) // 0f = facing north
            //gameCar.initializeAnimations() ANIMATION
            sceneManager.activeCars.add(gameCar)
            lastPlacedInstance = gameCar
            println("Placed ${carType.displayName}. Locked: ${gameCar.isLocked}")
        }
    }

    private fun removeCar(carToRemove: GameCar) {
        sceneManager.activeCars.removeValue(carToRemove, true)
        //carToRemove.dispose() // Dispose the car's animation resources ANIMATION
        println("${carToRemove.carType.displayName} removed at: ${carToRemove.position}")
    }

    private fun placeHouse(ray: Ray) {
        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            val gridX = floor(tempVec3.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(tempVec3.z / blockSize) * blockSize + blockSize / 2
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
        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            val gridX = floor(tempVec3.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(tempVec3.z / blockSize) * blockSize + blockSize / 2
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
        // Parallax images are placed based on where the cursor intersects the ground plane
        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            val imageType = uiManager.getCurrentParallaxImageType()
            val layerIndex = uiManager.getCurrentParallaxLayer()

            // The x-position comes from the ray intersection with the ground.
            val xPosition = tempVec3.x

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

    fun toggleInvisibleBlockOutlines() {
        showInvisibleBlockOutlines = !showInvisibleBlockOutlines
        val status = if (showInvisibleBlockOutlines) "ON" else "OFF"
        uiManager.updatePlacementInfo("Invisible Block Outlines: $status")
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

        val floorPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, floorPlane, tempVec3)) {
            addInterior(tempVec3, interiorSystem.currentSelectedInterior)
        }
    }

    private fun addInterior(position: Vector3, interiorType: InteriorType) {
        val newInterior = interiorSystem.createInteriorInstance(interiorType) ?: return

        newInterior.position.set(position)
        // Raise the object based on its type
        if (interiorType.isFloorObject) {
            // For carpets
            newInterior.position.y += 0.01f
        } else {
            // For regular objects
            newInterior.position.y += interiorType.height / 2f
        }
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

    private fun placeEnemy(ray: Ray) {
        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            val surfaceY = findHighestSurfaceYAt(tempVec3.x, tempVec3.z)

            // Position the enemy so its feet are on the surface
            val enemyType = enemySystem.currentSelectedEnemyType
            val enemyPosition = Vector3(tempVec3.x, surfaceY + enemyType.height / 2f, tempVec3.z)

            val newEnemy = enemySystem.createEnemy(
                enemyPosition,
                enemySystem.currentSelectedEnemyType,
                enemySystem.currentSelectedBehavior
            )

            if (newEnemy != null) {
                sceneManager.activeEnemies.add(newEnemy)
                lastPlacedInstance = newEnemy // For fine positioning
                println("Placed ${newEnemy.enemyType.displayName} with ${newEnemy.behaviorType.displayName} behavior at $enemyPosition")
            }
        }
    }

    private fun removeEnemy(enemyToRemove: GameEnemy) {
        sceneManager.activeEnemies.removeValue(enemyToRemove, true)
        println("Removed ${enemyToRemove.enemyType.displayName} at: ${enemyToRemove.position}")
    }

    private fun placeNPC(ray: Ray) {
        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            val surfaceY = findHighestSurfaceYAt(tempVec3.x, tempVec3.z)

            // Position the NPC so its feet are on the surface.
            val npcType = npcSystem.currentSelectedNPCType
            val npcPosition = Vector3(tempVec3.x, surfaceY + npcType.height / 2f, tempVec3.z)

            val newNPC = npcSystem.createNPC(
                npcPosition,
                npcSystem.currentSelectedNPCType,
                npcSystem.currentSelectedBehavior
            )

            if (newNPC != null) {
                sceneManager.activeNPCs.add(newNPC)
                lastPlacedInstance = newNPC
                println("Placed ${newNPC.npcType.displayName} with ${newNPC.behaviorType.displayName} behavior at $npcPosition")
            }
        }
    }

    private fun removeNPC(npcToRemove: GameNPC) {
        sceneManager.activeNPCs.removeValue(npcToRemove, true)
        println("Removed ${npcToRemove.npcType.displayName} at: ${npcToRemove.position}")
    }

    private fun placeParticleEffect(ray: Ray) {
        var hitPoint: Vector3? = null
        var hitNormal: Vector3? = null // To orient effects like impacts

        val hitBlock = raycastSystem.getBlockAtRay(ray, sceneManager.activeChunkManager.getAllBlocks())
        if (hitBlock != null) {
            val blockBounds = hitBlock.getBoundingBox(blockSize, BoundingBox())
            if (com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, blockBounds, tempVec3)) {
                hitPoint = tempVec3.cpy()
                // A simple way to get the hit normal
                val relativePos = Vector3(tempVec3).sub(hitBlock.position)
                val absX = kotlin.math.abs(relativePos.x)
                val absY = kotlin.math.abs(relativePos.y)
                val absZ = kotlin.math.abs(relativePos.z)
                hitNormal = when {
                    absY > absX && absY > absZ -> Vector3(0f, if (relativePos.y > 0) 1f else -1f, 0f)
                    absX > absY && absX > absZ -> Vector3(if (relativePos.x > 0) 1f else -1f, 0f, 0f)
                    else -> Vector3(0f, 0f, if (relativePos.z > 0) 1f else -1f)
                }
            }
        }

        // If no object was hit, intersect with the ground plane
        if (hitPoint == null) {
            if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
                hitPoint = tempVec3.cpy()
                hitNormal = Vector3.Y // Normal is straight up
            }
        }

        // Spawn the effect if we found a point
        hitPoint?.let { pos ->
            val effectType = particleSystem.currentSelectedEffect
            val isGunSmokeEffect = effectType == ParticleEffectType.GUN_SMOKE_INITIAL ||
                effectType == ParticleEffectType.GUN_SMOKE_BURST_1 ||
                effectType == ParticleEffectType.GUN_SMOKE_BURST_2 ||
                effectType == ParticleEffectType.GUN_SMOKE_BURST_3 ||
                effectType == ParticleEffectType.GUN_SMOKE_DENSE ||
                effectType == ParticleEffectType.GUN_SMOKE_WISPY ||
                effectType == ParticleEffectType.GUN_SMOKE_FINAL ||
                effectType == ParticleEffectType.GUN_SMOKE_DISSIPATING ||
                effectType == ParticleEffectType.GUN_SMOKE_THIN
            val direction = if (isGunSmokeEffect) ray.direction else hitNormal

            // Pass the surface normal for ground-oriented effects
            particleSystem.spawnEffect(effectType, pos, direction, hitNormal)
            println("Spawned ${effectType.displayName} at $pos")
        }
    }

    private fun placeSpawner(ray: Ray) {
        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            val surfaceY = findHighestSurfaceYAt(tempVec3.x, tempVec3.z)
            val spawnerPosition = Vector3(tempVec3.x, surfaceY, tempVec3.z)

            val spawnerGameObject = objectSystem.createGameObjectWithLight(ObjectType.SPAWNER, spawnerPosition.cpy())

            if (spawnerGameObject != null) {
                // Manually set the transform of the visible debug model to the spawn position
                spawnerGameObject.debugInstance?.transform?.setTranslation(spawnerPosition)

                val newSpawner = GameSpawner(
                    position = spawnerPosition.cpy(),
                    gameObject = spawnerGameObject
                )
                sceneManager.activeSpawners.add(newSpawner)
                lastPlacedInstance = newSpawner // For fine positioning
                println("Placed a new generic Spawner at $spawnerPosition")

                // Immediately open the UI to configure the new spawner
                uiManager.showSpawnerUI(newSpawner)
            }
        }
    }

    private fun removeSpawner(spawner: GameSpawner) {
        sceneManager.activeSpawners.removeValue(spawner, true)
        sceneManager.activeObjects.removeValue(spawner.gameObject, true)
        println("Removed Spawner at ${spawner.position}")
    }

    private fun placeFire(ray: Ray) {
        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            val surfaceY = findHighestSurfaceYAt(tempVec3.x, tempVec3.z)
            val firePosition = Vector3(tempVec3.x, surfaceY, tempVec3.z)

            val newFire = fireSystem.addFire(firePosition, objectSystem, lightingManager)
            if (newFire != null) {
                sceneManager.activeObjects.add(newFire.gameObject)
                lastPlacedInstance = newFire
                println("Placed Spreading Fire object.")
            }
        }
    }

    private fun placeTeleporter(ray: Ray) {
        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            val newTeleporter = teleporterSystem.addTeleporterAt(tempVec3) ?: return

            lastPlacedInstance = newTeleporter

            if (teleporterSystem.isLinkingMode) {
                teleporterSystem.completeLinking(newTeleporter)
            } else {
                teleporterSystem.startLinking(newTeleporter)
            }
        }
    }

    override fun render() {
        // Begin capturing the frame for post-processing
        shaderEffectManager.beginCapture()

        // Clear screen
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)

        // Check if we are in an interior
        val currentSceneType = sceneManager.currentScene
        val isInInterior = currentSceneType == SceneType.HOUSE_INTERIOR || currentSceneType == SceneType.TRANSITIONING_TO_WORLD

        // If in an interior, use a black background
        val clearColor = if (isInInterior) {
            Color.BLACK
        } else {
            // Get current sky color for clearing
            lightingManager.getCurrentSkyColor()
        }
        Gdx.gl.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        val isPaused = uiManager.isPauseMenuVisible()
        if (!isPaused) {
            // Get delta time for this frame
            val deltaTime = Gdx.graphics.deltaTime
            val timeMultiplier = if (inputHandler.isTimeSpeedUpActive()) 200f else 1f

            val isSinCityEffect = shaderEffectManager.isEffectsEnabled &&
                shaderEffectManager.getCurrentEffect() == ShaderEffect.SIN_CITY
            lightingManager.setGrayscaleMode(isSinCityEffect)

            sceneManager.update(deltaTime)
            transitionSystem.update(deltaTime)

            // Update lighting manager
            lightingManager.update(deltaTime, cameraManager.camera.position, timeMultiplier)

            val expiredLightIds = lightingManager.collectAndClearExpiredLights()
            if (expiredLightIds.isNotEmpty()) {
                expiredLightIds.forEach { id ->
                    // Also remove it from the object system so it doesn't leave a ghost object
                    objectSystem.removeLightSource(id)
                }
            }

            // Update input handler for continuous actions
            inputHandler.update(deltaTime)

            // Handle player input
            handlePlayerInput()
            particleSystem.update(deltaTime)
            spawnerSystem.update(deltaTime, sceneManager.activeSpawners, playerSystem.getPosition())
            val expiredFires = fireSystem.update(Gdx.graphics.deltaTime, playerSystem, particleSystem, sceneManager)
            if (expiredFires.isNotEmpty()) {
                for (fireToRemove in expiredFires) {
                    sceneManager.activeObjects.removeValue(fireToRemove.gameObject, true)
                    fireSystem.removeFire(fireToRemove, objectSystem, lightingManager)
                }
            }

            playerSystem.update(deltaTime, sceneManager)
            enemySystem.update(deltaTime, playerSystem, sceneManager, blockSize)
            npcSystem.update(deltaTime, playerSystem, sceneManager, blockSize)
            bloodPoolSystem.update(deltaTime, sceneManager.activeBloodPools)
            footprintSystem.update(deltaTime, sceneManager.activeFootprints)

            // Handle car destruction and removals
            val carIterator = sceneManager.activeCars.iterator()
            while (carIterator.hasNext()) {
                val car = carIterator.next()

                // 1. Check if a drivable car should be destroyed
                if (car.state == CarState.DRIVABLE && car.health <= 0) {
                    // If the player is driving this car, kick them out
                    if (playerSystem.isDriving && playerSystem.getControlledEntityPosition() == car.position) {
                        playerSystem.exitCar(sceneManager)
                    }
                    val shouldSpawnFire = car.lastDamageType != DamageType.FIRE

                    // Trigger the destruction sequence
                    val newFireObjects = car.destroy(
                        particleSystem,
                        carSystem,
                        shouldSpawnFire,
                        fireSystem,
                        objectSystem,
                        lightingManager
                    )

                    sceneManager.activeObjects.addAll(newFireObjects)
                }

                // 2. Check if a faded-out car should be removed
                if (car.isReadyForRemoval) {
                    // If player is inside car
                    if (playerSystem.isDriving && playerSystem.getControlledEntityPosition() == car.position) {
                        playerSystem.exitCar(sceneManager)
                    }

                    carIterator.remove()
                    println("Removed wrecked car from scene: ${car.carType.displayName}")
                }
            }

            // Update the lock indicator based on player, car, and house positions
            lockIndicatorSystem.update(playerSystem.getPosition(), sceneManager.activeCars, sceneManager.activeHouses)

            // Update item system (animations, collisions, etc.)
            itemSystem.update(deltaTime, cameraManager.camera, playerSystem, sceneManager)

            sceneManager.activeChunkManager.processDirtyChunks()
        }

        // Update highlight system
        if (isEditorMode && !isPaused) {
            highlightSystem.update(
                cameraManager,
                uiManager,
                blockSystem,
                sceneManager.activeChunkManager.getAllBlocks(),
                sceneManager.activeObjects,
                sceneManager.activeSpawners,
                sceneManager.activeCars,
                sceneManager.activeHouses,
                backgroundSystem,
                parallaxBackgroundSystem,
                itemSystem,
                objectSystem,
                raycastSystem,
                sceneManager.activeInteriors,
                interiorSystem,
                sceneManager.activeEnemies,
                sceneManager.activeNPCs,
                particleSystem
            )
        }

        //shaderProvider.setEnvironment(environment)
        //println("MafiaGame.render: Passing environment to provider, hash: ${environment.hashCode()}")

        parallaxBackgroundSystem.update(cameraManager.camera.position)
        //occlusionSystem.update(cameraManager.camera, playerSystem.getPosition(), sceneManager.activeBlocks)

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
        sceneManager.activeChunkManager.render(modelBatch, environment, cameraManager.camera)

        // Render Blood Pool
        bloodPoolSystem.render(cameraManager.camera, environment, sceneManager.activeBloodPools)
        footprintSystem.render(cameraManager.camera, environment, sceneManager.activeFootprints)

        // Render all objects
        for (gameObject in sceneManager.activeObjects) {
            if (gameObject.objectType != ObjectType.FIRE_SPREAD) {
                gameObject.getRenderInstance(objectSystem.debugMode)?.let {
                    modelBatch.render(it, environment)
                }
            }
        }

        // Render teleporter pads
        for (teleporter in teleporterSystem.activeTeleporters) {
            teleporter.gameObject.getRenderInstance(objectSystem.debugMode)?.let {
                modelBatch.render(it, environment)
            }
        }

        for (spawner in sceneManager.activeSpawners) {
            spawner.gameObject.getRenderInstance(objectSystem.debugMode)?.let {
                modelBatch.render(it, environment)
            }
        }

        // Render light sources
        lightingManager.renderLightInstances(modelBatch, environment, objectSystem.debugMode)

        for (house in sceneManager.activeHouses) {
            modelBatch.render(house.modelInstance, environment)
        }

        // Render backgrounds
        for (background in backgroundSystem.getBackgrounds()) {
            modelBatch.render(background.modelInstance, environment)
        }

        for (interior in sceneManager.activeInteriors) {
            // Render 3D models and new floor objects
            if (interior.interiorType.is3D || interior.interiorType.isFloorObject) {
                modelBatch.render(interior.instance, environment) // This will only render the 3D ones
            }
        }

        if (isEditorMode) {
            // Render background preview
            backgroundSystem.renderPreview(modelBatch, cameraManager.camera, environment)
        }

        // Render 3D player with custom billboard shader
        playerSystem.render(cameraManager.camera, environment)
        enemySystem.renderEnemies(cameraManager.camera, environment, sceneManager.activeEnemies)
        npcSystem.renderNPCs(cameraManager.camera, environment, sceneManager.activeNPCs)
        carSystem.render(cameraManager.camera, environment, sceneManager.activeCars)
        lockIndicatorSystem.render(cameraManager.camera, environment)

        fireSystem.render(cameraManager.camera, environment)


        // Render particles
        particleSystem.render(cameraManager.camera, environment)

        // Render items
        itemSystem.render(cameraManager.camera, environment)

        modelBatch.end()

        teleporterSystem.renderNameplates(cameraManager.camera, playerSystem)
        interiorSystem.renderBillboards(cameraManager.camera, environment, sceneManager.activeInteriors)

        if (isEditorMode) {
            // Render highlight using HighlightSystem
            highlightSystem.render(modelBatch, cameraManager.camera, environment)

            if (interiorSystem.isPreviewActive()) {
                interiorSystem.billboardModelBatch.begin(cameraManager.camera)
                interiorSystem.renderPreview(interiorSystem.billboardModelBatch, environment)
                interiorSystem.billboardModelBatch.end()
            }

            if (showInvisibleBlockOutlines) {
                highlightSystem.renderInvisibleBlockOutlines(
                    modelBatch,
                    environment,
                    cameraManager.camera,
                    sceneManager.activeChunkManager.getAllBlocks()
                )
            }
        }

        // Transition to 2D UI Rendering
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        // Render UI using UIManager
        uiManager.updateFps()
        uiManager.render()

        // Render the transition animation ON TOP of everything else.
        transitionSystem.render()

        // End capture and apply post-processing effects
        shaderEffectManager.endCaptureAndRender()
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(true) // Allow writing to the depth buffer
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
    }

    override fun resize(width: Int, height: Int) {
        if (width == 0 || height == 0) {
            return
        }

        // Resize UIManager's viewport
        uiManager.resize(width, height)
        cameraManager.resize(width, height)

        // Resize shader effect manager
        shaderEffectManager.resize(width, height)
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
        lockIndicatorSystem.dispose()
        lightingManager.dispose()
        parallaxBackgroundSystem.dispose()
        interiorSystem.dispose()
        transitionSystem.dispose()
        enemySystem.dispose()
        npcSystem.dispose()
        particleSystem.dispose()
        fireSystem.dispose()
        bloodPoolSystem.dispose()
        footprintSystem.dispose()

        // Dispose shader effect manager
        shaderEffectManager.dispose()

        // Dispose UIManager
        teleporterSystem.dispose()
        uiManager.dispose()
    }
}
