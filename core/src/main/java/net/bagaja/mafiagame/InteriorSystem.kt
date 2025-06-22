package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader
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
    val modelPath: String? = null, // null means 2D billboard
    val width: Float,
    val height: Float,
    val depth: Float = 1f,
    val hasCollision: Boolean = true,
    val category: InteriorCategory = InteriorCategory.FURNITURE
) {
    // 2D Billboard objects
    PLANT_SMALL("Small Plant", "textures/interior/room_floor_tile.png", null, 1f, 2f, 1f, false, InteriorCategory.DECORATION),
    PAINTING("Painting", "Interiors/painting.png", null, 2f, 1.5f, 0.1f, false, InteriorCategory.DECORATION),
    LAMP("Lamp", "Interiors/lamp.png", null, 0.8f, 2.5f, 0.8f, false, InteriorCategory.LIGHTING),
    BOOKSHELF_2D("Bookshelf", "Interiors/bookshelf.png", null, 2f, 3f, 0.5f, true, InteriorCategory.FURNITURE),

    // 3D Model objects (same system as houses)
    SHELF("Wooden Table", "Models/shelf_model.png", "Models/bookshelf.g3dj", 2f, 1f, 4f, true, InteriorCategory.FURNITURE),
    CHAIR_3D("Chair", "Interiors/chair.png", "Interiors/chair.g3dj", 1f, 2f, 1f, true, InteriorCategory.FURNITURE),
    SOFA_3D("Sofa", "Interiors/sofa.png", "Interiors/sofa.g3dj", 1.5f, 1f, 3f, true, InteriorCategory.FURNITURE),
    BED_3D("Bed", "Interiors/bed.png", "Interiors/bed.g3dj", 2f, 1f, 4f, true, InteriorCategory.FURNITURE),
    DESK_3D("Desk", "Interiors/desk.png", "Interiors/desk.g3dj", 1.5f, 1.2f, 3f, true, InteriorCategory.FURNITURE),
    CABINET_3D("Cabinet", "Interiors/cabinet.png", "Interiors/cabinet.g3dj", 1f, 2.5f, 2f, true, InteriorCategory.FURNITURE),
    STOVE_3D("Stove", "Interiors/stove.png", "Interiors/stove.g3dj", 1f, 1.2f, 2f, true, InteriorCategory.APPLIANCE),
    FRIDGE_3D("Refrigerator", "Interiors/fridge.png", "Interiors/fridge.g3dj", 1.5f, 2.5f, 2f, true, InteriorCategory.APPLIANCE);

    val is3D: Boolean get() = modelPath != null
    val is2D: Boolean get() = modelPath == null
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

    var currentSelectedInterior = InteriorType.SHELF
    var currentSelectedInteriorIndex = 0

    override var finePosMode = false
    override val fineStep = 0.25f

    // Rotation support
    var currentRotation = 0f
        private set
    private val rotationStep = 90f

    fun initialize() {
        println("Initializing Interior System...")

        for (interiorType in InteriorType.entries) {
            try {
                // Load texture (common for both 2D and 3D)
                val texture = Texture(Gdx.files.internal(interiorType.texturePath), false)
                texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
                interiorTextures[interiorType] = texture

                // Load 3D model if it exists
                if (interiorType.is3D) {
                    val model = modelLoader.loadModel(Gdx.files.internal(interiorType.modelPath!!))

                    // Apply texture to model materials
                    for (material in model.materials) {
                        val textureAttribute = TextureAttribute.createDiffuse(texture)
                        material.set(textureAttribute)
                    }

                    interiorModels[interiorType] = model
                    println("Loaded 3D interior model: ${interiorType.displayName}")
                } else {
                    println("Loaded 2D interior texture: ${interiorType.displayName}")
                }
            } catch (e: Exception) {
                println("Failed to load interior ${interiorType.displayName}: ${e.message}")
                e.printStackTrace()
            }
        }

        println("Interior System initialized with ${interiorTextures.size} items")
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
        return when {
            interiorType.is3D -> {
                val model = interiorModels[interiorType]
                model?.let {
                    val instance = ModelInstance(it)
                    GameInterior(interiorType, instance = instance)
                }
            }
            interiorType.is2D -> {
                val texture = interiorTextures[interiorType]
                texture?.let {
                    GameInterior(interiorType, texture = it)
                }
            }
            else -> null
        }
    }

    fun getTexture(interiorType: InteriorType): Texture? {
        return interiorTextures[interiorType]
    }

    fun dispose() {
        interiorModels.values.forEach { it.dispose() }
        interiorTextures.values.forEach { it.dispose() }
    }
}

