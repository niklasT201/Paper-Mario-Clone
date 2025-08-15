package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
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

    // Simulation parameters
    companion object {
        private const val GRAVITY = -35f       // Must match the gravity in PlayerSystem's ThrowableEntity
        private const val TIME_STEP = 0.03f    // The time between each point on the arc
        private const val MAX_STEPS = 80       // The maximum number of points to calculate
    }

    fun initialize() {
        modelBatch = ModelBatch()
    }

    /**
     * The main update function. It decides whether to show the arc and calculates its path.
     */
    fun update(playerSystem: PlayerSystem, sceneManager: SceneManager) {
        // Condition to show the arc: Player is charging a throwable weapon
        if (playerSystem.isChargingThrow()) {
            calculateAndBuildArc(playerSystem, sceneManager)
            isVisible = true
        } else {
            isVisible = false
        }
    }

    private fun calculateAndBuildArc(playerSystem: PlayerSystem, sceneManager: SceneManager) {
        // Dispose the previous frame's model to avoid memory leaks
        arcModel?.dispose()

        val points = mutableListOf<Vector3>()
        val startPosition = playerSystem.getThrowableSpawnPosition()
        val startVelocity = playerSystem.getThrowableInitialVelocity()

        points.add(startPosition)
        var currentPosition = startPosition.cpy()
        var currentVelocity = startVelocity.cpy()

        val collisionRay = Ray()
        val hitPoint = Vector3()

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
                break
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

    fun render(camera: Camera) {
        if (isVisible && arcInstance != null) {
            // Enable blending for a slightly transparent look if desired
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

            modelBatch.begin(camera)
            modelBatch.render(arcInstance)
            modelBatch.end()

            Gdx.gl.glDisable(GL20.GL_BLEND)
        }
    }

    override fun dispose() {
        modelBatch.dispose()
        arcModel?.dispose()
    }
}
