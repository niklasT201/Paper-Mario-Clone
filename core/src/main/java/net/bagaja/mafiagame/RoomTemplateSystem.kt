package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter

// Data classes for room building
data class RoomElement(
    val position: Vector3 = Vector3(),
    val elementType: RoomElementType = RoomElementType.BLOCK,
    val blockType: BlockType? = null,
    val shape: BlockShape? = null,
    val cameraVisibility: CameraVisibility? = null,
    val objectType: ObjectType? = null,
    val itemType: ItemType? = null,
    val interiorType: InteriorType? = null,
    val enemyType: EnemyType? = null,
    val enemyBehavior: EnemyBehavior? = null,
    val npcType: NPCType? = null,
    val npcBehavior: NPCBehavior? = null,
    val npcRotation: Float = 0f,
    val targetId: String? = null,
    val rotation: Float = 0f,
    val textureRotation: Float = 0f,
    val topTextureRotation: Float = 0f,
    val scale: Vector3 = Vector3(1f, 1f, 1f),
    val lightColor: Color? = null,
    val lightIntensity: Float? = null,
    val lightRange: Float? = null,
    val flickerMode: FlickerMode? = null,
    val loopOnDuration: Float? = null,
    val loopOffDuration: Float? = null,
    // General
    val spawnerType: SpawnerType? = null,
    val spawnerInterval: Float? = null,
    val spawnerMinRange: Float? = null,
    val spawnerMaxRange: Float? = null,
    // Particle Spawner Specific
    val particleEffectType: ParticleEffectType? = null,
    val spawnerMinParticles: Int? = null,
    val spawnerMaxParticles: Int? = null,
    // Item Spawner Specific
    val spawnerItemType: ItemType? = null,
    val spawnerMinItems: Int? = null,
    val spawnerMaxItems: Int? = null,
    // Weapon Spawner Specific
    val spawnerWeaponItemType: ItemType? = null,
    val spawnerMinAmmo: Int? = null,
    val spawnerMaxAmmo: Int? = null,
    val ammoSpawnMode: AmmoSpawnMode? = null,
    val setAmmoValue: Int? = null,
    val randomMinAmmo: Int? = null,
    val randomMaxAmmo: Int? = null,
    val teleporterId: String? = null,
    val linkedTeleporterId: String? = null,
    val teleporterName: String? = null,
    val isLooping: Boolean? = null,
    val fadesOut: Boolean? = null,
    val lifetime: Float? = null,
    val canBeExtinguished: Boolean? = null,
    val dealsDamage: Boolean? = null,
    val damagePerSecond: Float? = null,
    val damageRadius: Float? = null,
    val spawnOnlyWhenPreviousIsGone: Boolean? = null
)

enum class RoomElementType {
    BLOCK,
    OBJECT,
    ITEM,
    INTERIOR,
    ENEMY,
    NPC,
    PARTICLE_SPAWNER,
    TELEPORTER,
    FIRE
}

data class RoomTemplate(
    val id: String = "",
    val name: String = "Unnamed Room",
    val description: String = "",
    val size: Vector3 = Vector3(),
    val elements: List<RoomElement> = emptyList(),
    val entrancePosition: Vector3 = Vector3(),
    val exitTriggerPosition: Vector3 = Vector3(),
    val exitTriggerSize: Vector3 = Vector3(4f, 4f, 2f),
    val category: String = "default",
    val exitDoorPosition: Vector3 = Vector3(),
    val isTimeFixed: Boolean = false,
    val fixedTimeProgress: Float = 0.5f, // A value from 0.0 (midnight) to 1.0 (end of day). 0.5 is midday.
    val savedShaderEffect: ShaderEffect = ShaderEffect.NONE
)

// The Builder to make creating templates easy
class RoomBuilder {
    private val elements = mutableListOf<RoomElement>()
    private var roomSize = Vector3(20f, 8f, 15f)
    private var entrancePos = Vector3(10f, 2f, 13f)
    private var exitTriggerPos = Vector3(10f, 2f, 14f)
    private var exitTriggerSize = Vector3(4f, 4f, 2f)

    fun setSize(width: Float, height: Float, depth: Float): RoomBuilder {
        roomSize.set(width, height, depth)
        return this
    }

    fun setEntrance(x: Float, y: Float, z: Float): RoomBuilder {
        entrancePos.set(x, y, z)
        return this
    }

    fun setExitTrigger(x: Float, y: Float, z: Float, width: Float = 4f, height: Float = 4f, depth: Float = 2f): RoomBuilder {
        exitTriggerPos.set(x, y, z)
        exitTriggerSize.set(width, height, depth)
        return this
    }

    private fun addBlock(x: Float, y: Float, z: Float, blockType: BlockType, rotation: Float = 0f): RoomBuilder {
        elements.add(RoomElement(
            position = Vector3(x, y, z),
            elementType = RoomElementType.BLOCK,
            blockType = blockType,
            rotation = rotation
        ))
        return this
    }

    fun addObject(x: Float, y: Float, z: Float, objectType: ObjectType, rotation: Float = 0f, scale: Vector3 = Vector3(1f, 1f, 1f)): RoomBuilder {
        elements.add(RoomElement(
            position = Vector3(x, y, z),
            elementType = RoomElementType.OBJECT,
            objectType = objectType,
            rotation = rotation,
            scale = scale
        ))
        return this
    }

