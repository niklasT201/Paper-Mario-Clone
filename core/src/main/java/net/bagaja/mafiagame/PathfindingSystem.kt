package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
import java.util.*
import kotlin.math.abs
import kotlin.math.floor

class PathfindingSystem(
    private val sceneManager: SceneManager,
    private val blockSize: Float,
    private val characterSize: Vector3
) {

    // Represents a node in our grid for the A* algorithm
    private data class Node(
        val position: Vector3,
        var gCost: Int = 0, // Cost from the start node
        var hCost: Int = 0, // Heuristic cost to the end node
        var parent: Node? = null
    ) {
        val fCost: Int get() = gCost + hCost
    }

    fun findPath(startPos: Vector3, targetPos: Vector3): Queue<Vector3>? {
        val startNode = Node(snapToGrid(startPos))
        val targetNode = Node(snapToGrid(targetPos))

        val openSet = PriorityQueue<Node>(compareBy { it.fCost })
        val closedSet = mutableSetOf<Vector3>()

        openSet.add(startNode)

        while (openSet.isNotEmpty()) {
            val currentNode = openSet.poll()
            closedSet.add(currentNode.position)

            if (currentNode.position.epsilonEquals(targetNode.position, 1f)) {
                return reconstructPath(currentNode)
            }

            for (neighborPos in getNeighbors(currentNode)) {
                val moveCost = getMovementCost(neighborPos)
                if (moveCost >= 9999 || neighborPos in closedSet) { // 9999 means unwalkable
                    continue
                }

                val newGCost = currentNode.gCost + moveCost + getDistance(currentNode, Node(neighborPos))
                val existingNode = openSet.find { it.position.epsilonEquals(neighborPos) }

                if (existingNode == null || newGCost < existingNode.gCost) {
                    val neighborNode = existingNode ?: Node(neighborPos)
                    neighborNode.gCost = newGCost
                    neighborNode.hCost = getDistance(Node(neighborPos), targetNode)
                    neighborNode.parent = currentNode

                    if (existingNode == null) {
                        openSet.add(neighborNode)
                    }
                }
            }
        }
        return null // No path found
    }

    private fun getMovementCost(position: Vector3): Int {
        // Base cost for a clear tile
        var cost = 10

        // Check for solid obstacles (unwalkable)
        val groundCheckPos = position.cpy().sub(0f, 0.1f, 0f)
        val headCheckPos = position.cpy().add(0f, blockSize / 2f, 0f)
        val groundBlock = sceneManager.activeChunkManager.getBlockAtWorld(groundCheckPos)
        val headBlock = sceneManager.activeChunkManager.getBlockAtWorld(headCheckPos)
        val hasSupport = groundBlock != null && groundBlock.blockType.hasCollision
        val isClear = headBlock == null || !headBlock.blockType.hasCollision

        if (!hasSupport || !isClear) {
            return 9999 // Unwalkable
        }

        // Check for fire (high cost but walkable)
        sceneManager.game.fireSystem.activeFires.forEach { fire ->
            // Use a slightly larger radius for avoidance than the actual damage radius
            val avoidanceRadius = fire.damageRadius * 1.5f
            if (fire.gameObject.position.dst(position) < avoidanceRadius) {
                cost += 200 // Add a high penalty for walking through fire
            }
        }

        return cost
    }

    private fun reconstructPath(endNode: Node): Queue<Vector3> {
        val path = LinkedList<Vector3>()
        var currentNode: Node? = endNode
        while (currentNode != null) {
            path.addFirst(currentNode.position)
            currentNode = currentNode.parent
        }
        return path
    }

    private fun getNeighbors(node: Node): List<Vector3> {
        val neighbors = mutableListOf<Vector3>()
        for (x in -1..1) {
            for (z in -1..1) {
                if (x == 0 && z == 0) continue
                if (abs(x) == 1 && abs(z) == 1) continue
                neighbors.add(node.position.cpy().add(x * blockSize, 0f, z * blockSize))
            }
        }
        return neighbors
    }

    private fun getDistance(nodeA: Node, nodeB: Node): Int {
        val dstX = abs(nodeA.position.x - nodeB.position.x) / blockSize
        val dstZ = abs(nodeA.position.z - nodeB.position.z) / blockSize
        return (dstX + dstZ).toInt() * 10
    }

    private fun snapToGrid(pos: Vector3): Vector3 {
        val gridX = floor(pos.x / blockSize) * blockSize + (blockSize / 2f)
        val gridY = sceneManager.findHighestSupportY(pos.x, pos.z, pos.y, 0.1f, blockSize) + (characterSize.y / 2f)
        val gridZ = floor(pos.z / blockSize) * blockSize + (blockSize / 2f)
        return Vector3(gridX, gridY, gridZ)
    }
}
