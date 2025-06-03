package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.math.Vector2

class InputHandler(
    private val uiManager: UIManager,
    private val cameraManager: CameraManager,
    private val blockSystem: BlockSystem,
    private val onLeftClick: (screenX: Int, screenY: Int) -> Unit,
    private val onRightClickAttemptBlockRemove: (screenX: Int, screenY: Int) -> Boolean
) {
    private var isRightMousePressed = false
    private var lastMouseX = 0f
    private var lastMouseY = 0f
    private var isBlockSelectionMode = false

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
                        onLeftClick(screenX, screenY)
                        return true
                    }
                    Input.Buttons.RIGHT -> {
                        // Try to remove a block. If successful, consume the event.
                        if (onRightClickAttemptBlockRemove(screenX, screenY)) {
                            return true // Block was removed, event handled.
                        }
                        // No block removed, or onRightClickAttemptBlockRemove decided not to consume
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
}
