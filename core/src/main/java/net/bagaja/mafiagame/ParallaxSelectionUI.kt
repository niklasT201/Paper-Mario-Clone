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

class ParallaxSelectionUI(
    private val parallaxSystem: ParallaxBackgroundSystem,
    private val skin: Skin,
    private val stage: Stage
) {
    private lateinit var parallaxSelectionTable: Table
    private lateinit var parallaxItems: MutableList<ParallaxSelectionItem>
    private lateinit var layerSelection: MutableList<LayerButton>
    private val loadedTextures = mutableMapOf<String, Texture>()

    // Current selection state
    private var currentSelectedImageIndex = 0
    private var currentSelectedLayer = 0
    private var isVisible = false

    // Data class for parallax selection items
    private data class ParallaxSelectionItem(
        val container: Table,
        val iconImage: Image,
        val nameLabel: Label,
        val layerLabel: Label,
        val sizeLabel: Label,
        val background: Drawable,
        val selectedBackground: Drawable,
        val imageType: ParallaxBackgroundSystem.ParallaxImageType
    )

    // Data class for layer buttons
    private data class LayerButton(
        val button: Button,
        val label: Label,
        val layerIndex: Int,
        val background: Drawable,
        val selectedBackground: Drawable
    )

    fun initialize() {
        setupParallaxSelectionUI()
        parallaxSelectionTable.setVisible(false)
    }

    private fun setupParallaxSelectionUI() {
        // Create parallax selection table at the top center
        parallaxSelectionTable = Table()
        parallaxSelectionTable.setFillParent(true)
        parallaxSelectionTable.top()
        parallaxSelectionTable.pad(40f)

        // Create main container with modern styling
        val mainContainer = Table()
        val backgroundStyle = createModernBackground()
        mainContainer.background = backgroundStyle
        mainContainer.pad(20f, 30f, 20f, 30f)

        // Title
        val titleLabel = Label("Parallax Image Selection", skin)
        titleLabel.setFontScale(1.4f)
        titleLabel.color = Color(0.9f, 0.9f, 0.9f, 1f)
        mainContainer.add(titleLabel).padBottom(15f).row()

        // Layer selection section
        val layerSectionLabel = Label("Select Layer:", skin)
        layerSectionLabel.setFontScale(1.1f)
        layerSectionLabel.color = Color(0.8f, 0.8f, 0.8f, 1f)
        mainContainer.add(layerSectionLabel).padBottom(10f).row()

        // Create layer selection buttons
        val layerContainer = Table()
        layerSelection = mutableListOf()

        for (i in 0..3) { // 4 layers (0-3)
            val layerButton = createLayerButton(i, i == currentSelectedLayer)
            layerSelection.add(layerButton)

            if (i > 0) {
                layerContainer.add().width(10f) // Spacer
            }
            layerContainer.add(layerButton.button).size(80f, 40f)
        }

        mainContainer.add(layerContainer).padBottom(20f).row()

        // Image selection section
        val imageSectionLabel = Label("Select Image:", skin)
        imageSectionLabel.setFontScale(1.1f)
        imageSectionLabel.color = Color(0.8f, 0.8f, 0.8f, 1f)
        mainContainer.add(imageSectionLabel).padBottom(10f).row()

        // Create scrollable container for parallax images
        val imageContainer = Table()
        val scrollPane = ScrollPane(imageContainer, skin)
        scrollPane.setFadeScrollBars(false)
        scrollPane.setScrollingDisabled(false, true) // Only horizontal scrolling

        // Create parallax image items
        parallaxItems = mutableListOf()
        val imageTypes = ParallaxBackgroundSystem.ParallaxImageType.values()

        for (i in imageTypes.indices) {
            val imageType = imageTypes[i]
            val item = createParallaxItem(imageType, i == currentSelectedImageIndex)
            parallaxItems.add(item)

            if (i > 0) {
                imageContainer.add().width(15f) // Spacer
            }
            imageContainer.add(item.container).size(120f, 150f)
        }

        mainContainer.add(scrollPane).size(600f, 180f).padBottom(15f).row()

        // Controls section
        val controlsContainer = Table()

        // Current selection info
        val currentImageType = imageTypes[currentSelectedImageIndex]
        val selectionInfo = Label("Selected: ${currentImageType.displayName} | Layer: $currentSelectedLayer", skin)
        selectionInfo.setFontScale(0.9f)
        selectionInfo.color = Color(0.7f, 0.9f, 0.7f, 1f)
        controlsContainer.add(selectionInfo).padBottom(10f).row()

        // Instructions
        val instructionLabel = Label("Hold [P] + Mouse Wheel to change selection", skin)
        instructionLabel.setFontScale(0.9f)
        instructionLabel.color = Color(0.7f, 0.7f, 0.7f, 1f)
        controlsContainer.add(instructionLabel).padBottom(5f).row()

        val additionalInstructionLabel = Label("Left click to place | Right click to remove | [L] to change layer", skin)
        additionalInstructionLabel.setFontScale(0.8f)
        additionalInstructionLabel.color = Color(0.6f, 0.6f, 0.6f, 1f)
        controlsContainer.add(additionalInstructionLabel).padBottom(5f).row()

        // Layer info display
        val layerInfoLabel = Label("Layer Info: Far→Near (0=Slowest, 3=Fastest)", skin)
        layerInfoLabel.setFontScale(0.75f)
        layerInfoLabel.color = Color(0.5f, 0.5f, 0.6f, 1f)
        controlsContainer.add(layerInfoLabel)

        mainContainer.add(controlsContainer)
        parallaxSelectionTable.add(mainContainer)
        stage.addActor(parallaxSelectionTable)
    }

    private fun createLayerButton(layerIndex: Int, isSelected: Boolean): LayerButton {
        val normalBg = createItemBackground(Color(0.25f, 0.25f, 0.3f, 0.9f))
        val selectedBg = createItemBackground(Color(0.3f, 0.5f, 0.7f, 0.95f))

        val button = Button(if (isSelected) selectedBg else normalBg)

        val layerNames = arrayOf("Far", "Mid-Far", "Mid-Near", "Near")
        val speedInfo = arrayOf("0.1x", "0.3x", "0.6x", "0.8x")

        val container = Table()
        val nameLabel = Label(layerNames[layerIndex], skin)
        nameLabel.setFontScale(0.8f)
        nameLabel.color = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
        container.add(nameLabel).row()

        val speedLabel = Label(speedInfo[layerIndex], skin)
        speedLabel.setFontScale(0.6f)
        speedLabel.color = if (isSelected) Color(0.9f, 0.9f, 0.9f, 1f) else Color(0.6f, 0.6f, 0.6f, 1f)
        container.add(speedLabel)

        button.add(container)

        return LayerButton(button, nameLabel, layerIndex, normalBg, selectedBg)
    }

    private fun createParallaxItem(imageType: ParallaxBackgroundSystem.ParallaxImageType, isSelected: Boolean): ParallaxSelectionItem {
        val container = Table()
        container.pad(8f)

        // Create backgrounds for normal and selected states
        val normalBg = createItemBackground(Color(0.3f, 0.3f, 0.35f, 0.9f))
        val selectedBg = createItemBackground(Color(0.4f, 0.6f, 0.8f, 0.95f))

        container.background = if (isSelected) selectedBg else normalBg

        // Image icon
        val iconTexture = loadParallaxTexture(imageType)
        val iconImage = Image(iconTexture)
        container.add(iconImage).size(80f, 60f).padBottom(8f).row()

        // Image name
        val nameLabel = Label(imageType.displayName, skin)
        nameLabel.setFontScale(0.75f)
        nameLabel.setWrap(true)
        nameLabel.setAlignment(Align.center)
        nameLabel.color = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
        container.add(nameLabel).width(110f).center().padBottom(3f).row()

        // Preferred layer info
        val layerLabel = Label("Layer: ${imageType.preferredLayer}", skin)
        layerLabel.setFontScale(0.65f)
        layerLabel.color = if (isSelected) Color(0.7f, 0.9f, 0.7f, 1f) else Color(0.5f, 0.7f, 0.5f, 1f)
        container.add(layerLabel).center().padBottom(3f).row()

        // Size info
        val sizeText = "${imageType.width.toInt()}x${imageType.height.toInt()}"
        val sizeLabel = Label(sizeText, skin)
        sizeLabel.setFontScale(0.6f)
        sizeLabel.color = if (isSelected) Color(0.9f, 0.9f, 0.9f, 1f) else Color(0.6f, 0.6f, 0.6f, 1f)
        container.add(sizeLabel).center()

        return ParallaxSelectionItem(container, iconImage, nameLabel, layerLabel, sizeLabel, normalBg, selectedBg, imageType)
    }

    private fun loadParallaxTexture(imageType: ParallaxBackgroundSystem.ParallaxImageType): TextureRegion {
        val texturePath = imageType.texturePath

        // Check cache first
        if (loadedTextures.containsKey(texturePath)) {
            return TextureRegion(loadedTextures[texturePath]!!)
        }

        // Try to load actual texture
        return try {
            val fileHandle = Gdx.files.internal(texturePath)
            if (fileHandle.exists()) {
                val texture = Texture(fileHandle)
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                loadedTextures[texturePath] = texture
                println("Loaded parallax texture: $texturePath")
                TextureRegion(texture)
            } else {
                println("Parallax texture not found: $texturePath, using fallback")
                createParallaxIcon(imageType)
            }
        } catch (e: Exception) {
            println("Failed to load parallax texture: $texturePath, error: ${e.message}")
            createParallaxIcon(imageType)
        }
    }

    private fun createParallaxIcon(imageType: ParallaxBackgroundSystem.ParallaxImageType): TextureRegion {
        val pixmap = Pixmap(80, 60, Pixmap.Format.RGBA8888)

        when (imageType) {
            ParallaxBackgroundSystem.ParallaxImageType.MOUNTAINS -> {
                // Mountain silhouette
                pixmap.setColor(Color(0.4f, 0.5f, 0.7f, 1f))
                pixmap.fillTriangle(10, 50, 25, 20, 40, 50)
                pixmap.fillTriangle(30, 50, 45, 15, 60, 50)
                pixmap.fillTriangle(50, 50, 65, 25, 75, 50)
            }
            ParallaxBackgroundSystem.ParallaxImageType.HILLS -> {
                // Rolling hills
                pixmap.setColor(Color(0.5f, 0.7f, 0.4f, 1f))
                for (x in 0..79) {
                    val height = (Math.sin(x * 0.1) * 15 + 35).toInt()
                    pixmap.drawLine(x, height, x, 60)
                }
            }
            ParallaxBackgroundSystem.ParallaxImageType.FOREST -> {
                // Simple trees
                pixmap.setColor(Color(0.2f, 0.6f, 0.2f, 1f))
                for (i in 0..6) {
                    val x = 5 + i * 12
                    pixmap.fillRectangle(x, 45, 3, 15) // Trunk
                    pixmap.fillCircle(x + 1, 40, 8) // Leaves
                }
            }
            ParallaxBackgroundSystem.ParallaxImageType.CITY_SKYLINE -> {
                // City buildings
                pixmap.setColor(Color(0.6f, 0.6f, 0.7f, 1f))
                val heights = intArrayOf(40, 25, 50, 35, 45, 30, 40)
                for (i in heights.indices) {
                    val x = i * 11
                    val height = heights[i]
                    pixmap.fillRectangle(x, 60 - height, 10, height)
                    // Windows
                    pixmap.setColor(Color(0.9f, 0.9f, 0.5f, 1f))
                    for (row in 0 until height / 8) {
                        pixmap.fillRectangle(x + 2, 60 - height + row * 8 + 2, 2, 2)
                        pixmap.fillRectangle(x + 6, 60 - height + row * 8 + 2, 2, 2)
                    }
                    pixmap.setColor(Color(0.6f, 0.6f, 0.7f, 1f))
                }
            }
            ParallaxBackgroundSystem.ParallaxImageType.BUILDINGS -> {
                // Individual buildings
                pixmap.setColor(Color(0.7f, 0.5f, 0.4f, 1f))
                pixmap.fillRectangle(10, 25, 25, 35)
                pixmap.fillRectangle(45, 30, 20, 30)
                pixmap.setColor(Color(0.5f, 0.3f, 0.2f, 1f))
                // Roofs
                pixmap.fillTriangle(5, 25, 22, 10, 40, 25)
                pixmap.fillTriangle(40, 30, 55, 15, 70, 30)
            }
            ParallaxBackgroundSystem.ParallaxImageType.TREES -> {
                // Individual trees
                pixmap.setColor(Color(0.4f, 0.3f, 0.2f, 1f))
                pixmap.fillRectangle(15, 45, 4, 15) // Trunk 1
                pixmap.fillRectangle(35, 40, 5, 20) // Trunk 2
                pixmap.fillRectangle(55, 42, 4, 18) // Trunk 3

                pixmap.setColor(Color(0.3f, 0.7f, 0.3f, 1f))
                pixmap.fillCircle(17, 40, 12) // Leaves 1
                pixmap.fillCircle(37, 35, 15) // Leaves 2
                pixmap.fillCircle(57, 37, 13) // Leaves 3
            }
            ParallaxBackgroundSystem.ParallaxImageType.TEST_HOUSE -> {
                // Simple house
                pixmap.setColor(Color(0.8f, 0.6f, 0.4f, 1f))
                pixmap.fillRectangle(25, 35, 30, 25)
                pixmap.setColor(Color(0.6f, 0.3f, 0.2f, 1f))
                pixmap.fillTriangle(20, 35, 40, 20, 60, 35)
                pixmap.setColor(Color(0.4f, 0.2f, 0.1f, 1f))
                pixmap.fillRectangle(30, 45, 8, 15) // Door
            }
            ParallaxBackgroundSystem.ParallaxImageType.TEST_VILLA -> {
                // Large villa
                pixmap.setColor(Color(0.9f, 0.8f, 0.6f, 1f))
                pixmap.fillRectangle(15, 30, 50, 30)
                pixmap.setColor(Color(0.7f, 0.4f, 0.3f, 1f))
                pixmap.fillTriangle(10, 30, 40, 15, 70, 30)
                pixmap.setColor(Color(0.3f, 0.6f, 0.3f, 1f))
                pixmap.fillRectangle(10, 55, 60, 5) // Garden
            }
        }

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegion(texture)
    }

    private fun createModernBackground(): Drawable {
        val pixmap = Pixmap(100, 80, Pixmap.Format.RGBA8888)

        // Gradient background
        for (y in 0 until 80) {
            val alpha = 0.85f + (y / 80f) * 0.1f
            pixmap.setColor(0.1f, 0.1f, 0.15f, alpha)
            pixmap.drawLine(0, y, 99, y)
        }

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createItemBackground(color: Color): Drawable {
        val pixmap = Pixmap(120, 150, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()

        // Subtle border
        pixmap.setColor(color.r + 0.1f, color.g + 0.1f, color.b + 0.1f, color.a)
        pixmap.drawRectangle(0, 0, 120, 150)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    // Public methods for controlling the UI
    fun nextImage() {
        currentSelectedImageIndex = (currentSelectedImageIndex + 1) % parallaxItems.size
        update()
    }

    fun previousImage() {
        currentSelectedImageIndex = if (currentSelectedImageIndex > 0) {
            currentSelectedImageIndex - 1
        } else {
            parallaxItems.size - 1
        }
        update()
    }

    fun nextLayer() {
        currentSelectedLayer = (currentSelectedLayer + 1) % 4
        update()
    }

    fun previousLayer() {
        currentSelectedLayer = if (currentSelectedLayer > 0) {
            currentSelectedLayer - 1
        } else {
            3
        }
        update()
    }

    fun getCurrentSelectedImageType(): ParallaxBackgroundSystem.ParallaxImageType {
        return parallaxItems[currentSelectedImageIndex].imageType
    }

    fun getCurrentSelectedLayer(): Int {
        return currentSelectedLayer
    }

    fun update() {
        if (!isVisible) return

        // Update image selection UI
        for (i in parallaxItems.indices) {
            val item = parallaxItems[i]
            val isSelected = i == currentSelectedImageIndex

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
                        item.layerLabel.color = if (isSelected) Color(0.7f, 0.9f, 0.7f, 1f) else Color(0.5f, 0.7f, 0.5f, 1f)
                        item.sizeLabel.color = if (isSelected) Color(0.9f, 0.9f, 0.9f, 1f) else Color(0.6f, 0.6f, 0.6f, 1f)
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

        // Update layer selection UI
        for (i in layerSelection.indices) {
            val layerButton = layerSelection[i]
            val isSelected = i == currentSelectedLayer

            val targetBackground = if (isSelected) layerButton.selectedBackground else layerButton.background
            layerButton.button.style.up = targetBackground
            layerButton.label.color = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
        }
    }

    fun show() {
        isVisible = true
        parallaxSelectionTable.setVisible(true)
        update()
    }

    fun hide() {
        isVisible = false
        parallaxSelectionTable.setVisible(false)
    }

    fun isVisible(): Boolean = isVisible

    fun dispose() {
        for (texture in loadedTextures.values) {
            texture.dispose()
        }
        loadedTextures.clear()
    }
}
