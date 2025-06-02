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
    private lateinit var stage: Stage
    private lateinit var skin: Skin
    private lateinit var cameraManager: CameraManager

    // Block system
    private lateinit var blockSystem: BlockSystem

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
    private var playerTargetRotationY = 0f // Target rotation in degrees
    private var playerCurrentRotationY = 0f // Current rotation in degrees
    private val rotationSpeed = 360f // Degrees per second for smooth rotation
    private var lastMovementDirection = 0f // -1 for left, 1 for right, 0 for none

    // UI state
    private var selectedTool = Tool.BLOCK
    private var isUIVisible = true
    private var isBlockSelectionMode = false

    // Block size
    private val blockSize = 4f

    // Input handling
    private var isRightMousePressed = false
    private var lastMouseX = 0f
    private var lastMouseY = 0f

    // UI Elements for block selection
    private lateinit var blockSelectionTable: Table
    private lateinit var currentBlockLabel: Label
    private lateinit var blockPreviewImage: Image

    enum class Tool {
        BLOCK, PLAYER
    }

    override fun create() {
        setupGraphics()
        setupBlockSystem()
        setupModels()
        setupUI()
        setupInputHandling()

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

        // Setup environment and lighting
        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f))
        environment.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f))
    }

    private fun setupBlockSystem() {
        blockSystem = BlockSystem()
        blockSystem.initialize(blockSize)
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
            -playerSizeVisual/2, -playerSizeVisual/2, 0f,
            playerSizeVisual/2, -playerSizeVisual/2, 0f,
            playerSizeVisual/2, playerSizeVisual/2, 0f,
            -playerSizeVisual/2, playerSizeVisual/2, 0f,
            0f, 0f, 1f,
            playerMaterial,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
        )

        // Create player instance
        playerInstance = ModelInstance(playerModel)
        updatePlayerTransform()
    }

    private fun setupUI() {
        stage = Stage(ScreenViewport())

        // Load UI skin with custom font handling
        skin = try {
            val loadedSkin = Skin(Gdx.files.internal("ui/uiskin.json"))

            // Try to load and set custom font
            try {
                val customFont = com.badlogic.gdx.graphics.g2d.BitmapFont(Gdx.files.internal("ui/default.fnt"))
                loadedSkin.add("default-font", customFont, com.badlogic.gdx.graphics.g2d.BitmapFont::class.java)

                // Update existing styles to use the new font
                loadedSkin.get(Label.LabelStyle::class.java).font = customFont
                loadedSkin.get(TextButton.TextButtonStyle::class.java).font = customFont

                println("Custom font loaded successfully from ui/default.fnt")
            } catch (e: Exception) {
                println("Could not load custom font from ui/default.fnt: ${e.message}")
            }

            loadedSkin
        } catch (e: Exception) {
            println("Could not load UI skin from assets/ui/, using default skin with custom font")
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
        • Mouse wheel to zoom in/out (or block selection)
        • WASD to move player
        • H to toggle this UI
        • B to toggle Block Selection Mode
        • C to toggle Free Camera
        • 1 for Orbiting Camera (building)
        • 2 for Player Camera (Paper Mario)
        • Q/E to adjust camera angle (player mode)
        • R/T to adjust camera height (player mode)
        • Blocks snap to 4x4 grid
        • Paper Mario style rotation!
        • Player has collision detection!
        • Block Selection: Hold B + scroll to choose blocks!
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

        // Create block selection UI (top center)
        setupBlockSelectionUI()
    }

    private fun setupBlockSelectionUI() {
        // Create block selection table at the top center
        blockSelectionTable = Table()
        blockSelectionTable.setFillParent(true)
        blockSelectionTable.top()
        blockSelectionTable.pad(20f)

        // Create background for the block selection
        val backgroundTable = Table()
        backgroundTable.background = skin.getDrawable("default-round")

        // Current block label
        currentBlockLabel = Label("Current Block: ${blockSystem.currentSelectedBlock.displayName}", skin)
        currentBlockLabel.setFontScale(1.2f)

        // Add some spacing and styling
        backgroundTable.pad(15f)
        backgroundTable.add(currentBlockLabel).padBottom(10f).row()

        // Instructions for block selection
        val blockInstructions = Label("Hold [B] + Mouse Wheel to change blocks", skin)
        blockInstructions.setFontScale(0.8f)
        backgroundTable.add(blockInstructions)

        blockSelectionTable.add(backgroundTable)
        blockSelectionTable.setVisible(false) // Initially hidden

        stage.addActor(blockSelectionTable)
    }

    private fun updateBlockSelectionUI() {
        currentBlockLabel.setText("Current Block: ${blockSystem.currentSelectedBlock.displayName}")
    }

    private fun createDefaultSkin(): Skin {
        val skin = Skin()

        // Create a 1x1 white texture
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.WHITE)
        pixmap.fill()
        val texture = Texture(pixmap)
        pixmap.dispose()

        // Try to load custom font first, fallback to default
        val font = try {
            val customFont = com.badlogic.gdx.graphics.g2d.BitmapFont(Gdx.files.internal("ui/default.fnt"))
            println("Custom font loaded successfully from ui/default.fnt (fallback)")
            customFont
        } catch (e: Exception) {
            println("Could not load custom font from ui/default.fnt, using system default: ${e.message}")
            com.badlogic.gdx.graphics.g2d.BitmapFont()
        }

        // Add font to skin
        skin.add("default-font", font)

        // Create drawable for buttons
        val buttonTexture = com.badlogic.gdx.graphics.g2d.TextureRegion(texture)
        val buttonDrawable = com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(buttonTexture)

        // Add background drawable
        skin.add("default-round", buttonDrawable.tint(Color(0.2f, 0.2f, 0.2f, 0.8f)))

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

        // Add custom input processor first
        inputMultiplexer.addProcessor(object : com.badlogic.gdx.InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                // Check if click is over UI
                val stageCoords = stage.screenToStageCoordinates(com.badlogic.gdx.math.Vector2(screenX.toFloat(), screenY.toFloat()))
                val actorHit = stage.hit(stageCoords.x, stageCoords.y, true)

                // If we clicked on UI, let the stage handle it
                if (actorHit != null && isUIVisible) {
                    return false
                }

                // Handle mouse input
                if (button == Input.Buttons.LEFT) {
                    handleLeftClick(screenX, screenY)
                    return true
                } else if (button == Input.Buttons.RIGHT) {
                    // Check if we're starting a drag operation or clicking on a block
                    val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
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
                    val deltaX = screenX - lastMouseX
                    cameraManager.handleMouseDrag(deltaX)

                    lastMouseX = screenX.toFloat()
                    lastMouseY = screenY.toFloat()
                    return true
                }
                return false
            }

            override fun scrolled(amountX: Float, amountY: Float): Boolean {
                // Check if block selection mode is active
                if (isBlockSelectionMode) {
                    // Use mouse scroll to change blocks
                    if (amountY > 0) {
                        blockSystem.nextBlock()
                    } else if (amountY < 0) {
                        blockSystem.previousBlock()
                    }
                    updateBlockSelectionUI()
                    return true
                } else {
                    // Normal camera zoom
                    cameraManager.handleMouseScroll(amountY)
                    return true
                }
            }

            override fun keyDown(keycode: Int): Boolean {
                when (keycode) {
                    Input.Keys.H -> {
                        isUIVisible = !isUIVisible
                        return true
                    }
                    Input.Keys.B -> {
                        isBlockSelectionMode = true
                        blockSelectionTable.setVisible(true)
                        return true
                    }
                    Input.Keys.C -> {
                        cameraManager.toggleFreeCameraMode()
                        return true
                    }
                    // Camera mode switching
                    Input.Keys.NUM_1 -> {
                        cameraManager.switchToOrbitingCamera()
                        return true
                    }
                    Input.Keys.NUM_2 -> {
                        cameraManager.switchToPlayerCamera()
                        return true
                    }
                }
                return false
            }

            override fun keyUp(keycode: Int): Boolean {
                when (keycode) {
                    Input.Keys.B -> {
                        isBlockSelectionMode = false
                        blockSelectionTable.setVisible(false)
                        return true
                    }
                }
                return false
            }
        })

        inputMultiplexer.addProcessor(stage)
        Gdx.input.inputProcessor = inputMultiplexer
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
            Vector3(x - (playerSize.x/2 - horizontalShrink), y - playerSize.y/2, z - (playerSize.z/2 - horizontalShrink)),
            Vector3(x + (playerSize.x/2 - horizontalShrink), y + playerSize.y/2, z + (playerSize.z/2 - horizontalShrink))
        )

        for (gameBlock in gameBlocks) {
            val blockBounds = BoundingBox()
            blockBounds.set(
                Vector3(gameBlock.position.x - blockSize/2, gameBlock.position.y - blockSize/2, gameBlock.position.z - blockSize/2),
                Vector3(gameBlock.position.x + blockSize/2, gameBlock.position.y + blockSize/2, gameBlock.position.z + blockSize/2)
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
            Vector3(playerPosition.x - playerSize.x/2, playerPosition.y - playerSize.y/2, playerPosition.z - playerSize.z/2),
            Vector3(playerPosition.x + playerSize.x/2, playerPosition.y + playerSize.y/2, playerPosition.z + playerSize.z/2)
        )
    }

    private fun updatePlayerRotation(deltaTime: Float) {
        // Calculate the shortest rotation path
        var rotationDifference = playerTargetRotationY - playerCurrentRotationY

        // Handle wrap-around
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

            // Rotation in 0-360 range
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
        val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())

        when (selectedTool) {
            Tool.BLOCK -> placeBlock(ray)
            Tool.PLAYER -> placePlayer(ray)
        }
    }

    private fun placeBlock(ray: Ray) {
        // First, try to find intersection with existing blocks
        val hitBlock = getBlockAtRay(ray)

        if (hitBlock != null) {
            // We hit an existing block, place new block adjacent to it
            placeBlockAdjacentTo(ray, hitBlock)
        } else {
            // No block hit, place on ground (existing logic)
            val intersection = Vector3()
            val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

            if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
                // Snap to grid
                val gridX = floor(intersection.x / blockSize) * blockSize
                val gridZ = floor(intersection.z / blockSize) * blockSize

                // Check if block already exists at this position
                val existingBlock = gameBlocks.find { gameBlock ->
                    kotlin.math.abs(gameBlock.position.x - (gridX + blockSize/2)) < 0.1f &&
                        kotlin.math.abs(gameBlock.position.y - (blockSize/2)) < 0.1f &&
                        kotlin.math.abs(gameBlock.position.z - (gridZ + blockSize/2)) < 0.1f
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
            Vector3(hitBlock.position.x - blockSize/2, hitBlock.position.y - blockSize/2, hitBlock.position.z - blockSize/2),
            Vector3(hitBlock.position.x + blockSize/2, hitBlock.position.y + blockSize/2, hitBlock.position.z + blockSize/2)
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
                absY >= absX && absY >= absZ -> {
                    // Hit top or bottom face
                    newY += if (relativePos.y > 0) blockSize else -blockSize
                }
                absX >= absY && absX >= absZ -> {
                    // Hit left or right face
                    newX += if (relativePos.x > 0) blockSize else -blockSize
                }
                else -> {
                    // Hit front or back face
                    newZ += if (relativePos.z > 0) blockSize else -blockSize
                }
            }

            // Snap to grid
            val gridX = floor(newX / blockSize) * blockSize
            val gridY = floor(newY / blockSize) * blockSize
            val gridZ = floor(newZ / blockSize) * blockSize

            // Check if block already exists at this position
            val existingBlock = gameBlocks.find { gameBlock ->
                kotlin.math.abs(gameBlock.position.x - (gridX + blockSize/2)) < 0.1f &&
                    kotlin.math.abs(gameBlock.position.y - (gridY + blockSize/2)) < 0.1f &&
                    kotlin.math.abs(gameBlock.position.z - (gridZ + blockSize/2)) < 0.1f
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
                Vector3(gameBlock.position.x - blockSize/2, gameBlock.position.y - blockSize/2, gameBlock.position.z - blockSize/2),
                Vector3(gameBlock.position.x + blockSize/2, gameBlock.position.y + blockSize/2, gameBlock.position.z + blockSize/2)
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
            val gridX = floor(intersection.x / blockSize) * blockSize + blockSize/2
            val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize/2

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

        // Handle player input
        handlePlayerInput()
        updatePlayerTransform()

        // Render 3D scene
        modelBatch.begin(cameraManager.camera)

        // Render all blocks
        for (gameBlock in gameBlocks) {
            modelBatch.render(gameBlock.modelInstance, environment)
        }

        // Render 3D player
        modelBatch.render(playerInstance, environment)

        modelBatch.end()

        // Render UI
        if (isUIVisible) {
            stage.act()
            stage.draw()
        } else {
            // Always show block selection UI when it's active, even if main UI is hidden
            if (isBlockSelectionMode) {
                blockSelectionTable.act(Gdx.graphics.deltaTime)
                blockSelectionTable.draw(stage.batch, 1f)
            }
        }
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        cameraManager.resize(width, height)
    }

    override fun dispose() {
        modelBatch.dispose()
        spriteBatch.dispose()
        blockSystem.dispose()
        playerModel.dispose()
        playerTexture.dispose()
        stage.dispose()
        skin.dispose()
    }
}
