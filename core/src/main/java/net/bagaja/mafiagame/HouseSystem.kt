package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.JsonReader
import java.util.*
import kotlin.math.floor

data class GameEntryPoint(
    val id: String = UUID.randomUUID().toString(),
    val houseId: String,
    val position: Vector3,
    val debugInstance: ModelInstance
)

class HouseSystem: IFinePositionable {
    private val houseModels = mutableMapOf<HouseType, Model>()
    private val houseTextures = mutableMapOf<HouseType, Texture>()

    // Model for the entry point's debug visual
    private var entryPointDebugModel: Model? = null

    private lateinit var modelLoader: G3dModelLoader

    var currentSelectedHouse = HouseType.HOUSE_1
        private set
    var currentSelectedHouseIndex = 0
        private set
    var isNextHouseLocked: Boolean = false
        private set
    var currentRotation: Float = 0f
        private set
    private val rotationStep = 90f

    var selectedRoomTemplateId: String? = null

    override var finePosMode = false
    override val fineStep = 0.25f

    lateinit var sceneManager: SceneManager
    private lateinit var raycastSystem: RaycastSystem
    private val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)
    private val tempVec3 = Vector3()
    private var blockSize: Float = 4f

    fun initialize() {
        this.blockSize = 4f // Assuming default size, or pass from MafiaGame
        this.raycastSystem = RaycastSystem(blockSize)

        // Initialize the 3D model loader
        modelLoader = G3dModelLoader(JsonReader())

        // Create the debug model for entry points
        val modelBuilder = ModelBuilder()
        val debugMaterial = Material(
            ColorAttribute.createDiffuse(Color.BLUE),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.4f)
        )
        entryPointDebugModel = modelBuilder.createBox(3f, 5f, 3f, debugMaterial,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong())

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

    fun handlePlaceAction(ray: Ray) {
        if (Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            val gridX = floor(tempVec3.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(tempVec3.z / blockSize) * blockSize + blockSize / 2
            val properY = findHighestSurfaceYAt(gridX, gridZ)

            val existingHouse = sceneManager.activeHouses.find { house ->
                kotlin.math.abs(house.position.x - gridX) < 3f &&
                    kotlin.math.abs(house.position.z - gridZ) < 3f
            }

            if (existingHouse == null) {
                val gameHouse = addHouse(gridX, properY, gridZ, currentSelectedHouse)
                // After placing the house, enter the special placement mode for its entry point
                if (gameHouse != null) {
                    sceneManager.game.uiManager.enterEntryPointPlacementMode(gameHouse)
                }
            } else {
                println("House already exists near this position")
            }
        }
    }

    fun handleEntryPointPlaceAction(ray: Ray, house: GameHouse) {
        if (house.intersectsRay(ray, tempVec3)) {
            val entryPoint = createEntryPoint(tempVec3.cpy(), house.id)
            if (entryPoint != null) {
                // Link the house to this new entry point
                house.entryPointId = entryPoint.id
                sceneManager.activeEntryPoints.add(entryPoint)
                println("Custom entry point ${entryPoint.id} created for house ${house.id}")
                sceneManager.game.uiManager.exitEntryPointPlacementMode() // Exit the special mode
            }
        } else {
            sceneManager.game.uiManager.updatePlacementInfo("Placement failed: You must click directly on the house model.")
        }
    }

    private fun createEntryPoint(position: Vector3, houseId: String): GameEntryPoint? {
        val model = entryPointDebugModel ?: return null
        return GameEntryPoint(
            houseId = houseId,
            position = position,
            debugInstance = ModelInstance(model)
        )
    }

    fun handleRemoveAction(ray: Ray): Boolean {
        val houseToRemove = raycastSystem.getHouseAtRay(ray, sceneManager.activeHouses)
        if (houseToRemove != null) {
            removeHouse(houseToRemove)
            return true
        }
        return false
    }

    private fun addHouse(x: Float, y: Float, z: Float, houseType: HouseType): GameHouse? {
        val houseInstance = createHouseInstance(houseType) ?: return null
        val position = Vector3(x, y, z)

        val canHaveRoom = houseType.canHaveRoom
        val isLocked = if (canHaveRoom) isNextHouseLocked else false
        val roomTemplateId = if (isLocked || !canHaveRoom) null else selectedRoomTemplateId

        val gameHouse = GameHouse(
            modelInstance = houseInstance,
            houseType = houseType,
            position = position,
            isLocked = isLocked,
            assignedRoomTemplateId = roomTemplateId,
            exitDoorId = null,
            rotationY = currentRotation
        )
        gameHouse.updateTransform()

        sceneManager.activeHouses.add(gameHouse)
        sceneManager.game.lastPlacedInstance = gameHouse

        println("Placed ${houseType.displayName}. Locked: ${gameHouse.isLocked}. Room Template ID: ${gameHouse.assignedRoomTemplateId ?: "None"}")
        return gameHouse
    }

    private fun removeHouse(houseToRemove: GameHouse) {
        sceneManager.activeHouses.removeValue(houseToRemove, true)
        println("${houseToRemove.houseType.displayName} removed at: ${houseToRemove.position}")
    }

    // --- NEW: Helper Function Copied from MafiaGame.kt ---
    private fun findHighestSurfaceYAt(x: Float, z: Float): Float {
        val blocksInColumn = sceneManager.activeChunkManager.getBlocksInColumn(x, z)
        var highestY = 0f // Default to ground level

        for (gameBlock in blocksInColumn) {
            if (!gameBlock.blockType.hasCollision) continue
            val blockBounds = gameBlock.getBoundingBox(blockSize, BoundingBox())
            if (blockBounds.max.y > highestY) {
                highestY = blockBounds.max.y
            }
        }
        return highestY
    }

    fun rotateSelection() {
        currentRotation = (currentRotation + rotationStep) % 360f
        println("House rotation set to: $currentRotation")
    }

    fun nextHouse() {
        currentSelectedHouseIndex = (currentSelectedHouseIndex + 1) % HouseType.entries.size
        currentSelectedHouse = HouseType.entries.toTypedArray()[currentSelectedHouseIndex]
        if (!currentSelectedHouse.canHaveRoom) {
            isNextHouseLocked = false // Reset lock state if stairs are selected
        }
        println("Selected house: ${currentSelectedHouse.displayName}")
    }

    fun previousHouse() {
        currentSelectedHouseIndex = if (currentSelectedHouseIndex > 0) {
            currentSelectedHouseIndex - 1
        } else {
            HouseType.entries.size - 1
        }
        currentSelectedHouse = HouseType.entries.toTypedArray()[currentSelectedHouseIndex]
        if (!currentSelectedHouse.canHaveRoom) {
            isNextHouseLocked = false // Reset lock state if stairs are selected
        }
        println("Selected house: ${currentSelectedHouse.displayName}")
    }

    fun toggleLockState() {
        if (currentSelectedHouse.canHaveRoom) {
            isNextHouseLocked = !isNextHouseLocked
            println("Next house will be placed as: ${if (isNextHouseLocked) "Locked" else "Open"}")
        } else {
            isNextHouseLocked = false // Ensure stairs are never locked
            println("Cannot lock/unlock a ${currentSelectedHouse.displayName}.")
        }
    }

    private fun createHouseInstance(houseType: HouseType): ModelInstance? {
        val model = houseModels[houseType]
        return model?.let { ModelInstance(it) }
    }

    fun renderEntryPoints(modelBatch: ModelBatch, environment: Environment, objectSystem: ObjectSystem) {
        if (!objectSystem.debugMode) return

        for (entryPoint in sceneManager.activeEntryPoints) {
            entryPoint.debugInstance.transform.setToTranslation(entryPoint.position)
            modelBatch.render(entryPoint.debugInstance, environment)
        }
    }

    fun dispose() {
        houseModels.values.forEach { it.dispose() }
        houseTextures.values.forEach { it.dispose() }
        entryPointDebugModel?.dispose() // NEW
    }
}

