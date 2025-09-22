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

    // Tables to hold the dynamic settings
    private val areaSettingsTable: Table
    private val npcSettingsTable: Table
    private val itemSettingsTable: Table
    private val houseSettingsTable: Table
    private val enemySettingsTable: Table
    private val carSettingsTable: Table
    private val instructions: Label

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

        val settingsStack = Stack(areaSettingsTable, npcSettingsTable, itemSettingsTable, houseSettingsTable, enemySettingsTable, carSettingsTable)
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

        updateVisibleFields()
    }

    private fun applyChanges() {
        val mission = currentMissionDef ?: return

        mission.startTrigger.type = TriggerType.valueOf(triggerTypeSelectBox.selected)
        mission.startTrigger.areaRadius = radiusField.text.toFloatOrNull() ?: TriggerSystem.VISUAL_RADIUS
        mission.startTrigger.targetNpcId = targetNpcIdField.text.ifBlank { null }
        mission.startTrigger.dialogId = dialogIdSelectBox.selected
        mission.startTrigger.itemType = ItemType.entries.find { it.displayName == itemTypeSelectBox.selected }
        mission.startTrigger.itemCount = itemCountField.text.toIntOrNull() ?: 1
        mission.startTrigger.targetHouseId = targetHouseIdField.text.ifBlank { null }
        mission.startTrigger.targetCarId = targetCarIdField.text.ifBlank { null }

        missionSystem.saveMission(mission)
        uiManager.showTemporaryMessage("Trigger for '${mission.title}' saved.")
        hide()
    }

    private fun updateVisibleFields() {
        val selectedType = try { TriggerType.valueOf(triggerTypeSelectBox.selected) } catch (e: Exception) { TriggerType.ON_ENTER_AREA }

        areaSettingsTable.isVisible = (selectedType == TriggerType.ON_ENTER_AREA)
        npcSettingsTable.isVisible = (selectedType == TriggerType.ON_TALK_TO_NPC)
        itemSettingsTable.isVisible = (selectedType == TriggerType.ON_COLLECT_ITEM)
        houseSettingsTable.isVisible = (selectedType == TriggerType.ON_ENTER_HOUSE)
        enemySettingsTable.isVisible = (selectedType == TriggerType.ON_HURT_ENEMY)
        carSettingsTable.isVisible = (selectedType == TriggerType.ON_ENTER_CAR)

        // Hide all tables if it's the "all enemies eliminated" trigger
        if (selectedType == TriggerType.ON_ALL_ENEMIES_ELIMINATED) {
            areaSettingsTable.isVisible = false
            npcSettingsTable.isVisible = false
            itemSettingsTable.isVisible = false
            houseSettingsTable.isVisible = false
            enemySettingsTable.isVisible = false
            carSettingsTable.isVisible = false
        }

        instructions.isVisible = (selectedType == TriggerType.ON_ENTER_AREA)

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
