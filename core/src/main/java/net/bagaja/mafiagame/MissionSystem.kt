package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
import net.bagaja.mafiagame.EnemyType
import net.bagaja.mafiagame.MafiaGame
import net.bagaja.mafiagame.NPCBehavior
import net.bagaja.mafiagame.NPCType

class MissionSystem(private val game: MafiaGame) {

    private val allMissions = mutableMapOf<String, MissionDefinition>()
    private var activeMission: MissionState? = null
    private var gameState = GameState() // This will be loaded from a file later

    fun initialize() {
        // For now, we will load a hardcoded test mission
        loadTestMissions()
    }

    private fun loadTestMissions() {
        val testMission = MissionDefinition(
            id = "test_mission_01",
            title = "First Hit",
            startTrigger = MissionTrigger(
                type = TriggerType.ON_ENTER_AREA,
                areaCenter = Vector3(-10f, 2f, 10f),
                areaRadius = 5f
            ),
            objectives = listOf(
                MissionObjective(
                    description = "Go to the alley behind the restaurant.",
                    completionCondition = CompletionCondition(
                        type = ConditionType.ENTER_AREA,
                        areaCenter = Vector3(-25f, 2f, -15f),
                        areaRadius = 8f
                    )
                ),
                MissionObjective(
                    description = "Take out the thug.",
                    completionCondition = CompletionCondition(
                        type = ConditionType.ELIMINATE_TARGET,
                        targetId = "mission_thug_01" // We'll need to spawn an enemy with this ID
                    )
                ),
                MissionObjective(
                    description = "Mission Complete!",
                    completionCondition = CompletionCondition(
                        type = ConditionType.TIMER_EXPIRES,
                        timerDuration = 3f // Show "Complete" for 3 seconds
                    )
                )
            )
        )
        allMissions[testMission.id] = testMission
    }

    fun update(deltaTime: Float) {
        val currentMission = activeMission ?: return // If no mission is active, do nothing

        val objective = currentMission.getCurrentObjective() ?: return

        // Check if the current objective is complete
        if (isObjectiveComplete(objective, currentMission)) {
            advanceObjective()
        }

        // Update any time-based objectives
        if (objective.completionCondition.type == ConditionType.TIMER_EXPIRES) {
            val timer = currentMission.missionVariables["timer"] as? Float ?: objective.completionCondition.timerDuration ?: 0f
            currentMission.missionVariables["timer"] = timer - deltaTime
        }
    }

    private fun isObjectiveComplete(objective: MissionObjective, state: MissionState): Boolean {
        val condition = objective.completionCondition
        when (condition.type) {
            ConditionType.ENTER_AREA -> {
                val playerPos = game.playerSystem.getPosition()
                val distance = playerPos.dst(condition.areaCenter!!)
                return distance < condition.areaRadius!!
            }
            ConditionType.ELIMINATE_TARGET -> {
                // Check if an enemy with the target ID still exists in the scene
                val targetExists = game.sceneManager.activeEnemies.any { it.id == condition.targetId }
                return !targetExists
            }
            ConditionType.TIMER_EXPIRES -> {
                val timer = state.missionVariables["timer"] as? Float ?: 0f
                return timer <= 0f
            }
            ConditionType.TALK_TO_NPC -> {
                // Logic for this will be added later
                return false
            }
        }
    }

    fun startMission(id: String) {
        if (activeMission != null || gameState.completedMissionIds.contains(id)) {
            return // Don't start a mission if one is active or it's already done
        }

        val missionDef = allMissions[id] ?: return
        println("--- MISSION STARTED: ${missionDef.title} ---")
        activeMission = MissionState(missionDef)

        // SPECIAL CASE: Spawn the enemy for our test mission
        if (id == "test_mission_01") {
            // Now we create the enemy directly with the ID it needs for the mission objective.
            val missionThug = game.enemySystem.createEnemy(
                position = Vector3(-25f, 2f, -20f),
                enemyType = EnemyType.MOUSE_THUG,
                behavior = EnemyBehavior.STATIONARY_SHOOTER,
                id = "mission_thug_01" // <-- Pass the specific ID here
            )

            if (missionThug != null) {
                game.sceneManager.activeEnemies.add(missionThug)
            }
        }

        updateUIForCurrentObjective()
    }

    private fun advanceObjective() {
        val missionState = activeMission ?: return
        missionState.currentObjectiveIndex++

        if (missionState.getCurrentObjective() == null) {
            // Mission is complete!
            endMission(true)
        } else {
            // Moved to the next objective
            println("Objective complete! New objective: ${missionState.getCurrentObjective()!!.description}")
            updateUIForCurrentObjective()
        }
    }

    private fun endMission(completed: Boolean) {
        val mission = activeMission ?: return
        if (completed) {
            println("--- MISSION COMPLETE: ${mission.definition.title} ---")
            gameState.completedMissionIds.add(mission.definition.id)
            // TODO: Give rewards, apply world changes
        } else {
            println("--- MISSION FAILED: ${mission.definition.title} ---")
            // TODO: Reset any changes made by the mission
        }
        activeMission = null
        game.uiManager.updateMissionObjective("") // Clear the UI
    }

    fun isMissionCompleted(id: String): Boolean {
        return gameState.completedMissionIds.contains(id)
    }

    private fun updateUIForCurrentObjective() {
        val objective = activeMission?.getCurrentObjective()
        if (objective != null) {
            game.uiManager.updateMissionObjective(objective.description)
        }
    }
}
