package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable

// Data class to hold a decal and its associated resources
data class GameDecal(
    val modelInstance: ModelInstance,
    val model: Model, // We need to hold onto the model to dispose of it
    var lifetime: Float,
    val initialLifetime: Float,
    private val blendingAttribute: BlendingAttribute
) : Disposable {
    fun update(deltaTime: Float): Boolean {
        lifetime -= deltaTime
        if (lifetime <= 0) return true // Should be removed

        // Fade out
        val fadeTime = initialLifetime * 0.5f
        if (lifetime < fadeTime) {
            blendingAttribute.opacity = (lifetime / fadeTime).coerceIn(0f, 1f)
        }
        return false
    }

    override fun dispose() {
        model.dispose()
    }
}

class DecalSystem(private val sceneManager: SceneManager) : Disposable {
    private val activeDecals = Array<GameDecal>()
    private lateinit var modelBatch: ModelBatch
    private lateinit var billboardShaderProvider: BillboardShaderProvider

    companion object {
        private const val DECAL_Y_OFFSET = 0.05f // A small value to prevent Z-fighting
    }

    fun initialize() {
        billboardShaderProvider = BillboardShaderProvider().apply {
            setBillboardLightingStrength(0.8f) // Decals should be strongly affected by light
            setMinLightLevel(0.3f)             // Don't let them be pure black in shadows
        }
        modelBatch = ModelBatch(billboardShaderProvider)
    }

    fun update(deltaTime: Float) {
        val iterator = activeDecals.iterator()
        while (iterator.hasNext()) {
            val decal = iterator.next()
            if (decal.update(deltaTime)) {
                decal.dispose()
                iterator.remove()
            }
        }
    }

