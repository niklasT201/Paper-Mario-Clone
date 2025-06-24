package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.OrthographicCamera // NEW: Import OrthographicCamera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import kotlin.math.cos
import kotlin.math.sin

class CameraManager {
    lateinit var camera: PerspectiveCamera
        private set

    // Camera specifically for 2D UI and overlays
    lateinit var uiCamera: OrthographicCamera
        private set

    // Camera modes
    enum class CameraMode {
        ORBITING,    // Original orbiting camera (for building/placing blocks)
        PLAYER,      // Paper Mario style camera that follows player
        FREE         // Free camera mode
    }

    private var currentCameraMode = CameraMode.ORBITING
    val isFreeCameraMode: Boolean
        get() = currentCameraMode == CameraMode.FREE

    // Free camera mode variables
    private var freeCameraPosition = Vector3(0f, 8f, 20f)
    private var freeCameraTarget = Vector3(0f, 2f, 0f)
    private val freeCameraSpeed = 15f
    private var freeCameraYaw = 0f
    private var freeCameraPitch = 0f
    private val cameraRotationSpeed = 90f

    // Orbiting camera mode variables (original)
    private var cameraDistance = 20f
    private var cameraAngleY = 0f

    // Player camera mode variables (Paper Mario style)
    private var playerCameraDistance = 12f // Distance behind/beside the player
    private var playerCameraHeight = 6f // Height above the player
    private var playerCameraAngle = 25f // Side view angle (0° = behind, 90° = side view)
    private val playerCameraSmoothing = 20f // How smoothly the camera follows
    private var currentPlayerCameraPosition = Vector3()
    private var targetPlayerCameraPosition = Vector3()
    private var playerPosition = Vector3(0f, 2f, 0f) // This will be updated from the game

    // Paper Mario style settings
    private val paperMarioSideAngle = 90f // Slightly angled side view
    private val paperMarioDistance = 15f
    private val paperMarioHeight = 8f

