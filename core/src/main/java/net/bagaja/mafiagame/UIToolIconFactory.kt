package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Factory for creating tool icons for the UI
 */
object UIToolIconFactory {

    /**
     * Tool types that can have icons created for them
     */
    enum class ToolType {
        BLOCK, PLAYER, OBJECT, ITEM, CAR, HOUSE, BACKGROUND, PARALLAX, INTERIOR, ENEMY, NPC, PARTICLE, TRIGGER, AUDIO_EMITTER, AREA
    }

    /**
     * Creates an enhanced tool icon texture
     */
    fun createEnhancedToolIcon(tool: ToolType): TextureRegion {
        val pixmap = Pixmap(36, 36, Pixmap.Format.RGBA8888)

        val baseColor = getToolBaseColor(tool)

        // Create gradient background
        for (y in 0 until 36) {
            for (x in 0 until 36) {
                val centerDist = sqrt((x - 18f) * (x - 18f) + (y - 18f) * (y - 18f))
                val gradient = kotlin.math.max(0f, 1f - centerDist / 18f)

                val r = baseColor.r * (0.7f + gradient * 0.3f)
                val g = baseColor.g * (0.7f + gradient * 0.3f)
                val b = baseColor.b * (0.7f + gradient * 0.3f)

                pixmap.setColor(r, g, b, baseColor.a)
                pixmap.drawPixel(x, y)
            }
        }

        // Add enhanced icons based on tool type
        drawToolIcon(pixmap, tool, baseColor)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegion(texture)
    }

    /**
     * Gets the accent color for a tool
     */
    fun getToolAccentColor(tool: ToolType): Color {
        return when (tool) {
            ToolType.BLOCK -> UIDesignSystem.SUCCESS_COLOR
            ToolType.PLAYER -> UIDesignSystem.WARNING_COLOR
            ToolType.OBJECT -> UIDesignSystem.SECONDARY_COLOR
            ToolType.ITEM -> Color(1f, 0.9f, 0.2f, 1f)
            ToolType.CAR -> Color(0.9f, 0.3f, 0.6f, 1f)
            ToolType.HOUSE -> Color(0.5f, 0.9f, 0.3f, 1f)
            ToolType.BACKGROUND -> UIDesignSystem.ACCENT_COLOR
            ToolType.PARALLAX -> Color(0.4f, 0.8f, 0.7f, 1f)
            ToolType.INTERIOR -> Color(0.8f, 0.5f, 0.2f, 1f)
            ToolType.ENEMY -> Color.RED
            ToolType.NPC -> Color(0.2f, 0.8f, 1f, 1f)
            ToolType.PARTICLE -> Color(1f, 0.5f, 0.2f, 1f)
            ToolType.TRIGGER -> Color.valueOf("#20B2AA")
            ToolType.AUDIO_EMITTER -> Color.CYAN
            else -> Color.RED
        }
    }

    /**
     * Gets the display name for a tool
     */
    fun getToolDisplayName(tool: ToolType): String {
        return when (tool) {
            ToolType.BLOCK -> "Block"
            ToolType.PLAYER -> "Player"
            ToolType.OBJECT -> "Object"
            ToolType.ITEM -> "Item"
            ToolType.CAR -> "Car"
            ToolType.HOUSE -> "House"
            ToolType.BACKGROUND -> "Background"
            ToolType.PARALLAX -> "Parallax"
            ToolType.INTERIOR -> "Interior"
            ToolType.ENEMY -> "Enemy"
            ToolType.NPC -> "NPC"
            ToolType.PARTICLE -> "Particle"
            ToolType.TRIGGER -> "Trigger"
            ToolType.AUDIO_EMITTER -> "Audio"
            ToolType.AREA -> "Area"
        }
    }

    private fun getToolBaseColor(tool: ToolType): Color {
        return when (tool) {
            ToolType.BLOCK -> Color(0.3f, 0.8f, 0.3f, 1f)
            ToolType.PLAYER -> Color(1f, 0.7f, 0.2f, 1f)
            ToolType.OBJECT -> Color(0.7f, 0.3f, 0.9f, 1f)
            ToolType.ITEM -> Color(1f, 0.9f, 0.2f, 1f)
            ToolType.CAR -> Color(0.9f, 0.3f, 0.6f, 1f)
            ToolType.HOUSE -> Color(0.5f, 0.9f, 0.3f, 1f)
            ToolType.BACKGROUND -> Color(0.2f, 0.7f, 1f, 1f)
            ToolType.PARALLAX -> Color(0.4f, 0.8f, 0.7f, 1f)
            ToolType.INTERIOR -> Color.BROWN
            ToolType.ENEMY -> Color.RED
            ToolType.NPC -> Color(0.2f, 0.8f, 1f, 1f)
            ToolType.PARTICLE -> Color(1f, 0.5f, 0.2f, 1f)
            ToolType.TRIGGER -> Color.valueOf("#20B2AA")
            ToolType.AUDIO_EMITTER -> Color.CYAN
            else -> Color.GOLD
        }
    }

