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
import com.badlogic.gdx.utils.Array
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
    val isRandomizer: Boolean = false,
    val defaultScale: Vector3 = Vector3(2f, 2f, 2f),
    val isFloorObject: Boolean = false
) {
    // 2D Billboard objects
    BAR("Bar", "textures/interior/bar.png", null, 2f, 1.5f, 0.5f, true, InteriorCategory.FURNITURE, 1f),
    BARREL("Barrel", "textures/interior/barrel.png", null, 1f, 1.5f, 0.5f, true, InteriorCategory.FURNITURE, 1f),
    BOARD("Board", "textures/interior/board.png", null, 2f, 1.5f, 0.1f, false, InteriorCategory.DECORATION, 1f),
    BROKEN_LAMP("Broken Lamp", "textures/interior/broken_lamp.png", null, 0.8f, 2f, 0.8f, false, InteriorCategory.LIGHTING, 1f),
    CHAIR("Chair", "textures/interior/chair.png", null, 1f, 2f, 0.5f, true, InteriorCategory.FURNITURE, 1f),
    DESK_LAMP("Desk Lamp", "textures/interior/desk_lamp.png", null, 0.6f, 1f, 0.4f, false, InteriorCategory.LIGHTING, 1f),
    HANDLANTERN("Hand Lantern", "textures/interior/handlantern.png", null, 0.5f, 1f, 0.4f, false, InteriorCategory.LIGHTING, 1f),
    ITEM_FRAME("Item Frame", "textures/interior/itemframe.png", null, 1.5f, 1.5f, 0.1f, false, InteriorCategory.DECORATION, 1f),
    MONEY_STACK("Money Stack", "textures/interior/money_stack.png", null, 0.5f, 0.3f, 0.3f, false, InteriorCategory.MISC, 1f),
    OFFICE_CHAIR("Office Chair", "textures/interior/office_chair.png", null, 1f, 2f, 0.5f, true, InteriorCategory.FURNITURE, 1f),
    TABLE("Table", "textures/interior/table.png", null, 2f, 2f, 0.5f, true, InteriorCategory.FURNITURE, 1f),
    TABLE_DISH("Table with Dish", "textures/interior/table_dish.png", null, 2f, 1.2f, 0.5f, true, InteriorCategory.FURNITURE, 1f),
    TELEPHONE("Telephone", "textures/interior/telephone.png", null, 0.4f, 0.6f, 0.3f, false, InteriorCategory.MISC, 1f),
    DOOR_INTERIOR("Interior Door", "textures/interior/door.png", null, 2f, 3f, 0.5f, true, InteriorCategory.FURNITURE, 1.5f),
    CARPET(
        "Carpet",
        "textures/interior/carpet.png", // The relative path to your new image
        null,
        width = 4f,
        height = 0.1f,
        depth = 6f,
        hasCollision = false,
        category = InteriorCategory.DECORATION,
        isFloorObject = true
    ),
    MICROPHONE(
        "Microphone",
        "textures/interior/microphone.png",
        null,
        2f, 5f, 0.5f,
        true,
        InteriorCategory.FURNITURE,
        0f,
        false,
        Vector3(0.8f, 0.8f, 0.8f)
    ),

    // 3D Model objects
    BOOKSHELF_3D("Bookshelf", "Models/shelf_model.png", "Models/bookshelf.g3dj", 4f, 6f, 2f, true, InteriorCategory.FURNITURE, -3f),
    TABLE_3D("Table 3D", "Models/table.png", "Models/table.g3dj", 4f, 6f, 2f, true, InteriorCategory.FURNITURE, -3f),
    RESTAURANT_TABLE("Dinner Table 3D", "Models/restaurant_table.png", "Models/restaurant_table.g3dj", 4f, 6f, 2f, true, InteriorCategory.FURNITURE, -3f),
    PLAYER_SPAWNPOINT(
        "Player Spawnpoint",
        "textures/player/pig_character.png",
        null,
        2f, 2f, 1f,
        hasCollision = false,
        category = InteriorCategory.MISC,
        groundOffset = 0.1f
    ),

    // Randomizers
    INTERIOR_RANDOMIZER("Small Item Randomizer", "", null, 1f, 1f, 1f, false, InteriorCategory.MISC, isRandomizer = true),
    FURNITURE_RANDOMIZER_3D("3D Furniture Randomizer", "", null, 1f, 1f, 1f, false, InteriorCategory.FURNITURE, isRandomizer = true);

    val is3D: Boolean get() = modelPath != null
    val is2D: Boolean get() = modelPath == null

    // Companion object to hold the list of randomized Interiors
    companion object {
        /** A list of SMALL interior types that can be selected by the small item randomizer. */
        val randomizableSmallItems: List<InteriorType> by lazy {
            listOf(
                BARREL,
                BOARD,
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

        /** A list of 3D FURNITURE types that can be selected by the furniture randomizer. */
        val randomizableFurniture3D: List<InteriorType> by lazy {
            listOf(
                BOOKSHELF_3D,
                TABLE_3D,
                RESTAURANT_TABLE
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
    val billboardModelBatch: ModelBatch // For rendering 2D billboards
    private val renderableBillboards = Array<ModelInstance>()
    private val billboardShaderProvider: BillboardShaderProvider = BillboardShaderProvider()
    private var previewInstance: GameInterior? = null

    fun isPreviewActive(): Boolean {
        return previewInstance != null
    }

    var currentSelectedInterior = InteriorType.BAR
    var currentSelectedInteriorIndex = 0

    override var finePosMode = false
    override val fineStep = 0.25f

    // Rotation support
    var currentRotation = 0f
        private set
    private val rotationStep = 90f

    lateinit var sceneManager: SceneManager
    private lateinit var raycastSystem: RaycastSystem
    private val floorPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)
    private val tempVec3 = Vector3()

    init {
        billboardShaderProvider.setBillboardLightingStrength(0.8f) // Adjust lighting as needed
        billboardShaderProvider.setMinLightLevel(0.4f)
        billboardModelBatch = ModelBatch(billboardShaderProvider)
    }

    fun initialize(blockSize: Float) {
        this.raycastSystem = RaycastSystem(blockSize)
        println("Initializing Interior System...")
        val modelBuilder = ModelBuilder() // Create one ModelBuilder to reuse

        for (interiorType in InteriorType.entries) {
            try {
                // Special handling for the randomizer placeholders
                if (interiorType.isRandomizer) {
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
                    // Differentiate between vertical billboards and floor objects
                    if (interiorType.isFloorObject) {
                        // Create a HORIZONTAL plane for floor objects like carpets
                        val model = modelBuilder.createRect(
                            -interiorType.width / 2f, 0f,  interiorType.depth / 2f, // back-left
                            interiorType.width / 2f, 0f,  interiorType.depth / 2f, // back-right
                            interiorType.width / 2f, 0f, -interiorType.depth / 2f, // front-right
                            -interiorType.width / 2f, 0f, -interiorType.depth / 2f, // front-left
                            0f, 1f, 0f, // normal (pointing straight up)
                            material,
                            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
                        )
                        interiorModels[interiorType] = model
                        println("Created 2D floor model for: ${interiorType.displayName}")
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
                }
            } catch (e: Exception) {
                println("Failed to load/create interior model for ${interiorType.displayName}: ${e.message}")
                e.printStackTrace()
            }
        }
        println("Interior System initialized with ${interiorModels.size} models.")
    }

    fun handlePlaceAction(ray: Ray) {
        if (sceneManager.currentScene != SceneType.HOUSE_INTERIOR) {
            println("Can only place interiors inside a house.")
            return
        }

        if (sceneManager.game.isPlacingExitDoorMode) {
            if (currentSelectedInterior != InteriorType.DOOR_INTERIOR) {
                println("You must place a DOOR to designate it as the exit.")
                sceneManager.game.uiManager.setPersistentMessage("ERROR: You must select and place a DOOR.")
                return
            }
        }

        if (Intersector.intersectRayPlane(ray, floorPlane, tempVec3)) {
            addInterior(tempVec3, currentSelectedInterior)
        }
    }

    fun handleRemoveAction(ray: Ray): Boolean {
        val interiorToRemove = raycastSystem.getInteriorAtRay(ray, sceneManager.activeInteriors)
        if (interiorToRemove != null) {
            removeInterior(interiorToRemove)
            return true
        }
        return false
    }

    private fun addInterior(position: Vector3, interiorType: InteriorType) {
        val newInterior = createInteriorInstance(interiorType) ?: return

        newInterior.position.set(position)
        if (interiorType.isFloorObject) {
            newInterior.position.y += 0.01f
        } else {
            newInterior.position.y += interiorType.height / 2f
        }
        newInterior.rotation = currentRotation
        newInterior.updateTransform()

        sceneManager.activeInteriors.add(newInterior)
        sceneManager.game.lastPlacedInstance = newInterior
        println("${interiorType.displayName} placed at: $position")

        // Assign the door and exit placement mode
        val uiManager = sceneManager.game.uiManager
        if (uiManager.isPlacingExitDoorMode && interiorType == InteriorType.DOOR_INTERIOR) {
            val house = uiManager.houseRequiringDoor
            house?.exitDoorId = newInterior.id
            println("SUCCESS: Door ${newInterior.id} assigned as exit for house ${house?.id}")

            // Exit the special mode
            uiManager.exitDoorPlacementModeCompleted()
        }
    }

    private fun removeInterior(interiorToRemove: GameInterior) {
        sceneManager.activeInteriors.removeValue(interiorToRemove, true)
        println("${interiorToRemove.interiorType.displayName} removed at: ${interiorToRemove.position}")
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
            GameInterior(
                interiorType = interiorType,
                instance = ModelInstance(it),
                scale = Vector3(interiorType.defaultScale)
            )
        }
    }

    fun updatePreview(ray: Ray) {
        // Only show a preview if the selected object is a spawnpoint
        if (currentSelectedInterior != InteriorType.PLAYER_SPAWNPOINT) {
            hidePreview() // Hide any existing preview if another type is selected
            return
        }

        val intersection = Vector3()
        val floorPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

        if (Intersector.intersectRayPlane(ray, floorPlane, intersection)) {
            // If we don't have a preview instance yet, create one
            if (previewInstance == null) {
                previewInstance = createInteriorInstance(InteriorType.PLAYER_SPAWNPOINT)?.also {
                    // Make the preview semi-transparent
                    val material = it.instance.materials.first()
                    val blendingAttr = material.get(BlendingAttribute.Type) as? BlendingAttribute
                    blendingAttr?.opacity = 0.6f // Set transparency
                }
            }

            // Update the preview's position and transform
            previewInstance?.let {
                it.position.set(intersection)
                // Adjust for ground offset and height, just like placing
                it.position.y += it.interiorType.groundOffset
                it.position.y += it.interiorType.height / 2f
                it.updateTransform()
            }
        }
    }

    fun hidePreview() {
        previewInstance = null
    }

    fun renderPreview(modelBatch: ModelBatch, environment: Environment) {
        // If the preview instance exists
        previewInstance?.let {
            if (it.interiorType.is2D) {
                modelBatch.render(it.instance, environment)
            }
        }
    }

    fun renderBillboards(camera: Camera, environment: Environment, interiors: com.badlogic.gdx.utils.Array<GameInterior>) {
        billboardShaderProvider.setEnvironment(environment)
        billboardModelBatch.begin(camera)

        // Collect all billboard instances.
        renderableBillboards.clear()
        for (interior in interiors) {
            if (interior.interiorType.is2D && !interior.interiorType.isFloorObject) {
                if (interior.interiorType != InteriorType.PLAYER_SPAWNPOINT) {
                    renderableBillboards.add(interior.instance)
                }
            }
        }

        // Render all billboards at once.
        if (renderableBillboards.size > 0) {
            billboardModelBatch.render(renderableBillboards, environment)
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
    val scale: Vector3,
    val id: String = UUID.randomUUID().toString(),
    var missionId: String? = null
) {
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
    private val tempIntersection = Vector3()

    val modelInstance: ModelInstance
        get() = this.instance

    fun getBoundingBox(out: BoundingBox): BoundingBox {
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

        var closestDistance2 = -1f
        var hit = false

        // We must check every triangle to find the one closest to the ray's origin.
        for (i in indexShorts.indices step 3) {
            val idx1 = indexShorts[i].toInt() * vertexSize
            val idx2 = indexShorts[i + 1].toInt() * vertexSize
            val idx3 = indexShorts[i + 2].toInt() * vertexSize

            v1.set(vertexFloats[idx1], vertexFloats[idx1 + 1], vertexFloats[idx1 + 2])
            v2.set(vertexFloats[idx2], vertexFloats[idx2 + 1], vertexFloats[idx2 + 2])
            v3.set(vertexFloats[idx3], vertexFloats[idx3 + 1], vertexFloats[idx3 + 2])

            v1.mul(instance.transform)
            v2.mul(instance.transform)
            v3.mul(instance.transform)

            // Use the Intersector.intersectRayTriangle overload that does NOT check face culling (backface culling = false)
            if (Intersector.intersectRayTriangle(ray, v1, v2, v3, tempIntersection)) {
                val dist2 = ray.origin.dst2(tempIntersection)

                // If this is the first hit, or if this hit is closer than the last one, we record it.
                if (!hit || dist2 < closestDistance2) {
                    closestDistance2 = dist2
                    outIntersection.set(tempIntersection) // This is our new best candidate
                    hit = true
                }
            }
        }
        return hit
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
        // 2D objects use the better rectangular collision check
        return collidesWithPlayerRectangular2D(playerPos, playerRadius)
    }

    fun collidesWithPlayerRectangular2D(playerPos: Vector3, playerRadius: Float): Boolean {
        val objectHalfWidth = (interiorType.width * scale.x) * 0.5f
        // The 0.8f multiplier gives the flat object some "depth" for collision.
        val objectHalfDepth = (interiorType.depth * scale.z) * 0.8f

        // Check if player is within the rectangular bounds
        val dx = kotlin.math.abs(playerPos.x - position.x)
        val dz = kotlin.math.abs(playerPos.z - position.z)

        // Use rectangular collision instead of circular
        return dx <= (objectHalfWidth + playerRadius) && dz <= (objectHalfDepth + playerRadius)
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
