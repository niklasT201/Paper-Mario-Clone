package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array as GdxArray

data class CarSpawnConfig(
    val carType: CarType,
    val isLocked: Boolean,
    val driverCharacterType: String, // "None", "Enemy", "NPC"
    val enemyDriverType: EnemyType?,
    val npcDriverType: NPCType?
)

class CarSelectionUI(
    private val skin: Skin,
    private val stage: Stage,
    private val carSystem: CarSystem,
    private val enemySystem: EnemySystem,
    private val npcSystem: NPCSystem
) {
    private lateinit var carSelectionTable: Table
    private lateinit var carItems: MutableList<CarSelectionItem>
    private val loadedTextures = mutableMapOf<String, Texture>() // Cache for loaded textures
    private lateinit var lockStatusLabel: Label

    private lateinit var isLockedCheckbox: CheckBox
    private lateinit var driverTypeSelectBox: SelectBox<String>
    private lateinit var enemyDriverSelectBox: SelectBox<String>
    private lateinit var npcDriverSelectBox: SelectBox<String>

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
        carSelectionTable.isVisible = false
    }

    private fun setupCarSelectionUI() {
        // Create car selection table at the top center
        carSelectionTable = Table()
        carSelectionTable.setFillParent(true)
        carSelectionTable.top()
        carSelectionTable.pad(40f)

        // Create main container with modern styling
        val mainContainer = Table()
        mainContainer.background = createModernBackground()
        mainContainer.pad(20f, 30f, 20f, 30f)

        val titleLabel = Label("Car Placer", skin, "title")
        mainContainer.add(titleLabel).padBottom(15f).row()

        // Create horizontal container for car items
        val carContainer = Table()
        carItems = mutableListOf()
        CarType.entries.forEachIndexed { i, carType ->
            val item = createCarItem(carType, i == carSystem.currentSelectedCarIndex)
            carItems.add(item)
            if (i > 0) carContainer.add().width(15f)
            carContainer.add(item.container).size(90f, 110f)
        }
        val carScrollPane = ScrollPane(carContainer, skin)
        carScrollPane.setScrollingDisabled(false, true)
        carScrollPane.fadeScrollBars = false
        mainContainer.add(carScrollPane).growX().maxHeight(120f).padBottom(20f).row()

        // --- Driver and Lock Configuration Section ---
        val configTable = Table()

        // Is Locked Checkbox
        isLockedCheckbox = CheckBox(" Start Locked", skin)
        isLockedCheckbox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (carSystem.isNextCarLocked != isLockedCheckbox.isChecked) {
                    carSystem.toggleLockState()
                }
            }
        })
        configTable.add(isLockedCheckbox).colspan(2).left().padBottom(10f).row()

        // Driver Type Dropdown
        driverTypeSelectBox = SelectBox(skin)
        driverTypeSelectBox.items = GdxArray(arrayOf("None", "Enemy", "NPC"))
        configTable.add(Label("Driver:", skin)).padRight(10f)
        configTable.add(driverTypeSelectBox).width(150f).left().row()

        // Enemy Driver Row
        val enemyRow = Table()
        enemyDriverSelectBox = SelectBox(skin)
        enemyDriverSelectBox.items = GdxArray(EnemyType.entries.map { it.displayName }.toTypedArray())
        enemyRow.add(Label("Enemy Type:", skin)).padRight(10f).left()
        enemyRow.add(enemyDriverSelectBox).width(200f).left()
        configTable.add(enemyRow).colspan(2).left().row()

        // NPC Driver Row
        val npcRow = Table()
        npcDriverSelectBox = SelectBox(skin)
        npcDriverSelectBox.items = GdxArray(NPCType.entries.map { it.displayName }.toTypedArray())
        npcRow.add(Label("NPC Type:", skin)).padRight(10f).left()
        npcRow.add(npcDriverSelectBox).width(200f).left()
        configTable.add(npcRow).colspan(2).left().row()

        driverTypeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val driverType = driverTypeSelectBox.selected
                enemyRow.isVisible = driverType == "Enemy"
                npcRow.isVisible = driverType == "NPC"
                carSelectionTable.pack() // Resize the main table
            }
        })

        // Set initial visibility
        enemyRow.isVisible = false
        npcRow.isVisible = false

        mainContainer.add(configTable).padBottom(15f).row()

        // Lock Status Display
        lockStatusLabel = Label("", skin).apply { setFontScale(1.1f) }
        mainContainer.add(lockStatusLabel).padBottom(15f).row()

        // Updated Instructions
        val instructionLabel = Label("Hold [M] + Mouse Wheel to change cars", skin)
        instructionLabel.color = Color(0.7f, 0.7f, 0.7f, 1f)
        mainContainer.add(instructionLabel).padBottom(5f).row()
        val fineInstructionLabel = Label("F - Fine positioning mode", skin)
        fineInstructionLabel.color = Color(0.6f, 0.6f, 0.6f, 1f)
        mainContainer.add(fineInstructionLabel)

        carSelectionTable.add(mainContainer)
        stage.addActor(carSelectionTable)
        update()
    }

    private fun addListeners() {
        // This listener shows/hides the specific driver dropdowns
        driverTypeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                updateDriverVisibility()
            }
        })
    }

    private fun updateDriverVisibility() {
        val driverType = driverTypeSelectBox.selected
        // Find the parent table (the row) of the SelectBox to toggle its visibility
        enemyDriverSelectBox.parent.isVisible = driverType == "Enemy"
        npcDriverSelectBox.parent.isVisible = driverType == "NPC"
        carSelectionTable.pack() // Resize the main table to fit the new content
    }

    fun getSpawnConfig(): CarSpawnConfig {
        val carType = carSystem.currentSelectedCar
        val driverType = driverTypeSelectBox.selected
        val enemyType = if (driverType == "Enemy") EnemyType.entries.find { it.displayName == enemyDriverSelectBox.selected } else null
        val npcType = if (driverType == "NPC") NPCType.entries.find { it.displayName == npcDriverSelectBox.selected } else null

        return CarSpawnConfig(
            carType = carType,
            isLocked = isLockedCheckbox.isChecked,
            driverCharacterType = driverType,
            enemyDriverType = enemyType,
            npcDriverType = npcType
        )
    }

    private fun createCarItem(carType: CarType, isSelected: Boolean): CarSelectionItem {
        val container = Table()
        container.pad(8f)

        // Create backgrounds for normal and selected states
        val normalBg = createItemBackground(Color(0.3f, 0.3f, 0.35f, 0.9f))
        val selectedBg = createItemBackground(Color(0.6f, 0.4f, 0.8f, 0.95f))
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
        container.add(nameLabel).width(80f).center().row() // Removed padBottom

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
            val alpha = 0.85f + (y / 60f) * 0.1f
            pixmap.setColor(0.15f, 0.1f, 0.2f, alpha)
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
        pixmap.setColor(Color.PURPLE) // Simple fallback
        pixmap.fill()
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

        if (isLockedCheckbox.isChecked != carSystem.isNextCarLocked) {
            isLockedCheckbox.isChecked = carSystem.isNextCarLocked
        }

        val currentIndex = carSystem.currentSelectedCarIndex

        // Animate all car items
        for (i in carItems.indices) {
            val item = carItems[i]
            val isSelected = i == currentIndex

            // Create smooth transition animations
            val targetScale = if (isSelected) 1.1f else 1.0f
            val targetColor = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)

            // Apply animations using LibGDX actions
            item.container.clearActions()
            item.container.addAction(
                Actions.parallel(
                    Actions.scaleTo(targetScale, targetScale, 0.2f, Interpolation.smooth),
                    Actions.run {
                        item.container.background = if (isSelected) item.selectedBackground else item.background
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
        carSelectionTable.isVisible = true
        carSelectionTable.toFront()
    }

    fun hide() {
        carSelectionTable.isVisible = false
    }

    fun dispose() {
        // Dispose cached textures
        for (texture in loadedTextures.values) {
            texture.dispose()
        }
        loadedTextures.clear()
    }
}
