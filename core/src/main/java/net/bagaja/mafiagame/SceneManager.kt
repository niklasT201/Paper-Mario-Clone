package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array

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
    private val playerSystem: PlayerSystem,
    private val blockSystem: BlockSystem,
    private val objectSystem: ObjectSystem,
    private val itemSystem: ItemSystem,
    private val interiorSystem: InteriorSystem,
    private val enemySystem: EnemySystem,
    private val npcSystem: NPCSystem,
    private val roomTemplateManager: RoomTemplateManager,
    val cameraManager: CameraManager,
    private val transitionSystem: TransitionSystem,
    private val faceCullingSystem: FaceCullingSystem,
    private val game: MafiaGame
) {
    // --- ACTIVE SCENE DATA ---
    val activeBlocks = Array<GameBlock>()
    val activeObjects = Array<GameObject>()
    val activeCars = Array<GameCar>()
    val activeHouses = Array<GameHouse>()
    val activeItems = Array<GameItem>()
    val activeInteriors = Array<GameInterior>()
    val activeEnemies = Array<GameEnemy>()
    val activeNPCs = Array<GameNPC>()

    // --- STATE MANAGEMENT ---
    var currentScene: SceneType = SceneType.WORLD
        private set
    private var worldState: WorldState? = null
    private val interiorStates = mutableMapOf<String, InteriorState>()
    private var currentInteriorId: String? = null
    private var pendingHouse: GameHouse? = null

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
        activeBlocks.addAll(initialBlocks)
        activeObjects.addAll(initialObjects)
        activeCars.addAll(initialCars)
        activeHouses.addAll(initialHouses)
        activeItems.addAll(initialItems)
        activeEnemies.addAll(initialEnemies)
        activeNPCs.addAll(initialNPCs)

        // Initial face culling for the entire world on startup
        recalculateAllFacesInCollection(activeBlocks)

        currentScene = SceneType.WORLD

        // Synchronize the ItemSystem with the initial world items
        itemSystem.setActiveItems(activeItems)
        println("SceneManager initialized. World scene is active.")
    }

    fun findHighestSupportY(x: Float, z: Float, checkRadius: Float, blockSize: Float): Float {
        var highestSupportY = 0f // Default to ground level

        // Check against all active blocks
        for (block in activeBlocks) {
            val blockBounds = block.getBoundingBox(blockSize)

            // Check for horizontal overlap
            val horizontalOverlap = (x + checkRadius > blockBounds.min.x && x - checkRadius < blockBounds.max.x) &&
                (z + checkRadius > blockBounds.min.z && z - checkRadius < blockBounds.max.z)

            if (horizontalOverlap) {
                val blockTop = blockBounds.max.y
                if (blockTop > highestSupportY) {
                    highestSupportY = blockTop
                }
            }
        }

        // Check against all active houses (which can include stairs)
        for (house in activeHouses) {
            val houseBounds = house.modelInstance.calculateBoundingBox(BoundingBox())
            val horizontalOverlap = (x + checkRadius > houseBounds.min.x && x - checkRadius < houseBounds.max.x) &&
                (z + checkRadius > houseBounds.min.z && z - checkRadius < houseBounds.max.z)
            if(horizontalOverlap) {
                val houseTop = houseBounds.max.y
                if (houseTop > highestSupportY) {
                    highestSupportY = houseTop
                }
            }
        }

        // Check against all solid 3D interiors
        for (interior in activeInteriors) {
            if (!interior.interiorType.is3D || !interior.interiorType.hasCollision) continue

            val interiorBounds = interior.instance.calculateBoundingBox(BoundingBox())
            val horizontalOverlap = (x + checkRadius > interiorBounds.min.x && x - checkRadius < interiorBounds.max.x) &&
                (z + checkRadius > interiorBounds.min.z && z - checkRadius < interiorBounds.max.z)

            if(horizontalOverlap) {
                val interiorTop = interiorBounds.max.y
                if (interiorTop > highestSupportY) {
                    highestSupportY = interiorTop
                }
            }
        }


        return highestSupportY
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
        transitionSystem.start(duration = 0.7f)
    }

    fun transitionToWorld() {
        if (isTransitioning()) return

        println("Starting transition back to world...")
        saveCurrentInteriorState() // Save any changes made to the live interior
        currentScene = SceneType.TRANSITIONING_TO_WORLD
        transitionSystem.start(duration = 0.7f) // Start the 0.7 second animation
    }

    private fun completeTransitionToInterior() {
        val house = pendingHouse ?: return
        var interior: InteriorState? = interiorStates[house.id]
        var foundExitDoorId: String? = null // To store the ID from the template

        if (interior == null) {
            println("No saved state for this house instance. Generating new interior from template.")
            val templateId = house.assignedRoomTemplateId
            if (templateId != null) {
                val template = roomTemplateManager.getTemplate(templateId)
                if (template != null) {
                    // Call the modified function and get the Pair result
                    val (newInteriorState, exitDoorId) = createInteriorFromTemplate(house, template)
                    interior = newInteriorState
                    foundExitDoorId = exitDoorId // Store the found ID

                    interiorStates[house.id] = interior
                } else {
                    println("Warning: House has template ID '$templateId' but template was not found. Creating empty room.")
                    interior = createNewEmptyInteriorFor(house)
                    interiorStates[house.id] = interior
                }
            } else {
                println("Warning: Unlocked house has no assigned room template. Creating empty room.")
                interior = createNewEmptyInteriorFor(house)
                interiorStates[house.id] = interior
            }
        } else {
            println("Found saved state for this house instance. Loading it.")
        }

        // If we generated a new interior from a template and found an exit door
        if (foundExitDoorId != null) {
            house.exitDoorId = foundExitDoorId
            println("Assigned exit door ID '${house.exitDoorId}' to house '${house.id}' from template.")
        }

        loadInteriorState(interior)

        currentInteriorId = house.id
        currentScene = SceneType.HOUSE_INTERIOR
        playerSystem.setPosition(interior.playerPosition)
        cameraManager.resetAndSnapToPlayer(interior.playerPosition, false)
        pendingHouse = null

        if (house.exitDoorId == null) {
            game.enterExitDoorPlacementMode(house)
        }
    }

    fun getCurrentHouse(): GameHouse? {
        if (currentScene != SceneType.HOUSE_INTERIOR || currentInteriorId == null) {
            return null
        }
        return worldState?.houses?.find { it.id == currentInteriorId }
    }

    private fun completeTransitionToWorld() {
        println("Transition finished. Restoring world.")
        restoreWorldState()

        currentScene = SceneType.WORLD
        currentInteriorId = null

        // Position the player at the saved exit position
        worldState?.let {
            playerSystem.setPosition(it.playerPosition)
            cameraManager.resetAndSnapToPlayer(it.playerPosition, false)
        }
    }

    // --- PRIVATE HELPER METHODS ---

    private fun saveWorldState() {
        println("Saving world state...")
        // We create NEW arrays to snapshot the state, not just reference the active ones.
        worldState = WorldState(
            blocks = Array(activeBlocks),
            objects = Array(activeObjects),
            cars = Array(activeCars),
            houses = Array(activeHouses),
            items = Array(activeItems),
            enemies = Array(activeEnemies),
            npcs = Array(activeNPCs),
            playerPosition = playerSystem.getPosition(), // Save player pos just outside door
            cameraPosition = Vector3() // You would save camera state here too if needed
        )
        println("World state saved. Player at ${worldState!!.playerPosition}")
    }

    private fun restoreWorldState() {
        val state = worldState ?: return
        println("Restoring world state...")

        // Clear active data and load from the saved state
        clearActiveScene()
        activeBlocks.addAll(state.blocks)
        activeObjects.addAll(state.objects)
        activeCars.addAll(state.cars)
        activeHouses.addAll(state.houses)
        activeItems.addAll(state.items)
        activeEnemies.addAll(state.enemies)
        activeNPCs.addAll(state.npcs)

        // Synchronize the ItemSystem with the restored world items
        itemSystem.setActiveItems(activeItems)
    }


    private fun saveCurrentInteriorState() {
        val id = currentInteriorId ?: return
        val currentState = interiorStates[id] ?: return
        println("Saving state for interior instance: $id")

        // MODIFIED: Added enemies to the state
        currentState.blocks.clear(); currentState.blocks.addAll(activeBlocks)
        currentState.objects.clear(); currentState.objects.addAll(activeObjects)
        currentState.items.clear(); currentState.items.addAll(activeItems)
        currentState.interiors.clear(); currentState.interiors.addAll(activeInteriors)
        currentState.enemies.clear(); currentState.enemies.addAll(activeEnemies)
        currentState.npcs.clear(); currentState.npcs.addAll(activeNPCs)
        currentState.playerPosition.set(playerSystem.getPosition())
    }

    private fun loadInteriorState(state: InteriorState) {
        println("Loading state for interior instance: ${state.houseId}")
        clearActiveScene()
        activeBlocks.addAll(state.blocks)
        activeObjects.addAll(state.objects)
        activeItems.addAll(state.items)
        activeInteriors.addAll(state.interiors)
        activeEnemies.addAll(state.enemies)
        activeNPCs.addAll(state.npcs)

        // Synchronize the ItemSystem with the loaded interior items
        itemSystem.setActiveItems(activeItems)
    }

    private fun createInteriorFromTemplate(house: GameHouse, template: RoomTemplate): Pair<InteriorState, String?> {
        val newBlocks = Array<GameBlock>()
        val newObjects = Array<GameObject>()
        val newItems = Array<GameItem>()
        val newInteriors = Array<GameInterior>()
        val newEnemies = Array<GameEnemy>()
        val newNPCs = Array<GameNPC>()

        println("Building interior from template: ${template.name}")

        template.elements.forEach { element ->
            when (element.elementType) {
                RoomElementType.BLOCK -> {
                    element.blockType?.let { blockType ->
                        // Use createFaceInstances and the new GameBlock constructor
                        blockSystem.createFaceInstances(blockType)?.let { faceInstances ->
                            val gameBlock = GameBlock(faceInstances, blockType, element.position.cpy(), element.rotation)
                            gameBlock.updateTransform()
                            newBlocks.add(gameBlock)
                        }
                    }
                }
                RoomElementType.OBJECT -> {
                    element.objectType?.let { objectType ->
                        // Note: If your objects require a LightingManager during creation, you'll need
                        // to pass the LightingManager instance into the SceneManager.
                        objectSystem.createGameObjectWithLight(objectType, element.position.cpy())?.let { gameObject ->
                            newObjects.add(gameObject)
                        }
                    }
                }
                RoomElementType.ITEM -> {
                    element.itemType?.let { itemType ->
                        itemSystem.createItem(element.position.cpy(), itemType)?.let { gameItem ->
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
                        enemySystem.createEnemy(element.position.cpy(), element.enemyType, element.enemyBehavior)?.let { gameEnemy ->
                            newEnemies.add(gameEnemy)
                        }
                    }
                }
                RoomElementType.NPC -> {
                    if (element.npcType != null && element.npcBehavior != null) {
                        npcSystem.createNPC(element.position.cpy(), element.npcType, element.npcBehavior)?.let { gameNPC ->
                            newNPCs.add(gameNPC)
                        }
                    }
                }
            }
        }

        // After all blocks are created, run face culling on the entire collection
        recalculateAllFacesInCollection(newBlocks)

        var foundExitDoorId: String? = null
        // Check if the template has a valid saved door position.
        if (template.exitDoorPosition.len2() > 0) {
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
        val interiorState = InteriorState(
            houseId = house.id,
            blocks = newBlocks,
            objects = newObjects,
            items = newItems,
            interiors = newInteriors,
            enemies = newEnemies,
            npcs = newNPCs,
            playerPosition = template.entrancePosition.cpy()
        )
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

    fun saveCurrentInteriorAsTemplate(id: String, name: String, category: String): Boolean {
        if (currentScene != SceneType.HOUSE_INTERIOR) {
            println("Error: Must be in an interior to save it as a template.")
            return false
        }

        // Find the house we are currently in to get its exit door ID
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
        activeBlocks.forEach { block ->
            elements.add(RoomElement(
                position = block.position.cpy(),
                elementType = RoomElementType.BLOCK,
                blockType = block.blockType,
                rotation = block.rotationY
            ))
        }

        // Convert active objects to RoomElements
        activeObjects.forEach { obj ->
            elements.add(RoomElement(
                position = obj.position.cpy(),
                elementType = RoomElementType.OBJECT,
                objectType = obj.objectType,
                rotation = 0f // Assuming objects don't rotate for now
            ))
        }

        // Convert active items to RoomElements
        activeItems.forEach { item ->
            elements.add(RoomElement(
                position = item.position.cpy(),
                elementType = RoomElementType.ITEM,
                itemType = item.itemType
            ))
        }

        activeInteriors.forEach { interior ->
            elements.add(RoomElement(
                position = interior.position.cpy(),
                elementType = RoomElementType.INTERIOR,
                interiorType = interior.interiorType,
                rotation = interior.rotation,
                scale = interior.scale.cpy()
            ))
        }

        activeEnemies.forEach { enemy ->
            elements.add(RoomElement(
                position = enemy.position.cpy(),
                elementType = RoomElementType.ENEMY,
                enemyType = enemy.enemyType,
                enemyBehavior = enemy.behaviorType
            ))
        }

        val newTemplate = RoomTemplate(
            id = id,
            name = name,
            description = "A user-created room.",
            size = Vector3(20f, 8f, 20f),
            elements = elements,
            entrancePosition = playerSystem.getPosition(),
            exitTriggerPosition = playerSystem.getPosition().add(0f, 0f, 1f),
            category = category,
            exitDoorPosition = doorPosition
        )

        roomTemplateManager.addTemplate(newTemplate)
        println("Successfully saved room as template '$id'!")
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

        // Build the scene from the template's elements
        template.elements.forEach { element ->
            when (element.elementType) {
                RoomElementType.BLOCK -> {
                    // REVISED: Use createFaceInstances and the new GameBlock constructor
                    element.blockType?.let { blockType ->
                        blockSystem.createFaceInstances(blockType)?.let { faceInstances ->
                            val gameBlock = GameBlock(faceInstances, blockType, element.position.cpy(), element.rotation)
                            gameBlock.updateTransform()
                            activeBlocks.add(gameBlock)
                        }
                    }
                }
                RoomElementType.OBJECT -> {
                    element.objectType?.let { objectType ->
                        objectSystem.createGameObjectWithLight(objectType, element.position.cpy(), lightingManager = null)?.let { gameObject ->
                            activeObjects.add(gameObject)
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
                        enemySystem.createEnemy(element.position.cpy(), element.enemyType, element.enemyBehavior)?.let { gameEnemy ->
                            activeEnemies.add(gameEnemy)
                        }
                    }
                }
                RoomElementType.NPC -> {
                    if (element.npcType != null && element.npcBehavior != null) {
                        npcSystem.createNPC(element.position.cpy(), element.npcType, element.npcBehavior)?.let { gameNPC ->
                            activeNPCs.add(gameNPC)
                        }
                    }
                }
            }
        }
        // After loading all blocks, run face culling on the entire active collection
        recalculateAllFacesInCollection(activeBlocks)

        itemSystem.setActiveItems(activeItems)

        // Move player to the template's entrance
        playerSystem.setPosition(template.entrancePosition)
        cameraManager.resetAndSnapToPlayer(template.entrancePosition, false)
    }

    private fun recalculateAllFacesInCollection(blocks: Array<GameBlock>) {
        faceCullingSystem.recalculateAllFaces(blocks)
    }

    private fun clearActiveScene() {
        activeBlocks.clear()
        activeObjects.clear()
        activeCars.clear()
        activeHouses.clear()
        activeItems.clear()
        activeInteriors.clear()
        activeEnemies.clear()
        activeNPCs.clear()
    }
}

data class WorldState(
    val blocks: Array<GameBlock>,
    val objects: Array<GameObject>,
    val cars: Array<GameCar>,
    val houses: Array<GameHouse>,
    val items: Array<GameItem>,
    val enemies: Array<GameEnemy>,
    val npcs: Array<GameNPC>,
    val playerPosition: Vector3,
    val cameraPosition: Vector3
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
    var playerPosition: Vector3
)

data class InteriorLayout(
    val size: Vector3,
    val defaultBlocks: List<Pair<Vector3, BlockType>>, // Position and Type
    val defaultFurniture: List<Pair<Vector3, ObjectType>>, // Position and Type
    val entrancePosition: Vector3, // Where player appears when entering
    val exitTriggerPosition: Vector3, // Center of the exit area
    val exitTriggerSize: Vector3 = Vector3(4f, 4f, 2f) // Size of the exit area
)
