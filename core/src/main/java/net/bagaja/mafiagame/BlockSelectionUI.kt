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

class BlockSelectionUI(
    private val blockSystem: BlockSystem,
    private val skin: Skin,
    private val stage: Stage
) {
    private lateinit var blockSelectionTable: Table
    private lateinit var blockContainer: ScrollPane
    private lateinit var blockItemsTable: Table
    private lateinit var blockItems: MutableList<BlockSelectionItem>
    private val loadedTextures = mutableMapOf<String, Texture>() // Cache for loaded textures
    private var lastSelectedIndex = -1 // Track last selected index to detect changes

    // Data class to hold block selection item components
    private data class BlockSelectionItem(
        val container: Table,
        val iconImage: Image,
        val nameLabel: Label,
        val background: Drawable,
        val selectedBackground: Drawable,
        val blockType: BlockType
    )

    fun initialize() {
        setupBlockSelectionUI()
        blockSelectionTable.setVisible(false)
    }

    private fun setupBlockSelectionUI() {
        // Create block selection table at the top center
        blockSelectionTable = Table()
        blockSelectionTable.setFillParent(true)
        blockSelectionTable.top()
        blockSelectionTable.pad(40f)

        // Create main container with modern styling
        val mainContainer = Table()

        // Create a more modern background with rounded corners and shadow effect
        val backgroundStyle = createModernBackground()
        mainContainer.background = backgroundStyle
        mainContainer.pad(20f, 30f, 20f, 30f)

        // Title with modern styling
        val titleLabel = Label("Block Selection", skin)
        titleLabel.setFontScale(1.4f)
        titleLabel.color = Color(0.9f, 0.9f, 0.9f, 1f) // Light gray
        mainContainer.add(titleLabel).padBottom(15f).row()

        // Create scrollable container for block items
        blockItemsTable = Table()
        blockItemsTable.pad(10f)

        // Create scroll pane to handle many blocks
        blockContainer = ScrollPane(blockItemsTable, skin)
        blockContainer.setScrollingDisabled(false, true) // Allow horizontal scrolling only
        blockContainer.setFadeScrollBars(false)

        // Set max size to prevent UI from becoming too large
        mainContainer.add(blockContainer).size(800f, 120f).padBottom(10f).row()

        // Instructions with modern styling
        val instructionLabel = Label("Hold [B] + Mouse Wheel to change blocks", skin)
        instructionLabel.setFontScale(0.9f)
        instructionLabel.color = Color(0.7f, 0.7f, 0.7f, 1f) // Darker gray
        mainContainer.add(instructionLabel)

        blockSelectionTable.add(mainContainer)
        stage.addActor(blockSelectionTable)

        // Initial setup of block items
        refreshBlockItems()
    }

    private fun refreshBlockItems() {
        // Clear existing items
        blockItemsTable.clear()
        blockItems = mutableListOf()

        val blockTypes = BlockType.values()
        val currentSelectedIndex = blockSystem.currentSelectedBlockIndex

        // Create block items for each block type
        for (i in blockTypes.indices) {
            val blockType = blockTypes[i]
            val item = createBlockItem(blockType, i == currentSelectedIndex)
            blockItems.add(item)

            // Add spacing between items
            if (i > 0) {
                blockItemsTable.add().width(15f) // Spacer
            }
            blockItemsTable.add(item.container).size(80f, 100f)
        }

        // Update scroll position to keep selected item visible
        scrollToSelectedItem()
    }

    private fun scrollToSelectedItem() {
        val selectedIndex = blockSystem.currentSelectedBlockIndex
        val totalItems = BlockType.values().size

        if (totalItems > 0) {
            // Calculate the percentage position of the selected item
            val scrollPercentage = selectedIndex.toFloat() / (totalItems - 1).toFloat()

            // Smooth scroll to the selected item
            blockContainer.addAction(
                Actions.run {
                    blockContainer.scrollPercentX = scrollPercentage.coerceIn(0f, 1f)
                }
            )
        }
    }

    private fun createBlockItem(blockType: BlockType, isSelected: Boolean): BlockSelectionItem {
        val container = Table()
        container.pad(8f)

        // Create backgrounds for normal and selected states
        val normalBg = createItemBackground(Color(0.3f, 0.3f, 0.35f, 0.9f))
        val selectedBg = createItemBackground(Color(0.4f, 0.6f, 0.8f, 0.95f))

        container.background = if (isSelected) selectedBg else normalBg

        // Block icon - try to load actual texture first, fallback to generated icon
        val iconTexture = loadBlockTexture(blockType)
        val iconImage = Image(iconTexture)
        container.add(iconImage).size(40f, 40f).padBottom(8f).row()

        // Block name
        val nameLabel = Label(blockType.displayName, skin)
        nameLabel.setFontScale(0.7f)
        nameLabel.setWrap(true)
        nameLabel.setAlignment(Align.center)
        nameLabel.color = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
        container.add(nameLabel).width(70f).center()

        return BlockSelectionItem(container, iconImage, nameLabel, normalBg, selectedBg, blockType)
    }

    private fun loadBlockTexture(blockType: BlockType): TextureRegion {
        val texturePath = blockType.texturePath

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
                println("Loaded block texture: $texturePath")
                TextureRegion(texture)
            } else {
                println("Texture not found: $texturePath, using fallback")
                createBlockIcon(blockType)
            }
        } catch (e: Exception) {
            println("Failed to load texture: $texturePath, error: ${e.message}, using fallback")
            createBlockIcon(blockType)
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
        val pixmap = Pixmap(80, 100, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()

        // Add subtle border
        pixmap.setColor(color.r + 0.1f, color.g + 0.1f, color.b + 0.1f, color.a)
        pixmap.drawRectangle(0, 0, 80, 100)

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createBlockIcon(blockType: BlockType): TextureRegion {
        // Create colored squares representing different block types (fallback)
        val pixmap = Pixmap(32, 32, Pixmap.Format.RGBA8888)

        val color = when (blockType) {
            BlockType.GRASS -> Color(0.4f, 0.8f, 0.2f, 1f)
            BlockType.COBBLESTONE -> Color(0.6f, 0.6f, 0.6f, 1f)
            BlockType.ROOM_FLOOR -> Color(0.8f, 0.7f, 0.5f, 1f)
            BlockType.STONE -> Color(0.5f, 0.5f, 0.5f, 1f)
            BlockType.WINDOW_OPENED -> Color(0.7f, 0.9f, 1f, 1f)
            BlockType.WINDOW_CLOSE -> Color(0.5f, 0.7f, 0.9f, 1f)
            BlockType.RESTAURANT_FLOOR -> Color(0.9f, 0.8f, 0.6f, 1f)
            BlockType.CARGO_FLOOR -> Color(0.7f, 0.6f, 0.4f, 1f)
            BlockType.BRICK_WALL -> Color(0.8f, 0.4f, 0.3f, 1f)
            BlockType.STREET_LOW -> Color.BLUE
            BlockType.BETON_TILE -> Color(0.75f, 0.75f, 0.75f, 1f)
            BlockType.BRICK_WALL_PNG -> Color(0.8f, 0.4f, 0.3f, 1f)
            BlockType.BROKEN_CEILING -> Color(0.8f, 0.8f, 0.8f, 1f)
            BlockType.BROKEN_WALL -> Color(0.7f, 0.6f, 0.5f, 1f)
            BlockType.BROWN_BRICK_WALL -> Color(0.6f, 0.4f, 0.3f, 1f)
            BlockType.BROWN_CLEAR_FLOOR -> Color(0.7f, 0.5f, 0.3f, 1f)
            BlockType.BROWN_FLOOR -> Color(0.6f, 0.4f, 0.2f, 1f)
            BlockType.CARD_FLOOR -> Color(0.9f, 0.9f, 0.8f, 1f)
            BlockType.CARPET -> Color(0.6f, 0.2f, 0.2f, 1f)
            BlockType.CEILING_WITH_LAMP -> Color(0.95f, 0.95f, 0.9f, 1f)
            BlockType.CEILING -> Color(0.9f, 0.9f, 0.9f, 1f)
            BlockType.CLUSTER_FLOOR -> Color(0.6f, 0.6f, 0.7f, 1f)
            BlockType.CRACKED_WALL -> Color(0.8f, 0.8f, 0.7f, 1f)
            BlockType.DARK_WALL -> Color(0.3f, 0.3f, 0.3f, 1f)
            BlockType.DARK_YELLOW_FLOOR -> Color(0.7f, 0.6f, 0.2f, 1f)
            BlockType.DIRTY_GROUND -> Color(0.5f, 0.4f, 0.3f, 1f)
            BlockType.FLIESSEN -> Color(0.9f, 0.95f, 1f, 1f)
            BlockType.FLOOR -> Color(0.8f, 0.8f, 0.8f, 1f)
            BlockType.GRAY_FLOOR -> Color(0.6f, 0.6f, 0.6f, 1f)
            BlockType.LIGHT_CEILING -> Color(0.95f, 0.95f, 1f, 1f)
            BlockType.OFFICE_WALL -> Color(0.9f, 0.9f, 0.85f, 1f)
            BlockType.SIDEWALK -> Color(0.7f, 0.7f, 0.7f, 1f)
            BlockType.SIDEWALK_START -> Color(0.75f, 0.75f, 0.75f, 1f)
            BlockType.SPRAYED_WALL -> Color(0.7f, 0.8f, 0.6f, 1f)
            BlockType.STREET_TILE -> Color(0.4f, 0.4f, 0.4f, 1f)
            BlockType.STRIPED_FLOOR -> Color(0.8f, 0.7f, 0.6f, 1f)
            BlockType.STRIPED_TAPETE -> Color(0.9f, 0.8f, 0.7f, 1f)
            BlockType.TAPETE -> Color(0.8f, 0.7f, 0.6f, 1f)
            BlockType.TAPETE_WALL -> Color(0.85f, 0.75f, 0.65f, 1f)
            BlockType.TRANS_WALL -> Color(0.9f, 0.9f, 0.9f, 0.8f)
            BlockType.WALL -> Color(0.9f, 0.9f, 0.9f, 1f)
            BlockType.WOOD_WALL -> Color(0.6f, 0.4f, 0.2f, 1f)
            BlockType.WOODEN_FLOOR -> Color(0.7f, 0.5f, 0.3f, 1f)
        }

        // Fill with base color
        pixmap.setColor(color)
        pixmap.fill()

        // Add some texture/pattern
        pixmap.setColor(color.r * 0.8f, color.g * 0.8f, color.b * 0.8f, 1f)
        for (i in 0 until 32 step 4) {
            pixmap.drawLine(i, 0, i, 31)
            pixmap.drawLine(0, i, 31, i)
        }

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegion(texture)
    }

    fun update() {
        val currentIndex = blockSystem.currentSelectedBlockIndex

        // Check if selection has changed
        if (currentIndex != lastSelectedIndex) {
            lastSelectedIndex = currentIndex

            // If we don't have the right number of items, refresh completely
            if (blockItems.size != BlockType.values().size) {
                refreshBlockItems()
                return
            }

            // Scroll to the selected item
            scrollToSelectedItem()
        }

        // Animate all block items
        for (i in blockItems.indices) {
            val item = blockItems[i]
            val isSelected = i == currentIndex

            // Create smooth transition animations
            val targetScale = if (isSelected) 1.1f else 1.0f
            val targetColor = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
            val targetBackground = if (isSelected) item.selectedBackground else item.background

            // Apply animations using LibGDX actions
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
        blockSelectionTable.setVisible(true)
        // Refresh items when showing to ensure we have all current blocks
        refreshBlockItems()
    }

    fun hide() {
        blockSelectionTable.setVisible(false)
    }

    fun dispose() {
        // Dispose cached textures
        for (texture in loadedTextures.values) {
            texture.dispose()
        }
        loadedTextures.clear()
    }
}
