package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Interpolation

class TransitionSystem {

    private lateinit var spriteBatch: SpriteBatch
    private lateinit var swirlTexture: Texture
    private lateinit var camera: OrthographicCamera // To get screen dimensions

    // Animation state
    private var isActive = false
    private var progress = 0f
    private var duration = 0f

    // You can replace this with your own swirl.png asset later!
    fun create(camera: OrthographicCamera) {
        this.camera = camera
        spriteBatch = SpriteBatch()

        // Create a simple placeholder texture programmatically.
        // A white spiral on a transparent background is ideal.
        val pixmap = Pixmap(256, 256, Pixmap.Format.RGBA8888)
        pixmap.setColor(1f, 1f, 1f, 1f)
        val centerX = pixmap.width / 2
        val centerY = pixmap.height / 2
        for (i in 0..720) { // Two full rotations
            val angle = Math.toRadians(i.toDouble())
            val radius = i / 720.0 * centerX // Radius grows with angle
            val x = centerX + (radius * Math.cos(angle)).toInt()
            val y = centerY + (radius * Math.sin(angle)).toInt()
            pixmap.drawPixel(x, y)
            pixmap.drawPixel(x+1, y) // Make it a bit thicker
            pixmap.drawPixel(x, y+1)
        }
        swirlTexture = Texture(pixmap)
        pixmap.dispose()
    }

    fun start(duration: Float) {
        if (isActive) return // Don't start a new one if already running
        this.isActive = true
        this.progress = 0f
        this.duration = duration
    }

    fun update(deltaTime: Float) {
        if (!isActive) return

        progress += deltaTime
        if (progress >= duration) {
            isActive = false
            progress = duration // Clamp it
        }
    }

    fun render() {
        if (!isActive) return

        // Use an interpolation for a nice "wuuushh" effect instead of a linear one
        val percent = progress / duration
        val interpolatedPercent = Interpolation.pow3In.apply(percent)

        // Animation logic:
        // Scale: Starts huge (e.g., 10x screen size) and shrinks to nothing.
        // Rotation: Spins rapidly.
        val scale = Interpolation.linear.apply(15f, 0f, interpolatedPercent)
        val rotation = Interpolation.linear.apply(0f, 1080f, interpolatedPercent) // Three full spins

        val screenWidth = Gdx.graphics.width.toFloat()
        val screenHeight = Gdx.graphics.height.toFloat()
        val textureWidth = swirlTexture.width.toFloat()
        val textureHeight = swirlTexture.height.toFloat()

        spriteBatch.projectionMatrix = camera.combined
        spriteBatch.begin()

        // Draw the swirl centered on the screen
        spriteBatch.draw(
            swirlTexture,
            (screenWidth - textureWidth) / 2f,
            (screenHeight - textureHeight) / 2f,
            textureWidth / 2f, // originX for rotation
            textureHeight / 2f, // originY for rotation
            textureWidth,
            textureHeight,
            scale, // scaleX
            scale, // scaleY
            rotation,
            0, 0, // srcX, srcY
            swirlTexture.width, swirlTexture.height, // srcWidth, srcHeight
            false, false // flipX, flipY
        )

        spriteBatch.end()
    }

    fun isFinished(): Boolean {
        return !isActive && progress > 0f // It's finished only after it has run at least once
    }

    fun isActive(): Boolean {
        return isActive
    }

    fun reset() {
        isActive = false
        progress = 0f
    }

    fun dispose() {
        spriteBatch.dispose()
        swirlTexture.dispose()
    }
}
