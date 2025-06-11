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

class HouseSystem {
    private val houseModels = mutableMapOf<HouseType, Model>()
    private val houseTextures = mutableMapOf<HouseType, Texture>()
    private val modelBuilder = ModelBuilder()

    var currentSelectedHouse = HouseType.HOUSE_1
        private set
    var currentSelectedHouseIndex = 0
        private set

    fun initialize() {
        val modelBuilder = ModelBuilder()

        // Load textures and create models for each house type
        for (houseType in HouseType.values()) {
            try {
                // Load texture
                val texture = Texture(Gdx.files.internal(houseType.texturePath))
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                houseTextures[houseType] = texture

                // Create material with texture and transparency
                val material = Material(
                    TextureAttribute.createDiffuse(texture),
                    BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                    IntAttribute.createCullFace(GL20.GL_NONE) // Disable backface culling
                )

                // Create simple quad model
                val model = createSimpleQuadModel(modelBuilder, material, houseType)
                houseModels[houseType] = model

                println("Loaded house type: ${houseType.displayName}")
            } catch (e: Exception) {
                println("Failed to load house ${houseType.displayName}: ${e.message}")
            }
        }
    }

    private fun createSimpleQuadModel(modelBuilder: ModelBuilder, material: Material, houseType: HouseType): Model {
        // Calculate dimensions based on the house size
        val width = houseType.width
        val height = houseType.height
        val halfWidth = width / 2f

        // Create a model with a single quad
        modelBuilder.begin()

        val part = modelBuilder.part(
            "house",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or
                VertexAttributes.Usage.Normal or
                VertexAttributes.Usage.TextureCoordinates).toLong(),
            material
        )

        // Vertices for the quad (facing forward along Z-axis)
        part.vertex(-halfWidth, 0f, 0f, 0f, 0f, 1f, 0f, 1f) // Bottom left
        part.vertex(halfWidth, 0f, 0f, 0f, 0f, 1f, 1f, 1f)  // Bottom right
        part.vertex(halfWidth, height, 0f, 0f, 0f, 1f, 1f, 0f) // Top right
        part.vertex(-halfWidth, height, 0f, 0f, 0f, 1f, 0f, 0f) // Top left

        // Triangles for the quad
        part.triangle(0, 1, 2) // First triangle
        part.triangle(2, 3, 0) // Second triangle

        return modelBuilder.end()
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
    // Get bounding box for collision detection - thin like the flat image
    fun getBoundingBox(): BoundingBox {
        val bounds = BoundingBox()

        // Use the actual house width from the enum
        val halfWidth = houseType.width / 2f
        val height = houseType.height

        // Make it very thin (like a wall) - adjust this thickness as needed
        val thickness = 0.5f // Very thin collision box
        val halfThickness = thickness / 2f

        bounds.set(
            Vector3(position.x - halfWidth, position.y, position.z - halfThickness),
            Vector3(position.x + halfWidth, position.y + height, position.z + halfThickness)
        )
        return bounds
    }

    // Check if a point is inside the house collision area
    fun containsPoint(point: Vector3): Boolean {
        val bounds = getBoundingBox()
        return bounds.contains(point)
    }

    // Collision detection with player - thin rectangular collision
    fun collidesWith(playerPos: Vector3, playerRadius: Float): Boolean {
        val halfWidth = houseType.width / 2f
        val thickness = 0.5f
        val halfThickness = thickness / 2f

        // Define the thin rectangle bounds
        val minX = position.x - halfWidth
        val maxX = position.x + halfWidth
        val minZ = position.z - halfThickness
        val maxZ = position.z + halfThickness
        val minY = position.y
        val maxY = position.y + houseType.height

        // Check if player (as a circle) intersects with the thin rectangle
        // Clamp player position to the rectangle bounds
        val closestX = kotlin.math.max(minX, kotlin.math.min(playerPos.x, maxX))
        val closestZ = kotlin.math.max(minZ, kotlin.math.min(playerPos.z, maxZ))
        val closestY = kotlin.math.max(minY, kotlin.math.min(playerPos.y, maxY))

        // Calculate distance from player to closest point on rectangle
        val distanceX = playerPos.x - closestX
        val distanceZ = playerPos.z - closestZ
        val distanceY = playerPos.y - closestY

        // Use 2D collision (ignore Y for ground-based movement)
        val distance2D = kotlin.math.sqrt(distanceX * distanceX + distanceZ * distanceZ)

        return distance2D < playerRadius
    }
}

// House type definitions
enum class HouseType(
    val displayName: String,
    val texturePath: String,
    val width: Float,
    val height: Float
) {
    HOUSE_1("Small House", "textures/objects/houses/worker_house.png", 20f, 25f),
    FLAT("Flat", "textures/objects/houses/flat_default.png", 20f, 25f),
    HOUSE_2("Medium House", "textures/objects/houses/house2.png", 25f, 30f),
    HOUSE_3("Large House", "textures/objects/houses/house3.png", 30f, 35f),
    MANSION("Mansion", "textures/objects/houses/mansion.png", 40f, 45f),
    COTTAGE("Cottage", "textures/objects/houses/cottage.png", 15f, 20f),
}
