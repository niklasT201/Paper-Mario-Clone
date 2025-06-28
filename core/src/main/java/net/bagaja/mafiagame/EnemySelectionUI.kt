package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align

class EnemySelectionUI(
    private val enemySystem: EnemySystem,
    private val skin: Skin,
    private val stage: Stage
) {
    private lateinit var enemySelectionTable: Table
    private lateinit var enemyTypeItems: MutableList<EnemySelectionItem>
    private lateinit var behaviorItems: MutableList<BehaviorSelectionItem>
    private val loadedTextures = mutableMapOf<String, Texture>() // Cache for loaded textures

    // Data classes to hold selection item components
    private data class EnemySelectionItem(
        val container: Table,
        val iconImage: Image,
        val nameLabel: Label,
        val statsLabel: Label,
        val background: Drawable,
        val selectedBackground: Drawable,
        val enemyType: EnemyType
    )

    private data class BehaviorSelectionItem(
        val container: Table,
        val iconImage: Image,
        val nameLabel: Label,
        val background: Drawable,
        val selectedBackground: Drawable,
        val behavior: EnemyBehavior
    )

    fun initialize() {
        setupEnemySelectionUI()
        enemySelectionTable.setVisible(false)
    }

    private fun setupEnemySelectionUI() {
        // Create enemy selection table at the top center
        enemySelectionTable = Table()
        enemySelectionTable.setFillParent(true)
        enemySelectionTable.top()
        enemySelectionTable.pad(40f)

        // Create main container with modern styling
        val mainContainer = Table()

        // Create a modern background with rounded corners and shadow effect
        val backgroundStyle = createModernBackground()
        mainContainer.background = backgroundStyle
        mainContainer.pad(20f, 30f, 20f, 30f)

        // Title with modern styling
        val titleLabel = Label("Enemy Selection", skin)
        titleLabel.setFontScale(1.4f)
        titleLabel.color = Color(0.9f, 0.9f, 0.9f, 1f) // Light gray
        mainContainer.add(titleLabel).padBottom(15f).row()

        // Enemy Type Section
        val typeTitle = Label("Enemy Type", skin)
        typeTitle.setFontScale(1.1f)
        typeTitle.color = Color(0.8f, 0.8f, 0.8f, 1f)
        mainContainer.add(typeTitle).padBottom(10f).row()

        // Create horizontal container for enemy type items
        val enemyTypeContainer = Table()
        enemyTypeContainer.pad(10f)

        // Create enemy type items
        enemyTypeItems = mutableListOf()
        val enemyTypes = EnemyType.entries.toTypedArray()

        for (i in enemyTypes.indices) {
            val enemyType = enemyTypes[i]
            val item = createEnemyTypeItem(enemyType, i == enemySystem.currentEnemyTypeIndex)
            enemyTypeItems.add(item)

            // Add spacing between items
            if (i > 0) {
                enemyTypeContainer.add().width(15f) // Spacer
            }
            enemyTypeContainer.add(item.container).size(100f, 130f)
        }

        mainContainer.add(enemyTypeContainer).padBottom(20f).row()

        // Behavior Section
        val behaviorTitle = Label("Enemy Behavior", skin)
        behaviorTitle.setFontScale(1.1f)
        behaviorTitle.color = Color(0.8f, 0.8f, 0.8f, 1f)
        mainContainer.add(behaviorTitle).padBottom(10f).row()

        // Create horizontal container for behavior items
        val behaviorContainer = Table()
        behaviorContainer.pad(10f)

        // Create behavior items
        behaviorItems = mutableListOf()
        val behaviors = EnemyBehavior.entries.toTypedArray()

        for (i in behaviors.indices) {
            val behavior = behaviors[i]
            val item = createBehaviorItem(behavior, i == enemySystem.currentBehaviorIndex)
            behaviorItems.add(item)

            // Add spacing between items
            if (i > 0) {
                behaviorContainer.add().width(15f) // Spacer
            }
            behaviorContainer.add(item.container).size(90f, 100f)
        }

        mainContainer.add(behaviorContainer).padBottom(10f).row()

        // Instructions with modern styling
        val instructionLabel = Label("Hold [Y] | Mouse Wheel: Change Type | Shift+Wheel: Change Behavior", skin)
        instructionLabel.setFontScale(0.9f)
        instructionLabel.color = Color(0.7f, 0.7f, 0.7f, 1f) // Darker gray
        mainContainer.add(instructionLabel).padBottom(5f).row()

        // Additional instructions
        val fineInstructionLabel = Label("F - Fine positioning | Left Click - Place Enemy", skin)
        fineInstructionLabel.setFontScale(0.8f)
        fineInstructionLabel.color = Color(0.6f, 0.6f, 0.6f, 1f)
        mainContainer.add(fineInstructionLabel)

        enemySelectionTable.add(mainContainer)
        stage.addActor(enemySelectionTable)
    }

    private fun createEnemyTypeItem(enemyType: EnemyType, isSelected: Boolean): EnemySelectionItem {
        val container = Table()
        container.pad(8f)

        // Create backgrounds for normal and selected states
        val normalBg = createItemBackground(Color(0.3f, 0.3f, 0.35f, 0.9f))
        val selectedBg = createItemBackground(Color(0.6f, 0.4f, 0.8f, 0.95f)) // Purple theme for enemies

        container.background = if (isSelected) selectedBg else normalBg

        // Enemy icon - try to load actual texture first, fallback to generated icon
        val iconTexture = loadEnemyTexture(enemyType)
        val iconImage = Image(iconTexture)
        container.add(iconImage).size(60f, 60f).padBottom(8f).row()

        // Enemy name
        val nameLabel = Label(enemyType.displayName, skin)
        nameLabel.setFontScale(0.7f)
        nameLabel.setWrap(true)
        nameLabel.setAlignment(Align.center)
        nameLabel.color = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
        container.add(nameLabel).width(90f).center().padBottom(5f).row()

        // Enemy stats
        val statsText = "HP: ${enemyType.baseHealth.toInt()}\nSpd: ${enemyType.speed}"
        val statsLabel = Label(statsText, skin)
        statsLabel.setFontScale(0.6f)
        statsLabel.setWrap(true)
        statsLabel.setAlignment(Align.center)
        statsLabel.color = if (isSelected) Color(0.9f, 0.9f, 0.9f, 1f) else Color(0.7f, 0.7f, 0.7f, 1f)
        container.add(statsLabel).width(90f).center()

        return EnemySelectionItem(container, iconImage, nameLabel, statsLabel, normalBg, selectedBg, enemyType)
    }

    private fun createBehaviorItem(behavior: EnemyBehavior, isSelected: Boolean): BehaviorSelectionItem {
        val container = Table()
        container.pad(8f)

        // Create backgrounds for normal and selected states
        val normalBg = createItemBackground(Color(0.3f, 0.35f, 0.3f, 0.9f))
        val selectedBg = createItemBackground(Color(0.4f, 0.8f, 0.6f, 0.95f)) // Green theme for behaviors

        container.background = if (isSelected) selectedBg else normalBg

        // Behavior icon
        val iconTexture = createBehaviorIcon(behavior)
        val iconImage = Image(iconTexture)
        container.add(iconImage).size(40f, 40f).padBottom(8f).row()

        // Behavior name
        val nameLabel = Label(behavior.displayName, skin)
        nameLabel.setFontScale(0.7f)
        nameLabel.setWrap(true)
        nameLabel.setAlignment(Align.center)
        nameLabel.color = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
        container.add(nameLabel).width(80f).center()

        return BehaviorSelectionItem(container, iconImage, nameLabel, normalBg, selectedBg, behavior)
    }

    private fun loadEnemyTexture(enemyType: EnemyType): TextureRegion {
        val texturePath = enemyType.texturePath

        // Check if we already have this texture cached
        if (loadedTextures.containsKey(texturePath)) {
            return TextureRegion(loadedTextures[texturePath]!!)
        }

        // Try to load the actual texture file
        return try {
            val fileHandle = Gdx.files.internal(texturePath)
            if (fileHandle.exists()) {
                val texture = Texture(fileHandle)
                loadedTextures[texturePath] = texture // Cache it
                println("Loaded enemy texture: $texturePath")
                TextureRegion(texture)
            } else {
                println("Enemy texture not found: $texturePath, using fallback")
                createEnemyIcon(enemyType)
            }
        } catch (e: Exception) {
            println("Failed to load enemy texture: $texturePath, error: ${e.message}, using fallback")
            createEnemyIcon(enemyType)
        }
    }

    private fun createEnemyIcon(enemyType: EnemyType): TextureRegion {
        val pixmap = Pixmap(60, 60, Pixmap.Format.RGBA8888)

        when (enemyType) {
            EnemyType.NOUSE_THUG -> {
                // Simple thug silhouette
                pixmap.setColor(Color(0.4f, 0.2f, 0.2f, 1f))
                pixmap.fillCircle(30, 45, 8) // Head
                pixmap.fillRectangle(25, 30, 10, 15) // Body
                pixmap.fillRectangle(23, 15, 5, 15) // Left arm
                pixmap.fillRectangle(32, 15, 5, 15) // Right arm
                pixmap.fillRectangle(27, 5, 6, 20) // Legs
            }
            EnemyType.GEORGE_MELES -> {
                // Distinguished figure with hat
                pixmap.setColor(Color(0.3f, 0.3f, 0.5f, 1f))
                pixmap.fillCircle(30, 45, 8) // Head
                pixmap.fillRectangle(22, 47, 16, 6) // Hat
                pixmap.setColor(Color(0.2f, 0.2f, 0.4f, 1f))
                pixmap.fillRectangle(25, 30, 10, 15) // Body
                pixmap.fillRectangle(23, 15, 5, 15) // Arms
                pixmap.fillRectangle(32, 15, 5, 15)
                pixmap.fillRectangle(27, 5, 6, 20) // Legs
            }
            EnemyType.GUNTHER -> {
                // Large, imposing figure
                pixmap.setColor(Color(0.5f, 0.3f, 0.2f, 1f))
                pixmap.fillCircle(30, 45, 10) // Larger head
                pixmap.fillRectangle(23, 28, 14, 17) // Larger body
                pixmap.fillRectangle(20, 12, 7, 18) // Thick arms
                pixmap.fillRectangle(33, 12, 7, 18)
                pixmap.fillRectangle(25, 3, 10, 22) // Thick legs
            }
            EnemyType.CORRUPT_DETECTIVE -> {
                // Detective with badge
                pixmap.setColor(Color(0.3f, 0.3f, 0.3f, 1f))
                pixmap.fillCircle(30, 45, 8) // Head
                pixmap.fillRectangle(25, 30, 10, 15) // Body
                pixmap.setColor(Color(0.8f, 0.7f, 0.2f, 1f))
                pixmap.fillCircle(28, 35, 3) // Badge
                pixmap.setColor(Color(0.3f, 0.3f, 0.3f, 1f))
                pixmap.fillRectangle(23, 15, 5, 15) // Arms
                pixmap.fillRectangle(32, 15, 5, 15)
                pixmap.fillRectangle(27, 5, 6, 20) // Legs
            }
            EnemyType.LADY_FOX -> {
                // Sleek, feminine silhouette
                pixmap.setColor(Color(0.6f, 0.2f, 0.3f, 1f))
                pixmap.fillCircle(30, 45, 7) // Smaller head
                pixmap.fillRectangle(26, 32, 8, 13) // Slimmer body
                pixmap.fillRectangle(24, 17, 4, 13) // Slimmer arms
                pixmap.fillRectangle(32, 17, 4, 13)
                pixmap.fillRectangle(28, 5, 4, 18) // Slimmer legs
            }
            EnemyType.MAFIA_BOSS -> {
                // Distinguished boss with suit
                pixmap.setColor(Color(0.1f, 0.1f, 0.1f, 1f))
                pixmap.fillCircle(30, 45, 9) // Head
                pixmap.fillRectangle(24, 28, 12, 17) // Wide body (suit)
                pixmap.setColor(Color(0.8f, 0.8f, 0.8f, 1f))
                pixmap.fillRectangle(28, 35, 4, 8) // Shirt/tie
                pixmap.setColor(Color(0.1f, 0.1f, 0.1f, 1f))
                pixmap.fillRectangle(22, 15, 6, 15) // Arms
                pixmap.fillRectangle(32, 15, 6, 15)
                pixmap.fillRectangle(26, 5, 8, 20) // Legs
            }
            EnemyType.NUN -> {
                pixmap.setColor(Color(0.6f, 0.2f, 0.3f, 1f))
                pixmap.fillCircle(30, 45, 7) // Smaller head
                pixmap.fillRectangle(26, 32, 8, 13) // Slimmer body
                pixmap.fillRectangle(24, 17, 4, 13) // Slimmer arms
                pixmap.fillRectangle(32, 17, 4, 13)
                pixmap.fillRectangle(28, 5, 4, 18) // Slimmer legs
            }
            EnemyType.SINGER -> {
                pixmap.setColor(Color(0.6f, 0.2f, 0.3f, 1f))
                pixmap.fillCircle(30, 45, 7) // Smaller head
                pixmap.fillRectangle(26, 32, 8, 13) // Slimmer body
                pixmap.fillRectangle(24, 17, 4, 13) // Slimmer arms
                pixmap.fillRectangle(32, 17, 4, 13)
                pixmap.fillRectangle(28, 5, 4, 18) // Slimmer legs
            }
            EnemyType.FRED_THE_HERMIT -> {
                pixmap.setColor(Color(0.6f, 0.2f, 0.3f, 1f))
                pixmap.fillCircle(30, 45, 7) // Smaller head
                pixmap.fillRectangle(26, 32, 8, 13) // Slimmer body
                pixmap.fillRectangle(24, 17, 4, 13) // Slimmer arms
                pixmap.fillRectangle(32, 17, 4, 13)
                pixmap.fillRectangle(28, 5, 4, 18) // Slimmer legs
            }
            EnemyType.MR_QUESTMARK -> {
                pixmap.setColor(Color(0.6f, 0.2f, 0.3f, 1f))
                pixmap.fillCircle(30, 45, 7) // Smaller head
                pixmap.fillRectangle(26, 32, 8, 13) // Slimmer body
                pixmap.fillRectangle(24, 17, 4, 13) // Slimmer arms
                pixmap.fillRectangle(32, 17, 4, 13)
                pixmap.fillRectangle(28, 5, 4, 18) // Slimmer legs
            }
        }

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegion(texture)
    }

    private fun createBehaviorIcon(behavior: EnemyBehavior): TextureRegion {
        val pixmap = Pixmap(40, 40, Pixmap.Format.RGBA8888)

        when (behavior) {
            EnemyBehavior.STATIONARY_SHOOTER -> {
                // Target/crosshair icon
                pixmap.setColor(Color(0.8f, 0.2f, 0.2f, 1f))
                pixmap.drawCircle(20, 20, 15)
                pixmap.drawCircle(20, 20, 10)
                pixmap.drawLine(20, 5, 20, 35) // Vertical line
                pixmap.drawLine(5, 20, 35, 20) // Horizontal line
            }
            EnemyBehavior.COWARD_HIDER -> {
                // Shield/hide icon
                pixmap.setColor(Color(0.2f, 0.6f, 0.8f, 1f))
                pixmap.fillCircle(20, 25, 12)
                pixmap.setColor(Color(0.1f, 0.4f, 0.6f, 1f))
                pixmap.fillRectangle(17, 22, 6, 8)
                // Add some "hiding" lines
                for (i in 0..3) {
                    pixmap.drawLine(12 + i * 4, 12, 12 + i * 4, 18)
                }
            }
            EnemyBehavior.AGGRESSIVE_RUSHER -> {
                // Arrow/charge icon
                pixmap.setColor(Color(0.8f, 0.4f, 0.2f, 1f))
                // Arrow pointing right
                pixmap.fillRectangle(8, 18, 20, 4)
                pixmap.fillRectangle(24, 12, 8, 16)
                // Arrow head
                for (i in 0..7) {
                    pixmap.drawLine(32 - i, 12 + i, 32 - i, 28 - i)
                }
            }
        }

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegion(texture)
    }

    private fun createModernBackground(): Drawable {
        // Create a modern dark background with subtle transparency
        val pixmap = Pixmap(100, 80, Pixmap.Format.RGBA8888)

        // Gradient effect
        for (y in 0 until 80) {
            val alpha = 0.85f + (y / 80f) * 0.1f // Subtle gradient
            pixmap.setColor(0.1f, 0.1f, 0.15f, alpha)
            pixmap.drawLine(0, y, 99, y)
        }

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createItemBackground(color: Color): Drawable {
        val pixmap = Pixmap(100, 130, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()

        // Add subtle border
        pixmap.setColor(color.r + 0.1f, color.g + 0.1f, color.b + 0.1f, color.a)
        pixmap.drawRectangle(0, 0, 100, 130)

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegionDrawable(TextureRegion(texture))
    }

    fun update() {
        val currentEnemyIndex = enemySystem.currentEnemyTypeIndex
        val currentBehaviorIndex = enemySystem.currentBehaviorIndex

        // Animate enemy type items
        for (i in enemyTypeItems.indices) {
            val item = enemyTypeItems[i]
            val isSelected = i == currentEnemyIndex

            // Create smooth transition animations
            val targetScale = if (isSelected) 1.1f else 1.0f
            val targetColor = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
            val targetStatsColor = if (isSelected) Color(0.9f, 0.9f, 0.9f, 1f) else Color(0.7f, 0.7f, 0.7f, 1f)
            val targetBackground = if (isSelected) item.selectedBackground else item.background

            // Apply animations using LibGDX actions
            item.container.clearActions()
            item.container.addAction(
                Actions.parallel(
                    Actions.scaleTo(targetScale, targetScale, 0.2f, Interpolation.smooth),
                    Actions.run {
                        item.container.background = targetBackground
                        item.nameLabel.color = targetColor
                        item.statsLabel.color = targetStatsColor
                    }
                )
            )

            // Add a subtle bounce effect for the selected item
            if (isSelected) {
                item.iconImage.clearActions()
                item.iconImage.addAction(
                    Actions.sequence(
                        Actions.scaleTo(1.2f, 1.2f, 0.1f, Interpolation.bounceOut),
                        Actions.scaleTo(1.0f, 1.0f, 0.1f, Interpolation.smooth)
                    )
                )
            }
        }

        // Animate behavior items
        for (i in behaviorItems.indices) {
            val item = behaviorItems[i]
            val isSelected = i == currentBehaviorIndex

            val targetScale = if (isSelected) 1.1f else 1.0f
            val targetColor = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
            val targetBackground = if (isSelected) item.selectedBackground else item.background

            item.container.clearActions()
            item.container.addAction(
                Actions.parallel(
                    Actions.scaleTo(targetScale, targetScale, 0.2f, Interpolation.smooth),
                    Actions.run {
                        item.container.background = targetBackground
                        item.nameLabel.color = targetColor
                    }
                )
            )

            if (isSelected) {
                item.iconImage.clearActions()
                item.iconImage.addAction(
                    Actions.sequence(
                        Actions.scaleTo(1.2f, 1.2f, 0.1f, Interpolation.bounceOut),
                        Actions.scaleTo(1.0f, 1.0f, 0.1f, Interpolation.smooth)
                    )
                )
            }
        }
    }

    fun show() {
        enemySelectionTable.setVisible(true)
    }

    fun hide() {
        enemySelectionTable.setVisible(false)
    }

    fun dispose() {
        // Dispose cached textures
        for (texture in loadedTextures.values) {
            texture.dispose()
        }
        loadedTextures.clear()
    }
}
