package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import kotlin.math.sin

// Item system class to manage 2D rotating item pickups
class ItemSystem {
    private val itemModels = mutableMapOf<ItemType, Model>()
    private val itemTextures = mutableMapOf<ItemType, Texture>()
    private val gameItems = Array<GameItem>()

    var currentSelectedItem = ItemType.MONEY_STACK
        private set
    var currentSelectedItemIndex = 0
        private set

    fun initialize() {
        val modelBuilder = ModelBuilder()

        // Load textures and create models for each item type
        for (itemType in ItemType.values()) {
            try {
                // Load texture
                val texture = Texture(Gdx.files.internal(itemType.texturePath))
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                itemTextures[itemType] = texture

                // Create material with texture and transparency
                val material = Material(
                    TextureAttribute.createDiffuse(texture),
                    BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                    IntAttribute.createCullFace(GL20.GL_NONE) // Disable backface culling for 2D sprites
                )

                // Create billboard model (always faces camera)
                val model = createBillboardModel(modelBuilder, material, itemType)
                itemModels[itemType] = model

                println("Loaded item type: ${itemType.displayName}")
            } catch (e: Exception) {
                println("Failed to load item ${itemType.displayName}: ${e.message}")
            }
        }
    }

    private fun createBillboardModel(modelBuilder: ModelBuilder, material: Material, itemType: ItemType): Model {
        val width = itemType.width
        val height = itemType.height
        val halfWidth = width / 2f
        val halfHeight = height / 2f

        // Create a single quad that will be rotated to face the camera
        modelBuilder.begin()

        val part = modelBuilder.part(
            "billboard",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or
                VertexAttributes.Usage.Normal or
                VertexAttributes.Usage.TextureCoordinates).toLong(),
            material
        )

        // Vertices for the quad (Y-up orientation)
        part.vertex(-halfWidth, 0f, 0f, 0f, 0f, 1f, 0f, 1f) // Bottom left
        part.vertex(halfWidth, 0f, 0f, 0f, 0f, 1f, 1f, 1f)  // Bottom right
        part.vertex(halfWidth, height, 0f, 0f, 0f, 1f, 1f, 0f) // Top right
        part.vertex(-halfWidth, height, 0f, 0f, 0f, 1f, 0f, 0f) // Top left

        // Triangles for the quad
        part.triangle(0, 1, 2) // First triangle
        part.triangle(2, 3, 0) // Second triangle

        return modelBuilder.end()
    }

    fun nextItem() {
        currentSelectedItemIndex = (currentSelectedItemIndex + 1) % ItemType.values().size
        currentSelectedItem = ItemType.values()[currentSelectedItemIndex]
        println("Selected item: ${currentSelectedItem.displayName}")
    }

    fun previousItem() {
        currentSelectedItemIndex = if (currentSelectedItemIndex > 0) {
            currentSelectedItemIndex - 1
        } else {
            ItemType.values().size - 1
        }
        currentSelectedItem = ItemType.values()[currentSelectedItemIndex]
        println("Selected item: ${currentSelectedItem.displayName}")
    }

    fun createItemInstance(itemType: ItemType): ModelInstance? {
        val model = itemModels[itemType]
        return model?.let { ModelInstance(it) }
    }

    fun addItem(position: Vector3, itemType: ItemType) {
        val modelInstance = createItemInstance(itemType)
        if (modelInstance != null) {
            val gameItem = GameItem(modelInstance, itemType, position.cpy())
            gameItems.add(gameItem)
            println("Added ${itemType.displayName} at position: $position")
        }
    }

    fun removeItem(item: GameItem) {
        if (gameItems.removeValue(item, true)) {
            println("Removed ${item.itemType.displayName}")
        }
    }

    fun update(deltaTime: Float, cameraPosition: Vector3, playerPosition: Vector3, playerRadius: Float = 1f) {
        val itemsToRemove = Array<GameItem>()

        for (item in gameItems) {
            if (!item.isCollected) {
                // Update item animation
                item.update(deltaTime, cameraPosition)

                // Check collision with player
                if (item.checkCollision(playerPosition, playerRadius)) {
                    item.collect()
                    itemsToRemove.add(item)
                }
            }
        }

        // Remove collected items
        for (item in itemsToRemove) {
            removeItem(item)
        }
    }

    fun render(modelBatch: com.badlogic.gdx.graphics.g3d.ModelBatch, environment: com.badlogic.gdx.graphics.g3d.Environment) {
        for (item in gameItems) {
            if (!item.isCollected) {
                modelBatch.render(item.modelInstance, environment)
            }
        }
    }

    fun getItemAtPosition(position: Vector3, radius: Float = 2f): GameItem? {
        for (item in gameItems) {
            if (!item.isCollected && item.position.dst(position) <= radius) {
                return item
            }
        }
        return null
    }

    fun getAllItems(): Array<GameItem> = gameItems

    fun dispose() {
        itemModels.values.forEach { it.dispose() }
        itemTextures.values.forEach { it.dispose() }
    }
}

