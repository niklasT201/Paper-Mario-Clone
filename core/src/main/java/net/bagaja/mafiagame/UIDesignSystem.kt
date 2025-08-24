package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Central design system for UI styling and theming
 */
object UIDesignSystem {
    // Design constants
    val ACCENT_COLOR = Color(0.2f, 0.7f, 1f, 1f) // Bright blue
    val SECONDARY_COLOR = Color(0.8f, 0.4f, 1f, 1f) // Purple
    val SUCCESS_COLOR = Color(0.2f, 0.9f, 0.4f, 1f) // Green
    val WARNING_COLOR = Color(1f, 0.6f, 0.2f, 1f) // Orange
    val PANEL_COLOR = Color(0.08f, 0.08f, 0.12f, 0.92f)
    val PANEL_BORDER = Color(0.25f, 0.35f, 0.45f, 0.8f)
    val TEXT_PRIMARY = Color(0.95f, 0.95f, 1f, 1f)
    val TEXT_SECONDARY = Color(0.7f, 0.8f, 0.9f, 1f)
    val TEXT_MUTED = Color(0.6f, 0.6f, 0.7f, 1f)

    /**
     * Creates a glassmorphism-style background
     */
    fun createGlassmorphismBackground(): Drawable {
        val pixmap = Pixmap(400, 600, Pixmap.Format.RGBA8888)

        // Create glassmorphism effect
        for (y in 0 until 600) {
            for (x in 0 until 400) {
                val noise = (sin(x * 0.01f) + cos(y * 0.01f)) * 0.02f
                val alpha = 0.85f + noise
                pixmap.setColor(0.06f, 0.08f, 0.15f, alpha)
                pixmap.drawPixel(x, y)
            }
        }

        // Add glowing border
        pixmap.setColor(PANEL_BORDER.r, PANEL_BORDER.g, PANEL_BORDER.b, 0.9f)
        pixmap.drawRectangle(0, 0, 400, 600)
        pixmap.drawRectangle(1, 1, 398, 598)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    /**
     * Creates a gradient title background
     */
    fun createTitleBackground(): Drawable {
        val pixmap = Pixmap(300, 50, Pixmap.Format.RGBA8888)

        // Gradient background
        for (y in 0 until 50) {
            val progress = y / 50f
            val r = ACCENT_COLOR.r * (1f - progress) + SECONDARY_COLOR.r * progress
            val g = ACCENT_COLOR.g * (1f - progress) + SECONDARY_COLOR.g * progress
            val b = ACCENT_COLOR.b * (1f - progress) + SECONDARY_COLOR.b * progress
            pixmap.setColor(r, g, b, 0.3f)
            pixmap.drawLine(0, y, 299, y)
        }

        // Glowing border
        pixmap.setColor(ACCENT_COLOR.r, ACCENT_COLOR.g, ACCENT_COLOR.b, 0.6f)
        pixmap.drawRectangle(0, 0, 300, 50)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    /**
     * Creates a section header background
     */
    fun createSectionHeaderBackground(): Drawable {
        val pixmap = Pixmap(350, 40, Pixmap.Format.RGBA8888)

        // Subtle gradient
        for (y in 0 until 40) {
            val alpha = 0.15f + (y / 40f) * 0.1f
            pixmap.setColor(0.15f, 0.2f, 0.3f, alpha)
            pixmap.drawLine(0, y, 349, y)
        }

        // Accent line at bottom
        pixmap.setColor(ACCENT_COLOR.r, ACCENT_COLOR.g, ACCENT_COLOR.b, 0.7f)
        pixmap.drawLine(0, 38, 349, 38)
        pixmap.drawLine(0, 39, 349, 39)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    /**
     * Creates a tool container background
     */
    fun createToolContainerBackground(): Drawable {
        val pixmap = Pixmap(350, 200, Pixmap.Format.RGBA8888)

        // Dark container with subtle pattern
        pixmap.setColor(0.1f, 0.12f, 0.18f, 0.9f)
        pixmap.fill()

        // Add subtle pattern
        pixmap.setColor(0.15f, 0.17f, 0.23f, 0.5f)
        for (i in 0 until 350 step 20) {
            for (j in 0 until 200 step 20) {
                if ((i + j) % 40 == 0) {
                    pixmap.fillRectangle(i, j, 1, 1)
                }
            }
        }

        // Border
        pixmap.setColor(0.25f, 0.3f, 0.4f, 0.8f)
        pixmap.drawRectangle(0, 0, 350, 200)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    /**
     * Creates a selected tool background with glow effect
     */
    fun createSelectedToolBackground(accentColor: Color): Drawable {
        val pixmap = Pixmap(90, 85, Pixmap.Format.RGBA8888)

        // Glowing selected background
        for (y in 0 until 85) {
            for (x in 0 until 90) {
                val centerX = 45f
                val centerY = 42.5f
                val dist = sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY))
                val glow = max(0f, 1f - dist / 45f)

                val alpha = 0.2f + glow * 0.3f
                pixmap.setColor(accentColor.r, accentColor.g, accentColor.b, alpha)
                pixmap.drawPixel(x, y)
            }
        }

        // Bright border
        pixmap.setColor(accentColor.r, accentColor.g, accentColor.b, 0.9f)
        pixmap.drawRectangle(0, 0, 90, 85)
        pixmap.drawRectangle(1, 1, 88, 83)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    /**
     * Creates a normal (unselected) tool background
     */
    fun createNormalToolBackground(): Drawable {
        val pixmap = Pixmap(90, 85, Pixmap.Format.RGBA8888)

        // Subtle gradient
        for (y in 0 until 85) {
            val alpha = 0.4f + (y / 85f) * 0.1f
            pixmap.setColor(0.18f, 0.2f, 0.25f, alpha)
            pixmap.drawLine(0, y, 89, y)
        }

        // Subtle border
        pixmap.setColor(0.3f, 0.35f, 0.4f, 0.6f)
        pixmap.drawRectangle(0, 0, 90, 85)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    /**
     * Creates an info card background
     */
    fun createInfoCardBackground(): Drawable {
        val pixmap = Pixmap(350, 60, Pixmap.Format.RGBA8888)

        // Card background with subtle glow
        pixmap.setColor(0.1f, 0.15f, 0.2f, 0.85f)
        pixmap.fill()

        // Accent border
        pixmap.setColor(WARNING_COLOR.r, WARNING_COLOR.g, WARNING_COLOR.b, 0.6f)
        pixmap.drawRectangle(0, 0, 350, 60)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    /**
     * Creates an instructions card background
     */
    fun createInstructionsCardBackground(): Drawable {
        val pixmap = Pixmap(380, 300, Pixmap.Format.RGBA8888)

        // Enhanced card background
        pixmap.setColor(0.08f, 0.12f, 0.18f, 0.9f)
        pixmap.fill()

        // Subtle inner glow
        pixmap.setColor(0.12f, 0.16f, 0.22f, 0.7f)
        pixmap.drawRectangle(5, 5, 370, 290)

        // Accent border
        pixmap.setColor(0.2f, 0.4f, 0.6f, 0.8f)
        pixmap.drawRectangle(0, 0, 380, 300)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    /**
     * Creates a stats card background
     */
    fun createStatsCardBackground(): Drawable {
        val pixmap = Pixmap(300, 250, Pixmap.Format.RGBA8888)

        // Enhanced stats background
        pixmap.setColor(0.08f, 0.12f, 0.16f, 0.92f)
        pixmap.fill()

        // Success color accent
        pixmap.setColor(SUCCESS_COLOR.r, SUCCESS_COLOR.g, SUCCESS_COLOR.b, 0.3f)
        pixmap.drawRectangle(0, 0, 300, 250)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }

    /**
     * Creates a stat row background
     */
    fun createStatRowBackground(): Drawable {
        val pixmap = Pixmap(280, 35, Pixmap.Format.RGBA8888)

        // Subtle row background
        pixmap.setColor(0.12f, 0.16f, 0.2f, 0.6f)
        pixmap.fill()

        // Subtle border
        pixmap.setColor(0.2f, 0.25f, 0.3f, 0.4f)
        pixmap.drawRectangle(0, 0, 280, 35)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }
}
