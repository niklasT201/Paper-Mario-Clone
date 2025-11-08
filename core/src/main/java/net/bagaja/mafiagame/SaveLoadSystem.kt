package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.JsonWriter
import com.badlogic.gdx.utils.Array as GdxArray
import com.badlogic.gdx.utils.ObjectMap

class SaveLoadSystem(private val game: MafiaGame) {
    companion object {
        const val EDITOR_SAVE_FILE = "savegame.json"
        const val PLAYER_SAVES_DIR = ".mafiagame/saves/" // This will be in the user's home directory
    }

    private val json = Json().apply {
        setOutputType(JsonWriter.OutputType.json)
        setUsePrototypes(false)

        setSerializer(Vector3::class.java, object : Json.Serializer<Vector3> {
            override fun write(json: Json, vec: Vector3, knownType: Class<*>?) {
                json.writeObjectStart()
                json.writeValue("x", vec.x)
                json.writeValue("y", vec.y)
                json.writeValue("z", vec.z)
                json.writeObjectEnd()
            }

            override fun read(json: Json, jsonData: JsonValue, type: Class<*>?): Vector3 {
                val x = jsonData.getFloat("x", 0f)
                val y = jsonData.getFloat("y", 0f)
                val z = jsonData.getFloat("z", 0f)
                return Vector3(x, y, z)
            }
        })

        setSerializer(ObjectMap::class.java, object : Json.Serializer<ObjectMap<*, *>> {
            override fun write(json: Json, map: ObjectMap<*, *>, knownType: Class<*>?) {
                json.writeObjectStart()
                for (entry in map) {
                    // Convert the enum key to its string name for saving
                    json.writeValue((entry.key as Enum<*>).name, entry.value)
                }
                json.writeObjectEnd()
            }

            override fun read(json: Json, jsonData: JsonValue, type: Class<*>?): ObjectMap<WeaponType, Int> {
                val map = ObjectMap<WeaponType, Int>()
                var entry = jsonData.child
                while (entry != null) {
                    try {
                        // Read the string key and convert it back to a WeaponType enum
                        val weaponType = WeaponType.valueOf(entry.name)
                        val ammo = entry.asInt()
                        map.put(weaponType, ammo)
                    } catch (e: IllegalArgumentException) {
                        // This handles cases where a saved weapon might be removed from the game later
                        println("Warning: Could not find WeaponType for saved key '${entry.name}'. Skipping.")
                    }
                    entry = entry.next
                }
                return map
            }
        })
    }

    private fun getPlayerSaveFile(fileName: String): FileHandle {
        return Gdx.files.external("$PLAYER_SAVES_DIR$fileName")
    }

    private fun getEditorSaveFile(): FileHandle {
        return Gdx.files.local(EDITOR_SAVE_FILE)
    }

    fun listSaveGames(): List<String> {
        val saveDir = Gdx.files.external(PLAYER_SAVES_DIR)
        if (!saveDir.exists() || !saveDir.isDirectory) {
            return emptyList()
        }
        return saveDir.list(".json").map { it.name() }
    }

    fun getMostRecentSave(): String? {
        val saveDir = Gdx.files.external(PLAYER_SAVES_DIR)
        if (!saveDir.exists() || !saveDir.isDirectory) {
            return null
        }
        return saveDir.list(".json").maxByOrNull { it.lastModified() }?.name()
    }

