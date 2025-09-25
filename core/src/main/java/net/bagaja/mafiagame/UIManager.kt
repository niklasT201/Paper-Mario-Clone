package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
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
    FULL_VIOLENCE("Full")
}

class UIManager(
    val game: MafiaGame,
    private val blockSystem: BlockSystem,
    private val objectSystem: ObjectSystem,
    private val itemSystem: ItemSystem,
    private val carSystem: CarSystem,
    private val houseSystem: HouseSystem,
    private val backgroundSystem: BackgroundSystem,
    private val parallaxSystem: ParallaxBackgroundSystem,
    private val roomTemplateManager: RoomTemplateManager,
    private val interiorSystem: InteriorSystem,
    private val lightingManager: LightingManager,
    private val shaderEffectManager: ShaderEffectManager,
    private val enemySystem: EnemySystem,
    private val npcSystem: NPCSystem,
    private val particleSystem: ParticleSystem,
    private val spawnerSystem: SpawnerSystem,
    private val dialogueManager: DialogueManager
) {
    private lateinit var stage: Stage
    lateinit var skin: Skin
    private lateinit var layoutBuilder: UILayoutBuilder
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

    private var currentHudStyle = HudStyle.MINIMALIST

    // HUD Tables
    private lateinit var wantedPosterHudTable: Table
    private lateinit var minimalistHudTable: Table
    private lateinit var missionTimerLabel: Label
    private lateinit var healthLabelMinimalist: Label

    // Minimalist HUD elements
    private lateinit var weaponIconImageMinimalist: Image
    private lateinit var ammoLabelMinimalist: Label
    private lateinit var healthBarMinimalist: ProgressBar
    private lateinit var throwableCountLabelPoster: Label
    private var blinkTimer = 0f
    private lateinit var reloadIndicatorPoster: Label

    // Money Display elements
    private lateinit var moneyDisplayTable: Table
    private lateinit var moneyValueLabel: Label
    private lateinit var moneyStackTexture: Texture
    private lateinit var toolButtons: MutableList<Table>
    private lateinit var statsLabels: MutableMap<String, Label>
    private lateinit var placementInfoLabel: Label
    private lateinit var persistentMessageLabel: Label
    private lateinit var temporaryMessageLabel: Label
    private lateinit var fpsLabel: Label
    private lateinit var fpsTable: Table
    private lateinit var missionObjectiveLabel: Label
    private lateinit var leaveCarTimerLabel: Label
    private var isTimerInDelayPhase = false

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

    fun initialize() {
        stage = Stage(ScreenViewport())
        skin = UISkinFactory.createSkin() // Use the factory
        characterInventoryUI = CharacterInventoryUI(skin, stage, itemSystem)
        layoutBuilder = UILayoutBuilder(skin) // Create the builder

        dialogSystem = DialogSystem()
        dialogSystem.initialize(stage, skin)

        // Setup FPS display
        val (fpsTableCreated, fpsLabelCreated) = layoutBuilder.createFpsDisplay()
        fpsTable = fpsTableCreated
        fpsLabel = fpsLabelCreated
        stage.addActor(fpsTable)

        // Setup main UI
        setupMainUI()
        setupLetterboxUI()
        setupCinematicBarsUI()
        setupGameHUD() // This function will now build BOTH HUDs
        setupMoneyDisplay()

        temporaryMessageLabel = Label("", skin, "title")
        temporaryMessageLabel.setAlignment(Align.center)
        temporaryMessageLabel.color = Color.GREEN // Use a different color for feedback
        temporaryMessageLabel.isVisible = false
        val tempMessageTable = Table()
        tempMessageTable.setFillParent(true)
        tempMessageTable.top().pad(90f) // Position it slightly below the persistent message
        tempMessageTable.add(temporaryMessageLabel)
        stage.addActor(tempMessageTable)

        // Setup persistent message
        persistentMessageLabel = layoutBuilder.createPersistentMessageLabel()
        val persistentMessageTable = Table()
        persistentMessageTable.setFillParent(true)
        persistentMessageTable.top().pad(50f)
        persistentMessageTable.add(persistentMessageLabel)
        stage.addActor(persistentMessageTable)

        // 1. Create the labels for the objective and timer
        missionObjectiveLabel = Label("", skin, "title")

        missionTimerLabel = Label("", skin, "title")
        missionTimerLabel.setFontScale(1.0f) // MODIFIED: Reduced font size to standard
        missionTimerLabel.color = Color.WHITE
        missionTimerLabel.isVisible = false

        // 2. Create ONE table in the top-right corner for both labels
        val objectiveTable = Table()
        objectiveTable.setFillParent(true)
        objectiveTable.top().right().pad(20f)

        // 3. Add the objective text to the first row, aligned right
        objectiveTable.add(missionObjectiveLabel).right()
        objectiveTable.row() // Move to the next row

        // 4. Add the timer to the second row, aligned right
        objectiveTable.add(missionTimerLabel).right().padTop(5f)

        stage.addActor(objectiveTable)

        // Leave Car Timer is separate and stays at the bottom center (Unchanged)
        leaveCarTimerLabel = Label("", skin, "title")
        leaveCarTimerLabel.setFontScale(1.0f)
        leaveCarTimerLabel.color = Color.RED
        leaveCarTimerLabel.setAlignment(Align.center)
        leaveCarTimerLabel.isVisible = false
        val timerTable = Table()
        timerTable.setFillParent(true)
        timerTable.bottom().padBottom(50f) // Position at the bottom center of the screen
        timerTable.add(leaveCarTimerLabel)
        stage.addActor(timerTable)

        // Initialize all your selection UIs
        blockSelectionUI = BlockSelectionUI(blockSystem, skin, stage)
        blockSelectionUI.initialize()

        // Initialize object selection UI
        objectSelectionUI = ObjectSelectionUI(objectSystem, skin, stage)
        objectSelectionUI.initialize()

        // Initialize item selection UI
        itemSelectionUI = ItemSelectionUI(itemSystem, skin, stage)
        itemSelectionUI.initialize()

        // Initialize car selection UI
        carSelectionUI = CarSelectionUI(skin, stage, carSystem, enemySystem, npcSystem)
        carSelectionUI.initialize()

        // Initialize light selection UI
        lightSourceUI = LightSourceUI(skin, stage)
        lightSourceUI.initialize()

        // Initialize house selection UI
        houseSelectionUI = HouseSelectionUI(houseSystem, roomTemplateManager, skin, stage)
        houseSelectionUI.initialize()

        // Initialize background selection UI
        backgroundSelectionUI = BackgroundSelectionUI(backgroundSystem, skin, stage)
        backgroundSelectionUI.initialize()

        // Initialize parallax selection UI
        parallaxSelectionUI = ParallaxSelectionUI(parallaxSystem, skin, stage)
        parallaxSelectionUI.initialize()

        // Initialize interior selection UI
        interiorSelectionUI = InteriorSelectionUI(interiorSystem, skin, stage)
        interiorSelectionUI.initialize()

        skyCustomizationUI = SkyCustomizationUI(skin, stage, lightingManager)
        skyCustomizationUI.initialize()

        enemySelectionUI = EnemySelectionUI(enemySystem, skin, stage)
        enemySelectionUI.initialize()

        npcSelectionUI = NPCSelectionUI(npcSystem, skin, stage)
        npcSelectionUI.initialize()

        particleSelectionUI = ParticleSelectionUI(particleSystem, skin, stage)
        particleSelectionUI.initialize()

        spawnerUI = SpawnerUI(skin, stage, spawnerSystem::removeSpawner, this)

        shaderEffectUI = ShaderEffectUI(skin, stage, shaderEffectManager)
        shaderEffectUI.initialize()

        enemyDebugUI = EnemyDebugUI(skin, stage)

        visualSettingsUI = VisualSettingsUI(
            skin,
            game.cameraManager,
            this,
            game.targetingIndicatorSystem,
            game.trajectorySystem,
            game.meleeRangeIndicatorSystem,
            game.playerSystem
        )

        pauseMenuUI = PauseMenuUI(skin, stage, this, game.saveLoadSystem)
        pauseMenuUI.initialize()

        // Set initial visibility for the main UI panel
        mainTable.isVisible = isUIVisible && game.isEditorMode
        missionEditorUI = MissionEditorUI(skin, stage, game.missionSystem, this)
        triggerEditorUI = TriggerEditorUI(skin, stage, game.missionSystem, game.triggerSystem, game.sceneManager, this)
        dialogueEditorUI = DialogueEditorUI(skin, stage, this, dialogueManager)
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

    fun updateFps() {
        if (fpsTable.isVisible) {
            fpsLabel.setText("FPS: ${Gdx.graphics.framesPerSecond}")
        }
    }

    fun toggleFpsLabel() {
        fpsTable.isVisible = !fpsTable.isVisible
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
            missionTimerLabel.isVisible = true

            if (isDelay) {
                missionTimerLabel.color = Color.LIGHT_GRAY
            } else {
                missionTimerLabel.color = Color.WHITE
            }

            val minutes = (timeRemaining / 60).toInt()
            val seconds = (timeRemaining % 60).toInt()
            missionTimerLabel.setText(String.format("%02d:%02d", minutes, seconds))

            // Ensure it's fully visible if it was faded out before
            missionTimerLabel.color.a = 1f
        } else {
            // fade it out smoothly
            if (missionTimerLabel.isVisible) {
                missionTimerLabel.addAction(Actions.sequence(
                    Actions.fadeOut(0.3f, Interpolation.fade),
                    Actions.run { missionTimerLabel.isVisible = false }
                ))
            }
        }
    }

    // Menu and settings methods stay the same
    fun showVisualSettings() {
        pauseMenuUI.hideInstantly()
        visualSettingsUI.show(stage)
    }

    fun togglePauseMenu() {
        if (visualSettingsUI.isVisible()) {
            returnToPauseMenu()
            return
        }

        if (pauseMenuUI.isVisible()) {
            pauseMenuUI.hide()
        } else {
            pauseMenuUI.show()
        }
    }

    fun isPauseMenuVisible(): Boolean = pauseMenuUI.isVisible() || visualSettingsUI.isVisible()
    fun returnToPauseMenu() {
        visualSettingsUI.hide()
        pauseMenuUI.show()
    }

    fun isInventoryVisible(): Boolean {
        return if (::characterInventoryUI.isInitialized) {
            characterInventoryUI.isVisible()
        } else {
            false
        }
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

    private fun setupMoneyDisplay() {
        try {
            moneyStackTexture = Texture(Gdx.files.internal("gui/dollar_stack.png"))
        } catch (e: Exception) {
            // Fallback
            val pixmap = Pixmap(32, 32, Pixmap.Format.RGBA8888); pixmap.setColor(Color.GREEN); pixmap.fill();
            moneyStackTexture = Texture(pixmap); pixmap.dispose()
        }

        moneyDisplayTable = Table()
        val moneyIcon = Image(moneyStackTexture).apply { setScaling(Scaling.fit) }
        val dollarLabel = Label("$", skin, "title")
        moneyValueLabel = Label("0", skin, "title")

        // CHANGED: Set the new font color for both labels
        val moneyFontColor = Color.WHITE
        dollarLabel.color = moneyFontColor
        moneyValueLabel.color = moneyFontColor

        moneyDisplayTable.add(moneyIcon).size(40f)
        moneyDisplayTable.add(dollarLabel).padLeft(10f)
        moneyDisplayTable.add(moneyValueLabel).padLeft(5f)

        // Position it off-screen to the right
        moneyDisplayTable.setPosition(stage.width, stage.height - 80f)
        moneyDisplayTable.isVisible = false // Start hidden
        stage.addActor(moneyDisplayTable)
    }

    fun setHudStyle(newStyle: HudStyle) {
        currentHudStyle = newStyle
        wantedPosterHudTable.isVisible = (newStyle == HudStyle.WANTED_POSTER) && !game.isEditorMode && !isPauseMenuVisible()
        minimalistHudTable.isVisible = (newStyle == HudStyle.MINIMALIST) && !game.isEditorMode && !isPauseMenuVisible()
    }

    fun getCurrentHudStyle(): HudStyle = currentHudStyle

    fun showMoneyUpdate(newAmount: Int) {
        moneyValueLabel.setText(newAmount.toString())
        moneyDisplayTable.pack() // Recalculate size based on new text

        // Always stop any previous animation sequence
        moneyDisplayTable.clearActions()

        val yPos = stage.height - 80f
        val targetX = stage.width - moneyDisplayTable.width - 30f // The final on-screen X position
        val startX = stage.width // The off-screen X position to the right

        // Make sure the actor is visible to start any new animation.
        moneyDisplayTable.isVisible = true

        // Check if the table is already on-screen by checking its current X coordinate.
        val isAlreadyOnScreen = moneyDisplayTable.x < startX

        // Define the sequence for sliding out and hiding
        val slideOutSequence = Actions.sequence(
            Actions.moveTo(startX, yPos, 0.4f, Interpolation.swingIn),
            Actions.run { moneyDisplayTable.isVisible = false }
        )

        if (isAlreadyOnScreen) {
            moneyDisplayTable.setPosition(targetX, yPos)
            moneyDisplayTable.addAction(Actions.sequence(
                Actions.delay(2.5f),
                slideOutSequence
            ))
        } else {
            // The display is completely off-screen. Play the full "slide in" animation.
            moneyDisplayTable.setPosition(startX, yPos) // Ensure it starts off-screen
            moneyDisplayTable.addAction(Actions.sequence(
                Actions.moveTo(targetX, yPos, 0.4f, Interpolation.swingOut), // Slide in
                Actions.delay(2.5f), // Wait
                slideOutSequence // Slide out
            ))
        }
    }

    fun isDialogActive(): Boolean = dialogSystem.isActive()

    fun updateMissionObjective(text: String) {
        missionObjectiveLabel.setText(text)
        // Optional: Add a fade-in animation
        missionObjectiveLabel.clearActions()
        missionObjectiveLabel.color.a = 0f
        missionObjectiveLabel.addAction(Actions.fadeIn(0.5f))
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

    fun getViolenceLevel(): ViolenceLevel = currentViolenceLevel

    fun updateTriggerRadiusField(newRadius: Float) {
        triggerEditorUI.setRadiusText(newRadius.toString())
    }

    fun render() {
        dialogSystem.update(Gdx.graphics.deltaTime)

        if (!game.isEditorMode && persistentMessageLabel.isVisible) {
            persistentMessageLabel.isVisible = false
        }

        // Update HUD visibility and values based on game state
        val shouldShowHud = !game.isEditorMode && !isPauseMenuVisible()
        wantedPosterHudTable.isVisible = shouldShowHud && currentHudStyle == HudStyle.WANTED_POSTER
        minimalistHudTable.isVisible = shouldShowHud && currentHudStyle == HudStyle.MINIMALIST

        if (shouldShowHud) {
            // Update health bar value every frame
            healthBar.value = game.playerSystem.getHealthPercentage()

            if (game.playerSystem.equippedWeapon != lastEquippedWeapon) {
                updateWeaponIcon() // This updates the main icon
                lastEquippedWeapon = game.playerSystem.equippedWeapon
            }

            // --- POSTER & MINIMALIST HUD AMMO LOGIC ---
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

        pauseMenuUI.update(Gdx.graphics.deltaTime)
        skyCustomizationUI.update()
        shaderEffectUI.update()
        enemyDebugUI.act(Gdx.graphics.deltaTime)
        stage.act(Gdx.graphics.deltaTime)
        stage.draw()
    }

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        updateLetterboxSize()
        updateCinematicBarsSize()
    }

    fun dispose() {
        if (::wantedPosterTexture.isInitialized) {
            wantedPosterTexture.dispose()
        }
        blockSelectionUI.dispose()
        objectSelectionUI.dispose()
        itemSelectionUI.dispose()
        carSelectionUI.dispose()
        houseSelectionUI.dispose()
        backgroundSelectionUI.dispose()
        parallaxSelectionUI.dispose()
        interiorSelectionUI.dispose()
        lightSourceUI.dispose()
        skyCustomizationUI.dispose()
        shaderEffectUI.dispose()
        particleSelectionUI.dispose()
        pauseMenuUI.dispose()
        enemyDebugUI.dispose()
        if (::fistTexture.isInitialized) {
            fistTexture.dispose()
        }
        if (::weaponUiTexture.isInitialized) {
            weaponUiTexture.dispose()
        }
        if (::healthBarEmptyTexture.isInitialized) {
            healthBarEmptyTexture.dispose()
        }
        if (::healthBarFullTexture.isInitialized) {
            healthBarFullTexture.dispose()
        }
        dialogSystem.dispose()
        characterInventoryUI.dispose()
        stage.dispose()
        skin.dispose()
    }
}
