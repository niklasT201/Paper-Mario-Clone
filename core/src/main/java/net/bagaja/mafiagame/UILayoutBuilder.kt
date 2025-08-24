package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align

/**
 * Builder class for creating UI layouts and components
 */
class UILayoutBuilder(private val skin: Skin) {

    /**
     * Creates the main UI container with modern glassmorphism design
     */
    fun createMainContainer(): Table {
        val mainContainer = Table()
        mainContainer.background = UIDesignSystem.createGlassmorphismBackground()
        mainContainer.pad(30f)
        mainContainer.top()
        return mainContainer
    }

    /**
     * Creates an enhanced title section
     */
    fun createTitleSection(): Table {
        val titleLabel = Label("World Builder", skin, "title-gradient")
        val titleContainer = Table()
        titleContainer.background = UIDesignSystem.createTitleBackground()
        titleContainer.pad(15f, 25f, 15f, 25f)
        titleContainer.add(titleLabel)
        return titleContainer
    }

    /**
     * Creates a tool selection section with enhanced styling
     */
    fun createToolSelectionSection(
        selectedTool: UIManager.Tool,
        onToolSelected: (UIManager.Tool) -> Unit
    ): Pair<Table, MutableList<Table>> {
        val container = Table()

        // Section header
        val toolSectionContainer = Table()
        toolSectionContainer.background = UIDesignSystem.createSectionHeaderBackground()
        toolSectionContainer.pad(12f, 20f, 12f, 20f)

        val toolSectionLabel = Label("🛠 Tools", skin, "section-header")
        val modeLabel = Label("P to cycle BG mode", skin, "hint")

        toolSectionContainer.add(toolSectionLabel).left().expandX()
        toolSectionContainer.add(modeLabel).right()

        container.add(toolSectionContainer).fillX().padBottom(20f).row()

        // Tool container
        val toolContainer = Table()
        toolContainer.background = UIDesignSystem.createToolContainerBackground()
        toolContainer.pad(20f)

        val toolButtons = mutableListOf<Table>()
        val tools = UIManager.Tool.entries

        // Create tool grid
        val toolGrid = Table()
        for (i in tools.indices) {
            val tool = tools[i]
            val toolButton = createToolButton(tool, tool == selectedTool) { onToolSelected(tool) }
            toolButtons.add(toolButton)

            toolGrid.add(toolButton).size(90f, 85f).pad(8f)

            // Create rows of 4 tools
            if ((i + 1) % 4 == 0) {
                toolGrid.row()
            }
        }

        toolContainer.add(toolGrid)
        container.add(toolContainer).padBottom(25f).fillX().row()

        return Pair(container, toolButtons)
    }

