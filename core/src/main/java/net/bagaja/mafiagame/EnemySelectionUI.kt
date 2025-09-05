package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import java.awt.SystemColor.window

class EnemySelectionUI(
    private val enemySystem: EnemySystem,
    private val skin: Skin,
    private val stage: Stage
) {
    private lateinit var enemySelectionTable: Table
    private lateinit var enemyTypeItems: MutableList<EnemySelectionItem>
    private lateinit var primaryTacticItems: MutableList<BehaviorSelectionItem>
    private lateinit var emptyAmmoTacticItems: MutableList<BehaviorSelectionItem>
    private lateinit var healthSettingSelectBox: SelectBox<String>
    private lateinit var customHealthField: TextField
    private lateinit var minHealthField: TextField
    private lateinit var maxHealthField: TextField
    private lateinit var weaponPolicySelectBox: SelectBox<String>
    private lateinit var canCollectItemsCheckbox: CheckBox
    private lateinit var healthFieldsTable: Table
    private lateinit var initialWeaponSelectBox: SelectBox<String>
    private lateinit var ammoModeSelectBox: SelectBox<String>
    private lateinit var setAmmoField: TextField
    private lateinit var ammoFieldsTable: Table

    private val primaryBehaviors = listOf(EnemyBehavior.STATIONARY_SHOOTER, EnemyBehavior.AGGRESSIVE_RUSHER, EnemyBehavior.NEUTRAL)
    private val emptyAmmoBehaviors = listOf(EnemyBehavior.AGGRESSIVE_RUSHER, EnemyBehavior.SKIRMISHER)

    private var primaryTacticIndex = 0
    private var emptyAmmoTacticIndex = 0

    private val loadedTextures = mutableMapOf<String, Texture>()

    private data class EnemySelectionItem(
        val container: Table, val iconImage: Image, val nameLabel: Label, val statsLabel: Label,
        val background: Drawable, val selectedBackground: Drawable, val enemyType: EnemyType
    )

    private data class BehaviorSelectionItem(
        val container: Table, val iconImage: Image, val nameLabel: Label,
        val background: Drawable, val selectedBackground: Drawable, val behavior: EnemyBehavior
    )

    fun initialize() {
        setupEnemySelectionUI()
        enemySelectionTable.setVisible(false)
    }

    private fun setupEnemySelectionUI() {
        // 1. Root Table Setup
        enemySelectionTable = Table()
        enemySelectionTable.setFillParent(true)
        enemySelectionTable.top()
        enemySelectionTable.pad(40f)

        // 2. Main Container with Background
        val mainContainer = Table()
        mainContainer.background = createModernBackground()
        mainContainer.pad(20f, 30f, 20f, 30f)

        val titleLabel = Label("Enemy Configuration", skin, "title")
        mainContainer.add(titleLabel).padBottom(20f).row()

        // 3. Top Section for Visual Choices (Archetypes & Tactics)
        val topSelectionContainer = Table()

        // 3a. Archetype Column (Left side)
        val archetypeColumn = Table()
        archetypeColumn.add(Label("Enemy Archetype", skin, "default")).padBottom(10f).row()
        val enemyTypeContainer = Table()
        enemyTypeItems = mutableListOf()
        EnemyType.entries.forEachIndexed { index, enemyType ->
            val item = createEnemyTypeItem(enemyType, index == enemySystem.currentEnemyTypeIndex)
            enemyTypeItems.add(item)
            if (index > 0) enemyTypeContainer.add().width(15f)
            enemyTypeContainer.add(item.container).size(100f, 130f)
        }
        archetypeColumn.add(enemyTypeContainer)

        // 3b. Tactics Column (Right side)
        val tacticsColumn = Table()

        // Primary Tactic (Restored)
        val primaryTacticTable = Table()
        primaryTacticTable.add(Label("Primary Tactic", skin, "default")).padBottom(10f).row()
        val primaryTacticContainer = Table()
        primaryTacticItems = mutableListOf()
        primaryBehaviors.forEachIndexed { index, behavior ->
            val item = createBehaviorItem(behavior, index == primaryTacticIndex)
            primaryTacticItems.add(item)
            if (index > 0) primaryTacticContainer.add().width(10f)
            primaryTacticContainer.add(item.container).size(110f, 100f)
        }
        primaryTacticTable.add(primaryTacticContainer)
        tacticsColumn.add(primaryTacticTable).padBottom(15f).row()

        // Out of Ammo Tactic (Restored)
        val emptyAmmoTacticTable = Table()
        emptyAmmoTacticTable.add(Label("Out of Ammo Tactic", skin, "default")).padBottom(10f).row()
        val emptyAmmoContainer = Table()
        emptyAmmoTacticItems = mutableListOf()
        emptyAmmoBehaviors.forEachIndexed { index, behavior ->
            val item = createBehaviorItem(behavior, index == emptyAmmoTacticIndex)
            emptyAmmoTacticItems.add(item)
            if (index > 0) emptyAmmoContainer.add().width(10f)
            emptyAmmoContainer.add(item.container).size(110f, 100f)
        }
        emptyAmmoTacticTable.add(emptyAmmoContainer)
        tacticsColumn.add(emptyAmmoTacticTable).row()

        // Add columns to the top container
        topSelectionContainer.add(archetypeColumn).top().padRight(40f)
        topSelectionContainer.add(tacticsColumn).top()
        mainContainer.add(topSelectionContainer).padBottom(25f).row()

        // 4. Bottom Section for Detailed Configuration
        val configTitle = Label("Placement Settings", skin, "title")
        mainContainer.add(configTitle).padTop(15f).padBottom(15f).row()

        val configTable = Table()

        // Health Settings
        healthFieldsTable = Table() // This table will hold the dynamic text fields
        val healthSettingNames = com.badlogic.gdx.utils.Array(HealthSetting.entries.map { it.displayName }.toTypedArray())
        healthSettingSelectBox = SelectBox(skin)
        healthSettingSelectBox.items = healthSettingNames
        configTable.add(Label("Health:", skin)).left().padRight(10f)
        configTable.add(healthSettingSelectBox).width(150f).left().row()

        customHealthField = TextField("", skin)
        minHealthField = TextField("", skin)
        maxHealthField = TextField("", skin)

        val customHealthRow = Table()
        customHealthRow.add(Label("Custom HP:", skin)).padRight(10f)
        customHealthRow.add(customHealthField).width(80f)
        healthFieldsTable.add(customHealthRow).left().row()

        val randomHealthRow = Table()
        randomHealthRow.add(Label("Min HP:", skin)).padRight(10f)
        randomHealthRow.add(minHealthField).width(60f).padRight(10f)
        randomHealthRow.add(Label("Max HP:", skin)).padRight(10f)
        randomHealthRow.add(maxHealthField).width(60f)
        healthFieldsTable.add(randomHealthRow).left().row()

        configTable.add(healthFieldsTable).colspan(2).left().padTop(5f).row()

        // Weapon & Item Settings
        val weaponPolicyNames = com.badlogic.gdx.utils.Array(WeaponCollectionPolicy.entries.map { it.displayName }.toTypedArray())
        weaponPolicySelectBox = SelectBox(skin)
        weaponPolicySelectBox.items = weaponPolicyNames
        configTable.add(Label("Weapon Pickup:", skin)).left().padTop(10f).padRight(10f)
        configTable.add(weaponPolicySelectBox).width(200f).left().row()

        // Initial Weapon Selection
        val weaponNames = com.badlogic.gdx.utils.Array(WeaponType.entries.map { it.displayName }.toTypedArray())
        initialWeaponSelectBox = SelectBox(skin)
        initialWeaponSelectBox.items = weaponNames
        configTable.add(Label("Start Weapon:", skin)).left().padTop(5f).padRight(10f)
        configTable.add(initialWeaponSelectBox).width(200f).left().row()

        // Ammo Settings
        ammoFieldsTable = Table()
        val ammoModeNames = com.badlogic.gdx.utils.Array(AmmoSpawnMode.entries.filter { it != AmmoSpawnMode.RANDOM }.map { it.name }.toTypedArray())
        ammoModeSelectBox = SelectBox(skin)
        ammoModeSelectBox.items = ammoModeNames
        val setAmmoRow = Table()
        setAmmoField = TextField("", skin)
        setAmmoRow.add(Label("Ammo:", skin)).padRight(10f)
        setAmmoRow.add(ammoModeSelectBox).padRight(10f)
        setAmmoRow.add(setAmmoField).width(60f)
        ammoFieldsTable.add(setAmmoRow).left().row()
        configTable.add(ammoFieldsTable).colspan(2).left().padTop(5f).row()

        // Item Collection Checkbox
        canCollectItemsCheckbox = CheckBox(" Can Collect Items (Money, etc.)", skin)
        canCollectItemsCheckbox.isChecked = true
        configTable.add(canCollectItemsCheckbox).colspan(2).left().padTop(5f).row()

        mainContainer.add(configTable).padBottom(15f).row()

        // 5. Instructions Label
        val instructionLabel = Label("Hold [Y] | Wheel: Archetype | Shift+Wheel: Tactic | Ctrl+Wheel: Fallback", skin)
        mainContainer.add(instructionLabel).padTop(20f).row()

        // 6. Final Assembly
        enemySelectionTable.add(mainContainer)
        stage.addActor(enemySelectionTable)

        // 7. Add Listeners to control dynamic UI
        healthSettingSelectBox.addListener {
            updateHealthFieldVisibility()
            true
        }
        initialWeaponSelectBox.addListener {
            updateAmmoUIVisibility()
            true
        }
        ammoModeSelectBox.addListener {
            updateAmmoUIVisibility()
            true
        }

        // Set initial visibility of dynamic fields
        updateHealthFieldVisibility()
        updateAmmoUIVisibility()
    }

    private fun updateHealthFieldVisibility() {
        val selected = HealthSetting.entries.find { it.displayName == healthSettingSelectBox.selected }
        healthFieldsTable.getChild(0).isVisible = selected == HealthSetting.FIXED_CUSTOM // Custom HP row
        healthFieldsTable.getChild(1).isVisible = selected == HealthSetting.RANDOM_RANGE  // Random HP row
    }

    private fun updateAmmoUIVisibility() {
        val selectedWeapon = WeaponType.entries.find { it.displayName == initialWeaponSelectBox.selected }
        val isGun = selectedWeapon != null && selectedWeapon.actionType != WeaponActionType.MELEE && selectedWeapon != WeaponType.UNARMED

        ammoFieldsTable.isVisible = isGun

        if (isGun) {
            val selectedMode = AmmoSpawnMode.valueOf(ammoModeSelectBox.selected)
            setAmmoField.isVisible = selectedMode == AmmoSpawnMode.SET
        }

        // REMOVE THIS LINE: window.pack()
    }

    // NEW METHOD to get the full configuration from the UI
    fun getSpawnConfig(position: Vector3): EnemySpawnConfig {
        val enemyType = EnemyType.entries[enemySystem.currentEnemyTypeIndex]
        val behavior = primaryBehaviors[primaryTacticIndex]

        val healthSetting = HealthSetting.entries.find { it.displayName == healthSettingSelectBox.selected } ?: HealthSetting.FIXED_DEFAULT
        val customHealth = customHealthField.text.toFloatOrNull() ?: enemyType.baseHealth
        val minHealth = minHealthField.text.toFloatOrNull() ?: (enemyType.baseHealth * 0.8f)
        val maxHealth = maxHealthField.text.toFloatOrNull() ?: (enemyType.baseHealth * 1.2f)

        val weaponPolicy = WeaponCollectionPolicy.entries.find { it.displayName == weaponPolicySelectBox.selected } ?: WeaponCollectionPolicy.CANNOT_COLLECT

        val initialWeapon = WeaponType.entries.find { it.displayName == initialWeaponSelectBox.selected } ?: WeaponType.UNARMED
        val ammoMode = AmmoSpawnMode.valueOf(ammoModeSelectBox.selected)
        val setAmmo = setAmmoField.text.toIntOrNull() ?: 30

        return EnemySpawnConfig(
            enemyType = enemyType,
            behavior = behavior,
            position = position,
            healthSetting = healthSetting,
            customHealthValue = customHealth,
            minRandomHealth = minHealth,
            maxRandomHealth = maxHealth,
            weaponCollectionPolicy = weaponPolicy,
            canCollectItems = canCollectItemsCheckbox.isChecked,
            initialWeapon = initialWeapon,
            ammoSpawnMode = ammoMode,
            setAmmoValue = setAmmo
        )
    }

    fun nextPrimaryTactic() {
        primaryTacticIndex = (primaryTacticIndex + 1) % primaryBehaviors.size
        enemySystem.currentSelectedBehavior = primaryBehaviors[primaryTacticIndex]
        update()
    }

    fun prevPrimaryTactic() {
        primaryTacticIndex--
        if (primaryTacticIndex < 0) primaryTacticIndex = primaryBehaviors.size - 1
        enemySystem.currentSelectedBehavior = primaryBehaviors[primaryTacticIndex]
        update()
    }

    fun nextEmptyAmmoTactic() {
        emptyAmmoTacticIndex = (emptyAmmoTacticIndex + 1) % emptyAmmoBehaviors.size
        update()
    }

    fun prevEmptyAmmoTactic() {
        emptyAmmoTacticIndex--
        if (emptyAmmoTacticIndex < 0) emptyAmmoTacticIndex = emptyAmmoBehaviors.size - 1
        update()
    }

    fun update() {
        enemyTypeItems.forEachIndexed { index, item ->
            updateItemSelection(item.container, index == enemySystem.currentEnemyTypeIndex, item.background, item.selectedBackground, item.nameLabel, item.statsLabel)
        }
        primaryTacticItems.forEachIndexed { index, item ->
            updateItemSelection(item.container, index == primaryTacticIndex, item.background, item.selectedBackground, item.nameLabel)
        }
        emptyAmmoTacticItems.forEachIndexed { index, item ->
            updateItemSelection(item.container, index == emptyAmmoTacticIndex, item.background, item.selectedBackground, item.nameLabel)
        }
    }

    private fun createEnemyTypeItem(enemyType: EnemyType, isSelected: Boolean): EnemySelectionItem {
        val container = Table()
        container.pad(8f)

        // Create backgrounds for normal and selected states
        val normalBg = createItemBackground(Color(0.3f, 0.3f, 0.35f, 0.9f))
        val selectedBg = createItemBackground(Color(0.6f, 0.4f, 0.8f, 0.95f)) // Purple theme for enemies

        container.background = if (isSelected) selectedBg else normalBg

        // Enemy icon - try to load actual texture first, fallback to generated icon
        val iconTexture = loadEnemyTexture(enemyType)
        val iconImage = Image(iconTexture)
        container.add(iconImage).size(60f, 60f).padBottom(8f).row()

        // Enemy name
        val nameLabel = Label(enemyType.displayName, skin)
        nameLabel.setFontScale(0.7f)
        nameLabel.setWrap(true)
        nameLabel.setAlignment(Align.center)
        nameLabel.color = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
        container.add(nameLabel).width(90f).center().padBottom(5f).row()

        // Enemy stats
        val statsText = "HP: ${enemyType.baseHealth.toInt()}\nSpd: ${enemyType.speed}"
        val statsLabel = Label(statsText, skin)
        statsLabel.setFontScale(0.6f)
        statsLabel.setWrap(true)
        statsLabel.setAlignment(Align.center)
        statsLabel.color = if (isSelected) Color(0.9f, 0.9f, 0.9f, 1f) else Color(0.7f, 0.7f, 0.7f, 1f)
        container.add(statsLabel).width(90f).center()

        return EnemySelectionItem(container, iconImage, nameLabel, statsLabel, normalBg, selectedBg, enemyType)
    }

    private fun createBehaviorItem(behavior: EnemyBehavior, isSelected: Boolean): BehaviorSelectionItem {
        val container = Table()
        container.pad(8f)

        // Create backgrounds for normal and selected states
        val normalBg = createItemBackground(Color(0.3f, 0.35f, 0.3f, 0.9f))
        val selectedBg = createItemBackground(Color(0.4f, 0.8f, 0.6f, 0.95f)) // Green theme for behaviors

        container.background = if (isSelected) selectedBg else normalBg

        // Behavior icon
        val iconTexture = createBehaviorIcon(behavior)
        val iconImage = Image(iconTexture)
        container.add(iconImage).size(40f, 40f).padBottom(8f).row()

        // Behavior name
        val nameLabel = Label(behavior.displayName, skin)
        nameLabel.setFontScale(0.7f)
        nameLabel.setWrap(true)
        nameLabel.setAlignment(Align.center)
        nameLabel.color = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
        container.add(nameLabel).width(80f).center()

        return BehaviorSelectionItem(container, iconImage, nameLabel, normalBg, selectedBg, behavior)
    }

    private fun loadEnemyTexture(enemyType: EnemyType): TextureRegion {
        val texturePath = enemyType.texturePath

        // Check if we already have this texture cached
        if (loadedTextures.containsKey(texturePath)) {
            return TextureRegion(loadedTextures[texturePath]!!)
        }

        // Try to load the actual texture file
        return try {
            val fileHandle = Gdx.files.internal(texturePath)
            if (fileHandle.exists()) {
                val texture = Texture(fileHandle)
                loadedTextures[texturePath] = texture // Cache it
                println("Loaded enemy texture: $texturePath")
                TextureRegion(texture)
            } else {
                println("Enemy texture not found: $texturePath, using fallback")
                createEnemyIcon(enemyType)
            }
        } catch (e: Exception) {
            println("Failed to load enemy texture: $texturePath, error: ${e.message}, using fallback")
            createEnemyIcon(enemyType)
        }
    }

    private fun createEnemyIcon(enemyType: EnemyType): TextureRegion {
        val pixmap = Pixmap(60, 60, Pixmap.Format.RGBA8888)
        when (enemyType) {
            EnemyType.MOUSE_THUG -> pixmap.setColor(Color(0.4f, 0.2f, 0.2f, 1f))
            EnemyType.GUNTHER -> pixmap.setColor(Color(0.5f, 0.3f, 0.2f, 1f))
            EnemyType.CORRUPT_DETECTIVE -> pixmap.setColor(Color(0.3f, 0.3f, 0.3f, 1f))
            EnemyType.MAFIA_BOSS -> pixmap.setColor(Color(0.1f, 0.1f, 0.1f, 1f))
        }
        pixmap.fill()
        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegion(texture)
    }

    private fun createBehaviorIcon(behavior: EnemyBehavior): TextureRegion {
        val pixmap = Pixmap(40, 40, Pixmap.Format.RGBA8888)

        when (behavior) {
            EnemyBehavior.STATIONARY_SHOOTER -> {
                // Target/crosshair icon
                pixmap.setColor(Color(0.8f, 0.2f, 0.2f, 1f))
                pixmap.drawCircle(20, 20, 15)
                pixmap.drawCircle(20, 20, 10)
                pixmap.drawLine(20, 5, 20, 35)
                pixmap.drawLine(5, 20, 35, 20)
            }
            EnemyBehavior.COWARD_HIDER -> {
                // Shield/hide icon
                pixmap.setColor(Color(0.2f, 0.6f, 0.8f, 1f))
                pixmap.fillCircle(20, 25, 12)
            }
            EnemyBehavior.AGGRESSIVE_RUSHER -> {
                // Arrow/charge icon
                pixmap.setColor(Color(0.8f, 0.4f, 0.2f, 1f))
                // Arrow pointing right
                pixmap.fillRectangle(8, 18, 20, 4)
            }
            EnemyBehavior.SKIRMISHER -> {
                pixmap.setColor(Color(0.2f, 0.8f, 0.4f, 1f))
                for (i in 0..5) {
                    pixmap.drawLine(8 + i, 10 + i, 8 + i, 30 - i)
                }
                for (i in 0..5) {
                    pixmap.drawLine(32 - i, 10 + i, 32 - i, 30 - i)
                }
            }
            EnemyBehavior.NEUTRAL -> {
                pixmap.setColor(Color(0.7f, 0.7f, 0.7f, 1f))
                pixmap.drawCircle(20,20,15)
            }
        }
        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegion(texture)
    }

    private fun createModernBackground(): Drawable {
        // Create a modern dark background with subtle transparency
        val pixmap = Pixmap(100, 80, Pixmap.Format.RGBA8888)

        // Gradient effect
        for (y in 0 until 80) {
            val alpha = 0.85f + (y / 80f) * 0.1f
            pixmap.setColor(0.1f, 0.1f, 0.15f, alpha)
            pixmap.drawLine(0, y, 99, y)
        }

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createItemBackground(color: Color): Drawable {
        val pixmap = Pixmap(100, 130, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()

        // Add subtle border
        pixmap.setColor(color.r + 0.1f, color.g + 0.1f, color.b + 0.1f, color.a)
        pixmap.drawRectangle(0, 0, 100, 130)

        val texture = Texture(pixmap)
        pixmap.dispose()

        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun updateItemSelection(container: Table, isSelected: Boolean, normalBg: Drawable, selectedBg: Drawable, vararg labels: Label) {
        val targetScale = if (isSelected) 1.1f else 1.0f
        val targetColor = if (isSelected) Color.WHITE else Color(0.8f, 0.8f, 0.8f, 1f)
        container.clearActions()
        container.addAction(
            Actions.parallel(
                Actions.scaleTo(targetScale, targetScale, 0.2f, Interpolation.smooth),
                Actions.run {
                    container.background = if (isSelected) selectedBg else normalBg
                    labels.forEach { it.color = targetColor }
                }
            )
        )
    }

    fun show() {
        enemySelectionTable.setVisible(true)
    }

    fun hide() {
        enemySelectionTable.setVisible(false)
    }

    fun dispose() {
        // Dispose cached textures
        for (texture in loadedTextures.values) {
            texture.dispose()
        }
        loadedTextures.clear()
    }
}
