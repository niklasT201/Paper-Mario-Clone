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
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Disposable
import java.util.UUID

/**
 * Data class for a single node in a character's path.
 */
data class CharacterPathNode(
    val id: String = UUID.randomUUID().toString(),
    val position: Vector3,
    var nextNodeId: String? = null,
    var previousNodeId: String? = null,
    val debugInstance: ModelInstance,
    var isOneWay: Boolean = false,        // If true, characters must follow the direction from prev -> next
    var isMissionOnly: Boolean = false, // If true, only used when a mission is active
    var missionId: String? = null,       // The ID of the mission this path belongs to
    var sceneId: String = "WORLD"
)

/**
 * The state of the editor for placing lines for characters.
 */
enum class CharLinePlacementState {
    IDLE,
    PLACING_LINE
}

/**
 * Manages the creation, rendering, and logic for character pathfinding lines.
 */
class CharacterPathSystem : Disposable {
    val nodes = mutableMapOf<String, CharacterPathNode>()
    private var lastPlacedNode: CharacterPathNode? = null
    var isVisible = false

    // Dependencies
    lateinit var game: MafiaGame
    lateinit var raycastSystem: RaycastSystem

    // Rendering and models
    private val modelBuilder = ModelBuilder()
    private val modelBatch = ModelBatch()
    val nodeModel: Model
    private val lineModel: Model
    private var arrowModel: Model? = null
    private var lineInstance: ModelInstance
    private var arrowInstance: ModelInstance
    private var previewLineInstance: ModelInstance

    // Editor state
    private var placementState = CharLinePlacementState.IDLE
    private var firstNode: CharacterPathNode? = null
    private var isPreviewVisible = false
    var isPlacingOneWay = false
    var isPlacingMissionOnly = false

    // Helpers
    private val groundPlane = Plane(Vector3.Y, 0f)
    private val tempVec3 = Vector3()
    private val NODE_VISUAL_RADIUS = 0.4f

    init {
        val nodeMaterial = Material(ColorAttribute.createDiffuse(Color.GREEN)) // Green for characters
        nodeModel = modelBuilder.createSphere(NODE_VISUAL_RADIUS * 2, NODE_VISUAL_RADIUS * 2, NODE_VISUAL_RADIUS * 2, 8, 8, nodeMaterial, (VertexAttributes.Usage.Position).toLong())

        modelBuilder.begin()
        val lineMaterial = Material(ColorAttribute.createDiffuse(Color.GREEN))
        val partBuilder = modelBuilder.part("line", GL20.GL_LINES, VertexAttributes.Usage.Position.toLong(), lineMaterial)
        partBuilder.line(Vector3.Zero, Vector3(0f, 0f, 1f))
        lineModel = modelBuilder.end()

        val arrowMaterial = Material(ColorAttribute.createDiffuse(Color.YELLOW))
        arrowModel = modelBuilder.createCone(1.2f, 2.4f, 1.2f, 8, arrowMaterial, (VertexAttributes.Usage.Position).toLong())
        arrowInstance = ModelInstance(arrowModel!!)

        lineInstance = ModelInstance(lineModel)
        previewLineInstance = ModelInstance(lineModel)
    }

    fun addNodeFromData(data: CharacterPathNodeData): CharacterPathNode? {
        val newNode = CharacterPathNode(
            id = data.id,
            position = data.position,
            nextNodeId = data.nextNodeId,
            previousNodeId = data.previousNodeId,
            debugInstance = ModelInstance(nodeModel),
            isOneWay = data.isOneWay,
            isMissionOnly = data.isMissionOnly,
            missionId = data.missionId,
            sceneId = data.sceneId
        )
        nodes[newNode.id] = newNode
        return newNode
    }

