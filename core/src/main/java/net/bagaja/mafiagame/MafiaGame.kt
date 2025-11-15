package net.bagaja.mafiagame

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import kotlin.random.Random

enum class GameMode {
    START_MENU,
    LOADING,
    IN_GAME
}

class MafiaGame : ApplicationAdapter() {
    var isEditorMode = true
    var currentSaveFileName: String? = null
    var currentGameMode = GameMode.IN_GAME
    private var initialSystemsLoaded = false
    var isInspectModeEnabled = false
    val renderDistanceInChunks = 2
    private lateinit var modelBatch: ModelBatch
    private lateinit var shaderProvider: BillboardShaderProvider
    private lateinit var spriteBatch: SpriteBatch
    lateinit var cameraManager: CameraManager
    lateinit var shaderEffectManager: ShaderEffectManager
    lateinit var triggerSystem: TriggerSystem
    lateinit var missionSystem: MissionSystem
    lateinit var saveLoadSystem: SaveLoadSystem
    private lateinit var dialogueManager: DialogueManager

    // UI and Input Managers
    lateinit var uiManager: UIManager
    private lateinit var inputHandler: InputHandler

    // Raycast System
    lateinit var raycastSystem: RaycastSystem

    // Block system
    lateinit var blockSystem: BlockSystem
    lateinit var objectSystem: ObjectSystem
    lateinit var itemSystem: ItemSystem
    lateinit var carSystem: CarSystem
    lateinit var sceneManager: SceneManager
    lateinit var enemySystem: EnemySystem
    lateinit var npcSystem: NPCSystem
    private lateinit var pathfindingSystem: PathfindingSystem
    private lateinit var roomTemplateManager: RoomTemplateManager
    lateinit var houseSystem: HouseSystem

    // Highlight System
    lateinit var highlightSystem: HighlightSystem
    lateinit var targetingIndicatorSystem: TargetingIndicatorSystem
    private lateinit var lockIndicatorSystem: LockIndicatorSystem
    lateinit var meleeRangeIndicatorSystem: MeleeRangeIndicatorSystem

    private lateinit var faceCullingSystem: FaceCullingSystem
    private lateinit var occlusionSystem: OcclusionSystem

    // Transition System
    private lateinit var transitionSystem: TransitionSystem

    // 2D Player (but positioned in 3D space)
    lateinit var playerSystem: PlayerSystem
    private lateinit var characterPhysicsSystem: CharacterPhysicsSystem

    lateinit var spawnerSystem: SpawnerSystem

    // Game objects
    var lastPlacedInstance: Any? = null

    lateinit var backgroundSystem: BackgroundSystem
    lateinit var parallaxBackgroundSystem: ParallaxBackgroundSystem
    private lateinit var interiorSystem: InteriorSystem
    var isPlacingExitDoorMode = false

    private var showInvisibleBlockOutlines = false
    private var showBlockCollisionOutlines = false

    // Block size
    val blockSize = 4f

    lateinit var lightingManager: LightingManager
    lateinit var particleSystem: ParticleSystem
    lateinit var teleporterSystem: TeleporterSystem
    lateinit var fireSystem: FireSystem
    private lateinit var bloodPoolSystem: BloodPoolSystem
    lateinit var footprintSystem: FootprintSystem
    private lateinit var boneSystem: BoneSystem
    lateinit var trajectorySystem: TrajectorySystem
    private lateinit var blockDebugRenderer: BlockDebugRenderer
    lateinit var carPathSystem: CarPathSystem
    lateinit var characterPathSystem: CharacterPathSystem
    lateinit var objectiveArrowSystem: ObjectiveArrowSystem
    lateinit var bulletTrailSystem: BulletTrailSystem
    lateinit var weatherSystem: WeatherSystem
    lateinit var musicManager: MusicManager
    lateinit var ambientSoundSystem: AmbientSoundSystem
    lateinit var soundManager: SoundManager
    lateinit var decalSystem: DecalSystem
    lateinit var waterPuddleSystem: WaterPuddleSystem
    lateinit var audioEmitterSystem: AudioEmitterSystem

    override fun create() {
        // --- Part 1: Initialize Core Systems ---
        musicManager = MusicManager() // Must initialize before setting volume
        soundManager = SoundManager() // Must initialize before setting volume
        ambientSoundSystem = AmbientSoundSystem()
        audioEmitterSystem = AudioEmitterSystem()
        musicManager.setMasterVolume(PlayerSettingsManager.current.masterVolume)
        musicManager.setMusicVolume(PlayerSettingsManager.current.musicVolume)
        soundManager.setMasterVolume(PlayerSettingsManager.current.masterVolume)
        soundManager.setSfxVolume(PlayerSettingsManager.current.sfxVolume)

        dialogueManager = DialogueManager()
        setupGraphics() // This initializes cameraManager and lightingManager
        shaderEffectManager = ShaderEffectManager()
        shaderEffectManager.initialize()
        shaderEffectManager.setEffect(PlayerSettingsManager.current.selectedShader)
        saveLoadSystem = SaveLoadSystem(this)

        // --- SOUND MANAGER SETUP ---
        soundManager.initialize()
        AssetLoader.loadGameSounds(soundManager)
        SoundCategoryManager.initialize()

        PlayerSettingsManager.load()

        // SONG 1: The new slow, dramatic Mafia Theme
        musicManager.registerSong(MusicSource.Procedural("mafia_theme") { ProceduralSongLibrary.getMafiaTheme() })
        musicManager.registerSong(MusicSource.Procedural("action_theme") { ProceduralSongLibrary.getActionTheme() })

        // Play the new Mafia Theme by default when the game starts
        musicManager.playSong("mafia_theme")

        // UIManager creation
        uiManager = UIManager(this, shaderEffectManager)
        uiManager.initializeUI()

        // Create InputHandler early with minimal dependencies
        inputHandler = InputHandler(this, uiManager)

        // --- Part 2: Decide Game Mode ---
        if (isEditorMode) {
            println("Editor mode detected. Starting game directly.")
            loadAllGameSystems() // This will now also assign dependencies to uiManager and inputHandler

            uiManager.applyAllSettingsFromManager() // Apply all loaded settings now that systems exist

            saveLoadSystem.loadGame(null)
            currentGameMode = GameMode.IN_GAME
        } else {
            println("Player mode detected. Showing start menu.")
            currentGameMode = GameMode.START_MENU
            uiManager.showStartMenu()
        }
    }

