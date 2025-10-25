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

/**
 * Represents a single visual bullet trail in the world.
 */
data class BulletTrail(
    val modelInstance: ModelInstance,
    var lifetime: Float,
    val initialLifetime: Float,
    private val material: Material,
    private val colorAttribute: ColorAttribute
) {
    fun update(deltaTime: Float) {
        lifetime -= deltaTime

        // Calculate fade progress and update the material's opacity
        if (initialLifetime > 0) {
            val progress = (lifetime / initialLifetime).coerceIn(0f, 1f)
            // Use a pow2 interpolation to make the fade-out start slow and then accelerate
            val opacity = progress * progress
            (material.get(BlendingAttribute.Type) as BlendingAttribute).opacity = opacity
            colorAttribute.color.a = opacity
        }
    }

    fun isExpired(): Boolean = lifetime <= 0
}

/**
 * Manages the rendering and lifecycle of bullet trails.
 */
class BulletTrailSystem : Disposable {
    private lateinit var modelBatch: ModelBatch
    private val trails = Array<BulletTrail>()

    private lateinit var trailModel: Model
    private val TRAIL_COLOR = Color.valueOf("#FFF5B2")

    var isEnabled = false
        private set // Allow external reading, but only internal setting via toggle()

    fun toggle() {
        isEnabled = !isEnabled
    }

    fun initialize() {
        modelBatch = ModelBatch()

        // Create a single, reusable line model programmatically
        val modelBuilder = ModelBuilder()
        val material = Material(
            ColorAttribute.createEmissive(TRAIL_COLOR), // Emissive makes it glow and ignore lighting
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        )
        val attributes = (VertexAttributes.Usage.Position).toLong()

        modelBuilder.begin()
        val partBuilder = modelBuilder.part("line", GL20.GL_LINES, attributes, material)
        // A simple line of length 1, which we will scale
        partBuilder.line(Vector3.Zero, Vector3(0f, 0f, 1f))
        trailModel = modelBuilder.end()
    }

    /**
     * Creates a new visual trail.
     * @param start The position where the trail begins (behind the bullet).
     * @param end The current position of the bullet.
     */
    fun addTrail(start: Vector3, end: Vector3) {
        if (!isEnabled) return

        // Create a unique material instance for this trail so its fade is independent
        val colorAttribute = ColorAttribute.createEmissive(TRAIL_COLOR)
        val material = Material(
            colorAttribute,
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        )
        val instance = ModelInstance(trailModel)
        instance.materials.set(0, material)

        val lifetime = 0.08f // Very fast fade
        val trail = BulletTrail(instance, lifetime, lifetime, material, colorAttribute)

        // Stretch and orient the line model to connect the start and end points
        val direction = end.cpy().sub(start)
        val length = direction.len()
        direction.nor()

        trail.modelInstance.transform.setToTranslation(start)
        trail.modelInstance.transform.rotateTowardDirection(direction, Vector3.Y)
        trail.modelInstance.transform.scale(1f, 1f, length)

        trails.add(trail)
    }

    fun update(deltaTime: Float) {
        val iterator = trails.iterator()
        while (iterator.hasNext()) {
            val trail = iterator.next()
            trail.update(deltaTime)
            if (trail.isExpired()) {
                iterator.remove()
            }
        }
    }

    fun render(camera: Camera) {
        if (!isEnabled || trails.isEmpty) return

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST) // Draw on top of everything
        modelBatch.begin(camera)
        modelBatch.render(trails.map { it.modelInstance })
        modelBatch.end()
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST) // Re-enable for the rest of the scene
    }

    override fun dispose() {
        modelBatch.dispose()
        trailModel.dispose()
    }
}
