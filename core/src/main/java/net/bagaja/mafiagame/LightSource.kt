package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox

enum class FlickerMode {
    NONE,           // Normal, steady light
    LOOP,           // On/off indefinitely
    TIMED_FLICKER_OFF // Flickers for a duration, then turns off permanently
}


/**
 * Represents a customizable light source in the game world
 */
data class LightSource(
    var id: Int,
    val position: Vector3,
    var intensity: Float = DEFAULT_INTENSITY,
    var range: Float = DEFAULT_RANGE,
    var color: Color = Color(DEFAULT_COLOR_R, DEFAULT_COLOR_G, DEFAULT_COLOR_B, 1f),
    var isEnabled: Boolean = true,
    var rotationX: Float = 0f,
    var rotationY: Float = 0f,
    var rotationZ: Float = 0f,
    val flickerMode: FlickerMode = FlickerMode.NONE,
    val loopOnDuration: Float = 0.1f,
    val loopOffDuration: Float = 0.1f,
    val timedFlickerLifetime: Float = 10.0f
) {
    var pointLight: RangePointLight? = null
    private var modelInstance: ModelInstance? = null
    private var debugModelInstance: ModelInstance? = null

    @Transient var flickerTimer: Float = 0f
    @Transient var lifetime: Float = 0f
    @Transient var isMarkedForRemoval: Boolean = false
    @Transient val baseIntensity: Float = intensity // Store original intensity

    companion object {
        // Default light properties
        const val DEFAULT_INTENSITY = 50f
        const val DEFAULT_RANGE = 50f
        const val DEFAULT_COLOR_R = 1f
        const val DEFAULT_COLOR_G = 0.9f
        const val DEFAULT_COLOR_B = 0.7f

        // Light property limits
        const val MIN_INTENSITY = 0f
        const val MAX_INTENSITY = 200f
        const val MIN_RANGE = 5f
        const val MAX_RANGE = 150f

        // Size for rendering
        const val LIGHT_SIZE = 2f
    }

    /**
     * Creates the actual PointLight for rendering
     */
    fun createPointLight(): RangePointLight {
        // Now creates a RangePointLight
        val light = RangePointLight()
        updatePointLight(light)
        pointLight = light
        return light
    }

    fun updateTransform() {
        val transform = Matrix4()
        transform.setToTranslation(position)
        transform.rotate(Vector3.X, rotationX)
        transform.rotate(Vector3.Y, rotationY)
        transform.rotate(Vector3.Z, rotationZ)

        modelInstance?.transform?.set(transform)
        debugModelInstance?.transform?.set(transform)
    }

    /**
     * Updates the PointLight with current properties
     */
    fun updatePointLight(light: RangePointLight? = pointLight) {
        val calculatedIntensity = (range * range * 0.2f)
        val finalIntensity = calculatedIntensity * (this.intensity / 50f)

        light?.set(
            // Use intensity > 0 check along with isEnabled
            if (isEnabled && this.intensity > 0) Color(color.r, color.g, color.b, 1f) else Color.BLACK,
            position.x,
            position.y + LIGHT_SIZE / 2f,
            position.z,
            if (isEnabled && this.intensity > 0) finalIntensity else 0f
        )
        light?.range = this.range
    }

    /**
     * Gets bounding box for selection/collision
     */
    fun getBoundingBox(): BoundingBox {
        val bounds = BoundingBox()
        val halfSize = LIGHT_SIZE / 2f
        bounds.set(
            Vector3(position.x - halfSize, position.y, position.z - halfSize),
            Vector3(position.x + halfSize, position.y + LIGHT_SIZE, position.z + halfSize)
        )
        return bounds
    }

    /**
     * Creates invisible model instance for the light source
     */
    fun createModelInstance(modelBuilder: ModelBuilder): ModelInstance {
        if (modelInstance == null) {
            val material = Material(
                ColorAttribute.createDiffuse(0f, 0f, 0f, 0f),
                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.0f)
            )

            val model = modelBuilder.createBox(
                0.1f, 0.1f, 0.1f,
                material,
                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
            )

            modelInstance = ModelInstance(model)
            modelInstance!!.transform.setToTranslation(position)
        }
        return modelInstance!!
    }

    /**
     * Creates debug visualization model instance
     */
    fun createDebugModelInstance(modelBuilder: ModelBuilder): ModelInstance {
        if (debugModelInstance == null) {
            // Color based on light state
            val debugColor = if (isEnabled) {
                Color(color.r, color.g, color.b, 0.7f)
            } else {
                Color(0.5f, 0.5f, 0.5f, 0.5f) // Gray when disabled
            }

            val material = Material(
                ColorAttribute.createDiffuse(debugColor),
                ColorAttribute.createEmissive(debugColor.r * 0.3f, debugColor.g * 0.3f, debugColor.b * 0.3f, 1f),
                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, debugColor.a)
            )

            val model = modelBuilder.createSphere(
                LIGHT_SIZE, LIGHT_SIZE, LIGHT_SIZE,
                12, 12,
                material,
                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
            )

            debugModelInstance = ModelInstance(model)
            debugModelInstance!!.transform.setToTranslation(position)
        }
        return debugModelInstance!!
    }

    /**
     * Disposes of resources
     */
    fun dispose() {
        modelInstance?.model?.dispose()
        debugModelInstance?.model?.dispose()
    }

    override fun toString(): String {
        return "Light #$id (${if (isEnabled) "ON" else "OFF"}) - Intensity: ${intensity.toInt()}, Range: ${range.toInt()}"
    }
}