    fun addItem(x: Float, y: Float, z: Float, itemType: ItemType): RoomBuilder {
        elements.add(RoomElement(
            position = Vector3(x, y, z),
            elementType = RoomElementType.ITEM,
            itemType = itemType
        ))
        return this
    }

    fun addFloor(floorType: BlockType = BlockType.WOODEN_FLOOR, blockSize: Float = 4f): RoomBuilder {
        for (x in 0 until roomSize.x.toInt() step blockSize.toInt()) {
            for (z in 0 until roomSize.z.toInt() step blockSize.toInt()) {
                addBlock(x.toFloat(), 0f, z.toFloat(), floorType)
            }
        }
        return this
    }

    fun addWalls(wallType: BlockType = BlockType.BRICK_WALL_PNG, blockSize: Float = 4f): RoomBuilder {
        val roomWidth = roomSize.x.toInt()
        val roomHeight = roomSize.y.toInt()
        val roomDepth = roomSize.z.toInt()
        for (y in 0 until roomHeight step blockSize.toInt()) {
            for (x in 0 until roomWidth step blockSize.toInt()) {
                addBlock(x.toFloat(), y.toFloat(), 0f, wallType) // Back wall
                addBlock(x.toFloat(), y.toFloat(), roomDepth - blockSize, wallType) // Front wall
            }
            for (z in 0 until roomDepth step blockSize.toInt()) {
                addBlock(0f, y.toFloat(), z.toFloat(), wallType) // Left wall
                addBlock(roomWidth - blockSize, y.toFloat(), z.toFloat(), wallType) // Right wall
            }
        }
        return this
    }

    // Add this new method inside the RoomBuilder class
    fun addInterior(x: Float, y: Float, z: Float, interiorType: InteriorType, rotation: Float = 0f, scale: Vector3 = Vector3(1f, 1f, 1f)): RoomBuilder {
        elements.add(RoomElement(
            position = Vector3(x, y, z),
            elementType = RoomElementType.INTERIOR,
            interiorType = interiorType,
            rotation = rotation,
            scale = scale
        ))
        return this
    }

    fun build(id: String, name: String, description: String = "", category: String = "default"): RoomTemplate {
        return RoomTemplate(
            id = id, name = name, description = description, category = category,
            size = roomSize.cpy(),
            elements = elements.toList(),
            entrancePosition = entrancePos.cpy(),
            exitTriggerPosition = exitTriggerPos.cpy(),
            exitTriggerSize = exitTriggerSize.cpy()
        )
    }
}

// Manages all available room templates
class RoomTemplateManager {
    private val templates = mutableMapOf<String, RoomTemplate>()
    private val json = Json().apply {
        setUsePrototypes(false)
        setOutputType(JsonWriter.OutputType.json)
    }
    private val templatesDir = "room_templates"

    fun initialize() {
        loadAllTemplates()
    }

    fun addTemplate(template: RoomTemplate) {
        templates[template.id] = template
        saveTemplateToFile(template)
    }

    fun getTemplate(id: String): RoomTemplate? = templates[id]
    fun getAllTemplates(): List<RoomTemplate> = templates.values.toList()
    fun getTemplatesByCategory(category: String): List<RoomTemplate> = templates.values.filter { it.category == category }

    private fun saveTemplateToFile(template: RoomTemplate) {
        try {
            val dirHandle = Gdx.files.local(templatesDir)
            if (!dirHandle.exists()) {
                dirHandle.mkdirs()
            }
            val fileHandle = dirHandle.child("${template.id}.json")
            val jsonString = json.prettyPrint(template) // Use prettyPrint for easier debugging
            fileHandle.writeString(jsonString, false)
            println("Saved template '${template.name}' to ${fileHandle.path()}")
        } catch (e: Exception) {
            println("ERROR: Failed to save room template '${template.id}': ${e.message}")
            e.printStackTrace()
        }
    }

    private fun loadAllTemplates() {
        templates.clear()
        val dirHandle = Gdx.files.local(templatesDir)
        println("RoomTemplateManager: Attempting to load templates from ${dirHandle.path()}...")

        if (!dirHandle.exists()) {
            println("RoomTemplateManager: Directory '$templatesDir' not found. Creating it for future use.")
            dirHandle.mkdirs()
            return
        }
        if (!dirHandle.isDirectory) {
            println("RoomTemplateManager: Error - '$templatesDir' is a file, not a directory. Cannot load templates.")
            return
        }

        val templateFiles = dirHandle.list(".json")
        if (templateFiles.isEmpty()) {
            println("RoomTemplateManager: Directory exists but contains no '.json' files.")
            return
        }

        println("RoomTemplateManager: Found ${templateFiles.size} potential template file(s).")
        templateFiles.forEach { file ->
            try {
                val jsonString = file.readString()
                if (jsonString.isBlank()) {
                    println(" -> WARNING: File ${file.name()} is empty. Skipping.")
                    return@forEach
                }

                val template = json.fromJson(RoomTemplate::class.java, jsonString)

                if (template != null && template.id.isNotEmpty()) {
                    templates[template.id] = template
                    println(" -> Successfully loaded template: ${template.name} (ID: ${template.id})")
                } else {
                    println(" -> ERROR: Failed to parse template from ${file.name()}. The result was null or had no ID.")
                }
            } catch (e: Exception) {
                println(" -> EXCEPTION while loading template from ${file.name()}: ${e.message}")
                e.printStackTrace()
            }
        }
        println("RoomTemplateManager: Finished loading templates. Total loaded: ${templates.size}")
    }
}
