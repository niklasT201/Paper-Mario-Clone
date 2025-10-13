package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox

data class Bullet(
    var position: Vector3,
    val velocity: Vector3,
    val modelInstance: ModelInstance,
    var lifetime: Float,
    val rotationY: Float,
    val owner: Any,
    var damage: Float
) {
    val bounds: BoundingBox = BoundingBox()
    private val bulletSize = 0.2f // A small collision box for the bullet

    init {
        updateBounds()
        updateTransform()
    }

    fun update(deltaTime: Float) {
        position.mulAdd(velocity, deltaTime)
        updateBounds()
        lifetime -= deltaTime

        // Visual update
        updateTransform()
    }

    private fun updateTransform() {
        modelInstance.transform.setToRotation(Vector3.Y, rotationY).setTranslation(position)
    }

    private fun updateBounds() {
        val halfSize = bulletSize / 2f
        bounds.set(
            position.cpy().sub(halfSize),
            position.cpy().add(halfSize)
        )
    }
}

enum class WeaponActionType {
    SHOOTING, // Fires projectiles
    MELEE,    // Close-range attack
    THROWABLE // Thrown weapon
}

enum class WeaponType(
    val displayName: String,
    val actionType: WeaponActionType,
    val fireRatePerSecond: Float, // How many shots per second. 0 for non-shooting
    val damage: Float,
    val meleeRange: Float,
    val magazineSize: Int, // 0 for infinite or non-applicable
    val requiresReload: Boolean,
    val reloadTime: Float,
    val allowsMovementWhileShooting: Boolean,
    val playerPoseTexturePath: String,
    val bulletTexturePath: String?,
    val bulletSpeed: Float = 0f,
    val bulletLifetime: Float = 0f
) {
    UNARMED(
        displayName = "Unarmed",
        actionType = WeaponActionType.MELEE,
        fireRatePerSecond = 2.5f,
        damage = 8f,
        meleeRange = 4f,
        magazineSize = 0,
        requiresReload = false,
        reloadTime = 0.0f,
        allowsMovementWhileShooting = true,
        playerPoseTexturePath = "textures/player/pig_character.png",
        bulletTexturePath = null
    ),
    KNIFE(
        displayName = "Knife",
        actionType = WeaponActionType.MELEE,
        fireRatePerSecond = 2.0f,
        damage = 25f, // High damage, short range
        meleeRange = 4.5f,
        magazineSize = 0,
        requiresReload = false,
        reloadTime = 0.0f,
        allowsMovementWhileShooting = true, // Can move while knifing
        playerPoseTexturePath = "textures/player/weapons/knife/player_knife.png",
        bulletTexturePath = null
    ),
    BASEBALL_BAT(
        displayName = "Baseball Bat",
        actionType = WeaponActionType.MELEE,
        fireRatePerSecond = 1.5f,
        damage = 35f, // Slower but higher damage and longer range than knife
        meleeRange = 7.0f,
        magazineSize = 0,
        requiresReload = false,
        reloadTime = 0.0f,
        allowsMovementWhileShooting = true,
        playerPoseTexturePath = "textures/player/weapons/baseball_bat/player_baseball_bat.png",
        bulletTexturePath = null
    ),
    TOMMY_GUN(
        displayName = "Tommy Gun",
        actionType = WeaponActionType.SHOOTING,
        fireRatePerSecond = 10f,
        damage = 15f,
        meleeRange = 0f,
        magazineSize = 50,
        requiresReload = true,
        reloadTime = 2.8f,
        allowsMovementWhileShooting = true,
        playerPoseTexturePath = "textures/player/weapons/tommy_gun/pig_character_tommy_gun.png",
        bulletTexturePath = "textures/player/weapons/bullet_tile.png",
        bulletSpeed = 150f,
        bulletLifetime = 1.5f
    ),
    LIGHT_TOMMY_GUN(
        displayName = "Light Tommy Gun",
        actionType = WeaponActionType.SHOOTING,
        fireRatePerSecond = 11f,
        damage = 13f,
        meleeRange = 0f,
        magazineSize = 40,
        requiresReload = true,
        reloadTime = 2.5f,
        allowsMovementWhileShooting = false,
        playerPoseTexturePath = "textures/player/weapons/tommy_gun/player_light_tommy_gun.png",
        bulletTexturePath = "textures/player/weapons/bullet_tile.png",
        bulletSpeed = 160f,
        bulletLifetime = 1.4f
    ),
    REVOLVER(
        displayName = "Revolver",
        actionType = WeaponActionType.SHOOTING,
        fireRatePerSecond = 2f,
        damage = 40f, // High damage, slow fire rate
        meleeRange = 0f,
        magazineSize = 6,
        requiresReload = true,
        reloadTime = 1.8f,
        allowsMovementWhileShooting = true,
        playerPoseTexturePath = "textures/player/weapons/revolver/player_revolver.png",
        bulletTexturePath = "textures/player/weapons/bullet_tile.png",
        bulletSpeed = 200f,
        bulletLifetime = 2.0f
    ),
    LIGHT_REVOLVER(
        displayName = "Light Revolver",
        actionType = WeaponActionType.SHOOTING,
        fireRatePerSecond = 2.5f,
        damage = 35f, // Faster, less damage
        meleeRange = 0f,
        magazineSize = 6,
        requiresReload = true,
        reloadTime = 1.6f,
        allowsMovementWhileShooting = true,
        playerPoseTexturePath = "textures/player/weapons/revolver/player_light_revolver.png",
        bulletTexturePath = "textures/player/weapons/bullet_tile.png",
        bulletSpeed = 210f,
        bulletLifetime = 2.0f
    ),
    SMALLER_REVOLVER(
        displayName = "Smaller Revolver",
        actionType = WeaponActionType.SHOOTING,
        fireRatePerSecond = 3f,
        damage = 28f, // Weakest but fastest revolver
        meleeRange = 0f,
        magazineSize = 5,
        requiresReload = true,
        reloadTime = 1.5f,
        allowsMovementWhileShooting = true,
        playerPoseTexturePath = "textures/player/weapons/revolver/player_smaller_revolver.png",
        bulletTexturePath = "textures/player/weapons/bullet_tile.png",
        bulletSpeed = 190f,
        bulletLifetime = 1.8f
    ),
    SHOTGUN(
        displayName = "Shotgun",
        actionType = WeaponActionType.SHOOTING,
        fireRatePerSecond = 1f,
        damage = 90f, // Massive close-range damage, but slow
        meleeRange = 0f,
        magazineSize = 3,
        requiresReload = true,
        reloadTime = 1.5f,
        allowsMovementWhileShooting = false,
        playerPoseTexturePath = "textures/player/weapons/shotgun/player_shotgun.png",
        bulletTexturePath = "textures/player/weapons/bullet_tile.png",
        bulletSpeed = 150f,
        bulletLifetime = 0.5f
    ),
    LIGHT_SHOTGUN(
        displayName = "Light Shotgun",
        actionType = WeaponActionType.SHOOTING,
        fireRatePerSecond = 1.2f,
        damage = 75f,
        meleeRange = 0f,
        magazineSize = 5, // A more reasonable magazine for a "light" version
        requiresReload = true,
        reloadTime = 2.0f,
        allowsMovementWhileShooting = false,
        playerPoseTexturePath = "textures/player/weapons/shotgun/player_light_shotgun.png",
        bulletTexturePath = "textures/player/weapons/bullet_tile.png",
        bulletSpeed = 160f,
        bulletLifetime = 0.6f
    ),
    SMALL_SHOTGUN(
        displayName = "Small Shotgun",
        actionType = WeaponActionType.SHOOTING,
        fireRatePerSecond = 1.5f,
        damage = 55f, // Sawed-off style: fast, wide spread, lower damage
        meleeRange = 0f,
        magazineSize = 2,
        requiresReload = true,
        reloadTime = 1.8f,
        allowsMovementWhileShooting = true, // Light weapon
        playerPoseTexturePath = "textures/player/weapons/shotgun/player_small_shotgun.png",
        bulletTexturePath = "textures/player/weapons/bullet_tile.png",
        bulletSpeed = 140f,
        bulletLifetime = 0.4f
    ),
    MACHINE_GUN(
        displayName = "Machine Gun",
        actionType = WeaponActionType.SHOOTING,
        fireRatePerSecond = 8f, // Slower than a Tommy Gun, more deliberate
        damage = 22f, // Higher damage per bullet (176 DPS)
        meleeRange = 0f,
        magazineSize = 100,
        requiresReload = true,
        reloadTime = 3.5f,
        allowsMovementWhileShooting = false, // Heavy weapon
        playerPoseTexturePath = "textures/player/weapons/machine_gun/pig_character_machine_gun.png",
        bulletTexturePath = "textures/player/weapons/bullet_tile.png",
        bulletSpeed = 180f,
        bulletLifetime = 1.8f
    ),
    MOLOTOV(
        displayName = "Molotov",
        actionType = WeaponActionType.THROWABLE,
        fireRatePerSecond = 0.5f,
        damage = 10f,
        meleeRange = 0f,
        magazineSize = 0,
        requiresReload = false,
        reloadTime = 0.0f,
        allowsMovementWhileShooting = true, // Can move while throwing
        playerPoseTexturePath = "textures/player/weapons/molotov/player_molotov.png",
        bulletTexturePath = null
    ),
    DYNAMITE(
        displayName = "Dynamite",
        actionType = WeaponActionType.THROWABLE,
        fireRatePerSecond = 0.5f,
        damage = 150f,
        meleeRange = 0f,
        magazineSize = 0,
        requiresReload = false,
        reloadTime = 0.0f,
        allowsMovementWhileShooting = true, // Can move while throwing
        playerPoseTexturePath = "textures/player/weapons/dynamite/player_dynamite.png",
        bulletTexturePath = null
    );

    // Calculated property for the cooldown timer
    val fireCooldown: Float = if (fireRatePerSecond > 0) 1f / fireRatePerSecond else Float.MAX_VALUE
}

