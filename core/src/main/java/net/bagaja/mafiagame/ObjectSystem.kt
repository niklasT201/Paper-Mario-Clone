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

    // Keep track of object-light associations
    private val objectLightAssociations = mutableMapOf<Int, Int>()
    private var nextObjectId = 1

    fun initialize() {
        val modelBuilder = ModelBuilder()

        // Load textures and create models for each object type
        for (objectType in ObjectType.entries) {
            try {
                if (objectType.isInvisible) {
                   // Create a tiny, completely transparent model for the standard "invisible" instance
                    val invisibleMaterial = Material(
                        ColorAttribute.createDiffuse(0f, 0f, 0f, 0f),
                        BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.0f),
                        IntAttribute.createCullFace(GL20.GL_NONE)
                    )
                    val invisibleModel = modelBuilder.createBox(0.1f, 0.1f, 0.1f, invisibleMaterial,
                        (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong())
                    objectModels[objectType] = invisibleModel

                    // Now create the unique, visible debug model
                    when (objectType) {
                        ObjectType.LIGHT_SOURCE -> {
                            // Create invisible light source
                            val debugMaterial = Material(
                                ColorAttribute.createDiffuse(Color.YELLOW),
                                ColorAttribute.createEmissive(0.3f, 0.3f, 0f, 1f),
                                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.7f)
                            )
                            val debugModel = modelBuilder.createSphere(objectType.width, objectType.height, objectType.width, 12, 12, debugMaterial,
                                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong())
                            debugModels[objectType] = debugModel
                        }
                        ObjectType.SPAWNER -> {
                            // A purple cube to distinguish it from light sources
                            val debugMaterial = Material(
                                ColorAttribute.createDiffuse(Color.PURPLE),
                                ColorAttribute.createEmissive(0.3f, 0.0f, 0.3f, 1f),
                                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.7f)
                            )
                            val debugModel = modelBuilder.createBox(objectType.width, objectType.height, objectType.width, debugMaterial,
                                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong())
                            debugModels[objectType] = debugModel
                        }
                        ObjectType.TELEPORTER -> {
                            // A cyan, semi-transparent box to represent the teleporter pad in debug mode
                            val debugMaterial = Material(
                                ColorAttribute.createDiffuse(Color.CYAN),
                                ColorAttribute.createEmissive(0.0f, 0.3f, 0.3f, 1f),
                                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.5f)
                            )
                            val debugModel = modelBuilder.createBox(
                                objectType.width, // Use the dimensions from the enum
                                objectType.height,
                                objectType.width, // Use width for depth to make a square pad
                                debugMaterial,
                                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
                            )
                            debugModels[objectType] = debugModel
                        }
                        else -> { /* No other invisible types yet */ }
                    }

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

                    // Decide which model to create
                    val model = if (objectType.isSinglePlane) {
                        createSinglePlaneModel(modelBuilder, material, objectType)
                    } else {
                        // Create cross-shaped model
                        createCrossModel(modelBuilder, material, objectType)
                    }
                    objectModels[objectType] = model
                }

                println("Loaded object type: ${objectType.displayName}")
            } catch (e: Exception) {
                println("Failed to load object ${objectType.displayName}: ${e.message}")
            }
        }
    }

    private fun createSinglePlaneModel(modelBuilder: ModelBuilder, material: Material, objectType: ObjectType): Model {
        val width = objectType.width
        val height = objectType.height
        val halfWidth = width / 2f

        modelBuilder.begin()

        val part = modelBuilder.part(
            "plane",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or
                VertexAttributes.Usage.Normal or
                VertexAttributes.Usage.TextureCoordinates).toLong(),
            material
        )

        // Vertices for a single, upright quad
        part.vertex(-halfWidth, 0f, 0f, 0f, 0f, 1f, 0f, 1f) // Bottom left
        part.vertex(halfWidth, 0f, 0f, 0f, 0f, 1f, 1f, 1f)  // Bottom right
        part.vertex(halfWidth, height, 0f, 0f, 0f, 1f, 1f, 0f) // Top right
        part.vertex(-halfWidth, height, 0f, 0f, 0f, 1f, 0f, 0f) // Top left

        // Triangles for the quad
        part.triangle(0, 1, 2)
        part.triangle(2, 3, 0)

        return modelBuilder.end()
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

    private fun createObjectInstance(objectType: ObjectType): ModelInstance? {
        val model = objectModels[objectType]
        return model?.let { ModelInstance(it) }
    }

    private fun createDebugInstance(objectType: ObjectType): ModelInstance? {
        val debugModel = debugModels[objectType]
        return debugModel?.let { ModelInstance(it) }
    }

    // Enhanced method to create game object with automatic light source
    fun createGameObjectWithLight(
        objectType: ObjectType,
        position: Vector3,
        lightingManager: LightingManager? = null
    ): GameObject? {
        val modelInstance = createObjectInstance(objectType) ?: return null
        val debugInstance = if (objectType.isInvisible) createDebugInstance(objectType) else null

        val objectId = nextObjectId++
        var associatedLightId: Int? = null

        // Create light source if the object type has one
        if (objectType.hasLightSource) {
            val lightPosition = Vector3(position).apply {
                y += objectType.lightOffsetY
            }

            val lightSource = createLightSource(
                position = lightPosition,
                intensity = objectType.lightIntensity,
                range = objectType.lightRange,
                color = objectType.getLightColor()
            )

            associatedLightId = lightSource.id
            objectLightAssociations[objectId] = lightSource.id

            // Add to lighting manager if provided
            lightingManager?.let { lm ->
                val lightInstances = createLightSourceInstances(lightSource)
                lm.addLightSource(lightSource, lightInstances)
            }

            println("Created ${objectType.displayName} with light source #${lightSource.id}")
        }

        return GameObject(
            id = objectId,
            modelInstance = modelInstance,
            objectType = objectType,
            position = position,
            debugInstance = debugInstance,
            associatedLightId = associatedLightId
        )
    }

    // Method to remove game object and its associated light
    fun removeGameObjectWithLight(gameObject: GameObject, lightingManager: LightingManager? = null) {
        // Remove associated light source if it exists
        gameObject.associatedLightId?.let { lightId ->
            lightingManager?.removeLightSource(lightId)
            removeLightSource(lightId)
            objectLightAssociations.remove(gameObject.id)
            println("Removed light source #$lightId associated with object #${gameObject.id}")
        }
    }

    // Method to toggle light for objects that support it
    fun toggleObjectLight(gameObject: GameObject, lightingManager: LightingManager): Boolean {
        if (!gameObject.objectType.hasLightSource) return false

        val lightId = gameObject.associatedLightId ?: return false
        val lightSource = lightSources[lightId] ?: return false

        lightSource.isEnabled = !lightSource.isEnabled
        lightSource.updatePointLight()

        println("${gameObject.objectType.displayName} light #$lightId: ${if (lightSource.isEnabled) "ON" else "OFF"}")
        return true
    }

    fun createLightSource(
        position: Vector3,
        intensity: Float = LightSource.DEFAULT_INTENSITY,
        range: Float = LightSource.DEFAULT_RANGE,
        color: Color = Color(LightSource.DEFAULT_COLOR_R, LightSource.DEFAULT_COLOR_G, LightSource.DEFAULT_COLOR_B, 1f),
        rotationX: Float = 0f,
        rotationY: Float = 0f,
        rotationZ: Float = 0f,
        flickerMode: FlickerMode = FlickerMode.NONE,
        loopOnDuration: Float = 0.1f,
        loopOffDuration: Float = 0.1f,
        timedFlickerLifetime: Float = 10f
    ): LightSource {
        val lightSource = LightSource(
            id = nextLightId++,
            position = position,
            intensity = intensity,
            range = range,
            color = color,
            isEnabled = true,
            rotationX = rotationX,
            rotationY = rotationY,
            rotationZ = rotationZ,
            flickerMode = flickerMode,
            loopOnDuration = loopOnDuration,
            loopOffDuration = loopOffDuration,
            timedFlickerLifetime = timedFlickerLifetime
        )
        lightSource.updateTransform() // Apply initial rotation
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
        objectLightAssociations.clear()
    }
}

