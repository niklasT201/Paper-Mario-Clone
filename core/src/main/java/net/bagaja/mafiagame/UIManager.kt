package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.DelayAction
import com.badlogic.gdx.scenes.scene2d.actions.SequenceAction
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Scaling
import com.badlogic.gdx.utils.viewport.ScreenViewport

enum class EditorMode {
    WORLD,
    MISSION
}

enum class HudStyle(val displayName: String) {
    WANTED_POSTER("Poster"),
    MINIMALIST("Minimalist")
}

enum class ViolenceLevel(val displayName: String) {
    NO_VIOLENCE("Off"),
    REDUCED_VIOLENCE("Reduced"),
    FULL_VIOLENCE("Full"),
    ULTRA_VIOLENCE("ULTRA VIOLENCE")
}

enum class HudInfoKey(val order: Int) {
    FPS(0),
    MONEY(1),
    WEAPON_PICKUP(2),      // NEW: Added the weapon pickup key
    MISSION_OBJECTIVE(3),  // MODIFIED: Order updated
    ENEMIES_LEFT(4),       // MODIFIED: Order updated
    MISSION_TIMER(5)       // MODIFIED: Order updated
}

data class HudInfoElement(val key: HudInfoKey, val actor: Actor, var isVisible: Boolean = false)

