package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
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
import com.badlogic.gdx.utils.Align

class AnimationPreviewUI(
    private val skin: Skin,
    private val stage: Stage,
    private val uiManager: UIManager,
    private val playerSystem: PlayerSystem // We need this to access the player's animation system
) {
    private val window = Dialog("Animation Frame Editor", skin, "dialog")
    private var isVisible = false

    // --- UI Elements ---
    private val animationSelectBox: SelectBox<String>
    private val frameList: com.badlogic.gdx.scenes.scene2d.ui.List<String>
    private val previewImage: Image
    private val previewContainer: Container<Image>
    private val offsetXSlider: Slider
    private val offsetXField: TextField
    private val generateCodeButton: TextButton

    // --- Data ---
    private var currentAnimation: Animation? = null
    private var currentFrames = com.badlogic.gdx.utils.Array<AnimationFrame>()
    private var selectedFrameIndex = -1

    // This constant determines the visual size of the preview. Tweak if needed.
    companion object {
        const val PREVIEW_BASE_HEIGHT = 250f
    }

    init {
        window.isMovable = true
        window.setSize(500f, 600f)
        window.setPosition(stage.width - window.width - 20f, stage.height / 2f, Align.center)

        val content = window.contentTable
        content.pad(10f).defaults().pad(5f).align(Align.left)

        // --- Animation Selection ---
        animationSelectBox = SelectBox(skin)
        content.add(Label("Animation:", skin)).padRight(10f)
        content.add(animationSelectBox).growX().row()

        // --- Frame List ---
        frameList = List(skin)
        val frameScrollPane = ScrollPane(frameList, skin)
        frameScrollPane.setFadeScrollBars(false)

        // --- Preview Area ---
        previewImage = Image()
        previewContainer = Container(previewImage).size(PREVIEW_BASE_HEIGHT, PREVIEW_BASE_HEIGHT)
        // Add a background to see the bounds and a centerline for alignment
        val bgPixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888).apply { setColor(0.1f, 0.1f, 0.15f, 1f); fill() }
        previewContainer.background = TextureRegionDrawable(Texture(bgPixmap))
        bgPixmap.dispose()

        val centerlinePixmap = Pixmap(1, PREVIEW_BASE_HEIGHT.toInt(), Pixmap.Format.RGBA8888).apply { setColor(Color.RED); fill() }
        val centerlineImage = Image(Texture(centerlinePixmap))
        centerlinePixmap.dispose()

        val previewStack = Stack()
        previewStack.add(previewContainer)
        previewStack.add(centerlineImage) // The red line is drawn on top

        val previewTable = Table()
        previewTable.add(Label("Frame Preview (Red line is center)", skin, "small")).row()
        previewTable.add(previewStack).grow().padTop(5f).row()

        // --- Split Pane Layout ---
        val splitPane = SplitPane(frameScrollPane, previewTable, false, skin)
        splitPane.splitAmount = 0.3f
        content.add(splitPane).grow().colspan(2).padTop(10f).row()

        // --- Controls ---
        val controlsTable = Table()
        offsetXSlider = Slider(-5f, 5f, 0.01f, false, skin)
        offsetXField = TextField("0.0", skin)
        controlsTable.add(Label("X-Offset:", skin)).padRight(10f)
        controlsTable.add(offsetXSlider).growX()
        controlsTable.add(offsetXField).width(80f).padLeft(10f)
        content.add(controlsTable).growX().colspan(2).padTop(10f).row()

        generateCodeButton = TextButton("Generate Code", skin)
        content.add(generateCodeButton).growX().colspan(2).padTop(15f).row()

        window.button("Close").addListener(object: ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                hide()
            }
        })

        setupListeners()
    }

    private fun setupListeners() {
        // When an animation is selected from the dropdown
        animationSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                loadAnimation(animationSelectBox.selected)
            }
        })

        // When a frame is selected from the list
        frameList.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                selectFrame(frameList.selectedIndex)
            }
        })

        // When the slider is moved
        offsetXSlider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                updateOffset(offsetXSlider.value, true)
            }
        })

        // When text is entered in the field
        offsetXField.addListener(object: ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                updateOffset(offsetXField.text.toFloatOrNull() ?: 0f, false)
            }
        })

        // When "Generate Code" is clicked
        generateCodeButton.addListener(object: ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                generateAndShowCode()
            }
        })
    }

    private fun loadAnimation(animationName: String) {
        currentAnimation = playerSystem.animationSystem.currentAnimation // A bit of a hack, need to get all animations
        // In a more robust system, you'd get the animation by name from the AnimationSystem
        if (currentAnimation?.name != animationName) {
            // For now, we assume the user has to trigger the animation in-game first to edit it
            // Let's refine this to get all animations
            // TODO: Refactor AnimationSystem to expose all animations
        }

        // For now, let's just get the one we want to test:
        if (animationName == "attack_baseball_bat") {
            playerSystem.animationSystem.playAnimation("attack_baseball_bat", true)
            currentAnimation = playerSystem.animationSystem.currentAnimation
        } else if (animationName == "attack_knife") {
            playerSystem.animationSystem.playAnimation("attack_knife", true)
            currentAnimation = playerSystem.animationSystem.currentAnimation
        } else {
            currentAnimation = null
        }

        currentFrames.clear()
        val frameNames = com.badlogic.gdx.utils.Array<String>()
        selectedFrameIndex = -1

        currentAnimation?.frames?.forEachIndexed { index, frame ->
            currentFrames.add(AnimationFrame(frame.texture, frame.duration, frame.offsetX)) // Make a mutable copy
            frameNames.add("Frame ${index + 1}")
        }

        frameList.setItems(frameNames)
        if (frameNames.size > 0) {
            frameList.selectedIndex = 0
            selectFrame(0)
        } else {
            clearPreview()
        }
    }

    private fun selectFrame(index: Int) {
        if (index < 0 || index >= currentFrames.size) {
            clearPreview()
            return
        }
        selectedFrameIndex = index
        val frame = currentFrames[index]

        // Update the preview image
        previewImage.drawable = TextureRegionDrawable(frame.texture)

        // --- Calculate and set the preview size to match in-game aspect ratio ---
        val baseTex = playerSystem.animationSystem.getCurrentTexture() // A bit of a hack
        if(baseTex != null) {
            val baseAspect = playerSystem.playerSize.x / playerSystem.playerSize.y
            val currentAspect = frame.texture.width.toFloat() / frame.texture.height.toFloat()
            val scaleX = currentAspect / baseAspect
            val previewWidth = PREVIEW_BASE_HEIGHT * scaleX

            previewContainer.minWidth(previewWidth)
            previewContainer.minHeight(PREVIEW_BASE_HEIGHT)
            previewContainer.pack()
        }

        // Update the controls with the frame's current offset
        offsetXSlider.value = frame.offsetX
        offsetXField.text = String.format("%.3f", frame.offsetX)

        // Apply the offset to the preview image's position
        previewImage.x = -frame.offsetX * (PREVIEW_BASE_HEIGHT / playerSystem.playerSize.y)
    }

    private fun updateOffset(newOffset: Float, fromSlider: Boolean) {
        if (selectedFrameIndex < 0 || selectedFrameIndex >= currentFrames.size) return

        // Update the data in our temporary copy
        val frame = currentFrames[selectedFrameIndex]
        currentFrames[selectedFrameIndex] = frame.copy(offsetX = newOffset)

        // Update the other control
        if (fromSlider) {
            offsetXField.text = String.format("%.3f", newOffset)
        } else {
            offsetXSlider.value = newOffset
        }

        // Update the preview image position in real-time
        // We need to scale the world offset to match the UI preview size
        val scaleFactor = PREVIEW_BASE_HEIGHT / playerSystem.playerSize.y
        previewImage.x = -newOffset * scaleFactor
    }

    private fun generateAndShowCode() {
        if (currentAnimation == null) {
            uiManager.showTemporaryMessage("No animation selected.")
            return
        }

        val offsetString = currentFrames.map { String.format("%.3f", it.offsetX) + "f" }.joinToString(", ")
        val codeLine = "frameOffsetsX = floatArrayOf($offsetString)"

        println("--- Animation Offset Code ---")
        println("For animation: '${currentAnimation!!.name}'")
        println("Copy this line into PlayerSystem.kt:")
        println(codeLine)
        println("-----------------------------")

        uiManager.showTemporaryMessage("Code generated in console!")
    }

    private fun clearPreview() {
        previewImage.drawable = null
        offsetXField.text = "0.0"
        offsetXSlider.value = 0f
    }

    fun show() {
        // For now, let's hardcode the animations we want to edit.
        // A better system would get all animations from the AnimationSystem.
        animationSelectBox.setItems("attack_baseball_bat", "attack_knife")

        loadAnimation(animationSelectBox.items.first())

        stage.addActor(window)
        window.isVisible = true
        isVisible = true
    }

    fun hide() {
        window.remove()
        isVisible = false
    }

    fun isVisible(): Boolean = isVisible
}