    /**
     * Editor Function: Handles placing a new node.
     */
    fun handlePlaceAction(ray: Ray) {
        if (game.uiManager.currentEditorMode == EditorMode.MISSION) {
            handleMissionPlacement(ray)
            return
        }

        val hitPosition = getPlacementPositionFromRay(ray) ?: return
        val position = hitPosition.add(0f, NODE_VISUAL_RADIUS, 0f)

        val currentSceneId = game.sceneManager.getCurrentSceneId()

        when (placementState) {
            CharLinePlacementState.IDLE -> {
                val startNode = CharacterPathNode(
                    position = position,
                    debugInstance = ModelInstance(nodeModel),
                    isOneWay = isPlacingOneWay,
                    isMissionOnly = isPlacingMissionOnly,
                    missionId = if (isPlacingMissionOnly) game.uiManager.selectedMissionForEditing?.id else null,
                    sceneId = currentSceneId
                )
                nodes[startNode.id] = startNode
                firstNode = startNode
                game.lastPlacedInstance = startNode

                lastPlacedNode?.nextNodeId = startNode.id
                startNode.previousNodeId = lastPlacedNode?.id

                placementState = CharLinePlacementState.PLACING_LINE
                game.uiManager.updatePlacementInfo("Character Path: First point set. Click to place next.")
            }
            CharLinePlacementState.PLACING_LINE -> {
                val endNode = CharacterPathNode(
                    position = position,
                    debugInstance = ModelInstance(nodeModel),
                    sceneId = currentSceneId
                )
                nodes[endNode.id] = endNode
                game.lastPlacedInstance = endNode

                val startNode = firstNode!!
                startNode.nextNodeId = endNode.id
                endNode.previousNodeId = startNode.id

                startNode.isOneWay = isPlacingOneWay
                startNode.isMissionOnly = isPlacingMissionOnly
                startNode.missionId = if (isPlacingMissionOnly) game.uiManager.selectedMissionForEditing?.id else null

                game.uiManager.updatePlacementInfo("Character Path line created.")
                firstNode = endNode
                lastPlacedNode = endNode
            }
        }
    }

    private fun handleMissionPlacement(ray: Ray) {
        val mission = game.uiManager.selectedMissionForEditing ?: return
        val hitPosition = getPlacementPositionFromRay(ray) ?: return
        val position = hitPosition.add(0f, NODE_VISUAL_RADIUS, 0f)

        val currentSceneId = game.sceneManager.getCurrentSceneId()
        val newNodeId = "char_path_${UUID.randomUUID()}"
        val lastPlaced = game.lastPlacedInstance

        var previousNodeId: String? = null
        // Check if the last placed object was a mission preview node
        if (lastPlaced is CharacterPathNode && (lastPlaced.isMissionOnly || mission.eventsOnStart.any { it.pathNodeId == lastPlaced.id })) {
            previousNodeId = lastPlaced.id
        }

        val event = GameEvent(
            type = GameEventType.SPAWN_CHARACTER_PATH_NODE,
            spawnPosition = position,
            sceneId = currentSceneId,
            pathNodeId = newNodeId,
            previousNodeId = previousNodeId,
            isOneWay = isPlacingOneWay,
            isMissionOnly = isPlacingMissionOnly,
            missionId = if (isPlacingMissionOnly) mission.id else null
        )

        mission.eventsOnStart.add(event)
        game.missionSystem.saveMission(mission)

        // Create a temporary "preview" node to see in the editor
        val previewNodeData = CharacterPathNodeData(
            id = newNodeId,
            position = position,
            // Link the preview node visually in the editor
            previousNodeId = previousNodeId,
            isMissionOnly = true, // Mark preview as mission-owned
            sceneId = currentSceneId
        )

        val previewNode = addNodeFromData(previewNodeData)
        if (previewNode != null) {
            // Link the previous preview node to this new one for visual continuity
            if (previousNodeId != null) {
                nodes[previousNodeId]?.nextNodeId = previewNode.id
            }
            game.lastPlacedInstance = previewNode
        }

        game.uiManager.updatePlacementInfo("Added path node to '${mission.title}'")
        game.uiManager.missionEditorUI.refreshEventWidgets()
    }

    /**
     * Editor Function: Handles removing a node.
     */
    fun handleRemoveAction(ray: Ray): Boolean {
        val nodeToRemove = findNodeAtRay(ray) ?: return false
        val prevNode = nodeToRemove.previousNodeId?.let { nodes[it] }
        val nextNode = nodeToRemove.nextNodeId?.let { nodes[it] }
        prevNode?.nextNodeId = nextNode?.id
        nextNode?.previousNodeId = prevNode?.id

        if (lastPlacedNode?.id == nodeToRemove.id) lastPlacedNode = prevNode
        if (firstNode?.id == nodeToRemove.id) cancelPlacement()

        nodes.remove(nodeToRemove.id)
        return true
    }

