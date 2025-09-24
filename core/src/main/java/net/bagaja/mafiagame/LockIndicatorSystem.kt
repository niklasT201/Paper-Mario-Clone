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
import com.badlogic.gdx.utils.Array

/**
 * Manages floating 2D lock icons for both cars and houses.
 * - Car locks circle the car to always stay between the player and the car.
 * - House locks are static in front of the door.
 */
class LockIndicatorSystem {

    // --- Data class to manage each individual lock icon ---
    private data class LockIcon(
        val instance: ModelInstance,
        var isVisible: Boolean = false
    )

    // Rendering components
    private lateinit var lockTexture: Texture
    private lateinit var lockModel: Model
    private lateinit var billboardModelBatch: ModelBatch
    private lateinit var billboardShaderProvider: BillboardShaderProvider

    // A list to hold all lock icons we might need to render
    private val lockIcons = Array<LockIcon>()
    private var carLockIcon: LockIcon? = null

    // Configuration
    private val activationDistance = 25f // Increased range to see house locks from further
    private val carHorizontalOffset = 2.5f

    fun initialize() {
        billboardShaderProvider = BillboardShaderProvider().apply {
            setBillboardLightingStrength(1.0f)
            setMinLightLevel(0.8f)
        }
        billboardModelBatch = ModelBatch(billboardShaderProvider)

        // Load the lock texture from your assets
        try {
            lockTexture = Texture(Gdx.files.internal("gui/lock.png"))
            lockTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        } catch (e: Exception) {
            println("ERROR: Could not load 'gui/lock.png'. Make sure the file exists in your assets folder.")
            lockTexture = Texture(Gdx.files.internal("textures/player/pig_character.png"))
        }

        val modelBuilder = ModelBuilder()
        val lockSize = 2f
        val material = Material(
            TextureAttribute.createDiffuse(lockTexture),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
            IntAttribute.createCullFace(GL20.GL_NONE)
        )

        lockModel = modelBuilder.createRect(
            -lockSize / 2, -lockSize / 2, 0f,
            lockSize / 2, -lockSize / 2, 0f,
            lockSize / 2,  lockSize / 2, 0f,
            -lockSize / 2,  lockSize / 2, 0f,
            0f, 0f, 1f,
            material,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
        )
    }

    /**
     * Creates a new lock icon instance. This is a helper function.
     */
    private fun createLockIcon(): LockIcon {
        val instance = ModelInstance(lockModel)
        instance.userData = "player" // For the billboard shader
        return LockIcon(instance)
    }

    /**
     * The main update loop. It now checks both cars and houses.
     */
    fun update(playerPos: Vector3, isPlayerDriving: Boolean, cars: Array<GameCar>, houses: Array<GameHouse>) {
        // --- Handle Car Lock ---
        if (isPlayerDriving) {
            // If the player is driving, hide BOTH car and house locks.
            carLockIcon?.isVisible = false
            for (icon in lockIcons) {
                icon.isVisible = false
            }
            // Exit early since there's nothing else to do.
            return
        }

        updateCarLock(playerPos, cars)

        // Handle House Locks
        updateHouseLocks(playerPos, houses)
    }

    private fun updateCarLock(playerPos: Vector3, cars: Array<GameCar>) {
        // Ensure the single car lock icon exists
        if (carLockIcon == null) {
            carLockIcon = createLockIcon()
        }

        val closestLockedCar = cars.filter { it.isLocked && it.state == CarState.DRIVABLE }
            .minByOrNull { it.position.dst2(playerPos) }

        // Check if a car was found and if it's within our activation range
        if (closestLockedCar != null && closestLockedCar.position.dst(playerPos) < activationDistance) {
            val car = closestLockedCar
            val carLockPos = Vector3()
            val direction = Vector3(playerPos).sub(car.position).apply { y = 0f }.nor()
            val distanceFromCarCenter = (car.carType.width / 2f) + carHorizontalOffset
            carLockPos.set(car.position)
                .add(direction.scl(distanceFromCarCenter))
                .add(0f, car.carType.height / 2f, 0f)

            // Visibility Logic
            val playerDistSq = car.position.dst2(playerPos)
            val lockDistSq = car.position.dst2(carLockPos)
            carLockIcon!!.isVisible = lockDistSq < playerDistSq

            if (carLockIcon!!.isVisible) {
                carLockIcon!!.instance.transform.setTranslation(carLockPos)
            }

        } else {
            carLockIcon!!.isVisible = false
        }
    }

    private fun updateHouseLocks(playerPos: Vector3, houses: com.badlogic.gdx.utils.Array<GameHouse>) {
        val lockedHouses = houses.filter { it.isLocked }

        // Ensure we have enough lock icons for all locked houses
        while (lockIcons.size < lockedHouses.size) {
            lockIcons.add(createLockIcon())
        }

        // Hide all house locks by default
        for (icon in lockIcons) {
            icon.isVisible = false
        }

        // Now, iterate through the locked houses and activate their corresponding icons
        for (i in lockedHouses.indices) {
            val house = lockedHouses[i]
            val icon = lockIcons[i]

            // Check if player is close enough to this specific house
            if (house.position.dst(playerPos) < activationDistance) {
                icon.isVisible = true

                // Calculate the static door position
                val doorPosition = Vector3(house.position).add(house.houseType.doorOffset)
                icon.instance.transform.setTranslation(doorPosition)
            }
        }
    }

    fun render(camera: Camera, environment: Environment) {
        // Pass world lighting information to our special shader
        billboardShaderProvider.setEnvironment(environment)
        billboardModelBatch.begin(camera)

        // Render the car lock if it's visible
        carLockIcon?.let {
            if (it.isVisible) {
                billboardModelBatch.render(it.instance, environment)
            }
        }

        // Render all visible house locks
        for (icon in lockIcons) {
            if (icon.isVisible) {
                billboardModelBatch.render(icon.instance, environment)
            }
        }

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
