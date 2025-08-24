package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable

/**
 * Factory class for creating and configuring UI skins
 */
object UISkinFactory {

    /**
     * Creates a fully configured skin for the game UI
     */
    fun createSkin(): Skin {
        return try {
            val loadedSkin = Skin(Gdx.files.internal("ui/uiskin.json"))

            // Try to load and set custom font
            try {
                val customFont = BitmapFont(Gdx.files.internal("ui/default.fnt"), false)
                loadedSkin.add("default-font", customFont, BitmapFont::class.java)

                // Update all styles with the custom font
                updateAllStylesWithFont(loadedSkin, customFont)
                addEnhancedLabelStyles(loadedSkin, customFont)

                println("Custom font loaded and all styles patched successfully.")

            } catch (e: Exception) {
                println("Could not load custom font: ${e.message}")
                addEnhancedLabelStyles(loadedSkin, loadedSkin.get(BitmapFont::class.java))
            }

            loadedSkin
        } catch (e: Exception) {
            println("Could not load UI skin, using default")
            createDefaultSkin()
        }
    }

    /**
     * Updates all UI component styles with the provided font
     */
    private fun updateAllStylesWithFont(skin: Skin, font: BitmapFont) {
        // Update Labels
        skin.get(Label.LabelStyle::class.java).font = font

        // Update TextButtons
        try {
            skin.get(TextButton.TextButtonStyle::class.java).font = font
            skin.get("toggle", TextButton.TextButtonStyle::class.java).font = font
        } catch (e: Exception) {
            // TextButton style might not exist in a minimal skin
        }

        // Update TextFields
        try {
            val textFieldStyle = skin.get(TextField.TextFieldStyle::class.java)
            textFieldStyle.font = font
            textFieldStyle.messageFont = font
            textFieldStyle.fontColor = Color.WHITE
        } catch (e: Exception) {
            println("Could not update TextFieldStyle: ${e.message}")
        }

        // Update SelectBoxes
        try {
            val selectBoxStyle = skin.get(SelectBox.SelectBoxStyle::class.java)
            selectBoxStyle.font = font
            selectBoxStyle.listStyle.font = font
        } catch (e: Exception) {
            println("Could not update SelectBoxStyle: ${e.message}")
        }

        // Update Windows/Dialogs
        try {
            skin.get(Window.WindowStyle::class.java).titleFont = font
            skin.get("dialog", Window.WindowStyle::class.java).titleFont = font
        } catch (e: Exception) {
            println("Could not update WindowStyle: ${e.message}")
        }

        // Update CheckBoxes
        try {
            skin.get(CheckBox.CheckBoxStyle::class.java).font = font
        } catch (e: Exception) {
            println("Could not update CheckBoxStyle: ${e.message}")
        }
    }

