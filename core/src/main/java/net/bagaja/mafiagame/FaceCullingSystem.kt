package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array

class FaceCullingSystem(private val blockSize: Float = 4f) {

    /**
     * Finds a block at a given world position by iterating through the array.
     * Uses an indexed loop to be safe from iterator conflicts.
     */
    fun getBlockAt(position: Vector3, allBlocks: Array<GameBlock>): GameBlock? {
        val halfBlock = blockSize / 2f

        for (i in 0 until allBlocks.size) {
            val block = allBlocks[i]

            // Only consider full blocks for neighbor lookups to avoid issues with custom shapes
            if (block.shape != BlockShape.FULL_BLOCK) continue

            val halfHeight = (block.blockType.height * blockSize) / 2f

            val minX = block.position.x - halfBlock
            val maxX = block.position.x + halfBlock
            val minY = block.position.y - halfHeight
            val maxY = block.position.y + halfHeight
            val minZ = block.position.z - halfBlock
            val maxZ = block.position.z + halfBlock

            if (position.x > minX && position.x < maxX &&
                position.y > minY && position.y < maxY &&
                position.z > minZ && position.z < maxZ) {
                return block
            }
        }
        return null
    }

    /**
     * Efficiently recalculates visible faces for a single block using a pre-built map for lookups.
     */
    /**
     * Efficiently recalculates visible faces for a single block using a pre-built map for lookups.
     * This function now checks neighbor height to prevent incorrect culling.
     */
    fun recalculateVisibleFaces(block: GameBlock, blockMap: Map<String, GameBlock>) {
        // If the block is not a full block, DO NOTHING
        if (block.shape != BlockShape.FULL_BLOCK) {
            return
        }

        block.visibleFaces.clear()

        val neighborOffsets = mapOf(
            BlockFace.TOP to Vector3(0f, blockSize, 0f),
            BlockFace.BOTTOM to Vector3(0f, -blockSize, 0f),
            BlockFace.FRONT to Vector3(0f, 0f, blockSize),
            BlockFace.BACK to Vector3(0f, 0f, -blockSize),
            BlockFace.RIGHT to Vector3(blockSize, 0f, 0f),
            BlockFace.LEFT to Vector3(-blockSize, 0f, 0f)
        )

        for ((face, offset) in neighborOffsets) {
            val rotatedOffset = offset.cpy()
            // Rotate offset by the block's rotation around Y axis
            rotatedOffset.rotate(Vector3.Y, block.rotationY)

            // Use the NEW rotated offset to find the neighbor's position
            val neighborPos = Vector3(block.position).add(rotatedOffset)
            val neighborKey = "${neighborPos.x.toInt()}_${neighborPos.y.toInt()}_${neighborPos.z.toInt()}"

            // Get the actual neighbor block
            val neighbor = blockMap[neighborKey]

            // A face is visible if there is NO neighbor,
            if (neighbor == null || !neighbor.isFaceSolid(face.getOpposite())) {
                block.visibleFaces.add(face)
            }
        }
    }

    fun updateFacesAround(position: Vector3, allBlocks: Array<GameBlock>) {
        val blockMap = allBlocks.associateBy {
            "${it.position.x.toInt()}_${it.position.y.toInt()}_${it.position.z.toInt()}"
        }

        val offsets = arrayOf(
            Vector3(0f, 0f, 0f),      // The block itself
            Vector3(0f, blockSize, 0f),   // Top
            Vector3(0f, -blockSize, 0f),  // Bottom
            Vector3(0f, 0f, blockSize),   // Front
            Vector3(0f, 0f, -blockSize),  // Back
            Vector3(blockSize, 0f, 0f),   // Right
            Vector3(-blockSize, 0f, 0f)   // Left
        )

        for (offset in offsets) {
            val checkPos = Vector3(position).add(offset)
            getBlockAt(checkPos, allBlocks)?.let { blockToUpdate ->
                // Ensure we only ever try to recalculate faces for full blocks
                if (blockToUpdate.shape == BlockShape.FULL_BLOCK) {
                    recalculateVisibleFaces(blockToUpdate, blockMap)
                }
            }
        }
    }

    /**
     * Performs optimized batch face culling on a whole collection of blocks.
     */
    fun recalculateAllFaces(blocks: Array<GameBlock>) {
        if (blocks.isEmpty) return
        println("Performing optimized batch face culling on ${blocks.size} blocks...")

        // 1. Build a lookup map.
        val blockMap = blocks.associateBy {
            "${it.position.x.toInt()}_${it.position.y.toInt()}_${it.position.z.toInt()}"
        }

        // 2. Iterate through all blocks and use the map for neighbor checks.
        for (block in blocks) {
            if (block.shape == BlockShape.FULL_BLOCK) {
                recalculateVisibleFaces(block, blockMap)
            }
        }

        println("Batch culling complete.")
    }
}
