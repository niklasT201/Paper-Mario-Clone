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
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import kotlin.math.sin

class ObjectiveArrowSystem(private val game: MafiaGame) : Disposable {

    private lateinit var modelBatch: ModelBatch
    private val modelBuilder = ModelBuilder()

    private var arrowModel: Model? = null
    private var arrowInstance: ModelInstance? = null
    private var isVisible = false

    private var targetPosition = Vector3()

    private var bobbingTimer = 0f
    private val BOBBING_SPEED = 3f
    private val BOBBING_HEIGHT = 0.5f

    companion object {
        private val ARROW_COLOR = Color(0.2f, 0.9f, 1f, 0.85f)
        private const val ARROW_SIZE = 2.5f
        private const val ARROW_HEIGHT_OFFSET = 3f
    }

    fun initialize() {
        modelBatch = ModelBatch()

        val material = Material(
            ColorAttribute.createDiffuse(ARROW_COLOR),
            ColorAttribute.createEmissive(ARROW_COLOR),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        )

        // 1. Create the cone model as before (it defaults to pointing UP along the Y-axis)
        arrowModel = modelBuilder.createCone(
            ARROW_SIZE, ARROW_SIZE * 1.5f, ARROW_SIZE,
            12,
            material,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        )

        // 2. NEW: Apply a one-time, permanent rotation to the model's geometry.
        // This makes the cone's "pointy end" face FORWARD along the Z-axis.
        arrowModel?.meshes?.forEach { mesh ->
            mesh.transform(Matrix4().setToRotation(Vector3.X, -90f))
        }

        arrowInstance = ModelInstance(arrowModel)
    }

    fun show(position: Vector3) {
        targetPosition.set(position)
        isVisible = true
        println("ObjectiveArrowSystem: Showing arrow pointing to $position")
    }

    fun hide() {
        isVisible = false
        println("ObjectiveArrowSystem: Hiding arrow.")
    }

    fun update() {
        val car = game.playerSystem.drivingCar
        if (!isVisible || car == null) {
            return
        }

        bobbingTimer += Gdx.graphics.deltaTime
        val bobOffset = sin(bobbingTimer * BOBBING_SPEED) * BOBBING_HEIGHT
        val arrowPosition = car.position.cpy().add(0f, car.carType.height + ARROW_HEIGHT_OFFSET + bobOffset, 0f)

        val direction = targetPosition.cpy().sub(car.position)
        direction.y = 0f

        if (direction.len2() < 0.01f) {
            // At the target, don't rotate.
        } else {
            direction.nor()
        }

        arrowInstance?.transform?.setToRotation(direction, Vector3.Y)
        arrowInstance?.transform?.setTranslation(arrowPosition)
    }


    fun render(camera: Camera, environment: Environment) {
        val car = game.playerSystem.drivingCar
        if (!isVisible || arrowInstance == null || car == null) return

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDepthMask(false)

        modelBatch.begin(camera)
        modelBatch.render(arrowInstance!!, environment)
        modelBatch.end()

        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    override fun dispose() {
        modelBatch.dispose()
        arrowModel?.dispose()
    }
}