    fun saveGame(fileName: String?) {
        val saveFile = if (fileName == null) getEditorSaveFile() else getPlayerSaveFile(fileName)
        println("--- SAVING GAME STATE to ${saveFile.path()} ---")
        try {
            val state = GameSaveState()
            val sm = game.sceneManager

            // 1. Player Data
            state.playerState = PlayerStateData(
                position = game.playerSystem.getPosition(),
                money = game.playerSystem.getMoney(),
                weapons = ObjectMap<WeaponType, Int>().apply {
                    game.playerSystem.getWeaponReserves().forEach { (weapon, ammo) -> put(weapon, ammo) }
                },
                equippedWeapon = game.playerSystem.equippedWeapon,
                currentMagazineCounts = ObjectMap<WeaponType, Int>().apply {
                    game.playerSystem.getMagazineCounts().forEach { (weapon, count) -> put(weapon, count) }
                }
            )

            // 2. World Data
            val world = WorldStateData()
            world.dayNightCycleTime = game.lightingManager.getDayNightCycle().currentTime

            // --- FILTERED ENTITY SAVING ---
            sm.activeChunkManager.getAllBlocks().filter { it.missionId == null }.forEach { b -> world.blocks.add(BlockData(b.blockType, b.shape, b.position, b.rotationY, b.textureRotationY, b.topTextureRotationY, b.cameraVisibility)) }

            sm.activeCars.filter { it.missionId == null }.forEach { c ->
                world.cars.add(CarData(c.id, c.carType, c.position, c.health, c.isLocked,
                    (c.seats.first()?.occupant as? GameEnemy)?.id ?: (c.seats.first()?.occupant as? GameNPC)?.id,
                    c.state, c.wreckedTimer, c.fadeOutTimer, c.visualRotationY,
                    c.areHeadlightsOn,
                    c.assignedLockedSoundId,
                    c.assignedOpenSoundId,
                    c.assignedCloseSoundId
                ))
            }
            sm.activeEnemies.filter { it.missionId == null }.forEach { e ->
                world.enemies.add(EnemyData(
                    e.id, e.enemyType, e.behaviorType, e.position, e.health,
                    e.assignedPathId,
                    GdxArray(e.inventory.map { i -> ItemData(i.id, i.itemType, i.position, i.ammo, i.value) }.toTypedArray()),
                    ObjectMap<WeaponType, Int>().apply { e.weapons.forEach { (w, a) -> put(w, a) } },
                    e.equippedWeapon,
                    e.currentState, e.currentMagazineCount, e.provocationLevel,
                    e.standaloneDialog,
                    e.standaloneDialogCompleted,
                    e.canBePulledFromCar
                ))
            }
            sm.activeNPCs.filter { it.missionId == null }.forEach { n ->
                world.npcs.add(NpcData(
                    n.id, n.npcType, n.behaviorType, n.position, n.health,
                    n.assignedPathId,
                    n.currentState,
                    n.provocationLevel,
                    GdxArray(n.inventory.map { i -> ItemData(i.id, i.itemType, i.position, i.ammo, i.value) }.toTypedArray()),
                    n.isHonest,
                    n.canCollectItems,
                    n.pathFollowingStyle,
                    n.standaloneDialog,
                    n.standaloneDialogCompleted,
                    n.canBePulledFromCar
                ))
            }
            sm.activeItems.filter { it.missionId == null }.forEach { i -> world.items.add(ItemData(i.id, i.itemType, i.position, i.ammo, i.value)) }
            sm.activeObjects.filter { it.missionId == null }.forEach { o ->
                if (o.objectType == ObjectType.FIRE_SPREAD) return@forEach

                world.objects.add(ObjectData(
                    o.id, o.objectType, o.position, o.associatedLightId,
                    o.isBroken
                ))
            }
            sm.activeHouses.filter { it.missionId == null }.forEach { h ->
                world.houses.add(HouseData(
                    h.id,
                    h.houseType,
                    h.position,
                    h.isLocked,
                    h.rotationY,
                    h.entryPointId,
                    h.assignedRoomTemplateId,
                    h.exitDoorId
                ))
            }
            sm.activeEntryPoints.forEach { ep -> world.entryPoints.add(EntryPointData(ep.id, ep.houseId, ep.position)) } // Entry points are not mission-only
            game.lightingManager.getLightSources().values.filter { it.missionId == null }.forEach { l ->
                world.lights.add(LightData(
                    l.id, l.position, l.color, l.intensity, l.range,
                    l.isEnabled, l.flickerMode, l.loopOnDuration, l.loopOffDuration, l.timedFlickerLifetime,
                    l.rotationX, l.rotationY, l.rotationZ,
                    l.missionId,
                    l.parentObjectId
                ))
            }
            sm.activeSpawners.forEach { s ->
                world.spawners.add(SpawnerData(
                    id = s.id,
                    position = s.position,
                    sceneId = s.sceneId,
                    spawnerType = s.spawnerType,
                    spawnInterval = s.spawnInterval,
                    minSpawnRange = s.minSpawnRange,
                    maxSpawnRange = s.maxSpawnRange,
                    spawnerMode = s.spawnerMode,
                    isDepleted = s.isDepleted,
                    spawnOnlyWhenPreviousIsGone = s.spawnOnlyWhenPreviousIsGone,
                    spawnedEntityId = s.spawnedEntityId,
                    particleEffectType = s.particleEffectType,
                    minParticles = s.minParticles,
                    maxParticles = s.maxParticles,
                    itemType = s.itemType,
                    minItems = s.minItems,
                    maxItems = s.maxItems,
                    weaponItemType = s.weaponItemType,
                    ammoSpawnMode = s.ammoSpawnMode,
                    setAmmoValue = s.setAmmoValue,
                    randomMinAmmo = s.randomMinAmmo,
                    randomMaxAmmo = s.randomMaxAmmo,
                    enemyType = s.enemyType,
                    enemyBehavior = s.enemyBehavior,
                    enemyHealthSetting = s.enemyHealthSetting,
                    enemyCustomHealth = s.enemyCustomHealth,
                    enemyMinHealth = s.enemyMinHealth,
                    enemyMaxHealth = s.enemyMaxHealth,
                    enemyInitialWeapon = s.enemyInitialWeapon,
                    enemyWeaponCollectionPolicy = s.enemyWeaponCollectionPolicy,
                    enemyCanCollectItems = s.enemyCanCollectItems,
                    enemyInitialMoney = s.enemyInitialMoney,
                    npcType = s.npcType,
                    npcBehavior = s.npcBehavior,
                    npcIsHonest = s.npcIsHonest,
                    npcCanCollectItems = s.npcCanCollectItems,
                    carType = s.carType,
                    carIsLocked = s.carIsLocked,
                    carDriverType = s.carDriverType,
                    carEnemyDriverType = s.carEnemyDriverType,
                    carNpcDriverType = s.carNpcDriverType,
                    carSpawnDirection = s.carSpawnDirection,
                    upgradedWeapon = s.upgradedWeapon
                ))
            }

            game.teleporterSystem.activeTeleporters.filter { it.missionId == null }.forEach { tp ->
                world.teleporters.add(TeleporterData(tp.id, tp.name, tp.linkedTeleporterId, tp.gameObject.position))
            }

            game.fireSystem.activeFires.filter { it.missionId == null }.forEach { fire ->
                world.fires.add(FireData(
                    fire.id,
                    fire.gameObject.position,
                    fire.isLooping,
                    fire.fadesOut,
                    fire.lifetime,
                    fire.canBeExtinguished,
                    fire.dealsDamage,
                    fire.damagePerSecond,
                    fire.damageRadius,
                    fire.initialScale,
                    fire.canSpread,
                    fire.generation,
                    fire.gameObject.associatedLightId
                ))
            }

            // Backgrounds are not mission-specific
            game.backgroundSystem.getBackgrounds().forEach { bg ->
                world.backgrounds.add(BackgroundData(bg.backgroundType, bg.position))
            }
            game.parallaxBackgroundSystem.getLayers().forEachIndexed { index, layer ->  layer.images.forEach { image ->
                world.parallaxImages.add(
                    ParallaxImageData(
                        (image.modelInstance.materials.first().get(TextureAttribute.Diffuse) as TextureAttribute)
                            .textureDescription.texture.let { tex ->
                                ParallaxBackgroundSystem.ParallaxImageType.entries.find { it.texturePath == image.texture.toString() } // Find the type by texture path
                                    ?: ParallaxBackgroundSystem.ParallaxImageType.MOUNTAINS // Fallback
                            },
                        image.basePosition.x,
                        index
                    )
                )
            }
                game.audioEmitterSystem.activeEmitters.filter { it.missionId == null }.forEach { e ->
                    world.audioEmitters.add(AudioEmitterData(
                        e.id, e.position, e.soundIds, e.volume, e.range, e.playbackMode,
                        e.playlistMode, e.reactivationMode, e.interval, e.timedLoopDuration,
                        e.minPitch, e.maxPitch, e.falloffMode, e.sceneId
                    ))
                }
            }
            state.worldState = world

            // 3. Mission Data
            state.missionState = game.missionSystem.getSaveData()

            // 4. Car Path Data
            val carPath = CarPathData()
            game.carPathSystem.nodes.values.filter { it.missionId == null }.forEach { n ->
                carPath.nodes.add(CarPathNodeData(n.id, n.position, n.nextNodeId, n.previousNodeId, n.isOneWay, n.sceneId))
            }
            state.carPathState = carPath

            // 5. Character Path Data
            val charPath = CharacterPathData()
            game.characterPathSystem.nodes.values.filter { it.missionId == null }.forEach { n ->
                charPath.nodes.add(CharacterPathNodeData(n.id, n.position, n.nextNodeId, n.previousNodeId, n.isOneWay, n.isMissionOnly, n.missionId, n.sceneId
                ))
            }
            state.characterPathState = charPath

            // 6. Write to file
            saveFile.writeString(json.prettyPrint(state), false)
            println("--- GAME SAVED SUCCESSFULLY to ${saveFile.path()} ---")
            game.uiManager.showTemporaryMessage("Game Saved")

        } catch (e: Exception) {
            println("--- ERROR SAVING GAME: ${e.message} ---"); e.printStackTrace()
            game.uiManager.showTemporaryMessage("Error: Could not save game!")
        }
    }

