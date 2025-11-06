package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array as GdxArray
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.List
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import java.util.*

class MissionEditorUI(
    private val skin: Skin,
    private val stage: Stage,
    private val missionSystem: MissionSystem,
    private val uiManager: UIManager
) {
    private val window: Window = Window("Mission Editor", skin, "dialog")
    private var currentMissionDef: MissionDefinition? = null
    private var defaultMissionBackground: Drawable
    private var selectedMissionBackground: Drawable

    // Left Panel
    private val missionListContainer: Table
    private var selectedMissionRow: Table? = null

    // Right Panel
    private val missionIdField: TextField
    private val missionTitleField: TextField
    private val missionDescriptionArea: TextArea
    private val prerequisitesField: TextField
    private val scopeSelectBox: SelectBox<String>

    private val objectivesContainer: VerticalGroup
    private val rewardsContainer: VerticalGroup
    private val startEventsContainer: VerticalGroup
    private val completeEventsContainer: VerticalGroup

    private val modeSwitchButton: TextButton
    private val missionSelectionTable: Table
    private val missionSelectBox: SelectBox<String>

    // Temporary storage while editing
    private var tempObjectives = mutableListOf<MissionObjective>()
    private var tempRewards = mutableListOf<MissionReward>()
    private var tempStartEvents = mutableListOf<GameEvent>()
    private var tempCompleteEvents = mutableListOf<GameEvent>()

    private val radiusField: TextField
    private val areaXField: TextField
    private val areaYField: TextField
    private val areaZField: TextField

    private lateinit var timeRestrictedCheckbox: CheckBox
    private lateinit var timeSliderTable: Table
    private lateinit var startTimeSlider: Slider
    private lateinit var endTimeSlider: Slider
    private lateinit var startTimeLabel: Label
    private lateinit var endTimeLabel: Label

    private val tempCycle = DayNightCycle()
    private fun updateTimeLabels() {
        tempCycle.setDayProgress(startTimeSlider.value)
        startTimeLabel.setText("Start: ${tempCycle.getTimeString()}")
        tempCycle.setDayProgress(endTimeSlider.value)
        endTimeLabel.setText("End: ${tempCycle.getTimeString()}")
    }

    init {
        window.isMovable = true
        window.isModal = false
        window.setSize(Gdx.graphics.width * 1.9f, Gdx.graphics.height * 1.8f)
        window.setPosition(stage.width / 2f, stage.height / 2f, Align.center)
        window.padTop(40f)

        radiusField = TextField("", skin)
        areaXField = TextField("", skin)
        areaYField = TextField("", skin)
        areaZField = TextField("", skin)

        val defaultColor = Color.valueOf("#3a3a3a") // A dark, neutral gray
        val selectedColor = Color.valueOf("#553C73") // A muted, dark purple
        defaultMissionBackground = skin.newDrawable("white", defaultColor)
        selectedMissionBackground = skin.newDrawable("white", selectedColor)

        val mainContentTable = Table()

        val topBar = Table()
        modeSwitchButton = TextButton("Mode: World Editing", skin)
        missionSelectBox = SelectBox(skin)
        missionSelectionTable = Table()
        missionSelectionTable.add(Label("Editing Mission:", skin)).padRight(10f)
        missionSelectionTable.add(missionSelectBox).growX()
        missionSelectionTable.isVisible = false

        topBar.add(modeSwitchButton).padRight(20f)
        topBar.add(missionSelectionTable).growX()
        mainContentTable.add(topBar).fillX().pad(10f).colspan(2).row()

        // --- Left Panel ---
        val leftPanel = Table()
        missionListContainer = Table()
        missionListContainer.top() // Align rows to the top of the table

        val listScrollPane = ScrollPane(missionListContainer, skin)
        listScrollPane.fadeScrollBars = false // Keep scrollbars visible

        val newMissionButton = TextButton("New Mission", skin)
        val deleteMissionButton = TextButton("Delete Selected", skin)
        val listButtonTable = Table()
        listButtonTable.add(newMissionButton).growX().pad(5f)
        listButtonTable.add(deleteMissionButton).growX().pad(5f)

        leftPanel.add(listScrollPane).expand().fill().row()
        leftPanel.add(listButtonTable).fillX()

        // --- Right Panel (for all mission details) ---
        val editorDetailsTable = Table()
        editorDetailsTable.pad(10f)
        editorDetailsTable.align(Align.topLeft)

        missionIdField = TextField("", skin).apply { isDisabled = true }
        missionTitleField = TextField("", skin)
        missionDescriptionArea = TextArea("", skin)
        prerequisitesField = TextField("", skin)
        scopeSelectBox = SelectBox(skin); scopeSelectBox.items = GdxArray(MissionScope.entries.map { it.name }.toTypedArray())

        objectivesContainer = VerticalGroup().apply { space(5f); wrap(false); align(Align.left) }
        rewardsContainer = VerticalGroup().apply { space(5f); wrap(false); align(Align.left) }
        startEventsContainer = VerticalGroup().apply { space(5f); wrap(false); align(Align.left) }
        completeEventsContainer = VerticalGroup().apply { space(5f); wrap(false); align(Align.left) }

        editorDetailsTable.add(Label("ID:", skin)).left(); editorDetailsTable.add(missionIdField).growX().row()
        editorDetailsTable.add(Label("Title:", skin)).left(); editorDetailsTable.add(missionTitleField).growX().row()
        editorDetailsTable.add(Label("Description:", skin)).left().top(); editorDetailsTable.add(ScrollPane(missionDescriptionArea, skin)).growX().height(60f).row()
        editorDetailsTable.add(Label("Prerequisites (IDs):", skin)).left(); editorDetailsTable.add(prerequisitesField).growX().row()
        timeRestrictedCheckbox = CheckBox(" Time Restricted", skin)
        editorDetailsTable.add(timeRestrictedCheckbox).colspan(2).left().padTop(8f).row()

        timeSliderTable = Table()
        startTimeSlider = Slider(0f, 1f, 0.01f, false, skin)
        endTimeSlider = Slider(0f, 1f, 0.01f, false, skin)
        startTimeLabel = Label("Start: 00:00", skin, "small")
        endTimeLabel = Label("End: 00:00", skin, "small")

        val tempCycle = DayNightCycle() // Helper to convert progress to time string
        fun updateTimeLabels() {
            tempCycle.setDayProgress(startTimeSlider.value)
            startTimeLabel.setText("Start: ${tempCycle.getTimeString()}")
            tempCycle.setDayProgress(endTimeSlider.value)
            endTimeLabel.setText("End: ${tempCycle.getTimeString()}")
        }

        startTimeSlider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) = updateTimeLabels()
        })
        endTimeSlider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) = updateTimeLabels()
        })

        timeSliderTable.add(startTimeLabel).width(100f)
        timeSliderTable.add(startTimeSlider).growX()
        timeSliderTable.row()
        timeSliderTable.add(endTimeLabel).width(100f)
        timeSliderTable.add(endTimeSlider).growX()

        editorDetailsTable.add(timeSliderTable).colspan(2).fillX().padLeft(20f).row()

        timeRestrictedCheckbox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                timeSliderTable.isVisible = timeRestrictedCheckbox.isChecked
                // The main editor window will now keep its size.
            }
        })
        editorDetailsTable.add(Label("Scope:", skin)).left(); editorDetailsTable.add(scopeSelectBox).row()

        val buttonsTable = Table()
        val showFlowButton = TextButton("View Mission Flow", skin)
        val editModifiersButton = TextButton("Edit Modifiers", skin)
        buttonsTable.add(showFlowButton).pad(5f)
        buttonsTable.add(editModifiersButton).pad(5f)
        editorDetailsTable.add(buttonsTable).colspan(2).center().padTop(10f).padBottom(15f).row()

        editorDetailsTable.add(Label("--- Debug Controls ---", skin, "title")).colspan(2).center().padTop(15f).row()

        val debugButtonTable = Table()
        val forceStartButton = TextButton("Force Start", skin)
        val forceEndButton = TextButton("Force End Current", skin)
        val resetProgressButton = TextButton("Reset All Progress", skin)

        debugButtonTable.add(forceStartButton).pad(5f)
        debugButtonTable.add(forceEndButton).pad(5f)
        debugButtonTable.add(resetProgressButton).pad(5f)

        editorDetailsTable.add(debugButtonTable).colspan(2).center().padBottom(15f).row()

        editorDetailsTable.add(Label("--- Events on Start ---", skin, "title")).colspan(2).padTop(15f).row()
        editorDetailsTable.add(ScrollPane(startEventsContainer, skin)).colspan(2).growX().height(100f).row()
        val addStartEventButton = TextButton("Add Start Event", skin)
        editorDetailsTable.add(addStartEventButton).colspan(2).left().padTop(5f).row()

        editorDetailsTable.add(Label("--- Objectives ---", skin, "title")).colspan(2).padTop(15f).row()
        editorDetailsTable.add(ScrollPane(objectivesContainer, skin)).colspan(2).growX().height(150f).row()
        val addObjectiveButton = TextButton("Add Objective", skin)
        editorDetailsTable.add(addObjectiveButton).colspan(2).left().padTop(5f).row()

        editorDetailsTable.add(Label("--- Events on Complete ---", skin, "title")).colspan(2).padTop(15f).row()
        editorDetailsTable.add(ScrollPane(completeEventsContainer, skin)).colspan(2).growX().height(100f).row()
        val addCompleteEventButton = TextButton("Add Complete Event", skin)
        editorDetailsTable.add(addCompleteEventButton).colspan(2).left().padTop(5f).row()

        editorDetailsTable.add(Label("--- Rewards ---", skin, "title")).colspan(2).padTop(15f).row()
        editorDetailsTable.add(ScrollPane(rewardsContainer, skin)).colspan(2).growX().height(100f).row()
        val addRewardButton = TextButton("Add Reward", skin)
        editorDetailsTable.add(addRewardButton).colspan(2).left().padTop(5f).row()


        // --- Assembly using SplitPane ---
        val editorScrollPane = ScrollPane(editorDetailsTable, skin)
        editorScrollPane.fadeScrollBars = false

        // The SplitPane connects the left and right panels
        val splitPane = SplitPane(leftPanel, editorScrollPane, false, skin)
        splitPane.splitAmount = 0.25f // Left panel takes 25% of the width
        mainContentTable.add(splitPane).expand().fill().row()

        // Bottom buttons
        val saveButton = TextButton("Save and Close", skin); val closeButton = TextButton("Close Without Saving", skin)
        val bottomButtonTable = Table(); bottomButtonTable.add(saveButton).pad(10f); bottomButtonTable.add(closeButton).pad(10f)
        mainContentTable.add(bottomButtonTable).padTop(10f)

        window.add(mainContentTable).expand().fill()
        window.isVisible = false
        stage.addActor(window)

        // --- Listeners ---
        modeSwitchButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                uiManager.game.sceneManager.clearMissionPreviews() // Clear old previews
                if (uiManager.currentEditorMode == EditorMode.WORLD) {
                    uiManager.currentEditorMode = EditorMode.MISSION
                    modeSwitchButton.setText("Mode: Mission Editing")
                    missionSelectionTable.isVisible = true
                    // Select the first mission in the list by default
                    if (missionSelectBox.items.size > 0) {
                        missionSelectBox.selectedIndex = 0
                        val missionId = missionSelectBox.selected.split(":")[0].trim()
                        uiManager.selectedMissionForEditing = missionSystem.getMissionDefinition(missionId)
                        uiManager.setPersistentMessage("MISSION EDITING: ${uiManager.selectedMissionForEditing?.title}")
                    } else {
                        uiManager.selectedMissionForEditing = null
                        uiManager.setPersistentMessage("MISSION EDITING: No missions available!")
                    }
                } else {
                    uiManager.currentEditorMode = EditorMode.WORLD
                    modeSwitchButton.setText("Mode: World Editing")
                    missionSelectionTable.isVisible = false
                    uiManager.selectedMissionForEditing = null
                    uiManager.clearPersistentMessage()
                }
            }
        })

        showFlowButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { showMissionFlowDialog() } })
        missionSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val selectedText = missionSelectBox.selected ?: return
                val missionId = selectedText.split(":")[0].trim()

                // Find the corresponding row widget in our main list
                val rowToSelect = missionListContainer.children.find { (it as? Table)?.userObject == missionId } as? Table

                // Call our central selection function
                if (rowToSelect != null) {
                    selectMissionRow(rowToSelect, missionId)
                }
            }
        })
        addStartEventButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { showEventDialog(null, true) } })
        addCompleteEventButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { showEventDialog(null, false) } })
        addObjectiveButton.addListener(object: ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { showObjectiveDialog(null) } })
        addRewardButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { showRewardDialog(null) } })
        newMissionButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val newMission = missionSystem.createNewMission()
                refreshMissionList()
                val newRow = missionListContainer.children.find { (it as? Table)?.userObject == newMission.id } as? Table
                if (newRow != null) {
                    selectMissionRow(newRow, newMission.id)
                }
                missionSystem.game.triggerSystem.refreshTriggers()
            }
        })

        deleteMissionButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                // Use the userObject from the selected row table to get the ID
                val selectedId = selectedMissionRow?.userObject as? String
                if (selectedId != null) {
                    missionSystem.deleteMission(selectedId)
                    refreshMissionList()
                    clearEditor()
                    missionSystem.game.triggerSystem.refreshTriggers()
                }
            }
        })
        editModifiersButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { showModifiersDialog() } })
        saveButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                applyChanges()
                missionSystem.game.triggerSystem.refreshTriggers()
                hide() // This calls the hide() method below
            }
        })
        closeButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { hide() } })

        forceStartButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                currentMissionDef?.id?.let {
                    missionSystem.forceStartMission(it)
                    hide() // Hide the editor so you can see the mission start
                }
            }
        })

        forceEndButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                missionSystem.forceEndMission()
            }
        })

        resetProgressButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                missionSystem.resetAllMissionProgress()
                // Refresh the list to remove any visual "completed" status indicators if you add them later
                refreshMissionList()
            }
        })
    }

    private fun initializeMode() {
        val isMissionMode = uiManager.currentEditorMode == EditorMode.MISSION

        modeSwitchButton.setText(if (isMissionMode) "Mode: Mission Editing" else "Mode: World Editing")
        missionSelectionTable.isVisible = isMissionMode

        if (isMissionMode) {
            if (missionSelectBox.items.size > 0) {
                if (missionSelectBox.selectedIndex == -1) {
                    missionSelectBox.selectedIndex = 0
                }
                val missionId = missionSelectBox.selected.split(":")[0].trim()
                uiManager.selectedMissionForEditing = missionSystem.getMissionDefinition(missionId)
                uiManager.setPersistentMessage("MISSION EDITING: ${uiManager.selectedMissionForEditing?.title}")
            } else {
                uiManager.selectedMissionForEditing = null
                uiManager.setPersistentMessage("MISSION EDITING: No missions available!")
            }
        } else {
            uiManager.selectedMissionForEditing = null
            uiManager.clearPersistentMessage()
        }
    }

    private fun populateEditor(missionId: String) {
        val mission = missionSystem.getMissionDefinition(missionId) ?: return
        currentMissionDef = mission

        tempObjectives = mission.objectives
        tempRewards = mission.rewards
        tempStartEvents = mission.eventsOnStart
        tempCompleteEvents = mission.eventsOnComplete

        missionIdField.text = mission.id
        missionTitleField.text = mission.title
        missionDescriptionArea.text = mission.description
        prerequisitesField.text = mission.prerequisites.joinToString(", ")

        timeRestrictedCheckbox.isChecked = mission.availableStartTime != null
        timeSliderTable.isVisible = timeRestrictedCheckbox.isChecked
        startTimeSlider.value = mission.availableStartTime ?: 0.25f
        endTimeSlider.value = mission.availableEndTime ?: 0.9f

        // Now you can just call the shared helper function
        updateTimeLabels()

        scopeSelectBox.selected = mission.scope.name

        refreshObjectiveWidgets()
        refreshRewardWidgets()
        refreshEventWidgets()
    }

    private fun clearEditor() {
        currentMissionDef = null
        missionIdField.text = ""; missionTitleField.text = ""; missionDescriptionArea.text = ""; prerequisitesField.text = ""
        objectivesContainer.clearChildren()
        rewardsContainer.clearChildren()
        startEventsContainer.clearChildren()
        completeEventsContainer.clearChildren()
    }

    private fun applyChanges() {
        val mission = currentMissionDef ?: return

        // Save the simple text fields
        mission.title = missionTitleField.text.ifBlank { "Untitled Mission" }
        mission.description = missionDescriptionArea.text
        mission.prerequisites = prerequisitesField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (timeRestrictedCheckbox.isChecked) {
            mission.availableStartTime = startTimeSlider.value
            mission.availableEndTime = endTimeSlider.value
        } else {
            mission.availableStartTime = null
            mission.availableEndTime = null
        }

        mission.scope = MissionScope.valueOf(scopeSelectBox.selected)

        missionSystem.saveMission(mission)
    }

    private fun showModifiersDialog() {
        val mission = currentMissionDef ?: return
        val dialog = Dialog("Edit Mission Modifiers", skin, "dialog")
        dialog.isMovable = true

        val content = Table()
        content.pad(10f).defaults().pad(4f).align(Align.left)

        // --- Player Modifiers ---
        val playerTable = Table(skin)
        playerTable.add(Label("[YELLOW]--- Player Modifiers ---", skin)).colspan(2).left().padBottom(5f).row()
        val unlimitedHealth = CheckBox(" Unlimited Health", skin).apply { isChecked = mission.modifiers.setUnlimitedHealth }
        val incomingDmg = TextField(mission.modifiers.incomingDamageMultiplier.toString(), skin)
        val outgoingDmg = TextField(mission.modifiers.playerDamageMultiplier.toString(), skin)
        val speedMulti = TextField(mission.modifiers.playerSpeedMultiplier.toString(), skin)
        val infiniteAmmoAll = CheckBox(" Infinite Ammo (All)", skin).apply { isChecked = mission.modifiers.infiniteAmmo }
        val infiniteAmmoSpecificSelectBox = SelectBox<String>(skin).apply {
            items = GdxArray(arrayOf("(None)") + WeaponType.entries.map { it.displayName }.toTypedArray())
            selected = mission.modifiers.infiniteAmmoForWeapon?.displayName ?: "(None)"
        }
        playerTable.add(infiniteAmmoAll).colspan(2).left().row()
        playerTable.add("Infinite Ammo (Specific):").left(); playerTable.add(infiniteAmmoSpecificSelectBox).growX().row()
        val disableSwitch = CheckBox(" Disable Weapon Switching", skin).apply { isChecked = mission.modifiers.disableWeaponSwitching }
        val disablePickups = CheckBox(" Disable Weapon Pickups", skin).apply { isChecked = mission.modifiers.disableWeaponPickups }
        val disableItemPickups = CheckBox(" Disable Item Pickups", skin).apply { isChecked = mission.modifiers.disableItemPickups }
        val playerOneHitKills = CheckBox(" One-Hit Kills (Player)", skin).apply { isChecked = mission.modifiers.playerHasOneHitKills }
        val enemyOneHitKills = CheckBox(" One-Hit Kills (Enemies)", skin).apply { isChecked = mission.modifiers.enemiesHaveOneHitKills }
        playerTable.add(playerOneHitKills).colspan(2).left().row()
        playerTable.add(enemyOneHitKills).colspan(2).left().row()

        playerTable.add(unlimitedHealth).colspan(2).left().row()
        playerTable.add("Incoming Dmg x:"); playerTable.add(incomingDmg).width(60f).left().row()
        playerTable.add("Player Dmg x:"); playerTable.add(outgoingDmg).width(60f).left().row()
        playerTable.add("Player Speed x:"); playerTable.add(speedMulti).width(60f).left().row()
        playerTable.add(infiniteAmmoAll).colspan(2).left().row()
        playerTable.add(disableSwitch).colspan(2).left().row()
        playerTable.add(disablePickups).colspan(2).left().row()
        playerTable.add(disableItemPickups).colspan(2).left().row()

        // --- Vehicle Modifiers ---
        val vehicleTable = Table(skin)
        vehicleTable.add(Label("[LIME]--- Vehicle Modifiers ---", skin)).colspan(2).left().padBottom(5f).row()
        val invincibleVehicle = CheckBox(" Player's Vehicle is Invincible", skin).apply { isChecked = mission.modifiers.makePlayerVehicleInvincible }
        val allCarsUnlocked = CheckBox(" All Cars are Unlocked", skin).apply { isChecked = mission.modifiers.allCarsUnlocked }
        val carSpeedMulti = TextField(mission.modifiers.carSpeedMultiplier.toString(), skin)
        vehicleTable.add(invincibleVehicle).colspan(2).left().row()
        vehicleTable.add(allCarsUnlocked).colspan(2).left().row()
        vehicleTable.add("Car Speed x:"); vehicleTable.add(carSpeedMulti).width(60f).left().row()

        // --- World & AI Modifiers ---
        val worldTable = Table(skin)
        worldTable.add(Label("[CYAN]--- World & AI Modifiers ---", skin)).colspan(2).left().padBottom(5f).row()

        val overrideEnemyBehaviorSelectBox = SelectBox<String>(skin).apply {
            items = GdxArray(arrayOf("(None)") + EnemyBehavior.entries.map { it.displayName }.toTypedArray())
            selected = mission.modifiers.overrideEnemyBehavior?.displayName ?: "(None)"
        }
        val overrideNpcBehaviorSelectBox = SelectBox<String>(skin).apply {
            items = GdxArray(arrayOf("(None)") + NPCBehavior.entries.map { it.displayName }.toTypedArray())
            selected = mission.modifiers.overrideNpcBehavior?.displayName ?: "(None)"
        }
        worldTable.add("Force Enemy AI:").left(); worldTable.add(overrideEnemyBehaviorSelectBox).growX().row()
        worldTable.add("Force NPC AI:").left(); worldTable.add(overrideNpcBehaviorSelectBox).growX().row()
        val allHousesLocked = CheckBox(" All Houses are Locked", skin).apply { isChecked = mission.modifiers.allHousesLocked }
        val allHousesUnlocked = CheckBox(" All Houses are Unlocked", skin).apply { isChecked = mission.modifiers.allHousesUnlocked }
        val disableCarSpawners = CheckBox(" Disable Car Spawners", skin).apply { isChecked = mission.modifiers.disableCarSpawners }
        val disableCharacterSpawners = CheckBox(" Disable Character Spawners", skin).apply { isChecked = mission.modifiers.disableCharacterSpawners }
        val civiliansFlee = CheckBox(" Civilians Flee on Sight", skin).apply { isChecked = mission.modifiers.civiliansFleeOnSight }
        val increasedSpawns = CheckBox(" Increased Enemy Spawns", skin).apply { isChecked = mission.modifiers.increasedEnemySpawns }
        val disableNoItemDrops = CheckBox(" Disable All Item Drops", skin).apply { isChecked = mission.modifiers.disableNoItemDrops }
        worldTable.add(allHousesLocked).colspan(2).left().row()
        worldTable.add(allHousesUnlocked).colspan(2).left().row()
        worldTable.add(disableCarSpawners).colspan(2).left().row()
        worldTable.add(disableCharacterSpawners).colspan(2).left().row()
        worldTable.add(disableNoItemDrops).colspan(2).left().row()
        worldTable.add(civiliansFlee).colspan(2).left().row()
        worldTable.add(increasedSpawns).colspan(2).left().row()

        val weatherTable = Table(skin)
        weatherTable.add(Label("[BLUE]--- Weather Modifiers ---", skin)).colspan(2).left().padTop(10f).padBottom(5f).row()
        val overrideWeatherCheckbox = CheckBox(" Override Weather", skin).apply { isChecked = mission.modifiers.overrideRainIntensity != null }
        val intensityField = TextField(mission.modifiers.overrideRainIntensity?.toString() ?: "0.0", skin)
        val durationField = TextField(mission.modifiers.rainDuration?.toString() ?: "", skin).apply { messageText = "Infinite" }
        val delayField = TextField(mission.modifiers.rainStartDelay?.toString() ?: "", skin).apply { messageText = "Immediate" }

        weatherTable.add(overrideWeatherCheckbox).colspan(2).left().row()
        val settingsTable = Table(skin)
        settingsTable.add("Intensity (0-1):"); settingsTable.add(intensityField).width(60f).padRight(10f)
        settingsTable.add("Duration (s):"); settingsTable.add(durationField).width(60f).padRight(10f)
        settingsTable.add("Delay (s):"); settingsTable.add(delayField).width(60f)
        weatherTable.add(settingsTable).colspan(2).left().row()
        settingsTable.isVisible = overrideWeatherCheckbox.isChecked // Show/hide based on checkbox

        overrideWeatherCheckbox.addListener {
            settingsTable.isVisible = overrideWeatherCheckbox.isChecked
            dialog.pack()
            true
        }

        val freezeTimeCheckbox = CheckBox(" Freeze Time of Day", skin).apply { isChecked = mission.modifiers.freezeTimeAt != null }
        val timeSlider = Slider(0f, 1f, 0.01f, false, skin).apply { value = mission.modifiers.freezeTimeAt ?: 0.5f }
        val timeSliderTable = Table(skin).apply{ add(freezeTimeCheckbox).left(); add(timeSlider).growX() }
        timeSliderTable.isVisible = freezeTimeCheckbox.isChecked
        freezeTimeCheckbox.addListener(object: ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                timeSliderTable.isVisible = freezeTimeCheckbox.isChecked
            }
        })
        worldTable.add(freezeTimeCheckbox).colspan(2).left().row()
        worldTable.add(timeSliderTable).colspan(2).fillX().row()

        val audioTable = Table(skin)
        audioTable.add(Label("[ORANGE]--- Audio Modifiers ---", skin)).colspan(2).left().padTop(10f).padBottom(5f).row()

        val stopAllSoundsCheckbox = CheckBox(" Stop All Sounds on Start", skin).apply { isChecked = mission.modifiers.stopAllSounds }
        val disableAllEmittersCheckbox = CheckBox(" Disable All Audio Emitters", skin).apply { isChecked = mission.modifiers.disableAllAudioEmitters }

        val stopSpecificSoundsArea = TextArea(mission.modifiers.stopSpecificSounds.joinToString("\n"), skin)
        val disableEmittersArea = TextArea(mission.modifiers.disableEmittersWithSounds.joinToString("\n"), skin)

        audioTable.add(stopAllSoundsCheckbox).colspan(2).left().row()
        audioTable.add(disableAllEmittersCheckbox).colspan(2).left().row()
        audioTable.add(Label("Mute Specific Sound IDs (one per line):", skin)).colspan(2).left().padTop(8f).row()
        audioTable.add(ScrollPane(stopSpecificSoundsArea, skin)).colspan(2).growX().height(60f).row()
        audioTable.add(Label("Disable Emitters with Sound IDs (one per line):", skin)).colspan(2).left().padTop(8f).row()
        audioTable.add(ScrollPane(disableEmittersArea, skin)).colspan(2).growX().height(60f).row()

        // --- ASSEMBLE MAIN LAYOUT ---
        content.add(playerTable).top().padRight(15f)
        content.add(vehicleTable).top().padRight(15f)
        content.add(worldTable).top().row()
        content.add(weatherTable).top().colspan(3).row() // Add a .row() here
        content.add(audioTable).top().colspan(3)

        val scrollPane = ScrollPane(content, skin)
        scrollPane.fadeScrollBars = false
        dialog.contentTable.add(scrollPane).grow().minWidth(600f).minHeight(300f).maxHeight(450f)

        // --- Buttons ---
        val applyButton = TextButton("Apply", skin)
        applyButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                // Player Modifiers
                mission.modifiers.setUnlimitedHealth = unlimitedHealth.isChecked
                mission.modifiers.incomingDamageMultiplier = incomingDmg.text.toFloatOrNull() ?: 1.0f
                mission.modifiers.playerDamageMultiplier = outgoingDmg.text.toFloatOrNull() ?: 1.0f
                mission.modifiers.playerSpeedMultiplier = speedMulti.text.toFloatOrNull() ?: 1.0f
                mission.modifiers.infiniteAmmo = infiniteAmmoAll.isChecked
                mission.modifiers.infiniteAmmoForWeapon = WeaponType.entries.find { it.displayName == infiniteAmmoSpecificSelectBox.selected }
                mission.modifiers.disableWeaponSwitching = disableSwitch.isChecked
                mission.modifiers.disableWeaponPickups = disablePickups.isChecked
                mission.modifiers.disableItemPickups = disableItemPickups.isChecked
                // Vehicle Modifiers
                mission.modifiers.makePlayerVehicleInvincible = invincibleVehicle.isChecked
                mission.modifiers.allCarsUnlocked = allCarsUnlocked.isChecked
                mission.modifiers.carSpeedMultiplier = carSpeedMulti.text.toFloatOrNull() ?: 1.0f
                // World Modifiers
                mission.modifiers.allHousesLocked = allHousesLocked.isChecked
                mission.modifiers.allHousesUnlocked = allHousesUnlocked.isChecked
                mission.modifiers.disableCarSpawners = disableCarSpawners.isChecked
                mission.modifiers.disableCharacterSpawners = disableCharacterSpawners.isChecked
                mission.modifiers.civiliansFleeOnSight = civiliansFlee.isChecked
                mission.modifiers.increasedEnemySpawns = increasedSpawns.isChecked
                mission.modifiers.freezeTimeAt = if (freezeTimeCheckbox.isChecked) timeSlider.value else null
                mission.modifiers.disableNoItemDrops = disableNoItemDrops.isChecked
                mission.modifiers.overrideEnemyBehavior = EnemyBehavior.entries.find { it.displayName == overrideEnemyBehaviorSelectBox.selected }
                mission.modifiers.overrideNpcBehavior = NPCBehavior.entries.find { it.displayName == overrideNpcBehaviorSelectBox.selected }
                mission.modifiers.playerHasOneHitKills = playerOneHitKills.isChecked
                mission.modifiers.enemiesHaveOneHitKills = enemyOneHitKills.isChecked

                if (overrideWeatherCheckbox.isChecked) {
                    mission.modifiers.overrideRainIntensity = intensityField.text.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
                    mission.modifiers.rainDuration = durationField.text.toFloatOrNull()
                    mission.modifiers.rainStartDelay = delayField.text.toFloatOrNull()
                } else {
                    mission.modifiers.overrideRainIntensity = null
                    mission.modifiers.rainDuration = null
                    mission.modifiers.rainStartDelay = null
                }

                mission.modifiers.stopAllSounds = stopAllSoundsCheckbox.isChecked
                mission.modifiers.disableAllAudioEmitters = disableAllEmittersCheckbox.isChecked

                mission.modifiers.stopSpecificSounds = stopSpecificSoundsArea.text
                    .split("\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toMutableList()

                mission.modifiers.disableEmittersWithSounds = disableEmittersArea.text
                    .split("\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toMutableList()

                uiManager.showTemporaryMessage("Modifiers updated for '${mission.title}'")
                dialog.hide()
            }
        })

        dialog.button(applyButton)
        dialog.button("Close")
        dialog.show(stage)
    }

    private fun showMissionFlowDialog() {
        val mission = currentMissionDef ?: return // Do nothing if no mission is selected

        val dialog = Dialog("Mission Flow: ${mission.title}", skin, "dialog")
        dialog.isMovable = true
        dialog.isResizable = true // Allow user to resize for complex missions

        // A Table is better than a VerticalGroup for this, as it handles alignment better.
        val flowContentTable = Table(skin)
        flowContentTable.align(Align.topLeft)
        flowContentTable.pad(10f)

        // Populate the table with the flow information
        populateMissionFlow(mission, flowContentTable)

        val scrollPane = ScrollPane(flowContentTable, skin)
        scrollPane.setFadeScrollBars(false)

        dialog.contentTable.add(scrollPane).grow().minSize(500f, 400f).maxSize(800f, 600f)
        dialog.button("Close")
        dialog.show(stage)
    }

    private fun createMissionRowWidget(mission: MissionDefinition, index: Int): Table {
        val rowTable = Table()
        rowTable.userObject = mission.id // Store the ID for easy retrieval
        rowTable.background = defaultMissionBackground // Use our new default background
        rowTable.pad(4f)

        // The mission title and ID
        val missionLabelText = "$index. ${mission.title} [#A0A0A0](${mission.id})[]"
        val missionLabel = Label(missionLabelText, skin, "small")
        missionLabel.style.font.data.markupEnabled = true
        missionLabel.color = Color.valueOf("#EAEAEA") // Brighter default text
        missionLabel.setWrap(true)
        missionLabel.setAlignment(Align.left)
        // The "C" (Copy) button
        val copyButton = TextButton("C", skin, "default")

        rowTable.add(missionLabel).growX().left()
        rowTable.add(copyButton).size(25f, 25f).padLeft(5f)

        // Handler for clicking the main row to select it for editing
        rowTable.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (copyButton.isOver) return // Let the copy button's listener handle it
                selectMissionRow(rowTable, mission.id)
            }
        })

        // Handler for the copy button
        copyButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                Gdx.app.clipboard.contents = mission.id
                uiManager.showTemporaryMessage("Copied ID: ${mission.id}")
                event?.stop() // Stop the event from propagating to the row's listener
            }
        })

        return rowTable
    }

    private fun selectMissionRow(rowTable: Table, missionId: String) {
        // Guard Clause: If this mission is already selected, do nothing
        if (currentMissionDef?.id == missionId) return

        uiManager.game.sceneManager.clearMissionPreviews()

        // Deselect the old row
        selectedMissionRow?.let {
            it.background = defaultMissionBackground
            (it.getChild(0) as? Label)?.color = Color.valueOf("#EAEAEA")
        }

        // Select the new row and highlight it
        rowTable.background = selectedMissionBackground
        (rowTable.getChild(0) as? Label)?.color = Color.valueOf("#F5F5DC")
        selectedMissionRow = rowTable

        // Update all relevant states
        currentMissionDef = missionSystem.getMissionDefinition(missionId)
        uiManager.selectedMissionForEditing = currentMissionDef
        uiManager.setPersistentMessage("MISSION EDITING: ${currentMissionDef?.title}")

        // Populate the editor with the selected mission's data
        populateEditor(missionId)

        // Synchronize the dropdown menu
        val items = missionSelectBox.items
        val indexToSelect = items.indexOfFirst { it.startsWith(missionId) }
        if (indexToSelect != -1) {
            missionSelectBox.selectedIndex = indexToSelect
        }
    }

    private fun populateMissionFlow(mission: MissionDefinition, container: Table) {
        container.clearChildren() // Clear any old content
        val indent = "   > "

        // --- 1. Prerequisites ---
        if (mission.prerequisites.isNotEmpty()) {
            container.add(Label("[YELLOW]UNLOCKED BY:", skin)).left().row()
            mission.prerequisites.forEach { prereqId ->
                val prereqMission = missionSystem.getMissionDefinition(prereqId)
                container.add(Label("$indent${prereqMission?.title ?: prereqId}", skin, "small")).left().row()
            }
        }

        // --- 2. Start Trigger ---
        container.add(Label("[LIME]START TRIGGER:", skin)).left().padTop(8f).row()
        val trigger = mission.startTrigger
        val triggerText = when (trigger.type) {
            TriggerType.ON_ENTER_AREA -> "Enter area at (${trigger.areaCenter.x.toInt()}, ${trigger.areaCenter.z.toInt()})"
            TriggerType.ON_TALK_TO_NPC -> "Talk to NPC with ID: ${trigger.targetNpcId}"
            TriggerType.ON_COLLECT_ITEM -> "Collect ${trigger.itemCount}x ${trigger.itemType?.displayName}"
            TriggerType.ON_ENTER_HOUSE -> "Enter House with ID: ${trigger.targetHouseId}"
            TriggerType.ON_HURT_ENEMY -> "Hurt Enemy with ID: ${trigger.targetNpcId}"
            TriggerType.ON_ENTER_CAR -> "Enter Car with ID: ${trigger.targetCarId}"
            TriggerType.ON_ALL_ENEMIES_ELIMINATED -> "All enemies in the scene are eliminated"
            TriggerType.ON_LEAVE_AREA -> "Leave area at (${trigger.areaCenter.x.toInt()}, ${trigger.areaCenter.z.toInt()})"
            TriggerType.ON_STAY_IN_AREA_FOR_TIME -> "Stay in area at (${trigger.areaCenter.x.toInt()}, ${trigger.areaCenter.z.toInt()}) for ${trigger.requiredTimeInArea}s"
            TriggerType.ON_DESTROY_CAR -> "Destroy Car with ID: ${trigger.targetCarId}"
            TriggerType.ON_DESTROY_OBJECT -> "Destroy Object with ID: ${trigger.targetNpcId}" // Reusing the NPC ID field
            TriggerType.ON_MONEY_BELOW_THRESHOLD -> "Player money drops below: $${trigger.moneyThreshold}"
            TriggerType.ON_MISSION_FAILED -> "Previous mission failed: ${trigger.targetNpcId}" // Reusing the NPC ID field for the mission ID
        }
        container.add(Label("$indent$triggerText", skin, "small")).left().row()

        // --- 3. Events on Start ---
        if (mission.eventsOnStart.isNotEmpty()) {
            container.add(Label("[CYAN]EVENTS ON START:", skin)).left().padTop(8f).row()
            mission.eventsOnStart.forEach { event ->
                container.add(Label("$indent${getEventDescription(event)}", skin, "small")).left().row()
            }
        }

        // --- 4. Objectives and Their Events ---
        mission.objectives.forEachIndexed { index, objective ->
            container.add(Label("[ORANGE]OBJECTIVE ${index + 1}: ${objective.description}", skin)).left().padTop(8f).row()
            if (objective.eventsOnStart.isNotEmpty()) {
                objective.eventsOnStart.forEach { event ->
                    container.add(Label("$indent[CYAN]Event: ${getEventDescription(event)}", skin, "small")).left().row()
                }
            }
        }

        // --- 5. Events on Complete ---
        if (mission.eventsOnComplete.isNotEmpty()) {
            container.add(Label("[CYAN]EVENTS ON COMPLETE:", skin)).left().padTop(8f).row()
            mission.eventsOnComplete.forEach { event ->
                container.add(Label("$indent${getEventDescription(event)}", skin, "small")).left().row()
            }
        }

        // --- 6. Rewards ---
        if (mission.rewards.isNotEmpty()) {
            container.add(Label("[GOLD]REWARDS:", skin)).left().padTop(8f).row()
            mission.rewards.forEach { reward ->
                val rewardText = when (reward.type) {
                    RewardType.GIVE_MONEY -> "Give Money: $${reward.amount}"
                    RewardType.SHOW_MESSAGE -> "Show Message: '${reward.message}'"
                    RewardType.GIVE_AMMO -> "Give Ammo: ${reward.amount} for ${reward.weaponType?.displayName}"
                    RewardType.GIVE_ITEM -> "Give Item: ${reward.amount}x ${reward.itemType?.displayName}"
                    else -> reward.type.name
                }
                container.add(Label("$indent$rewardText", skin, "small")).left().row()
            }
        }

        // --- 7. Unlocks ---
        val unlocksMissions = missionSystem.getAllMissionDefinitions().values
            .filter { it.prerequisites.contains(mission.id) }
        if (unlocksMissions.isNotEmpty()) {
            container.add(Label("[YELLOW]UNLOCKS:", skin)).left().padTop(8f).row()
            unlocksMissions.forEach { unlockedMission ->
                container.add(Label("$indent${unlockedMission.title}", skin, "small")).left().row()
            }
        }
    }

    private fun showEventDialog(existingEvent: GameEvent?, isStartEvent: Boolean, onSaveEvent: ((GameEvent) -> Unit)? = null) {
        val onSave: (GameEvent) -> Unit = { newEvent ->
            if (onSaveEvent != null) {
                onSaveEvent(newEvent)
            } else {
                // Otherwise, this is a main mission event (Start/Complete).
                val list = if (isStartEvent) tempStartEvents else tempCompleteEvents
                if (existingEvent == null) {
                    list.add(newEvent)
                } else {
                    val index = list.indexOf(existingEvent)
                    if (index != -1) list[index] = newEvent
                }
                refreshEventWidgets() // Refresh the main mission editor UI.
            }
        }

        val dialog = Dialog(if (existingEvent == null) "Add Event" else "Edit Event", skin, "dialog")
        val content = Table(skin)
        content.pad(10f).defaults().pad(5f).align(Align.left)

        val typeSelect = SelectBox<String>(skin).apply {
            items = GdxArray(GameEventType.entries.map { it.name }.toTypedArray())
            if (existingEvent != null) selected = existingEvent.type.name
        }

        // --- Common Fields ---
        val targetIdField = TextField(existingEvent?.targetId ?: "", skin).apply { messageText = "Unique ID (optional)" }
        val posXField = TextField(existingEvent?.spawnPosition?.x?.toString() ?: "0", skin)
        val posYField = TextField(existingEvent?.spawnPosition?.y?.toString() ?: "0", skin)
        val posZField = TextField(existingEvent?.spawnPosition?.z?.toString() ?: "0", skin)
        val keepAfterMissionCheckbox = CheckBox(" Keep After Mission Ends", skin).apply { isChecked = existingEvent?.keepAfterMission ?: false }

        // --- All Event-Specific Fields ---
        val enemyTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(EnemyType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.enemyType?.displayName }
        val enemyBehaviorSelectBox = SelectBox<String>(skin).apply { items = GdxArray(EnemyBehavior.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.enemyBehavior?.displayName }
        val enemyPathIdField = TextField(existingEvent?.assignedPathId ?: "", skin).apply { messageText = "Optional Path ID" }
        val enemyHealthSettingSelectBox = SelectBox<String>(skin).apply { items = GdxArray(HealthSetting.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.healthSetting?.displayName }
        val enemyCustomHealthField = TextField(existingEvent?.customHealthValue?.toString() ?: "100", skin)
        val enemyMinHealthField = TextField(existingEvent?.minRandomHealth?.toString() ?: "80", skin)
        val enemyMaxHealthField = TextField(existingEvent?.maxRandomHealth?.toString() ?: "120", skin)
        val enemyInitialWeaponSelectBox = SelectBox<String>(skin).apply { items = GdxArray(WeaponType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.initialWeapon?.displayName }
        val enemyAmmoModeSelectBox = SelectBox<String>(skin).apply { items = GdxArray(AmmoSpawnMode.entries.map { it.name }.toTypedArray()); selected = existingEvent?.ammoSpawnMode?.name }
        val enemySetAmmoField = TextField(existingEvent?.setAmmoValue?.toString() ?: "30", skin)
        val enemyWeaponCollectionPolicySelectBox = SelectBox<String>(skin).apply { items = GdxArray(WeaponCollectionPolicy.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.weaponCollectionPolicy?.displayName }
        val enemyCanCollectItemsCheckbox = CheckBox(" Can Collect Items", skin).apply { isChecked = existingEvent?.canCollectItems ?: true }
        val enemyInitialMoneyField = TextField(existingEvent?.enemyInitialMoney?.toString() ?: "0", skin)

        val npcTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(NPCType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.npcType?.displayName }
        val npcBehaviorSelectBox = SelectBox<String>(skin).apply { items = GdxArray(NPCBehavior.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.npcBehavior?.displayName }
        val npcRotationField = TextField(existingEvent?.npcRotation?.toString() ?: "0", skin)
        val npcPathFollowingStyleSelectBox = SelectBox<String>(skin).apply { items = GdxArray(PathFollowingStyle.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.pathFollowingStyle?.displayName }
        val carTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(CarType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.carType?.displayName }
        val carLockedCheckbox = CheckBox(" Start Locked", skin).apply { isChecked = existingEvent?.carIsLocked ?: false }
        val carDriverTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(arrayOf("None", "Enemy", "NPC")); selected = existingEvent?.carDriverType ?: "None" }
        val carEnemyDriverTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(EnemyType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.carEnemyDriverType?.displayName }
        val carNpcDriverTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(NPCType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.carNpcDriverType?.displayName }
        val itemTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(ItemType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.itemType?.displayName }
        val itemValueField = TextField(existingEvent?.itemValue?.toString() ?: "100", skin)
        val moneyValueField = TextField(existingEvent?.itemValue?.toString() ?: "100", skin)
        val houseTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(HouseType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.houseType?.displayName }
        val houseLockedCheckbox = CheckBox(" Start Locked", skin).apply { isChecked = existingEvent?.houseIsLocked ?: false }
        val houseRotationYField = TextField(existingEvent?.houseRotationY?.toString() ?: "0", skin)
        val objectTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(ObjectType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.objectType?.displayName }
        val lightColorRField = TextField(existingEvent?.lightColor?.r?.toString() ?: "1.0", skin)
        val lightColorGField = TextField(existingEvent?.lightColor?.g?.toString() ?: "1.0", skin)
        val lightColorBField = TextField(existingEvent?.lightColor?.b?.toString() ?: "1.0", skin)
        val lightIntensityField = TextField(existingEvent?.lightIntensity?.toString() ?: "50", skin)
        val lightRangeField = TextField(existingEvent?.lightRange?.toString() ?: "50", skin)
        val lightFlickerSelect = SelectBox<String>(skin).apply { items = GdxArray(FlickerMode.entries.map { it.name }.toTypedArray()); selected = existingEvent?.flickerMode?.name ?: FlickerMode.NONE.name }
        val loopOnDurationField = TextField(existingEvent?.loopOnDuration?.toString() ?: "0.1", skin)
        val loopOffDurationField = TextField(existingEvent?.loopOffDuration?.toString() ?: "0.2", skin)
        val blockTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(BlockType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.blockType?.displayName }
        val blockShapeSelect = SelectBox<String>(skin).apply { items = GdxArray(BlockShape.entries.map { it.getDisplayName() }.toTypedArray()); selected = existingEvent?.blockShape?.getDisplayName() }
        val blockRotationYField = TextField(existingEvent?.blockRotationY?.toString() ?: "0", skin)
        val blockTextureRotationYField = TextField(existingEvent?.blockTextureRotationY?.toString() ?: "0", skin)
        val blockTopTextureRotationYField = TextField(existingEvent?.blockTopTextureRotationY?.toString() ?: "0", skin)
        val blockCameraVisibilitySelect = SelectBox<String>(skin).apply { items = GdxArray(CameraVisibility.entries.map { it.getDisplayName() }.toTypedArray()); selected = existingEvent?.blockCameraVisibility?.getDisplayName() ?: CameraVisibility.ALWAYS_VISIBLE.getDisplayName() }
        val dialogIdSelect = SelectBox<String>(skin).apply { items = GdxArray(missionSystem.getAllDialogueIds().toTypedArray()); selected = existingEvent?.dialogId }
        val particleTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(ParticleEffectType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.particleEffectType?.displayName }
        val minParticlesField = TextField(existingEvent?.spawnerMinParticles?.toString() ?: "1", skin)
        val maxParticlesField = TextField(existingEvent?.spawnerMaxParticles?.toString() ?: "3", skin)
        val spawnerIntervalField = TextField(existingEvent?.spawnInterval?.toString() ?: "5.0", skin)
        val spawnerMinRangeField = TextField(existingEvent?.minSpawnRange?.toString() ?: "0", skin)
        val spawnerMaxRangeField = TextField(existingEvent?.maxSpawnRange?.toString() ?: "100", skin)
        val weaponTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(WeaponType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.weaponType?.displayName }
        val ammoAmountField = TextField(existingEvent?.ammoAmount?.toString() ?: "0", skin).apply { messageText = "Default" }
        val rainIntensityField = TextField(existingEvent?.rainIntensity?.toString() ?: "0.8", skin)
        val rainDurationField = TextField(existingEvent?.rainDuration?.toString() ?: "", skin).apply { messageText = "Infinite" }
        val emitterEnabledCheckbox = CheckBox(" Emitter Enabled", skin).apply { isChecked = existingEvent?.emitterIsEnabled ?: true }
        val emitterSoundIdArea = TextArea(existingEvent?.soundIds?.joinToString("\n") ?: "", skin)
        val emitterVolumeSlider = Slider(0f, 1f, 0.01f, false, skin).apply { value = existingEvent?.volume ?: 1.0f }
        val emitterRangeField = TextField(existingEvent?.range?.toString() ?: "100", skin)
        val emitterPlaybackModeSelect = SelectBox<String>(skin).apply { items = GdxArray(EmitterPlaybackMode.entries.map { it.name }.toTypedArray()); selected = existingEvent?.playbackMode?.name }
        val emitterPlaylistModeSelect = SelectBox<String>(skin).apply { items = GdxArray(EmitterPlaylistMode.entries.map { it.name }.toTypedArray()); selected = existingEvent?.playlistMode?.name }
        val emitterReactivationModeSelect = SelectBox<String>(skin).apply { items = GdxArray(EmitterReactivationMode.entries.map { it.name }.toTypedArray()); selected = existingEvent?.reactivationMode?.name }
        val emitterIntervalField = TextField(existingEvent?.interval?.toString() ?: "1.0", skin)
        val emitterTimedLoopDurationField = TextField(existingEvent?.timedLoopDuration?.toString() ?: "30", skin)
        val emitterMinPitchField = TextField(existingEvent?.minPitch?.toString() ?: "1.0", skin)
        val emitterMaxPitchField = TextField(existingEvent?.maxPitch?.toString() ?: "1.0", skin)

        // --- Layout Tables ---
        val targetIdTable = Table(skin).apply { add("Target/Spawn ID:"); add(targetIdField).growX() }
        val posTable = Table(skin).apply { add("Spawn Pos X:"); add(posXField).width(60f); add("Y:"); add(posYField).width(60f); add("Z:"); add(posZField).width(60f) }
        val enemyCustomHpRow = Table(skin).apply { add("Custom HP:"); add(enemyCustomHealthField).width(80f) }
        val enemyRandomTbl = Table(skin).apply { add("Min:"); add(enemyMinHealthField).width(60f); add("Max:").padLeft(10f); add(enemyMaxHealthField).width(60f) }
        val enemySettingsTable = Table(skin).apply {
            add("Enemy Type:"); add(enemyTypeSelect).growX().row()
            add("Behavior:"); add(enemyBehaviorSelectBox).growX().row()
            add("Path ID:"); add(enemyPathIdField).growX().row()
            add(Label("--- Health ---", skin, "title")).colspan(2).center().padTop(8f).row()
            add("Setting:"); add(enemyHealthSettingSelectBox).growX().row()
            add(enemyCustomHpRow).colspan(2).left().row()
            add(enemyRandomTbl).colspan(2).left().row()
            add(Label("--- Equipment ---", skin, "title")).colspan(2).center().padTop(8f).row()
            add("Initial Weapon:"); add(enemyInitialWeaponSelectBox).growX().row()
            val ammoRow = Table(skin); ammoRow.add("Ammo Mode:"); ammoRow.add(enemyAmmoModeSelectBox); ammoRow.add("Set Ammo:").padLeft(10f); ammoRow.add(enemySetAmmoField).width(80f); add(ammoRow).colspan(2).left().row()
            add("Pickup Policy:"); add(enemyWeaponCollectionPolicySelectBox).growX().row()
            add(enemyCanCollectItemsCheckbox).colspan(2).left().row()
            add(Label("Initial Money:", skin)).left(); add(enemyInitialMoneyField).width(80f).left().row()
        }
        val npcSettingsTable = Table(skin).apply {
            add("NPC Type:"); add(npcTypeSelect).growX().row()
            add("Behavior:"); add(npcBehaviorSelectBox).growX().row()
            add("Rotation:"); add(npcRotationField).width(80f).row()
            add("Path Style:"); add(npcPathFollowingStyleSelectBox).growX().row()
        }
        val carEnemyDriverRow = Table(skin).apply { add("Enemy Driver:"); add(carEnemyDriverTypeSelect).growX() }
        val carNpcDriverRow = Table(skin).apply { add("NPC Driver:"); add(carNpcDriverTypeSelect).growX() }
        val carSettingsTable = Table(skin).apply {
            add("Car Type:"); add(carTypeSelect).growX().row()
            add(carLockedCheckbox).colspan(2).left().row()
            add("Driver Type:"); add(carDriverTypeSelect).growX().row()
            add(carEnemyDriverRow).colspan(2).growX().row()
            add(carNpcDriverRow).colspan(2).growX().row()
        }
        val itemValueRow = Table(skin).apply { add("Value ($):"); add(itemValueField).width(100f) }
        val itemSettingsTable = Table(skin).apply {
            add("Item Type:"); add(itemTypeSelect).growX().row()
            add(itemValueRow).colspan(2).left().row()
        }
        val moneySettingsTable = Table(skin).apply { add("Amount:"); add(moneyValueField).width(100f) }
        val houseSettingsTable = Table(skin).apply {
            add("House Type:"); add(houseTypeSelect).growX().row()
            add("Rotation Y:"); add(houseRotationYField).width(80f).row()
            add(houseLockedCheckbox).colspan(2).left().row()
        }
        val lightSettingsTable = Table(skin).apply {
            val intensityRangeRow = Table(skin); intensityRangeRow.add("Intensity:"); intensityRangeRow.add(lightIntensityField).width(60f); intensityRangeRow.add("Range:").padLeft(10f); intensityRangeRow.add(lightRangeField).width(60f); add(intensityRangeRow).colspan(4).left().row()
            val colorRow = Table(skin); colorRow.add("Color R:"); colorRow.add(lightColorRField).width(50f); colorRow.add("G:").padLeft(10f); colorRow.add(lightColorGField).width(50f); colorRow.add("B:").padLeft(10f); colorRow.add(lightColorBField).width(50f); add(colorRow).colspan(4).left().row()
            val flickerRow = Table(skin); flickerRow.add("Flicker:"); flickerRow.add(lightFlickerSelect); add(flickerRow).colspan(4).left().row()
            val loopRow = Table(skin); loopRow.add("On (s):"); loopRow.add(loopOnDurationField).width(60f); loopRow.add("Off (s):").padLeft(10f); loopRow.add(loopOffDurationField).width(60f); add(loopRow).colspan(4).left().row()
        }
        val objectSettingsTable = Table(skin).apply {
            add("Object Type:"); add(objectTypeSelect).growX().row()
            add(lightSettingsTable).colspan(2).left().row()
        }
        val blockSettingsTable = Table(skin).apply {
            add("Block Type:"); add(blockTypeSelect).growX().row()
            add("Shape:"); add(blockShapeSelect).growX().row()
            add("Geo Rotation:"); add(blockRotationYField).width(80f).row()
            add("Tex Rotation:"); add(blockTextureRotationYField).width(80f).row()
            add("Top Tex Rot:"); add(blockTopTextureRotationYField).width(80f).row()
            add("Visibility:"); add(blockCameraVisibilitySelect).growX().row()
        }
        val dialogSettingsTable = Table(skin).apply { add("Dialog ID:"); add(dialogIdSelect).growX() }
        val weaponSettingsTable = Table(skin).apply {
            add("Weapon:"); add(weaponTypeSelect).growX().row()
            add("Ammo Amount:"); add(ammoAmountField).width(80f).left().row()
        }
        val weatherSettingsTable = Table(skin).apply {
            add("Rain Intensity (0-1):"); add(rainIntensityField).width(80f).row()
            add("Duration (s):"); add(rainDurationField).width(80f).row()
        }

        // --- ENEMY DIALOG UI (created once) ---
        val enemyInteractionTable = Table(skin).apply { add(Label("--- Standalone Interaction ---", skin, "title")).colspan(2).center().padTop(10f).row() }
        val enemyDialogIds = GdxArray<String>().apply { add("(None)"); addAll(*missionSystem.getAllDialogueIds().toTypedArray()) }
        val enemyDialogIdSelectBox = SelectBox<String>(skin).apply { items = enemyDialogIds; selected = existingEvent?.standaloneDialog?.dialogId ?: "(None)" }
        enemyInteractionTable.add(Label("Dialog ID:", skin)).padRight(10f); enemyInteractionTable.add(enemyDialogIdSelectBox).growX().row()
        val enemyOutcomeTypeSelectBox = SelectBox<String>(skin).apply { items = GdxArray(DialogOutcomeType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.standaloneDialog?.outcome?.type?.displayName ?: DialogOutcomeType.NONE.displayName }
        enemyInteractionTable.add(Label("Outcome:", skin)).padRight(10f).padTop(8f); enemyInteractionTable.add(enemyOutcomeTypeSelectBox).growX().row()
        val enemyItemTypeNames = GdxArray(ItemType.entries.map { it.displayName }.toTypedArray())
        val enemyGiveItemTable = Table(); val enemySellItemTable = Table(); val enemyTradeItemTable = Table(); val enemyBuyItemTable = Table()
        val enemyGiveItemSelectBox = SelectBox<String>(skin).apply { items = enemyItemTypeNames; selected = existingEvent?.standaloneDialog?.outcome?.itemToGive?.displayName }; val enemyGiveAmmoField = TextField(existingEvent?.standaloneDialog?.outcome?.ammoToGive?.toString() ?: "", skin).apply { messageText = "Default" }
        enemyGiveItemTable.add(Label("Item to Give:", skin)).padRight(10f); enemyGiveItemTable.add(enemyGiveItemSelectBox).row(); enemyGiveItemTable.add(Label("Ammo:", skin)).padRight(10f); enemyGiveItemTable.add(enemyGiveAmmoField).width(80f).row()
        val enemySellItemSelectBox = SelectBox<String>(skin).apply { items = enemyItemTypeNames; selected = existingEvent?.standaloneDialog?.outcome?.itemToGive?.displayName }; val enemySellAmmoField = TextField(existingEvent?.standaloneDialog?.outcome?.ammoToGive?.toString() ?: "", skin).apply { messageText = "Default" }; val enemySellPriceField = TextField(existingEvent?.standaloneDialog?.outcome?.price?.toString() ?: "", skin).apply { messageText = "e.g., 100" }
        enemySellItemTable.add(Label("Item to Sell:", skin)).padRight(10f); enemySellItemTable.add(enemySellItemSelectBox).row(); enemySellItemTable.add(Label("Ammo:", skin)).padRight(10f); enemySellItemTable.add(enemySellAmmoField).width(80f).row(); enemySellItemTable.add(Label("Price:", skin)).padRight(10f); enemySellItemTable.add(enemySellPriceField).width(80f).row()
        val enemyTradeRequiredItemSelectBox = SelectBox<String>(skin).apply { items = enemyItemTypeNames; selected = existingEvent?.standaloneDialog?.outcome?.requiredItem?.displayName }; val enemyTradeRewardItemSelectBox = SelectBox<String>(skin).apply { items = enemyItemTypeNames; selected = existingEvent?.standaloneDialog?.outcome?.itemToGive?.displayName }; val enemyTradeRewardAmmoField = TextField(existingEvent?.standaloneDialog?.outcome?.ammoToGive?.toString() ?: "", skin).apply { messageText = "Default" }
        enemyTradeItemTable.add(Label("Player Gives:", skin)).padRight(10f); enemyTradeItemTable.add(enemyTradeRequiredItemSelectBox).row(); enemyTradeItemTable.add(Label("Player Gets:", skin)).padRight(10f); enemyTradeItemTable.add(enemyTradeRewardItemSelectBox).row(); enemyTradeItemTable.add(Label("Reward Ammo:", skin)).padRight(10f); enemyTradeItemTable.add(enemyTradeRewardAmmoField).width(80f).row()
        val enemyBuyItemSelectBox = SelectBox<String>(skin).apply { items = enemyItemTypeNames; selected = existingEvent?.standaloneDialog?.outcome?.requiredItem?.displayName }; val enemyBuyPriceField = TextField(existingEvent?.standaloneDialog?.outcome?.price?.toString() ?: "", skin).apply { messageText = "e.g., 50" }
        enemyBuyItemTable.add(Label("Item to Buy:", skin)).padRight(10f); enemyBuyItemTable.add(enemyBuyItemSelectBox).row(); enemyBuyItemTable.add(Label("Price:", skin)).padRight(10f); enemyBuyItemTable.add(enemyBuyPriceField).width(80f).row()
        val enemyOutcomeSettingsStack = Stack(enemyGiveItemTable, enemySellItemTable, enemyTradeItemTable, enemyBuyItemTable, Table())
        enemyInteractionTable.add(enemyOutcomeSettingsStack).colspan(2).padTop(10f).row()
        enemySettingsTable.add(enemyInteractionTable).colspan(2).padTop(10f).row()

        // --- NPC DIALOG UI (created separately) ---
        val npcInteractionTable = Table(skin).apply { add(Label("--- Standalone Interaction ---", skin, "title")).colspan(2).center().padTop(10f).row() }
        val npcDialogIds = GdxArray<String>().apply { add("(None)"); addAll(*missionSystem.getAllDialogueIds().toTypedArray()) }
        val npcDialogIdSelectBox = SelectBox<String>(skin).apply { items = npcDialogIds; selected = existingEvent?.standaloneDialog?.dialogId ?: "(None)" }
        npcInteractionTable.add(Label("Dialog ID:", skin)).padRight(10f); npcInteractionTable.add(npcDialogIdSelectBox).growX().row()
        val npcOutcomeTypeSelectBox = SelectBox<String>(skin).apply { items = GdxArray(DialogOutcomeType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.standaloneDialog?.outcome?.type?.displayName ?: DialogOutcomeType.NONE.displayName }
        npcInteractionTable.add(Label("Outcome:", skin)).padRight(10f).padTop(8f); npcInteractionTable.add(npcOutcomeTypeSelectBox).growX().row()
        val npcItemTypeNames = GdxArray(ItemType.entries.map { it.displayName }.toTypedArray())
        val npcGiveItemTable = Table(); val npcSellItemTable = Table(); val npcTradeItemTable = Table(); val npcBuyItemTable = Table()
        val npcGiveItemSelectBox = SelectBox<String>(skin).apply { items = npcItemTypeNames; selected = existingEvent?.standaloneDialog?.outcome?.itemToGive?.displayName }; val npcGiveAmmoField = TextField(existingEvent?.standaloneDialog?.outcome?.ammoToGive?.toString() ?: "", skin).apply { messageText = "Default" }
        npcGiveItemTable.add(Label("Item to Give:", skin)).padRight(10f); npcGiveItemTable.add(npcGiveItemSelectBox).row(); npcGiveItemTable.add(Label("Ammo:", skin)).padRight(10f); npcGiveItemTable.add(npcGiveAmmoField).width(80f).row()
        val npcSellItemSelectBox = SelectBox<String>(skin).apply { items = npcItemTypeNames; selected = existingEvent?.standaloneDialog?.outcome?.itemToGive?.displayName }; val npcSellAmmoField = TextField(existingEvent?.standaloneDialog?.outcome?.ammoToGive?.toString() ?: "", skin).apply { messageText = "Default" }; val npcSellPriceField = TextField(existingEvent?.standaloneDialog?.outcome?.price?.toString() ?: "", skin).apply { messageText = "e.g., 100" }
        npcSellItemTable.add(Label("Item to Sell:", skin)).padRight(10f); npcSellItemTable.add(npcSellItemSelectBox).row(); npcSellItemTable.add(Label("Ammo:", skin)).padRight(10f); npcSellItemTable.add(npcSellAmmoField).width(80f).row(); npcSellItemTable.add(Label("Price:", skin)).padRight(10f); npcSellItemTable.add(npcSellPriceField).width(80f).row()
        val npcTradeRequiredItemSelectBox = SelectBox<String>(skin).apply { items = npcItemTypeNames; selected = existingEvent?.standaloneDialog?.outcome?.requiredItem?.displayName }; val npcTradeRewardItemSelectBox = SelectBox<String>(skin).apply { items = npcItemTypeNames; selected = existingEvent?.standaloneDialog?.outcome?.itemToGive?.displayName }; val npcTradeRewardAmmoField = TextField(existingEvent?.standaloneDialog?.outcome?.ammoToGive?.toString() ?: "", skin).apply { messageText = "Default" }
        npcTradeItemTable.add(Label("Player Gives:", skin)).padRight(10f); npcTradeItemTable.add(npcTradeRequiredItemSelectBox).row(); npcTradeItemTable.add(Label("Player Gets:", skin)).padRight(10f); npcTradeItemTable.add(npcTradeRewardItemSelectBox).row(); npcTradeItemTable.add(Label("Reward Ammo:", skin)).padRight(10f); npcTradeItemTable.add(npcTradeRewardAmmoField).width(80f).row()
        val npcBuyItemSelectBox = SelectBox<String>(skin).apply { items = npcItemTypeNames; selected = existingEvent?.standaloneDialog?.outcome?.requiredItem?.displayName }; val npcBuyPriceField = TextField(existingEvent?.standaloneDialog?.outcome?.price?.toString() ?: "", skin).apply { messageText = "e.g., 50" }
        npcBuyItemTable.add(Label("Item to Buy:", skin)).padRight(10f); npcBuyItemTable.add(npcBuyItemSelectBox).row(); npcBuyItemTable.add(Label("Price:", skin)).padRight(10f); npcBuyItemTable.add(npcBuyPriceField).width(80f).row()
        val npcOutcomeSettingsStack = Stack(npcGiveItemTable, npcSellItemTable, npcTradeItemTable, npcBuyItemTable, Table())
        npcInteractionTable.add(npcOutcomeSettingsStack).colspan(2).padTop(10f).row()
        npcSettingsTable.add(npcInteractionTable).colspan(2).padTop(10f).row()


        val spawnerSettingsTable = Table(skin)
        spawnerSettingsTable.add(Label("--- General Spawner Settings ---", skin, "title")).colspan(2).center().padBottom(5f).row()
        spawnerSettingsTable.add(Label("Spawn Interval (s):", skin)).left()
        spawnerSettingsTable.add(spawnerIntervalField).width(80f).left().row()
        val rangeTable = Table(skin)
        rangeTable.add(Label("Range (Min/Max):", skin)).left()
        rangeTable.add(spawnerMinRangeField).width(80f).padLeft(5f)
        rangeTable.add(spawnerMaxRangeField).width(80f).padLeft(5f)
        spawnerSettingsTable.add(rangeTable).colspan(2).left().row()
        spawnerSettingsTable.add(Label("--- Particle Settings ---", skin, "title")).colspan(2).center().padTop(10f).row()
        spawnerSettingsTable.add(Label("Particle Effect:", skin)).left().row()
        spawnerSettingsTable.add(particleTypeSelect).growX().row()
        val particleCountTable = Table(skin)
        particleCountTable.add(Label("Min:", skin)).padRight(5f); particleCountTable.add(minParticlesField).width(60f).padRight(10f)
        particleCountTable.add(Label("Max:", skin)).padRight(5f); particleCountTable.add(maxParticlesField).width(60f)
        spawnerSettingsTable.add(particleCountTable).left().padTop(5f).row()

        val audioEmitterSettingsTable = Table(skin)
        audioEmitterSettingsTable.add(Label("--- Modify Audio Emitter ---", skin, "title")).colspan(2).center().padBottom(5f).row()
        audioEmitterSettingsTable.add(emitterEnabledCheckbox).colspan(2).left().row()
        audioEmitterSettingsTable.add(Label("Sound IDs (one per line):", skin)).colspan(2).left().row()
        audioEmitterSettingsTable.add(ScrollPane(emitterSoundIdArea, skin)).growX().height(60f).colspan(2).row()
        audioEmitterSettingsTable.add("Volume:").left(); audioEmitterSettingsTable.add(emitterVolumeSlider).growX().row()
        audioEmitterSettingsTable.add("Range:").left(); audioEmitterSettingsTable.add(emitterRangeField).width(100f).row()
        audioEmitterSettingsTable.add("Playback Mode:").left(); audioEmitterSettingsTable.add(emitterPlaybackModeSelect).row()
        audioEmitterSettingsTable.add("Playlist Mode:").left(); audioEmitterSettingsTable.add(emitterPlaylistModeSelect).row()
        audioEmitterSettingsTable.add("Reactivation:").left(); audioEmitterSettingsTable.add(emitterReactivationModeSelect).row()
        audioEmitterSettingsTable.add("Interval/Delay:").left(); audioEmitterSettingsTable.add(emitterIntervalField).width(100f).row()
        audioEmitterSettingsTable.add("Timed Loop (s):").left(); audioEmitterSettingsTable.add(emitterTimedLoopDurationField).width(100f).row()
        val emitterPitchTable = Table(); emitterPitchTable.add(Label("Pitch (Min/Max):", skin)); emitterPitchTable.add(emitterMinPitchField).width(80f); emitterPitchTable.add(emitterMaxPitchField).width(80f); audioEmitterSettingsTable.add(emitterPitchTable).colspan(2).left().row()


        val settingsStack = Stack(enemySettingsTable, npcSettingsTable, carSettingsTable, itemSettingsTable, moneySettingsTable, houseSettingsTable, objectSettingsTable, blockSettingsTable, dialogSettingsTable, weaponSettingsTable, weatherSettingsTable, spawnerSettingsTable, audioEmitterSettingsTable)

        content.add("Event Type:"); content.add(typeSelect).row()
        content.add(targetIdTable).colspan(2).growX().row()
        content.add(posTable).colspan(2).growX().row()
        content.add(keepAfterMissionCheckbox).colspan(2).left().padTop(5f).row()
        content.add(settingsStack).colspan(2).growX().row()

        fun updateVisibleFields() {
            val type = GameEventType.valueOf(typeSelect.selected)
            val isSpawn = type.name.startsWith("SPAWN")

            keepAfterMissionCheckbox.isVisible = isSpawn

            posTable.isVisible = isSpawn || type == GameEventType.DESPAWN_BLOCK_AT_POS
            targetIdTable.isVisible = type in listOf(GameEventType.DESPAWN_ENTITY, GameEventType.ENABLE_SPAWNER, GameEventType.DISABLE_SPAWNER, GameEventType.LOCK_HOUSE, GameEventType.UNLOCK_HOUSE, GameEventType.SPAWN_ENEMY, GameEventType.SPAWN_NPC)
            enemySettingsTable.isVisible = type == GameEventType.SPAWN_ENEMY
            npcSettingsTable.isVisible = type == GameEventType.SPAWN_NPC
            carSettingsTable.isVisible = type == GameEventType.SPAWN_CAR
            itemSettingsTable.isVisible = type == GameEventType.SPAWN_ITEM
            moneySettingsTable.isVisible = type == GameEventType.SPAWN_MONEY_STACK
            houseSettingsTable.isVisible = type == GameEventType.SPAWN_HOUSE
            objectSettingsTable.isVisible = type == GameEventType.SPAWN_OBJECT
            blockSettingsTable.isVisible = type == GameEventType.SPAWN_BLOCK
            dialogSettingsTable.isVisible = type == GameEventType.START_DIALOG
            weaponSettingsTable.isVisible = type in listOf(GameEventType.GIVE_WEAPON, GameEventType.FORCE_EQUIP_WEAPON)
            weatherSettingsTable.isVisible = type == GameEventType.SET_WEATHER
            spawnerSettingsTable.isVisible = type == GameEventType.SPAWN_SPAWNER

            // Dynamic visibility within panels
            lightSettingsTable.isVisible = ObjectType.entries.find { it.displayName == objectTypeSelect.selected } == ObjectType.LIGHT_SOURCE
            val enemyHealthSetting = HealthSetting.entries.find { it.displayName == enemyHealthSettingSelectBox.selected }
            enemyCustomHpRow.isVisible = enemyHealthSetting == HealthSetting.FIXED_CUSTOM
            enemyRandomTbl.isVisible = enemyHealthSetting == HealthSetting.RANDOM_RANGE
            carEnemyDriverRow.isVisible = carDriverTypeSelect.selected == "Enemy"
            carNpcDriverRow.isVisible = carDriverTypeSelect.selected == "NPC"
            itemValueRow.isVisible = ItemType.entries.find { it.displayName == itemTypeSelect.selected } == ItemType.MONEY_STACK

            // Visibility for dialog panels
            enemyInteractionTable.isVisible = type == GameEventType.SPAWN_ENEMY
            npcInteractionTable.isVisible = type == GameEventType.SPAWN_NPC
            val enemyOutcomeType = DialogOutcomeType.entries.find { it.displayName == enemyOutcomeTypeSelectBox.selected }
            enemyGiveItemTable.isVisible = enemyOutcomeType == DialogOutcomeType.GIVE_ITEM
            enemySellItemTable.isVisible = enemyOutcomeType == DialogOutcomeType.SELL_ITEM_TO_PLAYER
            enemyTradeItemTable.isVisible = enemyOutcomeType == DialogOutcomeType.TRADE_ITEM
            enemyBuyItemTable.isVisible = enemyOutcomeType == DialogOutcomeType.BUY_ITEM_FROM_PLAYER
            val npcOutcomeType = DialogOutcomeType.entries.find { it.displayName == npcOutcomeTypeSelectBox.selected }
            npcGiveItemTable.isVisible = npcOutcomeType == DialogOutcomeType.GIVE_ITEM
            npcSellItemTable.isVisible = npcOutcomeType == DialogOutcomeType.SELL_ITEM_TO_PLAYER
            npcTradeItemTable.isVisible = npcOutcomeType == DialogOutcomeType.TRADE_ITEM
            npcBuyItemTable.isVisible = npcOutcomeType == DialogOutcomeType.BUY_ITEM_FROM_PLAYER
            audioEmitterSettingsTable.isVisible = type == GameEventType.MODIFY_AUDIO_EMITTER || type == GameEventType.SPAWN_AUDIO_EMITTER
            emitterEnabledCheckbox.isVisible = type == GameEventType.MODIFY_AUDIO_EMITTER

            dialog.pack()
        }

        typeSelect.addListener(object: ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = updateVisibleFields() })
        objectTypeSelect.addListener(object: ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = updateVisibleFields() })
        enemyHealthSettingSelectBox.addListener(object: ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = updateVisibleFields() })
        carDriverTypeSelect.addListener(object: ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = updateVisibleFields() })
        itemTypeSelect.addListener(object: ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = updateVisibleFields() })
        enemyOutcomeTypeSelectBox.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = updateVisibleFields() })
        npcOutcomeTypeSelectBox.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = updateVisibleFields() })


        dialog.button("Save").addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val eventType = GameEventType.valueOf(typeSelect.selected)
                val baseEvent = GameEvent(
                    type = eventType,
                    keepAfterMission = keepAfterMissionCheckbox.isChecked,
                    targetId = targetIdField.text.ifBlank { null },
                    spawnPosition = Vector3(posXField.text.toFloatOrNull() ?: 0f, posYField.text.toFloatOrNull() ?: 0f, posZField.text.toFloatOrNull() ?: 0f)
                )

                var dialogInfo: StandaloneDialog? = null
                if (eventType == GameEventType.SPAWN_ENEMY || eventType == GameEventType.SPAWN_NPC) {
                    val dialogId = if(eventType == GameEventType.SPAWN_ENEMY) enemyDialogIdSelectBox.selected else npcDialogIdSelectBox.selected
                    if (dialogId != null && dialogId != "(None)") {
                        val outcomeTypeStr = if (eventType == GameEventType.SPAWN_ENEMY) enemyOutcomeTypeSelectBox.selected else npcOutcomeTypeSelectBox.selected
                        val outcomeType = DialogOutcomeType.entries.find { it.displayName == outcomeTypeStr } ?: DialogOutcomeType.NONE
                        val outcome = when (outcomeType) {
                            DialogOutcomeType.GIVE_ITEM -> DialogOutcome(type = outcomeType, itemToGive = ItemType.entries.find { it.displayName == (if(eventType == GameEventType.SPAWN_ENEMY) enemyGiveItemSelectBox.selected else npcGiveItemSelectBox.selected) }, ammoToGive = (if(eventType == GameEventType.SPAWN_ENEMY) enemyGiveAmmoField.text.toIntOrNull() else npcGiveAmmoField.text.toIntOrNull()))
                            DialogOutcomeType.SELL_ITEM_TO_PLAYER -> DialogOutcome(type = outcomeType, itemToGive = ItemType.entries.find { it.displayName == (if(eventType == GameEventType.SPAWN_ENEMY) enemySellItemSelectBox.selected else npcSellItemSelectBox.selected) }, ammoToGive = (if(eventType == GameEventType.SPAWN_ENEMY) enemySellAmmoField.text.toIntOrNull() else npcSellAmmoField.text.toIntOrNull()), price = (if(eventType == GameEventType.SPAWN_ENEMY) enemySellPriceField.text.toIntOrNull() else npcSellPriceField.text.toIntOrNull()))
                            DialogOutcomeType.BUY_ITEM_FROM_PLAYER -> DialogOutcome(type = outcomeType, requiredItem = ItemType.entries.find { it.displayName == (if(eventType == GameEventType.SPAWN_ENEMY) enemyBuyItemSelectBox.selected else npcBuyItemSelectBox.selected) }, price = (if(eventType == GameEventType.SPAWN_ENEMY) enemyBuyPriceField.text.toIntOrNull() else npcBuyPriceField.text.toIntOrNull()))
                            DialogOutcomeType.TRADE_ITEM -> DialogOutcome(type = outcomeType, requiredItem = ItemType.entries.find { it.displayName == (if(eventType == GameEventType.SPAWN_ENEMY) enemyTradeRequiredItemSelectBox.selected else npcTradeRequiredItemSelectBox.selected) }, itemToGive = ItemType.entries.find { it.displayName == (if(eventType == GameEventType.SPAWN_ENEMY) enemyTradeRewardItemSelectBox.selected else npcTradeRewardItemSelectBox.selected) }, ammoToGive = (if(eventType == GameEventType.SPAWN_ENEMY) enemyTradeRewardAmmoField.text.toIntOrNull() else npcTradeRewardAmmoField.text.toIntOrNull()))
                            else -> DialogOutcome(type = DialogOutcomeType.NONE)
                        }
                        dialogInfo = StandaloneDialog(dialogId, outcome)
                    }
                }

                val finalEvent = when (eventType) {
                    GameEventType.SPAWN_ENEMY -> baseEvent.copy(
                        enemyType = EnemyType.entries.find { it.displayName == enemyTypeSelect.selected },
                        enemyBehavior = EnemyBehavior.entries.find { it.displayName == enemyBehaviorSelectBox.selected },
                        assignedPathId = enemyPathIdField.text.ifBlank { null },
                        healthSetting = HealthSetting.entries.find { it.displayName == enemyHealthSettingSelectBox.selected },
                        customHealthValue = enemyCustomHealthField.text.toFloatOrNull(),
                        minRandomHealth = enemyMinHealthField.text.toFloatOrNull(),
                        maxRandomHealth = enemyMaxHealthField.text.toFloatOrNull(),
                        initialWeapon = WeaponType.entries.find { it.displayName == enemyInitialWeaponSelectBox.selected },
                        ammoSpawnMode = AmmoSpawnMode.valueOf(enemyAmmoModeSelectBox.selected),
                        setAmmoValue = enemySetAmmoField.text.toIntOrNull(),
                        weaponCollectionPolicy = WeaponCollectionPolicy.entries.find { it.displayName == enemyWeaponCollectionPolicySelectBox.selected },
                        canCollectItems = enemyCanCollectItemsCheckbox.isChecked,
                        enemyInitialMoney = enemyInitialMoneyField.text.toIntOrNull(),
                        standaloneDialog = dialogInfo
                    )
                    GameEventType.SPAWN_NPC -> baseEvent.copy(
                        npcType = NPCType.entries.find { it.displayName == npcTypeSelect.selected },
                        npcBehavior = NPCBehavior.entries.find { it.displayName == npcBehaviorSelectBox.selected },
                        npcRotation = npcRotationField.text.toFloatOrNull() ?: 0f,
                        pathFollowingStyle = PathFollowingStyle.entries.find { it.displayName == npcPathFollowingStyleSelectBox.selected },
                        standaloneDialog = dialogInfo
                    )
                    GameEventType.SPAWN_CAR -> baseEvent.copy(
                        carType = CarType.entries.find { it.displayName == carTypeSelect.selected },
                        carIsLocked = carLockedCheckbox.isChecked,
                        carDriverType = carDriverTypeSelect.selected,
                        carEnemyDriverType = EnemyType.entries.find { it.displayName == carEnemyDriverTypeSelect.selected },
                        carNpcDriverType = NPCType.entries.find { it.displayName == carNpcDriverTypeSelect.selected }
                    )
                    GameEventType.SPAWN_ITEM -> baseEvent.copy(
                        itemType = ItemType.entries.find { it.displayName == itemTypeSelect.selected },
                        itemValue = if (ItemType.entries.find { it.displayName == itemTypeSelect.selected } == ItemType.MONEY_STACK) itemValueField.text.toIntOrNull() ?: 100 else 0
                    )
                    GameEventType.SPAWN_MONEY_STACK -> baseEvent.copy(
                        itemType = ItemType.MONEY_STACK,
                        itemValue = moneyValueField.text.toIntOrNull() ?: 100
                    )
                    GameEventType.SPAWN_HOUSE -> baseEvent.copy(
                        houseType = HouseType.entries.find { it.displayName == houseTypeSelect.selected },
                        houseRotationY = houseRotationYField.text.toFloatOrNull(),
                        houseIsLocked = houseLockedCheckbox.isChecked
                    )
                    GameEventType.SPAWN_OBJECT -> baseEvent.copy(
                        objectType = ObjectType.entries.find { it.displayName == objectTypeSelect.selected },
                        lightColor = if (ObjectType.entries.find { it.displayName == objectTypeSelect.selected } == ObjectType.LIGHT_SOURCE) Color(lightColorRField.text.toFloatOrNull() ?: 1f, lightColorGField.text.toFloatOrNull() ?: 1f, lightColorBField.text.toFloatOrNull() ?: 1f, 1f) else null,
                        lightIntensity = lightIntensityField.text.toFloatOrNull(),
                        lightRange = lightRangeField.text.toFloatOrNull(),
                        flickerMode = if (ObjectType.entries.find { it.displayName == objectTypeSelect.selected } == ObjectType.LIGHT_SOURCE) FlickerMode.valueOf(lightFlickerSelect.selected) else null,
                        loopOnDuration = loopOnDurationField.text.toFloatOrNull(),
                        loopOffDuration = loopOffDurationField.text.toFloatOrNull()
                    )
                    GameEventType.SPAWN_BLOCK -> baseEvent.copy(
                        blockType = BlockType.entries.find { it.displayName == blockTypeSelect.selected },
                        blockShape = BlockShape.entries.find { it.getDisplayName() == blockShapeSelect.selected },
                        blockRotationY = blockRotationYField.text.toFloatOrNull(),
                        blockTextureRotationY = blockTextureRotationYField.text.toFloatOrNull(),
                        blockTopTextureRotationY = blockTopTextureRotationYField.text.toFloatOrNull(),
                        blockCameraVisibility = CameraVisibility.entries.find { it.getDisplayName() == blockCameraVisibilitySelect.selected }
                    )
                    GameEventType.START_DIALOG -> baseEvent.copy(dialogId = dialogIdSelect.selected)
                    GameEventType.GIVE_WEAPON, GameEventType.FORCE_EQUIP_WEAPON -> baseEvent.copy(
                        weaponType = WeaponType.entries.find { it.displayName == weaponTypeSelect.selected },
                        ammoAmount = ammoAmountField.text.toIntOrNull()
                    )
                    GameEventType.SPAWN_SPAWNER -> baseEvent.copy(
                        spawnerType = SpawnerType.PARTICLE, // This dialog is still just for particles
                        particleEffectType = ParticleEffectType.entries.find { it.displayName == particleTypeSelect.selected },
                        spawnerMinParticles = minParticlesField.text.toIntOrNull() ?: 1,
                        spawnerMaxParticles = maxParticlesField.text.toIntOrNull() ?: 3,
                        spawnInterval = spawnerIntervalField.text.toFloatOrNull() ?: 5.0f,
                        minSpawnRange = spawnerMinRangeField.text.toFloatOrNull() ?: 0f,
                        maxSpawnRange = spawnerMaxRangeField.text.toFloatOrNull() ?: 100f
                    )
                    GameEventType.SET_WEATHER -> baseEvent.copy(
                        rainIntensity = rainIntensityField.text.toFloatOrNull()?.coerceIn(0f, 1f),
                        rainDuration = rainDurationField.text.toFloatOrNull()
                    )
                    GameEventType.SPAWN_AUDIO_EMITTER -> baseEvent.copy(
                        soundIds = emitterSoundIdArea.text.split("\n").map { it.trim() }.filter { it.isNotBlank() }.toMutableList(),
                        volume = emitterVolumeSlider.value,
                        range = emitterRangeField.text.toFloatOrNull(),
                        playbackMode = EmitterPlaybackMode.valueOf(emitterPlaybackModeSelect.selected),
                        playlistMode = EmitterPlaylistMode.valueOf(emitterPlaylistModeSelect.selected),
                        reactivationMode = EmitterReactivationMode.valueOf(emitterReactivationModeSelect.selected),
                        interval = emitterIntervalField.text.toFloatOrNull(),
                        timedLoopDuration = emitterTimedLoopDurationField.text.toFloatOrNull(),
                        minPitch = emitterMinPitchField.text.toFloatOrNull(),
                        maxPitch = emitterMaxPitchField.text.toFloatOrNull()
                    )
                    GameEventType.MODIFY_AUDIO_EMITTER -> baseEvent.copy(
                        emitterIsEnabled = emitterEnabledCheckbox.isChecked,
                        soundIds = emitterSoundIdArea.text.split("\n").map { it.trim() }.filter { it.isNotBlank() }.toMutableList(),
                        volume = emitterVolumeSlider.value,
                        range = emitterRangeField.text.toFloatOrNull(),
                        playbackMode = EmitterPlaybackMode.valueOf(emitterPlaybackModeSelect.selected),
                        playlistMode = EmitterPlaylistMode.valueOf(emitterPlaylistModeSelect.selected),
                        reactivationMode = EmitterReactivationMode.valueOf(emitterReactivationModeSelect.selected),
                        interval = emitterIntervalField.text.toFloatOrNull(),
                        timedLoopDuration = emitterTimedLoopDurationField.text.toFloatOrNull(),
                        minPitch = emitterMinPitchField.text.toFloatOrNull(),
                        maxPitch = emitterMaxPitchField.text.toFloatOrNull()
                    )

                    else -> baseEvent
                }
                onSave(finalEvent)
            }
        })
        dialog.button("Cancel")

        val scrollPane = ScrollPane(content, skin)
        scrollPane.fadeScrollBars = false
        dialog.contentTable.clear()
        dialog.add(scrollPane).expand().fill()

        updateVisibleFields()
        dialog.show(stage)
    }

    fun refreshEventWidgets() {
        startEventsContainer.clearChildren()
        currentMissionDef?.eventsOnStart?.forEach { event ->
            startEventsContainer.addActor(createEventWidget(event, true))
        }
        completeEventsContainer.clearChildren()
        currentMissionDef?.eventsOnComplete?.forEach { event ->
            completeEventsContainer.addActor(createEventWidget(event, false))
        }
    }

    private fun createEventWidget(event: GameEvent, isStartEvent: Boolean): Actor {
        val eventCopy = event
        val table = Table()

        // MODIFIED: Added the new event types to the 'when' expression
        val text = when(event.type) {
            GameEventType.SPAWN_ENEMY -> "SPAWN ${event.enemyType?.displayName} with ID '${event.targetId}'"
            GameEventType.SPAWN_NPC -> "SPAWN ${event.npcType?.displayName} with ID '${event.targetId}'"
            GameEventType.SPAWN_CAR -> "SPAWN ${event.carType?.displayName}"
            GameEventType.SPAWN_ITEM -> "SPAWN ${event.itemType?.displayName}"
            GameEventType.SPAWN_MONEY_STACK -> "SPAWN Money Stack ($${event.itemValue})"
            GameEventType.DESPAWN_ENTITY -> "DESPAWN entity with ID '${event.targetId}'"
            GameEventType.START_DIALOG -> "START DIALOG '${event.dialogId}'"
            GameEventType.SPAWN_HOUSE -> "SPAWN House: ${event.houseType?.displayName}"
            GameEventType.SPAWN_OBJECT -> "SPAWN Object: ${event.objectType?.displayName}"
            GameEventType.SPAWN_BLOCK -> "SPAWN Block: ${event.blockType?.displayName}"
            GameEventType.DESPAWN_BLOCK_AT_POS -> "DESPAWN Block at (${event.spawnPosition?.x?.toInt()}, ${event.spawnPosition?.y?.toInt()}, ${event.spawnPosition?.z?.toInt()})"
            GameEventType.SPAWN_SPAWNER -> "SPAWN Spawner (${event.spawnerType?.name})"
            GameEventType.SPAWN_TELEPORTER -> "SPAWN Teleporter: '${event.teleporterName}'"
            GameEventType.SPAWN_FIRE -> "SPAWN Fire"
            GameEventType.ENABLE_SPAWNER -> "ENABLE Spawner with ID '${event.targetId}'"
            GameEventType.DISABLE_SPAWNER -> "DISABLE Spawner with ID '${event.targetId}'"
            GameEventType.LOCK_HOUSE -> "LOCK House with ID '${event.targetId}'"
            GameEventType.UNLOCK_HOUSE -> "UNLOCK House with ID '${event.targetId}'"
            GameEventType.GIVE_WEAPON -> "GIVE WEAPON: ${event.weaponType?.displayName} (Ammo: ${event.ammoAmount ?: "N/A"})"
            GameEventType.FORCE_EQUIP_WEAPON -> "FORCE EQUIP: ${event.weaponType?.displayName}"
            GameEventType.CLEAR_INVENTORY -> "CLEAR PLAYER INVENTORY"
            GameEventType.SPAWN_CAR_PATH_NODE -> "SPAWN Car Path Node (ID: ${event.pathNodeId})"
            GameEventType.SPAWN_CHARACTER_PATH_NODE -> "SPAWN Char Path Node (ID: ${event.pathNodeId})"
            GameEventType.SET_WEATHER -> {
                val intensity = event.rainIntensity ?: 0f
                val durationText = event.rainDuration?.let { "for ${it}s" } ?: "(Infinite)"
                "SET WEATHER: Rain to %.1f %s".format(intensity, durationText)
            }
            GameEventType.SPAWN_AUDIO_EMITTER -> "SPAWN Emitter: '${event.targetId}'"
            GameEventType.MODIFY_AUDIO_EMITTER -> "MODIFY Emitter: '${event.targetId}'"
        }

        table.add(Label(text, skin)).growX()
        val editButton = TextButton("Edit", skin)
        editButton.addListener(object: ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                showEventDialog(eventCopy, isStartEvent)
            }
        })

        val removeButton = TextButton("X", skin)
        removeButton.addListener(object : ChangeListener() {
            override fun changed(eventChanged: ChangeEvent?, actor: Actor?) {
                currentMissionDef?.let { mission ->
                    val listToModify = if (isStartEvent) mission.eventsOnStart else mission.eventsOnComplete
                    listToModify.remove(event)

                    refreshEventWidgets() // Refresh the UI
                }
            }
        })

        table.add(editButton); table.add(removeButton)
        return table
    }

    fun updateAreaFields(center: Vector3, radius: Float) {
        areaXField.text = String.format(Locale.US, "%.2f", center.x)
        areaYField.text = String.format(Locale.US, "%.2f", center.y)
        areaZField.text = String.format(Locale.US, "%.2f", center.z)
        radiusField.text = String.format(Locale.US, "%.2f", radius)
    }

    private fun showObjectiveDialog(existingObjective: MissionObjective?) {
        val dialog = Dialog(if (existingObjective == null) "Add Objective" else "Edit Objective", skin, "dialog")
        dialog.isModal = true

        val rootTable = Table()
        dialog.contentTable.add(rootTable).grow()

        val contentContainer = Table()
        contentContainer.pad(10f).defaults().pad(2f).align(Align.left)

        // --- OBJECTIVE PROPERTY UI ---
        val descField = TextField(existingObjective?.description ?: "", skin)
        val typeSelect = SelectBox<String>(skin).apply {
            items = GdxArray(ConditionType.entries.map { it.name }.toTypedArray())
            selected = existingObjective?.completionCondition?.type?.name ?: ConditionType.ENTER_AREA.name
        }

        // NEW: Checkbox for objective visuals
        val showVisualsCheckbox = CheckBox(" Show Visual Indicator", skin).apply {
            isChecked = existingObjective?.showVisuals ?: true
        }

        // --- Timer UI elements ---
        val hasTimerCheckbox = CheckBox(" Enable Timer", skin).apply {
            isChecked = existingObjective?.hasTimer ?: false
        }
        val showCounterCheckbox = CheckBox(" Show 'Enemies Left' Counter", skin).apply {
            isChecked = existingObjective?.showEnemiesLeftCounter ?: false
        }
        val timerDurationField = TextField(existingObjective?.timerDuration?.toString() ?: "60.0", skin)
        val targetIdField = TextField(existingObjective?.completionCondition?.targetId ?: "", skin)
        val altitudeField = TextField(existingObjective?.completionCondition?.targetAltitude?.toString() ?: "100.0", skin)
        val itemTypeSelect = SelectBox<String>(skin).apply {
            items = GdxArray(ItemType.entries.map { it.displayName }.toTypedArray())
            selected = existingObjective?.completionCondition?.itemType?.displayName ?: ItemType.MONEY_STACK.displayName
        }
        val itemCountField = TextField(existingObjective?.completionCondition?.itemCount?.toString() ?: "1", skin)
        val itemIdField = TextField(existingObjective?.completionCondition?.itemId ?: "", skin)
        itemIdField.messageText = "Unique Item ID (e.g., 'key_to_vault')"

        val requiredDistanceField = TextField(existingObjective?.completionCondition?.requiredDistance?.toString() ?: "50.0", skin)

        val checkTargetInsteadCheckbox = CheckBox(" Track Target ID Instead of Player", skin).apply {
            isChecked = existingObjective?.completionCondition?.checkTargetInsteadOfPlayer ?: false
        }

        val requirePlayerCheckbox = CheckBox(" Player Must Also Be At Destination", skin).apply {
            isChecked = existingObjective?.completionCondition?.requirePlayerAtDestination ?: true // Default to true
        }

        // --- DYNAMIC UI TABLES (Setup) ---
        val timerTable = Table(skin)
        timerTable.add(Label("Duration (sec):", skin)).padRight(10f)
        timerTable.add(timerDurationField).width(80f)
        timerTable.isVisible = hasTimerCheckbox.isChecked

        hasTimerCheckbox.addListener(object: ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                timerTable.isVisible = hasTimerCheckbox.isChecked
            }
        })

        // Condition-specific fields
        val dialogSettingsTable = Table(skin)
        val objectiveDialogIdSelectBox = SelectBox<String>(skin)
        objectiveDialogIdSelectBox.items = GdxArray(missionSystem.getAllDialogueIds().toTypedArray())
        objectiveDialogIdSelectBox.selected = existingObjective?.completionCondition?.dialogId
        dialogSettingsTable.add(Label("Dialogue to Play:", skin)).left().row()
        dialogSettingsTable.add(objectiveDialogIdSelectBox).growX().row()

        // --- DYNAMIC UI TABLES ---
        val targetIdTable = Table(skin).apply { add("Target ID:"); add(targetIdField).width(250f) }
        val altitudeTable = Table(skin).apply { add("Target Altitude (Y):"); add(altitudeField).width(80f) }
        val areaTable = Table(skin)
        val itemTable = Table(skin).apply {
            add("Item Type:"); add(itemTypeSelect).row()
            add("Count:"); add(itemCountField).width(80f).row()
        }
        val specificItemTable = Table(skin).apply {
            add("Specific Item ID:"); add(itemIdField).width(250f)
        }

        val stayInAreaModeSelectBox = SelectBox<String>(skin).apply {
            items = GdxArray(StayInAreaMode.entries.map { it.displayName }.toTypedArray())
            selected = existingObjective?.completionCondition?.stayInAreaMode?.displayName ?: StayInAreaMode.PLAYER_ONLY.displayName
        }
        val stayInAreaTable = Table(skin)
        stayInAreaTable.add(Label("Must stay in area as:", skin)).padRight(10f)
        stayInAreaTable.add(stayInAreaModeSelectBox).growX()

        val completionActionSelectBox = SelectBox<String>(skin).apply {
            items = GdxArray(MaintainDistanceCompletionAction.entries.map { it.displayName }.toTypedArray())
            selected = existingObjective?.completionCondition?.maintainDistanceCompletionAction?.displayName
                ?: MaintainDistanceCompletionAction.STOP_AND_EXIT_CAR.displayName // A sensible default
        }

        val maintainDistanceTable = Table(skin)
        val requiredDistanceTable = Table(skin) // Sub-table for distance
        requiredDistanceTable.add(Label("Required Distance:", skin)).padRight(10f)
        requiredDistanceTable.add(requiredDistanceField).width(80f)
        maintainDistanceTable.add(requiredDistanceTable).left().row()
        maintainDistanceTable.add(requirePlayerCheckbox).left().padTop(5f).row()
        maintainDistanceTable.add(Label("On Completion:", skin)).left().padTop(5f)
        maintainDistanceTable.add(completionActionSelectBox).growX().left().padTop(5f).row()

        radiusField.text = String.format(Locale.US, "%.2f", existingObjective?.completionCondition?.areaRadius ?: 10.0f)
        areaXField.text = String.format(Locale.US, "%.2f", existingObjective?.completionCondition?.areaCenter?.x ?: 0.0f)
        areaYField.text = String.format(Locale.US, "%.2f", existingObjective?.completionCondition?.areaCenter?.y ?: 0.0f)
        areaZField.text = String.format(Locale.US, "%.2f", existingObjective?.completionCondition?.areaCenter?.z ?: 0.0f)

        val posTable = Table(skin)
        posTable.add("X:").padRight(5f); posTable.add(areaXField).width(60f).padRight(10f)
        posTable.add("Y:").padRight(5f); posTable.add(areaYField).width(60f).padRight(10f)
        posTable.add("Z:").padRight(5f); posTable.add(areaZField).width(60f)

        areaTable.add(Label("Area Center:", skin)).left().row()
        areaTable.add(posTable).left().padBottom(5f).row()
        areaTable.add(Label("Radius:", skin)).left().padRight(10f)
        areaTable.add(radiusField).width(80f).left()

        val placeButton = TextButton("Place Area", skin)
        areaTable.add(placeButton).padLeft(15f).row()
        areaTable.add(checkTargetInsteadCheckbox).colspan(3).left().padTop(5f)

        // --- UI FOR OBJECTIVE-SPECIFIC EVENTS ---
        val objectiveEventsContainer = VerticalGroup().apply { space(5f); wrap(false); align(Align.left) }
        val objectiveEventsScrollPane = ScrollPane(objectiveEventsContainer, skin).apply {
            fadeScrollBars = false
            setScrollingDisabled(true, false)
        }

        fun refreshObjectiveEventWidgets() {
            objectiveEventsContainer.clearChildren()
            // Use a safe copy of the list to avoid issues while editing
            val events = existingObjective?.eventsOnStart?.toList() ?: emptyList()
            events.forEach { event ->
                val eventWidget = createEventWidget(event, isStartEvent = false)
                val removeButton = (eventWidget as Table).children.find { it is TextButton && it.text.toString() == "X" } as? TextButton
                removeButton?.clearListeners()
                removeButton?.addListener(object : ChangeListener() {
                    override fun changed(eventChanged: ChangeEvent?, actor: Actor?) {
                        existingObjective?.eventsOnStart?.remove(event)
                        refreshObjectiveEventWidgets()
                    }
                })
                objectiveEventsContainer.addActor(eventWidget)
            }
        }

        // --- DIALOG LAYOUT ---
        val settingsTable = Table()
        settingsTable.defaults().align(Align.left).padBottom(2f)

        settingsTable.add(Label("Description:", skin)); settingsTable.add(descField).width(300f).row()
        settingsTable.add(Label("Condition Type:", skin)); settingsTable.add(typeSelect).row()
        settingsTable.add(showVisualsCheckbox).colspan(2).left().padTop(5f).row() // NEW: Add checkbox to layout
        settingsTable.add(hasTimerCheckbox).colspan(2).left().padTop(5f).row()
        settingsTable.add(timerTable).colspan(2).left().row()
        settingsTable.add(showCounterCheckbox).colspan(2).left().padTop(5f).row()
        settingsTable.add(targetIdTable).colspan(2).row()
        settingsTable.add(dialogSettingsTable).colspan(2).row()
        settingsTable.add(areaTable).colspan(2).row()
        settingsTable.add(stayInAreaTable).colspan(2).row()
        settingsTable.add(maintainDistanceTable).colspan(2).row()
        settingsTable.add(altitudeTable).colspan(2).row()
        settingsTable.add(itemTable).colspan(2).row()
        settingsTable.add(specificItemTable).colspan(2).row()

        contentContainer.add(settingsTable).row()

        contentContainer.add(Label("--- Events on Objective Start ---", skin, "title")).colspan(2).padTop(10f).row()
        contentContainer.add(objectiveEventsScrollPane).colspan(2).growX().height(80f).row()
        val addEventToObjectiveButton = TextButton("Add Event", skin)
        contentContainer.add(addEventToObjectiveButton).colspan(2).left().padTop(5f).row()

        // --- SCROLLPANE SETUP ---
        val mainScrollPane = ScrollPane(contentContainer, skin)
        mainScrollPane.setFadeScrollBars(false)
        mainScrollPane.setScrollingDisabled(true, false)
        rootTable.add(mainScrollPane).grow()

        if (existingObjective != null) {
            refreshObjectiveEventWidgets()
        }

        // --- LISTENER FOR "ADD EVENT" BUTTON ---
        addEventToObjectiveButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (existingObjective == null) {
                    uiManager.showTemporaryMessage("Save the objective first before adding events.")
                    return
                }
                // Show the event editor dialog. When it saves, it will add the event
                showEventDialog(null, isStartEvent = false) { newEvent ->
                    existingObjective.eventsOnStart.add(newEvent)
                    refreshObjectiveEventWidgets()
                }
            }
        })

        placeButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val objectiveToPlace = existingObjective ?: MissionObjective(
                    completionCondition = CompletionCondition(
                        type = ConditionType.ENTER_AREA, // This is a default, it will be updated by the save button
                        areaCenter = Vector3(
                            areaXField.text.toFloatOrNull() ?: 0f,
                            areaYField.text.toFloatOrNull() ?: 0f,
                            areaZField.text.toFloatOrNull() ?: 0f
                        ),
                        areaRadius = radiusField.text.toFloatOrNull() ?: 10f
                    )
                )
                uiManager.enterObjectiveAreaPlacementMode(objectiveToPlace, dialog)
            }
        })

        // --- LOGIC TO SHOW/HIDE DYNAMIC FIELDS ---
        fun updateVisibleFields() {
            val selectedType = try { ConditionType.valueOf(typeSelect.selected) } catch (e: Exception) { ConditionType.ENTER_AREA }
            showCounterCheckbox.isVisible = selectedType in listOf(
                ConditionType.ELIMINATE_TARGET,
                ConditionType.ELIMINATE_ALL_ENEMIES
            )

            val isSurviveObjective = selectedType == ConditionType.SURVIVE_FOR_TIME

            if (isSurviveObjective) {
                hasTimerCheckbox.isChecked = true
                hasTimerCheckbox.isDisabled = true
                timerTable.isVisible = true
            } else {
                hasTimerCheckbox.isDisabled = false // Unlock the checkbox
                timerTable.isVisible = hasTimerCheckbox.isChecked
            }

            dialogSettingsTable.isVisible = selectedType == ConditionType.TALK_TO_NPC
            checkTargetInsteadCheckbox.isVisible = (selectedType == ConditionType.ENTER_AREA)

            targetIdTable.isVisible = selectedType in listOf(
                ConditionType.ELIMINATE_TARGET, ConditionType.PLAYER_ELIMINATE_TARGET,
                ConditionType.TALK_TO_NPC,
                ConditionType.INTERACT_WITH_OBJECT, ConditionType.DESTROY_CAR,
                ConditionType.BURN_DOWN_HOUSE, ConditionType.DESTROY_OBJECT,
                ConditionType.DRIVE_TO_LOCATION, ConditionType.MAINTAIN_DISTANCE
            ) || (selectedType == ConditionType.ENTER_AREA && checkTargetInsteadCheckbox.isChecked)

            areaTable.isVisible = selectedType in listOf(
                ConditionType.ENTER_AREA,
                ConditionType.DRIVE_TO_LOCATION,
                ConditionType.STAY_IN_AREA,
                ConditionType.MAINTAIN_DISTANCE
            )

            altitudeTable.isVisible = selectedType == ConditionType.REACH_ALTITUDE
            itemTable.isVisible = selectedType == ConditionType.COLLECT_ITEM
            specificItemTable.isVisible = selectedType == ConditionType.COLLECT_SPECIFIC_ITEM
            stayInAreaTable.isVisible = selectedType == ConditionType.STAY_IN_AREA
            maintainDistanceTable.isVisible = selectedType == ConditionType.MAINTAIN_DISTANCE

            val targetIdLabel = targetIdTable.children.first() as Label
            if (selectedType == ConditionType.DRIVE_TO_LOCATION) {
                targetIdLabel.setText("Required Car ID (Optional):")
                targetIdField.messageText = "Leave blank for any car"
            } else {
                targetIdLabel.setText("Target ID:")
                targetIdField.messageText = "Paste ID here"
            }

            dialog.pack()
            val maxWidth = stage.width * 0.9f
            val maxHeight = stage.height * 0.85f
            if (dialog.width > maxWidth) dialog.width = maxWidth
            if (dialog.height > maxHeight) dialog.height = maxHeight
            dialog.setPosition(stage.width / 2f, stage.height / 2f, Align.center)
        }

        checkTargetInsteadCheckbox.addListener(object: ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                updateVisibleFields()
            }
        })

        typeSelect.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { updateVisibleFields() } })

        // --- MAIN "SAVE" BUTTON FOR THE OBJECTIVE ---
        val saveButton = TextButton("Save", skin)
        saveButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val conditionType = try { ConditionType.valueOf(typeSelect.selected) } catch (e: Exception) { ConditionType.ENTER_AREA }
                val itemType = ItemType.entries.find { it.displayName == itemTypeSelect.selected }
                val areaCenterVec = Vector3(areaXField.text.toFloatOrNull() ?: 0f, areaYField.text.toFloatOrNull() ?: 0f, areaZField.text.toFloatOrNull() ?: 0f)
                val areaRadiusValue = radiusField.text.toFloatOrNull() ?: 10f
                val stayInAreaModeSelection = StayInAreaMode.entries.find { it.displayName == stayInAreaModeSelectBox.selected }

                val needsArea = conditionType in listOf(ConditionType.ENTER_AREA, ConditionType.DRIVE_TO_LOCATION, ConditionType.STAY_IN_AREA, ConditionType.MAINTAIN_DISTANCE)
                val completionAction = MaintainDistanceCompletionAction.entries.find { it.displayName == completionActionSelectBox.selected }

                val newCondition = CompletionCondition(
                    type = conditionType,
                    targetId = targetIdField.text.ifBlank { null },
                    checkTargetInsteadOfPlayer = (conditionType == ConditionType.ENTER_AREA && checkTargetInsteadCheckbox.isChecked),
                    requirePlayerAtDestination = if (conditionType == ConditionType.MAINTAIN_DISTANCE) requirePlayerCheckbox.isChecked else true,
                    maintainDistanceCompletionAction = if (conditionType == ConditionType.MAINTAIN_DISTANCE) completionAction else null, // NEW
                    requiredDistance = if (conditionType == ConditionType.MAINTAIN_DISTANCE) requiredDistanceField.text.toFloatOrNull() else null,
                    dialogId = if (conditionType == ConditionType.TALK_TO_NPC) objectiveDialogIdSelectBox.selected.ifBlank { null } else null,
                    targetAltitude = altitudeField.text.toFloatOrNull(),
                    areaCenter = if (needsArea) areaCenterVec else null,
                    areaRadius = if (needsArea) areaRadiusValue else null,
                    stayInAreaMode = if (conditionType == ConditionType.STAY_IN_AREA) stayInAreaModeSelection else null,
                    itemType = itemType,
                    itemCount = itemCountField.text.toIntOrNull() ?: 1,
                    itemId = itemIdField.text.ifBlank { null }
                )

                // If we are editing, copy the existing event list. Otherwise, create a new one.
                val eventsOnStart = existingObjective?.eventsOnStart ?: mutableListOf()

                val newObjective = existingObjective?.copy(
                    description = descField.text.ifBlank { "New Objective" },
                    completionCondition = newCondition,
                    hasTimer = hasTimerCheckbox.isChecked,
                    timerDuration = timerDurationField.text.toFloatOrNull() ?: 60f,
                    showVisuals = showVisualsCheckbox.isChecked, // NEW: Save the visual state
                    showEnemiesLeftCounter = showCounterCheckbox.isChecked
                ) ?: MissionObjective(
                    description = descField.text.ifBlank { "New Objective" },
                    completionCondition = newCondition,
                    eventsOnStart = eventsOnStart,
                    hasTimer = hasTimerCheckbox.isChecked,
                    timerDuration = timerDurationField.text.toFloatOrNull() ?: 60f,
                    showVisuals = showVisualsCheckbox.isChecked, // NEW: Save the visual state
                    showEnemiesLeftCounter = showCounterCheckbox.isChecked
                )

                if (existingObjective == null) {
                    tempObjectives.add(newObjective)
                } else {
                    val index = tempObjectives.indexOfFirst { it.id == existingObjective.id }
                    if (index != -1) tempObjectives[index] = newObjective
                }
                refreshObjectiveWidgets()
                dialog.hide()
                stage.unfocusAll()
            }
        })

        dialog.button(saveButton)
        dialog.button("Cancel")

        updateVisibleFields()
        dialog.show(stage)
        stage.keyboardFocus = descField
    }

    private fun showRewardDialog(existingReward: MissionReward?) {
        val dialog = Dialog(if (existingReward == null) "Add Reward" else "Edit Reward", skin, "dialog")
        val content = dialog.contentTable
        content.pad(10f).defaults().pad(5f).align(Align.left)

        val typeSelect = SelectBox<String>(skin).apply {
            items = GdxArray(RewardType.entries.map { it.name }.toTypedArray())
            selected = existingReward?.type?.name ?: RewardType.SHOW_MESSAGE.name
        }
        val amountField = TextField(existingReward?.amount?.toString() ?: "0", skin)
        val messageField = TextField(existingReward?.message ?: "", skin)
        val weaponTypeSelect = SelectBox<String>(skin).apply {
            items = GdxArray(WeaponType.entries.map { it.displayName }.toTypedArray())
            selected = existingReward?.weaponType?.displayName ?: WeaponType.REVOLVER.displayName
        }
        val itemTypeSelect = SelectBox<String>(skin).apply {
            items = GdxArray(ItemType.entries.map { it.displayName }.toTypedArray())
            selected = existingReward?.itemType?.displayName ?: ItemType.MONEY_STACK.displayName
        }
        val spawnerTargetEnemySelectBox = SelectBox<String>(skin).apply {
            items = GdxArray(EnemyType.entries.map { it.displayName }.toTypedArray())
            selected = existingReward?.spawnerTargetEnemyType?.displayName
        }
        val newDefaultWeaponSelectBox = SelectBox<String>(skin).apply {
            items = GdxArray(WeaponType.entries.map { it.displayName }.toTypedArray())
            selected = existingReward?.newDefaultWeapon?.displayName
        }

        content.add("Reward Type:"); content.add(typeSelect).row()

        val amountTable = Table(skin).apply { add("Amount:"); add(amountField).width(100f) }
        val messageTable = Table(skin).apply { add("Message:"); add(messageField).width(300f) }
        val weaponTable = Table(skin).apply { add("Weapon Type:"); add(weaponTypeSelect) }
        val itemTable = Table(skin).apply { add("Item Type:"); add(itemTypeSelect) }

        val spawnerUpgradeTable = Table(skin)
        spawnerUpgradeTable.add(Label("Enemy Type to Upgrade:", skin)).left().row()
        spawnerUpgradeTable.add(spawnerTargetEnemySelectBox).growX().row()
        spawnerUpgradeTable.add(Label("New Default Weapon:", skin)).left().padTop(5f).row()
        spawnerUpgradeTable.add(newDefaultWeaponSelectBox).growX().row()

        content.add(amountTable).colspan(2).row()
        content.add(messageTable).colspan(2).row()
        content.add(weaponTable).colspan(2).row()
        content.add(itemTable).colspan(2).row()
        content.add(spawnerUpgradeTable).colspan(2).row()

        fun updateVisibleFields() {
            val selectedType = try { RewardType.valueOf(typeSelect.selected) } catch (e: Exception) { RewardType.SHOW_MESSAGE }
            amountTable.isVisible = selectedType == RewardType.GIVE_MONEY || selectedType == RewardType.GIVE_AMMO || selectedType == RewardType.GIVE_ITEM
            messageTable.isVisible = selectedType == RewardType.SHOW_MESSAGE
            weaponTable.isVisible = selectedType == RewardType.GIVE_AMMO
            itemTable.isVisible = selectedType == RewardType.GIVE_ITEM
            spawnerUpgradeTable.isVisible = selectedType == RewardType.UPGRADE_SPAWNER_WEAPON
            dialog.pack()
        }

        typeSelect.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) { updateVisibleFields() }
        })

        updateVisibleFields()

        val saveButton = TextButton("Save", skin)
        saveButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val rewardType = try { RewardType.valueOf(typeSelect.selected) } catch (e: Exception) { RewardType.SHOW_MESSAGE }
                val weaponType = WeaponType.entries.find { it.displayName == weaponTypeSelect.selected }
                val itemType = ItemType.entries.find { it.displayName == itemTypeSelect.selected }
                val targetEnemyType = EnemyType.entries.find { it.displayName == spawnerTargetEnemySelectBox.selected }
                val newWeaponType = WeaponType.entries.find { it.displayName == newDefaultWeaponSelectBox.selected }

                val newReward = existingReward?.copy(
                    type = rewardType,
                    amount = amountField.text.toIntOrNull() ?: 0,
                    message = messageField.text.ifBlank { "Reward granted." },
                    weaponType = weaponType,
                    itemType = itemType,
                    spawnerTargetEnemyType = targetEnemyType,
                    newDefaultWeapon = newWeaponType
                ) ?: MissionReward(
                    type = rewardType,
                    amount = amountField.text.toIntOrNull() ?: 0,
                    message = messageField.text.ifBlank { "Reward granted." },
                    weaponType = weaponType,
                    itemType = itemType,
                    spawnerTargetEnemyType = targetEnemyType,
                    newDefaultWeapon = newWeaponType
                )

                if (existingReward == null) tempRewards.add(newReward)
                else {
                    val index = tempRewards.indexOf(existingReward)
                    if (index != -1) tempRewards[index] = newReward
                }
                refreshRewardWidgets()
                dialog.hide()
            }
        })

        dialog.button(saveButton)
        dialog.button("Cancel", false)
        dialog.show(stage)
    }

    private fun refreshObjectiveWidgets() {
        objectivesContainer.clearChildren()
        tempObjectives.forEach { objective ->
            val objectiveCopy = objective
            val table = Table()
            table.add(Label("${objective.completionCondition.type}: ${objective.description}", skin)).growX()
            val editButton = TextButton("Edit", skin)
            editButton.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) { showObjectiveDialog(objectiveCopy) }
            })
            val removeButton = TextButton("X", skin)
            removeButton.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    currentMissionDef?.objectives?.remove(objective)
                    tempObjectives.remove(objective)
                    refreshObjectiveWidgets()
                }
            })
            table.add(editButton); table.add(removeButton)
            objectivesContainer.addActor(table)
        }
    }

    private fun refreshRewardWidgets() {
        rewardsContainer.clearChildren()
        tempRewards.forEach { reward ->
            val rewardCopy = reward
            val table = Table()
            val rewardText = when (reward.type) {
                RewardType.GIVE_MONEY -> "Give Money: $${reward.amount}"
                RewardType.SHOW_MESSAGE -> "Show Message: '${reward.message}'"
                RewardType.GIVE_AMMO -> "Give Ammo: ${reward.amount} for ${reward.weaponType?.displayName}"
                RewardType.GIVE_ITEM -> "Give Item: ${reward.amount}x ${reward.itemType?.displayName}"
                else -> "${reward.type}"
            }
            table.add(Label(rewardText, skin)).growX()
            val editButton = TextButton("Edit", skin)
            editButton.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) { showRewardDialog(rewardCopy) }
            })
            val removeButton = TextButton("X", skin)
            removeButton.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    currentMissionDef?.rewards?.remove(reward)
                    tempRewards.remove(reward)
                    refreshRewardWidgets()
                }
            })
            table.add(editButton); table.add(removeButton)
            rewardsContainer.addActor(table)
        }
    }

    private fun getEventDescription(event: GameEvent): String {
        return when(event.type) {
            GameEventType.SPAWN_ENEMY -> "Spawn ${event.enemyType?.displayName ?: "Enemy"}"
            GameEventType.SPAWN_NPC -> "Spawn ${event.npcType?.displayName ?: "NPC"}"
            GameEventType.SPAWN_CAR -> "Spawn ${event.carType?.displayName ?: "Car"}"
            GameEventType.SPAWN_ITEM -> "Spawn ${event.itemType?.displayName ?: "Item"}"
            GameEventType.SPAWN_MONEY_STACK -> "Spawn Money ($${event.itemValue})"
            GameEventType.DESPAWN_ENTITY -> "Despawn Entity: ${event.targetId}"
            GameEventType.DESPAWN_BLOCK_AT_POS -> "Despawn Block at (${event.spawnPosition?.x?.toInt()}, ${event.spawnPosition?.y?.toInt()}, ${event.spawnPosition?.z?.toInt()})"
            GameEventType.ENABLE_SPAWNER -> "Enable Spawner: ${event.targetId}"
            GameEventType.DISABLE_SPAWNER -> "Disable Spawner: ${event.targetId}"
            GameEventType.LOCK_HOUSE -> "Lock House: ${event.targetId}"
            GameEventType.UNLOCK_HOUSE -> "Unlock House: ${event.targetId}"
            GameEventType.START_DIALOG -> "Start Dialog: ${event.dialogId}"
            else -> event.type.name // Fallback for other types
        }
    }

    private fun refreshMissionList() {
        val previouslySelectedId = selectedMissionRow?.userObject as? String

        missionListContainer.clearChildren()
        selectedMissionRow = null
        clearEditor()

        val missions = missionSystem.getAllMissionDefinitions().values.sortedBy { it.id }
        val missionStrings = GdxArray(missions.map { "${it.id}: ${it.title}" }.toTypedArray())
        missionSelectBox.items = missionStrings // Also update the mission selector dropdown

        if (missions.isNotEmpty()) {
            missions.forEachIndexed { index, mission ->
                val rowWidget = createMissionRowWidget(mission, index + 1)
                missionListContainer.add(rowWidget).growX().fillX().row()
            }

            // Try to re-select the previously selected mission
            val rowToSelect = missionListContainer.children.find { (it as? Table)?.userObject == previouslySelectedId } as? Table
            if (rowToSelect != null) {
                selectMissionRow(rowToSelect, previouslySelectedId!!)
            } else {
                // Otherwise, just select the first one in the list
                val firstRow = missionListContainer.children.first() as Table
                val firstMissionId = firstRow.userObject as String
                selectMissionRow(firstRow, firstMissionId)
            }
        }
    }

    fun show() {
        refreshMissionList()
        initializeMode()
        window.isVisible = true
        window.toFront()
    }

    fun hide() {
        window.isVisible = false
        stage.unfocusAll()
    }

    fun isVisible(): Boolean = window.isVisible
}
