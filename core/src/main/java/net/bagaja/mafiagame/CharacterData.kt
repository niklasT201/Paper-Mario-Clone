package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3

enum class WeaponCollectionPolicy(val displayName: String) {
    CANNOT_COLLECT("Cannot Collect Weapons"),
    COLLECT_ONLY("Collects, but won't use"),
    COLLECT_AND_USE("Collects and Uses Weapons")
}

enum class HealthSetting(val displayName: String) {
    FIXED_DEFAULT("Default"), // Health is taken from EnemyType
    FIXED_CUSTOM("Custom"),  // Health is a specific value set in the editor
    RANDOM_RANGE("Random")   // Health is a random value between a min/max
}

data class EnemySpawnConfig(
    val enemyType: EnemyType,
    val behavior: EnemyBehavior,
    val position: Vector3,
    val id: String? = null,
    val count: Int = 1,

    // Health Settings
    val healthSetting: HealthSetting = HealthSetting.FIXED_DEFAULT,
    val customHealthValue: Float = 100f,
    val minRandomHealth: Float = 80f,
    val maxRandomHealth: Float = 120f,

    // WEAPON SETTINGS
    val initialWeapon: WeaponType = WeaponType.UNARMED,
    val ammoSpawnMode: AmmoSpawnMode = AmmoSpawnMode.FIXED, // Uses weapon's default
    val setAmmoValue: Int = 30, // Custom ammo amount
    val enemyInitialMoney: Int = 0,

    val weaponCollectionPolicy: WeaponCollectionPolicy = WeaponCollectionPolicy.CANNOT_COLLECT,
    val canCollectItems: Boolean = true,
    val assignedPathId: String? = null,
    val canBePulledFromCar: Boolean = true,
    val standaloneDialog: StandaloneDialog? = null
)

data class NPCSpawnConfig(
    val npcType: NPCType,
    val behavior: NPCBehavior,
    val position: Vector3,
    val id: String? = null,
    val isHonest: Boolean = true,
    val canCollectItems: Boolean = true,
    val pathFollowingStyle: PathFollowingStyle = PathFollowingStyle.CONTINUOUS,
    val assignedPathId: String? = null,
    val canBePulledFromCar: Boolean = true,
    val standaloneDialog: StandaloneDialog? = null
)
