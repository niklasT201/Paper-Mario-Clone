// In: OcclusionSystem.kt (replace the whole file)
package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array

class OcclusionSystem(private val blockSize: Float) {
    // Reusable objects to avoid garbage collection
    private val tempVec1 = Vector3()
    private val tempVec2 = Vector3()
    private val tempVec3 = Vector3()
    private val blockCorners = Array(8) { Vector3() }

    // A set to keep track of which blocks were hidden in the last frame.
    private val hiddenBlocks = mutableSetOf<GameBlock>()

    // Defines the size of the "cutout" window in screen percentage.
    private val cutoutSizePercentage = 0.3f

    // Defines how far away from the player a wall can be to be considered for occlusion.
    private val maxOcclusionDistance = 30f

    fun update(camera: Camera, playerPosition: Vector3, allBlocks: Array<GameBlock>) {
        // --- Step 1: Restore visibility of all blocks hidden in the previous frame. ---
        for (block in hiddenBlocks) {
            block.visibleFaces.addAll(BlockFace.entries)
        }
        hiddenBlocks.clear()

        // --- Step 2: Find all potential candidates for occlusion. ---
        val candidateBlocks = findCandidateBlocks(camera, playerPosition, allBlocks)

        // --- Step 3: NEW - Validate the candidates to only include "walls". ---
        // A block is part of a wall if it has a vertical neighbor that is ALSO a candidate.
        if (candidateBlocks.size < 2) {
            // Not enough candidates to form a wall, so do nothing.
            return
        }

        // For efficient lookup, map candidate block positions to the blocks themselves.
        val candidateMap = candidateBlocks.associateBy { it.position }
        val finalBlocksToHide = mutableSetOf<GameBlock>()

        for (candidate in candidateBlocks) {
            val pos = candidate.position
            val posAbove = tempVec1.set(pos.x, pos.y + blockSize, pos.z)
            val posBelow = tempVec2.set(pos.x, pos.y - blockSize, pos.z)

            // If a candidate has a vertical neighbor (above or below) that is ALSO a candidate,
            // then it's part of a valid wall stack.
            if (candidateMap.containsKey(posAbove) || candidateMap.containsKey(posBelow)) {
                finalBlocksToHide.add(candidate)
            }
        }

        // --- Step 4: Hide the final, validated blocks. ---
        for (blockToHide in finalBlocksToHide) {
            blockToHide.visibleFaces.clear()
            hiddenBlocks.add(blockToHide)
        }
    }

    /**
     * Performs the initial pass to find all blocks that meet the criteria for being occluders,
     * without yet checking if they form a wall.
     */
    private fun findCandidateBlocks(camera: Camera, playerPosition: Vector3, allBlocks: Array<GameBlock>): List<GameBlock> {
        val candidates = mutableListOf<GameBlock>()

        val playerScreenPos = camera.project(tempVec1.set(playerPosition).add(0f, 2f, 0f))
        val cutoutWidth = com.badlogic.gdx.Gdx.graphics.width * cutoutSizePercentage
        val cutoutHeight = com.badlogic.gdx.Gdx.graphics.height * cutoutSizePercentage
        val cutoutX = playerScreenPos.x - cutoutWidth / 2
        val cutoutY = playerScreenPos.y - cutoutHeight / 2

        val cameraRight = tempVec1.set(camera.direction).crs(camera.up).nor()
        cameraRight.y = 0f
        cameraRight.nor()

        for (block in allBlocks) {
            if (!block.blockType.isVisible || !block.blockType.hasCollision || block.shape != BlockShape.FULL_BLOCK) {
                continue
            }

            val distanceToCamera = block.position.dst(camera.position)
            val playerDistanceToCamera = playerPosition.dst(camera.position)
            if (distanceToCamera >= playerDistanceToCamera || block.position.dst(playerPosition) > maxOcclusionDistance) {
                continue
            }

            val playerToBlock = tempVec3.set(block.position).sub(playerPosition)
            playerToBlock.y = 0f
            val dotProduct = cameraRight.dot(playerToBlock)
            val sideThreshold = blockSize * 0.75f
            if (kotlin.math.abs(dotProduct) > sideThreshold) {
                continue
            }

            if (isBlockInCutout(block, camera, cutoutX, cutoutY, cutoutWidth, cutoutHeight)) {
                candidates.add(block)
            }
        }
        return candidates
    }

    /**
     * Checks if a block is visible within the screen-space cutout rectangle.
     */
    private fun isBlockInCutout(block: GameBlock, camera: Camera, cutX: Float, cutY: Float, cutW: Float, cutH: Float): Boolean {
        val halfSize = blockSize / 2f
        blockCorners[0].set(block.position.x - halfSize, block.position.y - halfSize, block.position.z - halfSize)
        blockCorners[1].set(block.position.x + halfSize, block.position.y - halfSize, block.position.z - halfSize)
        blockCorners[2].set(block.position.x + halfSize, block.position.y + halfSize, block.position.z - halfSize)
        blockCorners[3].set(block.position.x - halfSize, block.position.y + halfSize, block.position.z - halfSize)
        blockCorners[4].set(block.position.x - halfSize, block.position.y - halfSize, block.position.z + halfSize)
        blockCorners[5].set(block.position.x + halfSize, block.position.y - halfSize, block.position.z + halfSize)
        blockCorners[6].set(block.position.x + halfSize, block.position.y + halfSize, block.position.z + halfSize)
        blockCorners[7].set(block.position.x - halfSize, block.position.y + halfSize, block.position.z + halfSize)

        for (corner in blockCorners) {
            val screenPos = camera.project(corner)
            if (screenPos.x >= cutX && screenPos.x <= cutX + cutW &&
                screenPos.y >= cutY && screenPos.y <= cutY + cutH) {
                return true
            }
        }
        return false
    }
}