    fun loadGame(fileName: String?): Boolean {
        val saveFile = if (fileName == null) getEditorSaveFile() else getPlayerSaveFile(fileName)

        if (!saveFile.exists()) {
            println("No save file found at ${saveFile.path()}.");
            if (fileName != null) game.uiManager.showTemporaryMessage("Save file not found!")
            return false // Indicate failure
        }
        println("--- LOADING GAME STATE from ${saveFile.path()} ---")
        try {
            val state = json.fromJson(GameSaveState::class.java, saveFile)
            val sm = game.sceneManager

            // --- CLEAR CURRENT WORLD STATE ---
            sm.clearActiveSceneForLoad()
            sm.activeEntryPoints.clear()

            val world = state.worldState

            game.lightingManager.getDayNightCycle().currentTime = world.dayNightCycleTime

            // 1. Restore World State (Characters must be created before they can be put in cars)
            val enemyMap = mutableMapOf<String, GameEnemy>()
            state.worldState.enemies.forEach { data ->
                val config = EnemySpawnConfig(
                    id = data.id,
                    enemyType = data.enemyType,
                    behavior = data.behaviorType,
                    position = data.position,
                    assignedPathId = data.assignedPathId,
                    canBePulledFromCar = data.canBePulledFromCar,
                    standaloneDialog = data.standaloneDialog
                )
                game.enemySystem.createEnemy(config)?.let {
                    it.health = data.health
                    data.weapons.forEach { entry -> it.weapons[entry.key] = entry.value }
                    it.equippedWeapon = data.equippedWeapon
                    it.inventory.addAll(data.inventory.map { iData -> game.itemSystem.createItem(iData.position, iData.itemType)!!.apply { id = iData.id; ammo = iData.ammo; value = iData.value } })

                    it.currentState = data.currentState
                    it.currentMagazineCount = data.currentMagazineCount
                    it.provocationLevel = data.provocationLevel

                    game.enemySystem.updateEnemyTexture(it)
                    sm.activeEnemies.add(it)
                    enemyMap[it.id] = it
                }
            }
            val npcMap = mutableMapOf<String, GameNPC>()
            state.worldState.npcs.forEach { data ->
                val config = NPCSpawnConfig(
                    id = data.id,
                    npcType = data.npcType,
                    behavior = data.behaviorType,
                    position = data.position,
                    isHonest = data.isHonest,
                    canCollectItems = data.canCollectItems,
                    pathFollowingStyle = data.pathFollowingStyle,
                    assignedPathId = data.assignedPathId,
                    canBePulledFromCar = data.canBePulledFromCar,
                    standaloneDialog = data.standaloneDialog
                )
                game.npcSystem.createNPC(config)?.let {
                    it.health = data.health
                    it.currentState = data.currentState
                    it.provocationLevel = data.provocationLevel
                    it.pathFollowingStyle = data.pathFollowingStyle
                    it.inventory.addAll(data.inventory.map { iData -> game.itemSystem.createItem(iData.position, iData.itemType)!!.apply { id = iData.id; ammo = iData.ammo; value = iData.value } })

                    it.assignedPathId = data.assignedPathId
                    it.pathFollowingStyle = data.pathFollowingStyle

                    it.standaloneDialogCompleted = data.standaloneDialogCompleted
                    sm.activeNPCs.add(it)
                    npcMap[it.id] = it
                }
            }
            // Now create other world entities
            state.worldState.blocks.forEach { data -> sm.addBlock(game.blockSystem.createGameBlock(data.blockType, data.shape, data.position, data.rotationY, data.textureRotationY, data.topTextureRotationY).copy(cameraVisibility = data.cameraVisibility)) }
            sm.activeChunkManager.processDirtyChunks()
            state.worldState.cars.forEach { data ->
                game.carSystem.spawnCar(data.position, data.carType, data.isLocked, data.visualRotationY)?.let { car ->
                    car.id = data.id
                    car.health = data.health

                    car.state = data.state
                    car.wreckedTimer = data.wreckedTimer
                    car.fadeOutTimer = data.fadeOutTimer
                    car.areHeadlightsOn = data.areHeadlightsOn
                    // car.visualRotationY is now set during creation
                    car.assignedLockedSoundId = data.assignedLockedSoundId
                    car.assignedOpenSoundId = data.assignedOpenSoundId
                    car.assignedCloseSoundId = data.assignedCloseSoundId

                    data.driverId?.let { driverId ->
                        enemyMap[driverId]?.enterCar(car) ?: npcMap[driverId]?.enterCar(car)
                    }
                    sm.activeCars.add(car)
                }
            }
            state.worldState.items.forEach { data -> game.itemSystem.createItem(data.position, data.itemType)?.let {
                it.id = data.id
                it.ammo = data.ammo
                it.value = data.value
                sm.activeItems.add(it)
            } }
            state.worldState.objects.forEach { data ->
                game.objectSystem.createGameObjectWithLight(data.objectType, data.position, game.lightingManager)?.let { obj ->
                    obj.id = data.id
                    obj.associatedLightId = data.associatedLightId
                    obj.isBroken = data.isBroken

                    // Also check if the light needs to be disabled for a broken object
                    if (obj.isBroken) {
                        obj.associatedLightId?.let { lightId ->
                            game.lightingManager.getLightSources()[lightId]?.let { lightSource ->
                                lightSource.isEnabled = false
                                lightSource.updatePointLight()
                            }
                        }
                    }

                    sm.activeObjects.add(obj)
                }
            }
            state.worldState.houses.forEach { data ->
                game.houseSystem.currentRotation = data.rotationY
                val newHouse = game.houseSystem.addHouse(data.position.x, data.position.y, data.position.z, data.houseType, data.isLocked)
                if (newHouse != null) {
                    newHouse.id = data.id
                    newHouse.entryPointId = data.entryPointId
                    newHouse.assignedRoomTemplateId = data.assignedRoomTemplateId
                    newHouse.exitDoorId = data.exitDoorId
                }
            }
            state.worldState.entryPoints.forEach { data ->
                val debugInstance = ModelInstance(game.houseSystem.entryPointDebugModel!!) // Recreate the visual model
                val entryPoint = GameEntryPoint(data.id, data.houseId, data.position, debugInstance)
                sm.activeEntryPoints.add(entryPoint)
            }
            state.worldState.lights.forEach { data ->
                val light = LightSource(
                    data.id, data.position, data.intensity, data.range, data.color,
                    data.isEnabled, data.rotationX, data.rotationY, data.rotationZ,
                    data.flickerMode, data.loopOnDuration, data.loopOffDuration, data.timedFlickerLifetime
                )
                light.missionId = data.missionId
                light.parentObjectId = data.parentObjectId

                game.objectSystem.loadLightSource(light)
                game.lightingManager.addLightSource(light, game.objectSystem.createLightSourceInstances(light))
            }

            validateLoadedLights(state.worldState)

            state.worldState.spawners.forEach { data ->
                val spawnerGameObject = game.objectSystem.createGameObjectWithLight(ObjectType.SPAWNER, data.position.cpy())
                if (spawnerGameObject != null) {
                    spawnerGameObject.debugInstance?.transform?.setTranslation(data.position)

                    val newSpawner = GameSpawner(
                        id = data.id,
                        position = data.position,
                        gameObject = spawnerGameObject,
                        sceneId = data.sceneId,
                        spawnerType = data.spawnerType,
                        spawnInterval = data.spawnInterval,
                        minSpawnRange = data.minSpawnRange,
                        maxSpawnRange = data.maxSpawnRange,
                        spawnerMode = data.spawnerMode,
                        isDepleted = data.isDepleted,
                        spawnOnlyWhenPreviousIsGone = data.spawnOnlyWhenPreviousIsGone,
                        spawnedEntityId = data.spawnedEntityId,
                        particleEffectType = data.particleEffectType,
                        minParticles = data.minParticles,
                        maxParticles = data.maxParticles,
                        itemType = data.itemType,
                        minItems = data.minItems,
                        maxItems = data.maxItems,
                        weaponItemType = data.weaponItemType,
                        ammoSpawnMode = data.ammoSpawnMode,
                        setAmmoValue = data.setAmmoValue,
                        randomMinAmmo = data.randomMinAmmo,
                        randomMaxAmmo = data.randomMaxAmmo,
                        enemyType = data.enemyType,
                        enemyBehavior = data.enemyBehavior,
                        enemyHealthSetting = data.enemyHealthSetting,
                        enemyCustomHealth = data.enemyCustomHealth,
                        enemyMinHealth = data.enemyMinHealth,
                        enemyMaxHealth = data.enemyMaxHealth,
                        enemyInitialWeapon = data.enemyInitialWeapon,
                        enemyWeaponCollectionPolicy = data.enemyWeaponCollectionPolicy,
                        enemyCanCollectItems = data.enemyCanCollectItems,
                        enemyInitialMoney = data.enemyInitialMoney,
                        npcType = data.npcType,
                        npcBehavior = data.npcBehavior,
                        npcIsHonest = data.npcIsHonest,
                        npcCanCollectItems = data.npcCanCollectItems,
                        carType = data.carType,
                        carIsLocked = data.carIsLocked,
                        carDriverType = data.carDriverType,
                        carEnemyDriverType = data.carEnemyDriverType,
                        carNpcDriverType = data.carNpcDriverType,
                        carSpawnDirection = data.carSpawnDirection
                    )
                    newSpawner.upgradedWeapon = data.upgradedWeapon

                    sm.activeSpawners.add(newSpawner)
                }
            }
            // Load Teleporters
            state.worldState.teleporters.forEach { data ->
                val gameObject = game.objectSystem.createGameObjectWithLight(ObjectType.TELEPORTER, data.position.cpy())
                if (gameObject != null) {
                    gameObject.modelInstance.transform.setTranslation(data.position)
                    gameObject.debugInstance?.transform?.setTranslation(data.position)
                    val newTeleporter = GameTeleporter(
                        id = data.id,
                        gameObject = gameObject,
                        linkedTeleporterId = data.linkedTeleporterId, // Link will be correct on load
                        name = data.name
                    )
                    game.teleporterSystem.activeTeleporters.add(newTeleporter)
                }
            }

            // Load Fires
            state.worldState.fires.forEach { data ->
                val fireSystem = game.fireSystem
                // Temporarily configure fire system from saved data
                fireSystem.nextFireIsLooping = data.isLooping
                fireSystem.nextFireFadesOut = data.fadesOut
                fireSystem.nextFireLifetime = data.lifetime
                fireSystem.nextFireCanBeExtinguished = data.canBeExtinguished
                fireSystem.nextFireDealsDamage = data.dealsDamage
                fireSystem.nextFireDamagePerSecond = data.damagePerSecond
                fireSystem.nextFireDamageRadius = data.damageRadius
                fireSystem.nextFireMinScale = data.initialScale
                fireSystem.nextFireMaxScale = data.initialScale

                // Create the fire object
                val newFire = fireSystem.addFire(
                    position = data.position,
                    objectSystem = game.objectSystem,
                    lightingManager = game.lightingManager,
                    generation = data.generation,
                    canSpread = data.canSpread,
                    id = data.id,
                    existingAssociatedLightId = data.associatedLightId
                )
                if (newFire != null) {
                    sm.activeObjects.add(newFire.gameObject)
                }
            }

            state.worldState.audioEmitters.forEach { data ->
                game.audioEmitterSystem.addEmitterFromData(data)
            }

            // 2. Restore Car Paths
            game.carPathSystem.nodes.clear()
            state.carPathState.nodes.forEach { data ->
                val node = CarPathNode(data.id, data.position, data.nextNodeId, data.previousNodeId, ModelInstance(game.carPathSystem.nodeModel), data.isOneWay, data.sceneId)
                game.carPathSystem.nodes[data.id] = node
            }
            // Second pass to link previousNodeId
            game.carPathSystem.nodes.values.forEach { node -> node.nextNodeId?.let { nextId -> game.carPathSystem.nodes[nextId]?.previousNodeId = node.id } }

            // 3. Restore Character Paths
            game.characterPathSystem.nodes.clear()
            state.characterPathState.nodes.forEach { data ->
                val node = CharacterPathNode(
                    id = data.id,
                    position = data.position,
                    nextNodeId = data.nextNodeId,
                    previousNodeId = data.previousNodeId,
                    debugInstance = ModelInstance(game.characterPathSystem.nodeModel),
                    isOneWay = data.isOneWay,
                    isMissionOnly = data.isMissionOnly,
                    missionId = data.missionId,
                    sceneId = data.sceneId
                )
                game.characterPathSystem.nodes[data.id] = node
            }

            // 4. Restore Player State
            game.playerSystem.loadState(state.playerState)

            state.worldState.backgrounds.forEach { data ->
                game.backgroundSystem.addBackground(data.position.x, data.position.y, data.position.z, data.backgroundType)
            }

            game.parallaxBackgroundSystem.clearAll()

            state.worldState.parallaxImages.forEach { data ->
                game.parallaxBackgroundSystem.addParallaxImage(data.imageType, data.basePositionX, data.layerIndex)
            }

            // 4. Restore Mission State
            game.missionSystem.loadSaveData(state.missionState)

            // 5. Finalize
            game.cameraManager.resetAndSnapToPlayer(state.playerState.position, false)
            println("--- GAME LOADED SUCCESSFULLY ---")
            if (fileName != null) game.uiManager.showTemporaryMessage("Game Loaded")
            return true // Indicate success
        } catch (e: Exception) {
            println("--- ERROR LOADING GAME: ${e.message} ---"); e.printStackTrace()
            game.uiManager.showTemporaryMessage("Error: Save file corrupted!")
            return false // Indicate failure
        }
    }

