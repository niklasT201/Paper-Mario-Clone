package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
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
import com.badlogic.gdx.utils.viewport.ScreenViewport
import kotlin.math.cos
import kotlin.math.sin

class UIManager(
    private val blockSystem: BlockSystem,
    private val objectSystem: ObjectSystem,
    private val itemSystem: ItemSystem,
    private val carSystem: CarSystem,
    private val houseSystem: HouseSystem,
    private val backgroundSystem: BackgroundSystem,
    private val parallaxSystem: ParallaxBackgroundSystem,
    private val roomTemplateManager: RoomTemplateManager,
    private val interiorSystem: InteriorSystem,
    private val lightingManager: LightingManager,
    private val shaderEffectManager: ShaderEffectManager,
    private val enemySystem: EnemySystem,
    private val npcSystem: NPCSystem,
    private val particleSystem: ParticleSystem,
    private val particleSpawnerSystem: ParticleSpawnerSystem,
    private val onRemoveSpawner: (spawner: GameParticleSpawner) -> Unit
) {
    private lateinit var stage: Stage
    lateinit var skin: Skin
    private lateinit var blockSelectionUI: BlockSelectionUI
    private lateinit var objectSelectionUI: ObjectSelectionUI
    private lateinit var itemSelectionUI: ItemSelectionUI
    private lateinit var carSelectionUI: CarSelectionUI
    private lateinit var houseSelectionUI: HouseSelectionUI
    private lateinit var backgroundSelectionUI: BackgroundSelectionUI
    private lateinit var parallaxSelectionUI: ParallaxSelectionUI
    private lateinit var interiorSelectionUI: InteriorSelectionUI
    private lateinit var enemySelectionUI: EnemySelectionUI
    private lateinit var npcSelectionUI: NPCSelectionUI
    private lateinit var particleSelectionUI: ParticleSelectionUI
    private lateinit var particleSpawnerUI: ParticleSpawnerUI
    private lateinit var lightSourceUI: LightSourceUI
    private lateinit var skyCustomizationUI: SkyCustomizationUI
    private lateinit var shaderEffectUI: ShaderEffectUI
    private lateinit var mainTable: Table
    private lateinit var toolButtons: MutableList<Table>
    private lateinit var statsLabels: MutableMap<String, Label>
    private lateinit var placementInfoLabel: Label
    private lateinit var persistentMessageLabel: Label

    var isUIVisible = false
        private set
    var selectedTool = Tool.BLOCK

    // Design constants
    private val ACCENT_COLOR = Color(0.2f, 0.7f, 1f, 1f) // Bright blue
    private val SECONDARY_COLOR = Color(0.8f, 0.4f, 1f, 1f) // Purple
    private val SUCCESS_COLOR = Color(0.2f, 0.9f, 0.4f, 1f) // Green
    private val WARNING_COLOR = Color(1f, 0.6f, 0.2f, 1f) // Orange
    private val PANEL_COLOR = Color(0.08f, 0.08f, 0.12f, 0.92f)
    private val PANEL_BORDER = Color(0.25f, 0.35f, 0.45f, 0.8f)
    private val TEXT_PRIMARY = Color(0.95f, 0.95f, 1f, 1f)
    private val TEXT_SECONDARY = Color(0.7f, 0.8f, 0.9f, 1f)
    private val TEXT_MUTED = Color(0.6f, 0.6f, 0.7f, 1f)

    enum class Tool {
        BLOCK, PLAYER, OBJECT, ITEM, CAR, HOUSE, BACKGROUND, PARALLAX, INTERIOR, ENEMY, NPC, PARTICLE
    }

    fun initialize() {
        stage = Stage(ScreenViewport())
        skin = createSkin()

        setupMainUI()

        persistentMessageLabel = Label("", skin, "title") // Use a prominent style like "title"
        persistentMessageLabel.setAlignment(Align.center)
        persistentMessageLabel.color = Color.YELLOW
        persistentMessageLabel.isVisible = false

        // Create a separate table for it so it can be centered easily
        val persistentMessageTable = Table()
        persistentMessageTable.setFillParent(true)
        persistentMessageTable.top().pad(50f) // Position it near the top
        persistentMessageTable.add(persistentMessageLabel)
        stage.addActor(persistentMessageTable) // Add it directly to the stage to overlay everyth

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
        houseSelectionUI = HouseSelectionUI(houseSystem, roomTemplateManager, skin, stage)
        houseSelectionUI.initialize()

        // Initialize background selection UI
        backgroundSelectionUI = BackgroundSelectionUI(backgroundSystem, skin, stage)
        backgroundSelectionUI.initialize()

        // Initialize parallax selection UI
        parallaxSelectionUI = ParallaxSelectionUI(parallaxSystem, skin, stage)
        parallaxSelectionUI.initialize()

        // Initialize interior selection UI
        interiorSelectionUI = InteriorSelectionUI(interiorSystem, skin, stage)
        interiorSelectionUI.initialize()

        skyCustomizationUI = SkyCustomizationUI(skin, stage, lightingManager)
        skyCustomizationUI.initialize()

        enemySelectionUI = EnemySelectionUI(enemySystem, skin, stage)
        enemySelectionUI.initialize()

        npcSelectionUI = NPCSelectionUI(npcSystem, skin, stage)
        npcSelectionUI.initialize()

        particleSelectionUI = ParticleSelectionUI(particleSystem, skin, stage)
        particleSelectionUI.initialize()

        particleSpawnerUI = ParticleSpawnerUI(skin, stage, particleSystem, onRemoveSpawner)

        shaderEffectUI = ShaderEffectUI(skin, stage, shaderEffectManager)
        shaderEffectUI.initialize()

        // Set initial visibility for the main UI panel
        mainTable.isVisible = isUIVisible
    }

    private fun setupMainUI() {
        mainTable = Table()
        mainTable.setFillParent(true)
        mainTable.top().left()
        mainTable.pad(15f)

        // Create main container with enhanced modern design
        val mainContainer = Table()
        mainContainer.background = createGlassmorphismBackground()
        mainContainer.pad(30f)
        mainContainer.top()

        // Enhanced title with gradient effect
        val titleLabel = Label("World Builder", skin, "title-gradient")
        val titleContainer = Table()
        titleContainer.background = createTitleBackground()
        titleContainer.pad(15f, 25f, 15f, 25f)
        titleContainer.add(titleLabel)
        mainContainer.add(titleContainer).padBottom(30f).fillX().row()

        // Tool selection with enhanced styling
        createEnhancedToolSelectionSection(mainContainer)

        // Enhanced placement info with modern card design
        val infoContainer = Table()
        infoContainer.background = createInfoCardBackground()
        infoContainer.pad(15f)
        placementInfoLabel = Label("", skin, "info-card")
        placementInfoLabel.setWrap(true)
        infoContainer.add(placementInfoLabel).width(320f)
        mainContainer.add(infoContainer).left().padBottom(25f).fillX().row()

        // Enhanced instructions section
        createEnhancedInstructionsSection(mainContainer)

        // Enhanced stats section
        createEnhancedStatsSection(mainContainer)

        val scrollPane = ScrollPane(mainContainer, skin)
        scrollPane.setScrollingDisabled(true, false)
        scrollPane.fadeScrollBars = true
        scrollPane.setFlickScroll(false)
        scrollPane.setOverscroll(false, false)

        mainTable.add(scrollPane).expandY().fillY().top().left() // Add the scroll pane
        stage.addActor(mainTable)
    }

    private fun createEnhancedToolSelectionSection(container: Table) {
        // Section header with modern styling
        val toolSectionContainer = Table()
        toolSectionContainer.background = createSectionHeaderBackground()
        toolSectionContainer.pad(12f, 20f, 12f, 20f)

        val toolSectionLabel = Label("🛠 Tools", skin, "section-header")
        val modeLabel = Label("P to cycle BG mode", skin, "hint")

        toolSectionContainer.add(toolSectionLabel).left().expandX()
        toolSectionContainer.add(modeLabel).right()

        container.add(toolSectionContainer).fillX().padBottom(20f).row()

        // Enhanced tool buttons with modern card design
        val toolContainer = Table()
        toolContainer.background = createToolContainerBackground()
        toolContainer.pad(20f)

        toolButtons = mutableListOf()
        val tools = Tool.entries

        // Create tool grid with better spacing
        val toolGrid = Table()
        for (i in tools.indices) {
            val tool = tools[i]
            val toolButton = createEnhancedToolButton(tool, tool == selectedTool)
            toolButtons.add(toolButton)

            toolGrid.add(toolButton).size(90f, 85f).pad(8f)

            // Create rows of 4 tools
            if ((i + 1) % 4 == 0) {
                toolGrid.row()
            }
        }

        toolContainer.add(toolGrid)
        container.add(toolContainer).padBottom(25f).fillX().row()
    }

    private fun createEnhancedToolButton(tool: Tool, isSelected: Boolean): Table {
        val buttonContainer = Table()
        buttonContainer.pad(12f)

        // Enhanced backgrounds with glow effect
        val background = if (isSelected) {
            createSelectedToolBackground(getToolAccentColor(tool))
        } else {
            createNormalToolBackground()
        }
        buttonContainer.background = background

        // Enhanced tool icon with better graphics
        val iconTexture = createEnhancedToolIcon(tool)
        val iconImage = Image(iconTexture)
        buttonContainer.add(iconImage).size(36f, 36f).padBottom(8f).row()

        // Tool name with enhanced styling
        val nameLabel = Label(getToolDisplayName(tool), skin, "tool-name")
        nameLabel.color = if (isSelected) TEXT_PRIMARY else TEXT_SECONDARY
        buttonContainer.add(nameLabel)

        return buttonContainer
    }

    private fun createEnhancedInstructionsSection(container: Table) {
        // Section header
        val instructionSectionContainer = Table()
        instructionSectionContainer.background = createSectionHeaderBackground()
        instructionSectionContainer.pad(12f, 20f, 12f, 20f)

        val instructionSectionLabel = Label("📋 Controls", skin, "section-header")
        instructionSectionContainer.add(instructionSectionLabel).left()

        container.add(instructionSectionContainer).fillX().padBottom(20f).row()

        // Enhanced instructions with better organization
        val instructionsContainer = Table()
        instructionsContainer.background = createInstructionsCardBackground()
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
    }

    private fun createEnhancedStatsSection(container: Table) {
        // Section header
        val statsSectionContainer = Table()
        statsSectionContainer.background = createSectionHeaderBackground()
        statsSectionContainer.pad(12f, 20f, 12f, 20f)

        val statsSectionLabel = Label("📊 Statistics", skin, "section-header")
        statsSectionContainer.add(statsSectionLabel).left()

        container.add(statsSectionContainer).fillX().padBottom(20f).row()

        // Enhanced stats with modern card design
        val statsContainer = Table()
        statsContainer.background = createStatsCardBackground()
        statsContainer.pad(20f)

        statsLabels = mutableMapOf()

        // Create individual stat items - Updated to include cars
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
            statRow.background = createStatRowBackground()
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
    }

    // Enhanced background creation methods
    private fun createGlassmorphismBackground(): Drawable {
        val pixmap = Pixmap(400, 600, Pixmap.Format.RGBA8888)

        // Create glassmorphism effect
        for (y in 0 until 600) {
            for (x in 0 until 400) {
                val noise = (sin(x * 0.01f) + cos(y * 0.01f)) * 0.02f
                val alpha = 0.85f + noise
                pixmap.setColor(0.06f, 0.08f, 0.15f, alpha)
                pixmap.drawPixel(x, y)
            }
        }

        // Add glowing border
        pixmap.setColor(PANEL_BORDER.r, PANEL_BORDER.g, PANEL_BORDER.b, 0.9f)
        pixmap.drawRectangle(0, 0, 400, 600)
        pixmap.drawRectangle(1, 1, 398, 598)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createTitleBackground(): Drawable {
        val pixmap = Pixmap(300, 50, Pixmap.Format.RGBA8888)

        // Gradient background
        for (y in 0 until 50) {
            val progress = y / 50f
            val r = ACCENT_COLOR.r * (1f - progress) + SECONDARY_COLOR.r * progress
            val g = ACCENT_COLOR.g * (1f - progress) + SECONDARY_COLOR.g * progress
            val b = ACCENT_COLOR.b * (1f - progress) + SECONDARY_COLOR.b * progress
            pixmap.setColor(r, g, b, 0.3f)
            pixmap.drawLine(0, y, 299, y)
        }

        // Glowing border
        pixmap.setColor(ACCENT_COLOR.r, ACCENT_COLOR.g, ACCENT_COLOR.b, 0.6f)
        pixmap.drawRectangle(0, 0, 300, 50)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createSectionHeaderBackground(): Drawable {
        val pixmap = Pixmap(350, 40, Pixmap.Format.RGBA8888)

        // Subtle gradient
        for (y in 0 until 40) {
            val alpha = 0.15f + (y / 40f) * 0.1f
            pixmap.setColor(0.15f, 0.2f, 0.3f, alpha)
            pixmap.drawLine(0, y, 349, y)
        }

        // Accent line at bottom
        pixmap.setColor(ACCENT_COLOR.r, ACCENT_COLOR.g, ACCENT_COLOR.b, 0.7f)
        pixmap.drawLine(0, 38, 349, 38)
        pixmap.drawLine(0, 39, 349, 39)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createToolContainerBackground(): Drawable {
        val pixmap = Pixmap(350, 200, Pixmap.Format.RGBA8888)

        // Dark container with subtle pattern
        pixmap.setColor(0.1f, 0.12f, 0.18f, 0.9f)
        pixmap.fill()

        // Add subtle pattern
        pixmap.setColor(0.15f, 0.17f, 0.23f, 0.5f)
        for (i in 0 until 350 step 20) {
            for (j in 0 until 200 step 20) {
                if ((i + j) % 40 == 0) {
                    pixmap.fillRectangle(i, j, 1, 1)
                }
            }
        }

        // Border
        pixmap.setColor(0.25f, 0.3f, 0.4f, 0.8f)
        pixmap.drawRectangle(0, 0, 350, 200)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createSelectedToolBackground(accentColor: Color): Drawable {
        val pixmap = Pixmap(90, 85, Pixmap.Format.RGBA8888)

        // Glowing selected background
        for (y in 0 until 85) {
            for (x in 0 until 90) {
                val centerX = 45f
                val centerY = 42.5f
                val dist = kotlin.math.sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY))
                val glow = kotlin.math.max(0f, 1f - dist / 45f)

                val alpha = 0.2f + glow * 0.3f
                pixmap.setColor(accentColor.r, accentColor.g, accentColor.b, alpha)
                pixmap.drawPixel(x, y)
            }
        }

        // Bright border
        pixmap.setColor(accentColor.r, accentColor.g, accentColor.b, 0.9f)
        pixmap.drawRectangle(0, 0, 90, 85)
        pixmap.drawRectangle(1, 1, 88, 83)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createNormalToolBackground(): Drawable {
        val pixmap = Pixmap(90, 85, Pixmap.Format.RGBA8888)

        // Subtle gradient
        for (y in 0 until 85) {
            val alpha = 0.4f + (y / 85f) * 0.1f
            pixmap.setColor(0.18f, 0.2f, 0.25f, alpha)
            pixmap.drawLine(0, y, 89, y)
        }

        // Subtle border
        pixmap.setColor(0.3f, 0.35f, 0.4f, 0.6f)
        pixmap.drawRectangle(0, 0, 90, 85)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createInfoCardBackground(): Drawable {
        val pixmap = Pixmap(350, 60, Pixmap.Format.RGBA8888)

        // Card background with subtle glow
        pixmap.setColor(0.1f, 0.15f, 0.2f, 0.85f)
        pixmap.fill()

        // Accent border
        pixmap.setColor(WARNING_COLOR.r, WARNING_COLOR.g, WARNING_COLOR.b, 0.6f)
        pixmap.drawRectangle(0, 0, 350, 60)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createInstructionsCardBackground(): Drawable {
        val pixmap = Pixmap(380, 300, Pixmap.Format.RGBA8888)

        // Enhanced card background
        pixmap.setColor(0.08f, 0.12f, 0.18f, 0.9f)
        pixmap.fill()

        // Subtle inner glow
        pixmap.setColor(0.12f, 0.16f, 0.22f, 0.7f)
        pixmap.drawRectangle(5, 5, 370, 290)

        // Accent border
        pixmap.setColor(0.2f, 0.4f, 0.6f, 0.8f)
        pixmap.drawRectangle(0, 0, 380, 300)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createStatsCardBackground(): Drawable {
        val pixmap = Pixmap(300, 250, Pixmap.Format.RGBA8888)

        // Enhanced stats background
        pixmap.setColor(0.08f, 0.12f, 0.16f, 0.92f)
        pixmap.fill()

        // Success color accent
        pixmap.setColor(SUCCESS_COLOR.r, SUCCESS_COLOR.g, SUCCESS_COLOR.b, 0.3f)
        pixmap.drawRectangle(0, 0, 300, 250)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createStatRowBackground(): Drawable {
        val pixmap = Pixmap(280, 35, Pixmap.Format.RGBA8888)

        // Subtle row background
        pixmap.setColor(0.12f, 0.16f, 0.2f, 0.6f)
        pixmap.fill()

        // Subtle border
        pixmap.setColor(0.2f, 0.25f, 0.3f, 0.4f)
        pixmap.drawRectangle(0, 0, 280, 35)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createEnhancedToolIcon(tool: Tool): TextureRegion {
        val pixmap = Pixmap(36, 36, Pixmap.Format.RGBA8888)

        val baseColor = when (tool) {
            Tool.BLOCK -> Color(0.3f, 0.8f, 0.3f, 1f)
            Tool.PLAYER -> Color(1f, 0.7f, 0.2f, 1f)
            Tool.OBJECT -> Color(0.7f, 0.3f, 0.9f, 1f)
            Tool.ITEM -> Color(1f, 0.9f, 0.2f, 1f)
            Tool.CAR -> Color(0.9f, 0.3f, 0.6f, 1f)
            Tool.HOUSE -> Color(0.5f, 0.9f, 0.3f, 1f)
            Tool.BACKGROUND -> Color(0.2f, 0.7f, 1f, 1f)
            Tool.PARALLAX -> Color(0.4f, 0.8f, 0.7f, 1f)
            Tool.INTERIOR -> Color.BROWN
            Tool.ENEMY -> Color.RED
            Tool.NPC -> Color(0.2f, 0.8f, 1f, 1f)
            Tool.PARTICLE -> Color(1f, 0.5f, 0.2f, 1f)
        }

        // Create gradient background
        for (y in 0 until 36) {
            for (x in 0 until 36) {
                val centerDist = kotlin.math.sqrt((x - 18f) * (x - 18f) + (y - 18f) * (y - 18f))
                val gradient = kotlin.math.max(0f, 1f - centerDist / 18f)

                val r = baseColor.r * (0.7f + gradient * 0.3f)
                val g = baseColor.g * (0.7f + gradient * 0.3f)
                val b = baseColor.b * (0.7f + gradient * 0.3f)

                pixmap.setColor(r, g, b, baseColor.a)
                pixmap.drawPixel(x, y)
            }
        }

        // Add enhanced icons
        val shadowColor = Color(0f, 0f, 0f, 0.3f)
        val highlightColor = Color(1f, 1f, 1f, 0.8f)

        when (tool) {
            Tool.BLOCK -> {
                // 3D block effect
                pixmap.setColor(shadowColor)
                pixmap.fillRectangle(10, 10, 16, 16)
                pixmap.setColor(highlightColor)
                pixmap.fillRectangle(8, 8, 16, 16)
                pixmap.setColor(baseColor)
                pixmap.fillRectangle(9, 9, 14, 14)

                // Grid lines
                pixmap.setColor(shadowColor)
                for (i in 9..23 step 5) {
                    pixmap.drawLine(i, 9, i, 23)
                    pixmap.drawLine(9, i, 23, i)
                }
            }
            Tool.PLAYER -> {
                // Enhanced player silhouette
                pixmap.setColor(shadowColor)
                pixmap.fillCircle(19, 13, 7) // Head shadow
                pixmap.fillRectangle(13, 19, 12, 15) // Body shadow

                pixmap.setColor(highlightColor)
                pixmap.fillCircle(18, 12, 6) // Head
                pixmap.fillRectangle(12, 18, 12, 14) // Body
                pixmap.fillRectangle(10, 28, 4, 6) // Left leg
                pixmap.fillRectangle(22, 28, 4, 6) // Right leg
                pixmap.fillRectangle(8, 20, 4, 8) // Left arm
                pixmap.fillRectangle(24, 20, 4, 8) // Right arm
            }
            Tool.OBJECT -> {
                // Enhanced 3D cube
                pixmap.setColor(shadowColor)
                pixmap.fillRectangle(12, 12, 14, 14)
                pixmap.setColor(highlightColor)
                pixmap.fillRectangle(10, 10, 14, 14)
                pixmap.setColor(baseColor)
                pixmap.fillRectangle(11, 11, 12, 12)

                // Cross pattern
                pixmap.setColor(highlightColor)
                pixmap.fillRectangle(16, 8, 4, 20)
                pixmap.fillRectangle(8, 16, 20, 4)
            }
            Tool.ITEM -> {
                // Enhanced diamond/gem
                pixmap.setColor(shadowColor)
                pixmap.fillTriangle(19, 8, 12, 18, 19, 18)
                pixmap.fillTriangle(19, 8, 19, 18, 26, 18)
                pixmap.fillTriangle(12, 18, 19, 28, 26, 18)

                pixmap.setColor(highlightColor)
                pixmap.fillTriangle(18, 7, 11, 17, 18, 17)
                pixmap.fillTriangle(18, 7, 18, 17, 25, 17)
                pixmap.fillTriangle(11, 17, 18, 27, 25, 17)

                // Sparkle effects
                pixmap.setColor(Color.WHITE)
                pixmap.fillRectangle(15, 12, 2, 2)
                pixmap.fillRectangle(20, 15, 2, 2)
            }
            Tool.CAR -> {
                // Enhanced car with more detail
                pixmap.setColor(shadowColor)
                pixmap.fillRectangle(8, 18, 22, 10) // Body shadow
                pixmap.fillRectangle(12, 12, 14, 8) // Roof shadow

                pixmap.setColor(highlightColor)
                pixmap.fillRectangle(7, 17, 22, 10) // Main body
                pixmap.fillRectangle(11, 11, 14, 8) // Roof

                // Windows
                pixmap.setColor(Color(0.6f, 0.8f, 1f, 1f))
                pixmap.fillRectangle(13, 13, 4, 4)
                pixmap.fillRectangle(19, 13, 4, 4)

                // Wheels
                pixmap.setColor(Color.BLACK)
                pixmap.fillCircle(13, 28, 3)
                pixmap.fillCircle(23, 28, 3)
                pixmap.setColor(Color.GRAY)
                pixmap.fillCircle(13, 28, 2)
                pixmap.fillCircle(23, 28, 2)
            }
            Tool.HOUSE -> {
                // Enhanced house
                pixmap.setColor(shadowColor)
                pixmap.fillRectangle(9, 18, 20, 16) // House shadow
                pixmap.fillTriangle(18, 8, 8, 18, 28, 18) // Roof shadow

                pixmap.setColor(highlightColor)
                pixmap.fillRectangle(8, 17, 20, 16) // House body
                pixmap.fillTriangle(18, 7, 7, 17, 29, 17) // Roof

                // Door and windows
                pixmap.setColor(Color(0.4f, 0.2f, 0.1f, 1f))
                pixmap.fillRectangle(15, 25, 6, 8) // Door
                pixmap.setColor(Color(0.6f, 0.8f, 1f, 1f))
                pixmap.fillRectangle(10, 20, 4, 4) // Left window
                pixmap.fillRectangle(22, 20, 4, 4) // Right window
            }
            Tool.BACKGROUND -> {
                // Enhanced landscape
                pixmap.setColor(shadowColor)
                pixmap.fillRectangle(6, 26, 26, 8) // Ground shadow

                pixmap.setColor(Color(0.3f, 0.8f, 0.3f, 1f))
                pixmap.fillRectangle(5, 25, 26, 8) // Ground

                // Mountains with gradient
                pixmap.setColor(Color(0.4f, 0.6f, 0.8f, 1f))
                pixmap.fillTriangle(18, 12, 8, 25, 28, 25)
                pixmap.setColor(Color(0.6f, 0.8f, 1f, 1f))
                pixmap.fillTriangle(18, 12, 8, 25, 18, 25)

                // Sun with rays
                pixmap.setColor(Color(1f, 0.9f, 0.3f, 1f))
                pixmap.fillCircle(27, 13, 4)
                pixmap.setColor(Color(1f, 1f, 0.7f, 0.8f))
                for (i in 0..7) {
                    val angle = i * 45f * kotlin.math.PI / 180f
                    val x1 = 27 + cos(angle) * 6
                    val y1 = 13 + sin(angle) * 6
                    val x2 = 27 + cos(angle) * 8
                    val y2 = 13 + sin(angle) * 8
                    pixmap.drawLine(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt())
                }
            }
            Tool.PARALLAX -> {
                // Layered landscape icon
                // Far mountains
                pixmap.setColor(Color(0.4f, 0.5f, 0.7f, 0.8f))
                pixmap.fillTriangle(18, 10, 8, 28, 28, 28)
                // Mid hills
                pixmap.setColor(Color(0.5f, 0.7f, 0.4f, 0.9f))
                pixmap.fillTriangle(5, 18, 15, 30, 25, 30)
                pixmap.fillTriangle(15, 20, 25, 32, 32, 32)
                // Near ground
                pixmap.setColor(Color(0.3f, 0.6f, 0.2f, 1f))
                pixmap.fillRectangle(4, 26, 28, 8)
            }
            Tool.INTERIOR -> {
                // A simple chair icon
                pixmap.setColor(shadowColor)
                pixmap.fillRectangle(12, 20, 14, 8) // Seat shadow
                pixmap.fillRectangle(22, 10, 4, 12) // Back shadow

                pixmap.setColor(highlightColor)
                pixmap.fillRectangle(11, 19, 14, 8) // Seat
                pixmap.fillRectangle(21, 9, 4, 12)  // Back
                pixmap.fillRectangle(12, 27, 4, 6) // Front leg
                pixmap.fillRectangle(20, 27, 4, 6) // Back leg
            }
            Tool.ENEMY -> {
                // Icon for enemy (e.g., a simple skull or angry face)
                pixmap.setColor(shadowColor)
                pixmap.fillCircle(19, 16, 12)
                pixmap.setColor(highlightColor)
                pixmap.fillCircle(18, 15, 12)
                pixmap.setColor(Color.BLACK)
                pixmap.fillCircle(14, 12, 3) // Left eye
                pixmap.fillCircle(22, 12, 3) // Right eye
                pixmap.fillRectangle(12, 22, 12, 3) // Mouth
            }
            Tool.NPC -> {
                // Speech bubble icon
                pixmap.setColor(shadowColor)
                pixmap.fillRectangle(8, 8, 22, 18)
                pixmap.fillTriangle(12, 26, 12, 32, 20, 26)

                pixmap.setColor(highlightColor)
                pixmap.fillRectangle(7, 7, 22, 18)
                pixmap.fillTriangle(11, 25, 11, 31, 19, 25)

                // "..." text
                pixmap.setColor(Color.DARK_GRAY)
                pixmap.fillCircle(12, 16, 2)
                pixmap.fillCircle(18, 16, 2)
                pixmap.fillCircle(24, 16, 2)
            }
            Tool.PARTICLE -> {
                // Sparkle/Burst icon
                pixmap.setColor(shadowColor)
                pixmap.fillCircle(19, 19, 8)
                pixmap.setColor(highlightColor)
                pixmap.fillCircle(18, 18, 8)
                // Rays
                for (i in 0..7) {
                    val angle = i * 45f * kotlin.math.PI / 180f
                    val x1 = 18 + cos(angle) * 8
                    val y1 = 18 + sin(angle) * 8
                    val x2 = 18 + cos(angle) * 14
                    val y2 = 18 + sin(angle) * 14
                    pixmap.drawLine(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt())
                }
            }
        }

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegion(texture)
    }

    private fun getToolAccentColor(tool: Tool): Color {
        return when (tool) {
            Tool.BLOCK -> SUCCESS_COLOR
            Tool.PLAYER -> WARNING_COLOR
            Tool.OBJECT -> SECONDARY_COLOR
            Tool.ITEM -> Color(1f, 0.9f, 0.2f, 1f)
            Tool.CAR -> Color(0.9f, 0.3f, 0.6f, 1f)
            Tool.HOUSE -> Color(0.5f, 0.9f, 0.3f, 1f)
            Tool.BACKGROUND -> ACCENT_COLOR
            Tool.PARALLAX -> Color(0.4f, 0.8f, 0.7f, 1f)
            Tool.INTERIOR -> Color(0.8f, 0.5f, 0.2f, 1f)
            Tool.ENEMY -> Color.RED
            Tool.NPC -> Color(0.2f, 0.8f, 1f, 1f)
            Tool.PARTICLE -> Color(1f, 0.5f, 0.2f, 1f)
        }
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
            Tool.PARALLAX -> "Parallax"
            Tool.INTERIOR -> "Interior"
            Tool.ENEMY -> "Enemy"
            Tool.NPC -> "NPC"
            Tool.PARTICLE -> "Particle"
        }
    }

    private fun createSkin(): Skin {
        return try {
            val loadedSkin = Skin(Gdx.files.internal("ui/uiskin.json"))

            // Try to load and set custom font
            try {
                val customFont = BitmapFont(Gdx.files.internal("ui/default.fnt"), false)
                loadedSkin.add("default-font", customFont, BitmapFont::class.java)

                // Update existing styles to use the new font
                loadedSkin.get(Label.LabelStyle::class.java).font = customFont

                // Only update TextButton style if it exists
                try {
                    loadedSkin.get(TextButton.TextButtonStyle::class.java).font = customFont
                } catch (e: Exception) {
                    // TextButton style might not exist
                }

                addEnhancedLabelStyles(loadedSkin, customFont)
                println("Custom font loaded successfully from ui/default.fnt")
            } catch (e: Exception) {
                println("Could not load custom font: ${e.message}")
                addEnhancedLabelStyles(loadedSkin, loadedSkin.get(BitmapFont::class.java))
            }

            loadedSkin
        } catch (e: Exception) {
            println("Could not load UI skin, using default")
            createDefaultSkin()
        }
    }

    private fun addEnhancedLabelStyles(skin: Skin, font: BitmapFont) {
        // Enhanced title with gradient effect
        val titleGradientStyle = Label.LabelStyle()
        titleGradientStyle.font = font
        titleGradientStyle.fontColor = TEXT_PRIMARY
        skin.add("title-gradient", titleGradientStyle)

        // Section headers with accent color
        val sectionHeaderStyle = Label.LabelStyle()
        sectionHeaderStyle.font = font
        sectionHeaderStyle.fontColor = ACCENT_COLOR
        skin.add("section-header", sectionHeaderStyle)

        // Tool names
        val toolNameStyle = Label.LabelStyle()
        toolNameStyle.font = font
        toolNameStyle.fontColor = TEXT_SECONDARY
        skin.add("tool-name", toolNameStyle)

        // Enhanced instruction text
        val instructionEnhancedStyle = Label.LabelStyle()
        instructionEnhancedStyle.font = font
        instructionEnhancedStyle.fontColor = TEXT_SECONDARY
        skin.add("instruction-enhanced", instructionEnhancedStyle)

        // Info card text
        val infoCardStyle = Label.LabelStyle()
        infoCardStyle.font = font
        infoCardStyle.fontColor = WARNING_COLOR
        skin.add("info-card", infoCardStyle)

        // Enhanced stats styles
        val statIconStyle = Label.LabelStyle()
        statIconStyle.font = font
        statIconStyle.fontColor = ACCENT_COLOR
        skin.add("stat-icon", statIconStyle)

        val statKeyEnhancedStyle = Label.LabelStyle()
        statKeyEnhancedStyle.font = font
        statKeyEnhancedStyle.fontColor = TEXT_SECONDARY
        skin.add("stat-key-enhanced", statKeyEnhancedStyle)

        val statValueEnhancedStyle = Label.LabelStyle()
        statValueEnhancedStyle.font = font
        statValueEnhancedStyle.fontColor = SUCCESS_COLOR
        skin.add("stat-value-enhanced", statValueEnhancedStyle)

        // Hint text
        val hintStyle = Label.LabelStyle()
        hintStyle.font = font
        hintStyle.fontColor = TEXT_MUTED
        skin.add("hint", hintStyle)

        // Legacy styles for compatibility
        val titleStyle = Label.LabelStyle()
        titleStyle.font = font
        titleStyle.fontColor = TEXT_PRIMARY
        skin.add("title", titleStyle)

        // Section header style
        val sectionStyle = Label.LabelStyle()
        sectionStyle.font = font
        sectionStyle.fontColor = TEXT_SECONDARY
        skin.add("section", sectionStyle)

        // Small text style
        val smallStyle = Label.LabelStyle()
        smallStyle.font = font
        smallStyle.fontColor = TEXT_MUTED
        skin.add("small", smallStyle)

        // Instruction text style
        val instructionStyle = Label.LabelStyle()
        instructionStyle.font = font
        instructionStyle.fontColor = TEXT_SECONDARY
        skin.add("instruction", instructionStyle)

        // Stats key style
        val statKeyStyle = Label.LabelStyle()
        statKeyStyle.font = font
        statKeyStyle.fontColor = TEXT_SECONDARY
        skin.add("stat-key", statKeyStyle)

        // Stats value style
        val statValueStyle = Label.LabelStyle()
        statValueStyle.font = font
        statValueStyle.fontColor = TEXT_PRIMARY
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

        addEnhancedLabelStyles(skin, font)

        return skin
    }

    private fun updateToolButtons() {
        val tools = Tool.entries.toTypedArray()

        for (i in toolButtons.indices) {
            val toolButton = toolButtons[i]
            val tool = tools[i]
            val isSelected = tool == selectedTool

            // Create smooth transition
            val targetBackground = if (isSelected) {
                createSelectedToolBackground(getToolAccentColor(tool))
            } else {
                createNormalToolBackground()
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
            nameLabel.color = if (isSelected) TEXT_PRIMARY else TEXT_SECONDARY
        }
    }

    fun toggleVisibility() {
        isUIVisible = !isUIVisible
        mainTable.isVisible = isUIVisible

        // Add fade animation
        mainTable.clearActions()
        if (isUIVisible) {
            mainTable.color.a = 0f
            mainTable.addAction(Actions.fadeIn(0.3f, Interpolation.smooth))
        } else {
            mainTable.addAction(Actions.fadeOut(0.2f, Interpolation.smooth))
        }
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

    // Car selection methods
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

    fun showInteriorSelection() {
        interiorSelectionUI.show()
    }

    fun hideInteriorSelection() {
        interiorSelectionUI.hide()
    }

    fun updateInteriorSelection() {
        interiorSelectionUI.update()
    }

    fun getLightSourceSettings(): LightSourceSettings {
        return lightSourceUI.getCurrentSettings()
    }

    fun toggleLightSourceUI() {
        lightSourceUI.toggle()
    }

    fun toggleSkyCustomizationUI() {
        skyCustomizationUI.toggle()
    }

    fun showParallaxSelection() {
        parallaxSelectionUI.show()
    }

    fun hideParallaxSelection() {
        parallaxSelectionUI.hide()
    }

    fun nextParallaxImage() {
        parallaxSelectionUI.nextImage()
    }

    fun previousParallaxImage() {
        parallaxSelectionUI.previousImage()
    }

    fun nextParallaxLayer() {
        parallaxSelectionUI.nextLayer()
    }

    fun getCurrentParallaxImageType(): ParallaxBackgroundSystem.ParallaxImageType {
        return parallaxSelectionUI.getCurrentSelectedImageType()
    }

    fun getCurrentParallaxLayer(): Int {
        return parallaxSelectionUI.getCurrentSelectedLayer()
    }

    fun toggleShaderEffectUI() {
        shaderEffectUI.toggle()
    }

    fun showNPCSelection() { npcSelectionUI.show() }
    fun hideNPCSelection() { npcSelectionUI.hide() }
    fun updateNPCSelection() { npcSelectionUI.update() }

    fun showParticleSelection() {
        particleSelectionUI.show()
    }

    fun hideParticleSelection() {
        particleSelectionUI.hide()
    }

    fun updateParticleSelection() {
        particleSelectionUI.update()
    }

    fun updatePlacementInfo(info: String) {
        if (::placementInfoLabel.isInitialized) {
            placementInfoLabel.setText(info)

            // Add pulse animation for new info
            placementInfoLabel.clearActions()
            placementInfoLabel.addAction(
                Actions.sequence(
                    Actions.scaleTo(1.05f, 1.05f, 0.1f, Interpolation.bounceOut),
                    Actions.scaleTo(1.0f, 1.0f, 0.1f, Interpolation.smooth)
                )
            )
        }
    }

    fun showSaveRoomDialog(sceneManager: SceneManager) {
        // Check if we are actually in a room to save.
        if (sceneManager.currentScene != SceneType.HOUSE_INTERIOR) {
            updatePlacementInfo("Error: Can only save a room when inside a house.")
            return
        }

        // Get the current interior state to see if it was loaded from a template
        val currentInteriorState = sceneManager.getCurrentInteriorState()
        val sourceTemplateId = currentInteriorState?.sourceTemplateId
        val sourceTemplate = sourceTemplateId?.let { roomTemplateManager.getTemplate(it) }

        val dialogTitle = if (sourceTemplate != null) "Save Room" else "Save Room As Template"
        val dialog = Dialog(dialogTitle, skin, "dialog")
        dialog.text("Configure room options and choose a save method.").padBottom(10f)

        // UI Elements for Time Settings ---
        val contentTable = dialog.contentTable
        contentTable.row()

        // --- Common UI Elements ---
        val nameField = TextField("", skin).apply {
            messageText = "Template Name"
            // Pre-fill name field with the original name for convenience when using "Save As New"
            text = sourceTemplate?.name ?: ""
        }
        contentTable.add(Label("Name:", skin)).padRight(10f)
        contentTable.add(nameField).width(300f).row()

        val shaderLabel = Label("Room Shader:", skin)
        val shaderOptions = ShaderEffect.entries.toTypedArray()
        val shaderSelectBox = SelectBox<String>(skin)
        shaderSelectBox.items = com.badlogic.gdx.utils.Array(shaderOptions.map { it.displayName }.toTypedArray())

        val shaderTable = Table()
        shaderTable.add(shaderLabel).padRight(10f)
        shaderTable.add(shaderSelectBox).growX()
        contentTable.add(shaderTable).fillX().colspan(2).padTop(10f).row()

        val fixTimeCheckbox = CheckBox(" Fix time in this room", skin)
        contentTable.add(fixTimeCheckbox).left().padTop(10f).colspan(2).row()

        val timeSliderTable = Table()
        val timeLabel = Label("Time: 12:00", skin)
        val timeSlider = Slider(0f, 1f, 0.01f, false, skin)

        val tempCycle = DayNightCycle() // Assuming you have access to this or a similar class
        fun updateTimeLabel() {
            tempCycle.setDayProgress(timeSlider.value)
            timeLabel.setText("Time: ${tempCycle.getTimeString()}")
        }
        timeSlider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                updateTimeLabel()
            }
        })
        timeSliderTable.add(timeLabel).width(100f)
        timeSliderTable.add(timeSlider).growX()
        contentTable.add(timeSliderTable).fillX().padTop(5f).colspan(2).row()

        fixTimeCheckbox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                timeSliderTable.isVisible = fixTimeCheckbox.isChecked
                dialog.pack()
            }
        })

        // Pre-fill dialog with values from the source template if it exists
        if (sourceTemplate != null) {
            fixTimeCheckbox.isChecked = sourceTemplate.isTimeFixed
            timeSlider.value = sourceTemplate.fixedTimeProgress
            shaderSelectBox.selected = sourceTemplate.savedShaderEffect.displayName
        } else if (currentInteriorState != null) { // Or from the live interior state if it's a new room
            fixTimeCheckbox.isChecked = currentInteriorState.isTimeFixed
            timeSlider.value = currentInteriorState.fixedTimeProgress
            shaderSelectBox.selected = currentInteriorState.savedShaderEffect.displayName
        }
        timeSliderTable.isVisible = fixTimeCheckbox.isChecked
        updateTimeLabel()


        // --- Button Logic ---
        // This is the common save logic used by all save buttons
        fun performSave(id: String, name: String) {
            val isTimeFixed = fixTimeCheckbox.isChecked
            val fixedTimeProgress = timeSlider.value
            val selectedShaderName = shaderSelectBox.selected
            val selectedShader = ShaderEffect.entries.find { it.displayName == selectedShaderName } ?: ShaderEffect.NONE

            val success = sceneManager.saveCurrentInteriorAsTemplate(
                id, name, "user_created", isTimeFixed, fixedTimeProgress, selectedShader
            )

            if (success) {
                updatePlacementInfo("Room saved as '$name'")
                houseSelectionUI.refreshRoomList() // Crucial to see the new/updated room
            } else {
                updatePlacementInfo("Failed to save room template.")
            }
            dialog.hide()
        }

        // --- Conditional Button Setup ---
        if (sourceTemplate != null) {
            // This room was loaded from a template. Offer "Overwrite" and "Save As New".
            val overwriteButton = TextButton("Overwrite", skin)
            overwriteButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    // For overwrite, we use the original template's ID and name.
                    performSave(sourceTemplate.id, sourceTemplate.name)
                }
            })
            dialog.button(overwriteButton)

            val saveAsNewButton = TextButton("Save As New", skin)
            saveAsNewButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    val newName = nameField.text.trim()
                    if (newName.isBlank() || newName.equals(sourceTemplate.name, ignoreCase = true)) {
                        updatePlacementInfo("To save a new copy, please enter a different name.")
                        nameField.color = Color.RED
                        return
                    }
                    nameField.color = Color.WHITE
                    val newId = "user_room_${System.currentTimeMillis()}"
                    performSave(newId, newName)
                }
            })
            dialog.button(saveAsNewButton)

        } else {
            // This is a new room. Only "Save" (as new) is possible.
            val saveButton = TextButton("Save", skin)
            saveButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    val name = nameField.text.trim().ifBlank { "Room - ${System.currentTimeMillis().toString().takeLast(6)}" }
                    val newId = "user_room_${System.currentTimeMillis()}"
                    performSave(newId, name)
                }
            })
            dialog.button(saveButton)
        }

        dialog.button("Cancel", false) // LibGDX built-in cancel button
        dialog.key(com.badlogic.gdx.Input.Keys.ESCAPE, false) // Close on escape
        dialog.show(stage)
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

    fun showEnemySelection() {
        enemySelectionUI.show()
    }

    fun hideEnemySelection() {
        enemySelectionUI.hide()
    }

    fun updateEnemySelection() {
        enemySelectionUI.update()
    }

    fun updateToolDisplay() {
        updateToolButtons()
    }

    fun refreshHouseRoomList() {
        if (::houseSelectionUI.isInitialized) {
            houseSelectionUI.refreshRoomList()
        }
    }

    fun showParticleSpawnerUI(spawner: GameParticleSpawner) {
        particleSpawnerUI.show(spawner)
    }

    fun setPersistentMessage(message: String) {
        if (::persistentMessageLabel.isInitialized) {
            persistentMessageLabel.setText(message)
            persistentMessageLabel.isVisible = true

            // Add a pulsing animation to draw attention
            persistentMessageLabel.clearActions()
            persistentMessageLabel.addAction(Actions.forever(
                Actions.sequence(
                    Actions.color(Color.WHITE, 0.7f, Interpolation.sine),
                    Actions.color(Color.YELLOW, 0.7f, Interpolation.sine)
                )
            ))
        }
    }

    fun clearPersistentMessage() {
        if (::persistentMessageLabel.isInitialized) {
            persistentMessageLabel.isVisible = false
            persistentMessageLabel.clearActions() // Stop the pulsing animation
        }
    }

    fun navigateHouseRooms(direction: Int) {
        houseSelectionUI.navigateRooms(direction)
    }

    fun selectHouseRoom() {
        houseSelectionUI.selectCurrentRoom()
    }

    fun showTeleporterNameDialog(title: String, initialText: String = "", onConfirm: (name: String) -> Unit) {
        val dialog = Dialog(title, skin, "dialog")
        val nameField = TextField(initialText, skin)
        nameField.messageText = "e.g., 'To the stage'"

        dialog.contentTable.add(nameField).width(300f).pad(10f)

        val confirmButton = TextButton("Confirm", skin)
        confirmButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                val name = nameField.text.trim().ifBlank { "Teleporter" }
                onConfirm(name)
                dialog.hide()
            }
        })

        dialog.button(confirmButton)
        dialog.key(com.badlogic.gdx.Input.Keys.ENTER, true)
        dialog.key(com.badlogic.gdx.Input.Keys.ESCAPE, false)
        dialog.show(stage)
        stage.keyboardFocus = nameField
    }

    fun getStage(): Stage = stage

    fun render() {
        skyCustomizationUI.update()
        shaderEffectUI.update()
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
        parallaxSelectionUI.dispose()
        interiorSelectionUI.dispose()
        lightSourceUI.dispose()
        skyCustomizationUI.dispose()
        shaderEffectUI.dispose()
        particleSelectionUI.dispose()
        stage.dispose()
        skin.dispose()
    }
}
