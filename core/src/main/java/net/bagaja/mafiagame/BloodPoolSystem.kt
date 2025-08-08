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
import kotlin.random.Random

// Data class to hold the state of a single blood pool
data class BloodPool(
    val id: String = UUID.randomUUID().toString(),
    val instance: ModelInstance,
    val position: Vector3,
    var currentScale: Float = 0.1f, // Start very small
    val maxScale: Float,            // The random maximum size
    val growthRate: Float           // How fast it grows
)

class BloodPoolSystem {
    private val textures = Array<Texture>()
    private val models = Array<Model>()
    private lateinit var billboardModelBatch: ModelBatch
    private val renderableInstances = Array<ModelInstance>()
    private lateinit var billboardShaderProvider: BillboardShaderProvider

    fun initialize() {
        // MODIFIED: Initialize the shader and batch for proper rendering
        billboardShaderProvider = BillboardShaderProvider().apply {
            setBillboardLightingStrength(0.8f) // Make it affected by light
            setMinLightLevel(0.3f)             // Ensure it's not completely black in shadows
        }
        billboardModelBatch = ModelBatch(billboardShaderProvider)

        val modelBuilder = ModelBuilder()

        // Load blood pool textures
        val texturePaths = arrayOf(
            "textures/particles/blood_pool/blood_pool_one.png",
            "textures/particles/blood_pool/blood_pool_two.png"
        )

        for (path in texturePaths) {
            try {
                val texture = Texture(Gdx.files.internal(path), true).apply {
                    setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
                }
                textures.add(texture)

                val material = Material(
                    TextureAttribute.createDiffuse(texture),
                    BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                    IntAttribute.createCullFace(GL20.GL_NONE)
                )

                // Create a flat, horizontal plane model (1x1 unit)
                val model = modelBuilder.createRect(
                    -0.5f, 0f, 0.5f, -0.5f, 0f, -0.5f,
                    0.5f, 0f, -0.5f, 0.5f, 0f, 0.5f,
                    0f, 1f, 0f,
                    material,
                    (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
                )
                models.add(model)
            } catch (e: Exception) {
                println("ERROR: Could not load blood pool texture at $path: ${e.message}")
            }
        }
    }

    fun addPool(deathPosition: Vector3, sceneManager: SceneManager) {
        if (models.isEmpty) return // Can't create a pool if no models loaded

        // Find the ground level at the death position to ensure the pool is flat
        val groundY = sceneManager.findHighestSupportY(deathPosition.x, deathPosition.z, deathPosition.y, 0.1f, 4f)
        val spawnPosition = Vector3(deathPosition.x, groundY + 0.05f, deathPosition.z) // Place slightly above ground

        // Pick a random model (texture)
        val modelToUse = models.random()
        val instance = ModelInstance(modelToUse)
        instance.userData = "player"

        // Randomize the maximum size and growth rate for variety
        val maxScale = Random.nextFloat() * 4f + 3f // Random max size between 3 and 7 units
        val growthRate = Random.nextFloat() * 0.4f + 0.2f // Random growth speed

        val newPool = BloodPool(
            instance = instance,
            position = spawnPosition,
            maxScale = maxScale,
            growthRate = growthRate
        )
        sceneManager.activeBloodPools.add(newPool)
    }

    fun update(deltaTime: Float, activePools: Array<BloodPool>) {
        for (pool in activePools) {
            // Grow the pool over time until it reaches its max size
            if (pool.currentScale < pool.maxScale) {
                pool.currentScale += pool.growthRate * deltaTime
                if (pool.currentScale > pool.maxScale) {
                    pool.currentScale = pool.maxScale
                }
            }
            // Update the visual transform of the model instance
            pool.instance.transform.setToTranslation(pool.position)
            pool.instance.transform.scale(pool.currentScale, 1f, pool.currentScale)
        }
    }

    fun render(camera: Camera, environment: Environment, activePools: Array<BloodPool>) {
        if (activePools.isEmpty) return

        billboardShaderProvider.setEnvironment(environment)
        billboardModelBatch.begin(camera)
        renderableInstances.clear()

        for (pool in activePools) {
            renderableInstances.add(pool.instance)
        }
        billboardModelBatch.render(renderableInstances, environment)
        billboardModelBatch.end()
    }

    fun dispose() {
        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()
        textures.forEach { it.dispose() }
        models.forEach { it.dispose() }
    }
}
