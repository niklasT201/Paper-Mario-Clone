package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align

class ParticleSelectionUI(
    private val particleSystem: ParticleSystem,
    private val skin: Skin,
    private val stage: Stage
) {
    private lateinit var selectionTable: Table
    private lateinit var effectNameLabel: Label
    private lateinit var effectPreviewImage: Image
    private val textures = mutableMapOf<ParticleEffectType, Texture>()

    fun initialize() {
        // Pre-load textures for preview
        ParticleEffectType.entries.forEach { type ->
            try {
                textures[type] = Texture(Gdx.files.internal(type.texturePaths.first()))
            } catch (e: Exception) {
                println("Warning: Could not load preview texture for ${type.displayName}")
            }
        }

        selectionTable = Table(skin)
        selectionTable.background = skin.getDrawable("default-round")
        selectionTable.pad(20f)
        selectionTable.setSize(300f, 150f)
        selectionTable.setPosition(
            (stage.width - selectionTable.width) / 2f,
            stage.height - selectionTable.height - 20f
        )
        selectionTable.isVisible = false

        val titleLabel = Label("Particle Effect (X)", skin, "title")
        titleLabel.setAlignment(Align.center)
        selectionTable.add(titleLabel).colspan(2).expandX().fillX().padBottom(10f).row()

        effectPreviewImage = Image()
        selectionTable.add(effectPreviewImage).size(64f, 64f).padRight(15f)

        effectNameLabel = Label("", skin)
        effectNameLabel.setFontScale(1.2f)
        selectionTable.add(effectNameLabel).expand().fill().left()

        stage.addActor(selectionTable)
        update()
    }

    fun show() {
        selectionTable.isVisible = true
    }

    fun hide() {
        selectionTable.isVisible = false
    }

    fun update() {
        val selectedEffect = particleSystem.currentSelectedEffect
        effectNameLabel.setText(selectedEffect.displayName)

        textures[selectedEffect]?.let {
            effectPreviewImage.drawable = Image(it).drawable
        } ?: run {
            effectPreviewImage.drawable = null // Clear image if no texture
        }
    }

    fun dispose() {
        textures.values.forEach { it.dispose() }
    }
}
