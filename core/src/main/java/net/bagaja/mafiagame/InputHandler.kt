package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.math.Vector2
import net.bagaja.mafiagame.UIManager.Tool

class InputHandler(
    private val uiManager: UIManager,
    private val cameraManager: CameraManager,
    private val blockSystem: BlockSystem,
    private val onLeftClick: (screenX: Int, screenY: Int) -> Unit,
    private val onRightClickAttemptBlockRemove: (screenX: Int, screenY: Int) -> Boolean
) {
    private var isRightMousePressed = false
    private var isLeftMousePressed = false
    private var lastMouseX = 0f
    private var lastMouseY = 0f
    private var isBlockSelectionMode = false

    // Variables for continuous block placement/removal
    private var continuousActionTimer = 0f
    private val continuousActionDelay = 0.1f // 100ms delay between actions
    private var lastPlacementX = -1
    private var lastPlacementY = -1
    private var lastRemovalX = -1
    private var lastRemovalY = -1

    fun initialize() {
        val inputMultiplexer = InputMultiplexer()

        // Add custom input processor first
        inputMultiplexer.addProcessor(object : InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                // Check if click is over UI
                val stageCoords = uiManager.getStage().screenToStageCoordinates(Vector2(screenX.toFloat(), screenY.toFloat()))
                val actorHit = uiManager.getStage().hit(stageCoords.x, stageCoords.y, true)

                // If we clicked on UI, let the stage handle it
                if (actorHit != null && uiManager.isUIVisible) {
                    return false
                }

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
                            return true // Block was removed, event handled.
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
                } else {
                    // Normal camera zoom
                    cameraManager.handleMouseScroll(amountY)
                    return true
                }
            }

            override fun keyDown(keycode: Int): Boolean {
                when (keycode) {
                    Input.Keys.H -> {
                        uiManager.toggleVisibility()
                        return true
                    }
                    Input.Keys.B -> {
                        isBlockSelectionMode = true
                        uiManager.showBlockSelection()
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
                    Input.Keys.NUMPAD_1 -> {
                        uiManager.selectedTool = Tool.BLOCK
                    }
                    Input.Keys.NUMPAD_2 -> {
                        uiManager.selectedTool = Tool.PLAYER
                    }
                    Input.Keys.NUMPAD_3 -> {
                        uiManager.selectedTool = Tool.OBJECT
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
                }
                return false
            }
        })

        inputMultiplexer.addProcessor(uiManager.getStage())
        Gdx.input.inputProcessor = inputMultiplexer
    }

    fun update(deltaTime: Float) {
        continuousActionTimer += deltaTime

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
    }

    // Helper method to determine if we're in block removal mode vs camera drag mode
    private fun isBlockBeingRemoved(): Boolean {
        // We're in block removal mode if the last removal position is set
        return lastRemovalX != -1 && lastRemovalY != -1
    }
}
