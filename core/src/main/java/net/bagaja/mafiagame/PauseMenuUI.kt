package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A Paper Mario-style pause menu with storybook charm and whimsical animations.
 * Features a floating book/journal design with decorative elements.
 */
class PauseMenuUI(private val skin: Skin, private val stage: Stage) {

    private lateinit var mainContainer: Table
    private lateinit var overlay: Image
    private lateinit var decorativeElements: Array<Image>
    private var isVisible = false
    private var animationTime = 0f

    fun initialize() {
        createMagicalOverlay()
        createStoryBookMenu()
        createFloatingDecorations()
    }

    fun update(deltaTime: Float) {
        if (isVisible) {
            animationTime += deltaTime
            animateFloatingElements()
        }
    }

    private fun createMagicalOverlay() {
        // Create a more magical, softer overlay
        val pixmap = Pixmap(100, 100, Pixmap.Format.RGBA8888)

        // Create a radial gradient effect
        val centerX = 50
        val centerY = 50

        for (x in 0 until 100) {
            for (y in 0 until 100) {
                val distance = kotlin.math.sqrt(((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)).toDouble()).toFloat()
                val normalizedDistance = (distance / 70f).coerceIn(0f, 1f)

                // Create a dreamy purple-blue gradient
                val alpha = 0.3f + (normalizedDistance * 0.4f)
                pixmap.setColor(0.1f, 0.05f, 0.2f, alpha)
                pixmap.drawPixel(x, y)
            }
        }

        overlay = Image(Texture(pixmap))
        pixmap.dispose()
        overlay.setFillParent(true)
        overlay.isVisible = false
        stage.addActor(overlay)
    }

    private fun createStoryBookMenu() {
        mainContainer = Table()
        mainContainer.setFillParent(true)
        mainContainer.center()
        mainContainer.isVisible = false
        stage.addActor(mainContainer)

        // Main book/journal container
        val bookTable = Table()
        bookTable.background = createStoryBookBackground()
        bookTable.pad(60f, 70f, 50f, 70f)

        // Decorative header with flourishes
        val headerTable = Table()
        headerTable.add(createDecorationImage("left-flourish")).padRight(20f)

        val titleLabel = Label("~ PAUSED ~", skin, "title")
        titleLabel.setAlignment(Align.center)
        titleLabel.color = Color.valueOf("#2C1810") // Dark brown ink color
        headerTable.add(titleLabel)

        headerTable.add(createDecorationImage("right-flourish")).padLeft(20f)

        bookTable.add(headerTable).padBottom(40f).row()

        // Menu buttons with Paper Mario style
        val buttonsTable = Table()
        buttonsTable.add(createPaperButton("✦ RESUME ✦", Color.valueOf("#4A90E2"))).fillX().height(65f).padBottom(12f).row()
        buttonsTable.add(createPaperButton("⚙ SETTINGS ⚙", Color.valueOf("#7B68EE"))).fillX().height(65f).padBottom(12f).row()
        buttonsTable.add(createPaperButton("💾 SAVE GAME 💾", Color.valueOf("#50C878"))).fillX().height(65f).padBottom(12f).row()
        buttonsTable.add(createPaperButton("🚪 QUIT TO MENU 🚪", Color.valueOf("#FF6B6B"))).fillX().height(65f).padTop(20f).row()

        bookTable.add(buttonsTable).width(400f).row()

        // Decorative footer
        val footerDecoration = createDecorationImage("bottom-swirl")
        bookTable.add(footerDecoration).padTop(30f)

        mainContainer.add(bookTable)
    }

