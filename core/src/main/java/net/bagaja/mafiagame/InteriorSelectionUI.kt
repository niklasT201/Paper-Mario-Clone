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

class InteriorSelectionUI(
    private val interiorSystem: InteriorSystem,
    private val skin: Skin,
    private val stage: Stage
) {
    private lateinit var interiorSelectionTable: Table
    private lateinit var interiorItems: MutableList<InteriorSelectionItem>
    private val loadedTextures = mutableMapOf<String, Texture>()
    private val generatedTextures = mutableListOf<Texture>()

    // UI elements for category filtering
    private lateinit var categoryContainer: Table
    private lateinit var categoryButtons: MutableList<TextButton>
    private var currentCategory: InteriorCategory? = null

    // UI elements for placement info
    private lateinit var placementInfoLabel: Label
    private lateinit var rotationLabel: Label
    private lateinit var finePosLabel: Label

    private data class InteriorSelectionItem(
        val container: Table,
        val iconImage: Image,
        val nameLabel: Label,
        val typeLabel: Label,
        val background: Drawable,
        val selectedBackground: Drawable,
        val interiorType: InteriorType
    )

    fun initialize() {
        setupInteriorSelectionUI()
        interiorSelectionTable.isVisible = false
    }

    private fun setupInteriorSelectionUI() {
        // Create interior selection table at the top center
        interiorSelectionTable = Table()
        interiorSelectionTable.setFillParent(true)
        interiorSelectionTable.top().pad(40f)

        // Create main container with modern styling
        val mainContainer = Table()
        mainContainer.background = createModernBackground()
        mainContainer.pad(20f, 30f, 20f, 30f)

        val titleLabel = Label("Interior Designer", skin).apply {
            setFontScale(1.4f)
            color = Color(0.9f, 0.9f, 0.9f, 1f)
        }
        mainContainer.add(titleLabel).padBottom(15f).row()

        // Category Filter Section
        setupCategoryFilter(mainContainer)

        // Interior Items Container (Scrollable)
        val interiorScrollContainer = setupInteriorItemsContainer()
        mainContainer.add(interiorScrollContainer).width(600f).height(200f).padBottom(15f).row()

        // Placement Information Section
        setupPlacementInfo(mainContainer)

        // Instructions
        setupInstructions(mainContainer)

        interiorSelectionTable.add(mainContainer)
        stage.addActor(interiorSelectionTable)

        update()
    }

    private fun setupCategoryFilter(mainContainer: Table) {
        categoryContainer = Table()
        categoryButtons = mutableListOf()

        val categoryLabel = Label("Category Filter:", skin).apply {
            setFontScale(1.0f)
            color = Color(0.9f, 0.9f, 0.9f, 1f)
        }
        categoryContainer.add(categoryLabel).padRight(10f)

        // Add "All" button
        val allButton = TextButton("All", skin).apply {
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    setCategory(null)
                }
            })
        }
        categoryButtons.add(allButton)
        categoryContainer.add(allButton).width(60f).height(30f).padRight(5f)

        // Add category buttons
        InteriorCategory.entries.forEach { category ->
            val button = TextButton(category.displayName, skin).apply {
                addListener(object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        setCategory(category)
                    }
                })
            }
            categoryButtons.add(button)
            categoryContainer.add(button).width(80f).height(30f).padRight(5f)
        }

        mainContainer.add(categoryContainer).padBottom(15f).row()
    }

    private fun setupInteriorItemsContainer(): ScrollPane {
        val interiorContainer = Table()
        interiorContainer.pad(10f)

        interiorItems = mutableListOf()
        rebuildInteriorItems(interiorContainer)

        val scrollPane = ScrollPane(interiorContainer, skin)
        scrollPane.setScrollingDisabled(false, true) // Horizontal scrolling only
        scrollPane.fadeScrollBars = false
        return scrollPane
    }

    private fun rebuildInteriorItems(container: Table) {
        container.clear()
        interiorItems.clear()

        val filteredTypes = if (currentCategory != null) {
            InteriorType.entries.filter { it.category == currentCategory }
        } else {
            InteriorType.entries.toList()
        }

        filteredTypes.forEachIndexed { i, interiorType ->
            val item = createInteriorItem(interiorType, interiorType == interiorSystem.currentSelectedInterior)
            interiorItems.add(item)

            // Add spacing between items
            if (i > 0) container.add().width(15f)
            container.add(item.container).size(120f, 140f)
        }
    }

    private fun setupPlacementInfo(mainContainer: Table) {
        val infoContainer = Table()

        // Current selection info
        placementInfoLabel = Label("Selected: ${interiorSystem.currentSelectedInterior.displayName}", skin).apply {
            setFontScale(1.0f)
            color = Color(0.8f, 0.9f, 1.0f, 1f)
        }
        infoContainer.add(placementInfoLabel).padBottom(5f).row()

        // Rotation info
        rotationLabel = Label("Rotation: ${interiorSystem.currentRotation}°", skin).apply {
            setFontScale(0.9f)
            color = Color(0.7f, 0.8f, 0.9f, 1f)
        }
        infoContainer.add(rotationLabel).padBottom(5f).row()

        // Fine positioning info
        finePosLabel = Label("Fine Positioning: ${if (interiorSystem.finePosMode) "ON" else "OFF"}", skin).apply {
            setFontScale(0.9f)
            color = if (interiorSystem.finePosMode) Color.GREEN else Color.GRAY
        }
        infoContainer.add(finePosLabel).padBottom(10f).row()

        mainContainer.add(infoContainer).padBottom(15f).row()
    }

    private fun setupInstructions(mainContainer: Table) {
        val instructionsTable = Table()

        val instructions = listOf(
            "Hold [I] + Mouse Wheel to change interiors",
            "Hold [I] + [R] to rotate interior",
            "Hold [I] + [F] to toggle fine positioning",
            "Left Click to place interior",
            "Right Click on interior to remove it",
            "2D interiors are billboards that face the camera",
            "3D interiors have full collision like houses"
        )

        instructions.forEach { instruction ->
            val label = Label(instruction, skin).apply {
                setFontScale(0.8f)
                color = Color(0.7f, 0.7f, 0.7f, 1f)
            }
            instructionsTable.add(label).padBottom(3f).row()
        }

        mainContainer.add(instructionsTable)
    }

    private fun setCategory(category: InteriorCategory?) {
        currentCategory = category

        // Update button colors
        categoryButtons.forEachIndexed { index, button ->
            if (index == 0) { // "All" button
                button.color = if (category == null) Color.CYAN else Color.WHITE
            } else {
                val buttonCategory = InteriorCategory.entries[index - 1]
                button.color = if (category == buttonCategory) Color.CYAN else Color.WHITE
            }
        }

        // Rebuild items with filter
        val scrollPane = interiorSelectionTable.findActor<ScrollPane>("interiorScrollPane")
        val container = scrollPane?.actor as? Table
        if (container != null) {
            rebuildInteriorItems(container)
        }
    }

    private fun createInteriorItem(interiorType: InteriorType, isSelected: Boolean): InteriorSelectionItem {
        val container = Table()
        container.pad(8f)

        // Create backgrounds for normal and selected states
        val normalBg = createItemBackground(Color(0.3f, 0.3f, 0.35f, 0.9f))
        val selectedBg = createItemBackground(
            when (interiorType.category) {
                InteriorCategory.FURNITURE -> Color(0.4f, 0.6f, 0.4f, 0.95f) // Green
                InteriorCategory.DECORATION -> Color(0.6f, 0.4f, 0.6f, 0.95f) // Purple
                InteriorCategory.LIGHTING -> Color(0.8f, 0.8f, 0.4f, 0.95f) // Yellow
                InteriorCategory.APPLIANCE -> Color(0.4f, 0.4f, 0.8f, 0.95f) // Blue
                InteriorCategory.MISC -> Color(0.6f, 0.6f, 0.6f, 0.95f) // Gray
            }
        )

        container.background = if (isSelected) selectedBg else normalBg

        // Interior icon - try to load actual texture first, fallback to generated icon
        val iconTexture = loadInteriorTexture(interiorType)
        val iconImage = Image(iconTexture)
        container.add(iconImage).size(60f, 60f).padBottom(8f).row()

        // Interior name
        val nameLabel = Label(interiorType.displayName, skin)
        nameLabel.setFontScale(0.7f)
        nameLabel.setWrap(true)
        nameLabel.setAlignment(Align.center)
        nameLabel.color = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
        container.add(nameLabel).width(100f).center().padBottom(5f).row()

        // Type info (2D/3D)
        val typeText = if (interiorType.is3D) "3D Model" else "2D Billboard"
        val typeLabel = Label(typeText, skin)
        typeLabel.setFontScale(0.6f)
        typeLabel.color = if (interiorType.is3D) Color(0.7f, 0.9f, 0.7f, 1f) else Color(0.9f, 0.7f, 0.7f, 1f)
        container.add(typeLabel).padBottom(3f).row()

        // Size info
        val sizeText = if (interiorType.is3D) {
            "${interiorType.width.toInt()}×${interiorType.height.toInt()}×${interiorType.depth.toInt()}"
        } else {
            "${interiorType.width.toInt()}×${interiorType.height.toInt()}"
        }
        val sizeLabel = Label(sizeText, skin)
        sizeLabel.setFontScale(0.6f)
        sizeLabel.color = Color(0.7f, 0.7f, 0.7f, 1f)
        container.add(sizeLabel)

        // Add click listener for selection
        container.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                selectInterior(interiorType)
            }
        })

        return InteriorSelectionItem(container, iconImage, nameLabel, typeLabel, normalBg, selectedBg, interiorType)
    }

    private fun selectInterior(interiorType: InteriorType) {
        // Find the index of the selected interior type
        val index = InteriorType.entries.indexOf(interiorType)
        if (index >= 0) {
            interiorSystem.currentSelectedInteriorIndex = index
            interiorSystem.currentSelectedInterior = interiorType
            println("UI Selected Interior: ${interiorType.displayName}")
            update()
        }
    }

    private fun loadInteriorTexture(interiorType: InteriorType): TextureRegion {
        val texturePath = interiorType.texturePath

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
                println("Loaded interior texture: $texturePath")
                TextureRegion(texture)
            } else {
                println("Texture not found: $texturePath, using fallback")
                createInteriorIcon(interiorType)
            }
        } catch (e: Exception) {
            println("Failed to load texture: $texturePath, error: ${e.message}, using fallback")
            createInteriorIcon(interiorType)
        }
    }

    private fun createInteriorIcon(interiorType: InteriorType): TextureRegion {
        // Create colored shapes representing different interior types (fallback)
        val pixmap = Pixmap(60, 60, Pixmap.Format.RGBA8888)

        when (interiorType.category) {
            InteriorCategory.FURNITURE -> {
                pixmap.setColor(Color(0.6f, 0.4f, 0.2f, 1f)) // Brown for furniture
                when (interiorType) {
                    InteriorType.BAR -> {
                        // Bar counter
                        pixmap.fillRectangle(10, 35, 40, 15) // Bar top
                        pixmap.fillRectangle(15, 45, 5, 10) // Support legs
                        pixmap.fillRectangle(35, 45, 5, 10)
                        pixmap.setColor(Color.DARK_GRAY)
                        pixmap.fillRectangle(12, 25, 36, 10) // Bar front
                    }
                    InteriorType.BARREL -> {
                        // Barrel shape
                        pixmap.setColor(Color(0.5f, 0.3f, 0.1f, 1f))
                        pixmap.fillCircle(30, 35, 12) // Barrel body
                        pixmap.setColor(Color.DARK_GRAY)
                        pixmap.drawLine(18, 25, 42, 25) // Barrel bands
                        pixmap.drawLine(18, 35, 42, 35)
                        pixmap.drawLine(18, 45, 42, 45)
                    }
                    InteriorType.CHAIR -> {
                        // Simple chair
                        pixmap.fillRectangle(20, 30, 20, 15) // Seat
                        pixmap.fillRectangle(20, 15, 20, 15) // Back
                        pixmap.fillRectangle(22, 45, 4, 10) // Front legs
                        pixmap.fillRectangle(34, 45, 4, 10)
                        pixmap.fillRectangle(22, 30, 4, 15) // Back legs
                        pixmap.fillRectangle(34, 30, 4, 15)
                    }
                    InteriorType.OFFICE_CHAIR -> {
                        // Office chair with wheels
                        pixmap.fillRectangle(18, 30, 24, 15) // Seat
                        pixmap.fillRectangle(18, 15, 24, 15) // Back
                        pixmap.fillRectangle(28, 40, 4, 12) // Central post
                        pixmap.setColor(Color.DARK_GRAY)
                        pixmap.fillCircle(20, 50, 3) // Wheels
                        pixmap.fillCircle(30, 50, 3)
                        pixmap.fillCircle(40, 50, 3)
                    }
                    InteriorType.TABLE -> {
                        // Simple table
                        pixmap.fillRectangle(10, 35, 40, 10) // Table top
                        pixmap.fillRectangle(15, 45, 5, 10) // Legs
                        pixmap.fillRectangle(40, 45, 5, 10)
                        pixmap.fillRectangle(15, 25, 5, 10)
                        pixmap.fillRectangle(40, 25, 5, 10)
                    }
                    InteriorType.TABLE_DISH -> {
                        // Table with dishes
                        pixmap.fillRectangle(10, 35, 40, 10) // Table top
                        pixmap.fillRectangle(15, 45, 5, 10) // Legs
                        pixmap.fillRectangle(40, 45, 5, 10)
                        pixmap.setColor(Color.WHITE)
                        pixmap.fillCircle(25, 40, 4) // Plate
                        pixmap.fillCircle(35, 40, 3) // Cup
                    }
                    InteriorType.BOOKSHELF_3D -> {
                        // 3D Bookshelf
                        pixmap.fillRectangle(15, 10, 30, 45) // Shelf body
                        pixmap.setColor(Color.DARK_GRAY)
                        pixmap.fillRectangle(18, 20, 24, 2) // Shelf lines
                        pixmap.fillRectangle(18, 30, 24, 2)
                        pixmap.fillRectangle(18, 40, 24, 2)
                        pixmap.setColor(Color(0.8f, 0.2f, 0.2f, 1f)) // Books
                        pixmap.fillRectangle(20, 22, 3, 6)
                        pixmap.fillRectangle(25, 22, 4, 6)
                        pixmap.fillRectangle(32, 22, 3, 6)
                    }
                    else -> {
                        // Generic furniture
                        pixmap.fillRectangle(20, 20, 20, 20)
                    }
                }
            }
            InteriorCategory.DECORATION -> {
                pixmap.setColor(Color(0.4f, 0.8f, 0.4f, 1f)) // Green for decoration
                when (interiorType) {
                    InteriorType.BOARD -> {
                        // Notice board or bulletin board
                        pixmap.setColor(Color(0.8f, 0.6f, 0.4f, 1f)) // Wood frame
                        pixmap.fillRectangle(15, 20, 30, 25)
                        pixmap.setColor(Color.WHITE) // Paper/board surface
                        pixmap.fillRectangle(18, 23, 24, 19)
                        pixmap.setColor(Color.BLACK) // Text lines
                        pixmap.drawLine(20, 35, 38, 35)
                        pixmap.drawLine(20, 30, 35, 30)
                        pixmap.drawLine(20, 25, 40, 25)
                    }
                    InteriorType.ITEM_FRAME -> {
                        // Picture frame
                        pixmap.setColor(Color(0.8f, 0.6f, 0.4f, 1f)) // Frame
                        pixmap.fillRectangle(15, 20, 30, 25)
                        pixmap.setColor(Color(0.2f, 0.4f, 0.8f, 1f)) // Picture
                        pixmap.fillRectangle(18, 23, 24, 19)
                        pixmap.setColor(Color.YELLOW) // Highlight
                        pixmap.fillRectangle(20, 25, 8, 8)
                    }
                    else -> {
                        // Generic decoration
                        pixmap.fillCircle(30, 30, 15)
                    }
                }
            }
            InteriorCategory.LIGHTING -> {
                pixmap.setColor(Color(0.9f, 0.9f, 0.4f, 1f)) // Yellow for lighting
                when (interiorType) {
                    InteriorType.BROKEN_LAMP -> {
                        // Broken lamp with cracks
                        pixmap.fillRectangle(28, 40, 4, 15) // Stand
                        pixmap.setColor(Color(0.7f, 0.7f, 0.3f, 1f)) // Dimmer shade
                        pixmap.fillCircle(30, 25, 10) // Lampshade
                        pixmap.setColor(Color.BLACK) // Crack lines
                        pixmap.drawLine(25, 20, 35, 30)
                        pixmap.drawLine(22, 25, 32, 35)
                    }
                    InteriorType.DESK_LAMP -> {
                        // Desk lamp with adjustable arm
                        pixmap.fillRectangle(25, 45, 10, 8) // Base
                        pixmap.fillRectangle(28, 35, 4, 10) // Arm
                        pixmap.fillRectangle(25, 25, 4, 10) // Upper arm
                        pixmap.fillCircle(25, 20, 6) // Lamp head
                    }
                    InteriorType.HANDLANTERN -> {
                        // Hand lantern
                        pixmap.setColor(Color(0.4f, 0.4f, 0.4f, 1f)) // Metal
                        pixmap.fillRectangle(25, 15, 10, 8) // Top handle
                        pixmap.fillRectangle(27, 25, 6, 20) // Body
                        pixmap.setColor(Color.YELLOW) // Light
                        pixmap.fillCircle(30, 30, 4)
                    }
                    else -> {
                        // Generic light
                        pixmap.fillCircle(30, 30, 12)
                    }
                }
            }
            InteriorCategory.MISC -> {
                pixmap.setColor(Color(0.6f, 0.6f, 0.6f, 1f)) // Gray for misc
                when (interiorType) {
                    InteriorType.MONEY_STACK -> {
                        // Stack of money/bills
                        pixmap.setColor(Color(0.2f, 0.7f, 0.2f, 1f)) // Green money
                        pixmap.fillRectangle(20, 40, 20, 4) // Bills
                        pixmap.fillRectangle(22, 36, 16, 4)
                        pixmap.fillRectangle(24, 32, 12, 4)
                        pixmap.setColor(Color.YELLOW) // Coins
                        pixmap.fillCircle(35, 45, 3)
                        pixmap.fillCircle(38, 42, 2)
                    }
                    InteriorType.TELEPHONE -> {
                        // Old telephone
                        pixmap.setColor(Color.BLACK) // Phone body
                        pixmap.fillRectangle(20, 35, 20, 15) // Base
                        pixmap.fillRectangle(25, 25, 10, 10) // Handset
                        pixmap.setColor(Color.DARK_GRAY) // Dial/buttons
                        pixmap.fillCircle(25, 40, 2)
                        pixmap.fillCircle(30, 40, 2)
                        pixmap.fillCircle(35, 40, 2)
                    }
                    else -> {
                        // Generic misc item
                        pixmap.fillRectangle(20, 20, 20, 20)
                    }
                }
            }
            InteriorCategory.APPLIANCE -> {
                pixmap.setColor(Color(0.7f, 0.7f, 0.7f, 1f)) // Gray for appliances
                // No appliances in your current enum, so just generic
                pixmap.fillRectangle(20, 20, 20, 20)
            }
        }

        val texture = Texture(pixmap)
        generatedTextures.add(texture) // Track texture for disposal
        pixmap.dispose()

        return TextureRegion(texture)
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
        generatedTextures.add(texture) // Track texture for disposal
        pixmap.dispose()

        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createItemBackground(color: Color): Drawable {
        val pixmap = Pixmap(120, 140, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()

        // Add subtle border
        pixmap.setColor(color.r + 0.1f, color.g + 0.1f, color.b + 0.1f, color.a)
        pixmap.drawRectangle(0, 0, 120, 140)

        val texture = Texture(pixmap)
        generatedTextures.add(texture) // Track texture for disposal
        pixmap.dispose()

        return TextureRegionDrawable(TextureRegion(texture))
    }

    fun update() {
        // Update info labels
        placementInfoLabel.setText("Selected: ${interiorSystem.currentSelectedInterior.displayName}")
        rotationLabel.setText("Rotation: ${interiorSystem.currentRotation}°")
        finePosLabel.setText("Fine Positioning: ${if (interiorSystem.finePosMode) "ON" else "OFF"}")
        finePosLabel.color = if (interiorSystem.finePosMode) Color.GREEN else Color.GRAY

        // Animate all interior items
        interiorItems.forEachIndexed { i, item ->
            val isSelected = item.interiorType == interiorSystem.currentSelectedInterior

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
                        item.typeLabel.color = targetColor
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
        interiorSelectionTable.isVisible = true
        update()
    }

    fun hide() {
        interiorSelectionTable.isVisible = false
    }

    fun dispose() {
        // Dispose cached textures from files
        for (texture in loadedTextures.values) {
            texture.dispose()
        }
        loadedTextures.clear()

        // Dispose dynamically generated textures
        for (texture in generatedTextures) {
            texture.dispose()
        }
        generatedTextures.clear()
    }
}
