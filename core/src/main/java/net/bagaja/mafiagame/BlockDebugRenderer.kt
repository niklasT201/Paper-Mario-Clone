package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable

class BlockDebugRenderer : Disposable {

    private lateinit var modelBatch: ModelBatch
    private val modelBuilder = ModelBuilder()
    private val lineMaterial = Material(ColorAttribute.createDiffuse(Color.YELLOW))
    private val tempBounds = BoundingBox()
    private val v1 = Vector3()
    private val v2 = Vector3()
    private val v3 = Vector3()

    fun initialize() {
        modelBatch = ModelBatch()
    }

    fun render(camera: Camera, environment: Environment, blocks: Array<GameBlock>) {
        if (blocks.isEmpty) return

        var hasGeometry = false
        modelBuilder.begin()
        val part = modelBuilder.part("all_lines", GL20.GL_LINES, VertexAttributes.Usage.Position.toLong(), lineMaterial)
        part.setColor(Color.YELLOW)

        for (block in blocks) {
            if (!block.blockType.hasCollision) continue

            if (block.shape == BlockShape.FULL_BLOCK) {
                addBoundingBoxLinesToPart(part, block.getBoundingBox(4f, tempBounds))
                hasGeometry = true
            } else if (block.modelInstance != null) {
                val added = addMeshWireframeToPart(part, block.modelInstance)
                if (added) {
                    hasGeometry = true
                }
            }
        }

        // Only create and render the model if we actually added lines to it
        if (hasGeometry) {
            val combinedModel = modelBuilder.end()
            modelBatch.begin(camera)
            modelBatch.render(ModelInstance(combinedModel), environment)
            modelBatch.end()
            combinedModel.dispose() // Dispose the temporary model immediately
        }
    }

    private fun addBoundingBoxLinesToPart(part: MeshPartBuilder, bounds: BoundingBox) {
        val c = bounds.getCorner000(Vector3())
        val d = bounds.getDimensions(Vector3())

        part.line(c.x, c.y, c.z, c.x + d.x, c.y, c.z)
        part.line(c.x + d.x, c.y, c.z, c.x + d.x, c.y, c.z + d.z)
        part.line(c.x + d.x, c.y, c.z + d.z, c.x, c.y, c.z + d.z)
        part.line(c.x, c.y, c.z + d.z, c.x, c.y, c.z)
        part.line(c.x, c.y, c.z, c.x, c.y + d.y, c.z)
        part.line(c.x + d.x, c.y, c.z, c.x + d.x, c.y + d.y, c.z)
        part.line(c.x + d.x, c.y, c.z + d.z, c.x + d.x, c.y + d.y, c.z + d.z)
        part.line(c.x, c.y, c.z + d.z, c.x, c.y + d.y, c.z + d.z)
        part.line(c.x, c.y + d.y, c.z, c.x + d.x, c.y + d.y, c.z)
        part.line(c.x + d.x, c.y + d.y, c.z, c.x + d.x, c.y + d.y, c.z + d.z)
        part.line(c.x + d.x, c.y + d.y, c.z + d.z, c.x, c.y + d.y, c.z + d.z)
        part.line(c.x, c.y + d.y, c.z + d.z, c.x, c.y + d.y, c.z)
    }

    private fun addMeshWireframeToPart(part: MeshPartBuilder, instance: ModelInstance): Boolean {
        val mesh = instance.model.meshes.firstOrNull() ?: return false
        if (mesh.numIndices == 0) return false // CRITICAL: This prevents the crash

        val vertices = FloatArray(mesh.numVertices * mesh.vertexAttributes.vertexSize / 4)
        val indices = ShortArray(mesh.numIndices)
        mesh.getVertices(vertices)
        mesh.getIndices(indices)
        val vertexSize = mesh.vertexAttributes.vertexSize / 4

        for (i in indices.indices step 3) {
            val i1 = indices[i] * vertexSize
            val i2 = indices[i + 1] * vertexSize
            val i3 = indices[i + 2] * vertexSize

            v1.set(vertices[i1], vertices[i1 + 1], vertices[i1 + 2]).mul(instance.transform)
            v2.set(vertices[i2], vertices[i2 + 1], vertices[i2 + 2]).mul(instance.transform)
            v3.set(vertices[i3], vertices[i3 + 1], vertices[i3 + 2]).mul(instance.transform)

            part.line(v1, v2)
            part.line(v2, v3)
            part.line(v3, v1)
        }
        return true
    }

    override fun dispose() {
        modelBatch.dispose()
    }
}
