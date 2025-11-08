package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.*
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectMap
import java.util.*
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

data class PlayerWeaponInstance(
    val id: String = UUID.randomUUID().toString(), // A unique ID for this specific gun instance
    val weaponType: WeaponType,
    val soundVariationId: String?
)

class PlayerSystem {
    private lateinit var playerTexture: Texture
    private lateinit var playerModel: Model
    lateinit var playerInstance: ModelInstance
    private var currentSeat: CarSeat? = null
    private lateinit var playerMaterial: Material
    private var baseTextureWidth: Float = 1f
    private var baseTextureHeight: Float = 1f

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

    private var wasHoldingShootButton = false
    private var automaticFireSoundId: Long? = null

    private var respawnPosition = Vector3(9f, 2f, 9f)
    private var isDead = false

    lateinit var waterPuddleSystem: WaterPuddleSystem
    private var wetFootprintsTimer = 0f
    private val WET_FOOTPRINT_COOLDOWN = 10f

    fun isDead(): Boolean = isDead

    fun takeDamage(amount: Float) {
        // If the game is in editor mode, completely ignore all incoming damage.
        if (sceneManager.game.isEditorMode) {
            return
        }

        // Check for the active mission modifier
        val modifiers = sceneManager.game.missionSystem.activeModifiers

        // Check for invincibility first
        if (isDead) return
        if (modifiers?.setUnlimitedHealth == true) {
            println("Player is invincible due to mission modifier. Damage blocked.")
            return
        }

        var finalDamage = amount

        // Apply one-hit kill modifier from enemies
        if (modifiers?.enemiesHaveOneHitKills == true) {
            finalDamage = this.maxHealth // Instantly kill the player
        }

        // Apply incoming damage multiplier
        if (modifiers != null) {
            finalDamage *= modifiers.incomingDamageMultiplier
            if (modifiers.incomingDamageMultiplier != 1.0f) {
                println("Incoming damage modified by ${modifiers.incomingDamageMultiplier}x. New damage: $finalDamage")
            }
        }

        if (isDriving) return

        if (health > 0) {
            // Play the hurt sound before applying damage if damage will be taken
            if (finalDamage > 0) {
                // Play the hurt sound with a slight random pitch variation
                val randomPitch = 1.0f + (Random.nextFloat() * 0.1f - 0.05f) // Varies pitch by +/- 5%
                sceneManager.game.soundManager.playSound(
                    id = "PLAYER_HURT",
                    position = getPosition(),
                    reverbProfile = null,
                    pitch = randomPitch
                )
            }

            health -= finalDamage // Use the final calculated damage
            println("Player took damage! Health is now: ${health.toInt()}")
            if (health <= 0) {
                health = 0f
                println("Player has been defeated!")
                // You can add logic here for player death/respawn
                startDeathSequence()
            }
        }
    }

    private fun startDeathSequence() {
        if (isDead || sceneManager.game.isEditorMode) return

        println("--- PLAYER DEATH SEQUENCE INITIATED ---")
        isDead = true

        // Play a heavy, final impact sound for death
        sceneManager.game.soundManager.playSound(
            effect = SoundManager.Effect.CAR_CRASH_HEAVY, // A good, deep thud sound
            position = getPosition(),
            reverbProfile = SoundManager.DEFAULT_REVERB
        )

        state = PlayerState.IDLE
        throwChargeTime = 0f
        isReloading = false
        reloadTimer = 0f

        // 1. Visual Feedback: Apply a strong "Film Noir" or "Black & White" shader effect
        sceneManager.game.shaderEffectManager.setRoomOverride(ShaderEffect.FILM_NOIR)

        // 2. Gameplay Feedback: Fail the current mission
        sceneManager.game.missionSystem.failMissionOnPlayerDeath()

        // 3. UI Feedback: Show a "YOU DIED" message
        sceneManager.game.uiManager.showDeathScreen()
    }

    fun respawn() {
        if (!isDead) return

        println("--- RESPAWNING PLAYER ---")

        sceneManager.game.soundManager.playSound(
            effect = SoundManager.Effect.TELEPORT, // A fitting sound for reappearing
            position = respawnPosition, // Play the sound where the player will appear
            reverbProfile = null
        )

        // 1. Reset player state
        health = maxHealth
        isDead = false
        isOnFire = false // Extinguish fire on death

        equipWeapon(WeaponType.UNARMED)

        // 2. Reset visuals and UI
        sceneManager.game.shaderEffectManager.clearRoomOverride()
        sceneManager.game.uiManager.hideDeathScreen()
        sceneManager.cameraManager.stopShake()
        sceneManager.game.uiManager.showMoneyUpdate(getMoney())

        // 3. Move player to respawn position
        if (sceneManager.currentScene != SceneType.WORLD) {
            sceneManager.transitionToWorld()
            // We need to delay the position set until after the transition
            Gdx.app.postRunnable {
                setPosition(respawnPosition)
                sceneManager.cameraManager.resetAndSnapToPlayer(respawnPosition, false)
            }
        } else {
            setPosition(respawnPosition)
            sceneManager.cameraManager.resetAndSnapToPlayer(respawnPosition, false)
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
            return !MathUtils.isEqual(playerCurrentRotationY, playerTargetRotationY, 1.0f)
        }

    // Animation system
    lateinit var animationSystem: AnimationSystem
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
    var drivingCar: GameCar? = null

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

    val equippedWeapon: WeaponType
        get() = weaponInstances.getOrNull(currentWeaponIndex)?.weaponType ?: WeaponType.UNARMED
    private var weaponInstances: MutableList<PlayerWeaponInstance> = mutableListOf(PlayerWeaponInstance(weaponType = WeaponType.UNARMED, soundVariationId = null))
    private var currentWeaponIndex = 0

    private fun getEquippedWeaponInstance(): PlayerWeaponInstance? {
        return weaponInstances.getOrNull(currentWeaponIndex)
    }

    private val shootingPoseDuration = 0.2f
    private val MIN_THROW_CHARGE_TIME = 0.1f
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
    private lateinit var bulletTrailSystem: BulletTrailSystem
    private val waterSplashSoundIds = (1..10).map { i -> "WATER_SPLASH_V$i" }

    private val lockedCarSounds = listOf(
        "CAR_LOCKED_V1",
        "CAR_LOCKED_V2",
        "CAR_LOCKED_V3",
        "CAR_LOCKED_V4",
        "CAR_LOCKED_V5",
        "CAR_LOCKED_V6",
        "CAR_LOCKED_V7",
        "CAR_LOCKED_V8",
        "CAR_LOCKED_V9",
        SoundManager.Effect.DOOR_LOCKED.name
    )
     private val footstepSoundIds = listOf(
        "FOOTSTEP_V1", "FOOTSTEP_V2", "FOOTSTEP_V3",
        "FOOTSTEP_V4", "FOOTSTEP_V5", "FOOTSTEP_V6"
    )

    fun resetWetnessFromRain() {
        wetFootprintsTimer = 0f // ONLY reset the timer for wet footprints
        println("Player's feet are no longer wet from rain.")
    }

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

        // 3. ADD THIS LINE HERE: Assign the system inside initialize()
        this.bulletTrailSystem = sceneManager.game.bulletTrailSystem

        physicsComponent = PhysicsComponent(
            position = Vector3(0f, 2f, 0f), // Default start position
            size = this.playerSize,
            speed = 8f // Player's specific speed
        )

        setupAnimationSystem()
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
            name = "attack_baseball_bat",
            texturePaths = arrayOf(
                "textures/player/weapons/baseball_bat/player_baseball_bat.png",
                "textures/player/weapons/baseball_bat/player_baseball_bat_two.png",
                "textures/player/weapons/baseball_bat/player_baseball_bat_three.png"
            ),
            frameDuration = 0.15f,
            isLooping = false,
            frameOffsetsX = floatArrayOf(0f, 0.245f, 0.34f)
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

        // --- STORE BASE DIMENSIONS ---
        baseTextureWidth = playerTexture.width.toFloat()
        baseTextureHeight = playerTexture.height.toFloat()

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

            // Play a procedural "click" sound at the player's position.
            sceneManager.game.soundManager.playSound(
                effect = SoundManager.Effect.RELOAD_CLICK,
                position = getPosition(), // The sound originates from the player
                reverbProfile = null // A dry click sounds better without reverb
            )

            checkAndRemoveWeaponIfOutOfAmmo()
            return
        }

