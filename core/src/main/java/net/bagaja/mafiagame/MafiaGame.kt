package net.bagaja.mafiagame

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import kotlin.math.floor

class MafiaGame : ApplicationAdapter() {
    private lateinit var modelBatch: ModelBatch
    private lateinit var spriteBatch: SpriteBatch
    private lateinit var environment: Environment
    private lateinit var cameraManager: CameraManager

    // UI and Input Managers
    private lateinit var uiManager: UIManager
    private lateinit var inputHandler: InputHandler

    // Block system
    private lateinit var blockSystem: BlockSystem
    private lateinit var objectSystem: ObjectSystem
    private lateinit var itemSystem: ItemSystem
    private val gameObjects = Array<GameObject>()

    private var highlightModel: Model? = null
    private var highlightInstance: ModelInstance? = null
    private var highlightMaterial: Material? = null
    private var isHighlightVisible = false
    private var highlightPosition = Vector3()

    // 2D Player (but positioned in 3D space)
    private lateinit var playerSystem: PlayerSystem

    // Game objects
    private val gameBlocks = Array<GameBlock>()

    // Block size
    private val blockSize = 4f

    // Light management
    private val activeLights = Array<PointLight>()
    private val maxLights = 8

    override fun create() {
        setupGraphics()
        setupBlockSystem()
        setupObjectSystem()
        setupItemSystem()

        // Initialize UI Manager
        uiManager = UIManager(blockSystem, objectSystem, itemSystem)
        uiManager.initialize()

        // Initialize Input Handler
        inputHandler = InputHandler(
            uiManager,
            cameraManager,
            blockSystem,
            objectSystem,
            itemSystem,
            this::handleLeftClickAction,
            this::handleRightClickAndRemoveAction,
            this::handleFinePosMove
        )
        inputHandler.initialize()

        playerSystem = PlayerSystem()
        playerSystem.initialize(blockSize)
        setupHighlight()

        addBlock(0f, 0f, 0f, BlockType.GRASS)
        addBlock(blockSize, 0f, 0f, BlockType.COBBLESTONE)
        addBlock(0f, 0f, blockSize, BlockType.ROOM_FLOOR)
    }

