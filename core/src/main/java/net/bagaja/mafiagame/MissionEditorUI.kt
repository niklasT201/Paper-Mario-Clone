package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array as GdxArray
import com.badlogic.gdx.utils.GdxRuntimeException

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
    private val editorTable: Table
    private val missionIdField: TextField
    private val missionTitleField: TextField
    private val missionDescriptionArea: TextArea
    private val prerequisitesField: TextField
    private val scopeSelectBox: SelectBox<String>

    private val objectivesContainer: VerticalGroup
    private val rewardsContainer: VerticalGroup

    // Temporary storage while editing
    private var tempObjectives = mutableListOf<MissionObjective>()
    private var tempRewards = mutableListOf<MissionReward>()

    init {
        window.isModal = true; window.isMovable = true
        window.setSize(Gdx.graphics.width * 1.8f, Gdx.graphics.height * 1.8f)
        window.setPosition(stage.width / 2f, stage.height / 2f, Align.center)
        window.padTop(40f)

        // --- Left Panel ---
        val leftPanel = Table()
        missionList = com.badlogic.gdx.scenes.scene2d.ui.List(skin)
        val listScrollPane = ScrollPane(missionList, skin); listScrollPane.setFadeScrollBars(false)
        val newMissionButton = TextButton("New Mission", skin)
        val deleteMissionButton = TextButton("Delete Selected", skin)
        val buttonTable = Table(); buttonTable.add(newMissionButton).growX().pad(5f); buttonTable.add(deleteMissionButton).growX().pad(5f)
        leftPanel.add(listScrollPane).expand().fill().row(); leftPanel.add(buttonTable).fillX()

        editorTable = Table(); editorTable.pad(10f); editorTable.align(Align.topLeft)
        missionIdField = TextField("", skin).apply { isDisabled = true }
        missionTitleField = TextField("", skin)
        missionDescriptionArea = TextArea("", skin)
        prerequisitesField = TextField("", skin)
        scopeSelectBox = SelectBox(skin); scopeSelectBox.items = GdxArray(MissionScope.entries.map { it.name }.toTypedArray())

        objectivesContainer = VerticalGroup().apply { space(5f); wrap(false); align(Align.topLeft) }
        rewardsContainer = VerticalGroup().apply { space(5f); wrap(false); align(Align.topLeft) }

        editorTable.add(Label("ID:", skin)).left(); editorTable.add(missionIdField).growX().row()
        editorTable.add(Label("Title:", skin)).left(); editorTable.add(missionTitleField).growX().row()
        editorTable.add(Label("Description:", skin)).left().top(); editorTable.add(ScrollPane(missionDescriptionArea, skin)).growX().height(60f).row()
        editorTable.add(Label("Prerequisites (IDs):", skin)).left(); editorTable.add(prerequisitesField).growX().row()
        editorTable.add(Label("Scope:", skin)).left(); editorTable.add(scopeSelectBox).left().row()

        editorTable.add(Label("--- Objectives ---", skin, "title")).colspan(2).padTop(15f).row()
        editorTable.add(ScrollPane(objectivesContainer, skin)).colspan(2).growX().height(200f).row()
        val addObjectiveButton = TextButton("Add Objective", skin)
        editorTable.add(addObjectiveButton).colspan(2).left().padTop(5f).row()

        editorTable.add(Label("--- Rewards ---", skin, "title")).colspan(2).padTop(15f).row()
        editorTable.add(ScrollPane(rewardsContainer, skin)).colspan(2).growX().height(120f).row()
        val addRewardButton = TextButton("Add Reward", skin)
        editorTable.add(addRewardButton).colspan(2).left().padTop(5f).row()

        val editorScrollPane = ScrollPane(editorTable, skin); editorScrollPane.setFadeScrollBars(false)
        val splitPane = SplitPane(leftPanel, editorScrollPane, false, skin); splitPane.splitAmount = 0.25f

        val mainContentTable = Table(); mainContentTable.add(splitPane).expand().fill().row()
        val saveButton = TextButton("Save and Close", skin); val closeButton = TextButton("Close Without Saving", skin)
        val bottomButtonTable = Table(); bottomButtonTable.add(saveButton).pad(10f); bottomButtonTable.add(closeButton).pad(10f)
        mainContentTable.add(bottomButtonTable).padTop(10f)
        window.add(mainContentTable).expand().fill(); window.isVisible = false; stage.addActor(window)

        // --- Listeners ---
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
                refreshMissionList(); missionList.setSelected(newMission.id + ": " + newMission.title)
            }
        })

        deleteMissionButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                currentMissionDef?.id?.let { missionSystem.deleteMission(it); refreshMissionList(); clearEditor() }
            }
        })
        saveButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { applyChanges(); hide() } })
        closeButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { hide() } })
    }

    private fun populateEditor(missionId: String) {
        val mission = missionSystem.getMissionDefinition(missionId) ?: return
        currentMissionDef = mission

        tempObjectives = mission.objectives.toMutableList()
        tempRewards = mission.rewards.toMutableList()

        missionIdField.text = mission.id
        missionTitleField.text = mission.title
        missionDescriptionArea.text = mission.description
        prerequisitesField.text = mission.prerequisites.joinToString(", ")
        scopeSelectBox.selected = mission.scope.name

        refreshObjectiveWidgets()
        refreshRewardWidgets()
    }

    private fun clearEditor() {
        currentMissionDef = null
        missionIdField.text = ""; missionTitleField.text = ""; missionDescriptionArea.text = ""; prerequisitesField.text = ""
        objectivesContainer.clearChildren()
        rewardsContainer.clearChildren()
    }

    private fun applyChanges() {
        val mission = currentMissionDef ?: return
        val newDef = mission.copy(
            title = missionTitleField.text.ifBlank { "Untitled Mission" },
            description = missionDescriptionArea.text,
            prerequisites = prerequisitesField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList(),
            scope = MissionScope.valueOf(scopeSelectBox.selected),
            objectives = tempObjectives,
            rewards = tempRewards
        )
        missionSystem.saveMission(newDef)
        refreshMissionList()
    }

    private fun showObjectiveDialog(existingObjective: MissionObjective?) {
        val dialog = Dialog(if (existingObjective == null) "Add Objective" else "Edit Objective", skin, "dialog")
        dialog.isModal = true
        val content = dialog.contentTable
        content.pad(10f).defaults().pad(5f).align(Align.left)

        // --- UI Elements ---
        val descField = TextField(existingObjective?.description ?: "", skin)
        val typeSelect = SelectBox<String>(skin).apply {
            items = GdxArray(ConditionType.entries.map { it.name }.toTypedArray())
            selected = existingObjective?.completionCondition?.type?.name ?: ConditionType.ENTER_AREA.name
        }

        // Condition-specific fields
        val targetIdField = TextField(existingObjective?.completionCondition?.targetId ?: "", skin)
        val areaRadiusField = TextField(existingObjective?.completionCondition?.areaRadius?.toString() ?: "10.0", skin)
        val timerDurationField = TextField(existingObjective?.completionCondition?.timerDuration?.toString() ?: "60.0", skin) // ADDED
        val itemTypeSelect = SelectBox<String>(skin).apply {
            items = GdxArray(ItemType.entries.map { it.name }.toTypedArray())
            selected = existingObjective?.completionCondition?.itemType?.name ?: ItemType.MONEY_STACK.name
        }
        val itemCountField = TextField(existingObjective?.completionCondition?.itemCount?.toString() ?: "1", skin)

        // --- Layout ---
        content.add("Description:"); content.add(descField).width(300f).row()
        content.add("Condition Type:"); content.add(typeSelect).row()

        // Create tables for each condition type to show/hide them
        val targetIdTable = Table(skin).apply { add("Target ID:"); add(targetIdField).width(250f) }
        val areaTable = Table(skin).apply { add("Area Radius:"); add(areaRadiusField).width(80f) }
        val timerTable = Table(skin).apply { add("Duration (sec):"); add(timerDurationField).width(80f) } // ADDED
        val itemTable = Table(skin).apply {
            add("Item Type:"); add(itemTypeSelect).row()
            add("Count:"); add(itemCountField).width(80f).row()
        }

        content.add(targetIdTable).colspan(2).row()
        content.add(areaTable).colspan(2).row()
        content.add(timerTable).colspan(2).row()
        content.add(itemTable).colspan(2).row()

        // --- Dynamic UI Logic ---
        fun updateVisibleFields() {
            val selectedType = try { ConditionType.valueOf(typeSelect.selected) } catch (e: Exception) { ConditionType.ENTER_AREA }
            targetIdTable.isVisible = selectedType == ConditionType.ELIMINATE_TARGET || selectedType == ConditionType.TALK_TO_NPC || selectedType == ConditionType.INTERACT_WITH_OBJECT
            areaTable.isVisible = selectedType == ConditionType.ENTER_AREA
            timerTable.isVisible = selectedType == ConditionType.TIMER_EXPIRES // ADDED
            itemTable.isVisible = selectedType == ConditionType.COLLECT_ITEM
            dialog.pack()
        }

        typeSelect.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) { updateVisibleFields() }
        })

        // Set initial visibility
        updateVisibleFields()

        // --- Buttons ---
        val saveButton = TextButton("Save", skin)
        saveButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                // Safely create the new condition and objective
                val conditionType = try { ConditionType.valueOf(typeSelect.selected) } catch (e: Exception) { ConditionType.ENTER_AREA }
                val itemType = try { ItemType.valueOf(itemTypeSelect.selected) } catch(e: Exception) { null }

                val newCondition = CompletionCondition(
                    type = conditionType,
                    targetId = targetIdField.text.ifBlank { null },
                    areaRadius = areaRadiusField.text.toFloatOrNull() ?: 10.0f,
                    timerDuration = timerDurationField.text.toFloatOrNull() ?: 60.0f, // ADDED
                    itemType = itemType,
                    itemCount = itemCountField.text.toIntOrNull() ?: 1
                )

                val newObjective = existingObjective?.copy(
                    description = descField.text.ifBlank { "New Objective" },
                    completionCondition = newCondition
                ) ?: MissionObjective(
                    description = descField.text.ifBlank { "New Objective" },
                    completionCondition = newCondition
                )

                if (existingObjective == null) tempObjectives.add(newObjective)
                else {
                    val index = tempObjectives.indexOfFirst { it.id == existingObjective.id }
                    if (index != -1) tempObjectives[index] = newObjective
                }
                refreshObjectiveWidgets()
                dialog.hide()
            }
        })

        dialog.button(saveButton)
        dialog.button("Cancel", false)
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
                override fun changed(event: ChangeEvent?, actor: Actor?) { tempObjectives.remove(objectiveCopy); refreshObjectiveWidgets() }
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
                override fun changed(event: ChangeEvent?, actor: Actor?) { tempRewards.remove(rewardCopy); refreshRewardWidgets() }
            })
            table.add(editButton); table.add(removeButton)
            rewardsContainer.addActor(table)
        }
    }

    private fun refreshMissionList() {
        val missions = missionSystem.getAllMissionDefinitions().values
        val missionStrings = GdxArray(missions.map { "${it.id}: ${it.title}" }.toTypedArray())
        missionList.setItems(missionStrings)
    }

    fun show() {
        refreshMissionList()
        window.isVisible = true
        window.toFront()
    }

    fun hide() {
        window.isVisible = false
        stage.unfocusAll()
    }

    fun isVisible(): Boolean = window.isVisible
}
