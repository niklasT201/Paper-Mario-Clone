package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align

class BlockSelectionUI(
    private val blockSystem: BlockSystem,
    private val skin: Skin,
    private val stage: Stage
) {
    private lateinit var blockSelectionTable: Table
    private lateinit var categoryTabsTable: Table
    private lateinit var blockContainer: ScrollPane
    private lateinit var blockItemsTable: Table
    private lateinit var blockItems: MutableList<BlockSelectionItem>
    private lateinit var categoryButtons: MutableList<Button>
    private val loadedTextures = mutableMapOf<String, Texture>()
    private var lastSelectedIndex = -1
    private var currentCategory = BlockCategory.NATURAL
    private var filteredBlockTypes = listOf<BlockType>()

    // Data class to hold block selection item components
    private data class BlockSelectionItem(
        val container: Table,
        val iconImage: Image,
        val nameLabel: Label,
        val background: Drawable,
        val selectedBackground: Drawable,
        val blockType: BlockType,
        val globalIndex: Int
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
        titleLabel.color = Color(0.9f, 0.9f, 0.9f, 1f)
        mainContainer.add(titleLabel).padBottom(15f).row()

        // Create category tabs
        setupCategoryTabs(mainContainer)

        // Create scrollable container for block items
        blockItemsTable = Table()
        blockItemsTable.pad(10f)

        // Create scroll pane to handle many blocks
        blockContainer = ScrollPane(blockItemsTable, skin)
        blockContainer.setScrollingDisabled(false, true)
        blockContainer.setFadeScrollBars(false)

        // Set max size to prevent UI from becoming too large
        mainContainer.add(blockContainer).size(800f, 120f).padBottom(10f).row()

        // Instructions
        val instructionLabel = Label("Hold [B] + Mouse Wheel to change blocks | Click category tabs to filter", skin)
        instructionLabel.setFontScale(0.8f)
        instructionLabel.color = Color(0.7f, 0.7f, 0.7f, 1f)
        instructionLabel.setWrap(true)
        instructionLabel.setAlignment(Align.center)
        mainContainer.add(instructionLabel).width(800f)

        blockSelectionTable.add(mainContainer)
        stage.addActor(blockSelectionTable)

        // Initialize with first category
        updateCurrentCategory()
        refreshBlockItems()
    }

    private fun setupCategoryTabs(mainContainer: Table) {
        categoryTabsTable = Table()
        categoryButtons = mutableListOf()

        val categories = BlockCategory.entries.toTypedArray()

        for (category in categories) {
            val categoryButton = TextButton(category.displayName, skin)
            categoryButton.label.setFontScale(0.8f)

            // Create custom button style based on category color
            val buttonStyle = createCategoryButtonStyle(category)
            categoryButton.style = buttonStyle

            categoryButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    selectCategory(category)
                }
            })

            categoryButtons.add(categoryButton)
            categoryTabsTable.add(categoryButton).size(120f, 35f).pad(2f)
        }

        mainContainer.add(categoryTabsTable).padBottom(15f).row()
        updateCategoryButtonStates()
    }

    private fun createCategoryButtonStyle(category: BlockCategory): TextButton.TextButtonStyle {
        val style = TextButton.TextButtonStyle()

        // Create normal and active backgrounds
        val normalColor = Color(0.3f, 0.3f, 0.35f, 0.9f)
        val activeColor = Color.valueOf(category.color.toString(16).padStart(6, '0'))
        activeColor.a = 0.8f

        style.up = createButtonBackground(normalColor)
        style.down = createButtonBackground(activeColor)
        style.checked = createButtonBackground(activeColor)
        style.font = skin.getFont("default-font")
        style.fontColor = Color.WHITE
        style.downFontColor = Color.WHITE
        style.checkedFontColor = Color.WHITE

        return style
    }

    private fun createButtonBackground(color: Color): Drawable {
        val pixmap = Pixmap(120, 35, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()

        // Add border
        pixmap.setColor(color.r + 0.1f, color.g + 0.1f, color.b + 0.1f, color.a)
        pixmap.drawRectangle(0, 0, 120, 35)

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun selectCategory(category: BlockCategory) {
        if (currentCategory != category) {
            currentCategory = category
            updateCurrentCategory()
            refreshBlockItems()
            updateCategoryButtonStates()
        }
    }

    private fun updateCurrentCategory() {
        filteredBlockTypes = BlockType.getByCategory(currentCategory)
    }

    private fun updateCategoryButtonStates() {
        val categories = BlockCategory.entries.toTypedArray()
        for (i in categoryButtons.indices) {
            val button = categoryButtons[i]
            val category = categories[i]
            button.isChecked = category == currentCategory
        }
    }

    private fun refreshBlockItems() {
        // Clear existing items
        blockItemsTable.clear()
        blockItems = mutableListOf()

        val currentSelectedIndex = blockSystem.currentSelectedBlockIndex
        val allBlockTypes = BlockType.entries.toTypedArray()

        // Create block items for filtered blocks
        for (i in filteredBlockTypes.indices) {
            val blockType = filteredBlockTypes[i]
            val globalIndex = allBlockTypes.indexOf(blockType)
            val isSelected = globalIndex == currentSelectedIndex

            val item = createBlockItem(blockType, isSelected, globalIndex)
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
        val currentSelectedIndex = blockSystem.currentSelectedBlockIndex
        val allBlockTypes = BlockType.entries.toTypedArray()
        val selectedBlockType = allBlockTypes[currentSelectedIndex]

        // Find the index of the selected block in the current filtered list
        val filteredIndex = filteredBlockTypes.indexOf(selectedBlockType)

        if (filteredIndex != -1 && filteredBlockTypes.isNotEmpty()) {
            val scrollPercentage = filteredIndex.toFloat() / (filteredBlockTypes.size - 1).toFloat()
            blockContainer.addAction(
                Actions.run {
                    blockContainer.scrollPercentX = scrollPercentage.coerceIn(0f, 1f)
                }
            )
        }
    }

    private fun createBlockItem(blockType: BlockType, isSelected: Boolean, globalIndex: Int): BlockSelectionItem {
        val container = Table()
        container.pad(8f)

        // Create backgrounds
        val normalBg = createItemBackground(Color(0.3f, 0.3f, 0.35f, 0.9f))
        val selectedBg = createItemBackground(Color(0.4f, 0.6f, 0.8f, 0.95f))

        container.background = if (isSelected) selectedBg else normalBg

        // Block icon
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

        // Add click listener to select this block
        container.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                blockSystem.setSelectedBlock(globalIndex)
            }
        })

        return BlockSelectionItem(container, iconImage, nameLabel, normalBg, selectedBg, blockType, globalIndex)
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
                loadedTextures[texturePath] = texture
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
            val alpha = 0.85f + (y / 60f) * 0.1f
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
            else -> Color.GRAY // Default for any new blocks
        }

        // Fill with base color
        pixmap.setColor(color)
        pixmap.fill()

        // Add texture pattern
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
        val allBlockTypes = BlockType.entries.toTypedArray()
        val selectedBlockType = allBlockTypes[currentIndex]

        // Check if we need to switch category to show the selected block
        if (selectedBlockType.category != currentCategory) {
            selectCategory(selectedBlockType.category)
            return
        }

        if (currentIndex != lastSelectedIndex) {
            lastSelectedIndex = currentIndex
            refreshBlockItems()
            return
        }

        // Animate block items
        for (item in blockItems) {
            val isSelected = item.globalIndex == currentIndex
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
        updateCurrentCategory()
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