// Game object class to store object data
data class GameObject(
    val id: Int,
    val modelInstance: ModelInstance,
    val objectType: ObjectType,
    val position: Vector3,
    var debugInstance: ModelInstance? = null, // For debug visualization of invisible objects
    var associatedLightId: Int? = null, // Link to light source if this object has one
    var isBroken: Boolean = false // For future lantern breaking functionality
) {
    init {
        // Apply initial transform to the model instance(s)
        modelInstance.transform.setTranslation(position)
        debugInstance?.transform?.setTranslation(position)
    }

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
            null // Invisible object not in debug mode
        }
    }

    // Check if this object has an associated light source
    fun hasLight(): Boolean = associatedLightId != null && objectType.hasLightSource

    // Break the lantern (disable its light)
    fun breakLantern(objectSystem: ObjectSystem, lightingManager: LightingManager) {
        if (objectType == ObjectType.LANTERN && !isBroken) {
            isBroken = true

            // Disable the associated light
            associatedLightId?.let { lightId ->
                objectSystem.getAllLightSources().find { it.id == lightId }?.let { lightSource ->
                    lightSource.isEnabled = false
                    lightSource.updatePointLight()
                }
            }

            println("Lantern at $position is now broken! Light disabled.")
        }
    }
}

data class GameTeleporter(
    val id: String,
    val gameObject: GameObject,
    var linkedTeleporterId: String? = null,
    var name: String = "Unnamed Link"
)