class UIManager(
    val game: MafiaGame,
    var shaderEffectManager: ShaderEffectManager
) {
    lateinit var blockSystem: BlockSystem
    lateinit var objectSystem: ObjectSystem
    lateinit var itemSystem: ItemSystem
    lateinit var carSystem: CarSystem
    lateinit var houseSystem: HouseSystem
    lateinit var backgroundSystem: BackgroundSystem
    lateinit var parallaxSystem: ParallaxBackgroundSystem
    lateinit var roomTemplateManager: RoomTemplateManager
    lateinit var interiorSystem: InteriorSystem
    lateinit var lightingManager: LightingManager
    lateinit var enemySystem: EnemySystem
    lateinit var npcSystem: NPCSystem
    lateinit var particleSystem: ParticleSystem
    lateinit var spawnerSystem: SpawnerSystem
    lateinit var dialogueManager: DialogueManager

    private lateinit var stage: Stage
    lateinit var skin: Skin
    private lateinit var layoutBuilder: UILayoutBuilder
    private lateinit var audioSettingsUI: AudioSettingsUI
    private lateinit var blockSelectionUI: BlockSelectionUI
    private lateinit var objectSelectionUI: ObjectSelectionUI
    private lateinit var itemSelectionUI: ItemSelectionUI
    private lateinit var carSelectionUI: CarSelectionUI
    private lateinit var houseSelectionUI: HouseSelectionUI
    private lateinit var backgroundSelectionUI: BackgroundSelectionUI
    private lateinit var parallaxSelectionUI: ParallaxSelectionUI
    private lateinit var interiorSelectionUI: InteriorSelectionUI
    lateinit var enemySelectionUI: EnemySelectionUI
    lateinit var npcSelectionUI: NPCSelectionUI
    private lateinit var particleSelectionUI: ParticleSelectionUI
    private lateinit var spawnerUI: SpawnerUI
    lateinit var missionEditorUI: MissionEditorUI
    private lateinit var lightSourceUI: LightSourceUI
    private lateinit var skyCustomizationUI: SkyCustomizationUI
    private lateinit var shaderEffectUI: ShaderEffectUI
    private lateinit var pauseMenuUI: PauseMenuUI
    private lateinit var visualSettingsUI: VisualSettingsUI
    private lateinit var enemyDebugUI: EnemyDebugUI
    private lateinit var characterInventoryUI: CharacterInventoryUI
    private lateinit var triggerEditorUI: TriggerEditorUI
    private lateinit var dialogueEditorUI: DialogueEditorUI
    private lateinit var mainTable: Table
    private lateinit var letterboxTable: Table
    private lateinit var cinematicBarsTable: Table
    private var isLetterboxVisible = false
    private var isCinematicBarsVisible = false

    private lateinit var wantedPosterTexture: Texture
    private lateinit var healthBar: ProgressBar
    private lateinit var weaponIconImage: Image
    private lateinit var fistTexture: Texture
    private var lastEquippedWeapon: WeaponType? = null
    private lateinit var weaponIconContainer: Container<Image>
    private lateinit var weaponUiTexture: Texture
    private lateinit var ammoUiContainer: Stack
    private lateinit var magazineAmmoLabel: Label
    private lateinit var reserveAmmoLabel: Label
    private lateinit var healthBarEmptyTexture: Texture
    private lateinit var healthBarFullTexture: Texture

    private lateinit var startMenuTable: Table
    private lateinit var startMenuOverlay: Image
    private lateinit var loadingScreenTable: Table
    private var currentHudStyle = HudStyle.MINIMALIST
    private lateinit var loadGameTable: Table
    private lateinit var saveFileIconTexture: Texture
    private var saveFileIconLoaded = false

    // HUD Tables
    private lateinit var wantedPosterHudTable: Table
    private lateinit var minimalistHudTable: Table
    private lateinit var healthLabelMinimalist: Label

    // Minimalist HUD elements
    private lateinit var weaponIconImageMinimalist: Image
    private lateinit var ammoLabelMinimalist: Label
    private lateinit var healthBarMinimalist: ProgressBar
    private lateinit var throwableCountLabelPoster: Label
    private var blinkTimer = 0f
    private lateinit var reloadIndicatorPoster: Label

    // Money Display elements
    private lateinit var moneyStackTexture: Texture
    private lateinit var toolButtons: MutableList<Table>
    private lateinit var statsLabels: MutableMap<String, Label>
    private lateinit var placementInfoLabel: Label
    private lateinit var persistentMessageLabel: Label
    private lateinit var temporaryMessageLabel: Label
    private lateinit var dynamicHudTable: Table
    private val dynamicHudElements = mutableListOf<HudInfoElement>()
    private lateinit var fpsLabel: Label
    private lateinit var moneyDisplayTable: Table
    private lateinit var moneyValueLabel: Label
    private lateinit var missionObjectiveLabel: Label
    private lateinit var missionTimerLabel: Label
    private lateinit var leaveCarTimerLabel: Label
    private lateinit var returnToAreaTimerLabel: Label
    private lateinit var enemiesLeftLabel: Label
    private var isTimerInDelayPhase = false

    private lateinit var notificationPatch: NinePatch
    private lateinit var notificationTable: Table
    private lateinit var notificationLabel: Label
    private lateinit var weaponPickupTable: Table
    private lateinit var weaponPickupIcon: Image
    private lateinit var weaponPickupLabel: Label
    private lateinit var weaponPickupStackLabel: Label
    private val weaponStackCountColor = Color.valueOf("#D3D3D3")
    private val inventoryChangeQueue = ArrayDeque<InventoryChangeInfo>()
    private var isWeaponNotificationActive = false
    private data class InventoryChangeInfo(
        val weaponType: WeaponType,
        var amount: Int, // Can be positive or negative. 'var' so we can update it for stacking.
        var stackCount: Int = 1
    )

    private lateinit var deathOverlay: Image
    private lateinit var deathTable: Table

    private var currentViolenceLevel = ViolenceLevel.FULL_VIOLENCE
    var currentEditorMode = EditorMode.WORLD
    var selectedMissionForEditing: MissionDefinition? = null

    private var isUIVisible = false
        private set
    var selectedTool = Tool.BLOCK

    var isPlacingExitDoorMode = false
        private set
    var houseRequiringDoor: GameHouse? = null
        private set

    var isPlacingEntryPointMode = false
        private set
    var houseRequiringEntryPoint: GameHouse? = null
        private set
    private var activeObjectiveDialog: Dialog? = null
    var isPlacingObjectiveArea = false
        private set
    var objectiveBeingPlaced: MissionObjective? = null
        private set

    enum class Tool {
        BLOCK, PLAYER, OBJECT, ITEM, CAR, HOUSE, BACKGROUND, PARALLAX, INTERIOR, ENEMY, NPC, PARTICLE, TRIGGER
    }

    lateinit var dialogSystem: DialogSystem

    fun initializeUI() {
        stage = Stage(ScreenViewport())
        skin = UISkinFactory.createSkin() // Use the factory
        layoutBuilder = UILayoutBuilder(skin) // Create the builder

        dialogSystem = DialogSystem()
        dialogSystem.uiManager = this
        dialogSystem.initialize(stage, skin)

        // Initialize UIs that can exist without a game world
        createStartMenu()
        createLoadGameScreen()
        setupLoadingScreen()
        setupMessageLabels()
        setupMissionNotification()
        setupLetterboxUI()
        setupCinematicBarsUI()
        setupDeathScreen()

        pauseMenuUI = PauseMenuUI(skin, stage, this, game.saveLoadSystem)
        pauseMenuUI.initialize()

        // Set the stage as the input processor
        Gdx.input.inputProcessor = stage
    }

    /**
     * This new method initializes UI elements that REQUIRE the game systems to be loaded.
     */
    fun initializeWorldDependent() {
        // Initialize all your selection UIs
        characterInventoryUI = CharacterInventoryUI(skin, stage, itemSystem)
        blockSelectionUI = BlockSelectionUI(blockSystem, skin, stage); blockSelectionUI.initialize()
        objectSelectionUI = ObjectSelectionUI(objectSystem, skin, stage); objectSelectionUI.initialize()
        itemSelectionUI = ItemSelectionUI(itemSystem, skin, stage); itemSelectionUI.initialize()
        carSelectionUI = CarSelectionUI(skin, stage, carSystem, enemySystem, npcSystem); carSelectionUI.initialize()
        houseSelectionUI = HouseSelectionUI(houseSystem, roomTemplateManager, skin, stage); houseSelectionUI.initialize()
        backgroundSelectionUI = BackgroundSelectionUI(backgroundSystem, skin, stage); backgroundSelectionUI.initialize()
        parallaxSelectionUI = ParallaxSelectionUI(parallaxSystem, skin, stage); parallaxSelectionUI.initialize()
        interiorSelectionUI = InteriorSelectionUI(interiorSystem, skin, stage); interiorSelectionUI.initialize()
        enemySelectionUI = EnemySelectionUI(enemySystem, skin, stage, dialogueManager); enemySelectionUI.initialize()
        npcSelectionUI = NPCSelectionUI(npcSystem, skin, stage, dialogueManager); npcSelectionUI.initialize()
        particleSelectionUI = ParticleSelectionUI(particleSystem, skin, stage); particleSelectionUI.initialize()
        spawnerUI = SpawnerUI(skin, stage, spawnerSystem::removeSpawner, this)
        missionEditorUI = MissionEditorUI(skin, stage, game.missionSystem, this)
        triggerEditorUI = TriggerEditorUI(skin, stage, game.missionSystem, game.triggerSystem, game.sceneManager, this)
        dialogueEditorUI = DialogueEditorUI(skin, stage, this, dialogueManager)
        lightSourceUI = LightSourceUI(skin, stage); lightSourceUI.initialize()
        skyCustomizationUI = SkyCustomizationUI(skin, stage, lightingManager); skyCustomizationUI.initialize()
        shaderEffectUI = ShaderEffectUI(skin, stage, shaderEffectManager); shaderEffectUI.initialize()
        visualSettingsUI = VisualSettingsUI(skin, game.cameraManager, this, game.targetingIndicatorSystem, game.trajectorySystem, game.meleeRangeIndicatorSystem, game.playerSystem)
        audioSettingsUI = AudioSettingsUI(skin, this, game.musicManager, game.soundManager)
        enemyDebugUI = EnemyDebugUI(skin, stage)

        // Setup main UI
        setupMainUI()
        setupGameHUD()
        setupDynamicHud()

        // Set initial visibility for the main UI panel
        mainTable.isVisible = isUIVisible && game.isEditorMode
    }

    private fun createStartMenu() {
        // Smoky Overlay
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(0.05f, 0.05f, 0.08f, 1f)
        pixmap.fill()
        startMenuOverlay = Image(Texture(pixmap))
        pixmap.dispose()
        startMenuOverlay.setFillParent(true)
        startMenuOverlay.isVisible = false
        stage.addActor(startMenuOverlay)

        // Main Menu Table
        startMenuTable = Table()
        startMenuTable.setFillParent(true)
        startMenuTable.center()
        startMenuTable.isVisible = false
        stage.addActor(startMenuTable)

        // Title
        val titleLabel = Label("MAFIA GAME", skin, "title")
        titleLabel.setFontScale(2.5f)
        titleLabel.color = Color.valueOf("#EAEAEA")
        startMenuTable.add(titleLabel).padBottom(50f).row()

        // Button Container
        val buttonsTable = Table()
        buttonsTable.defaults().pad(10f).width(300f).height(50f)

        // --- BUTTONS ---
        val startGameButton = TextButton("Start Game", skin)
        val newGameButton = TextButton("New Game", skin)
        val loadGameButton = TextButton("Load Game", skin)
        val settingsButton = TextButton("Settings", skin)
        val exitButton = TextButton("Exit", skin)

        buttonsTable.add(startGameButton).row()
        buttonsTable.add(newGameButton).row()
        buttonsTable.add(loadGameButton).row()
        buttonsTable.add(settingsButton).row() // And here
        buttonsTable.add(exitButton).row()

        startMenuTable.add(buttonsTable)

        // --- BUTTON LISTENERS ---
        startGameButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val mostRecent = game.saveLoadSystem.getMostRecentSave()
                if (mostRecent != null) {
                    game.startGame(mostRecent)
                } else {
                    // No saves exist, act like "New Game"
                    showNewGameDialog()
                }
            }
        })


        newGameButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                showNewGameDialog()
            }
        })

        loadGameButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                showLoadGameScreen()
            }
        })

        settingsButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                showTemporaryMessage("Settings will be available from the in-game pause menu.")
            }
        })

        exitButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                Gdx.app.exit()
            }
        })
    }

    private fun showNewGameDialog() {
        val dialog = Dialog("Start a New Game", skin, "dialog")
        val nameField = TextField("", skin)
        nameField.messageText = "Enter save name..."

        dialog.contentTable.add(Label("Save File Name:", skin)).pad(10f)
        dialog.contentTable.add(nameField).width(250f).pad(10f)

        val startButton = TextButton("Start", skin)
        dialog.button(startButton)
        dialog.button("Cancel")

        startButton.addListener(object: ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val name = nameField.text.trim()
                if (name.isNotBlank() && name.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                    game.startNewGame("$name.json")
                } else {
                    showTemporaryMessage("Invalid name. Use only letters, numbers, -, _")
                }
            }
        })
        dialog.show(stage)
        stage.keyboardFocus = nameField
    }

    private fun showLoadGameDialog() {
        val dialog = Dialog("Load Game", skin, "dialog")
        val saveFiles = game.saveLoadSystem.listSaveGames()

        if (saveFiles.isEmpty()) {
            dialog.text("No save files found.")
        } else {
            val list = List<String>(skin)
            list.setItems(*saveFiles.toTypedArray())
            val scrollPane = ScrollPane(list, skin)
            scrollPane.fadeScrollBars = false
            dialog.contentTable.add(scrollPane).width(350f).height(200f)

            val loadButton = TextButton("Load Selected", skin)
            dialog.button(loadButton)
            loadButton.addListener(object: ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    list.selected?.let { game.startGame(it) }
                }
            })
        }
        dialog.button("Cancel")
        dialog.show(stage)
    }

    fun showStartMenu() {
        loadingScreenTable.isVisible = false
        startMenuOverlay.isVisible = true
        startMenuTable.isVisible = true
        startMenuTable.toFront()

        // Animate in
        startMenuOverlay.color.a = 0f
        startMenuOverlay.addAction(Actions.fadeIn(0.5f))
        startMenuTable.color.a = 0f
        startMenuTable.addAction(Actions.sequence(Actions.delay(0.2f), Actions.fadeIn(0.5f)))
    }

    fun hideStartMenu() {
        hideLoadingScreen() // Add this call
        startMenuOverlay.addAction(Actions.sequence(Actions.fadeOut(0.3f), Actions.visible(false)))
        startMenuTable.addAction(Actions.sequence(Actions.fadeOut(0.3f), Actions.visible(false)))
    }

    private fun setupLoadingScreen() {
        loadingScreenTable = Table()
        loadingScreenTable.setFillParent(true)
        loadingScreenTable.center()

        val loadingLabel = Label("LOADING...", skin, "title")
        loadingLabel.setFontScale(1.5f)
        loadingScreenTable.add(loadingLabel)
        loadingScreenTable.isVisible = false // Start hidden
        stage.addActor(loadingScreenTable)
    }

    fun renderLoadingScreen() {
        if (!loadingScreenTable.isVisible) {
            loadingScreenTable.isVisible = true
            loadingScreenTable.toFront()
        }
    }

    fun hideLoadingScreen() {
        if (loadingScreenTable.isVisible) {
            loadingScreenTable.isVisible = false
        }
    }

    private fun createLoadGameScreen() {
        // --- Load the save file icon, with a fallback ---
        try {
            saveFileIconTexture = Texture(Gdx.files.internal("gui/save_icon.png"))
            saveFileIconLoaded = true
        } catch (e: Exception) {
            println("WARNING: Could not load 'gui/save_icon.png'. Creating fallback icon.")
            val pixmap = Pixmap(64, 64, Pixmap.Format.RGBA8888)
            // Draw a floppy disk icon
            pixmap.setColor(Color.valueOf("#a0a0b0")) // Gray
            pixmap.fillRectangle(5, 5, 54, 54)
            pixmap.setColor(Color.valueOf("#d0d0e0")) // Light Gray
            pixmap.fillRectangle(15, 40, 34, 18)
            pixmap.setColor(Color.valueOf("#505060")) // Dark Gray
            pixmap.fillRectangle(5, 5, 54, 15)
            pixmap.fillRectangle(28, 6, 8, 14)
            saveFileIconTexture = Texture(pixmap)
            pixmap.dispose()
            saveFileIconLoaded = false // Technically loaded, but it's the fallback
        }

        // --- Create the main table for the load screen ---
        loadGameTable = Table()
        loadGameTable.setFillParent(true)
        loadGameTable.center()
        loadGameTable.isVisible = false
        stage.addActor(loadGameTable)

        val titleLabel = Label("LOAD GAME", skin, "title")
        titleLabel.setFontScale(2.0f)
        titleLabel.color = Color.valueOf("#EAEAEA")
        loadGameTable.add(titleLabel).padBottom(40f).row()

        // Placeholder for the list, will be populated later
        val listContainer = Table()
        loadGameTable.add(listContainer).grow().row()

        val backButton = TextButton("Back", skin)
        loadGameTable.add(backButton).padTop(30f).width(200f).height(50f)

        backButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                hideLoadGameScreen(showStartMenu = true)
            }
        })
    }

    private fun createSaveFileRowWidget(saveName: String): Table {
        val rowTable = Table()
        // Create a semi-transparent, dark background for the row
        val bgPixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        bgPixmap.setColor(0.15f, 0.15f, 0.2f, 0.7f)
        bgPixmap.fill()
        rowTable.background = TextureRegionDrawable(Texture(bgPixmap))
        bgPixmap.dispose()

        rowTable.pad(10f)

        // 1. Icon
        val icon = Image(saveFileIconTexture)
        icon.setScaling(Scaling.fit)
        rowTable.add(icon).size(48f).padRight(15f)

        // 2. Save Name Label
        val nameLabel = Label(saveName.removeSuffix(".json"), skin, "default")
        nameLabel.setFontScale(1.2f)
        rowTable.add(nameLabel).expandX().left()

        // 3. Load Button
        val loadButton = TextButton("Load", skin)
        rowTable.add(loadButton).width(100f).height(40f)

        // Add a hover effect to the row
        rowTable.addListener(object: ClickListener() {
            override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
                // On hover, tint the background slightly lighter
                val hoverPixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
                hoverPixmap.setColor(0.25f, 0.25f, 0.3f, 0.8f)
                hoverPixmap.fill()
                rowTable.background = TextureRegionDrawable(Texture(hoverPixmap))
                hoverPixmap.dispose()
            }

            override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Actor?) {
                // On exit, revert to the original background
                val normalPixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
                normalPixmap.setColor(0.15f, 0.15f, 0.2f, 0.7f)
                normalPixmap.fill()
                rowTable.background = TextureRegionDrawable(Texture(normalPixmap))
                normalPixmap.dispose()
            }
        })

        loadButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                game.startGame(saveName)
            }
        })

        return rowTable
    }

    fun showLoadGameScreen() {
        // 1. Hide the main menu with a fade-out animation
        startMenuTable.addAction(Actions.sequence(
            Actions.fadeOut(0.3f, Interpolation.fade),
            Actions.visible(false)
        ))

        // 2. Get the list of save files
        val saveFiles = game.saveLoadSystem.listSaveGames()

        // 3. Find the container in our loadGameTable to put the list into
        val listContainer = loadGameTable.children[1] as Table // The second child is our placeholder
        listContainer.clear() // Clear any old list items

        if (saveFiles.isEmpty()) {
            listContainer.add(Label("No saved games found.", skin))
        } else {
            val saveListTable = Table()
            saveListTable.defaults().pad(5f).width(600f) // Set width for all rows

            // Create a styled widget for each save file
            saveFiles.sortedDescending().forEach { saveName ->
                saveListTable.add(createSaveFileRowWidget(saveName)).row()
            }

            val scrollPane = ScrollPane(saveListTable, skin)
            scrollPane.fadeScrollBars = false
            listContainer.add(scrollPane).width(620f).height(400f)
        }

        // 4. Show and fade in the load game screen after a short delay
        loadGameTable.isVisible = true
        loadGameTable.color.a = 0f
        loadGameTable.addAction(Actions.sequence(
            Actions.delay(0.3f), // Wait for the main menu to fade out
            Actions.fadeIn(0.4f, Interpolation.fade)
        ))
    }

    fun hideLoadGameScreen(showStartMenu: Boolean) {
        loadGameTable.addAction(Actions.sequence(
            Actions.fadeOut(0.3f, Interpolation.fade),
            Actions.visible(false),
            Actions.run {
                if (showStartMenu) {
                    // After this screen is hidden, show the start menu again
                    this@UIManager.showStartMenu()
                }
            }
        ))
    }

    private fun setupDeathScreen() {
        // 1. Create a semi-transparent red overlay
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(0.5f, 0f, 0f, 0.5f) // Dark red, 50% transparent
        pixmap.fill()
        deathOverlay = Image(Texture(pixmap))
        pixmap.dispose()
        deathOverlay.setFillParent(true)
        deathOverlay.isVisible = false
        stage.addActor(deathOverlay)

        // 2. Create the main table for the text and button
        deathTable = Table()
        deathTable.setFillParent(true)
        deathTable.center()
        deathTable.isVisible = false

        val titleLabel = Label("YOU DIED", skin, "title")
        titleLabel.setFontScale(2.5f)
        titleLabel.color = Color.valueOf("#7D0000") // Dark blood red

        val subtitleLabel = Label("Press [R] to Respawn", skin, "default")
        subtitleLabel.color = Color.LIGHT_GRAY

        deathTable.add(titleLabel).padBottom(20f).row()
        deathTable.add(subtitleLabel)

        stage.addActor(deathTable)
    }

    fun showDeathScreen() {
        deathOverlay.isVisible = true
        deathTable.isVisible = true

        deathOverlay.color.a = 0f
        deathTable.color.a = 0f

        deathOverlay.addAction(Actions.fadeIn(1.5f, Interpolation.fade))
        deathTable.addAction(Actions.fadeIn(2.0f, Interpolation.fade))
    }

    fun hideDeathScreen() {
        deathOverlay.addAction(Actions.sequence(
            Actions.fadeOut(0.5f),
            Actions.visible(false)
        ))
        deathTable.addAction(Actions.sequence(
            Actions.fadeOut(0.5f),
            Actions.visible(false)
        ))
    }

    fun toggleDialogueEditor() {
        if (dialogueEditorUI.isVisible()) {
            dialogueEditorUI.hide()
        } else {
            dialogueEditorUI.show()
        }
    }

    fun showTemporaryMessage(message: String) {
        temporaryMessageLabel.setText(message)
        temporaryMessageLabel.pack()

        // Always stop any previous animation before starting a new one
        temporaryMessageLabel.clearActions()
        temporaryMessageLabel.isVisible = true
        temporaryMessageLabel.color.a = 0f // Start fully transparent

        // Create the fade-in, delay, and fade-out sequence
        temporaryMessageLabel.addAction(Actions.sequence(
            Actions.fadeIn(0.3f, Interpolation.fade),  // Fade in over 0.3s
            Actions.delay(2.5f),                      // Stay visible for 2.5s
            Actions.fadeOut(0.5f, Interpolation.fade), // Fade out over 0.5s
            Actions.run { temporaryMessageLabel.isVisible = false } // Hide when done
        ))
    }

    private fun setupGameHUD() {
        // Load Textures
        try {
            wantedPosterTexture = Texture(Gdx.files.internal("gui/wanted_poster.png"))
            fistTexture = Texture(Gdx.files.internal("textures/objects/items/weapons/fist.png"))
            weaponUiTexture = Texture(Gdx.files.internal("gui/weapon_ui.png"))
            healthBarEmptyTexture = Texture(Gdx.files.internal("gui/healthbar_empty.png"))
            healthBarFullTexture = Texture(Gdx.files.internal("gui/healthbar_full.png"))
        } catch (e: Exception) {
            println("ERROR: Could not load HUD textures. Creating placeholders. Details: ${e.message}")
            val pixmap = Pixmap(100, 120, Pixmap.Format.RGBA8888)
            pixmap.setColor(Color.SCARLET)
            pixmap.fill()
            if (!::wantedPosterTexture.isInitialized) wantedPosterTexture = Texture(pixmap)
            if (!::fistTexture.isInitialized) fistTexture = Texture(pixmap)
            if (!::weaponUiTexture.isInitialized) weaponUiTexture = Texture(pixmap)
            pixmap.dispose()
        }

        // --- Create Health Bar Style (reusable for both HUDs) ---
        val healthBarBackground = TextureRegionDrawable(healthBarEmptyTexture)
        val healthBarFill = TextureRegionDrawable(healthBarFullTexture)
        val healthBarStyle = ProgressBar.ProgressBarStyle(healthBarBackground, null)
        healthBarStyle.knobBefore = healthBarFill

        // 1. BUILD WANTED POSTER HUD
        wantedPosterHudTable = Table()
        wantedPosterHudTable.setFillParent(true)
        wantedPosterHudTable.top().left().pad(20f)

        // Health Bar for Poster
        healthBar = ProgressBar(0f, 100f, 1f, false, healthBarStyle)
        val healthBarTable = Table().apply {
            bottom()
            add(healthBar).width(150f).height(20f).padBottom(12f).padRight(1f)
        }

        // Wanted Poster Stack
        val wantedStack = Stack()
        wantedStack.add(Image(wantedPosterTexture))
        wantedStack.add(healthBarTable)

        // Weapon Icon and Ammo UI
        weaponIconImage = Image().apply { setScaling(Scaling.fit) }
        weaponIconContainer = Container(weaponIconImage).size(100f, 80f).align(Align.left)

        // Create the single label for throwable counts, similar to minimalist HUD
        throwableCountLabelPoster = Label("00", skin, "default").apply { setFontScale(0.9f) }
        val throwableCountContainer = Container(throwableCountLabelPoster).align(Align.bottomRight).pad(5f)
        throwableCountContainer.isVisible = false // Start hidden

        // Create the reload indicator label for the Poster HUD
        reloadIndicatorPoster = Label("[R]", skin, "title").apply {
            color = Color.RED
            isVisible = false
        }
        val reloadIndicatorContainer = Container(reloadIndicatorPoster).align(Align.topRight).padTop(5f).padRight(-10f)

        // Create a stack for the weapon icon and the throwable count overlay
        val weaponIconStack = Stack()
        weaponIconStack.add(weaponIconContainer)
        weaponIconStack.add(throwableCountContainer)
        weaponIconStack.add(reloadIndicatorContainer)

        magazineAmmoLabel = Label("00", skin, "title").apply { color = Color.valueOf("#F5F1E8"); fontScaleX = 1.1f; fontScaleY = 1.1f }
        reserveAmmoLabel = Label("/00", skin, "title").apply { color = Color.valueOf("#D3C9B6"); fontScaleX = 1.1f; fontScaleY = 1.1f }

        // Ammo Label Table (using your original padding)
        val ammoLabelTable = Table()
        ammoLabelTable.add(magazineAmmoLabel).padLeft(85f).padBottom(12f)
        ammoLabelTable.add(reserveAmmoLabel).padBottom(12f)

        ammoUiContainer = Stack(Image(weaponUiTexture), ammoLabelTable)

        // Weapon Info Table
        val weaponInfoTable = Table()
        weaponInfoTable.add(weaponIconStack).row()
        weaponInfoTable.add(ammoUiContainer).width(180f).height(72f).padTop(0f).padLeft(85f)

        // Add to main poster table (using your original layout)
        wantedPosterHudTable.add(wantedStack).width(180f).height(240f).top().left()
        wantedPosterHudTable.add(weaponInfoTable).padLeft(-80f).top().padTop(15f)

        // 2. BUILD MINIMALIST HUD
        minimalistHudTable = Table()
        minimalistHudTable.setFillParent(true)
        minimalistHudTable.top().left().pad(20f)

        weaponIconImageMinimalist = Image().apply { setScaling(Scaling.fit) }
        ammoLabelMinimalist = Label("00/00", skin, "default").apply { setFontScale(0.8f) }
        healthBarMinimalist = ProgressBar(0f, 100f, 1f, false, healthBarStyle)

        // Create and style health label
        healthLabelMinimalist = Label("100/100", skin, "default")
        healthLabelMinimalist.color = Color.valueOf("#a83e26")

        // Group 1: Health (vertical)
        val healthGroup = Table()
        healthGroup.add(healthLabelMinimalist).left().padBottom(2f).row()
        healthGroup.add(healthBarMinimalist).width(150f).height(25f)

        // Group 2: Weapon (vertical)
        val weaponStack = Stack()
        weaponStack.add(Container(weaponIconImageMinimalist).size(64f))

        val ammoContainer = Container(ammoLabelMinimalist)
        ammoContainer.align(Align.bottomRight) // Aligns the label to the bottom-right corner of the icon
        weaponStack.add(ammoContainer)

        minimalistHudTable.add(weaponStack).width(90f).top().left()
        minimalistHudTable.add(healthGroup).top().left().padLeft(20f)

        // 3. ADD TO STAGE & SET INITIAL VISIBILITY
        stage.addActor(wantedPosterHudTable)
        stage.addActor(minimalistHudTable)
        setHudStyle(currentHudStyle) // Apply the default style
    }

    private fun setupMainUI() {
        mainTable = Table()
        mainTable.setFillParent(true)
        mainTable.top().left()
        mainTable.pad(15f)

        // Create main container using the builder
        val mainContainer = layoutBuilder.createMainContainer()

        // Add title section
        val titleContainer = layoutBuilder.createTitleSection()
        mainContainer.add(titleContainer).padBottom(30f).fillX().row()

        // Add tool selection section
        val (toolSection, toolButtonsCreated) = layoutBuilder.createToolSelectionSection(selectedTool) { tool ->
            selectedTool = tool
            updateToolDisplay()
        }
        toolButtons = toolButtonsCreated
        mainContainer.add(toolSection).padBottom(25f).fillX().row()

        // Add info card section
        val (infoContainer, placementInfoLabelCreated) = layoutBuilder.createInfoCardSection()
        placementInfoLabel = placementInfoLabelCreated
        mainContainer.add(infoContainer).left().padBottom(25f).fillX().row()

        // Add instructions section
        val instructionsSection = layoutBuilder.createInstructionsSection()
        mainContainer.add(instructionsSection).width(380f).padBottom(25f).fillX().row()

        // Add stats section
        val (statsSection, statsLabelsCreated) = layoutBuilder.createStatsSection()
        statsLabels = statsLabelsCreated
        mainContainer.add(statsSection).width(300f).fillX()

        // Create scroll pane
        val scrollPane = ScrollPane(mainContainer, skin)
        scrollPane.setScrollingDisabled(true, false)
        scrollPane.fadeScrollBars = true
        scrollPane.setFlickScroll(false)
        scrollPane.setOverscroll(false, false)

        mainTable.add(scrollPane).expandY().fillY().top().left()
        stage.addActor(mainTable)
    }

    private fun setupLetterboxUI() {
        // Create a simple 1x1 black pixel texture. This is very efficient.
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.BLACK)
        pixmap.fill()
        val blackDrawable = TextureRegionDrawable(Texture(pixmap))
        pixmap.dispose()

        // Create the main table for the letterbox
        letterboxTable = Table()
        letterboxTable.setFillParent(true)
        letterboxTable.isVisible = isLetterboxVisible

        letterboxTable.add(Image(blackDrawable)).growY()
        letterboxTable.add().expandX()
        letterboxTable.add(Image(blackDrawable)).growY()

        // maybe changing it, so User cant click through it
        letterboxTable.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled

        stage.addActor(letterboxTable)
        updateLetterboxSize()
    }

    private fun setupCinematicBarsUI() {
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.BLACK)
        pixmap.fill()
        val blackDrawable = TextureRegionDrawable(Texture(pixmap))
        pixmap.dispose()

        cinematicBarsTable = Table()
        cinematicBarsTable.setFillParent(true)

        // Row 1: Top bar image, grows to fill width
        cinematicBarsTable.add(Image(blackDrawable)).growX()
        cinematicBarsTable.row()
        // Row 2: Empty spacer that pushes top/bottom bars to edges
        cinematicBarsTable.add().expandY()
        cinematicBarsTable.row()
        // Row 3: Bottom bar image, grows to fill width
        cinematicBarsTable.add(Image(blackDrawable)).growX()

        cinematicBarsTable.isVisible = isCinematicBarsVisible
        cinematicBarsTable.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
        stage.addActor(cinematicBarsTable)

        updateCinematicBarsSize() // Set initial size
    }

    fun updateToolDisplay() {
        if (selectedTool == Tool.TRIGGER) {
            triggerEditorUI.show()
            game.triggerSystem.isEditorVisible = true // Tell the system to draw its editor visual
        } else {
            triggerEditorUI.hide()
            game.triggerSystem.isEditorVisible = false // Tell the system to HIDE its editor visual
        }

        layoutBuilder.updateToolButtons(toolButtons, selectedTool)
    }

    fun updatePlacementInfo(info: String) {
        if (::placementInfoLabel.isInitialized) {
            placementInfoLabel.setText(info)
            layoutBuilder.animatePlacementInfo(placementInfoLabel)
        }
    }

    fun setPersistentMessage(message: String) {
        if (::persistentMessageLabel.isInitialized) {
            persistentMessageLabel.setText(message)
            layoutBuilder.animatePersistentMessage(persistentMessageLabel, true)
        }
    }

    fun clearPersistentMessage() {
        if (::persistentMessageLabel.isInitialized) {
            layoutBuilder.animatePersistentMessage(persistentMessageLabel, false)
        }
    }

    private fun setupDynamicHud() {
        // 1. Create the master table that will hold all dynamic elements
        dynamicHudTable = Table()
        dynamicHudTable.setFillParent(true)
        dynamicHudTable.top().right().pad(20f) // Anchor it to the top-right
        stage.addActor(dynamicHudTable)

        // 2. Create the individual UI components (Labels, Tables, etc.)
        fpsLabel = Label("FPS: --", skin, "title").apply { color = Color.GREEN }

        moneyStackTexture = try {
            Texture(Gdx.files.internal("gui/dollar_stack.png"))
        } catch (e: Exception) {
            Pixmap(32, 32, Pixmap.Format.RGBA8888).let { pixmap ->
                pixmap.setColor(Color.GREEN); pixmap.fill(); Texture(pixmap).also { pixmap.dispose() }
            }
        }
        moneyDisplayTable = Table()
        val moneyIcon = Image(moneyStackTexture).apply { setScaling(Scaling.fit) }
        val dollarLabel = Label("$", skin, "title").apply { color = Color.WHITE }
        moneyValueLabel = Label("0", skin, "title").apply { color = Color.WHITE }
        moneyDisplayTable.add(moneyIcon).size(40f)
        moneyDisplayTable.add(dollarLabel).padLeft(10f)
        moneyDisplayTable.add(moneyValueLabel).padLeft(5f)

        missionObjectiveLabel = Label("", skin, "title")
        missionTimerLabel = Label("", skin, "title").apply {
            setFontScale(1.0f)
            color = Color.WHITE
        }

        enemiesLeftLabel = Label("", skin, "title").apply { color = Color.ORANGE }

        weaponPickupTable = Table()
        weaponPickupIcon = Image().apply { setScaling(Scaling.fit) }
        weaponPickupLabel = Label("", skin, "title").apply { color = Color.WHITE }

        weaponPickupStackLabel = Label("", skin, "title").apply {
            color = weaponStackCountColor
            setFontScale(0.7f) // Make it smaller
        }

        val stackLabelContainer = Container(weaponPickupStackLabel)
        stackLabelContainer.top().right().padTop(-2f).padRight(-11f)

        val labelStack = Stack()
        labelStack.add(weaponPickupLabel)
        labelStack.add(stackLabelContainer)

        weaponPickupTable.add(weaponPickupIcon).size(40f)
        weaponPickupTable.add(labelStack).padLeft(10f)

        // 3. Register all components with the dynamic HUD manager list, defining their order
        dynamicHudElements.add(HudInfoElement(HudInfoKey.FPS, fpsLabel, false))
        dynamicHudElements.add(HudInfoElement(HudInfoKey.MONEY, moneyDisplayTable, false))
        dynamicHudElements.add(HudInfoElement(HudInfoKey.WEAPON_PICKUP, weaponPickupTable, false))
        dynamicHudElements.add(HudInfoElement(HudInfoKey.MISSION_OBJECTIVE, missionObjectiveLabel, false))
        dynamicHudElements.add(HudInfoElement(HudInfoKey.ENEMIES_LEFT, enemiesLeftLabel, false))
        dynamicHudElements.add(HudInfoElement(HudInfoKey.MISSION_TIMER, missionTimerLabel, false))
    }

    private fun refreshDynamicHudTable() {
        dynamicHudTable.clear()
        dynamicHudTable.top().right()

        // Filter for visible elements, sort them by their predefined order, and add them to the table
        dynamicHudElements
            .filter { it.isVisible }
            .sortedBy { it.key.order }
            .forEach { hudElement ->
                dynamicHudTable.add(hudElement.actor).right().padBottom(8f)
                dynamicHudTable.row()
            }
    }

    private fun setHudElementVisibility(key: HudInfoKey, visible: Boolean) {
        val element = dynamicHudElements.find { it.key == key }
        if (element != null && element.isVisible != visible) {
            element.isVisible = visible
            // If we are showing an element, ensure it's fully opaque before it appears
            if (visible) {
                element.actor.color.a = 1f
            }
            refreshDynamicHudTable() // Rebuild the table layout!
        }
    }

    private fun setupMessageLabels() {
        temporaryMessageLabel = Label("", skin, "title").apply {
            setAlignment(Align.center)
            color = Color.WHITE
            isVisible = false
        }
        val tempMessageTable = Table().apply {
            setFillParent(true)
            top().pad(90f)
            add(temporaryMessageLabel)
        }
        stage.addActor(tempMessageTable)

        persistentMessageLabel = layoutBuilder.createPersistentMessageLabel()
        val persistentMessageTable = Table().apply {
            setFillParent(true)
            top().pad(50f)
            add(persistentMessageLabel)
        }
        stage.addActor(persistentMessageTable)

        leaveCarTimerLabel = Label("", skin, "title").apply {
            setFontScale(1.0f)
            color = Color.RED
            setAlignment(Align.center)
            isVisible = false
        }
        val timerTable = Table().apply {
            setFillParent(true)
            bottom().padBottom(50f)
            add(leaveCarTimerLabel)
        }
        stage.addActor(timerTable)

        returnToAreaTimerLabel = Label("", skin, "title").apply {
            setFontScale(1.0f)
            color = Color.ORANGE
            setAlignment(Align.center)
            isVisible = false
        }
        val returnAreaTimerTable = Table().apply {
            setFillParent(true)
            bottom().padBottom(90f)
            add(returnToAreaTimerLabel)
        }
        stage.addActor(returnAreaTimerTable)
    }

    fun queueInventoryChangeNotification(weaponType: WeaponType, amount: Int) {
        if (amount == 0) return

        val lastChange = inventoryChangeQueue.lastOrNull()

        if (lastChange != null && lastChange.weaponType == weaponType && (lastChange.amount > 0) == (amount > 0)) {
            // It's the same! Increment the stack count.
            lastChange.amount += amount
            lastChange.stackCount++
        } else {
            // It's different, so add a new entry.
            inventoryChangeQueue.add(InventoryChangeInfo(weaponType, amount))
        }

        // If a notification isn't already running, start the processing loop.
        if (!isWeaponNotificationActive) {
            // Set the flag immediately to "lock" the processing loop.
            isWeaponNotificationActive = true
            // Schedule the processing to start on the *next* frame.
            Gdx.app.postRunnable { processNextInventoryChange() }
        }
    }

    private fun processNextInventoryChange() {
        if (inventoryChangeQueue.isEmpty()) {
            // The queue is empty, so we are done. Animate out.
            val width = weaponPickupTable.width + 5f
            weaponPickupTable.addAction(Actions.sequence(
                Actions.moveBy(width, 0f, 0.4f, Interpolation.swingIn),
                Actions.run {
                    setHudElementVisibility(HudInfoKey.WEAPON_PICKUP, false)
                    isWeaponNotificationActive = false // Release the lock
                }
            ))
            return
        }

        val changeInfo = inventoryChangeQueue.removeFirst()

        // Update icon
        val itemType = ItemType.entries.find { it.correspondingWeapon == changeInfo.weaponType }
        if (itemType != null) {
            val texture = itemSystem.getTextureForItem(itemType)
            if (texture != null) {
                weaponPickupIcon.drawable = TextureRegionDrawable(texture)
            } else {
                weaponPickupIcon.drawable = null // Clear icon if texture is missing
            }
        } else {
            weaponPickupIcon.drawable = null // Clear icon if no item type corresponds to the weapon
        }

        // 2. Set the Text and Color
        val prefix = if (changeInfo.amount > 0) "+" else "" // Add a "+" for positive amounts. Negative amounts already have "-".
        weaponPickupLabel.setText("$prefix${changeInfo.amount}")
        weaponPickupLabel.color = if (changeInfo.amount > 0) Color.WHITE else Color.RED

        // 3. Set the Stack Count (e.g., "x3")
        if (changeInfo.stackCount > 1) {
            weaponPickupStackLabel.setText("x${changeInfo.stackCount}")
        } else {
            weaponPickupStackLabel.setText("") // Clear the stack text if count is 1.
        }

        weaponPickupTable.pack() // Repack to fit new text

        val isQueueMonotonous = inventoryChangeQueue.all { it.weaponType == changeInfo.weaponType }
        val duration = if (isQueueMonotonous) 2.0f else 1.2f

        val isFirstInSequence = !dynamicHudElements.find { it.key == HudInfoKey.WEAPON_PICKUP }!!.isVisible

        if (isFirstInSequence) {
            // First item in a new batch, play the slide-in animation.
            setHudElementVisibility(HudInfoKey.WEAPON_PICKUP, true)
            val width = weaponPickupTable.width + 5f

            weaponPickupTable.addAction(Actions.sequence(
                Actions.moveBy(width, 0f),
                Actions.moveBy(-width, 0f, 0.4f, Interpolation.swingOut),
                Actions.delay(duration),
                Actions.run { processNextInventoryChange() } // Process next item
            ))
        } else {
            // Subsequent item, just fade in the new content.
            weaponPickupTable.clearActions()
            weaponPickupTable.color.a = 0f

            weaponPickupTable.addAction(Actions.sequence(
                Actions.fadeIn(0.2f),
                Actions.delay(duration),
                Actions.run { processNextInventoryChange() } // Process next item
            ))
        }
    }

    fun updateFps() {
        fpsLabel.setText("FPS: ${Gdx.graphics.framesPerSecond}")
    }

    fun toggleFpsLabel() {
        val element = dynamicHudElements.find { it.key == HudInfoKey.FPS } ?: return
        setHudElementVisibility(HudInfoKey.FPS, !element.isVisible)
    }

    fun toggleVisibility() {
        if (!game.isEditorMode) {
            isUIVisible = false
            mainTable.isVisible = false
            return
        }
        isUIVisible = !isUIVisible
        mainTable.isVisible = isUIVisible

        // Add fade animation
        mainTable.clearActions()
        if (isUIVisible) {
            mainTable.color.a = 0f
            mainTable.addAction(Actions.fadeIn(0.3f, Interpolation.smooth))
        } else {
            mainTable.addAction(Actions.fadeOut(0.2f, Interpolation.smooth))
        }
    }

    // All your existing selection UI methods stay the same:
    fun showBlockSelection() = blockSelectionUI.show()
    fun hideBlockSelection() = blockSelectionUI.hide()
    fun updateBlockSelection() = blockSelectionUI.update()

    fun showObjectSelection() = objectSelectionUI.show()
    fun hideObjectSelection() = objectSelectionUI.hide()
    fun updateObjectSelection() = objectSelectionUI.update()

    fun showItemSelection() = itemSelectionUI.show()
    fun hideItemSelection() = itemSelectionUI.hide()
    fun updateItemSelection() = itemSelectionUI.update()

    fun showCarSelection() = carSelectionUI.show()
    fun hideCarSelection() = carSelectionUI.hide()
    fun getCarSpawnConfig(): CarSpawnConfig = carSelectionUI.getSpawnConfig()
    fun updateCarSelection() = carSelectionUI.update()

    fun showHouseSelection() = houseSelectionUI.show()
    fun hideHouseSelection() = houseSelectionUI.hide()
    fun updateHouseSelection() = houseSelectionUI.update()

    fun showInteriorSelection() = interiorSelectionUI.show()
    fun hideInteriorSelection() = interiorSelectionUI.hide()
    fun updateInteriorSelection() = interiorSelectionUI.update()

    fun showParallaxSelection() = parallaxSelectionUI.show()
    fun hideParallaxSelection() = parallaxSelectionUI.hide()
    fun nextParallaxImage() = parallaxSelectionUI.nextImage()
    fun previousParallaxImage() = parallaxSelectionUI.previousImage()
    fun nextParallaxLayer() = parallaxSelectionUI.nextLayer()
    fun getCurrentParallaxImageType() = parallaxSelectionUI.getCurrentSelectedImageType()
    fun getCurrentParallaxLayer() = parallaxSelectionUI.getCurrentSelectedLayer()

    fun showBackgroundSelection() = backgroundSelectionUI.show()
    fun hideBackgroundSelection() = backgroundSelectionUI.hide()
    fun updateBackgroundSelection() = backgroundSelectionUI.update()

    fun showEnemySelection() = enemySelectionUI.show()
    fun hideEnemySelection() = enemySelectionUI.hide()
    fun updateEnemySelection() = enemySelectionUI.update()

    fun showNPCSelection() = npcSelectionUI.show()
    fun hideNPCSelection() = npcSelectionUI.hide()
    fun updateNPCSelection() = npcSelectionUI.update()

    fun showParticleSelection() = particleSelectionUI.show()
    fun hideParticleSelection() = particleSelectionUI.hide()
    fun updateParticleSelection() = particleSelectionUI.update()

    // All your other existing methods like toggleLightSourceUI, toggleSkyCustomizationUI, etc.
    fun getLightSourceSettings() = lightSourceUI.getCurrentSettings()
    fun toggleLightSourceUI() = lightSourceUI.toggle()
    fun toggleSkyCustomizationUI() = skyCustomizationUI.toggle()
    fun toggleShaderEffectUI() = shaderEffectUI.toggle()

    fun showSpawnerUI(spawner: GameSpawner) = spawnerUI.show(spawner)
    fun refreshHouseRoomList() {
        if (::houseSelectionUI.isInitialized) {
            houseSelectionUI.refreshRoomList()
        }
    }

    fun navigateHouseRooms(direction: Int) = houseSelectionUI.navigateRooms(direction)
    fun selectHouseRoom() = houseSelectionUI.selectCurrentRoom()

    fun nextPrimaryTactic() {
        if (::enemySelectionUI.isInitialized) {
            enemySelectionUI.nextPrimaryTactic()
        }
    }

    fun prevPrimaryTactic() {
        if (::enemySelectionUI.isInitialized) {
            enemySelectionUI.prevPrimaryTactic()
        }
    }

    fun nextEmptyAmmoTactic() {
        if (::enemySelectionUI.isInitialized) {
            enemySelectionUI.nextEmptyAmmoTactic()
        }
    }

    fun prevEmptyAmmoTactic() {
        if (::enemySelectionUI.isInitialized) {
            enemySelectionUI.prevEmptyAmmoTactic()
        }
    }

    fun updateEnemiesLeft(count: Int) {
        // A count less than 0 is a signal to hide the label.
        val isVisible = count > 0

        if (isVisible) {
            enemiesLeftLabel.setText("Enemies Left: $count")
        }

        val element = dynamicHudElements.find { it.key == HudInfoKey.ENEMIES_LEFT }
        val wasVisible = element?.isVisible ?: false

        setHudElementVisibility(HudInfoKey.ENEMIES_LEFT, isVisible)

        // Only play the fade-in animation when it first appears.
        if (isVisible && !wasVisible) {
            enemiesLeftLabel.clearActions()
            enemiesLeftLabel.color.a = 0f
            enemiesLeftLabel.addAction(Actions.fadeIn(0.5f))
        }
    }

    // Dialog methods stay the same
    fun showTeleporterNameDialog(title: String, teleporter: GameTeleporter?, initialText: String = "", onConfirm: (name: String) -> Unit) {
        val dialog = Dialog(title, skin, "dialog")
        val nameField = TextField(initialText, skin)
        nameField.messageText = "e.g., 'To the stage'"

        dialog.contentTable.add(nameField).width(300f).pad(10f).row()

        val buttonTable = Table()
        val confirmButton = TextButton("Confirm", skin)
        buttonTable.add(confirmButton).pad(5f)

        if (teleporter != null) {
            val copyIdButton = TextButton("Copy ID", skin)
            copyIdButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    Gdx.app.clipboard.contents = teleporter.id
                    showTemporaryMessage("Copied ID: ${teleporter.id}")
                }
            })
            buttonTable.add(copyIdButton).pad(5f)

            val convertToEventButton = TextButton("To Mission Event", skin)
            convertToEventButton.isVisible = (currentEditorMode == EditorMode.MISSION)
            convertToEventButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    val mission = selectedMissionForEditing
                    if (mission == null) {
                        showTemporaryMessage("Error: No mission selected for editing!")
                        return
                    }

                    val currentSceneId = if (game.sceneManager.currentScene == SceneType.HOUSE_INTERIOR) {
                        game.sceneManager.getCurrentHouse()?.id ?: "WORLD"
                    } else {
                        "WORLD"
                    }

                    // Create the event from the teleporter's data
                    val gameEvent = GameEvent(
                        type = GameEventType.SPAWN_TELEPORTER,
                        spawnPosition = teleporter.gameObject.position.cpy(),
                        sceneId = currentSceneId,
                        teleporterId = teleporter.id,
                        linkedTeleporterId = teleporter.linkedTeleporterId,
                        teleporterName = nameField.text.trim().ifBlank { "Teleporter" } // Use current name from field
                    )

                    mission.eventsOnStart.add(gameEvent)
                    game.missionSystem.saveMission(mission)

                    // Remove the original from the world
                    game.teleporterSystem.removeTeleporter(teleporter, breakPartnerLink = false)

                    dialog.hide()
                    showTemporaryMessage("Teleporter converted to event for '${mission.title}'")
                    missionEditorUI.refreshEventWidgets()
                }
            })
            buttonTable.add(convertToEventButton).pad(5f)

            val removeButton = TextButton("Remove", skin).apply { color.set(1f, 0.6f, 0.6f, 1f) }
            removeButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    game.teleporterSystem.removeTeleporter(teleporter)
                    showTemporaryMessage("Teleporter '${teleporter.name}' removed.")
                    dialog.hide()
                }
            })
            buttonTable.add(removeButton).pad(5f)
        }

        dialog.buttonTable.add(buttonTable)

        confirmButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                val name = nameField.text.trim().ifBlank { "Teleporter" }
                onConfirm(name)
                dialog.hide()
            }
        })

        dialog.key(com.badlogic.gdx.Input.Keys.ENTER, true)
        dialog.key(com.badlogic.gdx.Input.Keys.ESCAPE, false)
        dialog.show(stage)
        stage.keyboardFocus = nameField
    }

    fun showSaveRoomDialog(sceneManager: SceneManager) {
        // Check if we are actually in a room to save.
        if (sceneManager.currentScene != SceneType.HOUSE_INTERIOR) {
            updatePlacementInfo("Error: Can only save a room when inside a house.")
            return
        }

        // Get the current interior state to see if it was loaded from a template
        val currentInteriorState = sceneManager.getCurrentInteriorState()
        val sourceTemplateId = currentInteriorState?.sourceTemplateId
        val sourceTemplate = sourceTemplateId?.let { roomTemplateManager.getTemplate(it) }

        val dialogTitle = if (sourceTemplate != null) "Save Room" else "Save Room As Template"
        val dialog = Dialog(dialogTitle, skin, "dialog")
        dialog.text("Configure room options and choose a save method.").padBottom(10f)

        // UI Elements for Time Settings ---
        val contentTable = dialog.contentTable
        contentTable.row()

        // --- Common UI Elements ---
        val nameField = TextField("", skin).apply {
            messageText = "Template Name"
            // Pre-fill name field with the original name for convenience when using "Save As New"
            text = sourceTemplate?.name ?: ""
        }
        contentTable.add(Label("Name:", skin)).padRight(10f)
        contentTable.add(nameField).width(300f).row()

        val shaderLabel = Label("Room Shader:", skin)
        val shaderOptions = ShaderEffect.entries.toTypedArray()
        val shaderSelectBox = SelectBox<String>(skin)
        shaderSelectBox.items = com.badlogic.gdx.utils.Array(shaderOptions.map { it.displayName }.toTypedArray())

        val shaderTable = Table()
        shaderTable.add(shaderLabel).padRight(10f)
        shaderTable.add(shaderSelectBox).growX()
        contentTable.add(shaderTable).fillX().colspan(2).padTop(10f).row()

        val fixTimeCheckbox = CheckBox(" Fix time in this room", skin)
        contentTable.add(fixTimeCheckbox).left().padTop(10f).colspan(2).row()

        val timeSliderTable = Table()
        val timeLabel = Label("Time: 12:00", skin)
        val timeSlider = Slider(0f, 1f, 0.01f, false, skin)

        val tempCycle = DayNightCycle() // Assuming you have access to this or a similar class
        fun updateTimeLabel() {
            tempCycle.setDayProgress(timeSlider.value)
            timeLabel.setText("Time: ${tempCycle.getTimeString()}")
        }
        timeSlider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                updateTimeLabel()
            }
        })
        timeSliderTable.add(timeLabel).width(100f)
        timeSliderTable.add(timeSlider).growX()
        contentTable.add(timeSliderTable).fillX().padTop(5f).colspan(2).row()

        fixTimeCheckbox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                timeSliderTable.isVisible = fixTimeCheckbox.isChecked
                dialog.pack()
            }
        })

        // Pre-fill dialog with values from the source template if it exists
        if (sourceTemplate != null) {
            fixTimeCheckbox.isChecked = sourceTemplate.isTimeFixed
            timeSlider.value = sourceTemplate.fixedTimeProgress
            shaderSelectBox.selected = sourceTemplate.savedShaderEffect.displayName
        } else if (currentInteriorState != null) { // Or from the live interior state if it's a new room
            fixTimeCheckbox.isChecked = currentInteriorState.isTimeFixed
            timeSlider.value = currentInteriorState.fixedTimeProgress
            shaderSelectBox.selected = currentInteriorState.savedShaderEffect.displayName
        }
        timeSliderTable.isVisible = fixTimeCheckbox.isChecked
        updateTimeLabel()


        // --- Button Logic ---
        // This is the common save logic used by all save buttons
        fun performSave(id: String, name: String) {
            val isTimeFixed = fixTimeCheckbox.isChecked
            val fixedTimeProgress = timeSlider.value
            val selectedShaderName = shaderSelectBox.selected
            val selectedShader = ShaderEffect.entries.find { it.displayName == selectedShaderName } ?: ShaderEffect.NONE

            val success = sceneManager.saveCurrentInteriorAsTemplate(
                id, name, "user_created", isTimeFixed, fixedTimeProgress, selectedShader
            )

            if (success) {
                updatePlacementInfo("Room saved as '$name'")
                houseSelectionUI.refreshRoomList() // Crucial to see the new/updated room
            } else {
                updatePlacementInfo("Failed to save room template.")
            }
            dialog.hide()
        }

        // --- Conditional Button Setup ---
        if (sourceTemplate != null) {
            // This room was loaded from a template. Offer "Overwrite" and "Save As New".
            val overwriteButton = TextButton("Overwrite", skin)
            overwriteButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    // For overwrite, we use the original template's ID and name.
                    performSave(sourceTemplate.id, sourceTemplate.name)
                }
            })
            dialog.button(overwriteButton)

            val saveAsNewButton = TextButton("Save As New", skin)
            saveAsNewButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    val newName = nameField.text.trim()
                    if (newName.isBlank() || newName.equals(sourceTemplate.name, ignoreCase = true)) {
                        updatePlacementInfo("To save a new copy, please enter a different name.")
                        nameField.color = Color.RED
                        return
                    }
                    nameField.color = Color.WHITE
                    val newId = "user_room_${System.currentTimeMillis()}"
                    performSave(newId, newName)
                }
            })
            dialog.button(saveAsNewButton)

        } else {
            // This is a new room. Only "Save" (as new) is possible.
            val saveButton = TextButton("Save", skin)
            saveButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    val name = nameField.text.trim().ifBlank { "Room - ${System.currentTimeMillis().toString().takeLast(6)}" }
                    val newId = "user_room_${System.currentTimeMillis()}"
                    performSave(newId, name)
                }
            })
            dialog.button(saveButton)
        }

        dialog.button("Cancel", false) // LibGDX built-in cancel button
        dialog.key(com.badlogic.gdx.Input.Keys.ESCAPE, false) // Close on escape
        dialog.show(stage)
    }

    // Letterbox and cinematic bars methods stay the same
    fun isLetterboxEnabled(): Boolean = isLetterboxVisible
    fun isCinematicBarsEnabled(): Boolean = isCinematicBarsVisible
    fun toggleLetterbox() {
        isLetterboxVisible = !isLetterboxVisible
        updateLetterboxSize()
    }

    fun toggleCinematicBars() {
        isCinematicBarsVisible = !isCinematicBarsVisible
        cinematicBarsTable.isVisible = isCinematicBarsVisible
    }

    private fun updateLetterboxSize() {
        val screenWidth = stage.width
        val screenHeight = stage.height

        // 4:3 is 1.333
        val targetAspect = 1.375f

        val targetWidth = screenHeight * targetAspect

        if (screenWidth > targetWidth) {
            val barWidth = (screenWidth - targetWidth) / 2f

            val leftCell = letterboxTable.getCell(letterboxTable.children.first())
            val middleCell = letterboxTable.cells[1]
            val rightCell = letterboxTable.getCell(letterboxTable.children.last())

            leftCell.width(barWidth)
            middleCell.width(targetWidth)
            rightCell.width(barWidth)

            letterboxTable.isVisible = isLetterboxVisible
        } else {
            // If the screen isn't wide enough, hide the bars
            letterboxTable.isVisible = false
        }
    }

    private fun updateCinematicBarsSize() {
        if (!::cinematicBarsTable.isInitialized) return

        val screenHeight = stage.height
        val barHeight = screenHeight * 0.08f // 8% of screen height is a good cinematic size

        cinematicBarsTable.cells[0].height(barHeight) // Top bar cell
        cinematicBarsTable.cells[2].height(barHeight) // Bottom bar cell
        cinematicBarsTable.invalidateHierarchy()
    }

    fun enterExitDoorPlacementMode(house: GameHouse) {
        println("Entering EXIT DOOR PLACEMENT mode for house ${house.id}")
        isPlacingExitDoorMode = true
        houseRequiringDoor = house

        // Force the UI and system to the DOOR_INTERIOR tool
        selectedTool = Tool.INTERIOR
        val doorIndex = InteriorType.entries.indexOf(InteriorType.DOOR_INTERIOR)
        if (doorIndex != -1) {
            interiorSystem.currentSelectedInteriorIndex = doorIndex
            interiorSystem.currentSelectedInterior = InteriorType.DOOR_INTERIOR
            updateInteriorSelection()
        }

        setPersistentMessage("PLACE THE EXIT DOOR (Press J to see options)")
    }

    fun exitDoorPlacementModeCompleted() {
        isPlacingExitDoorMode = false
        houseRequiringDoor = null
        clearPersistentMessage()
    }

    fun enterEntryPointPlacementMode(house: GameHouse) {
        println("UI MANAGER: Entering Entry Point Placement mode for house ${house.id}")
        isPlacingEntryPointMode = true
        houseRequiringEntryPoint = house
        setPersistentMessage("Click on the house model to place the entry point.\nPress ESC to cancel and use the default entrance.")
    }

    fun exitEntryPointPlacementMode() {
        println("UI MANAGER: Exiting Entry Point Placement mode.")
        isPlacingEntryPointMode = false
        houseRequiringEntryPoint = null
        clearPersistentMessage()
    }

    fun updateLeaveCarTimer(timeRemaining: Float) {
        if (timeRemaining > 0f) {
            leaveCarTimerLabel.isVisible = true
            // Format the string to one decimal place for a nice countdown effect
            leaveCarTimerLabel.setText(String.format("Return to your vehicle: %.1fs", timeRemaining))
        } else {
            leaveCarTimerLabel.isVisible = false
        }
    }

    fun updateMissionTimer(timeRemaining: Float, isDelay: Boolean = false) {
        // Stop any previous animations
        missionTimerLabel.clearActions()
        isTimerInDelayPhase = isDelay

        if (timeRemaining > 0f) {
            missionTimerLabel.color = if (isDelay) Color.LIGHT_GRAY else Color.WHITE
            val minutes = (timeRemaining / 60).toInt()
            val seconds = (timeRemaining % 60).toInt()
            missionTimerLabel.setText(String.format("%02d:%02d", minutes, seconds))

            // Ensure it's fully visible if it was faded out before
            setHudElementVisibility(HudInfoKey.MISSION_TIMER, true)
        } else {
            // fade it out smoothly
            setHudElementVisibility(HudInfoKey.MISSION_TIMER, false)
        }
    }

    fun hidePauseMenu() {
        if (::pauseMenuUI.isInitialized && pauseMenuUI.isVisible()) {
            pauseMenuUI.hide()
        }
        if (::visualSettingsUI.isInitialized && visualSettingsUI.isVisible()) {
            visualSettingsUI.hide()
        }
        if (::audioSettingsUI.isInitialized && audioSettingsUI.isVisible()) { // ADD
            audioSettingsUI.hide()
        }
    }

    fun showAudioSettings() {
        pauseMenuUI.hideInstantly()
        audioSettingsUI.show(stage)
    }

    fun hideAllGameHUDs() {
        wantedPosterHudTable.isVisible = false
        minimalistHudTable.isVisible = false

        // Clear all elements from the dynamic HUD (top-right info)
        dynamicHudElements.forEach { it.isVisible = false }
        refreshDynamicHudTable()
    }

    // Menu and settings methods stay the same
    fun showVisualSettings() {
        // First, check if the visual settings UI has been initialized.
        if (!::visualSettingsUI.isInitialized) {
            // If not, it means we are in the start menu. Show a message and do nothing.
            showTemporaryMessage("Settings can only be accessed while in-game.")
            return
        }

        pauseMenuUI.hideInstantly()
        visualSettingsUI.show(stage)
    }

    fun togglePauseMenu() {
        if (visualSettingsUI.isVisible()) {
            returnToPauseMenu()
            return
        }

        if (audioSettingsUI.isVisible()) {
            returnToPauseMenu()
            return // Stop further processing
        }

        if (pauseMenuUI.isVisible()) {
            pauseMenuUI.hide()
            game.musicManager.playSong("mafia_theme")
            game.soundManager.resumeAllLoopingSounds()
        } else {
            val showSettings = (game.currentGameMode == GameMode.IN_GAME)
            pauseMenuUI.setSettingsButtonVisibility(showSettings)
            pauseMenuUI.show()
            game.musicManager.stop()
            game.soundManager.pauseAllLoopingSounds()
        }
    }

    fun isPauseMenuVisible(): Boolean {
        // Check if visualSettingsUI has been created before accessing it.
        val isVisualSettingsVisible = if (::visualSettingsUI.isInitialized) visualSettingsUI.isVisible() else false
        val isAudioSettingsVisible = if (::audioSettingsUI.isInitialized) audioSettingsUI.isVisible() else false

        // pauseMenuUI is always initialized, so it's safe to check.
        val isPauseVisible = if (::pauseMenuUI.isInitialized) pauseMenuUI.isVisible() else false

        return isPauseVisible || isVisualSettingsVisible || isAudioSettingsVisible
    }

    fun returnToPauseMenu() {
        // Only hide the UI that is currently visible
        if (visualSettingsUI.isVisible()) {
            visualSettingsUI.hide()
        }
        if (audioSettingsUI.isVisible()) {
            audioSettingsUI.hide()
        }

        pauseMenuUI.show()
    }

    fun isInventoryVisible(): Boolean {
        return if (::characterInventoryUI.isInitialized) {
            characterInventoryUI.isVisible()
        } else {
            false
        }
    }

    private fun setupMissionNotification() {
        // 1. Load your image as a texture
        val texture = Texture(Gdx.files.internal("gui/notifycation.png"))

        // 2. Create the NinePatch (8, 8, 8, 8)
        // left, right, top, bottom
        notificationPatch = NinePatch(TextureRegion(texture), 8, 8, 8, 8)

        // 3. Create the table that will act as the notification window
        notificationTable = Table()
        notificationTable.background = NinePatchDrawable(notificationPatch) // Use the NinePatch as the background
        notificationTable.isVisible = false // Start hidden

        // 4. Create the label that will hold the mission title
        notificationLabel = Label("", skin, "title")
        notificationLabel.color = Color.BLACK
        notificationLabel.wrap = true // Allow text to wrap if the title is very long
        notificationLabel.setAlignment(Align.center)

        // 5. Add the label
        notificationTable.add(notificationLabel).pad(15f).growX()

        // 6. Position the notification table at the top-center of the screen
        val container = Container(notificationTable)
        container.setFillParent(true)
        container.top().padTop(5f)
        stage.addActor(container)
    }

    fun showMissionStartNotification(missionTitle: String) {
        // Set the text for the label
        notificationLabel.setText(missionTitle)

        notificationTable.pack()

        // Stop any previous animations
        notificationTable.clearActions()
        notificationTable.isVisible = true

        // Set initial state for animation (off-screen and faded out)
        notificationTable.color.a = 0f
        notificationTable.setOrigin(Align.center)
        notificationTable.setPosition(stage.width / 2f, stage.height + 20f, Align.top) // Start above the screen

        // Create the animation sequence
        notificationTable.addAction(Actions.sequence(
            // Animate it fading in and sliding down into view
            Actions.parallel(
                Actions.fadeIn(0.5f, Interpolation.pow2Out),
                Actions.moveBy(0f, -80f, 0.5f, Interpolation.pow2Out)
            ),
            Actions.delay(4.0f),
            // Animate it fading out
            Actions.fadeOut(0.5f, Interpolation.fade),
            // Hide the actor completely when the animation is finished
            Actions.visible(false)
        ))
    }

    fun hideAllEditorPanels() {
        // Hide the main UI panel
        isUIVisible = false
        mainTable.isVisible = false

        // Hide all pop-up selection windows
        hideBlockSelection()
        hideObjectSelection()
        hideItemSelection()
        hideCarSelection()
        hideHouseSelection()
        hideBackgroundSelection()
        hideParallaxSelection()
        hideInteriorSelection()
        hideEnemySelection()
        hideNPCSelection()
        hideParticleSelection()

        // Hide settings windows
        if (lightSourceUI.isVisible()) lightSourceUI.toggle()
        if (skyCustomizationUI.isVisible()) skyCustomizationUI.toggle()
        if (shaderEffectUI.isVisible()) shaderEffectUI.toggle()
    }

    fun enterObjectiveAreaPlacementMode(objective: MissionObjective, dialog: Dialog) {
        if (objective.completionCondition.type != ConditionType.ENTER_AREA) return

        isPlacingObjectiveArea = true
        objectiveBeingPlaced = objective
        activeObjectiveDialog = dialog

        dialog.isVisible = false
        missionEditorUI.hide() // Hide the main editor window as well

        setPersistentMessage("LEFT-CLICK to set area center | SCROLL to change radius | ENTER/ESC to confirm")
    }

    fun exitObjectiveAreaPlacementMode() {
        isPlacingObjectiveArea = false
        objectiveBeingPlaced = null

        missionEditorUI.show() // Re-show the main editor window first
        activeObjectiveDialog?.isVisible = true // Then make the objective dialog visible on top
        activeObjectiveDialog?.toFront() // Ensure it has focus

        activeObjectiveDialog = null

        clearPersistentMessage()
        game.highlightSystem.hideHighlight()
    }

    fun updateObjectiveAreaFromVisuals(updatedObjective: MissionObjective) {
        objectiveBeingPlaced = updatedObjective
        missionEditorUI.updateAreaFields(
            updatedObjective.completionCondition.areaCenter ?: Vector3(),
            updatedObjective.completionCondition.areaRadius ?: 10f
        )
    }

    fun showEnemyDebugInfo(enemy: GameEnemy) {
        // Hide other selection UIs to avoid clutter
        hideAllEditorPanels()
        enemyDebugUI.show(enemy)
    }

    fun toggleMissionEditor() {
        if (missionEditorUI.isVisible()) {
            missionEditorUI.hide()
        } else {
            missionEditorUI.show()
        }
    }

    fun showTextInputDialog(title: String, initialText: String, onConfirm: (String) -> Unit) {
        val dialog = Dialog(title, skin, "dialog")
        val field = TextField(initialText, skin)
        dialog.contentTable.add(field).width(300f)
        dialog.button("OK").addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                onConfirm(field.text)
            }
        })
        dialog.button("Cancel")
        dialog.key(com.badlogic.gdx.Input.Keys.ENTER, true)
        dialog.key(com.badlogic.gdx.Input.Keys.ESCAPE, false)
        dialog.show(stage)
        stage.keyboardFocus = field
    }

    fun getStage(): Stage = stage

    private fun updateWeaponIcon() {
        val currentWeapon = game.playerSystem.equippedWeapon

        if (currentWeapon == WeaponType.UNARMED) {
            // Special case for unarmed: use the fist texture
            weaponIconImage.drawable = TextureRegionDrawable(fistTexture)
            return
        }

        // Find the ItemType that corresponds to the equipped weapon
        val itemType = ItemType.entries.find { it.correspondingWeapon == currentWeapon }
        if (itemType != null) {
            // Get the texture from the ItemSystem
            val weaponTexture = itemSystem.getTextureForItem(itemType)
            if (weaponTexture != null) {
                weaponIconImage.drawable = TextureRegionDrawable(weaponTexture)
            } else {
                // Hide the icon if the texture couldn't be found
                weaponIconImage.drawable = null
            }
        } else {
            // Hide the icon if no corresponding item type exists
            weaponIconImage.drawable = null
        }
    }

    fun showFloatingText(text: String, color: Color, startPosition: Vector3) {
        val label = Label(text, skin, "title")
        label.color = color

        // Convert the 3D world position to 2D screen coordinates
        val screenCoords = game.cameraManager.camera.project(Vector3(startPosition))
        label.setPosition(screenCoords.x, screenCoords.y)

        stage.addActor(label)

        // Animate the label floating up and fading out
        label.addAction(Actions.sequence(
            Actions.parallel(
                Actions.moveBy(0f, 80f, 1.5f, Interpolation.pow2Out), // Move up
                Actions.fadeOut(1.5f, Interpolation.fade)             // Fade out
            ),
            Actions.removeActor() // Remove the label from the stage when done
        ))
    }

    fun setHudStyle(newStyle: HudStyle) {
        currentHudStyle = newStyle
        wantedPosterHudTable.isVisible = (newStyle == HudStyle.WANTED_POSTER) && !game.isEditorMode && !isPauseMenuVisible()
        minimalistHudTable.isVisible = (newStyle == HudStyle.MINIMALIST) && !game.isEditorMode && !isPauseMenuVisible()
    }

    fun getCurrentHudStyle(): HudStyle = currentHudStyle

    fun showMoneyUpdate(newAmount: Int) {
        // Find our money element in the master list to check its visibility state.
        val moneyElement = dynamicHudElements.find { it.key == HudInfoKey.MONEY } ?: return

        moneyValueLabel.setText(newAmount.toString())
        moneyDisplayTable.pack() // Recalculate size based on new text

        if (moneyElement.isVisible) {
            var foundAndResetDelay = false
            for (action in moneyDisplayTable.actions) {
                if (action is SequenceAction) {
                    // Find the delay part of the animation sequence.
                    for (subAction in action.actions) {
                        if (subAction is DelayAction) {
                            subAction.restart() // This resets the timer on the delay!
                            foundAndResetDelay = true
                            break
                        }
                    }
                }
                if (foundAndResetDelay) break
            }

        } else {
            // The display is completely off-screen. Play the full "slide in" animation.
            setHudElementVisibility(HudInfoKey.MONEY, true)

            val width = moneyDisplayTable.width + 5f

            // Play the full slide-in, delay, and slide-out sequence.
            moneyDisplayTable.addAction(Actions.sequence(
                Actions.moveBy(width, 0f), // Instantly move it off-screen to the right
                Actions.moveBy(-width, 0f, 0.4f, Interpolation.swingOut), // Slide in
                Actions.delay(2.5f), // Wait
                Actions.moveBy(width, 0f, 0.4f, Interpolation.swingIn), // Slide out
                Actions.run {
                    setHudElementVisibility(HudInfoKey.MONEY, false)
                }
            ))
        }
    }

    fun isDialogActive(): Boolean = dialogSystem.isActive()

    fun updateMissionObjective(text: String) {
        val isVisible = text.isNotBlank()
        missionObjectiveLabel.setText(text)
        setHudElementVisibility(HudInfoKey.MISSION_OBJECTIVE, isVisible)

        if (isVisible) {
            // Optional: Add a fade-in animation
            missionObjectiveLabel.clearActions()
            missionObjectiveLabel.color.a = 0f
            missionObjectiveLabel.addAction(Actions.fadeIn(0.5f))
        }
    }

    fun isGamePaused(): Boolean {
        return isPauseMenuVisible() || isDialogActive() || (::characterInventoryUI.isInitialized && characterInventoryUI.isVisible())
    }

    fun isCursorRequired(): Boolean {
        return isGamePaused()
    }

    fun showEnemyInventory(enemy: GameEnemy) {
        // Hide other debug/selection UIs to avoid clutter
        hideAllEditorPanels()
        characterInventoryUI.show(enemy)
    }

    fun showNpcInventory(npc: GameNPC) {
        hideAllEditorPanels()
        characterInventoryUI.show(npc)
    }

    fun cycleViolenceLevel() {
        val nextOrdinal = (currentViolenceLevel.ordinal + 1) % ViolenceLevel.entries.size
        currentViolenceLevel = ViolenceLevel.entries[nextOrdinal]
    }

    fun setViolenceLevel(level: ViolenceLevel) {
        currentViolenceLevel = level
    }

    fun getViolenceLevel(): ViolenceLevel = currentViolenceLevel

    fun updateTriggerRadiusField(newRadius: Float) {
        triggerEditorUI.setRadiusText(newRadius.toString())
    }

    fun updateReturnToAreaTimer(timeRemaining: Float) {
        if (timeRemaining > 0f) {
            returnToAreaTimerLabel.isVisible = true
            returnToAreaTimerLabel.setText(String.format("Return to the area: %.1fs", timeRemaining))
        } else {
            returnToAreaTimerLabel.isVisible = false
        }
    }

    fun render() {
        dialogSystem.update(Gdx.graphics.deltaTime)

        if (!game.isEditorMode && persistentMessageLabel.isVisible) {
            persistentMessageLabel.isVisible = false
        }

        // --- Guard Clause for all IN_GAME UI updates ---
        if (game.currentGameMode == GameMode.IN_GAME) {
            // Update HUD visibility and values based on game state
            val shouldShowHud = !game.isEditorMode && !isPauseMenuVisible() && !game.playerSystem.isDead()
            wantedPosterHudTable.isVisible = shouldShowHud && currentHudStyle == HudStyle.WANTED_POSTER
            minimalistHudTable.isVisible = shouldShowHud && currentHudStyle == HudStyle.MINIMALIST

            if (shouldShowHud) {
                // Update health bar value every frame
                healthBar.value = game.playerSystem.getHealthPercentage()

                if (game.playerSystem.equippedWeapon != lastEquippedWeapon) {
                    updateWeaponIcon() // This updates the main icon
                    lastEquippedWeapon = game.playerSystem.equippedWeapon
                }

                // Ammo and Reloading UI Logic
                val isReloading = game.playerSystem.isReloading()
                val magCount = game.playerSystem.getCurrentMagazineCount()
                val weapon = game.playerSystem.equippedWeapon
                val needsReload = weapon.requiresReload && magCount == 0 && !isReloading

                // Update blink timer (blinks about 2.5 times per second)
                blinkTimer = (blinkTimer + Gdx.graphics.deltaTime * 2f) % 1f

                // Reset colors and visibility to their default state each frame
                magazineAmmoLabel.color = Color.valueOf("#F5F1E8")
                ammoLabelMinimalist.color = Color.WHITE
                weaponIconImageMinimalist.isVisible = true
                reloadIndicatorPoster.isVisible = false

                if (isReloading) {
                    val isBlinkOn = blinkTimer < 0.5f
                    // Minimalist HUD: Blink weapon icon
                    weaponIconImageMinimalist.isVisible = isBlinkOn
                    // Poster HUD: Blink [R] indicator
                    reloadIndicatorPoster.isVisible = isBlinkOn
                } else if (needsReload) {
                    // Set ammo counters to red to indicate "Please Reload"
                    magazineAmmoLabel.color = Color.RED
                    ammoLabelMinimalist.color = Color.RED
                }

                when (weapon.actionType) {
                    WeaponActionType.SHOOTING -> {
                        // Poster HUD
                        ammoUiContainer.isVisible = true
                        throwableCountLabelPoster.parent.isVisible = false // Hide the single count label
                        val reserveCount = game.playerSystem.getCurrentReserveAmmo()
                        magazineAmmoLabel.setText(magCount.toString().padStart(2, '0'))
                        reserveAmmoLabel.setText("/${reserveCount.toString().padStart(2, '0')}")

                        // Minimalist HUD
                        val totalAmmo = magCount + reserveCount
                        ammoLabelMinimalist.setText(totalAmmo.toString())
                        ammoLabelMinimalist.isVisible = true
                    }
                    WeaponActionType.THROWABLE -> {
                        // Poster HUD
                        ammoUiContainer.isVisible = false // Hide the bar UI
                        throwableCountLabelPoster.parent.isVisible = true // Show the single count label
                        val totalCount = game.playerSystem.getCurrentReserveAmmo()
                        throwableCountLabelPoster.setText(totalCount.toString())

                        // Minimalist HUD
                        ammoLabelMinimalist.setText(totalCount.toString())
                        ammoLabelMinimalist.isVisible = true
                    }
                    else -> { // Melee
                        // Hide ammo display for both HUDs
                        ammoUiContainer.isVisible = false
                        throwableCountLabelPoster.parent.isVisible = false
                        ammoLabelMinimalist.isVisible = false
                    }
                }

                // Update Minimalist Health
                healthBarMinimalist.value = game.playerSystem.getHealthPercentage()
                val currentHealth = game.playerSystem.getHealth().toInt()
                val maxHealth = game.playerSystem.getMaxHealth().toInt()
                healthLabelMinimalist.setText("$currentHealth/$maxHealth")

                // Update Minimalist Icon
                weaponIconImageMinimalist.drawable = weaponIconImage.drawable // Reuse the same drawable
            }

            // --- Editor/Debug UI Updates (only when IN_GAME) ---
            if (::skyCustomizationUI.isInitialized) skyCustomizationUI.update()
            if (::shaderEffectUI.isInitialized) shaderEffectUI.update()
            if (::enemyDebugUI.isInitialized) enemyDebugUI.act(Gdx.graphics.deltaTime)
        }

        // --- UI that can update in ANY state (Menu or In-Game) ---
        if (::pauseMenuUI.isInitialized) {
            pauseMenuUI.update(Gdx.graphics.deltaTime)
        }

        stage.act(Gdx.graphics.deltaTime)
        stage.draw()
    }

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        updateLetterboxSize()
        updateCinematicBarsSize()
    }

    fun dispose() {
        // --- SAFE DISPOSING BLOCK ---
        // Only dispose of UI elements if they have been initialized.
        // This is crucial for when the game is closed from the start menu.

        if (::wantedPosterTexture.isInitialized) wantedPosterTexture.dispose()
        if (::moneyStackTexture.isInitialized) moneyStackTexture.dispose()
        if (::fistTexture.isInitialized) fistTexture.dispose()
        if (::weaponUiTexture.isInitialized) weaponUiTexture.dispose()
        if (::healthBarEmptyTexture.isInitialized) healthBarEmptyTexture.dispose()
        if (::healthBarFullTexture.isInitialized) healthBarFullTexture.dispose()

        if (::blockSelectionUI.isInitialized) blockSelectionUI.dispose()
        if (::objectSelectionUI.isInitialized) objectSelectionUI.dispose()
        if (::itemSelectionUI.isInitialized) itemSelectionUI.dispose()
        if (::carSelectionUI.isInitialized) carSelectionUI.dispose()
        if (::houseSelectionUI.isInitialized) houseSelectionUI.dispose()
        if (::backgroundSelectionUI.isInitialized) backgroundSelectionUI.dispose()
        if (::parallaxSelectionUI.isInitialized) parallaxSelectionUI.dispose()
        if (::interiorSelectionUI.isInitialized) interiorSelectionUI.dispose()
        if (::lightSourceUI.isInitialized) lightSourceUI.dispose()
        if (::skyCustomizationUI.isInitialized) skyCustomizationUI.dispose()
        if (::shaderEffectUI.isInitialized) shaderEffectUI.dispose()
        if (::particleSelectionUI.isInitialized) particleSelectionUI.dispose()
        if (::pauseMenuUI.isInitialized) pauseMenuUI.dispose()
        if (::enemyDebugUI.isInitialized) enemyDebugUI.dispose()
        if (::dialogueEditorUI.isInitialized) dialogueEditorUI.dispose()
        if (::characterInventoryUI.isInitialized) characterInventoryUI.dispose()

        // These are always safe because they are created in initializeUI()
        dialogSystem.dispose()
        stage.dispose()
        skin.dispose()
    }
}
