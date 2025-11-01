package net.bagaja.mafiagame

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
import kotlin.random.Random
import kotlin.math.sin
import kotlin.math.cos

class AudioSettingsUI(
    private val skin: Skin,
    private val uiManager: UIManager,
    private val musicManager: MusicManager,
    private val soundManager: SoundManager
) {
    private lateinit var mainContainer: Table
    private lateinit var overlay: Image
    private var isVisible = false

    lateinit var backButton: TextButton
    private lateinit var masterVolumeSlider: Slider
    private lateinit var musicVolumeSlider: Slider
    private lateinit var sfxVolumeSlider: Slider

    init {
        initialize()
        setupListeners()
    }

    private fun initialize() {
        createSmokyOverlay()
        createNewspaperSettings()
    }

    private fun createSmokyOverlay() {
        val pixmap = Pixmap(100, 100, Pixmap.Format.RGBA8888)
        for (y in 0 until 100) {
            for (x in 0 until 100) {
                val waveEffect = sin((x + y) * 0.1) * 0.1f
                val smokeIntensity = (y / 100f) * 0.4f + waveEffect
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

        val titleLabel = Label("⦿ AUDIO SETTINGS ⦿", skin, "title")
        titleLabel.setAlignment(Align.center)
        titleLabel.color = Color.valueOf("#1A0F0A") // Dark ink black
        headerTable.add(titleLabel)

        headerTable.add(createArtDecoDecoration("right")).padLeft(25f)

        newspaperTable.add(headerTable).padBottom(35f).row()

        // Vintage subtitle
        val subtitleLabel = Label("~ Tune Your Listening Experience ~", skin, "default")
        subtitleLabel.setAlignment(Align.center)
        subtitleLabel.color = Color.valueOf("#3D2817")
        newspaperTable.add(subtitleLabel).padBottom(30f).row()

        // Settings section with vintage newspaper styling
        val scrollContentTable = Table()
        scrollContentTable.pad(25f)

        // Settings header
        val settingsHeaderLabel = Label("═══ VOLUME CONTROLS ═══", skin, "default")
        settingsHeaderLabel.setAlignment(Align.center)
        settingsHeaderLabel.color = Color.valueOf("#2C1810")
        scrollContentTable.add(settingsHeaderLabel).padBottom(20f).row()

        // Sliders section
        val slidersTable = Table()
        slidersTable.defaults().left().padBottom(15f)

        masterVolumeSlider = Slider(0f, 1f, 0.01f, false, skin)
        musicVolumeSlider = Slider(0f, 1f, 0.01f, false, skin)
        sfxVolumeSlider = Slider(0f, 1f, 0.01f, false, skin)

        slidersTable.add(Label("Master Volume", skin, "default")).left().row()
        slidersTable.add(masterVolumeSlider).width(320f).padBottom(20f).row()

        slidersTable.add(Label("Music Volume", skin, "default")).left().row()
        slidersTable.add(musicVolumeSlider).width(320f).padBottom(20f).row()

        slidersTable.add(Label("Sound FX Volume", skin, "default")).left().row()
        slidersTable.add(sfxVolumeSlider).width(320f).row()

        scrollContentTable.add(slidersTable).fillX().row()

        // Create the ScrollPane and put the scrollContentTable inside it
        val scrollPane = ScrollPane(scrollContentTable, skin, "vintage-newspaper")
        scrollPane.setScrollingDisabled(true, false)
        scrollPane.fadeScrollBars = false
        scrollPane.variableSizeKnobs = false

        newspaperTable.add(scrollPane).width(420f).height(350f).padBottom(25f).row()

        // Back button with vintage styling
        backButton = createVintageButton("⬅ RETURN TO PAUSE MENU ⬅", Color.valueOf("#8B0000"))
        newspaperTable.add(backButton).width(320f).height(50f).row()

        // Bottom art deco decoration
        val bottomDecoration = createBottomBanner()
        newspaperTable.add(bottomDecoration).padTop(20f)

        mainContainer.add(newspaperTable)
    }

    private fun setupListeners() {
        backButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                PlayerSettingsManager.save()
                uiManager.returnToPauseMenu()
            }
        })

        val sliderListener = object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                when (actor) {
                    masterVolumeSlider -> {
                        musicManager.setMasterVolume(masterVolumeSlider.value)
                        soundManager.setMasterVolume(masterVolumeSlider.value)
                        PlayerSettingsManager.current.masterVolume = masterVolumeSlider.value
                    }
                    musicVolumeSlider -> {
                        musicManager.setMusicVolume(musicVolumeSlider.value)
                        PlayerSettingsManager.current.musicVolume = musicVolumeSlider.value
                    }
                    sfxVolumeSlider -> {
                        soundManager.setSfxVolume(sfxVolumeSlider.value)
                        PlayerSettingsManager.current.sfxVolume = sfxVolumeSlider.value
                    }
                }
            }
        }

        val saveOnUpListener = object : ClickListener() {
            override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
                super.touchUp(event, x, y, pointer, button)
                PlayerSettingsManager.save()
            }
        }

        masterVolumeSlider.addListener(sliderListener)
        musicVolumeSlider.addListener(sliderListener)
        sfxVolumeSlider.addListener(sliderListener)

        masterVolumeSlider.addListener(saveOnUpListener)
        musicVolumeSlider.addListener(saveOnUpListener)
        sfxVolumeSlider.addListener(saveOnUpListener)
    }

    fun show(stage: Stage) {
        masterVolumeSlider.value = PlayerSettingsManager.current.masterVolume
        musicVolumeSlider.value = PlayerSettingsManager.current.musicVolume
        sfxVolumeSlider.value = PlayerSettingsManager.current.sfxVolume

        stage.addActor(overlay)
        stage.addActor(mainContainer)
        isVisible = true
        overlay.isVisible = true
        mainContainer.isVisible = true
        mainContainer.toFront()

        overlay.color.a = 0f
        overlay.addAction(Actions.fadeIn(0.6f, Interpolation.fade))

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

    // --- HELPER FUNCTIONS (matching VisualSettingsUI) ---

    private fun createNewspaperBackground(): TextureRegionDrawable {
        val pixmap = Pixmap(420, 520, Pixmap.Format.RGBA8888)
        val paperColor = Color.valueOf("#F5F1E8")
        pixmap.setColor(paperColor)
        pixmap.fill()

        val inkSpots = Color.valueOf("#E8E0D6")
        for (i in 0 until 350) {
            val x = Random.nextInt(420)
            val y = Random.nextInt(520)
            val size = Random.nextInt(2) + 1
            pixmap.setColor(inkSpots)
            pixmap.fillCircle(x, y, size)
        }

        val borderColor = Color.valueOf("#2C1810")
        pixmap.setColor(borderColor)

        for (i in 0 until 6) {
            pixmap.drawRectangle(i, i, 420 - i * 2, 520 - i * 2)
        }

        drawArtDecoCorners(pixmap, 420, 520)
        drawVintageElements(pixmap, 420, 520)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun drawArtDecoCorners(pixmap: Pixmap, width: Int, height: Int) {
        val accentColor = Color.valueOf("#8B6914")
        pixmap.setColor(accentColor)

        val cornerSize = 25

        for (i in 10 until cornerSize) {
            pixmap.drawLine(10, i, i, 10)
            pixmap.drawLine(12, i, i, 12)

            pixmap.drawLine(width - 10, i, width - i, 10)
            pixmap.drawLine(width - 12, i, width - i, 12)

            pixmap.drawLine(10, height - i, i, height - 10)
            pixmap.drawLine(12, height - i, i, height - 12)

            pixmap.drawLine(width - 10, height - i, width - i, height - 10)
            pixmap.drawLine(width - 12, height - i, width - i, height - 12)
        }
    }

    private fun drawVintageElements(pixmap: Pixmap, width: Int, height: Int) {
        val accentColor = Color.valueOf("#8B6914")
        pixmap.setColor(accentColor)

        for (y in 120 until height - 120 step 50) {
            pixmap.fillCircle(20, y, 4)
            pixmap.drawRectangle(16, y - 2, 8, 4)

            pixmap.fillCircle(width - 20, y, 4)
            pixmap.drawRectangle(width - 24, y - 2, 8, 4)
        }
    }

    private fun createVintageButton(text: String, bgColor: Color): TextButton {
        val button = TextButton(text, skin, "default")

        val buttonPixmap = Pixmap(320, 45, Pixmap.Format.RGBA8888)

        val baseColor = Color(bgColor.r * 0.8f, bgColor.g * 0.8f, bgColor.b * 0.8f, 1f)
        buttonPixmap.setColor(baseColor)
        buttonPixmap.fill()

        val textureColor = Color(bgColor.r * 0.6f, bgColor.g * 0.6f, bgColor.b * 0.6f, 0.7f)
        for (i in 0 until 90) {
            val x = Random.nextInt(320)
            val y = Random.nextInt(45)
            val size = Random.nextInt(3) + 1
            buttonPixmap.setColor(textureColor)
            buttonPixmap.fillCircle(x, y, size)
        }

        val borderColor = Color.valueOf("#1A0F0A")
        buttonPixmap.setColor(borderColor)

        buttonPixmap.drawRectangle(0, 0, 320, 45)
        buttonPixmap.drawRectangle(1, 1, 318, 43)
        buttonPixmap.drawRectangle(2, 2, 316, 41)

        val highlightColor = Color.valueOf("#CD7F32")
        buttonPixmap.setColor(highlightColor)
        buttonPixmap.drawLine(3, 3, 316, 3)
        buttonPixmap.drawLine(3, 3, 3, 41)

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
        button.label.color = Color.valueOf("#F5F1E8")

        return button
    }

    private fun createArtDecoDecoration(side: String): Image {
        val pixmap = Pixmap(45, 45, Pixmap.Format.RGBA8888)
        val decorColor = Color.valueOf("#8B6914")
        pixmap.setColor(decorColor)

        val center = 22

        for (i in 0 until 20) {
            val offset = i / 2
            pixmap.drawLine(center - offset, center - i/2, center + offset, center - i/2)
            if (i < 15) {
                pixmap.drawLine(center - offset, center + i/2, center + offset, center + i/2)
            }
        }

        pixmap.fillCircle(center, center, 6)
        pixmap.setColor(Color.valueOf("#CD7F32"))
        pixmap.fillCircle(center, center, 3)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return Image(texture)
    }

    private fun createBottomBanner(): Image {
        val pixmap = Pixmap(200, 30, Pixmap.Format.RGBA8888)
        val bannerColor = Color.valueOf("#2C1810")
        pixmap.setColor(bannerColor)

        pixmap.fill()

        for (i in 0 until 8) {
            pixmap.setColor(Color.CLEAR)
            pixmap.drawLine(i, 15 + i, i, 30)
            pixmap.drawLine(199 - i, 15 + i, 199 - i, 30)
        }

        pixmap.setColor(Color.valueOf("#CD7F32"))
        pixmap.drawRectangle(0, 0, 200, 30)
        pixmap.drawRectangle(1, 1, 198, 28)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return Image(texture)
    }
}
