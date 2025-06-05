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

// Object system class to manage 3D cross-shaped objects
class ObjectSystem {
    private val objectModels = mutableMapOf<ObjectType, Model>()
    private val objectTextures = mutableMapOf<ObjectType, Texture>()
    var currentSelectedObject = ObjectType.LANTERN
        private set
    var currentSelectedObjectIndex = 0
        private set

    fun initialize() {
        val modelBuilder = ModelBuilder()

        // Load textures and create models for each object type
        for (objectType in ObjectType.values()) {
            try {
                // Load texture
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

                println("Loaded object type: ${objectType.displayName}")
            } catch (e: Exception) {
                println("Failed to load texture for ${objectType.displayName}: ${e.message}")
            }
        }
    }

    private fun createCrossModel(modelBuilder: ModelBuilder, material: Material, objectType: ObjectType): Model {
        // Calculate dimensions based on the original image size (278 x 405)
        // Scale it down to reasonable game units
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
        currentSelectedObjectIndex = (currentSelectedObjectIndex + 1) % ObjectType.values().size
        currentSelectedObject = ObjectType.values()[currentSelectedObjectIndex]
        println("Selected object: ${currentSelectedObject.displayName}")
    }

    fun previousObject() {
        currentSelectedObjectIndex = if (currentSelectedObjectIndex > 0) {
            currentSelectedObjectIndex - 1
        } else {
            ObjectType.values().size - 1
        }
        currentSelectedObject = ObjectType.values()[currentSelectedObjectIndex]
        println("Selected object: ${currentSelectedObject.displayName}")
    }

    fun createObjectInstance(objectType: ObjectType): ModelInstance? {
        val model = objectModels[objectType]
        return model?.let { ModelInstance(it) }
    }

    fun getCurrentObjectTexture(): Texture? {
        return objectTextures[currentSelectedObject]
    }

    fun dispose() {
        objectModels.values.forEach { it.dispose() }
        objectTextures.values.forEach { it.dispose() }
    }
}

// Game object class to store object data
data class GameObject(
    val modelInstance: ModelInstance,
    val objectType: ObjectType,
    val position: Vector3
)

// Object type definitions
enum class ObjectType(
    val displayName: String,
    val texturePath: String,
    val width: Float,
    val height: Float
) {
    TREE("Tree", "textures/objects/models/tree.png", 14.5f, 15.6f), // Scaled down from 278x405
    LANTERN("Lantern","textures/objects/models/lantern.png", 3f, 11f)
    // FLOWER("Flower", "textures/objects/flower.png", 1f, 1.5f),
    // BUSH("Bush", "textures/objects/bush.png", 2f, 1.5f),
}
