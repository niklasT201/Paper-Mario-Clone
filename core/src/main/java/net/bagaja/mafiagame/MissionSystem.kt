package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter

class MissionSystem(private val game: MafiaGame) {

    private val allMissions = mutableMapOf<String, MissionDefinition>()
    private var activeMission: MissionState? = null
    private var gameState = GameState() // This will be loaded from a file later

    // LibGDX's JSON parser
    private val json = Json().apply {
        setUsePrototypes(false)
        setOutputType(JsonWriter.OutputType.json)
    }
    private val missionsDir = "missions"

    fun initialize() {
        loadAllMissionsFromFiles()
    }

    private fun loadAllMissionsFromFiles() {
        allMissions.clear()
        val dirHandle = Gdx.files.local(missionsDir)
        println("MissionSystem: Attempting to load missions from ${dirHandle.path()}...")

        if (!dirHandle.exists() || !dirHandle.isDirectory) {
            println("MissionSystem: ERROR - Directory '$missionsDir' not found or is not a directory.")
            return
        }

        val missionFiles = dirHandle.list(".json")
        if (missionFiles.isEmpty()) {
            println("MissionSystem: Directory exists but contains no '.json' files.")
            return
        }

        println("MissionSystem: Found ${missionFiles.size} potential mission file(s).")
        missionFiles.forEach { file ->
            try {
                val jsonString = file.readString()
                val missionDef = json.fromJson(MissionDefinition::class.java, jsonString)

                if (missionDef != null && missionDef.id.isNotEmpty()) {
                    allMissions[missionDef.id] = missionDef
                    println(" -> Successfully loaded mission: ${missionDef.title} (ID: ${missionDef.id})")
                } else {
                    println(" -> ERROR: Failed to parse mission from ${file.name()}.")
                }
            } catch (e: Exception) {
                println(" -> EXCEPTION while loading mission from ${file.name()}: ${e.message}")
            }
        }
        println("MissionSystem: Finished loading. Total missions loaded: ${allMissions.size}")
    }

    // THIS FUNCTION IS NO LONGER NEEDED, YOU CAN DELETE IT
    // private fun loadTestMissions() { ... }

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

        // --- CORRECTED CODE BLOCK ---
        if (id == "test_mission_01") {
            // Build the configuration object for the enemy
            val missionThugConfig = EnemySpawnConfig(
                position = Vector3(-25f, 2f, -20f),
                enemyType = EnemyType.MOUSE_THUG,
                behavior = EnemyBehavior.STATIONARY_SHOOTER,
                id = "mission_thug_01", // The specific ID for the objective
                initialWeapon = WeaponType.LIGHT_TOMMY_GUN // Give him a weapon
            )

            // Call createEnemy with the single config object
            val missionThug = game.enemySystem.createEnemy(missionThugConfig)

            if (missionThug != null) {
                game.sceneManager.activeEnemies.add(missionThug)
            }
        }
        // --- END CORRECTION ---

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

    private fun updateUIForCurrentObjective() {
        val objective = activeMission?.getCurrentObjective()
        if (objective != null) {
            game.uiManager.updateMissionObjective(objective.description)
        }
    }

    fun isMissionCompleted(id: String): Boolean {
        return gameState.completedMissionIds.contains(id)
    }

    fun getAllMissionDefinitions(): Map<String, MissionDefinition> = allMissions
}