// Game item class to store item pickup data
data class GameItem(
    val modelInstance: ModelInstance,
    val itemType: ItemType,
    val position: Vector3,
    var rotationSpeed: Float = 90f, // Degrees per second
    private var totalTime: Float = 0f,
    private var bobOffset: Float = 0f, // Random offset for bobbing animation
    var isCollected: Boolean = false
) {
    init {
        // Random bobbing offset so items don't all bob in sync
        bobOffset = (Math.random() * Math.PI * 2).toFloat()
        // Randomize rotation speed slightly
        rotationSpeed += ((Math.random() - 0.5) * 30).toFloat() // ±15 degrees variation
    }

    // Get bounding box for collision detection
    fun getBoundingBox(): BoundingBox {
        val bounds = BoundingBox()
        val halfWidth = itemType.width / 2f
        val halfHeight = itemType.height / 2f

        // Create a smaller collision box for easier pickup
        val collisionScale = 0.8f
        bounds.set(
            Vector3(
                position.x - halfWidth * collisionScale,
                position.y,
                position.z - halfWidth * collisionScale
            ),
            Vector3(
                position.x + halfWidth * collisionScale,
                position.y + itemType.height * collisionScale,
                position.z + halfWidth * collisionScale
            )
        )
        return bounds
    }

    // Update item animation (rotation and bobbing)
    fun update(deltaTime: Float, cameraPosition: Vector3) {
        if (isCollected) return

        totalTime += deltaTime

        // Rotate around Y-axis
        val currentRotation = totalTime * rotationSpeed

        // Bobbing animation (slight up-down movement)
        val bobHeight = sin(totalTime * 3f + bobOffset) * 0.3f // 0.3 units max bob

        // Calculate billboard rotation to face camera
        val direction = Vector3(cameraPosition).sub(position)
        direction.y = 0f // Keep it horizontal
        direction.nor()

        // Calculate angle to rotate towards camera
        val angle = Math.atan2(direction.x.toDouble(), direction.z.toDouble()) * 180.0 / Math.PI

        // Apply transformations: position + bobbing + rotation towards camera + spinning
        modelInstance.transform.setToTranslation(position.x, position.y + bobHeight, position.z)
        modelInstance.transform.rotate(Vector3.Y, angle.toFloat() + currentRotation)
    }

    // Check collision with player
    fun checkCollision(playerPosition: Vector3, playerRadius: Float = 1f): Boolean {
        if (isCollected) return false

        val distance = Vector3(playerPosition).sub(position).len()
        return distance < (playerRadius + itemType.width / 2f)
    }

    // Collect the item
    fun collect(): String {
        isCollected = true
        println("Collected ${itemType.displayName}!")
        return itemType.displayName
    }
}

// Item type definitions - you can add more item types here
enum class ItemType(
    val displayName: String,
    val texturePath: String,
    val width: Float,
    val height: Float,
    val value: Int = 1 // For future scoring/inventory system
) {
    MONEY_STACK("Money Stack", "textures/objects/items/money_stack.png", 2f, 2f, 10),
    COIN("Coin", "textures/objects/items/coin.png", 1.5f, 1.5f, 1),
    HEALTH_POTION("Health Potion", "textures/objects/items/health_potion.png", 1.8f, 2.2f, 5),
    KEY("Key", "textures/objects/items/key.png", 1.2f, 2f, 1),
    GEM("Gem", "textures/objects/items/gem.png", 1.5f, 1.5f, 20)
}
