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
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align

/**
 * Modern UI for customizing light source properties with sleek design
 */
class LightSourceUI(
    private val skin: Skin,
    private val stage: Stage
) {
    private lateinit var lightPanel: Table
    private lateinit var intensitySlider: Slider
    private lateinit var rangeSlider: Slider
    private lateinit var colorPreviewButton: Button
    private lateinit var intensityLabel: Label
    private lateinit var rangeLabel: Label
    private lateinit var mainContainer: Table
    private var colorPickerDialog: ColorPickerDialog? = null

    private lateinit var rotationXSlider: Slider
    private lateinit var rotationYSlider: Slider
    private lateinit var rotationZSlider: Slider
    private lateinit var rotationXLabel: Label
    private lateinit var rotationYLabel: Label
    private lateinit var rotationZLabel: Label

    // Add rotation properties
    var nextRotationX = 0f
    var nextRotationY = 0f
    var nextRotationZ = 0f

    var nextIntensity = LightSource.DEFAULT_INTENSITY
    var nextRange = LightSource.DEFAULT_RANGE
    var nextColor = Color(LightSource.DEFAULT_COLOR_R, LightSource.DEFAULT_COLOR_G, LightSource.DEFAULT_COLOR_B, 1f)

    private var isVisible = false

    fun initialize() {
        createLightPanel()
    }

    private fun createLightPanel() {
        lightPanel = Table()
        lightPanel.setFillParent(true)
        lightPanel.top().right()
        lightPanel.pad(20f)
        lightPanel.isVisible = false

        mainContainer = Table()
        mainContainer.background = createModernBackground()
        mainContainer.pad(25f, 30f, 25f, 30f)

        // Title
        val titleLabel = Label("Light Settings", skin)
        titleLabel.setFontScale(1.6f)
        titleLabel.color = Color(0.9f, 0.9f, 1f, 1f)
        titleLabel.setAlignment(Align.center)
        mainContainer.add(titleLabel).padBottom(20f).fillX().row()

        // Instructions
        val instructionLabel = Label("Set properties for next light to be placed", skin)
        instructionLabel.setFontScale(0.9f)
        instructionLabel.color = Color(0.7f, 0.7f, 0.8f, 1f)
        instructionLabel.setAlignment(Align.center)
        mainContainer.add(instructionLabel).padBottom(20f).fillX().row()

        createLightSettingsSection()
        createActionButtons()

        lightPanel.add(mainContainer).width(380f)
        stage.addActor(lightPanel)
    }

    private fun createLightSettingsSection() {
        val settingsContainer = Table()
        settingsContainer.background = createSectionBackground(Color(0.25f, 0.2f, 0.35f, 0.9f))
        settingsContainer.pad(15f)

        // Intensity control
        createSliderControl(
            settingsContainer,
            "Intensity",
            { intensitySlider = it },
            { intensityLabel = it },
            0f, 100f
        ) { value ->
            nextIntensity = (value / 100f) * LightSource.MAX_INTENSITY
            intensityLabel.setText("${value.toInt()}%")
        }

        // Range control
        createSliderControl(
            settingsContainer,
            "Range",
            { rangeSlider = it },
            { rangeLabel = it },
            0f, 100f
        ) { value ->
            nextRange = LightSource.MIN_RANGE + (value / 100f) * (LightSource.MAX_RANGE - LightSource.MIN_RANGE)
            rangeLabel.setText("${value.toInt()}%")
        }

        // Add rotation controls
        createSliderControl(
            settingsContainer,
            "Rotation X",
            { rotationXSlider = it },
            { rotationXLabel = it },
            0f, 360f
        ) { value ->
            nextRotationX = value
            rotationXLabel.setText("${value.toInt()}°")
        }

        createSliderControl(
            settingsContainer,
            "Rotation Y",
            { rotationYSlider = it },
            { rotationYLabel = it },
            0f, 360f
        ) { value ->
            nextRotationY = value
            rotationYLabel.setText("${value.toInt()}°")
        }

        createSliderControl(
            settingsContainer,
            "Rotation Z",
            { rotationZSlider = it },
            { rotationZLabel = it },
            0f, 360f
        ) { value ->
            nextRotationZ = value
            rotationZLabel.setText("${value.toInt()}°")
        }

        // Color picker - now as clickable button
        val colorContainer = Table()
        colorContainer.add(Label("Color:", skin)).left().padBottom(8f).row()

        colorPreviewButton = Button(skin)
        colorPreviewButton.add(Label("Click to choose color", skin)).pad(10f)
        updateColorPreview()

        colorPreviewButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                openColorPickerDialog()
            }
        })

        colorContainer.add(colorPreviewButton).size(320f, 60f)
        settingsContainer.add(colorContainer).fillX().padTop(10f).row()

        mainContainer.add(settingsContainer).fillX().padBottom(15f).row()

        // Set initial values
        intensitySlider.value = (nextIntensity / LightSource.MAX_INTENSITY) * 100f
        rangeSlider.value = ((nextRange - LightSource.MIN_RANGE) / (LightSource.MAX_RANGE - LightSource.MIN_RANGE)) * 100f
        intensityLabel.setText("${((nextIntensity / LightSource.MAX_INTENSITY) * 100f).toInt()}%")
        rangeLabel.setText("${(((nextRange - LightSource.MIN_RANGE) / (LightSource.MAX_RANGE - LightSource.MIN_RANGE)) * 100f).toInt()}%")
        rotationXSlider.value = nextRotationX
        rotationYSlider.value = nextRotationY
        rotationZSlider.value = nextRotationZ
        rotationXLabel.setText("${nextRotationX.toInt()}°")
        rotationYLabel.setText("${nextRotationY.toInt()}°")
        rotationZLabel.setText("${nextRotationZ.toInt()}°")
    }

    private fun openColorPickerDialog() {
        if (colorPickerDialog != null) {
            colorPickerDialog?.remove()
        }

        colorPickerDialog = ColorPickerDialog(skin, nextColor) { selectedColor ->
            nextColor.set(selectedColor)
            updateColorPreview()
        }

        colorPickerDialog?.show(stage)
    }

    private fun updateColorPreview() {
        val pixmap = Pixmap(320, 60, Pixmap.Format.RGBA8888)

        // Create gradient effect in color preview
        for (x in 0 until 320) {
            for (y in 0 until 60) {
                val brightness = 0.8f + (y / 60f) * 0.2f
                pixmap.setColor(
                    nextColor.r * brightness,
                    nextColor.g * brightness,
                    nextColor.b * brightness,
                    1f
                )
                pixmap.drawPixel(x, y)
            }
        }

        // Add border
        pixmap.setColor(0.6f, 0.6f, 0.6f, 1f)
        pixmap.drawRectangle(0, 0, 320, 60)

        val texture = Texture(pixmap)
        pixmap.dispose()

        colorPreviewButton.style = Button.ButtonStyle(colorPreviewButton.style)
        colorPreviewButton.style.up = TextureRegionDrawable(TextureRegion(texture))
        colorPreviewButton.style.down = TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createActionButtons() {
        val buttonContainer = Table()
        buttonContainer.pad(10f)

        // Reset to defaults button
        val resetButton = TextButton("Reset to Defaults", skin)
        resetButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                resetToDefaults()
            }
        })

        // Close button to clear text field focus
        val closeButton = TextButton("Close", skin)
        closeButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                clearFocusAndHide()
            }
        })

        buttonContainer.add(resetButton).size(140f, 40f).pad(5f)
        buttonContainer.add(closeButton).size(100f, 40f).pad(5f)
        mainContainer.add(buttonContainer).center()
    }

    private fun resetToDefaults() {
        nextIntensity = LightSource.DEFAULT_INTENSITY
        nextRange = LightSource.DEFAULT_RANGE
        nextColor.set(LightSource.DEFAULT_COLOR_R, LightSource.DEFAULT_COLOR_G, LightSource.DEFAULT_COLOR_B, 1f)
        nextRotationX = 0f
        nextRotationY = 0f
        nextRotationZ = 0f

        intensitySlider.value = (nextIntensity / LightSource.MAX_INTENSITY) * 100f
        rangeSlider.value = ((nextRange - LightSource.MIN_RANGE) / (LightSource.MAX_RANGE - LightSource.MIN_RANGE)) * 100f

        // Update rotation sliders
        rotationXSlider.value = nextRotationX
        rotationYSlider.value = nextRotationY
        rotationZSlider.value = nextRotationZ

        // Update labels
        intensityLabel.setText("${((nextIntensity / LightSource.MAX_INTENSITY) * 100f).toInt()}%")
        rangeLabel.setText("${(((nextRange - LightSource.MIN_RANGE) / (LightSource.MAX_RANGE - LightSource.MIN_RANGE)) * 100f).toInt()}%")
        rotationXLabel.setText("${nextRotationX.toInt()}°")
        rotationYLabel.setText("${nextRotationY.toInt()}°")
        rotationZLabel.setText("${nextRotationZ.toInt()}°")

        updateColorPreview()
    }

    // Method to clear focus and hide UI
    private fun clearFocusAndHide() {
        stage.keyboardFocus = null
        hide()
    }

    private fun createSliderControl(
        parent: Table,
        labelText: String,
        sliderSetter: (Slider) -> Unit,
        labelSetter: (Label) -> Unit,
        min: Float, max: Float,
        onChange: (Float) -> Unit
    ) {
        val container = Table()

        val headerContainer = Table()
        headerContainer.add(Label("$labelText:", skin)).left().expandX()
        val valueLabel = Label("50%", skin)
        valueLabel.color = Color(0.8f, 0.9f, 1f, 1f)
        headerContainer.add(valueLabel).right()
        container.add(headerContainer).fillX().padBottom(5f).row()

        val slider = Slider(min, max, 1f, false, skin)
        slider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val changedSlider = actor as? Slider ?: return
                onChange(changedSlider.value)
            }
        })

        container.add(slider).fillX().padBottom(15f).row()
        parent.add(container).fillX().row()

        sliderSetter(slider)
        labelSetter(valueLabel)
    }

    private fun createModernBackground(): Drawable {
        val pixmap = Pixmap(100, 100, Pixmap.Format.RGBA8888)

        // Create gradient background
        for (y in 0 until 100) {
            val alpha = 0.88f + (y / 100f) * 0.1f
            val brightness = 0.08f + (y / 100f) * 0.05f
            pixmap.setColor(brightness, brightness + 0.02f, brightness + 0.08f, alpha)
            pixmap.drawLine(0, y, 99, y)
        }

        // Add subtle border
        pixmap.setColor(0.3f, 0.4f, 0.6f, 0.6f)
        pixmap.drawRectangle(0, 0, 100, 100)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createSectionBackground(baseColor: Color): Drawable {
        val pixmap = Pixmap(100, 60, Pixmap.Format.RGBA8888)

        // Gradient effect for sections
        for (y in 0 until 60) {
            val factor = y / 60f
            val r = baseColor.r + factor * 0.05f
            val g = baseColor.g + factor * 0.05f
            val b = baseColor.b + factor * 0.05f
            pixmap.setColor(r, g, b, baseColor.a)
            pixmap.drawLine(0, y, 99, y)
        }

        // Subtle border
        pixmap.setColor(baseColor.r + 0.1f, baseColor.g + 0.1f, baseColor.b + 0.1f, 0.8f)
        pixmap.drawRectangle(0, 0, 100, 60)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    fun show() {
        isVisible = true
        lightPanel.isVisible = true

        mainContainer.clearActions()
        mainContainer.setScale(0.8f)
        mainContainer.addAction(
            Actions.scaleTo(1.0f, 1.0f, 0.3f, Interpolation.bounceOut)
        )
    }

    fun hide() {
        isVisible = false
        // Clear any text field focus before hiding
        stage.keyboardFocus = null
        mainContainer.clearActions()
        mainContainer.addAction(
            Actions.sequence(
                Actions.scaleTo(0.8f, 0.8f, 0.2f, Interpolation.smooth),
                Actions.run { lightPanel.isVisible = false }
            )
        )
    }

    fun toggle() {
        if (isVisible) hide() else show()
    }

    fun isVisible(): Boolean = isVisible

    // Get current settings for placing new light
    fun getCurrentSettings(): Tuple6<Float, Float, Color, Float, Float, Float> {
        return Tuple6(nextIntensity, nextRange, Color(nextColor), nextRotationX, nextRotationY, nextRotationZ)
    }

    fun dispose() {
        colorPickerDialog?.remove()
    }
}

data class Tuple6<A, B, C, D, E, F>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E, val sixth: F)

