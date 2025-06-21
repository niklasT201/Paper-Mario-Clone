package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.Gdx
import kotlin.collections.List

// Data classes for room building
data class RoomElement(
    val position: Vector3,
    val elementType: RoomElementType,
    val blockType: BlockType? = null,
    val objectType: ObjectType? = null,
    val itemType: ItemType? = null,
    val rotation: Float = 0f,
    val scale: Vector3 = Vector3(1f, 1f, 1f)
)

enum class RoomElementType {
    BLOCK,
    OBJECT,
    ITEM
}

data class RoomTemplate(
    val id: String,
    val name: String,
    val description: String,
    val size: Vector3,
    val elements: List<RoomElement>, // Use Kotlin List
    val entrancePosition: Vector3,
    val exitTriggerPosition: Vector3,
    val exitTriggerSize: Vector3 = Vector3(4f, 4f, 2f),
    val category: String = "default"
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

    fun addBlock(x: Float, y: Float, z: Float, blockType: BlockType, rotation: Float = 0f): RoomBuilder {
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
    private val json = Json()

    fun initialize() {
        createDefaultTemplates()
        loadTemplatesFromFile()
    }

    private fun createDefaultTemplates() {
        if (templates.containsKey("living_room")) return // Don't recreate if loaded from file

        val livingRoom = RoomBuilder()
            .setSize(20f, 8f, 16f)
            .setEntrance(10f, 2f, 13f)
            .setExitTrigger(10f, 2f, 14f)
            .addFloor().addWalls()
            .addObject(3f, 0f, 3f, ObjectType.LANTERN)
            .addObject(17f, 0f, 3f, ObjectType.TREE)
            .build("living_room", "Living Room", "A cozy living room.", "residential")
        templates[livingRoom.id] = livingRoom

        val bedroom = RoomBuilder()
            .setSize(12f, 8f, 12f)
            .setEntrance(6f, 2f, 9f)
            .setExitTrigger(6f, 2f, 10f)
            .addFloor(BlockType.CARPET).addWalls(BlockType.TAPETE_WALL)
            .addObject(2f, 0f, 2f, ObjectType.LANTERN)
            .build("bedroom", "Bedroom", "A small bedroom.", "residential")
        templates[bedroom.id] = bedroom
    }

    fun addTemplate(template: RoomTemplate) {
        templates[template.id] = template
        saveTemplatesToFile()
    }

    fun getTemplate(id: String): RoomTemplate? = templates[id]
    fun getAllTemplates(): List<RoomTemplate> = templates.values.toList()
    fun getTemplatesByCategory(category: String): List<RoomTemplate> = templates.values.filter { it.category == category }

    private fun saveTemplatesToFile() {
        try {
            val fileHandle = Gdx.files.local("room_templates.json")
            val jsonString = json.toJson(templates.values.toList())
            fileHandle.writeString(jsonString, false)
            println("Room templates saved successfully")
        } catch (e: Exception) {
            println("Failed to save room templates: ${e.message}")
        }
    }
    private fun loadTemplatesFromFile() {
        try {
            val fileHandle = Gdx.files.local("room_templates.json")
            if (fileHandle.exists()) {
                val jsonString = fileHandle.readString()
                val loadedTemplates = json.fromJson(Array<RoomTemplate>().javaClass, jsonString)
                loadedTemplates?.forEach { template ->
                    templates[template.id] = template
                }
                println("Room templates loaded successfully")
            }
        } catch (e: Exception) {
            println("Failed to load room templates: ${e.message}")
        }
    }
}

// This is the Adapter that connects the RoomTemplate system to the SceneManager
class EnhancedInteriorLayoutSystem(private val templateManager: RoomTemplateManager) {

    private val houseToTemplateMap = mutableMapOf<HouseType, String>(
        HouseType.HOUSE_1 to "bedroom",
        HouseType.HOUSE_2 to "living_room",
        HouseType.HOUSE_3 to "living_room",
        HouseType.HOUSE_4 to "living_room",
        HouseType.STAIR to "bedroom"
    )

    fun assignTemplateToHouseType(houseType: HouseType, templateId: String) {
        houseToTemplateMap[houseType] = templateId
    }

    fun getLayout(houseType: HouseType): InteriorLayout? {
        val templateId = houseToTemplateMap[houseType] ?: return null
        val template = templateManager.getTemplate(templateId) ?: return null
        return convertTemplateToLayout(template)
    }

    private fun convertTemplateToLayout(template: RoomTemplate): InteriorLayout {
        val blocks = template.elements.filter { it.elementType == RoomElementType.BLOCK }
            .mapNotNull { element -> element.blockType?.let { Pair(element.position.cpy(), it) } }

        val furniture = template.elements.filter { it.elementType == RoomElementType.OBJECT }
            .mapNotNull { element -> element.objectType?.let { Pair(element.position.cpy(), it) } }

        return InteriorLayout(
            size = template.size,
            defaultBlocks = blocks,
            defaultFurniture = furniture,
            entrancePosition = template.entrancePosition,
            exitTriggerPosition = template.exitTriggerPosition,
            exitTriggerSize = template.exitTriggerSize
        )
    }
}
