package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
import net.bagaja.mafiagame.NPCType
import net.bagaja.mafiagame.EnemyType
import net.bagaja.mafiagame.WeaponType

// --- Core Mission Definition ---

data class MissionDefinition(
    val id: String,
    val title: String,
    val objectives: List<MissionObjective>,
    val startTrigger: MissionTrigger,
    val modifiers: MissionModifiers? = null,
    // We will add rewards and worldChanges later
)

// --- Mission State (Runtime Data) ---

data class MissionState(
    val definition: MissionDefinition,
    var currentObjectiveIndex: Int = 0,
    val missionVariables: MutableMap<String, Any> = mutableMapOf()
) {
    fun getCurrentObjective(): MissionObjective? {
        return definition.objectives.getOrNull(currentObjectiveIndex)
    }
}

// --- Mission Objectives & Conditions ---

data class MissionObjective(
    val description: String,
    val completionCondition: CompletionCondition,
    // We will add eventsOnStart later
)

enum class ConditionType {
    ENTER_AREA,
    ELIMINATE_TARGET,
    TIMER_EXPIRES,
    TALK_TO_NPC
}

data class CompletionCondition(
    val type: ConditionType,
    val areaCenter: Vector3? = null, // For ENTER_AREA
    val areaRadius: Float? = null,   // For ENTER_AREA
    val targetId: String? = null,      // For ELIMINATE_TARGET or TALK_TO_NPC
    val timerDuration: Float? = null // For TIMER_EXPIRES
)

// --- Mission Triggers (How missions start) ---

enum class TriggerType {
    ON_ENTER_AREA,
    ON_TALK_TO_NPC
}

data class MissionTrigger(
    val type: TriggerType,
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

// --- Game State (For Saving/Loading Progress) ---

data class GameState(
    val completedMissionIds: MutableSet<String> = mutableSetOf(),
    // We will add worldStateOverrides here later
)
