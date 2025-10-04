package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Plane
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Array
import java.util.*
import kotlin.math.abs

enum class SceneType {
    WORLD,
    HOUSE_INTERIOR,
    TRANSITIONING_TO_INTERIOR,
    TRANSITIONING_TO_WORLD
}

data class SceneTransition(
    val fromScene: SceneType,
    val toScene: SceneType,
    val houseId: String? = null,
    val exitPosition: Vector3? = null
)

class SceneManager(
    // It needs references to systems to create/manage objects
    val playerSystem: PlayerSystem,
    private val blockSystem: BlockSystem,
    private val objectSystem: ObjectSystem,
    private val itemSystem: ItemSystem,
    private val interiorSystem: InteriorSystem,
    val enemySystem: EnemySystem,
    val npcSystem: NPCSystem,
    private val roomTemplateManager: RoomTemplateManager,
    val cameraManager: CameraManager,
    private val houseSystem: HouseSystem,
    private val transitionSystem: TransitionSystem,
    private val faceCullingSystem: FaceCullingSystem,
    val game: MafiaGame,
    val particleSystem: ParticleSystem,
    val fireSystem: FireSystem,
    val boneSystem: BoneSystem
) {
    lateinit var raycastSystem: RaycastSystem
    lateinit var teleporterSystem: TeleporterSystem
    // --- ACTIVE SCENE DATA ---
    lateinit var worldChunkManager: ChunkManager
    val interiorChunkManagers = mutableMapOf<String, ChunkManager>()
    lateinit var activeChunkManager: ChunkManager
    val activeObjects = Array<GameObject>()
    val activeCars = Array<GameCar>()
    val activeHouses = Array<GameHouse>()
    val activeEntryPoints = Array<GameEntryPoint>()
    val activeItems = Array<GameItem>()
    val activeInteriors = Array<GameInterior>()
    val activeEnemies = Array<GameEnemy>()
    val activeNPCs = Array<GameNPC>()
    val activeSpawners = Array<GameSpawner>()
    val activeBloodPools = Array<BloodPool>()
    val activeFootprints = Array<GameFootprint>()
    val activeBones = Array<GameBone>()
    val activeBullets = Array<Bullet>()
    val activeThrowables = Array<ThrowableEntity>()
    val activeMissionPreviewEnemies = Array<GameEnemy>()
    val activeMissionPreviewNPCs = Array<GameNPC>()
    val activeMissionPreviewCars = Array<GameCar>()
    val activeMissionPreviewItems = Array<GameItem>()
    val activeMissionPreviewHouses = Array<GameHouse>()
    val activeMissionPreviewObjects = Array<GameObject>()


    // State Management
    var currentScene: SceneType = SceneType.WORLD
        private set
    var worldState: WorldState? = null
    val interiorStates = mutableMapOf<String, InteriorState>()
    private var currentInteriorId: String? = null
    private var pendingHouse: GameHouse? = null

    // Helper objects for physics/raycasting
    private val tempBlockBounds = BoundingBox()
    private val tempStairBounds = BoundingBox()
    private val wedgePlane = Plane()
    private val p1 = Vector3()
    private val p2 = Vector3()
    private val p3 = Vector3()

    // --- INITIALIZATION ---
    fun initializeWorld(
        initialBlocks: Array<GameBlock>,
        initialObjects: Array<GameObject>,
        initialCars: Array<GameCar>,
        initialHouses: Array<GameHouse>,
        initialItems: Array<GameItem>,
        initialEnemies: Array<GameEnemy>,
        initialNPCs: Array<GameNPC>,
    ) {
        worldChunkManager = ChunkManager(faceCullingSystem, game.blockSize, game)
        worldChunkManager.loadInitialBlocks(initialBlocks)
        activeChunkManager = worldChunkManager

        activeObjects.addAll(initialObjects)
        activeCars.addAll(initialCars)
        activeHouses.addAll(initialHouses)
        activeItems.addAll(initialItems)
        activeEnemies.addAll(initialEnemies)
        activeNPCs.addAll(initialNPCs)

        currentScene = SceneType.WORLD

        println("SceneManager initialized. World scene is active with ChunkManager.")
    }

    fun clearMissionPreviews() {
        activeMissionPreviewEnemies.clear()
        activeMissionPreviewNPCs.clear()
        activeMissionPreviewCars.clear()
        activeMissionPreviewItems.clear()
        activeMissionPreviewHouses.clear()
        activeMissionPreviewObjects.clear()

        // Also remove any preview blocks
        val blocksToRemove = activeChunkManager.getAllBlocks().filter { it.missionId != null }
        if (blocksToRemove.isNotEmpty()) {
            println("Removing ${blocksToRemove.size} mission preview blocks...")
            blocksToRemove.forEach { removeBlock(it) }
            activeChunkManager.processDirtyChunks() // Force the chunks to rebuild their visuals
        }

        println("Cleared all mission preview entities.")
    }

    fun addBlock(block: GameBlock) {
        activeChunkManager.addBlock(block)
    }

    fun removeBlock(block: GameBlock) {
        activeChunkManager.removeBlock(block)
    }

    fun cleanupMissionEntities(missionId: String) {
        println("--- Cleaning up entities for mission: $missionId ---")

        // --- Special Cases First (Entities requiring system-specific removal logic) ---

        // Blocks (must process dirty chunks after removal)
        val blocksToRemove = activeChunkManager.getAllBlocks().filter { it.missionId == missionId }
        if (blocksToRemove.isNotEmpty()) {
            println("  - Removing ${blocksToRemove.size} mission blocks.")
            blocksToRemove.forEach { removeBlock(it) }
            activeChunkManager.processDirtyChunks()
        }

        // Objects (must also remove associated lights)
        val objectsToRemove = activeObjects.filter { it.missionId == missionId }
        if (objectsToRemove.isNotEmpty()) {
            println("  - Removing ${objectsToRemove.size} mission objects.")
            // Create a copy to iterate over while modifying the original list
            val objectsToRemoveCopy = Array(objectsToRemove.toTypedArray())
            for (obj in objectsToRemoveCopy) {
                objectSystem.removeGameObjectWithLight(obj, game.lightingManager)
                activeObjects.removeValue(obj, true)
            }
        }

        // Fires (must be removed via its system to also clean up lights)
        val firesToRemove = fireSystem.activeFires.filter { it.missionId == missionId }
        if (firesToRemove.isNotEmpty()) {
            println("  - Removing ${firesToRemove.size} mission fires.")
            val firesToRemoveCopy = Array(firesToRemove.toTypedArray())
            for (fire in firesToRemoveCopy) {
                // This properly removes the fire and its associated game object and light
                fireSystem.removeFire(fire, objectSystem, game.lightingManager)
                activeObjects.removeValue(fire.gameObject, true)
            }
        }

        // Teleporters (must be removed via its system for unlinking)
        val teleportersToRemove = teleporterSystem.activeTeleporters.filter { it.missionId == missionId }
        if (teleportersToRemove.isNotEmpty()){
            println("  - Removing ${teleportersToRemove.size} mission teleporters.")
            val teleportersToRemoveCopy = Array(teleportersToRemove.toTypedArray())
            for (teleporter in teleportersToRemoveCopy) {
                teleporterSystem.removeTeleporter(teleporter)
            }
        }

        // Path Nodes (must be removed from a map, not a list)
        val carNodesToRemove = game.carPathSystem.nodes.values.filter { it.missionId == missionId }.map { it.id }
        if (carNodesToRemove.isNotEmpty()) {
            println("  - Removing ${carNodesToRemove.size} mission car path nodes.")
            carNodesToRemove.forEach { game.carPathSystem.nodes.remove(it) }
        }
        val charNodesToRemove = game.characterPathSystem.nodes.values.filter { it.missionId == missionId }.map { it.id }
        if (charNodesToRemove.isNotEmpty()) {
            println("  - Removing ${charNodesToRemove.size} mission character path nodes.")
            charNodesToRemove.forEach { game.characterPathSystem.nodes.remove(it) }
        }

        // --- Standard List Cleanup (using removeAll with a temporary array) ---
        // Note: com.badlogic.gdx.utils.Array doesn't have a simple removeAll with a predicate.

        val carsToRemove = activeCars.filter { it.missionId == missionId }
        if (carsToRemove.isNotEmpty()) {
            println("  - Removing ${carsToRemove.size} mission cars.")
            activeCars.removeAll(Array(carsToRemove.toTypedArray()), true)
        }

        val enemiesToRemove = activeEnemies.filter { it.missionId == missionId }
        if (enemiesToRemove.isNotEmpty()) {
            println("  - Removing ${enemiesToRemove.size} mission enemies.")
            activeEnemies.removeAll(Array(enemiesToRemove.toTypedArray()), true)
        }

        val npcsToRemove = activeNPCs.filter { it.missionId == missionId }
        if (npcsToRemove.isNotEmpty()) {
            println("  - Removing ${npcsToRemove.size} mission NPCs.")
            activeNPCs.removeAll(Array(npcsToRemove.toTypedArray()), true)
        }

        val itemsToRemove = activeItems.filter { it.missionId == missionId }
        if (itemsToRemove.isNotEmpty()) {
            println("  - Removing ${itemsToRemove.size} mission items.")
            activeItems.removeAll(Array(itemsToRemove.toTypedArray()), true)
        }

        val housesToRemove = activeHouses.filter { it.missionId == missionId }
        if (housesToRemove.isNotEmpty()) {
            println("  - Removing ${housesToRemove.size} mission houses.")
            activeHouses.removeAll(Array(housesToRemove.toTypedArray()), true)
        }

        val interiorsToRemove = activeInteriors.filter { it.missionId == missionId }
        if (interiorsToRemove.isNotEmpty()) {
            println("  - Removing ${interiorsToRemove.size} mission interiors.")
            activeInteriors.removeAll(Array(interiorsToRemove.toTypedArray()), true)
        }

        val spawnersToRemove = activeSpawners.filter { it.missionId == missionId }
        if (spawnersToRemove.isNotEmpty()) {
            println("  - Removing ${spawnersToRemove.size} mission spawners.")
            activeSpawners.removeAll(Array(spawnersToRemove.toTypedArray()), true)
        }

        println("--- Mission cleanup complete ---")
    }

    fun checkCollisionForRay(ray: Ray, maxDistance: Float): CollisionResult? {
        val intersectionPoint = Vector3()
        var closestResult: CollisionResult? = null
        var closestDistSq = Float.MAX_VALUE
        val maxDistanceSq = maxDistance * maxDistance

        // 1. Check against Blocks
        activeChunkManager.getAllBlocks().forEach { block ->
            if (!block.blockType.hasCollision || !block.blockType.isVisible) return@forEach
            val blockBounds = block.getBoundingBox(game.blockSize, BoundingBox())
            if (Intersector.intersectRayBounds(ray, blockBounds, intersectionPoint)) {
                val distSq = ray.origin.dst2(intersectionPoint)
                if (distSq <= maxDistanceSq && distSq < closestDistSq) {
                    // Simplified normal calculation for this purpose
                    val normal = ray.direction.cpy().scl(-1f)
                    closestResult = CollisionResult(HitObjectType.BLOCK, block, intersectionPoint.cpy(), normal)
                    closestDistSq = distSq
                }
            }
        }

        // 2. Check against complex meshes (Houses, 3D Interiors)
        val allMeshes = activeHouses.map { it to HitObjectType.HOUSE } +
            activeInteriors.filter { it.interiorType.is3D && it.interiorType.hasCollision }.map { it to HitObjectType.INTERIOR }

        for ((meshObject, type) in allMeshes) {
            var hit = false
            when (meshObject) {
                is GameHouse -> if (meshObject.intersectsRay(ray, intersectionPoint)) hit = true
                is GameInterior -> if (meshObject.intersectsRay(ray, intersectionPoint)) hit = true
            }

            if (hit) {
                val distSq = ray.origin.dst2(intersectionPoint)
                if (distSq <= maxDistanceSq && distSq < closestDistSq) {
                    val normal = ray.direction.cpy().scl(-1f)
                    closestResult = CollisionResult(type, meshObject, intersectionPoint.cpy(), normal)
                    closestDistSq = distSq
                }
            }
        }

        return closestResult
    }

    private fun calculateWedgeSupportY(wedge: GameBlock, x: Float, z: Float): Float {
        val modelInstance = wedge.modelInstance ?: return -Float.MAX_VALUE
        val transform = modelInstance.transform
        val halfSize = game.blockSize / 2f
        val topY = (game.blockSize * wedge.blockType.height) / 2f

        // 1. Define the local-space vertices of the sloped top face
        val v_top_corner = p1.set(-halfSize, topY, -halfSize)
        val v_top_x = p2.set(halfSize, topY, -halfSize)
        val v_top_z = p3.set(-halfSize, topY, halfSize)

        // 2. Transform them into world space
        v_top_corner.mul(transform)
        v_top_x.mul(transform)
        v_top_z.mul(transform)

        // 3. Create a mathematical plane from the three world-space points
        wedgePlane.set(v_top_corner, v_top_x, v_top_z)

        // 4. Calculate the Y value on the plane for the given X and Z
        if (wedgePlane.normal.y == 0f) return -Float.MAX_VALUE // Avoid division by zero
        val supportY = (-wedgePlane.normal.x * x - wedgePlane.normal.z * z - wedgePlane.d) / wedgePlane.normal.y

        // 5. Final check: ensure the point (x,z) is actually within the triangle's 2D footprint.
        if (Intersector.isPointInTriangle(x, z, v_top_corner.x, v_top_corner.z, v_top_x.x, v_top_x.z, v_top_z.x, v_top_z.z)) {
            return supportY
        }

        return -Float.MAX_VALUE // Return a very low number if not on the triangle
    }

    private fun getSupportYForBlock(block: GameBlock, x: Float, z: Float, checkRadius: Float): Float {
        if (block.shape == BlockShape.CORNER_WEDGE) {
            // This will return the exact Y on the sloped plane, or a very low number if (x,z) is outside the triangle.
            return calculateWedgeSupportY(block, x, z)
        } else {
            val blockBounds = block.getBoundingBox(game.blockSize, tempBlockBounds)
            val horizontalOverlap = (x + checkRadius > blockBounds.min.x && x - checkRadius < blockBounds.max.x) &&
                (z + checkRadius > blockBounds.min.z && z - checkRadius < blockBounds.max.z)

            // Return the top of the box if we overlap, otherwise a very low number.
            return if (horizontalOverlap) blockBounds.max.y else -Float.MAX_VALUE
        }
    }

    fun findHighestSupportY(x: Float, z: Float, currentY: Float, checkRadius: Float, blockSize: Float): Float {
        val fallbackY = if (game.isEditorMode) 0f else -1000f

        var highestSupportY = fallbackY // Default to ground level
        val entityFootY = currentY - (playerSystem.playerSize.y / 2f)
        val checkRange = 10f

        // 1. Check Blocks
        val blocksInColumn = activeChunkManager.getBlocksInColumn(x, z)
        for (block in blocksInColumn) {
            if (!block.blockType.hasCollision) continue

            // Get the potential support Y
            val potentialSupportY = getSupportYForBlock(block, x, z, checkRadius)

            // support must be reasonably close to the player's feet
            if (potentialSupportY <= entityFootY + PlayerSystem.MAX_STEP_HEIGHT && potentialSupportY > highestSupportY) {
                highestSupportY = potentialSupportY
            }
        }

        // Check against all active houses
        for (house in activeHouses) {
            // Is the player even horizontally close to this house?
            if (abs(house.position.x - x) > checkRange || abs(house.position.z - z) > checkRange) {
                continue // Skip this house, it's too far away
            }
            // NEW STAIR LOGIC (from old PlayerSystem)
            if (house.houseType == HouseType.STAIR) {
                val playerHeight = playerSystem.playerSize.y

                // Create test bounds at the potential new position
                val testBounds = BoundingBox(
                    Vector3(x - checkRadius, entityFootY, z - checkRadius),
                    Vector3(x + checkRadius, currentY + playerHeight / 2f, z + checkRadius)
                )

                // Are we currently colliding with the stair mesh?
                if (house.collidesWithMesh(testBounds)) {
                    // Yes -> We need to step UP. Find the correct height.
                    val stairStepHeight = findStairStepHeight(house, x, z, currentY, checkRadius, playerHeight)
                    if (stairStepHeight > highestSupportY) {
                        highestSupportY = stairStepHeight
                    }
                } else {
                    // No -> We might be standing on it. Find the support height BELOW us.
                    val supportHeight = findStairSupportHeight(house, x, z, currentY, checkRadius, playerHeight)
                    if (supportHeight > highestSupportY) {
                        highestSupportY = supportHeight
                    }
                }
            } else {
                val houseBounds = house.modelInstance.calculateBoundingBox(BoundingBox())
                val horizontalOverlap = (x + checkRadius > houseBounds.min.x && x - checkRadius < houseBounds.max.x) &&
                    (z + checkRadius > houseBounds.min.z && z - checkRadius < houseBounds.max.z)

                if(horizontalOverlap) {
                    val houseTop = houseBounds.max.y
                    if (houseTop <= entityFootY + PlayerSystem.MAX_STEP_HEIGHT && houseTop > highestSupportY) {
                        highestSupportY = houseTop
                    }
                }
            }
        }

        // Check against all solid 3D interiors
        for (interior in activeInteriors) {
            if (!interior.interiorType.is3D || !interior.interiorType.hasCollision) continue

            // CHEAP CHECK FIRST
            if (abs(interior.position.x - x) > checkRange || abs(interior.position.z - z) > checkRange) {
                continue
            }

            val supportRay = Ray()
            val supportIntersection = Vector3()
            val rayDirection = Vector3.Y.cpy().scl(-1f)
            val checkPoints = arrayOf(
                Vector3(x, 1000f, z), Vector3(x - checkRadius * 0.9f, 1000f, z - checkRadius * 0.9f),
                Vector3(x + checkRadius * 0.9f, 1000f, z - checkRadius * 0.9f), Vector3(x - checkRadius * 0.9f, 1000f, z + checkRadius * 0.9f),
                Vector3(x + checkRadius * 0.9f, 1000f, z + checkRadius * 0.9f)
            )
            var wasHit = false
            var maxHitY = -Float.MAX_VALUE
            for (point in checkPoints) {
                supportRay.set(point, rayDirection)
                if (interior.intersectsRay(supportRay, supportIntersection)) {
                    wasHit = true
                    if (supportIntersection.y > maxHitY) {
                        maxHitY = supportIntersection.y
                    }
                }
            }

            if (wasHit) {
                // Only consider this interior as support if it's at or below the player's feet.
                if (maxHitY <= entityFootY + PlayerSystem.MAX_STEP_HEIGHT && maxHitY > highestSupportY) {
                    highestSupportY = maxHitY
                }
            }
        }

        return highestSupportY
    }

    fun findStrictSupportY(x: Float, z: Float, currentY: Float, checkRadius: Float, blockSize: Float): Float {
        val fallbackY = if (game.isEditorMode) 0f else -1000f

        var highestSupportY = fallbackY // Default to ground level
        val entityFootY = currentY - (playerSystem.playerSize.y / 2f)

        // 1. Check Blocks
        val blocksInColumn = activeChunkManager.getBlocksInColumn(x, z)
        for (block in blocksInColumn) {
            if (!block.blockType.hasCollision) continue

            // Get the potential support Y
            val potentialSupportY = getSupportYForBlock(block, x, z, checkRadius)

            // support must be at or below the player's feet
            if (potentialSupportY <= entityFootY + 0.01f && potentialSupportY > highestSupportY) {
                highestSupportY = potentialSupportY
            }
        }

        // 2. Check against all active houses
        for (house in activeHouses) {
            val houseBounds = house.modelInstance.calculateBoundingBox(BoundingBox())
            val horizontalOverlap = (x + checkRadius > houseBounds.min.x && x - checkRadius < houseBounds.max.x) &&
                (z + checkRadius > houseBounds.min.z && z - checkRadius < houseBounds.max.z)

            if(horizontalOverlap) {
                val houseTop = houseBounds.max.y
                // STRICT CHECK
                if (houseTop <= entityFootY + 0.01f && houseTop > highestSupportY) {
                    highestSupportY = houseTop
                }
            }
        }

        // 3. Check against all solid 3D interiors
        for (interior in activeInteriors) {
            if (!interior.interiorType.is3D || !interior.interiorType.hasCollision) continue
            val interiorBounds = interior.instance.calculateBoundingBox(BoundingBox())
            val horizontalOverlap = (x + checkRadius > interiorBounds.min.x && x - checkRadius < interiorBounds.max.x) &&
                (z + checkRadius > interiorBounds.min.z && z - checkRadius < interiorBounds.max.z)
            if(horizontalOverlap) {
                val interiorTop = interiorBounds.max.y
                // STRICT CHECK
                if (interiorTop <= entityFootY + 0.01f && interiorTop > highestSupportY) {
                    highestSupportY = interiorTop
                }
            }
        }

        return highestSupportY
    }

    fun findHighestSupportYForItem(x: Float, z: Float, currentY: Float, blockSize: Float): Float {
        val fallbackY = if (game.isEditorMode) 0f else -1000f
        var highestSupportY = fallbackY // Default to ground level
        val checkRadius = 0.1f // A tiny radius is fine for items

        // 1. Check Blocks using the efficient column query
        val blocksInColumn = activeChunkManager.getBlocksInColumn(x, z)
        for (block in blocksInColumn) {
            if (!block.blockType.hasCollision) continue

            val blockBounds = block.getBoundingBox(blockSize, tempBlockBounds)
            val blockTop = blockBounds.max.y

            // The support must be at or below the item's current position, and higher than any other support found.
            if (blockTop <= currentY && blockTop > highestSupportY) {
                highestSupportY = blockTop
            }
        }

        // 2. Check against all active houses (simplified check)
        for (house in activeHouses) {
            val houseBounds = house.modelInstance.calculateBoundingBox(BoundingBox())
            val horizontalOverlap = (x + checkRadius > houseBounds.min.x && x - checkRadius < houseBounds.max.x) &&
                (z + checkRadius > houseBounds.min.z && z - checkRadius < houseBounds.max.z)

            if(horizontalOverlap) {
                val houseTop = houseBounds.max.y
                // Same logic as for blocks.
                if (houseTop <= currentY && houseTop > highestSupportY) {
                    highestSupportY = houseTop
                }
            }
        }

        // 3. Check against all solid 3D interiors (simplified check)
        for (interior in activeInteriors) {
            if (!interior.interiorType.is3D || !interior.interiorType.hasCollision) continue

            val interiorBounds = interior.instance.calculateBoundingBox(BoundingBox())
            val horizontalOverlap = (x + checkRadius > interiorBounds.min.x && x - checkRadius < interiorBounds.max.x) &&
                (z + checkRadius > interiorBounds.min.z && z - checkRadius < interiorBounds.max.z)

            if(horizontalOverlap) {
                val interiorTop = interiorBounds.max.y
                if (interiorTop <= currentY && interiorTop > highestSupportY) {
                    highestSupportY = interiorTop
                }
            }
        }

        return highestSupportY
    }

    private fun findStairStepHeight(house: GameHouse, x: Float, z: Float, currentY: Float, checkRadius: Float, playerHeight: Float): Float {
        val stepSize = 0.1f
        val maxStepUp = 4.0f // Matches player's MAX_STEP_HEIGHT
        val playerFootY = currentY - playerHeight / 2f
        var lastCollisionHeight = playerFootY
        val testBounds = tempStairBounds

        // Try different heights to find where we stop colliding
        for (stepHeight in generateSequence(0f) { it + stepSize }.takeWhile { it <= maxStepUp }) {
            val testPlayerCenterY = currentY + stepHeight
            testBounds.set(
                Vector3(x - checkRadius, testPlayerCenterY - playerHeight / 2f, z - checkRadius),
                Vector3(x + checkRadius, testPlayerCenterY + playerHeight / 2f, z + checkRadius)
            )

            if (house.collidesWithMesh(testBounds)) {
                lastCollisionHeight = testPlayerCenterY - playerHeight / 2f
            } else {
                // Found the first non-colliding height. The surface is just below this.
                return lastCollisionHeight + 0.1f // Small margin above the step
            }
        }

        // If we still collide after max step up, it's a wall. Return current foot position.
        return playerFootY
    }

    /**
     * Finds the height of the stair surface when the player is already on it.
     * Iterates downwards to find the ground.
     */
    private fun findStairSupportHeight(house: GameHouse, x: Float, z: Float, currentY: Float, checkRadius: Float, playerHeight: Float): Float {
        val stepSize = 0.05f
        var lastNonCollisionFootY = 0f // Default to ground
        val testBounds = tempStairBounds

        // Check downward from current position to find the stair surface
        for (checkPlayerCenterY in generateSequence(currentY) { it - stepSize }.takeWhile { it >= 0f }) {
            testBounds.set(
                Vector3(x - checkRadius, checkPlayerCenterY - playerHeight / 2f, z - checkRadius),
                Vector3(x + checkRadius, checkPlayerCenterY + playerHeight / 2f, z + checkRadius)
            )

            if (house.collidesWithMesh(testBounds)) {
                // Found a collision. The support surface is the last place we *didn't* collide.
                return lastNonCollisionFootY
            } else {
                // No collision yet, so this height is safe. Record the foot level.
                lastNonCollisionFootY = checkPlayerCenterY - playerHeight / 2f
            }
        }

        return 0f // If we fall all the way through, we're at ground level.
    }

    fun findHighestSupportYForCar(x: Float, z: Float, checkRadius: Float, blockSize: Float): Float {
        val fallbackY = if (game.isEditorMode) 0f else -1000f
        var highestSupportY = fallbackY // Default to ground level

        // Check against all active blocks
        val blocksInColumn = activeChunkManager.getBlocksInColumn(x, z)
        for (block in blocksInColumn) {
            if (!block.blockType.hasCollision) {
                continue
            }
            val blockBounds = block.getBoundingBox(blockSize, tempBlockBounds)
            val horizontalOverlap = (x + checkRadius > blockBounds.min.x && x - checkRadius < blockBounds.max.x) &&
                (z + checkRadius > blockBounds.min.z && z - checkRadius < blockBounds.max.z)
            if (horizontalOverlap && blockBounds.max.y > highestSupportY) {
                highestSupportY = blockBounds.max.y
            }
        }

        // Check against all active houses (which can include stairs)
        for (house in activeHouses) {
            val houseBounds = house.modelInstance.calculateBoundingBox(BoundingBox())
            val horizontalOverlap = (x + checkRadius > houseBounds.min.x && x - checkRadius < houseBounds.max.x) &&
                (z + checkRadius > houseBounds.min.z && z - checkRadius < houseBounds.max.z)

            if(horizontalOverlap) {
                val houseTop = houseBounds.max.y
                // No Y-check here, so we can find stairs above the car
                if (houseTop > highestSupportY) {
                    highestSupportY = houseTop
                }
            }
        }

        return highestSupportY
    }

    fun hasSolidSupportAt(x: Float, z: Float): Boolean {
        val tempBounds = BoundingBox()

        val blocksInColumn = activeChunkManager.getBlocksInColumn(x, z)
        for (block in blocksInColumn) {
            // We only care about blocks that actually have collision.
            if (!block.blockType.hasCollision) continue

            // Get the world-space bounding box for the current block.
            val blockBounds = block.getBoundingBox(game.blockSize, tempBounds)

            if (x >= blockBounds.min.x && x <= blockBounds.max.x &&
                z >= blockBounds.min.z && z <= blockBounds.max.z) {
                // We found a solid block under this point!
                return true
            }
        }

        return false
    }

    fun isPositionValidForFire(position: Vector3): Boolean {
        // Define the collision volume for a fire. It's a small box at its base.
        val fireVisualWidth = ObjectType.FIRE_SPREAD.width
        val fireHalfWidth = fireVisualWidth / 2f
        val fireCollisionHeight = 1.0f // A small height is sufficient for the check

        val fireBounds = BoundingBox()
        fireBounds.set(
            position.cpy().sub(fireHalfWidth, 0f, fireHalfWidth), // Check from the ground up
            position.cpy().add(fireHalfWidth, fireCollisionHeight, fireHalfWidth)
        )

        // Efficiently check only against blocks in the relevant column
        val blocksInColumn = activeChunkManager.getBlocksInColumn(position.x, position.z)
        for (block in blocksInColumn) {
            if (!block.blockType.hasCollision) continue

            val blockBounds = block.getBoundingBox(game.blockSize, tempBlockBounds)
            if (fireBounds.intersects(blockBounds)) {
                val fireIsOnTop = fireBounds.min.y >= blockBounds.max.y - 0.1f // Use a small tolerance

                if (fireIsOnTop) {
                    continue
                } else {
                    // Collision detected! This is an invalid spot.
                    return false
                }
            }
        }

        // If we looped through all blocks and found no invalid collisions, the spot is valid.
        return true
    }

    fun update(deltaTime: Float) {
        // Checks if an animation has finished
        if (transitionSystem.isFinished()) {
            when (currentScene) {
                SceneType.TRANSITIONING_TO_INTERIOR -> completeTransitionToInterior()
                SceneType.TRANSITIONING_TO_WORLD -> completeTransitionToWorld()
                else -> {} // Do nothing
            }
            transitionSystem.reset() // Reset for the next use
        }
    }

    fun isTransitioning(): Boolean {
        return currentScene == SceneType.TRANSITIONING_TO_INTERIOR ||
            currentScene == SceneType.TRANSITIONING_TO_WORLD
    }

    // --- TRANSITION LOGIC ---
    fun transitionToInterior(house: GameHouse) {
        if (isTransitioning()) return // Prevent starting a new transition while one is active

        println("Starting transition to interior of house: ${house.id}")
        saveWorldState()
        pendingHouse = house
        currentScene = SceneType.TRANSITIONING_TO_INTERIOR
        transitionSystem.startInTransition(duration = 1.5f)
    }

    fun transitionToWorld() {
        if (isTransitioning()) return

        println("Starting transition back to world...")
        saveCurrentInteriorState() // Save any changes made to the live interior
        currentScene = SceneType.TRANSITIONING_TO_WORLD
        transitionSystem.startOutTransition(duration = 1.5f) // Start the 0.7 second animation
    }

    private fun completeTransitionToInterior() {
        val house = pendingHouse ?: return

        game.missionSystem.onPlayerEnteredHouse(house.id)

        val interiorCm = interiorChunkManagers.getOrPut(house.id) {
            println("Creating new ChunkManager for house ID: ${house.id}")
            ChunkManager(faceCullingSystem, game.blockSize, game)
        }

        // This will hold the state of the interior we are about to load.
        val interiorState: InteriorState
        var foundExitDoorId: String? = null // To store the ID from the template

        // 2. Check if we have already created and stored a state for this interior.
        if (!interiorStates.containsKey(house.id)) {
            println("No saved state for this house instance. Generating new interior from template.")
            val templateId = house.assignedRoomTemplateId
            if (templateId != null) {
                val template = roomTemplateManager.getTemplate(templateId)
                if (template != null) {
                    // Call the modified function and get the Pair result
                    val (newInteriorState, exitDoorId) = createInteriorFromTemplate(house, template)
                    interiorState = newInteriorState
                    foundExitDoorId = exitDoorId // Store the found ID

                    interiorCm.loadInitialBlocks(interiorState.blocks)
                } else {
                    println("Warning: House has template ID '$templateId' but template was not found. Creating empty room.")
                    interiorState = createNewEmptyInteriorFor(house)
                }
            } else {
                println("Warning: Unlocked house has no assigned room template. Creating empty room.")
                interiorState = createNewEmptyInteriorFor(house)
            }
            // Store the newly created state so we can return to it later.
            interiorStates[house.id] = interiorState

        } else {
            println("Loading existing state for house ID: ${house.id}")
            interiorState = interiorStates[house.id]!!
        }

        activeChunkManager = interiorCm
        loadInteriorState(interiorState)

        if (interiorState.isTimeFixed) {
            game.lightingManager.overrideTime(interiorState.fixedTimeProgress)
        } else {
            // Make sure the lighting manager is using the LIVE clock.
            game.lightingManager.clearTimeOverride()
        }
        game.shaderEffectManager.setRoomOverride(interiorState.savedShaderEffect)

        // If we generated a new interior from a template and found an exit door
        if (foundExitDoorId != null) {
            house.exitDoorId = foundExitDoorId
            println("Assigned exit door ID '${house.exitDoorId}' to house '${house.id}' from template.")
        }

        // 7. Finalize the transition
        particleSystem.clearAllParticles()

        currentInteriorId = house.id
        currentScene = SceneType.HOUSE_INTERIOR
        playerSystem.setPosition(interiorState.playerPosition)
        cameraManager.resetAndSnapToPlayer(interiorState.playerPosition, false)
        pendingHouse = null

        if (house.exitDoorId == null) {
            game.uiManager.enterExitDoorPlacementMode(house)
        }

        // Start fading back in to reveal the room.
        transitionSystem.startInTransition()
    }

    private fun getInteriorStateFor(houseId: String): InteriorState {
        return InteriorState(
            houseId = houseId,
            playerPosition = Vector3(0f, 2f, 0f) // Default spawn point
        )
    }

    fun getCurrentHouse(): GameHouse? {
        if (currentScene != SceneType.HOUSE_INTERIOR || currentInteriorId == null) {
            return null
        }
        return worldState?.houses?.find { it.id == currentInteriorId }
    }

    private fun completeTransitionToWorld() {
        println("Transition finished. Restoring world.")
        // Always resume time progression when returning to the outside world.
        game.lightingManager.clearTimeOverride()
        game.shaderEffectManager.clearRoomOverride()

        restoreWorldState()

        activeChunkManager = worldChunkManager

        currentScene = SceneType.WORLD
        currentInteriorId = null

        // Position the player at the saved exit position
        worldState?.let {
            playerSystem.setPosition(it.playerPosition)
            cameraManager.resetAndSnapToPlayer(it.playerPosition, false)
        }

        particleSystem.clearAllParticles()

        // Tell the transition system to fade back IN to the world scene
        transitionSystem.startInTransition()
    }

    private fun setSceneLights(lights: Map<Int, LightSource>) {
        // 1. Clear all existing lights from the manager
        val currentLightIds = game.lightingManager.getLightSources().keys.toList()
        currentLightIds.forEach { game.lightingManager.removeLightSource(it) }

        // 2. Add the lights for the new scene
        lights.values.forEach { light ->
            val instances = objectSystem.createLightSourceInstances(light)
            game.lightingManager.addLightSource(light, instances)
        }
    }

    fun getCurrentSceneId(): String {
        return if (currentScene == SceneType.HOUSE_INTERIOR) {
            currentInteriorId ?: "WORLD" // Fallback to WORLD if ID is null
        } else {
            "WORLD"
        }
    }

    // --- PRIVATE HELPER METHODS ---

    private fun saveWorldState() {
        println("Saving world state...")

        val worldCarPathNodes = Array<CarPathNode>().apply {
            addAll(*game.carPathSystem.nodes.values.filter { it.sceneId == "WORLD" }.toTypedArray())
        }
        val worldCharPathNodes = Array<CharacterPathNode>().apply {
            addAll(*game.characterPathSystem.nodes.values.filter { it.sceneId == "WORLD" }.toTypedArray())
        }

        // We create NEW arrays to snapshot the state, not just reference the active ones.
        worldState = WorldState(
            objects = Array(activeObjects),
            cars = Array(activeCars),
            houses = Array(activeHouses),
            entryPoints = Array(activeEntryPoints),
            items = Array(activeItems),
            enemies = Array(activeEnemies),
            npcs = Array(activeNPCs),
            spawners = Array(activeSpawners),
            playerPosition = playerSystem.getPosition(),
            cameraPosition = Vector3(),
            lights = game.lightingManager.getLightSources().toMutableMap(),
            bloodPools = Array(activeBloodPools),
            footprints = Array(activeFootprints),
            bones = Array(activeBones),
            fires = Array(fireSystem.activeFires),
            bullets = Array(activeBullets),
            throwables = Array(activeThrowables),
            teleporters = Array(teleporterSystem.activeTeleporters),
            carPathNodes = worldCarPathNodes,
            characterPathNodes = worldCharPathNodes
        )
        println("World state saved. Player at ${worldState!!.playerPosition}")
    }

    private fun restoreWorldState() {
        val state = worldState
        if (state == null) {
            println("ERROR: Cannot restore world state because it was never saved.")
            return
        }

        println("Restoring world state...")

        // Clear active data and load from the saved state
        clearActiveScene()

        activeObjects.addAll(state.objects)
        activeCars.addAll(state.cars)
        activeHouses.addAll(state.houses)
        activeEntryPoints.addAll(state.entryPoints)
        activeItems.addAll(state.items)
        activeEnemies.addAll(state.enemies)
        activeNPCs.addAll(state.npcs)
        activeSpawners.addAll(state.spawners)
        activeBloodPools.addAll(state.bloodPools)
        activeFootprints.addAll(state.footprints)
        activeBones.addAll(state.bones)
        fireSystem.activeFires.addAll(state.fires)
        activeBullets.addAll(state.bullets)
        activeThrowables.addAll(state.throwables)
        teleporterSystem.activeTeleporters.addAll(state.teleporters)

        game.carPathSystem.nodes.clear()
        state.carPathNodes.forEach { game.carPathSystem.nodes[it.id] = it }
        game.characterPathSystem.nodes.clear()
        state.characterPathNodes.forEach { game.characterPathSystem.nodes[it.id] = it }

        setSceneLights(state.lights)
    }

    fun clearActiveSceneForLoad() {
        // Clear all dynamic entities
        activeObjects.clear()
        activeCars.clear()
        activeItems.clear()
        activeEnemies.clear()
        activeNPCs.clear()
        activeSpawners.clear()
        activeBloodPools.clear()
        activeFootprints.clear()
        activeBones.clear()
        teleporterSystem.activeTeleporters.clear()

        activeChunkManager.getAllBlocks().forEach { removeBlock(it) }
        activeChunkManager.processDirtyChunks()
    }

    private fun saveCurrentInteriorState() {
        val id = currentInteriorId ?: return
        val currentState = interiorStates.getOrPut(id) { getInteriorStateFor(id) }
        println("Saving state for interior instance: $id")

        currentState.objects.clear(); currentState.objects.addAll(activeObjects)
        currentState.items.clear(); currentState.items.addAll(activeItems)
        currentState.interiors.clear(); currentState.interiors.addAll(activeInteriors)
        currentState.enemies.clear(); currentState.enemies.addAll(activeEnemies)
        currentState.npcs.clear(); currentState.npcs.addAll(activeNPCs)
        currentState.spawners.clear(); currentState.spawners.addAll(activeSpawners)
        currentState.teleporters.clear(); currentState.teleporters.addAll(teleporterSystem.activeTeleporters)
        currentState.bloodPools.clear(); currentState.bloodPools.addAll(activeBloodPools)
        currentState.footprints.clear(); currentState.footprints.addAll(activeFootprints)
        currentState.bones.clear(); currentState.bones.addAll(activeBones)
        currentState.fires.clear(); currentState.fires.addAll(fireSystem.activeFires)
        currentState.bullets.clear(); currentState.bullets.addAll(activeBullets)
        currentState.throwables.clear(); currentState.throwables.addAll(activeThrowables)
        currentState.playerPosition.set(playerSystem.getPosition())

        currentState.characterPathNodes.clear()
        currentState.characterPathNodes.addAll(
            *game.characterPathSystem.nodes.values.filter { it.sceneId == id }.toTypedArray()
        )

        currentState.lights.clear()
        currentState.lights.putAll(game.lightingManager.getLightSources())
    }

    private fun loadInteriorState(state: InteriorState) {
        println("Loading state for interior instance: ${state.houseId}")
        clearActiveScene()

        activeObjects.addAll(state.objects)
        activeItems.addAll(state.items)
        activeInteriors.addAll(state.interiors)
        activeEnemies.addAll(state.enemies)
        activeNPCs.addAll(state.npcs)
        activeSpawners.addAll(state.spawners)
        teleporterSystem.activeTeleporters.addAll(state.teleporters)
        activeBloodPools.addAll(state.bloodPools)
        activeFootprints.addAll(state.footprints)
        activeBones.addAll(state.bones)
        fireSystem.activeFires.addAll(state.fires)

        game.characterPathSystem.nodes.clear()
        state.characterPathNodes.forEach { game.characterPathSystem.nodes[it.id] = it }
        game.carPathSystem.nodes.clear()

        setSceneLights(state.lights)
    }

    private fun createInteriorFromTemplate(house: GameHouse, template: RoomTemplate): Pair<InteriorState, String?> {
        val newBlocks = Array<GameBlock>()
        val newObjects = Array<GameObject>()
        val newItems = Array<GameItem>()
        val newInteriors = Array<GameInterior>()
        val newEnemies = Array<GameEnemy>()
        val newNPCs = Array<GameNPC>()
        val newParticleSpawners = Array<GameSpawner>()
        val newLights = mutableMapOf<Int, LightSource>()
        val newTeleporters = Array<GameTeleporter>()
        val newCharacterPathNodes = Array<CharacterPathNode>()

        println("Building interior from template: ${template.name}")

        template.elements.forEach { element ->
            when (element.elementType) {
                RoomElementType.BLOCK -> {
                    element.blockType?.let { blockType ->
                        val gameBlock = blockSystem.createGameBlock(
                            type = blockType,
                            shape = element.shape ?: BlockShape.FULL_BLOCK, // Use saved shape or default to full block
                            position = element.position.cpy(),
                            geometryRotation = element.rotation,
                            textureRotation = element.textureRotation,
                            topTextureRotation = element.topTextureRotation
                        )
                        val finalBlock = gameBlock.copy(
                            cameraVisibility = element.cameraVisibility ?: CameraVisibility.ALWAYS_VISIBLE
                        )
                        newBlocks.add(finalBlock)
                    }
                }
                RoomElementType.OBJECT -> {
                    element.objectType?.let { objectType ->
                        if (objectType == ObjectType.LIGHT_SOURCE) {
                            val light = objectSystem.createLightSource(
                                position = element.position.cpy(),
                                intensity = element.lightIntensity ?: LightSource.DEFAULT_INTENSITY,
                                range = element.lightRange ?: LightSource.DEFAULT_RANGE,
                                color = element.lightColor ?: Color(LightSource.DEFAULT_COLOR_R, LightSource.DEFAULT_COLOR_G, LightSource.DEFAULT_COLOR_B, 1f),
                                flickerMode = element.flickerMode ?: FlickerMode.NONE,
                                loopOnDuration = element.loopOnDuration ?: 0.1f,
                                loopOffDuration = element.loopOffDuration ?: 0.2f
                            )
                            element.targetId?.let { savedId -> light.id = savedId.toInt() }
                            newLights[light.id] = light
                        } else {
                            // This is for regular, non-light-source objects
                            objectSystem.createGameObjectWithLight(objectType, element.position.cpy(), game.lightingManager)?.let { gameObject ->
                                element.targetId?.let { gameObject.id = it }
                                newObjects.add(gameObject)
                            }
                        }
                    }
                }
                RoomElementType.ITEM -> {
                    element.itemType?.let { itemType ->
                        itemSystem.createItem(element.position.cpy(), itemType)?.let { gameItem ->
                            element.targetId?.let { gameItem.id = it }
                            newItems.add(gameItem)
                        }
                    }
                }
                RoomElementType.INTERIOR -> {
                    element.interiorType?.let { interiorType ->
                        // Check if the placed element is a randomizer
                        if (interiorType.isRandomizer) {
                            // Determine which list of random items to use based on the randomizer type
                            val randomizableList = when (interiorType) {
                                InteriorType.INTERIOR_RANDOMIZER -> InteriorType.randomizableSmallItems
                                InteriorType.FURNITURE_RANDOMIZER_3D -> InteriorType.randomizableFurniture3D
                                else -> emptyList() // Fallback for other potential randomizers
                            }

                            val randomType = randomizableList.randomOrNull()

                            if (randomType != null) {
                                println("${interiorType.displayName} found at ${element.position}. Replacing with a random object: ${randomType.displayName}")
                                // Create an instance of the *newly chosen* type
                                interiorSystem.createInteriorInstance(randomType)?.let { gameInterior ->
                                    // Use the position, rotation, and scale from the original randomizer placeholder
                                    gameInterior.position.set(element.position)
                                    gameInterior.rotation = element.rotation
                                    gameInterior.scale.set(element.scale)
                                    gameInterior.updateTransform()
                                    newInteriors.add(gameInterior)
                                }
                            } else {
                                // This case happens if the randomizable list is empty.
                                println("Warning: ${interiorType.displayName} was placed, but its list of randomizable interiors is empty. Nothing was generated.")
                            }
                        } else {
                            // This is a normal, non-randomizer interior object. Process it as before.
                            interiorSystem.createInteriorInstance(interiorType)?.let { gameInterior ->
                                gameInterior.position.set(element.position)
                                gameInterior.rotation = element.rotation
                                gameInterior.scale.set(element.scale)
                                gameInterior.updateTransform()
                                newInteriors.add(gameInterior)
                            }
                        }
                    }
                }
                RoomElementType.ENEMY -> {
                    if (element.enemyType != null && element.enemyBehavior != null) {
                        // Create a full config using all the new properties from the RoomElement
                        val config = EnemySpawnConfig(
                            enemyType = element.enemyType,
                            behavior = element.enemyBehavior,
                            position = element.position.cpy(),
                            id = element.targetId,
                            assignedPathId = element.assignedPathId,
                            healthSetting = element.healthSetting ?: HealthSetting.FIXED_DEFAULT,
                            customHealthValue = element.customHealthValue ?: element.enemyType.baseHealth,
                            minRandomHealth = element.minRandomHealth ?: (element.enemyType.baseHealth * 0.8f),
                            maxRandomHealth = element.maxRandomHealth ?: (element.enemyType.baseHealth * 1.2f),
                            initialWeapon = element.initialWeapon ?: WeaponType.UNARMED,
                            ammoSpawnMode = element.ammoSpawnMode ?: AmmoSpawnMode.FIXED,
                            setAmmoValue = element.setAmmoValue ?: 30,
                            weaponCollectionPolicy = element.weaponCollectionPolicy ?: WeaponCollectionPolicy.CANNOT_COLLECT,
                            canCollectItems = element.canCollectItems ?: true
                        )
                        enemySystem.createEnemy(config)?.let { gameEnemy ->
                            // Add initial money if it was saved in the template
                            if ((element.enemyInitialMoney ?: 0) > 0) {
                                val moneyItem = itemSystem.createItem(Vector3.Zero, ItemType.MONEY_STACK)!!
                                moneyItem.value = element.enemyInitialMoney!!
                                gameEnemy.inventory.add(moneyItem)
                            }
                            newEnemies.add(gameEnemy)
                        }
                    }
                }
                RoomElementType.NPC -> {
                    if (element.npcType != null && element.npcBehavior != null) {
                        val config = NPCSpawnConfig(
                            npcType = element.npcType,
                            behavior = element.npcBehavior,
                            position = element.position.cpy(),
                            id = element.targetId,
                            isHonest = element.npcIsHonest ?: true, // Provide defaults
                            canCollectItems = element.npcCanCollectItems ?: true,
                            pathFollowingStyle = element.pathFollowingStyle ?: PathFollowingStyle.CONTINUOUS
                        )
                        npcSystem.createNPC(config, element.npcRotation)?.let { gameNPC ->
                            // Load the NPC's starting inventory from the template
                            element.npcInventory?.forEach { itemData ->
                                itemSystem.createItem(gameNPC.position, itemData.itemType)?.let { gameItem ->
                                    gameItem.ammo = itemData.ammo
                                    gameItem.value = itemData.value
                                    gameNPC.inventory.add(gameItem)
                                }
                            }
                            newNPCs.add(gameNPC)
                        }
                    }
                }
                RoomElementType.PARTICLE_SPAWNER -> {
                    val spawnerGameObject = objectSystem.createGameObjectWithLight(ObjectType.SPAWNER, element.position.cpy())
                    if (spawnerGameObject != null) {
                        spawnerGameObject.debugInstance?.transform?.setTranslation(element.position)
                        val newSpawner = GameSpawner(
                            id = element.targetId ?: UUID.randomUUID().toString(),
                            position = element.position.cpy(),
                            gameObject = spawnerGameObject
                        )

                        // Load all saved spawner properties from the element
                        newSpawner.spawnerType = element.spawnerType ?: newSpawner.spawnerType
                        newSpawner.spawnerMode = element.spawnerMode ?: newSpawner.spawnerMode
                        newSpawner.spawnInterval = element.spawnerInterval ?: newSpawner.spawnInterval
                        newSpawner.minSpawnRange = element.spawnerMinRange ?: newSpawner.minSpawnRange
                        newSpawner.maxSpawnRange = element.spawnerMaxRange ?: newSpawner.maxSpawnRange
                        newSpawner.spawnOnlyWhenPreviousIsGone = element.spawnOnlyWhenPreviousIsGone ?: false

                        // Particle properties
                        newSpawner.particleEffectType = element.particleEffectType ?: newSpawner.particleEffectType
                        newSpawner.minParticles = element.spawnerMinParticles ?: newSpawner.minParticles
                        newSpawner.maxParticles = element.spawnerMaxParticles ?: newSpawner.maxParticles

                        // Item properties
                        newSpawner.itemType = element.spawnerItemType ?: newSpawner.itemType
                        newSpawner.minItems = element.spawnerMinItems ?: newSpawner.minItems
                        newSpawner.maxItems = element.spawnerMaxItems ?: newSpawner.maxItems

                        // Weapon properties
                        newSpawner.weaponItemType = element.spawnerWeaponItemType ?: newSpawner.weaponItemType

                        // Load the ammo settings from the template element
                        newSpawner.ammoSpawnMode = element.ammoSpawnMode ?: newSpawner.ammoSpawnMode
                        newSpawner.setAmmoValue = element.setAmmoValue ?: newSpawner.setAmmoValue
                        newSpawner.randomMinAmmo = element.randomMinAmmo ?: newSpawner.randomMinAmmo
                        newSpawner.randomMaxAmmo = element.randomMaxAmmo ?: newSpawner.randomMaxAmmo

                        // NPC Spawner properties
                        newSpawner.npcType = element.spawnerNpcType ?: newSpawner.npcType
                        newSpawner.npcBehavior = element.npcBehavior ?: newSpawner.npcBehavior
                        newSpawner.npcIsHonest = element.npcIsHonest ?: newSpawner.npcIsHonest
                        newSpawner.npcCanCollectItems = element.npcCanCollectItems ?: newSpawner.npcCanCollectItems

                        // Car Spawner properties
                        newSpawner.carType = element.spawnerCarType ?: newSpawner.carType
                        newSpawner.carIsLocked = element.spawnerCarIsLocked ?: newSpawner.carIsLocked
                        newSpawner.carDriverType = element.spawnerCarDriverType ?: newSpawner.carDriverType
                        newSpawner.carEnemyDriverType = element.spawnerCarEnemyDriverType ?: newSpawner.carEnemyDriverType
                        newSpawner.carNpcDriverType = element.spawnerCarNpcDriverType ?: newSpawner.carNpcDriverType
                        newSpawner.carSpawnDirection = element.spawnerCarSpawnDirection ?: newSpawner.carSpawnDirection

                        newSpawner.enemyType = element.enemyType ?: newSpawner.enemyType
                        newSpawner.enemyBehavior = element.enemyBehavior ?: newSpawner.enemyBehavior
                        newSpawner.enemyHealthSetting = element.healthSetting ?: newSpawner.enemyHealthSetting
                        newSpawner.enemyCustomHealth = element.customHealthValue ?: newSpawner.enemyCustomHealth
                        newSpawner.enemyMinHealth = element.minRandomHealth ?: newSpawner.enemyMinHealth
                        newSpawner.enemyMaxHealth = element.maxRandomHealth ?: newSpawner.enemyMaxHealth
                        newSpawner.enemyInitialWeapon = element.initialWeapon ?: newSpawner.enemyInitialWeapon
                        newSpawner.enemyWeaponCollectionPolicy = element.weaponCollectionPolicy ?: newSpawner.enemyWeaponCollectionPolicy
                        newSpawner.enemyCanCollectItems = element.canCollectItems ?: newSpawner.enemyCanCollectItems
                        newSpawner.enemyInitialMoney = element.enemyInitialMoney ?: newSpawner.enemyInitialMoney

                        newParticleSpawners.add(newSpawner)
                    }
                }
                RoomElementType.TELEPORTER -> {
                    if (element.teleporterId != null) {
                        val gameObject = objectSystem.createGameObjectWithLight(ObjectType.TELEPORTER, element.position.cpy())
                        if (gameObject != null) {
                            gameObject.modelInstance.transform.setTranslation(element.position)
                            gameObject.debugInstance?.transform?.setTranslation(element.position)

                            val newTeleporter = GameTeleporter(
                                id = element.teleporterId, // Use saved ID to preserve links
                                gameObject = gameObject,
                                name = element.teleporterName ?: "Teleporter"
                                // Link is set after all teleporters are created
                            )
                            newTeleporters.add(newTeleporter)
                        }
                    }
                }
                RoomElementType.FIRE -> {
                    // Temporarily configure the fire system with the saved properties
                    val originalLooping = fireSystem.nextFireIsLooping
                    val originalFadesOut = fireSystem.nextFireFadesOut
                    val originalLifetime = fireSystem.nextFireLifetime
                    val originalCanBeExtinguished = fireSystem.nextFireCanBeExtinguished
                    val originalDealsDamage = fireSystem.nextFireDealsDamage
                    val originalDamagePerSecond = fireSystem.nextFireDamagePerSecond
                    val originalDamageRadius = fireSystem.nextFireDamageRadius
                    val originalMinScale = fireSystem.nextFireMinScale
                    val originalMaxScale = fireSystem.nextFireMaxScale

                    fireSystem.nextFireIsLooping = element.isLooping ?: true
                    fireSystem.nextFireFadesOut = element.fadesOut ?: false
                    fireSystem.nextFireLifetime = element.lifetime ?: 20f
                    fireSystem.nextFireCanBeExtinguished = element.canBeExtinguished ?: true
                    fireSystem.nextFireDealsDamage = element.dealsDamage ?: true
                    fireSystem.nextFireDamagePerSecond = element.damagePerSecond ?: 10f
                    fireSystem.nextFireDamageRadius = element.damageRadius ?: 5f
                    fireSystem.nextFireMinScale = element.scale.x
                    fireSystem.nextFireMaxScale = element.scale.x

                    // Create the fire
                    val newFire = fireSystem.addFire(
                        position = element.position.cpy(),
                        objectSystem = objectSystem,
                        lightingManager = game.lightingManager,
                        id = element.targetId ?: UUID.randomUUID().toString()
                    )
                    if (newFire != null) {
                        // Add its underlying game object to the list of objects for this new room state
                        newObjects.add(newFire.gameObject)
                    }

                    // Restore the original settings to the fire system
                    fireSystem.nextFireIsLooping = originalLooping
                    fireSystem.nextFireFadesOut = originalFadesOut
                    fireSystem.nextFireLifetime = originalLifetime
                    fireSystem.nextFireCanBeExtinguished = originalCanBeExtinguished
                    fireSystem.nextFireDealsDamage = originalDealsDamage
                    fireSystem.nextFireDamagePerSecond = originalDamagePerSecond
                    fireSystem.nextFireDamageRadius = originalDamageRadius
                    fireSystem.nextFireMinScale = originalMinScale
                    fireSystem.nextFireMaxScale = originalMaxScale
                }
                RoomElementType.CHARACTER_PATH_NODE -> {
                    if (element.pathNodeId != null) {
                        val node = CharacterPathNode(
                            id = element.pathNodeId,
                            position = element.position.cpy(),
                            nextNodeId = element.nextNodeId,
                            previousNodeId = element.previousNodeId,
                            debugInstance = ModelInstance(game.characterPathSystem.nodeModel),
                            sceneId = house.id, // The new sceneId is this house's ID
                            isOneWay = element.isOneWay,
                            isMissionOnly = element.isMissionOnly,
                            missionId = element.missionId
                        )
                        newCharacterPathNodes.add(node)
                    }
                }
            }
        }
        // After all blocks are created, run face culling on the entire collection
        faceCullingSystem.recalculateAllFaces(newBlocks)

        var foundExitDoorId: String? = null
        // Check if the template has a valid saved door position.
        if (template.exitDoorPosition.len2() > 0.1f) { // Use len2 for efficiency, check > 0.1f to avoid floating point issues
            // Find the door in the newly created interiors that is closest to the saved position.
            val closestDoor = newInteriors
                .filter { it.interiorType == InteriorType.DOOR_INTERIOR }
                .minByOrNull { it.position.dst2(template.exitDoorPosition) }

            if (closestDoor != null) {
                foundExitDoorId = closestDoor.id
                println("Template specified an exit door. Found closest match with new ID: $foundExitDoorId")
            } else {
                println("Template has an exit door position, but no door object was found in the template's elements.")
            }
        }

        // Link teleporters now that they all exist
        template.elements.filter { it.elementType == RoomElementType.TELEPORTER }.forEach { element ->
            val sourceTeleporter = newTeleporters.find { it.id == element.teleporterId }
            sourceTeleporter?.linkedTeleporterId = element.linkedTeleporterId
        }

        val interiorState = InteriorState(
            houseId = house.id,
            blocks = newBlocks, // This list will be loaded into the chunk manager
            objects = newObjects,
            items = newItems,
            interiors = newInteriors,
            enemies = newEnemies,
            npcs = newNPCs,
            spawners = newParticleSpawners,
            teleporters = newTeleporters,
            playerPosition = template.entrancePosition.cpy(),
            isTimeFixed = template.isTimeFixed,
            fixedTimeProgress = template.fixedTimeProgress,
            lights = newLights,
            savedShaderEffect = template.savedShaderEffect,
            sourceTemplateId = template.id,
            characterPathNodes = newCharacterPathNodes
        )
        interiorState.sourceTemplateId = template.id

        return Pair(interiorState, foundExitDoorId)
    }

    private fun createNewEmptyInteriorFor(house: GameHouse): InteriorState {
        println("No saved state for this house instance. Creating a new, TRULY EMPTY interior.")

        // Create the state with empty lists of objects.
        val newState = InteriorState(
            houseId = house.id,
            blocks = Array(),
            objects = Array(),
            items = Array(),
            interiors = Array(),
            enemies = Array(),
            playerPosition = Vector3(0f, 8f, 0f)
        )

        interiorStates[house.id] = newState
        println("New interior created and saved for ${house.id}")
        return newState
    }

    fun saveCurrentInteriorAsTemplate(id: String, name: String, category: String, isTimeFixed: Boolean, fixedTimeProgress: Float, shaderEffect: ShaderEffect): Boolean {
        if (currentScene != SceneType.HOUSE_INTERIOR) {
            println("Error: Must be in an interior to save it as a template.")
            return false
        }
        // 1. Determine the Entrance Position
        val spawnPointObject = activeInteriors.find { it.interiorType == InteriorType.PLAYER_SPAWNPOINT }

        val entrancePosition: Vector3

        if (spawnPointObject != null) {
            // A spawn point was found! Use its position.
            entrancePosition = spawnPointObject.position.cpy()
            println("Found Player Spawnpoint. Using its position for template entrance: $entrancePosition")
        } else {
            // No spawn point found. Fall back to the player's current position.
            entrancePosition = playerSystem.getPosition()
            println("No Player Spawnpoint found. Using player's current position as fallback: $entrancePosition")
        }

        // 2. Determine the Exit Door Position (this logic remains the same)
        val currentHouse = getCurrentHouse()
        var doorPosition = Vector3() // Default to (0,0,0)

        if (currentHouse != null && currentHouse.exitDoorId != null) {
            // Find the actual door object in the scene using its ID
            val exitDoorObject = activeInteriors.find { it.id == currentHouse.exitDoorId }
            if (exitDoorObject != null) {
                // We found the door! Save its position.
                doorPosition = exitDoorObject.position.cpy()
                println("Found exit door at ${doorPosition}. Saving this position to the template.")
            }
        }

        println("Converting current room to template with ID: $id")
        val elements = mutableListOf<RoomElement>()

        // Convert active blocks to RoomElements
        activeChunkManager.getAllBlocks().forEach { block ->
            elements.add(RoomElement(
                position = block.position.cpy(),
                elementType = RoomElementType.BLOCK,
                blockType = block.blockType,
                shape = block.shape,
                rotation = block.rotationY,
                textureRotation = block.textureRotationY,
                topTextureRotation = block.topTextureRotationY,
                cameraVisibility = block.cameraVisibility
            ))
        }

        // Convert active objects to RoomElements
        activeObjects.forEach { obj ->
            if (obj.objectType != ObjectType.FIRE_SPREAD) {
                elements.add(RoomElement(
                    position = obj.position.cpy(),
                    elementType = RoomElementType.OBJECT,
                    objectType = obj.objectType,
                    targetId = obj.id,
                    rotation = 0f // Assuming objects don't rotate for now
                ))
            }
        }

        // Convert active items to RoomElements
        activeItems.forEach { item ->
            elements.add(RoomElement(
                position = item.position.cpy(),
                elementType = RoomElementType.ITEM,
                itemType = item.itemType,
                targetId = item.id
            ))
        }

        // Convert active interiors to RoomElements
        activeInteriors.forEach { interior ->
            elements.add(RoomElement(
                position = interior.position.cpy(),
                elementType = RoomElementType.INTERIOR,
                interiorType = interior.interiorType,
                rotation = interior.rotation,
                scale = interior.scale.cpy()
            ))
        }

        // Convert active enemies to RoomElements
        activeEnemies.forEach { enemy ->
            // Calculate starting money from inventory
            val startingMoney = enemy.inventory
                .filter { it.itemType == ItemType.MONEY_STACK }
                .sumOf { it.value }

            elements.add(RoomElement(
                position = enemy.position.cpy(),
                elementType = RoomElementType.ENEMY,
                enemyType = enemy.enemyType,
                enemyBehavior = enemy.behaviorType,
                assignedPathId = enemy.assignedPathId,
                targetId = enemy.id,
                healthSetting = HealthSetting.FIXED_CUSTOM,
                customHealthValue = enemy.health,
                initialWeapon = enemy.equippedWeapon,
                ammoSpawnMode = AmmoSpawnMode.SET, // We save the exact ammo count
                setAmmoValue = enemy.weapons.getOrDefault(enemy.equippedWeapon, 0) + enemy.currentMagazineCount,
                weaponCollectionPolicy = enemy.weaponCollectionPolicy,
                canCollectItems = enemy.canCollectItems,
                enemyInitialMoney = startingMoney
            ))
        }

        // Convert active NPCs to RoomElements
        activeNPCs.forEach { npc ->
            // Convert the NPC's live inventory into savable ItemData
            val inventoryData = npc.inventory.map { gameItem ->
                ItemData(gameItem.id, gameItem.itemType, gameItem.position, gameItem.ammo, gameItem.value)
            }

            elements.add(RoomElement(
                position = npc.position.cpy(),
                elementType = RoomElementType.NPC,
                npcType = npc.npcType,
                npcBehavior = npc.behaviorType,
                npcRotation = npc.facingRotationY,
                pathFollowingStyle = npc.pathFollowingStyle,
                targetId = npc.id,
                npcIsHonest = npc.isHonest,
                npcCanCollectItems = npc.canCollectItems,
                npcInventory = inventoryData
            ))
        }

        activeSpawners.forEach { spawner ->
            elements.add(RoomElement(
                position = spawner.position.cpy(),
                elementType = RoomElementType.PARTICLE_SPAWNER,
                targetId = spawner.id,

                // General Spawner Settings
                spawnerType = spawner.spawnerType,
                spawnerMode = spawner.spawnerMode,
                spawnerInterval = spawner.spawnInterval,
                spawnerMinRange = spawner.minSpawnRange,
                spawnerMaxRange = spawner.maxSpawnRange,
                spawnOnlyWhenPreviousIsGone = spawner.spawnOnlyWhenPreviousIsGone,

                // Particle Spawner
                particleEffectType = spawner.particleEffectType,
                spawnerMinParticles = spawner.minParticles,
                spawnerMaxParticles = spawner.maxParticles,

                // Item Spawner
                spawnerItemType = spawner.itemType,
                spawnerMinItems = spawner.minItems,
                spawnerMaxItems = spawner.maxItems,

                // Weapon Spawner
                spawnerWeaponItemType = spawner.weaponItemType,

                // Save the new ammo settings
                ammoSpawnMode = spawner.ammoSpawnMode,
                setAmmoValue = spawner.setAmmoValue,
                randomMinAmmo = spawner.randomMinAmmo,
                randomMaxAmmo = spawner.randomMaxAmmo,

                // NPC Spawner
                spawnerNpcType = spawner.npcType,
                npcBehavior = spawner.npcBehavior,
                npcIsHonest = spawner.npcIsHonest,
                npcCanCollectItems = spawner.npcCanCollectItems,

                // Car Spawner
                spawnerCarType = spawner.carType,
                spawnerCarIsLocked = spawner.carIsLocked,
                spawnerCarDriverType = spawner.carDriverType,
                spawnerCarEnemyDriverType = spawner.carEnemyDriverType,
                spawnerCarNpcDriverType = spawner.carNpcDriverType,
                spawnerCarSpawnDirection = spawner.carSpawnDirection,

                enemyType = spawner.enemyType,
                enemyBehavior = spawner.enemyBehavior,
                healthSetting = spawner.enemyHealthSetting,
                customHealthValue = spawner.enemyCustomHealth,
                minRandomHealth = spawner.enemyMinHealth,
                maxRandomHealth = spawner.enemyMaxHealth,
                initialWeapon = spawner.enemyInitialWeapon,
                weaponCollectionPolicy = spawner.enemyWeaponCollectionPolicy,
                canCollectItems = spawner.enemyCanCollectItems,
                enemyInitialMoney = spawner.enemyInitialMoney
            ))
        }

        game.lightingManager.getLightSources().values.forEach { light ->
            if (light.flickerMode == FlickerMode.NONE || light.flickerMode == FlickerMode.LOOP) {
                elements.add(RoomElement(
                    position = light.position.cpy(),
                    elementType = RoomElementType.OBJECT,
                    objectType = ObjectType.LIGHT_SOURCE,
                    targetId = light.id.toString(),
                    lightColor = light.color.cpy(),
                    lightIntensity = light.baseIntensity,
                    lightRange = light.range,
                    flickerMode = light.flickerMode,
                    loopOnDuration = light.loopOnDuration,
                    loopOffDuration = light.loopOffDuration
                ))
            }
        }

        teleporterSystem.activeTeleporters.forEach { teleporter ->
            elements.add(RoomElement(
                position = teleporter.gameObject.position.cpy(),
                elementType = RoomElementType.TELEPORTER,
                teleporterId = teleporter.id,
                linkedTeleporterId = teleporter.linkedTeleporterId,
                teleporterName = teleporter.name
            ))
        }

        fireSystem.activeFires.forEach { fire ->
            elements.add(RoomElement(
                position = fire.gameObject.position.cpy(),
                elementType = RoomElementType.FIRE,
                targetId = fire.id,
                isLooping = fire.isLooping,
                fadesOut = fire.fadesOut,
                lifetime = fire.lifetime,
                canBeExtinguished = fire.canBeExtinguished,
                dealsDamage = fire.dealsDamage,
                damagePerSecond = fire.damagePerSecond,
                damageRadius = fire.damageRadius,
                scale = Vector3(fire.initialScale, 1f, 1f)
            ))
        }

        // ADDED: Convert active CHARACTER PATH NODES to RoomElements
        // We must filter to only get nodes that belong to the current room!
        val interiorId = currentInteriorId ?: return false
        game.characterPathSystem.nodes.values.filter { it.sceneId == interiorId }.forEach { node ->
            elements.add(RoomElement(
                position = node.position.cpy(),
                elementType = RoomElementType.CHARACTER_PATH_NODE,
                pathNodeId = node.id,
                nextNodeId = node.nextNodeId,
                previousNodeId = node.previousNodeId,
                sceneId = node.sceneId,
                isOneWay = node.isOneWay,
                isMissionOnly = node.isMissionOnly,
                missionId = node.missionId
            ))
        }

        val newTemplate = RoomTemplate(
            id = id,
            name = name,
            description = "A user-created room.",
            size = Vector3(20f, 8f, 20f),
            elements = elements,
            entrancePosition = entrancePosition,
            exitTriggerPosition = playerSystem.getPosition().add(0f, 0f, 1f),
            category = category,
            exitDoorPosition = doorPosition,
            isTimeFixed = isTimeFixed,
            fixedTimeProgress = fixedTimeProgress,
            savedShaderEffect = shaderEffect
        )

        roomTemplateManager.addTemplate(newTemplate)
        println("Successfully saved room as template '$id'!")
        game.uiManager.refreshHouseRoomList()
        return true
    }

    /**
     * Wipes the current interior and rebuilds it from a saved template.
     */
    fun loadTemplateIntoCurrentInterior(templateId: String) {
        if (currentScene != SceneType.HOUSE_INTERIOR) {
            println("Error: Must be in an interior to load a template.")
            return
        }

        val template = roomTemplateManager.getTemplate(templateId)
        if (template == null) {
            println("Error: Template with ID '$templateId' not found.")
            return
        }

        println("Loading template '$templateId' into current room...")
        clearActiveScene()

        activeChunkManager.dispose()

        val newBlocksForChunkManager = Array<GameBlock>()
        val tempTeleporters = Array<GameTeleporter>()
        val tempPathNodes = mutableMapOf<String, CharacterPathNode>()

        // Build the scene from the template's elements
        template.elements.forEach { element ->
            when (element.elementType) {
                RoomElementType.BLOCK -> {
                    element.blockType?.let { blockType ->
                        val gameBlock = blockSystem.createGameBlock(
                            type = blockType,
                            shape = element.shape ?: BlockShape.FULL_BLOCK,
                            position = element.position.cpy(),
                            geometryRotation = element.rotation,
                            textureRotation = element.textureRotation,
                            topTextureRotation = element.topTextureRotation
                        )
                        val finalBlock = gameBlock.copy(
                            cameraVisibility = element.cameraVisibility ?: CameraVisibility.ALWAYS_VISIBLE
                        )
                        newBlocksForChunkManager.add(finalBlock)
                    }
                }
                RoomElementType.OBJECT -> {
                    element.objectType?.let { objectType ->
                        if (objectType == ObjectType.LIGHT_SOURCE) {
                            val light = objectSystem.createLightSource(
                                position = element.position.cpy(),
                                intensity = element.lightIntensity ?: LightSource.DEFAULT_INTENSITY,
                                range = element.lightRange ?: LightSource.DEFAULT_RANGE,
                                color = element.lightColor ?: Color(LightSource.DEFAULT_COLOR_R, LightSource.DEFAULT_COLOR_G, LightSource.DEFAULT_COLOR_B, 1f),
                                flickerMode = element.flickerMode ?: FlickerMode.NONE,
                                loopOnDuration = element.loopOnDuration ?: 0.1f,
                                loopOffDuration = element.loopOffDuration ?: 0.2f
                            )
                            val instances = objectSystem.createLightSourceInstances(light)
                            game.lightingManager.addLightSource(light, instances)
                        } else {
                            objectSystem.createGameObjectWithLight(objectType, element.position.cpy(), lightingManager = game.lightingManager)?.let { gameObject ->
                                activeObjects.add(gameObject)
                            }
                        }
                    }
                }
                RoomElementType.ITEM -> {
                    element.itemType?.let { itemType ->
                        itemSystem.createItem(element.position.cpy(), itemType)?.let { gameItem ->
                            activeItems.add(gameItem)
                        }
                    }
                }
                RoomElementType.INTERIOR -> {
                    element.interiorType?.let { interiorType ->
                        interiorSystem.createInteriorInstance(interiorType)?.let { gameInterior ->
                            gameInterior.position.set(element.position)
                            gameInterior.rotation = element.rotation
                            gameInterior.scale.set(element.scale)
                            gameInterior.updateTransform()
                            activeInteriors.add(gameInterior)
                        }
                    }
                }
                RoomElementType.ENEMY -> {
                    if (element.enemyType != null && element.enemyBehavior != null) {
                        // --- CORRECTED CODE ---
                        val config = EnemySpawnConfig(
                            enemyType = element.enemyType,
                            behavior = element.enemyBehavior,
                            position = element.position.cpy(),
                            id = element.targetId
                        )
                        enemySystem.createEnemy(config)?.let { gameEnemy ->
                            activeEnemies.add(gameEnemy)
                        }
                        // --- END CORRECTION ---
                    }
                }
                RoomElementType.NPC -> {
                    if (element.npcType != null && element.npcBehavior != null) {
                        val config = NPCSpawnConfig(
                            npcType = element.npcType,
                            behavior = element.npcBehavior,
                            position = element.position.cpy(),
                            id = element.targetId
                        )
                        npcSystem.createNPC(config, element.npcRotation)?.let { gameNPC ->
                            activeNPCs.add(gameNPC)
                        }
                    }
                }
                RoomElementType.PARTICLE_SPAWNER -> {
                    val spawnerGameObject = objectSystem.createGameObjectWithLight(ObjectType.SPAWNER, element.position.cpy())
                    if (spawnerGameObject != null) {
                        spawnerGameObject.debugInstance?.transform?.setTranslation(element.position)
                        val newSpawner = GameSpawner(
                            position = element.position.cpy(),
                            gameObject = spawnerGameObject
                        )

                        // NEW: Load all saved spawner properties from the element
                        newSpawner.spawnerType = element.spawnerType ?: newSpawner.spawnerType
                        newSpawner.spawnInterval = element.spawnerInterval ?: newSpawner.spawnInterval
                        newSpawner.minSpawnRange = element.spawnerMinRange ?: newSpawner.minSpawnRange
                        newSpawner.maxSpawnRange = element.spawnerMaxRange ?: newSpawner.maxSpawnRange

                        newSpawner.particleEffectType = element.particleEffectType ?: newSpawner.particleEffectType
                        newSpawner.minParticles = element.spawnerMinParticles ?: newSpawner.minParticles
                        newSpawner.maxParticles = element.spawnerMaxParticles ?: newSpawner.maxParticles

                        newSpawner.itemType = element.spawnerItemType ?: newSpawner.itemType
                        newSpawner.minItems = element.spawnerMinItems ?: newSpawner.minItems
                        newSpawner.maxItems = element.spawnerMaxItems ?: newSpawner.maxItems

                        newSpawner.weaponItemType = element.spawnerWeaponItemType ?: newSpawner.weaponItemType

                        // Load the ammo settings from the template element
                        newSpawner.ammoSpawnMode = element.ammoSpawnMode ?: newSpawner.ammoSpawnMode
                        newSpawner.setAmmoValue = element.setAmmoValue ?: newSpawner.setAmmoValue
                        newSpawner.randomMinAmmo = element.randomMinAmmo ?: newSpawner.randomMinAmmo
                        newSpawner.randomMaxAmmo = element.randomMaxAmmo ?: newSpawner.randomMaxAmmo
                        newSpawner.spawnOnlyWhenPreviousIsGone = element.spawnOnlyWhenPreviousIsGone ?: false

                        activeSpawners.add(newSpawner)
                    }
                }
                RoomElementType.TELEPORTER -> {
                    if (element.teleporterId != null) {
                        val gameObject = objectSystem.createGameObjectWithLight(ObjectType.TELEPORTER, element.position.cpy())
                        if (gameObject != null) {
                            gameObject.modelInstance.transform.setTranslation(element.position)
                            gameObject.debugInstance?.transform?.setTranslation(element.position)
                            val newTeleporter = GameTeleporter(
                                id = element.teleporterId,
                                gameObject = gameObject,
                                name = element.teleporterName ?: "Teleporter"
                            )
                            tempTeleporters.add(newTeleporter) // Add to temp list first
                        }
                    }
                }
                RoomElementType.FIRE -> {
                    // Temporarily configure the fire system with the saved properties
                    val originalLooping = fireSystem.nextFireIsLooping
                    val originalFadesOut = fireSystem.nextFireFadesOut
                    val originalLifetime = fireSystem.nextFireLifetime
                    val originalCanBeExtinguished = fireSystem.nextFireCanBeExtinguished
                    val originalDealsDamage = fireSystem.nextFireDealsDamage
                    val originalDamagePerSecond = fireSystem.nextFireDamagePerSecond
                    val originalDamageRadius = fireSystem.nextFireDamageRadius
                    val originalMinScale = fireSystem.nextFireMinScale
                    val originalMaxScale = fireSystem.nextFireMaxScale

                    fireSystem.nextFireIsLooping = element.isLooping ?: true
                    fireSystem.nextFireFadesOut = element.fadesOut ?: false
                    fireSystem.nextFireLifetime = element.lifetime ?: 20f
                    fireSystem.nextFireCanBeExtinguished = element.canBeExtinguished ?: true
                    fireSystem.nextFireDealsDamage = element.dealsDamage ?: true
                    fireSystem.nextFireDamagePerSecond = element.damagePerSecond ?: 10f
                    fireSystem.nextFireDamageRadius = element.damageRadius ?: 5f
                    fireSystem.nextFireMinScale = element.scale.x
                    fireSystem.nextFireMaxScale = element.scale.x

                    // Create the fire
                    val newFire = fireSystem.addFire(element.position.cpy(), objectSystem, game.lightingManager)
                    if (newFire != null) {
                        // Add its underlying game object to the active list for this scene
                        activeObjects.add(newFire.gameObject)
                    }

                    // Restore the original settings to the fire system
                    fireSystem.nextFireIsLooping = originalLooping
                    fireSystem.nextFireFadesOut = originalFadesOut
                    fireSystem.nextFireLifetime = originalLifetime
                    fireSystem.nextFireCanBeExtinguished = originalCanBeExtinguished
                    fireSystem.nextFireDealsDamage = originalDealsDamage
                    fireSystem.nextFireDamagePerSecond = originalDamagePerSecond
                    fireSystem.nextFireDamageRadius = originalDamageRadius
                    fireSystem.nextFireMinScale = originalMinScale
                    fireSystem.nextFireMaxScale = originalMaxScale
                }
                RoomElementType.CHARACTER_PATH_NODE -> {
                    if (element.pathNodeId != null) {
                        val node = CharacterPathNode(
                            id = element.pathNodeId,
                            position = element.position.cpy(),
                            nextNodeId = element.nextNodeId,
                            previousNodeId = element.previousNodeId,
                            debugInstance = ModelInstance(game.characterPathSystem.nodeModel),
                            sceneId = currentInteriorId ?: "UNKNOWN_INTERIOR", // Use the current room's ID
                            isOneWay = element.isOneWay,
                            isMissionOnly = element.isMissionOnly,
                            missionId = element.missionId
                        )
                        tempPathNodes[node.id] = node // Add to temp map
                    }
                }
            }
        }

        // ADDED: Add all the newly created path nodes to the active system
        game.characterPathSystem.nodes.putAll(tempPathNodes)
        // Link the newly created teleporters
        template.elements.filter { it.elementType == RoomElementType.TELEPORTER }.forEach { element ->
            val sourceTeleporter = tempTeleporters.find { it.id == element.teleporterId }
            sourceTeleporter?.linkedTeleporterId = element.linkedTeleporterId
        }
        teleporterSystem.activeTeleporters.addAll(tempTeleporters)

        // After loading all blocks, run face culling on the entire active collection
        activeChunkManager.loadInitialBlocks(newBlocksForChunkManager)

        // Move player to the template's entrance
        playerSystem.setPosition(template.entrancePosition)
        cameraManager.resetAndSnapToPlayer(template.entrancePosition, false)
    }

    fun getCurrentInteriorState(): InteriorState? {
        val id = currentInteriorId ?: return null
        return interiorStates[id]
    }

    private fun clearActiveScene() {
        activeObjects.clear()
        activeCars.clear()
        activeHouses.clear()
        activeItems.clear()
        activeInteriors.clear()
        activeEnemies.clear()
        activeNPCs.clear()
        activeSpawners.clear()
        activeEntryPoints.clear()
        teleporterSystem.activeTeleporters.clear()
        activeBloodPools.clear()
        activeFootprints.clear()
        activeBones.clear()
        fireSystem.activeFires.clear()
        activeBullets.clear()
        activeThrowables.clear()
        game.carPathSystem.nodes.clear()
        game.characterPathSystem.nodes.clear()


        // Also clear any active lights from the lighting manager
        val currentLightIds = game.lightingManager.getLightSources().keys.toList()
        currentLightIds.forEach { game.lightingManager.removeLightSource(it) }
    }
}

