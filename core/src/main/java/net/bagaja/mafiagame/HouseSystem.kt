package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.JsonReader

class HouseSystem {
    private val houseModels = mutableMapOf<HouseType, Model>()
    private val houseTextures = mutableMapOf<HouseType, Texture>()

    // Support for 3D models only
    private lateinit var modelLoader: G3dModelLoader

    var currentSelectedHouse = HouseType.HOUSE_1
        private set
    var currentSelectedHouseIndex = 0
        private set

    fun initialize() {
        // Initialize the 3D model loader
        modelLoader = G3dModelLoader(JsonReader())

        // Load textures and create models for each house type (3D only)
        for (houseType in HouseType.values()) {
            try {
                // 1. Manually load the texture for the 3D model
                val house3dTexture = Texture(Gdx.files.internal(houseType.texturePath), false)

                // 2. Set the filtering to Nearest
                house3dTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)

                houseTextures[houseType] = house3dTexture

                // 3. Load the 3D model.
                val model = modelLoader.loadModel(Gdx.files.internal(houseType.modelPath))

                for (material in model.materials) {
                    // Create a new TextureAttribute with our crisp texture.
                    val textureAttribute = TextureAttribute.createDiffuse(house3dTexture)
                    material.set(textureAttribute)
                }

                houseModels[houseType] = model
                println("Loaded 3D house model: ${houseType.displayName} with crisp texture.")
            } catch (e: Exception) {
                println("Failed to load house ${houseType.displayName}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun nextHouse() {
        currentSelectedHouseIndex = (currentSelectedHouseIndex + 1) % HouseType.values().size
        currentSelectedHouse = HouseType.values()[currentSelectedHouseIndex]
        println("Selected house: ${currentSelectedHouse.displayName}")
    }

    fun previousHouse() {
        currentSelectedHouseIndex = if (currentSelectedHouseIndex > 0) {
            currentSelectedHouseIndex - 1
        } else {
            HouseType.values().size - 1
        }
        currentSelectedHouse = HouseType.values()[currentSelectedHouseIndex]
        println("Selected house: ${currentSelectedHouse.displayName}")
    }

    fun createHouseInstance(houseType: HouseType): ModelInstance? {
        val model = houseModels[houseType]
        return model?.let { ModelInstance(it) }
    }

    fun dispose() {
        houseModels.values.forEach { it.dispose() }
        houseTextures.values.forEach { it.dispose() }
    }
}

// Game house class to store house data with collision
data class GameHouse(
    val modelInstance: ModelInstance,
    val houseType: HouseType,
    val position: Vector3
) {
    // Get bounding box for collision detection (3D models only)
    fun getBoundingBox(): BoundingBox {
        val bounds = BoundingBox()
        modelInstance.calculateBoundingBox(bounds)
        // Adjust position
        bounds.min.add(position)
        bounds.max.add(position)
        return bounds
    }

    // Check if a point is inside the house collision area
    fun containsPoint(point: Vector3): Boolean {
        val bounds = getBoundingBox()
        return bounds.contains(point)
    }

    // Collision detection with player (3D models only)
    fun collidesWith(playerPos: Vector3, playerRadius: Float): Boolean {
        // For 3D models, use bounding box collision
        val bounds = getBoundingBox()

        // Expand the bounds by player radius for collision
        val expandedBounds = BoundingBox(bounds)
        expandedBounds.min.add(-playerRadius, -playerRadius, -playerRadius)
        expandedBounds.max.add(playerRadius, playerRadius, playerRadius)

        return expandedBounds.contains(playerPos)
    }
}

// House type definitions (3D only)
enum class HouseType(
    val displayName: String,
    val texturePath: String,
    val width: Float,
    val height: Float,
    val modelPath: String
) {
    HOUSE_1("Flat Left", "Models/flat_left.png", 10f, 10f, "Models/flat_left.g3dj"),
    HOUSE_2("Flat Middle", "Models/flat_middle.png", 12f, 12f, "Models/flat_middle.g3dj"),
    HOUSE_3("Flat Right", "Models/flat_right.png", 15f, 15f, "Models/flat_right.g3dj"),
    HOUSE_4("House", "Models/house_model.png", 18f, 18f, "Models/house.g3dj")
}
