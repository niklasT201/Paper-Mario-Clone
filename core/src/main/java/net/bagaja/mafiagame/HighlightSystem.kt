package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Plane
import com.badlogic.gdx.math.collision.Ray
import kotlin.math.floor
import com.badlogic.gdx.utils.Array

class HighlightSystem(private val blockSize: Float) {
    private var highlightModel: Model? = null
    private var highlightInstance: ModelInstance? = null
    private var highlightMaterial: Material? = null
    private var isHighlightVisible = false
    private var highlightPosition = Vector3()

    fun initialize() {
        val modelBuilder = ModelBuilder()

        // Create a transparent material with blending
        highlightMaterial = Material(
            ColorAttribute.createDiffuse(Color.GREEN),
            com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(
                GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.3f  // Alpha value (0.0 = fully transparent, 1.0 = fully opaque)
            )
        )

        // Create a wireframe box that's slightly larger than regular blocks
        val highlightSize = blockSize + 0.2f
        highlightModel = modelBuilder.createBox(
            highlightSize, highlightSize, highlightSize,
            highlightMaterial!!,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        )
        highlightInstance = ModelInstance(highlightModel!!)
    }

    fun update(
        cameraManager: CameraManager,
        uiManager: UIManager,
        gameBlocks: Array<GameBlock>,
        getBlockAtRayFunction: (Ray) -> GameBlock?
    ) {
        val mouseX = Gdx.input.x.toFloat()
        val mouseY = Gdx.input.y.toFloat()
        val ray = cameraManager.camera.getPickRay(mouseX, mouseY)

        when (uiManager.selectedTool) {
            UIManager.Tool.BLOCK -> updateBlockHighlight(ray, gameBlocks, getBlockAtRayFunction)
            UIManager.Tool.OBJECT -> updateObjectHighlight(ray)
            UIManager.Tool.ITEM -> updateItemHighlight(ray)
            UIManager.Tool.CAR -> updateCarHighlight(ray)
            UIManager.Tool.PLAYER -> updatePlayerHighlight(ray, gameBlocks)
        }
    }

    private fun updateBlockHighlight(ray: Ray, gameBlocks: Array<GameBlock>, getBlockAtRayFunction: (Ray) -> GameBlock?) {
        // Check if we're hovering over an existing block (for removal)
        val hitBlock = getBlockAtRayFunction(ray)

        if (hitBlock != null) {
            // Show transparent red highlight for block removal
            isHighlightVisible = true
            highlightPosition.set(hitBlock.position)
            setHighlightColor(Color(1f, 0f, 0f, 0.3f)) // Red
            updateHighlightTransform()
        } else {
            // Show green highlight for block placement
            val intersection = Vector3()
            val groundPlane = Plane(Vector3.Y, 0f)

            if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
                // Snap to grid
                val gridX = floor(intersection.x / blockSize) * blockSize
                val gridZ = floor(intersection.z / blockSize) * blockSize

                // Check if there's already a block at this position
                val existingBlock = gameBlocks.find { gameBlock ->
                    kotlin.math.abs(gameBlock.position.x - (gridX + blockSize / 2)) < 0.1f &&
                        kotlin.math.abs(gameBlock.position.y - (blockSize / 2)) < 0.1f &&
                        kotlin.math.abs(gameBlock.position.z - (gridZ + blockSize / 2)) < 0.1f
                }

                if (existingBlock == null) {
                    isHighlightVisible = true
                    highlightPosition.set(gridX + blockSize / 2, blockSize / 2, gridZ + blockSize / 2)
                    setHighlightColor(Color(0f, 1f, 0f, 0.3f)) // Green
                    updateHighlightTransform()
                } else {
                    isHighlightVisible = false
                }
            } else {
                isHighlightVisible = false
            }
        }
    }

    private fun updateObjectHighlight(ray: Ray) {
        // Show blue highlight for object placement
        val intersection = Vector3()
        val groundPlane = Plane(Vector3.Y, 0f)

        if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            val gridX = floor(intersection.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize / 2

            isHighlightVisible = true
            highlightPosition.set(gridX, 0.5f, gridZ)
            setHighlightColor(Color(0f, 0f, 1f, 0.3f)) // Blue
            updateHighlightTransform()
        } else {
            isHighlightVisible = false
        }
    }

    private fun updateItemHighlight(ray: Ray) {
        // Show yellow highlight for item placement
        val intersection = Vector3()
        val groundPlane = Plane(Vector3.Y, 0f)

        if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            isHighlightVisible = true
            highlightPosition.set(intersection.x, intersection.y + 1f, intersection.z) // Items float above ground
            setHighlightColor(Color(1f, 1f, 0f, 0.3f)) // Yellow
            updateHighlightTransform()
        } else {
            isHighlightVisible = false
        }
    }

    private fun updateCarHighlight(ray: Ray) {
        // Show purple highlight for car placement
        val intersection = Vector3()
        val groundPlane = Plane(Vector3.Y, 0f)

        if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            val gridX = floor(intersection.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize / 2

            isHighlightVisible = true
            highlightPosition.set(gridX, 0.5f, gridZ)
            setHighlightColor(Color(1f, 0f, 1f, 0.3f)) // Purple
            updateHighlightTransform()
        } else {
            isHighlightVisible = false
        }
    }

    private fun updatePlayerHighlight(ray: Ray, gameBlocks: Array<GameBlock>) {
        // Show green highlight for player placement (same as block placement logic)
        val intersection = Vector3()
        val groundPlane = Plane(Vector3.Y, 0f)

        if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            // Snap to grid
            val gridX = floor(intersection.x / blockSize) * blockSize
            val gridZ = floor(intersection.z / blockSize) * blockSize

            // Check if there's already a block at this position
            val existingBlock = gameBlocks.find { gameBlock ->
                kotlin.math.abs(gameBlock.position.x - (gridX + blockSize / 2)) < 0.1f &&
                    kotlin.math.abs(gameBlock.position.y - (blockSize / 2)) < 0.1f &&
                    kotlin.math.abs(gameBlock.position.z - (gridZ + blockSize / 2)) < 0.1f
            }

            if (existingBlock == null) {
                isHighlightVisible = true
                highlightPosition.set(gridX + blockSize / 2, blockSize / 2, gridZ + blockSize / 2)
                setHighlightColor(Color(0f, 1f, 0f, 0.3f)) // Green
                updateHighlightTransform()
            } else {
                isHighlightVisible = false
            }
        } else {
            isHighlightVisible = false
        }
    }

    private fun setHighlightColor(color: Color) {
        highlightMaterial?.set(ColorAttribute.createDiffuse(color))
    }

    private fun updateHighlightTransform() {
        highlightInstance?.transform?.setTranslation(highlightPosition)
    }

    fun render(modelBatch: ModelBatch, camera: com.badlogic.gdx.graphics.Camera, environment: com.badlogic.gdx.graphics.g3d.Environment) {
        if (isHighlightVisible && highlightInstance != null) {
            // Enable blending for transparency
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

            // Disable depth writing but keep depth testing
            Gdx.gl.glDepthMask(false)

            modelBatch.begin(camera)
            modelBatch.render(highlightInstance!!, environment)
            modelBatch.end()

            // Restore depth writing and disable blending
            Gdx.gl.glDepthMask(true)
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }
    }

    fun dispose() {
        highlightModel?.dispose()
    }
}