data class WorldState(
    val objects: Array<GameObject>,
    val cars: Array<GameCar>,
    val houses: Array<GameHouse>,
    val entryPoints: Array<GameEntryPoint>,
    val items: Array<GameItem>,
    val enemies: Array<GameEnemy>,
    val npcs: Array<GameNPC>,
    val spawners: Array<GameSpawner>,
    val playerPosition: Vector3,
    val cameraPosition: Vector3,
    val lights: MutableMap<Int, LightSource>,
    val bloodPools: Array<BloodPool>,
    val footprints: Array<GameFootprint>,
    val bones: Array<GameBone>,
    val fires: Array<GameFire> = Array(),
    val bullets: Array<Bullet> = Array(),
    val throwables: Array<ThrowableEntity> = Array(),
    val teleporters: Array<GameTeleporter> = Array(),
    val carPathNodes: Array<CarPathNode> = Array(),
    val characterPathNodes: Array<CharacterPathNode> = Array()
)

// The state for a single house interior.
data class InteriorState(
    val houseId: String,
    val blocks: Array<GameBlock> = Array(),
    val objects: Array<GameObject> = Array(),
    val items: Array<GameItem> = Array(),
    val interiors: Array<GameInterior> = Array(),
    val enemies: Array<GameEnemy> = Array(),
    val npcs: Array<GameNPC> = Array(),
    val spawners: Array<GameSpawner> = Array(),
    val teleporters: Array<GameTeleporter> = Array(),
    var playerPosition: Vector3,
    var isTimeFixed: Boolean = false,
    var fixedTimeProgress: Float = 0.5f,
    val lights: MutableMap<Int, LightSource> = mutableMapOf(),
    var savedShaderEffect: ShaderEffect = ShaderEffect.NONE,
    var sourceTemplateId: String? = null,
    val bloodPools: Array<BloodPool> = Array(),
    val footprints: Array<GameFootprint> = Array(),
    val bones: Array<GameBone> = Array(),
    val fires: Array<GameFire> = Array(),
    val bullets: Array<Bullet> = Array(),
    val throwables: Array<ThrowableEntity> = Array(),
    val carPathNodes: Array<CharacterPathNode> = Array(),
    val characterPathNodes: Array<CharacterPathNode> = Array()
)

data class InteriorLayout(
    val size: Vector3,
    val defaultBlocks: List<Pair<Vector3, BlockType>>, // Position and Type
    val defaultFurniture: List<Pair<Vector3, ObjectType>>, // Position and Type
    val entrancePosition: Vector3, // Where player appears when entering
    val exitTriggerPosition: Vector3, // Center of the exit area
    val exitTriggerSize: Vector3 = Vector3(4f, 4f, 2f) // Size of the exit area
)