    private fun createStoryBookBackground(): TextureRegionDrawable {
        val pixmap = Pixmap(400, 500, Pixmap.Format.RGBA8888)

        // Create a warm, aged paper background
        val paperColor = Color.valueOf("#F7F3E9") // Warm cream
        pixmap.setColor(paperColor)
        pixmap.fill()

        // Add subtle texture and aging spots
        val agingColor = Color.valueOf("#E8D5B7")
        for (i in 0 until 200) {
            val x = Random.nextInt(400)
            val y = Random.nextInt(500)
            val size = Random.nextInt(3) + 1
            pixmap.setColor(agingColor)
            pixmap.fillCircle(x, y, size)
        }

        // Create decorative border
        val borderColor = Color.valueOf("#8B4513") // Saddle brown
        pixmap.setColor(borderColor)

        // Outer decorative border
        for (i in 0 until 10) {
            pixmap.drawRectangle(i, i, 400 - i * 2, 500 - i * 2)
        }

        // Inner decorative elements
        pixmap.setColor(Color.valueOf("#D4AF37")) // Gold accents
        drawDecorativeCorners(pixmap)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun drawDecorativeCorners(pixmap: Pixmap) {
        // Simple corner decorations
        val size = 30

        // Top-left corner flourish
        for (i in 15 until size) {
            pixmap.drawLine(15, i, i, 15)
        }

        // Top-right corner flourish
        for (i in 15 until size) {
            pixmap.drawLine(400 - 15, i, 400 - i, 15)
        }

        // Bottom-left corner flourish
        for (i in 15 until size) {
            pixmap.drawLine(15, 500 - i, i, 500 - 15)
        }

        // Bottom-right corner flourish
        for (i in 15 until size) {
            pixmap.drawLine(400 - 15, 500 - i, 400 - i, 500 - 15)
        }
    }

    private fun createPaperButton(text: String, bgColor: Color): TextButton {
        val button = TextButton(text, skin, "default")

        // Create paper-style button background
        val buttonPixmap = Pixmap(300, 50, Pixmap.Format.RGBA8888)

        // Main button color with slight transparency
        val mainColor = Color(bgColor.r, bgColor.g, bgColor.b, 0.9f)
        buttonPixmap.setColor(mainColor)
        buttonPixmap.fill()

        // Add paper texture
        val textureColor = Color(bgColor.r * 0.9f, bgColor.g * 0.9f, bgColor.b * 0.9f, 0.8f)
        buttonPixmap.setColor(textureColor)
        for (i in 0 until 30) {
            val x = Random.nextInt(300)
            val y = Random.nextInt(50)
            buttonPixmap.drawPixel(x, y)
        }

        // Dark border for depth
        val borderColor = Color(bgColor.r * 0.6f, bgColor.g * 0.6f, bgColor.b * 0.6f, 1f)
        buttonPixmap.setColor(borderColor)
        buttonPixmap.drawRectangle(0, 0, 300, 50)
        buttonPixmap.drawRectangle(1, 1, 298, 48)

        // Light highlight for 3D effect
        val highlightColor = Color(bgColor.r * 1.2f, bgColor.g * 1.2f, bgColor.b * 1.2f, 0.8f)
        buttonPixmap.setColor(highlightColor)
        buttonPixmap.drawLine(2, 2, 297, 2)
        buttonPixmap.drawLine(2, 2, 2, 47)

        val buttonTexture = Texture(buttonPixmap)
        buttonPixmap.dispose()

        button.style.up = TextureRegionDrawable(TextureRegion(buttonTexture))
        button.label.color = Color.WHITE

        return button
    }

    private fun createDecorationImage(type: String): Image {
        val size = when (type) {
            "left-flourish", "right-flourish" -> 40
            else -> 60
        }

        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val decorColor = Color.valueOf("#D4AF37") // Gold
        pixmap.setColor(decorColor)

        when (type) {
            "left-flourish" -> {
                // Simple left-pointing decorative swirl
                for (i in 0 until size) {
                    val y = (size / 2 + sin(i * 0.2) * 8).toInt()
                    if (y in 0 until size) {
                        pixmap.fillCircle(i, y, 2)
                    }
                }
            }
            "right-flourish" -> {
                // Simple right-pointing decorative swirl
                for (i in 0 until size) {
                    val y = (size / 2 + sin((size - i) * 0.2) * 8).toInt()
                    if (y in 0 until size) {
                        pixmap.fillCircle(i, y, 2)
                    }
                }
            }
            else -> {
                // Bottom decoration - simple star pattern
                pixmap.fillCircle(size / 2, size / 2, 8)
                for (angle in 0 until 360 step 45) {
                    val rad = Math.toRadians(angle.toDouble())
                    val x = (size / 2 + cos(rad) * 15).toInt()
                    val y = (size / 2 + sin(rad) * 15).toInt()
                    pixmap.fillCircle(x, y, 3)
                }
            }
        }

        val texture = Texture(pixmap)
        pixmap.dispose()
        return Image(texture)
    }

    private fun createFloatingDecorations() {
        decorativeElements = Array(6) { index ->
            val decoration = createMagicalParticle()
            decoration.setPosition(
                Random.nextFloat() * stage.width,
                Random.nextFloat() * stage.height
            )
            decoration.isVisible = false
            stage.addActor(decoration)
            decoration
        }
    }

    private fun createMagicalParticle(): Image {
        val pixmap = Pixmap(12, 12, Pixmap.Format.RGBA8888)
        val colors = arrayOf(
            Color.valueOf("#FFD700"), // Gold
            Color.valueOf("#FFA500"), // Orange
            Color.valueOf("#DA70D6"), // Orchid
            Color.valueOf("#87CEEB")  // Sky blue
        )

        val color = colors[Random.nextInt(colors.size)]
        pixmap.setColor(color)
        pixmap.fillCircle(6, 6, 4)

        // Add sparkle effect
        pixmap.setColor(Color.WHITE)
        pixmap.fillCircle(6, 6, 2)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return Image(texture)
    }

    private fun animateFloatingElements() {
        decorativeElements.forEachIndexed { index, element ->
            val offsetY = sin(animationTime * 2f + index) * 20f
            val originalY = element.y
            element.setPosition(element.x, originalY + offsetY)

            // Gentle rotation
            element.rotateBy(0.5f)

            // Subtle scale pulsing
            val scale = 1f + sin(animationTime * 3f + index * 0.5f) * 0.1f
            element.setScale(scale)
        }
    }

    fun toggle() {
        if (isVisible) hide() else show()
    }

    private fun show() {
        isVisible = true
        overlay.isVisible = true
        mainContainer.isVisible = true
        decorativeElements.forEach { it.isVisible = true }
        mainContainer.toFront()

        // Magical book opening animation
        overlay.color.a = 0f
        overlay.addAction(Actions.fadeIn(0.5f, Interpolation.fade))

        mainContainer.scaleX = 0.3f
        mainContainer.scaleY = 0.3f
        mainContainer.color.a = 0f
        mainContainer.rotation = -180f // Start rotated

        mainContainer.addAction(
            Actions.parallel(
                Actions.fadeIn(0.5f, Interpolation.fade),
                Actions.scaleTo(1f, 1f, 0.6f, Interpolation.swingOut),
                Actions.rotateTo(0f, 0.6f, Interpolation.swingOut)
            )
        )

        // Animate decorative particles
        decorativeElements.forEach { particle ->
            particle.color.a = 0f
            particle.addAction(
                Actions.delay(Random.nextFloat() * 0.3f,
                    Actions.fadeIn(0.8f, Interpolation.fade))
            )
        }
    }

    private fun hide() {
        isVisible = false

        overlay.addAction(Actions.sequence(
            Actions.fadeOut(0.4f, Interpolation.fade),
            Actions.visible(false)
        ))

        mainContainer.addAction(Actions.sequence(
            Actions.parallel(
                Actions.fadeOut(0.4f, Interpolation.fade),
                Actions.scaleTo(0.3f, 0.3f, 0.4f, Interpolation.swingIn),
                Actions.rotateTo(-90f, 0.4f, Interpolation.swingIn)
            ),
            Actions.visible(false)
        ))

        decorativeElements.forEach { particle ->
            particle.addAction(Actions.sequence(
                Actions.fadeOut(0.3f, Interpolation.fade),
                Actions.visible(false)
            ))
        }
    }

    fun isVisible(): Boolean = isVisible

    fun dispose() {
        // Dispose textures if needed
    }
}