// Enhanced object type definitions with invisible light source and future broken lantern
enum class ObjectType(
    val displayName: String,
    val texturePath: String,
    val width: Float,
    val height: Float,
    val isInvisible: Boolean = false,
    val canBePlacedAnywhere: Boolean = false, // For allowing placement inside blocks
    val hasLightSource: Boolean = false,
    val lightIntensity: Float = 0f,
    val lightRange: Float = 0f,
    val lightColorR: Float = 1f,
    val lightColorG: Float = 1f,
    val lightColorB: Float = 1f,
    val lightOffsetY: Float = 0f, // Vertical offset from object center
    val isSinglePlane: Boolean = false
) {
    TREE("Tree", "textures/objects/models/tree.png", 14.5f, 15.6f),
    BUSH("Bush", "textures/objects/models/bush.png", 3f, 3f),
    IRON_FENCE("Iron Fence", "textures/objects/models/iron_fence.png", 10f, 5f, isSinglePlane = true),
    GRAFITI_MOTH("Moth Graffiti", "textures/objects/models/grafity_moth.png", 5f, 5f,
        isSinglePlane = true
    ),

    FIRE_SPREAD("Spreading Fire", "textures/particles/fire_spread/fire_spread_frame_one.png", 8f, 8f,
        isSinglePlane = true,
        hasLightSource = true, // Fire should emit light!
        lightIntensity = 60f,
        lightRange = 25f,
        lightColorR = 1.0f, lightColorG = 0.5f, lightColorB = 0.1f, // Orange glow
        lightOffsetY = 2f
    ),

    LANTERN("Lantern", "textures/objects/models/lantern.png", 3f, 11f,
        hasLightSource = true,
        lightIntensity = 55f,
        lightRange = 20f,
        lightColorR = 1f,    // Orange color
        lightColorG = 0.6f,
        lightColorB = 0.2f,
        lightOffsetY = 7.5f  // Place light at middle of lantern
    ),

    LIGHT_SOURCE("Light Source", "", 2f, 2f, true, true),
    SPAWNER("Particle Spawner", "", 2f, 2f, true, canBePlacedAnywhere = true),

    BROKEN_LANTERN("Broken Lantern", "textures/objects/models/broken_lantern.png", 3f, 11f,
        hasLightSource = false // Broken lanterns don't emit light
    ),

    TELEPORTER("Teleporter", "", 1f, 1f,
        isSinglePlane = true, isInvisible = true,
    ),

    TURNEDOFF_LANTERN("Turned Off Lantern", "textures/objects/models/turnedoff_lantern.png", 3f, 11f,
        hasLightSource = false // Turned off lanterns don't emit light
    );

    // Helper function to get light color as Color object
    fun getLightColor(): Color {
        return Color(lightColorR, lightColorG, lightColorB, 1f)
    }
}