data class ThrowableEntity(
    val weaponType: WeaponType,
    val modelInstance: ModelInstance,
    val position: Vector3,
    var velocity: Vector3,
    var lifetime: Float // For timed explosions (like dynamite)
) {
    private val GRAVITY = -35f // A constant for gravitational pull
    private val bounds = BoundingBox()
    private val size = 1.0f // Collision size of the throwable
    private var rotationZ = 0f
    private val rotationSpeed = 360f // Degrees per second for tumbling effect

    init {
        updateBounds()
    }

    fun update(deltaTime: Float) {
        // Apply gravity
        velocity.y += GRAVITY * deltaTime

        // Move the object
        position.mulAdd(velocity, deltaTime)

        // Apply rotation
        rotationZ += rotationSpeed * deltaTime

        // Update lifetime timer
        lifetime -= deltaTime

        updateTransform()
        updateBounds()
    }

    private fun updateTransform() {
        modelInstance.transform.setToTranslation(position)
        modelInstance.transform.rotate(Vector3.Z, rotationZ) // Tumble forward
    }

    private fun updateBounds() {
        val halfSize = size / 2f
        bounds.set(
            position.cpy().sub(halfSize),
            position.cpy().add(halfSize)
        )
    }

    fun getBoundingBox(): BoundingBox {
        return bounds
    }
}
