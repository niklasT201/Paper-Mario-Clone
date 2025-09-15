package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.JsonWriter
import com.badlogic.gdx.utils.Array as GdxArray
import com.badlogic.gdx.utils.ObjectMap

class SaveLoadSystem(private val game: MafiaGame) {

    private val json = Json().apply {
        setOutputType(JsonWriter.OutputType.json)
        setUsePrototypes(false)

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
    private val saveFile = Gdx.files.local("savegame.json")

    fun saveGame() {
        println("--- SAVING GAME STATE ---")
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
            sm.activeChunkManager.getAllBlocks().forEach { b -> world.blocks.add(BlockData(b.blockType, b.shape, b.position, b.rotationY, b.textureRotationY, b.topTextureRotationY, b.cameraVisibility)) }
            sm.activeCars.forEach { c -> world.cars.add(CarData(c.id, c.carType, c.position, c.health, c.isLocked, (c.seats.first()?.occupant as? GameEnemy)?.id ?: (c.seats.first()?.occupant as? GameNPC)?.id)) }
            sm.activeEnemies.forEach { e ->
                world.enemies.add(EnemyData(
                    e.id, e.enemyType, e.behaviorType, e.position, e.health,
                    GdxArray(e.inventory.map { i -> ItemData(i.itemType, i.position, i.ammo, i.value) }.toTypedArray()),
                    ObjectMap<WeaponType, Int>().apply {
                        e.weapons.forEach { (weapon, ammo) ->
                            put(weapon, ammo)
                        }
                    },
                    e.equippedWeapon
                ))
            }
            sm.activeNPCs.forEach { n -> world.npcs.add(NpcData(n.id, n.npcType, n.behaviorType, n.position, n.health)) }
            sm.activeItems.forEach { i -> world.items.add(ItemData(i.itemType, i.position, i.ammo, i.value)) }
            sm.activeObjects.forEach { o -> world.objects.add(ObjectData(o.objectType, o.position)) }
            sm.activeHouses.forEach { h ->
                world.houses.add(HouseData(
                    h.houseType,
                    h.position,
                    h.isLocked,
                    h.rotationY,
                    h.entryPointId,
                    h.assignedRoomTemplateId
                ))
            }
            sm.activeEntryPoints.forEach { ep -> world.entryPoints.add(EntryPointData(ep.id, ep.houseId, ep.position)) }
            game.lightingManager.getLightSources().values.forEach { l -> world.lights.add(LightData(l.position, l.color, l.intensity, l.range)) }
            sm.activeSpawners.forEach { s ->
                world.spawners.add(SpawnerData(
                    id = s.id,
                    position = s.position,
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
                    carSpawnDirection = s.carSpawnDirection
                ))
            }
            game.backgroundSystem.getBackgrounds().forEach { bg ->
                world.backgrounds.add(BackgroundData(bg.backgroundType, bg.position))
            }
            game.parallaxBackgroundSystem.getLayers().forEachIndexed { index, layer ->
                layer.images.forEach { image ->
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
            }
            state.worldState = world

            // 3. Mission Data
            state.missionState = game.missionSystem.getSaveData()

            // 4. Car Path Data
            val carPath = CarPathData()
            game.carPathSystem.nodes.values.forEach { n -> carPath.nodes.add(CarPathNodeData(n.id, n.position, n.nextNodeId, n.isOneWay)) }
            state.carPathState = carPath

            // 5. Write to file
            saveFile.writeString(json.prettyPrint(state), false)
            println("--- GAME SAVED SUCCESSFULLY to ${saveFile.path()} ---")
            game.uiManager.showTemporaryMessage("Game Saved")

        } catch (e: Exception) {
            println("--- ERROR SAVING GAME: ${e.message} ---"); e.printStackTrace()
            game.uiManager.showTemporaryMessage("Error: Could not save game!")
        }
    }

    fun loadGame() {
        if (!saveFile.exists()) {
            println("No save file found."); game.uiManager.showTemporaryMessage("No save file found!"); return
        }
        println("--- LOADING GAME STATE ---")
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
                val config = EnemySpawnConfig(id = data.id, enemyType = data.enemyType, behavior = data.behaviorType, position = data.position)
                game.enemySystem.createEnemy(config)?.let {
                    it.health = data.health
                    data.weapons.forEach { entry -> it.weapons[entry.key] = entry.value }
                    it.equippedWeapon = data.equippedWeapon
                    it.inventory.addAll(data.inventory.map { iData -> game.itemSystem.createItem(iData.position, iData.itemType)!!.apply { ammo = iData.ammo; value = iData.value } })
                    game.enemySystem.updateEnemyTexture(it)
                    sm.activeEnemies.add(it)
                    enemyMap[it.id] = it
                }
            }
            val npcMap = mutableMapOf<String, GameNPC>()
            state.worldState.npcs.forEach { data ->
                val config = NPCSpawnConfig(id = data.id, npcType = data.npcType, behavior = data.behaviorType, position = data.position)
                game.npcSystem.createNPC(config)?.let {
                    it.health = data.health
                    sm.activeNPCs.add(it)
                    npcMap[it.id] = it
                }
            }
            // Now create other world entities
            state.worldState.blocks.forEach { data -> sm.addBlock(game.blockSystem.createGameBlock(data.blockType, data.shape, data.position, data.rotationY, data.textureRotationY, data.topTextureRotationY).copy(cameraVisibility = data.cameraVisibility)) }
            sm.activeChunkManager.processDirtyChunks()
            state.worldState.cars.forEach { data ->
                game.carSystem.spawnCar(data.position, data.carType, data.isLocked, 0f)?.let {
                    it.health = data.health
                    data.driverId?.let { driverId ->
                        enemyMap[driverId]?.enterCar(it) ?: npcMap[driverId]?.enterCar(it)
                    }
                }
            }
            state.worldState.items.forEach { data -> game.itemSystem.createItem(data.position, data.itemType)?.let { it.ammo = data.ammo; it.value = data.value; sm.activeItems.add(it) } }
            state.worldState.objects.forEach { data -> game.objectSystem.createGameObjectWithLight(data.objectType, data.position, game.lightingManager)?.let { sm.activeObjects.add(it) } }
            state.worldState.houses.forEach { data ->
                game.houseSystem.currentRotation = data.rotationY
                val newHouse = game.houseSystem.addHouse(data.position.x, data.position.y, data.position.z, data.houseType, data.isLocked)
                if (newHouse != null) {
                    // Restore the link to the custom entry point
                    newHouse.entryPointId = data.entryPointId
                    newHouse.assignedRoomTemplateId = data.assignedRoomTemplateId
                }
            }
            state.worldState.entryPoints.forEach { data ->
                val debugInstance = ModelInstance(game.houseSystem.entryPointDebugModel!!) // Recreate the visual model
                val entryPoint = GameEntryPoint(data.id, data.houseId, data.position, debugInstance)
                sm.activeEntryPoints.add(entryPoint)
            }
            state.worldState.lights.forEach { data ->
                val light = game.objectSystem.createLightSource(data.position, data.intensity, data.range, data.color)
                game.lightingManager.addLightSource(light, game.objectSystem.createLightSourceInstances(light))
            }
            state.worldState.spawners.forEach { data ->
                val spawnerGameObject = game.objectSystem.createGameObjectWithLight(ObjectType.SPAWNER, data.position.cpy())
                if (spawnerGameObject != null) {
                    spawnerGameObject.debugInstance?.transform?.setTranslation(data.position)

                    val newSpawner = GameSpawner(
                        id = data.id,
                        position = data.position,
                        gameObject = spawnerGameObject,
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

                    sm.activeSpawners.add(newSpawner)
                }
            }

            // 2. Restore Car Paths
            game.carPathSystem.nodes.clear()
            val nodeDataMap = state.carPathState.nodes.associateBy { it.id }
            nodeDataMap.values.forEach { data ->
                val node = CarPathNode(data.id, data.position, data.nextNodeId, null, ModelInstance(game.carPathSystem.nodeModel), data.isOneWay)
                game.carPathSystem.nodes[data.id] = node
            }
            // Second pass to link previousNodeId
            game.carPathSystem.nodes.values.forEach { node -> node.nextNodeId?.let { nextId -> game.carPathSystem.nodes[nextId]?.previousNodeId = node.id } }

            // 3. Restore Player State
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
            game.uiManager.showTemporaryMessage("Game Loaded")

        } catch (e: Exception) {
            println("--- ERROR LOADING GAME: ${e.message} ---"); e.printStackTrace()
            game.uiManager.showTemporaryMessage("Error: Save file corrupted!")
        }
    }
}