    private fun loadAllGameSystems() {
        if (initialSystemsLoaded) return
        println("Loading all game systems...")

        raycastSystem = RaycastSystem(blockSize)
        carPathSystem = CarPathSystem()
        characterPathSystem = CharacterPathSystem()
        particleSystem = ParticleSystem()
        objectiveArrowSystem = ObjectiveArrowSystem(this)
        bulletTrailSystem = BulletTrailSystem()
        blockSystem = BlockSystem()
        objectSystem = ObjectSystem()
        fireSystem = FireSystem()
        bloodPoolSystem = BloodPoolSystem()
        footprintSystem = FootprintSystem()
        waterPuddleSystem = WaterPuddleSystem()
        boneSystem = BoneSystem()
        itemSystem = ItemSystem()
        carSystem = CarSystem()
        lockIndicatorSystem = LockIndicatorSystem()
        houseSystem = HouseSystem()
        backgroundSystem = BackgroundSystem()
        parallaxBackgroundSystem = ParallaxBackgroundSystem()
        interiorSystem = InteriorSystem()
        enemySystem = EnemySystem()
        npcSystem = NPCSystem()
        roomTemplateManager = RoomTemplateManager()
        transitionSystem = TransitionSystem()
        playerSystem = PlayerSystem()
        spawnerSystem = SpawnerSystem(particleSystem, itemSystem)
        highlightSystem = HighlightSystem(this, blockSize)
        targetingIndicatorSystem = TargetingIndicatorSystem()
        meleeRangeIndicatorSystem = MeleeRangeIndicatorSystem()
        trajectorySystem = TrajectorySystem()
        blockDebugRenderer = BlockDebugRenderer()

        missionSystem = MissionSystem(this, dialogueManager)
        triggerSystem = TriggerSystem(this)

        // SceneManager depends on many systems, so it's created here.
        faceCullingSystem = FaceCullingSystem(blockSize)
        weatherSystem = WeatherSystem()

        sceneManager = SceneManager(
            playerSystem, blockSystem, objectSystem, itemSystem, interiorSystem,
            enemySystem, npcSystem, roomTemplateManager, cameraManager, houseSystem, transitionSystem,
            faceCullingSystem, this, particleSystem, fireSystem, boneSystem
        )
        sceneManager.transitionSystem.useSimpleFade = true

        pathfindingSystem = PathfindingSystem(sceneManager, blockSize, playerSystem.playerSize)
        characterPhysicsSystem = CharacterPhysicsSystem(sceneManager)
        decalSystem = DecalSystem(sceneManager)
        teleporterSystem = TeleporterSystem(objectSystem, uiManager)

        // --- DEPENDENCY INJECTION FOR UIManager ---
        audioEmitterSystem.game = this
        uiManager.blockSystem = blockSystem
        uiManager.objectSystem = objectSystem
        uiManager.itemSystem = itemSystem
        uiManager.carSystem = carSystem
        uiManager.houseSystem = houseSystem
        uiManager.backgroundSystem = backgroundSystem
        uiManager.parallaxSystem = parallaxBackgroundSystem
        uiManager.roomTemplateManager = roomTemplateManager
        uiManager.interiorSystem = interiorSystem
        uiManager.lightingManager = lightingManager
        uiManager.enemySystem = enemySystem
        uiManager.npcSystem = npcSystem
        uiManager.particleSystem = particleSystem
        uiManager.spawnerSystem = spawnerSystem
        uiManager.dialogueManager = dialogueManager
        uiManager.dialogSystem.itemSystem = itemSystem
        uiManager.audioEmitterSystem = this.audioEmitterSystem

        inputHandler.audioEmitterSystem = this.audioEmitterSystem
        inputHandler.cameraManager = cameraManager
        inputHandler.blockSystem = blockSystem
        inputHandler.objectSystem = objectSystem
        inputHandler.itemSystem = itemSystem
        inputHandler.carSystem = carSystem
        inputHandler.houseSystem = houseSystem
        inputHandler.backgroundSystem = backgroundSystem
        inputHandler.parallaxSystem = parallaxBackgroundSystem
        inputHandler.interiorSystem = interiorSystem
        inputHandler.enemySystem = enemySystem
        inputHandler.npcSystem = npcSystem
        inputHandler.particleSystem = particleSystem
        inputHandler.spawnerSystem = spawnerSystem
        inputHandler.teleporterSystem = teleporterSystem
        inputHandler.sceneManager = sceneManager
        inputHandler.roomTemplateManager = roomTemplateManager
        inputHandler.shaderEffectManager = shaderEffectManager
        inputHandler.carPathSystem = carPathSystem
        inputHandler.characterPathSystem = characterPathSystem

        weatherSystem.lightingManager = this.lightingManager
        weatherSystem.particleSystem = this.particleSystem
        weatherSystem.sceneManager = this.sceneManager

        sceneManager.raycastSystem = this.raycastSystem
        sceneManager.teleporterSystem = this.teleporterSystem
        sceneManager.decalSystem = decalSystem

        carPathSystem.sceneManager = sceneManager
        carPathSystem.raycastSystem = raycastSystem
        characterPathSystem.game = this
        characterPathSystem.raycastSystem = raycastSystem

        blockSystem.sceneManager = sceneManager
        fireSystem.sceneManager = sceneManager
        objectSystem.sceneManager = sceneManager
        itemSystem.sceneManager = sceneManager
        carSystem.sceneManager = sceneManager
        carSystem.uiManager = uiManager
        carSystem.enemySystem = enemySystem
        carSystem.npcSystem = npcSystem
        houseSystem.sceneManager = sceneManager
        backgroundSystem.sceneManager = sceneManager
        parallaxBackgroundSystem.sceneManager = sceneManager
        interiorSystem.sceneManager = sceneManager
        enemySystem.sceneManager = sceneManager
        npcSystem.sceneManager = sceneManager
        particleSystem.sceneManager = sceneManager
        spawnerSystem.sceneManager = sceneManager
        bloodPoolSystem.sceneManager = sceneManager
        footprintSystem.sceneManager = sceneManager
        waterPuddleSystem.sceneManager = sceneManager
        playerSystem.waterPuddleSystem = this.waterPuddleSystem

        ambientSoundSystem.initialize(soundManager, playerSystem, sceneManager, lightingManager, weatherSystem)
        weatherSystem.initialize(cameraManager.camera)

        highlightSystem.initialize()
        targetingIndicatorSystem.initialize()
        meleeRangeIndicatorSystem.initialize()
        trajectorySystem.initialize()
        blockDebugRenderer.initialize()
        bulletTrailSystem.initialize()

        objectiveArrowSystem.initialize()
        missionSystem.initialize()
        triggerSystem.initialize()

        particleSystem.initialize(blockSize)
        blockSystem.initialize(blockSize)
        objectSystem.initialize(blockSize, lightingManager, fireSystem, teleporterSystem, uiManager)
        fireSystem.initialize()
        bloodPoolSystem.initialize()
        footprintSystem.initialize()
        waterPuddleSystem.initialize()
        boneSystem.initialize()
        decalSystem.initialize()
        itemSystem.initialize(blockSize)
        carSystem.initialize(blockSize)
        lockIndicatorSystem.initialize()
        houseSystem.initialize()
        backgroundSystem.initialize(blockSize)
        parallaxBackgroundSystem.initialize(blockSize)
        interiorSystem.initialize(blockSize)
        enemySystem.initialize(blockSize, characterPhysicsSystem, pathfindingSystem)
        npcSystem.initialize(blockSize, characterPhysicsSystem)
        roomTemplateManager.initialize()
        playerSystem.initialize(blockSize, particleSystem, lightingManager, bloodPoolSystem, footprintSystem, characterPhysicsSystem, sceneManager)

        // Initialize managers that depend on initialized systems
        transitionSystem.create(cameraManager.findUiCamera())
        uiManager.initializeWorldDependent() // Initialize world-related UI
        inputHandler.initialize()

        // Pass the initial world data to the SceneManager
        sceneManager.initializeWorld(
            Array(), Array(), Array(), Array(), Array(), Array(), Array()
        )
        initialSystemsLoaded = true
        println("All game systems loaded.")
    }

    fun startGame(saveName: String) {
        // Hide the menu and show a "loading" state
        uiManager.hideStartMenu()
        currentGameMode = GameMode.LOADING
        // Tell the UIManager to start showing the loading screen
        uiManager.renderLoadingScreen()

        // This ensures the game systems are loaded only once.
        if (!initialSystemsLoaded) {
            loadAllGameSystems()
            inputHandler.initialize() // Fully initialize input handler now that systems exist
            uiManager.applyAllSettingsFromManager()
        }

        Gdx.app.postRunnable {
            val success = saveLoadSystem.checkForWorldUpdateAndLoad(saveName)
            if (success) {
                this.currentSaveFileName = saveName
                currentGameMode = GameMode.IN_GAME
                uiManager.hideLoadingScreen()
            } else {
                // If loading failed (e.g., file corrupted), go back to the menu
                currentGameMode = GameMode.START_MENU
                uiManager.showStartMenu()
                uiManager.hideLoadingScreen()
                uiManager.showTemporaryMessage("Error: Could not load save file.")
            }
        }
    }

