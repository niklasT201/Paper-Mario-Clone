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
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.viewport.ScreenViewport
import kotlin.math.abs
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
    private lateinit var playerSprite: TextureRegion
    private lateinit var playerModel: Model
    private lateinit var playerInstance: ModelInstance
    private lateinit var playerMaterial: Material

    // Game objects
    private val blocks = Array<ModelInstance>()
    private val playerPosition = Vector3(0f, 2f, 0f)
    private val playerSpeed = 8f // Units per second

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
            com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        )

        // Create a 3D plane/quad for the player (billboard)
        // Make it 4x4 to match your block size, but you can adjust as needed
        val playerSize = 4f
        playerModel = modelBuilder.createRect(
            -playerSize/2, -playerSize/2, 0f,  // bottom left
            playerSize/2, -playerSize/2, 0f,   // bottom right
            playerSize/2, playerSize/2, 0f,    // top right
            -playerSize/2, playerSize/2, 0f,   // top left
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
        • Paper Mario style side view!
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

        // Handle WASD input for player movement
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            playerPosition.x -= playerSpeed * deltaTime
            moved = true
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            playerPosition.x += playerSpeed * deltaTime
            moved = true
        }
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            playerPosition.z -= playerSpeed * deltaTime
            moved = true
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            playerPosition.z += playerSpeed * deltaTime
            moved = true
        }

        // Update player model position if moved
        if (moved) {
            // Keep player slightly above ground level
            playerPosition.y = 2f
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

            playerPosition.set(gridX, 2f, gridZ)
            println("Player placed at: $gridX, 2, $gridZ") // Debug output
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
        // Simple approach: just set position and make it face camera
        playerInstance.transform.idt() // Reset to identity matrix

        // Set position
        playerInstance.transform.setTranslation(playerPosition)

        // Calculate direction from player to camera
        val directionToCamera = Vector3()
        directionToCamera.set(camera.position).sub(playerPosition)
        directionToCamera.y = 0f
        directionToCamera.nor()

        // Calculate rotation angle around Y axis
        val angle = Math.atan2(directionToCamera.x.toDouble(), directionToCamera.z.toDouble())
        val angleDegrees = Math.toDegrees(angle).toFloat()

        // Apply rotation around Y axis to face camera
        playerInstance.transform.rotate(Vector3.Y, angleDegrees)

        // Apply position again (sometimes rotation can affect position)
        playerInstance.transform.setTranslation(playerPosition)
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
