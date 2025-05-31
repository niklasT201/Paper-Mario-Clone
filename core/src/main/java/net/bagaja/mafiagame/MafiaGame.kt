package net.bagaja.mafiagame

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.viewport.ScreenViewport
import kotlin.math.floor

class MafiaGame : ApplicationAdapter() {
    private lateinit var modelBatch: ModelBatch
    private lateinit var spriteBatch: SpriteBatch
    private lateinit var environment: Environment
    private lateinit var camera: PerspectiveCamera
    private lateinit var cameraController: CameraInputController
    private lateinit var stage: Stage
    private lateinit var skin: Skin

    private var isFreeCameraMode = false
    private var freeCameraPosition = Vector3(0f, 8f, 20f)
    private var freeCameraTarget = Vector3(0f, 2f, 0f)
    private val freeCameraSpeed = 15f
    private var freeCameraYaw = 0f // Horizontal rotation (left/right)
    private var freeCameraPitch = 0f // Vertical rotation (up/down)
    private val cameraRotationSpeed = 90f // Degrees per second for rotation

    // 3D Models
    private lateinit var blockModel: Model

    // 2D Player (but positioned in 3D space)
    private lateinit var playerTexture: Texture
    private lateinit var playerModel: Model
    private lateinit var playerInstance: ModelInstance
    private lateinit var playerMaterial: Material

    // Game objects
    private val blocks = Array<ModelInstance>()
    private val playerPosition = Vector3(0f, 2f, 0f)
    private val playerSpeed = 8f // Units per second

    // Player collision box
    private val playerBounds = BoundingBox()
    private val playerSize = Vector3(3f, 4f, 3f) // Width, Height, Depth - slightly smaller than block size for better feel

    // Player rotation variables for Paper Mario effect
    private var playerTargetRotationY = 0f // Target rotation in degrees
    private var playerCurrentRotationY = 0f // Current rotation in degrees
    private val rotationSpeed = 360f // Degrees per second for smooth rotation
    private var lastMovementDirection = 0f // -1 for left, 1 for right, 0 for none

    // UI state
    private var selectedTool = Tool.BLOCK
    private var isUIVisible = true

    // Block size (4x4 like you requested)
    private val blockSize = 4f

    // Input handling
    private var isRightMousePressed = false
    private var lastMouseX = 0f
    private var lastMouseY = 0f
    private var cameraDistance = 20f // Distance from the action
    private var cameraAngleY = 0f // Horizontal rotation around the scene

    enum class Tool {
        BLOCK, PLAYER
    }

    override fun create() {
        setupGraphics()
        setupModels()
        setupUI()
        setupInputHandling()

        // Add some initial blocks for reference
        addBlock(0f, 0f, 0f)
        addBlock(blockSize, 0f, 0f)
        addBlock(0f, 0f, blockSize)

        // Initialize player bounding box
        updatePlayerBounds()
    }

