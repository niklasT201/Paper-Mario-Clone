package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align

class CharacterInventoryUI(
    private val skin: Skin,
    private val stage: Stage,
    private val itemSystem: ItemSystem // We need this to get weapon icons
) {
    private val window: Window = Window("Character Details", skin, "dialog")
    private lateinit var slotBackground: TextureRegionDrawable
    private lateinit var slotBackgroundHover: TextureRegionDrawable
    private lateinit var headerBackground: TextureRegionDrawable
    private lateinit var contentBackground: TextureRegionDrawable

    // UI containers for different character types
    private val enemyContainer: Table
    private val npcContainer: Table

    // NPC specific UI elements
    private val npcHealthLabel: Label

    init {
        createBackgrounds()

        // --- Setup Enemy Container ---
        enemyContainer = Table()
        enemyContainer.align(Align.top)
        enemyContainer.background = contentBackground

        // --- Setup NPC Container ---
        npcContainer = Table()
        npcContainer.align(Align.top)
        npcContainer.background = contentBackground
        npcHealthLabel = Label("", skin, "default")

        setupNPCContainer()
        setupWindow()

        stage.addActor(window)
    }

    private fun createBackgrounds() {
        // Enhanced slot background with gradient effect
        val slotPixmap = Pixmap(80, 80, Pixmap.Format.RGBA8888)
        // Dark background with subtle gradient
        slotPixmap.setColor(Color(0.15f, 0.15f, 0.2f, 0.95f))
        slotPixmap.fill()
        // Lighter inner area for depth
        slotPixmap.setColor(Color(0.25f, 0.25f, 0.35f, 0.8f))
        slotPixmap.fillRectangle(4, 4, 72, 72)
        // Subtle highlight on top edge
        slotPixmap.setColor(Color(0.4f, 0.4f, 0.55f, 0.6f))
        slotPixmap.fillRectangle(6, 74, 68, 2)
        // Shadow on bottom edge
        slotPixmap.setColor(Color(0.05f, 0.05f, 0.1f, 0.8f))
        slotPixmap.fillRectangle(6, 4, 68, 2)
        // Border
        slotPixmap.setColor(Color(0.4f, 0.4f, 0.5f, 0.7f))
        slotPixmap.drawRectangle(2, 2, 76, 76)
        slotBackground = TextureRegionDrawable(Texture(slotPixmap))
        slotPixmap.dispose()

        // Hover state for slots
        val hoverPixmap = Pixmap(80, 80, Pixmap.Format.RGBA8888)
        hoverPixmap.setColor(Color(0.25f, 0.35f, 0.45f, 0.95f))
        hoverPixmap.fill()
        hoverPixmap.setColor(Color(0.35f, 0.45f, 0.55f, 0.8f))
        hoverPixmap.fillRectangle(4, 4, 72, 72)
        hoverPixmap.setColor(Color(0.5f, 0.6f, 0.7f, 0.6f))
        hoverPixmap.fillRectangle(6, 74, 68, 2)
        hoverPixmap.setColor(Color(0.15f, 0.25f, 0.35f, 0.8f))
        hoverPixmap.fillRectangle(6, 4, 68, 2)
        hoverPixmap.setColor(Color(0.6f, 0.7f, 0.8f, 0.8f))
        hoverPixmap.drawRectangle(2, 2, 76, 76)
        slotBackgroundHover = TextureRegionDrawable(Texture(hoverPixmap))
        hoverPixmap.dispose()

        // Header background with gradient
        val headerPixmap = Pixmap(400, 60, Pixmap.Format.RGBA8888)
        headerPixmap.setColor(Color(0.2f, 0.25f, 0.35f, 0.9f))
        headerPixmap.fill()
        headerPixmap.setColor(Color(0.3f, 0.35f, 0.45f, 0.7f))
        headerPixmap.fillRectangle(0, 40, 400, 20)
        headerPixmap.setColor(Color(0.1f, 0.15f, 0.25f, 0.9f))
        headerPixmap.fillRectangle(0, 0, 400, 10)
        headerBackground = TextureRegionDrawable(Texture(headerPixmap))
        headerPixmap.dispose()

        // Content area background
        val contentPixmap = Pixmap(400, 300, Pixmap.Format.RGBA8888)
        contentPixmap.setColor(Color(0.12f, 0.12f, 0.18f, 0.85f))
        contentPixmap.fill()
        // Subtle inner shadow effect
        contentPixmap.setColor(Color(0.08f, 0.08f, 0.12f, 0.6f))
        contentPixmap.fillRectangle(0, 290, 400, 10)
        contentPixmap.fillRectangle(0, 0, 10, 300)
        contentPixmap.setColor(Color(0.18f, 0.18f, 0.24f, 0.4f))
        contentPixmap.fillRectangle(390, 0, 10, 300)
        contentPixmap.fillRectangle(0, 0, 400, 10)
        contentBackground = TextureRegionDrawable(Texture(contentPixmap))
        contentPixmap.dispose()
    }

    private fun setupWindow() {
        window.isMovable = true
        window.padTop(50f)
        window.pad(20f)
        window.defaults().pad(8f)
        window.isVisible = false

        // Add containers
        val contentStack = Stack()
        contentStack.add(enemyContainer)
        contentStack.add(npcContainer)

        window.add(contentStack).expand().fill().row()

        // Enhanced close button
        val closeButton = TextButton("✕ Close", skin)
        closeButton.color = Color(0.8f, 0.3f, 0.3f, 1f)
        closeButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                hide()
            }
        })
        window.add(closeButton).colspan(2).padTop(20f).width(120f).height(40f)
    }

    private fun setupNPCContainer() {
        // Create styled health section
        val healthSection = Table()
        healthSection.background = headerBackground
        healthSection.pad(15f)

        val healthIcon = Label("❤", skin, "default")
        healthIcon.color = Color.RED
        healthSection.add(healthIcon).padRight(8f)
        healthSection.add(Label("Health:", skin)).padRight(10f)
        healthSection.add(npcHealthLabel)

        npcContainer.add(healthSection).growX().padBottom(15f).row()

        // Shop section with better styling
        val shopSection = Table()
        shopSection.background = contentBackground
        shopSection.pad(20f)

        val shopTitle = Label("🏪 Merchant Shop", skin, "title")
        shopTitle.color = Color(0.9f, 0.8f, 0.4f, 1f)
        shopSection.add(shopTitle).padBottom(15f).row()

        val descriptionLabel = Label("NPCs may offer to sell or give you items they find.", skin)
        descriptionLabel.wrap = true
        descriptionLabel.color = Color(0.8f, 0.8f, 0.9f, 1f)
        shopSection.add(descriptionLabel).width(280f).row()

        val comingSoonLabel = Label("Coming Soon™", skin, "small")
        comingSoonLabel.color = Color(0.6f, 0.6f, 0.7f, 1f)
        shopSection.add(comingSoonLabel).padTop(10f)

        npcContainer.add(shopSection).expand().fill()
    }

    fun show(enemy: GameEnemy) {
        window.titleLabel.setText("⚔ Enemy Loadout")
        window.titleLabel.color = Color(0.9f, 0.4f, 0.4f, 1f)
        enemyContainer.clear()
        npcContainer.isVisible = false
        enemyContainer.isVisible = true

        // Header section with health
        val headerSection = Table()
        headerSection.background = headerBackground
        headerSection.pad(15f)

        val healthIcon = Label("❤", skin, "default")
        healthIcon.color = Color.RED
        val healthText = "${enemy.health.toInt()} / ${enemy.enemyType.baseHealth.toInt()}"
        val healthLabel = Label(healthText, skin, "default")
        healthLabel.color = if (enemy.health / enemy.enemyType.baseHealth > 0.6f) Color.GREEN
        else if (enemy.health / enemy.enemyType.baseHealth > 0.3f) Color.YELLOW
        else Color.RED

        headerSection.add(healthIcon).padRight(8f)
        headerSection.add(Label("Health:", skin)).padRight(10f)
        headerSection.add(healthLabel)

        enemyContainer.add(headerSection).growX().padBottom(20f).row()

        // Inventory section
        val inventorySection = Table()
        inventorySection.background = contentBackground
        inventorySection.pad(20f)

        val inventoryTitle = Label("🎒 Inventory", skin, "title")
        inventoryTitle.color = Color(0.7f, 0.9f, 0.7f, 1f)
        inventorySection.add(inventoryTitle).padBottom(15f).row()

        // Consolidate items (keeping original logic)
        val enemyWeapons = enemy.weapons.toMutableMap()
        if (enemy.equippedWeapon != WeaponType.UNARMED) {
            val reserveAmmo = enemyWeapons.getOrDefault(enemy.equippedWeapon, 0)
            enemyWeapons[enemy.equippedWeapon] = reserveAmmo + enemy.currentMagazineCount
        }

        val totalMoneyValue = enemy.inventory
            .filter { it.itemType == ItemType.MONEY_STACK }
            .sumOf { it.value }

        // Build enhanced inventory grid
        if (enemyWeapons.isEmpty() && totalMoneyValue <= 0) {
            val emptyLabel = Label("Empty inventory", skin, "small")
            emptyLabel.color = Color(0.6f, 0.6f, 0.7f, 1f)
            inventorySection.add(emptyLabel)
        } else {
            val inventoryGrid = Table()
            var column = 0
            val maxColumns = 4

            // Money slot with enhanced design
            if (totalMoneyValue > 0) {
                val moneySlot = createEnhancedSlot(ItemType.MONEY_STACK, "$$totalMoneyValue", Color.GOLD)
                inventoryGrid.add(moneySlot).size(90f).pad(8f)
                column++
            }

            // Weapon slots with enhanced design
            for ((weaponType, ammo) in enemyWeapons) {
                if (weaponType == WeaponType.UNARMED) continue

                val itemType = ItemType.entries.find { it.correspondingWeapon == weaponType } ?: continue
                val weaponSlot = createEnhancedSlot(itemType, ammo.toString(), Color.WHITE)

                inventoryGrid.add(weaponSlot).size(90f).pad(8f)
                column++
                if (column >= maxColumns) {
                    inventoryGrid.row()
                    column = 0
                }
            }
            inventorySection.add(inventoryGrid)
        }

        enemyContainer.add(inventorySection).expand().fill()
        showWindow()
    }

    private fun createEnhancedSlot(itemType: ItemType, valueText: String, valueColor: Color): Stack {
        val slot = Stack()
        slot.add(Image(slotBackground))

        val texture = itemSystem.getTextureForItem(itemType)
        if (texture != null) {
            val itemImage = Image(texture)
            slot.add(itemImage)
        }

        // Enhanced value label with background
        val valueLabel = Label(valueText, skin, "small")
        valueLabel.color = valueColor

        // Create a small background for the value label - much more subtle
        val labelBg = Pixmap(40, 16, Pixmap.Format.RGBA8888)
        labelBg.setColor(Color(0f, 0f, 0f, 0.25f)) // Much more transparent
        labelBg.fill()
        labelBg.setColor(Color(0.2f, 0.2f, 0.2f, 0.4f)) // Lighter border
        labelBg.drawRectangle(0, 0, 40, 16)
        val labelBackground = TextureRegionDrawable(Texture(labelBg))
        labelBg.dispose()

        val valueContainer = Container(valueLabel)
        valueContainer.background = labelBackground
        valueContainer.align(Align.bottomRight)
        valueContainer.pad(2f)

        slot.add(valueContainer)
        return slot
    }

    fun show(npc: GameNPC) {
        window.titleLabel.setText("👤 NPC Details")
        window.titleLabel.color = Color(0.4f, 0.8f, 0.9f, 1f)
        npcContainer.clear()
        enemyContainer.isVisible = false
        npcContainer.isVisible = true

        // Rebuild NPC UI with enhanced styling
        npcHealthLabel.setText("${npc.health.toInt()} / ${npc.npcType.baseHealth.toInt()}")
        npcHealthLabel.color = if (npc.health / npc.npcType.baseHealth > 0.6f) Color.GREEN
        else if (npc.health / npc.npcType.baseHealth > 0.3f) Color.YELLOW
        else Color.RED

        setupNPCContainer()
        showWindow()
    }

    private fun showWindow() {
        window.pack()
        window.setSize(460f, 520f)
        window.setPosition(stage.width / 2f - window.width / 2f, stage.height / 2f - window.height / 2f)
        window.isVisible = true
        window.toFront()

        // Add subtle entrance animation effect
        window.color.a = 0f
        window.addAction(
            com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(0.3f)
        )
    }

    fun hide() {
        window.addAction(
            com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeOut(0.2f),
                com.badlogic.gdx.scenes.scene2d.actions.Actions.run {
                    window.isVisible = false
                    stage.unfocusAll()
                }
            )
        )
    }

    fun isVisible(): Boolean = window.isVisible

    fun dispose() {
        (slotBackground.region.texture as Texture).dispose()
        (slotBackgroundHover.region.texture as Texture).dispose()
        (headerBackground.region.texture as Texture).dispose()
        (contentBackground.region.texture as Texture).dispose()
    }
}
