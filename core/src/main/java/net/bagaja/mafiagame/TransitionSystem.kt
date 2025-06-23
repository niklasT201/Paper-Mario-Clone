package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import kotlin.math.*

class TransitionSystem {

    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var camera: OrthographicCamera

    // Animation state
    private var isActive = false
    private var progress = 0f
    private var duration = 0f

    fun create(camera: OrthographicCamera) {
        this.camera = camera
        shapeRenderer = ShapeRenderer()
    }

    fun start(duration: Float) {
        if (isActive) return
        this.isActive = true
        this.progress = 0f
        this.duration = duration
    }

    fun update(deltaTime: Float) {
        if (!isActive) return

        progress += deltaTime
        if (progress >= duration) {
            isActive = false
            progress = duration
        }
    }

    fun render() {
        if (!isActive) return

        // Use an interpolation for a nice "wuuushh" effect instead of a linear one
        val percent = progress / duration
        val interpolatedPercent = Interpolation.pow3In.apply(percent)

        val screenWidth = Gdx.graphics.width.toFloat()
        val screenHeight = Gdx.graphics.height.toFloat()
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f

        // Enable blending for smooth lines
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        // Calculate how much of the spiral to draw (Snake-like growth)
        val totalSpirals = 3f // Total number of spiral turns
        val totalAngle = totalSpirals * 360f
        val currentMaxAngle = totalAngle * interpolatedPercent

        // Start radius and position
        val maxRadius = min(screenWidth, screenHeight) * 0.4f
        val startX = centerX
        val startY = screenHeight - 50f // Start near top

        // First draw the black area that follows the spiral pattern
        shapeRenderer.setColor(0f, 0f, 0f, 1f) // Black
        val blackThickness = 40f // Thicker black area around the spiral

        var previousBlackX = startX
        var previousBlackY = startY

        // Draw the black spiral area progressively
        val step = 2f // Angle step for smoothness
        var angle = 0f

        while (angle <= currentMaxAngle) {
            val radians = Math.toRadians(angle.toDouble())

            // Spiral inward: start from outside, gradually move to center
            val normalizedProgress = angle / totalAngle
            val radius = maxRadius * (1f - normalizedProgress * 0.9f) // Don't go completely to center

            // Calculate spiral position
            val spiralCenterX = centerX
            val spiralCenterY = centerY + (1f - normalizedProgress) * (startY - centerY) * 0.3f

            val currentBlackX = spiralCenterX + radius * cos(radians).toFloat()
            val currentBlackY = spiralCenterY + radius * sin(radians).toFloat()

            // Draw thick black line segment
            if (angle > 0) {
                drawThickLine(previousBlackX, previousBlackY, currentBlackX, currentBlackY, blackThickness)
            }

            previousBlackX = currentBlackX
            previousBlackY = currentBlackY
            angle += step
        }

        // Now draw the white spiral line on top
        shapeRenderer.setColor(1f, 1f, 1f, 1f) // White line

        val lineThickness = 8f
        var previousX = startX
        var previousY = startY

        // Draw the white spiral progressively
        angle = 0f
        while (angle <= currentMaxAngle) {
            val radians = Math.toRadians(angle.toDouble())

            // Spiral inward: start from outside, gradually move to center
            val normalizedProgress = angle / totalAngle
            val radius = maxRadius * (1f - normalizedProgress * 0.9f) // Don't go completely to center

            // Calculate spiral position
            val spiralCenterX = centerX
            val spiralCenterY = centerY + (1f - normalizedProgress) * (startY - centerY) * 0.3f

            val currentX = spiralCenterX + radius * cos(radians).toFloat()
            val currentY = spiralCenterY + radius * sin(radians).toFloat()

            // Draw thick line segment from previous point to current point
            if (angle > 0) {
                drawThickLine(previousX, previousY, currentX, currentY, lineThickness)
            }

            previousX = currentX
            previousY = currentY
            angle += step
        }

        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    private fun drawThickLine(x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float) {
        // Calculate perpendicular vector for line thickness
        val dx = x2 - x1
        val dy = y2 - y1
        val length = sqrt(dx * dx + dy * dy)

        if (length == 0f) return

        val perpX = -dy / length * thickness / 2f
        val perpY = dx / length * thickness / 2f

        // Draw thick line as a quad
        val vertices = floatArrayOf(
            x1 + perpX, y1 + perpY,
            x1 - perpX, y1 - perpY,
            x2 - perpX, y2 - perpY,
            x2 + perpX, y2 + perpY
        )

        // Draw as two triangles to form a quad
        shapeRenderer.triangle(
            vertices[0], vertices[1],
            vertices[2], vertices[3],
            vertices[4], vertices[5]
        )
        shapeRenderer.triangle(
            vertices[0], vertices[1],
            vertices[4], vertices[5],
            vertices[6], vertices[7]
        )

        // Draw circles at endpoints for smooth connections
        shapeRenderer.circle(x1, y1, thickness / 2f, 12)
        shapeRenderer.circle(x2, y2, thickness / 2f, 12)
    }

    fun isFinished(): Boolean {
        return !isActive && progress > 0f
    }

    fun isActive(): Boolean {
        return isActive
    }

    fun reset() {
        isActive = false
        progress = 0f
    }

    fun dispose() {
        shapeRenderer.dispose()
    }
}
