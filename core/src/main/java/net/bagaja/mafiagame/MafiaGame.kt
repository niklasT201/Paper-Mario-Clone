package net.bagaja.mafiagame

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array

class MafiaGame : ApplicationAdapter() {
    val isEditorMode = true
    val renderDistanceInChunks = 2
    private lateinit var modelBatch: ModelBatch
    private lateinit var shaderProvider: BillboardShaderProvider
    private lateinit var spriteBatch: SpriteBatch
    lateinit var cameraManager: CameraManager
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
    lateinit var playerSystem: PlayerSystem

    private lateinit var spawnerSystem: SpawnerSystem

    // Game objects
    var lastPlacedInstance: Any? = null

    private lateinit var backgroundSystem: BackgroundSystem
    private lateinit var parallaxBackgroundSystem: ParallaxBackgroundSystem
    private lateinit var interiorSystem: InteriorSystem
    var isPlacingExitDoorMode = false
    var houseRequiringDoor: GameHouse? = null

    private var showInvisibleBlockOutlines = false

    // Block size
    val blockSize = 4f

    lateinit var lightingManager: LightingManager
    lateinit var particleSystem: ParticleSystem
    lateinit var teleporterSystem: TeleporterSystem
    lateinit var fireSystem: FireSystem
    private lateinit var bloodPoolSystem: BloodPoolSystem
    private lateinit var footprintSystem: FootprintSystem
    private lateinit var boneSystem: BoneSystem

    override fun create() {
        setupGraphics()
        particleSystem = ParticleSystem()
        blockSystem = BlockSystem()
        objectSystem = ObjectSystem()
        fireSystem = FireSystem()
        bloodPoolSystem = BloodPoolSystem()
        footprintSystem = FootprintSystem()
        boneSystem = BoneSystem()
        itemSystem = ItemSystem()
        carSystem = CarSystem()
        lockIndicatorSystem = LockIndicatorSystem()
        houseSystem = HouseSystem()
        backgroundSystem = BackgroundSystem()
        parallaxBackgroundSystem = ParallaxBackgroundSystem()
        interiorSystem = InteriorSystem()
        enemySystem = EnemySystem()
        npcSystem = NPCSystem()
        roomTemplateManager = RoomTemplateManager()
        // Initialize Transition System
        transitionSystem = TransitionSystem()
        playerSystem = PlayerSystem()
        // Initialize Shader Effect Manager
        shaderEffectManager = ShaderEffectManager()
        spawnerSystem = SpawnerSystem(particleSystem, itemSystem)

        // Initialize UI Manager
        uiManager = UIManager(
            this, blockSystem, objectSystem, itemSystem, carSystem, houseSystem,
            backgroundSystem, parallaxBackgroundSystem, roomTemplateManager, interiorSystem,
            lightingManager, shaderEffectManager, enemySystem, npcSystem, particleSystem, spawnerSystem
        )
        teleporterSystem = TeleporterSystem(objectSystem, uiManager)

        faceCullingSystem = FaceCullingSystem(blockSize)
        sceneManager = SceneManager(
            playerSystem, blockSystem, objectSystem, itemSystem, interiorSystem,
            enemySystem, npcSystem, roomTemplateManager, cameraManager, transitionSystem,
            faceCullingSystem, this, particleSystem, fireSystem, boneSystem
        )

        blockSystem.sceneManager = sceneManager
        objectSystem.sceneManager = sceneManager
        itemSystem.sceneManager = sceneManager
        carSystem.sceneManager = sceneManager
        houseSystem.sceneManager = sceneManager
        backgroundSystem.sceneManager = sceneManager
        parallaxBackgroundSystem.sceneManager = sceneManager
        interiorSystem.sceneManager = sceneManager
        enemySystem.sceneManager = sceneManager
        npcSystem.sceneManager = sceneManager
        particleSystem.sceneManager = sceneManager
        spawnerSystem.sceneManager = sceneManager
        sceneManager.teleporterSystem = teleporterSystem

        // Initialize systems in an order that makes sense
        particleSystem.initialize(blockSize)
        blockSystem.initialize(blockSize)
        objectSystem.initialize(blockSize, lightingManager, fireSystem, teleporterSystem, uiManager) // CORRECTED: Pass dependencies
        fireSystem.initialize()
        bloodPoolSystem.initialize()
        footprintSystem.initialize()
        boneSystem.initialize()
        itemSystem.initialize(blockSize)
        carSystem.initialize(blockSize)
        lockIndicatorSystem.initialize()
        houseSystem.initialize()
        backgroundSystem.initialize(blockSize)
        parallaxBackgroundSystem.initialize(blockSize)
        interiorSystem.initialize(blockSize)
        enemySystem.initialize(blockSize)
        npcSystem.initialize(blockSize)
        roomTemplateManager.initialize()
        playerSystem.initialize(blockSize, particleSystem, lightingManager, bloodPoolSystem, footprintSystem)
        shaderEffectManager.initialize()

        // Initialize managers that depend on initialized systems
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
            shaderEffectManager
        )
        inputHandler.initialize()
        raycastSystem = RaycastSystem(blockSize)

        highlightSystem = HighlightSystem(blockSize)
        highlightSystem.initialize()

        // Pass the initial world data to the SceneManager
        sceneManager.initializeWorld(
            Array(), Array(), Array(), Array(), Array(), Array(), Array()
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
                        sceneManager.game.uiManager.enterExitDoorPlacementMode(currentHouse)
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

    internal fun handleFinePosMove(deltaX: Float, deltaY: Float, deltaZ: Float) {
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

    fun toggleInvisibleBlockOutlines() {
        showInvisibleBlockOutlines = !showInvisibleBlockOutlines
        val status = if (showInvisibleBlockOutlines) "ON" else "OFF"
        uiManager.updatePlacementInfo("Invisible Block Outlines: $status")
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
            carSystem.update(deltaTime, sceneManager)

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

        // Render cars first as they are mostly opaque
        carSystem.render(cameraManager.camera, environment, sceneManager.activeCars)
        lockIndicatorSystem.render(cameraManager.camera, environment)

        // Render effects that should appear BEHIND characters
        fireSystem.render(cameraManager.camera, environment)

        // Render Blood Pool
        bloodPoolSystem.render(cameraManager.camera, environment, sceneManager.activeBloodPools)
        footprintSystem.render(cameraManager.camera, environment, sceneManager.activeFootprints)
        boneSystem.render(cameraManager.camera, environment, sceneManager.activeBones)

        // Render all potentially transparent billboards AFTER the fire
        playerSystem.render(cameraManager.camera, environment)
        enemySystem.renderEnemies(cameraManager.camera, environment, sceneManager.activeEnemies)
        npcSystem.renderNPCs(cameraManager.camera, environment, sceneManager.activeNPCs)
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
        boneSystem.dispose()

        // Dispose shader effect manager
        shaderEffectManager.dispose()

        // Dispose UIManager
        teleporterSystem.dispose()
        uiManager.dispose()
    }
}
