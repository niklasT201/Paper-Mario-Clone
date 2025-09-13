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
    private val triggerSystem: TriggerSystem
) {
    private val window: Window = Window("Trigger Placer", skin)
    private val missionSelectBox: SelectBox<String>

    init {
        window.setSize(350f, 150f)
        window.setPosition(stage.width / 2f, stage.height - 160f, Align.center)
        window.padTop(30f)

        missionSelectBox = SelectBox(skin)

        window.add(Label("Mission to Place:", skin)).padRight(10f)
        window.add(missionSelectBox).growX()
        window.row()
        window.add(Label("Left-Click to place at cursor.", skin, "small")).colspan(2).padTop(10f)

        missionSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                // Tell the TriggerSystem which mission is now selected
                val selectedText = missionSelectBox.selected
                val missionId = selectedText.split(":")[0].trim()
                triggerSystem.selectedMissionIdForEditing = missionId
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
        triggerSystem.isEditorVisible = true
    }

    fun hide() {
        window.isVisible = false
        triggerSystem.isEditorVisible = false
        triggerSystem.selectedMissionIdForEditing = null
    }
}
