package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import kotlin.math.*
import kotlin.random.Random

class TransitionSystem {

    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var camera: OrthographicCamera

    // Animation state
    private var isActive = false
    private var progress = 0f
    private var duration = 1.2f
    private var transitionType = TransitionType.OUT

    // Vortex parameters
    private val particles = mutableListOf<VortexParticle>()
    private val maxParticles = 120
    private var vortexRadius = 0f
    private var rotationSpeed = 0f
    private var centerX = 0f
    private var centerY = 0f

    enum class TransitionType {
        OUT,    // Leaving current area (contract to center)
        IN      // Entering new area (expand from center)
    }

    data class VortexParticle(
        var x: Float,
        var y: Float,
        var angle: Float,
        var distance: Float,
        var speed: Float,
        var size: Float,
        var alpha: Float,
        val originalDistance: Float,
        val startDelay: Float = 0f
    )

    fun create(camera: OrthographicCamera) {
        this.camera = camera
        shapeRenderer = ShapeRenderer()
    }

    private fun initializeParticles() {
        particles.clear()
        val screenWidth = Gdx.graphics.width.toFloat()
        val screenHeight = Gdx.graphics.height.toFloat()
        centerX = screenWidth / 2f
        centerY = screenHeight / 2f

        repeat(maxParticles) {
            val angle = Random.nextFloat() * 360f
            val maxDistance = min(screenWidth, screenHeight) * 0.8f
            val distance = Random.nextFloat() * maxDistance + 50f

            // For OUT transition: start at original positions
            // For IN transition: start at center
            val startDistance = if (transitionType == TransitionType.OUT) distance else 0f
            val x = centerX + cos(Math.toRadians(angle.toDouble())).toFloat() * startDistance
            val y = centerY + sin(Math.toRadians(angle.toDouble())).toFloat() * startDistance

            particles.add(VortexParticle(
                x = x,
                y = y,
                angle = angle,
                distance = startDistance,
                speed = Random.nextFloat() * 1.5f + 0.8f,
                size = Random.nextFloat() * 3f + 1.5f,
                alpha = if (transitionType == TransitionType.OUT) 0.8f else 0f,
                originalDistance = distance,
                startDelay = Random.nextFloat() * 0.3f
            ))
        }
    }

    // Call this when LEAVING an area (room -> open world, or open world -> room)
    fun startOutTransition(duration: Float = 1.2f) {
        if (isActive) return

        this.transitionType = TransitionType.OUT
        this.isActive = true
        this.progress = 0f
        this.duration = duration
        this.rotationSpeed = 0f

        initializeParticles()
    }

    // Call this when ENTERING an area (after the game state has switched)
    fun startInTransition(duration: Float = 1.2f) {
        if (isActive) return

        this.transitionType = TransitionType.IN
        this.isActive = true
        this.progress = 0f
        this.duration = duration
        this.rotationSpeed = 200f // Start with some rotation

        initializeParticles()
    }

    fun update(deltaTime: Float) {
        if (!isActive) return

        progress += deltaTime

        // Update rotation speed based on transition type
        when (transitionType) {
            TransitionType.OUT -> {
                // Accelerate rotation as we contract
                rotationSpeed += deltaTime * 400f
            }
            TransitionType.IN -> {
                // Decelerate rotation as we expand
                rotationSpeed = max(0f, rotationSpeed - deltaTime * 150f)
            }
        }

        updateParticles(deltaTime)

        if (progress >= duration) {
            isActive = false
            progress = duration
        }
    }

