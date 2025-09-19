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
    SHOW_MESSAGE
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
    val eventsOnStart: MutableList<GameEvent> = mutableListOf()
)

data class MissionReward(
    val type: RewardType = RewardType.SHOW_MESSAGE,
    val amount: Int = 0,
    val message: String = "Mission Complete!",
    val weaponType: WeaponType? = null,
    val itemType: ItemType? = null,
    val carType: CarType? = null
)

enum class ConditionType {
    ENTER_AREA,
    ELIMINATE_TARGET,
    TIMER_EXPIRES,
    TALK_TO_NPC,
    INTERACT_WITH_OBJECT, // NEW
    COLLECT_ITEM          // NEW
}

data class CompletionCondition(
    val type: ConditionType = ConditionType.ENTER_AREA,
    val areaCenter: Vector3? = null,
    val areaRadius: Float? = null,
    val targetId: String? = null,
    val timerDuration: Float? = null,
    val itemType: ItemType? = null,
    val itemCount: Int = 1
)

// --- Mission Triggers (How missions start) ---

enum class TriggerType {
    ON_ENTER_AREA,
    ON_TALK_TO_NPC,
    ON_COLLECT_ITEM,
    ON_ENTER_HOUSE,
    ON_HURT_ENEMY,
    ON_ENTER_CAR
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
    var disableTraffic: Boolean = false,
    val setUnlimitedAmmoFor: WeaponType? = null,
    var setUnlimitedHealth: Boolean = false
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
    SPAWN_SPAWNER,
    SPAWN_TELEPORTER,
    SPAWN_FIRE
}

data class GameEvent(
    val type: GameEventType = GameEventType.SPAWN_ENEMY,
    val spawnPosition: Vector3? = null,
    val targetId: String? = null,

    // Enemy-specific
    val enemyType: EnemyType? = null,
    val enemyBehavior: EnemyBehavior? = null,
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
    val minParticles: Int? = null,
    val maxParticles: Int? = null,
    // Item Spawner
    val spawnerItemType: ItemType? = null,
    val minItems: Int? = null,
    val maxItems: Int? = null,
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
    val fireScale: Float? = null // Using a single value for scale
)

// --- Game State (For Saving/Loading Progress) ---

data class GameState(
    var completedMissionIds: MutableSet<String> = mutableSetOf()
)
