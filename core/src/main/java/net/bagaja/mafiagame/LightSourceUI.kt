package net.bagaja.mafiagame

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
    private lateinit var colorPicker: ColorPickerWidget
    private lateinit var intensityLabel: Label
    private lateinit var rangeLabel: Label
    private lateinit var mainContainer: Table

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

        // Color picker
        val colorContainer = Table()
        colorContainer.add(Label("Color:", skin)).left().padBottom(8f).row()
        colorPicker = ColorPickerWidget(skin) { color ->
            nextColor.set(color)
        }
        colorContainer.add(colorPicker).size(320f, 140f)
        settingsContainer.add(colorContainer).fillX().padTop(10f).row()

        mainContainer.add(settingsContainer).fillX().padBottom(15f).row()

        // Set initial values
        intensitySlider.value = (nextIntensity / LightSource.MAX_INTENSITY) * 100f
        rangeSlider.value = ((nextRange - LightSource.MIN_RANGE) / (LightSource.MAX_RANGE - LightSource.MIN_RANGE)) * 100f
        intensityLabel.setText("${((nextIntensity / LightSource.MAX_INTENSITY) * 100f).toInt()}%")
        rangeLabel.setText("${(((nextRange - LightSource.MIN_RANGE) / (LightSource.MAX_RANGE - LightSource.MIN_RANGE)) * 100f).toInt()}%")
        colorPicker.setColor(nextColor)
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

        buttonContainer.add(resetButton).size(180f, 40f).pad(5f)
        mainContainer.add(buttonContainer).center()
    }

    private fun resetToDefaults() {
        nextIntensity = LightSource.DEFAULT_INTENSITY
        nextRange = LightSource.DEFAULT_RANGE
        nextColor.set(LightSource.DEFAULT_COLOR_R, LightSource.DEFAULT_COLOR_G, LightSource.DEFAULT_COLOR_B, 1f)

        intensitySlider.value = (nextIntensity / LightSource.MAX_INTENSITY) * 100f
        rangeSlider.value = ((nextRange - LightSource.MIN_RANGE) / (LightSource.MAX_RANGE - LightSource.MIN_RANGE)) * 100f
        intensityLabel.setText("${((nextIntensity / LightSource.MAX_INTENSITY) * 100f).toInt()}%")
        rangeLabel.setText("${(((nextRange - LightSource.MIN_RANGE) / (LightSource.MAX_RANGE - LightSource.MIN_RANGE)) * 100f).toInt()}%")
        colorPicker.setColor(nextColor)
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
    fun getCurrentSettings(): Triple<Float, Float, Color> {
        return Triple(nextIntensity, nextRange, Color(nextColor))
    }

    fun dispose() {
        // Any cleanup if needed
    }
}

/**
 * Modern color picker widget with enhanced styling
 */
