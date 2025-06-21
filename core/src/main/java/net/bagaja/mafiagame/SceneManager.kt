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
    private val roomTemplateManager: RoomTemplateManager,
    private val cameraManager: CameraManager,
    private val transitionSystem: TransitionSystem
) {
    // --- ACTIVE SCENE DATA ---
    val activeBlocks = Array<GameBlock>()
    val activeObjects = Array<GameObject>()
    val activeCars = Array<GameCar>()
    val activeHouses = Array<GameHouse>()
    val activeItems = Array<GameItem>()

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
        println("Transition finished. Loading interior for ${house.id}")

        val interior = interiorStates[house.id] ?: createNewEmptyInteriorFor(house)

        loadInteriorState(interior)

        currentInteriorId = house.id
        currentScene = SceneType.HOUSE_INTERIOR

        // Position player at the entrance defined in the layout
        val newPlayerPos = interior.playerPosition
        playerSystem.setPosition(newPlayerPos)

        // Force the camera to snap to the player's new position
        cameraManager.resetAndSnapToPlayer(newPlayerPos)
        pendingHouse = null
    }

    private fun completeTransitionToWorld() {
        println("Transition finished. Restoring world.")
        restoreWorldState()

        currentScene = SceneType.WORLD
        currentInteriorId = null

        // Position the player at the saved exit position
        worldState?.let {
            val newPlayerPos = it.playerPosition
            playerSystem.setPosition(newPlayerPos)

            // Force the camera to snap to the player's new position
            cameraManager.resetAndSnapToPlayer(newPlayerPos)
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
        currentState.playerPosition.set(playerSystem.getPosition())
    }

    private fun loadInteriorState(state: InteriorState) {
        println("Loading state for interior instance: ${state.houseId}")
        clearActiveScene()
        activeBlocks.addAll(state.blocks)
        activeObjects.addAll(state.objects)
        activeItems.addAll(state.items)

        // Synchronize the ItemSystem with the loaded interior items
        itemSystem.setActiveItems(activeItems)
    }

    private fun createNewEmptyInteriorFor(house: GameHouse): InteriorState {
        println("No saved state for this house instance. Creating a new empty interior.")

        // Create a default room with just a floor.
        val builder = RoomBuilder()
            .setSize(20f, 8f, 20f) // A decent default size
            .setEntrance(10f, 4f, 18f)
            .addFloor()

        val newState = InteriorState(
            houseId = house.id,
            // You can decide if an empty room should have a floor by default
            playerPosition = Vector3(10f, 4f, 18f) // Default entrance
        )

        // Add a default floor so the player doesn't fall.
        val floorBlock = blockSystem.createBlockInstance(BlockType.WOODEN_FLOOR)
        if(floorBlock != null) {
            val gameBlock = GameBlock(floorBlock, BlockType.WOODEN_FLOOR, Vector3(10f, 2f, 10f), 0f)
            newState.blocks.add(gameBlock)
        }

        interiorStates[house.id] = newState
        println("New interior created and saved for ${house.id}")
        return newState
    }

    fun saveCurrentInteriorAsTemplate(id: String, name: String, category: String) {
        if (currentScene != SceneType.HOUSE_INTERIOR) {
            println("Error: Must be in an interior to save it as a template.")
            return
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

        val newTemplate = RoomTemplate(
            id = id,
            name = name,
            description = "A user-created room.",
            size = Vector3(20f, 8f, 20f), // You could calculate this dynamically
            elements = elements,
            entrancePosition = playerSystem.getPosition(), // Use current pos as a sensible default
            exitTriggerPosition = playerSystem.getPosition().add(0f, 0f, 1f),
            category = category
        )

        roomTemplateManager.addTemplate(newTemplate)
        println("Successfully saved room as template '$id'!")
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
                        val gameBlock = GameBlock(instance, element.blockType, element.position.cpy(), element.rotation)
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
    var playerPosition: Vector3 // var because player can move inside
)

data class InteriorLayout(
    val size: Vector3,
    val defaultBlocks: List<Pair<Vector3, BlockType>>, // Position and Type
    val defaultFurniture: List<Pair<Vector3, ObjectType>>, // Position and Type
    val entrancePosition: Vector3, // Where player appears when entering
    val exitTriggerPosition: Vector3, // Center of the exit area
    val exitTriggerSize: Vector3 = Vector3(4f, 4f, 2f) // Size of the exit area
)

class InteriorLayoutSystem {
    // A map from the exterior house model to its interior layout.
    // This allows different looking houses to share the same interior if you want.
    private val layouts = mapOf<HouseType, InteriorLayout>(
        // Example layout for HOUSE_4
        HouseType.HOUSE_4 to InteriorLayout(
            size = Vector3(20f, 8f, 15f),
            entrancePosition = Vector3(10f, 2f, 13f), // Appear just inside the door
            exitTriggerPosition = Vector3(10f, 2f, 14f), // Area right at the door
            defaultBlocks = buildRoom(Vector3(20f, 8f, 15f)), // Use a helper to build walls
            defaultFurniture = listOf(
                Pair(Vector3(3f, 0f, 3f), ObjectType.LANTERN),
                Pair(Vector3(17f, 0f, 3f), ObjectType.TREE) // A nice indoor plant
            )
        ),
        // Add layouts for other houses like HOUSE_1, HOUSE_2 etc. here
        // If a house has no entry, the player can't enter it.
        HouseType.STAIR to InteriorLayout(
            size = Vector3(10f, 12f, 20f),
            entrancePosition = Vector3(5f, 2f, 18f),
            exitTriggerPosition = Vector3(5f, 2f, 19f),
            defaultBlocks = buildRoom(Vector3(10f, 12f, 20f)),
            defaultFurniture = listOf()
        )
    )

    // Helper function to quickly generate walls, floor, and ceiling for a room
    private fun buildRoom(size: Vector3): List<Pair<Vector3, BlockType>> {
        val blocks = mutableListOf<Pair<Vector3, BlockType>>()
        val blockSize = 4f // Assuming a standard block size

        // Floor
        for (x in 0 until size.x.toInt() step blockSize.toInt()) {
            for (z in 0 until size.z.toInt() step blockSize.toInt()) {
                blocks.add(Pair(Vector3(x.toFloat(), 0f, z.toFloat()), BlockType.WOODEN_FLOOR))
            }
        }
        // Ceiling
        for (x in 0 until size.x.toInt() step blockSize.toInt()) {
            for (z in 0 until size.z.toInt() step blockSize.toInt()) {
                blocks.add(Pair(Vector3(x.toFloat(), size.y, z.toFloat()), BlockType.CEILING))
            }
        }
        // Walls (X-axis)
        for (x in 0 until size.x.toInt() step blockSize.toInt()) {
            blocks.add(Pair(Vector3(x.toFloat(), blockSize, 0f), BlockType.BRICK_WALL_PNG))
            blocks.add(Pair(Vector3(x.toFloat(), blockSize, size.z - blockSize), BlockType.BRICK_WALL_PNG))
        }
        // Walls (Z-axis)
        for (z in 0 until size.z.toInt() step blockSize.toInt()) {
            blocks.add(Pair(Vector3(0f, blockSize, z.toFloat()), BlockType.BRICK_WALL_PNG))
            blocks.add(Pair(Vector3(size.x - blockSize, blockSize, z.toFloat()), BlockType.BRICK_WALL_PNG))
        }

        return blocks
    }


    fun getLayout(houseType: HouseType): InteriorLayout? {
        return layouts[houseType]
    }
}
