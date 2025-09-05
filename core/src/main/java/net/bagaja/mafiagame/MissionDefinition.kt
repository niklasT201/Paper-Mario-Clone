package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3

// --- Core Mission Definition ---

data class MissionDefinition(
    val id: String = "",
    val title: String = "",
    val objectives: List<MissionObjective> = emptyList(),
    val startTrigger: MissionTrigger = MissionTrigger(),
    val modifiers: MissionModifiers? = null
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
    val description: String = "",
    val completionCondition: CompletionCondition = CompletionCondition(),
    val eventsOnStart: List<GameEvent>? = null
)

enum class ConditionType {
    ENTER_AREA,
    ELIMINATE_TARGET,
    TIMER_EXPIRES,
    TALK_TO_NPC
}

data class CompletionCondition(
    val type: ConditionType = ConditionType.ENTER_AREA,
    val areaCenter: Vector3? = null,
    val areaRadius: Float? = null,

    val targetId: String? = null,
    val timerDuration: Float? = null
)

// --- Mission Triggers (How missions start) ---

enum class TriggerType {
    ON_ENTER_AREA,
    ON_TALK_TO_NPC
}

data class MissionTrigger(
    val type: TriggerType = TriggerType.ON_ENTER_AREA,
    val areaCenter: Vector3? = null,
    val areaRadius: Float? = null,
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
    SPAWN_ENEMY
}

data class GameEvent(
    val type: GameEventType = GameEventType.SPAWN_ENEMY,
    val enemyType: EnemyType? = null,
    val spawnPosition: Vector3? = null,
    val targetId: String? = null
)

// --- Game State (For Saving/Loading Progress) ---

data class GameState(
    val completedMissionIds: MutableSet<String> = mutableSetOf()
)
