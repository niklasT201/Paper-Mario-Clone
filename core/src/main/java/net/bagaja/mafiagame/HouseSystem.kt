package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.utils.JsonReader

class HouseSystem: IFinePositionable {
    private val houseModels = mutableMapOf<HouseType, Model>()
    private val houseTextures = mutableMapOf<HouseType, Texture>()

    // Support for 3D models only
    private lateinit var modelLoader: G3dModelLoader

    var currentSelectedHouse = HouseType.HOUSE_1
        private set
    var currentSelectedHouseIndex = 0
        private set

    override var finePosMode = false
    override val fineStep = 0.25f

    fun initialize() {
        // Initialize the 3D model loader
        modelLoader = G3dModelLoader(JsonReader())

        // Load textures and create models for each house type (3D only)
        for (houseType in HouseType.entries) {
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
        currentSelectedHouseIndex = (currentSelectedHouseIndex + 1) % HouseType.entries.size
        currentSelectedHouse = HouseType.entries.toTypedArray()[currentSelectedHouseIndex]
        println("Selected house: ${currentSelectedHouse.displayName}")
    }

    fun previousHouse() {
        currentSelectedHouseIndex = if (currentSelectedHouseIndex > 0) {
            currentSelectedHouseIndex - 1
        } else {
            HouseType.entries.size - 1
        }
        currentSelectedHouse = HouseType.entries.toTypedArray()[currentSelectedHouseIndex]
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
    // Data for Mesh Collision
    private val mesh = modelInstance.model.meshes.first() // Get the first mesh from the model
    private val vertexFloats: FloatArray
    private val indexShorts: ShortArray
    private val vertexSize: Int = mesh.vertexAttributes.vertexSize / 4 // Number of floats per vertex

    // Helper vectors to avoid creating new objects every frame
    private val v1 = Vector3()
    private val v2 = Vector3()
    private val v3 = Vector3()

    init {
        // Pre-load the vertex and index data when the GameHouse is created
        vertexFloats = FloatArray(mesh.numVertices * vertexSize)
        indexShorts = ShortArray(mesh.numIndices)
        mesh.getVertices(vertexFloats)
        mesh.getIndices(indexShorts)
    }

    // Proper BoundingBox reference
    fun getBoundingBox(): BoundingBox {
        val bounds = BoundingBox()
        // The modelInstance's transform already includes the position
        modelInstance.calculateBoundingBox(bounds)
        return bounds
    }

    fun collidesWithMesh(playerBounds: BoundingBox): Boolean {
        // Loop through all the triangles in the mesh
        for (i in indexShorts.indices step 3) {
            // Get the indices of the three vertices that form this triangle
            val idx1 = indexShorts[i].toInt()
            val idx2 = indexShorts[i + 1].toInt()
            val idx3 = indexShorts[i + 2].toInt()

            // Extract the (x, y, z) position for each vertex from the flat vertexFloats array
            v1.set(vertexFloats[idx1 * vertexSize], vertexFloats[idx1 * vertexSize + 1], vertexFloats[idx1 * vertexSize + 2])
            v2.set(vertexFloats[idx2 * vertexSize], vertexFloats[idx2 * vertexSize + 1], vertexFloats[idx2 * vertexSize + 2])
            v3.set(vertexFloats[idx3 * vertexSize], vertexFloats[idx3 * vertexSize + 1], vertexFloats[idx3 * vertexSize + 2])

            v1.mul(modelInstance.transform)
            v2.mul(modelInstance.transform)
            v3.mul(modelInstance.transform)

            // Now, check if the player's box intersects with this single, world-space triangle
            if (intersectTriangleBounds(v1, v2, v3, playerBounds)) {
                return true // Collision found! No need to check other triangles.
            }
        }

        return false // No triangles collided with the player.
    }

    private fun intersectTriangleBounds(v1: Vector3, v2: Vector3, v3: Vector3, bounds: BoundingBox): Boolean {
        // First, quick rejection test: if all triangle vertices are outside the same face of the box
        val minX = bounds.min.x
        val minY = bounds.min.y
        val minZ = bounds.min.z
        val maxX = bounds.max.x
        val maxY = bounds.max.y
        val maxZ = bounds.max.z

        // Check if all vertices are on the same side of any face
        if ((v1.x < minX && v2.x < minX && v3.x < minX) ||
            (v1.x > maxX && v2.x > maxX && v3.x > maxX) ||
            (v1.y < minY && v2.y < minY && v3.y < minY) ||
            (v1.y > maxY && v2.y > maxY && v3.y > maxY) ||
            (v1.z < minZ && v2.z < minZ && v3.z < minZ) ||
            (v1.z > maxZ && v2.z > maxZ && v3.z > maxZ)) {
            return false
        }

        // Check if any vertex is inside the bounding box
        if (isPointInBounds(v1, bounds) || isPointInBounds(v2, bounds) || isPointInBounds(v3, bounds)) {
            return true
        }

        // Check if any edge of the triangle intersects the bounding box
        if (lineIntersectsBounds(v1, v2, bounds) ||
            lineIntersectsBounds(v2, v3, bounds) ||
            lineIntersectsBounds(v3, v1, bounds)) {
            return true
        }

        return false
    }

    private fun isPointInBounds(point: Vector3, bounds: BoundingBox): Boolean {
        return point.x >= bounds.min.x && point.x <= bounds.max.x &&
            point.y >= bounds.min.y && point.y <= bounds.max.y &&
            point.z >= bounds.min.z && point.z <= bounds.max.z
    }

    private fun lineIntersectsBounds(start: Vector3, end: Vector3, bounds: BoundingBox): Boolean {
        // Use LibGDX's built-in ray-box intersection
        val direction = Vector3(end).sub(start)
        val length = direction.len()
        direction.nor()

        val ray = com.badlogic.gdx.math.collision.Ray(start, direction)
        val intersection = Vector3()

        if (Intersector.intersectRayBounds(ray, bounds, intersection)) {
            // Check if intersection point is within the line segment
            val distanceToIntersection = start.dst(intersection)
            return distanceToIntersection <= length
        }

        return false
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
