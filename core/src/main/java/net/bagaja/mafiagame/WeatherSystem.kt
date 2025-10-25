package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import kotlin.random.Random

/**
 * Manages weather effects, starting with rain.
 */
class WeatherSystem : Disposable {

    // A simple data class to hold the state of a single raindrop
    private data class Raindrop(
        val instance: ModelInstance,
        val position: Vector3 = Vector3(),
        val velocity: Vector3 = Vector3()
    )

    private lateinit var rainModel: Model
    private lateinit var modelBatch: ModelBatch
    private lateinit var camera: Camera
    lateinit var lightingManager: LightingManager // Dependency Injected from MafiaGame

    private val raindrops = Array<Raindrop>()
    private val renderableInstances = Array<ModelInstance>()

    // --- Configuration ---
    private var rainIntensity = 0f // 0.0f (no rain) to 1.0f (heavy rain)
    private val maxRaindrops = 2000 // The maximum number of drops for the heaviest rain
    private val rainAreaSize = Vector3(120f, 60f, 120f) // The box around the player where rain exists
    private val rainColor = Color(0.7f, 0.8f, 1.0f, 0.5f) // Bluish, semi-transparent

    fun initialize(camera: Camera) {
        this.camera = camera
        this.modelBatch = ModelBatch()

        // Create a single, reusable model for all raindrops (a simple vertical line)
        val modelBuilder = ModelBuilder()
        val material = Material(
            ColorAttribute.createDiffuse(rainColor),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        )
        modelBuilder.begin()
        val partBuilder = modelBuilder.part("raindrop", GL20.GL_LINES, (VertexAttributes.Usage.Position).toLong(), material)
        // A short, thin line to represent a rain streak
        partBuilder.line(0f, 0f, 0f, 0f, -0.8f, 0f)
        rainModel = modelBuilder.end()

        // Create a pool of raindrop objects to be reused
        for (i in 0 until maxRaindrops) {
            val drop = Raindrop(ModelInstance(rainModel))
            resetRaindrop(drop, true) // Initialize with a random position
            raindrops.add(drop)
        }
    }

    /**
     * Sets the intensity of the rain.
     * @param intensity A value from 0.0 (off) to 1.0 (heavy).
     */
    fun setRainIntensity(intensity: Float) {
        this.rainIntensity = intensity.coerceIn(0f, 1f)
        // Inform the LightingManager to adjust the sky color
        lightingManager.setRainFactor(this.rainIntensity)
    }

    fun getRainIntensity(): Float = this.rainIntensity

    /**
     * Resets a raindrop to a new random position at the top of the rain area.
     */
    private fun resetRaindrop(drop: Raindrop, initialSpawn: Boolean = false) {
        val rainCenter = camera.position
        val halfSize = rainAreaSize.cpy().scl(0.5f)

        // Give it a random X and Z position within the rain area
        drop.position.x = rainCenter.x + Random.nextFloat() * rainAreaSize.x - halfSize.x
        drop.position.z = rainCenter.z + Random.nextFloat() * rainAreaSize.z - halfSize.z

        // If it's the first time, scatter them vertically. Otherwise, place at the top.
        if (initialSpawn) {
            drop.position.y = rainCenter.y + Random.nextFloat() * rainAreaSize.y - halfSize.y
        } else {
            drop.position.y = rainCenter.y + halfSize.y
        }

        // Give it a downward velocity with some variance
        val baseSpeed = 40f
        val speedVariance = 15f
        drop.velocity.set(0f, -(baseSpeed + Random.nextFloat() * speedVariance), 0f)
    }

    fun update(deltaTime: Float) {
        if (rainIntensity == 0f) return

        val rainCenter = camera.position
        val bottomY = rainCenter.y - rainAreaSize.y / 2f
        val activeDropCount = (maxRaindrops * rainIntensity).toInt()

        // Update only the active raindrops
        for (i in 0 until activeDropCount) {
            val drop = raindrops.get(i)

            // Move the drop
            drop.position.mulAdd(drop.velocity, deltaTime)

            // If the drop has fallen below the rain area, reset it
            if (drop.position.y < bottomY) {
                resetRaindrop(drop)
            }
        }
    }

    fun render(environment: Environment) {
        if (rainIntensity == 0f) return

        val activeDropCount = (maxRaindrops * rainIntensity).toInt()
        if (activeDropCount == 0) return

        renderableInstances.clear()
        for (i in 0 until activeDropCount) {
            val drop = raindrops.get(i)
            // Update the transform of the instance to match the particle's position
            drop.instance.transform.setToTranslation(drop.position)
            renderableInstances.add(drop.instance)
        }

        // --- Render with Transparency ---
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDepthMask(false) // Disable writing to the depth buffer for transparency

        modelBatch.begin(camera)
        modelBatch.render(renderableInstances, environment)
        modelBatch.end()

        // --- Restore GL State ---
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    override fun dispose() {
        rainModel.dispose()
        modelBatch.dispose()
    }
}