/**
 * Color picker dialog with HSV color wheel and RGB sliders
 */
class ColorPickerDialog(
    private val skin: Skin,
    initialColor: Color,
    private val onColorSelected: (Color) -> Unit
) : Dialog("Choose Color", skin) {

    private val selectedColor = Color(initialColor)
    private val colorPreview = Image()
    private val rgbSliders = mutableListOf<Slider>()
    private val rgbLabels = mutableListOf<Label>()
    private var isUpdating = false

    init {
        setupDialog()
    }

    private fun setupDialog() {
        // Color preview
        updateColorPreview()
        contentTable.add(colorPreview).size(200f, 60f).padBottom(20f).row()

        // RGB Sliders
        createRGBSliders()

        // Preset colors
        createColorPresets()

        // Buttons
        buttonTable.add(TextButton("Cancel", skin).apply {
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    hide()
                    remove()
                }
            })
        }).size(80f, 40f).pad(5f)

        buttonTable.add(TextButton("OK", skin).apply {
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    onColorSelected(Color(selectedColor))
                    hide()
                    remove()
                }
            })
        }).size(80f, 40f).pad(5f)

        pack()
    }

    private fun createRGBSliders() {
        val sliderTable = Table()

        val colors = arrayOf(Color.RED, Color.GREEN, Color.BLUE)
        val names = arrayOf("Red", "Green", "Blue")
        val values = arrayOf(selectedColor.r, selectedColor.g, selectedColor.b)

        for (i in 0..2) {
            val container = Table()

            val label = Label("${names[i]}:", skin)
            label.color = colors[i]
            container.add(label).width(50f).left()

            val valueLabel = Label("${(values[i] * 255).toInt()}", skin)
            rgbLabels.add(valueLabel)
            container.add(valueLabel).width(40f).right()

            sliderTable.add(container).fillX().padBottom(5f).row()

            val slider = Slider(0f, 255f, 1f, false, skin)
            slider.value = values[i] * 255f
            slider.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    if (!isUpdating) {
                        updateColorFromSliders()
                    }
                }
            })
            rgbSliders.add(slider)
            sliderTable.add(slider).fillX().padBottom(10f).row()
        }

        contentTable.add(sliderTable).fillX().padBottom(15f).row()
    }

    private fun createColorPresets() {
        val presetTable = Table()
        val presets = arrayOf(
            Color.WHITE, Color.LIGHT_GRAY, Color.GRAY, Color.DARK_GRAY,
            Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW,
            Color.ORANGE, Color.PURPLE, Color.CYAN, Color.MAGENTA
        )

        for ((index, color) in presets.withIndex()) {
            val button = Button(skin)
            val colorPixmap = Pixmap(30, 30, Pixmap.Format.RGBA8888)
            colorPixmap.setColor(color)
            colorPixmap.fill()
            val colorTexture = Texture(colorPixmap)
            colorPixmap.dispose()

            button.style = Button.ButtonStyle(button.style)
            button.style.up = TextureRegionDrawable(TextureRegion(colorTexture))

            button.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    setColor(color)
                }
            })

            presetTable.add(button).size(30f, 30f).pad(2f)
            if ((index + 1) % 4 == 0) presetTable.row()
        }

        contentTable.add(Label("Presets:", skin)).left().padBottom(5f).row()
        contentTable.add(presetTable).padBottom(15f).row()
    }

    private fun updateColorFromSliders() {
        isUpdating = true

        val r = rgbSliders[0].value / 255f
        val g = rgbSliders[1].value / 255f
        val b = rgbSliders[2].value / 255f

        selectedColor.set(r, g, b, 1f)

        rgbLabels[0].setText("${rgbSliders[0].value.toInt()}")
        rgbLabels[1].setText("${rgbSliders[1].value.toInt()}")
        rgbLabels[2].setText("${rgbSliders[2].value.toInt()}")

        updateColorPreview()

        isUpdating = false
    }

    override fun setColor(color: Color) {
        isUpdating = true

        selectedColor.set(color)

        rgbSliders[0].value = color.r * 255f
        rgbSliders[1].value = color.g * 255f
        rgbSliders[2].value = color.b * 255f

        rgbLabels[0].setText("${(color.r * 255).toInt()}")
        rgbLabels[1].setText("${(color.g * 255).toInt()}")
        rgbLabels[2].setText("${(color.b * 255).toInt()}")

        updateColorPreview()

        isUpdating = false
    }

    private fun updateColorPreview() {
        val pixmap = Pixmap(200, 60, Pixmap.Format.RGBA8888)
        pixmap.setColor(selectedColor)
        pixmap.fill()

        // Add border
        pixmap.setColor(0.6f, 0.6f, 0.6f, 1f)
        pixmap.drawRectangle(0, 0, 200, 60)

        val texture = Texture(pixmap)
        pixmap.dispose()

        colorPreview.drawable = TextureRegionDrawable(TextureRegion(texture))
    }
}
