package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align

data class DialogLine(val speaker: String, val text: String)

data class DialogSequence(val lines: List<DialogLine>, val onComplete: (() -> Unit)? = null)

class DialogSystem {
    private lateinit var stage: Stage
    private lateinit var skin: Skin

    // --- UI Components ---
    private lateinit var mainContainer: Table
    private lateinit var speakerLabel: Label
    private lateinit var textLabel: Label
    private lateinit var continuePrompt: Label

    // --- State Management ---
    private var activeSequence: DialogSequence? = null
    private var currentLineIndex: Int = 0
    private var textRevealProgress: Float = 0f
    private var isLineComplete: Boolean = false

    // --- Configuration ---
    companion object {
        private const val CHARACTERS_PER_SECOND = 40f
        private const val FAST_FORWARD_MULTIPLIER = 5f
    }

    fun initialize(stage: Stage, skin: Skin) {
        this.stage = stage
        this.skin = skin
        setupUI()
    }

    private fun setupUI() {
        // Create a semi-transparent black background for the dialog box
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color(0.1f, 0.1f, 0.1f, 0.85f))
        pixmap.fill()
        val backgroundDrawable = TextureRegionDrawable(Texture(pixmap))
        pixmap.dispose()

        // Main container for the whole dialog box
        mainContainer = Table()
        mainContainer.background = backgroundDrawable
        mainContainer.isVisible = false // Start hidden
        mainContainer.setFillParent(true)

        // --- THIS IS THE CHANGED LINE ---
        mainContainer.bottom().padBottom(100f) // Increased padding from the bottom edge
        // --- END OF CHANGE ---

        // Inner table for content to provide padding
        val contentTable = Table()
        contentTable.pad(20f)

        // Speaker name setup
        val speakerBackgroundPixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        speakerBackgroundPixmap.setColor(Color(0.8f, 0.75f, 0.6f, 1f)) // Cream/Paper color
        speakerBackgroundPixmap.fill()
        val speakerBackground = TextureRegionDrawable(Texture(speakerBackgroundPixmap))
        speakerBackgroundPixmap.dispose()

        val speakerTable = Table()
        speakerTable.background = speakerBackground
        speakerLabel = Label("", skin, "title")
        speakerLabel.color = Color.BLACK
        speakerTable.add(speakerLabel).pad(5f, 15f, 5f, 15f)

        // Dialog text setup
        textLabel = Label("", skin, "default")
        textLabel.setWrap(true)
        textLabel.setAlignment(Align.topLeft)

        // Continue prompt (e.g., a blinking arrow or text)
        continuePrompt = Label("▼", skin, "title")
        continuePrompt.isVisible = false
        val promptContainer = Container(continuePrompt).padRight(15f).padBottom(10f)
        promptContainer.align(Align.bottomRight)

        // Assemble the layout
        contentTable.add(speakerTable).left().padBottom(15f).row()
        contentTable.add(textLabel).expand().fill().left().row()

        mainContainer.add(contentTable).width(Gdx.graphics.width * 0.8f).height(Gdx.graphics.height * 0.3f)
        mainContainer.stack(Table(), promptContainer) // Use stack to overlay the prompt
    }

    /**
     * Starts a new dialog sequence. This is the main entry point for triggering conversations.
     */
    fun startDialog(sequence: DialogSequence) {
        if (isActive()) {
            println("Warning: Tried to start a new dialog while one is already active.")
            return
        }
        activeSequence = sequence
        currentLineIndex = 0
        isLineComplete = false
        textRevealProgress = 0f
        stage.addActor(mainContainer)
        mainContainer.isVisible = true
        mainContainer.color.a = 0f
        mainContainer.addAction(Actions.fadeIn(0.3f, Interpolation.fade))
        displayCurrentLine()
    }

    /**
     * Updates the text reveal effect and handles state transitions.
     * Should be called every frame from your UIManager's render/act method.
     */
    fun update(deltaTime: Float) {
        if (!isActive()) return

        val sequence = activeSequence ?: return
        val currentLine = sequence.lines.getOrNull(currentLineIndex) ?: return

        if (!isLineComplete) {
            val speedMultiplier = if (isFastForwarding()) FAST_FORWARD_MULTIPLIER else 1f
            textRevealProgress += deltaTime * CHARACTERS_PER_SECOND * speedMultiplier

            val charsToShow = textRevealProgress.toInt()
            if (charsToShow >= currentLine.text.length) {
                textLabel.setText(currentLine.text)
                isLineComplete = true
                showContinuePrompt()
            } else {
                textLabel.setText(currentLine.text.substring(0, charsToShow))
            }
        }
    }

    /**
     * Processes user input to advance or skip the dialog.
     * Should be called from your InputHandler when a key is pressed and a dialog is active.
     */
    fun handleInput() {
        // If the line is fully displayed, the next key press advances the dialog
        if (isLineComplete) {
            currentLineIndex++
            displayCurrentLine()
        } else {
            // If the line is still revealing, the key press instantly finishes it
            finishCurrentLine()
        }
    }

    /**
     * Skips the entire current dialog sequence.
     */
    fun skipAll() {
        endDialog()
    }

    private fun displayCurrentLine() {
        isLineComplete = false
        textRevealProgress = 0f
        continuePrompt.isVisible = false
        continuePrompt.clearActions()

        val sequence = activeSequence ?: return
        if (currentLineIndex >= sequence.lines.size) {
            endDialog()
            return
        }

        val line = sequence.lines[currentLineIndex]
        speakerLabel.setText(line.speaker)
        textLabel.setText("") // Clear previous text
    }

    private fun finishCurrentLine() {
        val sequence = activeSequence ?: return
        val line = sequence.lines.getOrNull(currentLineIndex) ?: return
        textRevealProgress = line.text.length.toFloat()
        textLabel.setText(line.text)
        isLineComplete = true
        showContinuePrompt()
    }

    private fun showContinuePrompt() {
        continuePrompt.isVisible = true
        // Add a simple "bobbing" animation to the prompt to make it noticeable
        continuePrompt.clearActions()
        continuePrompt.addAction(Actions.forever(
            Actions.sequence(
                Actions.moveBy(0f, -5f, 0.5f, Interpolation.sine),
                Actions.moveBy(0f, 5f, 0.5f, Interpolation.sine)
            )
        ))
    }

    private fun endDialog() {
        val sequence = activeSequence ?: return
        mainContainer.addAction(Actions.sequence(
            Actions.fadeOut(0.3f, Interpolation.fade),
            Actions.run {
                mainContainer.remove()
                // Execute the callback function after the dialog is completely finished and faded out
                sequence.onComplete?.invoke()
                // Reset state
                activeSequence = null
                currentLineIndex = 0
                isLineComplete = false
            }
        ))
    }

    /**
     * Checks if a dialog is currently active.
     * @return `true` if a dialog is being shown, `false` otherwise.
     */
    fun isActive(): Boolean = activeSequence != null

    /**
     * Checks if the user is holding the fast-forward key.
     */
    private fun isFastForwarding(): Boolean = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)

    fun dispose() {
        // Dispose of the main container's background texture
        (mainContainer.background as? TextureRegionDrawable)?.region?.texture?.dispose()

        // Correctly cast the parent to a Table before accessing its background
        val speakerTable = speakerLabel.parent as? Table
        val speakerBgDrawable = speakerTable?.background as? TextureRegionDrawable
        speakerBgDrawable?.region?.texture?.dispose()
    }
}