    private fun setupGraphics() {
        modelBatch = ModelBatch()
        spriteBatch = SpriteBatch()

        // Initialize camera manager
        cameraManager = CameraManager()
        cameraManager.initialize()

        // Setup environment and lighting
        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f))
        environment.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f))
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

    private fun setupHighlight() {
        val modelBuilder = ModelBuilder()

        // Create a transparent material with blending
        highlightMaterial = Material(
            ColorAttribute.createDiffuse(Color.GREEN),
            com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(
                GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.3f  // Alpha value (0.0 = fully transparent, 1.0 = fully opaque)
            )
        )

        // Create a wireframe box that's slightly larger than regular blocks
        val highlightSize = blockSize + 0.2f
        highlightModel = modelBuilder.createBox(
            highlightSize, highlightSize, highlightSize,
            highlightMaterial!!,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        )
        highlightInstance = ModelInstance(highlightModel!!)
    }

    private fun updateHighlight() {
        val mouseX = Gdx.input.x.toFloat()
        val mouseY = Gdx.input.y.toFloat()
        val ray = cameraManager.camera.getPickRay(mouseX, mouseY)

        // Check if we're hovering over an existing block (for removal)
        val hitBlock = getBlockAtRay(ray)

        if (hitBlock != null && uiManager.selectedTool == UIManager.Tool.BLOCK) {
            // Show transparent red highlight for block removal
            isHighlightVisible = true
            highlightPosition.set(hitBlock.position)
            val transparentRed = Color(1f, 0f, 0f, 0.3f)
            highlightMaterial?.set(ColorAttribute.createDiffuse(transparentRed))
            highlightInstance?.transform?.setTranslation(highlightPosition)
        } else if (uiManager.selectedTool == UIManager.Tool.OBJECT) {
            // Show green highlight for object placement
            val intersection = Vector3()
            val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

            if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
                val gridX = floor(intersection.x / blockSize) * blockSize + blockSize / 2
                val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize / 2

                isHighlightVisible = true
                highlightPosition.set(gridX, 0.5f, gridZ)
                val transparentBlue = Color(0f, 0f, 1f, 0.3f)
                highlightMaterial?.set(ColorAttribute.createDiffuse(transparentBlue))
                highlightInstance?.transform?.setTranslation(highlightPosition)
            } else {
                isHighlightVisible = false
            }
        } else if (uiManager.selectedTool == UIManager.Tool.ITEM) {
            // Show yellow highlight for item placement
            val intersection = Vector3()
            val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

            if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
                isHighlightVisible = true
                highlightPosition.set(intersection.x, intersection.y + 1f, intersection.z) // Items float above ground
                val transparentYellow = Color(1f, 1f, 0f, 0.3f)
                highlightMaterial?.set(ColorAttribute.createDiffuse(transparentYellow))
                highlightInstance?.transform?.setTranslation(highlightPosition)
            } else {
                isHighlightVisible = false
            }
        } else {
            // Show transparent green highlight for player placement
            val intersection = Vector3()
            val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

            if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
                // Snap to grid
                val gridX = floor(intersection.x / blockSize) * blockSize
                val gridZ = floor(intersection.z / blockSize) * blockSize

                // Check if there's already a block at this position
                val existingBlock = gameBlocks.find { gameBlock ->
                    kotlin.math.abs(gameBlock.position.x - (gridX + blockSize / 2)) < 0.1f &&
                        kotlin.math.abs(gameBlock.position.y - (blockSize / 2)) < 0.1f &&
                        kotlin.math.abs(gameBlock.position.z - (gridZ + blockSize / 2)) < 0.1f
                }

                if (existingBlock == null && uiManager.selectedTool == UIManager.Tool.BLOCK) {
                    isHighlightVisible = true
                    highlightPosition.set(gridX + blockSize / 2, blockSize / 2, gridZ + blockSize / 2)
                    val transparentGreen = Color(0f, 1f, 0f, 0.3f)
                    highlightMaterial?.set(ColorAttribute.createDiffuse(transparentGreen))
                    highlightInstance?.transform?.setTranslation(highlightPosition)
                } else {
                    isHighlightVisible = false
                }
            } else {
                isHighlightVisible = false
            }
        }
    }

    private fun handlePlayerInput() {
        val deltaTime = Gdx.graphics.deltaTime

        if (cameraManager.isFreeCameraMode) {
            // Handle free camera movement
            cameraManager.handleInput(deltaTime)
        } else {
            // Handle player movement through PlayerSystem
            val moved = playerSystem.handleMovement(deltaTime, gameBlocks)

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
        }
    }

    // Callback for InputHandler for right mouse click (attempt to remove block)
    private fun handleRightClickAndRemoveAction(screenX: Int, screenY: Int): Boolean {
        val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())

        when (uiManager.selectedTool) {
            UIManager.Tool.BLOCK -> {
                val blockToRemove = getBlockAtRay(ray)
                if (blockToRemove != null) {
                    removeBlock(blockToRemove)
                    return true
                }
            }
            UIManager.Tool.OBJECT -> {
                val objectToRemove = getObjectAtRay(ray)
                if (objectToRemove != null) {
                    removeObject(objectToRemove)
                    return true
                }
            }
            UIManager.Tool.PLAYER -> {
                return false
            }
            UIManager.Tool.ITEM -> {
                val itemToRemove = getItemAtRay(ray)
                if (itemToRemove != null) {
                    itemSystem.removeItem(itemToRemove)
                    return true
                }
            }
        }
        return false
    }

    private fun handleFinePosMove(deltaX: Float, deltaY: Float, deltaZ: Float) {
        // Find the most recently placed light source or allow selection
        val lightSource = gameObjects.findLast { it.objectType == ObjectType.LIGHT_SOURCE }

        if (lightSource != null) {
            // Update position
            lightSource.position.add(deltaX, deltaY, deltaZ)

            // Update model instance transform
            lightSource.modelInstance.transform.setTranslation(lightSource.position)

            // Update debug instance transform if it exists
            lightSource.debugInstance?.transform?.setTranslation(lightSource.position)

            // Update light position for lighting effects
            lightSource.updateLightPosition()

            println("Light source moved to: ${lightSource.position}")
        } else {
            println("No light source found to move. Place a light source first.")
        }
    }

    private fun placeBlock(ray: Ray) {
        // First, try to find intersection with existing blocks
        val hitBlock = getBlockAtRay(ray)

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

    private fun getObjectAtRay(ray: Ray): GameObject? {
        var closestObject: GameObject? = null
        var closestDistance = Float.MAX_VALUE

        for (gameObject in gameObjects) {
            // Create a bounding box for the object (adjust size as needed)
            val objectBounds = BoundingBox()
            val objectSize = 2f // Adjust this based on your object sizes
            objectBounds.set(
                Vector3(gameObject.position.x - objectSize / 2, gameObject.position.y, gameObject.position.z - objectSize / 2),
                Vector3(gameObject.position.x + objectSize / 2, gameObject.position.y + objectSize, gameObject.position.z + objectSize / 2)
            )

            // Check if ray intersects with this object's bounding box
            val intersection = Vector3()
            if (com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, objectBounds, intersection)) {
                val distance = ray.origin.dst(intersection)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestObject = gameObject
                }
            }
        }

        return closestObject
    }

    // New function to get items at ray intersection
    private fun getItemAtRay(ray: Ray): GameItem? {
        var closestItem: GameItem? = null
        var closestDistance = Float.MAX_VALUE

        for (item in itemSystem.getAllItems()) {
            if (item.isCollected) continue

            // Use the item's bounding box for ray intersection
            val itemBounds = item.getBoundingBox()

            // Check if ray intersects with this item's bounding box
            val intersection = Vector3()
            if (com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, itemBounds, intersection)) {
                val distance = ray.origin.dst(intersection)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestItem = item
                }
            }
        }

        return closestItem
    }

    private fun getBlockAtRay(ray: Ray): GameBlock? {
        var closestBlock: GameBlock? = null
        var closestDistance = Float.MAX_VALUE

        for (gameBlock in gameBlocks) {
            val blockBounds = BoundingBox()
            blockBounds.set(
                Vector3(gameBlock.position.x - blockSize / 2, gameBlock.position.y - blockSize / 2, gameBlock.position.z - blockSize / 2),
                Vector3(gameBlock.position.x + blockSize / 2, gameBlock.position.y + blockSize / 2, gameBlock.position.z + blockSize / 2)
            )

            // Check if ray intersects with this block's bounding box
            val intersection = Vector3()
            if (com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, blockBounds, intersection)) {
                val distance = ray.origin.dst(intersection)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestBlock = gameBlock
                }
            }
        }

        return closestBlock
    }

    private fun removeBlock(blockToRemove: GameBlock) {
        gameBlocks.removeValue(blockToRemove, true)
        println("${blockToRemove.blockType.displayName} block removed at: ${blockToRemove.position}")
    }

    private fun removeObject(objectToRemove: GameObject) {
        // Remove light from environment if it exists
        if (objectToRemove.pointLight != null) {
            environment.remove(objectToRemove.pointLight)
            activeLights.removeValue(objectToRemove.pointLight, true)
            println("Light removed. Remaining lights: ${activeLights.size}")
        }

        gameObjects.removeValue(objectToRemove, true)
        println("${objectToRemove.objectType.displayName} removed at: ${objectToRemove.position}")
    }

    private fun placePlayer(ray: Ray) {
        playerSystem.placePlayer(ray, gameBlocks)
    }

    private fun placeObject(ray: Ray) {
        val intersection = Vector3()
        val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            // Snap to grid (optional - you might want objects to be placed more freely)
            val gridX = floor(intersection.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize / 2

            // Check if there's already an object at this position (optional)
            val existingObject = gameObjects.find { gameObject ->
                kotlin.math.abs(gameObject.position.x - gridX) < 1f &&
                    kotlin.math.abs(gameObject.position.z - gridZ) < 1f
            }

            if (existingObject == null) {
                addObject(gridX, 0f, gridZ, objectSystem.currentSelectedObject)
                println("${objectSystem.currentSelectedObject.displayName} placed at: $gridX, 0, $gridZ")
            } else {
                println("Object already exists near this position")
            }
        }
    }

    // New function to place items
    private fun placeItem(ray: Ray) {
        val intersection = Vector3()
        val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            // Items can be placed more freely, don't need to snap to grid
            val itemPosition = Vector3(intersection.x, intersection.y + 1f, intersection.z) // Float items above ground

            // Check if there's already an item too close to this position
            val existingItem = itemSystem.getItemAtPosition(itemPosition, 1.5f)

            if (existingItem == null) {
                itemSystem.addItem(itemPosition, itemSystem.currentSelectedItem)
                println("${itemSystem.currentSelectedItem.displayName} placed at: $itemPosition")
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
                if (light != null && activeLights.size < maxLights) {
                    environment.add(light)
                    activeLights.add(light)
                    println("Light source added at position: $position (Total lights: ${activeLights.size})")
                } else if (activeLights.size >= maxLights) {
                    println("Warning: Maximum number of lights ($maxLights) reached!")
                }
            }

            gameObjects.add(gameObject)
        }
    }

    private fun addBlock(x: Float, y: Float, z: Float, blockType: BlockType) {
        val blockInstance = blockSystem.createBlockInstance(blockType)
        if (blockInstance != null) {
            val position = Vector3(x + blockSize/2, y + blockSize/2, z + blockSize/2)
            blockInstance.transform.setTranslation(position)

            val gameBlock = GameBlock(blockInstance, blockType, position)
            gameBlocks.add(gameBlock)
        }
    }

    override fun render() {
        // Clear screen
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        // Get delta time for this frame
        val deltaTime = Gdx.graphics.deltaTime

        // Update input handler for continuous actions
        inputHandler.update(deltaTime)

        // Handle player input
        handlePlayerInput()
        playerSystem.update(deltaTime)

        // Update item system (animations, collisions, etc.)
        itemSystem.update(deltaTime, cameraManager.camera.position, playerSystem.getPosition(), 2f)

        // Update highlight effect
        updateHighlight()

        // Render 3D scene
        modelBatch.begin(cameraManager.camera)

        // Render all blocks
        for (gameBlock in gameBlocks) {
            modelBatch.render(gameBlock.modelInstance, environment)
        }

        // Render all objects (modified to handle invisible objects properly)
        for (gameObject in gameObjects) {
            val renderInstance = gameObject.getRenderInstance(objectSystem.debugMode)
            if (renderInstance != null) {
                modelBatch.render(renderInstance, environment)
            }
        }
        modelBatch.end()

        // Render 3D player with custom billboard shader
        playerSystem.render(cameraManager.camera, environment)

        // Render items
        itemSystem.render(modelBatch, environment)

        // Render transparent highlight separately with proper blending
        if (isHighlightVisible && highlightInstance != null) {
            // Enable blending for transparency
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

            // Disable depth writing but keep depth testing
            Gdx.gl.glDepthMask(false)

            modelBatch.begin(cameraManager.camera)
            modelBatch.render(highlightInstance!!, environment)
            modelBatch.end()

            // Restore depth writing and disable blending
            Gdx.gl.glDepthMask(true)
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }

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
        playerSystem.dispose()
        highlightModel?.dispose()

        // Dispose UIManager
        uiManager.dispose()
    }
}
