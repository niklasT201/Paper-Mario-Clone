package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable

/**
 * A UI panel for customizing the sky colors and day/night cycle in real-time.
 */
class SkyCustomizationUI(
    private val skin: Skin,
    private val stage: Stage,
    private val lightingManager: LightingManager // To send updates and get data
) {
    private lateinit var mainPanel: Table
    private var colorPickerDialog: ColorPickerDialog? = null
    private var isVisible = false

    // UI elements for cycle control
    private lateinit var timeSlider: Slider
    private lateinit var timeLabel: Label

    fun initialize() {
        val palettes = lightingManager.getSkyPalettes()
        createMainPanel(palettes)
    }

    private fun createMainPanel(palettes: Map<DayNightCycle.TimeOfDay, SkySystem.SkyColors>) {
        mainPanel = Table()
        mainPanel.setFillParent(true)
        mainPanel.top().right()
        mainPanel.pad(20f)
        mainPanel.isVisible = false

        val container = Table(skin)
        container.background = skin.getDrawable("default-window")
        container.pad(15f)

        container.add(Label("Sky & Time Controls", skin, "title")).colspan(2).padBottom(15f).row()

        // --- Day/Night Cycle Control Section ---
        createCycleControlSection(container)

        // --- Sky Color Customization Section ---
        val colorTable = Table() // Create the table first, so its type is known
        val scrollPane = ScrollPane(colorTable, skin) // Then, put it inside the scroll pane
        scrollPane.setScrollingDisabled(true, false) // Disable horizontal scrolling

        // Now the loop works perfectly because colorTable is a Table
        for (timeOfDay in DayNightCycle.TimeOfDay.entries) {
            val palette = palettes[timeOfDay] ?: continue
            createTimeOfDaySection(colorTable, timeOfDay, palette)
        }
        container.add(scrollPane).colspan(2).growX().height(300f).padBottom(10f).row()


        // --- Close Button ---
        val closeButton = TextButton("Close", skin)
        closeButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                hide()
            }
        })
        container.add(closeButton).colspan(2).padTop(10f)

        mainPanel.add(container)
        stage.addActor(mainPanel)
    }

    private fun createCycleControlSection(parent: Table) {
        val cycleTable = Table()
        cycleTable.add(Label("Day/Night Cycle", skin)).colspan(3).padBottom(10f).row()

        timeLabel = Label("00:00", skin)
        cycleTable.add(timeLabel).left()

        timeSlider = Slider(0f, 1f, 0.001f, false, skin)
        timeSlider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                lightingManager.getDayNightCycle().setDayProgress(timeSlider.value)
            }
        })
        cycleTable.add(timeSlider).growX().padLeft(10f).padRight(10f)
        parent.add(cycleTable).colspan(2).fillX().padBottom(20f).row()
    }


    private fun createTimeOfDaySection(parent: Table, timeOfDay: DayNightCycle.TimeOfDay, colors: SkySystem.SkyColors) {
        val header = Label(timeOfDay.name, skin, "default-font", Color.YELLOW)
        parent.add(header).colspan(2).padTop(15f).padBottom(5f).row()

        // Top Color
        parent.add(Label("Top:", skin)).left().padRight(10f)
        val topButton = createColorButton(colors.topColor) { newColor ->
            lightingManager.updateSkyPaletteColor(timeOfDay, SkySystem.SkyColorType.TOP, newColor)
        }
        parent.add(topButton).size(150f, 25f).right().row()

        // Horizon Color
        parent.add(Label("Horizon:", skin)).left()
        val horizonButton = createColorButton(colors.horizonColor) { newColor ->
            lightingManager.updateSkyPaletteColor(timeOfDay, SkySystem.SkyColorType.HORIZON, newColor)
        }
        parent.add(horizonButton).size(150f, 25f).right().row()

        // Bottom Color
        parent.add(Label("Bottom:", skin)).left()
        val bottomButton = createColorButton(colors.bottomColor) { newColor ->
            lightingManager.updateSkyPaletteColor(timeOfDay, SkySystem.SkyColorType.BOTTOM, newColor)
        }
        parent.add(bottomButton).size(150f, 25f).right().padBottom(10f).row()
    }

    private fun createColorButton(initialColor: Color, onColorChanged: (Color) -> Unit): Button {
        val button = Button(skin)
        val currentColor = Color(initialColor)

        fun updateButtonBackground() {
            val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
            pixmap.setColor(currentColor)
            pixmap.fill()
            val drawable = TextureRegionDrawable(Texture(pixmap))
            pixmap.dispose()
            // Make a new style instance to avoid modifying the skin's shared style
            val newStyle = Button.ButtonStyle(button.style)
            newStyle.up = drawable
            button.style = newStyle
        }

        updateButtonBackground()

        button.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                openColorPickerDialog(currentColor) { newColor ->
                    currentColor.set(newColor)
                    updateButtonBackground()
                    onColorChanged(newColor)
                }
            }
        })
        return button
    }

    private fun openColorPickerDialog(initialColor: Color, onColorSelected: (Color) -> Unit) {
        colorPickerDialog?.remove()
        colorPickerDialog = ColorPickerDialog(skin, initialColor, onColorSelected)
        colorPickerDialog?.show(stage)
    }

    /**
     * Call this every frame to update the UI with the current game time.
     */
    fun update() {
        if (!isVisible) return

        val cycle = lightingManager.getDayNightCycle()
        timeLabel.setText(cycle.getTimeString())
        // Update slider only if the user isn't currently dragging it
        if (!timeSlider.isDragging) {
            timeSlider.value = cycle.getDayProgress()
        }
    }

    fun show() {
        if (isVisible) return
        isVisible = true
        mainPanel.isVisible = true
        mainPanel.toFront()
    }

    fun hide() {
        if (!isVisible) return
        isVisible = false
        mainPanel.isVisible = false
    }

    fun toggle() {
        if (isVisible) hide() else show()
    }

    fun isVisible(): Boolean = isVisible

    fun dispose() {
        colorPickerDialog?.remove()
    }
}
