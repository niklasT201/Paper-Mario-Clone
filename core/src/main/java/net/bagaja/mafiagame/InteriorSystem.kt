package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.JsonReader
import java.util.*

// Interior object types with their properties
enum class InteriorType(
    val displayName: String,
    val texturePath: String,
    val modelPath: String? = null,
    val width: Float,
    val height: Float,
    val depth: Float = 1f,
    val hasCollision: Boolean = true,
    val category: InteriorCategory = InteriorCategory.FURNITURE,
    val groundOffset: Float = 0f,
    val isRandomizer: Boolean = false
) {
    // 2D Billboard objects
    BAR("Bar", "textures/interior/bar.png", null, 2f, 1.5f, 1f, true, InteriorCategory.FURNITURE, 1f),
    BARREL("Barrel", "textures/interior/barrel.png", null, 1f, 1.5f, 1f, true, InteriorCategory.FURNITURE, 1f),
    BOARD("Board", "textures/interior/board.png", null, 2f, 1.5f, 0.1f, false, InteriorCategory.DECORATION, 1f),
    BROKEN_LAMP("Broken Lamp", "textures/interior/broken_lamp.png", null, 0.8f, 2f, 0.8f, false, InteriorCategory.LIGHTING, 1f),
    CHAIR("Chair", "textures/interior/chair.png", null, 1f, 2f, 1f, true, InteriorCategory.FURNITURE, 1f),
    DESK_LAMP("Desk Lamp", "textures/interior/desk_lamp.png", null, 0.6f, 1f, 0.6f, false, InteriorCategory.LIGHTING, 1f),
    HANDLANTERN("Hand Lantern", "textures/interior/handlantern.png", null, 0.5f, 1f, 0.5f, false, InteriorCategory.LIGHTING, 1f),
    ITEM_FRAME("Item Frame", "textures/interior/itemframe.png", null, 1.5f, 1.5f, 0.1f, false, InteriorCategory.DECORATION, 1f),
    MONEY_STACK("Money Stack", "textures/interior/money_stack.png", null, 0.5f, 0.3f, 0.5f, false, InteriorCategory.MISC, 1f),
    OFFICE_CHAIR("Office Chair", "textures/interior/office_chair.png", null, 1f, 2f, 1f, true, InteriorCategory.FURNITURE, 1f),
    TABLE("Table", "textures/interior/table.png", null, 2f, 1.2f, 2f, true, InteriorCategory.FURNITURE, 1f),
    TABLE_DISH("Table with Dish", "textures/interior/table_dish.png", null, 2f, 1.2f, 2f, true, InteriorCategory.FURNITURE, 1f),
    TELEPHONE("Telephone", "textures/interior/telephone.png", null, 0.4f, 0.6f, 0.4f, false, InteriorCategory.MISC, 1f),
    DOOR_INTERIOR("Interior Door", "textures/interior/door.png", null, 2f, 3f, 0.5f, true, InteriorCategory.FURNITURE, 1.5f),

    // 3D Model objects
    BOOKSHELF_3D("Bookshelf", "Models/shelf_model.png", "Models/bookshelf.g3dj", 4f, 6f, 2f, true, InteriorCategory.FURNITURE, -3f),
    TABLE_3D("Table 3D", "Models/table.png", "Models/table.g3dj", 4f, 6f, 2f, true, InteriorCategory.FURNITURE, -3f),
    RESTAURANT_TABLE("Dinner Table 3D", "Models/restaurant_table.png", "Models/restaurant_table.g3dj", 4f, 6f, 2f, true, InteriorCategory.FURNITURE, -3f),

    INTERIOR_RANDOMIZER("Interior Randomizer", "", null, 1f, 1f, 1f, false, InteriorCategory.MISC, isRandomizer = true);

    val is3D: Boolean get() = modelPath != null
    val is2D: Boolean get() = modelPath == null

    // Companion object to hold the list of randomized Interiors
    companion object {
        /** A list of interior types that can be selected by the randomizer. */
        val randomizableTypes: List<InteriorType> by lazy {
            listOf(
                BARREL,
                BOARD,
                BROKEN_LAMP,
                CHAIR,
                DESK_LAMP,
                HANDLANTERN,
                ITEM_FRAME,
                MONEY_STACK,
                OFFICE_CHAIR,
                TABLE,
                TABLE_DISH,
                TELEPHONE
            )
        }
    }
}

