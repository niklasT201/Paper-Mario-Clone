package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter
import com.badlogic.gdx.utils.ObjectMap
import java.util.*

class MissionSystem(val game: MafiaGame, private val dialogueManager: DialogueManager) {
    private val allMissions = mutableMapOf<String, MissionDefinition>()
    var activeMission: MissionState? = null
    var activeModifiers: MissionModifiers? = null
    private var gameState = GameState() // This will be loaded from a file later
    private var playerInventorySnapshot: PlayerStateData? = null
    private var leaveCarTimer = -1f // -1 indicates timer is not active
    private var requiredCarIdForTimer: String? = null
    private var objectiveTimerStartDelay = -1f

    private var stayInAreaGraceTimer = -1f // -1 means timer is not active
    private val GRACE_PERIOD_DURATION = 5.0f
    private val destroyedCarIdsThisFrame = mutableListOf<String>()
    private val destroyedObjectIdsThisFrame = mutableListOf<String>()
    private val moneyTriggerStates = mutableMapOf<String, Boolean>()

    fun getSaveData(): MissionProgressData {
        val variablesToSave = ObjectMap<String, Any>()

        activeMission?.missionVariables?.forEach { (key, value) ->
            variablesToSave.put(key, value)
        }

        return MissionProgressData(
            activeMissionId = activeMission?.definition?.id,
            activeMissionObjectiveIndex = activeMission?.currentObjectiveIndex ?: 0,
            completedMissionIds = gameState.completedMissionIds,
            missionVariables = variablesToSave
        )
    }

    fun loadSaveData(data: MissionProgressData) {
        gameState.completedMissionIds = data.completedMissionIds
        data.activeMissionId?.let {
            startMission(it)
            activeMission?.currentObjectiveIndex = data.activeMissionObjectiveIndex

            activeMission?.missionVariables?.let { liveVariables ->
                for (entry in data.missionVariables) {
                    liveVariables[entry.key] = entry.value
                }
            }

            updateUIForCurrentObjective()
        } ?: run {
            activeMission = null // No active mission
            game.uiManager.updateMissionObjective("")
        }
    }

    private fun isPlayerInInterior(): Boolean = game.sceneManager.currentScene == SceneType.HOUSE_INTERIOR

    fun refreshDialogueIds() {
        dialogueManager.loadAllDialogues()
        println("MissionSystem: Dialogue IDs have been refreshed for the editor.")
    }

    // LibGDX's JSON parser
    private val json = Json().apply {
        setUsePrototypes(false)
        setOutputType(JsonWriter.OutputType.json)
    }
    private val missionsDir = "missions"

