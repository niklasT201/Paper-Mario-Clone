package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter
import com.badlogic.gdx.utils.ObjectMap
import java.util.*

class MissionSystem(val game: MafiaGame, private val dialogueManager: DialogueManager) {
    private val allMissions = mutableMapOf<String, MissionDefinition>()
    private var activeMission: MissionState? = null
    var activeModifiers: MissionModifiers? = null
    private var gameState = GameState() // This will be loaded from a file later

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

    fun update(deltaTime: Float) {
        // It only runs if there is no currently active mission.
        if (activeMission == null) {
            for ((missionId, missionDef) in allMissions) {
                // Skip missions that are already completed
                if (gameState.completedMissionIds.contains(missionId)) continue

                val trigger = missionDef.startTrigger
                var shouldStartMission = false

                when (trigger.type) {
                    // This is the new trigger check
                    TriggerType.ON_COLLECT_ITEM -> {
                        val requiredItem = trigger.itemType ?: continue
                        val currentCount = game.playerSystem.countItemInInventory(requiredItem)
                        if (currentCount >= trigger.itemCount) {
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
                    break // Important: Start only one mission per frame to avoid conflicts
                }
            }
        }

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

        // Activate the modifiers for this mission
        activeModifiers = missionDef.modifiers
        println("Activating mission modifiers: Invincible=${activeModifiers?.setUnlimitedHealth}")

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
            nextObjective.eventsOnStart.forEach { executeEvent(it) }

            // The existing timer and UI update logic remains unchanged
            if (nextObjective.completionCondition.type == ConditionType.TIMER_EXPIRES) {
                val duration = nextObjective.completionCondition.timerDuration ?: 60f
                missionState.missionVariables["timer"] = duration
                println("Mission timer started for $duration seconds.")
            }

            updateUIForCurrentObjective()
        }
    }

    private fun endMission(completed: Boolean) {
        val mission = activeMission ?: return
        if (completed) {
            println("--- MISSION COMPLETE: ${mission.definition.title} ---")
            gameState.completedMissionIds.add(mission.definition.id)

            // Execute mission-complete events and grant rewards
            mission.definition.eventsOnComplete.forEach { executeEvent(it) }
            mission.definition.rewards.forEach { grantReward(it) }

        } else {
            println("--- MISSION FAILED: ${mission.definition.title} ---")
            // TODO: Reset any changes made by the mission
        }

        activeModifiers?.let {
            // Reset player speed back to its default value if it was changed.
            game.playerSystem.physicsComponent.speed = game.playerSystem.basePlayerSpeed
        }

        // Deactivate modifiers when the mission ends
        println("Deactivating mission modifiers.")
        activeModifiers = null

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

            // Access the missionVariables map through the 'currentMission' object
            currentMission.missionVariables["dialog_complete_${condition.targetId}"] = true

            // The rest of the function is correct and can stay the same
            if (isObjectiveComplete(objective, currentMission)) {
                advanceObjective()
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
            else -> println("Reward type ${reward.type} not yet implemented.")
        }
        println("Granted reward: ${reward.type}")
    }

    fun getAllDialogueIds(): List<String> {
        // We get the list from the dialogueLoader. The keys of its map are the IDs.
        return dialogueManager.getAllDialogueIds()
    }

    private fun executeEvent(event: GameEvent) {
        println("Executing mission event: ${event.type} for scene '${event.sceneId ?: "WORLD"}'")

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
            if (targetSceneId == currentSceneId) {
                activeList.add(entity)
            } else {
                if (targetSceneId == "WORLD") {
                    game.sceneManager.worldState?.let { worldListProvider()?.add(entity) }
                    println("Event for WORLD scene: Added entity to world state while in an interior.")
                } else {
                    game.sceneManager.interiorStates[targetSceneId]?.let {
                        interiorListProvider(targetSceneId)?.add(entity)
                        println("Event for another scene: Added entity to state for house '$targetSceneId'.")
                    } ?: println("ERROR: Could not find interior state for house '$targetSceneId' to spawn entity.")
                }
            }
        }

        when (event.type) {
            GameEventType.SPAWN_ENEMY -> {
                if (event.enemyType != null && event.enemyBehavior != null && event.spawnPosition != null) {
                    val config = EnemySpawnConfig(
                        enemyType = event.enemyType,
                        behavior = event.enemyBehavior,
                        position = event.spawnPosition,
                        id = event.targetId,
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
                        addEntityToScene(newEnemy, game.sceneManager.activeEnemies, { game.sceneManager.worldState?.enemies }) {
                            game.sceneManager.interiorStates[it]?.enemies
                        }
                    }
                }
            }
            GameEventType.SPAWN_NPC -> {
                if (event.npcType != null && event.npcBehavior != null && event.spawnPosition != null) {
                    val config = NPCSpawnConfig(
                        npcType = event.npcType,
                        behavior = event.npcBehavior,
                        position = event.spawnPosition,
                        id = event.targetId
                    )
                    event.npcRotation?.let {
                        game.npcSystem.createNPC(config, it)?.let { newNpc ->
                            addEntityToScene(newNpc, game.sceneManager.activeNPCs, { game.sceneManager.worldState?.npcs }) {
                                game.sceneManager.interiorStates[it]?.npcs
                            }
                        }
                    }
                }
            }
            GameEventType.SPAWN_CAR -> {
                if (event.carType != null && event.spawnPosition != null) {
                    if (targetSceneId != "WORLD") {
                        println("WARNING: Mission event tried to spawn a car in an interior ('$targetSceneId'). Aborting event.")
                        return
                    }

                    val carInstance = game.carSystem.createCarInstance(event.carType) ?: return
                    val newCar = GameCar(
                        modelInstance = carInstance,
                        carType = event.carType,
                        position = event.spawnPosition,
                        sceneManager = game.sceneManager,
                        isLocked = event.carIsLocked,
                        health = event.carType.baseHealth,
                        initialVisualRotation = event.houseRotationY ?: 0f
                    )
                    event.targetId?.let { newCar.id = it }

                    var driver: Any? = null

                    when (event.carDriverType) {
                        "Enemy" -> {
                            event.carEnemyDriverType?.let { enemyType ->
                                val enemyConfig = EnemySpawnConfig(enemyType, EnemyBehavior.AGGRESSIVE_RUSHER, newCar.position)
                                val createdDriver = game.enemySystem.createEnemy(enemyConfig)
                                if (createdDriver != null) {
                                    driver = createdDriver
                                    createdDriver.enterCar(newCar)
                                    createdDriver.currentState = AIState.PATROLLING_IN_CAR
                                }
                            }
                        }
                        "NPC" -> {
                            event.carNpcDriverType?.let { npcType ->
                                val npcConfig = NPCSpawnConfig(npcType, NPCBehavior.WANDER, newCar.position)
                                val createdDriver = game.npcSystem.createNPC(npcConfig)
                                if (createdDriver != null) {
                                    driver = createdDriver
                                    createdDriver.enterCar(newCar)
                                    createdDriver.currentState = NPCState.PATROLLING_IN_CAR
                                }
                            }
                        }
                    }

                    addEntityToScene(newCar, game.sceneManager.activeCars, { game.sceneManager.worldState?.cars }, { null })

                    // Create a local, non-changing copy of the driver for the smart cast to work.
                    when (val finalDriver = driver) {
                        is GameEnemy -> addEntityToScene(finalDriver, game.sceneManager.activeEnemies, { game.sceneManager.worldState?.enemies }, { null })
                        is GameNPC -> addEntityToScene(finalDriver, game.sceneManager.activeNPCs, { game.sceneManager.worldState?.npcs }, { null })
                    }
                }
            }
            GameEventType.SPAWN_ITEM, GameEventType.SPAWN_MONEY_STACK -> {
                if (event.itemType != null && event.spawnPosition != null) {
                    game.itemSystem.createItem(event.spawnPosition, event.itemType)?.let { newItem ->
                        if (event.type == GameEventType.SPAWN_MONEY_STACK) newItem.value = event.itemValue
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

                    val targetChunkManager = when (targetSceneId) {
                        currentSceneId -> game.sceneManager.activeChunkManager
                        "WORLD" -> game.sceneManager.worldChunkManager
                        else -> game.sceneManager.interiorChunkManagers[targetSceneId]
                    }

                    targetChunkManager?.addBlock(newBlock) ?: println("ERROR: Could not find ChunkManager for scene '$targetSceneId' to spawn block.")
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
                        event.targetId?.let { newHouse.id = it }
                        addEntityToScene(newHouse, game.sceneManager.activeHouses, { game.sceneManager.worldState?.houses }, { null })
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
                            loopOffDuration = event.loopOffDuration ?: 0.2f
                        )
                        if (targetSceneId == currentSceneId) {
                            val instances = game.objectSystem.createLightSourceInstances(light)
                            game.lightingManager.addLightSource(light, instances)
                        } else {
                            val targetLightsMap = if (targetSceneId == "WORLD") game.sceneManager.worldState?.lights else game.sceneManager.interiorStates[targetSceneId]?.lights
                            targetLightsMap?.put(light.id, light)
                        }
                    } else {
                        game.objectSystem.createGameObjectWithLight(event.objectType, event.spawnPosition, if (targetSceneId == currentSceneId) game.lightingManager else null)?.let { newObject ->
                            event.targetId?.let { newObject.id = it.hashCode() }
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

                        // Load all saved spawner properties from the event
                        newSpawner.spawnerType = event.spawnerType ?: newSpawner.spawnerType
                        newSpawner.spawnerMode = event.spawnerMode ?: newSpawner.spawnerMode
                        newSpawner.spawnInterval = event.spawnInterval ?: newSpawner.spawnInterval
                        newSpawner.minSpawnRange = event.minSpawnRange ?: newSpawner.minSpawnRange
                        newSpawner.maxSpawnRange = event.maxSpawnRange ?: newSpawner.maxSpawnRange
                        newSpawner.spawnOnlyWhenPreviousIsGone = event.spawnOnlyWhenPreviousIsGone ?: false

                        // Type-specific settings
                        newSpawner.particleEffectType = event.particleEffectType ?: newSpawner.particleEffectType
                        newSpawner.itemType = event.spawnerItemType ?: newSpawner.itemType
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
                    fireSystem.nextFireIsLooping = event.isLooping ?: true
                    fireSystem.nextFireFadesOut = event.fadesOut ?: false
                    fireSystem.nextFireLifetime = event.lifetime ?: 20f
                    fireSystem.nextFireCanBeExtinguished = event.canBeExtinguished ?: true
                    fireSystem.nextFireDealsDamage = event.dealsDamage ?: true
                    fireSystem.nextFireDamagePerSecond = event.damagePerSecond ?: 10f
                    fireSystem.nextFireDamageRadius = event.damageRadius ?: 5f
                    fireSystem.nextFireMinScale = event.fireScale ?: 1f // Use fireScale for both min/max
                    fireSystem.nextFireMaxScale = event.fireScale ?: 1f

                    // Create the fire using the dedicated system
                    val newFire = fireSystem.addFire(event.spawnPosition.cpy(), game.objectSystem, game.lightingManager)
                    if (newFire != null) {
                        addEntityToScene(newFire, game.fireSystem.activeFires, { game.sceneManager.worldState?.fires }) {
                            game.sceneManager.interiorStates[it]?.fires
                        }
                        addEntityToScene(newFire.gameObject, game.sceneManager.activeObjects, { game.sceneManager.worldState?.objects }) {
                            game.sceneManager.interiorStates[it]?.objects
                        }
                    }
                }
            }
            GameEventType.DESPAWN_ENTITY -> {
                event.targetId?.let { id ->
                    // Despawning only makes sense in the currently active scene.
                    game.sceneManager.activeEnemies.removeAll { it.id == id }
                    game.sceneManager.activeNPCs.removeAll { it.id == id }
                    game.sceneManager.activeCars.removeAll { it.id == id }
                    println("Despawned entity with ID: $id from active scene.")
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