class ColorPickerWidget(
    private val skin: Skin,
    private val onColorChanged: (Color) -> Unit
) : Table() {

    private val currentColor = Color(1f, 1f, 1f, 1f)
    private val colorDisplay = Image()
    private lateinit var redField: TextField
    private lateinit var greenField: TextField
    private lateinit var blueField: TextField

    // Flag to prevent recursive updates when setting color programmatically
    private var isUpdating = false

    init {
        setupColorPicker()
    }

    private fun setupColorPicker() {
        // Color display with modern styling
        updateColorDisplay()
        add(colorDisplay).size(80f, 40f).padBottom(12f).row()

        // RGB text field editors with improved layout
        val rgbTable = Table()

        // Create TextField editors
        createColorTextField(rgbTable, "R", Color.RED) { redField = it }
        createColorTextField(rgbTable, "G", Color.GREEN) { greenField = it }
        createColorTextField(rgbTable, "B", Color.BLUE) { blueField = it }

        add(rgbTable).fillX().expandX().padBottom(8f).row()

        // Enhanced preset colors
        val presetTable = Table()
        val presetColors = arrayOf(
            Color.WHITE to "White",
            Color(1f, 0.95f, 0.8f, 1f) to "Warm",
            Color.YELLOW to "Yellow",
            Color.ORANGE to "Orange",
            Color.RED to "Red",
            Color(0.3f, 0.7f, 1f, 1f) to "Blue",
            Color(0.7f, 0.3f, 1f, 1f) to "Purple",
            Color(0.3f, 1f, 0.5f, 1f) to "Green"
        )

        for ((color, name) in presetColors) {
            val button = TextButton(name, skin)
            button.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    setColor(color)
                    onColorChanged(currentColor)
                    animatePresetSelection(button)
                }
            })
            presetTable.add(button).size(50f, 28f).pad(1f)
            if (presetTable.children.size == 4) presetTable.row()
        }

        add(presetTable).padTop(5f)
    }

    /**
     * Creates a Label and a TextField for editing a color component (0-255).
     */
    private fun createColorTextField(
        parent: Table,
        label: String,
        color: Color,
        textFieldSetter: (TextField) -> Unit
    ) {
        val container = Table()

        val componentLabel = Label(label, skin)
        componentLabel.color = color
        componentLabel.setFontScale(1.0f)
        container.add(componentLabel).width(20f).padRight(10f)

        val textField = TextField("", skin)
        textField.maxLength = 3 // Max value is 255
        textField.setTextFieldFilter(TextField.TextFieldFilter.DigitsOnlyFilter())
        textField.alignment = Align.center

        // Listener for when the user changes the text and focus is lost
        textField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (!isUpdating) {
                    updateColorFromTextFields()
                }
            }
        })

        container.add(textField).width(60f).pad(0f, 10f, 0f, 10f)

        parent.add(container).fillX().padBottom(5f).row()
        textFieldSetter(textField) // Assign the created field to the class property
    }

    /**
     * Reads values from all three TextFields and updates the current color.
     */
    private fun updateColorFromTextFields() {
        val rText = redField.text
        val gText = greenField.text
        val bText = blueField.text

        // Read values, defaulting to 0 if empty or invalid. Coerce to 0-255 range.
        val r = rText.toIntOrNull()?.coerceIn(0, 255) ?: 0
        val g = gText.toIntOrNull()?.coerceIn(0, 255) ?: 0
        val b = bText.toIntOrNull()?.coerceIn(0, 255) ?: 0

        // If the user typed a value > 255, clamp it in the UI as well for clarity.
        if ((rText.toIntOrNull() ?: 0) > 255) redField.text = "255"
        if ((gText.toIntOrNull() ?: 0) > 255) greenField.text = "255"
        if ((bText.toIntOrNull() ?: 0) > 255) blueField.text = "255"

        currentColor.set(r / 255f, g / 255f, b / 255f, 1f)
        updateColorDisplay()
        onColorChanged(currentColor)
    }

    private fun updateColorDisplay() {
        val pixmap = Pixmap(80, 40, Pixmap.Format.RGBA8888)

        // Create gradient effect in color display
        for (x in 0 until 80) {
            for (y in 0 until 40) {
                val brightness = 0.8f + (y / 40f) * 0.2f
                pixmap.setColor(
                    currentColor.r * brightness,
                    currentColor.g * brightness,
                    currentColor.b * brightness,
                    1f
                )
                pixmap.drawPixel(x, y)
            }
        }

        // Add border
        pixmap.setColor(0.6f, 0.6f, 0.6f, 1f)
        pixmap.drawRectangle(0, 0, 80, 40)

        val texture = Texture(pixmap)
        pixmap.dispose()
        (colorDisplay.drawable as? TextureRegionDrawable)?.region?.texture?.dispose()
        colorDisplay.drawable = TextureRegionDrawable(TextureRegion(texture))
    }

    private fun animatePresetSelection(button: TextButton) {
        button.clearActions()
        button.addAction(
            Actions.sequence(
                Actions.scaleTo(1.1f, 1.1f, 0.1f, Interpolation.smooth),
                Actions.scaleTo(1.0f, 1.0f, 0.1f, Interpolation.smooth)
            )
        )
    }

    /**
     * Sets the color of the widget, now updating TextFields
     */
    override fun setColor(color: Color) {
        isUpdating = true // Prevent listeners from firing while we set values

        currentColor.set(color)

        // Update the TextFields with the new color values
        redField.text = (color.r * 255).toInt().toString()
        greenField.text = (color.g * 255).toInt().toString()
        blueField.text = (color.b * 255).toInt().toString()

        updateColorDisplay()

        isUpdating = false // Re-enable listeners
    }
}
