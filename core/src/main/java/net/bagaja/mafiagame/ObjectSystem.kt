package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox

// Object system class to manage 3D cross-shaped objects and light sources
class ObjectSystem: IFinePositionable {
    private val objectModels = mutableMapOf<ObjectType, Model>()
    private val objectTextures = mutableMapOf<ObjectType, Texture>()
    private val debugModels = mutableMapOf<ObjectType, Model>() // For showing invisible objects in debug mode

    private val lightSources = mutableMapOf<Int, LightSource>()
    private var nextLightId = 1
    private val modelBuilder = ModelBuilder()

    var currentSelectedObject = ObjectType.LIGHT_SOURCE
        private set
    var currentSelectedObjectIndex = 0
        private set

    // Fine positioning mode
    override var finePosMode = false
    override val fineStep = 0.25f

    // Debug mode to show invisible objects
    var debugMode = false
        set(value) {
            field = value
            println("Debug mode: ${if (value) "ON" else "OFF"}")
        }

    fun initialize() {
        val modelBuilder = ModelBuilder()

        // Load textures and create models for each object type
        for (objectType in ObjectType.entries) {
            try {
                if (objectType.isInvisible) {
                    // Create invisible light source
                    createInvisibleLightSource(modelBuilder, objectType)
                } else {
                    // Load texture for visible objects
                    val texture = Texture(Gdx.files.internal(objectType.texturePath))
                    texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                    objectTextures[objectType] = texture

                    // Create material with texture and transparency
                    val material = Material(
                        TextureAttribute.createDiffuse(texture),
                        BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                        IntAttribute.createCullFace(GL20.GL_NONE) // Disable backface culling
                    )

                    // Create cross-shaped model (X when viewed from above)
                    val model = createCrossModel(modelBuilder, material, objectType)
                    objectModels[objectType] = model
                }

                println("Loaded object type: ${objectType.displayName}")
            } catch (e: Exception) {
                println("Failed to load object ${objectType.displayName}: ${e.message}")
            }
        }
    }

    private fun createInvisibleLightSource(modelBuilder: ModelBuilder, objectType: ObjectType) {
        // Create completely transparent material for invisible light source
        val invisibleMaterial = Material(
            ColorAttribute.createDiffuse(0f, 0f, 0f, 0f), // Completely transparent
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.0f),
            IntAttribute.createCullFace(GL20.GL_NONE) // Disable culling so it doesn't interfere
        )

        // Create a tiny invisible point - use a very small size to minimize interference
        val invisibleModel = modelBuilder.createBox(
            0.1f, 0.1f, 0.1f, // Very small size
            invisibleMaterial,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        )
        objectModels[objectType] = invisibleModel

        // Create debug visualization (semi-transparent bright yellow sphere for better visibility)
        val debugMaterial = Material(
            ColorAttribute.createDiffuse(Color.YELLOW),
            ColorAttribute.createEmissive(0.3f, 0.3f, 0f, 1f), // Add emissive glow
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.7f) // More visible
        )

        // Use sphere instead of box for better light source representation
        val debugModel = modelBuilder.createSphere(
            objectType.width, objectType.height, objectType.width,
            12, 12, // segments for sphere
            debugMaterial,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        )
        debugModels[objectType] = debugModel
    }

    private fun createCrossModel(modelBuilder: ModelBuilder, material: Material, objectType: ObjectType): Model {
        // Calculate dimensions based on the original image size
        val width = objectType.width
        val height = objectType.height
        val halfWidth = width / 2f
        val halfHeight = height / 2f

        // Create a model with two intersecting quads forming an X shape
        modelBuilder.begin()

        // First quad (front-back orientation)
        val part1 = modelBuilder.part(
            "cross1",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or
                VertexAttributes.Usage.Normal or
                VertexAttributes.Usage.TextureCoordinates).toLong(),
            material
        )

        // Vertices for first quad (Z-axis oriented)
        part1.vertex(-halfWidth, 0f, 0f, 0f, 0f, 1f, 0f, 1f) // Bottom left
        part1.vertex(halfWidth, 0f, 0f, 0f, 0f, 1f, 1f, 1f)  // Bottom right
        part1.vertex(halfWidth, height, 0f, 0f, 0f, 1f, 1f, 0f) // Top right
        part1.vertex(-halfWidth, height, 0f, 0f, 0f, 1f, 0f, 0f) // Top left

        // Triangles for first quad
        part1.triangle(0, 1, 2) // First triangle
        part1.triangle(2, 3, 0) // Second triangle

        // Second quad (left-right orientation, rotated 90 degrees)
        val part2 = modelBuilder.part(
            "cross2",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or
                VertexAttributes.Usage.Normal or
                VertexAttributes.Usage.TextureCoordinates).toLong(),
            material
        )

        // Vertices for second quad (X-axis oriented)
        part2.vertex(0f, 0f, -halfWidth, 1f, 0f, 0f, 0f, 1f) // Bottom left
        part2.vertex(0f, 0f, halfWidth, 1f, 0f, 0f, 1f, 1f)  // Bottom right
        part2.vertex(0f, height, halfWidth, 1f, 0f, 0f, 1f, 0f) // Top right
        part2.vertex(0f, height, -halfWidth, 1f, 0f, 0f, 0f, 0f) // Top left

        // Triangles for second quad
        part2.triangle(4, 5, 6) // First triangle
        part2.triangle(6, 7, 4) // Second triangle

        return modelBuilder.end()
    }

    fun nextObject() {
        currentSelectedObjectIndex = (currentSelectedObjectIndex + 1) % ObjectType.entries.size
        currentSelectedObject = ObjectType.entries.toTypedArray()[currentSelectedObjectIndex]
        println("Selected object: ${currentSelectedObject.displayName}")
    }

    fun previousObject() {
        currentSelectedObjectIndex = if (currentSelectedObjectIndex > 0) {
            currentSelectedObjectIndex - 1
        } else {
            ObjectType.entries.size - 1
        }
        currentSelectedObject = ObjectType.entries.toTypedArray()[currentSelectedObjectIndex]
        println("Selected object: ${currentSelectedObject.displayName}")
    }

    fun toggleDebugMode() {
        debugMode = !debugMode
    }

    fun createObjectInstance(objectType: ObjectType): ModelInstance? {
        val model = objectModels[objectType]
        return model?.let { ModelInstance(it) }
    }

    fun createDebugInstance(objectType: ObjectType): ModelInstance? {
        val debugModel = debugModels[objectType]
        return debugModel?.let { ModelInstance(it) }
    }

    fun createLightSource(
        position: Vector3,
        intensity: Float = LightSource.DEFAULT_INTENSITY,
        range: Float = LightSource.DEFAULT_RANGE,
        color: Color = Color(LightSource.DEFAULT_COLOR_R, LightSource.DEFAULT_COLOR_G, LightSource.DEFAULT_COLOR_B, 1f)
    ): LightSource {
        val lightSource = LightSource(nextLightId++, position, intensity, range, color)
        lightSources[lightSource.id] = lightSource
        return lightSource
    }

    fun removeLightSource(lightId: Int): LightSource? {
        val lightSource = lightSources.remove(lightId)
        lightSource?.dispose()
        return lightSource
    }

    fun getAllLightSources(): Collection<LightSource> = lightSources.values

    fun getLightSourceAt(position: Vector3, tolerance: Float = 2f): LightSource? {
        return lightSources.values.find { lightSource ->
            lightSource.position.dst(position) <= tolerance
        }
    }

    fun createLightSourceInstances(lightSource: LightSource): Pair<ModelInstance, ModelInstance> {
        val invisible = lightSource.createModelInstance(modelBuilder)
        val debug = lightSource.createDebugModelInstance(modelBuilder)
        return Pair(invisible, debug)
    }

    fun dispose() {
        objectModels.values.forEach { it.dispose() }
        objectTextures.values.forEach { it.dispose() }
        debugModels.values.forEach { it.dispose() }
        lightSources.values.forEach { it.dispose() }
        lightSources.clear()
    }
}

