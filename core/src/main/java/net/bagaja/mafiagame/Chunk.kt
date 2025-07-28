package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import kotlin.math.floor

// A simple data class to represent a chunk's position in the world grid
data class ChunkPosition(val x: Int, val y: Int, val z: Int)

class Chunk(val position: ChunkPosition, private val blockSize: Float) {
    companion object {
        const val CHUNK_SIZE = 16 // The dimensions of the chunk in blocks (16x16x16).
    }

    var modelInstance: ModelInstance? = null
    var model: Model? = null
    val boundingBox = BoundingBox()
    val blocks = mutableMapOf<Vector3, GameBlock>()

    init {
        // Pre-calculate the world-space bounding box for this chunk for frustum culling.
        val worldMin = Vector3(
            (position.x * CHUNK_SIZE * blockSize),
            (position.y * CHUNK_SIZE * blockSize),
            (position.z * CHUNK_SIZE * blockSize)
        )
        val worldMax = Vector3(
            worldMin.x + (CHUNK_SIZE * blockSize),
            worldMin.y + (CHUNK_SIZE * blockSize),
            worldMin.z + (CHUNK_SIZE * blockSize)
        )
        boundingBox.set(worldMin, worldMax)
    }

    fun addBlock(block: GameBlock) {
        val localPos = worldToLocal(block.position)
        blocks[localPos] = block
    }

    fun removeBlock(block: GameBlock): GameBlock? {
        val localPos = worldToLocal(block.position)
        return blocks.remove(localPos)
    }

    fun getBlockAtWorld(worldPos: Vector3): GameBlock? {
        val localPos = worldToLocal(worldPos)
        return blocks[localPos]
    }

    /**
     * Converts a block's world position to its local coordinate key within this chunk (0-15).
     */
    fun worldToLocal(worldPos: Vector3): Vector3 {
        return Vector3(
            floor(worldPos.x / blockSize) - (position.x * CHUNK_SIZE),
            floor(worldPos.y / blockSize) - (position.y * CHUNK_SIZE),
            floor(worldPos.z / blockSize) - (position.z * CHUNK_SIZE)
        )
    }

    fun dispose() {
        model?.dispose()
        model = null
        modelInstance = null
    }
}
