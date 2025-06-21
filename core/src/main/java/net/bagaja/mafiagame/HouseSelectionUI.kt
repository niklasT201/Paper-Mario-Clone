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

class HouseSelectionUI(
    private val houseSystem: HouseSystem,
    private val roomTemplateManager: RoomTemplateManager,
    private val skin: Skin,
    private val stage: Stage
) {
    private lateinit var houseSelectionTable: Table
    private lateinit var houseItems: MutableList<HouseSelectionItem>
    private val loadedTextures = mutableMapOf<String, Texture>()
    private val generatedTextures = mutableListOf<Texture>()
    private lateinit var lockStatusLabel: Label

    // UI elements for room selection - redesigned
    private lateinit var roomSelectionContainer: Table
    private lateinit var roomListContainer: ScrollPane
    private lateinit var roomCountLabel: Label
    private lateinit var selectedRoomLabel: Label
    private var currentRoomTemplates = listOf<RoomTemplate>()
    private var selectedRoomIndex = 0

    private data class HouseSelectionItem(
        val container: Table, val iconImage: Image, val nameLabel: Label,
        val background: Drawable, val selectedBackground: Drawable, val houseType: HouseType
    )

    fun initialize() {
        setupHouseSelectionUI()
        houseSelectionTable.isVisible = false
    }

    private fun setupHouseSelectionUI() {
        // Create house selection table at the top center
        houseSelectionTable = Table()
        houseSelectionTable.setFillParent(true)
        houseSelectionTable.top().pad(40f)

        // Create main container with modern styling
        val mainContainer = Table()
        mainContainer.background = createModernBackground()
        mainContainer.pad(20f, 30f, 20f, 30f)

        val titleLabel = Label("House Selection", skin).apply {
            setFontScale(1.4f)
            color = Color(0.9f, 0.9f, 0.9f, 1f)
        }
        mainContainer.add(titleLabel).padBottom(15f).row()

        val houseContainer = Table().apply { pad(10f) }
        houseItems = mutableListOf()
        val houseTypes = HouseType.entries.toTypedArray()
        houseTypes.forEachIndexed { i, houseType ->
            val item = createHouseItem(houseType, i == houseSystem.currentSelectedHouseIndex)
            houseItems.add(item)

            // Add spacing between items
            if (i > 0) houseContainer.add().width(15f)
            houseContainer.add(item.container).size(90f, 110f)
        }

        mainContainer.add(houseContainer).padBottom(10f).row()

        // Room Selection Section
        roomSelectionContainer = Table()
        setupRoomSelectionUI()
        mainContainer.add(roomSelectionContainer).padBottom(15f).row()

        // Lock Status Display
        lockStatusLabel = Label("", skin).apply { setFontScale(1.1f) }
        mainContainer.add(lockStatusLabel).padBottom(15f).row()

        val instructionLabel = Label("Hold [H] + Mouse Wheel to change houses", skin).apply {
            setFontScale(0.9f)
            color = Color(0.7f, 0.7f, 0.7f, 1f)
        }
        mainContainer.add(instructionLabel).padBottom(5f).row()

        val lockInstructionLabel = Label("Hold [H] + [L] to toggle Lock", skin).apply {
            setFontScale(0.9f)
            color = Color(0.7f, 0.7f, 0.7f, 1f)
        }
        mainContainer.add(lockInstructionLabel).padBottom(5f).row()

        // Room navigation instructions
        val roomInstructionLabel = Label("Use ↑↓ arrows to navigate rooms, Enter to select", skin).apply {
            setFontScale(0.8f)
            color = Color(0.6f, 0.6f, 0.8f, 1f)
        }
        mainContainer.add(roomInstructionLabel).padBottom(5f).row()

        // Additional instructions
        val additionalLabel = Label("Houses have collision - players cannot walk through them", skin)
        additionalLabel.setFontScale(0.8f)
        additionalLabel.color = Color(0.6f, 0.6f, 0.6f, 1f)
        mainContainer.add(additionalLabel)

        houseSelectionTable.add(mainContainer)
        stage.addActor(houseSelectionTable)

        update()
    }

    private fun setupRoomSelectionUI() {
        roomSelectionContainer.clear()

        // Room selection header
        val roomHeaderTable = Table()
        val roomLabel = Label("Interior Room:", skin).apply {
            setFontScale(1.0f)
            color = Color(0.9f, 0.9f, 0.9f, 1f)
        }
        roomHeaderTable.add(roomLabel).padRight(10f)

        // Room count label
        roomCountLabel = Label("(0 rooms)", skin).apply {
            setFontScale(0.9f)
            color = Color(0.7f, 0.7f, 0.7f, 1f)
        }
        roomHeaderTable.add(roomCountLabel)

        roomSelectionContainer.add(roomHeaderTable).padBottom(8f).row()

        // Selected room display
        selectedRoomLabel = Label("No room selected", skin).apply {
            setFontScale(1.0f)
            color = Color(0.8f, 0.9f, 1.0f, 1f)
            setWrap(true)
            setAlignment(Align.center)
        }

        // FIX: Error 2 - Set background via style
        val selectedRoomBg = createRoomItemBackground(Color(0.2f, 0.3f, 0.4f, 0.8f), true)
        val newStyle = Label.LabelStyle(selectedRoomLabel.style)
        newStyle.background = selectedRoomBg
        selectedRoomLabel.style = newStyle

        roomSelectionContainer.add(selectedRoomLabel).width(300f).height(40f).pad(5f).padBottom(10f).row()

        // Navigation buttons
        val navButtonTable = Table()

        val prevButton = TextButton("↑ Previous", skin).apply {
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    navigateRooms(-1)
                }
            })
        }

        val nextButton = TextButton("↓ Next", skin).apply {
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    navigateRooms(1)
                }
            })
        }

        val selectButton = TextButton("✓ Select", skin).apply {
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    selectCurrentRoom()
                }
            })
        }

        navButtonTable.add(prevButton).width(80f).height(30f).padRight(5f)
        navButtonTable.add(nextButton).width(80f).height(30f).padRight(5f)
        navButtonTable.add(selectButton).width(80f).height(30f)

        roomSelectionContainer.add(navButtonTable).padBottom(10f).row()

        // Room list preview (scrollable)
        createRoomListPreview()
    }

    private fun createRoomListPreview() {
        val roomListTable = Table()
        roomListTable.pad(5f)

        roomListContainer = ScrollPane(roomListTable, skin)
        roomListContainer.setScrollingDisabled(true, false)
        roomListContainer.fadeScrollBars = false
        roomSelectionContainer.add(roomListContainer).width(300f).height(120f)
    }

    private fun createRoomItemBackground(color: Color, isSelected: Boolean): Drawable {
        val pixmap = Pixmap(280, 25, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()

        if (isSelected) {
            // Add border for selected item
            pixmap.setColor(color.r + 0.2f, color.g + 0.2f, color.b + 0.2f, color.a)
            pixmap.drawRectangle(0, 0, 280, 25)
            pixmap.drawRectangle(1, 1, 278, 23)
        }

        val texture = Texture(pixmap)
        generatedTextures.add(texture) // Track texture for disposal
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun navigateRooms(direction: Int) {
        if (currentRoomTemplates.isEmpty()) return

        selectedRoomIndex = (selectedRoomIndex + direction).coerceIn(0, currentRoomTemplates.size - 1)
        updateRoomSelection()
    }

    private fun selectCurrentRoom() {
        if (currentRoomTemplates.isNotEmpty() && selectedRoomIndex < currentRoomTemplates.size) {
            val selectedTemplate = currentRoomTemplates[selectedRoomIndex]
            houseSystem.selectedRoomTemplateId = selectedTemplate.id
            println("UI Selected Room Template ID: ${houseSystem.selectedRoomTemplateId}")
            updateSelectedRoomDisplay()
        }
    }

    private fun updateSelectedRoomDisplay() {
        val currentTemplate = currentRoomTemplates.find { it.id == houseSystem.selectedRoomTemplateId }
        selectedRoomLabel.setText(currentTemplate?.name ?: "No room selected")
        selectedRoomLabel.color = if (currentTemplate != null) Color(0.8f, 1.0f, 0.8f, 1f) else Color(0.8f, 0.8f, 0.8f, 1f)
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
        generatedTextures.add(texture) // Track texture for disposal
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
        generatedTextures.add(texture) // Track texture for disposal
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
                // Flat
                pixmap.setColor(Color(0.6f, 0.4f, 0.2f, 1f))
                pixmap.fillRectangle(15, 25, 20, 20) // House body
                pixmap.setColor(Color(0.8f, 0.2f, 0.2f, 1f))
                pixmap.fillTriangle(10, 25, 25, 10, 40, 25) // Roof
            }
            HouseType.HOUSE_3 -> {
                pixmap.setColor(Color(0.6f, 0.4f, 0.2f, 1f))
                pixmap.fillRectangle(15, 25, 20, 20) // House body
                pixmap.setColor(Color(0.8f, 0.2f, 0.2f, 1f))
                pixmap.fillTriangle(10, 25, 25, 10, 40, 25) // Roof
            }
            HouseType.HOUSE_4 -> {
                pixmap.setColor(Color(0.6f, 0.4f, 0.2f, 1f))
                pixmap.fillRectangle(15, 25, 20, 20) // House body
                pixmap.setColor(Color(0.8f, 0.2f, 0.2f, 1f))
                pixmap.fillTriangle(10, 25, 25, 10, 40, 25) // Roof
            }
            HouseType.STAIR -> {
                pixmap.setColor(Color(0.6f, 0.4f, 0.2f, 1f))
                pixmap.fillRectangle(15, 25, 20, 20) // House body
                pixmap.setColor(Color(0.8f, 0.2f, 0.2f, 1f))
                pixmap.fillTriangle(10, 25, 25, 10, 40, 25) // Roof
            }
        }

        val texture = Texture(pixmap)
        generatedTextures.add(texture) // Track texture for disposal
        pixmap.dispose()

        return TextureRegion(texture)
    }

    fun update() {
        // Update Lock Status and Room Selection Visibility
        if (houseSystem.isNextHouseLocked) {
            lockStatusLabel.setText("State: [LOCKED]")
            lockStatusLabel.color = Color.FIREBRICK
            roomSelectionContainer.isVisible = false
            houseSystem.selectedRoomTemplateId = null // Ensure no room is selected for a locked house
        } else {
            lockStatusLabel.setText("State: [OPEN]")
            lockStatusLabel.color = Color.FOREST
            roomSelectionContainer.isVisible = true
            updateRoomSelection() // Populate and manage the room selection
        }

        // Animate all house items
        houseItems.forEachIndexed { i, item ->
            val isSelected = i == houseSystem.currentSelectedHouseIndex

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

    private fun updateRoomSelection() {
        currentRoomTemplates = roomTemplateManager.getAllTemplates()

        // Update room count
        roomCountLabel.setText("(${currentRoomTemplates.size} rooms)")
        roomCountLabel.color = if (currentRoomTemplates.isNotEmpty()) Color(0.7f, 0.9f, 0.7f, 1f) else Color(0.9f, 0.7f, 0.7f, 1f)

        if (currentRoomTemplates.isNotEmpty()) {
            // Ensure selected index is valid
            selectedRoomIndex = selectedRoomIndex.coerceIn(0, currentRoomTemplates.size - 1)

            // Try to find current selected template
            val currentTemplate = currentRoomTemplates.find { it.id == houseSystem.selectedRoomTemplateId }
            if (currentTemplate != null) {
                selectedRoomIndex = currentRoomTemplates.indexOf(currentTemplate)
            } else {
                // Select first template if none selected
                houseSystem.selectedRoomTemplateId = currentRoomTemplates.first().id
                selectedRoomIndex = 0
            }
        } else {
            houseSystem.selectedRoomTemplateId = null
            selectedRoomIndex = 0
        }

        updateSelectedRoomDisplay()

        val roomListTable = roomListContainer.actor as? Table
        if (roomListTable == null) return // Safety check

        roomListTable.clear() // Clear all old room labels from the table
        currentRoomTemplates.forEachIndexed { index, template ->
            val isSelected = index == selectedRoomIndex
            val roomItem = Label(template.name, skin).apply {
                setFontScale(0.8f)
                color = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
                setWrap(true)
                setAlignment(Align.left)

                val newStyle = Label.LabelStyle(style)
                newStyle.background = createRoomItemBackground(
                    if (isSelected) Color(0.4f, 0.6f, 0.8f, 0.7f) else Color(0.3f, 0.3f, 0.3f, 0.5f),
                    isSelected
                )
                style = newStyle
            }

            roomItem.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    selectedRoomIndex = index
                    selectCurrentRoom()
                    updateRoomSelection()
                }
            })

            roomListTable.add(roomItem).width(280f).height(25f).pad(2f).row()
        }
    }

    fun show() {
        houseSelectionTable.isVisible = true
        refreshRoomList()
    }

    fun hide() {
        houseSelectionTable.setVisible(false)
    }

    fun refreshRoomList() {
        println("HouseSelectionUI: Refreshing room list.")
        updateRoomSelection()
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