    /**
     * This is called from the UI to create a new game.
     */
    fun startNewGame(saveName: String) {
        uiManager.hideStartMenu()
        currentGameMode = GameMode.LOADING
        uiManager.renderLoadingScreen()

        if (!initialSystemsLoaded) {
            loadAllGameSystems()
            inputHandler.initialize()
            uiManager.applyAllSettingsFromManager()
        }

        Gdx.app.postRunnable {
            val success = saveLoadSystem.startNewGame(saveName)
            if (success) {
                startGame(saveName)
            } else {
                currentGameMode = GameMode.START_MENU
                uiManager.showStartMenu()
                uiManager.hideLoadingScreen()
                uiManager.showTemporaryMessage("Error: Could not create new game.")
            }
        }
    }

    fun returnToStartMenu() {
        // Safety check to prevent running this if we're already in the menu
        if (currentGameMode != GameMode.IN_GAME) return

        println("Returning to Start Menu...")

        // 1. Unload the current game world and reset active systems
        sceneManager.clearActiveSceneForLoad()
        missionSystem.activeMission = null
        missionSystem.activeModifiers = null
        currentSaveFileName = null // Forget which save was loaded

        // 2. Hide all in-game specific UI elements
        uiManager.hideAllGameHUDs()
        uiManager.hidePauseMenu() // This will hide the pause menu and visual settings

        // 3. Reset the cursor so it's visible and usable in the menu
        Gdx.input.isCursorCatched = false

        // 4. Change the game state back to the start menu
        currentGameMode = GameMode.START_MENU

        // 5. Show the start menu UI
        uiManager.showStartMenu()
    }

    private fun setupGraphics() {
        //shaderProvider = BillboardShaderProvider()
        //shaderProvider.setBlockCartoonySaturation(1.3f)
        //modelBatch = ModelBatch(shaderProvider)
        val shaderConfig = DefaultShader.Config()
        shaderConfig.numPointLights = 16
        shaderConfig.numDirectionalLights = 1
        val shaderProvider = DefaultShaderProvider(shaderConfig)
        modelBatch = ModelBatch(shaderProvider)
        spriteBatch = SpriteBatch()

        // Initialize camera manager
        cameraManager = CameraManager()
        cameraManager.game = this
        cameraManager.initialize()

        // Initialize lighting manager
        lightingManager = LightingManager()
        lightingManager.game = this
        lightingManager.initialize()
    }

    private fun handlePlayerInput() {
        val deltaTime = Gdx.graphics.deltaTime

        // House Interaction Logic
        handleInteractionInput()

        if (cameraManager.isFreeCameraMode) {
            // Handle free camera movement
            cameraManager.handleInput(deltaTime)
        } else {
            // Handle player movement through PlayerSystem
            val allBlocks = sceneManager.activeChunkManager.getAllBlocks()

            // Now, call the function with the list it expects
            val moved = playerSystem.handleMovement(
                deltaTime,
                sceneManager,
                sceneManager.activeCars,
                particleSystem
            )

            if (moved) {
                val isDriving = playerSystem.isDriving
                // Update camera manager with player position
                cameraManager.setPlayerPosition(playerSystem.getControlledEntityPosition(), isDriving)

                // Auto-switch to player camera when moving
                cameraManager.switchToPlayerCamera()
            }

            // Handle camera input for player camera mode
            cameraManager.handleInput(deltaTime)
        }
    }

    private fun startStandaloneDialog(character: Any, dialogInfo: StandaloneDialog) {
        val hasBeenCompleted = when (character) {
            is GameEnemy -> character.standaloneDialogCompleted
            is GameNPC -> character.standaloneDialogCompleted
            else -> false
        }

        val dialogIdToUse = if (hasBeenCompleted && dialogInfo.postBehavior == PostDialogBehavior.REPEATABLE_NO_REWARD && dialogInfo.alternativeDialogId != null) {
            println("Character has completed this dialog. Using alternative: ${dialogInfo.alternativeDialogId}")
            dialogInfo.alternativeDialogId
        } else {
            dialogInfo.dialogId
        }

        var dialogSequence = dialogueManager.getDialogue(dialogIdToUse)
        if (dialogSequence == null) {
            println("ERROR: Character has standalone dialog '$dialogIdToUse', but it was not found.")
            return
        }

        // Add confirmation choices for transactions
        val outcome = dialogInfo.outcome
        if (outcome.type in listOf(DialogOutcomeType.SELL_ITEM_TO_PLAYER, DialogOutcomeType.BUY_ITEM_FROM_PLAYER, DialogOutcomeType.TRADE_ITEM)) {
            // Only add confirmation choices if the dialog has NOT been completed.
            if (!hasBeenCompleted) {
                val originalLines = dialogSequence.lines.toMutableList()
                val confirmationLineText = when (outcome.type) {
                    DialogOutcomeType.SELL_ITEM_TO_PLAYER -> "What do you say?"
                    DialogOutcomeType.BUY_ITEM_FROM_PLAYER -> "Is that a deal?"
                    DialogOutcomeType.TRADE_ITEM -> "Do we have a deal?"
                    else -> "..."
                }
                val confirmationChoices = listOf(
                    DialogChoice("Deal") {
                        executeDialogOutcome(character, outcome)
                        uiManager.dialogSystem.skipAll()
                    },
                    DialogChoice("Cancel") {
                        uiManager.dialogSystem.skipAll()
                    }
                )
                val lastSpeaker = originalLines.lastOrNull()?.speaker ?: "System"
                val confirmationLine = DialogLine(lastSpeaker, confirmationLineText, null, confirmationChoices)
                originalLines.add(confirmationLine)
                dialogSequence = dialogSequence.copy(lines = originalLines)
            }
        }

        val sequenceWithCallback = dialogSequence.copy(
            onComplete = {
                println("Standalone dialog finished. Behavior: ${dialogInfo.postBehavior}")

                val shouldGiveReward = when (character) {
                    is GameEnemy -> !character.standaloneDialogCompleted
                    is GameNPC -> !character.standaloneDialogCompleted
                    else -> true // Failsafe for other types
                }

                if (shouldGiveReward && outcome.type !in listOf(DialogOutcomeType.SELL_ITEM_TO_PLAYER, DialogOutcomeType.BUY_ITEM_FROM_PLAYER, DialogOutcomeType.TRADE_ITEM)) {
                    executeDialogOutcome(character, dialogInfo.outcome)
                }

                when (dialogInfo.postBehavior) {
                    PostDialogBehavior.REPEATABLE -> {
                        // Do nothing, it will just repeat next time.
                    }
                    PostDialogBehavior.REPEATABLE_NO_REWARD,
                    PostDialogBehavior.ONE_TIME_HIDE_ICON -> {
                        when (character) {
                            is GameEnemy -> character.standaloneDialogCompleted = true
                            is GameNPC -> character.standaloneDialogCompleted = true
                        }
                    }
                    PostDialogBehavior.ONE_TIME_DESPAWN -> {
                        when (character) {
                            is GameEnemy -> {
                                character.standaloneDialogCompleted = true
                                character.scheduledForDespawn = true
                            }
                            is GameNPC -> {
                                character.standaloneDialogCompleted = true
                                character.scheduledForDespawn = true
                            }
                        }
                    }
                }
            }
        )

        uiManager.dialogSystem.startDialog(sequenceWithCallback, dialogInfo.outcome)
    }

