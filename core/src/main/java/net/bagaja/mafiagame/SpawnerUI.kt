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

class SpawnerUI(
    private val skin: Skin,
    private val stage: Stage,
    private val particleSystem: ParticleSystem,
    private val itemSystem: ItemSystem,
    private val onRemove: (spawner: GameSpawner) -> Unit
) {
    private val window: Window = Window("Spawner Settings", skin, "dialog")
    private var currentSpawner: GameSpawner? = null

    // --- UI Elements ---
    private val tabButtonTable: Table
    private val particleTabButton: TextButton
    private val itemTabButton: TextButton
    private val weaponTabButton: TextButton

    private val particleSettingsTable: Table
    private val itemSettingsTable: Table
    private val weaponSettingsTable: Table

    // General Settings
    private val intervalField: TextField
    private val minRangeField: TextField
    private val maxRangeField: TextField

    // Particle Settings
    private val effectSelectBox: SelectBox<String>
    private val minParticlesField: TextField
    private val maxParticlesField: TextField
    private val particlePreviewImage: Image

    // Item Settings
    private val itemSelectBox: SelectBox<String>
    private val minItemsField: TextField
    private val maxItemsField: TextField
    private val itemPreviewImage: Image

    // Weapon Settings
    private val weaponSelectBox: SelectBox<String>
    private val minAmmoField: TextField
    private val maxAmmoField: TextField
    private val weaponPreviewImage: Image


    private val applyButton: TextButton
    private val removeButton: TextButton
    private val closeButton: TextButton

    // Cache for preview textures
    private val previewTextures = mutableMapOf<String, Texture>()

    init {
        // Pre-load all preview textures
        ParticleEffectType.entries.forEach { type ->
            try {
                previewTextures[type.name] = Texture(Gdx.files.internal(type.texturePaths.first()))
            } catch (e: Exception) { /* ignore */ }
        }
        ItemType.entries.forEach { type ->
            try {
                previewTextures[type.name] = Texture(Gdx.files.internal(type.texturePath))
            } catch (e: Exception) { /* ignore */ }
        }


        window.isModal = false
        window.isMovable = true
        window.setSize(500f, 480f)
        window.setPosition(stage.width / 2f - 250f, stage.height / 2f - 240f)
        window.align(Align.top)
        window.padTop(40f)

        // --- Tab Buttons ---
        tabButtonTable = Table()
        particleTabButton = TextButton("Particle", skin, "toggle")
        itemTabButton = TextButton("Item", skin, "toggle")
        weaponTabButton = TextButton("Weapon", skin, "toggle")
        val buttonGroup = ButtonGroup(particleTabButton, itemTabButton, weaponTabButton)
        buttonGroup.setMaxCheckCount(1)
        buttonGroup.setMinCheckCount(1) // Always one selected

        tabButtonTable.add(particleTabButton).expandX().fillX()
        tabButtonTable.add(itemTabButton).expandX().fillX()
        tabButtonTable.add(weaponTabButton).expandX().fillX()
        window.add(tabButtonTable).fillX().colspan(2).padBottom(10f).row()

        // --- Settings Tables (one for each tab) ---
        particleSettingsTable = Table()
        itemSettingsTable = Table()
        weaponSettingsTable = Table()

        // --- Build Particle Tab ---
        particlePreviewImage = Image()
        val effectNames = GdxArray(ParticleEffectType.entries.map { it.displayName }.toTypedArray())
        effectSelectBox = SelectBox(skin)
        effectSelectBox.items = effectNames
        particleSettingsTable.add(particlePreviewImage).size(64f).padRight(15f)
        val particleEffectFields = Table()
        particleEffectFields.add(Label("Particle Effect:", skin)).left().row()
        particleEffectFields.add(effectSelectBox).expandX().fillX().row()
        particleSettingsTable.add(particleEffectFields).grow()
        minParticlesField = TextField("", skin)
        maxParticlesField = TextField("", skin)
        particleSettingsTable.row().padTop(10f)
        particleSettingsTable.add(Label("Min Particles:", skin)).left()
        particleSettingsTable.add(minParticlesField).width(80f).left().row()
        particleSettingsTable.add(Label("Max Particles:", skin)).left()
        particleSettingsTable.add(maxParticlesField).width(80f).left().row()

        // --- Build Item Tab ---
        itemPreviewImage = Image()
        val itemNames = GdxArray(ItemType.entries.map { it.displayName }.toTypedArray())
        itemSelectBox = SelectBox(skin)
        itemSelectBox.items = itemNames
        itemSettingsTable.add(itemPreviewImage).size(64f).padRight(15f)
        val itemEffectFields = Table()
        itemEffectFields.add(Label("Item Type:", skin)).left().row()
        itemEffectFields.add(itemSelectBox).expandX().fillX().row()
        itemSettingsTable.add(itemEffectFields).grow()
        minItemsField = TextField("", skin)
        maxItemsField = TextField("", skin)
        itemSettingsTable.row().padTop(10f)
        itemSettingsTable.add(Label("Min Items:", skin)).left()
        itemSettingsTable.add(minItemsField).width(80f).left().row()
        itemSettingsTable.add(Label("Max Items:", skin)).left()
        itemSettingsTable.add(maxItemsField).width(80f).left().row()


        // --- Build Weapon Tab ---
        weaponPreviewImage = Image()
        val weaponNames = GdxArray(ItemType.entries.filter { it.correspondingWeapon != null }.map { it.displayName }.toTypedArray())
        weaponSelectBox = SelectBox(skin)
        weaponSelectBox.items = weaponNames
        weaponSettingsTable.add(weaponPreviewImage).size(64f).padRight(15f)
        val weaponEffectFields = Table()
        weaponEffectFields.add(Label("Weapon Type:", skin)).left().row()
        weaponEffectFields.add(weaponSelectBox).expandX().fillX().row()
        weaponSettingsTable.add(weaponEffectFields).grow()
        minAmmoField = TextField("", skin)
        maxAmmoField = TextField("", skin)
        weaponSettingsTable.row().padTop(10f)
        weaponSettingsTable.add(Label("Min Ammo:", skin)).left()
        weaponSettingsTable.add(minAmmoField).width(80f).left().row()
        weaponSettingsTable.add(Label("Max Ammo:", skin)).left()
        weaponSettingsTable.add(maxAmmoField).width(80f).left().row()

        // Add the tab content tables to the window, but only one will be visible at a time
        window.add(particleSettingsTable).colspan(2).fillX().padBottom(15f).row()
        window.add(itemSettingsTable).colspan(2).fillX().padBottom(15f).row()
        window.add(weaponSettingsTable).colspan(2).fillX().padBottom(15f).row()


        // --- General Settings (visible for all tabs) ---
        val generalTable = Table()
        generalTable.add(Label("GENERAL SETTINGS", skin, "title")).colspan(4).center().padBottom(10f).row()
        intervalField = TextField("", skin)
        minRangeField = TextField("", skin)
        maxRangeField = TextField("", skin)
        generalTable.add(Label("Interval (s):", skin)).padRight(5f)
        generalTable.add(intervalField).width(60f).padRight(15f)
        generalTable.add(Label("Min Range:", skin)).padRight(5f)
        generalTable.add(minRangeField).width(60f).padRight(15f)
        generalTable.add(Label("Max Range:", skin)).padRight(5f)
        generalTable.add(maxRangeField).width(60f)
        window.add(generalTable).colspan(2).padTop(10f).row()


        // --- Buttons ---
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

        // Tab listeners
        particleTabButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) { if (particleTabButton.isChecked) showTab(SpawnerType.PARTICLE) }
        })
        itemTabButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) { if (itemTabButton.isChecked) showTab(SpawnerType.ITEM) }
        })
        weaponTabButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) { if (weaponTabButton.isChecked) showTab(SpawnerType.WEAPON) }
        })

        // Preview image listeners
        effectSelectBox.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { updatePreviewImages() }})
        itemSelectBox.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { updatePreviewImages() }})
        weaponSelectBox.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { updatePreviewImages() }})
    }

    private fun showTab(type: SpawnerType) {
        particleSettingsTable.isVisible = type == SpawnerType.PARTICLE
        itemSettingsTable.isVisible = type == SpawnerType.ITEM
        weaponSettingsTable.isVisible = type == SpawnerType.WEAPON
        window.pack() // Recalculate window size
    }

    fun show(spawner: GameSpawner) {
        currentSpawner = spawner

        // Populate general fields
        intervalField.text = spawner.spawnInterval.toString()
        minRangeField.text = spawner.minSpawnRange.toString()
        maxRangeField.text = spawner.maxSpawnRange.toString()

        // Populate particle fields
        effectSelectBox.selected = spawner.particleEffectType.displayName
        minParticlesField.text = spawner.minParticles.toString()
        maxParticlesField.text = spawner.maxParticles.toString()

        // Populate item fields
        itemSelectBox.selected = spawner.itemType.displayName
        minItemsField.text = spawner.minItems.toString()
        maxItemsField.text = spawner.maxItems.toString()

        // Populate weapon fields
        weaponSelectBox.selected = spawner.weaponItemType.displayName
        minAmmoField.text = spawner.minAmmo.toString()
        maxAmmoField.text = spawner.maxAmmo.toString()

        // Set the correct tab
        when (spawner.spawnerType) {
            SpawnerType.PARTICLE -> particleTabButton.isChecked = true
            SpawnerType.ITEM -> itemTabButton.isChecked = true
            SpawnerType.WEAPON -> weaponTabButton.isChecked = true
        }
        showTab(spawner.spawnerType)
        updatePreviewImages()

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

        // Apply general settings
        spawner.spawnInterval = intervalField.text.toFloatOrNull()?.coerceAtLeast(0.1f) ?: spawner.spawnInterval
        spawner.minSpawnRange = minRangeField.text.toFloatOrNull()?.coerceAtLeast(0f) ?: spawner.minSpawnRange
        spawner.maxSpawnRange = maxRangeField.text.toFloatOrNull()?.coerceAtLeast(spawner.minSpawnRange + 1f) ?: spawner.maxSpawnRange

        // Apply tab-specific settings
        when {
            particleTabButton.isChecked -> {
                spawner.spawnerType = SpawnerType.PARTICLE
                val selectedEffectName = effectSelectBox.selected
                spawner.particleEffectType = ParticleEffectType.entries.find { it.displayName == selectedEffectName } ?: spawner.particleEffectType
                val newMin = minParticlesField.text.toIntOrNull()?.coerceAtLeast(0) ?: spawner.minParticles
                spawner.minParticles = newMin
                spawner.maxParticles = maxParticlesField.text.toIntOrNull()?.coerceAtLeast(newMin) ?: spawner.maxParticles
            }
            itemTabButton.isChecked -> {
                spawner.spawnerType = SpawnerType.ITEM
                val selectedItemName = itemSelectBox.selected
                spawner.itemType = ItemType.entries.find { it.displayName == selectedItemName } ?: spawner.itemType
                val newMin = minItemsField.text.toIntOrNull()?.coerceAtLeast(1) ?: spawner.minItems
                spawner.minItems = newMin
                spawner.maxItems = maxItemsField.text.toIntOrNull()?.coerceAtLeast(newMin) ?: spawner.maxItems
            }
            weaponTabButton.isChecked -> {
                spawner.spawnerType = SpawnerType.WEAPON
                val selectedWeaponName = weaponSelectBox.selected
                spawner.weaponItemType = ItemType.entries.find { it.displayName == selectedWeaponName } ?: spawner.weaponItemType
                val newMin = minAmmoField.text.toIntOrNull()?.coerceAtLeast(1) ?: spawner.minAmmo
                spawner.minAmmo = newMin
                spawner.maxAmmo = maxAmmoField.text.toIntOrNull()?.coerceAtLeast(newMin) ?: spawner.maxAmmo
            }
        }
    }

    private fun updatePreviewImages() {
        // Particle Preview
        val effectType = ParticleEffectType.entries.find { it.displayName == effectSelectBox.selected }
        particlePreviewImage.drawable = effectType?.let { previewTextures[it.name]?.let { tex -> TextureRegionDrawable(tex) } }

        // Item Preview
        val itemType = ItemType.entries.find { it.displayName == itemSelectBox.selected }
        itemPreviewImage.drawable = itemType?.let { previewTextures[it.name]?.let { tex -> TextureRegionDrawable(tex) } }

        // Weapon Preview
        val weaponItemType = ItemType.entries.find { it.displayName == weaponSelectBox.selected }
        weaponPreviewImage.drawable = weaponItemType?.let { previewTextures[it.name]?.let { tex -> TextureRegionDrawable(tex) } }

    }

    fun isVisible(): Boolean = window.isVisible

    fun dispose() {
        previewTextures.values.forEach { it.dispose() }
        previewTextures.clear()
    }
}
