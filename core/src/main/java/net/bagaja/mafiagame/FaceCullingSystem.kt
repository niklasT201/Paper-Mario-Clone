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
     * Recalculates visible faces for a single block by querying the ChunkManager,
     * correctly accounting for the block's rotation.
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
                val oppositeWorldDirection = worldDirection.cpy().scl(-1f)
                val neighborTouchingFace = getFaceFromVector(oppositeWorldDirection, neighbor.rotationY)
                if (!neighbor.isFaceSolid(neighborTouchingFace)) {
                    block.visibleFaces.add(localFace)
                }
            }
        }
    }

    /**
     * Efficiently recalculates visible faces for a single block using a pre-built map for lookups.
     * This function now checks neighbor height to prevent incorrect culling.
     */
    fun recalculateAllFaces(blocks: Array<GameBlock>) {
        // If the block is not a full block, DO NOTHING
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
                // 3. A neighbor exists. We need to check if its touching face is solid.
                val oppositeWorldDirection = worldDirection.cpy().scl(-1f)
                val neighborTouchingFace = getFaceFromVector(oppositeWorldDirection, neighbor.rotationY)

                // 4. Ask the neighbor if its face at that world orientation is solid.
                if (!neighbor.isFaceSolid(neighborTouchingFace)) {
                    block.visibleFaces.add(localFace)
                }
            }
        }
    }

    private fun getFaceFromVector(worldDirection: Vector3, blockRotationY: Float): BlockFace {
        // Normalize to be safe, though it should be already.
        val localDirection = worldDirection.cpy().rotate(Vector3.Y, -blockRotationY)

        // Check dominant axis to determine the face
        val dir = localDirection.nor()
        if (abs(dir.x) > abs(dir.y) && abs(dir.x) > abs(dir.z)) {
            return if (dir.x > 0) BlockFace.RIGHT else BlockFace.LEFT
        } else if (abs(dir.y) > abs(dir.z)) {
            return if (dir.y > 0) BlockFace.TOP else BlockFace.BOTTOM
        } else {
            return if (dir.z > 0) BlockFace.FRONT else BlockFace.BACK
        }
    }

    /**
     * Performs optimized batch face culling on a whole collection of blocks.
     */
    private fun getDirectionForFace(face: BlockFace): Vector3 {
        return when (face) {
            BlockFace.TOP    -> Vector3(0f, 1f, 0f)
            BlockFace.BOTTOM -> Vector3(0f, -1f, 0f)
            BlockFace.FRONT  -> Vector3(0f, 0f, 1f)
            BlockFace.BACK   -> Vector3(0f, 0f, -1f)
            BlockFace.RIGHT  -> Vector3(1f, 0f, 0f)
            BlockFace.LEFT   -> Vector3(-1f, 0f, 0f)
        }
    }
}