// Game object class to store object data
data class GameObject(
    val modelInstance: ModelInstance,
    val objectType: ObjectType,
    val position: Vector3,
    var debugInstance: ModelInstance? = null, // For debug visualization of invisible objects
    var pointLight: PointLight? = null, // Actual light source for lighting effects
    var isBroken: Boolean = false // For future lantern breaking functionality
) {
    // Get bounding box for collision detection
    fun getBoundingBox(): BoundingBox {
        val bounds = BoundingBox()
        val halfWidth = objectType.width / 2f
        val halfHeight = objectType.height / 2f

        bounds.set(
            Vector3(position.x - halfWidth, position.y, position.z - halfWidth),
            Vector3(position.x + halfWidth, position.y + objectType.height, position.z + halfWidth)
        )
        return bounds
    }

    // Check if this object should be rendered (visible objects or debug mode for invisible ones)
    fun shouldRender(debugMode: Boolean): Boolean {
        return !objectType.isInvisible || debugMode
    }

    // Get the appropriate model instance to render
    fun getRenderInstance(debugMode: Boolean): ModelInstance? {
        return if (objectType.isInvisible && debugMode) {
            debugInstance
        } else if (!objectType.isInvisible) {
            modelInstance
        } else {
            null // Invisible object not in debug mode - don't render at all
        }
    }

    // Create actual light source for lighting effects
    fun createLight(): PointLight? {
        return if (objectType.isInvisible && objectType == ObjectType.LIGHT_SOURCE) {
            val light = PointLight()
            light.set(
                Color(1f, 0.9f, 0.7f, 1f), // Warm white light
                position.x, position.y + objectType.height / 2f, position.z,
                50f
            )
            pointLight = light
            light
        } else {
            null
        }
    }

    // Update light position if the object moves
    fun updateLightPosition() {
        pointLight?.set(
            pointLight!!.color,
            position.x, position.y + objectType.height / 2f, position.z,
            pointLight!!.intensity
        )
    }

    // For future functionality: break the lantern (change texture)
    fun breakLantern() {
        if (objectType == ObjectType.LANTERN && !isBroken) {
            isBroken = true
            // TODO: Change texture to broken lantern
            // This would require texture swapping functionality
            println("Lantern at ${position} is now broken!")
        }
    }
}

// Enhanced object type definitions with invisible light source and future broken lantern
enum class ObjectType(
    val displayName: String,
    val texturePath: String,
    val width: Float,
    val height: Float,
    val isInvisible: Boolean = false,
    val canBePlacedAnywhere: Boolean = false // For allowing placement inside blocks
) {
    TREE("Tree", "textures/objects/models/tree.png", 14.5f, 15.6f),
    LANTERN("Lantern", "textures/objects/models/lantern.png", 3f, 11f),
    LIGHT_SOURCE("Light Source", "", 2f, 2f, true, true), // Invisible, can be placed anywhere
    BROKEN_LANTERN("Broken Lantern", "textures/objects/models/broken_lantern.png", 3f, 11f),
    TURNEDOFF_LANTERN("Turned Off Lantern", "textures/objects/models/turnedoff_lantern.png", 3f, 11f),
}
