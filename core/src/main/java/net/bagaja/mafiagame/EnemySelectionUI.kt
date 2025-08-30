package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align

class EnemySelectionUI(
    private val enemySystem: EnemySystem,
    private val skin: Skin,
    private val stage: Stage
) {
    private lateinit var enemySelectionTable: Table
    private lateinit var enemyTypeItems: MutableList<EnemySelectionItem>
    private lateinit var primaryTacticItems: MutableList<BehaviorSelectionItem>
    private lateinit var emptyAmmoTacticItems: MutableList<BehaviorSelectionItem>

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
        // Create enemy selection table at the top center
        enemySelectionTable = Table()
        enemySelectionTable.setFillParent(true)
        enemySelectionTable.top()
        enemySelectionTable.pad(40f)

        // Create main container with modern styling
        val mainContainer = Table()
        mainContainer.background = createModernBackground()
        mainContainer.pad(20f, 30f, 20f, 30f)

        val titleLabel = Label("Enemy Configuration", skin, "title")
        mainContainer.add(titleLabel).padBottom(15f).colspan(2).row()

        val typeTitle = Label("Enemy Archetype", skin, "default")
        mainContainer.add(typeTitle).colspan(2).padBottom(10f).row()

        // Create horizontal container for enemy type items
        val enemyTypeContainer = Table()
        enemyTypeItems = mutableListOf()
        EnemyType.entries.forEachIndexed { index, enemyType ->
            val item = createEnemyTypeItem(enemyType, index == enemySystem.currentEnemyTypeIndex)
            enemyTypeItems.add(item)
            if (index > 0) enemyTypeContainer.add().width(15f)
            enemyTypeContainer.add(item.container).size(100f, 130f)
        }
        mainContainer.add(enemyTypeContainer).colspan(2).padBottom(20f).row()

        val primaryTacticTable = Table()
        primaryTacticTable.add(Label("Primary Tactic", skin, "default")).padBottom(10f).row()
        val primaryTacticContainer = Table()
        primaryTacticItems = mutableListOf()
        primaryBehaviors.forEachIndexed { index, behavior ->
            val item = createBehaviorItem(behavior, index == primaryTacticIndex)
            primaryTacticItems.add(item)
            if (index > 0) primaryTacticContainer.add().height(10f).row()
            primaryTacticContainer.add(item.container).size(110f, 100f)
        }
        primaryTacticTable.add(primaryTacticContainer)

        val emptyAmmoTacticTable = Table()
        emptyAmmoTacticTable.add(Label("Out of Ammo Tactic", skin, "default")).padBottom(10f).row()
        val emptyAmmoContainer = Table()
        emptyAmmoTacticItems = mutableListOf()
        emptyAmmoBehaviors.forEachIndexed { index, behavior ->
            val item = createBehaviorItem(behavior, index == emptyAmmoTacticIndex)
            emptyAmmoTacticItems.add(item)
            if (index > 0) emptyAmmoContainer.add().height(10f).row()
            emptyAmmoContainer.add(item.container).size(110f, 100f)
        }
        emptyAmmoTacticTable.add(emptyAmmoContainer)

        mainContainer.add(primaryTacticTable).uniformX().padRight(20f)
        mainContainer.add(emptyAmmoTacticTable).uniformX().padLeft(20f).row()

        val instructionLabel = Label("Hold [Y] | Wheel: Archetype | Shift+Wheel: Tactic | Ctrl+Wheel: Fallback", skin)
        mainContainer.add(instructionLabel).colspan(2).padTop(20f).row()

        enemySelectionTable.add(mainContainer)
        stage.addActor(enemySelectionTable)
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
            EnemyType.NOUSE_THUG -> pixmap.setColor(Color(0.4f, 0.2f, 0.2f, 1f))
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
