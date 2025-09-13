package net.bagaja.mafiagame

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array as GdxArray

class TriggerEditorUI(
    private val skin: Skin,
    private val stage: Stage,
    private val missionSystem: MissionSystem,
    private val triggerSystem: TriggerSystem,
    private val sceneManager: SceneManager,
    private val uiManager: UIManager
) {
    private val window: Window = Window("Trigger Placer", skin)
    private val missionSelectBox: SelectBox<String>

    init {
        window.setSize(550f, 200f)
        window.setPosition(stage.width / 2f, stage.height - 160f, Align.center)
        window.padTop(30f)

        val closeButton = TextButton("X", skin, "default")
        window.titleTable.add(closeButton).size(30f, 30f).padLeft(10f)
        closeButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                // When closed, deselect the tool and update the UI
                uiManager.selectedTool = UIManager.Tool.BLOCK
                uiManager.updateToolDisplay()
            }
        })

        missionSelectBox = SelectBox(skin)

        window.add(Label("Mission to Place:", skin)).padRight(10f)
        window.add(missionSelectBox).growX()
        window.row()
        window.add(Label("Left-Click to place at cursor.", skin, "small")).colspan(2).padTop(10f)

        missionSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val selectedText = missionSelectBox.selected ?: return
                val missionId = selectedText.split(":")[0].trim()

                // Tell the TriggerSystem which mission is now selected for editing
                triggerSystem.selectedMissionIdForEditing = missionId

                // Set the selected trigger as the "last placed instance" for fine-tuning
                val mission = missionSystem.getMissionDefinition(missionId)
                if (mission != null) {
                    sceneManager.game.lastPlacedInstance = mission.startTrigger
                    triggerSystem.createOrUpdateEditorCylinder(mission.startTrigger.areaRadius, 10f) // Update visual
                }
            }
        })

        window.isVisible = false
        stage.addActor(window)
    }

    fun show() {
        // Populate the dropdown with missions that don't have triggers yet
        val availableMissions = missionSystem.getAllMissionDefinitions().values
            .filter { it.startTrigger.type == TriggerType.ON_ENTER_AREA } // You can expand this later
            .map { "${it.id}: ${it.title}" }

        missionSelectBox.items = GdxArray(availableMissions.toTypedArray())

        // Auto-select the first one
        if (availableMissions.isNotEmpty()) {
            val firstMissionId = availableMissions[0].split(":")[0].trim()
            triggerSystem.selectedMissionIdForEditing = firstMissionId
        }

        window.isVisible = true
    }

    fun hide() {
        window.isVisible = false
        stage.unfocusAll()
    }
}
