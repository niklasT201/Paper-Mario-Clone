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

    // UI Elements
    lateinit var backButton: TextButton
    private lateinit var masterVolumeSlider: Slider
    private lateinit var musicVolumeSlider: Slider
    private lateinit var sfxVolumeSlider: Slider

    // New Checkboxes for categories
    private val categoryCheckboxes = mutableMapOf<SoundCategory, CheckBox>()

    init {
        initialize()
        setupListeners()
        applyAllAudioSettings() // Apply settings once on initialization
    }

    /**
     * Applies all audio settings from PlayerSettingsManager.
     * Crucially, this should be called once when the game starts.
     */
    fun applyAllAudioSettings() {
        val settings = PlayerSettingsManager.current

        // Apply master volume
        musicManager.setMasterVolume(settings.masterVolume)
        soundManager.setMasterVolume(settings.masterVolume)

        // Apply individual volumes (or mute if category is disabled)
        musicManager.setMusicVolume(if (settings.playMusic) settings.musicVolume else 0f)
        soundManager.setSfxVolume(settings.sfxVolume)

        // Apply category mutes
        for (category in SoundCategory.entries) {
            val isEnabled = when (category) {
                SoundCategory.MUSIC -> settings.playMusic
                SoundCategory.AMBIENCE -> settings.playAmbience
                SoundCategory.WEATHER -> settings.playWeather
                SoundCategory.WEAPONS -> settings.playWeapons
                SoundCategory.VEHICLES -> settings.playVehicles
                SoundCategory.CHARACTER -> settings.playCharacter
                SoundCategory.UI_GENERAL -> settings.playUiGeneral
            }
            val ids = SoundCategoryManager.getSoundIdsForCategory(category)
            if (isEnabled) {
                soundManager.unmuteCategory(ids)
            } else {
                soundManager.muteCategory(ids)
            }
        }
    }

    private fun initialize() {
        createSmokyOverlay()
        createNewspaperSettings()
    }

    private fun createNewspaperSettings() {
        mainContainer = Table()
        mainContainer.setFillParent(true)
        mainContainer.center()
        mainContainer.isVisible = false

        val newspaperTable = Table()
        newspaperTable.background = createNewspaperBackground()
        newspaperTable.pad(50f, 60f, 40f, 60f)

        val headerTable = Table()
        headerTable.add(createArtDecoDecoration("left")).padRight(25f)
        val titleLabel = Label("⦿ AUDIO SETTINGS ⦿", skin, "title")
        titleLabel.setAlignment(Align.center)
        titleLabel.color = Color.valueOf("#1A0F0A")
        headerTable.add(titleLabel)
        headerTable.add(createArtDecoDecoration("right")).padLeft(25f)
        newspaperTable.add(headerTable).padBottom(35f).row()

        val subtitleLabel = Label("~ Tune Your Listening Experience ~", skin, "default")
        subtitleLabel.setAlignment(Align.center)
        subtitleLabel.color = Color.valueOf("#3D2817")
        newspaperTable.add(subtitleLabel).padBottom(30f).row()

        val scrollContentTable = Table()
        scrollContentTable.pad(25f)

        // --- VOLUME CONTROLS ---
        scrollContentTable.add(Label("═══ VOLUME CONTROLS ═══", skin, "default").apply {
            setAlignment(Align.center); color = Color.valueOf("#2C1810")
        }).padBottom(20f).row()

        val slidersTable = Table()
        slidersTable.defaults().left().padBottom(15f)
        masterVolumeSlider = Slider(0f, 1f, 0.01f, false, skin)
        musicVolumeSlider = Slider(0f, 1f, 0.01f, false, skin)
        sfxVolumeSlider = Slider(0f, 1f, 0.01f, false, skin)
        val labelColor = Color.valueOf("#3D2817")

        slidersTable.add(Label("Master Volume", skin, "default").also { it.color = labelColor }).left().row()
        slidersTable.add(masterVolumeSlider).width(320f).padBottom(20f).row()

        slidersTable.add(Label("Music Volume", skin, "default").also { it.color = labelColor }).left().row()
        slidersTable.add(musicVolumeSlider).width(320f).padBottom(20f).row()

        slidersTable.add(Label("Sound FX Volume", skin, "default").also { it.color = labelColor }).left().row()
        slidersTable.add(sfxVolumeSlider).width(320f).row()
        scrollContentTable.add(slidersTable).fillX().padBottom(30f).row()

        // --- CATEGORY TOGGLES ---
        scrollContentTable.add(Label("═══ CATEGORY TOGGLES ═══", skin, "default").apply {
            setAlignment(Align.center); color = Color.valueOf("#2C1810")
        }).padBottom(20f).row()

        // Create checkbox table with vertical layout (one per row, left-aligned)
        val categoriesTable = Table()
        categoriesTable.defaults().left().padBottom(10f)

        // Add all checkboxes vertically, one per row, left-aligned
        for (category in SoundCategory.entries) {
            val checkbox = createVintageCheckbox(" ${category.displayName}")
            categoryCheckboxes[category] = checkbox
            categoriesTable.add(checkbox).left().row()
        }

        scrollContentTable.add(categoriesTable).left().padLeft(20f).row()

        val scrollPane = ScrollPane(scrollContentTable, skin, "vintage-newspaper")
        scrollPane.setScrollingDisabled(true, false)
        scrollPane.fadeScrollBars = false
        newspaperTable.add(scrollPane).width(420f).height(450f).padBottom(25f).row()

        backButton = createVintageButton("⬅ RETURN TO PAUSE MENU ⬅", Color.valueOf("#8B0000"))
        newspaperTable.add(backButton).width(320f).height(50f).row()

        newspaperTable.add(createBottomBanner()).padTop(20f)
        mainContainer.add(newspaperTable)
    }

    private fun setupListeners() {
        backButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                uiManager.returnToPauseMenu()
            }
        })

        val sliderListener = object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                when (actor) {
                    masterVolumeSlider -> {
                        PlayerSettingsManager.current.masterVolume = masterVolumeSlider.value
                    }
                    musicVolumeSlider -> {
                        PlayerSettingsManager.current.musicVolume = musicVolumeSlider.value
                    }
                    sfxVolumeSlider -> {
                        PlayerSettingsManager.current.sfxVolume = sfxVolumeSlider.value
                    }
                }
                applyAllAudioSettings() // Re-apply all settings whenever a slider moves
            }
        }

        // Save settings when the user releases the slider
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

        // Add listeners for the new category checkboxes
        for ((category, checkbox) in categoryCheckboxes) {
            checkbox.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    when (category) {
                        SoundCategory.MUSIC -> PlayerSettingsManager.current.playMusic = checkbox.isChecked
                        SoundCategory.AMBIENCE -> PlayerSettingsManager.current.playAmbience = checkbox.isChecked
                        SoundCategory.WEATHER -> PlayerSettingsManager.current.playWeather = checkbox.isChecked
                        SoundCategory.WEAPONS -> PlayerSettingsManager.current.playWeapons = checkbox.isChecked
                        SoundCategory.VEHICLES -> PlayerSettingsManager.current.playVehicles = checkbox.isChecked
                        SoundCategory.CHARACTER -> PlayerSettingsManager.current.playCharacter = checkbox.isChecked
                        SoundCategory.UI_GENERAL -> PlayerSettingsManager.current.playUiGeneral = checkbox.isChecked
                    }
                    applyAllAudioSettings() // Re-apply settings to mute/unmute
                    PlayerSettingsManager.save()
                }
            })
        }
    }

    fun show(stage: Stage) {
        val settings = PlayerSettingsManager.current
        masterVolumeSlider.value = settings.masterVolume
        musicVolumeSlider.value = settings.musicVolume
        sfxVolumeSlider.value = settings.sfxVolume

        categoryCheckboxes[SoundCategory.MUSIC]?.isChecked = settings.playMusic
        categoryCheckboxes[SoundCategory.AMBIENCE]?.isChecked = settings.playAmbience
        categoryCheckboxes[SoundCategory.WEATHER]?.isChecked = settings.playWeather
        categoryCheckboxes[SoundCategory.WEAPONS]?.isChecked = settings.playWeapons
        categoryCheckboxes[SoundCategory.VEHICLES]?.isChecked = settings.playVehicles
        categoryCheckboxes[SoundCategory.CHARACTER]?.isChecked = settings.playCharacter
        categoryCheckboxes[SoundCategory.UI_GENERAL]?.isChecked = settings.playUiGeneral

        stage.addActor(overlay)
        stage.addActor(mainContainer)
        isVisible = true
        overlay.isVisible = true
        mainContainer.isVisible = true
        mainContainer.toFront()

        overlay.color.a = 0f
        overlay.addAction(Actions.fadeIn(0.6f, Interpolation.fade))

        mainContainer.scaleX = 0.1f; mainContainer.scaleY = 0.8f; mainContainer.color.a = 0f
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

    private fun createNewspaperBackground(): TextureRegionDrawable {
        val pixmap = Pixmap(420, 600, Pixmap.Format.RGBA8888)
        val paperColor = Color.valueOf("#F5F1E8")
        pixmap.setColor(paperColor)
        pixmap.fill()

        val inkSpots = Color.valueOf("#E8E0D6")
        for (i in 0 until 400) {
            pixmap.setColor(inkSpots)
            pixmap.fillCircle(Random.nextInt(420), Random.nextInt(600), Random.nextInt(2) + 1)
        }

        val borderColor = Color.valueOf("#2C1810")
        pixmap.setColor(borderColor)
        for (i in 0 until 6) {
            pixmap.drawRectangle(i, i, 420 - i * 2, 600 - i * 2)
        }

        drawArtDecoCorners(pixmap, 420, 600)
        drawVintageElements(pixmap, 420, 600)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun drawArtDecoCorners(pixmap: Pixmap, width: Int, height: Int) {
        val accentColor = Color.valueOf("#8B6914")
        pixmap.setColor(accentColor)
        val cornerSize = 25
        for (i in 10 until cornerSize) {
            pixmap.drawLine(10, i, i, 10); pixmap.drawLine(12, i, i, 12)
            pixmap.drawLine(width - 10, i, width - i, 10); pixmap.drawLine(width - 12, i, width - i, 12)
            pixmap.drawLine(10, height - i, i, height - 10); pixmap.drawLine(12, height - i, i, height - 12)
            pixmap.drawLine(width - 10, height - i, width - i, height - 10); pixmap.drawLine(width - 12, height - i, width - i, height - 12)
        }
    }

    private fun drawVintageElements(pixmap: Pixmap, width: Int, height: Int) {
        val accentColor = Color.valueOf("#8B6914")
        pixmap.setColor(accentColor)
        for (y in 120 until height - 120 step 50) {
            pixmap.fillCircle(20, y, 4); pixmap.drawRectangle(16, y - 2, 8, 4)
            pixmap.fillCircle(width - 20, y, 4); pixmap.drawRectangle(width - 24, y - 2, 8, 4)
        }
    }

    private fun createVintageButton(text: String, bgColor: Color): TextButton {
        val button = TextButton(text, skin, "default")
        val buttonPixmap = Pixmap(320, 45, Pixmap.Format.RGBA8888)
        val baseColor = Color(bgColor.r * 0.8f, bgColor.g * 0.8f, bgColor.b * 0.8f, 1f)
        buttonPixmap.setColor(baseColor); buttonPixmap.fill()
        val textureColor = Color(bgColor.r * 0.6f, bgColor.g * 0.6f, bgColor.b * 0.6f, 0.7f)
        for (i in 0 until 90) {
            buttonPixmap.setColor(textureColor)
            buttonPixmap.fillCircle(Random.nextInt(320), Random.nextInt(45), Random.nextInt(3) + 1)
        }
        val borderColor = Color.valueOf("#1A0F0A")
        buttonPixmap.setColor(borderColor)
        buttonPixmap.drawRectangle(0, 0, 320, 45); buttonPixmap.drawRectangle(1, 1, 318, 43); buttonPixmap.drawRectangle(2, 2, 316, 41)
        val highlightColor = Color.valueOf("#CD7F32")
        buttonPixmap.setColor(highlightColor); buttonPixmap.drawLine(3, 3, 316, 3); buttonPixmap.drawLine(3, 3, 3, 41)
        for (i in 0 until 8) {
            buttonPixmap.drawPixel(5 + i, 5); buttonPixmap.drawPixel(5, 5 + i)
            buttonPixmap.drawPixel(314 - i, 5); buttonPixmap.drawPixel(314, 5 + i)
            buttonPixmap.drawPixel(5 + i, 39); buttonPixmap.drawPixel(5, 39 - i)
            buttonPixmap.drawPixel(314 - i, 39); buttonPixmap.drawPixel(314, 39 - i)
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
            if (i < 15) pixmap.drawLine(center - offset, center + i/2, center + offset, center + i/2)
        }
        pixmap.fillCircle(center, center, 6)
        pixmap.setColor(Color.valueOf("#CD7F32")); pixmap.fillCircle(center, center, 3)
        val texture = Texture(pixmap)
        pixmap.dispose()
        return Image(texture)
    }

    private fun createBottomBanner(): Image {
        val pixmap = Pixmap(200, 30, Pixmap.Format.RGBA8888)
        val bannerColor = Color.valueOf("#2C1810")
        pixmap.setColor(bannerColor); pixmap.fill()
        for (i in 0 until 8) {
            pixmap.setColor(Color.CLEAR)
            pixmap.drawLine(i, 15 + i, i, 30); pixmap.drawLine(199 - i, 15 + i, 199 - i, 30)
        }
        pixmap.setColor(Color.valueOf("#CD7F32"))
        pixmap.drawRectangle(0, 0, 200, 30); pixmap.drawRectangle(1, 1, 198, 28)
        val texture = Texture(pixmap)
        pixmap.dispose()
        return Image(texture)
    }
}
