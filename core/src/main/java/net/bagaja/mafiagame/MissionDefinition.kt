package net.bagaja.mafiagame

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
    val startTrigger: MissionTrigger = MissionTrigger()
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
    val id: String = UUID.randomUUID().toString(), // ADD this unique ID
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
    ON_TALK_TO_NPC
}

data class MissionTrigger(
    val type: TriggerType = TriggerType.ON_ENTER_AREA,
    var areaCenter: Vector3 = Vector3(),
    var areaRadius: Float = TriggerSystem.VISUAL_RADIUS,
    val targetNpcId: String? = null
)

// --- Mission Modifiers (Special Rules) ---

data class MissionModifiers(
    val disableTraffic: Boolean = false,
    val setUnlimitedAmmoFor: WeaponType? = null,
    val setUnlimitedHealth: Boolean = false
)

// --- Events ---

enum class GameEventType {
    SPAWN_ENEMY,
    SPAWN_NPC,
    SPAWN_CAR,
    SPAWN_ITEM,
    SPAWN_MONEY_STACK,
    DESPAWN_ENTITY,
    START_DIALOG
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

    // Car-specific
    val carType: CarType? = null,
    val carIsLocked: Boolean = false,
    val carDriverType: String? = null,
    val carEnemyDriverType: EnemyType? = null,
    val carNpcDriverType: NPCType? = null,


    // Item/Money-specific properties
    val itemType: ItemType? = null,
    val itemValue: Int = 0,

    // Dialog-specific
    val dialogId: String? = null
)

// --- Game State (For Saving/Loading Progress) ---

data class GameState(
    var completedMissionIds: MutableSet<String> = mutableSetOf()
)
