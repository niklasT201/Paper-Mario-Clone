package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array as GdxArray

class ParticleSpawnerUI(
    private val skin: Skin,
    private val stage: Stage,
    private val particleSystem: ParticleSystem,
    private val onRemove: (spawner: GameParticleSpawner) -> Unit
) {
    private val window: Window = Window("Particle Spawner Settings", skin, "dialog")
    private var currentSpawner: GameParticleSpawner? = null

    // UI elements
    private val effectSelectBox: SelectBox<String>
    private val minParticlesField: TextField
    private val maxParticlesField: TextField
    private val intervalField: TextField
    private val previewImage: Image
    private val applyButton: TextButton
    private val removeButton: TextButton
    private val closeButton: TextButton

    // Cache for preview textures to avoid reloading
    private val previewTextures = mutableMapOf<ParticleEffectType, Texture>()

    init {
        // Pre-load all preview textures once
        ParticleEffectType.entries.forEach { type ->
            try {
                previewTextures[type] = Texture(Gdx.files.internal(type.texturePaths.first()))
            } catch (e: Exception) {
                println("Could not load preview texture for ${type.displayName}: ${e.message}")
            }
        }

        window.isModal = false
        window.isMovable = true
        window.setSize(450f, 380f)
        window.setPosition(stage.width / 2f - 225f, stage.height / 2f - 190f)
        window.align(Align.top)
        window.padTop(40f) // Space for the title bar

        // Preview and Effect Selection
        val topTable = Table()
        previewImage = Image()
        topTable.add(previewImage).size(64f).padRight(15f)

        val effectTable = Table()
        val effectNames = GdxArray(ParticleEffectType.entries.map { it.displayName }.toTypedArray())
        effectSelectBox = SelectBox(skin)
        effectSelectBox.items = effectNames
        effectTable.add(Label("Particle Effect:", skin)).left().row()
        effectTable.add(effectSelectBox).expandX().fillX()
        topTable.add(effectTable).grow()
        window.add(topTable).colspan(2).fillX().padBottom(15f).row()

        // Min/Max Particles
        minParticlesField = TextField("", skin)
        maxParticlesField = TextField("", skin)
        val minMaxTable = Table()
        minMaxTable.add(Label("Min Particles:", skin)).padRight(10f)
        minMaxTable.add(minParticlesField).width(80f)
        minMaxTable.add(Label("Max Particles:", skin)).padLeft(20f).padRight(10f)
        minMaxTable.add(maxParticlesField).width(80f)
        window.add(minMaxTable).colspan(2).padTop(10f).left().row()

        // Spawn Interval
        intervalField = TextField("", skin)
        val intervalTable = Table()
        intervalTable.add(Label("Spawn Interval (sec):", skin)).left().padRight(10f)
        intervalTable.add(intervalField).width(80f).left()
        window.add(intervalTable).colspan(2).padTop(10f).left().row()

        applyButton = TextButton("Apply", skin)
        removeButton = TextButton("Remove Spawner", skin)
        removeButton.color.set(1f, 0.6f, 0.6f, 1f)
        closeButton = TextButton("Close", skin)
        val buttonTable = Table()
        buttonTable.add(applyButton).pad(10f)
        buttonTable.add(removeButton).pad(10f)
        buttonTable.add(closeButton).pad(10f)
        window.add(buttonTable).colspan(2).expandY().bottom().padBottom(10f)

        window.isVisible = false
        stage.addActor(window)

        addListeners()
    }

    private fun addListeners() {
        applyButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                applyChanges()
                hide()
            }
        })
        removeButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                currentSpawner?.let { onRemove(it) }
                hide()
            }
        })
        closeButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                hide()
            }
        })
        effectSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                updatePreviewImage()
            }
        })
    }

    fun show(spawner: GameParticleSpawner) {
        currentSpawner = spawner
        effectSelectBox.selected = spawner.particleEffectType.displayName
        minParticlesField.text = spawner.minParticles.toString()
        maxParticlesField.text = spawner.maxParticles.toString()
        intervalField.text = spawner.spawnInterval.toString()

        updatePreviewImage()
        window.toFront()
        window.isVisible = true
    }

    fun hide() {
        window.isVisible = false
        currentSpawner = null
        stage.unfocusAll()
    }

    private fun applyChanges() {
        val spawner = currentSpawner ?: return

        val selectedEffectName = effectSelectBox.selected
        spawner.particleEffectType = ParticleEffectType.entries.find { it.displayName == selectedEffectName } ?: spawner.particleEffectType

        // Allow min particles to be 0
        val newMin = minParticlesField.text.toIntOrNull()?.coerceAtLeast(0) ?: spawner.minParticles
        spawner.minParticles = newMin
        spawner.maxParticles = maxParticlesField.text.toIntOrNull()?.coerceAtLeast(newMin) ?: spawner.maxParticles

        spawner.spawnInterval = intervalField.text.toFloatOrNull()?.coerceAtLeast(0.05f) ?: spawner.spawnInterval

        println("Updated Spawner #${spawner.id}: Effect=${spawner.particleEffectType.name}, Count=${spawner.minParticles}-${spawner.maxParticles}, Interval=${spawner.spawnInterval}")
    }

    private fun updatePreviewImage() {
        val selectedEffectName = effectSelectBox.selected
        val effectType = ParticleEffectType.entries.find { it.displayName == selectedEffectName }
        if (effectType != null) {
            val texture = previewTextures[effectType]
            if (texture != null) {
                previewImage.drawable = TextureRegionDrawable(texture)
            } else {
                previewImage.drawable = null
            }
        }
    }

    fun isVisible(): Boolean = window.isVisible

    fun dispose() {
        previewTextures.values.forEach { it.dispose() }
        previewTextures.clear()
    }
}
