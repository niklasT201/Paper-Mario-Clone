package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox

class CarSystem: IFinePositionable {
    private val carModels = mutableMapOf<CarType, Model>()
    private val carTextures = mutableMapOf<CarType, Texture>()

    var currentSelectedCar = CarType.SEDAN
        private set
    var currentSelectedCarIndex = 0
        private set
    var isNextCarLocked = false
        private set

    // Fine positioning mode
    override var finePosMode = false
    override val fineStep = 0.25f

    fun initialize() {
        val modelBuilder = ModelBuilder()

        // Load textures and create models for each car type
        for (carType in CarType.entries) {
            try {
                // Load texture for cars
                val texture = Texture(Gdx.files.internal(carType.texturePath))
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                carTextures[carType] = texture

                // Create material with texture and transparency
                val material = Material(
                    TextureAttribute.createDiffuse(texture),
                    BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                    IntAttribute.createCullFace(GL20.GL_NONE) // Disable backface culling
                )

                // Create 2D billboard model (single quad that stands upright)
                val model = createCarBillboard(modelBuilder, material, carType)
                carModels[carType] = model

                println("Loaded car type: ${carType.displayName}")
            } catch (e: Exception) {
                println("Failed to load car ${carType.displayName}: ${e.message}")
            }
        }
    }

    private fun createCarBillboard(modelBuilder: ModelBuilder, material: Material, carType: CarType): Model {
        // Calculate dimensions based on the car size
        val width = carType.width
        val height = carType.height
        val halfWidth = width / 2f

        // Create a model with a single upright quad (billboard)
        modelBuilder.begin()

        val part = modelBuilder.part(
            "car_billboard",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or
                VertexAttributes.Usage.Normal or
                VertexAttributes.Usage.TextureCoordinates).toLong(),
            material
        )

        // Vertices for upright quad facing the camera (Z-axis oriented)
        part.vertex(-halfWidth, 0f, 0f, 0f, 0f, 1f, 0f, 1f) // Bottom left
        part.vertex(halfWidth, 0f, 0f, 0f, 0f, 1f, 1f, 1f)  // Bottom right
        part.vertex(halfWidth, height, 0f, 0f, 0f, 1f, 1f, 0f) // Top right
        part.vertex(-halfWidth, height, 0f, 0f, 0f, 1f, 0f, 0f) // Top left

        // Triangles for the quad
        part.triangle(0, 1, 2) // First triangle
        part.triangle(2, 3, 0) // Second triangle

        return modelBuilder.end()
    }

    fun toggleLockState() {
        isNextCarLocked = !isNextCarLocked
        println("Next car lock state toggled to: $isNextCarLocked")
    }

    fun nextCar() {
        currentSelectedCarIndex = (currentSelectedCarIndex + 1) % CarType.entries.size
        currentSelectedCar = CarType.entries.toTypedArray()[currentSelectedCarIndex]
        println("Selected car: ${currentSelectedCar.displayName}")
    }

    fun previousCar() {
        currentSelectedCarIndex = if (currentSelectedCarIndex > 0) {
            currentSelectedCarIndex - 1
        } else {
            CarType.entries.size - 1
        }
        currentSelectedCar = CarType.entries.toTypedArray()[currentSelectedCarIndex]
        println("Selected car: ${currentSelectedCar.displayName}")
    }

    fun createCarInstance(carType: CarType): ModelInstance? {
        val model = carModels[carType]
        return model?.let { ModelInstance(it) }
    }

    fun dispose() {
        carModels.values.forEach { it.dispose() }
        carTextures.values.forEach { it.dispose() }
    }
}

// Game car class to store car data
data class GameCar(
    val modelInstance: ModelInstance,
    val carType: CarType,
    val position: Vector3,
    var direction: Float = 0f, // Direction in degrees (0 = facing forward/north)
    val isLocked: Boolean = false
) {
    val id: String = java.util.UUID.randomUUID().toString()

    // Get bounding box for collision detection - FIXED VERSION
    fun getBoundingBox(): BoundingBox {
        val bounds = BoundingBox()
        val halfWidth = carType.width / 2f
        val halfHeight = carType.height / 2f

        // Use a more reasonable thickness based on car type
        val thickness = carType.width * 0.3f // Make thickness proportional to car width
        val halfThickness = thickness / 2f

        // Create collision box based on car's actual orientation
        when (direction.toInt()) {
            0, 180 -> {
                // Car is facing north/south: wide on X, thin on Z
                bounds.set(
                    Vector3(position.x - halfWidth, position.y, position.z - halfThickness),
                    Vector3(position.x + halfWidth, position.y + halfHeight, position.z + halfThickness)
                )
            }
            90, 270 -> {
                // Car is facing east/west: thin on X, wide on Z
                bounds.set(
                    Vector3(position.x - halfThickness, position.y, position.z - halfWidth),
                    Vector3(position.x + halfThickness, position.y + halfHeight, position.z + halfWidth)
                )
            }
            else -> {
                // For other angles, use a square collision box (simpler but less precise)
                val maxDimension = kotlin.math.max(halfWidth, halfThickness)
                bounds.set(
                    Vector3(position.x - maxDimension, position.y, position.z - maxDimension),
                    Vector3(position.x + maxDimension, position.y + halfHeight, position.z + maxDimension)
                )
            }
        }
        return bounds
    }

    // Update the car's position with fixed direction (no more billboard effect)
    fun updateTransform() {
        // Update position
        modelInstance.transform.setToTranslation(position)

        // Apply fixed rotation (cars maintain their direction)
        modelInstance.transform.rotate(Vector3.Y, direction)
    }

    // Method to move car in its current direction (for future driving)
    fun moveForward(distance: Float) {
        val radians = Math.toRadians(direction.toDouble()).toFloat()
        position.x += kotlin.math.sin(radians) * distance
        position.z += kotlin.math.cos(radians) * distance
    }

    // Method to move car sideways (strafe)
    fun moveSideways(distance: Float) {
        val radians = Math.toRadians((direction + 90f).toDouble()).toFloat()
        position.x += kotlin.math.sin(radians) * distance
        position.z += kotlin.math.cos(radians) * distance
    }
}

// Car type definitions
enum class CarType(
    val displayName: String,
    val texturePath: String,
    val width: Float,
    val height: Float
) {
    SEDAN("Sedan", "textures/objects/cars/car_driving.png", 10f, 7f),
    SUV("SUV", "textures/cars/suv.png", 8f, 5f),
    TRUCK("Truck", "textures/cars/truck.png", 11f, 6f), // was 12f
    SPORTS_CAR("Sports Car", "textures/cars/sports_car.png", 6f, 3.5f),
    VAN("Van", "textures/cars/van.png", 7f, 5.5f),
    POLICE_CAR("Police Car", "textures/cars/police_car.png", 7f, 4f),
    TAXI("Taxi", "textures/cars/taxi.png", 7f, 4f),
    MOTORCYCLE("Motorcycle", "textures/cars/motorcycle.png", 2.5f, 4f)
}