    private fun updateParticles(deltaTime: Float) {
        val normalizedProgress = (progress / duration).coerceIn(0f, 1f)

        particles.forEach { particle ->
            // Apply start delay
            val delayedProgress = ((progress - particle.startDelay) / duration).coerceIn(0f, 1f)

            // Update rotation
            particle.angle += rotationSpeed * deltaTime * particle.speed

            // Update distance and alpha based on transition type
            when (transitionType) {
                TransitionType.OUT -> {
                    // Contract to center with easing
                    val contractionFactor = Interpolation.pow2In.apply(delayedProgress)
                    particle.distance = particle.originalDistance * (1f - contractionFactor)
                    particle.alpha = 0.9f - contractionFactor * 0.4f

                    // Add spiral effect that increases over time
                    val spiralIntensity = contractionFactor * 2f
                    particle.angle += spiralIntensity * 180f * deltaTime
                }
                TransitionType.IN -> {
                    // Expand from center with bounce
                    val expansionFactor = Interpolation.bounceOut.apply(delayedProgress)
                    particle.distance = particle.originalDistance * expansionFactor
                    particle.alpha = expansionFactor * 0.8f + 0.1f

                    // Reverse spiral effect that decreases over time
                    val spiralIntensity = (1f - delayedProgress) * 1.5f
                    particle.angle -= spiralIntensity * 120f * deltaTime
                }
            }

            // Calculate final position with spiral offset
            val spiralOffset = when (transitionType) {
                TransitionType.OUT -> normalizedProgress * particle.distance * 0.4f
                TransitionType.IN -> (1f - normalizedProgress) * particle.distance * 0.2f
            }

            val spiralAngle = particle.angle
            val finalDistance = particle.distance + sin(Math.toRadians(spiralAngle.toDouble() * 3)).toFloat() * spiralOffset

            particle.x = centerX + cos(Math.toRadians(spiralAngle.toDouble())).toFloat() * finalDistance
            particle.y = centerY + sin(Math.toRadians(spiralAngle.toDouble())).toFloat() * finalDistance
        }

        // Update vortex radius
        when (transitionType) {
            TransitionType.OUT -> {
                val maxRadius = min(Gdx.graphics.width, Gdx.graphics.height) * 0.5f
                vortexRadius = maxRadius * (1f - Interpolation.pow2In.apply(normalizedProgress))
            }
            TransitionType.IN -> {
                val maxRadius = min(Gdx.graphics.width, Gdx.graphics.height) * 0.5f
                vortexRadius = maxRadius * Interpolation.elasticOut.apply(normalizedProgress)
            }
        }
    }

