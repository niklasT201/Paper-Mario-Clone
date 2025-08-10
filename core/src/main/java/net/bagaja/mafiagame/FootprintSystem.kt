package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import java.util.*

// Data class to hold the state of a single footprint instance
data class GameFootprint(
    val id: String = UUID.randomUUID().toString(),
    val instance: ModelInstance,
    val position: Vector3,
    var lifetime: Float,
    val initialLifetime: Float,
    private val blendingAttribute: BlendingAttribute
) {
    // A helper method to easily update the opacity for fading
    fun setOpacity(opacity: Float) {
        blendingAttribute.opacity = opacity.coerceIn(0f, 1f)
    }
}

class FootprintSystem {
    private lateinit var footprintTexture: Texture
    private lateinit var footprintModel: Model
    private lateinit var modelBatch: ModelBatch
    private val renderableInstances = Array<ModelInstance>()
    private lateinit var shaderProvider: BillboardShaderProvider

    companion object {
        const val FOOTPRINT_LIFETIME = 8.0f // Footprints last for 12 seconds
        const val FOOTPRINT_FADE_TIME = 2.0f  // Start fading 3 seconds before disappearing
    }

    fun initialize() {
        shaderProvider = BillboardShaderProvider().apply {
            setBillboardLightingStrength(0.8f) // Let them be affected by world light
            setMinLightLevel(0.3f)             // Don't let them be pure black in shadows
        }
        modelBatch = ModelBatch(shaderProvider)

        val modelBuilder = ModelBuilder()

        try {
            // Load the texture
            footprintTexture = Texture(Gdx.files.internal("textures/particles/bloody_shoeprints.png"), true).apply {
                setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
            }

            // Create a material with transparency
            val material = Material(
                TextureAttribute.createDiffuse(footprintTexture),
                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                IntAttribute.createCullFace(GL20.GL_NONE)
            )

            // Create a flat, horizontal plane model for the footprint
            footprintModel = modelBuilder.createRect(
                -0.5f, 0f, 0.5f, -0.5f, 0f, -0.5f,
                0.5f, 0f, -0.5f, 0.5f, 0f, 0.5f,
                0f, 1f, 0f, // Normal pointing up
                material,
                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
            )
        } catch (e: Exception) {
            println("ERROR: Could not load bloody_prints.png texture: ${e.message}")
        }
    }

    fun spawnFootprint(position: Vector3, rotation: Float, sceneManager: SceneManager) {
        if (!::footprintModel.isInitialized) return

        val instance = ModelInstance(footprintModel)
        instance.userData = "effect" // For potential future use

        val blendingAttribute = instance.materials.first().get(BlendingAttribute.Type) as BlendingAttribute

        val newFootprint = GameFootprint(
            instance = instance,
            position = position.cpy().add(0f, 0.06f, 0f), // Place slightly above ground to prevent z-fighting
            lifetime = FOOTPRINT_LIFETIME,
            initialLifetime = FOOTPRINT_LIFETIME,
            blendingAttribute = blendingAttribute
        )

        // Set the footprint's initial position, rotation, and size
        newFootprint.instance.transform.setToTranslation(newFootprint.position)
        newFootprint.instance.transform.rotate(Vector3.Y, rotation)
        newFootprint.instance.transform.scale(1f, 1f, 1f) // 1x1 unit size

        sceneManager.activeFootprints.add(newFootprint)
    }

    fun update(deltaTime: Float, activeFootprints: Array<GameFootprint>) {
        val iterator = activeFootprints.iterator()
        while (iterator.hasNext()) {
            val footprint = iterator.next()
            footprint.lifetime -= deltaTime

            // If lifetime is over, remove the footprint
            if (footprint.lifetime <= 0) {
                iterator.remove()
                continue
            }

            // Handle fading out
            if (footprint.lifetime < FOOTPRINT_FADE_TIME) {
                val opacity = footprint.lifetime / FOOTPRINT_FADE_TIME
                footprint.setOpacity(opacity)
            }
        }
    }

    fun render(camera: Camera, environment: Environment, activeFootprints: Array<GameFootprint>) {
        if (activeFootprints.isEmpty) return

        shaderProvider.setEnvironment(environment)
        modelBatch.begin(camera)
        renderableInstances.clear()

        for (footprint in activeFootprints) {
            renderableInstances.add(footprint.instance)
        }
        modelBatch.render(renderableInstances, environment)
        modelBatch.end()
    }

    fun dispose() {
        if (::footprintModel.isInitialized) footprintModel.dispose()
        if (::footprintTexture.isInitialized) footprintTexture.dispose()
        if (::modelBatch.isInitialized) modelBatch.dispose()
        if (::shaderProvider.isInitialized) shaderProvider.dispose()
    }
}
