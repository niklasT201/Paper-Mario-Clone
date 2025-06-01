package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import kotlin.math.cos
import kotlin.math.sin

class CameraManager {
    lateinit var camera: PerspectiveCamera
        private set

    // Free camera mode
    var isFreeCameraMode = false
        private set
    private var freeCameraPosition = Vector3(0f, 8f, 20f)
    private var freeCameraTarget = Vector3(0f, 2f, 0f)
    private val freeCameraSpeed = 15f
    private var freeCameraYaw = 0f // Horizontal rotation (left/right)
    private var freeCameraPitch = 0f // Vertical rotation (up/down)
    private val cameraRotationSpeed = 90f // Degrees per second for rotation

    // Normal camera mode (orbiting)
    private var cameraDistance = 20f // Distance from the action
    private var cameraAngleY = 0f // Horizontal rotation around the scene

    fun initialize() {
        // Setup camera for Paper Mario style (side view)
        camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

        // Position camera for side view - looking from the side at an angle
        cameraDistance = 20f
        cameraAngleY = 90f // Start looking from the side (90 degrees)
        updateCameraPosition()

        camera.near = 1f
        camera.far = 300f
        camera.update()
    }

    fun toggleFreeCameraMode() {
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
    }

    fun handleInput(deltaTime: Float) {
        if (isFreeCameraMode) {
            handleFreeCameraInput(deltaTime)
        }
        // Normal camera input is handled via mouse drag in the input processor
    }

    fun handleMouseDrag(deltaX: Float) {
        if (!isFreeCameraMode) {
            // Update camera angle based on horizontal mouse movement
            cameraAngleY += deltaX * 0.2f // Sensitivity factor
            updateCameraPosition()
        }
    }

    fun handleMouseScroll(amountY: Float) {
        if (!isFreeCameraMode) {
            // Zoom in/out with mouse wheel
            cameraDistance += amountY * 2f
            cameraDistance = cameraDistance.coerceIn(5f, 50f) // Limit zoom range
            updateCameraPosition()
        }
    }

    fun resize(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()
    }

    private fun updateCameraPosition() {
        if (!isFreeCameraMode) {
            // Original camera positioning code (orbiting around player/scene)
            val radians = Math.toRadians(cameraAngleY.toDouble())
            val x = (cos(radians) * cameraDistance).toFloat()
            val z = (sin(radians) * cameraDistance).toFloat()

            camera.position.set(x, 8f, z)
            camera.lookAt(0f, 2f, 0f)
            camera.up.set(0f, 1f, 0f)
            camera.update()
        }
        // If in free camera mode, the camera position is handled by handleFreeCameraInput()
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
}
