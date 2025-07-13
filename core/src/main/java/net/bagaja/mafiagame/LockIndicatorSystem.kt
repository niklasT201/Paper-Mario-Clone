package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3

/**
 * Manages a floating 2D lock icon that indicates a car is locked.
 * The icon circles the car to always stay between the player and the car.
 */
class LockIndicatorSystem {
    // Rendering components
    private lateinit var lockTexture: Texture
    private lateinit var lockModel: Model
    private lateinit var lockInstance: ModelInstance
    private lateinit var billboardModelBatch: ModelBatch
    private lateinit var billboardShaderProvider: BillboardShaderProvider

    // State
    private var isVisible = false
    private val position = Vector3()

    // --- Configuration ---
    // How close the player needs to be to see the lock
    private val activationDistance = 15f
    // How far from the car's side the lock floats
    private val horizontalOffset = 2.5f

    fun initialize() {
        // Setup a special shader and batch for billboards that always face the camera
        billboardShaderProvider = BillboardShaderProvider().apply {
            // Make the lock bright and mostly unaffected by world lighting
            setBillboardLightingStrength(1.0f)
            setMinLightLevel(0.8f)
        }
        billboardModelBatch = ModelBatch(billboardShaderProvider)

        // Load the lock texture from your assets
        try {
            // NOTE: The path is relative to your 'assets' folder
            lockTexture = Texture(Gdx.files.internal("gui/lock.png"))
            lockTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        } catch (e: Exception) {
            println("ERROR: Could not load 'gui/lock.png'. Make sure the file exists in your assets folder.")
            // As a fallback to prevent crashing, we use another texture.
            lockTexture = Texture(Gdx.files.internal("textures/player/pig_character.png"))
        }

        // Create the 2D plane (billboard) model for the lock
        val modelBuilder = ModelBuilder()
        val lockSize = 2f // The visual size of the lock icon in the 3D world
        val material = Material(
            TextureAttribute.createDiffuse(lockTexture),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA), // For transparency
            IntAttribute.createCullFace(GL20.GL_NONE) // Don't hide the back
        )

        lockModel = modelBuilder.createRect(
            -lockSize / 2, -lockSize / 2, 0f,
            lockSize / 2, -lockSize / 2, 0f,
            lockSize / 2,  lockSize / 2, 0f,
            -lockSize / 2,  lockSize / 2, 0f,
            0f, 0f, 1f, // The "normal" of the plane
            material,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
        )

        lockInstance = ModelInstance(lockModel)
    }

    /**
     * This is the core logic. It runs every frame to decide if and where to show the lock.
     */
    fun update(playerPos: Vector3, cars: com.badlogic.gdx.utils.Array<GameCar>) {
        // Find the closest car that is locked
        val closestLockedCar = cars.filter { it.isLocked }
            .minByOrNull { it.position.dst2(playerPos) } // Using dst2 is faster

        // Check if a car was found and if it's within our activation range
        if (closestLockedCar != null && closestLockedCar.position.dst(playerPos) < activationDistance) {
            isVisible = true
            val car = closestLockedCar

            // --- Positioning Logic ---
            // 1. Get the horizontal vector from the car to the player.
            val direction = Vector3(playerPos).sub(car.position)
            direction.y = 0f // We only care about the horizontal direction for rotation
            direction.nor()  // Normalize to get a pure direction vector (length of 1)

            // 2. Calculate the lock's final horizontal position.
            val distanceFromCarCenter = (car.carType.width / 2f) + horizontalOffset
            val finalX = car.position.x + (direction.x * distanceFromCarCenter)
            val finalZ = car.position.z + (direction.z * distanceFromCarCenter)

            // 3. Calculate the lock's vertical position (centered on the car).
            val finalY = car.position.y + (car.carType.height / 2f)

            // 4. Set the final position and update the 3D model's transform.
            position.set(finalX, finalY, finalZ)
            lockInstance.transform.setTranslation(position)

        } else {
            // If no suitable car is found, hide the icon.
            isVisible = false
        }
    }

    /**
     * Renders the lock icon if it's visible.
     */
    fun render(camera: Camera, environment: Environment) {
        if (!isVisible) return

        // Pass world lighting information to our special shader
        billboardShaderProvider.setEnvironment(environment)

        billboardModelBatch.begin(camera)
        billboardModelBatch.render(lockInstance, environment)
        billboardModelBatch.end()
    }

    /**
     * Cleans up resources when the game closes.
     */
    fun dispose() {
        lockTexture.dispose()
        lockModel.dispose()
        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()
    }
}