    fun initialize() {
        // Setup 3D camera
        camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

        // Initialize player camera settings for Paper Mario style
        playerCameraAngle = paperMarioSideAngle
        playerCameraDistance = paperMarioDistance
        playerCameraHeight = paperMarioHeight

        // Start in orbiting mode
        currentCameraMode = CameraMode.PLAYER
        updateCameraPosition()

        camera.near = 1f
        camera.far = 300f
        camera.update()

        // Setup the 2D UI camera
        uiCamera = OrthographicCamera(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        uiCamera.position.set(uiCamera.viewportWidth / 2f, uiCamera.viewportHeight / 2f, 0f)
        uiCamera.update()
    }

    fun findUiCamera(): OrthographicCamera {
        return uiCamera
    }

    fun resize(width: Int, height: Int) {
        // Update the 3D camera
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()

        // NEW: Also update the 2D UI camera
        uiCamera.viewportWidth = width.toFloat()
        uiCamera.viewportHeight = height.toFloat()
        uiCamera.position.set(uiCamera.viewportWidth / 2f, uiCamera.viewportHeight / 2f, 0f)
        uiCamera.update()
    }

    fun setPlayerPosition(position: Vector3, forceSnap: Boolean = false) {
        playerPosition.set(position)
        calculatePlayerCameraTarget()

        // If a snap is forced, immediately move the camera to the target.
        if (forceSnap) {
            currentPlayerCameraPosition.set(targetPlayerCameraPosition)
        }
    }

    fun resetAndSnapToPlayer(position: Vector3) {
        setPlayerPosition(position, true)
        handlePlayerCameraInput(0f) // Call with 0f delta to immediately update camera matrix
    }

    fun toggleFreeCameraMode() {
        when (currentCameraMode) {
            CameraMode.ORBITING, CameraMode.PLAYER -> {
                // Switch to free camera
                currentCameraMode = CameraMode.FREE
                freeCameraPosition.set(camera.position)

                val currentDirection = Vector3(camera.direction).nor()
                freeCameraYaw = Math.toDegrees(Math.atan2(currentDirection.x.toDouble(), currentDirection.z.toDouble())).toFloat()
                freeCameraPitch = Math.toDegrees(Math.asin(currentDirection.y.toDouble())).toFloat()

                freeCameraTarget.set(freeCameraPosition).add(currentDirection)
                println("Free Camera Mode: ON")
            }
            CameraMode.FREE -> {
                // Switch back to player camera (Paper Mario style)
                currentCameraMode = CameraMode.PLAYER
                updateCameraPosition()
                println("Player Camera Mode: ON (Paper Mario style)")
            }
        }
    }

    fun switchToPlayerCamera() {
        if (currentCameraMode != CameraMode.FREE) {
            currentCameraMode = CameraMode.PLAYER
            // Initialize the current position to avoid sudden jumps
            currentPlayerCameraPosition.set(camera.position)
            calculatePlayerCameraTarget()
            //println("Switched to Player Camera (Paper Mario style)")
        }
    }

    fun switchToOrbitingCamera() {
        if (currentCameraMode != CameraMode.FREE) {
            currentCameraMode = CameraMode.ORBITING
            updateCameraPosition()
            println("Switched to Orbiting Camera (Building mode)")
        }
    }

    fun handleInput(deltaTime: Float) {
        when (currentCameraMode) {
            CameraMode.FREE -> handleFreeCameraInput(deltaTime)
            CameraMode.PLAYER -> handlePlayerCameraInput(deltaTime)
            CameraMode.ORBITING -> {
                // Orbiting camera is handled via mouse drag
            }
        }
    }

    fun handleMouseDrag(deltaX: Float) {
        when (currentCameraMode) {
            CameraMode.ORBITING -> {
                cameraAngleY += deltaX * 0.2f
                updateCameraPosition()
            }
            CameraMode.PLAYER -> {
                // In player mode, mouse drag can adjust the side view angle
                playerCameraAngle += deltaX * 0.1f
                playerCameraAngle = playerCameraAngle.coerceIn(0f, 180f)
                calculatePlayerCameraTarget()
            }
            CameraMode.FREE -> {
                // Free camera mouse drag could be implemented here if needed
            }
        }
    }

    fun handleMouseScroll(amountY: Float) {
        when (currentCameraMode) {
            CameraMode.ORBITING -> {
                cameraDistance += amountY * 2f
                cameraDistance = cameraDistance.coerceIn(5f, 50f)
                updateCameraPosition()
            }
            CameraMode.PLAYER -> {
                // Adjust distance from player
                playerCameraDistance += amountY * 1f
                playerCameraDistance = playerCameraDistance.coerceIn(8f, 25f)
                calculatePlayerCameraTarget()
            }
            CameraMode.FREE -> {
                // Could add zoom functionality for free camera if needed
            }
        }
    }

    private fun updateCameraPosition() {
        when (currentCameraMode) {
            CameraMode.ORBITING -> updateOrbitingCamera()
            CameraMode.PLAYER -> calculatePlayerCameraTarget()
            CameraMode.FREE -> {
                // Free camera position is handled in handleFreeCameraInput
            }
        }
    }

    private fun updateOrbitingCamera() {
        val radians = Math.toRadians(cameraAngleY.toDouble())
        val x = (cos(radians) * cameraDistance).toFloat()
        val z = (sin(radians) * cameraDistance).toFloat()

        camera.position.set(x, 8f, z)
        camera.lookAt(0f, 2f, 0f)
        camera.up.set(0f, 1f, 0f)
        camera.update()
    }

    private fun calculatePlayerCameraTarget() {
        // Calculate the target position for Paper Mario style camera
        val angleRadians = Math.toRadians(playerCameraAngle.toDouble())
        val offsetX = (cos(angleRadians) * playerCameraDistance).toFloat()
        val offsetZ = (sin(angleRadians) * playerCameraDistance).toFloat()

        // Target camera position relative to player
        targetPlayerCameraPosition.set(
            playerPosition.x + offsetX,
            playerPosition.y + playerCameraHeight,
            playerPosition.z + offsetZ
        )

        // Initialize current position if this is the first time
        if (currentPlayerCameraPosition.isZero) {
            currentPlayerCameraPosition.set(targetPlayerCameraPosition)
        }
    }

    private fun handlePlayerCameraInput(deltaTime: Float) {
        // Smoothly move camera towards target position
        currentPlayerCameraPosition.lerp(targetPlayerCameraPosition, playerCameraSmoothing * deltaTime)

        // Set camera position and make it look at the player
        camera.position.set(currentPlayerCameraPosition)

        // Look at the player, but slightly above them for better view
        val lookAtTarget = Vector3(playerPosition.x, playerPosition.y + 1f, playerPosition.z)
        camera.lookAt(lookAtTarget)
        camera.up.set(0f, 1f, 0f)
        camera.update()

        // Optional: Allow some camera adjustments with keyboard
        handlePlayerCameraAdjustments(deltaTime)
    }

    private fun handlePlayerCameraAdjustments(deltaTime: Float) {
        val adjustmentSpeed = 30f * deltaTime

        // Allow fine-tuning of the camera angle with Q and E keys
//        if (Gdx.input.isKeyPressed(Input.Keys.Q)) {
//            playerCameraAngle += adjustmentSpeed
//            playerCameraAngle = playerCameraAngle.coerceIn(0f, 180f)
//            calculatePlayerCameraTarget()
//        }
//        if (Gdx.input.isKeyPressed(Input.Keys.E)) {
//            playerCameraAngle -= adjustmentSpeed
//            playerCameraAngle = playerCameraAngle.coerceIn(0f, 180f)
//            calculatePlayerCameraTarget()
//        }

        // Allow height adjustment with R and T keys
        if (Gdx.input.isKeyPressed(Input.Keys.R)) {
            playerCameraHeight += adjustmentSpeed * 0.5f
            playerCameraHeight = playerCameraHeight.coerceIn(3f, 15f)
            calculatePlayerCameraTarget()
        }
        if (Gdx.input.isKeyPressed(Input.Keys.T)) {
            playerCameraHeight -= adjustmentSpeed * 0.5f
            playerCameraHeight = playerCameraHeight.coerceIn(3f, 15f)
            calculatePlayerCameraTarget()
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
        if (Gdx.input.isKeyPressed(Input.Keys.COMMA)) {
            freeCameraPitch -= rotationAmount
            freeCameraPitch = freeCameraPitch.coerceIn(-89f, 89f)
        }
        if (Gdx.input.isKeyPressed(Input.Keys.PERIOD)) {
            freeCameraPitch += rotationAmount
            freeCameraPitch = freeCameraPitch.coerceIn(-89f, 89f)
        }

        // Calculate forward, right, and up vectors
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

        // Handle movement
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
            // Move forward in the direction the camera is looking
            freeCameraPosition.add(Vector3(forward).scl(moveDistance))
        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            // Move backward
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

        // WASD controls for those who prefer them
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

    // Utility function to get current camera mode as string
    fun getCurrentCameraMode(): String {
        return when (currentCameraMode) {
            CameraMode.ORBITING -> "Orbiting"
            CameraMode.PLAYER -> "Player (Paper Mario)"
            CameraMode.FREE -> "Free Camera"
        }
    }
}
