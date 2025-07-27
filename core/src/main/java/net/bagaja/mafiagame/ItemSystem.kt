package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import kotlin.math.sin

// Item system class to manage 2D rotating item pickups
class ItemSystem: IFinePositionable {
    companion object {
        const val UNIFIED_ITEM_HEIGHT = 2.0f // All items will now have this visual height in the game world.
        const val ITEM_SURFACE_OFFSET = 0.35f
    }
    private val itemModels = mutableMapOf<ItemType, Model>()
    private val itemTextures = mutableMapOf<ItemType, Texture>()
    private val gameItems = Array<GameItem>()
    private lateinit var itemModelBatch: ModelBatch
    private lateinit var billboardShaderProvider: BillboardShaderProvider
    private val FALL_SPEED = 25f
    private val MAX_STEP_HEIGHT = 0.5f // Items can step over small bumps
    private var blockSize: Float = 4f // Will be set by initialize

    var currentSelectedItem = ItemType.MONEY_STACK
        private set
    var currentSelectedItemIndex = 0
        private set

    override var finePosMode = false
    override val fineStep = 0.25f

    fun initialize(blockSize: Float) {
        this.blockSize = blockSize
        val modelBuilder = ModelBuilder()

        // Initialize shader and batch for items
        billboardShaderProvider = BillboardShaderProvider()
        itemModelBatch = ModelBatch(billboardShaderProvider)
        billboardShaderProvider.setBillboardLightingStrength(0.7f)

        // Load textures and create models for each item type
        for (itemType in ItemType.entries) {
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
        val height = UNIFIED_ITEM_HEIGHT
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
        currentSelectedItemIndex = (currentSelectedItemIndex + 1) % ItemType.entries.size
        currentSelectedItem = ItemType.entries.toTypedArray()[currentSelectedItemIndex]
        println("Selected item: ${currentSelectedItem.displayName}")
    }

    fun previousItem() {
        currentSelectedItemIndex = if (currentSelectedItemIndex > 0) {
            currentSelectedItemIndex - 1
        } else {
            ItemType.entries.size - 1
        }
        currentSelectedItem = ItemType.entries.toTypedArray()[currentSelectedItemIndex]
        println("Selected item: ${currentSelectedItem.displayName}")
    }

    private fun createItemInstance(itemType: ItemType): ModelInstance? {
        val model = itemModels[itemType]
        return model?.let {
            val instance = ModelInstance(it)
            instance.userData = "item"
            instance
        }
    }

    fun createItem(position: Vector3, itemType: ItemType): GameItem? {
        val modelInstance = createItemInstance(itemType)
        if (modelInstance != null) {
            // Returns a new GameItem instance. The caller is responsible for adding it to a list.
            return GameItem(modelInstance, itemType, position.cpy())
        }
        return null
    }

    fun setActiveItems(newItems: Array<GameItem>) {
        gameItems.clear()
        gameItems.addAll(newItems)
        println("ItemSystem has been updated with ${newItems.size} active items.")
    }

    fun removeItem(item: GameItem) {
        if (gameItems.removeValue(item, true)) {
            println("Removed ${item.itemType.displayName}")
        }
    }

    fun update(deltaTime: Float, camera: Camera, playerSystem: PlayerSystem, sceneManager: SceneManager) {
        val itemsToRemove = Array<GameItem>()

        for (item in gameItems) {
            if (item.isCollected) continue

            // 1. Apply Gravity and Find Support for the item
            val itemX = item.position.x
            val itemZ = item.position.z
            val itemRadius = item.itemType.width / 4f // Use a small radius for point-like check
            val itemBottomY = item.position.y // The item's origin is at its base

            val supportY = sceneManager.findHighestSupportY(itemX, itemZ, itemBottomY, itemRadius, this.blockSize)

            val effectiveSupportY = if (supportY - itemBottomY <= MAX_STEP_HEIGHT) {
                // The ground is within stepping range, so we can use it.
                supportY
            } else {
                // The ground is too high (it's a wall), so we maintain our current Y-level for this check.
                itemBottomY
            }

            // Apply Gravity
            val fallY = item.position.y - FALL_SPEED * deltaTime
            val nextY = kotlin.math.max(effectiveSupportY, fallY) // Item is on ground, stepping up, or falling.

            // 2. Update item position if it moved vertically
            if (kotlin.math.abs(nextY - item.position.y) > 0.01f) {
                item.position.y = nextY + ITEM_SURFACE_OFFSET
            }

            // Update item animation (rotation and bobbing)
            item.update(deltaTime, camera.position)

            // Check collision with player
            if (item.checkCollision(playerSystem.getPosition(), 2f)) {
                item.collect()
                itemsToRemove.add(item)

                // Check if the collected item corresponds to a weapon
                item.itemType.correspondingWeapon?.let { weaponToEquip ->
                    // If it does, tell the player system to equip it
                    playerSystem.equipWeapon(weaponToEquip)
                }
            }
        }

        // Remove collected items
        for (item in itemsToRemove) {
            removeItem(item)
        }
    }

    fun render(camera: Camera, environment: Environment) {
        billboardShaderProvider.setEnvironment(environment)

        itemModelBatch.begin(camera)
        for (item in gameItems) {
            if (!item.isCollected) {
                itemModelBatch.render(item.modelInstance, environment)
            }
        }
        itemModelBatch.end()
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
        itemModelBatch.dispose()
        billboardShaderProvider.dispose()
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
                position.y + ItemSystem.UNIFIED_ITEM_HEIGHT * collisionScale,
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

// Item type definitions - updated with new weapon folder structure and additional weapons
enum class ItemType(
    val displayName: String,
    val texturePath: String,
    val width: Float,
    val value: Int = 1, // For future scoring/inventory system
    val correspondingWeapon: WeaponType? = null
) {
    // Non-weapon items
    MONEY_STACK("Money Stack", "textures/objects/items/money_stack.png", 2f, 10),

    // Melee weapon
    BASEBALL_BAT("Baseball Bat", "textures/objects/items/weapons/baseball_bat.png", 2f, 15, WeaponType.BASEBALL_BAT),

    // Weapons
    REVOLVER("Revolver", "textures/objects/items/weapons/revolver.png", 2f, 15, WeaponType.PISTOL),
    REVOLVER_LIGHT("Light Revolver", "textures/objects/items/weapons/revolver_light.png", 2f, 12, WeaponType.PISTOL),
    SMALLER_REVOLVER("Small Revolver", "textures/objects/items/weapons/smaller_revolver.png", 1.8f, 12, WeaponType.PISTOL),

    SHOTGUN("Shotgun", "textures/objects/items/weapons/shotgun.png", 3f, 25),
    SHOTGUN_LIGHT("Light Shotgun", "textures/objects/items/weapons/shotgun_light.png", 3f, 22),
    SMALL_SHOTGUN("Small Shotgun", "textures/objects/items/weapons/small_shotgun.png", 2.5f, 20),

    MACHINE_GUN("Machine Gun", "textures/objects/items/weapons/machine_gun.png", 3.5f, 40, WeaponType.MACHINE_GUN),

    TOMMY_GUN("Tommy Gun", "textures/objects/items/weapons/tommy_gun.png", 3.5f, 35, WeaponType.TOMMY_GUN),
    TOMMY_GUN_LIGHT("Light Tommy Gun", "textures/objects/items/weapons/tommy_gun_light.png", 3.5f, 32, WeaponType.TOMMY_GUN),

    KNIFE("Knife", "textures/objects/items/weapons/knife.png", 1.5f, 8, WeaponType.KNIFE),

    // Explosives
    DYNAMITE("Dynamite", "textures/objects/items/weapons/dynamite.png", 2f, 10, WeaponType.DYNAMITE),
    MOLOTOV("Molotov", "textures/objects/items/weapons/molotov.png", 1.5f, 10, WeaponType.MOLOTOV);
}