        // Start the reload process
        isReloading = true
        reloadTimer = equippedWeapon.reloadTime

        // PLAY RELOAD SOUND
        val reloadSoundId = equippedWeapon.soundIdReload ?: SoundManager.Effect.RELOAD_CLICK.name
        sceneManager.game.soundManager.playSound(
            id = reloadSoundId,
            position = getPosition(), // Play sound at the player's position
            reverbProfile = SoundManager.DEFAULT_REVERB
        )

        println("Reloading... (${equippedWeapon.reloadTime}s)")
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
        if (isDead) return

        // State tracking for button presses
        val isCurrentlyHoldingShoot = Gdx.input.isButtonPressed(Input.Buttons.LEFT)
        val justPressedShoot = isCurrentlyHoldingShoot && !wasHoldingShootButton
        val justReleasedShoot = !isCurrentlyHoldingShoot && wasHoldingShootButton
        wasHoldingShootButton = isCurrentlyHoldingShoot

        if (isCurrentlyHoldingShoot && equippedWeapon.actionType == WeaponActionType.SHOOTING) {
            chargeTime += deltaTime
        } else {
            chargeTime = 0f
        }

        // Timers
        fireRateTimer -= deltaTime
        attackTimer -= deltaTime

        // Stop automatic fire sound when the button is released
        if (justReleasedShoot && automaticFireSoundId != null) {
            sceneManager.game.soundManager.fadeOutAndStopLoopingSound(automaticFireSoundId!!, 0.2f)
            automaticFireSoundId = null
        }

