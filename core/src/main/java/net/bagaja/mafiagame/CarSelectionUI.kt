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

class CarSelectionUI(
    private val carSystem: CarSystem,
    private val skin: Skin,
    private val stage: Stage
) {
    private lateinit var carSelectionTable: Table
    private lateinit var carItems: MutableList<CarSelectionItem>
    private val loadedTextures = mutableMapOf<String, Texture>() // Cache for loaded textures
    private lateinit var lockStatusLabel: Label

    // Data class to hold car selection item components
    private data class CarSelectionItem(
        val container: Table,
        val iconImage: Image,
        val nameLabel: Label,
        val background: Drawable,
        val selectedBackground: Drawable,
        val carType: CarType
    )

    fun initialize() {
        setupCarSelectionUI()
        carSelectionTable.setVisible(false)
    }

    private fun setupCarSelectionUI() {
        // Create car selection table at the top center
        carSelectionTable = Table()
        carSelectionTable.setFillParent(true)
        carSelectionTable.top()
        carSelectionTable.pad(40f)

        // Create main container with modern styling
        val mainContainer = Table()

        // Create a more modern background with rounded corners and shadow effect
        val backgroundStyle = createModernBackground()
        mainContainer.background = backgroundStyle
        mainContainer.pad(20f, 30f, 20f, 30f)

        // Title with modern styling
        val titleLabel = Label("Car Selection", skin)
        titleLabel.setFontScale(1.4f)
        titleLabel.color = Color(0.9f, 0.9f, 0.9f, 1f) // Light gray
        mainContainer.add(titleLabel).padBottom(15f).row()

        // Create horizontal container for car items
        val carContainer = Table()
        carContainer.pad(10f)

        // Create car items for each car type
        carItems = mutableListOf()
        val carTypes = CarType.values()

        for (i in carTypes.indices) {
            val carType = carTypes[i]
            val item = createCarItem(carType, i == carSystem.currentSelectedCarIndex)
            carItems.add(item)

            // Add spacing between items
            if (i > 0) {
                carContainer.add().width(15f) // Spacer
            }
            carContainer.add(item.container).size(90f, 110f)
        }

        mainContainer.add(carContainer).padBottom(10f).row()

        // Lock Status Display
        lockStatusLabel = Label("", skin).apply { setFontScale(1.1f) }
        mainContainer.add(lockStatusLabel).padBottom(15f).row()

        // Instructions with modern styling
        val instructionLabel = Label("Hold [R] + Mouse Wheel to change cars", skin)
        instructionLabel.setFontScale(0.9f)
        instructionLabel.color = Color(0.7f, 0.7f, 0.7f, 1f) // Darker gray
        mainContainer.add(instructionLabel).padBottom(5f).row()

        // Lock instruction
        val lockInstructionLabel = Label("Hold [M] + [L] to toggle Lock", skin)
        lockInstructionLabel.setFontScale(0.9f)
        lockInstructionLabel.color = Color(0.7f, 0.7f, 0.7f, 1f)
        mainContainer.add(lockInstructionLabel).padBottom(5f).row()

        // Additional instructions for fine positioning
        val fineInstructionLabel = Label("F - Fine positioning | Cars are 2D billboards", skin)
        fineInstructionLabel.setFontScale(0.8f)
        fineInstructionLabel.color = Color(0.6f, 0.6f, 0.6f, 1f)
        mainContainer.add(fineInstructionLabel)

        carSelectionTable.add(mainContainer)
        stage.addActor(carSelectionTable)
        update()
    }

    private fun createCarItem(carType: CarType, isSelected: Boolean): CarSelectionItem {
        val container = Table()
        container.pad(8f)

        // Create backgrounds for normal and selected states
        val normalBg = createItemBackground(Color(0.3f, 0.3f, 0.35f, 0.9f))
        val selectedBg = createItemBackground(Color(0.6f, 0.4f, 0.8f, 0.95f)) // Purple tint for cars

        container.background = if (isSelected) selectedBg else normalBg

        // Car icon - try to load actual texture first, fallback to generated icon
        val iconTexture = loadCarTexture(carType)
        val iconImage = Image(iconTexture)
        container.add(iconImage).size(50f, 50f).padBottom(8f).row()

        // Car name
        val nameLabel = Label(carType.displayName, skin)
        nameLabel.setFontScale(0.7f)
        nameLabel.setWrap(true)
        nameLabel.setAlignment(Align.center)
        nameLabel.color = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
        container.add(nameLabel).width(80f).center().padBottom(5f).row()

        // Car type indicator
        val typeLabel = Label("(2D Billboard)", skin)
        typeLabel.setFontScale(0.6f)
        typeLabel.color = Color(0.8f, 0.8f, 0.6f, 1f)
        container.add(typeLabel)

        return CarSelectionItem(container, iconImage, nameLabel, normalBg, selectedBg, carType)
    }

    private fun loadCarTexture(carType: CarType): TextureRegion {
        val texturePath = carType.texturePath

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
                println("Loaded car texture: $texturePath")
                TextureRegion(texture)
            } else {
                println("Texture not found: $texturePath, using fallback")
                createCarIcon(carType)
            }
        } catch (e: Exception) {
            println("Failed to load texture: $texturePath, error: ${e.message}, using fallback")
            createCarIcon(carType)
        }
    }

    private fun createModernBackground(): Drawable {
        // Create a modern dark background with subtle transparency
        val pixmap = Pixmap(100, 60, Pixmap.Format.RGBA8888)

        // Gradient effect with purple tint for cars
        for (y in 0 until 60) {
            val alpha = 0.85f + (y / 60f) * 0.1f // Subtle gradient
            pixmap.setColor(0.15f, 0.1f, 0.2f, alpha) // Purple tint
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

    private fun createCarIcon(carType: CarType): TextureRegion {
        // Create colored shapes representing different car types (fallback)
        val pixmap = Pixmap(50, 50, Pixmap.Format.RGBA8888)

        when (carType) {
            CarType.DEFAULT -> {
                // Standard sedan shape
                pixmap.setColor(Color(0.3f, 0.5f, 0.8f, 1f)) // Blue
                pixmap.fillRectangle(10, 20, 30, 15) // Main body
                pixmap.fillRectangle(15, 15, 20, 10) // Top/roof
                pixmap.setColor(Color.BLACK)
                pixmap.fillCircle(18, 38, 3) // Front wheel
                pixmap.fillCircle(32, 38, 3) // Rear wheel
            }
            CarType.SUV -> {
                // Taller SUV shape
                pixmap.setColor(Color(0.6f, 0.4f, 0.2f, 1f)) // Brown
                pixmap.fillRectangle(8, 15, 34, 20) // Main body
                pixmap.fillRectangle(12, 10, 26, 10) // Top/roof
                pixmap.setColor(Color.BLACK)
                pixmap.fillCircle(16, 38, 4) // Front wheel
                pixmap.fillCircle(34, 38, 4) // Rear wheel
            }
            CarType.TRUCK -> {
                // Truck shape with cargo area
                pixmap.setColor(Color(0.8f, 0.2f, 0.2f, 1f)) // Red
                pixmap.fillRectangle(5, 20, 15, 15) // Cab
                pixmap.fillRectangle(20, 18, 25, 17) // Cargo area
                pixmap.setColor(Color.BLACK)
                pixmap.fillCircle(13, 38, 4) // Front wheel
                pixmap.fillCircle(37, 38, 4) // Rear wheel
            }
            CarType.VAN -> {
                // Tall van shape
                pixmap.setColor(Color(0.5f, 0.5f, 0.5f, 1f)) // Gray
                pixmap.fillRectangle(10, 12, 30, 23) // Main body
                pixmap.setColor(Color.BLACK)
                pixmap.fillCircle(18, 38, 3) // Front wheel
                pixmap.fillCircle(32, 38, 3) // Rear wheel
            }
            CarType.POLICE_CAR -> {
                // Police car with light bar
                pixmap.setColor(Color(0.1f, 0.1f, 0.8f, 1f)) // Blue
                pixmap.fillRectangle(10, 20, 30, 15) // Main body
                pixmap.fillRectangle(15, 15, 20, 10) // Top/roof
                pixmap.setColor(Color(0.9f, 0.1f, 0.1f, 1f)) // Red light bar
                pixmap.fillRectangle(18, 12, 14, 3)
                pixmap.setColor(Color.BLACK)
                pixmap.fillCircle(18, 38, 3) // Front wheel
                pixmap.fillCircle(32, 38, 3) // Rear wheel
            }
            CarType.TAXI -> {
                // Yellow taxi
                pixmap.setColor(Color(0.9f, 0.9f, 0.2f, 1f)) // Yellow
                pixmap.fillRectangle(10, 20, 30, 15) // Main body
                pixmap.fillRectangle(15, 15, 20, 10) // Top/roof
                pixmap.setColor(Color.BLACK)
                pixmap.fillRectangle(20, 12, 10, 3) // Taxi sign
                pixmap.fillCircle(18, 38, 3) // Front wheel
                pixmap.fillCircle(32, 38, 3) // Rear wheel
            }
            CarType.BOSS_CAR -> {
                pixmap.setColor(Color(0.8f, 0.2f, 0.2f, 1f)) // Red
                pixmap.fillRectangle(5, 20, 15, 15) // Cab
                pixmap.fillRectangle(20, 18, 25, 17) // Cargo area
                pixmap.setColor(Color.BLACK)
                pixmap.fillCircle(13, 38, 4) // Front wheel
                pixmap.fillCircle(37, 38, 4) // Rear wheel
            }
        }

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegion(texture)
    }

    fun update() {
        if (carSystem.isNextCarLocked) {
            lockStatusLabel.setText("State: [LOCKED]")
            lockStatusLabel.color = Color.FIREBRICK
        } else {
            lockStatusLabel.setText("State: [OPEN]")
            lockStatusLabel.color = Color.FOREST
        }

        val currentIndex = carSystem.currentSelectedCarIndex

        // Animate all car items
        for (i in carItems.indices) {
            val item = carItems[i]
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
        carSelectionTable.setVisible(true)
    }

    fun hide() {
        carSelectionTable.setVisible(false)
    }

    fun dispose() {
        // Dispose cached textures
        for (texture in loadedTextures.values) {
            texture.dispose()
        }
        loadedTextures.clear()
    }
}
