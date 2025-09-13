package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter

class MissionSystem(val game: MafiaGame) {

    private val allMissions = mutableMapOf<String, MissionDefinition>()
    private var activeMission: MissionState? = null
    private var gameState = GameState() // This will be loaded from a file later

    fun getSaveData(): MissionProgressData {
        return MissionProgressData(
            activeMissionId = activeMission?.definition?.id,
            activeMissionObjectiveIndex = activeMission?.currentObjectiveIndex ?: 0,
            completedMissionIds = gameState.completedMissionIds
        )
    }

    fun loadSaveData(data: MissionProgressData) {
        gameState.completedMissionIds = data.completedMissionIds
        data.activeMissionId?.let {
            startMission(it) // This will create a new MissionState
            activeMission?.currentObjectiveIndex = data.activeMissionObjectiveIndex
            updateUIForCurrentObjective()
        } ?: run {
            activeMission = null // No active mission
            game.uiManager.updateMissionObjective("")
        }
    }

    private fun isPlayerInInterior(): Boolean = game.sceneManager.currentScene == SceneType.HOUSE_INTERIOR
    private val allDialogs = mutableMapOf<String, DialogSequence>()

    init {
        val testDialog = DialogSequence(
            lines = listOf(
                DialogLine("Mr. Big", "I've been expecting you. We have much to discuss."),
                DialogLine("Player", "I'm listening.")
            )
        )
        allDialogs["mr_big_intro"] = testDialog
    }

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
        // --- ADDED DEBUGGING ---
        println("---- MISSION LOADING DEBUG ----")
        println("Attempting to load missions from absolute path: ${dirHandle.file().absolutePath}")

        if (!dirHandle.exists() || !dirHandle.isDirectory) {
            println("ERROR: Directory does not exist or is not a directory. Stopping load process.")
            println("-----------------------------")
            return
        }

        val missionFiles = dirHandle.list(".json")
        if (missionFiles.isEmpty()) {
            println("Directory exists but contains no '.json' files.")
            println("-----------------------------")
            return
        }

        println("Found ${missionFiles.size} potential mission file(s): ${missionFiles.joinToString { it.name() }}")
        // --- END OF DEBUGGING ---

