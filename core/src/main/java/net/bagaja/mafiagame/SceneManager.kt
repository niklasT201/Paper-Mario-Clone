package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
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
    private val roomTemplateManager: RoomTemplateManager,
    private val cameraManager: CameraManager,
    private val transitionSystem: TransitionSystem,
    private val game: MafiaGame
) {
    // --- ACTIVE SCENE DATA ---
    val activeBlocks = Array<GameBlock>()
    val activeObjects = Array<GameObject>()
    val activeCars = Array<GameCar>()
    val activeHouses = Array<GameHouse>()
    val activeItems = Array<GameItem>()
    val activeInteriors = Array<GameInterior>()

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
        initialItems: Array<GameItem>
    ) {
        activeBlocks.addAll(initialBlocks)
        activeObjects.addAll(initialObjects)
        activeCars.addAll(initialCars)
        activeHouses.addAll(initialHouses)
        activeItems.addAll(initialItems)

        currentScene = SceneType.WORLD

        // Synchronize the ItemSystem with the initial world items
        itemSystem.setActiveItems(activeItems)
        println("SceneManager initialized. World scene is active.")
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
        cameraManager.resetAndSnapToPlayer(interior.playerPosition)
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
            cameraManager.resetAndSnapToPlayer(it.playerPosition)
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

        // Synchronize the ItemSystem with the restored world items
        itemSystem.setActiveItems(activeItems)
    }


    private fun saveCurrentInteriorState() {
        val id = currentInteriorId ?: return
        val currentState = interiorStates[id] ?: return
        println("Saving state for interior instance: $id")

        // Update the state with the latest data from the active scene
        currentState.blocks.clear(); currentState.blocks.addAll(activeBlocks)
        currentState.objects.clear(); currentState.objects.addAll(activeObjects)
        currentState.items.clear(); currentState.items.addAll(activeItems)
        currentState.interiors.clear(); currentState.interiors.addAll(activeInteriors)
        currentState.playerPosition.set(playerSystem.getPosition())
    }

    private fun loadInteriorState(state: InteriorState) {
        println("Loading state for interior instance: ${state.houseId}")
        clearActiveScene()
        activeBlocks.addAll(state.blocks)
        activeObjects.addAll(state.objects)
        activeItems.addAll(state.items)
        activeInteriors.addAll(state.interiors)

        // Synchronize the ItemSystem with the loaded interior items
        itemSystem.setActiveItems(activeItems)
    }

    private fun createInteriorFromTemplate(house: GameHouse, template: RoomTemplate): Pair<InteriorState, String?> {
        val newBlocks = Array<GameBlock>()
        val newObjects = Array<GameObject>()
        val newItems = Array<GameItem>()
        val newInteriors = Array<GameInterior>()

        println("Building interior from template: ${template.name}")

        template.elements.forEach { element ->
            when (element.elementType) {
                RoomElementType.BLOCK -> {
                    element.blockType?.let { blockType ->
                        blockSystem.createBlockInstance(blockType)?.let { instance ->
                            val gameBlock = GameBlock(instance, blockType, element.position.cpy(), element.rotation)
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
        }

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

        // Create the state object
        val interiorState = InteriorState(
            houseId = house.id,
            blocks = newBlocks,
            objects = newObjects,
            items = newItems,
            interiors = newInteriors,
            playerPosition = template.entrancePosition.cpy()
        )

        // Return both the state AND the found door ID
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
            interiors = Array(), // The interiors list start empty
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
                    val instance = blockSystem.createBlockInstance(element.blockType!!)
                    if (instance != null) {
                        val gameBlock = GameBlock(instance, element.blockType!!, element.position.cpy(), element.rotation)
                        gameBlock.updateTransform()
                        activeBlocks.add(gameBlock)
                    }
                }
                RoomElementType.OBJECT -> {
                    val gameObject = objectSystem.createGameObjectWithLight(element.objectType!!, element.position.cpy())
                    if (gameObject != null) {
                        activeObjects.add(gameObject)
                    }
                }
                RoomElementType.ITEM -> {
                    val gameItem = itemSystem.createItem(element.position.cpy(), element.itemType!!)
                    if (gameItem != null) {
                        activeItems.add(gameItem)
                    }
                }
                RoomElementType.INTERIOR -> {
                    val gameInterior = interiorSystem.createInteriorInstance(element.interiorType!!)
                    if (gameInterior != null) {
                        gameInterior.position.set(element.position)
                        gameInterior.rotation = element.rotation
                        gameInterior.scale.set(element.scale)
                        gameInterior.updateTransform()
                        activeInteriors.add(gameInterior)
                    }
                }
            }
        }

        // Update the item system with the new items
        itemSystem.setActiveItems(activeItems)

        // Move player to the template's entrance
        playerSystem.setPosition(template.entrancePosition)
        cameraManager.resetAndSnapToPlayer(template.entrancePosition)
    }

    private fun clearActiveScene() {
        activeBlocks.clear()
        activeObjects.clear()
        activeCars.clear()
        activeHouses.clear()
        activeItems.clear()
        activeInteriors.clear()
    }
}

data class WorldState(
    val blocks: Array<GameBlock>,
    val objects: Array<GameObject>,
    val cars: Array<GameCar>,
    val houses: Array<GameHouse>,
    val items: Array<GameItem>,
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