enum class InteriorCategory(val displayName: String) {
    FURNITURE("Furniture"),
    DECORATION("Decoration"),
    LIGHTING("Lighting"),
    APPLIANCE("Appliance"),
    MISC("Miscellaneous")
}

// Main interior system class
class InteriorSystem : IFinePositionable {
    private val interiorModels = mutableMapOf<InteriorType, Model>()
    private val interiorTextures = mutableMapOf<InteriorType, Texture>()
    private val modelLoader = G3dModelLoader(JsonReader())
    private val billboardModelBatch: ModelBatch // For rendering 2D billboards
    private val billboardShaderProvider: BillboardShaderProvider

    var currentSelectedInterior = InteriorType.BAR
    var currentSelectedInteriorIndex = 0

    override var finePosMode = false
    override val fineStep = 0.25f

    // Rotation support
    var currentRotation = 0f
        private set
    private val rotationStep = 90f

    init {
        billboardShaderProvider = BillboardShaderProvider()
        billboardShaderProvider.setBillboardLightingStrength(0.8f) // Adjust lighting as needed
        billboardShaderProvider.setMinLightLevel(0.4f)
        billboardModelBatch = ModelBatch(billboardShaderProvider)
    }

    fun initialize() {
        println("Initializing Interior System...")
        val modelBuilder = ModelBuilder() // Create one ModelBuilder to reuse

        for (interiorType in InteriorType.entries) {
            try {
                // Special handling for the randomizer placeholder
                if (interiorType == InteriorType.INTERIOR_RANDOMIZER) {
                    // Create a small, invisible model so it can be instantiated and highlighted.
                    val invisibleMaterial = Material()
                    invisibleMaterial.set(BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.0f))
                    invisibleMaterial.set(ColorAttribute.createDiffuse(0f, 0f, 0f, 0f)) // Fully transparent

                    val model = modelBuilder.createBox(0.5f, 0.5f, 0.5f, invisibleMaterial, (VertexAttributes.Usage.Position).toLong())
                    interiorModels[interiorType] = model
                    println("Created placeholder model for: ${interiorType.displayName}")
                    continue // Skip to the next enum entry, as it has no texture.
                }


                val texture = Texture(Gdx.files.internal(interiorType.texturePath), false)
                texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
                interiorTextures[interiorType] = texture

                // Create a material that will be used by the model
                val material = Material(
                    TextureAttribute.createDiffuse(texture),
                    BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                    IntAttribute.createCullFace(GL20.GL_NONE) // Don't cull back-face
                )

                if (interiorType.is3D) {
                    // Your existing 3D model loading logic
                    val model = modelLoader.loadModel(Gdx.files.internal(interiorType.modelPath!!))
                    for (mat in model.materials) {
                        mat.set(TextureAttribute.createDiffuse(texture))
                    }
                    interiorModels[interiorType] = model
                    println("Loaded 3D interior model: ${interiorType.displayName}")
                } else {
                    // NEW: Create a billboard model for 2D types
                    val model = modelBuilder.createRect(
                        -interiorType.width / 2f, -interiorType.height / 2f, 0f,
                        interiorType.width / 2f, -interiorType.height / 2f, 0f,
                        interiorType.width / 2f,  interiorType.height / 2f, 0f,
                        -interiorType.width / 2f,  interiorType.height / 2f, 0f,
                        0f, 0f, 1f,
                        material,
                        (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
                    )
                    interiorModels[interiorType] = model // Store this new billboard model
                    println("Created 2D billboard model for: ${interiorType.displayName}")
                }
            } catch (e: Exception) {
                println("Failed to load/create interior model for ${interiorType.displayName}: ${e.message}")
                e.printStackTrace()
            }
        }
        println("Interior System initialized with ${interiorModels.size} models.")
    }

    fun nextInterior() {
        currentSelectedInteriorIndex = (currentSelectedInteriorIndex + 1) % InteriorType.entries.size
        currentSelectedInterior = InteriorType.entries.toTypedArray()[currentSelectedInteriorIndex]
        println("Selected interior: ${currentSelectedInterior.displayName}")
    }

    fun previousInterior() {
        currentSelectedInteriorIndex = if (currentSelectedInteriorIndex > 0) {
            currentSelectedInteriorIndex - 1
        } else {
            InteriorType.entries.size - 1
        }
        currentSelectedInterior = InteriorType.entries.toTypedArray()[currentSelectedInteriorIndex]
        println("Selected interior: ${currentSelectedInterior.displayName}")
    }

