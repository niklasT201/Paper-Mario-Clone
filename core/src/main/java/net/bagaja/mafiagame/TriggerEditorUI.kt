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
    private val radiusField: TextField

    init {
        window.setSize(550f, 240f)
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
        radiusField = TextField("", skin)

        window.add(Label("Mission to Place:", skin)).padRight(10f)
        window.add(missionSelectBox).growX().row()
        window.add(Label("Trigger Radius:", skin)).padRight(10f).padTop(10f)
        window.add(radiusField).width(100f).row()

        window.add(Label("L-Click: Place | R-Click: Reset", skin, "small")).colspan(2).padTop(10f)
        window.add(Label("F: Fine-Tune | Scroll: Adjust Radius", skin, "small")).colspan(2).row()


        missionSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val selectedText = missionSelectBox.selected ?: return
                val missionId = selectedText.split(":")[0].trim()

                triggerSystem.selectedMissionIdForEditing = missionId

                val mission = missionSystem.getMissionDefinition(missionId)
                if (mission != null) {
                    sceneManager.game.lastPlacedInstance = mission.startTrigger
                    // ADD this line to update the radius field
                    radiusField.text = mission.startTrigger.areaRadius.toString()
                }
            }
        })

        radiusField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                triggerSystem.selectedMissionIdForEditing?.let { missionId ->
                    missionSystem.getMissionDefinition(missionId)?.let { mission ->
                        mission.startTrigger.areaRadius = radiusField.text.toFloatOrNull() ?: TriggerSystem.VISUAL_RADIUS
                    }
                }
            }
        })

        window.isVisible = false
        stage.addActor(window)
    }

    fun setRadiusText(text: String) {
        radiusField.text = text
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
