package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align

class NPCSelectionUI(
    private val npcSystem: NPCSystem,
    private val skin: Skin,
    private val stage: Stage
) {
    private lateinit var selectionTable: Table
    private lateinit var npcTypeItems: MutableList<NPCSelectionItem>
    private lateinit var behaviorItems: MutableList<BehaviorSelectionItem>
    private lateinit var rotationLabel: Label
    private val loadedTextures = mutableMapOf<String, Texture>()

    // Use specific data classes like in EnemySelectionUI
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

        mainContainer.add(Label("NPC Selection", skin, "title")).padBottom(15f).row()

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
        mainContainer.add(npcTypeContainer).padBottom(20f).row()

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
        mainContainer.add(behaviorContainer).padBottom(10f).row()

        rotationLabel = Label("", skin, "default")
        rotationLabel.setAlignment(Align.center)
        mainContainer.add(rotationLabel).padTop(10f).padBottom(10f).row()

        mainContainer.add(Label("Hold [U] | Mouse Wheel: Change Type | Shift+Wheel: Change Behavior | Q/E: Rotate", skin, "small")).row()

        selectionTable.add(mainContainer)
        stage.addActor(selectionTable)
    }

    // Create a specific function for NPC types
    private fun createNPCTypeItem(npcType: NPCType, isSelected: Boolean): NPCSelectionItem {
        val container = Table()
        val normalBg = createItemBackground(Color(0.3f, 0.3f, 0.35f, 0.9f))
        val selectedBg = createItemBackground(Color(0.2f, 0.8f, 1f, 0.95f)) // NPC Blue

        container.background = if (isSelected) selectedBg else normalBg

        val nameLabel = Label(npcType.displayName, skin)
        nameLabel.setAlignment(Align.center)
        nameLabel.setWrap(true)
        container.add(nameLabel).expand().fill()

        return NPCSelectionItem(container, nameLabel, normalBg, selectedBg)
    }

    // Create a specific function for behaviors
    private fun createBehaviorItem(behavior: NPCBehavior, isSelected: Boolean): BehaviorSelectionItem {
        val container = Table()
        val normalBg = createItemBackground(Color(0.3f, 0.3f, 0.35f, 0.9f))
        val selectedBg = createItemBackground(Color(0.4f, 0.8f, 0.6f, 0.95f)) // Behavior Green

        container.background = if (isSelected) selectedBg else normalBg

        val nameLabel = Label(behavior.displayName, skin)
        nameLabel.setAlignment(Align.center)
        nameLabel.setWrap(true)
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
        }
        // Animate behavior items
        for (i in behaviorItems.indices) {
            val item = behaviorItems[i]
            val isSelected = i == npcSystem.currentBehaviorIndex
            updateItemVisuals(item.container, item.nameLabel, isSelected, item.background, item.selectedBackground)
        }

        // Update rotation label text
        val direction = if (npcSystem.currentRotation == 0f) "Right" else "Left"
        rotationLabel.setText("Initial Facing: [YELLOW]$direction[]")
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

    fun show() { selectionTable.setVisible(true) }
    fun hide() { selectionTable.setVisible(false) }
    fun dispose() { loadedTextures.values.forEach { it.dispose() } }
}
