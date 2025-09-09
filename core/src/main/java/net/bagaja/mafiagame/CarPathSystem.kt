package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Plane
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Disposable
import java.util.*

// Represents a single point in a car path
data class CarPathNode(
    val id: String = UUID.randomUUID().toString(),
    val position: Vector3,
    var nextNodeId: String? = null,
    var previousNodeId: String? = null,
    val debugInstance: ModelInstance
)

enum class LinePlacementState {
    IDLE,           // Waiting for the first click to start a line
    PLACING_LINE    // First point is set, waiting for the second click
}

class CarPathSystem : Disposable {
    val nodes = mutableMapOf<String, CarPathNode>()
    private var lastPlacedNode: CarPathNode? = null
    var isVisible = false

    private val modelBuilder = ModelBuilder()
    private val modelBatch = ModelBatch()

    // Visuals for the editor
    private val nodeModel: Model
    private val lineModel: Model
    private lateinit var lineInstance: ModelInstance

    private val groundPlane = Plane(Vector3.Y, 0f)
    private val tempVec3 = Vector3()
    private val NODE_VISUAL_RADIUS = 0.5f

    private var placementState = LinePlacementState.IDLE
    private var firstNode: CarPathNode? = null // To store the starting point of the current line segment
    private lateinit var previewLineInstance: ModelInstance
    private var isPreviewVisible = false

    init {
        val nodeMaterial = Material(ColorAttribute.createDiffuse(Color.CYAN))
        nodeModel = modelBuilder.createSphere(NODE_VISUAL_RADIUS * 2, NODE_VISUAL_RADIUS * 2, NODE_VISUAL_RADIUS * 2, 8, 8, nodeMaterial, (VertexAttributes.Usage.Position).toLong())

        modelBuilder.begin()
        val lineMaterial = Material(ColorAttribute.createDiffuse(Color.CYAN))
        val partBuilder = modelBuilder.part("line", GL20.GL_LINES, VertexAttributes.Usage.Position.toLong(), lineMaterial)
        partBuilder.line(Vector3.Zero, Vector3(0f, 0f, 1f)) // Draw a line from origin to one unit on the Z axis
        lineModel = modelBuilder.end()

        lineInstance = ModelInstance(lineModel)
        previewLineInstance = ModelInstance(lineModel)
    }

    fun toggleVisibility() {
        isVisible = !isVisible
    }

    fun startNewPath() {
        lastPlacedNode = null
        firstNode = null
        placementState = LinePlacementState.IDLE
        println("Car Path: Starting a new, disconnected path.")
    }

    fun cancelPlacement() {
        if (placementState == LinePlacementState.PLACING_LINE) {
            // If we only placed one node for a new line and then cancelled, remove that lonely node.
            if (firstNode != null && firstNode?.previousNodeId == null && firstNode?.nextNodeId == null) {
                nodes.remove(firstNode!!.id)
                println("Removed dangling start node.")
            }
            firstNode = null
            placementState = LinePlacementState.IDLE
            println("Car Path: Line placement cancelled.")
        }
    }

    fun handlePlaceAction(ray: Ray) {
        if (!Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            return // Can't place if not pointing at the ground
        }
        val position = tempVec3.cpy().add(0f, NODE_VISUAL_RADIUS, 0f)

        when (placementState) {
            LinePlacementState.IDLE -> {
                // This is the FIRST click. Create the starting node.
                val startNode = CarPathNode(
                    position = position,
                    debugInstance = ModelInstance(nodeModel)
                )
                nodes[startNode.id] = startNode
                firstNode = startNode
                lastPlacedNode?.let {
                    it.nextNodeId = startNode.id
                    startNode.previousNodeId = it.id
                    println("Linked previous path to new start node ${startNode.id}")
                }
                placementState = LinePlacementState.PLACING_LINE
                println("Car Path: Set starting point.")
            }
            LinePlacementState.PLACING_LINE -> {
                // This is the SECOND click. Create the end node and link it.
                val endNode = CarPathNode(
                    position = position,
                    debugInstance = ModelInstance(nodeModel)
                )
                nodes[endNode.id] = endNode

                firstNode?.nextNodeId = endNode.id
                endNode.previousNodeId = firstNode?.id
                println("Car Path: Set end point. Line created from ${firstNode?.id} to ${endNode.id}")

                // The new end point becomes the start of the next potential line segment
                firstNode = endNode
                lastPlacedNode = endNode // Keep track of the very last node placed
                // State remains PLACING_LINE, ready for the next click
            }
        }
    }

    fun handleRemoveAction(ray: Ray): Boolean {
        val nodeToRemove = findNearestNode(ray.origin, 2f) ?: return false

        // Re-link the path to patch the hole
        val prevNode = nodeToRemove.previousNodeId?.let { nodes[it] }
        val nextNode = nodeToRemove.nextNodeId?.let { nodes[it] }

        if (prevNode != null) {
            prevNode.nextNodeId = nextNode?.id
        }
        if (nextNode != null) {
            nextNode.previousNodeId = prevNode?.id
        }

        // If we deleted the last placed node, we need to allow placing a new one from the previous
        if (lastPlacedNode?.id == nodeToRemove.id) {
            lastPlacedNode = prevNode
        }

        nodes.remove(nodeToRemove.id)
        println("Removed car path node ${nodeToRemove.id}")
        return true
    }

    fun findNearestNode(position: Vector3, maxDistance: Float = 50f): CarPathNode? {
        val maxDistSq = maxDistance * maxDistance
        return nodes.values.filter { it.position.dst2(position) < maxDistSq }
            .minByOrNull { it.position.dst2(position) }
    }

    fun update(camera: Camera) {
        if (placementState == LinePlacementState.PLACING_LINE && firstNode != null) {
            val ray = camera.getPickRay(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
            if (Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
                val start = firstNode!!.position
                val end = tempVec3.cpy().add(0f, NODE_VISUAL_RADIUS, 0f)

                // Calculate transform for the preview line, same as the final lines
                val direction = end.cpy().sub(start)
                val distance = direction.len()

                previewLineInstance.transform.setToTranslation(start)
                previewLineInstance.transform.rotateTowardDirection(direction, Vector3.Y)
                previewLineInstance.transform.scale(1f, 1f, distance)
                isPreviewVisible = true
            } else {
                isPreviewVisible = false
            }
        } else {
            isPreviewVisible = false
        }
    }

    fun render(camera: Camera) {
        if (!isVisible) return

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        modelBatch.begin(camera)
        for (node in nodes.values) {
            // Render the node sphere
            node.debugInstance.transform.setToTranslation(node.position)
            modelBatch.render(node.debugInstance)

            // Render the line to the next node
            node.nextNodeId?.let { nextId ->
                val nextNode = nodes[nextId]
                if (nextNode != null) {
                    val start = node.position
                    val end = nextNode.position
                    val direction = end.cpy().sub(start)
                    val distance = direction.len()

                    lineInstance.transform.setToTranslation(start)
                    lineInstance.transform.rotateTowardDirection(direction, Vector3.Y)
                    lineInstance.transform.scale(1f, 1f, distance)
                    modelBatch.render(lineInstance)
                }
            }
        }

        if (isPreviewVisible) {
            modelBatch.render(previewLineInstance)
        }

        modelBatch.end()
    }

    override fun dispose() {
        nodeModel.dispose()
        lineModel.dispose()
        modelBatch.dispose()
    }
}
