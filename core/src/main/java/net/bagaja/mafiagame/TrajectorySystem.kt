package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Disposable

class TrajectorySystem : Disposable {

    private lateinit var modelBatch: ModelBatch
    private val modelBuilder = ModelBuilder()
    private var arcModel: Model? = null
    private var arcInstance: ModelInstance? = null
    private var isVisible = false
    private var isEnabled = true

    // Landing Indicator Members
    private lateinit var billboardModelBatch: ModelBatch // For the indicator (billboard shader)
    private lateinit var billboardShaderProvider: BillboardShaderProvider
    private var landingIndicatorModel: Model? = null
    private var landingIndicatorInstance: ModelInstance? = null
    private var isLandingIndicatorVisible = false
    private val landingPosition = Vector3()
    private val LANDING_INDICATOR_SIZE = 3.5f
    private val GROUND_OFFSET = 0.08f // To prevent Z-fighting with the ground

    // Simulation parameters
    companion object {
        private const val GRAVITY = -35f       // Must match the gravity in PlayerSystem's ThrowableEntity
        private const val TIME_STEP = 0.03f    // The time between each point on the arc
        private const val MAX_STEPS = 80       // The maximum number of points to calculate
    }

    fun initialize() {
        modelBatch = ModelBatch()

        // Initialize Landing Indicator
        billboardShaderProvider = BillboardShaderProvider().apply {
            setBillboardLightingStrength(0.8f) // Make it affected by world light
            setMinLightLevel(0.4f)             // Not completely black in shadows
        }
        billboardModelBatch = ModelBatch(billboardShaderProvider)

        try {
            val texture = Texture(Gdx.files.internal("gui/highlight_circle.png"))
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            val material = Material(
                TextureAttribute.createDiffuse(texture),
                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                IntAttribute.createCullFace(GL20.GL_NONE)
            )

            // Create a horizontal plane model for the indicator, so it lays flat on the ground
            landingIndicatorModel = modelBuilder.createRect(
                -LANDING_INDICATOR_SIZE / 2f, 0f,  LANDING_INDICATOR_SIZE / 2f,
                -LANDING_INDICATOR_SIZE / 2f, 0f, -LANDING_INDICATOR_SIZE / 2f,
                LANDING_INDICATOR_SIZE / 2f, 0f, -LANDING_INDICATOR_SIZE / 2f,
                LANDING_INDICATOR_SIZE / 2f, 0f,  LANDING_INDICATOR_SIZE / 2f,
                0f, 1f, 0f, // Normal pointing straight up
                material,
                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
            )
            landingIndicatorInstance = ModelInstance(landingIndicatorModel)
            landingIndicatorInstance?.userData = "effect" // Use a generic userData for lighting
        } catch (e: Exception) {
            println("ERROR: Could not load landing indicator texture 'gui/highlight_circle.png': ${e.message}")
        }
    }

    fun toggle() {
        isEnabled = !isEnabled
    }

    fun isEnabled(): Boolean = isEnabled

    /**
     * The main update function. It decides whether to show the arc and calculates its path.
     */
    fun update(playerSystem: PlayerSystem, sceneManager: SceneManager) {
        if (playerSystem.isCutsceneControlled) {
            isVisible = false
            isLandingIndicatorVisible = false
            return
        }

        // Condition to show the arc: Player is charging a throwable weapon
        if (isEnabled && playerSystem.isChargingThrow()) {
            calculateAndBuildArc(playerSystem, sceneManager)
            isVisible = true
        } else {
            isVisible = false
            isLandingIndicatorVisible = false
        }
    }

    private fun calculateAndBuildArc(playerSystem: PlayerSystem, sceneManager: SceneManager) {
        // Dispose the previous frame's model to avoid memory leaks
        arcModel?.dispose()
        isLandingIndicatorVisible = false

        val points = mutableListOf<Vector3>()
        val startPosition = playerSystem.getThrowableSpawnPosition()
        val startVelocity = playerSystem.getThrowableInitialVelocity()

        points.add(startPosition)
        val currentPosition = startPosition.cpy()
        val currentVelocity = startVelocity.cpy()

        val collisionRay = Ray()

        // Simulate the trajectory step-by-step
        for (i in 0 until MAX_STEPS) {
            val previousPosition = currentPosition.cpy()

            // Update velocity with gravity
            currentVelocity.y += GRAVITY * TIME_STEP
            // Update position
            currentPosition.mulAdd(currentVelocity, TIME_STEP)

            // Perform a raycast from the previous point to the new one to check for collisions
            collisionRay.set(previousPosition, currentPosition.cpy().sub(previousPosition).nor())
            val segmentLength = previousPosition.dst(currentPosition)

            val collisionResult = sceneManager.checkCollisionForRay(collisionRay, segmentLength)

            if (collisionResult != null) {
                // Collision detected! Add the hit point and stop the simulation.
                points.add(collisionResult.hitPoint)
                isLandingIndicatorVisible = true
                landingPosition.set(collisionResult.hitPoint).add(0f, GROUND_OFFSET, 0f) // Set its position with an offset
                break // Stop simulating the arc
            } else {
                // No collision, add the new point to our list.
                points.add(currentPosition.cpy())
            }
        }

        // Now, build a new 3D model from the calculated points
        if (points.size > 1) {
            modelBuilder.begin()
            val material = Material(ColorAttribute.createDiffuse(Color.WHITE))
            val partBuilder = modelBuilder.part("arc", GL20.GL_LINES, VertexAttributes.Usage.Position.toLong(), material)
            partBuilder.setColor(Color.WHITE)

            for (i in 0 until points.size - 1) {
                partBuilder.line(points[i], points[i+1])
            }
            arcModel = modelBuilder.end()
            arcInstance = ModelInstance(arcModel)
        }
    }

    fun render(camera: Camera, environment: Environment) {
        if (!isVisible || (arcInstance == null && !isLandingIndicatorVisible)) return // Early exit if nothing to render
        // Enable blending for a slightly transparent look if desired
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        // Render the arc line
        if (arcInstance != null) {
            modelBatch.begin(camera)
            modelBatch.render(arcInstance)
            modelBatch.end()
        }

        // Render landing indicator
        if (isLandingIndicatorVisible && landingIndicatorInstance != null) {
            // The billboard shader needs the environment to know about lights
            billboardShaderProvider.setEnvironment(environment)
            billboardModelBatch.begin(camera)
            landingIndicatorInstance!!.transform.setToTranslation(landingPosition)
            billboardModelBatch.render(landingIndicatorInstance, environment)
            billboardModelBatch.end()
        }

        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    override fun dispose() {
        modelBatch.dispose()
        arcModel?.dispose()
        landingIndicatorModel?.dispose()
        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()
    }
}
