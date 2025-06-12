package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.viewport.ScreenViewport

class UIManager(
    private val blockSystem: BlockSystem,
    private val objectSystem: ObjectSystem,
    private val itemSystem: ItemSystem,
    private val carSystem: CarSystem,
    private val houseSystem: HouseSystem,
    private val backgroundSystem: BackgroundSystem,
) {
    private lateinit var stage: Stage
    private lateinit var skin: Skin
    private lateinit var blockSelectionUI: BlockSelectionUI
    private lateinit var objectSelectionUI: ObjectSelectionUI
    private lateinit var itemSelectionUI: ItemSelectionUI
    private lateinit var carSelectionUI: CarSelectionUI
    private lateinit var houseSelectionUI: HouseSelectionUI
    private lateinit var backgroundSelectionUI: BackgroundSelectionUI
    private lateinit var lightSourceUI: LightSourceUI
    private lateinit var mainTable: Table
    private lateinit var toolButtons: MutableList<Table>
    private lateinit var statsLabels: MutableMap<String, Label>

    var isUIVisible = true
        private set
    var selectedTool = Tool.BLOCK

    enum class Tool {
        BLOCK, PLAYER, OBJECT, ITEM, CAR, HOUSE, BACKGROUND
    }

    fun initialize() {
        stage = Stage(ScreenViewport())
        skin = createSkin()

        setupMainUI()

        blockSelectionUI = BlockSelectionUI(blockSystem, skin, stage)
        blockSelectionUI.initialize()

        // Initialize object selection UI
        objectSelectionUI = ObjectSelectionUI(objectSystem, skin, stage)
        objectSelectionUI.initialize()

        // Initialize item selection UI
        itemSelectionUI = ItemSelectionUI(itemSystem, skin, stage)
        itemSelectionUI.initialize()

        // Initialize car selection UI
        carSelectionUI = CarSelectionUI(carSystem, skin, stage)
        carSelectionUI.initialize()

        // Initialize light selection UI
        lightSourceUI = LightSourceUI(skin, stage)
        lightSourceUI.initialize()

        // Initialize house selection UI
        houseSelectionUI = HouseSelectionUI(houseSystem, skin, stage)
        houseSelectionUI.initialize()

        // Initialize background selection UI
        backgroundSelectionUI = BackgroundSelectionUI(backgroundSystem, skin, stage)
        backgroundSelectionUI.initialize()

        // Set initial visibility for the main UI panel
        mainTable.isVisible = isUIVisible
    }

    private fun setupMainUI() {
        mainTable = Table()
        mainTable.setFillParent(true)
        mainTable.top().left()
        mainTable.pad(20f)

        // Create main container with modern background
        val mainContainer = Table()
        mainContainer.background = createModernPanelBackground()
        mainContainer.pad(25f)

        // Title with modern styling
        val titleLabel = Label("World Builder", skin, "title")
        mainContainer.add(titleLabel).padBottom(25f).row()

        // Tool selection section
        createToolSelectionSection(mainContainer)

        // Instructions section
        createInstructionsSection(mainContainer)

        // Stats section
        createStatsSection(mainContainer)

        mainTable.add(mainContainer).top().left()
        stage.addActor(mainTable)
    }

    private fun createToolSelectionSection(container: Table) {
        // Section title
        val toolSectionLabel = Label("Tools", skin, "section")
        container.add(toolSectionLabel).padBottom(15f).left().row()

        // Tool buttons container
        val toolContainer = Table()
        toolContainer.pad(10f)

        toolButtons = mutableListOf()
        val tools = Tool.values()

        for (i in tools.indices) {
            val tool = tools[i]
            val toolButton = createToolButton(tool, tool == selectedTool)
            toolButtons.add(toolButton)

            toolContainer.add(toolButton).size(80f, 70f).pad(5f)
            if (i < tools.size - 1) {
                toolContainer.add().width(10f) // Spacer
            }
        }

        container.add(toolContainer).padBottom(20f).left().row()
    }

    private fun createToolButton(tool: Tool, isSelected: Boolean): Table {
        val buttonContainer = Table()
        buttonContainer.pad(8f)

        // Set background based on selection
        val normalBg = createToolButtonBackground(Color(0.25f, 0.25f, 0.3f, 0.9f))
        val selectedBg = createToolButtonBackground(Color(0.4f, 0.6f, 0.8f, 0.95f))
        buttonContainer.background = if (isSelected) selectedBg else normalBg

        // Tool icon (using colored squares as placeholders)
        val iconTexture = createToolIcon(tool)
        val iconImage = Image(iconTexture)
        buttonContainer.add(iconImage).size(30f, 30f).padBottom(5f).row()

        // Tool name
        val nameLabel = Label(getToolDisplayName(tool), skin, "small")
        nameLabel.color = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
        buttonContainer.add(nameLabel)

        return buttonContainer
    }

    private fun createInstructionsSection(container: Table) {
        // Section title
        val instructionSectionLabel = Label("Controls", skin, "section")
        container.add(instructionSectionLabel).padBottom(15f).left().row()

        // Instructions container with modern background
        val instructionsContainer = Table()
        instructionsContainer.background = createInstructionsBackground()
        instructionsContainer.pad(15f)

        val instructions = """Tool Selection:
        1-6 - Switch tools

        Controls:
        • Left click - Place/Action
        • Right click - Remove
        • Right drag - Rotate camera
        • Mouse wheel - Zoom/Select
        • WASD - Move player
        • F1 - Toggle UI // CHANGE: Was H
        • B - Block selection mode
        • O - Object selection mode
        • I - Item selection mode
        • M - Car selection mode // CHANGE: The key is M in InputHandler
        • H - House selection mode // ADD THIS
        • C - Free camera mode
        • Q/E - Camera angle (player)
        • R/T - Camera height (player)
        - N - Background selection mode
        - Hold N + scroll for backgrounds

        Object Controls:
        • F - Fine positioning mode
        • D - Debug mode (show invisible)
        • Arrow keys - Fine position adjust

        Item Controls:
        • Hold I + scroll - Select items
        • Left click - Place selected item

        Car Controls:
        • Hold R + scroll - Select cars
        • Left click - Place selected car
        • F - Fine positioning mode
        • Cars are 2D billboards

        Features:
        • 4x4 grid snapping
        • Paper Mario rotation
        • Collision detection
        • Hold B + scroll for blocks
        • Hold O + scroll for objects
        • Hold I + scroll for items
        • Hold M + scroll for cars
        • Hold H + scroll for houses"""

        val instructionText = Label(instructions, skin, "instruction")
        instructionText.setWrap(true)
        instructionsContainer.add(instructionText).width(280f)

        container.add(instructionsContainer).width(320f).padBottom(20f).left().row()
    }

    private fun createStatsSection(container: Table) {
        // Section title
        val statsSectionLabel = Label("Statistics", skin, "section")
        container.add(statsSectionLabel).padBottom(15f).left().row()

        // Stats container
        val statsContainer = Table()
        statsContainer.background = createStatsBackground()
        statsContainer.pad(15f)

        statsLabels = mutableMapOf()

        // Create individual stat items - Updated to include cars
        val statItems = listOf(
            "Blocks" to "3",
            "Player" to "Placed",
            "Objects" to "0",
            "Items" to "0",
            "Cars" to "0",
            "Houses" to "0",
            "Backgrounds" to "0"
        )

        for ((key, value) in statItems) {
            val statRow = Table()

            val keyLabel = Label("$key:", skin, "stat-key")
            val valueLabel = Label(value, skin, "stat-value")
            statsLabels[key] = valueLabel

            statRow.add(keyLabel).left().padRight(10f)
            statRow.add(valueLabel).left().expandX()

            statsContainer.add(statRow).fillX().padBottom(5f).row()
        }

        container.add(statsContainer).width(250f).left()
    }

    private fun createModernPanelBackground(): Drawable {
        val pixmap = Pixmap(200, 400, Pixmap.Format.RGBA8888)

        // Create gradient background
        for (y in 0 until 400) {
            val alpha = 0.88f + (y / 400f) * 0.07f
            pixmap.setColor(0.08f, 0.08f, 0.12f, alpha)
            pixmap.drawLine(0, y, 199, y)
        }

        // Add subtle border
        pixmap.setColor(0.3f, 0.3f, 0.4f, 0.6f)
        pixmap.drawRectangle(0, 0, 200, 400)

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createToolButtonBackground(color: Color): Drawable {
        val pixmap = Pixmap(80, 70, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()

        // Add border
        pixmap.setColor(color.r + 0.1f, color.g + 0.1f, color.b + 0.1f, color.a)
        pixmap.drawRectangle(0, 0, 80, 70)

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createInstructionsBackground(): Drawable {
        val pixmap = Pixmap(300, 200, Pixmap.Format.RGBA8888)
        pixmap.setColor(0.12f, 0.12f, 0.18f, 0.85f)
        pixmap.fill()

        // Add border
        pixmap.setColor(0.25f, 0.25f, 0.35f, 0.8f)
        pixmap.drawRectangle(0, 0, 300, 200)

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createStatsBackground(): Drawable {
        val pixmap = Pixmap(250, 100, Pixmap.Format.RGBA8888)
        pixmap.setColor(0.15f, 0.15f, 0.2f, 0.9f)
        pixmap.fill()

        // Add border
        pixmap.setColor(0.3f, 0.4f, 0.5f, 0.7f)
        pixmap.drawRectangle(0, 0, 250, 100)

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createToolIcon(tool: Tool): TextureRegion {
        val pixmap = Pixmap(30, 30, Pixmap.Format.RGBA8888)

        val color = when (tool) {
            Tool.BLOCK -> Color(0.4f, 0.8f, 0.2f, 1f)
            Tool.PLAYER -> Color(0.8f, 0.6f, 0.2f, 1f)
            Tool.OBJECT -> Color(0.6f, 0.4f, 0.8f, 1f)
            Tool.ITEM -> Color(1f, 0.8f, 0.2f, 1f) // Golden color for items
            Tool.CAR -> Color(0.9f, 0.3f, 0.6f, 1f) // Pink/magenta color for cars
            Tool.HOUSE -> Color(0.6f, 0.8f, 0.1f, 1f)
            Tool.BACKGROUND -> Color(1f, 0.8f, 0.3f, 1f)
        }

        pixmap.setColor(color)
        pixmap.fill()

        // Add simple pattern
        pixmap.setColor(color.r * 0.7f, color.g * 0.7f, color.b * 0.7f, 1f)
        when (tool) {
            Tool.BLOCK -> {
                // Grid pattern for blocks
                for (i in 0 until 30 step 6) {
                    pixmap.drawLine(i, 0, i, 29)
                    pixmap.drawLine(0, i, 29, i)
                }
            }
            Tool.PLAYER -> {
                // Simple figure for player
                pixmap.fillCircle(15, 20, 8)
                pixmap.fillRectangle(11, 8, 8, 12)
            }
            Tool.OBJECT -> {
                // Plus sign for objects
                pixmap.fillRectangle(12, 5, 6, 20)
                pixmap.fillRectangle(5, 12, 20, 6)
            }
            Tool.ITEM -> {
                // Diamond shape for items
                pixmap.fillTriangle(15, 5, 8, 15, 15, 15)
                pixmap.fillTriangle(15, 5, 15, 15, 22, 15)
                pixmap.fillTriangle(8, 15, 15, 25, 22, 15)
            }
            Tool.CAR -> {
                // Car shape
                pixmap.fillRectangle(5, 12, 20, 8) // Main body
                pixmap.fillRectangle(8, 8, 14, 6) // Top/roof
                pixmap.fillCircle(10, 22, 2) // Front wheel
                pixmap.fillCircle(20, 22, 2) // Rear wheel
            }
            Tool.HOUSE -> {
                pixmap.fillRectangle(5, 12, 20, 8) // Main body
            }
            Tool.BACKGROUND -> {
                // Landscape/background icon
                pixmap.fillRectangle(5, 25, 20, 5) // Ground
                pixmap.fillTriangle(8, 25, 15, 15, 22, 25) // Mountain
                pixmap.setColor(color.r * 0.9f, color.g * 0.9f, color.b * 0.3f, 1f)
                pixmap.fillCircle(35, 18, 4) // Sun
            }
        }

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegion(texture)
    }

    private fun getToolDisplayName(tool: Tool): String {
        return when (tool) {
            Tool.BLOCK -> "Block"
            Tool.PLAYER -> "Player"
            Tool.OBJECT -> "Object"
            Tool.ITEM -> "Item"
            Tool.CAR -> "Car"
            Tool.HOUSE -> "House"
            Tool.BACKGROUND -> "Background"
        }
    }

    private fun createSkin(): Skin {
        return try {
            val loadedSkin = Skin(Gdx.files.internal("ui/uiskin.json"))

            // Try to load and set custom font
            try {
                val customFont = BitmapFont(Gdx.files.internal("ui/default.fnt"))
                loadedSkin.add("default-font", customFont, BitmapFont::class.java)

                // Update existing styles to use the new font
                loadedSkin.get(Label.LabelStyle::class.java).font = customFont

                // Only update TextButton style if it exists
                try {
                    loadedSkin.get(TextButton.TextButtonStyle::class.java).font = customFont
                } catch (e: Exception) {
                    // TextButton style might not exist
                }

                // Add custom label styles
                addCustomLabelStyles(loadedSkin, customFont)

                println("Custom font loaded successfully from ui/default.fnt")
            } catch (e: Exception) {
                println("Could not load custom font: ${e.message}")
                addCustomLabelStyles(loadedSkin, loadedSkin.get(BitmapFont::class.java))
            }

            loadedSkin
        } catch (e: Exception) {
            println("Could not load UI skin, using default")
            createDefaultSkin()
        }
    }

    private fun addCustomLabelStyles(skin: Skin, font: BitmapFont) {
        // Title style
        val titleStyle = Label.LabelStyle()
        titleStyle.font = font
        titleStyle.fontColor = Color(0.9f, 0.9f, 1f, 1f)
        skin.add("title", titleStyle)

        // Section header style
        val sectionStyle = Label.LabelStyle()
        sectionStyle.font = font
        sectionStyle.fontColor = Color(0.8f, 0.9f, 1f, 1f)
        skin.add("section", sectionStyle)

        // Small text style
        val smallStyle = Label.LabelStyle()
        smallStyle.font = font
        smallStyle.fontColor = Color(0.8f, 0.8f, 0.8f, 1f)
        skin.add("small", smallStyle)

        // Instruction text style
        val instructionStyle = Label.LabelStyle()
        instructionStyle.font = font
        instructionStyle.fontColor = Color(0.85f, 0.85f, 0.9f, 1f)
        skin.add("instruction", instructionStyle)

        // Stats key style
        val statKeyStyle = Label.LabelStyle()
        statKeyStyle.font = font
        statKeyStyle.fontColor = Color(0.7f, 0.8f, 0.9f, 1f)
        skin.add("stat-key", statKeyStyle)

        // Stats value style
        val statValueStyle = Label.LabelStyle()
        statValueStyle.font = font
        statValueStyle.fontColor = Color(0.9f, 0.9f, 1f, 1f)
        skin.add("stat-value", statValueStyle)
    }

    private fun createDefaultSkin(): Skin {
        val skin = Skin()

        // Create a 1x1 white texture
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.WHITE)
        pixmap.fill()
        val texture = Texture(pixmap)
        pixmap.dispose()

        // Try to load custom font first, fallback to default
        val font = try {
            val customFont = BitmapFont(Gdx.files.internal("ui/default.fnt"))
            println("Custom font loaded successfully (fallback)")
            customFont
        } catch (e: Exception) {
            println("Using system default font: ${e.message}")
            BitmapFont()
        }

        // Add font to skin
        skin.add("default-font", font)

        // Create drawable for UI elements
        val buttonTexture = TextureRegion(texture)
        val buttonDrawable = TextureRegionDrawable(buttonTexture)

        // Add background drawable
        skin.add("default-round", buttonDrawable.tint(Color(0.2f, 0.2f, 0.2f, 0.8f)))

        // Label style
        val labelStyle = Label.LabelStyle()
        labelStyle.font = font
        labelStyle.fontColor = Color.WHITE
        skin.add("default", labelStyle)

        addCustomLabelStyles(skin, font)

        return skin
    }

    private fun updateToolButtons() {
        val tools = Tool.values()

        for (i in toolButtons.indices) {
            val toolButton = toolButtons[i]
            val tool = tools[i]
            val isSelected = tool == selectedTool

            // Create smooth transition
            val targetBackground = if (isSelected) {
                createToolButtonBackground(Color(0.4f, 0.6f, 0.8f, 0.95f))
            } else {
                createToolButtonBackground(Color(0.25f, 0.25f, 0.3f, 0.9f))
            }

            // Animate the selection
            toolButton.clearActions()
            toolButton.addAction(
                Actions.sequence(
                    Actions.run { toolButton.background = targetBackground },
                    if (isSelected) {
                        Actions.sequence(
                            Actions.scaleTo(1.1f, 1.1f, 0.1f, Interpolation.bounceOut),
                            Actions.scaleTo(1.0f, 1.0f, 0.1f, Interpolation.smooth)
                        )
                    } else {
                        Actions.scaleTo(1.0f, 1.0f, 0.1f, Interpolation.smooth)
                    }
                )
            )

            // Update label color
            val nameLabel = toolButton.children.get(1) as Label
            nameLabel.color = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
        }
    }

    // Update stats with current values - Updated to include car count
    fun updateStats(blockCount: Int, playerPlaced: Boolean, objectCount: Int, itemCount: Int = 0, carCount: Int = 0, houseCount: Int = 0, backgroundCount: Int = 0) {
        statsLabels["Blocks"]?.setText(blockCount.toString())
        statsLabels["Player"]?.setText(if (playerPlaced) "Placed" else "Not Placed")
        statsLabels["Objects"]?.setText(objectCount.toString())
        statsLabels["Items"]?.setText(itemCount.toString())
        statsLabels["Cars"]?.setText(carCount.toString())
        statsLabels["Houses"]?.setText(houseCount.toString())
        statsLabels["Backgrounds"]?.setText(backgroundCount.toString())
    }

    fun toggleVisibility() {
        isUIVisible = !isUIVisible
        mainTable.isVisible = isUIVisible
    }

    // Block selection methods
    fun showBlockSelection() {
        blockSelectionUI.show()
    }

    fun hideBlockSelection() {
        blockSelectionUI.hide()
    }

    fun updateBlockSelection() {
        blockSelectionUI.update()
    }

    // Object selection methods
    fun showObjectSelection() {
        objectSelectionUI.show()
    }

    fun hideObjectSelection() {
        objectSelectionUI.hide()
    }

    fun updateObjectSelection() {
        objectSelectionUI.update()
    }

    // Item selection methods
    fun showItemSelection() {
        itemSelectionUI.show()
    }

    fun hideItemSelection() {
        itemSelectionUI.hide()
    }

    fun updateItemSelection() {
        itemSelectionUI.update()
    }

    // Car selection methods - Add these new methods
    fun showCarSelection() {
        carSelectionUI.show()
    }

    fun hideCarSelection() {
        carSelectionUI.hide()
    }

    fun updateCarSelection() {
        carSelectionUI.update()
    }

    fun showHouseSelection() {
        houseSelectionUI.show()
    }

    fun hideHouseSelection() {
        houseSelectionUI.hide()
    }

    fun updateHouseSelection() {
        houseSelectionUI.update()
    }

    fun getLightSourceSettings(): Triple<Float, Float, Color> {
        return lightSourceUI.getCurrentSettings()
    }

    fun toggleLightSourceUI() {
        lightSourceUI.toggle()
    }

    fun showBackgroundSelection() {
        backgroundSelectionUI.show()
    }

    fun hideBackgroundSelection() {
        backgroundSelectionUI.hide()
    }

    fun updateBackgroundSelection() {
        backgroundSelectionUI.update()
    }

    // Method to update the tool display when tool changes
    fun updateToolDisplay() {
        updateToolButtons()
    }

    fun getStage(): Stage = stage

    fun render() {
        stage.act(Gdx.graphics.deltaTime)
        stage.draw()
    }

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    fun dispose() {
        blockSelectionUI.dispose()
        objectSelectionUI.dispose()
        itemSelectionUI.dispose()
        carSelectionUI.dispose()
        houseSelectionUI.dispose()
        backgroundSelectionUI.dispose()
        lightSourceUI.dispose()
        stage.dispose()
        skin.dispose()
    }
}