        // State Machine Logic
        when (state) {
            PlayerState.IDLE -> {
                // If we are on cooldown, don't even check for input
                if (fireRateTimer > 0f) {
                    return
                }

                when (equippedWeapon.actionType) {
                    WeaponActionType.SHOOTING -> {
                        // 1. Determine the intended shooting direction from player input.
                        var intendedDirectionX = 0f
                        if (Gdx.input.isKeyPressed(Input.Keys.A)) intendedDirectionX = -1f
                        if (Gdx.input.isKeyPressed(Input.Keys.D)) intendedDirectionX = 1f

                        // If no movement keys are pressed, the direction is based on where the player is already facing.
                        if (intendedDirectionX == 0f) {
                            intendedDirectionX = if (playerCurrentRotationY == 180f) -1f else 1f
                        }

                        // 2. Check if the player's visual rotation matches the intended direction.
                        val isFacingCorrectDirection = (intendedDirectionX < 0 && playerCurrentRotationY == 180f) || (intendedDirectionX > 0 && playerCurrentRotationY == 0f)
                        val soundManager = sceneManager.game.soundManager

                        // Case 1: Automatic Weapon
                        if (equippedWeapon.soundIdAutomaticLoop != null) {

                            // 3. The final condition to allow shooting.
                            val canShootNow = canShoot() && !isReloading && isCurrentlyHoldingShoot && !isRotating && isFacingCorrectDirection

                            if (canShootNow) {
                                val isFirstShotOfBurst = justPressedShoot

                                // On the very first press of the trigger, play the distinct single-shot sound.
                                if (isFirstShotOfBurst && equippedWeapon.soundIdSingleShot != null) {
                                    soundManager.playSound(id = equippedWeapon.soundIdSingleShot!!, position = getPosition(), reverbProfile = SoundManager.DEFAULT_REVERB)
                                }

                                // If the automatic loop isn't already playing, schedule it to start.
                                if (automaticFireSoundId == null) {
                                    com.badlogic.gdx.utils.Timer.schedule(object : com.badlogic.gdx.utils.Timer.Task() {
                                        override fun run() {
                                            // Double-check if the player is still holding the fire button after the short delay.
                                            if (wasHoldingShootButton) {
                                                automaticFireSoundId = soundManager.playSound(
                                                    id = equippedWeapon.soundIdAutomaticLoop!!,
                                                    position = getPosition(), loop = true, reverbProfile = SoundManager.DEFAULT_REVERB
                                                )
                                            }
                                        }
                                    }, 0.1f) // 100ms delay to let the single-shot sound be heard.
                                }

                                spawnBullet()
                                fireRateTimer = equippedWeapon.fireCooldown
                                state = PlayerState.ATTACKING
                                attackTimer = shootingPoseDuration

                                if (currentMagazineCount <= 0 && automaticFireSoundId != null) {
                                    sceneManager.game.soundManager.fadeOutAndStopLoopingSound(automaticFireSoundId!!, 0.2f)
                                    automaticFireSoundId = null
                                }
                            } else {
                                if (automaticFireSoundId != null) {
                                    sceneManager.game.soundManager.stopLoopingSound(automaticFireSoundId!!)
                                    automaticFireSoundId = null
                                }
                                if (justPressedShoot && !canShoot() && !isReloading) {
                                    soundManager.playSound(id = SoundManager.Effect.RELOAD_CLICK.name, position = getPosition(), reverbProfile = null)
                                    println("Click! (Out of ammo or empty magazine)")
                                    checkAndRemoveWeaponIfOutOfAmmo()
                                }
                            }
                        }
                        // Case 2: Semi-Automatic Weapon (Revolver, Shotgun, etc.)
                        else {
                            val canShootSemiAuto = canShoot() && !isReloading && justPressedShoot && !isRotating && isFacingCorrectDirection
                            // Only play a sound on the initial press for semi-auto weapons.
                            if (canShootSemiAuto) {
                                // Get the specific sound ID from the equipped gun instance
                                val equippedInstance = getEquippedWeaponInstance()
                                var soundIdToPlay = equippedInstance?.soundVariationId ?: equippedWeapon.soundIdSingleShot
                                var volumeMultiplier = 1.0f
                                var reverb: SoundManager.ReverbProfile? = SoundManager.DEFAULT_REVERB

                                if (equippedWeapon.soundId == "GUNSHOT_SHOTGUN") {
                                    val variation = Random.nextInt(1, equippedWeapon.soundVariations + 1)
                                    soundIdToPlay = "GUNSHOT_SHOTGUN_V$variation"
                                    volumeMultiplier = 0.8f // Make shotguns a bit quieter
                                    reverb = SoundManager.ReverbProfile( // Give shotguns a custom "boom" echo
                                        numEchoes = 4,
                                        delayStep = 0.12f,
                                        volumeFalloff = 0.65f
                                    )
                                }

                                if (soundIdToPlay != null) {
                                    soundManager.playSound(
                                        id = soundIdToPlay,
                                        position = getPosition(),
                                        reverbProfile = reverb,
                                        volumeMultiplier = volumeMultiplier
                                    )
                                } else {
                                    // Ultimate fallback if no sound is defined at all for this weapon.
                                    println("WARN: No sound ID found for ${equippedWeapon.displayName}. Playing procedural fallback.")
                                    soundManager.playSound(effect = SoundManager.Effect.GUNSHOT_REVOLVER, position = getPosition(), reverbProfile = SoundManager.DEFAULT_REVERB)
                                }

                                // This function will now handle ammo reduction
                                spawnBullet()
                                fireRateTimer = equippedWeapon.fireCooldown
                                state = PlayerState.ATTACKING
                                attackTimer = shootingPoseDuration

                            } else if (justPressedShoot && !canShoot() && !isReloading) {
                                soundManager.playSound(id = SoundManager.Effect.RELOAD_CLICK.name, position = getPosition(), reverbProfile = null)
                                println("Click! (Out of ammo or empty magazine)")
                                checkAndRemoveWeaponIfOutOfAmmo()
                            }
                        }

                        // Reload logic
                        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
                            reload()
                        }
                    }
                    WeaponActionType.MELEE -> {
                        if (justPressedShoot && !isRotating) {
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
                    WeaponActionType.THROWABLE -> {
                        // Check for input to start an action
                        if (justPressedShoot) {
                            state = PlayerState.CHARGING_THROW
                            throwChargeTime = 0f
                        }
                    }
                }
            }
            // Other states are unchanged
            PlayerState.ATTACKING -> { if (attackTimer <= 0f) { state = PlayerState.IDLE; if (equippedWeapon.actionType == WeaponActionType.MELEE) { animationSystem.playAnimation("idle") } } }
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
                if (!isCurrentlyHoldingShoot) {
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

        var hitSomething = false

        // Check against enemies
        val enemyIterator = sceneManager.activeEnemies.iterator()
        while(enemyIterator.hasNext()) {
            val enemy = enemyIterator.next()

            // Only hit enemies that are in a valid state
            if (enemy.currentState != AIState.DYING && hitBox.intersects(enemy.physics.bounds)) {

                if (!hitSomething) {
                    equippedWeapon.soundIdMeleeHit?.randomOrNull()?.let { soundId ->
                        sceneManager.game.soundManager.playSound(id = soundId, position = enemy.position, reverbProfile = SoundManager.DEFAULT_REVERB)
                    }
                    hitSomething = true
                }

                println("Melee hit on enemy: ${enemy.enemyType.displayName}")

                val modifiers = sceneManager.game.missionSystem.activeModifiers
                val damageToDeal = if (modifiers?.playerHasOneHitKills == true) {
                    enemy.health // Deal exactly enough damage to kill
                } else {
                    equippedWeapon.damage // Deal normal weapon damage
                }

                // SHAKE
                val shakeIntensity = if (equippedWeapon == WeaponType.BASEBALL_BAT) 0.15f else 0.07f
                val shakeDuration = if (equippedWeapon == WeaponType.BASEBALL_BAT) 0.12f else 0.08f
                sceneManager.cameraManager.startShake(shakeDuration, shakeIntensity)

                val bloodSpawnPosition = enemy.position.cpy().add(0f, enemy.enemyType.height / 2f, 0f)
                val violenceLevel = sceneManager.game.uiManager.getViolenceLevel()

                if (violenceLevel == ViolenceLevel.ULTRA_VIOLENCE || Random.nextFloat() < 0.75f) {
                    spawnBloodEffects(bloodSpawnPosition, sceneManager)
                }

                if (enemy.takeDamage(damageToDeal, DamageType.MELEE, sceneManager, this) && enemy.currentState != AIState.DYING) {
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
                sceneManager.game.soundManager.playSound(
                    effect = SoundManager.Effect.PUNCH_HIT,
                    position = npc.position, // Play the sound at the NPC's location
                    reverbProfile = SoundManager.DEFAULT_REVERB
                )

                println("Melee hit on NPC: ${npc.npcType.displayName}")

                val modifiers = sceneManager.game.missionSystem.activeModifiers
                val damageToDeal = if (modifiers?.playerHasOneHitKills == true) {
                    npc.health // Deal exactly enough damage to kill
                } else {
                    equippedWeapon.damage // Deal normal weapon damage
                }

                val bloodSpawnPosition = npc.physics.position.cpy()
                val violenceLevel = sceneManager.game.uiManager.getViolenceLevel()

                if (violenceLevel == ViolenceLevel.ULTRA_VIOLENCE || Random.nextFloat() < 0.75f) { // 75% chance to bleed
                    spawnBloodEffects(bloodSpawnPosition, sceneManager)
                }

                if (npc.takeDamage(damageToDeal, DamageType.MELEE, sceneManager) && npc.currentState != NPCState.DYING) {
                    sceneManager.npcSystem.startDeathSequence(npc, sceneManager)
                }
            }
        }

        // Check against cars
        for (car in sceneManager.activeCars) {
            if (hitBox.intersects(car.getBoundingBox())) {
                println("Melee hit on car: ${car.carType.displayName}")

                val modifiers = sceneManager.game.missionSystem.activeModifiers
                val damageToDeal = if (modifiers?.playerHasOneHitKills == true) {
                    car.health // Deal exactly enough damage to destroy
                } else {
                    equippedWeapon.damage // Deal normal weapon damage
                }

                car.takeDamage(damageToDeal, DamageType.MELEE)
            }
        }
    }

    private fun spawnThrowable() {
        val model = throwableModels[equippedWeapon] ?: return

        // First, consume one throwable from the reserves.
        val currentAmmo = ammoReserves.getOrDefault(equippedWeapon, 0)
        ammoReserves[equippedWeapon] = (currentAmmo - 1).coerceAtLeast(0)

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

        if (equippedWeapon != modifiers?.infiniteAmmoForWeapon && modifiers?.infiniteAmmo != true) {
            if (equippedWeapon.requiresReload) {
                currentMagazineCount--
            } else {
                val currentReserve = ammoReserves.getOrDefault(equippedWeapon, 0)
                ammoReserves[equippedWeapon] = maxOf(0, currentReserve - 1)
            }
        }

        val bulletModel = bulletModels[equippedWeapon.bulletTexturePath] ?: return

        val pelletCount = equippedWeapon.pelletCount.coerceAtLeast(1)
        val directionX = if (playerCurrentRotationY == 180f) -1f else 1f
        val baseDirection = Vector3(directionX, 0f, 0f)

        val bulletSpawnOffsetX = directionX * 1.5f
        val bulletSpawnVerticalOffset = -playerSize.y * 0.1f // Lower the bullet spawn point from the player's center
        val bulletSpawnPos = physicsComponent.position.cpy().add(bulletSpawnOffsetX, bulletSpawnVerticalOffset, 0f)

        // Determine rotation
        val bulletRotation = if (directionX < 0) 180f else 0f
        val damagePerPellet = equippedWeapon.damage / pelletCount
        val damageMultiplier = modifiers?.playerDamageMultiplier ?: 1.0f

        for (i in 0 until pelletCount) {
            val finalDirection = if (pelletCount > 1) {
                val spreadAngle = 12.0f
                val randomSpread = (Random.nextFloat() - 0.5f) * spreadAngle
                val spreadRotation = Matrix4().setToRotation(Vector3.Y, randomSpread.toFloat())
                baseDirection.cpy().mul(spreadRotation)
            } else {
                baseDirection // Not a shotgun, no spread.
            }

            val velocity = finalDirection.cpy().scl(equippedWeapon.bulletSpeed)

            val bullet = Bullet(
                position = bulletSpawnPos.cpy(),
                velocity = velocity,
                modelInstance = ModelInstance(bulletModel),
                lifetime = equippedWeapon.bulletLifetime,
                rotationY = bulletRotation,
                owner = this,
                damage = damagePerPellet
            )

            // Apply player damage multiplier from mission modifier
            bullet.damage *= damageMultiplier

            sceneManager.activeBullets.add(bullet)
        }

        // 3. POST-SHOT SOUND (SHOTGUN PUMP)
        if (equippedWeapon == WeaponType.SHOTGUN) {
            equippedWeapon.soundIdPostShotAction?.let { soundIdBase ->
                // Randomize between the two pump sounds (_V1, _V2)
                val variation = Random.nextInt(1, 2 + 1)
                val pumpSoundId = "${soundIdBase}_V$variation"

                com.badlogic.gdx.utils.Timer.schedule(object : com.badlogic.gdx.utils.Timer.Task() {
                    override fun run() {
                        sceneManager.game.soundManager.playSound(id = pumpSoundId, position = getPosition(), reverbProfile = SoundManager.DEFAULT_REVERB)
                    }
                }, 0.3f) // 0.3 second delay for the pump sound
            }
        }

        // 4. SHELL CASING EJECTION
        if (equippedWeapon.actionType == WeaponActionType.SHOOTING && equippedWeapon != WeaponType.REVOLVER) {

            // Eject the casing in the opposite direction the player is facing.
            val ejectDirectionX = -directionX

            // Spawn position is near the center of the player model.
            val casingSpawnPos = physicsComponent.position.cpy().add(0f, 1f, 0f)

            // Give it an initial velocity pushing it sideways and slightly up.
            val casingVelocity = Vector3(ejectDirectionX * (Random.nextFloat() * 4f + 4f), 8f, 0f)

            particleSystem.spawnEffect(
                type = ParticleEffectType.SHELL_CASING_PLAYER,
                position = casingSpawnPos,
                baseDirection = casingVelocity // Use baseDirection to pass the velocity vector
            )
        }

        // Trigger camera shake for heavy weapons
        if (equippedWeapon == WeaponType.MACHINE_GUN || equippedWeapon == WeaponType.TOMMY_GUN || equippedWeapon.pelletCount > 1) {
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

    fun addWeaponToInventory(weaponType: WeaponType, ammo: Int, soundVariationId: String? = null) {
        // Check if player already has an instance of this weapon type
        val existingInstance = weaponInstances.find { it.weaponType == weaponType }

        if (existingInstance == null) {
            // Player does not have this weapon type, add a new instance
            val soundId = soundVariationId ?: run {
                if (weaponType.soundVariations > 0 && weaponType.soundId != null) {
                    val randomVariation = Random.nextInt(1, weaponType.soundVariations + 1)
                    "${weaponType.soundId}_V$randomVariation"
                } else {
                    null
                }
            }
            weaponInstances.add(PlayerWeaponInstance(weaponType = weaponType, soundVariationId = soundId))
        }

        addAmmoToReserves(weaponType, ammo)
    }

    fun hasWeapon(weaponType: WeaponType): Boolean {
        if (weaponType == WeaponType.UNARMED) return true // Player always has fists
        return weaponInstances.any { it.weaponType == weaponType }
    }

    fun clearInventory() {
        weaponInstances.clear()
        ammoReserves.clear()
        currentMagazineCounts.clear()
        // Always give the player their fists back.
        equipWeapon(WeaponType.UNARMED)
    }


    fun equipWeapon(weaponType: WeaponType, ammoToGive: Int? = null, soundVariationId: String? = null) {
        // Find the index of the first instance of this weapon type
        val index = weaponInstances.indexOfFirst { it.weaponType == weaponType }

        if (index != -1) {
            currentWeaponIndex = index
        } else {
            // Weapon not in inventory, add it
            if (weaponType == WeaponType.UNARMED) {
                weaponInstances.add(0, PlayerWeaponInstance(weaponType = WeaponType.UNARMED, soundVariationId = null))
                currentWeaponIndex = 0
            } else {
                val newInstance = PlayerWeaponInstance(weaponType = weaponType, soundVariationId = soundVariationId)
                weaponInstances.add(newInstance)
                currentWeaponIndex = weaponInstances.size - 1
            }
        }


        this.currentMagazineCount = currentMagazineCounts[weaponType] ?: weaponType.magazineSize // If it's null, THEN default to a full magazine.
        currentMagazineCounts[weaponType] = this.currentMagazineCount

        isReloading = false
        reloadTimer = 0f

        // Add the ammo from the picked-up item to reserves
        if (ammoToGive != null && ammoToGive > 0) {
            addAmmoToReserves(weaponType, ammoToGive)
        }

        println("Player equipped: ${equippedWeapon.displayName}. Sound: ${getEquippedWeaponInstance()?.soundVariationId ?: "Default"}")
    }

    fun removeWeaponFromInventory(weaponType: WeaponType) {
        if (weaponType == WeaponType.UNARMED) return // Cannot remove fists

        // If the weapon being removed is currently equipped, switch to unarmed first
        if (equippedWeapon == weaponType) {
            equipWeapon(WeaponType.UNARMED)
        }

        val ammoInMag = currentMagazineCounts.getOrDefault(weaponType, 0)
        val ammoInReserve = ammoReserves.getOrDefault(weaponType, 0)
        val totalAmmoLost = ammoInMag + ammoInReserve

        // Send a notification with a NEGATIVE amount
        sceneManager.game.uiManager.queueInventoryChangeNotification(weaponType, -totalAmmoLost)

        weaponInstances.removeAll { it.weaponType == weaponType }

        ammoReserves.remove(weaponType)
        currentMagazineCounts.remove(weaponType)
    }

    fun switchToNextWeapon() {
        if (isDead) return

        if (sceneManager.game.missionSystem.activeModifiers?.disableWeaponSwitching == true) {
            println("Weapon switching is disabled for this mission.")
            return // Exit the function immediately, preventing the switch.
        }

        if (weaponInstances.size <= 1) return // Can't switch if you only have fists

        // SAVE the current weapon's magazine state BEFORE switching
        currentMagazineCounts[equippedWeapon] = currentMagazineCount

        currentWeaponIndex = (currentWeaponIndex + 1) % weaponInstances.size
        val nextInstance = weaponInstances[currentWeaponIndex]
        equipWeapon(nextInstance.weaponType, soundVariationId = nextInstance.soundVariationId)
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

        // Trigger the notification from here with the correct amount.
        sceneManager.game.uiManager.queueInventoryChangeNotification(weaponType, amount)

        println("Added $amount ammo for ${weaponType.displayName}. Total reserve: ${ammoReserves[weaponType]}")

        // Make sure the weapon is in the player's inventory list if they get ammo for it
        if (weaponType != WeaponType.UNARMED && !hasWeapon(weaponType)) {
            val newSoundId = if (weaponType.soundVariations > 0 && weaponType.soundId != null) {
                val randomVariation = Random.nextInt(1, weaponType.soundVariations + 1)
                "${weaponType.soundId}_V$randomVariation"
            } else {
                null
            }
            weaponInstances.add(PlayerWeaponInstance(weaponType = weaponType, soundVariationId = newSoundId))
            println("Player did not have ${weaponType.displayName}, added it to inventory.")
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
            val soundIdToPlay = if (car.carType == CarType.POLICE_CAR) {
                "POLICE_CAR_LOCKED"
            } else {
                car.assignedLockedSoundId ?: run {
                    lockedCarSounds.random().also { car.assignedLockedSoundId = it }
                }
            }
            // Play a procedural "locked door" sound at the car's position.
            sceneManager.game.soundManager.playSound(id = soundIdToPlay, position = car.position, reverbProfile = SoundManager.DEFAULT_REVERB)
            return // Stop the function here, player cannot enter.
        }

        val seat = car.addOccupant(this)
        if (seat != null) {
            // Play the assigned open sound, with a fallback to a new random sound
            val soundIdToPlay = car.assignedOpenSoundId ?: run {
                val openSounds = listOf("CAR_DOOR_OPEN_V1", "CAR_DOOR_OPEN_V2", "CAR_DOOR_OPEN_V3")
                openSounds.random().also { car.assignedOpenSoundId = it }
            }
            sceneManager.game.soundManager.playSound(id = soundIdToPlay, position = car.position)

            if (car.drivingSoundId == null) {
                car.enginePitch = 0.7f // Reset pitch to idle
                car.drivingSoundId = sceneManager.game.soundManager.playSound(
                    id = "CAR_DRIVING_LOOP",
                    position = car.position,
                    loop = true,
                    volumeMultiplier = 0.0f // Start silent!
                )

                // Tell the sound manager to fade it in over 1.2 seconds
                car.drivingSoundId?.let {
                    sceneManager.game.soundManager.rampUpLoopingSoundVolume(it, 2f)
                }
            }

            isDriving = true
            drivingCar = car
            currentSeat = seat
            car.modelInstance.userData = "car"

            sceneManager.game.missionSystem.playerEnteredCar(car.id)
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

        // Play the assigned close sound, with a fallback to a new random sound
        val soundIdToPlay = car.assignedCloseSoundId ?: run {
            val closeSounds = listOf("CAR_DOOR_CLOSE_V1", "CAR_DOOR_CLOSE_V2", "CAR_DOOR_CLOSE_V3", "CAR_DOOR_CLOSE_V4")
            closeSounds.random().also { car.assignedCloseSoundId = it }
        }
        sceneManager.game.soundManager.playSound(id = soundIdToPlay, position = car.position)

        car.drivingSoundId?.let {
            sceneManager.game.soundManager.stopLoopingSound(it)
            car.drivingSoundId = null
        }

        sceneManager.game.missionSystem.playerExitedCar(car.id)

        // Remove player from the car's occupant list
        car.removeOccupant(this)
        car.headlightLight?.let { it.intensity = 0f; it.updatePointLight() }

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
        val fallbackY = if (sceneManager.game.isEditorMode) 0f else -1000f

        // Find the highest block/surface that the player can stand on at position (x, z)
        var highestSupportY = fallbackY // Ground level
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

        // CHANGED: Use the fallbackY in the final return statement
        val targetY = highestSupportY + playerSize.y / 2f + 0.05f
        return if (foundSupport) targetY else (fallbackY + playerSize.y / 2f)
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
        if (isDead) return false

        return if (isDriving) {
            handleCarMovement(deltaTime, sceneManager, allCars)
        } else {
            handlePlayerOnFootMovement(deltaTime, sceneManager, particleSystem)
        }
    }

    private fun handleCarMovement(deltaTime: Float, sceneManager: SceneManager, allCars: Array<GameCar>): Boolean {
        val car = drivingCar ?: return false

        car.updateHeadlight(deltaTime)

        if (car.isDestroyed) {
            car.drivingSoundId?.let {
                sceneManager.game.soundManager.stopLoopingSound(it)
                car.drivingSoundId = null
            }
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

        val isAccelerating = deltaX != 0f || deltaZ != 0f
        val targetPitch = if (isAccelerating) 1.2f else 0.7f
        // Smoothly interpolate the pitch for a nice "vroom" effect
        car.enginePitch = Interpolation.linear.apply(car.enginePitch, targetPitch, deltaTime * 2.5f)

        // Update the sound position and pitch
        car.drivingSoundId?.let {
            sceneManager.game.soundManager.updateLoopingSoundPosition(it, car.position)
            sceneManager.game.soundManager.setLoopingSoundPitch(it, car.enginePitch)
        }

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

    private fun checkPuddleCollision(sceneManager: SceneManager) {
        if (isDriving || isDead) return // No checks needed if in a car or dead

        var isTouchingPuddle = false
        val playerBounds = getPlayerBounds()

        for (puddle in waterPuddleSystem.activePuddles) {
            // Use bounding box intersection for a more accurate check
            if (playerBounds.intersects(puddle.bounds)) {
                isTouchingPuddle = true
                break // Found a collision, no need to check other puddles
            }
        }

        if (isTouchingPuddle) {
            // If we are just now stepping into a puddle, play the sound
            if (wetFootprintsTimer <= 0f) {
                // Play a random file-based water splash sound
                waterSplashSoundIds.randomOrNull()?.let { soundId ->
                    sceneManager.game.soundManager.playSound(
                        id = soundId,
                        position = getPosition(),
                        reverbProfile = SoundManager.DEFAULT_REVERB,
                        maxRange = 30f, // Splashes are not very loud
                        volumeMultiplier = 0.7f // Make them slightly quieter
                    )
                }
            }
            // If touching a puddle, refresh the timer to its maximum duration
            wetFootprintsTimer = WET_FOOTPRINT_COOLDOWN
        }
    }

    private fun handlePlayerOnFootMovement(deltaTime: Float, sceneManager: SceneManager, particleSystem: ParticleSystem): Boolean {
        // Disable on-foot movement and collision while driving
        if (isDriving) return false

        // Apply speed multiplier from mission modifier
        val speedMultiplier = sceneManager.game.missionSystem.activeModifiers?.playerSpeedMultiplier ?: 1.0f
        physicsComponent.speed = basePlayerSpeed * speedMultiplier

        // 1. Calculate desired horizontal movement from raw input
        val desiredMovement = Vector3()
        if (Gdx.input.isKeyPressed(Input.Keys.A)) desiredMovement.x -= 1f
        if (Gdx.input.isKeyPressed(Input.Keys.D)) desiredMovement.x += 1f

        isPressingW = Gdx.input.isKeyPressed(Input.Keys.W)
        if (isPressingW) desiredMovement.z -= 1f
        if (Gdx.input.isKeyPressed(Input.Keys.S)) desiredMovement.z += 1f

        // 2. Update rotation based on the raw input
        if (desiredMovement.x != 0f) {
            lastMovementDirection = desiredMovement.x
        }
        playerTargetRotationY = if (lastMovementDirection < 0f) 180f else 0f
        updatePlayerRotation(deltaTime)

        // 3. NOW, check if movement should be cancelled due to a heavy weapon.
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT) && !equippedWeapon.allowsMovementWhileShooting) {
            desiredMovement.set(0f, 0f, 0f) // Cancel the actual movement vector
        }

        // 4. Pass the (potentially zeroed) movement vector to the physics system
        val moved = characterPhysicsSystem.update(physicsComponent, desiredMovement, deltaTime)

        // 5. The rest of the logic for animations and effects remains the same.
        isMoving = physicsComponent.isMoving

        if (isMoving && !lastIsMoving) animationSystem.playAnimation("walking")
        else if (!isMoving && lastIsMoving) animationSystem.playAnimation("idle")
        lastIsMoving = isMoving

        // 6. Resolve Y-axis movement (Gravity and Grounding) with MULTI-POINT CHECK
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
                        wipeRotation = if (desiredMovement.z < 0) 90f else 270f  // W key is negative Z

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

            footprintSpawnTimer += deltaTime
            if (footprintSpawnTimer >= FOOTPRINT_SPAWN_INTERVAL) {
                footprintSpawnTimer = 0f // Reset for the next print

                // Play a footstep sound each time a footprint is due
                footstepSoundIds.randomOrNull()?.let { soundId ->
                    sceneManager.game.soundManager.playSound(
                        id = soundId,
                        position = getPosition(),
                        reverbProfile = SoundManager.DEFAULT_REVERB,
                        maxRange = 25f,
                        volumeMultiplier = 0.4f // Make footsteps a bit quieter
                    )
                }

                // Find the ground position directly below the player
                val groundY = sceneManager.findHighestSupportY(physicsComponent.position.x, physicsComponent.position.z, physicsComponent.position.y, physicsComponent.size.x / 2f, blockSize)
                val footprintPosition = Vector3(physicsComponent.position.x, groundY, physicsComponent.position.z)
                val movementRotation = MathUtils.atan2(-desiredMovement.x, -desiredMovement.z) * MathUtils.radiansToDegrees + 90f

                // Check if the player is currently standing in a puddle
                val isInPuddle = waterPuddleSystem.isPositionInPuddle(footprintPosition)

                // Priority 1: Bloody footprints override wet ones.
                if (bloodyFootprintsTimer > 0f) {
                    // Spawn the footprint using the new system
                    footprintSystem.spawnFootprint(footprintPosition, movementRotation, FootprintType.BLOODY, sceneManager)
                }
                // Priority 2: Wet footprints.
                else if (wetFootprintsTimer > 0f) {
                    if (isInPuddle) {
                        // If we are currently in a puddle, always spawn a normal wet print.
                        footprintSystem.spawnFootprint(footprintPosition, movementRotation, FootprintType.WET, sceneManager)
                    } else {
                        // If we are OUTSIDE a puddle, but our feet are still wet, there's a CHANCE to leave a short-lived print.
                        if (Random.nextFloat() < 0.15f) { // 15% chance
                            val shortLifetime = Random.nextFloat() * 2f + 1f // Random lifetime between 1 and 3 seconds
                            footprintSystem.spawnFootprint(footprintPosition, movementRotation, FootprintType.WET, sceneManager, lifetimeOverride = shortLifetime)
                        }
                    }
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

            var highestBlockY = sceneManager.findHighestSupportY(gridX, gridZ, 1000f, 0.1f, blockSize)
            if (highestBlockY < -500f) { // Check for fallback
                highestBlockY = 0f
            }

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

        // Play the teleport sound at the destination
        sceneManager.game.soundManager.playSound(
            effect = SoundManager.Effect.TELEPORT,
            position = finalDestination,
            reverbProfile = SoundManager.DEFAULT_REVERB
        )

        return true // Teleport was successful
    }

    fun getMoney(): Int = money

    fun addMoney(amount: Int) {
        if (amount == 0) return

        money += amount
        println("Player money changed by $amount. Total: $money")

        val message = if (amount > 0) "+$$amount" else "-$$${-amount}"
        val color = if (amount > 0) Color.GREEN else Color.RED

        // Show the floating text at the player's position
        sceneManager.game.uiManager.showFloatingText(message, color, getPosition())

        // Keep the existing HUD update
        sceneManager.game.uiManager.showMoneyUpdate(money)
    }

    fun setOnFire(duration: Float, dps: Float) {
        if (isOnFire) return // Already on fire
        isOnFire = true
        onFireTimer = duration
        initialOnFireDuration = duration
        onFireDamagePerSecond = dps
    }

    fun applyKnockback(force: Vector3) {
        if (!isDriving) {
            physicsComponent.knockbackVelocity.set(force)
        }
    }

    fun update(deltaTime: Float, sceneManager: SceneManager, weatherSystem: WeatherSystem, isInInterior: Boolean) {

        // HANDLE ON FIRE STATE (DAMAGE & VISUALS)
        if (this.isOnFire) {
            this.onFireTimer -= deltaTime

            // RAIN EXTINGUISH
            val visualRainIntensity = weatherSystem.getVisualRainIntensity()
            if (visualRainIntensity > 0.1f && !isInInterior && !isDriving) {
                // If it's raining and the player is outside and not in a car...

                // 1. Make the fire burn out faster based on rain intensity.
                val extinguishRate = 3.0f * visualRainIntensity
                this.onFireTimer -= extinguishRate * deltaTime
                println("Player is on fire in the rain! Extinguishing...")

                // 2. Spawn sizzle/steam particles for visual feedback.
                if (Random.nextFloat() < 0.3f) { // 30% chance per frame to spawn steam
                    val steamPosition = this.getPosition().add(
                        (Random.nextFloat() - 0.5f) * (this.playerSize.x * 0.8f),
                        (Random.nextFloat()) * (this.playerSize.y * 0.8f),
                        0f
                    )
                    this.particleSystem.spawnEffect(ParticleEffectType.SMOKE_FRAME_1, steamPosition)
                }
            }

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
        if (!isDriving && !isDead) { // Only check when on foot and alive
            checkPuddleCollision(sceneManager)
            if (wetFootprintsTimer > 0f) {
                wetFootprintsTimer -= deltaTime
            }
        }

        if (!isDriving && !isDead) { // Only check when on foot
            checkBloodPoolCollision(sceneManager)
            if (bloodyFootprintsTimer > 0f) {
                bloodyFootprintsTimer -= deltaTime
            }
        }
        handleWeaponInput(deltaTime, sceneManager)

        automaticFireSoundId?.let { soundId ->
            sceneManager.game.soundManager.updateLoopingSoundPosition(soundId, getPosition())
        }

        // Player interaction with ash
        if (isMoving && !isDriving) {
            val shrinkAmount = 2.0f * deltaTime // How much scale to remove per second of contact
            val removalThreshold = 0.3f
            val playerBounds = getPlayerBounds() // Use bounding box for better collision

            // Use the particleSystem directly, which is a member of PlayerSystem
            val ashIterator = particleSystem.getActiveParticles().iterator()
            while (ashIterator.hasNext()) {
                val particle = ashIterator.next()
                if (particle.type == ParticleEffectType.BURNED_ASH) {
                    // Create a simple bounding box for the ash particle (it's a flat plane)
                    val ashBounds = BoundingBox(
                        particle.position.cpy().sub(particle.scale / 2f, 0f, particle.scale / 2f),
                        particle.position.cpy().add(particle.scale / 2f, 0.5f, particle.scale / 2f) // Give it a little height
                    )

                    if (playerBounds.intersects(ashBounds)) {
                        particle.scale -= shrinkAmount
                        if (particle.scale < removalThreshold) {
                            particle.life = 0f // Mark for removal
                        }
                    }
                }
            }
        }

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

            // 1. Check if the bullet is old enough
            if (bullet.age > 0.05f) {
                // 2. Define the desired length of the trail.
                val trailLength = 3.0f

                // 3. Get the bullet's current position and direction.
                val bulletCurrentPos = bullet.position
                val bulletDirection = bullet.velocity.cpy().nor()

                // 4. Calculate the trail's start point
                val trailStartPos = bulletCurrentPos.cpy().mulAdd(bulletDirection, -trailLength)

                // 5. Apply the small offset to both start and end
                val offset = bulletDirection.cpy().scl(-0.4f)

                // 6. Add the trail to the system.
                bulletTrailSystem.addTrail(
                    trailStartPos.add(offset),
                    bulletCurrentPos.cpy().add(offset),
                    sceneManager.getCurrentSceneId()
                )
            }

            // Collision Check
            val collisionResult = checkBulletCollision(bullet, sceneManager)

            // Check if ANY valid object was hit
            if (collisionResult != null) {
                // Destroy bullet
                bulletIterator.remove()

                // Calculate the spawn position
                val particleSpawnPos = collisionResult.hitPoint.cpy().mulAdd(collisionResult.surfaceNormal, PARTICLE_IMPACT_OFFSET)

                val bulletHoleType = when (bullet.owner) {
                    is PlayerSystem -> ParticleEffectType.BULLET_HOLE_PLAYER
                    is GameEnemy -> ParticleEffectType.BULLET_HOLE_ENEMY
                    else -> null // Don't spawn a hole for other types
                }

                when (collisionResult.type) {
                    HitObjectType.BLOCK -> {
                        // Spawn dust/sparks for static objects
                        particleSystem.spawnEffect(ParticleEffectType.DUST_SMOKE_MEDIUM, collisionResult.hitPoint)

                        if (bulletHoleType != null) {
                            val bulletHoleOffset = 0.02f // Very small offset to prevent z-fighting

                            // Calculate the decal position with minimal offset
                            val decalPosition = collisionResult.hitPoint.cpy().mulAdd(collisionResult.surfaceNormal, bulletHoleOffset)

                            particleSystem.spawnEffect(
                                type = bulletHoleType,
                                position = decalPosition,
                                surfaceNormal = collisionResult.surfaceNormal, // Pass the normal to align the decal
                                gravityOverride = 0f
                            )
                        }
                    }
                    HitObjectType.INTERIOR -> {
                        // Spawn dust/sparks for static objects
                        particleSystem.spawnEffect(ParticleEffectType.DUST_SMOKE_MEDIUM, particleSpawnPos)
                    }
                    HitObjectType.OBJECT -> {
                        val gameObject = collisionResult.hitObject as GameObject
                        if (gameObject.takeDamage(bullet.damage)) {
                            if (gameObject.objectType == ObjectType.LANTERN) {
                                sceneManager.game.soundManager.playSound(
                                    effect = SoundManager.Effect.GLASS_BREAK,
                                    position = gameObject.position,
                                    reverbProfile = SoundManager.DEFAULT_REVERB
                                )
                            }

                            // Object was destroyed. REPORT IT!
                            sceneManager.game.missionSystem.reportObjectDestroyed(gameObject.id)

                            val replacementType = gameObject.objectType.destroyedObjectType
                            if (replacementType != null) {
                                // 1. Remove the old object's light source (if any)
                                sceneManager.game.objectSystem.removeGameObjectWithLight(gameObject, sceneManager.game.lightingManager)

                                // 2. Create the new "broken" object at the same position
                                val newObject = sceneManager.game.objectSystem.createGameObjectWithLight(replacementType, gameObject.position.cpy())

                                // 3. Remove the old object from the active list
                                sceneManager.activeObjects.removeValue(gameObject, true)

                                // 4. Add the new object to the active list (if creation was successful)
                                if (newObject != null) {
                                    sceneManager.activeObjects.add(newObject)
                                    println("Replaced ${gameObject.objectType.displayName} with ${newObject.objectType.displayName}.")
                                }
                            } else {
                                // It's destructible but has no replacement, so just remove it.
                                sceneManager.activeObjects.removeValue(gameObject, true)
                                println("${gameObject.objectType.displayName} was destroyed and removed.")
                            }

                            // Spawn a bigger effect for destroyed objects
                            particleSystem.spawnEffect(ParticleEffectType.DUST_SMOKE_HEAVY, gameObject.position)
                        } else {
                            // Object was hit but not destroyed, spawn a smaller effect
                            particleSystem.spawnEffect(ParticleEffectType.DUST_SMOKE_MEDIUM, particleSpawnPos)
                        }
                    }
                    HitObjectType.CAR -> {
                        val car = collisionResult.hitObject as GameCar
                        car.takeDamage(equippedWeapon.damage, DamageType.GENERIC)
                        particleSystem.spawnEffect(ParticleEffectType.DUST_SMOKE_MEDIUM, particleSpawnPos)
                    }

                    HitObjectType.HOUSE -> {
                        // Intentionally do nothing
                    }

                    HitObjectType.ENEMY -> {
                        val enemy = collisionResult.hitObject as GameEnemy
                        val weaponUsed = (bullet.owner as? PlayerSystem)?.equippedWeapon
                        val modifiers = sceneManager.game.missionSystem.activeModifiers

                        val damageToDeal = if (modifiers?.playerHasOneHitKills == true) {
                            enemy.health // Deal exactly enough damage to kill
                        } else {
                            bullet.damage // Deal normal bullet damage
                        }

                        // Use the enemy's center
                        val bloodSpawnPosition = enemy.position.cpy()
                        val violenceLevel = sceneManager.game.uiManager.getViolenceLevel()

                        // small random offset to make it look less robotic
                        val offsetX = (Random.nextFloat() - 0.5f) * (enemy.enemyType.width * 0.4f)
                        val offsetY = (Random.nextFloat() - 0.5f) * (enemy.enemyType.height * 0.4f)
                        bloodSpawnPosition.add(offsetX, offsetY, 0f) // No Z offset for 2D characters

                        // 50% chance to spawn blood effects
                        val shouldSpawnBlood = when (violenceLevel) {
                            ViolenceLevel.ULTRA_VIOLENCE -> true // 100% chance
                            ViolenceLevel.FULL_VIOLENCE -> Random.nextFloat() < 0.75f // 75% chance
                            ViolenceLevel.REDUCED_VIOLENCE -> Random.nextFloat() < 0.25f // 25% chance
                            else -> false // No violence = 0% chance
                        }

                        if (shouldSpawnBlood) {
                            spawnBloodEffects(bloodSpawnPosition, sceneManager)
                        }

                        if (enemy.takeDamage(damageToDeal, DamageType.GENERIC, sceneManager, this) && enemy.currentState != AIState.DYING) {
                            sceneManager.enemySystem.startDeathSequence(enemy, sceneManager)
                        }
                        // APPLY KNOCKBACK
                        if (weaponUsed != null && weaponUsed.knockbackForce > 0 && !enemy.isInCar) {
                            val willDie = enemy.health <= damageToDeal

                            val knockbackDirection = bullet.velocity.cpy().nor()
                            val forcePerPellet = weaponUsed.knockbackForce / weaponUsed.pelletCount.coerceAtLeast(1)

                            // Add ragdoll effect for fatal shots
                            val ragdollMultiplier = if (willDie) 1.3f else 1.0f
                            val knockbackVector = knockbackDirection.scl(forcePerPellet * ragdollMultiplier)

                            // Apply directly to physics to work even on dying enemies
                            enemy.physics.knockbackVelocity.set(knockbackVector)
                        }
                    }
                    HitObjectType.NPC -> {
                        val npc = collisionResult.hitObject as GameNPC
                        val weaponUsed = (bullet.owner as? PlayerSystem)?.equippedWeapon
                        val modifiers = sceneManager.game.missionSystem.activeModifiers

                        val damageToDeal = if (modifiers?.playerHasOneHitKills == true) {
                            npc.health
                        } else {
                            bullet.damage
                        }

                        // Use the NPC's center
                        val bloodSpawnPosition = npc.position.cpy()

                        // small random offset
                        val offsetX = (Random.nextFloat() - 0.5f) * (npc.npcType.width * 0.4f)
                        val offsetY = (Random.nextFloat() - 0.5f) * (npc.npcType.height * 0.4f)
                        bloodSpawnPosition.add(offsetX, offsetY, 0f)

                        // 50% chance to spawn blood effects
                        val violenceLevel = sceneManager.game.uiManager.getViolenceLevel()

                        if (violenceLevel == ViolenceLevel.ULTRA_VIOLENCE || Random.nextFloat() < 0.5f) {
                            spawnBloodEffects(bloodSpawnPosition, sceneManager)
                        }

                        if (npc.takeDamage(damageToDeal, DamageType.GENERIC, sceneManager) && npc.currentState != NPCState.DYING) {
                            // NPC died, remove it from the scene
                            sceneManager.npcSystem.startDeathSequence(npc, sceneManager)
                        }

                        // APPLY KNOCKBACK
                        if (weaponUsed != null && weaponUsed.knockbackForce > 0 && !npc.isInCar) {
                            val willDie = npc.health <= damageToDeal

                            val knockbackDirection = bullet.velocity.cpy().nor()
                            val forcePerPellet = weaponUsed.knockbackForce / weaponUsed.pelletCount.coerceAtLeast(1)

                            val ragdollMultiplier = if (willDie) 1.3f else 1.0f
                            val knockbackVector = knockbackDirection.scl(forcePerPellet * ragdollMultiplier)

                            npc.physics.knockbackVelocity.set(knockbackVector)
                        }
                    }
                    HitObjectType.PLAYER -> {
                        val enemyOwner = bullet.owner as GameEnemy
                        takeDamage(enemyOwner.equippedWeapon.damage)
                        // You could add a blood effect for the player here if you wish
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
            // If we are just now stepping into blood, play the sound
            if (bloodyFootprintsTimer <= 0f) {
                // Play the procedural blood squish sound
                sceneManager.game.soundManager.playSound(
                    effect = SoundManager.Effect.BLOOD_SQUISH,
                    position = getPosition(),
                    reverbProfile = SoundManager.DEFAULT_REVERB
                )
            }
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

                val dynamiteReverb = SoundManager.ReverbProfile(
                    numEchoes = 4,           // More echoes
                    delayStep = 0.12f,       // Longer delay between echoes
                    volumeFalloff = 0.7f     // Slower volume decay
                )

                sceneManager.game.soundManager.playSound(
                    id = "EXPLOSION_HIGH",
                    position = explosionOrigin,
                    reverbProfile = dynamiteReverb, // Use our custom profile
                    pitch = 0.85f,                  // Play at 85% pitch to stretch it
                    maxRange = 120f
                )

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
                    // 3. Spawn the projected scorch mark decal on the ground.
                    val decalTexture = particleSystem.getTextureForEffect(ParticleEffectType.DYNAMITE_EXPLOSION_AREA_ONE) // A way to get the texture
                    if (decalTexture != null) {
                        val decalSize = Vector3(17f, 10f, 17f) // Width, Projection Depth, Length
                        sceneManager.game.decalSystem.spawnProjectedDecal(explosionOrigin, decalSize, decalTexture, 25f)
                    }
                }

                println("Dynamite effect originating at $explosionOrigin")

                val modifiers = sceneManager.game.missionSystem.activeModifiers
                // For AoE, we can't get each target's health. So we set a "super high" damage value.
                val baseDamageToDeal = if (modifiers?.playerHasOneHitKills == true) {
                    9999f // A number high enough to kill anything instantly
                } else {
                    throwable.weaponType.damage // Normal dynamite damage
                }

                // Area-of-effect damage logic
                val explosionRadius = 12f
                val maxKnockbackForce = 65.0f

                val closeExplosionRadius = 4f
                val baseUpwardLift = 1.2f
                val closeUpwardLift = 2.5f

                val explosionRadiusSquared = explosionRadius * explosionRadius

                // Damage cars
                for (car in sceneManager.activeCars) {
                    val distanceToCar = car.position.dst(validGroundPosition)
                    if (distanceToCar < explosionRadius) {
                        val actualDamage = calculateFalloffDamage(baseDamageToDeal, distanceToCar, explosionRadius)
                        car.takeDamage(actualDamage, DamageType.EXPLOSIVE)
                    }
                }

                // Damage enemies
                sceneManager.activeEnemies.forEach { enemy ->
                    val distanceToEnemy = enemy.position.dst(validGroundPosition)
                    if (distanceToEnemy < explosionRadius) {
                        // Apply Damage
                        val actualDamage = calculateFalloffDamage(baseDamageToDeal, distanceToEnemy, explosionRadius)
                        val willDie = enemy.health <= actualDamage

                        // Apply Damage
                        if (enemy.takeDamage(actualDamage, DamageType.EXPLOSIVE, sceneManager, this) && enemy.currentState != AIState.DYING) {
                            sceneManager.enemySystem.startDeathSequence(enemy, sceneManager)
                        }

                        // Apply Knockback
                        val knockbackStrength = maxKnockbackForce * (1.0f - (distanceToEnemy / explosionRadius))
                        if (knockbackStrength > 0 && !enemy.isInCar) {
                            val finalUpwardLift = if (distanceToEnemy <= closeExplosionRadius) closeUpwardLift else baseUpwardLift

                            // Add extra upward force for dying enemies to make them "ragdoll"
                            val ragdollBonus = if (willDie && distanceToEnemy <= closeExplosionRadius) 1.5f else 1.0f

                            val knockbackDirection = enemy.position.cpy().sub(explosionOrigin).apply {
                                y = finalUpwardLift * ragdollBonus
                            }.nor()

                            val knockbackVector = knockbackDirection.scl(knockbackStrength)

                            // Apply knockback directly to physics component to bypass state checks
                            enemy.physics.knockbackVelocity.set(knockbackVector)

                            println("Knocked ${enemy.enemyType.displayName} with force: ${knockbackVector.len()} (${if(willDie) "FATAL" else "survived"})")
                        }
                    }
                }

                // Damage NPCs
                sceneManager.activeNPCs.forEach { npc ->
                    val distanceToNPC = npc.position.dst(validGroundPosition)
                    if (distanceToNPC < explosionRadius) {
                        // Apply Damage
                        val actualDamage = calculateFalloffDamage(baseDamageToDeal, distanceToNPC, explosionRadius)
                        val willDie = npc.health <= actualDamage

                        // Apply Damage
                        if (npc.takeDamage(actualDamage, DamageType.EXPLOSIVE, sceneManager) && npc.currentState != NPCState.DYING) {
                            sceneManager.npcSystem.startDeathSequence(npc, sceneManager)
                        }

                        // Apply Knockback
                        val knockbackStrength = maxKnockbackForce * (1.0f - (distanceToNPC / explosionRadius))
                        if (knockbackStrength > 0 && !npc.isInCar) {
                            val finalUpwardLift = if (distanceToNPC <= closeExplosionRadius) closeUpwardLift else baseUpwardLift
                            val ragdollBonus = if (willDie && distanceToNPC <= closeExplosionRadius) 1.5f else 1.0f

                            val knockbackDirection = npc.position.cpy().sub(explosionOrigin).apply {
                                y = finalUpwardLift * ragdollBonus
                            }.nor()

                            val knockbackVector = knockbackDirection.scl(knockbackStrength)
                            npc.physics.knockbackVelocity.set(knockbackVector)

                            println("Knocked ${npc.npcType.displayName} with force: ${knockbackVector.len()} (${if(willDie) "FATAL" else "survived"})")
                        }
                    }
                }

                // Damage Player
                val distanceToPlayerSelf = getPosition().dst(validGroundPosition)
                if (distanceToPlayerSelf < explosionRadius) {
                    val actualDamage = calculateFalloffDamage(baseDamageToDeal, distanceToPlayerSelf, explosionRadius)
                    takeDamage(actualDamage)

                    // Calculate the knockback strength based on distance
                    val knockbackStrength = maxKnockbackForce * (1.0f - (distanceToPlayerSelf / explosionRadius))

                    if (knockbackStrength > 0) {
                        // Calculate the direction vector
                        val finalUpwardLift = if (distanceToPlayerSelf <= closeExplosionRadius) closeUpwardLift else baseUpwardLift
                        val knockbackDirection = getPosition().sub(explosionOrigin)
                        knockbackDirection.y = finalUpwardLift // Apply the conditional lift

                        if (knockbackDirection.len2() > 0.001f) {
                            knockbackDirection.nor()

                            // Create the final knockback vector and apply it
                            val knockbackVector = knockbackDirection.scl(knockbackStrength)
                            applyKnockback(knockbackVector)

                            println("Player knocked back with velocity: $knockbackVector")
                        }
                    }
                }

                sceneManager.activeObjects.forEach { obj ->
                    if (obj.objectType.isDestructible) {
                        val distanceToObj = obj.position.dst(validGroundPosition)
                        if (distanceToObj < explosionRadius) {
                            val actualDamage = calculateFalloffDamage(baseDamageToDeal, distanceToObj, explosionRadius)
                            if (obj.takeDamage(actualDamage)) {
                                // Object was destroyed by the explosion. REPORT IT!
                                sceneManager.game.missionSystem.reportObjectDestroyed(obj.id)

                                val replacementType = obj.objectType.destroyedObjectType
                                if (replacementType != null) {
                                    // Replace it
                                    sceneManager.game.objectSystem.removeGameObjectWithLight(obj, sceneManager.game.lightingManager)
                                    val newObject = sceneManager.game.objectSystem.createGameObjectWithLight(replacementType, obj.position.cpy())
                                    sceneManager.activeObjects.removeValue(obj, true)
                                    if (newObject != null) {
                                        sceneManager.activeObjects.add(newObject)
                                        println("Replaced ${obj.objectType.displayName} with ${newObject.objectType.displayName} via explosion.")
                                    }
                                } else {
                                    // Remove it
                                    sceneManager.activeObjects.removeValue(obj, true)
                                    println("${obj.objectType.displayName} was destroyed by explosion and removed.")
                                }
                                particleSystem.spawnEffect(ParticleEffectType.DUST_SMOKE_HEAVY, obj.position)
                            }
                        }
                    }
                }

                // Affect Ash
                val ashIterator = particleSystem.getActiveParticles().iterator()
                while (ashIterator.hasNext()) {
                    val particle = ashIterator.next()
                    if (particle.type == ParticleEffectType.BURNED_ASH) {
                        if (particle.position.dst2(validGroundPosition) < explosionRadiusSquared) {
                            // Define "big" ash threshold
                            val bigAshThreshold = 1.8f // BURNED_ASH default scale is 2.0
                            if (particle.scale > bigAshThreshold) {
                                // Shrink big ash to be very small
                                particle.scale = 0.5f
                                println("Shrank big ash particle due to explosion.")
                            } else {
                                // Small ash is removed completely
                                particle.life = 0f // Mark for removal
                                println("Removed small ash particle due to explosion.")
                            }
                        }
                    }
                }

                // Affect Bones
                val boneIterator = sceneManager.activeBones.iterator()
                while (boneIterator.hasNext()) {
                    val bone = boneIterator.next()
                    if (bone.position.dst2(validGroundPosition) < explosionRadiusSquared) {
                        boneIterator.remove()
                        println("Destroyed bone due to explosion.")
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

        val currentTexture = animationSystem.getCurrentTexture() ?: playerTexture
        val currentWidth = currentTexture.width.toFloat()
        val currentHeight = currentTexture.height.toFloat()

        // Only apply scaling if the dimensions are valid and different from the base
        if (baseTextureWidth > 0 && baseTextureHeight > 0 && currentWidth > 0 && currentHeight > 0) {
            val baseAspect = baseTextureWidth / baseTextureHeight
            val currentAspect = currentWidth / currentHeight

            val scaleX = currentAspect / baseAspect
            playerInstance.transform.scale(scaleX, 1f, 1f)
        }

        // Get the offset for the current frame
        val frameOffsetX = animationSystem.getCurrentFrameOffsetX()

        // Determine the direction the player is facing
        val facingDirection = if (playerCurrentRotationY == 180f) -1.0f else 1.0f

        playerInstance.transform.translate(frameOffsetX * facingDirection, 0f, 0f)

        // Apply Y-axis rotation for Paper Mario effect
        playerInstance.transform.rotate(Vector3.Y, playerCurrentRotationY)
    }

    fun render(camera: Camera, environment: Environment) {
        updatePlayerTransform()

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
        weaponInstances.clear()
        weaponInstances.add(PlayerWeaponInstance(weaponType = WeaponType.UNARMED, soundVariationId = null))

        data.weapons.keys().forEach { weaponType ->
            if (weaponType != WeaponType.UNARMED) {
                val newSoundId = if (weaponType.soundVariations > 0 && weaponType.soundId != null) {
                    val randomVariation = Random.nextInt(1, weaponType.soundVariations + 1)
                    "${weaponType.soundId}_V$randomVariation"
                } else {
                    null // This weapon type has no sound variations.
                }

                weaponInstances.add(PlayerWeaponInstance(weaponType = weaponType, soundVariationId = newSoundId))
            }
        }

        // Load Magazine Counts
        currentMagazineCounts.clear()
        data.currentMagazineCounts.forEach { entry ->
            currentMagazineCounts[entry.key] = entry.value
        }

        equipWeapon(data.equippedWeapon)

        currentMagazineCount = currentMagazineCounts.getOrDefault(data.equippedWeapon, 0)

        println("Player state loaded. Equipped ${data.equippedWeapon} with $currentMagazineCount rounds in magazine.")
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