    private fun setupGraphics() {
        modelBatch = ModelBatch()
        spriteBatch = SpriteBatch()

        // Setup camera for Paper Mario style (side view)
        camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

        // Position camera for side view - looking from the side at an angle
        cameraDistance = 20f
        cameraAngleY = 90f // Start looking from the side (90 degrees)
        updateCameraPosition()

        camera.near = 1f
        camera.far = 300f
        camera.update()

        // Create camera controller but we'll handle input manually for better control
        cameraController = CameraInputController(camera)

        // Setup environment and lighting
        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f))
        environment.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f))
    }

    private fun updateCameraPosition() {
        if (!isFreeCameraMode) {
            // Original camera positioning code (orbiting around player/scene)
            val radians = Math.toRadians(cameraAngleY.toDouble())
            val x = (Math.cos(radians) * cameraDistance).toFloat()
            val z = (Math.sin(radians) * cameraDistance).toFloat()

            camera.position.set(x, 8f, z)
            camera.lookAt(0f, 2f, 0f)
            camera.up.set(0f, 1f, 0f)
            camera.update()
        }
        // If in free camera mode, the camera position is handled by handleFreeCameraInput()
    }

    private fun setupModels() {
        val modelBuilder = ModelBuilder()

        // Create block model (4x4x4 cube)
        blockModel = modelBuilder.createBox(
            blockSize, blockSize, blockSize,
            Material(ColorAttribute.createDiffuse(Color.BROWN)),
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        )

        // Load player texture
        playerTexture = Texture(Gdx.files.internal("textures/player/pig_character.png"))

        // Create player material with the texture
        playerMaterial = Material(
            com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.createDiffuse(playerTexture),
            com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
            com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.createCullFace(GL20.GL_NONE)
        )

        // Create a 3D plane/quad for the player (billboard)
        // Make it 4x4 to match your block size, but you can adjust as needed
        val playerSizeVisual = 4f
        playerModel = modelBuilder.createRect(
            -playerSizeVisual/2, -playerSizeVisual/2, 0f,  // bottom left
            playerSizeVisual/2, -playerSizeVisual/2, 0f,   // bottom right
            playerSizeVisual/2, playerSizeVisual/2, 0f,    // top right
            -playerSizeVisual/2, playerSizeVisual/2, 0f,   // top left
            0f, 0f, 1f,                        // normal (facing camera)
            playerMaterial,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
        )

        // Create player instance
        playerInstance = ModelInstance(playerModel)
        updatePlayerTransform()
    }

    private fun setupUI() {
        stage = Stage(ScreenViewport())

        // Try to load UI skin from assets/ui/ folder, fallback to default if not available
        skin = try {
            Skin(Gdx.files.internal("ui/uiskin.json"))
        } catch (e: Exception) {
            println("Could not load UI skin from assets/ui/, using default skin")
            createDefaultSkin()
        }

        // Create main UI table
        val mainTable = Table()
        mainTable.setFillParent(true)
        mainTable.top().left()
        mainTable.pad(20f)

        // Title
        val titleLabel = Label("World Builder", skin)
        titleLabel.setFontScale(1.5f)
        mainTable.add(titleLabel).colspan(2).padBottom(20f).row()

        // Tool selection
        val toolLabel = Label("Select Tool:", skin)
        mainTable.add(toolLabel).padBottom(10f).row()

        val blockButton = TextButton("Ground Block", skin, "toggle")
        val playerButton = TextButton("Player", skin, "toggle")

        blockButton.isChecked = true

        blockButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                if (blockButton.isChecked) {
                    selectedTool = Tool.BLOCK
                    playerButton.isChecked = false
                }
            }
        })

        playerButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                if (playerButton.isChecked) {
                    selectedTool = Tool.PLAYER
                    blockButton.isChecked = false
                }
            }
        })

        mainTable.add(blockButton).width(120f).padBottom(5f).row()
        mainTable.add(playerButton).width(120f).padBottom(20f).row()

        // Instructions
        val instructionLabel = Label("Instructions:", skin)
        mainTable.add(instructionLabel).padBottom(10f).row()

        val instructions = """
        • Left click to place
        • Hold Right click + drag to rotate camera
        • Mouse wheel to zoom in/out
        • WASD to move player
        • H to toggle this UI
        • Blocks snap to 4x4 grid
        • Paper Mario style rotation!
        • Player has collision detection!
        """.trimIndent()

        val instructionText = Label(instructions, skin)
        instructionText.setWrap(true)
        mainTable.add(instructionText).width(200f).padBottom(20f).row()

        // Stats
        val statsLabel = Label("Stats:", skin)
        mainTable.add(statsLabel).padBottom(10f).row()

        val statsText = Label("Blocks: 3\nPlayer: Placed", skin)
        mainTable.add(statsText).width(200f).row()

        stage.addActor(mainTable)
    }

    private fun createDefaultSkin(): Skin {
        val skin = Skin()

        // Create a 1x1 white texture
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.WHITE)
        pixmap.fill()
        val texture = Texture(pixmap)
        pixmap.dispose()

        // Create font
        val font = com.badlogic.gdx.graphics.g2d.BitmapFont()

        // Add font to skin
        skin.add("default-font", font)

        // Create drawable for buttons
        val buttonTexture = com.badlogic.gdx.graphics.g2d.TextureRegion(texture)
        val buttonDrawable = com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(buttonTexture)

        // Button styles
        val buttonStyle = Button.ButtonStyle()
        buttonStyle.up = buttonDrawable.tint(Color.GRAY)
        buttonStyle.down = buttonDrawable.tint(Color.DARK_GRAY)
        skin.add("default", buttonStyle)

        val toggleButtonStyle = Button.ButtonStyle()
        toggleButtonStyle.up = buttonDrawable.tint(Color.GRAY)
        toggleButtonStyle.down = buttonDrawable.tint(Color.DARK_GRAY)
        toggleButtonStyle.checked = buttonDrawable.tint(Color.GREEN)
        skin.add("toggle", toggleButtonStyle)

        // Text button styles
        val textButtonStyle = TextButton.TextButtonStyle()
        textButtonStyle.up = buttonDrawable.tint(Color.GRAY)
        textButtonStyle.down = buttonDrawable.tint(Color.DARK_GRAY)
        textButtonStyle.font = font
        textButtonStyle.fontColor = Color.WHITE
        skin.add("default", textButtonStyle)

        val toggleTextButtonStyle = TextButton.TextButtonStyle()
        toggleTextButtonStyle.up = buttonDrawable.tint(Color.GRAY)
        toggleTextButtonStyle.down = buttonDrawable.tint(Color.DARK_GRAY)
        toggleTextButtonStyle.checked = buttonDrawable.tint(Color.GREEN)
        toggleTextButtonStyle.font = font
        toggleTextButtonStyle.fontColor = Color.WHITE
        skin.add("toggle", toggleTextButtonStyle)

        // Label style
        val labelStyle = Label.LabelStyle()
        labelStyle.font = font
        labelStyle.fontColor = Color.WHITE
        skin.add("default", labelStyle)

        return skin
    }

    private fun setupInputHandling() {
        val inputMultiplexer = com.badlogic.gdx.InputMultiplexer()

        // Add custom input processor first (before stage) so clicks work properly
        inputMultiplexer.addProcessor(object : com.badlogic.gdx.InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                // Check if click is over UI
                val stageCoords = stage.screenToStageCoordinates(com.badlogic.gdx.math.Vector2(screenX.toFloat(), screenY.toFloat()))
                val actorHit = stage.hit(stageCoords.x, stageCoords.y, true)

                // If we clicked on UI, let the stage handle it
                if (actorHit != null && isUIVisible) {
                    return false // Let stage handle it
                }

                // Handle mouse input
                if (button == Input.Buttons.LEFT) {
                    handleLeftClick(screenX, screenY)
                    return true
                } else if (button == Input.Buttons.RIGHT) {
                    // Check if we're starting a drag operation or clicking on a block
                    val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())
                    val blockToRemove = getBlockAtRay(ray)

                    if (blockToRemove != null) {
                        // Remove the block
                        removeBlock(blockToRemove)
                        return true
                    } else {
                        // No block hit, start camera drag
                        isRightMousePressed = true
                        lastMouseX = screenX.toFloat()
                        lastMouseY = screenY.toFloat()
                        return true
                    }
                }
                return false
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                if (button == Input.Buttons.RIGHT) {
                    isRightMousePressed = false
                    return true
                }
                return false
            }

            override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
                if (isRightMousePressed) {
                    // Calculate mouse movement
                    val deltaX = screenX - lastMouseX

                    // Update camera angle based on horizontal mouse movement
                    cameraAngleY += deltaX * 0.2f // Sensitivity factor

                    // Update camera position
                    updateCameraPosition()

                    // Update last mouse position
                    lastMouseX = screenX.toFloat()
                    lastMouseY = screenY.toFloat()

                    return true
                }
                return false
            }

            override fun scrolled(amountX: Float, amountY: Float): Boolean {
                // Zoom in/out with mouse wheel
                cameraDistance += amountY * 2f
                cameraDistance = cameraDistance.coerceIn(5f, 50f) // Limit zoom range
                updateCameraPosition()
                return true
            }

            override fun keyDown(keycode: Int): Boolean {
                if (keycode == Input.Keys.H) {
                    isUIVisible = !isUIVisible
                    return true
                } else if (keycode == Input.Keys.C) {
                    // Toggle between free camera and normal camera
                    isFreeCameraMode = !isFreeCameraMode

                    if (isFreeCameraMode) {
                        // Entering free camera mode - store current camera position
                        freeCameraPosition.set(camera.position)

                        // Calculate initial yaw based on current camera direction
                        val currentDirection = Vector3(camera.direction).nor()
                        freeCameraYaw = Math.toDegrees(Math.atan2(currentDirection.x.toDouble(), currentDirection.z.toDouble())).toFloat()
                        freeCameraPitch = Math.toDegrees(Math.asin(currentDirection.y.toDouble())).toFloat()

                        // Set initial target
                        freeCameraTarget.set(freeCameraPosition).add(currentDirection)

                        println("Free Camera Mode: ON")
                        println("Controls: Arrow Keys (Up/Down=Move, Left/Right=Rotate), Space=Up, Shift=Down")
                    } else {
                        // Returning to normal camera mode
                        updateCameraPosition()
                        println("Free Camera Mode: OFF - Back to normal camera")
                    }
                    return true
                }
                return false
            }
        })

        inputMultiplexer.addProcessor(stage)
        // Note: We're not adding cameraController to avoid conflicts with our custom camera handling
        Gdx.input.inputProcessor = inputMultiplexer
    }

    private fun handlePlayerInput() {
        val deltaTime = Gdx.graphics.deltaTime

        if (isFreeCameraMode) {
            // Handle free camera movement with arrow keys
            handleFreeCameraInput(deltaTime)
        } else {
            // Original player movement code
            var moved = false
            var currentMovementDirection = 0f
            val originalPosition = Vector3(playerPosition)

            // Handle WASD input for player movement
            if (Gdx.input.isKeyPressed(Input.Keys.A)) {
                val newX = playerPosition.x - playerSpeed * deltaTime
                if (canMoveTo(newX, playerPosition.y, playerPosition.z)) {
                    playerPosition.x = newX
                    moved = true
                    currentMovementDirection = -1f
                }
            }
            if (Gdx.input.isKeyPressed(Input.Keys.D)) {
                val newX = playerPosition.x + playerSpeed * deltaTime
                if (canMoveTo(newX, playerPosition.y, playerPosition.z)) {
                    playerPosition.x = newX
                    moved = true
                    currentMovementDirection = 1f
                }
            }
            if (Gdx.input.isKeyPressed(Input.Keys.W)) {
                val newZ = playerPosition.z - playerSpeed * deltaTime
                if (canMoveTo(playerPosition.x, playerPosition.y, newZ)) {
                    playerPosition.z = newZ
                    moved = true
                }
            }
            if (Gdx.input.isKeyPressed(Input.Keys.S)) {
                val newZ = playerPosition.z + playerSpeed * deltaTime
                if (canMoveTo(playerPosition.x, playerPosition.y, newZ)) {
                    playerPosition.z = newZ
                    moved = true
                }
            }

            // Update target rotation based on horizontal movement
            if (currentMovementDirection != 0f && currentMovementDirection != lastMovementDirection) {
                if (currentMovementDirection < 0f) {
                    playerTargetRotationY = 180f
                } else {
                    playerTargetRotationY = 0f
                }
                lastMovementDirection = currentMovementDirection
            }

            // Smoothly interpolate current rotation towards target rotation
            updatePlayerRotation(deltaTime)

            // Update player model position if moved
            if (moved) {
                playerPosition.y = 2f
                updatePlayerBounds()
            }
        }
    }

    private fun handleFreeCameraInput(deltaTime: Float) {
        val moveDistance = freeCameraSpeed * deltaTime
        val rotationAmount = cameraRotationSpeed * deltaTime

        // Handle rotation with left/right arrow keys
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            freeCameraYaw += rotationAmount
        }
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            freeCameraYaw -= rotationAmount
        }

        // Handle pitch rotation
        if (Gdx.input.isKeyPressed(Input.Keys.COMMA)) { // Look down
            freeCameraPitch -= rotationAmount
            freeCameraPitch = freeCameraPitch.coerceIn(-89f, 89f) // Prevent camera flip
        }
        if (Gdx.input.isKeyPressed(Input.Keys.PERIOD)) { // Look up
            freeCameraPitch += rotationAmount
            freeCameraPitch = freeCameraPitch.coerceIn(-89f, 89f) // Prevent camera flip
        }


        // Calculate forward, right, and up vectors based on current rotation
        val yawRadians = Math.toRadians(freeCameraYaw.toDouble())
        val pitchRadians = Math.toRadians(freeCameraPitch.toDouble())

        // Forward vector (where the camera is looking)
        val forward = Vector3(
            (Math.cos(pitchRadians) * Math.sin(yawRadians)).toFloat(),
            Math.sin(pitchRadians).toFloat(),
            (Math.cos(pitchRadians) * Math.cos(yawRadians)).toFloat()
        )

        // Right vector (perpendicular to forward, for strafing)
        val right = Vector3(forward).crs(Vector3.Y).nor()

        // Up vector
        val up = Vector3.Y

        // Handle movement with arrow keys and space/shift
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
            // Move forward in the direction the camera is looking
            freeCameraPosition.add(Vector3(forward).scl(moveDistance))
        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            // Move backward (opposite of forward direction)
            freeCameraPosition.sub(Vector3(forward).scl(moveDistance))
        }

        // Vertical movement
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
            // Move up
            freeCameraPosition.add(Vector3(up).scl(moveDistance))
        }
        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
            // Move down
            freeCameraPosition.sub(Vector3(up).scl(moveDistance))
        }

        // Optional: Add WASD controls for those who prefer them
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            freeCameraPosition.add(Vector3(forward).scl(moveDistance))
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            freeCameraPosition.sub(Vector3(forward).scl(moveDistance))
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            freeCameraYaw += rotationAmount
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            freeCameraYaw -= rotationAmount
        }

        // Update camera target based on position and rotation
        freeCameraTarget.set(freeCameraPosition).add(forward)

        // Apply the new position and rotation to the camera
        camera.position.set(freeCameraPosition)
        camera.lookAt(freeCameraTarget)
        camera.up.set(0f, 1f, 0f)
        camera.update()
    }

    private fun canMoveTo(x: Float, y: Float, z: Float): Boolean {
        // Create a temporary bounding box for the new position
        val tempBounds = BoundingBox()
        tempBounds.set(
            Vector3(x - playerSize.x/2, y - playerSize.y/2, z - playerSize.z/2),
            Vector3(x + playerSize.x/2, y + playerSize.y/2, z + playerSize.z/2)
        )

        // Check collision with all blocks
        for (block in blocks) {
            val blockPosition = Vector3()
            block.transform.getTranslation(blockPosition)

            // Create bounding box for the block
            val blockBounds = BoundingBox()
            blockBounds.set(
                Vector3(blockPosition.x - blockSize/2, blockPosition.y - blockSize/2, blockPosition.z - blockSize/2),
                Vector3(blockPosition.x + blockSize/2, blockPosition.y + blockSize/2, blockPosition.z + blockSize/2)
            )

            // Check if the temporary player bounds intersect with the block bounds
            if (tempBounds.intersects(blockBounds)) {
                return false // Collision detected
            }
        }

        return true // No collision
    }

    private fun updatePlayerBounds() {
        playerBounds.set(
            Vector3(playerPosition.x - playerSize.x/2, playerPosition.y - playerSize.y/2, playerPosition.z - playerSize.z/2),
            Vector3(playerPosition.x + playerSize.x/2, playerPosition.y + playerSize.y/2, playerPosition.z + playerSize.z/2)
        )
    }

    private fun updatePlayerRotation(deltaTime: Float) {
        // Calculate the shortest rotation path
        var rotationDifference = playerTargetRotationY - playerCurrentRotationY

        // Handle wrap-around (e.g., from 350° to 10°)
        if (rotationDifference > 180f) {
            rotationDifference -= 360f
        } else if (rotationDifference < -180f) {
            rotationDifference += 360f
        }

        // Apply smooth rotation
        if (kotlin.math.abs(rotationDifference) > 1f) { // Small threshold to avoid jittering
            val rotationStep = rotationSpeed * deltaTime
            if (rotationDifference > 0f) {
                playerCurrentRotationY += kotlin.math.min(rotationStep, rotationDifference)
            } else {
                playerCurrentRotationY += kotlin.math.max(-rotationStep, rotationDifference)
            }

            // Keep rotation in 0-360 range
            if (playerCurrentRotationY >= 360f) {
                playerCurrentRotationY -= 360f
            } else if (playerCurrentRotationY < 0f) {
                playerCurrentRotationY += 360f
            }
        } else {
            // Snap to target if close enough
            playerCurrentRotationY = playerTargetRotationY
        }
    }

    private fun handleLeftClick(screenX: Int, screenY: Int) {
        val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())

        when (selectedTool) {
            Tool.BLOCK -> placeBlock(ray)
            Tool.PLAYER -> placePlayer(ray)
        }
    }

    private fun placeBlock(ray: Ray) {
        // Find intersection with Y=0 plane or existing blocks
        val intersection = Vector3()
        val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            // Snap to grid
            val gridX = floor(intersection.x / blockSize) * blockSize
            val gridZ = floor(intersection.z / blockSize) * blockSize

            // Check if block already exists at this position
            val existingBlock = blocks.find { instance ->
                val pos = Vector3()
                instance.transform.getTranslation(pos)
                kotlin.math.abs(pos.x - (gridX + blockSize/2)) < 0.1f &&
                    kotlin.math.abs(pos.z - (gridZ + blockSize/2)) < 0.1f
            }

            if (existingBlock == null) {
                addBlock(gridX, 0f, gridZ)
                println("Block placed at: $gridX, 0, $gridZ") // Debug output
            } else {
                println("Block already exists at this position") // Debug output
            }
        } else {
            println("No intersection found with ground plane") // Debug output
        }
    }

    private fun getBlockAtRay(ray: Ray): ModelInstance? {
        var closestBlock: ModelInstance? = null
        var closestDistance = Float.MAX_VALUE

        for (block in blocks) {
            val blockPosition = Vector3()
            block.transform.getTranslation(blockPosition)

            // Create bounding box for the block
            val blockBounds = BoundingBox()
            blockBounds.set(
                Vector3(blockPosition.x - blockSize/2, blockPosition.y - blockSize/2, blockPosition.z - blockSize/2),
                Vector3(blockPosition.x + blockSize/2, blockPosition.y + blockSize/2, blockPosition.z + blockSize/2)
            )

            // Check if ray intersects with this block's bounding box
            val intersection = Vector3()
            if (com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, blockBounds, intersection)) {
                val distance = ray.origin.dst(intersection)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestBlock = block
                }
            }
        }

        return closestBlock
    }

    private fun removeBlock(blockToRemove: ModelInstance) {
        val blockPosition = Vector3()
        blockToRemove.transform.getTranslation(blockPosition)

        // Remove from the blocks array
        blocks.removeValue(blockToRemove, true)

        println("Block removed at: ${blockPosition.x - blockSize/2}, ${blockPosition.y - blockSize/2}, ${blockPosition.z - blockSize/2}")
    }

    private fun placePlayer(ray: Ray) {
        val intersection = Vector3()
        val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            // Snap to grid
            val gridX = floor(intersection.x / blockSize) * blockSize + blockSize/2
            val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize/2

            // Check if the new position would cause a collision
            if (canMoveTo(gridX, 2f, gridZ)) {
                playerPosition.set(gridX, 2f, gridZ)
                updatePlayerBounds()
                println("Player placed at: $gridX, 2, $gridZ") // Debug output
            } else {
                println("Cannot place player here - collision with block") // Debug output
            }
        } else {
            println("No intersection found for player placement") // Debug output
        }
    }

    private fun addBlock(x: Float, y: Float, z: Float) {
        val blockInstance = ModelInstance(blockModel)
        blockInstance.transform.setTranslation(x + blockSize/2, y + blockSize/2, z + blockSize/2)
        blocks.add(blockInstance)
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

        // Handle player input
        handlePlayerInput()

        // Update player billboard transformation
        updatePlayerTransform()

        // Render 3D scene
        modelBatch.begin(camera)

        // Render all blocks
        for (block in blocks) {
            modelBatch.render(block, environment)
        }

        // Render 3D player
        modelBatch.render(playerInstance, environment)

        modelBatch.end()

        // Render UI
        if (isUIVisible) {
            stage.act()
            stage.draw()
        }
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()
    }

    override fun dispose() {
        modelBatch.dispose()
        spriteBatch.dispose()
        blockModel.dispose()
        playerModel.dispose()
        playerTexture.dispose()
        stage.dispose()
        skin.dispose()
    }
}
