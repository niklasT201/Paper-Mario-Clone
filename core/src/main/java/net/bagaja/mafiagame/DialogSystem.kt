package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Scaling

data class DialogChoice(
    val text: String,
    val onSelect: () -> Unit
)

data class DialogLine(
    val speaker: String,
    val text: String,
    val speakerTexturePath: String? = null,
    val choices: List<DialogChoice>? = null,
    val customWidth: Float? = null,
    val customHeight: Float? = null,
    val portraitOffsetX: Float? = null,
    val portraitOffsetY: Float? = null
)

data class DialogSequence(val lines: List<DialogLine>, val onComplete: (() -> Unit)? = null)

class DialogSystem {
    lateinit var uiManager: UIManager
    private lateinit var stage: Stage
    private lateinit var skin: Skin
    lateinit var itemSystem: ItemSystem
    private var isPreviewMode = false

    // --- UI Components ---
    private lateinit var mainContainer: Table // This will now be the root container for portrait + dialog box
    private lateinit var dialogContentTable: Table // The actual box with text
    private lateinit var dialogContentCell: Cell<Table>
    private lateinit var outcomeVisualsContainer: Table
    private lateinit var speakerPortraitImage: Image
    private lateinit var portraitCell: Cell<Image>
    private lateinit var layoutCell: Cell<Table>
    private lateinit var speakerLabel: Label
    private lateinit var textLabel: Label
    private lateinit var textLabelCell: Cell<Label>
    private lateinit var continuePrompt: Label

    // --- State Management ---
    private var activeSequence: DialogSequence? = null
    private var activeOutcome: DialogOutcome? = null // NEW: To store the outcome until the end
    private var currentLineIndex: Int = 0
    private var textRevealProgress: Float = 0f
    private var isLineComplete: Boolean = false
    private var lastSpeakerTexturePath: String? = null

    // --- Caching ---
    private val textureCache = mutableMapOf<String, Texture>()

    private lateinit var choicesContainer: HorizontalGroup
    private var isAwaitingChoice: Boolean = false
    private var isCancelled: Boolean = false
    private var isTransactionalDialog = false

    // --- Configuration ---
    companion object {
        private const val CHARACTERS_PER_SECOND = 40f
        private const val FAST_FORWARD_MULTIPLIER = 5f

        // NPC / Default Portrait Size
        private const val NPC_PORTRAIT_WIDTH = 180f
        private const val NPC_PORTRAIT_HEIGHT = 300f // The FULL height of the source image

        // Player-Specific Portrait Size
        private const val PLAYER_PORTRAIT_WIDTH = 145f
        private const val PLAYER_PORTRAIT_HEIGHT = 260f // Noticeably shorter

        // Shared Settings
        private const val VISIBLE_PORTRAIT_RATIO = 0.8f // Show the top 60% of the portrait
        private const val NPC_PORTRAIT_OVERLAP = -30f
        private const val PLAYER_PORTRAIT_OVERLAP = 5f
        private const val PLAYER_VERTICAL_OFFSET = 25f
    }

    fun initialize(stage: Stage, skin: Skin) {
        this.stage = stage
        this.skin = skin
        setupUI()
    }

    private fun setupUI() {
        // This is the root container that fills the screen and handles final positioning.
        mainContainer = Table()
        mainContainer.setFillParent(true)
        mainContainer.isVisible = false

        mainContainer.bottom()

        // This table will hold the actual content (portrait and dialog box)
        val layoutTable = Table()

        // 1. Setup Speaker Portrait Image
        speakerPortraitImage = Image()
        speakerPortraitImage.isVisible = false

        // 2. Setup Dialog Box Content
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color(0.1f, 0.1f, 0.1f, 0.85f))
        pixmap.fill()
        val backgroundDrawable = TextureRegionDrawable(Texture(pixmap))
        pixmap.dispose()

        dialogContentTable = Table()
        dialogContentTable.background = backgroundDrawable

        val innerContentTable = Table()
        innerContentTable.pad(20f)

        val speakerBackgroundPixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        speakerBackgroundPixmap.setColor(Color(0.8f, 0.75f, 0.6f, 1f))
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
        textLabel.wrap = true
        textLabel.setAlignment(Align.topLeft)

        // Continue prompt (e.g., a blinking arrow or text)
        continuePrompt = Label("▼", skin, "title")
        continuePrompt.isVisible = false
        val promptContainer = Container(continuePrompt).padRight(15f).padBottom(10f)
        promptContainer.align(Align.bottomRight)