    fun initialize() {
        dialogueManager.loadAllDialogues()
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

    private fun handleStayInAreaObjective(objective: MissionObjective, state: MissionState) {
        val condition = objective.completionCondition
        val center = condition.areaCenter ?: return
        val radius = condition.areaRadius ?: return
        val mode = condition.stayInAreaMode ?: StayInAreaMode.PLAYER_ONLY

        var isInside = false
        val playerPos = game.playerSystem.getPosition()
        val isDriving = game.playerSystem.isDriving
        val car = game.playerSystem.drivingCar

        // Determine if the player/car is currently satisfying the condition
        when (mode) {
            StayInAreaMode.PLAYER_ONLY -> {
                if (!isDriving && playerPos.dst(center) < radius) {
                    isInside = true
                }
            }
            StayInAreaMode.CAR_ONLY -> {
                if (isDriving && car != null && car.position.dst(center) < radius) {
                    isInside = true
                }
            }
            StayInAreaMode.PLAYER_OR_CAR -> {
                val checkPos = if (isDriving && car != null) car.position else playerPos
                if (checkPos.dst(center) < radius) {
                    isInside = true
                }
            }
        }

        if (isInside) {
            // Player is safe inside the area.
            if (stayInAreaGraceTimer > 0f) {
                println("Player re-entered the area. Grace timer cancelled.")
                stayInAreaGraceTimer = -1f
                game.uiManager.updateReturnToAreaTimer(-1f) // Hide the timer
            }
        } else {
            // Player is outside the area.
            if (stayInAreaGraceTimer < 0f) { // Check < 0 to start the timer only once
                println("Player left the required area! Starting grace period timer.")
                stayInAreaGraceTimer = GRACE_PERIOD_DURATION
            }
        }
    }

    private fun handleMaintainDistanceObjective(objective: MissionObjective, state: MissionState) {
        val condition = objective.completionCondition
        val targetId = condition.targetId ?: return
        val requiredDistance = condition.requiredDistance ?: return

        val playerPos = game.playerSystem.getControlledEntityPosition()

        val targetEntity = game.sceneManager.activeEnemies.find { it.id == targetId } as Any?
            ?: game.sceneManager.activeNPCs.find { it.id == targetId } as Any?
            ?: game.sceneManager.activeCars.find { it.id == targetId } as Any?

        val targetPos = when (targetEntity) {
            is GameEnemy -> targetEntity.position
            is GameNPC -> targetEntity.position
            is GameCar -> targetEntity.position
            else -> {
                println("Mission Failed: Target to maintain distance from (ID: $targetId) is no longer in the scene.")
                failMission()
                return
            }
        }

        val currentDistance = playerPos.dst(targetPos)

        if (currentDistance < requiredDistance) {
            println("Mission Failed: Player got too close to the target! (Distance: $currentDistance < Required: $requiredDistance)")
            failMission()
        }
    }

    fun update(deltaTime: Float) {
        if (game.uiManager.isPauseMenuVisible()) {
            return // Stop all mission processing if the game is paused.
        }

        // Handle the leave-car timer
        if (leaveCarTimer > 0f) {
            leaveCarTimer -= deltaTime
            game.uiManager.updateLeaveCarTimer(leaveCarTimer) // Tell UI to show the timer

            if (leaveCarTimer <= 0f) {
                println("Mission Failed: Player did not return to the required car in time.")
                failMission() // Fails the mission if time runs out
            }
        }

        if (stayInAreaGraceTimer > 0f) {
            stayInAreaGraceTimer -= deltaTime
            game.uiManager.updateReturnToAreaTimer(stayInAreaGraceTimer) // Tell UI to show the timer

            if (stayInAreaGraceTimer <= 0f) {
                println("Mission Failed: Player did not return to the required area in time.")
                failMission() // Fails the mission if time runs out
            }
        }

        // --- MISSION START TRIGGER CHECK ---
        if (activeMission == null) {
            for ((missionId, missionDef) in allMissions) {
                if (gameState.completedMissionIds.contains(missionId)) continue

                val prerequisitesMet = missionDef.prerequisites.all { gameState.completedMissionIds.contains(it) }
                if (!prerequisitesMet) continue // If not, skip to the next mission.

                if (missionDef.availableStartTime != null && missionDef.availableEndTime != null) {
                    val currentTimeProgress = game.lightingManager.getDayNightCycle().getDayProgress()
                    val startTime = missionDef.availableStartTime!!
                    val endTime = missionDef.availableEndTime!!

                    val isAvailable = if (startTime <= endTime) {
                        // Normal case: e.g., 08:00 (0.33) to 17:00 (0.71)
                        currentTimeProgress in startTime..endTime
                    } else {
                        // Overnight case: e.g., 22:00 (0.91) to 06:00 (0.25)
                        currentTimeProgress >= startTime || currentTimeProgress <= endTime
                    }

                    if (!isAvailable) {
                        continue // Skip this mission, it's outside its time window.
                    }
                }

                val trigger = missionDef.startTrigger
                var shouldStartMission = false

                when (trigger.type) {
                    TriggerType.ON_DESTROY_CAR -> {
                        if (destroyedCarIdsThisFrame.contains(trigger.targetCarId)) {
                            shouldStartMission = true
                        }
                    }
                    TriggerType.ON_DESTROY_OBJECT -> {
                        // We are reusing targetNpcId for the object ID
                        if (destroyedObjectIdsThisFrame.contains(trigger.targetNpcId)) {
                            shouldStartMission = true
                        }
                    }
                    TriggerType.ON_MONEY_BELOW_THRESHOLD -> {
                        val playerMoney = game.playerSystem.getMoney()
                        if (playerMoney < trigger.moneyThreshold) {
                            // Only trigger if we haven't already triggered for this state
                            if (moneyTriggerStates[missionId] != true) {
                                shouldStartMission = true
                                moneyTriggerStates[missionId] = true // Mark as triggered
                            }
                        } else {
                            // If player's money goes back up, reset the trigger state so it can fire again later
                            moneyTriggerStates[missionId] = false
                        }
                    }
                    // This is the new trigger check
                    TriggerType.ON_COLLECT_ITEM -> {
                        val requiredItem = trigger.itemType ?: continue
                        val currentCount = game.playerSystem.countItemInInventory(requiredItem)
                        if (currentCount >= trigger.itemCount) {
                            shouldStartMission = true
                        }
                    }
                    // Check for the "all enemies eliminated" start trigger
                    TriggerType.ON_ALL_ENEMIES_ELIMINATED -> {
                        // This trigger should only fire if there are absolutely no enemies in the active scene.
                        if (game.sceneManager.activeEnemies.isEmpty) {
                            shouldStartMission = true
                        }
                    }
                    // Add other non-standard trigger checks here in the future
                    else -> {
                        // Nothing here
                    }
                }

                if (shouldStartMission) {
                    startMission(missionId)
                    break
                }
            }
        }

        destroyedCarIdsThisFrame.clear()
        destroyedObjectIdsThisFrame.clear()

        val currentMission = activeMission ?: return
        val objective = currentMission.getCurrentObjective() ?: return

        if (objective.showEnemiesLeftCounter) {
            val condition = objective.completionCondition
            if (condition.type == ConditionType.ELIMINATE_ALL_ENEMIES) {
                val enemiesToTrack = (currentMission.missionVariables["enemies_to_eliminate"] as? List<*>)?.filterIsInstance<String>()
                val count = if (enemiesToTrack != null) {
                    game.sceneManager.activeEnemies.count { it.id in enemiesToTrack }
                } else {
                    game.sceneManager.activeEnemies.size
                }
                game.uiManager.updateEnemiesLeft(count)
            } else if (condition.type == ConditionType.ELIMINATE_TARGET) {
                val targetExists = game.sceneManager.activeEnemies.any { it.id == condition.targetId }
                val count = if (targetExists) 1 else 0
                game.uiManager.updateEnemiesLeft(count)
            } else {
                // Hide the counter if the objective type doesn't match (e.g., if you change it in the editor)
                game.uiManager.updateEnemiesLeft(-1)
            }
        } else {
            // If the objective does not want the counter, make sure it's hidden.
            game.uiManager.updateEnemiesLeft(-1)
        }

        // 1. Check if we are in the "start delay" phase.
        if (objectiveTimerStartDelay > 0f) {
            objectiveTimerStartDelay -= deltaTime
            game.uiManager.updateMissionTimer(objective.timerDuration, true)

            // Check if a timer is currently active for this objective.
            if (objectiveTimerStartDelay <= 0f) {
                objectiveTimerStartDelay = -1f
                activeMission?.missionVariables?.set("objective_timer", objective.timerDuration)
                println("Breather finished. Objective timer started for ${objective.timerDuration} seconds.")
            }
        } else {
            // 2. If not in a delay, run the normal timer logic.
            val objectiveTimer = currentMission.missionVariables["objective_timer"] as? Float
            if (objectiveTimer != null && objectiveTimer > 0f) {
                // Determine if the main objective timer should be paused
                val effectiveDeltaTime = if (objective.completionCondition.type == ConditionType.STAY_IN_AREA && stayInAreaGraceTimer > 0f) {
                    0f
                } else {
                    deltaTime // Run the timer normally
                }

                val newTime = objectiveTimer - effectiveDeltaTime
                currentMission.missionVariables["objective_timer"] = newTime
                game.uiManager.updateMissionTimer(newTime)

                if (newTime <= 0f && objective.completionCondition.type != ConditionType.SURVIVE_FOR_TIME
                    && objective.completionCondition.type != ConditionType.STAY_IN_AREA) {
                    println("Mission Failed: Objective timer expired.")
                    failMission()
                    return // Stop processing this frame as the mission is over
                }
            }
        }

        if (objective.completionCondition.type == ConditionType.STAY_IN_AREA) {
            handleStayInAreaObjective(objective, currentMission)
        }

        if (objective.completionCondition.type == ConditionType.MAINTAIN_DISTANCE) {
            handleMaintainDistanceObjective(objective, currentMission)
        }

        if (!isScopeValid(objective, currentMission)) { return }

        if (isObjectiveComplete(objective, currentMission)) {
            advanceObjective()
        }
    }

    fun onPlayerEnteredHouse(houseId: String) {
        if (activeMission != null) return // Don't start a new mission if one is already active

        for ((missionId, missionDef) in allMissions) {
            if (gameState.completedMissionIds.contains(missionId)) continue

            val trigger = missionDef.startTrigger
            if (trigger.type == TriggerType.ON_ENTER_HOUSE && trigger.targetHouseId == houseId) {
                println("Player entered house '$houseId', triggering mission '${missionDef.title}'.")
                startMission(missionId)
                break // Start only one mission per house entry
            }
        }
    }

    fun onPlayerEnteredCar(carId: String) {
        if (activeMission != null) return // Don't start a new mission if one is already active

        for ((missionId, missionDef) in allMissions) {
            if (gameState.completedMissionIds.contains(missionId)) continue

            val trigger = missionDef.startTrigger
            if (trigger.type == TriggerType.ON_ENTER_CAR && trigger.targetCarId == carId) {
                println("Player entered car '$carId', triggering mission '${missionDef.title}'.")
                startMission(missionId)
                break
            }
        }
    }

    fun onPlayerHurtEnemy(enemyId: String) {
        if (activeMission != null) return

        for ((missionId, missionDef) in allMissions) {
            if (gameState.completedMissionIds.contains(missionId)) continue

            val trigger = missionDef.startTrigger
            if (trigger.type == TriggerType.ON_HURT_ENEMY && trigger.targetNpcId == enemyId) {
                println("Player hurt enemy '$enemyId', triggering mission '${missionDef.title}'.")
                startMission(missionId)
                break
            }
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

    private fun reportDialogComplete(dialogId: String) {
        val objective = activeMission?.getCurrentObjective() ?: return

        // Check if the current objective was to complete this specific dialog
        // (This is a simplified way to handle "TALK_TO_NPC")
        if (objective.completionCondition.type == ConditionType.TALK_TO_NPC) {
            // In a more complex system, you'd check targetId here too
            activeMission?.missionVariables?.set("dialog_complete_${objective.completionCondition.targetId}", true)
            println("Reported dialog completion for objective.")
        }
    }

    fun reportItemCollected(itemId: String) {
        val objective = activeMission?.getCurrentObjective() ?: return
        val condition = objective.completionCondition

        // Check if the current objective is to collect a specific item and if the ID matches.
        if (condition.type == ConditionType.COLLECT_SPECIFIC_ITEM && condition.itemId == itemId) {
            // It matches! Set a flag in our mission variables to mark it as complete.
            activeMission?.missionVariables?.set("collected_${itemId}", true)
            println("Player collected mission-critical item: $itemId")
        }
    }

    private fun onObjectiveStarted(objective: MissionObjective) {
        val condition = objective.completionCondition
        if (condition.targetId != null) {
            val targetExists = when (condition.type) {
                // These objectives require a specific entity to exist
                ConditionType.ELIMINATE_TARGET,
                ConditionType.TALK_TO_NPC,
                ConditionType.INTERACT_WITH_OBJECT,
                ConditionType.DESTROY_CAR,
                ConditionType.BURN_DOWN_HOUSE,
                ConditionType.DESTROY_OBJECT,
                ConditionType.DRIVE_TO_LOCATION,
                ConditionType.MAINTAIN_DISTANCE -> {
                    // Check all relevant lists for the target ID
                    game.sceneManager.activeEnemies.any { it.id == condition.targetId } ||
                        game.sceneManager.activeNPCs.any { it.id == condition.targetId } ||
                        game.sceneManager.activeCars.any { it.id == condition.targetId } ||
                        game.sceneManager.activeObjects.any { it.id == condition.targetId } ||
                        game.sceneManager.activeHouses.any { it.id == condition.targetId }
                }
                // For ENTER_AREA, it only matters if we are checking the target's position
                ConditionType.ENTER_AREA -> {
                    if (condition.checkTargetInsteadOfPlayer) {
                        game.sceneManager.activeCars.any { it.id == condition.targetId } ||
                            game.sceneManager.activeEnemies.any { it.id == condition.targetId } ||
                            game.sceneManager.activeNPCs.any { it.id == condition.targetId }
                    } else {
                        true // Not checking a target, so it's valid
                    }
                }
                else -> true // Other objectives don't require a targetId, so they are valid by default
            }

            if (!targetExists) {
                println("Mission Failed: Objective '${objective.description}' cannot start because its target (ID: ${condition.targetId}) does not exist in the scene.")
                failMission()
                return // IMPORTANT: Stop processing the start of this broken objective
            }
        }

        // If the new objective is to eliminate all enemies
        objective.eventsOnStart.forEach { executeEvent(it) }

        // Check if this new objective has a timer and start it.
        if (objective.hasTimer) {
            objectiveTimerStartDelay = 3.0f
            game.uiManager.updateMissionTimer(objective.timerDuration)
            println("Objective has a timer. Starting 3s breather period.")
        } else {
            // If the new objective does NOT have a timer, ensure any old timer is cleared.
            objectiveTimerStartDelay = -1f // Ensure no delay for non-timed objectives
            activeMission?.missionVariables?.remove("objective_timer")
            game.uiManager.updateMissionTimer(-1f) // Tell UI to hide the timer
        }

        // Hide any old markers and show new ones if needed.
        game.objectiveArrowSystem.hide() // Always hide the arrow first

        // Check if the objective is the one that needs the arrow
        if (objective.completionCondition.type == ConditionType.DRIVE_TO_LOCATION) {
            objective.completionCondition.areaCenter?.let { game.objectiveArrowSystem.show(it) }
        }

        if (objective.completionCondition.type == ConditionType.ELIMINATE_ALL_ENEMIES) {

            val enemyIdsForThisObjective = objective.eventsOnStart
                .filter { it.type == GameEventType.SPAWN_ENEMY && it.targetId != null }
                .map { it.targetId!! } // Get a list of the IDs of the enemies we just spawned for this task

            if (enemyIdsForThisObjective.isNotEmpty()) {
                // Store this list of IDs in the active mission's state
                activeMission?.missionVariables?.set("enemies_to_eliminate", enemyIdsForThisObjective)
                println("Objective started: Tracking ${enemyIdsForThisObjective.size} enemies to eliminate.")
            }
        }
    }

    fun reportObjectDestroyed(objectId: String) {
        destroyedObjectIdsThisFrame.add(objectId)
        println("MissionSystem: Reported object destroyed: $objectId")
    }

    fun reportCarDestroyed(carId: String) {
        destroyedCarIdsThisFrame.add(carId) // Add to our new list for start triggers
        println("MissionSystem: Reported car destroyed: $carId")

        // Keep the old logic for completing objectives
        val objective = activeMission?.getCurrentObjective() ?: return
        val condition = objective.completionCondition

        if (condition.type == ConditionType.DRIVE_TO_LOCATION && condition.targetId == carId) {
            println("Mission Failed: Required vehicle (ID: $carId) was destroyed.")
            failMission()
            return // Stop further processing for this objective
        }

        if (condition.type == ConditionType.DESTROY_CAR && condition.targetId == carId) {
            // The objective was to destroy this specific car. Mark it as done.
            activeMission?.missionVariables?.set("destroyed_${carId}", true)
            println("Player destroyed mission-critical car: $carId")
        }
    }

    private fun isObjectiveComplete(objective: MissionObjective, state: MissionState): Boolean {
        val condition = objective.completionCondition
        when (condition.type) {
            ConditionType.ENTER_AREA -> {
                // Safe handling of nullable properties
                val center = condition.areaCenter ?: return false // If center is null, can't complete
                val radius = condition.areaRadius ?: return false // If radius is null, can't complete

                val positionToCheck: Vector3? = if (condition.checkTargetInsteadOfPlayer) {
                    val targetId = condition.targetId ?: return false

                    val targetEntity = game.sceneManager.activeCars.find { it.id == targetId } as Any?
                        ?: game.sceneManager.activeEnemies.find { it.id == targetId }
                        ?: game.sceneManager.activeNPCs.find { it.id == targetId }

                    when (targetEntity) {
                        is GameCar -> targetEntity.position
                        is GameEnemy -> targetEntity.position
                        is GameNPC -> targetEntity.position
                        else -> null
                    }
                } else {
                    game.playerSystem.getControlledEntityPosition()
                }

                if (positionToCheck == null) return false
                return positionToCheck.dst(center) < radius
            }
            ConditionType.STAY_IN_AREA -> {
                val objectiveTimer = state.missionVariables["objective_timer"] as? Float
                return objectiveTimer != null && objectiveTimer <= 0f
            }
            ConditionType.ELIMINATE_TARGET -> {
                // Check if an enemy with the target ID still exists in the scene
                val targetExists = game.sceneManager.activeEnemies.any { it.id == condition.targetId }
                return !targetExists
            }

            ConditionType.ELIMINATE_ALL_ENEMIES -> {
               // 1. Get the list of enemy IDs we are supposed to be tracking for this objective.
                val variable = state.missionVariables["enemies_to_eliminate"]

                if (variable is List<*>) {
                    // 3. Create a new, clean list of Strings.
                    val enemiesToTrack = mutableListOf<String>()

                    // Iterate through the list and safely cast each element
                    for (item in variable) {
                        if (item is String) {
                            enemiesToTrack.add(item)
                        }
                    }

                    // If the list was empty or contained non-strings
                    if (enemiesToTrack.isEmpty()) {
                        return game.sceneManager.activeEnemies.isEmpty
                    }

                    // 2. Check if ANY of the tracked enemies are still present in the active scene.
                    val anyTrackedEnemyAlive = game.sceneManager.activeEnemies.any { enemy ->
                        enemy.id in enemiesToTrack
                    }

                    // 3. The objective is complete if NONE of the tracked enemies are alive anymore.
                    return !anyTrackedEnemyAlive

                } else {
                    return game.sceneManager.activeEnemies.isEmpty
                }
            }
            ConditionType.DESTROY_CAR -> {
                val carId = condition.targetId ?: return false
                // Check the flag that reportCarDestroyed() sets.
                return state.missionVariables["destroyed_${carId}"] == true
            }
            ConditionType.BURN_DOWN_HOUSE -> {
                val houseId = condition.targetId ?: return false

                // Find the target house in the scene.
                val targetHouse = game.sceneManager.activeHouses.find { it.id == houseId } ?: return false

                // Now, check if any active fire is close enough to the house's position.
                val burnRadius = 15f // How close the fire needs to be
                val isBurning = game.fireSystem.activeFires.any { fire ->
                    fire.gameObject.position.dst(targetHouse.position) < burnRadius
                }

                return isBurning
            }
            ConditionType.DESTROY_OBJECT -> {
                val objectId = condition.targetId ?: return false
                val targetExists = game.sceneManager.activeObjects.any { it.id == objectId }
                return !targetExists
            }
            ConditionType.REACH_ALTITUDE -> {
                val targetY = condition.targetAltitude ?: return false
                val playerY = game.playerSystem.getPosition().y
                return playerY >= targetY
            }
            ConditionType.DRIVE_TO_LOCATION -> {
                // 1. Check if the player is actually driving a car. If not, the objective cannot be completed.
                if (!game.playerSystem.isDriving) {
                    return false
                }

                // 2. Get the car the player is currently driving.
                val playerCar = game.playerSystem.drivingCar ?: return false

                // 3. (Optional) Check if a *specific* car is required for this objective.
                val requiredCarId = condition.targetId
                if (requiredCarId != null && playerCar.id != requiredCarId) {
                    // The player is driving, but it's the wrong car.
                    return false
                }

                // 4. Get the destination area from the condition.
                val destinationCenter = condition.areaCenter ?: return false
                val destinationRadius = condition.areaRadius ?: return false

                // 5. Check if the car's position is within the destination radius.
                val distance = playerCar.position.dst(destinationCenter)
                return distance < destinationRadius
            }
            ConditionType.SURVIVE_FOR_TIME -> {
                val timer = state.missionVariables["objective_timer"] as? Float ?: return false
                // The objective is complete if the timer has run out (or gone below zero).
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
            ConditionType.COLLECT_SPECIFIC_ITEM -> {
                val requiredId = condition.itemId ?: return false
                // Check the flag we set in reportItemCollected()
                return state.missionVariables["collected_${requiredId}"] == true
            }
            ConditionType.MAINTAIN_DISTANCE -> {
                // This objective is complete ONLY when the conditions at the destination are met.
                val targetId = condition.targetId ?: return false
                val center = condition.areaCenter ?: return false
                val radius = condition.areaRadius ?: return false

                // --- 1. Find the target entity and its position ---
                val targetEntity = game.sceneManager.activeCars.find { it.id == targetId } as Any?
                    ?: game.sceneManager.activeEnemies.find { it.id == targetId }
                    ?: game.sceneManager.activeNPCs.find { it.id == targetId }

                if (targetEntity == null) return false // Target must exist to complete the objective

                val targetPos = when (targetEntity) {
                    is GameCar -> targetEntity.position
                    is GameEnemy -> targetEntity.position
                    is GameNPC -> targetEntity.position
                    else -> return false
                }

                // --- 2. Check if the TARGET is in the destination area ---
                val isTargetAtDestination = targetPos.dst(center) < radius
                if (!isTargetAtDestination) {
                    return false // If the target isn't there yet, the objective can't be complete.
                }

                // --- 3. Check if the PLAYER also needs to be at the destination ---
                if (condition.requirePlayerAtDestination) {
                    val playerPos = game.playerSystem.getControlledEntityPosition()
                    val isPlayerAtDestination = playerPos.dst(center) < radius

                    // If the player is required, BOTH must be at the destination.
                    return isTargetAtDestination && isPlayerAtDestination
                } else {
                    // If the player is NOT required, only the target needs to be at the destination.
                    return isTargetAtDestination
                }
            }
        }
    }

    fun startMission(id: String) {
        if (activeMission != null || gameState.completedMissionIds.contains(id)) {
            return // Don't start a mission if one is active or it's already done
        }

        val missionDef = allMissions[id] ?: return

        // Trigger UI notification
        game.uiManager.showMissionStartNotification(missionDef.title)

        // Take an inventory snapshot if this mission modifies it
        val modifiesInventory = missionDef.eventsOnStart.any {
            it.type in listOf(GameEventType.CLEAR_INVENTORY, GameEventType.GIVE_WEAPON, GameEventType.FORCE_EQUIP_WEAPON)
        } || missionDef.modifiers.disableWeaponSwitching

        if (modifiesInventory) {
            println("This mission modifies the player's inventory. Taking a snapshot.")
            playerInventorySnapshot = game.playerSystem.createStateDataSnapshot()
        }

        println("--- MISSION STARTED: ${missionDef.title} ---")
        activeMission = MissionState(missionDef)

        // Activate the modifiers for this mission
        activeModifiers = missionDef.modifiers
        println("Activating mission modifiers: Invincible=${activeModifiers?.setUnlimitedHealth}")

        missionDef.eventsOnStart.forEach { executeEvent(it) }

        // After starting the mission
        missionDef.objectives.firstOrNull()?.let { firstObjective ->
            onObjectiveStarted(firstObjective)
        }

        updateUIForCurrentObjective()
    }

    private fun advanceObjective() {
        val missionState = activeMission ?: return

        // Check for and execute the completion action
        val completedObjective = missionState.getCurrentObjective()
        if (completedObjective?.completionCondition?.type == ConditionType.MAINTAIN_DISTANCE) {
            executeMaintainDistanceCompletionAction(completedObjective.completionCondition)
        }

        if (stayInAreaGraceTimer > 0f) {
            stayInAreaGraceTimer = -1f
            game.uiManager.updateReturnToAreaTimer(-1f) // Hide the timer
        }

        // When an objective is completed, clear its timer from the state.
        missionState.missionVariables.remove("objective_timer")
        objectiveTimerStartDelay = -1f // Also clear the start delay
        game.uiManager.updateMissionTimer(-1f) // Hide the UI timer
        game.uiManager.updateEnemiesLeft(-1)

        missionState.currentObjectiveIndex++

        val nextObjective = missionState.getCurrentObjective()
        if (nextObjective == null) {
            endMission(true) // Mission is complete
        } else {
            println("Objective complete! New objective: ${nextObjective.description}")
            onObjectiveStarted(nextObjective)
            updateUIForCurrentObjective()
        }
    }

    private fun executeMaintainDistanceCompletionAction(condition: CompletionCondition) {
        val action = condition.maintainDistanceCompletionAction ?: return // Do nothing if no action is set
        val targetId = condition.targetId ?: return

        // Find the target car
        val targetCar = game.sceneManager.activeCars.find { it.id == targetId } ?: return

        // Find the driver inside the car
        val driver = targetCar.seats.firstNotNullOfOrNull { it.occupant }
        val enemyDriver = driver as? GameEnemy
        val npcDriver = driver as? GameNPC

        println("Executing completion action '${action.name}' for car '$targetId'")

        when (action) {
            MaintainDistanceCompletionAction.STOP_AND_STAY_IN_CAR -> {
                enemyDriver?.currentState = AIState.IDLE
                npcDriver?.currentState = NPCState.IDLE
            }
            MaintainDistanceCompletionAction.STOP_AND_EXIT_CAR -> {
                if (enemyDriver != null) {
                    game.enemySystem.handleCarExit(enemyDriver, game.sceneManager)
                    enemyDriver.currentState = AIState.IDLE // Set to idle after exiting
                }
                if (npcDriver != null) {
                    game.npcSystem.handleCarExit(npcDriver, game.sceneManager)
                    npcDriver.currentState = NPCState.IDLE // Set to idle after exiting
                }
            }
            MaintainDistanceCompletionAction.CONTINUE_PATROLLING -> {
                if (enemyDriver != null) {
                    enemyDriver.currentState = AIState.PATROLLING_IN_CAR
                }
                if (npcDriver != null) {
                    npcDriver.currentState = NPCState.PATROLLING_IN_CAR
                }
            }
        }
    }

    private fun endMission(completed: Boolean) {
        val mission = activeMission ?: return
        val endedMissionId = mission.definition.id // Get ID before we null it out

        if (completed) {
            println("--- MISSION COMPLETE: ${mission.definition.title} ---")
            gameState.completedMissionIds.add(mission.definition.id)

            // Execute mission-complete events and grant rewards
            mission.definition.eventsOnComplete.forEach { executeEvent(it) }
            mission.definition.rewards.forEach { grantReward(it) }

        } else {
           // println("--- MISSION FAILED: ${mission.definition.title} ---")
            // TODO: Reset any changes made by the mission
        }
        playerInventorySnapshot?.let {
            game.playerSystem.loadState(it)
            playerInventorySnapshot = null // Clear the snapshot
        }

        activeModifiers?.let {
            // Reset player speed back to its default value if it was changed.
            game.playerSystem.physicsComponent.speed = game.playerSystem.basePlayerSpeed
        }

        game.uiManager.updateEnemiesLeft(-1)

        // Deactivate modifiers when the mission ends
        println("Deactivating mission modifiers.")
        activeModifiers = null

        // Clear UI elements
        game.uiManager.updateMissionObjective("")
        game.uiManager.updateMissionTimer(-1f)
        game.uiManager.updateLeaveCarTimer(-1f)

        // Always hide the objective arrow when a mission ends.
        game.objectiveArrowSystem.hide()

        // Reset internal timer states
        leaveCarTimer = -1f
        requiredCarIdForTimer = null
        objectiveTimerStartDelay = -1f

        stayInAreaGraceTimer = -1f
        game.uiManager.updateReturnToAreaTimer(-1f)
        game.sceneManager.cleanupMissionEntities(endedMissionId)

        activeMission = null
    }

    private fun failMission() {
        // --- NEW LOGIC: CHECK FOR ON_MISSION_FAILED TRIGGERS ---
        val failedMissionId = activeMission?.definition?.id

        // End the current mission first to clean everything up.
        // This is important so the new mission can start in a clean state.
        endMission(completed = false)

        // Now, if a mission actually failed (not just a force-end), check for consequences.
        if (failedMissionId != null) {
            // Check all available missions to see if any should be triggered by this failure.
            for ((missionIdToStart, missionDef) in allMissions) {
                // Skip missions that are already completed or active (though none should be active here)
                if (gameState.completedMissionIds.contains(missionIdToStart) || isMissionActive(missionIdToStart)) {
                    continue
                }

                val trigger = missionDef.startTrigger
                if (trigger.type == TriggerType.ON_MISSION_FAILED && trigger.targetNpcId == failedMissionId) {
                    println("Mission '$failedMissionId' failed, triggering consequence mission '${missionDef.title}'.")
                    startMission(missionIdToStart)
                    // We break here to only start one consequence mission per failure.
                    break
                }
            }
        }

        game.uiManager.showTemporaryMessage("Mission Failed!")
        // Use the existing endMission logic to handle cleanup
    }

    fun playerExitedCar(carId: String) {
        val objective = activeMission?.getCurrentObjective() ?: return

        if (objective.completionCondition.type == ConditionType.DRIVE_TO_LOCATION &&
            objective.completionCondition.targetId == carId) {

            println("Player exited a required mission car ($carId). Starting 10-second timer.")
            leaveCarTimer = 10f
            requiredCarIdForTimer = carId
        }
    }

    fun playerEnteredCar(carId: String) {
        // If the player enters the specific car the timer is running for, cancel the timer.
        if (leaveCarTimer > 0f && carId == requiredCarIdForTimer) {
            println("Player re-entered the required car. Timer cancelled.")
            leaveCarTimer = -1f
            requiredCarIdForTimer = null
            game.uiManager.updateLeaveCarTimer(leaveCarTimer) // Tell UI to hide the timer
        }
    }

    fun reportInteraction(objectId: String) {
        val objective = activeMission?.getCurrentObjective() ?: return
        if (objective.completionCondition.type == ConditionType.INTERACT_WITH_OBJECT &&
            objective.completionCondition.targetId == objectId) {
            activeMission?.missionVariables?.set("interacted_$objectId", true)
            println("Player interacted with mission-critical object: $objectId")
        }
    }

    fun startMissionDialog(missionDef: MissionDefinition) {
        val dialogId = missionDef.startTrigger.dialogId ?: return
        val dialogSequence = dialogueManager.getDialogue(dialogId)

        if (dialogSequence == null) {
            println("ERROR: Mission '${missionDef.title}' wants to start dialog '$dialogId', but it was not found.")
            // As a fallback, start the mission directly without dialogue.
            startMission(missionDef.id)
            return
        }

        // Create a new DialogSequence where the onComplete lambda starts the mission.
        val sequenceWithCallback = dialogSequence.copy(
            onComplete = {
                println("Dialogue for '${missionDef.title}' finished. Starting mission now.")
                startMission(missionDef.id)
            }
        )

        game.uiManager.dialogSystem.startDialog(sequenceWithCallback)
    }

    fun checkTalkToNpcObjective(npcId: String): Boolean {
        // Get the active mission state first
        val currentMission = activeMission ?: return false
        val objective = currentMission.getCurrentObjective() ?: return false
        val condition = objective.completionCondition

        if (condition.type == ConditionType.TALK_TO_NPC && condition.targetId == npcId) {
            // The objective matches!

            // Check if a specific dialogue is linked to this objective.
            if (!condition.dialogId.isNullOrBlank()) {
                val dialogSequence = dialogueManager.getDialogue(condition.dialogId)
                if (dialogSequence != null) {
                    // We found a dialogue! Create a new sequence with an onComplete callback.
                    val sequenceWithCallback = dialogSequence.copy(
                        onComplete = {
                            println("Objective dialogue finished. Completing objective.")
                            // Mark the objective as complete and advance the mission state.
                            currentMission.missionVariables["dialog_complete_${condition.targetId}"] = true
                            if (isObjectiveComplete(objective, currentMission)) {
                                advanceObjective()
                            }
                        }
                    )
                    game.uiManager.dialogSystem.startDialog(sequenceWithCallback)
                } else {
                    println("ERROR: Objective tried to start dialog '${condition.dialogId}', but it was not found.")
                    // Fallback: complete the objective anyway so the mission doesn't get stuck.
                    currentMission.missionVariables["dialog_complete_${condition.targetId}"] = true
                    if (isObjectiveComplete(objective, currentMission)) {
                        advanceObjective()
                    }
                }
            } else {
                // Access the missionVariables map through the 'currentMission' object
                currentMission.missionVariables["dialog_complete_${condition.targetId}"] = true

                if (isObjectiveComplete(objective, currentMission)) {
                    advanceObjective()
                }
            }

            return true
        }

        return false
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
            RewardType.UPGRADE_SPAWNER_WEAPON -> {
                val targetEnemy = reward.spawnerTargetEnemyType
                val newWeapon = reward.newDefaultWeapon
                if (targetEnemy != null && newWeapon != null) {
                    println("Applying spawner upgrade: All ${targetEnemy.displayName} will now spawn with ${newWeapon.displayName}.")
                    val allSpawners = game.sceneManager.activeSpawners + (game.sceneManager.worldState?.spawners ?: Array())
                    allSpawners.filter { it.spawnerType == SpawnerType.ENEMY && it.enemyType == targetEnemy }
                        .forEach { it.upgradedWeapon = newWeapon }
                }
            }

            else -> println("Reward type ${reward.type} not yet implemented.")
        }
        println("Granted reward: ${reward.type}")
    }

    fun getAllDialogueIds(): List<String> {
        // We get the list from the dialogueLoader. The keys of its map are the IDs.
        return dialogueManager.getAllDialogueIds()
    }

    private fun executeEvent(event: GameEvent) {
        val missionId = if (event.keepAfterMission) null else activeMission?.definition?.id
        println("Executing mission event: ${event.type} for scene '${event.sceneId ?: "WORLD"}' (Keep after mission: ${event.keepAfterMission}, Assigned missionId: $missionId)")

        val targetSceneId = event.sceneId ?: "WORLD"
        val currentSceneId = if (game.sceneManager.currentScene == SceneType.HOUSE_INTERIOR) {
            game.sceneManager.getCurrentHouse()?.id
        } else {
            "WORLD"
        }

        fun <T> addEntityToScene(
            entity: T,
            activeList: com.badlogic.gdx.utils.Array<T>,
            worldListProvider: () -> com.badlogic.gdx.utils.Array<T>?,
            interiorListProvider: (String) -> com.badlogic.gdx.utils.Array<T>?
        ) {
            // Always add the entity to the currently active list so it appears immediately.
            activeList.add(entity)

            // If the entity is permanent (missionId is null), we must also add it to the correct persistent state.
            if (missionId == null) {
                if (targetSceneId == "WORLD") {
                    game.sceneManager.worldState?.let {
                        worldListProvider()?.add(entity)
                        println("Added permanent entity to WORLD state.")
                    }
                } else {
                    game.sceneManager.interiorStates[targetSceneId]?.let {
                        interiorListProvider(targetSceneId)?.add(entity)
                        println("Added permanent entity to INTERIOR state for scene '$targetSceneId'.")
                    }
                }
            }
        }

        when (event.type) {
            GameEventType.SPAWN_ENEMY -> {
                if (event.enemyType != null && event.spawnPosition != null) {
                    val config = EnemySpawnConfig(
                        enemyType = event.enemyType,
                        behavior = event.enemyBehavior ?: EnemyBehavior.STATIONARY_SHOOTER,
                        position = event.spawnPosition,
                        id = event.targetId,
                        assignedPathId = event.assignedPathId,
                        healthSetting = event.healthSetting ?: HealthSetting.FIXED_DEFAULT,
                        customHealthValue = event.customHealthValue ?: event.enemyType.baseHealth,
                        minRandomHealth = event.minRandomHealth ?: (event.enemyType.baseHealth * 0.8f),
                        maxRandomHealth = event.maxRandomHealth ?: (event.enemyType.baseHealth * 1.2f),
                        initialWeapon = event.initialWeapon ?: WeaponType.UNARMED,
                        ammoSpawnMode = event.ammoSpawnMode ?: AmmoSpawnMode.FIXED,
                        setAmmoValue = event.setAmmoValue ?: 30,
                        weaponCollectionPolicy = event.weaponCollectionPolicy ?: WeaponCollectionPolicy.CANNOT_COLLECT,
                        canCollectItems = event.canCollectItems ?: true
                    )
                    game.enemySystem.createEnemy(config)?.let { newEnemy ->
                        newEnemy.missionId = missionId
                        addEntityToScene(newEnemy, game.sceneManager.activeEnemies, { game.sceneManager.worldState?.enemies }) {
                            game.sceneManager.interiorStates[it]?.enemies
                        }
                    }
                }
            }
            GameEventType.SPAWN_NPC -> {
                if (event.npcType != null && event.spawnPosition != null) {
                    val config = NPCSpawnConfig(
                        npcType = event.npcType,
                        behavior = event.npcBehavior ?: NPCBehavior.STATIONARY,
                        position = event.spawnPosition,
                        id = event.targetId,
                        pathFollowingStyle = event.pathFollowingStyle ?: PathFollowingStyle.CONTINUOUS
                    )
                    game.npcSystem.createNPC(config, event.npcRotation ?: 0f)?.let { newNpc ->
                        newNpc.missionId = missionId
                        addEntityToScene(newNpc, game.sceneManager.activeNPCs, { game.sceneManager.worldState?.npcs }) {
                            game.sceneManager.interiorStates[it]?.npcs
                        }
                    }
                }
            }
            GameEventType.SPAWN_CAR -> {
                if (event.carType != null && event.spawnPosition != null) {
                    if (targetSceneId != "WORLD") return

                    val carInstance = game.carSystem.createCarInstance(event.carType) ?: return
                    val newCar = GameCar(
                        modelInstance = carInstance, carType = event.carType, position = event.spawnPosition,
                        sceneManager = game.sceneManager, isLocked = event.carIsLocked,
                        health = event.carType.baseHealth, initialVisualRotation = event.houseRotationY ?: 0f,
                        missionId = missionId
                    )
                    event.targetId?.let { newCar.id = it }

                    var driver: Any? = null

                    when (event.carDriverType) {
                        "Enemy" -> {
                            if (event.carEnemyDriverType == null) {
                                println("Mission Failed: SPAWN_CAR event requested an Enemy driver but no Enemy Type was specified.")
                                failMission()
                                return // Stop executing this broken event
                            }
                            driver = game.enemySystem.createEnemy(EnemySpawnConfig(event.carEnemyDriverType, EnemyBehavior.AGGRESSIVE_RUSHER, newCar.position))
                        }
                        "NPC" -> {
                            if (event.carNpcDriverType == null) {
                                println("Mission Failed: SPAWN_CAR event requested an NPC driver but no NPC Type was specified.")
                                failMission()
                                return // Stop executing this broken event
                            }
                            driver = game.npcSystem.createNPC(NPCSpawnConfig(event.carNpcDriverType, NPCBehavior.WANDER, newCar.position))
                        }
                    }

                    addEntityToScene(newCar, game.sceneManager.activeCars, { game.sceneManager.worldState?.cars }, { null })

                    // Create a local, non-changing copy of the driver for the smart cast to work.
                    when (val finalDriver = driver) {
                        is GameEnemy -> {
                            finalDriver.missionId = missionId
                            finalDriver.enterCar(newCar)
                            finalDriver.currentState = AIState.PATROLLING_IN_CAR
                            addEntityToScene(finalDriver, game.sceneManager.activeEnemies, { game.sceneManager.worldState?.enemies }, { null })
                        }
                        is GameNPC -> {
                            finalDriver.missionId = missionId
                            finalDriver.enterCar(newCar)
                            finalDriver.currentState = NPCState.PATROLLING_IN_CAR
                            addEntityToScene(finalDriver, game.sceneManager.activeNPCs, { game.sceneManager.worldState?.npcs }, { null })
                        }
                    }
                }
            }
            GameEventType.SPAWN_ITEM, GameEventType.SPAWN_MONEY_STACK -> {
                if (event.itemType != null && event.spawnPosition != null) {
                    game.itemSystem.createItem(event.spawnPosition, event.itemType)?.let { newItem ->
                        newItem.missionId = missionId
                        if (event.type == GameEventType.SPAWN_MONEY_STACK) newItem.value = event.itemValue
                        event.targetId?.let { newItem.id = it }
                        addEntityToScene(newItem, game.sceneManager.activeItems, { game.sceneManager.worldState?.items }) {
                            game.sceneManager.interiorStates[it]?.items
                        }
                    }
                }
            }
            GameEventType.SPAWN_BLOCK -> {
                if (event.blockType != null && event.spawnPosition != null) {
                    val newBlock = game.blockSystem.createGameBlock(
                        type = event.blockType,
                        shape = event.blockShape ?: BlockShape.FULL_BLOCK,
                        position = event.spawnPosition,
                        geometryRotation = event.blockRotationY ?: 0f,
                        textureRotation = event.blockTextureRotationY ?: 0f,
                        topTextureRotation = event.blockTopTextureRotationY ?: 0f
                    ).copy(cameraVisibility = event.blockCameraVisibility ?: CameraVisibility.ALWAYS_VISIBLE)

                    newBlock.missionId = missionId

                    val targetChunkManager = when (targetSceneId) {
                        currentSceneId -> game.sceneManager.activeChunkManager
                        "WORLD" -> game.sceneManager.worldChunkManager
                        else -> game.sceneManager.interiorChunkManagers[targetSceneId]
                    }

                    targetChunkManager?.addBlock(newBlock) ?: println("ERROR: Could not find ChunkManager for scene '$targetSceneId' to spawn block.")
                }
            }
            GameEventType.DESPAWN_BLOCK_AT_POS -> {
                if (event.spawnPosition != null) {
                    // We reuse the 'spawnPosition' field to specify the location of the block to remove.
                    val blockToRemove = game.sceneManager.activeChunkManager.getBlockAtWorld(event.spawnPosition)
                    if (blockToRemove != null) {
                        game.sceneManager.removeBlock(blockToRemove)
                        // IMPORTANT: We must tell the chunk to rebuild its visual mesh after removing a block.
                        game.sceneManager.activeChunkManager.processDirtyChunks()
                        println("Mission event despawned block at ${event.spawnPosition}")
                    } else {
                        println("Warning: DESPAWN_BLOCK_AT_POS event failed. No block found at ${event.spawnPosition}")
                    }
                }
            }
            GameEventType.SPAWN_HOUSE -> {
                if (event.houseType != null && event.spawnPosition != null) {
                    if (targetSceneId != "WORLD") {
                        println("WARNING: Mission event tried to spawn a house in an interior ('$targetSceneId'). Aborting.")
                        return
                    }
                    game.houseSystem.currentRotation = event.houseRotationY ?: 0f
                    val newHouse = game.houseSystem.addHouse(event.spawnPosition.x, event.spawnPosition.y, event.spawnPosition.z, event.houseType, event.houseIsLocked ?: false)
                    if (newHouse != null) {
                        newHouse.missionId = missionId
                        event.targetId?.let { newHouse.id = it }
                        addEntityToScene(newHouse, game.sceneManager.activeHouses, { game.sceneManager.worldState?.houses }, { null })
                    }
                }
            }
            GameEventType.LOCK_HOUSE -> {
                event.targetId?.let { houseId ->
                    // Find the house in the active scene OR in the saved world state
                    val house = game.sceneManager.activeHouses.find { it.id == houseId }
                        ?: game.sceneManager.worldState?.houses?.find { it.id == houseId }

                    if (house != null) {
                        // To change the 'isLocked' property, we need to replace the data class instance
                        val updatedHouse = house.copy(isLocked = true)

                        // Find and replace it in the correct list
                        if (game.sceneManager.activeHouses.removeValue(house, true)) {
                            game.sceneManager.activeHouses.add(updatedHouse)
                        } else {
                            game.sceneManager.worldState?.houses?.let {
                                if (it.removeValue(house, true)) it.add(updatedHouse)
                            }
                        }
                        println("Mission event locked house: $houseId")
                    } else {
                        println("Warning: LOCK_HOUSE event failed. No house found with ID: $houseId")
                    }
                }
            }
            GameEventType.UNLOCK_HOUSE -> {
                event.targetId?.let { houseId ->
                    val house = game.sceneManager.activeHouses.find { it.id == houseId }
                        ?: game.sceneManager.worldState?.houses?.find { it.id == houseId }

                    if (house != null) {
                        val updatedHouse = house.copy(isLocked = false)
                        if (game.sceneManager.activeHouses.removeValue(house, true)) {
                            game.sceneManager.activeHouses.add(updatedHouse)
                        } else {
                            game.sceneManager.worldState?.houses?.let {
                                if (it.removeValue(house, true)) it.add(updatedHouse)
                            }
                        }
                        println("Mission event unlocked house: $houseId")
                    } else {
                        println("Warning: UNLOCK_HOUSE event failed. No house found with ID: $houseId")
                    }
                }
            }
            GameEventType.SPAWN_OBJECT -> {
                if (event.objectType != null && event.spawnPosition != null) {
                    // Handle light sources separately
                    if (event.objectType == ObjectType.LIGHT_SOURCE) {
                        val light = game.objectSystem.createLightSource(
                            position = event.spawnPosition,
                            intensity = event.lightIntensity ?: LightSource.DEFAULT_INTENSITY,
                            range = event.lightRange ?: LightSource.DEFAULT_RANGE,
                            color = event.lightColor ?: Color.WHITE,
                            flickerMode = event.flickerMode ?: FlickerMode.NONE,
                            loopOnDuration = event.loopOnDuration ?: 0.1f,
                            loopOffDuration = event.loopOffDuration ?: 0.2f,
                            timedFlickerLifetime = event.lifetime ?: 10f
                        )
                        light.missionId = missionId // missionId will be null if keepAfterMission is true

                        // 2. Add to the active scene IF it's the current scene
                        if (targetSceneId == currentSceneId) {
                            val instances = game.objectSystem.createLightSourceInstances(light)
                            game.lightingManager.addLightSource(light, instances)
                        }

                        if (missionId == null) { // This means keepAfterMission was true
                            if (targetSceneId == "WORLD") {
                                game.sceneManager.worldState?.lights?.put(light.id, light)
                                println("Added permanent light source #${light.id} to world state map.")
                            } else {
                                // Logic for adding to a persistent interior state if needed
                                game.sceneManager.interiorStates[targetSceneId]?.lights?.put(light.id, light)
                                println("Added permanent light source #${light.id} to interior state map for scene '$targetSceneId'.")
                            }
                        }
                    } else {
                        game.objectSystem.createGameObjectWithLight(event.objectType, event.spawnPosition, if (targetSceneId == currentSceneId) game.lightingManager else null)?.let { newObject ->
                            newObject.missionId = missionId
                            event.targetId?.let { newObject.id = it }
                            addEntityToScene(newObject, game.sceneManager.activeObjects, { game.sceneManager.worldState?.objects }) {
                                game.sceneManager.interiorStates[it]?.objects
                            }
                        }
                    }
                }
            }
            GameEventType.SPAWN_SPAWNER -> {
                if (event.spawnPosition != null) {
                    val spawnerGameObject = game.objectSystem.createGameObjectWithLight(ObjectType.SPAWNER, event.spawnPosition.cpy())
                    if (spawnerGameObject != null) {
                        spawnerGameObject.debugInstance?.transform?.setTranslation(event.spawnPosition)
                        val newSpawner = GameSpawner(
                            id = event.targetId ?: UUID.randomUUID().toString(),
                            position = event.spawnPosition.cpy(),
                            gameObject = spawnerGameObject
                        )
                        newSpawner.missionId = missionId

                        // Load all saved spawner properties from the event
                        newSpawner.spawnerType = event.spawnerType ?: newSpawner.spawnerType
                        newSpawner.spawnerMode = event.spawnerMode ?: newSpawner.spawnerMode
                        newSpawner.spawnInterval = event.spawnInterval ?: newSpawner.spawnInterval
                        newSpawner.minSpawnRange = event.minSpawnRange ?: newSpawner.minSpawnRange
                        newSpawner.maxSpawnRange = event.maxSpawnRange ?: newSpawner.maxSpawnRange
                        newSpawner.spawnOnlyWhenPreviousIsGone = event.spawnOnlyWhenPreviousIsGone ?: false

                        // Type-specific settings
                        newSpawner.particleEffectType = event.particleEffectType ?: newSpawner.particleEffectType
                        newSpawner.minParticles = event.spawnerMinParticles ?: newSpawner.minParticles
                        newSpawner.maxParticles = event.spawnerMaxParticles ?: newSpawner.maxParticles
                        newSpawner.itemType = event.spawnerItemType ?: newSpawner.itemType
                        newSpawner.minItems = event.spawnerMinItems ?: newSpawner.minItems
                        newSpawner.maxItems = event.spawnerMaxItems ?: newSpawner.maxItems
                        newSpawner.weaponItemType = event.spawnerWeaponItemType ?: newSpawner.weaponItemType
                        newSpawner.ammoSpawnMode = event.ammoSpawnMode ?: newSpawner.ammoSpawnMode
                        newSpawner.setAmmoValue = event.setAmmoValue ?: newSpawner.setAmmoValue
                        newSpawner.randomMinAmmo = event.randomMinAmmo ?: newSpawner.randomMinAmmo
                        newSpawner.randomMaxAmmo = event.randomMaxAmmo ?: newSpawner.randomMaxAmmo
                        newSpawner.enemyType = event.spawnerEnemyType ?: newSpawner.enemyType
                        newSpawner.npcType = event.spawnerNpcType ?: newSpawner.npcType
                        newSpawner.carType = event.spawnerCarType ?: newSpawner.carType
                        newSpawner.carIsLocked = event.spawnerCarIsLocked ?: newSpawner.carIsLocked
                        newSpawner.carDriverType = event.spawnerCarDriverType ?: newSpawner.carDriverType
                        newSpawner.carEnemyDriverType = event.spawnerCarEnemyDriverType ?: newSpawner.carEnemyDriverType
                        newSpawner.carNpcDriverType = event.spawnerCarNpcDriverType ?: newSpawner.carNpcDriverType
                        newSpawner.carSpawnDirection = event.spawnerCarSpawnDirection ?: newSpawner.carSpawnDirection

                        addEntityToScene(newSpawner, game.sceneManager.activeSpawners, { game.sceneManager.worldState?.spawners }) {
                            game.sceneManager.interiorStates[it]?.spawners
                        }
                    }
                }
            }
            GameEventType.ENABLE_SPAWNER -> {
                event.targetId?.let { spawnerId ->
                    val spawner = game.sceneManager.activeSpawners.find { it.id == spawnerId }
                    if (spawner != null) {
                        spawner.isDepleted = false // Re-enable it
                        spawner.timer = spawner.spawnInterval // Reset its timer
                        println("Mission event enabled spawner: $spawnerId")
                    } else {
                        println("Warning: ENABLE_SPAWNER event failed. No spawner found with ID: $spawnerId")
                    }
                }
            }
            GameEventType.DISABLE_SPAWNER -> {
                event.targetId?.let { spawnerId ->
                    val spawner = game.sceneManager.activeSpawners.find { it.id == spawnerId }
                    if (spawner != null) {
                        spawner.isDepleted = true // This effectively disables it
                        println("Mission event disabled spawner: $spawnerId")
                    } else {
                        println("Warning: DISABLE_SPAWNER event failed. No spawner found with ID: $spawnerId")
                    }
                }
            }
            GameEventType.SPAWN_TELEPORTER -> {
                if (event.spawnPosition != null) {
                    val gameObject = game.objectSystem.createGameObjectWithLight(ObjectType.TELEPORTER, event.spawnPosition.cpy())
                    if (gameObject != null) {
                        gameObject.modelInstance.transform.setTranslation(event.spawnPosition)
                        gameObject.debugInstance?.transform?.setTranslation(event.spawnPosition)
                        val newTeleporter = GameTeleporter(
                            id = event.teleporterId ?: UUID.randomUUID().toString(),
                            gameObject = gameObject,
                            linkedTeleporterId = event.linkedTeleporterId,
                            name = event.teleporterName ?: "Teleporter"
                        )
                        newTeleporter.missionId = missionId
                        addEntityToScene(newTeleporter, game.teleporterSystem.activeTeleporters, { game.sceneManager.worldState?.teleporters }) {
                            game.sceneManager.interiorStates[it]?.teleporters
                        }
                    }
                }
            }
            GameEventType.SPAWN_FIRE -> {
                if (event.spawnPosition != null) {
                    // Configure fire system
                    val fireSystem = game.fireSystem

                    // Save the FireSystem's current settings so this event doesn't mess up the user's editor selection
                    val originalLooping = fireSystem.nextFireIsLooping
                    val originalFadesOut = fireSystem.nextFireFadesOut
                    val originalLifetime = fireSystem.nextFireLifetime
                    val originalCanBeExtinguished = fireSystem.nextFireCanBeExtinguished
                    val originalDealsDamage = fireSystem.nextFireDealsDamage
                    val originalDamagePerSecond = fireSystem.nextFireDamagePerSecond
                    val originalDamageRadius = fireSystem.nextFireDamageRadius
                    val originalMinScale = fireSystem.nextFireMinScale
                    val originalMaxScale = fireSystem.nextFireMaxScale

                    // Configure the FireSystem for this specific event's fire
                    fireSystem.nextFireIsLooping = event.isLooping ?: true
                    fireSystem.nextFireFadesOut = event.fadesOut ?: false
                    fireSystem.nextFireLifetime = event.lifetime ?: 20f
                    fireSystem.nextFireCanBeExtinguished = event.canBeExtinguished ?: true
                    fireSystem.nextFireDealsDamage = event.dealsDamage ?: true
                    fireSystem.nextFireDamagePerSecond = event.damagePerSecond ?: 10f
                    fireSystem.nextFireDamageRadius = event.damageRadius ?: 5f
                    fireSystem.nextFireMinScale = event.fireScale ?: 1f
                    fireSystem.nextFireMaxScale = event.fireScale ?: 1f

                    // Create the fire using the dedicated system, now passing the existing light ID
                    val newFire = fireSystem.addFire(
                        position = event.spawnPosition.cpy(),
                        objectSystem = game.objectSystem,
                        lightingManager = game.lightingManager,
                        generation = event.generation ?: 0,
                        canSpread = event.canSpread ?: false,
                        id = event.targetId ?: UUID.randomUUID().toString(),
                        existingAssociatedLightId = event.associatedLightId // <-- THE FIX IS HERE!
                    )

                    if (newFire != null) {
                        newFire.missionId = missionId
                        addEntityToScene(newFire, game.fireSystem.activeFires, { game.sceneManager.worldState?.fires }) {
                            game.sceneManager.interiorStates[it]?.fires
                        }
                        addEntityToScene(newFire.gameObject, game.sceneManager.activeObjects, { game.sceneManager.worldState?.objects }) {
                            game.sceneManager.interiorStates[it]?.objects
                        }
                    }

                    // Restore original settings to the FireSystem so manual placement isn't affected
                    fireSystem.nextFireIsLooping = originalLooping
                    fireSystem.nextFireFadesOut = originalFadesOut
                    fireSystem.nextFireLifetime = originalLifetime
                    fireSystem.nextFireCanBeExtinguished = originalCanBeExtinguished
                    fireSystem.nextFireDealsDamage = originalDealsDamage
                    fireSystem.nextFireDamagePerSecond = originalDamagePerSecond
                    fireSystem.nextFireDamageRadius = originalDamageRadius
                    fireSystem.nextFireMinScale = originalMinScale
                    fireSystem.nextFireMaxScale = originalMaxScale
                }
            }
            GameEventType.DESPAWN_ENTITY -> {
                event.targetId?.let { id ->
                    // Despawning only makes sense in the currently active scene.
                    var wasRemoved = false
                    if (game.sceneManager.activeEnemies.removeAll { it.id == id }) wasRemoved = true
                    if (game.sceneManager.activeNPCs.removeAll { it.id == id }) wasRemoved = true
                    if (game.sceneManager.activeCars.removeAll { it.id == id }) wasRemoved = true
                    if (game.sceneManager.activeItems.removeAll { it.id == id }) wasRemoved = true
                    if (game.sceneManager.activeObjects.removeAll { it.id == id }) wasRemoved = true
                    if (game.sceneManager.activeSpawners.removeAll { it.id == id }) wasRemoved = true

                    if (game.carPathSystem.nodes.remove(id) != null) wasRemoved = true
                    if (game.characterPathSystem.nodes.remove(id) != null) wasRemoved = true

                    if (game.sceneManager.activeHouses.removeAll { it.id == id }) wasRemoved = true

                    val fireToRemove = game.fireSystem.activeFires.find { it.id == id }
                    if (fireToRemove != null) {
                        game.fireSystem.removeFire(fireToRemove, game.objectSystem, game.lightingManager)
                        wasRemoved = true
                    }

                    val teleporterToRemove = game.teleporterSystem.activeTeleporters.find { it.id == id }
                    if (teleporterToRemove != null) {
                        game.teleporterSystem.removeTeleporter(teleporterToRemove)
                        wasRemoved = true
                    }

                    if (wasRemoved) {
                        println("Despawned entity (or path node) with ID: $id from active scene.")
                    } else {
                        println("Warning: Despawn event for ID '$id' did not find a matching entity in the active scene.")
                    }
                }
            }
            GameEventType.START_DIALOG -> {
                event.dialogId?.let { dialogId ->
                    dialogueManager.getDialogue(dialogId)?.let { dialogSequence ->
                        val sequenceWithCallback = dialogSequence.copy(onComplete = { reportDialogComplete(dialogId) })
                        game.uiManager.dialogSystem.startDialog(sequenceWithCallback)
                    } ?: println("ERROR: Mission tried to start dialog '$dialogId', but it was not found.")
                }
            }
            GameEventType.GIVE_WEAPON -> {
                event.weaponType?.let { weapon ->
                    val ammo = event.ammoAmount ?: weapon.magazineSize // Give one magazine by default
                    game.playerSystem.addWeaponToInventory(weapon, ammo)
                    println("Gave player weapon: ${weapon.displayName} with $ammo ammo.")
                }
            }
            GameEventType.FORCE_EQUIP_WEAPON -> {
                event.weaponType?.let { weapon ->
                    // First, check if the player has the weapon. If not, give it to them.
                    if (!game.playerSystem.hasWeapon(weapon)) {
                        val ammo = event.ammoAmount ?: weapon.magazineSize
                        game.playerSystem.addWeaponToInventory(weapon, ammo)
                    }
                    // Now, force the equip.
                    game.playerSystem.equipWeapon(weapon)
                    println("Forced player to equip: ${weapon.displayName}")
                }
            }
            GameEventType.SPAWN_CAR_PATH_NODE -> {
                if (event.spawnPosition != null && event.pathNodeId != null) {
                    val nodeData = CarPathNodeData(
                        id = event.pathNodeId,
                        position = event.spawnPosition,
                        previousNodeId = event.previousNodeId,
                        isOneWay = event.isOneWay,
                        sceneId = targetSceneId,
                        missionId = missionId
                    )
                    val newNode = game.carPathSystem.addNodeFromData(nodeData)

                    // Link the previous node to this new one for mission execution
                    if (newNode != null && nodeData.previousNodeId != null) {
                        game.carPathSystem.nodes[nodeData.previousNodeId]?.nextNodeId = newNode.id
                    }
                }
            }

            GameEventType.SPAWN_CHARACTER_PATH_NODE -> {
                if (event.spawnPosition != null && event.pathNodeId != null) {
                    val nodeData = CharacterPathNodeData(
                        id = event.pathNodeId,
                        position = event.spawnPosition,
                        previousNodeId = event.previousNodeId,
                        isOneWay = event.isOneWay,
                        isMissionOnly = event.isMissionOnly,
                        missionId = missionId,
                        sceneId = targetSceneId
                    )
                    val newNode = game.characterPathSystem.addNodeFromData(nodeData)

                    // Link the previous node to this new one for mission execution
                    if (newNode != null && nodeData.previousNodeId != null) {
                        game.characterPathSystem.nodes[nodeData.previousNodeId]?.nextNodeId = newNode.id
                    }
                }
            }
            GameEventType.CLEAR_INVENTORY -> {
                game.playerSystem.clearInventory()
                println("Player inventory temporarily cleared for mission.")
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

    fun forceStartMission(id: String) {
        // First, forcefully end any mission that might already be running to ensure a clean state.
        if (activeMission != null) {
            forceEndMission()
        }

        val missionDef = allMissions[id]
        if (missionDef == null) {
            println("ERROR: Could not force start mission. ID not found: $id")
            return
        }

        game.uiManager.showMissionStartNotification("DEBUG: " + missionDef.title)

        val modifiesInventory = missionDef.eventsOnStart.any {
            it.type in listOf(GameEventType.CLEAR_INVENTORY, GameEventType.GIVE_WEAPON, GameEventType.FORCE_EQUIP_WEAPON)
        } || missionDef.modifiers.disableWeaponSwitching

        if (modifiesInventory) {
            playerInventorySnapshot = game.playerSystem.createStateDataSnapshot()
        }

        println("--- FORCE STARTING MISSION: ${missionDef.title} ---")
        activeMission = MissionState(missionDef)
        activeModifiers = missionDef.modifiers

        missionDef.eventsOnStart.forEach { executeEvent(it) }

        missionDef.objectives.firstOrNull()?.let { firstObjective ->
            onObjectiveStarted(firstObjective)
        }

        updateUIForCurrentObjective()
    }

    fun forceEndMission() {
        val mission = activeMission ?: return
        val endedMissionId = mission.definition.id

        println("--- FORCE ENDING MISSION: ${mission.definition.title} ---")

        // Restore player state if it was snapshotted
        playerInventorySnapshot?.let {
            game.playerSystem.loadState(it)
            playerInventorySnapshot = null
        }

        // Deactivate modifiers
        activeModifiers?.let {
            game.playerSystem.physicsComponent.speed = game.playerSystem.basePlayerSpeed
        }
        activeModifiers = null

        // Clear all mission-related UI elements
        game.uiManager.updateEnemiesLeft(-1)
        game.uiManager.updateMissionObjective("")
        game.uiManager.updateMissionTimer(-1f)
        game.uiManager.updateLeaveCarTimer(-1f)
        game.uiManager.updateReturnToAreaTimer(-1f)
        game.objectiveArrowSystem.hide()

        // Reset internal timers
        leaveCarTimer = -1f
        requiredCarIdForTimer = null
        objectiveTimerStartDelay = -1f
        stayInAreaGraceTimer = -1f

        // Clean up any entities spawned specifically for this mission
        game.sceneManager.cleanupMissionEntities(endedMissionId)

        // Set the active mission to null. CRUCIALLY, we do NOT add it to completedMissionIds.
        activeMission = null

        game.uiManager.showTemporaryMessage("Mission force-stopped.")
    }

    fun resetAllMissionProgress() {
        if (activeMission != null) {
            forceEndMission()
        }
        gameState.completedMissionIds.clear()
        println("--- ALL MISSION PROGRESS RESET ---")
        game.uiManager.showTemporaryMessage("All mission progress has been reset.")
    }
}
