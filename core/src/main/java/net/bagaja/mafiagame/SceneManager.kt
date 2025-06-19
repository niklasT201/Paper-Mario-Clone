package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import java.util.UUID

enum class SceneType {
    WORLD,
    HOUSE_INTERIOR
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
    private val interiorLayoutSystem: InteriorLayoutSystem
) {
    // --- ACTIVE SCENE DATA ---
    // The main game loop will render whatever is in these arrays.
    val activeBlocks = Array<GameBlock>()
    val activeObjects = Array<GameObject>()
    val activeCars = Array<GameCar>()
    val activeHouses = Array<GameHouse>()
    val activeItems = Array<GameItem>()

    // --- STATE MANAGEMENT ---
    var currentScene: SceneType = SceneType.WORLD
        private set
    private var worldState: WorldState? = null // Holds the saved world data
    private val interiorStates = mutableMapOf<String, InteriorState>()
    private var currentInteriorId: String? = null

    // --- INITIALIZATION ---
    // Called once at the start to populate the world for the first time
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

        // NEW: Synchronize the ItemSystem with the initial world items
        itemSystem.setActiveItems(activeItems)

        println("SceneManager initialized. World scene is active.")
    }

    // --- TRANSITION LOGIC ---

    fun transitionToInterior(house: GameHouse) {
        if (currentScene == SceneType.HOUSE_INTERIOR) return // Already inside

        println("Transitioning to interior of house: ${house.id}")
        saveWorldState()

        val interior = interiorStates[house.id] ?: createInteriorForHouse(house)
        loadInteriorState(interior)

        currentInteriorId = house.id
        currentScene = SceneType.HOUSE_INTERIOR

        // Position player at the entrance defined in the layout
        playerSystem.setPosition(interior.playerPosition)
    }

    fun transitionToWorld() {
        if (currentScene == SceneType.WORLD) return // Already in world

        println("Transitioning back to world...")
        saveCurrentInteriorState()
        restoreWorldState()

        currentScene = SceneType.WORLD
        currentInteriorId = null

        // Position the player at the saved exit position
        worldState?.let { playerSystem.setPosition(it.playerPosition) }
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

        // NEW: Synchronize the ItemSystem with the restored world items
        itemSystem.setActiveItems(activeItems)
    }


    private fun saveCurrentInteriorState() {
        val id = currentInteriorId ?: return
        val currentState = interiorStates[id] ?: return

        println("Saving state for interior: $id")
        // Update the state with the latest data from the active scene
        currentState.blocks.clear(); currentState.blocks.addAll(activeBlocks)
        currentState.objects.clear(); currentState.objects.addAll(activeObjects)
        currentState.items.clear(); currentState.items.addAll(activeItems)
        currentState.playerPosition.set(playerSystem.getPosition())
    }

    private fun loadInteriorState(state: InteriorState) {
        println("Loading state for interior: ${state.houseId}")
        clearActiveScene()
        activeBlocks.addAll(state.blocks)
        activeObjects.addAll(state.objects)
        activeItems.addAll(state.items)

        // NEW: Synchronize the ItemSystem with the loaded interior items
        itemSystem.setActiveItems(activeItems)
    }

    private fun createInteriorForHouse(house: GameHouse): InteriorState {
        println("No saved state for ${house.id}, creating new interior...")
        val layout = interiorLayoutSystem.getLayout(house.houseType) ?: run {
            println("WARNING: No interior layout found for house type ${house.houseType}. Creating empty interior.")
            // Return a default empty interior so the game doesn't crash
            return InteriorState(house.id, playerPosition = Vector3(0f, 1f, 0f))
        }

        val newState = InteriorState(
            houseId = house.id,
            playerPosition = layout.entrancePosition.cpy()
        )

        // Generate blocks (walls, floor, ceiling) from layout
        layout.defaultBlocks.forEach { (pos, type) ->
            val blockInstance = blockSystem.createBlockInstance(type)
            if (blockInstance != null) {
                // NOTE: This assumes a fixed block size and rotation for interiors.
                // You could extend the layout data to include this.
                val gameBlock = GameBlock(blockInstance, type, pos.cpy(), 0f)
                gameBlock.updateTransform()
                newState.blocks.add(gameBlock)
            }
        }

        // Generate furniture and other objects from layout
        layout.defaultFurniture.forEach { (pos, type) ->
            val newGameObject = objectSystem.createGameObjectWithLight(type, pos.cpy())
            if (newGameObject != null) {
                newGameObject.modelInstance.transform.setTranslation(pos)
                newState.objects.add(newGameObject)
            }
        }

        // You could add items here too if layouts supported them
        // layout.defaultItems.forEach { ... }

        interiorStates[house.id] = newState
        println("New interior created and saved for ${house.id}")
        return newState
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
