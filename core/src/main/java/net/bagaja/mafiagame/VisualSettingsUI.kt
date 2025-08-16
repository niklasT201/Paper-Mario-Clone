package net.bagaja.mafiagame

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class VisualSettingsUI(
    private val skin: Skin,
    private val cameraManager: CameraManager,
    private val uiManager: UIManager,
    private val targetingIndicatorSystem: TargetingIndicatorSystem,
    private val trajectorySystem: TrajectorySystem,
    private val meleeRangeIndicatorSystem: MeleeRangeIndicatorSystem
) {

    private lateinit var mainContainer: Table
    private lateinit var overlay: Image
    private var isVisible = false

    private val fullscreenCheckbox: CheckBox
    private val letterboxCheckbox: CheckBox
    private val indicatorCheckbox: CheckBox
    private val trajectoryCheckbox: CheckBox
    private lateinit var indicatorStyleButton: TextButton

    init {
        // Initialize checkboxes with vintage styling
        fullscreenCheckbox = createVintageCheckbox(" Full Screen Operations")
        letterboxCheckbox = createVintageCheckbox(" Enable Letterbox (4:3 Ratio)")
        indicatorCheckbox = createVintageCheckbox(" Show Targeting Indicator")
        trajectoryCheckbox = createVintageCheckbox(" Show Trajectory Arc")

        initialize()
        setupListeners()
    }

    private fun initialize() {
        createSmokyOverlay()
        createNewspaperSettings()
    }

    private fun updateIndicatorButtonStyle() {
        val style = meleeRangeIndicatorSystem.getCurrentStyle()
        indicatorStyleButton.setText("Melee Ring: ${style.displayName}")
    }

    private fun createSmokyOverlay() {
        // Create a smoky, noir-style overlay matching the pause menu
        val pixmap = Pixmap(100, 100, Pixmap.Format.RGBA8888)

        // Create layered smoke effect
        for (y in 0 until 100) {
            for (x in 0 until 100) {
                val waveEffect = sin((x + y) * 0.1) * 0.1f
                val smokeIntensity = (y / 100f) * 0.4f + waveEffect

                // Dark sepia overlay with smoke
                val alpha = 0.7f + smokeIntensity
                pixmap.setColor(0.1f, 0.08f, 0.06f, alpha.coerceIn(0.0, 0.85).toFloat())
                pixmap.drawPixel(x, y)
            }
        }

        overlay = Image(Texture(pixmap))
        pixmap.dispose()
        overlay.setFillParent(true)
        overlay.isVisible = false
    }

    private fun createNewspaperSettings() {
        mainContainer = Table()
        mainContainer.setFillParent(true)
        mainContainer.center()
        mainContainer.isVisible = false

        // Main newspaper/wanted poster container
        val newspaperTable = Table()
        newspaperTable.background = createNewspaperBackground()
        newspaperTable.pad(50f, 60f, 40f, 60f)

        // Vintage header with art deco styling
        val headerTable = Table()
        headerTable.add(createArtDecoDecoration("left")).padRight(25f)

        val titleLabel = Label("⦿ VISUAL SETTINGS ⦿", skin, "title")
        titleLabel.setAlignment(Align.center)
        titleLabel.color = Color.valueOf("#1A0F0A") // Dark ink black
        headerTable.add(titleLabel)

        headerTable.add(createArtDecoDecoration("right")).padLeft(25f)

        newspaperTable.add(headerTable).padBottom(35f).row()

        // Vintage subtitle
        val subtitleLabel = Label("~ Configure Your Territory View ~", skin, "default")
        subtitleLabel.setAlignment(Align.center)
        subtitleLabel.color = Color.valueOf("#3D2817")
        newspaperTable.add(subtitleLabel).padBottom(30f).row()

        // Settings section with vintage newspaper styling
        val settingsTable = Table()
        settingsTable.background = createSettingsBackground()
        settingsTable.pad(25f)

        // Settings header
        val settingsHeaderLabel = Label("═══ DISPLAY OPTIONS ═══", skin, "default")
        settingsHeaderLabel.setAlignment(Align.center)
        settingsHeaderLabel.color = Color.valueOf("#2C1810")
        settingsTable.add(settingsHeaderLabel).padBottom(20f).row()

        // Checkbox options with vintage styling
        val checkboxTable = Table()
        checkboxTable.add(fullscreenCheckbox).left().padBottom(15f).row()
        checkboxTable.add(letterboxCheckbox).left().padBottom(15f).row()
        checkboxTable.add(indicatorCheckbox).left().padBottom(15f).row()
        checkboxTable.add(trajectoryCheckbox).left().padBottom(10f).row()

        settingsTable.add(checkboxTable).fillX().row()

        // Melee Indicator Style Button
        indicatorStyleButton = createVintageButton("Melee Style: Solid", Color.valueOf("#654321"))
        updateIndicatorButtonStyle() // Set initial text
        settingsTable.add(indicatorStyleButton).width(320f).height(50f).padTop(20f).row()

        newspaperTable.add(settingsTable).width(380f).padBottom(25f).row()

        // Back button with vintage styling
        val backButton = createVintageButton("⬅ RETURN TO PAUSE MENU ⬅", Color.valueOf("#8B0000"))
        newspaperTable.add(backButton).width(320f).height(50f).row()

        // Bottom art deco decoration
        val bottomDecoration = createBottomBanner()
        newspaperTable.add(bottomDecoration).padTop(20f)

        mainContainer.add(newspaperTable)

        // Setup back button listener
        backButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                uiManager.returnToPauseMenu()
            }
        })
    }

    private fun createNewspaperBackground(): TextureRegionDrawable {
        val pixmap = Pixmap(420, 520, Pixmap.Format.RGBA8888)

        // Aged newspaper color
        val paperColor = Color.valueOf("#F5F1E8") // Cream newspaper
        pixmap.setColor(paperColor)
        pixmap.fill()

        // Add newsprint texture and aging
        val inkSpots = Color.valueOf("#E8E0D6")
        for (i in 0 until 350) {
            val x = Random.nextInt(420)
            val y = Random.nextInt(520)
            val size = Random.nextInt(2) + 1
            pixmap.setColor(inkSpots)
            pixmap.fillCircle(x, y, size)
        }

        // Create art deco border with geometric patterns
        val borderColor = Color.valueOf("#2C1810") // Dark brown
        pixmap.setColor(borderColor)

        // Main border
        for (i in 0 until 6) {
            pixmap.drawRectangle(i, i, 420 - i * 2, 520 - i * 2)
        }

        // Art deco corner elements
        drawArtDecoCorners(pixmap, 420, 520)

        // Add prohibition-era elements
        drawVintageElements(pixmap, 420, 520)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createSettingsBackground(): TextureRegionDrawable {
        // I've increased the pixmap height slightly to accommodate the thicker border and new button
        val pixmap = Pixmap(340, 250, Pixmap.Format.RGBA8888)

        // Slightly darker paper for settings section
        val paperColor = Color.valueOf("#F0EDE4")
        pixmap.setColor(paperColor)
        pixmap.fill()

        // Add subtle texture
        val textureColor = Color.valueOf("#E5E2D9")
        for (i in 0 until 120) { // More iterations for the larger area
            val x = Random.nextInt(340)
            val y = Random.nextInt(250)
            pixmap.setColor(textureColor)
            pixmap.drawPixel(x, y)
        }

        // Elegant border
        val borderColor = Color.valueOf("#8B6914") // Dark gold
        pixmap.setColor(borderColor)
        val borderThickness = 3 // Set the desired thickness here (e.g., 3 pixels)

        // Draw the four rectangles that make up the thick border
        pixmap.fillRectangle(0, 0, 340, borderThickness) // Top line
        pixmap.fillRectangle(0, 250 - borderThickness, 340, borderThickness) // Bottom line
        pixmap.fillRectangle(0, 0, borderThickness, 250) // Left line
        pixmap.fillRectangle(340 - borderThickness, 0, borderThickness, 250) // Right line

        // Corner flourishes
        for (i in 0 until 10) {
            // Top corners
            pixmap.drawLine(5 + i, 5, 5, 5 + i)
            pixmap.drawLine(334 - i, 5, 334, 5 + i)
            // Bottom corners
            pixmap.drawLine(5 + i, 244, 5, 244 - i)
            pixmap.drawLine(334 - i, 244, 334, 244 - i)
        }

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun drawArtDecoCorners(pixmap: Pixmap, width: Int, height: Int) {
        val accentColor = Color.valueOf("#8B6914") // Dark gold
        pixmap.setColor(accentColor)

        val cornerSize = 25

        // Art deco fan patterns at corners
        for (i in 10 until cornerSize) {
            // Top-left fan
            pixmap.drawLine(10, i, i, 10)
            pixmap.drawLine(12, i, i, 12)

            // Top-right fan
            pixmap.drawLine(width - 10, i, width - i, 10)
            pixmap.drawLine(width - 12, i, width - i, 12)

            // Bottom-left fan
            pixmap.drawLine(10, height - i, i, height - 10)
            pixmap.drawLine(12, height - i, i, height - 12)

            // Bottom-right fan
            pixmap.drawLine(width - 10, height - i, width - i, height - 10)
            pixmap.drawLine(width - 12, height - i, width - i, height - 12)
        }
    }

    private fun drawVintageElements(pixmap: Pixmap, width: Int, height: Int) {
        val accentColor = Color.valueOf("#8B6914") // Dark gold
        pixmap.setColor(accentColor)

        // Side geometric patterns
        for (y in 120 until height - 120 step 50) {
            // Left side diamonds
            pixmap.fillCircle(20, y, 4)
            pixmap.drawRectangle(16, y - 2, 8, 4)

            // Right side diamonds
            pixmap.fillCircle(width - 20, y, 4)
            pixmap.drawRectangle(width - 24, y - 2, 8, 4)
        }
    }

    private fun createVintageCheckbox(text: String): CheckBox {
        val checkbox = CheckBox(text, skin)

        // Style the checkbox with vintage colors
        checkbox.label.color = Color.valueOf("#2C1810") // Dark brown text

        // Create custom checkbox style
        val checkboxPixmap = Pixmap(20, 20, Pixmap.Format.RGBA8888)

        // Unchecked state - vintage box
        val boxColor = Color.valueOf("#F5F1E8") // Cream
        checkboxPixmap.setColor(boxColor)
        checkboxPixmap.fill()

        // Border
        val borderColor = Color.valueOf("#2C1810") // Dark brown
        checkboxPixmap.setColor(borderColor)
        checkboxPixmap.drawRectangle(0, 0, 20, 20)
        checkboxPixmap.drawRectangle(1, 1, 18, 18)

        // Corner details
        val accentColor = Color.valueOf("#8B6914") // Dark gold
        checkboxPixmap.setColor(accentColor)
        for (i in 0 until 4) {
            checkboxPixmap.drawPixel(3 + i, 3)
            checkboxPixmap.drawPixel(3, 3 + i)
            checkboxPixmap.drawPixel(16 - i, 3)
            checkboxPixmap.drawPixel(16, 3 + i)
            checkboxPixmap.drawPixel(3 + i, 16)
            checkboxPixmap.drawPixel(3, 16 - i)
            checkboxPixmap.drawPixel(16 - i, 16)
            checkboxPixmap.drawPixel(16, 16 - i)
        }

        val uncheckedTexture = Texture(checkboxPixmap)
        checkboxPixmap.dispose()

        // Checked state - add vintage checkmark
        val checkedPixmap = Pixmap(20, 20, Pixmap.Format.RGBA8888)
        checkedPixmap.setColor(boxColor)
        checkedPixmap.fill()

        // Border
        checkedPixmap.setColor(borderColor)
        checkedPixmap.drawRectangle(0, 0, 20, 20)
        checkedPixmap.drawRectangle(1, 1, 18, 18)

        // Corner details
        checkedPixmap.setColor(accentColor)
        for (i in 0 until 4) {
            checkedPixmap.drawPixel(3 + i, 3)
            checkedPixmap.drawPixel(3, 3 + i)
            checkedPixmap.drawPixel(16 - i, 3)
            checkedPixmap.drawPixel(16, 3 + i)
            checkedPixmap.drawPixel(3 + i, 16)
            checkedPixmap.drawPixel(3, 16 - i)
            checkedPixmap.drawPixel(16 - i, 16)
            checkedPixmap.drawPixel(16, 16 - i)
        }

        // Vintage checkmark - art deco style
        val checkColor = Color.valueOf("#2C1810")
        checkedPixmap.setColor(checkColor)
        // Draw art deco checkmark
        for (i in 0 until 6) {
            checkedPixmap.drawLine(6, 10 + i, 8 + i, 12 + i)
            checkedPixmap.drawLine(8 + i, 12 + i, 14, 6 + i)
        }

        val checkedTexture = Texture(checkedPixmap)
        checkedPixmap.dispose()

        // Apply textures to checkbox style
        checkbox.style.checkboxOff = TextureRegionDrawable(TextureRegion(uncheckedTexture))
        checkbox.style.checkboxOn = TextureRegionDrawable(TextureRegion(checkedTexture))

        return checkbox
    }

    private fun createVintageButton(text: String, bgColor: Color): TextButton {
        val button = TextButton(text, skin, "default")

        // Create vintage button with leather/wood texture
        val buttonPixmap = Pixmap(320, 45, Pixmap.Format.RGBA8888)

        // Base color - darker vintage tone
        val baseColor = Color(bgColor.r * 0.8f, bgColor.g * 0.8f, bgColor.b * 0.8f, 1f)
        buttonPixmap.setColor(baseColor)
        buttonPixmap.fill()

        // Add leather-like texture
        val textureColor = Color(bgColor.r * 0.6f, bgColor.g * 0.6f, bgColor.b * 0.6f, 0.7f)
        for (i in 0 until 90) {
            val x = Random.nextInt(320)
            val y = Random.nextInt(45)
            val size = Random.nextInt(3) + 1
            buttonPixmap.setColor(textureColor)
            buttonPixmap.fillCircle(x, y, size)
        }

        // Art deco style border
        val borderColor = Color.valueOf("#1A0F0A")
        buttonPixmap.setColor(borderColor)

        // Multiple border lines for depth
        buttonPixmap.drawRectangle(0, 0, 320, 45)
        buttonPixmap.drawRectangle(1, 1, 318, 43)
        buttonPixmap.drawRectangle(2, 2, 316, 41)

        // Brass-like highlight
        val highlightColor = Color.valueOf("#CD7F32") // Bronze
        buttonPixmap.setColor(highlightColor)
        buttonPixmap.drawLine(3, 3, 316, 3)
        buttonPixmap.drawLine(3, 3, 3, 41)

        // Corner accents
        for (i in 0 until 8) {
            buttonPixmap.drawPixel(5 + i, 5)
            buttonPixmap.drawPixel(5, 5 + i)
            buttonPixmap.drawPixel(314 - i, 5)
            buttonPixmap.drawPixel(314, 5 + i)
            buttonPixmap.drawPixel(5 + i, 39)
            buttonPixmap.drawPixel(5, 39 - i)
            buttonPixmap.drawPixel(314 - i, 39)
            buttonPixmap.drawPixel(314, 39 - i)
        }

        val buttonTexture = Texture(buttonPixmap)
        buttonPixmap.dispose()

        button.style.up = TextureRegionDrawable(TextureRegion(buttonTexture))
        button.label.color = Color.valueOf("#F5F1E8") // Cream text

        return button
    }

    private fun createArtDecoDecoration(side: String): Image {
        val pixmap = Pixmap(45, 45, Pixmap.Format.RGBA8888)
        val decorColor = Color.valueOf("#8B6914") // Dark gold
        pixmap.setColor(decorColor)

        // Art deco geometric pattern
        val center = 22

        when (side) {
            "left" -> {
                // Left-pointing arrow with geometric elements
                for (i in 0 until 20) {
                    val offset = i / 2
                    pixmap.drawLine(center - offset, center - i/2, center + offset, center - i/2)
                    if (i < 15) {
                        pixmap.drawLine(center - offset, center + i/2, center + offset, center + i/2)
                    }
                }

                // Central diamond
                pixmap.fillCircle(center, center, 6)
                pixmap.setColor(Color.valueOf("#CD7F32"))
                pixmap.fillCircle(center, center, 3)
            }
            "right" -> {
                // Right-pointing arrow with geometric elements
                for (i in 0 until 20) {
                    val offset = i / 2
                    pixmap.drawLine(center - offset, center - i/2, center + offset, center - i/2)
                    if (i < 15) {
                        pixmap.drawLine(center - offset, center + i/2, center + offset, center + i/2)
                    }
                }

                // Central diamond
                pixmap.fillCircle(center, center, 6)
                pixmap.setColor(Color.valueOf("#CD7F32"))
                pixmap.fillCircle(center, center, 3)
            }
        }

        val texture = Texture(pixmap)
        pixmap.dispose()
        return Image(texture)
    }

    private fun createBottomBanner(): Image {
        val pixmap = Pixmap(200, 30, Pixmap.Format.RGBA8888)
        val bannerColor = Color.valueOf("#2C1810")
        pixmap.setColor(bannerColor)

        // Create banner ribbon
        pixmap.fill()

        // Banner notches at ends
        for (i in 0 until 8) {
            pixmap.setColor(Color.CLEAR)
            pixmap.drawLine(i, 15 + i, i, 30)
            pixmap.drawLine(199 - i, 15 + i, 199 - i, 30)
        }

        // Gold trim
        pixmap.setColor(Color.valueOf("#CD7F32"))
        pixmap.drawRectangle(0, 0, 200, 30)
        pixmap.drawRectangle(1, 1, 198, 28)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return Image(texture)
    }

    private fun setupListeners() {
        // Fullscreen checkbox listener
        fullscreenCheckbox.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                val mode = if (fullscreenCheckbox.isChecked) CameraManager.DisplayMode.FULLSCREEN else CameraManager.DisplayMode.WINDOWED
                cameraManager.setDisplayMode(mode)
            }
        })

        // Letterbox checkbox listener
        letterboxCheckbox.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                uiManager.toggleLetterbox()
            }
        })

        // Targeting indicator listener
        indicatorCheckbox.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                targetingIndicatorSystem.toggle()
            }
        })

        // Trajectory arc listener
        trajectoryCheckbox.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                trajectorySystem.toggle()
            }
        })

        indicatorStyleButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val nextStyle = when (meleeRangeIndicatorSystem.getCurrentStyle()) {
                    IndicatorStyle.SOLID_CIRCLE -> IndicatorStyle.TEXTURED_RING
                    IndicatorStyle.TEXTURED_RING -> IndicatorStyle.SOLID_CIRCLE
                }
                meleeRangeIndicatorSystem.setStyle(nextStyle)
                updateIndicatorButtonStyle()
            }
        })
    }

    fun show(stage: Stage) {
        // Update checkbox states
        fullscreenCheckbox.isChecked = cameraManager.currentDisplayMode == CameraManager.DisplayMode.FULLSCREEN
        letterboxCheckbox.isChecked = uiManager.isLetterboxEnabled()
        indicatorCheckbox.isChecked = targetingIndicatorSystem.isEnabled()
        trajectoryCheckbox.isChecked = trajectorySystem.isEnabled()
        updateIndicatorButtonStyle()
        stage.addActor(overlay)

        // Add to stage
        stage.addActor(overlay)
        stage.addActor(mainContainer)

        isVisible = true
        overlay.isVisible = true
        mainContainer.isVisible = true
        mainContainer.toFront()

        // Smoky fade-in effect matching pause menu
        overlay.color.a = 0f
        overlay.addAction(Actions.fadeIn(0.6f, Interpolation.fade))

        // Newspaper unfolding animation
        mainContainer.scaleX = 0.1f
        mainContainer.scaleY = 0.8f
        mainContainer.color.a = 0f

        mainContainer.addAction(
            Actions.parallel(
                Actions.fadeIn(0.5f, Interpolation.fade),
                Actions.scaleTo(1f, 1f, 0.7f, Interpolation.swingOut)
            )
        )
    }

    fun hide() {
        isVisible = false

        overlay.addAction(Actions.sequence(
            Actions.fadeOut(0.5f, Interpolation.fade),
            Actions.run { overlay.remove() }
        ))

        mainContainer.addAction(Actions.sequence(
            Actions.parallel(
                Actions.fadeOut(0.4f, Interpolation.fade),
                Actions.scaleTo(0.1f, 0.8f, 0.4f, Interpolation.swingIn)
            ),
            Actions.run { mainContainer.remove() }
        ))
    }

    fun isVisible(): Boolean = isVisible

    fun dispose() {
        // Dispose textures if needed
    }
}