    fun render(camera: Camera, environment: Environment) {
        if (activeDecals.isEmpty) return
        if (!::modelBatch.isInitialized) return // Safety check

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDepthMask(false)

        // MODIFIED: Set the environment for the shader
        billboardShaderProvider.setEnvironment(environment)
        modelBatch.begin(camera)
        activeDecals.forEach { modelBatch.render(it.modelInstance, environment) }
        modelBatch.end()

        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    /**
     * This is the main function to spawn a projected decal.
     */
    fun spawnProjectedDecal(
        center: Vector3,
        size: Vector3,
        texture: Texture,
        lifetime: Float
    ) {
        val decalBounds = BoundingBox(center.cpy().sub(size.cpy().scl(0.5f)), center.cpy().add(size.cpy().scl(0.5f)))

        val material = Material(
            TextureAttribute.createDiffuse(texture),
            BlendingAttribute(true, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1f)
        )

        // Generate the custom model for the decal
        val decalModel = generateDecalModel(decalBounds, material) ?: return

        val decalInstance = ModelInstance(decalModel)
        val blendingAttribute = decalInstance.materials.first().get(BlendingAttribute.Type) as BlendingAttribute

        val gameDecal = GameDecal(decalInstance, decalModel, lifetime, lifetime, blendingAttribute)
        activeDecals.add(gameDecal)
    }

    /**
     * The core logic to generate the custom model for the decal.
     */
    private fun generateDecalModel(decalBounds: BoundingBox, material: Material): Model? {
        val modelBuilder = ModelBuilder()
        modelBuilder.begin()

        val attributes = (VertexAttributes.Usage.Position or
            VertexAttributes.Usage.Normal or
            VertexAttributes.Usage.TextureCoordinates).toLong()

        val partBuilder = modelBuilder.part("decal", GL20.GL_TRIANGLES, attributes, material)

        val tempBounds = BoundingBox()
        var verticesAdded = 0

        sceneManager.activeChunkManager.getAllBlocks().forEach { block ->
            if (!block.blockType.hasCollision) return@forEach
            val blockBounds = block.getBoundingBox(sceneManager.game.blockSize, tempBounds)
            if (!decalBounds.intersects(blockBounds)) return@forEach

            if (block.shape == BlockShape.FULL_BLOCK) {
                val halfSize = sceneManager.game.blockSize / 2f
                val topY = block.position.y + halfSize * block.blockType.height + DECAL_Y_OFFSET // Keep small offset

                val v1 = Vector3(block.position.x - halfSize, topY, block.position.z + halfSize)
                val v2 = Vector3(block.position.x + halfSize, topY, block.position.z + halfSize)
                val v3 = Vector3(block.position.x + halfSize, topY, block.position.z - halfSize)
                val v4 = Vector3(block.position.x - halfSize, topY, block.position.z - halfSize)

                val vertices = listOf(v1, v2, v3, v4)
                val clippedVertices = clipQuadToBounds(vertices, decalBounds)

                if (clippedVertices.size >= 3) {
                    for (i in 1 until clippedVertices.size - 1) {
                        val p1 = clippedVertices[0]
                        val p2 = clippedVertices[i]
                        val p3 = clippedVertices[i + 1]

                        val idx1 = partBuilder.vertex(p1, Vector3.Y, null, calculateUV(p1, decalBounds))
                        val idx2 = partBuilder.vertex(p2, Vector3.Y, null, calculateUV(p2, decalBounds))
                        val idx3 = partBuilder.vertex(p3, Vector3.Y, null, calculateUV(p3, decalBounds))

                        // --- THE FIX IS HERE ---
                        // The order is now (idx1, idx2, idx3) for counter-clockwise winding.
                        partBuilder.triangle(idx1, idx2, idx3)
                        // --- END OF FIX ---

                        verticesAdded += 3
                    }
                }
            }
        }

        return if (verticesAdded > 0) modelBuilder.end() else null
    }

    // Helper to calculate UV coordinates based on world position within the decal bounds
    private fun calculateUV(vertex: Vector3, bounds: BoundingBox): com.badlogic.gdx.math.Vector2 {
        val u = (vertex.x - bounds.min.x) / bounds.getWidth()
        val v = 1f - ((vertex.z - bounds.min.z) / bounds.getDepth()) // Invert V for standard texture mapping
        return com.badlogic.gdx.math.Vector2(u, v)
    }

    /**
     * A simplified clipping function for a quad against an AABB.
     */
    private fun clipQuadToBounds(quadVertices: List<Vector3>, bounds: BoundingBox): List<Vector3> {
        var currentVertices = quadVertices.toMutableList()
        currentVertices = clipPolygonAgainstPlane(currentVertices, Vector3.X, bounds.max.x, false)
        currentVertices = clipPolygonAgainstPlane(currentVertices, Vector3.X, bounds.min.x, true)
        currentVertices = clipPolygonAgainstPlane(currentVertices, Vector3.Z, bounds.max.z, false)
        currentVertices = clipPolygonAgainstPlane(currentVertices, Vector3.Z, bounds.min.z, true)
        return currentVertices
    }

    private fun clipPolygonAgainstPlane(vertices: List<Vector3>, axis: Vector3, planeOffset: Float, isMinPlane: Boolean): MutableList<Vector3> {
        val result = mutableListOf<Vector3>()
        if (vertices.isEmpty()) return result

        for (i in vertices.indices) {
            val p1 = vertices[i]
            val p2 = vertices[(i + 1) % vertices.size]

            val p1Dot = p1.dot(axis)
            val p2Dot = p2.dot(axis)

            val p1Inside = if (isMinPlane) p1Dot >= planeOffset - 0.001f else p1Dot <= planeOffset + 0.001f
            val p2Inside = if (isMinPlane) p2Dot >= planeOffset - 0.001f else p2Dot <= planeOffset + 0.001f

            if (p1Inside && p2Inside) {
                if (!result.any { it.epsilonEquals(p2, 0.01f) }) result.add(p2)
            } else if (p1Inside && !p2Inside) {
                result.add(getIntersection(p1, p2, axis, planeOffset))
            } else if (!p1Inside && p2Inside) {
                result.add(getIntersection(p1, p2, axis, planeOffset))
                if (!result.any { it.epsilonEquals(p2, 0.01f) }) result.add(p2)
            }
        }
        return result
    }

    private fun getIntersection(p1: Vector3, p2: Vector3, axis: Vector3, planeOffset: Float): Vector3 {
        val direction = p2.cpy().sub(p1)
        val dot = direction.dot(axis)
        if (kotlin.math.abs(dot) < 0.0001f) return p1.cpy() // Parallel
        val t = (planeOffset - p1.dot(axis)) / dot
        return p1.cpy().mulAdd(direction, t)
    }

    override fun dispose() {
        if (::modelBatch.isInitialized) modelBatch.dispose()
        if (::billboardShaderProvider.isInitialized) billboardShaderProvider.dispose()
        activeDecals.forEach { it.dispose() }
        activeDecals.clear()
    }
}
