package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener

class ShaderEffectUI(
    private val skin: Skin,
    private val stage: Stage,
    private val shaderEffectManager: ShaderEffectManager
) {
    private lateinit var window: Window
    private lateinit var effectNameLabel: Label
    private lateinit var enabledCheckBox: CheckBox

    fun initialize() {
        // Create the window using a style that has a title bar
        window = Window("🎨 Visual Effects", skin, "dialog")
        window.isMovable = true
        window.isResizable = false
        window.padTop(40f) // Adjust padding to fit the title

        // Create the UI elements
        effectNameLabel = Label("Effect: None", skin)
        enabledCheckBox = CheckBox(" Effects Enabled", skin)
        enabledCheckBox.isChecked = shaderEffectManager.isEffectsEnabled

        // Add a listener to the checkbox to toggle the feature
        enabledCheckBox.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                shaderEffectManager.toggleEffectsEnabled()
                // The update() method will sync the state, but this ensures immediate feedback
                enabledCheckBox.isChecked = shaderEffectManager.isEffectsEnabled
            }
        })

        // Create a close button
        val closeButton = TextButton("Close", skin)
        closeButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                close()
            }
        })

        // --- Layout the elements inside the window ---
        window.add(Label("Current Effect:", skin)).padRight(10f)
        window.add(effectNameLabel).row()
        window.add(enabledCheckBox).colspan(2).padTop(15f).left().row()
        window.add(closeButton).colspan(2).padTop(10f).center()

        // Set the window's size and initial position
        window.pack()
        window.setPosition(Gdx.graphics.width - window.width - 20, 20f)
        window.isVisible = false // Initially hidden

        stage.addActor(window)
    }

    // This method is called every frame to keep the UI in sync with the system state
    fun update() {
        if (!window.isVisible) return

        // Update the label and checkbox to reflect the current state
        effectNameLabel.setText(shaderEffectManager.getCurrentEffect().displayName)
        enabledCheckBox.isChecked = shaderEffectManager.isEffectsEnabled
    }

    // Toggles the visibility of the UI window
    fun toggle() {
        if (window.isVisible) {
            close() // Use close() method to properly clear focus
        } else {
            window.isVisible = true
        }
    }

    // Add a method to explicitly close the window
    fun close() {
        window.isVisible = false
        // Clear keyboard focus to ensure input handling works properly
        stage.keyboardFocus = null
        stage.scrollFocus = null
    }

    // Add a method to check if the window is visible
    fun isVisible(): Boolean {
        return window.isVisible
    }

    // In case this UI component creates its own disposable resources in the future
    fun dispose() {
        // The skin and stage are managed by UIManager, so nothing to dispose here for now.
    }
}
