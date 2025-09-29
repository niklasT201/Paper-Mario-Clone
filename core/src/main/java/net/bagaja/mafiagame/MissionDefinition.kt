package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import java.util.*

enum class MissionScope {
    WORLD_ONLY,     // Objectives can only be completed in the main world.
    INTERIOR_ONLY,  // Objectives can only be completed inside any house interior.
    ANYWHERE        // Objectives can be completed in both the world and interiors.
}

enum class ObjectiveMarkerType {
    NONE,           // No visual aid.
    AREA_CIRCLE,    // A circle on the ground for "go here" objectives.
    TARGET_ENTITY,  // A highlight on a specific NPC, Enemy, or Car.
    TARGET_LOCATION // A static marker at a specific 3D point.
}

// NEW: Defines the different types of rewards a mission can grant.
enum class RewardType {
    GIVE_MONEY,
    GIVE_AMMO,
    GIVE_ITEM,
    UNLOCK_CAR_SPAWN, // For adding a new car to a garage, for example.
    SHOW_MESSAGE,
    UPGRADE_SPAWNER_WEAPON
}

// --- Core Mission Definition ---

data class MissionDefinition(
    val id: String = "",
    var title: String = "",
    var description: String = "",
    var prerequisites: MutableList<String> = mutableListOf(),
    var scope: MissionScope = MissionScope.WORLD_ONLY,
    val eventsOnStart: MutableList<GameEvent> = mutableListOf(),
    val eventsOnComplete: MutableList<GameEvent> = mutableListOf(),
    val objectives: MutableList<MissionObjective> = mutableListOf(),
    val rewards: MutableList<MissionReward> = mutableListOf(),
    val startTrigger: MissionTrigger = MissionTrigger(),
    val modifiers: MissionModifiers = MissionModifiers()
)

// --- Mission State (Runtime Data) ---

data class MissionState(
    val definition: MissionDefinition = MissionDefinition(),
    var currentObjectiveIndex: Int = 0,
    val missionVariables: MutableMap<String, Any> = mutableMapOf()
) {
    fun getCurrentObjective(): MissionObjective? {
        return definition.objectives.getOrNull(currentObjectiveIndex)
    }
}

// --- Mission Objectives & Conditions ---

data class MissionObjective(
    val id: String = UUID.randomUUID().toString(),
    val description: String = "",
    val markerType: ObjectiveMarkerType = ObjectiveMarkerType.NONE,
    val markerTargetId: String? = null,
    val markerPosition: Vector3? = null,
    val markerRadius: Float = 10f,
    val completionCondition: CompletionCondition = CompletionCondition(),
    val eventsOnStart: MutableList<GameEvent> = mutableListOf(),
    val hasTimer: Boolean = false,
    val timerDuration: Float = 60f
)

data class MissionReward(
    val type: RewardType = RewardType.SHOW_MESSAGE,
    val amount: Int = 0,
    val message: String = "Mission Complete!",
    val weaponType: WeaponType? = null,
    val itemType: ItemType? = null,
    val carType: CarType? = null,
    val spawnerTargetEnemyType: EnemyType? = null, // Which enemy type to upgrade
    val newDefaultWeapon: WeaponType? = null     // The new weapon they will spawn with
)

enum class ConditionType {
    ENTER_AREA,
    ELIMINATE_TARGET,
    ELIMINATE_ALL_ENEMIES,
    DESTROY_CAR,
    BURN_DOWN_HOUSE,
    DESTROY_OBJECT,
    REACH_ALTITUDE,
    DRIVE_TO_LOCATION,
    SURVIVE_FOR_TIME,
    TALK_TO_NPC,
    INTERACT_WITH_OBJECT,
    COLLECT_ITEM,
    COLLECT_SPECIFIC_ITEM,
    STAY_IN_AREA,
    MAINTAIN_DISTANCE
}

enum class StayInAreaMode(val displayName: String) {
    PLAYER_ONLY("Player Only"),
    CAR_ONLY("Car Only"),
    PLAYER_OR_CAR("Player or Car")
}

enum class MaintainDistanceCompletionAction(val displayName: String) {
    STOP_AND_STAY_IN_CAR("Stop and Stay in Car"),
    STOP_AND_EXIT_CAR("Stop and Exit Car"),
    CONTINUE_PATROLLING("Continue Patrolling Path")
}

