package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import kotlin.random.Random

// Defines different types of crimes and their impact on the wanted level
enum class CrimeType(val wantedIncrease: Float) {
    SHOOT_WEAPON(0.5f),
    HIT_CIVILIAN(5f),
    KILL_CIVILIAN(20f),
    KILL_POLICE(35f),
    STEAL_CAR(10f),
    HIT_POLICE_CAR(8f)
}

data class ConfiscatedWeapon(
    val weaponType: WeaponType,
    val totalAmmo: Int,
    val soundVariationId: String?
)

class WantedSystem(
    private val sceneManager: SceneManager,
    private val playerSystem: PlayerSystem,
    private val enemySystem: EnemySystem,
    private val uiManager: UIManager,
    private val characterPhysicsSystem: CharacterPhysicsSystem
) {
    var currentWantedLevel = 0
        private set
    private var wantedProgress = 0f
    private var cooldownTimer = 0f

    private val activePolice = Array<GameEnemy>()
    private var isPlayerDead = false
    private var postDeathCooldownTimer = 0f

    // Defines how the police respond at each wanted level
    private val policeResponseConfig = mapOf(
        1 to PoliceLevelConfig(maxUnits = 2, spawnTypes = listOf(EnemyType.POLICEMAN), canShoot = false),
        2 to PoliceLevelConfig(maxUnits = 4, spawnTypes = listOf(EnemyType.POLICEMAN), canShoot = true, accuracy = 0.6f),
        3 to PoliceLevelConfig(maxUnits = 6, spawnTypes = listOf(EnemyType.POLICEMAN, EnemyType.DETECTIVE_OTTER), canShoot = true, accuracy = 0.75f),
        4 to PoliceLevelConfig(maxUnits = 8, spawnTypes = listOf(EnemyType.DETECTIVE_OTTER, EnemyType.OCTOPUS_OFFICER), canShoot = true, accuracy = 0.85f),
        5 to PoliceLevelConfig(maxUnits = 10, spawnTypes = listOf(EnemyType.OCTOPUS_OFFICER, EnemyType.POLICE_CHIEF), canShoot = true, accuracy = 0.9f)
    )

    private var surrenderTimer = 0f
    private val surrenderDuration = 2.0f // Player must be surrendering for this long
    private val arrestRespawnPoint = Vector3(50f, 2f, 50f) // Example: Police station location
    val confiscatedWeapons = mutableListOf<ConfiscatedWeapon>()

    // --- Public API ---

    fun reportCrime(crime: CrimeType) {
        if (sceneManager.currentScene != SceneType.WORLD) return

        // --- MODIFICATION ---
        // If the player was "playing possum", any new crime makes the police instantly aware again.
        if (isPlayerDead) {
            println("Player committed a crime after respawning. Police are aware again!")
            isPlayerDead = false
            postDeathCooldownTimer = 0f
        }

        if (currentWantedLevel < 5) {
            wantedProgress += crime.wantedIncrease
            println("Crime reported: ${crime.name}. Wanted progress: $wantedProgress")
        }
        cooldownTimer = COOLDOWN_TIME_BEFORE_DECREASE
    }

    // --- ADD THIS NEW METHOD ---
    fun onPlayerDied() {
        println("WantedSystem: Player has died. Wanted level will persist after respawn.")
        isPlayerDead = true
        // Set a very long cooldown. Randomly between 10 minutes (600s) and 30 minutes (1800s)
        postDeathCooldownTimer = Random.nextFloat() * (1800f - 600f) + 600f

        // Despawn all current police units. They will respawn when the player is back.
        despawnAllPolice()
    }

    // --- ADD THIS NEW METHOD ---
    fun onMissionStart() {
        println("WantedSystem: Mission started. Clearing wanted level.")
        reset()
    }

    fun getterConfiscatedWeapons(): List<ConfiscatedWeapon> = confiscatedWeapons.toList()

    fun buyBackWeapon(weaponToReturn: ConfiscatedWeapon): Boolean {
        // Find the ItemType that corresponds to this weapon
        val itemType = ItemType.entries.find { it.correspondingWeapon == weaponToReturn.weaponType }

        // Use the item's value for the cost, with a fallback
        val baseCost = itemType?.value ?: 50 // Default cost of 50 if no item is found
        val cost = baseCost * 5 // 5x markup to buy back

        if (playerSystem.getMoney() >= cost) {
            playerSystem.addMoney(-cost)
            playerSystem.addWeaponToInventory(weaponToReturn.weaponType, weaponToReturn.totalAmmo, weaponToReturn.soundVariationId)
            confiscatedWeapons.remove(weaponToReturn)
            uiManager.showTemporaryMessage("Retrieved ${weaponToReturn.weaponType.displayName}")
            return true
        } else {
            uiManager.showTemporaryMessage("Not enough money!")
            return false
        }
    }

    // --- Core Logic ---

    fun update(deltaTime: Float) {
        if (sceneManager.currentScene != SceneType.WORLD) {
            if (currentWantedLevel > 0) {
                cooldownTimer -= deltaTime // Wanted level cools down even in interiors
            }
        }

        if (isPlayerDead) {
            // If the player is dead and waiting, tick down the long timer.
            postDeathCooldownTimer -= deltaTime
            if (postDeathCooldownTimer <= 0f) {
                // The "playing possum" period is over. The police now "forget".
                println("Player has laid low long enough. Wanted level can now decrease.")
                isPlayerDead = false
            }
            // IMPORTANT: Do not process normal wanted level decay or police spawning during this time.
            return
        }

        // 1. Update Wanted Level based on progress
        if (wantedProgress >= WANTED_THRESHOLD && currentWantedLevel < 5) {
            currentWantedLevel++
            wantedProgress = 0f
            uiManager.updateWantedLevel(currentWantedLevel)
            println("Wanted level increased to $currentWantedLevel!")
        }

        // 2. Handle Cooldown and Level Decrease
        if (currentWantedLevel > 0) {
            cooldownTimer -= deltaTime
            if (cooldownTimer <= 0f) {
                wantedProgress -= DECAY_RATE * deltaTime
                if (wantedProgress <= 0f) {
                    currentWantedLevel--
                    wantedProgress = if (currentWantedLevel > 0) WANTED_THRESHOLD else 0f
                    uiManager.updateWantedLevel(currentWantedLevel)
                    println("Wanted level decreased to $currentWantedLevel.")
                    cooldownTimer = COOLDOWN_TIME_BEFORE_DECREASE
                }
            }
        }

        // If no longer wanted, despawn police and reset
        if (currentWantedLevel == 0 && activePolice.notEmpty()) {
            reset()
            return
        }

        // If player is in an interior, don't spawn or update police AI
        if (sceneManager.currentScene != SceneType.WORLD || currentWantedLevel == 0) return

        // 3. Manage Police Spawning
        managePoliceSpawning()

        // 4. Update Police AI and Behavior
        updatePoliceAI(deltaTime)

        // 5. Check for Surrender
        checkForSurrender(deltaTime)
    }

    private fun managePoliceSpawning() {
        val config = policeResponseConfig[currentWantedLevel] ?: return
        if (activePolice.size < config.maxUnits) {
            spawnPoliceUnit()
        }
    }

    private fun spawnPoliceUnit() {
        val config = policeResponseConfig[currentWantedLevel] ?: return
        val playerPos = playerSystem.getPosition()
        val spawnRadius = 80f // Spawn police within this radius
        val angle = Random.nextFloat() * 2 * Math.PI.toFloat()

        val spawnX = playerPos.x + kotlin.math.cos(angle) * spawnRadius
        val spawnZ = playerPos.z + kotlin.math.sin(angle) * spawnRadius
        val surfaceY = sceneManager.findHighestSupportY(spawnX, spawnZ, playerPos.y, 0.1f, sceneManager.game.blockSize)

        if (surfaceY < -500f) return // Invalid spawn point

        val policeType = config.spawnTypes.random()
        val spawnPos = Vector3(spawnX, surfaceY + policeType.height / 2f, spawnZ)

        val enemyConfig = EnemySpawnConfig(
            enemyType = policeType,
            behavior = EnemyBehavior.AGGRESSIVE_RUSHER, // Base behavior
            position = spawnPos,
            initialWeapon = WeaponType.REVOLVER, // Give them a default weapon
            ammoSpawnMode = AmmoSpawnMode.SET,
            setAmmoValue = 999 // Police have plenty of ammo
        )

        enemySystem.createEnemy(enemyConfig)?.let { police ->
            police.provocationLevel = 999f // Make them hostile immediately
            activePolice.add(police)
            sceneManager.activeEnemies.add(police) // Add to the main enemy list for rendering and physics
            println("Spawned a ${police.enemyType.displayName} unit.")
        }
    }

    private fun updatePoliceAI(deltaTime: Float) {
        val config = policeResponseConfig[currentWantedLevel] ?: return
        val playerPos = playerSystem.getPosition()

        val iterator = activePolice.iterator()
        while(iterator.hasNext()) {
            val police = iterator.next()

            // If a police unit is dead, remove it from our management pool
            if (police.health <= 0) {
                iterator.remove()
                continue
            }

            // Level 1: Follow and Intimidate, but don't shoot.
            if (currentWantedLevel == 1) {
                police.attackTimer = 1.0f // Prevent them from attacking
                val distanceToPlayer = police.position.dst(playerPos)
                if (distanceToPlayer > 10f) { // If far, move towards player
                    val direction = playerPos.cpy().sub(police.position).nor()
                    characterPhysicsSystem.update(police.physics, direction, deltaTime)
                } else { // If close, just stand still and watch
                    characterPhysicsSystem.update(police.physics, Vector3.Zero, deltaTime)
                }
            }
            // Higher Levels: Standard enemy AI takes over (chasing and shooting)
            else {
                police.attackTimer -= deltaTime // Allow them to attack
            }
        }
    }

    private fun checkForSurrender(deltaTime: Float) {
        if (currentWantedLevel != 1 || playerSystem.isDriving) {
            surrenderTimer = 0f
            return
        }

        val isUnarmed = playerSystem.equippedWeapon == WeaponType.UNARMED
        val isMovingSlowly = playerSystem.physicsComponent.velocity.len2() < 0.5f

        // Find the closest police officer
        val closestPolice = activePolice.minByOrNull { it.position.dst2(playerSystem.getPosition()) }
        val isNearPolice = closestPolice != null && closestPolice.position.dst(playerSystem.getPosition()) < 15f

        if (isUnarmed && isMovingSlowly && isNearPolice) {
            surrenderTimer += deltaTime
            uiManager.showTemporaryMessage("Surrendering... ${"%.1f".format(surrenderDuration - surrenderTimer)}s")
            if (surrenderTimer >= surrenderDuration) {
                handleArrest()
            }
        } else {
            surrenderTimer = 0f
        }
    }

    private fun handleArrest() {
        println("Player has been arrested!")
        uiManager.showTemporaryMessage("BUSTED!")

        // 1. Confiscate weapons
        confiscatedWeapons.clear()
        val weaponsToConfiscate = playerSystem.getWeaponInstances()
        for(weaponInstance in weaponsToConfiscate) {
            if (weaponInstance.weaponType != WeaponType.UNARMED) {
                val totalAmmo = playerSystem.getCurrentReserveAmmoFor(weaponInstance.weaponType) + playerSystem.getCurrentMagazineCountFor(weaponInstance.weaponType)
                confiscatedWeapons.add(ConfiscatedWeapon(weaponInstance.weaponType, totalAmmo, weaponInstance.soundVariationId))
            }
        }
        playerSystem.confiscateAllWeapons()

        // 2. Reset wanted level
        currentWantedLevel = 0
        wantedProgress = 0f
        uiManager.updateWantedLevel(0)

        isPlayerDead = false // Getting arrested clears the "playing dead" state.
        postDeathCooldownTimer = 0f

        // 3. Despawn police and reset system
        reset()

        // 4. Move player to "jail"
        playerSystem.setPosition(arrestRespawnPoint)
        sceneManager.cameraManager.resetAndSnapToPlayer(arrestRespawnPoint, false)
    }

    private fun despawnAllPolice() {
        for (police in activePolice) {
            // We just remove them from the main list. The EnemySystem's loop will handle fade-out/removal.
            sceneManager.activeEnemies.removeValue(police, true)
        }
        activePolice.clear()
    }

    fun reset() {
        println("Wanted level cleared. Despawning all police units.")
        despawnAllPolice() // Use the new helper method
        currentWantedLevel = 0
        wantedProgress = 0f
        cooldownTimer = 0f
        isPlayerDead = false // Reset the death flag
        postDeathCooldownTimer = 0f
        uiManager.updateWantedLevel(0)
    }

    // --- Constants ---
    companion object {
        const val WANTED_THRESHOLD = 100f
        const val COOLDOWN_TIME_BEFORE_DECREASE = 15f // in seconds
        const val DECAY_RATE = 5f // points per second
    }

    data class PoliceLevelConfig(
        val maxUnits: Int,
        val spawnTypes: List<EnemyType>,
        val canShoot: Boolean,
        val accuracy: Float = 1.0f // 1.0 = perfect, 0.0 = always miss
    )
}