    /**
     * Adds enhanced label styles to the skin
     */
    private fun addEnhancedLabelStyles(skin: Skin, font: BitmapFont) {
        // Enhanced title with gradient effect
        val titleGradientStyle = Label.LabelStyle()
        titleGradientStyle.font = font
        titleGradientStyle.fontColor = UIDesignSystem.TEXT_PRIMARY
        skin.add("title-gradient", titleGradientStyle)

        // Section headers with accent color
        val sectionHeaderStyle = Label.LabelStyle()
        sectionHeaderStyle.font = font
        sectionHeaderStyle.fontColor = UIDesignSystem.ACCENT_COLOR
        skin.add("section-header", sectionHeaderStyle)

        // Tool names
        val toolNameStyle = Label.LabelStyle()
        toolNameStyle.font = font
        toolNameStyle.fontColor = UIDesignSystem.TEXT_SECONDARY
        skin.add("tool-name", toolNameStyle)

        // Enhanced instruction text
        val instructionEnhancedStyle = Label.LabelStyle()
        instructionEnhancedStyle.font = font
        instructionEnhancedStyle.fontColor = UIDesignSystem.TEXT_SECONDARY
        skin.add("instruction-enhanced", instructionEnhancedStyle)

        // Info card text
        val infoCardStyle = Label.LabelStyle()
        infoCardStyle.font = font
        infoCardStyle.fontColor = UIDesignSystem.WARNING_COLOR
        skin.add("info-card", infoCardStyle)

        // Enhanced stats styles
        val statIconStyle = Label.LabelStyle()
        statIconStyle.font = font
        statIconStyle.fontColor = UIDesignSystem.ACCENT_COLOR
        skin.add("stat-icon", statIconStyle)

        val statKeyEnhancedStyle = Label.LabelStyle()
        statKeyEnhancedStyle.font = font
        statKeyEnhancedStyle.fontColor = UIDesignSystem.TEXT_SECONDARY
        skin.add("stat-key-enhanced", statKeyEnhancedStyle)

        val statValueEnhancedStyle = Label.LabelStyle()
        statValueEnhancedStyle.font = font
        statValueEnhancedStyle.fontColor = UIDesignSystem.SUCCESS_COLOR
        skin.add("stat-value-enhanced", statValueEnhancedStyle)

        // Hint text
        val hintStyle = Label.LabelStyle()
        hintStyle.font = font
        hintStyle.fontColor = UIDesignSystem.TEXT_MUTED
        skin.add("hint", hintStyle)

        // Legacy styles for compatibility
        addLegacyLabelStyles(skin, font)
    }

    /**
     * Adds legacy label styles for backward compatibility
     */
    private fun addLegacyLabelStyles(skin: Skin, font: BitmapFont) {
        val titleStyle = Label.LabelStyle()
        titleStyle.font = font
        titleStyle.fontColor = UIDesignSystem.TEXT_PRIMARY
        skin.add("title", titleStyle)

        val sectionStyle = Label.LabelStyle()
        sectionStyle.font = font
        sectionStyle.fontColor = UIDesignSystem.TEXT_SECONDARY
        skin.add("section", sectionStyle)

        val smallStyle = Label.LabelStyle()
        smallStyle.font = font
        smallStyle.fontColor = UIDesignSystem.TEXT_MUTED
        skin.add("small", smallStyle)

        val instructionStyle = Label.LabelStyle()
        instructionStyle.font = font
        instructionStyle.fontColor = UIDesignSystem.TEXT_SECONDARY
        skin.add("instruction", instructionStyle)

        val statKeyStyle = Label.LabelStyle()
        statKeyStyle.font = font
        statKeyStyle.fontColor = UIDesignSystem.TEXT_SECONDARY
        skin.add("stat-key", statKeyStyle)

        val statValueStyle = Label.LabelStyle()
        statValueStyle.font = font
        statValueStyle.fontColor = UIDesignSystem.TEXT_PRIMARY
        skin.add("stat-value", statValueStyle)
    }

    /**
     * Creates a fallback default skin when the main skin cannot be loaded
     */
    private fun createDefaultSkin(): Skin {
        val skin = Skin()

        // Create a 1x1 white texture
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.WHITE)
        pixmap.fill()
        val texture = Texture(pixmap)
        pixmap.dispose()

        // Try to load custom font first, fallback to default
        val font = try {
            val customFont = BitmapFont(Gdx.files.internal("ui/default.fnt"))
            println("Custom font loaded successfully (fallback)")
            customFont
        } catch (e: Exception) {
            println("Using system default font: ${e.message}")
            BitmapFont()
        }

        // Add font to skin
        skin.add("default-font", font)

        // Create drawable for UI elements
        val buttonTexture = TextureRegion(texture)
        val buttonDrawable = TextureRegionDrawable(buttonTexture)

        // Add background drawable
        skin.add("default-round", buttonDrawable.tint(Color(0.2f, 0.2f, 0.2f, 0.8f)))

        // Label style
        val labelStyle = Label.LabelStyle()
        labelStyle.font = font
        labelStyle.fontColor = Color.WHITE
        skin.add("default", labelStyle)

        addEnhancedLabelStyles(skin, font)

        return skin
    }
}
