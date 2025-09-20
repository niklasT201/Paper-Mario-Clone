package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectMap
import kotlin.math.floor
import kotlin.random.Random

enum class HitObjectType {
    NONE,
    BLOCK,
    HOUSE,
    OBJECT,
    INTERIOR,
    ENEMY,
    NPC,
    CAR,
    PLAYER
}

data class CollisionResult(
    val type: HitObjectType,
    val hitObject: Any?,
    val hitPoint: Vector3,
    val surfaceNormal: Vector3
)

enum class PlayerState {
    IDLE,
    ATTACKING,      // For shooting and melee swings
    CHARGING_THROW  // For holding down the mouse with a throwable weapon
}

class PlayerSystem {
    private lateinit var playerTexture: Texture
    private lateinit var playerModel: Model
    lateinit var playerInstance: ModelInstance
    private var currentSeat: CarSeat? = null
    private lateinit var playerMaterial: Material

    // Custom shader for billboard lighting
    private lateinit var billboardShaderProvider: BillboardShaderProvider
    private lateinit var billboardModelBatch: ModelBatch

    private var health: Float = 100f
        private set
    private val maxHealth: Float = 100f
    val basePlayerSpeed = 8f
    private var isOnFire: Boolean = false
    private var onFireTimer: Float = 0f
    private var initialOnFireDuration: Float = 0f
    private var onFireDamagePerSecond: Float = 0f
    private var money: Int = 0
    private lateinit var sceneManager: SceneManager

    fun takeDamage(amount: Float) {
        // Check for the active mission modifier
        val modifiers = sceneManager.game.missionSystem.activeModifiers

        // Check for invincibility first
        if (modifiers?.setUnlimitedHealth == true) {
            println("Player is invincible due to mission modifier. Damage blocked.")
            return
        }

        var finalDamage = amount
        // Apply incoming damage multiplier
        if (modifiers != null) {
            finalDamage *= modifiers.incomingDamageMultiplier
            if (modifiers.incomingDamageMultiplier != 1.0f) {
                println("Incoming damage modified by ${modifiers.incomingDamageMultiplier}x. New damage: $finalDamage")
            }
        }

        if (isDriving) return
        if (health > 0) {
            health -= finalDamage // Use the final calculated damage
            println("Player took damage! Health is now: ${health.toInt()}")
            if (health <= 0) {
                health = 0f
                println("Player has been defeated!")
                // You can add logic here for player death/respawn
            }
        }
    }

    fun getHealthPercentage(): Float {
        if (maxHealth <= 0) return 0f
        return (health / maxHealth) * 100f
    }

    companion object {
        const val FALL_SPEED = 25f
        const val MAX_STEP_HEIGHT = 1.1f
        const val CAR_MAX_STEP_HEIGHT = 4.1f
        const val HEADLIGHT_INTENSITY = 18f
    }

    private lateinit var characterPhysicsSystem: CharacterPhysicsSystem
    lateinit var physicsComponent: PhysicsComponent

    // Player position and movement
    val playerSize = Vector3(3f, 4f, 3f) //z = thickness

    // Player rotation for Paper Mario effect
    private var playerTargetRotationY = 0f
    var playerCurrentRotationY = 0f
    private val rotationSpeed = 360f
    private var lastMovementDirection = 0f
    private lateinit var playerBackTexture: Texture
    private var isPressingW = false
    private var isHoldingShootButton = false
    private val isRotating: Boolean
        get() {
            return kotlin.math.abs(playerCurrentRotationY - playerTargetRotationY) > 1f
        }

    // Animation system
    private lateinit var animationSystem: AnimationSystem
    private var isMoving = false
    private var lastIsMoving = false

    // Wipe effect
    private var continuousMovementTimer = 0f
    private var wipeEffectTimer = 0f
    private val WIPE_EFFECT_INTERVAL = 0.15f

    private var blockSize = 4f
    private lateinit var particleSystem: ParticleSystem
    private val PARTICLE_IMPACT_OFFSET = 1.2f

    var isDriving = false
        private set
    private var drivingCar: GameCar? = null
    private val carSpeed = 20f // Speed is still relevant

    private var state = PlayerState.IDLE
    private var attackTimer = 0f // Timer for how long the ATTACKING state lasts
    private var throwChargeTime = 0f
        private set
    private val ammoReserves = mutableMapOf<WeaponType, Int>()
    private var currentMagazineCount = 0
        private set
    private val currentMagazineCounts = mutableMapOf<WeaponType, Int>()
    private var isReloading = false
    private var reloadTimer = 0f

    var equippedWeapon: WeaponType = WeaponType.UNARMED
    private var weapons: MutableList<WeaponType> = mutableListOf(WeaponType.UNARMED)
    private var currentWeaponIndex = 0

    private var isShooting = false
    private val shootingPoseDuration = 0.2f
    private val MIN_THROW_CHARGE_TIME = 0.1f
    private var shootingPoseTimer = 0f
    private var fireRateTimer = 0f
    private var chargeTime = 0f
    private val minShotScale = 0.7f // The initial size of the particle on a quick tap
    private val maxShotScale = 2.0f // The maximum size limit for the particle
    private val chargeDurationForMaxScale = 10f
    private var isMuzzleFlashLightEnabled = true

    private var muzzleFlashLight: LightSource? = null
    private var muzzleFlashTimer = 0f

    // Caches for weapon assets to avoid loading them repeatedly
    private val poseTextures = mutableMapOf<String, Texture>()
    val bulletModels = mutableMapOf<String, Model>()
    private val throwableModels = mutableMapOf<WeaponType, Model>()

    private var teleportCooldown = 0f
    lateinit var bloodPoolSystem: BloodPoolSystem
    private lateinit var footprintSystem: FootprintSystem

    private var bloodyFootprintsTimer = 0f
    private val BLOODY_FOOTPRINT_COOLDOWN = 10f // Effect lasts 10 seconds after leaving a pool
    private var footprintSpawnTimer = 0f
    private val FOOTPRINT_SPAWN_INTERVAL = 0.35f // One print every 0.35 seconds of movement

    private lateinit var lightingManager: LightingManager
    private var headlightLight: LightSource? = null
    private val headlightForwardOffset = 10f // How far in front of the car center the light is
    private val headlightVerticalOffset = 2.0f

    fun getPlayerBounds(): BoundingBox {
        return physicsComponent.bounds
    }

    fun initialize(blockSize: Float, particleSystem: ParticleSystem, lightingManager: LightingManager, bloodPoolSystem: BloodPoolSystem, footprintSystem: FootprintSystem, characterPhysicsSystem: CharacterPhysicsSystem, sceneManager: SceneManager) {
        this.blockSize = blockSize
        this.particleSystem = particleSystem
        this.lightingManager = lightingManager
        this.bloodPoolSystem = bloodPoolSystem
        this.footprintSystem = footprintSystem
        this.characterPhysicsSystem = characterPhysicsSystem
        this.sceneManager = sceneManager
        physicsComponent = PhysicsComponent(
            position = Vector3(0f, 2f, 0f), // Default start position
            size = this.playerSize,
            speed = 8f // Player's specific speed
        )

        setupAnimationSystem()

        // Load weapon
        setupWeaponAssets()

        setupBillboardShader()
        setupPlayerModel()
        physicsComponent.updateBounds()

        // Set initial weapon state
        currentMagazineCount = equippedWeapon.magazineSize

        // Create the muzzle flash light source once and keep it
        val light = LightSource(id = -1, position = Vector3(), intensity = 0f, range = 0f)
        muzzleFlashLight = light
        // We add the PointLight to the environment so it can be rendered
        lightingManager.getEnvironment().add(light.createPointLight())

        val carLight = LightSource(
            id = -2, // A different negative ID to be safe
            position = Vector3(),
            intensity = 0f, // Start OFF
            range = 45f,
            color = Color(1f, 1f, 0.8f, 1f)
        )
        headlightLight = carLight
        // Directly add its PointLight to the environment. It is now a transient effect.
        lightingManager.getEnvironment().add(carLight.createPointLight())
    }

    private fun setupBillboardShader() {
        billboardShaderProvider = BillboardShaderProvider()
        billboardModelBatch = ModelBatch(billboardShaderProvider)

        // Updated configuration for better billboard lighting
        billboardShaderProvider.setBillboardLightingStrength(0.9f)
        billboardShaderProvider.setMinLightLevel(0.3f)
    }

    private fun setupAnimationSystem() {
        animationSystem = AnimationSystem()

        // Create walking animation
        val walkingFrames = arrayOf(
            "textures/player/animations/walking/walking_left.png",
            "textures/player/animations/walking/walking_right.png",
        )

        // Create walking animation with 0.15 seconds per frame (about 6.7 fps for smooth walking)
        animationSystem.createAnimation("walking", walkingFrames, 0.4f, true)

        // Create idle animation (single frame)
        val idleFrames = arrayOf("textures/player/pig_character.png")
        animationSystem.createAnimation("idle", idleFrames, 1.0f, true)
        playerBackTexture = Texture(Gdx.files.internal("textures/player/pig_character_back.png"))

        // Punch Animation
        animationSystem.createAnimation(
            "attack_punch",
            arrayOf(
                "textures/player/weapons/punch/player_punch_one.png",
                "textures/player/weapons/punch/player_punch_two.png",
                "textures/player/weapons/punch/player_punch_three.png"
            ),
            0.15f,
            false
        )

        // Baseball Bat Animation (updated)
        animationSystem.createAnimation(
            "attack_baseball_bat",
            arrayOf(
                "textures/player/weapons/baseball_bat/player_baseball_bat.png",
                "textures/player/weapons/baseball_bat/player_baseball_bat_two.png",
                "textures/player/weapons/baseball_bat/player_baseball_bat_three.png"
            ),
            0.15f,
            false
        )

        // Knife Animation (updated)
        animationSystem.createAnimation(
            "attack_knife",
            arrayOf(
                "textures/player/weapons/knife/player_knife.png",
                "textures/player/weapons/knife/player_knife_two.png"
            ),
            0.09f,
            false
        )


        // Start with idle animation
        animationSystem.playAnimation("idle")
    }

    private fun setupPlayerModel() {
        val modelBuilder = ModelBuilder()

        // Get initial texture from animation system
        playerTexture = animationSystem.getCurrentTexture() ?: Texture(Gdx.files.internal("textures/player/pig_character.png"))

        // Create player material with the texture
        playerMaterial = Material(
            TextureAttribute.createDiffuse(playerTexture),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
            IntAttribute.createCullFace(GL20.GL_NONE)
        )

        // Create a 3D plane/quad for the player (billboard)
        val playerSizeVisual = 4f
        playerModel = modelBuilder.createRect(
            -playerSizeVisual / 2, -playerSizeVisual / 2, 0f,
            playerSizeVisual / 2, -playerSizeVisual / 2, 0f,
            playerSizeVisual / 2, playerSizeVisual / 2, 0f,
            -playerSizeVisual / 2, playerSizeVisual / 2, 0f,
            0f, 0f, 1f,
            playerMaterial,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
        )

        // Create player instance
        playerInstance = ModelInstance(playerModel)
        playerInstance.userData = "character"
        updatePlayerTransform()
    }

