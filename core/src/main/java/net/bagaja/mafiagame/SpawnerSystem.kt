package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import java.util.*
import kotlin.random.Random

enum class SpawnerType {
    PARTICLE, ITEM, WEAPON
}

enum class AmmoSpawnMode {
    FIXED, // Uses the default value from the weapon's ItemType
    SET,   // Uses a specific value set in the spawner
    RANDOM // Uses a random value between a min/max
}

/**
 * MODIFIED: Data class to hold all spawner instance data. Replaces GameParticleSpawner.
 */
data class GameSpawner(
    val id: String = UUID.randomUUID().toString(),
    var position: Vector3,
    val gameObject: GameObject, // The visible purple cube in the world

    // General Spawner Settings
    var spawnerType: SpawnerType = SpawnerType.PARTICLE,
    var spawnInterval: Float = 2.0f,
    var timer: Float = spawnInterval,

    // NEW: Special range settings for anti-AFK farming
    var minSpawnRange: Float = 15f, // Spawning stops if player is closer than this
    var maxSpawnRange: Float = 100f, // Spawning only happens if player is within this range

    // Particle-specific settings
    var particleEffectType: ParticleEffectType = ParticleEffectType.SMOKE_FRAME_1,
    var minParticles: Int = 1,
    var maxParticles: Int = 3,

    // Item-specific settings
    var itemType: ItemType = ItemType.MONEY_STACK,
    var minItems: Int = 1,
    var maxItems: Int = 1,

    // Weapon-specific settings
    var weaponItemType: ItemType = ItemType.REVOLVER, // Spawns the item corresponding to the weapon

    // Ammo settings
    var ammoSpawnMode: AmmoSpawnMode = AmmoSpawnMode.FIXED,
    var setAmmoValue: Int = 12,
    var randomMinAmmo: Int = 6,
    var randomMaxAmmo: Int = 18
)

/**
 * MODIFIED: Manages the update loop for all particle, item, and weapon spawners in the scene.
 */
class SpawnerSystem(
    private val particleSystem: ParticleSystem,
    private val itemSystem: ItemSystem,
) {
    lateinit var sceneManager: SceneManager
    // Convert ranges to squared values for more efficient distance checks
    private fun getMaxRangeSq(spawner: GameSpawner) = spawner.maxSpawnRange * spawner.maxSpawnRange
    private fun getMinRangeSq(spawner: GameSpawner) = spawner.minSpawnRange * spawner.minSpawnRange

    /**
     * Iterates through all spawners and triggers emissions based on their timers and ranges.
     */
    fun update(deltaTime: Float, spawners: Array<GameSpawner>, playerPosition: Vector3) {
        if (spawners.isEmpty) return

        for (spawner in spawners) {
            val distanceSq = spawner.position.dst2(playerPosition)

            // NEW: Range check logic
            val isInRange = distanceSq > getMinRangeSq(spawner) && distanceSq < getMaxRangeSq(spawner)
            if (!isInRange) {
                spawner.timer = spawner.spawnInterval // Reset timer if player is out of range
                continue // Skip this spawner
            }

            spawner.timer -= deltaTime
            if (spawner.timer <= 0f) {
                // Time to spawn!
                when (spawner.spawnerType) {
                    SpawnerType.PARTICLE -> spawnParticles(spawner)
                    SpawnerType.ITEM -> spawnItems(spawner)
                    SpawnerType.WEAPON -> spawnWeaponPickup(spawner)
                }
                spawner.timer += spawner.spawnInterval
            }
        }
    }

    private fun spawnParticles(spawner: GameSpawner) {
        val particleCount = if (spawner.minParticles >= spawner.maxParticles) {
            spawner.minParticles
        } else {
            Random.nextInt(spawner.minParticles, spawner.maxParticles + 1)
        }

        for (i in 0 until particleCount) {
            val baseDirection = when (spawner.particleEffectType) {
                ParticleEffectType.SMOKE_FRAME_1,
                ParticleEffectType.SMOKE_FRAME_2,
                ParticleEffectType.FACTORY_SMOKE_INITIAL,
                ParticleEffectType.FACTORY_SMOKE_THICK,
                ParticleEffectType.FIRE_FLAME -> Vector3.Y.cpy()
                else -> null
            }
            particleSystem.spawnEffect(
                type = spawner.particleEffectType,
                position = spawner.position,
                baseDirection = baseDirection
            )
        }
    }

    private fun spawnItems(spawner: GameSpawner) {
        val itemCount = if (spawner.minItems >= spawner.maxItems) {
            spawner.minItems
        } else {
            Random.nextInt(spawner.minItems, spawner.maxItems + 1)
        }

        for (i in 0 until itemCount) {
            val newItem = itemSystem.createItem(spawner.position, spawner.itemType)
            if (newItem != null) {
                sceneManager.activeItems.add(newItem)
                itemSystem.setActiveItems(sceneManager.activeItems)
                // NEW: Placeholder for giving money when this item is picked up.
                // You can add a property to GameItem like 'moneyValue' and check it in PlayerSystem on pickup.
                println("Item spawner created ${newItem.itemType.displayName}. Value: ${newItem.itemType.value}")
            }
        }
    }

    private fun spawnWeaponPickup(spawner: GameSpawner) {
        val weaponItem = itemSystem.createItem(spawner.position, spawner.weaponItemType)
        if (weaponItem != null) {
            // Calculate ammo based on the spawner's settings
            val ammoToGive = when (spawner.ammoSpawnMode) {
                AmmoSpawnMode.FIXED -> {
                    // Use the default ammo amount from the item's definition
                    spawner.weaponItemType.ammoAmount
                }
                AmmoSpawnMode.SET -> {
                    // Use the specific value set on the spawner
                    spawner.setAmmoValue
                }
                AmmoSpawnMode.RANDOM -> {
                    // Generate a random amount within the spawner's defined range
                    if (spawner.randomMinAmmo >= spawner.randomMaxAmmo) {
                        spawner.randomMinAmmo
                    } else {
                        Random.nextInt(spawner.randomMinAmmo, spawner.randomMaxAmmo + 1)
                    }
                }
            }

            // Set the calculated ammo on the individual item instance
            weaponItem.ammo = ammoToGive

            sceneManager.activeItems.add(weaponItem)
            itemSystem.setActiveItems(sceneManager.activeItems)

            println("Weapon spawner created ${weaponItem.itemType.displayName}. It will grant $ammoToGive ammo on pickup.")
        }
    }

    fun removeSpawner(spawner: GameSpawner) {
        sceneManager.activeSpawners.removeValue(spawner, true)
        // Also remove its associated GameObject from the scene to hide the purple cube
        sceneManager.activeObjects.removeValue(spawner.gameObject, true)
        println("Removed Spawner at ${spawner.position}")
    }
}