    private fun executeDialogOutcome(character: Any?, outcome: DialogOutcome) {
        when (outcome.type) {
            DialogOutcomeType.NONE -> {
                // Nothing to do, the conversation is over.
            }
            DialogOutcomeType.GIVE_ITEM -> {
                if (outcome.itemToGive != null) {
                    if (outcome.itemToGive.correspondingWeapon != null) {
                        val weapon = outcome.itemToGive.correspondingWeapon
                        val countOrAmmo = if (weapon.actionType == WeaponActionType.SHOOTING) {
                            outcome.ammoToGive ?: weapon.magazineSize
                        } else {
                            1
                        }
                        playerSystem.addWeaponToInventory(weapon, countOrAmmo)
                    } else {
                        if (outcome.itemToGive == ItemType.MONEY_STACK) {
                            val amount = outcome.ammoToGive ?: outcome.itemToGive.value
                            playerSystem.addMoney(amount)
                        } else {
                            println("Player received item: ${outcome.itemToGive.displayName}")
                        }
                    }
                }
            }
            DialogOutcomeType.SELL_ITEM_TO_PLAYER -> {
                if (outcome.itemToGive != null && outcome.price != null && outcome.itemToGive.correspondingWeapon != null) {
                    if (playerSystem.getMoney() >= outcome.price) {
                        playerSystem.addMoney(-outcome.price)
                        val weapon = outcome.itemToGive.correspondingWeapon
                        val countOrAmmo = if (weapon.actionType == WeaponActionType.SHOOTING) {
                            outcome.ammoToGive ?: weapon.magazineSize
                        } else {
                            1
                        }
                        playerSystem.addWeaponToInventory(weapon, countOrAmmo)
                        uiManager.showTemporaryMessage("You bought ${outcome.itemToGive.displayName} for $${outcome.price}.")
                    } else {
                        uiManager.showTemporaryMessage("You don't have enough money.")
                    }
                }
            }
            DialogOutcomeType.BUY_ITEM_FROM_PLAYER -> {
                if (outcome.requiredItem != null && outcome.price != null && outcome.requiredItem.correspondingWeapon != null) {
                    val requiredWeapon = outcome.requiredItem.correspondingWeapon
                    if (playerSystem.hasWeapon(requiredWeapon)) {
                        playerSystem.removeWeaponFromInventory(requiredWeapon)
                        playerSystem.addMoney(outcome.price)

                        if (character is GameEnemy) {
                            val currentAmmo = character.weapons.getOrDefault(requiredWeapon, 0)
                            val ammoToGain = requiredWeapon.magazineSize // Give them a full magazine for it
                            character.weapons[requiredWeapon] = currentAmmo + ammoToGain
                            println("${character.enemyType.displayName} now has a ${requiredWeapon.displayName} in their inventory.")
                        }
                        // You could add a similar 'else if (character is GameNPC)' block here if you add a weapon inventory to GameNPC

                        uiManager.showTemporaryMessage("You sold ${outcome.requiredItem.displayName} for $${outcome.price}.")
                    } else {
                        uiManager.showTemporaryMessage("You don't have a ${outcome.requiredItem.displayName} to sell.")
                    }
                }
            }
            DialogOutcomeType.TRADE_ITEM -> {
                if (outcome.requiredItem != null && outcome.itemToGive != null && outcome.requiredItem.correspondingWeapon != null && outcome.itemToGive.correspondingWeapon != null) {
                    val requiredWeapon = outcome.requiredItem.correspondingWeapon
                    if (playerSystem.hasWeapon(requiredWeapon)) {
                        playerSystem.removeWeaponFromInventory(requiredWeapon)

                        if (character is GameEnemy) {
                            val currentAmmo = character.weapons.getOrDefault(requiredWeapon, 0)
                            val ammoToGain = requiredWeapon.magazineSize
                            character.weapons[requiredWeapon] = currentAmmo + ammoToGain
                            println("${character.enemyType.displayName} received your ${requiredWeapon.displayName} in the trade.")
                        }
                        // You could add a similar 'else if (character is GameNPC)' block here

                        val rewardWeapon = outcome.itemToGive.correspondingWeapon
                        val rewardAmmo = outcome.ammoToGive ?: rewardWeapon.magazineSize
                        playerSystem.addWeaponToInventory(rewardWeapon, rewardAmmo)
                        uiManager.showTemporaryMessage("You traded ${outcome.requiredItem.displayName} for ${outcome.itemToGive.displayName}.")
                    } else {
                        uiManager.showTemporaryMessage("You don't have the required item: ${outcome.requiredItem.displayName}.")
                    }
                }
            }
        }
    }