    fun startNewPath() {
        lastPlacedNode = null
        cancelPlacement()
    }

    fun cancelPlacement() {
        firstNode = null
        placementState = CharLinePlacementState.IDLE
        isPreviewVisible = false
    }

    fun findNodeAtRay(ray: Ray): CharacterPathNode? {
        return nodes.values.minByOrNull {
            ray.origin.dst2(it.position)
        }?.takeIf {
            Intersector.intersectRaySphere(ray, it.position, NODE_VISUAL_RADIUS, null)
        }
    }

    private fun getPlacementPositionFromRay(ray: Ray): Vector3? {
        val hitBlock = raycastSystem.getBlockAtRay(ray, game.sceneManager.activeChunkManager.getAllBlocks())
        if (hitBlock != null) {
            if (Intersector.intersectRayBounds(ray, hitBlock.getBoundingBox(game.blockSize, BoundingBox()), tempVec3)) {
                return tempVec3.cpy()
            }
        }
        if (Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            return tempVec3.cpy()
        }
        return null
    }

    /**
     * Editor Function: Updates the preview line from the last point to the cursor.
     */
    fun update(camera: Camera) {
        if (placementState == CharLinePlacementState.PLACING_LINE && firstNode != null) {
            val ray = camera.getPickRay(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
            val hitPosition = getPlacementPositionFromRay(ray)
            if (hitPosition != null) {
                val start = firstNode!!.position
                val end = hitPosition.add(0f, NODE_VISUAL_RADIUS, 0f)
                val direction = end.cpy().sub(start)
                val distance = direction.len()

                previewLineInstance.transform.setToTranslation(start).rotateTowardDirection(end.cpy().sub(start), Vector3.Y).rotate(Vector3.Y, 180f)
                previewLineInstance.transform.scale(1f, 1f, distance)

                if (isPlacingOneWay) {
                    val midPoint = start.cpy().lerp(end, 0.5f)
                    arrowInstance.transform.setToTranslation(midPoint).rotateTowardDirection(direction, Vector3.Y).rotate(Vector3.X, -90f)
                }
                isPreviewVisible = true
            } else {
                isPreviewVisible = false
            }
        } else {
            isPreviewVisible = false
        }
    }

    /**
     * Editor Function: Renders the path nodes and lines.
     */
    fun render(camera: Camera) {
        if (!isVisible) return

        val currentSceneId = game.sceneManager.getCurrentSceneId()

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        modelBatch.begin(camera)
        for (node in nodes.values) {
            if (node.sceneId != currentSceneId) continue

            node.debugInstance.transform.setToTranslation(node.position)
            val colorAttr = node.debugInstance.materials.first().get(ColorAttribute.Diffuse) as ColorAttribute
            colorAttr.color.set(when {
                node.isMissionOnly -> Color.CORAL
                node.isOneWay -> Color.SKY
                else -> Color.FOREST
            })
            modelBatch.render(node.debugInstance)

            val nextNode = node.nextNodeId?.let { nodes[it] }
            if (nextNode != null) {
                val start = node.position
                val end = nextNode.position
                val direction = end.cpy().sub(start)
                val distance = direction.len()
                lineInstance.transform.setToTranslation(start).rotateTowardDirection(direction, Vector3.Y).rotate(Vector3.Y, 180f)
                lineInstance.transform.scale(1f, 1f, distance)
                modelBatch.render(lineInstance)

                if (node.isOneWay) {
                    val midPoint = start.cpy().lerp(end, 0.5f)
                    arrowInstance.transform.setToTranslation(midPoint).rotateTowardDirection(direction, Vector3.Y).rotate(Vector3.X, -90f)
                    modelBatch.render(arrowInstance)
                }
            }
        }
        if (isPreviewVisible) {
            modelBatch.render(previewLineInstance)
            if (isPlacingOneWay) modelBatch.render(arrowInstance)
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
