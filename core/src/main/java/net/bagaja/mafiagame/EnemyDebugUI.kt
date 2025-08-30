// In the file: EnemyDebugUI.kt
package net.bagaja.mafiagame

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.Window
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align

// THE ONLY CHANGE IS ON THIS NEXT LINE:
class EnemyDebugUI(private val skin: Skin, private val stage: Stage) {

    private val debugWindow: Window = Window("Enemy Inspector", skin, "dialog")
    private var currentEnemy: GameEnemy? = null

    // Labels to display the enemy's stats
    private val healthLabel: Label
    private val typeLabel: Label
    private val stateLabel: Label
    private val weaponLabel: Label
    private val ammoLabel: Label

    init {
        // Initialize the labels
        healthLabel = Label("Health:", skin)
        typeLabel = Label("Type:", skin)
        stateLabel = Label("AI State:", skin)
        weaponLabel = Label("Weapon:", skin)
        ammoLabel = Label("Ammo:", skin)

        // Setup the window layout
        debugWindow.isMovable = true
        debugWindow.padTop(40f) // Add padding for the title
        debugWindow.defaults().pad(5f).align(Align.left)

        debugWindow.add(healthLabel).row()
        debugWindow.add(typeLabel).row()
        debugWindow.add(stateLabel).row()
        debugWindow.add(weaponLabel).row()
        debugWindow.add(ammoLabel).row()

        val closeButton = TextButton("Close", skin)
        debugWindow.add(closeButton).padTop(15f).center().row()

        closeButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                hide()
            }
        })

        debugWindow.pack() // Size the window to its content
        debugWindow.isVisible = false
        stage.addActor(debugWindow)
    }

    /**
     * Shows the debug window and populates it with data from the selected enemy.
     */
    fun show(enemy: GameEnemy) {
        currentEnemy = enemy
        update() // Populate with initial data
        debugWindow.isVisible = true
        debugWindow.toFront() // Make sure it's on top of other UI elements
        // Center the window on the screen
        debugWindow.setPosition(
            (stage.width - debugWindow.width) / 2f,
            (stage.height - debugWindow.height) / 2f
        )
    }

    /**
     * Hides the debug window.
     */
    fun hide() {
        debugWindow.isVisible = false
        currentEnemy = null
        stage.unfocusAll() // <-- ADD THIS LINE
    }

    /**
     * Updates the text of the labels with the current enemy's stats.
     */
    fun update() {
        val enemy = currentEnemy ?: return

        healthLabel.setText("Health: ${enemy.health.toInt()} / ${enemy.enemyType.baseHealth.toInt()}")
        typeLabel.setText("Type: ${enemy.enemyType.displayName}")
        stateLabel.setText("AI State: ${enemy.currentState.name}")
        weaponLabel.setText("Weapon: ${enemy.equippedWeapon.displayName}")

        // Calculate total ammo (magazine + reserves) for display
        val totalAmmo = enemy.currentMagazineCount + enemy.ammo
        ammoLabel.setText("Ammo: $totalAmmo (${enemy.currentMagazineCount} in mag)")

        debugWindow.pack() // Resize window to fit the potentially longer text
    }

    // You can call this from your main render loop if you want live updates
    fun act(delta: Float) {
        if (debugWindow.isVisible) {
            update()
        }
    }

    fun dispose() {
        println("EnemyDebugUI disposed.")
    }
}
