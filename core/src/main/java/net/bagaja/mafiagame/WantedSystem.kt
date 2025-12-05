package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
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
    private val characterPhysicsSystem: CharacterPhysicsSystem,
    private val raycastSystem: RaycastSystem
) {
    var currentWantedLevel = 0
        private set
    private var wantedProgress = 0f
    private var cooldownTimer = 0f
    private var playerThreatTimer = 0f
    private val THREAT_TIME_THRESHOLD = 2.5f
    private val THREATENING_WEAPON_RADIUS = 25f
    private val BLOCKADE_DISTANCE = 40f
    private var lastBlockadeTime = 0f
    private val BLOCKADE_COOLDOWN = 20f

    val activePolice = Array<GameEnemy>()
    private val activePoliceCars = Array<GameCar>()
    private var isPlayerDead = false
    private var postDeathCooldownTimer = 0f
    private var policeSpawnTimer = 0f
    private val POLICE_SPAWN_INTERVAL = 4.0f

    // Defines how the police respond at each wanted level
    private val policeResponseConfig = mapOf(
        1 to PoliceLevelConfig(maxUnits = 2, spawnTypes = listOf(EnemyType.POLICEMAN), canShoot = false),
        2 to PoliceLevelConfig(maxUnits = 4, spawnTypes = listOf(EnemyType.POLICEMAN), canShoot = true, accuracy = 0.6f, carSpawnChance = 0.25f), // 25% chance
        3 to PoliceLevelConfig(maxUnits = 6, spawnTypes = listOf(EnemyType.POLICEMAN, EnemyType.DETECTIVE_OTTER), canShoot = true, accuracy = 0.75f, carSpawnChance = 0.50f), // 50% chance
        4 to PoliceLevelConfig(maxUnits = 8, spawnTypes = listOf(EnemyType.DETECTIVE_OTTER, EnemyType.OCTOPUS_OFFICER), canShoot = true, accuracy = 0.85f, carSpawnChance = 0.75f), // 75% chance
        5 to PoliceLevelConfig(maxUnits = 10, spawnTypes = listOf(EnemyType.OCTOPUS_OFFICER, EnemyType.POLICE_CHIEF), canShoot = true, accuracy = 0.9f, carSpawnChance = 1.0f) // 100% chance
    )

    private var surrenderTimer = 0f
    private val surrenderDuration = 2.0f // Player must be surrendering for this long
    private val arrestRespawnPoint = Vector3(50f, 2f, 50f) // Example: Police station location
    val confiscatedWeapons = mutableListOf<ConfiscatedWeapon>()
    private var bodyCheckTimer = 0f

    // --- Public API ---

    fun reportCrime(crime: CrimeType) {
        if (sceneManager.currentScene != SceneType.WORLD) return

        val modifiers = sceneManager.game.missionSystem.activeModifiers

        // Check: Is Wanted Level Disabled?
        if (modifiers?.disableWantedLevel == true) return

        if (isPlayerDead) { /* ... existing dead logic ... */ }

        // Check: Max Level Cap
        val maxLevel = modifiers?.maxWantedLevel ?: 5

        if (currentWantedLevel < maxLevel) {
            wantedProgress += crime.wantedIncrease
            println("Crime reported: ${crime.name}. Wanted progress: $wantedProgress")
        }
        cooldownTimer = COOLDOWN_TIME_BEFORE_DECREASE
    }

    fun reportCrimeByPolice(crime: CrimeType) {
        if (sceneManager.currentScene != SceneType.WORLD) return
        val modifiers = sceneManager.game.missionSystem.activeModifiers

        if (modifiers?.disableWantedLevel == true) return // Check disable

        val policeImpact = crime.wantedIncrease * 4f
        val maxLevel = modifiers?.maxWantedLevel ?: 5 // Check cap

        if (currentWantedLevel < maxLevel) {
            wantedProgress += policeImpact
            println("POLICE REPORT: ${crime.name}. Wanted progress increased by $policeImpact.")
        }
        cooldownTimer = COOLDOWN_TIME_BEFORE_DECREASE
    }

    fun onPlayerDied() {
        println("WantedSystem: Player has died. Wanted level will persist after respawn.")
        isPlayerDead = true
        // Set a very long cooldown. Randomly between 10 minutes (600s) and 30 minutes (1800s)
        postDeathCooldownTimer = Random.nextFloat() * (1800f - 600f) + 600f

        // Despawn all current police units. They will respawn when the player is back.
        despawnAllPolice()
    }

    fun onMissionStart() {
        println("WantedSystem: Mission started.")
        reset() // Clear existing stars first

        // Apply Fixed Start Level immediately
        val modifiers = sceneManager.game.missionSystem.activeModifiers
        if (modifiers?.fixedWantedLevel != null) {
            currentWantedLevel = modifiers.fixedWantedLevel!!
            wantedProgress = 0f
            uiManager.updateWantedLevel(currentWantedLevel)
            println("Mission enforced fixed wanted level: $currentWantedLevel")
        }
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
            return // Don't spawn blockades inside houses
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

        if (currentWantedLevel < 5 && !isPlayerDead && sceneManager.currentScene == SceneType.WORLD) {
            checkPoliceWitnesses(deltaTime)
        }

        // 1. Update Wanted Level based on progress
        if (wantedProgress >= WANTED_THRESHOLD && currentWantedLevel < 5) {
            currentWantedLevel++
            wantedProgress = 0f
            uiManager.updateWantedLevel(currentWantedLevel)
            println("Wanted level increased to $currentWantedLevel!")
        }

        // 1. Update Wanted Level based on progress
        val modifiers = sceneManager.game.missionSystem.activeModifiers
        val maxLevel = modifiers?.maxWantedLevel ?: 5

        if (wantedProgress >= WANTED_THRESHOLD && currentWantedLevel < maxLevel) {
            currentWantedLevel++
            wantedProgress = 0f
            uiManager.updateWantedLevel(currentWantedLevel)
            println("Wanted level increased to $currentWantedLevel!")
        }

        // 2. Handle Cooldown and Level Decrease
        val minLevel = modifiers?.fixedWantedLevel ?: 0

        if (currentWantedLevel > minLevel) {
            cooldownTimer -= deltaTime
            if (cooldownTimer <= 0f) {
                wantedProgress -= DECAY_RATE * deltaTime
                if (wantedProgress <= 0f) {
                    currentWantedLevel--
                    // Ensure we don't drop below the fixed floor
                    if (currentWantedLevel < minLevel) currentWantedLevel = minLevel

                    wantedProgress = if (currentWantedLevel > 0) WANTED_THRESHOLD else 0f
                    uiManager.updateWantedLevel(currentWantedLevel)
                    println("Wanted level decreased to $currentWantedLevel.")
                    cooldownTimer = COOLDOWN_TIME_BEFORE_DECREASE
                }
            }
        } else {
            // If we are at or below the fixed level, ensure the timer stays reset so we don't have pending decay
            cooldownTimer = COOLDOWN_TIME_BEFORE_DECREASE
            wantedProgress = 0f
        }

        // If no longer wanted, despawn police and reset
        if (currentWantedLevel == 0 && activePolice.notEmpty()) {
            reset()
            return
        }

        // Update spawn timer
        if (policeSpawnTimer > 0) {
            policeSpawnTimer -= deltaTime
        }

        // If player is in an interior, don't spawn or update police AI
        if (sceneManager.currentScene != SceneType.WORLD || currentWantedLevel == 0) return

        // 3. Manage Police Spawning
        spawnPoliceUnit()

        // 4. Update Police AI and Behavior
        updatePoliceAI(deltaTime)

        // 5. Check for Surrender
        checkForSurrender(deltaTime)

        // 6. Attempt Blockade (New Logic)
        if (currentWantedLevel >= 3) {
            attemptSpawnBlockade()
        }

        // 7. Check for Dead Bodies (Ultra Violence Mode)
        if (sceneManager.game.uiManager.getViolenceLevel() == ViolenceLevel.ULTRA_VIOLENCE && currentWantedLevel < 5) {
            bodyCheckTimer -= deltaTime
            if (bodyCheckTimer <= 0f) {
                checkForDeadBodyDiscovery()
                bodyCheckTimer = 0.5f // Check twice per second
            }
        }
    }

    private fun checkForDeadBodyDiscovery() {
        val playerPos = playerSystem.getPosition()

        // 1. Gather all dead bodies
        val deadBodies = mutableListOf<Vector3>()
        sceneManager.activeEnemies.filter { it.currentState == AIState.DEAD_BODY }.forEach { deadBodies.add(it.position) }
        sceneManager.activeNPCs.filter { it.currentState == NPCState.DEAD_BODY }.forEach { deadBodies.add(it.position) }

        if (deadBodies.isEmpty()) return

        // 2. Check if police see them
        for (police in activePolice) {
            if (police.health <= 0 || police.isInCar) continue

            for (bodyPos in deadBodies) {
                // Police must be close to body (visual range)
                if (police.position.dst(bodyPos) < 20f) {

                    // Player must be close to body (incriminating range)
                    if (playerPos.dst(bodyPos) < 8f) {

                        // Condition: Is anyone else nearby?
                        val witnessNear = sceneManager.activeNPCs.any {
                            it.currentState != NPCState.DEAD_BODY && it.currentState != NPCState.DYING &&
                                it.position.dst(bodyPos) < 10f
                        }

                        // Condition: Player is armed?
                        val playerArmed = playerSystem.equippedWeapon != WeaponType.UNARMED

                        // IF (No Witnesses OR Player Armed) -> Guilty!
                        if (!witnessNear || playerArmed) {
                            println("POLICE: Found body, player implicated! (Armed: $playerArmed, Witness: $witnessNear)")

                            // Instant 2 Stars if below
                            if (currentWantedLevel < 2) {
                                // Force progress high enough to trigger level up next frame
                                reportCrimeByPolice(CrimeType.KILL_CIVILIAN)
                                reportCrimeByPolice(CrimeType.KILL_CIVILIAN)
                            } else {
                                reportCrimeByPolice(CrimeType.KILL_CIVILIAN)
                            }

                            police.currentState = AIState.CHASING
                            return
                        }
                    }
                }
            }
        }
    }

    private fun checkPoliceWitnesses(deltaTime: Float) {
        val playerPos = playerSystem.getPosition()
        val threateningWeapons = listOf(WeaponType.TOMMY_GUN, WeaponType.MACHINE_GUN, WeaponType.DYNAMITE, WeaponType.SHOTGUN)
        var isThreatening = false

        for (police in activePolice) {
            if (police.isInCar || police.health <= 0) continue

            val distanceToPlayer = police.position.dst(playerPos)

            // 1. Check if police can SEE the player holding a threatening weapon
            if (distanceToPlayer < THREATENING_WEAPON_RADIUS) {
                if (playerSystem.equippedWeapon in threateningWeapons) {
                    isThreatening = true
                    break // Found one officer who sees you, no need to check others
                }
            }
        }

        if (isThreatening) {
            // Player is holding a threatening weapon in front of a cop. Start the timer.
            playerThreatTimer += deltaTime
            if (playerThreatTimer >= THREAT_TIME_THRESHOLD) {
                // Timer exceeded. Report the crime and reset the timer.
                reportCrimeByPolice(CrimeType.SHOOT_WEAPON) // Using this as a generic "threat" crime
                playerThreatTimer = 0f
            }
        } else {
            // Player is not holding a threatening weapon, or no cops are nearby.
            // Decay the timer so they don't get an instant star if they quickly switch back.
            if (playerThreatTimer > 0) {
                playerThreatTimer -= deltaTime * 2f // Timer decays twice as fast as it builds
                if (playerThreatTimer < 0) playerThreatTimer = 0f
            }
        }
    }

    private fun spawnPoliceUnit() {
        val config = policeResponseConfig[currentWantedLevel] ?: return
        val modifiers = sceneManager.game.missionSystem.activeModifiers

        // Check active count limit
        if (activePolice.size >= config.maxUnits) return

        // Check Timer
        if (policeSpawnTimer > 0) return

        // LOGIC CHANGE: Modifiable Spawn Rate
        // If multiplier is 0.5, interval becomes double (slower). If 2.0, interval becomes half (faster).
        val rateMultiplier = modifiers?.policeSpawnRateMultiplier ?: 1.0f
        // Avoid division by zero
        val actualInterval = if (rateMultiplier > 0.01f) POLICE_SPAWN_INTERVAL / rateMultiplier else 9999f

        // LOGIC CHANGE: Disable Cars
        val carsAllowed = !(modifiers?.disablePoliceCars ?: false)

        if (carsAllowed && Random.nextFloat() < config.carSpawnChance) {
            spawnPoliceCar()
        } else {
            spawnPoliceFootPatrol()
        }

        // Reset timer
        policeSpawnTimer = actualInterval
    }

    private fun spawnPoliceFootPatrol() {
        val config = policeResponseConfig[currentWantedLevel] ?: return
        val playerPos = playerSystem.getPosition()
        val spawnRadius = 80f
        val angle = Random.nextFloat() * 2 * Math.PI.toFloat()

        val spawnX = playerPos.x + kotlin.math.cos(angle) * spawnRadius
        val spawnZ = playerPos.z + kotlin.math.sin(angle) * spawnRadius
        val surfaceY = sceneManager.findHighestSupportY(spawnX, spawnZ, playerPos.y, 0.1f, sceneManager.game.blockSize)

        if (surfaceY < -500f) return

        val policeType = config.spawnTypes.random()
        val spawnPos = Vector3(spawnX, surfaceY + policeType.height / 2f, spawnZ)

        val modifiers = sceneManager.game.missionSystem.activeModifiers
        val healthMult = modifiers?.policeHealthMultiplier ?: 1.0f

        val enemyConfig = EnemySpawnConfig(
            enemyType = policeType,
            behavior = EnemyBehavior.AGGRESSIVE_RUSHER,
            position = spawnPos,
            initialWeapon = WeaponType.REVOLVER,
            ammoSpawnMode = AmmoSpawnMode.SET,
            setAmmoValue = 999,
            healthSetting = HealthSetting.FIXED_CUSTOM,
            customHealthValue = policeType.baseHealth * healthMult
        )

        enemySystem.createEnemy(enemyConfig)?.let { police ->
            police.provocationLevel = 999f
            activePolice.add(police)
            sceneManager.activeEnemies.add(police)
            println("Spawned a ${police.enemyType.displayName} foot patrol (HP: ${police.health}).")
        }
    }

    private fun spawnPoliceCar() {
        val playerPos = playerSystem.getPosition()
        val spawnCheckRadius = 100f // Check for roads in a larger area
        val spawnDistance = 80f    // How far away to actually spawn the car

        // Find a valid road block to spawn on
        val roadBlock = findRandomRoadBlockNear(playerPos, spawnCheckRadius)
        if (roadBlock == null) {
            println("WantedSystem: Could not find a road to spawn a police car. Spawning foot patrol instead.")
            spawnPoliceFootPatrol()
            return
        }

        val spawnPos = roadBlock.position.cpy().add(0f, 2f, 0f)

        // Spawn the car
        val policeCar = sceneManager.game.carSystem.spawnCar(spawnPos, CarType.POLICE_CAR, isLocked = false)
        if (policeCar == null) return

        val modifiers = sceneManager.game.missionSystem.activeModifiers
        val healthMult = modifiers?.policeHealthMultiplier ?: 1.0f

        // Apply health modifier to car
        policeCar.health = policeCar.carType.baseHealth * healthMult

        // Spawn two police officers to be the occupants
        val config = policeResponseConfig[currentWantedLevel] ?: return
        val driverType = config.spawnTypes.random()
        val passengerType = config.spawnTypes.random()

        val driverConfig = EnemySpawnConfig(
            enemyType = driverType,
            behavior = EnemyBehavior.AGGRESSIVE_RUSHER,
            position = policeCar.position,
            healthSetting = HealthSetting.FIXED_CUSTOM,
            customHealthValue = driverType.baseHealth * healthMult
        )
        val passengerConfig = EnemySpawnConfig(enemyType = passengerType, behavior = EnemyBehavior.AGGRESSIVE_RUSHER, position = policeCar.position, healthSetting = HealthSetting.FIXED_CUSTOM, customHealthValue = driverType.baseHealth * healthMult)

        val driver = enemySystem.createEnemy(driverConfig)
        val passenger = enemySystem.createEnemy(passengerConfig)

        if (driver != null && passenger != null) {
            driver.provocationLevel = 999f
            passenger.provocationLevel = 999f

            // Set their initial state
            driver.currentState = AIState.DRIVING_TO_SCENE
            driver.targetPosition = playerPos.cpy() // Their target is the player's last known location
            passenger.currentState = AIState.DRIVING_TO_SCENE

            // Put them in the car
            driver.enterCar(policeCar)
            // Note: We'll need to add a second seat to the car definition for this to work
            passenger.enterCar(policeCar)

            // Add them to all necessary lists
            activePolice.add(driver)
            activePolice.add(passenger)
            sceneManager.activeEnemies.add(driver)
            sceneManager.activeEnemies.add(passenger)

            println("Spawned a police car with two officers.")
        } else {
            // Cleanup if something went wrong
            sceneManager.activeCars.removeValue(policeCar, true)
        }
    }

    private fun findRandomRoadBlockNear(center: Vector3, radius: Float): GameBlock? {
        val roadTypes = BlockType.entries.filter { it.category == BlockCategory.STREET }
        val ray = Ray()

        // Try a few times to find a suitable spot
        for (i in 0..20) {
            val randomAngle = Random.nextFloat() * 2 * Math.PI.toFloat()
            val randomDist = radius * Random.nextFloat()

            val checkX = center.x + kotlin.math.cos(randomAngle) * randomDist
            val checkZ = center.z + kotlin.math.sin(randomAngle) * randomDist

            // Raycast from high above down to the ground
            ray.set(Vector3(checkX, 100f, checkZ), Vector3.Y.cpy().scl(-1f))

            val hitBlock = raycastSystem.getBlockAtRay(ray, sceneManager.activeChunkManager.getAllBlocks())

            // Check if the hit block is a road and not too high/low
            if (hitBlock != null && hitBlock.blockType in roadTypes && kotlin.math.abs(hitBlock.position.y - center.y) < 20f) {
                return hitBlock
            }
        }
        return null // No suitable road block found after several attempts
    }

    private fun updatePoliceAI(deltaTime: Float) {
        val config = policeResponseConfig[currentWantedLevel] ?: return
        val playerPos = playerSystem.getPosition()

        // FIX 4: Use a reverse for-loop instead of an iterator.
        // This prevents "ConcurrentModificationException" and the LibGDX nested iterator error
        // when removing items or modifying the list during the loop.
        for (i in activePolice.size - 1 downTo 0) {
            val police = activePolice[i]

            // If a police unit is dead, remove it from our management pool
            if (police.health <= 0) {
                activePolice.removeIndex(i)
                continue
            }

            if (police.isInCar) {
                val car = police.drivingCar
                // Safety check: if car is null but state says isInCar, eject them
                if (car == null) {
                    police.isInCar = false
                    continue
                }

                when (police.currentState) {
                    AIState.DRIVING_TO_SCENE -> {
                        val crimeScene = police.targetPosition
                        if (crimeScene == null || car.position.dst2(crimeScene) < 400f) {
                            // Arrived within 20 units

                            // FIX: Use index loop instead of 'for(seat in car.seats)' to avoid iterator crash
                            for (k in 0 until car.seats.size) {
                                val occupant = car.seats[k].occupant
                                if (occupant is GameEnemy && occupant.isInCar) {
                                    enemySystem.handleCarExit(occupant, sceneManager)
                                    occupant.currentState = AIState.CHASING
                                }
                            }
                        } else {
                            // Drive towards the crime scene
                            val direction = crimeScene.cpy().sub(car.position).nor()
                            car.updateAIControlled(deltaTime, direction, sceneManager, sceneManager.activeCars)
                        }
                    }
                    AIState.PATROLLING_AND_DESPAWNING -> {
                        // Job is done, drive away and despawn
                        if (police.stateTimer == 0f) police.stateTimer = Random.nextFloat() * 5f + 5f
                        police.stateTimer -= deltaTime

                        if (police.stateTimer <= 0f) {
                            // Despawn Logic
                            activePolice.removeIndex(i) // Remove self
                            sceneManager.activeEnemies.removeValue(police, true)

                            // If car is empty, remove car (simplified check)
                            if (car.seats.all { it.occupant == null || it.occupant == police }) {
                                sceneManager.activeCars.removeValue(car, true)
                            }
                            continue
                        }

                        // Drive along the nearest car path
                        var desiredMovement = Vector3.Zero
                        var targetNode = police.currentTargetPathNodeId?.let { sceneManager.game.carPathSystem.nodes[it] }
                        if (targetNode == null) {
                            targetNode = sceneManager.game.carPathSystem.findNearestNode(car.position)
                            police.currentTargetPathNodeId = targetNode?.id
                        }
                        if (targetNode != null) {
                            if (car.position.dst2(targetNode.position) < 25f) {
                                val nextNode = targetNode.nextNodeId?.let { sceneManager.game.carPathSystem.nodes[it] }
                                police.currentTargetPathNodeId = nextNode?.id
                            }
                            targetNode = police.currentTargetPathNodeId?.let { sceneManager.game.carPathSystem.nodes[it] }
                            targetNode?.let { desiredMovement = it.position.cpy().sub(car.position).nor() }
                        }
                        car.updateAIControlled(deltaTime, desiredMovement, sceneManager, sceneManager.activeCars)
                    }
                    else -> {
                        val direction = playerPos.cpy().sub(car.position).nor()
                        car.updateAIControlled(deltaTime, direction, sceneManager, sceneManager.activeCars)
                    }
                }
                // Sync police officer's logical position with the car they are in
                police.position.set(car.position)
            }
            else {
                // On Foot AI
                if (currentWantedLevel == 1) {
                    police.attackTimer = 1.0f
                    val distanceToPlayer = police.position.dst(playerPos)
                    if (distanceToPlayer > 10f) {
                        val direction = playerPos.cpy().sub(police.position).nor()
                        characterPhysicsSystem.update(police.physics, direction, deltaTime)
                    } else {
                        characterPhysicsSystem.update(police.physics, Vector3.Zero, deltaTime)
                    }
                } else {
                    police.attackTimer -= deltaTime
                }
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
        triggerPoliceDespawnSequence()

        // Clear wanted level from UI
        currentWantedLevel = 0
        wantedProgress = 0f
        uiManager.updateWantedLevel(0)
        isPlayerDead = false
        postDeathCooldownTimer = 0f

        // Move player to "jail"
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
        println("Wanted level cleared. Triggering police despawn sequence.")
        triggerPoliceDespawnSequence() // Use the new despawn sequence

        currentWantedLevel = 0
        wantedProgress = 0f
        cooldownTimer = 0f
        isPlayerDead = false
        postDeathCooldownTimer = 0f
        uiManager.updateWantedLevel(0)
    }

    private fun triggerPoliceDespawnSequence() {
        // Find all officers currently in cars
        val policeInCars = activePolice.filter { it.isInCar }
        val processedCars = mutableSetOf<String>()

        for (officer in policeInCars) {
            val car = officer.drivingCar
            if (car != null && !processedCars.contains(car.id)) {
                // Set all occupants of this car to the despawning state
                car.seats.forEach { seat ->
                    (seat.occupant as? GameEnemy)?.let { occupant ->
                        occupant.currentState = AIState.PATROLLING_AND_DESPAWNING
                        occupant.stateTimer = 0f // Reset timer to start the despawn countdown
                    }
                }
                processedCars.add(car.id)
            }
        }

        // Remove on-foot officers immediately
        val policeOnFoot = activePolice.filter { !it.isInCar }
        for (officer in policeOnFoot) {
            sceneManager.activeEnemies.removeValue(officer, true)
        }
        // Remove only the on-foot officers from the activePolice list
        activePolice.removeAll(Array(policeOnFoot.toTypedArray()), true)
    }

    fun reportCrimeByWitness(crime: CrimeType, location: Vector3) {
        if (sceneManager.currentScene != SceneType.WORLD) return

        // Crimes reported by witnesses have a slightly smaller impact
        val witnessImpact = crime.wantedIncrease * 0.75f

        if (currentWantedLevel < 5) {
            wantedProgress += witnessImpact
            println("WITNESS REPORT: ${crime.name} at $location. Wanted progress: $wantedProgress")
        }
        // Reporting a crime also resets the cooldown
        cooldownTimer = COOLDOWN_TIME_BEFORE_DECREASE
    }

    fun attemptSpawnBlockade() {
        if (sceneManager.currentScene != SceneType.WORLD) return

        // Only spawn if cooldown passed
        if (lastBlockadeTime > 0) {
            lastBlockadeTime -= Gdx.graphics.deltaTime
            return
        }

        val playerPos = playerSystem.getPosition()
        val playerVel = playerSystem.physicsComponent.velocity

        // Only spawn if player is moving fast enough (driving)
        if (playerVel.len2() < 50f) return

        // Predict where player is going
        val forwardDir = playerVel.cpy().nor()
        val spawnPos = playerPos.cpy().mulAdd(forwardDir, BLOCKADE_DISTANCE)

        // Snap to grid to align with blocks
        val blockSize = sceneManager.game.blockSize
        val gridX = kotlin.math.round(spawnPos.x / blockSize) * blockSize + blockSize / 2
        val gridZ = kotlin.math.round(spawnPos.z / blockSize) * blockSize + blockSize / 2
        val alignedPos = Vector3(gridX, spawnPos.y, gridZ)

        // Check if valid street
        if (isValidStreetBlock(alignedPos)) {
            spawnBlockadeAt(alignedPos, forwardDir)
            lastBlockadeTime = BLOCKADE_COOLDOWN
        }
    }

    private fun isValidStreetBlock(pos: Vector3): Boolean {
        // 1. Get the block directly at the coordinates
        val block = sceneManager.activeChunkManager.getBlockAtWorld(pos) ?: return false

        // 2. Check if the category is correct first
        if (block.blockType.category != BlockCategory.STREET) {
            return false
        }

        // 3. STRICT check: Exclude sidewalks explicitly.
        // We define a set of block types that represent actual drivable road.
        val validRoadTypes = setOf(
            BlockType.COBBLESTONE,
            BlockType.STONE,
            BlockType.STREET_DIRTY_PATH,
            BlockType.STREET_LOW,
            BlockType.STREET_INDUSTRY,
            BlockType.STREET_TILE,
            BlockType.DARK_STREET,
            BlockType.DARK_STREET_MIDDLE,
            BlockType.DARK_STREET_MIXED,
            BlockType.OLD_STREET
        )

        if (block.blockType !in validRoadTypes) {
            return false // It's a STREET category block (like sidewalk), but not a road.
        }

        // 4. Optional: Check height variance.
        if (kotlin.math.abs(pos.y - block.position.y) > 2f) {
            return false
        }

        return true
    }

    private fun spawnBlockadeAt(centerPos: Vector3, playerDirection: Vector3) {
        println("Spawning Police Blockade at $centerPos")

        // 1. Determine Blockade Orientation
        val isMovingX = kotlin.math.abs(playerDirection.x) > kotlin.math.abs(playerDirection.z)
        val carRotation = if (isMovingX) 90f else 0f

        // 2. Spawn The Police Car
        val surfaceY = sceneManager.findHighestSupportY(centerPos.x, centerPos.z, centerPos.y + 5f, 0.1f, 4f)
        val carPos = Vector3(centerPos.x, surfaceY, centerPos.z)

        val policeCar = sceneManager.game.carSystem.spawnCar(
            carPos,
            CarType.POLICE_CAR,
            isLocked = false,
            initialVisualRotation = carRotation
        )

        if (policeCar != null) {
            activePoliceCars.add(policeCar)

            // 3. Spawn Improvised Barricades (Barrels)
            if (Random.nextFloat() < 0.3f) {
                spawnBarricadeProps(policeCar, isMovingX)
            }

            // 4. Spawn Human Roadblock
            spawnOfficerLine(policeCar, isMovingX)
        }
    }

    private fun spawnBarricadeProps(car: GameCar, isCarNorthSouth: Boolean) {
        val offsetDist = 6f
        val offsets = if (isCarNorthSouth) {
            listOf(Vector3(0f, 0f, offsetDist), Vector3(0f, 0f, -offsetDist))
        } else {
            listOf(Vector3(offsetDist, 0f, 0f), Vector3(-offsetDist, 0f, 0f))
        }

        for (offset in offsets) {
            val propPos = car.position.cpy().add(offset)
            if (isValidStreetBlock(propPos)) {
                val barrel = sceneManager.game.interiorSystem.createInteriorInstance(InteriorType.BARREL)
                if (barrel != null) {
                    barrel.position.set(propPos)
                    barrel.updateTransform()
                    sceneManager.activeInteriors.add(barrel)
                }
            }
        }
    }

    private fun spawnOfficerLine(car: GameCar, isCarNorthSouth: Boolean) {
        val officersCount = Random.nextInt(2, 4)
        val spacing = 2.5f

        for (i in 1..officersCount) {
            val sideMultiplier = if (i % 2 == 0) 1f else -1f
            val distance = (3f + (spacing * ((i+1)/2))) * sideMultiplier

            val offset = if (isCarNorthSouth) {
                Vector3(0f, 0f, distance)
            } else {
                Vector3(distance, 0f, 0f)
            }

            val spawnPos = car.position.cpy().add(offset)

            if (isValidStreetBlock(spawnPos)) {
                val config = EnemySpawnConfig(
                    enemyType = EnemyType.POLICEMAN,
                    behavior = EnemyBehavior.STATIONARY_SHOOTER,
                    position = spawnPos,
                    initialWeapon = WeaponType.REVOLVER,
                    ammoSpawnMode = AmmoSpawnMode.SET,
                    setAmmoValue = 100
                )

                val officer = enemySystem.createEnemy(config)
                if (officer != null) {
                    activePolice.add(officer)
                    sceneManager.activeEnemies.add(officer)
                }
            }
        }
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
        val accuracy: Float = 1.0f,
        val carSpawnChance: Float = 0.0f // Chance (0.0 to 1.0) to spawn a car instead of a foot patrol
    )
}
