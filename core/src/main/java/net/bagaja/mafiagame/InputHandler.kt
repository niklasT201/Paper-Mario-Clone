package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.math.Vector3
import net.bagaja.mafiagame.UIManager.Tool

class InputHandler(
    private val game: MafiaGame,
    private val uiManager: UIManager,
    private val cameraManager: CameraManager,
    private val blockSystem: BlockSystem,
    private val objectSystem: ObjectSystem,
    private val itemSystem: ItemSystem,
    private val carSystem: CarSystem,
    private val houseSystem: HouseSystem,
    private val backgroundSystem: BackgroundSystem,
    private val parallaxSystem: ParallaxBackgroundSystem,
    private val interiorSystem: InteriorSystem,
    private val enemySystem: EnemySystem,
    private val npcSystem: NPCSystem,
    private val particleSystem: ParticleSystem,
    private val spawnerSystem: SpawnerSystem,
    private val teleporterSystem: TeleporterSystem,
    private val sceneManager: SceneManager,
    private val roomTemplateManager: RoomTemplateManager,
    private val shaderEffectManager: ShaderEffectManager,
) {
    private var isRightMousePressed = false
    private var isLeftMousePressed = false
    private var lastMouseX = 0f
    private var lastMouseY = 0f
    private var isBlockSelectionMode = false
    private var isObjectSelectionMode = false
    private var isItemSelectionMode = false
    private var isCarSelectionMode = false
    private var isHouseSelectionMode = false
    private var isBackgroundSelectionMode = false
    private var isParallaxSelectionMode = false
    private var isInteriorSelectionMode = false
    private var isEnemySelectionMode = false
    private var isNPCSelectionMode = false
    private var isParticleSelectionMode = false
    private var isTimeSpeedUpPressed = false

    // Variables for continuous block placement/removal
    private var continuousActionTimer = 0f
    private val continuousActionDelay = 0.1f
    private var lastPlacementX = -1
    private var lastPlacementY = -1
    private var lastRemovalX = -1
    private var lastRemovalY = -1

    // Variables for continuous fine positioning
    private var continuousFineTimer = 0f
    private val continuousFineDelay = 0.05f // Faster for smooth movement
    private var leftPressed = false
    private var rightPressed = false
    private var upPressed = false
    private var downPressed = false
    private var pageUpPressed = false
    private var pageDownPressed = false

    // NEW: Helper function to handle preview logic to avoid code duplication
    private fun handleBackgroundPreviewUpdate(screenX: Int, screenY: Int) {
        if (uiManager.selectedTool == Tool.BACKGROUND) {
            val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
            val intersection = Vector3()
            val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

            if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
                val adjustedPos = backgroundSystem.updatePreview(intersection)
                uiManager.updatePlacementInfo(backgroundSystem.getPlacementInfo(adjustedPos))
            }
        } else {
            backgroundSystem.hidePreview()
            uiManager.updatePlacementInfo("") // Clear placement info text
        }
    }

    fun initialize() {
        val inputMultiplexer = InputMultiplexer()

        inputMultiplexer.addProcessor(uiManager.getStage())
        inputMultiplexer.addProcessor(object : InputAdapter() {

            // Handle mouse movement for real-time previews
            override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
                handleBackgroundPreviewUpdate(screenX, screenY)
                return false // Don't consume the event, let other things use it if needed
            }

            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                // Check if click is over UI
                /*
                val stageCoords = uiManager.getStage().screenToStageCoordinates(Vector2(screenX.toFloat(), screenY.toFloat()))
                val actorHit = uiManager.getStage().hit(stageCoords.x, stageCoords.y, true)

                // If we clicked on UI, let the stage handle it
                if (actorHit != null && uiManager.isUIVisible) {
                    return false
                }
                 */

                if (uiManager.isPauseMenuVisible()) {
                    return true
                }

                // Handle mouse input
                when (button) {
                    Input.Buttons.LEFT -> {
                        if (game.isEditorMode) {
                            // Check for special entry point placement mode
                            if (uiManager.isPlacingEntryPointMode) {
                                val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
                                houseSystem.handleEntryPointPlaceAction(ray, uiManager.houseRequiringEntryPoint!!)
                                return true // Consume the click
                            }

                            isLeftMousePressed = true
                            val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
                            when (uiManager.selectedTool) {
                                Tool.BLOCK -> blockSystem.handlePlaceAction(ray)
                                Tool.PLAYER -> game.playerSystem.placePlayer(ray, sceneManager)
                                Tool.OBJECT -> { objectSystem.handlePlaceAction(ray, objectSystem.currentSelectedObject) }
                                Tool.ITEM -> itemSystem.handlePlaceAction(ray)
                                Tool.CAR -> carSystem.handlePlaceAction(ray)
                                Tool.HOUSE -> houseSystem.handlePlaceAction(ray)
                                Tool.BACKGROUND -> backgroundSystem.handlePlaceAction(ray)
                                Tool.PARALLAX -> parallaxSystem.handlePlaceAction(ray)
                                Tool.INTERIOR -> interiorSystem.handlePlaceAction(ray)
                                Tool.ENEMY -> enemySystem.handlePlaceAction(ray)
                                Tool.NPC -> npcSystem.handlePlaceAction(ray)
                                Tool.PARTICLE -> particleSystem.handlePlaceAction(ray)
                            }
                            // Reset timer and track position for continuous placement
                            continuousActionTimer = 0f
                            lastPlacementX = screenX
                            lastPlacementY = screenY

                            // Hide preview right after placing
                            if (uiManager.selectedTool == Tool.BACKGROUND) {
                                backgroundSystem.hidePreview()
                            }
                            return true
                        }
                    }
                    Input.Buttons.RIGHT -> {
                        // Cancel a charged throw
                        if (!game.isEditorMode && game.playerSystem.isChargingThrow()) {
                            return true
                        }

                        // PRIORITY 2: Handle removal actions in Editor Mode.
                        if (game.isEditorMode) {
                            // Try to remove a block. If successful, consume the event.
                            val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())

                            // Handle cancelling teleporter linking first
                            if (teleporterSystem.isLinkingMode) {
                                teleporterSystem.cancelLinking()
                                return true // Consume the click
                            }

                            if (uiManager.selectedTool == Tool.HOUSE) {
                                val entryPointToSelect = particleSystem.raycastSystem.getEntryPointAtRay(ray, sceneManager.activeEntryPoints)
                                if (entryPointToSelect != null) {
                                    game.lastPlacedInstance = entryPointToSelect
                                    println("Selected Entry Point ${entryPointToSelect.id} for fine positioning.")
                                    uiManager.updatePlacementInfo("Selected Entry Point for Fine Positioning (F key)")
                                    return true // Consume the click, don't rotate camera
                                }
                            }

                            var removed = false
                            when (uiManager.selectedTool) {
                                Tool.BLOCK -> removed = blockSystem.handleRemoveAction(ray)
                                Tool.OBJECT -> removed = objectSystem.handleRemoveAction(ray)
                                Tool.ITEM -> removed = itemSystem.handleRemoveAction(ray)
                                Tool.CAR -> removed = carSystem.handleRemoveAction(ray)
                                Tool.HOUSE -> removed = houseSystem.handleRemoveAction(ray)
                                Tool.BACKGROUND -> removed = backgroundSystem.handleRemoveAction(ray)
                                Tool.PARALLAX -> removed = parallaxSystem.handleRemoveAction(ray)
                                Tool.INTERIOR -> removed = interiorSystem.handleRemoveAction(ray)
                                Tool.ENEMY -> removed = enemySystem.handleRemoveAction(ray)
                                Tool.NPC -> removed = npcSystem.handleRemoveAction(ray)
                                Tool.PLAYER, Tool.PARTICLE -> { /* No removal action */ }
                            }

                            if (removed) {
                                isRightMousePressed = true
                                // Reset timer and track position for continuous removal
                                continuousActionTimer = 0f
                                lastRemovalX = screenX
                                lastRemovalY = screenY
                                return true
                            }
                            // No object removed, handle as camera drag
                        }
                        isRightMousePressed = true
                        lastMouseX = screenX.toFloat()
                        lastMouseY = screenY.toFloat()
                        return true
                    }
                }
                return false
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                when (button) {
                    Input.Buttons.LEFT -> isLeftMousePressed = false
                    Input.Buttons.RIGHT -> isRightMousePressed = false
                }
                return true
            }

            override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
                // Also update the preview when dragging
                handleBackgroundPreviewUpdate(screenX, screenY)

                if (isRightMousePressed && !isBlockBeingRemoved()) {
                    // Only handle camera drag if we're not in block removal mode
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
                    if (amountY > 0) blockSystem.nextBlock() else blockSystem.previousBlock()
                    uiManager.updateBlockSelection()
                    return true
                } else if (isObjectSelectionMode) {
                    // Use mouse scroll to change objects
                    if (amountY > 0) objectSystem.nextObject() else objectSystem.previousObject()
                    uiManager.updateObjectSelection()
                    return true
                } else if (isItemSelectionMode) {
                    // Use mouse scroll to change items
                    if (amountY > 0) itemSystem.nextItem() else itemSystem.previousItem()
                    uiManager.updateItemSelection()
                    return true
                } else if (isCarSelectionMode) {
                    // Use mouse scroll to change cars
                    if (amountY > 0) carSystem.nextCar() else carSystem.previousCar()
                    uiManager.updateCarSelection()
                    return true
                } else if (isHouseSelectionMode) {
                    // Use mouse scroll to change houses
                    if (amountY > 0) houseSystem.nextHouse() else houseSystem.previousHouse()
                    uiManager.updateHouseSelection()
                    return true
                } else if (isBackgroundSelectionMode) {
                    if (amountY > 0) backgroundSystem.nextBackground() else backgroundSystem.previousBackground()
                    uiManager.updateBackgroundSelection()
                    return true
                } else if (isParallaxSelectionMode) {
                    if (amountY > 0) uiManager.nextParallaxImage() else uiManager.previousParallaxImage()
                    return true
                } else if (isInteriorSelectionMode) {
                    if (amountY > 0) interiorSystem.nextInterior() else interiorSystem.previousInterior()
                    uiManager.updateInteriorSelection()
                    return true
                } else if (isEnemySelectionMode) {
                    // Check for a modifier key to switch between type and behavior
                    if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                        // Scroll through behaviors
                        if (amountY > 0) enemySystem.nextBehavior() else enemySystem.previousBehavior()
                    } else {
                        // Scroll through enemy types
                        if (amountY > 0) enemySystem.nextEnemyType() else enemySystem.previousEnemyType()
                    }
                    uiManager.updateEnemySelection() // Update the UI to reflect the change
                    return true
                } else if (isNPCSelectionMode) {
                    if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                        if (amountY > 0) npcSystem.nextBehavior() else npcSystem.previousBehavior()
                    } else {
                        if (amountY > 0) npcSystem.nextNPCType() else npcSystem.previousNPCType()
                    }
                    uiManager.updateNPCSelection()
                    return true
                } else if (isParticleSelectionMode) { // ADD THIS BLOCK
                    if (amountY > 0) particleSystem.nextEffect() else particleSystem.previousEffect()
                    uiManager.updateParticleSelection()
                    return true
                } else {
                    // Normal camera zoom
                    cameraManager.handleMouseScroll(amountY)
                    return true
                }
            }

            override fun keyDown(keycode: Int): Boolean {
                // If the user is typing in a UI text field, DO NOT process any game keybinds.
                if (uiManager.getStage().keyboardFocus != null) {
                    return false
                }

                // Camera Flip Hotkey
                if (keycode == Input.Keys.TAB) {
                    // Only flip if we are in player camera mode
                    if (!cameraManager.isFreeCameraMode) {
                        cameraManager.flipCamera()
                        return true // Consume the key press
                    }
                }

                if (keycode == Input.Keys.ESCAPE) {
                    // Check for cancelling entry point placement
                    if (uiManager.isPlacingEntryPointMode) {
                        uiManager.exitEntryPointPlacementMode()
                        return true
                    }

                    // First, handle cancelling any ongoing actions
                    if (game.teleporterSystem.isLinkingMode) {
                        game.teleporterSystem.cancelLinking()
                        return true
                    }
                    // If no actions to cancel, toggle the pause menu.
                    uiManager.togglePauseMenu()
                    return true
                }

                if (keycode == Input.Keys.F12) {
                    BillboardShader.DEBUG_SHADER_INFO = !BillboardShader.DEBUG_SHADER_INFO
                    val status = if (BillboardShader.DEBUG_SHADER_INFO) "ON" else "OFF"
                    uiManager.updatePlacementInfo("Shader Debug Info: $status")
                    return true
                }

                if (uiManager.isPauseMenuVisible()) {
                    return true
                }

                if (keycode == Input.Keys.F11) {
                    uiManager.toggleFpsLabel()
                    return true
                }

                if (keycode == Input.Keys.Z) {
                    if (!game.isEditorMode) {
                        game.targetingIndicatorSystem.toggle()
                        val status = if (game.targetingIndicatorSystem.isEnabled()) "ON" else "OFF"
                        // Provide feedback to the player via the UI
                        uiManager.updatePlacementInfo("Targeting Indicator: $status")
                        return true // Consume the key press
                    }
                }

                if (keycode == Input.Keys.F8) {
                    game.toggleEditorMode()
                    val modeStatus = if (game.isEditorMode) "EDITOR" else "GAME"
                    uiManager.updatePlacementInfo("Mode switched to: $modeStatus")
                    return true
                }

                // EDITOR MODE CHECK
                if (game.isEditorMode) {
                    // Shader effect controls
                    if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) && keycode == Input.Keys.S) {
                        uiManager.showSaveRoomDialog(sceneManager)
                        return true // Consume the input
                    }

                    // LOAD TEMPLATE HOTKEY
                    if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) && keycode == Input.Keys.L) {
                        val firstTemplate = roomTemplateManager.getAllTemplates().firstOrNull()
                        if (firstTemplate != null) {
                            sceneManager.loadTemplateIntoCurrentInterior(firstTemplate.id)
                            uiManager.updatePlacementInfo("Loaded template: ${firstTemplate.name}")
                        } else {
                            println("No saved templates to load!")
                            uiManager.updatePlacementInfo("No saved templates to load!")
                        }
                        return true // Consume the input
                    }

                    if (isHouseSelectionMode) {
                        when (keycode) {
                            Input.Keys.UP -> {
                                uiManager.navigateHouseRooms(-1)
                                return true
                            }
                            Input.Keys.DOWN -> {
                                uiManager.navigateHouseRooms(1)
                                return true
                            }
                            Input.Keys.ENTER -> {
                                uiManager.selectHouseRoom()
                                return true
                            }
                        }
                    }

                    when (keycode) {
                        Input.Keys.F1 -> {
                            uiManager.toggleVisibility()
                            return true
                        }
                        Input.Keys.F2 -> {
                            shaderEffectManager.nextEffect()
                            uiManager.updatePlacementInfo("Shader Effect: ${shaderEffectManager.getCurrentEffect().displayName}")
                            return true
                        }
                        Input.Keys.F3 -> {
                            shaderEffectManager.previousEffect()
                            uiManager.updatePlacementInfo("Shader Effect: ${shaderEffectManager.getCurrentEffect().displayName}")
                            return true
                        }
                        Input.Keys.F4 -> {
                            uiManager.toggleShaderEffectUI()
                            return true
                        }
                        Input.Keys.F5 -> {
                            val isBright = game.lightingManager.toggleBuildModeBrightness()
                            val status = if (isBright) "ON" else "OFF"
                            uiManager.updatePlacementInfo("Build Mode Brightness: $status")
                            return true
                        }
                        Input.Keys.K -> {
                            uiManager.toggleSkyCustomizationUI()
                            return true
                        }
                        Input.Keys.C -> {
                            cameraManager.toggleFreeCameraMode()
                            return true
                        }
                        Input.Keys.G -> {
                            // MODIFIED: Split the logic for the 'G' key
                            if (uiManager.selectedTool == Tool.BLOCK) {
                                // If the block tool is active, toggle the collision wireframes
                                game.toggleBlockCollisionOutlines()
                            } else {
                                // Otherwise, perform the original debug action for objects and invisible blocks
                                objectSystem.toggleDebugMode()
                                game.toggleInvisibleBlockOutlines()
                            }
                            return true
                        }

                        // Tool-specific hotkeys
                        Input.Keys.V -> {
                            // Only cycle area when the block tool is active
                            if (uiManager.selectedTool == Tool.BLOCK) {
                                if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                                    // SHIFT+V cycles camera visibility
                                    blockSystem.nextCameraVisibility()
                                } else {
                                    // V alone cycles build area
                                    blockSystem.nextBuildMode()
                                }
                                uiManager.updateBlockSelection() // Update UI to show the new mode
                                return true
                            }
                            return false // Not in block mode, let other systems handle 'V' if needed
                        }
                        Input.Keys.T -> {
                            if (isBlockSelectionMode) {  // Only cycle shapes when in block mode
                                if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
                                    blockSystem.previousShape()
                                } else {
                                    blockSystem.nextShape()
                                }
                                uiManager.updateBlockSelection() // Update UI to show the new shape
                                return true
                            }
                            return false
                        }
                        Input.Keys.R -> {
                            if (isBlockSelectionMode) {
                                blockSystem.toggleRotationMode()
                                uiManager.updateBlockSelection()
                                return true
                            }
                            if (isInteriorSelectionMode) {
                                interiorSystem.rotateSelection()
                                uiManager.updateInteriorSelection()
                                return true
                            }
                        }
                        Input.Keys.Q, Input.Keys.E -> {
                            val reverse = (keycode == Input.Keys.E)
                            when {
                                uiManager.selectedTool == Tool.HOUSE -> {
                                    houseSystem.rotateSelection()
                                    uiManager.updatePlacementInfo("House Rotation: ${houseSystem.currentRotation}°")
                                }
                                uiManager.selectedTool == Tool.NPC -> {
                                    npcSystem.toggleRotation()
                                    val direction = if (npcSystem.currentRotation == 0f) "Right" else "Left"
                                    uiManager.updatePlacementInfo("NPC will face: $direction")
                                }
                                uiManager.selectedTool == Tool.BLOCK || isBlockSelectionMode -> {
                                    if (reverse) blockSystem.rotateCurrentBlockReverse() else blockSystem.rotateCurrentBlock()
                                    uiManager.updateBlockSelection()
                                }
                            }
                            return true
                        }

                        Input.Keys.B -> { if (!isBlockSelectionMode && !isObjectSelectionMode && !isItemSelectionMode && !isCarSelectionMode && !isHouseSelectionMode && !isBackgroundSelectionMode) { isBlockSelectionMode = true; uiManager.showBlockSelection(); }; return true }
                        Input.Keys.O -> { if (!isObjectSelectionMode && !isBlockSelectionMode && !isItemSelectionMode && !isCarSelectionMode && !isHouseSelectionMode && !isBackgroundSelectionMode) { isObjectSelectionMode = true; uiManager.showObjectSelection(); }; return true }
                        Input.Keys.I -> { if (!isItemSelectionMode && !isBlockSelectionMode && !isObjectSelectionMode && !isCarSelectionMode && !isHouseSelectionMode && !isBackgroundSelectionMode) { isItemSelectionMode = true; uiManager.showItemSelection(); }; return true }
                        Input.Keys.M -> { if (!isCarSelectionMode && !isBlockSelectionMode && !isObjectSelectionMode && !isItemSelectionMode && !isHouseSelectionMode && !isBackgroundSelectionMode) { isCarSelectionMode = true; uiManager.showCarSelection(); }; return true }
                        Input.Keys.H -> { if (!isHouseSelectionMode && !isBlockSelectionMode && !isObjectSelectionMode && !isItemSelectionMode && !isCarSelectionMode && !isBackgroundSelectionMode) { isHouseSelectionMode = true; uiManager.showHouseSelection(); }; return true }
                        Input.Keys.N -> { if (!isBackgroundSelectionMode && !isBlockSelectionMode && !isObjectSelectionMode && !isItemSelectionMode && !isCarSelectionMode && !isHouseSelectionMode) { isBackgroundSelectionMode = true; uiManager.showBackgroundSelection(); }; return true }
                        Input.Keys.J -> { if (!isInteriorSelectionMode && !isItemSelectionMode && !isBlockSelectionMode && !isObjectSelectionMode && !isCarSelectionMode && !isHouseSelectionMode && !isBackgroundSelectionMode) { isInteriorSelectionMode = true; uiManager.showInteriorSelection(); }; return true }
                        Input.Keys.Y -> { if (!isEnemySelectionMode && !isBlockSelectionMode && !isObjectSelectionMode && !isItemSelectionMode && !isCarSelectionMode && !isHouseSelectionMode && !isBackgroundSelectionMode && !isInteriorSelectionMode && !isNPCSelectionMode) { isEnemySelectionMode = true; uiManager.showEnemySelection(); }; return true }
                        Input.Keys.U -> { if (!isNPCSelectionMode && !isEnemySelectionMode && !isBlockSelectionMode&& !isObjectSelectionMode && !isItemSelectionMode && !isCarSelectionMode && !isHouseSelectionMode && !isBackgroundSelectionMode && !isInteriorSelectionMode) { isNPCSelectionMode = true; uiManager.showNPCSelection(); }; return true }
                        Input.Keys.X -> { if (!isParticleSelectionMode && !isNPCSelectionMode && !isEnemySelectionMode && !isBlockSelectionMode && !isObjectSelectionMode && !isItemSelectionMode && !isCarSelectionMode && !isHouseSelectionMode && !isBackgroundSelectionMode && !isInteriorSelectionMode) { isParticleSelectionMode = true; uiManager.showParticleSelection(); }; return true }

                        // Other special keys
                        Input.Keys.L -> {
                            if (isHouseSelectionMode) { houseSystem.toggleLockState(); uiManager.updateHouseSelection(); return true }
                            if (isCarSelectionMode) { carSystem.toggleLockState(); uiManager.updateCarSelection(); return true }
                            if (isParallaxSelectionMode) { uiManager.nextParallaxLayer(); return true }
                            uiManager.toggleLightSourceUI()
                            return true
                        }
                        // Key to cycle background placement modes
                        Input.Keys.P -> {
                            // If parallax tool is active, this key now shows the parallax selection UI
                            if (uiManager.selectedTool == Tool.PARALLAX) { if (!isParallaxSelectionMode) { isParallaxSelectionMode = true; uiManager.showParallaxSelection(); }; return true }
                            if (uiManager.selectedTool == Tool.BACKGROUND) { backgroundSystem.cyclePlacementMode(); handleBackgroundPreviewUpdate(Gdx.input.x, Gdx.input.y); }
                            return true
                        }
                        Input.Keys.F -> { getCurrentPositionableSystem()?.toggleFinePosMode(); return true }

                        // Numpad tool selection
                        Input.Keys.NUMPAD_1 -> uiManager.selectedTool = Tool.BLOCK
                        Input.Keys.NUMPAD_2 -> uiManager.selectedTool = Tool.PLAYER
                        Input.Keys.NUMPAD_3 -> uiManager.selectedTool = Tool.OBJECT
                        Input.Keys.NUMPAD_4 -> uiManager.selectedTool = Tool.ITEM
                        Input.Keys.NUMPAD_5 -> uiManager.selectedTool = Tool.CAR
                        Input.Keys.NUMPAD_6 -> uiManager.selectedTool = Tool.HOUSE
                        Input.Keys.NUMPAD_7 -> uiManager.selectedTool = Tool.BACKGROUND
                        Input.Keys.NUMPAD_8 -> uiManager.selectedTool = Tool.PARALLAX
                        Input.Keys.NUMPAD_9 -> uiManager.selectedTool = Tool.INTERIOR
                        Input.Keys.NUMPAD_0 -> uiManager.selectedTool = Tool.ENEMY
                        Input.Keys.NUM_7 -> uiManager.selectedTool = Tool.NPC
                        Input.Keys.NUM_6 -> uiManager.selectedTool = Tool.PARTICLE
                        // Fine positioning controls
                        Input.Keys.LEFT -> { if (getCurrentPositionableSystem()?.finePosMode == true) { leftPressed = true; continuousFineTimer = 0f; return true } }
                        Input.Keys.RIGHT -> { if (getCurrentPositionableSystem()?.finePosMode == true) { rightPressed = true; continuousFineTimer = 0f; return true } }
                        Input.Keys.UP -> { if (getCurrentPositionableSystem()?.finePosMode == true) { upPressed = true; continuousFineTimer = 0f; return true } }
                        Input.Keys.DOWN -> { if (getCurrentPositionableSystem()?.finePosMode == true) { downPressed = true; continuousFineTimer = 0f; return true } }
                        Input.Keys.NUM_0 -> { if (getCurrentPositionableSystem()?.finePosMode == true) { pageUpPressed = true; continuousFineTimer = 0f; return true } }
                        Input.Keys.NUM_9 -> { if (getCurrentPositionableSystem()?.finePosMode == true) { pageDownPressed = true; continuousFineTimer = 0f; return true } }

                        // Gameplay/Debug controls
                        Input.Keys.COMMA -> { isTimeSpeedUpPressed = true; return true }
                    }

                    // Update UI after tool selection
                    if (keycode in Input.Keys.NUMPAD_0..Input.Keys.NUMPAD_9 || keycode == Input.Keys.NUM_6 || keycode == Input.Keys.NUM_7) {
                        uiManager.updateToolDisplay()
                        // Update preview in case we switched to/from the background tool
                        handleBackgroundPreviewUpdate(Gdx.input.x, Gdx.input.y)
                    }
                }
                return false
            }

            override fun keyUp(keycode: Int): Boolean {
                when (keycode) {
                    Input.Keys.B -> { isBlockSelectionMode = false; uiManager.hideBlockSelection(); return true }
                    Input.Keys.O -> { isObjectSelectionMode = false; uiManager.hideObjectSelection(); return true }
                    Input.Keys.I -> { isItemSelectionMode = false; uiManager.hideItemSelection(); return true }
                    Input.Keys.M -> { isCarSelectionMode = false; uiManager.hideCarSelection(); return true }
                    Input.Keys.H -> { isHouseSelectionMode = false; uiManager.hideHouseSelection(); return true }
                    Input.Keys.N -> { isBackgroundSelectionMode = false; uiManager.hideBackgroundSelection(); return true }
                    Input.Keys.P -> {
                        if (isParallaxSelectionMode) {
                            isParallaxSelectionMode = false
                            uiManager.hideParallaxSelection()
                            return true
                        }
                    }
                    Input.Keys.J -> { isInteriorSelectionMode = false; uiManager.hideInteriorSelection(); return true }
                    Input.Keys.Y -> { isEnemySelectionMode = false; uiManager.hideEnemySelection(); return true }
                    Input.Keys.X -> { isParticleSelectionMode = false; uiManager.hideParticleSelection(); return true }
                    Input.Keys.U -> { isNPCSelectionMode = false; uiManager.hideNPCSelection(); return true }
                    // Release fine positioning keys
                    Input.Keys.LEFT -> { leftPressed = false; return true }
                    Input.Keys.RIGHT -> { rightPressed = false; return true }
                    Input.Keys.UP -> { upPressed = false; return true }
                    Input.Keys.DOWN -> { downPressed = false; return true }
                    Input.Keys.NUM_0 -> { pageUpPressed = false; return true }
                    Input.Keys.NUM_9 -> { pageDownPressed = false; return true }
                    Input.Keys.COMMA -> { isTimeSpeedUpPressed = false;return true }
                }
                return false
            }
        })
        Gdx.input.inputProcessor = inputMultiplexer
    }

    fun update(deltaTime: Float) {
        if (uiManager.getStage().keyboardFocus != null) {
            return
        }

        continuousActionTimer += deltaTime
        continuousFineTimer += deltaTime

        // Handle continuous block placement
        if (isLeftMousePressed && continuousActionTimer >= continuousActionDelay) {
            val currentMouseX = Gdx.input.x
            val currentMouseY = Gdx.input.y

            // Only place if mouse has moved or enough time has passed
            if (currentMouseX != lastPlacementX || currentMouseY != lastPlacementY) {
                val ray = cameraManager.camera.getPickRay(currentMouseX.toFloat(), currentMouseY.toFloat())
                when (uiManager.selectedTool) {
                    Tool.BLOCK -> blockSystem.handlePlaceAction(ray)
                    Tool.PLAYER -> game.playerSystem.placePlayer(ray, sceneManager)
                    Tool.OBJECT -> objectSystem.handlePlaceAction(ray, objectSystem.currentSelectedObject)
                    Tool.ITEM -> itemSystem.handlePlaceAction(ray)
                    Tool.CAR -> carSystem.handlePlaceAction(ray)
                    Tool.HOUSE -> houseSystem.handlePlaceAction(ray)
                    Tool.BACKGROUND -> backgroundSystem.handlePlaceAction(ray)
                    Tool.PARALLAX -> parallaxSystem.handlePlaceAction(ray)
                    Tool.INTERIOR -> interiorSystem.handlePlaceAction(ray)
                    Tool.ENEMY -> enemySystem.handlePlaceAction(ray)
                    Tool.NPC -> npcSystem.handlePlaceAction(ray)
                    Tool.PARTICLE -> particleSystem.handlePlaceAction(ray)
                }
                lastPlacementX = currentMouseX
                lastPlacementY = currentMouseY
                continuousActionTimer = 0f
            }
        }

        // Handle continuous block removal
        if (isRightMousePressed && isBlockBeingRemoved() && continuousActionTimer >= continuousActionDelay) {
            val currentMouseX = Gdx.input.x
            val currentMouseY = Gdx.input.y

            // Only remove if mouse has moved or enough time has passed
            if (currentMouseX != lastRemovalX || currentMouseY != lastRemovalY) {
                val ray = cameraManager.camera.getPickRay(currentMouseX.toFloat(), currentMouseY.toFloat())
                var removed = false
                when (uiManager.selectedTool) {
                    Tool.BLOCK -> removed = blockSystem.handleRemoveAction(ray)
                    Tool.OBJECT -> removed = objectSystem.handleRemoveAction(ray)
                    Tool.ITEM -> removed = itemSystem.handleRemoveAction(ray)
                    Tool.CAR -> removed = carSystem.handleRemoveAction(ray)
                    Tool.HOUSE -> removed = houseSystem.handleRemoveAction(ray)
                    Tool.BACKGROUND -> removed = backgroundSystem.handleRemoveAction(ray)
                    Tool.PARALLAX -> removed = parallaxSystem.handleRemoveAction(ray)
                    Tool.INTERIOR -> removed = interiorSystem.handleRemoveAction(ray)
                    Tool.ENEMY -> removed = enemySystem.handleRemoveAction(ray)
                    Tool.NPC -> removed = npcSystem.handleRemoveAction(ray)
                    Tool.PLAYER, Tool.PARTICLE -> { /* No removal action */ }
                }

                if (removed) {
                    lastRemovalX = currentMouseX
                    lastRemovalY = currentMouseY
                    continuousActionTimer = 0f
                }
            }
        }

        // Handle continuous fine positioning
        if (continuousFineTimer >= continuousFineDelay) {
            var deltaX = 0f
            var deltaY = 0f
            var deltaZ = 0f

            if (leftPressed) deltaX -= getCurrentFineStep()
            if (rightPressed) deltaX += getCurrentFineStep()
            if (downPressed) deltaZ += getCurrentFineStep()
            if (upPressed) deltaZ -= getCurrentFineStep()
            if (pageDownPressed) deltaY -= getCurrentFineStep()
            if (pageUpPressed) deltaY += getCurrentFineStep()

            // Only call if there's actual movement
            if (deltaX != 0f || deltaY != 0f || deltaZ != 0f) {
                game.handleFinePosMove(deltaX, deltaY, deltaZ)
                continuousFineTimer = 0f
            }
        }
    }

    // Helper method to determine if we're in block removal mode vs camera drag mode
    private fun isBlockBeingRemoved(): Boolean {
        // We're in block removal mode if the last removal position is set
        return lastRemovalX != -1 && lastRemovalY != -1
    }

    private fun getCurrentFineStep(): Float {
        return getCurrentPositionableSystem()?.fineStep ?: 0.25f
    }

    private fun getCurrentPositionableSystem(): IFinePositionable? {
        return when (uiManager.selectedTool) {
            Tool.OBJECT -> {
                // Check if the selected object is a teleporter
                if (objectSystem.currentSelectedObject == ObjectType.TELEPORTER) {
                    teleporterSystem // If so, return the TeleporterSystem
                } else {
                    objectSystem // Otherwise, return the normal
                }
            }
            Tool.CAR -> carSystem
            Tool.HOUSE -> houseSystem
            Tool.ITEM -> itemSystem
            Tool.BACKGROUND -> backgroundSystem
            Tool.INTERIOR -> interiorSystem
            Tool.ENEMY -> enemySystem
            Tool.NPC -> npcSystem
            else -> null
        }
    }

    fun isTimeSpeedUpActive(): Boolean = isTimeSpeedUpPressed
}