// Game interior class that handles both 2D and 3D objects
data class GameInterior(
    val interiorType: InteriorType,
    val instance: ModelInstance? = null, // For 3D objects
    val texture: Texture? = null, // For 2D objects
    val position: Vector3 = Vector3(),
    var rotation: Float = 0f,
    val scale: Vector3 = Vector3(1f, 1f, 1f),
    val id: String = UUID.randomUUID().toString()
) {
    // For 3D collision detection (same as GameHouse)
    private val mesh = instance?.model?.meshes?.firstOrNull()
    private val vertexFloats: FloatArray?
    private val indexShorts: ShortArray?
    private val vertexSize: Int

    // Helper vectors for collision detection
    private val v1 = Vector3()
    private val v2 = Vector3()
    private val v3 = Vector3()

    // 2D billboard matrix for rendering
    private val billboardMatrix = Matrix4()

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

    fun updateTransform() {
        if (interiorType.is3D && instance != null) {
            // Update 3D model transform
            instance.transform.setToTranslation(position)
            instance.transform.rotate(Vector3.Y, rotation)
            instance.transform.scale(scale.x, scale.y, scale.z)
        } else if (interiorType.is2D) {
            // Update 2D billboard matrix
            billboardMatrix.setToTranslation(position)
            billboardMatrix.rotate(Vector3.Y, rotation)
            billboardMatrix.scale(scale.x, scale.y, scale.z)
        }
    }

    fun render3D(modelBatch: ModelBatch, environment: Environment) {
        if (interiorType.is3D && instance != null) {
            modelBatch.render(instance, environment)
        }
    }

    fun render2D(spriteBatch: SpriteBatch, camera: Camera) {
        if (interiorType.is2D && texture != null) {
            spriteBatch.begin()

            // Calculate billboard position facing camera
            val camPos = camera.position
            val dirToCam = Vector3(camPos).sub(position).nor()
            val right = Vector3(dirToCam).crs(Vector3.Y).nor()
            val up = Vector3.Y

            // Calculate billboard corners
            val halfWidth = interiorType.width * scale.x * 0.5f
            val halfHeight = interiorType.height * scale.y * 0.5f

            val bottomLeft = Vector3(position).sub(right.x * halfWidth, halfHeight, right.z * halfWidth)
            val bottomRight = Vector3(position).add(right.x * halfWidth, -halfHeight, right.z * halfWidth)
            val topLeft = Vector3(position).sub(right.x * halfWidth, halfHeight, right.z * halfWidth)
            val topRight = Vector3(position).add(right.x * halfWidth, halfHeight, right.z * halfWidth)

            // Project to screen coordinates and draw
            val textureRegion = TextureRegion(texture)

            // Simple billboard rendering (you may need to adjust based on your camera system)
            spriteBatch.draw(textureRegion,
                position.x - halfWidth, position.z - halfWidth,
                halfWidth, halfWidth,
                interiorType.width * scale.x, interiorType.height * scale.y,
                1f, 1f, rotation)

            spriteBatch.end()
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

            v1.mul(instance!!.transform)
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

            v1.mul(instance!!.transform)
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

        val distance = Vector3(playerPos).sub(position).len()
        val collisionRadius = (interiorType.width + interiorType.depth) * 0.5f * scale.x
        return distance < (playerRadius + collisionRadius)
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