    private fun validateLoadedLights(world: WorldStateData) {
        val allObjectIds = world.objects.map { it.id } + world.fires.map { it.id } // Create a set of all valid parent IDs

        // Find all lights that have a parentObjectId that no longer exists
        val lightsToRemove = game.lightingManager.getLightSources().values.filter { light ->
            light.parentObjectId != null && !allObjectIds.contains(light.parentObjectId)
        }

        if (lightsToRemove.isNotEmpty()) {
            println("--- Save File Validation: Found ${lightsToRemove.size} orphan light(s). Removing... ---")
            lightsToRemove.forEach { light ->
                println("  - Removing orphan light #${light.id} (parent '${light.parentObjectId}' not found).")
                game.lightingManager.removeLightSource(light.id)
                game.objectSystem.removeLightSource(light.id)
            }
        }
    }

    fun startNewGame(newSaveName: String): Boolean {
        println("--- STARTING NEW GAME: $newSaveName ---")
        try {
            val editorFile = getEditorSaveFile()
            if (!editorFile.exists()) {
                println("ERROR: Cannot start new game. Master editor file '${EDITOR_SAVE_FILE}' not found.")
                return false
            }

            // 1. Load the master world state from the editor file.
            val masterState = json.fromJson(GameSaveState::class.java, editorFile)

            // 2. Create a fresh GameSaveState for the new player.
            val newPlayerState = GameSaveState()

            // 3. Copy ONLY the world-related data from the master file.
            newPlayerState.worldState = masterState.worldState
            newPlayerState.carPathState = masterState.carPathState
            newPlayerState.characterPathState = masterState.characterPathState
            // PlayerState and MissionState will remain as their default, empty versions.

            // 4. Save this new combined state to the player's new save file.
            val newPlayerSaveFile = getPlayerSaveFile(newSaveName)
            newPlayerSaveFile.writeString(json.prettyPrint(newPlayerState), false)

            println("New game '$newSaveName' created successfully.")
            return true
        } catch (e: Exception) {
            println("--- ERROR CREATING NEW GAME: ${e.message} ---"); e.printStackTrace()
            return false
        }
    }

