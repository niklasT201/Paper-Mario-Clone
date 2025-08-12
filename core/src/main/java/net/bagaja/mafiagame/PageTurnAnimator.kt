    package net.bagaja.mafiagame

    import com.badlogic.gdx.graphics.Color
    import com.badlogic.gdx.graphics.Pixmap
    import com.badlogic.gdx.graphics.Texture
    import com.badlogic.gdx.graphics.g2d.TextureRegion
    import com.badlogic.gdx.math.Interpolation
    import com.badlogic.gdx.scenes.scene2d.Actor
    import com.badlogic.gdx.scenes.scene2d.Stage
    import com.badlogic.gdx.scenes.scene2d.actions.Actions
    import com.badlogic.gdx.scenes.scene2d.ui.Image
    import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
    import kotlin.math.abs
    import kotlin.math.cos

    /**
     * Page turn animation system for transitioning between newspaper-style UIs
     * Creates a realistic book/newspaper page turning effect
     */
    class PageTurnAnimator(private val stage: Stage) {

        private var isAnimating = false
        private var currentPage: Actor? = null
        private var nextPage: Actor? = null
        private var turningPage: Image? = null
        private var animationTime = 0f
        private val animationDuration = 1.2f

        /**
         * Animates turning from current page to next page
         * @param fromPage The current visible page (will be hidden)
         * @param toPage The page to show after animation
         * @param onComplete Callback when animation finishes
         */
        fun turnPage(fromPage: Actor, toPage: Actor, onComplete: () -> Unit = {}) {
            if (isAnimating) return

            isAnimating = true
            currentPage = fromPage
            nextPage = toPage
            animationTime = 0f

            // Create the turning page effect
            createTurningPage(fromPage)

            // Start animation sequence
            animatePageTurn(onComplete)
        }

        private fun createTurningPage(sourcePage: Actor) {
            // Create a snapshot-like texture of the current page
            // For simplicity, we'll create a newspaper page texture
            val pageTexture = createPageTexture()
            turningPage = Image(pageTexture)

            // Position the turning page to match the source page
            turningPage?.setSize(sourcePage.width, sourcePage.height)
            turningPage?.setPosition(sourcePage.x, sourcePage.y)
            turningPage?.setOrigin(0f, sourcePage.height / 2f) // Left edge as rotation origin

            stage.addActor(turningPage)
            turningPage?.toFront()
        }

        private fun createPageTexture(): Texture {
            val pixmap = Pixmap(420, 520, Pixmap.Format.RGBA8888)

            // Create newspaper page texture similar to your existing ones
            val paperColor = Color.valueOf("#F5F1E8")
            pixmap.setColor(paperColor)
            pixmap.fill()

            // Add some text lines to make it look like a newspaper page
            val textColor = Color.valueOf("#2C1810")
            pixmap.setColor(textColor)

            // Draw horizontal lines to simulate text
            for (y in 50 until 470 step 25) {
                for (x in 30 until 390 step 200) {
                    val lineLength = 150 + (Math.random() * 50).toInt()
                    pixmap.drawLine(x, y, (x + lineLength).coerceAtMost(390), y)
                }
            }

            // Add some "headline" blocks
            pixmap.fillRectangle(30, 100, 200, 8)
            pixmap.fillRectangle(30, 200, 150, 8)
            pixmap.fillRectangle(30, 300, 180, 8)

            // Border
            val borderColor = Color.valueOf("#8B6914")
            pixmap.setColor(borderColor)
            pixmap.drawRectangle(0, 0, 420, 520)
            pixmap.drawRectangle(1, 1, 418, 518)

            val texture = Texture(pixmap)
            pixmap.dispose()
            return texture
        }

        private fun animatePageTurn(onComplete: () -> Unit) {
            // Hide the original page immediately
            currentPage?.isVisible = false

            // Create the animation sequence
            val turningPageActor = turningPage ?: return

            // Phase 1: Page starts to lift and rotate (0.0 to 0.6)
            // Phase 2: Page completes the turn and settles (0.6 to 1.0)

            turningPageActor.addAction(
                Actions.sequence(
                    // Phase 1: Lift and turn the page
                    Actions.parallel(
                        // Rotate from 0° to 180° (flipping over)
                        Actions.rotateBy(180f, animationDuration * 0.7f, Interpolation.sineOut),

                        // Scale effect to simulate perspective
                        Actions.sequence(
                            // First half: scale down as page turns away
                            Actions.scaleTo(0.1f, 1f, animationDuration * 0.35f, Interpolation.sineIn),
                            // Second half: scale back up as page turns toward us
                            Actions.scaleTo(1f, 1f, animationDuration * 0.35f, Interpolation.sineOut)
                        ),

                        // Slight movement to simulate page lifting
                        Actions.sequence(
                            Actions.moveBy(20f, 10f, animationDuration * 0.3f, Interpolation.sineOut),
                            Actions.moveBy(-20f, -10f, animationDuration * 0.4f, Interpolation.sineIn)
                        ),

                        // Color fade to simulate shadow
                        Actions.sequence(
                            Actions.color(Color(0.7f, 0.7f, 0.7f, 1f), animationDuration * 0.35f),
                            Actions.color(Color.WHITE, animationDuration * 0.35f)
                        )
                    ),

                    // Phase 2: Quick settle and reveal new page
                    Actions.run {
                        // At the halfway point, show the next page behind
                        nextPage?.isVisible = true
                        nextPage?.toBack() // Put it behind the turning page

                        // Make turning page transparent to create "see-through" effect
                        turningPageActor.color.a = 0.3f
                    },

                    // Final settling animation
                    Actions.parallel(
                        Actions.fadeOut(animationDuration * 0.3f, Interpolation.fade),
                        Actions.scaleTo(0.8f, 0.8f, animationDuration * 0.3f, Interpolation.sineIn)
                    ),

                    // Cleanup and finish
                    Actions.run {
                        cleanup()
                        onComplete()
                    }
                )
            )
        }

        /**
         * Alternative: Simpler page slide animation
         * More like sliding a newspaper page to the side
         */
        fun slidePageTransition(fromPage: Actor, toPage: Actor, onComplete: () -> Unit = {}) {
            if (isAnimating) return

            isAnimating = true
            currentPage = fromPage
            nextPage = toPage

            // Position the new page off-screen to the right
            toPage.x = stage.width
            toPage.isVisible = true
            toPage.toFront()

            // Animate both pages
            fromPage.addAction(
                Actions.sequence(
                    // Slide current page to the left
                    Actions.moveBy(-stage.width, 0f, 0.8f, Interpolation.pow2),
                    Actions.run {
                        fromPage.isVisible = false
                        // Reset position for next time
                        fromPage.x = stage.width / 2f - fromPage.width / 2f
                    }
                )
            )

            toPage.addAction(
                Actions.sequence(
                    // Slide new page in from the right
                    Actions.moveBy(-stage.width, 0f, 0.8f, Interpolation.pow2),
                    Actions.run {
                        cleanup()
                        onComplete()
                    }
                )
            )
        }

        /**
         * Book-style page curl animation
         * Creates a more realistic page curling effect
         */
        fun curlPageTransition(fromPage: Actor, toPage: Actor, onComplete: () -> Unit = {}) {
            if (isAnimating) return

            isAnimating = true
            currentPage = fromPage
            nextPage = toPage

            // Create curling page effect
            createTurningPage(fromPage)
            val turningPageActor = turningPage ?: return

            // Set up for curl animation
            fromPage.isVisible = false
            toPage.isVisible = true
            toPage.toBack()

            // Create curl effect using skew and rotation
            turningPageActor.setOrigin(turningPageActor.width, 0f) // Bottom-right origin

            turningPageActor.addAction(
                Actions.sequence(
                    // Curl starts from bottom-right corner
                    Actions.parallel(
                        // Rotate as if curling
                        Actions.rotateBy(-45f, 0.4f, Interpolation.sineOut),
                        // Scale to create curl perspective
                        Actions.scaleTo(0.7f, 0.9f, 0.4f, Interpolation.sineOut),
                        // Move to simulate curl motion
                        Actions.moveBy(-50f, 30f, 0.4f, Interpolation.sineOut)
                    ),

                    // Continue curl and fade
                    Actions.parallel(
                        Actions.rotateBy(-90f, 0.5f, Interpolation.sineIn),
                        Actions.scaleTo(0.1f, 0.8f, 0.5f, Interpolation.sineIn),
                        Actions.fadeOut(0.5f, Interpolation.sineIn),
                        Actions.moveBy(-100f, 20f, 0.5f, Interpolation.sineIn)
                    ),

                    Actions.run {
                        cleanup()
                        onComplete()
                    }
                )
            )
        }

        private fun cleanup() {
            isAnimating = false
            turningPage?.remove()
            turningPage = null
            currentPage = null
            nextPage = null
        }

        fun isAnimating(): Boolean = isAnimating
    }

    // Extension for UIManager to use the page turn animator
    class PageTurnUIManager(private val stage: Stage) {

        private val pageTurnAnimator = PageTurnAnimator(stage)

        fun animateToVisualSettings(pauseMenu: Actor, visualSettings: Actor) {
            if (pageTurnAnimator.isAnimating()) return

            // Choose your preferred animation style:

            // Option 1: Full page turn (most realistic)
            pageTurnAnimator.turnPage(pauseMenu, visualSettings) {
                // Animation complete
            }

            // Option 2: Simple slide (faster, still nice)
            // pageTurnAnimator.slidePageTransition(pauseMenu, visualSettings)

            // Option 3: Page curl (elegant)
            // pageTurnAnimator.curlPageTransition(pauseMenu, visualSettings)
        }

        fun animateBackToPauseMenu(visualSettings: Actor, pauseMenu: Actor) {
            if (pageTurnAnimator.isAnimating()) return

            // Animate back - could be same or different animation
            pageTurnAnimator.turnPage(visualSettings, pauseMenu) {
                // Animation complete
            }
        }
    }
