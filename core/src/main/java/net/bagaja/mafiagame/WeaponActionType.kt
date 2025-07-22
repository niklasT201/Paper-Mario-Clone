package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox

data class Bullet(
    val position: Vector3,
    val velocity: Vector3,
    val modelInstance: ModelInstance,
    var lifetime: Float,
    val rotationY: Float
) {
    val bounds: BoundingBox = BoundingBox()
    private val bulletSize = 0.2f // A small collision box for the bullet

    init {
        updateBounds()
    }

    fun update(deltaTime: Float) {
        position.mulAdd(velocity, deltaTime)
        updateBounds()
        lifetime -= deltaTime

        // Visual update
        updateTransform()
    }

    private fun updateTransform() {
        modelInstance.transform.idt()
        modelInstance.transform.setTranslation(position)
        modelInstance.transform.rotate(Vector3.Y, rotationY)
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
    val magazineSize: Int, // 0 for infinite or non-applicable
    val requiresReload: Boolean,
    val allowsMovementWhileShooting: Boolean,
    val playerPoseTexturePath: String,
    val bulletTexturePath: String?,
    val bulletSpeed: Float = 0f,
    val bulletLifetime: Float = 0f
) {
    UNARMED(
        displayName = "Unarmed",
        actionType = WeaponActionType.MELEE,
        fireRatePerSecond = 0f,
        damage = 5f,
        magazineSize = 0,
        requiresReload = false,
        allowsMovementWhileShooting = true,
        playerPoseTexturePath = "textures/player/pig_character.png",
        bulletTexturePath = null
    ),
    TOMMY_GUN(
        displayName = "Tommy Gun",
        actionType = WeaponActionType.SHOOTING,
        fireRatePerSecond = 10f, // High rate of fire
        damage = 15f,
        magazineSize = 50,
        requiresReload = true,
        allowsMovementWhileShooting = false,
        playerPoseTexturePath = "textures/player/weapons/tommy_gun/player_tommy_gun.png",
        bulletTexturePath = "textures/player/weapons/bullet_tile.png",
        bulletSpeed = 150f,
        bulletLifetime = 1.5f
    ),
    PISTOL(
        displayName = "Pistol",
        actionType = WeaponActionType.SHOOTING,
        fireRatePerSecond = 3f, // Slower rate of fire
        damage = 25f,
        magazineSize = 8,
        requiresReload = true,
        allowsMovementWhileShooting = true,
        playerPoseTexturePath = "textures/player/weapons/tommy_gun/player_tommy_gun.png",
        bulletTexturePath = "textures/player/weapons/bullet_tile.png",
        bulletSpeed = 200f,
        bulletLifetime = 2.0f
    ),
    // Example of a future melee weapon
    BASEBALL_BAT(
        displayName = "Baseball Bat",
        actionType = WeaponActionType.MELEE,
        fireRatePerSecond = 1.5f, // Swing speed
        damage = 30f,
        magazineSize = 0,
        requiresReload = false,
        allowsMovementWhileShooting = true,
        playerPoseTexturePath = "textures/player/pig_character.png",
        bulletTexturePath = null
    );

    // Calculated property for the cooldown timer
    val fireCooldown: Float = if (fireRatePerSecond > 0) 1f / fireRatePerSecond else Float.MAX_VALUE
}