    fun render() {
        if (!isActive) return

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        // Render background overlay
        renderBackgroundOverlay()

        // Render vortex rings
        renderVortexRings()

        // Render particles
        renderParticles()

        // Render central vortex core
        renderVortexCore()

        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    private fun renderBackgroundOverlay() {
        val normalizedProgress = (progress / duration).coerceIn(0f, 1f)

        val alpha = when (transitionType) {
            TransitionType.OUT -> normalizedProgress * 0.8f
            TransitionType.IN -> 0.8f * (1f - normalizedProgress)
        }

        shapeRenderer.setColor(0.05f, 0.05f, 0.15f, alpha.coerceIn(0f, 1f))
        shapeRenderer.rect(0f, 0f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
    }

    private fun renderVortexRings() {
        val ringCount = 6
        repeat(ringCount) { i ->
            val ringRadius = vortexRadius * (1f + i * 0.25f)
            val ringAlpha = (1f - i.toFloat() / ringCount) * 0.3f
            val ringRotation = rotationSpeed * 0.008f * (i + 1)

            // Different colors for different transition types
            when (transitionType) {
                TransitionType.OUT -> {
                    shapeRenderer.setColor(0.2f, 0.4f, 0.9f, ringAlpha) // Blue
                }
                TransitionType.IN -> {
                    shapeRenderer.setColor(0.3f, 0.9f, 0.4f, ringAlpha) // Green
                }
            }

            drawRingSegments(centerX, centerY, ringRadius, 24, ringRotation)
        }
    }

    private fun drawRingSegments(centerX: Float, centerY: Float, radius: Float, segments: Int, rotation: Float) {
        val angleStep = 360f / segments
        repeat(segments) { i ->
            if (i % 2 == 0) {
                val startAngle = i * angleStep + rotation
                val endAngle = startAngle + angleStep * 0.6f

                val startRad = Math.toRadians(startAngle.toDouble())
                val endRad = Math.toRadians(endAngle.toDouble())

                val innerRadius = radius * 0.85f
                val outerRadius = radius

                val x1 = centerX + cos(startRad).toFloat() * innerRadius
                val y1 = centerY + sin(startRad).toFloat() * innerRadius
                val x2 = centerX + cos(endRad).toFloat() * innerRadius
                val y2 = centerY + sin(endRad).toFloat() * innerRadius
                val x3 = centerX + cos(endRad).toFloat() * outerRadius
                val y3 = centerY + sin(endRad).toFloat() * outerRadius
                val x4 = centerX + cos(startRad).toFloat() * outerRadius
                val y4 = centerY + sin(startRad).toFloat() * outerRadius

                shapeRenderer.triangle(x1, y1, x2, y2, x3, y3)
                shapeRenderer.triangle(x1, y1, x3, y3, x4, y4)
            }
        }
    }

    private fun renderParticles() {
        particles.forEach { particle ->
            val color = when (transitionType) {
                TransitionType.OUT -> Triple(0.4f, 0.6f, 1f) // Blue particles
                TransitionType.IN -> Triple(0.3f, 1f, 0.5f)  // Green particles
            }

            shapeRenderer.setColor(color.first, color.second, color.third, particle.alpha)
            shapeRenderer.circle(particle.x, particle.y, particle.size, 8)

            // Add motion blur/trail effect
            if (particle.alpha > 0.1f) {
                val trailLength = particle.size * 1.5f
                val motionAngle = particle.angle + 180f
                val trailX = particle.x + cos(Math.toRadians(motionAngle.toDouble())).toFloat() * trailLength
                val trailY = particle.y + sin(Math.toRadians(motionAngle.toDouble())).toFloat() * trailLength

                shapeRenderer.setColor(color.first, color.second, color.third, particle.alpha * 0.4f)
                drawThickLine(particle.x, particle.y, trailX, trailY, particle.size * 0.4f)
            }
        }
    }

    private fun renderVortexCore() {
        if (vortexRadius < 10f) return

        val coreRadius = vortexRadius * 0.15f
        val normalizedProgress = (progress / duration).coerceIn(0f, 1f)
        val pulseIntensity = sin(progress * 15f) * 0.2f + 0.8f

        when (transitionType) {
            TransitionType.OUT -> {
                shapeRenderer.setColor(0.1f, 0.3f, 1f, normalizedProgress * pulseIntensity)
            }
            TransitionType.IN -> {
                shapeRenderer.setColor(0.2f, 1f, 0.3f, (1f - normalizedProgress) * pulseIntensity)
            }
        }

        shapeRenderer.circle(centerX, centerY, coreRadius, 12)

        // Bright inner core
        shapeRenderer.setColor(1f, 1f, 1f, 0.8f * pulseIntensity)
        shapeRenderer.circle(centerX, centerY, coreRadius * 0.4f, 8)
    }

    private fun drawThickLine(x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float) {
        val dx = x2 - x1
        val dy = y2 - y1
        val length = sqrt(dx * dx + dy * dy)

        if (length == 0f) return

        val perpX = -dy / length * thickness / 2f
        val perpY = dx / length * thickness / 2f

        shapeRenderer.triangle(
            x1 + perpX, y1 + perpY,
            x1 - perpX, y1 - perpY,
            x2 - perpX, y2 - perpY
        )
        shapeRenderer.triangle(
            x1 + perpX, y1 + perpY,
            x2 - perpX, y2 - perpY,
            x2 + perpX, y2 + perpY
        )
    }

    fun isFinished(): Boolean {
        return !isActive && progress > 0f
    }

    fun isActive(): Boolean {
        return isActive
    }

    fun getTransitionType(): TransitionType {
        return transitionType
    }

    fun reset() {
        isActive = false
        progress = 0f
        particles.clear()
    }

    fun dispose() {
        shapeRenderer.dispose()
    }
}