    fun checkForWorldUpdateAndLoad(fileName: String): Boolean {
        val playerFile = getPlayerSaveFile(fileName)
        val editorFile = getEditorSaveFile()

        if (!playerFile.exists()) {
            println("Player save file does not exist.")
            return false
        }
        if (!editorFile.exists()) {
            println("Editor master file does not exist. Cannot check for updates.")
            // Proceed with normal load
            return loadGame(fileName)
        }

        // Check if the editor file has been modified more recently than the player file
        if (editorFile.lastModified() > playerFile.lastModified()) {
            println("World update detected! Merging world data from '${editorFile.name()}' into '${playerFile.name()}'.")
            try {
                // 1. Load the player's current progress
                val playerStateData = json.fromJson(GameSaveState::class.java, playerFile)
                val playerProgress = playerStateData.playerState
                val missionProgress = playerStateData.missionState

                // 2. Load the new world data from the editor file
                val masterStateData = json.fromJson(GameSaveState::class.java, editorFile)
                val newWorld = masterStateData.worldState
                val newCarPaths = masterStateData.carPathState
                val newCharPaths = masterStateData.characterPathState

                // Check if the player's old position is still valid in the new world.
                // This is a simplified check. A real game might need more complex validation.
                val playerOldPos = playerProgress.position
                val blockAtOldPos = newWorld.blocks.find { b -> b.position.dst(playerOldPos) < game.blockSize / 2f }
                if (blockAtOldPos != null && blockAtOldPos.blockType.hasCollision) {
                    println("WARNING: Player's saved position is now inside a solid block in the new world. Resetting player to default spawn.")
                    playerProgress.position.set(9f, 2f, 9f) // Reset to a known safe spot
                }

                // 3. Create a new, merged state
                val mergedState = GameSaveState(
                    playerState = playerProgress,
                    missionState = missionProgress,
                    worldState = newWorld,
                    carPathState = newCarPaths,
                    characterPathState = newCharPaths
                )

                // 4. Overwrite the player's save file with the merged data
                playerFile.writeString(json.prettyPrint(mergedState), false)
                println("Merge complete. Player progress has been preserved with the updated world.")

            } catch (e: Exception) {
                println("--- ERROR during world update merge: ${e.message} ---"); e.printStackTrace()
                // If merge fails, try to load the old save anyway.
                return loadGame(fileName)
            }
        }

        // Now, load the (potentially newly merged) save file.
        return loadGame(fileName)
    }
}
