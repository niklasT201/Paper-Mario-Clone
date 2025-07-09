// In: OcclusionSystem.kt (replace the whole file)
package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array

class OcclusionSystem(private val blockSize: Float) {
    // Reusable objects to avoid garbage collection
    private val tempVec3 = Vector3()
    private val blockCorners = Array(8) { Vector3() }

    // A set to keep track of which blocks were hidden in the last frame.
    private val hiddenBlocks = mutableSetOf<GameBlock>()

    // Defines the size of the "cutout" window in screen percentage.
    // 0.3 means the cutout will be 30% of the screen's width and height.
    private val cutoutSizePercentage = 0.3f

    // Defines how far away from the player a wall can be to be considered for occlusion.
    private val maxOcclusionDistance = 30f

    /**
     * Main update function, called every frame.
     * Finds occluding blocks and hides them to create a "cutaway" view.
     */
    fun update(camera: Camera, playerPosition: Vector3, allBlocks: Array<GameBlock>) {
        // --- Step 1: Restore all blocks that were hidden in the previous frame. ---
        for (block in hiddenBlocks) {
            // Restore all faces to the block. The regular FaceCullingSystem will
            // re-hide the necessary ones later.
            block.visibleFaces.addAll(BlockFace.entries)
        }
        hiddenBlocks.clear()

        // --- Step 2: Determine the player's position on the 2D screen. ---
        // We will build our cutout area around this point.
        val playerScreenPos = camera.project(tempVec3.set(playerPosition).add(0f, 2f, 0f))

        // Define the 2D cutout rectangle on the screen
        val cutoutWidth = com.badlogic.gdx.Gdx.graphics.width * cutoutSizePercentage
        val cutoutHeight = com.badlogic.gdx.Gdx.graphics.height * cutoutSizePercentage
        val cutoutX = playerScreenPos.x - cutoutWidth / 2
        val cutoutY = playerScreenPos.y - cutoutHeight / 2

        // --- Step 3: Iterate through all blocks to find which ones should be hidden. ---
        for (block in allBlocks) {
            // Only check visible, solid, full blocks for occlusion.
            if (!block.blockType.isVisible || !block.blockType.hasCollision || block.shape != BlockShape.FULL_BLOCK) {
                continue
            }

            // --- Condition A: Is the block between the camera and the player? ---
            val distanceToCamera = block.position.dst(camera.position)
            val playerDistanceToCamera = playerPosition.dst(camera.position)

            // The block must be physically located between the camera and the player
            // and within a reasonable range to avoid hiding distant walls.
            if (distanceToCamera >= playerDistanceToCamera || block.position.dst(playerPosition) > maxOcclusionDistance) {
                continue
            }

            // --- Condition B: Does any part of this block fall inside the screen cutout? ---
            if (isBlockInCutout(block, camera, cutoutX, cutoutY, cutoutWidth, cutoutHeight)) {
                // This block is inside our cutout window. Hide it.
                block.visibleFaces.clear()
                hiddenBlocks.add(block) // Remember to restore it next frame.
            }
        }
    }

    /**
     * Checks if a block is visible within the screen-space cutout rectangle.
     * It does this by projecting the block's 8 corners to screen space.
     */
    private fun isBlockInCutout(block: GameBlock, camera: Camera, cutX: Float, cutY: Float, cutW: Float, cutH: Float): Boolean {
        // Get the 8 corners of the block in world space.
        val halfSize = blockSize / 2f
        blockCorners[0].set(block.position.x - halfSize, block.position.y - halfSize, block.position.z - halfSize)
        blockCorners[1].set(block.position.x + halfSize, block.position.y - halfSize, block.position.z - halfSize)
        blockCorners[2].set(block.position.x + halfSize, block.position.y + halfSize, block.position.z - halfSize)
        blockCorners[3].set(block.position.x - halfSize, block.position.y + halfSize, block.position.z - halfSize)
        blockCorners[4].set(block.position.x - halfSize, block.position.y - halfSize, block.position.z + halfSize)
        blockCorners[5].set(block.position.x + halfSize, block.position.y - halfSize, block.position.z + halfSize)
        blockCorners[6].set(block.position.x + halfSize, block.position.y + halfSize, block.position.z + halfSize)
        blockCorners[7].set(block.position.x - halfSize, block.position.y + halfSize, block.position.z + halfSize)

        // Project each corner to screen space and check if it's in the cutout.
        for (corner in blockCorners) {
            val screenPos = camera.project(corner)
            // Check if the projected point is inside the 2D rectangle.
            if (screenPos.x >= cutX && screenPos.x <= cutX + cutW &&
                screenPos.y >= cutY && screenPos.y <= cutY + cutH) {
                // If any corner is in the cutout, the whole block is considered part of it.
                return true
            }
        }

        // No corners were inside the cutout.
        return false
    }
}
