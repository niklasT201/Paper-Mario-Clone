package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A 1920s Mafia-style pause menu with vintage newspaper and speakeasy charm.
 * Features a prohibition-era design with art deco elements and smoky atmosphere.
 */
class PauseMenuUI(private val skin: Skin, private val stage: Stage, private val uiManager: UIManager, private val saveLoadSystem: SaveLoadSystem) {

    private lateinit var mainContainer: Table
    private lateinit var overlay: Image
    private lateinit var decorativeElements: Array<Image>
    private var isVisible = false
    private var animationTime = 0f
    private lateinit var visualSettingsButton: TextButton
    private lateinit var audioSettingsButton: TextButton

    fun initialize() {
        createSmokyOverlay()
        createNewspaperMenu()
        createFloatingDecorations()
    }

    fun update(deltaTime: Float) {
        if (isVisible) {
            animationTime += deltaTime
            animateFloatingElements()
        }
    }

    private fun createSmokyOverlay() {
        // Create a smoky, noir-style overlay with cigarette smoke effect
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
        stage.addActor(overlay)
    }

    fun setSettingsButtonVisibility(visible: Boolean) {
        if (::visualSettingsButton.isInitialized) {
            visualSettingsButton.isVisible = visible
        }
        if (::audioSettingsButton.isInitialized) {
            audioSettingsButton.isVisible = visible
        }
    }

