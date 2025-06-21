package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter

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

data class RoomTemplateList(val templates: List<RoomTemplate> = emptyList())

data class RoomTemplate(
    val id: String,
    val name: String,
    val description: String,
    val size: Vector3,
    val elements: List<RoomElement>,
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
            val jsonString = json.toJson(template)
            fileHandle.writeString(jsonString, false)
            println("Saved template '${template.id}' to ${fileHandle.path()}")
        } catch (e: Exception) {
            println("Failed to save room template '${template.id}': ${e.message}")
        }
    }

    private fun loadAllTemplates() {
        templates.clear()
        val dirHandle = Gdx.files.local(templatesDir)

        if (dirHandle.exists() && dirHandle.isDirectory() && dirHandle.list().isNotEmpty()) {
            println("Loading room templates from directory: $templatesDir/")

            dirHandle.list("json").forEach { file ->
                try {
                    val jsonString = file.readString()
                    val template = json.fromJson(RoomTemplate::class.java, jsonString)
                    if (template != null) {
                        templates[template.id] = template
                        println(" -> Loaded template: ${template.name} (id: ${template.id})")
                    }
                } catch (e: Exception) {
                    println("Error loading template from ${file.name()}: ${e.message}")
                }
            }
        } else {
            val oldFileHandle = Gdx.files.local("room_templates.json")
            if (oldFileHandle.exists()) {
                println("'$templatesDir/' directory not found or is empty. Falling back to 'room_templates.json'.")
                try {
                    val jsonString = oldFileHandle.readString()
                    val templateList = json.fromJson(RoomTemplateList::class.java, jsonString)
                    templateList?.templates?.forEach { template ->
                        templates[template.id] = template
                        println(" -> Loaded template from old file: ${template.name} (id: ${template.id})")
                    }
                } catch (e: Exception) {
                    println("Failed to load room templates from 'room_templates.json': ${e.message}")
                }
            } else {
                println("No room template files found.")
            }
        }
    }
}
