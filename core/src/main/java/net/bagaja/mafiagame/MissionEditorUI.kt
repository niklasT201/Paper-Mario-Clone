package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array as GdxArray
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.ui.List
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import java.util.*

class MissionEditorUI(
    private val skin: Skin,
    private val stage: Stage,
    private val missionSystem: MissionSystem,
    private val uiManager: UIManager
) {
    private val window: Window = Window("Mission Editor", skin, "dialog")
    private var currentMissionDef: MissionDefinition? = null

    // Left Panel
    private val missionList: com.badlogic.gdx.scenes.scene2d.ui.List<String>

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

    init {
        window.isMovable = true
        window.isModal = false
        window.setSize(Gdx.graphics.width * 1.9f, Gdx.graphics.height * 1.8f)
        window.setPosition(stage.width / 2f, stage.height / 2f, Align.center)
        window.padTop(40f)

        // MODIFIED: Initialize the fields in the init block
        radiusField = TextField("", skin)
        areaXField = TextField("", skin)
        areaYField = TextField("", skin)
        areaZField = TextField("", skin)
        // END MODIFIED

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
        missionList = List(skin)
        val listScrollPane = ScrollPane(missionList, skin)
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
        val buttonsTable = Table()
        val showFlowButton = TextButton("View Mission Flow", skin)
        val editModifiersButton = TextButton("Edit Modifiers", skin)
        buttonsTable.add(showFlowButton).pad(5f)
        buttonsTable.add(editModifiersButton).pad(5f)
        editorDetailsTable.add(buttonsTable).colspan(2).center().padTop(10f).padBottom(15f).row()

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
        editorScrollPane.setFadeScrollBars(false)

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

        showFlowButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                showMissionFlowDialog()
            }
        })

        missionSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                uiManager.game.sceneManager.clearMissionPreviews() // Clear old previews when changing mission
                val missionId = missionSelectBox.selected.split(":")[0].trim()
                uiManager.selectedMissionForEditing = missionSystem.getMissionDefinition(missionId)
                uiManager.setPersistentMessage("MISSION EDITING: ${uiManager.selectedMissionForEditing?.title}")
            }
        })

        addStartEventButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) { showEventDialog(null, true) }
        })
        addCompleteEventButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) { showEventDialog(null, false) }
        })
        addObjectiveButton.addListener(object: ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) { showObjectiveDialog(null) }
        })
        addRewardButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) { showRewardDialog(null) }
        })
        missionList.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val selectedId = missionList.selected?.split(":")?.get(0)?.trim()
                if (selectedId != null) populateEditor(selectedId)
            }
        })
        newMissionButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val newMission = missionSystem.createNewMission()
                refreshMissionList()
                missionList.selected = newMission.id + ": " + newMission.title
                missionSystem.game.triggerSystem.refreshTriggers()
            }
        })

        deleteMissionButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                currentMissionDef?.id?.let { missionSystem.deleteMission(it); refreshMissionList(); clearEditor(); missionSystem.game.triggerSystem.refreshTriggers() }
            }
        })
        editModifiersButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                showModifiersDialog()
            }
        })

        saveButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                applyChanges()
                missionSystem.game.triggerSystem.refreshTriggers()
                hide() // This calls the hide() method below
            }
        })
        closeButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                hide() // This also calls the hide() method
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
        mission.scope = MissionScope.valueOf(scopeSelectBox.selected)

        missionSystem.saveMission(mission)
    }

    private fun showModifiersDialog() {
        val mission = currentMissionDef ?: return
        val dialog = Dialog("Edit Mission Modifiers", skin, "dialog")
        dialog.isMovable = true
        dialog.isResizable = true

        val content = VerticalGroup().apply {
            space(8f)
            pad(15f)
            align(Align.left)
            fill()
        }

        // --- Player Modifiers ---
        content.addActor(Label("[YELLOW]--- Player Modifiers ---", skin))
        val unlimitedHealth = CheckBox(" Unlimited Health", skin).apply { isChecked = mission.modifiers.setUnlimitedHealth }
        val incomingDmg = TextField(mission.modifiers.incomingDamageMultiplier.toString(), skin)
        val outgoingDmg = TextField(mission.modifiers.playerDamageMultiplier.toString(), skin)
        val speedMulti = TextField(mission.modifiers.playerSpeedMultiplier.toString(), skin)
        val infiniteAmmoAll = CheckBox(" Infinite Ammo (All Weapons)", skin).apply { isChecked = mission.modifiers.infiniteAmmo }
        val disableSwitch = CheckBox(" Disable Weapon Switching", skin).apply { isChecked = mission.modifiers.disableWeaponSwitching }
        val disablePickups = CheckBox(" Disable Weapon Pickups", skin).apply { isChecked = mission.modifiers.disableWeaponPickups }

        content.addActor(unlimitedHealth)
        content.addActor(Table(skin).apply { add("Incoming Damage Multiplier:"); add(incomingDmg).width(80f).left() })
        content.addActor(Table(skin).apply { add("Player Damage Multiplier:"); add(outgoingDmg).width(80f).left() })
        content.addActor(Table(skin).apply { add("Player Speed Multiplier:"); add(speedMulti).width(80f).left() })
        content.addActor(infiniteAmmoAll)
        content.addActor(disableSwitch)
        content.addActor(disablePickups)

        // --- Vehicle Modifiers ---
        content.addActor(Label("[LIME]--- Vehicle Modifiers ---", skin))
        content.space(18f)
        val invincibleVehicle = CheckBox(" Player's Vehicle is Invincible", skin).apply { isChecked = mission.modifiers.makePlayerVehicleInvincible }
        val allCarsUnlocked = CheckBox(" All Cars are Unlocked", skin).apply { isChecked = mission.modifiers.allCarsUnlocked }
        val carSpeedMulti = TextField(mission.modifiers.carSpeedMultiplier.toString(), skin)

        content.addActor(invincibleVehicle)
        content.addActor(allCarsUnlocked)
        content.addActor(Table(skin).apply { add("Player Car Speed Multiplier:"); add(carSpeedMulti).width(80f).left() })

        // --- World & AI Modifiers ---
        content.addActor(Label("[CYAN]--- World & AI Modifiers ---", skin))
        content.space(18f)
        val disableSpawners = CheckBox(" Disable Car Spawners (No Traffic)", skin).apply { isChecked = mission.modifiers.disableCarSpawners }

        val freezeTimeCheckbox = CheckBox(" Freeze Time of Day", skin).apply { isChecked = mission.modifiers.freezeTimeAt != null }
        val timeSlider = Slider(0f, 1f, 0.01f, false, skin).apply { value = mission.modifiers.freezeTimeAt ?: 0.5f }
        val timeSliderTable = Table(skin).apply{ add("Time:"); add(timeSlider).growX() }
        timeSliderTable.isVisible = freezeTimeCheckbox.isChecked
        freezeTimeCheckbox.addListener(object: ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                timeSliderTable.isVisible = freezeTimeCheckbox.isChecked
            }
        })

        content.addActor(disableSpawners)
        content.addActor(freezeTimeCheckbox)
        content.addActor(timeSliderTable)

        val scrollPane = ScrollPane(content, skin)
        scrollPane.fadeScrollBars = false
        dialog.contentTable.add(scrollPane).grow().minWidth(450f).minHeight(400f)

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
                mission.modifiers.disableWeaponSwitching = disableSwitch.isChecked
                mission.modifiers.disableWeaponPickups = disablePickups.isChecked

                // Vehicle Modifiers
                mission.modifiers.makePlayerVehicleInvincible = invincibleVehicle.isChecked
                mission.modifiers.allCarsUnlocked = allCarsUnlocked.isChecked
                mission.modifiers.carSpeedMultiplier = carSpeedMulti.text.toFloatOrNull() ?: 1.0f

                // World Modifiers
                mission.modifiers.disableCarSpawners = disableSpawners.isChecked
                mission.modifiers.freezeTimeAt = if (freezeTimeCheckbox.isChecked) timeSlider.value else null

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
        val content = dialog.contentTable
        content.pad(10f).defaults().pad(5f).align(Align.left)

        val typeSelect = SelectBox<String>(skin).apply {
            items = GdxArray(GameEventType.entries.map { it.name }.toTypedArray())
            if (existingEvent != null) selected = existingEvent.type.name
        }

        // --- Common & Spawn Fields ---
        val targetIdField = TextField(existingEvent?.targetId ?: "", skin)
        val posXField = TextField(existingEvent?.spawnPosition?.x?.toString() ?: "0", skin)
        val posYField = TextField(existingEvent?.spawnPosition?.y?.toString() ?: "0", skin)
        val posZField = TextField(existingEvent?.spawnPosition?.z?.toString() ?: "0", skin)

        // --- Event-Specific Fields ---
        val enemyTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(EnemyType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.enemyType?.displayName }
        val npcTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(NPCType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.npcType?.displayName }
        val carTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(CarType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.carType?.displayName }
        val itemTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(ItemType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.itemType?.displayName }
        val moneyValueField = TextField(existingEvent?.itemValue?.toString() ?: "100", skin)
        val houseTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(HouseType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.houseType?.displayName }
        val objectTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(ObjectType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.objectType?.displayName }
        val blockTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(BlockType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.blockType?.displayName }

        // --- Fields for Inventory Events ---
        val weaponTypeSelect = SelectBox<String>(skin).apply { items = GdxArray(WeaponType.entries.map { it.displayName }.toTypedArray()); selected = existingEvent?.weaponType?.displayName }
        val ammoAmountField = TextField(existingEvent?.ammoAmount?.toString() ?: "0", skin).apply { messageText = "Default" }

        // --- Layout Tables for each Event Type ---
        val targetIdTable = Table(skin).apply { add("Target/Spawn ID:"); add(targetIdField).growX() }
        val posTable = Table(skin).apply { add("Spawn Pos X:"); add(posXField).width(60f); add("Y:"); add(posYField).width(60f); add("Z:"); add(posZField).width(60f) }

        val enemyTable = Table(skin).apply { add("Enemy Type:"); add(enemyTypeSelect).growX() }
        val npcTable = Table(skin).apply { add("NPC Type:"); add(npcTypeSelect).growX() }
        val carTable = Table(skin).apply { add("Car Type:"); add(carTypeSelect).growX() }
        val itemTable = Table(skin).apply { add("Item Type:"); add(itemTypeSelect).growX() }
        val moneyTable = Table(skin).apply { add("Amount:"); add(moneyValueField).width(100f) }
        val houseTable = Table(skin).apply { add("House Type:"); add(houseTypeSelect).growX() }
        val objectTable = Table(skin).apply { add("Object Type:"); add(objectTypeSelect).growX() }
        val blockTable = Table(skin).apply { add("Block Type:"); add(blockTypeSelect).growX() }

        // Layout Tables for Inventory Events
        val giveWeaponTable = Table(skin).apply {
            add("Weapon:"); add(weaponTypeSelect).growX().row()
            add("Ammo Amount:").padTop(5f); add(ammoAmountField).width(80f).left()
        }
        val forceEquipTable = Table(skin).apply {
            add("Weapon:"); add(weaponTypeSelect).growX().row()
            add("Ammo (if not owned):").padTop(5f); add(ammoAmountField).width(80f).left()
        }

        // Stack to hold all the specific settings panels
        val settingsStack = Stack(enemyTable, npcTable, carTable, itemTable, moneyTable, houseTable, objectTable, blockTable, giveWeaponTable, forceEquipTable)

        content.add("Event Type:"); content.add(typeSelect).row()
        content.add(targetIdTable).colspan(2).growX().row()
        content.add(posTable).colspan(2).growX().row()
        content.add(settingsStack).colspan(2).growX().row()

        fun updateVisibleFields() {
            val type = GameEventType.valueOf(typeSelect.selected)
            val isSpawn = type.name.startsWith("SPAWN")

            posTable.isVisible = isSpawn
            targetIdTable.isVisible = type == GameEventType.SPAWN_ENEMY || type == GameEventType.SPAWN_NPC || type == GameEventType.DESPAWN_ENTITY

            // Control visibility of each panel in the stack
            enemyTable.isVisible = type == GameEventType.SPAWN_ENEMY
            npcTable.isVisible = type == GameEventType.SPAWN_NPC
            carTable.isVisible = type == GameEventType.SPAWN_CAR
            itemTable.isVisible = type == GameEventType.SPAWN_ITEM
            moneyTable.isVisible = type == GameEventType.SPAWN_MONEY_STACK
            houseTable.isVisible = type == GameEventType.SPAWN_HOUSE
            objectTable.isVisible = type == GameEventType.SPAWN_OBJECT
            blockTable.isVisible = type == GameEventType.SPAWN_BLOCK

            // Update visibility for inventory panels
            giveWeaponTable.isVisible = type == GameEventType.GIVE_WEAPON
            forceEquipTable.isVisible = type == GameEventType.FORCE_EQUIP_WEAPON

            dialog.pack()
        }

        typeSelect.addListener(object: ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) = updateVisibleFields()
        })

        dialog.button("Save").addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val newEvent = GameEvent(
                    type = GameEventType.valueOf(typeSelect.selected),
                    targetId = targetIdField.text.ifBlank { null },
                    spawnPosition = Vector3(posXField.text.toFloatOrNull() ?: 0f, posYField.text.toFloatOrNull() ?: 0f, posZField.text.toFloatOrNull() ?: 0f),
                    enemyType = EnemyType.entries.find { it.displayName == enemyTypeSelect.selected },
                    npcType = NPCType.entries.find { it.displayName == npcTypeSelect.selected },
                    carType = CarType.entries.find { it.displayName == carTypeSelect.selected },
                    itemType = ItemType.entries.find { it.displayName == itemTypeSelect.selected },
                    itemValue = moneyValueField.text.toIntOrNull() ?: 100,
                    houseType = HouseType.entries.find { it.displayName == houseTypeSelect.selected },
                    objectType = ObjectType.entries.find { it.displayName == objectTypeSelect.selected },
                    blockType = BlockType.entries.find { it.displayName == blockTypeSelect.selected },
                    weaponType = WeaponType.entries.find { it.displayName == weaponTypeSelect.selected },
                    ammoAmount = ammoAmountField.text.toIntOrNull()
                )
                onSave(newEvent)
            }
        })
        dialog.button("Cancel")

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
            GameEventType.SPAWN_SPAWNER -> "SPAWN Spawner (${event.spawnerType?.name})"
            GameEventType.SPAWN_TELEPORTER -> "SPAWN Teleporter: '${event.teleporterName}'"
            GameEventType.SPAWN_FIRE -> "SPAWN Fire"
            GameEventType.GIVE_WEAPON -> "GIVE WEAPON: ${event.weaponType?.displayName} (Ammo: ${event.ammoAmount ?: "N/A"})"
            GameEventType.FORCE_EQUIP_WEAPON -> "FORCE EQUIP: ${event.weaponType?.displayName}"
            GameEventType.CLEAR_INVENTORY -> "CLEAR PLAYER INVENTORY"
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
        val content = dialog.contentTable
        content.pad(10f).defaults().pad(5f).align(Align.left)

        // --- OBJECTIVE PROPERTY UI ---
        val descField = TextField(existingObjective?.description ?: "", skin)
        val typeSelect = SelectBox<String>(skin).apply {
            items = GdxArray(ConditionType.entries.map { it.name }.toTypedArray())
            selected = existingObjective?.completionCondition?.type?.name ?: ConditionType.ENTER_AREA.name
        }

        // Condition-specific fields
        val targetIdField = TextField(existingObjective?.completionCondition?.targetId ?: "", skin)
        val timerDurationField = TextField(existingObjective?.completionCondition?.timerDuration?.toString() ?: "60.0", skin)
        val itemTypeSelect = SelectBox<String>(skin).apply {
            items = GdxArray(ItemType.entries.map { it.name }.toTypedArray())
            selected = existingObjective?.completionCondition?.itemType?.name ?: ItemType.MONEY_STACK.name
        }
        val itemCountField = TextField(existingObjective?.completionCondition?.itemCount?.toString() ?: "1", skin)
        val itemIdField = TextField(existingObjective?.completionCondition?.itemId ?: "", skin)
        itemIdField.messageText = "Unique Item ID (e.g., 'key_to_vault')"

        // --- DYNAMIC UI TABLES ---
        val targetIdTable = Table(skin).apply { add("Target ID:"); add(targetIdField).width(250f) }
        val areaTable = Table(skin) // This one will have other tables added, so it also needs a skin.
        val timerTable = Table(skin).apply { add("Duration (sec):"); add(timerDurationField).width(80f) }
        val itemTable = Table(skin).apply {
            add("Item Type:"); add(itemTypeSelect).row()
            add("Count:"); add(itemCountField).width(80f).row()
        }
        val specificItemTable = Table(skin).apply {
            add("Specific Item ID:"); add(itemIdField).width(250f)
        }

        // --- Build the areaTable using the class properties ---
        radiusField.text = String.format(Locale.US, "%.2f", existingObjective?.completionCondition?.areaRadius ?: 10.0f)
        areaXField.text = String.format(Locale.US, "%.2f", existingObjective?.completionCondition?.areaCenter?.x ?: 0.0f)
        areaYField.text = String.format(Locale.US, "%.2f", existingObjective?.completionCondition?.areaCenter?.y ?: 0.0f)
        areaZField.text = String.format(Locale.US, "%.2f", existingObjective?.completionCondition?.areaCenter?.z ?: 0.0f)

        val posTable = Table(skin) // MODIFIED: Pass the skin here as well
        posTable.add("X:").padRight(5f); posTable.add(areaXField).width(60f).padRight(10f)
        posTable.add("Y:").padRight(5f); posTable.add(areaYField).width(60f).padRight(10f)
        posTable.add("Z:").padRight(5f); posTable.add(areaZField).width(60f)

        areaTable.add(Label("Area Center:", skin)).left().row()
        areaTable.add(posTable).left().padBottom(5f).row()
        areaTable.add(Label("Radius:", skin)).left().padRight(10f)
        areaTable.add(radiusField).width(80f).left()

        val placeButton = TextButton("Place Area", skin)
        areaTable.add(placeButton).padLeft(15f)

        // --- UI FOR OBJECTIVE-SPECIFIC EVENTS ---
        val objectiveEventsContainer = VerticalGroup().apply { space(5f); wrap(false); align(Align.left) }
        val objectiveEventsScrollPane = ScrollPane(objectiveEventsContainer, skin).apply {
            setFadeScrollBars(false)
            setScrollingDisabled(true, false)
        }

        // This is a local function that refreshes the event list inside THIS dialog
        fun refreshObjectiveEventWidgets() {
            objectiveEventsContainer.clearChildren()
            // Use a safe copy of the list to avoid issues while editing
            val events = existingObjective?.eventsOnStart?.toList() ?: emptyList()
            events.forEach { event ->
                val eventWidget = createEventWidget(event, isStartEvent = false)
                val removeButton = (eventWidget as Table).children.find { it is TextButton && it.text.toString() == "X" } as? TextButton

                removeButton?.clearListeners() // Important: remove the old listener
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
        content.add("Description:"); content.add(descField).width(300f).row()
        content.add("Condition Type:"); content.add(typeSelect).row()
        content.add(targetIdTable).colspan(2).row()
        content.add(areaTable).colspan(2).row()
        content.add(timerTable).colspan(2).row()
        content.add(itemTable).colspan(2).row()
        content.add(specificItemTable).colspan(2).row()

        content.add(Label("--- Events on Objective Start ---", skin, "title")).colspan(2).padTop(15f).row()
        content.add(objectiveEventsScrollPane).colspan(2).growX().height(80f).row()

        val addEventToObjectiveButton = TextButton("Add Event", skin)
        content.add(addEventToObjectiveButton).colspan(2).left().padTop(5f).row()

        // If we are editing an existing objective, load its events
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
                        type = ConditionType.ENTER_AREA,
                        areaCenter = Vector3(
                            areaXField.text.toFloatOrNull() ?: 0f,
                            areaYField.text.toFloatOrNull() ?: 0f,
                            areaZField.text.toFloatOrNull() ?: 0f
                        ),
                        areaRadius = radiusField.text.toFloatOrNull() ?: 10f
                    )
                )

                // MODIFIED: Pass the 'dialog' instance to the UIManager
                uiManager.enterObjectiveAreaPlacementMode(objectiveToPlace, dialog)
            }
        })

        // --- LOGIC TO SHOW/HIDE DYNAMIC FIELDS ---
        fun updateVisibleFields() {
            val selectedType = try { ConditionType.valueOf(typeSelect.selected) } catch (e: Exception) { ConditionType.ENTER_AREA }
            targetIdTable.isVisible = selectedType in listOf(ConditionType.ELIMINATE_TARGET, ConditionType.TALK_TO_NPC, ConditionType.INTERACT_WITH_OBJECT)
            areaTable.isVisible = selectedType == ConditionType.ENTER_AREA
            timerTable.isVisible = selectedType == ConditionType.TIMER_EXPIRES
            itemTable.isVisible = selectedType == ConditionType.COLLECT_ITEM
            specificItemTable.isVisible = selectedType == ConditionType.COLLECT_SPECIFIC_ITEM
            dialog.pack()
        }
        typeSelect.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { updateVisibleFields() } })
        updateVisibleFields()

        // --- MAIN "SAVE" BUTTON FOR THE OBJECTIVE ---
        val saveButton = TextButton("Save", skin)
        saveButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val conditionType = try { ConditionType.valueOf(typeSelect.selected) } catch (e: Exception) { ConditionType.ENTER_AREA }
                val itemType = try { ItemType.valueOf(itemTypeSelect.selected) } catch (e: Exception) { null }

                // MODIFIED: Read the final values from the text fields for the area condition
                val areaCenterVec = Vector3(
                    areaXField.text.toFloatOrNull() ?: 0f,
                    areaYField.text.toFloatOrNull() ?: 0f,
                    areaZField.text.toFloatOrNull() ?: 0f
                )
                val areaRadiusValue = radiusField.text.toFloatOrNull()

                val newCondition = CompletionCondition(
                    type = conditionType,
                    targetId = targetIdField.text.ifBlank { null },
                    areaCenter = if(conditionType == ConditionType.ENTER_AREA) areaCenterVec else null,
                    areaRadius = if(conditionType == ConditionType.ENTER_AREA) areaRadiusValue else null,
                    timerDuration = timerDurationField.text.toFloatOrNull() ?: 60.0f,
                    itemType = itemType,
                    itemCount = itemCountField.text.toIntOrNull() ?: 1,
                    itemId = itemIdField.text.ifBlank { null }
                )

                // If we are editing, copy the existing event list. Otherwise, create a new one.
                val eventsOnStart = existingObjective?.eventsOnStart ?: mutableListOf()

                val newObjective = existingObjective?.copy(
                    description = descField.text.ifBlank { "New Objective" },
                    completionCondition = newCondition
                ) ?: MissionObjective(
                    description = descField.text.ifBlank { "New Objective" },
                    completionCondition = newCondition,
                    eventsOnStart = eventsOnStart
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

        content.add("Reward Type:"); content.add(typeSelect).row()

        val amountTable = Table(skin).apply { add("Amount:"); add(amountField).width(100f) }
        val messageTable = Table(skin).apply { add("Message:"); add(messageField).width(300f) }
        val weaponTable = Table(skin).apply { add("Weapon Type:"); add(weaponTypeSelect) }
        val itemTable = Table(skin).apply { add("Item Type:"); add(itemTypeSelect) }

        content.add(amountTable).colspan(2).row()
        content.add(messageTable).colspan(2).row()
        content.add(weaponTable).colspan(2).row()
        content.add(itemTable).colspan(2).row()

        fun updateVisibleFields() {
            val selectedType = try { RewardType.valueOf(typeSelect.selected) } catch (e: Exception) { RewardType.SHOW_MESSAGE }
            amountTable.isVisible = selectedType == RewardType.GIVE_MONEY || selectedType == RewardType.GIVE_AMMO || selectedType == RewardType.GIVE_ITEM
            messageTable.isVisible = selectedType == RewardType.SHOW_MESSAGE
            weaponTable.isVisible = selectedType == RewardType.GIVE_AMMO
            itemTable.isVisible = selectedType == RewardType.GIVE_ITEM
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

                val newReward = existingReward?.copy(
                    type = rewardType,
                    amount = amountField.text.toIntOrNull() ?: 0,
                    message = messageField.text.ifBlank { "Reward granted." },
                    weaponType = weaponType,
                    itemType = itemType
                ) ?: MissionReward(
                    type = rewardType,
                    amount = amountField.text.toIntOrNull() ?: 0,
                    message = messageField.text.ifBlank { "Reward granted." },
                    weaponType = weaponType,
                    itemType = itemType
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
            GameEventType.START_DIALOG -> "Start Dialog: ${event.dialogId}"
            else -> event.type.name // Fallback for other types
        }
    }

    private fun refreshMissionList() {
        val missions = missionSystem.getAllMissionDefinitions().values
        val missionStrings = GdxArray(missions.map { "${it.id}: ${it.title}" }.toTypedArray())
        missionList.setItems(missionStrings)
        missionSelectBox.items = missionStrings // Also update the mission selector dropdown
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
