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

class ItemSelectionUI(
    private val itemSystem: ItemSystem,
    private val skin: Skin,
    private val stage: Stage
) {
    private lateinit var itemSelectionTable: Table
    private lateinit var itemItems: MutableList<ItemSelectionItem>
    private val loadedTextures = mutableMapOf<String, Texture>() // Cache for loaded textures

    // Data class to hold item selection item components
    private data class ItemSelectionItem(
        val container: Table,
        val iconImage: Image,
        val nameLabel: Label,
        val valueLabel: Label,
        val background: Drawable,
        val selectedBackground: Drawable,
        val itemType: ItemType
    )

    fun initialize() {
        setupItemSelectionUI()
        itemSelectionTable.setVisible(false)
    }

    private fun setupItemSelectionUI() {
        // Create item selection table at the top center
        itemSelectionTable = Table()
        itemSelectionTable.setFillParent(true)
        itemSelectionTable.top()
        itemSelectionTable.pad(40f)

        // Create main container with modern styling
        val mainContainer = Table()

        // Create a more modern background with rounded corners and shadow effect
        val backgroundStyle = createModernBackground()
        mainContainer.background = backgroundStyle
        mainContainer.pad(20f, 30f, 20f, 30f)

        // Title with modern styling
        val titleLabel = Label("Item Selection", skin)
        titleLabel.setFontScale(1.4f)
        titleLabel.color = Color(0.9f, 0.9f, 0.9f, 1f) // Light gray
        mainContainer.add(titleLabel).padBottom(15f).row()

        // Create horizontal container for item items
        val itemContainer = Table()
        itemContainer.pad(10f)

        // Create item items for each item type
        itemItems = mutableListOf()
        val itemTypes = ItemType.values()

        for (i in itemTypes.indices) {
            val itemType = itemTypes[i]
            val item = createItemItem(itemType, i == itemSystem.currentSelectedItemIndex)
            itemItems.add(item)

            // Add spacing between items
            if (i > 0) {
                itemContainer.add().width(15f) // Spacer
            }
            itemContainer.add(item.container).size(90f, 120f)
        }

        mainContainer.add(itemContainer).padBottom(10f).row()

        // Instructions with modern styling
        val instructionLabel = Label("Hold [I] + Mouse Wheel to change items", skin)
        instructionLabel.setFontScale(0.9f)
        instructionLabel.color = Color(0.7f, 0.7f, 0.7f, 1f) // Darker gray
        mainContainer.add(instructionLabel)

        itemSelectionTable.add(mainContainer)
        stage.addActor(itemSelectionTable)
    }

    private fun createItemItem(itemType: ItemType, isSelected: Boolean): ItemSelectionItem {
        val container = Table()
        container.pad(8f)

        // Create backgrounds for normal and selected states
        val normalBg = createItemBackground(Color(0.3f, 0.3f, 0.35f, 0.9f))
        val selectedBg = createItemBackground(Color(0.8f, 0.6f, 0.2f, 0.95f)) // Golden color for items

        container.background = if (isSelected) selectedBg else normalBg

        // Item icon - try to load actual texture first, fallback to generated icon
        val iconTexture = loadItemTexture(itemType)
        val iconImage = Image(iconTexture)
        container.add(iconImage).size(50f, 50f).padBottom(8f).row()

        // Item name
        val nameLabel = Label(itemType.displayName, skin)
        nameLabel.setFontScale(0.7f)
        nameLabel.setWrap(true)
        nameLabel.setAlignment(Align.center)
        nameLabel.color = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
        container.add(nameLabel).width(80f).center().padBottom(3f).row()

        // Item value
        val valueLabel = Label("Value: ${itemType.value}", skin)
        valueLabel.setFontScale(0.6f)
        valueLabel.setAlignment(Align.center)
        valueLabel.color = if (isSelected) Color(1f, 1f, 0.8f, 1f) else Color(0.7f, 0.7f, 0.7f, 1f)
        container.add(valueLabel).width(80f).center()

        return ItemSelectionItem(container, iconImage, nameLabel, valueLabel, normalBg, selectedBg, itemType)
    }

    private fun loadItemTexture(itemType: ItemType): TextureRegion {
        val texturePath = itemType.texturePath

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
                println("Successfully loaded item texture: $texturePath")
                TextureRegion(texture)
            } else {
                println("ERROR: Texture file not found: $texturePath")
                println("Make sure the file exists in your assets folder")
                // Still fallback to generated icon if texture is missing
                createItemIcon(itemType)
            }
        } catch (e: Exception) {
            println("ERROR: Failed to load texture: $texturePath")
            println("Error details: ${e.message}")
            // Fallback to generated icon
            createItemIcon(itemType)
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
        val pixmap = Pixmap(90, 120, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()

        // Add subtle border
        pixmap.setColor(color.r + 0.1f, color.g + 0.1f, color.b + 0.1f, color.a)
        pixmap.drawRectangle(0, 0, 90, 120)

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createItemIcon(itemType: ItemType): TextureRegion {
        // Create colored shapes representing different item types (fallback)
        val pixmap = Pixmap(50, 50, Pixmap.Format.RGBA8888)

        when (itemType) {
            ItemType.MONEY_STACK -> {
                // Green money stack
                pixmap.setColor(Color(0.2f, 0.8f, 0.2f, 1f))
                for (i in 0..3) {
                    pixmap.fillRectangle(15 + i, 20 + i * 2, 20, 8)
                }
                // Dollar sign
                pixmap.setColor(Color.WHITE)
                pixmap.fillRectangle(24, 15, 2, 20)
                pixmap.fillRectangle(20, 20, 10, 2)
                pixmap.fillRectangle(20, 28, 10, 2)
            }
            ItemType.REVOLVER, ItemType.SMALLER_REVOLVER -> {
                // Gray revolver
                pixmap.setColor(Color(0.5f, 0.5f, 0.5f, 1f))
                pixmap.fillRectangle(10, 20, 30, 8) // Barrel
                pixmap.fillCircle(15, 24, 8) // Cylinder
                pixmap.fillRectangle(15, 30, 10, 6) // Handle
                pixmap.setColor(Color(0.3f, 0.2f, 0.1f, 1f))
                pixmap.fillRectangle(18, 32, 4, 8) // Grip
            }
            ItemType.SHOTGUN, ItemType.SMALL_SHOTGUN -> {
                // Brown shotgun
                pixmap.setColor(Color(0.4f, 0.3f, 0.2f, 1f))
                pixmap.fillRectangle(5, 22, 40, 6) // Barrel
                pixmap.setColor(Color(0.5f, 0.5f, 0.5f, 1f))
                pixmap.fillRectangle(25, 28, 15, 4) // Stock
                pixmap.fillRectangle(35, 18, 4, 6) // Trigger guard
            }
            ItemType.TOMMY_GUN -> {
                // Black tommy gun
                pixmap.setColor(Color(0.2f, 0.2f, 0.2f, 1f))
                pixmap.fillRectangle(5, 22, 35, 6) // Barrel
                pixmap.fillCircle(10, 16, 6) // Drum magazine
                pixmap.setColor(Color(0.4f, 0.3f, 0.2f, 1f))
                pixmap.fillRectangle(30, 28, 12, 4) // Stock
                pixmap.fillRectangle(25, 18, 4, 6) // Trigger guard
            }
            ItemType.MOLOTOV -> {
                pixmap.setColor(Color(0.2f, 0.2f, 0.2f, 1f))
                pixmap.fillRectangle(5, 22, 35, 6) // Barrel
                pixmap.fillCircle(10, 16, 6) // Drum magazine
                pixmap.setColor(Color(0.4f, 0.3f, 0.2f, 1f))
                pixmap.fillRectangle(30, 28, 12, 4) // Stock
                pixmap.fillRectangle(25, 18, 4, 6) // Trigger guard
            }
            ItemType.DYNAMITE -> {
                pixmap.setColor(Color(0.2f, 0.2f, 0.2f, 1f))
                pixmap.fillRectangle(5, 22, 35, 6) // Barrel
                pixmap.fillCircle(10, 16, 6) // Drum magazine
                pixmap.setColor(Color(0.4f, 0.3f, 0.2f, 1f))
                pixmap.fillRectangle(30, 28, 12, 4) // Stock
                pixmap.fillRectangle(25, 18, 4, 6) // Trigger guard
            }
        }

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegion(texture)
    }

    fun update() {
        val currentIndex = itemSystem.currentSelectedItemIndex

        // Animate all item items
        for (i in itemItems.indices) {
            val item = itemItems[i]
            val isSelected = i == currentIndex

            // Create smooth transition animations
            val targetScale = if (isSelected) 1.1f else 1.0f
            val targetColor = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
            val targetValueColor = if (isSelected) Color(1f, 1f, 0.8f, 1f) else Color(0.7f, 0.7f, 0.7f, 1f)
            val targetBackground = if (isSelected) item.selectedBackground else item.background

            // Apply animations using LibGDX actions
            item.container.clearActions()
            item.container.addAction(
                Actions.parallel(
                    Actions.scaleTo(targetScale, targetScale, 0.2f, Interpolation.smooth),
                    Actions.run {
                        item.container.background = targetBackground
                        item.nameLabel.color = targetColor
                        item.valueLabel.color = targetValueColor
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
        itemSelectionTable.setVisible(true)
    }

    fun hide() {
        itemSelectionTable.setVisible(false)
    }

    fun dispose() {
        // Dispose cached textures
        for (texture in loadedTextures.values) {
            texture.dispose()
        }
        loadedTextures.clear()
    }
}