        choicesContainer = HorizontalGroup()
        choicesContainer.space(15f)
        choicesContainer.align(Align.right)

        outcomeVisualsContainer = Table()

        // Add the choices container below the text label
        innerContentTable.add(speakerTable).left().padBottom(15f).row()
        innerContentTable.add(outcomeVisualsContainer).growX().padBottom(10f).row()
        textLabelCell = innerContentTable.add(textLabel).expand().fill().left()
        innerContentTable.row()
        innerContentTable.add(choicesContainer).expandX().right().padTop(15f).row()

        dialogContentTable.add(innerContentTable).expand().fill()
        dialogContentTable.stack(Table(), promptContainer)

        // 3. Assemble the layout within the 'layoutTable'
        portraitCell = layoutTable.add(speakerPortraitImage)
            .size(NPC_PORTRAIT_WIDTH, NPC_PORTRAIT_HEIGHT * VISIBLE_PORTRAIT_RATIO) // Use NPC defaults
            .align(Align.bottom)
            .padRight(NPC_PORTRAIT_OVERLAP)

        dialogContentCell = layoutTable.add(dialogContentTable)
            .width(Gdx.graphics.width * 0.7f)
            .minHeight(Gdx.graphics.height * 0.28f)

        layoutCell = mainContainer.add(layoutTable).padBottom(60f)

