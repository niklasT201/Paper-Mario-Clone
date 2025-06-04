package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.viewport.ScreenViewport

class UIManager(private val blockSystem: BlockSystem) {
    private lateinit var stage: Stage
    private lateinit var skin: Skin
    private lateinit var blockSelectionUI: BlockSelectionUI
    private lateinit var mainTable: Table

    var isUIVisible = true
        private set
    var selectedTool = Tool.BLOCK

    enum class Tool {
        BLOCK, PLAYER, OBJECT
    }

    fun initialize() {
        stage = Stage(ScreenViewport())
        skin = createSkin()

        // Initialize mainTable first as blockSelectionUI might depend on the stage already having some base setup
        setupMainUI() // This creates mainTable and adds it to the stage

        blockSelectionUI = BlockSelectionUI(blockSystem, skin, stage)
        blockSelectionUI.initialize() // This creates blockSelectionTable and adds it to the stage

        // Set initial visibility for the main UI panel
        mainTable.isVisible = isUIVisible
    }

    private fun setupMainUI() {
        // Create main UI table
        mainTable = Table() // Assign here
        mainTable.setFillParent(true)
        mainTable.top().left()
        mainTable.pad(20f)

        // Title - removed font scaling to prevent pixelation
        val titleLabel = Label("World Builder", skin)
        mainTable.add(titleLabel).colspan(2).padBottom(20f).row()

        // Current tool display
        val currentToolLabel = Label("Current Tool:", skin)
        mainTable.add(currentToolLabel).padBottom(10f).row()

        val toolDisplayLabel = Label("Ground Block", skin)
        toolDisplayLabel.name = "toolDisplay" // Give it a name so we can find it later
        mainTable.add(toolDisplayLabel).padBottom(20f).row()

        // Instructions
        val instructionLabel = Label("Instructions:", skin)
        mainTable.add(instructionLabel).padBottom(10f).row()

        val instructions = """
        Tool Selection:
        • 1 - Block Tool (place/remove blocks)
        • 2 - Player Tool (move player)
        • 3 - Object Tool (place objects)

        Controls:
        • Left click to place
        • Right click to remove blocks
        • Hold Right click + drag to rotate camera
        • Mouse wheel to zoom in/out (or block selection)
        • WASD to move player
        • H to toggle this UI
        • B to toggle Block Selection Mode
        • C to toggle Free Camera
        • 1 for Orbiting Camera (building)
        • 2 for Player Camera (Paper Mario)
        • Q/E to adjust camera angle (player mode)
        • R/T to adjust camera height (player mode)
        • Blocks snap to 4x4 grid
        • Paper Mario style rotation!
        • Player has collision detection!
        • Block Selection: Hold B + scroll to choose blocks!
        • Object Tool: Place 3D cross-shaped objects!
        """.trimIndent()

        val instructionText = Label(instructions, skin)
        instructionText.setWrap(true)
        mainTable.add(instructionText).width(250f).padBottom(20f).row()

        // Stats
        val statsLabel = Label("Stats:", skin)
        mainTable.add(statsLabel).padBottom(10f).row()

        val statsText = Label("Blocks: 3\nPlayer: Placed\nObjects: 0", skin)
        mainTable.add(statsText).width(200f).row()

        stage.addActor(mainTable)
    }

    private fun createSkin(): Skin {
        return try {
            val loadedSkin = Skin(Gdx.files.internal("ui/uiskin.json"))

            // Try to load and set custom font
            try {
                val customFont = BitmapFont(Gdx.files.internal("ui/default.fnt"))
                loadedSkin.add("default-font", customFont, BitmapFont::class.java)

                // Update existing styles to use the new font
                loadedSkin.get(Label.LabelStyle::class.java).font = customFont

                // Only update TextButton style if it exists
                try {
                    loadedSkin.get(TextButton.TextButtonStyle::class.java).font = customFont
                } catch (e: Exception) {
                    // TextButton style might not exist in the skin, that's okay
                }

                println("Custom font loaded successfully from ui/default.fnt")
            } catch (e: Exception) {
                println("Could not load custom font from ui/default.fnt: ${e.message}")
            }

            loadedSkin
        } catch (e: Exception) {
            println("Could not load UI skin from assets/ui/, using default skin with custom font")
            createDefaultSkin()
        }
    }

    private fun createDefaultSkin(): Skin {
        val skin = Skin()

        // Create a 1x1 white texture
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.WHITE)
        pixmap.fill()
        val texture = Texture(pixmap)
        pixmap.dispose()

        // Try to load custom font first, fallback to default
        val font = try {
            val customFont = BitmapFont(Gdx.files.internal("ui/default.fnt"))
            println("Custom font loaded successfully from ui/default.fnt (fallback)")
            customFont
        } catch (e: Exception) {
            println("Could not load custom font from ui/default.fnt, using system default: ${e.message}")
            BitmapFont()
        }

        // Add font to skin
        skin.add("default-font", font)

        // Create drawable for UI elements
        val buttonTexture = TextureRegion(texture)
        val buttonDrawable = TextureRegionDrawable(buttonTexture)

        // Add background drawable
        skin.add("default-round", buttonDrawable.tint(Color(0.2f, 0.2f, 0.2f, 0.8f)))

        // Label style
        val labelStyle = Label.LabelStyle()
        labelStyle.font = font
        labelStyle.fontColor = Color.WHITE
        skin.add("default", labelStyle)

        return skin
    }

    fun toggleVisibility() {
        isUIVisible = !isUIVisible
        mainTable.isVisible = isUIVisible
    }

    fun showBlockSelection() {
        blockSelectionUI.show()
    }

    fun hideBlockSelection() {
        blockSelectionUI.hide()
    }

    fun updateBlockSelection() {
        blockSelectionUI.update()
    }

    // Method to update the tool display when tool changes
    fun updateToolDisplay() {
        val toolDisplayLabel = mainTable.findActor<Label>("toolDisplay")
            toolDisplayLabel.setText(when (selectedTool) {
                Tool.BLOCK -> "Ground Block"
                Tool.PLAYER -> "Player"
                Tool.OBJECT -> "Object"
            })
    }

    fun getStage(): Stage = stage

    fun render() {
        stage.act(Gdx.graphics.deltaTime)
        stage.draw()
    }

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    fun dispose() {
        blockSelectionUI.dispose()
        stage.dispose()
        skin.dispose()
    }
}
