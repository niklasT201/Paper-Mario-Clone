package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import java.util.*
import kotlin.random.Random

enum class SpawnerType {
    PARTICLE, ITEM, WEAPON, ENEMY, NPC, CAR
}

// NEW: Spawner behavior modes
enum class SpawnerMode {
    CONTINUOUS, // Spawns repeatedly
    ONE_SHOT    // Spawns once and is then disabled
}

enum class AmmoSpawnMode {
    FIXED, // Uses the default value from the weapon's ItemType
    SET,   // Uses a specific value set in the spawner
    RANDOM // Uses a random value between a min/max
}

enum class CarSpawnDirection(val displayName: String) {
    LEFT("Left"),
    RIGHT("Right")
}

data class GameSpawner(
    val id: String = UUID.randomUUID().toString(),
    var position: Vector3,
    val gameObject: GameObject, // The visible purple cube in the world
    var sceneId: String = "WORLD",

    // --- General Spawner Settings ---
    var spawnerType: SpawnerType = SpawnerType.PARTICLE,
    var spawnInterval: Float = 5.0f,
    var timer: Float = spawnInterval,
    var spawnerMode: SpawnerMode = SpawnerMode.CONTINUOUS,
    var isDepleted: Boolean = false, // for ONE_SHOT mode

    var spawnOnlyWhenPreviousIsGone: Boolean = false,
    var spawnedEntityId: String? = null,

    var minSpawnRange: Float = 0f,
    var maxSpawnRange: Float = 100f,

    // --- Particle-specific settings ---
    var particleEffectType: ParticleEffectType = ParticleEffectType.SMOKE_FRAME_1,
    var minParticles: Int = 1,
    var maxParticles: Int = 3,

    // --- Item-specific settings ---
    var itemType: ItemType = ItemType.MONEY_STACK,
    var minItems: Int = 1,
    var maxItems: Int = 1,

    // --- Weapon-specific settings ---
    var weaponItemType: ItemType = ItemType.REVOLVER,
    var ammoSpawnMode: AmmoSpawnMode = AmmoSpawnMode.FIXED,
    var setAmmoValue: Int = 12,
    var randomMinAmmo: Int = 6,
    var randomMaxAmmo: Int = 18,

    // --- NEW: Enemy specific settings ---
    var enemyType: EnemyType = EnemyType.MOUSE_THUG,
    var enemyBehavior: EnemyBehavior = EnemyBehavior.STATIONARY_SHOOTER,
    var enemyHealthSetting: HealthSetting = HealthSetting.FIXED_DEFAULT,
    var enemyCustomHealth: Float = 100f,
    var enemyMinHealth: Float = 80f,
    var enemyMaxHealth: Float = 120f,
    var enemyInitialWeapon: WeaponType = WeaponType.UNARMED,
    var enemyWeaponCollectionPolicy: WeaponCollectionPolicy = WeaponCollectionPolicy.CANNOT_COLLECT,
    var enemyCanCollectItems: Boolean = true,
    var enemyInitialMoney: Int = 0,

    // NPC specific settings
    var npcType: NPCType = NPCType.GEORGE_MELES,
    var npcBehavior: NPCBehavior = NPCBehavior.WANDER,
    var npcIsHonest: Boolean = true,
    var npcCanCollectItems: Boolean = true,

    var carType: CarType = CarType.DEFAULT,
    var carIsLocked: Boolean = false,
    var carDriverType: String = "None", // "None", "Enemy", or "NPC"
    var carEnemyDriverType: EnemyType = EnemyType.MOUSE_THUG,
    var carNpcDriverType: NPCType = NPCType.GEORGE_MELES,
    var carSpawnDirection: CarSpawnDirection = CarSpawnDirection.LEFT,
    var upgradedWeapon: WeaponType? = null,
    var missionId: String? = null
)

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

        val currentSceneId = if (sceneManager.currentScene == SceneType.HOUSE_INTERIOR) {
            sceneManager.getCurrentHouse()?.id ?: "WORLD" // Fallback to WORLD if house ID is somehow null
        } else {
            "WORLD"
        }

        val weatherSystem = sceneManager.game.weatherSystem
        val rainIntensity = weatherSystem.getRainIntensity() // Use logical intensity for gameplay effects
        val isRaining = rainIntensity > 0.1f // A small threshold to consider it "raining"

        val modifiers = sceneManager.game.missionSystem.activeModifiers
        val activeSpawners = spawners.filter { spawner ->
            // Condition 1: The spawner MUST belong to the current scene.
            if (spawner.sceneId != currentSceneId) {
                return@filter false
            }

            // Condition 2: Check for mission modifiers that might disable this spawner.
            if (modifiers?.disableCharacterSpawners == true &&
                (spawner.spawnerType == SpawnerType.ENEMY || spawner.spawnerType == SpawnerType.NPC)) {
                return@filter false
            }

            when (spawner.spawnerType) {
                SpawnerType.CAR -> modifiers?.disableCarSpawners != true
                SpawnerType.ITEM -> modifiers?.disableItemSpawners != true
                SpawnerType.WEAPON -> modifiers?.disableItemSpawners != true // Also treated as an item spawner
                SpawnerType.PARTICLE -> modifiers?.disableParticleSpawners != true
                SpawnerType.ENEMY -> modifiers?.disableEnemySpawners != true
                SpawnerType.NPC -> modifiers?.disableNpcSpawners != true
            }
        }

        for (spawner in activeSpawners) {
            // Skip one-shot spawners that have already fired
            if (spawner.isDepleted) continue

            // If this spawner should only spawn when the previous entity is gone
            if (spawner.spawnOnlyWhenPreviousIsGone && spawner.spawnedEntityId != null) {
                // Check if the entity it spawned still exists
                val entityExists = when (spawner.spawnerType) {
                    SpawnerType.ITEM, SpawnerType.WEAPON ->
                        sceneManager.activeItems.any { it.id == spawner.spawnedEntityId && !it.isCollected }
                    SpawnerType.ENEMY ->
                        sceneManager.activeEnemies.any { it.id == spawner.spawnedEntityId }
                    SpawnerType.NPC ->
                        sceneManager.activeNPCs.any { it.id == spawner.spawnedEntityId }
                    else -> false
                }

                if (entityExists) {
                    spawner.timer = spawner.spawnInterval
                    continue // Skip to the next spawner
                } else {
                    // The item has been collected, so the spawner can forget about it and start its timer.
                    spawner.spawnedEntityId = null
                }
            }

            // Check if the player is within the spawner's activation range.
            val distanceSq = spawner.position.dst2(playerPosition)
            val isInRange = distanceSq >= getMinRangeSq(spawner) && distanceSq <= getMaxRangeSq(spawner)

            if (!isInRange) {
                spawner.timer = spawner.spawnInterval // Reset timer if player is out of range
                continue // Skip this spawner
            }

            // RAIN DELAY LOGIC
            var effectiveDeltaTime = deltaTime
            val isCharacterOrCarSpawner = spawner.spawnerType in listOf(SpawnerType.CAR, SpawnerType.ENEMY, SpawnerType.NPC)

            // Apply the delay only if it's raining, the spawner is for a character/car, AND it's in the world scene.
            if (isRaining && isCharacterOrCarSpawner && spawner.sceneId == "WORLD") {
                // At max rain intensity (1.0), spawner 6 times slower
                val rainSlowdownFactor = 1.0f + (5.0f * rainIntensity)
                effectiveDeltaTime /= rainSlowdownFactor
            }

            // Countdown the timer.
            spawner.timer -= effectiveDeltaTime

            if (spawner.timer <= 0f) {
                // Check for increased enemy spawn modifier
                val spawnCount = if (spawner.spawnerType == SpawnerType.ENEMY && modifiers?.increasedEnemySpawns == true) {
                    (2..4).random() // Spawn 2 to 4 enemies instead of just one
                } else {
                    1 // Default behavior: spawn one entity
                }

                for (i in 0 until spawnCount) {
                    // Time to spawn!
                    when (spawner.spawnerType) {
                        SpawnerType.PARTICLE -> spawnParticles(spawner)
                        SpawnerType.ITEM -> spawnItems(spawner)
                        SpawnerType.WEAPON -> spawnWeaponPickup(spawner)
                        SpawnerType.ENEMY -> spawnEnemyFromSpawner(spawner)
                        SpawnerType.NPC -> spawnNpcFromSpawner(spawner)
                        SpawnerType.CAR -> spawnCar(spawner)
                    }
                }

                // If it's a one-shot spawner, mark it as depleted.
                if (spawner.spawnerMode == SpawnerMode.ONE_SHOT) {
                    spawner.isDepleted = true
                }
                spawner.timer = spawner.spawnInterval // Reset timer for the next spawn
            }
        }
    }

    private fun spawnEnemyFromSpawner(spawner: GameSpawner) {
        val finalWeapon = spawner.upgradedWeapon ?: spawner.enemyInitialWeapon

        val config = EnemySpawnConfig(
            enemyType = spawner.enemyType,
            behavior = spawner.enemyBehavior,
            position = spawner.position.cpy(), // Spawn at the spawner's location
            healthSetting = spawner.enemyHealthSetting,
            customHealthValue = spawner.enemyCustomHealth,
            minRandomHealth = spawner.enemyMinHealth,
            maxRandomHealth = spawner.enemyMaxHealth,
            initialWeapon = finalWeapon,
            ammoSpawnMode = spawner.ammoSpawnMode,
            setAmmoValue = spawner.setAmmoValue,
            weaponCollectionPolicy = spawner.enemyWeaponCollectionPolicy,
            canCollectItems = spawner.enemyCanCollectItems
        )

        val newEnemy = sceneManager.enemySystem.createEnemy(config)
        if (newEnemy != null) {
            // Add initial money if specified
            if (spawner.enemyInitialMoney > 0) {
                val moneyItem = itemSystem.createItem(Vector3.Zero, ItemType.MONEY_STACK)!!
                moneyItem.value = spawner.enemyInitialMoney
                newEnemy.inventory.add(moneyItem)
            }

            sceneManager.activeEnemies.add(newEnemy)
            if (spawner.spawnOnlyWhenPreviousIsGone || spawner.spawnerMode == SpawnerMode.ONE_SHOT) {
                spawner.spawnedEntityId = newEnemy.id
            }
            println("Spawner ${spawner.id} spawned enemy ${newEnemy.enemyType.displayName}")
        }
    }

    private fun spawnNpcFromSpawner(spawner: GameSpawner) {
        val config = NPCSpawnConfig(
            npcType = spawner.npcType,
            behavior = spawner.npcBehavior,
            position = spawner.position.cpy(),
            isHonest = spawner.npcIsHonest,
            canCollectItems = spawner.npcCanCollectItems
        )

        val newNpc = sceneManager.npcSystem.createNPC(config)
        if (newNpc != null) {
            sceneManager.activeNPCs.add(newNpc)
            if (spawner.spawnOnlyWhenPreviousIsGone || spawner.spawnerMode == SpawnerMode.ONE_SHOT) {
                spawner.spawnedEntityId = newNpc.id
            }
            println("Spawner ${spawner.id} spawned NPC ${newNpc.npcType.displayName}")
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
                // Track the spawned item's ID if needed
                if (spawner.spawnOnlyWhenPreviousIsGone) {
                    spawner.spawnedEntityId = newItem.id
                }

                sceneManager.activeItems.add(newItem)
                println("Item spawner created ${newItem.itemType.displayName}. Value: ${newItem.itemType.value}")
            }
        }
    }

    private fun spawnWeaponPickup(spawner: GameSpawner) {
        val weaponItem = itemSystem.createItem(spawner.position, spawner.weaponItemType)
        if (weaponItem != null) {
            // Track the spawned weapon's ID if needed
            if (spawner.spawnOnlyWhenPreviousIsGone) {
                spawner.spawnedEntityId = weaponItem.id
            }
            // Calculate ammo based on the spawner's settings
            val ammoToGive = when (spawner.ammoSpawnMode) {
                AmmoSpawnMode.FIXED -> spawner.weaponItemType.ammoAmount
                AmmoSpawnMode.SET -> spawner.setAmmoValue
                AmmoSpawnMode.RANDOM -> {
                    if (spawner.randomMinAmmo >= spawner.randomMaxAmmo) spawner.randomMinAmmo
                    else Random.nextInt(spawner.randomMinAmmo, spawner.randomMaxAmmo + 1)
                }
            }

            // Set the calculated ammo on the individual item instance
            weaponItem.ammo = ammoToGive

            sceneManager.activeItems.add(weaponItem)

            println("Weapon spawner created ${weaponItem.itemType.displayName}. It will grant $ammoToGive ammo on pickup.")
        }
    }

    private fun spawnCar(spawner: GameSpawner) {
        val initialRotation = when (spawner.carSpawnDirection) {
            CarSpawnDirection.LEFT -> 0f
            CarSpawnDirection.RIGHT -> 180f
        }

        val newCar = sceneManager.game.carSystem.spawnCar(
            spawner.position,
            spawner.carType,
            spawner.carIsLocked,
            initialRotation
        )

        if (newCar != null) {
            // If the spawner is set to track the entity, store its ID
            if (spawner.spawnOnlyWhenPreviousIsGone || spawner.spawnerMode == SpawnerMode.ONE_SHOT) {
                spawner.spawnedEntityId = newCar.id
            }

            // Now, check if we need to add a driver
            when (spawner.carDriverType) {
                "Enemy" -> {
                    val enemyConfig = EnemySpawnConfig(
                        enemyType = spawner.carEnemyDriverType,
                        behavior = EnemyBehavior.AGGRESSIVE_RUSHER, // Default behavior for car drivers
                        position = newCar.position
                    )
                    val driver = sceneManager.enemySystem.createEnemy(enemyConfig)
                    if (driver != null) {
                        driver.enterCar(newCar)
                        driver.currentState = AIState.PATROLLING_IN_CAR
                        sceneManager.activeEnemies.add(driver)
                        println("Spawner ${spawner.id} spawned a ${spawner.carType.displayName} with an Enemy driver.")
                    }
                }
                "NPC" -> {
                    val npcConfig = NPCSpawnConfig(
                        npcType = spawner.carNpcDriverType,
                        behavior = NPCBehavior.WANDER, // Default behavior for car drivers
                        position = newCar.position
                    )
                    val driver = sceneManager.npcSystem.createNPC(npcConfig)
                    if (driver != null) {
                        driver.enterCar(newCar)
                        driver.currentState = NPCState.PATROLLING_IN_CAR
                        sceneManager.activeNPCs.add(driver)
                        println("Spawner ${spawner.id} spawned a ${spawner.carType.displayName} with an NPC driver.")
                    }
                }
                else -> {
                    println("Spawner ${spawner.id} spawned an empty ${spawner.carType.displayName}.")
                }
            }
        }
    }

    fun removeSpawner(spawner: GameSpawner) {
        sceneManager.activeSpawners.removeValue(spawner, true)
        // Also remove its associated GameObject from the scene to hide the purple cube
        sceneManager.activeObjects.removeValue(spawner.gameObject, true)
        println("Removed Spawner at ${spawner.position}")
    }
}