    private fun handleInteractionInput() {
        if (playerSystem.isDead()) {
            return
        }

        // Prevent interaction while a transition is active
        if (sceneManager.isTransitioning()) {
            return
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            // If the player is currently driving
            if (playerSystem.isDriving) {

                // Get all blocks from the chunk manager first
                val allBlocks = sceneManager.activeChunkManager.getAllBlocks()

                // Now, call the function with the list it expects
                playerSystem.exitCar(sceneManager)
                return
            }

            val playerPosForTeleport = playerSystem.getPosition()
            val closestTeleporter = teleporterSystem.findClosestTeleporter(playerPosForTeleport, 3f)

            if (closestTeleporter != null) {
                closestTeleporter.linkedTeleporterId?.let { destId ->
                    val destination = teleporterSystem.activeTeleporters.find { it.id == destId }
                    if (destination != null) {
                        // Step 1: Attempt to teleport and capture the result
                        val teleportSucceeded = playerSystem.teleportTo(destination.gameObject.position)

                        // Step 2: If it succeeded, update the camera
                        if (teleportSucceeded) {
                            // Use the camera's dedicated function to instantly snap to the player's new position
                            cameraManager.resetAndSnapToPlayer(playerSystem.getPosition(), playerSystem.isDriving)
                        }
                        return // Interaction handled, stop here.
                    }
                }
            }

            when (sceneManager.currentScene) {
                SceneType.WORLD -> {
                    val playerPos = playerSystem.getPosition()
                    val closestCar = sceneManager.activeCars.minByOrNull { it.position.dst2(playerPos) }

                    // Check if a car is found and is close enough
                    if (closestCar != null && playerPos.dst(closestCar.position) < 8f) {

                        // CARJACKING
                        val driverSeat = closestCar.seats.firstOrNull()
                        val driver = driverSeat?.occupant

                        if (driver != null) {
                            val modifiers = sceneManager.game.missionSystem.activeModifiers
                            val isCarEffectivelyLocked = closestCar.isLocked && modifiers?.allCarsUnlocked != true

                            if (isCarEffectivelyLocked) {
                                // Car is locked, play the locked sound and PROVOKE the driver.
                                println("Cannot pull driver out. The car is locked.")
                                val soundIdToPlay = closestCar.assignedLockedSoundId ?: "CAR_LOCKED_V1"
                                sceneManager.game.soundManager.playSound(id = soundIdToPlay, position = closestCar.position)

                                val provocationAmount = 15f // Each attempt makes them 15% angrier
                                var shouldExitAndFight = false

                                when (driver) {
                                    is GameEnemy -> {
                                        driver.carProvocation += provocationAmount
                                        println("${driver.enemyType.displayName} provocation is now ${driver.carProvocation}")
                                        // Enemies have a high chance to fight back if provoked enough
                                        if (driver.carProvocation >= 100f && Random.nextFloat() < 0.8f) {
                                            shouldExitAndFight = true
                                        }
                                    }
                                    is GameNPC -> {
                                        driver.carProvocation += provocationAmount
                                        println("${driver.npcType.displayName} provocation is now ${driver.carProvocation}")
                                        // NPCs only fight back if their personality allows it, and it's a small chance
                                        if (driver.carProvocation >= 100f && driver.reactionToDamage == DamageReaction.FIGHT_BACK && Random.nextFloat() < 0.25f) {
                                            shouldExitAndFight = true
                                        }
                                    }
                                }

                                if (shouldExitAndFight) {
                                    println("Driver has been provoked and is exiting the car to fight!")
                                    when (driver) {
                                        is GameEnemy -> sceneManager.enemySystem.handleEjectionFromCar(driver, sceneManager)
                                        is GameNPC -> sceneManager.npcSystem.handleEjectionFromCar(driver, closestCar, sceneManager)
                                    }
                                }

                                return // Stop the interaction here.
                            }

                            // If the car is not locked
                            var canPullDriver = false
                            when (driver) {
                                is GameEnemy -> canPullDriver = driver.canBePulledFromCar
                                is GameNPC -> canPullDriver = driver.canBePulledFromCar
                            }

                            if (canPullDriver) {
                                // Success! Pull the driver out.
                                println("Player is pulling the driver out of the car!")

                                val soundIdToPlay = closestCar.assignedOpenSoundId ?: "CAR_DOOR_OPEN_V1"
                                sceneManager.game.soundManager.playSound(id = soundIdToPlay, position = closestCar.position)

                                when (driver) {
                                    is GameEnemy -> sceneManager.enemySystem.handleEjectionFromCar(driver, sceneManager)
                                    is GameNPC -> sceneManager.npcSystem.handleEjectionFromCar(driver, closestCar, sceneManager)
                                }
                            } else {
                                // Failed! Driver resists.
                                println("Driver is resisting! Can't open the door.")
                                val soundIdToPlay = closestCar.assignedLockedSoundId ?: "CAR_LOCKED_V1"
                                sceneManager.game.soundManager.playSound(id = soundIdToPlay, position = closestCar.position)
                            }
                            return // Interaction handled, stop here.
                        }

                        // If the car was empty, this logic will now correctly run
                        val modifiers = sceneManager.game.missionSystem.activeModifiers
                        if (closestCar.isLocked && modifiers?.allCarsUnlocked != true) {
                            println("This car is locked.")
                            val soundIdToPlay = closestCar.assignedLockedSoundId ?: "CAR_LOCKED_V1"
                            sceneManager.game.soundManager.playSound(id = soundIdToPlay, position = closestCar.position)
                            return
                        }

                        playerSystem.enterCar(closestCar)
                        return // Interaction handled, stop here.
                    }

                    // Check for NPC interaction before checking for houses
                    val closestNpc = sceneManager.activeNPCs.minByOrNull { it.position.dst2(playerPos) }
                    if (closestNpc != null && playerPos.dst(closestNpc.position) < 5f) {

                        // Check if this NPC is the start trigger for any available mission
                        val missionToStart = triggerSystem.findMissionForNpc(closestNpc.id)

                        if (missionToStart != null) {
                            if (!missionToStart.startTrigger.dialogId.isNullOrBlank()) {
                                missionSystem.startMissionDialog(missionToStart)
                            } else {
                                missionSystem.startMission(missionToStart.id)
                            }
                            return // Interaction handled, stop here.
                        }
                        if (missionSystem.checkTalkToNpcObjective(closestNpc.id)) {
                            return
                        }
                        val npcDialog = closestNpc.standaloneDialog
                        if (npcDialog != null) {
                            val allowInteraction = !closestNpc.standaloneDialogCompleted || npcDialog.postBehavior == PostDialogBehavior.REPEATABLE_NO_REWARD
                            if (allowInteraction) {
                                startStandaloneDialog(closestNpc, npcDialog)
                                return // Interaction handled, stop here.
                            }
                            // If no new mission starts, check if this NPC completes an ACTIVE objective
                        }
                        println("Player is near NPC '${closestNpc.npcType.displayName}', but they have nothing to say right now.")
                    }

                    // --- Check for Enemy interaction ---
                    val closestEnemy = sceneManager.activeEnemies.minByOrNull { it.position.dst2(playerPos) }
                    if (closestEnemy != null && playerPos.dst(closestEnemy.position) < 5f && closestEnemy.currentState == AIState.IDLE) {
                        // MODIFIED: Replaced the old check here as well
                        val enemyDialog = closestEnemy.standaloneDialog
                        if (enemyDialog != null) {
                            val allowInteraction = !closestEnemy.standaloneDialogCompleted || enemyDialog.postBehavior == PostDialogBehavior.REPEATABLE_NO_REWARD
                            if (allowInteraction) {
                                startStandaloneDialog(closestEnemy, enemyDialog)
                                return
                            }
                        }
                    }

                    // Try to enter a house
                    val closestHouse = sceneManager.activeHouses.minByOrNull { it.position.dst2(playerPos) }

                    if (closestHouse != null && playerPos.dst(closestHouse.position) < 15f) {
                        // First, check if the "house" is even enterable (e.g., not a stair model).
                        if (!closestHouse.houseType.canHaveRoom) {
                            return
                        }

                        val missionModifiers = missionSystem.activeModifiers
                        var isEffectivelyLocked = closestHouse.isLocked

                        // Mission modifiers can override the house's individual lock state
                        if (missionModifiers != null) {
                            if (missionModifiers.allHousesLocked) {
                                isEffectivelyLocked = true // Mission forces all houses to be locked
                            } else if (missionModifiers.allHousesUnlocked) {
                                isEffectivelyLocked = false // Mission forces all houses to be open
                            }
                        }

                        if (isEffectivelyLocked) {
                            println("This house is locked.")
                            // Here you could play a "locked door" sound or show a UI message
                            return // Stop the interaction
                        }

                        // We now calculate the HORIZONTAL distance, ignoring the Y-axis.
                        val entryPointPosition: Vector3
                        val entryRadius: Float

                        if (closestHouse.entryPointId != null) {
                            // This house has a CUSTOM entry point
                            val customEntryPoint = sceneManager.activeEntryPoints.find { it.id == closestHouse.entryPointId }
                            if (customEntryPoint != null) {
                                entryPointPosition = customEntryPoint.position
                                entryRadius = 3.5f
                            } else {
                                // Fallback if ID is invalid (shouldn't happen)
                                entryPointPosition = closestHouse.position.cpy().add(closestHouse.houseType.doorOffset)
                                entryRadius = 5f
                            }
                        } else {
                            // This house uses the DEFAULT hard-coded entry point
                            entryPointPosition = closestHouse.position.cpy().add(closestHouse.houseType.doorOffset)
                            entryRadius = 5f // Larger radius for less precise default points
                            println("Checking against default door offset.")
                        }

                        // Check the 2D distance on the ground plane.
                        if (playerPos.dst(entryPointPosition) < entryRadius) {
                            sceneManager.transitionToInterior(closestHouse)
                        }
                    }
                }
                SceneType.HOUSE_INTERIOR -> {
                    // If we are in the special placement mode, 'E' does nothing.
                    if (isPlacingExitDoorMode) {
                        println("You must place the designated exit door first!")
                        uiManager.setPersistentMessage("ACTION LOCKED: Place the EXIT DOOR to continue.")
                        return
                    }

                    // IMPROVED EXIT LOGIC
                    val currentHouse = sceneManager.getCurrentHouse()
                    if (currentHouse == null) {
                        println("Error: Cannot find current house data.")
                        return
                    }

                    val exitDoorId = currentHouse.exitDoorId
                    if (exitDoorId == null) {
                        println("This house has no designated exit! This shouldn't happen in normal gameplay.")
                        sceneManager.game.uiManager.enterExitDoorPlacementMode(currentHouse)
                        return
                    }

                    // Find the one specific door that is the exit
                    val exitDoor = sceneManager.activeInteriors.find { it.id == exitDoorId }

                    if (exitDoor == null) {
                        println("Error: The designated exit door (ID: $exitDoorId) is missing from the room!")
                        return
                    }

                    val playerPos = playerSystem.getPosition()

                    // Collision detection for doors
                    if (isPlayerNearDoor(playerPos, exitDoor)) {
                        println("Player is at the designated exit. Leaving...")
                        sceneManager.transitionToWorld()
                    }
                }
                else -> {}
            }
            if (!playerSystem.isDriving) { // Don't switch weapons if driving
                playerSystem.switchToNextWeapon()
            }
        }
    }