    private fun drawToolIcon(pixmap: Pixmap, tool: ToolType, baseColor: Color) {
        val shadowColor = Color(0f, 0f, 0f, 0.3f)
        val highlightColor = Color(1f, 1f, 1f, 0.8f)

        when (tool) {
            ToolType.BLOCK -> drawBlockIcon(pixmap, shadowColor, highlightColor, baseColor)
            ToolType.PLAYER -> drawPlayerIcon(pixmap, shadowColor, highlightColor)
            ToolType.OBJECT -> drawObjectIcon(pixmap, shadowColor, highlightColor, baseColor)
            ToolType.ITEM -> drawItemIcon(pixmap, shadowColor, highlightColor)
            ToolType.CAR -> drawCarIcon(pixmap, shadowColor, highlightColor)
            ToolType.HOUSE -> drawHouseIcon(pixmap, shadowColor, highlightColor)
            ToolType.BACKGROUND -> drawBackgroundIcon(pixmap, shadowColor)
            ToolType.PARALLAX -> drawParallaxIcon(pixmap)
            ToolType.INTERIOR -> drawInteriorIcon(pixmap, shadowColor, highlightColor)
            ToolType.ENEMY -> drawEnemyIcon(pixmap, shadowColor, highlightColor)
            ToolType.NPC -> drawNPCIcon(pixmap, shadowColor, highlightColor)
            ToolType.PARTICLE -> drawParticleIcon(pixmap, shadowColor, highlightColor)
            ToolType.TRIGGER -> drawTriggerIcon(pixmap, shadowColor, highlightColor)
            ToolType.AUDIO_EMITTER -> drawTriggerIcon(pixmap, shadowColor, highlightColor)
            else -> drawBlockIcon(pixmap, shadowColor, highlightColor, baseColor)
        }
    }

    private fun drawBlockIcon(pixmap: Pixmap, shadowColor: Color, highlightColor: Color, baseColor: Color) {
        // 3D block effect
        pixmap.setColor(shadowColor)
        pixmap.fillRectangle(10, 10, 16, 16)
        pixmap.setColor(highlightColor)
        pixmap.fillRectangle(8, 8, 16, 16)
        pixmap.setColor(baseColor)
        pixmap.fillRectangle(9, 9, 14, 14)

        // Grid lines
        pixmap.setColor(shadowColor)
        for (i in 9..23 step 5) {
            pixmap.drawLine(i, 9, i, 23)
            pixmap.drawLine(9, i, 23, i)
        }
    }

    private fun drawPlayerIcon(pixmap: Pixmap, shadowColor: Color, highlightColor: Color) {
        // Enhanced player silhouette
        pixmap.setColor(shadowColor)
        pixmap.fillCircle(19, 13, 7) // Head shadow
        pixmap.fillRectangle(13, 19, 12, 15) // Body shadow

        pixmap.setColor(highlightColor)
        pixmap.fillCircle(18, 12, 6) // Head
        pixmap.fillRectangle(12, 18, 12, 14) // Body
        pixmap.fillRectangle(10, 28, 4, 6) // Left leg
        pixmap.fillRectangle(22, 28, 4, 6) // Right leg
        pixmap.fillRectangle(8, 20, 4, 8) // Left arm
        pixmap.fillRectangle(24, 20, 4, 8) // Right arm
    }

    private fun drawObjectIcon(pixmap: Pixmap, shadowColor: Color, highlightColor: Color, baseColor: Color) {
        // Enhanced 3D cube
        pixmap.setColor(shadowColor)
        pixmap.fillRectangle(12, 12, 14, 14)
        pixmap.setColor(highlightColor)
        pixmap.fillRectangle(10, 10, 14, 14)
        pixmap.setColor(baseColor)
        pixmap.fillRectangle(11, 11, 12, 12)

        // Cross pattern
        pixmap.setColor(highlightColor)
        pixmap.fillRectangle(16, 8, 4, 20)
        pixmap.fillRectangle(8, 16, 20, 4)
    }