    private fun createNewspaperMenu() {
        mainContainer = Table()
        mainContainer.setFillParent(true)
        mainContainer.center()
        mainContainer.isVisible = false
        stage.addActor(mainContainer)

        val buttonsTable = Table()

        // Main newspaper/wanted poster container
        val newspaperTable = Table()
        newspaperTable.background = createNewspaperBackground()
        newspaperTable.pad(50f, 60f, 40f, 60f)

        // Vintage header with art deco styling
        val headerTable = Table()
        headerTable.add(createArtDecoDecoration("left")).padRight(25f)

        val titleLabel = Label("⦿ GAME PAUSED ⦿", skin, "title")
        titleLabel.setAlignment(Align.center)
        titleLabel.color = Color.valueOf("#1A0F0A") // Dark ink black
        headerTable.add(titleLabel)

        headerTable.add(createArtDecoDecoration("right")).padLeft(25f)

        newspaperTable.add(headerTable).padBottom(35f).row()

        // Vintage subtitle
        val subtitleLabel = Label("~ The Family Business Awaits ~", skin, "default")
        subtitleLabel.setAlignment(Align.center)
        subtitleLabel.color = Color.valueOf("#3D2817")
        newspaperTable.add(subtitleLabel).padBottom(30f).row()

        // Menu buttons with 1920s styling
        val resumeButton = createVintageButton("⚡ RESUME OPERATIONS ⚡", Color.valueOf("#8B4513"))
        resumeButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                hide() // Resume is the same as closing the menu
            }
        })
        buttonsTable.add(resumeButton).fillX().height(60f).padBottom(10f).row()

        visualSettingsButton = createVintageButton("👁️ VISUAL SETTINGS 👁️", Color.valueOf("#654321"))
        visualSettingsButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                uiManager.showVisualSettings()
            }
        })
        // Original: "💾 SAVE GAME 💾"
        buttonsTable.add(visualSettingsButton).fillX().height(60f).padBottom(10f).row()

        audioSettingsButton = createVintageButton("🔊 AUDIO SETTINGS 🔊", Color.valueOf("#4A4A4A")) // A neutral gray
        audioSettingsButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                uiManager.showAudioSettings()
            }
        })
        buttonsTable.add(audioSettingsButton).fillX().height(60f).padBottom(10f).row()

        val saveButton = createVintageButton("💰 SAVE GAME 💰", Color.valueOf("#2F4F2F"))
        saveButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                saveLoadSystem.saveGame(uiManager.game.currentSaveFileName)
                hide()
            }
        })
        buttonsTable.add(saveButton).fillX().height(60f).padBottom(10f).row()

        // --- NEW BUTTON REPLACES "LOAD GAME" ---
        val returnToMenuButton = createVintageButton("↩️ RETURN TO MENU ↩️", Color.valueOf("#4682B4")) // Steel Blue
        returnToMenuButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                uiManager.game.returnToStartMenu()
            }
        })
        buttonsTable.add(returnToMenuButton).fillX().height(60f).padBottom(10f).row()

        val quitButton = createVintageButton("🚪 QUIT TO DESKTOP 🚪", Color.valueOf("#8B0000"))
        quitButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                Gdx.app.exit()
            }
        })
        buttonsTable.add(quitButton).fillX().height(60f).padTop(15f).row()

        newspaperTable.add(buttonsTable).width(380f).row()

        // Bottom art deco decoration
        val bottomDecoration = createBottomBanner()
        newspaperTable.add(bottomDecoration).padTop(25f)

        mainContainer.add(newspaperTable)
    }

    private fun createNewspaperBackground(): TextureRegionDrawable {
        val pixmap = Pixmap(420, 480, Pixmap.Format.RGBA8888)

        // Aged newspaper color
        val paperColor = Color.valueOf("#F5F1E8") // Cream newspaper
        pixmap.setColor(paperColor)
        pixmap.fill()

        // Add newsprint texture and aging
        val inkSpots = Color.valueOf("#E8E0D6")
        for (i in 0 until 300) {
            val x = Random.nextInt(420)
            val y = Random.nextInt(480)
            val size = Random.nextInt(2) + 1
            pixmap.setColor(inkSpots)
            pixmap.fillCircle(x, y, size)
        }

        // Create art deco border with geometric patterns
        val borderColor = Color.valueOf("#2C1810") // Dark brown
        pixmap.setColor(borderColor)

        // Main border
        for (i in 0 until 6) {
            pixmap.drawRectangle(i, i, 420 - i * 2, 480 - i * 2)
        }

        // Art deco corner elements
        drawArtDecoCorners(pixmap)

        // Add prohibition-era elements
        drawVintageElements(pixmap)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun drawArtDecoCorners(pixmap: Pixmap) {
        val accentColor = Color.valueOf("#8B6914") // Dark gold
        pixmap.setColor(accentColor)

        val cornerSize = 25

        // Top corners - art deco fan patterns
        for (i in 10 until cornerSize) {
            // Top-left fan
            pixmap.drawLine(10, i, i, 10)
            pixmap.drawLine(12, i, i, 12)

            // Top-right fan
            pixmap.drawLine(420 - 10, i, 420 - i, 10)
            pixmap.drawLine(420 - 12, i, 420 - i, 12)

            // Bottom-left fan
            pixmap.drawLine(10, 480 - i, i, 480 - 10)
            pixmap.drawLine(12, 480 - i, i, 480 - 12)

            // Bottom-right fan
            pixmap.drawLine(420 - 10, 480 - i, 420 - i, 480 - 10)
            pixmap.drawLine(420 - 12, 480 - i, 420 - i, 480 - 12)
        }
    }

    private fun drawVintageElements(pixmap: Pixmap) {
        val accentColor = Color.valueOf("#8B6914") // Dark gold
        pixmap.setColor(accentColor)

        // Side geometric patterns
        for (y in 100 until 380 step 40) {
            // Left side diamonds
            pixmap.fillCircle(20, y, 4)
            pixmap.drawRectangle(16, y - 2, 8, 4)

            // Right side diamonds
            pixmap.fillCircle(400, y, 4)
            pixmap.drawRectangle(396, y - 2, 8, 4)
        }
    }

    private fun createVintageButton(text: String, bgColor: Color): TextButton {
        val button = TextButton(text, skin, "default")

        // Create vintage button with leather/wood texture
        val buttonPixmap = Pixmap(300, 45, Pixmap.Format.RGBA8888)

        // Base color - darker vintage tone
        val baseColor = Color(bgColor.r * 0.8f, bgColor.g * 0.8f, bgColor.b * 0.8f, 1f)
        buttonPixmap.setColor(baseColor)
        buttonPixmap.fill()

        // Add leather-like texture
        val textureColor = Color(bgColor.r * 0.6f, bgColor.g * 0.6f, bgColor.b * 0.6f, 0.7f)
        for (i in 0 until 80) {
            val x = Random.nextInt(300)
            val y = Random.nextInt(45)
            val size = Random.nextInt(3) + 1
            buttonPixmap.setColor(textureColor)
            buttonPixmap.fillCircle(x, y, size)
        }

        // Art deco style border
        val borderColor = Color.valueOf("#1A0F0A")
        buttonPixmap.setColor(borderColor)

        // Multiple border lines for depth
        buttonPixmap.drawRectangle(0, 0, 300, 45)
        buttonPixmap.drawRectangle(1, 1, 298, 43)
        buttonPixmap.drawRectangle(2, 2, 296, 41)

        // Brass-like highlight
        val highlightColor = Color.valueOf("#CD7F32") // Bronze
        buttonPixmap.setColor(highlightColor)
        buttonPixmap.drawLine(3, 3, 296, 3)
        buttonPixmap.drawLine(3, 3, 3, 41)

        // Corner accents
        for (i in 0 until 8) {
            buttonPixmap.drawPixel(5 + i, 5)
            buttonPixmap.drawPixel(5, 5 + i)
            buttonPixmap.drawPixel(294 - i, 5)
            buttonPixmap.drawPixel(294, 5 + i)
            buttonPixmap.drawPixel(5 + i, 39)
            buttonPixmap.drawPixel(5, 39 - i)
            buttonPixmap.drawPixel(294 - i, 39)
            buttonPixmap.drawPixel(294, 39 - i)
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

    private fun createFloatingDecorations() {
        decorativeElements = Array(8) { index ->
            val decoration = createSmokeParticle()
            decoration.setPosition(
                Random.nextFloat() * stage.width,
                Random.nextFloat() * stage.height
            )
            decoration.isVisible = false
            stage.addActor(decoration)
            decoration
        }
    }

    private fun createSmokeParticle(): Image {
        val pixmap = Pixmap(20, 20, Pixmap.Format.RGBA8888)

        // Create more realistic cigarette smoke wisps
        val smokeColors = arrayOf(
            Color.valueOf("#E5E5E5"), // Very light gray
            Color.valueOf("#D3D3D3"), // Light gray
            Color.valueOf("#C8C8C8"), // Medium light gray
            Color.valueOf("#F0F0F0")  // Almost white
        )

        val baseColor = smokeColors[Random.nextInt(smokeColors.size)]

        // Create wispy smoke with varying density
        val smokeShape = Random.nextInt(3)
        when (smokeShape) {
            0 -> {
                // Circular wisp
                for (radius in 1..6) {
                    val alpha = (0.8f - radius * 0.12f).coerceAtLeast(0.1f)
                    val color = Color(baseColor.r, baseColor.g, baseColor.b, alpha)
                    pixmap.setColor(color)
                    pixmap.fillCircle(10, 10, radius)
                }
            }
            1 -> {
                // Elongated wisp
                for (i in 0 until 12) {
                    val alpha = (0.7f - i * 0.05f).coerceAtLeast(0.1f)
                    val color = Color(baseColor.r, baseColor.g, baseColor.b, alpha)
                    pixmap.setColor(color)
                    val thickness = (4 - i / 3).coerceAtLeast(1)
                    pixmap.fillCircle(10 + (sin(i * 0.3) * 2).toInt(), 5 + i, thickness)
                }
            }
            else -> {
                // Swirly wisp
                for (i in 0 until 10) {
                    val angle = i * 0.6
                    val radius = 2 + i * 0.4
                    val alpha = (0.6f - i * 0.05f).coerceAtLeast(0.1f)
                    val color = Color(baseColor.r, baseColor.g, baseColor.b, alpha)
                    pixmap.setColor(color)
                    val x = (10 + cos(angle) * radius).toInt().coerceIn(0, 19)
                    val y = (10 + sin(angle) * radius).toInt().coerceIn(0, 19)
                    pixmap.fillCircle(x, y, 2)
                }
            }
        }

        val texture = Texture(pixmap)
        pixmap.dispose()
        return Image(texture)
    }

    private fun animateFloatingElements() {
        decorativeElements.forEachIndexed { index, element ->
            // More realistic smoke behavior - slower, more organic movement
            val baseSpeed = 0.3f + (index % 3) * 0.1f
            element.y += baseSpeed

            // Natural smoke turbulence with layered movement
            val primarySway = sin(animationTime * 0.3f + (index * 0.8f)) * 12f
            val secondarySway = cos(animationTime * 0.7f + (index * 1.3f)) * 6f
            val turbulence = sin(animationTime * 1.5f + (index * 2.1f)) * 3f

            val baseX = Random.nextFloat() * stage.width // More random starting positions
            element.x = baseX + primarySway + secondarySway + turbulence

            // More complex fading - smoke gets lighter as it rises
            val lifePhase = ((element.y / stage.height) * 2f).coerceIn(0f, 1f)
            val fadeIntensity = sin(animationTime * 0.5f + index * 0.7f)
            val baseAlpha = 0.4f - (lifePhase * 0.3f) // Fade as it rises
            element.color.a = (baseAlpha + fadeIntensity * 0.15f).coerceIn(0.05f, 0.6f)

            // Slight scale changes for depth illusion
            val scaleVariation = 1f + sin(animationTime * 0.4f + index * 1.1f) * 0.2f
            element.setScale(scaleVariation)

            // Reset smoke particle when it drifts away
            if (element.y > stage.height + 50 || element.x < -30 || element.x > stage.width + 30) {
                element.y = -30f - Random.nextFloat() * 20f // Start below screen
                element.x = Random.nextFloat() * (stage.width + 60f) - 30f // Can start slightly off-screen
                element.color.a = 0.5f // Reset to fuller opacity
                element.setScale(0.8f + Random.nextFloat() * 0.4f) // Random initial scale
            }
        }
    }

    fun toggle() {
        if (isVisible) hide() else show()
    }

    fun show() {
        isVisible = true
        overlay.isVisible = true
        mainContainer.isVisible = true
        decorativeElements.forEach { it.isVisible = true }
        mainContainer.toFront()

        // Smoky fade-in effect
        overlay.color.a = 0f
        overlay.addAction(Actions.fadeIn(0.6f, Interpolation.fade))

        // Newspaper unfolding animation
        mainContainer.scaleX = 0.1f
        mainContainer.scaleY = 0.8f
        mainContainer.color.a = 0f
        mainContainer.rotation = 0f

        mainContainer.addAction(
            Actions.parallel(
                Actions.fadeIn(0.5f, Interpolation.fade),
                Actions.scaleTo(1f, 1f, 0.7f, Interpolation.swingOut)
            )
        )

        // Smoke particles drift in
        decorativeElements.forEach { particle ->
            particle.color.a = 0f
            particle.addAction(
                Actions.delay(Random.nextFloat() * 0.4f,
                    Actions.fadeIn(1.2f, Interpolation.fade))
            )
        }
    }

    fun hide() {
        isVisible = false

        overlay.addAction(Actions.sequence(
            Actions.fadeOut(0.5f, Interpolation.fade),
            Actions.visible(false)
        ))

        mainContainer.addAction(Actions.sequence(
            Actions.parallel(
                Actions.fadeOut(0.4f, Interpolation.fade),
                Actions.scaleTo(0.1f, 0.8f, 0.4f, Interpolation.swingIn)
            ),
            Actions.visible(false)
        ))

        decorativeElements.forEach { particle ->
            particle.addAction(Actions.sequence(
                Actions.fadeOut(0.4f, Interpolation.fade),
                Actions.visible(false)
            ))
        }
    }

    fun hideInstantly() {
        isVisible = false
        overlay.clearActions()
        mainContainer.clearActions()
        decorativeElements.forEach { it.clearActions() }

        overlay.isVisible = false
        mainContainer.isVisible = false
        decorativeElements.forEach { it.isVisible = false }
    }

    fun isVisible(): Boolean = isVisible

    fun dispose() {
        // Dispose textures if needed
    }
}