// Game house class to store house data with collision
data class GameHouse(
    val modelInstance: ModelInstance,
    val houseType: HouseType,
    val position: Vector3,
    val isLocked: Boolean,
    val assignedRoomTemplateId: String? = null,
    var exitDoorId: String? = null,
    var rotationY: Float = 0f,
    val id: String = UUID.randomUUID().toString(),
    var entryPointId: String? = null
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

    fun intersectsRay(ray: Ray, outIntersection: Vector3): Boolean {
        // Loop through all the triangles in the mesh
        for (i in indexShorts.indices step 3) {
            // Get the indices of the three vertices that form this triangle
            val idx1 = indexShorts[i].toInt()
            val idx2 = indexShorts[i + 1].toInt()
            val idx3 = indexShorts[i + 2].toInt()

            // Extract the local (x, y, z) position for each vertex
            v1.set(vertexFloats[idx1 * vertexSize], vertexFloats[idx1 * vertexSize + 1], vertexFloats[idx1 * vertexSize + 2])
            v2.set(vertexFloats[idx2 * vertexSize], vertexFloats[idx2 * vertexSize + 1], vertexFloats[idx2 * vertexSize + 2])
            v3.set(vertexFloats[idx3 * vertexSize], vertexFloats[idx3 * vertexSize + 1], vertexFloats[idx3 * vertexSize + 2])

            // Transform the vertices from local model space to world space
            v1.mul(modelInstance.transform)
            v2.mul(modelInstance.transform)
            v3.mul(modelInstance.transform)

            // Now, check if the ray intersects with this single, world-space triangle
            if (Intersector.intersectRayTriangle(ray, v1, v2, v3, outIntersection)) {
                return true // Collision found! No need to check other triangles.
            }
        }
        return false // No triangles collided with the ray.
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
        val ray = Ray(start, direction)
        val intersection = Vector3()

        if (Intersector.intersectRayBounds(ray, bounds, intersection)) {
            return start.dst(intersection) <= length
        }

        return false
    }

    fun updateTransform() {
        modelInstance.transform.setToTranslationAndScaling(position, Vector3(6f, 6f, 6f))
        modelInstance.transform.rotate(Vector3.Y, rotationY)
    }
}

// House type definitions (3D only)
enum class HouseType(
    val displayName: String,
    val texturePath: String,
    val width: Float,
    val height: Float,
    val modelPath: String,
    val canHaveRoom: Boolean,
    val doorOffset: Vector3
) {
    HOUSE_1("Flat Left", "Models/flat_left.png", 10f, 10f, "Models/flat_left.g3dj", true, Vector3(0f, 4.5f, 7f)),
    HOUSE_2("Flat Middle", "Models/flat_middle.png", 12f, 12f, "Models/flat_middle.g3dj", true, Vector3(0f, 4.5f, 7f)),
    HOUSE_3("Flat Right", "Models/flat_right.png", 15f, 15f, "Models/flat_right.g3dj", true, Vector3(0f, 4.5f, 7f)),
    HOUSE_4("House", "Models/house_model.png", 18f, 18f, "Models/house.g3dj", true,  Vector3(0f, 4.5f, 7f)),
    STAIR("Stair", "Models/stair_flat.png", 18f, 18f, "Models/stair.g3dj", false, Vector3.Zero)
}
