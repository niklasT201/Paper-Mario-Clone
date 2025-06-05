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
    private val gameObjects = Array<GameObject>()

    private var highlightModel: Model? = null
    private var highlightInstance: ModelInstance? = null
    private var highlightMaterial: Material? = null
    private var isHighlightVisible = false
    private var highlightPosition = Vector3()

    // 2D Player (but positioned in 3D space)
    private lateinit var playerTexture: Texture
    private lateinit var playerModel: Model
    private lateinit var playerInstance: ModelInstance
    private lateinit var playerMaterial: Material

    // Game objects
    private val gameBlocks = Array<GameBlock>()
    private val playerPosition = Vector3(0f, 2f, 0f)
    private val playerSpeed = 8f

    // Player collision box
    private val playerBounds = BoundingBox()
    private val playerSize = Vector3(3f, 4f, 3f)

    // Player rotation variables for Paper Mario effect
    private var playerTargetRotationY = 0f
    private var playerCurrentRotationY = 0f
    private val rotationSpeed = 360f
    private var lastMovementDirection = 0f

    // Block size
    private val blockSize = 4f

    override fun create() {
        setupGraphics()
        setupBlockSystem()
        setupObjectSystem()

        // Initialize UI Manager
        uiManager = UIManager(blockSystem)
        uiManager.initialize()

        // Initialize Input Handler
        inputHandler = InputHandler(
            uiManager,
            cameraManager,
            blockSystem,
            this::handleLeftClickAction,
            this::handleRightClickAndRemoveBlockAction
        )
        inputHandler.initialize()

        setupModels()
        setupHighlight()

        addBlock(0f, 0f, 0f, BlockType.GRASS)
        addBlock(blockSize, 0f, 0f, BlockType.COBBLESTONE)
        addBlock(0f, 0f, blockSize, BlockType.ROOM_FLOOR)

        // Initialize player bounding box
        updatePlayerBounds()
    }

    private fun setupGraphics() {
        modelBatch = ModelBatch()
        spriteBatch = SpriteBatch()

        // Initialize camera manager
        cameraManager = CameraManager()
        cameraManager.initialize()

        // Setup environment with bright, uniform lighting
        environment = Environment()

        // Set very bright ambient light - this ensures everything is well-lit from all angles
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.9f, 0.9f, 0.9f, 1f))

        // Add multiple directional lights from different angles to eliminate shadows
        // Top-down light (like the sun)
        environment.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, 0f, -1f, 0f))

        // Light from the front
        environment.add(DirectionalLight().set(0.6f, 0.6f, 0.6f, 0f, 0f, -1f))

        // Light from the back
        environment.add(DirectionalLight().set(0.6f, 0.6f, 0.6f, 0f, 0f, 1f))

        // Light from the left
        environment.add(DirectionalLight().set(0.6f, 0.6f, 0.6f, -1f, 0f, 0f))

        // Light from the right
        environment.add(DirectionalLight().set(0.6f, 0.6f, 0.6f, 1f, 0f, 0f))

        // Optional: Add a subtle upward light to brighten bottom faces
        environment.add(DirectionalLight().set(0.4f, 0.4f, 0.4f, 0f, 1f, 0f))
    }

    private fun setupBlockSystem() {
        blockSystem = BlockSystem()
        blockSystem.initialize(blockSize)
    }

    private fun setupObjectSystem() {
        objectSystem = ObjectSystem()
        objectSystem.initialize()
    }

    private fun setupModels() {
        val modelBuilder = ModelBuilder()

        // Load player texture
        playerTexture = Texture(Gdx.files.internal("textures/player/pig_character.png"))

        // Create player material with the texture
        playerMaterial = Material(
            TextureAttribute.createDiffuse(playerTexture),
            com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
            com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.createCullFace(GL20.GL_NONE)
        )

        // Create a 3D plane/quad for the player (billboard)
        val playerSizeVisual = 4f
        playerModel = modelBuilder.createRect(
            -playerSizeVisual / 2, -playerSizeVisual / 2, 0f,
            playerSizeVisual / 2, -playerSizeVisual / 2, 0f,
            playerSizeVisual / 2, playerSizeVisual / 2, 0f,
            -playerSizeVisual / 2, playerSizeVisual / 2, 0f,
            0f, 0f, 1f,
            playerMaterial,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
        )

        // Create player instance
        playerInstance = ModelInstance(playerModel)
        updatePlayerTransform()
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
            // Player movement code
            var moved = false
            var currentMovementDirection = 0f
            val originalPosition = Vector3(playerPosition) // Not used

            if (Gdx.input.isKeyPressed(Input.Keys.A)) {
                val newX = playerPosition.x - playerSpeed * deltaTime
                if (canMoveTo(newX, playerPosition.y, playerPosition.z)) {
                    playerPosition.x = newX; moved = true; currentMovementDirection = -1f
                }
            }
            if (Gdx.input.isKeyPressed(Input.Keys.D)) {
                val newX = playerPosition.x + playerSpeed * deltaTime
                if (canMoveTo(newX, playerPosition.y, playerPosition.z)) {
                    playerPosition.x = newX; moved = true; currentMovementDirection = 1f
                }
            }
            if (Gdx.input.isKeyPressed(Input.Keys.W)) {
                val newZ = playerPosition.z - playerSpeed * deltaTime
                if (canMoveTo(playerPosition.x, playerPosition.y, newZ)) {
                    playerPosition.z = newZ; moved = true
                }
            }
            if (Gdx.input.isKeyPressed(Input.Keys.S)) {
                val newZ = playerPosition.z + playerSpeed * deltaTime
                if (canMoveTo(playerPosition.x, playerPosition.y, newZ)) {
                    playerPosition.z = newZ; moved = true
                }
            }

            // Update target rotation based on horizontal movement
            if (currentMovementDirection != 0f && currentMovementDirection != lastMovementDirection) {
                playerTargetRotationY = if (currentMovementDirection < 0f) 180f else 0f
                lastMovementDirection = currentMovementDirection
            }

            // Smoothly interpolate current rotation towards target rotation
            updatePlayerRotation(deltaTime)

            // Update player model position if moved
            if (moved) {
                playerPosition.y = 2f
                updatePlayerBounds()

                // Update camera manager with player position
                cameraManager.setPlayerPosition(playerPosition)

                // Auto-switch to player camera when moving (optional)
                cameraManager.switchToPlayerCamera()
            }

            // Handle camera input for player camera mode
            cameraManager.handleInput(deltaTime)
        }
    }

    private fun canMoveTo(x: Float, y: Float, z: Float): Boolean {
        // Create a temporary bounding box for the new position
        val horizontalShrink = 0.3f
        val tempBounds = BoundingBox()
        tempBounds.set(
            Vector3(x - (playerSize.x / 2 - horizontalShrink), y - playerSize.y / 2, z - (playerSize.z / 2 - horizontalShrink)),
            Vector3(x + (playerSize.x / 2 - horizontalShrink), y + playerSize.y / 2, z + (playerSize.z / 2 - horizontalShrink))
        )

        for (gameBlock in gameBlocks) {
            val blockBounds = BoundingBox()
            blockBounds.set(
                Vector3(gameBlock.position.x - blockSize / 2, gameBlock.position.y - blockSize / 2, gameBlock.position.z - blockSize / 2),
                Vector3(gameBlock.position.x + blockSize / 2, gameBlock.position.y + blockSize / 2, gameBlock.position.z + blockSize / 2)
            )

            // Check if the temporary player bounds intersect with the block bounds
            if (tempBounds.intersects(blockBounds)) {
                // Additional check: if player is standing on top of the block, allow movement
                val playerBottom = tempBounds.min.y
                val blockTop = blockBounds.max.y
                val tolerance = 0.5f // Small tolerance for floating point precision

                // If player's bottom is at or above the block's top (standing on it), allow movement
                if (playerBottom >= blockTop - tolerance) {
                    continue // Skip this collision, player is standing on the block
                }

                return false // Real collision detected
            }
        }

        return true // No collision
    }

    private fun updatePlayerBounds() {
        playerBounds.set(
            Vector3(playerPosition.x - playerSize.x / 2, playerPosition.y - playerSize.y / 2, playerPosition.z - playerSize.z / 2),
            Vector3(playerPosition.x + playerSize.x / 2, playerPosition.y + playerSize.y / 2, playerPosition.z + playerSize.z / 2)
        )
    }

    private fun updatePlayerRotation(deltaTime: Float) {
        // Calculate the shortest rotation path
        var rotationDifference = playerTargetRotationY - playerCurrentRotationY
        if (rotationDifference > 180f) rotationDifference -= 360f
        else if (rotationDifference < -180f) rotationDifference += 360f

        if (kotlin.math.abs(rotationDifference) > 1f) {
            val rotationStep = rotationSpeed * deltaTime
            if (rotationDifference > 0f) playerCurrentRotationY += kotlin.math.min(rotationStep, rotationDifference)
            else playerCurrentRotationY += kotlin.math.max(-rotationStep, rotationDifference)
            if (playerCurrentRotationY >= 360f) playerCurrentRotationY -= 360f
            else if (playerCurrentRotationY < 0f) playerCurrentRotationY += 360f
        } else {
            // Snap to target if close enough
            playerCurrentRotationY = playerTargetRotationY
        }
    }

    // Callback for InputHandler for left mouse click
    private fun handleLeftClickAction(screenX: Int, screenY: Int) {
        val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
        when (uiManager.selectedTool) {
            UIManager.Tool.BLOCK -> placeBlock(ray)
            UIManager.Tool.PLAYER -> placePlayer(ray)
            UIManager.Tool.OBJECT -> placeObject(ray)
        }
    }

    // Callback for InputHandler for right mouse click (attempt to remove block)
    private fun handleRightClickAndRemoveBlockAction(screenX: Int, screenY: Int): Boolean {
        val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
        val blockToRemove = getBlockAtRay(ray)
        if (blockToRemove != null) {
            removeBlock(blockToRemove)
            return true // Block was removed
        }
        return false // No block removed
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

    private fun placePlayer(ray: Ray) {
        val intersection = Vector3()
        val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            // Snap to grid
            val gridX = floor(intersection.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize / 2

            // Check if the new position would cause a collision
            if (canMoveTo(gridX, 2f, gridZ)) {
                playerPosition.set(gridX, 2f, gridZ)
                updatePlayerBounds()
                println("Player placed at: $gridX, 2, $gridZ")
            } else {
                println("Cannot place player here - collision with block")
            }
        }
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

    private fun addObject(x: Float, y: Float, z: Float, objectType: ObjectType) {
        val objectInstance = objectSystem.createObjectInstance(objectType)
        if (objectInstance != null) {
            val position = Vector3(x, y, z)
            objectInstance.transform.setTranslation(position)

            val gameObject = GameObject(objectInstance, objectType, position)
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

    private fun updatePlayerTransform() {
        // Reset transform matrix
        playerInstance.transform.idt()

        // Set position
        playerInstance.transform.setTranslation(playerPosition)

        // Apply Y-axis rotation for Paper Mario effect
        playerInstance.transform.rotate(Vector3.Y, playerCurrentRotationY)
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
        updatePlayerTransform()

        // Update highlight effect
        updateHighlight()

        // Render 3D scene
        modelBatch.begin(cameraManager.camera)

        // Render all blocks
        for (gameBlock in gameBlocks) {
            modelBatch.render(gameBlock.modelInstance, environment)
        }

        // Render all objects
        for (gameObject in gameObjects) {
            modelBatch.render(gameObject.modelInstance, environment)
        }

        // Render 3D player
        modelBatch.render(playerInstance, environment)

        modelBatch.end()

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
        playerModel.dispose()
        playerTexture.dispose()
        highlightModel?.dispose()

        // Dispose UIManager
        uiManager.dispose()
    }
}