    private fun drawItemIcon(pixmap: Pixmap, shadowColor: Color, highlightColor: Color) {
        // Enhanced diamond/gem
        pixmap.setColor(shadowColor)
        pixmap.fillTriangle(19, 8, 12, 18, 19, 18)
        pixmap.fillTriangle(19, 8, 19, 18, 26, 18)
        pixmap.fillTriangle(12, 18, 19, 28, 26, 18)

        pixmap.setColor(highlightColor)
        pixmap.fillTriangle(18, 7, 11, 17, 18, 17)
        pixmap.fillTriangle(18, 7, 18, 17, 25, 17)
        pixmap.fillTriangle(11, 17, 18, 27, 25, 17)

        // Sparkle effects
        pixmap.setColor(Color.WHITE)
        pixmap.fillRectangle(15, 12, 2, 2)
        pixmap.fillRectangle(20, 15, 2, 2)
    }

    private fun drawCarIcon(pixmap: Pixmap, shadowColor: Color, highlightColor: Color) {
        // Enhanced car with more detail
        pixmap.setColor(shadowColor)
        pixmap.fillRectangle(8, 18, 22, 10) // Body shadow
        pixmap.fillRectangle(12, 12, 14, 8) // Roof shadow

        pixmap.setColor(highlightColor)
        pixmap.fillRectangle(7, 17, 22, 10) // Main body
        pixmap.fillRectangle(11, 11, 14, 8) // Roof

        // Windows
        pixmap.setColor(Color(0.6f, 0.8f, 1f, 1f))
        pixmap.fillRectangle(13, 13, 4, 4)
        pixmap.fillRectangle(19, 13, 4, 4)

        // Wheels
        pixmap.setColor(Color.BLACK)
        pixmap.fillCircle(13, 28, 3)
        pixmap.fillCircle(23, 28, 3)
        pixmap.setColor(Color.GRAY)
        pixmap.fillCircle(13, 28, 2)
        pixmap.fillCircle(23, 28, 2)
    }

    private fun drawHouseIcon(pixmap: Pixmap, shadowColor: Color, highlightColor: Color) {
        // Enhanced house
        pixmap.setColor(shadowColor)
        pixmap.fillRectangle(9, 18, 20, 16) // House shadow
        pixmap.fillTriangle(18, 8, 8, 18, 28, 18) // Roof shadow

        pixmap.setColor(highlightColor)
        pixmap.fillRectangle(8, 17, 20, 16) // House body
        pixmap.fillTriangle(18, 7, 7, 17, 29, 17) // Roof

        // Door and windows
        pixmap.setColor(Color(0.4f, 0.2f, 0.1f, 1f))
        pixmap.fillRectangle(15, 25, 6, 8) // Door
        pixmap.setColor(Color(0.6f, 0.8f, 1f, 1f))
        pixmap.fillRectangle(10, 20, 4, 4) // Left window
        pixmap.fillRectangle(22, 20, 4, 4) // Right window
    }

    private fun drawBackgroundIcon(pixmap: Pixmap, shadowColor: Color) {
        // Enhanced landscape
        pixmap.setColor(shadowColor)
        pixmap.fillRectangle(6, 26, 26, 8) // Ground shadow

        pixmap.setColor(Color(0.3f, 0.8f, 0.3f, 1f))
        pixmap.fillRectangle(5, 25, 26, 8) // Ground

        // Mountains with gradient
        pixmap.setColor(Color(0.4f, 0.6f, 0.8f, 1f))
        pixmap.fillTriangle(18, 12, 8, 25, 28, 25)
        pixmap.setColor(Color(0.6f, 0.8f, 1f, 1f))
        pixmap.fillTriangle(18, 12, 8, 25, 18, 25)

        // Sun with rays
        pixmap.setColor(Color(1f, 0.9f, 0.3f, 1f))
        pixmap.fillCircle(27, 13, 4)
        pixmap.setColor(Color(1f, 1f, 0.7f, 0.8f))
        for (i in 0..7) {
            val angle = i * 45f * PI / 180f
            val x1 = 27 + cos(angle) * 6
            val y1 = 13 + sin(angle) * 6
            val x2 = 27 + cos(angle) * 8
            val y2 = 13 + sin(angle) * 8
            pixmap.drawLine(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt())
        }
    }