    private fun isPlayerNearDoor(playerPos: Vector3, door: GameInterior): Boolean {
        val playerBounds = playerSystem.getPlayerBounds()

        // Create a slightly expanded player bounds for door interaction
        val expandedBounds = BoundingBox(playerBounds)
        val expansion = 0.5f // Expand by 0.5 units in all directions
        expandedBounds.set(
            expandedBounds.min.sub(expansion, 0f, expansion),
            expandedBounds.max.add(expansion, 0f, expansion)
        )

        // Use the special door bounding box
        val doorBounds = door.getBoundingBoxForDoor()

        val intersects = expandedBounds.intersects(doorBounds)

        if (intersects) {
            println("Door collision detected using bounding box intersection!")
        } else {
            println("No door collision. Player bounds: ${expandedBounds.min} to ${expandedBounds.max}")
            println("Door bounds: ${doorBounds.min} to ${doorBounds.max}")
        }

        return intersects
    }

    internal fun handleFinePosMove(deltaX: Float, deltaY: Float, deltaZ: Float) {
        if (lastPlacedInstance == null) {
            println("No object selected to move. Place an object first.")
            return
        }

        when (val instance = lastPlacedInstance) {
            is GameCar -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                instance.updateTransform() // Uses the car's specific update method
                println("Moved Car to ${instance.position}")
            }
            is GameObject -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                instance.modelInstance.transform.setTranslation(instance.position)
                instance.debugInstance?.transform?.setTranslation(instance.position)

                // If it has an associated light, move that too.
                instance.associatedLightId?.let { lightId ->
                    val lightSource = lightingManager.getLightSources()[lightId]
                    if (lightSource != null) {
                        // The light's position must follow the object's position, including its offset.
                        val objectType = instance.objectType
                        lightSource.position.set(
                            instance.position.x,
                            instance.position.y + objectType.lightOffsetY,
                            instance.position.z
                        )
                        // Update the light's render data and models
                        lightSource.updateTransform()
                        lightSource.updatePointLight()
                    }
                }
                println("Moved Object to ${instance.position}")
            }
            is GameFire -> {
                // Move the fire's underlying GameObject
                instance.gameObject.position.add(deltaX, deltaY, deltaZ)
                instance.gameObject.modelInstance.transform.setTranslation(instance.gameObject.position)

                // Also move the fire's associated light source
                instance.gameObject.associatedLightId?.let { lightId ->
                    val lightSource = lightingManager.getLightSources()[lightId]
                    if (lightSource != null) {
                        val objectType = instance.gameObject.objectType
                        lightSource.position.set(
                            instance.gameObject.position.x,
                            instance.gameObject.position.y + objectType.lightOffsetY,
                            instance.gameObject.position.z
                        )
                        lightSource.updateTransform()
                        lightSource.updatePointLight()
                    }
                }
                println("Moved Fire to ${instance.gameObject.position}")
            }
            is LightSource -> {
                // Use lighting manager for movement
                val moved = lightingManager.moveLightSource(instance.id, deltaX, deltaY, deltaZ)
                if (moved) {
                    instance.updateTransform()
                    println("Moved Light to ${instance.position}")
                }
            }
            is GameHouse -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                // Apply uniform scaling to all house types
                instance.updateTransform()
                println("Moved House to ${instance.position}")
            }
            is GameItem -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                println("Moved Item to ${instance.position}")
            }
            is GameBackground -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                instance.modelInstance.transform.setTranslation(instance.position)
                println("Moved Background to ${instance.position}")
            }
            is GameInterior -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                instance.updateTransform()
                println("Moved Interior to ${instance.position}")
            }
            is GameEnemy -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                instance.updateVisuals()
                println("Moved Enemy to ${instance.position}")
            }
            is GameNPC -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                instance.updateVisuals()
                println("Moved NPC to ${instance.position}")
            }
            is GameSpawner -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                instance.gameObject.position.set(instance.position)
                instance.gameObject.modelInstance.transform.setTranslation(instance.position)
                instance.gameObject.debugInstance?.transform?.setTranslation(instance.position)
                println("Moved Spawner to ${instance.position}")
            }
            is GameTeleporter -> {
                instance.gameObject.position.add(deltaX, deltaY, deltaZ)
                instance.gameObject.modelInstance.transform.setTranslation(instance.gameObject.position)
                instance.gameObject.debugInstance?.transform?.setTranslation(instance.gameObject.position)
                println("Moved Teleporter to ${instance.gameObject.position}")
            }
            is GameEntryPoint -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                instance.debugInstance.transform.setTranslation(instance.position)
                println("Moved Entry Point to ${instance.position}")
            }
            is CarPathNode -> {
                instance.position.add(deltaX, deltaY, deltaZ)
                // The visual will update automatically in the render loop
                println("Moved Path Node to ${instance.position}")
            }
            is MissionTrigger -> {
                instance.areaCenter.add(deltaX, deltaY, deltaZ)
                println("Moved Trigger to ${instance.areaCenter}")
            }
            else -> println("Fine positioning not supported for this object type.")
        }
    }

    fun toggleInvisibleBlockOutlines() {
        showInvisibleBlockOutlines = !showInvisibleBlockOutlines
        val status = if (showInvisibleBlockOutlines) "ON" else "OFF"
        uiManager.updatePlacementInfo("Invisible Block Outlines: $status")
    }

    fun toggleEditorMode() {
        isEditorMode = !isEditorMode

        // If we just switched OUT of editor mode, hide all editor-specific UI panels.
        if (!isEditorMode) {
            uiManager.hideAllEditorPanels()
            uiManager.updatePlacementInfo("")
        }
    }

    fun toggleBlockCollisionOutlines() {
        showBlockCollisionOutlines = !showBlockCollisionOutlines
        val status = if (showBlockCollisionOutlines) "ON" else "OFF"
        uiManager.updatePlacementInfo("Block Collision Outlines: $status")
    }


    private fun updateCursorVisibility() {
        val shouldCatchCursor = !isEditorMode && !uiManager.isCursorRequired()

        // if the current state doesn't match what it should be.
        if (Gdx.input.isCursorCatched != shouldCatchCursor) {
            Gdx.input.isCursorCatched = shouldCatchCursor
        }
    }

    override fun render() {
        when (currentGameMode) {
            GameMode.START_MENU, GameMode.LOADING -> {
                // In menu or loading state, just clear the screen and render UI
                Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
                Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f)
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

                // Render UI (start menu or a "Loading..." screen)
                if (currentGameMode == GameMode.LOADING) {
                    uiManager.renderLoadingScreen()
                }
                uiManager.render()
                return
            }

            GameMode.IN_GAME -> {
                val rawDeltaTime = Gdx.graphics.deltaTime
                val MAX_DELTA_TIME = 0.1f
                val deltaTime = minOf(rawDeltaTime, MAX_DELTA_TIME)

                updateCursorVisibility()
                // Begin capturing the frame for post-processing
                shaderEffectManager.beginCapture()

                // Clear screen
                Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)

                // Check if we are in an interior
                val currentSceneType = sceneManager.currentScene
                val isInInterior = currentSceneType == SceneType.HOUSE_INTERIOR || currentSceneType == SceneType.TRANSITIONING_TO_WORLD

                // If in an interior, use a black background
                val clearColor = if (isInInterior) Color.BLACK else lightingManager.getCurrentSkyColor() // Get current sky color for clearing
                Gdx.gl.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a)
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

                val isPaused = uiManager.isGamePaused()

                if (!isPaused) {
                    val timeMultiplier = if (inputHandler.isTimeSpeedUpActive()) 200f else 1f
                    musicManager.update(deltaTime)
                    soundManager.update(playerSystem.getControlledEntityPosition())
                    ambientSoundSystem.update(deltaTime)
                    audioEmitterSystem.update(deltaTime)
                    val isSinCityEffect = shaderEffectManager.isEffectsEnabled && shaderEffectManager.getCurrentEffect() == ShaderEffect.SIN_CITY

                    lightingManager.setGrayscaleMode(isSinCityEffect)

                    sceneManager.update(deltaTime)
                    missionSystem.update(deltaTime)
                    objectiveArrowSystem.update()

                    triggerSystem.update()
                    transitionSystem.update(deltaTime)

                    // Update lighting manager
                    lightingManager.update(deltaTime, cameraManager.camera.position, timeMultiplier, isInInterior)

                    weatherSystem.update(deltaTime, isInInterior)

                    val expiredLightIds = lightingManager.collectAndClearExpiredLights()
                    if (expiredLightIds.isNotEmpty()) {
                        expiredLightIds.forEach { id ->
                            // Also remove it from the object system so it doesn't leave a ghost object
                            objectSystem.removeLightSource(id)
                        }
                    }

                    // Update input handler for continuous actions
                    inputHandler.update(deltaTime)

                    // Handle player input
                    handlePlayerInput()
                    if (isEditorMode) {
                        carPathSystem.update(cameraManager.camera)
                        characterPathSystem.update(cameraManager.camera)
                    }
                    particleSystem.update(deltaTime, weatherSystem, isInInterior)
                    spawnerSystem.update(deltaTime, sceneManager.activeSpawners, playerSystem.getPosition())

                    val expiredFires = fireSystem.update(deltaTime, playerSystem, particleSystem, sceneManager, weatherSystem, isInInterior)
                    if (expiredFires.isNotEmpty()) {
                        for (fireToRemove in expiredFires) {
                            sceneManager.activeObjects.removeValue(fireToRemove.gameObject, true)
                            fireSystem.removeFire(fireToRemove, objectSystem, lightingManager)
                        }
                    }

                    if (!isEditorMode) {
                        trajectorySystem.update(playerSystem, sceneManager)
                    }

                    bulletTrailSystem.update(deltaTime)
                    playerSystem.update(deltaTime, sceneManager, weatherSystem, isInInterior)
                    enemySystem.update(deltaTime, playerSystem, sceneManager, blockSize, weatherSystem, isInInterior)
                    npcSystem.update(deltaTime, playerSystem, sceneManager, blockSize, weatherSystem, isInInterior)
                    bloodPoolSystem.update(deltaTime, sceneManager.activeBloodPools, weatherSystem, isInInterior)
                    waterPuddleSystem.update(deltaTime, weatherSystem, isInInterior)
                    footprintSystem.update(deltaTime, sceneManager.activeFootprints, weatherSystem, isInInterior)
                    decalSystem.update(deltaTime)

                    // Handle car destruction and removals
                    carSystem.update(deltaTime, sceneManager)

                    // Update the lock indicator based on player, car, and house positions
                    lockIndicatorSystem.update(playerSystem.getPosition(), playerSystem.isDriving, sceneManager.activeCars, sceneManager.activeHouses)

                    // Update item system (animations, collisions, etc.)
                    itemSystem.update(deltaTime, cameraManager.camera, playerSystem, sceneManager)

                    sceneManager.activeChunkManager.processDirtyChunks()
                }

                // Update highlight system
                if (isEditorMode && !isPaused) {
                    highlightSystem.update(
                        cameraManager,
                        uiManager,
                        blockSystem,
                        sceneManager.activeChunkManager.getAllBlocks(),
                        sceneManager.activeObjects,
                        sceneManager.activeSpawners,
                        sceneManager.activeCars,
                        sceneManager.activeHouses,
                        backgroundSystem,
                        parallaxBackgroundSystem,
                        sceneManager.activeItems,
                        objectSystem,
                        raycastSystem,
                        sceneManager.activeInteriors,
                        interiorSystem,
                        sceneManager.activeEnemies,
                        sceneManager.activeNPCs,
                        particleSystem
                    )
                } else if (!isEditorMode && !isPaused) {
                    // When not in editor mode, update the new targeting indicator instead
                    targetingIndicatorSystem.update(
                        cameraManager,
                        playerSystem,
                        sceneManager,
                        raycastSystem
                    )
                    // Update the new melee indicator
                    meleeRangeIndicatorSystem.update(playerSystem, sceneManager)
                }

                //shaderProvider.setEnvironment(environment)
                //println("MafiaGame.render: Passing environment to provider, hash: ${environment.hashCode()}")

                parallaxBackgroundSystem.update(cameraManager.camera.position)
                //occlusionSystem.update(cameraManager.camera, playerSystem.getPosition(), sceneManager.activeBlocks)

                val environment = lightingManager.getEnvironment()

                // Render 3D scene
                modelBatch.begin(cameraManager.camera)

                // Only render the sky and sun when we are in the outside world scene.
                if (!isInInterior) {
                    // Render sky FIRST
                    lightingManager.renderSky(modelBatch, cameraManager.camera)

                    // Render sun
                    lightingManager.renderSun(modelBatch, cameraManager.camera)
                }

                // Render parallax backgrounds
                parallaxBackgroundSystem.render(modelBatch, cameraManager.camera, environment)

                if (!isInInterior) {
                    weatherSystem.render(environment)
                }

                objectiveArrowSystem.render(cameraManager.camera, environment)

                // Render all blocks
                sceneManager.activeChunkManager.render(modelBatch, environment, cameraManager.camera)

                // Render all objects
                for (gameObject in sceneManager.activeObjects) {
                    if (gameObject.objectType != ObjectType.FIRE_SPREAD) {
                        gameObject.getRenderInstance(objectSystem.debugMode)?.let {
                            modelBatch.render(it, environment)
                        }
                    }
                }

                for (gameObject in sceneManager.activeMissionPreviewObjects) { // NEW
                    gameObject.getRenderInstance(objectSystem.debugMode)?.let {
                        modelBatch.render(it, environment)
                    }
                }

                for (spawner in sceneManager.activeSpawners) {
                    spawner.gameObject.getRenderInstance(objectSystem.debugMode)?.let {
                        modelBatch.render(it, environment)
                    }
                }

                // Render light sources
                if (isEditorMode) {
                    // Render light source debug visuals
                    lightingManager.renderLightInstances(modelBatch, environment, objectSystem.debugMode)
                    lightingManager.renderLightAreas(modelBatch)

                    // Render house entry point debug visuals
                    houseSystem.renderEntryPoints(modelBatch, environment, objectSystem)

                    // Render teleporter pad debug visuals
                    for (teleporter in teleporterSystem.activeTeleporters) {
                        teleporter.gameObject.getRenderInstance(objectSystem.debugMode)?.let {
                            modelBatch.render(it, environment)
                        }
                    }
                }

                for (house in sceneManager.activeHouses) {
                    modelBatch.render(house.modelInstance, environment)
                }

                for (house in sceneManager.activeMissionPreviewHouses) {
                    modelBatch.render(house.modelInstance, environment)
                }

                // Render backgrounds
                for (background in backgroundSystem.getBackgrounds()) {
                    modelBatch.render(background.modelInstance, environment)
                }

                for (interior in sceneManager.activeInteriors) {
                    // Render 3D models and new floor objects
                    if (interior.interiorType.is3D || interior.interiorType.isFloorObject) {
                        modelBatch.render(interior.instance, environment) // This will only render the 3D ones
                    }
                }

                if (isEditorMode) {
                    // Render background preview
                    backgroundSystem.renderPreview(modelBatch, cameraManager.camera, environment)
                }

                // Render cars first as they are mostly opaque
                carSystem.render(cameraManager.camera, environment, sceneManager.activeCars)
                carSystem.render(cameraManager.camera, environment, sceneManager.activeMissionPreviewCars)

                lockIndicatorSystem.render(cameraManager.camera, environment)

                // Render effects that should appear BEHIND characters
                fireSystem.render(cameraManager.camera, environment)

                // Render Blood Pool
                bloodPoolSystem.render(cameraManager.camera, environment, sceneManager.activeBloodPools)
                waterPuddleSystem.render(cameraManager.camera, environment)

                footprintSystem.render(cameraManager.camera, environment, sceneManager.activeFootprints)
                boneSystem.render(cameraManager.camera, environment, sceneManager.activeBones)
                decalSystem.render(cameraManager.camera, environment)

                // Render all potentially transparent billboards AFTER the fire
                playerSystem.render(cameraManager.camera, environment)

                enemySystem.renderEnemies(cameraManager.camera, environment, sceneManager.activeEnemies)
                enemySystem.renderEnemies(cameraManager.camera, environment, sceneManager.activeMissionPreviewEnemies)
                npcSystem.renderNPCs(cameraManager.camera, environment, sceneManager.activeNPCs)

                npcSystem.renderNPCs(cameraManager.camera, environment, sceneManager.activeMissionPreviewNPCs)
                particleSystem.render(cameraManager.camera, environment)

                // Render items
                itemSystem.render(cameraManager.camera, environment)

                itemSystem.render(cameraManager.camera, environment, sceneManager.activeMissionPreviewItems)

                if (!isEditorMode) {
                    trajectorySystem.render(cameraManager.camera, environment)
                }

                modelBatch.end()

                bulletTrailSystem.render(cameraManager.camera, sceneManager.getCurrentSceneId())

                if (isEditorMode) {
                    carPathSystem.render(cameraManager.camera)
                    characterPathSystem.render(cameraManager.camera)
                    audioEmitterSystem.render(cameraManager.camera)
                }

                teleporterSystem.renderNameplates(cameraManager.camera, playerSystem)
                triggerSystem.render(cameraManager.camera, lightingManager.getEnvironment())
                interiorSystem.renderBillboards(cameraManager.camera, environment, sceneManager.activeInteriors)

                if (isEditorMode) {
                    // Render highlight using HighlightSystem
                    highlightSystem.render(modelBatch, cameraManager.camera, environment)

                    if (interiorSystem.isPreviewActive()) {
                        interiorSystem.billboardModelBatch.begin(cameraManager.camera)
                        interiorSystem.renderPreview(interiorSystem.billboardModelBatch, environment)
                        interiorSystem.billboardModelBatch.end()
                    }

                    // Render block collision wireframes if enabled
                    if (showBlockCollisionOutlines) {
                        blockDebugRenderer.render(cameraManager.camera, environment, sceneManager.activeChunkManager.getAllBlocks())
                    }

                    if (showInvisibleBlockOutlines) {
                        highlightSystem.renderInvisibleBlockOutlines(
                            modelBatch,
                            environment,
                            cameraManager.camera,
                            sceneManager.activeChunkManager.getAllBlocks()
                        )
                    }
                } else {
                    // When not in editor mode, render the targeting indicator
                    targetingIndicatorSystem.render(cameraManager.camera, environment)
                    meleeRangeIndicatorSystem.render(cameraManager.camera, environment)
                }

                if (showBlockCollisionOutlines) { // We can reuse this toggle for convenience
                    blockDebugRenderer.renderBoundingBox(cameraManager.camera, playerSystem.getPlayerBounds(), Color.CYAN)
                }

                // Transition to 2D UI Rendering
                Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
                Gdx.gl.glDepthMask(false)
                Gdx.gl.glDisable(GL20.GL_CULL_FACE)
                Gdx.gl.glEnable(GL20.GL_BLEND)
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

                // Render UI using UIManager
                uiManager.updateFps()

                uiManager.render()

                // Render the transition animation ON TOP of everything else.
                transitionSystem.render()

                // End capture and apply post-processing effects
                shaderEffectManager.endCaptureAndRender()
                Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
                Gdx.gl.glDepthMask(true) // Allow writing to the depth buffer
                Gdx.gl.glDisable(GL20.GL_BLEND)
                Gdx.gl.glEnable(GL20.GL_CULL_FACE)
            }
        }
    }

    override fun resize(width: Int, height: Int) {
        if (width == 0 || height == 0) {
            return
        }

        // Resize UIManager's viewport
        uiManager.resize(width, height)
        cameraManager.resize(width, height)

        // Resize shader effect manager
        shaderEffectManager.resize(width, height)
    }

    override fun pause() {
        if (currentGameMode != GameMode.IN_GAME) return

        if (!uiManager.isGamePaused()) {
            println("Game window lost focus. Auto-pausing.")
            uiManager.togglePauseMenu() // This function already handles pausing sounds and music
        }
    }

    override fun resume() {
        println("Game window regained focus.")
    }

    override fun dispose() {
        if (::soundManager.isInitialized) soundManager.dispose()
        if (::musicManager.isInitialized) musicManager.dispose()
        if (::ambientSoundSystem.isInitialized) ambientSoundSystem.dispose()
        if (::audioEmitterSystem.isInitialized) audioEmitterSystem.dispose()
        if (::modelBatch.isInitialized) modelBatch.dispose()
        if (::spriteBatch.isInitialized) spriteBatch.dispose()
        if (::blockSystem.isInitialized) blockSystem.dispose()
        if (::objectSystem.isInitialized) objectSystem.dispose()
        if (::itemSystem.isInitialized) itemSystem.dispose()
        if (::carSystem.isInitialized) carSystem.dispose()
        if (::playerSystem.isInitialized) playerSystem.dispose()
        if (::houseSystem.isInitialized) houseSystem.dispose()
        if (::backgroundSystem.isInitialized) backgroundSystem.dispose()
        if (::highlightSystem.isInitialized) highlightSystem.dispose()
        if (::targetingIndicatorSystem.isInitialized) targetingIndicatorSystem.dispose()
        if (::meleeRangeIndicatorSystem.isInitialized) meleeRangeIndicatorSystem.dispose()
        if (::lockIndicatorSystem.isInitialized) lockIndicatorSystem.dispose()
        if (::lightingManager.isInitialized) lightingManager.dispose()
        if (::parallaxBackgroundSystem.isInitialized) parallaxBackgroundSystem.dispose()
        if (::interiorSystem.isInitialized) interiorSystem.dispose()
        if (::transitionSystem.isInitialized) transitionSystem.dispose()
        if (::enemySystem.isInitialized) enemySystem.dispose()
        if (::npcSystem.isInitialized) npcSystem.dispose()
        if (::particleSystem.isInitialized) particleSystem.dispose()
        if (::fireSystem.isInitialized) fireSystem.dispose()
        if (::bloodPoolSystem.isInitialized) bloodPoolSystem.dispose()
        if (::footprintSystem.isInitialized) footprintSystem.dispose()
        if (::waterPuddleSystem.isInitialized) waterPuddleSystem.dispose()
        if (::boneSystem.isInitialized) boneSystem.dispose()
        if (::objectiveArrowSystem.isInitialized) objectiveArrowSystem.dispose()
        if (::triggerSystem.isInitialized) triggerSystem.dispose()
        if (::bulletTrailSystem.isInitialized) bulletTrailSystem.dispose()
        if (::carPathSystem.isInitialized) carPathSystem.dispose()
        if (::characterPathSystem.isInitialized) characterPathSystem.dispose()
        if (::weatherSystem.isInitialized) weatherSystem.dispose()
        if (::decalSystem.isInitialized) decalSystem.dispose()

        // Dispose shader effect manager
        if (::shaderEffectManager.isInitialized) shaderEffectManager.dispose()

        // Dispose UIManager
        if (::teleporterSystem.isInitialized) teleporterSystem.dispose()
        if (::trajectorySystem.isInitialized) trajectorySystem.dispose()
        if (::blockDebugRenderer.isInitialized) blockDebugRenderer.dispose()

        // UIManager is always initialized, so it's safe to dispose.
        uiManager.dispose()
    }
}
