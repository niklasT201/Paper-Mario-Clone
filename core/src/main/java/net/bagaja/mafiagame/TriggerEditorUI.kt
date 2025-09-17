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
    // The main window for the editor
    private val window: Window = Window("Trigger Editor", skin, "dialog") // Using "dialog" style for the nice border
    private var currentMissionDef: MissionDefinition? = null

    // UI Elements
    private val missionSelectBox: SelectBox<String>
    private val triggerTypeSelectBox: SelectBox<String>
    private val radiusField: TextField
    private val targetNpcIdField: TextField
    private val dialogIdField: TextField

    // Tables to hold the dynamic settings
    private val areaSettingsTable: Table
    private val npcSettingsTable: Table

    init {
        window.setSize(550f, 350f)
        window.setPosition(stage.width / 2f, stage.height - 200f, Align.center)
        window.isMovable = true
        window.padTop(40f) // Keep padding for the title bar

        // --- FIX: Create our own content table ---
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
        targetNpcIdField.messageText = "Paste NPC ID here"
        dialogIdField = TextField("", skin)
        dialogIdField.messageText = "e.g., 'mr_big_intro'"
        npcSettingsTable.add(Label("Target NPC ID:", skin)).left().row()
        npcSettingsTable.add(targetNpcIdField).growX().row()
        npcSettingsTable.add(Label("Dialog ID:", skin)).left().padTop(5f).row()
        npcSettingsTable.add(dialogIdField).growX().row()

        val settingsStack = Stack()
        settingsStack.add(areaSettingsTable)
        settingsStack.add(npcSettingsTable)
        contentTable.add(settingsStack).colspan(2).growX().padTop(10f).row()

        // --- Instructions ---
        val instructions = Label("Use L-Click with Trigger Tool to set position in the world.", skin, "small")
        contentTable.add(instructions).colspan(2).center().padTop(15f).row()

        // --- Add the content table to the window ---
        window.add(contentTable).expand().fill().row()

        // --- FIX: Create our own button table ---
        val buttonTable = Table()
        val applyButton = TextButton("Apply & Close", skin)
        val resetButton = TextButton("Reset Trigger", skin)
        val closeButton = TextButton("Close", skin)

        buttonTable.padTop(15f)
        buttonTable.add(applyButton).pad(5f)
        buttonTable.add(resetButton).pad(5f)
        buttonTable.add(closeButton).pad(5f)

        // --- Add the button table to the window ---
        window.add(buttonTable).padBottom(10f).row()

        window.isVisible = false
        stage.addActor(window)

        // --- LISTENERS (Unchanged) ---
        missionSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) = loadSelectedMissionTrigger()
        })
        triggerTypeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) = updateVisibleFields()
        })
        applyButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) = applyChanges()
        })
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
        targetNpcIdField.text = mission.startTrigger.targetNpcId ?: ""
        dialogIdField.text = mission.startTrigger.dialogId ?: ""

        updateVisibleFields()
    }

    private fun applyChanges() {
        val mission = currentMissionDef ?: return

        mission.startTrigger.type = TriggerType.valueOf(triggerTypeSelectBox.selected)
        mission.startTrigger.areaRadius = radiusField.text.toFloatOrNull() ?: TriggerSystem.VISUAL_RADIUS
        mission.startTrigger.targetNpcId = targetNpcIdField.text.ifBlank { null }
        mission.startTrigger.dialogId = dialogIdField.text.ifBlank { null }

        missionSystem.saveMission(mission)
        uiManager.showTemporaryMessage("Trigger for '${mission.title}' saved.")
        hide()
    }

    private fun updateVisibleFields() {
        val selectedType = try { TriggerType.valueOf(triggerTypeSelectBox.selected) } catch (e: Exception) { TriggerType.ON_ENTER_AREA }

        areaSettingsTable.isVisible = (selectedType == TriggerType.ON_ENTER_AREA)
        npcSettingsTable.isVisible = (selectedType == TriggerType.ON_TALK_TO_NPC)

        window.pack()
    }

    fun setRadiusText(text: String) {
        radiusField.text = text
    }

    fun show() {
        val missionDefs = missionSystem.getAllMissionDefinitions().values
        val missionStrings = GdxArray(missionDefs.map { "${it.id}: ${it.title}" }.toTypedArray())
        missionSelectBox.items = missionStrings

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