    /**
     * Creates an enhanced tool button
     */
    private fun createToolButton(
        tool: UIManager.Tool,
        isSelected: Boolean,
        onClick: () -> Unit
    ): Table {
        val buttonContainer = Table()
        buttonContainer.pad(12f)

        val toolType = UIToolIconFactory.ToolType.valueOf(tool.name)
        val background = if (isSelected) {
            UIDesignSystem.createSelectedToolBackground(UIToolIconFactory.getToolAccentColor(toolType))
        } else {
            UIDesignSystem.createNormalToolBackground()
        }
        buttonContainer.background = background

        // Tool icon
        val iconTexture = UIToolIconFactory.createEnhancedToolIcon(toolType)
        val iconImage = Image(iconTexture)
        buttonContainer.add(iconImage).size(36f, 36f).padBottom(8f).row()

        // Tool name
        val nameLabel = Label(UIToolIconFactory.getToolDisplayName(toolType), skin, "tool-name")
        nameLabel.color = if (isSelected) UIDesignSystem.TEXT_PRIMARY else UIDesignSystem.TEXT_SECONDARY
        buttonContainer.add(nameLabel)

        // Add click listener
        buttonContainer.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                onClick()
            }
        })

        return buttonContainer
    }

    /**
     * Creates an info card section
     */
    fun createInfoCardSection(): Pair<Table, Label> {
        val infoContainer = Table()
        infoContainer.background = UIDesignSystem.createInfoCardBackground()
        infoContainer.pad(15f)

        val placementInfoLabel = Label("", skin, "info-card")
        placementInfoLabel.setWrap(true)
        infoContainer.add(placementInfoLabel).width(320f)

        return Pair(infoContainer, placementInfoLabel)
    }

    /**
     * Creates an enhanced instructions section
     */
    fun createInstructionsSection(): Table {
        val container = Table()

        // Section header
        val instructionSectionContainer = Table()
        instructionSectionContainer.background = UIDesignSystem.createSectionHeaderBackground()
        instructionSectionContainer.pad(12f, 20f, 12f, 20f)

        val instructionSectionLabel = Label("📋 Controls", skin, "section-header")
        instructionSectionContainer.add(instructionSectionLabel).left()

        container.add(instructionSectionContainer).fillX().padBottom(20f).row()

        // Instructions content
        val instructionsContainer = Table()
        instructionsContainer.background = UIDesignSystem.createInstructionsCardBackground()
        instructionsContainer.pad(20f)

        val instructions = """🎯 Tool Selection:
1-6 • Switch tools

⌨️ General Controls:
• Left click → Place/Action
• Right click → Remove
• Right drag → Rotate camera
• Mouse wheel → Zoom/Select
• WASD → Move player
• F1 → Toggle UI
• C → Free camera mode
• Q/E → Camera angle (player)
• R/T → Camera height (player)

🧱 Building Tools:
• B → Block selection mode
• O → Object selection mode
• I → Item selection mode
• M → Car selection mode
• H → House selection mode
• N → Background selection mode
• Hold N + scroll → Select backgrounds

🔧 Advanced Controls:
• F → Fine positioning mode
• D → Debug mode (show invisible)
• Arrow keys → Fine position adjust

💎 Special Features:
• 4×4 grid snapping
• Paper Mario rotation
• Collision detection
• Hold tool key + scroll to select variants"""

        val instructionText = Label(instructions, skin, "instruction-enhanced")
        instructionText.setWrap(true)
        instructionsContainer.add(instructionText).width(340f)

        container.add(instructionsContainer).width(380f).padBottom(25f).fillX().row()
        return container
    }

    /**
     * Creates an enhanced statistics section
     */
    fun createStatsSection(): Pair<Table, MutableMap<String, Label>> {
        val container = Table()

        // Section header
        val statsSectionContainer = Table()
        statsSectionContainer.background = UIDesignSystem.createSectionHeaderBackground()
        statsSectionContainer.pad(12f, 20f, 12f, 20f)

        val statsSectionLabel = Label("📊 Statistics", skin, "section-header")
        statsSectionContainer.add(statsSectionLabel).left()

        container.add(statsSectionContainer).fillX().padBottom(20f).row()

        // Stats content
        val statsContainer = Table()
        statsContainer.background = UIDesignSystem.createStatsCardBackground()
        statsContainer.pad(20f)

        val statsLabels = mutableMapOf<String, Label>()

        // Create individual stat items
        val statItems = listOf(
            "Blocks" to "3" to "🧱",
            "Player" to "Placed" to "👤",
            "Objects" to "0" to "📦",
            "Items" to "0" to "💎",
            "Cars" to "0" to "🚗",
            "Houses" to "0" to "🏠",
            "Backgrounds" to "0" to "🌄",
            "Parallax" to "0" to "🏞️",
            "Interiors" to "0" to "🛋️",
            "Enemies" to "0" to "💀",
            "NPCs" to "0" to "💬"
        )

        for ((data, icon) in statItems) {
            val (key, value) = data
            val statRow = Table()
            statRow.background = UIDesignSystem.createStatRowBackground()
            statRow.pad(8f, 12f, 8f, 12f)

            val iconLabel = Label(icon, skin, "stat-icon")
            val keyLabel = Label(key, skin, "stat-key-enhanced")
            val valueLabel = Label(value, skin, "stat-value-enhanced")
            statsLabels[key] = valueLabel

            statRow.add(iconLabel).padRight(10f)
            statRow.add(keyLabel).left().expandX()
            statRow.add(valueLabel).right()

            statsContainer.add(statRow).fillX().padBottom(6f).row()
        }

        container.add(statsContainer).width(300f).fillX()
        return Pair(container, statsLabels)
    }

    /**
     * Updates tool buttons with smooth animations
     */
    fun updateToolButtons(
        toolButtons: List<Table>,
        selectedTool: UIManager.Tool
    ) {
        val tools = UIManager.Tool.entries.toTypedArray()

        for (i in toolButtons.indices) {
            val toolButton = toolButtons[i]
            val tool = tools[i]
            val isSelected = tool == selectedTool

            val toolType = UIToolIconFactory.ToolType.valueOf(tool.name)
            val targetBackground = if (isSelected) {
                UIDesignSystem.createSelectedToolBackground(UIToolIconFactory.getToolAccentColor(toolType))
            } else {
                UIDesignSystem.createNormalToolBackground()
            }

            // Animate the selection
            toolButton.clearActions()
            toolButton.addAction(
                Actions.sequence(
                    Actions.run { toolButton.background = targetBackground },
                    if (isSelected) {
                        Actions.sequence(
                            Actions.scaleTo(1.05f, 1.05f, 0.15f, Interpolation.bounceOut),
                            Actions.scaleTo(1.0f, 1.0f, 0.1f, Interpolation.smooth)
                        )
                    } else {
                        Actions.scaleTo(1.0f, 1.0f, 0.15f, Interpolation.smooth)
                    }
                )
            )

            // Update label color
            val nameLabel = toolButton.children.get(1) as Label
            nameLabel.color = if (isSelected) UIDesignSystem.TEXT_PRIMARY else UIDesignSystem.TEXT_SECONDARY
        }
    }

    /**
     * Animates placement info updates
     */
    fun animatePlacementInfo(placementInfoLabel: Label) {
        placementInfoLabel.clearActions()
        placementInfoLabel.addAction(
            Actions.sequence(
                Actions.scaleTo(1.05f, 1.05f, 0.1f, Interpolation.bounceOut),
                Actions.scaleTo(1.0f, 1.0f, 0.1f, Interpolation.smooth)
            )
        )
    }

    /**
     * Creates a persistent message label with animations
     */
    fun createPersistentMessageLabel(): Label {
        val persistentMessageLabel = Label("", skin, "title")
        persistentMessageLabel.setAlignment(Align.center)
        persistentMessageLabel.color = Color.YELLOW
        persistentMessageLabel.isVisible = false
        return persistentMessageLabel
    }

    /**
     * Animates persistent message visibility
     */
    fun animatePersistentMessage(persistentMessageLabel: Label, show: Boolean) {
        persistentMessageLabel.clearActions()
        if (show) {
            persistentMessageLabel.isVisible = true
            persistentMessageLabel.addAction(Actions.forever(
                Actions.sequence(
                    Actions.color(Color.WHITE, 0.7f, Interpolation.sine),
                    Actions.color(Color.YELLOW, 0.7f, Interpolation.sine)
                )
            ))
        } else {
            persistentMessageLabel.isVisible = false
        }
    }

    /**
     * Creates FPS counter display
     */
    fun createFpsDisplay(): Pair<Table, Label> {
        val fpsTable = Table()
        fpsTable.setFillParent(true)
        fpsTable.top().right()
        fpsTable.pad(15f)

        val fpsLabel = Label("FPS: 0", skin)
        fpsLabel.color = Color.GREEN

        fpsTable.add(fpsLabel)
        fpsTable.isVisible = false

        return Pair(fpsTable, fpsLabel)
    }
}
