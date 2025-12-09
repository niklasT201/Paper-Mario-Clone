package net.bagaja.mafiagame

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array as GdxArray
import com.badlogic.gdx.math.Vector3

class TriggerEditorUI(
    private val skin: Skin,
    private val stage: Stage,
    private val missionSystem: MissionSystem,
    private val triggerSystem: TriggerSystem,
    private val sceneManager: SceneManager,
    private val uiManager: UIManager
) {
    private val window: Window = Window("Trigger Editor", skin, "dialog")
    private var currentMissionDef: MissionDefinition? = null

    // UI Elements
    private val missionSelectBox: SelectBox<String>
    private val triggerTypeSelectBox: SelectBox<String>
    private val radiusField: TextField
    private val targetNpcIdField: TextField
    private val dialogIdSelectBox: SelectBox<String>
    private val itemTypeSelectBox: SelectBox<String>
    private val itemCountField: TextField
    private val targetHouseIdField: TextField
    private val targetCarIdField: TextField
    private val shaderTriggerTable: Table // Create a table for it
    private val shaderTriggerSelect: SelectBox<String>

    // Tables to hold the dynamic settings
    private val areaSettingsTable: Table
    private val npcSettingsTable: Table
    private val itemSettingsTable: Table
    private val houseSettingsTable: Table
    private val enemySettingsTable: Table
    private val carSettingsTable: Table
    private val instructions: Label
    private var stayInAreaTable: Table
    private var requiredTimeField: TextField
    private val moneySettingsTable: Table
    private val moneyThresholdField: TextField
    private val showVisualsCheckbox: CheckBox

    init {
        window.setSize(550f, 450f)
        window.setPosition(stage.width / 2f, stage.height - 250f, Align.center)
        window.isMovable = true
        window.padTop(40f)

        val contentTable = Table()
        contentTable.pad(10f).defaults().pad(5f).align(Align.left)

        // --- Mission Selection ---
        missionSelectBox = SelectBox(skin)
        contentTable.add(Label("Mission to Edit:", skin)).padRight(10f)
        contentTable.add(missionSelectBox).growX().row()

        // --- Trigger Type Selection ---
        triggerTypeSelectBox = SelectBox(skin)
        triggerTypeSelectBox.items = GdxArray(TriggerType.entries.map { it.name }.toTypedArray())
        contentTable.add(Label("Trigger Type:", skin)).padRight(10f).padTop(10f)
        contentTable.add(triggerTypeSelectBox).growX().row()

        showVisualsCheckbox = CheckBox(" Show Visual Indicator", skin)
        contentTable.add(showVisualsCheckbox).colspan(2).left().padTop(10f).row()

        // --- Dynamic Settings Panels ---
        areaSettingsTable = Table()
        radiusField = TextField("", skin)
        areaSettingsTable.add(Label("Trigger Radius:", skin)).padRight(10f)
        areaSettingsTable.add(radiusField).width(100f)

        npcSettingsTable = Table()
        targetNpcIdField = TextField("", skin)
        targetNpcIdField.messageText = "Paste NPC or Enemy ID here"
        dialogIdSelectBox = SelectBox(skin)
        npcSettingsTable.add(Label("Target NPC ID:", skin)).left().row()
        npcSettingsTable.add(targetNpcIdField).growX().row()
        npcSettingsTable.add(Label("Dialog ID (Optional):", skin)).left().padTop(5f).row()
        npcSettingsTable.add(dialogIdSelectBox).growX().row()

        itemSettingsTable = Table()
        itemTypeSelectBox = SelectBox(skin)
        itemTypeSelectBox.items = GdxArray(ItemType.entries.map { it.displayName }.toTypedArray())
        itemCountField = TextField("1", skin)
        itemSettingsTable.add(Label("Item Type:", skin)).left()
        itemSettingsTable.add(itemTypeSelectBox).growX().row()
        itemSettingsTable.add(Label("Item Count:", skin)).left().padTop(5f)
        itemSettingsTable.add(itemCountField).width(80f).row()

        houseSettingsTable = Table()
        targetHouseIdField = TextField("", skin)
        targetHouseIdField.messageText = "Paste House ID here"
        houseSettingsTable.add(Label("Target House ID:", skin)).left().row()
        houseSettingsTable.add(targetHouseIdField).growX().row()

        enemySettingsTable = Table()
        enemySettingsTable.add(Label("Target Enemy ID:", skin)).left().row()
        enemySettingsTable.add(targetNpcIdField).growX().row() // Reusing the field is efficient

        carSettingsTable = Table()
        targetCarIdField = TextField("", skin)
        targetCarIdField.messageText = "Paste Car ID here"
        carSettingsTable.add(Label("Target Car ID:", skin)).left().row()
        carSettingsTable.add(targetCarIdField).growX().row()

        stayInAreaTable = Table()
        requiredTimeField = TextField("", skin)
        stayInAreaTable.add(Label("Required Time (sec):", skin)).padRight(10f)
        stayInAreaTable.add(requiredTimeField).width(100f)

        moneySettingsTable = Table()
        moneyThresholdField = TextField("0", skin)
        moneySettingsTable.add(Label("Money Below:", skin)).padRight(10f)
        moneySettingsTable.add(moneyThresholdField).width(100f)

        // Setup the selector
        shaderTriggerSelect = SelectBox(skin)
        shaderTriggerSelect.items = GdxArray(ShaderEffect.entries.map { it.displayName }.toTypedArray())

        shaderTriggerTable = Table()
        shaderTriggerTable.add(Label("Trigger Shader:", skin)).padRight(10f)
        shaderTriggerTable.add(shaderTriggerSelect).growX()

        val settingsStack = Stack(areaSettingsTable, npcSettingsTable, itemSettingsTable, houseSettingsTable, enemySettingsTable, carSettingsTable, stayInAreaTable, moneySettingsTable, shaderTriggerTable)
        contentTable.add(settingsStack).colspan(2).growX().padTop(10f).row()

        instructions = Label("L-Click to set position, Scroll to resize.", skin, "small")
        contentTable.add(instructions).colspan(2).center().padTop(15f).row()

        window.add(contentTable).expand().fill().row()

        val buttonTable = Table()
        val applyButton = TextButton("Apply & Close", skin)
        val resetButton = TextButton("Reset Trigger", skin)
        val closeButton = TextButton("Close", skin)
        buttonTable.padTop(15f)
        buttonTable.add(applyButton).pad(5f)
        buttonTable.add(resetButton).pad(5f)
        buttonTable.add(closeButton).pad(5f)
        window.add(buttonTable).padBottom(10f).row()

        window.isVisible = false
        stage.addActor(window)

        // --- LISTENERS ---
        missionSelectBox.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = loadSelectedMissionTrigger() })
        triggerTypeSelectBox.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = updateVisibleFields() })
        applyButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = applyChanges() })
        resetButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                currentMissionDef?.let {
                    it.startTrigger.areaCenter.set(Vector3.Zero)
                    it.startTrigger.sceneId = "WORLD"
                    missionSystem.saveMission(it)
                    uiManager.showTemporaryMessage("Trigger for '${it.title}' has been reset.")
                }
            }
        })
        closeButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                // MODIFIED: Move the logic from hide() to here
                hide() // First, hide the window.
                // Then, change the tool. This will call updateToolDisplay, but it's safe now.
                uiManager.selectedTool = UIManager.Tool.BLOCK
                uiManager.updateToolDisplay()
            }
        })
    }

    private fun loadSelectedMissionTrigger() {
        val selectedText = missionSelectBox.selected ?: return
        val missionId = selectedText.split(":")[0].trim()
        val mission = missionSystem.getMissionDefinition(missionId) ?: return

        currentMissionDef = mission
        triggerSystem.selectedMissionIdForEditing = missionId
        uiManager.game.lastPlacedInstance = mission.startTrigger

        triggerTypeSelectBox.selected = mission.startTrigger.type.name
        radiusField.text = mission.startTrigger.areaRadius.toString()

        dialogIdSelectBox.items = GdxArray(missionSystem.getAllDialogueIds().toTypedArray())
        targetNpcIdField.text = mission.startTrigger.targetNpcId ?: ""
        dialogIdSelectBox.selected = mission.startTrigger.dialogId

        itemTypeSelectBox.selected = mission.startTrigger.itemType?.displayName ?: ItemType.MONEY_STACK.displayName
        itemCountField.text = mission.startTrigger.itemCount.toString()
        targetHouseIdField.text = mission.startTrigger.targetHouseId ?: ""
        targetCarIdField.text = mission.startTrigger.targetCarId ?: ""

        requiredTimeField.text = mission.startTrigger.requiredTimeInArea.toString()

        moneyThresholdField.text = mission.startTrigger.moneyThreshold.toString()

        shaderTriggerSelect.selected = mission.startTrigger.targetShader?.displayName ?: ShaderEffect.NONE.displayName

        showVisualsCheckbox.isChecked = mission.startTrigger.showVisuals // NEW: Load state
        updateVisibleFields()
    }

    private fun applyChanges() {
        val mission = currentMissionDef ?: return

        mission.startTrigger.type = TriggerType.valueOf(triggerTypeSelectBox.selected)
        mission.startTrigger.requiredTimeInArea = requiredTimeField.text.toFloatOrNull() ?: 10f
        mission.startTrigger.areaRadius = radiusField.text.toFloatOrNull() ?: TriggerSystem.VISUAL_RADIUS
        mission.startTrigger.targetNpcId = targetNpcIdField.text.ifBlank { null }
        mission.startTrigger.dialogId = dialogIdSelectBox.selected

        mission.startTrigger.itemType = ItemType.entries.find { it.displayName == itemTypeSelectBox.selected }
        mission.startTrigger.itemCount = itemCountField.text.toIntOrNull() ?: 1
        mission.startTrigger.targetHouseId = targetHouseIdField.text.ifBlank { null }
        mission.startTrigger.targetCarId = targetCarIdField.text.ifBlank { null }
        mission.startTrigger.showVisuals = showVisualsCheckbox.isChecked // NEW: Save state
        mission.startTrigger.targetShader = ShaderEffect.entries.find { it.displayName == shaderTriggerSelect.selected }

        missionSystem.saveMission(mission)
        uiManager.showTemporaryMessage("Trigger for '${mission.title}' saved.")
        hide()
    }

    private fun updateVisibleFields() {
        val selectedType = try { TriggerType.valueOf(triggerTypeSelectBox.selected) } catch (e: Exception) { TriggerType.ON_ENTER_AREA }

        // Area settings are now used by multiple types
        val isAreaBased = selectedType in listOf(
            TriggerType.ON_ENTER_AREA,
            TriggerType.ON_LEAVE_AREA,
            TriggerType.ON_STAY_IN_AREA_FOR_TIME
        )

        // Show/hide the radius field and placement instructions based on the type.
        areaSettingsTable.isVisible = isAreaBased
        instructions.isVisible = isAreaBased

        // Show the new time field only for its specific trigger type
        stayInAreaTable.isVisible = (selectedType == TriggerType.ON_STAY_IN_AREA_FOR_TIME)

        // NPC settings are only for talking to NPCs.
        npcSettingsTable.isVisible = (selectedType == TriggerType.ON_TALK_TO_NPC)

        // Item settings are only for collecting items.
        itemSettingsTable.isVisible = (selectedType == TriggerType.ON_COLLECT_ITEM)

        // House settings are only for entering a house.
        houseSettingsTable.isVisible = (selectedType == TriggerType.ON_ENTER_HOUSE)

        // Car settings are now used by two types.
        carSettingsTable.isVisible = (selectedType == TriggerType.ON_ENTER_CAR ||
            selectedType == TriggerType.ON_DESTROY_CAR)

        // Money threshold settings are only for the money trigger.
        moneySettingsTable.isVisible = (selectedType == TriggerType.ON_MONEY_BELOW_THRESHOLD)

        shaderTriggerTable.isVisible = selectedType == TriggerType.ON_SHADER_CHANGED

        // Hide all tables if it's the "all enemies eliminated" trigger
        val targetIdLabel = enemySettingsTable.children.first() as Label
        if (selectedType == TriggerType.ON_DESTROY_OBJECT || selectedType == TriggerType.ON_INTERACT) {
            targetIdLabel.setText("Target Object ID:")
            enemySettingsTable.isVisible = true
        } else if (selectedType == TriggerType.ON_HURT_ENEMY) {
            targetIdLabel.setText("Target Enemy ID:")
            enemySettingsTable.isVisible = true
        } else if (selectedType == TriggerType.ON_MISSION_FAILED) {
            targetIdLabel.setText("On Fail of Mission ID:")
            enemySettingsTable.isVisible = true
        } else {
            enemySettingsTable.isVisible = false
        }

        // Hide all specific setting tables if the trigger type doesn't require any.
        if (selectedType == TriggerType.ON_ALL_ENEMIES_ELIMINATED) {
            areaSettingsTable.isVisible = false
            stayInAreaTable.isVisible = false // Also hide the new table
            npcSettingsTable.isVisible = false
            itemSettingsTable.isVisible = false
            houseSettingsTable.isVisible = false
            enemySettingsTable.isVisible = false
            carSettingsTable.isVisible = false
            moneySettingsTable.isVisible = false
        }

        instructions.isVisible = (selectedType == TriggerType.ON_ENTER_AREA ||
            selectedType == TriggerType.ON_LEAVE_AREA ||
            selectedType == TriggerType.ON_STAY_IN_AREA_FOR_TIME)

        window.pack()
    }

    fun setRadiusText(text: String) {
        radiusField.text = text
    }

    fun show() {
        val missionDefs = missionSystem.getAllMissionDefinitions().values
        val missionStrings = GdxArray(missionDefs.map { "${it.id}: ${it.title}" }.toTypedArray())
        missionSelectBox.items = missionStrings
        dialogIdSelectBox.items = GdxArray(missionSystem.getAllDialogueIds().toTypedArray())

        if (missionSelectBox.items.size > 0) {
            missionSelectBox.selectedIndex = 0
            loadSelectedMissionTrigger()
        } else {
            currentMissionDef = null
        }

        window.isVisible = true
        window.toFront()
    }

    fun hide() {
        window.isVisible = false
        stage.unfocusAll()
    }
}
