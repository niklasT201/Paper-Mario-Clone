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

class BackgroundSelectionUI(
    private val backgroundSystem: BackgroundSystem,
    private val skin: Skin,
    private val stage: Stage
) {
    private lateinit var backgroundSelectionTable: Table
    private lateinit var backgroundItems: MutableList<BackgroundSelectionItem>
    private val loadedTextures = mutableMapOf<String, Texture>() // Cache for loaded textures

    // Data class to hold background selection item components
    private data class BackgroundSelectionItem(
        val container: Table,
        val iconImage: Image,
        val nameLabel: Label,
        val sizeLabel: Label,
        val background: Drawable,
        val selectedBackground: Drawable,
        val backgroundType: BackgroundType
    )

    fun initialize() {
        setupBackgroundSelectionUI()
        backgroundSelectionTable.setVisible(false)
    }

    private fun setupBackgroundSelectionUI() {
        // Create background selection table at the top center
        backgroundSelectionTable = Table()
        backgroundSelectionTable.setFillParent(true)
        backgroundSelectionTable.top()
        backgroundSelectionTable.pad(40f)

        // Create main container with modern styling
        val mainContainer = Table()

        // Create a more modern background with rounded corners and shadow effect
        val backgroundStyle = createModernBackground()
        mainContainer.background = backgroundStyle
        mainContainer.pad(20f, 30f, 20f, 30f)

        // Title with modern styling
        val titleLabel = Label("Background Selection", skin)
        titleLabel.setFontScale(1.4f)
        titleLabel.color = Color(0.9f, 0.9f, 0.9f, 1f) // Light gray
        mainContainer.add(titleLabel).padBottom(15f).row()

        // Create horizontal container for background items
        val backgroundContainer = Table()
        backgroundContainer.pad(10f)

        // Create background items for each background type
        backgroundItems = mutableListOf()
        val backgroundTypes = BackgroundType.values()

        for (i in backgroundTypes.indices) {
            val backgroundType = backgroundTypes[i]
            val item = createBackgroundItem(backgroundType, i == backgroundSystem.currentSelectedBackgroundIndex)
            backgroundItems.add(item)

            // Add spacing between items
            if (i > 0) {
                backgroundContainer.add().width(15f) // Spacer
            }
            backgroundContainer.add(item.container).size(100f, 130f)
        }

        mainContainer.add(backgroundContainer).padBottom(10f).row()

        // Instructions with modern styling
        val instructionLabel = Label("Hold [N] + Mouse Wheel to change backgrounds", skin)
        instructionLabel.setFontScale(0.9f)
        instructionLabel.color = Color(0.7f, 0.7f, 0.7f, 1f) // Darker gray
        mainContainer.add(instructionLabel).padBottom(5f).row()

        // Additional instructions
        val additionalInstructionLabel = Label("Left click to place background | Right click to remove", skin)
        additionalInstructionLabel.setFontScale(0.8f)
        additionalInstructionLabel.color = Color(0.6f, 0.6f, 0.6f, 1f)
        mainContainer.add(additionalInstructionLabel)

        backgroundSelectionTable.add(mainContainer)
        stage.addActor(backgroundSelectionTable)
    }

    private fun createBackgroundItem(backgroundType: BackgroundType, isSelected: Boolean): BackgroundSelectionItem {
        val container = Table()
        container.pad(8f)

        // Create backgrounds for normal and selected states
        val normalBg = createItemBackground(Color(0.3f, 0.3f, 0.35f, 0.9f))
        val selectedBg = createItemBackground(Color(0.4f, 0.6f, 0.8f, 0.95f))

        container.background = if (isSelected) selectedBg else normalBg

        // Background icon - try to load actual texture first, fallback to generated icon
        val iconTexture = loadBackgroundTexture(backgroundType)
        val iconImage = Image(iconTexture)
        container.add(iconImage).size(60f, 50f).padBottom(8f).row()

        // Background name
        val nameLabel = Label(backgroundType.displayName, skin)
        nameLabel.setFontScale(0.7f)
        nameLabel.setWrap(true)
        nameLabel.setAlignment(Align.center)
        nameLabel.color = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
        container.add(nameLabel).width(90f).center().padBottom(3f).row()

        // Size info
        val sizeText = "${backgroundType.width.toInt()}x${backgroundType.height.toInt()}"
        val sizeLabel = Label(sizeText, skin)
        sizeLabel.setFontScale(0.6f)
        sizeLabel.color = if (isSelected) Color(0.9f, 0.9f, 0.9f, 1f) else Color(0.6f, 0.6f, 0.6f, 1f)
        container.add(sizeLabel).center()

        return BackgroundSelectionItem(container, iconImage, nameLabel, sizeLabel, normalBg, selectedBg, backgroundType)
    }

    private fun loadBackgroundTexture(backgroundType: BackgroundType): TextureRegion {
        val texturePath = backgroundType.texturePath

        // Check if we already have this texture cached
        if (loadedTextures.containsKey(texturePath)) {
            return TextureRegion(loadedTextures[texturePath]!!)
        }

        // Try to load the actual texture file
        return try {
            val fileHandle = Gdx.files.internal(texturePath)
            if (fileHandle.exists()) {
                val texture = Texture(fileHandle)
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                loadedTextures[texturePath] = texture // Cache it
                println("Loaded background texture: $texturePath")
                TextureRegion(texture)
            } else {
                println("Background texture not found: $texturePath, using fallback")
                createBackgroundIcon(backgroundType)
            }
        } catch (e: Exception) {
            println("Failed to load background texture: $texturePath, error: ${e.message}, using fallback")
            createBackgroundIcon(backgroundType)
        }
    }

    private fun createModernBackground(): Drawable {
        // Create a modern dark background with subtle transparency
        val pixmap = Pixmap(100, 60, Pixmap.Format.RGBA8888)

        // Gradient effect
        for (y in 0 until 60) {
            val alpha = 0.85f + (y / 60f) * 0.1f // Subtle gradient
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

    private fun createBackgroundIcon(backgroundType: BackgroundType): TextureRegion {
        // Create colored shapes representing different background types (fallback)
        val pixmap = Pixmap(60, 50, Pixmap.Format.RGBA8888)

        when (backgroundType) {
            BackgroundType.SMALL_HOUSE -> {
                // Simple house shape
                pixmap.setColor(Color(0.8f, 0.6f, 0.4f, 1f)) // Brown
                pixmap.fillRectangle(15, 25, 30, 20) // House body
                pixmap.setColor(Color(0.6f, 0.3f, 0.2f, 1f)) // Dark brown
                pixmap.fillTriangle(10, 25, 30, 10, 50, 25) // Roof
                pixmap.setColor(Color(0.4f, 0.2f, 0.1f, 1f)) // Very dark brown
                pixmap.fillRectangle(20, 35, 8, 10) // Door
            }
            BackgroundType.FLAT -> {
                // Apartment building
                pixmap.setColor(Color(0.7f, 0.7f, 0.7f, 1f)) // Gray
                pixmap.fillRectangle(10, 15, 40, 30) // Building body
                pixmap.setColor(Color(0.5f, 0.5f, 0.5f, 1f)) // Darker gray
                // Windows
                for (i in 0..2) {
                    for (j in 0..1) {
                        pixmap.fillRectangle(15 + i * 10, 20 + j * 10, 6, 6)
                    }
                }
            }
            BackgroundType.VILLA -> {
                // Large house
                pixmap.setColor(Color(0.9f, 0.8f, 0.6f, 1f)) // Light cream
                pixmap.fillRectangle(5, 20, 50, 25) // Main body
                pixmap.setColor(Color(0.7f, 0.4f, 0.3f, 1f)) // Red-brown
                pixmap.fillTriangle(0, 20, 30, 5, 60, 20) // Large roof
                pixmap.setColor(Color(0.3f, 0.6f, 0.3f, 1f)) // Green
                pixmap.fillRectangle(0, 40, 60, 10) // Garden/ground
            }
            BackgroundType.MANSION -> {
                pixmap.setColor(Color(0.9f, 0.8f, 0.6f, 1f)) // Light cream
                pixmap.fillRectangle(5, 20, 50, 25) // Main body
                pixmap.setColor(Color(0.7f, 0.4f, 0.3f, 1f)) // Red-brown
                pixmap.fillTriangle(0, 20, 30, 5, 60, 20) // Large roof
                pixmap.setColor(Color(0.3f, 0.6f, 0.3f, 1f)) // Green
                pixmap.fillRectangle(0, 40, 60, 10) // Garden/ground
            }
            BackgroundType.NICKELODEON -> {
                pixmap.setColor(Color(0.8f, 0.6f, 0.4f, 1f)) // Brown
                pixmap.fillRectangle(15, 25, 30, 20) // House body
                pixmap.setColor(Color(0.6f, 0.3f, 0.2f, 1f)) // Dark brown
                pixmap.fillTriangle(10, 25, 30, 10, 50, 25) // Roof
                pixmap.setColor(Color(0.4f, 0.2f, 0.1f, 1f)) // Very dark brown
                pixmap.fillRectangle(20, 35, 8, 10) // Door
            }
            BackgroundType.TRANSPARENT_HOUSE -> {
                // Apartment building
                pixmap.setColor(Color(0.7f, 0.7f, 0.7f, 1f)) // Gray
                pixmap.fillRectangle(10, 15, 40, 30) // Building body
                pixmap.setColor(Color(0.5f, 0.5f, 0.5f, 1f)) // Darker gray
                // Windows
                for (i in 0..2) {
                    for (j in 0..1) {
                        pixmap.fillRectangle(15 + i * 10, 20 + j * 10, 6, 6)
                    }
                }
            }
        }

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegion(texture)
    }

    fun update() {
        val currentIndex = backgroundSystem.currentSelectedBackgroundIndex

        // Animate all background items
        for (i in backgroundItems.indices) {
            val item = backgroundItems[i]
            val isSelected = i == currentIndex

            // Create smooth transition animations
            val targetScale = if (isSelected) 1.1f else 1.0f
            val targetColor = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
            val targetSizeColor = if (isSelected) Color(0.9f, 0.9f, 0.9f, 1f) else Color(0.6f, 0.6f, 0.6f, 1f)
            val targetBackground = if (isSelected) item.selectedBackground else item.background

            // Apply animations using LibGDX actions
            item.container.clearActions()
            item.container.addAction(
                Actions.parallel(
                    Actions.scaleTo(targetScale, targetScale, 0.2f, Interpolation.smooth),
                    Actions.run {
                        item.container.background = targetBackground
                        item.nameLabel.color = targetColor
                        item.sizeLabel.color = targetSizeColor
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
    }

    fun show() {
        backgroundSelectionTable.setVisible(true)
    }

    fun hide() {
        backgroundSelectionTable.setVisible(false)
    }

    fun dispose() {
        // Dispose cached textures
        for (texture in loadedTextures.values) {
            texture.dispose()
        }
        loadedTextures.clear()
    }
}