    fun setPosition(newPosition: Vector3) {
        physicsComponent.position.set(newPosition)
        physicsComponent.updateBounds()
        println("Player position set to: $newPosition")
    }

    private fun setupWeaponAssets() {
        val modelBuilder = ModelBuilder()
        for (weapon in WeaponType.entries) {
            // Load player pose texture
            if (!poseTextures.containsKey(weapon.playerPoseTexturePath)) {
                poseTextures[weapon.playerPoseTexturePath] = Texture(Gdx.files.internal(weapon.playerPoseTexturePath))
            }

            // Load bullet model if it's a shooting weapon and we haven't loaded it yet
            weapon.bulletTexturePath?.let { path ->
                if (!bulletModels.containsKey(path)) {
                    val bulletTexture = Texture(Gdx.files.internal(path))
                    val bulletMaterial = Material(
                        TextureAttribute.createDiffuse(bulletTexture),
                        BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                        IntAttribute.createCullFace(GL20.GL_NONE)
                    )
                    bulletModels[path] = modelBuilder.createRect(
                        -0.3f, -0.15f, 0f, 0.3f, -0.15f, 0f, 0.3f, 0.15f, 0f, -0.3f, 0.15f, 0f,
                        0f, 0f, 1f, bulletMaterial,
                        (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
                    )
                }
            }
            if (weapon.actionType == WeaponActionType.THROWABLE) {
                if (!throwableModels.containsKey(weapon)) {
                    // Use the item texture for the thrown model
                    val itemType = ItemType.entries.find { it.correspondingWeapon == weapon }
                    if (itemType != null) {
                        val texture = Texture(Gdx.files.internal(itemType.texturePath))
                        val material = Material(
                            TextureAttribute.createDiffuse(texture),
                            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                            IntAttribute.createCullFace(GL20.GL_NONE)
                        )
                        // Create a simple billboard model for the thrown object
                        throwableModels[weapon] = modelBuilder.createRect(
                            -0.5f, -0.5f, 0f, 0.5f, -0.5f, 0f, 0.5f, 0.5f, 0f, -0.5f, 0.5f, 0f,
                            0f, 0f, 1f, material,
                            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
                        )
                    }
                }
            }
        }
    }

    fun isChargingThrow(): Boolean {
        return state == PlayerState.CHARGING_THROW
    }

    private fun addAmmo(weaponType: WeaponType, amount: Int) {
        if (weaponType.actionType == WeaponActionType.SHOOTING || weaponType.actionType == WeaponActionType.THROWABLE) {
            val currentAmmo = ammoReserves.getOrDefault(weaponType, 0)
            ammoReserves[weaponType] = currentAmmo + amount
            // Updated the log message for clarity
            if (weaponType.actionType == WeaponActionType.THROWABLE) {
                println("Added $amount ${weaponType.displayName}(s). Total: ${ammoReserves[weaponType]}")
            } else {
                println("Added $amount ammo for ${weaponType.displayName}. Total: ${ammoReserves[weaponType]}")
            }
        }
    }

    private fun reload() {
        if (!equippedWeapon.requiresReload || isReloading) return // Can't reload this weapon type or if already reloading

        val ammoNeeded = equippedWeapon.magazineSize - currentMagazineCount
        if (ammoNeeded <= 0) return // Magazine is already full

        val ammoAvailable = ammoReserves.getOrDefault(equippedWeapon, 0)
        if (ammoAvailable <= 0) {
            println("Out of reserve ammo for ${equippedWeapon.displayName}!")
            checkAndRemoveWeaponIfOutOfAmmo()
            // TODO: Play an "empty" sound effect here
            return
        }

        // Start the reload process
        isReloading = true
        reloadTimer = equippedWeapon.reloadTime
        println("Reloading... (${equippedWeapon.reloadTime}s)")
        // TODO: Play a reload sound effect here
    }

    private fun canShoot(): Boolean {
        // If the weapon requires a reload, we check the magazine.
        if (equippedWeapon.requiresReload) {
            return currentMagazineCount > 0
        }
        // Otherwise (like a shotgun), we check the total reserve ammo.
        return ammoReserves.getOrDefault(equippedWeapon, 0) > 0
    }

    fun getThrowableSpawnPosition(): Vector3 {
        val directionX = if (playerCurrentRotationY == 180f) -1f else 1f
        return physicsComponent.position.cpy().add(directionX * 1.5f, 1f, 0f)
    }

    fun getThrowableInitialVelocity(): Vector3 {
        val minPower = 15f
        val maxPower = 45f
        val chargeTimeToMaxPower = 1.2f

        val chargeRatio = (throwChargeTime / chargeTimeToMaxPower).coerceIn(0f, 1f)
        val throwPower = minPower + (maxPower - minPower) * chargeRatio

        val directionX = if (playerCurrentRotationY == 180f) -1f else 1f
        return Vector3(directionX, 1f, 0f).nor().scl(throwPower)
    }

    private fun handleWeaponInput(deltaTime: Float, sceneManager: SceneManager) {
        isHoldingShootButton = Gdx.input.isButtonPressed(Input.Buttons.LEFT) &&
            equippedWeapon.actionType == WeaponActionType.SHOOTING

        if (isHoldingShootButton) {
            chargeTime += deltaTime
        } else {
            chargeTime = 0f
        }

        // Timers
        fireRateTimer -= deltaTime
        attackTimer -= deltaTime

        // State Machine Logic
        when (state) {
            PlayerState.IDLE -> {
                // If we are on cooldown, don't even check for input
                if (fireRateTimer > 0f) {
                    return
                }

                when (equippedWeapon.actionType) {
                    WeaponActionType.SHOOTING -> {
                        if (canShoot() && !isReloading && Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
                            if (!isRotating) {
                                spawnBullet() // This function will now handle ammo reduction
                                fireRateTimer = equippedWeapon.fireCooldown
                                state = PlayerState.ATTACKING
                                attackTimer = shootingPoseDuration
                            }
                        } else if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                            // Player tried to shoot but couldn't (e.g., empty magazine)
                            println("Click! (Out of ammo or empty magazine)")
                            checkAndRemoveWeaponIfOutOfAmmo()
                            // TODO: Play an "empty clip" sound effect here
                        }

                        // Reload logic
                        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
                            reload()
                        }
                    }
                    WeaponActionType.MELEE -> {
                        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                            if (!isRotating) {
                                val animName = when (equippedWeapon) {
                                    WeaponType.UNARMED -> "attack_punch"
                                    WeaponType.BASEBALL_BAT -> "attack_baseball_bat"
                                    WeaponType.KNIFE -> "attack_knife"
                                    else -> null
                                }

                                if (animName != null) {
                                    animationSystem.playAnimation(animName, true)
                                    state = PlayerState.ATTACKING
                                    attackTimer = animationSystem.currentAnimation?.getTotalDuration() ?: 0.3f
                                    fireRateTimer = equippedWeapon.fireCooldown

                                    performMeleeAttack(sceneManager)
                                }
                            }
                        }
                    }
                    WeaponActionType.THROWABLE -> {
                        // Check for input to start an action
                        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                            state = PlayerState.CHARGING_THROW
                            throwChargeTime = 0f
                        }
                    }
                }
            }
            PlayerState.ATTACKING -> {
                if (attackTimer <= 0f) {
                    state = PlayerState.IDLE
                    if (equippedWeapon.actionType == WeaponActionType.MELEE) {
                        animationSystem.playAnimation("idle")
                    }
                }
            }
            PlayerState.CHARGING_THROW -> {
                throwChargeTime += deltaTime

                // Check for the right mouse button to cancel the throw
                if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
                    state = PlayerState.IDLE
                    throwChargeTime = 0f
                    // Give the player feedback that the throw was cancelled
                    sceneManager.game.uiManager.updatePlacementInfo("Throw Cancelled")
                    println("Throw cancelled by user.")
                    return // Exit the state machine check for this frame
                }

                // Check if the button was released
                if (!Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
                    //  Check if the button was held long enough to be a valid throw
                    if (throwChargeTime >= MIN_THROW_CHARGE_TIME) {
                        spawnThrowable() // Execute the throw
                        fireRateTimer = equippedWeapon.fireCooldown
                    } else {
                        // The click was too short, treat it as an accidental click and do nothing.
                        println("Throw cancelled: click too short.")
                    }
                    // Always return to idle after releasing the button
                    state = PlayerState.IDLE
                    // Reset charge time when the throw is finished
                    throwChargeTime = 0f
                }
            }
        }
    }

    private fun performMeleeAttack(sceneManager: SceneManager) {
        val attackRange = equippedWeapon.meleeRange // How far the melee attack reaches
        val attackWidth = 3f  // How wide the attack is

        // Determine direction
        val directionX = if (playerCurrentRotationY == 180f) -1f else 1f

        val hitBoxCenter = physicsComponent.position.cpy().add(directionX * (attackRange / 2f), 0f, 0f)
        val hitBox = BoundingBox(
            Vector3(hitBoxCenter.x - (attackRange / 2f), hitBoxCenter.y - (playerSize.y / 2f), hitBoxCenter.z - (attackWidth / 2f)),
            Vector3(hitBoxCenter.x + (attackRange / 2f), hitBoxCenter.y + (playerSize.y / 2f), hitBoxCenter.z + (attackWidth / 2f))
        )

        // Check against enemies
        val enemyIterator = sceneManager.activeEnemies.iterator()
        while(enemyIterator.hasNext()) {
            val enemy = enemyIterator.next()

            // Only hit enemies that are in a valid state
            if (enemy.currentState != AIState.DYING && hitBox.intersects(enemy.physics.bounds)) {
                println("Melee hit on enemy: ${enemy.enemyType.displayName}")

                // SHAKE
                val shakeIntensity = if (equippedWeapon == WeaponType.BASEBALL_BAT) 0.15f else 0.07f
                val shakeDuration = if (equippedWeapon == WeaponType.BASEBALL_BAT) 0.12f else 0.08f
                sceneManager.cameraManager.startShake(shakeDuration, shakeIntensity)

                if (Random.nextFloat() < 0.75f) { // 75% chance to bleed
                    val bloodSpawnPosition = enemy.position.cpy().add(0f, enemy.enemyType.height / 2f, 0f)
                    spawnBloodEffects(bloodSpawnPosition, sceneManager)
                }

                if (enemy.takeDamage(equippedWeapon.damage, DamageType.MELEE, sceneManager) && enemy.currentState != AIState.DYING) {
                    sceneManager.enemySystem.startDeathSequence(enemy, sceneManager)
                }
            }
        }

        // Check against NPCs
        val npcIterator = sceneManager.activeNPCs.iterator()
        while(npcIterator.hasNext()) {
            val npc = npcIterator.next()

            // Only hit NPCs that are in a valid state
            if (npc.currentState != NPCState.DYING && npc.currentState != NPCState.FLEEING && hitBox.intersects(npc.physics.bounds)) {
                println("Melee hit on NPC: ${npc.npcType.displayName}")

                if (Random.nextFloat() < 0.75f) { // 75% chance to bleed
                    val bloodSpawnPosition = npc.physics.position.cpy().add(0f, npc.npcType.height / 2f, 0f)
                    spawnBloodEffects(bloodSpawnPosition, sceneManager)
                }

                if (npc.takeDamage(equippedWeapon.damage, DamageType.MELEE, sceneManager) && npc.currentState != NPCState.DYING) {
                    sceneManager.npcSystem.startDeathSequence(npc, sceneManager)
                }
            }
        }

        // Check against cars
        for (car in sceneManager.activeCars) {
            if (hitBox.intersects(car.getBoundingBox())) {
                println("Melee hit on car: ${car.carType.displayName}")
                car.takeDamage(equippedWeapon.damage, DamageType.MELEE)
            }
        }
    }

    private fun spawnThrowable() {
        val model = throwableModels[equippedWeapon] ?: return

        // --- START: MODIFIED SECTION ---
        // First, consume one throwable from the reserves.
        val currentAmmo = ammoReserves.getOrDefault(equippedWeapon, 0)
        ammoReserves[equippedWeapon] = (currentAmmo - 1).coerceAtLeast(0)
        // --- END: MODIFIED SECTION ---

        // Throw Physics Calculation
        val minPower = 15f
        val maxPower = 45f
        val chargeTimeToMaxPower = 1.2f // Seconds to reach max power

        val chargeRatio = (throwChargeTime / chargeTimeToMaxPower).coerceIn(0f, 1f)
        val throwPower = minPower + (maxPower - minPower) * chargeRatio

        val directionX = if (playerCurrentRotationY == 180f) -1f else 1f

        // Throw at a 45-degree angle
        val initialVelocity = Vector3(directionX, 1f, 0f).nor().scl(throwPower)

        val spawnPosition = physicsComponent.position.cpy().add(directionX * 1.5f, 1f, 0f)

        val modelInstance = ModelInstance(model)
        modelInstance.userData = "effect"

        val throwable = ThrowableEntity(
            weaponType = equippedWeapon,
            modelInstance = modelInstance,
            position = spawnPosition,
            velocity = initialVelocity,
            lifetime = 3.0f // 3-second fuse for dynamite
        )

        sceneManager.activeThrowables.add(throwable)

        println("Threw ${equippedWeapon.displayName} with power $throwPower")

        // After throwing, check if that was the last one.
        checkAndRemoveWeaponIfOutOfAmmo()
    }

    fun toggleMuzzleFlashLight() {
        isMuzzleFlashLightEnabled = !isMuzzleFlashLightEnabled
        println("Muzzle Flash Light toggled: ${if (isMuzzleFlashLightEnabled) "ON" else "OFF"}")
        // If we turn it off, make sure any active flash is immediately extinguished
        if (!isMuzzleFlashLightEnabled && muzzleFlashTimer > 0f) {
            muzzleFlashLight?.let { light ->
                light.intensity = 0f
                light.updatePointLight()
            }
            muzzleFlashTimer = 0f
        }
    }

    fun isMuzzleFlashLightEnabled(): Boolean {
        return isMuzzleFlashLightEnabled
    }

    private fun spawnBullet() {
        val modifiers = sceneManager.game.missionSystem.activeModifiers

        // If we don't have infinite ammo for this specific weapon OR for all weapons, consume ammo.
        if (equippedWeapon != modifiers?.infiniteAmmoForWeapon && modifiers?.infiniteAmmo != true) {
            if (equippedWeapon.requiresReload) {
                currentMagazineCount--
            } else {
                // For weapons like shotguns, decrement from the main reserve
                val currentReserve = ammoReserves.getOrDefault(equippedWeapon, 0)
                ammoReserves[equippedWeapon] = maxOf(0, currentReserve - 1)
            }
        }

        val bulletModel = bulletModels[equippedWeapon.bulletTexturePath] ?: return

        val directionX = if (playerCurrentRotationY == 180f) -1f else 1f
        val velocity = Vector3(directionX * equippedWeapon.bulletSpeed, 0f, 0f)
        val bulletSpawnOffsetX = directionX * 1.5f
        val bulletSpawnVerticalOffset = -playerSize.y * 0.1f // Lower the bullet spawn point from the player's center
        val bulletSpawnPos = physicsComponent.position.cpy().add(bulletSpawnOffsetX, bulletSpawnVerticalOffset, 0f)

        // Determine rotation
        val bulletRotation = if (directionX < 0) 180f else 0f

        val bullet = Bullet(
            position = bulletSpawnPos,
            velocity = velocity,
            modelInstance = ModelInstance(bulletModel),
            lifetime = equippedWeapon.bulletLifetime,
            rotationY = bulletRotation,
            owner = this,
            damage = equippedWeapon.damage
        )

        // Apply player damage multiplier from mission modifier
        val damageMultiplier = modifiers?.playerDamageMultiplier ?: 1.0f
        bullet.damage *= damageMultiplier

        sceneManager.activeBullets.add(bullet)

        // Trigger camera shake for heavy weapons
        if (equippedWeapon == WeaponType.MACHINE_GUN || equippedWeapon == WeaponType.TOMMY_GUN) {
            // This will feel like a strong, satisfying kick for each shot.
            particleSystem.sceneManager.cameraManager.startShake(duration = 0.22f, intensity = 0.35f)
        }

        // Spawn the muzzle flash particle effect
        val muzzleFlashRightOffset = 0.7f
        val muzzleFlashLeftOffset = -0.01f

        // vertical offset
        val muzzleFlashVerticalOffset = 0.4f

        val finalHorizontalOffset = when (directionX) {
            -1f -> -muzzleFlashLeftOffset
            else -> muzzleFlashRightOffset
        }

        // Calculate the final position
        val muzzleFlashPosition = bulletSpawnPos.cpy().add(finalHorizontalOffset, muzzleFlashVerticalOffset, 0f)

        // Spawn particle effect
        val chargeProgress = (chargeTime / chargeDurationForMaxScale).coerceIn(0f, 1f)
        val currentShotScale = minShotScale + (maxShotScale - minShotScale) * chargeProgress

        // Spawn particle effect with override scale
        particleSystem.spawnEffect(
            type = ParticleEffectType.FIRED_SHOT,
            position = muzzleFlashPosition,
            overrideScale = currentShotScale
        )

        // Muzzle Flashlight Logic
        if (isMuzzleFlashLightEnabled) {
            muzzleFlashLight?.let { light ->
                // 1. Move the light to the muzzle flash position
                light.position.set(muzzleFlashPosition)

                // 2. Set its properties for the flash
                light.intensity = 18f // A good, noticeable but not overwhelming brightness
                light.range = 8f // A small radius
                light.color.set(1f, 0.75f, 0.4f, 1f) // orange/dark yellow

                // 3. Update the light in the rendering environment
                light.updatePointLight()

                // 4. Start the timer to turn it off
                muzzleFlashTimer = 0.06f // The flash will last for a very short time
            }
        }

        // Check if we need to remove the weapon (only if we don't have infinite ammo)
        if (modifiers != null) {
            if (!modifiers.infiniteAmmo && equippedWeapon != modifiers.infiniteAmmoForWeapon) {
                checkAndRemoveWeaponIfOutOfAmmo()
            }
        }
    }

    fun createStateDataSnapshot(): PlayerStateData {
        // Make sure the current magazine count is saved before snapshotting
        currentMagazineCounts[equippedWeapon] = currentMagazineCount

        return PlayerStateData(
            position = getPosition(), // Position isn't used for restore, but good to have
            money = getMoney(),
            weapons = ObjectMap<WeaponType, Int>().apply {
                getWeaponReserves().forEach { (weapon, ammo) -> put(weapon, ammo) }
            },
            equippedWeapon = equippedWeapon,
            currentMagazineCounts = ObjectMap<WeaponType, Int>().apply {
                getMagazineCounts().forEach { (weapon, count) -> put(weapon, count) }
            }
        )
    }

    fun addWeaponToInventory(weaponType: WeaponType, ammo: Int) {
        if (!weapons.contains(weaponType)) {
            weapons.add(weaponType)
        }
        addAmmo(weaponType, ammo)
    }

    fun hasWeapon(weaponType: WeaponType): Boolean {
        return weapons.contains(weaponType)
    }

    fun clearInventory() {
        weapons.clear()
        ammoReserves.clear()
        currentMagazineCounts.clear()
        // Always give the player their fists back.
        equipWeapon(WeaponType.UNARMED)
    }


    fun equipWeapon(weaponType: WeaponType, ammoToGive: Int? = null) {
        if (weaponType == WeaponType.UNARMED) {
            if (!weapons.contains(WeaponType.UNARMED)) {
                weapons.add(0, WeaponType.UNARMED) // Ensure fists are always at the start
            }
            currentWeaponIndex = weapons.indexOf(WeaponType.UNARMED)
        } else {
            if (!weapons.contains(weaponType)) {
                // A mutable list is needed to add items
                weapons.add(weaponType)
            }
            currentWeaponIndex = weapons.indexOf(weaponType)
        }

        this.equippedWeapon = weaponType
        this.currentMagazineCount = currentMagazineCounts.getOrDefault(weaponType, weaponType.magazineSize)

        isReloading = false
        reloadTimer = 0f

        // Add the ammo from the picked-up item to reserves
        if (ammoToGive != null && ammoToGive > 0) {
            addAmmo(weaponType, ammoToGive)
        }

        println("Player equipped: ${weaponType.displayName}. Magazine loaded with $currentMagazineCount rounds.")
    }

    private fun removeWeaponFromInventory(weaponType: WeaponType) {
        if (weaponType == WeaponType.UNARMED) return // Cannot remove fists

        weapons.remove(weaponType)
        ammoReserves.remove(weaponType)
        currentMagazineCounts.remove(weaponType)
    }

    fun switchToNextWeapon() {
        if (sceneManager.game.missionSystem.activeModifiers?.disableWeaponSwitching == true) {
            println("Weapon switching is disabled for this mission.")
            return // Exit the function immediately, preventing the switch.
        }

        if (weapons.size <= 1) return // Can't switch if you only have fists

        // SAVE the current weapon's magazine state BEFORE switching
        currentMagazineCounts[equippedWeapon] = currentMagazineCount

        currentWeaponIndex = (currentWeaponIndex + 1) % weapons.size
        equipWeapon(weapons[currentWeaponIndex])
    }

    private fun checkAndRemoveWeaponIfOutOfAmmo() {
        val weaponToCheck = equippedWeapon

        if (weaponToCheck.actionType == WeaponActionType.MELEE) {
            return
        }

        val magCount = getCurrentMagazineCount()
        val reserveCount = getCurrentReserveAmmo()

        // If both magazine and reserves are empty, the weapon is useless
        if (magCount <= 0 && reserveCount <= 0) {
            println("${weaponToCheck.displayName} is completely out of ammo. Removing from inventory.")

            // Remove the weapon from the player's list
            removeWeaponFromInventory(weaponToCheck)

            equipWeapon(WeaponType.UNARMED)
        }
    }

    fun hasGunEquipped(): Boolean {
        // A "gun" is any weapon that uses the SHOOTING action type.
        return equippedWeapon.actionType == WeaponActionType.SHOOTING
    }

    fun countItemInInventory(itemType: ItemType): Int {
        // This is a placeholder for a real inventory system.
        // It just checks the currently held weapon.
        return if (equippedWeapon == itemType.correspondingWeapon) 1 else 0
    }

    fun addAmmoToReserves(weaponType: WeaponType, amount: Int) {
        val currentAmmo = ammoReserves.getOrDefault(weaponType, 0)
        ammoReserves[weaponType] = currentAmmo + amount
        println("Added $amount ammo for ${weaponType.displayName}. Total reserve: ${ammoReserves[weaponType]}")
        // Make sure the weapon is in the player's inventory list if they get ammo for it
        if (weaponType != WeaponType.UNARMED && !weapons.contains(weaponType)) {
            weapons.add(weaponType)
        }
    }

    fun getControlledEntityPosition(): Vector3 {
        return if (isDriving && drivingCar != null) {
            drivingCar!!.position
        } else {
            physicsComponent.position
        }
    }

    fun enterCar(car: GameCar) {
        if (isDriving) return // Already driving, can't enter another car

        val modifiers = sceneManager.game.missionSystem.activeModifiers
        val canBypassLock = modifiers?.allCarsUnlocked == true

        // Check if the car is locked before entering
        if (car.isLocked && !canBypassLock) {
            println("This car is locked.")
            // TODO: Play a "locked door" sound or show a UI message
            return // Stop the function here, player cannot enter.
        }

        val seat = car.addOccupant(this)
        if (seat != null) {
            isDriving = true
            drivingCar = car
            currentSeat = seat
            car.modelInstance.userData = "car"

            // If entering a wrecked car
            sceneManager.game.missionSystem.onPlayerEnteredCar(car.id)

            println("Player entered car ${car.carType.displayName}")
        } else {
            println("Could not enter car: No seats available.")
        }
    }

    fun exitCar(sceneManager: SceneManager) {
        if (!isDriving || drivingCar == null) return

        val car = drivingCar!!

        // Remove player from the car's occupant list
        car.removeOccupant(this)
        headlightLight?.let { it.intensity = 0f; it.updatePointLight() }
        car.modelInstance.userData = null
        val exitOffset = Vector3(-5f, 0f, 0f).rotate(Vector3.Y, car.visualRotationY) // Use visual rotation
        val exitPosition = Vector3(car.position).add(exitOffset)
        val safeY = calculateSafeYPositionForExit(exitPosition.x, exitPosition.z, car.position.y, sceneManager)
        val finalExitPos = Vector3(exitPosition.x, safeY, exitPosition.z)

        if (characterPhysicsSystem.isPositionValid(finalExitPos, this.physicsComponent)) {
            setPosition(finalExitPos)
            println("Player exited car. Placed at $finalExitPos")
            isDriving = false
            drivingCar = null
            currentSeat = null
        } else {
            println("Cannot exit car, path is blocked.")
            car.modelInstance.userData = "car"
            // If exit fails, put the player back in the seat conceptually
            car.addOccupant(this)
        }
    }

    private fun calculateSafeYPositionForExit(x: Float, z: Float, carY: Float, sceneManager: SceneManager): Float {
        // Find the highest block/surface that the player can stand on at position (x, z)
        var highestSupportY = 0f // Ground level
        var foundSupport = false

        // Create a small area around the player position to check for support
        val checkRadius = playerSize.x / 2f

        // Check blocks - allow standing on any block at this position
        for (gameBlock in sceneManager.activeChunkManager.getBlocksInColumn(x, z)) {
            if (!gameBlock.blockType.hasCollision) continue

            val blockTop = gameBlock.getBoundingBox(blockSize, BoundingBox()).max.y
            if (blockTop <= carY + 2f && blockTop > highestSupportY) {
                highestSupportY = blockTop
                foundSupport = true
            }
        }

        // Handle stairs (similar to original logic but without the "already close" requirement)
        for (house in sceneManager.activeHouses) {
            if (house.houseType == HouseType.STAIR) {
                val supportHeight = findStairSupportHeightForExit(house, x, z, carY)
                if (supportHeight > highestSupportY && supportHeight <= carY + 2f) {
                    highestSupportY = supportHeight
                    foundSupport = true
                }
            }
        }

        // Check 3D interiors (similar to original logic but without the "already close" requirement)
        for (interior in sceneManager.activeInteriors) {
            if (!interior.interiorType.is3D || !interior.interiorType.hasCollision) continue

            val objectBounds = interior.instance.calculateBoundingBox(BoundingBox())
            if (x >= objectBounds.min.x && x <= objectBounds.max.x && z >= objectBounds.min.z && z <= objectBounds.max.z) {
                val interiorTop = objectBounds.max.y
                if (interiorTop <= carY + 2f && interiorTop > highestSupportY) {
                    highestSupportY = interiorTop
                    foundSupport = true
                }
            }
        }

        // Calculate where the player should be placed
        val targetY = highestSupportY + playerSize.y / 2f + 0.05f
        return if (foundSupport) targetY else (0f + playerSize.y / 2f) // Ground level if no support
    }

    // Helper function for stair support during car exit
    private fun findStairSupportHeightForExit(house: GameHouse, x: Float, z: Float, maxHeight: Float): Float {
        val checkRadius = playerSize.x / 2f
        val stepSize = 0.05f

        // Check from maxHeight downward to find the stair surface
        for (checkHeight in generateSequence(maxHeight) { it - stepSize }.takeWhile { it >= 0f }) {
            val testBounds = BoundingBox()
            testBounds.set(
                Vector3(x - checkRadius, checkHeight - playerSize.y / 2f, z - checkRadius),
                Vector3(x + checkRadius, checkHeight + playerSize.y / 2f, z + checkRadius)
            )

            if (!house.collidesWithMesh(testBounds)) {
                // Found a height where we don't collide - this is where the player can stand
                return checkHeight - playerSize.y / 2f + 0.05f
            }
        }

        return 0f // Ground level if no suitable height found
    }

    fun handleMovement(deltaTime: Float, sceneManager: SceneManager, allCars: Array<GameCar>, particleSystem: ParticleSystem): Boolean {
        return if (isDriving) {
            handleCarMovement(deltaTime, sceneManager, allCars)
        } else {
            handlePlayerOnFootMovement(deltaTime, sceneManager, particleSystem)
        }
    }

    private fun handleCarMovement(deltaTime: Float, sceneManager: SceneManager, allCars: Array<GameCar>): Boolean {
        val car = drivingCar ?: return false

        headlightLight?.let { light ->
            val sunIntensity = lightingManager.getDayNightCycle().getSunIntensity()
            val targetIntensity = if (sunIntensity < 0.25f && !car.isDestroyed) HEADLIGHT_INTENSITY else 0f
            light.intensity = MathUtils.lerp(light.intensity, targetIntensity, deltaTime * 5f)

            // Calculate the forward direction based on the CAR'S visual rotation
            val forwardX = if (car.visualRotationY == 180f) 1f else -1f

            // Position the light in front of the car
            val lightPosition = car.position.cpy().add(
                forwardX * headlightForwardOffset,
                headlightVerticalOffset,
                0f
            )

            light.position.set(lightPosition)
            light.updatePointLight()
        }

        if (car.isDestroyed) {
            car.setDrivingAnimationState(false) // Ensure it doesn't play driving animations
            return false // Return false to indicate no movement occurred
        }

        val originalPosition = car.position.cpy() // Store original position
        var moved = false

        val moveAmount = car.carType.speed * deltaTime

        // 1. Calculate desired horizontal movement
        var deltaX = 0f
        var deltaZ = 0f
        var horizontalDirection = 0f
        if (Gdx.input.isKeyPressed(Input.Keys.A)) { deltaX -= moveAmount; horizontalDirection = 1f }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) { deltaX += moveAmount; horizontalDirection = -1f }
        if (Gdx.input.isKeyPressed(Input.Keys.W)) { deltaZ -= moveAmount }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) { deltaZ += moveAmount }

        car.updateFlipAnimation(horizontalDirection, deltaTime)

        // Tell the car to play the correct animation based on input
        car.setDrivingAnimationState(deltaX != 0f || deltaZ != 0f)

        // 2. Determine potential next position and apply physics
        if (deltaX != 0f) {
            val nextX = car.position.x + deltaX
            // Check for ground support and step height before checking for collision
            val supportY = sceneManager.findHighestSupportYForCar(nextX, car.position.z, car.carType.width / 2f, blockSize)
            if (supportY - car.position.y <= CAR_MAX_STEP_HEIGHT) {
                // Check for collision at the current height
                val potentialPos = Vector3(nextX, car.position.y, car.position.z)
                if (canCarMoveTo(potentialPos, car, sceneManager, allCars)) {
                    car.position.x = nextX // If clear, apply the X movement
                }
            }
        }

        // 3. Resolve Z-axis movement second
        if (deltaZ != 0f) {
            // Use the *potentially updated* X position from the previous step
            val nextZ = car.position.z + deltaZ
            val supportY = sceneManager.findHighestSupportYForCar(car.position.x, nextZ, car.carType.width / 2f, blockSize)
            if (supportY - car.position.y <= CAR_MAX_STEP_HEIGHT) {
                val potentialPos = Vector3(car.position.x, car.position.y, nextZ)
                if (canCarMoveTo(potentialPos, car, sceneManager, allCars)) {
                    car.position.z = nextZ // If clear, apply the Z movement
                }
            }
        }

        // 4. Resolve Y-axis movement (Gravity and Grounding)
        val finalSupportY = sceneManager.findHighestSupportYForCar(car.position.x, car.position.z, car.carType.width / 2f, blockSize)

        // Use the original Y position to check step height to prevent "snapping" up high walls
        val effectiveSupportY = if (finalSupportY - originalPosition.y <= CAR_MAX_STEP_HEIGHT) {
            finalSupportY
        } else {
            // If step is too high, find support at the original location to prevent floating
            sceneManager.findHighestSupportYForCar(originalPosition.x, originalPosition.z, car.carType.width / 2f, blockSize)
        }

        // Apply gravity
        val fallY = car.position.y - FALL_SPEED * deltaTime
        car.position.y = kotlin.math.max(effectiveSupportY, fallY)

        // 5. Finalize and update visuals
        moved = !car.position.epsilonEquals(originalPosition, 0.001f)
        if (moved) {
            car.updateTransform()
        }

        return moved
    }

    fun canCarMoveTo(newPosition: Vector3, thisCar: GameCar, sceneManager: SceneManager, allCars: Array<GameCar>): Boolean {
        val carBounds = thisCar.getBoundingBox(newPosition)
        val tempBlockBounds = BoundingBox() // Create a temporary box to reuse

        // Check collision with blocks - BUT allow driving ON TOP of blocks
        for (block in sceneManager.activeChunkManager.getAllBlocks()) {
            if (!block.blockType.hasCollision) continue

          if (block.collidesWith(carBounds)) {
                val carBottom = newPosition.y
                val blockTop = block.getBoundingBox(blockSize, tempBlockBounds).max.y

                if (carBounds.min.y >= blockTop - 0.5f) {
                    //   if (carBottom >= blockTop - 0.5f || blockTop - carBottom <= CAR_MAX_STEP_HEIGHT) { // old Version
                    continue
                }

                // Otherwise, it's a real collision
                return false
            }
        }

        // Check collision with houses
        for (house in sceneManager.activeHouses) {
            if (house.collidesWithMesh(carBounds)) return false
        }

        // Check collision with interiors that have collision
        for (interior in sceneManager.activeInteriors) {
            if (interior.interiorType.hasCollision && interior.collidesWithMesh(carBounds)) return false
        }

        // Check collision with other cars
        for (otherCar in allCars) {
            // Get the other car's bounding box at its actual position
            if (otherCar.id != thisCar.id && otherCar.getBoundingBox().intersects(carBounds)) return false
        }

        return true
    }

    private fun handlePlayerOnFootMovement(deltaTime: Float, sceneManager: SceneManager, particleSystem: ParticleSystem): Boolean {
        // Disable on-foot movement and collision while driving
        if (isDriving) return false

        // Apply speed multiplier from mission modifier
        val speedMultiplier = sceneManager.game.missionSystem.activeModifiers?.playerSpeedMultiplier ?: 1.0f
        physicsComponent.speed = basePlayerSpeed * speedMultiplier

        // 1. Calculate desired horizontal movement
        val desiredMovement = Vector3()
        if (Gdx.input.isKeyPressed(Input.Keys.A)) desiredMovement.x -= 1f
        if (Gdx.input.isKeyPressed(Input.Keys.D)) desiredMovement.x += 1f

        isPressingW = Gdx.input.isKeyPressed(Input.Keys.W)
        if (isPressingW) desiredMovement.z -= 1f
        if (Gdx.input.isKeyPressed(Input.Keys.S)) desiredMovement.z += 1f

        if (isHoldingShootButton && !equippedWeapon.allowsMovementWhileShooting) {
            desiredMovement.set(0f, 0f, 0f)
        }

        // 2. Resolve X-axis movement
        val moved = characterPhysicsSystem.update(physicsComponent, desiredMovement, deltaTime)

        // 3. Resolve Z-axis movement second
        isMoving = physicsComponent.isMoving

        if (desiredMovement.x != 0f) {
            lastMovementDirection = if (desiredMovement.x > 0) 1f else -1f
        }
        playerTargetRotationY = if (lastMovementDirection < 0f) 180f else 0f
        updatePlayerRotation(deltaTime)

        if (isMoving && !lastIsMoving) animationSystem.playAnimation("walking")
        else if (!isMoving && lastIsMoving) animationSystem.playAnimation("idle")
        lastIsMoving = isMoving

        // 4. Resolve Y-axis movement (Gravity and Grounding) with MULTI-POINT CHECK
        if (isMoving) {
            // Player is moving
            continuousMovementTimer += deltaTime

            // Only check for wipe spawning if player moving for at least 1 second
            if (continuousMovementTimer >= 0.3f) { // Only spawn after a brief moment of sustained movement
                wipeEffectTimer += deltaTime
                if (wipeEffectTimer >= WIPE_EFFECT_INTERVAL) {
                    wipeEffectTimer = 0f // Reset timer
                    val particleTypeToSpawn: ParticleEffectType
                    val wipeRotation: Float
                    val wipePosition: Vector3

                    // Check if movement is PURELY on the Z-axis
                    if (desiredMovement.x == 0f && desiredMovement.z != 0f) {
                        // CASE 1: Forward / Backward Movement
                        particleTypeToSpawn = ParticleEffectType.MOVEMENT_WIPE_VERTICAL
                        wipeRotation = if (desiredMovement.z < 0) 90f else 270f // W key is negative Z

                        val zOffset = if (desiredMovement.z < 0) 1.0f else -1.0f
                        wipePosition = physicsComponent.position.cpy().add(0f, -1.0f, zOffset)

                    } else {
                        // CASE 2: Sideways / Diagonal Movement
                        particleTypeToSpawn = ParticleEffectType.MOVEMENT_WIPE
                        wipeRotation = MathUtils.atan2(-desiredMovement.x, -desiredMovement.z) * MathUtils.radiansToDegrees + 90f

                        val xOffset: Float
                        val zOffset: Float

                        if (desiredMovement.z != 0f) {
                            // DIAGONAL MOVEMENT (W/S + A/D keys are pressed)
                            xOffset = if (desiredMovement.x < 0) 1.0f else -1.0f

                            zOffset = if (desiredMovement.z < 0) 1.0f else -1.0f
                        } else {
                            // PURE SIDEWAYS MOVEMENT (A/D keys only)
                            xOffset = 0f
                            zOffset = -0.5f
                        }

                        // Use the original, correct position for this case.
                        wipePosition = physicsComponent.position.cpy().add(xOffset, -1.0f, zOffset)
                    }

                    particleSystem.spawnEffect(
                        type = particleTypeToSpawn,
                        position = wipePosition,
                        initialRotation = wipeRotation,
                        targetRotation = wipeRotation,
                        overrideScale = 2.5f
                    )
                }
            }
            if (bloodyFootprintsTimer > 0f) {
                footprintSpawnTimer += deltaTime
                if (footprintSpawnTimer >= FOOTPRINT_SPAWN_INTERVAL) {
                    footprintSpawnTimer = 0f

                    // Find the ground position directly below the player
                    val groundY = sceneManager.findHighestSupportY(physicsComponent.position.x, physicsComponent.position.z, physicsComponent.position.y, physicsComponent.size.x / 2f, blockSize)
                    val footprintPosition = Vector3(physicsComponent.position.x, groundY, physicsComponent.position.z)
                    val movementRotation = MathUtils.atan2(-desiredMovement.x, -desiredMovement.z) * MathUtils.radiansToDegrees + 90f

                    // Spawn the footprint using the new system
                    footprintSystem.spawnFootprint(footprintPosition, movementRotation, sceneManager)
                }
            }
        } else {
            // If player stops, reset the continuous movement timer
            continuousMovementTimer = 0f

            // Reset timer if not moving
            wipeEffectTimer = WIPE_EFFECT_INTERVAL
        }

        return moved
    }

    fun placePlayer(ray: Ray, sceneManager: SceneManager): Boolean {
        val intersection = Vector3()
        if (Intersector.intersectRayPlane(ray, com.badlogic.gdx.math.Plane(Vector3.Y, 0f), intersection)) {
            // Snap to grid
            val gridX = floor(intersection.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize / 2
            val highestBlockY = sceneManager.findHighestSupportY(gridX, gridZ, 1000f, 0.1f, blockSize)
            val playerY = highestBlockY + playerSize.y / 2f
            val potentialPosition = Vector3(gridX, playerY, gridZ)

            if (characterPhysicsSystem.isPositionValid(potentialPosition, this.physicsComponent)) {
                setPosition(potentialPosition)
                return true
            }
        }
        return false
    }

    private fun updatePlayerRotation(deltaTime: Float) {
        // Calculate the shortest rotation path
        var rotationDifference = playerTargetRotationY - playerCurrentRotationY
        if (rotationDifference > 180f) rotationDifference -= 360f
        else if (rotationDifference < -180f) rotationDifference += 360f

        if (kotlin.math.abs(rotationDifference) > 1f) {
            val rotationStep = rotationSpeed * deltaTime
            if (rotationDifference > 0f) playerCurrentRotationY += kotlin.math.min(rotationStep, rotationDifference)
            else playerCurrentRotationY += kotlin.math.max(-rotationStep, rotationDifference)
            if (playerCurrentRotationY >= 360f) playerCurrentRotationY -= 360f
            else if (playerCurrentRotationY < 0f) playerCurrentRotationY += 360f
        } else {
            // Snap to target if close enough
            playerCurrentRotationY = playerTargetRotationY
        }
    }

    fun teleportTo(destination: Vector3): Boolean {
        if (teleportCooldown > 0f) {
            return false // Teleport failed due to cooldown
        }

        val finalDestination = destination.cpy().add(0f, playerSize.y / 2f, 0f)
        setPosition(finalDestination)
        teleportCooldown = 1.0f // 1 second cooldown before teleporting again
        println("Player teleported!")

        return true // Teleport was successful
    }

    fun getMoney(): Int = money

    fun addMoney(amount: Int) {
        if (amount <= 0) return
        money += amount
        println("Player collected $amount. Total money: $money")
        // Notify the UI manager about the change
        sceneManager.game.uiManager.showMoneyUpdate(money)
    }

    fun setOnFire(duration: Float, dps: Float) {
        if (isOnFire) return // Already on fire
        isOnFire = true
        onFireTimer = duration
        initialOnFireDuration = duration
        onFireDamagePerSecond = dps
    }

    private fun applyKnockback(force: Vector3) {
        if (!isDriving) {
            physicsComponent.knockbackVelocity.set(force)
        }
    }

    fun update(deltaTime: Float, sceneManager: SceneManager) {

        // HANDLE ON FIRE STATE (DAMAGE & VISUALS)
        if (this.isOnFire) {
            this.onFireTimer -= deltaTime
            if (this.onFireTimer <= 0) {
                this.isOnFire = false
            } else {
                // Damage falloff over the duration of the effect
                val progress = (this.onFireTimer / this.initialOnFireDuration).coerceIn(0f, 1f)
                val currentDps = this.onFireDamagePerSecond * progress
                val damageThisFrame = currentDps * deltaTime
                this.takeDamage(damageThisFrame)

                // 1% chance for big head on fire effect
                if (Random.nextFloat() < 0.01f) {
                    val particlePos = this.getPosition().add(0f, this.playerSize.y * 0.8f, 0f)
                    this.particleSystem.spawnEffect(ParticleEffectType.FIRE_FLAME, particlePos)
                }

                // Frequent, smaller body flames
                if (Random.nextFloat() < 0.6f) {
                    val halfWidth = this.playerSize.x / 2f
                    val offsetX = (Random.nextFloat() - 0.5f) * (halfWidth * 1.5f)
                    val particlePos = this.getPosition().add(offsetX, -this.playerSize.y * 0.2f, 0f)

                    this.particleSystem.spawnEffect(ParticleEffectType.BODY_FLAME, particlePos)
                }
            }
        }
        // Handle Reload Timer
        if (isReloading) {
            reloadTimer -= deltaTime
            if (reloadTimer <= 0f) {
                isReloading = false
                // Finish the reload by moving ammo from reserves to magazine
                val ammoNeeded = equippedWeapon.magazineSize - currentMagazineCount
                val ammoAvailable = ammoReserves.getOrDefault(equippedWeapon, 0)
                val ammoToMove = minOf(ammoNeeded, ammoAvailable)

                currentMagazineCount += ammoToMove
                ammoReserves[equippedWeapon] = ammoAvailable - ammoToMove
                println("Reload finished! Magazine: $currentMagazineCount")
            }
        }

        // Timer for the muzzle flash
        if (muzzleFlashTimer > 0f) {
            muzzleFlashTimer -= deltaTime
            if (muzzleFlashTimer <= 0f) {
                // Timer finished, turn the light off
                muzzleFlashLight?.let { light ->
                    light.intensity = 0f
                    light.updatePointLight()
                }
            }
        }

        if (teleportCooldown > 0f) {
            teleportCooldown -= deltaTime
        }

        // Check for blood pool collision and update the footprint timer
        if (!isDriving) { // Only check when on foot
            checkBloodPoolCollision(sceneManager)
            if (bloodyFootprintsTimer > 0f) {
                bloodyFootprintsTimer -= deltaTime
            }
        }
        handleWeaponInput(deltaTime, sceneManager)

        // Update animation system
        animationSystem.update(deltaTime)

        // Update player texture if it changed
        val finalTexture: Texture? = when {
            state == PlayerState.ATTACKING && equippedWeapon.actionType == WeaponActionType.MELEE -> {
                animationSystem.getCurrentTexture() // Use the melee animation
            }

            state == PlayerState.CHARGING_THROW -> {
                poseTextures[equippedWeapon.playerPoseTexturePath]
            }

            isHoldingShootButton -> {
                poseTextures[equippedWeapon.playerPoseTexturePath]  // Use the shooting pose
            }

            isPressingW && !(fireRateTimer > 0 && equippedWeapon.actionType == WeaponActionType.MELEE) -> {
                playerBackTexture
            }

            equippedWeapon != WeaponType.UNARMED -> {
                poseTextures[equippedWeapon.playerPoseTexturePath]
            }

            else -> {
                animationSystem.getCurrentTexture() // Standard idle/walking
            }
        }

        // Update the player's texture
        finalTexture?.let {
            updatePlayerTexture(it)
        }

        val bulletIterator = sceneManager.activeBullets.iterator()
        while (bulletIterator.hasNext()) {
            val bullet = bulletIterator.next()
            bullet.update(deltaTime)

            // Collision Check
            val collisionResult = checkBulletCollision(bullet, sceneManager)

            // Check if ANY valid object was hit
            if (collisionResult != null) {
                // Destroy bullet
                bulletIterator.remove()

                // Calculate the spawn position
                val particleSpawnPos = collisionResult.hitPoint.cpy().mulAdd(collisionResult.surfaceNormal, PARTICLE_IMPACT_OFFSET)

                when (collisionResult.type) {
                    HitObjectType.BLOCK, HitObjectType.INTERIOR -> {
                        // Spawn dust/sparks for static objects
                        particleSystem.spawnEffect(ParticleEffectType.DUST_SMOKE_MEDIUM, particleSpawnPos)
                    }
                    HitObjectType.CAR -> {
                        val car = collisionResult.hitObject as GameCar
                        car.takeDamage(equippedWeapon.damage, DamageType.GENERIC)
                        particleSystem.spawnEffect(ParticleEffectType.DUST_SMOKE_MEDIUM, particleSpawnPos)
                    }

                    HitObjectType.OBJECT,
                    HitObjectType.HOUSE -> {
                        // Intentionally do nothing
                    }

                    HitObjectType.ENEMY -> {
                        val enemy = collisionResult.hitObject as GameEnemy

                        // Use the enemy's center
                        val bloodSpawnPosition = enemy.position.cpy()

                        // small random offset to make it look less robotic
                        val offsetX = (Random.nextFloat() - 0.5f) * (enemy.enemyType.width * 0.4f)
                        val offsetY = (Random.nextFloat() - 0.5f) * (enemy.enemyType.height * 0.4f)
                        bloodSpawnPosition.add(offsetX, offsetY, 0f) // No Z offset for 2D characters

                        // 50% chance to spawn blood effects
                        if (Random.nextFloat() < 0.5f) {
                            spawnBloodEffects(bloodSpawnPosition, sceneManager)
                        }

                        if (enemy.takeDamage(equippedWeapon.damage, DamageType.GENERIC, sceneManager) && enemy.currentState != AIState.DYING) {
                            sceneManager.enemySystem.startDeathSequence(enemy, sceneManager)
                        }
                    }
                    HitObjectType.NPC -> {
                        val npc = collisionResult.hitObject as GameNPC

                        // Use the NPC's center
                        val bloodSpawnPosition = npc.position.cpy()

                        // small random offset
                        val offsetX = (Random.nextFloat() - 0.5f) * (npc.npcType.width * 0.4f)
                        val offsetY = (Random.nextFloat() - 0.5f) * (npc.npcType.height * 0.4f)
                        bloodSpawnPosition.add(offsetX, offsetY, 0f)

                        // 50% chance to spawn blood effects
                        if (Random.nextFloat() < 0.5f) {
                            spawnBloodEffects(bloodSpawnPosition, sceneManager)
                        }

                        if (npc.takeDamage(equippedWeapon.damage, DamageType.GENERIC, sceneManager) && npc.currentState != NPCState.DYING) {
                            // NPC died, remove it from the scene
                            sceneManager.npcSystem.startDeathSequence(npc, sceneManager)
                        }
                    }
                    HitObjectType.PLAYER -> {
                        val enemyOwner = bullet.owner as GameEnemy
                        takeDamage(enemyOwner.equippedWeapon.damage)
                        // You can also add a blood effect for the player here if you wish
                    }
                    HitObjectType.NONE -> {}
                }
                continue // Skip next bullet
            }

            if (bullet.lifetime <= 0f) {
                bulletIterator.remove()
            }
        }

        val throwableIterator = sceneManager.activeThrowables.iterator()
        while (throwableIterator.hasNext()) {
            val throwable = throwableIterator.next()
            throwable.update(deltaTime)

            val collisionResult = checkThrowableCollision(throwable, sceneManager)

            if (collisionResult != null || throwable.lifetime <= 0) {
                // It hit something or its fuse ran out
                handleThrowableImpact(
                    throwable,
                    collisionResult,
                    sceneManager
                )
                throwableIterator.remove()
            }
        }

        updatePlayerTransform()
    }

    private fun checkBloodPoolCollision(sceneManager: SceneManager) {
        var isTouchingBlood = false
        val playerPos2D = Vector2(physicsComponent.position.x, physicsComponent.position.z)

        for (pool in sceneManager.activeBloodPools) {
            // Simple 2D distance check from player center to pool center
            val distance = playerPos2D.dst(pool.position.x, pool.position.z)

            val playerRadius = playerSize.x / 2f
            if (distance < (pool.currentScale / 2f - playerRadius)) {
                isTouchingBlood = true
                break // Found a collision, no need to check other pools
            }
        }

        if (isTouchingBlood) {
            // If touching a blood pool, refresh the timer to its maximum duration
            bloodyFootprintsTimer = BLOODY_FOOTPRINT_COOLDOWN
        }
    }

    private fun getValidGroundImpactPosition(
        collisionResult: CollisionResult?,
        sceneManager: SceneManager,
        initialImpactPosition: Vector3
    ): Vector3 {
        val impactedBlock = collisionResult?.hitObject as? GameBlock

        // Case A: The throwable hit a vertical face of a specific block
        if (collisionResult != null && kotlin.math.abs(collisionResult.surfaceNormal.y) < 0.7f && impactedBlock != null) {
            val effectHalfWidth = 2.0f
            val blockHalfWidth = blockSize / 2f
            val totalOffset = blockHalfWidth + effectHalfWidth + 0.1f

            val adjacentPosition = impactedBlock.position.cpy().mulAdd(collisionResult.surfaceNormal, totalOffset)

            // Find the ground Y-coordinate at this new, safe position
            val groundY = sceneManager.findHighestSupportY(adjacentPosition.x, adjacentPosition.z, 1000f, 0.1f, blockSize)

            return Vector3(adjacentPosition.x, groundY, adjacentPosition.z)
        }

        // Case B: The throwable hit a flat surface, a non-block object, or exploded in mid-air
        else {
            val groundY = sceneManager.findHighestSupportY(
                initialImpactPosition.x,
                initialImpactPosition.z,
                1000f, // Use a very high starting point for the search
                0.1f,
                blockSize
            )
            return Vector3(initialImpactPosition.x, groundY, initialImpactPosition.z)
        }
    }

    private fun calculateFalloffDamage(baseDamage: Float, distance: Float, maxRadius: Float): Float {
        if (distance >= maxRadius || maxRadius <= 0f) {
            return 0f
        }
        // A simple linear falloff. Damage is 100% at the center, 0% at the edge.
        val multiplier = 1.0f - (distance / maxRadius)
        return baseDamage * multiplier
    }


    private fun handleThrowableImpact(
        throwable: ThrowableEntity,
        collisionResult: CollisionResult?,
        sceneManager: SceneManager
    ) {
        when (throwable.weaponType) {
            WeaponType.DYNAMITE -> {
                val explosionOrigin: Vector3
                val spawnSmokeOnGround: Boolean

                if (collisionResult != null) {
                    explosionOrigin = getValidGroundImpactPosition(collisionResult, sceneManager, throwable.position)
                    spawnSmokeOnGround = true
                } else {
                    explosionOrigin = throwable.position.cpy()
                    spawnSmokeOnGround = false
                }

                // Camera Shake
                val distanceToPlayer = explosionOrigin.dst(getPosition())
                val maxShakeDistance = 45f

                if (distanceToPlayer < maxShakeDistance) {
                    val baseIntensity = 1.3f
                    val baseDuration = 0.9f

                    val intensity = baseIntensity * (1f - (distanceToPlayer / maxShakeDistance))
                    val duration = baseDuration * (1f - (distanceToPlayer / maxShakeDistance))

                    sceneManager.cameraManager.startShake(duration, intensity.coerceAtLeast(0.15f))
                }

                // Now the explosion correctly happens on the ground next to a wall, not in mid-air!
                val validGroundPosition = getValidGroundImpactPosition(collisionResult, sceneManager, throwable.position)
                val explosionCenterPosition = explosionOrigin.cpy().add(0f, 2.5f, 0f)
                particleSystem.spawnEffect(ParticleEffectType.DYNAMITE_EXPLOSION, explosionCenterPosition)

                // 2. Spawn multiple smoke plumes for a lingering effect.
                val smokePlumeTypes = listOf(
                    ParticleEffectType.DUST_SMOKE_LIGHT,
                    ParticleEffectType.DUST_SMOKE_DEFAULT,
                    ParticleEffectType.DUST_SMOKE_HEAVY
                )
                val smokeCount = (2..5).random()

                // Smoke Spawning
                for (i in 0 until smokeCount) {
                    val randomSmokeType = smokePlumeTypes.random()
                    val spreadRadius = 6f

                    val offsetX = (Random.nextFloat() * 2f - 1f) * spreadRadius
                    val offsetZ = (Random.nextFloat() * 2f - 1f) * spreadRadius

                    val smokePosition: Vector3

                    if (spawnSmokeOnGround) {
                        // Step 1: Get a random X and Z coordinate in a radius
                        val finalX = explosionOrigin.x + offsetX
                        val finalZ = explosionOrigin.z + offsetZ

                        // Step 2: Find the actual ground height at this new random spot
                        val groundY = sceneManager.findHighestSupportY(finalX, finalZ, explosionOrigin.y + 2f, 0.1f, blockSize)
                        val verticalOffset = 1.5f + Random.nextFloat() * 2f

                        // Step 3: Create the final position vector for the smoke plume
                        smokePosition = Vector3(finalX, groundY + verticalOffset, finalZ)
                    } else {
                        // Exploded in the void. Spawn smoke relative to the explosion's origin.
                        val verticalOffset = (Random.nextFloat() * 2f - 1f) * (spreadRadius / 2f) // Random Y offset
                        smokePosition = explosionOrigin.cpy().add(offsetX, verticalOffset, offsetZ)
                    }

                    val smokeScale = 2.5f + Random.nextFloat() * (3.0f - 1f)
                    particleSystem.spawnEffect(type = randomSmokeType, position = smokePosition, overrideScale = smokeScale)
                }

                // 4. Scorch Mark
                if (collisionResult != null) {
                    // 3. Spawn the explosion scorch mark on the ground.
                    val explosionAreaTypes = listOf(
                        ParticleEffectType.DYNAMITE_EXPLOSION_AREA_ONE,
                        ParticleEffectType.DYNAMITE_EXPLOSION_AREA_TWO
                    )
                    val selectedAreaType = explosionAreaTypes.random() // Randomly pick one of the two scorch marks.
                    val scorchMarkPosition = explosionOrigin.cpy().add(0f, 0.15f, 0f)

                    particleSystem.spawnEffect(
                        type = selectedAreaType,
                        position = scorchMarkPosition,
                        surfaceNormal = Vector3.Y, // The ground's normal is straight up.
                        gravityOverride = 0f       // Ensure it doesn't fall through the world.
                    )
                }

                println("Dynamite effect originating at $explosionOrigin")

                // Area-of-effect damage logic
                val explosionRadius = 12f
                val explosionDamage = throwable.weaponType.damage
                val maxKnockbackForce = 35.0f
                val upwardLift = 0.7f

                // Damage cars
                for (car in sceneManager.activeCars) {
                    val distanceToCar = car.position.dst(validGroundPosition)
                    if (distanceToCar < explosionRadius) {
                        val actualDamage = calculateFalloffDamage(explosionDamage, distanceToCar, explosionRadius)
                        car.takeDamage(actualDamage, DamageType.EXPLOSIVE)
                    }
                }
                // Damage enemies
                sceneManager.activeEnemies.forEach { enemy ->
                    val distanceToEnemy = enemy.position.dst(validGroundPosition)
                    if (distanceToEnemy < explosionRadius) {
                        // Apply Damage
                        val actualDamage = calculateFalloffDamage(explosionDamage, distanceToEnemy, explosionRadius)
                        if (enemy.takeDamage(actualDamage, DamageType.EXPLOSIVE, sceneManager) && enemy.currentState != AIState.DYING) {
                            sceneManager.enemySystem.startDeathSequence(enemy, sceneManager)
                        }

                        // Apply Knockback
                        val knockbackStrength = maxKnockbackForce * (1.0f - (distanceToEnemy / explosionRadius))
                        if (knockbackStrength > 0) {
                            val knockbackDirection = enemy.position.cpy().sub(explosionOrigin).apply { y = upwardLift }.nor()
                            val knockbackVector = knockbackDirection.scl(knockbackStrength)
                            sceneManager.enemySystem.applyKnockback(enemy, knockbackVector)
                        }
                    }
                }

                // Damage NPCs
                sceneManager.activeNPCs.forEach { npc ->
                    val distanceToNPC = npc.position.dst(validGroundPosition)
                    if (distanceToNPC < explosionRadius) {
                        // Apply Damage
                        val actualDamage = calculateFalloffDamage(explosionDamage, distanceToNPC, explosionRadius)
                        if (npc.takeDamage(actualDamage, DamageType.EXPLOSIVE, sceneManager) && npc.currentState != NPCState.DYING) {
                            sceneManager.npcSystem.startDeathSequence(npc, sceneManager)
                        }

                        // Apply Knockback
                        val knockbackStrength = maxKnockbackForce * (1.0f - (distanceToNPC / explosionRadius))
                        if (knockbackStrength > 0) {
                            val knockbackDirection = npc.position.cpy().sub(explosionOrigin).apply { y = upwardLift }.nor()
                            val knockbackVector = knockbackDirection.scl(knockbackStrength)
                            sceneManager.npcSystem.applyKnockback(npc, knockbackVector)
                        }
                    }
                }

                // Damage Player
                val distanceToPlayerSelf = getPosition().dst(validGroundPosition)
                if (distanceToPlayerSelf < explosionRadius) {
                    val actualDamage = calculateFalloffDamage(explosionDamage, distanceToPlayerSelf, explosionRadius)
                    takeDamage(actualDamage)

                    // Calculate the knockback strength based on distance
                    val knockbackStrength = maxKnockbackForce * (1.0f - (distanceToPlayerSelf / explosionRadius))

                    if (knockbackStrength > 0) {
                        // Calculate the direction vector
                        val knockbackDirection = getPosition().sub(explosionOrigin)
                        knockbackDirection.y = upwardLift

                        if (knockbackDirection.len2() > 0.001f) {
                            knockbackDirection.nor()

                            // Create the final knockback vector and apply it
                            val knockbackVector = knockbackDirection.scl(knockbackStrength)
                            applyKnockback(knockbackVector)

                            println("Player knocked back with velocity: $knockbackVector")
                        }
                    }
                }
            }
            WeaponType.MOLOTOV -> {
                // If the Molotov's lifetime ended without hitting anything, do nothing
                if (collisionResult == null) {
                    return
                }

                // If we are here, the Molotov DID hit something. Proceed with spawning fire.
                val validGroundPosition = getValidGroundImpactPosition(collisionResult, sceneManager, throwable.position)
                println("Molotov fire originating at $validGroundPosition")

                val fireSystem = sceneManager.game.fireSystem
                val objectSystem = sceneManager.game.objectSystem
                val lightingManager = sceneManager.game.lightingManager

                // Configure fire system settings
                val originalFadesOut = fireSystem.nextFireFadesOut
                val originalLifetime = fireSystem.nextFireLifetime
                val originalMinScale = fireSystem.nextFireMinScale
                val originalMaxScale = fireSystem.nextFireMaxScale
                fireSystem.nextFireFadesOut = true
                fireSystem.nextFireLifetime = 15f + Random.nextFloat() * 10f
                fireSystem.nextFireMinScale = 0.5f
                fireSystem.nextFireMaxScale = 0.8f

                // Spawn fires with robust collision checking, starting from our valid position.
                val fireCount = (1..5).random()
                val spreadRadius = 7.0f
                var spawnedCount = 0

                // The groundZeroPosition is now the already-calculated validGroundPosition
                if (sceneManager.isPositionValidForFire(validGroundPosition)) {
                    fireSystem.addFire(
                        position = validGroundPosition,
                        objectSystem = objectSystem,
                        lightingManager = lightingManager,
                        canSpread = true,
                        lightIntensityOverride = 35f,
                        lightRangeOverride = 15f
                    )
                    spawnedCount++
                }

                for (i in 1 until fireCount) {
                    val finalX = validGroundPosition.x + (Random.nextFloat() * 2f - 1f) * spreadRadius
                    val finalZ = validGroundPosition.z + (Random.nextFloat() * 2f - 1f) * spreadRadius

                    val potentialGroundY = sceneManager.findHighestSupportY(finalX, finalZ, validGroundPosition.y + 2f, 0.1f, blockSize)
                    val firePosition = Vector3(finalX, potentialGroundY, finalZ)

                    // Only spawn the fire if the position is not inside a block.
                    if (sceneManager.isPositionValidForFire(firePosition)) {
                        fireSystem.addFire(
                            position = firePosition,
                            objectSystem = objectSystem,
                            lightingManager = lightingManager,
                            canSpread = true,
                            lightIntensityOverride = 35f, // Dimmer light
                            lightRangeOverride = 10f // Smaller range
                        )
                        spawnedCount++
                    }
                }
                println("Molotov spawned $spawnedCount valid fires.")

                // Restore the original FireSystem settings
                fireSystem.nextFireFadesOut = originalFadesOut
                fireSystem.nextFireLifetime = originalLifetime
                fireSystem.nextFireMinScale = originalMinScale
                fireSystem.nextFireMaxScale = originalMaxScale
            }
            else -> {}
        }
    }

    // Collision detection logic for throwables
    private fun checkThrowableCollision(throwable: ThrowableEntity, sceneManager: SceneManager): CollisionResult? {
        val throwableBounds = throwable.getBoundingBox()

        // Check against Blocks
        for (block in sceneManager.activeChunkManager.getAllBlocks()) {
            if (!block.blockType.hasCollision) continue
            if (throwableBounds.intersects(block.getBoundingBox(blockSize, BoundingBox()))) {
                return CollisionResult(HitObjectType.BLOCK, block, throwable.position, Vector3.Y)
            }
        }
        for (meshObject in sceneManager.activeHouses.map { it to HitObjectType.HOUSE } + sceneManager.activeInteriors.filter { it.interiorType.is3D && it.interiorType.hasCollision }.map { it to HitObjectType.INTERIOR }) {
            val collides = when (val obj = meshObject.first) {
                is GameHouse -> obj.collidesWithMesh(throwableBounds)
                is GameInterior -> obj.collidesWithMesh(throwableBounds)
                else -> false
            }
            if (collides) return CollisionResult(meshObject.second, meshObject.first, throwable.position, Vector3.Y)
        }
        return null
    }

    private fun checkBulletCollision(bullet: Bullet, sceneManager: SceneManager): CollisionResult? {
        val travelDistanceSq = bullet.velocity.len2() * (Gdx.graphics.deltaTime * Gdx.graphics.deltaTime)
        if (travelDistanceSq == 0f) return null // Don't check if bullet hasn't moved

        val bulletRay = Ray(bullet.position.cpy().mulAdd(bullet.velocity, -Gdx.graphics.deltaTime), bullet.velocity.cpy().nor())
        val intersectionPoint = Vector3()

        var closestResult: CollisionResult? = null
        var closestDistSq = Float.MAX_VALUE

        // 1. Check against Blocks
        for (block in sceneManager.activeChunkManager.getAllBlocks()) {
            // Ignore invisible blocks or blocks without collision
            if (!block.blockType.hasCollision || !block.blockType.isVisible) continue

            val blockBounds = block.getBoundingBox(blockSize, BoundingBox())
            if (Intersector.intersectRayBounds(bulletRay, blockBounds, intersectionPoint)) {
                val distSq = bulletRay.origin.dst2(intersectionPoint)
                if (distSq <= travelDistanceSq && distSq < closestDistSq) {
                    val blockCenter = block.position
                    val relativeHitPos = intersectionPoint.cpy().sub(blockCenter)
                    val normal = Vector3()

                    if (kotlin.math.abs(relativeHitPos.x) > kotlin.math.abs(relativeHitPos.y) && kotlin.math.abs(relativeHitPos.x) > kotlin.math.abs(relativeHitPos.z)) {
                        normal.set(if (relativeHitPos.x > 0) 1f else -1f, 0f, 0f)
                    } else if (kotlin.math.abs(relativeHitPos.y) > kotlin.math.abs(relativeHitPos.x) && kotlin.math.abs(relativeHitPos.y) > kotlin.math.abs(relativeHitPos.z)) {
                        normal.set(0f, if (relativeHitPos.y > 0) 1f else -1f, 0f)
                    } else {
                        normal.set(0f, 0f, if (relativeHitPos.z > 0) 1f else -1f)
                    }
                    closestResult = CollisionResult(HitObjectType.BLOCK, block, intersectionPoint.cpy(), normal)
                    closestDistSq = distSq
                }
            }
        }

        // 2. Check against complex meshes
        val allMeshes = sceneManager.activeHouses.map { it to HitObjectType.HOUSE } +
            sceneManager.activeInteriors.filter { it.interiorType.is3D && it.interiorType.hasCollision }.map { it to HitObjectType.INTERIOR }

        for ((meshObject, type) in allMeshes) {
            var hit = false
            when (meshObject) {
                is GameHouse -> { if (meshObject.intersectsRay(bulletRay, intersectionPoint)) hit = true }
                is GameInterior -> { if (meshObject.intersectsRay(bulletRay, intersectionPoint)) hit = true }
            }

            if (hit) {
                val distSq = bulletRay.origin.dst2(intersectionPoint)
                if (distSq <= travelDistanceSq && distSq < closestDistSq) {
                    val normal = bullet.velocity.cpy().nor().scl(-1f)
                    closestResult = CollisionResult(type, meshObject, intersectionPoint.cpy(), normal)
                    closestDistSq = distSq
                }
            }
        }

        // 3. Check against simple bounding boxes
        val simpleObjects = sceneManager.activeEnemies.map { it to HitObjectType.ENEMY } +
            sceneManager.activeNPCs.map { it to HitObjectType.NPC } +
            sceneManager.activeObjects.filter { !it.objectType.isInvisible }.map { it to HitObjectType.OBJECT } +
            sceneManager.activeCars.map { it to HitObjectType.CAR }

        for ((obj, type) in simpleObjects) {
            val bounds = when(obj) {
                is GameEnemy -> if (obj.currentState == AIState.DYING) continue else obj.physics.bounds
                is GameNPC -> if (obj.currentState == NPCState.DYING) continue else obj.physics.bounds
                is GameObject -> obj.getBoundingBox()
                is GameCar -> obj.getBoundingBox()
                else -> continue
            }

            if (Intersector.intersectRayBounds(bulletRay, bounds, intersectionPoint)) {
                val distSq = bulletRay.origin.dst2(intersectionPoint)
                if (distSq <= travelDistanceSq && distSq < closestDistSq) {

                    // If the bullet's owner is an enemy, and it hits that SAME enemy, ignore the collision.
                    if (bullet.owner is GameEnemy && obj is GameEnemy && bullet.owner.id == obj.id) {
                        continue // Skip this collision, it's the enemy shooting itself.
                    }

                    val normal = bullet.velocity.cpy().nor().scl(-1f)
                    closestResult = CollisionResult(type, obj, intersectionPoint.cpy(), normal)
                    closestDistSq = distSq
                }
            }
        }

        // Check if an enemy bullet hits the player
        if (bullet.owner is GameEnemy) {
            if (Intersector.intersectRayBounds(bulletRay, getPlayerBounds(), intersectionPoint)) {
                val distSq = bulletRay.origin.dst2(intersectionPoint)
                if (distSq <= travelDistanceSq && distSq < closestDistSq) {
                    // For simplicity, we don't need a normal when hitting the player
                    return CollisionResult(HitObjectType.PLAYER, this, intersectionPoint.cpy(), Vector3.Zero)
                }
            }
        }

        return closestResult
    }

    private fun spawnBloodEffects(position: Vector3, sceneManager: SceneManager) {
        val violenceLevel = sceneManager.game.uiManager.getViolenceLevel()

        if (violenceLevel == ViolenceLevel.NO_VIOLENCE) {
            return // Don't spawn any blood effects
        }

        val bloodSplatterEffects = listOf(
            ParticleEffectType.BLOOD_SPLATTER_1,
            ParticleEffectType.BLOOD_SPLATTER_2,
            ParticleEffectType.BLOOD_SPLATTER_3,
        )
        val bloodDripEffect = ParticleEffectType.BLOOD_DRIP

        // All possible effects, used for random picking
        val allBloodEffects = bloodSplatterEffects + bloodDripEffect

        // Spawn a random number of particles
        val particleCount = when (violenceLevel) {
            ViolenceLevel.REDUCED_VIOLENCE -> (0..1).random() // Spawns 0 or 1 particles
            ViolenceLevel.FULL_VIOLENCE -> (1..3).random()    // Original behavior
            else -> 0
        }

        for (i in 0 until particleCount) {
            // Pick a random effect from our list
            val randomEffect = allBloodEffects.random()

            if (randomEffect == bloodDripEffect) {
                val randomVelocity = Vector3(
                    (Math.random() - 0.5).toFloat() * 5f,
                    (Math.random() * 3f).toFloat(),
                    (Math.random() - 0.5).toFloat() * 5f
                )
                particleSystem.spawnEffect(randomEffect, position.cpy(), randomVelocity)
            } else {
                // 1. Find the ground Y-coordinate
                val groundY = sceneManager.findHighestSupportY(position.x, position.z, position.y, 0.1f, blockSize)
                val spawnPos = Vector3(position.x, groundY + 0.1f, position.z) // Place slightly above ground

                // 2. The normal for a flat ground surface is always straight up.
                val groundNormal = Vector3.Y

                // 3. Spawn the splatter
                particleSystem.spawnEffect(
                    type = randomEffect,
                    position = spawnPos,
                    baseDirection = null,
                    surfaceNormal = groundNormal,
                    gravityOverride = 0f
                )
            }
        }
    }

    private fun updatePlayerTransform() {
        // Reset transform matrix
        playerInstance.transform.idt()

        // Set position
        playerInstance.transform.setTranslation(physicsComponent.position)

        // Apply Y-axis rotation for Paper Mario effect
        playerInstance.transform.rotate(Vector3.Y, playerCurrentRotationY)
    }

    fun render(camera: Camera, environment: Environment) {
        // Do not render the player separately if they are driving
        if (isDriving) return
        // Set the environment for the billboard shader so it knows about the lights
        billboardShaderProvider.setEnvironment(environment)
        billboardModelBatch.begin(camera)

        // If the player is driving, do not render their 2D model.
        if (!isDriving) {
            billboardModelBatch.render(playerInstance, environment)
        }

        // Render all active bullets
        for (bullet in sceneManager.activeBullets) {
            billboardModelBatch.render(bullet.modelInstance, environment)
        }

        // Render all active throwables
        for (throwable in sceneManager.activeThrowables) {
            billboardModelBatch.render(throwable.modelInstance, environment)
        }

        billboardModelBatch.end()
    }

    fun getPosition(): Vector3 = physicsComponent.position.cpy()

    private fun updatePlayerTexture(newTexture: Texture) {
        // This line is fine, it just keeps track of the current texture reference.
        playerTexture = newTexture

        val instanceMaterial = playerInstance.materials.get(0)

        // Update the attribute on the correct material
        val textureAttribute = instanceMaterial.get(TextureAttribute.Diffuse) as TextureAttribute?
        textureAttribute?.textureDescription?.texture = newTexture

        // println("Updated texture for animation: ${animationSystem.getCurrentAnimationName()}")
    }

    // Add these utility methods for external animation control:
    fun playAnimation(animationName: String, restart: Boolean = false) {
        animationSystem.playAnimation(animationName, restart)
    }

    fun getCurrentAnimationName(): String? {
        return animationSystem.getCurrentAnimationName()
    }

    fun isAnimationFinished(): Boolean {
        return animationSystem.isAnimationFinished()
    }

    fun getCurrentMagazineCount(): Int {
        return currentMagazineCount
    }

    fun getCurrentReserveAmmo(): Int {
        return ammoReserves.getOrDefault(equippedWeapon, 0)
    }

    fun isReloading(): Boolean = isReloading

    fun getReloadProgress(): Float {
        if (!isReloading || equippedWeapon.reloadTime <= 0f) return 0f
        // Invert the ratio so it goes from 0 to 1
        return 1f - (reloadTimer / equippedWeapon.reloadTime)
    }

    fun getHealth(): Float = health
    fun getMaxHealth(): Float = maxHealth

    fun getWeaponReserves(): Map<WeaponType, Int> = ammoReserves

    fun getMagazineCounts(): Map<WeaponType, Int> {
        currentMagazineCounts[equippedWeapon] = currentMagazineCount
        return currentMagazineCounts.toMap()
    }

    private fun setMagazineCount(weaponType: WeaponType, count: Int) {
        currentMagazineCounts[weaponType] = count
        // If the weapon being loaded is the one we have equipped, also update the live counter
        if (equippedWeapon == weaponType) {
            currentMagazineCount = count
        }
    }

    fun loadState(data: PlayerStateData) {
        setPosition(data.position)
        health = maxHealth
        money = data.money

        // Load Reserve Ammo
        ammoReserves.clear()
        data.weapons.forEach { entry -> ammoReserves[entry.key] = entry.value }

        // Rebuild the list of weapons the player possesses
        weapons.clear()
        weapons.add(WeaponType.UNARMED)
        data.weapons.keys().forEach { weapon ->
            if (weapon != WeaponType.UNARMED && !weapons.contains(weapon)) {
                weapons.add(weapon)
            }
        }

        // Load Magazine Counts
        currentMagazineCounts.clear()
        data.currentMagazineCounts.forEach { entry ->
            setMagazineCount(entry.key, entry.value)
        }

        equipWeapon(data.equippedWeapon)
    }

    fun dispose() {
        playerModel.dispose()
        playerBackTexture.dispose()

        animationSystem.dispose()
        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()

        poseTextures.values.forEach { it.dispose() }
        bulletModels.values.forEach { it.dispose() }
        throwableModels.values.forEach { it.dispose() }
    }
}
