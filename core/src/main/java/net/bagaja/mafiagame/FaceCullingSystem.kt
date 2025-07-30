package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import kotlin.math.abs

class FaceCullingSystem(private val blockSize: Float = 4f) {

    /**
     * Finds a block at a given world position by iterating through the array.
     * Uses an indexed loop to be safe from iterator conflicts.
     */
    fun updateFacesForChunk(chunk: Chunk, chunkManager: ChunkManager) {
        for (block in chunk.blocks.values) {
            // We only need to perform culling checks on standard full blocks.
            if (block.shape == BlockShape.FULL_BLOCK) {
                recalculateVisibleFaces(block, chunkManager)
            }
        }
    }

    /**
     * Recalculates visible faces for all blocks at once.
     * Useful for initial world load or large-scale changes.
     */
    fun recalculateAllFaces(blocks: Array<GameBlock>) {
        if (blocks.isEmpty) return
        val blockMap = blocks.associateBy {
            "${it.position.x.toInt()}_${it.position.y.toInt()}_${it.position.z.toInt()}"
        }

        for (block in blocks) {
            if (block.shape == BlockShape.FULL_BLOCK) {
                recalculateVisibleFacesWithMap(block, blockMap)
            }
        }
    }

    /**
     * The core culling logic for a single block, using the ChunkManager to find neighbors.
     */
    private fun recalculateVisibleFaces(block: GameBlock, chunkManager: ChunkManager) {
        if (block.shape != BlockShape.FULL_BLOCK) return
        block.visibleFaces.clear()

        for (localFace in BlockFace.entries) {
            val baseDirection = getDirectionForFace(localFace)
            val worldDirection = baseDirection.cpy().rotate(Vector3.Y, block.rotationY)
            val neighborPos = Vector3(block.position).mulAdd(worldDirection, blockSize)
            val neighbor = chunkManager.getBlockAtWorld(neighborPos)

            if (neighbor == null || !neighbor.blockType.isVisible) {
                block.visibleFaces.add(localFace)
            } else {
                if (neighbor.shape == BlockShape.FULL_BLOCK) {
                    // Neighbor is a full block, so it completely covers our face
                } else {
                    // Neighbor is a partial block
                    block.visibleFaces.add(localFace)
                }
            }
        }
    }

    /**
     * The core culling logic for a single block, using a pre-built Map for faster neighbor lookups.
     */
    private fun recalculateVisibleFacesWithMap(block: GameBlock, blockMap: Map<String, GameBlock>) {
        if (block.shape != BlockShape.FULL_BLOCK) return
        block.visibleFaces.clear()
        for (localFace in BlockFace.entries) {
            val baseDirection = getDirectionForFace(localFace)
            val worldDirection = baseDirection.cpy().rotate(Vector3.Y, block.rotationY)
            val neighborPos = Vector3(block.position).mulAdd(worldDirection, blockSize)
            val neighborKey = "${neighborPos.x.toInt()}_${neighborPos.y.toInt()}_${neighborPos.z.toInt()}"

            // Get the actual neighbor block
            val neighbor = blockMap[neighborKey]

            if (neighbor == null || !neighbor.blockType.isVisible) {
                // If there's no neighbor, this local face is visible.
                block.visibleFaces.add(localFace)
            } else {
                if (neighbor.shape == BlockShape.FULL_BLOCK) {
                    // Neighbor is a full block, so it completely covers our face
                } else {
                    // Neighbor is a partial block
                    block.visibleFaces.add(localFace)
                }
            }
        }
    }

    /**
     * Helper to get a direction vector for a given LOCAL face.
     */
    private fun getDirectionForFace(face: BlockFace): Vector3 {
        return when (face) {
            BlockFace.TOP -> Vector3(0f, 1f, 0f)
            BlockFace.BOTTOM -> Vector3(0f, -1f, 0f)
            BlockFace.FRONT -> Vector3(0f, 0f, 1f)
            BlockFace.BACK -> Vector3(0f, 0f, -1f)
            BlockFace.RIGHT -> Vector3(1f, 0f, 0f)
            BlockFace.LEFT -> Vector3(-1f, 0f, 0f)
        }
    }
}
