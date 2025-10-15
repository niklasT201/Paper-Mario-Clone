package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array as GdxArray

class NPCSelectionUI(
    private val npcSystem: NPCSystem,
    private val skin: Skin,
    private val stage: Stage,
    private val dialogueManager: DialogueManager
) {
    private lateinit var selectionTable: Table
    private lateinit var npcTypeItems: MutableList<NPCSelectionItem>
    private lateinit var behaviorItems: MutableList<BehaviorSelectionItem>
    private lateinit var rotationLabel: Label
    private lateinit var npcScrollPane: ScrollPane
    private lateinit var behaviorScrollPane: ScrollPane
    private val loadedTextures = mutableMapOf<String, Texture>()
    private lateinit var canCollectItemsCheckbox: CheckBox
    private lateinit var isHonestCheckbox: CheckBox
    private lateinit var pathStyleSelectBox: SelectBox<String>
    private lateinit var pathStyleTable: Table
    private lateinit var pathIdField: TextField
    private lateinit var pathIdTable: Table

    // Dialog UI properties
    private lateinit var dialogIdSelectBox: SelectBox<String>
    private lateinit var outcomeTypeSelectBox: SelectBox<String>
    private lateinit var giveItemTable: Table
    private lateinit var sellItemTable: Table
    private lateinit var tradeItemTable: Table
    private lateinit var buyItemTable: Table
    private lateinit var outcomeSettingsContainer: Table

    // Outcome fields
    private lateinit var giveItemSelectBox: SelectBox<String>
    private lateinit var giveAmmoField: TextField
    private lateinit var sellItemSelectBox: SelectBox<String>
    private lateinit var sellAmmoField: TextField
    private lateinit var sellPriceField: TextField
    private lateinit var tradeRequiredItemSelectBox: SelectBox<String>
    private lateinit var tradeRewardItemSelectBox: SelectBox<String>
    private lateinit var tradeRewardAmmoField: TextField
    private lateinit var buyItemSelectBox: SelectBox<String>
    private lateinit var buyPriceField: TextField

    private data class NPCSelectionItem(
        val container: Table,
        val nameLabel: Label,
        val background: Drawable,
        val selectedBackground: Drawable
    )

    private data class BehaviorSelectionItem(
        val container: Table,
        val nameLabel: Label,
        val background: Drawable,
        val selectedBackground: Drawable
    )

    fun initialize() {
        setupSelectionUI()
        selectionTable.isVisible = false
    }

    private fun setupSelectionUI() {
        selectionTable = Table()
        selectionTable.setFillParent(true)
        selectionTable.top()
        selectionTable.pad(40f)

        val mainContainer = Table()
        val bgColor = Color(0.1f, 0.1f, 0.15f, 0.9f)
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(bgColor)
        pixmap.fill()
        val bgTexture = Texture(pixmap)
        pixmap.dispose()
        mainContainer.background = TextureRegionDrawable(TextureRegion(bgTexture))
        mainContainer.pad(20f)

        mainContainer.add(Label("NPC Configuration", skin, "title")).padBottom(15f).row()

        // NPC Type Section
        mainContainer.add(Label("NPC Type", skin, "section")).padBottom(10f).row()
        val npcTypeContainer = Table()
        npcTypeItems = mutableListOf()

        NPCType.entries.forEachIndexed { index, npcType ->
            val item = createNPCTypeItem(npcType, index == npcSystem.currentNPCTypeIndex)
            npcTypeItems.add(item)
            if (index > 0) npcTypeContainer.add().width(15f)
            npcTypeContainer.add(item.container).size(100f, 60f)
        }

        // Create scroll pane for NPC types
        npcScrollPane = ScrollPane(npcTypeContainer, skin)
        npcScrollPane.setScrollingDisabled(false, true) // Allow horizontal scrolling only
        npcScrollPane.fadeScrollBars = false
        mainContainer.add(npcScrollPane).width(600f).height(80f).padBottom(20f).row()

        // Behavior Section
        mainContainer.add(Label("NPC Behavior", skin, "section")).padBottom(10f).row()
        val behaviorContainer = Table()
        behaviorItems = mutableListOf()

        NPCBehavior.entries.forEachIndexed { index, behavior ->
            val item = createBehaviorItem(behavior, index == npcSystem.currentBehaviorIndex)
            behaviorItems.add(item)
            if (index > 0) behaviorContainer.add().width(15f)
            behaviorContainer.add(item.container).size(90f, 60f)
        }

        // Create scroll pane for behaviors
        behaviorScrollPane = ScrollPane(behaviorContainer, skin)
        behaviorScrollPane.setScrollingDisabled(false, true) // Allow horizontal scrolling only
        behaviorScrollPane.fadeScrollBars = false
        mainContainer.add(behaviorScrollPane).width(600f).height(80f).padBottom(10f).row()

        val settingsContainer = Table()
        settingsContainer.defaults().padTop(10f)

        pathIdTable = Table()
        pathIdField = TextField("", skin).apply { messageText = "Paste Path Start Node ID" }
        pathIdTable.add(Label("Path Start ID:", skin)).padRight(10f)
        pathIdTable.add(pathIdField).width(250f)
        settingsContainer.add(pathIdTable).row()

        pathStyleTable = Table()
        pathStyleSelectBox = SelectBox<String>(skin)
        pathStyleSelectBox.items = GdxArray(PathFollowingStyle.entries.map { it.displayName }.toTypedArray())
        pathStyleTable.add(Label("Path Style:", skin)).padRight(10f)
        pathStyleTable.add(pathStyleSelectBox).growX()
        settingsContainer.add(pathStyleTable).row()

        // After behavior section, before rotation label
        val optionsTable = Table()
        canCollectItemsCheckbox = CheckBox(" Can Collect Items", skin)
        canCollectItemsCheckbox.isChecked = true // Default
        isHonestCheckbox = CheckBox(" Is Honest (Returns Items)", skin)
        isHonestCheckbox.isChecked = true // Default

        optionsTable.add(canCollectItemsCheckbox).left().padRight(20f)
        optionsTable.add(isHonestCheckbox).left()
        settingsContainer.add(optionsTable).row()

        // --- Standalone Interaction Section ---
        settingsContainer.add(Label("Standalone Interaction", skin, "title")).padTop(15f).padBottom(15f).row()
        val interactionTable = Table()
        interactionTable.defaults().left().pad(2f)

        dialogIdSelectBox = SelectBox(skin)
        interactionTable.add(Label("Dialog ID:", skin)).padRight(10f)
        interactionTable.add(dialogIdSelectBox).growX().row()

        outcomeTypeSelectBox = SelectBox(skin)
        outcomeTypeSelectBox.items = GdxArray(DialogOutcomeType.entries.map { it.displayName }.toTypedArray())
        interactionTable.add(Label("Outcome:", skin)).padRight(10f).padTop(8f)
        interactionTable.add(outcomeTypeSelectBox).growX().row()

        val itemTypeNames = GdxArray(ItemType.entries.map { it.displayName }.toTypedArray())

        // Create individual tables for each outcome type
        giveItemTable = Table(); sellItemTable = Table(); tradeItemTable = Table(); buyItemTable = Table()
        giveItemSelectBox = SelectBox<String>(skin).apply { items = itemTypeNames }
        giveAmmoField = TextField("", skin).apply { messageText = "Default" }
        giveItemTable.add(Label("Item to Give:", skin)).padRight(10f); giveItemTable.add(giveItemSelectBox).row(); giveItemTable.add(Label("Ammo:", skin)).padRight(10f); giveItemTable.add(giveAmmoField).width(80f).row()
        sellItemSelectBox = SelectBox<String>(skin).apply { items = itemTypeNames }
        sellAmmoField = TextField("", skin).apply { messageText = "Default" }
        sellPriceField = TextField("", skin).apply { messageText = "e.g., 100" }
        sellItemTable.add(Label("Item to Sell:", skin)).padRight(10f); sellItemTable.add(sellItemSelectBox).row(); sellItemTable.add(Label("Ammo:", skin)).padRight(10f); sellItemTable.add(sellAmmoField).width(80f).row(); sellItemTable.add(Label("Price:", skin)).padRight(10f); sellItemTable.add(sellPriceField).width(80f).row()
        tradeRequiredItemSelectBox = SelectBox<String>(skin).apply { items = itemTypeNames }
        tradeRewardItemSelectBox = SelectBox<String>(skin).apply { items = itemTypeNames }
        tradeRewardAmmoField = TextField("", skin).apply { messageText = "Default" }
        tradeItemTable.add(Label("Player Gives:", skin)).padRight(10f); tradeItemTable.add(tradeRequiredItemSelectBox).row(); tradeItemTable.add(Label("Player Gets:", skin)).padRight(10f); tradeItemTable.add(tradeRewardItemSelectBox).row(); tradeItemTable.add(Label("Reward Ammo:", skin)).padRight(10f); tradeItemTable.add(tradeRewardAmmoField).width(80f).row()
        buyItemSelectBox = SelectBox<String>(skin).apply { items = itemTypeNames }
        buyPriceField = TextField("", skin).apply { messageText = "e.g., 50" }
        buyItemTable.add(Label("Item to Buy:", skin)).padRight(10f); buyItemTable.add(buyItemSelectBox).row(); buyItemTable.add(Label("Price:", skin)).padRight(10f); buyItemTable.add(buyPriceField).width(80f).row()

        outcomeSettingsContainer = Table()
        interactionTable.add(outcomeSettingsContainer).colspan(2).padTop(10f).row()
        settingsContainer.add(interactionTable).row()

        val settingsScrollPane = ScrollPane(settingsContainer, skin)
        settingsScrollPane.setScrollingDisabled(true, false)
        settingsScrollPane.fadeScrollBars = false
        mainContainer.add(settingsScrollPane).growX().height(350f).row()

        rotationLabel = Label("", skin, "default")
        rotationLabel.setAlignment(Align.center)
        mainContainer.add(rotationLabel).padTop(10f).padBottom(10f).row()

        mainContainer.add(Label("Hold [U] | Mouse Wheel: Change Type | Shift+Wheel: Change Behavior | Q/E: Rotate", skin, "small")).row()

        selectionTable.add(mainContainer)
        stage.addActor(selectionTable)

        outcomeTypeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                updateOutcomeFieldsVisibility()
            }
        })
        selectionTable.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (selectionTable.isVisible) {
                    populateDialogIds()
                }
            }
        })
        updateOutcomeFieldsVisibility() // Set initial state
    }

    private fun populateDialogIds() {
        val currentSelection = dialogIdSelectBox.selected
        val dialogIds = GdxArray<String>()
        dialogIds.add("(None)")
        dialogIds.addAll(*dialogueManager.getAllDialogueIds().toTypedArray())
        dialogIdSelectBox.items = dialogIds
        if (dialogIds.contains(currentSelection, false)) {
            dialogIdSelectBox.selected = currentSelection
        }
    }

    private fun updateOutcomeFieldsVisibility() {
        outcomeSettingsContainer.clear() // Clear the container
        val selectedType = DialogOutcomeType.entries.find { it.displayName == outcomeTypeSelectBox.selected }

        when (selectedType) {
            DialogOutcomeType.GIVE_ITEM -> outcomeSettingsContainer.add(giveItemTable)
            DialogOutcomeType.SELL_ITEM_TO_PLAYER -> outcomeSettingsContainer.add(sellItemTable)
            DialogOutcomeType.TRADE_ITEM -> outcomeSettingsContainer.add(tradeItemTable)
            DialogOutcomeType.BUY_ITEM_FROM_PLAYER -> outcomeSettingsContainer.add(buyItemTable)
            else -> {} // Do nothing for NONE
        }
    }

    fun getSpawnConfig(position: Vector3): NPCSpawnConfig {
        val selectedStyle = PathFollowingStyle.entries.find { it.displayName == pathStyleSelectBox.selected }
            ?: PathFollowingStyle.CONTINUOUS
        val pathId = pathIdField.text.ifBlank { null }
        var standaloneDialog: StandaloneDialog? = null
        val selectedDialogId = dialogIdSelectBox.selected
        if (selectedDialogId != null && selectedDialogId != "(None)") {
            val outcomeType = DialogOutcomeType.entries.find { it.displayName == outcomeTypeSelectBox.selected } ?: DialogOutcomeType.NONE
            val outcome = when (outcomeType) {
                DialogOutcomeType.GIVE_ITEM -> DialogOutcome(type = outcomeType, itemToGive = ItemType.entries.find { it.displayName == giveItemSelectBox.selected }, ammoToGive = giveAmmoField.text.toIntOrNull())
                DialogOutcomeType.SELL_ITEM_TO_PLAYER -> DialogOutcome(type = outcomeType, itemToGive = ItemType.entries.find { it.displayName == sellItemSelectBox.selected }, ammoToGive = sellAmmoField.text.toIntOrNull(), price = sellPriceField.text.toIntOrNull())
                DialogOutcomeType.BUY_ITEM_FROM_PLAYER -> DialogOutcome(type = outcomeType, requiredItem = ItemType.entries.find { it.displayName == buyItemSelectBox.selected }, price = buyPriceField.text.toIntOrNull())
                DialogOutcomeType.TRADE_ITEM -> DialogOutcome(type = outcomeType, requiredItem = ItemType.entries.find { it.displayName == tradeRequiredItemSelectBox.selected }, itemToGive = ItemType.entries.find { it.displayName == tradeRewardItemSelectBox.selected }, ammoToGive = tradeRewardAmmoField.text.toIntOrNull())
                else -> DialogOutcome(type = DialogOutcomeType.NONE)
            }
            standaloneDialog = StandaloneDialog(selectedDialogId, outcome)
        }
        return NPCSpawnConfig(
            npcType = npcSystem.currentSelectedNPCType,
            behavior = npcSystem.currentSelectedBehavior,
            position = position,
            canCollectItems = canCollectItemsCheckbox.isChecked,
            isHonest = isHonestCheckbox.isChecked,
            pathFollowingStyle = selectedStyle,
            assignedPathId = pathId,
            standaloneDialog = standaloneDialog
        )
    }

    private fun createNPCTypeItem(npcType: NPCType, isSelected: Boolean): NPCSelectionItem {
        val container = Table()
        val normalBg = createItemBackground(Color(0.3f, 0.3f, 0.35f, 0.9f))
        val selectedBg = createItemBackground(Color(0.2f, 0.8f, 1f, 0.95f)) // NPC Blue

        container.background = if (isSelected) selectedBg else normalBg

        val nameLabel = Label(npcType.displayName, skin)
        nameLabel.setAlignment(Align.center)
        nameLabel.wrap = true
        container.add(nameLabel).expand().fill()

        return NPCSelectionItem(container, nameLabel, normalBg, selectedBg)
    }

    private fun createBehaviorItem(behavior: NPCBehavior, isSelected: Boolean): BehaviorSelectionItem {
        val container = Table()
        val normalBg = createItemBackground(Color(0.3f, 0.3f, 0.35f, 0.9f))
        val selectedBg = createItemBackground(Color(0.4f, 0.8f, 0.6f, 0.95f)) // Behavior Green

        container.background = if (isSelected) selectedBg else normalBg

        val nameLabel = Label(behavior.displayName, skin)
        nameLabel.setAlignment(Align.center)
        nameLabel.wrap = true
        container.add(nameLabel).expand().fill()

        return BehaviorSelectionItem(container, nameLabel, normalBg, selectedBg)
    }

    private fun createItemBackground(color: Color): Drawable {
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()
        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    fun update() {
        // Animate NPC type items
        for (i in npcTypeItems.indices) {
            val item = npcTypeItems[i]
            val isSelected = i == npcSystem.currentNPCTypeIndex
            updateItemVisuals(item.container, item.nameLabel, isSelected, item.background, item.selectedBackground)

            // Auto-scroll to the selected NPC type
            if (isSelected) {
                scrollToSelectedItem(npcScrollPane, i, npcTypeItems.size, 115f) // 100f item width + 15f spacing
            }
        }

        // Animate behavior items and scroll to selected
        for (i in behaviorItems.indices) {
            val item = behaviorItems[i]
            val isSelected = i == npcSystem.currentBehaviorIndex
            updateItemVisuals(item.container, item.nameLabel, isSelected, item.background, item.selectedBackground)

            // Auto-scroll to the selected behavior
            if (isSelected) {
                scrollToSelectedItem(behaviorScrollPane, i, behaviorItems.size, 105f) // 90f item width + 15f spacing
            }
        }

        // Show/hide the path style dropdown
        val isPathFollower = npcSystem.currentSelectedBehavior == NPCBehavior.PATH_FOLLOWER
        pathStyleTable.isVisible = isPathFollower
        pathIdTable.isVisible = isPathFollower

        // Update rotation label text
        val direction = if (npcSystem.currentRotation == 0f) "Right" else "Left"
        rotationLabel.setText("Initial Facing: [YELLOW]$direction[]")
    }

    private fun scrollToSelectedItem(scrollPane: ScrollPane, selectedIndex: Int, totalItems: Int, itemWidth: Float) {
        if (totalItems <= 1) return

        // Calculate the position of the selected item
        val selectedItemPosition = selectedIndex * itemWidth
        val scrollPaneWidth = scrollPane.width
        val totalContentWidth = totalItems * itemWidth

        // Only scroll if content is wider than the scroll pane
        if (totalContentWidth > scrollPaneWidth) {
            // Calculate the center position for the selected item
            val targetScrollX = selectedItemPosition - (scrollPaneWidth / 2f) + (itemWidth / 2f)

            // Clamp the scroll position to valid bounds
            val maxScrollX = totalContentWidth - scrollPaneWidth
            val clampedScrollX = targetScrollX.coerceIn(0f, maxScrollX)

            // Instantly scroll to the target position
            scrollPane.scrollX = clampedScrollX
        }
    }

    private fun updateItemVisuals(container: Table, label: Label, isSelected: Boolean, normalBg: Drawable, selectedBg: Drawable) {
        val targetScale = if (isSelected) 1.1f else 1.0f
        val targetColor = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
        container.clearActions()
        container.addAction(
            Actions.parallel(
                Actions.scaleTo(targetScale, targetScale, 0.2f, Interpolation.smooth),
                Actions.run {
                    container.background = if (isSelected) selectedBg else normalBg
                    label.color = targetColor
                }
            )
        )
    }

    fun show() {
        populateDialogIds()
        selectionTable.isVisible = true
        update()
    }

    fun hide() {
        selectionTable.isVisible = false
        stage.unfocusAll()
    }

    fun dispose() {
        loadedTextures.values.forEach { it.dispose() }
    }
}
