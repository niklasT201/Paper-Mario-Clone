package net.bagaja.mafiagame

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
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
        // Calculate camera position based on angle and distance
        val radians = Math.toRadians(cameraAngleY.toDouble())
        val x = (Math.cos(radians) * cameraDistance).toFloat()
        val z = (Math.sin(radians) * cameraDistance).toFloat()

        // Position camera at calculated position, elevated to see the scene
        camera.position.set(x, 8f, z)
        camera.lookAt(0f, 2f, 0f) // Look at the center of the action
        camera.up.set(0f, 1f, 0f) // Ensure up vector is correct
        camera.update()
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
                    isRightMousePressed = true
                    lastMouseX = screenX.toFloat()
                    lastMouseY = screenY.toFloat()
                    return true
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
        var moved = false
        var currentMovementDirection = 0f

        // Store the original position in case we need to revert due to collision
        val originalPosition = Vector3(playerPosition)

        // Handle WASD input for player movement
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            val newX = playerPosition.x - playerSpeed * deltaTime
            if (canMoveTo(newX, playerPosition.y, playerPosition.z)) {
                playerPosition.x = newX
                moved = true
                currentMovementDirection = -1f // Moving left
            }
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            val newX = playerPosition.x + playerSpeed * deltaTime
            if (canMoveTo(newX, playerPosition.y, playerPosition.z)) {
                playerPosition.x = newX
                moved = true
                currentMovementDirection = 1f // Moving right
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
            // Player changed direction or started moving horizontally
            if (currentMovementDirection < 0f) {
                // Moving left - rotate to show left side (180 degrees)
                playerTargetRotationY = 180f
            } else {
                // Moving right - rotate to show right side (0 degrees)
                playerTargetRotationY = 0f
            }
            lastMovementDirection = currentMovementDirection
        } else if (currentMovementDirection == 0f && (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.S))) {
            // Only moving vertically, keep last horizontal direction or face forward
        }

        // Smoothly interpolate current rotation towards target rotation
        updatePlayerRotation(deltaTime)

        // Update player model position if moved
        if (moved) {
            // Keep player slightly above ground level
            playerPosition.y = 2f
            updatePlayerBounds()
        }
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
