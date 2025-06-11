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

class HouseSelectionUI(
    private val houseSystem: HouseSystem,
    private val skin: Skin,
    private val stage: Stage
) {
    private lateinit var houseSelectionTable: Table
    private lateinit var houseItems: MutableList<HouseSelectionItem>
    private val loadedTextures = mutableMapOf<String, Texture>() // Cache for loaded textures

    // Data class to hold house selection item components
    private data class HouseSelectionItem(
        val container: Table,
        val iconImage: Image,
        val nameLabel: Label,
        val background: Drawable,
        val selectedBackground: Drawable,
        val houseType: HouseType
    )

    fun initialize() {
        setupHouseSelectionUI()
        houseSelectionTable.setVisible(false)
    }

    private fun setupHouseSelectionUI() {
        // Create house selection table at the top center
        houseSelectionTable = Table()
        houseSelectionTable.setFillParent(true)
        houseSelectionTable.top()
        houseSelectionTable.pad(40f)

        // Create main container with modern styling
        val mainContainer = Table()

        // Create a more modern background with rounded corners and shadow effect
        val backgroundStyle = createModernBackground()
        mainContainer.background = backgroundStyle
        mainContainer.pad(20f, 30f, 20f, 30f)

        // Title with modern styling
        val titleLabel = Label("House Selection", skin)
        titleLabel.setFontScale(1.4f)
        titleLabel.color = Color(0.9f, 0.9f, 0.9f, 1f) // Light gray
        mainContainer.add(titleLabel).padBottom(15f).row()

        // Create horizontal container for house items
        val houseContainer = Table()
        houseContainer.pad(10f)

        // Create house items for each house type
        houseItems = mutableListOf()
        val houseTypes = HouseType.values()

        for (i in houseTypes.indices) {
            val houseType = houseTypes[i]
            val item = createHouseItem(houseType, i == houseSystem.currentSelectedHouseIndex)
            houseItems.add(item)

            // Add spacing between items
            if (i > 0) {
                houseContainer.add().width(15f) // Spacer
            }
            houseContainer.add(item.container).size(90f, 110f)
        }

        mainContainer.add(houseContainer).padBottom(10f).row()

        // Instructions with modern styling
        val instructionLabel = Label("Hold [H] + Mouse Wheel to change houses", skin)
        instructionLabel.setFontScale(0.9f)
        instructionLabel.color = Color(0.7f, 0.7f, 0.7f, 1f) // Darker gray
        mainContainer.add(instructionLabel).padBottom(5f).row()

        // Additional instructions
        val additionalLabel = Label("Houses have collision - players cannot walk through them", skin)
        additionalLabel.setFontScale(0.8f)
        additionalLabel.color = Color(0.6f, 0.6f, 0.6f, 1f)
        mainContainer.add(additionalLabel)

        houseSelectionTable.add(mainContainer)
        stage.addActor(houseSelectionTable)
    }

    private fun createHouseItem(houseType: HouseType, isSelected: Boolean): HouseSelectionItem {
        val container = Table()
        container.pad(8f)

        // Create backgrounds for normal and selected states
        val normalBg = createItemBackground(Color(0.3f, 0.3f, 0.35f, 0.9f))
        val selectedBg = createItemBackground(Color(0.6f, 0.4f, 0.8f, 0.95f)) // Purple theme for houses

        container.background = if (isSelected) selectedBg else normalBg

        // House icon - try to load actual texture first, fallback to generated icon
        val iconTexture = loadHouseTexture(houseType)
        val iconImage = Image(iconTexture)
        container.add(iconImage).size(50f, 50f).padBottom(8f).row()

        // House name
        val nameLabel = Label(houseType.displayName, skin)
        nameLabel.setFontScale(0.7f)
        nameLabel.setWrap(true)
        nameLabel.setAlignment(Align.center)
        nameLabel.color = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
        container.add(nameLabel).width(80f).center().padBottom(5f).row()

        // Size info
        val sizeLabel = Label("${houseType.width.toInt()}x${houseType.height.toInt()}", skin)
        sizeLabel.setFontScale(0.6f)
        sizeLabel.color = Color(0.7f, 0.7f, 0.7f, 1f)
        container.add(sizeLabel)

        return HouseSelectionItem(container, iconImage, nameLabel, normalBg, selectedBg, houseType)
    }

    private fun loadHouseTexture(houseType: HouseType): TextureRegion {
        val texturePath = houseType.texturePath

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
                println("Loaded house texture: $texturePath")
                TextureRegion(texture)
            } else {
                println("Texture not found: $texturePath, using fallback")
                createHouseIcon(houseType)
            }
        } catch (e: Exception) {
            println("Failed to load texture: $texturePath, error: ${e.message}, using fallback")
            createHouseIcon(houseType)
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
        val pixmap = Pixmap(90, 110, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()

        // Add subtle border
        pixmap.setColor(color.r + 0.1f, color.g + 0.1f, color.b + 0.1f, color.a)
        pixmap.drawRectangle(0, 0, 90, 110)

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createHouseIcon(houseType: HouseType): TextureRegion {
        // Create colored shapes representing different house types (fallback)
        val pixmap = Pixmap(50, 50, Pixmap.Format.RGBA8888)

        when (houseType) {
            HouseType.HOUSE_1 -> {
                // Small brown house
                pixmap.setColor(Color(0.6f, 0.4f, 0.2f, 1f))
                pixmap.fillRectangle(15, 25, 20, 20) // House body
                pixmap.setColor(Color(0.8f, 0.2f, 0.2f, 1f))
                pixmap.fillTriangle(10, 25, 25, 10, 40, 25) // Roof
            }
            HouseType.HOUSE_2 -> {
                // Medium house
                pixmap.setColor(Color(0.7f, 0.5f, 0.3f, 1f))
                pixmap.fillRectangle(12, 22, 26, 23) // House body
                pixmap.setColor(Color(0.6f, 0.2f, 0.2f, 1f))
                pixmap.fillTriangle(8, 22, 25, 8, 42, 22) // Roof
            }
            HouseType.HOUSE_3 -> {
                // Large house
                pixmap.setColor(Color(0.8f, 0.6f, 0.4f, 1f))
                pixmap.fillRectangle(10, 20, 30, 25) // House body
                pixmap.setColor(Color(0.5f, 0.2f, 0.2f, 1f))
                pixmap.fillTriangle(5, 20, 25, 5, 45, 20) // Roof
            }
            HouseType.MANSION -> {
                // Mansion - larger and more elaborate
                pixmap.setColor(Color(0.9f, 0.8f, 0.7f, 1f))
                pixmap.fillRectangle(5, 18, 40, 27) // Main body
                pixmap.setColor(Color(0.4f, 0.2f, 0.2f, 1f))
                pixmap.fillTriangle(2, 18, 25, 2, 48, 18) // Main roof
                pixmap.setColor(Color(0.8f, 0.7f, 0.6f, 1f))
                pixmap.fillRectangle(10, 30, 10, 10) // Tower
                pixmap.fillRectangle(30, 30, 10, 10) // Tower
            }
            HouseType.COTTAGE -> {
                // Small cozy cottage
                pixmap.setColor(Color(0.5f, 0.7f, 0.4f, 1f))
                pixmap.fillRectangle(18, 28, 14, 17) // House body
                pixmap.setColor(Color(0.9f, 0.7f, 0.2f, 1f))
                pixmap.fillTriangle(15, 28, 25, 15, 35, 28) // Roof
                pixmap.setColor(Color(0.3f, 0.5f, 0.2f, 1f))
                pixmap.fillCircle(10, 35, 3) // Bush
                pixmap.fillCircle(40, 35, 3) // Bush
            }
            HouseType.FLAT -> {
                // Flat
                pixmap.setColor(Color(0.6f, 0.4f, 0.2f, 1f))
                pixmap.fillRectangle(15, 25, 20, 20) // House body
                pixmap.setColor(Color(0.8f, 0.2f, 0.2f, 1f))
                pixmap.fillTriangle(10, 25, 25, 10, 40, 25) // Roof
            }
        }

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegion(texture)
    }

    fun update() {
        val currentIndex = houseSystem.currentSelectedHouseIndex

        // Animate all house items
        for (i in houseItems.indices) {
            val item = houseItems[i]
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
        houseSelectionTable.setVisible(true)
    }

    fun hide() {
        houseSelectionTable.setVisible(false)
    }

    fun dispose() {
        // Dispose cached textures
        for (texture in loadedTextures.values) {
            texture.dispose()
        }
        loadedTextures.clear()
    }
}