data class CompletionCondition(
    val type: ConditionType = ConditionType.ENTER_AREA,
    val areaCenter: Vector3? = if (type == ConditionType.ENTER_AREA) Vector3() else null,
    val areaRadius: Float? = if (type == ConditionType.ENTER_AREA) 10f else null,
    val stayInAreaMode: StayInAreaMode? = null,
    val targetId: String? = null,
    val checkTargetInsteadOfPlayer: Boolean = false,
    val requirePlayerAtDestination: Boolean = true,
    val maintainDistanceCompletionAction: MaintainDistanceCompletionAction? = null,
    val requiredDistance: Float? = null,
    val dialogId: String? = null,
    val targetAltitude: Float? = null,
    val timerDuration: Float? = null,
    val itemType: ItemType? = null,
    val itemCount: Int = 1,
    val itemId: String? = null
)

// --- Mission Triggers (How missions start) ---

enum class TriggerType {
    ON_ENTER_AREA,
    ON_TALK_TO_NPC,
    ON_COLLECT_ITEM,
    ON_ENTER_HOUSE,
    ON_HURT_ENEMY,
    ON_ENTER_CAR,
    ON_ALL_ENEMIES_ELIMINATED
}

data class MissionTrigger(
    var type: TriggerType = TriggerType.ON_ENTER_AREA,
    var areaCenter: Vector3 = Vector3(),
    var areaRadius: Float = TriggerSystem.VISUAL_RADIUS,
    var targetNpcId: String? = null,
    var dialogId: String? = null,
    var sceneId: String = "WORLD",

    var itemType: ItemType? = null,     // For ON_COLLECT_ITEM
    var itemCount: Int = 1,             // For ON_COLLECT_ITEM
    var targetHouseId: String? = null,  // For ON_ENTER_HOUSE
    var targetCarId: String? = null     // For ON_ENTER_CAR
)

// --- Mission Modifiers (Special Rules) ---

data class MissionModifiers(
    // Player Modifiers
    var setUnlimitedHealth: Boolean = false,
    var incomingDamageMultiplier: Float = 1.0f, // 1.0 = normal, 0.5 = half damage, 2.0 = double damage
    var playerDamageMultiplier: Float = 1.0f,   // 1.0 = normal, 2.0 = double damage dealt
    var playerSpeedMultiplier: Float = 1.0f,    // 1.0 = normal, 1.5 = 50% faster
    var infiniteAmmo: Boolean = false,          // For ALL weapons
    var infiniteAmmoForWeapon: WeaponType? = null,
    var disableWeaponSwitching: Boolean = false,
    var disableWeaponPickups: Boolean = false,
    var disableItemPickups: Boolean = false,

    // Vehicle Modifiers
    var makePlayerVehicleInvincible: Boolean = false,
    var allCarsUnlocked: Boolean = false,
    var carSpeedMultiplier: Float = 1.0f,       // Affects player-driven cars

    // World & AI Modifiers
    var allHousesLocked: Boolean = false,
    var allHousesUnlocked: Boolean = false,
    var disableCarSpawners: Boolean = false,
    var disableItemSpawners: Boolean = false,
    var disableParticleSpawners: Boolean = false,
    var disableEnemySpawners: Boolean = false,
    var disableNpcSpawners: Boolean = false,
    var disableNoItemDrops: Boolean = false,
    var disableCharacterSpawners: Boolean = false,
    var civiliansFleeOnSight: Boolean = false,
    var increasedEnemySpawns: Boolean = false,
    var freezeTimeAt: Float? = null, // A value from 0.0 to 1.0
    var disablePoliceResponse: Boolean = false // For a future police system
)

// --- Events ---

enum class GameEventType {
    SPAWN_ENEMY,
    SPAWN_NPC,
    SPAWN_CAR,
    SPAWN_ITEM,
    SPAWN_MONEY_STACK,
    DESPAWN_ENTITY,
    START_DIALOG,
    SPAWN_HOUSE,
    SPAWN_OBJECT,
    SPAWN_BLOCK,
    DESPAWN_BLOCK_AT_POS,
    SPAWN_SPAWNER,
    SPAWN_TELEPORTER,
    SPAWN_FIRE,
    ENABLE_SPAWNER,
    DISABLE_SPAWNER,
    LOCK_HOUSE,
    UNLOCK_HOUSE,
    GIVE_WEAPON,
    FORCE_EQUIP_WEAPON,
    SPAWN_CAR_PATH_NODE,
    SPAWN_CHARACTER_PATH_NODE,
    CLEAR_INVENTORY
}

