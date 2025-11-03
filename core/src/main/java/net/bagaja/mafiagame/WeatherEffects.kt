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
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import kotlin.math.abs
import kotlin.random.Random

enum class FootprintType {
    BLOODY, WET
}

data class GamePuddle(
    val instance: ModelInstance,
    val position: Vector3,
    val maxScale: Float, // The largest this puddle can get
    var currentScale: Float, // The current size of the puddle
    val bounds: BoundingBox,
    val washAwayComponent: WashAwayComponent = WashAwayComponent()
)

class WaterPuddleSystem : Disposable {
    private val puddleModels = Array<Model>()
    private val puddleTextures = Array<Texture>()
    val activePuddles = Array<GamePuddle>()
    private lateinit var modelBatch: ModelBatch
    private lateinit var shaderProvider: BillboardShaderProvider
    private val renderableInstances = Array<ModelInstance>()

    lateinit var sceneManager: SceneManager

    private var spawnTimer = 0f
    // MODIFIED: Increased the base interval to make new puddles less frequent.
    private val spawnInterval = 5.0f

    fun initialize() {
        shaderProvider = BillboardShaderProvider().apply {
            setBillboardLightingStrength(0.8f)
            setMinLightLevel(0.3f)
        }
        modelBatch = ModelBatch(shaderProvider)
        val modelBuilder = ModelBuilder()

        val texturePaths = arrayOf(
            "textures/particles/rain/puddle.png",
            "textures/particles/rain/puddle_two.png"
        )

        for (path in texturePaths) {
            try {
                val texture = Texture(Gdx.files.internal(path), true).apply {
                    setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
                }
                puddleTextures.add(texture)

                val material = Material(
                    TextureAttribute.createDiffuse(texture),
                    BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                    IntAttribute.createCullFace(GL20.GL_NONE)
                )
                val model = modelBuilder.createRect(
                    -0.5f, 0f, 0.5f, -0.5f, 0f, -0.5f,
                    0.5f, 0f, -0.5f, 0.5f, 0f, 0.5f,
                    0f, 1f, 0f,
                    material,
                    (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
                )
                puddleModels.add(model)
            } catch (e: Exception) {
                println("ERROR: Could not load puddle texture at $path: ${e.message}")
            }
        }
    }

    // MODIFIED: The entire update logic is new to handle growth and shrinking.
    fun update(deltaTime: Float, weatherSystem: WeatherSystem, isInInterior: Boolean) {
        val rainIntensity = weatherSystem.getRainIntensity()
        val isHeavyRain = rainIntensity > 0.7f && !isInInterior

        // --- Handle Spawning & Growth during heavy rain ---
        if (isHeavyRain) {
            // 1. Handle spawning new puddles (less frequently now)
            spawnTimer -= deltaTime
            if (spawnTimer <= 0f) {
                spawnTimer = spawnInterval / rainIntensity // Spawn faster in heavier rain
                if (activePuddles.size < 50) { // Limit total number of puddles
                    spawnPuddle()
                }
            }

            // 2. Handle growth of existing puddles
            for (puddle in activePuddles) {
                if (puddle.currentScale < puddle.maxScale) {
                    val growthRate = 0.2f * rainIntensity // Puddles grow faster in heavier rain
                    puddle.currentScale += growthRate * deltaTime
                    if (puddle.currentScale > puddle.maxScale) {
                        puddle.currentScale = puddle.maxScale
                    }
                    // Update the visual transform with the new scale
                    puddle.instance.transform.setToTranslation(puddle.position)
                    puddle.instance.transform.scale(puddle.currentScale, 1f, puddle.currentScale)
                }
            }
        }

        // --- Handle Shrinking/Evaporation when not raining heavily ---
        val iterator = activePuddles.iterator()
        while (iterator.hasNext()) {
            val puddle = iterator.next()
            if (!isHeavyRain) {
                val shrinkRate = 0.1f // Puddles shrink at a constant rate
                puddle.currentScale -= shrinkRate * deltaTime
                if (puddle.currentScale <= 0f) {
                    iterator.remove() // Remove the puddle if it has completely shrunk
                } else {
                    // Update the visual transform as it shrinks
                    puddle.instance.transform.setToTranslation(puddle.position)
                    puddle.instance.transform.scale(puddle.currentScale, 1f, puddle.currentScale)
                }
            }
        }
    }

    // MODIFIED: Puddles now spawn small and have a max size to grow towards.
    private fun spawnPuddle() {
        val spawnRadius = 50f
        val playerPos = sceneManager.playerSystem.getPosition()

        val randomX = playerPos.x + (Random.nextFloat() * 2f - 1f) * spawnRadius
        val randomZ = playerPos.z + (Random.nextFloat() * 2f - 1f) * spawnRadius

        val groundY = sceneManager.findHighestSupportY(randomX, randomZ, playerPos.y, 0.1f, sceneManager.game.blockSize)

        if (abs(groundY - playerPos.y) > 10f) return

        val position = Vector3(randomX, groundY + 0.04f, randomZ)

        val modelToUse = puddleModels.random()
        val instance = ModelInstance(modelToUse)
        instance.userData = "effect"

        val maxScale = Random.nextFloat() * 3f + 2f // Random max size between 2 and 5 units
        val currentScale = 0.5f // Start small
        val rotation = Random.nextFloat() * 360f

        instance.transform.setToTranslation(position)
        instance.transform.rotate(Vector3.Y, rotation)
        instance.transform.scale(currentScale, 1f, currentScale)

        val bounds = BoundingBox(
            Vector3(position.x - maxScale / 2f, position.y - 0.1f, position.z - maxScale / 2f),
            Vector3(position.x + maxScale / 2f, position.y + 0.1f, position.z + maxScale / 2f)
        )

        val newPuddle = GamePuddle(instance, position, maxScale, currentScale, bounds)
        activePuddles.add(newPuddle)
    }

    // NEW: Helper function to check if a position is inside any active puddle.
    fun isPositionInPuddle(position: Vector3): Boolean {
        for (puddle in activePuddles) {
            if (puddle.bounds.contains(position)) {
                return true
            }
        }
        return false
    }


    fun render(camera: Camera, environment: Environment) {
        // Check if we are in the world scene
        if (activePuddles.isEmpty || sceneManager.currentScene != SceneType.WORLD) {
            return
        }

        shaderProvider.setEnvironment(environment)
        modelBatch.begin(camera)
        renderableInstances.clear()
        activePuddles.forEach { renderableInstances.add(it.instance) }
        modelBatch.render(renderableInstances, environment)
        modelBatch.end()
    }

    override fun dispose() {
        modelBatch.dispose()
        shaderProvider.dispose()
        puddleModels.forEach { it.dispose() }
        puddleTextures.forEach { it.dispose() }
    }
}
