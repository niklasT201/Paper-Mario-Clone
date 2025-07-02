package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import kotlin.math.abs

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
     * This function now checks neighbor height to prevent incorrect culling.
     */
    private fun recalculateVisibleFaces(block: GameBlock, blockMap: Map<String, GameBlock>) {
        // If the block is not a full block, DO NOTHING
        if (block.shape != BlockShape.FULL_BLOCK) {
            return
        }

        block.visibleFaces.clear()

        // This map represents the block's LOCAL faces and their UN-ROTATED direction vectors.
        val localFaces = mapOf(
            BlockFace.TOP    to Vector3(0f, 1f, 0f),
            BlockFace.BOTTOM to Vector3(0f, -1f, 0f),
            BlockFace.FRONT  to Vector3(0f, 0f, 1f),
            BlockFace.BACK   to Vector3(0f, 0f, -1f),
            BlockFace.RIGHT  to Vector3(1f, 0f, 0f),
            BlockFace.LEFT   to Vector3(-1f, 0f, 0f)
        )

        for ((localFace, direction) in localFaces) {
            // 1. Determine the WORLD direction this local face is currently pointing.
            val worldDirection = direction.cpy().rotate(Vector3.Y, block.rotationY)

            // 2. Find the position of the neighbor in that world direction.
            val neighborOffset = worldDirection.scl(blockSize)
            val neighborPos = block.position.cpy().add(neighborOffset)
            val neighborKey = "${neighborPos.x.toInt()}_${neighborPos.y.toInt()}_${neighborPos.z.toInt()}"

            // Get the actual neighbor block
            val neighbor = blockMap[neighborKey]

            if (neighbor == null) {
                // If there's no neighbor, this local face is visible.
                block.visibleFaces.add(localFace)
            } else {
                // 3. A neighbor exists. We need to check if its touching face is solid.
                val oppositeWorldDirection = worldDirection.nor().scl(-1f)
                val neighborWorldFace = getFaceFromVector(oppositeWorldDirection)

                // 4. Ask the neighbor if its face at that world orientation is solid.
                if (!neighbor.isFaceSolid(neighborWorldFace)) {
                    block.visibleFaces.add(localFace)
                }
            }
        }
    }

    private fun getFaceFromVector(direction: Vector3): BlockFace {
        // Normalize to be safe, though it should be already.
        val dir = direction.cpy().nor()

        // Check dominant axis to determine the face
        if (abs(dir.x) > abs(dir.y) && abs(dir.x) > abs(dir.z)) {
            return if (dir.x > 0) BlockFace.RIGHT else BlockFace.LEFT
        } else if (abs(dir.y) > abs(dir.z)) {
            return if (dir.y > 0) BlockFace.TOP else BlockFace.BOTTOM
        } else {
            return if (dir.z > 0) BlockFace.FRONT else BlockFace.BACK
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
