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
    val debugInstance: ModelInstance,
    var isOneWay: Boolean = false
)

enum class PathDirectionality(val displayName: String) {
    BI_DIRECTIONAL("Bi-Directional"),
    ONE_WAY("One-Way")
}

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
    private var arrowModel: Model? = null
    private lateinit var arrowInstance: ModelInstance
    private var currentDirectionality = PathDirectionality.BI_DIRECTIONAL
    private var isDirectionFlipped = false

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

    val isInPlacementMode: Boolean
        get() = placementState == LinePlacementState.PLACING_LINE

    init {
        val nodeMaterial = Material(ColorAttribute.createDiffuse(Color.CYAN))
        nodeModel = modelBuilder.createSphere(NODE_VISUAL_RADIUS * 2, NODE_VISUAL_RADIUS * 2, NODE_VISUAL_RADIUS * 2, 8, 8, nodeMaterial, (VertexAttributes.Usage.Position).toLong())

        modelBuilder.begin()
        val lineMaterial = Material(ColorAttribute.createDiffuse(Color.CYAN))
        val partBuilder = modelBuilder.part("line", GL20.GL_LINES, VertexAttributes.Usage.Position.toLong(), lineMaterial)
        partBuilder.line(Vector3.Zero, Vector3(0f, 0f, 1f)) // Draw a line from origin to one unit on the Z axis
        lineModel = modelBuilder.end()

        val arrowMaterial = Material(ColorAttribute.createDiffuse(Color.ORANGE))
        // A cone model makes a great arrow. We create it pointing up the Y-axis.
        arrowModel = modelBuilder.createCone(1.5f, 3f, 1.5f, 8, arrowMaterial, (VertexAttributes.Usage.Position).toLong())
        arrowInstance = ModelInstance(arrowModel!!)

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
            if (firstNode != null && firstNode?.previousNodeId == null) {
                nodes.remove(firstNode!!.id)
                println("Removed dangling start node ${firstNode!!.id}.")
                lastPlacedNode = null // Since we removed the only node, there is no last placed node.
            }

            // Reset the state machine to stop drawing.
            firstNode = null
            placementState = LinePlacementState.IDLE
            isPreviewVisible = false
            println("Car Path: Line placement cancelled.")
        }
    }

    fun cycleDirectionality(): String {
        currentDirectionality = if (currentDirectionality == PathDirectionality.BI_DIRECTIONAL) {
            PathDirectionality.ONE_WAY
        } else {
            PathDirectionality.BI_DIRECTIONAL
        }
        // When switching to bi-directional, always reset the flip state
        if (currentDirectionality == PathDirectionality.BI_DIRECTIONAL) {
            isDirectionFlipped = false
        }
        return "Line Type: ${currentDirectionality.displayName}"
    }

    fun toggleDirectionFlip(): String {
        if (currentDirectionality == PathDirectionality.ONE_WAY) {
            isDirectionFlipped = !isDirectionFlipped
            return "One-Way Direction: ${if (isDirectionFlipped) "Reversed" else "Forward"}"
        }
        return "Direction flip only applies to One-Way lines."
    }

    fun handlePlaceAction(ray: Ray) {
        if (!Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) return
        val position = tempVec3.cpy().add(0f, NODE_VISUAL_RADIUS, 0f)

        when (placementState) {
            LinePlacementState.IDLE -> {
                val startNode = CarPathNode(position = position, debugInstance = ModelInstance(nodeModel))
                nodes[startNode.id] = startNode
                firstNode = startNode
                // Link to the absolute last node placed in the previous chain, if it exists
                lastPlacedNode?.let {
                    // Only link if the new line isn't a reversed one-way
                    if (currentDirectionality == PathDirectionality.BI_DIRECTIONAL || !isDirectionFlipped) {
                        it.nextNodeId = startNode.id
                        startNode.previousNodeId = it.id
                    }
                }
                placementState = LinePlacementState.PLACING_LINE
                println("Car Path: Set starting point.")
            }
            LinePlacementState.PLACING_LINE -> {
                val endNode = CarPathNode(position = position, debugInstance = ModelInstance(nodeModel))
                nodes[endNode.id] = endNode
                val startNode = firstNode!!

                if (isDirectionFlipped && currentDirectionality == PathDirectionality.ONE_WAY) {
                    // REVERSED ONE-WAY: The flow is from the new node (end) to the old one (start)
                    endNode.nextNodeId = startNode.id
                    startNode.previousNodeId = endNode.id
                    endNode.isOneWay = true // The one-way flag is on the source of the flow
                    lastPlacedNode?.nextNodeId = null // Break link from previous path if reversed
                } else {
                    // NORMAL (BI-DIRECTIONAL or ONE-WAY FORWARD)
                    startNode.nextNodeId = endNode.id
                    endNode.previousNodeId = startNode.id
                    if (currentDirectionality == PathDirectionality.ONE_WAY) {
                        startNode.isOneWay = true // The one-way flag is on the source of the flow
                    }
                }

                println("Car Path: Line created. Mode: ${currentDirectionality.displayName}, Reversed: $isDirectionFlipped")

                firstNode = endNode
                lastPlacedNode = endNode
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

    fun findNodeAtRay(ray: Ray): CarPathNode? {
        return nodes.values.minByOrNull {
            ray.origin.dst2(it.position)
        }?.takeIf {
            // Ensure the closest node is actually near the ray
            Intersector.intersectRaySphere(ray, it.position, NODE_VISUAL_RADIUS, null)
        }
    }

    fun toggleNodeDirectionality(node: CarPathNode) {
        node.isOneWay = !node.isOneWay
        val status = if (node.isOneWay) "ONE-WAY" else "BI-DIRECTIONAL"
        println("Path segment from ${node.id} is now $status.")
    }

    fun update(camera: Camera) {
        if (placementState == LinePlacementState.PLACING_LINE && firstNode != null) {
            val ray = camera.getPickRay(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
            if (Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
                val start = firstNode!!.position
                val end = tempVec3.cpy().add(0f, NODE_VISUAL_RADIUS, 0f)

                // Determine direction based on the flip state for the preview
                val previewStart = if (isDirectionFlipped) end else start
                val previewEnd = if (isDirectionFlipped) start else end

                val direction = previewEnd.cpy().sub(previewStart)
                val distance = direction.len()

                // Position the line itself correctly between the two points
                previewLineInstance.transform.setToTranslation(start)
                previewLineInstance.transform.rotateTowardDirection(end.cpy().sub(start), Vector3.Y)
                previewLineInstance.transform.rotate(Vector3.Y, 180f)
                previewLineInstance.transform.scale(1f, 1f, distance)

                // Position and orient the arrow based on the preview direction
                if (currentDirectionality == PathDirectionality.ONE_WAY) {
                    val midPoint = previewStart.cpy().lerp(previewEnd, 0.5f)
                    arrowInstance.transform.setToTranslation(midPoint)
                    arrowInstance.transform.rotateTowardDirection(direction, Vector3.Y)
                    arrowInstance.transform.rotate(Vector3.X, -90f)
                }

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

            val nextNode = node.nextNodeId?.let { nodes[it] }
            if (nextNode != null) {
                val start = node.position
                val end = nextNode.position

                // Render line
                val direction = end.cpy().sub(start)
                val distance = direction.len()
                lineInstance.transform.setToTranslation(start)
                lineInstance.transform.rotateTowardDirection(direction, Vector3.Y)
                lineInstance.transform.rotate(Vector3.Y, 180f)
                lineInstance.transform.scale(1f, 1f, distance)
                modelBatch.render(lineInstance)

                // Render arrow if this segment is one-way
                if (node.isOneWay) {
                    val midPoint = start.cpy().lerp(end, 0.5f)
                    arrowInstance.transform.setToTranslation(midPoint)
                    arrowInstance.transform.rotateTowardDirection(direction, Vector3.Y)
                    arrowInstance.transform.rotate(Vector3.X, -90f)
                    modelBatch.render(arrowInstance)
                }
            }
        }

        if (isPreviewVisible) {
            modelBatch.render(previewLineInstance)
            if (currentDirectionality == PathDirectionality.ONE_WAY) {
                modelBatch.render(arrowInstance)
            }
        }

        modelBatch.end()
    }

    override fun dispose() {
        nodeModel.dispose()
        lineModel.dispose()
        arrowModel?.dispose()
        modelBatch.dispose()
    }
}
