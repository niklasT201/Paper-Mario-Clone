package net.bagaja.mafiagame

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align

class VisualSettingsUI(
    skin: Skin,
    private val cameraManager: CameraManager,
    private val uiManager: UIManager
) : Dialog("Visual Settings", skin, "dialog") {

    private val fullscreenCheckbox: CheckBox
    private val letterboxCheckbox: CheckBox

    init {
        isMovable = true
        pad(20f)

        val contentTable = contentTable
        contentTable.align(Align.left).pad(10f)

        // --- Options ---
        fullscreenCheckbox = CheckBox(" Fullscreen Mode", skin)
        letterboxCheckbox = CheckBox(" Enable Letterbox (4:3)", skin)

        contentTable.add(fullscreenCheckbox).left().padBottom(10f).row()
        contentTable.add(letterboxCheckbox).left().padBottom(10f).row()

        // --- Back Button ---
        val backButton = TextButton("Back", skin)
        button(backButton, true) // The 'true' is the result object
        key(Input.Keys.ESCAPE, true) // Also set 'true' as the result for the ESC key

        // --- Listeners ---
        fullscreenCheckbox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val mode = if (fullscreenCheckbox.isChecked) CameraManager.DisplayMode.FULLSCREEN else CameraManager.DisplayMode.WINDOWED
                cameraManager.setDisplayMode(mode)
            }
        })

        letterboxCheckbox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                uiManager.toggleLetterbox()
            }
        })

        pack()
    }

    override fun result(obj: Any?) {
        if (obj == true) {
            uiManager.returnToPauseMenu()
        }
    }

    override fun show(stage: Stage): Dialog {
        fullscreenCheckbox.isChecked = cameraManager.currentDisplayMode == CameraManager.DisplayMode.FULLSCREEN
        letterboxCheckbox.isChecked = uiManager.isLetterboxEnabled()
        super.show(stage)
        setPosition(stage.width / 2f - width / 2, stage.height / 2f - height / 2)
        return this
    }
}
