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

    // UI containers for different character types
    private val enemyContainer: Table
    private val npcContainer: Table

    // NPC specific UI elements
    private val npcHealthLabel: Label

    init {
        // Create a reusable background for the inventory slots
        val pixmap = Pixmap(64, 64, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color(0.2f, 0.2f, 0.2f, 0.8f))
        pixmap.fill()
        pixmap.setColor(Color.DARK_GRAY)
        pixmap.drawRectangle(0, 0, 64, 64)
        slotBackground = TextureRegionDrawable(Texture(pixmap))
        pixmap.dispose()

        // Setup the main window
        window.isMovable = true
        window.padTop(40f)
        window.pad(15f)
        window.defaults().pad(5f)
        window.isVisible = false

        // --- Setup Enemy Container ---
        enemyContainer = Table()
        enemyContainer.align(Align.topLeft)

        // --- Setup NPC Container ---
        npcContainer = Table()
        npcHealthLabel = Label("", skin, "default")
        npcContainer.add(Label("Health:", skin)).padRight(10f)
        npcContainer.add(npcHealthLabel).row()
        npcContainer.add(Label("Shop (Coming Soon)", skin, "title")).colspan(2).padTop(20f).row()

// Create the label first
        val descriptionLabel = Label("NPCs may offer to sell or give you items they find.", skin)
        descriptionLabel.wrap = true // Set wrapping on the Label itself

// Now add the configured label to the table's cell
        npcContainer.add(descriptionLabel).colspan(2).width(250f)

        // Add both containers to the window (only one will be visible at a time)
        val contentStack = Stack()
        contentStack.add(enemyContainer)
        contentStack.add(npcContainer)

// Add the single Stack to the window. It will fill the available space.
        window.add(contentStack).expand().fill()

        val closeButton = TextButton("Close", skin)
        closeButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                hide()
            }
        })
        window.row()
        window.add(closeButton).colspan(2).padTop(15f)

        stage.addActor(window)
    }

    fun show(enemy: GameEnemy) {
        window.titleLabel.setText("Enemy Loadout")
        enemyContainer.clear() // Clear previous content
        npcContainer.isVisible = false
        enemyContainer.isVisible = true

        // --- 1. Display Enemy Health ---
        val healthLabel = Label("Health: ${enemy.health.toInt()} / ${enemy.enemyType.baseHealth.toInt()}", skin, "default")
        enemyContainer.add(healthLabel).left().padBottom(15f).row()

        // --- 2. Consolidate Ammo and Money ---
        // Consolidate ammo from the equipped weapon into the main map for accurate display
        val enemyWeapons = enemy.weapons.toMutableMap()
        if (enemy.equippedWeapon != WeaponType.UNARMED) {
            val reserveAmmo = enemyWeapons.getOrDefault(enemy.equippedWeapon, 0)
            enemyWeapons[enemy.equippedWeapon] = reserveAmmo + enemy.currentMagazineCount
        }

        // Consolidate all money stacks from the inventory into a single value
        val totalMoneyValue = enemy.inventory
            .filter { it.itemType == ItemType.MONEY_STACK }
            .sumOf { it.value }

        // --- 3. Build the Inventory Grid ---
        // Check if there's anything to display at all
        if (enemyWeapons.isEmpty() && totalMoneyValue <= 0) {
            enemyContainer.add(Label("No items in inventory.", skin))
        } else {
            val inventoryTable = Table()
            var column = 0
            val maxColumns = 3 // Items per row

            // A) Add the consolidated money slot if there is any money
            if (totalMoneyValue > 0) {
                val moneyItemType = ItemType.MONEY_STACK
                val texture = itemSystem.getTextureForItem(moneyItemType)

                val slot = Stack()
                slot.add(Image(slotBackground))
                if (texture != null) {
                    slot.add(Image(texture))
                }

                // Display the total value with a '$' prefix
                val valueLabel = Label("$$totalMoneyValue", skin, "small")
                valueLabel.color = Color.GREEN // Make the money value stand out
                val valueContainer = Container(valueLabel).align(Align.bottomRight).pad(3f)
                slot.add(valueContainer)

                inventoryTable.add(slot).size(70f).pad(5f)
                column++
            }

            // B) Add all weapon slots
            for ((weaponType, ammo) in enemyWeapons) {
                if (weaponType == WeaponType.UNARMED) continue

                val itemType = ItemType.entries.find { it.correspondingWeapon == weaponType } ?: continue
                val texture = itemSystem.getTextureForItem(itemType)

                val slot = Stack()
                slot.add(Image(slotBackground))
                if (texture != null) {
                    slot.add(Image(texture))
                }

                // Add ammo count label
                val ammoLabel = Label(ammo.toString(), skin, "small")
                val ammoContainer = Container(ammoLabel).align(Align.bottomRight).pad(3f)
                slot.add(ammoContainer)

                inventoryTable.add(slot).size(70f).pad(5f)
                column++
                if (column >= maxColumns) {
                    inventoryTable.row()
                    column = 0
                }
            }
            enemyContainer.add(inventoryTable)
        }

        showWindow()
    }

    fun show(npc: GameNPC) {
        window.titleLabel.setText("NPC Details")
        npcContainer.clear() // Clear and rebuild
        enemyContainer.isVisible = false
        npcContainer.isVisible = true

        // Rebuild NPC UI to ensure labels are present
        npcHealthLabel.setText("${npc.health.toInt()} / ${npc.npcType.baseHealth.toInt()}")
        npcContainer.add(Label("Health:", skin)).padRight(10f)
        npcContainer.add(npcHealthLabel).row()
        npcContainer.add(Label("Shop (Coming Soon)", skin, "title")).colspan(2).padTop(20f).row()

        // Create, configure, then add the label
        val descriptionLabel = Label("NPCs may offer to sell or give you items they find.", skin)
        descriptionLabel.wrap = true
        npcContainer.add(descriptionLabel).colspan(2).width(250f)

        showWindow()
    }

    private fun showWindow() {
        window.pack()
        window.setPosition(stage.width / 2f - window.width / 2f, stage.height / 2f - window.height / 2f)
        window.isVisible = true
        window.toFront()
    }

    fun hide() {
        window.isVisible = false
        stage.unfocusAll()
    }

    fun isVisible(): Boolean = window.isVisible

    fun dispose() {
        (slotBackground.region.texture as Texture).dispose()
    }
}