        missionFiles.forEach { file ->
            try {
                val jsonString = file.readString()
                val missionDef = json.fromJson(MissionDefinition::class.java, jsonString)

                if (missionDef != null && missionDef.id.isNotEmpty()) {
                    allMissions[missionDef.id] = missionDef
                    println(" -> SUCCESS: Loaded mission '${missionDef.title}' (ID: ${missionDef.id})")
                } else {
                    println(" -> ERROR: Failed to parse mission from ${file.name()}. JSON might be invalid or ID is missing.")
                }
            } catch (e: Exception) {
                println(" -> CRITICAL EXCEPTION while loading ${file.name()}: ${e.message}")
            }
        }
        println("Finished loading. Total missions in memory: ${allMissions.size}")
        println("-----------------------------")
    }

    private fun isScopeValid(objective: MissionObjective, mission: MissionState): Boolean {
        return when (mission.definition.scope) {
            MissionScope.WORLD_ONLY -> !isPlayerInInterior()
            MissionScope.INTERIOR_ONLY -> isPlayerInInterior()
            MissionScope.ANYWHERE -> true
        }
    }

    fun update(deltaTime: Float) {
        val currentMission = activeMission ?: return
        val objective = currentMission.getCurrentObjective() ?: return

        // Scope check at the beginning of the update loop
        if (!isScopeValid(objective, currentMission)) {
            return
        }

        if (isObjectiveComplete(objective, currentMission)) {
            advanceObjective()
        }

        // Update any time-based objectives
        if (objective.completionCondition.type == ConditionType.TIMER_EXPIRES) {
            val timer = currentMission.missionVariables["timer"] as? Float ?: objective.completionCondition.timerDuration ?: 0f
            currentMission.missionVariables["timer"] = timer - deltaTime
        }
    }

    fun getMissionDefinition(id: String): MissionDefinition? = allMissions[id]

    fun createNewMission(): MissionDefinition {
        val newId = "mission_${System.currentTimeMillis()}"
        val newMission = MissionDefinition(id = newId, title = "New Mission")
        saveMission(newMission) // Save it immediately
        return newMission
    }

    fun deleteMission(id: String) {
        allMissions.remove(id)
        try {
            val file = Gdx.files.local("$missionsDir/$id.json")
            if (file.exists()) {
                file.delete()
                println("Deleted mission file for ID: $id")
            }
        } catch (e: Exception) {
            println("Error deleting mission file for ID: $id - ${e.message}")
        }
    }

    fun saveMission(missionDef: MissionDefinition) {
        allMissions[missionDef.id] = missionDef
        saveMissionToFile(missionDef)
    }

    fun reportDialogComplete(dialogId: String) {
        val objective = activeMission?.getCurrentObjective() ?: return

        // Check if the current objective was to complete this specific dialog
        // (This is a simplified way to handle "TALK_TO_NPC")
        if (objective.completionCondition.type == ConditionType.TALK_TO_NPC) {
            // In a more complex system, you'd check targetId here too
            activeMission?.missionVariables?.set("dialog_complete_${objective.completionCondition.targetId}", true)
            println("Reported dialog completion for objective.")
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
                return state.missionVariables["dialog_complete_${condition.targetId}"] == true
            }
            ConditionType.INTERACT_WITH_OBJECT -> {
                return state.missionVariables["interacted_${condition.targetId}"] == true
            }
            ConditionType.COLLECT_ITEM -> {
                val requiredItem = condition.itemType ?: return false
                val requiredCount = condition.itemCount
                val currentCount = game.playerSystem.countItemInInventory(requiredItem)
                return currentCount >= requiredCount
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

        missionDef.eventsOnStart.forEach { executeEvent(it) }

        updateUIForCurrentObjective()
    }

    private fun advanceObjective() {
        val missionState = activeMission ?: return
        missionState.currentObjectiveIndex++

        val nextObjective = missionState.getCurrentObjective()
        if (nextObjective == null) {
            endMission(true) // Mission is complete
        } else {
            println("Objective complete! New objective: ${nextObjective.description}")

            // If the new objective is a timer, set its starting value now.
            if (nextObjective.completionCondition.type == ConditionType.TIMER_EXPIRES) {
                val duration = nextObjective.completionCondition.timerDuration ?: 60f
                missionState.missionVariables["timer"] = duration
                println("Mission timer started for $duration seconds.")
            }

            nextObjective.eventsOnStart.forEach { executeEvent(it) }
            updateUIForCurrentObjective()
        }
    }

    private fun endMission(completed: Boolean) {
        val mission = activeMission ?: return
        if (completed) {
            println("--- MISSION COMPLETE: ${mission.definition.title} ---")
            gameState.completedMissionIds.add(mission.definition.id)

            // ADDED: Execute mission-complete events and grant rewards
            mission.definition.eventsOnComplete.forEach { executeEvent(it) }
            mission.definition.rewards.forEach { grantReward(it) }

        } else {
            println("--- MISSION FAILED: ${mission.definition.title} ---")
            // TODO: Reset any changes made by the mission
        }
        activeMission = null
        game.uiManager.updateMissionObjective("")
    }

    fun reportInteraction(objectId: String) {
        val objective = activeMission?.getCurrentObjective() ?: return
        if (objective.completionCondition.type == ConditionType.INTERACT_WITH_OBJECT &&
            objective.completionCondition.targetId == objectId) {
            activeMission?.missionVariables?.set("interacted_$objectId", true)
            println("Player interacted with mission-critical object: $objectId")
        }
    }

    private fun grantReward(reward: MissionReward) {
        when (reward.type) {
            RewardType.GIVE_MONEY -> game.playerSystem.addMoney(reward.amount)
            RewardType.SHOW_MESSAGE -> game.uiManager.showTemporaryMessage(reward.message)

            RewardType.GIVE_AMMO -> {
                reward.weaponType?.let {
                    game.playerSystem.addAmmoToReserves(it, reward.amount)
                }
            }
            RewardType.GIVE_ITEM -> {
                reward.itemType?.let {
                    game.itemSystem.createItem(game.playerSystem.getPosition(), it)?.let { item ->
                        game.sceneManager.activeItems.add(item)
                    }
                }
            }
            else -> println("Reward type ${reward.type} not yet implemented.")
        }
        println("Granted reward: ${reward.type}")
    }

    private fun executeEvent(event: GameEvent) {
        println("Executing mission event: ${event.type}")
        when (event.type) {
            GameEventType.SPAWN_ENEMY -> {
                if (event.enemyType != null && event.enemyBehavior != null && event.spawnPosition != null) {
                    val config = EnemySpawnConfig(
                        enemyType = event.enemyType,
                        behavior = event.enemyBehavior,
                        position = event.spawnPosition,
                        id = event.targetId // The ID is crucial for objectives
                    )
                    game.enemySystem.createEnemy(config)?.let { game.sceneManager.activeEnemies.add(it) }
                }
            }
            GameEventType.DESPAWN_ENTITY -> {
                event.targetId?.let { id ->
                    game.sceneManager.activeEnemies.find { it.id == id }?.let { game.sceneManager.activeEnemies.removeValue(it, true) }
                    game.sceneManager.activeNPCs.find { it.id == id }?.let { game.sceneManager.activeNPCs.removeValue(it, true) }
                    game.sceneManager.activeCars.find { it.id == id }?.let { game.sceneManager.activeCars.removeValue(it, true) }
                    println("Despawned entity with ID: $id")
                }
            }
            GameEventType.START_DIALOG -> {
                event.dialogId?.let { dialogId ->
                    allDialogs[dialogId]?.let { sequence ->
                        // Create a new sequence with an onComplete callback
                        val sequenceWithCallback = sequence.copy(
                            onComplete = {
                                // When the dialog finishes, report it to the mission system
                                reportDialogComplete(dialogId)
                            }
                        )
                        game.uiManager.dialogSystem.startDialog(sequenceWithCallback)
                    }
                }
            }
            else -> println("Event type ${event.type} not yet implemented.")
        }
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

    fun isMissionActive(id: String): Boolean {
        return activeMission?.definition?.id == id
    }

    fun getAllMissionDefinitions(): Map<String, MissionDefinition> = allMissions

    private fun saveMissionToFile(missionDef: MissionDefinition) {
        try {
            val dirHandle = Gdx.files.local(missionsDir)
            if (!dirHandle.exists()) dirHandle.mkdirs()
            val fileHandle = dirHandle.child("${missionDef.id}.json")
            fileHandle.writeString(json.prettyPrint(missionDef), false)
            println("Saved mission '${missionDef.title}' to ${fileHandle.path()}")
        } catch (e: Exception) {
            println("ERROR saving mission '${missionDef.id}': ${e.message}")
        }
    }
}