    private fun drawParallaxIcon(pixmap: Pixmap) {
        // Layered landscape icon
        // Far mountains
        pixmap.setColor(Color(0.4f, 0.5f, 0.7f, 0.8f))
        pixmap.fillTriangle(18, 10, 8, 28, 28, 28)
        // Mid hills
        pixmap.setColor(Color(0.5f, 0.7f, 0.4f, 0.9f))
        pixmap.fillTriangle(5, 18, 15, 30, 25, 30)
        pixmap.fillTriangle(15, 20, 25, 32, 32, 32)
        // Near ground
        pixmap.setColor(Color(0.3f, 0.6f, 0.2f, 1f))
        pixmap.fillRectangle(4, 26, 28, 8)
    }

    private fun drawInteriorIcon(pixmap: Pixmap, shadowColor: Color, highlightColor: Color) {
        // A simple chair icon
        pixmap.setColor(shadowColor)
        pixmap.fillRectangle(12, 20, 14, 8) // Seat shadow
        pixmap.fillRectangle(22, 10, 4, 12) // Back shadow

        pixmap.setColor(highlightColor)
        pixmap.fillRectangle(11, 19, 14, 8) // Seat
        pixmap.fillRectangle(21, 9, 4, 12)  // Back
        pixmap.fillRectangle(12, 27, 4, 6) // Front leg
        pixmap.fillRectangle(20, 27, 4, 6) // Back leg
    }

    private fun drawEnemyIcon(pixmap: Pixmap, shadowColor: Color, highlightColor: Color) {
        // Icon for enemy (skull)
        pixmap.setColor(shadowColor)
        pixmap.fillCircle(19, 16, 12)
        pixmap.setColor(highlightColor)
        pixmap.fillCircle(18, 15, 12)
        pixmap.setColor(Color.BLACK)
        pixmap.fillCircle(14, 12, 3) // Left eye
        pixmap.fillCircle(22, 12, 3) // Right eye
        pixmap.fillRectangle(12, 22, 12, 3) // Mouth
    }

    private fun drawNPCIcon(pixmap: Pixmap, shadowColor: Color, highlightColor: Color) {
        // Speech bubble icon
        pixmap.setColor(shadowColor)
        pixmap.fillRectangle(8, 8, 22, 18)
        pixmap.fillTriangle(12, 26, 12, 32, 20, 26)

        pixmap.setColor(highlightColor)
        pixmap.fillRectangle(7, 7, 22, 18)
        pixmap.fillTriangle(11, 25, 11, 31, 19, 25)

        // "..." text
        pixmap.setColor(Color.DARK_GRAY)
        pixmap.fillCircle(12, 16, 2)
        pixmap.fillCircle(18, 16, 2)
        pixmap.fillCircle(24, 16, 2)
    }

    private fun drawParticleIcon(pixmap: Pixmap, shadowColor: Color, highlightColor: Color) {
        // Sparkle/Burst icon
        pixmap.setColor(shadowColor)
        pixmap.fillCircle(19, 19, 8)
        pixmap.setColor(highlightColor)
        pixmap.fillCircle(18, 18, 8)
        // Rays
        for (i in 0..7) {
            val angle = i * 45f * PI / 180f
            val x1 = 18 + cos(angle) * 8
            val y1 = 18 + sin(angle) * 8
            val x2 = 18 + cos(angle) * 14
            val y2 = 18 + sin(angle) * 14
            pixmap.drawLine(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt())
        }
    }

    private fun drawTriggerIcon(pixmap: Pixmap, shadowColor: Color, highlightColor: Color) {
        // Bullseye/Target icon
        pixmap.setColor(shadowColor)
        pixmap.fillCircle(19, 19, 14) // Shadow

        pixmap.setColor(highlightColor)
        pixmap.fillCircle(18, 18, 14) // Outer ring highlight

        pixmap.setColor(getToolBaseColor(ToolType.TRIGGER))
        pixmap.fillCircle(18, 18, 13) // Outer ring main color

        pixmap.setColor(highlightColor)
        pixmap.fillCircle(18, 18, 8) // Inner ring highlight

        pixmap.setColor(getToolBaseColor(ToolType.TRIGGER).cpy().mul(0.7f)) // Darker inner ring
        pixmap.fillCircle(18, 18, 7)

        pixmap.setColor(Color.WHITE)
        pixmap.fillCircle(18, 18, 3) // Center dot
    }
}
