package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align

class ShaderEffectUI(
    private val skin: Skin,
    private val stage: Stage,
    private val shaderEffectManager: ShaderEffectManager
) {
    private lateinit var window: Window
    private lateinit var currentEffectLabel: Label
    private lateinit var enabledCheckBox: CheckBox
    private lateinit var effectsList: List<TextButton>
    private lateinit var scrollPane: ScrollPane
    private lateinit var statusLabel: Label

    // Color scheme for better visual appeal
    private val activeColor = Color(0.2f, 0.8f, 0.3f, 1f)
    private val inactiveColor = Color(0.6f, 0.6f, 0.6f, 1f)
    private val highlightColor = Color(0.3f, 0.5f, 0.8f, 1f)

    fun initialize() {
        // Create main window
        window = Window("🎨 Visual Effects Manager", skin, "dialog")
        window.isMovable = true
        window.isResizable = false
        window.padTop(50f)

        // Create header section
        createHeaderSection()

        // Create effects selection section
        createEffectsSection()

        // Create controls section
        createControlsSection()

        // Layout everything
        layoutWindow()

        // Position and add to stage
        window.pack()
        window.setPosition(Gdx.graphics.width - window.width - 20, 20f)
        window.isVisible = false

        stage.addActor(window)
    }

    private fun createHeaderSection() {
        // Current effect display with visual indicator
        currentEffectLabel = Label("Default", skin, "title")
        currentEffectLabel.setAlignment(Align.center)

        // Status indicator
        statusLabel = Label("● ENABLED", skin)
        statusLabel.color = activeColor

        // Master toggle
        enabledCheckBox = CheckBox(" Enable Visual Effects", skin)
        enabledCheckBox.isChecked = shaderEffectManager.isEffectsEnabled

        // Add a listener to the checkbox to toggle the feature
        enabledCheckBox.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                shaderEffectManager.toggleEffectsEnabled()
                updateUI()
            }
        })
    }

    private fun createEffectsSection() {
        val effectsTable = Table()
        effectsTable.defaults().fillX().pad(2f)

        // Create buttons for each effect
        val buttons = mutableListOf<TextButton>()

        ShaderEffect.entries.forEach { effect ->
            val button = TextButton(effect.displayName, skin)
            button.left()

            // Add visual indicator for current effect
            button.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    selectEffect(effect)
                }
            })

            buttons.add(button)
            effectsTable.add(button).row()
        }

        effectsList = buttons

        // Create scroll pane for effects list
        scrollPane = ScrollPane(effectsTable, skin)
        scrollPane.setScrollingDisabled(true, false)
        scrollPane.setFadeScrollBars(false)
    }

    private fun createControlsSection() {
        // Quick navigation buttons could be added here if needed
        // For now, we'll keep it simple
    }

    private fun layoutWindow() {
        // Header section
        val headerTable = Table()
        headerTable.add(Label("Current Effect:", skin)).padRight(5f)
        headerTable.add(statusLabel).padRight(15f)
        headerTable.row()
        headerTable.add(currentEffectLabel).colspan(2).padTop(5f).padBottom(15f)

        // Main layout with just spacing instead of separators
        window.add(headerTable).fillX().row()
        window.add(enabledCheckBox).fillX().padTop(10f).padBottom(15f).row()
        window.add(Label("Select Effect:", skin)).fillX().left().padBottom(5f).row()
        window.add(scrollPane).fillX().height(200f).padBottom(15f).row()

        // Control buttons
        val controlsTable = Table()
        controlsTable.defaults().fillX().pad(2f)

        val prevButton = TextButton("◀ Previous", skin)
        prevButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                shaderEffectManager.previousEffect()
                updateUI()
            }
        })

        val nextButton = TextButton("Next ▶", skin)
        nextButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                shaderEffectManager.nextEffect()
                updateUI()
            }
        })

        val closeButton = TextButton("Close", skin)
        closeButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                close()
            }
        })

        controlsTable.add(prevButton).width(80f)
        controlsTable.add(nextButton).width(80f)
        controlsTable.row()
        controlsTable.add(closeButton).colspan(2).padTop(5f)

        window.add(controlsTable).fillX()
    }

    private fun selectEffect(effect: ShaderEffect) {
        // Use reflection to set the effect since there's no direct setter
        // This is a workaround - ideally you'd add a setEffect method to ShaderEffectManager
        val currentEffect = shaderEffectManager.getCurrentEffect()
        if (currentEffect != effect) {
            // Navigate to the desired effect
            val effects = ShaderEffect.entries.toTypedArray()
            val currentIndex = effects.indexOf(currentEffect)
            val targetIndex = effects.indexOf(effect)

            if (targetIndex > currentIndex) {
                repeat(targetIndex - currentIndex) {
                    shaderEffectManager.nextEffect()
                }
            } else {
                repeat(currentIndex - targetIndex) {
                    shaderEffectManager.previousEffect()
                }
            }
        }
        updateUI()
    }

    // This method is called every frame to keep the UI in sync with the system state
    fun update() {
        if (!window.isVisible) return
        updateUI()
    }

    private fun updateUI() {
        val currentEffect = shaderEffectManager.getCurrentEffect()
        val isEnabled = shaderEffectManager.isEffectsEnabled

        // Update current effect label
        currentEffectLabel.setText(currentEffect.displayName)

        // Update status
        statusLabel.setText(if (isEnabled) "● ENABLED" else "● DISABLED")
        statusLabel.color = if (isEnabled) activeColor else inactiveColor

        // Update checkbox
        enabledCheckBox.isChecked = isEnabled

        // Update effect buttons
        effectsList.forEachIndexed { index, button ->
            val effect = ShaderEffect.entries[index]
            if (effect == currentEffect) {
                button.color = highlightColor
                button.setText("▶ ${effect.displayName}")
            } else {
                button.color = Color.WHITE
                button.setText("  ${effect.displayName}")
            }
        }

        // Dim everything if effects are disabled
        val alpha = if (isEnabled) 1f else 0.6f
        effectsList.forEach { it.color.a = alpha }
        scrollPane.color.a = alpha
    }

    // Toggles the visibility of the UI window
    fun toggle() {
        if (window.isVisible) {
            close()
        } else {
            window.isVisible = true
            updateUI()
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
        // Nothing to dispose currently
    }
}
