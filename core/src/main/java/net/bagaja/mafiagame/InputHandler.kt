package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import net.bagaja.mafiagame.UIManager.Tool

class InputHandler(
    private val uiManager: UIManager,
    private val cameraManager: CameraManager,
    private val blockSystem: BlockSystem,
    private val objectSystem: ObjectSystem,
    private val itemSystem: ItemSystem,
    private val carSystem: CarSystem,
    private val houseSystem: HouseSystem,
    private val backgroundSystem: BackgroundSystem,
    private val onLeftClick: (screenX: Int, screenY: Int) -> Unit,
    private val onRightClickAttemptBlockRemove: (screenX: Int, screenY: Int) -> Boolean,
    private val onFinePosMove: (deltaX: Float, deltaY: Float, deltaZ: Float) -> Unit
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

    fun initialize() {
        val inputMultiplexer = InputMultiplexer()

        // Add custom input processor first
        inputMultiplexer.addProcessor(uiManager.getStage()) // Stage first to catch UI clicks
        inputMultiplexer.addProcessor(object : InputAdapter() {
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

                // Handle mouse input
                when (button) {
                    Input.Buttons.LEFT -> {
                        isLeftMousePressed = true
                        onLeftClick(screenX, screenY)
                        // Reset timer and track position for continuous placement
                        continuousActionTimer = 0f
                        lastPlacementX = screenX
                        lastPlacementY = screenY
                        return true
                    }
                    Input.Buttons.RIGHT -> {
                        // Try to remove a block. If successful, consume the event.
                        if (onRightClickAttemptBlockRemove(screenX, screenY)) {
                            isRightMousePressed = true
                            // Reset timer and track position for continuous removal
                            continuousActionTimer = 0f
                            lastRemovalX = screenX
                            lastRemovalY = screenY
                            return true
                        }
                        // No block removed, handle as camera drag
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
                    Input.Buttons.LEFT -> {
                        isLeftMousePressed = false
                        return true
                    }
                    Input.Buttons.RIGHT -> {
                        isRightMousePressed = false
                        return true
                    }
                }
                return false
            }

            override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
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
                    if (amountY > 0) {
                        blockSystem.nextBlock()
                    } else if (amountY < 0) {
                        blockSystem.previousBlock()
                    }
                    uiManager.updateBlockSelection()
                    return true
                } else if (isObjectSelectionMode) {
                    // Use mouse scroll to change objects
                    if (amountY > 0) {
                        objectSystem.nextObject()
                    } else if (amountY < 0) {
                        objectSystem.previousObject()
                    }
                    uiManager.updateObjectSelection()
                    return true
                } else if (isItemSelectionMode) {
                    // Use mouse scroll to change items
                    if (amountY > 0) {
                        itemSystem.nextItem()
                    } else if (amountY < 0) {
                        itemSystem.previousItem()
                    }
                    uiManager.updateItemSelection()
                    return true
                } else if (isCarSelectionMode) {
                    // Use mouse scroll to change cars
                    if (amountY > 0) {
                        carSystem.nextCar()
                    } else if (amountY < 0) {
                        carSystem.previousCar()
                    }
                    uiManager.updateCarSelection()
                    return true
                } else if (isHouseSelectionMode) {
                    // Use mouse scroll to change houses
                    if (amountY > 0) {
                        houseSystem.nextHouse()
                    } else if (amountY < 0) {
                        houseSystem.previousHouse()
                    }
                    uiManager.updateHouseSelection()
                    return true
                } else if (isBackgroundSelectionMode) {
                    // Use mouse scroll to change backgrounds
                    if (amountY > 0) {
                        backgroundSystem.nextBackground()
                    } else if (amountY < 0) {
                        backgroundSystem.previousBackground()
                    }
                    uiManager.updateBackgroundSelection()
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

                when (keycode) {
                    Input.Keys.F1 -> {
                        uiManager.toggleVisibility()
                        return true
                    }
                    Input.Keys.H -> {
                        // House selection mode
                        if (!isBlockSelectionMode && !isObjectSelectionMode && !isItemSelectionMode && !isCarSelectionMode && !isBackgroundSelectionMode) {
                            isHouseSelectionMode = true
                            uiManager.showHouseSelection()
                        }
                        return true
                    }
                    Input.Keys.N -> {
                        // Background selection mode
                        if (!isBlockSelectionMode && !isObjectSelectionMode && !isItemSelectionMode && !isCarSelectionMode && !isHouseSelectionMode) {
                            isBackgroundSelectionMode = true
                            uiManager.showBackgroundSelection()
                        }
                        return true
                    }
                    Input.Keys.L -> {
                        uiManager.toggleLightSourceUI()
                        return true
                    }
                    Input.Keys.B -> {
                        // Only activate block selection if not in other selection modes
                        if (!isObjectSelectionMode && !isBackgroundSelectionMode) {
                            isBlockSelectionMode = true
                            uiManager.showBlockSelection()
                        }
                        return true
                    }
                    Input.Keys.O -> {
                        // Object selection mode (similar to B for blocks)
                        if (!isBlockSelectionMode && !isBackgroundSelectionMode) {
                            isObjectSelectionMode = true
                            uiManager.showObjectSelection()
                        }
                        return true
                    }
                    Input.Keys.I -> {
                        if (!isItemSelectionMode && !isBackgroundSelectionMode) {
                            isItemSelectionMode = true
                            uiManager.showItemSelection()
                        }
                        return true
                    }
                    Input.Keys.M -> {
                        // Car selection mode
                        if (!isBlockSelectionMode && !isObjectSelectionMode && !isItemSelectionMode && !isBackgroundSelectionMode) {
                            isCarSelectionMode = true
                            uiManager.showCarSelection()
                        }
                        return true
                    }
                    Input.Keys.F -> {
                        // Toggle fine positioning mode for objects OR cars
                        if (uiManager.selectedTool == Tool.OBJECT) {
                            objectSystem.toggleFinePosMode()
                        } else if (uiManager.selectedTool == Tool.CAR) {
                            carSystem.toggleFinePosMode()
                        }
                        return true
                    }
                    Input.Keys.G -> {
                        // Toggle debug mode for objects (to see invisible ones)
                        objectSystem.toggleDebugMode()
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
                    // Tool selection
                    Input.Keys.NUMPAD_1 -> {
                        uiManager.selectedTool = Tool.BLOCK
                        uiManager.updateToolDisplay()
                    }
                    Input.Keys.NUMPAD_2 -> {
                        uiManager.selectedTool = Tool.PLAYER
                        uiManager.updateToolDisplay()
                    }
                    Input.Keys.NUMPAD_3 -> {
                        uiManager.selectedTool = Tool.OBJECT
                        uiManager.updateToolDisplay()
                    }
                    Input.Keys.NUMPAD_4 -> {
                        uiManager.selectedTool = Tool.ITEM
                        uiManager.updateToolDisplay()
                    }
                    Input.Keys.NUMPAD_5 -> {
                        uiManager.selectedTool = Tool.CAR
                        uiManager.updateToolDisplay()
                    }
                    Input.Keys.NUMPAD_6 -> {
                        uiManager.selectedTool = Tool.HOUSE
                        uiManager.updateToolDisplay()
                    }
                    Input.Keys.NUMPAD_7 -> {
                        uiManager.selectedTool = Tool.BACKGROUND
                        uiManager.updateToolDisplay()
                    }
                    // Fine positioning controls
                    Input.Keys.LEFT -> {
                        if ((objectSystem.finePosMode && uiManager.selectedTool == Tool.OBJECT) ||
                            (carSystem.finePosMode && uiManager.selectedTool == Tool.CAR)) {
                            leftPressed = true
                            continuousFineTimer = 0f
                            return true
                        }
                    }
                    Input.Keys.RIGHT -> {
                        if (objectSystem.finePosMode && uiManager.selectedTool == Tool.OBJECT) {
                            rightPressed = true
                            continuousFineTimer = 0f
                            return true
                        }
                    }
                    Input.Keys.UP -> {
                        if (objectSystem.finePosMode && uiManager.selectedTool == Tool.OBJECT) {
                            upPressed = true
                            continuousFineTimer = 0f
                            return true
                        }
                    }
                    Input.Keys.DOWN -> {
                        if (objectSystem.finePosMode && uiManager.selectedTool == Tool.OBJECT) {
                            downPressed = true
                            continuousFineTimer = 0f
                            return true
                        }
                    }
                    Input.Keys.NUM_0 -> {
                        if (objectSystem.finePosMode && uiManager.selectedTool == Tool.OBJECT) {
                            pageUpPressed = true
                            continuousFineTimer = 0f
                            return true
                        }
                    }
                    Input.Keys.NUM_9 -> {
                        if (objectSystem.finePosMode && uiManager.selectedTool == Tool.OBJECT) {
                            pageDownPressed = true
                            continuousFineTimer = 0f
                            return true
                        }
                    }
                }
                return false
            }

            override fun keyUp(keycode: Int): Boolean {
                when (keycode) {
                    Input.Keys.B -> {
                        isBlockSelectionMode = false
                        uiManager.hideBlockSelection()
                        return true
                    }
                    Input.Keys.O -> {
                        isObjectSelectionMode = false
                        uiManager.hideObjectSelection()
                        return true
                    }
                    Input.Keys.I -> {
                        isItemSelectionMode = false
                        uiManager.hideItemSelection()
                        return true
                    }
                    Input.Keys.M -> {
                        isCarSelectionMode = false
                        uiManager.hideCarSelection()
                        return true
                    }
                    Input.Keys.H -> {
                        isHouseSelectionMode = false
                        uiManager.hideHouseSelection()
                        return true
                    }
                    Input.Keys.N -> {
                        isBackgroundSelectionMode = false
                        uiManager.hideBackgroundSelection()
                        return true
                    }
                    // Release fine positioning keys
                    Input.Keys.LEFT -> {
                        leftPressed = false
                        return true
                    }
                    Input.Keys.RIGHT -> {
                        rightPressed = false
                        return true
                    }
                    Input.Keys.UP -> {
                        upPressed = false
                        return true
                    }
                    Input.Keys.DOWN -> {
                        downPressed = false
                        return true
                    }
                    Input.Keys.NUM_0 -> {
                        pageUpPressed = false
                        return true
                    }
                    Input.Keys.NUM_9 -> {
                        pageDownPressed = false
                        return true
                    }
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
                onLeftClick(currentMouseX, currentMouseY)
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
                if (onRightClickAttemptBlockRemove(currentMouseX, currentMouseY)) {
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
                onFinePosMove(deltaX, deltaY, deltaZ)
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
        return when (uiManager.selectedTool) {
            Tool.OBJECT -> objectSystem.getFineStep()
            Tool.CAR -> carSystem.getFineStep()
            else -> 0.25f // default
        }
    }
}
