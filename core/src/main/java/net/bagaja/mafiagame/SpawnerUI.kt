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
    private val onRemove: (spawner: GameSpawner) -> Unit
) {
    private val window: Window = Window("Spawner Configuration", skin, "dialog")
    private var currentSpawner: GameSpawner? = null

    // --- UI Elements ---
    private val particleTabButton: TextButton
    private val itemTabButton: TextButton
    private val weaponTabButton: TextButton
    private val enemyTabButton: TextButton
    private val npcTabButton: TextButton
    private val carTabButton: TextButton

    private val particleSettingsTable: Table
    private val itemSettingsTable: Table
    private val weaponSettingsTable: Table
    private val enemySettingsTable: Table
    private val npcSettingsTable: Table
    private val carSettingsTable: Table

    // General Settings
    private val spawnerModeSelectBox: SelectBox<String>
    private val intervalField: TextField
    private val minRangeField: TextField
    private val maxRangeField: TextField
    private val spawnOnlyWhenGoneCheckbox: CheckBox

    // Particle Settings
    private val effectSelectBox: SelectBox<String>
    private val minParticlesField: TextField
    private val maxParticlesField: TextField
    private val particlePreviewImage: Image // Re-added for clarity

    // Item Settings
    private val itemSelectBox: SelectBox<String>
    private val minItemsField: TextField
    private val maxItemsField: TextField
    private val itemPreviewImage: Image // Re-added for clarity

    // Weapon Settings
    private val weaponSelectBox: SelectBox<String>
    private val weaponPreviewImage: Image // Re-added for clarity
    private val ammoModeSelectBox: SelectBox<String>
    private val setAmmoField: TextField
    private val randomMinAmmoField: TextField
    private val randomMaxAmmoField: TextField
    private val setAmmoTable: Table
    private val randomAmmoTable: Table

    // Enemy Settings
    private val enemyTypeSelectBox: SelectBox<String>
    private val enemyBehaviorSelectBox: SelectBox<String>
    private val enemyHealthSettingSelectBox: SelectBox<String>
    private val enemyCustomHealthField: TextField
    private val enemyRandomHealthTable: Table
    private val enemyMinHealthField: TextField
    private val enemyMaxHealthField: TextField
    private val enemyInitialWeaponSelectBox: SelectBox<String>
    private val enemyWeaponCollectionPolicySelectBox: SelectBox<String>
    private val enemyCanCollectItemsCheckbox: CheckBox
    private val enemyInitialMoneyField: TextField

    // NPC Settings
    private val npcTypeSelectBox: SelectBox<String>
    private val npcBehaviorSelectBox: SelectBox<String>
    private val npcCanCollectItemsCheckbox: CheckBox
    private val npcIsHonestCheckbox: CheckBox

    private val applyButton: TextButton
    private val removeButton: TextButton
    private val closeButton: TextButton

    // Cache for preview textures
    private val previewTextures = mutableMapOf<String, Texture>()

    private val carTypeSelectBox: SelectBox<String>
    private val carLockedCheckbox: CheckBox
    private val carDriverTypeSelectBox: SelectBox<String>
    private val carEnemyDriverSelectBox: SelectBox<String>
    private val carNpcDriverSelectBox: SelectBox<String>

    init {
        // Pre-load all preview textures for performance. This was the missing part.
        ParticleEffectType.entries.forEach { type ->
            try {
                previewTextures[type.name] = Texture(Gdx.files.internal(type.texturePaths.first()))
            } catch (e: Exception) { /* ignore missing textures */ }
        }
        ItemType.entries.forEach { type ->
            try {
                previewTextures[type.name] = Texture(Gdx.files.internal(type.texturePath))
            } catch (e: Exception) { /* ignore missing textures */ }
        }
        // --- END OF RESTORED CODE ---

        window.isModal = false
        window.isMovable = true
        window.setSize(550f, 680f)
        window.setPosition(stage.width / 2f - 275f, stage.height / 2f - 340f)
        window.align(Align.top)
        window.padTop(40f)

        val tabButtonTable = Table()
        particleTabButton = TextButton("Particle", skin, "toggle")
        itemTabButton = TextButton("Item", skin, "toggle")
        weaponTabButton = TextButton("Weapon", skin, "toggle")
        enemyTabButton = TextButton("Enemy", skin, "toggle")
        npcTabButton = TextButton("NPC", skin, "toggle")
        carTabButton = TextButton("Car", skin, "toggle")
        val buttonGroup = ButtonGroup(particleTabButton, itemTabButton, weaponTabButton, enemyTabButton, npcTabButton, carTabButton)
        buttonGroup.setMaxCheckCount(1); buttonGroup.setMinCheckCount(1)
        tabButtonTable.add(particleTabButton).expandX().fillX()
        tabButtonTable.add(itemTabButton).expandX().fillX()
        tabButtonTable.add(weaponTabButton).expandX().fillX()
        tabButtonTable.add(enemyTabButton).expandX().fillX()
        tabButtonTable.add(npcTabButton).expandX().fillX()
        tabButtonTable.add(carTabButton).expandX().fillX()
        window.add(tabButtonTable).fillX().colspan(2).padBottom(10f).row()

        particleSettingsTable = Table(); itemSettingsTable = Table(); weaponSettingsTable = Table()
        enemySettingsTable = Table(); npcSettingsTable = Table()
        carSettingsTable = Table()

        // --- Build Particle Tab with Preview Image ---
        particlePreviewImage = Image()
        val effectNames = GdxArray(ParticleEffectType.entries.map { it.displayName }.toTypedArray())
        effectSelectBox = SelectBox(skin); effectSelectBox.items = effectNames
        minParticlesField = TextField("", skin); maxParticlesField = TextField("", skin)
        val particleFields = Table()
        particleFields.add(Label("Effect:", skin)).left().row(); particleFields.add(effectSelectBox).growX().row()
        particleSettingsTable.add(particlePreviewImage).size(64f).pad(10f)
        particleSettingsTable.add(particleFields).growX().row()
        particleSettingsTable.add(Label("Min Count:", skin)).left(); particleSettingsTable.add(minParticlesField).width(80f).left().row()
        particleSettingsTable.add(Label("Max Count:", skin)).left(); particleSettingsTable.add(maxParticlesField).width(80f).left().row()

        // --- Build Item Tab with Preview Image ---
        itemPreviewImage = Image()
        val itemNames = GdxArray(ItemType.entries.map { it.displayName }.toTypedArray())
        itemSelectBox = SelectBox(skin); itemSelectBox.items = itemNames
        minItemsField = TextField("", skin); maxItemsField = TextField("", skin)
        val itemFields = Table()
        itemFields.add(Label("Item:", skin)).left().row(); itemFields.add(itemSelectBox).growX().row()
        itemSettingsTable.add(itemPreviewImage).size(64f).pad(10f)
        itemSettingsTable.add(itemFields).growX().row()
        itemSettingsTable.add(Label("Min Count:", skin)).left(); itemSettingsTable.add(minItemsField).width(80f).left().row()
        itemSettingsTable.add(Label("Max Count:", skin)).left(); itemSettingsTable.add(maxItemsField).width(80f).left().row()

        // --- Build Weapon Tab with Preview Image ---
        weaponPreviewImage = Image()
        val weaponNames = GdxArray(ItemType.entries.filter { it.correspondingWeapon != null }.map { it.displayName }.toTypedArray())
        weaponSelectBox = SelectBox(skin); weaponSelectBox.items = weaponNames
        val weaponFields = Table()
        weaponFields.add(Label("Weapon:", skin)).left().row(); weaponFields.add(weaponSelectBox).growX().row()
        weaponSettingsTable.add(weaponPreviewImage).size(64f).pad(10f)
        weaponSettingsTable.add(weaponFields).growX().row()

        val ammoModes = GdxArray(AmmoSpawnMode.entries.map { it.name }.toTypedArray())
        ammoModeSelectBox = SelectBox(skin); ammoModeSelectBox.items = ammoModes
        setAmmoField = TextField("", skin); randomMinAmmoField = TextField("", skin); randomMaxAmmoField = TextField("", skin)
        setAmmoTable = Table(); randomAmmoTable = Table()
        setAmmoTable.add(Label("Set Amount:", skin)).padRight(10f); setAmmoTable.add(setAmmoField).width(80f)
        randomAmmoTable.add(Label("Min:", skin)).padRight(5f); randomAmmoTable.add(randomMinAmmoField).width(60f).padRight(10f)
        randomAmmoTable.add(Label("Max:", skin)).padRight(5f); randomAmmoTable.add(randomMaxAmmoField).width(60f)
        val ammoSettingsTable = Table()
        ammoSettingsTable.add(Label("Ammo Mode:", skin)).left().padRight(10f); ammoSettingsTable.add(ammoModeSelectBox).left().row()
        ammoSettingsTable.add(setAmmoTable).colspan(2).left().padTop(5f).row()
        ammoSettingsTable.add(randomAmmoTable).colspan(2).left().padTop(5f).row()
        weaponSettingsTable.add(ammoSettingsTable).colspan(2).left().padTop(10f).row()

        // --- Build Enemy Tab ---
        enemyTypeSelectBox = SelectBox(skin); enemyTypeSelectBox.items = GdxArray(EnemyType.entries.map { it.displayName }.toTypedArray())
        enemyBehaviorSelectBox = SelectBox(skin); enemyBehaviorSelectBox.items = GdxArray(EnemyBehavior.entries.map { it.displayName }.toTypedArray())
        enemySettingsTable.add(Label("Type:", skin)).left(); enemySettingsTable.add(enemyTypeSelectBox).growX().row()
        enemySettingsTable.add(Label("Behavior:", skin)).left(); enemySettingsTable.add(enemyBehaviorSelectBox).growX().row()

        enemyHealthSettingSelectBox = SelectBox(skin); enemyHealthSettingSelectBox.items = GdxArray(HealthSetting.entries.map { it.displayName }.toTypedArray())
        enemyCustomHealthField = TextField("", skin); enemyMinHealthField = TextField("", skin); enemyMaxHealthField = TextField("", skin)
        enemyRandomHealthTable = Table()
        enemyRandomHealthTable.add(Label("Min:", skin)).padRight(5f); enemyRandomHealthTable.add(enemyMinHealthField).width(60f).padRight(10f)
        enemyRandomHealthTable.add(Label("Max:", skin)).padRight(5f); enemyRandomHealthTable.add(enemyMaxHealthField).width(60f)
        val healthSettingsTable = Table()
        healthSettingsTable.add(Label("Health:", skin)).left().padRight(10f); healthSettingsTable.add(enemyHealthSettingSelectBox).left().row()
        healthSettingsTable.add(Label("Custom HP:", skin)).left().padRight(10f); healthSettingsTable.add(enemyCustomHealthField).width(80f).left().row()
        healthSettingsTable.add(enemyRandomHealthTable).colspan(2).left().padTop(5f).row()
        enemySettingsTable.add(healthSettingsTable).colspan(2).left().padTop(10f).row()

        enemyInitialWeaponSelectBox = SelectBox(skin); enemyInitialWeaponSelectBox.items = GdxArray(WeaponType.entries.map { it.displayName }.toTypedArray())
        enemyWeaponCollectionPolicySelectBox = SelectBox(skin); enemyWeaponCollectionPolicySelectBox.items = GdxArray(WeaponCollectionPolicy.entries.map { it.displayName }.toTypedArray())
        enemyCanCollectItemsCheckbox = CheckBox(" Can Collect Items (Money, etc.)", skin)
        enemyInitialMoneyField = TextField("0", skin)
        val inventoryTable = Table()
        inventoryTable.add(Label("Initial Weapon:", skin)).left(); inventoryTable.add(enemyInitialWeaponSelectBox).growX().row()
        inventoryTable.add(Label("Pickup Policy:", skin)).left(); inventoryTable.add(enemyWeaponCollectionPolicySelectBox).growX().row()
        inventoryTable.add(enemyCanCollectItemsCheckbox).colspan(2).left().padTop(5f).row()
        inventoryTable.add(Label("Initial Money:", skin)).left(); inventoryTable.add(enemyInitialMoneyField).width(80f).left().row()
        enemySettingsTable.add(inventoryTable).colspan(2).left().padTop(10f).row()

        // --- Build NPC Tab ---
        npcTypeSelectBox = SelectBox(skin); npcTypeSelectBox.items = GdxArray(NPCType.entries.map { it.displayName }.toTypedArray())
        npcBehaviorSelectBox = SelectBox(skin); npcBehaviorSelectBox.items = GdxArray(NPCBehavior.entries.map { it.displayName }.toTypedArray())
        npcCanCollectItemsCheckbox = CheckBox(" Can Collect Items", skin)
        npcIsHonestCheckbox = CheckBox(" Is Honest (Returns Items)", skin)
        npcSettingsTable.add(Label("Type:", skin)).left(); npcSettingsTable.add(npcTypeSelectBox).growX().row()
        npcSettingsTable.add(Label("Behavior:", skin)).left(); npcSettingsTable.add(npcBehaviorSelectBox).growX().row()
        npcSettingsTable.add(npcCanCollectItemsCheckbox).colspan(2).left().padTop(10f).row()
        npcSettingsTable.add(npcIsHonestCheckbox).colspan(2).left().row()

        // --- Build Car Tab ---
        carTypeSelectBox = SelectBox(skin); carTypeSelectBox.items = GdxArray(CarType.entries.map { it.displayName }.toTypedArray())
        carLockedCheckbox = CheckBox(" Start Locked", skin)
        carDriverTypeSelectBox = SelectBox(skin); carDriverTypeSelectBox.items = GdxArray(arrayOf("None", "Enemy", "NPC"))
        carEnemyDriverSelectBox = SelectBox(skin); carEnemyDriverSelectBox.items = GdxArray(EnemyType.entries.map { it.displayName }.toTypedArray())
        carNpcDriverSelectBox = SelectBox(skin); carNpcDriverSelectBox.items = GdxArray(NPCType.entries.map { it.displayName }.toTypedArray())

        carSettingsTable.add(Label("Car Type:", skin)).left(); carSettingsTable.add(carTypeSelectBox).growX().row()
        carSettingsTable.add(carLockedCheckbox).colspan(2).left().padTop(5f).row()
        carSettingsTable.add(Label("Driver:", skin)).left().padTop(10f); carSettingsTable.add(carDriverTypeSelectBox).growX().row()

        val carEnemyRow = Table()
        carEnemyRow.add(Label("Enemy Driver:", skin)).left().padRight(10f); carEnemyRow.add(carEnemyDriverSelectBox).growX()
        carSettingsTable.add(carEnemyRow).colspan(2).left().padTop(5f).row()

        val carNpcRow = Table()
        carNpcRow.add(Label("NPC Driver:", skin)).left().padRight(10f); carNpcRow.add(carNpcDriverSelectBox).growX()
        carSettingsTable.add(carNpcRow).colspan(2).left().padTop(5f).row()

        carDriverTypeSelectBox.addListener(object: ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val driverType = carDriverTypeSelectBox.selected
                carEnemyRow.isVisible = driverType == "Enemy"
                carNpcRow.isVisible = driverType == "NPC"
                window.pack()
            }
        })
        carEnemyRow.isVisible = false
        carNpcRow.isVisible = false

        // Add tabs to a Stack
        val settingsStack = Stack(particleSettingsTable, itemSettingsTable, weaponSettingsTable, enemySettingsTable, npcSettingsTable, carSettingsTable) // ADDED carSettingsTable
        window.add(settingsStack).colspan(2).fillX().padBottom(15f).row()

        // --- General Settings ---
        val generalTable = Table()
        generalTable.add(Label("--- GENERAL ---", skin, "title")).colspan(4).center().padBottom(10f).row()
        intervalField = TextField("", skin); minRangeField = TextField("", skin); maxRangeField = TextField("", skin)
        spawnOnlyWhenGoneCheckbox = CheckBox(" Respawn Only If Previous Gone", skin)
        spawnerModeSelectBox = SelectBox(skin); spawnerModeSelectBox.items = GdxArray(SpawnerMode.entries.map { it.name }.toTypedArray())
        generalTable.add(Label("Mode:", skin)).right(); generalTable.add(spawnerModeSelectBox).left().padRight(15f).row()
        generalTable.add(Label("Interval (s):", skin)).right(); generalTable.add(intervalField).width(60f).left().row()
        generalTable.add(Label("Min Player Range:", skin)).right(); generalTable.add(minRangeField).width(60f).left().row()
        generalTable.add(Label("Max Player Range:", skin)).right(); generalTable.add(maxRangeField).width(60f).left().row()
        generalTable.add(spawnOnlyWhenGoneCheckbox).colspan(2).left().padTop(5f).row()
        window.add(generalTable).colspan(2).padTop(10f).row()

        // --- Buttons ---
        applyButton = TextButton("Apply", skin); removeButton = TextButton("Remove", skin); closeButton = TextButton("Close", skin)
        removeButton.color.set(1f, 0.6f, 0.6f, 1f)
        val buttonTable = Table()
        buttonTable.add(applyButton).pad(10f); buttonTable.add(removeButton).pad(10f); buttonTable.add(closeButton).pad(10f)
        window.add(buttonTable).colspan(2).expandY().bottom().padBottom(10f)

        window.isVisible = false
        stage.addActor(window)

        addListeners()
    }

    private fun addListeners() {
        applyButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { applyChanges(); hide() } })
        removeButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { currentSpawner?.let { onRemove(it) }; hide() } })
        closeButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { hide() } })

        val tabListener = object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                when {
                    particleTabButton.isChecked -> showTab(SpawnerType.PARTICLE)
                    itemTabButton.isChecked -> showTab(SpawnerType.ITEM)
                    weaponTabButton.isChecked -> showTab(SpawnerType.WEAPON)
                    enemyTabButton.isChecked -> showTab(SpawnerType.ENEMY)
                    npcTabButton.isChecked -> showTab(SpawnerType.NPC)
                    carTabButton.isChecked -> showTab(SpawnerType.CAR)
                }
            }
        }
        particleTabButton.addListener(tabListener); itemTabButton.addListener(tabListener); weaponTabButton.addListener(tabListener)
        enemyTabButton.addListener(tabListener); npcTabButton.addListener(tabListener); carTabButton.addListener(tabListener)

        ammoModeSelectBox.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { updateAmmoFieldVisibility() } })
        enemyHealthSettingSelectBox.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { updateEnemyHealthFieldVisibility() } })

        // Preview image listeners
        effectSelectBox.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { updatePreviewImages() }})
        itemSelectBox.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { updatePreviewImages() }})
        weaponSelectBox.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { updatePreviewImages() }})
    }

    private fun updateAmmoFieldVisibility() {
        val selectedMode = AmmoSpawnMode.valueOf(ammoModeSelectBox.selected)
        setAmmoTable.isVisible = selectedMode == AmmoSpawnMode.SET
        randomAmmoTable.isVisible = selectedMode == AmmoSpawnMode.RANDOM
        window.pack()
    }

    private fun updateEnemyHealthFieldVisibility() {
        val selected = HealthSetting.entries.find { it.displayName == enemyHealthSettingSelectBox.selected }
        (enemyCustomHealthField.parent.parent as Table).getCell(enemyCustomHealthField.parent).actor.isVisible = selected == HealthSetting.FIXED_CUSTOM
        enemyRandomHealthTable.isVisible = selected == HealthSetting.RANDOM_RANGE
        window.pack()
    }

    private fun showTab(type: SpawnerType) {
        particleSettingsTable.isVisible = type == SpawnerType.PARTICLE
        itemSettingsTable.isVisible = type == SpawnerType.ITEM
        weaponSettingsTable.isVisible = type == SpawnerType.WEAPON
        enemySettingsTable.isVisible = type == SpawnerType.ENEMY
        npcSettingsTable.isVisible = type == SpawnerType.NPC
        carSettingsTable.isVisible = type == SpawnerType.CAR
        window.pack()
    }

    fun show(spawner: GameSpawner) {
        currentSpawner = spawner
        // General
        spawnerModeSelectBox.selected = spawner.spawnerMode.name
        intervalField.text = spawner.spawnInterval.toString()
        minRangeField.text = spawner.minSpawnRange.toString()
        maxRangeField.text = spawner.maxSpawnRange.toString()
        spawnOnlyWhenGoneCheckbox.isChecked = spawner.spawnOnlyWhenPreviousIsGone
        // Particle
        effectSelectBox.selected = spawner.particleEffectType.displayName
        minParticlesField.text = spawner.minParticles.toString(); maxParticlesField.text = spawner.maxParticles.toString()
        // Item
        itemSelectBox.selected = spawner.itemType.displayName
        minItemsField.text = spawner.minItems.toString(); maxItemsField.text = spawner.maxItems.toString()
        // Weapon
        weaponSelectBox.selected = spawner.weaponItemType.displayName
        ammoModeSelectBox.selected = spawner.ammoSpawnMode.name
        setAmmoField.text = spawner.setAmmoValue.toString()
        randomMinAmmoField.text = spawner.randomMinAmmo.toString(); randomMaxAmmoField.text = spawner.randomMaxAmmo.toString()
        // Enemy
        enemyTypeSelectBox.selected = spawner.enemyType.displayName
        enemyBehaviorSelectBox.selected = spawner.enemyBehavior.displayName
        enemyHealthSettingSelectBox.selected = spawner.enemyHealthSetting.displayName
        enemyCustomHealthField.text = spawner.enemyCustomHealth.toString()
        enemyMinHealthField.text = spawner.enemyMinHealth.toString(); enemyMaxHealthField.text = spawner.enemyMaxHealth.toString()
        enemyInitialWeaponSelectBox.selected = spawner.enemyInitialWeapon.displayName
        enemyWeaponCollectionPolicySelectBox.selected = spawner.enemyWeaponCollectionPolicy.displayName
        enemyCanCollectItemsCheckbox.isChecked = spawner.enemyCanCollectItems
        enemyInitialMoneyField.text = spawner.enemyInitialMoney.toString()
        // NPC
        npcTypeSelectBox.selected = spawner.npcType.displayName
        npcBehaviorSelectBox.selected = spawner.npcBehavior.displayName
        npcCanCollectItemsCheckbox.isChecked = spawner.npcCanCollectItems
        npcIsHonestCheckbox.isChecked = spawner.npcIsHonest

        carTypeSelectBox.selected = spawner.carType.displayName
        carLockedCheckbox.isChecked = spawner.carIsLocked
        carDriverTypeSelectBox.selected = spawner.carDriverType
        carEnemyDriverSelectBox.selected = spawner.carEnemyDriverType.displayName
        carNpcDriverSelectBox.selected = spawner.carNpcDriverType.displayName

        // Set Tab
        when (spawner.spawnerType) {
            SpawnerType.PARTICLE -> particleTabButton.isChecked = true
            SpawnerType.ITEM -> itemTabButton.isChecked = true
            SpawnerType.WEAPON -> weaponTabButton.isChecked = true
            SpawnerType.ENEMY -> enemyTabButton.isChecked = true
            SpawnerType.NPC -> npcTabButton.isChecked = true
            SpawnerType.CAR -> carTabButton.isChecked = true
        }
        showTab(spawner.spawnerType)
        updatePreviewImages()
        updateAmmoFieldVisibility()
        updateEnemyHealthFieldVisibility()

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
        // General settings
        spawner.spawnerMode = SpawnerMode.valueOf(spawnerModeSelectBox.selected)
        spawner.spawnInterval = intervalField.text.toFloatOrNull()?.coerceAtLeast(0.1f) ?: spawner.spawnInterval
        spawner.minSpawnRange = minRangeField.text.toFloatOrNull()?.coerceAtLeast(0f) ?: spawner.minSpawnRange
        spawner.maxSpawnRange = maxRangeField.text.toFloatOrNull()?.coerceAtLeast(spawner.minSpawnRange) ?: spawner.maxSpawnRange
        spawner.spawnOnlyWhenPreviousIsGone = spawnOnlyWhenGoneCheckbox.isChecked

        // Apply tab-specific settings
        when {
            particleTabButton.isChecked -> {
                spawner.spawnerType = SpawnerType.PARTICLE
                spawner.particleEffectType = ParticleEffectType.entries.find { it.displayName == effectSelectBox.selected } ?: spawner.particleEffectType
                spawner.minParticles = minParticlesField.text.toIntOrNull() ?: spawner.minParticles
                spawner.maxParticles = maxParticlesField.text.toIntOrNull() ?: spawner.maxParticles
            }
            itemTabButton.isChecked -> {
                spawner.spawnerType = SpawnerType.ITEM
                spawner.itemType = ItemType.entries.find { it.displayName == itemSelectBox.selected } ?: spawner.itemType
                spawner.minItems = minItemsField.text.toIntOrNull() ?: spawner.minItems
                spawner.maxItems = maxItemsField.text.toIntOrNull() ?: spawner.maxItems
            }
            weaponTabButton.isChecked -> {
                spawner.spawnerType = SpawnerType.WEAPON
                spawner.weaponItemType = ItemType.entries.find { it.displayName == weaponSelectBox.selected } ?: spawner.weaponItemType
                spawner.ammoSpawnMode = AmmoSpawnMode.valueOf(ammoModeSelectBox.selected)
                spawner.setAmmoValue = setAmmoField.text.toIntOrNull() ?: spawner.setAmmoValue
                spawner.randomMinAmmo = randomMinAmmoField.text.toIntOrNull() ?: spawner.randomMinAmmo
                spawner.randomMaxAmmo = randomMaxAmmoField.text.toIntOrNull() ?: spawner.randomMaxAmmo
            }
            enemyTabButton.isChecked -> {
                spawner.spawnerType = SpawnerType.ENEMY
                spawner.enemyType = EnemyType.entries.find { it.displayName == enemyTypeSelectBox.selected } ?: spawner.enemyType
                spawner.enemyBehavior = EnemyBehavior.entries.find { it.displayName == enemyBehaviorSelectBox.selected } ?: spawner.enemyBehavior
                spawner.enemyHealthSetting = HealthSetting.entries.find { it.displayName == enemyHealthSettingSelectBox.selected } ?: spawner.enemyHealthSetting
                spawner.enemyCustomHealth = enemyCustomHealthField.text.toFloatOrNull() ?: spawner.enemyCustomHealth
                spawner.enemyMinHealth = enemyMinHealthField.text.toFloatOrNull() ?: spawner.enemyMinHealth
                spawner.enemyMaxHealth = enemyMaxHealthField.text.toFloatOrNull() ?: spawner.enemyMaxHealth
                spawner.enemyInitialWeapon = WeaponType.entries.find { it.displayName == enemyInitialWeaponSelectBox.selected } ?: spawner.enemyInitialWeapon
                spawner.enemyWeaponCollectionPolicy = WeaponCollectionPolicy.entries.find { it.displayName == enemyWeaponCollectionPolicySelectBox.selected } ?: spawner.enemyWeaponCollectionPolicy
                spawner.enemyCanCollectItems = enemyCanCollectItemsCheckbox.isChecked
                spawner.enemyInitialMoney = enemyInitialMoneyField.text.toIntOrNull() ?: spawner.enemyInitialMoney
            }
            npcTabButton.isChecked -> {
                spawner.spawnerType = SpawnerType.NPC
                spawner.npcType = NPCType.entries.find { it.displayName == npcTypeSelectBox.selected } ?: spawner.npcType
                spawner.npcBehavior = NPCBehavior.entries.find { it.displayName == npcBehaviorSelectBox.selected } ?: spawner.npcBehavior
                spawner.npcCanCollectItems = npcCanCollectItemsCheckbox.isChecked
                spawner.npcIsHonest = npcIsHonestCheckbox.isChecked
            }
            carTabButton.isChecked -> {
                spawner.spawnerType = SpawnerType.CAR
                spawner.carType = CarType.entries.find { it.displayName == carTypeSelectBox.selected } ?: spawner.carType
                spawner.carIsLocked = carLockedCheckbox.isChecked
                spawner.carDriverType = carDriverTypeSelectBox.selected
                spawner.carEnemyDriverType = EnemyType.entries.find { it.displayName == carEnemyDriverSelectBox.selected } ?: spawner.carEnemyDriverType
                spawner.carNpcDriverType = NPCType.entries.find { it.displayName == carNpcDriverSelectBox.selected } ?: spawner.carNpcDriverType
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
