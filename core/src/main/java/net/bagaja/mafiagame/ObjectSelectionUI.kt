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

class ObjectSelectionUI(
    private val objectSystem: ObjectSystem,
    private val skin: Skin,
    private val stage: Stage
) {
    private lateinit var objectSelectionTable: Table
    private lateinit var objectItems: MutableList<ObjectSelectionItem>
    private val loadedTextures = mutableMapOf<String, Texture>() // Cache for loaded textures

    // Data class to hold object selection item components
    private data class ObjectSelectionItem(
        val container: Table,
        val iconImage: Image,
        val nameLabel: Label,
        val background: Drawable,
        val selectedBackground: Drawable,
        val objectType: ObjectType
    )

    fun initialize() {
        setupObjectSelectionUI()
        objectSelectionTable.setVisible(false)
    }

    private fun setupObjectSelectionUI() {
        // Create object selection table at the top center
        objectSelectionTable = Table()
        objectSelectionTable.setFillParent(true)
        objectSelectionTable.top()
        objectSelectionTable.pad(40f)

        // Create main container with modern styling
        val mainContainer = Table()

        // Create a more modern background with rounded corners and shadow effect
        val backgroundStyle = createModernBackground()
        mainContainer.background = backgroundStyle
        mainContainer.pad(20f, 30f, 20f, 30f)

        // Title with modern styling
        val titleLabel = Label("Object Selection", skin)
        titleLabel.setFontScale(1.4f)
        titleLabel.color = Color(0.9f, 0.9f, 0.9f, 1f) // Light gray
        mainContainer.add(titleLabel).padBottom(15f).row()

        // Create horizontal container for object items
        val objectContainer = Table()
        objectContainer.pad(10f)

        // Create object items for each object type
        objectItems = mutableListOf()
        val objectTypes = ObjectType.values()

        for (i in objectTypes.indices) {
            val objectType = objectTypes[i]
            val item = createObjectItem(objectType, i == objectSystem.currentSelectedObjectIndex)
            objectItems.add(item)

            // Add spacing between items
            if (i > 0) {
                objectContainer.add().width(15f) // Spacer
            }
            objectContainer.add(item.container).size(90f, 110f)
        }

        mainContainer.add(objectContainer).padBottom(10f).row()

        // Instructions with modern styling
        val instructionLabel = Label("Hold [O] + Mouse Wheel to change objects", skin)
        instructionLabel.setFontScale(0.9f)
        instructionLabel.color = Color(0.7f, 0.7f, 0.7f, 1f) // Darker gray
        mainContainer.add(instructionLabel).padBottom(5f).row()

        // Additional instructions for fine positioning and debug mode
        val fineInstructionLabel = Label("F - Fine positioning | D - Debug mode (show invisible)", skin)
        fineInstructionLabel.setFontScale(0.8f)
        fineInstructionLabel.color = Color(0.6f, 0.6f, 0.6f, 1f)
        mainContainer.add(fineInstructionLabel)

        objectSelectionTable.add(mainContainer)
        stage.addActor(objectSelectionTable)
    }

    private fun createObjectItem(objectType: ObjectType, isSelected: Boolean): ObjectSelectionItem {
        val container = Table()
        container.pad(8f)

        // Create backgrounds for normal and selected states
        val normalBg = createItemBackground(Color(0.3f, 0.3f, 0.35f, 0.9f))
        val selectedBg = createItemBackground(Color(0.4f, 0.6f, 0.8f, 0.95f))

        container.background = if (isSelected) selectedBg else normalBg

        // Object icon - try to load actual texture first, fallback to generated icon
        val iconTexture = loadObjectTexture(objectType)
        val iconImage = Image(iconTexture)
        container.add(iconImage).size(50f, 50f).padBottom(8f).row()

        // Object name
        val nameLabel = Label(objectType.displayName, skin)
        nameLabel.setFontScale(0.7f)
        nameLabel.setWrap(true)
        nameLabel.setAlignment(Align.center)
        nameLabel.color = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
        container.add(nameLabel).width(80f).center().padBottom(5f).row()

        // Additional info for invisible objects
        if (objectType.isInvisible) {
            val invisibleLabel = Label("(Invisible)", skin)
            invisibleLabel.setFontScale(0.6f)
            invisibleLabel.color = Color(0.8f, 0.6f, 0.6f, 1f)
            container.add(invisibleLabel)
        }

        return ObjectSelectionItem(container, iconImage, nameLabel, normalBg, selectedBg, objectType)
    }

    private fun loadObjectTexture(objectType: ObjectType): TextureRegion {
        // For invisible objects, create a special icon
        if (objectType.isInvisible) {
            return createInvisibleObjectIcon(objectType)
        }

        val texturePath = objectType.texturePath

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
                println("Loaded object texture: $texturePath")
                TextureRegion(texture)
            } else {
                println("Texture not found: $texturePath, using fallback")
                createObjectIcon(objectType)
            }
        } catch (e: Exception) {
            println("Failed to load texture: $texturePath, error: ${e.message}, using fallback")
            createObjectIcon(objectType)
        }
    }

    private fun createInvisibleObjectIcon(objectType: ObjectType): TextureRegion {
        val pixmap = Pixmap(50, 50, Pixmap.Format.RGBA8888)

        when (objectType) {
            ObjectType.LIGHT_SOURCE -> {
                // Create a light bulb icon with rays
                pixmap.setColor(Color.YELLOW)

                // Light bulb shape
                pixmap.fillCircle(25, 30, 12)
                pixmap.fillRectangle(22, 40, 6, 8)

                // Light rays
                pixmap.setColor(Color(1f, 1f, 0.8f, 0.8f))
                for (i in 0..7) {
                    val angle = i * 45f * Math.PI / 180
                    val startX = 25 + (15 * Math.cos(angle)).toInt()
                    val startY = 30 + (15 * Math.sin(angle)).toInt()
                    val endX = 25 + (22 * Math.cos(angle)).toInt()
                    val endY = 30 + (22 * Math.sin(angle)).toInt()

                    // Draw thick rays
                    for (thickness in -1..1) {
                        pixmap.drawLine(
                            startX + thickness, startY,
                            endX + thickness, endY
                        )
                        pixmap.drawLine(
                            startX, startY + thickness,
                            endX, endY + thickness
                        )
                    }
                }
            }
            else -> {
                // Fallback for other invisible objects
                pixmap.setColor(Color(0.8f, 0.8f, 0.8f, 0.5f))
                pixmap.drawRectangle(10, 10, 30, 30)
                pixmap.drawLine(10, 10, 40, 40)
                pixmap.drawLine(40, 10, 10, 40)
            }
        }

        val texture = Texture(pixmap)
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

    private fun createObjectIcon(objectType: ObjectType): TextureRegion {
        // Create colored shapes representing different object types (fallback)
        val pixmap = Pixmap(50, 50, Pixmap.Format.RGBA8888)

        when (objectType) {
            ObjectType.TREE -> {
                // Green tree shape
                pixmap.setColor(Color(0.4f, 0.8f, 0.2f, 1f))
                pixmap.fillCircle(25, 20, 15) // Tree crown
                pixmap.setColor(Color(0.6f, 0.4f, 0.2f, 1f))
                pixmap.fillRectangle(22, 35, 6, 15) // Tree trunk
            }
            ObjectType.LANTERN -> {
                // Yellow/orange lantern
                pixmap.setColor(Color(1f, 0.8f, 0.3f, 1f))
                pixmap.fillRectangle(20, 15, 10, 20) // Lantern body
                pixmap.setColor(Color(0.8f, 0.6f, 0.2f, 1f))
                pixmap.fillRectangle(18, 10, 14, 5) // Top
                pixmap.fillRectangle(18, 35, 14, 5) // Bottom
                pixmap.setColor(Color(1f, 1f, 0.8f, 1f))
                pixmap.fillCircle(25, 25, 3) // Light glow
            }
            ObjectType.LIGHT_SOURCE -> {
                // This shouldn't normally be called for invisible objects
                createInvisibleObjectIcon(objectType)
            }
            ObjectType.BROKEN_LANTERN -> {
                // Yellow/orange lantern
                pixmap.setColor(Color(1f, 0.8f, 0.3f, 1f))
                pixmap.fillRectangle(20, 15, 10, 20) // Lantern body
                pixmap.setColor(Color(0.8f, 0.6f, 0.2f, 1f))
                pixmap.fillRectangle(18, 10, 14, 5) // Top
                pixmap.fillRectangle(18, 35, 14, 5) // Bottom
                pixmap.setColor(Color(1f, 1f, 0.8f, 1f))
                pixmap.fillCircle(25, 25, 3) // Light glow
            }
            ObjectType.TURNEDOFF_LANTERN -> {
                // Yellow/orange lantern
                pixmap.setColor(Color(1f, 0.8f, 0.3f, 1f))
                pixmap.fillRectangle(20, 15, 10, 20) // Lantern body
                pixmap.setColor(Color(0.8f, 0.6f, 0.2f, 1f))
                pixmap.fillRectangle(18, 10, 14, 5) // Top
                pixmap.fillRectangle(18, 35, 14, 5) // Bottom
                pixmap.setColor(Color(1f, 1f, 0.8f, 1f))
                pixmap.fillCircle(25, 25, 3) // Light glow
            }
        }

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegion(texture)
    }

    fun update() {
        val currentIndex = objectSystem.currentSelectedObjectIndex

        // Animate all object items
        for (i in objectItems.indices) {
            val item = objectItems[i]
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
        objectSelectionTable.setVisible(true)
    }

    fun hide() {
        objectSelectionTable.setVisible(false)
    }

    fun dispose() {
        // Dispose cached textures
        for (texture in loadedTextures.values) {
            texture.dispose()
        }
        loadedTextures.clear()
    }
}