        stage.addActor(mainContainer)
    }

    fun startDialog(sequence: DialogSequence, outcome: DialogOutcome? = null) {
        if (isActive()) {
            println("Warning: Tried to start a new dialog while one is already active.")
            return
        }

        // Check if the outcome involves a financial transaction.
        isTransactionalDialog = outcome?.type in listOf(
            DialogOutcomeType.SELL_ITEM_TO_PLAYER,
            DialogOutcomeType.BUY_ITEM_FROM_PLAYER,
            DialogOutcomeType.TRADE_ITEM
        )

        isPreviewMode = false
        activeSequence = sequence
        activeOutcome = outcome
        isCancelled = false
        isAwaitingChoice = false // Reset choice state
        currentLineIndex = 0
        isLineComplete = false
        textRevealProgress = 0f
        lastSpeakerTexturePath = null // Reset last speaker
        speakerPortraitImage.isVisible = false // Hide portrait initially

        // Always clear the outcome visuals at the start of any dialog.
        outcomeVisualsContainer.clear()

        mainContainer.isVisible = true
        mainContainer.color.a = 0f
        mainContainer.addAction(Actions.fadeIn(0.3f, Interpolation.fade))
        displayCurrentLine()
    }

    fun previewDialog(lines: List<DialogLine>, index: Int) {
        if (lines.isEmpty() || index < 0 || index >= lines.size) {
            hidePreview()
            return
        }
        isPreviewMode = true
        activeSequence = DialogSequence(lines) // Create a temporary sequence
        currentLineIndex = index
        mainContainer.isVisible = true
        mainContainer.color.a = 1f // No fade-in for preview
        displayCurrentLine()
    }

    fun hidePreview() {
        if (isPreviewMode) {
            mainContainer.isVisible = false
            activeSequence = null
            isPreviewMode = false
        }
    }

    private fun buildOutcomeVisuals(outcome: DialogOutcome) {
        outcomeVisualsContainer.clear()
        outcomeVisualsContainer.defaults().pad(0f, 8f, 0f, 8f).center()

        val iconSize = 48f

        fun getItemImage(itemType: ItemType?): Image? {
            if (itemType == null) return null
            val texture = itemSystem.getTextureForItem(itemType) ?: return null
            return Image(texture).apply { setScaling(Scaling.fit) }
        }

        when (outcome.type) {
            DialogOutcomeType.GIVE_ITEM -> {
                getItemImage(outcome.itemToGive)?.let {
                    val receiveLabel = Label("[GREEN]You receive:", skin)
                    receiveLabel.style.font.data.markupEnabled = true
                    outcomeVisualsContainer.add(receiveLabel)
                    outcomeVisualsContainer.add(it).size(iconSize)
                }
            }
            DialogOutcomeType.SELL_ITEM_TO_PLAYER -> {
                getItemImage(outcome.itemToGive)?.let {
                    val buyLabel = Label("[ORANGE]Buy:", skin)
                    buyLabel.style.font.data.markupEnabled = true
                    outcomeVisualsContainer.add(buyLabel)
                    outcomeVisualsContainer.add(it).size(iconSize)
                    outcome.price?.let { price ->
                        val priceLabel = Label("for [GOLD]$$price", skin)
                        priceLabel.style.font.data.markupEnabled = true
                        outcomeVisualsContainer.add(priceLabel)
                    }
                }
            }
            DialogOutcomeType.BUY_ITEM_FROM_PLAYER -> {
                getItemImage(outcome.requiredItem)?.let {
                    val sellLabel = Label("[ORANGE]Sell:", skin)
                    sellLabel.style.font.data.markupEnabled = true
                    outcomeVisualsContainer.add(sellLabel)
                    outcomeVisualsContainer.add(it).size(iconSize)
                    outcome.price?.let { price ->
                        val priceLabel = Label("for [GOLD]$$price", skin)
                        priceLabel.style.font.data.markupEnabled = true
                        outcomeVisualsContainer.add(priceLabel)
                    }
                }
            }
            DialogOutcomeType.TRADE_ITEM -> {
                val tradeLabel = Label("[ORANGE]Trade:", skin)
                tradeLabel.style.font.data.markupEnabled = true
                outcomeVisualsContainer.add(tradeLabel)
                getItemImage(outcome.requiredItem)?.let { outcomeVisualsContainer.add(it).size(iconSize) }
                outcomeVisualsContainer.add(Label("for", skin))
                getItemImage(outcome.itemToGive)?.let { outcomeVisualsContainer.add(it).size(iconSize) }
            }
            DialogOutcomeType.NONE -> { /* Do nothing, container is already cleared */ }
        }
    }

    fun update(deltaTime: Float) {
        if (!isActive()) return

        val sequence = activeSequence ?: return
        val currentLine = sequence.lines.getOrNull(currentLineIndex) ?: return

        if (!isLineComplete) {
            val speedMultiplier = if (isFastForwarding()) FAST_FORWARD_MULTIPLIER else 1f
            textRevealProgress += deltaTime * CHARACTERS_PER_SECOND * speedMultiplier

            val charsToShow = textRevealProgress.toInt()
            if (charsToShow >= currentLine.text.length) {
                finishCurrentLine()
            } else {
                textLabel.setText(currentLine.text.substring(0, charsToShow))
            }
        }
    }

    fun handleInput() {
        // --- MODIFICATION: Don't advance if waiting for a choice ---
        if (isAwaitingChoice) return

        if (isLineComplete) {
            currentLineIndex++
            displayCurrentLine()
        } else {
            // If the line is still revealing, the key press instantly finishes it
            finishCurrentLine()
        }
    }

    fun skipAll() {
        isCancelled = true
        endDialog()
    }

    private fun displayCurrentLine() {
        isLineComplete = false
        textRevealProgress = 0f
        continuePrompt.isVisible = false
        continuePrompt.clearActions()

        val sequence = activeSequence ?: return
        if (currentLineIndex >= sequence.lines.size) {
            if (isPreviewMode) {
                hidePreview()
            } else {
                endDialog()
            }
            return
        }

        val isLastLine = currentLineIndex == sequence.lines.size - 1

        // If this is the last line of a transactional dialog, show the player's money.
        if (isTransactionalDialog && isLastLine && !isPreviewMode) {
            // We use the uiManager reference to get to the game and then the player's money.
            uiManager.showMoneyUpdate(uiManager.game.playerSystem.getMoney())
        }

        val line = sequence.lines[currentLineIndex]
        speakerLabel.setText(line.speaker)
        textLabel.setText("")

        if (currentLineIndex == sequence.lines.size - 1 && !isPreviewMode) {
            activeOutcome?.let { buildOutcomeVisuals(it) }
        }

        val defaultWidth = Gdx.graphics.width * 0.7f
        val defaultMinHeight = Gdx.graphics.height * 0.28f

        // 1. Width Override
        val targetWidth = line.customWidth ?: defaultWidth
        dialogContentCell.width(targetWidth) // Corrected

        // 2. Height Override
        val targetHeight = line.customHeight ?: defaultMinHeight
        dialogContentCell.minHeight(targetHeight) // Corrected

        // 3. Portrait Offset Override
        val portraitXOffset = line.portraitOffsetX ?: 0f
        val portraitYOffset = line.portraitOffsetY ?: 0f

        // Check the speaker's name and adjust BOTH the portrait width AND the layout padding.
        if (line.speaker.equals("Player", ignoreCase = true)) {
            portraitCell.size(PLAYER_PORTRAIT_WIDTH, PLAYER_PORTRAIT_HEIGHT * VISIBLE_PORTRAIT_RATIO)
            portraitCell.padRight(PLAYER_PORTRAIT_OVERLAP + portraitXOffset)
            portraitCell.padBottom(PLAYER_VERTICAL_OFFSET + portraitYOffset) // Push the player portrait up
            speakerPortraitImage.setScaling(Scaling.fill)

        } else {
            portraitCell.size(NPC_PORTRAIT_WIDTH, NPC_PORTRAIT_HEIGHT * VISIBLE_PORTRAIT_RATIO)
            portraitCell.padRight(NPC_PORTRAIT_OVERLAP + portraitXOffset)
            portraitCell.padBottom(portraitYOffset) // Reset padding for NPCs to keep them at the baseline
            speakerPortraitImage.setScaling(Scaling.fit)
        }

        choicesContainer.clear() // Clear old buttons

        if (line.choices.isNullOrEmpty()) {
            isAwaitingChoice = false
            textLabelCell.padBottom(0f)
        } else {
            isAwaitingChoice = true
            continuePrompt.isVisible = false // Hide the '▼' prompt when choices are shown
            continuePrompt.clearActions()
            textLabelCell.padBottom(20f)

            line.choices.forEach { choice ->
                val button = TextButton(choice.text, skin)
                button.addListener(object : ChangeListener() {
                    override fun changed(event: ChangeEvent?, actor: Actor?) {
                        // When a choice is made, execute its action and then end the dialog
                        choice.onSelect()
                    }
                })
                choicesContainer.addActor(button)
            }
        }
        mainContainer.invalidate() // Tell the layout to recalculate itself with the new padding

        // Update the speaker portrait
        updateSpeakerPortrait(line.speakerTexturePath)
    }

    private fun updateSpeakerPortrait(texturePath: String?) {
        if (texturePath == lastSpeakerTexturePath) {
            return
        }
        lastSpeakerTexturePath = texturePath

        speakerPortraitImage.clearActions()

        if (texturePath == null) {
            speakerPortraitImage.addAction(Actions.sequence(
                Actions.fadeOut(0.2f),
                Actions.visible(false)
            ))
            return
        }

        val texture = textureCache.getOrPut(texturePath) {
            try {
                Texture(Gdx.files.internal(texturePath))
            } catch (e: Exception) {
                println("ERROR: Could not load speaker texture at '$texturePath'. Using placeholder.")
                val pixmap = Pixmap(64, 64, Pixmap.Format.RGBA8888).apply {
                    setColor(Color.MAGENTA)
                    fill()
                }
                Texture(pixmap).also { pixmap.dispose() }
            }
        }

        // Create a TextureRegion that represents the desired visible portion of the texture.
        val regionHeight = (texture.height * VISIBLE_PORTRAIT_RATIO).toInt()
        val visibleRegion = TextureRegion(texture, 0, 0, texture.width, regionHeight)
        speakerPortraitImage.drawable = TextureRegionDrawable(visibleRegion)

        // Animate the portrait sliding in, now based on the visible height
        speakerPortraitImage.isVisible = true
        speakerPortraitImage.color.a = 0f
        speakerPortraitImage.clearActions() // Clear any old animations
        speakerPortraitImage.addAction(Actions.fadeIn(0.4f, Interpolation.fade))
    }

    private fun finishCurrentLine() {
        val sequence = activeSequence ?: return
        val line = sequence.lines.getOrNull(currentLineIndex) ?: return

        textRevealProgress = line.text.length.toFloat()
        textLabel.setText(line.text)
        isLineComplete = true

        if (line.choices.isNullOrEmpty()) {
            showContinuePrompt()
        }
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
                mainContainer.isVisible = false

                // --- ADD THIS CHECK ---
                // Only call onComplete if the dialog wasn't cancelled by the user.
                if (!isCancelled) {
                    sequence.onComplete?.invoke()
                }

                // Reset state
                activeSequence = null
                activeOutcome = null
                currentLineIndex = 0
                isLineComplete = false
                isCancelled = false // Reset for the next dialog
            }
        ))
    }

    fun isActive(): Boolean = activeSequence != null

    private fun isFastForwarding(): Boolean = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)

    fun dispose() {
        (dialogContentTable.background as? TextureRegionDrawable)?.region?.texture?.dispose()
        val speakerTable = speakerLabel.parent as? Table
        val speakerBgDrawable = speakerTable?.background as? TextureRegionDrawable
        speakerBgDrawable?.region?.texture?.dispose()

        // Dispose all cached textures
        textureCache.values.forEach { it.dispose() }
        textureCache.clear()
    }
}
