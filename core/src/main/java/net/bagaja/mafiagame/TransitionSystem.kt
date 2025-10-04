package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation

class TransitionSystem {

    private lateinit var spriteBatch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var camera: OrthographicCamera

    // Door textures
    private lateinit var doorClosedTexture: Texture
    private lateinit var doorHalfOpenTexture: Texture
    private lateinit var doorFullyOpenTexture: Texture

    // Animation state
    private var isActive = false
    private var progress = 0f
    private var duration = 2.5f
    private var transitionType = TransitionType.OUT

    // Phase tracking
    private var currentPhase = TransitionPhase.FADE_TO_BLACK
    private var phaseProgress = 0f

    // Visual parameters
    private var blackScreenAlpha = 0f
    private var doorAlpha = 0f
    private var doorScale = 1f
    private val targetDoorScale = 1.8f

    // Animation mode
    var useSimpleFade = false // Set to true for black fade only, false for door animation

    enum class TransitionType {
        OUT,    // Leaving current area
        IN      // Entering new area
    }

    enum class TransitionPhase {
        FADE_TO_BLACK,      // Screen fades to black
        DOOR_ANIMATION,     // Door opens on black background
        COMPLETE
    }

    fun create(camera: OrthographicCamera) {
        this.camera = camera
        spriteBatch = SpriteBatch()
        shapeRenderer = ShapeRenderer()

        // Only load door textures if not using simple fade
        if (!useSimpleFade) {
            // Load door textures
            doorClosedTexture = Texture("textures/interior/door.png")
            doorHalfOpenTexture = Texture("textures/interior/half_open_door.png")
            doorFullyOpenTexture = Texture("textures/interior/fully_open_door.png")

            // Set texture filtering for better scaling
            doorClosedTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            doorHalfOpenTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            doorFullyOpenTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
    }

    fun startOutTransition(duration: Float = 2.5f) {
        if (isActive) return

        this.transitionType = TransitionType.OUT
        this.isActive = true
        this.progress = 0f
        this.duration = duration
        this.currentPhase = TransitionPhase.FADE_TO_BLACK
        this.phaseProgress = 0f
        this.blackScreenAlpha = 0f
        this.doorAlpha = 0f
        this.doorScale = 1f
    }

    fun startInTransition(duration: Float = 2.5f) {
        if (isActive) return

        this.transitionType = TransitionType.IN
        this.isActive = true
        this.progress = 0f
        this.duration = duration
        this.currentPhase = TransitionPhase.FADE_TO_BLACK
        this.phaseProgress = 0f
        this.blackScreenAlpha = 0f
        this.doorAlpha = 0f
        this.doorScale = 1f
    }

    fun update(deltaTime: Float) {
        if (!isActive) return

        progress += deltaTime
        val normalizedProgress = (progress / duration).coerceIn(0f, 1f)

        if (useSimpleFade) {
            // Simple black fade in and out - much faster now
            currentPhase = if (normalizedProgress < 0.25f) {
                TransitionPhase.FADE_TO_BLACK
            } else {
                TransitionPhase.DOOR_ANIMATION
            }

            if (normalizedProgress < 0.5f) {
                // Fade to black quickly (first half) - reach full black at 25% (about 0.6 seconds)
                val fadeInProgress = (normalizedProgress / 0.25f).coerceIn(0f, 1f)
                blackScreenAlpha = Interpolation.pow2In.apply(0f, 1f, fadeInProgress)
            } else {
                // Fade from black (second half)
                blackScreenAlpha = 1f - Interpolation.smooth.apply(0f, 1f, (normalizedProgress - 0.5f) / 0.5f)
            }
            doorAlpha = 0f
        } else {
            // Original door animation
            // Phase 1: Fade to black (first ~28% = 2 seconds out of 7)
            if (normalizedProgress < 0.286f) {
                currentPhase = TransitionPhase.FADE_TO_BLACK
                phaseProgress = normalizedProgress / 0.286f
                blackScreenAlpha = Interpolation.smooth.apply(0f, 1f, phaseProgress)
                doorAlpha = 0f
            }
            // Phase 2: Door opening animation (remaining ~72% = 5 seconds)
            else {
                currentPhase = TransitionPhase.DOOR_ANIMATION
                phaseProgress = (normalizedProgress - 0.286f) / 0.714f
                blackScreenAlpha = 1f

                // Door fades in, scales up slowly, then fades out
                if (phaseProgress < 0.15f) {
                    // Fade in door
                    doorAlpha = Interpolation.smooth.apply(0f, 1f, phaseProgress / 0.15f)
                    doorScale = 1f
                } else if (phaseProgress < 0.85f) {
                    // Door fully visible and scaling slowly
                    doorAlpha = 1f
                    val scaleProgress = (phaseProgress - 0.15f) / 0.7f
                    doorScale = Interpolation.smooth.apply(1f, targetDoorScale, scaleProgress)
                } else {
                    // Fade out door
                    val fadeProgress = (phaseProgress - 0.85f) / 0.15f
                    doorAlpha = 1f - Interpolation.smooth.apply(0f, 1f, fadeProgress)
                    doorScale = targetDoorScale
                }
            }
        }

        if (progress >= duration) {
            isActive = false
            currentPhase = TransitionPhase.COMPLETE
            progress = duration
        }
    }

    fun render() {
        if (!isActive) return

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        // Always draw black screen
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, blackScreenAlpha)
        shapeRenderer.rect(0f, 0f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        shapeRenderer.end()

        // Draw door only if not using simple fade and in door animation phase
        if (!useSimpleFade && currentPhase == TransitionPhase.DOOR_ANIMATION && doorAlpha > 0.01f) {
            // Determine which door texture to use based on animation progress
            val currentTexture = when {
                phaseProgress < 0.33f -> doorClosedTexture
                phaseProgress < 0.66f -> doorHalfOpenTexture
                else -> doorFullyOpenTexture
            }

            spriteBatch.projectionMatrix = camera.combined
            spriteBatch.begin()

            val screenWidth = Gdx.graphics.width.toFloat()
            val screenHeight = Gdx.graphics.height.toFloat()

            // Base scale on the widest texture (closed door = 465px) to maintain consistent size
            val referenceWidth = 465f
            val referenceHeight = 789f

            // Calculate the actual scale needed for this texture to match the reference width
            val textureWidthRatio = referenceWidth / currentTexture.width.toFloat()
            val textureHeightRatio = referenceHeight / currentTexture.height.toFloat()

            // Apply the door scale animation on top of the size normalization
            val doorWidth = referenceWidth * doorScale
            val doorHeight = referenceHeight * doorScale

            // Center horizontally and vertically
            val doorX = (screenWidth - doorWidth) / 2f
            val doorY = (screenHeight - doorHeight) / 2f

            spriteBatch.setColor(1f, 1f, 1f, doorAlpha)
            spriteBatch.draw(currentTexture, doorX, doorY, doorWidth, doorHeight)
            spriteBatch.setColor(1f, 1f, 1f, 1f)

            spriteBatch.end()
        }

        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    fun getCurrentPhase(): TransitionPhase {
        return currentPhase
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
        currentPhase = TransitionPhase.FADE_TO_BLACK
        phaseProgress = 0f
        blackScreenAlpha = 0f
        doorAlpha = 0f
        doorScale = 1f
    }

    fun dispose() {
        spriteBatch.dispose()
        shapeRenderer.dispose()
        if (!useSimpleFade) {
            doorClosedTexture.dispose()
            doorHalfOpenTexture.dispose()
            doorFullyOpenTexture.dispose()
        }
    }
}