data class GameEvent(
    val type: GameEventType = GameEventType.SPAWN_ENEMY,
    val spawnPosition: Vector3? = null,
    val targetId: String? = null,
    val sceneId: String? = null,
    val objectiveTag: String? = null,

    // Enemy-specific
    val enemyType: EnemyType? = null,
    val enemyBehavior: EnemyBehavior? = null,
    val assignedPathId: String? = null,
    val healthSetting: HealthSetting? = null,
    val customHealthValue: Float? = null,
    val minRandomHealth: Float? = null,
    val maxRandomHealth: Float? = null,
    val initialWeapon: WeaponType? = null,
    val ammoSpawnMode: AmmoSpawnMode? = null,
    val setAmmoValue: Int? = null,
    val weaponCollectionPolicy: WeaponCollectionPolicy? = null,
    val canCollectItems: Boolean? = null,

    // NPC-specific
    val npcType: NPCType? = null,
    val npcBehavior: NPCBehavior? = null,
    val npcRotation: Float? = null,
    val pathFollowingStyle: PathFollowingStyle? = null,

    // Car-specific (Already complete)
    val carType: CarType? = null,
    val carIsLocked: Boolean = false,
    val carDriverType: String? = null,
    val carEnemyDriverType: EnemyType? = null,
    val carNpcDriverType: NPCType? = null,


    // Item/Money-specific properties
    val itemType: ItemType? = null,
    val itemValue: Int = 0,

    // Dialog-specific
    val dialogId: String? = null,

    // --- SYNCHRONIZED PROPERTIES FOR HOUSE, OBJECT, AND BLOCK ---

    // House Properties
    val houseType: HouseType? = null,
    val houseRotationY: Float? = null,
    val houseIsLocked: Boolean? = null,

    // Object Properties (Includes light source details)
    val objectType: ObjectType? = null,
    val lightColor: Color? = null,
    val lightIntensity: Float? = null,
    val lightRange: Float? = null,
    val flickerMode: FlickerMode? = null,
    val loopOnDuration: Float? = null,
    val loopOffDuration: Float? = null,

    // Block Properties
    val blockType: BlockType? = null,
    val blockShape: BlockShape? = null,
    val blockRotationY: Float? = null,
    val blockTextureRotationY: Float? = null,
    val blockTopTextureRotationY: Float? = null,
    val blockCameraVisibility: CameraVisibility? = null,

    // Spawner Properties
    val spawnerType: SpawnerType? = null,
    val spawnerMode: SpawnerMode? = null,
    val spawnInterval: Float? = null,
    val minSpawnRange: Float? = null,
    val maxSpawnRange: Float? = null,
    val spawnOnlyWhenPreviousIsGone: Boolean? = null,
    // Particle Spawner
    val particleEffectType: ParticleEffectType? = null,
    val spawnerMinParticles: Int? = null,
    val spawnerMaxParticles: Int? = null,
    // Item Spawner
    val spawnerItemType: ItemType? = null,
    val spawnerMinItems: Int? = null,
    val spawnerMaxItems: Int? = null,
    // Weapon Spawner
    val spawnerWeaponItemType: ItemType? = null,
    val randomMinAmmo: Int? = null,
    val randomMaxAmmo: Int? = null,
    // Enemy Spawner
    val spawnerEnemyType: EnemyType? = null,
    // NPC Spawner
    val spawnerNpcType: NPCType? = null,
    // Car Spawner
    val spawnerCarType: CarType? = null,
    val spawnerCarIsLocked: Boolean? = null,
    val spawnerCarDriverType: String? = null,
    val spawnerCarEnemyDriverType: EnemyType? = null,
    val spawnerCarNpcDriverType: NPCType? = null,
    val spawnerCarSpawnDirection: CarSpawnDirection? = null,

    // Teleporter Properties
    val teleporterId: String? = null,
    val linkedTeleporterId: String? = null,
    val teleporterName: String? = null,

    // Fire Properties
    val isLooping: Boolean? = null,
    val fadesOut: Boolean? = null,
    val lifetime: Float? = null,
    val canBeExtinguished: Boolean? = null,
    val dealsDamage: Boolean? = null,
    val damagePerSecond: Float? = null,
    val damageRadius: Float? = null,
    val fireScale: Float? = null,
    val weaponType: WeaponType? = null,
    val ammoAmount: Int? = null,

    val pathNodeId: String? = null,
    val previousNodeId: String? = null,
    val isOneWay: Boolean = false,
    val isMissionOnly: Boolean = false,
    val missionId: String? = null,
)

// --- Game State (For Saving/Loading Progress) ---

data class GameState(
    var completedMissionIds: MutableSet<String> = mutableSetOf()
)