    fun rotateSelection() {
        currentRotation = (currentRotation + rotationStep) % 360f
        println("Interior rotation: ${currentRotation}°")
    }

    fun createInteriorInstance(interiorType: InteriorType): GameInterior? {
        // Both 2D and 3D types now have a model in `interiorModels`
        val model = interiorModels[interiorType]
        return model?.let {
            GameInterior(interiorType, instance = ModelInstance(it))
        }
    }

    fun renderBillboards(camera: Camera, environment: Environment, interiors: com.badlogic.gdx.utils.Array<GameInterior>) {
        billboardShaderProvider.setEnvironment(environment)
        billboardModelBatch.begin(camera)
        for (interior in interiors) {
            if (interior.interiorType.is2D) {
                // The billboard shader will handle facing the camera
                billboardModelBatch.render(interior.instance, environment)
            }
        }
        billboardModelBatch.end()
    }

    fun getTexture(interiorType: InteriorType): Texture? {
        return interiorTextures[interiorType]
    }

    fun dispose() {
        interiorModels.values.forEach { it.dispose() }
        interiorTextures.values.forEach { it.dispose() }
        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()
    }
}

// Game interior class that handles both 2D and 3D objects
data class GameInterior(
    val interiorType: InteriorType,
    val instance: ModelInstance,
    val position: Vector3 = Vector3(),
    var rotation: Float = 0f,
    val scale: Vector3 = Vector3(2f, 2f, 2f), // Increased default scale
    val id: String = UUID.randomUUID().toString()
) : OcclusionSystem.Occludable {
    // For 3D collision detection (same as GameHouse)
    private val mesh = instance.model?.meshes?.firstOrNull()
    private val vertexFloats: FloatArray?
    private val indexShorts: ShortArray?
    private val vertexSize: Int

    // Helper vectors for collision detection
    private val v1 = Vector3()
    private val v2 = Vector3()
    private val v3 = Vector3()

    // 2D billboard matrix for rendering
    private val worldBoundingBox = BoundingBox()
    private val tempCenter = Vector3()

    override val modelInstance: ModelInstance
        get() = this.instance

    override fun getBoundingBox(out: BoundingBox): BoundingBox {
        // Calculate and return the world-space bounding box
        return instance.calculateBoundingBox(out)
    }

    init {
        // Pre-load mesh data for 3D objects
        if (interiorType.is3D && mesh != null) {
            vertexSize = mesh.vertexAttributes.vertexSize / 4
            vertexFloats = FloatArray(mesh.numVertices * vertexSize)
            indexShorts = ShortArray(mesh.numIndices)
            mesh.getVertices(vertexFloats)
            mesh.getIndices(indexShorts)
        } else {
            vertexFloats = null
            indexShorts = null
            vertexSize = 0
        }

        updateTransform()
    }

    fun getBoundingBoxForDoor(): BoundingBox {
        // Special handling for doors - create a more generous collision box
        instance.transform.getTranslation(tempCenter)

        // Make the collision area larger and more accessible
        val width = interiorType.width * scale.x * 1.2f // 20% larger width
        val height = interiorType.height * scale.y
        val depth = 3.0f // Generous depth for easier interaction

        // Create the bounding box
        worldBoundingBox.set(
            tempCenter.cpy().sub(width / 2f, 0f, depth / 2f), // Start from ground level
            tempCenter.cpy().add(width / 2f, height, depth / 2f)
        )

        return worldBoundingBox
    }

    fun updateTransform() {
        // Apply ground offset for both 2D and 3D objects
        val adjustedPosition = Vector3(position)
        adjustedPosition.y += interiorType.groundOffset

        instance.transform.setToTranslation(adjustedPosition)
        instance.transform.rotate(Vector3.Y, rotation)
        instance.transform.scale(scale.x, scale.y, scale.z)
    }

    fun render(modelBatch: ModelBatch, environment: Environment) {
        // This will be used for 3D objects only now
        if (interiorType.is3D) {
            modelBatch.render(instance, environment)
        }
    }

    // Collision detection for 3D objects (same as GameHouse)
    fun intersectsRay(ray: Ray, outIntersection: Vector3): Boolean {
        if (!interiorType.is3D || !interiorType.hasCollision || vertexFloats == null || indexShorts == null) {
            return false
        }

        for (i in indexShorts.indices step 3) {
            val idx1 = indexShorts[i].toInt()
            val idx2 = indexShorts[i + 1].toInt()
            val idx3 = indexShorts[i + 2].toInt()

            v1.set(vertexFloats[idx1 * vertexSize], vertexFloats[idx1 * vertexSize + 1], vertexFloats[idx1 * vertexSize + 2])
            v2.set(vertexFloats[idx2 * vertexSize], vertexFloats[idx2 * vertexSize + 1], vertexFloats[idx2 * vertexSize + 2])
            v3.set(vertexFloats[idx3 * vertexSize], vertexFloats[idx3 * vertexSize + 1], vertexFloats[idx3 * vertexSize + 2])

            v1.mul(instance.transform)
            v2.mul(instance.transform)
            v3.mul(instance.transform)

            if (Intersector.intersectRayTriangle(ray, v1, v2, v3, outIntersection)) {
                return true
            }
        }
        return false
    }

    fun collidesWithMesh(playerBounds: BoundingBox): Boolean {
        if (!interiorType.is3D || !interiorType.hasCollision || vertexFloats == null || indexShorts == null) {
            return false
        }

        for (i in indexShorts.indices step 3) {
            val idx1 = indexShorts[i].toInt()
            val idx2 = indexShorts[i + 1].toInt()
            val idx3 = indexShorts[i + 2].toInt()

            v1.set(vertexFloats[idx1 * vertexSize], vertexFloats[idx1 * vertexSize + 1], vertexFloats[idx1 * vertexSize + 2])
            v2.set(vertexFloats[idx2 * vertexSize], vertexFloats[idx2 * vertexSize + 1], vertexFloats[idx2 * vertexSize + 2])
            v3.set(vertexFloats[idx3 * vertexSize], vertexFloats[idx3 * vertexSize + 1], vertexFloats[idx3 * vertexSize + 2])

            v1.mul(instance.transform)
            v2.mul(instance.transform)
            v3.mul(instance.transform)

            if (intersectTriangleBounds(v1, v2, v3, playerBounds)) {
                return true
            }
        }
        return false
    }

    // Simple 2D collision for billboard objects
    fun collidesWithPlayer2D(playerPos: Vector3, playerRadius: Float): Boolean {
        if (!interiorType.is2D || !interiorType.hasCollision) {
            return false
        }

        // For doors specifically, use a more generous and rectangular collision area
        if (interiorType == InteriorType.DOOR_INTERIOR) {
            return isPlayerNearDoor2D(playerPos, playerRadius)
        }

        // For other 2D objects, keep the existing circular collision
        val dx = playerPos.x - position.x
        val dz = playerPos.z - position.z
        val distance2D = kotlin.math.sqrt(dx * dx + dz * dz)
        val collisionRadius = (interiorType.width * scale.x) * 0.5f
        return distance2D < (playerRadius + collisionRadius)
    }

    private fun isPlayerNearDoor2D(playerPos: Vector3, playerRadius: Float): Boolean {
        // Create a rectangular interaction area around the door
        val doorHalfWidth = (interiorType.width * scale.x) * 0.6f // Slightly larger than visual
        val doorHalfDepth = (interiorType.depth * scale.z) * 0.8f // More depth for easier interaction

        // Check if player is within the rectangular bounds
        val dx = kotlin.math.abs(playerPos.x - position.x)
        val dz = kotlin.math.abs(playerPos.z - position.z)

        // Use rectangular collision instead of circular
        return dx <= (doorHalfWidth + playerRadius) && dz <= (doorHalfDepth + playerRadius)
    }

    private fun intersectTriangleBounds(v1: Vector3, v2: Vector3, v3: Vector3, bounds: BoundingBox): Boolean {
        val minX = bounds.min.x
        val minY = bounds.min.y
        val minZ = bounds.min.z
        val maxX = bounds.max.x
        val maxY = bounds.max.y
        val maxZ = bounds.max.z

        if ((v1.x < minX && v2.x < minX && v3.x < minX) ||
            (v1.x > maxX && v2.x > maxX && v3.x > maxX) ||
            (v1.y < minY && v2.y < minY && v3.y < minY) ||
            (v1.y > maxY && v2.y > maxY && v3.y > maxY) ||
            (v1.z < minZ && v2.z < minZ && v3.z < minZ) ||
            (v1.z > maxZ && v2.z > maxZ && v3.z > maxZ)) {
            return false
        }

        if (isPointInBounds(v1, bounds) || isPointInBounds(v2, bounds) || isPointInBounds(v3, bounds)) {
            return true
        }

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
}
